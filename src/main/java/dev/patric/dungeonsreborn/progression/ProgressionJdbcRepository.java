package dev.patric.dungeonsreborn.progression;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProgressionJdbcRepository implements ProgressionRepository {
  private static final String SELECT_SQL = """
      SELECT points, level, skill_points, max_mana, last_update,
        stat_strength, stat_dexterity, stat_intelligence, stat_vitality, skill_tree_points
      FROM player_progression
      WHERE uuid = ?
      """;
  private static final String UPSERT_SQL = """
      INSERT INTO player_progression (uuid, points, level, skill_points, max_mana, last_update,
        stat_strength, stat_dexterity, stat_intelligence, stat_vitality, skill_tree_points)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(uuid) DO UPDATE SET
        points=excluded.points,
        level=excluded.level,
        skill_points=excluded.skill_points,
        max_mana=excluded.max_mana,
        last_update=excluded.last_update,
        stat_strength=excluded.stat_strength,
        stat_dexterity=excluded.stat_dexterity,
        stat_intelligence=excluded.stat_intelligence,
        stat_vitality=excluded.stat_vitality,
        skill_tree_points=excluded.skill_tree_points
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public ProgressionJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = database;
    this.logger = logger;
  }

  @Override
  public Optional<PlayerProgression> load(UUID uuid) {
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
          int skillPoints = rs.getInt("skill_points");
          int maxMana = rs.getInt("max_mana");
          long lastUpdate = rs.getLong("last_update");
          int strength = rs.getInt("stat_strength");
          int dexterity = rs.getInt("stat_dexterity");
          int intelligence = rs.getInt("stat_intelligence");
          int vitality = rs.getInt("stat_vitality");
          int skillTreePoints = rs.getInt("skill_tree_points");
          return Optional.of(new PlayerProgression(uuid, points, level, skillPoints, maxMana, lastUpdate,
              strength, dexterity, intelligence, vitality, skillTreePoints));
        }
      } catch (SQLException ex) {
        logger.log(Level.WARNING, "[Progression] Failed to load player progression", ex);
        return Optional.empty();
      }
    }
  }

  @Override
  public void save(PlayerProgression progression) {
    if (progression == null) {
      return;
    }
    synchronized (database) {
      try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_SQL)) {
        bind(statement, progression);
        statement.executeUpdate();
      } catch (SQLException ex) {
        logger.log(Level.WARNING, "[Progression] Failed to save player progression", ex);
        throw new RuntimeException(ex);
      }
    }
  }

  @Override
  public void saveAll(Collection<PlayerProgression> progressions) {
    if (progressions == null || progressions.isEmpty()) {
      return;
    }
    synchronized (database) {
      Connection connection = database.connection();
      boolean previousAutoCommit = true;
      try {
        previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
          for (PlayerProgression progression : progressions) {
            if (progression == null) {
              continue;
            }
            bind(statement, progression);
            statement.addBatch();
          }
          statement.executeBatch();
        }
        connection.commit();
      } catch (SQLException ex) {
        try {
          connection.rollback();
        } catch (SQLException rollback) {
          logger.log(Level.WARNING, "[Progression] Failed to rollback progression save", rollback);
        }
        logger.log(Level.WARNING, "[Progression] Failed to save progression batch", ex);
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

  private void bind(PreparedStatement statement, PlayerProgression progression) throws SQLException {
    statement.setString(1, progression.uuid().toString());
    statement.setLong(2, progression.points());
    statement.setInt(3, progression.level());
    statement.setInt(4, progression.skillPoints());
    statement.setInt(5, progression.maxMana());
    statement.setLong(6, progression.lastUpdateMillis());
    statement.setInt(7, progression.strength());
    statement.setInt(8, progression.dexterity());
    statement.setInt(9, progression.intelligence());
    statement.setInt(10, progression.vitality());
    statement.setInt(11, progression.skillTreePoints());
  }
}
