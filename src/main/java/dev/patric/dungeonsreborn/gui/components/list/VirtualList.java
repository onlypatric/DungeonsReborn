package dev.patric.dungeonsreborn.gui.components.list;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.LoadingIndicator;
import dev.patric.dungeonsreborn.gui.components.StatusPanel;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;

/**
 * A virtualized scrollable list for inventory GUIs.
 * <p>
 * "Scrollable" here means paging controls (prev/next) with an optional text filter.
 * The list is virtualized: only the visible page is rendered into the GUI, and components are reused.
 */
public final class VirtualList<T> {
  @FunctionalInterface
  public interface EntryRenderer<T> {
    ItemStack render(Player player, T entry);
  }

  @FunctionalInterface
  public interface EntryClickHandler<T> {
    void onClick(Window.ClickContext ctx, T entry);
  }

  private static final Predicate<?> ALWAYS_TRUE = t -> true;

  private final int topRow;
  private final int leftCol;
  private final int rows;
  private final int cols;
  private final Function<Player, List<T>> entries;
  private final EntryRenderer<T> renderer;
  private final EntryClickHandler<T> onClick;

  private Predicate<T> globalFilter = cast(ALWAYS_TRUE);
  private Function<T, String> searchKey;
  private int maxEntries = 2000;

  private final Map<UUID, State<T>> stateByPlayer = new ConcurrentHashMap<>();

  private ItemStack emptyCellItem = GuiItem.of(Material.GRAY_STAINED_GLASS_PANE).displayName(Component.text(" ")).build();
  private Function<Player, ItemStack> emptyStateItem;
  private Function<Player, ItemStack> loadingStateItem = LoadingIndicator.item();

  // Mounted slots for targeted redraw.
  private final int[] cellSlots;
  private int prevSlot = -1;
  private int nextSlot = -1;
  private int pageIndicatorSlot = -1;

  private Component tr(Player player, String key, Object... pairs) {
    return Locales.component(player, key, Locales.placeholders(pairs));
  }

  public VirtualList(int topRow, int leftCol, int rows, int cols,
      Function<Player, List<T>> entries,
      EntryRenderer<T> renderer,
      EntryClickHandler<T> onClick) {
    if (rows <= 0 || cols <= 0) {
      throw new IllegalArgumentException("rows/cols must be > 0");
    }
    this.topRow = topRow;
    this.leftCol = leftCol;
    this.rows = rows;
    this.cols = cols;
    this.entries = Objects.requireNonNull(entries, "entries");
    this.renderer = Objects.requireNonNull(renderer, "renderer");
    this.onClick = Objects.requireNonNull(onClick, "onClick");
    this.cellSlots = new int[rows * cols];
    java.util.Arrays.fill(this.cellSlots, -1);
    this.emptyStateItem = StatusPanel.item(StatusPanel.Type.INFO,
        tr(null, "gui.list.empty.title"),
        List.of(tr(null, "gui.list.empty.hint")));
  }

  public int pageSize() {
    return rows * cols;
  }

  public T visibleEntry(Player player, int index) {
    Objects.requireNonNull(player, "player");
    if (index < 0 || index >= pageSize()) {
      return null;
    }
    State<T> state = state(player);
    resolve(player, state);
    Resolved<T> resolved = state.cache;
    if (resolved == null || resolved.filtered().isEmpty()) {
      return null;
    }
    int start = resolved.page() * pageSize();
    int absoluteIndex = start + index;
    if (absoluteIndex < 0 || absoluteIndex >= resolved.filtered().size()) {
      return null;
    }
    return resolved.filtered().get(absoluteIndex);
  }

  public VirtualList<T> emptyCellItem(ItemStack item) {
    this.emptyCellItem = Objects.requireNonNull(item, "item").clone();
    return this;
  }

  public VirtualList<T> emptyStateItem(Function<Player, ItemStack> item) {
    this.emptyStateItem = Objects.requireNonNull(item, "item");
    return this;
  }

  public VirtualList<T> emptyStateItem(ItemStack item) {
    Objects.requireNonNull(item, "item");
    return emptyStateItem(p -> item);
  }

