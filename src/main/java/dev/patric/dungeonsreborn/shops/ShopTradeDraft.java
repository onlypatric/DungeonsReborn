package dev.patric.dungeonsreborn.shops;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

public final class ShopTradeDraft {
  private ItemStack buyA;
  private ItemStack buyB;
  private ItemStack sell;
  private int maxUses;
  private int minLevel;
  private boolean experienceReward;
  private float priceMultiplier;
  private List<String> previewLore = new ArrayList<>();
  private ShopDynamicPriceSpec dynamicPrice;

  public ItemStack buyA() {
    return buyA == null ? null : buyA.clone();
  }

  public void buyA(ItemStack buyA) {
    this.buyA = buyA == null ? null : buyA.clone();
  }

  public ItemStack buyB() {
    return buyB == null ? null : buyB.clone();
  }

  public void buyB(ItemStack buyB) {
    this.buyB = buyB == null ? null : buyB.clone();
  }

  public ItemStack sell() {
    return sell == null ? null : sell.clone();
  }

  public void sell(ItemStack sell) {
    this.sell = sell == null ? null : sell.clone();
  }

  public int maxUses() {
    return maxUses;
  }

  public void maxUses(int maxUses) {
    this.maxUses = maxUses;
  }

  public int minLevel() {
    return minLevel;
  }

  public void minLevel(int minLevel) {
    this.minLevel = Math.max(0, minLevel);
  }

  public boolean experienceReward() {
    return experienceReward;
  }

  public void experienceReward(boolean experienceReward) {
    this.experienceReward = experienceReward;
  }

  public float priceMultiplier() {
    return priceMultiplier;
  }

  public void priceMultiplier(float priceMultiplier) {
    this.priceMultiplier = priceMultiplier;
  }

  public List<String> previewLore() {
    return new ArrayList<>(previewLore);
  }

  public void previewLore(List<String> previewLore) {
    this.previewLore = previewLore == null ? new ArrayList<>() : new ArrayList<>(previewLore);
  }

  public ShopDynamicPriceSpec dynamicPrice() {
    return dynamicPrice;
  }

  public void dynamicPrice(ShopDynamicPriceSpec dynamicPrice) {
    this.dynamicPrice = dynamicPrice;
  }
}
