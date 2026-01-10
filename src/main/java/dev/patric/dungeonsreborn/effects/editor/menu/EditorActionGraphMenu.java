package dev.patric.dungeonsreborn.effects.editor.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.effects.editor.EditorAbilityDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorActionTree;
import dev.patric.dungeonsreborn.effects.editor.EditorActionType;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.gui.GuiItem;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiMini;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.list.VirtualList;
import dev.patric.dungeonsreborn.gui.flow.OptionPickerWindow;
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class EditorActionGraphMenu extends Window {
  private static final int SIZE = 54;
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private record ActionEntry(int index, Map<String, Object> node, String type, String summary) {
  }

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Map<String, Object> root;
  private final List<Map<String, Object>> actions;
  private final String title;
  private final Runnable onCloseRefresh;
  private final VirtualList<ActionEntry> list;

  public EditorActionGraphMenu(EditorServices services, EditorAbilityDraft draft, Map<String, Object> root,
      List<Map<String, Object>> actions, String title, Runnable onCloseRefresh) {
    super(SIZE, GuiMini.mm("<white><bold>" + title + "</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.root = Objects.requireNonNull(root, "root");
    this.actions = Objects.requireNonNull(actions, "actions");
    this.title = title == null ? "Actions" : title;
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(entry),
        (ctx, entry) -> openDetail(ctx.player(), entry.index()));
    list.searchKey(entry -> entry.type + " " + entry.summary);
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Back"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, addActionButton());
    nav(5, clearActionsButton());

    setFixedAt(0, 4, new Label(GuiItems.named(Material.REPEATER, GuiMini.mm("<gold><bold>" + this.title + "</bold></gold>"), List.of(
        GuiMini.mm("<gray>Sequence editor</gray>")))));
    setFixedAt(0, 7, scriptEditButton());
    setFixedAt(0, 8, scriptClearButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  private List<ActionEntry> entries(Player player) {
    List<ActionEntry> out = new ArrayList<>();
    for (int i = 0; i < actions.size(); i++) {
      Map<String, Object> node = actions.get(i);
      String type = EditorActionTree.typeOf(node);
      out.add(new ActionEntry(i, node, type, summarize(node)));
    }
    return out;
  }

  private org.bukkit.inventory.ItemStack entryItem(ActionEntry entry) {
    EditorActionType type = EditorActionType.fromType(entry.type);
    Material mat = type == null ? Material.GRAY_DYE : type.icon();
    String label = type == null ? entry.type : type.label();
    List<Component> lore = new ArrayList<>();
    lore.add(GuiMini.mm("<gray>Type:</gray> <white>" + label + "</white>"));
    if (!entry.summary.isBlank()) {
      lore.add(renderSummary(entry.summary));
    }
    return GuiItem.of(mat)
        .displayName(GuiMini.mm("<yellow><bold>#" + (entry.index + 1) + "</bold></yellow>"))
        .lore(lore)
        .build();
  }

  private Button addActionButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.PRIMARY, Component.text("Add Action")), ctx -> {
      OptionPickerWindow<EditorActionType> picker = new OptionPickerWindow<>(
          GuiMini.mm("<white><bold>Add Action</bold></white>"),
          List.of(EditorActionType.values()),
          type -> GuiItems.named(type.icon(), Component.text(type.label()), List.of(Component.text(type.hint()))),
          (player, type) -> {
            Map<String, Object> node = type.create();
            actions.add(node);
            saveDraft(player, "action.add" + type.id());
            list.invalidate(player);
            list.redraw(this, player);
          });
      openSubWindow(ctx.player(), picker);
    }).autoDescribeInLore(false);
  }

  private Button clearActionsButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, Component.text("Clear")), ctx -> {
      actions.clear();
      saveDraft(ctx.player(), "action.clear");
      list.invalidate(ctx.player());
      list.redraw(this, ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton scriptEditButton() {
    return new TextButton(
        p -> {
          boolean active = draft.scriptMode() != EditorAbilityDraft.ScriptMode.NONE;
          Material mat = active ? Material.LIME_DYE : Material.GRAY_DYE;
          String status = active ? "active" : "off";
          return GuiItems.named(mat, GuiMini.mm("<aqua><bold>DSL Script</bold></aqua>"), List.of(
              GuiMini.mm("<gray>Status:</gray> <white>" + status + "</white>"),
              GuiMini.mm("<gray>Click to edit.</gray>")));
        },
        GuiMini.mm("<gray>Paste DSL script (blank to clear)</gray>"),
        "cancel",
        java.time.Duration.ofSeconds(45),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          if (text == null || text.isBlank()) {
            draft.clearScript();
          } else {
            draft.setInlineScript(text);
          }
          services.drafts().save(draft);
          services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), "dsl.script"));
          w.redraw(player);
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button scriptClearButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.CANCEL, Component.text("Clear Script")), ctx -> {
      if (draft.scriptMode() == EditorAbilityDraft.ScriptMode.NONE) {
        ctx.player().sendMessage(Component.text("§7No script to clear."));
        return;
      }
      draft.clearScript();
      services.drafts().save(draft);
      services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, ctx.player().getUniqueId(), ctx.player().getName(), draft.id(), "dsl.clear"));
      ctx.window().redraw(ctx.player());
    }).autoDescribeInLore(false);
  }

  private void openDetail(Player player, int index) {
    openSubWindow(player, new EditorActionDetailMenu(services, draft, root, actions, index, this::refreshList));
  }

  private void refreshList() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      list.invalidate(viewer);
      list.redraw(this, viewer);
    }
  }

  private void saveDraft(Player player, String detail) {
    draft.clearScript();
    EditorActionTree.setRoot(draft, root);
    services.drafts().save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
  }

  private static String summarize(Map<String, Object> node) {
    String type = EditorActionTree.typeOf(node);
    return switch (type) {
      case "message", "action_bar" -> "text=" + truncate(String.valueOf(node.get("text")));
      case "sound" -> "sound=" + String.valueOf(node.get("sound"));
      case "delay" -> "ticks=" + String.valueOf(node.get("ticks"));
      case "repeat_ticks" -> "period=" + String.valueOf(node.get("periodTicks")) + ", times=" + String.valueOf(node.get("times"));
      case "particles_point" -> "particle=" + String.valueOf(node.get("particle"));
      case "damage" -> "amount=" + String.valueOf(node.get("amount"));
      case "when" -> "condition=" + summarizeCondition(node.get("condition"));
      case "for_each_target" -> "targeter=" + summarizeTargeter(node.get("targeter"));
      default -> "";
    };
  }

  private static String summarizeCondition(Object raw) {
    Map<String, Object> map = EditorActionTree.mapFrom(raw);
    if (map == null) {
      return "(none)";
    }
    String type = String.valueOf(map.get("type"));
    if ("chance".equalsIgnoreCase(type)) {
      return "chance=" + map.get("chance");
    }
    if ("permission".equalsIgnoreCase(type)) {
      return "perm=" + map.get("permission");
    }
    return type;
  }

  private static String summarizeTargeter(Object raw) {
    Map<String, Object> map = EditorActionTree.mapFrom(raw);
    if (map == null) {
      return "(none)";
    }
    String type = String.valueOf(map.get("type"));
    if ("sphere".equalsIgnoreCase(type) || "nearest".equalsIgnoreCase(type)) {
      return type + "(" + map.get("radius") + ")";
    }
    return type;
  }

  private static String truncate(String text) {
    if (text == null) {
      return "";
    }
    String trimmed = text.replace('\n', ' ').trim();
    if (trimmed.length() <= 28) {
      return trimmed;
    }
    return trimmed.substring(0, 28) + "...";
  }

  private static Component renderSummary(String raw) {
    if (raw == null || raw.isBlank()) {
      return Component.text("");
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
}
