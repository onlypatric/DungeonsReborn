package dev.patric.dungeonsreborn.shops;

import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.effects.integration.ItemMatchers;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import net.kyori.adventure.text.Component;

public record ShopIngredientSpec(
    ShopIngredientType type,
    String itemId,
    Material material,
    ItemStack item,
    int amount,
    String tag,
    String category,
    ItemMatcher matcher,
    String label
) {
  public ShopIngredientSpec {
    if (type == null) {
      throw new IllegalArgumentException("ingredient type is required");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("ingredient amount must be > 0");
    }
    if (item != null) {
      item = item.clone();
    }
    switch (type) {
      case TOKEN -> {
      }
      case ITEM_ID -> {
        if (itemId == null || itemId.isBlank()) {
          throw new IllegalArgumentException("itemId is required");
        }
      }
      case MATERIAL -> {
        if (material == null) {
          throw new IllegalArgumentException("material is required");
        }
      }
      case ITEMSTACK -> {
        if (item == null) {
          throw new IllegalArgumentException("item is required");
        }
      }
      case TAG -> {
        if (tag == null || tag.isBlank()) {
          throw new IllegalArgumentException("tag is required");
        }
      }
      case CATEGORY -> {
        if (category == null || category.isBlank()) {
          throw new IllegalArgumentException("category is required");
        }
      }
      case MATCHER -> {
        if (matcher == null) {
          throw new IllegalArgumentException("matcher is required");
        }
      }
      case CURRENCY -> {
        if (matcher == null) {
          throw new IllegalArgumentException("currency matcher is required");
        }
      }
      case XP, CUSTOM_XP -> {
      }
    }
  }

  public ItemStack resolve(Function<String, ItemStack> itemResolver, ShopTokenSpec tokenSpec) {
    return switch (type) {
      case TOKEN -> tokenSpec == null || tokenSpec.item() == null ? null : withAmount(tokenSpec.item(), amount);
      case ITEM_ID -> itemResolver == null ? null : withAmount(itemResolver.apply(itemId), amount);
      case MATERIAL -> new ItemStack(material, amount);
      case ITEMSTACK -> withAmount(item, amount);
      case TAG, CATEGORY, MATCHER, CURRENCY, XP, CUSTOM_XP -> {
        if (item != null) {
          yield withAmount(item, amount);
        }
        if (material != null) {
          yield new ItemStack(material, amount);
        }
        if (itemId != null && itemResolver != null) {
          yield withAmount(itemResolver.apply(itemId), amount);
        }
        yield null;
      }
    };
  }

  public boolean matches(ItemStack stack, Function<String, ItemStack> itemResolver, ShopTokenSpec tokenSpec) {
    if (stack == null || stack.getType().isAir()) {
      return false;
    }
    return switch (type) {
      case TOKEN -> {
        ItemStack resolved = resolve(itemResolver, tokenSpec);
        yield resolved != null && ItemMatchers.similar(resolved).matches(null, stack);
      }
      case ITEM_ID -> ItemMatchers.itemId(itemId).matches(null, stack);
      case MATERIAL -> stack.getType() == material;
      case ITEMSTACK -> ItemMatchers.similar(item).matches(null, stack);
      case TAG -> {
        String rawTag = tag == null ? "" : tag.trim();
        java.util.List<String> tags = ItemMarkers.getItemTags(stack);
        yield tags.stream().anyMatch(entry -> entry != null && entry.equalsIgnoreCase(rawTag));
      }
      case CATEGORY -> {
        String rawCategory = category == null ? "" : category.trim();
        String current = ItemMarkers.getItemCategory(stack);
        yield current != null && current.equalsIgnoreCase(rawCategory);
      }
      case MATCHER, CURRENCY -> matcher.matches(null, stack);
      case XP, CUSTOM_XP -> false;
    };
  }

  public String displayLabel(Function<String, ItemStack> itemResolver, ShopTokenSpec tokenSpec) {
    if (label != null && !label.isBlank()) {
      return label;
    }
    if (type == ShopIngredientType.TAG && tag != null) {
      return tag;
    }
    if (type == ShopIngredientType.CATEGORY && category != null) {
      return category;
    }
    if (type == ShopIngredientType.CURRENCY && itemId != null) {
      return itemId;
    }
    if (type == ShopIngredientType.XP) {
      return "XP";
    }
    if (type == ShopIngredientType.CUSTOM_XP) {
      return "Custom XP";
    }
    ItemStack resolved = resolve(itemResolver, tokenSpec);
    if (resolved == null) {
      if (type == ShopIngredientType.TOKEN) {
        return "Token";
      }
      if (type == ShopIngredientType.ITEM_ID && itemId != null) {
        return itemId;
      }
      if (type == ShopIngredientType.MATERIAL && material != null) {
        return material.name().toLowerCase(java.util.Locale.ROOT);
      }
      return "?";
    }
    ItemMeta meta = resolved.getItemMeta();
    if (meta != null && meta.hasDisplayName()) {
      Component display = meta.displayName();
      if (display != null) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(display);
        if (plain != null && !plain.isBlank()) {
          return plain;
        }
      }
    }
    return resolved.getType().name().toLowerCase(java.util.Locale.ROOT);
  }

  public ShopIngredientSpec withAmount(int amount) {
    return new ShopIngredientSpec(type, itemId, material, item, amount, tag, category, matcher, label);
  }

  private static ItemStack withAmount(ItemStack base, int amount) {
    if (base == null) {
      return null;
    }
    ItemStack out = base.clone();
    out.setAmount(amount);
    return out;
  }
}
