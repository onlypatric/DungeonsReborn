package dev.patric.dungeonsreborn.gui;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class GuiText {
  private Component value;

  private GuiText(Component value) {
    this.value = Objects.requireNonNull(value, "value");
  }

  public static GuiText of(Component value) {
    return new GuiText(value);
  }

  public static GuiText text(String value) {
    Objects.requireNonNull(value, "value");
    return of(Component.text(value));
  }

  public static GuiText translatable(String key) {
    Objects.requireNonNull(key, "key");
    return of(Component.translatable(key));
  }

  public static Component itemName(Material material) {
    Objects.requireNonNull(material, "material");
    return Component.translatable(material.translationKey());
  }

  public static Component itemName(Material material, NamedTextColor color) {
    return itemName(material).color(color);
  }

  public static Component itemName(Material material, NamedTextColor color, Component prefix, Component suffix) {
    return withPrefixSuffix(itemName(material, color), prefix, suffix);
  }

  public static Component itemName(Material material, Component prefix, Component suffix) {
    return withPrefixSuffix(itemName(material), prefix, suffix);
  }

  /**
   * Returns the vanilla translatable potion name, e.g. "Potion of Swiftness" / "Long Potion of Swiftness".
   */
  public static Component potionName(PotionType type) {
    Objects.requireNonNull(type, "type");
    NamespacedKey key = type.getKey();
    if (key == null) {
      return Component.text(type.name());
    }
    return Component.translatable("item.minecraft.potion.effect." + key.getKey());
  }

  public static Component potionName(PotionType type, NamedTextColor color) {
    return potionName(type).color(color);
  }

  public static Component potionName(PotionType type, NamedTextColor color, Component prefix, Component suffix) {
    return withPrefixSuffix(potionName(type, color), prefix, suffix);
  }

  public static Component potionName(PotionType type, Component prefix, Component suffix) {
    return withPrefixSuffix(potionName(type), prefix, suffix);
  }

  public static Component withPrefix(Component base, Component prefix) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(prefix, "prefix");
    return prefix.append(base);
  }

  public static Component withPrefix(Component base, String prefix) {
    Objects.requireNonNull(prefix, "prefix");
    return withPrefix(base, Component.text(prefix));
  }

  public static Component withSuffix(Component base, Component suffix) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(suffix, "suffix");
    return base.append(suffix);
  }

  public static Component withSuffix(Component base, String suffix) {
    Objects.requireNonNull(suffix, "suffix");
    return withSuffix(base, Component.text(suffix));
  }

  public static Component withPrefixSuffix(Component base, Component prefix, Component suffix) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(prefix, "prefix");
    Objects.requireNonNull(suffix, "suffix");
    return prefix.append(base).append(suffix);
  }

  public static Component withPrefixSuffix(Component base, String prefix, String suffix) {
    Objects.requireNonNull(prefix, "prefix");
    Objects.requireNonNull(suffix, "suffix");
    return withPrefixSuffix(base, Component.text(prefix), Component.text(suffix));
  }

  public static GuiText itemNameText(Material material) {
    return of(itemName(material));
  }

  public static GuiText potionNameText(PotionType type) {
    return of(potionName(type));
  }

  public GuiText prefix(Component prefix) {
    Objects.requireNonNull(prefix, "prefix");
    this.value = prefix.append(this.value);
    return this;
  }

  public GuiText prefix(String prefix) {
    Objects.requireNonNull(prefix, "prefix");
    return prefix(Component.text(prefix));
  }

  public GuiText suffix(Component suffix) {
    Objects.requireNonNull(suffix, "suffix");
    this.value = this.value.append(suffix);
    return this;
  }

  public GuiText suffix(String suffix) {
    Objects.requireNonNull(suffix, "suffix");
    return suffix(Component.text(suffix));
  }

  public GuiText append(Component other) {
    Objects.requireNonNull(other, "other");
    this.value = this.value.append(other);
    return this;
  }

  public GuiText color(TextColor color) {
    Objects.requireNonNull(color, "color");
    this.value = this.value.color(color);
    return this;
  }

  public GuiText decorate(TextDecoration... decorations) {
    Objects.requireNonNull(decorations, "decorations");
    this.value = this.value.decorate(decorations);
    return this;
  }

  public Component build() {
    return this.value;
  }
}
