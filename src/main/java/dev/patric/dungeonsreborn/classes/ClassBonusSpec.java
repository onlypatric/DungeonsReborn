package dev.patric.dungeonsreborn.classes;

import java.util.List;
import java.util.Map;

import dev.patric.dungeonsreborn.effects.damage.DamageType;

public record ClassBonusSpec(
    int strength,
    int dexterity,
    int intelligence,
    int vitality,
    double manaMaxBonus,
    double manaRegenBonus,
    List<ClassAttributeBonus> attributes,
    List<ClassPotionBonus> potions,
    Map<DamageType, Double> resistances,
    Map<String, Double> attributeCaps
) {
  public static ClassBonusSpec empty() {
    return new ClassBonusSpec(0, 0, 0, 0, 0.0, 0.0, List.of(), List.of(), Map.of(), Map.of());
  }

  public List<ClassAttributeBonus> attributesOrEmpty() {
    return attributes == null ? List.of() : attributes;
  }

  public List<ClassPotionBonus> potionsOrEmpty() {
    return potions == null ? List.of() : potions;
  }

  public Map<DamageType, Double> resistancesOrEmpty() {
    return resistances == null ? Map.of() : resistances;
  }

  public Map<String, Double> attributeCapsOrEmpty() {
    return attributeCaps == null ? Map.of() : attributeCaps;
  }
}
