package dev.patric.dungeonsreborn.gui.style;

import java.util.Objects;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;

public final class GuiNav {
  private GuiNav() {
  }

  public static Window applyDetail(Window window) {
    return applyDetail(window, new BackButton(), new CloseButton());
  }

  public static Window applyDetail(Window window, GuiComponent back, GuiComponent close) {
    Objects.requireNonNull(window, "window");
    window.navLeft(back == null ? new BackButton() : back);
    window.navRight(close == null ? new CloseButton() : close);
    return window;
  }

  public static Window applyDetail(Window window, GuiComponent back, GuiComponent close, GuiComponent... actions) {
    applyDetail(window, back, close);
    if (actions != null) {
      for (int i = 0; i < actions.length && i < Window.NAV_EDITABLE_SLOTS; i++) {
        GuiComponent action = actions[i];
        if (action != null) {
          window.nav(i, action);
        }
      }
    }
    return window;
  }

  public static Window applyList(Window window, VirtualList<?> list) {
    return applyList(window, list, new BackButton(), new CloseButton());
  }

  public static Window applyList(Window window, VirtualList<?> list, GuiComponent back, GuiComponent close) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(list, "list");
    window.navLeft(back == null ? new BackButton() : back);
    window.navRight(close == null ? new CloseButton() : close);
    window.nav(0, list.prevButton());
    window.nav(1, list.pageIndicator());
    window.nav(2, list.nextButton());
    return window;
  }

  public static Window applyWizard(Window window, GuiComponent back, GuiComponent next, GuiComponent cancel) {
    Objects.requireNonNull(window, "window");
    window.navLeft(back == null ? new BackButton() : back);
    window.navRight(next == null ? new CloseButton() : next);
    if (cancel != null) {
      window.nav(Window.NAV_EDITABLE_SLOTS - 1, cancel);
    }
    return window;
  }
}
