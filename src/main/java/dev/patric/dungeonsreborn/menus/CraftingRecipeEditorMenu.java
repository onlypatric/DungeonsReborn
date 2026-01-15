package dev.patric.dungeonsreborn.menus;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import dev.patric.dungeonsreborn.admin.AdminAuditStore;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
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
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

public final class CraftingRecipeEditorMenu extends Window {
  private static final int SIZE = 54;
  private static final DateTimeFormatter AUDIT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private static final int SLOT_TITLE = 4;
  private static final int SLOT_RESULT_LABEL = 16;
  private static final int SLOT_RECIPE_ID = 15;
  private static final int SLOT_RECIPE_NAME = 24;
  private static final int SLOT_RECIPE_DESC = 33;
  private static final int SLOT_LOAD = 32;
  private static final int SLOT_SAVE = 34;
  private static final int SLOT_APPLY = 41;
  private static final int SLOT_CLEAR = 43;
  private static final int SLOT_SUMMARY = 52;
  private static final int SLOT_DIRTY = 7;

  private final CraftingYamlRegistry registry;
  private final StorageArea inputs = new StorageArea(1, 1, 4, 4);
  private final StorageArea output = new StorageArea(2, 7, 1, 1);
  private final CraftingGuiSession sessionHandler = this::returnItems;

  private String recipeId = "";
  private String recipeName = "";
  private String recipeDescription = "";
  private boolean dirty;

