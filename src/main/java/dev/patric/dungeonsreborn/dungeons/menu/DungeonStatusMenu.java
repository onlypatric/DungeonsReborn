package dev.patric.dungeonsreborn.dungeons.menu;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import net.kyori.adventure.text.Component;

public final class DungeonStatusMenu extends Window {
  private static final int SIZE = 54;

  private final DungeonYamlRegistry registry;
  private final DungeonQueueService queue;
  private final DungeonSessionManager sessions;

  public DungeonStatusMenu(DungeonYamlRegistry registry, DungeonQueueService queue, DungeonSessionManager sessions) {
    super(SIZE, GuiMini.mm("<white><bold>Dungeon Status</bold></white>"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.queue = queue;
    this.sessions = sessions;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    setFixedAt(0, 4, header());
    setFixedAt(2, 4, new Label(this::statusItem));

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Close</bold></red>"), List.of())));
    nav(4, queueButton());
    nav(5, refreshButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(player -> GuiItems.named(Material.NETHER_STAR, GuiMini.mm("<gold><bold>Dungeon Status</bold></gold>"), List.of(
        GuiMini.mm("<gray>Shows the active run, if any.</gray>"),
        GuiMini.mm("<gray>Refresh to update timings.</gray>"))));
  }

  private Button queueButton() {
    return new Button(p -> GuiItems.named(Material.DIAMOND_SWORD, GuiMini.mm("<aqua><bold>Queue</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Open the dungeon queue.</gray>"))), ctx -> {
      if (queue == null) {
        ctx.player().sendMessage(Component.text("§cDungeon queue is unavailable."));
        return;
      }
      new DungeonQueueMenu(registry, queue).open(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiMini.mm("<yellow><bold>Refresh</bold></yellow>"), List.of(
        GuiMini.mm("<gray>Update dungeon status.</gray>"))), ctx -> {
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private ItemStack statusItem(Player player) {
    if (registry.dungeon() == null) {
      return GuiItems.named(Material.BARRIER, GuiMini.mm("<gray>Dungeons</gray>"),
          List.of(registry.unavailableMessage()));
    }
    if (sessions == null) {
      return GuiItems.named(Material.BARRIER, GuiMini.mm("<gray>Status</gray>"), List.of(
          GuiMini.mm("<red>Dungeon system not available.</red>")));
    }
    DungeonSessionManager.SessionStatus status = sessions.status();
    if (!status.active()) {
      return GuiItems.named(Material.CLOCK, GuiMini.mm("<gray>No Active Run</gray>"), List.of(
          GuiMini.mm("<gray>No dungeon is running right now.</gray>")));
    }
    long now = System.currentTimeMillis();
    long remaining = status.timeLimitMillis() <= 0L ? -1L
        : Math.max(0L, status.timeLimitMillis() - (now - status.startedAt()));
    String timeLeft = remaining < 0 ? "No limit" : formatDuration(remaining);
    List<Component> lore = new java.util.ArrayList<>();
    lore.add(Component.text("Dungeon: ").append(status.dungeonName()));
    lore.add(GuiMini.mm("<gray>Level:</gray> <white>" + status.level() + "</white>"));
    lore.add(GuiMini.mm("<gray>State:</gray> <white>" + status.state().name().toLowerCase() + "</white>"));
    if (status.affixes() != null && !status.affixes().isEmpty()) {
      lore.add(GuiMini.mm("<gray>Affixes:</gray> <white>" + String.join(", ", status.affixes()) + "</white>"));
    }
    if (status.bossPhase()) {
      lore.add(GuiMini.mm("<gray>Phase:</gray> <red><bold>Boss</bold></red>"));
    } else if (status.totalWaves() > 0) {
      lore.add(GuiMini.mm("<gray>Wave:</gray> <white>" + status.wave() + "/" + status.totalWaves() + "</white>"));
    } else {
      lore.add(GuiMini.mm("<gray>Wave:</gray> <white>None</white>"));
    }
    lore.add(GuiMini.mm("<gray>Time remaining:</gray> <white>" + timeLeft + "</white>"));
    if (status.waitUntilMillis() > now) {
      lore.add(GuiMini.mm("<gray>Next wave in:</gray> <white>"
          + formatDuration(status.waitUntilMillis() - now) + "</white>"));
    }
    return GuiItems.named(Material.BEACON, GuiMini.mm("<gold><bold>Active Run</bold></gold>"), lore);
  }

  private static String formatDuration(long millis) {
    if (millis <= 0L) {
      return "0s";
    }
    long totalSeconds = millis / 1000L;
    long minutes = totalSeconds / 60L;
    long seconds = totalSeconds % 60L;
    if (minutes > 0) {
      return minutes + "m " + seconds + "s";
    }
    return seconds + "s";
  }
}
