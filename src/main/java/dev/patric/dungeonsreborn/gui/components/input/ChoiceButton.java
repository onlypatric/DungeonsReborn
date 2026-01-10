package dev.patric.dungeonsreborn.gui.components.input;

import java.util.Objects;
import java.util.function.BiFunction;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;

/**
 * A button that toggles a selection in a {@link ChoiceGroup}.
 */
public final class ChoiceButton<T> extends Button {
  private final ChoiceGroup<T> group;
  private final T option;
  private final BiFunction<Player, Boolean, ItemStack> itemFactory;

  public ChoiceButton(ChoiceGroup<T> group, T option, BiFunction<Player, Boolean, ItemStack> itemFactory) {
    super(player -> {
      throw new IllegalStateException("ChoiceButton not initialized");
    });
    this.group = Objects.requireNonNull(group, "group");
    this.option = Objects.requireNonNull(option, "option");
    this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");

    autoDescribeInLore(false);
    left(ctx -> {
      group.toggle(ctx.window(), ctx.player(), this.option);
      group.redraw(ctx.window(), ctx.player());
    });
  }

  @Override
  public void mounted(Window window, int slot) {
    group.register(window, slot);
  }

  @Override
  public ItemStack render(Player player) {
    boolean selected = group.isSelected(player, option);
    ItemStack item = itemFactory.apply(player, selected);
    return item == null ? null : item.clone();
  }
}
