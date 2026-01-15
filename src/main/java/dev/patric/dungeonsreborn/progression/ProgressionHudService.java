package dev.patric.dungeonsreborn.progression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class ProgressionHudService {
  private static final String OBJECTIVE_ID = "dr_hud";
  private static final long UPDATE_TICKS = 40L;

  private final JavaPlugin plugin;
  private final ProgressionService progressionService;
  private final ProgressionStatService statService;
  private final EffectsEngine effectsEngine;
  private final Predicate<World> worldAllowed;
  private final ScoreboardManager scoreboardManager;
  private ClassBonusService classBonuses;
  private int taskId = -1;
  private SharedTickScheduler.Handle schedulerHandle;

  public ProgressionHudService(JavaPlugin plugin, ProgressionService progressionService, ProgressionStatService statService,
      EffectsEngine effectsEngine, Predicate<World> worldAllowed) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.progressionService = Objects.requireNonNull(progressionService, "progressionService");
    this.statService = statService;
    this.effectsEngine = Objects.requireNonNull(effectsEngine, "effectsEngine");
    this.worldAllowed = worldAllowed;
    this.scoreboardManager = Objects.requireNonNull(Bukkit.getScoreboardManager(), "scoreboardManager");
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
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, UPDATE_TICKS, UPDATE_TICKS);
  }

  public void start(SharedTickScheduler scheduler) {
    if (scheduler == null) {
      start();
      return;
    }
    if (schedulerHandle != null || taskId != -1) {
      return;
    }
    schedulerHandle = scheduler.schedule("progressionHud", UPDATE_TICKS, this::tick);
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
          OBJECTIVE_ID, Criteria.DUMMY, Component.text("DungeonsReborn", NamedTextColor.GOLD), RenderType.INTEGER);
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
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
    List<String> lines = new ArrayList<>();
    lines.add(formatLine("Level", String.valueOf(progression.level()), NamedTextColor.GREEN));
    lines.add(formatLine("XP", String.valueOf(progression.points()), NamedTextColor.GREEN));
    lines.add(formatLine("SP", String.valueOf(progression.skillPoints()), NamedTextColor.YELLOW));
    lines.add(formatLine("STR", String.valueOf(progression.strength()), NamedTextColor.RED));
    lines.add(formatLine("DEX", String.valueOf(progression.dexterity()), NamedTextColor.BLUE));
    lines.add(formatLine("INT", String.valueOf(progression.intelligence()), NamedTextColor.LIGHT_PURPLE));
    lines.add(formatLine("VIT", String.valueOf(progression.vitality()), NamedTextColor.DARK_RED));
    ManaProvider manaProvider = effectsEngine.manaProvider();
    if (manaProvider != null) {
      double mana = manaProvider.get(player);
      double maxMana = manaProvider.getMax(player);
      lines.add(formatLine("Mana", format(mana) + "/" + format(maxMana), NamedTextColor.AQUA));
    }
    applyLines(board, objective, lines);
  }

  private void applyLines(Scoreboard board, Objective objective, List<String> lines) {
    Set<String> entries = new HashSet<>(board.getEntries());
    for (String entry : entries) {
      board.resetScores(entry);
    }
    List<String> unique = dedupe(lines);
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

  private static String formatLine(String label, String value, NamedTextColor labelColor) {
    Component line = Component.text(label + ": ", labelColor)
        .append(Component.text(value, NamedTextColor.WHITE));
    return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(line);
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
}
