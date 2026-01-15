package dev.patric.dungeonsreborn.kits;

import java.util.List;

public record KitSpec(
    String id,
    String title,
    String permission,
    boolean oneTime,
    long cooldownSeconds,
    List<KitItemSpec> items,
    KitRewards rewards
) {
  public KitSpec {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
    if (items == null) {
      items = List.of();
    } else {
      items = List.copyOf(items);
    }
    if (rewards == null) {
      rewards = KitRewards.none();
    }
    cooldownSeconds = Math.max(0L, cooldownSeconds);
  }
}
