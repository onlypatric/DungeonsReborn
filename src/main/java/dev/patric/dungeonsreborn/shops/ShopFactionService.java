package dev.patric.dungeonsreborn.shops;

import java.util.UUID;

public interface ShopFactionService {
  boolean hasFaction(UUID playerId, String factionId, int minRank);

  default void addReputation(UUID playerId, String factionId, int amount) {
  }
}
