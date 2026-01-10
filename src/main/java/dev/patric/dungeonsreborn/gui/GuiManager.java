package dev.patric.dungeonsreborn.gui;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class GuiManager implements Listener {
  /**
   * Central listener/router for the GUI library.
   * <p>
   * Responsibilities:
   * <ul>
   * <li>Route top-inventory clicks to {@link Window} components</li>
   * <li>Optionally block interaction with the player inventory while a window is open</li>
   * <li>Provide chat-based text input sessions for components like {@code TextButton}</li>
   * </ul>
   */
  public record TextRequest(Component prompt, String cancelWord, Duration timeout,
      BiConsumer<Player, String> onText, Consumer<Player> onCancel, Consumer<Player> onTimeout) {
    public TextRequest {
      Objects.requireNonNull(prompt, "prompt");
      Objects.requireNonNull(cancelWord, "cancelWord");
      Objects.requireNonNull(timeout, "timeout");
      Objects.requireNonNull(onText, "onText");
      Objects.requireNonNull(onCancel, "onCancel");
      Objects.requireNonNull(onTimeout, "onTimeout");
    }

    public TextRequest(Component prompt, String cancelWord, Duration timeout, BiConsumer<Player, String> onText) {
      this(prompt, cancelWord, timeout, onText, player -> {
      }, player -> {
      });
    }
  }

  public record AnvilRequest(
      Component title,
      Component prompt,
      String initialText,
      Duration timeout,
      BiConsumer<Player, String> onText,
      Consumer<Player> onCancel,
      Consumer<Player> onTimeout) {
    public AnvilRequest {
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(prompt, "prompt");
      Objects.requireNonNull(initialText, "initialText");
      Objects.requireNonNull(timeout, "timeout");
      Objects.requireNonNull(onText, "onText");
      Objects.requireNonNull(onCancel, "onCancel");
      Objects.requireNonNull(onTimeout, "onTimeout");
    }

    public AnvilRequest(Component title, Component prompt, String initialText, Duration timeout, BiConsumer<Player, String> onText) {
      this(title, prompt, initialText, timeout, onText, player -> {
      }, player -> {
      });
    }
  }

  public record SignRequest(
      Component prompt,
      List<Component> initialLines,
      Side side,
      Duration timeout,
      BiConsumer<Player, List<String>> onLines,
      Consumer<Player> onCancel,
      Consumer<Player> onTimeout) {
    public SignRequest {
      Objects.requireNonNull(prompt, "prompt");
      Objects.requireNonNull(initialLines, "initialLines");
      Objects.requireNonNull(side, "side");
      Objects.requireNonNull(timeout, "timeout");
      Objects.requireNonNull(onLines, "onLines");
      Objects.requireNonNull(onCancel, "onCancel");
      Objects.requireNonNull(onTimeout, "onTimeout");
    }

    public SignRequest(Component prompt, List<Component> initialLines, Side side, Duration timeout, BiConsumer<Player, List<String>> onLines) {
      this(prompt, initialLines, side, timeout, onLines, player -> {
      }, player -> {
      });
    }
  }

  public record ItemPickRequest(
      Component prompt,
      Duration timeout,
      InventoryHolder requiredTopHolder,
      boolean allowAirSelection,
      Predicate<ItemStack> filter,
      Component invalidMessage,
      BiConsumer<Player, ItemStack> onPick,
      Consumer<Player> onCancel,
      Consumer<Player> onTimeout) {
    public ItemPickRequest {
      Objects.requireNonNull(prompt, "prompt");
      Objects.requireNonNull(timeout, "timeout");
      Objects.requireNonNull(filter, "filter");
      Objects.requireNonNull(invalidMessage, "invalidMessage");
      Objects.requireNonNull(onPick, "onPick");
      Objects.requireNonNull(onCancel, "onCancel");
      Objects.requireNonNull(onTimeout, "onTimeout");
    }

    public ItemPickRequest(Component prompt, Duration timeout, InventoryHolder requiredTopHolder, BiConsumer<Player, ItemStack> onPick) {
      this(prompt, timeout, requiredTopHolder, false, item -> item != null && !item.getType().isAir(),
          Component.text("Please click a valid item."), onPick, player -> {
          }, player -> {
          });
    }
  }

  private static GuiManager instance;

  public static GuiManager init(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    if (instance != null) {
      return instance;
    }
    instance = new GuiManager(plugin);
    Bukkit.getPluginManager().registerEvents(instance, plugin);
    instance.startWindowTicker();
    instance.debug("Initialized");
    return instance;
  }

  public static GuiManager get() {
    if (instance == null) {
      throw new IllegalStateException("GuiManager not initialized");
    }
    return instance;
  }

  private final JavaPlugin plugin;
  private BukkitTask windowTickTask;
  private final Map<UUID, TextRequest> pendingText = new ConcurrentHashMap<>();
  private final Map<UUID, AnvilSession> pendingAnvil = new ConcurrentHashMap<>();
  private final Map<UUID, SignSession> pendingSign = new ConcurrentHashMap<>();
  private final Map<UUID, ItemPickSession> pendingItemPick = new ConcurrentHashMap<>();
  private final Map<UUID, ArrayDeque<Window>> windowStacks = new ConcurrentHashMap<>();
  private final Set<UUID> suppressNextCloseCallbacks = ConcurrentHashMap.newKeySet();
  private final Set<UUID> suppressNextCloseStackPop = ConcurrentHashMap.newKeySet();
  private final Map<UUID, ActiveWindowState> activeWindows = new ConcurrentHashMap<>();
  private final Map<UUID, InventoryHolder> expectedNextOpenHolder = new ConcurrentHashMap<>();
  private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
  private volatile boolean debugEnabled = true;
  private volatile boolean cancelTopInventoryDragsByDefault = true;

  private GuiManager(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  private void startWindowTicker() {
    if (windowTickTask != null) {
      return;
    }
    windowTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      int tick = Bukkit.getCurrentTick();
      for (var entry : windowStacks.entrySet()) {
        UUID id = entry.getKey();
        ArrayDeque<Window> stack = entry.getValue();
        if (stack == null || stack.isEmpty()) {
          continue;
        }
        Window window = stack.peek();
        if (window == null) {
          continue;
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
          continue;
        }
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder != window) {
          continue;
        }
        window.handleTick(player, tick);
      }
    }, 1L, 1L);
  }

  public void setDebug(boolean enabled) {
    debugEnabled = enabled;
    debug("Debug " + (enabled ? "enabled" : "disabled"));
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  /**
   * Controls whether {@link InventoryDragEvent}s that touch the top inventory are cancelled by default.
   * <p>
   * Individual {@link Window}s may override this behavior.
   */
  public void setCancelTopInventoryDragsByDefault(boolean cancel) {
    cancelTopInventoryDragsByDefault = cancel;
    debug("cancelTopInventoryDragsByDefault=" + cancel);
  }

  public boolean isCancelTopInventoryDragsByDefault() {
    return cancelTopInventoryDragsByDefault;
  }

  public void debug(String message) {
    if (!debugEnabled) {
      return;
    }
    plugin.getLogger().info("[GUI] " + message);
  }

  public void debug(String message, Throwable throwable) {
    if (!debugEnabled) {
      return;
    }
    plugin.getLogger().log(Level.WARNING, "[GUI] " + message, throwable);
  }

  /**
   * Returns a snapshot describing what window this player is "supposed" to be in (stack top),
   * what top inventory they actually have open right now, and whether a resume is pending.
   * <p>
   * This is useful for debugging and for handling cross-plugin inventory opens without fighting them.
   */
  public ActiveWindowSnapshot activeWindow(Player player) {
    Objects.requireNonNull(player, "player");
    ActiveWindowState state = activeWindows.get(player.getUniqueId());
    if (state == null) {
      return new ActiveWindowSnapshot(null, null, null, null, null);
    }
    return state.snapshot();
  }

  /**
   * Attempts to resume {@code window} on this player. If another plugin currently has a non-player inventory open,
   * the resume is deferred until that inventory closes.
   */
  public void resume(Player player, Window window, String reason) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(reason, "reason");
    ActiveWindowState state = activeWindows.computeIfAbsent(player.getUniqueId(), id -> new ActiveWindowState());
    state.pendingResumeWindow = window;
    state.pendingResumeReason = reason;
    debug("resume: request player=" + player.getName() + " window=" + window.getClass().getSimpleName() + " reason=" + reason);

    if (canOpenOverCurrentTop(player)) {
      debug("resume: immediate player=" + player.getName() + " window=" + window.getClass().getSimpleName());
      state.pendingResumeWindow = null;
      state.pendingResumeReason = null;
      show(player, window, Window.OpenReason.RESUME, reason);
      return;
    }

    debug("resume: deferred player=" + player.getName() + " window=" + window.getClass().getSimpleName()
        + " currentTopHolder=" + describeHolder(player.getOpenInventory().getTopInventory().getHolder()));
  }

  public void runNextTick(Runnable task) {
    Bukkit.getScheduler().runTask(plugin, task);
  }

  /**
   * Opens a window as the new root for this player (clears any previous stack).
   */
  public void open(Player player, Window window) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(window, "window");
    ArrayDeque<Window> stack = new ArrayDeque<>();
    stack.push(window);
    windowStacks.put(player.getUniqueId(), stack);
    debug("open: player=" + player.getName() + " window=" + window.getClass().getSimpleName() + " stackDepth=1");
    setExpectedWindow(player, window, "open");
    expectNextInventoryOpen(player, window);
    window.openInternal(player, Window.OpenReason.ROOT, "open");
  }

  /**
   * Pushes a sub-window on the player's stack and opens it.
   * The current window's {@code onClose} is suppressed because switching inventories fires a close event.
   */
  public void push(Player player, Window window) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(window, "window");
    UUID id = player.getUniqueId();
    ArrayDeque<Window> stack = windowStacks.computeIfAbsent(id, k -> new ArrayDeque<>());
    if (!stack.isEmpty()) {
      suppressNextCloseCallbacks.add(id);
    }
    stack.push(window);
    debug("push: player=" + player.getName() + " window=" + window.getClass().getSimpleName() + " stackDepth=" + stack.size());
    setExpectedWindow(player, window, "push");
    expectNextInventoryOpen(player, window);
    window.openInternal(player, Window.OpenReason.PUSH, "push");
  }

  /**
   * Ensures {@code window} is shown as the current window.
   * If it's already on top, it just redraws; otherwise it is pushed.
   */
  public void show(Player player, Window window) {
    show(player, window, Window.OpenReason.SHOW, "show");
  }

  private void show(Player player, Window window, Window.OpenReason reason, String detail) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(window, "window");
    UUID id = player.getUniqueId();
    ArrayDeque<Window> stack = windowStacks.get(id);
    if (stack != null && stack.peek() == window) {
      InventoryHolder currentHolder = player.getOpenInventory().getTopInventory().getHolder();
      if (currentHolder == window) {
        debug("show: player=" + player.getName() + " window=" + window.getClass().getSimpleName() + " -> redraw");
        window.redraw(player);
      } else {
        debug("show: player=" + player.getName() + " window=" + window.getClass().getSimpleName()
            + " currentHolder=" + (currentHolder == null ? "null" : currentHolder.getClass().getSimpleName()) + " -> open");
        expectNextInventoryOpen(player, window);
        window.openInternal(player, reason, detail);
      }
      return;
    }
    debug("show: player=" + player.getName() + " window=" + window.getClass().getSimpleName()
        + " not on top (stackDepth=" + (stack == null ? 0 : stack.size()) + ") -> push");
    push(player, window);
  }

  public int stackDepth(Player player) {
    Objects.requireNonNull(player, "player");
    ArrayDeque<Window> stack = windowStacks.get(player.getUniqueId());
    return stack == null ? 0 : stack.size();
  }

  public boolean hasPreviousWindow(Player player) {
    return stackDepth(player) > 1;
  }

  /**
   * Marks the next inventory close as "temporary" for this player:
   * it won't fire the window's close callback and it won't pop the window stack.
   * <p>
   * Intended for flows like chat input where a window is closed briefly and reopened afterwards.
   */
  public void prepareTemporaryClose(Player player) {
    UUID id = Objects.requireNonNull(player, "player").getUniqueId();
    suppressNextCloseCallbacks.add(id);
    suppressNextCloseStackPop.add(id);
    debug("prepareTemporaryClose: player=" + player.getName());
    ActiveWindowState state = activeWindows.computeIfAbsent(id, k -> new ActiveWindowState());
    state.lastEvent = "prepareTemporaryClose";
  }

  public void setCursorNextTick(Player player, ItemStack item) {
    runNextTick(() -> player.setItemOnCursor(item));
  }

  public void requestText(Player player, TextRequest request) {
    debug("requestText: player=" + player.getName() + " cancelWord=" + request.cancelWord()
        + " timeout=" + request.timeout().toSeconds() + "s");
    player.sendMessage(request.prompt());
    if (!request.cancelWord().isBlank()) {
      player.sendMessage(Component.text("§7Type §f" + request.cancelWord() + " §7to cancel."));
    }
    pendingText.put(player.getUniqueId(), request);

    long ticks = Math.max(1L, (request.timeout().toMillis() + 49L) / 50L);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!pendingText.remove(player.getUniqueId(), request)) {
        return;
      }
      debug("requestText: timeout fired for player=" + player.getName());
      Bukkit.getScheduler().runTask(plugin, () -> request.onTimeout().accept(player));
    }, ticks);
  }

  public void requestTextAnvil(Player player, AnvilRequest request) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(request, "request");
    debug("requestTextAnvil: player=" + player.getName() + " timeout=" + request.timeout().toSeconds() + "s");
    player.sendMessage(request.prompt());

    UUID id = player.getUniqueId();
    AnvilSession existing = pendingAnvil.remove(id);
    if (existing != null) {
      existing.cancelTimeout();
    }

    AnvilSession session = new AnvilSession(plugin, id, request);
    pendingAnvil.put(id, session);

    Inventory inv = Bukkit.createInventory(session, InventoryType.ANVIL, request.title());
    if (inv instanceof AnvilInventory anvil) {
      ensureAnvilItems(session, anvil);
    }

    expectNextInventoryOpen(player, session);
    player.openInventory(inv);
    // Configure cost/limits via the view API (AnvilInventory setters are deprecated as of 1.21).
    runNextTick(() -> {
      if (!(player.getOpenInventory() instanceof AnvilView view)) {
        return;
      }
      if (view.getTopInventory().getHolder() != session) {
        return;
      }
      ensureAnvilItems(session, view.getTopInventory());
      view.setMaximumRepairCost(999_999);
      view.setRepairCost(0);
      view.setRepairItemCountCost(0);
    });
    // Some clients clear the input slot after open; re-assert a few times.
    for (int i = 2; i <= 6; i++) {
      int delay = i;
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (!(player.getOpenInventory() instanceof AnvilView view)) {
          return;
        }
        if (view.getTopInventory().getHolder() != session) {
          return;
        }
        ensureAnvilItems(session, view.getTopInventory());
      }, delay);
    }
    session.startTimeout();
  }

  public void requestTextSign(Player player, SignRequest request) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(request, "request");
    debug("requestTextSign: player=" + player.getName() + " timeout=" + request.timeout().toSeconds() + "s side=" + request.side());
    player.sendMessage(request.prompt());

    UUID id = player.getUniqueId();
    SignSession existing = pendingSign.remove(id);
    if (existing != null) {
      existing.cleanup(true);
    }

    SignSession session = SignSession.open(plugin, player, request);
    pendingSign.put(id, session);
  }

  /**
   * Requests the player to pick an item from their own inventory (bottom inventory of the currently open view).
   * <p>
   * This is meant for "form-like" flows where the GUI needs an item input without moving it around.
   * The click is cancelled so the player's inventory is not modified.
   */
  public void requestItemPick(Player player, ItemPickRequest request) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(request, "request");
    debug("requestItemPick: player=" + player.getName() + " timeout=" + request.timeout().toSeconds()
        + "s requiredTop=" + describeHolder(request.requiredTopHolder()));
    player.sendMessage(request.prompt());

    UUID id = player.getUniqueId();
    ItemPickSession existing = pendingItemPick.remove(id);
    if (existing != null) {
      existing.cancelTimeout();
      Bukkit.getScheduler().runTask(plugin, () -> existing.request.onCancel().accept(player));
    }

    ItemPickSession session = new ItemPickSession(plugin, id, request);
    pendingItemPick.put(id, session);
    session.startTimeout(() -> {
      if (!pendingItemPick.remove(id, session)) {
        return;
      }
      debug("itemPick: timeout player=" + player.getName());
      request.onTimeout().accept(player);
    });
  }

  public void cancelItemPick(Player player) {
    Objects.requireNonNull(player, "player");
    UUID id = player.getUniqueId();
    ItemPickSession session = pendingItemPick.remove(id);
    if (session == null) {
      return;
    }
    session.cancelTimeout();
    debug("itemPick: cancel player=" + player.getName());
    Bukkit.getScheduler().runTask(plugin, () -> session.request.onCancel().accept(player));
  }

  @EventHandler
  public void onInventoryOpen(InventoryOpenEvent event) {
    HumanEntity who = event.getPlayer();
    if (!(who instanceof Player player)) {
      return;
    }
    InventoryHolder holder = event.getInventory().getHolder();
    if (holder instanceof AnvilSession session) {
      ensureAnvilItems(session, event.getInventory());
    }
    UUID id = player.getUniqueId();
    ActiveWindowState state = activeWindows.computeIfAbsent(id, k -> new ActiveWindowState());
    state.actualTopHolder = holder;
    state.actualTopHolderDescription = describeHolder(holder);
    state.lastEvent = "inventoryOpen";

    ItemPickSession pick = pendingItemPick.get(id);
    if (pick != null && pick.request.requiredTopHolder() != null && holder != pick.request.requiredTopHolder()) {
      // Context switched while picking (other plugin or another inventory): cancel the pick to avoid surprises.
      pendingItemPick.remove(id, pick);
      pick.cancelTimeout();
      debug("itemPick: cancel player=" + player.getName() + " (contextSwitch -> " + describeHolder(holder) + ")");
      Bukkit.getScheduler().runTask(plugin, () -> pick.request.onCancel().accept(player));
    }

    InventoryHolder expected = expectedNextOpenHolder.remove(id);
    if (expected != null && expected == holder) {
      debug("openEvent: player=" + player.getName() + " holder=" + describeHolder(holder) + " ours=true");
      return;
    }

    if (holder instanceof Window) {
      debug("openEvent: player=" + player.getName() + " holder=" + describeHolder(holder) + " ours=false (window)");
      return;
    }

    debug("openEvent: player=" + player.getName() + " holder=" + describeHolder(holder) + " ours=false (external)");
    state.lastExternalHolderDescription = state.actualTopHolderDescription;
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    HumanEntity who = event.getWhoClicked();
    if (!(who instanceof Player player)) {
      return;
    }

    ItemPickSession pick = pendingItemPick.get(player.getUniqueId());
    if (pick != null && handleItemPickClick(player, event, pick)) {
      return;
    }

    Inventory top = event.getView().getTopInventory();
    InventoryHolder holder = top.getHolder();
    if (holder instanceof AnvilSession session) {
      handleAnvilClick(player, event, session);
      return;
    }
    if (!(holder instanceof Window window)) {
      return;
    }
    debug("click: player=" + player.getName() + " window=" + window.getClass().getSimpleName()
        + " click=" + event.getClick() + " rawSlot=" + event.getRawSlot() + " shift=" + event.isShiftClick()
        + " keyboard=" + event.getClick().isKeyboardClick());

    int topSize = top.getSize();
    int rawSlot = event.getRawSlot();

    if (rawSlot < 0) {
      event.setCancelled(true);
      window.handleClickOutside(player, event);
      return;
    }

    boolean clickedTop = rawSlot < topSize;
    if (!clickedTop) {
      if (!window.allowPlayerInventoryClicks() || event.isShiftClick() || event.getClick().isKeyboardClick()) {
        event.setCancelled(true);
      }
      return;
    }

    GuiComponent component = window.componentAt(rawSlot);
    if (component != null && component.allowVanillaClicks()) {
      Window.ClickContext ctx = new Window.ClickContext(window, player, event, rawSlot);
      if (!component.beforeVanillaClick(ctx)) {
        debug("click: player=" + player.getName() + " window=" + window.getClass().getSimpleName()
            + " rawSlot=" + rawSlot + " -> vanillaBlocked");
        event.setCancelled(true);
        return;
      }
      event.setCancelled(false);
      debug("click: player=" + player.getName() + " window=" + window.getClass().getSimpleName()
          + " rawSlot=" + rawSlot + " -> vanilla");
      runNextTick(() -> component.afterVanillaClick(ctx));
      return;
    }

    event.setCancelled(true);
    window.handleTopClick(player, event, rawSlot);
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    Inventory top = event.getView().getTopInventory();
    InventoryHolder holder = top.getHolder();
    if (!(holder instanceof Window window)) {
      return;
    }

    int topSize = top.getSize();
    boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize);
    boolean touchesBottom = event.getRawSlots().stream().anyMatch(slot -> slot >= topSize);

    boolean cancelTopDrag = window.cancelTopInventoryDragsEffective(cancelTopInventoryDragsByDefault);
    if (touchesTop && cancelTopDrag) {
      boolean onlyVanillaSlots = event.getRawSlots().stream()
          .filter(slot -> slot >= 0 && slot < topSize)
          .allMatch(slot -> {
            GuiComponent component = window.componentAt(slot);
            return component != null && component.allowVanillaDrags();
          });
      if (!onlyVanillaSlots) {
        debug("drag: player=" + event.getWhoClicked().getName() + " cancelled=true (touchesTop)");
        event.setCancelled(true);
        return;
      }

      // Give components a chance to block a vanilla drag (e.g. acceptance rules).
      if (event.getWhoClicked() instanceof Player player) {
        Set<Integer> rawSlots = Set.copyOf(event.getRawSlots());
        for (Integer rawSlot : rawSlots) {
          if (rawSlot == null || rawSlot < 0 || rawSlot >= topSize) {
            continue;
          }
          GuiComponent component = window.componentAt(rawSlot);
          if (component == null || !component.allowVanillaDrags()) {
            continue;
          }
          if (!component.beforeVanillaDrag(window, player, event, rawSlots)) {
            debug("drag: player=" + event.getWhoClicked().getName() + " cancelled=true (blockedByComponent)");
            event.setCancelled(true);
            return;
          }
        }
      }
    }

    if (!window.allowPlayerInventoryClicks() && touchesBottom) {
      debug("drag: player=" + event.getWhoClicked().getName() + " cancelled=true (playerInvLocked)");
      event.setCancelled(true);
      return;
    }

    if (touchesTop) {
      debug("drag: player=" + event.getWhoClicked().getName() + " cancelled=false (touchesTopAllowed)");
      if (event.getWhoClicked() instanceof Player player) {
        Set<Integer> rawSlots = Set.copyOf(event.getRawSlots());
        runNextTick(() -> {
          for (Integer rawSlot : rawSlots) {
            if (rawSlot == null || rawSlot < 0 || rawSlot >= topSize) {
              continue;
            }
            GuiComponent component = window.componentAt(rawSlot);
            if (component == null || !component.allowVanillaDrags()) {
              continue;
            }
            component.afterVanillaDrag(window, player, event, rawSlots);
          }
        });
      }
    }
  }

  @EventHandler
  public void onPrepareAnvil(PrepareAnvilEvent event) {
    InventoryHolder holder = event.getInventory().getHolder();
    if (!(holder instanceof AnvilSession session)) {
      return;
    }
    AnvilView view = event.getView();
    view.setMaximumRepairCost(999_999);
    view.setRepairCost(0);
    view.setRepairItemCountCost(0);
    ensureAnvilItems(session, event.getInventory());
  }

  @EventHandler
  public void onSignChange(SignChangeEvent event) {
    Player player = event.getPlayer();
    SignSession session = pendingSign.get(player.getUniqueId());
    if (session == null) {
      return;
    }
    if (!session.isTarget(event.getBlock(), event.getSide())) {
      return;
    }

    event.setCancelled(true);
    pendingSign.remove(player.getUniqueId(), session);
    session.cancelTimeout();
    session.restore();

    List<String> lines = event.lines().stream().map(plain::serialize).toList();
    debug("sign: player=" + player.getName() + " lines=" + lines);
    Bukkit.getScheduler().runTask(plugin, () -> {
      try {
        session.request.onLines().accept(player, lines);
      } finally {
        session.cleanup(false);
      }
    });
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    HumanEntity who = event.getPlayer();
    if (!(who instanceof Player player)) {
      return;
    }
    UUID id = player.getUniqueId();

    ActiveWindowState state = activeWindows.computeIfAbsent(id, k -> new ActiveWindowState());
    state.actualTopHolder = player.getOpenInventory().getTopInventory().getHolder();
    state.actualTopHolderDescription = describeHolder(state.actualTopHolder);
    state.lastEvent = "inventoryClose";

    InventoryHolder holder = event.getInventory().getHolder();
    if (holder instanceof AnvilSession session) {
      handleAnvilClose(player, session);
      return;
    }
    if (holder instanceof Window window) {
      ItemPickSession pick = pendingItemPick.get(id);
      if (pick != null && pick.request.requiredTopHolder() == window) {
        pendingItemPick.remove(id, pick);
        pick.cancelTimeout();
        debug("itemPick: cancel player=" + player.getName() + " (windowClose)");
        Bukkit.getScheduler().runTask(plugin, () -> pick.request.onCancel().accept(player));
      }

      boolean suppressCallbacks = suppressNextCloseCallbacks.remove(id);
      boolean suppressStackPop = suppressNextCloseStackPop.remove(id);
      ArrayDeque<Window> stack = windowStacks.get(id);
      Window.CloseReason reason = closeReasonFor(player, window, suppressStackPop, state.actualTopHolder);
      String detail = "suppressStackPop=" + suppressStackPop + " actualTopHolder=" + describeHolder(state.actualTopHolder);
      debug("close: player=" + player.getName() + " window=" + window.getClass().getSimpleName()
          + " suppressCallbacks=" + suppressCallbacks + " suppressStackPop=" + suppressStackPop
          + " stackDepth=" + (stack == null ? 0 : stack.size()));
      window.handleClose(player, reason, detail, suppressCallbacks);
      state.lastEvent = suppressStackPop ? "closeWindowTemporary" : "closeWindow";

      if (!suppressStackPop) {
        // Delay stack decisions by 1 tick so we can detect cross-plugin inventory opens.
        runNextTick(() -> handleStackAfterClose(player, window));
      }
      return;
    }

    // Non-window inventory closed (e.g. another plugin). If we deferred a resume, try it now.
    if (state.pendingResumeWindow != null) {
      runNextTick(() -> tryResumeIfPossible(player));
    }
  }

  @EventHandler
  public void onAsyncChat(AsyncChatEvent event) {
    Player player = event.getPlayer();
    TextRequest request = pendingText.remove(player.getUniqueId());
    if (request == null) {
      return;
    }
    event.setCancelled(true);

    String text = plain.serialize(event.message()).trim();
    debug("chat: player=" + player.getName() + " text=\"" + text + "\"");
    if (text.equalsIgnoreCase(request.cancelWord())) {
      debug("chat: player=" + player.getName() + " cancelled");
      Bukkit.getScheduler().runTask(plugin, () -> request.onCancel().accept(player));
      return;
    }

    Bukkit.getScheduler().runTask(plugin, () -> request.onText().accept(player, text));
  }

  @EventHandler
  public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
    UUID id = event.getPlayer().getUniqueId();
    pendingText.remove(id);
    AnvilSession anvil = pendingAnvil.remove(id);
    if (anvil != null) {
      anvil.cancelTimeout();
    }
    SignSession sign = pendingSign.remove(id);
    if (sign != null) {
      sign.cleanup(true);
    }
    ItemPickSession pick = pendingItemPick.remove(id);
    if (pick != null) {
      pick.cancelTimeout();
      pick.request.onCancel().accept(event.getPlayer());
    }
    windowStacks.remove(id);
    suppressNextCloseCallbacks.remove(id);
    suppressNextCloseStackPop.remove(id);
    activeWindows.remove(id);
    expectedNextOpenHolder.remove(id);
    debug("quit: player=" + event.getPlayer().getName());
  }

  private void handleStackAfterClose(Player player, Window closed) {
    UUID id = player.getUniqueId();
    ArrayDeque<Window> stack = windowStacks.get(id);
    if (stack == null || stack.isEmpty()) {
      debug("stack: player=" + player.getName() + " close=" + closed.getClass().getSimpleName() + " -> no stack");
      return;
    }

    // If another plugin opened a non-player inventory, don't fight it by reopening our stack immediately.
    InventoryHolder current = player.getOpenInventory().getTopInventory().getHolder();
    if (!canOpenOverHolder(player, current)) {
      ActiveWindowState state = activeWindows.computeIfAbsent(id, k -> new ActiveWindowState());
      state.lastExternalHolderDescription = describeHolder(current);
      state.lastEvent = "stackSuspendedExternal";
      state.pendingResumeWindow = closed;
      state.pendingResumeReason = "externalInventoryOpen";
      debug("stack: player=" + player.getName() + " close=" + closed.getClass().getSimpleName()
          + " -> suspend (externalTopHolder=" + describeHolder(current) + ")");
      return;
    }

    // Only react when the player closes the current (top) window.
    if (stack.peek() != closed) {
      debug("stack: player=" + player.getName() + " close=" + closed.getClass().getSimpleName()
          + " but top=" + stack.peek().getClass().getSimpleName() + " -> ignore");
      return;
    }

    stack.pop();
    Window next = stack.peek();
    if (next == null) {
      windowStacks.remove(id);
      setExpectedWindow(player, null, "stackEmpty");
      debug("stack: player=" + player.getName() + " close=" + closed.getClass().getSimpleName() + " -> empty stack");
      return;
    }
    debug("stack: player=" + player.getName() + " returningTo=" + next.getClass().getSimpleName()
        + " stackDepth=" + stack.size());
    setExpectedWindow(player, next, "stackReturn");
    runNextTick(() -> {
      expectNextInventoryOpen(player, next);
      next.openInternal(player, Window.OpenReason.STACK_RETURN, "stackReturn");
    });
  }

  private void setExpectedWindow(Player player, Window expected, String reason) {
    UUID id = player.getUniqueId();
    ActiveWindowState state = activeWindows.computeIfAbsent(id, k -> new ActiveWindowState());
    state.expectedWindowDescription = expected == null ? null : expected.getClass().getSimpleName();
    state.lastEvent = "expected=" + reason;
  }

  private void expectNextInventoryOpen(Player player, InventoryHolder holder) {
    expectedNextOpenHolder.put(player.getUniqueId(), holder);
  }

  private void tryResumeIfPossible(Player player) {
    ActiveWindowState state = activeWindows.get(player.getUniqueId());
    if (state == null || state.pendingResumeWindow == null) {
      return;
    }
    if (!canOpenOverCurrentTop(player)) {
      return;
    }
    Window window = state.pendingResumeWindow;
    String reason = state.pendingResumeReason;
    state.pendingResumeWindow = null;
    state.pendingResumeReason = null;
    debug("resume: now player=" + player.getName() + " window=" + window.getClass().getSimpleName()
        + " reason=" + (reason == null ? "unknown" : reason));
    show(player, window, Window.OpenReason.RESUME, reason);
  }

  private static Window.CloseReason closeReasonFor(Player player, Window window, boolean suppressStackPop, InventoryHolder currentTopHolderAfterClose) {
    if (suppressStackPop) {
      return Window.CloseReason.TEMPORARY;
    }
    if (currentTopHolderAfterClose == window) {
      return Window.CloseReason.SWITCHED;
    }
    if (currentTopHolderAfterClose instanceof Player) {
      return Window.CloseReason.PLAYER;
    }
    if (canOpenOverHolder(player, currentTopHolderAfterClose)) {
      return Window.CloseReason.SWITCHED;
    }
    return Window.CloseReason.EXTERNAL;
  }

  private boolean canOpenOverCurrentTop(Player player) {
    InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
    return canOpenOverHolder(player, holder);
  }

  private static boolean canOpenOverHolder(Player player, InventoryHolder holder) {
    if (holder == null) {
      return true;
    }
    if (holder instanceof Window) {
      return true;
    }
    return holder instanceof Player && holder == player;
  }

  private static String describeHolder(InventoryHolder holder) {
    if (holder == null) {
      return "null";
    }
    if (holder instanceof Window window) {
      return "Window(" + window.getClass().getSimpleName() + ")";
    }
    return holder.getClass().getSimpleName();
  }

  private boolean handleItemPickClick(Player player, InventoryClickEvent event, ItemPickSession session) {
    InventoryHolder required = session.request.requiredTopHolder();
    if (required != null) {
      InventoryHolder currentTopHolder = event.getView().getTopInventory().getHolder();
      if (currentTopHolder != required) {
        // Don't react to clicks if the player is no longer in the expected GUI.
        pendingItemPick.remove(player.getUniqueId(), session);
        session.cancelTimeout();
        debug("itemPick: cancel player=" + player.getName() + " (topHolderChanged -> " + describeHolder(currentTopHolder) + ")");
        Bukkit.getScheduler().runTask(plugin, () -> session.request.onCancel().accept(player));
        return false;
      }
    }

    int rawSlot = event.getRawSlot();
    if (rawSlot < 0) {
      event.setCancelled(true);
      return true;
    }

    int topSize = event.getView().getTopInventory().getSize();
    if (rawSlot < topSize) {
      // Let the window handle top-inventory clicks (e.g., cancel buttons).
      return false;
    }

    event.setCancelled(true);

    ItemStack current = event.getCurrentItem();
    if (current == null || current.getType().isAir()) {
      if (!session.request.allowAirSelection()) {
        player.sendMessage(session.request.invalidMessage());
        return true;
      }
      resolveItemPick(player, session, null);
      return true;
    }

    if (!session.request.filter().test(current)) {
      player.sendMessage(session.request.invalidMessage());
      return true;
    }

    resolveItemPick(player, session, current.clone());
    return true;
  }

  private void resolveItemPick(Player player, ItemPickSession session, ItemStack selected) {
    if (!pendingItemPick.remove(player.getUniqueId(), session)) {
      return;
    }
    session.cancelTimeout();
    String picked = selected == null ? "null" : selected.getType().name() + "x" + selected.getAmount();
    debug("itemPick: picked player=" + player.getName() + " item=" + picked);
    Bukkit.getScheduler().runTask(plugin, () -> session.request.onPick().accept(player, selected));
  }

  private static final class ActiveWindowState {
    private String expectedWindowDescription;
    private InventoryHolder actualTopHolder;
    private String actualTopHolderDescription;
    private String lastExternalHolderDescription;
    private Window pendingResumeWindow;
    private String pendingResumeReason;
    private String lastEvent;

    private ActiveWindowSnapshot snapshot() {
      return new ActiveWindowSnapshot(
          expectedWindowDescription,
          actualTopHolderDescription,
          pendingResumeWindow == null ? null : pendingResumeWindow.getClass().getSimpleName(),
          lastExternalHolderDescription,
          lastEvent);
    }
  }

  public record ActiveWindowSnapshot(
      String expectedWindow,
      String actualTopHolder,
      String pendingResumeWindow,
      String lastExternalTopHolder,
      String lastEvent) {
  }

  private void handleAnvilClick(Player player, InventoryClickEvent event, AnvilSession session) {
    event.setCancelled(true);
    if (event.getRawSlot() < 0) {
      return;
    }
    if (event.getRawSlot() > 2) {
      return;
    }
    if (session.resolved) {
      return;
    }

    // Slot 1 = cancel button.
    if (event.getRawSlot() == 1) {
      debug("anvil: cancelClick player=" + player.getName());
      session.resolved = true;
      pendingAnvil.remove(player.getUniqueId(), session);
      session.cancelTimeout();
      Bukkit.getScheduler().runTask(plugin, () -> session.request.onCancel().accept(player));
      player.closeInventory();
      return;
    }

    // Slot 2 = confirm/result.
    if (event.getRawSlot() == 2) {
      String text = event.getView() instanceof AnvilView view ? view.getRenameText() : null;
      if (text == null || text.isBlank()) {
        text = resolveAnvilResultText(event);
      }
      if (text == null) {
        text = "";
      }
      text = text.trim();
      debug("anvil: confirmClick player=" + player.getName() + " text=\"" + text + "\"");
      session.resolved = true;
      pendingAnvil.remove(player.getUniqueId(), session);
      session.cancelTimeout();
      String finalText = text;
      Bukkit.getScheduler().runTask(plugin, () -> session.request.onText().accept(player, finalText));
      player.closeInventory();
    }
  }

  private void handleAnvilClose(Player player, AnvilSession session) {
    if (session.resolved) {
      return;
    }
    if (!pendingAnvil.remove(player.getUniqueId(), session)) {
      return;
    }
    session.cancelTimeout();
    debug("anvil: close player=" + player.getName() + " -> cancel");
    Bukkit.getScheduler().runTask(plugin, () -> session.request.onCancel().accept(player));
  }

  private String resolveAnvilResultText(InventoryClickEvent event) {
    ItemStack item = event.getCurrentItem();
    if (item == null) {
      return null;
    }
    if (!item.hasItemMeta()) {
      return null;
    }
    Component name = item.getItemMeta().displayName();
    if (name != null) {
      String text = plain.serialize(name);
      if (text != null && !text.isBlank()) {
        return text;
      }
    }
    if (item.getItemMeta().hasDisplayName()) {
      String legacy = item.getItemMeta().displayName().toString();
      return legacy == null || legacy.isBlank() ? null : legacy;
    }
    return null;
  }

  private void ensureAnvilItems(AnvilSession session, Inventory inventory) {
    ItemStack first = inventory.getItem(0);
    if (first == null || first.getType().isAir()) {
      inventory.setItem(0, GuiItems.named(Material.PAPER, Component.text(session.request.initialText())));
    }
    ItemStack second = inventory.getItem(1);
    if (second != null && !second.getType().isAir()) {
      inventory.setItem(1, null);
    }
  }

  private static final class AnvilSession implements InventoryHolder {
    private final JavaPlugin plugin;
    private final UUID playerId;
    private final AnvilRequest request;
    private volatile boolean resolved;
    private BukkitTask timeoutTask;

    private AnvilSession(JavaPlugin plugin, UUID playerId, AnvilRequest request) {
      this.plugin = plugin;
      this.playerId = playerId;
      this.request = request;
    }

    @Override
    public Inventory getInventory() {
      return Bukkit.createInventory(this, InventoryType.ANVIL);
    }

    private void startTimeout() {
      long ticks = Math.max(1L, (request.timeout().toMillis() + 49L) / 50L);
      timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (resolved) {
          return;
        }
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) {
          return;
        }
        p.closeInventory();
        request.onTimeout().accept(p);
      }, ticks);
    }

    private void cancelTimeout() {
      if (timeoutTask != null) {
        timeoutTask.cancel();
        timeoutTask = null;
      }
    }
  }

  private static final class SignSession {
    private final JavaPlugin plugin;
    private final UUID playerId;
    private final SignRequest request;
    private final Location location;
    private final BlockState originalState;
    private BukkitTask timeoutTask;

    private SignSession(JavaPlugin plugin, UUID playerId, SignRequest request, Location location, BlockState originalState) {
      this.plugin = plugin;
      this.playerId = playerId;
      this.request = request;
      this.location = location;
      this.originalState = originalState;
    }

    static SignSession open(JavaPlugin plugin, Player player, SignRequest request) {
      Location loc = chooseSignLocation(player);
      Block block = loc.getBlock();
      BlockState original = block.getState();

      block.setType(Material.OAK_SIGN, false);
      BlockState state = block.getState();
      if (!(state instanceof Sign sign)) {
        original.update(true, false);
        throw new IllegalStateException("Failed to create sign state at " + loc);
      }

      sign.setWaxed(true);
      sign.setAllowedEditorUniqueId(player.getUniqueId());

      List<Component> lines = request.initialLines();
      for (int i = 0; i < 4; i++) {
        Component line = i < lines.size() ? lines.get(i) : Component.empty();
        sign.getSide(request.side()).line(i, line == null ? Component.empty() : line);
      }
      sign.update(true, false);

      player.openSign(sign, request.side());

      SignSession session = new SignSession(plugin, player.getUniqueId(), request, loc, original);
      session.startTimeout();
      return session;
    }

    private static Location chooseSignLocation(Player player) {
      int x = player.getLocation().getBlockX();
      int z = player.getLocation().getBlockZ();
      int y = player.getWorld().getMaxHeight() - 1;
      for (int i = 0; i < 16 && y - i > player.getWorld().getMinHeight(); i++) {
        Block block = player.getWorld().getBlockAt(x, y - i, z);
        if (block.getType().isAir()) {
          return block.getLocation();
        }
      }
      return player.getWorld().getBlockAt(x, y, z).getLocation();
    }

    boolean isTarget(Block block, Side side) {
      return block.getLocation().equals(location) && side == request.side();
    }

    void restore() {
      Bukkit.getScheduler().runTask(plugin, () -> originalState.update(true, false));
    }

    void startTimeout() {
      long ticks = Math.max(1L, (request.timeout().toMillis() + 49L) / 50L);
      timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
          cleanup(true);
          return;
        }
        restore();
        request.onTimeout().accept(player);
        cleanup(true);
      }, ticks);
    }

    void cancelTimeout() {
      if (timeoutTask != null) {
        timeoutTask.cancel();
        timeoutTask = null;
      }
    }

    void cleanup(boolean restore) {
      cancelTimeout();
      if (restore) {
        restore();
      }
    }
  }

  private static final class ItemPickSession {
    private final JavaPlugin plugin;
    private final UUID playerId;
    private final ItemPickRequest request;
    private BukkitTask timeoutTask;

    private ItemPickSession(JavaPlugin plugin, UUID playerId, ItemPickRequest request) {
      this.plugin = plugin;
      this.playerId = playerId;
      this.request = request;
    }

    private void startTimeout(Runnable onTimeout) {
      long ticks = Math.max(1L, (request.timeout().toMillis() + 49L) / 50L);
      timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
          return;
        }
        onTimeout.run();
      }, ticks);
    }

    private void cancelTimeout() {
      if (timeoutTask != null) {
        timeoutTask.cancel();
        timeoutTask = null;
      }
    }
  }
}
