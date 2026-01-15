package dev.patric.dungeonsreborn.quests;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import dev.patric.dungeonsreborn.mobs.MobMarkers;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;

public final class QuestListener implements Listener {
  private final QuestService quests;
  private final PartyService parties;
  private final double assistRadius;

  public QuestListener(QuestService quests, PartyService parties, double assistRadius) {
    this.quests = quests;
    this.parties = parties;
    this.assistRadius = Math.max(0.0, assistRadius);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    quests.load(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    quests.unload(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDeath(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer == null) {
      return;
    }
    String mobId = MobMarkers.getMobId(event.getEntity());
    Location loc = event.getEntity().getLocation();
    for (Player recipient : resolveRecipients(killer, loc)) {
      quests.handleKill(recipient, mobId, event.getEntity().getType());
    }
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }
    quests.handleItemUse(event.getPlayer(), event.getItem());
  }

  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    if (event.getFrom() == null || event.getTo() == null) {
      return;
    }
    if (event.getFrom().getBlockX() == event.getTo().getBlockX()
        && event.getFrom().getBlockY() == event.getTo().getBlockY()
        && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
      return;
    }
    quests.handleVisit(event.getPlayer(), event.getTo());
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
