package dev.patric.dungeonsreborn.effects.conditions;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public final class Conditions {
  private Conditions() {
  }

  public static Condition always() {
    return ctx -> true;
  }

  public static Condition sneaking() {
    return ctx -> ctx.caster() instanceof Player player && player.isSneaking();
  }

  public static Condition permission(String permission) {
    Objects.requireNonNull(permission, "permission");
    return ctx -> ctx.caster() instanceof Player player && player.hasPermission(permission);
  }

  /**
   * Cooldown check for player casters. Non-player casters are treated as always ready.
   */
  public static Condition cooldownReady(String key) {
    Objects.requireNonNull(key, "key");
    return ctx -> {
      if (!(ctx.caster() instanceof Player player)) {
        return true;
      }
      return ctx.engine().cooldownRemainingTicks(player.getUniqueId(), key) <= 0L;
    };
  }

  public static Condition hasItemTag(NamespacedKey key) {
    Objects.requireNonNull(key, "key");
    return ctx -> ctx.caster() instanceof Player && ItemMarkers.has(ctx.itemInHand(), key);
  }

  public static Condition casterHasTag(String tag) {
    Objects.requireNonNull(tag, "tag");
    if (tag.isBlank()) {
      throw new IllegalArgumentException("tag is blank");
    }
    return ctx -> ctx.caster().getScoreboardTags().contains(tag);
  }

  public static Condition casterLacksTag(String tag) {
    return ctx -> !casterHasTag(tag).test(ctx);
  }
}
