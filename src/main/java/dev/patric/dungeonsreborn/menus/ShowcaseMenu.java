package dev.patric.dungeonsreborn.menus;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.layout.Layouts;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Single entry-point menu showcasing the GUI library.
 */
public final class ShowcaseMenu extends Window {
  private static final int SIZE = 54;
  private static final int SLOT_CLOCK = 4;

  public ShowcaseMenu() {
    super(SIZE, GuiI18n.tr("gui.showcase.title"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, GuiI18n.tr(p, "gui.button.close"))).autoDescribeInLore(false));
    navRight(new Button(GuiButtons.item(GuiButtons.Type.INFO, GuiI18n.tr("gui.showcase.about.button")),
        ctx -> openSubWindow(ctx.player(), new ShowcaseAboutMenu()))
        .autoDescribeInLore(false));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));

    onClickOutside(ctx -> GuiSounds.error(ctx.player()));

    onTick(ctx -> redrawSlot(ctx.player(), SLOT_CLOCK));
    tickEvery(20);

    setFixed(SLOT_CLOCK, new Label(p -> GuiItems.named(Material.CLOCK,
        GuiI18n.tr(p, "gui.showcase.tick", Placeholder.unparsed("value", ctxTickLabel(p))))));
  }

  @Override
  protected void build(Player player) {
    // Header
    setDynamic(0, new Label(GuiItems.named(Material.NETHER_STAR, GuiI18n.tr("gui.showcase.header.title"),
        List.of(GuiI18n.tr("gui.showcase.header.subtitle")))));

    // Main actions
    var grid = Layouts.grid(2, 1, 3, 7);
    grid.set(0, 0, tile("vault", GuiButtons.Type.PRIMARY,
        ctx -> openSubWindow(ctx.player(), new ShowcaseVaultMenu())));
    grid.set(0, 2, tile("mobs", GuiButtons.Type.SECONDARY,
        ctx -> openSubWindow(ctx.player(), new ShowcaseMobIndexMenu())));
    grid.set(0, 4, tile("settings", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), new ShowcaseSettingsMenu())));
    grid.set(0, 6, tile("slots", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), new ShowcaseDraggableSlotsMenu())));

    grid.set(1, 1, tile("confirm", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), ShowcaseAboutMenu.confirmExample())));

    grid.set(1, 3, tile("sounds", GuiButtons.Type.INFO, ctx -> {
      GuiSounds.click(ctx.player());
      ctx.player().sendMessage(GuiI18n.tr(ctx.player(), "gui.showcase.sounds.played"));
    }));

    grid.set(1, 5, tile("wizard", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), ShowcaseWizardMenu.create())));

    grid.apply(this, Placement.DYNAMIC);
  }

  private Button tile(String key, GuiButtons.Type type, java.util.function.Consumer<ClickContext> onClick) {
    return new Button(p -> {
      Component name = type.theme().name(GuiI18n.tr(p, "gui.showcase.tile." + key + ".title"));
      List<Component> lore = List.of(
          type.theme().loreLine(GuiI18n.tr(p, "gui.showcase.tile." + key + ".subtitle")),
          Component.empty(),
          GuiI18n.tr(p, "gui.showcase.tile.action"));
      return dev.patric.dungeonsreborn.gui.GuiItem.of(type.material())
          .displayName(name)
          .lore(lore)
          .build();
    },
        onClick)
            .autoDescribeInLore(false);
  }

  private static String ctxTickLabel(Player player) {
    // This value isn't critical; it's just a visible heartbeat for the window tick hook.
    return Integer.toString(org.bukkit.Bukkit.getCurrentTick());
  }
}
