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
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.shops.ShopIngredientSpec;
import dev.patric.dungeonsreborn.shops.ShopIngredientType;
import dev.patric.dungeonsreborn.shops.ShopSpec;
import dev.patric.dungeonsreborn.shops.ShopTokenSpec;
import dev.patric.dungeonsreborn.shops.ShopTradeSpec;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ShopPreviewMenu extends Window {
  private static final int SIZE = 54;

  private final ShopYamlRegistry shops;
  private final String shopId;
  private final String shopTitle;
  @SuppressWarnings("unused")
  private final ShopSpec spec;
  private final VirtualList<ShopTradeSpec> list;

  public ShopPreviewMenu(ShopYamlRegistry shops, String shopId, String shopTitle, ShopSpec spec) {
    super(SIZE, GuiI18n.tr("gui.shops.preview.title"), true);
    this.shops = Objects.requireNonNull(shops, "shops");
    this.shopId = shopId == null ? "" : shopId;
    this.shopTitle = shopTitle == null ? this.shopId : shopTitle;
    this.spec = Objects.requireNonNull(spec, "spec");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(GuiNav.backButton().autoDescribeInLore(false));

    setFixedAt(0, 1, new Label(this::headerItem));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        player -> spec.trades(),
        this::tradeItem,
        (ctx, trade) -> GuiSounds.click(ctx.player()));
    list.apply(this, Placement.FIXED);

    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private ItemStack headerItem(Player player) {
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.shops.preview.header.hint",
        Placeholder.component("title", GuiMini.mm(shopTitle))));
    lore.add(GuiI18n.tr(player, "gui.shops.preview.header.id",
        Placeholder.unparsed("id", shopId)));
    return GuiItems.named(Material.EMERALD, GuiI18n.tr(player, "gui.shops.preview.header.title"), lore);
  }

  private ItemStack tradeItem(Player player, ShopTradeSpec trade) {
    ItemStack icon = resolveIngredient(trade == null ? null : trade.sell());
    if (icon == null || icon.getType().isAir()) {
      icon = new ItemStack(Material.PAPER);
    }
    ItemMeta meta = icon.getItemMeta();
    if (meta == null) {
      return icon;
    }
    List<Component> lore = new ArrayList<>();
    if (trade != null) {
      lore.add(GuiI18n.tr(player, "gui.shops.preview.entry.cost",
          Placeholder.unparsed("cost", costLabel(trade))));
      if (trade.minLevel() > 0) {
        lore.add(GuiI18n.tr(player, "gui.shops.preview.entry.minLevel",
            Placeholder.unparsed("level", String.valueOf(trade.minLevel()))));
      }
    }
    lore.add(GuiI18n.tr(player, "gui.shops.preview.entry.hint"));
    meta.lore(lore);
    icon.setItemMeta(meta);
    return icon;
  }

  private String costLabel(ShopTradeSpec trade) {
    String buy = ingredientLabel(trade.buyA());
    if (trade.buyB() != null) {
      buy = buy + " + " + ingredientLabel(trade.buyB());
    }
    return buy;
  }

  private String ingredientLabel(ShopIngredientSpec spec) {
    if (spec == null) {
      return "?";
    }
    String name = "?";
    if (spec.type() == ShopIngredientType.TOKEN) {
      ShopTokenSpec token = shops.tokenSpec();
      ItemStack base = token == null ? null : token.item();
      name = stackName(base, "Token");
    } else if (spec.type() == ShopIngredientType.ITEM_ID) {
      name = spec.itemId();
      ItemStack base = shops.itemResolver().apply(spec.itemId());
      name = stackName(base, name);
    } else if (spec.type() == ShopIngredientType.MATERIAL) {
      name = spec.material() == null ? "Material" : spec.material().name();
    } else if (spec.type() == ShopIngredientType.ITEMSTACK) {
      name = stackName(spec.item(), "Item");
    }
    return spec.amount() + "x " + name;
  }

  private static String stackName(ItemStack stack, String fallback) {
    if (stack == null) {
      return fallback;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta != null && meta.hasDisplayName()) {
      Component display = meta.displayName();
      if (display != null) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(display);
        if (!plain.isBlank()) {
          return plain;
        }
      }
    }
    return stack.getType() == null ? fallback : stack.getType().name();
  }

  private ItemStack resolveIngredient(ShopIngredientSpec spec) {
    if (spec == null) {
      return null;
    }
    return spec.resolve(shops.itemResolver(), shops.tokenSpec());
  }
}
