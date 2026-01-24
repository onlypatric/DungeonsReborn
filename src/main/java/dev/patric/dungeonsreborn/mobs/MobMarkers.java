package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class MobMarkers {
  public static final NamespacedKey MOB_ID = new NamespacedKey("dungeonsreborn", "mob_id");
  public static final NamespacedKey MOB_OWNER = new NamespacedKey("dungeonsreborn", "mob_owner");
  public static final NamespacedKey MOB_VARIANT = new NamespacedKey("dungeonsreborn", "mob_variant");
  public static final NamespacedKey MOB_TRAIT = new NamespacedKey("dungeonsreborn", "mob_trait");
  public static final NamespacedKey MOB_MODEL = new NamespacedKey("dungeonsreborn", "mob_model");
  public static final NamespacedKey MOB_ANIMATION = new NamespacedKey("dungeonsreborn", "mob_animation");
  public static final NamespacedKey MOB_ANIM_SPEED = new NamespacedKey("dungeonsreborn", "mob_anim_speed");
  public static final NamespacedKey MINION_ID = new NamespacedKey("dungeonsreborn", "minion_id");
  public static final NamespacedKey MINION_PERSIST = new NamespacedKey("dungeonsreborn", "minion_persist");
  public static final NamespacedKey MINION_EXPIRES_AT = new NamespacedKey("dungeonsreborn", "minion_expires_at");
  public static final NamespacedKey MINION_DESPAWN_ON_LOGOUT = new NamespacedKey("dungeonsreborn", "minion_despawn_on_logout");
  public static final NamespacedKey MINION_MAIN_ATTACK = new NamespacedKey("dungeonsreborn", "minion_main_attack");
  public static final NamespacedKey MINION_SECONDARY_ATTACK = new NamespacedKey("dungeonsreborn", "minion_secondary_attack");
  public static final NamespacedKey MINION_DISABLE_BASE_PASSIVES = new NamespacedKey("dungeonsreborn", "minion_disable_base_passives");
  public static final NamespacedKey MINION_DISABLE_BASE_ATTACKS = new NamespacedKey("dungeonsreborn", "minion_disable_base_attacks");
  public static final NamespacedKey MINION_DISABLE_BASE_AI = new NamespacedKey("dungeonsreborn", "minion_disable_base_ai");
  public static final NamespacedKey MINION_MODE = new NamespacedKey("dungeonsreborn", "minion_mode");
  public static final NamespacedKey MINION_ALLOW_PVP = new NamespacedKey("dungeonsreborn", "minion_allow_pvp");
  public static final NamespacedKey MINION_ALLOW_PARTY = new NamespacedKey("dungeonsreborn", "minion_allow_party_targets");
  public static final NamespacedKey MINION_SHARE_OWNER_AGGRO = new NamespacedKey("dungeonsreborn", "minion_share_owner_aggro");
  public static final NamespacedKey MINION_TARGET_RADIUS = new NamespacedKey("dungeonsreborn", "minion_target_radius");
  public static final NamespacedKey MINION_NAME_OVERRIDE = new NamespacedKey("dungeonsreborn", "minion_name_override");
  public static final NamespacedKey MINION_GLOW_OVERRIDE = new NamespacedKey("dungeonsreborn", "minion_glow_override");

  private MobMarkers() {
  }

  public static String getMobId(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String raw = pdc.get(MOB_ID, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static UUID getOwner(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String raw = pdc.get(MOB_OWNER, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  public static void setMobId(Entity entity, String id) {
    Objects.requireNonNull(entity, "entity");
    if (id == null || id.isBlank()) {
      entity.getPersistentDataContainer().remove(MOB_ID);
      return;
    }
    entity.getPersistentDataContainer().set(MOB_ID, PersistentDataType.STRING, id);
  }

  public static void setOwner(Entity entity, UUID ownerId) {
    Objects.requireNonNull(entity, "entity");
    if (ownerId == null) {
      entity.getPersistentDataContainer().remove(MOB_OWNER);
      return;
    }
    entity.getPersistentDataContainer().set(MOB_OWNER, PersistentDataType.STRING, ownerId.toString());
  }

  public static String getVariant(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String raw = pdc.get(MOB_VARIANT, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setVariant(Entity entity, String variantId) {
    Objects.requireNonNull(entity, "entity");
    if (variantId == null || variantId.isBlank()) {
      entity.getPersistentDataContainer().remove(MOB_VARIANT);
      return;
    }
    entity.getPersistentDataContainer().set(MOB_VARIANT, PersistentDataType.STRING, variantId);
  }

  public static String getTrait(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String raw = pdc.get(MOB_TRAIT, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setTrait(Entity entity, String traitId) {
    Objects.requireNonNull(entity, "entity");
    if (traitId == null || traitId.isBlank()) {
      entity.getPersistentDataContainer().remove(MOB_TRAIT);
      return;
    }
    entity.getPersistentDataContainer().set(MOB_TRAIT, PersistentDataType.STRING, traitId);
  }

  public static String getModelId(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String raw = pdc.get(MOB_MODEL, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setModelId(Entity entity, String modelId) {
    Objects.requireNonNull(entity, "entity");
    if (modelId == null || modelId.isBlank()) {
      entity.getPersistentDataContainer().remove(MOB_MODEL);
      return;
    }
    entity.getPersistentDataContainer().set(MOB_MODEL, PersistentDataType.STRING, modelId);
  }

  public static String getAnimationId(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String raw = pdc.get(MOB_ANIMATION, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setAnimationId(Entity entity, String animationId) {
    Objects.requireNonNull(entity, "entity");
    if (animationId == null || animationId.isBlank()) {
      entity.getPersistentDataContainer().remove(MOB_ANIMATION);
      return;
    }
    entity.getPersistentDataContainer().set(MOB_ANIMATION, PersistentDataType.STRING, animationId);
  }

  public static Double getAnimationSpeed(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    return pdc.get(MOB_ANIM_SPEED, PersistentDataType.DOUBLE);
  }

  public static void setAnimationSpeed(Entity entity, Double speed) {
    Objects.requireNonNull(entity, "entity");
    if (speed == null) {
      entity.getPersistentDataContainer().remove(MOB_ANIM_SPEED);
      return;
    }
    entity.getPersistentDataContainer().set(MOB_ANIM_SPEED, PersistentDataType.DOUBLE, speed);
  }

  public static String getMinionId(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    PersistentDataContainer pdc = entity.getPersistentDataContainer();
    String raw = pdc.get(MINION_ID, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setMinionId(Entity entity, String minionId) {
    Objects.requireNonNull(entity, "entity");
    if (minionId == null || minionId.isBlank()) {
      entity.getPersistentDataContainer().remove(MINION_ID);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_ID, PersistentDataType.STRING, minionId);
  }

  public static boolean isMinionPersistent(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_PERSIST, PersistentDataType.BYTE);
    return raw != null && raw == (byte) 1;
  }

  public static void setMinionPersistent(Entity entity, boolean persistent) {
    Objects.requireNonNull(entity, "entity");
    if (!persistent) {
      entity.getPersistentDataContainer().remove(MINION_PERSIST);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_PERSIST, PersistentDataType.BYTE, (byte) 1);
  }

  public static Long getMinionExpiresAt(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    return entity.getPersistentDataContainer().get(MINION_EXPIRES_AT, PersistentDataType.LONG);
  }

  public static void setMinionExpiresAt(Entity entity, Long epochMillis) {
    Objects.requireNonNull(entity, "entity");
    if (epochMillis == null || epochMillis <= 0L) {
      entity.getPersistentDataContainer().remove(MINION_EXPIRES_AT);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_EXPIRES_AT, PersistentDataType.LONG, epochMillis);
  }

  public static Boolean getMinionDespawnOnLogout(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_DESPAWN_ON_LOGOUT, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionDespawnOnLogout(Entity entity, Boolean despawnOnLogout) {
    Objects.requireNonNull(entity, "entity");
    if (despawnOnLogout == null) {
      entity.getPersistentDataContainer().remove(MINION_DESPAWN_ON_LOGOUT);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_DESPAWN_ON_LOGOUT, PersistentDataType.BYTE,
        (byte) (despawnOnLogout ? 1 : 0));
  }

  public static String getMinionMainAttack(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    String raw = entity.getPersistentDataContainer().get(MINION_MAIN_ATTACK, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setMinionMainAttack(Entity entity, String abilityId) {
    Objects.requireNonNull(entity, "entity");
    if (abilityId == null || abilityId.isBlank()) {
      entity.getPersistentDataContainer().remove(MINION_MAIN_ATTACK);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_MAIN_ATTACK, PersistentDataType.STRING, abilityId);
  }

  public static String getMinionSecondaryAttack(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    String raw = entity.getPersistentDataContainer().get(MINION_SECONDARY_ATTACK, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setMinionSecondaryAttack(Entity entity, String abilityId) {
    Objects.requireNonNull(entity, "entity");
    if (abilityId == null || abilityId.isBlank()) {
      entity.getPersistentDataContainer().remove(MINION_SECONDARY_ATTACK);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_SECONDARY_ATTACK, PersistentDataType.STRING, abilityId);
  }

  public static Boolean getMinionDisableBasePassives(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_DISABLE_BASE_PASSIVES, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionDisableBasePassives(Entity entity, Boolean disable) {
    Objects.requireNonNull(entity, "entity");
    if (disable == null) {
      entity.getPersistentDataContainer().remove(MINION_DISABLE_BASE_PASSIVES);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_DISABLE_BASE_PASSIVES, PersistentDataType.BYTE,
        (byte) (disable ? 1 : 0));
  }

  public static Boolean getMinionDisableBaseAttacks(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_DISABLE_BASE_ATTACKS, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionDisableBaseAttacks(Entity entity, Boolean disable) {
    Objects.requireNonNull(entity, "entity");
    if (disable == null) {
      entity.getPersistentDataContainer().remove(MINION_DISABLE_BASE_ATTACKS);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_DISABLE_BASE_ATTACKS, PersistentDataType.BYTE,
        (byte) (disable ? 1 : 0));
  }

  public static Boolean getMinionDisableBaseAi(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_DISABLE_BASE_AI, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionDisableBaseAi(Entity entity, Boolean disable) {
    Objects.requireNonNull(entity, "entity");
    if (disable == null) {
      entity.getPersistentDataContainer().remove(MINION_DISABLE_BASE_AI);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_DISABLE_BASE_AI, PersistentDataType.BYTE,
        (byte) (disable ? 1 : 0));
  }

  public static String getMinionNameOverride(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    String raw = entity.getPersistentDataContainer().get(MINION_NAME_OVERRIDE, PersistentDataType.STRING);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }

  public static void setMinionNameOverride(Entity entity, String name) {
    Objects.requireNonNull(entity, "entity");
    if (name == null || name.isBlank()) {
      entity.getPersistentDataContainer().remove(MINION_NAME_OVERRIDE);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_NAME_OVERRIDE, PersistentDataType.STRING, name);
  }

  public static Boolean getMinionGlowOverride(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_GLOW_OVERRIDE, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionGlowOverride(Entity entity, Boolean glow) {
    Objects.requireNonNull(entity, "entity");
    if (glow == null) {
      entity.getPersistentDataContainer().remove(MINION_GLOW_OVERRIDE);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_GLOW_OVERRIDE, PersistentDataType.BYTE,
        (byte) (glow ? 1 : 0));
  }

  public static dev.patric.dungeonsreborn.effects.minions.MinionMode getMinionMode(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    String raw = entity.getPersistentDataContainer().get(MINION_MODE, PersistentDataType.STRING);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return dev.patric.dungeonsreborn.effects.minions.MinionMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  public static void setMinionMode(Entity entity, dev.patric.dungeonsreborn.effects.minions.MinionMode mode) {
    Objects.requireNonNull(entity, "entity");
    if (mode == null) {
      entity.getPersistentDataContainer().remove(MINION_MODE);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_MODE, PersistentDataType.STRING, mode.name());
  }

  public static Boolean getMinionAllowPvp(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_ALLOW_PVP, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionAllowPvp(Entity entity, Boolean allow) {
    Objects.requireNonNull(entity, "entity");
    if (allow == null) {
      entity.getPersistentDataContainer().remove(MINION_ALLOW_PVP);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_ALLOW_PVP, PersistentDataType.BYTE,
        (byte) (allow ? 1 : 0));
  }

  public static Boolean getMinionAllowPartyTargets(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_ALLOW_PARTY, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionAllowPartyTargets(Entity entity, Boolean allow) {
    Objects.requireNonNull(entity, "entity");
    if (allow == null) {
      entity.getPersistentDataContainer().remove(MINION_ALLOW_PARTY);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_ALLOW_PARTY, PersistentDataType.BYTE,
        (byte) (allow ? 1 : 0));
  }

  public static Boolean getMinionShareOwnerAggro(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    Byte raw = entity.getPersistentDataContainer().get(MINION_SHARE_OWNER_AGGRO, PersistentDataType.BYTE);
    if (raw == null) {
      return null;
    }
    return raw == (byte) 1;
  }

  public static void setMinionShareOwnerAggro(Entity entity, Boolean share) {
    Objects.requireNonNull(entity, "entity");
    if (share == null) {
      entity.getPersistentDataContainer().remove(MINION_SHARE_OWNER_AGGRO);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_SHARE_OWNER_AGGRO, PersistentDataType.BYTE,
        (byte) (share ? 1 : 0));
  }

  public static Double getMinionTargetRadius(Entity entity) {
    Objects.requireNonNull(entity, "entity");
    return entity.getPersistentDataContainer().get(MINION_TARGET_RADIUS, PersistentDataType.DOUBLE);
  }

  public static void setMinionTargetRadius(Entity entity, Double radius) {
    Objects.requireNonNull(entity, "entity");
    if (radius == null || !Double.isFinite(radius) || radius <= 0.0) {
      entity.getPersistentDataContainer().remove(MINION_TARGET_RADIUS);
      return;
    }
    entity.getPersistentDataContainer().set(MINION_TARGET_RADIUS, PersistentDataType.DOUBLE, radius);
  }
}
