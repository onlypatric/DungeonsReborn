package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.ClickType;

import dev.patric.dungeonsreborn.crafting.CraftingInventoryPlanner;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeVariant;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class CraftingDiscoveryMenu extends Window {
  private static final int SIZE = 54;

  private record Entry(CraftingRecipeTemplate recipe, CraftingRecipeVariant variant) {
  }

  private final CraftingYamlRegistry registry;
  private final CraftingTestMenu craftMenu;
  private final VirtualList<Entry> list;

  public CraftingDiscoveryMenu(CraftingYamlRegistry registry, CraftingTestMenu craftMenu) {
    super(SIZE, GuiI18n.tr("gui.crafting.discovery.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.craftMenu = Objects.requireNonNull(craftMenu, "craftMenu");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> {
          boolean craftNow = ctx.isShiftClick() || ctx.clickType() == ClickType.RIGHT;
          if (craftNow) {
            boolean crafted = this.craftMenu.craftFromDiscovery(ctx.player(), entry.recipe(), entry.variant(), false);
            if (!crafted) {
              GuiSounds.error(ctx.player());
              return;
            }
            GuiSounds.success(ctx.player());
            ctx.close();
            return;
          }
          boolean loaded = this.craftMenu.loadRecipeFromInventory(ctx.player(), entry.recipe(), entry.variant());
          if (!loaded) {
            GuiSounds.error(ctx.player());
            return;
          }
          GuiSounds.click(ctx.player());
          ctx.close();
        });
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.BOOK, GuiI18n.tr("gui.crafting.discovery.header.title"), List.of(
        GuiI18n.tr("gui.crafting.discovery.header.hint")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<Entry> entries(Player player) {
    List<Entry> entries = new ArrayList<>();
    ItemStack[] storage = player.getInventory().getStorageContents();
    for (CraftingRecipeTemplate recipe : registry.recipes().values()) {
      CraftingRecipeVariant variant = findBestVariant(storage, recipe);
      if (variant != null) {
        entries.add(new Entry(recipe, variant));
      }
    }
    entries.sort(Comparator.comparing(entry -> entryTitle(entry).toLowerCase(Locale.ROOT)));
    return entries;
  }

  private CraftingRecipeVariant findBestVariant(ItemStack[] storage, CraftingRecipeTemplate recipe) {
    CraftingRecipeSpec spec = recipe.spec();
    CraftingRecipeVariant best = null;
    int bestConsumed = -1;
    int bestSpecificity = -1;
    for (CraftingRecipeVariant variant : spec.variants()) {
      Map<Integer, Integer> plan = CraftingInventoryPlanner.plan(storage, variant);
      if (plan == null) {
        continue;
      }
      int consumed = totalConsumed(plan);
      int specificity = variantSpecificity(variant);
      if (consumed > bestConsumed || (consumed == bestConsumed && specificity > bestSpecificity)) {
        best = variant;
        bestConsumed = consumed;
        bestSpecificity = specificity;
      }
    }
    return best;
  }

  private int totalConsumed(Map<Integer, Integer> plan) {
    int total = 0;
    for (int amount : plan.values()) {
      total += Math.max(0, amount);
    }
    return total;
  }

  private int variantSpecificity(CraftingRecipeVariant variant) {
    int score = 0;
    for (var ingredient : variant.inputs()) {
      int weight = switch (ingredient.type()) {
        case ITEM_ID -> 5;
        case TAG -> 4;
        case MATERIAL -> 3;
        case CATEGORY -> 2;
        case ANY -> 1;
      };
      score += weight * Math.max(1, ingredient.amount());
    }
    return score;
  }

  private ItemStack entryItem(Entry entry) {
    CraftingRecipeTemplate recipe = entry.recipe();
    ItemStack output = recipe.outputTemplate();
    ItemStack base = output != null ? output.clone() : new ItemStack(Material.PAPER);
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr("gui.crafting.discovery.lore.id", Placeholder.unparsed("id", recipe.spec().id())));
    if (!recipe.spec().description().isBlank()) {
      lore.add(GuiI18n.tr("gui.crafting.discovery.lore.description",
          Placeholder.unparsed("text", recipe.spec().description())));
    }
    if (recipe.spec().outputs().size() > 1) {
      lore.add(GuiI18n.tr("gui.crafting.discovery.lore.outputs",
          Placeholder.unparsed("count", String.valueOf(recipe.spec().outputs().size()))));
    }
    lore.add(GuiI18n.tr("gui.crafting.discovery.lore.prepare"));
    lore.add(GuiI18n.tr("gui.crafting.discovery.lore.craftNow"));
    return GuiItem.of(base)
        .displayName(GuiI18n.tr("gui.crafting.discovery.entry.title",
            Placeholder.unparsed("title", entryTitle(entry))))
        .lore(lore)
        .build();
  }

  private String entryTitle(Entry entry) {
    String name = entry.recipe().spec().name();
    if (name == null || name.isBlank()) {
      return entry.recipe().spec().id();
    }
    return name;
  }
}
