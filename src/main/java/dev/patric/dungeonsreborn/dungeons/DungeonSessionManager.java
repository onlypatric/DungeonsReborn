package dev.patric.dungeonsreborn.dungeons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

public final class DungeonSessionManager {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  public enum State {
    RUNNING,
    COMPLETE,
    FAILED
  }

  public record DungeonSession(DungeonQueueService.QueueEntry entry, String dungeonId, int level,
      long startedAt, State state) {
  }

  public record SessionStatus(boolean active, int level, State state, boolean bossPhase, int wave, int totalWaves,
      long startedAt, long timeLimitMillis, long waitUntilMillis, Component dungeonName, List<String> affixes) {
  }

  private static final class SessionState {
    private final DungeonSpec dungeon;
    private final DungeonSpec.DungeonLevel level;
    private final World world;
    private final Map<String, Location> spawnPoints;
    private final List<Location> spawnPointList = new ArrayList<>();
    private final Set<java.util.UUID> aliveMobs = new HashSet<>();
    private final Random random = new Random();
    private final long timeLimitMillis;
    private final boolean checkpointEnabled;
    private final boolean checkpointOnWave;
    private Location checkpointLocation;
    private final DungeonSpec.DungeonModifiers modifiers;
    private int nextWaveIndex;
    private boolean bossPhase;
    private long waitUntilMs;
    private boolean hadDeath;

    private SessionState(DungeonSpec dungeon, DungeonSpec.DungeonLevel level, World world) {
      this.dungeon = dungeon;
      this.level = level;
      this.world = world;
      this.timeLimitMillis = Math.max(0L, level.timeLimitSeconds() * 1000L);
      DungeonSpec.DungeonCheckpoint checkpoint = level.checkpoint();
      this.checkpointEnabled = checkpoint != null && checkpoint.enabled();
      this.checkpointOnWave = checkpoint != null && checkpoint.onWave();
      this.modifiers = level.modifiers();
      this.spawnPoints = new HashMap<>();
      for (DungeonSpec.DungeonSpawnPoint point : level.spawnPoints()) {
        Location loc = toLocation(world, point.pos());
        spawnPoints.put(point.id(), loc);
        spawnPointList.add(loc);
      }
      if (checkpointEnabled) {
        if (checkpoint != null && checkpoint.location() != null) {
          checkpointLocation = toLocation(world, checkpoint.location());
        } else if (dungeon.entry() != null && dungeon.entry().spawn() != null) {
          checkpointLocation = toLocation(world, dungeon.entry().spawn());
        }
      }
    }

    private static Location toLocation(World world, DungeonSpec.DungeonPoint point) {
      return new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5);
    }

    private Location resolveSpawn(String mobId) {
      if (mobId != null) {
        String override = level.spawnOverrides().get(mobId);
        if (override != null) {
          Location loc = spawnPoints.get(override);
          if (loc != null) {
            return jitter(loc);
          }
        }
      }
      if (!spawnPointList.isEmpty()) {
        Location loc = spawnPointList.get(random.nextInt(spawnPointList.size()));
        return jitter(loc);
      }
      if (dungeon.entry() != null && dungeon.entry().spawn() != null) {
        return jitter(toLocation(world, dungeon.entry().spawn()));
      }
      DungeonSpec.DungeonRegion region = dungeon.region();
      if (region != null) {
        int x = (region.min().x() + region.max().x()) / 2;
        int y = (region.min().y() + region.max().y()) / 2;
        int z = (region.min().z() + region.max().z()) / 2;
        return new Location(world, x + 0.5, y, z + 0.5);
      }
      return new Location(world, world.getSpawnLocation().getX(), world.getSpawnLocation().getY(),
          world.getSpawnLocation().getZ());
    }

    private Location jitter(Location base) {
      double dx = (random.nextDouble() - 0.5) * 1.5;
      double dz = (random.nextDouble() - 0.5) * 1.5;
      return base.clone().add(dx, 0.0, dz);
    }

    private void cleanupAlive() {
      aliveMobs.removeIf(uuid -> {
        var entity = Bukkit.getEntity(uuid);
        return !(entity instanceof LivingEntity living) || living.isDead();
      });
    }

