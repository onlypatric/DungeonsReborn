package dev.patric.dungeonsreborn.gui.style;

import dev.patric.dungeonsreborn.gui.components.BackButton;

public final class GuiNav {
  private GuiNav() {
  }

  public static BackButton backButton() {
    return new BackButton(p -> GuiButtons.back());
  }

  public static BackButton closeButton() {
    return new BackButton(p -> GuiButtons.close());
  }
}
