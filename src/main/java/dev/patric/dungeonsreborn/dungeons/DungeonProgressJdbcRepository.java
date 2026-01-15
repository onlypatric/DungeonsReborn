package dev.patric.dungeonsreborn.dungeons;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

import dev.patric.dungeonsreborn.progression.ProgressionDatabase;

public final class DungeonProgressJdbcRepository implements DungeonProgressRepository {
  private static final String SELECT_SQL = """
      SELECT max_level
      FROM player_dungeon_progress
      WHERE uuid = ? AND dungeon_id = ?
      """;
  private static final String UPSERT_SQL = """
      INSERT INTO player_dungeon_progress (uuid, dungeon_id, max_level, last_update)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(uuid, dungeon_id) DO UPDATE SET
        max_level = CASE
          WHEN excluded.max_level > max_level THEN excluded.max_level
          ELSE max_level
        END,
        last_update = excluded.last_update
      """;

  private final ProgressionDatabase database;
  private final ServiceLogger logger;

  public DungeonProgressJdbcRepository(ProgressionDatabase database, ServiceLogger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public int maxCompleted(UUID uuid, String dungeonId) {
    if (uuid == null || dungeonId == null || database.connection() == null) {
      return 0;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(SELECT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, dungeonId);
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) {
          return Math.max(0, rs.getInt(1));
        }
      }
    } catch (SQLException ex) {
      logger.warn("[Dungeon] Failed to load progress", ex);
    }
    return 0;
  }

  @Override
  public void recordCompletion(UUID uuid, String dungeonId, int level) {
    if (uuid == null || dungeonId == null || database.connection() == null) {
      return;
    }
    int normalized = Math.max(0, level);
    try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, dungeonId);
      statement.setInt(3, normalized);
      statement.setLong(4, System.currentTimeMillis());
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.warn("[Dungeon] Failed to save progress", ex);
    }
  }
}
