package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.quests.QuestService;
import dev.patric.dungeonsreborn.quests.QuestSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestLogMenu extends Window {
  private static final int SIZE = 54;

  private record Entry(QuestService.QuestLogEntry logEntry) {
  }

  private final QuestService quests;
  private final VirtualList<Entry> list;

  public QuestLogMenu(QuestService quests) {
    super(SIZE, GuiI18n.tr("gui.quests.log.title"), true);
    this.quests = Objects.requireNonNull(quests, "quests");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry, player),
        (ctx, entry) -> handleClick(ctx.player(), entry));
    list.apply(this, Placement.FIXED);

    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr("gui.quests.log.header.title"), List.of(
        GuiI18n.tr("gui.quests.log.header.hint1"),
        GuiI18n.tr("gui.quests.log.header.hint2")))))
        ;

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<Entry> entries(Player player) {
    List<Entry> out = new ArrayList<>();
    for (QuestService.QuestLogEntry entry : quests.logEntries(player)) {
      out.add(new Entry(entry));
    }
    return out;
  }

  private org.bukkit.inventory.ItemStack entryItem(Entry entry, Player player) {
    QuestService.QuestLogEntry logEntry = entry.logEntry();
    QuestSpec spec = logEntry.spec();
    List<Component> lore = new ArrayList<>();
    lore.add(logEntry.statusLine());
    lore.add(badgeLine(logEntry.status()));
    Component progress = progressBar(spec, logEntry.state());
    if (progress != null) {
      lore.add(progress);
    }
    if (!spec.description().isEmpty()) {
      for (String line : spec.description()) {
        lore.add(GuiI18n.tr(player, "gui.quests.description.line", Placeholder.unparsed("line", line)));
      }
    }
    if (logEntry.state() != null) {
      lore.addAll(quests.describeObjectives(player, spec, logEntry.state()));
    } else if (logEntry.status() == QuestService.QuestEntryStatus.AVAILABLE) {
      lore.add(GuiI18n.tr(player, "gui.quests.log.action.accept"));
    }
    Material icon = switch (logEntry.status()) {
      case ACTIVE -> Material.LIME_DYE;
      case AVAILABLE -> Material.PAPER;
      case COMPLETED -> Material.GRAY_DYE;
      case COOLDOWN -> Material.CLOCK;
      case LOCKED -> Material.BARRIER;
    };
    return GuiItems.named(icon, GuiI18n.tr(player, "gui.quests.log.entry.title", Placeholder.unparsed("name", spec.name())), lore);
  }

  private Component badgeLine(QuestService.QuestEntryStatus status) {
    Component badge = switch (status) {
      case ACTIVE -> GuiI18n.tr("gui.quests.log.badge.active");
      case AVAILABLE -> GuiI18n.tr("gui.quests.log.badge.available");
      case COMPLETED -> GuiI18n.tr("gui.quests.log.badge.completed");
      case COOLDOWN -> GuiI18n.tr("gui.quests.log.badge.cooldown");
      case LOCKED -> GuiI18n.tr("gui.quests.log.badge.locked");
    };
    return GuiI18n.tr("gui.quests.log.badge.label", Placeholder.component("badge", badge));
  }

  private Component progressBar(QuestSpec spec, dev.patric.dungeonsreborn.quests.QuestPlayerQuest state) {
    if (spec == null || state == null) {
      return null;
    }
    int requiredTotal = 0;
    int currentTotal = 0;
    for (int i = 0; i < spec.objectives().size(); i++) {
      int required = Math.max(1, spec.objectives().get(i).count());
      int current = Math.min(required, state.progress(i));
      requiredTotal += required;
      currentTotal += current;
    }
    if (requiredTotal <= 0) {
      return null;
    }
    int percent = (int) Math.round((currentTotal * 100.0) / requiredTotal);
    String bar = progressBar(percent);
    return GuiI18n.tr("gui.quests.log.progress", Placeholder.unparsed("bar", bar), Placeholder.unparsed("percent", String.valueOf(percent)));
  }

  private String progressBar(int percent) {
    int bars = Math.max(0, Math.min(10, (int) Math.round(percent / 10.0)));
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < 10; i++) {
      out.append(i < bars ? "#" : "-");
    }
    out.append("]");
    return out.toString();
  }

  private void handleClick(Player player, Entry entry) {
    if (player == null) {
      return;
    }
    QuestService.QuestLogEntry logEntry = entry.logEntry();
    if (logEntry.status() != QuestService.QuestEntryStatus.AVAILABLE) {
      return;
    }
    QuestService.QuestAcceptResult result = quests.accept(player, logEntry.spec().id());
    player.sendMessage(result.message());
    redraw(player);
  }
}
