package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.InteractBinding;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.effects.integration.PassiveBinding;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.config.UpgradeScalingConfig;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.progression.custom.CustomXpProfile;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import dev.patric.dungeonsreborn.shops.ShopTokenTierSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

public final class UpgradeService {
  public record ApplyResult(boolean success, String error, ItemStack updated, boolean consumeUpgrade, boolean destroyTarget) {
    public static ApplyResult success(ItemStack updated) {
      return new ApplyResult(true, null, updated, true, false);
    }
  }

  public record SalvageResult(boolean success, String error, ItemStack updated, List<ItemStack> rewards) {
  }

  private record ResolvedUpgrade(UpgradeSpec spec, String recordId) {
  }

  private final EffectsEngine engine;
  private final EffectsBindings bindings;
  private final UpgradeYamlRegistry registry;
  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final ShopYamlRegistry shopRegistry;
  private final CustomXpService customXpService;
  private static final String INVENTORY_UPGRADE_PREFIX = "inv_upgrade_";
  private static final int INVENTORY_EFFECT_MIN_TICKS = 40;
  private final Map<UUID, Map<String, Long>> onDamagedCooldowns = new HashMap<>();

  public UpgradeService(JavaPlugin plugin, EffectsEngine engine, EffectsBindings bindings, UpgradeYamlRegistry registry,
      ShopYamlRegistry shopRegistry, CustomXpService customXpService, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.bindings = Objects.requireNonNull(bindings, "bindings");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.shopRegistry = shopRegistry;
    this.customXpService = customXpService;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public UpgradeYamlRegistry registry() {
    return registry;
  }

  public UpgradeSpec resolveSpec(ItemStack upgradeItem) {
    ResolvedUpgrade resolved = resolveUpgrade(upgradeItem);
    return resolved == null ? null : resolved.spec();
  }

  public List<String> upgradeRecords(ItemStack item) {
    return ItemMarkers.getUpgradeRecords(item);
  }

  public boolean isUpgradeItem(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    if (ItemMarkers.getUpgradeId(item) != null) {
      return true;
    }
    if (item.getType() != org.bukkit.Material.ENCHANTED_BOOK) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta stored) {
      return stored.hasStoredEnchants();
    }
    return false;
  }

  public ApplyResult preview(Player player, ItemStack target, ItemStack upgradeItem) {
    return applyUpgrade(player, target, upgradeItem, true);
  }

  public ApplyResult apply(Player player, ItemStack target, ItemStack upgradeItem) {
    return applyUpgrade(player, target, upgradeItem, false);
  }

