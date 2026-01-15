package dev.patric.dungeonsreborn.kits;

import java.util.OptionalLong;
import java.util.UUID;

public interface KitClaimRepository {
  OptionalLong lastClaim(UUID uuid, String kitId);

  void markClaimed(UUID uuid, String kitId, long whenMillis);
}
