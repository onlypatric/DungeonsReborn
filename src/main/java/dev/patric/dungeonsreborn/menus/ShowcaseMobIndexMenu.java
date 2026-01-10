package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.state.GuiState;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

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
    super(SIZE, GuiMini.mm("<white><bold>Mob Index</bold></white>"), true);

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

    list.apply(this, dev.patric.dungeonsreborn.gui.layout.Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))).autoDescribeInLore(false));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixed(SLOT_FILTER, filterButton());
    setFixed(SLOT_CLEAR_FILTER, clearFilterButton());

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

  private TextButton filterButton() {
    return new TextButton(
        p -> {
          String q = list.query(p);
          List<Component> lore = new ArrayList<>();
          lore.add(GuiMini.mm("<gray>Set a search query for the list.</gray>"));
          if (q != null && !q.isBlank()) {
            lore.add(Component.text("Current: " + q));
          } else {
            lore.add(Component.text("Current: (none)"));
          }
          return GuiItem.of(Material.NAME_TAG)
              .displayName(GuiMini.mm("<aqua><bold>Filter</bold></aqua>"))
              .lore(lore)
              .build();
        },
        GuiMini.mm("<gray>Type a filter query (or 'cancel')</gray>"),
        "cancel",
        java.time.Duration.ofSeconds(30),
        (w, text) -> {
          Player viewer = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (viewer == null) {
            return;
          }
          list.query(viewer, text);
          list.redraw(w, viewer);
          w.redrawSlot(viewer, SLOT_FILTER);
        },
        true)
            .inputMode(TextButton.InputMode.ANVIL)
            .anvilTitle(GuiMini.mm("<white><bold>Search</bold></white>"))
            .initialText(p -> Objects.requireNonNullElse(list.query(p), ""));
  }

  private Button clearFilterButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.CANCEL, Component.text("Clear")), ctx -> {
      list.clearFilter(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      ctx.window().redrawSlot(ctx.player(), SLOT_FILTER);
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private ItemStack previewItem(Player player) {
    MobEntry entry = selected.get(player);
    if (entry == null) {
      return GuiItem.of(Material.BARRIER).displayName(Component.text("No selection")).build();
    }
    return GuiItem.of(entry.icon())
        .displayName(GuiMini.mm("<yellow><bold>Selected</bold></yellow>"))
        .lore(List.of(entry.name(), Component.empty(), GuiMini.mm("<gray>Click list entries to change.</gray>")))
        .hideItemFlags(true)
        .build();
  }

  private ItemStack previewLoreItem(Player player) {
    MobEntry entry = selected.get(player);
    if (entry == null) {
      return GuiItem.of(Material.PAPER).displayName(Component.text("Details")).lore(List.of(Component.text("N/A"))).build();
    }
    List<Component> lore = new ArrayList<>();
    lore.add(GuiMini.mm("<gray>ID:</gray> <white>" + entry.id() + "</white>"));
    lore.add(Component.empty());
    lore.addAll(entry.lore());
    lore.add(Component.empty());
    lore.add(GuiMini.mm("<dark_gray>Tip: use the Filter button.</dark_gray>"));
    return GuiItem.of(Material.PAPER).displayName(Component.text("Details")).lore(lore).build();
  }

  private static MobEntry mob(String id, Material icon, String name, String... loreLines) {
    List<Component> lore = new ArrayList<>();
    for (String line : loreLines) {
      lore.add(GuiMini.mm("<gray>" + line + "</gray>"));
    }
    lore.add(Component.empty());
    lore.add(GuiMini.mm("<dark_gray>Left-click to select</dark_gray>"));
    return new MobEntry(id, icon, GuiMini.mm("<gold><bold>" + name + "</bold></gold>"), lore);
  }

  private static String plainName(Component component) {
    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
  }
}
