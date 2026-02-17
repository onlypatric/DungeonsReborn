package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.mana.ManaUiSettings;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;

public final class ManaSettingsMenu extends Window {
  private final EffectsEngine engine;

  public ManaSettingsMenu(EffectsEngine engine) {
    super(45, GuiI18n.tr("gui.settings.mana.title"));
    this.engine = Objects.requireNonNull(engine, "engine");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    buildToggles();
  }

  private void buildToggles() {
    setFixedAt(1, 2, toggle(ManaUiSettings.Flag.ACTIONBAR, "gui.mana.actionbar.title", "gui.mana.actionbar.desc"));
    setFixedAt(1, 4, toggle(ManaUiSettings.Flag.WARNINGS, "gui.mana.warnings.title", "gui.mana.warnings.desc"));
    setFixedAt(1, 6, toggle(ManaUiSettings.Flag.SCOREBOARD, "gui.mana.scoreboard.title", "gui.mana.scoreboard.desc"));
  }

  private Button toggle(ManaUiSettings.Flag flag, String titleKey, String descKey) {
    return new Button(player -> renderToggle(player, flag, titleKey, descKey))
        .left(GuiI18n.tr("gui.controls.action"), ctx -> {
          ManaUiSettings settings = engine.manaUiSettings();
          boolean next = !settings.enabled(ctx.player(), flag);
          settings.set(ctx.player().getUniqueId(), flag, next);
          ctx.redrawSlot();
        })
        .autoDescribeInLore(false);
  }

  private ItemStack renderToggle(Player player, ManaUiSettings.Flag flag, String titleKey, String descKey) {
    ManaUiSettings settings = engine.manaUiSettings();
    boolean enabled = settings.enabled(player, flag);
    String headId = enabled ? "STATE_ON" : "STATE_OFF";
    Component status = enabled ? GuiI18n.tr(player, "gui.toggle.on") : GuiI18n.tr(player, "gui.toggle.off");
    return GuiItems.head(headId,
        GuiI18n.tr(player, titleKey),
        List.of(GuiI18n.tr(player, descKey), status));
  }
}
