package dev.patric.dungeonsreborn.effects.combat;

import java.util.Collections;
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
    Set<String> dotTags) {

  public CombatEventFilters {
    damageTypes = damageTypes == null ? Set.of() : Collections.unmodifiableSet(damageTypes);
    sources = sources == null ? Set.of() : Collections.unmodifiableSet(sources);
    victimRelations = victimRelations == null ? Set.of() : Collections.unmodifiableSet(victimRelations);
    ccTypes = normalizeLowerSet(ccTypes);
    dotTags = normalizeLowerSet(dotTags);
    minDamage = Double.isFinite(minDamage) ? Math.max(0.0, minDamage) : 0.0;
  }

  public static CombatEventFilters none() {
    return new CombatEventFilters(null, Set.of(), Set.of(), Set.of(), 0.0, false, false, Set.of(), Set.of());
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
    java.util.HashSet<String> out = new java.util.HashSet<>();
    for (String entry : set) {
      if (entry != null && !entry.isBlank()) {
        out.add(entry.trim().toLowerCase(Locale.ROOT));
      }
    }
    return Collections.unmodifiableSet(out);
  }
}

