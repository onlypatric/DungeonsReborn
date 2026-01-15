package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.crafting.CraftingMatchResult;
import dev.patric.dungeonsreborn.crafting.CraftingMatcher;
import dev.patric.dungeonsreborn.crafting.CraftingInventoryPlanner;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSession;
import dev.patric.dungeonsreborn.crafting.CraftingGuiSessionManager;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeVariant;
import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.components.storage.StorageSlot;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.quests.QuestService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Sample crafting GUI: 9x6 with a 4x4 input grid and a result preview slot.
 */
public final class CraftingTestMenu extends Window {
  private static final int SIZE = 54;

  private static final int SLOT_TITLE = 4;
  private static final int SLOT_RESULT_LABEL = 16;
  private static final int SLOT_RESULT_PREVIEW = 25;
  private static final int SLOT_DISCOVER = 33;
  private static final int SLOT_CRAFT = 34;
  private static final int SLOT_CLEAR = 43;

  private final CraftingYamlRegistry registry;
  private final QuestService questService;
  private final AdvancementService advancements;
  private final StorageArea inputs = new StorageArea(1, 1, 4, 4);
  private final Set<UUID> craftLocks = new HashSet<>();
  private final CraftingGuiSession sessionHandler = this::returnInputs;

  public CraftingTestMenu(CraftingYamlRegistry registry, CraftingGuiSessionManager sessions,
      AdvancementService advancements, QuestService questService) {
    super(SIZE, GuiI18n.tr("gui.crafting.test.title"), true);
    this.registry = registry;
    this.advancements = advancements;
    this.questService = questService;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.CRAFTING_TABLE, GuiI18n.tr("gui.crafting.test.header.title"), List.of(
        GuiI18n.tr("gui.crafting.test.header.hint1"),
        GuiI18n.tr("gui.crafting.test.header.hint2")))));

    setFixed(SLOT_RESULT_LABEL, new Label(GuiItems.named(Material.PAPER, GuiI18n.tr("gui.crafting.test.result.title"), List.of(
        GuiI18n.tr("gui.crafting.test.result.hint")))));

    setFixed(SLOT_RESULT_PREVIEW, new Label(this::resultPreview));

    setFixed(SLOT_DISCOVER, new Button(this::discoverButtonItem, ctx -> {
      if (registry == null) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.crafting.test.error.registry"));
        return;
      }
      GuiManager.get().push(ctx.player(), new CraftingDiscoveryMenu(registry, this));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    setFixed(SLOT_CRAFT, new Button(this::craftButtonItem, ctx -> {
      tryCraft(ctx.player(), false);
    }).autoDescribeInLore(false));

    setFixed(SLOT_CLEAR, new Button(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.crafting.test.clear.title"), List.of(
        GuiI18n.tr(p, "gui.crafting.test.clear.hint"))), ctx -> {
      returnInputs(ctx.player());
      redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    configureInputs();
    inputs.applyFixed(this);
    inputs.onChange((player, index, stack) -> redrawSlot(player, SLOT_RESULT_PREVIEW));

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
      craftLocks.remove(ctx.player().getUniqueId());
      returnInputs(ctx.player());
      GuiSounds.close(ctx.player());
    });
  }

  private void configureInputs() {
    for (int i = 0; i < inputs.size(); i++) {
      StorageSlot slot = inputs.slot(i);
      slot.vanilla(true).accepts(stack -> true);
    }
  }

  private ItemStack resultPreview(Player player) {
    CraftingMatchResult match = matchFor(player);
    if (match == null) {
      return GuiItems.named(Material.GRAY_DYE, GuiI18n.tr(player, "gui.crafting.test.noRecipe.title"), List.of(
          GuiI18n.tr(player, "gui.crafting.test.noRecipe.preview")));
    }
    List<ItemStack> outputs = buildOutputs(match);
    if (outputs.isEmpty()) {
      return GuiItems.named(Material.BARRIER, GuiI18n.tr(player, "gui.crafting.test.invalidOutput.title"), List.of(
          GuiI18n.tr(player, "gui.crafting.test.invalidOutput.hint")));
    }
    ItemStack preview = outputs.get(0).clone();
    List<Component> detail = new ArrayList<>(buildMatchLore(match));
    if (outputs.size() > 1) {
      detail.add(GuiI18n.tr(player, "gui.crafting.test.outputs.more",
          Placeholder.unparsed("count", String.valueOf(outputs.size() - 1))));
    }
    appendLore(preview, detail);
    return preview;
  }

  private ItemStack craftButtonItem(Player player) {
    CraftingMatchResult match = matchFor(player);
    if (match == null) {
      return GuiItems.named(Material.GRAY_DYE, GuiI18n.tr(player, "gui.crafting.test.noRecipe.title"), List.of(
          GuiI18n.tr(player, "gui.crafting.test.noRecipe.craft")));
    }
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.crafting.test.craft.hint"));
    lore.addAll(buildMatchLore(match));
    return GuiItems.named(Material.LIME_DYE, GuiI18n.tr(player, "gui.crafting.test.craft.title"), lore);
  }

  private ItemStack discoverButtonItem(Player player) {
    return GuiItems.named(Material.BOOK, GuiI18n.tr(player, "gui.crafting.test.discover.title"), List.of(
        GuiI18n.tr(player, "gui.crafting.test.discover.hint")));
  }

  private boolean lockCraft(Player player) {
    UUID id = player.getUniqueId();
    if (craftLocks.contains(id)) {
      return false;
    }
    craftLocks.add(id);
    setInputsLocked(true);
    JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> unlockCraft(player), 2L);
    return true;
  }

  private void unlockCraft(Player player) {
    craftLocks.remove(player.getUniqueId());
    setInputsLocked(false);
  }

  private void setInputsLocked(boolean locked) {
    for (int i = 0; i < inputs.size(); i++) {
      StorageSlot slot = inputs.slot(i);
      slot.allowPut(!locked);
      slot.allowTake(!locked);
    }
  }

  private void returnInputs(Player player) {
    for (ItemStack stack : inputs.contents(player)) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      var leftovers = player.getInventory().addItem(stack);
      if (!leftovers.isEmpty()) {
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      }
    }
    inputs.clear(player);
  }

  public boolean craftFromDiscovery(Player player, CraftingRecipeTemplate recipe, CraftingRecipeVariant variant,
      boolean closeAfter) {
    if (player == null || recipe == null || variant == null) {
      return false;
    }
    if (!loadRecipeFromInventory(player, recipe, variant)) {
      return false;
    }
    return tryCraft(player, closeAfter);
  }

  private void logCraft(Player player, CraftingMatchResult match, ItemStack[] before, List<ItemStack> outputs) {
    if (registry == null) {
      return;
    }
    StringBuilder inputSummary = new StringBuilder();
    for (var entry : match.consumed().entrySet()) {
      int slot = entry.getKey();
      int amount = entry.getValue();
      if (amount <= 0 || slot < 0 || slot >= before.length) {
        continue;
      }
      ItemStack stack = before[slot];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!inputSummary.isEmpty()) {
        inputSummary.append(", ");
      }
      String itemId = ItemMarkers.getItemId(stack);
      String name = itemId != null ? itemId : stack.getType().name();
      inputSummary.append(name).append("x").append(amount);
    }
    StringBuilder outputSummary = new StringBuilder();
    for (ItemStack output : outputs) {
      if (output == null || output.getType().isAir()) {
        continue;
      }
      if (!outputSummary.isEmpty()) {
        outputSummary.append(", ");
      }
      String outputName = ItemMarkers.getItemId(output);
      if (outputName == null) {
        outputName = output.getType().name();
      }
      outputSummary.append(outputName).append("x").append(output.getAmount());
    }
    registry.logger().info("[Crafting] craft: player=" + player.getName()
        + " recipe=" + match.recipe().spec().id()
        + " inputs=[" + inputSummary + "]"
        + " outputs=[" + outputSummary + "]");
    if (questService != null) {
      questService.handleCraft(player, match.recipe().spec().id(), outputs);
    }
  }

  private CraftingMatchResult matchFor(Player player) {
    if (registry == null) {
      return null;
    }
    ItemStack[] contents = inputs.contents(player);
    return CraftingMatcher.match(contents, registry.recipes().values());
  }

  private boolean hasPermissions(Player player, CraftingRecipeSpec spec) {
    if (spec.permissions().isEmpty()) {
      return true;
    }
    for (String permission : spec.permissions()) {
      if (permission == null || permission.isBlank()) {
        continue;
      }
      if (player.hasPermission(permission.trim())) {
        return true;
      }
    }
    return false;
  }

  private List<ItemStack> buildOutputs(CraftingMatchResult match) {
    List<ItemStack> templates = match.recipe().outputTemplates();
    List<ItemStack> outputs = new ArrayList<>();
    List<dev.patric.dungeonsreborn.crafting.CraftingOutputSpec> specs = match.recipe().spec().outputs();
    int count = Math.min(templates.size(), specs.size());
    for (int i = 0; i < count; i++) {
      ItemStack template = templates.get(i);
      if (template == null || template.getType().isAir()) {
        continue;
      }
      int amount = Math.max(1, specs.get(i).amount());
      ItemStack output = template.clone();
      output.setAmount(amount);
      outputs.add(output);
    }
    return outputs;
  }

  private boolean canFitOutputs(Player player, List<ItemStack> outputs) {
    ItemStack[] storage = player.getInventory().getStorageContents();
    ItemStack[] simulated = new ItemStack[storage.length];
    for (int i = 0; i < storage.length; i++) {
      ItemStack stack = storage[i];
      simulated[i] = stack == null ? null : stack.clone();
    }
    for (ItemStack output : outputs) {
      int remaining = Math.max(1, output.getAmount());
      int maxStack = Math.max(1, output.getMaxStackSize());
      for (int i = 0; i < simulated.length && remaining > 0; i++) {
        ItemStack slot = simulated[i];
        if (slot == null || slot.getType().isAir()) {
          int take = Math.min(remaining, maxStack);
          ItemStack placed = output.clone();
          placed.setAmount(take);
          simulated[i] = placed;
          remaining -= take;
          continue;
        }
        if (!slot.isSimilar(output)) {
          continue;
        }
        int capacity = Math.max(0, maxStack - slot.getAmount());
        if (capacity <= 0) {
          continue;
        }
        int take = Math.min(remaining, capacity);
        slot.setAmount(slot.getAmount() + take);
        remaining -= take;
      }
      if (remaining > 0) {
        return false;
      }
    }
    return true;
  }

  private void consumeInputs(Player player, CraftingMatchResult match) {
    ItemStack[] contents = inputs.contents(player);
    for (int i = 0; i < contents.length; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      int consume = match.consumed().getOrDefault(i, 0);
      if (consume <= 0) {
        continue;
      }
      int remaining = stack.getAmount() - consume;
      if (remaining <= 0) {
        contents[i] = null;
      } else {
        stack.setAmount(remaining);
        contents[i] = stack;
      }
    }
    for (int i = 0; i < contents.length; i++) {
      inputs.set(player, i, contents[i]);
    }
  }

  private void giveOutputs(Player player, List<ItemStack> outputs) {
    for (ItemStack output : outputs) {
      int remaining = Math.max(1, output.getAmount());
      int maxStack = Math.max(1, output.getMaxStackSize());
      while (remaining > 0) {
        int amount = Math.min(remaining, maxStack);
        ItemStack stack = output.clone();
        stack.setAmount(amount);
        var leftovers = player.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
          leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        remaining -= amount;
      }
    }
  }

  private void appendLore(ItemStack stack, List<Component> extra) {
    if (stack == null || extra == null || extra.isEmpty()) {
      return;
    }
    var meta = stack.getItemMeta();
    if (meta == null) {
      return;
    }
    List<Component> lore = new ArrayList<>();
    if (meta.lore() != null) {
      lore.addAll(meta.lore());
    }
    lore.addAll(extra);
    meta.lore(lore);
    stack.setItemMeta(meta);
  }

  public boolean loadRecipeFromInventory(Player player, CraftingRecipeTemplate recipe, CraftingRecipeVariant variant) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(recipe, "recipe");
    Objects.requireNonNull(variant, "variant");
    returnInputs(player);
    ItemStack[] storage = player.getInventory().getStorageContents();
    Map<Integer, Integer> plan = CraftingInventoryPlanner.plan(storage, variant);
    if (plan == null || plan.isEmpty()) {
      player.sendMessage(GuiI18n.tr(player, "gui.crafting.test.error.missingIngredients"));
      return false;
    }
    List<ItemStack> stacks = CraftingInventoryPlanner.materialize(storage, plan);
    if (stacks.size() > inputs.size()) {
      player.sendMessage(GuiI18n.tr(player, "gui.crafting.test.error.tooManyIngredients"));
      return false;
    }
    ItemStack[] updated = CraftingInventoryPlanner.apply(storage, plan);
    player.getInventory().setStorageContents(updated);
    inputs.clear(player);
    for (int i = 0; i < stacks.size(); i++) {
      inputs.set(player, i, stacks.get(i));
    }
    redraw(player);
    return true;
  }

  private boolean tryCraft(Player player, boolean closeAfter) {
    if (!lockCraft(player)) {
      return false;
    }
    CraftingMatchResult match = matchFor(player);
    if (match == null) {
      GuiSounds.error(player);
      player.sendMessage(GuiI18n.tr(player, "gui.crafting.test.error.noMatch"));
      unlockCraft(player);
      return false;
    }
    if (!hasPermissions(player, match.recipe().spec())) {
      GuiSounds.error(player);
      player.sendMessage(GuiI18n.tr(player, "gui.crafting.test.error.noPermission"));
      unlockCraft(player);
      return false;
    }
    List<ItemStack> outputs = buildOutputs(match);
    if (outputs.isEmpty()) {
      GuiSounds.error(player);
      player.sendMessage(GuiI18n.tr(player, "gui.crafting.test.error.missingOutput"));
      unlockCraft(player);
      return false;
    }
    if (!canFitOutputs(player, outputs)) {
      GuiSounds.error(player);
      player.sendMessage(GuiI18n.tr(player, "gui.crafting.test.error.inventoryFull"));
      unlockCraft(player);
      return false;
    }
    ItemStack[] before = inputs.contents(player);
    consumeInputs(player, match);
    giveOutputs(player, outputs);
    logCraft(player, match, before, outputs);
    recordTokenOutputs(player, outputs);
    GuiSounds.success(player);
    redraw(player);
    unlockCraft(player);
    if (closeAfter) {
      player.closeInventory();
    }
    return true;
  }

  private void recordTokenOutputs(Player player, List<ItemStack> outputs) {
    if (advancements == null || player == null || outputs == null || outputs.isEmpty()) {
      return;
    }
    for (ItemStack output : outputs) {
      if (output == null || output.getType().isAir()) {
        continue;
      }
      advancements.recordTokensFromItem(player, output);
    }
  }

  private List<Component> buildMatchLore(CraftingMatchResult match) {
    List<Component> lore = new ArrayList<>();
    if (match == null) {
      return lore;
    }
    int required = 0;
    for (var ingredient : match.variant().inputs()) {
      required += Math.max(0, ingredient.amount());
    }
    int consumed = 0;
    for (int amount : match.consumed().values()) {
      consumed += Math.max(0, amount);
    }
    int percent = required <= 0 ? 100 : Math.min(100, (int) Math.round((consumed / (double) required) * 100.0));
    int specificity = variantSpecificity(match.variant());
    lore.add(GuiI18n.tr("gui.crafting.test.match.strength", Placeholder.unparsed("percent", String.valueOf(percent))));
    lore.add(GuiI18n.tr("gui.crafting.test.match.consumed",
        Placeholder.unparsed("consumed", String.valueOf(consumed)),
        Placeholder.unparsed("required", String.valueOf(required))));
    lore.add(GuiI18n.tr("gui.crafting.test.match.bestFit", Placeholder.unparsed("specificity", String.valueOf(specificity))));
    lore.add(GuiI18n.tr("gui.crafting.test.match.requiredTitle"));
    List<String> lines = new ArrayList<>();
    for (var ingredient : match.variant().inputs()) {
      lines.add(formatIngredient(ingredient));
    }
    int limit = Math.min(lines.size(), 6);
    for (int i = 0; i < limit; i++) {
      lore.add(GuiI18n.tr("gui.crafting.test.match.requiredEntry",
          Placeholder.unparsed("value", lines.get(i))));
    }
    if (lines.size() > limit) {
      lore.add(GuiI18n.tr("gui.crafting.test.match.requiredMore"));
    }
    return lore;
  }

  private String formatIngredient(dev.patric.dungeonsreborn.crafting.CraftingIngredientSpec ingredient) {
    String name = switch (ingredient.type()) {
      case ITEM_ID -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.crafting.test.format.itemPrefix") + ingredient.itemId();
      case TAG -> ingredient.tag() == null
          ? GuiI18n.str(GuiI18n.defaultLocale(), "gui.crafting.test.format.tag")
          : GuiI18n.str(GuiI18n.defaultLocale(), "gui.crafting.test.format.tagPrefix") + ingredient.tag().asString();
      case MATERIAL -> ingredient.material() == null
          ? GuiI18n.str(GuiI18n.defaultLocale(), "gui.crafting.test.format.material")
          : ingredient.material().name().toLowerCase(Locale.ROOT);
      case CATEGORY -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.crafting.test.format.categoryPrefix")
          + ingredient.category().name().toLowerCase(Locale.ROOT);
      case ANY -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.crafting.test.format.any");
    };
    return ingredient.amount() + "x " + name;
  }

  private int variantSpecificity(CraftingRecipeVariant variant) {
    int score = 0;
    for (var ingredient : variant.inputs()) {
      int weight = switch (ingredient.type()) {
        case ITEM_ID -> 5;
        case TAG -> 4;
        case MATERIAL -> 3;
        case CATEGORY -> 2;
        case ANY -> 1;
      };
      score += weight * Math.max(1, ingredient.amount());
    }
    return score;
  }
}
