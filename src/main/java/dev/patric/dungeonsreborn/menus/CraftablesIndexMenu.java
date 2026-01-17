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

import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
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

public final class CraftablesIndexMenu extends Window {
  private static final int SIZE = 54;

  private record CraftEntry(String id, String name, CraftingRecipeTemplate template) {
  }

  private final CraftingYamlRegistry crafting;
  private final VirtualList<CraftEntry> list;
  private List<CraftEntry> cachedEntries = List.of();
  private boolean cacheValid;

  public CraftablesIndexMenu(CraftingYamlRegistry crafting) {
    super(SIZE, GuiI18n.tr("gui.craftables.index.title"), true);
    this.crafting = Objects.requireNonNull(crafting, "crafting");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        this::entryItem,
        (ctx, entry) -> openDetails(ctx.player(), entry));
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
    return new Label(p -> GuiItems.named(Material.ANVIL,
        GuiI18n.tr(p, "gui.craftables.index.header.title"),
        List.of(GuiI18n.tr(p, "gui.craftables.index.header.hint",
            Placeholder.unparsed("count", String.valueOf(crafting.recipes().size()))))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK,
        GuiI18n.tr(p, "gui.craftables.index.refresh.title"),
        List.of(GuiI18n.tr(p, "gui.craftables.index.refresh.hint"))), ctx -> {
      cacheValid = false;
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<CraftEntry> entries(Player player) {
    if (cacheValid) {
      return cachedEntries;
    }
    List<CraftEntry> out = new ArrayList<>();
    for (Map.Entry<String, CraftingRecipeTemplate> entry : crafting.recipes().entrySet()) {
      String id = entry.getKey();
      CraftingRecipeTemplate template = entry.getValue();
      CraftingRecipeSpec spec = template == null ? null : template.spec();
      String name = spec == null || spec.name().isBlank() ? id : spec.name();
      out.add(new CraftEntry(id, name, template));
    }
    out.sort(Comparator.comparing(CraftEntry::id, String.CASE_INSENSITIVE_ORDER));
    cachedEntries = out;
    cacheValid = true;
    return cachedEntries;
  }

  private ItemStack entryItem(Player player, CraftEntry entry) {
    ItemStack item = entry.template == null || entry.template.outputTemplates().isEmpty()
        ? new ItemStack(Material.PAPER)
        : entry.template.outputTemplates().get(0).clone();
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
    lore.add(GuiI18n.tr(player, "gui.craftables.index.entry.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private void openDetails(Player player, CraftEntry entry) {
    if (player == null) {
      return;
    }
    if (entry == null || entry.template == null) {
      player.sendMessage(Locales.component(player, "messages.index.viewOnly"));
      GuiSounds.error(player);
      return;
    }
    new CraftableDetailMenu(crafting, entry.template).open(player);
    GuiSounds.click(player);
  }
}
