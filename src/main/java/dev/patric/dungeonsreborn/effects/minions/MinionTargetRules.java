package dev.patric.dungeonsreborn.effects.minions;

public record MinionTargetRules(boolean allowPvp, boolean allowPartyTargets,
                                boolean shareOwnerAggro, double maxDistanceFromOwner) {
  public static final MinionTargetRules DEFAULT = new MinionTargetRules(false, false, true, 0.0);
}
