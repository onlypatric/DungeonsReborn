package dev.patric.dungeonsreborn.party;

import java.util.Map;
import java.util.UUID;

public interface PartyRepository {
  Map<UUID, Party> loadParties();

  Map<UUID, PartyInvite> loadInvites();

  void saveParty(Party party);

  void deleteParty(UUID partyId);

  void saveInvite(UUID targetId, PartyInvite invite);

  void deleteInvite(UUID targetId);

  void deleteInvitesForParty(UUID partyId);
}
