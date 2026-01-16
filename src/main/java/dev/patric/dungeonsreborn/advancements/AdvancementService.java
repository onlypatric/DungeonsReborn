package dev.patric.dungeonsreborn.advancements;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.events.PlayerLoadingCompletedEvent;

import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobAdvancementRewardSpec;
import dev.patric.dungeonsreborn.mobs.MobSpec;
import dev.patric.dungeonsreborn.dungeons.DungeonSpec;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.progression.ProgressionAwardSource;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopTokenTierSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

public final class AdvancementService {
  private static final String TAB_NAMESPACE = "dungeonsreborn";
  private static final int BOSS_FIRST_KILL_TOKENS = 50;
  private static final int BOSS_THRESHOLD_TOKENS = 20;
  private static final int DUNGEON_LEVEL_TOKENS = 30;
  private static final int DUNGEON_THRESHOLD_TOKENS = 15;

  private final Plugin plugin;
  private final Map<String, BaseAdvancement> bossFirstKill = new HashMap<>();
  private final Map<String, List<BossThreshold>> bossKillThresholds = new HashMap<>();
  private final Map<String, BaseAdvancement> dungeonLevelAdvancements = new HashMap<>();
  private final Map<String, List<DungeonThreshold>> dungeonCompletionThresholds = new HashMap<>();
  private final Map<String, BaseAdvancement> dungeonNoDeath = new HashMap<>();
  private final Map<String, BaseAdvancement> dungeonTime = new HashMap<>();
  private final Map<String, Map<Integer, BaseAdvancement>> dungeonStreakAdvancements = new HashMap<>();
  private final Map<String, Map<UUID, Integer>> dungeonStreakCounts = new HashMap<>();
  private final Map<String, BaseAdvancement> mobKillAdvancements = new HashMap<>();
  private final Map<Integer, BaseAdvancement> xpLevelAdvancements = new HashMap<>();
  private final Map<Integer, BaseAdvancement> xpTotalAdvancements = new HashMap<>();
  private final Map<Integer, BaseAdvancement> tokenMilestones = new HashMap<>();
  private final Map<Integer, BaseAdvancement> tokenPalletMilestones = new HashMap<>();
  private final List<BaseAdvancement> pendingAdvancements = new ArrayList<>();
  private AdvancementTab tab;
  private RootAdvancement root;
  private boolean enabled;
  private UltimateAdvancementAPI api;
  private ShopYamlRegistry shopRegistry;
  private ProgressionService progressionService;
  private AdvancementConfig config = AdvancementConfig.defaults();

