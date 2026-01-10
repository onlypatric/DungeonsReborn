package dev.patric.dungeonsreborn.effects.integration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.bukkit.inventory.EquipmentSlot;

public record PassiveBinding(
    String id,
    String abilityId,
    ItemMatcher itemMatcher,
    boolean requireSneaking,
    String requiredPermission,
    long periodTicks,
    Set<EquipmentSlot> slots) {

  public PassiveBinding {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(itemMatcher, "itemMatcher");
    if (periodTicks <= 0) {
      throw new IllegalArgumentException("periodTicks must be > 0");
    }
    Objects.requireNonNull(slots, "slots");
    if (slots.isEmpty()) {
      throw new IllegalArgumentException("slots must not be empty");
    }
    slots = Collections.unmodifiableSet(EnumSet.copyOf(slots));
  }
}