  public SalvageResult salvage(Player player, ItemStack target, String recordId) {
    if (player == null) {
      return new SalvageResult(false, "Player required.", null, List.of());
    }
    if (target == null || target.getType().isAir()) {
      return new SalvageResult(false, "Target item required.", null, List.of());
    }
    if (recordId == null || recordId.isBlank()) {
      return new SalvageResult(false, "Upgrade id required.", null, List.of());
    }
    String normalized = Ids.normalize(recordId);
    List<String> records = ItemMarkers.getUpgradeRecords(target);
    if (records.isEmpty()) {
      return new SalvageResult(false, "Target has no upgrades.", null, List.of());
    }
    String record = null;
    for (String candidate : records) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      if (candidate.startsWith("vanilla:")) {
        continue;
      }
      if (Ids.normalize(candidate).equals(normalized)) {
        record = candidate;
        break;
      }
    }
    if (record == null) {
      return new SalvageResult(false, "Upgrade not found on target.", null, List.of());
    }
    UpgradeSpec spec = registry.upgradeSpec(record);
    if (spec == null) {
      return new SalvageResult(false, "Upgrade spec missing.", null, List.of());
    }
    UpgradeSalvageSpec salvage = spec.salvage();
    if (salvage == null || salvage.isEmpty()) {
      return new SalvageResult(false, "Upgrade cannot be salvaged.", null, List.of());
    }
    String rewardError = validateSalvageRewards(salvage);
    if (rewardError != null) {
      return new SalvageResult(false, rewardError, null, List.of());
    }
    ItemStack updated = removeUpgradeRecord(target, record, spec);
    List<ItemStack> rewards = buildSalvageRewards(salvage);
    grantRewards(player, rewards);
    logger.info("[Upgrades] salvage: player=" + player.getName()
        + " target=" + describeItem(target)
        + " upgrade=" + record
        + " rewards=" + rewards.size());
    return new SalvageResult(true, null, updated, rewards);
  }

  public ItemStack clearUpgrades(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return item;
    }
    ItemStack updated = item.clone();
    ItemMeta meta = updated.getItemMeta();
    if (meta == null) {
      return updated;
    }
    List<String> existingRecords = new ArrayList<>(ItemMarkers.getUpgradeRecords(updated));
    if (meta.hasAttributeModifiers()) {
      var modifiers = meta.getAttributeModifiers();
      if (modifiers != null) {
        for (var entry : modifiers.entries()) {
          AttributeModifier modifier = entry.getValue();
          NamespacedKey key = modifier.getKey();
          if (key != null && "dungeonsreborn".equals(key.getNamespace())
              && key.getKey().startsWith("upgrade_")) {
            meta.removeAttributeModifier(entry.getKey());
          }
        }
      }
    }
    for (String record : existingRecords) {
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec != null) {
        for (UpgradeEnchantSpec enchantSpec : spec.enchants()) {
          meta.removeEnchant(enchantSpec.enchantment());
        }
        for (UpgradeSpellSpec spell : spec.spells()) {
          removeSpellBinding(updated, record, spell);
        }
      } else if (record != null && record.startsWith("vanilla:")) {
        Map<String, Integer> vanilla = parseVanillaRecord(record);
        for (String key : vanilla.keySet()) {
          Enchantment enchant = enchantmentByKey(key);
          if (enchant != null) {
            meta.removeEnchant(enchant);
          }
        }
      }
    }
    updated.setItemMeta(meta);
    ItemMarkers.setUpgradeRecords(updated, List.of());
    ItemMarkers.setUpgradeModifiers(updated, Map.of());
    ItemMarkers.setStringList(updated, ItemMarkers.UPGRADE_SECONDARY_ABILITIES, List.of());
    ItemMarkers.setUpgradeStatusEffects(updated, List.of());
    ItemMarkers.setUpgradeScale(updated, 1.0);
    UpgradeLore.applyAppliedUpgradeLore(updated, List.of(), registry);
    if (!existingRecords.isEmpty()) {
      logger.info("[Upgrades] clear: target=" + describeItem(item) + " removed=" + existingRecords.size());
    }
    return updated;
  }

  public ItemStack normalizeUpgradeMetadata(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return item;
    }
    List<String> records = ItemMarkers.getUpgradeRecords(item);
    if (records.isEmpty()) {
      return item;
    }
    ItemStack updated = item.clone();
    stripInventoryActiveAttributes(updated, records);
    double scale = ItemMarkers.getUpgradeScale(updated);
    ItemMarkers.setUpgradeModifiers(updated, aggregateModifiers(records, scale));
    ItemMarkers.setStringList(updated, ItemMarkers.UPGRADE_SECONDARY_ABILITIES, aggregateSecondaryAbilities(records));
    ItemMarkers.setUpgradeStatusEffects(updated, aggregateStatusEffects(records, scale));
    UpgradeLore.applyAppliedUpgradeLore(updated, records, registry);
    return updated;
  }

  public List<String> validateItemUpgrades(Player player, ItemStack item) {
    List<String> issues = new ArrayList<>();
    if (item == null || item.getType().isAir()) {
      issues.add("No item to validate.");
      return issues;
    }
    List<String> records = ItemMarkers.getUpgradeRecords(item);
    if (records.isEmpty()) {
      return issues;
    }
    for (String record : records) {
      if (record == null || record.isBlank()) {
        issues.add("Empty upgrade record entry.");
        continue;
      }
      if (record.startsWith("vanilla:")) {
        Map<String, Integer> vanilla = parseVanillaRecord(record);
        if (vanilla.isEmpty()) {
          issues.add("Invalid vanilla record: " + record);
          continue;
        }
        for (Map.Entry<String, Integer> entry : vanilla.entrySet()) {
          Enchantment enchant = enchantmentByKey(entry.getKey());
          if (enchant == null) {
            issues.add("Unknown vanilla enchant: " + entry.getKey());
          }
          if (entry.getValue() == null || entry.getValue() <= 0) {
            issues.add("Invalid vanilla enchant level for " + entry.getKey() + ": " + entry.getValue());
          }
        }
        continue;
      }
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec == null) {
        issues.add("Unknown upgrade record: " + record);
        continue;
      }
      ItemStack context = item.clone();
      List<String> reduced = new ArrayList<>(records);
      reduced.remove(record);
      ItemMarkers.setUpgradeRecords(context, reduced);
      String error = validateTarget(player, context, spec.target());
      if (error != null) {
        issues.add(record + ": " + error);
      }
      error = validateCompatibility(context, spec.compatibility());
      if (error != null) {
        issues.add(record + ": " + error);
      }
      error = validateLimits(context, spec);
      if (error != null) {
        issues.add(record + ": " + error);
      }
    }
    return issues;
  }

  public int migrateOnlinePlayers() {
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("UpgradeService.migrateOnlinePlayers must be called on the primary thread");
    }
    int updated = 0;
    for (Player player : Bukkit.getOnlinePlayers()) {
      PlayerInventory inv = player.getInventory();
      ItemStack[] contents = inv.getContents();
      boolean contentsChanged = false;
      for (int i = 0; i < contents.length; i++) {
        ItemStack before = contents[i];
        ItemStack after = normalizeUpgradeMetadata(before);
        if (!sameItem(before, after)) {
          contents[i] = after;
          contentsChanged = true;
          updated++;
        }
      }
      if (contentsChanged) {
        inv.setContents(contents);
      }

      ItemStack offhand = inv.getItemInOffHand();
      ItemStack offhandUpdated = normalizeUpgradeMetadata(offhand);
      if (!sameItem(offhand, offhandUpdated)) {
        inv.setItemInOffHand(offhandUpdated);
        updated++;
      }

      ItemStack[] armor = inv.getArmorContents();
      boolean armorChanged = false;
      for (int i = 0; i < armor.length; i++) {
        ItemStack before = armor[i];
        ItemStack after = normalizeUpgradeMetadata(before);
        if (!sameItem(before, after)) {
          armor[i] = after;
          armorChanged = true;
          updated++;
        }
      }
      if (armorChanged) {
        inv.setArmorContents(armor);
      }
    }
    return updated;
  }

  public void tickInventoryAuras() {
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException("UpgradeService.tickInventoryAuras must be called on the primary thread");
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      applyInventoryAuras(player);
    }
  }

  public void handleOnDamaged(Player player) {
    if (player == null) {
      return;
    }
    Map<String, UpgradeOnDamagedSpec> effects = collectOnDamagedEffects(player);
    if (effects.isEmpty()) {
      return;
    }
    UUID playerId = player.getUniqueId();
    long now = System.currentTimeMillis();
    Map<String, Long> cooldowns = onDamagedCooldowns.computeIfAbsent(playerId, key -> new LinkedHashMap<>());
    cooldowns.keySet().retainAll(effects.keySet());
    for (Map.Entry<String, UpgradeOnDamagedSpec> entry : effects.entrySet()) {
      String key = entry.getKey();
      UpgradeOnDamagedSpec spec = entry.getValue();
      Long nextReady = cooldowns.get(key);
      if (nextReady != null && nextReady > now) {
        continue;
      }
      UpgradeStatusEffectSpec effectSpec = spec.effect();
      double chance = effectSpec.chance() > 1.0 ? effectSpec.chance() / 100.0 : effectSpec.chance();
      if (chance <= 0.0) {
        continue;
      }
      if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) {
        continue;
      }
      PotionEffect effect = new PotionEffect(effectSpec.type(), effectSpec.durationTicks(), effectSpec.amplifier(),
          effectSpec.ambient(), effectSpec.particles(), effectSpec.icon());
      player.addPotionEffect(effect);
      long cooldownTicks = spec.cooldownTicks();
      if (cooldownTicks > 0L) {
        cooldowns.put(key, now + ticksToMillis(cooldownTicks));
      }
    }
    if (cooldowns.isEmpty()) {
      onDamagedCooldowns.remove(playerId);
    }
  }

  public void clearOnDamagedState(UUID playerId) {
    if (playerId == null) {
      return;
    }
    onDamagedCooldowns.remove(playerId);
  }

  private void applyInventoryAuras(Player player) {
    if (player == null) {
      return;
    }
    Map<Attribute, LinkedHashMap<NamespacedKey, AttributeModifier>> modifiers = new LinkedHashMap<>();
    List<UpgradeStatusEffectSpec> effects = new ArrayList<>();

    PlayerInventory inv = player.getInventory();
    List<ItemStack> items = new ArrayList<>();
    java.util.Collections.addAll(items, inv.getContents());
    java.util.Collections.addAll(items, inv.getArmorContents());
    items.add(inv.getItemInOffHand());

    for (ItemStack item : items) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      List<String> records = ItemMarkers.getUpgradeRecords(item);
      if (records.isEmpty()) {
        continue;
      }
      double scale = ItemMarkers.getUpgradeScale(item);
      for (String record : records) {
        if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
          continue;
        }
        UpgradeSpec spec = registry.upgradeSpec(record);
        if (spec == null || spec.behaviors() == null || !spec.behaviors().inventoryActive()) {
          continue;
        }
        int index = 0;
        for (UpgradeAttributeSpec attr : spec.attributes()) {
          AttributeModifier modifier = new AttributeModifier(inventoryModifierKey(record, attr.attribute(), index++),
              attr.amount() * scale, attr.operation());
          modifiers.computeIfAbsent(attr.attribute(), key -> new LinkedHashMap<>())
              .putIfAbsent(modifier.getKey(), modifier);
        }
        for (UpgradeStatusEffectSpec effect : spec.behaviors().inventoryEffects()) {
          effects.add(scaleStatusEffect(effect, scale));
        }
      }
    }

    clearInventoryModifiers(player);
    for (Map.Entry<Attribute, LinkedHashMap<NamespacedKey, AttributeModifier>> entry : modifiers.entrySet()) {
      AttributeInstance instance = player.getAttribute(entry.getKey());
      if (instance == null) {
        continue;
      }
      for (AttributeModifier modifier : entry.getValue().values()) {
        if (hasModifier(instance, modifier.getKey())) {
          continue;
        }
        instance.addModifier(modifier);
      }
    }
    applyInventoryStatusEffects(player, effects);
  }

  private Map<String, UpgradeOnDamagedSpec> collectOnDamagedEffects(Player player) {
    Map<String, UpgradeOnDamagedSpec> out = new LinkedHashMap<>();
    PlayerInventory inv = player.getInventory();
    List<ItemStack> items = new ArrayList<>();
    java.util.Collections.addAll(items, inv.getContents());
    java.util.Collections.addAll(items, inv.getArmorContents());
    items.add(inv.getItemInOffHand());

    for (ItemStack item : items) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      List<String> records = ItemMarkers.getUpgradeRecords(item);
      if (records.isEmpty()) {
        continue;
      }
      double scale = ItemMarkers.getUpgradeScale(item);
      for (String record : records) {
        if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
          continue;
        }
        UpgradeSpec spec = registry.upgradeSpec(record);
        if (spec == null || spec.behaviors() == null) {
          continue;
        }
        for (UpgradeOnDamagedSpec onDamaged : spec.behaviors().onDamagedEffects()) {
          UpgradeStatusEffectSpec scaled = scaleStatusEffect(onDamaged.effect(), scale);
          UpgradeOnDamagedSpec scaledSpec = new UpgradeOnDamagedSpec(scaled, onDamaged.cooldownTicks());
          String key = onDamagedKey(record, scaled);
          out.putIfAbsent(key, scaledSpec);
        }
      }
    }
    return out;
  }

  private void clearInventoryModifiers(Player player) {
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
    if (registry == null) {
      return;
    }
    for (Attribute attribute : registry) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance == null) {
        continue;
      }
      List<AttributeModifier> toRemove = new ArrayList<>();
      for (AttributeModifier modifier : instance.getModifiers()) {
        NamespacedKey key = modifier.getKey();
        if (key == null) {
          continue;
        }
        if (!"dungeonsreborn".equalsIgnoreCase(key.getNamespace())) {
          continue;
        }
        if (key.getKey().startsWith(INVENTORY_UPGRADE_PREFIX)) {
          toRemove.add(modifier);
        }
      }
      for (AttributeModifier modifier : toRemove) {
        instance.removeModifier(modifier);
      }
    }
  }

  private boolean hasModifier(AttributeInstance instance, NamespacedKey key) {
    if (instance == null || key == null) {
      return false;
    }
    for (AttributeModifier modifier : instance.getModifiers()) {
      NamespacedKey modifierKey = modifier.getKey();
      if (key.equals(modifierKey)) {
        return true;
      }
    }
    return false;
  }

  private static String onDamagedKey(String recordId, UpgradeStatusEffectSpec effect) {
    if (recordId == null || effect == null) {
      return "unknown";
    }
    String typeKey = effect.type().getKey().toString().toLowerCase(Locale.ROOT);
    return recordId + "|" + typeKey + "|" + effect.amplifier();
  }

  private static long ticksToMillis(long ticks) {
    if (ticks <= 0L) {
      return 0L;
    }
    return ticks * 50L;
  }

  private void applyInventoryStatusEffects(Player player, List<UpgradeStatusEffectSpec> effects) {
    if (effects.isEmpty()) {
      return;
    }
    for (UpgradeStatusEffectSpec spec : effects) {
      double chance = spec.chance() > 1.0 ? spec.chance() / 100.0 : spec.chance();
      if (chance <= 0.0) {
        continue;
      }
      if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) {
        continue;
      }
      int duration = Math.max(spec.durationTicks(), INVENTORY_EFFECT_MIN_TICKS);
      PotionEffect effect = new PotionEffect(spec.type(), duration, spec.amplifier(),
          spec.ambient(), spec.particles(), spec.icon());
      player.addPotionEffect(effect);
    }
  }

  private boolean sameItem(ItemStack before, ItemStack after) {
    if (before == null && after == null) {
      return true;
    }
    if (before == null || after == null) {
      return false;
    }
    return before.equals(after);
  }

  private void stripInventoryActiveAttributes(ItemStack item, List<String> records) {
    if (item == null || records == null || records.isEmpty()) {
      return;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null || !meta.hasAttributeModifiers()) {
      return;
    }
    boolean changed = false;
    for (String record : records) {
      if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
        continue;
      }
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec == null || spec.behaviors() == null || !spec.behaviors().inventoryActive()) {
        continue;
      }
      String prefix = "upgrade_" + sanitizeKeyPart(record.toLowerCase(Locale.ROOT)) + "_";
      for (UpgradeAttributeSpec attr : spec.attributes()) {
        var modifiers = meta.getAttributeModifiers(attr.attribute());
        if (modifiers == null || modifiers.isEmpty()) {
          continue;
        }
        List<AttributeModifier> toRemove = new ArrayList<>();
        for (AttributeModifier modifier : modifiers) {
          NamespacedKey key = modifier.getKey();
          if (key == null) {
            continue;
          }
          if (!"dungeonsreborn".equalsIgnoreCase(key.getNamespace())) {
            continue;
          }
          if (key.getKey().startsWith(prefix)) {
            toRemove.add(modifier);
          }
        }
        for (AttributeModifier modifier : toRemove) {
          meta.removeAttributeModifier(attr.attribute(), modifier);
          changed = true;
        }
      }
    }
    if (changed) {
      item.setItemMeta(meta);
    }
  }

  private ApplyResult applyUpgrade(Player player, ItemStack target, ItemStack upgradeItem, boolean dryRun) {
    if (player == null) {
      return fail("Player required.", dryRun, null, target, upgradeItem, "player");
    }
    if (target == null || target.getType().isAir()) {
      return fail("Place a target item first.", dryRun, player, target, upgradeItem, "target");
    }
    if (upgradeItem == null || upgradeItem.getType().isAir()) {
      return fail("Place an upgrade book first.", dryRun, player, target, upgradeItem, "upgrade");
    }
    ResolvedUpgrade resolved = resolveUpgrade(upgradeItem);
    if (resolved == null) {
      return fail("Not a valid upgrade book.", dryRun, player, target, upgradeItem, "upgrade");
    }
    UpgradeSpec spec = resolved.spec();
    String error = validateLimits(target, spec);
    if (error != null) {
      return fail(error, dryRun, player, target, upgradeItem, "limits");
    }
    error = validateCompatibility(target, spec.compatibility());
    if (error != null) {
      return fail(error, dryRun, player, target, upgradeItem, "compatibility");
    }
    error = validateTarget(player, target, spec.target());
    if (error != null) {
      return fail(error, dryRun, player, target, upgradeItem, "target");
    }
    error = validateProgression(target, spec);
    if (error != null) {
      return fail(error, dryRun, player, target, upgradeItem, "progression");
    }
    error = validateRequirements(player, spec.requirements());
    if (error != null) {
      return fail(error, dryRun, player, target, upgradeItem, "requirements");
    }
    error = validatePrice(player, spec.price());
    if (error != null) {
      return fail(error, dryRun, player, target, upgradeItem, "price");
    }
    if (spec.fusion() != null && spec.fusion().price() != null && !spec.fusion().price().isEmpty()) {
      error = validatePrice(player, spec.fusion().price());
      if (error != null) {
        return fail(error, dryRun, player, target, upgradeItem, "fusion_price");
      }
    }
    boolean activationConflict = false;
    for (UpgradeSpellSpec spell : spec.spells()) {
      if (hasActivationConflict(player, target, spell.activator())) {
        activationConflict = true;
        break;
      }
    }
    String enchantError = validateEnchants(target, spec);
    if (enchantError != null) {
      return fail(enchantError, dryRun, player, target, upgradeItem, "enchant");
    }

    UpgradeFusionSpec fusion = spec.fusion();
    if (!dryRun && fusion != null && fusion.successChance() < 1.0) {
      if (!fusion.price().isEmpty()) {
        consumePrice(player, fusion.price());
      }
      double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
      if (roll > fusion.successChance()) {
        ItemStack failed = fusion.destroyTargetOnFail() ? null : target.clone();
        if (failed != null && fusion.downgradeTo() != null && !fusion.downgradeTo().isBlank()) {
          failed = applyDowngrade(failed, fusion.downgradeTo());
        }
        String playerName = player == null ? "unknown" : player.getName();
        logger.warn("[Upgrades] apply failed: stage=fusion player=" + playerName
            + " target=" + describeItem(target) + " upgrade=" + describeItem(upgradeItem));
        return new ApplyResult(false, "Upgrade failed.", failed, fusion.consumeOnFail(), fusion.destroyTargetOnFail());
      }
    }

    Set<String> consumedRecords = collectConsumedRecords(target, spec);
    double scale = resolveUpgradeScale(player);
    ItemStack updated = applyUpgradeSpec(target, spec, resolved.recordId(), consumedRecords, scale);
    if (updated == null) {
      return fail("Target item has no meta.", dryRun, player, target, upgradeItem, "meta");
    }

    if (!dryRun) {
      consumeRequirements(player, spec.requirements());
      consumePrice(player, spec.price());
      if (fusion != null && fusion.successChance() >= 1.0 && fusion.price() != null && !fusion.price().isEmpty()) {
        consumePrice(player, fusion.price());
      }
      if (activationConflict && !spec.spells().isEmpty()) {
        String activators = spec.spells().stream()
            .map(spell -> spell.activator().name())
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.joining(","));
        logger.warn("[Upgrades] apply warning: overlapping activation slot "
            + activators + " player=" + player.getName()
            + " target=" + describeItem(target) + " upgrade=" + resolved.recordId());
      }
      logApply(player, target, updated, resolved.recordId());
    }
    if (dryRun) {
      return new ApplyResult(true, null, updated, false, false);
    }
    return ApplyResult.success(updated);
  }

  private ApplyResult fail(String error, boolean dryRun, Player player, ItemStack target, ItemStack upgrade, String stage) {
    if (!dryRun) {
      String playerName = player == null ? "unknown" : player.getName();
      logger.warn("[Upgrades] apply failed: stage=" + stage + " player=" + playerName
          + " target=" + describeItem(target) + " upgrade=" + describeItem(upgrade)
          + " reason=" + error);
    }
    return new ApplyResult(false, error, null, false, false);
  }

  private ItemStack removeUpgradeRecord(ItemStack target, String recordId, UpgradeSpec spec) {
    if (target == null || recordId == null || spec == null) {
      return target;
    }
    ItemStack updated = target.clone();
    List<String> records = new ArrayList<>(ItemMarkers.getUpgradeRecords(updated));
    if (!records.remove(recordId)) {
      return updated;
    }
    removeUpgradeEffects(updated, spec, recordId);
    ItemMarkers.setUpgradeRecords(updated, records);
    double scale = ItemMarkers.getUpgradeScale(updated);
    ItemMarkers.setUpgradeModifiers(updated, aggregateModifiers(records, scale));
    ItemMarkers.setStringList(updated, ItemMarkers.UPGRADE_SECONDARY_ABILITIES, aggregateSecondaryAbilities(records));
    ItemMarkers.setUpgradeStatusEffects(updated, aggregateStatusEffects(records, scale));
    UpgradeLore.applyAppliedUpgradeLore(updated, ItemMarkers.getUpgradeRecords(updated), registry);
    logger.info("[Upgrades] remove: target=" + describeItem(target) + " upgrade=" + recordId);
    return updated;
  }

  private String validateSalvageRewards(UpgradeSalvageSpec salvage) {
    if (salvage == null || salvage.isEmpty()) {
      return null;
    }
    UpgradePriceSpec tokens = salvage.tokenRefund();
    if (tokens != null && !tokens.isEmpty()) {
      if (shopRegistry == null || shopRegistry.tokenSpec() == null || shopRegistry.tokenSpec().item() == null) {
        return "Token system not available.";
      }
      if (tokens.compressed() > 0 && shopRegistry.tokenTier("compressed") == null) {
        return "Compressed token tier missing.";
      }
      if (tokens.pallet() > 0 && shopRegistry.tokenTier("pallet") == null) {
        return "Pallet token tier missing.";
      }
    }
    if (!salvage.items().isEmpty() && (shopRegistry == null || shopRegistry.itemResolver() == null)) {
      return "Item resolver not available.";
    }
    for (UpgradeSalvageItemSpec itemSpec : salvage.items()) {
      if (itemSpec == null) {
        continue;
      }
      ItemStack resolved = shopRegistry.itemResolver().apply(itemSpec.itemId());
      if (resolved == null || resolved.getType().isAir()) {
        return "Unknown salvage item: " + itemSpec.itemId();
      }
    }
    return null;
  }

  private List<ItemStack> buildSalvageRewards(UpgradeSalvageSpec salvage) {
    List<ItemStack> rewards = new ArrayList<>();
    if (salvage == null || salvage.isEmpty()) {
      return rewards;
    }
    UpgradePriceSpec tokens = salvage.tokenRefund();
    if (tokens != null && !tokens.isEmpty() && shopRegistry != null && shopRegistry.tokenSpec() != null) {
      if (tokens.normal() > 0) {
        ItemStack base = shopRegistry.tokenSpec().item();
        ItemStack stack = withAmount(base, tokens.normal());
        if (stack != null) {
          rewards.add(stack);
        }
      }
      if (tokens.compressed() > 0) {
        ShopTokenTierSpec tier = shopRegistry.tokenTier("compressed");
        ItemStack stack = withAmount(tier == null ? null : tier.item(), tokens.compressed());
        if (stack != null) {
          rewards.add(stack);
        }
      }
      if (tokens.pallet() > 0) {
        ShopTokenTierSpec tier = shopRegistry.tokenTier("pallet");
        ItemStack stack = withAmount(tier == null ? null : tier.item(), tokens.pallet());
        if (stack != null) {
          rewards.add(stack);
        }
      }
    }
    if (shopRegistry != null && shopRegistry.itemResolver() != null) {
      for (UpgradeSalvageItemSpec itemSpec : salvage.items()) {
        if (itemSpec == null) {
          continue;
        }
        ItemStack base = shopRegistry.itemResolver().apply(itemSpec.itemId());
        ItemStack stack = withAmount(base, itemSpec.amount());
        if (stack != null) {
          rewards.add(stack);
        }
      }
    }
    return rewards;
  }

  private void grantRewards(Player player, List<ItemStack> rewards) {
    if (player == null || rewards == null || rewards.isEmpty()) {
      return;
    }
    for (ItemStack reward : rewards) {
      if (reward == null || reward.getType().isAir()) {
        continue;
      }
      Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
      if (leftover == null || leftover.isEmpty()) {
        continue;
      }
      for (ItemStack drop : leftover.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), drop);
      }
    }
  }

  private static ItemStack withAmount(ItemStack base, int amount) {
    if (base == null || amount <= 0) {
      return null;
    }
    ItemStack out = base.clone();
    out.setAmount(amount);
    return out;
  }

  private ItemStack applyUpgradeSpec(ItemStack target, UpgradeSpec spec, String recordId, Set<String> consumedRecords,
      double scale) {
    ItemStack updated = target.clone();
    ItemMeta meta = updated.getItemMeta();
    if (meta == null) {
      return null;
    }

    if (consumedRecords != null && !consumedRecords.isEmpty()) {
      List<String> records = new ArrayList<>(ItemMarkers.getUpgradeRecords(updated));
      for (String record : consumedRecords) {
        if (!records.remove(record)) {
          continue;
        }
        UpgradeSpec existing = registry.upgradeSpec(record);
        if (existing != null) {
          removeUpgradeEffects(updated, existing, record);
        }
      }
      ItemMarkers.setUpgradeRecords(updated, records);
    }

    boolean inventoryActive = spec.behaviors() != null && spec.behaviors().inventoryActive();
    if (!inventoryActive) {
      applyAttributes(meta, target.getType(), spec, recordId, scale);
    }
    applyEnchants(meta, spec);
    updated.setItemMeta(meta);

    List<String> records = new ArrayList<>(ItemMarkers.getUpgradeRecords(updated));
    if (!spec.spells().isEmpty()) {
      Set<String> removed = new HashSet<>();
      Set<UpgradeActivator> seen = new HashSet<>();
      for (UpgradeSpellSpec spell : spec.spells()) {
        if (seen.add(spell.activator())) {
          removed.addAll(removeUpgradeSpellBindings(updated, spell.activator()));
        }
      }
      if (!removed.isEmpty()) {
        records.removeAll(removed);
      }
      for (UpgradeSpellSpec spell : spec.spells()) {
        applySpellBinding(updated, recordId, spell);
      }
    }
    records = removeConflictingRecords(records, spec);
    records.add(recordId);
    ItemMarkers.setUpgradeRecords(updated, records);
    ItemMarkers.setUpgradeModifiers(updated, aggregateModifiers(records, scale));
    ItemMarkers.setStringList(updated, ItemMarkers.UPGRADE_SECONDARY_ABILITIES, aggregateSecondaryAbilities(records));
    ItemMarkers.setUpgradeStatusEffects(updated, aggregateStatusEffects(records, scale));
    ItemMarkers.setUpgradeScale(updated, scale);
    UpgradeLore.applyAppliedUpgradeLore(updated, ItemMarkers.getUpgradeRecords(updated), registry);
    return updated;
  }

  private void removeUpgradeEffects(ItemStack item, UpgradeSpec spec, String recordId) {
    if (item == null || spec == null || recordId == null) {
      return;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    for (UpgradeEnchantSpec enchantSpec : spec.enchants()) {
      meta.removeEnchant(enchantSpec.enchantment());
    }
    for (UpgradeSpellSpec spell : spec.spells()) {
      removeSpellBinding(item, recordId, spell);
    }
    if (meta.hasAttributeModifiers() && !spec.attributes().isEmpty()) {
      var modifiers = meta.getAttributeModifiers();
      if (modifiers != null) {
        for (UpgradeAttributeSpec attr : spec.attributes()) {
          Attribute attribute = attr.attribute();
          if (attribute == null) {
            continue;
          }
          NamespacedKey key = upgradeModifierKey(recordId, attribute, attr.slotGroup());
          var attrModifiers = modifiers.get(attribute);
          if (attrModifiers == null || attrModifiers.isEmpty()) {
            continue;
          }
          for (AttributeModifier modifier : attrModifiers) {
            if (modifier != null && key.equals(modifier.getKey())) {
              meta.removeAttributeModifier(attribute, modifier);
            }
          }
        }
      }
    }
    item.setItemMeta(meta);
  }

  private Set<String> collectConsumedRecords(ItemStack target, UpgradeSpec spec) {
    UpgradeProgressionSpec progression = spec.progression();
    if (progression == null || progression.isEmpty()) {
      return Set.of();
    }
    List<String> records = ItemMarkers.getUpgradeRecords(target);
    if (records.isEmpty()) {
      return Set.of();
    }
    Set<String> consumed = new HashSet<>();
    for (String id : progression.consumes()) {
      for (String record : records) {
        if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
          continue;
        }
        if (Ids.normalize(record).equals(id)) {
          consumed.add(record);
        }
      }
    }
    if (progression.consumePreviousTier()) {
      String category = progression.category();
      int tier = progression.tier();
      if (category != null && !category.isBlank() && tier > 1) {
        for (String record : records) {
          UpgradeSpec existing = registry.upgradeSpec(record);
          if (existing == null) {
            continue;
          }
          UpgradeLimitsSpec limits = existing.limits();
          if (limits != null && category.equals(limits.category()) && limits.tier() == tier - 1) {
            consumed.add(record);
          }
        }
      }
    }
    return consumed;
  }

  private String validateProgression(ItemStack target, UpgradeSpec spec) {
    UpgradeProgressionSpec progression = spec.progression();
    if (progression == null || progression.isEmpty()) {
      return null;
    }
    List<String> records = ItemMarkers.getUpgradeRecords(target);
    if (!progression.requires().isEmpty()) {
      for (String required : progression.requires()) {
        boolean found = false;
        for (String record : records) {
          if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
            continue;
          }
          if (Ids.normalize(record).equals(required)) {
            found = true;
            break;
          }
        }
        if (!found) {
          return "Missing required upgrade: " + required;
        }
      }
    }
    if (progression.requirePreviousTier()) {
      String category = progression.category();
      int tier = progression.tier();
      if (category == null || category.isBlank()) {
        return "Upgrade requires a category for tier progression.";
      }
      if (tier > 1) {
        boolean found = false;
        for (String record : records) {
          UpgradeSpec existing = registry.upgradeSpec(record);
          if (existing == null) {
            continue;
          }
          UpgradeLimitsSpec limits = existing.limits();
          if (limits != null && category.equals(limits.category()) && limits.tier() == tier - 1) {
            found = true;
            break;
          }
        }
        if (!found) {
          return "Missing previous tier upgrade for category " + category + ".";
        }
      }
    }
    return null;
  }

  private ItemStack applyDowngrade(ItemStack target, String downgradeTo) {
    UpgradeTemplate template = registry.upgradeTemplate(downgradeTo);
    if (template == null) {
      return target;
    }
    UpgradeSpec downgrade = template.spec();
    Set<String> consumed = Set.of();
    double scale = ItemMarkers.getUpgradeScale(target);
    return applyUpgradeSpec(target, downgrade, downgrade.id(), consumed, scale);
  }

  private void logApply(Player player, ItemStack before, ItemStack after, String recordId) {
    if (player == null) {
      return;
    }
    logger.info("[Upgrades] apply: player=" + player.getName()
        + " target=" + describeItem(before)
        + " upgrade=" + recordId
        + " result=" + describeItem(after));
  }

  private String describeItem(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return "empty";
    }
    String itemId = ItemMarkers.getItemId(item);
    StringBuilder out = new StringBuilder();
    if (itemId != null && !itemId.isBlank()) {
      out.append(itemId).append('/');
    }
    out.append(item.getType().name().toLowerCase(Locale.ROOT));
    if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
      String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
          .serialize(item.getItemMeta().displayName());
      if (name != null && !name.isBlank()) {
        out.append(" \"").append(name.replace('\n', ' ')).append('"');
      }
    }
    return out.toString();
  }

  private ResolvedUpgrade resolveUpgrade(ItemStack upgradeItem) {
    String id = ItemMarkers.getUpgradeId(upgradeItem);
    if (id != null) {
      UpgradeSpec spec = registry.upgradeSpec(id);
      if (spec == null) {
        return null;
      }
      return new ResolvedUpgrade(spec, spec.id());
    }
    if (upgradeItem.getType() != org.bukkit.Material.ENCHANTED_BOOK) {
      return null;
    }
    ItemMeta meta = upgradeItem.getItemMeta();
    if (!(meta instanceof EnchantmentStorageMeta stored) || !stored.hasStoredEnchants()) {
      return null;
    }
    Set<String> blacklist = vanillaEnchantBlacklist();
    boolean allowUnsafeVanilla = allowUnsafeVanillaBooks();
    List<UpgradeEnchantSpec> enchants = new ArrayList<>();
    for (var entry : stored.getStoredEnchants().entrySet()) {
      Enchantment enchant = entry.getKey();
      if (enchant == null) {
        continue;
      }
      NamespacedKey key = enchant.getKey();
      if (key == null) {
        continue;
      }
      if (!isRegistryEnchantment(key)) {
        continue;
      }
      String keyString = key.toString().toLowerCase(Locale.ROOT);
      if (blacklist.contains(keyString)) {
        continue;
      }
      enchants.add(new UpgradeEnchantSpec(enchant, entry.getValue()));
    }
    if (enchants.isEmpty()) {
      return null;
    }
    UpgradeSpec spec = new UpgradeSpec("vanilla_enchanted_book", "Vanilla Enchanted Book", "",
        UpgradeRequirements.none(), UpgradePriceSpec.none(), UpgradeTargetSpec.none(), UpgradeCompatibilitySpec.none(),
        UpgradeLimitsSpec.none(), UpgradeProgressionSpec.none(), UpgradeFusionSpec.none(), UpgradeSalvageSpec.none(),
        UpgradeBehaviorSpec.none(), allowUnsafeVanilla, List.of(), List.of(), enchants, List.of());
    return new ResolvedUpgrade(spec, encodeVanillaRecord(enchants));
  }

  private String validateRequirements(Player player, UpgradeRequirements requirements) {
    if (requirements == null) {
      return null;
    }
    boolean xpGatingEnabled = plugin.getConfig().getBoolean("upgrades.xpGating.enabled", true);
    String bypassPermission = plugin.getConfig().getString("upgrades.xpGating.bypassPermission", "");
    boolean bypass = !bypassPermission.isBlank() && player.hasPermission(bypassPermission);
    int level = resolveXpLevel(player);
    if (xpGatingEnabled && !bypass) {
      if (requirements.minXp() > 0 && level < requirements.minXp()) {
        return Locales.text(player, "labels.upgrades.requirements.xpLevelMin",
            Locales.placeholders("level", requirements.minXp()));
      }
      if (requirements.consumeXp() > 0 && level < requirements.consumeXp()) {
        return Locales.text(player, "labels.upgrades.requirements.xpLevelConsume",
            Locales.placeholders("level", requirements.consumeXp()));
      }
      int totalXp = resolveTotalXp(player);
      if (requirements.minTotalXp() > 0 && totalXp < requirements.minTotalXp()) {
        return Locales.text(player, "labels.upgrades.requirements.totalXpMin",
            Locales.placeholders("xp", requirements.minTotalXp()));
      }
      if (requirements.consumeTotalXp() > 0 && totalXp < requirements.consumeTotalXp()) {
        return Locales.text(player, "labels.upgrades.requirements.totalXpConsume",
            Locales.placeholders("xp", requirements.consumeTotalXp()));
      }
      double progress = resolveXpProgress(player);
      if (requirements.minProgress() > 0.0 && progress + 1e-9 < requirements.minProgress()) {
        return Locales.text(player, "labels.upgrades.requirements.xpProgressMin",
            Locales.placeholders("percent", formatPercent(requirements.minProgress())));
      }
      if (requirements.consumeProgress() > 0.0 && progress + 1e-9 < requirements.consumeProgress()) {
        return Locales.text(player, "labels.upgrades.requirements.xpProgressConsume",
            Locales.placeholders("percent", formatPercent(requirements.consumeProgress())));
      }
    }
    if (requirements.minMaxMana() > 0.0) {
      ManaProvider provider = engine.manaProvider();
      if (provider == null) {
        return Locales.text(player, "labels.upgrades.requirements.manaMissing");
      }
      double max = provider.getMax(player);
      if (max + 1e-9 < requirements.minMaxMana()) {
        return Locales.text(player, "labels.upgrades.requirements.manaMin",
            Locales.placeholders("mana", format(requirements.minMaxMana())));
      }
    }
    return null;
  }

  private String validatePrice(Player player, UpgradePriceSpec price) {
    if (price == null || price.isEmpty()) {
      return null;
    }
    if (shopRegistry == null || shopRegistry.tokenSpec() == null || shopRegistry.tokenSpec().markerKey() == null) {
      return "Token system not available.";
    }
    ShopTokenTierSpec compressed = shopRegistry.tokenTier("compressed");
    ShopTokenTierSpec pallet = shopRegistry.tokenTier("pallet");
    if (price.compressed() > 0 && (compressed == null || compressed.markerKey() == null)) {
      return "Compressed token tier is not configured.";
    }
    if (price.pallet() > 0 && (pallet == null || pallet.markerKey() == null)) {
      return "Pallet token tier is not configured.";
    }
    int haveNormal = countTokens(player, shopRegistry.tokenSpec().markerKey());
    int haveCompressed = countTokens(player, compressed == null ? null : compressed.markerKey());
    int havePallet = countTokens(player, pallet == null ? null : pallet.markerKey());
    if (haveNormal >= price.normal() && haveCompressed >= price.compressed() && havePallet >= price.pallet()) {
      return null;
    }
    StringBuilder missing = new StringBuilder();
    if (price.pallet() > havePallet) {
      missing.append(price.pallet() - havePallet).append(" pallet");
    }
    if (price.compressed() > haveCompressed) {
      if (!missing.isEmpty()) {
        missing.append(", ");
      }
      missing.append(price.compressed() - haveCompressed).append(" compressed");
    }
    if (price.normal() > haveNormal) {
      if (!missing.isEmpty()) {
        missing.append(", ");
      }
      missing.append(price.normal() - haveNormal).append(" tokens");
    }
    if (missing.isEmpty()) {
      return "Missing tokens.";
    }
    return "Missing tokens: " + missing;
  }

  private String validateCompatibility(ItemStack target, UpgradeCompatibilitySpec compatibility) {
    if (compatibility == null || compatibility.isEmpty()) {
      return null;
    }
    String itemId = ItemMarkers.getItemId(target);
    Material material = target.getType();
    if (!compatibility.denyItemIds().isEmpty() && itemId != null && compatibility.denyItemIds().contains(itemId)) {
      return "That item is not compatible with this upgrade.";
    }
    if (!compatibility.denyMaterials().isEmpty() && compatibility.denyMaterials().contains(material)) {
      return "That item material is not compatible with this upgrade.";
    }
    if (!compatibility.allowItemIds().isEmpty()) {
      if (itemId == null || !compatibility.allowItemIds().contains(itemId)) {
        return "This upgrade can only be applied to specific items.";
      }
    }
    if (!compatibility.allowMaterials().isEmpty() && !compatibility.allowMaterials().contains(material)) {
      return "This upgrade can only be applied to specific materials.";
    }
    if (!compatibility.incompatibilityGroups().isEmpty()) {
      List<String> records = ItemMarkers.getUpgradeRecords(target);
      for (String record : records) {
        if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
          continue;
        }
        UpgradeSpec existing = registry.upgradeSpec(record);
        if (existing == null) {
          continue;
        }
        UpgradeCompatibilitySpec existingCompat = existing.compatibility();
        if (existingCompat == null || existingCompat.incompatibilityGroups().isEmpty()) {
          continue;
        }
        if (!sharesGroup(compatibility.incompatibilityGroups(), existingCompat.incompatibilityGroups())) {
          continue;
        }
        if (compatibility.priority() <= existingCompat.priority()) {
          return "This upgrade conflicts with an existing upgrade group.";
        }
      }
    }
    return null;
  }

  private boolean sharesGroup(Set<String> left, Set<String> right) {
    if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
      return false;
    }
    for (String group : left) {
      if (right.contains(group)) {
        return true;
      }
    }
    return false;
  }

  private String validateLimits(ItemStack target, UpgradeSpec incoming) {
    if (incoming == null) {
      return null;
    }
    UpgradeLimitsSpec limits = incoming.limits();
    if (limits == null || limits.isEmpty()) {
      return null;
    }
    java.util.Map<String, Integer> slotCaps = ItemMarkers.getUpgradeSlots(target);
    int maxUpgrades = ItemMarkers.getUpgradeMaxCount(target);
    int tierBudget = ItemMarkers.getUpgradeTierBudget(target);
    List<String> records = ItemMarkers.getUpgradeRecords(target);
    int existingCount = 0;
    int maxTierFound = 0;
    int tierTotal = 0;
    int copies = 0;
    String incomingId = Ids.normalize(incoming.id());
    java.util.Map<String, Integer> usedSlots = new java.util.HashMap<>();
    for (String record : records) {
      if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
        continue;
      }
      if (Ids.normalize(record).equals(incomingId)) {
        copies++;
      }
      UpgradeSpec existing = registry.upgradeSpec(record);
      if (existing == null) {
        continue;
      }
      existingCount++;
      UpgradeLimitsSpec existingLimits = existing.limits();
      if (existingLimits == null) {
        continue;
      }
      tierTotal += Math.max(1, existingLimits.tier());
      String existingSlot = slotTypeFrom(existingLimits);
      usedSlots.merge(existingSlot, 1, (left, right) -> (left == null ? 0 : left) + (right == null ? 0 : right));
      if (limits.category() != null && existingLimits.category() != null
          && limits.category().equals(existingLimits.category())) {
        if (limits.exclusive() || existingLimits.exclusive()) {
          return "This upgrade is exclusive with an existing upgrade.";
        }
        maxTierFound = Math.max(maxTierFound, existingLimits.tier());
        if (existingLimits.maxTier() > 0 && limits.tier() > existingLimits.maxTier()) {
          return "This upgrade exceeds the allowed tier for that category.";
        }
      }
    }
    if (limits.maxPerItem() > 0 && existingCount >= limits.maxPerItem()) {
      return "This item already has the maximum number of upgrades.";
    }
    if (limits.maxCopies() > 0 && copies >= limits.maxCopies()) {
      return "This item already has the maximum copies of that upgrade.";
    }
    if (maxUpgrades > 0 && existingCount >= maxUpgrades) {
      return "This item already has the maximum number of upgrades.";
    }
    if (limits.maxTier() > 0) {
      int nextTier = Math.max(maxTierFound, limits.tier());
      if (nextTier > limits.maxTier()) {
        return "This upgrade exceeds the maximum tier for that category.";
      }
    }
    if (!slotCaps.isEmpty()) {
      String slotType = slotTypeFrom(limits);
      Integer cap = slotCaps.get(slotType);
      if (cap == null) {
        cap = slotCaps.get("any");
      }
      if (cap == null || cap <= 0) {
        return "This item has no available upgrade slots for that type.";
      }
      int used = usedSlots.getOrDefault(slotType, 0);
      if (used >= cap) {
        return "This item has no available upgrade slots for that type.";
      }
    }
    if (tierBudget > 0) {
      int nextTotal = tierTotal + Math.max(1, limits.tier());
      if (nextTotal > tierBudget) {
        return "This upgrade exceeds the item tier budget.";
      }
    }
    return null;
  }

  private String slotTypeFrom(UpgradeLimitsSpec limits) {
    if (limits == null) {
      return "default";
    }
    String raw = limits.category();
    if (raw == null || raw.isBlank()) {
      return "default";
    }
    return Ids.normalize(raw);
  }

  private String validateTarget(Player player, ItemStack target, UpgradeTargetSpec targetSpec) {
    if (targetSpec == null || targetSpec.isEmpty()) {
      return null;
    }
    if (!targetSpec.itemIds().isEmpty()) {
      String itemId = ItemMarkers.getItemId(target);
      for (String id : targetSpec.itemIds()) {
        if (id.equalsIgnoreCase(itemId)) {
          itemId = null;
          break;
        }
      }
      if (itemId != null) {
        return "This upgrade requires a specific item type.";
      }
    }
    if (!targetSpec.itemTags().isEmpty()) {
      List<String> tags = ItemMarkers.getItemTags(target);
      boolean matched = false;
      for (String tag : targetSpec.itemTags()) {
        if (tags.contains(tag)) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        return "This upgrade requires a specific item tag.";
      }
    }
    if (!targetSpec.itemCategories().isEmpty()) {
      String category = ItemMarkers.getItemCategory(target);
      boolean matched = false;
      for (String value : targetSpec.itemCategories()) {
        if (value.equalsIgnoreCase(category)) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        return "This upgrade requires a specific item category.";
      }
    }
    var abilityIds = collectAbilityIds(player, target);
    if (!targetSpec.abilityIds().isEmpty()) {
      for (String id : targetSpec.abilityIds()) {
        if (abilityIds.contains(id)) {
          return null;
        }
      }
      return "This upgrade requires a specific spell ability.";
    }
    if (!targetSpec.abilityTags().isEmpty()) {
      for (String tag : targetSpec.abilityTags()) {
        for (String ability : abilityIds) {
          if (matchesTag(ability, tag)) {
            return null;
          }
        }
      }
      return "This upgrade requires a specific spell tag.";
    }
    return null;
  }

  private void consumeRequirements(Player player, UpgradeRequirements requirements) {
    if (requirements == null) {
      return;
    }
    double consumeProgress = requirements.consumeProgress();
    if (customXpService != null) {
      UUID uuid = player.getUniqueId();
      if (consumeProgress > 0.0) {
        int points = customXpService.pointsForProgress(uuid, consumeProgress);
        if (points > 0) {
          customXpService.removeXp(player, points);
        }
      }
      int consumeTotalXp = requirements.consumeTotalXp();
      if (consumeTotalXp > 0) {
        customXpService.removeXp(player, consumeTotalXp);
      }
      int consume = requirements.consumeXp();
      if (consume > 0) {
        CustomXpProfile profile = customXpService.getOrCreate(uuid);
        int targetLevel = Math.max(1, profile.level() - consume);
        int targetTotal = customXpService.totalForLevel(targetLevel);
        long diff = profile.points() - targetTotal;
        if (diff > 0L) {
          customXpService.removeXp(player, (int) Math.min(Integer.MAX_VALUE, diff));
        }
      }
      return;
    }
    if (consumeProgress > 0.0) {
      int points = Math.round((float) (consumeProgress * player.getExpToLevel()));
      if (points > 0) {
        player.giveExp(-points);
      }
    }
    int consumeTotalXp = requirements.consumeTotalXp();
    if (consumeTotalXp > 0) {
      player.giveExp(-consumeTotalXp);
    }
    int consume = requirements.consumeXp();
    if (consume > 0) {
      player.giveExpLevels(-consume);
    }
  }

  private int resolveXpLevel(Player player) {
    if (customXpService == null || player == null) {
      return player == null ? 1 : player.getLevel();
    }
    CustomXpProfile profile = customXpService.getOrCreate(player.getUniqueId());
    return profile == null ? player.getLevel() : profile.level();
  }

  private int resolveTotalXp(Player player) {
    if (customXpService == null || player == null) {
      return player == null ? 0 : player.getTotalExperience();
    }
    CustomXpProfile profile = customXpService.getOrCreate(player.getUniqueId());
    long points = profile == null ? 0L : profile.points();
    return (int) Math.min(Integer.MAX_VALUE, points);
  }

  private double resolveXpProgress(Player player) {
    if (customXpService == null || player == null) {
      return player == null ? 0.0 : player.getExp();
    }
    return customXpService.progress(player.getUniqueId());
  }

  private void consumePrice(Player player, UpgradePriceSpec price) {
    if (price == null || price.isEmpty() || shopRegistry == null || player == null) {
      return;
    }
    consumeTokens(player, shopRegistry.tokenSpec().markerKey(), price.normal());
    ShopTokenTierSpec compressed = shopRegistry.tokenTier("compressed");
    if (compressed != null) {
      consumeTokens(player, compressed.markerKey(), price.compressed());
    }
    ShopTokenTierSpec pallet = shopRegistry.tokenTier("pallet");
    if (pallet != null) {
      consumeTokens(player, pallet.markerKey(), price.pallet());
    }
  }

  private int countTokens(Player player, NamespacedKey markerKey) {
    if (player == null || markerKey == null) {
      return 0;
    }
    int total = 0;
    for (ItemStack stack : player.getInventory().getContents()) {
      if (ItemMarkers.has(stack, markerKey)) {
        total += stack.getAmount();
      }
    }
    return total;
  }

  private void consumeTokens(Player player, NamespacedKey markerKey, int amount) {
    if (player == null || markerKey == null || amount <= 0) {
      return;
    }
    PlayerInventory inventory = player.getInventory();
    ItemStack[] contents = inventory.getContents();
    int remaining = amount;
    for (int i = 0; i < contents.length; i++) {
      ItemStack stack = contents[i];
      if (!ItemMarkers.has(stack, markerKey)) {
        continue;
      }
      int take = Math.min(remaining, stack.getAmount());
      int next = stack.getAmount() - take;
      if (next <= 0) {
        contents[i] = null;
      } else {
        stack.setAmount(next);
        contents[i] = stack;
      }
      remaining -= take;
      if (remaining <= 0) {
        break;
      }
    }
    inventory.setContents(contents);
  }

  private String validateEnchants(ItemStack target, UpgradeSpec spec) {
    if (spec.enchants().isEmpty()) {
      return null;
    }
    ItemMeta meta = target.getItemMeta();
    if (meta == null) {
      return "Target item has no meta.";
    }
    boolean allowUnsafe = spec.allowUnsafe();
    Map<Enchantment, Integer> existing = new LinkedHashMap<>(meta.getEnchants());
    for (UpgradeEnchantSpec enchantSpec : spec.enchants()) {
      Enchantment enchant = enchantSpec.enchantment();
      int level = enchantSpec.level();
      if (!allowUnsafe) {
        int min = enchant.getStartLevel();
        int max = enchant.getMaxLevel();
        if (level < min || level > max) {
          return "Upgrade enchant level out of range for " + enchant.getKey().getKey() + ".";
        }
      }
      if (!allowUnsafe && !enchant.canEnchantItem(target)) {
        return "Upgrade enchant cannot be applied to this item.";
      }
      for (Enchantment other : existing.keySet()) {
        if (other.equals(enchant)) {
          continue;
        }
        if (!allowUnsafe && enchant.conflictsWith(other)) {
          return "Upgrade enchant conflicts with existing enchantments.";
        }
      }
      existing.put(enchant, enchantSpec.level());
    }
    return null;
  }

  private void applyEnchants(ItemMeta meta, UpgradeSpec spec) {
    boolean allowUnsafe = spec.allowUnsafe();
    for (UpgradeEnchantSpec enchantSpec : spec.enchants()) {
      Enchantment enchant = enchantSpec.enchantment();
      if (meta.hasEnchant(enchant)) {
        meta.removeEnchant(enchant);
      }
      meta.addEnchant(enchant, enchantSpec.level(), allowUnsafe);
    }
  }

  private void applyAttributes(ItemMeta meta, Material material, UpgradeSpec spec, String recordId, double scale) {
    if (spec.attributes().isEmpty()) {
      return;
    }
    ensureDefaultAttributes(meta, material);
    HashSet<Attribute> cleared = new HashSet<>();
    for (UpgradeAttributeSpec attr : spec.attributes()) {
      if (cleared.add(attr.attribute())) {
        removeUpgradeAttributeModifiers(meta, attr.attribute());
      }
      NamespacedKey key = upgradeModifierKey(recordId, attr.attribute(), attr.slotGroup());
      double amount = attr.amount() * scale;
      AttributeModifier modifier = new AttributeModifier(key, amount, attr.operation(), attr.slotGroup());
      meta.addAttributeModifier(attr.attribute(), modifier);
    }
  }

  private void removeUpgradeAttributeModifiers(ItemMeta meta, Attribute attribute) {
    var modifiers = meta.getAttributeModifiers(attribute);
    if (modifiers == null || modifiers.isEmpty()) {
      return;
    }
    List<AttributeModifier> toRemove = new ArrayList<>();
    for (AttributeModifier modifier : modifiers) {
      NamespacedKey key = modifier.getKey();
      if (key == null) {
        continue;
      }
      if (!"dungeonsreborn".equalsIgnoreCase(key.getNamespace())) {
        continue;
      }
      if (key.getKey().startsWith("upgrade_")) {
        toRemove.add(modifier);
      }
    }
    for (AttributeModifier modifier : toRemove) {
      meta.removeAttributeModifier(attribute, modifier);
    }
  }

  private void ensureDefaultAttributes(ItemMeta meta, Material material) {
    if (meta == null || material == null) {
      return;
    }
    var existing = meta.getAttributeModifiers();
    if (existing != null && !existing.isEmpty()) {
      return;
    }
    var defaults = material.getDefaultAttributeModifiers();
    if (defaults == null || defaults.isEmpty()) {
      return;
    }
    for (var entry : defaults.entries()) {
      meta.addAttributeModifier(entry.getKey(), entry.getValue());
    }
  }

  private static NamespacedKey upgradeModifierKey(String upgradeId, Attribute attribute, EquipmentSlotGroup slot) {
    String base = sanitizeKeyPart((upgradeId == null ? "upgrade" : upgradeId).toLowerCase(Locale.ROOT));
    String attr = attribute.getKey().getKey().toLowerCase(Locale.ROOT);
    String slotName = slot.toString().toLowerCase(Locale.ROOT);
    return new NamespacedKey("dungeonsreborn", "upgrade_" + base + "_" + attr + "_" + slotName);
  }

  private static NamespacedKey inventoryModifierKey(String upgradeId, Attribute attribute, int index) {
    String base = sanitizeKeyPart((upgradeId == null ? "upgrade" : upgradeId).toLowerCase(Locale.ROOT));
    String attr = attribute.getKey().getKey().toLowerCase(Locale.ROOT);
    return new NamespacedKey("dungeonsreborn", INVENTORY_UPGRADE_PREFIX + base + "_" + attr + "_" + index);
  }

  private static String sanitizeKeyPart(String raw) {
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
      out.append(ok ? c : '_');
    }
    return out.toString();
  }

  private void applySpellBinding(ItemStack item, String upgradeId, UpgradeSpellSpec spell) {
    String id = spell.abilityId();
    switch (spell.activator()) {
      case RIGHT_CLICK -> ItemMarkers.addToStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES, id);
      case LEFT_CLICK -> ItemMarkers.addToStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES, id);
      case SHIFT_RIGHT_CLICK -> ItemMarkers.addToStringList(item, ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES, id);
      case SHIFT_LEFT_CLICK -> ItemMarkers.addToStringList(item, ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES, id);
      case PASSIVE -> ItemMarkers.addToStringList(item, ItemMarkers.PASSIVE_ABILITIES, id);
    }
    if (upgradeId != null && !upgradeId.isBlank()) {
      List<String> records = new ArrayList<>(ItemMarkers.getUpgradeSpellBindings(item));
      UpgradeSpellBindingSpec binding = new UpgradeSpellBindingSpec(
          upgradeId,
          id,
          spell.activator(),
          spell.cooldownTicks(),
          spell.manaCost(),
          spell.durabilityCost(),
          spell.consumeAmount(),
          spell.cooldownScope(),
          spell.requireSneaking(),
          spell.requireSprinting(),
          spell.requireAirborne(),
          spell.requireOnGround());
      String record = binding.toRecord();
      if (!records.contains(record)) {
        records.add(record);
      }
      ItemMarkers.setUpgradeSpellBindings(item, records);
    }
  }

  private void removeSpellBinding(ItemStack item, String upgradeId, UpgradeSpellSpec spell) {
    String id = spell.abilityId();
    switch (spell.activator()) {
      case RIGHT_CLICK -> ItemMarkers.removeFromStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES, id);
      case LEFT_CLICK -> ItemMarkers.removeFromStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES, id);
      case SHIFT_RIGHT_CLICK -> ItemMarkers.removeFromStringList(item, ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES, id);
      case SHIFT_LEFT_CLICK -> ItemMarkers.removeFromStringList(item, ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES, id);
      case PASSIVE -> ItemMarkers.removeFromStringList(item, ItemMarkers.PASSIVE_ABILITIES, id);
    }
    if (upgradeId == null || upgradeId.isBlank()) {
      return;
    }
    List<String> records = ItemMarkers.getUpgradeSpellBindings(item);
    if (records.isEmpty()) {
      return;
    }
    List<String> updated = new ArrayList<>(records.size());
    for (String record : records) {
      UpgradeSpellBindingSpec spec = UpgradeSpellBindingSpec.fromRecord(record);
      if (spec == null) {
        updated.add(record);
        continue;
      }
      if (spec.matchesUpgrade(upgradeId)
          && spec.abilityId().equals(id)
          && spec.activator() == spell.activator()) {
        continue;
      }
      updated.add(record);
    }
    ItemMarkers.setUpgradeSpellBindings(item, updated);
  }

  private List<String> removeUpgradeSpellBindings(ItemStack item, UpgradeActivator activator) {
    if (item == null || activator == null) {
      return List.of();
    }
    List<String> records = ItemMarkers.getUpgradeRecords(item);
    if (records.isEmpty()) {
      return List.of();
    }
    List<String> removed = new ArrayList<>();
    for (String record : records) {
      if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
        continue;
      }
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec == null || spec.spells().isEmpty()) {
        continue;
      }
      boolean matches = false;
      for (UpgradeSpellSpec spell : spec.spells()) {
        if (spell.activator() == activator) {
          matches = true;
          break;
        }
      }
      if (!matches) {
        continue;
      }
      for (UpgradeSpellSpec spell : spec.spells()) {
        removeSpellBinding(item, record, spell);
      }
      removed.add(record);
    }
    return removed;
  }

  public boolean hasActivationConflict(Player player, ItemStack item, UpgradeActivator activator) {
    switch (activator) {
      case RIGHT_CLICK -> {
        if (!ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES).isEmpty()) {
          return true;
        }
        if (hasUpgradeSpellBinding(item, activator)) {
          return true;
        }
        return hasInteractBindingConflict(player, item, InteractTrigger.RIGHT_CLICK, false);
      }
      case LEFT_CLICK -> {
        if (!ItemMarkers.getStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES).isEmpty()) {
          return true;
        }
        if (hasUpgradeSpellBinding(item, activator)) {
          return true;
        }
        return hasInteractBindingConflict(player, item, InteractTrigger.LEFT_CLICK, false);
      }
      case SHIFT_RIGHT_CLICK -> {
        if (!ItemMarkers.getStringList(item, ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES).isEmpty()) {
          return true;
        }
        if (hasUpgradeSpellBinding(item, activator)) {
          return true;
        }
        return hasInteractBindingConflict(player, item, InteractTrigger.RIGHT_CLICK, true);
      }
      case SHIFT_LEFT_CLICK -> {
        if (!ItemMarkers.getStringList(item, ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES).isEmpty()) {
          return true;
        }
        if (hasUpgradeSpellBinding(item, activator)) {
          return true;
        }
        return hasInteractBindingConflict(player, item, InteractTrigger.LEFT_CLICK, true);
      }
      case PASSIVE -> {
        if (!ItemMarkers.getStringList(item, ItemMarkers.PASSIVE_ABILITIES).isEmpty()) {
          return true;
        }
        if (hasUpgradeSpellBinding(item, activator)) {
          return true;
        }
        for (PassiveBinding binding : bindings.passiveBindings()) {
          if (binding.itemMatcher().matches(player, item)) {
            return true;
          }
        }
        return false;
      }
    }
    return false;
  }

  private LinkedHashSet<String> collectAbilityIds(Player player, ItemStack item) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    out.addAll(ItemMarkers.getStringList(item, ItemMarkers.RIGHT_CLICK_ABILITIES));
    out.addAll(ItemMarkers.getStringList(item, ItemMarkers.LEFT_CLICK_ABILITIES));
    out.addAll(ItemMarkers.getStringList(item, ItemMarkers.SHIFT_RIGHT_CLICK_ABILITIES));
    out.addAll(ItemMarkers.getStringList(item, ItemMarkers.SHIFT_LEFT_CLICK_ABILITIES));
    out.addAll(ItemMarkers.getStringList(item, ItemMarkers.PASSIVE_ABILITIES));
    for (InteractBinding binding : bindings.interactBindings()) {
      if (binding.itemMatcher().matches(player, item)) {
        out.add(binding.abilityId());
      }
    }
    for (PassiveBinding binding : bindings.passiveBindings()) {
      if (binding.itemMatcher().matches(player, item)) {
        out.add(binding.abilityId());
      }
    }
    if (!out.isEmpty()) {
      out.removeAll(collectUpgradeSpellIds(item));
    }
    return out;
  }

  private Set<String> collectUpgradeSpellIds(ItemStack item) {
    List<String> records = ItemMarkers.getUpgradeRecords(item);
    if (records.isEmpty()) {
      return Set.of();
    }
    Set<String> out = new HashSet<>();
    for (String record : records) {
      if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
        continue;
      }
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec == null || spec.spells().isEmpty()) {
        continue;
      }
      for (UpgradeSpellSpec spell : spec.spells()) {
        out.add(spell.abilityId());
      }
    }
    return out;
  }

  private boolean hasUpgradeSpellBinding(ItemStack item, UpgradeActivator activator) {
    List<UpgradeSpellBindingSpec> specs = UpgradeSpellBindingSpec.parseRecords(
        ItemMarkers.getUpgradeSpellBindings(item));
    if (specs.isEmpty()) {
      return false;
    }
    for (UpgradeSpellBindingSpec spec : specs) {
      if (spec.activator() == activator) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesTag(String abilityId, String tag) {
    if (abilityId == null || tag == null) {
      return false;
    }
    if (abilityId.equals(tag)) {
      return true;
    }
    return abilityId.startsWith(tag + "_") || abilityId.startsWith(tag + ".");
  }

  private boolean hasInteractBindingConflict(Player player, ItemStack item, InteractTrigger trigger, boolean sneaking) {
    for (InteractBinding binding : bindings.interactBindings()) {
      if (binding.trigger() != trigger) {
        continue;
      }
      if (binding.requireSneaking() != sneaking) {
        continue;
      }
      if (binding.itemMatcher().matches(player, item)) {
        return true;
      }
    }
    return false;
  }

  private List<String> removeConflictingRecords(List<String> records, UpgradeSpec incoming) {
    if (records.isEmpty()) {
      return records;
    }
    List<String> out = new ArrayList<>();
    for (String record : records) {
      if (record == null || record.isBlank()) {
        continue;
      }
    UpgradeSpec spec = null;
    Map<String, Integer> vanilla = null;
      if (record.startsWith("vanilla:")) {
        vanilla = parseVanillaRecord(record);
      } else {
        spec = registry.upgradeSpec(record);
      }
      if (conflicts(incoming, spec, vanilla)) {
        if (incoming != null) {
          logger.info("[Upgrades] conflict removed: incoming=" + incoming.id() + " removed=" + record);
        }
        continue;
      }
      out.add(record);
    }
    return out;
  }

  private boolean conflicts(UpgradeSpec incoming, UpgradeSpec existing, Map<String, Integer> vanilla) {
    if (incoming == null) {
      return false;
    }
    if (incoming != null && !incoming.spells().isEmpty()) {
      // Spell activators can overlap; do not treat as a hard conflict.
    }
    if (!incoming.enchants().isEmpty()) {
      for (UpgradeEnchantSpec enchantSpec : incoming.enchants()) {
        if (existing != null) {
          for (UpgradeEnchantSpec other : existing.enchants()) {
            if (other.enchantment().equals(enchantSpec.enchantment())) {
              return true;
            }
          }
        }
        if (vanilla != null && vanilla.containsKey(enchantSpec.enchantment().getKey().toString())) {
          return true;
        }
      }
    }
    if (!incoming.modifiers().isEmpty() && existing != null) {
      for (UpgradeModifierSpec modifier : incoming.modifiers()) {
        for (UpgradeModifierSpec other : existing.modifiers()) {
          if (other.type() == modifier.type()) {
            return true;
          }
        }
      }
    }
    if (!incoming.behaviors().statusEffects().isEmpty() && existing != null) {
      for (UpgradeStatusEffectSpec effect : incoming.behaviors().statusEffects()) {
        for (UpgradeStatusEffectSpec other : existing.behaviors().statusEffects()) {
          if (other.type().equals(effect.type())) {
            return true;
          }
        }
      }
    }
    if (!incoming.behaviors().inventoryEffects().isEmpty() && existing != null) {
      for (UpgradeStatusEffectSpec effect : incoming.behaviors().inventoryEffects()) {
        for (UpgradeStatusEffectSpec other : existing.behaviors().inventoryEffects()) {
          if (other.type().equals(effect.type())) {
            return true;
          }
        }
      }
    }
    if (!incoming.behaviors().onDamagedEffects().isEmpty() && existing != null) {
      for (UpgradeOnDamagedSpec effect : incoming.behaviors().onDamagedEffects()) {
        for (UpgradeOnDamagedSpec other : existing.behaviors().onDamagedEffects()) {
          if (other.effect().type().equals(effect.effect().type())) {
            return true;
          }
        }
      }
    }
    if (!incoming.attributes().isEmpty() && existing != null) {
      for (UpgradeAttributeSpec attr : incoming.attributes()) {
        for (UpgradeAttributeSpec other : existing.attributes()) {
          if (other.attribute().equals(attr.attribute())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private Map<String, Double> aggregateModifiers(List<String> records, double scale) {
    if (records == null || records.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, Double> out = new LinkedHashMap<>();
    Map<String, Integer> stacks = new HashMap<>();
    for (String record : records) {
      if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
        continue;
      }
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec == null) {
        continue;
      }
      UpgradeLimitsSpec limits = spec.limits();
      double factor = limits == null ? 1.0 : limits.diminishingFactor();
      double floor = limits == null ? 0.0 : limits.diminishingFloor();
      for (UpgradeModifierSpec modifier : spec.modifiers()) {
        String key = modifier.type().key();
        int index = stacks.getOrDefault(key, 0);
        stacks.put(key, index + 1);
        double multiplier = 1.0;
        if (factor != 1.0) {
          multiplier = Math.max(floor, Math.pow(factor, index));
        }
        double add = modifier.value() * multiplier * scale;
        out.merge(key, add, (left, right) -> (left == null ? 0.0 : left) + (right == null ? 0.0 : right));
      }
    }
    return out;
  }

  private List<String> aggregateSecondaryAbilities(List<String> records) {
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String record : records) {
      if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
        continue;
      }
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec == null) {
        continue;
      }
      out.addAll(spec.behaviors().secondaryAbilities());
      out.addAll(spec.behaviors().particlePresets());
    }
    return List.copyOf(out);
  }

  private List<String> aggregateStatusEffects(List<String> records, double scale) {
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String record : records) {
      if (record == null || record.isBlank() || record.startsWith("vanilla:")) {
        continue;
      }
      UpgradeSpec spec = registry.upgradeSpec(record);
      if (spec == null) {
        continue;
      }
      for (UpgradeStatusEffectSpec effect : spec.behaviors().statusEffects()) {
        UpgradeStatusEffectSpec scaled = scaleStatusEffect(effect, scale);
        out.add(scaled.toRecord());
      }
    }
    return List.copyOf(out);
  }

  private UpgradeStatusEffectSpec scaleStatusEffect(UpgradeStatusEffectSpec spec, double scale) {
    if (spec == null) {
      return spec;
    }
    if (Math.abs(scale - 1.0) < 1e-9) {
      return spec;
    }
    int duration = Math.max(1, (int) Math.round(spec.durationTicks() * scale));
    int amplifier = Math.max(0, (int) Math.round(spec.amplifier() * scale));
    return new UpgradeStatusEffectSpec(spec.type(), duration, amplifier, spec.chance(),
        spec.ambient(), spec.particles(), spec.icon());
  }

  private double resolveUpgradeScale(Player player) {
    UpgradeScalingConfig config = UpgradeScalingConfig.from(plugin.getConfig());
    return config.resolve(player, this::resolveXpLevel);
  }

  private static String encodeVanillaRecord(List<UpgradeEnchantSpec> enchants) {
    StringBuilder out = new StringBuilder("vanilla:");
    boolean first = true;
    for (UpgradeEnchantSpec spec : enchants) {
      String key = spec.enchantment().getKey().toString().toLowerCase(Locale.ROOT);
      if (!first) {
        out.append(':');
      }
      first = false;
      out.append(key).append('-').append(spec.level());
    }
    return out.toString();
  }

  private static Map<String, Integer> parseVanillaRecord(String record) {
    Map<String, Integer> out = new LinkedHashMap<>();
    if (record == null || !record.startsWith("vanilla:")) {
      return out;
    }
    String raw = record.substring("vanilla:".length());
    if (raw.isBlank()) {
      return out;
    }
    String[] parts = raw.split(":");
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      int dash = part.lastIndexOf('-');
      if (dash <= 0 || dash >= part.length() - 1) {
        continue;
      }
      String key = part.substring(0, dash);
      String levelRaw = part.substring(dash + 1);
      try {
        int level = Integer.parseInt(levelRaw);
        out.put(key, level);
      } catch (Exception ignored) {
      }
    }
    return out;
  }

  private static Enchantment enchantmentByKey(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    String normalized = key.toLowerCase(Locale.ROOT);
    if (!normalized.contains(":")) {
      normalized = "minecraft:" + normalized;
    }
    NamespacedKey namespaced = NamespacedKey.fromString(normalized);
    if (namespaced == null) {
      return null;
    }
    return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(namespaced);
  }

  private boolean allowUnsafeVanillaBooks() {
    return engine.plugin().getConfig().getBoolean("upgrades.vanilla.allowUnsafeLevels", false);
  }

  private Set<String> vanillaEnchantBlacklist() {
    List<String> raw = engine.plugin().getConfig().getStringList("upgrades.vanilla.enchantBlacklist");
    if (raw == null || raw.isEmpty()) {
      return Set.of();
    }
    Set<String> out = new HashSet<>();
    for (String entry : raw) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      String normalized = entry.trim().toLowerCase(Locale.ROOT);
      if (!normalized.contains(":")) {
        normalized = "minecraft:" + normalized;
      }
      out.add(normalized);
    }
    return out;
  }

  private boolean isRegistryEnchantment(NamespacedKey key) {
    if (key == null) {
      return false;
    }
    return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key) != null;
  }

  private static String format(double value) {
    if (Math.abs(value - Math.round(value)) < 1e-9) {
      return String.valueOf((long) Math.round(value));
    }
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static String formatPercent(double value) {
    double pct = value * 100.0;
    if (Math.abs(pct - Math.round(pct)) < 1e-9) {
      return String.valueOf((long) Math.round(pct)) + "%";
    }
    return String.format(Locale.ROOT, "%.1f%%", pct);
  }
}
