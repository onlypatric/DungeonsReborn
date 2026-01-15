package dev.patric.dungeonsreborn.kits;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.patric.dungeonsreborn.progression.ProgressionDatabase;

public final class KitJdbcRepository implements KitClaimRepository {
  private static final String SELECT_SQL = """
      SELECT last_claim
      FROM kit_claims
      WHERE uuid=? AND kit_id=?
      """;
  private static final String UPSERT_SQL = """
      INSERT INTO kit_claims (uuid, kit_id, last_claim)
      VALUES (?, ?, ?)
      ON CONFLICT(uuid, kit_id) DO UPDATE SET last_claim=excluded.last_claim
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public KitJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public OptionalLong lastClaim(UUID uuid, String kitId) {
    if (uuid == null || kitId == null) {
      return OptionalLong.empty();
    }
    Connection connection = database.connection();
    if (connection == null) {
      return OptionalLong.empty();
    }
    try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, kitId);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return OptionalLong.empty();
        }
        return OptionalLong.of(rs.getLong(1));
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Kits] Failed to load kit claim", ex);
      return OptionalLong.empty();
    }
  }

  @Override
  public void markClaimed(UUID uuid, String kitId, long whenMillis) {
    if (uuid == null || kitId == null) {
      return;
    }
    Connection connection = database.connection();
    if (connection == null) {
      return;
    }
    try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, kitId);
      statement.setLong(3, Math.max(0L, whenMillis));
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Kits] Failed to update kit claim", ex);
    }
  }
}
