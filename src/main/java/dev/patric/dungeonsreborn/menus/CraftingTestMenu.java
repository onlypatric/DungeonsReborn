package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.patric.dungeonsreborn.crafting.CraftingDiscoveryService;
import dev.patric.dungeonsreborn.crafting.CraftingInventoryPlanner;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeSpec;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeTemplate;
import dev.patric.dungeonsreborn.crafting.CraftingRecipeVariant;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.item.PreviewCard;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class CraftingTestMenu extends Window {
  private final CraftingDiscoveryService discovery;
  private final CraftingRecipeTemplate template;
  @SuppressWarnings("unused")
  private final boolean previewOnly;

  public CraftingTestMenu(CraftingDiscoveryService discovery, CraftingRecipeTemplate template) {
    this(discovery, template, false);
  }

  public CraftingTestMenu(CraftingDiscoveryService discovery, CraftingRecipeTemplate template, boolean previewOnly) {
    super(54, GuiI18n.tr("gui.crafting.test.title",
        Placeholder.unparsed("recipe", template == null ? "" : template.spec().name())));
    this.discovery = Objects.requireNonNull(discovery, "discovery");
    this.template = Objects.requireNonNull(template, "template");
    this.previewOnly = previewOnly;
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    GuiNav.applyDetail(this, new BackButton(), new CloseButton());

    setFixedAt(0, 4, PreviewCard.head("ICON_CRAFTING",
        player -> GuiI18n.tr(player, "gui.crafting.test.header",
            Placeholder.unparsed("recipe", template.spec().name())),
        player -> List.of()));
    setFixedAt(2, 4, new Label(this::outputItem));
    setFixedAt(3, 4, PreviewCard.head("ICON_CRAFTING",
        player -> GuiI18n.tr(player, "gui.crafting.test.info"),
        this::infoLore));
    if (!previewOnly) {
      setFixedAt(rows() - 1, 5, testButton());
    }
  }

  private ItemStack outputItem(Player player) {
    ItemStack output = template.outputTemplate();
    if (output == null) {
      return GuiItems.head("ICON_CRAFTING", GuiI18n.tr(player, "gui.crafting.test.output"), List.of());
    }
    ItemMeta meta = output.getItemMeta();
    if (meta != null) {
      output.setItemMeta(meta);
    }
    return GuiItems.named(output, GuiI18n.tr(player, "gui.crafting.test.output"), List.of(), true);
  }

  private List<Component> infoLore(Player player) {
    CraftingRecipeSpec spec = template.spec();
    List<Component> lore = new ArrayList<>();
    if (!spec.description().isBlank()) {
      lore.add(GuiMini.mm(spec.description()));
    }
    lore.add(GuiI18n.tr(player, "gui.crafting.test.requirements",
        Placeholder.unparsed("count", String.valueOf(spec.requirements().size()))));
    lore.add(GuiI18n.tr(player, "gui.crafting.test.costs",
        Placeholder.unparsed("count", String.valueOf(spec.costs().size()))));
    boolean available = discovery.isAvailable(player, spec);
    Component status = available
        ? GuiI18n.tr(player, "gui.crafting.status.available")
        : GuiI18n.tr(player, "gui.crafting.status.locked");
    lore.add(status);
    return lore;
  }

  private Button testButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CONFIRM,
        GuiI18n.tr(player, "gui.crafting.test.button.title"),
        List.of(GuiI18n.tr(player, "gui.crafting.test.button.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      Player player = ctx.player();
      CraftingRecipeSpec spec = template.spec();
      if (!discovery.isAvailable(player, spec)) {
        player.sendMessage(Locales.component(player, "messages.gui.crafting.test.locked"));
        return;
      }
      CraftingRecipeVariant variant = spec.variants().isEmpty() ? null : spec.variants().get(0);
      if (variant == null) {
        player.sendMessage(Locales.component(player, "messages.gui.crafting.test.invalid"));
        return;
      }
      var plan = CraftingInventoryPlanner.plan(player.getInventory().getContents(), variant);
      if (plan == null) {
        player.sendMessage(Locales.component(player, "messages.gui.crafting.test.missing"));
      } else {
        player.sendMessage(Locales.component(player, "messages.gui.crafting.test.ready"));
      }
    });
    button.autoDescribeInLore(false);
    return button;
  }
}
