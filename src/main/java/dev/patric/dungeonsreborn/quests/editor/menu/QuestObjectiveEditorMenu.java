package dev.patric.dungeonsreborn.quests.editor.menu;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.patric.dungeonsreborn.gui.GuiI18n;
import dev.patric.dungeonsreborn.gui.GuiItems;
import dev.patric.dungeonsreborn.gui.GuiSounds;
import dev.patric.dungeonsreborn.gui.Window;
import dev.patric.dungeonsreborn.gui.components.BackButton;
import dev.patric.dungeonsreborn.gui.components.Button;
import dev.patric.dungeonsreborn.gui.components.Label;
import dev.patric.dungeonsreborn.gui.components.TextButton;
import dev.patric.dungeonsreborn.quests.QuestObjectiveType;
import dev.patric.dungeonsreborn.quests.QuestYamlRegistry;
import dev.patric.dungeonsreborn.quests.editor.QuestEditorYaml;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class QuestObjectiveEditorMenu extends Window {
  private static final int SIZE = 54;

  private final QuestYamlRegistry yaml;
  private final String questId;
  private final int index;

  public QuestObjectiveEditorMenu(QuestYamlRegistry yaml, String questId, int index) {
    super(SIZE, GuiI18n.tr("gui.quests.objective.edit.title"), true);
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.questId = Objects.requireNonNull(questId, "questId");
    this.index = index;

    background(GuiItems.blankPane(Material.GRAY_STAINED_GLASS_PANE));

    navLeft(new BackButton(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.button.back"), List.of())));

    setFixedAt(0, 4, new Label(p -> GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.quests.objective.edit.header.title"), List.of(
        GuiI18n.tr(p, "gui.quests.objective.edit.header.index", Placeholder.unparsed("value", String.valueOf(index)))))));

    setFixedAt(1, 1, typeButton());
    setFixedAt(1, 2, countButton());
    setFixedAt(1, 3, mobButton());
    setFixedAt(1, 4, entityButton());
    setFixedAt(2, 1, itemIdButton());
    setFixedAt(2, 2, materialButton());
    setFixedAt(2, 3, recipeButton());
    setFixedAt(2, 4, regionButton());
    setFixedAt(3, 6, deleteButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private QuestEditorYaml.ObjectiveData objective() {
    List<QuestEditorYaml.ObjectiveData> list = QuestEditorYaml.objectives(yaml.file(), questId);
    if (index < 0 || index >= list.size()) {
      return null;
    }
    return list.get(index);
  }

  private Button typeButton() {
    return new Button(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      QuestObjectiveType type = data == null ? QuestObjectiveType.KILL_MOB : data.type();
      return GuiItems.named(Material.NETHER_STAR, GuiI18n.tr(p, "gui.quests.objective.edit.type.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.type.current", Placeholder.unparsed("value", type.name().toLowerCase())),
          GuiI18n.tr(p, "gui.quests.objective.edit.type.hint")));
    }, ctx -> {
      QuestEditorYaml.ObjectiveData data = objective();
      if (data == null) {
        return;
      }
      QuestObjectiveType next = switch (data.type()) {
        case KILL_MOB -> QuestObjectiveType.USE_ITEM;
        case USE_ITEM -> QuestObjectiveType.VISIT_REGION;
        case VISIT_REGION -> QuestObjectiveType.CRAFT_ITEM;
        case CRAFT_ITEM -> QuestObjectiveType.KILL_MOB;
      };
      QuestEditorYaml.setObjectiveType(yaml.file(), questId, index, next);
      yaml.reload();
      ctx.window().redraw(ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private TextButton countButton() {
    TextButton button = new TextButton(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      int count = data == null ? 1 : intValue(data.raw().get("count"), 1);
      return GuiItems.named(Material.EXPERIENCE_BOTTLE, GuiI18n.tr(p, "gui.quests.objective.edit.count.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.count.current", Placeholder.unparsed("value", String.valueOf(count)))));
    }, GuiI18n.tr("gui.quests.objective.edit.count.prompt"), GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          int count = parseInt(text, 1);
          QuestEditorYaml.setObjectiveCount(yaml.file(), questId, index, count);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        }, true);
    button.validate((w, p, input) -> validatePositive(input));
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton mobButton() {
    TextButton button = new TextButton(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      String mob = data == null ? null : string(data.raw(), "mob");
      return GuiItems.named(Material.ZOMBIE_HEAD, GuiI18n.tr(p, "gui.quests.objective.edit.mob.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.mob.current",
              Placeholder.unparsed("value", mob == null ? GuiI18n.str(p, "gui.common.none") : mob))));
    }, GuiI18n.tr("gui.quests.objective.edit.mob.prompt"), GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          QuestEditorYaml.setObjectiveMob(yaml.file(), questId, index, text);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton entityButton() {
    TextButton button = new TextButton(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      String entity = data == null ? null : string(data.raw(), "entity");
      return GuiItems.named(Material.SKELETON_SKULL, GuiI18n.tr(p, "gui.quests.objective.edit.entity.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.entity.current",
              Placeholder.unparsed("value", entity == null ? GuiI18n.str(p, "gui.common.none") : entity))));
    }, GuiI18n.tr("gui.quests.objective.edit.entity.prompt"), GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          QuestEditorYaml.setObjectiveEntity(yaml.file(), questId, index, text);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton itemIdButton() {
    TextButton button = new TextButton(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      String itemId = data == null ? null : string(data.raw(), "itemId");
      return GuiItems.named(Material.PAPER, GuiI18n.tr(p, "gui.quests.objective.edit.itemId.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.itemId.current",
              Placeholder.unparsed("value", itemId == null ? GuiI18n.str(p, "gui.common.none") : itemId))));
    }, GuiI18n.tr("gui.quests.objective.edit.itemId.prompt"), GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          QuestEditorYaml.setObjectiveItemId(yaml.file(), questId, index, text);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton materialButton() {
    TextButton button = new TextButton(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      String material = data == null ? null : string(data.raw(), "material");
      return GuiItems.named(Material.BRICKS, GuiI18n.tr(p, "gui.quests.objective.edit.material.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.material.current",
              Placeholder.unparsed("value", material == null ? GuiI18n.str(p, "gui.common.none") : material))));
    }, GuiI18n.tr("gui.quests.objective.edit.material.prompt"), GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          QuestEditorYaml.setObjectiveMaterial(yaml.file(), questId, index, text);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton recipeButton() {
    TextButton button = new TextButton(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      String recipeId = data == null ? null : string(data.raw(), "recipeId");
      return GuiItems.named(Material.CRAFTING_TABLE, GuiI18n.tr(p, "gui.quests.objective.edit.recipe.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.recipe.current",
              Placeholder.unparsed("value", recipeId == null ? GuiI18n.str(p, "gui.common.none") : recipeId))));
    }, GuiI18n.tr("gui.quests.objective.edit.recipe.prompt"), GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(45),
        (window, text) -> {
          QuestEditorYaml.setObjectiveRecipeId(yaml.file(), questId, index, text);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private TextButton regionButton() {
    TextButton button = new TextButton(p -> {
      QuestEditorYaml.ObjectiveData data = objective();
      Map<String, Object> raw = data == null ? Map.of() : data.raw();
      String world = string(raw, "world");
      double x = doubleValue(raw.get("x"), 0.0);
      double y = doubleValue(raw.get("y"), 64.0);
      double z = doubleValue(raw.get("z"), 0.0);
      double radius = doubleValue(raw.get("radius"), 4.0);
      String defaultWorld = world == null ? "minecraft:world" : world;
      return GuiItems.named(Material.COMPASS, GuiI18n.tr(p, "gui.quests.objective.edit.region.title"), List.of(
          GuiI18n.tr(p, "gui.quests.objective.edit.region.world", Placeholder.unparsed("value", defaultWorld)),
          GuiI18n.tr(p, "gui.quests.objective.edit.region.xyz",
              Placeholder.unparsed("x", String.valueOf(x)),
              Placeholder.unparsed("y", String.valueOf(y)),
              Placeholder.unparsed("z", String.valueOf(z))),
          GuiI18n.tr(p, "gui.quests.objective.edit.region.radius", Placeholder.unparsed("value", String.valueOf(radius))),
          GuiI18n.tr(p, "gui.quests.objective.edit.region.format")));
    }, GuiI18n.tr("gui.quests.objective.edit.region.prompt"), GuiI18n.str(GuiI18n.defaultLocale(), "gui.textInput.cancelWord"),
        Duration.ofSeconds(60),
        (window, text) -> {
          if (text == null || text.isBlank()) {
            return;
          }
          String[] parts = text.split(",");
          String world = parts.length > 0 ? parts[0].trim() : "minecraft:world";
          double x = parts.length > 1 ? parseDouble(parts[1], 0.0) : 0.0;
          double y = parts.length > 2 ? parseDouble(parts[2], 64.0) : 64.0;
          double z = parts.length > 3 ? parseDouble(parts[3], 0.0) : 0.0;
          double radius = parts.length > 4 ? parseDouble(parts[4], 4.0) : 4.0;
          QuestEditorYaml.setObjectiveRegion(yaml.file(), questId, index, world, x, y, z, radius);
          yaml.reload();
          Player player = viewerPlayer(window);
          if (player != null) {
            window.redraw(player);
          }
        }, true);
    button.autoDescribeInLore(false);
    return button;
  }

  private Button deleteButton() {
    return new Button(p -> GuiItems.named(Material.BARRIER, GuiI18n.tr(p, "gui.quests.objective.edit.delete.title"), List.of(
        GuiI18n.tr(p, "gui.quests.objective.edit.delete.hint"))), ctx -> {
      QuestEditorYaml.removeObjective(yaml.file(), questId, index);
      yaml.reload();
      ctx.close();
    }).autoDescribeInLore(false);
  }

  private Component validatePositive(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    try {
      int value = Integer.parseInt(input.trim());
      return value <= 0 ? GuiI18n.tr("gui.textInput.error.positive") : null;
    } catch (NumberFormatException ex) {
      return GuiI18n.tr("gui.textInput.error.integer");
    }
  }

  private int parseInt(String input, int def) {
    if (input == null || input.isBlank()) {
      return def;
    }
    try {
      return Integer.parseInt(input.trim());
    } catch (NumberFormatException ex) {
      return def;
    }
  }

  private double parseDouble(String input, double def) {
    if (input == null || input.isBlank()) {
      return def;
    }
    try {
      return Double.parseDouble(input.trim());
    } catch (NumberFormatException ex) {
      return def;
    }
  }

  private String string(Map<String, Object> map, String key) {
    Object raw = map.get(key);
    if (raw == null) {
      return null;
    }
    String value = raw.toString();
    return value.isBlank() ? null : value;
  }

  private int intValue(Object raw, int def) {
    if (raw instanceof Number num) {
      return num.intValue();
    }
    if (raw instanceof String str) {
      try {
        return Integer.parseInt(str.trim());
      } catch (NumberFormatException ex) {
        return def;
      }
    }
    return def;
  }

  private double doubleValue(Object raw, double def) {
    if (raw instanceof Number num) {
      return num.doubleValue();
    }
    if (raw instanceof String str) {
      try {
        return Double.parseDouble(str.trim());
      } catch (NumberFormatException ex) {
        return def;
      }
    }
    return def;
  }

  private Player viewerPlayer(Window window) {
    if (window == null || window.viewer() == null) {
      return null;
    }
    return org.bukkit.Bukkit.getPlayer(window.viewer());
  }
}
