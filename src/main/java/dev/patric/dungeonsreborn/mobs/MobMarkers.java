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
  public static final NamespacedKey MINION_ID = new NamespacedKey("dungeonsreborn", "minion_id");

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
}
