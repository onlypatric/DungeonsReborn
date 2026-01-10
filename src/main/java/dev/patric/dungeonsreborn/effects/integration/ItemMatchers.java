package dev.patric.dungeonsreborn.effects.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ItemMatchers {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final String MARKER_START = "[dr:effects]";
  private static final String MARKER_END = "[/dr:effects]";

  private ItemMatchers() {
  }

  public static ItemMatcher anyNonAir() {
    return (player, item) -> item != null && !item.getType().isAir();
  }

  public static ItemMatcher material(Material material) {
    Objects.requireNonNull(material, "material");
    return (player, item) -> item != null && item.getType() == material;
  }

  public static ItemMatcher customModelData(int cmd) {
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null || !meta.hasCustomModelDataComponent()) {
        return false;
      }
      CustomModelDataComponent component = meta.getCustomModelDataComponent();
      if (component == null) {
        return false;
      }
      // Legacy integer custom model data is represented as a single float in the floats list.
      var floats = component.getFloats();
      if (floats == null || floats.size() != 1) {
        return false;
      }
      Float value = floats.getFirst();
      if (value == null) {
        return false;
      }
      return Math.abs(value - (float) cmd) < 0.0001f;
    };
  }

  public static ItemMatcher tag(NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    return (player, item) -> ItemMarkers.has(item, key);
  }

  public static ItemMatcher itemId(String id) {
    Objects.requireNonNull(id, "id");
    String normalized = Ids.normalize(id);
    return (player, item) -> {
      String itemId = ItemMarkers.getItemId(item);
      return itemId != null && itemId.equals(normalized);
    };
  }

  /**
   * Discouraged but sometimes pragmatic: matches if any lore line (plain text) contains {@code substring} (case-insensitive).
   */
  public static ItemMatcher loreContains(String substring) {
    Objects.requireNonNull(substring, "substring");
    String needle = substring.toLowerCase();
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
        return false;
      }
      var lore = meta.lore();
      if (lore == null || lore.isEmpty()) {
        return false;
      }
      for (Component line : lore) {
        if (line == null) {
          continue;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(line);
        if (text != null && text.toLowerCase().contains(needle)) {
          return true;
        }
      }
      return false;
    };
  }

  public static ItemMatcher similar(ItemStack template) {
    Objects.requireNonNull(template, "template");
    ItemStack base = template.clone();
    base.setAmount(1);
    stripAbilityLore(base);
    stripItemId(base);
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      ItemStack compare = item.clone();
      compare.setAmount(1);
      stripAbilityLore(compare);
      stripItemId(compare);
      return compare.isSimilar(base);
    };
  }

  public static ItemMatcher and(ItemMatcher a, ItemMatcher b) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    return (player, item) -> a.matches(player, item) && b.matches(player, item);
  }

  public static ItemMatcher or(ItemMatcher a, ItemMatcher b) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    return (player, item) -> a.matches(player, item) || b.matches(player, item);
  }

  private static void stripAbilityLore(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return;
    }
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
    meta.lore(out.isEmpty() ? null : out);
    item.setItemMeta(meta);
  }

  private static void stripItemId(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    meta.getPersistentDataContainer().remove(ItemMarkers.ITEM_ID);
    item.setItemMeta(meta);
  }
}
