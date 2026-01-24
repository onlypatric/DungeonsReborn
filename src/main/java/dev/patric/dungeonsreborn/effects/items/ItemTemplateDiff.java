package dev.patric.dungeonsreborn.effects.items;

import dev.patric.dungeonsreborn.effects.Ids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ItemTemplateDiff {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final double STAT_EPS = 1e-6;

  private ItemTemplateDiff() {
  }

  public static List<String> diff(ItemTemplateSnapshot template, ItemStack item) {
    List<String> out = new ArrayList<>();
    if (template == null) {
      out.add("template: missing");
      return out;
    }
    if (item == null || item.getType().isAir()) {
      out.add("item: missing");
      return out;
    }
    ItemStack base = template.baseItem();
    if (base == null) {
      out.add("template: base item missing");
      return out;
    }
    if (item.getType() != base.getType()) {
      out.add("material: expected=" + base.getType() + " got=" + item.getType());
    }
    ItemMeta baseMeta = base.getItemMeta();
    ItemMeta itemMeta = item.getItemMeta();
    if (!Objects.equals(plainName(baseMeta), plainName(itemMeta))) {
      out.add("name: expected=\"" + plainName(baseMeta) + "\" got=\"" + plainName(itemMeta) + "\"");
    }
    List<String> baseLore = plainLore(baseMeta);
    List<String> itemLore = plainLore(itemMeta);
    if (!Objects.equals(baseLore, itemLore)) {
      out.add("lore: expected=" + baseLore.size() + " lines got=" + itemLore.size());
    }
    boolean baseHasCmd = baseMeta != null && baseMeta.hasCustomModelDataComponent();
    boolean itemHasCmd = itemMeta != null && itemMeta.hasCustomModelDataComponent();
    if (baseHasCmd != itemHasCmd) {
      out.add("custom_model_data: expected=" + baseHasCmd + " got=" + itemHasCmd);
    } else if (baseHasCmd) {
      Float baseCmd = customModelDataValue(baseMeta);
      Float itemCmd = customModelDataValue(itemMeta);
      if (!Objects.equals(baseCmd, itemCmd)) {
        out.add("custom_model_data: expected=" + baseCmd + " got=" + itemCmd);
      }
    }
    if (baseMeta != null && itemMeta != null && baseMeta.isUnbreakable() != itemMeta.isUnbreakable()) {
      out.add("unbreakable: expected=" + baseMeta.isUnbreakable() + " got=" + itemMeta.isUnbreakable());
    }
    String expectedId = Ids.normalize(template.id());
    String actualId = ItemMarkers.getItemId(item);
    if (!Objects.equals(expectedId, actualId)) {
      out.add("item_id: expected=" + expectedId + " got=" + actualId);
    }
    int expectedVersion = template.version();
    int actualVersion = ItemMarkers.getItemVersion(item);
    if (expectedVersion != actualVersion) {
      out.add("item_version: expected=" + expectedVersion + " got=" + actualVersion);
    }
    String expectedTier = template.tierSpec() == null ? null : normalize(template.tierSpec().id());
    String actualTier = ItemMarkers.getItemTier(item);
    if (!Objects.equals(expectedTier, actualTier)) {
      out.add("tier: expected=" + expectedTier + " got=" + actualTier);
    }
    String expectedRarity = normalize(template.rarityId());
    String actualRarity = ItemMarkers.getItemRarity(item);
    if (!Objects.equals(expectedRarity, actualRarity)) {
      out.add("rarity: expected=" + expectedRarity + " got=" + actualRarity);
    }
    double expectedManaMax = ItemMarkers.getManaMaxBonus(base);
    double expectedManaRegen = ItemMarkers.getManaRegenBonus(base);
    double expectedManaRegenMultiplier = ItemMarkers.getManaRegenMultiplier(base);
    double expectedManaRegenPercent = ItemMarkers.getManaRegenPercent(base);
    double expectedManaCostMultiplier = ItemMarkers.getManaCostMultiplier(base);
    double expectedManaCostAdd = ItemMarkers.getManaCostAdd(base);
    String expectedManaRegenMode = ItemMarkers.getManaRegenMode(base);
    if (Math.abs(expectedManaMax - ItemMarkers.getManaMaxBonus(item)) > STAT_EPS) {
      out.add("mana.max: expected=" + expectedManaMax + " got=" + ItemMarkers.getManaMaxBonus(item));
    }
    if (Math.abs(expectedManaRegen - ItemMarkers.getManaRegenBonus(item)) > STAT_EPS) {
      out.add("mana.regen: expected=" + expectedManaRegen + " got=" + ItemMarkers.getManaRegenBonus(item));
    }
    if (Math.abs(expectedManaRegenMultiplier - ItemMarkers.getManaRegenMultiplier(item)) > STAT_EPS) {
      out.add("mana.regenMultiplier: expected=" + expectedManaRegenMultiplier + " got=" + ItemMarkers.getManaRegenMultiplier(item));
    }
    if (Math.abs(expectedManaRegenPercent - ItemMarkers.getManaRegenPercent(item)) > STAT_EPS) {
      out.add("mana.regenPercent: expected=" + expectedManaRegenPercent + " got=" + ItemMarkers.getManaRegenPercent(item));
    }
    if (Math.abs(expectedManaCostMultiplier - ItemMarkers.getManaCostMultiplier(item)) > STAT_EPS) {
      out.add("mana.costMultiplier: expected=" + expectedManaCostMultiplier + " got=" + ItemMarkers.getManaCostMultiplier(item));
    }
    if (Math.abs(expectedManaCostAdd - ItemMarkers.getManaCostAdd(item)) > STAT_EPS) {
      out.add("mana.costAdd: expected=" + expectedManaCostAdd + " got=" + ItemMarkers.getManaCostAdd(item));
    }
    String actualManaRegenMode = ItemMarkers.getManaRegenMode(item);
    if (!Objects.equals(normalize(expectedManaRegenMode), normalize(actualManaRegenMode))) {
      out.add("mana.regenMode: expected=" + normalize(expectedManaRegenMode) + " got=" + normalize(actualManaRegenMode));
    }
    ItemConsumeMode expectedConsume = ItemMarkers.getConsumeMode(base);
    ItemConsumeMode actualConsume = ItemMarkers.getConsumeMode(item);
    if (expectedConsume != actualConsume) {
      out.add("consume.mode: expected=" + expectedConsume + " got=" + actualConsume);
    }
    int expectedConsumeAmount = ItemMarkers.getConsumeAmount(base);
    int actualConsumeAmount = ItemMarkers.getConsumeAmount(item);
    if (expectedConsumeAmount != actualConsumeAmount) {
      out.add("consume.amount: expected=" + expectedConsumeAmount + " got=" + actualConsumeAmount);
    }
    ItemTemplateCompiler.DurabilityRange range = template.durabilityRange();
    if (range != null && itemMeta instanceof Damageable damageable) {
      int damage = damageable.getDamage();
      if (damage < range.minDamage() || damage > range.maxDamage()) {
        out.add("durability: expected=" + range.minDamage() + "-" + range.maxDamage() + " got=" + damage);
      }
    }
    checkStats(template, item, out);
    checkAffixes(template, item, out);
    return out;
  }

  private static void checkStats(ItemTemplateSnapshot template, ItemStack item, List<String> out) {
    ItemStatBlock expectedBlock = template.baseStats();
    if (expectedBlock != null && template.tierSpec() != null) {
      expectedBlock = template.tierSpec().apply(expectedBlock);
    }
    Map<String, Double> expected = expectedBlock == null ? Map.of() : expectedBlock.values();
    Map<String, Double> actual = ItemMarkers.getItemStats(item);
    if (expected.isEmpty()) {
      if (!actual.isEmpty() && template.affixPool() == null) {
        out.add("stats: unexpected stats present (template has none)");
      }
      return;
    }
    for (var entry : expected.entrySet()) {
      Double value = actual.get(entry.getKey());
      if (value == null) {
        out.add("stats." + entry.getKey() + ": missing (expected >= " + entry.getValue() + ")");
      } else if (value + STAT_EPS < entry.getValue()) {
        out.add("stats." + entry.getKey() + ": below base expected=" + entry.getValue() + " got=" + value);
      }
    }
  }

  private static void checkAffixes(ItemTemplateSnapshot template, ItemStack item, List<String> out) {
    List<String> actual = ItemMarkers.getItemAffixes(item);
    ItemAffixPool pool = template.affixPool();
    if (pool == null) {
      if (!actual.isEmpty()) {
        out.add("affixes: unexpected affixes present (" + actual.size() + ")");
      }
      return;
    }
    if (actual.isEmpty()) {
      out.add("affixes: expected affixes but none present");
      return;
    }
    Set<String> allowed = new LinkedHashSet<>();
    for (ItemAffixSpec spec : pool.affixes()) {
      allowed.add(Ids.normalize(spec.id()));
    }
    for (String id : actual) {
      if (!allowed.contains(Ids.normalize(id))) {
        out.add("affixes: unknown id=" + id);
      }
    }
  }

  private static String plainName(ItemMeta meta) {
    if (meta == null) {
      return "";
    }
    Component name = meta.displayName();
    if (name == null) {
      return "";
    }
    return PLAIN.serialize(name);
  }

  private static List<String> plainLore(ItemMeta meta) {
    if (meta == null) {
      return List.of();
    }
    List<Component> lore = meta.lore();
    if (lore == null || lore.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>(lore.size());
    for (Component line : lore) {
      out.add(line == null ? "" : PLAIN.serialize(line));
    }
    return out;
  }

  private static Float customModelDataValue(ItemMeta meta) {
    if (meta == null || !meta.hasCustomModelDataComponent()) {
      return null;
    }
    CustomModelDataComponent component = meta.getCustomModelDataComponent();
    if (component == null) {
      return null;
    }
    List<Float> floats = component.getFloats();
    if (floats == null || floats.isEmpty()) {
      return null;
    }
    return floats.get(0);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Ids.normalize(value);
    } catch (IllegalArgumentException ex) {
      return value.trim().toLowerCase(Locale.ROOT);
    }
  }
}
