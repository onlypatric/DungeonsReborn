package dev.patric.dungeonsreborn.shops;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public final class ShopEditorStore {
  private final ShopYamlRegistry registry;

  public ShopEditorStore(ShopYamlRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public ShopEditorDraft createDraft() {
    ShopEditorDraft draft = new ShopEditorDraft();
    draft.id("new_shop");
    draft.title("New Shop");
    draft.enabled(true);
    draft.cooldownSeconds(0.0);
    draft.stockMin(null);
    draft.stockMax(null);
    draft.restockSeconds(null);
    return draft;
  }

  public ShopEditorDraft loadDraft(String id) {
    ShopSpec spec = registry.shop(id);
    if (spec == null) {
      return null;
    }
    ShopEditorDraft draft = new ShopEditorDraft();
    draft.id(spec.id());
    draft.originalId(spec.id());
    draft.title(spec.title());
    draft.enabled(spec.enabled());
    draft.permission(spec.permission());
    draft.cooldownSeconds(spec.cooldownTicks() / 20.0);
    draft.worlds().addAll(spec.worlds());
    if (spec.icon() != null) {
      draft.icon(spec.icon().resolve(registry.itemResolver(), registry.tokenSpec()));
    }
    if (spec.stock() != null) {
      draft.stockMin(spec.stock().min());
      draft.stockMax(spec.stock().max());
      draft.restockSeconds(spec.stock().restockSeconds());
    }
    for (ShopTradeSpec trade : spec.trades()) {
      ShopTradeDraft tradeDraft = new ShopTradeDraft();
      tradeDraft.buyA(resolveTradeItem(trade.buyA()));
      tradeDraft.buyB(resolveTradeItem(trade.buyB()));
      tradeDraft.sell(resolveTradeItem(trade.sell()));
      tradeDraft.maxUses(trade.maxUses());
      tradeDraft.experienceReward(trade.experienceReward());
      tradeDraft.priceMultiplier(trade.priceMultiplier());
      tradeDraft.previewLore(trade.previewLore());
      tradeDraft.dynamicPrice(trade.dynamicPrice());
      draft.trades().add(tradeDraft);
    }
    return draft;
  }

  public boolean saveDraft(ShopEditorDraft draft) {
    Objects.requireNonNull(draft, "draft");
    if (draft.id() == null || draft.id().isBlank()) {
      return false;
    }
    String normalized = Ids.normalize(draft.id());
    File file = registry.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection shops = cfg.getConfigurationSection("shops");
    if (shops == null) {
      shops = cfg.createSection("shops");
    }
    if (draft.originalId() != null && !draft.originalId().equals(normalized)) {
      shops.set(draft.originalId(), null);
    }
    shops.set(normalized, null);
    ConfigurationSection node = shops.createSection(normalized);
    String title = draft.title();
    if (title == null || title.isBlank()) {
      title = normalized;
    }
    node.set("title", title);
    if (!draft.enabled()) {
      node.set("enabled", false);
    }
    if (draft.permission() != null && !draft.permission().isBlank()) {
      node.set("permission", draft.permission());
    }
    if (draft.cooldownSeconds() > 0.0) {
      node.set("cooldownSeconds", draft.cooldownSeconds());
    }
    if (!draft.worlds().isEmpty()) {
      node.set("worlds", new ArrayList<>(draft.worlds()));
    }
    if (draft.icon() != null && !draft.icon().getType().isAir()) {
      node.set("icon", ingredientMap(draft.icon(), false));
    }
    if (draft.stockMax() != null && draft.stockMax() > 0) {
      ConfigurationSection stock = node.createSection("stock");
      stock.set("min", Math.max(0, draft.stockMin() == null ? 0 : draft.stockMin()));
      stock.set("max", Math.max(0, draft.stockMax()));
      if (draft.restockSeconds() != null) {
        stock.set("restockSeconds", Math.max(0, draft.restockSeconds()));
      }
    }
    List<Map<String, Object>> trades = new ArrayList<>();
    for (ShopTradeDraft trade : draft.trades()) {
      if (trade.buyA() == null || trade.sell() == null) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("buyA", ingredientMap(trade.buyA(), true));
      if (trade.buyB() != null && !trade.buyB().getType().isAir()) {
        entry.put("buyB", ingredientMap(trade.buyB(), true));
      }
      entry.put("sell", ingredientMap(trade.sell(), true));
      if (trade.maxUses() > 0) {
        entry.put("maxUses", trade.maxUses());
      }
      if (trade.experienceReward()) {
        entry.put("experienceReward", true);
      }
      if (trade.priceMultiplier() != 0.0f) {
        entry.put("priceMultiplier", trade.priceMultiplier());
      }
      if (!trade.previewLore().isEmpty()) {
        entry.put("previewLore", new ArrayList<>(trade.previewLore()));
      }
      if (trade.dynamicPrice() != null) {
        ShopDynamicPriceSpec dynamic = trade.dynamicPrice();
        Map<String, Object> dynamicMap = new LinkedHashMap<>();
        dynamicMap.put("mode", dynamic.mode().name().toLowerCase());
        dynamicMap.put("minMultiplier", dynamic.minMultiplier());
        dynamicMap.put("maxMultiplier", dynamic.maxMultiplier());
        if (dynamic.mode() == ShopDynamicPriceMode.TIME && dynamic.periodSeconds() > 0) {
          dynamicMap.put("periodSeconds", dynamic.periodSeconds());
        }
        entry.put("dynamicPrice", dynamicMap);
      }
      trades.add(entry);
    }
    node.set("trades", trades);
    try {
      cfg.save(file);
    } catch (IOException ex) {
      return false;
    }
    draft.originalId(normalized);
    return true;
  }

  public boolean deleteShop(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    File file = registry.file();
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection shops = cfg.getConfigurationSection("shops");
    if (shops == null) {
      return false;
    }
    String normalized = Ids.normalize(id);
    if (!shops.contains(normalized)) {
      return false;
    }
    shops.set(normalized, null);
    try {
      cfg.save(file);
    } catch (IOException ex) {
      return false;
    }
    return true;
  }

  private ItemStack resolveTradeItem(ShopIngredientSpec spec) {
    if (spec == null) {
      return null;
    }
    return spec.resolve(registry.itemResolver(), registry.tokenSpec());
  }

  private Map<String, Object> ingredientMap(ItemStack item, boolean allowToken) {
    Map<String, Object> out = new LinkedHashMap<>();
    int amount = Math.max(1, item.getAmount());
    ShopTokenSpec tokenSpec = registry.tokenSpec();
    if (allowToken && tokenSpec != null && tokenSpec.markerKey() != null
        && ItemMarkers.has(item, tokenSpec.markerKey())) {
      out.put("type", "token");
      out.put("amount", amount);
      return out;
    }
    String itemId = ItemMarkers.getItemId(item);
    if (itemId != null && !itemId.isBlank()) {
      out.put("type", "item_id");
      out.put("itemId", itemId);
      out.put("amount", amount);
      return out;
    }
    if (isPlainMaterial(item)) {
      out.put("type", "material");
      out.put("material", item.getType().name());
      out.put("amount", amount);
      return out;
    }
    ItemStack copy = item.clone();
    copy.setAmount(1);
    out.put("type", "item");
    out.put("item", copy);
    out.put("amount", amount);
    return out;
  }

  private boolean isPlainMaterial(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return true;
    }
    if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants() || meta.hasCustomModelDataComponent()
        || meta.hasAttributeModifiers() || meta.isUnbreakable()) {
      return false;
    }
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    return pdc.getKeys().isEmpty();
  }
}
