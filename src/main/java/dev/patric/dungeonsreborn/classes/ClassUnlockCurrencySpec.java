package dev.patric.dungeonsreborn.classes;

public record ClassUnlockCurrencySpec(String id, int amount) {
  public ClassUnlockCurrencySpec {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("currency id is required");
    }
    if (amount < 0) {
      throw new IllegalArgumentException("amount must be >= 0");
    }
  }
}
