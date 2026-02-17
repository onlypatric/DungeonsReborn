package dev.patric.dungeonsreborn.gui.components;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.Window;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Button implements GuiComponent {
  /**
   * A clickable item component.
   * <p>
   * Use {@link #bind(ClickType, Component, Component, Consumer)} / {@link #bind(ClickType, Component, List, Consumer)}
   * (or the convenience helpers like {@link #left(Component, Consumer)} / {@link #left(List, Consumer)})
   * to register specific click handlers. When {@link #autoDescribeInLore(boolean)} is enabled (default), the bound clicks are
   * appended to the item's lore under a small "Controls" section.
   */
  private static final Component DEFAULT_ACTION_DESCRIPTION = GuiI18n.tr("gui.controls.action");
  private static final List<ClickType> DEFAULT_LORE_ORDER = List.of(
      ClickType.LEFT,
      ClickType.SHIFT_LEFT,
      ClickType.RIGHT,
      ClickType.SHIFT_RIGHT);

  /**
   * Formats the auto-generated "Controls" lore section.
   * <p>
   * Devs can override this globally via {@link #setDefaultControlsFormatter(ControlsFormatter)} or per-button via
   * {@link #controlsFormatter(ControlsFormatter)}.
   */
  public interface ControlsFormatter {
    Component header();

    /**
     * Whether to insert a blank lore line before the header when the item already has lore.
     */
    boolean blankLineBeforeHeader();

    /**
     * Produces one or more lore lines for a binding (multiline descriptions should return multiple lines).
     */
    List<Component> formatBinding(Component title, List<Component> descriptionLines);

    static ControlsFormatter defaultFormatter() {
      return new ControlsFormat(
          GuiI18n.tr("gui.controls.header"),
          GuiI18n.tr("gui.controls.bullet"),
          GuiI18n.tr("gui.controls.separator"),
          GuiI18n.tr("gui.controls.continuation"),
          true);
    }
  }

  /**
   * Simple configurable formatter: bullet/indent/separator can be swapped for styling and localization.
   */
  public record ControlsFormat(
      Component header,
      Component bullet,
      Component separator,
      Component continuationIndent,
      boolean blankLineBeforeHeader) implements ControlsFormatter {
    public ControlsFormat {
      Objects.requireNonNull(header, "header");
      Objects.requireNonNull(bullet, "bullet");
      Objects.requireNonNull(separator, "separator");
      Objects.requireNonNull(continuationIndent, "continuationIndent");
    }

    @Override
    public List<Component> formatBinding(Component title, List<Component> descriptionLines) {
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(descriptionLines, "descriptionLines");
      if (descriptionLines.isEmpty()) {
        return List.of(bullet.append(title));
      }

      List<Component> lines = new java.util.ArrayList<>(descriptionLines.size());
      lines.add(bullet.append(title).append(separator).append(descriptionLines.get(0)));
      for (int i = 1; i < descriptionLines.size(); i++) {
        lines.add(continuationIndent.append(descriptionLines.get(i)));
      }
      return lines;
    }
  }

  private record Binding(Component title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    private Binding {
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(descriptionLines, "descriptionLines");
      if (descriptionLines.isEmpty()) {
        throw new IllegalArgumentException("descriptionLines must not be empty");
      }
      Objects.requireNonNull(handler, "handler");
    }
  }

  private final Function<Player, ItemStack> item;
  private Consumer<Window.ClickContext> onClick;
  private final EnumMap<ClickType, Binding> bindings = new EnumMap<>(ClickType.class);
  private boolean autoDescribeInLore = true;
  private ControlsFormatter controlsFormatter;
  private Function<ClickType, Component> titleProvider;
  private boolean cachePerPlayer;
  private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, ItemStack> renderCache = new java.util.concurrent.ConcurrentHashMap<>();

  private static volatile ControlsFormatter defaultControlsFormatter = ControlsFormatter.defaultFormatter();
  private static volatile Function<ClickType, Component> defaultTitleProvider = Button::defaultTitle;

  public static void setDefaultControlsFormatter(ControlsFormatter formatter) {
    defaultControlsFormatter = Objects.requireNonNull(formatter, "formatter");
  }

  public static ControlsFormatter defaultControlsFormatter() {
    return defaultControlsFormatter;
  }

  public static void setDefaultTitleProvider(Function<ClickType, Component> provider) {
    defaultTitleProvider = Objects.requireNonNull(provider, "provider");
  }

  public static Function<ClickType, Component> defaultTitleProvider() {
    return defaultTitleProvider;
  }

  public Button(ItemStack item) {
    this(p -> item, null);
  }

  public Button(ItemStack item, Consumer<Window.ClickContext> onClick) {
    this(p -> item, onClick);
  }

  public Button(Function<Player, ItemStack> item) {
    this(item, null);
  }

  public Button(Function<Player, ItemStack> item, Consumer<Window.ClickContext> onClick) {
    this.item = Objects.requireNonNull(item, "item");
    this.onClick = onClick;
  }

  @Override
  public void onClick(Window.ClickContext ctx) {
    Binding binding = bindings.get(ctx.clickType());
    if (binding != null) {
      dev.patric.dungeonsreborn.gui.GuiManager.get().debug("Button.onClick: player=" + ctx.player().getName()
          + " button=" + getClass().getSimpleName() + " click=" + ctx.clickType() + " -> bound");
      binding.handler.accept(ctx);
      return;
    }
    if (onClick != null) {
      dev.patric.dungeonsreborn.gui.GuiManager.get().debug("Button.onClick: player=" + ctx.player().getName()
          + " button=" + getClass().getSimpleName() + " click=" + ctx.clickType() + " -> fallback");
      onClick.accept(ctx);
      return;
    }
    dev.patric.dungeonsreborn.gui.GuiManager.get().debug("Button.onClick: player=" + ctx.player().getName()
        + " button=" + getClass().getSimpleName() + " click=" + ctx.clickType() + " -> ignored");
  }

  public Button autoDescribeInLore(boolean enabled) {
    this.autoDescribeInLore = enabled;
    return this;
  }

  /**
   * Memoize the rendered item per player for immutable buttons.
   * Use with care: only for buttons whose appearance does not change at runtime.
   */
  public Button cachePerPlayer() {
    this.cachePerPlayer = true;
    return this;
  }

  public Button cachePerPlayer(boolean enabled) {
    this.cachePerPlayer = enabled;
    if (!enabled) {
      renderCache.clear();
    }
    return this;
  }

  public void clearCache() {
    renderCache.clear();
  }

  /**
   * Overrides the auto-generated "Controls" section formatting for this button.
   */
  public Button controlsFormatter(ControlsFormatter formatter) {
    this.controlsFormatter = Objects.requireNonNull(formatter, "formatter");
    return this;
  }

  /**
   * Overrides how default click titles (Left-click, Right-click, ...) are generated for this button.
   * <p>
   * This only applies to bindings that don't provide an explicit title.
   */
  public Button titleProvider(Function<ClickType, Component> provider) {
    this.titleProvider = Objects.requireNonNull(provider, "provider");
    return this;
  }

  /**
   * Convenience: sets the "Controls" header for this button while keeping the current/default formatter layout.
   */
  public Button setControlLabel(Component label) {
    Objects.requireNonNull(label, "label");
    ControlsFormatter base = effectiveControlsFormatter();
    if (base instanceof ControlsFormat f) {
      return controlsFormatter(new ControlsFormat(label, f.bullet(), f.separator(), f.continuationIndent(), f.blankLineBeforeHeader()));
    }
    return controlsFormatter(new ControlsFormat(
        label,
        GuiI18n.tr("gui.controls.bullet"),
        GuiI18n.tr("gui.controls.separator"),
        GuiI18n.tr("gui.controls.continuation"),
        true));
  }

  /**
   * Convenience: sets the "Controls" header from legacy section-color codes (e.g. "§6Controls:").
   */
  public Button setControlLabel(String label) {
    Objects.requireNonNull(label, "label");
    return setControlLabel(LegacyComponentSerializer.legacySection().deserialize(label));
  }

  public Button bind(ClickType type, Consumer<Window.ClickContext> handler) {
    return bind(type, titleFor(type), DEFAULT_ACTION_DESCRIPTION, handler);
  }

  public Button bind(ClickType type, Component description, Consumer<Window.ClickContext> handler) {
    return bind(type, titleFor(type), description, handler);
  }

  public Button bind(ClickType type, Component title, Component description, Consumer<Window.ClickContext> handler) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(handler, "handler");
    bindings.put(type, new Binding(title, List.of(description), handler));
    return this;
  }

  public Button bind(ClickType type, String title, Component description, Consumer<Window.ClickContext> handler) {
    Objects.requireNonNull(title, "title");
    return bind(type, Component.text(title), description, handler);
  }

  public Button bind(ClickType type, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(type, titleFor(type), descriptionLines, handler);
  }

  public Button bind(ClickType type, Component title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(descriptionLines, "descriptionLines");
    Objects.requireNonNull(handler, "handler");
    bindings.put(type, new Binding(title, List.copyOf(descriptionLines), handler));
    return this;
  }

  public Button bind(ClickType type, String title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    Objects.requireNonNull(title, "title");
    return bind(type, Component.text(title), descriptionLines, handler);
  }

  public Button left(Consumer<Window.ClickContext> handler) {
    return bind(ClickType.LEFT, handler);
  }

  public Button left(Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.LEFT, description, handler);
  }

  public Button left(Component title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.LEFT, title, description, handler);
  }

  public Button left(String title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.LEFT, title, description, handler);
  }

  public Button left(List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.LEFT, descriptionLines, handler);
  }

  public Button left(Component title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.LEFT, title, descriptionLines, handler);
  }

  public Button left(String title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.LEFT, title, descriptionLines, handler);
  }

  public Button shiftLeft(Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_LEFT, handler);
  }

  public Button shiftLeft(Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_LEFT, description, handler);
  }

  public Button shiftLeft(Component title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_LEFT, title, description, handler);
  }

  public Button shiftLeft(String title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_LEFT, title, description, handler);
  }

  public Button shiftLeft(List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_LEFT, descriptionLines, handler);
  }

  public Button shiftLeft(Component title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_LEFT, title, descriptionLines, handler);
  }

  public Button shiftLeft(String title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_LEFT, title, descriptionLines, handler);
  }

  public Button right(Consumer<Window.ClickContext> handler) {
    return bind(ClickType.RIGHT, handler);
  }

  public Button right(Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.RIGHT, description, handler);
  }

  public Button right(Component title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.RIGHT, title, description, handler);
  }

  public Button right(String title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.RIGHT, title, description, handler);
  }

  public Button right(List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.RIGHT, descriptionLines, handler);
  }

  public Button right(Component title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.RIGHT, title, descriptionLines, handler);
  }

  public Button right(String title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.RIGHT, title, descriptionLines, handler);
  }

  public Button shiftRight(Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_RIGHT, handler);
  }

  public Button shiftRight(Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_RIGHT, description, handler);
  }

  public Button shiftRight(Component title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_RIGHT, title, description, handler);
  }

  public Button shiftRight(String title, Component description, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_RIGHT, title, description, handler);
  }

  public Button shiftRight(List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_RIGHT, descriptionLines, handler);
  }

  public Button shiftRight(Component title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_RIGHT, title, descriptionLines, handler);
  }

  public Button shiftRight(String title, List<Component> descriptionLines, Consumer<Window.ClickContext> handler) {
    return bind(ClickType.SHIFT_RIGHT, title, descriptionLines, handler);
  }

  private List<ClickType> orderedBoundClicks() {
    List<ClickType> ordered = new ArrayList<>(bindings.size());
    EnumSet<ClickType> included = EnumSet.noneOf(ClickType.class);
    for (ClickType type : DEFAULT_LORE_ORDER) {
      if (bindings.containsKey(type)) {
        ordered.add(type);
        included.add(type);
      }
    }
    for (ClickType type : bindings.keySet()) {
      if (included.add(type)) {
        ordered.add(type);
      }
    }
    return ordered;
  }

  private static String clickLabel(ClickType type) {
    return switch (type) {
      case LEFT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.left");
      case SHIFT_LEFT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.shift_left");
      case RIGHT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.right");
      case SHIFT_RIGHT -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.click.shift_right");
      default -> type.name();
    };
  }

  private static Component defaultTitle(ClickType type) {
    return Component.text(clickLabel(type));
  }

  private Component titleFor(ClickType type) {
    Function<ClickType, Component> provider = titleProvider != null ? titleProvider : defaultTitleProvider;
    Component title = provider.apply(type);
    return title != null ? title : defaultTitle(type);
  }

  private ControlsFormatter effectiveControlsFormatter() {
    return controlsFormatter != null ? controlsFormatter : defaultControlsFormatter;
  }

  @Override
  public ItemStack render(Player player) {
    if (cachePerPlayer && player != null) {
      ItemStack cached = renderCache.get(player.getUniqueId());
      if (cached != null) {
        return cached.clone();
      }
    }
    ItemStack base = item.apply(player);
    if (base == null) {
      return null;
    }
    ItemStack stack = base.clone();
    if (!autoDescribeInLore || bindings.isEmpty()) {
      if (cachePerPlayer && player != null) {
        renderCache.put(player.getUniqueId(), stack.clone());
      }
      return stack;
    }

    ControlsFormatter formatter = effectiveControlsFormatter();

    // Append a small "Controls" section so the user knows what different clicks do.
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      meta = Bukkit.getItemFactory().getItemMeta(stack.getType());
    }
    if (meta == null) {
      dev.patric.dungeonsreborn.gui.GuiManager.get().debug(
          "Button.render: item meta is null for type=" + stack.getType() + " (skipping control lore)");
      return stack;
    }
    List<Component> lore = meta.lore();
    List<Component> next = new ArrayList<>(lore == null ? List.of() : lore);
    if (!next.isEmpty() && formatter.blankLineBeforeHeader()) {
      next.add(Component.empty());
    }
    next.add(formatter.header());
    for (ClickType type : orderedBoundClicks()) {
      Binding binding = bindings.get(type);
      if (binding == null) {
        continue;
      }
      next.addAll(formatter.formatBinding(binding.title, binding.descriptionLines));
    }
    meta.lore(next);
    stack.setItemMeta(meta);
    if (cachePerPlayer && player != null) {
      renderCache.put(player.getUniqueId(), stack.clone());
    }
    return stack;
  }
}
