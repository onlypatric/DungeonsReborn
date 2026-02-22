package dev.patric.dungeonsreborn.crafting.vanilla;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.crafting.CraftingCostSpec;
import dev.patric.dungeonsreborn.crafting.CraftingIngredientSpec;
import dev.patric.dungeonsreborn.crafting.CraftingMatchResult;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopTokenTierSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CraftingCostExecutor {
  public record CostResult(boolean ok, Component message) {
    public static CostResult success() {
      return new CostResult(true, null);
    }

    public static CostResult failure(Component message) {
      return new CostResult(false, message);
    }
  }

  private final DungeonsRebornPlugin plugin;

  public CraftingCostExecutor(DungeonsRebornPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public CostResult canPay(Player player, CraftingRecipeSpec spec, CraftingMatchResult match, ItemStack[] matrix) {
    if (player == null || spec == null) {
      return CostResult.failure(Component.text("Invalid crafting state."));
    }
    for (CraftingCostSpec cost : spec.costs()) {
      CostResult result = checkCost(player, cost, match, matrix);
      if (!result.ok()) {
        return result;
      }
    }
    return CostResult.success();
  }

  public CostResult consume(Player player, CraftingRecipeSpec spec, CraftingMatchResult match, ItemStack[] matrix) {
    CostResult check = canPay(player, spec, match, matrix);
    if (!check.ok()) {
      return check;
    }
    for (CraftingCostSpec cost : spec.costs()) {
      CostResult result = consumeCost(player, cost, match, matrix);
      if (!result.ok()) {
        return result;
      }
    }
    return CostResult.success();
  }

  private CostResult checkCost(Player player, CraftingCostSpec cost, CraftingMatchResult match, ItemStack[] matrix) {
    if (cost == null) {
      return CostResult.success();
    }
    return switch (cost.type()) {
      case MANA -> canConsumeResource(player, ManaProvider.DEFAULT_RESOURCE, cost.amount(), cost.message());
      case RESOURCE -> canConsumeResource(player, cost.resourceId(), cost.amount(), cost.message());
      case TOKENS -> hasTokens(player, cost.tokenTier(), (int) Math.ceil(cost.amount()), cost.message());
      case ITEM -> hasItemCost(player, cost.item(), cost.message());
      case DURABILITY -> hasDurability(match, matrix, cost.amount(), cost.allowBreak(), cost.message());
    };
  }

  private CostResult consumeCost(Player player, CraftingCostSpec cost, CraftingMatchResult match, ItemStack[] matrix) {
    if (cost == null) {
      return CostResult.success();
    }
    return switch (cost.type()) {
      case MANA -> consumeResource(player, ManaProvider.DEFAULT_RESOURCE, cost.amount(), cost.message());
      case RESOURCE -> consumeResource(player, cost.resourceId(), cost.amount(), cost.message());
      case TOKENS -> consumeTokens(player, cost.tokenTier(), (int) Math.ceil(cost.amount()), cost.message());
      case ITEM -> consumeItemCost(player, cost.item(), cost.message());
      case DURABILITY -> consumeDurability(match, matrix, cost.amount(), cost.allowBreak(), cost.message());
    };
  }

  private CostResult canConsumeResource(Player player, String resourceId, double amount, String message) {
    ManaProvider provider = plugin.effectsEngine() == null ? null : plugin.effectsEngine().manaProvider();
    if (provider == null) {
      return fail(message, "Resource system unavailable.");
    }
    String resource = resourceId == null || resourceId.isBlank() ? ManaProvider.DEFAULT_RESOURCE : resourceId;
    return provider.get(player, resource) >= amount
        ? CostResult.success()
        : fail(message, "Not enough " + resource + ".");
  }

  private CostResult consumeResource(Player player, String resourceId, double amount, String message) {
    ManaProvider provider = plugin.effectsEngine() == null ? null : plugin.effectsEngine().manaProvider();
    if (provider == null) {
      return fail(message, "Resource system unavailable.");
    }
    String resource = resourceId == null || resourceId.isBlank() ? ManaProvider.DEFAULT_RESOURCE : resourceId;
    Component result = provider.tryConsume(player, resource, amount);
    return result == null ? CostResult.success() : fail(message, result);
  }

  private CostResult hasTokens(Player player, String tokenTier, int amount, String message) {
    return countTokens(player, tokenTier) >= amount
        ? CostResult.success()
        : fail(message, "Not enough tokens.");
  }

  private CostResult consumeTokens(Player player, String tokenTier, int amount, String message) {
    int left = amount;
    ShopTokenSpec token = resolveTokenSpec(tokenTier);
    if (token == null || token.markerKey() == null) {
      return fail(message, "Token tier unavailable.");
    }
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && left > 0; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!ItemMarkers.has(stack, token.markerKey())) {
        continue;
      }
      int take = Math.min(left, stack.getAmount());
      stack.setAmount(stack.getAmount() - take);
      if (stack.getAmount() <= 0) {
        contents[i] = null;
      }
      left -= take;
    }
    player.getInventory().setContents(contents);
    return left <= 0 ? CostResult.success() : fail(message, "Not enough tokens.");
  }

  private int countTokens(Player player, String tokenTier) {
    ShopTokenSpec token = resolveTokenSpec(tokenTier);
    if (token == null || token.markerKey() == null) {
      return 0;
    }
    int count = 0;
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (ItemMarkers.has(stack, token.markerKey())) {
        count += stack.getAmount();
      }
    }
    return count;
  }

  private ShopTokenSpec resolveTokenSpec(String tier) {
    ShopYamlRegistry shops = plugin.shopRegistry();
    if (shops == null) {
      return null;
    }
    if (tier == null || tier.isBlank()) {
      return shops.tokenSpec();
    }
    ShopTokenTierSpec tierSpec = shops.tokenTier(tier);
    if (tierSpec == null) {
      return null;
    }
    return new ShopTokenSpec(tierSpec.item(), tierSpec.markerKey());
  }

  private CostResult hasItemCost(Player player, CraftingIngredientSpec ingredient, String message) {
    if (ingredient == null) {
      return CostResult.success();
    }
    int needed = Math.max(1, ingredient.amount());
    for (ItemStack stack : player.getInventory().getContents()) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!ingredient.matches(stack)) {
        continue;
      }
      needed -= stack.getAmount();
      if (needed <= 0) {
        return CostResult.success();
      }
    }
    return fail(message, "Missing item cost.");
  }

  private CostResult consumeItemCost(Player player, CraftingIngredientSpec ingredient, String message) {
    if (ingredient == null) {
      return CostResult.success();
    }
    int needed = Math.max(1, ingredient.amount());
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length && needed > 0; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!ingredient.matches(stack)) {
        continue;
      }
      int take = Math.min(needed, stack.getAmount());
      stack.setAmount(stack.getAmount() - take);
      if (stack.getAmount() <= 0) {
        contents[i] = null;
      }
      needed -= take;
    }
    player.getInventory().setContents(contents);
    return needed <= 0 ? CostResult.success() : fail(message, "Missing item cost.");
  }

  private CostResult hasDurability(CraftingMatchResult match, ItemStack[] matrix, double amount, boolean allowBreak, String message) {
    ItemStack target = firstDamageableInput(match, matrix);
    if (target == null) {
      return fail(message, "No damageable ingredient found.");
    }
    if (!(target.getItemMeta() instanceof Damageable damageable)) {
      return fail(message, "No damageable ingredient found.");
    }
    int max = target.getType().getMaxDurability();
    if (max <= 0) {
      return fail(message, "No damageable ingredient found.");
    }
    int next = damageable.getDamage() + (int) Math.ceil(amount);
    if (!allowBreak && next >= max) {
      return fail(message, "Durability too low.");
    }
    return CostResult.success();
  }

  private CostResult consumeDurability(CraftingMatchResult match, ItemStack[] matrix, double amount, boolean allowBreak, String message) {
    ItemStack target = firstDamageableInput(match, matrix);
    if (target == null) {
      return fail(message, "No damageable ingredient found.");
    }
    if (!(target.getItemMeta() instanceof Damageable damageable)) {
      return fail(message, "No damageable ingredient found.");
    }
    int max = target.getType().getMaxDurability();
    if (max <= 0) {
      return fail(message, "No damageable ingredient found.");
    }
    int next = damageable.getDamage() + (int) Math.ceil(amount);
    if (!allowBreak && next >= max) {
      return fail(message, "Durability too low.");
    }
    if (next >= max && allowBreak) {
      target.setAmount(0);
      return CostResult.success();
    }
    damageable.setDamage(Math.max(0, next));
    target.setItemMeta(damageable);
    return CostResult.success();
  }

  private ItemStack firstDamageableInput(CraftingMatchResult match, ItemStack[] matrix) {
    if (match == null || matrix == null) {
      return null;
    }
    List<Integer> slots = new ArrayList<>();
    for (Map.Entry<Integer, Integer> entry : match.consumed().entrySet()) {
      if (entry.getValue() != null && entry.getValue() > 0) {
        slots.add(entry.getKey());
      }
    }
    slots.sort(Integer::compareTo);
    for (Integer slot : slots) {
      if (slot == null || slot < 0 || slot >= matrix.length) {
        continue;
      }
      ItemStack stack = matrix[slot];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (stack.getItemMeta() instanceof Damageable) {
        return stack;
      }
    }
    return null;
  }

  private CostResult fail(String customMessage, String fallback) {
    return CostResult.failure(Component.text((customMessage == null || customMessage.isBlank()) ? fallback : customMessage));
  }

  private CostResult fail(String customMessage, Component fallback) {
    return (customMessage == null || customMessage.isBlank())
        ? CostResult.failure(fallback)
        : CostResult.failure(Component.text(customMessage));
  }
}
