package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.effects.upgrades.UpgradeActivator;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeSpellSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeTemplate;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class UpgradeAdminListMenu extends Window {
  private static final int SIZE = 54;

  private record UpgradeEntry(String id, String name, UpgradeTemplate template) {
  }

  private final UpgradeService upgrades;
  private final VirtualList<UpgradeEntry> list;

  public UpgradeAdminListMenu(UpgradeService upgrades) {
    super(SIZE, GuiI18n.tr("gui.upgrades.admin.title"), true);
    this.upgrades = Objects.requireNonNull(upgrades, "upgrades");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        this::entryItem,
        (ctx, entry) -> giveUpgrade(ctx.player(), entry));
    list.searchKey(entry -> entry.id + " " + entry.name);
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.closeButton());
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    nav(5, refreshButton());

    setFixedAt(0, 1, header());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.ENCHANTED_BOOK, GuiI18n.tr(p, "gui.upgrades.admin.header.title"),
        List.of(GuiI18n.tr(p, "gui.upgrades.admin.header.hint",
            Placeholder.unparsed("count", String.valueOf(upgrades.registry().upgrades().size()))))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.upgrades.admin.refresh.title"), List.of(
        GuiI18n.tr(p, "gui.upgrades.admin.refresh.hint"))), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<UpgradeEntry> entries(Player player) {
    List<UpgradeEntry> out = new ArrayList<>();
    for (Map.Entry<String, UpgradeTemplate> entry : upgrades.registry().upgrades().entrySet()) {
      String id = entry.getKey();
      UpgradeTemplate template = entry.getValue();
      UpgradeSpec spec = template == null ? null : template.spec();
      String name = spec == null || spec.name() == null || spec.name().isBlank() ? id : spec.name();
      out.add(new UpgradeEntry(id, name, template));
    }
    out.sort(Comparator.comparing(UpgradeEntry::id, String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  private ItemStack entryItem(Player player, UpgradeEntry entry) {
    ItemStack item = entry.template == null ? new ItemStack(Material.PAPER) : entry.template.buildItem();
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    List<Component> lore = meta.lore();
    if (lore == null) {
      lore = new ArrayList<>();
    } else {
      lore = new ArrayList<>(lore);
    }
    lore.add(Component.empty());
    lore.add(GuiI18n.tr(player, "gui.upgrades.admin.entry.id", Placeholder.unparsed("id", entry.id)));
    UpgradeSpec spec = entry.template == null ? null : entry.template.spec();
    if (spec != null && !spec.spells().isEmpty()) {
      String activators = spec.spells().stream()
          .map(spell -> activatorLabel(player, spell.activator()))
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.joining(", "));
      String abilities = spec.spells().stream()
          .map(UpgradeSpellSpec::abilityId)
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.joining(", "));
      lore.add(GuiI18n.tr(player, "gui.upgrades.admin.entry.activator",
          Placeholder.unparsed("value", activators)));
      lore.add(GuiI18n.tr(player, "gui.upgrades.admin.entry.ability",
          Placeholder.unparsed("id", abilities)));
    }
    lore.add(GuiI18n.tr(player, "gui.upgrades.admin.entry.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private void giveUpgrade(Player player, UpgradeEntry entry) {
    ItemStack item = upgrades.registry().upgradeItem(entry.id);
    if (item == null) {
      player.sendMessage(Locales.component(player, "messages.command.unknownUpgrade",
          Locales.placeholders("id", entry.id)));
      return;
    }
    player.getInventory().addItem(item);
    player.sendMessage(Locales.component(player, "messages.command.upgradeGiven",
        Locales.placeholders("player", player.getName())));
    GuiSounds.click(player);
  }

  private static String activatorLabel(Player player, UpgradeActivator activator) {
    return switch (activator) {
      case LEFT_CLICK -> GuiI18n.str(player, "gui.upgrades.merge.activator.left");
      case RIGHT_CLICK -> GuiI18n.str(player, "gui.upgrades.merge.activator.right");
      case SHIFT_LEFT_CLICK -> GuiI18n.str(player, "gui.upgrades.merge.activator.shiftLeft");
      case SHIFT_RIGHT_CLICK -> GuiI18n.str(player, "gui.upgrades.merge.activator.shiftRight");
      case PASSIVE -> GuiI18n.str(player, "gui.upgrades.merge.activator.passive");
    };
  }
}
