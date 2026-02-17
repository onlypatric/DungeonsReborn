package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateSnapshot;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.item.PreviewCard;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ItemInspectMenu extends Window {
  private final EffectsYamlAbilities items;
  private final String itemId;
  private final Component itemTitle;

  public ItemInspectMenu(EffectsYamlAbilities items, String itemId) {
    super(54, GuiI18n.tr("gui.items.inspect.title",
        Placeholder.component("item", titleFromItem(items.itemTemplate(itemId), itemId))));
    this.items = Objects.requireNonNull(items, "items");
    this.itemId = Objects.requireNonNull(itemId, "itemId");
    this.itemTitle = titleFromItem(items.itemTemplate(itemId), itemId);
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());

    setFixedAt(0, 4, PreviewCard.head("ICON_ITEMS",
        player -> GuiI18n.tr(player, "gui.items.inspect.header",
            Placeholder.component("item", itemTitle)),
        player -> List.of()));
    setFixedAt(2, 4, new Label(this::itemDisplay));
    setFixedAt(3, 4, PreviewCard.head("ICON_ITEMS",
        player -> GuiI18n.tr(player, "gui.items.inspect.info"),
        this::infoLore));
  }

  private ItemStack itemDisplay(Player player) {
    ItemStack item = items.itemTemplate(itemId);
    if (item == null) {
      return GuiItems.head("ICON_ITEMS", GuiI18n.tr(player, "gui.items.inspect.missing"), List.of());
    }
    Component title = titleFromItem(item, itemId);
    return GuiItems.named(item, title, List.of(), true);
  }

  private List<Component> infoLore(Player player) {
    List<Component> lore = new ArrayList<>();
    ItemTemplateSnapshot snapshot = items.itemTemplateSnapshot(itemId);
    if (snapshot == null) {
      lore.add(GuiMini.mm("<gray>Missing template data.</gray>"));
      return lore;
    }
    lore.add(GuiI18n.tr(player, "gui.items.inspect.version",
        Placeholder.unparsed("version", String.valueOf(snapshot.version()))));
    if (snapshot.rarityId() != null) {
      lore.add(GuiI18n.tr(player, "gui.items.inspect.rarity",
          Placeholder.unparsed("rarity", snapshot.rarityId())));
    }
    if (snapshot.baseStats() != null && !snapshot.baseStats().isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.items.inspect.stats",
          Placeholder.unparsed("count", String.valueOf(snapshot.baseStats().values().size()))));
    }
    if (snapshot.affixPool() != null) {
      lore.add(GuiI18n.tr(player, "gui.items.inspect.affixes"));
    }
    if (snapshot.hooks() != null && !snapshot.hooks().isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.items.inspect.hooks",
          Placeholder.unparsed("count", String.valueOf(snapshot.hooks().size()))));
    }
    return lore;
  }

  private static Component titleFromItem(ItemStack item, String fallback) {
    if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
      Component display = item.getItemMeta().displayName();
      if (display != null) {
        return display;
      }
    }
    return Component.text(fallback);
  }
}
