package dev.patric.dungeonsreborn.effects.combat;

import java.util.Objects;

public record CombatEventBinding(
    String id,
    String abilityId,
    CombatEventType eventType,
    double chance,
    long cooldownTicks,
    CombatCooldownScope cooldownScope,
    boolean requireSneaking,
    String requiredPermission,
    CombatEventFilters filters,
    CombatEventTargetBind targetBind) {

  public CombatEventBinding {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(eventType, "eventType");
    if (!Double.isFinite(chance)) {
      chance = 1.0;
    }
    if (chance <= 0.0) {
      chance = 0.0;
    } else if (chance > 1.0) {
      chance = chance / 100.0;
      if (chance > 1.0) {
        chance = 1.0;
      }
    }
    cooldownTicks = Math.max(0L, cooldownTicks);
    cooldownScope = cooldownScope == null ? CombatCooldownScope.PER_PLAYER : cooldownScope;
    filters = filters == null ? CombatEventFilters.none() : filters;
    targetBind = targetBind == null ? CombatEventTargetBind.EVENT_PRIMARY : targetBind;
  }
}

