package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.locale.Locales;

public final class UpgradeLore {
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final String MARKER_START = "[dr:upgrade]";
  private static final String MARKER_END = "[/dr:upgrade]";
  private static final String APPLIED_START = "[dr:upgrades]";
  private static final String APPLIED_END = "[/dr:upgrades]";
  private static final String DEFAULT_BOOK_NAME =
      "<gradient:#ff9f1a:#ffd166><bold>Upgrade Tome</bold></gradient>";
  private static final Map<String, String> ABILITY_NAMES = new HashMap<>();
  private static int targetLineLimit = 10;
  private static String targetMoreFormat = "... {remaining} more";

  private UpgradeLore() {
  }

  public static void configure(org.bukkit.configuration.ConfigurationSection section, EffectsEngine engine) {
    if (section == null) {
      ABILITY_NAMES.clear();
    } else {
      targetLineLimit = Math.max(0, section.getInt("maxTargetLines", targetLineLimit));
      String format = section.getString("moreFormat", targetMoreFormat);
      if (format != null && !format.isBlank()) {
        targetMoreFormat = format;
      }
    }
    ABILITY_NAMES.clear();
    if (engine == null) {
      return;
    }
    for (AbilitySpec spec : engine.abilitySpecs().values()) {
      ABILITY_NAMES.put(spec.id(), abilityLabel(spec));
    }
  }

  public static Component parseRichText(String raw) {
    if (raw == null) {
      return Component.empty();
    }
    if (raw.indexOf('§') >= 0) {
      return LEGACY.deserialize(raw);
    }
    try {
      return MINI.deserialize(raw);
    } catch (Exception ignored) {
      return LEGACY.deserialize(raw.replace('&', '§'));
    }
  }

  private static String loreText(String key, Object... pairs) {
    return Locales.text(null, key, Locales.placeholders(pairs));
  }

