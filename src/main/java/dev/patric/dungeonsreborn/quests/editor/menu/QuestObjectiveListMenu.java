package dev.patric.dungeonsreborn.quests.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.quests.QuestObjectiveType;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import dev.patric.dungeonsreborn.quests.editor.QuestEditorYaml;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestObjectiveListMenu extends Window {
  private static final int SIZE = 54;

  private record Entry(QuestEditorYaml.ObjectiveData data) {
  }

  private final QuestYamlRegistry yaml;
  private final String questId;
  private final VirtualList<Entry> list;

  public QuestObjectiveListMenu(QuestYamlRegistry yaml, String questId) {
    super(SIZE, GuiI18n.tr("gui.quests.objectives.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.questId = Objects.requireNonNull(questId, "questId");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> openEntry(ctx.player(), entry));
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.button.back"), List.of())));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(5, refreshButton());
    nav(6, addKillButton());
    nav(7, addUseButton());
    nav(8, addVisitButton());
    nav(9, addCraftButton());

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.quests.objectives.header.title"), List.of(
        GuiI18n.tr(p, "gui.quests.objectives.header.hint")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.quests.objectives.refresh.title"), List.of(
        GuiI18n.tr(p, "gui.quests.objectives.refresh.hint"))), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button addKillButton() {
    return addButton(Material.IRON_SWORD, QuestObjectiveType.KILL_MOB, "gui.quests.objectives.add.kill");
  }

  private Button addUseButton() {
    return addButton(Material.CARROT_ON_A_STICK, QuestObjectiveType.USE_ITEM, "gui.quests.objectives.add.use");
  }

  private Button addVisitButton() {
    return addButton(Material.COMPASS, QuestObjectiveType.VISIT_REGION, "gui.quests.objectives.add.visit");
  }

  private Button addCraftButton() {
    return addButton(Material.CRAFTING_TABLE, QuestObjectiveType.CRAFT_ITEM, "gui.quests.objectives.add.craft");
  }

  private Button addButton(Material material, QuestObjectiveType type, String labelKey) {
    return new Button(p -> GuiItems.named(material, GuiI18n.tr(p, labelKey), List.of()),
        ctx -> {
          QuestEditorYaml.addObjective(yaml.file(), questId, type);
          yaml.reload();
          list.invalidate(ctx.player());
          list.redraw(ctx.window(), ctx.player());
          GuiSounds.click(ctx.player());
        }).autoDescribeInLore(false);
  }

  private List<Entry> entries(Player player) {
    List<Entry> out = new ArrayList<>();
    for (QuestEditorYaml.ObjectiveData data : QuestEditorYaml.objectives(yaml.file(), questId)) {
      out.add(new Entry(data));
    }
    return out;
  }

  private org.bukkit.inventory.ItemStack entryItem(Entry entry) {
    QuestObjectiveType type = entry.data.type();
    String summary = QuestObjectiveSummary.describe(entry.data);
    Material material = switch (type) {
      case KILL_MOB -> Material.IRON_SWORD;
      case USE_ITEM -> Material.CARROT_ON_A_STICK;
      case VISIT_REGION -> Material.COMPASS;
      case CRAFT_ITEM -> Material.CRAFTING_TABLE;
    };
    return GuiItems.named(material, GuiI18n.tr("gui.quests.objectives.entry.title",
        Placeholder.unparsed("type", type.name().toLowerCase())), List.of(
            GuiI18n.tr("gui.quests.objectives.entry.summary", Placeholder.unparsed("summary", summary))));
  }

  private void openEntry(Player player, Entry entry) {
    openSubWindow(player, new QuestObjectiveEditorMenu(yaml, questId, entry.data.index()));
    GuiSounds.click(player);
  }
}
