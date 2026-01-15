package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.state.GuiState;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Mob index showcase: virtual list + filter + preview + state binding.
 */
public final class ShowcaseMobIndexMenu extends Window {
  private static final int SIZE = 54;
  private static final int SLOT_FILTER = 6;
  private static final int SLOT_CLEAR_FILTER = 7;
  private static final int SLOT_PREVIEW = 17;
  private static final int SLOT_PREVIEW_LORE = 26;

  private record MobEntry(String id, Material icon, Component name, List<Component> lore) {
    private MobEntry {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(icon, "icon");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(lore, "lore");
    }
  }

  private static final List<MobEntry> MOBS = List.of(
      mob("zombie", Material.ZOMBIE_SPAWN_EGG, "Zombie", "Slow but relentless.", "Common undead."),
      mob("skeleton", Material.SKELETON_SPAWN_EGG, "Skeleton", "Ranged attacker.", "Drops bones/arrows."),
      mob("creeper", Material.CREEPER_SPAWN_EGG, "Creeper", "Explosive.", "Keep your distance."),
      mob("spider", Material.SPIDER_SPAWN_EGG, "Spider", "Climbs walls.", "Fast melee."),
      mob("enderman", Material.ENDERMAN_SPAWN_EGG, "Enderman", "Teleports.", "Aggro on eye contact."),
      mob("witch", Material.WITCH_SPAWN_EGG, "Witch", "Throws potions.", "Heals and poisons."),
      mob("slime", Material.SLIME_SPAWN_EGG, "Slime", "Splits on death.", "Bouncy."),
      mob("blaze", Material.BLAZE_SPAWN_EGG, "Blaze", "Shoots fire.", "Nether mob."),
      mob("ghast", Material.GHAST_SPAWN_EGG, "Ghast", "Flying artillery.", "Fireballs."),
      mob("piglin", Material.PIGLIN_SPAWN_EGG, "Piglin", "Gold lover.", "Barters."),
      mob("hoglin", Material.HOGLIN_SPAWN_EGG, "Hoglin", "Charges.", "Raw pork in nether."),
      mob("strider", Material.STRIDER_SPAWN_EGG, "Strider", "Walks on lava.", "Saddleable."),
      mob("guardian", Material.GUARDIAN_SPAWN_EGG, "Guardian", "Laser beam.", "Ocean monument."),
      mob("elder_guardian", Material.ELDER_GUARDIAN_SPAWN_EGG, "Elder Guardian", "Mining fatigue.", "Boss-like."),
      mob("phantom", Material.PHANTOM_SPAWN_EGG, "Phantom", "Night flier.", "Sleepless hunter."),
      mob("drowned", Material.DROWNED_SPAWN_EGG, "Drowned", "Trident user.", "Aquatic undead."),
      mob("husk", Material.HUSK_SPAWN_EGG, "Husk", "Desert zombie.", "Hunger effect."),
      mob("stray", Material.STRAY_SPAWN_EGG, "Stray", "Icy arrows.", "Snow skeleton."),
      mob("magma_cube", Material.MAGMA_CUBE_SPAWN_EGG, "Magma Cube", "Hot slime.", "Nether."),
      mob("pillager", Material.PILLAGER_SPAWN_EGG, "Pillager", "Crossbow.", "Raid mob."),
      mob("vindicator", Material.VINDICATOR_SPAWN_EGG, "Vindicator", "Axe brute.", "Raid mob."),
      mob("evoker", Material.EVOKER_SPAWN_EGG, "Evoker", "Vex + fangs.", "Raid mob."),
      mob("vex", Material.VEX_SPAWN_EGG, "Vex", "Tiny flier.", "Annoying."),
      mob("ravager", Material.RAVAGER_SPAWN_EGG, "Ravager", "Big charge.", "Raid mount."),
      mob("shulker", Material.SHULKER_SPAWN_EGG, "Shulker", "Levitation.", "End city."),
      mob("silverfish", Material.SILVERFISH_SPAWN_EGG, "Silverfish", "Stone swarm.", "Annoying."),
      mob("endermite", Material.ENDERMITE_SPAWN_EGG, "Endermite", "Tiny end bug.", "Enderman bait."),
      mob("cave_spider", Material.CAVE_SPIDER_SPAWN_EGG, "Cave Spider", "Poison bite.", "Mineshaft."),
      mob("zombified_piglin", Material.ZOMBIFIED_PIGLIN_SPAWN_EGG, "Zombified Piglin", "Neutral.", "Anger swarm."),
      mob("wither_skeleton", Material.WITHER_SKELETON_SPAWN_EGG, "Wither Skeleton", "Wither effect.", "Nether fortress."),
      mob("wither", Material.WITHER_SPAWN_EGG, "Wither (Egg)", "Boss (creative egg).", "Demo entry."),
      mob("warden", Material.WARDEN_SPAWN_EGG, "Warden (Egg)", "Do not.", "Demo entry."),
      mob("villager", Material.VILLAGER_SPAWN_EGG, "Villager", "Trades.", "Not hostile."),
      mob("iron_golem", Material.IRON_GOLEM_SPAWN_EGG, "Iron Golem", "Village defender.", "Strong."),
      mob("snow_golem", Material.SNOW_GOLEM_SPAWN_EGG, "Snow Golem", "Throws snowballs.", "Cute."),
      mob("bee", Material.BEE_SPAWN_EGG, "Bee", "Pollinates.", "Angry when provoked."),
      mob("axolotl", Material.AXOLOTL_SPAWN_EGG, "Axolotl", "Aquatic ally.", "Plays dead."),
      mob("allay", Material.ALLAY_SPAWN_EGG, "Allay", "Item helper.", "Music vibes."),
      mob("frog", Material.FROG_SPAWN_EGG, "Frog", "Slime eater.", "Swamp friend."),
      mob("camel", Material.CAMEL_SPAWN_EGG, "Camel", "Desert mount.", "Two riders.")
  );

