package dev.patric.dungeonsreborn.gui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import net.kyori.adventure.text.Component;

/**
 * A minimal multi-step "wizard" window. Steps are rendered inside a single window instance and the user can navigate
 * back/next (and finish on the last step).
 * <p>
 * This is a per-player concept (same as {@link Window}): create one wizard instance per player.
 */
public final class WizardWindow<S> extends Window {
  private final List<WizardStep<S>> steps = new ArrayList<>();
  private final Supplier<S> initialState;

  private S state;
  private int stepIndex;
  private boolean finishedOrCancelled;

  private BiConsumer<Player, S> onFinish = (p, s) -> {
  };
  private BiConsumer<Player, S> onCancel = (p, s) -> {
  };

  public WizardWindow(int size, Component title, Supplier<S> initialState) {
    super(size, Objects.requireNonNull(title, "title"), true);
    this.initialState = Objects.requireNonNull(initialState, "initialState");
    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    // Top bar
    setFixed(8, new CloseButton().autoDescribeInLore(false));
    setFixed(4, new Label(p -> GuiItems.named(Material.PAPER, currentStepTitle())));

    // Bottom nav bar (uses Window's nav helpers)
    navLeft(new Button(p -> navBackItem(), ctx -> back(ctx.player())).autoDescribeInLore(false));
    navRight(new Button(p -> navNextItem(), ctx -> next(ctx.player())).autoDescribeInLore(false));
    nav(3, new Label(p -> GuiItems.named(Material.PAPER, pageText())));

    onClose(player -> {
      if (!finishedOrCancelled) {
        cancel(player);
      }
    });
  }

  public WizardWindow<S> step(WizardStep<S> step) {
    steps.add(Objects.requireNonNull(step, "step"));
    return this;
  }

  public WizardWindow<S> steps(List<WizardStep<S>> steps) {
    this.steps.clear();
    this.steps.addAll(Objects.requireNonNull(steps, "steps"));
    return this;
  }

  public WizardWindow<S> onFinish(BiConsumer<Player, S> onFinish) {
    this.onFinish = Objects.requireNonNull(onFinish, "onFinish");
    return this;
  }

  public WizardWindow<S> onCancel(BiConsumer<Player, S> onCancel) {
    this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
    return this;
  }

  public S state() {
    if (state == null) {
      state = initialState.get();
    }
    return state;
  }

  public int stepIndex() {
    return stepIndex;
  }

  public int stepCount() {
    return Math.max(1, steps.size());
  }

  public boolean hasPrevious() {
    return stepIndex > 0;
  }

  public boolean hasNext() {
    return stepIndex + 1 < steps.size();
  }

  public void next(Player player) {
    Objects.requireNonNull(player, "player");
    if (steps.isEmpty()) {
      return;
    }
    if (hasNext()) {
      stepIndex++;
      redraw(player);
      return;
    }
    finish(player);
  }

  public void back(Player player) {
    Objects.requireNonNull(player, "player");
    if (!hasPrevious()) {
      return;
    }
    stepIndex--;
    redraw(player);
  }

  public void finish(Player player) {
    Objects.requireNonNull(player, "player");
    if (finishedOrCancelled) {
      return;
    }
    finishedOrCancelled = true;
    onFinish.accept(player, state());
    player.closeInventory();
  }

  public void cancel(Player player) {
    Objects.requireNonNull(player, "player");
    if (finishedOrCancelled) {
      return;
    }
    finishedOrCancelled = true;
    onCancel.accept(player, state());
    player.closeInventory();
  }

  @Override
  protected void build(Player player) {
    if (steps.isEmpty()) {
      setDynamic(13, new Label(p -> GuiItems.named(Material.BARRIER, Component.text("No steps configured"))));
      return;
    }
    stepIndex = Math.max(0, Math.min(stepIndex, steps.size() - 1));

    WizardStep<S> step = steps.get(stepIndex);
    setFixed(4, new Label(p -> GuiItems.named(Material.PAPER, currentStepTitle())));
    step.build(new WizardContext<>(this, player));
  }

  private Component currentStepTitle() {
    if (steps.isEmpty()) {
      return Component.text("Wizard");
    }
    return steps.get(Math.max(0, Math.min(stepIndex, steps.size() - 1))).title();
  }

  private Component pageText() {
    if (steps.isEmpty()) {
      return Component.text("Step 0/0");
    }
    return Component.text("Step " + (stepIndex + 1) + "/" + steps.size());
  }

  private org.bukkit.inventory.ItemStack navBackItem() {
    if (!hasPrevious()) {
      return GuiItems.named(Material.GRAY_DYE, Component.text("Back"), List.of(pageText(), Component.text("No previous step")));
    }
    return GuiItems.named(Material.ARROW, Component.text("Back"), List.of(pageText()));
  }

  private org.bukkit.inventory.ItemStack navNextItem() {
    if (steps.isEmpty()) {
      return GuiItems.named(Material.GRAY_DYE, Component.text("Next"), List.of(Component.text("No steps")));
    }
    if (hasNext()) {
      return GuiItems.named(Material.ARROW, Component.text("Next"), List.of(pageText()));
    }
    return GuiItems.named(Material.LIME_CONCRETE, Component.text("Finish"), List.of(pageText()));
  }
}

