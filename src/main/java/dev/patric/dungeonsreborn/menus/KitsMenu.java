package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.kits.KitRewards;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.kits.KitSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class KitsMenu extends Window {
  private final KitService kits;
  private final VirtualList<KitSpec> list;
  private final boolean allowGive;

  public static void open(Player player, KitService kits) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new KitsMenu(kits, false));
  }

  public static void openAdmin(Player player, KitService kits) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new KitsMenu(kits, true));
  }

  public KitsMenu(KitService kits) {
    this(kits, false);
  }

  public KitsMenu(KitService kits, boolean allowGive) {
    super(54, GuiI18n.tr("gui.kits.title"));
    this.kits = Objects.requireNonNull(kits, "kits");
    this.allowGive = allowGive;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        this::kitEntries,
        this::renderEntry,
        this::handleEntryClick);
    this.list.searchKey(spec -> spec == null || spec.title() == null ? "" : spec.title());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<KitSpec> kitEntries(Player player) {
    List<KitSpec> entries = new ArrayList<>(kits.registry().kits().values());
    entries.sort(Comparator.comparing(kit -> {
      String title = kit.title();
      if (title == null || title.isBlank()) {
        title = kit.id();
      }
      return title.toLowerCase(java.util.Locale.ROOT);
    }));
    return entries;
  }

  private ItemStack renderEntry(Player player, KitSpec kit) {
    if (kit == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    String titleText = kit.title() == null || kit.title().isBlank() ? kit.id() : kit.title();
    Component title = Component.text(titleText);
    List<Component> lore = new ArrayList<>();
    KitService.KitStatus status = kits.status(player, kit);
    if (status.available()) {
      lore.add(GuiI18n.tr(player, "gui.kits.status.available"));
    } else if (status.remainingMillis() > 0) {
      lore.add(GuiI18n.tr(player, "gui.kits.status.cooldown",
          Placeholder.unparsed("time", formatDuration(status.remainingMillis()))));
    } else {
      lore.add(GuiI18n.tr(player, "gui.kits.status.unavailable"));
      lore.add(GuiMini.mm(status.message()));
    }
    lore.add(GuiI18n.tr(player, "gui.kits.entry.items",
        Placeholder.unparsed("count", String.valueOf(kit.items().size()))));
    KitRewards rewards = kit.rewards();
    if (rewards != null) {
      if (rewards.xp() > 0) {
        lore.add(GuiI18n.tr(player, "gui.kits.entry.xp",
            Placeholder.unparsed("amount", String.valueOf(rewards.xp()))));
      }
      if (rewards.tokens() > 0) {
        lore.add(GuiI18n.tr(player, "gui.kits.entry.tokens",
            Placeholder.unparsed("amount", String.valueOf(rewards.tokens()))));
      }
      if (rewards.compressed() > 0) {
        lore.add(GuiI18n.tr(player, "gui.kits.entry.compressed",
            Placeholder.unparsed("amount", String.valueOf(rewards.compressed()))));
      }
      if (rewards.pallet() > 0) {
        lore.add(GuiI18n.tr(player, "gui.kits.entry.pallets",
            Placeholder.unparsed("amount", String.valueOf(rewards.pallet()))));
      }
    }
    if (kit.oneTime()) {
      lore.add(GuiI18n.tr(player, "gui.kits.entry.oneTime"));
    }
    if (kit.permission() != null && !kit.permission().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.kits.entry.permission",
          Placeholder.unparsed("permission", kit.permission())));
    }
    lore.add(GuiI18n.tr(player, "gui.kits.entry.view"));
    if (allowGive && player.hasPermission("dungeonsreborn.kits.give")) {
      lore.add(GuiI18n.tr(player, "gui.kits.entry.give"));
    }

    return GuiItems.head("ICON_KITS", title, lore);
  }

  private void handleEntryClick(Window.ClickContext ctx, KitSpec kit) {
    if (kit == null) {
      return;
    }
    if (allowGive && ctx.player().hasPermission("dungeonsreborn.kits.give")
        && ctx.clickType().isLeftClick()) {
      List<ItemStack> items = kits.previewItems(kit);
      for (ItemStack item : items) {
        if (item == null) {
          continue;
        }
        giveToPlayer(ctx.player(), item.clone());
      }
      return;
    }
    ctx.window().openSubWindow(ctx.player(), new KitInspectMenu(kits, kit));
  }

  private ItemStack headerItem(Player player) {
    int count = kits.registry().kits().size();
    return GuiItems.head("ICON_KITS", GuiI18n.tr(player, "gui.kits.header",
        Placeholder.unparsed("count", String.valueOf(count))), List.of());
  }

  private static String formatDuration(long millis) {
    long totalSeconds = Math.max(0L, millis / 1000L);
    long minutes = totalSeconds / 60L;
    long seconds = totalSeconds % 60L;
    if (minutes > 0) {
      return minutes + "m " + seconds + "s";
    }
    return seconds + "s";
  }

  private static void giveToPlayer(Player player, ItemStack item) {
    var leftovers = player.getInventory().addItem(item);
    if (!leftovers.isEmpty()) {
      for (ItemStack stack : leftovers.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), stack);
      }
    }
  }
}
