package dev.patric.dungeonsreborn.effects.config;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.Vibration;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.actions.ActionHandle;
import dev.patric.dungeonsreborn.effects.actions.ActionWithHandle;
import dev.patric.dungeonsreborn.effects.actions.Actions;
import dev.patric.dungeonsreborn.effects.actions.EntityActions;
import dev.patric.dungeonsreborn.effects.anim.Easings;
import dev.patric.dungeonsreborn.effects.conditions.Conditions;
import dev.patric.dungeonsreborn.effects.costs.Costs;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileSpec;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.targeting.Targeters;
import dev.patric.dungeonsreborn.effects.targeting.Targeter;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.InteractBinding;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.effects.integration.ItemMatchers;
import dev.patric.dungeonsreborn.effects.items.ItemConsumeMode;
import dev.patric.dungeonsreborn.effects.integration.PassiveBinding;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.effects.editor.EditorItemYaml;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.minions.MinionScaling;
import dev.patric.dungeonsreborn.effects.minions.MinionSpec;
import dev.patric.dungeonsreborn.effects.minions.MinionMode;
import dev.patric.dungeonsreborn.effects.minions.MinionPassiveSpec;
import dev.patric.dungeonsreborn.effects.minions.MinionSpecialAttackSpec;
import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.logging.ServiceLogManager;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import java.time.Duration;

/**
 * Phase-1 YAML ability loader/compiler.
 * <p>
 * This is intentionally limited and lives in the plugin layer (not the core engine).
 */
public final class EffectsYamlAbilities {
  public record ReloadResult(int loadedAbilities, int loadedItemBindings, List<String> errors) {
  }

  public record LintResult(int scripts, List<String> errors) {
  }

  private record AbilityEntry(String normalizedId, String basePath, ConfigurationSection section) {
  }

  private record ItemTemplate(String id, ItemStack item, ItemStack matchBase) {
  }

  private enum VarScope {
    CAST,
    PLAYER,
    ENTITY
  }

  private final JavaPlugin plugin;
  private final EffectsEngine engine;
  private final EffectsBindings bindings;
  private final ServiceLogger effectsLog;
  private final ServiceLogger bindingsLog;
  private final Set<String> loadedAbilityIds = new HashSet<>();
  private final Set<String> loadedBindingIds = new HashSet<>();
  private final java.util.Map<String, AbilitySpec> overriddenCodeAbilities = new java.util.HashMap<>();
  private java.util.Map<String, java.util.Map<String, Object>> macros = java.util.Collections.emptyMap();
  private final Map<UUID, Map<String, Object>> playerVars = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Object>> entityVars = new ConcurrentHashMap<>();
  private final java.util.Map<String, dev.patric.dungeonsreborn.effects.actions.Action> yamlActionGraphs = new ConcurrentHashMap<>();
  private final java.util.Map<java.nio.file.Path, ScriptCacheEntry> scriptCache = new ConcurrentHashMap<>();
  private final java.util.Map<String, ScriptMetrics> scriptMetrics = new ConcurrentHashMap<>();
  private final java.util.Map<String, ItemTemplate> itemTemplates = new java.util.HashMap<>();
  private volatile boolean scriptDebug;
  private volatile boolean scriptTrace;
  private static final String YAML_LAST_ENTITY = "yaml_last_entity";
  private static final String YAML_INVOKE_STACK = "yaml_invoke_stack";
  private static final String DSL_ON_HIT = "dsl_on_hit";
  private static final String DSL_ON_FINISH = "dsl_on_finish";
  private static final String DSL_CAST_DONE = "dsl_cast_done";
  private static final String DSL_PENDING = "dsl_pending";
  private static final String DSL_OPS_TICK = "__dsl_ops_tick";
  private static final String DSL_OPS_USED = "__dsl_ops_used";
  private static final String DSL_OPS_WARN_TICK = "__dsl_ops_warn_tick";
  private static final String DSL_ON_COST_FAIL = "dsl_on_cost_fail";
  private static final String DSL_ON_COOLDOWN_FAIL = "dsl_on_cooldown_fail";
  private static final String DSL_MACRO_STACK = "dsl_macro_stack";
  private static final String DSL_PARTICLE_USED = "__dsl_particles_used";
  private static final String DSL_SCRIPT_ID = "__dsl_script_id";
  private static final int MAX_CAST_VARS = 128;
  private static final int MAX_PLAYER_VARS = 256;
  private static final int MAX_ENTITY_VARS = 256;
  private static final int SCRIPT_VERSION = 1;
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final String ITEM_MARKER_START = "[dr:effects]";
  private static final String ITEM_MARKER_END = "[/dr:effects]";
  private enum EasingId {
    LINEAR,
    IN_OUT_CUBIC,
    OUT_QUAD
  }

  public EffectsYamlAbilities(JavaPlugin plugin, EffectsEngine engine) {
    this(plugin, engine, null,
        ServiceLogManager.fromConfig(plugin).effects(),
        ServiceLogManager.fromConfig(plugin).bindings());
  }