  public AdvancementService(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public boolean enable() {
    if (enabled) {
      return true;
    }
    try {
      if (Bukkit.getPluginManager().getPlugin("UltimateAdvancementAPI") == null) {
        plugin.getLogger().warning("[Advancements] UltimateAdvancementAPI not installed, skipping.");
        return false;
      }
      api = UltimateAdvancementAPI.getInstance(plugin);
      if (api == null) {
        plugin.getLogger().warning("[Advancements] UltimateAdvancementAPI instance unavailable, skipping.");
        return false;
      }
      createTab();
      reloadConfig();
      enabled = true;
      plugin.getLogger().info("[Advancements] Initialized");
      return true;
    } catch (Throwable t) {
      plugin.getLogger().log(java.util.logging.Level.WARNING,
          "[Advancements] Failed to initialize, continuing without advancements", t);
      return false;
    }
  }

  public void disable() {
    enabled = false;
    api = null;
    tab = null;
    root = null;
    bossFirstKill.clear();
    bossKillThresholds.clear();
    dungeonLevelAdvancements.clear();
    dungeonCompletionThresholds.clear();
    dungeonNoDeath.clear();
    dungeonTime.clear();
    dungeonStreakAdvancements.clear();
    dungeonStreakCounts.clear();
    dungeonStreakAdvancements.clear();
    dungeonStreakCounts.clear();
    mobKillAdvancements.clear();
    xpLevelAdvancements.clear();
    xpTotalAdvancements.clear();
    tokenMilestones.clear();
    tokenPalletMilestones.clear();
    pendingAdvancements.clear();
    shopRegistry = null;
    progressionService = null;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setShopRegistry(ShopYamlRegistry shopRegistry) {
    this.shopRegistry = shopRegistry;
  }

  public void setProgressionService(ProgressionService progressionService) {
    this.progressionService = progressionService;
  }

  public void reloadConfig() {
    File file = configFile();
    if (!file.exists()) {
      plugin.saveResource("advancements.yml", false);
    }
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    config = AdvancementConfig.from(cfg);
  }

  public void reloadAll(MobRegistry mobs, DungeonYamlRegistry dungeons) {
    rebuildAll(mobs, dungeons);
  }

  public void rebuildAll(MobRegistry mobs, DungeonYamlRegistry dungeons) {
    if (!enabled || api == null) {
      return;
    }
    reloadConfig();
    resetTab();
    createTab();
    registerXpAdvancements();
    registerXpTotalAdvancements();
    registerTokenAdvancements();
    registerTokenPalletAdvancements();
    registerMobKillAdvancements(mobs);
    registerBossAdvancements(mobs);
    registerDungeonAdvancements(dungeons);
    finalizeRegistrations();
  }

  public void grantRoot(Player player) {
    if (!enabled || tab == null || root == null || player == null) {
      return;
    }
    tab.grantRootAdvancement(player);
  }

  public void registerBossAdvancements(MobRegistry mobRegistry) {
    if (!enabled || tab == null || root == null || mobRegistry == null || !config.bossesEnabled) {
      return;
    }
    bossFirstKill.clear();
    bossKillThresholds.clear();
    int x = 2;
    int y = 0;
    for (String mobId : mobRegistry.ids()) {
      MobSpec spec = mobRegistry.get(mobId);
      if (spec == null) {
        continue;
      }
      if (spec.bossBar() == null && spec.bossBroadcast() == null) {
        continue;
      }
      String title = bossTitle(spec, mobId);
      String description = "Defeat " + title + ".";
      Material icon = bossIcon(spec);
      AdvancementDisplay display = new AdvancementDisplay(
          icon,
          title,
          AdvancementFrameType.CHALLENGE,
          true,
          true,
          x,
          y,
          description
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("boss_first_kill_" + mobId),
          display,
          root,
          1
      );
      pendingAdvancements.add(advancement);
      bossFirstKill.put(mobId, advancement);
      int row = y + 1;
      List<Integer> thresholds = config.bossThresholds;
      List<BossThreshold> thresholdList = new ArrayList<>();
      for (int threshold : thresholds) {
        if (threshold <= 0) {
          continue;
        }
        AdvancementDisplay thresholdDisplay = new AdvancementDisplay(
            icon,
            title + " " + threshold + "x",
            AdvancementFrameType.TASK,
            true,
            true,
            x,
            row,
            "Defeat " + title + " " + threshold + " times."
        );
        BaseAdvancement thresholdAdvancement = new BaseAdvancement(
            AdvancementIds.key("boss_kills_" + mobId + "_" + threshold),
            thresholdDisplay,
            advancement,
            threshold
        );
        pendingAdvancements.add(thresholdAdvancement);
        thresholdList.add(new BossThreshold(thresholdAdvancement, config.bossThresholdTokens, threshold));
        row++;
      }
      bossKillThresholds.put(mobId, thresholdList);
      x++;
      if (x > 7) {
        x = 2;
        y += 3;
      }
    }
  }

  public void registerMobKillAdvancements(MobRegistry mobRegistry) {
    if (!enabled || tab == null || root == null || mobRegistry == null || !config.mobKillsEnabled) {
      return;
    }
    mobKillAdvancements.clear();
    int x = 0;
    int y = 3;
    for (String mobId : mobRegistry.ids()) {
      MobSpec spec = mobRegistry.get(mobId);
      if (spec == null) {
        continue;
      }
      String title = mobTitle(spec, mobId);
      AdvancementDisplay display = new AdvancementDisplay(
          mobIcon(spec),
          title,
          AdvancementFrameType.TASK,
          true,
          true,
          x,
          y,
          "Defeat " + title + "."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("mob_kill_" + mobId),
          display,
          root,
          1
      );
      pendingAdvancements.add(advancement);
      mobKillAdvancements.put(mobId, advancement);
      x++;
      if (x > 1) {
        x = 0;
        y++;
      }
    }
  }

  public void registerXpAdvancements() {
    if (!enabled || tab == null || root == null || !config.xpLevelsEnabled) {
      return;
    }
    xpLevelAdvancements.clear();
    List<Integer> levels = config.xpLevels;
    int x = 6;
    int y = 0;
    for (int level : levels) {
      if (level <= 0) {
        continue;
      }
      AdvancementFrameType frameType = level >= 50000 ? AdvancementFrameType.CHALLENGE : AdvancementFrameType.TASK;
      AdvancementDisplay display = new AdvancementDisplay(
          Material.EXPERIENCE_BOTTLE,
          "Level " + level,
          frameType,
          true,
          true,
          x,
          y,
          "Reach XP level " + level + "."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("xp_level_" + level),
          display,
          root,
          level
      );
      pendingAdvancements.add(advancement);
      xpLevelAdvancements.put(level, advancement);
      y++;
    }
  }

  public void registerXpTotalAdvancements() {
    if (!enabled || tab == null || root == null || !config.xpTotalsEnabled) {
      return;
    }
    xpTotalAdvancements.clear();
    List<Integer> totals = config.xpTotals;
    int x = 5;
    int y = 0;
    for (int total : totals) {
      if (total <= 0) {
        continue;
      }
      AdvancementFrameType frameType = total >= 50000 ? AdvancementFrameType.CHALLENGE : AdvancementFrameType.TASK;
      AdvancementDisplay display = new AdvancementDisplay(
          Material.EXPERIENCE_BOTTLE,
          "Total XP " + total,
          frameType,
          true,
          true,
          x,
          y,
          "Earn " + total + " total XP."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("xp_total_" + total),
          display,
          root,
          1
      );
      pendingAdvancements.add(advancement);
      xpTotalAdvancements.put(total, advancement);
      y++;
    }
  }

  public void registerTokenAdvancements() {
    if (!enabled || tab == null || root == null || !config.tokensEnabled) {
      return;
    }
    tokenMilestones.clear();
    List<Integer> thresholds = config.tokenMilestones;
    int x = 7;
    int y = 0;
    for (int threshold : thresholds) {
      if (threshold <= 0) {
        continue;
      }
      AdvancementDisplay display = new AdvancementDisplay(
          Material.SUNFLOWER,
          threshold + " Tokens",
          AdvancementFrameType.TASK,
          true,
          true,
          x,
          y,
          "Gather " + threshold + " tokens."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("tokens_earned_" + threshold),
          display,
          root,
          threshold
      );
      pendingAdvancements.add(advancement);
      tokenMilestones.put(threshold, advancement);
      y++;
    }
  }

  public void registerTokenPalletAdvancements() {
    if (!enabled || tab == null || root == null || !config.tokensEnabled) {
      return;
    }
    tokenPalletMilestones.clear();
    List<Integer> pallets = config.tokenPalletMilestones;
    if (pallets.isEmpty()) {
      return;
    }
    int maxPallet = pallets.stream().mapToInt(Integer::intValue).max().orElse(0);
    int x = 8;
    int y = 0;
    for (int pallet : pallets) {
      if (pallet <= 0) {
        continue;
      }
      int tokenCount = pallet * 4096;
      AdvancementFrameType frameType = pallet == maxPallet ? AdvancementFrameType.CHALLENGE : AdvancementFrameType.TASK;
      AdvancementDisplay display = new AdvancementDisplay(
          Material.CHEST,
          "Token Pallets " + pallet,
          frameType,
          true,
          true,
          x,
          y,
          "Earn " + pallet + " token pallets (" + tokenCount + " tokens)."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("tokens_pallets_" + pallet),
          display,
          root,
          tokenCount
      );
      pendingAdvancements.add(advancement);
      tokenPalletMilestones.put(tokenCount, advancement);
      y++;
    }
  }

  public void registerDungeonAdvancements(DungeonYamlRegistry registry) {
    if (!enabled || tab == null || root == null || registry == null || !config.dungeonsEnabled) {
      return;
    }
    dungeonLevelAdvancements.clear();
    dungeonCompletionThresholds.clear();
    dungeonNoDeath.clear();
    dungeonTime.clear();
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null || dungeon.levels().isEmpty()) {
      return;
    }
    String dungeonId = dungeon.id();
    String title = dungeonTitle(dungeon, dungeonId);
    int x = 0;
    int y = 3;
    for (DungeonSpec.DungeonLevel level : dungeon.levels().values()) {
      AdvancementDisplay display = new AdvancementDisplay(
          Material.END_PORTAL_FRAME,
          title + " Lv " + level.level(),
          AdvancementFrameType.CHALLENGE,
          true,
          true,
          x,
          y,
          "Complete level " + level.level() + " of " + title + "."
      );
      String key = "dungeon_level_" + dungeonId + "_" + level.level();
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key(key),
          display,
          root,
          1
      );
      pendingAdvancements.add(advancement);
      dungeonLevelAdvancements.put(dungeonLevelKey(dungeonId, level.level()), advancement);
      y++;
    }
    int specialRow = y + 1;
    for (DungeonSpec.DungeonLevel level : dungeon.levels().values()) {
      AdvancementDisplay noDeathDisplay = new AdvancementDisplay(
          Material.TOTEM_OF_UNDYING,
          title + " Lv " + level.level() + " (No Deaths)",
          AdvancementFrameType.CHALLENGE,
          true,
          true,
          x + 1,
          specialRow,
          "Complete level " + level.level() + " without dying."
      );
      BaseAdvancement noDeathAdv = new BaseAdvancement(
          AdvancementIds.key("dungeon_no_death_" + dungeonId + "_" + level.level()),
          noDeathDisplay,
          root,
          1
      );
      pendingAdvancements.add(noDeathAdv);
      dungeonNoDeath.put(dungeonLevelKey(dungeonId, level.level()), noDeathAdv);
      int timeLimit = Math.max(0, level.timeLimitSeconds());
      if (timeLimit > 0) {
        AdvancementDisplay timeDisplay = new AdvancementDisplay(
            Material.CLOCK,
            title + " Lv " + level.level() + " (Speedrun)",
            AdvancementFrameType.TASK,
            true,
            true,
            x + 2,
            specialRow,
            "Complete within " + timeLimit + " seconds."
        );
        BaseAdvancement timeAdv = new BaseAdvancement(
            AdvancementIds.key("dungeon_time_" + dungeonId + "_" + level.level()),
            timeDisplay,
            root,
            1
        );
        pendingAdvancements.add(timeAdv);
        dungeonTime.put(dungeonLevelKey(dungeonId, level.level()), timeAdv);
      }
      specialRow++;
    }
    List<DungeonThreshold> thresholds = new ArrayList<>();
    List<Integer> counts = config.dungeonThresholds;
    int row = y + 1;
    for (int count : counts) {
      if (count <= 0) {
        continue;
      }
      AdvancementDisplay display = new AdvancementDisplay(
          Material.NETHER_STAR,
          title + " " + count + "x",
          AdvancementFrameType.TASK,
          true,
          true,
          x,
          row,
          "Complete " + title + " " + count + " times."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("dungeon_completions_" + dungeonId + "_" + count),
          display,
          root,
          count
      );
      pendingAdvancements.add(advancement);
      thresholds.add(new DungeonThreshold(advancement, config.dungeonThresholdTokens));
      row++;
    }
    dungeonCompletionThresholds.put(dungeonId, thresholds);

    List<Integer> streaks = config.dungeonStreaks;
    Map<Integer, BaseAdvancement> streakMap = new HashMap<>();
    int streakX = x + 1;
    int streakY = 3;
    for (int streak : streaks) {
      if (streak <= 0) {
        continue;
      }
      AdvancementDisplay streakDisplay = new AdvancementDisplay(
          Material.CLOCK,
          title + " Streak " + streak,
          AdvancementFrameType.TASK,
          true,
          true,
          streakX,
          streakY,
          "Complete " + streak + " dungeon runs in a row."
      );
      BaseAdvancement streakAdvancement = new BaseAdvancement(
          AdvancementIds.key("dungeon_streak_" + dungeonId + "_" + streak),
          streakDisplay,
          root,
          1
      );
      pendingAdvancements.add(streakAdvancement);
      streakMap.put(streak, streakAdvancement);
      streakY++;
    }
    if (!streakMap.isEmpty()) {
      dungeonStreakAdvancements.put(dungeonId, streakMap);
    }
  }

