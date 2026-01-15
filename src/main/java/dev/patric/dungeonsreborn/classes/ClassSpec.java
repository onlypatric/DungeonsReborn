package dev.patric.dungeonsreborn.classes;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import dev.patric.dungeonsreborn.classes.skills.SkillTreeSpec;

public record ClassSpec(String id, boolean enabled, Component name, List<Component> description, ItemStack icon,
    ClassUnlockSpec unlock, SkillTreeSpec skillTree, ClassBonusSpec bonuses) {
  public Component displayName() {
    return name == null ? Component.text(id) : name;
  }

  public List<Component> descriptionOrEmpty() {
    return description == null ? List.of() : description;
  }

  public SkillTreeSpec skillTreeOrEmpty() {
    return skillTree == null ? SkillTreeSpec.empty() : skillTree;
  }

  public ClassBonusSpec bonusesOrEmpty() {
    return bonuses == null ? ClassBonusSpec.empty() : bonuses;
  }
}
