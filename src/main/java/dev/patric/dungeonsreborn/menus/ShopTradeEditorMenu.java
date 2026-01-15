package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.storage.StorageArea;
import dev.patric.dungeonsreborn.gui.components.storage.StorageSlot;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.shops.ShopTradeDraft;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;

public final class ShopTradeEditorMenu extends Window {
  private static final int SIZE = 54;

  private final ShopYamlRegistry registry;
  private final ShopTradeDraft trade;
  private final Runnable onUpdate;
  private final StorageArea slots = new StorageArea(2, 2, 1, 3);

  public ShopTradeEditorMenu(ShopYamlRegistry registry, ShopTradeDraft trade, Runnable onUpdate) {
    super(SIZE, GuiI18n.tr("gui.shop.tradeEditor.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.trade = Objects.requireNonNull(trade, "trade");
    this.onUpdate = Objects.requireNonNull(onUpdate, "onUpdate");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));
    navRight(new Button(this::doneButtonItem, Window.ClickContext::close).autoDescribeInLore(false));

    setFixedAt(1, 2, new Label(GuiItems.named(Material.GOLD_INGOT, GuiI18n.tr("gui.shop.tradeEditor.buyA.title"), List.of(
        GuiI18n.tr("gui.shop.tradeEditor.buyA.hint")))));
    setFixedAt(1, 3, new Label(GuiItems.named(Material.IRON_INGOT, GuiI18n.tr("gui.shop.tradeEditor.buyB.title"), List.of(
        GuiI18n.tr("gui.shop.tradeEditor.buyB.hint")))));
    setFixedAt(1, 4, new Label(GuiItems.named(Material.EMERALD, GuiI18n.tr("gui.shop.tradeEditor.sell.title"), List.of(
        GuiI18n.tr("gui.shop.tradeEditor.sell.hint")))));

    configureSlots();
    slots.apply(this, Placement.FIXED);

    setFixedAt(4, 1, maxUsesButton());
    setFixedAt(4, 3, expRewardButton());
    setFixedAt(4, 5, priceMultiplierButton());
    setFixedAt(4, 7, tokenButton());

    onOpenWithReason(ctx -> {
      slots.set(ctx.player(), 0, trade.buyA());
      slots.set(ctx.player(), 1, trade.buyB());
      slots.set(ctx.player(), 2, trade.sell());
      GuiSounds.open(ctx.player());
    });
    onCloseWithReason(ctx -> {
      returnItems(ctx.player());
      this.onUpdate.run();
      GuiSounds.close(ctx.player());
    });
  }

  private void configureSlots() {
    for (int i = 0; i < slots.size(); i++) {
      StorageSlot slot = slots.slot(i);
      slot.vanilla(true).accepts(item -> item != null && !item.getType().isAir());
    }
    slots.onChange((player, index, stack) -> {
      switch (index) {
        case 0 -> trade.buyA(stack);
        case 1 -> trade.buyB(stack);
        case 2 -> trade.sell(stack);
        default -> {
        }
      }
    });
  }

  private Button maxUsesButton() {
    TextButton button = new TextButton(
        p -> GuiItems.named(Material.BOOK, GuiI18n.tr(p, "gui.shop.tradeEditor.maxUses.title"), List.of(
            Locales.component(p, "gui.shop.tradeEditor.maxUses.value", Locales.placeholders("value", trade.maxUses())),
            GuiI18n.tr(p, "gui.shop.tradeEditor.maxUses.hint"))),
        GuiI18n.tr("gui.shop.tradeEditor.maxUses.prompt"),
        Locales.text(null, "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          trade.maxUses(parseInt(text));
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        },
        true);
    button.validate((w, p, input) -> isInt(input) ? null : GuiI18n.tr(p, "gui.shop.tradeEditor.maxUses.invalid"));
    return button;
  }

  private Button expRewardButton() {
    return new Button(
        p -> GuiItems.named(Material.EXPERIENCE_BOTTLE, GuiI18n.tr(p, "gui.shop.tradeEditor.rewardXp.title"), List.of(
            Locales.component(p, "gui.shop.tradeEditor.rewardXp.value",
                Locales.placeholders("value", trade.experienceReward() ? Locales.text(p, "messages.common.yes")
                    : Locales.text(p, "messages.common.no"))),
            GuiI18n.tr(p, "gui.shop.tradeEditor.rewardXp.hint"))),
        ctx -> {
          trade.experienceReward(!trade.experienceReward());
          ctx.redraw();
        }).autoDescribeInLore(false);
  }

  private Button priceMultiplierButton() {
    TextButton button = new TextButton(
        p -> GuiItems.named(Material.REPEATER, GuiI18n.tr(p, "gui.shop.tradeEditor.priceMult.title"), List.of(
            Locales.component(p, "gui.shop.tradeEditor.priceMult.value",
                Locales.placeholders("value", trade.priceMultiplier())),
            GuiI18n.tr(p, "gui.shop.tradeEditor.priceMult.hint"))),
        GuiI18n.tr("gui.shop.tradeEditor.priceMult.prompt"),
        Locales.text(null, "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (window, text) -> {
          trade.priceMultiplier(parseFloat(text));
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        },
        true);
    button.validate((w, p, input) -> isFloat(input) ? null : GuiI18n.tr(p, "gui.shop.tradeEditor.priceMult.invalid"));
    return button;
  }

  private Button tokenButton() {
    return new Button(
        p -> GuiItems.named(Material.SUNFLOWER, GuiI18n.tr(p, "gui.shop.tradeEditor.tokens.title"), List.of(
            GuiI18n.tr(p, "gui.shop.tradeEditor.tokens.hint"))),
        ctx -> {
          ShopTokenSpec tokenSpec = registry.tokenSpec();
          if (tokenSpec == null || tokenSpec.item() == null) {
            ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.shop.tokens.missing"));
            return;
          }
          ItemStack item = tokenSpec.item().clone();
          item.setAmount(64);
          var leftovers = ctx.player().getInventory().addItem(item);
          if (!leftovers.isEmpty()) {
            leftovers.values().forEach(stack -> ctx.player().getWorld().dropItemNaturally(ctx.player().getLocation(), stack));
          }
          ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "messages.shop.tokens.added"));
        }).autoDescribeInLore(false);
  }

  private ItemStack doneButtonItem(Player player) {
    return GuiItems.named(Material.LIME_DYE, GuiI18n.tr(player, "gui.shop.tradeEditor.done.title"), List.of(
        GuiI18n.tr(player, "gui.shop.tradeEditor.done.hint")));
  }

  private void returnItems(Player player) {
    for (ItemStack stack : slots.contents(player)) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      var leftovers = player.getInventory().addItem(stack);
      if (!leftovers.isEmpty()) {
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      }
    }
    slots.clear(player);
  }

  private int parseInt(String raw) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private float parseFloat(String raw) {
    try {
      return Float.parseFloat(raw.trim());
    } catch (NumberFormatException ex) {
      return 0.0f;
    }
  }

  private boolean isInt(String raw) {
    try {
      Integer.parseInt(raw.trim());
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private boolean isFloat(String raw) {
    try {
      Float.parseFloat(raw.trim());
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }
}
