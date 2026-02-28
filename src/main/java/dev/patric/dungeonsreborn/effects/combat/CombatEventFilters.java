package dev.patric.dungeonsreborn.effects.combat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.relations.Relation;

public record CombatEventFilters(
    String weaponTag,
    Set<DamageType> damageTypes,
    Set<CombatEventSource> sources,
    Set<Relation> victimRelations,
    double minDamage,
    boolean critOnly,
    boolean blockedOnly,
    Set<String> ccTypes,
    Set<String> dotTags,
    Set<String> projectileTypes,
    Set<ProjectileFamily> projectileFamilies,
    Set<String> projectileKinds,
    double distanceMin,
    double distanceMax,
    double speedMin,
    double speedMax,
    double drawForceMin,
    double drawForceMax,
    int inGroundTicksMin,
    int inGroundTicksMax,
    Boolean projectileCritical,
    Boolean projectileCharged,
    Boolean projectilePiercing,
    Boolean shotFromCrossbow,
    Boolean shooterIsPlayer,
    Set<String> hitBlockMaterials,
    Set<String> hitBlockTags) {

  public CombatEventFilters {
    damageTypes = damageTypes == null ? Set.of() : Collections.unmodifiableSet(damageTypes);
    sources = sources == null ? Set.of() : Collections.unmodifiableSet(sources);
    victimRelations = victimRelations == null ? Set.of() : Collections.unmodifiableSet(victimRelations);
    ccTypes = normalizeLowerSet(ccTypes);
    dotTags = normalizeLowerSet(dotTags);
    projectileTypes = normalizeUpperSet(projectileTypes);
    projectileFamilies = projectileFamilies == null ? Set.of() : Collections.unmodifiableSet(projectileFamilies);
    projectileKinds = normalizeLowerSet(projectileKinds);
    minDamage = Double.isFinite(minDamage) ? Math.max(0.0, minDamage) : 0.0;
    distanceMin = clampNonNegative(distanceMin);
    distanceMax = clampMax(distanceMax);
    speedMin = clampNonNegative(speedMin);
    speedMax = clampMax(speedMax);
    drawForceMin = clampNonNegative(drawForceMin);
    drawForceMax = clampMax(drawForceMax);
    inGroundTicksMin = Math.max(0, inGroundTicksMin);
    inGroundTicksMax = Math.max(0, inGroundTicksMax);
    hitBlockMaterials = normalizeUpperSet(hitBlockMaterials);
    hitBlockTags = normalizeLowerSet(hitBlockTags);
  }

  public static CombatEventFilters none() {
    return new CombatEventFilters(
        null,
        Set.of(),
        Set.of(),
        Set.of(),
        0.0,
        false,
        false,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0,
        0,
        null,
        null,
        null,
        null,
        null,
        Set.of(),
        Set.of());
  }

  public boolean matches(CombatEventContext ctx, EffectsEngine engine) {
    if (ctx == null) {
      return false;
    }
    if (minDamage > 0.0 && ctx.damage() < minDamage) {
      return false;
    }
    if (critOnly && !ctx.crit()) {
      return false;
    }
    if (blockedOnly && !ctx.blocked()) {
      return false;
    }
    if (!damageTypes.isEmpty()) {
      if (ctx.damageType() == null || !damageTypes.contains(ctx.damageType())) {
        return false;
      }
    }
    if (!sources.isEmpty() && !sources.contains(ctx.source())) {
      return false;
    }
    if (!projectileTypes.isEmpty()) {
      String type = ctx.projectileType();
      if (type == null || type.isBlank() || !projectileTypes.contains(type.trim().toUpperCase(Locale.ROOT))) {
        return false;
      }
    }
    if (!projectileFamilies.isEmpty()) {
      if (!projectileFamilies.contains(ctx.projectileFamily())) {
        return false;
      }
    }
    if (!projectileKinds.isEmpty()) {
      String kind = ctx.projectileKind();
      if (kind == null || !projectileKinds.contains(kind.toLowerCase(Locale.ROOT))) {
        return false;
      }
    }
    if (distanceMin > 0.0 && ctx.projectileDistance() < distanceMin) {
      return false;
    }
    if (distanceMax > 0.0 && ctx.projectileDistance() > distanceMax) {
      return false;
    }
    if (speedMin > 0.0 && ctx.projectileSpeed() < speedMin) {
      return false;
    }
    if (speedMax > 0.0 && ctx.projectileSpeed() > speedMax) {
      return false;
    }
    if (drawForceMin > 0.0 && ctx.projectileDrawForce() < drawForceMin) {
      return false;
    }
    if (drawForceMax > 0.0 && ctx.projectileDrawForce() > drawForceMax) {
      return false;
    }
    if (inGroundTicksMin > 0 && ctx.projectileInGroundTicks() < inGroundTicksMin) {
      return false;
    }
    if (inGroundTicksMax > 0 && ctx.projectileInGroundTicks() > inGroundTicksMax) {
      return false;
    }
    if (projectileCritical != null && projectileCritical.booleanValue() != ctx.projectileCritical()) {
      return false;
    }
    if (projectileCharged != null && projectileCharged.booleanValue() != ctx.projectileCharged()) {
      return false;
    }
    if (projectilePiercing != null && projectilePiercing.booleanValue() != ctx.projectilePiercing()) {
      return false;
    }
    if (shotFromCrossbow != null && shotFromCrossbow.booleanValue() != ctx.projectileShotFromCrossbow()) {
      return false;
    }
    if (shooterIsPlayer != null && shooterIsPlayer.booleanValue() != ctx.shooterIsPlayer()) {
      return false;
    }
    if (!hitBlockMaterials.isEmpty()) {
      String material = ctx.hitBlockMaterial();
      if (material == null || !hitBlockMaterials.contains(material.toUpperCase(Locale.ROOT))) {
        return false;
      }
    }
    if (!hitBlockTags.isEmpty()) {
      String tag = ctx.hitBlockTag();
      if (tag == null || !hitBlockTags.contains(tag.toLowerCase(Locale.ROOT))) {
        return false;
      }
    }
    if (!ccTypes.isEmpty()) {
      String type = ctx.ccType();
      if (type == null || !ccTypes.contains(type.toLowerCase(Locale.ROOT))) {
        return false;
      }
    }
    if (!dotTags.isEmpty()) {
      String tag = ctx.dotTag();
      if (tag == null || !dotTags.contains(tag.toLowerCase(Locale.ROOT))) {
        return false;
      }
    }
    if (!victimRelations.isEmpty() && ctx.attacker() != null && ctx.victim() != null && engine != null) {
      Relation relation = engine.relation(ctx.attacker(), ctx.victim());
      if (!victimRelations.contains(relation)) {
        return false;
      }
    }
    if (weaponTag != null && !weaponTag.isBlank()) {
      var attacker = ctx.attacker();
      if (attacker == null) {
        return false;
      }
      var item = attacker.getEquipment() == null ? null : attacker.getEquipment().getItemInMainHand();
      if (item == null || item.getType().isAir()) {
        return false;
      }
      String needle = weaponTag.toLowerCase(Locale.ROOT);
      String material = item.getType().name().toLowerCase(Locale.ROOT);
      if (!material.contains(needle)) {
        return false;
      }
    }
    return true;
  }

  private static Set<String> normalizeLowerSet(Set<String> set) {
    if (set == null || set.isEmpty()) {
      return Set.of();
    }
    HashSet<String> out = new HashSet<>();
    for (String entry : set) {
      if (entry != null && !entry.isBlank()) {
        out.add(entry.trim().toLowerCase(Locale.ROOT));
      }
    }
    return Collections.unmodifiableSet(out);
  }

  private static Set<String> normalizeUpperSet(Set<String> set) {
    if (set == null || set.isEmpty()) {
      return Set.of();
    }
    HashSet<String> out = new HashSet<>();
    for (String entry : set) {
      if (entry != null && !entry.isBlank()) {
        out.add(entry.trim().toUpperCase(Locale.ROOT));
      }
    }
    return Collections.unmodifiableSet(out);
  }

  private static double clampNonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
  }

  private static double clampMax(double value) {
    return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
  }
}
