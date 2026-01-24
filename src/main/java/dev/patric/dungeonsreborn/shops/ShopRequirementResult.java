package dev.patric.dungeonsreborn.shops;

import net.kyori.adventure.text.Component;

public record ShopRequirementResult(boolean allowed, String reason, Component message) {
  public static ShopRequirementResult allow() {
    return new ShopRequirementResult(true, null, null);
  }

  public static ShopRequirementResult deny(String reason, Component message) {
    return new ShopRequirementResult(false, reason, message);
  }
}
