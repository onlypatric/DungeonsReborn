package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import dev.patric.dungeonsreborn.gui.GuiComponent;
import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.gui.components.input.CycleSelector;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import dev.patric.dungeonsreborn.locale.Locales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class EditorActionDetailMenu extends Window {
  private static final int SIZE = 54;
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private enum ConditionType {
    ALWAYS("always", "Always"),
    SNEAKING("sneaking", "Sneaking"),
    PERMISSION("permission", "Permission"),
    CHANCE("chance", "Chance");

    private final String id;
    private final String label;

    ConditionType(String id, String label) {
      this.id = id;
      this.label = label;
    }
  }

  private enum TargeterType {
    SELF("self", "Self"),
    SPHERE("sphere", "Sphere"),
    NEAREST("nearest", "Nearest"),
    CONE("cone", "Cone"),
    BOX("box", "Box"),
    CYLINDER("cylinder", "Cylinder");

    private final String id;
    private final String label;

    TargeterType(String id, String label) {
      this.id = id;
      this.label = label;
    }
  }

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final Map<String, Object> root;
  private final List<Map<String, Object>> actions;
  private final int index;
  private final Runnable onCloseRefresh;

  public EditorActionDetailMenu(EditorServices services, EditorAbilityDraft draft, Map<String, Object> root,
      List<Map<String, Object>> actions, int index, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.effects.editor.actionDetail.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.root = Objects.requireNonNull(root, "root");
    this.actions = Objects.requireNonNull(actions, "actions");
    this.index = index;
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.BOOK,
        GuiI18n.tr(p, "gui.effects.editor.actionDetail.header.title",
            Placeholder.unparsed("index", String.valueOf(index + 1))))));
    setFixedAt(1, 3, moveUpButton());
    setFixedAt(1, 5, moveDownButton());
    setFixedAt(1, 7, deleteButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  @Override
  protected void build(Player player) {
    if (index < 0 || index >= actions.size()) {
      return;
    }
    Map<String, Object> node = actions.get(index);
    String typeId = EditorActionTree.typeOf(node);
    EditorActionType type = EditorActionType.fromType(typeId);

    setDynamicAt(1, 1, typeLabel(typeId, type));

    if (type != null && type.supportsChildren()) {
      setDynamicAt(2, 1, editChildrenButton(type));
    }

    if ("when".equals(typeId)) {
      setDynamicAt(2, 3, editOtherwiseButton());
    }

    buildParams(player, node, typeId);
  }

  private GuiComponent typeLabel(String typeId, EditorActionType type) {
    String label = type == null ? typeId : type.label();
    Material mat = type == null ? Material.GRAY_DYE : type.icon();
    return new Label(GuiItems.named(mat, GuiI18n.tr("gui.effects.editor.actionDetail.type.title"), List.of(
        GuiI18n.tr(GuiI18n.defaultLocale(), "gui.effects.editor.actionDetail.type.value",
            Placeholder.unparsed("value", label)))));
  }

  private Button moveUpButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.SECONDARY, GuiI18n.tr(p, "gui.effects.editor.actionDetail.move.up")), ctx -> {
      if (index <= 0 || index >= actions.size()) {
        return;
      }
      Map<String, Object> node = actions.remove(index);
      actions.add(index - 1, node);
      saveDraft(ctx.player(), "action.move_up");
      ctx.close();
    }).autoDescribeInLore(false);
  }

  private Button moveDownButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.SECONDARY, GuiI18n.tr(p, "gui.effects.editor.actionDetail.move.down")), ctx -> {
      if (index < 0 || index >= actions.size() - 1) {
        return;
      }
      Map<String, Object> node = actions.remove(index);
      actions.add(index + 1, node);
      saveDraft(ctx.player(), "action.move_down");
      ctx.close();
    }).autoDescribeInLore(false);
  }

  private Button deleteButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, GuiI18n.tr(p, "gui.effects.editor.actionDetail.delete")), ctx -> {
      if (index < 0 || index >= actions.size()) {
        return;
      }
      actions.remove(index);
      saveDraft(ctx.player(), "action.delete");
      ctx.close();
    }).autoDescribeInLore(false);
  }

  private Button editChildrenButton(EditorActionType type) {
    return new Button(p -> GuiItems.named(Material.CHEST, GuiI18n.tr(p, "gui.effects.editor.actionDetail.children.title"), List.of(
        GuiI18n.tr(p, "gui.effects.editor.actionDetail.children.hint"))), ctx -> {
      Map<String, Object> node = actions.get(index);
      List<Map<String, Object>> childList = EditorActionTree.ensureChildList(node, type.childKey());
      EditorActionGraphMenu menu = new EditorActionGraphMenu(services, draft, root, childList,
          GuiI18n.str(GuiI18n.defaultLocale(), "gui.effects.editor.actionDetail.children.graphTitle"), this::childUpdated);
      openSubWindow(ctx.player(), menu);
      saveDraft(ctx.player(), "action.children");
    }).autoDescribeInLore(false);
  }

  private Button editOtherwiseButton() {
    return new Button(p -> GuiItems.named(Material.CHEST, GuiI18n.tr(p, "gui.effects.editor.actionDetail.otherwise.title"), List.of(
        GuiI18n.tr(p, "gui.effects.editor.actionDetail.otherwise.hint"))), ctx -> {
      Map<String, Object> node = actions.get(index);
      List<Map<String, Object>> childList = EditorActionTree.ensureChildList(node, "otherwise");
      EditorActionGraphMenu menu = new EditorActionGraphMenu(services, draft, root, childList,
          GuiI18n.str(GuiI18n.defaultLocale(), "gui.effects.editor.actionDetail.otherwise.graphTitle"), this::childUpdated);
      openSubWindow(ctx.player(), menu);
      saveDraft(ctx.player(), "action.otherwise");
    }).autoDescribeInLore(false);
  }

  private void childUpdated() {
    Player viewer = viewer() == null ? null : org.bukkit.Bukkit.getPlayer(viewer());
    if (viewer != null) {
      redraw(viewer);
    }
  }

  private void buildParams(Player player, Map<String, Object> node, String typeId) {
    switch (typeId) {
      case "message", "action_bar" -> {
        setDynamicAt(3, 1, textParam("gui.effects.editor.actionDetail.param.text.label",
            node, "text", "gui.effects.editor.actionDetail.param.text.prompt"));
      }
      case "sound" -> {
        setDynamicAt(3, 1, textParam("gui.effects.editor.actionDetail.param.sound.label",
            node, "sound", "gui.effects.editor.actionDetail.param.sound.prompt"));
        setDynamicAt(3, 3, numberParam("gui.effects.editor.actionDetail.param.volume.label", node, "volume", false));
        setDynamicAt(3, 5, numberParam("gui.effects.editor.actionDetail.param.pitch.label", node, "pitch", false));
      }
      case "particles_point" -> {
        setDynamicAt(3, 1, textParam("gui.effects.editor.actionDetail.param.particle.label",
            node, "particle", "gui.effects.editor.actionDetail.param.particle.prompt"));
        setDynamicAt(3, 3, numberParam("gui.effects.editor.actionDetail.param.count.label", node, "count", true));
        setDynamicAt(3, 5, numberParam("gui.effects.editor.actionDetail.param.offset.label", node, "offset", false));
        setDynamicAt(3, 7, numberParam("gui.effects.editor.actionDetail.param.extra.label", node, "extra", false));
      }
      case "damage" -> {
        setDynamicAt(3, 1, numberParam("gui.effects.editor.actionDetail.param.amount.label", node, "amount", false));
        setDynamicAt(3, 3, damagePolicySelector(node, player));
      }
      case "delay" -> setDynamicAt(3, 1, numberParam("gui.effects.editor.actionDetail.param.ticks.label", node, "ticks", true));
      case "repeat_ticks" -> {
        setDynamicAt(3, 1, numberParam("gui.effects.editor.actionDetail.param.delay.label", node, "delayTicks", true));
        setDynamicAt(3, 3, numberParam("gui.effects.editor.actionDetail.param.period.label", node, "periodTicks", true));
        setDynamicAt(3, 5, numberParam("gui.effects.editor.actionDetail.param.times.label", node, "times", true));
      }
      case "when" -> {
        setDynamicAt(3, 1, conditionSelector(node, player));
        setDynamicAt(3, 3, conditionValue(node));
      }
      case "for_each_target" -> {
        setDynamicAt(3, 1, targeterSelector(node, player));
        setDynamicAt(3, 3, targeterParamOne(node));
        setDynamicAt(3, 5, targeterParamTwo(node));
        setDynamicAt(3, 7, targeterParamThree(node));
        setDynamicAt(4, 1, targeterFilterSelector(node, player));
        setDynamicAt(4, 3, targeterModeSelector(node, player));
        setDynamicAt(4, 5, numberParam("gui.effects.editor.actionDetail.param.maxTargets.label", node, "maxTargets", true));
        setDynamicAt(4, 7, targeterOriginSelector(node, player));
      }
      case "title" -> {
        setDynamicAt(3, 1, textParam("gui.effects.editor.actionDetail.param.title.label",
            node, "title", "gui.effects.editor.actionDetail.param.title.prompt"));
        setDynamicAt(3, 3, textParam("gui.effects.editor.actionDetail.param.subtitle.label",
            node, "subtitle", "gui.effects.editor.actionDetail.param.subtitle.prompt"));
        setDynamicAt(4, 1, numberParam("gui.effects.editor.actionDetail.param.fadeIn.label", node, "fadeInTicks", true));
        setDynamicAt(4, 3, numberParam("gui.effects.editor.actionDetail.param.stay.label", node, "stayTicks", true));
        setDynamicAt(4, 5, numberParam("gui.effects.editor.actionDetail.param.fadeOut.label", node, "fadeOutTicks", true));
      }
      default -> {
      }
    }
  }

  private TextButton textParam(String labelKey, Map<String, Object> node, String key, String promptKey) {
    return new TextButton(
        p -> {
          String current = valueOrNone(p, node.get(key));
          return GuiItems.named(Material.PAPER, GuiI18n.tr(p, labelKey), List.of(
              GuiI18n.tr(p, "gui.effects.editor.actionDetail.param.current.label"),
              renderValue(current)));
        },
        GuiI18n.tr(promptKey),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          node.put(key, text.trim());
          saveDraft(player, "action." + key);
          w.redraw(player);
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton numberParam(String labelKey, Map<String, Object> node, String key, boolean integer) {
    return new TextButton(
        p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, labelKey), List.of(
            GuiI18n.tr(p, "gui.effects.editor.actionDetail.param.current.value",
                Placeholder.unparsed("value", valueOrNone(p, node.get(key)))))),
        GuiI18n.tr("gui.effects.editor.actionDetail.param.number.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          if (integer) {
            node.put(key, Integer.parseInt(text.trim()));
          } else {
            node.put(key, Double.parseDouble(text.trim()));
          }
          saveDraft(player, "action." + key);
          w.redraw(player);
        },
        true)
            .inputMode(TextButton.InputMode.CHAT)
            .validate((window, player, input) -> validateNumber(player, input, integer));
  }

  private GuiComponent damagePolicySelector(Map<String, Object> node, Player player) {
    List<String> policies = List.of("hostile_default", "any", "pve_only", "pvp_only");
    CycleSelector<String> selector = new CycleSelector<>(policies,
        (viewer, value) -> GuiItems.named(Material.IRON_SWORD, GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.policy.title"), List.of(
            GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.policy.current", Placeholder.unparsed("value", value)))));
    Object raw = node.get("policy");
    String current = raw == null ? "hostile_default" : raw.toString();
    if (player != null && policies.contains(current)) {
      selector.select(player, current);
    }
    selector.onChange((viewer, value) -> {
      node.put("policy", value);
      saveDraft(viewer, "action.policy");
    });
    return selector;
  }

  private GuiComponent conditionSelector(Map<String, Object> node, Player player) {
    Map<String, Object> cond = conditionMap(node);
    List<ConditionType> types = List.of(ConditionType.values());
    CycleSelector<ConditionType> selector = new CycleSelector<>(types,
        (viewer, value) -> GuiItems.named(Material.REDSTONE_TORCH, GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.condition.title"), List.of(
            GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.condition.type", Placeholder.unparsed("value", value.label)))));
    ConditionType current = conditionType(cond);
    if (player != null) {
      selector.select(player, current);
    }
    selector.onChange((viewer, value) -> {
      cond.clear();
      cond.put("type", value.id);
      if (value == ConditionType.PERMISSION) {
        cond.put("permission", "dungeonsreborn.editor");
      } else if (value == ConditionType.CHANCE) {
        cond.put("chance", 0.5);
      }
      node.put("condition", cond);
      saveDraft(viewer, "action.condition.type");
    });
    return selector;
  }

  private GuiComponent conditionValue(Map<String, Object> node) {
    Map<String, Object> cond = conditionMap(node);
    ConditionType type = conditionType(cond);
    if (type == ConditionType.PERMISSION) {
      return textParam("gui.effects.editor.actionDetail.condition.permission.label", cond, "permission",
          "gui.effects.editor.actionDetail.condition.permission.prompt");
    }
    if (type == ConditionType.CHANCE) {
      return numberParam("gui.effects.editor.actionDetail.condition.chance.label", cond, "chance", false);
    }
    return new Label(GuiItems.named(Material.GRAY_DYE, GuiI18n.tr("gui.effects.editor.actionDetail.condition.title"), List.of(
        GuiI18n.tr("gui.effects.editor.actionDetail.condition.none"))));
  }

  private GuiComponent targeterSelector(Map<String, Object> node, Player player) {
    Map<String, Object> targeter = targeterMap(node);
    List<TargeterType> types = List.of(TargeterType.values());
    CycleSelector<TargeterType> selector = new CycleSelector<>(types,
        (viewer, value) -> GuiItems.named(Material.TARGET, GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.title"), List.of(
            GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.type", Placeholder.unparsed("value", value.label)))));
    TargeterType current = targeterType(targeter);
    if (player != null) {
      selector.select(player, current);
    }
    selector.onChange((viewer, value) -> {
      targeter.clear();
      targeter.put("type", value.id);
      applyTargeterDefaults(targeter, value);
      node.put("targeter", targeter);
      saveDraft(viewer, "action.targeter.type");
      redraw(viewer);
    });
    return selector;
  }

  private GuiComponent targeterParamOne(Map<String, Object> node) {
    Map<String, Object> targeter = targeterMap(node);
    TargeterType type = targeterType(targeter);
    return switch (type) {
      case SPHERE, NEAREST, CONE, CYLINDER -> numberParam("gui.effects.editor.actionDetail.targeter.param.radius", targeter, "radius", false);
      case BOX -> numberParam("gui.effects.editor.actionDetail.targeter.param.xRadius", targeter, "xRadius", false);
      default -> new Label(GuiItems.named(Material.GRAY_DYE, Component.text("")));
    };
  }

  private GuiComponent targeterParamTwo(Map<String, Object> node) {
    Map<String, Object> targeter = targeterMap(node);
    TargeterType type = targeterType(targeter);
    return switch (type) {
      case CONE -> numberParam("gui.effects.editor.actionDetail.targeter.param.angle", targeter, "angleDegrees", false);
      case BOX -> numberParam("gui.effects.editor.actionDetail.targeter.param.yRadius", targeter, "yRadius", false);
      case CYLINDER -> numberParam("gui.effects.editor.actionDetail.targeter.param.height", targeter, "height", false);
      default -> new Label(GuiItems.named(Material.GRAY_DYE, Component.text("")));
    };
  }

  private GuiComponent targeterParamThree(Map<String, Object> node) {
    Map<String, Object> targeter = targeterMap(node);
    TargeterType type = targeterType(targeter);
    return switch (type) {
      case BOX -> numberParam("gui.effects.editor.actionDetail.targeter.param.zRadius", targeter, "zRadius", false);
      default -> new Label(GuiItems.named(Material.GRAY_DYE, Component.text("")));
    };
  }

  private GuiComponent targeterFilterSelector(Map<String, Object> node, Player player) {
    Map<String, Object> targeter = targeterMap(node);
    List<String> options = List.of("any", "players", "mobs");
    CycleSelector<String> selector = new CycleSelector<>(options,
        (viewer, value) -> GuiItems.named(Material.CHAINMAIL_HELMET, GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.filter.title"), List.of(
            GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.filter.current", Placeholder.unparsed("value", value)))));
    String current = String.valueOf(targeter.getOrDefault("filter", "any"));
    if (player != null && options.contains(current)) {
      selector.select(player, current);
    }
    selector.onChange((viewer, value) -> {
      targeter.put("filter", value);
      saveDraft(viewer, "action.targeter.filter");
    });
    return selector;
  }

  private GuiComponent targeterModeSelector(Map<String, Object> node, Player player) {
    List<String> modes = List.of("each", "first");
    CycleSelector<String> selector = new CycleSelector<>(modes,
        (viewer, value) -> GuiItems.named(Material.LEVER, GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.mode.title"), List.of(
            GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.mode.current", Placeholder.unparsed("value", value)))));
    String current = String.valueOf(node.getOrDefault("mode", "each"));
    if (player != null && modes.contains(current)) {
      selector.select(player, current);
    }
    selector.onChange((viewer, value) -> {
      node.put("mode", value);
      saveDraft(viewer, "action.mode");
    });
    return selector;
  }

  private GuiComponent targeterOriginSelector(Map<String, Object> node, Player player) {
    List<String> options = List.of("origin", "caster", "target");
    CycleSelector<String> selector = new CycleSelector<>(options,
        (viewer, value) -> GuiItems.named(Material.ENDER_PEARL, GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.origin.title"), List.of(
            GuiI18n.tr(viewer, "gui.effects.editor.actionDetail.targeter.origin.current", Placeholder.unparsed("value", value)))));
    String current = String.valueOf(node.getOrDefault("originAt", "origin"));
    if (player != null && options.contains(current)) {
      selector.select(player, current);
    }
    selector.onChange((viewer, value) -> {
      node.put("originAt", value);
      saveDraft(viewer, "action.originAt");
    });
    return selector;
  }

  private Map<String, Object> conditionMap(Map<String, Object> node) {
    Object raw = node.get("condition");
    Map<String, Object> map = EditorActionTree.mapFrom(raw);
    if (map == null) {
      map = new LinkedHashMap<>();
      map.put("type", "always");
      node.put("condition", map);
    }
    return map;
  }

  private ConditionType conditionType(Map<String, Object> condition) {
    String type = String.valueOf(condition.getOrDefault("type", "always")).toLowerCase(Locale.ROOT);
    for (ConditionType candidate : ConditionType.values()) {
      if (candidate.id.equals(type)) {
        return candidate;
      }
    }
    return ConditionType.ALWAYS;
  }

  private Map<String, Object> targeterMap(Map<String, Object> node) {
    Object raw = node.get("targeter");
    Map<String, Object> map = EditorActionTree.mapFrom(raw);
    if (map == null) {
      map = new LinkedHashMap<>();
      map.put("type", "self");
      node.put("targeter", map);
    }
    return map;
  }

  private TargeterType targeterType(Map<String, Object> targeter) {
    String type = String.valueOf(targeter.getOrDefault("type", "self")).toLowerCase(Locale.ROOT);
    for (TargeterType candidate : TargeterType.values()) {
      if (candidate.id.equals(type)) {
        return candidate;
      }
    }
    return TargeterType.SELF;
  }

  private void applyTargeterDefaults(Map<String, Object> targeter, TargeterType type) {
    switch (type) {
      case SPHERE, NEAREST -> targeter.put("radius", 6.0);
      case CONE -> {
        targeter.put("radius", 8.0);
        targeter.put("angleDegrees", 90.0);
      }
      case BOX -> {
        targeter.put("xRadius", 6.0);
        targeter.put("yRadius", 3.0);
        targeter.put("zRadius", 6.0);
      }
      case CYLINDER -> {
        targeter.put("radius", 6.0);
        targeter.put("height", 4.0);
      }
      default -> {
      }
    }
  }

  private void saveDraft(Player player, String detail) {
    draft.clearScript();
    EditorActionTree.setRoot(draft, root);
    services.drafts().save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
  }

  private static Component validateNumber(Player player, String input, boolean integer) {
    if (input == null || input.isBlank()) {
      return GuiI18n.tr(player, "gui.effects.editor.actionDetail.error.valueRequired");
    }
    try {
      if (integer) {
        Integer.parseInt(input.trim());
      } else {
        Double.parseDouble(input.trim());
      }
      return null;
    } catch (NumberFormatException ex) {
      return GuiI18n.tr(player, "gui.effects.editor.actionDetail.error.invalidNumber");
    }
  }

  private static String valueOrNone(Player player, Object raw) {
    if (raw == null) {
      return Locales.text(player, "gui.common.none");
    }
    String value = raw.toString();
    return value.isBlank() ? Locales.text(player, "gui.common.none") : value;
  }

  private static Component renderValue(String raw) {
    if (raw == null) {
      return GuiI18n.tr(GuiI18n.defaultLocale(), "gui.common.none");
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
