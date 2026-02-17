package dev.patric.dungeonsreborn.gui.components;

import java.util.List;
import java.util.Objects;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;

/**
 * Simple "what is this?" tooltip button for major menus.
 */
public final class InfoButton extends Button {
  public InfoButton(String titleKey, String descKey) {
    super(player -> GuiItems.head(
        "NAV_INFO",
        GuiI18n.tr(player, Objects.requireNonNull(titleKey, "titleKey")),
        List.of(GuiI18n.tr(player, Objects.requireNonNull(descKey, "descKey")))));
    autoDescribeInLore(false);
    cachePerPlayer();
  }

  public InfoButton(String titleKey, List<String> descKeys) {
    super(player -> {
      List<net.kyori.adventure.text.Component> lore = descKeys == null ? List.of()
          : descKeys.stream().map(key -> GuiI18n.tr(player, key)).toList();
      return GuiItems.head("NAV_INFO",
          GuiI18n.tr(player, Objects.requireNonNull(titleKey, "titleKey")),
          lore);
    });
    autoDescribeInLore(false);
    cachePerPlayer();
  }

  public InfoButton(net.kyori.adventure.text.Component title, List<net.kyori.adventure.text.Component> lore) {
    super(player -> GuiItems.head("NAV_INFO", title, lore));
    autoDescribeInLore(false);
    cachePerPlayer();
  }

  public InfoButton() {
    this("gui.info.title", "gui.info.desc");
  }
}
