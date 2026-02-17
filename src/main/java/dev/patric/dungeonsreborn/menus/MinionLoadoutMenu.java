package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.effects.minions.MinionManager.MinionLoadoutSnapshot;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class MinionLoadoutMenu extends Window {
  private final MinionManager minions;
  private final String minionId;

  public MinionLoadoutMenu(MinionManager minions, String minionId) {
    super(54, GuiI18n.tr("gui.minions.loadout.title",
        Placeholder.unparsed("minion", minionId == null ? "" : minionId)));
    this.minions = Objects.requireNonNull(minions, "minions");
    this.minionId = Objects.requireNonNull(minionId, "minionId");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    GuiNav.applyDetail(this, new BackButton(), new CloseButton());
    setFixedAt(0, 4, new Label(this::headerItem));
    setFixedAt(2, 4, new Label(this::infoItem));
    setFixedAt(rows() - 1, 5, dismissButton());
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.minions.loadout.header",
        Placeholder.unparsed("minion", minionId));
    return GuiItems.head("ICON_MINIONS", title, List.of());
  }

  private ItemStack infoItem(Player player) {
    List<Component> lore = new ArrayList<>();
    MinionLoadoutSnapshot snapshot = minions.loadout(player.getUniqueId(), minionId);
    if (snapshot == null) {
      lore.add(GuiMini.mm("<gray>No active minions.</gray>"));
      return GuiItems.head("ICON_MINIONS", GuiI18n.tr(player, "gui.minions.loadout.info"), lore);
    }
    lore.add(GuiI18n.tr(player, "gui.minions.loadout.count",
        Placeholder.unparsed("count", String.valueOf(snapshot.count()))));
    lore.add(GuiI18n.tr(player, "gui.minions.loadout.mob",
        Placeholder.unparsed("mob", snapshot.mobId())));
    if (snapshot.modeOverride() != null) {
      lore.add(GuiI18n.tr(player, "gui.minions.loadout.mode",
          Placeholder.unparsed("mode", snapshot.modeOverride().name())));
    }
    if (snapshot.mainAttackOverride() != null) {
      lore.add(GuiI18n.tr(player, "gui.minions.loadout.mainAttack",
          Placeholder.unparsed("attack", snapshot.mainAttackOverride())));
    }
    if (snapshot.secondaryAttackOverride() != null) {
      lore.add(GuiI18n.tr(player, "gui.minions.loadout.secondaryAttack",
          Placeholder.unparsed("attack", snapshot.secondaryAttackOverride())));
    }
    if (snapshot.disableBasePassives()) {
      lore.add(GuiI18n.tr(player, "gui.minions.loadout.disablePassives",
          Placeholder.unparsed("state", String.valueOf(snapshot.disableBasePassives()))));
    }
    if (snapshot.disableBaseAttacks()) {
      lore.add(GuiI18n.tr(player, "gui.minions.loadout.disableAttacks",
          Placeholder.unparsed("state", String.valueOf(snapshot.disableBaseAttacks()))));
    }
    if (snapshot.disableBaseAi()) {
      lore.add(GuiI18n.tr(player, "gui.minions.loadout.disableAi",
          Placeholder.unparsed("state", String.valueOf(snapshot.disableBaseAi()))));
    }
    return GuiItems.head("ICON_MINIONS", GuiI18n.tr(player, "gui.minions.loadout.info"), lore);
  }

  private Button dismissButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CANCEL,
        GuiI18n.tr(player, "gui.minions.loadout.dismiss.title"),
        List.of(GuiI18n.tr(player, "gui.minions.loadout.dismiss.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      int removed = minions.dismissById(ctx.player().getUniqueId(), minionId);
      ctx.player().sendMessage(GuiMini.mm("<gray>Dismissed:</gray> <white>" + removed + "</white>"));
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }
}
