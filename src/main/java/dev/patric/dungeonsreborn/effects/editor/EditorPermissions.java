package dev.patric.dungeonsreborn.effects.editor;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class EditorPermissions {
  public static final String VIEW = "dungeonsreborn.editor.view";
  public static final String EDIT = "dungeonsreborn.editor.edit";
  public static final String PUBLISH = "dungeonsreborn.editor.publish";
  public static final String DELETE = "dungeonsreborn.editor.delete";
  public static final String CODE_EDIT = "dungeonsreborn.editor.code";

  private EditorPermissions() {
  }

  public static boolean has(CommandSender sender, String permission) {
    Objects.requireNonNull(permission, "permission");
    if (sender == null) {
      return false;
    }
    if (!(sender instanceof Player player)) {
      return true;
    }
    return player.hasPermission(permission);
  }
}
