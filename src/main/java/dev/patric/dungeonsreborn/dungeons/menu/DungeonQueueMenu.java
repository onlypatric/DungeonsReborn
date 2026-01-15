package dev.patric.dungeonsreborn.dungeons.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSpec;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import net.kyori.adventure.text.Component;

public final class DungeonQueueMenu extends Window {
  private static final int SIZE = 54;

  private record LevelEntry(int level, DungeonSpec.DungeonLevel spec) {
  }

  private final DungeonYamlRegistry registry;
  private final DungeonQueueService queue;
  private final VirtualList<LevelEntry> list;

  public DungeonQueueMenu(DungeonYamlRegistry registry, DungeonQueueService queue) {
    super(SIZE, GuiMini.mm("<white><bold>Dungeon Queue</bold></white>"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.queue = Objects.requireNonNull(queue, "queue");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> join(ctx.player(), entry));
    list.searchKey(entry -> "level " + entry.level);
    list.emptyStateItem(player -> {
      if (registry.dungeon() == null) {
        return GuiItems.named(Material.BARRIER, GuiMini.mm("<gray>Dungeons</gray>"), List.of(registry.unavailableMessage()));
      }
      return EmptyState.list().apply(player);
    });
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Close</bold></red>"), List.of())));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, leaveButton());
    nav(5, refreshButton());

    setFixedAt(0, 1, header());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(player -> {
      DungeonQueueService.QueueStatus status = queue.status(player.getUniqueId());
      List<Component> lore = new ArrayList<>();
      if (registry.dungeon() == null) {
        lore.add(registry.unavailableMessage());
        return GuiItems.named(Material.NETHER_STAR, GuiMini.mm("<gold><bold>Dungeon Queue</bold></gold>"), lore);
      }
      if (status.active()) {
        lore.add(GuiMini.mm("<green>Active run:</green> <white>Level " + status.level() + "</white>"));
      } else if (status.queued()) {
        lore.add(GuiMini.mm("<gray>Queued:</gray> <white>Level " + status.level() + "</white>"));
        lore.add(GuiMini.mm("<gray>Position:</gray> <white>" + status.position() + "/" + status.totalInLevel() + "</white>"));
      } else {
        lore.add(GuiMini.mm("<gray>Not queued.</gray>"));
      }
      return GuiItems.named(Material.NETHER_STAR, GuiMini.mm("<gold><bold>Dungeon Queue</bold></gold>"), lore);
    });
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiMini.mm("<yellow><bold>Refresh</bold></yellow>"), List.of(
        GuiMini.mm("<gray>Reload dungeon config.</gray>"))), ctx -> {
      registry.reload();
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button leaveButton() {
    return new Button(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Leave Queue</bold></red>"), List.of(
        GuiMini.mm("<gray>Leave your current queue.</gray>"))), ctx -> {
      var result = queue.leave(ctx.player());
      ctx.player().sendMessage(result.message());
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<LevelEntry> entries(Player player) {
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null) {
      return List.of();
    }
    List<LevelEntry> out = new ArrayList<>();
    for (var entry : dungeon.levels().entrySet()) {
      out.add(new LevelEntry(entry.getKey(), entry.getValue()));
    }
    out.sort(Comparator.comparingInt(a -> a.level));
    return out;
  }

  private ItemStack entryItem(Player player, LevelEntry entry) {
    DungeonSpec dungeon = registry.dungeon();
    int queued = queue.queueSize(entry.level);
    DungeonQueueService.QueueStatus status = queue.status(player.getUniqueId());
    List<Component> lore = new ArrayList<>();
    lore.add(GuiMini.mm("<gray>Queue size:</gray> <white>" + queued + "</white>"));
    lore.add(GuiMini.mm("<gray>Cost:</gray> <white>" + entry.spec.queueTokens() + " tokens</white>"));
    lore.add(GuiMini.mm("<gray>Wait:</gray> <white>" + entry.spec.waitSeconds() + "s</white>"));
    if (dungeon != null && entry.level > 1) {
      int maxCompleted = queue.maxCompleted(player.getUniqueId(), dungeon.id());
      if (maxCompleted >= entry.level - 1) {
        lore.add(GuiMini.mm("<gray>Requires:</gray> <green>Complete level " + (entry.level - 1) + "</green>"));
      } else {
        lore.add(GuiMini.mm("<gray>Requires:</gray> <red>Complete level " + (entry.level - 1) + "</red>"));
      }
    }
    int position = status.queued() && status.level() == entry.level ? status.position() : queued + 1;
    if (status.queued() && status.level() == entry.level) {
      lore.add(GuiMini.mm("<gray>Your position:</gray> <white>" + status.position()
          + "/" + status.totalInLevel() + "</white>"));
    }
    String estimate = formatEstimate(entry, position);
    if (estimate != null) {
      lore.add(GuiMini.mm("<gray>Est. wait:</gray> <white>~" + estimate + "</white>"));
    }
    lore.add(Component.text(" "));
    lore.add(GuiMini.mm("<gray>Click to join.</gray>"));
    return GuiItem.of(new ItemStack(Material.DIAMOND_SWORD))
        .displayName(GuiMini.mm("<white><bold>Level " + entry.level + "</bold></white>"))
        .lore(lore)
        .hideItemFlags(true)
        .build();
  }

  private void join(Player player, LevelEntry entry) {
    var result = queue.join(player, entry.level);
    player.sendMessage(result.message());
    list.invalidate(player);
    list.redraw(this, player);
    redraw(player);
    GuiSounds.click(player);
  }

  private String formatEstimate(LevelEntry entry, int position) {
    if (position <= 1) {
      return "0s";
    }
    int base = entry.spec.timeLimitSeconds() > 0 ? entry.spec.timeLimitSeconds() : Math.max(60, entry.spec.waitSeconds());
    if (base <= 0) {
      return null;
    }
    int seconds = Math.max(0, position - 1) * base;
    int minutes = seconds / 60;
    int rem = seconds % 60;
    if (minutes > 0) {
      return minutes + "m " + rem + "s";
    }
    return rem + "s";
  }
}
