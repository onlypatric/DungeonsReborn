package dev.patric.dungeonsreborn.dungeons.menu;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonSpec;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import net.kyori.adventure.text.Component;

public final class DungeonAdminMenu extends Window {
  private static final int SIZE = 54;

  private final DungeonYamlRegistry registry;
  private final DungeonQueueService queue;
  private final DungeonSessionManager sessions;

  public DungeonAdminMenu(DungeonYamlRegistry registry, DungeonQueueService queue, DungeonSessionManager sessions) {
    super(SIZE, GuiMini.mm("<white><bold>Dungeon Admin</bold></white>"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.queue = Objects.requireNonNull(queue, "queue");
    this.sessions = Objects.requireNonNull(sessions, "sessions");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    navLeft(new BackButton());

    setFixed(4, new Label(GuiItems.named(Material.DIAMOND_SWORD, GuiMini.mm("<gold><bold>Dungeon Control</bold></gold>"), List.of(
        GuiMini.mm("<gray>Admin queue + run tools.</gray>")))));

    setFixedAt(1, 1, new Label(p -> statusItem()));
    setFixedAt(1, 2, new Label(p -> queueItem()));

    setFixedAt(1, 4, openQueueButton());
    setFixedAt(1, 5, openStatusButton());

    setFixedAt(3, 1, forceStartButton());
    setFixedAt(3, 2, skipWaveButton());
    setFixedAt(3, 3, endWinButton());
    setFixedAt(3, 4, endFailButton());
    setFixedAt(3, 5, clearQueueButton());
    setFixedAt(3, 6, abortButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private ItemStack statusItem() {
    DungeonSessionManager.SessionStatus status = sessions.status();
    List<Component> lore = new ArrayList<>();
    if (!status.active()) {
      lore.add(GuiMini.mm("<gray>No active run.</gray>"));
    } else {
      lore.add(GuiMini.mm("<gray>Level:</gray> <white>" + status.level() + "</white>"));
      lore.add(GuiMini.mm("<gray>Wave:</gray> <white>" + status.wave() + "/" + status.totalWaves() + "</white>"));
      lore.add(GuiMini.mm("<gray>Boss:</gray> <white>" + (status.bossPhase() ? "yes" : "no") + "</white>"));
      lore.add(GuiMini.mm("<gray>State:</gray> <white>" + status.state().name().toLowerCase() + "</white>"));
      if (status.affixes() != null && !status.affixes().isEmpty()) {
        lore.add(GuiMini.mm("<gray>Affixes:</gray> <white>" + String.join(", ", status.affixes()) + "</white>"));
      }
    }
    return GuiItems.named(Material.BEACON, GuiMini.mm("<aqua><bold>Session Status</bold></aqua>"), lore);
  }

  private ItemStack queueItem() {
    DungeonSpec spec = registry.dungeon();
    List<Component> lore = new ArrayList<>();
    if (spec == null || spec.levels().isEmpty()) {
      lore.add(GuiMini.mm("<gray>No dungeon configured.</gray>"));
    } else {
      int total = 0;
      for (Map.Entry<Integer, DungeonSpec.DungeonLevel> entry : spec.levels().entrySet()) {
        int level = entry.getKey();
        int size = queue.queueSize(level);
        total += size;
        lore.add(GuiMini.mm("<gray>Level " + level + ":</gray> <white>" + size + "</white>"));
      }
      lore.add(GuiMini.mm("<gray>Total queued:</gray> <white>" + total + "</white>"));
    }
    return GuiItems.named(Material.PAPER, GuiMini.mm("<yellow><bold>Queue</bold></yellow>"), lore);
  }

  private Button openQueueButton() {
    return new Button(p -> GuiItems.named(Material.MAP, GuiMini.mm("<green><bold>Open Queue</bold></green>"), List.of(
        GuiMini.mm("<gray>Open the user queue menu.</gray>"))), ctx -> {
      new DungeonQueueMenu(registry, queue).open(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button openStatusButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiMini.mm("<green><bold>Open Status</bold></green>"), List.of(
        GuiMini.mm("<gray>Open the status menu.</gray>"))), ctx -> {
      new DungeonStatusMenu(registry, queue, sessions).open(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton forceStartButton() {
    TextButton button = new TextButton(
        p -> GuiItems.named(Material.LIME_DYE, GuiMini.mm("<green><bold>Force Start</bold></green>"), List.of(
            GuiMini.mm("<gray>Type level number.</gray>"))),
        GuiMini.mm("<yellow>Enter dungeon level</yellow>"),
        "cancel",
        Duration.ofSeconds(20),
        (window, text) -> {
          Player player = viewerPlayer(window);
          if (player == null) {
            return;
          }
          int level;
          try {
            level = Integer.parseInt(text.trim());
          } catch (NumberFormatException ex) {
            player.sendMessage(Component.text("§cEnter a valid level."));
            return;
          }
          boolean started = queue.debugStart(player, level);
          player.sendMessage(Component.text(started ? "§aForced dungeon start." : "§cUnable to start dungeon."));
          window.redraw(player);
        },
        true);
    button.autoDescribeInLore(false);
    return button;
  }

  private Button skipWaveButton() {
    return new Button(p -> GuiItems.named(Material.FEATHER, GuiMini.mm("<yellow><bold>Skip Wave</bold></yellow>"), List.of(
        GuiMini.mm("<gray>Skip the current wave.</gray>"))), ctx -> {
      boolean success = sessions.debugSkipWave();
      ctx.player().sendMessage(Component.text(success ? "§aWave skipped." : "§cNo active wave."));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button endWinButton() {
    return new Button(p -> GuiItems.named(Material.DIAMOND, GuiMini.mm("<green><bold>End Win</bold></green>"), List.of(
        GuiMini.mm("<gray>Force a win.</gray>"))), ctx -> {
      boolean success = sessions.debugEnd(true);
      ctx.player().sendMessage(Component.text(success ? "§aDungeon ended as win." : "§cNo active dungeon."));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button endFailButton() {
    return new Button(p -> GuiItems.named(Material.REDSTONE, GuiMini.mm("<red><bold>End Fail</bold></red>"), List.of(
        GuiMini.mm("<gray>Force a failure.</gray>"))), ctx -> {
      boolean success = sessions.debugEnd(false);
      ctx.player().sendMessage(Component.text(success ? "§aDungeon ended as fail." : "§cNo active dungeon."));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button clearQueueButton() {
    return new Button(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Clear Queue</bold></red>"), List.of(
        GuiMini.mm("<gray>Remove all queued players.</gray>"))), ctx -> {
      queue.clearQueues();
      ctx.player().sendMessage(Component.text("§aQueue cleared."));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button abortButton() {
    return new Button(p -> GuiItems.named(Material.TNT, GuiMini.mm("<red><bold>Abort Run</bold></red>"), List.of(
        GuiMini.mm("<gray>Force stop the active run.</gray>"))), ctx -> {
      boolean success = sessions.abortActive(null);
      ctx.player().sendMessage(Component.text(success ? "§aDungeon run aborted." : "§cNo active dungeon."));
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }
}
