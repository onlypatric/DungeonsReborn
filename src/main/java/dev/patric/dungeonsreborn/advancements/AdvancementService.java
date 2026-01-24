package dev.patric.dungeonsreborn.advancements;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
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
import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopTokenTierSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.logging.AdvancementAuditLog;
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
  private final Map<String, YamlAdvancementNode> yamlNodes = new HashMap<>();
  private final Map<CriteriaType, Map<String, List<YamlAdvancementNode>>> yamlCriteria = new EnumMap<>(CriteriaType.class);
  private final Map<CriteriaType, List<YamlAdvancementNode>> yamlCriteriaWildcard = new EnumMap<>(CriteriaType.class);
  private final Map<String, AdvancementTab> yamlTabs = new LinkedHashMap<>();
  private final Map<String, RootAdvancement> yamlRoots = new LinkedHashMap<>();
  private final Map<String, List<BaseAdvancement>> yamlPending = new LinkedHashMap<>();
  private final Map<String, BaseAdvancement> advancementLookup = new HashMap<>();
  private final Map<BaseAdvancement, String> advancementReverseLookup = new IdentityHashMap<>();
  private final Map<String, FallbackAdvancement> fallbackAdvancements = new HashMap<>();
  private final Map<String, FallbackYamlNode> fallbackYamlNodes = new HashMap<>();
  private final Map<CriteriaType, Map<String, List<FallbackYamlNode>>> fallbackYamlCriteria = new EnumMap<>(CriteriaType.class);
  private final Map<CriteriaType, List<FallbackYamlNode>> fallbackYamlWildcard = new EnumMap<>(CriteriaType.class);
  private final Map<String, String> idMigrations = new HashMap<>();
  private int schemaVersion = AdvancementIds.CURRENT_SCHEMA_VERSION;
  private List<String> worldAllow = List.of();
  private List<String> worldDeny = List.of();
  private final Map<String, List<String>> worldAllowByCategory = new HashMap<>();
  private final Map<String, List<String>> worldDenyByCategory = new HashMap<>();
  private java.util.function.BiPredicate<Player, String> regionPredicate;
  private AdvancementTab tab;
  private RootAdvancement root;
  private boolean enabled;
  private boolean fallbackMode;
  private UltimateAdvancementAPI api;
  private ShopYamlRegistry shopRegistry;
  private ProgressionService progressionService;
  private AdvancementAuditLog auditLog;
  private AdvancementFallbackStore fallbackStore;
  private AdvancementConfig config = AdvancementConfig.defaults();
  private YamlConfiguration rawConfig;

  public AdvancementService(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public boolean enable() {
    if (enabled) {
      return true;
    }
    try {
      if (Bukkit.getPluginManager().getPlugin("UltimateAdvancementAPI") == null) {
        plugin.getLogger().warning("[Advancements] UltimateAdvancementAPI not installed, using fallback mode.");
        fallbackMode = true;
        enabled = true;
        reloadConfig();
        initFallbackStore();
        plugin.getLogger().info("[Advancements] Initialized fallback mode");
        return true;
      }
      api = UltimateAdvancementAPI.getInstance(plugin);
      if (api == null) {
        plugin.getLogger().warning("[Advancements] UltimateAdvancementAPI instance unavailable, using fallback mode.");
        fallbackMode = true;
        enabled = true;
        reloadConfig();
        initFallbackStore();
        plugin.getLogger().info("[Advancements] Initialized fallback mode");
        return true;
      }
      fallbackMode = false;
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
    fallbackMode = false;
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
    yamlNodes.clear();
    yamlCriteria.clear();
    yamlCriteriaWildcard.clear();
    yamlTabs.clear();
    yamlRoots.clear();
    yamlPending.clear();
    advancementLookup.clear();
    advancementReverseLookup.clear();
    fallbackAdvancements.clear();
    fallbackYamlNodes.clear();
    fallbackYamlCriteria.clear();
    fallbackYamlWildcard.clear();
    idMigrations.clear();
    schemaVersion = AdvancementIds.CURRENT_SCHEMA_VERSION;
    worldAllow = List.of();
    worldDeny = List.of();
    worldAllowByCategory.clear();
    worldDenyByCategory.clear();
    shopRegistry = null;
    progressionService = null;
    auditLog = null;
    fallbackStore = null;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isFallbackMode() {
    return fallbackMode;
  }

  private boolean useFallback() {
    return fallbackMode || api == null;
  }

  public void setShopRegistry(ShopYamlRegistry shopRegistry) {
    this.shopRegistry = shopRegistry;
  }

  public void setProgressionService(ProgressionService progressionService) {
    this.progressionService = progressionService;
  }

  public void setAuditLog(AdvancementAuditLog auditLog) {
    this.auditLog = auditLog;
  }

  public void setRegionPredicate(java.util.function.BiPredicate<Player, String> regionPredicate) {
    this.regionPredicate = regionPredicate;
  }

  private void initFallbackStore() {
    if (fallbackStore == null && plugin instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
      fallbackStore = new AdvancementFallbackStore(javaPlugin);
      fallbackStore.load();
    }
  }

  public boolean isWorldAllowed(Player player, String categoryId) {
    if (player == null) {
      return false;
    }
    String worldName = player.getWorld().getName();
    if (!isWorldAllowed(worldName, categoryId)) {
      return false;
    }
    if (regionPredicate != null && !regionPredicate.test(player, categoryId)) {
      return false;
    }
    return true;
  }

  public void reloadConfig() {
    File file = configFile();
    if (!file.exists()) {
      plugin.saveResource("advancements.yml", false);
    }
    rawConfig = YamlConfiguration.loadConfiguration(file);
    config = AdvancementConfig.from(rawConfig);
    loadIdMigrations();
    loadWorldRules();
  }

  public void reloadAll(MobRegistry mobs, DungeonYamlRegistry dungeons) {
    rebuildAll(mobs, dungeons);
  }

  public void rebuildAll(MobRegistry mobs, DungeonYamlRegistry dungeons) {
    if (!enabled) {
      return;
    }
    reloadConfig();
    resetTab();
    if (!fallbackMode) {
      createTab();
    } else {
      initFallbackStore();
    }
    advancementLookup.clear();
    advancementReverseLookup.clear();
    fallbackAdvancements.clear();
    fallbackYamlNodes.clear();
    fallbackYamlCriteria.clear();
    fallbackYamlWildcard.clear();
    registerYamlAdvancements();
    registerXpAdvancements();
    registerXpTotalAdvancements();
    registerTokenAdvancements();
    registerTokenPalletAdvancements();
    registerMobKillAdvancements(mobs);
    registerBossAdvancements(mobs);
    registerDungeonAdvancements(dungeons);
    if (!fallbackMode) {
      finalizeRegistrations();
    }
  }

  public void grantRoot(Player player) {
    if (!enabled || player == null) {
      return;
    }
    if (tab != null && root != null) {
      tab.grantRootAdvancement(player);
    }
    for (Map.Entry<String, RootAdvancement> entry : yamlRoots.entrySet()) {
      AdvancementTab categoryTab = yamlTabs.get(entry.getKey());
      if (categoryTab != null && entry.getValue() != null) {
        categoryTab.grantRootAdvancement(player);
      }
    }
  }

  public void registerBossAdvancements(MobRegistry mobRegistry) {
    if (!enabled || mobRegistry == null || !config.bossesEnabled) {
      return;
    }
    if (useFallback()) {
      registerBossFallback(mobRegistry);
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
      registerLookup("boss_first_kill_" + mobId, advancement);
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
        registerLookup("boss_kills_" + mobId + "_" + threshold, thresholdAdvancement);
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
    if (!enabled || mobRegistry == null || !config.mobKillsEnabled) {
      return;
    }
    if (useFallback()) {
      registerMobKillFallback(mobRegistry);
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
      registerLookup("mob_kill_" + mobId, advancement);
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
    if (!enabled || !config.xpLevelsEnabled) {
      return;
    }
    if (useFallback()) {
      registerXpLevelFallback();
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
          "Aether Level " + level,
          frameType,
          true,
          true,
          x,
          y,
          "Reach Aether level " + level + "."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("xp_level_" + level),
          display,
          root,
          level
      );
      registerLookup("xp_level_" + level, advancement);
      pendingAdvancements.add(advancement);
      xpLevelAdvancements.put(level, advancement);
      y++;
    }
  }

  public void registerXpTotalAdvancements() {
    if (!enabled || !config.xpTotalsEnabled) {
      return;
    }
    if (useFallback()) {
      registerXpTotalFallback();
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
          "Total Aether XP " + total,
          frameType,
          true,
          true,
          x,
          y,
          "Earn " + total + " total Aether XP."
      );
      BaseAdvancement advancement = new BaseAdvancement(
          AdvancementIds.key("xp_total_" + total),
          display,
          root,
          1
      );
      registerLookup("xp_total_" + total, advancement);
      pendingAdvancements.add(advancement);
      xpTotalAdvancements.put(total, advancement);
      y++;
    }
  }

  public void registerTokenAdvancements() {
    if (!enabled || !config.tokensEnabled) {
      return;
    }
    if (useFallback()) {
      registerTokenFallback();
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
      registerLookup("tokens_earned_" + threshold, advancement);
      pendingAdvancements.add(advancement);
      tokenMilestones.put(threshold, advancement);
      y++;
    }
  }

  public void registerTokenPalletAdvancements() {
    if (!enabled || !config.tokensEnabled) {
      return;
    }
    if (useFallback()) {
      registerTokenPalletFallback();
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
      registerLookup("tokens_pallets_" + pallet, advancement);
      pendingAdvancements.add(advancement);
      tokenPalletMilestones.put(tokenCount, advancement);
      y++;
    }
  }

  public void registerDungeonAdvancements(DungeonYamlRegistry registry) {
    if (!enabled || registry == null || !config.dungeonsEnabled) {
      return;
    }
    if (useFallback()) {
      registerDungeonFallback(registry);
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
      registerLookup(key, advancement);
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
      registerLookup("dungeon_no_death_" + dungeonId + "_" + level.level(), noDeathAdv);
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
        registerLookup("dungeon_time_" + dungeonId + "_" + level.level(), timeAdv);
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
      registerLookup("dungeon_completions_" + dungeonId + "_" + count, advancement);
      pendingAdvancements.add(advancement);
      thresholds.add(new DungeonThreshold(advancement, config.dungeonThresholdTokens, count));
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
      registerLookup("dungeon_streak_" + dungeonId + "_" + streak, streakAdvancement);
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
    if (!isWorldAllowed(killer, "bosses")) {
      return;
    }
    if (useFallback()) {
      fallbackIncrement(killer, "boss_first_kill_" + mobId, "bosses", "boss_kill", mobId, 1, 0);
      for (int threshold : config.bossThresholds) {
        if (threshold <= 0) {
          continue;
        }
        fallbackIncrement(killer, "boss_kills_" + mobId + "_" + threshold, "bosses", "boss_kill", mobId, 1, 0);
      }
      progressYamlFallback(CriteriaType.BOSS_KILL, mobId, killer, 1, 0);
      return;
    }
    String title = bossTitle(spec, mobId);
    BaseAdvancement advancement = bossFirstKill.get(mobId);
    if (advancement != null) {
      boolean wasGranted = advancement.isGranted(killer);
      advancement.incrementProgression(killer);
      auditProgress(killer, "boss_first_kill_" + mobId, "bosses", "boss_kill", mobId, 1, 0);
      if (!wasGranted && advancement.isGranted(killer)) {
        grantBossRewards(spec, killer, config.bossFirstKillTokens);
        announceAdvancement(killer, title, null);
        auditGrant(killer, "boss_first_kill_" + mobId, "bosses", "boss_kill", mobId, 1, 0);
      }
    }
    List<BossThreshold> thresholds = bossKillThresholds.get(mobId);
    if (thresholds != null) {
      for (BossThreshold threshold : thresholds) {
        boolean wasGranted = threshold.advancement.isGranted(killer);
        threshold.advancement.incrementProgression(killer);
        auditProgress(killer, "boss_kills_" + mobId + "_" + threshold.count, "bosses", "boss_kill",
            mobId, 1, 0);
        if (!wasGranted && threshold.advancement.isGranted(killer)) {
          grantBossRewards(spec, killer, threshold.tokenReward);
          announceAdvancement(killer, title, threshold.count);
          auditGrant(killer, "boss_kills_" + mobId + "_" + threshold.count, "bosses", "boss_kill",
              mobId, 1, 0);
        }
      }
    }
    progressYamlAdvancements(CriteriaType.BOSS_KILL, mobId, killer, 1, 0);
  }

  public void recordDungeonCompletion(DungeonSpec dungeon, int level, Player player, boolean hadDeath,
      long durationMillis, int timeLimitSeconds) {
    if (!enabled || !config.dungeonsEnabled || dungeon == null || player == null) {
      return;
    }
    if (!isWorldAllowed(player, "dungeons")) {
      return;
    }
    if (useFallback()) {
      String dungeonId = dungeon.id();
      fallbackIncrement(player, "dungeon_level_" + dungeonId + "_" + level, "dungeons", "dungeon_complete",
          dungeonId, 1, 0);
      for (int count : config.dungeonThresholds) {
        if (count <= 0) {
          continue;
        }
        fallbackIncrement(player, "dungeon_completions_" + dungeonId + "_" + count, "dungeons", "dungeon_complete",
            dungeonId, 1, 0);
      }
      if (!hadDeath) {
        fallbackIncrement(player, "dungeon_no_death_" + dungeonId + "_" + level, "dungeons", "dungeon_no_death",
            dungeonId, 1, 0);
      }
      if (timeLimitSeconds > 0 && durationMillis >= 0 && durationMillis <= timeLimitSeconds * 1000L) {
        fallbackIncrement(player, "dungeon_time_" + dungeonId + "_" + level, "dungeons", "dungeon_speedrun",
            dungeonId, 1, 0);
      }
      if (!config.dungeonStreaks.isEmpty()) {
        Map<UUID, Integer> streaks = dungeonStreakCounts.computeIfAbsent(dungeonId, id -> new HashMap<>());
        int current = streaks.getOrDefault(player.getUniqueId(), 0) + 1;
        streaks.put(player.getUniqueId(), current);
        for (int streak : config.dungeonStreaks) {
          if (streak <= 0 || current < streak) {
            continue;
          }
          fallbackIncrement(player, "dungeon_streak_" + dungeonId + "_" + streak, "dungeons", "dungeon_streak",
              dungeonId, 1, 0);
        }
      }
      progressYamlFallback(CriteriaType.DUNGEON_COMPLETE, dungeonId, player, 1, 0);
      return;
    }
    String dungeonId = dungeon.id();
    BaseAdvancement levelAdvancement = dungeonLevelAdvancements.get(dungeonLevelKey(dungeonId, level));
    if (levelAdvancement != null) {
      boolean wasGranted = levelAdvancement.isGranted(player);
      levelAdvancement.incrementProgression(player);
      auditProgress(player, "dungeon_level_" + dungeonId + "_" + level, "dungeons", "dungeon_complete",
          dungeonId, 1, 0);
      if (!wasGranted && levelAdvancement.isGranted(player)) {
        DungeonSpec.DungeonLevel levelSpec = dungeon.levels().get(level);
        if (levelSpec != null) {
          grantDungeonRewards(levelSpec, player, config.dungeonLevelTokens);
        }
        auditGrant(player, "dungeon_level_" + dungeonId + "_" + level, "dungeons", "dungeon_complete",
            dungeonId, 1, 0);
      }
    }
    List<DungeonThreshold> thresholds = dungeonCompletionThresholds.get(dungeonId);
    if (thresholds != null) {
      for (DungeonThreshold threshold : thresholds) {
        boolean wasGranted = threshold.advancement.isGranted(player);
        threshold.advancement.incrementProgression(player);
        auditProgress(player, "dungeon_completions_" + dungeonId + "_" + threshold.count,
            "dungeons", "dungeon_complete", dungeonId, 1, 0);
        if (!wasGranted && threshold.advancement.isGranted(player)) {
          giveTokenBundle(player, threshold.tokenReward);
          auditGrant(player, "dungeon_completions_" + dungeonId + "_" + threshold.count,
              "dungeons", "dungeon_complete", dungeonId, 1, 0);
        }
      }
    }
    if (!hadDeath) {
      BaseAdvancement noDeathAdv = dungeonNoDeath.get(dungeonLevelKey(dungeonId, level));
      if (noDeathAdv != null && !noDeathAdv.isGranted(player)) {
        noDeathAdv.incrementProgression(player);
        auditProgress(player, "dungeon_no_death_" + dungeonId + "_" + level, "dungeons", "dungeon_no_death",
            dungeonId, 1, 0);
        if (noDeathAdv.isGranted(player)) {
          auditGrant(player, "dungeon_no_death_" + dungeonId + "_" + level, "dungeons", "dungeon_no_death",
              dungeonId, 1, 0);
        }
      }
    }
    if (timeLimitSeconds > 0 && durationMillis >= 0 && durationMillis <= timeLimitSeconds * 1000L) {
      BaseAdvancement timeAdv = dungeonTime.get(dungeonLevelKey(dungeonId, level));
      if (timeAdv != null && !timeAdv.isGranted(player)) {
        timeAdv.incrementProgression(player);
        auditProgress(player, "dungeon_time_" + dungeonId + "_" + level, "dungeons", "dungeon_speedrun",
            dungeonId, 1, 0);
        if (timeAdv.isGranted(player)) {
          auditGrant(player, "dungeon_time_" + dungeonId + "_" + level, "dungeons", "dungeon_speedrun",
              dungeonId, 1, 0);
        }
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
            auditProgress(player, "dungeon_streak_" + dungeonId + "_" + entry.getKey(), "dungeons",
                "dungeon_streak", dungeonId, 1, 0);
            if (adv.isGranted(player)) {
              auditGrant(player, "dungeon_streak_" + dungeonId + "_" + entry.getKey(), "dungeons",
                  "dungeon_streak", dungeonId, 1, 0);
            }
          }
        }
      }
    }
    progressYamlAdvancements(CriteriaType.DUNGEON_COMPLETE, dungeonId, player, 1, 0);
  }

  public void recordDungeonFailure(DungeonSpec dungeon, Player player) {
    if (!enabled || !config.dungeonsEnabled || dungeon == null || player == null) {
      return;
    }
    if (!isWorldAllowed(player, "dungeons")) {
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
    if (!isWorldAllowed(player, "xpLevels")) {
      return;
    }
    if (useFallback()) {
      for (int level : config.xpLevels) {
        if (level <= 0) {
          continue;
        }
        fallbackIncrement(player, "xp_level_" + level, "xpLevels", "xp_level", null, levelsGained, 0);
      }
      progressYamlFallback(CriteriaType.XP_LEVEL, null, player, levelsGained, 0);
      return;
    }
    for (Map.Entry<Integer, BaseAdvancement> entry : xpLevelAdvancements.entrySet()) {
      BaseAdvancement advancement = entry.getValue();
      if (advancement == null || advancement.isGranted(player)) {
        continue;
      }
      int increments = 0;
      for (int i = 0; i < levelsGained && !advancement.isGranted(player); i++) {
        advancement.incrementProgression(player);
        increments++;
      }
      if (increments > 0) {
        auditProgress(player, "xp_level_" + entry.getKey(), "xpLevels", "xp_level", null, increments, 0);
        if (advancement.isGranted(player)) {
          auditGrant(player, "xp_level_" + entry.getKey(), "xpLevels", "xp_level", null, increments, 0);
        }
      }
    }
    progressYamlAdvancements(CriteriaType.XP_LEVEL, null, player, levelsGained, 0);
  }

  public void recordXpTotal(Player player, int totalXp) {
    if (!enabled || !config.xpTotalsEnabled || player == null || totalXp <= 0) {
      return;
    }
    if (!isWorldAllowed(player, "xpTotals")) {
      return;
    }
    if (useFallback()) {
      for (int threshold : config.xpTotals) {
        if (threshold <= 0) {
          continue;
        }
        if (totalXp < threshold) {
          continue;
        }
        fallbackIncrement(player, "xp_total_" + threshold, "xpTotals", "xp_total", null, 1, totalXp);
      }
      progressYamlFallback(CriteriaType.XP_TOTAL, null, player, 1, totalXp);
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
      auditProgress(player, "xp_total_" + threshold, "xpTotals", "xp_total", null, 1, totalXp);
      if (advancement.isGranted(player)) {
        auditGrant(player, "xp_total_" + threshold, "xpTotals", "xp_total", null, 1, totalXp);
      }
    }
    progressYamlAdvancements(CriteriaType.XP_TOTAL, null, player, 1, totalXp);
  }

  public void recordTokensEarned(Player player, int amount) {
    if (!enabled || !config.tokensEnabled || player == null || amount <= 0) {
      return;
    }
    if (!isWorldAllowed(player, "tokens")) {
      return;
    }
    if (useFallback()) {
      for (int threshold : config.tokenMilestones) {
        if (threshold <= 0) {
          continue;
        }
        fallbackIncrement(player, "tokens_earned_" + threshold, "tokens", "tokens_earned", null, amount, 0);
      }
      for (int pallet : config.tokenPalletMilestones) {
        if (pallet <= 0) {
          continue;
        }
        fallbackIncrement(player, "tokens_pallets_" + pallet, "tokens", "tokens_pallets", null, amount, 0);
      }
      progressYamlFallback(CriteriaType.TOKENS, null, player, amount, 0);
      return;
    }
    incrementTokenAdvancements(player, tokenMilestones, amount, "tokens", "tokens_earned", null);
    incrementTokenAdvancements(player, tokenPalletMilestones, amount, "tokens", "tokens_pallets", null);
    progressYamlAdvancements(CriteriaType.TOKENS, null, player, amount, 0);
  }

  public void recordMobKill(Player player, MobSpec spec) {
    if (!enabled || !config.mobKillsEnabled || player == null || spec == null) {
      return;
    }
    if (!isWorldAllowed(player, "mobKills")) {
      return;
    }
    if (useFallback()) {
      fallbackIncrement(player, "mob_kill_" + spec.id(), "mobKills", "mob_kill", spec.id(), 1, 0);
      progressYamlFallback(CriteriaType.MOB_KILL, spec.id(), player, 1, 0);
      return;
    }
    BaseAdvancement advancement = mobKillAdvancements.get(spec.id());
    if (advancement == null || advancement.isGranted(player)) {
      return;
    }
    advancement.incrementProgression(player);
    auditProgress(player, "mob_kill_" + spec.id(), "mobKills", "mob_kill", spec.id(), 1, 0);
    if (advancement.isGranted(player)) {
      auditGrant(player, "mob_kill_" + spec.id(), "mobKills", "mob_kill", spec.id(), 1, 0);
    }
    progressYamlAdvancements(CriteriaType.MOB_KILL, spec.id(), player, 1, 0);
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

  private void incrementTokenAdvancements(Player player, Map<Integer, BaseAdvancement> milestones, int amount,
      String categoryId, String source, String detail) {
    for (Map.Entry<Integer, BaseAdvancement> entry : milestones.entrySet()) {
      BaseAdvancement advancement = entry.getValue();
      if (advancement == null || advancement.isGranted(player)) {
        continue;
      }
      int remaining = amount;
      int increments = 0;
      while (remaining > 0 && !advancement.isGranted(player)) {
        advancement.incrementProgression(player);
        remaining--;
        increments++;
      }
      if (increments > 0) {
        String advId = lookupId(advancement);
        if (advId != null) {
          auditProgress(player, advId, categoryId, source, detail, increments, 0);
          if (advancement.isGranted(player)) {
            auditGrant(player, advId, categoryId, source, detail, increments, 0);
          }
        }
      }
    }
  }

  private void auditProgress(Player player, String advancementId, String categoryId, String source, String detail,
      int amount, int total) {
    if (auditLog == null) {
      return;
    }
    auditLog.recordProgress(player, advancementId, categoryId, source, detail, amount, total);
  }

  private void auditGrant(Player player, String advancementId, String categoryId, String source, String detail,
      int amount, int total) {
    if (auditLog == null) {
      return;
    }
    auditLog.recordGrant(player, advancementId, categoryId, source, detail, amount, total);
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

  private record DungeonThreshold(BaseAdvancement advancement, int tokenReward, int count) {
  }

  private record FallbackAdvancement(String id, String categoryId, int required, String title, String description) {
  }

  private record FallbackYamlNode(String id, String categoryId, YamlCriteria criteria, List<String> requires,
      int requiresAny, YamlRewards rewards, String title) {
  }

  private File configFile() {
    return new File(plugin.getDataFolder(), "advancements.yml");
  }

  private void resetTab() {
    if (api == null) {
      return;
    }
    if (tab != null) {
      try {
        api.unregisterAdvancementTab(TAB_NAMESPACE);
      } catch (Exception ex) {
        String message = ex.getMessage();
        if (message == null || !message.contains("has not been initialised yet")) {
          plugin.getLogger().warning("[Advancements] Failed to unregister advancement tab: " + ex.getMessage());
        }
      }
    }
    for (String namespace : yamlTabs.keySet()) {
      try {
        api.unregisterAdvancementTab(namespace);
      } catch (Exception ex) {
        plugin.getLogger().warning("[Advancements] Failed to unregister advancement tab: " + ex.getMessage());
      }
    }
    tab = null;
    root = null;
    pendingAdvancements.clear();
    yamlTabs.clear();
    yamlRoots.clear();
    yamlPending.clear();
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
    if (tab != null && root != null) {
      BaseAdvancement[] advs = pendingAdvancements.toArray(new BaseAdvancement[0]);
      tab.registerAdvancements(root, advs);
      pendingAdvancements.clear();
    }
    if (!yamlPending.isEmpty()) {
      for (Map.Entry<String, List<BaseAdvancement>> entry : yamlPending.entrySet()) {
        AdvancementTab categoryTab = yamlTabs.get(entry.getKey());
        RootAdvancement categoryRoot = yamlRoots.get(entry.getKey());
        if (categoryTab == null || categoryRoot == null) {
          continue;
        }
        List<BaseAdvancement> nodes = entry.getValue();
        BaseAdvancement[] advs = nodes.toArray(new BaseAdvancement[0]);
        categoryTab.registerAdvancements(categoryRoot, advs);
      }
      yamlPending.clear();
    }
  }

  private void registerYamlAdvancements() {
    yamlNodes.clear();
    yamlCriteria.clear();
    yamlCriteriaWildcard.clear();
    yamlTabs.clear();
    yamlRoots.clear();
    yamlPending.clear();
    fallbackYamlNodes.clear();
    fallbackYamlCriteria.clear();
    fallbackYamlWildcard.clear();
    if (!enabled || rawConfig == null) {
      return;
    }
    if (useFallback()) {
      registerYamlFallbackAdvancements();
      return;
    }
    if (tab == null || root == null) {
      return;
    }
    ConfigurationSection defs = rawConfig.getConfigurationSection("advancements.definitions");
    if (defs == null) {
      return;
    }
    ConfigurationSection categories = defs.getConfigurationSection("categories");
    if (categories == null) {
      return;
    }
    Set<String> usedIds = new HashSet<>();
    List<String> categoryIds = new ArrayList<>(categories.getKeys(false));
    categoryIds.sort(Comparator
        .comparingInt((String id) -> categories.getInt(id + ".order", 0))
        .thenComparing(String::compareToIgnoreCase));
    for (String categoryId : categoryIds) {
      ConfigurationSection categorySection = categories.getConfigurationSection(categoryId);
      if (categorySection == null) {
        continue;
      }
      String categoryTitle = resolveText(categorySection, "titleKey", "title", categoryId);
      if (categoryTitle == null || categoryTitle.isBlank()) {
        warn("Category '" + categoryId + "' missing title, skipping.");
        continue;
      }
      String categoryDescription = resolveText(categorySection, "descriptionKey", "description", "");
      Material categoryIcon = parseMaterial(categorySection.getString("icon"), Material.NETHER_STAR, categoryId);
      AdvancementFrameType categoryFrame = parseFrame(categorySection.getString("frame"), AdvancementFrameType.TASK,
          categoryId);
      int categoryX = categorySection.getInt("x", 0);
      int categoryY = categorySection.getInt("y", 0);
      boolean categoryToast = categorySection.getBoolean("showToast", true);
      boolean categoryAnnounce = categorySection.getBoolean("announceToChat", true);
      String background = categorySection.getString("background");
      boolean createTab = categorySection.getBoolean("tab", false) || (background != null && !background.isBlank());
      AdvancementDisplay categoryDisplay = new AdvancementDisplay(
          categoryIcon,
          categoryTitle,
          categoryFrame,
          categoryToast,
          categoryAnnounce,
          categoryX,
          categoryY,
          categoryDescription
      );
      BaseAdvancement categoryAdvancement = null;
      AdvancementTab categoryTab = null;
      RootAdvancement categoryRoot = null;
      String tabNamespace = null;
      if (createTab) {
        tabNamespace = tabNamespace(categoryId);
        categoryTab = api.createAdvancementTab(tabNamespace);
        AdvancementTab finalTab = categoryTab;
        finalTab.getEventManager().register(finalTab, PlayerLoadingCompletedEvent.class, event -> {
          finalTab.showTab(event.getPlayer());
        });
        String texture = (background == null || background.isBlank()) ? "textures/block/obsidian.png" : background;
        categoryRoot = new RootAdvancement(categoryTab, "root_" + AdvancementIds.key(categoryId), categoryDisplay, texture);
        yamlTabs.put(tabNamespace, categoryTab);
        yamlRoots.put(tabNamespace, categoryRoot);
        yamlPending.put(tabNamespace, new ArrayList<>());
      } else {
        String categoryKey = AdvancementIds.key("category_" + categoryId);
        categoryAdvancement = new BaseAdvancement(
            categoryKey,
            categoryDisplay,
            root,
            1
        );
        registerLookup(categoryKey, categoryAdvancement);
      }
      List<YamlAdvancementNode> categoryNodes = new ArrayList<>();
      ConfigurationSection nodes = categorySection.getConfigurationSection("nodes");
      if (nodes != null) {
        for (String nodeId : nodes.getKeys(false)) {
          ConfigurationSection nodeSection = nodes.getConfigurationSection(nodeId);
          if (nodeSection == null) {
            continue;
          }
          Advancement defaultParent = createTab ? categoryRoot : categoryAdvancement;
          YamlAdvancementNode node = parseYamlNode(nodeId, categoryId, nodeSection, defaultParent, usedIds);
          if (node == null) {
            continue;
          }
          categoryNodes.add(node);
          yamlNodes.put(node.id(), node);
          indexYamlNode(node);
          if (createTab && tabNamespace != null) {
            yamlPending.get(tabNamespace).add(node.advancement());
          } else {
            pendingAdvancements.add(node.advancement());
          }
        }
      }
      if (!categoryNodes.isEmpty()) {
        if (!createTab && categoryAdvancement != null) {
          pendingAdvancements.add(categoryAdvancement);
        }
      }
    }
  }

  private YamlAdvancementNode parseYamlNode(String nodeId, String categoryId, ConfigurationSection nodeSection,
      Advancement defaultParent, Set<String> usedIds) {
    String resolvedNodeId = migrateAdvancementId(nodeId);
    String title = resolveText(nodeSection, "titleKey", "title", nodeId);
    if (title == null || title.isBlank()) {
      warn("Node '" + nodeId + "' missing title, skipping.");
      return null;
    }
    ConfigurationSection criteriaSection = nodeSection.getConfigurationSection("criteria");
    if (criteriaSection == null) {
      warn("Node '" + nodeId + "' missing criteria, skipping.");
      return null;
    }
    CriteriaType type = CriteriaType.fromString(criteriaSection.getString("type"));
    if (type == null) {
      warn("Node '" + nodeId + "' has invalid criteria type, skipping.");
      return null;
    }
    String targetId = criteriaTarget(type, criteriaSection);
    if (type.requiresTarget() && (targetId == null || targetId.isBlank())) {
      warn("Node '" + nodeId + "' missing criteria target, skipping.");
      return null;
    }
    int amount = Math.max(1, criteriaSection.getInt("amount", 1));
    String description = resolveText(nodeSection, "descriptionKey", "description", "");
    Material icon = parseMaterial(nodeSection.getString("icon"), Material.NETHER_STAR, nodeId);
    AdvancementFrameType frame = parseFrame(nodeSection.getString("frame"), AdvancementFrameType.TASK, nodeId);
    int x = nodeSection.getInt("x", 0);
    int y = nodeSection.getInt("y", 0);
    boolean showToast = nodeSection.getBoolean("showToast", true);
    boolean announceToChat = nodeSection.getBoolean("announceToChat", true);
    AdvancementDisplay display = new AdvancementDisplay(icon, title, frame, showToast, announceToChat, x, y,
        description);
    Advancement parent = resolveParent(nodeSection.getString("parent"), defaultParent);
    String key = AdvancementIds.key(resolvedNodeId);
    if (usedIds.contains(key)) {
      warn("Duplicate advancement id '" + resolvedNodeId + "', skipping.");
      return null;
    }
    usedIds.add(key);
    BaseAdvancement advancement = new BaseAdvancement(key, display, parent, amount);
    registerLookup(resolvedNodeId, advancement);
    List<String> requires = migrateRequirementIds(nodeSection.getStringList("requires"));
    int requiresAny = Math.max(0, nodeSection.getInt("requiresAny", 0));
    YamlRewards rewards = parseRewards(nodeSection.getConfigurationSection("rewards"));
    return new YamlAdvancementNode(resolvedNodeId, categoryId, advancement, new YamlCriteria(type, targetId, amount),
        List.copyOf(requires), requiresAny, rewards);
  }

  private Advancement resolveParent(String parentId, Advancement fallback) {
    if (parentId == null || parentId.isBlank()) {
      return fallback;
    }
    YamlAdvancementNode parent = yamlNodes.get(migrateAdvancementId(parentId));
    if (parent == null) {
      warn("Parent advancement '" + parentId + "' not found, using category root.");
      return fallback;
    }
    return parent.advancement();
  }

  private void indexYamlNode(YamlAdvancementNode node) {
    CriteriaType type = node.criteria().type();
    String target = node.criteria().targetId();
    if (target == null || target.isBlank()) {
      yamlCriteriaWildcard.computeIfAbsent(type, key -> new ArrayList<>()).add(node);
      return;
    }
    yamlCriteria.computeIfAbsent(type, key -> new HashMap<>())
        .computeIfAbsent(target, key -> new ArrayList<>())
        .add(node);
  }

  private void progressYamlAdvancements(CriteriaType type, String targetId, Player player, int amount, int total) {
    if (!enabled || player == null) {
      return;
    }
    List<YamlAdvancementNode> candidates = new ArrayList<>();
    List<YamlAdvancementNode> wildcard = yamlCriteriaWildcard.get(type);
    if (wildcard != null) {
      candidates.addAll(wildcard);
    }
    if (targetId != null) {
      Map<String, List<YamlAdvancementNode>> byTarget = yamlCriteria.get(type);
      if (byTarget != null) {
        List<YamlAdvancementNode> specific = byTarget.get(targetId);
        if (specific != null) {
          candidates.addAll(specific);
        }
      }
    }
    if (candidates.isEmpty()) {
      return;
    }
    for (YamlAdvancementNode node : candidates) {
      BaseAdvancement advancement = node.advancement();
      if (advancement == null || advancement.isGranted(player)) {
        continue;
      }
      if (!node.requirementsMet(player, yamlNodes)) {
        continue;
      }
      boolean wasGranted = advancement.isGranted(player);
      if (!isWorldAllowed(player, node.categoryId())) {
        continue;
      }
      if (type == CriteriaType.XP_TOTAL) {
        if (total < node.criteria().amount()) {
          continue;
        }
        advancement.incrementProgression(player);
        auditProgress(player, node.id(), node.categoryId(), type.name().toLowerCase(Locale.ROOT), targetId, 1, total);
      } else {
        int increments = Math.max(1, amount);
        for (int i = 0; i < increments && !advancement.isGranted(player); i++) {
          advancement.incrementProgression(player);
        }
        auditProgress(player, node.id(), node.categoryId(), type.name().toLowerCase(Locale.ROOT), targetId, increments,
            total);
      }
      if (!wasGranted && advancement.isGranted(player)) {
        grantYamlRewards(node.rewards(), player);
        auditGrant(player, node.id(), node.categoryId(), type.name().toLowerCase(Locale.ROOT), targetId, amount, total);
      }
    }
  }

  private void grantYamlRewards(YamlRewards rewards, Player player) {
    if (rewards == null || player == null) {
      return;
    }
    if (rewards.tokens() > 0) {
      giveTokenBundle(player, rewards.tokens());
    }
    if (progressionService != null) {
      if (rewards.xp() > 0) {
        progressionService.awardXp(player, rewards.xp(), ProgressionAwardSource.ADVANCEMENT, "yaml");
      }
      if (rewards.skillPoints() > 0) {
        progressionService.awardSkillPoints(player, rewards.skillPoints());
      }
    }
    if (shopRegistry != null && shopRegistry.itemResolver() != null) {
      for (String itemId : rewards.items()) {
        if (itemId == null || itemId.isBlank()) {
          continue;
        }
        ItemStack resolved = shopRegistry.itemResolver().apply(itemId);
        if (resolved != null) {
          giveItemOrDrop(player, resolved.clone());
        } else {
          warn("Reward item id '" + itemId + "' could not be resolved.");
        }
      }
    }
    for (String command : rewards.commands()) {
      if (command == null || command.isBlank()) {
        continue;
      }
      String resolved = command.replace("{player}", player.getName());
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
    }
  }

  private YamlRewards parseRewards(ConfigurationSection rewardsSection) {
    if (rewardsSection == null) {
      return YamlRewards.empty();
    }
    int tokens = Math.max(0, rewardsSection.getInt("tokens", 0));
    int xp = Math.max(0, rewardsSection.getInt("xp", 0));
    int skillPoints = Math.max(0, rewardsSection.getInt("skillPoints", 0));
    List<String> items = rewardsSection.getStringList("items");
    List<String> commands = rewardsSection.getStringList("commands");
    return new YamlRewards(tokens, xp, skillPoints, List.copyOf(items), List.copyOf(commands));
  }

  private void loadIdMigrations() {
    idMigrations.clear();
    if (rawConfig == null) {
      schemaVersion = AdvancementIds.CURRENT_SCHEMA_VERSION;
      return;
    }
    schemaVersion = rawConfig.getInt("advancements.schemaVersion", AdvancementIds.CURRENT_SCHEMA_VERSION);
    if (schemaVersion > AdvancementIds.CURRENT_SCHEMA_VERSION) {
      warn("Advancement schemaVersion " + schemaVersion + " is newer than supported version "
          + AdvancementIds.CURRENT_SCHEMA_VERSION + ".");
    }
    ConfigurationSection section = rawConfig.getConfigurationSection("advancements.idMigrations");
    if (section == null) {
      return;
    }
    for (String key : section.getKeys(false)) {
      String target = section.getString(key);
      if (target == null || target.isBlank()) {
        continue;
      }
      String from = AdvancementIds.key(key);
      String to = AdvancementIds.key(target);
      if (from.isBlank() || to.isBlank()) {
        continue;
      }
      idMigrations.put(from, to);
    }
  }

  private void registerBossFallback(MobRegistry mobRegistry) {
    fallbackAdvancements.clear();
    List<Integer> thresholds = config.bossThresholds;
    for (String mobId : mobRegistry.ids()) {
      MobSpec spec = mobRegistry.get(mobId);
      if (spec == null) {
        continue;
      }
      if (spec.bossBar() == null && spec.bossBroadcast() == null) {
        continue;
      }
      String title = bossTitle(spec, mobId);
      addFallbackAdvancement("boss_first_kill_" + mobId, "bosses", 1, title, "Defeat " + title + ".");
      for (int threshold : thresholds) {
        if (threshold <= 0) {
          continue;
        }
        addFallbackAdvancement("boss_kills_" + mobId + "_" + threshold, "bosses", threshold,
            title + " " + threshold + "x", "Defeat " + title + " " + threshold + " times.");
      }
    }
  }

  private void registerMobKillFallback(MobRegistry mobRegistry) {
    for (String mobId : mobRegistry.ids()) {
      MobSpec spec = mobRegistry.get(mobId);
      if (spec == null) {
        continue;
      }
      String title = mobTitle(spec, mobId);
      addFallbackAdvancement("mob_kill_" + mobId, "mobKills", 1, title, "Defeat " + title + ".");
    }
  }

  private void registerXpLevelFallback() {
    for (int level : config.xpLevels) {
      if (level <= 0) {
        continue;
      }
      addFallbackAdvancement("xp_level_" + level, "xpLevels", level, "Aether Level " + level,
          "Reach Aether level " + level + ".");
    }
  }

  private void registerXpTotalFallback() {
    for (int total : config.xpTotals) {
      if (total <= 0) {
        continue;
      }
      addFallbackAdvancement("xp_total_" + total, "xpTotals", 1, "Total Aether XP " + total,
          "Earn " + total + " total Aether XP.");
    }
  }

  private void registerTokenFallback() {
    for (int threshold : config.tokenMilestones) {
      if (threshold <= 0) {
        continue;
      }
      addFallbackAdvancement("tokens_earned_" + threshold, "tokens", threshold,
          "Tokens Earned " + threshold, "Earn " + threshold + " tokens.");
    }
  }

  private void registerTokenPalletFallback() {
    for (int pallet : config.tokenPalletMilestones) {
      if (pallet <= 0) {
        continue;
      }
      int tokenCount = pallet * 4096;
      addFallbackAdvancement("tokens_pallets_" + pallet, "tokens", tokenCount,
          "Token Pallets " + pallet, "Earn " + pallet + " token pallets (" + tokenCount + " tokens).");
    }
  }

  private void registerDungeonFallback(DungeonYamlRegistry registry) {
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null || dungeon.levels().isEmpty()) {
      return;
    }
    String dungeonId = dungeon.id();
    String title = dungeonTitle(dungeon, dungeonId);
    for (DungeonSpec.DungeonLevel level : dungeon.levels().values()) {
      addFallbackAdvancement("dungeon_level_" + dungeonId + "_" + level.level(), "dungeons", 1,
          title + " Lv " + level.level(), "Complete level " + level.level() + " of " + title + ".");
      addFallbackAdvancement("dungeon_no_death_" + dungeonId + "_" + level.level(), "dungeons", 1,
          title + " Lv " + level.level() + " (No Deaths)",
          "Complete level " + level.level() + " without dying.");
      int timeLimit = Math.max(0, level.timeLimitSeconds());
      if (timeLimit > 0) {
        addFallbackAdvancement("dungeon_time_" + dungeonId + "_" + level.level(), "dungeons", 1,
            title + " Lv " + level.level() + " (Speedrun)",
            "Complete within " + timeLimit + " seconds.");
      }
    }
    for (int count : config.dungeonThresholds) {
      if (count <= 0) {
        continue;
      }
      addFallbackAdvancement("dungeon_completions_" + dungeonId + "_" + count, "dungeons", count,
          title + " " + count + "x", "Complete " + title + " " + count + " times.");
    }
    for (int streak : config.dungeonStreaks) {
      if (streak <= 0) {
        continue;
      }
      addFallbackAdvancement("dungeon_streak_" + dungeonId + "_" + streak, "dungeons", 1,
          title + " Streak " + streak, "Complete " + streak + " dungeon runs in a row.");
    }
  }

  private void registerYamlFallbackAdvancements() {
    ConfigurationSection defs = rawConfig.getConfigurationSection("advancements.definitions");
    if (defs == null) {
      return;
    }
    ConfigurationSection categories = defs.getConfigurationSection("categories");
    if (categories == null) {
      return;
    }
    for (String categoryId : categories.getKeys(false)) {
      ConfigurationSection categorySection = categories.getConfigurationSection(categoryId);
      if (categorySection == null) {
        continue;
      }
      ConfigurationSection nodes = categorySection.getConfigurationSection("nodes");
      if (nodes == null) {
        continue;
      }
      for (String nodeId : nodes.getKeys(false)) {
        ConfigurationSection nodeSection = nodes.getConfigurationSection(nodeId);
        if (nodeSection == null) {
          continue;
        }
        String resolvedNodeId = migrateAdvancementId(nodeId);
        String title = resolveText(nodeSection, "titleKey", "title", resolvedNodeId);
        String description = resolveText(nodeSection, "descriptionKey", "description", "");
        ConfigurationSection criteriaSection = nodeSection.getConfigurationSection("criteria");
        if (criteriaSection == null) {
          continue;
        }
        CriteriaType type = CriteriaType.fromString(criteriaSection.getString("type"));
        if (type == null) {
          continue;
        }
        String targetId = criteriaTarget(type, criteriaSection);
        if (type.requiresTarget() && (targetId == null || targetId.isBlank())) {
          continue;
        }
        int amount = Math.max(1, criteriaSection.getInt("amount", 1));
        List<String> requires = migrateRequirementIds(nodeSection.getStringList("requires"));
        int requiresAny = Math.max(0, nodeSection.getInt("requiresAny", 0));
        YamlRewards rewards = parseRewards(nodeSection.getConfigurationSection("rewards"));
        addFallbackAdvancement(resolvedNodeId, categoryId, amount, title, description);
        FallbackYamlNode node = new FallbackYamlNode(resolvedNodeId, categoryId,
            new YamlCriteria(type, targetId, amount), requires, requiresAny, rewards, title);
        fallbackYamlNodes.put(resolvedNodeId, node);
        indexFallbackNode(node);
      }
    }
  }

  private void indexFallbackNode(FallbackYamlNode node) {
    CriteriaType type = node.criteria().type();
    String target = node.criteria().targetId();
    if (target == null || target.isBlank()) {
      fallbackYamlWildcard.computeIfAbsent(type, key -> new ArrayList<>()).add(node);
      return;
    }
    fallbackYamlCriteria.computeIfAbsent(type, key -> new HashMap<>())
        .computeIfAbsent(target, key -> new ArrayList<>())
        .add(node);
  }

  private void addFallbackAdvancement(String id, String categoryId, int required, String title, String description) {
    if (id == null || id.isBlank()) {
      return;
    }
    fallbackAdvancements.put(id, new FallbackAdvancement(id, categoryId, Math.max(1, required), title, description));
  }

  private void fallbackIncrement(Player player, String id, String categoryId, String source, String detail,
      int amount, int total) {
    if (!useFallback() || player == null || id == null || id.isBlank()) {
      return;
    }
    FallbackAdvancement definition = fallbackAdvancements.get(id);
    if (definition == null) {
      return;
    }
    if (!isWorldAllowed(player, categoryId)) {
      return;
    }
    initFallbackStore();
    if (fallbackStore == null) {
      return;
    }
    AdvancementFallbackStore.ProgressUpdate update = fallbackStore.addProgress(player.getUniqueId(), id, amount);
    fallbackStore.saveAsync();
    auditProgress(player, id, categoryId, source, detail, amount, total);
    if (update.previous() < definition.required() && update.current() >= definition.required()) {
      sendFallbackGrant(player, definition);
      auditGrant(player, id, categoryId, source, detail, amount, total);
    }
  }

  private void progressYamlFallback(CriteriaType type, String targetId, Player player, int amount, int total) {
    if (!useFallback() || player == null) {
      return;
    }
    List<FallbackYamlNode> candidates = new ArrayList<>();
    List<FallbackYamlNode> wildcard = fallbackYamlWildcard.get(type);
    if (wildcard != null) {
      candidates.addAll(wildcard);
    }
    if (targetId != null) {
      Map<String, List<FallbackYamlNode>> byTarget = fallbackYamlCriteria.get(type);
      if (byTarget != null) {
        List<FallbackYamlNode> specific = byTarget.get(targetId);
        if (specific != null) {
          candidates.addAll(specific);
        }
      }
    }
    if (candidates.isEmpty()) {
      return;
    }
    for (FallbackYamlNode node : candidates) {
      if (!fallbackRequirementsMet(node, player)) {
        continue;
      }
      int required = node.criteria().amount();
      int increments = Math.max(1, amount);
      if (type == CriteriaType.XP_TOTAL && total < required) {
        continue;
      }
      initFallbackStore();
      if (fallbackStore == null) {
        return;
      }
      int previous = fallbackStore.getProgress(player.getUniqueId(), node.id());
      if (previous >= required) {
        continue;
      }
      fallbackIncrement(player, node.id(), node.categoryId(), type.name().toLowerCase(Locale.ROOT), targetId,
          increments, total);
      int current = fallbackStore.getProgress(player.getUniqueId(), node.id());
      if (previous < required && current >= required) {
        grantYamlRewards(node.rewards(), player);
      }
    }
  }

  private boolean fallbackRequirementsMet(FallbackYamlNode node, Player player) {
    if (node.requires().isEmpty()) {
      return true;
    }
    initFallbackStore();
    if (fallbackStore == null) {
      return false;
    }
    int complete = 0;
    for (String requirement : node.requires()) {
      FallbackAdvancement requirementDef = fallbackAdvancements.get(requirement);
      if (requirementDef == null) {
        continue;
      }
      int progress = fallbackStore.getProgress(player.getUniqueId(), requirement);
      if (progress >= requirementDef.required()) {
        complete++;
      }
    }
    if (node.requiresAny() > 0) {
      return complete >= node.requiresAny();
    }
    return complete >= node.requires().size();
  }

  private void sendFallbackGrant(Player player, FallbackAdvancement advancement) {
    if (player == null || advancement == null) {
      return;
    }
    LocaleService service = Locales.service();
    String title = advancement.title() == null ? advancement.id() : advancement.title();
    if (service != null) {
      Map<String, String> placeholders = Map.of("title", title);
      player.sendMessage(service.component(player, "messages.advancements.fallbackGranted", placeholders));
    } else {
      player.sendMessage("Advancement completed: " + title);
    }
  }
  private String migrateAdvancementId(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    String key = AdvancementIds.key(raw);
    String migrated = idMigrations.get(key);
    return migrated == null ? key : migrated;
  }

  private List<String> migrateRequirementIds(List<String> requirements) {
    if (requirements == null || requirements.isEmpty()) {
      return List.of();
    }
    List<String> migrated = new ArrayList<>(requirements.size());
    for (String requirement : requirements) {
      if (requirement == null || requirement.isBlank()) {
        continue;
      }
      migrated.add(migrateAdvancementId(requirement));
    }
    return migrated;
  }

  private void registerLookup(String id, BaseAdvancement advancement) {
    if (advancement == null || id == null || id.isBlank()) {
      return;
    }
    String key = AdvancementIds.key(id);
    advancementLookup.put(key, advancement);
    advancementReverseLookup.put(advancement, key);
  }

  private String lookupId(BaseAdvancement advancement) {
    if (advancement == null) {
      return null;
    }
    return advancementReverseLookup.get(advancement);
  }

  private BaseAdvancement lookupAdvancement(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    return advancementLookup.get(AdvancementIds.key(id));
  }

  public int replayAuditLog(boolean dryRun) {
    if (auditLog == null) {
      return 0;
    }
    int applied = 0;
    for (AdvancementAuditLog.AuditEvent event : auditLog.loadEvents()) {
      if (event == null) {
        continue;
      }
      BaseAdvancement advancement = lookupAdvancement(event.advancementId());
      if (advancement == null) {
        continue;
      }
      UUID playerId = event.playerId();
      if (playerId == null) {
        continue;
      }
      Player player = Bukkit.getPlayer(playerId);
      if (player == null) {
        continue;
      }
      if (dryRun) {
        applied++;
        continue;
      }
      int increments = Math.max(1, event.amount());
      for (int i = 0; i < increments && !advancement.isGranted(player); i++) {
        advancement.incrementProgression(player);
      }
      applied++;
    }
    return applied;
  }

  private String criteriaTarget(CriteriaType type, ConfigurationSection criteriaSection) {
    return switch (type) {
      case MOB_KILL, BOSS_KILL -> criteriaSection.getString("mob");
      case DUNGEON_COMPLETE -> criteriaSection.getString("dungeon");
      case SHOP_TRADE -> criteriaSection.getString("shop");
      case CRAFT -> criteriaSection.getString("recipe");
      case KIT -> criteriaSection.getString("kit");
      default -> null;
    };
  }

  private Material parseMaterial(String raw, Material fallback, String id) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      warn("Invalid material '" + raw + "' for '" + id + "', using " + fallback);
      return fallback;
    }
  }

  private AdvancementFrameType parseFrame(String raw, AdvancementFrameType fallback, String id) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return AdvancementFrameType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      warn("Invalid frame '" + raw + "' for '" + id + "', using " + fallback);
      return fallback;
    }
  }

  private String resolveText(ConfigurationSection section, String keyField, String valueField, String fallback) {
    if (section == null) {
      return fallback;
    }
    String key = section.getString(keyField);
    if (key != null && !key.isBlank()) {
      return localeText(key, fallback);
    }
    String raw = section.getString(valueField);
    if (raw == null) {
      return fallback;
    }
    if (raw.startsWith("key:")) {
      String stripped = raw.substring("key:".length()).trim();
      if (!stripped.isEmpty()) {
        return localeText(stripped, fallback);
      }
    }
    return raw;
  }

  private String localeText(String key, String fallback) {
    if (key == null || key.isBlank()) {
      return fallback;
    }
    LocaleService service = Locales.service();
    if (service == null) {
      return fallback == null ? key : fallback;
    }
    String resolved = service.text(service.defaultLocale(), key, Map.of());
    if (resolved == null || resolved.equals(key)) {
      return fallback == null ? key : fallback;
    }
    return resolved;
  }

  private void warn(String message) {
    plugin.getLogger().warning("[Advancements] " + message);
  }

  private String tabNamespace(String categoryId) {
    if (categoryId == null || categoryId.isBlank()) {
      return TAB_NAMESPACE + "_custom";
    }
    String normalized = categoryId.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
    return TAB_NAMESPACE + "_" + normalized;
  }

  private void loadWorldRules() {
    worldAllow = List.of();
    worldDeny = List.of();
    worldAllowByCategory.clear();
    worldDenyByCategory.clear();
    if (plugin == null || plugin.getConfig() == null) {
      return;
    }
    ConfigurationSection adv = plugin.getConfig().getConfigurationSection("advancements");
    if (adv == null) {
      return;
    }
    ConfigurationSection worlds = adv.getConfigurationSection("worlds");
    if (worlds != null) {
      worldAllow = normalizeWorldList(worlds.getStringList("allow"));
      worldDeny = normalizeWorldList(worlds.getStringList("deny"));
      ConfigurationSection categories = worlds.getConfigurationSection("categories");
      if (categories != null) {
        for (String categoryId : categories.getKeys(false)) {
          ConfigurationSection section = categories.getConfigurationSection(categoryId);
          if (section == null) {
            continue;
          }
          worldAllowByCategory.put(categoryId, normalizeWorldList(section.getStringList("allow")));
          worldDenyByCategory.put(categoryId, normalizeWorldList(section.getStringList("deny")));
        }
      }
    }
  }

  private boolean isWorldAllowed(String worldName, String categoryId) {
    if (worldName == null || worldName.isBlank()) {
      return false;
    }
    String normalized = worldName.toLowerCase(Locale.ROOT);
    if (categoryId != null) {
      List<String> allow = worldAllowByCategory.get(categoryId);
      if (allow != null && !allow.isEmpty() && !allow.contains(normalized)) {
        return false;
      }
      List<String> deny = worldDenyByCategory.get(categoryId);
      if (deny != null && deny.contains(normalized)) {
        return false;
      }
    }
    if (!worldAllow.isEmpty() && !worldAllow.contains(normalized)) {
      return false;
    }
    if (worldDeny.contains(normalized)) {
      return false;
    }
    return true;
  }

  private List<String> normalizeWorldList(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<String> normalized = new ArrayList<>();
    for (String entry : raw) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      normalized.add(entry.trim().toLowerCase(Locale.ROOT));
    }
    return List.copyOf(normalized);
  }

  private enum CriteriaType {
    MOB_KILL(true),
    BOSS_KILL(true),
    XP_LEVEL(false),
    XP_TOTAL(false),
    TOKENS(false),
    DUNGEON_COMPLETE(true),
    SHOP_TRADE(true),
    CRAFT(true),
    KIT(true);

    private final boolean requiresTarget;

    CriteriaType(boolean requiresTarget) {
      this.requiresTarget = requiresTarget;
    }

    boolean requiresTarget() {
      return requiresTarget;
    }

    static CriteriaType fromString(String raw) {
      if (raw == null || raw.isBlank()) {
        return null;
      }
      try {
        return CriteriaType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
  }

  private record YamlCriteria(CriteriaType type, String targetId, int amount) {
  }

  private record YamlRewards(int tokens, int xp, int skillPoints, List<String> items, List<String> commands) {
    static YamlRewards empty() {
      return new YamlRewards(0, 0, 0, List.of(), List.of());
    }
  }

  private record YamlAdvancementNode(String id, String categoryId, BaseAdvancement advancement, YamlCriteria criteria,
      List<String> requires, int requiresAny, YamlRewards rewards) {
    boolean requirementsMet(Player player, Map<String, YamlAdvancementNode> nodes) {
      if (requires == null || requires.isEmpty()) {
        return true;
      }
      int granted = 0;
      for (String requirement : requires) {
        YamlAdvancementNode node = nodes.get(requirement);
        if (node != null && node.advancement().isGranted(player)) {
          granted++;
        }
      }
      if (requiresAny > 0) {
        return granted >= requiresAny;
      }
      return granted == requires.size();
    }
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
