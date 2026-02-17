package dev.patric.dungeonsreborn.dungeons.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonSpec;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class DungeonStatusMenu extends Window {
  private final DungeonYamlRegistry registry;
  private final DungeonQueueService queue;
  private final DungeonSessionManager sessions;

  public DungeonStatusMenu(DungeonYamlRegistry registry, DungeonQueueService queue, DungeonSessionManager sessions) {
    super(54, GuiI18n.tr("gui.dungeons.status.title"));
    this.registry = Objects.requireNonNull(registry, "registry");
    this.queue = Objects.requireNonNull(queue, "queue");
    this.sessions = sessions;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());

    setFixedAt(0, 4, new Label(this::headerItem));
    setFixedAt(2, 4, new Label(this::queueStatusItem));
    setFixedAt(3, 4, new Label(this::sessionStatusItem));
  }

  private ItemStack headerItem(Player player) {
    DungeonSpec dungeon = registry.dungeon();
    Component title = dungeon == null
        ? registry.unavailableMessage()
        : GuiI18n.tr(player, "gui.dungeons.status.header");
    return GuiItems.head("ICON_DUNGEONS", title, List.of());
  }

  private ItemStack queueStatusItem(Player player) {
    DungeonQueueService.QueueStatus status = queue.status(player.getUniqueId());
    Component title;
    if (!status.queued()) {
      title = GuiI18n.tr(player, "gui.dungeons.status.queue.none");
    } else if (status.active()) {
      title = GuiI18n.tr(player, "gui.dungeons.status.queue.active",
          Placeholder.unparsed("level", String.valueOf(status.level())));
    } else {
      title = GuiI18n.tr(player, "gui.dungeons.status.queue.queued",
          Placeholder.unparsed("level", String.valueOf(status.level())),
          Placeholder.unparsed("position", String.valueOf(status.position())),
          Placeholder.unparsed("total", String.valueOf(status.totalInLevel())));
    }
    return GuiItems.head("ICON_DUNGEONS", title, List.of());
  }

  private ItemStack sessionStatusItem(Player player) {
    if (sessions == null) {
      return GuiItems.head("ICON_DUNGEONS", GuiI18n.tr(player, "gui.dungeons.status.none"), List.of());
    }
    DungeonSessionManager.SessionStatus status = sessions.status();
    if (!status.active()) {
      return GuiItems.head("ICON_DUNGEONS", GuiI18n.tr(player, "gui.dungeons.status.none"), List.of());
    }

    List<Component> lore = new ArrayList<>();
    if (status.dungeonName() != null) {
      lore.add(status.dungeonName());
    }
    lore.add(GuiI18n.tr(player, "gui.dungeons.status.session.state",
        Placeholder.unparsed("state", status.state().name())));
    if (status.totalWaves() > 0) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.status.session.wave",
          Placeholder.unparsed("wave", String.valueOf(status.wave())),
          Placeholder.unparsed("total", String.valueOf(status.totalWaves()))));
    }
    lore.add(GuiI18n.tr(player,
        status.bossPhase() ? "gui.dungeons.status.session.boss.on" : "gui.dungeons.status.session.boss.off"));
    long now = System.currentTimeMillis();
    if (status.timeLimitMillis() > 0) {
      long remaining = Math.max(0L, status.timeLimitMillis() - Math.max(0L, now - status.startedAt()));
      lore.add(GuiI18n.tr(player, "gui.dungeons.status.session.timeLeft",
          Placeholder.unparsed("time", formatDuration(remaining))));
    }
    if (status.waitUntilMillis() > now) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.status.session.wait",
          Placeholder.unparsed("time", formatDuration(status.waitUntilMillis() - now))));
    }
    if (status.affixes() != null && !status.affixes().isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.status.session.affixes",
          Placeholder.unparsed("count", String.valueOf(status.affixes().size()))));
    }

    return GuiItems.head("ICON_DUNGEONS", GuiI18n.tr(player, "gui.dungeons.status.active"), lore);
  }

  private static String formatDuration(long millis) {
    long totalSeconds = Math.max(0L, millis / 1000L);
    long minutes = totalSeconds / 60L;
    long seconds = totalSeconds % 60L;
    if (minutes > 0) {
      return minutes + "m " + seconds + "s";
    }
    return seconds + "s";
  }
}
