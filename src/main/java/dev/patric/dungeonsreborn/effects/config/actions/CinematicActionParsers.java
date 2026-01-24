package dev.patric.dungeonsreborn.effects.config.actions;

import java.util.Map;

import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities.NumValue;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

final class CinematicActionParsers {
  private CinematicActionParsers() {
  }

  static void register(Map<String, ActionParser> registry) {
    registry.put("screen_shake", CinematicActionParsers::parseScreenShake);
    registry.put("screen_flash", CinematicActionParsers::parseScreenFlash);
  }

  private static Action parseScreenShake(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    NumValue durationTicks = ctx.numValue(node, "durationTicks", 20.0, path);
    NumValue amplifier = ctx.numValue(node, "amplifier", 0.0, path);
    boolean ambient = ctx.bool(node, "ambient", false);
    boolean particles = ctx.bool(node, "particles", true);
    boolean icon = ctx.bool(node, "icon", true);
    return castCtx -> {
      Player player = ctx.targetPlayer(castCtx);
      if (player == null) {
        return;
      }
      int duration = Math.max(0, ctx.evalInt(durationTicks, castCtx));
      int amp = Math.max(0, ctx.evalInt(amplifier, castCtx));
      dev.patric.dungeonsreborn.effects.actions.Actions.screenShake(player, castCtx.engine().cinematicSettings(),
          duration, amp, ambient, particles, icon);
    };
  }

  private static Action parseScreenFlash(ActionParserContext ctx, Map<String, Object> node, String path,
      java.util.ArrayDeque<String> includeStack) {
    Particle particle = node.containsKey("particle")
        ? dev.patric.dungeonsreborn.effects.config.YamlValueParsers.enumValue(node, "particle", Particle.class, path + ".particle")
        : Particle.FLASH;
    NumValue count = ctx.numValue(node, "count", 1.0, path);
    NumValue offset = ctx.numValue(node, "offset", 0.0, path);
    NumValue extra = ctx.numValue(node, "extra", 0.0, path);
    Sound sound = node.containsKey("sound") ? ctx.soundValue(node, "sound", path + ".sound") : null;
    NumValue volume = ctx.numValue(node, "volume", 1.0, path);
    NumValue pitch = ctx.numValue(node, "pitch", 1.0, path);
    return castCtx -> {
      Player player = ctx.targetPlayer(castCtx);
      if (player == null) {
        return;
      }
      int emitCount = ctx.evalInt(count, castCtx);
      double off = ctx.evalDouble(offset, castCtx);
      double ex = ctx.evalDouble(extra, castCtx);
      dev.patric.dungeonsreborn.effects.actions.Actions.screenFlash(player, castCtx.engine().cinematicSettings(),
          particle, emitCount, off, ex);
      if (sound != null) {
        player.getWorld().playSound(player.getLocation(), sound,
            (float) ctx.evalDouble(volume, castCtx), (float) ctx.evalDouble(pitch, castCtx));
      }
    };
  }
}
