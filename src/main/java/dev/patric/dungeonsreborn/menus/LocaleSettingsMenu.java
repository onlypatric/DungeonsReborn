package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.LocaleService;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class LocaleSettingsMenu extends Window {
  private final LocaleService locales;
  private final VirtualList<String> list;

  public LocaleSettingsMenu(LocaleService locales) {
    super(54, GuiI18n.tr("gui.settings.locale.title"));
    this.locales = Objects.requireNonNull(locales, "locales");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> localesFor(player),
        this::renderLocale,
        this::selectLocale);
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(6, clearButton());
    setFixedAt(0, 4, new Label(this::currentLocaleLabel));
  }

  private List<String> localesFor(Player player) {
    List<String> enabled = new ArrayList<>(locales.enabledLocales());
    enabled.sort(Comparator.naturalOrder());
    return enabled;
  }

  private ItemStack renderLocale(Player player, String locale) {
    boolean current = locale.equalsIgnoreCase(locales.localeFor(player));
    Component title = Component.text(locale.toUpperCase(Locale.ROOT));
    Component status = current
        ? GuiI18n.tr(player, "gui.locale.entry.current")
        : GuiI18n.tr(player, "gui.locale.entry.select");
    String headId = current ? "STATE_SELECTED" : "STATE_UNSELECTED";
    return GuiItems.head(headId, title, List.of(status));
  }

  private void selectLocale(Window.ClickContext ctx, String locale) {
    boolean updated = locales.setPlayerOverride(ctx.player().getUniqueId(), locale);
    if (!updated) {
      ctx.player().sendMessage(Locales.component(ctx.player(), "messages.gui.locale.overrideDisabled"));
      return;
    }
    ctx.player().sendMessage(Locales.component(ctx.player(), "messages.gui.locale.set",
        Locales.placeholders("locale", locale)));
    ctx.window().redraw(ctx.player());
  }

  private Button clearButton() {
    Button button = new Button(player -> GuiButtons.item(GuiButtons.Type.CLEAR,
        GuiI18n.tr(player, "gui.locale.clear.title"),
        List.of(GuiI18n.tr(player, "gui.locale.clear.desc"))));
    button.left(GuiI18n.tr("gui.controls.action"), ctx -> {
      boolean cleared = locales.clearPlayerOverride(ctx.player().getUniqueId());
      if (!cleared) {
        ctx.player().sendMessage(Locales.component(ctx.player(), "messages.gui.locale.overrideDisabled"));
        return;
      }
      ctx.player().sendMessage(Locales.component(ctx.player(), "messages.gui.locale.cleared"));
      ctx.window().redraw(ctx.player());
    });
    button.autoDescribeInLore(false);
    return button;
  }

  private ItemStack currentLocaleLabel(Player player) {
    Component title = GuiI18n.tr(player, "gui.locale.current",
        Placeholder.unparsed("locale", locales.localeFor(player)));
    return GuiItems.head("ICON_LOCALE", title, List.of());
  }
}