  public EffectsYamlAbilities(JavaPlugin plugin, EffectsEngine engine, EffectsBindings bindings, ServiceLogger effectsLog, ServiceLogger bindingsLog) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.bindings = bindings;
    this.effectsLog = Objects.requireNonNull(effectsLog, "effectsLog");
    this.bindingsLog = Objects.requireNonNull(bindingsLog, "bindingsLog");
  }

  public File file() {
    return new File(plugin.getDataFolder(), "effects.yml");
  }

  public File abilitiesDir() {
    return new File(plugin.getDataFolder(), "effects/abilities");
  }

  public File itemsDir() {
    return new File(plugin.getDataFolder(), "effects/items");
  }

  public ItemStack itemTemplate(String id) {
    if (id == null) {
      return null;
    }
    ItemTemplate template = itemTemplates.get(dev.patric.dungeonsreborn.effects.Ids.normalize(id));
    if (template == null) {
      return null;
    }
    return template.item().clone();
  }

  public Set<String> loadedAbilityIds() {
    return java.util.Collections.unmodifiableSet(loadedAbilityIds);
  }

  public boolean isScriptDebugEnabled() {
    return scriptDebug;
  }

  public void setScriptDebug(boolean enabled) {
    this.scriptDebug = enabled;
  }

  public boolean isScriptTraceEnabled() {
    return scriptTrace;
  }

  public void setScriptTrace(boolean enabled) {
    this.scriptTrace = enabled;
  }

  public void clearPlayerVars(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    playerVars.remove(playerId);
  }

  public void clearEntityVars(UUID entityId) {
    Objects.requireNonNull(entityId, "entityId");
    entityVars.remove(entityId);
  }

  public ReloadResult reload() {
    plugin.getDataFolder().mkdirs();
    new java.io.File(plugin.getDataFolder(), "effects/scripts").mkdirs();
    boolean cancelRunningOnReload = YamlConfiguration.loadConfiguration(file()).getBoolean("options.cancelRunningOnReload", false);
    // Provide a starter file for iteration.
    if (!file().exists()) {
      try {
        plugin.saveResource("effects.yml", false);
      } catch (IllegalArgumentException ignored) {
      }
    }

    if (cancelRunningOnReload && !loadedAbilityIds.isEmpty()) {
      Set<String> previous = new HashSet<>(loadedAbilityIds);
      int cancelled = engine.cancelCasts(r -> previous.contains(r.abilityId()), true);
      if (cancelled > 0) {
        effectsLog.info("[Effects] YAML cancelled " + cancelled + " running casts on reload");
      }
    }

    Set<String> previousLoadedAbilityIds = new HashSet<>(loadedAbilityIds);
    Set<String> previousLoadedBindingIds = new HashSet<>(loadedBindingIds);
    Map<String, AbilitySpec> previousYamlAbilities = new HashMap<>();
    for (String id : previousLoadedAbilityIds) {
      AbilitySpec spec = engine.abilitySpec(id);
      if (spec != null) {
        previousYamlAbilities.put(id, spec);
      }
    }
    Map<String, AbilitySpec> previousOverridden = new HashMap<>(overriddenCodeAbilities);
    Map<String, ItemTemplate> previousTemplates = new HashMap<>(itemTemplates);
    Map<String, dev.patric.dungeonsreborn.effects.actions.Action> previousYamlGraphs = new HashMap<>(yamlActionGraphs);
    Map<String, java.util.Map<String, Object>> previousMacros = macros;
    List<InteractBinding> previousInteractBindings = bindings == null ? java.util.List.of() : new ArrayList<>(bindings.interactBindings());
    List<PassiveBinding> previousPassiveBindings = bindings == null ? java.util.List.of() : new ArrayList<>(bindings.passiveBindings());

    // Unregister previously-loaded abilities (YAML owned).
    for (String id : loadedAbilityIds) {
      engine.unregisterAbility(id);
    }
    loadedAbilityIds.clear();

    // Restore any code-defined abilities that were overridden by YAML.
    for (var entry : overriddenCodeAbilities.entrySet()) {
      if (!engine.hasAbility(entry.getKey())) {
        engine.registerAbility(entry.getValue());
      }
    }

    // Unregister previously-loaded YAML bindings.
    if (bindings != null) {
      for (String id : loadedBindingIds) {
        bindings.unregister(id);
      }
    }
    loadedBindingIds.clear();
    yamlActionGraphs.clear();

    ArrayList<String> errors = new ArrayList<>();
    java.util.ArrayList<java.util.Map.Entry<String, YamlConfiguration>> sources = new java.util.ArrayList<>();
    sources.add(java.util.Map.entry(file().getPath(), YamlConfiguration.loadConfiguration(file())));

    File dir = abilitiesDir();
    File[] extra = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (extra != null) {
      java.util.Arrays.sort(extra, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File f : extra) {
        sources.add(java.util.Map.entry(f.getPath(), YamlConfiguration.loadConfiguration(f)));
      }
    }
    File[] folders = dir.listFiles(File::isDirectory);
    if (folders != null) {
      java.util.Arrays.sort(folders, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File folder : folders) {
        File abilityFile = new File(folder, "ability.yml");
        if (!abilityFile.exists()) {
          abilityFile = new File(folder, "ability.yaml");
        }
        if (abilityFile.exists()) {
          sources.add(java.util.Map.entry(abilityFile.getPath(), YamlConfiguration.loadConfiguration(abilityFile)));
        }
      }
    }

    java.util.LinkedHashMap<String, AbilityEntry> all = new java.util.LinkedHashMap<>();
    java.util.HashMap<String, java.util.Map<String, Object>> macroTmp = new java.util.HashMap<>();

    for (var source : sources) {
      String sourcePath = source.getKey();
      YamlConfiguration cfg = source.getValue();

      int schemaVersion = cfg.getInt("schemaVersion", 1);
      if (schemaVersion != 1) {
        errors.add(sourcePath + ": Unsupported schemaVersion=" + schemaVersion + " (expected 1)");
        continue;
      }

      // Optional macros (Phase 2): reusable action nodes.
      ConfigurationSection macrosSec = cfg.getConfigurationSection("macros");
      if (macrosSec != null) {
        for (String key : macrosSec.getKeys(false)) {
          if (macroTmp.containsKey(key)) {
            String message = sourcePath + ": macros." + key + ": duplicate macro id";
            errors.add(message);
            effectsLog.warn("[Effects] " + message);
            continue;
          }
          ConfigurationSection m = macrosSec.getConfigurationSection(key);
          if (m == null) {
            errors.add(sourcePath + ": macros." + key + ": must be an object");
            continue;
          }
          macroTmp.put(key, normalizeMap(m.getValues(false)));
        }
      }

      ConfigurationSection abilities = cfg.getConfigurationSection("abilities");
      if (abilities == null) {
        continue;
      }

      for (String id : abilities.getKeys(false)) {
        String normalizedId;
        try {
          normalizedId = dev.patric.dungeonsreborn.effects.Ids.normalize(id);
        } catch (Exception ex) {
          errors.add(sourcePath + ": abilities." + id + ": invalid id (" + ex.getMessage() + ")");
          continue;
        }
        if (all.containsKey(normalizedId)) {
          String message = sourcePath + ": abilities." + id + ": duplicate ability id (normalized=" + normalizedId + ")";
          errors.add(message);
          effectsLog.warn("[Effects] " + message);
          continue;
        }

        ConfigurationSection a = abilities.getConfigurationSection(id);
        if (a == null) {
          errors.add(sourcePath + ": abilities." + id + ": must be an object");
          continue;
        }

        all.put(normalizedId, new AbilityEntry(normalizedId, sourcePath + ": abilities." + id, a));
      }
    }

    macros = java.util.Collections.unmodifiableMap(macroTmp);

    warmScriptCache(errors);
    int loaded = 0;
    int loadedItemBindings = 0;
    for (AbilityEntry entry : all.values()) {
      try {
        AbilitySpec spec = compileAbility(entry.normalizedId(), entry.basePath(), entry.section());
        // Phase 0 merge policy: YAML overrides code-defined abilities.
        AbilitySpec existing = engine.abilitySpec(entry.normalizedId());
        if (existing != null) {
          overriddenCodeAbilities.putIfAbsent(entry.normalizedId(), existing);
          engine.unregisterAbility(entry.normalizedId());
        }
        engine.registerAbility(spec);
        if (bindings != null) {
          for (InteractBinding binding : spec.interactBindings()) {
            bindings.register(binding);
            loadedBindingIds.add(binding.id());
          }
        }
        loadedAbilityIds.add(entry.normalizedId());
        loaded++;
      } catch (Exception ex) {
        errors.add(entry.basePath() + ": " + ex.getMessage());
      }
    }

    File itemsDir = itemsDir();
    if (!itemsDir.exists()) {
      itemsDir.mkdirs();
    }
    File[] itemFiles = itemsDir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (itemFiles == null) {
      itemFiles = new File[0];
    }
    itemTemplates.clear();
    if (itemFiles != null) {
      java.util.Arrays.sort(itemFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File f : itemFiles) {
        String fileName = f.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
          errors.add(f.getPath() + ": filename must be <itemId>.yml");
          continue;
        }
        String itemIdRaw = fileName.substring(0, dot);
        String itemId;
        try {
          itemId = dev.patric.dungeonsreborn.effects.Ids.normalize(itemIdRaw);
        } catch (Exception ex) {
          errors.add(f.getPath() + ": invalid item id (" + ex.getMessage() + ")");
          continue;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        int schemaVersion = cfg.getInt("schemaVersion", 1);
        if (schemaVersion != 1) {
          errors.add(f.getPath() + ": Unsupported schemaVersion=" + schemaVersion + " (expected 1)");
          continue;
        }
        Map<String, Object> root = normalizeMap(cfg.getValues(false));
        loadItemTemplate(itemId, f.getPath(), cfg, errors);
        loadedItemBindings += compileItemBindings(itemId, f.getPath(), root, errors);
      }
    }

    if (!errors.isEmpty()) {
      // Roll back to previous YAML state on error.
      for (String id : loadedAbilityIds) {
        engine.unregisterAbility(id);
      }
      if (bindings != null) {
        for (String id : loadedBindingIds) {
          bindings.unregister(id);
          bindings.unregisterPassive(id);
        }
        for (InteractBinding binding : previousInteractBindings) {
          bindings.register(binding);
        }
        for (PassiveBinding binding : previousPassiveBindings) {
          bindings.registerPassive(binding);
        }
      }
      loadedBindingIds.clear();
      loadedBindingIds.addAll(previousLoadedBindingIds);
      for (Map.Entry<String, AbilitySpec> entry : previousYamlAbilities.entrySet()) {
        engine.unregisterAbility(entry.getKey());
        engine.registerAbility(entry.getValue());
      }
      loadedAbilityIds.clear();
      loadedAbilityIds.addAll(previousLoadedAbilityIds);
      overriddenCodeAbilities.clear();
      overriddenCodeAbilities.putAll(previousOverridden);
      itemTemplates.clear();
      itemTemplates.putAll(previousTemplates);
      yamlActionGraphs.clear();
      yamlActionGraphs.putAll(previousYamlGraphs);
      macros = previousMacros;

      effectsLog.warn("[Effects] YAML reload had " + errors.size() + " errors (some abilities/bindings may be missing)");
      for (String e : errors) {
        if (isBindingError(e)) {
          bindingsLog.warn("[Bindings] YAML: " + e);
        } else {
          effectsLog.warn("[Effects] YAML: " + e);
        }
      }
      effectsLog.warn("[Effects] YAML reload failed; previous configuration kept");
    } else {
      effectsLog.info("[Effects] YAML loaded " + loaded + " abilities");
      bindingsLog.info("[Bindings] YAML loaded " + loadedItemBindings + " item bindings");
    }
    SystemStatusStore.get().record(
        "effects",
        "Effects",
        file().getPath(),
        "abilities=" + (errors.isEmpty() ? loaded : previousLoadedAbilityIds.size()),
        errors);
    SystemStatusStore.get().record(
        "bindings",
        "Bindings",
        itemsDir.getPath(),
        "itemBindings=" + (errors.isEmpty() ? loadedItemBindings : previousLoadedBindingIds.size()),
        errors);
    return new ReloadResult(
        errors.isEmpty() ? loaded : previousLoadedAbilityIds.size(),
        errors.isEmpty() ? loadedItemBindings : previousLoadedBindingIds.size(),
        errors);
  }

  public int syncOnlineItems() {
    int updated = 0;
    for (Player player : Bukkit.getOnlinePlayers()) {
      updated += syncPlayerItems(player);
    }
    return updated;
  }

  public int syncPlayerItems(Player player) {
    Objects.requireNonNull(player, "player");
    if (itemTemplates.isEmpty()) {
      return 0;
    }
    int updated = 0;
    var inv = player.getInventory();

    ItemStack[] contents = inv.getContents();
    updated += syncArray(player, contents);
    inv.setContents(contents);

    ItemStack[] armor = inv.getArmorContents();
    updated += syncArray(player, armor);
    inv.setArmorContents(armor);

    ItemStack offhand = inv.getItemInOffHand();
    ItemStack updatedOffhand = syncItem(player, offhand);
    if (updatedOffhand != offhand) {
      inv.setItemInOffHand(updatedOffhand);
      updated++;
    }
    return updated;
  }

  private int syncArray(Player player, ItemStack[] contents) {
    int updated = 0;
    for (int i = 0; i < contents.length; i++) {
      ItemStack current = contents[i];
      ItemStack next = syncItem(player, current);
      if (next != current) {
        contents[i] = next;
        updated++;
      }
    }
    return updated;
  }

  private ItemStack syncItem(Player player, ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return item;
    }
    if (!ItemMarkers.getUpgradeRecords(item).isEmpty()) {
      return item;
    }
    ItemTemplate template = findTemplate(player, item);
    if (template == null) {
      return item;
    }
    ItemStack updated = applyTemplate(template, item);
    if (isSameItem(item, updated)) {
      return item;
    }
    return updated;
  }

  private ItemTemplate findTemplate(Player player, ItemStack item) {
    String id = ItemMarkers.getItemId(item);
    if (id != null) {
      ItemTemplate template = itemTemplates.get(id);
      if (template != null) {
        return template;
      }
    }
    if (!hasEffectLoreMarker(item)) {
      return null;
    }
    ItemTemplate match = null;
    for (ItemTemplate template : itemTemplates.values()) {
      if (matchesTemplate(template, item)) {
        if (match != null) {
          return null;
        }
        match = template;
      }
    }
    return match;
  }

  private static boolean hasEffectLoreMarker(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }
    List<Component> lore = meta.lore();
    if (lore == null || lore.isEmpty()) {
      return false;
    }
    boolean inBlock = false;
    for (Component line : lore) {
      String plain = line == null ? null : PLAIN.serialize(line);
      if (ITEM_MARKER_START.equals(plain)) {
        inBlock = true;
        continue;
      }
      if (ITEM_MARKER_END.equals(plain)) {
        return inBlock;
      }
    }
    return inBlock;
  }

  private static boolean matchesTemplate(ItemTemplate template, ItemStack item) {
    ItemStack compare = item.clone();
    compare.setAmount(1);
    ItemMarkers.setItemId(compare, null);
    stripEffectLore(compare);
    return compare.isSimilar(template.matchBase());
  }

  private static boolean isSameItem(ItemStack a, ItemStack b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.getAmount() != b.getAmount()) {
      return false;
    }
    return a.isSimilar(b);
  }

  private static ItemStack applyTemplate(ItemTemplate template, ItemStack current) {
    ItemStack updated = template.item().clone();
    updated.setAmount(current.getAmount());
    ItemMeta meta = updated.getItemMeta();
    ItemMeta existing = current.getItemMeta();
    if (meta instanceof Damageable dmgNew && existing instanceof Damageable dmgOld) {
      dmgNew.setDamage(dmgOld.getDamage());
      updated.setItemMeta(meta);
    }
    return updated;
  }

  private static void stripEffectLore(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    List<Component> lore = meta.lore();
    if (lore == null || lore.isEmpty()) {
      return;
    }
    List<Component> out = new ArrayList<>();
    boolean inBlock = false;
    for (Component line : lore) {
      String plain = line == null ? null : PLAIN.serialize(line);
      if (ITEM_MARKER_START.equals(plain)) {
        inBlock = true;
        continue;
      }
      if (ITEM_MARKER_END.equals(plain)) {
        inBlock = false;
        continue;
      }
      if (!inBlock) {
        out.add(line);
      }
    }
    meta.lore(out.isEmpty() ? null : out);
    item.setItemMeta(meta);
  }

  private void loadItemTemplate(String itemId, String base, YamlConfiguration cfg, List<String> errors) {
    ItemStack item = cfg.getItemStack("item");
    if (item == null || item.getType().isAir()) {
      errors.add(base + ": item is missing or invalid");
      return;
    }
    try {
      ItemStack template = item.clone();
      ItemMarkers.setItemId(template, itemId);
      ConfigurationSection mana = cfg.getConfigurationSection("mana");
      if (mana != null) {
        double maxBonus = mana.contains("maxBonus") ? mana.getDouble("maxBonus") : mana.getDouble("max", 0.0);
        double regenBonus = mana.contains("regenBonus") ? mana.getDouble("regenBonus") : mana.getDouble("regen", 0.0);
        double boost = 0.0;
        if (mana.contains("boost")) {
          boost = mana.getDouble("boost");
        } else if (mana.contains("tempBoost")) {
          boost = mana.getDouble("tempBoost");
        } else if (mana.contains("temporaryBoost")) {
          boost = mana.getDouble("temporaryBoost");
        }
        if (Double.isFinite(boost)) {
          maxBonus += boost;
        }
        ItemMarkers.setManaMaxBonus(template, maxBonus);
        ItemMarkers.setManaRegenBonus(template, regenBonus);
      }
      Object consumableRaw = cfg.get("consumable");
      if (consumableRaw == null) {
        consumableRaw = cfg.get("consume");
      }
      if (consumableRaw != null) {
        try {
          ItemConsumeMode consumeMode = ItemConsumeMode.NONE;
          int consumeAmount = 1;
          if (consumableRaw instanceof Boolean bool) {
            consumeMode = bool ? ItemConsumeMode.STACK : ItemConsumeMode.NONE;
          } else if (consumableRaw instanceof String rawString) {
            consumeMode = parseConsumeMode(rawString, base + ".consumable");
          } else if (consumableRaw instanceof ConfigurationSection section) {
            String modeRaw = section.getString("mode", section.getString("type", null));
            consumeMode = parseConsumeMode(modeRaw, base + ".consumable.mode");
            consumeAmount = section.getInt("amount", section.getInt("damage", 1));
          } else if (consumableRaw instanceof Map<?, ?> map) {
            Object modeRaw = map.get("mode");
            if (modeRaw == null) {
              modeRaw = map.get("type");
            }
            consumeMode = parseConsumeMode(modeRaw == null ? null : String.valueOf(modeRaw), base + ".consumable.mode");
            Object amountRaw = map.containsKey("amount") ? map.get("amount") : map.get("damage");
            if (amountRaw != null) {
              consumeAmount = Integer.parseInt(String.valueOf(amountRaw));
            }
          } else {
            throw new IllegalArgumentException(base + ".consumable: expected boolean, string, or object");
          }
          if (consumeMode != ItemConsumeMode.NONE) {
            if (consumeAmount <= 0) {
              throw new IllegalArgumentException(base + ".consumable.amount: must be > 0");
            }
            boolean damageable = template.getItemMeta() instanceof Damageable && template.getType().getMaxDurability() > 0;
            if (consumeMode == ItemConsumeMode.DURABILITY && !damageable) {
              throw new IllegalArgumentException(base + ".consumable.mode: durability requires a damageable item");
            }
            ItemMarkers.setConsumeMode(template, consumeMode);
            ItemMarkers.setConsumeAmount(template, consumeAmount);
          }
        } catch (Exception ex) {
          errors.add(base + ": " + ex.getMessage());
        }
      }
      List<Map<String, Object>> bindings = EditorItemYaml.bindings(cfg);
      EditorItemLore.applyAbilityLore(template, bindings, engine);
      ItemStack matchBase = template.clone();
      ItemMarkers.setItemId(matchBase, null);
      stripEffectLore(matchBase);
      itemTemplates.put(itemId, new ItemTemplate(itemId, template, matchBase));
    } catch (Exception ex) {
      errors.add(base + ": " + ex.getMessage());
    }
  }

  public LintResult lintScripts() {
    plugin.getDataFolder().mkdirs();
    List<String> errors = new ArrayList<>();
    int scripts = 0;

    java.util.ArrayList<java.util.Map.Entry<String, YamlConfiguration>> sources = new java.util.ArrayList<>();
    sources.add(java.util.Map.entry(file().getPath(), YamlConfiguration.loadConfiguration(file())));

    File dir = abilitiesDir();
    File[] extra = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (extra != null) {
      java.util.Arrays.sort(extra, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File f : extra) {
        sources.add(java.util.Map.entry(f.getPath(), YamlConfiguration.loadConfiguration(f)));
      }
    }
    File[] folders = dir.listFiles(File::isDirectory);
    if (folders != null) {
      java.util.Arrays.sort(folders, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File folder : folders) {
        File abilityFile = new File(folder, "ability.yml");
        if (!abilityFile.exists()) {
          abilityFile = new File(folder, "ability.yaml");
        }
        if (abilityFile.exists()) {
          sources.add(java.util.Map.entry(abilityFile.getPath(), YamlConfiguration.loadConfiguration(abilityFile)));
        }
      }
    }

    for (var source : sources) {
      String sourcePath = source.getKey();
      YamlConfiguration cfg = source.getValue();
      ConfigurationSection abilities = cfg.getConfigurationSection("abilities");
      if (abilities == null) {
        continue;
      }
      for (String id : abilities.getKeys(false)) {
        ConfigurationSection a = abilities.getConfigurationSection(id);
        if (a == null) {
          continue;
        }
        Object scriptObj = a.get("script");
        if (scriptObj == null) {
          continue;
        }
        String basePath = sourcePath + ": abilities." + id + ".script";
        scripts++;
        try {
          compileScript(scriptObj, basePath);
        } catch (IllegalArgumentException ex) {
          errors.add(ex.getMessage());
        }
      }
    }

    File scriptsDir = new File(plugin.getDataFolder(), "effects/scripts");
    if (scriptsDir.exists()) {
      try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(scriptsDir.toPath())) {
        for (java.nio.file.Path path : walk.filter(java.nio.file.Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".es"))
            .toList()) {
          scripts++;
          try {
            compileScriptFileCached(path.toString(), path.toString(), null);
          } catch (IllegalArgumentException ex) {
            errors.add(ex.getMessage());
          }
        }
      } catch (java.io.IOException ex) {
        errors.add("scripts: failed to read scripts folder (" + ex.getMessage() + ")");
      }
    }

    return new LintResult(scripts, errors);
  }

  public LintResult lintScriptFile(String filePath) {
    List<String> errors = new ArrayList<>();
    try {
      compileScriptFileCached(filePath, "lint", null);
    } catch (IllegalArgumentException ex) {
      errors.add(ex.getMessage());
    }
    return new LintResult(1, errors);
  }

  public void runScriptFile(Player player, String filePath) {
    Objects.requireNonNull(player, "player");
    java.nio.file.Path resolved = resolveScriptPath(filePath, "script.run");
    String scriptId = "script:" + resolved.getFileName();
    ScriptHandlers handlers = compileScriptFileCached(resolved.toString(), "script.run", null);
    dev.patric.dungeonsreborn.effects.actions.Action action = buildScriptAction(handlers, scriptId);
    engine.castAction("dsl_playground", player, action);
  }

  private AbilitySpec compileAbility(String id, String base, ConfigurationSection a) {
    AbilitySpec.Builder builder = AbilitySpec.builder(id)
        .name(a.getString("name"))
        .description(a.getString("description"));

    // Requirements
    var requirements = a.getMapList("requirements");
    for (int i = 0; i < requirements.size(); i++) {
      Map<?, ?> raw = requirements.get(i);
      Map<String, Object> req = castMap(raw, base + ".requirements[" + i + "]");
      String type = requireString(req, "type", base + ".requirements[" + i + "].type");
      Component message = req.containsKey("message") ? richText(String.valueOf(req.get("message"))) : null;

      switch (type.toLowerCase(Locale.ROOT)) {
        case "sneaking" -> builder.require(Conditions.sneaking(), message);
        case "permission" -> builder.require(Conditions.permission(requireString(req, "permission", base + ".requirements[" + i + "].permission")), message);
        case "has_item_tag" -> {
          String keyStr = requireString(req, "key", base + ".requirements[" + i + "].key");
          NamespacedKey key = NamespacedKey.fromString(keyStr);
          if (key == null) {
            throw new IllegalArgumentException(base + ".requirements[" + i + "].key: invalid NamespacedKey: " + keyStr);
          }
          builder.require(Conditions.hasItemTag(key), message);
        }
        default -> throw new IllegalArgumentException(base + ".requirements[" + i + "].type: unknown type: " + type);
      }
    }

    // Costs
    var costs = a.getMapList("costs");
    for (int i = 0; i < costs.size(); i++) {
      Map<?, ?> raw = costs.get(i);
      Map<String, Object> cost = castMap(raw, base + ".costs[" + i + "]");
      String type = requireString(cost, "type", base + ".costs[" + i + "].type");
      switch (type.toLowerCase(Locale.ROOT)) {
        case "mana" -> {
          NumValue amount = requireNumValue(cost, "amount", base + ".costs[" + i + "].amount");
          builder.cost(ctx -> {
            double v = evalDouble(amount, ctx);
            if (!(v > 0.0)) {
              return Component.text("Invalid mana cost.");
            }
            return Costs.mana(v).tryApply(ctx);
          });
        }
        case "consume_item", "consume_main_hand" -> {
          NumValue amount = requireNumValue(cost, "amount", base + ".costs[" + i + "].amount");
          builder.cost(ctx -> {
            int v = evalInt(amount, ctx);
            if (v <= 0) {
              return Component.text("Invalid item cost.");
            }
            return Costs.consumeMainHand(v).tryApply(ctx);
          });
        }
        case "durability", "durability_main_hand" -> {
          NumValue dmg = requireNumValue(cost, "damage", base + ".costs[" + i + "].damage");
          boolean allowBreak = bool(cost, "allowBreak", false);
          builder.cost(ctx -> {
            int v = evalInt(dmg, ctx);
            if (v <= 0) {
              return Component.text("Invalid durability cost.");
            }
            return Costs.durabilityMainHand(v, allowBreak).tryApply(ctx);
          });
        }
        default -> throw new IllegalArgumentException(base + ".costs[" + i + "].type: unknown type: " + type);
      }
    }

    // Cooldown
    ConfigurationSection cooldown = a.getConfigurationSection("cooldown");
    if (cooldown != null) {
      long ticks = cooldown.getLong("ticks", 0L);
      if (ticks > 0) {
        builder.cooldownTicks(ticks, cooldown.getString("key"));
      }
    }

    // Progression XP awards (vanilla XP system).
    ConfigurationSection progression = a.getConfigurationSection("progression");
    if (progression != null) {
      int fixedXp = progression.getInt("xp", -1);
      int minXp = progression.getInt("minXp", fixedXp >= 0 ? fixedXp : 0);
      int maxXp = progression.getInt("maxXp", fixedXp >= 0 ? fixedXp : minXp);
      builder.xpAward(minXp, maxXp);
    }

    // Action or Script
    Object scriptObj = a.get("script");
    dev.patric.dungeonsreborn.effects.actions.Action compiled;
    if (scriptObj != null) {
      ScriptHandlers handlers = compileScript(scriptObj, base + ".script");
      compiled = buildScriptAction(handlers, id);
      if (handlers.onCostFail() != null) {
        builder.onCostFail(handlers.onCostFail());
      }
      if (handlers.onCooldownFail() != null) {
        builder.onCooldownFail(handlers.onCooldownFail());
      }
    } else {
      Object actionObj = a.get("action");
      Map<String, Object> actionNode;
      if (actionObj instanceof ConfigurationSection sec) {
        actionNode = sec.getValues(false);
      } else if (actionObj instanceof Map<?, ?> map) {
        actionNode = castMap(map, base + ".action");
      } else {
        throw new IllegalArgumentException(base + ".action: missing or invalid");
      }
      compiled = compileAction(actionNode, base + ".action", new java.util.ArrayDeque<>());
    }
    if (a.getBoolean("profile", false)) {
      compiled = Actions.timed("yaml:" + id, compiled);
    }
    builder.action(compiled);
    yamlActionGraphs.put(id, compiled);

    // Triggers (Phase 3): compile YAML triggers into InteractBindings.
    var triggers = a.getMapList("triggers");
    for (int i = 0; i < triggers.size(); i++) {
      Map<?, ?> raw = triggers.get(i);
      Map<String, Object> trig = castMap(raw, base + ".triggers[" + i + "]");
      String type = requireString(trig, "type", base + ".triggers[" + i + "].type").trim().toLowerCase(Locale.ROOT);
      if (!type.equals("interact") && !type.equals("item_bind") && !type.equals("item-bind")) {
        throw new IllegalArgumentException(base + ".triggers[" + i + "].type: unknown trigger type: " + type);
      }

      String click = string(trig, "click", string(trig, "trigger", "RIGHT_CLICK"));
      InteractTrigger trigger = parseInteractTrigger(click, base + ".triggers[" + i + "].click");
      boolean requireSneaking = bool(trig, "requireSneaking", false);
      boolean cancelEvent = bool(trig, "cancelEvent", true);
      String permission = trig.containsKey("permission") ? String.valueOf(trig.get("permission")) : null;
      if (permission != null && permission.isBlank()) {
        permission = null;
      }

      ItemMatcher matcher = ItemMatchers.anyNonAir();
      if (trig.containsKey("item")) {
        matcher = compileItemMatcher(trig.get("item"), base + ".triggers[" + i + "].item");
      }

      String bindingId = trig.containsKey("id") ? String.valueOf(trig.get("id")) : null;
      if (bindingId == null || bindingId.isBlank()) {
        bindingId = "yaml:" + id + ":" + i;
      } else if (!bindingId.startsWith("yaml:")) {
        bindingId = "yaml:" + bindingId;
      }

      builder.triggerInteract(bindingId, trigger, matcher, requireSneaking, permission, cancelEvent);
    }

    return builder.build();
  }

  private int compileItemBindings(String itemId, String base, Map<String, Object> root, List<String> errors) {
    Object itemRaw = root.get("item");
    if (itemRaw == null) {
      itemRaw = root.get("matcher");
    }
    if (itemRaw == null) {
      errors.add(base + ": missing item matcher (key: item)");
      return 0;
    }

    ItemMatcher baseMatcher;
    try {
      ItemStack stack = itemRaw instanceof ItemStack ? ((ItemStack) itemRaw).clone() : null;
      if (stack != null) {
        baseMatcher = ItemMatchers.or(ItemMatchers.similar(stack), ItemMatchers.itemId(itemId));
      } else {
        baseMatcher = compileItemMatcher(itemRaw, base + ".item");
      }
    } catch (Exception ex) {
      errors.add(base + ": " + ex.getMessage());
      return 0;
    }

    String bindingKey = "bindings";
    List<?> bindingList = mapList(root, "bindings", base + ".bindings");
    if (bindingList.isEmpty()) {
      bindingList = mapList(root, "triggers", base + ".triggers");
      bindingKey = "triggers";
    }
    if (bindingList.isEmpty()) {
      return 0;
    }

    int loaded = 0;
    for (int i = 0; i < bindingList.size(); i++) {
      String path = base + "." + bindingKey + "[" + i + "]";
      try {
        Map<String, Object> binding = castMap(bindingList.get(i), path);
        String type = string(binding, "type", "interact").trim().toLowerCase(Locale.ROOT);
        String clickRaw = string(binding, "click", null);
        boolean passive = type.equals("passive") || (clickRaw != null && clickRaw.equalsIgnoreCase("passive"));
        if (!passive && !type.equals("interact") && !type.equals("item_bind") && !type.equals("item-bind")) {
          throw new IllegalArgumentException(path + ".type: expected interact or passive");
        }

        String rawAbility = requireString(binding, "ability", path + ".ability");
        String abilityId;
        try {
          abilityId = dev.patric.dungeonsreborn.effects.Ids.normalize(rawAbility);
        } catch (Exception ex) {
          throw new IllegalArgumentException(path + ".ability: invalid id (" + ex.getMessage() + ")");
        }
        if (!engine.hasAbility(abilityId)) {
          throw new IllegalArgumentException(path + ".ability: ability not registered: " + abilityId);
        }

        boolean requireSneaking = bool(binding, "requireSneaking", false);
        String permission = string(binding, "permission", null);
        if (permission != null && permission.isBlank()) {
          permission = null;
        }
        boolean cancelEvent = bool(binding, "cancelEvent", true);

        ItemMatcher matcher = baseMatcher;
        if (binding.containsKey("item")) {
          ItemMatcher extra = compileItemMatcher(binding.get("item"), path + ".item");
          matcher = ItemMatchers.and(matcher, extra);
        }

        String bindingId = string(binding, "id", null);
        if (bindingId == null || bindingId.isBlank()) {
          bindingId = passive ? "yaml:item:passive:" + itemId + ":" + i : "yaml:item:" + itemId + ":" + i;
        } else if (!bindingId.startsWith("yaml:")) {
          bindingId = passive ? "yaml:item:passive:" + bindingId : "yaml:item:" + bindingId;
        }

        if (passive) {
          int periodTicks = intValue(binding, "periodTicks", 20);
          if (periodTicks <= 0) {
            throw new IllegalArgumentException(path + ".periodTicks: must be > 0");
          }
          EnumSet<EquipmentSlot> slots = parsePassiveSlots(binding, path);
          PassiveBinding compiled = new PassiveBinding(bindingId, abilityId, matcher, requireSneaking, permission, periodTicks, slots);
          if (bindings != null) {
            bindings.registerPassive(compiled);
            loadedBindingIds.add(bindingId);
            loaded++;
          }
        } else {
          InteractTrigger trigger = parseInteractTrigger(string(binding, "click", null), path + ".click");
          InteractBinding compiled = InteractBinding.builder(bindingId)
              .trigger(trigger)
              .ability(abilityId)
              .item(matcher)
              .requireSneaking(requireSneaking)
              .permission(permission)
              .cancelEvent(cancelEvent)
              .build();
          if (bindings != null) {
            bindings.register(compiled);
            loadedBindingIds.add(bindingId);
            loaded++;
          }
        }
      } catch (Exception ex) {
        errors.add(path + ": " + ex.getMessage());
      }
    }
    return loaded;
  }

  private static VarScope parseVarScope(String raw, String path, VarScope def) {
    if (raw == null) {
      return def;
    }
    String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (s) {
      case "cast" -> VarScope.CAST;
      case "player" -> VarScope.PLAYER;
      case "entity", "target" -> VarScope.ENTITY;
      default -> throw new IllegalArgumentException(path + ": invalid scope=" + raw + " (use cast|player|entity)");
    };
  }

  private static ItemConsumeMode parseConsumeMode(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return ItemConsumeMode.NONE;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "none", "off", "false" -> ItemConsumeMode.NONE;
      case "stack", "item", "item_stack", "itemstack" -> ItemConsumeMode.STACK;
      case "durability", "damage", "durable" -> ItemConsumeMode.DURABILITY;
      default -> throw new IllegalArgumentException(path + ": invalid consumable mode=" + raw + " (use stack|durability)");
    };
  }

  private Map<String, Object> vars(CastContext ctx, VarScope scope) {
    return switch (scope) {
      case CAST -> ctx.variables();
      case PLAYER -> {
        if (!(ctx.caster() instanceof Player)) {
          yield ctx.variables();
        }
        yield playerVars.computeIfAbsent(ctx.caster().getUniqueId(), k -> new java.util.HashMap<>());
      }
      case ENTITY -> {
        LivingEntity entity = lastEntity(ctx);
        if (entity == null) {
          entity = ctx.caster();
        }
        yield entityVars.computeIfAbsent(entity.getUniqueId(), k -> new java.util.HashMap<>());
      }
    };
  }

  private static int maxVars(VarScope scope) {
    return switch (scope) {
      case CAST -> MAX_CAST_VARS;
      case PLAYER -> MAX_PLAYER_VARS;
      case ENTITY -> MAX_ENTITY_VARS;
    };
  }

  private static ActionHandle scheduledHandle(EffectsEngine.ScheduledHandle handle, AtomicBoolean done) {
    return new ActionHandle() {
      @Override
      public boolean cancel() {
        boolean cancelled = handle.cancel();
        done.set(true);
        return cancelled;
      }

      @Override
      public boolean isDone() {
        return done.get() || handle.isCancelled();
      }
    };
  }

  private boolean setVar(CastContext ctx, VarScope scope, String key, Object value) {
    Map<String, Object> vars = vars(ctx, scope);
    if (value == null) {
      vars.remove(key);
      return true;
    }
    if (vars.containsKey(key)) {
      vars.put(key, value);
      return true;
    }
    int limit = maxVars(scope);
    if (vars.size() >= limit) {
      if (ctx.engine().isDebugEnabled()) {
        ctx.engine().debug("dsl vars: scope=" + scope.name().toLowerCase(Locale.ROOT) + " cap reached (" + limit + ")");
      }
      return false;
    }
    vars.put(key, value);
    return true;
  }

  private static InteractTrigger parseInteractTrigger(String raw, String path) {
    if (raw == null) {
      return InteractTrigger.RIGHT_CLICK;
    }
    String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (s) {
      case "right", "right_click", "rightclick" -> InteractTrigger.RIGHT_CLICK;
      case "left", "left_click", "leftclick" -> InteractTrigger.LEFT_CLICK;
      default -> throw new IllegalArgumentException(path + ": invalid click=" + raw + " (use RIGHT_CLICK or LEFT_CLICK)");
    };
  }

  private static EnumSet<EquipmentSlot> parsePassiveSlots(Map<String, Object> node, String path) {
    String key = node.containsKey("slots") ? "slots" : "slot";
    Object raw = node.get(key);
    if (raw == null) {
      return EnumSet.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);
    }
    List<String> values = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object v : list) {
        values.add(String.valueOf(v));
      }
    } else {
      values.add(String.valueOf(raw));
    }

    EnumSet<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
    for (String value : values) {
      String token = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
      switch (token) {
        case "hand", "main_hand", "main", "primary" -> slots.add(EquipmentSlot.HAND);
        case "off_hand", "offhand", "off", "secondary" -> slots.add(EquipmentSlot.OFF_HAND);
        case "hands", "any_hand", "anyhand" -> {
          slots.add(EquipmentSlot.HAND);
          slots.add(EquipmentSlot.OFF_HAND);
        }
        case "head", "helmet" -> slots.add(EquipmentSlot.HEAD);
        case "chest", "chestplate" -> slots.add(EquipmentSlot.CHEST);
        case "legs", "leggings" -> slots.add(EquipmentSlot.LEGS);
        case "feet", "boots" -> slots.add(EquipmentSlot.FEET);
        case "armor" -> {
          slots.add(EquipmentSlot.HEAD);
          slots.add(EquipmentSlot.CHEST);
          slots.add(EquipmentSlot.LEGS);
          slots.add(EquipmentSlot.FEET);
        }
        case "all", "any" -> {
          slots.add(EquipmentSlot.HAND);
          slots.add(EquipmentSlot.OFF_HAND);
          slots.add(EquipmentSlot.HEAD);
          slots.add(EquipmentSlot.CHEST);
          slots.add(EquipmentSlot.LEGS);
          slots.add(EquipmentSlot.FEET);
        }
        default -> throw new IllegalArgumentException(path + "." + key + ": invalid slot=" + value);
      }
    }
    if (slots.isEmpty()) {
      throw new IllegalArgumentException(path + "." + key + ": no valid slots specified");
    }
    return slots;
  }

  private static ItemMatcher compileItemMatcher(Object raw, String path) {
    Map<String, Object> node = castMap(raw, path);
    String type = requireString(node, "type", path + ".type").trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case "any_non_air", "any-non-air", "any_nonair", "any" -> ItemMatchers.anyNonAir();
      case "material" -> {
        String m = requireString(node, "material", path + ".material").trim().toUpperCase(Locale.ROOT);
        Material mat = Material.matchMaterial(m);
        if (mat == null) {
          throw new IllegalArgumentException(path + ".material: unknown material: " + m);
        }
        yield ItemMatchers.material(mat);
      }
      case "custom_model_data", "custom-model-data", "cmd" -> {
        Object v = node.containsKey("value") ? node.get("value") : (node.containsKey("cmd") ? node.get("cmd") : node.get("customModelData"));
        if (v == null) {
          throw new IllegalArgumentException(path + ": missing value/cmd/customModelData");
        }
        int cmd = (int) requireDouble(java.util.Map.of("value", v), "value", path + ".value");
        yield ItemMatchers.customModelData(cmd);
      }
      case "pdc_tag", "pdc-tag", "tag" -> {
        String keyStr = requireString(node, "key", path + ".key");
        NamespacedKey key = NamespacedKey.fromString(keyStr);
        if (key == null) {
          throw new IllegalArgumentException(path + ".key: invalid NamespacedKey: " + keyStr);
        }
        yield ItemMatchers.tag(key);
      }
      case "lore_contains", "lore-contains" -> ItemMatchers.loreContains(requireString(node, "text", path + ".text"));
      case "and" -> {
        List<?> list = mapList(node, "matchers", path + ".matchers");
        if (list.isEmpty()) {
          throw new IllegalArgumentException(path + ".matchers: must not be empty");
        }
        ItemMatcher out = null;
        for (int i = 0; i < list.size(); i++) {
          ItemMatcher m = compileItemMatcher(list.get(i), path + ".matchers[" + i + "]");
          out = out == null ? m : ItemMatchers.and(out, m);
        }
        yield out;
      }
      case "or" -> {
        List<?> list = mapList(node, "matchers", path + ".matchers");
        if (list.isEmpty()) {
          throw new IllegalArgumentException(path + ".matchers: must not be empty");
        }
        ItemMatcher out = null;
        for (int i = 0; i < list.size(); i++) {
          ItemMatcher m = compileItemMatcher(list.get(i), path + ".matchers[" + i + "]");
          out = out == null ? m : ItemMatchers.or(out, m);
        }
        yield out;
      }
      default -> throw new IllegalArgumentException(path + ".type: unknown matcher type: " + type);
    };
  }

  private dev.patric.dungeonsreborn.effects.conditions.Condition compileCondition(Object raw, String path) {
    Map<String, Object> node = castMap(raw, path);
    String type = requireString(node, "type", path + ".type").trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case "always" -> Conditions.always();
      case "sneaking" -> Conditions.sneaking();
      case "permission" -> Conditions.permission(requireString(node, "permission", path + ".permission"));
      case "has_item_tag", "has-item-tag" -> {
        String keyStr = requireString(node, "key", path + ".key");
        NamespacedKey key = NamespacedKey.fromString(keyStr);
        if (key == null) {
          throw new IllegalArgumentException(path + ".key: invalid NamespacedKey: " + keyStr);
        }
        yield Conditions.hasItemTag(key);
      }
      case "has_target", "has-target" -> ctx -> lastEntity(ctx) != null;
      case "chance" -> {
        NumValue p = numValue(node, "probability", 0.5, path);
        yield ctx -> {
          double chance = evalDouble(p, ctx);
          if (chance < 0.0) {
            chance = 0.0;
          } else if (chance > 1.0) {
            chance = 1.0;
          }
          return ctx.rng().nextDouble() < chance;
        };
      }
      case "var_present", "var-present" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        yield ctx -> vars(ctx, scope).get(key) != null;
      }
      case "var_equals", "var-equals" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        ValueSupplier expected = varValue(node.get("value"), path + ".value");
        boolean ignoreCase = bool(node, "ignoreCase", false);
        yield ctx -> {
          Object actual = vars(ctx, scope).get(key);
          Object exp = expected.eval(ctx);
          if (actual == null && exp == null) {
            return true;
          }
          if (actual == null || exp == null) {
            return false;
          }
          if (exp instanceof Number en) {
            double actualNum = numericVar(actual, Double.NaN);
            return !Double.isNaN(actualNum) && Double.compare(actualNum, en.doubleValue()) == 0;
          }
          String as = String.valueOf(actual);
          String es = String.valueOf(exp);
          return ignoreCase ? as.equalsIgnoreCase(es) : as.equals(es);
        };
      }
      case "var_gte", "var-gte" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) >= evalDouble(threshold, ctx);
      }
      case "var_lte", "var-lte" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) <= evalDouble(threshold, ctx);
      }
      case "var_gt", "var-gt" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) > evalDouble(threshold, ctx);
      }
      case "var_lt", "var-lt" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) < evalDouble(threshold, ctx);
      }
      case "not" -> {
        var inner = compileCondition(require(node, "condition", path + ".condition"), path + ".condition");
        yield ctx -> !inner.test(ctx);
      }
      case "and" -> {
        Object v = require(node, "conditions", path + ".conditions");
        if (!(v instanceof List<?> list) || list.isEmpty()) {
          throw new IllegalArgumentException(path + ".conditions: expected non-empty list");
        }
        var compiled = new ArrayList<dev.patric.dungeonsreborn.effects.conditions.Condition>(list.size());
        for (int i = 0; i < list.size(); i++) {
          compiled.add(compileCondition(list.get(i), path + ".conditions[" + i + "]"));
        }
        yield ctx -> {
          for (var c : compiled) {
            if (!c.test(ctx)) {
              return false;
            }
          }
          return true;
        };
      }
      case "or" -> {
        Object v = require(node, "conditions", path + ".conditions");
        if (!(v instanceof List<?> list) || list.isEmpty()) {
          throw new IllegalArgumentException(path + ".conditions: expected non-empty list");
        }
        var compiled = new ArrayList<dev.patric.dungeonsreborn.effects.conditions.Condition>(list.size());
        for (int i = 0; i < list.size(); i++) {
          compiled.add(compileCondition(list.get(i), path + ".conditions[" + i + "]"));
        }
        yield ctx -> {
          for (var c : compiled) {
            if (c.test(ctx)) {
              return true;
            }
          }
          return false;
        };
      }
      default -> throw new IllegalArgumentException(path + ".type: unknown condition type: " + type);
    };
  }

  private dev.patric.dungeonsreborn.effects.actions.Action compileAction(Map<String, Object> node, String path, java.util.ArrayDeque<String> includeStack) {
    Object rawType = node.get("type");
    if (rawType == null) {
      throw new IllegalArgumentException(path + ": missing type");
    }
    String type = String.valueOf(rawType);
    if (type.isBlank()) {
      throw new IllegalArgumentException(path + ": type is blank");
    }
    type = type.toLowerCase(Locale.ROOT);

    return switch (type) {
      case "include" -> {
        String macro = requireString(node, "macro", path + ".macro");
        var def = macros.get(macro);
        if (def == null) {
          throw new IllegalArgumentException(path + ".macro: unknown macro: " + macro);
        }
        if (includeStack.contains(macro)) {
          throw new IllegalArgumentException(path + ": include cycle: " + String.join(" -> ", includeStack) + " -> " + macro);
        }
        includeStack.addLast(macro);
        try {
          yield compileAction(def, "macros." + macro, includeStack);
        } finally {
          includeStack.removeLast();
        }
      }
      case "sequence" -> {
        List<?> list = mapList(node, "actions", path + ".actions");
        var actions = new ArrayList<dev.patric.dungeonsreborn.effects.actions.Action>(list.size());
        for (int i = 0; i < list.size(); i++) {
          actions.add(compileAction(castMap(list.get(i), path + ".actions[" + i + "]"), path + ".actions[" + i + "]", includeStack));
        }
        yield Actions.sequence(actions.toArray(dev.patric.dungeonsreborn.effects.actions.Action[]::new));
      }
      case "delay" -> {
        NumValue ticksValue = numValue(node, "ticks", 0.0, path);
        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        yield new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long ticks = Math.max(0L, evalLong(ticksValue, ctx));
            final LivingEntity captured = lastEntity(ctx);
            final Object prev = ctx.state().get(YAML_LAST_ENTITY);
            if (ticks <= 0L) {
              if (captured != null) {
                ctx.state().put(YAML_LAST_ENTITY, captured);
              }
              try {
                return thenAction.executeWithHandle(ctx);
              } finally {
                ctx.state().put(YAML_LAST_ENTITY, prev);
              }
            }
            AtomicBoolean done = new AtomicBoolean(false);
            var handle = ctx.engine().runLater(ticks, () -> {
              if (captured != null) {
                ctx.state().put(YAML_LAST_ENTITY, captured);
              }
              try {
                thenAction.executeWithHandle(ctx);
              } finally {
                ctx.state().put(YAML_LAST_ENTITY, prev);
                done.set(true);
              }
            });
            ctx.state().track(handle);
            return scheduledHandle(handle, done);
          }
        };
      }
      case "repeat_ticks" -> {
        NumValue delayTicksValue = numValue(node, "delayTicks", 0.0, path);
        NumValue periodTicksValue = numValue(node, "periodTicks", 1.0, path);
        NumValue timesValue = numValue(node, "times", 1.0, path);
        Map<String, Object> action = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action body = compileAction(action, path + ".action", includeStack);
        yield new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long delayTicks = Math.max(0L, evalLong(delayTicksValue, ctx));
            long periodTicks = evalLong(periodTicksValue, ctx);
            int times = evalInt(timesValue, ctx);
            if (periodTicks <= 0 || times <= 0) {
              return ActionHandle.completed();
            }
            final LivingEntity captured = lastEntity(ctx);
            final Object prev = ctx.state().get(YAML_LAST_ENTITY);
            final int[] remaining = new int[] { times };
            AtomicBoolean done = new AtomicBoolean(false);
            final dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle[] handle = new dev.patric.dungeonsreborn.effects.EffectsEngine.ScheduledHandle[1];
            handle[0] = ctx.engine().runRepeating(delayTicks, periodTicks, () -> {
              if (handle[0] == null || handle[0].isCancelled()) {
                done.set(true);
                return;
              }
              if (remaining[0]-- <= 0) {
                handle[0].cancel();
                done.set(true);
                return;
              }
              if (captured != null) {
                ctx.state().put(YAML_LAST_ENTITY, captured);
              }
              try {
                body.executeWithHandle(ctx);
              } finally {
                ctx.state().put(YAML_LAST_ENTITY, prev);
              }
            });
            ctx.state().track(handle[0]);
            return scheduledHandle(handle[0], done);
          }
        };
      }
      case "set_var" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        ValueSupplier value = varValue(node.get("value"), path + ".value");
        yield ctx -> {
          Object v = value.eval(ctx);
          setVar(ctx, scope, key, v);
        };
      }
      case "inc_var" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue amount = numValue(node, "amount", 1.0, path);
        NumValue def = numValue(node, "default", 0.0, path);
        yield ctx -> {
          Object cur = vars(ctx, scope).get(key);
          double next = numericVar(cur, evalDouble(def, ctx)) + evalDouble(amount, ctx);
          setVar(ctx, scope, key, next);
        };
      }
      case "with_var" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        ValueSupplier value = varValue(node.get("value"), path + ".value");
        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        yield ctx -> {
          Map<String, Object> vars = vars(ctx, scope);
          boolean had = vars.containsKey(key);
          Object prev = vars.get(key);
          Object v = value.eval(ctx);
          setVar(ctx, scope, key, v);
          try {
            thenAction.execute(ctx);
          } finally {
            if (!had) {
              vars.remove(key);
            } else {
              vars.put(key, prev);
            }
          }
        };
      }
      case "debug_var" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        String label = string(node, "label", key);
        yield ctx -> {
          if (!ctx.engine().isDebugEnabled()) {
            return;
          }
          Object v = vars(ctx, scope).get(key);
          ctx.engine().debug("var(" + scope.name().toLowerCase(Locale.ROOT) + "): " + label + "=" + v);
        };
      }
      case "when" -> {
        var cond = compileCondition(require(node, "condition", path + ".condition"), path + ".condition");
        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action otherwise = Actions.noop();
        if (node.containsKey("otherwise")) {
          otherwise = compileAction(castMap(node.get("otherwise"), path + ".otherwise"), path + ".otherwise", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOtherwise = otherwise;
        yield ctx -> {
          if (cond.test(ctx)) {
            thenAction.execute(ctx);
          } else {
            finalOtherwise.execute(ctx);
          }
        };
      }
      case "random_choice_weighted" -> {
        Object v = require(node, "choices", path + ".choices");
        if (!(v instanceof List<?> choices) || choices.isEmpty()) {
          throw new IllegalArgumentException(path + ".choices: expected non-empty list");
        }
        record Choice(NumValue weight, dev.patric.dungeonsreborn.effects.actions.Action action) {
        }
        var compiled = new ArrayList<Choice>(choices.size());
        for (int i = 0; i < choices.size(); i++) {
          Map<String, Object> c = castMap(choices.get(i), path + ".choices[" + i + "]");
          NumValue w = requireNumValue(c, "weight", path + ".choices[" + i + "].weight");
          Map<String, Object> a = castMap(require(c, "action", path + ".choices[" + i + "].action"), path + ".choices[" + i + "].action");
          compiled.add(new Choice(w, compileAction(a, path + ".choices[" + i + "].action", includeStack)));
        }
        yield ctx -> {
          double totalWeight = 0.0;
          double[] weights = new double[compiled.size()];
          for (int i = 0; i < compiled.size(); i++) {
            double w = evalDouble(compiled.get(i).weight(), ctx);
            if (w > 0.0) {
              weights[i] = w;
              totalWeight += w;
            } else {
              weights[i] = 0.0;
            }
          }
          if (!(totalWeight > 0.0)) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("random_choice_weighted: no positive weights");
            }
            return;
          }
          double r = ctx.rng().nextDouble() * totalWeight;
          double acc = 0.0;
          for (int i = 0; i < compiled.size(); i++) {
            acc += weights[i];
            if (r <= acc) {
              compiled.get(i).action().execute(ctx);
              return;
            }
          }
          compiled.get(compiled.size() - 1).action().execute(ctx);
        };
      }
      case "invoke_ability" -> {
        String rawAbility = requireString(node, "ability", path + ".ability");
        String abilityId;
        try {
          abilityId = dev.patric.dungeonsreborn.effects.Ids.normalize(rawAbility);
        } catch (Exception ex) {
          throw new IllegalArgumentException(path + ".ability: invalid id: " + rawAbility);
        }

        String mode = string(node, "mode", "subgraph").trim().toLowerCase(Locale.ROOT);
        int maxDepth = intValue(node, "maxDepth", 8);
        if (maxDepth <= 0) {
          throw new IllegalArgumentException(path + ".maxDepth: must be > 0");
        }

        yield ctx -> {
          if ("cast".equals(mode)) {
            if (ctx.engine().hasAbility(abilityId)) {
              ctx.engine().cast(abilityId, ctx.caster());
            } else if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke_ability cast: ability not registered: " + abilityId);
            }
            return;
          }

          dev.patric.dungeonsreborn.effects.actions.Action target = yamlActionGraphs.get(abilityId);
          if (target == null) {
            // Fallback: allow invoking code-first abilities by creating a nested cast.
            if (ctx.engine().hasAbility(abilityId)) {
              ctx.engine().cast(abilityId, ctx.caster());
              return;
            }
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke_ability: unknown ability: " + abilityId);
            }
            return;
          }

          Object existing = ctx.state().get(YAML_INVOKE_STACK);
          @SuppressWarnings("unchecked")
          java.util.ArrayDeque<String> stack = existing instanceof java.util.ArrayDeque<?> d ? (java.util.ArrayDeque<String>) d : null;
          if (stack == null) {
            stack = new java.util.ArrayDeque<>();
            stack.addLast(ctx.abilityId());
            ctx.state().put(YAML_INVOKE_STACK, stack);
          } else if (stack.isEmpty()) {
            stack.addLast(ctx.abilityId());
          }

          if (stack.size() >= maxDepth) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke_ability: maxDepth reached (" + maxDepth + "): " + String.join(" -> ", stack));
            }
            return;
          }
          if (stack.contains(abilityId)) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke_ability: cycle detected: " + String.join(" -> ", stack) + " -> " + abilityId);
            }
            return;
          }

          stack.addLast(abilityId);
          try {
            target.execute(ctx);
          } finally {
            stack.removeLast();
          }
        };
      }
      case "sound" -> {
        Sound sound = soundValue(node, "sound", path + ".sound");
        NumValue volume = numValue(node, "volume", 1.0, path);
        NumValue pitch = numValue(node, "pitch", 1.0, path);
        yield ctx -> Actions.sound(sound, (float) evalDouble(volume, ctx), (float) evalDouble(pitch, ctx)).execute(ctx);
      }
      case "animate" -> {
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Map<String, Object> action = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action tickAction = compileAction(action, path + ".action", includeStack);
        yield ctx -> {
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Actions.animate(duration, period, easing, (tickCtx, t) -> {
            CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
            withTempVar(exec, VarScope.CAST, "t", t, () -> tickAction.execute(exec));
          }).execute(ctx);
        };
      }
      case "animate_realtime", "animate_real_time" -> {
        NumValue durationMillis = numValue(node, "durationMillis", 1000.0, path);
        NumValue periodMillis = numValue(node, "periodMillis", 50.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Map<String, Object> action = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action tickAction = compileAction(action, path + ".action", includeStack);
        yield ctx -> {
          long duration = evalLong(durationMillis, ctx);
          long period = evalLong(periodMillis, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Actions.animateRealTime(Duration.ofMillis(duration), Duration.ofMillis(period), easing, (tickCtx, t) -> {
            CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
            withTempVar(exec, VarScope.CAST, "t", t, () -> tickAction.execute(exec));
          }).execute(ctx);
        };
      }
      case "message" -> {
        String raw = requireString(node, "text", path + ".text");
        yield ctx -> {
          if (ctx.caster() instanceof Player player) {
            player.sendMessage(renderText(raw, ctx));
          }
        };
      }
      case "action_bar" -> {
        String raw = requireString(node, "text", path + ".text");
        yield ctx -> {
          if (ctx.caster() instanceof Player player) {
            player.sendActionBar(renderText(raw, ctx));
          }
        };
      }
      case "title" -> {
        String rawTitle = requireString(node, "title", path + ".title");
        String rawSubtitle = node.containsKey("subtitle") ? String.valueOf(node.get("subtitle")) : null;
        NumValue fadeInTicks = numValue(node, "fadeInTicks", 10.0, path);
        NumValue stayTicks = numValue(node, "stayTicks", 40.0, path);
        NumValue fadeOutTicks = numValue(node, "fadeOutTicks", 10.0, path);
        yield ctx -> {
          if (ctx.caster() instanceof Player player) {
            Component title = renderText(rawTitle, ctx);
            Component subtitle = rawSubtitle == null ? Component.empty() : renderText(rawSubtitle, ctx);
            long fadeIn = Math.max(0L, evalLong(fadeInTicks, ctx));
            long stay = Math.max(0L, evalLong(stayTicks, ctx));
            long fadeOut = Math.max(0L, evalLong(fadeOutTicks, ctx));
            Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L));
            player.showTitle(Title.title(title, subtitle, times));
          }
        };
      }
      case "particles_ring" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.0, path);
        NumValue points = numValue(node, "points", 24.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final var at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          Location center = resolveAt(ctx, at);
          var pe = ctx.engine().particles();
          if (center.getWorld() == null) {
            return;
          }
          final var world = center.getWorld();
          double r = evalDouble(radius, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          double off = evalDouble(offset, ctx);
          double ex = evalDouble(extra, ctx);
          if (r < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          dev.patric.dungeonsreborn.effects.particles.ParticleShapes.ring(center, new org.bukkit.util.Vector(0, 1, 0), r, pts,
              loc -> pe.emit(world, loc, particle, emitCount, off, off, off, ex, resolveParticleData(data, ctx, loc)));
        };
      }
      case "particles_point" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final var at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          Location loc = resolveAt(ctx, at);
          if (loc.getWorld() == null) {
            return;
          }
          int emitCount = evalInt(count, ctx);
          if (emitCount <= 0) {
            return;
          }
          double off = evalDouble(offset, ctx);
          double ex = evalDouble(extra, ctx);
          Object resolved = resolveParticleData(data, ctx, loc);
          ctx.engine().particles().emit(loc.getWorld(), loc, particle, emitCount, off, off, off, ex, resolved);
        };
      }
      case "particles_line" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue length = numValue(node, "length", 10.0, path);
        NumValue step = numValue(node, "step", 0.35, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double len = evalDouble(length, ctx);
          double st = evalDouble(step, ctx);
          int emitCount = evalInt(count, ctx);
          if (len <= 0.0 || st <= 0.0 || emitCount <= 0) {
            return;
          }
          Actions.particlesLine(particle, len, st, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_arc" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.2, path);
        NumValue angleDegrees = numValue(node, "angleDegrees", 90.0, path);
        NumValue points = numValue(node, "points", 24.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || angle <= 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesArc(particle, r, angle, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_disk" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.0, path);
        NumValue rings = numValue(node, "rings", 6.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 42.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int ringCount = evalInt(rings, ctx);
          int ppr = evalInt(pointsPerRing, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || ringCount <= 0 || ppr <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesDisk(particle, r, ringCount, ppr, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_sphere_shell" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.0, path);
        NumValue points = numValue(node, "points", 120.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesSphereShell(particle, r, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_sphere_filled", "particles_sphere_fill", "particles_sphere" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.0, path);
        NumValue points = numValue(node, "points", 120.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesSphereFilled(particle, r, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_helix" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.2, path);
        NumValue length = numValue(node, "length", 6.0, path);
        NumValue turns = numValue(node, "turns", 3.0, path);
        NumValue points = numValue(node, "points", 80.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double len = evalDouble(length, ctx);
          int t = evalInt(turns, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || len < 0.0 || t <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesHelix(particle, r, len, t, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_bezier" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue pointsPerMeter = numValue(node, "pointsPerMeter", 6.0, path);
        NumValue maxPoints = numValue(node, "maxPoints", 180.0, path);

        PointSpec p0 = pointSpec(node.get("p0"), path + ".p0");
        PointSpec p1 = pointSpec(node.get("p1"), path + ".p1");
        PointSpec p2 = pointSpec(node.get("p2"), path + ".p2");
        PointSpec p3 = pointSpec(node.get("p3"), path + ".p3");

        yield ctx -> {
          double ppm = evalDouble(pointsPerMeter, ctx);
          int maxPts = evalInt(maxPoints, ctx);
          int emitCount = evalInt(count, ctx);
          if (ppm <= 0.0 || maxPts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesBezier(
              c -> pointAt(c, p0),
              c -> pointAt(c, p1),
              c -> pointAt(c, p2),
              c -> pointAt(c, p3),
              ppm,
              maxPts,
              particle,
              emitCount,
              evalDouble(offset, ctx),
              evalDouble(extra, ctx),
              data).execute(ctx);
        };
      }
      case "particles_spline" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue pointsPerMeter = numValue(node, "pointsPerMeter", 10.0, path);
        NumValue maxPoints = numValue(node, "maxPoints", 320.0, path);

        Object rawPoints = require(node, "points", path + ".points");
        if (!(rawPoints instanceof List<?> list) || list.size() < 2) {
          throw new IllegalArgumentException(path + ".points: expected a list with at least 2 point objects");
        }
        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(list.size());
        for (int i = 0; i < list.size(); i++) {
          PointSpec p = pointSpec(list.get(i), path + ".points[" + i + "]");
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          double ppm = evalDouble(pointsPerMeter, ctx);
          int maxPts = evalInt(maxPoints, ctx);
          int emitCount = evalInt(count, ctx);
          if (ppm <= 0.0 || maxPts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesSpline(fns, ppm, maxPts, particle, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_cone" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue length = numValue(node, "length", 8.0, path);
        NumValue angleDegrees = numValue(node, "angleDegrees", 70.0, path);
        NumValue rings = numValue(node, "rings", 10.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 18.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double len = evalDouble(length, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          int ringCount = evalInt(rings, ctx);
          int ppr = evalInt(pointsPerRing, ctx);
          int emitCount = evalInt(count, ctx);
          if (len <= 0.0 || angle <= 0.0 || ringCount <= 0 || ppr <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesCone(particle, len, angle, ringCount, ppr, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data)
              .execute(ctx);
        };
      }
      case "particles_cylinder" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.4, path);
        NumValue height = numValue(node, "height", 3.2, path);
        NumValue rings = numValue(node, "rings", 10.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 24.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double h = evalDouble(height, ctx);
          int ringCount = evalInt(rings, ctx);
          int ppr = evalInt(pointsPerRing, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || h < 0.0 || ringCount <= 0 || ppr <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesCylinder(particle, r, h, ringCount, ppr, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data)
              .execute(ctx);
        };
      }
      case "particles_box" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue xRadius = numValue(node, "xRadius", 2.2, path);
        NumValue yRadius = numValue(node, "yRadius", 1.6, path);
        NumValue zRadius = numValue(node, "zRadius", 2.2, path);
        NumValue step = numValue(node, "step", 0.35, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double xr = evalDouble(xRadius, ctx);
          double yr = evalDouble(yRadius, ctx);
          double zr = evalDouble(zRadius, ctx);
          double st = evalDouble(step, ctx);
          int emitCount = evalInt(count, ctx);
          if (xr < 0.0 || yr < 0.0 || zr < 0.0 || st <= 0.0 || emitCount <= 0) {
            return;
          }
          Actions.particlesBox(particle, xr, yr, zr, st, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "particles_polygon" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.5, path);
        NumValue sides = numValue(node, "sides", 6.0, path);
        NumValue pointsPerEdge = numValue(node, "pointsPerEdge", 10.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int s = evalInt(sides, ctx);
          int ppe = evalInt(pointsPerEdge, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || s <= 0 || ppe <= 0 || emitCount <= 0) {
            return;
          }
          Actions.particlesPolygon(particle, new Vector(0, 1, 0), r, s, ppe, emitCount, evalDouble(offset, ctx),
              evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "preset_shockwave" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue startRadius = numValue(node, "startRadius", 0.5, path);
        NumValue endRadius = numValue(node, "endRadius", 7.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.OUT_QUAD);
        NumValue points = numValue(node, "points", 56.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.presetShockwave(particle, start, end, duration, period, easing, pts, emitCount, evalDouble(offset, ctx),
              evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "preset_orbit" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.4, path);
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.LINEAR);
        NumValue copies = numValue(node, "copies", 3.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.02, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int c = evalInt(copies, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || duration <= 0 || period <= 0 || c <= 0 || emitCount <= 0) {
            return;
          }
          Actions.presetOrbit(particle, r, duration, period, easing, c, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data)
              .execute(ctx);
        };
      }
      case "preset_swirl" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.8, path);
        NumValue height = numValue(node, "height", 2.6, path);
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue points = numValue(node, "points", 22.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double h = evalDouble(height, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || h < 0.0 || duration <= 0 || period <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Actions.presetSwirl(particle, r, h, duration, period, easing, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx), data)
              .execute(ctx);
        };
      }
      case "preset_beam_chargeup" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue startLength = numValue(node, "startLength", 0.0, path);
        NumValue endLength = node.containsKey("endLength")
            ? numValue(node, "endLength", 10.0, path)
            : numValue(node, "length", 10.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 20.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue step = numValue(node, "step", 0.35, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        yield ctx -> {
          double start = evalDouble(startLength, ctx);
          double end = evalDouble(endLength, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          double st = evalDouble(step, ctx);
          int emitCount = evalInt(count, ctx);
          if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || st <= 0.0 || emitCount <= 0) {
            return;
          }
          Actions.presetBeamChargeup(particle, start, end, duration, period, easing, st, emitCount, evalDouble(offset, ctx),
              evalDouble(extra, ctx), data).execute(ctx);
        };
      }
      case "chance" -> {
        NumValue probability = numValue(node, "probability", 0.5, path);
        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action otherwise = Actions.noop();
        if (node.containsKey("otherwise")) {
          otherwise = compileAction(castMap(node.get("otherwise"), path + ".otherwise"), path + ".otherwise", includeStack);
        }
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = otherwise;
        yield ctx -> {
          double p = evalDouble(probability, ctx);
          if (p <= 0.0) {
            elseAction.execute(ctx);
            return;
          }
          if (p >= 1.0) {
            thenAction.execute(ctx);
            return;
          }
          if (ctx.rng().nextDouble() < p) {
            thenAction.execute(ctx);
          } else {
            elseAction.execute(ctx);
          }
        };
      }
      case "debug_log" -> {
        String raw = requireString(node, "message", path + ".message");
        yield ctx -> {
          if (!ctx.engine().isDebugEnabled()) {
            return;
          }
          ctx.engine().debug(interpolate(raw, ctx));
        };
      }
      case "raycast_hit_entity" -> {
        NumValue maxDistance = numValue(node, "maxDistance", 20.0, path);
        NumValue raySize = numValue(node, "raySize", 0.35, path);
        boolean stopOnBlock = bool(node, "stopOnBlock", true);
        boolean ignoreCaster = bool(node, "ignoreCaster", true);
        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action otherwise = Actions.noop();
        if (node.containsKey("otherwise")) {
          otherwise = compileAction(castMap(node.get("otherwise"), path + ".otherwise"), path + ".otherwise", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOtherwise = otherwise;
        yield ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double size = evalDouble(raySize, ctx);
          if (max <= 0.0 || size < 0.0) {
            finalOtherwise.execute(ctx);
            return;
          }
          var hits = Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, e -> true).select(ctx);
          if (hits.isEmpty()) {
            finalOtherwise.execute(ctx);
            return;
          }
          LivingEntity target = hits.get(0);
          ctx.state().put(YAML_LAST_ENTITY, target);
          thenAction.execute(ctx);
          Object hook = ctx.state().get(DSL_ON_HIT);
          if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
            action.execute(ctx);
          }
        };
      }
      case "for_each_target" -> {
        Map<String, Object> targeterNode = castMap(require(node, "targeter", path + ".targeter"), path + ".targeter");
        Targeter<LivingEntity> targeter = compileTargeter(targeterNode, path + ".targeter");

        String mode = string(node, "mode", "each").trim().toLowerCase(Locale.ROOT);
        boolean firstOnly = switch (mode) {
          case "each" -> false;
          case "first" -> true;
          default -> throw new IllegalArgumentException(path + ".mode: invalid mode=" + mode + " (use each|first)");
        };

        NumValue maxTargets = numValue(node, "maxTargets", 0.0, path);

        AtMode originAt = parseAt(string(node, "originAt", "origin"), path + ".originAt");

        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action otherwise = Actions.noop();
        if (node.containsKey("otherwise")) {
          otherwise = compileAction(castMap(node.get("otherwise"), path + ".otherwise"), path + ".otherwise", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOtherwise = otherwise;

        yield ctx -> {
          CastContext selectCtx = ctx;
          if (originAt != AtMode.ORIGIN) {
            Location origin = resolveAt(ctx, originAt);
            if (origin.getWorld() != null) {
              selectCtx = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  ctx.direction().clone(),
                  ctx.itemInHand());
            }
          }

          List<LivingEntity> targets = targeter.select(selectCtx);
          if (targets.isEmpty()) {
            finalOtherwise.execute(ctx);
            return;
          }

          int limit = targets.size();
          if (firstOnly) {
            limit = 1;
          }
          int max = evalInt(maxTargets, ctx);
          if (max > 0) {
            limit = Math.min(limit, max);
          }

          for (int i = 0; i < limit; i++) {
            ctx.state().put(YAML_LAST_ENTITY, targets.get(i));
            thenAction.execute(ctx);
          }
        };
      }
      case "damage" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damage(dmg, p).execute(ctx, target);
          }
        };
      }
      case "damage_typed" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        DamageType dmgType = damageTypeValue(node, path + ".damageType");
        boolean ignoreResistance = bool(node, "ignoreResistance", false);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damageTyped(dmg, dmgType, ignoreResistance, p).execute(ctx, target);
          }
        };
      }
      case "set_resistance" -> {
        DamageType dmgType = damageTypeValue(node, path + ".damageType");
        NumValue multiplier = requireNumValue(node, "multiplier", path + ".multiplier");
        NumValue durationTicks = numValue(node, "durationTicks", 0.0, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double mult = evalDouble(multiplier, ctx);
          if (!Double.isFinite(mult) || mult < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), dmgType, mult);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration,
                () -> ctx.engine().restoreResistance(target.getUniqueId(), dmgType, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      case "add_resistance" -> {
        DamageType dmgType = damageTypeValue(node, path + ".damageType");
        NumValue delta = requireNumValue(node, "delta", path + ".delta");
        NumValue durationTicks = numValue(node, "durationTicks", 0.0, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double add = evalDouble(delta, ctx);
          if (!Double.isFinite(add)) {
            return;
          }
          double current = ctx.engine().resistanceMultiplier(target.getUniqueId(), dmgType);
          double next = current + add;
          if (!Double.isFinite(next) || next < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), dmgType, next);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration,
                () -> ctx.engine().restoreResistance(target.getUniqueId(), dmgType, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      case "clear_resistance" -> {
        boolean hasType = hasDamageType(node);
        DamageType dmgType = hasType ? damageTypeValue(node, path + ".damageType") : null;
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          if (dmgType == null) {
            ctx.engine().clearResistances(target.getUniqueId());
          } else {
            ctx.engine().clearResistance(target.getUniqueId(), dmgType);
          }
        };
      }
      case "set_reflect" -> {
        NumValue ratio = numValue(node, "ratio", 0.25, path);
        NumValue flat = numValue(node, "flat", 0.0, path);
        boolean ignoreResistance = bool(node, "ignoreResistance", false);
        DamageType dmgType = hasDamageType(node) ? damageTypeValue(node, path + ".damageType") : null;
        NumValue durationTicks = numValue(node, "durationTicks", 0.0, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double r = evalDouble(ratio, ctx);
          double f = evalDouble(flat, ctx);
          if (!(r > 0.0) && !(f > 0.0)) {
            return;
          }
          long token = ctx.engine().setReflect(target.getUniqueId(),
              new EffectsEngine.ReflectSpec(r, f, dmgType, ignoreResistance, p));
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().clearReflect(target.getUniqueId(), token));
            ctx.state().track(handle);
          }
        };
      }
      case "clear_reflect" -> {
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          ctx.engine().clearReflect(target.getUniqueId());
        };
      }
      case "damage_percent" -> {
        NumValue percent = numValue(node, "percent", 0.15, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double pct = evalDouble(percent, ctx);
            if (pct <= 0.0) {
              return;
            }
            EntityActions.damagePercent(pct, p).execute(ctx, target);
          }
        };
      }
      case "damage_true" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damageTrue(dmg, p).execute(ctx, target);
          }
        };
      }
      case "damage_falloff" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue maxDistance = numValue(node, "maxDistance", 12.0, path);
        NumValue minMultiplier = numValue(node, "minMultiplier", 0.2, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            double maxDist = evalDouble(maxDistance, ctx);
            double minMult = evalDouble(minMultiplier, ctx);
            if (dmg <= 0.0 || maxDist <= 0.0) {
              return;
            }
            EntityActions.damageWithFalloff(dmg, maxDist, minMult, p).execute(ctx, target);
          }
        };
      }
      case "damage_crit" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue critChance = numValue(node, "critChance", 0.2, path);
        NumValue critMultiplier = numValue(node, "critMultiplier", 1.5, path);
        NumValue headshotMultiplier = numValue(node, "headshotMultiplier", 1.0, path);
        NumValue headshotThreshold = numValue(node, "headshotThreshold", 0.25, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damageCrit(
                dmg,
                evalDouble(critChance, ctx),
                evalDouble(critMultiplier, ctx),
                evalDouble(headshotMultiplier, ctx),
                evalDouble(headshotThreshold, ctx),
                p).execute(ctx, target);
          }
        };
      }
      case "damage_lifesteal" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue ratio = numValue(node, "ratio", 0.25, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damageLifesteal(dmg, evalDouble(ratio, ctx), p).execute(ctx, target);
          }
        };
      }
      case "damage_dot", "damage_over_time" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue periodTicks = numValue(node, "periodTicks", 10.0, path);
        NumValue times = numValue(node, "times", 5.0, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            long period = evalLong(periodTicks, ctx);
            int count = evalInt(times, ctx);
            if (dmg <= 0.0 || period <= 0 || count <= 0) {
              return;
            }
            EntityActions.damageOverTime(dmg, period, count, p).execute(ctx, target);
          }
        };
      }
      case "damage_chain", "chain_damage", "chain_lightning" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue radius = numValue(node, "radius", 6.0, path);
        NumValue maxJumps = numValue(node, "maxJumps", 4.0, path);
        NumValue delayTicks = numValue(node, "delayTicks", 2.0, path);
        NumValue falloff = numValue(node, "falloff", 0.8, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (node.containsKey("onHit")) {
          onHitAction = compileAction(castMap(node.get("onHit"), path + ".onHit"), path + ".onHit", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            double r = evalDouble(radius, ctx);
            int jumps = evalInt(maxJumps, ctx);
            long delay = evalLong(delayTicks, ctx);
            double f = evalDouble(falloff, ctx);
            if (dmg <= 0.0 || r <= 0.0 || jumps <= 0 || delay < 0) {
              return;
            }
            EntityActions.chainDamage(dmg, r, jumps, delay, f, p, (cast, hit) -> {
              Object prev = cast.state().get(YAML_LAST_ENTITY);
              cast.state().put(YAML_LAST_ENTITY, hit);
              try {
                finalOnHit.execute(cast);
              } finally {
                cast.state().put(YAML_LAST_ENTITY, prev);
              }
            }).execute(ctx, target);
          }
        };
      }
      case "heal" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double heal = evalDouble(amount, ctx);
            if (heal <= 0.0) {
              return;
            }
            EntityActions.heal(heal).execute(ctx, target);
          }
        };
      }
      case "potion" -> {
        PotionEffectType effect = potionEffectValue(node, "effect", path + ".effect");
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue amplifier = numValue(node, "amplifier", 0.0, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            long ticks = Math.max(1L, evalLong(durationTicks, ctx));
            int amp = Math.max(0, evalInt(amplifier, ctx));
            EntityActions.potion(effect, Duration.ofMillis(ticks * 50L), amp).execute(ctx, target);
          }
        };
      }
      case "knockback" -> {
        NumValue horizontal = numValue(node, "horizontal", 1.0, path);
        NumValue vertical = numValue(node, "vertical", 0.35, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            EntityActions.knockbackFromOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
          }
        };
      }
      case "pull" -> {
        NumValue horizontal = numValue(node, "horizontal", 0.75, path);
        NumValue vertical = numValue(node, "vertical", 0.08, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            EntityActions.pullToOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
          }
        };
      }
      case "projectile" -> {
        NumValue speedPerTick = numValue(node, "speedPerTick", 1.3, path);
        NumValue maxDistance = numValue(node, "maxDistance", 24.0, path);
        NumValue hitRadius = numValue(node, "hitRadius", 0.25, path);
        boolean ignoreCaster = bool(node, "ignoreCaster", true);
        String bc = string(node, "blockCollision", "stop").toUpperCase(Locale.ROOT);
        boolean bounce = "BOUNCE".equals(bc);
        boolean passThrough = "PASS_THROUGH".equals(bc) || "PASS-THROUGH".equals(bc) || "PASS".equals(bc);
        Map<String, Object> bounces = bounce
            ? (node.containsKey("bounces") ? castMap(node.get("bounces"), path + ".bounces") : Map.of())
            : Map.of();
        NumValue bounceMax = bounce ? numValue(bounces, "max", 0.0, path + ".bounces") : null;
        NumValue bounceRestitution = bounce ? numValue(bounces, "restitution", 0.9, path + ".bounces") : null;

        boolean hasTrail = node.containsKey("trail");
        Map<String, Object> trail = hasTrail ? castMap(node.get("trail"), path + ".trail") : Map.of();
        Particle trailParticle = hasTrail ? enumValue(trail, "particle", Particle.class, path + ".trail.particle") : Particle.END_ROD;
        NumValue trailCount = hasTrail ? numValue(trail, "count", 1.0, path + ".trail") : null;
        NumValue trailOffset = hasTrail ? numValue(trail, "offset", 0.0, path + ".trail") : null;
        NumValue trailExtra = hasTrail ? numValue(trail, "extra", 0.0, path + ".trail") : null;

        if (node.containsKey("trail")) {
          castMap(node.get("trail"), path + ".trail");
        }

        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (node.containsKey("onHit")) {
          Map<String, Object> onHit = castMap(node.get("onHit"), path + ".onHit");
          onHitAction = compileAction(onHit, path + ".onHit", includeStack);
        }

        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        yield ctx -> {
          double speed = evalDouble(speedPerTick, ctx);
          double max = evalDouble(maxDistance, ctx);
          double radius = evalDouble(hitRadius, ctx);
          if (speed <= 0.0 || max <= 0.0 || radius < 0.0) {
            return;
          }
          ProjectileSpec.Builder b = ProjectileSpec.builder()
              .speedPerTick(speed)
              .maxDistance(max)
              .hitRadius(radius)
              .ignoreCaster(ignoreCaster);
          if (passThrough) {
            b.stopOnBlock(false);
          } else if (bounce) {
            int maxBounce = bounceMax == null ? 0 : Math.max(0, evalInt(bounceMax, ctx));
            double restitution = bounceRestitution == null ? 0.9 : Math.max(0.0, evalDouble(bounceRestitution, ctx));
            b.bounces(maxBounce, restitution);
          } else {
            b.stopOnBlock(true);
          }

          if (hasTrail) {
            int count = trailCount == null ? 1 : Math.max(0, evalInt(trailCount, ctx));
            double offset = trailOffset == null ? 0.0 : Math.max(0.0, evalDouble(trailOffset, ctx));
            double extra = trailExtra == null ? 0.0 : evalDouble(trailExtra, ctx);
            b.trail(trailParticle, count, offset, extra);
          }

          b.onHit(hit -> {
            CastContext cast = hit.cast();
            cast.state().put(YAML_LAST_ENTITY, hit.hitEntity());
            finalOnHit.execute(cast);
            Object hook = cast.state().get(DSL_ON_HIT);
            if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
              action.execute(cast);
            }
          });

          Actions.projectile(b.build()).execute(ctx);
        };
      }
      case "minion_summon", "summon_minion", "minions" -> {
        String mobId = requireString(node, "mob", path + ".mob");
        String minionId = string(node, "id", string(node, "minionId", mobId));
        int count = intValue(node, "count", 1);
        long duration = longValue(node, "durationTicks", 20L * 30L);
        double radius = doubleValue(node, "radius", 1.5);
        boolean despawnOnLogout = bool(node, "despawnOnLogout", true);
        MinionScaling scaling = parseMinionScaling(node.get("scale"), path + ".scale");
        Map<DamageType, Double> resistances = parseResistanceMap(node.get("resistances"), path + ".resistances");
        java.util.Set<DamageType> immunities = parseDamageTypeSet(node.get("immunities"), path + ".immunities");
        dev.patric.dungeonsreborn.effects.minions.MinionMode mode = parseMinionMode(string(node, "mode", null), path + ".mode");
        java.util.List<dev.patric.dungeonsreborn.effects.minions.MinionPassiveSpec> passives = parseMinionPassives(node.get("passives"), path + ".passives");
        java.util.List<dev.patric.dungeonsreborn.effects.minions.MinionSpecialAttackSpec> specialAttacks = parseMinionSpecialAttacks(node.get("specialAttacks"), path + ".specialAttacks");
        if (count <= 0) {
          throw new IllegalArgumentException(path + ".count: must be > 0");
        }
        if (duration <= 0) {
          throw new IllegalArgumentException(path + ".durationTicks: must be > 0");
        }
        MinionManager minions = resolveMinionManager(path);
        yield ctx -> {
          if (!minions.hasMob(mobId)) {
            throw new IllegalArgumentException(path + ".mob: unknown mob id: " + mobId);
          }
          java.util.UUID ownerId = ctx.caster().getUniqueId();
          MinionSpec spec = new MinionSpec(minionId, mobId, count, duration, ownerId, radius, scaling, resistances, immunities, despawnOnLogout, mode, passives, specialAttacks);
          java.util.List<LivingEntity> spawned = minions.summon(spec, ctx.caster().getLocation());
          java.util.List<java.util.UUID> ids = new java.util.ArrayList<>(spawned.size());
          for (LivingEntity living : spawned) {
            ids.add(living.getUniqueId());
          }
          ctx.state().put(Vars.MINION_ID, minionId);
          ctx.state().put(Vars.MINION_COUNT, spawned.size());
          ctx.state().put(Vars.MINION_IDS, java.util.List.copyOf(ids));
          ctx.state().put(Vars.MINION_DURATION, duration);
        };
      }
      default -> throw new IllegalArgumentException(path + ": unknown action type: " + type);
    };
  }

  private MinionManager resolveMinionManager(String path) {
    if (plugin instanceof DungeonsRebornPlugin dr) {
      MinionManager minions = dr.minionManager();
      if (minions != null) {
        return minions;
      }
    }
    throw new IllegalArgumentException(path + ": minion system not available");
  }

  private ScriptHandlers compileScript(Object raw, String path) {
    String language = "dsl-v1";
    String source = null;
    String filePath = null;
    Integer explicitVersion = null;
    if (raw instanceof String s) {
      source = s;
    } else if (raw instanceof ConfigurationSection sec) {
      language = sec.getString("language", "dsl-v1");
      explicitVersion = parseScriptVersion(sec.get("version"), path + ".version");
      source = sec.getString("source");
      filePath = sec.getString("file");
    } else if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = castMap(map, path);
      language = string(node, "language", "dsl-v1");
      explicitVersion = parseScriptVersion(node.get("version"), path + ".version");
      Object src = node.get("source");
      if (src != null) {
        source = String.valueOf(src);
      }
      Object file = node.get("file");
      if (file != null) {
        filePath = String.valueOf(file);
      }
    } else {
      throw new IllegalArgumentException(path + ": expected string or object");
    }

    String lang = language == null ? "dsl-v1" : language.trim().toLowerCase(Locale.ROOT);
    if (!lang.equals("dsl-v1") && !lang.equals("dsl")) {
      throw new IllegalArgumentException(path + ".language: unsupported language: " + language);
    }
    if (filePath != null && !filePath.isBlank()) {
      return compileScriptFileCached(filePath, path + ".file", explicitVersion);
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException(path + ": missing source");
    }
    ScriptSource script = parseScriptSource(source, path, explicitVersion);
    return new DslParser(script.source(), path).parse();
  }

  public dev.patric.dungeonsreborn.effects.actions.Action compileScriptAction(Object raw, String path, String scriptId) {
    ScriptHandlers handlers = compileScript(raw, path);
    String resolvedId = scriptId == null || scriptId.isBlank() ? path : scriptId;
    return buildScriptAction(handlers, resolvedId);
  }

  private void debugScript(String message) {
    if (!scriptDebug && !scriptTrace) {
      return;
    }
    effectsLog.info("[Effects][Script] " + message);
  }

  private record ScriptHandlers(
      dev.patric.dungeonsreborn.effects.actions.Action onCast,
      dev.patric.dungeonsreborn.effects.actions.Action onCancel,
      dev.patric.dungeonsreborn.effects.actions.Action onEnd,
      dev.patric.dungeonsreborn.effects.actions.Action onHit,
      dev.patric.dungeonsreborn.effects.actions.Action onFinish,
      dev.patric.dungeonsreborn.effects.actions.Action onCostFail,
      dev.patric.dungeonsreborn.effects.actions.Action onCooldownFail) {
  }

  private record ScriptSource(String source, int version) {
  }

  private record ScriptCacheEntry(long lastModified, ScriptHandlers handlers, String error, int version) {
  }

  private static final class ScriptMetrics {
    private final java.util.concurrent.atomic.LongAdder executions = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder totalNanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder errors = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.AtomicReference<String> lastError = new java.util.concurrent.atomic.AtomicReference<>();
  }

  public record ScriptMetricSnapshot(String scriptId, long executions, long totalNanos, long errors, String lastError) {
  }

  private ScriptMetrics metricsFor(String scriptId) {
    return scriptMetrics.computeIfAbsent(scriptId, k -> new ScriptMetrics());
  }

  public java.util.List<ScriptMetricSnapshot> scriptMetrics() {
    java.util.List<ScriptMetricSnapshot> out = new java.util.ArrayList<>();
    for (Map.Entry<String, ScriptMetrics> entry : scriptMetrics.entrySet()) {
      ScriptMetrics m = entry.getValue();
      out.add(new ScriptMetricSnapshot(
          entry.getKey(),
          m.executions.sum(),
          m.totalNanos.sum(),
          m.errors.sum(),
          m.lastError.get()));
    }
    out.sort(java.util.Comparator.comparingLong(ScriptMetricSnapshot::totalNanos).reversed());
    return out;
  }

  public void clearScriptMetrics() {
    scriptMetrics.clear();
  }

  private void recordScriptTime(String scriptId, long nanos) {
    ScriptMetrics metrics = metricsFor(scriptId);
    metrics.executions.increment();
    metrics.totalNanos.add(Math.max(0L, nanos));
  }

  private void recordScriptError(String scriptId, Throwable error) {
    ScriptMetrics metrics = metricsFor(scriptId);
    metrics.errors.increment();
    if (error != null) {
      String msg = error.getMessage();
      metrics.lastError.set(msg == null || msg.isBlank() ? error.getClass().getSimpleName() : msg);
    }
  }

  private String scriptId(CastContext ctx) {
    Object id = ctx.state().get(DSL_SCRIPT_ID);
    return id == null ? "dsl" : id.toString();
  }

  private dev.patric.dungeonsreborn.effects.actions.Action buildScriptAction(ScriptHandlers handlers, String scriptId) {
    dev.patric.dungeonsreborn.effects.actions.Action onCast = handlers.onCast() == null ? Actions.noop() : handlers.onCast();
    dev.patric.dungeonsreborn.effects.actions.Action onCancel = handlers.onCancel();
    dev.patric.dungeonsreborn.effects.actions.Action onEnd = handlers.onEnd();
    dev.patric.dungeonsreborn.effects.actions.Action onHit = handlers.onHit();
    dev.patric.dungeonsreborn.effects.actions.Action onFinish = handlers.onFinish();
    dev.patric.dungeonsreborn.effects.actions.Action onCostFail = handlers.onCostFail();
    dev.patric.dungeonsreborn.effects.actions.Action onCooldownFail = handlers.onCooldownFail();
    return ctx -> {
      ctx.state().put(DSL_SCRIPT_ID, scriptId);
      if (onCancel != null) {
        ctx.state().onCancel(() -> onCancel.execute(ctx));
      }
      if (onHit != null) {
        ctx.state().put(DSL_ON_HIT, onHit);
      }
      if (onFinish != null) {
        ctx.state().put(DSL_ON_FINISH, onFinish);
      }
      if (onCostFail != null) {
        ctx.state().put(DSL_ON_COST_FAIL, onCostFail);
      }
      if (onCooldownFail != null) {
        ctx.state().put(DSL_ON_COOLDOWN_FAIL, onCooldownFail);
      }
      long start = System.nanoTime();
      try {
        onCast.execute(ctx);
      } catch (RuntimeException ex) {
        recordScriptError(scriptId, ex);
        throw ex;
      } finally {
        recordScriptTime(scriptId, System.nanoTime() - start);
        ctx.state().put(DSL_CAST_DONE, Boolean.TRUE);
        if (onEnd != null) {
          try {
            onEnd.execute(ctx);
          } catch (RuntimeException ex) {
            recordScriptError(scriptId, ex);
            throw ex;
          }
        }
        runFinishIfReady(ctx);
      }
    };
  }

  private void warmScriptCache(List<String> errors) {
    scriptCache.clear();
    File scriptsDir = new File(plugin.getDataFolder(), "effects/scripts");
    if (!scriptsDir.exists()) {
      return;
    }
    try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(scriptsDir.toPath())) {
      for (java.nio.file.Path path : walk.filter(java.nio.file.Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".es"))
          .toList()) {
        try {
          compileScriptFileCached(path.toString(), path.toString(), null);
        } catch (IllegalArgumentException ex) {
          errors.add(ex.getMessage());
        }
      }
    } catch (java.io.IOException ex) {
      errors.add("scripts: failed to read scripts folder (" + ex.getMessage() + ")");
    }
  }

  private java.nio.file.Path resolveScriptPath(String filePath, String path) {
    String raw = filePath.trim();
    java.nio.file.Path file = java.nio.file.Path.of(raw);
    if (!file.isAbsolute()) {
      java.io.File scriptsDir = new java.io.File(plugin.getDataFolder(), "effects/scripts");
      java.io.File candidate = new java.io.File(scriptsDir, raw);
      if (!candidate.exists()) {
        candidate = new java.io.File(plugin.getDataFolder(), raw);
      }
      file = candidate.toPath();
    }
    if (!java.nio.file.Files.exists(file)) {
      throw new IllegalArgumentException(path + ": file not found: " + file);
    }
    return file.toAbsolutePath().normalize();
  }

  private ScriptSource readScriptFile(java.nio.file.Path file, String path, Integer explicitVersion) {
    try {
      String source = java.nio.file.Files.readString(file);
      return parseScriptSource(source, path, explicitVersion);
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException(path + ": failed to read " + file + " (" + ex.getMessage() + ")");
    }
  }

  private ScriptHandlers compileScriptFileCached(String filePath, String path, Integer explicitVersion) {
    java.nio.file.Path file = resolveScriptPath(filePath, path);
    long lastModified;
    try {
      lastModified = java.nio.file.Files.getLastModifiedTime(file).toMillis();
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException(path + ": failed to read " + file + " (" + ex.getMessage() + ")");
    }
    ScriptCacheEntry cached = scriptCache.get(file);
    if (cached != null && cached.lastModified() == lastModified) {
      if (explicitVersion != null && cached.version() != explicitVersion) {
        throw new IllegalArgumentException(path + ": script version mismatch (expected " + explicitVersion + ", found " + cached.version() + ")");
      }
      if (cached.error() != null) {
        throw new IllegalArgumentException(cached.error());
      }
      return cached.handlers();
    }

    try {
      ScriptSource source = readScriptFile(file, path, explicitVersion);
      ScriptHandlers handlers = new DslParser(source.source(), file.toString()).parse();
      scriptCache.put(file, new ScriptCacheEntry(lastModified, handlers, null, source.version()));
      return handlers;
    } catch (IllegalArgumentException ex) {
      scriptCache.put(file, new ScriptCacheEntry(lastModified, null, ex.getMessage(), explicitVersion == null ? SCRIPT_VERSION : explicitVersion));
      throw ex;
    }
  }

  private ScriptSource parseScriptSource(String source, String path, Integer explicitVersion) {
    Integer headerVersion = detectScriptHeaderVersion(source);
    Integer version = explicitVersion != null ? explicitVersion : headerVersion;
    if (explicitVersion != null && headerVersion != null && !explicitVersion.equals(headerVersion)) {
      throw new IllegalArgumentException(path + ": script version mismatch (header=" + headerVersion + ", expected=" + explicitVersion + ")");
    }
    int resolved = version == null ? SCRIPT_VERSION : version;
    if (resolved != SCRIPT_VERSION) {
      throw new IllegalArgumentException(path + ": unsupported script version: " + resolved + " (expected " + SCRIPT_VERSION + ")");
    }
    return new ScriptSource(source, resolved);
  }

  private Integer detectScriptHeaderVersion(String source) {
    String[] lines = source.split("\\R", -1);
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (!trimmed.startsWith("#")) {
        break;
      }
      Integer version = parseVersionDirective(trimmed);
      if (version != null) {
        return version;
      }
    }
    return null;
  }

  private Integer parseVersionDirective(String line) {
    String text = line.trim();
    if (!text.startsWith("#")) {
      return null;
    }
    text = text.substring(1).trim();
    if (text.startsWith("!")) {
      text = text.substring(1).trim();
    }
    String lowered = text.toLowerCase(Locale.ROOT);
    if (lowered.startsWith("dsl")) {
      lowered = lowered.substring(3).trim();
    } else if (lowered.startsWith("version")) {
      lowered = lowered.substring("version".length()).trim();
    } else {
      return null;
    }
    if (lowered.startsWith(":") || lowered.startsWith("-")) {
      lowered = lowered.substring(1).trim();
    }
    if (lowered.startsWith("v")) {
      lowered = lowered.substring(1).trim();
    }
    if (lowered.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(lowered);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Integer parseScriptVersion(Object raw, String path) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number n) {
      return n.intValue();
    }
    String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    if (text.startsWith("dsl")) {
      text = text.substring(3).trim();
    }
    if (text.startsWith("v")) {
      text = text.substring(1).trim();
    }
    if (text.startsWith(":") || text.startsWith("-")) {
      text = text.substring(1).trim();
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": invalid script version: " + raw);
    }
  }

  private final class DslParser {
    private enum TokenType {
      IDENT,
      NUMBER,
      STRING,
      OP,
      COMP,
      LPAREN,
      RPAREN,
      COMMA,
      COLON,
      LBRACE,
      RBRACE,
      EQUALS,
      EOF
    }

    private record Token(TokenType type, String text, double number, int line, int column) {
    }

    private enum ValueKind {
      STRING,
      NUMBER,
      IDENT,
      EXPR
    }

    private record Value(ValueKind kind, String text, double number, ExprNode expr, int line, int column) {
    }

    private interface ScriptValue {
      Object eval(CastContext ctx);
    }

    private final class LiteralValue implements ScriptValue {
      private final Object value;

      private LiteralValue(Object value) {
        this.value = value;
      }

      @Override
      public Object eval(CastContext ctx) {
        return value;
      }
    }

    private final class ExprValue implements ScriptValue {
      private final ExprNode expr;

      private ExprValue(ExprNode expr) {
        this.expr = expr;
      }

      @Override
      public Object eval(CastContext ctx) {
        return evalExpr(ctx, expr);
      }
    }

    private final class VarValue implements ScriptValue {
      private final String name;

      private VarValue(String name) {
        this.name = name;
      }

      @Override
      public Object eval(CastContext ctx) {
        return resolveValue(name, ctx);
      }
    }

    private final String input;
    private final String path;
    private int pos;
    private int line = 1;
    private int column = 1;
    private Token lookahead;
    private final java.util.Map<String, MacroDef> macros = new java.util.HashMap<>();
    private static final int MAX_REPEAT_TIMES = 10000;
    private static final long MAX_ON_TICK_TICKS = 20L * 60L * 5L;
    private static final int MAX_OPS_PER_TICK = 2000;
    private static final int MAX_MACRO_DEPTH = 32;
    private static final int MAX_PARTICLES_PER_CAST = 20000;
    private static final java.util.List<String> ALLOWED_FUNCTIONS = java.util.List.of(
        "min",
        "max",
        "clamp",
        "lerp",
        "rand",
        "abs",
        "floor",
        "ceil",
        "round");
    private static final java.util.Set<String> ALLOWED_FUNCTION_SET = java.util.Set.copyOf(ALLOWED_FUNCTIONS);

    private record MacroDef(List<String> params, Map<String, ScriptValue> defaults, dev.patric.dungeonsreborn.effects.actions.Action body, Token at) {
    }

    DslParser(String input, String path) {
      this.input = Objects.requireNonNull(input, "input");
      this.path = Objects.requireNonNull(path, "path");
      this.lookahead = nextToken();
    }

    ScriptHandlers parse() {
      dev.patric.dungeonsreborn.effects.actions.Action onCast = null;
      dev.patric.dungeonsreborn.effects.actions.Action onCancel = null;
      dev.patric.dungeonsreborn.effects.actions.Action onEnd = null;
      dev.patric.dungeonsreborn.effects.actions.Action onHit = null;
      dev.patric.dungeonsreborn.effects.actions.Action onFinish = null;
      dev.patric.dungeonsreborn.effects.actions.Action onCostFail = null;
      dev.patric.dungeonsreborn.effects.actions.Action onCooldownFail = null;
      while (lookahead.type != TokenType.EOF) {
        if (lookahead.type == TokenType.IDENT && "macro".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          parseMacro();
          continue;
        }
        String handler = requireIdent("handler").toLowerCase(Locale.ROOT);
        dev.patric.dungeonsreborn.effects.actions.Action action = parseBlock();
        switch (handler) {
          case "on_cast", "oncast" -> {
            if (onCast != null) {
              throw error("duplicate on_cast block");
            }
            onCast = action;
          }
          case "on_cancel", "oncancel" -> {
            if (onCancel != null) {
              throw error("duplicate on_cancel block");
            }
            onCancel = action;
          }
          case "on_end", "onend", "finally" -> {
            if (onEnd != null) {
              throw error("duplicate on_end block");
            }
            onEnd = action;
          }
          case "on_hit", "onhit" -> {
            if (onHit != null) {
              throw error("duplicate on_hit block");
            }
            onHit = action;
          }
          case "on_finish", "onfinish", "on_complete", "oncomplete", "on_done", "ondone" -> {
            if (onFinish != null) {
              throw error("duplicate on_finish block");
            }
            onFinish = action;
          }
          case "on_cost_fail", "oncostfail" -> {
            if (onCostFail != null) {
              throw error("duplicate on_cost_fail block");
            }
            onCostFail = action;
          }
          case "on_cooldown_fail", "oncooldownfail" -> {
            if (onCooldownFail != null) {
              throw error("duplicate on_cooldown_fail block");
            }
            onCooldownFail = action;
          }
          default -> throw error("unsupported handler: " + handler);
        }
      }
      if (onCast == null) {
        throw new IllegalArgumentException(path + ": missing on_cast block");
      }
      return new ScriptHandlers(onCast, onCancel, onEnd, onHit, onFinish, onCostFail, onCooldownFail);
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseBlock() {
      consume(TokenType.LBRACE);
      List<dev.patric.dungeonsreborn.effects.actions.Action> actions = new ArrayList<>();
      while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
        Token stmtToken = lookahead;
        actions.add(guard(parseStatement(), stmtToken));
      }
      consume(TokenType.RBRACE);
      if (actions.isEmpty()) {
        return Actions.noop();
      }
      if (actions.size() == 1) {
        return actions.get(0);
      }
      return Actions.sequence(actions.toArray(dev.patric.dungeonsreborn.effects.actions.Action[]::new));
    }

    private dev.patric.dungeonsreborn.effects.actions.Action guard(dev.patric.dungeonsreborn.effects.actions.Action action, Token at) {
      return ctx -> {
        if (!consumeOps(ctx, 1)) {
          return;
        }
        if (scriptDebug) {
          debugScript("stmt " + pathAt(at) + " ability=" + ctx.abilityId() + " cast=" + ctx.castId());
        }
        long start = scriptTrace ? System.nanoTime() : 0L;
        try {
          action.execute(ctx);
        } catch (RuntimeException ex) {
          recordScriptError(scriptId(ctx), ex);
          String message = ex.getMessage();
          String prefix = pathAt(at) + ": ";
          if (message != null && message.startsWith(pathAt(at))) {
            prefix = "";
          }
          String base = message == null || message.isBlank() ? "DSL runtime error" : message;
          throw new IllegalArgumentException(prefix + base + renderMacroStack(ctx), ex);
        } finally {
          if (scriptTrace) {
            long elapsed = System.nanoTime() - start;
            debugScript("stmt " + pathAt(at) + " took " + (elapsed / 1_000_000.0) + "ms");
          }
        }
      };
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseStatement() {
      Token stmtToken = lookahead;
      String name = requireIdent("statement");
      if ("if".equalsIgnoreCase(name) || "when".equalsIgnoreCase(name)) {
        var condition = parseCondition();
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          if (condition.test(ctx)) {
            thenAction.execute(ctx);
          } else {
            finalElse.execute(ctx);
          }
        };
      }
      if ("set".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        return ctx -> {
          Object v = value.eval(ctx);
          setVar(ctx, target.scope(), target.key(), v);
        };
      }
      if ("inc_var".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numAttr(attrs, "amount", 1.0, stmtToken);
        NumValue def = numAttr(attrs, "default", 0.0, stmtToken);
        return ctx -> {
          Object cur = vars(ctx, target.scope()).get(target.key());
          double next = numericVar(cur, evalDouble(def, ctx)) + evalDouble(amount, ctx);
          setVar(ctx, target.scope(), target.key(), next);
        };
      }
      if ("with_var".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return ctx -> {
          Map<String, Object> vars = vars(ctx, target.scope());
          boolean had = vars.containsKey(target.key());
          Object prev = vars.get(target.key());
          Object v = value.eval(ctx);
          setVar(ctx, target.scope(), target.key(), v);
          try {
            inner.executeWithHandle(ctx);
          } finally {
            if (!had) {
              vars.remove(target.key());
            } else {
              vars.put(target.key(), prev);
            }
          }
        };
      }
      if ("debug_var".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        Map<String, Value> attrs = parseAttributes();
        String label = stringAttr(attrs, "label", target.key(), stmtToken);
        return ctx -> {
          if (!ctx.engine().isDebugEnabled()) {
            return;
          }
          Object v = vars(ctx, target.scope()).get(target.key());
          ctx.engine().debug("var(" + target.scope().name().toLowerCase(Locale.ROOT) + "): " + label + "=" + v);
        };
      }
      if ("call".equalsIgnoreCase(name)) {
        return parseMacroCall(stmtToken);
      }
      if (lookahead.type == TokenType.EQUALS) {
        VarTarget target = new VarTarget(VarScope.CAST, name);
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        return ctx -> {
          Object v = value.eval(ctx);
          setVar(ctx, target.scope(), target.key(), v);
        };
      }
      if ("message".equalsIgnoreCase(name)) {
        String raw = requireStringToken("message");
        return ctx -> {
          if (ctx.caster() instanceof Player player) {
            player.sendMessage(renderText(raw, ctx));
          }
        };
      }
      if ("action_bar".equalsIgnoreCase(name) || "actionbar".equalsIgnoreCase(name)) {
        String raw = requireStringToken("action_bar");
        return ctx -> {
          if (ctx.caster() instanceof Player player) {
            player.sendActionBar(renderText(raw, ctx));
          }
        };
      }
      if ("sound".equalsIgnoreCase(name)) {
        Value soundValue = requireValue("sound");
        Map<String, Value> attrs = parseAttributes();
        Sound sound = parseSound(soundValue, stmtToken, "sound");
        NumValue volume = numAttr(attrs, "volume", 1.0, stmtToken);
        NumValue pitch = numAttr(attrs, "pitch", 1.0, stmtToken);
        return ctx -> Actions.sound(sound, (float) evalDouble(volume, ctx), (float) evalDouble(pitch, ctx)).execute(ctx);
      }
      if ("animate".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationTicks, ctx));
            long period = Math.max(0L, evalLong(periodTicks, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            return Actions.animate(duration, period, easingFromId(easingId), (tickCtx, t) -> {
              CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
              withTempVar(exec, VarScope.CAST, "t", t, () -> inner.executeWithHandle(exec));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("animate_realtime".equalsIgnoreCase(name) || "animate_real_time".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationMillis = numAttr(attrs, "durationMillis", 1000.0, stmtToken);
        NumValue periodMillis = numAttr(attrs, "periodMillis", 50.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationMillis, ctx));
            long period = Math.max(0L, evalLong(periodMillis, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            return Actions.animateRealTime(Duration.ofMillis(duration), Duration.ofMillis(period), easingFromId(easingId), (tickCtx, t) -> {
              CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
              withTempVar(exec, VarScope.CAST, "t", t, () -> inner.executeWithHandle(exec));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("delay".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue ticksValue = numAttr(attrs, "ticks", 0.0, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long ticks = Math.max(0L, evalLong(ticksValue, ctx));
            incPending(ctx);
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean done = new AtomicBoolean(false);
            Runnable finish = () -> {
              if (finished.compareAndSet(false, true)) {
                decPending(ctx);
                done.set(true);
              }
            };
            if (ticks <= 0L) {
              try {
                inner.executeWithHandle(ctx);
              } finally {
                finish.run();
              }
              return ActionHandle.completed();
            }
            EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
            handle[0] = ctx.engine().runLater(ticks, () -> {
              try {
                if (handle[0] == null || handle[0].isCancelled()) {
                  finish.run();
                  return;
                }
                inner.executeWithHandle(ctx);
              } finally {
                finish.run();
              }
            });
            ctx.state().track(handle[0]);
            return new ActionHandle() {
              @Override
              public boolean cancel() {
                boolean cancelled = handle[0] != null && handle[0].cancel();
                finish.run();
                return cancelled;
              }

              @Override
              public boolean isDone() {
                return done.get() || handle[0] == null || handle[0].isCancelled();
              }
            };
          }
        };
      }
      if ("repeat".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue timesValue = numAttr(attrs, "times", 1.0, stmtToken);
        NumValue everyValue = numAttr(attrs, "every", 1.0, stmtToken);
        NumValue delayValue = numAttr(attrs, "delay", 0.0, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            int times = Math.max(1, evalInt(timesValue, ctx));
            if (times > MAX_REPEAT_TIMES) {
              if (ctx.engine().isDebugEnabled()) {
                ctx.engine().debug("dsl repeat capped: " + times + " -> " + MAX_REPEAT_TIMES);
              }
              times = MAX_REPEAT_TIMES;
            }
            long every = Math.max(1L, evalLong(everyValue, ctx));
            long delay = Math.max(0L, evalLong(delayValue, ctx));
            incPending(ctx);
            final int[] remaining = new int[] { times };
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean done = new AtomicBoolean(false);
            Runnable finish = () -> {
              if (finished.compareAndSet(false, true)) {
                decPending(ctx);
                done.set(true);
              }
            };
            final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
            handle[0] = ctx.engine().runRepeating(delay, every, () -> {
              if (handle[0] == null || handle[0].isCancelled()) {
                finish.run();
                return;
              }
              if (remaining[0]-- <= 0) {
                handle[0].cancel();
                finish.run();
                return;
              }
              inner.executeWithHandle(ctx);
            });
            ctx.state().track(handle[0]);
            return new ActionHandle() {
              @Override
              public boolean cancel() {
                boolean cancelled = handle[0] != null && handle[0].cancel();
                finish.run();
                return cancelled;
              }

              @Override
              public boolean isDone() {
                return done.get() || handle[0] == null || handle[0].isCancelled();
              }
            };
          }
        };
      }
      if ("on_tick".equalsIgnoreCase(name) || "onTick".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue ticksValue = numAttr(attrs, "ticks", 20.0, stmtToken);
        NumValue everyValue = numAttr(attrs, "every", 1.0, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long ticks = Math.max(1L, evalLong(ticksValue, ctx));
            if (ticks > MAX_ON_TICK_TICKS) {
              if (ctx.engine().isDebugEnabled()) {
                ctx.engine().debug("dsl on_tick capped: " + ticks + " -> " + MAX_ON_TICK_TICKS);
              }
              ticks = MAX_ON_TICK_TICKS;
            }
            final long totalTicks = ticks;
            long every = Math.max(1L, evalLong(everyValue, ctx));
            incPending(ctx);
            final long start = ctx.engine().tickNow();
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean done = new AtomicBoolean(false);
            Runnable finish = () -> {
              if (finished.compareAndSet(false, true)) {
                decPending(ctx);
                done.set(true);
              }
            };
            final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
            handle[0] = ctx.engine().runRepeating(0L, every, () -> {
              if (handle[0] == null || handle[0].isCancelled()) {
                finish.run();
                return;
              }
              long elapsed = ctx.engine().tickNow() - start;
              if (elapsed >= totalTicks) {
                handle[0].cancel();
                finish.run();
                return;
              }
              double t = elapsed / (double) totalTicks;
              double eased = easingFromId(easingId).applyAsDouble(t);
              withTempVar(ctx, VarScope.CAST, "t", eased, () -> inner.executeWithHandle(ctx));
            });
            ctx.state().track(handle[0]);
            return new ActionHandle() {
              @Override
              public boolean cancel() {
                boolean cancelled = handle[0] != null && handle[0].cancel();
                finish.run();
                return cancelled;
              }

              @Override
              public boolean isDone() {
                return done.get() || handle[0] == null || handle[0].isCancelled();
              }
            };
          }
        };
      }

      if ("chance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        Value pValue = attrs.containsKey("probability") ? attrs.get("probability") : attrs.get("p");
        NumValue probability = pValue == null ? new ConstNum(0.5) : numFromValue(pValue, "probability", stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          double p = evalDouble(probability, ctx);
          if (p <= 0.0) {
            finalElse.execute(ctx);
            return;
          }
          if (p >= 1.0) {
            thenAction.execute(ctx);
            return;
          }
          if (ctx.rng().nextDouble() < p) {
            thenAction.execute(ctx);
          } else {
            finalElse.execute(ctx);
          }
        };
      }

      if ("invoke".equalsIgnoreCase(name) || "invoke_ability".equalsIgnoreCase(name) || "invokeAbility".equalsIgnoreCase(name)) {
        String rawAbility = null;
        if (lookahead.type == TokenType.STRING) {
          rawAbility = requireStringToken("ability");
        }
        Map<String, Value> attrs = parseAttributes();
        if (rawAbility == null) {
          rawAbility = stringValue(requireAttr(attrs, "ability", stmtToken), "ability", stmtToken);
        }
        String abilityId;
        try {
          abilityId = dev.patric.dungeonsreborn.effects.Ids.normalize(rawAbility);
        } catch (Exception ex) {
          throw error(stmtToken, "invalid ability id: " + rawAbility + " (" + ex.getMessage() + ")");
        }
        String mode = stringAttr(attrs, "mode", "subgraph", stmtToken).trim().toLowerCase(Locale.ROOT);
        NumValue maxDepthValue = numAttr(attrs, "maxDepth", 8.0, stmtToken);
        return ctx -> {
          int maxDepth = Math.max(1, evalInt(maxDepthValue, ctx));
          if ("cast".equals(mode)) {
            if (ctx.engine().hasAbility(abilityId)) {
              ctx.engine().cast(abilityId, ctx.caster());
            } else if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke cast: ability not registered: " + abilityId);
            }
            return;
          }

          dev.patric.dungeonsreborn.effects.actions.Action target = yamlActionGraphs.get(abilityId);
          if (target == null) {
            if (ctx.engine().hasAbility(abilityId)) {
              ctx.engine().cast(abilityId, ctx.caster());
              return;
            }
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke: unknown ability: " + abilityId);
            }
            return;
          }

          Object existing = ctx.state().get(YAML_INVOKE_STACK);
          @SuppressWarnings("unchecked")
          java.util.ArrayDeque<String> stack = existing instanceof java.util.ArrayDeque<?> d ? (java.util.ArrayDeque<String>) d : null;
          if (stack == null) {
            stack = new java.util.ArrayDeque<>();
            stack.addLast(ctx.abilityId());
            ctx.state().put(YAML_INVOKE_STACK, stack);
          } else if (stack.isEmpty()) {
            stack.addLast(ctx.abilityId());
          }

          if (stack.size() >= maxDepth) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke: maxDepth reached (" + maxDepth + "): " + String.join(" -> ", stack));
            }
            return;
          }
          if (stack.contains(abilityId)) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke: cycle detected: " + String.join(" -> ", stack) + " -> " + abilityId);
            }
            return;
          }

          stack.addLast(abilityId);
          try {
            target.execute(ctx);
          } finally {
            stack.removeLast();
          }
        };
      }

      if ("debug_log".equalsIgnoreCase(name) || "debug".equalsIgnoreCase(name)) {
        String raw = requireStringToken("debug_log");
        return ctx -> {
          if (!ctx.engine().isDebugEnabled()) {
            return;
          }
          ctx.engine().debug(interpolate(raw, ctx));
        };
      }

      if ("title".equalsIgnoreCase(name)) {
        String rawTitle = requireStringToken("title");
        Map<String, Value> attrs = parseAttributes();
        String rawSubtitle = attrs.containsKey("subtitle") ? stringValue(attrs.get("subtitle"), "subtitle", stmtToken) : null;
        NumValue fadeIn = attrs.containsKey("fadeInTicks")
            ? numFromValue(attrs.get("fadeInTicks"), "fadeInTicks", stmtToken)
            : numAttr(attrs, "fadeIn", 10.0, stmtToken);
        NumValue stay = attrs.containsKey("stayTicks")
            ? numFromValue(attrs.get("stayTicks"), "stayTicks", stmtToken)
            : numAttr(attrs, "stay", 40.0, stmtToken);
        NumValue fadeOut = attrs.containsKey("fadeOutTicks")
            ? numFromValue(attrs.get("fadeOutTicks"), "fadeOutTicks", stmtToken)
            : numAttr(attrs, "fadeOut", 10.0, stmtToken);
        return ctx -> {
          if (ctx.caster() instanceof Player player) {
            Component title = renderText(rawTitle, ctx);
            Component subtitle = rawSubtitle == null ? Component.empty() : renderText(rawSubtitle, ctx);
            long fi = Math.max(0L, evalLong(fadeIn, ctx));
            long st = Math.max(0L, evalLong(stay, ctx));
            long fo = Math.max(0L, evalLong(fadeOut, ctx));
            Title.Times times = Title.Times.times(Duration.ofMillis(fi * 50L), Duration.ofMillis(st * 50L), Duration.ofMillis(fo * 50L));
            player.showTitle(Title.title(title, subtitle, times));
          }
        };
      }

      if ("damage".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damage(dmg, policy).execute(ctx, target);
        };
      }
      if ("damage_typed".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        DamageType type = parseDamageType(requireAttr(attrs, "type", stmtToken), stmtToken, "type");
        boolean ignoreResistance = boolAttr(attrs, "ignoreResistance", false, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageTyped(dmg, type, ignoreResistance, policy).execute(ctx, target);
        };
      }
      if ("set_resistance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        DamageType type = parseDamageType(requireAttr(attrs, "type", stmtToken), stmtToken, "type");
        NumValue multiplier = numFromValue(requireAttr(attrs, "multiplier", stmtToken), "multiplier", stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 0.0, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double mult = evalDouble(multiplier, ctx);
          if (!Double.isFinite(mult) || mult < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), type, mult);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().restoreResistance(target.getUniqueId(), type, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      if ("add_resistance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        DamageType type = parseDamageType(requireAttr(attrs, "type", stmtToken), stmtToken, "type");
        NumValue delta = numFromValue(requireAttr(attrs, "delta", stmtToken), "delta", stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 0.0, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double add = evalDouble(delta, ctx);
          if (!Double.isFinite(add)) {
            return;
          }
          double current = ctx.engine().resistanceMultiplier(target.getUniqueId(), type);
          double next = current + add;
          if (!Double.isFinite(next) || next < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), type, next);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().restoreResistance(target.getUniqueId(), type, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      if ("clear_resistance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        Value typeValue = attrs.get("type");
        DamageType type = typeValue == null ? null : parseDamageType(typeValue, stmtToken, "type");
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          if (type == null) {
            ctx.engine().clearResistances(target.getUniqueId());
          } else {
            ctx.engine().clearResistance(target.getUniqueId(), type);
          }
        };
      }
      if ("set_reflect".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue ratio = numAttr(attrs, "ratio", 0.25, stmtToken);
        NumValue flat = numAttr(attrs, "flat", 0.0, stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 0.0, stmtToken);
        boolean ignoreResistance = boolAttr(attrs, "ignoreResistance", false, stmtToken);
        DamageType type = attrs.containsKey("type") ? parseDamageType(attrs.get("type"), stmtToken, "type") : null;
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double r = evalDouble(ratio, ctx);
          double f = evalDouble(flat, ctx);
          if (!(r > 0.0) && !(f > 0.0)) {
            return;
          }
          long token = ctx.engine().setReflect(target.getUniqueId(), new EffectsEngine.ReflectSpec(r, f, type, ignoreResistance, policy));
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().clearReflect(target.getUniqueId(), token));
            ctx.state().track(handle);
          }
        };
      }
      if ("clear_reflect".equalsIgnoreCase(name)) {
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          ctx.engine().clearReflect(target.getUniqueId());
        };
      }
      if ("damage_percent".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue percent = numAttr(attrs, "percent", 0.15, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double pct = evalDouble(percent, ctx);
          if (pct <= 0.0) {
            return;
          }
          EntityActions.damagePercent(pct, policy).execute(ctx, target);
        };
      }
      if ("damage_true".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageTrue(dmg, policy).execute(ctx, target);
        };
      }
      if ("damage_falloff".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue maxDistance = numAttr(attrs, "maxDistance", 12.0, stmtToken);
        NumValue minMultiplier = numAttr(attrs, "minMultiplier", 0.2, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          double maxDist = evalDouble(maxDistance, ctx);
          double minMult = evalDouble(minMultiplier, ctx);
          if (dmg <= 0.0 || maxDist <= 0.0) {
            return;
          }
          EntityActions.damageWithFalloff(dmg, maxDist, minMult, policy).execute(ctx, target);
        };
      }
      if ("damage_crit".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue critChance = numAttr(attrs, "critChance", 0.2, stmtToken);
        NumValue critMultiplier = numAttr(attrs, "critMultiplier", 1.5, stmtToken);
        NumValue headshotMultiplier = numAttr(attrs, "headshotMultiplier", 1.0, stmtToken);
        NumValue headshotThreshold = numAttr(attrs, "headshotThreshold", 0.25, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageCrit(
              dmg,
              evalDouble(critChance, ctx),
              evalDouble(critMultiplier, ctx),
              evalDouble(headshotMultiplier, ctx),
              evalDouble(headshotThreshold, ctx),
              policy).execute(ctx, target);
        };
      }
      if ("damage_lifesteal".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue ratio = numAttr(attrs, "ratio", 0.25, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageLifesteal(dmg, evalDouble(ratio, ctx), policy).execute(ctx, target);
        };
      }
      if ("damage_dot".equalsIgnoreCase(name) || "damage_over_time".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 10.0, stmtToken);
        NumValue times = numAttr(attrs, "times", 5.0, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          long period = evalLong(periodTicks, ctx);
          int count = evalInt(times, ctx);
          if (dmg <= 0.0 || period <= 0 || count <= 0) {
            return;
          }
          EntityActions.damageOverTime(dmg, period, count, policy).execute(ctx, target);
        };
      }
      if ("damage_chain".equalsIgnoreCase(name) || "chain_damage".equalsIgnoreCase(name) || "chain_lightning".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue radius = numAttr(attrs, "radius", 6.0, stmtToken);
        NumValue maxJumps = numAttr(attrs, "maxJumps", 4.0, stmtToken);
        NumValue delayTicks = numAttr(attrs, "delayTicks", 2.0, stmtToken);
        NumValue falloff = numAttr(attrs, "falloff", 0.8, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (lookahead.type == TokenType.LBRACE) {
          onHitAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          double r = evalDouble(radius, ctx);
          int jumps = evalInt(maxJumps, ctx);
          long delay = evalLong(delayTicks, ctx);
          double f = evalDouble(falloff, ctx);
          if (dmg <= 0.0 || r <= 0.0 || jumps <= 0 || delay < 0) {
            return;
          }
          EntityActions.chainDamage(dmg, r, jumps, delay, f, policy, (cast, hit) -> {
            Object prev = cast.state().get(YAML_LAST_ENTITY);
            cast.state().put(YAML_LAST_ENTITY, hit);
            try {
              finalOnHit.execute(cast);
            } finally {
              cast.state().put(YAML_LAST_ENTITY, prev);
            }
          }).execute(ctx, target);
        };
      }

      if ("heal".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double heal = evalDouble(amount, ctx);
          if (heal <= 0.0) {
            return;
          }
          EntityActions.heal(heal).execute(ctx, target);
        };
      }

      if ("potion".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        PotionEffectType effect = parsePotionEffect(requireAttr(attrs, "effect", stmtToken), stmtToken, "effect");
        NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
        NumValue amplifier = numAttr(attrs, "amplifier", 0.0, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          long ticks = Math.max(1L, evalLong(durationTicks, ctx));
          int amp = Math.max(0, evalInt(amplifier, ctx));
          EntityActions.potion(effect, Duration.ofMillis(ticks * 50L), amp).execute(ctx, target);
        };
      }

      if ("knockback".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue horizontal = numAttr(attrs, "horizontal", 1.0, stmtToken);
        NumValue vertical = numAttr(attrs, "vertical", 0.35, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          EntityActions.knockbackFromOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
        };
      }

      if ("pull".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue horizontal = numAttr(attrs, "horizontal", 0.75, stmtToken);
        NumValue vertical = numAttr(attrs, "vertical", 0.08, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          EntityActions.pullToOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
        };
      }

      if ("raycast_hit_entity".equalsIgnoreCase(name) || "raycast_hit".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue maxDistance = numAttr(attrs, "maxDistance", 20.0, stmtToken);
        NumValue raySize = numAttr(attrs, "raySize", 0.35, stmtToken);
        boolean stopOnBlock = boolAttr(attrs, "stopOnBlock", true, stmtToken);
        boolean ignoreCaster = boolAttr(attrs, "ignoreCaster", true, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double size = evalDouble(raySize, ctx);
          if (max <= 0.0 || size < 0.0) {
            finalElse.execute(ctx);
            return;
          }
          var hits = Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, e -> true).select(ctx);
          if (hits.isEmpty()) {
            finalElse.execute(ctx);
            return;
          }
          LivingEntity target = hits.get(0);
          ctx.state().put(YAML_LAST_ENTITY, target);
          thenAction.execute(ctx);
          Object hook = ctx.state().get(DSL_ON_HIT);
          if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
            action.execute(ctx);
          }
        };
      }

      if ("for_each_target".equalsIgnoreCase(name) || "targets".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        Targeter<LivingEntity> targeter = parseTargeter(attrs, stmtToken);
        String mode = stringAttr(attrs, "mode", "each", stmtToken).trim().toLowerCase(Locale.ROOT);
        boolean firstOnly = switch (mode) {
          case "each" -> false;
          case "first" -> true;
          default -> throw error(stmtToken, "invalid mode: " + mode + " (use each|first)");
        };
        NumValue maxTargets = numAttr(attrs, "maxTargets", 0.0, stmtToken);
        String sortRaw = stringAttr(attrs, "sort", "none", stmtToken).trim().toLowerCase(Locale.ROOT);
        AtMode originAt = parseAt(stringAttr(attrs, "originAt", "origin", stmtToken), pathAt(stmtToken) + ".originAt");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          CastContext selectCtx = ctx;
          if (originAt != AtMode.ORIGIN) {
            Location origin = resolveAt(ctx, originAt);
            if (origin.getWorld() != null) {
              selectCtx = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  ctx.direction().clone(),
                  ctx.itemInHand());
            }
          }
          List<LivingEntity> targets = new java.util.ArrayList<>(targeter.select(selectCtx));
          if (targets.isEmpty()) {
            finalElse.execute(ctx);
            return;
          }
          if (!"none".equals(sortRaw) && targets.size() > 1) {
            Location origin = selectCtx.origin();
            switch (sortRaw) {
              case "nearest" -> targets.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)));
              case "farthest" -> targets.sort(java.util.Comparator.comparingDouble((LivingEntity e) -> e.getLocation().distanceSquared(origin)).reversed());
              case "lowest_health" -> targets.sort(java.util.Comparator.comparingDouble(LivingEntity::getHealth));
              case "highest_health" -> targets.sort(java.util.Comparator.comparingDouble(LivingEntity::getHealth).reversed());
              case "random" -> java.util.Collections.shuffle(targets, ctx.rng());
              default -> throw error(stmtToken, "invalid sort: " + sortRaw + " (use none|nearest|farthest|lowest_health|highest_health|random)");
            }
          }
          int limit = targets.size();
          if (firstOnly) {
            limit = 1;
          }
          int max = evalInt(maxTargets, ctx);
          if (max > 0) {
            limit = Math.min(limit, max);
          }
          for (int i = 0; i < limit; i++) {
            ctx.state().put(YAML_LAST_ENTITY, targets.get(i));
            thenAction.execute(ctx);
          }
        };
      }

      if ("projectile".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue speedPerTick = numAttr(attrs, "speedPerTick", 1.3, stmtToken);
        NumValue maxDistance = numAttr(attrs, "maxDistance", 24.0, stmtToken);
        NumValue hitRadius = numAttr(attrs, "hitRadius", 0.25, stmtToken);
        boolean ignoreCaster = boolAttr(attrs, "ignoreCaster", true, stmtToken);
        String bc = stringAttr(attrs, "blockCollision", "stop", stmtToken).toUpperCase(Locale.ROOT);
        boolean bounce = "BOUNCE".equals(bc);
        boolean passThrough = "PASS_THROUGH".equals(bc) || "PASS-THROUGH".equals(bc) || "PASS".equals(bc);
        NumValue bounceMaxTmp = null;
        NumValue bounceRestitutionTmp = null;
        if (bounce) {
          Value maxAttr = attrs.getOrDefault("bounceMax", attrs.get("bouncesMax"));
          if (maxAttr != null) {
            bounceMaxTmp = numFromValue(maxAttr, "bounceMax", stmtToken);
          }
          Value restAttr = attrs.getOrDefault("bounceRestitution", attrs.get("bouncesRestitution"));
          if (restAttr != null) {
            bounceRestitutionTmp = numFromValue(restAttr, "bounceRestitution", stmtToken);
          }
        }
        final NumValue bounceMax = bounceMaxTmp;
        final NumValue bounceRestitution = bounceRestitutionTmp;
        Value trailParticleAttr = attrs.get("trailParticle");
        boolean hasTrail = trailParticleAttr != null;
        Particle trailParticle = hasTrail ? parseParticle(trailParticleAttr, stmtToken, "trailParticle") : null;
        NumValue trailCount = hasTrail ? numAttr(attrs, "trailCount", 1.0, stmtToken) : null;
        NumValue trailOffset = hasTrail ? numAttr(attrs, "trailOffset", 0.0, stmtToken) : null;
        NumValue trailExtra = hasTrail ? numAttr(attrs, "trailExtra", 0.0, stmtToken) : null;

        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (lookahead.type == TokenType.LBRACE) {
          consume(TokenType.LBRACE);
          while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
            String inner = requireIdent("projectile hook");
            if (!"on_hit".equalsIgnoreCase(inner) && !"onhit".equalsIgnoreCase(inner)) {
              throw error(stmtToken, "projectile only supports on_hit block");
            }
            onHitAction = parseBlock();
          }
          consume(TokenType.RBRACE);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        return ctx -> {
          double speed = evalDouble(speedPerTick, ctx);
          double max = evalDouble(maxDistance, ctx);
          double radius = evalDouble(hitRadius, ctx);
          if (speed <= 0.0 || max <= 0.0 || radius < 0.0) {
            return;
          }
          if (hasTrail) {
            int count = trailCount == null ? 1 : Math.max(0, evalInt(trailCount, ctx));
            if (count > 0) {
              long steps = (long) Math.ceil(max / Math.max(1e-6, speed)) + 1L;
              long total = steps * count;
              if (!consumeParticles(ctx, total)) {
                return;
              }
            }
          }
          ProjectileSpec.Builder b = ProjectileSpec.builder()
              .speedPerTick(speed)
              .maxDistance(max)
              .hitRadius(radius)
              .ignoreCaster(ignoreCaster);
          if (passThrough) {
            b.stopOnBlock(false);
          } else if (bounce) {
            int maxBounce = bounceMax == null ? 0 : Math.max(0, evalInt(bounceMax, ctx));
            double restitution = bounceRestitution == null ? 0.9 : Math.max(0.0, evalDouble(bounceRestitution, ctx));
            b.bounces(maxBounce, restitution);
          } else {
            b.stopOnBlock(true);
          }
          if (hasTrail) {
            int count = trailCount == null ? 1 : Math.max(0, evalInt(trailCount, ctx));
            double offset = trailOffset == null ? 0.0 : Math.max(0.0, evalDouble(trailOffset, ctx));
            double extra = trailExtra == null ? 0.0 : evalDouble(trailExtra, ctx);
            b.trail(trailParticle, count, offset, extra);
          }
          b.onHit(hit -> {
            CastContext cast = hit.cast();
            cast.state().put(YAML_LAST_ENTITY, hit.hitEntity());
            finalOnHit.execute(cast);
            Object hook = cast.state().get(DSL_ON_HIT);
            if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
              action.execute(cast);
            }
          });
          Actions.projectile(b.build()).execute(ctx);
        };
      }

      if ("summon_minion".equalsIgnoreCase(name) || "minion_summon".equalsIgnoreCase(name) || "minions".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        String mobId = stringValue(requireAttr(attrs, "mob", stmtToken), "mob", stmtToken);
        String minionId = attrs.containsKey("id") ? stringValue(attrs.get("id"), "id", stmtToken) : mobId;
        NumValue count = numAttr(attrs, "count", 1.0, stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 20.0 * 30.0, stmtToken);
        NumValue radius = numAttr(attrs, "radius", 1.5, stmtToken);
        boolean despawnOnLogout = boolAttr(attrs, "despawnOnLogout", true, stmtToken);
        String modeRaw = stringAttr(attrs, "mode", null, stmtToken);
        MinionMode mode = parseMinionMode(modeRaw, pathAt(stmtToken) + ".mode");
        NumValue healthPerLevel = numAttrAlias(attrs, "scale_healthPerLevel", "healthPerLevel", 0.0, stmtToken);
        NumValue damagePerLevel = numAttrAlias(attrs, "scale_damagePerLevel", "damagePerLevel", 0.0, stmtToken);
        NumValue healthPerMaxHealth = numAttrAlias(attrs, "scale_healthPerMaxHealth", "healthPerMaxHealth", 0.0, stmtToken);
        NumValue damagePerMaxHealth = numAttrAlias(attrs, "scale_damagePerMaxHealth", "damagePerMaxHealth", 0.0, stmtToken);
        NumValue healthPerManaMax = numAttrAlias(attrs, "scale_healthPerManaMax", "healthPerManaMax", 0.0, stmtToken);
        NumValue damagePerManaMax = numAttrAlias(attrs, "scale_damagePerManaMax", "damagePerManaMax", 0.0, stmtToken);

        Map<DamageType, NumValue> resistanceDefs = new java.util.EnumMap<>(DamageType.class);
        java.util.Set<DamageType> immunities = new java.util.HashSet<>();
        for (Map.Entry<String, Value> entry : attrs.entrySet()) {
          String key = entry.getKey();
          if (key.startsWith("resistance_") || key.startsWith("resist_")) {
            String typeRaw = key.substring(key.indexOf('_') + 1);
            DamageType type = parseDamageTypeKey(typeRaw, pathAt(stmtToken) + "." + key);
            resistanceDefs.put(type, numFromValue(entry.getValue(), key, stmtToken));
          }
          if (key.startsWith("immune_") || key.startsWith("immunity_")) {
            String typeRaw = key.substring(key.indexOf('_') + 1);
            if (boolAttr(attrs, key, false, stmtToken)) {
              immunities.add(parseDamageTypeKey(typeRaw, pathAt(stmtToken) + "." + key));
            }
          }
        }

        String passivesRaw = stringAttr(attrs, "passives", null, stmtToken);
        if (passivesRaw == null) {
          passivesRaw = stringAttr(attrs, "passive", null, stmtToken);
        }
        List<String> passiveIds = splitIdList(passivesRaw);
        NumValue passivePeriod = numAttr(attrs, "passivePeriodTicks", 40.0, stmtToken);

        String specialsRaw = stringAttr(attrs, "specialAttacks", null, stmtToken);
        if (specialsRaw == null) {
          specialsRaw = stringAttr(attrs, "specials", null, stmtToken);
        }
        List<String> specialIds = splitIdList(specialsRaw);
        NumValue specialCooldown = numAttr(attrs, "specialCooldownTicks", 60.0, stmtToken);
        NumValue specialChance = numAttr(attrs, "specialChance", 1.0, stmtToken);
        boolean specialRequireTarget = boolAttr(attrs, "specialRequireTarget", true, stmtToken);

        MinionManager minions = resolveMinionManager(pathAt(stmtToken));
        return ctx -> {
          if (!minions.hasMob(Ids.normalize(mobId))) {
            throw error(stmtToken, "unknown mob id: " + mobId);
          }
          int c = Math.max(0, evalInt(count, ctx));
          long dur = (long) Math.max(1L, Math.round(evalDouble(durationTicks, ctx)));
          double r = Math.max(0.0, evalDouble(radius, ctx));
          if (c <= 0 || dur <= 0) {
            return;
          }
          MinionScaling scaling = new MinionScaling(
              evalDouble(healthPerLevel, ctx),
              evalDouble(damagePerLevel, ctx),
              evalDouble(healthPerMaxHealth, ctx),
              evalDouble(damagePerMaxHealth, ctx),
              evalDouble(healthPerManaMax, ctx),
              evalDouble(damagePerManaMax, ctx));

          Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
          for (Map.Entry<DamageType, NumValue> entry : resistanceDefs.entrySet()) {
            double value = evalDouble(entry.getValue(), ctx);
            if (Double.isFinite(value) && value >= 0.0) {
              resistances.put(entry.getKey(), value);
            }
          }
          java.util.Set<DamageType> immuneTypes = java.util.Set.copyOf(immunities);

          long passiveTicks = Math.max(1L, Math.round(evalDouble(passivePeriod, ctx)));
          List<MinionPassiveSpec> passives = new java.util.ArrayList<>();
          for (String abilityId : passiveIds) {
            passives.add(new MinionPassiveSpec(abilityId, passiveTicks));
          }

          long specialTicks = Math.max(1L, Math.round(evalDouble(specialCooldown, ctx)));
          double chance = Math.max(0.0, Math.min(1.0, evalDouble(specialChance, ctx)));
          List<MinionSpecialAttackSpec> specials = new java.util.ArrayList<>();
          for (String abilityId : specialIds) {
            specials.add(new MinionSpecialAttackSpec(abilityId, specialTicks, chance, specialRequireTarget));
          }

          java.util.UUID ownerId = ctx.caster().getUniqueId();
          MinionSpec spec = new MinionSpec(minionId, Ids.normalize(mobId), c, dur, ownerId, r, scaling, resistances, immuneTypes, despawnOnLogout, mode, passives, specials);
          java.util.List<LivingEntity> spawned = minions.summon(spec, ctx.caster().getLocation());
          java.util.List<java.util.UUID> ids = new java.util.ArrayList<>(spawned.size());
          for (LivingEntity living : spawned) {
            ids.add(living.getUniqueId());
          }
          ctx.state().put(Vars.MINION_ID, minionId);
          ctx.state().put(Vars.MINION_COUNT, spawned.size());
          ctx.state().put(Vars.MINION_IDS, java.util.List.copyOf(ids));
          ctx.state().put(Vars.MINION_DURATION, dur);
        };
      }

      if ("choice".equalsIgnoreCase(name)) {
        if (!(lookahead.type == TokenType.IDENT && "weighted".equalsIgnoreCase(lookahead.text))) {
          throw error(stmtToken, "choice requires 'weighted' block");
        }
        consume(TokenType.IDENT);
        return parseWeightedChoice(stmtToken);
      }

      if ("preset_beam_chargeup".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        return parseParticles("beam_chargeup", attrs, stmtToken);
      }

      if (name.toLowerCase(Locale.ROOT).startsWith("particles.")) {
        String shape = name.substring("particles.".length()).toLowerCase(Locale.ROOT);
        Map<String, Value> attrs = parseAttributes();
        return parseParticles(shape, attrs, stmtToken);
      }

      throw error(stmtToken, "unknown statement: " + name);
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseParticles(String shape, Map<String, Value> attrs, Token stmtToken) {
      Particle particle = parseParticle(requireAttr(attrs, "particle", stmtToken), stmtToken, "particle");
      NumValue count = numAttr(attrs, "count", 1.0, stmtToken);
      NumValue offset = numAttr(attrs, "offset", 0.0, stmtToken);
      NumValue extra = numAttr(attrs, "extra", 0.0, stmtToken);
      String atRaw = stringAttr(attrs, "at", "origin", stmtToken);
      AtMode at = parseAt(atRaw, pathAt(stmtToken) + ".at");

      switch (shape) {
        case "point" -> {
          return ctx -> {
            Location loc = resolveAt(ctx, at);
            if (loc.getWorld() == null) {
              return;
            }
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (emitCount == 0) {
              return;
            }
            if (!consumeParticles(ctx, emitCount)) {
              return;
            }
            double off = evalDouble(offset, ctx);
            double ex = evalDouble(extra, ctx);
            ctx.engine().particles().emit(loc.getWorld(), loc, particle, emitCount, off, off, off, ex);
          };
        }
        case "ring" -> {
          NumValue radius = numAttr(attrs, "radius", 1.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 24.0, stmtToken);
          return ctx -> {
            Location center = resolveAt(ctx, at);
            if (center.getWorld() == null) {
              return;
            }
            var pe = ctx.engine().particles();
            double r = evalDouble(radius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            double off = evalDouble(offset, ctx);
            double ex = evalDouble(extra, ctx);
            dev.patric.dungeonsreborn.effects.particles.ParticleShapes.ring(center, new Vector(0, 1, 0), r, pts,
                loc -> pe.emit(center.getWorld(), loc, particle, emitCount, off, off, off, ex));
          };
        }
        case "line" -> {
          NumValue length = numAttr(attrs, "length", 10.0, stmtToken);
          NumValue step = numAttr(attrs, "step", 0.35, stmtToken);
          return ctx -> {
            double len = evalDouble(length, ctx);
            double st = evalDouble(step, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (len <= 0.0 || st <= 0.0 || emitCount == 0) {
              return;
            }
            int pts = (int) Math.floor(len / st) + 1;
            long total = (long) Math.max(0, pts) * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesLine(particle, len, st, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "arc" -> {
          NumValue radius = numAttr(attrs, "radius", 1.2, stmtToken);
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 90.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 24.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || angle <= 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesArc(particle, r, angle, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "disk" -> {
          NumValue radius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 6.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 42.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int ppr = Math.max(0, evalInt(pointsPerRing, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || ringCount == 0 || ppr == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = 1;
            for (int i = 1; i <= ringCount; i++) {
              int ringPts = Math.max(6, (int) Math.round(ppr * (i / (double) ringCount)));
              totalPoints += ringPts;
            }
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesDisk(particle, r, ringCount, ppr, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "sphere_shell", "sphere-shell", "sphere" -> {
          NumValue radius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 120.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesSphereShell(particle, r, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "sphere_filled", "sphere-filled", "sphere_fill" -> {
          NumValue radius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 160.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesSphereFilled(particle, r, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "helix" -> {
          NumValue radius = numAttr(attrs, "radius", 1.2, stmtToken);
          NumValue length = numAttr(attrs, "length", 6.0, stmtToken);
          NumValue turns = numAttr(attrs, "turns", 3.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 80.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double len = evalDouble(length, ctx);
            int t = Math.max(0, evalInt(turns, ctx));
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || len < 0.0 || t == 0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesHelix(particle, r, len, t, pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "bezier" -> {
          NumValue pointsPerMeter = numAttr(attrs, "pointsPerMeter", 6.0, stmtToken);
          NumValue maxPoints = numAttr(attrs, "maxPoints", 180.0, stmtToken);
          PointSpec p0 = pointSpecFromAttrs(attrs, "p0", stmtToken, true);
          PointSpec p1 = pointSpecFromAttrs(attrs, "p1", stmtToken, true);
          PointSpec p2 = pointSpecFromAttrs(attrs, "p2", stmtToken, true);
          PointSpec p3 = pointSpecFromAttrs(attrs, "p3", stmtToken, true);
          return ctx -> {
            double ppm = evalDouble(pointsPerMeter, ctx);
            int maxPts = Math.max(0, evalInt(maxPoints, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (ppm <= 0.0 || maxPts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) maxPts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            CastContext exec = ctx;
            if (at != AtMode.ORIGIN) {
              Location origin = resolveAt(ctx, at);
              if (origin.getWorld() == null) {
                return;
              }
              exec = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  ctx.direction().clone(),
                  ctx.itemInHand());
            }
            Actions.particlesBezier(
                c -> pointAt(c, p0),
                c -> pointAt(c, p1),
                c -> pointAt(c, p2),
                c -> pointAt(c, p3),
                ppm,
                maxPts,
                particle,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec)).execute(exec);
          };
        }
        case "spline" -> {
          NumValue pointsPerMeter = numAttr(attrs, "pointsPerMeter", 10.0, stmtToken);
          NumValue maxPoints = numAttr(attrs, "maxPoints", 320.0, stmtToken);
          java.util.List<PointSpec> points = splinePointsFromAttrs(attrs, stmtToken);
          var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
          for (PointSpec spec : points) {
            fns.add(ctx -> pointAt(ctx, spec));
          }
          return ctx -> {
            double ppm = evalDouble(pointsPerMeter, ctx);
            int maxPts = Math.max(0, evalInt(maxPoints, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (ppm <= 0.0 || maxPts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) maxPts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            CastContext exec = ctx;
            if (at != AtMode.ORIGIN) {
              Location origin = resolveAt(ctx, at);
              if (origin.getWorld() == null) {
                return;
              }
              exec = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  ctx.direction().clone(),
                  ctx.itemInHand());
            }
            Actions.particlesSpline(fns, ppm, maxPts, particle, emitCount, evalDouble(offset, exec), evalDouble(extra, exec)).execute(exec);
          };
        }
        case "cone" -> {
          NumValue length = numAttr(attrs, "length", 8.0, stmtToken);
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 70.0, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 10.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 18.0, stmtToken);
          return ctx -> {
            double len = evalDouble(length, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int ppr = Math.max(0, evalInt(pointsPerRing, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (len <= 0.0 || angle <= 0.0 || ringCount == 0 || ppr == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = 1L + (long) ringCount * ppr;
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesCone(particle, len, angle, ringCount, ppr, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx))
                .execute(ctx);
          };
        }
        case "cylinder" -> {
          NumValue radius = numAttr(attrs, "radius", 2.4, stmtToken);
          NumValue height = numAttr(attrs, "height", 3.2, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 10.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 24.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double h = evalDouble(height, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int ppr = Math.max(0, evalInt(pointsPerRing, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || h < 0.0 || ringCount == 0 || ppr == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = (long) (ringCount + 1) * ppr;
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesCylinder(particle, r, h, ringCount, ppr, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx))
                .execute(ctx);
          };
        }
        case "box" -> {
          NumValue xRadius = numAttr(attrs, "xRadius", 2.2, stmtToken);
          NumValue yRadius = numAttr(attrs, "yRadius", 1.6, stmtToken);
          NumValue zRadius = numAttr(attrs, "zRadius", 2.2, stmtToken);
          NumValue step = numAttr(attrs, "step", 0.35, stmtToken);
          return ctx -> {
            double xr = evalDouble(xRadius, ctx);
            double yr = evalDouble(yRadius, ctx);
            double zr = evalDouble(zRadius, ctx);
            double st = evalDouble(step, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (xr < 0.0 || yr < 0.0 || zr < 0.0 || st <= 0.0 || emitCount == 0) {
              return;
            }
            long px = (long) Math.ceil((2.0 * xr) / st) + 1L;
            long py = (long) Math.ceil((2.0 * yr) / st) + 1L;
            long pz = (long) Math.ceil((2.0 * zr) / st) + 1L;
            long totalPoints = 4L * (px + py + pz);
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesBox(particle, xr, yr, zr, st, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "polygon" -> {
          NumValue radius = numAttr(attrs, "radius", 2.5, stmtToken);
          NumValue sides = numAttr(attrs, "sides", 6.0, stmtToken);
          NumValue pointsPerEdge = numAttr(attrs, "pointsPerEdge", 10.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int s = Math.max(0, evalInt(sides, ctx));
            int ppe = Math.max(0, evalInt(pointsPerEdge, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || s == 0 || ppe == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = (long) s * (ppe + 1L);
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.particlesPolygon(particle, new Vector(0, 1, 0), r, s, ppe, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx))
                .execute(ctx);
          };
        }
        case "orbit" -> {
          NumValue radius = numAttr(attrs, "radius", 2.4, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.LINEAR.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue copies = numAttr(attrs, "copies", 3.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int c = Math.max(0, evalInt(copies, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || duration <= 0 || period <= 0 || c == 0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) c * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.presetOrbit(particle, r, duration, period, easingFromId(easingId), c, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx))
                .execute(ctx);
          };
        }
        case "swirl" -> {
          NumValue radius = numAttr(attrs, "radius", 1.8, stmtToken);
          NumValue height = numAttr(attrs, "height", 2.6, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue points = numAttr(attrs, "points", 22.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double h = evalDouble(height, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || h < 0.0 || duration <= 0 || period <= 0 || pts == 0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) pts * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.presetSwirl(particle, r, h, duration, period, easingFromId(easingId), pts, emitCount, evalDouble(offset, ctx), evalDouble(extra, ctx))
                .execute(ctx);
          };
        }
        case "shockwave" -> {
          NumValue startRadius = numAttr(attrs, "startRadius", 0.5, stmtToken);
          NumValue endRadius = numAttr(attrs, "endRadius", 7.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.OUT_QUAD.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue points = numAttr(attrs, "points", 56.0, stmtToken);
          return ctx -> {
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || pts == 0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) pts * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.presetShockwave(particle, start, end, duration, period, easingFromId(easingId), pts, emitCount,
                evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        case "beam_chargeup", "beam-chargeup" -> {
          NumValue startLength = numAttr(attrs, "startLength", 0.0, stmtToken);
          NumValue endLength = attrs.containsKey("endLength")
              ? numAttr(attrs, "endLength", 10.0, stmtToken)
              : numAttr(attrs, "length", 10.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 20.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue step = numAttr(attrs, "step", 0.35, stmtToken);
          return ctx -> {
            double start = evalDouble(startLength, ctx);
            double end = evalDouble(endLength, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            double st = evalDouble(step, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || st <= 0.0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) Math.ceil(Math.max(start, end) / st) * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Actions.presetBeamChargeup(particle, start, end, duration, period, easingFromId(easingId), st, emitCount,
                evalDouble(offset, ctx), evalDouble(extra, ctx)).execute(ctx);
          };
        }
        default -> throw error(stmtToken, "unknown particles shape: " + shape);
      }
    }

    private boolean hasPointAttrs(Map<String, Value> attrs, String prefix) {
      String base = prefix + "_";
      for (String key : attrs.keySet()) {
        if (key.startsWith(base)) {
          return true;
        }
      }
      return false;
    }

    private PointSpec pointSpecFromAttrs(Map<String, Value> attrs, String prefix, Token stmtToken, boolean required) {
      if (required && !hasPointAttrs(attrs, prefix)) {
        throw error(stmtToken, "missing " + prefix + "_* attributes");
      }
      String base = prefix + "_";
      NumValue forward = numAttr(attrs, base + "forward", 0.0, stmtToken);
      NumValue right = numAttr(attrs, base + "right", 0.0, stmtToken);
      NumValue up = numAttr(attrs, base + "up", 0.0, stmtToken);
      NumValue x = numAttr(attrs, base + "x", 0.0, stmtToken);
      NumValue y = numAttr(attrs, base + "y", 0.0, stmtToken);
      NumValue z = numAttr(attrs, base + "z", 0.0, stmtToken);
      return new PointSpec(forward, right, up, x, y, z);
    }

    private java.util.List<PointSpec> splinePointsFromAttrs(Map<String, Value> attrs, Token stmtToken) {
      java.util.Set<Integer> indices = new java.util.TreeSet<>();
      for (String key : attrs.keySet()) {
        if (key.length() < 3 || key.charAt(0) != 'p' || !Character.isDigit(key.charAt(1))) {
          continue;
        }
        int idx = 1;
        while (idx < key.length() && Character.isDigit(key.charAt(idx))) {
          idx++;
        }
        if (idx >= key.length() || key.charAt(idx) != '_') {
          continue;
        }
        try {
          int value = Integer.parseInt(key.substring(1, idx));
          indices.add(value);
        } catch (NumberFormatException ignored) {
        }
      }
      if (indices.size() < 2) {
        throw error(stmtToken, "particles.spline requires p0_*/p1_* (and more) point attributes");
      }
      java.util.List<PointSpec> points = new java.util.ArrayList<>(indices.size());
      for (int index : indices) {
        points.add(pointSpecFromAttrs(attrs, "p" + index, stmtToken, true));
      }
      return points;
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseWeightedChoice(Token stmtToken) {
      consume(TokenType.LBRACE);
      record Choice(ExprNode weight, dev.patric.dungeonsreborn.effects.actions.Action action) {
      }
      List<Choice> choices = new ArrayList<>();
      while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
        ExprNode weightExpr = parseExpression();
        if (lookahead.type != TokenType.COLON) {
          throw error(stmtToken, "choice weighted: expected ':' after weight");
        }
        consume(TokenType.COLON);
        Token choiceToken = lookahead;
        dev.patric.dungeonsreborn.effects.actions.Action action = guard(parseStatement(), choiceToken);
        choices.add(new Choice(weightExpr, action));
      }
      consume(TokenType.RBRACE);
      if (choices.isEmpty()) {
        throw error(stmtToken, "choice weighted: empty block");
      }
      return ctx -> {
        double totalWeight = 0.0;
        double[] weights = new double[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
          double w = evalExpr(ctx, choices.get(i).weight());
          if (w > 0.0) {
            weights[i] = w;
            totalWeight += w;
          } else {
            weights[i] = 0.0;
          }
        }
        if (!(totalWeight > 0.0)) {
          if (ctx.engine().isDebugEnabled()) {
            ctx.engine().debug("dsl choice weighted: no positive weights");
          }
          return;
        }
        double r = ctx.rng().nextDouble() * totalWeight;
        double acc = 0.0;
        for (int i = 0; i < choices.size(); i++) {
          acc += weights[i];
          if (r <= acc) {
            choices.get(i).action().execute(ctx);
            return;
          }
        }
        choices.get(choices.size() - 1).action().execute(ctx);
      };
    }

    private void parseMacro() {
      Token at = lookahead;
      String name = requireIdent("macro");
      if (macros.containsKey(name)) {
        throw error(at, "duplicate macro: " + name);
      }
      List<String> params = new ArrayList<>();
      Map<String, ScriptValue> defaults = new java.util.HashMap<>();
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        if (lookahead.type != TokenType.RPAREN) {
          String param = requireIdent("param");
          if (params.contains(param)) {
            throw error(at, "duplicate macro param: " + param);
          }
          params.add(param);
          if (lookahead.type == TokenType.EQUALS) {
            consume(TokenType.EQUALS);
            defaults.put(param, parseAssignValue());
          }
          while (lookahead.type == TokenType.COMMA) {
            consume(TokenType.COMMA);
            String nextParam = requireIdent("param");
            if (params.contains(nextParam)) {
              throw error(at, "duplicate macro param: " + nextParam);
            }
            params.add(nextParam);
            if (lookahead.type == TokenType.EQUALS) {
              consume(TokenType.EQUALS);
              defaults.put(nextParam, parseAssignValue());
            }
          }
        }
        consume(TokenType.RPAREN);
      }
      dev.patric.dungeonsreborn.effects.actions.Action body = parseBlock();
      macros.put(name, new MacroDef(List.copyOf(params), java.util.Collections.unmodifiableMap(defaults), body, at));
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseMacroCall(Token stmtToken) {
      String name = requireIdent("macro");
      MacroDef def = macros.get(name);
      if (def == null) {
        throw error(stmtToken, "unknown macro: " + name);
      }
      Map<String, ScriptValue> args = parseCallArgs();
      for (String param : def.params()) {
        if (!args.containsKey(param) && !def.defaults().containsKey(param)) {
          throw error(stmtToken, "missing macro arg: " + param);
        }
      }
      for (String arg : args.keySet()) {
        if (!def.params().contains(arg)) {
          throw error(stmtToken, "unexpected macro arg: " + arg);
        }
      }
      return ctx -> {
        String frame = name + "@" + pathAt(stmtToken);
        java.util.ArrayDeque<String> stack = macroStack(ctx);
        if (stack.size() >= MAX_MACRO_DEPTH) {
          throw new IllegalArgumentException(pathAt(stmtToken) + ": macro depth exceeded (" + MAX_MACRO_DEPTH + ")");
        }
        stack.addLast(frame);
        Map<String, Object> values = new java.util.HashMap<>();
        for (String param : def.params()) {
          ScriptValue value = args.containsKey(param) ? args.get(param) : def.defaults().get(param);
          values.put(param, value == null ? null : value.eval(ctx));
        }
        try {
          withTempVars(ctx, VarScope.CAST, values, () -> def.body().execute(ctx));
        } finally {
          stack.removeLast();
          if (stack.isEmpty()) {
            ctx.state().put(DSL_MACRO_STACK, null);
          }
        }
      };
    }

    private Map<String, ScriptValue> parseCallArgs() {
      Map<String, ScriptValue> args = new java.util.HashMap<>();
      while (lookahead.type == TokenType.IDENT && peekNextIs(TokenType.EQUALS)) {
        String key = requireIdent("arg");
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        args.put(key, value);
      }
      return args;
    }

    private java.util.ArrayDeque<String> macroStack(CastContext ctx) {
      Object current = ctx.state().get(DSL_MACRO_STACK);
      if (current instanceof java.util.ArrayDeque<?> deque) {
        @SuppressWarnings("unchecked")
        java.util.ArrayDeque<String> stack = (java.util.ArrayDeque<String>) deque;
        return stack;
      }
      java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
      ctx.state().put(DSL_MACRO_STACK, stack);
      return stack;
    }

    private String renderMacroStack(CastContext ctx) {
      Object current = ctx.state().get(DSL_MACRO_STACK);
      if (!(current instanceof java.util.ArrayDeque<?> deque) || deque.isEmpty()) {
        return "";
      }
      String joined = deque.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" -> "));
      return " (macro stack: " + joined + ")";
    }

    private boolean consumeOps(CastContext ctx, int cost) {
      long tick = ctx.engine().tickNow();
      Object lastTickObj = ctx.state().get(DSL_OPS_TICK);
      long lastTick = lastTickObj instanceof Number n ? n.longValue() : -1L;
      int used = (int) Math.floor(numericVar(ctx.state().get(DSL_OPS_USED), 0.0));
      if (lastTick != tick) {
        used = 0;
        ctx.state().put(DSL_OPS_TICK, tick);
      }
      used += Math.max(1, cost);
      if (used > MAX_OPS_PER_TICK) {
        Object warnTick = ctx.state().get(DSL_OPS_WARN_TICK);
        long lastWarn = warnTick instanceof Number n ? n.longValue() : -1L;
        if (lastWarn != tick && ctx.engine().isDebugEnabled()) {
          ctx.engine().debug("dsl op budget exceeded (" + used + " > " + MAX_OPS_PER_TICK + ")");
          ctx.state().put(DSL_OPS_WARN_TICK, tick);
        }
        return false;
      }
      ctx.state().put(DSL_OPS_USED, used);
      return true;
    }

    private boolean consumeParticles(CastContext ctx, long count) {
      if (count <= 0) {
        return true;
      }
      long used = (long) Math.floor(numericVar(ctx.state().get(DSL_PARTICLE_USED), 0.0));
      long next = used + count;
      if (next > MAX_PARTICLES_PER_CAST) {
        if (ctx.engine().isDebugEnabled()) {
          ctx.engine().debug("dsl particle budget exceeded (" + next + " > " + MAX_PARTICLES_PER_CAST + ")");
        }
        return false;
      }
      ctx.state().put(DSL_PARTICLE_USED, next);
      return true;
    }

    private void incPending(CastContext ctx) {
      int pending = (int) Math.floor(numericVar(ctx.state().get(DSL_PENDING), 0.0));
      ctx.state().put(DSL_PENDING, pending + 1);
    }

    private void decPending(CastContext ctx) {
      int pending = (int) Math.floor(numericVar(ctx.state().get(DSL_PENDING), 0.0));
      pending = Math.max(0, pending - 1);
      ctx.state().put(DSL_PENDING, pending);
      runFinishIfReady(ctx);
    }

    private EntityActions.DamagePolicy parseDamagePolicy(String raw, Token at) {
      if (raw == null) {
        return EntityActions.DamagePolicy.hostileDefault();
      }
      String policy = raw.trim().toLowerCase(Locale.ROOT);
      return switch (policy) {
        case "any" -> EntityActions.DamagePolicy.any();
        case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
        case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
        case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
        default -> throw error(at, "unknown damage policy: " + raw);
      };
    }

    private DamageType parseDamageType(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected damage type");
      }
      String raw = value.text();
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      try {
        return DamageType.valueOf(normalized);
      } catch (IllegalArgumentException ex) {
        String suggestion = suggestEnumValue(raw, DamageType.class);
        String msg = "invalid " + key + "=" + raw;
        if (suggestion != null) {
          msg += " (did you mean " + suggestion + "?)";
        }
        throw error(at, msg);
      }
    }

    private PotionEffectType parsePotionEffect(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected potion effect");
      }
      Map<String, Object> map = java.util.Map.of(key, value.text);
      return potionEffectValue(map, key, pathAt(at) + "." + key);
    }

    private String stringValue(Value value, String key, Token at) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected string");
      }
      return value.text();
    }

    private boolean boolAttr(Map<String, Value> attrs, String key, boolean def, Token at) {
      Value v = attrs.get(key);
      if (v == null) {
        return def;
      }
      switch (v.kind()) {
        case NUMBER -> {
          return Math.abs(v.number()) > 1e-9;
        }
        case STRING, IDENT -> {
          String raw = v.text().trim();
          if ("true".equalsIgnoreCase(raw)) {
            return true;
          }
          if ("false".equalsIgnoreCase(raw)) {
            return false;
          }
          throw error(at, "invalid " + key + ": expected boolean");
        }
        default -> throw error(at, "invalid " + key + ": expected boolean");
      }
    }

    private Targeter<LivingEntity> parseTargeter(Map<String, Value> attrs, Token at) {
      Value typeValue = requireAttr(attrs, "type", at);
      String type = stringValue(typeValue, "type", at).trim().toLowerCase(Locale.ROOT);
      boolean ignoreCaster = boolAttr(attrs, "ignoreCaster", true, at);
      String filterRaw = stringAttr(attrs, "filter", "any", at).trim().toLowerCase(Locale.ROOT);
      java.util.function.Predicate<LivingEntity> filter = switch (filterRaw) {
        case "any" -> e -> true;
        case "players" -> e -> e instanceof Player;
        case "mobs", "non_players", "non-players" -> e -> !(e instanceof Player);
        default -> throw error(at, "invalid filter: " + filterRaw + " (use any|players|mobs)");
      };

      return switch (type) {
        case "self" -> ctx -> {
          LivingEntity caster = ctx.caster();
          return filter.test(caster) ? List.of(caster) : List.of();
        };
        case "projectile_hit", "projectile-hit" -> Targeters.projectileHit();
        case "look_ray", "look-ray" -> {
          NumValue maxDistance = numAttr(attrs, "maxDistance", 20.0, at);
          NumValue raySize = numAttr(attrs, "raySize", 0.35, at);
          boolean stopOnBlock = boolAttr(attrs, "stopOnBlock", true, at);
          yield ctx -> {
            double max = evalDouble(maxDistance, ctx);
            double size = evalDouble(raySize, ctx);
            if (max <= 0.0 || size < 0.0) {
              return List.of();
            }
            return Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, filter).select(ctx);
          };
        }
        case "sphere" -> {
          NumValue radius = numAttr(attrs, "radius", 5.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            if (r < 0.0) {
              return List.of();
            }
            return Targeters.sphere(r, ignoreCaster, filter).select(ctx);
          };
        }
        case "nearest" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            if (r < 0.0) {
              return List.of();
            }
            return Targeters.nearest(r, ignoreCaster, filter).select(ctx);
          };
        }
        case "cone" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 90.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            if (r < 0.0 || angle <= 0.0 || angle > 180.0) {
              return List.of();
            }
            return Targeters.cone(r, angle, ignoreCaster, filter).select(ctx);
          };
        }
        case "box" -> {
          NumValue xRadius = numAttr(attrs, "xRadius", 6.0, at);
          NumValue yRadius = numAttr(attrs, "yRadius", 3.0, at);
          NumValue zRadius = numAttr(attrs, "zRadius", 6.0, at);
          yield ctx -> {
            double xr = evalDouble(xRadius, ctx);
            double yr = evalDouble(yRadius, ctx);
            double zr = evalDouble(zRadius, ctx);
            if (xr < 0.0 || yr < 0.0 || zr < 0.0) {
              return List.of();
            }
            return Targeters.box(xr, yr, zr, ignoreCaster, filter).select(ctx);
          };
        }
        case "cylinder" -> {
          NumValue radius = numAttr(attrs, "radius", 6.0, at);
          NumValue halfHeight = attrs.containsKey("halfHeight")
              ? numAttr(attrs, "halfHeight", 2.0, at)
              : numAttr(attrs, "height", 4.0, at);
          boolean useHalfHeight = attrs.containsKey("halfHeight");
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            double hh = evalDouble(halfHeight, ctx);
            if (!useHalfHeight) {
              hh = hh / 2.0;
            }
            if (r < 0.0 || hh < 0.0) {
              return List.of();
            }
            return Targeters.cylinder(r, hh, ignoreCaster, filter).select(ctx);
          };
        }
        case "capsule_ray", "capsule-ray" -> {
          NumValue maxDistance = numAttr(attrs, "maxDistance", 10.0, at);
          NumValue radius = numAttr(attrs, "radius", 1.5, at);
          boolean stopOnBlock = boolAttr(attrs, "stopOnBlock", true, at);
          yield ctx -> {
            double max = evalDouble(maxDistance, ctx);
            double r = evalDouble(radius, ctx);
            if (max <= 0.0 || r < 0.0) {
              return List.of();
            }
            return Targeters.capsuleRay(max, r, stopOnBlock, ignoreCaster, filter).select(ctx);
          };
        }
        default -> throw error(at, "unknown targeter type: " + type);
      };
    }

    private java.util.function.DoubleUnaryOperator easingFromId(EasingId id) {
      return switch (id) {
        case LINEAR -> Easings::linear;
        case IN_OUT_CUBIC -> Easings::inOutCubic;
        case OUT_QUAD -> Easings::outQuad;
      };
    }

    private EasingId parseEasing(String raw, Token token) {
      if (raw == null) {
        return EasingId.IN_OUT_CUBIC;
      }
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      try {
        return EasingId.valueOf(normalized);
      } catch (Exception ex) {
        String suggestion = suggestEnumValue(raw, EasingId.class);
        if (suggestion != null) {
          throw error(token, "unknown easing: " + raw + " (did you mean " + suggestion + "?)");
        }
        throw error(token, "unknown easing: " + raw);
      }
    }

    private Map<String, Value> parseAttributes() {
      Map<String, Value> attrs = new java.util.HashMap<>();
      while (lookahead.type == TokenType.IDENT && peekNextIs(TokenType.EQUALS)) {
        String key = requireIdent("attribute");
        consume(TokenType.EQUALS);
        Value value = parseValue();
        attrs.put(key, value);
      }
      return attrs;
    }

    private Value parseValue() {
      Token t = lookahead;
      if (t.type == TokenType.NUMBER) {
        if (peekNextIs(TokenType.OP)) {
          ExprNode expr = parseExpression();
          return new Value(ValueKind.EXPR, null, 0.0, expr, t.line, t.column);
        }
        consume(TokenType.NUMBER);
        return new Value(ValueKind.NUMBER, t.text, t.number, null, t.line, t.column);
      }
      if (t.type == TokenType.STRING) {
        consume(TokenType.STRING);
        return new Value(ValueKind.STRING, t.text, t.number, null, t.line, t.column);
      }
      if (t.type == TokenType.IDENT) {
        if (peekNextIs(TokenType.OP) || peekNextIs(TokenType.LPAREN)) {
          ExprNode expr = parseExpression();
          return new Value(ValueKind.EXPR, null, 0.0, expr, t.line, t.column);
        }
        consume(TokenType.IDENT);
        return new Value(ValueKind.IDENT, t.text, t.number, null, t.line, t.column);
      }
      if (t.type == TokenType.OP || t.type == TokenType.LPAREN) {
        ExprNode expr = parseExpression();
        return new Value(ValueKind.EXPR, null, 0.0, expr, t.line, t.column);
      }
      throw error("expected value");
    }

    private Value requireAttr(Map<String, Value> attrs, String key, Token at) {
      Value value = attrs.get(key);
      if (value == null) {
        throw new IllegalArgumentException(missingAttrMessage(attrs, key, at));
      }
      return value;
    }

    private String missingAttrMessage(Map<String, Value> attrs, String key, Token at) {
      StringBuilder msg = new StringBuilder(pathAt(at)).append(": missing ").append(key);
      String suggestion = suggestClosest(key, attrs.keySet());
      if (suggestion != null) {
        msg.append(" (did you mean ").append(suggestion).append("?)");
      } else if (!attrs.isEmpty()) {
        msg.append(" (available: ").append(formatKeys(attrs.keySet(), 8)).append(")");
      }
      return msg.toString();
    }

    private String requireIdent(String label) {
      Token t = lookahead;
      if (t.type != TokenType.IDENT) {
        throw error("expected " + label);
      }
      consume(TokenType.IDENT);
      return t.text;
    }

    private String requireStringToken(String label) {
      Token t = lookahead;
      if (t.type != TokenType.STRING) {
        throw error("expected " + label + " string");
      }
      consume(TokenType.STRING);
      return t.text;
    }

    private Value requireValue(String label) {
      if (lookahead.type != TokenType.STRING && lookahead.type != TokenType.IDENT) {
        throw error("expected " + label);
      }
      return parseValue();
    }

    private NumValue numAttr(Map<String, Value> attrs, String key, double def, Token at) {
      Value v = attrs.get(key);
      if (v == null) {
        return new ConstNum(def);
      }
      return numFromValue(v, key, at);
    }

    private NumValue numAttrAlias(Map<String, Value> attrs, String primary, String secondary, double def, Token at) {
      if (attrs.containsKey(primary)) {
        return numAttr(attrs, primary, def, at);
      }
      if (secondary != null && attrs.containsKey(secondary)) {
        return numAttr(attrs, secondary, def, at);
      }
      return new ConstNum(def);
    }

    private String stringAttr(Map<String, Value> attrs, String key, String def, Token at) {
      Value v = attrs.get(key);
      if (v == null) {
        return def;
      }
      if (v.kind() == ValueKind.EXPR || v.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected string");
      }
      return v.text;
    }

    private List<String> splitIdList(String raw) {
      if (raw == null || raw.isBlank()) {
        return List.of();
      }
      String[] parts = raw.split(",");
      List<String> out = new ArrayList<>();
      for (String part : parts) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          out.add(Ids.normalize(trimmed));
        }
      }
      return List.copyOf(out);
    }

    private Particle parseParticle(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected particle name");
      }
      Map<String, Object> map = java.util.Map.of(key, value.text);
      return enumValue(map, key, Particle.class, pathAt(at));
    }

    private Sound parseSound(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected sound name");
      }
      Map<String, Object> map = java.util.Map.of(key, value.text);
      return soundValue(map, key, pathAt(at));
    }

    private record VarTarget(VarScope scope, String key) {
    }

    private VarTarget parseVarTarget() {
      if (lookahead.type == TokenType.IDENT && "var".equalsIgnoreCase(lookahead.text)) {
        consume(TokenType.IDENT);
        consume(TokenType.LPAREN);
        Token at = lookahead;
        String rawKey = requireStringToken("var");
        consume(TokenType.RPAREN);
        return parseVarTarget(rawKey, at);
      }
      Token at = lookahead;
      String rawKey = requireIdent("var");
      return parseVarTarget(rawKey, at);
    }

    private VarTarget parseVarTarget(String rawKey, Token at) {
      String trimmed = rawKey.trim();
      if (trimmed.isEmpty()) {
        throw error(at, "empty var key");
      }
      VarScope scope = VarScope.CAST;
      String key = trimmed;
      int idx = trimmed.indexOf(':');
      if (idx > 0) {
        String prefix = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
        String rest = trimmed.substring(idx + 1).trim();
        if (rest.isEmpty()) {
          throw error(at, "empty var key");
        }
        switch (prefix) {
          case "cast" -> scope = VarScope.CAST;
          case "player" -> scope = VarScope.PLAYER;
          case "entity", "target" -> scope = VarScope.ENTITY;
          default -> {
          }
        }
        key = rest;
      }
      return new VarTarget(scope, key);
    }

    private ScriptValue parseAssignValue() {
      if (lookahead.type == TokenType.STRING) {
        String raw = requireStringToken("value");
        return new LiteralValue(raw);
      }
      if (lookahead.type == TokenType.IDENT) {
        if ("true".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.TRUE);
        }
        if ("false".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.FALSE);
        }
        if ("var".equalsIgnoreCase(lookahead.text) && peekNextIs(TokenType.LPAREN)) {
          consume(TokenType.IDENT);
          consume(TokenType.LPAREN);
          String rawKey = requireStringToken("var");
          consume(TokenType.RPAREN);
          return new VarValue(rawKey);
        }
      }
      ExprNode expr = parseExpression();
      return new ExprValue(expr);
    }

    private interface Condition {
      boolean test(CastContext ctx);
    }

    private Condition parseCondition() {
      ScriptValue left = parseConditionValue();
      if (lookahead.type == TokenType.COMP) {
        String op = lookahead.text;
        consume(TokenType.COMP);
        ScriptValue right = parseConditionValue();
        return ctx -> compare(left.eval(ctx), right.eval(ctx), op);
      }
      return ctx -> truthy(left.eval(ctx));
    }

    private ScriptValue parseConditionValue() {
      if (lookahead.type == TokenType.STRING) {
        String raw = requireStringToken("value");
        return new LiteralValue(raw);
      }
      if (lookahead.type == TokenType.IDENT && "var".equalsIgnoreCase(lookahead.text) && peekNextIs(TokenType.LPAREN)) {
        consume(TokenType.IDENT);
        consume(TokenType.LPAREN);
        String rawKey = requireStringToken("var");
        consume(TokenType.RPAREN);
        return new VarValue(rawKey);
      }
      if (lookahead.type == TokenType.IDENT) {
        if ("true".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.TRUE);
        }
        if ("false".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.FALSE);
        }
        if (peekNextIs(TokenType.COMP)) {
          String name = requireIdent("value");
          return new VarValue(name);
        }
      }
      ExprNode expr = parseExpression();
      return new ExprValue(expr);
    }

    private boolean truthy(Object value) {
      if (value == null) {
        return false;
      }
      if (value instanceof Boolean b) {
        return b;
      }
      if (value instanceof Number n) {
        return Math.abs(n.doubleValue()) > 1e-9;
      }
      String s = String.valueOf(value);
      return !s.isBlank() && !"false".equalsIgnoreCase(s);
    }

    private boolean compare(Object left, Object right, String op) {
      Double ln = toNumber(left);
      Double rn = toNumber(right);
      if (ln != null && rn != null) {
        return switch (op) {
          case "==" -> Double.compare(ln, rn) == 0;
          case "!=" -> Double.compare(ln, rn) != 0;
          case ">" -> ln > rn;
          case ">=" -> ln >= rn;
          case "<" -> ln < rn;
          case "<=" -> ln <= rn;
          default -> false;
        };
      }
      String ls = String.valueOf(left == null ? "" : left);
      String rs = String.valueOf(right == null ? "" : right);
      return switch (op) {
        case "==" -> ls.equals(rs);
        case "!=" -> !ls.equals(rs);
        default -> false;
      };
    }

    private Double toNumber(Object value) {
      if (value == null) {
        return null;
      }
      if (value instanceof Number n) {
        return n.doubleValue();
      }
      try {
        return Double.parseDouble(String.valueOf(value));
      } catch (Exception ignored) {
        return null;
      }
    }

    private Object resolveValue(String name, CastContext ctx) {
      if (name == null) {
        return null;
      }
      String trimmed = name.trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      String lower = trimmed.toLowerCase(Locale.ROOT);
      if (lower.startsWith("var:")) {
        String[] parts = trimmed.split(":", 3);
        if (parts.length == 2) {
          String key = parts[1].trim();
          if (!key.isEmpty()) {
            Object v = vars(ctx, VarScope.CAST).get(key);
            if (v == null) {
              v = vars(ctx, VarScope.PLAYER).get(key);
            }
            if (v == null) {
              v = vars(ctx, VarScope.ENTITY).get(key);
            }
            return v;
          }
        } else if (parts.length == 3) {
          VarScope scope = parseVarScope(parts[1].trim(), "dsl", VarScope.CAST);
          String key = parts[2].trim();
          if (!key.isEmpty()) {
            return vars(ctx, scope).get(key);
          }
        }
      }
      int idx = trimmed.indexOf(':');
      if (idx > 0) {
        String prefix = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
        String key = trimmed.substring(idx + 1).trim();
        if (!key.isEmpty()) {
          return switch (prefix) {
            case "cast" -> vars(ctx, VarScope.CAST).get(key);
            case "player" -> vars(ctx, VarScope.PLAYER).get(key);
            case "entity", "target" -> vars(ctx, VarScope.ENTITY).get(key);
            default -> null;
          };
        }
      }
      Object v = vars(ctx, VarScope.CAST).get(trimmed);
      if (v != null) {
        return v;
      }
      v = vars(ctx, VarScope.PLAYER).get(trimmed);
      if (v != null) {
        return v;
      }
      v = vars(ctx, VarScope.ENTITY).get(trimmed);
      if (v != null) {
        return v;
      }
      return resolveVar(trimmed, ctx);
    }

    private NumValue numFromValue(Value value, String key, Token at) {
      switch (value.kind()) {
        case NUMBER -> {
          return new ConstNum(value.number());
        }
        case IDENT -> {
          return exprValue(value.text(), pathAt(at) + "." + key);
        }
        case EXPR -> {
          ExprNode expr = value.expr();
          return ctx -> evalExpr(ctx, expr);
        }
        case STRING -> {
          String raw = value.text();
          if (raw.trim().startsWith("expr:")) {
            return exprValue(raw.trim().substring("expr:".length()).trim(), pathAt(at) + "." + key);
          }
          try {
            return new ConstNum(Double.parseDouble(raw.trim()));
          } catch (NumberFormatException ex) {
            throw error(at, "invalid number for " + key + ": " + raw);
          }
        }
        default -> throw error(at, "invalid number for " + key);
      }
    }

    private double evalExpr(CastContext ctx, ExprNode expr) {
      double v = expr.eval(ctx, EffectsYamlAbilities.this::resolveVar);
      if (!Double.isFinite(v)) {
        return 0.0;
      }
      return v;
    }

    private ExprNode parseExpression() {
      ExprNode expr = parseTerm();
      while (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parseTerm();
        expr = binary(op, expr, right);
      }
      return expr;
    }

    private ExprNode parseTerm() {
      ExprNode left = parsePower();
      while (lookahead.type == TokenType.OP && ("*".equals(lookahead.text) || "/".equals(lookahead.text) || "%".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary(op, left, right);
      }
      return left;
    }

    private ExprNode parsePower() {
      ExprNode left = parseUnary();
      if (lookahead.type == TokenType.OP && "^".equals(lookahead.text)) {
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary("^", left, right);
      }
      return left;
    }

    private ExprNode parseUnary() {
      if (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode inner = parseUnary();
        return unary(op, inner);
      }
      return parsePrimary();
    }

    private ExprNode parsePrimary() {
      if (lookahead.type == TokenType.NUMBER) {
        double v = lookahead.number;
        consume(TokenType.NUMBER);
        return (ctx, vars) -> v;
      }
      if (lookahead.type == TokenType.IDENT) {
        Token nameToken = lookahead;
        String name = nameToken.text;
        consume(TokenType.IDENT);
        if (lookahead.type == TokenType.LPAREN) {
          consume(TokenType.LPAREN);
          List<ExprNode> args = new ArrayList<>();
          if (lookahead.type != TokenType.RPAREN) {
            args.add(parseExpression());
            while (lookahead.type == TokenType.COMMA) {
              consume(TokenType.COMMA);
              args.add(parseExpression());
            }
          }
          consume(TokenType.RPAREN);
          return function(nameToken, name, args);
        }
        return (ctx, vars) -> vars.resolve(name, ctx);
      }
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        ExprNode expr = parseExpression();
        consume(TokenType.RPAREN);
        return expr;
      }
      throw error("expected expression");
    }

    private ExprNode binary(String op, ExprNode a, ExprNode b) {
      return switch (op) {
        case "+" -> (ctx, vars) -> a.eval(ctx, vars) + b.eval(ctx, vars);
        case "-" -> (ctx, vars) -> a.eval(ctx, vars) - b.eval(ctx, vars);
        case "*" -> (ctx, vars) -> a.eval(ctx, vars) * b.eval(ctx, vars);
        case "/" -> (ctx, vars) -> a.eval(ctx, vars) / b.eval(ctx, vars);
        case "%" -> (ctx, vars) -> a.eval(ctx, vars) % b.eval(ctx, vars);
        case "^" -> (ctx, vars) -> Math.pow(a.eval(ctx, vars), b.eval(ctx, vars));
        default -> (ctx, vars) -> 0.0;
      };
    }

    private ExprNode unary(String op, ExprNode inner) {
      return switch (op) {
        case "-" -> (ctx, vars) -> -inner.eval(ctx, vars);
        case "+" -> (ctx, vars) -> inner.eval(ctx, vars);
        default -> (ctx, vars) -> inner.eval(ctx, vars);
      };
    }

    private ExprNode function(Token at, String name, List<ExprNode> args) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (!ALLOWED_FUNCTION_SET.contains(lower)) {
        throw error(at, "unknown function: " + name + " (use " + String.join("|", ALLOWED_FUNCTIONS) + ")");
      }
      return switch (lower) {
        case "min" -> (ctx, vars) -> {
          if (args.isEmpty()) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.min(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "max" -> (ctx, vars) -> {
          if (args.isEmpty()) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.max(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "clamp" -> (ctx, vars) -> {
          if (args.size() < 3) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          double min = args.get(1).eval(ctx, vars);
          double max = args.get(2).eval(ctx, vars);
          return Math.max(min, Math.min(max, v));
        };
        case "lerp" -> (ctx, vars) -> {
          if (args.size() < 3) {
            return 0.0;
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          double t = args.get(2).eval(ctx, vars);
          return a + (b - a) * t;
        };
        case "rand" -> (ctx, vars) -> {
          if (args.size() < 2) {
            return 0.0;
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          double r = ctx.rng().nextDouble();
          return a + r * (b - a);
        };
        case "abs" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.abs(args.get(0).eval(ctx, vars));
        case "floor" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.floor(args.get(0).eval(ctx, vars));
        case "ceil" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.ceil(args.get(0).eval(ctx, vars));
        case "round" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.round(args.get(0).eval(ctx, vars));
        default -> (ctx, vars) -> 0.0;
      };
    }

    private String pathAt(Token token) {
      return path + " (" + token.line + ":" + token.column + ")";
    }

    private boolean peekNextIs(TokenType type) {
      Token current = lookahead;
      Token next = nextTokenInternal(current);
      return next.type == type;
    }

    private Token nextTokenInternal(Token current) {
      int savedPos = pos;
      int savedLine = line;
      int savedCol = column;
      Token savedLookahead = lookahead;
      lookahead = current;
      Token next = nextToken();
      pos = savedPos;
      line = savedLine;
      column = savedCol;
      lookahead = savedLookahead;
      return next;
    }

    private void consume(TokenType type) {
      if (lookahead.type != type) {
        throw error("expected " + type.name().toLowerCase(Locale.ROOT));
      }
      lookahead = nextToken();
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(path + " (" + line + ":" + column + "): " + message);
    }

    private IllegalArgumentException error(Token token, String message) {
      return new IllegalArgumentException(path + " (" + token.line + ":" + token.column + "): " + message);
    }

    private Token nextToken() {
      int len = input.length();
      while (pos < len) {
        char c = input.charAt(pos);
        if (c == '#') {
          skipLineComment();
          continue;
        }
        if (Character.isWhitespace(c)) {
          advance(c);
          continue;
        }
        int startLine = line;
        int startCol = column;
        if (c == '{') {
          advance(c);
          return new Token(TokenType.LBRACE, "{", 0.0, startLine, startCol);
        }
        if (c == '}') {
          advance(c);
          return new Token(TokenType.RBRACE, "}", 0.0, startLine, startCol);
        }
        if (c == '(') {
          advance(c);
          return new Token(TokenType.LPAREN, "(", 0.0, startLine, startCol);
        }
        if (c == ')') {
          advance(c);
          return new Token(TokenType.RPAREN, ")", 0.0, startLine, startCol);
        }
        if (c == ',') {
          advance(c);
          return new Token(TokenType.COMMA, ",", 0.0, startLine, startCol);
        }
        if (c == ':') {
          advance(c);
          return new Token(TokenType.COLON, ":", 0.0, startLine, startCol);
        }
        if (c == '=') {
          if (peekChar() == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "==", 0.0, startLine, startCol);
          }
          advance(c);
          return new Token(TokenType.EQUALS, "=", 0.0, startLine, startCol);
        }
        if (c == '!') {
          if (peekChar() == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "!=", 0.0, startLine, startCol);
          }
          throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): unexpected char: " + c);
        }
        if (c == '<' || c == '>') {
          char next = peekChar();
          if (next == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "" + c + next, 0.0, startLine, startCol);
          }
          advance(c);
          return new Token(TokenType.COMP, String.valueOf(c), 0.0, startLine, startCol);
        }
        if (c == '"' ) {
          String text = readString();
          return new Token(TokenType.STRING, text, 0.0, startLine, startCol);
        }
        if (c == '-' || c == '+') {
          char next = peekChar();
          if (Character.isDigit(next) || next == '.') {
            String num = readNumber();
            try {
              double value = Double.parseDouble(num);
              return new Token(TokenType.NUMBER, num, value, startLine, startCol);
            } catch (NumberFormatException ex) {
              throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): invalid number: " + num);
            }
          }
          advance(c);
          return new Token(TokenType.OP, String.valueOf(c), 0.0, startLine, startCol);
        }
        if (c == '*' || c == '/' || c == '%' || c == '^') {
          advance(c);
          return new Token(TokenType.OP, String.valueOf(c), 0.0, startLine, startCol);
        }
        if (Character.isDigit(c) || c == '.') {
          String num = readNumber();
          try {
            double value = Double.parseDouble(num);
            return new Token(TokenType.NUMBER, num, value, startLine, startCol);
          } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): invalid number: " + num);
          }
        }
        if (Character.isLetter(c) || c == '_' ) {
          String ident = readIdent();
          return new Token(TokenType.IDENT, ident, 0.0, startLine, startCol);
        }
        throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): unexpected char: " + c);
      }
      return new Token(TokenType.EOF, "", 0.0, line, column);
    }

    private char peekChar() {
      if (pos + 1 >= input.length()) {
        return '\0';
      }
      return input.charAt(pos + 1);
    }

    private void advance(char c) {
      pos++;
      if (c == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }

    private void skipLineComment() {
      while (pos < input.length()) {
        char c = input.charAt(pos);
        advance(c);
        if (c == '\n') {
          return;
        }
      }
    }

    private String readString() {
      StringBuilder out = new StringBuilder();
      char quote = input.charAt(pos);
      advance(quote);
      while (pos < input.length()) {
        char c = input.charAt(pos);
        advance(c);
        if (c == quote) {
          return out.toString();
        }
        if (c == '\\' && pos < input.length()) {
          char next = input.charAt(pos);
          advance(next);
          switch (next) {
            case 'n' -> out.append('\n');
            case 't' -> out.append('\t');
            case '"' -> out.append('"');
            case '\\' -> out.append('\\');
            default -> out.append(next);
          }
          continue;
        }
        out.append(c);
      }
      throw new IllegalArgumentException(path + " (" + line + ":" + column + "): unterminated string");
    }

    private String readNumber() {
      int start = pos;
      if (pos < input.length()) {
        char c = input.charAt(pos);
        if (c == '-' || c == '+') {
          advance(c);
        }
      }
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isDigit(c) || c == '.') {
          advance(c);
          continue;
        }
        break;
      }
      return input.substring(start, pos);
    }

    private String readIdent() {
      int start = pos;
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == ':') {
          advance(c);
          continue;
        }
        break;
      }
      return input.substring(start, pos);
    }
  }

  private void runFinishIfReady(CastContext ctx) {
    if (ctx.state().isCancelled()) {
      return;
    }
    if (!Boolean.TRUE.equals(ctx.state().get(DSL_CAST_DONE))) {
      return;
    }
    int pending = (int) Math.floor(numericVar(ctx.state().get(DSL_PENDING), 0.0));
    if (pending > 0) {
      return;
    }
    Object hook = ctx.state().get(DSL_ON_FINISH);
    if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
      ctx.state().put(DSL_ON_FINISH, null);
      action.execute(ctx);
    }
  }

  private void withTempVar(CastContext ctx, VarScope scope, String key, Object value, Runnable action) {
    Map<String, Object> vars = vars(ctx, scope);
    boolean had = vars.containsKey(key);
    Object prev = vars.get(key);
    setVar(ctx, scope, key, value);
    try {
      action.run();
    } finally {
      if (!had) {
        vars.remove(key);
      } else {
        vars.put(key, prev);
      }
    }
  }

  private void withTempVars(CastContext ctx, VarScope scope, Map<String, Object> values, Runnable action) {
    Map<String, Object> vars = vars(ctx, scope);
    Map<String, Object> prev = new java.util.HashMap<>();
    Set<String> had = new java.util.HashSet<>();
    Set<String> touched = new java.util.HashSet<>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String key = entry.getKey();
      if (vars.containsKey(key)) {
        had.add(key);
        prev.put(key, vars.get(key));
      }
      Object value = entry.getValue();
      if (setVar(ctx, scope, key, value)) {
        touched.add(key);
      }
    }
    try {
      action.run();
    } finally {
      for (String key : touched) {
        if (had.contains(key)) {
          vars.put(key, prev.get(key));
        } else {
          vars.remove(key);
        }
      }
    }
  }

  private static CastContext followCasterContext(CastContext ctx) {
    Location origin;
    Vector direction;
    if (ctx.caster() instanceof Player player) {
      origin = player.getEyeLocation();
      direction = origin.getDirection();
    } else {
      origin = ctx.caster().getLocation();
      direction = origin.getDirection();
    }
    return new CastContext(
        ctx.engine(),
        ctx.plugin(),
        ctx.castId(),
        ctx.abilityId(),
        ctx.engine().tickNow(),
        ctx.state(),
        ctx.caster(),
        origin.clone(),
        direction.clone(),
        ctx.itemInHand());
  }

  private Targeter<LivingEntity> compileTargeter(Map<String, Object> node, String path) {
    String type = requireString(node, "type", path + ".type").trim().toLowerCase(Locale.ROOT);
    boolean ignoreCaster = bool(node, "ignoreCaster", true);

    String filterRaw = string(node, "filter", "any").trim().toLowerCase(Locale.ROOT);
    java.util.function.Predicate<LivingEntity> filter = switch (filterRaw) {
      case "any" -> e -> true;
      case "players" -> e -> e instanceof org.bukkit.entity.Player;
      case "mobs", "non_players", "non-players" -> e -> !(e instanceof org.bukkit.entity.Player);
      default -> throw new IllegalArgumentException(path + ".filter: invalid filter=" + filterRaw + " (use any|players|mobs)");
    };

    boolean cacheable = true;
    Targeter<LivingEntity> base = switch (type) {
      case "self" -> Targeters.self();
      case "projectile_hit", "projectile-hit" -> Targeters.projectileHit();
      case "look_ray", "look-ray" -> {
        NumValue maxDistance = numValue(node, "maxDistance", 20.0, path);
        NumValue raySize = numValue(node, "raySize", 0.35, path);
        cacheable = cacheable && maxDistance.isConstant() && raySize.isConstant();
        boolean stopOnBlock = bool(node, "stopOnBlock", true);
        yield ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double size = evalDouble(raySize, ctx);
          if (max <= 0.0 || size < 0.0) {
            return List.of();
          }
          return Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, filter).select(ctx);
        };
      }
      case "sphere" -> {
        NumValue radius = numValue(node, "radius", 5.0, path);
        cacheable = cacheable && radius.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          if (r < 0.0) {
            return List.of();
          }
          return Targeters.sphere(r, ignoreCaster, filter).select(ctx);
        };
      }
      case "nearest" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        cacheable = cacheable && radius.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          if (r < 0.0) {
            return List.of();
          }
          return Targeters.nearest(r, ignoreCaster, filter).select(ctx);
        };
      }
      case "cone" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        NumValue angleDegrees = numValue(node, "angleDegrees", 90.0, path);
        cacheable = cacheable && radius.isConstant() && angleDegrees.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          if (r < 0.0 || angle <= 0.0 || angle > 180.0) {
            return List.of();
          }
          return Targeters.cone(r, angle, ignoreCaster, filter).select(ctx);
        };
      }
      case "box" -> {
        NumValue xRadius = numValue(node, "xRadius", 6.0, path);
        NumValue yRadius = numValue(node, "yRadius", 3.0, path);
        NumValue zRadius = numValue(node, "zRadius", 6.0, path);
        cacheable = cacheable && xRadius.isConstant() && yRadius.isConstant() && zRadius.isConstant();
        yield ctx -> {
          double xr = evalDouble(xRadius, ctx);
          double yr = evalDouble(yRadius, ctx);
          double zr = evalDouble(zRadius, ctx);
          if (xr < 0.0 || yr < 0.0 || zr < 0.0) {
            return List.of();
          }
          return Targeters.box(xr, yr, zr, ignoreCaster, filter).select(ctx);
        };
      }
      case "cylinder" -> {
        NumValue radius = numValue(node, "radius", 6.0, path);
        NumValue halfHeight = node.containsKey("halfHeight")
            ? numValue(node, "halfHeight", 2.0, path)
            : numValue(node, "height", 4.0, path);
        cacheable = cacheable && radius.isConstant() && halfHeight.isConstant();
        boolean useHalfHeight = node.containsKey("halfHeight");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double hh = evalDouble(halfHeight, ctx);
          if (!useHalfHeight) {
            hh = hh / 2.0;
          }
          if (r < 0.0 || hh < 0.0) {
            return List.of();
          }
          return Targeters.cylinder(r, hh, ignoreCaster, filter).select(ctx);
        };
      }
      case "capsule_ray", "capsule-ray" -> {
        NumValue maxDistance = numValue(node, "maxDistance", 10.0, path);
        NumValue radius = numValue(node, "radius", 1.5, path);
        cacheable = cacheable && maxDistance.isConstant() && radius.isConstant();
        boolean stopOnBlock = bool(node, "stopOnBlock", true);
        yield ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double r = evalDouble(radius, ctx);
          if (max <= 0.0 || r < 0.0) {
            return List.of();
          }
          return Targeters.capsuleRay(max, r, stopOnBlock, ignoreCaster, filter).select(ctx);
        };
      }
      default -> throw new IllegalArgumentException(path + ".type: unknown type: " + type);
    };

    return cacheable ? Targeters.cachedPerTick("yaml:" + path, base) : base;
  }

  private interface NumValue {
    double eval(CastContext ctx);

    default boolean isConstant() {
      return false;
    }
  }

  private record ConstNum(double value) implements NumValue {
    @Override
    public double eval(CastContext ctx) {
      return value;
    }

    @Override
    public boolean isConstant() {
      return true;
    }
  }

  private interface ValueSupplier {
    Object eval(CastContext ctx);
  }

  private interface VarResolver {
    double resolve(String name, CastContext ctx);
  }

  private interface ExprNode {
    double eval(CastContext ctx, VarResolver vars);
  }

  private NumValue numValue(Map<String, Object> node, String key, double def, String path) {
    return numValue(node.get(key), def, path + "." + key);
  }

  private NumValue requireNumValue(Map<String, Object> node, String key, String path) {
    if (!node.containsKey(key)) {
      throw new IllegalArgumentException(missingKeyMessage(node, key, path));
    }
    return numValue(node.get(key), 0.0, path);
  }

  private NumValue numValue(Object raw, double def, String path) {
    if (raw == null) {
      return new ConstNum(def);
    }
    if (raw instanceof Number n) {
      return new ConstNum(n.doubleValue());
    }
    if (raw instanceof Map<?, ?> map) {
      Object expr = map.get("expr");
      if (expr == null) {
        throw new IllegalArgumentException(path + ": expected number or expr");
      }
      return exprValue(String.valueOf(expr), path + ".expr");
    }
    String s = String.valueOf(raw).trim();
    if (s.startsWith("expr:")) {
      return exprValue(s.substring("expr:".length()).trim(), path);
    }
    try {
      return new ConstNum(Double.parseDouble(s));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected number or expr");
    }
  }

  private static double evalDouble(NumValue value, CastContext ctx) {
    double v = value.eval(ctx);
    if (!Double.isFinite(v)) {
      if (ctx.engine().isDebugEnabled()) {
        ctx.engine().debug("expr produced non-finite value: " + v);
      }
      return 0.0;
    }
    return v;
  }

  private static int evalInt(NumValue value, CastContext ctx) {
    return (int) Math.round(evalDouble(value, ctx));
  }

  private static long evalLong(NumValue value, CastContext ctx) {
    return Math.round(evalDouble(value, ctx));
  }

  private ValueSupplier varValue(Object raw, String path) {
    if (raw == null) {
      return ctx -> null;
    }
    if (raw instanceof Map<?, ?> map && map.containsKey("expr")) {
      NumValue value = numValue(raw, 0.0, path);
      return ctx -> evalDouble(value, ctx);
    }
    if (raw instanceof String s && s.trim().startsWith("expr:")) {
      NumValue value = numValue(raw, 0.0, path);
      return ctx -> evalDouble(value, ctx);
    }
    return ctx -> raw;
  }

  private NumValue exprValue(String expr, String path) {
    ExprNode node = new ExprParser(expr, path).parse();
    return ctx -> node.eval(ctx, this::resolveVar);
  }

  private double resolveVar(String name, CastContext ctx) {
    String lower = name.toLowerCase(Locale.ROOT);
    switch (lower) {
      case "mana" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return ctx.engine().manaProvider().get(player);
        }
        return 0.0;
      }
      case "mana_max", "manamax" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return ctx.engine().manaProvider().getMax(player);
        }
        return 0.0;
      }
      case "caster_health", "casterhealth" -> {
        return ctx.caster().getHealth();
      }
      case "caster_max_health", "castermaxhealth" -> {
        AttributeInstance attribute = ctx.caster().getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
      }
      case "t" -> {
        return numericVar(vars(ctx, VarScope.CAST).get("t"), 0.0);
      }
      case "distance" -> {
        LivingEntity target = lastEntity(ctx);
        if (target == null) {
          return 0.0;
        }
        return target.getLocation().distance(ctx.origin());
      }
      default -> {
      }
    }

    if (lower.startsWith("var:")) {
      String[] parts = name.split(":", 3);
      if (parts.length == 2) {
        String key = parts[1].trim();
        if (key.isEmpty()) {
          return 0.0;
        }
        Object v = vars(ctx, VarScope.CAST).get(key);
        if (v == null) {
          v = vars(ctx, VarScope.PLAYER).get(key);
        }
        if (v == null) {
          v = vars(ctx, VarScope.ENTITY).get(key);
        }
        return numericVar(v, 0.0);
      }
      if (parts.length == 3) {
        VarScope scope = parseVarScope(parts[1].trim(), "expr", VarScope.CAST);
        String key = parts[2].trim();
        if (key.isEmpty()) {
          return 0.0;
        }
        Object v = vars(ctx, scope).get(key);
        return numericVar(v, 0.0);
      }
    }

    Object direct = vars(ctx, VarScope.CAST).get(name);
    if (direct != null) {
      return numericVar(direct, 0.0);
    }

    return 0.0;
  }

  private static final class ExprParser {
    private enum TokenType {
      NUMBER,
      IDENT,
      OP,
      LPAREN,
      RPAREN,
      COMMA,
      END
    }

    private record Token(TokenType type, String text, double number) {
    }

    private final String input;
    private final String path;
    private int pos;
    private Token lookahead;

    ExprParser(String input, String path) {
      this.input = Objects.requireNonNull(input, "input");
      this.path = Objects.requireNonNull(path, "path");
      this.lookahead = nextToken();
    }

    ExprNode parse() {
      ExprNode expr = parseExpression();
      if (lookahead.type != TokenType.END) {
        throw new IllegalArgumentException(path + ": unexpected token: " + lookahead.text);
      }
      return expr;
    }

    private ExprNode parseExpression() {
      ExprNode left = parseTerm();
      while (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parseTerm();
        left = binary(op, left, right);
      }
      return left;
    }

    private ExprNode parseTerm() {
      ExprNode left = parsePower();
      while (lookahead.type == TokenType.OP && ("*".equals(lookahead.text) || "/".equals(lookahead.text) || "%".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary(op, left, right);
      }
      return left;
    }

    private ExprNode parsePower() {
      ExprNode left = parseUnary();
      if (lookahead.type == TokenType.OP && "^".equals(lookahead.text)) {
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary("^", left, right);
      }
      return left;
    }

    private ExprNode parseUnary() {
      if (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode inner = parseUnary();
        return unary(op, inner);
      }
      return parsePrimary();
    }

    private ExprNode parsePrimary() {
      if (lookahead.type == TokenType.NUMBER) {
        double v = lookahead.number;
        consume(TokenType.NUMBER);
        return (ctx, vars) -> v;
      }
      if (lookahead.type == TokenType.IDENT) {
        String name = lookahead.text;
        consume(TokenType.IDENT);
        if (lookahead.type == TokenType.LPAREN) {
          consume(TokenType.LPAREN);
          List<ExprNode> args = new ArrayList<>();
          if (lookahead.type != TokenType.RPAREN) {
            args.add(parseExpression());
            while (lookahead.type == TokenType.COMMA) {
              consume(TokenType.COMMA);
              args.add(parseExpression());
            }
          }
          consume(TokenType.RPAREN);
          return function(name, args);
        }
        return (ctx, vars) -> vars.resolve(name, ctx);
      }
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        ExprNode expr = parseExpression();
        consume(TokenType.RPAREN);
        return expr;
      }
      throw new IllegalArgumentException(path + ": unexpected token: " + lookahead.text);
    }

    private Token nextToken() {
      int len = input.length();
      while (pos < len) {
        char c = input.charAt(pos);
        if (Character.isWhitespace(c)) {
          pos++;
          continue;
        }
        if (Character.isDigit(c) || c == '.') {
          int start = pos;
          pos++;
          while (pos < len) {
            char ch = input.charAt(pos);
            if (Character.isDigit(ch) || ch == '.') {
              pos++;
              continue;
            }
            break;
          }
          String text = input.substring(start, pos);
          try {
            double v = Double.parseDouble(text);
            return new Token(TokenType.NUMBER, text, v);
          } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(path + ": invalid number: " + text);
          }
        }
        if (Character.isLetter(c) || c == '_') {
          int start = pos;
          pos++;
          while (pos < len) {
            char ch = input.charAt(pos);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == ':' || ch == '.') {
              pos++;
              continue;
            }
            break;
          }
          String text = input.substring(start, pos);
          return new Token(TokenType.IDENT, text, 0.0);
        }
        pos++;
        switch (c) {
          case '+', '-', '*', '/', '%', '^' -> {
            return new Token(TokenType.OP, String.valueOf(c), 0.0);
          }
          case '(' -> {
            return new Token(TokenType.LPAREN, "(", 0.0);
          }
          case ')' -> {
            return new Token(TokenType.RPAREN, ")", 0.0);
          }
          case ',' -> {
            return new Token(TokenType.COMMA, ",", 0.0);
          }
          default -> throw new IllegalArgumentException(path + ": invalid character: " + c);
        }
      }
      return new Token(TokenType.END, "<end>", 0.0);
    }

    private void consume(TokenType type) {
      if (lookahead.type != type) {
        throw new IllegalArgumentException(path + ": expected " + type + " but got " + lookahead.text);
      }
      lookahead = nextToken();
    }

    private ExprNode unary(String op, ExprNode inner) {
      if ("-".equals(op)) {
        return (ctx, vars) -> -inner.eval(ctx, vars);
      }
      return inner;
    }

    private ExprNode binary(String op, ExprNode a, ExprNode b) {
      return switch (op) {
        case "+" -> (ctx, vars) -> a.eval(ctx, vars) + b.eval(ctx, vars);
        case "-" -> (ctx, vars) -> a.eval(ctx, vars) - b.eval(ctx, vars);
        case "*" -> (ctx, vars) -> a.eval(ctx, vars) * b.eval(ctx, vars);
        case "/" -> (ctx, vars) -> a.eval(ctx, vars) / b.eval(ctx, vars);
        case "%" -> (ctx, vars) -> a.eval(ctx, vars) % b.eval(ctx, vars);
        case "^" -> (ctx, vars) -> Math.pow(a.eval(ctx, vars), b.eval(ctx, vars));
        default -> throw new IllegalArgumentException(path + ": unknown operator: " + op);
      };
    }

    private ExprNode function(String name, List<ExprNode> args) {
      String fn = name.toLowerCase(Locale.ROOT);
      return switch (fn) {
        case "min" -> (ctx, vars) -> {
          if (args.size() < 2) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.min(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "max" -> (ctx, vars) -> {
          if (args.size() < 2) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.max(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "clamp" -> (ctx, vars) -> {
          if (args.size() != 3) {
            return 0.0;
          }
          double x = args.get(0).eval(ctx, vars);
          double lo = args.get(1).eval(ctx, vars);
          double hi = args.get(2).eval(ctx, vars);
          return Math.max(lo, Math.min(hi, x));
        };
        case "lerp" -> (ctx, vars) -> {
          if (args.size() != 3) {
            return 0.0;
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          double t = args.get(2).eval(ctx, vars);
          return a + (b - a) * t;
        };
        case "rand" -> (ctx, vars) -> {
          double r = ctx.rng().nextDouble();
          if (args.isEmpty()) {
            return r;
          }
          if (args.size() == 1) {
            return r * args.get(0).eval(ctx, vars);
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          return a + r * (b - a);
        };
        case "abs" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.abs(args.get(0).eval(ctx, vars));
        case "floor" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.floor(args.get(0).eval(ctx, vars));
        case "ceil" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.ceil(args.get(0).eval(ctx, vars));
        case "round" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.round(args.get(0).eval(ctx, vars));
        default -> (ctx, vars) -> 0.0;
      };
    }
  }

  private static double numericVar(Object raw, double def) {
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (Exception ignored) {
      return def;
    }
  }

  private static java.util.function.DoubleUnaryOperator easing(Map<String, Object> node, String key, EasingId def) {
    String raw = string(node, key, def.name());
    EasingId id;
    try {
      id = EasingId.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      id = def;
    }
    return switch (id) {
      case LINEAR -> Easings::linear;
      case IN_OUT_CUBIC -> Easings::inOutCubic;
      case OUT_QUAD -> Easings::outQuad;
    };
  }

  private record PointSpec(NumValue forward, NumValue right, NumValue up, NumValue x, NumValue y, NumValue z) {
  }

  private PointSpec pointSpec(Object raw, String path) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object with offsets (forward/right/up or x/y/z)");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> node = (Map<String, Object>) map;
    NumValue forward = numValue(node, "forward", 0.0, path);
    NumValue right = numValue(node, "right", 0.0, path);
    NumValue up = numValue(node, "up", 0.0, path);
    NumValue x = numValue(node, "x", 0.0, path);
    NumValue y = numValue(node, "y", 0.0, path);
    NumValue z = numValue(node, "z", 0.0, path);
    return new PointSpec(forward, right, up, x, y, z);
  }

  private static Location pointAt(CastContext ctx, PointSpec spec) {
    Vector forward = ctx.direction().clone();
    if (forward.lengthSquared() < 1e-9) {
      forward.setX(0).setY(0).setZ(1);
    }
    forward.normalize();

    Vector up = new Vector(0, 1, 0);
    Vector right = forward.clone().crossProduct(up);
    if (right.lengthSquared() < 1e-9) {
      right = new Vector(1, 0, 0);
    } else {
      right.normalize();
    }

    Location out = ctx.origin().clone();
    out.add(forward.clone().multiply(evalDouble(spec.forward(), ctx)));
    out.add(right.clone().multiply(evalDouble(spec.right(), ctx)));
    out.add(0, evalDouble(spec.up(), ctx), 0);
    out.add(evalDouble(spec.x(), ctx), evalDouble(spec.y(), ctx), evalDouble(spec.z(), ctx));
    return out;
  }

  private enum AtMode {
    ORIGIN,
    LAST_HIT,
    LAST_ENTITY
  }

  private static AtMode parseAt(String raw, String path) {
    String s = (raw == null ? "origin" : raw).trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "origin" -> AtMode.ORIGIN;
      case "last_hit", "last-hit" -> AtMode.LAST_HIT;
      case "last_entity", "last-entity" -> AtMode.LAST_ENTITY;
      default -> throw new IllegalArgumentException(path + ": invalid at=" + raw + " (use origin|last_hit|last_entity)");
    };
  }

  private static Location resolveAt(CastContext ctx, AtMode mode) {
    return switch (mode) {
      case ORIGIN -> ctx.origin();
      case LAST_ENTITY -> {
        Object v = ctx.state().get(YAML_LAST_ENTITY);
        if (v instanceof LivingEntity living) {
          yield living.getLocation();
        }
        yield ctx.origin();
      }
      case LAST_HIT -> {
        Object hit = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
        if (hit instanceof dev.patric.dungeonsreborn.effects.projectile.ProjectileHit ph) {
          yield ph.location();
        }
        yield ctx.origin();
      }
    };
  }

  private static LivingEntity lastEntity(CastContext ctx) {
    Object v = ctx.state().get(YAML_LAST_ENTITY);
    if (v instanceof LivingEntity living) {
      return living;
    }
    Object hit = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
    if (hit instanceof dev.patric.dungeonsreborn.effects.projectile.ProjectileHit ph && ph.hitEntity() != null) {
      return ph.hitEntity();
    }
    return null;
  }

  private static PotionEffectType potionEffectValue(Map<String, Object> node, String key, String path) {
    String raw = requireString(node, key, path);
    String s = raw.trim();
    NamespacedKey ns;
    if (s.contains(":")) {
      ns = NamespacedKey.fromString(s);
    } else {
      ns = NamespacedKey.fromString("minecraft:" + s.toLowerCase(Locale.ROOT));
    }
    if (ns == null) {
      throw new IllegalArgumentException(path + ": invalid effect key: " + raw);
    }
    PotionEffectType type = Registry.EFFECT.get(ns);
    if (type == null) {
      throw new IllegalArgumentException(path + ": unknown effect: " + raw);
    }
    return type;
  }

  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private static Component richText(String raw) {
    if (raw == null) {
      return Component.empty();
    }
    // If user explicitly uses legacy codes, honor them.
    if (raw.indexOf('§') >= 0) {
      return LEGACY.deserialize(raw);
    }
    try {
      return MINI.deserialize(raw);
    } catch (Exception ignored) {
      return LEGACY.deserialize(raw.replace('&', '§'));
    }
  }

  private Component renderText(String raw, CastContext ctx) {
    return richText(interpolate(raw, ctx));
  }

  private String interpolate(String raw, CastContext ctx) {
    if (raw == null || raw.indexOf('{') < 0) {
      return raw;
    }
    StringBuilder out = new StringBuilder(raw.length() + 16);
    int i = 0;
    while (i < raw.length()) {
      int open = raw.indexOf('{', i);
      if (open < 0) {
        out.append(raw, i, raw.length());
        break;
      }
      int close = raw.indexOf('}', open + 1);
      if (close < 0) {
        out.append(raw, i, raw.length());
        break;
      }
      out.append(raw, i, open);
      String token = raw.substring(open + 1, close).trim();
      out.append(resolveToken(token, ctx, raw.substring(open, close + 1)));
      i = close + 1;
    }
    return out.toString();
  }

  private String resolveToken(String token, CastContext ctx, String fallback) {
    if (token.isBlank()) {
      return fallback;
    }
    String lower = token.toLowerCase(Locale.ROOT);
    switch (lower) {
      case "ability", "abilityid" -> {
        return ctx.abilityId();
      }
      case "cast", "castid" -> {
        return ctx.castId().toString();
      }
      case "tick" -> {
        return String.valueOf(ctx.engine().tickNow());
      }
      case "player", "caster" -> {
        return ctx.caster().getName();
      }
      case "target" -> {
        LivingEntity target = lastEntity(ctx);
        return target == null ? "" : target.getName();
      }
      case "target_type", "targettype" -> {
        LivingEntity target = lastEntity(ctx);
        return target == null ? "" : target.getType().name();
      }
      case "mana" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return String.valueOf((int) Math.floor(ctx.engine().manaProvider().get(player)));
        }
        return "";
      }
      case "mana_max", "manamax" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return String.valueOf((int) Math.floor(ctx.engine().manaProvider().getMax(player)));
        }
        return "";
      }
      default -> {
      }
    }

    if (lower.startsWith("var:")) {
      String[] parts = token.split(":", 3);
      if (parts.length == 2) {
        String key = parts[1].trim();
        if (key.isBlank()) {
          return fallback;
        }
        Object v = vars(ctx, VarScope.CAST).get(key);
        if (v == null) {
          v = vars(ctx, VarScope.PLAYER).get(key);
        }
        return v == null ? "" : String.valueOf(v);
      }
      if (parts.length == 3) {
        VarScope scope = parseVarScope(parts[1].trim(), "token", VarScope.CAST);
        String key = parts[2].trim();
        if (key.isBlank()) {
          return fallback;
        }
        Object v = vars(ctx, scope).get(key);
        return v == null ? "" : String.valueOf(v);
      }
    }

    return fallback;
  }

  private static Object require(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      throw new IllegalArgumentException(missingKeyMessage(node, key, path));
    }
    return v;
  }

  private static Map<String, Object> castMap(Object raw, String path) {
    if (raw instanceof ConfigurationSection sec) {
      return normalizeMap(sec.getValues(false));
    }
    if (raw instanceof Map<?, ?> map) {
      return normalizeMap(map);
    }
    throw new IllegalArgumentException(path + ": expected object");
  }

  private static List<?> mapList(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      return List.of();
    }
    if (!(v instanceof List<?> list)) {
      throw new IllegalArgumentException(path + ": expected list");
    }
    for (Object o : list) {
      if (!(o instanceof Map<?, ?>) && !(o instanceof ConfigurationSection)) {
        throw new IllegalArgumentException(path + ": list elements must be objects");
      }
    }
    return list;
  }

  private static Map<String, Object> normalizeMap(Map<?, ?> raw) {
    java.util.HashMap<String, Object> out = new java.util.HashMap<>();
    for (var e : raw.entrySet()) {
      String key = String.valueOf(e.getKey());
      out.put(key, normalizeValue(e.getValue()));
    }
    return out;
  }

  private static Object normalizeValue(Object v) {
    if (v instanceof ConfigurationSection sec) {
      return normalizeMap(sec.getValues(false));
    }
    if (v instanceof Map<?, ?> map) {
      return normalizeMap(map);
    }
    if (v instanceof List<?> list) {
      ArrayList<Object> out = new ArrayList<>(list.size());
      for (Object o : list) {
        out.add(normalizeValue(o));
      }
      return out;
    }
    return v;
  }

  private static String missingKeyMessage(Map<String, Object> node, String key, String path) {
    StringBuilder msg = new StringBuilder(path).append(": missing ").append(key);
    String suggestion = suggestClosest(key, node.keySet());
    if (suggestion != null) {
      msg.append(" (did you mean ").append(suggestion).append("?)");
    } else if (!node.isEmpty()) {
      msg.append(" (available: ").append(formatKeys(node.keySet(), 8)).append(")");
    }
    return msg.toString();
  }

  private static String formatKeys(java.util.Set<String> keys, int limit) {
    if (keys.isEmpty()) {
      return "";
    }
    int count = 0;
    StringBuilder out = new StringBuilder();
    for (String k : keys) {
      if (count++ >= limit) {
        out.append(", ...");
        break;
      }
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(k);
    }
    return out.toString();
  }

  private static <E extends Enum<E>> String suggestEnumValue(String raw, Class<E> enumType) {
    if (raw == null) {
      return null;
    }
    java.util.List<String> options = new java.util.ArrayList<>();
    for (E e : enumType.getEnumConstants()) {
      options.add(e.name());
    }
    return suggestClosest(raw, options);
  }

  private static String suggestClosest(String input, java.util.Collection<String> options) {
    String in = normalizeToken(input);
    if (in.isEmpty() || options.isEmpty()) {
      return null;
    }
    String best = null;
    int bestDist = Integer.MAX_VALUE;
    for (String opt : options) {
      if (opt == null) {
        continue;
      }
      String norm = normalizeToken(opt);
      if (norm.isEmpty()) {
        continue;
      }
      int dist = editDistance(in, norm);
      if (norm.startsWith(in) || in.startsWith(norm) || norm.contains(in)) {
        dist = Math.min(dist, 1);
      }
      if (dist < bestDist) {
        bestDist = dist;
        best = opt;
      }
    }
    int maxDist = Math.max(1, Math.min(4, in.length() / 3 + 1));
    return bestDist <= maxDist ? best : null;
  }

  private static String normalizeToken(String raw) {
    if (raw == null) {
      return "";
    }
    String s = raw.trim().toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static int editDistance(String a, String b) {
    if (a.equals(b)) {
      return 0;
    }
    int alen = a.length();
    int blen = b.length();
    if (alen == 0) {
      return blen;
    }
    if (blen == 0) {
      return alen;
    }
    int[] prev = new int[blen + 1];
    int[] curr = new int[blen + 1];
    for (int j = 0; j <= blen; j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= alen; i++) {
      curr[0] = i;
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= blen; j++) {
        int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
        int insert = curr[j - 1] + 1;
        int delete = prev[j] + 1;
        int replace = prev[j - 1] + cost;
        curr[j] = Math.min(insert, Math.min(delete, replace));
      }
      int[] tmp = prev;
      prev = curr;
      curr = tmp;
    }
    return prev[blen];
  }

  private static String requireString(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      throw new IllegalArgumentException(missingKeyMessage(node, key, path));
    }
    String s = String.valueOf(v);
    if (s.isBlank()) {
      throw new IllegalArgumentException(path + ": " + key + " is blank");
    }
    return s;
  }

  private static String string(Map<String, Object> node, String key, String def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    return String.valueOf(v);
  }

  private static boolean bool(Map<String, Object> node, String key, boolean def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }

  private static int intValue(Map<String, Object> node, String key, int def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  private static long longValue(Map<String, Object> node, String key, long def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  private static double doubleValue(Map<String, Object> node, String key, double def) {
    Object v = node.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  private static double requireDouble(Map<String, Object> node, String key, String path) {
    Object v = node.get(key);
    if (v == null) {
      throw new IllegalArgumentException(missingKeyMessage(node, key, path));
    }
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(v));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": " + key + " must be a number");
    }
  }

  private static Object parseParticleData(Map<String, Object> node, Particle particle, String path) {
    if (!node.containsKey("data")) {
      if (particleRequiresData(particle)) {
        throw new IllegalArgumentException(path + ".data: required for " + particle.name());
      }
      return null;
    }
    return parseParticleDataValue(node.get("data"), particle, path + ".data");
  }

  private static boolean particleRequiresData(Particle particle) {
    return switch (particle) {
      case DUST, DUST_COLOR_TRANSITION,
           ITEM,
           BLOCK, FALLING_DUST, DUST_PILLAR, BLOCK_CRUMBLE, BLOCK_MARKER,
           VIBRATION, TRAIL -> true;
      default -> false;
    };
  }

  private static Object resolveParticleData(Object data, CastContext ctx, Location loc) {
    if (data instanceof java.util.function.BiFunction<?, ?, ?> fn) {
      @SuppressWarnings("unchecked")
      java.util.function.BiFunction<CastContext, Location, Object> resolver =
          (java.util.function.BiFunction<CastContext, Location, Object>) fn;
      return resolver.apply(ctx, loc);
    }
    return data;
  }

  private static Object parseParticleDataValue(Object raw, Particle particle, String path) {
    return switch (particle) {
      case DUST -> parseDustOptions(raw, path);
      case DUST_COLOR_TRANSITION -> parseDustTransition(raw, path);
      case ENTITY_EFFECT, FLASH, TINTED_LEAVES -> parseColor(raw, path);
      case DRAGON_BREATH, SCULK_CHARGE -> parseFloatData(raw, path);
      case SHRIEK -> parseIntData(raw, path);
      case ITEM -> parseParticleItem(raw, path);
      case BLOCK, FALLING_DUST, DUST_PILLAR, BLOCK_CRUMBLE, BLOCK_MARKER -> parseBlockData(raw, path);
      case VIBRATION -> parseVibrationData(raw, path);
      case TRAIL -> parseTrailData(raw, path);
      case EFFECT, INSTANT_EFFECT -> throw new IllegalArgumentException(
          path + ": particle " + particle.name() + " uses Particle.Spell, which is not available in Paper 1.21.8");
      default -> throw new IllegalArgumentException(path + ": particle " + particle.name() + " does not support data");
    };
  }

  private static ItemStack parseParticleItem(Object raw, String path) {
    if (raw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseParticleItem(sec.getValues(false), path);
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = normalizeMap(map);
      Object nested = pick(node, "item", "stack", "value");
      if (nested != null && nested != raw) {
        return parseParticleItem(nested, path + ".item");
      }
      Map<String, Object> copy = new java.util.HashMap<>(node);
      if (!copy.containsKey("type") && copy.containsKey("material")) {
        copy.put("type", copy.get("material"));
      }
      if (copy.containsKey("type")) {
        try {
          return ItemStack.deserialize(copy);
        } catch (IllegalArgumentException ignored) {
          Material material = materialValue(copy.get("type"), path + ".type");
          int amount = Math.max(1, intValue(copy, "amount", 1));
          return new ItemStack(material, amount);
        }
      }
      if (copy.containsKey("material")) {
        Material material = materialValue(copy.get("material"), path + ".material");
        int amount = Math.max(1, intValue(copy, "amount", 1));
        return new ItemStack(material, amount);
      }
    }
    if (raw instanceof String s) {
      Material material = materialValue(s, path);
      return new ItemStack(material);
    }
    throw new IllegalArgumentException(path + ": expected itemstack or material");
  }

  private static BlockData parseBlockData(Object raw, String path) {
    if (raw instanceof BlockData blockData) {
      return blockData;
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseBlockData(sec.getValues(false), path);
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = normalizeMap(map);
      Object nested = pick(node, "data", "blockData", "block", "value");
      if (nested != null && nested != raw) {
        return parseBlockData(nested, path + ".data");
      }
      Object materialRaw = pick(node, "material", "type");
      if (materialRaw != null) {
        Material material = materialValue(materialRaw, path + ".material");
        if (!material.isBlock()) {
          throw new IllegalArgumentException(path + ": material is not a block: " + material);
        }
        Object statesRaw = node.get("states");
        if (statesRaw instanceof Map<?, ?> states) {
          return parseBlockDataFromStates(material, normalizeMap(states), path + ".states");
        }
        return material.createBlockData();
      }
    }
    if (raw instanceof Material material) {
      if (!material.isBlock()) {
        throw new IllegalArgumentException(path + ": material is not a block: " + material);
      }
      return material.createBlockData();
    }
    if (raw instanceof String s) {
      return parseBlockDataString(s, path);
    }
    throw new IllegalArgumentException(path + ": expected block data");
  }

  private static BlockData parseBlockDataFromStates(Material material, Map<String, Object> states, String path) {
    if (states.isEmpty()) {
      return material.createBlockData();
    }
    StringBuilder out = new StringBuilder(material.getKey().toString()).append('[');
    int index = 0;
    for (Map.Entry<String, Object> entry : states.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new IllegalArgumentException(path + ": state keys/values cannot be null");
      }
      if (index++ > 0) {
        out.append(',');
      }
      out.append(entry.getKey()).append('=').append(String.valueOf(entry.getValue()));
    }
    out.append(']');
    try {
      return Bukkit.createBlockData(out.toString());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(path + ": invalid block states for " + material, ex);
    }
  }

  private static BlockData parseBlockDataString(String value, String path) {
    String trimmed = value.trim();
    if (trimmed.isBlank()) {
      throw new IllegalArgumentException(path + ": block data is blank");
    }
    try {
      return Bukkit.createBlockData(trimmed);
    } catch (IllegalArgumentException ex) {
      Material material = materialValue(trimmed, path);
      if (!material.isBlock()) {
        throw new IllegalArgumentException(path + ": material is not a block: " + trimmed);
      }
      return material.createBlockData();
    }
  }

  private static java.util.function.BiFunction<CastContext, Location, Object> parseVibrationData(Object raw, String path) {
    Map<String, Object> data = castMap(raw, path);
    Object arrivalRaw = pick(data, "arrivalTicks", "arrival", "ticks");
    int arrivalTicks = arrivalRaw == null ? 20 : parseInt(arrivalRaw, path + ".arrivalTicks");
    String destRaw = pick(data, "destinationAt", "destination", "dest", "at") instanceof Object v ? String.valueOf(v) : "origin";
    AtMode at = parseAt(destRaw, path + ".destinationAt");
    boolean preferEntity = bool(data, "preferEntity", true);
    if (arrivalTicks < 0) {
      throw new IllegalArgumentException(path + ".arrivalTicks: must be >= 0");
    }
    return (ctx, loc) -> {
      Vibration.Destination destination;
      if (preferEntity && at == AtMode.LAST_ENTITY) {
        LivingEntity entity = lastEntity(ctx);
        if (entity != null) {
          destination = new Vibration.Destination.EntityDestination(entity);
          return new Vibration(destination, arrivalTicks);
        }
      }
      Location target = resolveAtWithEntity(ctx, at);
      if (target == null || target.getWorld() == null) {
        target = loc == null ? ctx.origin() : loc;
      }
      destination = new Vibration.Destination.BlockDestination(target);
      return new Vibration(destination, arrivalTicks);
    };
  }

  private static java.util.function.BiFunction<CastContext, Location, Object> parseTrailData(Object raw, String path) {
    Map<String, Object> data = castMap(raw, path);
    Object colorRaw = pick(data, "color", "colour");
    Color color = parseColor(colorRaw != null ? colorRaw : data, path + ".color");
    int duration = intValue(data, "durationTicks", intValue(data, "duration", 20));
    if (duration <= 0) {
      throw new IllegalArgumentException(path + ".durationTicks: must be > 0");
    }
    String targetRaw = pick(data, "targetAt", "destinationAt", "target", "at") instanceof Object v ? String.valueOf(v) : "origin";
    AtMode at = parseAt(targetRaw, path + ".targetAt");
    return (ctx, loc) -> {
      Location target = resolveAtWithEntity(ctx, at);
      if (target == null || target.getWorld() == null) {
        target = loc == null ? ctx.origin() : loc;
      }
      return new Particle.Trail(target, color, duration);
    };
  }

  private static Location resolveAtWithEntity(CastContext ctx, AtMode mode) {
    if (mode == AtMode.LAST_ENTITY) {
      LivingEntity entity = lastEntity(ctx);
      if (entity != null) {
        return entity.getLocation();
      }
    }
    return resolveAt(ctx, mode);
  }

  private static Material materialValue(Object raw, String path) {
    if (raw instanceof Material material) {
      return material;
    }
    if (raw == null) {
      throw new IllegalArgumentException(path + ": missing material");
    }
    String name = String.valueOf(raw).trim();
    if (name.isBlank()) {
      throw new IllegalArgumentException(path + ": material is blank");
    }
    Material material = Material.matchMaterial(name);
    if (material == null) {
      material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }
    if (material == null) {
      throw new IllegalArgumentException(path + ": unknown material: " + raw);
    }
    return material;
  }

  private static Particle.DustOptions parseDustOptions(Object raw, String path) {
    Map<String, Object> data = castMap(raw, path);
    Object colorRaw = pick(data, "color", "colour");
    Color color = parseColor(colorRaw != null ? colorRaw : data, path + ".color");
    double size = doubleValue(data, "size", 1.0);
    if (!Double.isFinite(size) || size <= 0.0) {
      throw new IllegalArgumentException(path + ".size: must be > 0");
    }
    return new Particle.DustOptions(color, (float) size);
  }

  private static Particle.DustTransition parseDustTransition(Object raw, String path) {
    Map<String, Object> data = castMap(raw, path);
    Object fromRaw = pick(data, "color", "from", "fromColor", "from_colour");
    Object toRaw = pick(data, "toColor", "colorTo", "to", "end", "endColor", "to_colour");
    if (fromRaw == null) {
      fromRaw = data;
    }
    if (toRaw == null) {
      throw new IllegalArgumentException(path + ".toColor: missing target color");
    }
    Color from = parseColor(fromRaw, path + ".color");
    Color to = parseColor(toRaw, path + ".toColor");
    double size = doubleValue(data, "size", 1.0);
    if (!Double.isFinite(size) || size <= 0.0) {
      throw new IllegalArgumentException(path + ".size: must be > 0");
    }
    return new Particle.DustTransition(from, to, (float) size);
  }

  private static Color parseColor(Object raw, String path) {
    if (raw == null) {
      throw new IllegalArgumentException(path + ": missing color");
    }
    if (raw instanceof Color color) {
      return color;
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = normalizeMap(map);
      Object nested = pick(node, "color", "colour", "hex", "value");
      if (nested != null && nested != raw) {
        return parseColor(nested, path);
      }
      if (hasColorKeys(node)) {
        int r = parseColorComponent(pick(node, "r", "red"), path + ".r");
        int g = parseColorComponent(pick(node, "g", "green"), path + ".g");
        int b = parseColorComponent(pick(node, "b", "blue"), path + ".b");
        return Color.fromRGB(r, g, b);
      }
    }
    if (raw instanceof List<?> list) {
      if (list.size() < 3) {
        throw new IllegalArgumentException(path + ": expected 3 color components");
      }
      int r = parseColorComponent(list.get(0), path + ".r");
      int g = parseColorComponent(list.get(1), path + ".g");
      int b = parseColorComponent(list.get(2), path + ".b");
      return Color.fromRGB(r, g, b);
    }
    if (raw instanceof Number n) {
      return Color.fromRGB(n.intValue() & 0xFFFFFF);
    }
    if (raw instanceof String s) {
      String trimmed = s.trim();
      if (trimmed.isBlank()) {
        throw new IllegalArgumentException(path + ": color is blank");
      }
      String hex = trimmed;
      if (hex.startsWith("#")) {
        hex = hex.substring(1);
      } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
        hex = hex.substring(2);
      }
      if (hex.matches("[0-9a-fA-F]{6}")) {
        return Color.fromRGB(Integer.parseInt(hex, 16));
      }
      String[] parts = trimmed.split("[,\\s]+");
      if (parts.length >= 3) {
        int r = parseColorComponent(parts[0], path + ".r");
        int g = parseColorComponent(parts[1], path + ".g");
        int b = parseColorComponent(parts[2], path + ".b");
        return Color.fromRGB(r, g, b);
      }
    }
    throw new IllegalArgumentException(path + ": invalid color value");
  }

  private static boolean hasColorKeys(Map<String, Object> node) {
    return node.containsKey("r") || node.containsKey("red")
        || node.containsKey("g") || node.containsKey("green")
        || node.containsKey("b") || node.containsKey("blue");
  }

  private static int parseColorComponent(Object raw, String path) {
    if (raw == null) {
      throw new IllegalArgumentException(path + ": missing color component");
    }
    int value = parseInt(raw, path);
    if (value < 0 || value > 255) {
      throw new IllegalArgumentException(path + ": must be in [0, 255]");
    }
    return value;
  }

  private static Float parseFloatData(Object raw, String path) {
    Object value = raw;
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = normalizeMap(map);
      value = pick(node, "value", "data", "scale");
      if (value == null) {
        throw new IllegalArgumentException(path + ": missing value");
      }
    }
    double d = parseDouble(value, path);
    if (!Double.isFinite(d)) {
      throw new IllegalArgumentException(path + ": value must be finite");
    }
    return (float) d;
  }

  private static Integer parseIntData(Object raw, String path) {
    Object value = raw;
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = normalizeMap(map);
      value = pick(node, "value", "data");
      if (value == null) {
        throw new IllegalArgumentException(path + ": missing value");
      }
    }
    return parseInt(value, path);
  }

  private static double parseDouble(Object raw, String path) {
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected number");
    }
  }

  private static int parseInt(Object raw, String path) {
    if (raw instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected integer");
    }
  }

  private static Object pick(Map<String, Object> node, String... keys) {
    for (String key : keys) {
      if (node.containsKey(key)) {
        return node.get(key);
      }
    }
    return null;
  }

  private static Map<DamageType, Double> parseResistanceMap(Object raw, String path) {
    if (raw == null) {
      return Map.of();
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseResistanceMap(sec.getValues(false), path);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object");
    }
    Map<DamageType, Double> out = new java.util.EnumMap<>(DamageType.class);
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = String.valueOf(entry.getKey());
      DamageType type = parseDamageTypeKey(key, path + "." + key);
      double value = doubleFrom(entry.getValue(), path + "." + key);
      out.put(type, value);
    }
    return out;
  }

  private static MinionScaling parseMinionScaling(Object raw, String path) {
    if (raw == null) {
      return MinionScaling.NONE;
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseMinionScaling(sec.getValues(false), path);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object");
    }
    Map<String, Object> scale = normalizeMap(map);
    double healthPerLevel = doubleValue(scale, "healthPerLevel", 0.0);
    double damagePerLevel = doubleValue(scale, "damagePerLevel", 0.0);
    double healthPerMaxHealth = doubleValue(scale, "healthPerMaxHealth", 0.0);
    double damagePerMaxHealth = doubleValue(scale, "damagePerMaxHealth", 0.0);
    double healthPerManaMax = doubleValue(scale, "healthPerManaMax", 0.0);
    double damagePerManaMax = doubleValue(scale, "damagePerManaMax", 0.0);
    return new MinionScaling(healthPerLevel, damagePerLevel, healthPerMaxHealth, damagePerMaxHealth, healthPerManaMax, damagePerManaMax);
  }

  private static MinionMode parseMinionMode(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MinionMode.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(raw, MinionMode.class);
      String msg = path + ": invalid mode=" + raw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static java.util.List<MinionPassiveSpec> parseMinionPassives(Object raw, String path) {
    if (raw == null) {
      return java.util.List.of();
    }
    if (!(raw instanceof Iterable<?> list)) {
      throw new IllegalArgumentException(path + ": expected list");
    }
    java.util.List<MinionPassiveSpec> out = new java.util.ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String base = path + "[" + index + "]";
      if (entry instanceof String abilityId) {
        if (abilityId.isBlank()) {
          throw new IllegalArgumentException(base + ": ability is blank");
        }
        out.add(new MinionPassiveSpec(Ids.normalize(abilityId), 40L));
      } else if (entry instanceof Map<?, ?> map) {
        Map<String, Object> node = castMap(map, base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long period = longValue(node, "periodTicks", 40L);
        if (period <= 0) {
          throw new IllegalArgumentException(base + ".periodTicks: must be > 0");
        }
        out.add(new MinionPassiveSpec(Ids.normalize(abilityId), period));
      } else if (entry instanceof ConfigurationSection sec) {
        Map<String, Object> node = castMap(sec.getValues(false), base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long period = longValue(node, "periodTicks", 40L);
        if (period <= 0) {
          throw new IllegalArgumentException(base + ".periodTicks: must be > 0");
        }
        out.add(new MinionPassiveSpec(Ids.normalize(abilityId), period));
      } else {
        throw new IllegalArgumentException(base + ": expected string or object");
      }
      index++;
    }
    return java.util.List.copyOf(out);
  }

  private static java.util.List<MinionSpecialAttackSpec> parseMinionSpecialAttacks(Object raw, String path) {
    if (raw == null) {
      return java.util.List.of();
    }
    if (!(raw instanceof Iterable<?> list)) {
      throw new IllegalArgumentException(path + ": expected list");
    }
    java.util.List<MinionSpecialAttackSpec> out = new java.util.ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String base = path + "[" + index + "]";
      if (entry instanceof String abilityId) {
        if (abilityId.isBlank()) {
          throw new IllegalArgumentException(base + ": ability is blank");
        }
        out.add(new MinionSpecialAttackSpec(Ids.normalize(abilityId), 60L, 1.0, true));
      } else if (entry instanceof Map<?, ?> map) {
        Map<String, Object> node = castMap(map, base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long cooldown = longValue(node, "cooldownTicks", 60L);
        double chance = doubleValue(node, "chance", 1.0);
        boolean requireTarget = bool(node, "requireTarget", true);
        if (cooldown <= 0) {
          throw new IllegalArgumentException(base + ".cooldownTicks: must be > 0");
        }
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
          throw new IllegalArgumentException(base + ".chance: must be in [0,1]");
        }
        out.add(new MinionSpecialAttackSpec(Ids.normalize(abilityId), cooldown, chance, requireTarget));
      } else if (entry instanceof ConfigurationSection sec) {
        Map<String, Object> node = castMap(sec.getValues(false), base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long cooldown = longValue(node, "cooldownTicks", 60L);
        double chance = doubleValue(node, "chance", 1.0);
        boolean requireTarget = bool(node, "requireTarget", true);
        if (cooldown <= 0) {
          throw new IllegalArgumentException(base + ".cooldownTicks: must be > 0");
        }
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
          throw new IllegalArgumentException(base + ".chance: must be in [0,1]");
        }
        out.add(new MinionSpecialAttackSpec(Ids.normalize(abilityId), cooldown, chance, requireTarget));
      } else {
        throw new IllegalArgumentException(base + ": expected string or object");
      }
      index++;
    }
    return java.util.List.copyOf(out);
  }

  private static java.util.Set<DamageType> parseDamageTypeSet(Object raw, String path) {
    if (raw == null) {
      return java.util.Set.of();
    }
    java.util.EnumSet<DamageType> out = java.util.EnumSet.noneOf(DamageType.class);
    if (raw instanceof Iterable<?> list) {
      for (Object item : list) {
        if (item == null) {
          continue;
        }
        String key = String.valueOf(item);
        out.add(parseDamageTypeKey(key, path));
      }
      return java.util.Set.copyOf(out);
    }
    String text = String.valueOf(raw);
    if (text.indexOf(',') >= 0) {
      for (String part : text.split(",")) {
        String key = part.trim();
        if (!key.isEmpty()) {
          out.add(parseDamageTypeKey(key, path));
        }
      }
      return java.util.Set.copyOf(out);
    }
    out.add(parseDamageTypeKey(text, path));
    return java.util.Set.copyOf(out);
  }

  private static double doubleFrom(Object raw, String path) {
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected number");
    }
  }

  private static DamageType parseDamageTypeKey(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": damage type is blank");
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return DamageType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(raw, DamageType.class);
      String msg = path + ": invalid damage type=" + raw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static <E extends Enum<E>> E enumValue(Map<String, Object> node, String key, Class<E> enumType, String path) {
    String raw = requireString(node, key, path);
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return Enum.valueOf(enumType, normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(raw, enumType);
      String msg = path + ": invalid " + key + "=" + raw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static boolean hasDamageType(Map<String, Object> node) {
    return node.containsKey("damageType")
        || node.containsKey("damage_type")
        || node.containsKey("dmgType")
        || node.containsKey("dmg_type");
  }

  private static DamageType damageTypeValue(Map<String, Object> node, String path) {
    String key = null;
    Object raw = null;
    if (node.containsKey("damageType")) {
      key = "damageType";
      raw = node.get(key);
    } else if (node.containsKey("damage_type")) {
      key = "damage_type";
      raw = node.get(key);
    } else if (node.containsKey("dmgType")) {
      key = "dmgType";
      raw = node.get(key);
    } else if (node.containsKey("dmg_type")) {
      key = "dmg_type";
      raw = node.get(key);
    }
    if (raw == null) {
      throw new IllegalArgumentException(missingKeyMessage(node, "damageType", path));
    }
    String value = String.valueOf(raw);
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    try {
      return DamageType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(value, DamageType.class);
      String msg = path + ": invalid " + key + "=" + value;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static Sound soundValue(Map<String, Object> node, String key, String path) {
    String raw = requireString(node, key, path);
    String s = raw.trim();

    NamespacedKey ns;
    if (s.contains(":")) {
      ns = NamespacedKey.fromString(s);
    } else {
      // Accept legacy-ish enum-like values (e.g. ENTITY_BLAZE_SHOOT) by converting to a minecraft key.
      String k = s.toLowerCase(Locale.ROOT).replace('_', '.');
      ns = NamespacedKey.fromString("minecraft:" + k);
    }
    if (ns == null) {
      throw new IllegalArgumentException(path + ": invalid sound key: " + raw);
    }
    Sound sound = Registry.SOUNDS.get(ns);
    if (sound == null) {
      throw new IllegalArgumentException(path + ": unknown sound: " + raw);
    }
    return sound;
  }

  private static boolean isBindingError(String message) {
    if (message == null) {
      return false;
    }
    String lower = message.toLowerCase(Locale.ROOT);
    return lower.contains("bindings")
        || lower.contains("effects/items")
        || lower.contains("/items/")
        || lower.contains("\\items\\");
  }
}
