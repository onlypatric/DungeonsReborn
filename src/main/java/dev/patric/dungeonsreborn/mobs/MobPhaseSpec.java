package dev.patric.dungeonsreborn.mobs;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.mobs.ai.MobAiPhaseMergeMode;

public record MobPhaseSpec(String id, double healthBelow, MobAttackSpec mainAttack, MobAttackSpec secondaryAttack,
                           List<MobPassiveSpec> passives, ItemStack mainHand, ItemStack offHand, ItemStack head,
                           ItemStack chest, ItemStack legs, ItemStack feet, Double scaleMultiplier,
                           Boolean collidable, MobModelSpec modelSpec, MobStyleSpec style, MobVisualSpec visualSpec,
                           MobAiSpec aiSpec, MobAiPhaseMergeMode aiMergeMode) {
  public MobPhaseSpec {
    Objects.requireNonNull(id, "id");
    if (!Double.isFinite(healthBelow) || healthBelow <= 0.0 || healthBelow > 1.0) {
      throw new IllegalArgumentException("healthBelow must be in (0, 1]");
    }
    if (scaleMultiplier != null && (!Double.isFinite(scaleMultiplier) || scaleMultiplier <= 0.0)) {
      throw new IllegalArgumentException("scaleMultiplier must be > 0");
    }
    if (passives != null) {
      for (MobPassiveSpec passive : passives) {
        if (passive == null) {
          throw new IllegalArgumentException("passives contains null");
        }
      }
      passives = List.copyOf(passives);
    }
    mainHand = cloneItem(mainHand);
    offHand = cloneItem(offHand);
    head = cloneItem(head);
    chest = cloneItem(chest);
    legs = cloneItem(legs);
    feet = cloneItem(feet);
    aiMergeMode = aiMergeMode == null ? MobAiPhaseMergeMode.PATCH : aiMergeMode;
  }

  private static ItemStack cloneItem(ItemStack item) {
    return item == null ? null : item.clone();
  }
}
