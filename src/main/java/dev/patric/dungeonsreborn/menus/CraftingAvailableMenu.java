package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingInventoryPlanner;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeVariant;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class CraftingAvailableMenu extends Window {
  private final CraftingYamlRegistry registry;
  private final CraftingDiscoveryService discovery;
  private final VirtualList<CraftingRecipeTemplate> list;

  public static void open(Player player, CraftingYamlRegistry registry, CraftingDiscoveryService discovery) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new CraftingAvailableMenu(registry, discovery));
  }

  public CraftingAvailableMenu(CraftingYamlRegistry registry, CraftingDiscoveryService discovery) {
    super(54, GuiI18n.tr("gui.crafting.available.title"));
    this.registry = Objects.requireNonNull(registry, "registry");
    this.discovery = Objects.requireNonNull(discovery, "discovery");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        this::visibleRecipes,
        this::renderEntry,
        this::openRecipe);
    this.list.searchKey(entry -> entry == null ? "" : entry.spec().name());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<CraftingRecipeTemplate> visibleRecipes(Player player) {
    List<CraftingRecipeTemplate> entries = new ArrayList<>();
    for (CraftingRecipeTemplate template : registry.recipes().values()) {
      if (template == null) {
        continue;
      }
      CraftingRecipeSpec spec = template.spec();
      if (!discovery.isVisible(player, spec)) {
        continue;
      }
      CraftingRecipeVariant variant = spec.variants().isEmpty() ? null : spec.variants().get(0);
      if (variant == null) {
        continue;
      }
      if (CraftingInventoryPlanner.plan(player.getInventory().getContents(), variant) == null) {
        continue;
      }
      entries.add(template);
    }
    entries.sort(Comparator.comparing(template -> template.spec().name().toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderEntry(Player player, CraftingRecipeTemplate template) {
    if (template == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    CraftingRecipeSpec spec = template.spec();
    String titleText = spec.name().isBlank() ? spec.id() : spec.name();
    Component title = Component.text(titleText);
    List<Component> lore = new ArrayList<>();
    if (!spec.description().isBlank()) {
      lore.add(GuiMini.mm(spec.description()));
    }
    lore.add(GuiI18n.tr(player, "gui.crafting.available.ready"));

    ItemStack output = template.outputTemplate();
    if (output == null) {
      return GuiItems.head("ICON_CRAFTING", title, lore);
    }
    ItemMeta meta = output.getItemMeta();
    if (meta != null) {
      output.setItemMeta(meta);
    }
    return GuiItems.named(output, title, lore, true);
  }

  private void openRecipe(Window.ClickContext ctx, CraftingRecipeTemplate template) {
    if (template == null) {
      return;
    }
    ctx.window().openSubWindow(ctx.player(), new CraftingTestMenu(discovery, template, true));
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.crafting.available.header");
    Component desc = GuiI18n.tr(player, "gui.crafting.available.desc");
    return GuiItems.head("ICON_CRAFTING", title, List.of(desc));
  }
}
