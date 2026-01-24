package dev.patric.dungeonsreborn.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public final class PartyAuraService implements Runnable {
  public enum CenterMode {
    LEADER,
    EACH_MEMBER;

    public static CenterMode fromString(String value, CenterMode fallback) {
      if (value == null) {
        return fallback;
      }
      try {
        return CenterMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return fallback;
      }
    }
  }

  private final PartyService parties;
  private final PartyAssistRules assistRules;
  private final List<PotionEffect> effects;
  private final boolean requireAssist;
  private final CenterMode centerMode;

  public PartyAuraService(PartyService parties, PartyAssistRules assistRules, List<PotionEffect> effects,
      boolean requireAssist, CenterMode centerMode) {
    this.parties = parties;
    this.assistRules = assistRules == null ? new PartyAssistRules(0.0, 0.0, 0.0) : assistRules;
    this.effects = effects == null ? List.of() : List.copyOf(effects);
    this.requireAssist = requireAssist;
    this.centerMode = centerMode == null ? CenterMode.LEADER : centerMode;
  }

  @Override
  public void run() {
    if (parties == null || effects.isEmpty()) {
      return;
    }
    for (Party party : parties.allParties()) {
      if (party == null || party.size() <= 0) {
        continue;
      }
      if (centerMode == CenterMode.LEADER) {
        Player leader = Bukkit.getPlayer(party.leader());
        if (leader == null) {
          continue;
        }
        applyAura(party, leader.getLocation());
      } else {
        for (UUID memberId : party.members()) {
          Player member = Bukkit.getPlayer(memberId);
          if (member == null) {
            continue;
          }
          applyAura(party, member.getLocation());
        }
      }
    }
  }

  private void applyAura(Party party, Location center) {
    if (center == null || party == null) {
      return;
    }
    double radius = assistRules.radiusForParty(party);
    double radiusSquared = radius * radius;
    List<Player> recipients = new ArrayList<>();
    for (UUID memberId : party.members()) {
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      if (!member.getWorld().equals(center.getWorld())) {
        continue;
      }
      if (requireAssist && radius > 0.0 && member.getLocation().distanceSquared(center) > radiusSquared) {
        continue;
      }
      recipients.add(member);
    }
    for (Player member : recipients) {
      for (PotionEffect effect : effects) {
        if (effect != null) {
          member.addPotionEffect(effect);
        }
      }
    }
  }
}
