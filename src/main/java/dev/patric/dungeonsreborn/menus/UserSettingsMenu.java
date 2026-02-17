package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.LocaleService;

public final class UserSettingsMenu extends Window {
  private final LocaleService locales;
  private final EffectsEngine engine;

  public UserSettingsMenu(LocaleService locales, EffectsEngine engine) {
    super(45, GuiI18n.tr("gui.settings.title"));
    this.locales = Objects.requireNonNull(locales, "locales");
    this.engine = Objects.requireNonNull(engine, "engine");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    buildTiles();
  }

  private void buildTiles() {
    setFixedAt(1, 1, tile("ICON_LOCALE", "gui.settings.locale.title", "gui.settings.locale.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new LocaleSettingsMenu(locales))));
    setFixedAt(1, 3, tile("ICON_MANA", "gui.settings.mana.title", "gui.settings.mana.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new ManaSettingsMenu(engine))));
    setFixedAt(1, 5, tile("ICON_PARTICLES", "gui.settings.particles.title", "gui.settings.particles.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new ParticlesSettingsMenu(engine))));
    setFixedAt(1, 7, tile("ICON_CINEMATIC", "gui.settings.cinematic.title", "gui.settings.cinematic.desc",
        ctx -> ctx.window().openSubWindow(ctx.player(), new CinematicSettingsMenu(engine))));
  }

  private Button tile(String headId, String titleKey, String descKey, java.util.function.Consumer<Window.ClickContext> onClick) {
    Button button = new Button(player -> GuiItems.head(headId, GuiI18n.tr(player, titleKey), List.of(GuiI18n.tr(player, descKey))));
    button.left(GuiI18n.tr("gui.controls.action"), onClick);
    button.autoDescribeInLore(false);
    return button;
  }
}
