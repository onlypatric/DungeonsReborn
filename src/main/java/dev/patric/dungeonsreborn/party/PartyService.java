package dev.patric.dungeonsreborn.party;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.PartyAuditLog;
import dev.patric.dungeonsreborn.quests.QuestRegion;
import net.kyori.adventure.text.Component;

public final class PartyService {
  public record Result(boolean success, Component message) {
  }

  public record InviteResult(boolean success, Component message, PartyInvite invite) {
  }

  public record CleanupResult(int invitesRemoved, int requestsRemoved, int partiesDisbanded) {
    public int total() {
      return invitesRemoved + requestsRemoved + partiesDisbanded;
    }
  }

  public record JoinRequest(UUID partyId, UUID requesterId, String requesterName, long expiresAt) {
    public boolean expired() {
      return expiresAt <= System.currentTimeMillis();
    }
  }

  public enum Permission {
    INVITE,
    KICK,
    TRANSFER_LEADER,
    TOGGLE_CHAT,
    MANAGE_ROLES,
    MANAGE_PUBLIC
  }

  public enum WorldPolicy {
    SAME_WORLD,
    LEADER_WORLD,
    ANY_ALLOWED;

    public static WorldPolicy fromString(String value, WorldPolicy fallback) {
      if (value == null) {
        return fallback;
      }
      try {
        return WorldPolicy.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return fallback;
      }
    }
  }

  private final Map<UUID, Party> parties = new HashMap<>();
  private final Map<UUID, Party> memberParties = new HashMap<>();
  private final Map<UUID, PartyInvite> invites = new HashMap<>();
  private final Map<UUID, JoinRequest> joinRequests = new HashMap<>();
  private final Set<UUID> chatEnabled = new HashSet<>();
  private final Predicate<World> worldAllowed;
  private final WorldPolicy worldPolicy;
  private final List<QuestRegion> allowedRegions;
  private final List<QuestRegion> deniedRegions;
  private final int maxSize;
  private final long inviteMillis;
  private final PartyRepository repository;
  private PartyAuditLog auditLog;

  public PartyService(Predicate<World> worldAllowed, int maxSize, Duration inviteDuration) {
    this(worldAllowed, maxSize, inviteDuration, WorldPolicy.SAME_WORLD, List.of(), List.of(), null);
  }

  public PartyService(Predicate<World> worldAllowed, int maxSize, Duration inviteDuration, PartyRepository repository) {
    this(worldAllowed, maxSize, inviteDuration, WorldPolicy.SAME_WORLD, List.of(), List.of(), repository);
  }

  public PartyService(Predicate<World> worldAllowed, int maxSize, Duration inviteDuration, WorldPolicy worldPolicy,
      List<QuestRegion> allowedRegions, List<QuestRegion> deniedRegions, PartyRepository repository) {
    this.worldAllowed = Objects.requireNonNull(worldAllowed, "worldAllowed");
    this.worldPolicy = worldPolicy == null ? WorldPolicy.SAME_WORLD : worldPolicy;
    this.allowedRegions = allowedRegions == null ? List.of() : List.copyOf(allowedRegions);
    this.deniedRegions = deniedRegions == null ? List.of() : List.copyOf(deniedRegions);
    this.maxSize = Math.max(1, maxSize);
    this.inviteMillis = Math.max(1L, inviteDuration.toMillis());
    this.repository = repository;
    loadFromRepository();
  }

  public void setAuditLog(PartyAuditLog auditLog) {
    this.auditLog = auditLog;
  }

  public Party partyOf(Player player) {
    if (player == null) {
      return null;
    }
    return memberParties.get(player.getUniqueId());
  }

  public Party partyOf(UUID playerId) {
    if (playerId == null) {
      return null;
    }
    return memberParties.get(playerId);
  }

  public Party partyById(UUID partyId) {
    if (partyId == null) {
      return null;
    }
    return parties.get(partyId);
  }

