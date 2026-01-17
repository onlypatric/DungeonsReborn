package dev.patric.dungeonsreborn.progression.custom;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.progression.ProgressionCurve;
import dev.patric.dungeonsreborn.system.SharedTickScheduler;
import dev.patric.dungeonsreborn.advancements.AdvancementService;

public final class CustomXpService {
  private static final long AUTO_SAVE_TICKS = 20L * 60L;

  private final JavaPlugin plugin;
  private final CustomXpRepository repository;
  private final ProgressionCurve curve;
  private final java.util.function.Predicate<World> worldAllowed;
  private final Logger logger;
  private AdvancementService advancementService;
  private final Map<UUID, CustomXpProfile> cache = new ConcurrentHashMap<>();
  private int autosaveTaskId = -1;
  private SharedTickScheduler.Handle autosaveHandle;

  public CustomXpService(JavaPlugin plugin, CustomXpRepository repository, ProgressionCurve curve,
      java.util.function.Predicate<World> worldAllowed, Logger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.curve = Objects.requireNonNull(curve, "curve");
    this.worldAllowed = worldAllowed;
    this.logger = Objects.requireNonNull(logger, "logger");
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
    autosaveHandle = scheduler.schedule("customXpAutosave", AUTO_SAVE_TICKS, this::flushDirty);
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

  public void setAdvancementService(AdvancementService advancementService) {
    this.advancementService = advancementService;
  }

  public CustomXpProfile getOrCreate(UUID uuid) {
    return cache.computeIfAbsent(uuid, id -> repository.load(id).orElseGet(() -> {
      CustomXpProfile created = CustomXpProfile.createDefault(id);
      created.markClean();
      return created;
    }));
  }

  public void load(Player player) {
    if (player == null) {
      return;
    }
    CustomXpProfile profile = getOrCreate(player.getUniqueId());
    recalcLevel(profile);
  }

  public void flush(Player player) {
    if (player == null) {
      return;
    }
    CustomXpProfile profile = cache.get(player.getUniqueId());
    if (profile == null) {
      return;
    }
    flushAll(List.of(profile));
  }

  public void flushAll() {
    flushAll(new ArrayList<>(cache.values()));
  }

  private void flushDirty() {
    ArrayList<CustomXpProfile> dirty = new ArrayList<>();
    for (CustomXpProfile profile : cache.values()) {
      if (profile != null && profile.dirty()) {
        dirty.add(profile);
      }
    }
    if (!dirty.isEmpty()) {
      flushAll(dirty);
    }
  }

  private void flushAll(Collection<CustomXpProfile> profiles) {
    if (profiles == null || profiles.isEmpty()) {
      return;
    }
    int attempts = 0;
    while (attempts < 2) {
      attempts++;
      try {
        repository.saveAll(profiles);
        for (CustomXpProfile profile : profiles) {
          if (profile != null) {
            profile.markClean();
          }
        }
        return;
      } catch (RuntimeException ex) {
        logger.log(Level.WARNING, "[Progression] Custom XP save attempt " + attempts + " failed", ex);
      }
    }
    backupSnapshot(profiles);
  }

  public boolean awardXp(Player player, int amount) {
    if (player == null || amount <= 0) {
      return false;
    }
    if (!isWorldAllowed(player.getWorld())) {
      return false;
    }
    CustomXpProfile profile = getOrCreate(player.getUniqueId());
    int beforeLevel = profile.level();
    int adjusted = curve.applySoftCap((int) Math.min(Integer.MAX_VALUE, profile.points()), amount);
    if (adjusted <= 0) {
      return false;
    }
    long next = Math.min(Long.MAX_VALUE, profile.points() + adjusted);
    profile.points(next);
    recalcLevel(profile);
    if (advancementService != null && advancementService.isEnabled()) {
      int gained = Math.max(0, profile.level() - beforeLevel);
      if (gained > 0) {
        advancementService.recordXpLevelProgress(player, gained);
      }
      advancementService.recordXpTotal(player, (int) Math.min(Integer.MAX_VALUE, profile.points()));
    }
    return true;
  }

  public boolean removeXp(Player player, int amount) {
    if (player == null || amount <= 0) {
      return false;
    }
    CustomXpProfile profile = getOrCreate(player.getUniqueId());
    long next = Math.max(0L, profile.points() - amount);
    if (next == profile.points()) {
      return false;
    }
    profile.points(next);
    recalcLevel(profile);
    return true;
  }

  public boolean isWorldAllowed(World world) {
    return worldAllowed == null || worldAllowed.test(world);
  }

  public double progress(UUID uuid) {
    if (uuid == null) {
      return 0.0;
    }
    CustomXpProfile profile = getOrCreate(uuid);
    return curve.progressForTotal((int) Math.min(Integer.MAX_VALUE, profile.points()));
  }

  public int pointsForProgress(UUID uuid, double fraction) {
    if (uuid == null) {
      return 0;
    }
    CustomXpProfile profile = getOrCreate(uuid);
    return curve.pointsForProgress((int) Math.min(Integer.MAX_VALUE, profile.points()), fraction);
  }

  public int totalForLevel(int level) {
    return curve.totalForLevel(level);
  }

  private void recalcLevel(CustomXpProfile profile) {
    if (profile == null) {
      return;
    }
    int level = curve.levelForTotal((int) Math.min(Integer.MAX_VALUE, profile.points()));
    if (profile.level() != level) {
      profile.level(level);
    }
  }

  private void backupSnapshot(Collection<CustomXpProfile> profiles) {
    try {
      File file = new File(plugin.getDataFolder(), "custom-xp-backup.yml");
      YamlConfiguration cfg = new YamlConfiguration();
      for (CustomXpProfile profile : profiles) {
        if (profile == null) {
          continue;
        }
        String base = "players." + profile.uuid().toString();
        cfg.set(base + ".points", profile.points());
        cfg.set(base + ".level", profile.level());
        cfg.set(base + ".lastUpdate", profile.lastUpdateMillis());
      }
      cfg.save(file);
      logger.warning("[Progression] Saved custom XP backup to " + file.getName());
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "[Progression] Failed to write custom XP backup", ex);
    }
  }

  public void shutdown() {
    stopAutoSave();
    flushAll();
  }
}