  private final VirtualList<MobEntry> list;
  private final GuiState<MobEntry> selected = GuiState.of(p -> MOBS.isEmpty() ? null : MOBS.get(0));
  private GuiState.Subscription selectedBinding;

  public ShowcaseMobIndexMenu() {
    super(SIZE, GuiI18n.tr("gui.showcase.mobs.title"), true);

    background(dev.patric.dungeonsreborn.gui.GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        p -> MOBS,
        (p, entry) -> GuiItem.of(entry.icon())
            .displayName(entry.name())
            .lore(entry.lore())
            .hideItemFlags(true)
            .build(),
        (ctx, entry) -> {
          selected.set(ctx.player(), entry);
          GuiSounds.click(ctx.player());
        });
    list.searchKey(entry -> entry.id() + " " + plainName(entry.name()));
    list.emptyStateItem(EmptyState.list());

    list.apply(this, dev.patric.dungeonsreborn.gui.layout.Placement.FIXED);

    navLeft(GuiNav.backButton().autoDescribeInLore(false));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixed(SLOT_FILTER, ListSearchBar.searchButton(list, SLOT_FILTER));
    setFixed(SLOT_CLEAR_FILTER, ListSearchBar.clearButton(list, SLOT_FILTER));

    setFixed(SLOT_PREVIEW, new Label(this::previewItem));
    setFixed(SLOT_PREVIEW_LORE, new Label(this::previewLoreItem));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      if (selectedBinding != null) {
        selectedBinding.close();
        selectedBinding = null;
      }
      GuiSounds.close(ctx.player());
    });
  }

  @Override
  protected void build(Player player) {
    if (selectedBinding == null) {
      selectedBinding = selected.bindRedraw(this, SLOT_PREVIEW, SLOT_PREVIEW_LORE);
    }
  }

  private ItemStack previewItem(Player player) {
    MobEntry entry = selected.get(player);
    if (entry == null) {
      return GuiItem.of(Material.BARRIER).displayName(GuiI18n.tr("gui.showcase.mobs.none")).build();
    }
    return GuiItem.of(entry.icon())
        .displayName(GuiI18n.tr(player, "gui.showcase.mobs.selected.title"))
        .lore(List.of(entry.name(), Component.empty(), GuiI18n.tr(player, "gui.showcase.mobs.selected.hint")))
        .hideItemFlags(true)
        .build();
  }

  private ItemStack previewLoreItem(Player player) {
    MobEntry entry = selected.get(player);
    if (entry == null) {
      return GuiItem.of(Material.PAPER).displayName(GuiI18n.tr("gui.showcase.mobs.details.title"))
          .lore(List.of(GuiI18n.tr("gui.showcase.mobs.details.none"))).build();
    }
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.showcase.mobs.details.id", Placeholder.unparsed("id", entry.id())));
    lore.add(Component.empty());
    lore.addAll(entry.lore());
    lore.add(Component.empty());
    lore.add(GuiI18n.tr(player, "gui.showcase.mobs.details.tip"));
    return GuiItem.of(Material.PAPER).displayName(GuiI18n.tr(player, "gui.showcase.mobs.details.title")).lore(lore).build();
  }

  private static MobEntry mob(String id, Material icon, String name, String... loreLines) {
    List<Component> lore = new ArrayList<>();
    for (String line : loreLines) {
      lore.add(GuiI18n.tr("gui.showcase.mobs.entry.line", Placeholder.unparsed("text", line)));
    }
    lore.add(Component.empty());
    lore.add(GuiI18n.tr("gui.showcase.mobs.entry.select"));
    return new MobEntry(id, icon, GuiI18n.tr("gui.showcase.mobs.entry.title", Placeholder.unparsed("name", name)), lore);
  }

  private static String plainName(Component component) {
    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
  }
}
