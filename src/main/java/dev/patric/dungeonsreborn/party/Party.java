package dev.patric.dungeonsreborn.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {
  private final UUID id;
  private UUID leader;
  private final LinkedHashSet<UUID> members;
  private final String worldName;
  private final String worldKey;
  private final long createdAt;

  Party(UUID leader, String worldName, String worldKey) {
    this.id = UUID.randomUUID();
    this.leader = leader;
    this.members = new LinkedHashSet<>();
    this.members.add(leader);
    this.worldName = worldName;
    this.worldKey = worldKey;
    this.createdAt = System.currentTimeMillis();
  }

  public UUID id() {
    return id;
  }

  public UUID leader() {
    return leader;
  }

  public void leader(UUID leader) {
    this.leader = leader;
  }

  public Set<UUID> members() {
    return Set.copyOf(members);
  }

  public boolean hasMember(UUID member) {
    return members.contains(member);
  }

  public boolean addMember(UUID member) {
    return members.add(member);
  }

  public boolean removeMember(UUID member) {
    return members.remove(member);
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

  public long createdAt() {
    return createdAt;
  }
}
