package dev.patric.dungeonsreborn.party;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

public final class PartyService {
  public record Result(boolean success, String message) {
  }

  public record InviteResult(boolean success, String message, PartyInvite invite) {
  }

  private final Map<UUID, Party> parties = new HashMap<>();
  private final Map<UUID, Party> memberParties = new HashMap<>();
  private final Map<UUID, PartyInvite> invites = new HashMap<>();
  private final Set<UUID> chatEnabled = new HashSet<>();
  private final Predicate<World> worldAllowed;
  private final int maxSize;
  private final long inviteMillis;

  public PartyService(Predicate<World> worldAllowed, int maxSize, Duration inviteDuration) {
    this.worldAllowed = Objects.requireNonNull(worldAllowed, "worldAllowed");
    this.maxSize = Math.max(1, maxSize);
    this.inviteMillis = Math.max(1L, inviteDuration.toMillis());
  }

  public Party partyOf(Player player) {
    if (player == null) {
      return null;
    }
    return memberParties.get(player.getUniqueId());
  }

  public boolean isChatEnabled(Player player) {
    return player != null && chatEnabled.contains(player.getUniqueId());
  }

  public Result createParty(Player leader) {
    if (leader == null) {
      return new Result(false, Locales.text(null, "messages.party.error.playersOnly"));
    }
    if (!worldAllowed.test(leader.getWorld())) {
      return new Result(false, Locales.text(leader, "messages.party.error.onlyRpgWorld"));
    }
    if (memberParties.containsKey(leader.getUniqueId())) {
      return new Result(false, Locales.text(leader, "messages.party.error.alreadyInParty"));
    }
    Party party = new Party(leader.getUniqueId(), leader.getWorld().getName(), leader.getWorld().getKey().toString());
    parties.put(party.id(), party);
    memberParties.put(leader.getUniqueId(), party);
    return new Result(true, Locales.text(leader, "messages.party.create"));
  }

  public InviteResult invite(Player leader, Player target) {
    if (leader == null || target == null) {
      return new InviteResult(false, Locales.text(null, "messages.party.error.targetNotFound"), null);
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new InviteResult(false, Locales.text(leader, "messages.party.error.notInParty"), null);
    }
    if (!party.leader().equals(leader.getUniqueId())) {
      return new InviteResult(false, Locales.text(leader, "messages.party.error.notLeader"), null);
    }
    if (!worldAllowed.test(leader.getWorld())) {
      return new InviteResult(false, Locales.text(leader, "messages.party.error.onlyRpgWorld"), null);
    }
    if (!leader.getWorld().equals(target.getWorld())) {
      return new InviteResult(false, Locales.text(leader, "messages.party.error.sameWorld"), null);
    }
    if (party.hasMember(target.getUniqueId())) {
      return new InviteResult(false, Locales.text(leader, "messages.party.error.alreadyInYourParty"), null);
    }
    if (memberParties.containsKey(target.getUniqueId())) {
      return new InviteResult(false, Locales.text(leader, "messages.party.error.targetAlreadyInParty"), null);
    }
    if (party.size() >= maxSize) {
      return new InviteResult(false, Locales.text(leader, "messages.party.full"), null);
    }
    PartyInvite invite = new PartyInvite(party.id(), party.leader(), leader.getName(),
        System.currentTimeMillis() + inviteMillis);
    invites.put(target.getUniqueId(), invite);
    return new InviteResult(true, Locales.text(leader, "messages.party.result.inviteSent",
        Map.of("player", target.getName())), invite);
  }

  public Result acceptInvite(Player player, String leaderName) {
    if (player == null) {
      return new Result(false, Locales.text(null, "messages.party.error.playersOnly"));
    }
    PartyInvite invite = invites.get(player.getUniqueId());
    if (invite == null) {
      return new Result(false, Locales.text(player, "messages.party.error.inviteMissing"));
    }
    if (invite.expired()) {
      invites.remove(player.getUniqueId());
      return new Result(false, Locales.text(player, "messages.party.error.inviteExpired"));
    }
    if (leaderName != null && !invite.leaderName().equalsIgnoreCase(leaderName)) {
      return new Result(false, Locales.text(player, "messages.party.error.inviteLeaderNotFound"));
    }
    Party party = parties.get(invite.partyId());
    if (party == null) {
      invites.remove(player.getUniqueId());
      return new Result(false, Locales.text(player, "messages.party.error.partyMissing"));
    }
    if (!worldAllowed.test(player.getWorld())) {
      return new Result(false, Locales.text(player, "messages.party.error.onlyRpgWorld"));
    }
    if (!player.getWorld().getName().equals(party.worldName())) {
      return new Result(false, Locales.text(player, "messages.party.error.mustBeSameWorldLeader"));
    }
    if (party.size() >= maxSize) {
      return new Result(false, Locales.text(player, "messages.party.full"));
    }
    if (memberParties.containsKey(player.getUniqueId())) {
      return new Result(false, Locales.text(player, "messages.party.error.alreadyInParty"));
    }
    party.addMember(player.getUniqueId());
    memberParties.put(player.getUniqueId(), party);
    invites.remove(player.getUniqueId());
    broadcast(party, "messages.party.broadcast.join", Map.of("player", player.getName()));
    return new Result(true, Locales.text(player, "messages.party.join"));
  }

