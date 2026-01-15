package dev.patric.dungeonsreborn.progression;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProgressionDatabase implements AutoCloseable {
  private static final int TARGET_SCHEMA_VERSION = 7;

  private final File file;
  private final Logger logger;
  private Connection connection;

  public ProgressionDatabase(File file, Logger logger) {
    this.file = Objects.requireNonNull(file, "file");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void open() throws SQLException {
    if (connection != null) {
      return;
    }
    String url = "jdbc:sqlite:" + file.getAbsolutePath();
    connection = DriverManager.getConnection(url);
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA journal_mode=WAL");
      statement.execute("PRAGMA foreign_keys=ON");
    }
  }

  public Connection connection() {
    return connection;
  }

  public void migrate() throws SQLException {
    ensureSchemaVersionTable();
    int current = currentSchemaVersion();
    if (current > TARGET_SCHEMA_VERSION) {
      logger.warning("[Progression] Database schema newer than expected: " + current);
      return;
    }
    if (current == TARGET_SCHEMA_VERSION) {
      return;
    }
    backup("pre-migration");
    for (int version = current + 1; version <= TARGET_SCHEMA_VERSION; version++) {
      applyMigration(version);
      setSchemaVersion(version);
    }
  }

  private void ensureSchemaVersionTable() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS dr_schema_version (version INTEGER NOT NULL)");
    }
    try (Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery("SELECT version FROM dr_schema_version LIMIT 1")) {
      if (!rs.next()) {
        statement.execute("INSERT INTO dr_schema_version (version) VALUES (0)");
      }
    }
  }

  private int currentSchemaVersion() throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery("SELECT version FROM dr_schema_version LIMIT 1")) {
      if (!rs.next()) {
        return 0;
      }
      return rs.getInt(1);
    }
  }

  private void setSchemaVersion(int version) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("UPDATE dr_schema_version SET version=" + version);
    }
  }

  private void applyMigration(int version) throws SQLException {
    switch (version) {
      case 1 -> migrateV1();
      case 2 -> migrateV2();
      case 3 -> migrateV3();
      case 4 -> migrateV4();
      case 5 -> migrateV5();
      case 6 -> migrateV6();
      case 7 -> migrateV7();
      default -> throw new SQLException("Unknown migration version " + version);
    }
  }

  private void migrateV1() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS player_progression (
            uuid TEXT PRIMARY KEY,
            points INTEGER NOT NULL,
            level INTEGER NOT NULL,
            skill_points INTEGER NOT NULL,
            max_mana INTEGER NOT NULL,
            last_update INTEGER NOT NULL
          )
          """);
    }
  }

  private void migrateV2() throws SQLException {
    addColumn("player_progression", "stat_strength", "INTEGER NOT NULL DEFAULT 0");
    addColumn("player_progression", "stat_dexterity", "INTEGER NOT NULL DEFAULT 0");
    addColumn("player_progression", "stat_intelligence", "INTEGER NOT NULL DEFAULT 0");
    addColumn("player_progression", "stat_vitality", "INTEGER NOT NULL DEFAULT 0");
  }

  private void migrateV3() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS kit_claims (
            uuid TEXT NOT NULL,
            kit_id TEXT NOT NULL,
            last_claim INTEGER NOT NULL,
            PRIMARY KEY (uuid, kit_id)
          )
          """);
    }
  }

  private void migrateV4() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS player_classes (
            uuid TEXT PRIMARY KEY,
            class_id TEXT NOT NULL,
            last_update INTEGER NOT NULL
          )
          """);
    }
  }

  private void migrateV5() throws SQLException {
    addColumn("player_progression", "skill_tree_points", "INTEGER NOT NULL DEFAULT 0");
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS player_class_skills (
            uuid TEXT NOT NULL,
            class_id TEXT NOT NULL,
            node_id TEXT NOT NULL,
            PRIMARY KEY (uuid, class_id, node_id)
          )
          """);
    }
  }

  private void migrateV6() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS player_quests (
            uuid TEXT NOT NULL,
            quest_id TEXT NOT NULL,
            status TEXT NOT NULL,
            started_at INTEGER NOT NULL,
            completed_at INTEGER NOT NULL,
            cooldown_until INTEGER NOT NULL,
            PRIMARY KEY (uuid, quest_id)
          )
          """);
      statement.execute("""
          CREATE TABLE IF NOT EXISTS player_quest_progress (
            uuid TEXT NOT NULL,
            quest_id TEXT NOT NULL,
            objective_index INTEGER NOT NULL,
            progress INTEGER NOT NULL,
            PRIMARY KEY (uuid, quest_id, objective_index)
          )
          """);
    }
  }

  private void migrateV7() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS player_dungeon_progress (
            uuid TEXT NOT NULL,
            dungeon_id TEXT NOT NULL,
            max_level INTEGER NOT NULL,
            last_update INTEGER NOT NULL,
            PRIMARY KEY (uuid, dungeon_id)
          )
          """);
    }
  }

  private void addColumn(String table, String column, String def) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + def);
    } catch (SQLException ex) {
      String msg = ex.getMessage();
      if (msg != null && msg.toLowerCase(java.util.Locale.ROOT).contains("duplicate column")) {
        return;
      }
      throw ex;
    }
  }

  public void backup(String reason) {
    try {
      if (!file.exists()) {
        return;
      }
      File backup = new File(file.getParentFile(),
          file.getName() + "." + reason + "." + Instant.now().toEpochMilli() + ".bak");
      Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception ex) {
      logger.log(Level.WARNING, "[Progression] Failed to create database backup", ex);
    }
  }

  @Override
  public void close() {
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException ex) {
      logger.log(Level.WARNING, "[Progression] Failed to close database", ex);
    } finally {
      connection = null;
    }
  }
}
