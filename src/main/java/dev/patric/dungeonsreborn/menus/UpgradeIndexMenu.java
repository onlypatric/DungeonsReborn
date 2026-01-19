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

import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeSpec;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeTemplate;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeActivator;
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

public final class UpgradeIndexMenu extends Window {
  private static final int SIZE = 54;

  private record UpgradeEntry(String id, String name, UpgradeTemplate template) {
  }

  private final UpgradeService upgrades;
  private final VirtualList<UpgradeEntry> list;
  private List<UpgradeEntry> cachedEntries = List.of();
  private boolean cacheValid;

  public UpgradeIndexMenu(UpgradeService upgrades) {
    super(SIZE, GuiI18n.tr("gui.upgrades.index.title"), true);
    this.upgrades = Objects.requireNonNull(upgrades, "upgrades");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        this::entryItem,
        (ctx, entry) -> viewOnly(ctx.player()));
    list.searchKey(entry -> entry.id + " " + entry.name);
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.backButton().autoDescribeInLore(false));
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
    return new Label(p -> GuiItems.named(Material.ENCHANTED_BOOK,
        GuiI18n.tr(p, "gui.upgrades.index.header.title"),
        List.of(GuiI18n.tr(p, "gui.upgrades.index.header.hint",
            Placeholder.unparsed("count", String.valueOf(upgrades.registry().upgrades().size()))))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK,
        GuiI18n.tr(p, "gui.upgrades.index.refresh.title"),
        List.of(GuiI18n.tr(p, "gui.upgrades.index.refresh.hint"))), ctx -> {
      cacheValid = false;
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<UpgradeEntry> entries(Player player) {
    if (cacheValid) {
      return cachedEntries;
    }
    List<UpgradeEntry> out = new ArrayList<>();
    for (Map.Entry<String, UpgradeTemplate> entry : upgrades.registry().upgrades().entrySet()) {
      String id = entry.getKey();
      UpgradeTemplate template = entry.getValue();
      UpgradeSpec spec = template == null ? null : template.spec();
      String name = spec == null || spec.name() == null || spec.name().isBlank() ? id : spec.name();
      out.add(new UpgradeEntry(id, name, template));
    }
    out.sort(Comparator.comparing(UpgradeEntry::id, String.CASE_INSENSITIVE_ORDER));
    cachedEntries = out;
    cacheValid = true;
    return cachedEntries;
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
    UpgradeSpec spec = entry.template == null ? null : entry.template.spec();
    if (spec != null && !spec.spells().isEmpty()) {
      String activators = spec.spells().stream()
          .map(spell -> activatorLabel(player, spell.activator()))
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.joining(", "));
      lore.add(GuiI18n.tr(player, "gui.upgrades.index.entry.activator",
          Placeholder.unparsed("value", activators)));
    }
    lore.add(GuiI18n.tr(player, "gui.upgrades.index.entry.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private void viewOnly(Player player) {
    if (player == null) {
      return;
    }
    player.sendMessage(Locales.component(player, "messages.index.viewOnly"));
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
