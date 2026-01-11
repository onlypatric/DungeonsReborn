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

import dev.patric.dungeonsreborn.crafting.CraftingInventoryPlanner;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeVariant;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class CraftingDiscoveryMenu extends Window {
  private static final int SIZE = 54;

  private record Entry(CraftingRecipeTemplate recipe, CraftingRecipeVariant variant) {
  }

  private final CraftingYamlRegistry registry;
  private final CraftingTestMenu craftMenu;
  private final VirtualList<Entry> list;

  public CraftingDiscoveryMenu(CraftingYamlRegistry registry, CraftingTestMenu craftMenu) {
    super(SIZE, GuiMini.mm("<white><bold>Crafting Discovery</bold></white>"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.craftMenu = Objects.requireNonNull(craftMenu, "craftMenu");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> {
          boolean loaded = craftMenu.loadRecipeFromInventory(ctx.player(), entry.recipe(), entry.variant());
          if (!loaded) {
            GuiSounds.error(ctx.player());
            return;
          }
          GuiSounds.click(ctx.player());
          ctx.close();
        });
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.BOOK, GuiMini.mm("<gold><bold>Craftable Recipes</bold></gold>"), List.of(
        GuiMini.mm("<gray>Click a recipe to auto-fill inputs.</gray>")))));

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
    lore.add(GuiMini.mm("<gray>ID:</gray> <white>" + recipe.spec().id() + "</white>"));
    if (!recipe.spec().description().isBlank()) {
      lore.add(GuiMini.mm("<dark_gray>" + recipe.spec().description() + "</dark_gray>"));
    }
    if (recipe.spec().outputs().size() > 1) {
      lore.add(GuiMini.mm("<gray>Outputs:</gray> <white>" + recipe.spec().outputs().size() + "</white>"));
    }
    lore.add(GuiMini.mm("<green>Click to prepare.</green>"));
    return GuiItem.of(base)
        .displayName(GuiMini.mm("<yellow>" + entryTitle(entry) + "</yellow>"))
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
