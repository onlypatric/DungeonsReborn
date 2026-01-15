package dev.patric.dungeonsreborn.effects.upgrades;

import java.util.Objects;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;

public record UpgradeAttributeSpec(
    Attribute attribute,
    AttributeModifier.Operation operation,
    double amount,
    EquipmentSlotGroup slotGroup
) {
  public UpgradeAttributeSpec {
    Objects.requireNonNull(attribute, "attribute");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(slotGroup, "slotGroup");
    if (!Double.isFinite(amount)) {
      throw new IllegalArgumentException("amount must be finite");
    }
  }
}