  public static ItemStack applyUpgradeBookLore(ItemStack item, UpgradeSpec spec) {
    if (item == null || spec == null) {
      return item;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    String bookName = loreText("labels.upgrades.lore.bookName");
    if (bookName.isBlank() || bookName.equals("labels.upgrades.lore.bookName")) {
      bookName = DEFAULT_BOOK_NAME;
    }
    meta.displayName(parseRichText(bookName));
    List<Component> lore = new ArrayList<>();
    if (spec.name() != null && !spec.name().isBlank()) {
      lore.add(noItalic(parseRichText(spec.name())));
    }
    if (!spec.spells().isEmpty()) {
      String activators = spec.spells().stream()
          .map(spell -> label(spell.activator()))
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.joining(", "));
      lore.add(mmLine(loreText("labels.upgrades.lore.activation", "binding", activators)));
    }
    if (spec.limits() != null && spec.limits().category() != null && !spec.limits().category().isBlank()) {
      StringBuilder line = new StringBuilder(spec.limits().category());
      if (spec.limits().tier() > 0) {
        line.append(" (")
            .append(loreText("labels.upgrades.lore.categoryTier", "tier", spec.limits().tier()))
            .append(')');
      }
      lore.add(mmLine(loreText("labels.upgrades.lore.category", "category", line)));
    }
    if (spec.limits() != null) {
      if (spec.limits().exclusive()) {
        String label = spec.limits().category() == null
            ? loreText("labels.upgrades.lore.exclusive")
            : loreText("labels.upgrades.lore.exclusiveCategory", "category", spec.limits().category());
        lore.add(mmLine(label));
      }
      if (spec.limits().maxPerItem() > 0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.limitPerItem", "count", spec.limits().maxPerItem())));
      }
      if (spec.limits().maxTier() > 0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.maxTier", "tier", spec.limits().maxTier())));
      }
    }
    if (spec.requirements() != null) {
      if (spec.requirements().minXp() > 0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.requiresLevels", "level", spec.requirements().minXp())));
      }
      if (spec.requirements().consumeXp() > 0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.consumesLevels", "level", spec.requirements().consumeXp())));
      }
      if (spec.requirements().minTotalXp() > 0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.requiresTotalXp", "xp", spec.requirements().minTotalXp())));
      }
      if (spec.requirements().consumeTotalXp() > 0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.consumesTotalXp", "xp", spec.requirements().consumeTotalXp())));
      }
      if (spec.requirements().minProgress() > 0.0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.requiresProgress",
            "percent", formatPercent(spec.requirements().minProgress()))));
      }
      if (spec.requirements().consumeProgress() > 0.0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.consumesProgress",
            "percent", formatPercent(spec.requirements().consumeProgress()))));
      }
      if (spec.requirements().minMaxMana() > 0.0) {
        lore.add(mmLine(loreText("labels.upgrades.lore.requiresMaxMana",
            "mana", format(spec.requirements().minMaxMana()))));
      }
    }
    if (spec.price() != null && !spec.price().isEmpty()) {
      lore.add(mmLine(loreText("labels.upgrades.lore.cost", "price", formatPrice(spec.price()))));
    }
    for (UpgradeModifierSpec modifier : spec.modifiers()) {
      String line = formatModifier(modifier);
      if (!line.isBlank()) {
        lore.add(mmLine("<gray>" + line + "</gray>"));
      }
    }
    if (spec.behaviors() != null && !spec.behaviors().isEmpty()) {
      if (spec.behaviors().inventoryActive()) {
        lore.add(mmLine(loreText("labels.upgrades.lore.inventoryActive")));
      }
      List<String> secondaryAbilities = spec.behaviors().secondaryAbilities();
      List<String> secondaryDescriptions = spec.behaviors().secondaryDescriptions();
      int secondaryCount = Math.max(secondaryAbilities.size(), secondaryDescriptions.size());
      for (int i = 0; i < secondaryCount; i++) {
        String description = i < secondaryDescriptions.size() ? secondaryDescriptions.get(i) : "";
        String abilityName = i < secondaryAbilities.size() ? resolveAbilityName(secondaryAbilities.get(i)) : "";
        String label = !description.isBlank() ? description : abilityName;
        if (label.isBlank()) {
          continue;
        }
        lore.add(mmLine(loreText("labels.upgrades.lore.secondary", "value", label)));
      }
      for (String preset : spec.behaviors().particlePresets()) {
        lore.add(mmLine(loreText("labels.upgrades.lore.particlePreset", "preset", preset)));
      }
      for (UpgradeStatusEffectSpec effect : spec.behaviors().statusEffects()) {
        lore.add(mmLine(loreText("labels.upgrades.lore.statusEffect", "value",
            formatStatusEffect(effect, "labels.upgrades.lore.prefix.onHit"))));
      }
      for (UpgradeStatusEffectSpec effect : spec.behaviors().inventoryEffects()) {
        lore.add(mmLine(loreText("labels.upgrades.lore.statusEffect", "value",
            formatStatusEffect(effect, "labels.upgrades.lore.prefix.inInventory"))));
      }
      for (UpgradeOnDamagedSpec effect : spec.behaviors().onDamagedEffects()) {
        String line = formatOnDamagedEffect(effect);
        if (!line.isBlank()) {
          lore.add(mmLine(loreText("labels.upgrades.lore.onDamagedLine", "value", line)));
        }
      }
    }
    if (spec.compatibility() != null && !spec.compatibility().isEmpty()) {
      if (!spec.compatibility().denyItemIds().isEmpty()) {
        lore.add(mmLine(loreText("labels.upgrades.lore.blockedItems",
            "items", String.join(", ", spec.compatibility().denyItemIds()))));
      }
      if (!spec.compatibility().denyMaterials().isEmpty()) {
        lore.add(mmLine(loreText("labels.upgrades.lore.blockedMaterials",
            "materials", joinMaterials(spec.compatibility().denyMaterials()))));
      }
    }
    if (!spec.spells().isEmpty()) {
      String bindings = spec.spells().stream()
          .map(spell -> label(spell.activator()))
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.joining(", "));
      String conflict = Locales.text(null, "labels.upgrades.lore.conflicts",
          Locales.placeholders("binding", bindings));
      lore.add(mmLine(conflict));
    }
    for (UpgradeAttributeSpec attr : spec.attributes()) {
      String line = formatAttribute(attr);
      if (!line.isBlank()) {
        lore.add(mmLine(loreText("labels.upgrades.lore.attribute", "value", line)));
      }
    }
    for (UpgradeEnchantSpec enchant : spec.enchants()) {
      lore.add(mmLine(loreText("labels.upgrades.lore.enchant", "value", formatEnchant(enchant))));
    }
    if (spec.description() != null && !spec.description().isBlank()) {
      String[] lines = spec.description().replace("\\n", "\n").split("\n", -1);
      for (String line : lines) {
        if (line.isBlank()) {
          continue;
        }
        lore.add(noItalic(parseRichText(line)));
      }
    }
    lore.add(mmLine(loreText("labels.upgrades.lore.hintApply")));
    if (!lore.isEmpty()) {
      List<Component> merged = new ArrayList<>();
      merged.add(noItalic(Component.text(MARKER_START, NamedTextColor.BLACK)));
      merged.addAll(lore);
      merged.add(noItalic(Component.text(MARKER_END, NamedTextColor.BLACK)));
      meta.lore(merged);
    }
    item.setItemMeta(meta);
    return item;
  }

  public static List<Component> stripUpgradeLore(List<Component> lore) {
    if (lore == null || lore.isEmpty()) {
      return new ArrayList<>();
    }
    List<Component> out = new ArrayList<>();
    boolean inBlock = false;
    for (Component line : lore) {
      String plain = line == null ? null : PLAIN.serialize(line);
      if (MARKER_START.equals(plain)) {
        inBlock = true;
        continue;
      }
      if (MARKER_END.equals(plain)) {
        inBlock = false;
        continue;
      }
      if (!inBlock) {
        out.add(line);
      }
    }
    return out;
  }

  public static ItemStack applyAppliedUpgradeLore(ItemStack item, List<String> records, UpgradeYamlRegistry registry) {
    if (item == null) {
      return null;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    List<Component> baseLore = stripAppliedUpgradeLore(meta.lore());
    if (records == null || records.isEmpty()) {
      meta.lore(baseLore.isEmpty() ? null : baseLore);
      item.setItemMeta(meta);
      return item;
    }

    List<Component> block = buildAppliedUpgradeLore(item, records, registry);
    if (block.isEmpty()) {
      meta.lore(baseLore.isEmpty() ? null : baseLore);
      item.setItemMeta(meta);
      return item;
    }

    List<Component> merged = new ArrayList<>(baseLore);
    if (!merged.isEmpty()) {
      Component last = merged.get(merged.size() - 1);
      if (!isBlankLine(last)) {
        merged.add(Component.text(" "));
      }
    }
    merged.add(Component.text(APPLIED_START, NamedTextColor.BLACK));
    merged.addAll(block);
    merged.add(Component.text(APPLIED_END, NamedTextColor.BLACK));
    meta.lore(merged);
    item.setItemMeta(meta);
    return item;
  }

  private static List<Component> stripAppliedUpgradeLore(List<Component> lore) {
    if (lore == null || lore.isEmpty()) {
      return new ArrayList<>();
    }
    List<Component> out = new ArrayList<>();
    boolean inBlock = false;
    for (Component line : lore) {
      String plain = line == null ? null : PLAIN.serialize(line);
      if (APPLIED_START.equals(plain)) {
        inBlock = true;
        continue;
      }
      if (APPLIED_END.equals(plain)) {
        inBlock = false;
        continue;
      }
      if (!inBlock) {
        out.add(line);
      }
    }
    return out;
  }

  private static boolean isBlankLine(Component line) {
    if (line == null) {
      return true;
    }
    String text = PLAIN.serialize(line);
    return text == null || text.trim().isEmpty();
  }

  private static List<Component> buildAppliedUpgradeLore(ItemStack item, List<String> records, UpgradeYamlRegistry registry) {
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    int count = 0;
    for (String record : records) {
      if (record != null && !record.isBlank()) {
        count++;
      }
    }
    List<Component> out = new ArrayList<>();
    out.add(parseRichText(loreText("labels.upgrades.lore.appliedHeader", "count", count)));
    boolean compact = item != null && ItemMarkers.isUpgradeLoreCompact(item);
    if (!compact) {
      appendUpgradeDetails(out, records, registry);
    }
    appendUpgradeTotals(out, item);
    return out;
  }

  private static void appendUpgradeDetails(List<Component> out, List<String> records, UpgradeYamlRegistry registry) {
    for (String record : records) {
      if (record == null || record.isBlank()) {
        continue;
      }
      if (record.startsWith("vanilla:")) {
        out.add(parseRichText(loreText("labels.upgrades.lore.appliedVanilla")));
        String raw = record.substring("vanilla:".length());
        if (!raw.isBlank()) {
          for (String part : raw.split(":")) {
            if (part.isBlank()) {
              continue;
            }
            int dash = part.lastIndexOf('-');
            String key = dash > 0 ? part.substring(0, dash) : part;
            String level = dash > 0 ? part.substring(dash + 1) : "";
            String name = key.replace("minecraft:", "").replace('_', ' ');
            String text = level.isBlank() ? name : name + " " + level;
            out.add(Component.text("  " + text, NamedTextColor.DARK_GRAY));
          }
        }
        continue;
      }
      UpgradeSpec spec = registry == null ? null : registry.upgradeSpec(record);
      if (spec == null) {
        out.add(Component.text("• " + record, NamedTextColor.GRAY));
        continue;
      }
      String name = spec.name() != null && !spec.name().isBlank() ? spec.name() : record;
      Component line = Component.text("• ", NamedTextColor.GRAY).append(parseRichText(name));
      if (!spec.spells().isEmpty()) {
        String activators = spec.spells().stream()
            .map(spell -> label(spell.activator()))
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.joining(", "));
        line = line.append(Component.text(" [" + activators + "]", NamedTextColor.DARK_GRAY));
      }
      out.add(line);
      if (spec.behaviors() != null && spec.behaviors().inventoryActive()) {
        out.add(parseRichText(loreText("labels.upgrades.lore.inventoryActiveApplied")));
      }
      if (spec.behaviors() != null && !spec.behaviors().onDamagedEffects().isEmpty()) {
        for (UpgradeOnDamagedSpec effect : spec.behaviors().onDamagedEffects()) {
          String detail = formatOnDamagedEffect(effect);
          if (!detail.isBlank()) {
            out.add(Component.text("  " + detail, NamedTextColor.DARK_GRAY));
          }
        }
      }
      if (spec.description() != null && !spec.description().isBlank()) {
        String[] lines = spec.description().replace("\\n", "\n").split("\n", -1);
        for (String desc : lines) {
          if (desc.isBlank()) {
            continue;
          }
          Component prefix = Component.text("  ", NamedTextColor.DARK_GRAY);
          out.add(prefix.append(parseRichText(desc)));
        }
      }
    }
  }

  private static void appendUpgradeTotals(List<Component> out, ItemStack item) {
    if (item == null) {
      return;
    }
    java.util.Map<String, Double> modifiers = ItemMarkers.getUpgradeModifiers(item);
    if (modifiers.isEmpty()) {
      return;
    }
    out.add(parseRichText(loreText("labels.upgrades.lore.appliedTotals")));
    for (UpgradeModifierType type : UpgradeModifierType.values()) {
      Double value = modifiers.get(type.key());
      if (value == null || !Double.isFinite(value)) {
        continue;
      }
      String line = formatModifier(type, value);
      if (!line.isBlank()) {
        out.add(Component.text(line, NamedTextColor.GRAY));
      }
    }
  }

  private static String label(UpgradeActivator activator) {
    return switch (activator) {
      case LEFT_CLICK -> loreText("labels.upgrades.lore.activator.left");
      case RIGHT_CLICK -> loreText("labels.upgrades.lore.activator.right");
      case SHIFT_LEFT_CLICK -> loreText("labels.upgrades.lore.activator.shiftLeft");
      case SHIFT_RIGHT_CLICK -> loreText("labels.upgrades.lore.activator.shiftRight");
      case PASSIVE -> loreText("labels.upgrades.lore.activator.passive");
    };
  }

  private static String formatAttribute(UpgradeAttributeSpec attr) {
    String name = attr.attribute().getKey().getKey().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    String amount = format(attr.amount());
    String sign = attr.amount() >= 0 ? "+" : "";
    return loreText("labels.upgrades.lore.attributeValue", "sign", sign, "amount", amount, "name", name);
  }

  private static String formatEnchant(UpgradeEnchantSpec spec) {
    String key = spec.enchantment().getKey().toString();
    String name = key.replace("minecraft:", "").replace('_', ' ');
    return loreText("labels.upgrades.lore.enchantValue", "name", name, "level", spec.level());
  }

  private static String formatModifier(UpgradeModifierSpec modifier) {
    if (modifier == null || modifier.type() == null) {
      return "";
    }
    return formatModifier(modifier.type(), modifier.value());
  }

  private static String formatModifier(UpgradeModifierType type, double value) {
    if (type == null) {
      return "";
    }
    if (type.isMultiplier()) {
      return type.label() + " x" + format(value);
    }
    String sign = value >= 0 ? "+" : "";
    return type.label() + " " + sign + format(value);
  }

  private static String formatStatusEffect(UpgradeStatusEffectSpec effect, String prefixKey) {
    if (effect == null) {
      return "";
    }
    String name = effect.type().getKey().getKey().replace('_', ' ');
    int seconds = Math.max(1, Math.round(effect.durationTicks() / 20.0f));
    int level = effect.amplifier() + 1;
    String prefix = loreText(prefixKey);
    return loreText("labels.upgrades.lore.statusEffectValue",
        "prefix", prefix,
        "effect", name,
        "level", level,
        "seconds", seconds);
  }

  private static String formatOnDamagedEffect(UpgradeOnDamagedSpec effect) {
    if (effect == null) {
      return "";
    }
    String base = formatStatusEffect(effect.effect(), "labels.upgrades.lore.prefix.onDamaged");
    long cooldownTicks = effect.cooldownTicks();
    if (cooldownTicks <= 0L) {
      return base;
    }
    int seconds = Math.max(1, Math.round(cooldownTicks / 20.0f));
    return loreText("labels.upgrades.lore.statusEffectCooldown", "base", base, "seconds", seconds);
  }

  private static String format(double value) {
    if (!Double.isFinite(value)) {
      return "0";
    }
    if (Math.abs(value - Math.round(value)) < 1e-9) {
      return String.valueOf((long) Math.round(value));
    }
    return String.format(java.util.Locale.ROOT, "%.2f", value);
  }

  private static String formatPercent(double value) {
    double pct = value * 100.0;
    if (Math.abs(pct - Math.round(pct)) < 1e-9) {
      return String.valueOf((long) Math.round(pct)) + "%";
    }
    return String.format(java.util.Locale.ROOT, "%.1f%%", pct);
  }

  private static String formatPrice(UpgradePriceSpec price) {
    List<String> parts = new ArrayList<>();
    if (price.pallet() > 0) {
      parts.add(loreText("labels.upgrades.lore.pricePallet", "count", price.pallet()));
    }
    if (price.compressed() > 0) {
      parts.add(loreText("labels.upgrades.lore.priceCompressed", "count", price.compressed()));
    }
    if (price.normal() > 0) {
      parts.add(loreText("labels.upgrades.lore.priceTokens", "count", price.normal()));
    }
    if (parts.isEmpty()) {
      return loreText("labels.upgrades.lore.priceFree");
    }
    return String.join(", ", parts);
  }

  private static String joinMaterials(java.util.Set<org.bukkit.Material> materials) {
    if (materials == null || materials.isEmpty()) {
      return "";
    }
    java.util.List<String> names = new java.util.ArrayList<>();
    for (org.bukkit.Material material : materials) {
      names.add(material.name().toLowerCase(java.util.Locale.ROOT));
    }
    return String.join(", ", names);
  }

  private static void appendBindTargets(List<Component> lore, UpgradeCompatibilitySpec compatibility) {
    if (compatibility == null) {
      return;
    }
    lore.add(mmLine(loreText("labels.upgrades.lore.bindsSeparator")));
    List<String> targets = new ArrayList<>();
    if (!compatibility.allowItemIds().isEmpty()) {
      targets.addAll(compatibility.allowItemIds());
    }
    if (!compatibility.allowMaterials().isEmpty()) {
      for (org.bukkit.Material material : compatibility.allowMaterials()) {
        targets.add(material.name().toLowerCase(java.util.Locale.ROOT));
      }
    }
    if (targets.isEmpty()) {
      lore.add(mmLine(loreText("labels.upgrades.lore.bindsAny")));
      return;
    }
    lore.add(mmLine(loreText("labels.upgrades.lore.bindsHeader")));
    int limit = targetLineLimit;
    int shown = 0;
    for (String entry : targets) {
      if (limit > 0 && shown >= limit) {
        break;
      }
      lore.add(mmLine(loreText("labels.upgrades.lore.bindsEntry", "entry", entry)));
      shown++;
    }
    int remaining = targets.size() - shown;
    if (limit > 0 && remaining > 0) {
      lore.add(mmLine(loreText("labels.upgrades.lore.bindsMore", "remaining", remaining)));
    }
  }

  private static String resolveAbilityName(String id) {
    if (id == null || id.isBlank()) {
      return "";
    }
    String normalized;
    try {
      normalized = Ids.normalize(id);
    } catch (Exception ex) {
      normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
    }
    String name = ABILITY_NAMES.get(normalized);
    if (name == null || name.isBlank()) {
      return prettifyId(id);
    }
    if (name.equalsIgnoreCase(normalized) || name.equalsIgnoreCase(id)) {
      return prettifyId(name);
    }
    return name;
  }

  private static String abilityLabel(AbilitySpec spec) {
    if (spec == null) {
      return "";
    }
    String name = spec.name();
    if (name != null && !name.isBlank()) {
      return PLAIN.serialize(parseRichText(name));
    }
    String description = spec.description();
    if (description != null && !description.isBlank()) {
      String[] lines = description.replace("\\n", "\n").split("\n", -1);
      for (String line : lines) {
        if (line.isBlank()) {
          continue;
        }
        return PLAIN.serialize(parseRichText(line));
      }
    }
    return spec.id();
  }

  private static String prettifyId(String raw) {
    if (raw == null) {
      return "";
    }
    String value = raw.replace(':', ' ').replace('_', ' ').replace('.', ' ').trim();
    if (value.isEmpty()) {
      return raw;
    }
    String[] parts = value.split("\\s+");
    StringBuilder out = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        out.append(part.substring(1));
      }
    }
    return out.toString();
  }

  private static Component noItalic(Component component) {
    return component.decoration(TextDecoration.ITALIC, false);
  }

  private static Component line(String text, NamedTextColor color) {
    return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
  }

  private static Component mmLine(String mm) {
    return noItalic(parseRichText(mm));
  }
}