  public void recordBossKill(MobSpec spec, String mobId, Player killer) {
    if (!enabled || !config.bossesEnabled || mobId == null || killer == null || spec == null) {
      return;
    }
    String title = bossTitle(spec, mobId);
    BaseAdvancement advancement = bossFirstKill.get(mobId);
    if (advancement != null) {
      boolean wasGranted = advancement.isGranted(killer);
      advancement.incrementProgression(killer);
      if (!wasGranted && advancement.isGranted(killer)) {
        grantBossRewards(spec, killer, config.bossFirstKillTokens);
        announceAdvancement(killer, title, null);
      }
    }
    List<BossThreshold> thresholds = bossKillThresholds.get(mobId);
    if (thresholds != null) {
      for (BossThreshold threshold : thresholds) {
        boolean wasGranted = threshold.advancement.isGranted(killer);
        threshold.advancement.incrementProgression(killer);
        if (!wasGranted && threshold.advancement.isGranted(killer)) {
          grantBossRewards(spec, killer, threshold.tokenReward);
          announceAdvancement(killer, title, threshold.count);
        }
      }
    }
  }

  public void recordDungeonCompletion(DungeonSpec dungeon, int level, Player player, boolean hadDeath,
      long durationMillis, int timeLimitSeconds) {
    if (!enabled || !config.dungeonsEnabled || dungeon == null || player == null) {
      return;
    }
    String dungeonId = dungeon.id();
    BaseAdvancement levelAdvancement = dungeonLevelAdvancements.get(dungeonLevelKey(dungeonId, level));
    if (levelAdvancement != null) {
      boolean wasGranted = levelAdvancement.isGranted(player);
      levelAdvancement.incrementProgression(player);
      if (!wasGranted && levelAdvancement.isGranted(player)) {
        DungeonSpec.DungeonLevel levelSpec = dungeon.levels().get(level);
        if (levelSpec != null) {
          grantDungeonRewards(levelSpec, player, config.dungeonLevelTokens);
        }
      }
    }
    List<DungeonThreshold> thresholds = dungeonCompletionThresholds.get(dungeonId);
    if (thresholds != null) {
      for (DungeonThreshold threshold : thresholds) {
        boolean wasGranted = threshold.advancement.isGranted(player);
        threshold.advancement.incrementProgression(player);
        if (!wasGranted && threshold.advancement.isGranted(player)) {
          giveTokenBundle(player, threshold.tokenReward);
        }
      }
    }
    if (!hadDeath) {
      BaseAdvancement noDeathAdv = dungeonNoDeath.get(dungeonLevelKey(dungeonId, level));
      if (noDeathAdv != null && !noDeathAdv.isGranted(player)) {
        noDeathAdv.incrementProgression(player);
      }
    }
    if (timeLimitSeconds > 0 && durationMillis >= 0 && durationMillis <= timeLimitSeconds * 1000L) {
      BaseAdvancement timeAdv = dungeonTime.get(dungeonLevelKey(dungeonId, level));
      if (timeAdv != null && !timeAdv.isGranted(player)) {
        timeAdv.incrementProgression(player);
      }
    }
    if (!dungeonStreakAdvancements.isEmpty()) {
      Map<UUID, Integer> streaks = dungeonStreakCounts.computeIfAbsent(dungeonId, id -> new HashMap<>());
      int current = streaks.getOrDefault(player.getUniqueId(), 0) + 1;
      streaks.put(player.getUniqueId(), current);
      Map<Integer, BaseAdvancement> streakMap = dungeonStreakAdvancements.get(dungeonId);
      if (streakMap != null) {
        for (Map.Entry<Integer, BaseAdvancement> entry : streakMap.entrySet()) {
          if (current < entry.getKey()) {
            continue;
          }
          BaseAdvancement adv = entry.getValue();
          if (adv != null && !adv.isGranted(player)) {
            adv.incrementProgression(player);
          }
        }
      }
    }
  }

