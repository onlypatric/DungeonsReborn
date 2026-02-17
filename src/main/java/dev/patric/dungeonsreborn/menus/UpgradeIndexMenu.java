package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.upgrades.UpgradeService;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeTemplate;
import dev.patric.dungeonsreborn.gui.GuiDebug;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class UpgradeIndexMenu extends Window {
  private final UpgradeService upgrades;
  private final VirtualList<UpgradeTemplate> list;
  private final boolean allowGive;
  private boolean debugLogged;

  public static void open(Player player, UpgradeService upgrades) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new UpgradeIndexMenu(upgrades, false));
  }

  public static void openAdmin(Player player, UpgradeService upgrades) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new UpgradeIndexMenu(upgrades, true));
  }

  public UpgradeIndexMenu(UpgradeService upgrades) {
    this(upgrades, false);
  }

  public UpgradeIndexMenu(UpgradeService upgrades, boolean allowGive) {
    super(54, GuiI18n.tr("gui.upgrades.title"));
    this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
    this.allowGive = allowGive;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> upgradeEntries(),
        this::renderEntry,
        this::handleEntryClick);
    this.list.searchKey(this::upgradeSearchKey);
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<UpgradeTemplate> upgradeEntries() {
    List<UpgradeTemplate> entries = new ArrayList<>(upgrades.registry().upgrades().values());
    debugLogged = GuiDebug.logIndexOnce(debugLogged, "upgrades", entries.size());
    entries.sort(Comparator.comparing(template -> template.spec().name().toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderEntry(Player player, UpgradeTemplate template) {
    if (template == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    Component title = titleComponent(template);
    List<Component> lore = new ArrayList<>();
    if (template.spec().description() != null && !template.spec().description().isBlank()) {
      lore.add(GuiMini.mm(template.spec().description()));
    }
    if (allowGive && player.hasPermission("dungeonsreborn.upgrades.give")) {
      lore.add(GuiI18n.tr(player, "gui.upgrades.entry.giveOne"));
      lore.add(GuiI18n.tr(player, "gui.upgrades.entry.giveStack"));
      lore.add(GuiI18n.tr(player, "gui.upgrades.entry.inspectHint"));
    }
    ItemStack item = template.buildItem();
    return GuiItems.named(item, title, lore, true);
  }

  private String upgradeSearchKey(UpgradeTemplate template) {
    if (template == null) {
      return "";
    }
    return PlainTextComponentSerializer.plainText().serialize(titleComponent(template));
  }

  private void handleEntryClick(Window.ClickContext ctx, UpgradeTemplate template) {
    if (template == null) {
      return;
    }
    if (allowGive && ctx.player().hasPermission("dungeonsreborn.upgrades.give")) {
      if (ctx.clickType().isLeftClick()) {
        int amount = ctx.isShiftClick() ? 64 : 1;
        ItemStack item = upgrades.registry().upgradeItem(template.spec().id());
        if (item != null) {
          item.setAmount(amount);
          giveToPlayer(ctx.player(), item);
        }
        return;
      }
    }
    ctx.window().openSubWindow(ctx.player(), new UpgradeInspectMenu(upgrades, template));
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.upgrades.header",
        Placeholder.unparsed("count", String.valueOf(upgrades.registry().upgrades().size())));
    return GuiItems.head("ICON_UPGRADES", title, List.of());
  }

  private Component titleComponent(UpgradeTemplate template) {
    String titleText = template.spec().name().isBlank() ? template.spec().id() : template.spec().name();
    return GuiMini.mm(titleText);
  }

  private static void giveToPlayer(Player player, ItemStack item) {
    var leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
      }
    }
  }
}