  public VirtualList<T> loadingStateItem(Function<Player, ItemStack> item) {
    this.loadingStateItem = Objects.requireNonNull(item, "item");
    return this;
  }

  public VirtualList<T> loadingStateItem(ItemStack item) {
    Objects.requireNonNull(item, "item");
    return loadingStateItem(p -> item);
  }

  public VirtualList<T> filter(Predicate<T> predicate) {
    this.globalFilter = Objects.requireNonNull(predicate, "predicate");
    return this;
  }

  /**
   * Enables built-in text search filtering when {@link #query(Player, String)} is used.
   */
  public VirtualList<T> searchKey(Function<T, String> extractor) {
    this.searchKey = Objects.requireNonNull(extractor, "extractor");
    return this;
  }

  public VirtualList<T> maxEntries(int maxEntries) {
    this.maxEntries = Math.max(0, maxEntries);
    return this;
  }

  public int page(Player player) {
    return state(player).page;
  }

  public VirtualList<T> page(Player player, int page) {
    State<T> state = state(player);
    state.page = Math.max(0, page);
    state.invalidate();
    return this;
  }

  public String query(Player player) {
    return state(player).query;
  }

  public VirtualList<T> query(Player player, String query) {
    State<T> state = state(player);
    state.query = Objects.requireNonNullElse(query, "").trim();
    state.page = 0;
    state.invalidate();
    return this;
  }

  public VirtualList<T> clearFilter(Player player) {
    State<T> state = state(player);
    state.query = "";
    state.playerFilter = null;
    state.page = 0;
    state.invalidate();
    return this;
  }

  /**
   * Optional per-player predicate filter. Applied in addition to the global filter.
   */
  public VirtualList<T> filter(Player player, Predicate<T> predicate) {
    State<T> state = state(player);
    state.playerFilter = Objects.requireNonNull(predicate, "predicate");
    state.page = 0;
    state.invalidate();
    return this;
  }

  /**
   * Invalidates cached computed entries for this player.
   * <p>
   * Call this if your entry list changes outside of the GUI redraw flow.
   */
  public void invalidate(Player player) {
    state(player).invalidate();
  }

  public void invalidateAll() {
    for (State<T> state : stateByPlayer.values()) {
      state.invalidate();
    }
  }

