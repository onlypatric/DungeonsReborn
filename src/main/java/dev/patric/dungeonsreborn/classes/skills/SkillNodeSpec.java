package dev.patric.dungeonsreborn.classes.skills;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

public record SkillNodeSpec(String id, Component name, List<Component> description, ItemStack icon, SkillNodeType type,
    int cost, List<String> requires, SkillStatSpec stat, SkillAttributeSpec attribute, SkillPotionSpec potion) {
  public Component displayName() {
    return name == null ? Component.text(id) : name;
  }

  public List<Component> descriptionOrEmpty() {
    return description == null ? List.of() : description;
  }

  public List<String> requiresOrEmpty() {
    return requires == null ? List.of() : requires;
  }
}
