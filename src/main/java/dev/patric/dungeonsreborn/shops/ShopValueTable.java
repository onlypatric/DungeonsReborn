package dev.patric.dungeonsreborn.shops;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;

public final class ShopValueTable {
  private final List<ShopValueSpec> values;

  public ShopValueTable(List<ShopValueSpec> values) {
    this.values = values == null ? List.of() : List.copyOf(values);
  }

  public static ShopValueTable empty() {
    return new ShopValueTable(List.of());
  }

  public List<ShopValueSpec> entries() {
    return List.copyOf(values);
  }

  public OptionalInt valueFor(ItemStack item, ShopTokenSpec tokenSpec, Function<String, ItemStack> itemResolver) {
    if (item == null || values.isEmpty()) {
      return OptionalInt.empty();
    }
    OptionalInt best = OptionalInt.empty();
    for (ShopValueSpec spec : values) {
      if (spec == null) {
        continue;
      }
      OptionalInt candidate = spec.valueFor(item, tokenSpec, itemResolver);
      if (candidate.isEmpty()) {
        continue;
      }
      if (best.isEmpty() || candidate.getAsInt() > best.getAsInt()) {
        best = candidate;
      }
    }
    return best;
  }

  public static ShopValueTable of(List<ShopValueSpec> entries) {
    if (entries == null || entries.isEmpty()) {
      return empty();
    }
    return new ShopValueTable(new ArrayList<>(entries));
  }
}
