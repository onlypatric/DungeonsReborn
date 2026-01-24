package dev.patric.dungeonsreborn.crafting;

import java.util.Objects;

public final class CraftingCostSpec {
  public enum Type {
    MANA,
    RESOURCE,
    TOKENS,
    DURABILITY,
    ITEM
  }

  private final Type type;
  private final double amount;
  private final String resourceId;
  private final String tokenTier;
  private final boolean allowBreak;
  private final CraftingIngredientSpec item;
  private final String message;

  private CraftingCostSpec(Type type,
                           double amount,
                           String resourceId,
                           String tokenTier,
                           boolean allowBreak,
                           CraftingIngredientSpec item,
                           String message) {
    this.type = Objects.requireNonNull(type, "type");
    this.amount = amount;
    this.resourceId = resourceId;
    this.tokenTier = tokenTier;
    this.allowBreak = allowBreak;
    this.item = item;
    this.message = message;
  }

  public static CraftingCostSpec mana(double amount, String message) {
    return new CraftingCostSpec(Type.MANA, amount, null, null, false, null, message);
  }

  public static CraftingCostSpec resource(String resourceId, double amount, String message) {
    return new CraftingCostSpec(Type.RESOURCE, amount, resourceId, null, false, null, message);
  }

  public static CraftingCostSpec tokens(String tokenTier, double amount, String message) {
    return new CraftingCostSpec(Type.TOKENS, amount, null, tokenTier, false, null, message);
  }

  public static CraftingCostSpec durability(double amount, boolean allowBreak, String message) {
    return new CraftingCostSpec(Type.DURABILITY, amount, null, null, allowBreak, null, message);
  }

  public static CraftingCostSpec item(CraftingIngredientSpec item, String message) {
    return new CraftingCostSpec(Type.ITEM, item == null ? 0.0 : item.amount(), null, null, false, item, message);
  }

  public Type type() {
    return type;
  }

  public double amount() {
    return amount;
  }

  public String resourceId() {
    return resourceId;
  }

  public String tokenTier() {
    return tokenTier;
  }

  public boolean allowBreak() {
    return allowBreak;
  }

  public CraftingIngredientSpec item() {
    return item;
  }

  public String message() {
    return message;
  }
}
