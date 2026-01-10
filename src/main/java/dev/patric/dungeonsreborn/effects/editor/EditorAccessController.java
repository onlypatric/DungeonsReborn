package dev.patric.dungeonsreborn.effects.editor;

import java.util.Objects;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;

public final class EditorAccessController {
  public boolean canView(Player player) {
    return player != null && player.hasPermission(EditorPermissions.VIEW);
  }

  public boolean canEdit(Player player) {
    return player != null && player.hasPermission(EditorPermissions.EDIT);
  }

  public boolean canPublish(Player player) {
    return player != null && player.hasPermission(EditorPermissions.PUBLISH);
  }

  public boolean canDelete(Player player) {
    return player != null && player.hasPermission(EditorPermissions.DELETE);
  }

  public boolean canEditCode(Player player) {
    return player != null && player.hasPermission(EditorPermissions.CODE_EDIT);
  }

  public boolean canEditAbility(Player player, String abilityId, EffectsYamlAbilities yamlAbilities, EffectsEngine engine) {
    Objects.requireNonNull(abilityId, "abilityId");
    if (!canEdit(player)) {
      return false;
    }
    String normalized = Ids.normalize(abilityId);
    boolean exists = engine != null && engine.abilitySpec(normalized) != null;
    boolean isYaml = yamlAbilities != null && yamlAbilities.loadedAbilityIds().contains(normalized);
    if (!exists || isYaml) {
      return true;
    }
    return canEditCode(player);
  }

  public boolean canDeleteAbility(Player player, String abilityId, EffectsYamlAbilities yamlAbilities, EffectsEngine engine) {
    return canDelete(player) && canEditAbility(player, abilityId, yamlAbilities, engine);
  }
}
