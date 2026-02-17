package dev.patric.dungeonsreborn.gui;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import dev.patric.dungeonsreborn.util.TextStyles;

/**
 * Small MiniMessage helpers for GUI styling.
 */
public final class GuiMini {
  private static final MiniMessage MM = MiniMessage.miniMessage();

  private GuiMini() {
  }

  public static MiniMessage miniMessage() {
    return MM;
  }

  public static Component mm(String template) {
    Objects.requireNonNull(template, "template");
    return TextStyles.noItalic(MM.deserialize(normalize(template)));
  }

  public static Component mm(String template, TagResolver... resolvers) {
    Objects.requireNonNull(template, "template");
    return TextStyles.noItalic(MM.deserialize(normalize(template), resolvers));
  }

  private static String normalize(String template) {
    return template.replace("\\n", "<newline>").replace("\n", "<newline>");
  }

  public static List<Component> loreMm(List<String> linesMm, TagResolver... resolvers) {
    Objects.requireNonNull(linesMm, "linesMm");
    return linesMm.stream().map(line -> mm(line, resolvers)).toList();
  }
}
