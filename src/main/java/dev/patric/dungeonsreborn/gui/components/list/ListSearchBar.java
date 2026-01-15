package dev.patric.dungeonsreborn.gui.components.list;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ListSearchBar {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
  private static final String DEFAULT_CANCEL = "cancel";

  private ListSearchBar() {
  }

  public static TextButton searchButton(VirtualList<?> list, int... redrawSlots) {
    return searchButton(list, DEFAULT_TIMEOUT, redrawSlots);
  }

  public static TextButton searchButton(VirtualList<?> list, Duration timeout, int... redrawSlots) {
    TextButton button = new TextButton(
        player -> {
          String query = list.query(player);
          List<Component> lore = new ArrayList<>();
          lore.add(GuiI18n.tr(player, "gui.search.hint"));
          Component queryLabel = query == null || query.isBlank()
              ? GuiI18n.tr(player, "gui.search.none")
              : Component.text(query);
          lore.add(GuiI18n.tr(player, "gui.search.current", Placeholder.component("query", queryLabel)));
          return GuiButtons.item(GuiButtons.Type.SEARCH, GuiI18n.tr(player, "gui.button.search"), lore);
        },
        GuiI18n.tr("gui.search.prompt"),
        DEFAULT_CANCEL,
        timeout,
        (window, text) -> {
          Player player = viewer(window);
          if (player == null) {
            return;
          }
          list.query(player, text);
          if (redrawSlots != null && redrawSlots.length > 0) {
            list.redraw(window, player);
            redraw(window, player, redrawSlots);
          } else {
            window.redraw(player);
          }
          GuiSounds.click(player);
        },
        true);
    button.inputMode(TextButton.InputMode.CHAT);
    button.autoDescribeInLore(false);
    return button;
  }

  public static Button clearButton(VirtualList<?> list, int... redrawSlots) {
    return new Button(
        player -> GuiButtons.item(GuiButtons.Type.CLEAR, GuiI18n.tr(player, "gui.button.clear"),
            List.of(GuiI18n.tr(player, "gui.search.clear_hint"))),
        ctx -> {
          list.clearFilter(ctx.player());
          if (redrawSlots != null && redrawSlots.length > 0) {
            list.redraw(ctx.window(), ctx.player());
            redraw(ctx.window(), ctx.player(), redrawSlots);
          } else {
            ctx.window().redraw(ctx.player());
          }
          GuiSounds.click(ctx.player());
        }).autoDescribeInLore(false);
  }

  private static Player viewer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return Bukkit.getPlayer(window.viewer());
  }

  private static void redraw(Window window, Player player, int... slots) {
    if (window == null || player == null || slots == null) {
      return;
    }
    for (int slot : slots) {
      if (slot >= 0) {
        window.redrawSlot(player, slot);
      }
    }
  }
}
