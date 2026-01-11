package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.components.storage.StorageSlot;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

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
  private final StorageArea inputs = new StorageArea(1, 1, 4, 4);
  private final Set<UUID> craftLocks = new HashSet<>();
  private final CraftingGuiSession sessionHandler = this::returnInputs;

  public CraftingTestMenu(CraftingYamlRegistry registry, CraftingGuiSessionManager sessions) {
    super(SIZE, GuiMini.mm("<white><bold>Custom Crafting (Test)</bold></white>"), true);
    this.registry = registry;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Component.text("Close"))).autoDescribeInLore(false));

    setFixed(SLOT_TITLE, new Label(GuiItems.named(Material.CRAFTING_TABLE, GuiMini.mm("<gold><bold>Crafting Test</bold></gold>"), List.of(
        GuiMini.mm("<gray>Drop items into the 4x4 grid.</gray>"),
        GuiMini.mm("<dark_gray>Use Craft to consume inputs.</dark_gray>")))));

    setFixed(SLOT_RESULT_LABEL, new Label(GuiItems.named(Material.PAPER, GuiMini.mm("<aqua><bold>Result</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Preview updates as inputs change.</gray>")))));

    setFixed(SLOT_RESULT_PREVIEW, new Label(this::resultPreview));

    setFixed(SLOT_DISCOVER, new Button(this::discoverButtonItem, ctx -> {
      if (registry == null) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(GuiMini.mm("<red>Crafting registry not available.</red>"));
        return;
      }
      GuiManager.get().push(ctx.player(), new CraftingDiscoveryMenu(registry, this));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false));

    setFixed(SLOT_CRAFT, new Button(this::craftButtonItem, ctx -> {
      if (!lockCraft(ctx.player())) {
        return;
      }
      CraftingMatchResult match = matchFor(ctx.player());
      if (match == null) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(GuiMini.mm("<red>No matching recipe.</red>"));
        unlockCraft(ctx.player());
        return;
      }
      if (!hasPermissions(ctx.player(), match.recipe().spec())) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(GuiMini.mm("<red>You don't have permission to craft this.</red>"));
        unlockCraft(ctx.player());
        return;
      }
      List<ItemStack> outputs = buildOutputs(match);
      if (outputs.isEmpty()) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(GuiMini.mm("<red>Recipe output is missing.</red>"));
        unlockCraft(ctx.player());
        return;
      }
      if (!canFitOutputs(ctx.player(), outputs)) {
        GuiSounds.error(ctx.player());
        ctx.player().sendMessage(GuiMini.mm("<red>Inventory is full.</red>"));
        unlockCraft(ctx.player());
        return;
      }
      ItemStack[] before = inputs.contents(ctx.player());
      consumeInputs(ctx.player(), match);
      giveOutputs(ctx.player(), outputs);
      logCraft(ctx.player(), match, before, outputs);
      GuiSounds.success(ctx.player());
      redraw(ctx.player());
      unlockCraft(ctx.player());
    }).autoDescribeInLore(false));

    setFixed(SLOT_CLEAR, new Button(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Clear Inputs</bold></red>"), List.of(
        GuiMini.mm("<gray>Return items to your inventory.</gray>"))), ctx -> {
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
      return GuiItems.named(Material.GRAY_DYE, GuiMini.mm("<dark_gray><bold>No Recipe</bold></dark_gray>"), List.of(
          GuiMini.mm("<gray>Add items to see a preview.</gray>")));
    }
    List<ItemStack> outputs = buildOutputs(match);
    if (outputs.isEmpty()) {
      return GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Invalid Output</bold></red>"), List.of(
          GuiMini.mm("<gray>Recipe output could not be built.</gray>")));
    }
    ItemStack preview = outputs.get(0).clone();
    if (outputs.size() > 1) {
      appendLore(preview, List.of(GuiMini.mm("<dark_gray>+" + (outputs.size() - 1) + " more outputs</dark_gray>")));
    }
    return preview;
  }

  private ItemStack craftButtonItem(Player player) {
    CraftingMatchResult match = matchFor(player);
    if (match == null) {
      return GuiItems.named(Material.GRAY_DYE, GuiMini.mm("<dark_gray><bold>No Recipe</bold></dark_gray>"), List.of(
          GuiMini.mm("<gray>Add ingredients to craft.</gray>")));
    }
    return GuiItems.named(Material.LIME_DYE, GuiMini.mm("<green><bold>Craft</bold></green>"), List.of(
        GuiMini.mm("<gray>Craft the previewed recipe.</gray>")));
  }

  private ItemStack discoverButtonItem(Player player) {
    return GuiItems.named(Material.BOOK, GuiMini.mm("<aqua><bold>Discover Recipes</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Find recipes you can craft.</gray>")));
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
      player.sendMessage(GuiMini.mm("<red>Missing ingredients for this recipe.</red>"));
      return false;
    }
    List<ItemStack> stacks = CraftingInventoryPlanner.materialize(storage, plan);
    if (stacks.size() > inputs.size()) {
      player.sendMessage(GuiMini.mm("<red>Too many ingredients for the 4x4 grid.</red>"));
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
}
