package dev.patric.dungeonsreborn.shops;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

public final class ShopEditorDraft {
  private String id;
  private String originalId;
  private String title;
  private boolean enabled = true;
  private ItemStack icon;
  private String permission;
  private double cooldownSeconds;
  private final List<String> worlds = new ArrayList<>();
  private Integer stockMin;
  private Integer stockMax;
  private Long restockSeconds;
  private final List<ShopTradeDraft> trades = new ArrayList<>();

  public String id() {
    return id;
  }

  public void id(String id) {
    this.id = id;
  }

  public String originalId() {
    return originalId;
  }

  public void originalId(String originalId) {
    this.originalId = originalId;
  }

  public String title() {
    return title;
  }

  public void title(String title) {
    this.title = title;
  }

  public boolean enabled() {
    return enabled;
  }

  public void enabled(boolean enabled) {
    this.enabled = enabled;
  }

  public ItemStack icon() {
    return icon == null ? null : icon.clone();
  }

  public void icon(ItemStack icon) {
    this.icon = icon == null ? null : icon.clone();
  }

  public String permission() {
    return permission;
  }

  public void permission(String permission) {
    this.permission = permission;
  }

  public double cooldownSeconds() {
    return cooldownSeconds;
  }

  public void cooldownSeconds(double cooldownSeconds) {
    this.cooldownSeconds = cooldownSeconds;
  }

  public List<String> worlds() {
    return worlds;
  }

  public Integer stockMin() {
    return stockMin;
  }

  public void stockMin(Integer stockMin) {
    this.stockMin = stockMin;
  }

  public Integer stockMax() {
    return stockMax;
  }

  public void stockMax(Integer stockMax) {
    this.stockMax = stockMax;
  }

  public Long restockSeconds() {
    return restockSeconds;
  }

  public void restockSeconds(Long restockSeconds) {
    this.restockSeconds = restockSeconds;
  }

  public List<ShopTradeDraft> trades() {
    return trades;
  }
}
