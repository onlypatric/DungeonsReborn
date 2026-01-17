package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.crafting.CraftingIngredientSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeVariant;
import dev.patric.dungeonsreborn.crafting.CraftingYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class CraftableDetailMenu extends Window {
  private static final int SIZE = 27;
  private static final int SLOT_HEADER = 4;
  private static final int SLOT_OUTPUT = 13;
  private static final int SLOT_INGREDIENTS = 11;

  private final CraftingYamlRegistry registry;
  private final CraftingRecipeTemplate template;

  public CraftableDetailMenu(CraftingYamlRegistry registry, CraftingRecipeTemplate template) {
    super(SIZE, GuiI18n.tr("gui.craftables.detail.title"), true);
    this.registry = Objects.requireNonNull(registry, "registry");
    this.template = Objects.requireNonNull(template, "template");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(GuiNav.backButton().autoDescribeInLore(false));

    setFixed(SLOT_HEADER, new Label(this::headerItem));
    setFixed(SLOT_OUTPUT, new Label(this::outputItem));
    setFixed(SLOT_INGREDIENTS, new Label(this::ingredientsItem));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private ItemStack headerItem(Player player) {
    CraftingRecipeSpec spec = template.spec();
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr(player, "gui.craftables.detail.header.hint"));
    lore.add(GuiI18n.tr(player, "gui.craftables.detail.header.id",
        Placeholder.unparsed("id", spec.id())));
    int variants = spec.variants().size();
    if (variants > 1) {
      lore.add(GuiI18n.tr(player, "gui.craftables.detail.variants",
          Placeholder.unparsed("count", String.valueOf(variants))));
    }
    return GuiItems.named(Material.CRAFTING_TABLE, GuiI18n.tr(player, "gui.craftables.detail.header.title"), lore);
  }

  private ItemStack outputItem(Player player) {
    ItemStack output = template.outputTemplate();
    ItemStack item = output == null ? new ItemStack(Material.PAPER) : output.clone();
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    List<Component> lore = meta.lore();
    if (lore == null) {
      lore = new ArrayList<>();
    } else {
      lore = new ArrayList<>(lore);
    }
    lore.add(Component.empty());
    lore.add(GuiI18n.tr(player, "gui.craftables.detail.output.hint"));
    meta.lore(lore);
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack ingredientsItem(Player player) {
    CraftingRecipeSpec spec = template.spec();
    List<Component> lore = new ArrayList<>();
    if (!spec.description().isBlank()) {
      lore.add(GuiI18n.tr(player, "gui.craftables.detail.description",
          Placeholder.unparsed("text", spec.description())));
    }
    CraftingRecipeVariant variant = spec.variants().isEmpty() ? null : spec.variants().get(0);
    if (variant == null || variant.inputs().isEmpty()) {
      lore.add(GuiI18n.tr(player, "gui.craftables.detail.ingredients.none"));
      return GuiItems.named(Material.BOOK, GuiI18n.tr(player, "gui.craftables.detail.ingredients.title"), lore);
    }
    if (!lore.isEmpty()) {
      lore.add(Component.empty());
    }
    lore.add(GuiI18n.tr(player, "gui.craftables.detail.ingredients.hint"));
    for (CraftingIngredientSpec ingredient : variant.inputs()) {
      String label = ingredientLabel(ingredient);
      lore.add(GuiI18n.tr(player, "gui.craftables.detail.ingredients.line",
          Placeholder.unparsed("amount", String.valueOf(ingredient.amount())),
          Placeholder.unparsed("item", label)));
    }
    return GuiItems.named(Material.BOOK, GuiI18n.tr(player, "gui.craftables.detail.ingredients.title"), lore);
  }

  private String ingredientLabel(CraftingIngredientSpec ingredient) {
    if (ingredient == null) {
      return "unknown";
    }
    return switch (ingredient.type()) {
      case ITEM_ID -> itemIdLabel(ingredient.itemId());
      case TAG -> ingredient.tag() == null ? "tag" : "tag:" + ingredient.tag().asString();
      case MATERIAL -> ingredient.material() == null
          ? "material"
          : ingredient.material().name().toLowerCase(Locale.ROOT).replace('_', ' ');
      case CATEGORY -> ingredient.category().name().toLowerCase(Locale.ROOT).replace('_', ' ');
      case ANY -> "any item";
    };
  }

  private String itemIdLabel(String itemId) {
    if (itemId == null || itemId.isBlank()) {
      return "item";
    }
    ItemStack resolved = registry.resolveItemTemplate(itemId);
    if (resolved != null && resolved.hasItemMeta()) {
      ItemMeta meta = resolved.getItemMeta();
      if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
        return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
      }
    }
    return itemId;
  }
}
