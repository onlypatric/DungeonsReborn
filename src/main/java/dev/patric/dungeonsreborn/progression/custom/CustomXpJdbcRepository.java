package dev.patric.dungeonsreborn.progression.custom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.patric.dungeonsreborn.progression.ProgressionDatabase;

public final class CustomXpJdbcRepository implements CustomXpRepository {
  private static final String SELECT_SQL = """
      SELECT points, level, last_update
      FROM player_custom_xp
      WHERE uuid = ?
      """;
  private static final String UPSERT_SQL = """
      INSERT INTO player_custom_xp (uuid, points, level, last_update)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(uuid) DO UPDATE SET
        points=excluded.points,
        level=excluded.level,
        last_update=excluded.last_update
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public CustomXpJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = database;
    this.logger = logger;
  }

  @Override
  public Optional<CustomXpProfile> load(UUID uuid) {
    if (uuid == null) {
      return Optional.empty();
    }
    synchronized (database) {
      try (PreparedStatement statement = database.connection().prepareStatement(SELECT_SQL)) {
        statement.setString(1, uuid.toString());
        try (ResultSet rs = statement.executeQuery()) {
          if (!rs.next()) {
            return Optional.empty();
          }
          long points = rs.getLong("points");
          int level = rs.getInt("level");
          long lastUpdate = rs.getLong("last_update");
          return Optional.of(new CustomXpProfile(uuid, points, level, lastUpdate));
        }
      } catch (SQLException ex) {
        logger.log(Level.WARNING, "[Progression] Failed to load custom XP", ex);
        return Optional.empty();
      }
    }
  }

  @Override
  public void save(CustomXpProfile profile) {
    if (profile == null) {
      return;
    }
    synchronized (database) {
      try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_SQL)) {
        bind(statement, profile);
        statement.executeUpdate();
      } catch (SQLException ex) {
        logger.log(Level.WARNING, "[Progression] Failed to save custom XP", ex);
        throw new RuntimeException(ex);
      }
    }
  }

  @Override
  public void saveAll(Collection<CustomXpProfile> profiles) {
    if (profiles == null || profiles.isEmpty()) {
      return;
    }
    synchronized (database) {
      Connection connection = database.connection();
      boolean previousAutoCommit = true;
      try {
        previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
          for (CustomXpProfile profile : profiles) {
            if (profile == null) {
              continue;
            }
            bind(statement, profile);
            statement.addBatch();
          }
          statement.executeBatch();
        }
        connection.commit();
      } catch (SQLException ex) {
        try {
          connection.rollback();
        } catch (SQLException rollback) {
          logger.log(Level.WARNING, "[Progression] Failed to rollback custom XP save", rollback);
        }
        logger.log(Level.WARNING, "[Progression] Failed to save custom XP batch", ex);
        throw new RuntimeException(ex);
      } finally {
        try {
          connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ex) {
          logger.log(Level.WARNING, "[Progression] Failed to restore autocommit", ex);
        }
      }
    }
  }

  private void bind(PreparedStatement statement, CustomXpProfile profile) throws SQLException {
    statement.setString(1, profile.uuid().toString());
    statement.setLong(2, profile.points());
    statement.setInt(3, profile.level());
    statement.setLong(4, profile.lastUpdateMillis());
  }
}
