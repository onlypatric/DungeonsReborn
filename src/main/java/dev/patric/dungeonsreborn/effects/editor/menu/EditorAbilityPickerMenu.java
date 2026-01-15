package dev.patric.dungeonsreborn.effects.editor.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class EditorAbilityPickerMenu extends Window {
  private static final int SIZE = 54;
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private record AbilityEntry(String id, String name, String description) {
  }

  private final EditorServices services;
  private final BiConsumer<Player, String> onPick;
  private final VirtualList<AbilityEntry> list;

  public EditorAbilityPickerMenu(EditorServices services, BiConsumer<Player, String> onPick) {
    super(SIZE, GuiI18n.tr("gui.effects.editor.abilityPicker.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.onPick = Objects.requireNonNull(onPick, "onPick");

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> {
          this.onPick.accept(ctx.player(), entry.id());
          ctx.close();
        });
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.BOOK, GuiI18n.tr("gui.effects.editor.abilityPicker.header.title"), List.of(
        GuiI18n.tr("gui.effects.editor.abilityPicker.header.hint")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private List<AbilityEntry> entries(Player player) {
    EffectsEngine engine = services.engine();
    List<AbilityEntry> entries = new ArrayList<>();
    for (AbilitySpec spec : engine.abilitySpecs().values()) {
      String id = spec.id();
      String name = firstNonBlank(spec.name(), id);
      entries.add(new AbilityEntry(id, name, spec.description()));
    }
    entries.sort(Comparator.comparing((AbilityEntry entry) -> entry.name().toLowerCase(Locale.ROOT)));
    return entries;
  }

  private org.bukkit.inventory.ItemStack entryItem(AbilityEntry entry) {
    List<Component> lore = new ArrayList<>();
    lore.add(GuiI18n.tr("gui.common.line.id", Placeholder.unparsed("value", entry.id())));
    if (entry.description() != null && !entry.description().isBlank()) {
      for (Component line : renderDescription(entry.description())) {
        lore.add(line);
      }
    }
    lore.add(GuiI18n.tr("gui.effects.editor.abilityPicker.entry.select"));
    return GuiItem.of(Material.PAPER)
        .displayName(render(entry.name()))
        .lore(lore)
        .build();
  }

  private static List<Component> renderDescription(String raw) {
    String[] lines = raw.split("\\R", -1);
    List<Component> out = new ArrayList<>();
    int limit = Math.min(lines.length, 3);
    for (int i = 0; i < limit; i++) {
      String line = lines[i];
      if (line == null || line.isBlank()) {
        continue;
      }
      out.add(render(line));
    }
    if (lines.length > limit) {
      out.add(GuiI18n.tr("gui.effects.editor.abilityPicker.entry.more"));
    }
    return out;
  }

  private static Component render(String raw) {
    if (raw == null) {
      return GuiI18n.tr("gui.effects.editor.abilityPicker.entry.unnamed");
    }
    if (raw.indexOf('§') >= 0) {
      return LEGACY.deserialize(raw);
    }
    try {
      return MINI.deserialize(raw);
    } catch (Exception ignored) {
      return LEGACY.deserialize(raw.replace('&', '§'));
    }
  }

  private static String firstNonBlank(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }
}