    private void updateCheckpoint(Location location) {
      if (!checkpointEnabled || location == null) {
        return;
      }
      checkpointLocation = location.clone();
    }
  }

  private final Plugin plugin;
  private final DungeonYamlRegistry registry;
  private final DungeonProgressRepository progress;
  private final DungeonQueueService queue;
  private final ServiceLogger logger;
  private boolean debugWaveLogs;
  private final MobRegistry mobs;
  private final ShopYamlRegistry shops;
  private final ProgressionService progression;
  private final AdvancementService advancements;
  private DungeonSession active;
  private SessionState state;
  private BukkitTask task;

  public DungeonSessionManager(Plugin plugin, DungeonYamlRegistry registry, DungeonProgressRepository progress,
      DungeonQueueService queue, MobRegistry mobs, ShopYamlRegistry shops, ProgressionService progression,
      AdvancementService advancements, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.progress = progress;
    this.queue = Objects.requireNonNull(queue, "queue");
    this.mobs = Objects.requireNonNull(mobs, "mobs");
    this.shops = shops;
    this.progression = progression;
    this.advancements = advancements;
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void setDebugWaveLogs(boolean debugWaveLogs) {
    this.debugWaveLogs = debugWaveLogs;
  }

  public synchronized boolean isActive() {
    return active != null;
  }

  public synchronized SessionStatus status() {
    if (active == null || state == null) {
      return new SessionStatus(false, 0, State.FAILED, false, 0, 0, 0L, 0L, 0L, null, List.of());
    }
    Component dungeonName = state.dungeon.name() != null
        ? state.dungeon.name()
        : Component.text(active.dungeonId());
    int totalWaves = Math.max(0, state.level.waves().size());
    int wave = state.bossPhase ? totalWaves : Math.min(Math.max(0, state.nextWaveIndex), Math.max(1, totalWaves));
    List<String> affixes = state.modifiers == null ? List.of() : state.modifiers.affixes();
    return new SessionStatus(true, active.level(), active.state(), state.bossPhase, wave, totalWaves,
        active.startedAt(), state.timeLimitMillis, state.waitUntilMs, dungeonName, affixes);
  }

  public synchronized boolean start(DungeonQueueService.QueueEntry entry) {
    if (entry == null || active != null) {
      return false;
    }
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null || !dungeon.levels().containsKey(entry.level())) {
      return false;
    }
    DungeonSpec.DungeonLevel levelSpec = dungeon.levels().get(entry.level());
    World world = Bukkit.getWorld(dungeon.world());
    if (levelSpec == null || world == null) {
      return false;
    }
    active = new DungeonSession(entry, dungeon.id(), entry.level(), System.currentTimeMillis(), State.RUNNING);
    state = new SessionState(dungeon, levelSpec, world);
    logger.info("[Dungeon] Session started level " + entry.level() + " for " + entry.playerName());
    Player player = Bukkit.getPlayer(entry.playerId());
    if (player != null) {
      player.sendMessage(Locales.component(player, "messages.dungeons.session.start"));
      teleportToEntry(player, dungeon, world);
    }
    spawnNextWave();
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    return true;
  }

  public synchronized boolean debugEnd(boolean success) {
    if (active == null) {
      return false;
    }
    finish(active.entry(), success);
    return true;
  }

  public synchronized boolean debugSkipWave() {
    if (active == null || state == null) {
      return false;
    }
    cleanupMobs(state);
    state.aliveMobs.clear();
    if (state.bossPhase) {
      finish(active.entry(), true);
      return true;
    }
    if (state.nextWaveIndex < state.level.waves().size()) {
      spawnNextWave();
      return true;
    }
    if (spawnBossPhase()) {
      return true;
    }
    finish(active.entry(), true);
    return true;
  }

  public synchronized boolean abortActive(Component reason) {
    if (active == null) {
      return false;
    }
    Player player = Bukkit.getPlayer(active.entry().playerId());
    if (player != null && reason != null && !reason.equals(Component.empty())) {
      player.sendMessage(reason);
    }
    finish(active.entry(), false);
    return true;
  }

  private void tick() {
    DungeonSession session;
    SessionState sessionState;
    synchronized (this) {
      session = active;
      sessionState = state;
    }
    if (session == null || sessionState == null) {
      cancelTask();
      return;
    }
    Player player = Bukkit.getPlayer(session.entry().playerId());
    if (player == null || !player.isOnline()) {
      fail(session.entry(), "messages.dungeons.session.fail.left");
      return;
    }
    if (!player.getWorld().getName().equalsIgnoreCase(sessionState.world.getName())) {
      fail(session.entry(), "messages.dungeons.session.fail.world");
      return;
    }
    sessionState.cleanupAlive();
    if (sessionState.bossPhase) {
      if (sessionState.aliveMobs.isEmpty()) {
        finish(session.entry(), true);
      }
      return;
    }
    if (sessionState.timeLimitMillis > 0L) {
      long elapsed = System.currentTimeMillis() - session.startedAt();
      if (elapsed > sessionState.timeLimitMillis) {
        fail(session.entry(), "messages.dungeons.session.fail.time");
        return;
      }
    }
    if (!sessionState.aliveMobs.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (sessionState.waitUntilMs == 0 && sessionState.level.waitSeconds() > 0) {
      sessionState.waitUntilMs = now + (sessionState.level.waitSeconds() * 1000L);
      return;
    }
    if (sessionState.waitUntilMs > 0 && now < sessionState.waitUntilMs) {
      return;
    }
    sessionState.waitUntilMs = 0;
    if (sessionState.nextWaveIndex < sessionState.level.waves().size()) {
      spawnNextWave();
      return;
    }
    if (spawnBossPhase()) {
      return;
    }
    finish(session.entry(), true);
  }

  private void spawnNextWave() {
    SessionState sessionState;
    synchronized (this) {
      sessionState = state;
    }
    if (sessionState == null) {
      return;
    }
    if (sessionState.nextWaveIndex >= sessionState.level.waves().size()) {
      return;
    }
    DungeonSpec.DungeonWave wave = sessionState.level.waves().get(sessionState.nextWaveIndex);
    int waveNumber = sessionState.nextWaveIndex + 1;
    int totalWaves = Math.max(0, sessionState.level.waves().size());
    sessionState.nextWaveIndex++;
    if (debugWaveLogs) {
      logger.debug("[Dungeon] wave " + waveNumber + "/" + totalWaves + " mobs=" + wave.mobs());
    }
    spawnWave(sessionState, wave);
    Player player = Bukkit.getPlayer(active.entry().playerId());
    notifyWaveStart(sessionState, player, waveNumber, totalWaves);
    if (sessionState.checkpointEnabled && sessionState.checkpointOnWave && player != null) {
      sessionState.updateCheckpoint(player.getLocation());
    }
    sessionState.waitUntilMs = 0L;
  }

  private void spawnWave(SessionState sessionState, DungeonSpec.DungeonWave wave) {
    for (String mobId : wave.mobs()) {
      Location spawn = sessionState.resolveSpawn(mobId);
      try {
        LivingEntity entity = mobs.spawn(mobId, spawn);
        applyModifiers(sessionState, entity);
        sessionState.aliveMobs.add(entity.getUniqueId());
      } catch (IllegalArgumentException ex) {
        logger.warn("[Dungeon] Failed to spawn mob " + mobId + ": " + ex.getMessage());
      }
    }
  }

  private boolean spawnBossPhase() {
    SessionState sessionState;
    synchronized (this) {
      sessionState = state;
    }
    if (sessionState == null) {
      return false;
    }
    DungeonSpec.DungeonWave bossWave = sessionState.level.bossWave();
    String bossMob = sessionState.level.bossMob();
    if (bossWave == null && (bossMob == null || bossMob.isBlank())) {
      return false;
    }
    if (debugWaveLogs) {
      logger.debug("[Dungeon] boss phase mobs=" + (bossWave == null ? List.of() : bossWave.mobs())
          + " boss=" + (bossMob == null ? "" : bossMob));
    }
    if (bossWave != null) {
      spawnWave(sessionState, bossWave);
    }
    if (bossMob != null && !bossMob.isBlank()) {
      Location spawn = sessionState.resolveSpawn(bossMob);
      try {
        LivingEntity entity = mobs.spawn(bossMob, spawn);
        applyModifiers(sessionState, entity);
        sessionState.aliveMobs.add(entity.getUniqueId());
      } catch (IllegalArgumentException ex) {
        logger.warn("[Dungeon] Failed to spawn boss " + bossMob + ": " + ex.getMessage());
      }
    }
    sessionState.bossPhase = true;
    Player player = Bukkit.getPlayer(active.entry().playerId());
    notifyBossSpawn(sessionState, player);
    return true;
  }

  private void finish(DungeonQueueService.QueueEntry entry, boolean success) {
    DungeonSession session;
    SessionState sessionState;
    synchronized (this) {
      if (active == null || entry == null || !active.entry().playerId().equals(entry.playerId())) {
        return;
      }
      session = active;
      sessionState = state;
      active = null;
      state = null;
    }
    cancelTask();
    cleanupMobs(sessionState);
    Player player = Bukkit.getPlayer(entry.playerId());
    if (player != null && sessionState != null && sessionState.dungeon.entry() != null) {
      teleportToExit(player, sessionState.dungeon, sessionState.world);
    }
    if (success && progress != null) {
      progress.recordCompletion(entry.playerId(), session.dungeonId(), entry.level());
    }
    if (success && player != null && sessionState != null && advancements != null) {
      long duration = System.currentTimeMillis() - session.startedAt();
      advancements.recordDungeonCompletion(sessionState.dungeon, entry.level(), player,
          sessionState.hadDeath, duration, sessionState.level.timeLimitSeconds());
    }
    if (success && player != null) {
      applyRewards(sessionState, player);
    }
    if (player != null) {
      notifyRunEnd(session, sessionState, player, success);
    }
    logger.info("[Dungeon] Session ended level " + entry.level() + " success=" + success);
    queue.onSessionFinished(entry);
  }

  private void fail(DungeonQueueService.QueueEntry entry, String key) {
    if (entry == null) {
      return;
    }
    Player player = Bukkit.getPlayer(entry.playerId());
    if (player != null && key != null && !key.isBlank()) {
      player.sendMessage(Locales.component(player, key));
    }
    finish(entry, false);
  }

  private void cleanupMobs(SessionState sessionState) {
    if (sessionState == null) {
      return;
    }
    for (var uuid : sessionState.aliveMobs) {
      var entity = Bukkit.getEntity(uuid);
      if (entity instanceof LivingEntity living) {
        living.remove();
      }
    }
    sessionState.aliveMobs.clear();
  }

  private void applyModifiers(SessionState sessionState, LivingEntity entity) {
    if (sessionState == null || entity == null) {
      return;
    }
    DungeonSpec.DungeonModifiers modifiers = sessionState.modifiers;
    if (modifiers == null) {
      return;
    }
    double healthMultiplier = modifiers.healthMultiplier();
    if (healthMultiplier > 0.0 && healthMultiplier != 1.0) {
      AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
      if (maxHealth != null) {
        double base = maxHealth.getBaseValue();
        double next = Math.max(1.0, base * healthMultiplier);
        maxHealth.setBaseValue(next);
        if (entity.getHealth() > next) {
          entity.setHealth(next);
        }
      }
    }
    double damageMultiplier = modifiers.damageMultiplier();
    if (damageMultiplier > 0.0 && damageMultiplier != 1.0) {
      AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
      if (damage != null) {
        double base = damage.getBaseValue();
        damage.setBaseValue(Math.max(0.0, base * damageMultiplier));
      }
    }
  }

  private void cancelTask() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  private void teleportToEntry(Player player, DungeonSpec dungeon, World world) {
    if (dungeon.entry() == null || dungeon.entry().spawn() == null) {
      return;
    }
    DungeonSpec.DungeonPoint spawn = dungeon.entry().spawn();
    player.teleport(new Location(world, spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5));
  }

  private void teleportToExit(Player player, DungeonSpec dungeon, World world) {
    if (dungeon.entry() == null || dungeon.entry().exit() == null) {
      return;
    }
    DungeonSpec.DungeonPoint exit = dungeon.entry().exit();
    player.teleport(new Location(world, exit.x() + 0.5, exit.y(), exit.z() + 0.5));
  }

  public synchronized Location checkpointLocation(Player player) {
    if (player == null || active == null || state == null) {
      return null;
    }
    if (!active.entry().playerId().equals(player.getUniqueId())) {
      return null;
    }
    if (!state.checkpointEnabled || state.checkpointLocation == null) {
      return null;
    }
    return state.checkpointLocation.clone();
  }

  public synchronized boolean isCheckpointEnabled(Player player) {
    if (player == null || active == null || state == null) {
      return false;
    }
    if (!active.entry().playerId().equals(player.getUniqueId())) {
      return false;
    }
    return state.checkpointEnabled;
  }

  public synchronized void markDeath(Player player) {
    if (player == null || active == null || state == null) {
      return;
    }
    if (!active.entry().playerId().equals(player.getUniqueId())) {
      return;
    }
    state.hadDeath = true;
  }

  private void applyRewards(SessionState sessionState, Player player) {
    if (sessionState == null || player == null) {
      return;
    }
    DungeonSpec.DungeonReward rewards = sessionState.level.rewards();
    if (rewards == null) {
      return;
    }
    int tokens = rollRange(rewards.tokens(), sessionState.random);
    if (tokens > 0) {
      giveTokenBundle(player, tokens);
      if (advancements != null) {
        advancements.recordTokensEarned(player, tokens);
      }
    }
    if (rewards.skillPoints() > 0 && progression != null) {
      progression.awardSkillPoints(player, rewards.skillPoints());
    }
    int extraCount = 0;
    List<String> extraIds = new ArrayList<>();
    for (DungeonSpec.DungeonExtraLoot extra : rewards.extraLoot()) {
      if (extra == null) {
        continue;
      }
      int chance = Math.max(0, extra.chancePercent());
      if (chance <= 0) {
        continue;
      }
      if (sessionState.random.nextInt(100) >= chance) {
        continue;
      }
      ItemStack item = resolveExtraLoot(extra);
      if (item == null || item.getType().isAir()) {
        continue;
      }
      extraCount++;
      if (extra.itemId() != null && !extra.itemId().isBlank()) {
        extraIds.add(extra.itemId());
      }
      giveItemOrDrop(player, item);
    }
    if (tokens > 0 || rewards.skillPoints() > 0 || extraCount > 0) {
      player.sendMessage(Locales.component(player, "messages.dungeons.rewards.header"));
      if (tokens > 0) {
        player.sendMessage(Locales.component(player, "messages.dungeons.rewards.tokens",
            Locales.placeholders("tokens", tokens)));
      }
      if (rewards.skillPoints() > 0) {
        player.sendMessage(Locales.component(player, "messages.dungeons.rewards.skillPoints",
            Locales.placeholders("skillPoints", rewards.skillPoints())));
      }
      if (extraCount > 0) {
        String detail = extraIds.isEmpty() ? String.valueOf(extraCount) : extraCount + " (" + String.join(", ", extraIds) + ")";
        player.sendMessage(Locales.component(player, "messages.dungeons.rewards.extraLoot",
            Locales.placeholders("detail", detail)));
      }
    }
    logger.info("[Dungeon] Rewards for " + player.getName() + ": tokens=" + tokens
        + ", skillPoints=" + rewards.skillPoints() + ", extraLoot=" + extraCount);
  }

  private int rollRange(DungeonSpec.IntRange range, Random random) {
    if (range == null || random == null) {
      return 0;
    }
    int min = Math.max(0, range.min());
    int max = Math.max(min, range.max());
    if (max == min) {
      return min;
    }
    return min + random.nextInt(max - min + 1);
  }

  private ItemStack resolveExtraLoot(DungeonSpec.DungeonExtraLoot extra) {
    if (extra == null || extra.itemId() == null || extra.itemId().isBlank()) {
      return null;
    }
    if (shops == null || shops.itemResolver() == null) {
      return null;
    }
    ItemStack resolved = shops.itemResolver().apply(extra.itemId());
    return resolved == null ? null : resolved.clone();
  }

  private void giveTokenBundle(Player player, int amount) {
    if (player == null || amount <= 0 || shops == null) {
      return;
    }
    ItemStack palletItem = shops.resolveTokenItem("pallet");
    ItemStack compressedItem = shops.resolveTokenItem("compressed");
    ItemStack normalItem = shops.resolveTokenItem("token");
    int remaining = amount;
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
    var leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItem(player.getLocation(), stack);
      }
    }
  }

  private void notifyWaveStart(SessionState sessionState, Player player, int waveNumber, int totalWaves) {
    if (player == null) {
      return;
    }
    Component title = Locales.component(player, "messages.dungeons.titles.wave.title",
        Locales.placeholders("wave", waveNumber));
    Component subtitle = totalWaves > 0
        ? Locales.component(player, "messages.dungeons.titles.wave.subtitle",
            Locales.placeholders("wave", waveNumber, "total", totalWaves))
        : Locales.component(player, "messages.dungeons.titles.wave.subtitlePrepare");
    player.showTitle(Title.title(title, subtitle, titleTimes()));
    String affixes = formatAffixes(sessionState);
    String affixSuffix = affixes.isBlank() ? "" : " <dark_gray>[" + affixes + "]</dark_gray>";
    player.sendActionBar(Locales.component(player, "messages.dungeons.action.wave",
        Locales.placeholders("wave", waveNumber, "affixSuffix", affixSuffix)));
  }

  private void notifyBossSpawn(SessionState sessionState, Player player) {
    if (player == null) {
      return;
    }
    Component title = Locales.component(player, "messages.dungeons.titles.boss.title");
    Component subtitle = Locales.component(player, "messages.dungeons.titles.boss.subtitle");
    player.showTitle(Title.title(title, subtitle, titleTimes()));
    String affixes = formatAffixes(sessionState);
    String affixSuffix = affixes.isBlank() ? "" : " <dark_gray>[" + affixes + "]</dark_gray>";
    player.sendActionBar(Locales.component(player, "messages.dungeons.action.boss",
        Locales.placeholders("affixSuffix", affixSuffix)));
  }

  private void notifyRunEnd(DungeonSession session, SessionState sessionState, Player player, boolean success) {
    if (player == null) {
      return;
    }
    String result = Locales.text(player, success
        ? "messages.dungeons.result.victory"
        : "messages.dungeons.result.defeat");
    String dungeonName = sessionState != null && sessionState.dungeon.name() != null
        ? PLAIN.serialize(sessionState.dungeon.name())
        : session.dungeonId();
    Component title = Locales.component(player, success
        ? "messages.dungeons.titles.end.win"
        : "messages.dungeons.titles.end.lose");
    Component subtitle = Locales.component(player, "messages.dungeons.titles.end.subtitle",
        Locales.placeholders("result", result, "dungeon", dungeonName));
    player.showTitle(Title.title(title, subtitle, titleTimes()));
    broadcastSummary(session, sessionState, success, player.getName());
  }

  private void broadcastSummary(DungeonSession session, SessionState sessionState, boolean success, String playerName) {
    String result = Locales.text(null, success
        ? "messages.dungeons.result.victory"
        : "messages.dungeons.result.defeat");
    String dungeonName = sessionState != null && sessionState.dungeon.name() != null
        ? PLAIN.serialize(sessionState.dungeon.name())
        : session.dungeonId();
    long durationSeconds = Math.max(0L, (System.currentTimeMillis() - session.startedAt()) / 1000L);
    Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.separator"));
    Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.header",
        Locales.placeholders("dungeon", dungeonName)));
    Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.result",
        Locales.placeholders("result", result)));
    Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.level",
        Locales.placeholders("level", session.level())));
    Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.duration",
        Locales.placeholders("seconds", durationSeconds)));
    Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.players",
        Locales.placeholders("players", playerName)));
    String affixes = formatAffixes(sessionState);
    if (!affixes.isBlank()) {
      Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.affixes",
          Locales.placeholders("affixes", affixes)));
    }
    Bukkit.broadcast(Locales.component(null, "messages.dungeons.broadcast.separator"));
  }

  private String formatAffixes(SessionState sessionState) {
    if (sessionState == null || sessionState.modifiers == null || sessionState.modifiers.affixes().isEmpty()) {
      return "";
    }
    return String.join(", ", sessionState.modifiers.affixes());
  }

  private static Title.Times titleTimes() {
    return Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500));
  }
}
