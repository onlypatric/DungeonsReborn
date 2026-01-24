package dev.patric.dungeonsreborn.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {
  public enum Role {
    LEADER,
    OFFICER,
    MEMBER
  }

  private final UUID id;
  private UUID leader;
  private final LinkedHashSet<UUID> members;
  private final java.util.Map<UUID, Role> roles;
  private String worldName;
  private String worldKey;
  private final long createdAt;
  private boolean publicOpen;

  Party(UUID leader, String worldName, String worldKey) {
    this(UUID.randomUUID(), leader, Set.of(leader), java.util.Map.of(), worldName, worldKey, System.currentTimeMillis(),
        false);
  }

  Party(UUID id, UUID leader, Set<UUID> members, java.util.Map<UUID, Role> roles, String worldName, String worldKey,
      long createdAt, boolean publicOpen) {
    this.id = id;
    this.leader = leader;
    this.members = new LinkedHashSet<>();
    this.roles = new java.util.HashMap<>();
    if (members != null) {
      this.members.addAll(members);
    }
    if (leader != null) {
      this.members.add(leader);
    }
    if (roles != null) {
      this.roles.putAll(roles);
    }
    if (leader != null) {
      this.roles.put(leader, Role.LEADER);
    }
    this.worldName = worldName;
    this.worldKey = worldKey;
    this.createdAt = createdAt;
    this.publicOpen = publicOpen;
  }

  public UUID id() {
    return id;
  }

  public UUID leader() {
    return leader;
  }

  public void leader(UUID leader) {
    this.leader = leader;
    if (leader != null) {
      roles.put(leader, Role.LEADER);
    }
  }

  public Set<UUID> members() {
    return Set.copyOf(members);
  }

  public boolean hasMember(UUID member) {
    return members.contains(member);
  }

  public boolean addMember(UUID member) {
    if (member == null) {
      return false;
    }
    boolean added = members.add(member);
    if (added) {
      roles.putIfAbsent(member, Role.MEMBER);
    }
    return added;
  }

  public boolean removeMember(UUID member) {
    roles.remove(member);
    return members.remove(member);
  }

  public Role role(UUID member) {
    if (member == null) {
      return null;
    }
    return roles.get(member);
  }

  public boolean setRole(UUID member, Role role) {
    if (member == null || role == null || !members.contains(member)) {
      return false;
    }
    if (leader != null && leader.equals(member)) {
      roles.put(member, Role.LEADER);
      return role == Role.LEADER;
    }
    roles.put(member, role);
    return true;
  }

  public java.util.Map<UUID, Role> roles() {
    return java.util.Map.copyOf(roles);
  }

  public int size() {
    return members.size();
  }

  public String worldName() {
    return worldName;
  }

  public String worldKey() {
    return worldKey;
  }

  public void setWorld(String worldName, String worldKey) {
    this.worldName = worldName;
    this.worldKey = worldKey;
  }

  public long createdAt() {
    return createdAt;
  }

  public boolean isPublic() {
    return publicOpen;
  }

  public void setPublic(boolean publicOpen) {
    this.publicOpen = publicOpen;
  }
}
