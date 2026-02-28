package dev.patric.dungeonsreborn.effects.combat;

import java.util.Objects;

public record CombatEventBinding(
    String id,
    String abilityId,
    CombatEventType eventType,
    CombatEventPhase phase,
    boolean cancelEvent,
    double chance,
    long cooldownTicks,
    CombatCooldownScope cooldownScope,
    boolean requireSneaking,
    String requiredPermission,
    CombatEventFilters filters,
    CombatEventTargetBind targetBind,
    CombatEventOriginBind originBind) {

  public CombatEventBinding {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(eventType, "eventType");
    phase = phase == null ? (eventType.isPreEvent() ? CombatEventPhase.PRE : CombatEventPhase.POST) : phase;
    if (cancelEvent && !eventType.isPreEvent()) {
      throw new IllegalArgumentException("cancelEvent is only supported for PRE projectile events: " + eventType);
    }
    if ((abilityId == null || abilityId.isBlank()) && !cancelEvent) {
      throw new IllegalArgumentException("abilityId is required unless cancelEvent=true");
    }
    if (abilityId != null && abilityId.isBlank()) {
      abilityId = null;
    }
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
    originBind = originBind == null ? CombatEventOriginBind.IMPACT : originBind;
  }
}
