package dev.patric.dungeonsreborn.effects.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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

  public static ItemMatcher not(ItemMatcher matcher) {
    Objects.requireNonNull(matcher, "matcher");
    return (player, item) -> !matcher.matches(player, item);
  }

  public static ItemMatcher itemTag(String tag) {
    Objects.requireNonNull(tag, "tag");
    String needle = tag.trim();
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      List<String> tags = ItemMarkers.getItemTags(item);
      for (String entry : tags) {
        if (entry != null && entry.equalsIgnoreCase(needle)) {
          return true;
        }
      }
      return false;
    };
  }

  public static ItemMatcher itemCategory(String category) {
    Objects.requireNonNull(category, "category");
    String needle = category.trim();
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      String current = ItemMarkers.getItemCategory(item);
      return current != null && current.equalsIgnoreCase(needle);
    };
  }

  public static <T, Z> ItemMatcher pdc(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(type, "type");
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
        return false;
      }
      PersistentDataContainer container = meta.getPersistentDataContainer();
      if (value == null) {
        return container.has(key, type);
      }
      Z stored = container.get(key, type);
      return Objects.equals(stored, value);
    };
  }

  public static ItemMatcher durabilityRange(Integer min, Integer max, boolean remaining) {
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      ItemMeta meta = item.getItemMeta();
      if (!(meta instanceof Damageable damageable)) {
        return false;
      }
      int value;
      if (remaining) {
        int maxDurability = item.getType().getMaxDurability();
        value = Math.max(0, maxDurability - damageable.getDamage());
      } else {
        value = Math.max(0, damageable.getDamage());
      }
      if (min != null && value < min) {
        return false;
      }
      if (max != null && value > max) {
        return false;
      }
      return true;
    };
  }

  public static ItemMatcher attribute(Attribute attribute, AttributeModifier.Operation op, Double min, Double max) {
    Objects.requireNonNull(attribute, "attribute");
    return (player, item) -> {
      if (item == null || item.getType().isAir()) {
        return false;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
        return false;
      }
      var modifiers = meta.getAttributeModifiers(attribute);
      if (modifiers == null || modifiers.isEmpty()) {
        return false;
      }
      double sum = 0.0;
      boolean matched = false;
      for (AttributeModifier modifier : modifiers) {
        if (modifier == null) {
          continue;
        }
        if (op != null && modifier.getOperation() != op) {
          continue;
        }
        matched = true;
        sum += modifier.getAmount();
      }
      if (!matched) {
        return false;
      }
      if (min != null && sum < min) {
        return false;
      }
      if (max != null && sum > max) {
        return false;
      }
      return true;
    };
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
