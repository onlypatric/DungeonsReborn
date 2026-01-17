package dev.patric.dungeonsreborn.progression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.RenderType;

import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.classes.ClassBonusService;
import dev.patric.dungeonsreborn.system.SharedTickScheduler;
import dev.patric.dungeonsreborn.progression.custom.CustomXpProfile;
import dev.patric.dungeonsreborn.progression.custom.CustomXpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ProgressionHudService {
  private static final String OBJECTIVE_ID = "dr_hud";
  private static final long DEFAULT_UPDATE_TICKS = 40L;
  private static final int MAX_LINES = 15;

  private final JavaPlugin plugin;
  private final ProgressionService progressionService;
  private final CustomXpService customXpService;
  private final ProgressionStatService statService;
  private final EffectsEngine effectsEngine;
  private final Predicate<World> worldAllowed;
  private final ScoreboardManager scoreboardManager;
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();
  private Layout layout;
  private ClassBonusService classBonuses;
  private int taskId = -1;
  private SharedTickScheduler.Handle schedulerHandle;

  public ProgressionHudService(JavaPlugin plugin, ProgressionService progressionService, CustomXpService customXpService,
      ProgressionStatService statService, EffectsEngine effectsEngine, Predicate<World> worldAllowed) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.progressionService = Objects.requireNonNull(progressionService, "progressionService");
    this.customXpService = customXpService;
    this.statService = statService;
    this.effectsEngine = Objects.requireNonNull(effectsEngine, "effectsEngine");
    this.worldAllowed = worldAllowed;
    this.scoreboardManager = Objects.requireNonNull(Bukkit.getScoreboardManager(), "scoreboardManager");
    this.layout = Layout.load(plugin);
  }

  public void setClassBonuses(ClassBonusService classBonuses) {
    this.classBonuses = classBonuses;
  }

  public void start() {
    if (schedulerHandle != null) {
      return;
    }
    if (taskId != -1) {
      return;
    }
    long ticks = layout.updateTicks();
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, ticks, ticks);
  }

  public void start(SharedTickScheduler scheduler) {
    if (scheduler == null) {
      start();
      return;
    }
    if (schedulerHandle != null || taskId != -1) {
      return;
    }
    schedulerHandle = scheduler.schedule("progressionHud", layout.updateTicks(), this::tick);
  }

  public void stop() {
    if (schedulerHandle != null) {
      schedulerHandle.cancel();
      schedulerHandle = null;
    }
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      hide(player);
    }
  }

  public void refresh(Player player) {
    if (player == null) {
      return;
    }
    if (!isWorldAllowed(player.getWorld())) {
      hide(player);
      return;
    }
    if (!layout.enabled()) {
      hide(player);
      return;
    }
    if (statService != null) {
      statService.apply(player);
    }
    if (classBonuses != null) {
      classBonuses.apply(player);
    }
    Scoreboard board = scoreboardManager.getNewScoreboard();
    Objective objective = board.getObjective(OBJECTIVE_ID);
    if (objective == null) {
      objective = board.registerNewObjective(
          OBJECTIVE_ID, Criteria.DUMMY, layout.titleComponent(), RenderType.INTEGER);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    } else {
      objective.displayName(layout.titleComponent());
    }
    updateBoard(player, board, objective);
    player.setScoreboard(board);
  }

  public void hide(Player player) {
    if (player == null) {
      return;
    }
    if (statService != null) {
      statService.clear(player);
    }
    if (classBonuses != null) {
      classBonuses.clear(player);
    }
    player.setScoreboard(scoreboardManager.getMainScoreboard());
  }

  private void tick() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!isWorldAllowed(player.getWorld())) {
        hide(player);
        continue;
      }
      if (!layout.enabled()) {
        hide(player);
        continue;
      }
      if (statService != null) {
        statService.apply(player);
      }
      if (classBonuses != null) {
        classBonuses.apply(player);
      }
      Scoreboard board = player.getScoreboard();
      Objective objective = board.getObjective(OBJECTIVE_ID);
      if (objective == null) {
        refresh(player);
        continue;
      }
      updateBoard(player, board, objective);
    }
  }

  private void updateBoard(Player player, Scoreboard board, Objective objective) {
    progressionService.syncFromPlayer(player);
    PlayerProgression progression = progressionService.getOrCreate(player.getUniqueId());
    ManaProvider manaProvider = effectsEngine.manaProvider();
    double mana = manaProvider == null ? 0.0 : manaProvider.get(player);
    double maxMana = manaProvider == null ? 0.0 : manaProvider.getMax(player);
    String classId = "";
    XpSnapshot xpSnapshot = resolveXp(player, progression);
    List<String> lines = formatLines(player, progression, xpSnapshot, mana, maxMana, classId);
    applyLines(board, objective, lines);
  }

  private void applyLines(Scoreboard board, Objective objective, List<String> lines) {
    Set<String> entries = new HashSet<>(board.getEntries());
    for (String entry : entries) {
      board.resetScores(entry);
    }
    List<String> unique = dedupe(lines);
    if (unique.size() > MAX_LINES) {
      unique = unique.subList(0, MAX_LINES);
    }
    int score = unique.size();
    for (String line : unique) {
      objective.getScore(line).setScore(score--);
    }
  }

  private List<String> dedupe(List<String> lines) {
    List<String> out = new ArrayList<>(lines.size());
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line == null) {
        line = "";
      }
      String candidate = line;
      if (seen.contains(candidate)) {
        candidate = line + " §" + Integer.toHexString(i % 16);
      }
      seen.add(candidate);
      out.add(candidate);
    }
    return out;
  }

  private boolean isWorldAllowed(World world) {
    return worldAllowed == null || worldAllowed.test(world);
  }

  private static String format(double value) {
    if (Math.abs(value - Math.round(value)) < 1e-9) {
      return String.valueOf((long) Math.round(value));
    }
    return String.format(java.util.Locale.ROOT, "%.1f", value);
  }

  private static String formatCompact(long value) {
    long abs = Math.abs(value);
    if (abs < 1000) {
      return String.valueOf(value);
    }
    String suffix;
    double scaled;
    if (abs >= 1_000_000_000L) {
      suffix = "B";
      scaled = value / 1_000_000_000.0;
    } else if (abs >= 1_000_000L) {
      suffix = "M";
      scaled = value / 1_000_000.0;
    } else {
      suffix = "K";
      scaled = value / 1_000.0;
    }
    String formatted = String.format(java.util.Locale.ROOT, "%.1f", scaled);
    return formatted + suffix;
  }

  public void reloadConfig() {
    Layout newLayout = Layout.load(plugin);
    if (newLayout.updateTicks() != layout.updateTicks()) {
      stop();
      layout = newLayout;
      start();
      return;
    }
    layout = newLayout;
    for (Player player : Bukkit.getOnlinePlayers()) {
      refresh(player);
    }
  }

  private List<String> formatLines(Player player, PlayerProgression progression, XpSnapshot xpSnapshot, double mana,
      double maxMana,
      String classId) {
    List<String> out = new ArrayList<>();
    Map<String, String> placeholders = new HashMap<>();
    placeholders.put("level", formatCompact(xpSnapshot.level));
    placeholders.put("xp", formatCompact(xpSnapshot.points));
    placeholders.put("skill_points", String.valueOf(progression.skillPoints()));
    placeholders.put("strength", String.valueOf(progression.strength()));
    placeholders.put("dexterity", String.valueOf(progression.dexterity()));
    placeholders.put("intelligence", String.valueOf(progression.intelligence()));
    placeholders.put("vitality", String.valueOf(progression.vitality()));
    placeholders.put("mana", format(mana));
    placeholders.put("mana_max", format(maxMana));
    placeholders.put("mana_percent",
        maxMana <= 0 ? "0" : String.valueOf((int) Math.round((mana / maxMana) * 100)));
    placeholders.put("class", classId);
    placeholders.put("world", player.getWorld().getName());
    for (String template : layout.lines()) {
      if (template == null) {
        continue;
      }
      String rendered = replacePlaceholders(template, placeholders);
      Component component = miniMessage.deserialize(rendered);
      out.add(legacySerializer.serialize(component));
    }
    return out;
  }

  private XpSnapshot resolveXp(Player player, PlayerProgression progression) {
    if (customXpService == null || player == null) {
      return new XpSnapshot(progression.level(), progression.points());
    }
    CustomXpProfile profile = customXpService.getOrCreate(player.getUniqueId());
    return new XpSnapshot(profile.level(), profile.points());
  }

  private static final class XpSnapshot {
    private final int level;
    private final long points;

    private XpSnapshot(int level, long points) {
      this.level = Math.max(1, level);
      this.points = Math.max(0L, points);
    }
  }

  private static String replacePlaceholders(String template, Map<String, String> placeholders) {
    String out = template;
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || value == null) {
        continue;
      }
      out = out.replace("{" + key + "}", value);
    }
    return out;
  }

  private record Layout(boolean enabled, long updateTicks, Component titleComponent, List<String> lines) {
    static Layout load(JavaPlugin plugin) {
      Objects.requireNonNull(plugin, "plugin");
      File file = new File(plugin.getDataFolder(), "scoreboard.yml");
      if (!file.exists()) {
        plugin.saveResource("scoreboard.yml", false);
      }
      org.bukkit.configuration.file.YamlConfiguration yaml =
          org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
      boolean enabled = yaml.getBoolean("enabled", true);
      long updateTicks = Math.max(10L, yaml.getLong("updateTicks", DEFAULT_UPDATE_TICKS));
      String title = yaml.getString("title", "<gold>DungeonsReborn</gold>");
      List<String> lines = yaml.getStringList("lines");
      if (lines.isEmpty()) {
        lines = defaultLines();
      }
      Component titleComponent = MiniMessage.miniMessage().deserialize(title);
      return new Layout(enabled, updateTicks, titleComponent, lines);
    }

    private static List<String> defaultLines() {
      List<String> lines = new ArrayList<>();
      lines.add("<gray>Level:</gray> <green>{level}</green>");
      lines.add("<gray>XP:</gray> <green>{xp}</green>");
      lines.add("<gray>SP:</gray> <yellow>{skill_points}</yellow>");
      lines.add("<gray>STR:</gray> <red>{strength}</red>");
      lines.add("<gray>DEX:</gray> <blue>{dexterity}</blue>");
      lines.add("<gray>INT:</gray> <light_purple>{intelligence}</light_purple>");
      lines.add("<gray>VIT:</gray> <dark_red>{vitality}</dark_red>");
      lines.add("<gray>Mana:</gray> <aqua>{mana}/{mana_max}</aqua>");
      return lines;
    }
  }
}
