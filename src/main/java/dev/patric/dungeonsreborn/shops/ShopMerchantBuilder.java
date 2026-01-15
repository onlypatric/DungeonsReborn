package dev.patric.dungeonsreborn.shops;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.gui.GuiMini;
import net.kyori.adventure.text.Component;

public final class ShopMerchantBuilder {
  private ShopMerchantBuilder() {
  }

  public static Merchant buildMerchant(ShopSpec spec, ShopTokenSpec tokenSpec, Function<String, ItemStack> itemResolver,
      ShopStockManager stockManager) {
    if (spec == null) {
      throw new IllegalArgumentException("spec is required");
    }
    Merchant merchant = Bukkit.createMerchant();
    List<MerchantRecipe> recipes = new ArrayList<>();
    for (ShopTradeSpec trade : spec.trades()) {
      MerchantRecipe recipe = buildRecipe(trade, tokenSpec, itemResolver, spec, stockManager);
      if (recipe != null) {
        recipes.add(recipe);
      }
    }
    merchant.setRecipes(recipes);
    return merchant;
  }

  public static MerchantRecipe buildRecipe(ShopTradeSpec trade, ShopTokenSpec tokenSpec,
      Function<String, ItemStack> itemResolver, ShopSpec spec, ShopStockManager stockManager) {
    if (trade == null) {
      return null;
    }
    ItemStack result = trade.sell().resolve(itemResolver, tokenSpec);
    if (result == null) {
      return null;
    }
    ItemStack previewResult = applyPreviewLore(result, trade.previewLore(), trade, spec, tokenSpec, stockManager);
    int maxUses = trade.maxUses();
    MerchantRecipe recipe = new MerchantRecipe(previewResult, maxUses <= 0 ? Integer.MAX_VALUE : maxUses);
    recipe.setExperienceReward(trade.experienceReward());
    recipe.setPriceMultiplier(resolvePriceMultiplier(trade, spec, stockManager));
    ShopIngredientSpec buyA = trade.buyA();
    ShopIngredientSpec buyB = trade.buyB();
    if (buyA != null) {
      ItemStack ingredient = buildIngredient(buyA, tokenSpec, itemResolver);
      if (ingredient != null) {
        recipe.addIngredient(ingredient);
      }
    }
    if (buyB != null) {
      ItemStack ingredient = buildIngredient(buyB, tokenSpec, itemResolver);
      if (ingredient != null) {
        recipe.addIngredient(ingredient);
      }
    }
    return recipe;
  }

  public static ItemStack buildIngredient(ShopIngredientSpec ingredient, ShopTokenSpec tokenSpec,
      Function<String, ItemStack> itemResolver) {
    if (ingredient == null) {
      return null;
    }
    return switch (ingredient.type()) {
      case TOKEN, ITEM_ID, ITEMSTACK, MATERIAL -> ingredient.resolve(itemResolver, tokenSpec);
    };
  }

  private static float resolvePriceMultiplier(ShopTradeSpec trade, ShopSpec spec, ShopStockManager stockManager) {
    float base = trade.priceMultiplier();
    ShopDynamicPriceSpec dynamic = trade.dynamicPrice();
    if (dynamic == null) {
      return base;
    }
    double min = dynamic.minMultiplier();
    double max = dynamic.maxMultiplier();
    if (max < min) {
      max = min;
    }
    double computed = switch (dynamic.mode()) {
      case STOCK -> computeStockMultiplier(spec, stockManager, min, max);
      case TIME -> computeTimeMultiplier(dynamic.periodSeconds(), min, max);
    };
    if (Double.isNaN(computed)) {
      return base;
    }
    return (float) computed;
  }

  private static double computeStockMultiplier(ShopSpec spec, ShopStockManager stockManager, double min, double max) {
    if (spec == null || stockManager == null || spec.stock() == null || !spec.stock().enabled()) {
      return Double.NaN;
    }
    int maxStock = spec.stock().max();
    if (maxStock <= 0) {
      return Double.NaN;
    }
    int currentStock = stockManager.currentStock(spec.id(), spec.stock());
    if (currentStock < 0) {
      return Double.NaN;
    }
    double ratio = Math.max(0.0, Math.min(1.0, currentStock / (double) maxStock));
    return min + ((1.0 - ratio) * (max - min));
  }

  private static double computeTimeMultiplier(long periodSeconds, double min, double max) {
    long periodMs = Math.max(1L, periodSeconds) * 1000L;
    long now = System.currentTimeMillis();
    double fraction = (now % periodMs) / (double) periodMs;
    return min + (fraction * (max - min));
  }

  private static ItemStack applyPreviewLore(ItemStack result, List<String> previewLore, ShopTradeSpec trade,
      ShopSpec spec, ShopTokenSpec tokenSpec, ShopStockManager stockManager) {
    if (result == null) {
      return null;
    }
    ItemStack copy = result.clone();
    ItemMeta meta = copy.getItemMeta();
    if (meta == null) {
      return copy;
    }
    List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
    if (previewLore != null && !previewLore.isEmpty()) {
      lore.addAll(GuiMini.loreMm(previewLore));
    }
    appendTokenBreakdown(lore, trade);
    appendStockLore(lore, spec, stockManager);
    meta.lore(lore);
    copy.setItemMeta(meta);
    return copy;
  }

  private static void appendTokenBreakdown(List<Component> lore, ShopTradeSpec trade) {
    if (lore == null || trade == null) {
      return;
    }
    int tokenTotal = tokenAmount(trade.buyA()) + tokenAmount(trade.buyB());
    if (tokenTotal <= 0) {
      return;
    }
    int pallet = tokenTotal / 4096;
    int remaining = tokenTotal % 4096;
    int compressed = remaining / 64;
    int normal = remaining % 64;
    lore.add(GuiMini.mm("<gold>Price (Tokens)</gold>"));
    if (pallet > 0) {
      lore.add(GuiMini.mm("<gray>- " + pallet + " pallet</gray>"));
    }
    if (compressed > 0) {
      lore.add(GuiMini.mm("<gray>- " + compressed + " compressed</gray>"));
    }
    if (normal > 0) {
      lore.add(GuiMini.mm("<gray>- " + normal + " tokens</gray>"));
    }
  }

  private static int tokenAmount(ShopIngredientSpec ingredient) {
    if (ingredient == null || ingredient.type() != ShopIngredientType.TOKEN) {
      return 0;
    }
    return Math.max(0, ingredient.amount());
  }

  private static void appendStockLore(List<Component> lore, ShopSpec spec, ShopStockManager stockManager) {
    if (lore == null || spec == null || stockManager == null) {
      return;
    }
    ShopStockSpec stock = spec.stock();
    if (stock == null || !stock.enabled()) {
      return;
    }
    int current = stockManager.currentStock(spec.id(), stock);
    if (current < 0) {
      return;
    }
    lore.add(GuiMini.mm("<gold>Stock</gold>"));
    lore.add(GuiMini.mm("<gray>- " + current + " / " + stock.max() + "</gray>"));
  }
}