  /**
   * Places the list cells into the given window at the configured position.
   */
  public void apply(Window window, Placement placement) {
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(placement, "placement");

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int idx = r * cols + c;
        int slot = window.slotAt(topRow + r, leftCol + c);
        place(window, placement, slot, new Cell(idx));
      }
    }
  }

  /**
   * A "Previous Page" control button. Place it wherever you want (e.g. window.navLeft(...)).
   */
  public GuiComponent prevButton() {
    return new PrevNavButton();
  }

  private final class PrevNavButton extends Button {
    private PrevNavButton() {
      super(p -> {
      State<T> state = state(p);
      resolve(p, state);
      boolean enabled = state.page > 0;
      Component name = tr(p, "gui.list.prev.title");
      Component lore = tr(p, "gui.list.page", "current", state.page + 1, "total", Math.max(1, state.lastTotalPages));
      List<Component> loreList = new ArrayList<>();
      loreList.add(lore);
      if (!enabled) {
        loreList.add(tr(p, "gui.list.prev.disabled"));
      }
      return GuiButtons.item(GuiButtons.Type.PREV, name, loreList);
      });
      left(tr(null, "gui.list.prev.action"), ctx -> {
        State<T> state = state(ctx.player());
        if (state.page <= 0) {
          return;
        }
        state.page--;
        state.invalidate();
        redraw(ctx.window(), ctx.player());
      }).autoDescribeInLore(true);
    }

    @Override
    public void mounted(Window window, int slot) {
      registerPrevSlot(slot);
    }
  }

  /**
   * A "Next Page" control button. Place it wherever you want (e.g. window.navRight(...)).
   */
  public GuiComponent nextButton() {
    return new NextNavButton();
  }

  private final class NextNavButton extends Button {
    private NextNavButton() {
      super(p -> {
      State<T> state = state(p);
      resolve(p, state);
      boolean enabled = state.page + 1 < Math.max(1, state.lastTotalPages);
      Component name = tr(p, "gui.list.next.title");
      Component lore = tr(p, "gui.list.page", "current", state.page + 1, "total", Math.max(1, state.lastTotalPages));
      List<Component> loreList = new ArrayList<>();
      loreList.add(lore);
      if (!enabled) {
        loreList.add(tr(p, "gui.list.next.disabled"));
      }
      return GuiButtons.item(GuiButtons.Type.NEXT, name, loreList);
      });
      left(tr(null, "gui.list.next.action"), ctx -> {
        State<T> state = state(ctx.player());
        resolve(ctx.player(), state);
        if (state.page + 1 >= Math.max(1, state.lastTotalPages)) {
          return;
        }
        state.page++;
        state.invalidate();
        redraw(ctx.window(), ctx.player());
      }).autoDescribeInLore(true);
    }

    @Override
    public void mounted(Window window, int slot) {
      registerNextSlot(slot);
    }
  }

  /**
   * A page indicator component (no click handler).
   */
  public GuiComponent pageIndicator() {
    return new PageIndicator();
  }

  /**
   * Redraws only the list area and any mounted controls (prev/next/indicator).
   */
  public void redraw(Window window, Player player) {
    for (int slot : cellSlots) {
      if (slot >= 0) {
        window.redrawSlot(player, slot);
      }
    }
    if (prevSlot >= 0) {
      window.redrawSlot(player, prevSlot);
    }
    if (nextSlot >= 0) {
      window.redrawSlot(player, nextSlot);
    }
    if (pageIndicatorSlot >= 0) {
      window.redrawSlot(player, pageIndicatorSlot);
    }
  }

  private void registerCellSlot(int index, int slot) {
    if (index < 0 || index >= cellSlots.length) {
      return;
    }
    cellSlots[index] = slot;
  }

  private void registerPrevSlot(int slot) {
    if (prevSlot < 0) {
      prevSlot = slot;
    }
  }

  private void registerNextSlot(int slot) {
    if (nextSlot < 0) {
      nextSlot = slot;
    }
  }

  private void registerPageIndicatorSlot(int slot) {
    if (pageIndicatorSlot < 0) {
      pageIndicatorSlot = slot;
    }
  }

  private State<T> state(Player player) {
    return stateByPlayer.computeIfAbsent(player.getUniqueId(), id -> new State<>());
  }

  private void resolve(Player player, State<T> state) {
    int currentTick = Bukkit.getCurrentTick();
    if (state.cacheTick == currentTick && state.cache != null) {
      return;
    }

    List<T> raw = entries.apply(player);
    state.loading = raw == null;
    if (raw == null || raw.isEmpty()) {
      raw = List.of();
    }

    Predicate<T> predicate = effectiveFilter(state);
    String query = Objects.requireNonNullElse(state.query, "");
    boolean hasQuery = !query.isBlank() && searchKey != null;
    String q = hasQuery ? query.toLowerCase(java.util.Locale.ROOT) : "";

    List<T> filtered = new ArrayList<>(raw.size());
    boolean truncated = false;
    for (T entry : raw) {
      if (!predicate.test(entry)) {
        continue;
      }
      if (hasQuery) {
        String key = searchKey.apply(entry);
        if (key == null || !key.toLowerCase(java.util.Locale.ROOT).contains(q)) {
          continue;
        }
      }
      filtered.add(entry);
      if (maxEntries > 0 && filtered.size() >= maxEntries) {
        truncated = true;
        break;
      }
    }

    int pageSize = pageSize();
    int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
    int page = Math.max(0, Math.min(state.page, totalPages - 1));
    if (page != state.page) {
      state.page = page;
    }

    state.lastTotalItems = filtered.size();
    state.lastTotalPages = totalPages;
    state.truncated = truncated;
    state.cache = new Resolved<>(filtered, page, totalPages);
    state.cacheTick = currentTick;
  }

  private Predicate<T> effectiveFilter(State<T> state) {
    Predicate<T> combined = globalFilter != null ? globalFilter : cast(ALWAYS_TRUE);
    if (state.playerFilter != null) {
      combined = combined.and(state.playerFilter);
    }
    return combined;
  }

  private static void place(Window window, Placement placement, int slot, GuiComponent component) {
    if (placement == Placement.FIXED) {
      window.setFixed(slot, component);
    } else {
      window.setDynamic(slot, component);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Predicate<T> cast(Predicate<?> p) {
    return (Predicate<T>) p;
  }

  private static final class State<T> {
    private int page;
    private String query = "";
    private Predicate<T> playerFilter;

    private int cacheTick = Integer.MIN_VALUE;
    private Resolved<T> cache;

    private int lastTotalItems;
    private int lastTotalPages = 1;
    private boolean truncated;
    private boolean loading;

    private void invalidate() {
      cacheTick = Integer.MIN_VALUE;
      cache = null;
    }
  }

  private record Resolved<T>(List<T> filtered, int page, int totalPages) {
  }

  private final class Cell implements GuiComponent {
    private final int index;

    private Cell(int index) {
      this.index = index;
    }

    @Override
    public void mounted(Window window, int slot) {
      registerCellSlot(index, slot);
    }

    @Override
    public ItemStack render(Player player) {
      State<T> state = state(player);
      resolve(player, state);
      Resolved<T> resolved = state.cache;
      if (resolved == null) {
        return emptyCellItem.clone();
      }
      if (state.loading && loadingStateItem != null) {
        if (index != 0) {
          return emptyCellItem.clone();
        }
        ItemStack item = loadingStateItem.apply(player);
        return item == null ? emptyCellItem.clone() : item.clone();
      }
      if (resolved.filtered().isEmpty() && emptyStateItem != null) {
        if (index != 0) {
          return emptyCellItem.clone();
        }
        ItemStack item = emptyStateItem.apply(player);
        return item == null ? emptyCellItem.clone() : item.clone();
      }
      int start = resolved.page() * pageSize();
      int absoluteIndex = start + index;
      if (absoluteIndex < 0 || absoluteIndex >= resolved.filtered().size()) {
        return emptyCellItem.clone();
      }
      T entry = resolved.filtered().get(absoluteIndex);
      ItemStack item = renderer.render(player, entry);
      return item == null ? emptyCellItem.clone() : item.clone();
    }

    @Override
    public void onClick(Window.ClickContext ctx) {
      ctx.event().setCancelled(true);
      if (ctx.isKeyboardClick() || ctx.clickType() == ClickType.MIDDLE) {
        return;
      }
      State<T> state = state(ctx.player());
      resolve(ctx.player(), state);
      Resolved<T> resolved = state.cache;
      if (resolved == null) {
        return;
      }
      int start = resolved.page() * pageSize();
      int absoluteIndex = start + index;
      if (absoluteIndex < 0 || absoluteIndex >= resolved.filtered().size()) {
        return;
      }
      T entry = resolved.filtered().get(absoluteIndex);
      onClick.onClick(ctx, entry);
    }
  }

  private final class PageIndicator implements GuiComponent {
    @Override
    public void mounted(Window window, int slot) {
      registerPageIndicatorSlot(slot);
    }

    @Override
    public ItemStack render(Player player) {
      State<T> state = state(player);
      resolve(player, state);
      int page = state.page + 1;
      int totalPages = Math.max(1, state.lastTotalPages);
      int totalItems = Math.max(0, state.lastTotalItems);
      Component name = tr(player, "gui.list.page", "current", page, "total", totalPages);
      List<Component> lore = new ArrayList<>();
      String countLabel = Integer.toString(totalItems) + (state.truncated ? "+" : "");
      lore.add(tr(player, "gui.list.items", "count", countLabel));
      if (state.truncated) {
        lore.add(tr(player, "gui.list.truncated"));
      }
      if (state.query != null && !state.query.isBlank()) {
        lore.add(tr(player, "gui.list.filter", "query", state.query));
      }
      return GuiButtons.item(GuiButtons.Type.PAGE, name, lore);
    }
  }
}