  public void recordDungeonFailure(DungeonSpec dungeon, Player player) {
    if (!enabled || !config.dungeonsEnabled || dungeon == null || player == null) {
      return;
    }
    Map<UUID, Integer> streaks = dungeonStreakCounts.get(dungeon.id());
    if (streaks != null) {
      streaks.remove(player.getUniqueId());
    }
  }

  public void recordXpLevelProgress(Player player, int levelsGained) {
    if (!enabled || !config.xpLevelsEnabled || player == null || levelsGained <= 0) {
      return;
    }
    for (Map.Entry<Integer, BaseAdvancement> entry : xpLevelAdvancements.entrySet()) {
      BaseAdvancement advancement = entry.getValue();
      if (advancement == null || advancement.isGranted(player)) {
        continue;
      }
      for (int i = 0; i < levelsGained && !advancement.isGranted(player); i++) {
        advancement.incrementProgression(player);
      }
    }
  }

  public void recordXpTotal(Player player, int totalXp) {
    if (!enabled || !config.xpTotalsEnabled || player == null || totalXp <= 0) {
      return;
    }
    for (Map.Entry<Integer, BaseAdvancement> entry : xpTotalAdvancements.entrySet()) {
      int threshold = entry.getKey();
      if (totalXp < threshold) {
        continue;
      }
      BaseAdvancement advancement = entry.getValue();
      if (advancement == null || advancement.isGranted(player)) {
        continue;
      }
      advancement.incrementProgression(player);
    }
  }

