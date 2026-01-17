package dev.patric.dungeonsreborn.classes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import dev.patric.dungeonsreborn.classes.skills.SkillTreeSpec;
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.locale.Locales;

public record ClassSpec(String id, boolean enabled, String nameKey, String descriptionKey, Component name,
    List<Component> description, ItemStack icon, ClassUnlockSpec unlock, SkillTreeSpec skillTree, ClassBonusSpec bonuses) {
  public Component displayName() {
    return name == null ? Component.text(id) : name;
  }

  public Component displayName(Player player) {
    if (nameKey != null && !nameKey.isBlank()) {
      return Locales.component(player, nameKey);
    }
    return displayName();
  }

  public List<Component> descriptionOrEmpty() {
    return description == null ? List.of() : description;
  }

  public List<Component> descriptionFor(Player player) {
    if (descriptionKey == null || descriptionKey.isBlank()) {
      return descriptionOrEmpty();
    }
    String raw = Locales.text(player, descriptionKey);
    if (raw == null || raw.isBlank()) {
      return descriptionOrEmpty();
    }
    List<Component> lines = new ArrayList<>();
    for (String line : raw.split("\\R", -1)) {
      lines.add(EditorItemLore.parseRichText(line));
    }
    return List.copyOf(lines);
  }

  public SkillTreeSpec skillTreeOrEmpty() {
    return skillTree == null ? SkillTreeSpec.empty() : skillTree;
  }

  public ClassBonusSpec bonusesOrEmpty() {
    return bonuses == null ? ClassBonusSpec.empty() : bonuses;
  }
}
