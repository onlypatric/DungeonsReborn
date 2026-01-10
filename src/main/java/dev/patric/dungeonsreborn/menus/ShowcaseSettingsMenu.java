package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.input.Dropdown;
import dev.patric.dungeonsreborn.gui.components.input.NumericInput;
import dev.patric.dungeonsreborn.gui.components.item.ItemCompare;
import dev.patric.dungeonsreborn.gui.components.item.ItemPicker;
import dev.patric.dungeonsreborn.gui.components.item.ItemPreview;
import dev.patric.dungeonsreborn.gui.state.GuiState;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiTheme;
import net.kyori.adventure.text.Component;

/**
 * Settings showcase: inputs + text modes + item picker + compare.
 */
public final class ShowcaseSettingsMenu extends Window {
  private static final int SIZE = 54;

  private static final int SLOT_LAST_INPUT = 22;
  private static final int SLOT_PREVIEW = 30;

  private final GuiState<String> lastInput = GuiState.ofValue("");
  private GuiState.Subscription binding;

  private final ItemPicker picker = new ItemPicker()
      .prompt(GuiMini.mm("<gray>Click an item in your inventory to select it.</gray>"))
      .timeout(Duration.ofSeconds(30))
      .redrawWindowOnChange(true);

  private final AtomicReference<Integer> demoNumber = new AtomicReference<>(10);

  public ShowcaseSettingsMenu() {
    super(SIZE, GuiMini.mm("<white><bold>Settings / Inputs</bold></white>"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))).autoDescribeInLore(false));
    navRight(new dev.patric.dungeonsreborn.gui.components.CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Component.text("Close")))
        .autoDescribeInLore(false));

    // Text input modes
    setFixed(10, textInputButton("Chat Input", TextButton.InputMode.CHAT));
    setFixed(11, textInputButton("Anvil Input", TextButton.InputMode.ANVIL));
    setFixed(12, textInputButton("Sign Input", TextButton.InputMode.SIGN));

    setFixed(SLOT_LAST_INPUT, new Label(p -> {
      String value = lastInput.get(p);
      if (value == null || value.isBlank()) {
        value = "(none yet)";
      }
      return GuiItem.of(Material.PAPER)
          .displayName(GuiMini.mm("<yellow><bold>Last Input</bold></yellow>"))
          .lore(List.of(GuiMini.mm("<gray>" + value + "</gray>")))
          .build();
    }));

    // Numeric input
    NumericInput numeric = new NumericInput(1, 5,
        p -> demoNumber.get(),
        (p, v) -> {
          demoNumber.set(v);
          GuiSounds.click(p);
        })
            .range(0, 100)
            .label(GuiMini.mm("<white><bold>Number</bold></white>"))
            .typingPrompt(GuiMini.mm("<gray>Type a number 0..100 (or 'cancel')</gray>"));
    numeric.applyFixed(this);

    // Dropdown (simple per-player selection)
    Dropdown<GuiTheme> themeDropdown = new Dropdown<>(
        GuiMini.mm("<white><bold>Theme</bold></white>"),
        List.of(GuiTheme.values()),
        t -> Component.text(t.name()));
    themeDropdown.button().autoDescribeInLore(false);
    setFixed(25, themeDropdown);

    // Item picker + preview + compare
    setFixed(28, picker);
    setFixed(SLOT_PREVIEW, new ItemPreview(p -> picker.selected(p))
        .placeholder(GuiItem.of(Material.GRAY_STAINED_GLASS_PANE).displayName(Component.text("No item selected")).build()));

    new ItemCompare(3, 5)
        .before(p -> picker.selected(p))
        .after(p -> {
          ItemStack selected = picker.selected(p);
          if (selected == null) {
            return null;
          }
          return GuiItem.of(selected).glint(true).build();
        })
        .applyFixed(this);

    setFixed(40, new Label(GuiItems.named(Material.PAPER, Component.text("Try this"), List.of(
        GuiMini.mm("<gray>1) Click an input button</gray>"),
        GuiMini.mm("<gray>2) Pick an item</gray>"),
        GuiMini.mm("<gray>3) Watch preview + compare update</gray>")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      if (binding != null) {
        binding.close();
        binding = null;
      }
      GuiSounds.close(ctx.player());
    });
  }

  @Override
  protected void build(Player player) {
    if (binding == null) {
      binding = lastInput.bindRedraw(this, SLOT_LAST_INPUT);
    }
  }

  private TextButton textInputButton(String title, TextButton.InputMode mode) {
    return new TextButton(
        p -> GuiItem.of(Material.OAK_SIGN)
            .displayName(GuiMini.mm("<aqua><bold>" + title + "</bold></aqua>"))
            .lore(List.of(GuiMini.mm("<gray>Stores your input in state.</gray>")))
            .build(),
        GuiMini.mm("<gray>Type something (or 'cancel')</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (window, text) -> {
          Player viewer = window.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(window.viewer());
          if (viewer == null) {
            return;
          }
          lastInput.set(viewer, text);
          GuiSounds.success(viewer);
        },
        true)
            .inputMode(mode)
            .anvilTitle(GuiMini.mm("<white><bold>Input</bold></white>"))
            .signInitialLines(List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty()));
  }
}
