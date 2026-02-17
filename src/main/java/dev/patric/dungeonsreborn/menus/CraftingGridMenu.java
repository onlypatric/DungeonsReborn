package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSession;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingMatcher;
import dev.patric.dungeonsreborn.crafting.CraftingMatchResult;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class CraftingGridMenu extends Window implements CraftingGuiSession {
  private static final int OUTPUT_ROW = 2;
  private static final int OUTPUT_COL = 5;

  private final CraftingYamlRegistry registry;
  private final CraftingDiscoveryService discovery;
  private final CraftingGuiSessionManager sessions;
  private final StorageArea grid;
  private final boolean showAll;

  public static void open(Player player, CraftingYamlRegistry registry, CraftingDiscoveryService discovery,
      CraftingGuiSessionManager sessions, boolean showAll) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new CraftingGridMenu(registry, discovery, sessions, showAll));
  }

  public CraftingGridMenu(CraftingYamlRegistry registry, CraftingDiscoveryService discovery,
      CraftingGuiSessionManager sessions, boolean showAll) {
    super(54, GuiI18n.tr("gui.crafting.grid.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.discovery = Objects.requireNonNull(discovery, "discovery");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.showAll = showAll;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.grid = new StorageArea(1, 1, 3, 3)
        .onChange((player, index, stack) -> redrawSlot(player, slotAt(OUTPUT_ROW, OUTPUT_COL)));
    for (int i = 0; i < grid.size(); i++) {
      grid.slot(i).vanilla(true);
    }
    grid.apply(this, Placement.FIXED);

    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    setFixedAt(0, 4, new Label(this::headerItem));
    setFixedAt(OUTPUT_ROW, OUTPUT_COL, new Label(this::outputItem));
    setFixedAt(4, 1, recipesButton());
    setFixedAt(4, 2, clearButton());
    setFixedAt(4, 5, craftButton());

    onOpen(player -> sessions.register(player, this));
    onClose(player -> {
      returnItems(player);
      sessions.unregister(player, this);
    });
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.crafting.grid.header");
    Component desc = GuiI18n.tr(player, "gui.crafting.grid.desc");
    return GuiItems.head("ICON_CRAFTING", title, List.of(desc));
  }

  private ItemStack outputItem(Player player) {
    CraftingMatchResult match = match(player);
    if (match == null) {
      return GuiItems.named(Material.BARRIER, GuiI18n.tr(player, "gui.crafting.grid.noMatch"),
          List.of(GuiI18n.tr(player, "gui.crafting.grid.noMatch.desc")));
    }
    CraftingRecipeSpec spec = match.recipe().spec();
    if (!discovery.isAvailable(player, spec)) {
      return GuiItems.named(Material.GRAY_STAINED_GLASS_PANE,
          GuiI18n.tr(player, "gui.crafting.grid.locked"),
          List.of(GuiI18n.tr(player, "gui.crafting.grid.locked.desc")));
    }
    ItemStack output = match.recipe().outputTemplate();
    if (output == null) {
      return GuiItems.head("ICON_CRAFTING", GuiI18n.tr(player, "gui.crafting.grid.output"), List.of());
    }
    ItemMeta meta = output.getItemMeta();
    if (meta != null) {
      output.setItemMeta(meta);
    }
    return GuiItems.named(output, GuiI18n.tr(player, "gui.crafting.grid.output"), List.of(), true);
  }

  private Button recipesButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.INFO,
        GuiI18n.tr(player, "gui.crafting.grid.recipes.title"),
        List.of(GuiI18n.tr(player, "gui.crafting.grid.recipes.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      ctx.window().openSubWindow(ctx.player(), new CraftingAvailableMenu(registry, discovery));
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private Button clearButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CANCEL,
        GuiI18n.tr(player, "gui.crafting.grid.clear.title"),
        List.of(GuiI18n.tr(player, "gui.crafting.grid.clear.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      Player player = ctx.player();
      grid.clear(player);
      ctx.window().redraw(player);
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private Button craftButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CONFIRM,
        GuiI18n.tr(player, "gui.crafting.grid.craft.title"),
        List.of(GuiI18n.tr(player, "gui.crafting.grid.craft.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      Player player = ctx.player();
      CraftingMatchResult match = match(player);
      if (match == null) {
        player.sendMessage(Locales.component(player, "messages.gui.crafting.test.invalid"));
        return;
      }
      CraftingRecipeSpec spec = match.recipe().spec();
      if (!discovery.isAvailable(player, spec)) {
        player.sendMessage(Locales.component(player, "messages.gui.crafting.test.locked"));
        return;
      }
      if (!consumeMatch(player, match)) {
        player.sendMessage(Locales.component(player, "messages.gui.crafting.test.missing"));
        return;
      }
      grantOutputs(player, match.recipe());
      discovery.unlockFromCraft(player, spec);
      ctx.window().redraw(player);
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private CraftingMatchResult match(Player player) {
    ItemStack[] inputs = grid.contents(player);
    return CraftingMatcher.match(inputs, registry.recipes().values());
  }

  private boolean consumeMatch(Player player, CraftingMatchResult match) {
    ItemStack[] contents = grid.contents(player);
    Map<Integer, Integer> consumed = match.consumed();
    for (var entry : consumed.entrySet()) {
      int slot = entry.getKey();
      int amount = entry.getValue();
      if (slot < 0 || slot >= contents.length) {
        continue;
      }
      ItemStack stack = contents[slot];
      if (stack == null || stack.getType().isAir()) {
        return false;
      }
      if (stack.getAmount() < amount) {
        return false;
      }
      stack.setAmount(stack.getAmount() - amount);
      if (stack.getAmount() <= 0) {
        contents[slot] = null;
      }
    }
    for (int i = 0; i < contents.length; i++) {
      grid.set(player, i, contents[i]);
    }
    return true;
  }

  private void grantOutputs(Player player, CraftingRecipeTemplate recipe) {
    if (recipe == null) {
      return;
    }
    List<ItemStack> outputs = recipe.outputTemplates();
    if (outputs == null || outputs.isEmpty()) {
      return;
    }
    for (ItemStack output : outputs) {
      if (output == null) {
        continue;
      }
      ItemStack clone = output.clone();
      var leftovers = player.getInventory().addItem(clone);
      if (!leftovers.isEmpty()) {
        for (ItemStack stack : leftovers.values()) {
          player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
      }
    }
  }

  private void returnItems(Player player) {
    ItemStack[] contents = grid.contents(player);
    List<ItemStack> leftovers = new ArrayList<>();
    for (ItemStack stack : contents) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      leftovers.add(stack);
    }
    grid.clear(player);
    for (ItemStack stack : leftovers) {
      var remaining = player.getInventory().addItem(stack);
      if (!remaining.isEmpty()) {
        for (ItemStack leftover : remaining.values()) {
          player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
      }
    }
  }

  @Override
  public void onDisconnect(Player player) {
    returnItems(player);
  }
}
