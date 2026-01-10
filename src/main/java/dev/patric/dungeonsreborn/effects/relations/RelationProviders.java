package dev.patric.dungeonsreborn.effects.relations;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

public final class RelationProviders {
  private RelationProviders() {
  }

  /**
   * Default relation provider using Bukkit scoreboard teams for player-player relations.
   */
  public static RelationProvider scoreboardTeams() {
    return (caster, target) -> {
      Objects.requireNonNull(caster, "caster");
      Objects.requireNonNull(target, "target");
      if (caster.getUniqueId().equals(target.getUniqueId())) {
        return Relation.SELF;
      }
      if (caster instanceof Player a && target instanceof Player b) {
        Team ta = a.getScoreboard().getEntryTeam(a.getName());
        Team tb = b.getScoreboard().getEntryTeam(b.getName());
        if (ta != null && tb != null && ta.getName().equals(tb.getName())) {
          return Relation.ALLY;
        }
      }
      return Relation.NEUTRAL;
    };
  }
}
