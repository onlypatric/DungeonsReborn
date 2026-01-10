package dev.patric.dungeonsreborn.effects.editor.menu;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityDraft;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityImporter;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityState;
import dev.patric.dungeonsreborn.effects.editor.EditorAbilityYaml;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditAction;
import dev.patric.dungeonsreborn.effects.editor.EditorAuditEvent;
import dev.patric.dungeonsreborn.effects.editor.EditorDraftStore;
import dev.patric.dungeonsreborn.effects.editor.EditorLockManager;
import dev.patric.dungeonsreborn.effects.editor.EditorServices;
import dev.patric.dungeonsreborn.effects.integration.InteractBinding;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
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
import dev.patric.dungeonsreborn.gui.layout.Placement;
import dev.patric.dungeonsreborn.gui.style.GuiButtons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class EditorAbilityListMenu extends Window {
  private static final int SIZE = 54;
  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private record AbilityEntry(
      String id,
      String name,
      AbilitySource source,
      boolean published,
      boolean code,
      EditorAbilityDraft draft) {
  }

  private enum AbilitySource {
    DRAFT,
    YAML,
    CODE
  }

  private final EditorServices services;
  private final VirtualList<AbilityEntry> list;
  private final EditorAbilityImporter importer;

  public EditorAbilityListMenu(EditorServices services) {
    super(SIZE, GuiMini.mm("<white><bold>Spell Editor</bold></white>"), true);
    this.services = Objects.requireNonNull(services, "services");
    this.importer = services.yaml() == null ? null : new EditorAbilityImporter(services.yaml(), services.drafts());

    background(GuiItems.blankPane(Material.BLACK_STAINED_GLASS_PANE));

    this.list = new VirtualList<>(
        1, 1, 4, 7,
        this::entries,
        (player, entry) -> entryItem(player, entry),
        (ctx, entry) -> openEntry(ctx.player(), entry));
    list.searchKey(entry -> entry.id + " " + entry.name);
    list.apply(this, Placement.FIXED);

    navLeft(new BackButton(p -> GuiButtons.item(GuiButtons.Type.BACK, Component.text("Close"))));
    nav(0, list.prevButton());
    nav(1, list.pageIndicator());
    nav(2, list.nextButton());
    nav(4, createButton());
    nav(5, refreshButton());
    nav(6, itemsButton());

    setFixedAt(0, 1, header());
    setFixedAt(0, 7, filterButton());
    setFixedAt(0, 8, clearFilterButton());

    onOpenWithReason(ctx -> GuiSounds.open(ctx.player()));
    onCloseWithReason(ctx -> GuiSounds.close(ctx.player()));
  }

  private Label header() {
    return new Label(p -> GuiItems.named(Material.BOOK, GuiMini.mm("<gold><bold>Abilities</bold></gold>"), List.of(
        GuiMini.mm("<gray>Manage spell ability drafts.</gray>"),
        GuiMini.mm("<gray>Click to edit, or create a new draft.</gray>"))));
  }

  private TextButton filterButton() {
    return new TextButton(
        p -> GuiItems.named(Material.NAME_TAG, GuiMini.mm("<aqua><bold>Filter</bold></aqua>"), List.of(
            GuiMini.mm("<gray>Set a search query.</gray>"),
            GuiMini.mm("<gray>Current:</gray> <white>" + (list.query(p).isBlank() ? "(none)" : list.query(p)) + "</white>"))),
        GuiMini.mm("<gray>Type a filter query (or 'cancel')</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player viewer = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (viewer == null) {
            return;
          }
          list.query(viewer, text);
          list.redraw(w, viewer);
          w.redrawSlot(viewer, slotAt(0, 7));
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button clearFilterButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.CANCEL, Component.text("Clear")), ctx -> {
      list.clearFilter(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      ctx.window().redrawSlot(ctx.player(), slotAt(0, 7));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button createButton() {
    return new TextButton(
        p -> GuiButtons.item(GuiButtons.Type.PRIMARY, Component.text("New")),
        GuiMini.mm("<gray>Enter a new ability id</gray>"),
        "cancel",
        Duration.ofSeconds(30),
        (w, text) -> {
          Player player = w.viewer() == null ? null : org.bukkit.Bukkit.getPlayer(w.viewer());
          if (player == null) {
            return;
          }
          if (!services.access().canEdit(player)) {
            player.sendMessage(Component.text("§cMissing permission: dungeonsreborn.editor.edit"));
            return;
          }
          String id;
          try {
            id = Ids.normalize(text);
          } catch (Exception ex) {
            player.sendMessage(Component.text("§cInvalid id: " + ex.getMessage()));
            return;
          }
          EditorAbilityDraft draft = services.drafts().create(id);
          draft.setState(EditorAbilityState.DRAFT);
          services.drafts().save(draft);
          services.audit().log(EditorAuditEvent.of(EditorAuditAction.CREATE, player.getUniqueId(), player.getName(), id, "draft"));
          list.invalidateAll();
          openDraft(player, draft);
        },
        true)
            .inputMode(TextButton.InputMode.CHAT);
  }

  private Button refreshButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.INFO, Component.text("Refresh")), ctx -> {
      list.invalidate(ctx.player());
      list.redraw(ctx.window(), ctx.player());
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private Button itemsButton() {
    return new Button(p -> GuiButtons.item(GuiButtons.Type.SECONDARY, Component.text("Items")), ctx -> {
      openSubWindow(ctx.player(), new EditorItemListMenu(services));
      GuiSounds.click(ctx.player());
    }).autoDescribeInLore(false);
  }

  private List<AbilityEntry> entries(Player player) {
    EditorDraftStore drafts = services.drafts();
    EffectsEngine engine = services.engine();
    EffectsYamlAbilities yaml = services.yaml();

    Map<String, EditorAbilityDraft> draftMap = new HashMap<>();
    for (EditorAbilityDraft draft : drafts.loadAll()) {
      draftMap.put(draft.id(), draft);
    }

    Set<String> yamlIds = yaml == null ? Set.of() : yaml.loadedAbilityIds();
    List<AbilityEntry> entries = new ArrayList<>();

    for (EditorAbilityDraft draft : draftMap.values()) {
      String name = firstNonBlank(draft.yaml().getString("abilities." + draft.id() + ".name"), draft.id());
      boolean published = yamlIds.contains(draft.id());
      AbilitySpec spec = engine.abilitySpec(draft.id());
      boolean code = spec != null && !published;
      entries.add(new AbilityEntry(draft.id(), name, AbilitySource.DRAFT, published, code, draft));
    }

    for (String abilityId : engine.abilityIds()) {
      if (draftMap.containsKey(abilityId)) {
        continue;
      }
      boolean published = yamlIds.contains(abilityId);
      AbilitySpec spec = engine.abilitySpec(abilityId);
      String name = spec == null ? abilityId : firstNonBlank(spec.name(), abilityId);
      AbilitySource source = published ? AbilitySource.YAML : AbilitySource.CODE;
      boolean code = !published;
      entries.add(new AbilityEntry(abilityId, name, source, published, code, null));
    }

    entries.sort(Comparator.comparing((AbilityEntry e) -> e.name.toLowerCase(Locale.ROOT)));
    return entries;
  }

  private org.bukkit.inventory.ItemStack entryItem(Player player, AbilityEntry entry) {
    List<Component> lore = new ArrayList<>();
    lore.add(GuiMini.mm("<gray>ID:</gray> <white>" + entry.id + "</white>"));
    List<String> activations = entry.draft != null
        ? activationsFromDraft(entry.draft)
        : activationsFromSpec(services.engine().abilitySpec(entry.id));
    lore.add(GuiMini.mm("<gray>Activation:</gray> <white>" + formatActivation(activations) + "</white>"));
    if (entry.draft != null) {
      lore.add(GuiMini.mm("<green>Draft</green>"));
    }
    if (entry.published) {
      lore.add(GuiMini.mm("<gold>Published</gold>"));
    }
    if (entry.code) {
      lore.add(GuiMini.mm("<gray>Code ability</gray>"));
    }
    EditorLockManager.LockInfo lock = services.locks().lockInfo(entry.id);
    if (lock != null && !lock.ownerId().equals(player.getUniqueId())) {
      lore.add(GuiMini.mm("<red>Locked by:</red> <white>" + lock.ownerName() + "</white>"));
    }
    return GuiItem.of(entry.draft != null ? Material.BOOK : Material.PAPER)
        .displayName(render(entry.name))
        .lore(lore)
        .build();
  }

  private void openEntry(Player player, AbilityEntry entry) {
    if (!services.access().canEditAbility(player, entry.id, services.yaml(), services.engine())) {
      player.sendMessage(Component.text("§cYou cannot edit this ability."));
      return;
    }
    EditorLockManager.LockResult lock = services.locks().tryLock(entry.id, player);
    if (!lock.acquired()) {
      player.sendMessage(Component.text("§cAbility is locked by " + lock.lock().ownerName()));
      return;
    }

    if (entry.draft != null) {
      openDraft(player, entry.draft);
      return;
    }

    EditorAbilityDraft draft = null;
    if (entry.source == AbilitySource.YAML && importer != null) {
      draft = importer.importAbility(entry.id).orElse(null);
    }

    if (draft == null && entry.source == AbilitySource.CODE) {
      draft = services.drafts().create(entry.id);
      AbilitySpec spec = services.engine().abilitySpec(entry.id);
      if (spec != null) {
        if (spec.name() != null) {
          draft.abilitySection().set("name", spec.name());
        }
        if (spec.description() != null) {
          draft.abilitySection().set("description", spec.description());
        }
        if (spec.cooldownTicks() != null) {
          ConfigurationSection cooldown = draft.abilitySection().createSection("cooldown");
          cooldown.set("ticks", spec.cooldownTicks());
          if (spec.cooldownKey() != null) {
            cooldown.set("key", spec.cooldownKey());
          }
        }
      }
      services.drafts().save(draft);
    }

    if (draft == null) {
      player.sendMessage(Component.text("§cUnable to open draft for this ability."));
      services.locks().release(entry.id, player.getUniqueId());
      return;
    }

    services.audit().log(EditorAuditEvent.of(EditorAuditAction.CREATE, player.getUniqueId(), player.getName(), entry.id, "import"));
    list.invalidateAll();
    openDraft(player, draft);
  }

  private void openDraft(Player player, EditorAbilityDraft draft) {
    EditorAbilityDetailMenu detail = new EditorAbilityDetailMenu(services, draft, () -> list.invalidateAll());
    openSubWindow(player, detail);
  }

  private static Component render(String raw) {
    if (raw == null) {
      return Component.text("(unnamed)");
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

  private static List<String> activationsFromDraft(EditorAbilityDraft draft) {
    List<Map<String, Object>> triggers = EditorAbilityYaml.triggers(draft);
    java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
    for (Map<String, Object> trig : triggers) {
      String type = String.valueOf(trig.getOrDefault("type", "interact"));
      if (!"interact".equalsIgnoreCase(type) && !"item_bind".equalsIgnoreCase(type) && !"item-bind".equalsIgnoreCase(type)) {
        continue;
      }
      String click = String.valueOf(trig.getOrDefault("click", "RIGHT_CLICK"));
      boolean sneaking = Boolean.parseBoolean(String.valueOf(trig.getOrDefault("requireSneaking", false)));
      labels.add(formatActivation(click, sneaking));
    }
    return labels.isEmpty() ? List.of() : List.copyOf(labels);
  }

  private static List<String> activationsFromSpec(AbilitySpec spec) {
    if (spec == null) {
      return List.of();
    }
    java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
    for (InteractBinding binding : spec.interactBindings()) {
      String click = binding.trigger() == InteractTrigger.LEFT_CLICK ? "LEFT_CLICK" : "RIGHT_CLICK";
      labels.add(formatActivation(click, binding.requireSneaking()));
    }
    return labels.isEmpty() ? List.of() : List.copyOf(labels);
  }

  private static String formatActivation(List<String> activations) {
    if (activations == null || activations.isEmpty()) {
      return "Passive/Manual";
    }
    return String.join(", ", activations);
  }

  private static String formatActivation(String click, boolean sneaking) {
    String base = "LEFT_CLICK".equalsIgnoreCase(click) ? "Left Click" : "Right Click";
    return sneaking ? "Shift+" + base : base;
  }
}
