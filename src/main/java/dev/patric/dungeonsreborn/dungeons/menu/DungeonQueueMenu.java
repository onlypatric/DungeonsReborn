package dev.patric.dungeonsreborn.dungeons.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.dungeons.DungeonQueueService;
import dev.patric.dungeonsreborn.dungeons.DungeonSessionManager;
import dev.patric.dungeonsreborn.dungeons.DungeonSpec;
import dev.patric.dungeonsreborn.dungeons.DungeonYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class DungeonQueueMenu extends Window {
  private final DungeonYamlRegistry registry;
  private final DungeonQueueService queue;
  private final DungeonSessionManager sessions;
  private final PartyService parties;
  private final VirtualList<Integer> list;

  public static void open(Player player, DungeonYamlRegistry registry, DungeonQueueService queue,
      DungeonSessionManager sessions, PartyService parties) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new DungeonQueueMenu(registry, queue, sessions, parties));
  }

  public DungeonQueueMenu(DungeonYamlRegistry registry, DungeonQueueService queue,
      DungeonSessionManager sessions, PartyService parties) {
    super(54, GuiI18n.tr("gui.dungeons.title"));
    this.registry = Objects.requireNonNull(registry, "registry");
    this.queue = Objects.requireNonNull(queue, "queue");
    this.sessions = sessions;
    this.parties = parties;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        this::levelsFor,
        this::renderLevel,
        this::handleEntryClick);
    this.list.apply(this, Placement.DYNAMIC);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, statusButton());
    nav(4, leaveButton());
    setFixedAt(0, 4, new Label(this::headerItem));
    setFixedAt(0, 3, new Label(this::statusItem));
  }

  private List<Integer> levelsFor(Player player) {
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null || dungeon.levels().isEmpty()) {
      return List.of();
    }
    List<Integer> levels = new ArrayList<>(dungeon.levels().keySet());
    levels.sort(Comparator.naturalOrder());
    return levels;
  }

  private ItemStack renderLevel(Player player, Integer level) {
    if (level == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    DungeonSpec.DungeonLevel spec = dungeon.levels().get(level);
    if (spec == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }

    Component title = GuiI18n.tr(player, "gui.dungeons.level.title",
        Placeholder.unparsed("level", String.valueOf(level)));
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.dungeons.level.queueTokens",
        Placeholder.unparsed("tokens", String.valueOf(spec.queueTokens()))));
    if (spec.waitSeconds() > 0) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.level.wait",
          Placeholder.unparsed("seconds", String.valueOf(spec.waitSeconds()))));
    }
    if (spec.timeLimitSeconds() > 0) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.level.timeLimit",
          Placeholder.unparsed("seconds", String.valueOf(spec.timeLimitSeconds()))));
    }
    if (spec.modifiers() != null && spec.modifiers().affixes() != null && !spec.modifiers().affixes().isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.level.affixes",
          Placeholder.unparsed("count", String.valueOf(spec.modifiers().affixes().size()))));
    }
    if (spec.rewards() != null) {
      if (spec.rewards().tokens() != null) {
        int min = Math.max(0, spec.rewards().tokens().min());
        int max = Math.max(min, spec.rewards().tokens().max());
        lore.add(GuiI18n.tr(player, "gui.dungeons.level.rewards.tokens",
            Placeholder.unparsed("min", String.valueOf(min)),
            Placeholder.unparsed("max", String.valueOf(max))));
      }
      if (spec.rewards().skillPoints() > 0) {
        lore.add(GuiI18n.tr(player, "gui.dungeons.level.rewards.skill",
            Placeholder.unparsed("points", String.valueOf(spec.rewards().skillPoints()))));
      }
    }

    boolean unlocked = isUnlocked(player, dungeon, level);
    if (!unlocked) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.level.locked",
          Placeholder.unparsed("level", String.valueOf(Math.max(1, level - 1)))));
    }
    lore.add(GuiI18n.tr(player, "gui.dungeons.level.action.queue"));
    if (canQueueParty(player)) {
      lore.add(GuiI18n.tr(player, "gui.dungeons.level.action.queueParty"));
    }

    return GuiItems.head("ICON_DUNGEONS", title, lore);
  }

  private void handleEntryClick(Window.ClickContext ctx, Integer level) {
    if (level == null) {
      return;
    }
    boolean partyQueue = ctx.clickType() == ClickType.RIGHT || ctx.clickType() == ClickType.SHIFT_RIGHT;
    DungeonQueueService.JoinResult result;
    if (partyQueue && canQueueParty(ctx.player())) {
      Party party = parties.partyOf(ctx.player());
      List<Player> members = new ArrayList<>();
      if (party != null) {
        for (UUID memberId : party.members()) {
          Player member = Bukkit.getPlayer(memberId);
          if (member != null) {
            members.add(member);
          }
        }
      }
      result = queue.joinParty(ctx.player(), members, level);
    } else {
      result = queue.join(ctx.player(), level);
    }
    ctx.player().sendMessage(result.message());
    ctx.redraw();
  }

  private ItemStack headerItem(Player player) {
    DungeonSpec dungeon = registry.dungeon();
    if (dungeon == null || dungeon.levels() == null) {
      return GuiItems.head("ICON_DUNGEONS", registry.unavailableMessage(), List.of());
    }
    Component title = GuiI18n.tr(player, "gui.dungeons.header",
        Placeholder.unparsed("count", String.valueOf(dungeon.levels().size())));
    return GuiItems.head("ICON_DUNGEONS", title, List.of());
  }

  private ItemStack statusItem(Player player) {
    DungeonQueueService.QueueStatus status = queue.status(player.getUniqueId());
    Component title;
    if (!status.queued()) {
      title = GuiI18n.tr(player, "gui.dungeons.status.queue.none");
    } else if (status.active()) {
      title = GuiI18n.tr(player, "gui.dungeons.status.queue.active",
          Placeholder.unparsed("level", String.valueOf(status.level())));
    } else {
      title = GuiI18n.tr(player, "gui.dungeons.status.queue.queued",
          Placeholder.unparsed("level", String.valueOf(status.level())),
          Placeholder.unparsed("position", String.valueOf(status.position())),
          Placeholder.unparsed("total", String.valueOf(status.totalInLevel())));
    }
    return GuiItems.head("ICON_DUNGEONS", title, List.of());
  }

  private Button statusButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.INFO,
        GuiI18n.tr(player, "gui.dungeons.action.status.title"),
        List.of(GuiI18n.tr(player, "gui.dungeons.action.status.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx ->
        ctx.window().openSubWindow(ctx.player(), new DungeonStatusMenu(registry, queue, sessions)));
    button.autoDescribeInLore(false);
    return button;
  }

  private Button leaveButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CANCEL,
        GuiI18n.tr(player, "gui.dungeons.action.leave.title"),
        List.of(GuiI18n.tr(player, "gui.dungeons.action.leave.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      DungeonQueueService.LeaveResult result = queue.leave(ctx.player());
      ctx.player().sendMessage(result.message());
      ctx.redraw();
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private boolean canQueueParty(Player player) {
    if (player == null || parties == null) {
      return false;
    }
    Party party = parties.partyOf(player);
    if (party == null || party.size() <= 1) {
      return false;
    }
    return parties.isLeader(player.getUniqueId());
  }

  private boolean isUnlocked(Player player, DungeonSpec dungeon, int level) {
    if (player == null || dungeon == null || level <= 1) {
      return true;
    }
    int max = queue.maxCompleted(player.getUniqueId(), dungeon.id());
    return max >= level - 1;
  }
}
