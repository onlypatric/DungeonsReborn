package dev.patric.dungeonsreborn.progression;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.mobs.MobProgressionSpec;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpec;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;

public final class ProgressionMobKillListener implements Listener {
  private final ProgressionService service;
  private final MobRegistry mobRegistry;
  private final PartyService parties;
  private final double assistRadius;

  public ProgressionMobKillListener(ProgressionService service, MobRegistry mobRegistry, PartyService parties, double assistRadius) {
    this.service = service;
    this.mobRegistry = mobRegistry;
    this.parties = parties;
    this.assistRadius = Math.max(0.0, assistRadius);
  }

  @EventHandler(ignoreCancelled = true)
  public void onDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    String mobId = MobMarkers.getMobId(entity);
    if (mobId == null) {
      return;
    }
    MobSpec spec = mobRegistry.get(mobId);
    if (spec == null) {
      return;
    }
    MobProgressionSpec progression = spec.progressionSpec();
    if (progression == null) {
      return;
    }
    Player killer = entity.getKiller();
    if (killer == null) {
      return;
    }
    int cap = progression.maxPlayerCap();
    int min = progression.minAward();
    int max = progression.maxAward();
    if (max <= 0) {
      return;
    }
    int award = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    if (award <= 0) {
      return;
    }
    Location loc = entity.getLocation();
    for (Player recipient : resolveRecipients(killer, loc)) {
      if (cap > 0 && recipient.getTotalExperience() >= cap) {
        continue;
      }
      service.awardXp(recipient, award, ProgressionAwardSource.MOB_KILL, mobId);
    }
  }

  private Set<Player> resolveRecipients(Player killer, Location loc) {
    Set<Player> recipients = new HashSet<>();
    if (killer == null) {
      return recipients;
    }
    recipients.add(killer);
    if (parties == null) {
      return recipients;
    }
    Party party = parties.partyOf(killer);
    if (party == null || party.size() <= 1) {
      return recipients;
    }
    double radiusSquared = assistRadius * assistRadius;
    for (var memberId : party.members()) {
      Player member = Bukkit.getPlayer(memberId);
      if (member == null) {
        continue;
      }
      if (!member.getWorld().equals(loc.getWorld())) {
        continue;
      }
      if (assistRadius > 0.0 && member.getLocation().distanceSquared(loc) > radiusSquared) {
        continue;
      }
      recipients.add(member);
    }
    return recipients;
  }
}
