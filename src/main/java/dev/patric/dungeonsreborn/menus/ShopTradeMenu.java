package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.shops.ShopIngredientSpec;
import dev.patric.dungeonsreborn.shops.ShopMerchantBuilder;
import dev.patric.dungeonsreborn.shops.ShopSpec;
import dev.patric.dungeonsreborn.shops.ShopTradeSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShopTradeMenu extends Window {
  private final ShopYamlRegistry registry;
  private final ShopTradeSpec trade;
  private final int tradeIndex;

  public ShopTradeMenu(ShopYamlRegistry registry, ShopSpec shop, ShopTradeSpec trade, int tradeIndex) {
    super(54, GuiI18n.tr("gui.shops.trade.title",
        Placeholder.unparsed("shop", shop == null ? "" : shop.title())));
    this.registry = Objects.requireNonNull(registry, "registry");
    this.trade = Objects.requireNonNull(trade, "trade");
    this.tradeIndex = tradeIndex;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());

    setFixedAt(0, 4, new Label(this::headerItem));
    placeCostSlots();
    placeResultSlots();
    setFixedAt(3, 4, new Label(this::costLabel));
    setFixedAt(2, 4, buyButton());
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.shops.trade.header",
        Placeholder.unparsed("index", String.valueOf(tradeIndex + 1)));
    return GuiItems.head("ICON_SHOPS", title, List.of());
  }

  private ItemStack costLabel(Player player) {
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.shops.trade.cost"));
    int i = 1;
    for (ShopIngredientSpec ingredient : trade.buys()) {
      if (ingredient == null) {
        continue;
      }
      String label = ingredient.displayLabel(registry.itemResolver(), registry.tokenSpec());
      lore.add(Component.text(i + ". " + ingredient.amount() + "x " + label));
      i++;
    }
    return GuiItems.head("ICON_SHOPS", GuiI18n.tr(player, "gui.shops.trade.costTitle"), lore);
  }

  private void placeCostSlots() {
    int[][] slots = {
        {2, 1}, {2, 2}, {2, 3},
        {3, 1}, {3, 2}, {3, 3}
    };
    for (int i = 0; i < slots.length; i++) {
      int idx = i;
      setFixedAt(slots[i][0], slots[i][1], new Label(player -> costItem(player, idx)));
    }
  }

  private void placeResultSlots() {
    int[][] slots = {
        {2, 5}, {2, 6}, {2, 7},
        {3, 5}, {3, 6}, {3, 7}
    };
    for (int i = 0; i < slots.length; i++) {
      int idx = i;
      setFixedAt(slots[i][0], slots[i][1], new Label(player -> resultItem(player, idx)));
    }
  }

  private ItemStack costItem(Player player, int index) {
    List<ShopIngredientSpec> buys = trade.buys();
    if (index >= buys.size()) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    ItemStack stack = resolveIngredient(buys.get(index));
    Component title = GuiI18n.tr(player, "gui.shops.trade.costItem");
    if (stack == null) {
      return GuiItems.named(Material.BARRIER, title, List.of());
    }
    return GuiItems.named(stack, title, List.of(), true);
  }

  private ItemStack resultItem(Player player, int index) {
    List<ShopIngredientSpec> sells = trade.sells();
    if (index >= sells.size()) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    ItemStack stack = resolveIngredient(sells.get(index));
    Component title = GuiI18n.tr(player, "gui.shops.trade.reward");
    if (stack == null) {
      return GuiItems.head("ICON_SHOPS", title, List.of());
    }
    return GuiItems.named(stack, title, List.of(), true);
  }

  private Button buyButton() {
    Button button = new Button(player -> {
      List<Component> lore = new ArrayList<>();
      lore.add(GuiI18n.tr(player, "gui.shops.trade.buy.desc"));
      return GuiButtons.item(GuiButtons.Type.CONFIRM,
          GuiI18n.tr(player, "gui.shops.trade.buy.title"), lore);
    });
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      Player player = ctx.player();
      TradeAvailability availability = tradeAvailability(player);
      if (availability != TradeAvailability.FULL) {
        player.sendMessage(Locales.component(player, "messages.shops.trade.missingItems"));
        return;
      }
      if (!consumeCosts(player)) {
        player.sendMessage(Locales.component(player, "messages.shops.trade.missingItems"));
        return;
      }
      if (!grantResults(player)) {
        player.sendMessage(Locales.component(player, "messages.shops.trade.invalid"));
        return;
      }
      player.sendMessage(Locales.component(player, "messages.shops.trade.ok"));
      ctx.window().redraw(player);
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private TradeAvailability tradeAvailability(Player player) {
    boolean any = false;
    for (ShopIngredientSpec ingredient : trade.buys()) {
      if (ingredient == null) {
        continue;
      }
      if (ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.XP
          || ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.CUSTOM_XP) {
        return TradeAvailability.NONE;
      }
      int have = countMatching(player, ingredient);
      if (have >= ingredient.amount()) {
        any = true;
        continue;
      }
      if (have > 0) {
        any = true;
      }
      return any ? TradeAvailability.PARTIAL : TradeAvailability.NONE;
    }
    return any ? TradeAvailability.FULL : TradeAvailability.NONE;
  }

  private int countMatching(Player player, ShopIngredientSpec ingredient) {
    if (player == null || ingredient == null) {
      return 0;
    }
    int total = 0;
    ItemStack[] contents = player.getInventory().getContents();
    for (ItemStack stack : contents) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (ingredient.matches(stack, registry.itemResolver(), registry.tokenSpec())) {
        total += stack.getAmount();
      }
    }
    return total;
  }

  private boolean consumeCosts(Player player) {
    for (ShopIngredientSpec ingredient : trade.buys()) {
      if (ingredient == null) {
        continue;
      }
      if (!consumeIngredient(player, ingredient)) {
        return false;
      }
    }
    return true;
  }

  private boolean consumeIngredient(Player player, ShopIngredientSpec ingredient) {
    if (ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.XP
        || ingredient.type() == dev.patric.dungeonsreborn.shops.ShopIngredientType.CUSTOM_XP) {
      return false;
    }
    int remaining = ingredient.amount();
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length; i++) {
      ItemStack stack = contents[i];
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      if (!ingredient.matches(stack, registry.itemResolver(), registry.tokenSpec())) {
        continue;
      }
      int take = Math.min(remaining, stack.getAmount());
      stack.setAmount(stack.getAmount() - take);
      remaining -= take;
      if (stack.getAmount() <= 0) {
        contents[i] = null;
      }
      if (remaining <= 0) {
        break;
      }
    }
    player.getInventory().setContents(contents);
    return remaining <= 0;
  }

  private boolean grantResults(Player player) {
    for (ShopIngredientSpec ingredient : trade.sells()) {
      if (ingredient == null) {
        continue;
      }
      ItemStack stack = resolveIngredient(ingredient);
      if (stack == null) {
        return false;
      }
      var leftovers = player.getInventory().addItem(stack);
      if (!leftovers.isEmpty()) {
        for (ItemStack leftover : leftovers.values()) {
          player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
      }
    }
    return true;
  }

  private enum TradeAvailability {
    NONE,
    PARTIAL,
    FULL
  }

  private ItemStack resolveIngredient(ShopIngredientSpec ingredient) {
    if (ingredient == null) {
      return null;
    }
    ItemStack resolved = ShopMerchantBuilder.buildIngredient(ingredient, registry.tokenSpec(), registry.itemResolver());
    if (resolved == null) {
      return null;
    }
    ItemMeta meta = resolved.getItemMeta();
    if (meta != null) {
      resolved.setItemMeta(meta);
    }
    return resolved;
  }
}
