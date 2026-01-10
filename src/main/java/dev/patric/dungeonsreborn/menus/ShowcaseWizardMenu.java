package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.input.Dropdown;
import dev.patric.dungeonsreborn.gui.components.input.NumericInput;
import dev.patric.dungeonsreborn.gui.flow.WizardStep;
import dev.patric.dungeonsreborn.gui.flow.WizardWindow;
import dev.patric.dungeonsreborn.gui.style.GuiTheme;
import net.kyori.adventure.text.Component;

/**
 * Wizard showcase: a tiny multi-step flow.
 */
public final class ShowcaseWizardMenu {
  public static final class DemoState {
    private String name = "";
    private int amount = 1;
    private GuiTheme theme = GuiTheme.PRIMARY;
  }

  private ShowcaseWizardMenu() {
  }

  public static Window create() {
    WizardWindow<DemoState> wizard = new WizardWindow<>(54, GuiMini.mm("<white><bold>Wizard Demo</bold></white>"), DemoState::new);

    wizard.steps(List.of(
        WizardStep.of(GuiMini.mm("<white><bold>Step 1: Text</bold></white>"), ctx -> {
          DemoState state = ctx.state();
          ctx.window().setDynamic(13, new Label(p -> GuiItem.of(Material.PAPER)
              .displayName(GuiMini.mm("<yellow><bold>Current Name</bold></yellow>"))
              .lore(List.of(GuiMini.mm("<gray>" + (state.name.isBlank() ? "(none)" : state.name) + "</gray>")))
              .build()));

          ctx.window().setDynamic(31, new TextButton(
              p -> GuiItem.of(Material.NAME_TAG)
                  .displayName(GuiMini.mm("<aqua><bold>Set Name</bold></aqua>"))
                  .lore(List.of(GuiMini.mm("<gray>Opens an anvil input.</gray>")))
                  .build(),
              GuiMini.mm("<gray>Type a name (or 'cancel')</gray>"),
              "cancel",
              Duration.ofSeconds(30),
              (w, text) -> {
                Player viewer = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
                if (viewer == null) {
                  return;
                }
                state.name = text;
                GuiSounds.success(viewer);
                w.redraw(viewer);
              },
              true)
                  .inputMode(TextButton.InputMode.ANVIL)
                  .anvilTitle(GuiMini.mm("<white><bold>Name</bold></white>"))
                  .initialText(p -> state.name));
        }),
        WizardStep.of(GuiMini.mm("<white><bold>Step 2: Inputs</bold></white>"), ctx -> {
          DemoState state = ctx.state();

          new NumericInput(2, 3,
              p -> state.amount,
              (p, value) -> {
                state.amount = value;
                GuiSounds.click(p);
              })
                  .range(1, 64)
                  .label(GuiMini.mm("<white><bold>Amount</bold></white>"))
                  .allowTyping(true)
                  .applyDynamic(ctx.window());

          Dropdown<GuiTheme> dropdown = new Dropdown<>(
              GuiMini.mm("<white><bold>Theme</bold></white>"),
              List.of(GuiTheme.values()),
              t -> Component.text(t.name()));
          dropdown.onSelect((player, theme) -> state.theme = theme);
          ctx.window().setDynamic(15, dropdown);
        }),
        WizardStep.of(GuiMini.mm("<white><bold>Step 3: Summary</bold></white>"), ctx -> {
          DemoState state = ctx.state();
          ctx.window().setDynamic(22, new Label(p -> GuiItem.of(Material.BOOK)
              .displayName(GuiMini.mm("<green><bold>Summary</bold></green>"))
              .lore(List.of(
                  Component.text("Name: " + (state.name.isBlank() ? "(none)" : state.name)),
                  Component.text("Amount: " + state.amount),
                  Component.text("Theme: " + state.theme.name()),
                  Component.empty(),
                  GuiMini.mm("<dark_gray>Use Next -> Finish to complete.</dark_gray>")))
              .build()));
        })));

    wizard.onFinish((player, state) -> {
      GuiSounds.success(player);
      player.sendMessage(GuiMini.mm("<green>Wizard finished.</green>"));
    });
    wizard.onCancel((player, state) -> {
      GuiSounds.error(player);
      player.sendMessage(GuiMini.mm("<red>Wizard cancelled.</red>"));
    });

    return wizard;
  }
}

