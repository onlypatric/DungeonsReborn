package dev.patric.dungeonsreborn.party;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.patric.dungeonsreborn.progression.ProgressionDatabase;

public final class PartyJdbcRepository implements PartyRepository {
  private static final String LOAD_PARTIES_SQL = """
      SELECT party_id, leader_uuid, world_name, world_key, created_at, public_open
      FROM party_state
      """;
  private static final String LOAD_MEMBERS_SQL = """
      SELECT party_id, member_uuid
      FROM party_members
      """;
  private static final String LOAD_ROLES_SQL = """
      SELECT party_id, member_uuid, role
      FROM party_roles
      """;
  private static final String LOAD_INVITES_SQL = """
      SELECT target_uuid, party_id, leader_uuid, leader_name, expires_at
      FROM party_invites
      """;
  private static final String UPSERT_PARTY_SQL = """
      INSERT INTO party_state (party_id, leader_uuid, world_name, world_key, created_at, public_open)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT(party_id) DO UPDATE SET
        leader_uuid = excluded.leader_uuid,
        world_name = excluded.world_name,
        world_key = excluded.world_key,
        created_at = excluded.created_at,
        public_open = excluded.public_open
      """;
  private static final String DELETE_MEMBERS_SQL = """
      DELETE FROM party_members WHERE party_id = ?
      """;
  private static final String DELETE_ROLES_SQL = """
      DELETE FROM party_roles WHERE party_id = ?
      """;
  private static final String INSERT_MEMBER_SQL = """
      INSERT INTO party_members (party_id, member_uuid, joined_at)
      VALUES (?, ?, ?)
      ON CONFLICT(party_id, member_uuid) DO UPDATE SET
        joined_at = excluded.joined_at
      """;
  private static final String INSERT_ROLE_SQL = """
      INSERT INTO party_roles (party_id, member_uuid, role)
      VALUES (?, ?, ?)
      ON CONFLICT(party_id, member_uuid) DO UPDATE SET
        role = excluded.role
      """;
  private static final String DELETE_PARTY_SQL = """
      DELETE FROM party_state WHERE party_id = ?
      """;
  private static final String DELETE_INVITES_PARTY_SQL = """
      DELETE FROM party_invites WHERE party_id = ?
      """;
  private static final String DELETE_INVITE_SQL = """
      DELETE FROM party_invites WHERE target_uuid = ?
      """;
  private static final String UPSERT_INVITE_SQL = """
      INSERT INTO party_invites (target_uuid, party_id, leader_uuid, leader_name, expires_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(target_uuid) DO UPDATE SET
        party_id = excluded.party_id,
        leader_uuid = excluded.leader_uuid,
        leader_name = excluded.leader_name,
        expires_at = excluded.expires_at
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public PartyJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public Map<UUID, Party> loadParties() {
    Map<UUID, Party> parties = new LinkedHashMap<>();
    if (database.connection() == null) {
      return parties;
    }
    Map<UUID, Set<UUID>> members = new LinkedHashMap<>();
    Map<UUID, Map<UUID, Party.Role>> roles = new LinkedHashMap<>();
    try (PreparedStatement statement = database.connection().prepareStatement(LOAD_MEMBERS_SQL);
         ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        UUID partyId = UUID.fromString(rs.getString(1));
        UUID memberId = UUID.fromString(rs.getString(2));
        members.computeIfAbsent(partyId, key -> new LinkedHashSet<>()).add(memberId);
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to load party members", ex);
    }
    try (PreparedStatement statement = database.connection().prepareStatement(LOAD_ROLES_SQL);
         ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        UUID partyId = UUID.fromString(rs.getString(1));
        UUID memberId = UUID.fromString(rs.getString(2));
        String roleRaw = rs.getString(3);
        Party.Role role;
        try {
          role = Party.Role.valueOf(roleRaw);
        } catch (Exception ex) {
          role = Party.Role.MEMBER;
        }
        roles.computeIfAbsent(partyId, key -> new LinkedHashMap<>()).put(memberId, role);
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to load party roles", ex);
    }
    try (PreparedStatement statement = database.connection().prepareStatement(LOAD_PARTIES_SQL);
         ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        UUID partyId = UUID.fromString(rs.getString(1));
        UUID leaderId = UUID.fromString(rs.getString(2));
        String worldName = rs.getString(3);
        String worldKey = rs.getString(4);
        long createdAt = rs.getLong(5);
        boolean publicOpen = rs.getInt(6) != 0;
        Set<UUID> partyMembers = members.getOrDefault(partyId, Set.of());
        Map<UUID, Party.Role> partyRoles = roles.getOrDefault(partyId, Map.of());
        parties.put(partyId,
            new Party(partyId, leaderId, partyMembers, partyRoles, worldName, worldKey, createdAt, publicOpen));
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to load parties", ex);
    }
    return parties;
  }

  @Override
  public Map<UUID, PartyInvite> loadInvites() {
    Map<UUID, PartyInvite> invites = new LinkedHashMap<>();
    if (database.connection() == null) {
      return invites;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(LOAD_INVITES_SQL);
         ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        UUID targetId = UUID.fromString(rs.getString(1));
        UUID partyId = UUID.fromString(rs.getString(2));
        UUID leaderId = UUID.fromString(rs.getString(3));
        String leaderName = rs.getString(4);
        long expiresAt = rs.getLong(5);
        invites.put(targetId, new PartyInvite(partyId, leaderId, leaderName, expiresAt));
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to load invites", ex);
    }
    return invites;
  }

  @Override
  public void saveParty(Party party) {
    if (party == null || database.connection() == null) {
      return;
    }
    boolean autoCommit = true;
    try {
      autoCommit = database.connection().getAutoCommit();
      database.connection().setAutoCommit(false);
      try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_PARTY_SQL)) {
        statement.setString(1, party.id().toString());
        statement.setString(2, party.leader().toString());
        statement.setString(3, party.worldName());
        statement.setString(4, party.worldKey());
        statement.setLong(5, party.createdAt());
        statement.setInt(6, party.isPublic() ? 1 : 0);
        statement.executeUpdate();
      }
      try (PreparedStatement deleteMembers = database.connection().prepareStatement(DELETE_MEMBERS_SQL)) {
        deleteMembers.setString(1, party.id().toString());
        deleteMembers.executeUpdate();
      }
      try (PreparedStatement deleteRoles = database.connection().prepareStatement(DELETE_ROLES_SQL)) {
        deleteRoles.setString(1, party.id().toString());
        deleteRoles.executeUpdate();
      }
      try (PreparedStatement insertMember = database.connection().prepareStatement(INSERT_MEMBER_SQL)) {
        long now = System.currentTimeMillis();
        for (UUID member : party.members()) {
          insertMember.setString(1, party.id().toString());
          insertMember.setString(2, member.toString());
          insertMember.setLong(3, now);
          insertMember.addBatch();
        }
        insertMember.executeBatch();
      }
      try (PreparedStatement insertRole = database.connection().prepareStatement(INSERT_ROLE_SQL)) {
        for (Map.Entry<UUID, Party.Role> entry : party.roles().entrySet()) {
          insertRole.setString(1, party.id().toString());
          insertRole.setString(2, entry.getKey().toString());
          insertRole.setString(3, entry.getValue().name());
          insertRole.addBatch();
        }
        insertRole.executeBatch();
      }
      database.connection().commit();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to save party state", ex);
      try {
        database.connection().rollback();
      } catch (SQLException rollbackEx) {
        logger.log(Level.WARNING, "[Party] Failed to rollback party save", rollbackEx);
      }
    } finally {
      try {
        database.connection().setAutoCommit(autoCommit);
      } catch (SQLException ex) {
        logger.log(Level.WARNING, "[Party] Failed to restore auto-commit", ex);
      }
    }
  }

  @Override
  public void deleteParty(UUID partyId) {
    if (partyId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement deleteParty = database.connection().prepareStatement(DELETE_PARTY_SQL);
         PreparedStatement deleteMembers = database.connection().prepareStatement(DELETE_MEMBERS_SQL);
         PreparedStatement deleteInvites = database.connection().prepareStatement(DELETE_INVITES_PARTY_SQL)) {
      deleteParty.setString(1, partyId.toString());
      deleteParty.executeUpdate();
      deleteMembers.setString(1, partyId.toString());
      deleteMembers.executeUpdate();
      deleteInvites.setString(1, partyId.toString());
      deleteInvites.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to delete party state", ex);
    }
  }

  @Override
  public void saveInvite(UUID targetId, PartyInvite invite) {
    if (targetId == null || invite == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_INVITE_SQL)) {
      statement.setString(1, targetId.toString());
      statement.setString(2, invite.partyId().toString());
      statement.setString(3, invite.leaderId().toString());
      statement.setString(4, invite.leaderName());
      statement.setLong(5, invite.expiresAt());
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to save party invite", ex);
    }
  }

  @Override
  public void deleteInvite(UUID targetId) {
    if (targetId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(DELETE_INVITE_SQL)) {
      statement.setString(1, targetId.toString());
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to delete party invite", ex);
    }
  }

  @Override
  public void deleteInvitesForParty(UUID partyId) {
    if (partyId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(DELETE_INVITES_PARTY_SQL)) {
      statement.setString(1, partyId.toString());
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Party] Failed to delete party invites", ex);
    }
  }
}