  public void recordTokensEarned(Player player, int amount) {
    if (!enabled || !config.tokensEnabled || player == null || amount <= 0) {
      return;
    }
    incrementTokenAdvancements(player, tokenMilestones, amount);
    incrementTokenAdvancements(player, tokenPalletMilestones, amount);
  }

  public void recordMobKill(Player player, MobSpec spec) {
    if (!enabled || !config.mobKillsEnabled || player == null || spec == null) {
      return;
    }
    BaseAdvancement advancement = mobKillAdvancements.get(spec.id());
    if (advancement == null || advancement.isGranted(player)) {
      return;
    }
    advancement.incrementProgression(player);
  }

  public void recordTokensFromItem(Player player, ItemStack item) {
    if (!enabled || !config.tokensEnabled || player == null || item == null) {
      return;
    }
    int tokens = tokenValue(item);
    if (tokens > 0) {
      recordTokensEarned(player, tokens);
    }
  }

  private void incrementTokenAdvancements(Player player, Map<Integer, BaseAdvancement> milestones, int amount) {
    for (Map.Entry<Integer, BaseAdvancement> entry : milestones.entrySet()) {
      BaseAdvancement advancement = entry.getValue();
      if (advancement == null || advancement.isGranted(player)) {
        continue;
      }
      int remaining = amount;
      while (remaining > 0 && !advancement.isGranted(player)) {
        advancement.incrementProgression(player);
        remaining--;
      }
    }
  }

  private String bossTitle(MobSpec spec, String fallback) {
    if (spec.displayName() != null) {
      return PlainTextComponentSerializer.plainText().serialize(spec.displayName());
    }
    return fallback;
  }

