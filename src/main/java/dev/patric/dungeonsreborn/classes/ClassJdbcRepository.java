package dev.patric.dungeonsreborn.classes;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.patric.dungeonsreborn.progression.ProgressionDatabase;

public final class ClassJdbcRepository implements ClassSelectionRepository {
  private static final String SELECT_SQL = """
      SELECT class_id
      FROM player_classes
      WHERE uuid = ?
      """;
  private static final String UPSERT_SQL = """
      INSERT INTO player_classes (uuid, class_id, last_update)
      VALUES (?, ?, ?)
      ON CONFLICT(uuid) DO UPDATE SET class_id=excluded.class_id, last_update=excluded.last_update
      """;
  private static final String DELETE_SQL = """
      DELETE FROM player_classes
      WHERE uuid = ?
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public ClassJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = database;
    this.logger = logger;
  }

  @Override
  public Optional<String> load(UUID uuid) {
    if (uuid == null || database.connection() == null) {
      return Optional.empty();
    }
    try (PreparedStatement statement = database.connection().prepareStatement(SELECT_SQL)) {
      statement.setString(1, uuid.toString());
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        String classId = rs.getString(1);
        return classId == null || classId.isBlank() ? Optional.empty() : Optional.of(classId);
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to load class selection", ex);
      return Optional.empty();
    }
  }

  @Override
  public void save(UUID uuid, String classId, long whenMillis) {
    if (uuid == null || classId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      statement.setLong(3, Math.max(0L, whenMillis));
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to save class selection", ex);
    }
  }

  @Override
  public void clear(UUID uuid) {
    if (uuid == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(DELETE_SQL)) {
      statement.setString(1, uuid.toString());
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to clear class selection", ex);
    }
  }
}
