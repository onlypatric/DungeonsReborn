package dev.patric.dungeonsreborn.crafting;

import org.bukkit.inventory.ItemStack;

public final class CraftingOutputSpec {
  private final String itemId;
  private final ItemStack item;
  private final int amount;

  public CraftingOutputSpec(String itemId, ItemStack item, int amount) {
    this.itemId = itemId;
    this.item = item == null ? null : item.clone();
    this.amount = Math.max(1, amount);
  }

  public String itemId() {
    return itemId;
  }

  public ItemStack item() {
    return item == null ? null : item.clone();
  }

  public int amount() {
    return amount;
  }

  public boolean isDefined() {
    return itemId != null || item != null;
  }

  public CraftingOutputSpec withAmount(int nextAmount) {
    return new CraftingOutputSpec(itemId, item, nextAmount);
  }
}
