package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.input.Dropdown;
import dev.patric.dungeonsreborn.gui.components.input.NumericInput;
import dev.patric.dungeonsreborn.gui.flow.WizardStep;
import dev.patric.dungeonsreborn.gui.flow.WizardWindow;
import dev.patric.dungeonsreborn.gui.style.GuiTheme;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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
    WizardWindow<DemoState> wizard = new WizardWindow<>(54, GuiI18n.tr("gui.showcase.wizard.title"), DemoState::new);

    wizard.steps(List.of(
        WizardStep.of(GuiI18n.tr("gui.showcase.wizard.step1.title"), ctx -> {
          DemoState state = ctx.state();
          ctx.window().setDynamic(13, new Label(p -> GuiItem.of(Material.PAPER)
              .displayName(GuiI18n.tr(p, "gui.showcase.wizard.step1.current.title"))
              .lore(List.of(GuiI18n.tr(p, "gui.showcase.wizard.step1.current.value",
                  Placeholder.unparsed("value", state.name.isBlank()
                      ? Locales.text(p, "gui.showcase.wizard.none")
                      : state.name))))
              .build()));

          ctx.window().setDynamic(31, new TextButton(
              p -> GuiItem.of(Material.NAME_TAG)
                  .displayName(GuiI18n.tr(p, "gui.showcase.wizard.step1.set.title"))
                  .lore(List.of(GuiI18n.tr(p, "gui.showcase.wizard.step1.set.hint")))
                  .build(),
              GuiI18n.tr("gui.showcase.wizard.step1.prompt"),
              GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
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
                  .anvilTitle(GuiI18n.tr("gui.showcase.wizard.step1.anvilTitle"))
                  .initialText(p -> state.name));
        }),
        WizardStep.of(GuiI18n.tr("gui.showcase.wizard.step2.title"), ctx -> {
          DemoState state = ctx.state();

          new NumericInput(2, 3,
              p -> state.amount,
              (p, value) -> {
                state.amount = value;
                GuiSounds.click(p);
              })
                  .range(1, 64)
                  .label(GuiI18n.tr("gui.showcase.wizard.step2.amount"))
                  .allowTyping(true)
                  .applyDynamic(ctx.window());

          Dropdown<GuiTheme> dropdown = new Dropdown<>(
              GuiI18n.tr("gui.showcase.wizard.step2.theme"),
              List.of(GuiTheme.values()),
              t -> Component.text(t.name()));
          dropdown.onSelect((player, theme) -> state.theme = theme);
          ctx.window().setDynamic(15, dropdown);
        }),
        WizardStep.of(GuiI18n.tr("gui.showcase.wizard.step3.title"), ctx -> {
          DemoState state = ctx.state();
          ctx.window().setDynamic(22, new Label(p -> GuiItem.of(Material.BOOK)
              .displayName(GuiI18n.tr(p, "gui.showcase.wizard.step3.summary.title"))
              .lore(List.of(
                  GuiI18n.tr(p, "gui.showcase.wizard.step3.summary.name",
                      Placeholder.unparsed("value", state.name.isBlank()
                          ? Locales.text(p, "gui.showcase.wizard.none")
                          : state.name)),
                  GuiI18n.tr(p, "gui.showcase.wizard.step3.summary.amount",
                      Placeholder.unparsed("value", String.valueOf(state.amount))),
                  GuiI18n.tr(p, "gui.showcase.wizard.step3.summary.theme",
                      Placeholder.unparsed("value", state.theme.name())),
                  Component.empty(),
                  GuiI18n.tr(p, "gui.showcase.wizard.step3.summary.hint")))
              .build()));
        })));

    wizard.onFinish((player, state) -> {
      GuiSounds.success(player);
      player.sendMessage(GuiI18n.tr(player, "gui.showcase.wizard.finished"));
    });
    wizard.onCancel((player, state) -> {
      GuiSounds.error(player);
      player.sendMessage(GuiI18n.tr(player, "gui.showcase.wizard.cancelled"));
    });

    return wizard;
  }
}
