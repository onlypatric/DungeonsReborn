package dev.patric.dungeonsreborn.advancements.menu;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.advancements.AdvancementService;
import dev.patric.dungeonsreborn.gui.GuiDebug;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class AdvancementIndexMenu extends Window {
  private final AdvancementService advancements;
  private final VirtualList<String> list;
  private boolean debugLogged;

  public static void open(Player player, AdvancementService advancements) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new AdvancementIndexMenu(advancements));
  }

  public AdvancementIndexMenu(AdvancementService advancements) {
    super(54, GuiI18n.tr("gui.advancements.index.title"));
    this.advancements = Objects.requireNonNull(advancements, "advancements");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> advancementEntries(),
        this::renderEntry,
        (ctx, id) -> {
        });
    this.list.searchKey(this::advancementSearchKey);
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<String> advancementEntries() {
    List<String> entries = advancements.advancementIds();
    debugLogged = GuiDebug.logIndexOnce(debugLogged, "advancements", entries.size());
    entries = entries.stream().sorted((a, b) -> advancementTitleKey(a).compareToIgnoreCase(advancementTitleKey(b))).toList();
    return entries;
  }

  private ItemStack renderEntry(Player player, String id) {
    if (id == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    Component title = advancements.advancementTitle(id);
    List<Component> lore = List.of(GuiI18n.tr(player, "gui.advancements.index.entry.id",
        Placeholder.unparsed("id", id)));
    return GuiItems.head("ICON_ADVANCEMENTS", title, lore);
  }

  private String advancementSearchKey(String id) {
    if (id == null) {
      return "";
    }
    Component title = advancements.advancementTitle(id);
    return PlainTextComponentSerializer.plainText().serialize(title);
  }

  private String advancementTitleKey(String id) {
    if (id == null) {
      return "";
    }
    Component title = advancements.advancementTitle(id);
    return PlainTextComponentSerializer.plainText().serialize(title);
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.advancements.index.header",
        Placeholder.unparsed("count", String.valueOf(advancementEntries().size())));
    return GuiItems.head("ICON_ADVANCEMENTS", title, List.of());
  }
}
