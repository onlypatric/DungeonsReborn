package dev.patric.dungeonsreborn.mobs.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.MobEditorYaml;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;

public final class MobEditorListMenu extends Window {
  private static final int SIZE = 54;

  private record MobEntry(String id, String name, boolean loaded) {
  }

  private final MobYamlRegistry yaml;
  private final MobRegistry registry;
  private final VirtualList<MobEntry> list;

  public MobEditorListMenu(MobYamlRegistry yaml, MobRegistry registry) {
    super(SIZE, GuiI18n.tr("gui.mobs.editor.list.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.registry = Objects.requireNonNull(registry, "registry");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> openEntry(ctx.player(), entry));
    list.searchKey(entry -> entry.id + " " + entry.name);
    list.emptyStateItem(EmptyState.list());
    list.apply(this, Placement.FIXED);

    navLeft(GuiNav.closeButton());
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    nav(5, refreshButton());
    nav(6, errorsButton());

    setFixedAt(0, 1, header());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.SPAWNER, GuiI18n.tr(p, "gui.mobs.editor.list.header.title"), List.of(
        GuiI18n.tr(p, "gui.mobs.editor.list.header.hint1"),
        GuiI18n.tr(p, "gui.mobs.editor.list.header.hint2"))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.mobs.editor.list.refresh.title"), List.of(
        GuiI18n.tr(p, "gui.mobs.editor.list.refresh.hint"))), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button errorsButton() {
    return new Button(p -> GuiItems.named(Material.BOOK, GuiI18n.tr(p, "gui.mobs.editor.list.errors.title"), List.of(
        GuiI18n.tr(p, "gui.mobs.editor.list.errors.hint"))), ctx -> {
      Player player = ctx.player();
      List<String> errors = yaml.lastErrors();
      if (errors.isEmpty()) {
        player.sendMessage(Locales.component(player, "messages.mobs.yaml.none"));
        return;
      }
      player.sendMessage(Locales.component(player, "messages.mobs.yaml.header",
          Locales.placeholders("count", String.valueOf(errors.size()))));
      for (String error : errors) {
        player.sendMessage(Locales.component(player, "messages.mobs.yaml.entry",
            Locales.placeholders("message", error)));
      }
    }).autoDescribeInLore(false);
  }

  private List<MobEntry> entries(Player player) {
    List<String> ids = MobEditorYaml.mobIds(yaml.file());
    List<MobEntry> out = new ArrayList<>();
    for (String id : ids) {
      String name = MobEditorYaml.name(yaml.file(), id);
      boolean loaded = registry.has(id);
      out.add(new MobEntry(id, name == null ? id : name, loaded));
    }
    return out;
  }

  private org.bukkit.inventory.ItemStack entryItem(MobEntry entry) {
    Material material = entry.loaded ? Material.LIME_DYE : Material.RED_DYE;
    String status = Locales.text(null, entry.loaded ? "gui.mobs.editor.list.status.loaded" : "gui.mobs.editor.list.status.missing");
    return GuiItems.named(material,
        Locales.component(null, "gui.mobs.editor.list.entry.title", Locales.placeholders("id", entry.id)),
        List.of(
            Locales.component(null, "gui.mobs.editor.list.entry.name", Locales.placeholders("name", entry.name)),
            Locales.component(null, "gui.common.line.status", Locales.placeholders("value", status))));
  }

  private void openEntry(Player player, MobEntry entry) {
    openSubWindow(player, new MobEditorDetailMenu(yaml, registry, entry.id));
    GuiSounds.click(player);
  }
}