  private String mobTitle(MobSpec spec, String fallback) {
    if (spec.displayName() != null) {
      return PlainTextComponentSerializer.plainText().serialize(spec.displayName());
    }
    return fallback;
  }

  private String dungeonTitle(DungeonSpec dungeon, String fallback) {
    if (dungeon.name() != null) {
      return PlainTextComponentSerializer.plainText().serialize(dungeon.name());
    }
    return fallback;
  }

  private String dungeonLevelKey(String dungeonId, int level) {
    return dungeonId + ":" + level;
  }

  private Material bossIcon(MobSpec spec) {
    if (spec.mainHand() != null && spec.mainHand().getType() != Material.AIR) {
      return spec.mainHand().getType();
    }
    return Material.NETHER_STAR;
  }

  private Material mobIcon(MobSpec spec) {
    if (spec.mainHand() != null && spec.mainHand().getType() != Material.AIR) {
      return spec.mainHand().getType();
    }
    if (spec.entityType() != null) {
      try {
        return Material.valueOf(spec.entityType().name() + "_SPAWN_EGG");
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    return Material.SPAWNER;
  }

  private void grantBossRewards(MobSpec spec, Player player, int tokenReward) {
    if (player == null || spec == null) {
      return;
    }
    if (tokenReward > 0) {
      giveTokenBundle(player, tokenReward);
    }
    MobAdvancementRewardSpec rewards = spec.advancementRewards();
    if (rewards == null) {
      return;
    }
    if (progressionService != null) {
      if (rewards.xp() > 0) {
        progressionService.awardXp(player, rewards.xp(), ProgressionAwardSource.ADVANCEMENT, spec.id());
      }
      if (rewards.skillPoints() > 0) {
        progressionService.awardSkillPoints(player, rewards.skillPoints());
      }
    }
    for (ItemStack item : rewards.items()) {
      giveItemOrDrop(player, item);
    }
  }

  private void grantDungeonRewards(DungeonSpec.DungeonLevel levelSpec, Player player, int tokenReward) {
    if (player == null || levelSpec == null) {
      return;
    }
    if (tokenReward > 0) {
      giveTokenBundle(player, tokenReward);
    }
    DungeonSpec.DungeonReward rewards = levelSpec.rewards();
    if (rewards == null) {
      return;
    }
    if (progressionService != null && rewards.skillPoints() > 0) {
      progressionService.awardSkillPoints(player, rewards.skillPoints());
    }
    for (DungeonSpec.DungeonExtraLoot extra : rewards.extraLoot()) {
      if (extra == null) {
        continue;
      }
      int chance = Math.max(0, extra.chancePercent());
      if (chance <= 0) {
        continue;
      }
      if (ThreadLocalRandom.current().nextInt(100) >= chance) {
        continue;
      }
      ItemStack item = resolveExtraLoot(extra);
      giveItemOrDrop(player, item);
    }
  }

  private ItemStack resolveExtraLoot(DungeonSpec.DungeonExtraLoot extra) {
    if (extra == null || extra.itemId() == null || extra.itemId().isBlank()) {
      return null;
    }
    if (shopRegistry == null || shopRegistry.itemResolver() == null) {
      return null;
    }
    ItemStack resolved = shopRegistry.itemResolver().apply(extra.itemId());
    return resolved == null ? null : resolved.clone();
  }

  private void giveTokenBundle(Player player, int amount) {
    if (shopRegistry == null || player == null || amount <= 0) {
      return;
    }
    int remaining = amount;
    ItemStack palletItem = shopRegistry.resolveTokenItem("pallet");
    ItemStack compressedItem = shopRegistry.resolveTokenItem("compressed");
    ItemStack normalItem = shopRegistry.resolveTokenItem("token");
    if (palletItem != null && !palletItem.getType().isAir()) {
      int pallets = remaining / 4096;
      remaining %= 4096;
      giveTokenStacks(player, palletItem, pallets);
    }
    if (compressedItem != null && !compressedItem.getType().isAir()) {
      int compressed = remaining / 64;
      remaining %= 64;
      giveTokenStacks(player, compressedItem, compressed);
    }
    if (normalItem != null && !normalItem.getType().isAir()) {
      giveTokenStacks(player, normalItem, remaining);
    }
  }

  private void giveTokenStacks(Player player, ItemStack template, int amount) {
    if (player == null || template == null || amount <= 0) {
      return;
    }
    int remaining = amount;
    int maxStack = Math.max(1, template.getMaxStackSize());
    while (remaining > 0) {
      int stackAmount = Math.min(maxStack, remaining);
      ItemStack stack = template.clone();
      stack.setAmount(stackAmount);
      giveItemOrDrop(player, stack);
      remaining -= stackAmount;
    }
  }

  private void giveItemOrDrop(Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir()) {
      return;
    }
    var leftovers = player.getInventory().addItem(item.clone());
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItem(player.getLocation(), stack);
      }
    }
  }

  private void announceAdvancement(Player player, String title, Integer count) {
    if (player == null || title == null || title.isBlank()) {
      return;
    }
    if (count == null || count <= 0) {
      player.sendMessage(dev.patric.dungeonsreborn.locale.Locales.component(player, "messages.advancements.unlocked",
          dev.patric.dungeonsreborn.locale.Locales.placeholders("title", title)));
    } else {
      player.sendMessage(dev.patric.dungeonsreborn.locale.Locales.component(player, "messages.advancements.bossKills",
          dev.patric.dungeonsreborn.locale.Locales.placeholders("title", title, "count", String.valueOf(count))));
    }
  }

  private int tokenValue(ItemStack item) {
    if (shopRegistry == null || item == null || item.getType().isAir()) {
      return 0;
    }
    int amount = Math.max(0, item.getAmount());
    if (amount <= 0) {
      return 0;
    }
    ShopTokenSpec token = shopRegistry.tokenSpec();
    if (token != null && token.markerKey() != null && ItemMarkers.has(item, token.markerKey())) {
      return amount;
    }
    for (ShopTokenTierSpec tier : shopRegistry.tokenTiers().values()) {
      if (tier == null || tier.markerKey() == null) {
        continue;
      }
      if (!ItemMarkers.has(item, tier.markerKey())) {
        continue;
      }
      String id = tier.id().toLowerCase(Locale.ROOT);
      int multiplier = switch (id) {
        case "compressed" -> 64;
        case "pallet" -> 4096;
        default -> 1;
      };
      return amount * multiplier;
    }
    return 0;
  }

  private record BossThreshold(BaseAdvancement advancement, int tokenReward, int count) {
  }

  private record DungeonThreshold(BaseAdvancement advancement, int tokenReward) {
  }

  private File configFile() {
    return new File(plugin.getDataFolder(), "advancements.yml");
  }

  private void resetTab() {
    if (api == null) {
      return;
    }
    try {
      api.unregisterAdvancementTab(TAB_NAMESPACE);
    } catch (Exception ex) {
      plugin.getLogger().warning("[Advancements] Failed to unregister advancement tab: " + ex.getMessage());
    }
    tab = null;
    root = null;
    pendingAdvancements.clear();
  }

  private void createTab() {
    if (api == null) {
      return;
    }
    tab = api.createAdvancementTab(TAB_NAMESPACE);
    AdvancementDisplay display = new AdvancementDisplay(
        Material.NETHER_STAR,
        "Enter the Dungeons",
        AdvancementFrameType.CHALLENGE,
        true,
        true,
        0,
        0,
        "Step into an RPG world."
    );
    root = new RootAdvancement(tab, "root", display, "textures/block/obsidian.png");
    tab.getEventManager().register(tab, PlayerLoadingCompletedEvent.class, event -> {
      tab.showTab(event.getPlayer());
    });
  }

  private void finalizeRegistrations() {
    if (tab == null || root == null) {
      return;
    }
    BaseAdvancement[] advs = pendingAdvancements.toArray(new BaseAdvancement[0]);
    tab.registerAdvancements(root, advs);
    pendingAdvancements.clear();
  }

  private record AdvancementConfig(
      boolean bossesEnabled,
      boolean dungeonsEnabled,
      boolean mobKillsEnabled,
      boolean xpLevelsEnabled,
      boolean xpTotalsEnabled,
      boolean tokensEnabled,
      List<Integer> bossThresholds,
      List<Integer> dungeonThresholds,
      List<Integer> dungeonStreaks,
      List<Integer> xpLevels,
      List<Integer> xpTotals,
      List<Integer> tokenMilestones,
      List<Integer> tokenPalletMilestones,
      int bossFirstKillTokens,
      int bossThresholdTokens,
      int dungeonLevelTokens,
      int dungeonThresholdTokens
  ) {
    static AdvancementConfig defaults() {
      return new AdvancementConfig(
          true,
          true,
          true,
          true,
          true,
          true,
          List.of(5, 20),
          List.of(5, 20),
          List.of(3, 5),
          List.of(10, 25, 50, 100),
          List.of(1000, 5000, 10000, 25000, 50000),
          List.of(100, 1000, 10000),
          List.of(1, 5, 10),
          BOSS_FIRST_KILL_TOKENS,
          BOSS_THRESHOLD_TOKENS,
          DUNGEON_LEVEL_TOKENS,
          DUNGEON_THRESHOLD_TOKENS
      );
    }

    static AdvancementConfig from(YamlConfiguration cfg) {
      AdvancementConfig defaults = defaults();
      ConfigurationSection adv = cfg.getConfigurationSection("advancements");
      if (adv == null) {
        return defaults;
      }
      ConfigurationSection categories = adv.getConfigurationSection("categories");
      boolean bossesEnabled = bool(categories, "bosses", defaults.bossesEnabled);
      boolean dungeonsEnabled = bool(categories, "dungeons", defaults.dungeonsEnabled);
      boolean mobKillsEnabled = bool(categories, "mobKills", defaults.mobKillsEnabled);
      boolean xpLevelsEnabled = bool(categories, "xpLevels", defaults.xpLevelsEnabled);
      boolean xpTotalsEnabled = bool(categories, "xpTotals", defaults.xpTotalsEnabled);
      boolean tokensEnabled = bool(categories, "tokens", defaults.tokensEnabled);

      ConfigurationSection bossSection = adv.getConfigurationSection("bosses");
      List<Integer> bossThresholds = intList(bossSection, "thresholds", defaults.bossThresholds);
      int bossFirstKillTokens = intValue(bossSection, "rewards.firstKillTokens", defaults.bossFirstKillTokens);
      int bossThresholdTokens = intValue(bossSection, "rewards.thresholdTokens", defaults.bossThresholdTokens);

      ConfigurationSection dungeonSection = adv.getConfigurationSection("dungeons");
      List<Integer> dungeonThresholds = intList(dungeonSection, "thresholds", defaults.dungeonThresholds);
      List<Integer> dungeonStreaks = intList(dungeonSection, "streaks", defaults.dungeonStreaks);
      int dungeonLevelTokens = intValue(dungeonSection, "rewards.levelTokens", defaults.dungeonLevelTokens);
      int dungeonThresholdTokens = intValue(dungeonSection, "rewards.completionTokens", defaults.dungeonThresholdTokens);

      ConfigurationSection xpSection = adv.getConfigurationSection("xp");
      List<Integer> xpLevels = intList(xpSection, "levels", defaults.xpLevels);
      List<Integer> xpLevelRange = intRangeList(xpSection, "levelsRange");
      if (!xpLevelRange.isEmpty()) {
        xpLevels = mergeSorted(xpLevels, xpLevelRange);
      }
      List<Integer> xpTotals = intList(xpSection, "totals", defaults.xpTotals);

      ConfigurationSection tokenSection = adv.getConfigurationSection("tokens");
      List<Integer> tokenMilestones = intList(tokenSection, "milestones", defaults.tokenMilestones);
      List<Integer> tokenPalletMilestones = intList(tokenSection, "palletMilestones", defaults.tokenPalletMilestones);

      return new AdvancementConfig(
          bossesEnabled,
          dungeonsEnabled,
          mobKillsEnabled,
          xpLevelsEnabled,
          xpTotalsEnabled,
          tokensEnabled,
          bossThresholds,
          dungeonThresholds,
          dungeonStreaks,
          xpLevels,
          xpTotals,
          tokenMilestones,
          tokenPalletMilestones,
          Math.max(0, bossFirstKillTokens),
          Math.max(0, bossThresholdTokens),
          Math.max(0, dungeonLevelTokens),
          Math.max(0, dungeonThresholdTokens)
      );
    }

    private static boolean bool(ConfigurationSection section, String key, boolean def) {
      if (section == null) {
        return def;
      }
      return section.getBoolean(key, def);
    }

    private static int intValue(ConfigurationSection section, String key, int def) {
      if (section == null) {
        return def;
      }
      return Math.max(0, section.getInt(key, def));
    }

    private static List<Integer> intList(ConfigurationSection section, String key, List<Integer> def) {
      if (section == null) {
        return def;
      }
      if (!section.contains(key)) {
        return def;
      }
      List<Integer> values = section.getIntegerList(key);
      if (values == null) {
        return def;
      }
      List<Integer> filtered = new ArrayList<>();
      for (Integer value : values) {
        if (value == null) {
          continue;
        }
        filtered.add(value);
      }
      return List.copyOf(filtered);
    }

    private static List<Integer> intRangeList(ConfigurationSection section, String key) {
      if (section == null) {
        return List.of();
      }
      ConfigurationSection range = section.getConfigurationSection(key);
      if (range == null) {
        return List.of();
      }
      int min = Math.max(1, range.getInt("min", 0));
      int max = Math.max(0, range.getInt("max", 0));
      int step = Math.max(1, range.getInt("step", 1));
      if (max <= 0 || min > max) {
        return List.of();
      }
      List<Integer> values = new ArrayList<>();
      for (int value = min; value <= max; value += step) {
        values.add(value);
      }
      return List.copyOf(values);
    }

    private static List<Integer> mergeSorted(List<Integer> a, List<Integer> b) {
      TreeSet<Integer> merged = new TreeSet<>();
      merged.addAll(a);
      merged.addAll(b);
      return List.copyOf(merged);
    }
  }
}
