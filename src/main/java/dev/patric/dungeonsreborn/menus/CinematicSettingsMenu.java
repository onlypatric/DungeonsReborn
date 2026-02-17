package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.CinematicSettings;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;

public final class CinematicSettingsMenu extends Window {
  private final EffectsEngine engine;

  public CinematicSettingsMenu(EffectsEngine engine) {
    super(45, GuiI18n.tr("gui.settings.cinematic.title"));
    this.engine = Objects.requireNonNull(engine, "engine");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    buildToggles();
  }

  private void buildToggles() {
    setFixedAt(1, 1, toggle(CinematicSettings.Flag.SHAKE, "gui.cinematic.shake.title", "gui.cinematic.shake.desc"));
    setFixedAt(1, 3, toggle(CinematicSettings.Flag.FLASH, "gui.cinematic.flash.title", "gui.cinematic.flash.desc"));
    setFixedAt(1, 5, toggle(CinematicSettings.Flag.OVERLAY, "gui.cinematic.overlay.title", "gui.cinematic.overlay.desc"));
    setFixedAt(1, 7, toggle(CinematicSettings.Flag.DEBUG_OVERLAY, "gui.cinematic.debugOverlay.title",
        "gui.cinematic.debugOverlay.desc"));
  }

  private Button toggle(CinematicSettings.Flag flag, String titleKey, String descKey) {
    return new Button(player -> renderToggle(player, flag, titleKey, descKey))
        .left(GuiI18n.tr("gui.controls.action"), ctx -> {
          CinematicSettings settings = engine.cinematicSettings();
          boolean next = !settings.enabled(ctx.player(), flag);
          settings.set(ctx.player().getUniqueId(), flag, next);
          ctx.redrawSlot();
        })
        .autoDescribeInLore(false);
  }

  private ItemStack renderToggle(Player player, CinematicSettings.Flag flag, String titleKey, String descKey) {
    CinematicSettings settings = engine.cinematicSettings();
    boolean enabled = settings.enabled(player, flag);
    String headId = enabled ? "STATE_ON" : "STATE_OFF";
    Component status = enabled ? GuiI18n.tr(player, "gui.toggle.on") : GuiI18n.tr(player, "gui.toggle.off");
    return GuiItems.head(headId,
        GuiI18n.tr(player, titleKey),
        List.of(GuiI18n.tr(player, descKey), status));
  }
}
