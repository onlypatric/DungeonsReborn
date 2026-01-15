package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class PartyMenu extends Window {
  private static final int SIZE = 54;
  private static final int SLOT_CHAT = 52;

  private record MemberEntry(UUID id, String name, boolean leader, boolean online) {
  }

  private final PartyService parties;
  private final VirtualList<MemberEntry> list;

  public PartyMenu(PartyService parties) {
    super(SIZE, GuiI18n.tr("gui.party.title"), true);
    this.parties = Objects.requireNonNull(parties, "parties");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> {
        });
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.button.close"), List.of())));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(5, refreshButton());
    nav(6, leaveButton());

    setFixedAt(0, 4, header());
    setFixed(SLOT_CHAT, chatToggleButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(player -> {
      Party party = parties.partyOf(player);
      if (party == null) {
        return GuiItems.named(Material.BOOK, GuiI18n.tr(player, "gui.party.header.title"), List.of(
            GuiI18n.tr(player, "gui.party.header.empty"),
            GuiI18n.tr(player, "gui.party.header.emptyHint")));
      }
      List<Component> lore = new ArrayList<>();
      String world = party.worldKey();
      lore.add(GuiI18n.tr(player, "gui.party.header.world", Placeholder.unparsed("world", world)));
      lore.add(GuiI18n.tr(player, "gui.party.header.members", Placeholder.unparsed("count", String.valueOf(party.size()))));
      boolean chatEnabled = parties.isChatEnabled(player);
      Component chatStatus = chatEnabled
          ? GuiI18n.tr(player, "gui.party.chat.on")
          : GuiI18n.tr(player, "gui.party.chat.off");
      lore.add(GuiI18n.tr(player, "gui.party.header.chat", Placeholder.component("status", chatStatus)));
      if (player.getWorld() != null) {
        String current = player.getWorld().getKey().toString();
        if (!current.equalsIgnoreCase(world)) {
          lore.add(GuiI18n.tr(player, "gui.party.header.worldMismatch", Placeholder.unparsed("world", current)));
        }
      }
      return GuiItems.named(Material.BOOK, GuiI18n.tr(player, "gui.party.header.title"), lore);
    });
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.party.refresh.title"), List.of(
        GuiI18n.tr(p, "gui.party.refresh.hint"))), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      ctx.window().redrawSlot(ctx.player(), 4);
      ctx.window().redrawSlot(ctx.player(), SLOT_CHAT);
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button leaveButton() {
    return new Button(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.party.leave.title"), List.of(
        GuiI18n.tr(p, "gui.party.leave.hint"))), ctx -> {
      Party party = parties.partyOf(ctx.player());
      if (party == null) {
        ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.party.error.notInParty"));
        return;
      }
      PartyService.Result result = parties.leave(ctx.player());
      String wrapped = result.success()
          ? "<green>" + result.message() + "</green>"
          : "<red>" + result.message() + "</red>";
      ctx.player().sendMessage(GuiMini.mm(wrapped));
      ctx.close();
    }).autoDescribeInLore(false);
  }

  private Button chatToggleButton() {
    return new Button(player -> {
      boolean enabled = parties.isChatEnabled(player);
      return GuiItems.named(Material.OAK_SIGN,
          enabled ? GuiI18n.tr(player, "gui.party.chat.titleOn") : GuiI18n.tr(player, "gui.party.chat.titleOff"),
          List.of(
              GuiI18n.tr(player, "gui.party.chat.status", Placeholder.component("status",
                  enabled ? GuiI18n.tr(player, "gui.party.chat.on") : GuiI18n.tr(player, "gui.party.chat.off"))),
              GuiI18n.tr(player, "gui.party.chat.hint")));
    }, ctx -> {
      boolean enabled = parties.isChatEnabled(ctx.player());
      PartyService.Result result = parties.toggleChat(ctx.player(), !enabled);
      String wrapped = result.success()
          ? "<green>" + result.message() + "</green>"
          : "<red>" + result.message() + "</red>";
      ctx.player().sendMessage(GuiMini.mm(wrapped));
      ctx.window().redrawSlot(ctx.player(), SLOT_CHAT);
      ctx.window().redrawSlot(ctx.player(), 4);
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<MemberEntry> entries(Player player) {
    Party party = parties.partyOf(player);
    if (party == null) {
      return List.of();
    }
    List<MemberEntry> out = new ArrayList<>();
    for (UUID memberId : party.members()) {
      OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
      String name = member.getName() == null ? memberId.toString() : member.getName();
      boolean online = member.isOnline();
      boolean leader = memberId.equals(party.leader());
      out.add(new MemberEntry(memberId, name, leader, online));
    }
    return out;
  }

  private org.bukkit.inventory.ItemStack entryItem(MemberEntry entry) {
    Material material = entry.leader ? Material.GOLDEN_HELMET : (entry.online ? Material.LIME_DYE : Material.GRAY_DYE);
    Component status = entry.online
        ? GuiI18n.tr("gui.party.member.online")
        : GuiI18n.tr("gui.party.member.offline");
    Component role = entry.leader
        ? GuiI18n.tr("gui.party.member.leader")
        : GuiI18n.tr("gui.party.member.member");
    return GuiItems.named(material, GuiI18n.tr("gui.party.member.title", Placeholder.unparsed("name", entry.name)), List.of(
        GuiI18n.tr("gui.party.member.status", Placeholder.component("status", status)),
        GuiI18n.tr("gui.party.member.role", Placeholder.component("role", role))));
  }
}
