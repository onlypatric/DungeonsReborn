package dev.patric.dungeonsreborn.advancements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
  private final Map<Integer, BaseAdvancement> xpLevelAdvancements = new HashMap<>();
  private final Map<Integer, BaseAdvancement> xpTotalAdvancements = new HashMap<>();
  private final Map<Integer, BaseAdvancement> tokenMilestones = new HashMap<>();
  private AdvancementTab tab;
  private RootAdvancement root;
  private boolean enabled;
  private ShopYamlRegistry shopRegistry;
  private ProgressionService progressionService;

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
      UltimateAdvancementAPI api = UltimateAdvancementAPI.getInstance(plugin);
      if (api == null) {
        plugin.getLogger().warning("[Advancements] UltimateAdvancementAPI instance unavailable, skipping.");
        return false;
      }
      tab = api.createAdvancementTab("dungeonsreborn");
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
      tab.registerAdvancements(root);
      tab.getEventManager().register(tab, PlayerLoadingCompletedEvent.class, event -> {
        tab.showTab(event.getPlayer());
      });
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
    tab = null;
    root = null;
    bossFirstKill.clear();
    bossKillThresholds.clear();
    dungeonLevelAdvancements.clear();
    dungeonCompletionThresholds.clear();
    dungeonNoDeath.clear();
    dungeonTime.clear();
    xpLevelAdvancements.clear();
    xpTotalAdvancements.clear();
    tokenMilestones.clear();
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

  public void grantRoot(Player player) {
    if (!enabled || tab == null || root == null || player == null) {
      return;
    }
    tab.grantRootAdvancement(player);
  }

  public void registerBossAdvancements(MobRegistry mobRegistry) {
    if (!enabled || tab == null || root == null || mobRegistry == null) {
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
      tab.registerAdvancements(root, advancement);
      bossFirstKill.put(mobId, advancement);
      int row = y + 1;
      int[] thresholds = new int[] {5, 20};
      List<BossThreshold> thresholdList = new ArrayList<>();
      for (int threshold : thresholds) {
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
            root,
            threshold
        );
        tab.registerAdvancements(root, thresholdAdvancement);
        thresholdList.add(new BossThreshold(thresholdAdvancement, BOSS_THRESHOLD_TOKENS));
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

  public void registerXpAdvancements() {
    if (!enabled || tab == null || root == null) {
      return;
    }
    xpLevelAdvancements.clear();
    int[] levels = new int[] {10, 25, 50, 100};
    int x = 6;
    int y = 0;
    for (int level : levels) {
      AdvancementDisplay display = new AdvancementDisplay(
          Material.EXPERIENCE_BOTTLE,
          "Level " + level,
          AdvancementFrameType.TASK,
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
          1
      );
      tab.registerAdvancements(root, advancement);
      xpLevelAdvancements.put(level, advancement);
      y++;
    }
  }

  public void registerXpTotalAdvancements() {
    if (!enabled || tab == null || root == null) {
      return;
    }
    xpTotalAdvancements.clear();
    int[] totals = new int[] {1000, 5000, 10000, 25000, 50000};
    int x = 5;
    int y = 0;
    for (int total : totals) {
      AdvancementDisplay display = new AdvancementDisplay(
          Material.EXPERIENCE_BOTTLE,
          "Total XP " + total,
          AdvancementFrameType.TASK,
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
      tab.registerAdvancements(root, advancement);
      xpTotalAdvancements.put(total, advancement);
      y++;
    }
  }

  public void registerTokenAdvancements() {
    if (!enabled || tab == null || root == null) {
      return;
    }
    tokenMilestones.clear();
    int[] thresholds = new int[] {100, 1000, 10000};
    int x = 7;
    int y = 0;
    for (int threshold : thresholds) {
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
      tab.registerAdvancements(root, advancement);
      tokenMilestones.put(threshold, advancement);
      y++;
    }
  }

  public void registerDungeonAdvancements(DungeonYamlRegistry registry) {
    if (!enabled || tab == null || root == null || registry == null) {
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
      tab.registerAdvancements(root, advancement);
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
      tab.registerAdvancements(root, noDeathAdv);
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
        tab.registerAdvancements(root, timeAdv);
        dungeonTime.put(dungeonLevelKey(dungeonId, level.level()), timeAdv);
      }
      specialRow++;
    }
    List<DungeonThreshold> thresholds = new ArrayList<>();
    int[] counts = new int[] {5, 20};
    int row = y + 1;
    for (int count : counts) {
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
      tab.registerAdvancements(root, advancement);
      thresholds.add(new DungeonThreshold(advancement, DUNGEON_THRESHOLD_TOKENS));
      row++;
    }
    dungeonCompletionThresholds.put(dungeonId, thresholds);
  }

  public void recordBossKill(MobSpec spec, String mobId, Player killer) {
    if (!enabled || mobId == null || killer == null || spec == null) {
      return;
    }
    BaseAdvancement advancement = bossFirstKill.get(mobId);
    if (advancement != null) {
      boolean wasGranted = advancement.isGranted(killer);
      advancement.incrementProgression(killer);
      if (!wasGranted && advancement.isGranted(killer)) {
        grantBossRewards(spec, killer, BOSS_FIRST_KILL_TOKENS);
      }
    }
    List<BossThreshold> thresholds = bossKillThresholds.get(mobId);
    if (thresholds != null) {
      for (BossThreshold threshold : thresholds) {
        boolean wasGranted = threshold.advancement.isGranted(killer);
        threshold.advancement.incrementProgression(killer);
        if (!wasGranted && threshold.advancement.isGranted(killer)) {
          grantBossRewards(spec, killer, threshold.tokenReward);
        }
      }
    }
  }

  public void recordDungeonCompletion(DungeonSpec dungeon, int level, Player player, boolean hadDeath,
      long durationMillis, int timeLimitSeconds) {
    if (!enabled || dungeon == null || player == null) {
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
          grantDungeonRewards(levelSpec, player, DUNGEON_LEVEL_TOKENS);
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
  }

  public void recordXpLevel(Player player, int level) {
    if (!enabled || player == null) {
      return;
    }
    for (Map.Entry<Integer, BaseAdvancement> entry : xpLevelAdvancements.entrySet()) {
      int threshold = entry.getKey();
      if (level < threshold) {
        continue;
      }
      BaseAdvancement advancement = entry.getValue();
      if (advancement == null || advancement.isGranted(player)) {
        continue;
      }
      advancement.incrementProgression(player);
    }
  }

  public void recordXpTotal(Player player, int totalXp) {
    if (!enabled || player == null || totalXp <= 0) {
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
    if (!enabled || player == null || amount <= 0) {
      return;
    }
    for (Map.Entry<Integer, BaseAdvancement> entry : tokenMilestones.entrySet()) {
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

  public void recordTokensFromItem(Player player, ItemStack item) {
    if (!enabled || player == null || item == null) {
      return;
    }
    int tokens = tokenValue(item);
    if (tokens > 0) {
      recordTokensEarned(player, tokens);
    }
  }

  private String bossTitle(MobSpec spec, String fallback) {
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

  private record BossThreshold(BaseAdvancement advancement, int tokenReward) {
  }

  private record DungeonThreshold(BaseAdvancement advancement, int tokenReward) {
  }
}
