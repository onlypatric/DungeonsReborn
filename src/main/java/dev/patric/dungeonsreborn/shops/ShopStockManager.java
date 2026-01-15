package dev.patric.dungeonsreborn.shops;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class ShopStockManager {
  private final ServiceLogger logger;
  private final Map<String, StockState> states = new ConcurrentHashMap<>();

  public ShopStockManager(ServiceLogger logger) {
    this.logger = logger;
  }

  public boolean consume(String shopId, ShopStockSpec spec) {
    if (shopId == null || spec == null || !spec.enabled()) {
      return true;
    }
    StockState state = states.computeIfAbsent(shopId, id -> new StockState(shopId, spec));
    state.refresh(spec);
    if (state.currentStock <= 0) {
      return false;
    }
    state.currentStock = Math.max(0, state.currentStock - 1);
    return true;
  }

  public int currentStock(String shopId, ShopStockSpec spec) {
    if (shopId == null || spec == null || !spec.enabled()) {
      return -1;
    }
    StockState state = states.computeIfAbsent(shopId, id -> new StockState(shopId, spec));
    state.refresh(spec);
    return state.currentStock;
  }

  private final class StockState {
    private final String shopId;
    private long nextRestockAt;
    private int currentStock;

    private StockState(String shopId, ShopStockSpec spec) {
      this.shopId = shopId;
      reseed(spec);
    }

    private void refresh(ShopStockSpec spec) {
      long now = System.currentTimeMillis();
      if (spec.restockSeconds() > 0 && now >= nextRestockAt) {
        reseed(spec);
      }
    }

    private void reseed(ShopStockSpec spec) {
      int min = spec.min();
      int max = spec.max();
      if (max <= 0) {
        currentStock = -1;
        nextRestockAt = Long.MAX_VALUE;
        return;
      }
      if (min > max) {
        min = max;
      }
      currentStock = min == max ? max : ThreadLocalRandom.current().nextInt(min, max + 1);
      long now = System.currentTimeMillis();
      if (spec.restockSeconds() > 0) {
        nextRestockAt = now + (spec.restockSeconds() * 1000L);
      } else {
        nextRestockAt = Long.MAX_VALUE;
      }
      logger.debug("[Shops] stock restock: shop=" + shopId + " stock=" + currentStock);
    }
  }
}