  public Party findPartyByLeaderName(String leaderName) {
    if (leaderName == null || leaderName.isBlank()) {
      return null;
    }
    for (Party party : parties.values()) {
      if (party == null) {
        continue;
      }
      String name = leaderName(party);
      if (name != null && name.equalsIgnoreCase(leaderName)) {
        return party;
      }
    }
    return null;
  }

  public boolean isChatEnabled(Player player) {
    return player != null && chatEnabled.contains(player.getUniqueId());
  }

  public boolean isLeader(UUID memberId) {
    Party party = partyOf(memberId);
    return party != null && memberId != null && memberId.equals(party.leader());
  }

  public boolean hasPermission(UUID memberId, Permission permission) {
    Party party = partyOf(memberId);
    if (party == null || permission == null) {
      return false;
    }
    Party.Role role = party.role(memberId);
    if (role == null) {
      return false;
    }
    if (role == Party.Role.LEADER) {
      return true;
    }
    if (role == Party.Role.OFFICER) {
      return permission != Permission.TRANSFER_LEADER;
    }
    return permission == Permission.TOGGLE_CHAT;
  }

  public int maxSize() {
    return maxSize;
  }

  public boolean isPublic(Party party) {
    return party != null && party.isPublic();
  }

  public List<Party> allParties() {
    return new ArrayList<>(parties.values());
  }

  public List<Party> publicParties() {
    List<Party> out = new java.util.ArrayList<>();
    for (Party party : parties.values()) {
      if (party != null && party.isPublic()) {
        out.add(party);
      }
    }
    out.sort((a, b) -> {
      int sizeCompare = Integer.compare(b.size(), a.size());
      if (sizeCompare != 0) {
        return sizeCompare;
      }
      return Long.compare(a.createdAt(), b.createdAt());
    });
    return List.copyOf(out);
  }

  public Result createParty(Player leader) {
    if (leader == null) {
      return new Result(false, Locales.component(null, "messages.party.error.playersOnly"));
    }
    if (!worldAllowed.test(leader.getWorld())) {
      return new Result(false, Locales.component(leader, "messages.party.error.onlyRpgWorld"));
    }
    if (!isRegionAllowed(leader.getLocation())) {
      return new Result(false, Locales.component(leader, "messages.party.error.regionRestricted"));
    }
    if (memberParties.containsKey(leader.getUniqueId())) {
      return new Result(false, Locales.component(leader, "messages.party.error.alreadyInParty"));
    }
    Party party = new Party(leader.getUniqueId(), leader.getWorld().getName(), leader.getWorld().getKey().toString());
    parties.put(party.id(), party);
    memberParties.put(leader.getUniqueId(), party);
    saveParty(party);
    audit("create", party, leader.getUniqueId(), leader.getName(), null, null, null);
    return new Result(true, Locales.component(leader, "messages.party.create"));
  }

