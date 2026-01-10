package dev.patric.dungeonsreborn.effects.minions;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.damage.DamageType;

public record MinionSpec(String id, String mobId, int count, long durationTicks, UUID ownerId, double spawnRadius,
                         MinionScaling scaling, Map<DamageType, Double> resistances, Set<DamageType> immunities,
                         boolean despawnOnOwnerLogout, MinionMode mode, List<MinionPassiveSpec> passives,
                         List<MinionSpecialAttackSpec> specialAttacks) {
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
  }
}
