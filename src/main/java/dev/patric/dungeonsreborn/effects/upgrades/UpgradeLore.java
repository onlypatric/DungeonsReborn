package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public final class UpgradeLore {
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final String MARKER_START = "[dr:upgrade]";
  private static final String MARKER_END = "[/dr:upgrade]";
  private static final String APPLIED_START = "[dr:upgrades]";
  private static final String APPLIED_END = "[/dr:upgrades]";
  private static final String DEFAULT_BOOK_NAME = "<gold>Spell Upgrade</gold>";

  private UpgradeLore() {
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

  public static ItemStack applyUpgradeBookLore(ItemStack item, UpgradeSpec spec) {
    if (item == null || spec == null) {
      return item;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    meta.displayName(parseRichText(DEFAULT_BOOK_NAME));
    List<Component> lore = new ArrayList<>();
    if (spec.name() != null && !spec.name().isBlank()) {
      lore.add(parseRichText(spec.name()));
    }
    if (spec.spell() != null) {
      lore.add(Component.text("Activator: " + label(spec.spell().activator()), NamedTextColor.DARK_GRAY));
    }
    if (spec.target() != null && !spec.target().isEmpty()) {
      String targetLine = formatTarget(spec.target());
      if (!targetLine.isBlank()) {
        lore.add(Component.text("Target: " + targetLine, NamedTextColor.DARK_GRAY));
      }
    }
    if (spec.limits() != null && spec.limits().category() != null && !spec.limits().category().isBlank()) {
      StringBuilder line = new StringBuilder("Category: ").append(spec.limits().category());
      if (spec.limits().tier() > 0) {
        line.append(" (Tier ").append(spec.limits().tier()).append(')');
      }
      lore.add(Component.text(line.toString(), NamedTextColor.DARK_GRAY));
    }
    if (spec.limits() != null) {
      if (spec.limits().exclusive()) {
        String label = spec.limits().category() == null ? "Exclusive" : ("Exclusive: " + spec.limits().category());
        lore.add(Component.text(label, NamedTextColor.DARK_GRAY));
      }
      if (spec.limits().maxPerItem() > 0) {
        lore.add(Component.text("Limit: " + spec.limits().maxPerItem() + " per item", NamedTextColor.DARK_GRAY));
      }
      if (spec.limits().maxTier() > 0) {
        lore.add(Component.text("Max Tier: " + spec.limits().maxTier(), NamedTextColor.DARK_GRAY));
      }
    }
    if (spec.requirements() != null) {
      if (spec.requirements().minXp() > 0) {
        lore.add(Component.text("Requires: " + spec.requirements().minXp() + " Levels", NamedTextColor.GRAY));
      }
      if (spec.requirements().consumeXp() > 0) {
        lore.add(Component.text("Consumes: " + spec.requirements().consumeXp() + " Levels", NamedTextColor.DARK_GRAY));
      }
      if (spec.requirements().minTotalXp() > 0) {
        lore.add(Component.text("Requires: " + spec.requirements().minTotalXp() + " Total XP", NamedTextColor.GRAY));
      }
      if (spec.requirements().consumeTotalXp() > 0) {
        lore.add(Component.text("Consumes: " + spec.requirements().consumeTotalXp() + " Total XP",
            NamedTextColor.DARK_GRAY));
      }
      if (spec.requirements().minProgress() > 0.0) {
        lore.add(Component.text("Requires: " + formatPercent(spec.requirements().minProgress()) + " XP Progress",
            NamedTextColor.GRAY));
      }
      if (spec.requirements().consumeProgress() > 0.0) {
        lore.add(Component.text("Consumes: " + formatPercent(spec.requirements().consumeProgress()) + " XP Progress",
            NamedTextColor.DARK_GRAY));
      }
      if (spec.requirements().minMaxMana() > 0.0) {
        lore.add(Component.text("Requires: " + format(spec.requirements().minMaxMana()) + " Max Mana",
            NamedTextColor.GRAY));
      }
    }
    if (spec.price() != null && !spec.price().isEmpty()) {
      lore.add(Component.text("Cost: " + formatPrice(spec.price()), NamedTextColor.GOLD));
    }
    for (UpgradeModifierSpec modifier : spec.modifiers()) {
      String line = formatModifier(modifier);
      if (!line.isBlank()) {
        lore.add(Component.text(line, NamedTextColor.GRAY));
      }
    }
    if (spec.behaviors() != null && !spec.behaviors().isEmpty()) {
      if (spec.behaviors().inventoryActive()) {
        lore.add(Component.text("Inventory Active", NamedTextColor.DARK_GRAY));
      }
      for (String ability : spec.behaviors().secondaryAbilities()) {
        lore.add(Component.text("Secondary: " + ability, NamedTextColor.DARK_GRAY));
      }
      for (String preset : spec.behaviors().particlePresets()) {
        lore.add(Component.text("Particle Preset: " + preset, NamedTextColor.DARK_GRAY));
      }
      for (UpgradeStatusEffectSpec effect : spec.behaviors().statusEffects()) {
        lore.add(Component.text(formatStatusEffect(effect, "On hit"), NamedTextColor.DARK_GRAY));
      }
      for (UpgradeStatusEffectSpec effect : spec.behaviors().inventoryEffects()) {
        lore.add(Component.text(formatStatusEffect(effect, "While in inventory"), NamedTextColor.DARK_GRAY));
      }
      for (UpgradeOnDamagedSpec effect : spec.behaviors().onDamagedEffects()) {
        String line = formatOnDamagedEffect(effect);
        if (!line.isBlank()) {
          lore.add(Component.text(line, NamedTextColor.DARK_GRAY));
        }
      }
    }
    if (spec.compatibility() != null && !spec.compatibility().isEmpty()) {
      if (!spec.compatibility().allowItemIds().isEmpty()) {
        lore.add(Component.text("Only Items: " + String.join(", ", spec.compatibility().allowItemIds()),
            NamedTextColor.DARK_GRAY));
      }
      if (!spec.compatibility().allowMaterials().isEmpty()) {
        lore.add(Component.text("Only Materials: " + joinMaterials(spec.compatibility().allowMaterials()),
            NamedTextColor.DARK_GRAY));
      }
      if (!spec.compatibility().denyItemIds().isEmpty()) {
        lore.add(Component.text("Blocked Items: " + String.join(", ", spec.compatibility().denyItemIds()),
            NamedTextColor.DARK_GRAY));
      }
      if (!spec.compatibility().denyMaterials().isEmpty()) {
        lore.add(Component.text("Blocked Materials: " + joinMaterials(spec.compatibility().denyMaterials()),
            NamedTextColor.DARK_GRAY));
      }
    }
    if (spec.spell() != null) {
      lore.add(Component.text("Conflicts: existing " + label(spec.spell().activator()) + " binding",
          NamedTextColor.DARK_GRAY));
    }
    for (UpgradeAttributeSpec attr : spec.attributes()) {
      String line = formatAttribute(attr);
      if (!line.isBlank()) {
        lore.add(Component.text(line, NamedTextColor.GRAY));
      }
    }
    for (UpgradeEnchantSpec enchant : spec.enchants()) {
      lore.add(Component.text(formatEnchant(enchant), NamedTextColor.DARK_GRAY));
    }
    if (spec.description() != null && !spec.description().isBlank()) {
      String[] lines = spec.description().replace("\\n", "\n").split("\n", -1);
      for (String line : lines) {
        if (line.isBlank()) {
          continue;
        }
        lore.add(parseRichText(line));
      }
    }
    lore.add(Component.text("Use in /dr upgrades", NamedTextColor.DARK_GRAY));
    if (!lore.isEmpty()) {
      List<Component> merged = new ArrayList<>();
      merged.add(Component.text(MARKER_START, NamedTextColor.BLACK));
      merged.addAll(lore);
      merged.add(Component.text(MARKER_END, NamedTextColor.BLACK));
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
    out.add(Component.text("Applied Upgrades (" + count + ")", NamedTextColor.DARK_GRAY));
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
        out.add(Component.text("• Enchanted Book", NamedTextColor.GRAY));
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
      if (spec.spell() != null) {
        line = line.append(Component.text(" [" + label(spec.spell().activator()) + "]", NamedTextColor.DARK_GRAY));
      }
      out.add(line);
      if (spec.behaviors() != null && spec.behaviors().inventoryActive()) {
        out.add(Component.text("  Inventory Active", NamedTextColor.DARK_GRAY));
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
    out.add(Component.text("Totals", NamedTextColor.DARK_GRAY));
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
      case LEFT_CLICK -> "Left Click";
      case RIGHT_CLICK -> "Right Click";
      case SHIFT_LEFT_CLICK -> "Shift+Left";
      case SHIFT_RIGHT_CLICK -> "Shift+Right";
      case PASSIVE -> "Passive";
    };
  }

  private static String formatAttribute(UpgradeAttributeSpec attr) {
    String name = attr.attribute().getKey().getKey().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    String amount = format(attr.amount());
    String sign = attr.amount() >= 0 ? "+" : "";
    return sign + amount + " " + name;
  }

  private static String formatEnchant(UpgradeEnchantSpec spec) {
    String key = spec.enchantment().getKey().toString();
    String name = key.replace("minecraft:", "").replace('_', ' ');
    return "Enchant: " + name + " " + spec.level();
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

  private static String formatStatusEffect(UpgradeStatusEffectSpec effect, String prefix) {
    if (effect == null) {
      return "";
    }
    String name = effect.type().getKey().getKey().replace('_', ' ');
    int seconds = Math.max(1, Math.round(effect.durationTicks() / 20.0f));
    int level = effect.amplifier() + 1;
    return prefix + ": " + name + " " + level + " (" + seconds + "s)";
  }

  private static String formatOnDamagedEffect(UpgradeOnDamagedSpec effect) {
    if (effect == null) {
      return "";
    }
    String base = formatStatusEffect(effect.effect(), "On damaged");
    long cooldownTicks = effect.cooldownTicks();
    if (cooldownTicks <= 0L) {
      return base;
    }
    int seconds = Math.max(1, Math.round(cooldownTicks / 20.0f));
    return base + " (cd " + seconds + "s)";
  }

  private static String formatTarget(UpgradeTargetSpec target) {
    if (target == null || target.isEmpty()) {
      return "";
    }
    if (!target.abilityIds().isEmpty()) {
      return String.join(", ", target.abilityIds());
    }
    if (!target.abilityTags().isEmpty()) {
      List<String> tags = new ArrayList<>();
      for (String tag : target.abilityTags()) {
        tags.add("tag:" + tag);
      }
      return String.join(", ", tags);
    }
    return "";
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
      parts.add(price.pallet() + " Pallet");
    }
    if (price.compressed() > 0) {
      parts.add(price.compressed() + " Compressed");
    }
    if (price.normal() > 0) {
      parts.add(price.normal() + " Tokens");
    }
    if (parts.isEmpty()) {
      return "Free";
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
}
