package dev.patric.dungeonsreborn.gui.style;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.event.inventory.ClickType;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.components.Button;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Opinionated styling helpers for the GUI mini-lib.
 * <p>
 * Provides:
 * <ul>
 * <li>10 common {@link GuiTheme} values</li>
 * <li>Consistent "Controls" lore formatting for {@link Button}</li>
 * <li>Default click title provider (Left-click, Right-click, ...)</li>
 * </ul>
 */
public final class GuiStyles {
  private GuiStyles() {
  }

  /**
   * Installs global defaults for {@link Button} control lore styling and click title labels.
   * <p>
   * This is intentionally global (not per-player) to keep the library simple.
   */
  public static void installButtonDefaults() {
    Button.setDefaultControlsFormatter(defaultControlsFormatter());
    Button.setDefaultTitleProvider(defaultTitleProvider());
  }

  public static Button.ControlsFormatter defaultControlsFormatter() {
    Component header = GuiI18n.tr("gui.controls.header");
    Component bullet = GuiI18n.tr("gui.controls.bullet");
    Component separator = GuiI18n.tr("gui.controls.separator");
    Component continuation = GuiI18n.tr("gui.controls.continuation");
    boolean blankLine = Boolean.parseBoolean(GuiI18n.str(GuiI18n.defaultLocale(), "gui.controls.blank_line"));
    return new Button.ControlsFormat(header, bullet, separator, continuation, blankLine);
  }

  public static Function<ClickType, Component> defaultTitleProvider() {
    return type -> GuiMini.mm("<gray><text></gray>", Placeholder.unparsed("text", clickLabel(type)));
  }

  public static Component hint(GuiTheme theme, String text) {
    Objects.requireNonNull(theme, "theme");
    Objects.requireNonNull(text, "text");
    return theme.loreLine(text);
  }

  public static List<Component> bulletList(String... lines) {
    Objects.requireNonNull(lines, "lines");
    return java.util.Arrays.stream(lines)
        .map(s -> GuiMini.mm("<dark_gray>• </dark_gray><gray><text></gray>", Placeholder.unparsed("text", s)))
        .toList();
  }

  private static String clickLabel(ClickType type) {
    if (type == null) {
      return "Click";
    }
    return switch (type) {
      case LEFT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.left");
      case SHIFT_LEFT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.shift_left");
      case RIGHT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.right");
      case SHIFT_RIGHT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.shift_right");
      default -> type.name();
    };
  }
}

