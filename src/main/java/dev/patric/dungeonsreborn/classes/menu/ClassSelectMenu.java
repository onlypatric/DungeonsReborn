package dev.patric.dungeonsreborn.classes.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.ClassService;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.ClassBonusSpec;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.Component;

public final class ClassSelectMenu extends Window {
  private static final int SIZE = 54;

  private final ClassYamlRegistry registry;
  private final ClassService service;
  private final VirtualList<ClassSpec> list;

  public ClassSelectMenu(ClassYamlRegistry registry, ClassService service) {
    super(SIZE, GuiI18n.tr("gui.classes.select.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.service = Objects.requireNonNull(service, "service");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> openConfirm(ctx.player(), entry));
    list.searchKey(spec -> spec.id());
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.button.close"), List.of())));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(6, refreshButton());

    setFixedAt(0, 1, header());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.BOOK, GuiI18n.tr(p, "gui.classes.select.header.title"), List.of(
        GuiI18n.tr(p, "gui.classes.select.header.hint1"),
        GuiI18n.tr(p, "gui.classes.select.header.hint2"))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.classes.select.refresh.title"), List.of(
        GuiI18n.tr(p, "gui.classes.select.refresh.hint"))), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<ClassSpec> entries(Player player) {
    List<ClassSpec> out = new ArrayList<>();
    for (ClassSpec spec : registry.classes().values()) {
      if (spec.enabled()) {
        out.add(spec);
      }
    }
    out.sort(Comparator.comparing(ClassSpec::id));
    return out;
  }

  private ItemStack entryItem(Player player, ClassSpec spec) {
    String current = service.currentClassId(player.getUniqueId());
    boolean selected = current != null && current.equals(spec.id());
    List<Component> lore = new ArrayList<>();
    lore.addAll(spec.descriptionOrEmpty());
    if (!lore.isEmpty()) {
      lore.add(Component.text(" "));
    }
    appendStatSummary(lore, spec.bonusesOrEmpty());
    lore.addAll(service.buildRequirementLore(player, spec));
    if (selected) {
      lore.add(Component.text(" "));
      lore.add(GuiI18n.tr(player, "gui.classes.select.entry.selected"));
    }
    ItemStack base = spec.icon() == null ? new ItemStack(Material.BOOK) : spec.icon().clone();
    return GuiItem.of(base)
        .displayName(spec.displayName())
        .lore(lore)
        .hideItemFlags(true)
        .build();
  }

  private void openConfirm(Player player, ClassSpec spec) {
    List<Component> lore = new ArrayList<>();
    lore.addAll(spec.descriptionOrEmpty());
    if (!lore.isEmpty()) {
      lore.add(Component.text(" "));
    }
    appendStatSummary(lore, spec.bonusesOrEmpty());
    lore.addAll(service.buildRequirementLore(player, spec));
    ConfirmDialogWindow confirm = new ConfirmDialogWindow(
        GuiI18n.tr(player, "gui.classes.select.confirm.title"),
        spec.displayName(),
        lore,
        (p, result) -> {
          if (result != ConfirmDialogWindow.ConfirmResult.CONFIRM) {
            return;
          }
          ClassService.SelectionResult outcome = service.selectClass(p, spec.id());
          p.sendMessage(outcome.message());
          list.invalidate(p);
          list.redraw(this, p);
        });
    openSubWindow(player, confirm);
    GuiSounds.click(player);
  }

  private void appendStatSummary(List<Component> lore, ClassBonusSpec bonuses) {
    if (bonuses == null) {
      return;
    }
    List<Component> stats = new ArrayList<>();
    if (bonuses.strength() > 0) {
      stats.add(GuiI18n.tr("gui.classes.select.stat.strength",
          Placeholder.unparsed("value", String.valueOf(bonuses.strength()))));
    }
    if (bonuses.dexterity() > 0) {
      stats.add(GuiI18n.tr("gui.classes.select.stat.dexterity",
          Placeholder.unparsed("value", String.valueOf(bonuses.dexterity()))));
    }
    if (bonuses.intelligence() > 0) {
      stats.add(GuiI18n.tr("gui.classes.select.stat.intelligence",
          Placeholder.unparsed("value", String.valueOf(bonuses.intelligence()))));
    }
    if (bonuses.vitality() > 0) {
      stats.add(GuiI18n.tr("gui.classes.select.stat.vitality",
          Placeholder.unparsed("value", String.valueOf(bonuses.vitality()))));
    }
    if (bonuses.manaMaxBonus() > 0.0) {
      stats.add(GuiI18n.tr("gui.classes.select.stat.manaMax",
          Placeholder.unparsed("value", String.valueOf(bonuses.manaMaxBonus()))));
    }
    if (bonuses.manaRegenBonus() > 0.0) {
      stats.add(GuiI18n.tr("gui.classes.select.stat.manaRegen",
          Placeholder.unparsed("value", String.valueOf(bonuses.manaRegenBonus()))));
    }
    if (!stats.isEmpty()) {
      lore.addAll(stats);
      lore.add(Component.text(" "));
    }
  }
}
