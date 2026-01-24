package dev.patric.dungeonsreborn.effects.minions;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.mobs.MobParticlesSpec;
import org.bukkit.attribute.Attribute;

public record MinionSpec(String id, String mobId, int count, long durationTicks, UUID ownerId, double spawnRadius,
                         MinionSummonSpec summonSpec,
                         MinionScaling scaling, Map<DamageType, Double> resistances, Set<DamageType> immunities,
                         boolean despawnOnOwnerLogout, boolean persistent, MinionMode mode,
                         MinionTargetRules targetRules, List<MinionPassiveSpec> passives,
                         List<MinionSpecialAttackSpec> specialAttacks, Map<Attribute, Double> statOverrides,
                         MinionOwnerScalingSpec ownerScaling, MinionScalingLimits scalingLimits,
                         String mainAttackOverride, String secondaryAttackOverride, boolean disableBasePassives,
                         boolean disableBaseAttacks, boolean disableBaseAi, boolean sharePotionEffects,
                         String nameOverride, Boolean glowOverride,
                         MobParticlesSpec particles, long particlesPeriodTicks) {
  public MinionSpec {
    Objects.requireNonNull(mobId, "mobId");
    if (id == null || id.isBlank()) {
      id = Ids.normalize(mobId);
    } else {
      id = Ids.normalize(id);
    }
    mobId = Ids.normalize(mobId);
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (durationTicks <= 0) {
      throw new IllegalArgumentException("durationTicks must be > 0");
    }
    if (!Double.isFinite(spawnRadius) || spawnRadius < 0.0) {
      throw new IllegalArgumentException("spawnRadius must be >= 0");
    }
    if (summonSpec == null) {
      summonSpec = MinionSummonSpec.DEFAULT;
    }
    if (scaling == null) {
      scaling = MinionScaling.NONE;
    }
    if (resistances == null) {
      resistances = Map.of();
    } else {
      resistances = Map.copyOf(resistances);
      for (Map.Entry<DamageType, Double> entry : resistances.entrySet()) {
        if (entry.getKey() == null) {
          throw new IllegalArgumentException("resistances contains null type");
        }
        Double value = entry.getValue();
        if (value == null || !Double.isFinite(value) || value < 0.0) {
          throw new IllegalArgumentException("resistance multiplier must be >= 0");
        }
      }
    }
    if (immunities == null) {
      immunities = Set.of();
    } else {
      for (DamageType type : immunities) {
        if (type == null) {
          throw new IllegalArgumentException("immunities contains null type");
        }
      }
      immunities = Set.copyOf(immunities);
    }
    if (passives == null) {
      passives = List.of();
    } else {
      for (MinionPassiveSpec passive : passives) {
        if (passive == null) {
          throw new IllegalArgumentException("passives contains null");
        }
      }
      passives = List.copyOf(passives);
    }
    if (specialAttacks == null) {
      specialAttacks = List.of();
    } else {
      for (MinionSpecialAttackSpec special : specialAttacks) {
        if (special == null) {
          throw new IllegalArgumentException("specialAttacks contains null");
        }
      }
      specialAttacks = List.copyOf(specialAttacks);
    }
    if (statOverrides == null) {
      statOverrides = Map.of();
    } else {
      Map<Attribute, Double> cleaned = new LinkedHashMap<>();
      for (Map.Entry<Attribute, Double> entry : statOverrides.entrySet()) {
        Attribute attr = entry.getKey();
        Double value = entry.getValue();
        if (attr == null || value == null || !Double.isFinite(value)) {
          continue;
        }
        cleaned.put(attr, value);
      }
      statOverrides = Map.copyOf(cleaned);
    }
    if (mainAttackOverride != null && mainAttackOverride.isBlank()) {
      mainAttackOverride = null;
    }
    if (secondaryAttackOverride != null && secondaryAttackOverride.isBlank()) {
      secondaryAttackOverride = null;
    }
    if (nameOverride != null && nameOverride.isBlank()) {
      nameOverride = null;
    }
    if (targetRules == null) {
      targetRules = MinionTargetRules.DEFAULT;
    }
    if (ownerScaling == null) {
      ownerScaling = MinionOwnerScalingSpec.NONE;
    }
    if (scalingLimits == null) {
      scalingLimits = MinionScalingLimits.NONE;
    }
    if (particlesPeriodTicks < 0L) {
      throw new IllegalArgumentException("particlesPeriodTicks must be >= 0");
    }
  }
}
