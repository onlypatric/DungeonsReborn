package dev.patric.dungeonsreborn.gui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.PaginatedWindow;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import net.kyori.adventure.text.Component;

/**
 * A simple option picker window, useful for dropdowns and small selection flows.
 */
public final class OptionPickerWindow<T> extends PaginatedWindow {
  private final List<T> options;
  private final Function<T, ItemStack> itemFactory;
  private final BiConsumer<Player, T> onPick;

  public OptionPickerWindow(Component title, List<T> options, Function<T, ItemStack> itemFactory, BiConsumer<Player, T> onPick) {
    super(54, Objects.requireNonNull(title, "title"));
    defaultNavControls(3);
    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    menu(0, new BackButton().autoDescribeInLore(false));
    menu(8, new CloseButton().autoDescribeInLore(false));

    this.options = List.copyOf(Objects.requireNonNull(options, "options"));
    this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
    this.onPick = Objects.requireNonNull(onPick, "onPick");
  }

  @Override
  protected void build(Player player) {
    List<Entry> entries = new ArrayList<>(options.size());
    for (T option : options) {
      if (option == null) {
        continue;
      }
      entries.add(new Entry(new Button(p -> itemFactory.apply(option), ctx -> {
        onPick.accept(ctx.player(), option);
        ctx.close();
      }).autoDescribeInLore(false)));
    }
    setEntries(entries);
    super.build(player);
  }
}
