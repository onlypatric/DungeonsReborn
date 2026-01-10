package dev.patric.dungeonsreborn.effects.editor.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.editor.EditorAbilityDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityYaml;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;

public final class EditorBindingsMenu extends Window {
  private static final int SIZE = 54;

  private record BindingEntry(int index, Map<String, Object> trigger, String click, String matcher, boolean conflict) {
  }

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Runnable onCloseRefresh;
  private final VirtualList<BindingEntry> list;

  public EditorBindingsMenu(EditorServices services, EditorAbilityDraft draft, Runnable onCloseRefresh) {
    super(SIZE, GuiMini.mm("<white><bold>Bindings</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> openBinding(ctx.player(), entry.index()));
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, addButton(InteractTrigger.RIGHT_CLICK));
    nav(5, addButton(InteractTrigger.LEFT_CLICK));

    setFixedAt(0, 4, new Label(GuiItems.named(Material.TRIPWIRE_HOOK, GuiMini.mm("<gold><bold>Bindings</bold></gold>"), List.of(
        GuiMini.mm("<gray>Configure right/left click triggers.</gray>")))));

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private List<BindingEntry> entries(Player player) {
    List<Map<String, Object>> triggers = EditorAbilityYaml.triggers(draft);
    Map<String, Integer> counts = new HashMap<>();
    List<BindingEntry> entries = new ArrayList<>();

    for (int i = 0; i < triggers.size(); i++) {
      Map<String, Object> trigger = triggers.get(i);
      String click = clickLabel(trigger);
      String matcher = matcherSummary(trigger);
      String signature = click.toLowerCase(Locale.ROOT) + "|" + matcher.toLowerCase(Locale.ROOT);
      counts.merge(signature, 1, (current, add) -> current + add);
      entries.add(new BindingEntry(i, trigger, click, matcher, false));
    }

    for (int i = 0; i < entries.size(); i++) {
      BindingEntry entry = entries.get(i);
      String signature = entry.click().toLowerCase(Locale.ROOT) + "|" + entry.matcher().toLowerCase(Locale.ROOT);
      boolean conflict = counts.getOrDefault(signature, 0) > 1;
      entries.set(i, new BindingEntry(entry.index(), entry.trigger(), entry.click(), entry.matcher(), conflict));
    }

    return entries;
  }

  private org.bukkit.inventory.ItemStack entryItem(BindingEntry entry) {
    Material mat = "LEFT_CLICK".equalsIgnoreCase(entry.click()) ? Material.RED_DYE : Material.LIME_DYE;
    List<Component> lore = new ArrayList<>();
    lore.add(GuiMini.mm("<gray>Click:</gray> <white>" + entry.click() + "</white>"));
    lore.add(GuiMini.mm("<gray>Matcher:</gray> <white>" + entry.matcher() + "</white>"));
    Object raw = entry.trigger().get("permission");
    if (raw != null && !raw.toString().isBlank()) {
      lore.add(GuiMini.mm("<gray>Perm:</gray> <white>" + raw + "</white>"));
    }
    if (Boolean.TRUE.equals(entry.trigger().get("requireSneaking"))) {
      lore.add(GuiMini.mm("<gray>Requires sneaking</gray>"));
    }
    if (Boolean.FALSE.equals(entry.trigger().get("cancelEvent"))) {
      lore.add(GuiMini.mm("<gray>Does not cancel event</gray>"));
    }
    if (entry.conflict()) {
      lore.add(GuiMini.mm("<red>Potential conflict</red>"));
    }
    return GuiItem.of(mat)
        .displayName(GuiMini.mm("<yellow><bold>Binding #" + (entry.index() + 1) + "</bold></yellow>"))
        .lore(lore)
        .build();
  }

  private void openBinding(Player player, int index) {
    openSubWindow(player, new EditorBindingDetailMenu(services, draft, index, this::refreshList));
  }

  private Button addButton(InteractTrigger trigger) {
    String label = trigger == InteractTrigger.LEFT_CLICK ? "Add Left" : "Add Right";
    Material mat = trigger == InteractTrigger.LEFT_CLICK ? Material.RED_WOOL : Material.LIME_WOOL;
    return new Button(p -> GuiButtons.item(GuiButtons.Type.PRIMARY, Component.text(label)), ctx -> {
      List<Map<String, Object>> triggers = EditorAbilityYaml.triggers(draft);
      Map<String, Object> entry = new java.util.LinkedHashMap<>();
      entry.put("type", "interact");
      entry.put("click", trigger.name());
      entry.put("cancelEvent", true);
      entry.put("requireSneaking", false);
      Map<String, Object> matcher = new java.util.LinkedHashMap<>();
      matcher.put("type", "any_non_air");
      entry.put("item", matcher);
      triggers.add(entry);
      EditorAbilityYaml.writeTriggers(draft, triggers);
      saveDraft(ctx.player(), "triggers.add");
      refreshList();
      openBinding(ctx.player(), triggers.size() - 1);
    }) {
      @Override
      public org.bukkit.inventory.ItemStack render(Player player) {
        return GuiItems.named(mat, Component.text(label), List.of(Component.text("Create a " + trigger.name() + " binding")));
      }
    };
  }

  private void refreshList() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      list.invalidate(viewer);
      list.redraw(this, viewer);
    }
  }

  private void saveDraft(Player player, String detail) {
    services.drafts().save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
  }

  private static String clickLabel(Map<String, Object> trigger) {
    Object raw = trigger.get("click");
    if (raw == null) {
      raw = trigger.get("trigger");
    }
    String value = raw == null ? "RIGHT_CLICK" : raw.toString();
    String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "LEFT", "LEFT_CLICK", "LEFTCLICK" -> "LEFT_CLICK";
      default -> "RIGHT_CLICK";
    };
  }

  private static String matcherSummary(Map<String, Object> trigger) {
    Object raw = trigger.get("item");
    if (!(raw instanceof Map<?, ?> map)) {
      return "any_non_air";
    }
    Object typeRaw = map.get("type");
    String type = typeRaw == null ? "any_non_air" : typeRaw.toString().toLowerCase(Locale.ROOT);
    return switch (type) {
      case "material" -> "material:" + String.valueOf(map.get("material"));
      case "custom_model_data", "custom-model-data", "cmd" -> "cmd:" + String.valueOf(valueOf(map, "value", "cmd", "customModelData"));
      case "pdc_tag", "pdc-tag", "tag" -> "pdc:" + String.valueOf(map.get("key"));
      case "lore_contains", "lore-contains" -> "lore:" + String.valueOf(map.get("text"));
      default -> type;
    };
  }

  private static Object valueOf(Map<?, ?> map, String... keys) {
    for (String key : keys) {
      if (map.containsKey(key)) {
        return map.get(key);
      }
    }
    return null;
  }
}
