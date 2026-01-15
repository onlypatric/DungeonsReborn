package dev.patric.dungeonsreborn.progression;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.system.SharedTickScheduler;

public final class ProgressionService {
  private static final long AUTO_SAVE_TICKS = 20L * 60L;

  private final JavaPlugin plugin;
  private final ProgressionRepository repository;
  private final ProgressionCurve curve;
  private final Predicate<World> worldAllowed;
  private final Logger logger;
  private final int skillPointsPerXp;
  private final Map<UUID, PlayerProgression> cache = new ConcurrentHashMap<>();
  private int autosaveTaskId = -1;
  private SharedTickScheduler.Handle autosaveHandle;

  public ProgressionService(JavaPlugin plugin, ProgressionRepository repository, ProgressionCurve curve,
      Predicate<World> worldAllowed, int skillPointsPerXp, Logger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.curve = Objects.requireNonNull(curve, "curve");
    this.worldAllowed = worldAllowed;
    this.logger = Objects.requireNonNull(logger, "logger");
    this.skillPointsPerXp = Math.max(0, skillPointsPerXp);
  }

  public void startAutoSave() {
    if (autosaveHandle != null) {
      return;
    }
    if (autosaveTaskId != -1) {
      return;
    }
    autosaveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::flushDirty, AUTO_SAVE_TICKS,
        AUTO_SAVE_TICKS);
  }

  public void startAutoSave(SharedTickScheduler scheduler) {
    if (scheduler == null) {
      startAutoSave();
      return;
    }
    if (autosaveHandle != null || autosaveTaskId != -1) {
      return;
    }
    autosaveHandle = scheduler.schedule("progressionAutosave", AUTO_SAVE_TICKS, this::flushDirty);
  }

  public void stopAutoSave() {
    if (autosaveHandle != null) {
      autosaveHandle.cancel();
      autosaveHandle = null;
    }
    if (autosaveTaskId == -1) {
      return;
    }
    Bukkit.getScheduler().cancelTask(autosaveTaskId);
    autosaveTaskId = -1;
  }

  public PlayerProgression getOrCreate(UUID uuid) {
    return cache.computeIfAbsent(uuid, id -> repository.load(id).orElseGet(() -> {
      PlayerProgression created = PlayerProgression.createDefault(id);
      created.markClean();
      return created;
    }));
  }

  public void load(Player player) {
    if (player == null) {
      return;
    }
    PlayerProgression progression = getOrCreate(player.getUniqueId());
    syncFromPlayer(player, progression);
  }

  public void flush(Player player) {
    if (player == null) {
      return;
    }
    PlayerProgression progression = cache.get(player.getUniqueId());
    if (progression == null) {
      return;
    }
    flushAll(List.of(progression));
  }

  public void flushAll() {
    flushAll(new ArrayList<>(cache.values()));
  }

  private void flushDirty() {
    syncFromOnlinePlayers(cache.values());
    ArrayList<PlayerProgression> dirty = new ArrayList<>();
    for (PlayerProgression progression : cache.values()) {
      if (progression != null && progression.dirty()) {
        dirty.add(progression);
      }
    }
    if (!dirty.isEmpty()) {
      flushAll(dirty);
    }
  }

  private void flushAll(Collection<PlayerProgression> progressions) {
    if (progressions == null || progressions.isEmpty()) {
      return;
    }
    syncFromOnlinePlayers(progressions);
    int attempts = 0;
    while (attempts < 2) {
      attempts++;
      try {
        repository.saveAll(progressions);
        for (PlayerProgression progression : progressions) {
          if (progression != null) {
            progression.markClean();
          }
        }
        return;
      } catch (RuntimeException ex) {
        logger.log(Level.WARNING, "[Progression] Save attempt " + attempts + " failed", ex);
      }
    }
    backupSnapshot(progressions);
  }

  private void syncFromOnlinePlayers(Collection<PlayerProgression> progressions) {
    for (PlayerProgression progression : progressions) {
      if (progression == null) {
        continue;
      }
      Player player = Bukkit.getPlayer(progression.uuid());
      if (player != null) {
        syncFromPlayer(player, progression);
      }
    }
  }

  public boolean awardXp(Player player, int amount, ProgressionAwardSource source, String detail) {
    if (player == null || amount <= 0) {
      return false;
    }
    if (!isWorldAllowed(player.getWorld())) {
      return false;
    }
    int total = player.getTotalExperience();
    int adjusted = curve.applySoftCap(total, amount);
    if (adjusted <= 0) {
      return false;
    }
    player.giveExp(adjusted);
    PlayerProgression progression = getOrCreate(player.getUniqueId());
    syncFromPlayer(player, progression);
    return true;
  }

  public boolean awardForItemUse(Player player, int amount, String itemId) {
    return awardXp(player, amount, ProgressionAwardSource.ITEM_USE, itemId);
  }

  public boolean awardForEffect(Player player, int amount, String abilityId) {
    return awardXp(player, amount, ProgressionAwardSource.EFFECT_CAST, abilityId);
  }

  public boolean awardForQuest(Player player, int amount, String questId) {
    return awardXp(player, amount, ProgressionAwardSource.QUEST, questId);
  }

  public boolean awardSkillPoints(Player player, int points) {
    if (player == null || points <= 0) {
      return false;
    }
    if (!isWorldAllowed(player.getWorld())) {
      return false;
    }
    PlayerProgression progression = getOrCreate(player.getUniqueId());
    if (skillPointsPerXp > 0) {
      int xp = points * skillPointsPerXp;
      if (xp <= 0) {
        return false;
      }
      player.giveExp(xp);
      syncFromPlayer(player, progression);
      return true;
    }
    progression.skillPoints(progression.skillPoints() + points);
    return true;
  }

  public boolean isWorldAllowed(World world) {
    return worldAllowed == null || worldAllowed.test(world);
  }

  public void syncFromPlayer(Player player) {
    if (player == null) {
      return;
    }
    PlayerProgression progression = getOrCreate(player.getUniqueId());
    syncFromPlayer(player, progression);
  }

  private void syncFromPlayer(Player player, PlayerProgression progression) {
    if (player == null || progression == null) {
      return;
    }
    int totalXp = player.getTotalExperience();
    int level = curve.levelFor(player, totalXp);
    if (progression.points() != totalXp) {
      progression.points(totalXp);
    }
    if (progression.level() != level) {
      progression.level(level);
    }
    if (skillPointsPerXp > 0) {
      int earned = totalXp / skillPointsPerXp;
      int allocated = progression.allocatedSkillPoints();
      int spentTree = progression.skillTreePoints();
      int unspent = Math.max(0, earned - allocated - spentTree);
      if (progression.skillPoints() != unspent) {
        progression.skillPoints(unspent);
      }
    }
  }

  private void backupSnapshot(Collection<PlayerProgression> progressions) {
    try {
      File file = new File(plugin.getDataFolder(), "progression-backup.yml");
      YamlConfiguration cfg = new YamlConfiguration();
      for (PlayerProgression progression : progressions) {
        if (progression == null) {
          continue;
        }
        String base = "players." + progression.uuid().toString();
        cfg.set(base + ".points", progression.points());
        cfg.set(base + ".level", progression.level());
        cfg.set(base + ".skillPoints", progression.skillPoints());
        cfg.set(base + ".maxMana", progression.maxMana());
        cfg.set(base + ".strength", progression.strength());
        cfg.set(base + ".dexterity", progression.dexterity());
        cfg.set(base + ".intelligence", progression.intelligence());
        cfg.set(base + ".vitality", progression.vitality());
        cfg.set(base + ".skillTreePoints", progression.skillTreePoints());
        cfg.set(base + ".lastUpdate", progression.lastUpdateMillis());
      }
      cfg.save(file);
      logger.warning("[Progression] Saved fallback backup to " + file.getName());
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "[Progression] Failed to write fallback backup", ex);
    }
  }

  public void shutdown() {
    stopAutoSave();
    flushAll();
  }
}
