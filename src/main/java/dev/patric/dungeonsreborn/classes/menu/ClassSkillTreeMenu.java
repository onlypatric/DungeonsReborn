package dev.patric.dungeonsreborn.classes.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillTreeSpec;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.flow.ConfirmDialogWindow;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ClassSkillTreeMenu extends Window {
  private static final int SIZE = 54;

  private final ClassSpec spec;
  private final ClassSkillService skills;
  private final VirtualList<SkillNodeSpec> list;

  public ClassSkillTreeMenu(ClassSpec spec, ClassSkillService skills) {
    super(SIZE, GuiI18n.tr("gui.classes.skillTree.title"), true);
    this.spec = Objects.requireNonNull(spec, "spec");
    this.skills = Objects.requireNonNull(skills, "skills");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> handleClick(ctx.player(), entry, ctx.clickType()));
    list.searchKey(node -> node.id());
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.button.close"), List.of())));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixedAt(0, 4, header());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(player -> {
      int points = skills.skillPoints(player);
      int spent = skills.spentSkillPoints(player);
      int total = skills.totalSkillPoints(player);
      SkillTreeSpec tree = spec.skillTreeOrEmpty();
      int unlocked = skills.unlockedNodes(player.getUniqueId(), spec.id()).size();
      int totalNodes = tree.nodes().size();
      List<Component> lore = new ArrayList<>();
      lore.add(GuiI18n.tr(player, "gui.classes.skillTree.header.class",
          Placeholder.component("value", spec.displayName())));
      lore.add(GuiI18n.tr(player, "gui.classes.skillTree.header.points.unspent",
          Placeholder.unparsed("value", String.valueOf(points))));
      lore.add(GuiI18n.tr(player, "gui.classes.skillTree.header.points.total",
          Placeholder.unparsed("value", String.valueOf(total)),
          Placeholder.unparsed("spent", String.valueOf(spent))));
      lore.add(GuiI18n.tr(player, "gui.classes.skillTree.header.unlocked",
          Placeholder.unparsed("unlocked", String.valueOf(unlocked)),
          Placeholder.unparsed("total", String.valueOf(totalNodes))));
      if (tree.respecTokens() > 0 || tree.respecPoints() > 0) {
        lore.add(Component.text(" "));
        lore.add(GuiI18n.tr(player, "gui.classes.skillTree.header.respecCost",
            Placeholder.unparsed("tokens", String.valueOf(tree.respecTokens())),
            Placeholder.unparsed("points", String.valueOf(tree.respecPoints()))));
      }
      lore.add(Component.text(" "));
      lore.add(GuiI18n.tr(player, "gui.classes.skillTree.header.hint.unlock"));
      lore.add(GuiI18n.tr(player, "gui.classes.skillTree.header.hint.respec"));
      return GuiItems.named(Material.ENCHANTED_BOOK, GuiI18n.tr(player, "gui.classes.skillTree.header.title"), lore);
    });
  }

  private List<SkillNodeSpec> entries(Player player) {
    return spec.skillTreeOrEmpty().nodes();
  }

  private ItemStack entryItem(Player player, SkillNodeSpec node) {
    boolean unlocked = skills.isUnlocked(player.getUniqueId(), spec.id(), node.id());
    List<String> requires = skills.requirements(spec, node);
    List<Component> lore = new ArrayList<>();
    lore.addAll(node.descriptionOrEmpty());
    if (!lore.isEmpty()) {
      lore.add(Component.text(" "));
    }
    lore.add(GuiI18n.tr(player, "gui.classes.skillTree.node.type",
        Placeholder.unparsed("value", node.type().name().toLowerCase())));
    lore.add(GuiI18n.tr(player, "gui.classes.skillTree.node.cost",
        Placeholder.unparsed("value", String.valueOf(node.cost()))));
    if (!requires.isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.classes.skillTree.node.requires",
          Placeholder.unparsed("value", String.join(", ", requires))));
    }
    lore.add(Component.text(" "));
    lore.add(unlocked
        ? GuiI18n.tr(player, "gui.classes.skillTree.node.status.unlocked")
        : GuiI18n.tr(player, "gui.classes.skillTree.node.status.locked"));
    ItemStack base = node.icon() == null ? new ItemStack(Material.PAPER) : node.icon().clone();
    return GuiItem.of(base)
        .displayName(node.displayName())
        .lore(lore)
        .glint(unlocked)
        .hideItemFlags(true)
        .build();
  }

  private void handleClick(Player player, SkillNodeSpec node, org.bukkit.event.inventory.ClickType clickType) {
    if (player == null || node == null) {
      return;
    }
    if (clickType.isLeftClick()) {
      ClassSkillService.SkillResult result = skills.unlock(player, spec, node);
      player.sendMessage(result.message());
      list.invalidate(player);
      list.redraw(this, player);
      GuiSounds.click(player);
      return;
    }
    if (clickType.isRightClick()) {
      openRespec(player, node);
    }
  }

  private void openRespec(Player player, SkillNodeSpec node) {
    List<Component> lore = new ArrayList<>();
    lore.addAll(node.descriptionOrEmpty());
    if (!lore.isEmpty()) {
      lore.add(Component.text(" "));
    }
    SkillTreeSpec tree = spec.skillTreeOrEmpty();
    lore.add(GuiI18n.tr(player, "gui.classes.skillTree.respec.cost",
        Placeholder.unparsed("tokens", String.valueOf(tree.respecTokens())),
        Placeholder.unparsed("points", String.valueOf(tree.respecPoints()))));
    ConfirmDialogWindow confirm = new ConfirmDialogWindow(
        GuiI18n.tr(player, "gui.classes.skillTree.respec.title"),
        node.displayName(),
        lore,
        (p, result) -> {
          if (result != ConfirmDialogWindow.ConfirmResult.CONFIRM) {
            return;
          }
          ClassSkillService.SkillResult outcome = skills.respec(p, spec, node);
          p.sendMessage(outcome.message());
          list.invalidate(p);
          list.redraw(this, p);
        });
    openSubWindow(player, confirm);
    GuiSounds.click(player);
  }
}
