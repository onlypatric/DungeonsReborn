package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.minions.MinionMode;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.mobs.MobMarkers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class MinionMenu extends Window {
  private record MinionEntry(String minionId, String mobId, int count) {
  }

  private final MinionManager minions;
  private final VirtualList<MinionEntry> list;

  public static void open(Player player, MinionManager minions) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new MinionMenu(minions));
  }

  public MinionMenu(MinionManager minions) {
    super(54, GuiI18n.tr("gui.minions.title"));
    this.minions = Objects.requireNonNull(minions, "minions");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        this::minionEntries,
        this::renderEntry,
        this::openLoadout);
    this.list.searchKey(entry -> entry == null ? "" : entry.minionId());
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    nav(5, dismissAllButton());
    nav(6, modeButton());
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<MinionEntry> minionEntries(Player player) {
    Map<String, MinionEntry> entries = new LinkedHashMap<>();
    UUID ownerId = player.getUniqueId();
    for (UUID entityId : minions.minionsFor(ownerId)) {
      Entity entity = Bukkit.getEntity(entityId);
      if (!(entity instanceof LivingEntity living)) {
        continue;
      }
      String minionId = MobMarkers.getMinionId(living);
      String mobId = MobMarkers.getMobId(living);
      if (minionId == null) {
        continue;
      }
      MinionEntry existing = entries.get(minionId);
      if (existing == null) {
        entries.put(minionId, new MinionEntry(minionId, mobId, 1));
      } else {
        entries.put(minionId, new MinionEntry(minionId, existing.mobId(), existing.count() + 1));
      }
    }
    List<MinionEntry> list = new ArrayList<>(entries.values());
    list.sort(Comparator.comparing(entry -> entry.minionId().toLowerCase(java.util.Locale.ROOT)));
    return list;
  }

  private ItemStack renderEntry(Player player, MinionEntry entry) {
    if (entry == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    Component title = Component.text(entry.minionId());
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.minions.entry.count",
        Placeholder.unparsed("count", String.valueOf(entry.count()))));
    if (entry.mobId() != null) {
      lore.add(GuiI18n.tr(player, "gui.minions.entry.mob",
          Placeholder.unparsed("mob", entry.mobId())));
    }
    return GuiItems.head("ICON_MINIONS", title, lore);
  }

  private void openLoadout(Window.ClickContext ctx, MinionEntry entry) {
    if (entry == null) {
      return;
    }
    ctx.window().openSubWindow(ctx.player(), new MinionLoadoutMenu(minions, entry.minionId()));
  }

  private Button modeButton() {
    Button button = new Button(player -> {
      MinionMode mode = minions.mode(player.getUniqueId());
      Component status = GuiI18n.tr(player, "gui.minions.mode.label",
          Placeholder.unparsed("mode", mode.name()));
      return GuiItems.head("ICON_MINIONS", GuiI18n.tr(player, "gui.minions.mode.title"),
          List.of(GuiI18n.tr(player, "gui.minions.mode.desc"), status));
    });
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      UUID ownerId = ctx.player().getUniqueId();
      MinionMode next = nextMode(minions.mode(ownerId));
      minions.setMode(ownerId, next);
      ctx.player().sendMessage(GuiMini.mm("<gray>Minion mode:</gray> <white>" + next.name() + "</white>"));
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private Button dismissAllButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CANCEL,
        GuiI18n.tr(player, "gui.minions.dismissAll.title"),
        List.of(GuiI18n.tr(player, "gui.minions.dismissAll.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      int removed = minions.dismiss(ctx.player().getUniqueId());
      ctx.player().sendMessage(GuiMini.mm("<gray>Dismissed:</gray> <white>" + removed + "</white>"));
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.minions.header",
        Placeholder.unparsed("count", String.valueOf(minions.minionsFor(player.getUniqueId()).size())));
    return GuiItems.head("ICON_MINIONS", title, List.of());
  }

  private MinionMode nextMode(MinionMode current) {
    MinionMode[] values = MinionMode.values();
    if (current == null || values.length == 0) {
      return MinionMode.AGGRESSIVE;
    }
    int next = current.ordinal() + 1;
    if (next >= values.length) {
      next = 0;
    }
    return values[next];
  }
}
