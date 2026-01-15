package dev.patric.dungeonsreborn.kits;

public record KitRewards(int xp, int tokens, int compressed, int pallet) {
  public KitRewards {
    if (xp < 0) {
      throw new IllegalArgumentException("xp must be >= 0");
    }
    if (tokens < 0) {
      throw new IllegalArgumentException("tokens must be >= 0");
    }
    if (compressed < 0) {
      throw new IllegalArgumentException("compressed must be >= 0");
    }
    if (pallet < 0) {
      throw new IllegalArgumentException("pallet must be >= 0");
    }
  }

  public static KitRewards none() {
    return new KitRewards(0, 0, 0, 0);
  }

  public boolean isEmpty() {
    return xp <= 0 && tokens <= 0 && compressed <= 0 && pallet <= 0;
  }
}
