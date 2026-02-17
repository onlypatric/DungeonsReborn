package dev.patric.dungeonsreborn.gui.components.input;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.layout.Layout;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A small 3-slot numeric input: [-] [value] [+], with optional command typing.
 */
public final class NumericInput implements Layout {
  private final int row;
  private final int leftCol;
  private final ToIntFunction<Player> get;
  private final ObjIntConsumer<Player> set;

  private int min = Integer.MIN_VALUE;
  private int max = Integer.MAX_VALUE;
  private int step = 1;
  private int shiftStep = 10;
  private boolean allowTyping = true;
  private Duration typingTimeout = Duration.ofSeconds(30);

  private Material minusMaterial = Material.RED_STAINED_GLASS_PANE;
  private Material plusMaterial = Material.LIME_STAINED_GLASS_PANE;
  private Material valueMaterial = Material.PAPER;

  private Component label = Locales.component(null, "gui.numericInput.label");
  private Component typingPrompt = Locales.component(null, "gui.numericInput.prompt");

  public NumericInput(int row, int leftCol, ToIntFunction<Player> get, ObjIntConsumer<Player> set) {
    this.row = row;
    this.leftCol = leftCol;
    this.get = Objects.requireNonNull(get, "get");
    this.set = Objects.requireNonNull(set, "set");
  }

  public NumericInput range(int min, int max) {
    this.min = min;
    this.max = max;
    return this;
  }

  public NumericInput step(int step) {
    this.step = step;
    return this;
  }

  public NumericInput shiftStep(int shiftStep) {
    this.shiftStep = shiftStep;
    return this;
  }

  public NumericInput allowTyping(boolean allowTyping) {
    this.allowTyping = allowTyping;
    return this;
  }

  public NumericInput typingTimeout(Duration timeout) {
    this.typingTimeout = Objects.requireNonNull(timeout, "timeout");
    return this;
  }

  public NumericInput materials(Material minus, Material value, Material plus) {
    this.minusMaterial = Objects.requireNonNull(minus, "minus");
    this.valueMaterial = Objects.requireNonNull(value, "value");
    this.plusMaterial = Objects.requireNonNull(plus, "plus");
    return this;
  }

  public NumericInput label(Component label) {
    this.label = Objects.requireNonNull(label, "label");
    return this;
  }

  public NumericInput typingPrompt(Component prompt) {
    this.typingPrompt = Objects.requireNonNull(prompt, "prompt");
    return this;
  }

  @Override
  public void apply(Window window, Placement placement) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(placement, "placement");
    int minusSlot = window.slotAt(row, leftCol);
    int valueSlot = window.slotAt(row, leftCol + 1);
    int plusSlot = window.slotAt(row, leftCol + 2);

    place(window, placement, minusSlot, minusButton(minusSlot, valueSlot, plusSlot));
    place(window, placement, valueSlot, valueButton(window, minusSlot, valueSlot, plusSlot));
    place(window, placement, plusSlot, plusButton(minusSlot, valueSlot, plusSlot));
  }

  private void place(Window window, Placement placement, int slot, dev.patric.dungeonsreborn.gui.GuiComponent component) {
    if (placement == Placement.FIXED) {
      window.setFixed(slot, component);
    } else {
      window.setDynamic(slot, component);
    }
  }

  private Button minusButton(int minusSlot, int valueSlot, int plusSlot) {
    return new Button(p -> {
      int value = clamp(get.applyAsInt(p));
      boolean enabled = value > min;
      if (!enabled) {
        return GuiItems.named(Material.GRAY_DYE, Locales.component(p, "gui.numericInput.minus"),
            List.of(Locales.component(p, "gui.numericInput.min", Locales.placeholders("min", min))));
      }
      return GuiItems.named(minusMaterial, Locales.component(p, "gui.numericInput.minus"),
          List.of(Locales.component(p, "gui.numericInput.value", Locales.placeholders("value", value))));
    })
        .left(Locales.component(null, "gui.numericInput.stepMinus", Locales.placeholders("step", step)),
            ctx -> adjust(ctx.window(), ctx.player(), -step, minusSlot, valueSlot, plusSlot))
        .shiftLeft(Locales.component(null, "gui.numericInput.stepMinus", Locales.placeholders("step", shiftStep)),
            ctx -> adjust(ctx.window(), ctx.player(), -shiftStep, minusSlot, valueSlot, plusSlot))
        .autoDescribeInLore(true);
  }

  private Button plusButton(int minusSlot, int valueSlot, int plusSlot) {
    return new Button(p -> {
      int value = clamp(get.applyAsInt(p));
      boolean enabled = value < max;
      if (!enabled) {
        return GuiItems.named(Material.GRAY_DYE, Locales.component(p, "gui.numericInput.plus"),
            List.of(Locales.component(p, "gui.numericInput.max", Locales.placeholders("max", max))));
      }
      return GuiItems.named(plusMaterial, Locales.component(p, "gui.numericInput.plus"),
          List.of(Locales.component(p, "gui.numericInput.value", Locales.placeholders("value", value))));
    })
        .left(Locales.component(null, "gui.numericInput.stepPlus", Locales.placeholders("step", step)),
            ctx -> adjust(ctx.window(), ctx.player(), step, minusSlot, valueSlot, plusSlot))
        .shiftLeft(Locales.component(null, "gui.numericInput.stepPlus", Locales.placeholders("step", shiftStep)),
            ctx -> adjust(ctx.window(), ctx.player(), shiftStep, minusSlot, valueSlot, plusSlot))
        .autoDescribeInLore(true);
  }

  private dev.patric.dungeonsreborn.gui.GuiComponent valueButton(Window window, int minusSlot, int valueSlot, int plusSlot) {
    if (!allowTyping) {
      return new Button(p -> {
        int value = clamp(get.applyAsInt(p));
        return GuiItems.named(valueMaterial, label, List.of(
            Locales.component(p, "gui.numericInput.value", Locales.placeholders("value", value))));
      }).autoDescribeInLore(false);
    }

    return new TextButton(
        p -> {
          int value = clamp(get.applyAsInt(p));
          return GuiItems.named(valueMaterial, label, List.of(
              Locales.component(p, "gui.numericInput.value", Locales.placeholders("value", value)),
              Locales.component(p, "gui.numericInput.clickToType")));
        },
        typingPrompt,
        Locales.text(null, "gui.textInput.cancelWord"),
        typingTimeout,
        (w, text) -> {
          // Parse is validated before acceptance by the validators below.
          Player player = w.viewer() == null ? null : Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          int parsed = Integer.parseInt(text);
          set.accept(player, clamp(parsed));
          // Ensure the trio reflects the new value even if the window isn't fully redrawn.
          w.redrawSlot(player, minusSlot);
          w.redrawSlot(player, valueSlot);
          w.redrawSlot(player, plusSlot);
        },
        true)
            .integerRange(min, max);
  }

  private void adjust(Window window, Player player, int delta, int minusSlot, int valueSlot, int plusSlot) {
    int current = clamp(get.applyAsInt(player));
    int next = clamp(current + delta);
    if (next == current) {
      return;
    }
    set.accept(player, next);
    window.redrawSlot(player, minusSlot);
    window.redrawSlot(player, valueSlot);
    window.redrawSlot(player, plusSlot);
  }

  private int clamp(int value) {
    return Math.max(min, Math.min(max, value));
  }
}
