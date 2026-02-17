package dev.patric.dungeonsreborn.menus;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.kits.KitRewards;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.kits.KitSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class KitInspectMenu extends Window {
  private final KitService kits;
  private final KitSpec kit;
  private final VirtualList<ItemStack> list;

  public KitInspectMenu(KitService kits, KitSpec kit) {
    super(54, GuiI18n.tr("gui.kits.inspect.title",
        Placeholder.unparsed("kit", kit == null ? "" : kit.id())));
    this.kits = Objects.requireNonNull(kits, "kits");
    this.kit = Objects.requireNonNull(kit, "kit");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> kits.previewItems(kit),
        this::renderItem,
        (ctx, item) -> {
        });
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyDetail(this, new BackButton(), new CloseButton(), claimButton());
    setFixedAt(0, 4, new Label(this::headerItem));
    setFixedAt(0, 3, new Label(this::infoItem));
  }

  private ItemStack renderItem(Player player, ItemStack item) {
    if (item == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    return item;
  }

  private ItemStack headerItem(Player player) {
    String titleText = kit.title() == null || kit.title().isBlank() ? kit.id() : kit.title();
    Component title = GuiI18n.tr(player, "gui.kits.inspect.header",
        Placeholder.unparsed("kit", titleText));
    return GuiItems.head("ICON_KITS", title, List.of());
  }

  private ItemStack infoItem(Player player) {
    List<Component> lore = new java.util.ArrayList<>();
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
    lore.add(GuiI18n.tr(player, "gui.kits.inspect.items",
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
    return GuiItems.head("ICON_KITS", GuiI18n.tr(player, "gui.kits.inspect.info"), lore);
  }

  private Button claimButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CONFIRM,
        GuiI18n.tr(player, "gui.kits.claim.title"),
        List.of(GuiI18n.tr(player, "gui.kits.claim.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      KitService.ClaimResult result = kits.claim(ctx.player(), kit.id());
      ctx.player().sendMessage(GuiMini.mm(result.message()));
      ctx.redraw();
    });
    button.autoDescribeInLore(false);
    return button;
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
}
