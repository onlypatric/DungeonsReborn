package dev.patric.dungeonsreborn.gui.flow;

import java.util.Objects;

import net.kyori.adventure.text.Component;

/**
 * A single step in a {@link WizardWindow}.
 */
public interface WizardStep<S> {
  /**
   * Title shown for this step.
   */
  Component title();

  /**
   * Called during {@link WizardWindow#build(org.bukkit.entity.Player)} to populate the step UI.
   * <p>
   * Implementations should set dynamic components; navigation controls are handled by the wizard.
   */
  void build(WizardContext<S> ctx);

  /**
   * Convenience for simple steps.
   */
  static <S> WizardStep<S> of(Component title, java.util.function.Consumer<WizardContext<S>> build) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(build, "build");
    return new WizardStep<>() {
      @Override
      public Component title() {
        return title;
      }

      @Override
      public void build(WizardContext<S> ctx) {
        build.accept(ctx);
      }
    };
  }
}