  public InviteResult invite(Player leader, Player target) {
    if (leader == null || target == null) {
      return new InviteResult(false, Locales.component(null, "messages.party.error.targetNotFound"), null);
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.notInParty"), null);
    }
    if (!hasPermission(leader.getUniqueId(), Permission.INVITE)) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.notLeader"), null);
    }
    if (!worldAllowed.test(leader.getWorld())) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.onlyRpgWorld"), null);
    }
    if (!worldAllowed.test(target.getWorld())) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.onlyRpgWorld"), null);
    }
    if (!isRegionAllowed(leader.getLocation()) || !isRegionAllowed(target.getLocation())) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.regionRestricted"), null);
    }
    if (!allowCrossWorldInvite(leader.getWorld(), target.getWorld())) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.sameWorld"), null);
    }
    if (party.hasMember(target.getUniqueId())) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.alreadyInYourParty"), null);
    }
    if (memberParties.containsKey(target.getUniqueId())) {
      return new InviteResult(false, Locales.component(leader, "messages.party.error.targetAlreadyInParty"), null);
    }
    if (party.size() >= maxSize) {
      return new InviteResult(false, Locales.component(leader, "messages.party.full"), null);
    }
    PartyInvite invite = new PartyInvite(party.id(), party.leader(), leader.getName(),
        System.currentTimeMillis() + inviteMillis);
    invites.put(target.getUniqueId(), invite);
    saveInvite(target.getUniqueId(), invite);
    audit("invite", party, leader.getUniqueId(), leader.getName(), target.getUniqueId(), target.getName(), null);
    return new InviteResult(true, Locales.component(leader, "messages.party.result.inviteSent",
        Map.of("player", target.getName())), invite);
  }

  public Result acceptInvite(Player player, String leaderName) {
    if (player == null) {
      return new Result(false, Locales.component(null, "messages.party.error.playersOnly"));
    }
    PartyInvite invite = invites.get(player.getUniqueId());
    if (invite == null) {
      return new Result(false, Locales.component(player, "messages.party.error.inviteMissing"));
    }
    if (invite.expired()) {
      removeInvite(player.getUniqueId());
      return new Result(false, Locales.component(player, "messages.party.error.inviteExpired"));
    }
    if (leaderName != null && !invite.leaderName().equalsIgnoreCase(leaderName)) {
      return new Result(false, Locales.component(player, "messages.party.error.inviteLeaderNotFound"));
    }
    Party party = parties.get(invite.partyId());
    if (party == null) {
      removeInvite(player.getUniqueId());
      return new Result(false, Locales.component(player, "messages.party.error.partyMissing"));
    }
    if (!worldAllowed.test(player.getWorld())) {
      return new Result(false, Locales.component(player, "messages.party.error.onlyRpgWorld"));
    }
    if (!isRegionAllowed(player.getLocation())) {
      return new Result(false, Locales.component(player, "messages.party.error.regionRestricted"));
    }
    if (!allowsPartyWorld(player.getWorld(), party)) {
      return new Result(false, Locales.component(player, "messages.party.error.mustBeSameWorldLeader"));
    }
    if (party.size() >= maxSize) {
      return new Result(false, Locales.component(player, "messages.party.full"));
    }
    if (memberParties.containsKey(player.getUniqueId())) {
      return new Result(false, Locales.component(player, "messages.party.error.alreadyInParty"));
    }
    party.addMember(player.getUniqueId());
    memberParties.put(player.getUniqueId(), party);
    party.setRole(player.getUniqueId(), Party.Role.MEMBER);
    removeInvite(player.getUniqueId());
    saveParty(party);
    audit("accept_invite", party, player.getUniqueId(), player.getName(), invite.leaderId(), invite.leaderName(), null);
    broadcast(party, "messages.party.broadcast.join", Map.of("player", player.getName()));
    return new Result(true, Locales.component(player, "messages.party.join"));
  }

  public Result setPublic(Player leader, boolean open) {
    if (leader == null) {
      return new Result(false, Locales.component(null, "messages.party.error.playersOnly"));
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(leader, "messages.party.error.notInParty"));
    }
    if (!hasPermission(leader.getUniqueId(), Permission.MANAGE_PUBLIC)) {
      return new Result(false, Locales.component(leader, "messages.party.error.notLeader"));
    }
    party.setPublic(open);
    saveParty(party);
    return new Result(true, Locales.component(leader,
        open ? "messages.party.result.publicOn" : "messages.party.result.publicOff"));
  }

  public Result requestJoin(Player requester, UUID partyId) {
    if (requester == null) {
      return new Result(false, Locales.component(null, "messages.party.error.playersOnly"));
    }
    if (partyId == null) {
      return new Result(false, Locales.component(requester, "messages.party.error.partyMissing"));
    }
    if (memberParties.containsKey(requester.getUniqueId())) {
      return new Result(false, Locales.component(requester, "messages.party.error.alreadyInParty"));
    }
    Party party = parties.get(partyId);
    if (party == null) {
      return new Result(false, Locales.component(requester, "messages.party.error.partyMissing"));
    }
    if (!party.isPublic()) {
      return new Result(false, Locales.component(requester, "messages.party.error.notPublic"));
    }
    if (!worldAllowed.test(requester.getWorld())) {
      return new Result(false, Locales.component(requester, "messages.party.error.onlyRpgWorld"));
    }
    if (!isRegionAllowed(requester.getLocation())) {
      return new Result(false, Locales.component(requester, "messages.party.error.regionRestricted"));
    }
    if (!allowsPartyWorld(requester.getWorld(), party)) {
      return new Result(false, Locales.component(requester, "messages.party.error.mustBeSameWorldLeader"));
    }
    if (party.size() >= maxSize) {
      return new Result(false, Locales.component(requester, "messages.party.full"));
    }
    JoinRequest existing = joinRequests.get(requester.getUniqueId());
    if (existing != null) {
      if (existing.expired()) {
        joinRequests.remove(requester.getUniqueId());
      } else if (existing.partyId().equals(partyId)) {
        return new Result(false, Locales.component(requester, "messages.party.error.requestPending"));
      }
    }
    JoinRequest request = new JoinRequest(partyId, requester.getUniqueId(), requester.getName(),
        System.currentTimeMillis() + inviteMillis);
    joinRequests.put(requester.getUniqueId(), request);
    notifyJoinRequest(party, request);
    audit("request_join", party, requester.getUniqueId(), requester.getName(), party.leader(), leaderName(party), null);
    return new Result(true, Locales.component(requester, "messages.party.result.requestSent",
        Map.of("leader", leaderName(party))));
  }

  public Result acceptRequest(Player leader, String requesterName) {
    if (leader == null || requesterName == null) {
      return new Result(false, Locales.component(null, "messages.party.error.targetNotFound"));
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(leader, "messages.party.error.notInParty"));
    }
    if (!hasPermission(leader.getUniqueId(), Permission.INVITE)) {
      return new Result(false, Locales.component(leader, "messages.party.error.notLeader"));
    }
    Player requester = Bukkit.getPlayerExact(requesterName);
    if (requester == null) {
      return new Result(false, Locales.component(leader, "messages.party.error.targetNotFound"));
    }
    JoinRequest request = joinRequests.get(requester.getUniqueId());
    if (request == null || request.expired() || !request.partyId().equals(party.id())) {
      joinRequests.remove(requester.getUniqueId());
      return new Result(false, Locales.component(leader, "messages.party.error.requestMissing"));
    }
    if (memberParties.containsKey(requester.getUniqueId())) {
      joinRequests.remove(requester.getUniqueId());
      return new Result(false, Locales.component(leader, "messages.party.error.targetAlreadyInParty"));
    }
    if (party.size() >= maxSize) {
      return new Result(false, Locales.component(leader, "messages.party.full"));
    }
    party.addMember(requester.getUniqueId());
    party.setRole(requester.getUniqueId(), Party.Role.MEMBER);
    memberParties.put(requester.getUniqueId(), party);
    joinRequests.remove(requester.getUniqueId());
    saveParty(party);
    audit("request_accept", party, leader.getUniqueId(), leader.getName(), requester.getUniqueId(), requester.getName(),
        null);
    broadcast(party, "messages.party.broadcast.join", Map.of("player", requester.getName()));
    requester.sendMessage(Locales.component(requester, "messages.party.join"));
    return new Result(true, Locales.component(leader, "messages.party.result.requestAccepted",
        Map.of("player", requester.getName())));
  }

  public Result denyRequest(Player leader, String requesterName) {
    if (leader == null || requesterName == null) {
      return new Result(false, Locales.component(null, "messages.party.error.targetNotFound"));
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(leader, "messages.party.error.notInParty"));
    }
    if (!hasPermission(leader.getUniqueId(), Permission.INVITE)) {
      return new Result(false, Locales.component(leader, "messages.party.error.notLeader"));
    }
    Player requester = Bukkit.getPlayerExact(requesterName);
    if (requester == null) {
      return new Result(false, Locales.component(leader, "messages.party.error.targetNotFound"));
    }
    JoinRequest request = joinRequests.get(requester.getUniqueId());
    if (request == null || request.expired() || !request.partyId().equals(party.id())) {
      joinRequests.remove(requester.getUniqueId());
      return new Result(false, Locales.component(leader, "messages.party.error.requestMissing"));
    }
    joinRequests.remove(requester.getUniqueId());
    audit("request_deny", party, leader.getUniqueId(), leader.getName(), requester.getUniqueId(), requester.getName(),
        null);
    requester.sendMessage(Locales.component(requester, "messages.party.result.requestDenied"));
    return new Result(true, Locales.component(leader, "messages.party.result.requestDenied",
        Map.of("player", requester.getName())));
  }

  public Result leave(Player player) {
    return leave(player, true);
  }

  public Result leave(Player player, boolean notify) {
    if (player == null) {
      return new Result(false, Locales.component(null, "messages.party.error.playersOnly"));
    }
    Party party = memberParties.get(player.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(player, "messages.party.error.notInParty"));
    }
    boolean leader = party.leader().equals(player.getUniqueId());
    removeMember(party, player.getUniqueId());
    if (notify) {
      broadcast(party, "messages.party.broadcast.leave", Map.of("player", player.getName()));
    }
    if (party.size() == 0) {
      disband(party);
      return new Result(true, Locales.component(player, "messages.party.disband"));
    }
    if (leader) {
      UUID newLeader = party.members().iterator().next();
      party.leader(newLeader);
      party.setRole(newLeader, Party.Role.LEADER);
      Player leaderPlayer = Bukkit.getPlayer(newLeader);
      String leaderName = leaderPlayer != null ? leaderPlayer.getName() : newLeader.toString();
      broadcast(party, "messages.party.broadcast.newLeader", Map.of("player", leaderName));
    }
    saveParty(party);
    audit("leave", party, player.getUniqueId(), player.getName(), null, null, null);
    return new Result(true, Locales.component(player, "messages.party.leave"));
  }

  public boolean forceDisband(UUID partyId, UUID actorId, String actorName, String reason) {
    if (partyId == null) {
      return false;
    }
    Party party = parties.get(partyId);
    if (party == null) {
      return false;
    }
    String actor = actorName == null || actorName.isBlank() ? "admin" : actorName;
    broadcast(party, "messages.party.broadcast.disbanded", Map.of("actor", actor));
    audit("force_disband", party, actorId, actor, null, null, reason);
    disband(party);
    return true;
  }

  public Result kick(Player leader, Player target) {
    if (leader == null || target == null) {
      return new Result(false, Locales.component(null, "messages.party.error.targetNotFound"));
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(leader, "messages.party.error.notInParty"));
    }
    if (!hasPermission(leader.getUniqueId(), Permission.KICK)) {
      return new Result(false, Locales.component(leader, "messages.party.error.notLeader"));
    }
    if (!party.hasMember(target.getUniqueId())) {
      return new Result(false, Locales.component(leader, "messages.party.error.targetNotInParty"));
    }
    if (leader.getUniqueId().equals(target.getUniqueId())) {
      return new Result(false, Locales.component(leader, "messages.party.error.kickSelf"));
    }
    removeMember(party, target.getUniqueId());
    broadcast(party, "messages.party.broadcast.kicked", Map.of("player", target.getName()));
    target.sendMessage(Locales.component(target, "messages.party.kicked"));
    saveParty(party);
    audit("kick", party, leader.getUniqueId(), leader.getName(), target.getUniqueId(), target.getName(), null);
    return new Result(true, Locales.component(leader, "messages.party.result.kicked",
        Map.of("player", target.getName())));
  }

  public Result transferLeader(Player leader, Player target) {
    if (leader == null || target == null) {
      return new Result(false, Locales.component(null, "messages.party.error.targetNotFound"));
    }
    Party party = memberParties.get(leader.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(leader, "messages.party.error.notInParty"));
    }
    if (!hasPermission(leader.getUniqueId(), Permission.TRANSFER_LEADER)) {
      return new Result(false, Locales.component(leader, "messages.party.error.notLeader"));
    }
    if (!party.hasMember(target.getUniqueId())) {
      return new Result(false, Locales.component(leader, "messages.party.error.targetNotInParty"));
    }
    party.leader(target.getUniqueId());
    party.setRole(target.getUniqueId(), Party.Role.LEADER);
    broadcast(party, "messages.party.broadcast.newLeader", Map.of("player", target.getName()));
    saveParty(party);
    audit("transfer_leader", party, leader.getUniqueId(), leader.getName(), target.getUniqueId(), target.getName(), null);
    return new Result(true, Locales.component(leader, "messages.party.result.transfer"));
  }

  public Result toggleChat(Player player, boolean enabled) {
    if (player == null) {
      return new Result(false, Locales.component(null, "messages.party.error.playersOnly"));
    }
    Party party = memberParties.get(player.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(player, "messages.party.error.notInParty"));
    }
    if (enabled) {
      chatEnabled.add(player.getUniqueId());
      return new Result(true, Locales.component(player, "messages.party.chat.true"));
    }
    chatEnabled.remove(player.getUniqueId());
    return new Result(true, Locales.component(player, "messages.party.chat.false"));
  }

  public Result sendChat(Player sender, String message) {
    if (sender == null) {
      return new Result(false, Locales.component(null, "messages.party.error.playersOnly"));
    }
    Party party = memberParties.get(sender.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(sender, "messages.party.error.notInParty"));
    }
    if (message == null || message.isBlank()) {
      return new Result(false, Locales.component(sender, "messages.party.error.messageEmpty"));
    }
    broadcast(party, "messages.party.broadcast.chat", Map.of(
        "player", sender.getName(),
        "message", message));
    return new Result(true, Locales.component(sender, "messages.party.result.messageSent"));
  }

  public void handleQuit(Player player) {
    if (player == null) {
      return;
    }
    chatEnabled.remove(player.getUniqueId());
  }

  public void handleJoin(Player player) {
    if (player == null) {
      return;
    }
    PartyInvite invite = invites.get(player.getUniqueId());
    if (invite != null) {
      if (invite.expired()) {
        removeInvite(player.getUniqueId());
      } else {
        player.sendMessage(Locales.component(player, "messages.command.party.inviteReceived",
            Map.of("leader", invite.leaderName())));
        player.sendMessage(Locales.component(player, "messages.command.party.inviteHint",
            Map.of("leader", invite.leaderName())));
      }
    }
    Party party = memberParties.get(player.getUniqueId());
    if (party == null) {
      JoinRequest request = joinRequests.get(player.getUniqueId());
      if (request != null && request.expired()) {
        joinRequests.remove(player.getUniqueId());
      }
      return;
    }
    if (!worldAllowed.test(player.getWorld())) {
      leave(player, true);
      return;
    }
    if (!isRegionAllowed(player.getLocation())) {
      leave(player, true);
      return;
    }
    if (!allowsPartyWorld(player.getWorld(), party)) {
      leave(player, true);
    }
  }

  public Result promote(Player actor, Player target) {
    return setRole(actor, target, Party.Role.OFFICER);
  }

  public Result demote(Player actor, Player target) {
    return setRole(actor, target, Party.Role.MEMBER);
  }

  private Result setRole(Player actor, Player target, Party.Role role) {
    if (actor == null || target == null) {
      return new Result(false, Locales.component(null, "messages.party.error.targetNotFound"));
    }
    Party party = memberParties.get(actor.getUniqueId());
    if (party == null) {
      return new Result(false, Locales.component(actor, "messages.party.error.notInParty"));
    }
    if (!hasPermission(actor.getUniqueId(), Permission.MANAGE_ROLES)) {
      return new Result(false, Locales.component(actor, "messages.party.error.notLeader"));
    }
    if (!party.hasMember(target.getUniqueId())) {
      return new Result(false, Locales.component(actor, "messages.party.error.targetNotInParty"));
    }
    if (target.getUniqueId().equals(party.leader())) {
      return new Result(false, Locales.component(actor, "messages.party.error.notLeader"));
    }
    boolean changed = party.setRole(target.getUniqueId(), role);
    saveParty(party);
    if (changed) {
      broadcast(party, "messages.party.broadcast.roleChanged", Map.of(
          "player", target.getName(),
          "role", role.name().toLowerCase(java.util.Locale.ROOT)));
      audit("role_change", party, actor.getUniqueId(), actor.getName(), target.getUniqueId(), target.getName(),
          role.name().toLowerCase(java.util.Locale.ROOT));
    }
    return new Result(true, Locales.component(actor, "messages.party.result.roleChanged",
        Map.of("player", target.getName(), "role", role.name().toLowerCase(java.util.Locale.ROOT))));
  }

  public void handleWorldChange(Player player, World from, World to) {
    if (player == null) {
      return;
    }
    Party party = memberParties.get(player.getUniqueId());
    if (party == null) {
      return;
    }
    if (to == null || !worldAllowed.test(to)) {
      leave(player, true);
      return;
    }
    if (!isRegionAllowed(player.getLocation())) {
      leave(player, true);
      return;
    }
    if (worldPolicy == WorldPolicy.ANY_ALLOWED) {
      return;
    }
    if (worldPolicy == WorldPolicy.LEADER_WORLD && player.getUniqueId().equals(party.leader())) {
      party.setWorld(to.getName(), to.getKey().toString());
      saveParty(party);
      return;
    }
    if (!to.getName().equals(party.worldName())) {
      leave(player, true);
    }
  }

  public int inviteCount(UUID partyId) {
    if (partyId == null) {
      return 0;
    }
    int count = 0;
    for (PartyInvite invite : invites.values()) {
      if (invite != null && partyId.equals(invite.partyId())) {
        count++;
      }
    }
    return count;
  }

  public int requestCount(UUID partyId) {
    if (partyId == null) {
      return 0;
    }
    int count = 0;
    for (JoinRequest request : joinRequests.values()) {
      if (request != null && partyId.equals(request.partyId())) {
        count++;
      }
    }
    return count;
  }

  public CleanupResult cleanupStale() {
    int invitesRemoved = 0;
    int requestsRemoved = 0;
    int partiesDisbanded = 0;
    var inviteIter = invites.entrySet().iterator();
    while (inviteIter.hasNext()) {
      var entry = inviteIter.next();
      PartyInvite invite = entry.getValue();
      if (invite == null || invite.expired() || !parties.containsKey(invite.partyId())) {
        inviteIter.remove();
        if (repository != null && entry.getKey() != null) {
          repository.deleteInvite(entry.getKey());
        }
        invitesRemoved++;
      }
    }
    var requestIter = joinRequests.entrySet().iterator();
    while (requestIter.hasNext()) {
      var entry = requestIter.next();
      JoinRequest request = entry.getValue();
      if (request == null || request.expired() || !parties.containsKey(request.partyId())) {
        requestIter.remove();
        requestsRemoved++;
      }
    }
    for (Party party : List.copyOf(parties.values())) {
      if (party == null || party.members().isEmpty()) {
        if (party != null) {
          disband(party);
        }
        partiesDisbanded++;
      }
    }
    return new CleanupResult(invitesRemoved, requestsRemoved, partiesDisbanded);
  }

  private boolean allowCrossWorldInvite(World leaderWorld, World targetWorld) {
    if (worldPolicy == WorldPolicy.ANY_ALLOWED) {
      return true;
    }
    if (leaderWorld == null || targetWorld == null) {
      return false;
    }
    return leaderWorld.equals(targetWorld);
  }

  private boolean allowsPartyWorld(World world, Party party) {
    if (worldPolicy == WorldPolicy.ANY_ALLOWED) {
      return true;
    }
    if (world == null || party == null) {
      return false;
    }
    return world.getName().equals(party.worldName());
  }

  private boolean isRegionAllowed(Location location) {
    if ((allowedRegions == null || allowedRegions.isEmpty())
        && (deniedRegions == null || deniedRegions.isEmpty())) {
      return true;
    }
    if (location == null) {
      return false;
    }
    if (allowedRegions != null && !allowedRegions.isEmpty()) {
      boolean allowed = false;
      for (QuestRegion region : allowedRegions) {
        if (region != null && region.contains(location)) {
          allowed = true;
          break;
        }
      }
      if (!allowed) {
        return false;
      }
    }
    if (deniedRegions != null && !deniedRegions.isEmpty()) {
      for (QuestRegion region : deniedRegions) {
        if (region != null && region.contains(location)) {
          return false;
        }
      }
    }
    return true;
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
    joinRequests.entrySet().removeIf(entry -> entry.getValue().partyId().equals(party.id()));
    clearInvites(party.id());
    deleteParty(party.id());
  }

  private void clearInvites(UUID partyId) {
    invites.entrySet().removeIf(entry -> entry.getValue().partyId().equals(partyId));
    deleteInvitesForParty(partyId);
  }

  private void broadcast(Party party, String key, Map<String, String> placeholders) {
    for (UUID memberId : party.members()) {
      Player member = Bukkit.getPlayer(memberId);
      if (member != null) {
        member.sendMessage(Locales.component(member, key, placeholders));
      }
    }
  }

  private void audit(String action, Party party, UUID actorId, String actorName,
      UUID targetId, String targetName, String reason) {
    if (auditLog == null || party == null) {
      return;
    }
    auditLog.record(action, party, actorId, actorName, targetId, targetName, reason);
  }

  private void loadFromRepository() {
    if (repository == null) {
      return;
    }
    parties.clear();
    memberParties.clear();
    invites.clear();
    joinRequests.clear();
    Map<UUID, Party> loaded = repository.loadParties();
    for (Party party : loaded.values()) {
      if (party == null || party.members().isEmpty()) {
        if (party != null) {
          repository.deleteParty(party.id());
        }
        continue;
      }
      if (!party.hasMember(party.leader())) {
        UUID newLeader = party.members().iterator().next();
        party.leader(newLeader);
        repository.saveParty(party);
      }
      parties.put(party.id(), party);
      for (UUID member : party.members()) {
        memberParties.put(member, party);
        if (party.role(member) == null) {
          if (party.leader() != null && party.leader().equals(member)) {
            party.setRole(member, Party.Role.LEADER);
          } else {
            party.setRole(member, Party.Role.MEMBER);
          }
        }
      }
      saveParty(party);
    }
    Map<UUID, PartyInvite> loadedInvites = repository.loadInvites();
    for (Map.Entry<UUID, PartyInvite> entry : loadedInvites.entrySet()) {
      UUID targetId = entry.getKey();
      PartyInvite invite = entry.getValue();
      if (invite == null || invite.expired() || !parties.containsKey(invite.partyId())) {
        if (targetId != null) {
          repository.deleteInvite(targetId);
        }
        continue;
      }
      invites.put(targetId, invite);
    }
  }

  private void saveParty(Party party) {
    if (repository != null) {
      repository.saveParty(party);
    }
  }

  private void deleteParty(UUID partyId) {
    if (repository != null) {
      repository.deleteParty(partyId);
    }
  }

  private void saveInvite(UUID targetId, PartyInvite invite) {
    if (repository != null) {
      repository.saveInvite(targetId, invite);
    }
  }

  private void notifyJoinRequest(Party party, JoinRequest request) {
    if (party == null || request == null) {
      return;
    }
    String leaderName = request.requesterName();
    for (UUID memberId : party.members()) {
      if (!hasPermission(memberId, Permission.INVITE)) {
        continue;
      }
      Player member = Bukkit.getPlayer(memberId);
      if (member != null) {
        member.sendMessage(Locales.component(member, "messages.party.request.received",
            Map.of("player", leaderName)));
        member.sendMessage(Locales.component(member, "messages.party.request.hint",
            Map.of("player", leaderName)));
      }
    }
  }

  public String leaderName(Party party) {
    if (party == null || party.leader() == null) {
      return "unknown";
    }
    Player leaderPlayer = Bukkit.getPlayer(party.leader());
    if (leaderPlayer != null && leaderPlayer.getName() != null) {
      return leaderPlayer.getName();
    }
    return party.leader().toString();
  }

  private void removeInvite(UUID targetId) {
    invites.remove(targetId);
    if (repository != null) {
      repository.deleteInvite(targetId);
    }
  }

  private void deleteInvitesForParty(UUID partyId) {
    if (repository != null) {
      repository.deleteInvitesForParty(partyId);
    }
  }
}
