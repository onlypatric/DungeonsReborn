package dev.patric.dungeonsreborn.gui.components.input;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.flow.OptionPickerWindow;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A single-slot dropdown that opens an {@link OptionPickerWindow} as a subwindow.
 */
public final class Dropdown<T> implements GuiComponent {
  private final Component title;
  private final List<T> options;
  private final BiFunction<Player, T, ItemStack> displayItem;
  private final Function<T, Component> optionLabel;
  private final Map<UUID, T> selectedByPlayer = new HashMap<>();
  private BiConsumer<Player, T> onSelect = (p, v) -> {
  };
  private final Button button;

  public Dropdown(Component title, List<T> options, Function<T, Component> optionLabel) {
    this(title, options,
        (player, value) -> GuiItems.named(Material.CHEST, title, List.of(
            Locales.component(player, "gui.dropdown.selected").append(Objects.requireNonNull(optionLabel, "optionLabel").apply(value)))),
        optionLabel);
  }

  public Dropdown(Component title, List<T> options, BiFunction<Player, T, ItemStack> displayItem, Function<T, Component> optionLabel) {
    this.title = Objects.requireNonNull(title, "title");
    this.options = List.copyOf(Objects.requireNonNull(options, "options"));
    if (this.options.isEmpty()) {
      throw new IllegalArgumentException("options must not be empty");
    }
    this.displayItem = Objects.requireNonNull(displayItem, "displayItem");
    this.optionLabel = Objects.requireNonNull(optionLabel, "optionLabel");

    this.button = new Button(p -> this.displayItem.apply(p, selected(p)))
        .left(Locales.component(null, "gui.dropdown.open"), ctx -> openPicker(ctx.window(), ctx.player()));
  }

  /**
   * Exposes the backing button so devs can customize lore formatting, control headers, etc.
   */
  public Button button() {
    return button;
  }

  public Dropdown<T> onSelect(BiConsumer<Player, T> onSelect) {
    this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
    return this;
  }

  public T selected(Player player) {
    Objects.requireNonNull(player, "player");
    T selected = selectedByPlayer.get(player.getUniqueId());
    return selected != null ? selected : options.get(0);
  }

  public void select(Player player, T value) {
    Objects.requireNonNull(player, "player");
    if (!options.contains(value)) {
      throw new IllegalArgumentException("value not in options");
    }
    selectedByPlayer.put(player.getUniqueId(), value);
  }

  @Override
  public ItemStack render(Player player) {
    return button.render(player);
  }

  @Override
  public void onClick(Window.ClickContext ctx) {
    button.onClick(ctx);
  }

  @Override
  public void mounted(Window window, int slot) {
    button.mounted(window, slot);
  }

  private void openPicker(Window parent, Player player) {
    OptionPickerWindow<T> picker = new OptionPickerWindow<>(
        title,
        options,
        option -> GuiItems.named(Material.PAPER, optionLabel.apply(option)),
        (p, picked) -> {
          selectedByPlayer.put(p.getUniqueId(), picked);
          onSelect.accept(p, picked);
        });
    parent.openSubWindow(player, picker);
  }
}
