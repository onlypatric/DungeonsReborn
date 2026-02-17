package dev.patric.dungeonsreborn.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.classes.ClassSpec;
import dev.patric.dungeonsreborn.classes.ClassYamlRegistry;
import dev.patric.dungeonsreborn.gui.GuiDebug;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiManager;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.CloseButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.ListSearchBar;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiNav;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ClassIndexMenu extends Window {
  private final ClassYamlRegistry classes;
  private final VirtualList<ClassSpec> list;
  private boolean debugLogged;

  public static void open(Player player, ClassYamlRegistry classes) {
    Objects.requireNonNull(player, "player");
    GuiManager.get().open(player, new ClassIndexMenu(classes));
  }

  public ClassIndexMenu(ClassYamlRegistry classes) {
    super(54, GuiI18n.tr("gui.classes.index.title"));
    this.classes = Objects.requireNonNull(classes, "classes");
    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(1, 1, 4, 7,
        player -> classEntries(),
        this::renderEntry,
        (ctx, spec) -> {
        });
    this.list.searchKey(this::classSearchKey);
    this.list.apply(this, Placement.FIXED);

    GuiNav.applyList(this, list, new BackButton(), new CloseButton());
    nav(3, ListSearchBar.searchButton(list));
    nav(4, ListSearchBar.clearButton(list));
    setFixedAt(0, 4, new Label(this::headerItem));
  }

  private List<ClassSpec> classEntries() {
    List<ClassSpec> entries = new ArrayList<>(classes.classes().values());
    debugLogged = GuiDebug.logIndexOnce(debugLogged, "classes", entries.size());
    entries.sort(Comparator.comparing(spec -> classTitleKey(spec).toLowerCase(java.util.Locale.ROOT)));
    return entries;
  }

  private ItemStack renderEntry(Player player, ClassSpec spec) {
    if (spec == null) {
      return GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE);
    }
    Component title = spec.displayName(player);
    List<Component> lore = new ArrayList<>(spec.descriptionFor(player));
    lore.add(GuiI18n.tr(player, spec.enabled() ? "gui.classes.index.entry.enabled"
        : "gui.classes.index.entry.disabled"));
    ItemStack icon = spec.icon();
    if (icon == null) {
      return GuiItems.head("ICON_CLASSES", title, lore);
    }
    return GuiItems.named(icon, title, lore, true);
  }

  private String classSearchKey(ClassSpec spec) {
    if (spec == null) {
      return "";
    }
    return PlainTextComponentSerializer.plainText().serialize(spec.displayName());
  }

  private String classTitleKey(ClassSpec spec) {
    if (spec == null) {
      return "";
    }
    return PlainTextComponentSerializer.plainText().serialize(spec.displayName());
  }

  private ItemStack headerItem(Player player) {
    Component title = GuiI18n.tr(player, "gui.classes.index.header",
        Placeholder.unparsed("count", String.valueOf(classes.classes().size())));
    return GuiItems.head("ICON_CLASSES", title, List.of());
  }
}
