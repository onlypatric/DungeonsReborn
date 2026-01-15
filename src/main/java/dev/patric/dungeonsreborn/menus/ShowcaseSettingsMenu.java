package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
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
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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
      .prompt(GuiI18n.tr("gui.showcase.settings.picker.prompt"))
      .timeout(Duration.ofSeconds(30))
      .redrawWindowOnChange(true);

  private final AtomicReference<Integer> demoNumber = new AtomicReference<>(10);

  public ShowcaseSettingsMenu() {
    super(SIZE, GuiI18n.tr("gui.showcase.settings.title"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))).autoDescribeInLore(false));
    navRight(new dev.patric.dungeonsreborn.gui.components.CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close")))
        .autoDescribeInLore(false));

    // Text input modes
    setFixed(10, textInputButton("gui.showcase.settings.input.chat", TextButton.InputMode.CHAT));
    setFixed(11, textInputButton("gui.showcase.settings.input.anvil", TextButton.InputMode.ANVIL));
    setFixed(12, textInputButton("gui.showcase.settings.input.sign", TextButton.InputMode.SIGN));

    setFixed(SLOT_LAST_INPUT, new Label(p -> {
      String value = lastInput.get(p);
      if (value == null || value.isBlank()) {
        value = Locales.text(p, "gui.showcase.settings.lastInput.none");
      }
      return GuiItem.of(Material.PAPER)
          .displayName(GuiI18n.tr(p, "gui.showcase.settings.lastInput.title"))
          .lore(List.of(GuiI18n.tr(p, "gui.showcase.settings.lastInput.value", Placeholder.unparsed("value", value))))
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
            .label(GuiI18n.tr("gui.showcase.settings.number.title"))
            .typingPrompt(GuiI18n.tr("gui.showcase.settings.number.prompt"));
    numeric.applyFixed(this);

    // Dropdown (simple per-player selection)
    Dropdown<GuiTheme> themeDropdown = new Dropdown<>(
        GuiI18n.tr("gui.showcase.settings.theme.title"),
        List.of(GuiTheme.values()),
        t -> Component.text(t.name()));
    themeDropdown.button().autoDescribeInLore(false);
    setFixed(25, themeDropdown);

    // Item picker + preview + compare
    setFixed(28, picker);
    setFixed(SLOT_PREVIEW, new ItemPreview(p -> picker.selected(p))
        .placeholder(GuiItem.of(Material.GRAY_STAINED_GLASS_PANE).displayName(GuiI18n.tr("gui.showcase.settings.preview.none")).build()));

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

    setFixed(40, new Label(GuiItems.named(Material.PAPER, GuiI18n.tr("gui.showcase.settings.tryThis"), List.of(
        GuiI18n.tr("gui.showcase.settings.steps.1"),
        GuiI18n.tr("gui.showcase.settings.steps.2"),
        GuiI18n.tr("gui.showcase.settings.steps.3")))));

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

  private TextButton textInputButton(String key, TextButton.InputMode mode) {
    return new TextButton(
        p -> GuiItem.of(Material.OAK_SIGN)
            .displayName(GuiI18n.tr(p, key))
            .lore(List.of(GuiI18n.tr(p, "gui.showcase.settings.input.hint")))
            .build(),
        GuiI18n.tr("gui.showcase.settings.input.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
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
            .anvilTitle(GuiI18n.tr("gui.showcase.settings.input.anvilTitle"))
            .signInitialLines(List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty()));
  }
}
