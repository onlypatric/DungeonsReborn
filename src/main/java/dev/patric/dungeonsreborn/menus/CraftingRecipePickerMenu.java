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
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

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
    super(SIZE, GuiMini.mm("<white><bold>Select Recipe</bold></white>"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.editor = Objects.requireNonNull(editor, "editor");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> handleClick(ctx, entry));
    list.searchKey(entry -> entryTitle(entry) + " " + entry.recipe().spec().id());
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, searchButton());
    nav(4, clearSearchButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.BOOK, GuiMini.mm("<gold><bold>Recipes</bold></gold>"), List.of(
        GuiMini.mm("<gray>Left click to edit.</gray>"),
        GuiMini.mm("<gray>Right click to delete.</gray>")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private TextButton searchButton() {
    return new TextButton(
        p -> GuiItems.named(Material.SPYGLASS, GuiMini.mm("<aqua><bold>Search</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Type a search term in chat.</gray>"))),
        GuiMini.mm("<yellow>Type search text</yellow>"),
        "cancel",
        Duration.ofSeconds(30),
        (window, text) -> {
          Player player = window.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(window.viewer());
          if (player == null) {
            return;
          }
          list.query(player, text);
          window.redraw(player);
        },
        true);
  }

  private Button clearSearchButton() {
    return new Button(
        p -> GuiItems.named(Material.MILK_BUCKET, GuiMini.mm("<gray><bold>Clear Search</bold></gray>"), List.of(
            GuiMini.mm("<gray>Reset the search filter.</gray>"))),
        ctx -> {
          list.clearFilter(ctx.player());
          ctx.redraw();
        }).autoDescribeInLore(false);
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
    lore.add(GuiMini.mm("<gray>ID:</gray> <white>" + recipe.spec().id() + "</white>"));
    if (!recipe.spec().description().isBlank()) {
      lore.add(GuiMini.mm("<dark_gray>" + recipe.spec().description() + "</dark_gray>"));
    }
    lore.add(GuiMini.mm("<green>Left click to edit.</green>"));
    lore.add(GuiMini.mm("<red>Right click to delete.</red>"));
    return GuiItem.of(base)
        .displayName(GuiMini.mm("<yellow>" + entryTitle(entry) + "</yellow>"))
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
    Component prompt = GuiMini.mm("<red>Type <white>" + DELETE_WORD + "</white> to delete <yellow>" + recipe.spec().id() + "</yellow>.</red>");
    GuiManager.get().requestText(player, new GuiManager.TextRequest(
        prompt,
        "cancel",
        DELETE_TIMEOUT,
        (p, text) -> {
          if (!DELETE_WORD.equalsIgnoreCase(text.trim())) {
            p.sendMessage(GuiMini.mm("<gray>Delete cancelled.</gray>"));
            GuiManager.get().resume(p, this, "delete-cancel");
            return;
          }
          if (!deleteRecipe(recipe.spec().id())) {
            p.sendMessage(GuiMini.mm("<red>Failed to delete recipe.</red>"));
          } else {
            p.sendMessage(GuiMini.mm("<green>Recipe deleted.</green>"));
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
