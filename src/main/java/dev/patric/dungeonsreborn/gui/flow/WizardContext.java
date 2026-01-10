package dev.patric.dungeonsreborn.gui.flow;

import java.util.Objects;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.Window;

/**
 * Context passed to {@link WizardStep#build(WizardContext)}.
 */
public final class WizardContext<S> {
  private final WizardWindow<S> wizard;
  private final Player player;

  WizardContext(WizardWindow<S> wizard, Player player) {
    this.wizard = Objects.requireNonNull(wizard, "wizard");
    this.player = Objects.requireNonNull(player, "player");
  }

  public WizardWindow<S> wizard() {
    return wizard;
  }

  public Window window() {
    return wizard;
  }

  public Player player() {
    return player;
  }

  public S state() {
    return wizard.state();
  }

  public int stepIndex() {
    return wizard.stepIndex();
  }

  public int stepCount() {
    return wizard.stepCount();
  }

  public void next() {
    wizard.next(player);
  }

  public void back() {
    wizard.back(player);
  }

  public void finish() {
    wizard.finish(player);
  }

  public void cancel() {
    wizard.cancel(player);
  }
}

