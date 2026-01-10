package dev.patric.dungeonsreborn.mobs.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.mobs.MobRegistry;
import dev.patric.dungeonsreborn.mobs.MobYamlRegistry;
import dev.patric.dungeonsreborn.mobs.editor.MobEditorYaml;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import net.kyori.adventure.text.Component;

public final class MobEditorListMenu extends Window {
  private static final int SIZE = 54;

  private record MobEntry(String id, String name, boolean loaded) {
  }

  private final MobYamlRegistry yaml;
  private final MobRegistry registry;
  private final VirtualList<MobEntry> list;

  public MobEditorListMenu(MobYamlRegistry yaml, MobRegistry registry) {
    super(SIZE, GuiMini.mm("<white><bold>Mob Editor</bold></white>"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.registry = Objects.requireNonNull(registry, "registry");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> openEntry(ctx.player(), entry));
    list.searchKey(entry -> entry.id + " " + entry.name);
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiMini.mm("<red><bold>Close</bold></red>"), List.of())));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(5, refreshButton());
    nav(6, errorsButton());

    setFixedAt(0, 1, header());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.SPAWNER, GuiMini.mm("<gold><bold>Mobs</bold></gold>"), List.of(
        GuiMini.mm("<gray>Click a mob to edit.</gray>"),
        GuiMini.mm("<gray>Loaded mobs show as green.</gray>"))));
  }

  private Button refreshButton() {
    return new Button(p -> GuiItems.named(Material.CLOCK, GuiMini.mm("<yellow><bold>Refresh</bold></yellow>"), List.of(
        GuiMini.mm("<gray>Reload the list.</gray>"))), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button errorsButton() {
    return new Button(p -> GuiItems.named(Material.BOOK, GuiMini.mm("<aqua><bold>Errors</bold></aqua>"), List.of(
        GuiMini.mm("<gray>Show last YAML errors.</gray>"))), ctx -> {
      Player player = ctx.player();
      List<String> errors = yaml.lastErrors();
      if (errors.isEmpty()) {
        player.sendMessage(Component.text("§a[Mobs] No YAML errors."));
        return;
      }
      player.sendMessage(Component.text("§6[Mobs] YAML errors (" + errors.size() + "):"));
      for (String error : errors) {
        player.sendMessage(Component.text("§c- " + error));
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
    return GuiItems.named(material, GuiMini.mm("<white><bold>" + entry.id + "</bold></white>"), List.of(
        GuiMini.mm("<gray>Name:</gray> <white>" + entry.name + "</white>"),
        GuiMini.mm("<gray>Status:</gray> " + (entry.loaded ? "<green>Loaded</green>" : "<red>Missing</red>"))));
  }

  private void openEntry(Player player, MobEntry entry) {
    openSubWindow(player, new MobEditorDetailMenu(yaml, registry, entry.id));
    GuiSounds.click(player);
  }
}
