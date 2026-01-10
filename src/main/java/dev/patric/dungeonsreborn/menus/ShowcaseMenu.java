package dev.patric.dungeonsreborn.menus;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.layout.Layouts;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

/**
 * Single entry-point menu showcasing the GUI library.
 */
public final class ShowcaseMenu extends Window {
  private static final int SIZE = 54;
  private static final int SLOT_CLOCK = 4;

  public ShowcaseMenu() {
    super(SIZE, GuiMini.mm("<gradient:#5fa8ff:#2ee59d><bold>DungeonsReborn GUI</bold></gradient>"), true);

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    navLeft(new CloseButton(p -> GuiButtons.item(GuiButtons.Type.CLOSE, Component.text("Close"))).autoDescribeInLore(false));
    navRight(new Button(GuiButtons.item(GuiButtons.Type.INFO, Component.text("About")), ctx -> openSubWindow(ctx.player(), new ShowcaseAboutMenu()))
        .autoDescribeInLore(false));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));

    onClickOutside(ctx -> GuiSounds.error(ctx.player()));

    onTick(ctx -> redrawSlot(ctx.player(), SLOT_CLOCK));
    tickEvery(20);

    setFixed(SLOT_CLOCK, new Label(p -> GuiItems.named(Material.CLOCK, Component.text("Tick: " + ctxTickLabel(p)))));
  }

  @Override
  protected void build(Player player) {
    // Header
    setDynamic(0, new Label(GuiItems.named(Material.NETHER_STAR, GuiMini.mm("<white><bold>Showcase</bold></white>"),
        List.of(GuiMini.mm("<gray>Everything in this screen uses the new component-based GUI library.</gray>")))));

    // Main actions
    var grid = Layouts.grid(2, 1, 3, 7);
    grid.set(0, 0, tile("Vault", "Multi-slot storage (rules + vanilla slots)", GuiButtons.Type.PRIMARY,
        ctx -> openSubWindow(ctx.player(), new ShowcaseVaultMenu())));
    grid.set(0, 2, tile("Mob Index", "Virtual list (paging + filtering + preview)", GuiButtons.Type.SECONDARY,
        ctx -> openSubWindow(ctx.player(), new ShowcaseMobIndexMenu())));
    grid.set(0, 4, tile("Settings", "Inputs + text modes + item picker", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), new ShowcaseSettingsMenu())));
    grid.set(0, 6, tile("Slots", "Draggable slots (vanilla + rules)", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), new ShowcaseDraggableSlotsMenu())));

    grid.set(1, 1, tile("Confirm Dialog", "Modal flow example", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), ShowcaseAboutMenu.confirmExample())));

    grid.set(1, 3, tile("Sounds", "Click for sound feedback", GuiButtons.Type.INFO, ctx -> {
      GuiSounds.click(ctx.player());
      ctx.player().sendMessage(GuiMini.mm("<gray>Played a click sound.</gray>"));
    }));

    grid.set(1, 5, tile("Wizard", "Multi-step flow (form)", GuiButtons.Type.INFO,
        ctx -> openSubWindow(ctx.player(), ShowcaseWizardMenu.create())));

    grid.apply(this, Placement.DYNAMIC);
  }

  private Button tile(String title, String subtitle, GuiButtons.Type type, java.util.function.Consumer<ClickContext> onClick) {
    return new Button(p -> {
      Component name = type.theme().name(Component.text(title));
      List<Component> lore = List.of(
          type.theme().loreLine(Component.text(subtitle)),
          Component.empty(),
          GuiMini.mm("<dark_gray>Click to open</dark_gray>"));
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
