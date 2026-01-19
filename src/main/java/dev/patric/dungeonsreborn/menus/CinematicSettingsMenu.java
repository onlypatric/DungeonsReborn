package dev.patric.dungeonsreborn.menus;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.CinematicSettings;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class CinematicSettingsMenu extends Window {
  private static final int SIZE = 27;

  private final CinematicSettings settings;

  public CinematicSettingsMenu(CinematicSettings settings) {
    super(SIZE, GuiI18n.tr("gui.cinematics.title"), true);
    this.settings = settings;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navRight(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));

    setFixed(4, new Label(GuiItems.named(Material.NETHER_STAR, GuiI18n.tr("gui.cinematics.header.title"), List.of(
        GuiI18n.tr("gui.cinematics.header.hint")))));

    setFixed(10, toggleButton(CinematicSettings.Flag.SHAKE, "gui.cinematics.shake.title", "gui.cinematics.shake.hint"));
    setFixed(13, toggleButton(CinematicSettings.Flag.FLASH, "gui.cinematics.flash.title", "gui.cinematics.flash.hint"));
    setFixed(16, toggleButton(CinematicSettings.Flag.OVERLAY, "gui.cinematics.overlay.title", "gui.cinematics.overlay.hint"));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Button toggleButton(CinematicSettings.Flag flag, String titleKey, String hintKey) {
    return new Button(player -> {
      boolean enabled = settings.enabled(player, flag);
      Material icon = enabled ? Material.LIME_DYE : Material.RED_DYE;
      Component status = GuiI18n.tr(player, enabled ? "gui.cinematics.toggle.on" : "gui.cinematics.toggle.off");
      return GuiItems.named(icon, GuiI18n.tr(player, titleKey), List.of(
          GuiI18n.tr(player, hintKey),
          status));
    }, ctx -> {
      Player player = ctx.player();
      boolean enabled = settings.enabled(player, flag);
      settings.set(player.getUniqueId(), flag, !enabled);
      GuiSounds.click(player);
      ctx.window().redraw(player);
    });
  }
}
