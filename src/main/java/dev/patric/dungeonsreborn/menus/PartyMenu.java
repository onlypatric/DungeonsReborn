package dev.patric.dungeonsreborn.menus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class PartyMenu extends Window {
  private record PartyEntry(Party party, UUID memberId) {
    boolean isMember() {
      return memberId != null;
    }
  }

  private final PartyService parties;
  private final VirtualList<PartyEntry> list;

  public static void open(Player player, PartyService parties) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new PartyMenu(parties));
  }

  public PartyMenu(PartyService parties) {
    super(54, GuiI18n.tr("gui.party.title"));
    this.parties = Objects.requireNonNull(parties, "parties");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        this::entriesFor,
        this::renderEntry,
        this::handleEntryClick);
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    setFixedAt(0, 4, new Label(this::statusLabel));
  }

  @Override
  protected void build(Player player) {
    super.build(player);
    int navRow = rows() - 1;
    Party party = parties.partyOf(player);
    if (party == null) {
      setDynamicAt(navRow, 4, createButton());
      setDynamicAt(navRow, 5, acceptInviteButton());
    } else {
      setDynamicAt(navRow, 4, inviteButton(player));
      setDynamicAt(navRow, 5, togglePublicButton(player, party));
      setDynamicAt(navRow, 6, leaveButton());
    }
  }

  private List<PartyEntry> entriesFor(Player player) {
    Party party = parties.partyOf(player);
    if (party == null) {
      List<Party> publicParties = parties.publicParties();
      List<PartyEntry> entries = new ArrayList<>(publicParties.size());
      for (Party entry : publicParties) {
        if (entry != null) {
          entries.add(new PartyEntry(entry, null));
        }
      }
      return entries;
    }
    List<UUID> members = new ArrayList<>(party.members());
    members.sort(Comparator.comparing(this::memberName, String.CASE_INSENSITIVE_ORDER));
    List<PartyEntry> entries = new ArrayList<>(members.size());
    for (UUID member : members) {
      entries.add(new PartyEntry(party, member));
    }
    return entries;
  }

  private ItemStack renderEntry(Player player, PartyEntry entry) {
    if (entry == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    if (!entry.isMember()) {
      Party party = entry.party();
      String leaderName = parties.leaderName(party);
      Component title = GuiI18n.tr(player, "gui.party.list.public.title",
          Placeholder.unparsed("leader", leaderName));
      Component size = GuiI18n.tr(player, "gui.party.list.public.members",
          Placeholder.unparsed("count", String.valueOf(party.size())));
      Component status = party.isPublic()
          ? GuiI18n.tr(player, "gui.party.list.public.open")
          : GuiI18n.tr(player, "gui.party.list.public.closed");
      return GuiItems.head("ICON_PARTY", title, List.of(size, status));
    }

    UUID memberId = entry.memberId();
    String name = memberName(memberId);
    Party.Role role = entry.party().role(memberId);
    Component roleLabel = switch (role) {
      case LEADER -> GuiI18n.tr(player, "gui.party.member.role.leader");
      case OFFICER -> GuiI18n.tr(player, "gui.party.member.role.officer");
      case MEMBER -> GuiI18n.tr(player, "gui.party.member.role.member");
      default -> Component.text("?");
    };
    Component online = isOnline(memberId)
        ? GuiI18n.tr(player, "gui.party.member.online")
        : GuiI18n.tr(player, "gui.party.member.offline");
    return GuiItems.head("ICON_PARTY", Component.text(name), List.of(roleLabel, online));
  }

  private void handleEntryClick(Window.ClickContext ctx, PartyEntry entry) {
    if (entry == null || entry.isMember()) {
      return;
    }
    PartyService.Result result = parties.requestJoin(ctx.player(), entry.party().id());
    ctx.player().sendMessage(result.message());
  }

  private ItemStack statusLabel(Player player) {
    Party party = parties.partyOf(player);
    if (party == null) {
      return GuiItems.head("ICON_PARTY", GuiI18n.tr(player, "gui.party.status.none"), List.of());
    }
    Component title = GuiI18n.tr(player, "gui.party.status.inParty",
        Placeholder.unparsed("leader", parties.leaderName(party)));
    Component size = GuiI18n.tr(player, "gui.party.status.members",
        Placeholder.unparsed("count", String.valueOf(party.size())));
    return GuiItems.head("ICON_PARTY", title, List.of(size));
  }

  private Button createButton() {
    Button button = new Button(player -> GuiItems.head("ICON_PARTY",
        GuiI18n.tr(player, "gui.party.action.create.title"),
        List.of(GuiI18n.tr(player, "gui.party.action.create.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      PartyService.Result result = parties.createParty(ctx.player());
      ctx.player().sendMessage(result.message());
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton acceptInviteButton() {
    return new TextButton(
        player -> GuiItems.head("ICON_PARTY",
            GuiI18n.tr(player, "gui.party.action.accept.title"),
            List.of(GuiI18n.tr(player, "gui.party.action.accept.desc"))),
        GuiI18n.tr("gui.party.accept.prompt"),
        "cancel",
        Duration.ofSeconds(30),
        (window, text) -> {
          Player viewer = resolveViewer(window);
          if (viewer == null) {
            return;
          }
          PartyService.Result result = parties.acceptInvite(viewer, text);
          viewer.sendMessage(result.message());
          window.redraw(viewer);
        },
        true).minLength(1);
  }

  private GuiComponent inviteButton(Player player) {
    if (!parties.hasPermission(player.getUniqueId(), PartyService.Permission.INVITE)) {
      return null;
    }
    TextButton button = new TextButton(
        p -> GuiItems.head("ICON_PARTY",
            GuiI18n.tr(p, "gui.party.action.invite.title"),
            List.of(GuiI18n.tr(p, "gui.party.action.invite.desc"))),
        GuiI18n.tr("gui.party.invite.prompt"),
        "cancel",
        Duration.ofSeconds(30),
        (window, text) -> {
          Player viewer = resolveViewer(window);
          if (viewer == null) {
            return;
          }
          Player target = Bukkit.getPlayerExact(text);
          if (target == null) {
            viewer.sendMessage(GuiI18n.tr(viewer, "gui.party.invite.notFound",
                Placeholder.unparsed("player", text)));
            return;
          }
          PartyService.InviteResult result = parties.invite(viewer, target);
          viewer.sendMessage(result.message());
          if (result.success() && result.invite() != null) {
            target.sendMessage(GuiI18n.tr(target, "gui.party.invite.received",
                Placeholder.unparsed("leader", result.invite().leaderName())));
          }
          window.redraw(viewer);
        },
        true).minLength(1);
    button.autoDescribeInLore(false);
    return button;
  }

  private Button togglePublicButton(Player player, Party party) {
    if (!parties.hasPermission(player.getUniqueId(), PartyService.Permission.MANAGE_PUBLIC)) {
      return null;
    }
    Button button = new Button(p -> {
      boolean open = party.isPublic();
      Component status = open ? GuiI18n.tr(p, "gui.toggle.on") : GuiI18n.tr(p, "gui.toggle.off");
      String headId = open ? "STATE_ON" : "STATE_OFF";
      return GuiItems.head(headId, GuiI18n.tr(p, "gui.party.action.public.title"),
          List.of(GuiI18n.tr(p, "gui.party.action.public.desc"), status));
    });
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      Party partyNow = parties.partyOf(ctx.player());
      if (partyNow == null) {
        return;
      }
      boolean next = !partyNow.isPublic();
      PartyService.Result result = parties.setPublic(ctx.player(), next);
      ctx.player().sendMessage(result.message());
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private Button leaveButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CANCEL,
        GuiI18n.tr(player, "gui.party.action.leave.title"),
        List.of(GuiI18n.tr(player, "gui.party.action.leave.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      PartyService.Result result = parties.leave(ctx.player());
      ctx.player().sendMessage(result.message());
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private Player resolveViewer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return Bukkit.getPlayer(window.viewer());
  }

  private String memberName(UUID memberId) {
    if (memberId == null) {
      return "unknown";
    }
    OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
    if (offline != null && offline.getName() != null) {
      return offline.getName();
    }
    return memberId.toString();
  }

  private boolean isOnline(UUID memberId) {
    if (memberId == null) {
      return false;
    }
    return Bukkit.getPlayer(memberId) != null;
  }
}
