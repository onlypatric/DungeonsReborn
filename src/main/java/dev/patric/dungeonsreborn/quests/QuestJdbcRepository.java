package dev.patric.dungeonsreborn.quests;

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

public final class QuestJdbcRepository implements QuestRepository {
  private static final String LOAD_QUESTS_SQL = """
      SELECT quest_id, status, started_at, completed_at, cooldown_until,
             daily_count, weekly_count, daily_reset_at, weekly_reset_at
      FROM player_quests
      WHERE uuid = ?
      """;
  private static final String LOAD_PROGRESS_SQL = """
      SELECT quest_id, objective_index, progress
      FROM player_quest_progress
      WHERE uuid = ?
      """;
  private static final String UPSERT_QUEST_SQL = """
      INSERT INTO player_quests (uuid, quest_id, status, started_at, completed_at, cooldown_until,
                                 daily_count, weekly_count, daily_reset_at, weekly_reset_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(uuid, quest_id) DO UPDATE SET
        status = excluded.status,
        started_at = excluded.started_at,
        completed_at = excluded.completed_at,
        cooldown_until = excluded.cooldown_until,
        daily_count = excluded.daily_count,
        weekly_count = excluded.weekly_count,
        daily_reset_at = excluded.daily_reset_at,
        weekly_reset_at = excluded.weekly_reset_at
      """;
  private static final String UPSERT_PROGRESS_SQL = """
      INSERT INTO player_quest_progress (uuid, quest_id, objective_index, progress)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(uuid, quest_id, objective_index) DO UPDATE SET
        progress = excluded.progress
      """;

  private final ProgressionDatabase database;
  private final Logger logger;

  public QuestJdbcRepository(ProgressionDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public Map<String, QuestPlayerQuest> load(UUID playerId) {
    Map<String, QuestPlayerQuest> out = new LinkedHashMap<>();
    if (playerId == null || database.connection() == null) {
      return out;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(LOAD_QUESTS_SQL)) {
      statement.setString(1, playerId.toString());
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          String questId = rs.getString(1);
          String statusRaw = rs.getString(2);
          long startedAt = rs.getLong(3);
          long completedAt = rs.getLong(4);
          long cooldownUntil = rs.getLong(5);
          int dailyCount = rs.getInt(6);
          int weeklyCount = rs.getInt(7);
          long dailyResetAt = rs.getLong(8);
          long weeklyResetAt = rs.getLong(9);
          QuestStatus status;
          if ("COMPLETED".equalsIgnoreCase(statusRaw)) {
            status = QuestStatus.COMPLETED;
          } else if ("FAILED".equalsIgnoreCase(statusRaw)) {
            status = QuestStatus.FAILED;
          } else {
            status = QuestStatus.ACTIVE;
          }
          out.put(questId, new QuestPlayerQuest(questId, status, startedAt, completedAt, cooldownUntil,
              dailyCount, weeklyCount, dailyResetAt, weeklyResetAt, new int[0]));
        }
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Quests] Failed to load quest states", ex);
      return out;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(LOAD_PROGRESS_SQL)) {
      statement.setString(1, playerId.toString());
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          String questId = rs.getString(1);
          int index = rs.getInt(2);
          int progress = rs.getInt(3);
          QuestPlayerQuest quest = out.get(questId);
          if (quest == null) {
            quest = new QuestPlayerQuest(questId, QuestStatus.ACTIVE, 0L, 0L, 0L, 0, 0, 0L, 0L, new int[0]);
            out.put(questId, quest);
          }
          quest.progress(index, progress);
        }
      }
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Quests] Failed to load quest progress", ex);
      return out;
    }
    return out;
  }

  @Override
  public void upsertQuest(UUID playerId, QuestPlayerQuest quest) {
    if (playerId == null || quest == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_QUEST_SQL)) {
      statement.setString(1, playerId.toString());
      statement.setString(2, quest.questId());
      statement.setString(3, quest.status().name());
      statement.setLong(4, quest.startedAt());
      statement.setLong(5, quest.completedAt());
      statement.setLong(6, quest.cooldownUntil());
      statement.setInt(7, quest.dailyCount());
      statement.setInt(8, quest.weeklyCount());
      statement.setLong(9, quest.dailyResetAt());
      statement.setLong(10, quest.weeklyResetAt());
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Quests] Failed to save quest state", ex);
    }
  }

  @Override
  public void setProgress(UUID playerId, String questId, int objectiveIndex, int progress) {
    if (playerId == null || questId == null || database.connection() == null) {
      return;
    }
    try (PreparedStatement statement = database.connection().prepareStatement(UPSERT_PROGRESS_SQL)) {
      statement.setString(1, playerId.toString());
      statement.setString(2, questId);
      statement.setInt(3, objectiveIndex);
      statement.setInt(4, progress);
      statement.executeUpdate();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Quests] Failed to save quest progress", ex);
    }
  }
}
