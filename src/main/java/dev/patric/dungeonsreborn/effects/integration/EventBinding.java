package dev.patric.dungeonsreborn.effects.integration;

import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

public record EventBinding(
    String id,
    String abilityId,
    EventTrigger trigger,
    Predicate<Player> playerPredicate,
    boolean requireSneaking,
    String requiredPermission
) {
  public EventBinding {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(trigger, "trigger");
    playerPredicate = playerPredicate == null ? player -> true : playerPredicate;
  }
}
