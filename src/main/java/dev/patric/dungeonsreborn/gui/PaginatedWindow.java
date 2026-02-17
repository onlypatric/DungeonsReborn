package dev.patric.dungeonsreborn.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.components.NextPageButton;
import dev.patric.dungeonsreborn.gui.components.PageIndicator;
import dev.patric.dungeonsreborn.gui.components.PrevPageButton;
import net.kyori.adventure.text.Component;

public class PaginatedWindow extends Window {
  /**
   * A {@link Window} that maps a list of entries onto a configurable set of content slots.
   * <p>
   * Layout convention:
   * <ul>
   * <li>Menu bar: top row (slots 0..8)</li>
   * <li>Content area: middle rows ({@link #defaultContentSlots(int)})</li>
   * <li>Navigation bar: bottom row (prev + 7 editable slots + next)</li>
   * </ul>
   */
  public record Entry(GuiComponent component) {
    public Entry {
      Objects.requireNonNull(component, "component");
    }
  }

  public static final int MENU_BAR_SIZE = 9;

  private List<Entry> entries = List.of();
  private int page = 0;
  private final List<Integer> contentSlots;

  public PaginatedWindow(int size, Component title) {
    this(size, title, defaultContentSlots(size));
  }

  public PaginatedWindow(int size, Component title, List<Integer> contentSlots) {
    super(size, title, true);
    this.contentSlots = List.copyOf(Objects.requireNonNull(contentSlots, "contentSlots"));
    installDefaultPrevNextControls();
  }

  /**
   * Installs the standard navigation controls (prev/next + page indicator).
   * <p>
   * Prev/next are in the left/right navbar control slots and the page indicator is placed in the editable navbar area.
   */
  public PaginatedWindow defaultNavControls() {
    return defaultNavControls(3);
  }

  /**
   * Installs the standard navigation controls (prev/next + page indicator) with a custom page indicator position.
   *
   * @param pageIndicatorNavIndex editable navbar index 0..6
   */
  public PaginatedWindow defaultNavControls(int pageIndicatorNavIndex) {
    installDefaultPrevNextControls();
    nav(pageIndicatorNavIndex, defaultPageIndicator());
    return this;
  }

  public void setEntries(List<Entry> entries) {
    this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    clampPage();
  }

  /**
   * Sets a component in the top menu bar (slots 0..8).
   */
  public PaginatedWindow menu(int slot, GuiComponent component) {
    if (slot < 0 || slot >= MENU_BAR_SIZE) {
      throw new IllegalArgumentException("menu slot out of bounds: " + slot);
    }
    setFixed(slot, Objects.requireNonNull(component, "component"));
    return this;
  }

  /**
   * Convenience override so chaining keeps the {@code PaginatedWindow} type.
   */
  @Override
  public PaginatedWindow nav(int index, GuiComponent component) {
    super.nav(index, component);
    return this;
  }

  public void resetPage() {
    page = 0;
  }

  public int page() {
    return page;
  }

  public int pageCount() {
    if (entries.isEmpty()) {
      return 1;
    }
    int perPage = contentSlots.size();
    if (perPage == 0) {
      return 1;
    }
    return (entries.size() + perPage - 1) / perPage;
  }

  public boolean hasPrevious() {
    return page > 0;
  }

  public boolean hasNext() {
    return page + 1 < pageCount();
  }

  public void previous(Player player) {
    if (!hasPrevious()) {
      return;
    }
    page--;
    redraw(player);
  }

  public void next(Player player) {
    if (!hasNext()) {
      return;
    }
    page++;
    redraw(player);
  }

  public void page(Player player, int page) {
    this.page = Math.max(0, Math.min(page, pageCount() - 1));
    redraw(player);
  }

  @Override
  protected void build(Player player) {
    int perPage = contentSlots.size();
    if (perPage == 0) {
      return;
    }
    int start = page * perPage;
    int end = Math.min(start + perPage, entries.size());

    int i = 0;
    for (int idx = start; idx < end; idx++) {
      int slot = contentSlots.get(i++);
      setDynamic(slot, entries.get(idx).component());
    }
  }

  public static List<Integer> defaultContentSlots(int size) {
    if (size < 9 || size % 9 != 0) {
      throw new IllegalArgumentException("size must be a positive multiple of 9");
    }
    if (size == 9) {
      return Collections.emptyList();
    }

    // Top row is a menu bar, bottom row is navigation.
    int contentStart = 9;
    int contentEndExclusive = Math.max(contentStart, size - 9);
    List<Integer> slots = new ArrayList<>(Math.max(0, contentEndExclusive - contentStart));
    for (int slot = contentStart; slot < contentEndExclusive; slot++) {
      slots.add(slot);
    }
    return slots;
  }

  private void clampPage() {
    page = Math.max(0, Math.min(page, pageCount() - 1));
  }

  protected void installDefaultPrevNextControls() {
    navLeft(new PrevPageButton(this));
    navRight(new NextPageButton(this));
  }

  protected GuiComponent defaultPageIndicator() {
    return new PageIndicator(this);
  }
}
