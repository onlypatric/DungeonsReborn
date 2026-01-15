package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import dev.patric.dungeonsreborn.kits.KitRewards;
import dev.patric.dungeonsreborn.kits.KitService;
import dev.patric.dungeonsreborn.kits.KitSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class KitsMenu extends Window {
  private static final int SIZE = 54;

  private record Entry(KitSpec kit) {
  }

  private final KitService kits;

  public KitsMenu(KitService kits) {
    super(SIZE, GuiI18n.tr("gui.kits.title"), true);
    this.kits = Objects.requireNonNull(kits, "kits");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    VirtualList<Entry> list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry.kit()),
        (ctx, entry) -> {
          KitService.ClaimResult result = kits.claim(ctx.player(), entry.kit().id());
          String wrapped = result.success()
              ? "<green>" + result.message() + "</green>"
              : "<red>" + result.message() + "</red>";
          ctx.player().sendMessage(GuiMini.mm(wrapped));
          ctx.redraw();
          GuiSounds.click(ctx.player());
        });
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.CHEST, GuiI18n.tr("gui.kits.header.title"), List.of(
        GuiI18n.tr("gui.kits.header.subtitle"),
        GuiI18n.tr("gui.kits.header.note")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<Entry> entries(Player player) {
    List<Entry> out = new ArrayList<>();
    for (KitSpec kit : kits.registry().kits().values()) {
      out.add(new Entry(kit));
    }
    out.sort(Comparator.comparing(entry -> entry.kit().title().toLowerCase(Locale.ROOT)));
    return out;
  }

  private ItemStack entryItem(Player player, KitSpec kit) {
    ItemStack base = resolveBaseItem(kit);
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.kits.lore.id", Placeholder.unparsed("id", kit.id())));
    if (kit.permission() != null && !kit.permission().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.kits.lore.permission", Placeholder.unparsed("permission", kit.permission())));
    }
    KitService.KitStatus status = kits.status(player, kit);
    Component statusLabel = status.available()
        ? GuiI18n.tr(player, "gui.kits.status.available")
        : GuiI18n.tr(player, "gui.kits.status.unavailable");
    lore.add(GuiI18n.tr(player, "gui.kits.lore.status",
        Placeholder.component("status", statusLabel),
        Placeholder.unparsed("message", status.message())));
    Component oneTimeValue = kit.oneTime()
        ? Locales.component(player, "messages.common.yes")
        : Locales.component(player, "messages.common.no");
    lore.add(GuiI18n.tr(player, "gui.kits.lore.oneTime", Placeholder.component("value", oneTimeValue)));
    if (kit.cooldownSeconds() > 0) {
      lore.add(GuiI18n.tr(player, "gui.kits.lore.cooldown", Placeholder.unparsed("seconds", String.valueOf(kit.cooldownSeconds()))));
    }
    appendRewardsPreview(lore, kit);
    lore.add(status.available()
        ? GuiI18n.tr(player, "gui.kits.lore.action.claim")
        : GuiI18n.tr(player, "gui.kits.lore.action.unavailable"));

    return GuiItem.of(base)
        .displayName(GuiI18n.tr(player, "gui.kits.itemTitle", Placeholder.unparsed("title", kit.title())))
        .lore(lore)
        .build();
  }

  private ItemStack resolveBaseItem(KitSpec kit) {
    List<ItemStack> items = kits.previewItems(kit);
    for (ItemStack stack : items) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      ItemStack out = stack.clone();
      out.setAmount(1);
      return out;
    }
    return new ItemStack(Material.CHEST);
  }

  private void appendRewardsPreview(List<Component> lore, KitSpec kit) {
    List<ItemStack> items = kits.previewItems(kit);
    if (!items.isEmpty()) {
      lore.add(GuiI18n.tr("gui.kits.rewards.itemsTitle"));
      int shown = 0;
      for (ItemStack stack : items) {
        if (stack == null || stack.getType().isAir()) {
          continue;
        }
        String name = itemLabel(stack);
        lore.add(GuiI18n.tr("gui.kits.rewards.itemEntry",
            Placeholder.unparsed("amount", String.valueOf(stack.getAmount())),
            Placeholder.unparsed("name", name)));
        shown++;
        if (shown >= 5) {
          break;
        }
      }
      if (items.size() > shown) {
        lore.add(GuiI18n.tr("gui.kits.rewards.more"));
      }
    }
    KitRewards rewards = kit.rewards();
    if (rewards == null || rewards.isEmpty()) {
      return;
    }
    lore.add(GuiI18n.tr("gui.kits.rewards.rewardsTitle"));
    if (rewards.xp() > 0) {
      lore.add(GuiI18n.tr("gui.kits.rewards.xp", Placeholder.unparsed("amount", String.valueOf(rewards.xp()))));
    }
    if (rewards.tokens() > 0) {
      lore.add(GuiI18n.tr("gui.kits.rewards.tokens", Placeholder.unparsed("amount", String.valueOf(rewards.tokens()))));
    }
    if (rewards.compressed() > 0) {
      lore.add(GuiI18n.tr("gui.kits.rewards.compressed", Placeholder.unparsed("amount", String.valueOf(rewards.compressed()))));
    }
    if (rewards.pallet() > 0) {
      lore.add(GuiI18n.tr("gui.kits.rewards.pallet", Placeholder.unparsed("amount", String.valueOf(rewards.pallet()))));
    }
  }

  private String itemLabel(ItemStack stack) {
    String itemId = ItemMarkers.getItemId(stack);
    if (itemId != null && !itemId.isBlank()) {
      return itemId;
    }
    return stack.getType().name().toLowerCase(Locale.ROOT);
  }
}
