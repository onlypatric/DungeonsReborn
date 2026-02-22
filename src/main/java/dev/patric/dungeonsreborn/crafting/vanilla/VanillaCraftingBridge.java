package dev.patric.dungeonsreborn.crafting.vanilla;

import dev.patric.dungeonsreborn.crafting.CraftingConsumedSlot;
import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingMatcher;
import dev.patric.dungeonsreborn.crafting.CraftingMatchResult;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VanillaCraftingBridge implements Listener {
  private final JavaPlugin plugin;
  private final CraftingYamlRegistry registry;
  private final CraftingDiscoveryService discovery;
  private final CraftingCooldownStore cooldowns;
  private final CraftingRuleEngine rules;
  private final CraftingCostExecutor costs;
  private final VanillaRecipeRegistrar registrar;

  public VanillaCraftingBridge(JavaPlugin plugin, CraftingYamlRegistry registry, CraftingDiscoveryService discovery) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.discovery = Objects.requireNonNull(discovery, "discovery");
    this.cooldowns = new CraftingCooldownStore();
    this.rules = new CraftingRuleEngine((dev.patric.dungeonsreborn.DungeonsRebornPlugin) plugin, discovery, cooldowns);
    this.costs = new CraftingCostExecutor((dev.patric.dungeonsreborn.DungeonsRebornPlugin) plugin);
    this.registrar = new VanillaRecipeRegistrar(plugin, registry, rules);
  }

  public void rebuild() {
    registrar.rebuild();
    for (Player player : plugin.getServer().getOnlinePlayers()) {
      syncPlayerRecipeBook(player);
    }
  }

  public void shutdown() {
    registrar.clear();
    cooldowns.clearAll();
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPrepare(PrepareItemCraftEvent event) {
    Player player = firstViewer(event.getViewers());
    if (player == null) {
      return;
    }
    CraftingInventory inventory = event.getInventory();
    if (!isSupportedInventory(inventory.getType())) {
      return;
    }
    CraftingMatchResult match = match(inventory);
    if (match == null) {
      return;
    }
    CraftingRuleEngine.CheckResult check = rules.check(player, match.recipe().spec(), CraftingRuleEngine.Phase.PREVIEW);
    if (!check.allowed()) {
      inventory.setResult(null);
      return;
    }
    ItemStack result = rules.primaryOutput(match.recipe());
    if (result == null || result.getType().isAir()) {
      inventory.setResult(null);
      return;
    }
    inventory.setResult(result);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onCraft(CraftItemEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (!(event.getInventory() instanceof CraftingInventory inventory)) {
      return;
    }
    if (!isSupportedInventory(inventory.getType())) {
      return;
    }
    CraftingMatchResult match = match(inventory);
    if (match == null) {
      return;
    }
    event.setCancelled(true);
    int attempts = event.isShiftClick() ? 64 : 1;
    int crafted = 0;
    for (int i = 0; i < attempts; i++) {
      CraftingMatchResult currentMatch = match(inventory);
      if (currentMatch == null) {
        break;
      }
      boolean success = craftOnce(player, inventory, currentMatch);
      if (!success) {
        break;
      }
      crafted++;
    }
    if (crafted == 0) {
      CraftingRuleEngine.CheckResult check = rules.check(player, match.recipe().spec(), CraftingRuleEngine.Phase.PRE_COMMIT);
      if (check.message() != null) {
        player.sendMessage(check.message());
      }
    }
    refreshPreview(player, inventory);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    syncPlayerRecipeBook(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    cooldowns.clearPlayer(event.getPlayer().getUniqueId());
  }

  private boolean craftOnce(Player player, CraftingInventory inventory, CraftingMatchResult match) {
    CraftingRecipeTemplate template = match.recipe();
    CraftingRuleEngine.CheckResult check = rules.check(player, template.spec(), CraftingRuleEngine.Phase.PRE_COMMIT);
    if (!check.allowed()) {
      if (check.message() != null) {
        player.sendMessage(check.message());
      }
      return false;
    }
    ItemStack[] matrix = cloneMatrix(inventory.getMatrix());
    CraftingCostExecutor.CostResult costResult = costs.consume(player, template.spec(), match, matrix);
    if (!costResult.ok()) {
      if (costResult.message() != null) {
        player.sendMessage(costResult.message());
      }
      return false;
    }

    List<ItemStack> outputs = rules.rollGrantedOutputs(player, template);
    if (outputs.isEmpty()) {
      return false;
    }
    ItemStack[] updated = applyConsumption(matrix, match);
    if (updated == null) {
      return false;
    }
    inventory.setMatrix(updated);
    grantReturns(player, match.consumedSlots());
    grantOutputs(player, outputs);

    if (template.spec().cooldownSeconds() > 0.0) {
      cooldowns.startCooldown(player.getUniqueId(), template.spec().id(), template.spec().cooldownSeconds());
    }
    discovery.unlockFromCraft(player, template.spec());
    syncPlayerRecipeBook(player);
    rules.runPostHook(player, template.spec());
    return true;
  }

  private void grantOutputs(Player player, List<ItemStack> outputs) {
    for (ItemStack output : outputs) {
      if (output == null || output.getType().isAir() || output.getAmount() <= 0) {
        continue;
      }
      Map<Integer, ItemStack> overflow = player.getInventory().addItem(output.clone());
      for (ItemStack extra : overflow.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), extra);
      }
    }
  }

  private void grantReturns(Player player, List<CraftingConsumedSlot> consumedSlots) {
    if (consumedSlots == null || consumedSlots.isEmpty()) {
      return;
    }
    List<ItemStack> items = new ArrayList<>();
    for (CraftingConsumedSlot consumed : consumedSlots) {
      if (consumed == null || consumed.ingredient() == null || consumed.amount() <= 0) {
        continue;
      }
      ItemStack returnItem = consumed.ingredient().returnItem();
      if (returnItem == null || returnItem.getType().isAir()) {
        continue;
      }
      ItemStack clone = returnItem.clone();
      clone.setAmount(Math.max(1, consumed.ingredient().returnAmount() * consumed.amount()));
      items.add(clone);
    }
    grantOutputs(player, items);
  }

  private ItemStack[] applyConsumption(ItemStack[] matrix, CraftingMatchResult match) {
    if (matrix == null || match == null) {
      return null;
    }
    ItemStack[] next = cloneMatrix(matrix);
    for (Map.Entry<Integer, Integer> entry : match.consumed().entrySet()) {
      int slot = entry.getKey();
      int amount = entry.getValue();
      if (slot < 0 || slot >= next.length || amount <= 0) {
        continue;
      }
      ItemStack stack = next[slot];
      if (stack == null || stack.getType().isAir() || stack.getAmount() < amount) {
        return null;
      }
      stack.setAmount(stack.getAmount() - amount);
      if (stack.getAmount() <= 0) {
        next[slot] = null;
      }
    }
    return next;
  }

  private void refreshPreview(Player player, CraftingInventory inventory) {
    CraftingMatchResult match = match(inventory);
    if (match == null) {
      inventory.setResult(null);
      return;
    }
    CraftingRuleEngine.CheckResult check = rules.check(player, match.recipe().spec(), CraftingRuleEngine.Phase.PREVIEW);
    if (!check.allowed()) {
      inventory.setResult(null);
      return;
    }
    inventory.setResult(rules.primaryOutput(match.recipe()));
  }

  private CraftingMatchResult match(CraftingInventory inventory) {
    if (inventory == null) {
      return null;
    }
    ItemStack[] matrix = inventory.getMatrix();
    if (matrix == null || matrix.length == 0) {
      return null;
    }
    return CraftingMatcher.match(matrix, registry.recipes().values());
  }

  private ItemStack[] cloneMatrix(ItemStack[] matrix) {
    ItemStack[] clone = new ItemStack[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      clone[i] = matrix[i] == null ? null : matrix[i].clone();
    }
    return clone;
  }

  private Player firstViewer(List<HumanEntity> viewers) {
    if (viewers == null || viewers.isEmpty()) {
      return null;
    }
    for (HumanEntity viewer : viewers) {
      if (viewer instanceof Player player) {
        return player;
      }
    }
    return null;
  }

  private boolean isSupportedInventory(InventoryType type) {
    return type == InventoryType.WORKBENCH || type == InventoryType.CRAFTING;
  }

  private void syncPlayerRecipeBook(Player player) {
    if (player == null) {
      return;
    }
    for (CraftingRecipeTemplate template : registry.recipes().values()) {
      if (template == null || template.spec() == null) {
        continue;
      }
      boolean shouldShow = shouldShowInRecipeBook(player, template.spec());
      for (NamespacedKey key : registrar.keysForRecipe(template.spec().id())) {
        try {
          if (shouldShow) {
            player.discoverRecipe(key);
          } else {
            player.undiscoverRecipe(key);
          }
        } catch (Exception ignored) {
        }
      }
    }
  }

  private boolean shouldShowInRecipeBook(Player player, dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec spec) {
    if (spec == null) {
      return false;
    }
    var discoverySpec = spec.discovery();
    if (discoverySpec == null || !discoverySpec.showInBook()) {
      return false;
    }
    return discovery.isAvailable(player, spec);
  }
}
