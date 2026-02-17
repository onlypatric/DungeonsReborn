package dev.patric.dungeonsreborn.gui.components;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public final class TextButton extends Button {
  private static final String DEFAULT_CANCEL_WORD_KEY = "gui.textInput.cancelWord";

  public enum InputMode {
    CHAT,
    ANVIL,
    SIGN
  }

  @FunctionalInterface
  public interface TextValidator {
    /**
     * @return a validation error message, or {@code null} when valid.
     */
    Component validate(Window window, Player player, String input);
  }

  private final Component prompt;
  private final String cancelWord;
  private final Duration timeout;
  private final BiConsumer<Window, String> onText;
  private final boolean reopen;
  private final List<TextValidator> validators = new ArrayList<>();
  private Component retryPrompt;
  private boolean showTitleOnPress;
  private InputMode inputMode = InputMode.CHAT;
  private Component anvilTitle = Locales.component(null, "gui.textInput.anvilTitle");
  private Function<Player, String> initialText = p -> "";
  private List<Component> signInitialLines = List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty());
  private Side signSide = Side.FRONT;
  private Function<List<String>, String> signToText = lines -> String.join("\n", lines).trim();
  private BiConsumer<Window, Player> onCancel = (w, p) -> {
  };
  private BiConsumer<Window, Player> onTimeout = (w, p) -> {
  };

  public TextButton(ItemStack item, Component prompt, BiConsumer<Window, String> onText) {
    this(item, prompt, Locales.text(null, DEFAULT_CANCEL_WORD_KEY), Duration.ofSeconds(30), onText, true);
  }

  public TextButton(ItemStack item, Component prompt, String cancelWord, Duration timeout, BiConsumer<Window, String> onText,
      boolean reopen) {
    this(p -> item, prompt, cancelWord, timeout, onText, reopen);
  }

  public TextButton(Function<Player, ItemStack> item, Component prompt, String cancelWord, Duration timeout,
      BiConsumer<Window, String> onText, boolean reopen) {
    super(item);
    this.prompt = Objects.requireNonNull(prompt, "prompt");
    this.cancelWord = Objects.requireNonNull(cancelWord, "cancelWord");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.onText = Objects.requireNonNull(onText, "onText");
    this.reopen = reopen;
    this.retryPrompt = prompt;

    Component desc = Locales.component(null, "gui.textInput.tooltip");
    bind(ClickType.LEFT, desc, this::start);
    bind(ClickType.SHIFT_LEFT, desc, this::start);
  }

  public TextButton showTitleOnPress(boolean enabled) {
    this.showTitleOnPress = enabled;
    return this;
  }

  public TextButton inputMode(InputMode mode) {
    this.inputMode = Objects.requireNonNull(mode, "mode");
    return this;
  }

  public TextButton anvilTitle(Component title) {
    this.anvilTitle = Objects.requireNonNull(title, "title");
    return this;
  }

  public TextButton initialText(String initialText) {
    Objects.requireNonNull(initialText, "initialText");
    return initialText(p -> initialText);
  }

  public TextButton initialText(Function<Player, String> initialText) {
    this.initialText = Objects.requireNonNull(initialText, "initialText");
    return this;
  }

  public TextButton signInitialLines(List<Component> lines) {
    this.signInitialLines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    return this;
  }

  public TextButton signSide(Side side) {
    this.signSide = Objects.requireNonNull(side, "side");
    return this;
  }

  public TextButton signToText(Function<List<String>, String> mapper) {
    this.signToText = Objects.requireNonNull(mapper, "mapper");
    return this;
  }

  /**
   * Called when the player cancels the input (types the cancel word).
   */
  public TextButton onCancel(BiConsumer<Window, Player> handler) {
    this.onCancel = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public TextButton onCancel(Consumer<Player> handler) {
    Objects.requireNonNull(handler, "handler");
    return onCancel((w, p) -> handler.accept(p));
  }

  /**
   * Called when the input times out.
   */
  public TextButton onTimeout(BiConsumer<Window, Player> handler) {
    this.onTimeout = Objects.requireNonNull(handler, "handler");
    return this;
  }

  public TextButton onTimeout(Consumer<Player> handler) {
    Objects.requireNonNull(handler, "handler");
    return onTimeout((w, p) -> handler.accept(p));
  }

  /**
   * Adds a validator for this text input. If validation fails, the player is shown the returned message and asked again.
   */
  public TextButton validate(TextValidator validator) {
    validators.add(Objects.requireNonNull(validator, "validator"));
    return this;
  }

  /**
   * Prompt to show after a validation failure (defaults to the initial {@link #prompt}).
   */
  public TextButton retryPrompt(Component prompt) {
    this.retryPrompt = Objects.requireNonNull(prompt, "prompt");
    return this;
  }

  public TextButton minLength(int min) {
    if (min < 0) {
      throw new IllegalArgumentException("min must be >= 0");
    }
    return validate((window, player, input) -> input.length() >= min
        ? null
        : Locales.component(player, "gui.textInput.error.minLength", Locales.placeholders("min", min)));
  }

  public TextButton maxLength(int max) {
    if (max < 0) {
      throw new IllegalArgumentException("max must be >= 0");
    }
    return validate((window, player, input) -> input.length() <= max
        ? null
        : Locales.component(player, "gui.textInput.error.maxLength", Locales.placeholders("max", max)));
  }

  public TextButton matchesRegex(String regex) {
    Objects.requireNonNull(regex, "regex");
    Pattern pattern = Pattern.compile(regex);
    return validate((window, player, input) -> pattern.matcher(input).matches()
        ? null
        : Locales.component(player, "gui.textInput.error.invalidFormat"));
  }

  public TextButton integer() {
    return validate((window, player, input) -> {
      try {
        Integer.parseInt(input);
        return null;
      } catch (NumberFormatException ex) {
        return Locales.component(player, "gui.textInput.error.integer");
      }
    });
  }

  public TextButton integerRange(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException("min must be <= max");
    }
    return validate((window, player, input) -> {
      int value;
      try {
        value = Integer.parseInt(input);
      } catch (NumberFormatException ex) {
        return Locales.component(player, "gui.textInput.error.integer");
      }
      if (value < min || value > max) {
        return Locales.component(player, "gui.textInput.error.integerRange", Locales.placeholders("min", min, "max", max));
      }
      return null;
    });
  }

  private void start(Window.ClickContext ctx) {
    if (showTitleOnPress) {
      Component title = Locales.component(ctx.player(), "gui.textInput.title");
      ctx.player().showTitle(Title.title(title, prompt));
    }

    GuiManager.get().debug("TextButton: press player=" + ctx.player().getName() + " window=" + ctx.window().getClass().getSimpleName());
    if (inputMode == InputMode.CHAT) {
      if (reopen) {
        // Close the inventory while the player types, but keep the window on the stack so we can reopen it afterwards.
        GuiManager.get().prepareTemporaryClose(ctx.player());
        ctx.close();
      }
      requestChat(ctx.window(), ctx.player(), prompt);
      return;
    }

    // Anvil/sign input requires leaving the current inventory UI.
    if (reopen) {
      GuiManager.get().prepareTemporaryClose(ctx.player());
    }
    ctx.close();

    if (inputMode == InputMode.ANVIL) {
      requestAnvil(ctx.window(), ctx.player(), prompt);
    } else if (inputMode == InputMode.SIGN) {
      requestSign(ctx.window(), ctx.player(), prompt);
    }
  }

  private void requestChat(Window window, Player player, Component promptToUse) {
    GuiManager.get().requestText(player,
        new GuiManager.TextRequest(
            promptToUse,
            cancelWord,
            timeout,
            true,
            (p, text) -> handleText(window, p, text),
            p -> handleCancel(window, p),
            p -> handleTimeout(window, p)));
  }

  private void requestAnvil(Window window, Player player, Component promptToUse) {
    GuiManager.get().requestTextAnvil(player,
        new GuiManager.AnvilRequest(
            anvilTitle,
            promptToUse,
            Objects.toString(initialText.apply(player), ""),
            timeout,
            (p, text) -> handleText(window, p, text),
            p -> handleCancel(window, p),
            p -> handleTimeout(window, p)));
  }

  private void requestSign(Window window, Player player, Component promptToUse) {
    GuiManager.get().requestTextSign(player,
        new GuiManager.SignRequest(
            promptToUse,
            signInitialLines,
            signSide,
            timeout,
            (p, lines) -> {
              String text = signToText.apply(lines);
              handleText(window, p, text == null ? "" : text);
            },
            p -> handleCancel(window, p),
            p -> handleTimeout(window, p)));
  }

  private void handleText(Window window, Player player, String text) {
    GuiManager.get().debug("TextButton: onText player=" + player.getName() + " text=\"" + text + "\"");

    Component error = validateAll(window, player, text);
    if (error != null) {
      GuiManager.get().debug("TextButton: invalid player=" + player.getName());
      GuiSounds.error(player);
      player.sendMessage(error);
      Component nextPrompt = retryPrompt == null ? prompt : retryPrompt;
      if (inputMode == InputMode.CHAT) {
        requestChat(window, player, nextPrompt);
      } else if (inputMode == InputMode.ANVIL) {
        requestAnvil(window, player, nextPrompt);
      } else {
        requestSign(window, player, nextPrompt);
      }
      return;
    }

    onText.accept(window, text);
    if (reopen) {
      GuiManager.get().resume(player, window, "TextButton.onText");
    }
  }

  private void handleCancel(Window window, Player player) {
    GuiManager.get().debug("TextButton: onCancel player=" + player.getName());
    try {
      onCancel.accept(window, player);
    } catch (Exception ex) {
      GuiManager.get().debug("TextButton: onCancel handler threw", ex);
    }
    if (reopen) {
      GuiManager.get().resume(player, window, "TextButton.onCancel");
    }
  }

  private void handleTimeout(Window window, Player player) {
    GuiManager.get().debug("TextButton: onTimeout player=" + player.getName());
    try {
      onTimeout.accept(window, player);
    } catch (Exception ex) {
      GuiManager.get().debug("TextButton: onTimeout handler threw", ex);
    }
    if (reopen) {
      GuiManager.get().resume(player, window, "TextButton.onTimeout");
    }
  }

  private Component validateAll(Window window, Player player, String text) {
    if (validators.isEmpty()) {
      return null;
    }
    for (TextValidator validator : validators) {
      Component error = validator.validate(window, player, text);
      if (error != null) {
        return error;
      }
    }
    return null;
  }
}
