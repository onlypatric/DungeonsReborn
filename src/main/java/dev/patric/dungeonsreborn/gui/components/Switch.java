package dev.patric.dungeonsreborn.gui.components;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;

public final class Switch implements GuiComponent {
  private boolean value;
  private final Function<Boolean, ItemStack> renderer;
  private final BiConsumer<Window.ClickContext, Boolean> onChange;

  public Switch(boolean initial, Function<Boolean, ItemStack> renderer, BiConsumer<Window.ClickContext, Boolean> onChange) {
    this.value = initial;
    this.renderer = Objects.requireNonNull(renderer, "renderer");
    this.onChange = Objects.requireNonNull(onChange, "onChange");
  }

  public boolean value() {
    return value;
  }

  @Override
  public ItemStack render(Player player) {
    return renderer.apply(value);
  }

  @Override
  public void onClick(Window.ClickContext ctx) {
    value = !value;
    dev.patric.dungeonsreborn.gui.GuiManager.get().debug("Switch.onClick: player=" + ctx.player().getName()
        + " value=" + value + " slot=" + ctx.slot());
    onChange.accept(ctx, value);
    ctx.redrawSlot();
  }
}
