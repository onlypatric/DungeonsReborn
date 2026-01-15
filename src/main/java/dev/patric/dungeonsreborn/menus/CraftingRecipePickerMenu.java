package dev.patric.dungeonsreborn.menus;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class CraftingRecipePickerMenu extends Window {
  private static final int SIZE = 54;
  private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(20);
  private static final String DELETE_WORD = "delete";

  private record Entry(CraftingRecipeTemplate recipe) {
  }

  private final CraftingYamlRegistry registry;
  private final CraftingRecipeEditorMenu editor;
  private final VirtualList<Entry> list;

  public CraftingRecipePickerMenu(CraftingYamlRegistry registry, CraftingRecipeEditorMenu editor) {
    super(SIZE, GuiI18n.tr("gui.crafting.picker.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.editor = Objects.requireNonNull(editor, "editor");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> handleClick(ctx, entry));
    list.searchKey(entry -> entryTitle(entry) + " " + entry.recipe().spec().id());
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.backButton());
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.BOOK, GuiI18n.tr("gui.crafting.picker.header.title"), List.of(
        GuiI18n.tr("gui.crafting.picker.header.edit"),
        GuiI18n.tr("gui.crafting.picker.header.delete")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<Entry> entries(Player player) {
    List<Entry> entries = new ArrayList<>();
    for (CraftingRecipeTemplate template : registry.recipes().values()) {
      entries.add(new Entry(template));
    }
    entries.sort(Comparator.comparing(entry -> entryTitle(entry).toLowerCase(Locale.ROOT)));
    return entries;
  }

  private void handleClick(Window.ClickContext ctx, Entry entry) {
    if (ctx.clickType() == ClickType.RIGHT || ctx.clickType() == ClickType.SHIFT_RIGHT) {
      confirmDelete(ctx.player(), entry.recipe());
      return;
    }
    boolean loaded = editor.loadRecipeById(ctx.player(), entry.recipe().spec().id());
    if (!loaded) {
      GuiSounds.error(ctx.player());
      return;
    }
    GuiSounds.click(ctx.player());
    ctx.close();
  }

  private ItemStack entryItem(Entry entry) {
    CraftingRecipeTemplate recipe = entry.recipe();
    ItemStack output = recipe.outputTemplate();
    ItemStack base = output != null ? output.clone() : new ItemStack(Material.PAPER);
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr("gui.crafting.picker.lore.id", Placeholder.unparsed("id", recipe.spec().id())));
    if (!recipe.spec().description().isBlank()) {
      lore.add(GuiI18n.tr("gui.crafting.picker.lore.description",
          Placeholder.unparsed("text", recipe.spec().description())));
    }
    lore.add(GuiI18n.tr("gui.crafting.picker.lore.edit"));
    lore.add(GuiI18n.tr("gui.crafting.picker.lore.delete"));
    return GuiItem.of(base)
        .displayName(GuiI18n.tr("gui.crafting.picker.entry.title",
            Placeholder.unparsed("title", entryTitle(entry))))
        .lore(lore)
        .build();
  }

  private String entryTitle(Entry entry) {
    String name = entry.recipe().spec().name();
    if (name == null || name.isBlank()) {
      return entry.recipe().spec().id();
    }
    return name;
  }

  private void confirmDelete(Player player, CraftingRecipeTemplate recipe) {
    GuiManager.get().prepareTemporaryClose(player);
    player.closeInventory();
    Component prompt = GuiI18n.tr("gui.crafting.picker.delete.prompt",
        Placeholder.unparsed("word", DELETE_WORD),
        Placeholder.unparsed("id", recipe.spec().id()));
    GuiManager.get().requestText(player, new GuiManager.TextRequest(
        prompt,
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        DELETE_TIMEOUT,
        (p, text) -> {
          if (!DELETE_WORD.equalsIgnoreCase(text.trim())) {
            p.sendMessage(GuiI18n.tr(p, "gui.crafting.picker.delete.cancelled"));
            GuiManager.get().resume(p, this, "delete-cancel");
            return;
          }
          if (!deleteRecipe(recipe.spec().id())) {
            p.sendMessage(GuiI18n.tr(p, "gui.crafting.picker.delete.failed"));
          } else {
            p.sendMessage(GuiI18n.tr(p, "gui.crafting.picker.delete.success"));
          }
          registry.reload();
          GuiManager.get().resume(p, this, "delete");
        },
        p -> GuiManager.get().resume(p, this, "delete-cancel"),
        p -> GuiManager.get().resume(p, this, "delete-timeout")
    ));
  }

  private boolean deleteRecipe(String id) {
    File dir = registry.recipesDir();
    File file = new File(dir, id + ".yml");
    if (!file.exists()) {
      File alt = new File(dir, id + ".yaml");
      if (alt.exists()) {
        file = alt;
      }
    }
    return file.exists() && file.delete();
  }
}
