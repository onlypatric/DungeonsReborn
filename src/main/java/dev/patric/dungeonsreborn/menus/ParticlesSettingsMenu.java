package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ParticlesSettingsMenu extends Window {
  private final EffectsEngine engine;

  public ParticlesSettingsMenu(EffectsEngine engine) {
    super(45, GuiI18n.tr("gui.settings.particles.title"));
    this.engine = Objects.requireNonNull(engine, "engine");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));
    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    setFixedAt(1, 4, new Label(this::infoItem));
    setFixedAt(2, 4, new Label(this::hintItem));
  }

  private ItemStack infoItem(Player player) {
    int max = engine.particles().maxParticlesPerPlayerPerTick();
    return GuiItems.head("ICON_PARTICLES",
        GuiI18n.tr(player, "gui.particles.info.title"),
        List.of(GuiI18n.tr(player, "gui.particles.info.desc", Placeholder.unparsed("max", String.valueOf(max)))));
  }

  private ItemStack hintItem(Player player) {
    return GuiItems.named(Material.PAPER, GuiI18n.tr(player, "gui.particles.hint"));
  }
}
