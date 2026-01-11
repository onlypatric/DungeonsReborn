package dev.patric.dungeonsreborn.menus;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.crafting.CraftingGuiSession;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.components.storage.StorageSlot;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class CraftingRecipeEditorMenu extends Window {
  private static final int SIZE = 54;

  private static final int SLOT_TITLE = 4;
  private static final int SLOT_RESULT_LABEL = 16;
  private static final int SLOT_RECIPE_ID = 15;
  private static final int SLOT_RECIPE_NAME = 24;
  private static final int SLOT_RECIPE_DESC = 33;
  private static final int SLOT_LOAD = 32;
  private static final int SLOT_SAVE = 34;
  private static final int SLOT_CLEAR = 43;

  private final CraftingYamlRegistry registry;
  private final StorageArea inputs = new StorageArea(1, 1, 4, 4);
  private final StorageArea output = new StorageArea(2, 7, 1, 1);
  private final CraftingGuiSession sessionHandler = this::returnItems;

  private String recipeId = "";
  private String recipeName = "";
  private String recipeDescription = "";

  public CraftingRecipeEditorMenu(CraftingYamlRegistry registry, CraftingGuiSessionManager sessions) {
    super(SIZE, GuiMini.mm("<white><bold>Crafting Recipe Editor</bold></white>"), true);
    this.registry = registry;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Component.text("Close"))).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.WRITABLE_BOOK, GuiMini.mm("<gold><bold>Recipe Editor</bold></gold>"), List.of(
        GuiMini.mm("<gray>Fill the 4x4 grid with inputs.</gray>"),
        GuiMini.mm("<gray>Place output on the right.</gray>")))));

    setFixed(SLOT_RESULT_LABEL, new Label(GuiItems.named(Material.PAPER, GuiMini.mm("<aqua><bold>Output</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Place the crafted item here.</gray>")))));

    setFixed(SLOT_RECIPE_ID, recipeIdButton());
    setFixed(SLOT_RECIPE_NAME, recipeNameButton());
    setFixed(SLOT_RECIPE_DESC, recipeDescriptionButton());

    setFixed(SLOT_LOAD, new Button(this::loadButtonItem, ctx -> {
      if (registry == null) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(GuiMini.mm("<red>Crafting registry not available.</red>"));
        return;
      }
      GuiManager.get().push(ctx.player(), new CraftingRecipePickerMenu(registry, this));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    setFixed(SLOT_SAVE, new Button(this::saveButtonItem, ctx -> {
      if (!saveRecipe(ctx.player())) {
        GuiSounds.error(ctx.player());
        return;
      }
      GuiSounds.success(ctx.player());
      ctx.player().sendMessage(GuiMini.mm("<green>Recipe saved.</green>"));
      ctx.redraw();
    }).autoDescribeInLore(false));

    setFixed(SLOT_CLEAR, new Button(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Clear Inputs</bold></red>"), List.of(
        GuiMini.mm("<gray>Return items to your inventory.</gray>"))), ctx -> {
      returnItems(ctx.player());
      redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    configureInputs();
    configureOutput();
    inputs.applyFixed(this);
    output.applyFixed(this);

    inputs.onChange((player, index, stack) -> redrawSlot(player, SLOT_SAVE));
    output.onChange((player, index, stack) -> redrawSlot(player, SLOT_SAVE));

    onOpenWithReason(ctx -> {
      if (sessions != null) {
        sessions.register(ctx.player(), sessionHandler);
      }
      GuiSounds.open(ctx.player());
    });
    onCloseWithReason(ctx -> {
      if (sessions != null) {
        sessions.unregister(ctx.player(), sessionHandler);
      }
      returnItems(ctx.player());
      GuiSounds.close(ctx.player());
    });
  }

  private void configureInputs() {
    for (int i = 0; i < inputs.size(); i++) {
      StorageSlot slot = inputs.slot(i);
      slot.vanilla(true).accepts(stack -> true);
    }
  }

  private void configureOutput() {
    StorageSlot slot = output.slot(0);
    slot.vanilla(true).accepts(stack -> true);
  }

  private TextButton recipeIdButton() {
    return new TextButton(p -> recipeIdItem(p), GuiMini.mm("<yellow>Type recipe id</yellow>"), "cancel",
        Duration.ofSeconds(30), (window, text) -> {
      String normalized;
      try {
        normalized = Ids.normalize(text);
      } catch (IllegalArgumentException ex) {
        Player player = viewerPlayer(window);
        if (player != null) {
          player.sendMessage(GuiMini.mm("<red>" + ex.getMessage() + "</red>"));
        }
        return;
      }
      this.recipeId = normalized;
      Player player = viewerPlayer(window);
      if (player != null) {
        window.redraw(player);
      }
    }, true).minLength(1);
  }

  private TextButton recipeNameButton() {
    return new TextButton(p -> recipeNameItem(p), GuiMini.mm("<yellow>Type recipe name</yellow>"), "cancel",
        Duration.ofSeconds(30), (window, text) -> {
      this.recipeName = text.trim();
      Player player = viewerPlayer(window);
      if (player != null) {
        window.redraw(player);
      }
    }, true).maxLength(64);
  }

  private TextButton recipeDescriptionButton() {
    return new TextButton(p -> recipeDescriptionItem(p), GuiMini.mm("<yellow>Type recipe description</yellow>"), "cancel",
        Duration.ofSeconds(30), (window, text) -> {
      this.recipeDescription = text.trim();
      Player player = viewerPlayer(window);
      if (player != null) {
        window.redraw(player);
      }
    }, true).maxLength(120);
  }

  private ItemStack recipeIdItem(Player player) {
    String display = recipeId.isBlank() ? "<gray>unset</gray>" : "<white>" + recipeId + "</white>";
    return GuiItems.named(Material.NAME_TAG, GuiMini.mm("<gold><bold>Recipe ID</bold></gold>"), List.of(
        GuiMini.mm("<gray>Current:</gray> " + display),
        GuiMini.mm("<dark_gray>Type in chat to change.</dark_gray>")));
  }

  private ItemStack recipeNameItem(Player player) {
    String display = recipeName.isBlank() ? "<gray>unset</gray>" : "<white>" + recipeName + "</white>";
    return GuiItems.named(Material.OAK_SIGN, GuiMini.mm("<gold><bold>Recipe Name</bold></gold>"), List.of(
        GuiMini.mm("<gray>Current:</gray> " + display),
        GuiMini.mm("<dark_gray>Optional.</dark_gray>")));
  }

  private ItemStack recipeDescriptionItem(Player player) {
    String display = recipeDescription.isBlank() ? "<gray>unset</gray>" : "<white>" + recipeDescription + "</white>";
    return GuiItems.named(Material.BOOK, GuiMini.mm("<gold><bold>Description</bold></gold>"), List.of(
        GuiMini.mm("<gray>Current:</gray> " + display),
        GuiMini.mm("<dark_gray>Optional.</dark_gray>")));
  }

  private ItemStack loadButtonItem(Player player) {
    return GuiItems.named(Material.COMPASS, GuiMini.mm("<yellow><bold>Edit Recipes</bold></yellow>"), List.of(
        GuiMini.mm("<gray>Browse and load existing recipes.</gray>"),
        GuiMini.mm("<dark_gray>Right-click a recipe to delete.</dark_gray>")));
  }

  private ItemStack saveButtonItem(Player player) {
    boolean ready = isReady(player);
    Material mat = ready ? Material.LIME_DYE : Material.GRAY_DYE;
    String title = ready ? "<green><bold>Save Recipe</bold></green>" : "<dark_gray><bold>Save Recipe</bold></dark_gray>";
    List<Component> lore = new ArrayList<>();
    if (!ready) {
      lore.add(GuiMini.mm("<gray>Set recipe id, inputs, and output.</gray>"));
    } else {
      lore.add(GuiMini.mm("<gray>Writes a recipe file to disk.</gray>"));
    }
    return GuiItems.named(mat, GuiMini.mm(title), lore);
  }

  private boolean isReady(Player player) {
    if (registry == null) {
      return false;
    }
    if (recipeId == null || recipeId.isBlank()) {
      return false;
    }
    if (outputItem(player) == null) {
      return false;
    }
    return !inputsList(player).isEmpty();
  }

  private boolean loadRecipe(Player player) {
    if (registry == null) {
      player.sendMessage(GuiMini.mm("<red>Crafting registry not available.</red>"));
      return false;
    }
    if (recipeId == null || recipeId.isBlank()) {
      player.sendMessage(GuiMini.mm("<red>Recipe id is required.</red>"));
      return false;
    }
    var template = registry.recipeTemplate(recipeId);
    if (template == null) {
      player.sendMessage(GuiMini.mm("<red>Recipe not found.</red>"));
      return false;
    }
    var spec = template.spec();
    if (spec.variants().isEmpty()) {
      player.sendMessage(GuiMini.mm("<red>Recipe has no inputs.</red>"));
      return false;
    }
    if (spec.variants().size() > 1) {
      player.sendMessage(GuiMini.mm("<yellow>Multiple variants found; loading the first.</yellow>"));
    }
    var variant = spec.variants().get(0);
    List<ItemStack> stacks = new ArrayList<>();
    for (var ingredient : variant.inputs()) {
      ItemStack stack;
      switch (ingredient.type()) {
        case ITEM_ID -> {
          stack = registry.resolveItemTemplate(ingredient.itemId());
          if (stack == null || stack.getType().isAir()) {
            player.sendMessage(GuiMini.mm("<red>Missing item for id: " + ingredient.itemId() + "</red>"));
            return false;
          }
        }
        case MATERIAL -> {
          stack = new ItemStack(ingredient.material());
        }
        default -> {
          player.sendMessage(GuiMini.mm("<red>Unsupported ingredient type in editor: " + ingredient.type().name().toLowerCase() + "</red>"));
          return false;
        }
      }
      stack.setAmount(Math.max(1, ingredient.amount()));
      stacks.add(stack);
    }
    if (stacks.size() > inputs.size()) {
      player.sendMessage(GuiMini.mm("<red>Recipe has too many ingredients for the 4x4 grid.</red>"));
      return false;
    }
    inputs.clear(player);
    output.clear(player);
    for (int i = 0; i < stacks.size(); i++) {
      inputs.set(player, i, stacks.get(i));
    }
    if (spec.outputs().size() > 1) {
      player.sendMessage(GuiMini.mm("<yellow>Multiple outputs found; loading the first.</yellow>"));
    }
    ItemStack outputTemplate = template.outputTemplate();
    if (outputTemplate == null || outputTemplate.getType().isAir()) {
      player.sendMessage(GuiMini.mm("<red>Recipe output is missing.</red>"));
      return false;
    }
    int amount = Math.max(1, spec.outputs().get(0).amount());
    ItemStack outputStack = outputTemplate.clone();
    outputStack.setAmount(amount);
    output.set(player, 0, outputStack);
    recipeName = spec.name();
    recipeDescription = spec.description();
    return true;
  }

  public boolean loadRecipeById(Player player, String id) {
    if (id == null || id.isBlank()) {
      player.sendMessage(GuiMini.mm("<red>Recipe id is required.</red>"));
      return false;
    }
    this.recipeId = id.trim();
    boolean loaded = loadRecipe(player);
    if (loaded) {
      redraw(player);
    }
    return loaded;
  }

  private boolean saveRecipe(Player player) {
    Objects.requireNonNull(player, "player");
    if (registry == null) {
      player.sendMessage(GuiMini.mm("<red>Crafting registry not available.</red>"));
      return false;
    }
    if (recipeId == null || recipeId.isBlank()) {
      player.sendMessage(GuiMini.mm("<red>Recipe id is required.</red>"));
      return false;
    }
    ItemStack outputItem = outputItem(player);
    if (outputItem == null || outputItem.getType().isAir()) {
      player.sendMessage(GuiMini.mm("<red>Output item is required.</red>"));
      return false;
    }
    List<Map<String, Object>> inputs = inputsList(player);
    if (inputs.isEmpty()) {
      player.sendMessage(GuiMini.mm("<red>Add at least one input item.</red>"));
      return false;
    }

    YamlConfiguration cfg = new YamlConfiguration();
    cfg.set("schemaVersion", 1);
    cfg.set("id", recipeId);
    if (!recipeName.isBlank()) {
      cfg.set("name", recipeName);
    }
    if (!recipeDescription.isBlank()) {
      cfg.set("description", recipeDescription);
    }
    cfg.set("inputs", inputs);
    cfg.set("output", outputMap(outputItem));

    File dir = registry.recipesDir();
    dir.mkdirs();
    File file = new File(dir, recipeId + ".yml");
    try {
      cfg.save(file);
    } catch (IOException ex) {
      player.sendMessage(GuiMini.mm("<red>Failed to save: " + ex.getMessage() + "</red>"));
      return false;
    }
    registry.reload();
    return true;
  }

  private ItemStack outputItem(Player player) {
    ItemStack stored = output.get(player, 0);
    if (stored == null || stored.getType().isAir()) {
      return null;
    }
    return stored.clone();
  }

  private List<Map<String, Object>> inputsList(Player player) {
    ItemStack[] contents = inputs.contents(player);
    List<Map<String, Object>> list = new ArrayList<>();
    for (ItemStack stack : contents) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      String itemId = ItemMarkers.getItemId(stack);
      if (itemId != null && !itemId.isBlank()) {
        entry.put("type", "item_id");
        entry.put("item", itemId.trim());
      } else {
        entry.put("type", "material");
        entry.put("material", stack.getType().name());
      }
      entry.put("amount", Math.max(1, stack.getAmount()));
      list.add(entry);
    }
    return list;
  }

  private Map<String, Object> outputMap(ItemStack outputItem) {
    Map<String, Object> out = new LinkedHashMap<>();
    String itemId = ItemMarkers.getItemId(outputItem);
    int amount = Math.max(1, outputItem.getAmount());
    if (itemId != null && !itemId.isBlank()) {
      out.put("itemId", itemId.trim());
      out.put("amount", amount);
      return out;
    }
    ItemStack copy = outputItem.clone();
    copy.setAmount(amount);
    out.put("itemStack", copy);
    out.put("amount", amount);
    return out;
  }

  private void returnItems(Player player) {
    for (ItemStack stack : inputs.contents(player)) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      var leftovers = player.getInventory().addItem(stack);
      if (!leftovers.isEmpty()) {
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      }
    }
    ItemStack out = output.get(player, 0);
    if (out != null && !out.getType().isAir()) {
      var leftovers = player.getInventory().addItem(out);
      if (!leftovers.isEmpty()) {
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      }
    }
    inputs.clear(player);
    output.clear(player);
  }

  private Player viewerPlayer(Window window) {
    if (window.viewer() == null) {
      return null;
    }
    return Bukkit.getPlayer(window.viewer());
  }
}
