package dev.patric.dungeonsreborn.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.layout.Layout;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;

/**
 * A compact action row for editor-like menus (save/test/preview/reset).
 */
public final class ActionRow implements Layout {
  private static final int DEFAULT_GAP = 1;

  private final int row;
  private final int startCol;
  private final int gap;
  private final List<GuiComponent> components = new ArrayList<>();

  public ActionRow(int row, int startCol) {
    this(row, startCol, DEFAULT_GAP);
  }

  public ActionRow(int row, int startCol, int gap) {
    this.row = row;
    this.startCol = startCol;
    this.gap = Math.max(0, gap);
  }

  public ActionRow save(Consumer<Window.ClickContext> onSave) {
    return action(GuiButtons.Type.SAVE,
        "gui.action.save.title",
        "gui.action.save.desc",
        "gui.action.save.action",
        onSave);
  }

  public ActionRow preview(Consumer<Window.ClickContext> onPreview) {
    return action(GuiButtons.Type.PREVIEW,
        "gui.action.preview.title",
        "gui.action.preview.desc",
        "gui.action.preview.action",
        onPreview);
  }

  public ActionRow test(Consumer<Window.ClickContext> onTest) {
    return action(GuiButtons.Type.TEST,
        "gui.action.test.title",
        "gui.action.test.desc",
        "gui.action.test.action",
        onTest);
  }

  public ActionRow reset(Consumer<Window.ClickContext> onReset) {
    return action(GuiButtons.Type.RESET,
        "gui.action.reset.title",
        "gui.action.reset.desc",
        "gui.action.reset.action",
        onReset);
  }

  public ActionRow action(GuiButtons.Type type, String titleKey, String descKey, String actionKey,
      Consumer<Window.ClickContext> handler) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(titleKey, "titleKey");
    Objects.requireNonNull(descKey, "descKey");
    Objects.requireNonNull(actionKey, "actionKey");
    Objects.requireNonNull(handler, "handler");
    components.add(new Button(player -> GuiButtons.item(type,
        Locales.component(player, titleKey),
        List.of(Locales.component(player, descKey))))
            .left(Locales.component(null, actionKey), handler));
    return this;
  }

  @Override
  public void apply(Window window, Placement placement) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(placement, "placement");
    int col = startCol;
    for (GuiComponent component : components) {
      if (component == null) {
        col += 1 + gap;
        continue;
      }
      int slot = window.slotAt(row, col);
      if (placement == Placement.FIXED) {
        window.setFixed(slot, component);
      } else {
        window.setDynamic(slot, component);
      }
      col += 1 + gap;
    }
  }
}