  public Result leave(Player player) {
    return leave(player, true);
  }

  public Result leave(Player player, boolean notify) {
    if (player == null) {
      return new Result(false, Locales.text(null, "messages.party.error.playersOnly"));
    }
    Party party = memberParties.get(player.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.text(player, "messages.party.error.notInParty"));
    }
    boolean leader = party.leader().equals(player.getUniqueId());
    removeMember(party, player.getUniqueId());
    if (notify) {
      broadcast(party, "messages.party.broadcast.leave", Map.of("player", player.getName()));
    }
    if (party.size() == 0) {
      disband(party);
      return new Result(true, Locales.text(player, "messages.party.disband"));
    }
    if (leader) {
      UUID newLeader = party.members().iterator().next();
      party.leader(newLeader);
      Player leaderPlayer = Bukkit.getPlayer(newLeader);
      String leaderName = leaderPlayer != null ? leaderPlayer.getName() : newLeader.toString();
      broadcast(party, "messages.party.broadcast.newLeader", Map.of("player", leaderName));
    }
    return new Result(true, Locales.text(player, "messages.party.leave"));
  }

  public Result kick(Player leader, Player target) {
    if (leader == null || target == null) {
      return new Result(false, Locales.text(null, "messages.party.error.targetNotFound"));
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.text(leader, "messages.party.error.notInParty"));
    }
    if (!party.leader().equals(leader.getUniqueId())) {
      return new Result(false, Locales.text(leader, "messages.party.error.notLeader"));
    }
    if (!party.hasMember(target.getUniqueId())) {
      return new Result(false, Locales.text(leader, "messages.party.error.targetNotInParty"));
    }
    if (leader.getUniqueId().equals(target.getUniqueId())) {
      return new Result(false, Locales.text(leader, "messages.party.error.kickSelf"));
    }
    removeMember(party, target.getUniqueId());
    broadcast(party, "messages.party.broadcast.kicked", Map.of("player", target.getName()));
    target.sendMessage(Locales.component(target, "messages.party.kicked"));
    return new Result(true, Locales.text(leader, "messages.party.result.kicked",
        Map.of("player", target.getName())));
  }

  public Result transferLeader(Player leader, Player target) {
    if (leader == null || target == null) {
      return new Result(false, Locales.text(null, "messages.party.error.targetNotFound"));
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.text(leader, "messages.party.error.notInParty"));
    }
    if (!party.leader().equals(leader.getUniqueId())) {
      return new Result(false, Locales.text(leader, "messages.party.error.notLeader"));
    }
    if (!party.hasMember(target.getUniqueId())) {
      return new Result(false, Locales.text(leader, "messages.party.error.targetNotInParty"));
    }
    party.leader(target.getUniqueId());
    broadcast(party, "messages.party.broadcast.newLeader", Map.of("player", target.getName()));
    return new Result(true, Locales.text(leader, "messages.party.result.transfer"));
  }

  public Result toggleChat(Player player, boolean enabled) {
    if (player == null) {
      return new Result(false, Locales.text(null, "messages.party.error.playersOnly"));
    }
    Party party = memberParties.get(player.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.text(player, "messages.party.error.notInParty"));
    }
    if (enabled) {
      chatEnabled.add(player.getUniqueId());
      return new Result(true, Locales.text(player, "messages.party.chat.true"));
    }
    chatEnabled.remove(player.getUniqueId());
    return new Result(true, Locales.text(player, "messages.party.chat.false"));
  }

  public Result sendChat(Player sender, String message) {
    if (sender == null) {
      return new Result(false, Locales.text(null, "messages.party.error.playersOnly"));
    }
    Party party = memberParties.get(sender.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.text(sender, "messages.party.error.notInParty"));
    }
    if (message == null || message.isBlank()) {
      return new Result(false, Locales.text(sender, "messages.party.error.messageEmpty"));
    }
    broadcast(party, "messages.party.broadcast.chat", Map.of(
        "player", sender.getName(),
        "message", message));
    return new Result(true, Locales.text(sender, "messages.party.result.messageSent"));
  }

  public void handleQuit(Player player) {
    if (player == null) {
      return;
    }
    leave(player, false);
    invites.remove(player.getUniqueId());
    chatEnabled.remove(player.getUniqueId());
  }

  public void handleWorldChange(Player player, World from, World to) {
    if (player == null) {
      return;
    }
    Party party = memberParties.get(player.getUniqueId());
    if (party == null) {
      return;
    }
    if (to == null || !to.getName().equals(party.worldName())) {
      leave(player, true);
    }
  }

  private void removeMember(Party party, UUID member) {
    party.removeMember(member);
    memberParties.remove(member);
    chatEnabled.remove(member);
  }

  private void disband(Party party) {
    parties.remove(party.id());
    for (UUID member : party.members()) {
      memberParties.remove(member);
      chatEnabled.remove(member);
    }
    clearInvites(party.id());
  }

  private void clearInvites(UUID partyId) {
    invites.entrySet().removeIf(entry -> entry.getValue().partyId().equals(partyId));
  }

  private void broadcast(Party party, String key, Map<String, String> placeholders) {
    for (UUID memberId : party.members()) {
      Player member = Bukkit.getPlayer(memberId);
      if (member != null) {
        member.sendMessage(Locales.component(member, key, placeholders));
      }
    }
  }
}
