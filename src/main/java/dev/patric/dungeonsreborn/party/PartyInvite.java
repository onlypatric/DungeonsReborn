package dev.patric.dungeonsreborn.party;

import java.util.UUID;

public record PartyInvite(UUID partyId, UUID leaderId, String leaderName, long expiresAt) {
  public boolean expired() {
    return System.currentTimeMillis() > expiresAt;
  }
}
