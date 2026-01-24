package dev.patric.dungeonsreborn.classes.skills;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.patric.dungeonsreborn.progression.ProgressionDatabase;

public final class ClassSkillJdbcRepository implements ClassSkillRepository {
  private static final String SELECT_SQL = """
      SELECT node_id, rank
      FROM player_class_skills
      WHERE uuid = ? AND class_id = ?
      """;
  private static final String UPSERT_SQL = """
      INSERT INTO player_class_skills (uuid, class_id, node_id, rank)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(uuid, class_id, node_id) DO UPDATE SET rank=excluded.rank
      """;
  private static final String DELETE_SQL = """
      DELETE FROM player_class_skills
      WHERE uuid = ? AND class_id = ? AND node_id = ?
      """;
  private static final String CLEAR_SQL = """
      DELETE FROM player_class_skills
      WHERE uuid = ? AND class_id = ?
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public ClassSkillJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public Map<String, Integer> load(UUID uuid, String classId) {
    if (uuid == null || classId == null || database.connection() == null) {
      return Map.of();
    }
    Map<String, Integer> out = new LinkedHashMap<>();
    try (PreparedStatement statement = database.connection().prepareStatement(SELECT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          String node = rs.getString(1);
          int rank = rs.getInt(2);
          if (node != null && !node.isBlank()) {
            out.put(node, Math.max(0, rank));
          }
        }
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to load skill nodes", ex);
      return Map.of();
    }
    return out;
  }

  @Override
  public void setRank(UUID uuid, String classId, String nodeId, int rank) {
    if (uuid == null || classId == null || nodeId == null || database.connection() == null) {
      return;
    }
    int safeRank = Math.max(0, rank);
    if (safeRank <= 0) {
      remove(uuid, classId, nodeId);
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      statement.setString(3, nodeId);
      statement.setInt(4, safeRank);
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to save skill node rank", ex);
    }
  }

  @Override
  public void remove(UUID uuid, String classId, String nodeId) {
    if (uuid == null || classId == null || nodeId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(DELETE_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      statement.setString(3, nodeId);
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to remove skill node", ex);
    }
  }

  @Override
  public void clear(UUID uuid, String classId) {
    if (uuid == null || classId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(CLEAR_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to clear skill nodes", ex);
    }
  }
}
