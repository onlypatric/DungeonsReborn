package dev.patric.dungeonsreborn.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

public final class GuiItem {
  /**
   * Small fluent builder around an {@link ItemStack} for GUI usage.
   * <p>
   * {@link #build()} always returns a clone so the internal instance stays private.
   */
  private static final NamespacedKey LEGACY_GLINT_KEY = new NamespacedKey("dungeonsreborn", "guiitem_glint");
  private static volatile NamespacedKey defaultGlintKey = LEGACY_GLINT_KEY;
  private static final byte GLINT_MARKER_V2 = 16;
  private static final byte GLINT_FLAG_ADDED_DUMMY_ENCHANT = 1;
  private static final byte GLINT_FLAG_ADDED_HIDE_ENCHANTS = 2;

  private final ItemStack item;

  public GuiItem(Material material) {
    this(new ItemStack(Objects.requireNonNull(material, "material")));
  }

  public GuiItem(ItemStack base) {
    Objects.requireNonNull(base, "base");
    this.item = base.clone();
  }

  public static GuiItem of(Material material) {
    return new GuiItem(material);
  }

  public static GuiItem of(ItemStack base) {
    return new GuiItem(base);
  }

  public GuiItem amount(int amount) {
    item.setAmount(Math.max(1, amount));
    return this;
  }

  public GuiItem displayName(Component name) {
    return editMeta(meta -> meta.displayName(Objects.requireNonNull(name, "name")));
  }

  public GuiItem lore(List<Component> lore) {
    Objects.requireNonNull(lore, "lore");
    return editMeta(meta -> meta.lore(List.copyOf(lore)));
  }

  public GuiItem clearLore() {
    return editMeta(meta -> meta.lore(null));
  }

  public GuiItem addLoreLine(Component line) {
    Objects.requireNonNull(line, "line");
    return editMeta(meta -> {
      List<Component> lore = meta.lore();
      List<Component> next = new ArrayList<>(lore == null ? List.of() : lore);
      next.add(line);
      meta.lore(next);
    });
  }

  public GuiItem flags(ItemFlag... flags) {
    Objects.requireNonNull(flags, "flags");
    return editMeta(meta -> meta.addItemFlags(flags));
  }

  /**
   * Convenience helper to hide/show most vanilla tooltip sections (attributes, enchants, etc.).
   */
  public GuiItem hideItemFlags(boolean hide) {
    return hide ? flags(ItemFlag.values()) : clearFlags(ItemFlag.values());
  }

  public GuiItem clearFlags(ItemFlag... flags) {
    Objects.requireNonNull(flags, "flags");
    return editMeta(meta -> meta.removeItemFlags(flags));
  }

  public GuiItem unbreakable(boolean value) {
    return editMeta(meta -> meta.setUnbreakable(value));
  }

  public GuiItem enchant(Enchantment enchantment, int level, boolean ignoreRestrictions) {
    Objects.requireNonNull(enchantment, "enchantment");
    return editMeta(meta -> meta.addEnchant(enchantment, level, ignoreRestrictions));
  }

  public GuiItem clearEnchant(Enchantment enchantment) {
    Objects.requireNonNull(enchantment, "enchantment");
    return editMeta(meta -> meta.removeEnchant(enchantment));
  }

  /**
   * Sets the default key used by {@link #glint(boolean)}.
   * <p>
   * This is useful when embedding this GUI library in another plugin, or when migrating from an older namespace.
   */
  public static void setDefaultGlintKey(NamespacedKey key) {
    defaultGlintKey = Objects.requireNonNull(key, "key");
  }

  public static NamespacedKey defaultGlintKey() {
    return defaultGlintKey;
  }

  public GuiItem glint(boolean enabled) {
    return glint(defaultGlintKey, enabled);
  }

  /**
   * Toggles a glint effect using the provided {@link NamespacedKey} marker.
   * <p>
   * If you enable glint with a custom key, use the same key to disable it later.
   */
  public GuiItem glint(NamespacedKey key, boolean enabled) {
    Objects.requireNonNull(key, "key");
    return editMeta(meta -> {
      if (enabled) {
        byte marker = GLINT_MARKER_V2;
        if (!meta.hasEnchants()) {
          meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
          marker |= GLINT_FLAG_ADDED_DUMMY_ENCHANT;
        }
        if (!meta.getItemFlags().contains(ItemFlag.HIDE_ENCHANTS)) {
          meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
          marker |= GLINT_FLAG_ADDED_HIDE_ENCHANTS;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, marker);
        return;
      }

      removeGlintMarker(meta, key);
      if (LEGACY_GLINT_KEY != key) {
        removeGlintMarker(meta, LEGACY_GLINT_KEY);
      }
    });
  }

  public ItemStack build() {
    return item.clone();
  }

  private static boolean removeGlintMarker(ItemMeta meta, NamespacedKey key) {
    Byte marker = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
    if (marker == null) {
      return false;
    }
    byte raw = marker.byteValue();
    boolean v2 = (raw & GLINT_MARKER_V2) != 0;
    if (!v2) {
      // Legacy markers: 0/1 meant "no dummy"/"dummy added" and we always removed HIDE_ENCHANTS.
      if (raw == 1) {
        meta.removeEnchant(Enchantment.LUCK_OF_THE_SEA);
      }
      meta.getPersistentDataContainer().remove(key);
      meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
      return true;
    }

    if ((raw & GLINT_FLAG_ADDED_DUMMY_ENCHANT) != 0) {
      meta.removeEnchant(Enchantment.LUCK_OF_THE_SEA);
    }
    if ((raw & GLINT_FLAG_ADDED_HIDE_ENCHANTS) != 0) {
      meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    meta.getPersistentDataContainer().remove(key);
    return true;
  }

  private GuiItem editMeta(Consumer<ItemMeta> edit) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      meta = Bukkit.getItemFactory().getItemMeta(item.getType());
    }
    if (meta == null) {
      return this;
    }
    edit.accept(meta);
    item.setItemMeta(meta);
    return this;
  }
}
