package dev.patric.dungeonsreborn.classes.skills;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.patric.dungeonsreborn.progression.ProgressionDatabase;

public final class ClassSkillPresetJdbcRepository implements ClassSkillPresetRepository {
  private static final String SELECT_ALL_SQL = """
      SELECT preset_id, name, nodes, updated_at
      FROM player_class_presets
      WHERE uuid = ? AND class_id = ?
      ORDER BY updated_at DESC
      """;
  private static final String SELECT_ONE_SQL = """
      SELECT name, nodes, updated_at
      FROM player_class_presets
      WHERE uuid = ? AND class_id = ? AND preset_id = ?
      """;
  private static final String UPSERT_SQL = """
      INSERT INTO player_class_presets (uuid, class_id, preset_id, name, nodes, updated_at)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT(uuid, class_id, preset_id) DO UPDATE SET
        name=excluded.name,
        nodes=excluded.nodes,
        updated_at=excluded.updated_at
      """;
  private static final String DELETE_SQL = """
      DELETE FROM player_class_presets
      WHERE uuid = ? AND class_id = ? AND preset_id = ?
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public ClassSkillPresetJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public List<ClassSkillPreset> list(UUID uuid, String classId) {
    if (uuid == null || classId == null || database.connection() == null) {
      return List.of();
    }
    List<ClassSkillPreset> out = new ArrayList<>();
    try (PreparedStatement statement = database.connection().prepareStatement(SELECT_ALL_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          String presetId = rs.getString(1);
          String name = rs.getString(2);
          String nodesRaw = rs.getString(3);
          long updatedAt = rs.getLong(4);
          Map<String, Integer> nodes = decode(nodesRaw);
          out.add(new ClassSkillPreset(presetId, name, nodes, updatedAt));
        }
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to load skill presets", ex);
      return List.of();
    }
    return List.copyOf(out);
  }

  @Override
  public ClassSkillPreset load(UUID uuid, String classId, String presetId) {
    if (uuid == null || classId == null || presetId == null || database.connection() == null) {
      return null;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(SELECT_ONE_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      statement.setString(3, presetId);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return null;
        }
        String name = rs.getString(1);
        String nodesRaw = rs.getString(2);
        long updatedAt = rs.getLong(3);
        return new ClassSkillPreset(presetId, name, decode(nodesRaw), updatedAt);
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to load skill preset", ex);
      return null;
    }
  }

  @Override
  public void save(UUID uuid, String classId, ClassSkillPreset preset) {
    if (uuid == null || classId == null || preset == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      statement.setString(3, preset.id());
      statement.setString(4, preset.name());
      statement.setString(5, encode(preset.nodes()));
      statement.setLong(6, preset.updatedAt());
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to save skill preset", ex);
    }
  }

  @Override
  public void delete(UUID uuid, String classId, String presetId) {
    if (uuid == null || classId == null || presetId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(DELETE_SQL)) {
      statement.setString(1, uuid.toString());
      statement.setString(2, classId);
      statement.setString(3, presetId);
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Classes] Failed to delete skill preset", ex);
    }
  }

  private static String encode(Map<String, Integer> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (Map.Entry<String, Integer> entry : nodes.entrySet()) {
      String node = entry.getKey();
      if (node == null || node.isBlank()) {
        continue;
      }
      int rank = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
      if (rank <= 0) {
        continue;
      }
      if (out.length() > 0) {
        out.append(';');
      }
      out.append(node.replace(";", "_").replace("=", "_"))
          .append('=')
          .append(rank);
    }
    return out.toString();
  }

  private static Map<String, Integer> decode(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    Map<String, Integer> out = new LinkedHashMap<>();
    String[] parts = raw.split(";");
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      String[] entry = part.split("=", 2);
      if (entry.length != 2) {
        continue;
      }
      String node = entry[0].trim();
      if (node.isEmpty()) {
        continue;
      }
      try {
        int rank = Integer.parseInt(entry[1].trim());
        if (rank > 0) {
          out.put(node, rank);
        }
      } catch (NumberFormatException ignored) {
        // ignore malformed rank
      }
    }
    return Map.copyOf(out);
  }
}
