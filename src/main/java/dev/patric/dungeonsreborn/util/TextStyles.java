package dev.patric.dungeonsreborn.util;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

public final class TextStyles {
  private TextStyles() {
  }

  public static Component noItalic(Component component) {
    Objects.requireNonNull(component, "component");
    return component.decoration(TextDecoration.ITALIC, false);
  }

  public static List<Component> noItalic(List<Component> components) {
    Objects.requireNonNull(components, "components");
    return components.stream().map(TextStyles::noItalic).toList();
  }
}
