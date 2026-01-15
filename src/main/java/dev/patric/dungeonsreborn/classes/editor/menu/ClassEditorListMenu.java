package dev.patric.dungeonsreborn.classes.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.classes.editor.ClassEditorYaml;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.EmptyState;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.time.Duration;

public final class ClassEditorListMenu extends Window {
  private static final int SIZE = 54;
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  private record ClassEntry(String id, String name, boolean loaded, boolean enabled) {
  }

  private final ClassYamlRegistry yaml;
  private final VirtualList<ClassEntry> list;

  public ClassEditorListMenu(ClassYamlRegistry yaml) {
    super(SIZE, GuiI18n.tr("gui.classes.editor.list.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");

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
    nav(5, createButton());
    nav(6, refreshButton());

    setFixedAt(0, 1, header());
    setFixedAt(0, 6, batchButton());
    setFixedAt(0, 8, errorsButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.BOOK, GuiI18n.tr(p, "gui.classes.editor.list.header.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.list.header.hint1"))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiI18n.tr(p, "gui.classes.editor.list.refresh.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.list.refresh.hint"))), ctx -> {
      yaml.reload();
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button errorsButton() {
    return new Button(p -> GuiItems.named(Material.BOOK, GuiI18n.tr(p, "gui.classes.editor.list.errors.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.list.errors.hint"))), ctx -> {
      Player player = ctx.player();
      List<String> errors = yaml.lastErrors();
      if (errors.isEmpty()) {
        player.sendMessage(Locales.component(player, "messages.classes.yaml.none"));
        return;
      }
      player.sendMessage(Locales.component(player, "messages.classes.yaml.header",
          Locales.placeholders("count", String.valueOf(errors.size()))));
      for (String error : errors) {
        player.sendMessage(Locales.component(player, "messages.classes.yaml.entry",
            Locales.placeholders("message", error)));
      }
    }).autoDescribeInLore(false);
  }

  private Button batchButton() {
    return new Button(p -> GuiItems.named(Material.COMPARATOR, GuiI18n.tr(p, "gui.classes.editor.list.batch.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.list.batch.hint"))), ctx -> {
      openSubWindow(ctx.player(), new ClassBatchMenu(yaml, this::refreshAfterChild));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton createButton() {
    TextButton button = new TextButton(p -> GuiItems.named(Material.ANVIL, GuiI18n.tr(p, "gui.classes.editor.list.create.title"), List.of(
        GuiI18n.tr(p, "gui.classes.editor.list.create.hint"))),
        GuiI18n.tr("gui.classes.editor.list.create.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          String normalized;
          try {
            normalized = Ids.normalize(text);
          } catch (IllegalArgumentException ex) {
            Player player = viewerPlayer(window);
            if (player != null) {
              player.sendMessage(Locales.component(player, "messages.classes.editor.invalidId",
                  Locales.placeholders("message", ex.getMessage())));
            }
            return;
          }
          ClassEditorYaml.createClass(yaml.file(), normalized);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            list.invalidate(player);
            list.redraw(window, player);
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private List<ClassEntry> entries(Player player) {
    List<String> ids = ClassEditorYaml.classIds(yaml.file());
    List<ClassEntry> out = new ArrayList<>();
    for (String id : ids) {
      ClassSpec spec = yaml.classSpec(id);
      String name = spec == null ? id : PLAIN.serialize(spec.displayName());
      boolean loaded = spec != null;
      boolean enabled = spec != null && spec.enabled();
      out.add(new ClassEntry(id, name == null ? id : name, loaded, enabled));
    }
    return out;
  }

  private org.bukkit.inventory.ItemStack entryItem(ClassEntry entry) {
    Material material = entry.loaded ? Material.LIME_DYE : Material.RED_DYE;
    String status = Locales.text(null, entry.loaded ? "gui.classes.editor.list.status.loaded" : "gui.classes.editor.list.status.missing");
    String enabled = Locales.text(null, entry.enabled ? "messages.common.yes" : "messages.common.no");
    return GuiItems.named(material,
        Locales.component(null, "gui.classes.editor.list.entry.title", Locales.placeholders("id", entry.id)),
        List.of(
            Locales.component(null, "gui.classes.editor.list.entry.name", Locales.placeholders("name", entry.name)),
            Locales.component(null, "gui.common.line.status", Locales.placeholders("value", status)),
            Locales.component(null, "gui.classes.editor.list.entry.enabled", Locales.placeholders("value", enabled))));
  }

  private void openEntry(Player player, ClassEntry entry) {
    openSubWindow(player, new ClassEditorDetailMenu(yaml, entry.id, this::refreshAfterChild));
    GuiSounds.click(player);
  }

  private void refreshAfterChild() {
    Player player = viewerPlayer(this);
    if (player == null) {
      return;
    }
    yaml.reload();
    list.invalidate(player);
    list.redraw(this, player);
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }
}
