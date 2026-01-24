package dev.patric.dungeonsreborn.progression;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.mobs.MobProgressionSpec;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpec;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyAssistRules;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.party.PartyShareMode;
import dev.patric.dungeonsreborn.progression.custom.CustomXpProfile;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;

public final class ProgressionMobKillListener implements Listener {
  private final ProgressionService service;
  private final CustomXpService customXpService;
  private final MobRegistry mobRegistry;
  private final PartyService parties;
  private final PartyAssistRules assistRules;
  private final PartyShareMode shareMode;
  private final boolean requireAssist;

  public ProgressionMobKillListener(ProgressionService service, CustomXpService customXpService, MobRegistry mobRegistry,
      PartyService parties, PartyAssistRules assistRules, PartyShareMode shareMode, boolean requireAssist) {
    this.service = service;
    this.customXpService = customXpService;
    this.mobRegistry = mobRegistry;
    this.parties = parties;
    this.assistRules = assistRules == null ? new PartyAssistRules(0.0, 0.0, 0.0) : assistRules;
    this.shareMode = shareMode == null ? PartyShareMode.NONE : shareMode;
    this.requireAssist = requireAssist;
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
    var recipients = resolveRecipients(killer, loc);
    if (recipients.isEmpty()) {
      return;
    }
    int perAward = award;
    int remainder = 0;
    if (shareMode == PartyShareMode.SPLIT && recipients.size() > 1) {
      perAward = award / recipients.size();
      remainder = award % recipients.size();
    }
    for (int i = 0; i < recipients.size(); i++) {
      Player recipient = recipients.get(i);
      int recipientAward = perAward + (remainder > 0 && i < remainder ? 1 : 0);
      if (recipientAward <= 0) {
        continue;
      }
      if (cap > 0 && getRecipientXp(recipient) >= cap) {
        continue;
      }
      if (customXpService != null) {
        customXpService.awardXp(recipient, recipientAward);
      } else {
        service.awardXp(recipient, recipientAward, ProgressionAwardSource.MOB_KILL, mobId);
      }
    }
  }

  private long getRecipientXp(Player recipient) {
    if (recipient == null) {
      return 0L;
    }
    if (customXpService == null) {
      return recipient.getTotalExperience();
    }
    CustomXpProfile profile = customXpService.getOrCreate(recipient.getUniqueId());
    return profile == null ? 0L : profile.points();
  }

  private java.util.List<Player> resolveRecipients(Player killer, Location loc) {
    java.util.List<Player> recipients = new java.util.ArrayList<>();
    if (killer == null) {
      return recipients;
    }
    recipients.add(killer);
    if (parties == null || shareMode == PartyShareMode.NONE) {
      return recipients;
    }
    Party party = parties.partyOf(killer);
    if (party == null || party.size() <= 1) {
      return recipients;
    }
    if (shareMode == PartyShareMode.FULL || shareMode == PartyShareMode.SPLIT) {
      double radius = requireAssist ? assistRules.radiusForParty(party) : 0.0;
      double radiusSquared = radius * radius;
      for (var memberId : party.members()) {
        if (memberId == null || memberId.equals(killer.getUniqueId())) {
          continue;
        }
        Player member = Bukkit.getPlayer(memberId);
        if (member == null) {
          continue;
        }
        if (!member.getWorld().equals(loc.getWorld())) {
          continue;
        }
        if (requireAssist && radius > 0.0 && member.getLocation().distanceSquared(loc) > radiusSquared) {
          continue;
        }
        recipients.add(member);
      }
    }
    return recipients;
  }
}
