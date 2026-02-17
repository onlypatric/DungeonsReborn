package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiDebug;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class MobIndexMenu extends Window {
  private final MobRegistry mobs;
  private final VirtualList<String> list;
  private final dev.patric.dungeonsreborn.mobs.MobYamlRegistry yaml;
  private final boolean allowGive;
  private boolean debugLogged;

  public static void open(Player player, MobRegistry mobs) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new MobIndexMenu(mobs, null, false));
  }

  public static void openAdmin(Player player, MobRegistry mobs, dev.patric.dungeonsreborn.mobs.MobYamlRegistry yaml) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new MobIndexMenu(mobs, yaml, true));
  }

  public MobIndexMenu(MobRegistry mobs) {
    this(mobs, null, false);
  }

  public MobIndexMenu(MobRegistry mobs, dev.patric.dungeonsreborn.mobs.MobYamlRegistry yaml, boolean allowGive) {
    super(54, GuiI18n.tr("gui.mobs.title"));
    this.mobs = Objects.requireNonNull(mobs, "mobs");
    this.yaml = yaml;
    this.allowGive = allowGive;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> mobEntries(),
        this::renderEntry,
        this::handleEntryClick);
    this.list.searchKey(this::mobSearchKey);
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<String> mobEntries() {
    List<String> ids = new ArrayList<>(mobs.ids());
    debugLogged = GuiDebug.logIndexOnce(debugLogged, "mobs", ids.size());
    ids.sort(Comparator.comparing(id -> mobTitleKey(id).toLowerCase(java.util.Locale.ROOT)));
    return ids;
  }

  private ItemStack renderEntry(Player player, String id) {
    if (id == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    MobSpec spec = mobs.get(id);
    Component title = titleFromSpec(spec, id);
    List<Component> lore = new ArrayList<>();
    if (spec != null && spec.entityType() != null) {
      lore.add(GuiI18n.tr(player, "gui.mobs.entry.type",
          Placeholder.unparsed("type", spec.entityType().name())));
    }
    if (spec != null && spec.tier() != null && !spec.tier().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.mobs.entry.tier",
          Placeholder.unparsed("tier", spec.tier())));
    }
    if (allowGive && player.hasPermission("dungeonsreborn.mobs.egg.give")) {
      lore.add(GuiI18n.tr(player, "gui.mobs.entry.giveEgg"));
      lore.add(GuiI18n.tr(player, "gui.mobs.entry.giveEggStack"));
    }
    if (allowGive && player.hasPermission("dungeonsreborn.mobs.spawner.give")) {
      lore.add(GuiI18n.tr(player, "gui.mobs.entry.giveSpawner"));
    }
    ItemStack egg = spawnEgg(spec);
    if (egg != null) {
      return GuiItems.named(egg, title, lore, true);
    }
    return GuiItems.head("ICON_MOBS", title, lore);
  }

  private String mobSearchKey(String id) {
    if (id == null) {
      return "";
    }
    MobSpec spec = mobs.get(id);
    Component title = titleFromSpec(spec, id);
    return PlainTextComponentSerializer.plainText().serialize(title);
  }

  private String mobTitleKey(String id) {
    if (id == null) {
      return "";
    }
    MobSpec spec = mobs.get(id);
    Component title = titleFromSpec(spec, id);
    return PlainTextComponentSerializer.plainText().serialize(title);
  }

  private static Component titleFromSpec(MobSpec spec, String fallback) {
    if (spec != null && spec.displayName() != null) {
      return spec.displayName();
    }
    return Component.text(fallback);
  }

  private static ItemStack spawnEgg(MobSpec spec) {
    if (spec == null || spec.entityType() == null) {
      return null;
    }
    String eggId = spec.entityType().name() + "_SPAWN_EGG";
    Material material = Material.matchMaterial(eggId);
    if (material == null || material.isAir()) {
      return null;
    }
    return new ItemStack(material);
  }

  private void handleEntryClick(Window.ClickContext ctx, String id) {
    if (id == null) {
      return;
    }
    if (allowGive && yaml != null) {
      if (ctx.clickType().isLeftClick() && ctx.player().hasPermission("dungeonsreborn.mobs.egg.give")) {
        int amount = ctx.isShiftClick() ? 64 : 1;
        ItemStack egg = yaml.eggItemForMob(id);
        if (egg != null) {
          egg.setAmount(amount);
          giveToPlayer(ctx.player(), egg);
        }
        return;
      }
      if (ctx.clickType().isRightClick() && ctx.player().hasPermission("dungeonsreborn.mobs.spawner.give")) {
        ItemStack spawner = yaml.spawnerBlockItemForMob(id);
        if (spawner != null) {
          giveToPlayer(ctx.player(), spawner);
        }
        return;
      }
    }
    MobSpec spec = mobs.get(id);
    if (spec != null) {
      ctx.window().openSubWindow(ctx.player(), new MobInspectMenu(spec, yaml));
    }
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.mobs.header",
        Placeholder.unparsed("count", String.valueOf(mobs.ids().size())));
    return GuiItems.head("ICON_MOBS", title, List.of());
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
