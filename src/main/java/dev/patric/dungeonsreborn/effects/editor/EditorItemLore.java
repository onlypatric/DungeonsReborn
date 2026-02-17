package dev.patric.dungeonsreborn.effects.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.util.YamlValues;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import dev.patric.dungeonsreborn.util.TextStyles;

public final class EditorItemLore {
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final String MARKER_START = "[dr:effects]";
  private static final String MARKER_END = "[/dr:effects]";

  private EditorItemLore() {
  }

  public static Component parseRichText(String raw) {
    if (raw == null) {
      return Component.empty();
    }
    if (raw.indexOf('§') >= 0) {
      return TextStyles.noItalic(LEGACY.deserialize(raw));
    }
    try {
      return TextStyles.noItalic(MINI.deserialize(raw));
    } catch (Exception ignored) {
      return TextStyles.noItalic(LEGACY.deserialize(raw.replace('&', '§')));
    }
  }

  public static boolean hasGlint(ItemStack item) {
    if (item == null) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }
    NamespacedKey key = GuiItem.defaultGlintKey();
    return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
  }

  public static ItemStack setGlint(ItemStack item, boolean enabled) {
    if (item == null) {
      return null;
    }
    return GuiItem.of(item).glint(enabled).build();
  }

  public static void setFlag(ItemMeta meta, ItemFlag flag, boolean enabled) {
    if (enabled) {
      meta.addItemFlags(flag);
    } else {
      meta.removeItemFlags(flag);
    }
  }

  public static ItemStack applyAbilityLore(ItemStack item, List<Map<String, Object>> bindings, EffectsEngine engine) {
    if (item == null || engine == null) {
      return item;
    }
    if (dev.patric.dungeonsreborn.effects.items.ItemMarkers.has(
        item, dev.patric.dungeonsreborn.effects.items.ItemMarkers.HIDE_EFFECTS_LORE)) {
      return item;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }

    List<Component> baseLore = stripAbilityLore(meta.lore());
    List<Component> block = buildAbilityLore(bindings, engine);
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
    merged.add(Component.text(MARKER_START, NamedTextColor.BLACK));
    merged.addAll(block);
    merged.add(Component.text(MARKER_END, NamedTextColor.BLACK));
    meta.lore(merged);
    item.setItemMeta(meta);
    return item;
  }

  private static List<Component> stripAbilityLore(List<Component> lore) {
    if (lore == null || lore.isEmpty()) {
      return new ArrayList<>();
    }
    List<Component> out = new ArrayList<>();
    boolean inBlock = false;
    for (Component line : lore) {
      String plain = PLAIN.serialize(line);
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

  private static boolean isBlankLine(Component line) {
    if (line == null) {
      return true;
    }
    String text = PLAIN.serialize(line);
    return text == null || text.trim().isEmpty();
  }

  private static List<Component> buildAbilityLore(List<Map<String, Object>> bindings, EffectsEngine engine) {
    if (bindings == null || bindings.isEmpty()) {
      return List.of();
    }

    class AbilityEntry {
      private final String id;
      private final LinkedHashSet<String> activations = new LinkedHashSet<>();

      AbilityEntry(String id) {
        this.id = id;
      }
    }

    LinkedHashMap<String, AbilityEntry> entries = new LinkedHashMap<>();
    for (Map<String, Object> binding : bindings) {
      Object raw = binding.get("ability");
      if (raw == null) {
        continue;
      }
      String value = raw.toString().trim();
      if (value.isBlank()) {
        continue;
      }
      try {
        String id = Ids.normalize(value);
        AbilityEntry entry = entries.computeIfAbsent(id, AbilityEntry::new);
        entry.activations.add(activationLabel(binding));
      } catch (Exception ignored) {
      }
    }
    if (entries.isEmpty()) {
      return List.of();
    }

    List<Component> out = new ArrayList<>();
    out.add(Component.text("Effects", NamedTextColor.DARK_GRAY));
    for (AbilityEntry entry : entries.values()) {
      String id = entry.id;
      AbilitySpec spec = engine.abilitySpec(id);
      String name = spec != null && spec.name() != null ? spec.name() : id;
      String activationText = entry.activations.isEmpty() ? "Passive" : String.join(", ", entry.activations);
      Component line = Component.text("• ", NamedTextColor.GRAY)
          .append(parseRichText(name))
          .append(Component.text(" [" + activationText + "]", NamedTextColor.DARK_GRAY));
      out.add(line);
      String desc = spec != null ? spec.description() : null;
      if (desc == null || desc.isBlank()) {
        continue;
      }
      String[] lines = desc.replace("\\n", "\n").split("\n", -1);
      for (String lineText : lines) {
        if (lineText.isBlank()) {
          continue;
        }
        out.add(Component.text("  ", NamedTextColor.DARK_GRAY).append(parseRichText(lineText)));
      }
    }
    return out;
  }

  private static String activationLabel(Map<String, Object> binding) {
    String type = YamlValues.string(binding, "type", "interact");
    if ("passive".equalsIgnoreCase(type)) {
      return "Passive";
    }
    String click = YamlValues.string(binding, "click", "RIGHT_CLICK");
    if ("passive".equalsIgnoreCase(click)) {
      return "Passive";
    }
    boolean sneaking = Boolean.parseBoolean(String.valueOf(binding.getOrDefault("requireSneaking", false)));
    String base = click.toUpperCase(Locale.ROOT).contains("LEFT") ? "Left Click" : "Right Click";
    return sneaking ? "Shift+" + base : base;
  }

}
