package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.editor.EditorAbilityDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityYaml;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.effects.integration.ItemMatchers;
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
import dev.patric.dungeonsreborn.gui.components.item.ItemPreview;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class EditorBindingDetailMenu extends Window {
  private static final int SIZE = 54;

  private enum MatcherType {
    ANY_NON_AIR("any_non_air", "Any Non-Air", "Matches any non-air item."),
    MATERIAL("material", "Material", "Matches a specific material."),
    CUSTOM_MODEL_DATA("custom_model_data", "Custom Model Data", "Matches a specific custom model data value."),
    PDC_TAG("pdc_tag", "PDC Tag", "Matches an item marker tag (NamespacedKey)."),
    LORE_CONTAINS("lore_contains", "Lore Contains", "Matches if lore contains a string.");

    private final String id;
    @SuppressWarnings("unused")
    private final String label;
    @SuppressWarnings("unused")
    private final String hint;

    MatcherType(String id, String label, String hint) {
      this.id = id;
      this.label = label;
      this.hint = hint;
    }

    static MatcherType fromRaw(String raw) {
      if (raw == null) {
        return ANY_NON_AIR;
      }
      String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_');
      return switch (normalized) {
        case "material" -> MATERIAL;
        case "custom_model_data", "cmd", "custommodeldata" -> CUSTOM_MODEL_DATA;
        case "pdc_tag", "tag" -> PDC_TAG;
        case "lore_contains", "lorecontains" -> LORE_CONTAINS;
        default -> ANY_NON_AIR;
      };
    }
  }

  private final EditorServices services;
  private final EditorAbilityDraft draft;
  private final int index;
  private final List<Map<String, Object>> triggers;
  private final Runnable onCloseRefresh;
  private final CycleSelector<InteractTrigger> clickSelector;
  private final CycleSelector<MatcherType> matcherTypeSelector;

  public EditorBindingDetailMenu(EditorServices services, EditorAbilityDraft draft, int index, Runnable onCloseRefresh) {
    super(SIZE, GuiI18n.tr("gui.effects.editor.binding.title"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.draft = Objects.requireNonNull(draft, "draft");
    this.index = index;
    this.onCloseRefresh = onCloseRefresh == null ? () -> {
    } : onCloseRefresh;
    this.triggers = EditorAbilityYaml.triggers(draft);

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));
    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, GuiI18n.tr(p, "gui.button.back"))));

    clickSelector = new CycleSelector<>(List.of(InteractTrigger.RIGHT_CLICK, InteractTrigger.LEFT_CLICK),
        (player, value) -> GuiItems.named(Material.ENDER_PEARL, GuiI18n.tr(player, "gui.effects.editor.binding.click.title"), List.of(
            GuiI18n.tr(player, "gui.effects.editor.binding.click.current",
                Placeholder.unparsed("value", value.name())))))
        .onChange((player, value) -> {
          Map<String, Object> trigger = binding();
          trigger.put("type", "interact");
          trigger.put("click", value.name());
          saveDraft(player, "triggers.click");
        });

    matcherTypeSelector = new CycleSelector<>(List.of(MatcherType.values()),
        (player, value) -> GuiItems.named(Material.COMPARATOR, GuiI18n.tr(player, "gui.effects.editor.binding.matcherType.title"), List.of(
            GuiI18n.tr(player, "gui.effects.editor.binding.matcherType.current",
                Placeholder.component("value", matcherLabel(player, value))),
            matcherHint(player, value))))
        .onChange((player, value) -> {
          Map<String, Object> trigger = binding();
          trigger.put("item", defaultMatcher(value));
          saveDraft(player, "triggers.matcher.type");
          redraw(player);
        });

    setFixedAt(0, 4, new Label(GuiItems.named(Material.TRIPWIRE_HOOK,
        GuiI18n.tr("gui.effects.editor.binding.header.title",
            Placeholder.unparsed("index", String.valueOf(index + 1))))));
    setFixedAt(1, 1, clickSelector);
    setFixedAt(1, 3, requireSneakingToggle());
    setFixedAt(1, 5, cancelEventToggle());
    setFixedAt(2, 1, permissionInput());
    setFixedAt(2, 3, bindingIdInput());
    setFixedAt(3, 1, matcherTypeSelector);
    setFixedAt(4, 1, new ItemPreview(p -> p.getInventory().getItemInMainHand())
        .placeholder(GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, GuiI18n.tr("gui.effects.editor.binding.preview.none"))));
    setFixedAt(4, 5, deleteButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> {
      this.onCloseRefresh.run();
      GuiSounds.close(ctx.player());
    });
  }

  @Override
  protected void build(Player player) {
    if (index < 0 || index >= triggers.size()) {
      return;
    }
    clickSelector.select(player, currentClick());
    matcherTypeSelector.select(player, currentMatcherType());
    setDynamicAt(3, 3, matcherValueButton());
    setDynamicAt(3, 5, matcherSummary());
    setDynamicAt(4, 3, matchStatus());
  }

  private Map<String, Object> binding() {
    if (index < 0 || index >= triggers.size()) {
      throw new IllegalStateException("Binding index out of range");
    }
    return triggers.get(index);
  }

  private InteractTrigger currentClick() {
    Map<String, Object> trigger = binding();
    Object raw = trigger.get("click");
    if (raw == null) {
      raw = trigger.get("trigger");
    }
    String value = raw == null ? "RIGHT_CLICK" : raw.toString();
    String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return normalized.startsWith("LEFT") ? InteractTrigger.LEFT_CLICK : InteractTrigger.RIGHT_CLICK;
  }

  private MatcherType currentMatcherType() {
    Map<String, Object> matcher = matcherMap();
    Object raw = matcher.get("type");
    return MatcherType.fromRaw(raw == null ? null : raw.toString());
  }

  private Map<String, Object> matcherMap() {
    Map<String, Object> trigger = binding();
    Object raw = trigger.get("item");
    if (raw instanceof Map<?, ?> map) {
      return castMap(map);
    }
    Map<String, Object> matcher = defaultMatcher(MatcherType.ANY_NON_AIR);
    trigger.put("item", matcher);
    return matcher;
  }

  private static Map<String, Object> castMap(Map<?, ?> map) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      out.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return out;
  }

  private static Map<String, Object> defaultMatcher(MatcherType type) {
    Map<String, Object> matcher = new LinkedHashMap<>();
    matcher.put("type", type.id);
    switch (type) {
      case MATERIAL -> matcher.put("material", "STONE");
      case CUSTOM_MODEL_DATA -> matcher.put("value", 1);
      case PDC_TAG -> matcher.put("key", "dungeonsreborn:marker");
      case LORE_CONTAINS -> matcher.put("text", "magic");
      default -> {
      }
    }
    return matcher;
  }

  private static Component matcherLabel(Player player, MatcherType type) {
    return GuiI18n.tr(player, "gui.effects.editor.binding.matcher." + type.id + ".label");
  }

  private static Component matcherHint(Player player, MatcherType type) {
    return GuiI18n.tr(player, "gui.effects.editor.binding.matcher." + type.id + ".hint");
  }

  private Button requireSneakingToggle() {
    return new Button(p -> {
      boolean enabled = Boolean.TRUE.equals(binding().get("requireSneaking"));
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.effects.editor.binding.sneaking.title"), List.of(
          GuiI18n.tr(p, "gui.effects.editor.binding.sneaking.status",
              Placeholder.component("value", GuiI18n.tr(p, enabled
                  ? "gui.common.status.enabled"
                  : "gui.common.status.disabled")))));
    }, ctx -> {
      Map<String, Object> trigger = binding();
      boolean enabled = Boolean.TRUE.equals(trigger.get("requireSneaking"));
      trigger.put("requireSneaking", !enabled);
      saveDraft(ctx.player(), "triggers.require_sneaking");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 3));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button cancelEventToggle() {
    return new Button(p -> {
      boolean enabled = !Boolean.FALSE.equals(binding().get("cancelEvent"));
      Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.effects.editor.binding.cancelEvent.title"), List.of(
          GuiI18n.tr(p, "gui.effects.editor.binding.cancelEvent.status",
              Placeholder.component("value", GuiI18n.tr(p, enabled
                  ? "gui.common.status.enabled"
                  : "gui.common.status.disabled")))));
    }, ctx -> {
      Map<String, Object> trigger = binding();
      boolean enabled = !Boolean.FALSE.equals(trigger.get("cancelEvent"));
      trigger.put("cancelEvent", !enabled);
      saveDraft(ctx.player(), "triggers.cancel_event");
      ctx.window().redrawSlot(ctx.player(), slotAt(1, 5));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton permissionInput() {
    return new TextButton(
        p -> {
          Object raw = binding().get("permission");
          String value = raw == null ? "" : raw.toString();
          return GuiItems.named(Material.NAME_TAG, GuiI18n.tr(p, "gui.effects.editor.binding.permission.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.binding.permission.current",
                  Placeholder.unparsed("value", value.isBlank()
                      ? GuiI18n.str(p, "gui.common.none")
                      : value))));
        },
        GuiI18n.tr("gui.effects.editor.binding.permission.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          Map<String, Object> trigger = binding();
          if (text == null || text.isBlank()) {
            trigger.remove("permission");
          } else {
            trigger.put("permission", text.trim());
          }
          saveDraft(player, "triggers.permission");
          w.redrawSlot(player, slotAt(2, 1));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private TextButton bindingIdInput() {
    return new TextButton(
        p -> {
          Object raw = binding().get("id");
          String value = raw == null ? "" : raw.toString();
          return GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.effects.editor.binding.id.title"), List.of(
              GuiI18n.tr(p, "gui.effects.editor.binding.id.current",
                  Placeholder.unparsed("value", value.isBlank()
                      ? GuiI18n.str(p, "gui.effects.editor.binding.id.auto")
                      : value))));
        },
        GuiI18n.tr("gui.effects.editor.binding.id.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          Map<String, Object> trigger = binding();
          if (text == null || text.isBlank()) {
            trigger.remove("id");
          } else {
            trigger.put("id", text.trim());
          }
          saveDraft(player, "triggers.id");
          w.redrawSlot(player, slotAt(2, 3));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private GuiComponent matcherValueButton() {
    MatcherType type = currentMatcherType();
    Map<String, Object> matcher = matcherMap();
    String valueLabel = switch (type) {
      case MATERIAL -> String.valueOf(matcher.get("material"));
      case CUSTOM_MODEL_DATA -> String.valueOf(matcher.getOrDefault("value", matcher.get("cmd")));
      case PDC_TAG -> String.valueOf(matcher.get("key"));
      case LORE_CONTAINS -> String.valueOf(matcher.get("text"));
      default -> GuiI18n.str(GuiI18n.defaultLocale(), "gui.common.none");
    };

    if (type == MatcherType.ANY_NON_AIR) {
      return new Label(GuiItems.named(Material.GRAY_DYE, GuiI18n.tr("gui.effects.editor.binding.matcherValue.title"), List.of(
          GuiI18n.tr("gui.effects.editor.binding.matcherValue.none"))));
    }

    return new TextButton(
        p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.effects.editor.binding.matcherValue.title"), List.of(
            GuiI18n.tr(p, "gui.effects.editor.binding.matcherValue.current",
                Placeholder.unparsed("value", valueLabel)))),
        GuiI18n.tr("gui.effects.editor.binding.matcherValue.prompt"),
        GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          Map<String, Object> updated = matcherMap();
          switch (type) {
            case MATERIAL -> updated.put("material", text.trim().toUpperCase(Locale.ROOT));
            case CUSTOM_MODEL_DATA -> updated.put("value", Integer.parseInt(text.trim()));
            case PDC_TAG -> updated.put("key", text.trim());
            case LORE_CONTAINS -> updated.put("text", text.trim());
            default -> {
            }
          }
          binding().put("item", updated);
          saveDraft(player, "triggers.matcher.value");
          redraw(player);
        },
        true)
            .inputMode(TextButton.InputMode.CHAT)
            .validate((window, player, input) -> validateMatcherValue(type, input));
  }

  private Label matcherSummary() {
    return new Label(p -> {
      Map<String, Object> matcher = matcherMap();
      MatcherType type = currentMatcherType();
      String value = switch (type) {
        case MATERIAL -> String.valueOf(matcher.get("material"));
        case CUSTOM_MODEL_DATA -> String.valueOf(matcher.getOrDefault("value", matcher.get("cmd")));
        case PDC_TAG -> String.valueOf(matcher.get("key"));
        case LORE_CONTAINS -> String.valueOf(matcher.get("text"));
        default -> GuiI18n.str(p, "gui.effects.editor.binding.matcherSummary.any");
      };
      return GuiItems.named(Material.COMPARATOR, GuiI18n.tr(p, "gui.effects.editor.binding.matcherSummary.title"), List.of(
          GuiI18n.tr(p, "gui.effects.editor.binding.matcherSummary.type",
              Placeholder.component("value", matcherLabel(p, type))),
          GuiI18n.tr(p, "gui.effects.editor.binding.matcherSummary.value",
              Placeholder.unparsed("value", value))));
    });
  }

  private Label matchStatus() {
    return new Label(p -> {
      boolean match = matchesCurrentItem(p);
      Material mat = match ? Material.LIME_DYE : Material.GRAY_DYE;
      return GuiItems.named(mat, GuiI18n.tr(p, "gui.effects.editor.binding.match.title"), List.of(
          GuiI18n.tr(p, "gui.effects.editor.binding.match.status",
              Placeholder.component("value", GuiI18n.tr(p, match
                  ? "gui.effects.editor.binding.match.matches"
                  : "gui.effects.editor.binding.match.noMatch")))));
    });
  }

  private Button deleteButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.TRASH, GuiI18n.tr(p, "gui.effects.editor.binding.delete")), ctx -> {
      if (index < 0 || index >= triggers.size()) {
        return;
      }
      triggers.remove(index);
      EditorAbilityYaml.writeTriggers(draft, triggers);
      saveDraft(ctx.player(), "triggers.delete");
      ctx.player().closeInventory();
    }).autoDescribeInLore(false);
  }

  private void saveDraft(Player player, String detail) {
    EditorAbilityYaml.writeTriggers(draft, triggers);
    services.drafts().save(draft);
    services.audit().log(EditorAuditEvent.of(EditorAuditAction.EDIT, player.getUniqueId(), player.getName(), draft.id(), detail));
  }

  private boolean matchesCurrentItem(Player player) {
    ItemStack item = player.getInventory().getItemInMainHand();
    Map<String, Object> matcher = matcherMap();
    MatcherType type = currentMatcherType();
    ItemMatcher compiled = switch (type) {
      case MATERIAL -> {
        Object raw = matcher.get("material");
        Material mat = raw == null ? null : Material.matchMaterial(raw.toString().trim().toUpperCase(Locale.ROOT));
        yield mat == null ? null : ItemMatchers.material(mat);
      }
      case CUSTOM_MODEL_DATA -> {
        Object raw = matcher.getOrDefault("value", matcher.get("cmd"));
        if (raw == null) {
          yield null;
        }
        try {
          int value = Integer.parseInt(raw.toString());
          yield ItemMatchers.customModelData(value);
        } catch (NumberFormatException ex) {
          yield null;
        }
      }
      case PDC_TAG -> {
        Object raw = matcher.get("key");
        if (raw == null) {
          yield null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw.toString());
        yield key == null ? null : ItemMatchers.tag(key);
      }
      case LORE_CONTAINS -> {
        Object raw = matcher.get("text");
        yield raw == null ? null : ItemMatchers.loreContains(raw.toString());
      }
      default -> ItemMatchers.anyNonAir();
    };
    if (compiled == null) {
      return false;
    }
    return compiled.matches(player, item);
  }

  private static Component validateMatcherValue(MatcherType type, String input) {
    if (input == null || input.isBlank()) {
      return GuiI18n.tr(GuiI18n.defaultLocale(), "gui.effects.editor.binding.validation.required");
    }
    return switch (type) {
      case MATERIAL -> Material.matchMaterial(input.trim().toUpperCase(Locale.ROOT)) == null
          ? GuiI18n.tr(GuiI18n.defaultLocale(), "gui.effects.editor.binding.validation.material")
          : null;
      case CUSTOM_MODEL_DATA -> {
        try {
          Integer.parseInt(input.trim());
          yield null;
        } catch (NumberFormatException ex) {
          yield GuiI18n.tr(GuiI18n.defaultLocale(), "gui.effects.editor.binding.validation.integer");
        }
      }
      case PDC_TAG -> NamespacedKey.fromString(input.trim()) == null
          ? GuiI18n.tr(GuiI18n.defaultLocale(), "gui.effects.editor.binding.validation.namespacedKey")
          : null;
      case LORE_CONTAINS -> null;
      default -> null;
    };
  }
}