  public CraftingRecipeEditorMenu(CraftingYamlRegistry registry, CraftingGuiSessionManager sessions) {
    super(SIZE, GuiI18n.tr("gui.crafting.editor.title"), true);
    this.registry = registry;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));
    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.WRITABLE_BOOK, GuiI18n.tr("gui.crafting.editor.header.title"), List.of(
        GuiI18n.tr("gui.crafting.editor.header.hint1"),
        GuiI18n.tr("gui.crafting.editor.header.hint2")))));

    setFixed(SLOT_RESULT_LABEL, new Label(GuiItems.named(Material.PAPER, GuiI18n.tr("gui.crafting.editor.output.title"), List.of(
        GuiI18n.tr("gui.crafting.editor.output.hint")))));

    setFixed(SLOT_DIRTY, new Label(p -> dirtyIndicatorItem()));
    setFixedAt(0, 6, new Label(p -> auditItem()));
    setFixed(SLOT_RECIPE_ID, recipeIdButton());
    setFixed(SLOT_RECIPE_NAME, recipeNameButton());
    setFixed(SLOT_RECIPE_DESC, recipeDescriptionButton());
    setFixed(SLOT_SUMMARY, new Label(p -> summaryItem(p)));

    setFixed(SLOT_LOAD, new Button(this::loadButtonItem, ctx -> {
      if (registry == null) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(Locales.component(ctx.player(), "messages.crafting.editor.registryMissing"));
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
      dirty = false;
      redrawSlot(ctx.player(), SLOT_DIRTY);
      GuiSounds.success(ctx.player());
      ctx.player().sendMessage(Locales.component(ctx.player(), "messages.crafting.editor.saved"));
      ctx.redraw();
    }).autoDescribeInLore(false));

    setFixed(SLOT_APPLY, new Button(p -> GuiItems.named(Material.LIME_DYE, GuiI18n.tr(p, "gui.crafting.editor.apply.title"), List.of(
        GuiI18n.tr(p, "gui.crafting.editor.apply.hint"))), ctx -> {
      if (registry != null) {
        registry.reload();
      }
      dirty = false;
      redrawSlot(ctx.player(), SLOT_DIRTY);
      ctx.player().sendMessage(Locales.component(ctx.player(), "messages.crafting.editor.reloaded"));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    setFixed(SLOT_CLEAR, new Button(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.crafting.editor.clear.title"), List.of(
        GuiI18n.tr(p, "gui.crafting.editor.clear.hint"))), ctx -> {
      returnItems(ctx.player());
      redraw(ctx.player());
      markDirty(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    configureInputs();
    configureOutput();
    inputs.applyFixed(this);
    output.applyFixed(this);

    inputs.onChange((player, index, stack) -> {
      markDirty(player);
      redrawSlot(player, SLOT_SAVE);
      redrawSlot(player, SLOT_SUMMARY);
    });
    output.onChange((player, index, stack) -> {
      markDirty(player);
      redrawSlot(player, SLOT_SAVE);
      redrawSlot(player, SLOT_SUMMARY);
    });

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
    return new TextButton(p -> recipeIdItem(p), GuiI18n.tr("gui.crafting.editor.id.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30), (window, text) -> {
      String normalized;
      try {
        normalized = Ids.normalize(text);
      } catch (IllegalArgumentException ex) {
        Player player = viewerPlayer(window);
        if (player != null) {
          player.sendMessage(Locales.component(player, "messages.crafting.editor.invalidId",
              Locales.placeholders("message", ex.getMessage())));
        }
        return;
      }
      this.recipeId = normalized;
      Player player = viewerPlayer(window);
      if (player != null) {
        markDirty(player);
        window.redraw(player);
      }
    }, true).minLength(1);
  }

  private TextButton recipeNameButton() {
    return new TextButton(p -> recipeNameItem(p), GuiI18n.tr("gui.crafting.editor.name.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30), (window, text) -> {
      this.recipeName = text.trim();
      Player player = viewerPlayer(window);
      if (player != null) {
        markDirty(player);
        window.redraw(player);
      }
    }, true).maxLength(64);
  }

  private TextButton recipeDescriptionButton() {
    return new TextButton(p -> recipeDescriptionItem(p), GuiI18n.tr("gui.crafting.editor.description.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30), (window, text) -> {
      this.recipeDescription = text.trim();
      Player player = viewerPlayer(window);
      if (player != null) {
        markDirty(player);
        window.redraw(player);
      }
    }, true).maxLength(120);
  }

  private ItemStack recipeIdItem(Player player) {
    String display = recipeId.isBlank() ? Locales.text(player, "gui.common.unset") : "<white>" + recipeId + "</white>";
    return GuiItems.named(Material.NAME_TAG, GuiI18n.tr(player, "gui.crafting.editor.id.title"), List.of(
        Locales.component(player, "gui.crafting.editor.field.current", Locales.placeholders("value", display)),
        GuiI18n.tr(player, "gui.crafting.editor.id.hint")));
  }

  private ItemStack recipeNameItem(Player player) {
    String display = recipeName.isBlank() ? Locales.text(player, "gui.common.unset") : "<white>" + recipeName + "</white>";
    return GuiItems.named(Material.OAK_SIGN, GuiI18n.tr(player, "gui.crafting.editor.name.title"), List.of(
        Locales.component(player, "gui.crafting.editor.field.current", Locales.placeholders("value", display)),
        GuiI18n.tr(player, "gui.crafting.editor.name.hint")));
  }

  private ItemStack recipeDescriptionItem(Player player) {
    String display = recipeDescription.isBlank() ? Locales.text(player, "gui.common.unset") : "<white>" + recipeDescription + "</white>";
    return GuiItems.named(Material.BOOK, GuiI18n.tr(player, "gui.crafting.editor.description.title"), List.of(
        Locales.component(player, "gui.crafting.editor.field.current", Locales.placeholders("value", display)),
        GuiI18n.tr(player, "gui.crafting.editor.description.hint")));
  }

  private ItemStack loadButtonItem(Player player) {
    return GuiItems.named(Material.COMPASS, GuiI18n.tr(player, "gui.crafting.editor.load.title"), List.of(
        GuiI18n.tr(player, "gui.crafting.editor.load.hint1"),
        GuiI18n.tr(player, "gui.crafting.editor.load.hint2")));
  }

  private ItemStack saveButtonItem(Player player) {
    boolean ready = isReady(player);
    Material mat = ready ? Material.LIME_DYE : Material.GRAY_DYE;
    Component title = GuiI18n.tr(player, ready ? "gui.crafting.editor.save.title" : "gui.crafting.editor.save.titleDisabled");
    List<Component> lore = new ArrayList<>();
    if (!ready) {
      lore.add(GuiI18n.tr(player, "gui.crafting.editor.save.hintMissing"));
    } else {
      lore.add(GuiI18n.tr(player, "gui.crafting.editor.save.hintReady"));
    }
    return GuiItems.named(mat, title, lore);
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
      player.sendMessage(Locales.component(player, "messages.crafting.editor.registryMissing"));
      return false;
    }
    if (recipeId == null || recipeId.isBlank()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.missingId"));
      return false;
    }
    var template = registry.recipeTemplate(recipeId);
    if (template == null) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.notFound"));
      return false;
    }
    var spec = template.spec();
    if (spec.variants().isEmpty()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.noInputs"));
      return false;
    }
    if (spec.variants().size() > 1) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.multipleVariants"));
    }
    var variant = spec.variants().get(0);
    List<ItemStack> stacks = new ArrayList<>();
    for (var ingredient : variant.inputs()) {
      ItemStack stack;
      switch (ingredient.type()) {
        case ITEM_ID -> {
          stack = registry.resolveItemTemplate(ingredient.itemId());
          if (stack == null || stack.getType().isAir()) {
            player.sendMessage(Locales.component(player, "messages.crafting.editor.missingItem",
                Locales.placeholders("id", ingredient.itemId())));
            return false;
          }
        }
        case MATERIAL -> {
          stack = new ItemStack(ingredient.material());
        }
        default -> {
          player.sendMessage(Locales.component(player, "messages.crafting.editor.unsupportedIngredient",
              Locales.placeholders("type", ingredient.type().name().toLowerCase(Locale.ROOT))));
          return false;
        }
      }
      stack.setAmount(Math.max(1, ingredient.amount()));
      stacks.add(stack);
    }
    if (stacks.size() > inputs.size()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.tooManyIngredients"));
      return false;
    }
    inputs.clear(player);
    output.clear(player);
    for (int i = 0; i < stacks.size(); i++) {
      inputs.set(player, i, stacks.get(i));
    }
    if (spec.outputs().size() > 1) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.multipleOutputs"));
    }
    ItemStack outputTemplate = template.outputTemplate();
    if (outputTemplate == null || outputTemplate.getType().isAir()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.missingOutput"));
      return false;
    }
    int amount = Math.max(1, spec.outputs().get(0).amount());
    ItemStack outputStack = outputTemplate.clone();
    outputStack.setAmount(amount);
    output.set(player, 0, outputStack);
    recipeName = spec.name();
    recipeDescription = spec.description();
    dirty = false;
    redrawSlot(player, SLOT_DIRTY);
    return true;
  }

  public boolean loadRecipeById(Player player, String id) {
    if (id == null || id.isBlank()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.missingId"));
      return false;
    }
    this.recipeId = id.trim();
    boolean loaded = loadRecipe(player);
    if (loaded) {
      dirty = false;
      redraw(player);
    }
    return loaded;
  }

  private boolean saveRecipe(Player player) {
    Objects.requireNonNull(player, "player");
    if (registry == null) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.registryMissing"));
      return false;
    }
    if (recipeId == null || recipeId.isBlank()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.missingId"));
      return false;
    }
    ItemStack outputItem = outputItem(player);
    if (outputItem == null || outputItem.getType().isAir()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.outputRequired"));
      return false;
    }
    List<Map<String, Object>> inputs = inputsList(player);
    if (inputs.isEmpty()) {
      player.sendMessage(Locales.component(player, "messages.crafting.editor.inputRequired"));
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
      player.sendMessage(Locales.component(player, "messages.crafting.editor.saveFailed",
          Locales.placeholders("message", ex.getMessage())));
      return false;
    }
    AdminAuditStore.get().record("recipe:" + recipeId, player.getName());
    registry.reload();
    return true;
  }

  private ItemStack auditItem() {
    AdminAuditStore.Entry entry = AdminAuditStore.get().entry("recipe:" + recipeId);
    List<Component> lore = new ArrayList<>();
    if (entry == null || entry.timestamp() <= 0L) {
      lore.add(GuiI18n.tr("gui.crafting.editor.audit.none"));
    } else {
      String when = AUDIT_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()).atZone(ZoneId.systemDefault()));
      lore.add(Locales.component(null, "gui.crafting.editor.audit.lastEdit", Locales.placeholders("value", when)));
      lore.add(Locales.component(null, "gui.crafting.editor.audit.by", Locales.placeholders("value", entry.editor())));
    }
    return GuiItems.named(Material.CLOCK, GuiI18n.tr("gui.crafting.editor.audit.title"), lore);
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

  private void markDirty(Player player) {
    dirty = true;
    if (player != null) {
      redrawSlot(player, SLOT_DIRTY);
    }
  }

  private ItemStack dirtyIndicatorItem() {
    if (dirty) {
      return GuiItems.named(Material.ORANGE_DYE, GuiI18n.tr("gui.crafting.editor.dirty.unsaved.title"), List.of(
          GuiI18n.tr("gui.crafting.editor.dirty.unsaved.hint")));
    }
    return GuiItems.named(Material.LIME_DYE, GuiI18n.tr("gui.crafting.editor.dirty.saved.title"), List.of(
        GuiI18n.tr("gui.crafting.editor.dirty.saved.hint")));
  }

  private ItemStack summaryItem(Player player) {
    List<Component> lore = new ArrayList<>();
    List<Map<String, Object>> inputs = inputsList(player);
    if (inputs.isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.crafting.editor.summary.ingredients.none"));
    } else {
      lore.add(Locales.component(player, "gui.crafting.editor.summary.ingredients.count",
          Locales.placeholders("count", String.valueOf(inputs.size()))));
      int shown = 0;
      for (Map<String, Object> entry : inputs) {
        if (shown >= 5) {
          break;
        }
        String label = String.valueOf(entry.getOrDefault("item",
            entry.getOrDefault("material", Locales.text(player, "gui.common.unknown"))));
        Object amount = entry.getOrDefault("amount", 1);
        lore.add(Locales.component(player, "gui.crafting.editor.summary.entry",
            Locales.placeholders("amount", String.valueOf(amount), "label", label)));
        shown++;
      }
      if (inputs.size() > shown) {
        lore.add(GuiI18n.tr(player, "gui.crafting.editor.summary.more"));
      }
    }
    ItemStack out = outputItem(player);
    if (out == null || out.getType().isAir()) {
      lore.add(GuiI18n.tr(player, "gui.crafting.editor.summary.output.none"));
    } else {
      lore.add(Locales.component(player, "gui.crafting.editor.summary.output.value",
          Locales.placeholders("value",
              out.getType().name().toLowerCase(Locale.ROOT) + " x" + out.getAmount())));
    }
    return GuiItems.named(Material.MAP, GuiI18n.tr(player, "gui.crafting.editor.summary.title"), lore);
  }
}
