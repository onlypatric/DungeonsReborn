package dev.patric.dungeonsreborn.effects.config;

import static dev.patric.dungeonsreborn.effects.config.YamlErrors.*;
import static dev.patric.dungeonsreborn.effects.config.YamlReaders.*;
import static dev.patric.dungeonsreborn.effects.config.YamlValueParsers.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.Vibration;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.datacomponent.DataComponentTypes;

import dev.patric.dungeonsreborn.DungeonsRebornPlugin;
import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.CastContext;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.actions.ActionHandle;
import dev.patric.dungeonsreborn.effects.actions.ActionWithHandle;
import dev.patric.dungeonsreborn.effects.actions.Actions;
import dev.patric.dungeonsreborn.effects.actions.EntityActions;
import dev.patric.dungeonsreborn.effects.particles.Frame;
import dev.patric.dungeonsreborn.effects.particles.Frames;
import dev.patric.dungeonsreborn.effects.anim.Easings;
import dev.patric.dungeonsreborn.effects.conditions.Conditions;
import dev.patric.dungeonsreborn.effects.costs.Costs;
import dev.patric.dungeonsreborn.effects.damage.DamageAmountMode;
import dev.patric.dungeonsreborn.effects.damage.DamageCause;
import dev.patric.dungeonsreborn.effects.damage.DamageSpec;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.heal.HealType;
import dev.patric.dungeonsreborn.effects.projectile.ProjectileSpec;
import dev.patric.dungeonsreborn.effects.Vars;
import dev.patric.dungeonsreborn.effects.combat.CombatCooldownScope;
import dev.patric.dungeonsreborn.effects.combat.CombatEventBinding;
import dev.patric.dungeonsreborn.effects.combat.CombatEventFilters;
import dev.patric.dungeonsreborn.effects.combat.CombatEventSource;
import dev.patric.dungeonsreborn.effects.combat.CombatEventTargetBind;
import dev.patric.dungeonsreborn.effects.combat.CombatEventType;
import dev.patric.dungeonsreborn.effects.config.actions.ActionParserContext;
import dev.patric.dungeonsreborn.effects.config.actions.ActionParsers;
import dev.patric.dungeonsreborn.effects.targeting.Targeters;
import dev.patric.dungeonsreborn.effects.targeting.Targeter;
import dev.patric.dungeonsreborn.effects.integration.EffectsBindings;
import dev.patric.dungeonsreborn.effects.integration.InteractBinding;
import dev.patric.dungeonsreborn.effects.integration.InteractTrigger;
import dev.patric.dungeonsreborn.effects.integration.ItemMatcher;
import dev.patric.dungeonsreborn.effects.integration.ItemMatchers;
import dev.patric.dungeonsreborn.effects.items.ItemConsumeMode;
import dev.patric.dungeonsreborn.effects.integration.PassiveBinding;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateCompiler;
import dev.patric.dungeonsreborn.effects.items.ItemTemplateSnapshot;
import dev.patric.dungeonsreborn.effects.items.ItemAffixPool;
import dev.patric.dungeonsreborn.effects.items.ItemAffixRoll;
import dev.patric.dungeonsreborn.effects.items.ItemHookSpec;
import dev.patric.dungeonsreborn.effects.items.ItemHookType;
import dev.patric.dungeonsreborn.effects.items.ItemStatBlock;
import dev.patric.dungeonsreborn.effects.items.ItemTierSpec;
import dev.patric.dungeonsreborn.effects.editor.EditorItemLore;
import dev.patric.dungeonsreborn.effects.editor.EditorItemYaml;
import dev.patric.dungeonsreborn.effects.minions.MinionManager;
import dev.patric.dungeonsreborn.party.Party;
import dev.patric.dungeonsreborn.party.PartyService;
import dev.patric.dungeonsreborn.progression.PlayerProgression;
import dev.patric.dungeonsreborn.progression.ProgressionService;
import dev.patric.dungeonsreborn.progression.custom.CustomXpProfile;
import dev.patric.dungeonsreborn.quests.QuestRegion;
import dev.patric.dungeonsreborn.effects.minions.MinionScaling;
import dev.patric.dungeonsreborn.effects.minions.MinionSpec;
import dev.patric.dungeonsreborn.effects.minions.MinionMode;
import dev.patric.dungeonsreborn.effects.minions.MinionOwnerScalingSpec;
import dev.patric.dungeonsreborn.effects.minions.MinionPassiveSpec;
import dev.patric.dungeonsreborn.effects.minions.MinionScalingLimits;
import dev.patric.dungeonsreborn.effects.minions.MinionSpecialAttackSpec;
import dev.patric.dungeonsreborn.effects.minions.MinionTargetRules;
import dev.patric.dungeonsreborn.effects.minions.MinionFormation;
import dev.patric.dungeonsreborn.effects.minions.MinionSummonSpec;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.logging.ServiceLogManager;
import dev.patric.dungeonsreborn.mobs.MobParticlesSpec;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.PluginResources;
import dev.patric.dungeonsreborn.util.YamlValues;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import java.time.Duration;

/**
 * Phase-1 YAML ability loader/compiler.
 * <p>
 * This is intentionally limited and lives in the plugin layer (not the core engine).
 */
public final class EffectsYamlAbilities {
  private static final int MAX_REPEAT_TIMES = 10000;
  public record ReloadResult(int loadedAbilities, int loadedItemBindings, List<String> errors) {
  }

  public record LintResult(int scripts, List<String> errors) {
  }

  private record AbilityEntry(String normalizedId, String basePath, ConfigurationSection section) {
  }

  private record ItemTemplate(
      String id,
      ItemStack item,
      ItemStack matchBase,
      int version,
      ItemTemplateCompiler.DurabilityRange durabilityRange,
      ItemStatBlock baseStats,
      ItemAffixPool affixPool,
      ItemTierSpec tierSpec,
      java.util.Map<ItemHookType, java.util.List<ItemHookSpec>> hooks) {
  }

  private record ShapeTemplate(List<PointSpec> points, List<List<PointSpec>> triangles) {
  }

  public enum VarScope {
    CAST,
    PLAYER,
    ENTITY,
    ABILITY
  }

  private final JavaPlugin plugin;
  private final EffectsEngine engine;
  private final EffectsBindings bindings;
  private final ServiceLogger effectsLog;
  private final ServiceLogger bindingsLog;
  private final Set<String> loadedAbilityIds = new HashSet<>();
  private final Set<String> loadedBindingIds = new HashSet<>();
  private final java.util.Map<String, AbilitySpec> overriddenCodeAbilities = new java.util.HashMap<>();
  private java.util.Map<String, java.util.Map<String, Object>> macros = java.util.Collections.emptyMap();
  private java.util.Map<String, ShapeTemplate> shapeTemplates = java.util.Collections.emptyMap();
  private final Map<UUID, Map<String, Object>> playerVars = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Object>> entityVars = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> abilityVars = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Long>> playerVarExpirations = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Long>> entityVarExpirations = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Long>> abilityVarExpirations = new ConcurrentHashMap<>();
  private final java.util.Map<String, dev.patric.dungeonsreborn.effects.actions.Action> yamlActionGraphs = new ConcurrentHashMap<>();
  private final java.util.Map<java.nio.file.Path, ScriptCacheEntry> scriptCache = new ConcurrentHashMap<>();
  private final java.util.Map<String, ScriptMetrics> scriptMetrics = new ConcurrentHashMap<>();
  private final java.util.Map<String, ItemTemplate> itemTemplates = new java.util.HashMap<>();
  private volatile List<String> lastItemErrors = List.of();
  private final java.util.Map<String, EffectsEngine.AbilityCombatProfile> abilityCombatProfiles = new java.util.HashMap<>();
  private volatile boolean scriptDebug;
  private volatile boolean scriptTrace;
  private final double globalMinMana;
  private final double globalMinManaPct;
  private final boolean strictCombatSchema;
  private final boolean combatMigrationRequired;
  private static final String YAML_LAST_ENTITY = "yaml_last_entity";
  private static final String YAML_INVOKE_STACK = "yaml_invoke_stack";
  private static final String DSL_ON_HIT = "dsl_on_hit";
  private static final String DSL_ON_FINISH = "dsl_on_finish";
  private static final String DSL_CAST_DONE = "dsl_cast_done";
  private static final String DSL_PENDING = "dsl_pending";
  private static final String DSL_OPS_TICK = "__dsl_ops_tick";
  private static final String DSL_OPS_USED = "__dsl_ops_used";
  private static final String DSL_OPS_WARN_TICK = "__dsl_ops_warn_tick";
  private static final String DSL_ON_COST_FAIL = "dsl_on_cost_fail";
  private static final String DSL_ON_COOLDOWN_FAIL = "dsl_on_cooldown_fail";
  private static final String DSL_MACRO_STACK = "dsl_macro_stack";
  private static final String DSL_PARTICLE_USED = "__dsl_particles_used";
  private static final String DSL_SCRIPT_ID = "__dsl_script_id";
  private static final int MAX_CAST_VARS = 128;
  private static final int MAX_PLAYER_VARS = 256;
  private static final int MAX_ENTITY_VARS = 256;
  private static final int MAX_ABILITY_VARS = 256;
  private static final int SCRIPT_VERSION = 1;
  private static final int EFFECTS_SCHEMA_VERSION = 1;
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final String ITEM_MARKER_START = "[dr:effects]";
  private static final String ITEM_MARKER_END = "[/dr:effects]";
  private static final String DEFAULT_UNSAFE_PERMISSION = "dungeonsreborn.effects.unsafe";
  private static final java.util.Set<String> UNSAFE_ACTION_TYPES = java.util.Set.of(
      "damage",
      "damage_typed",
      "damage_percent",
      "damage_true",
      "damage_falloff",
      "damage_crit",
      "damage_lifesteal",
      "damage_dot",
      "damage_over_time",
      "damage_chain",
      "chain_damage",
      "chain_lightning",
      "knockback",
      "ignite",
      "minion_summon",
      "summon_minion",
      "minions");
  private static final Map<String, String> DEPRECATED_ACTION_KEYS = java.util.Map.of(
      "delay", "delayTicks",
      "period", "periodTicks",
      "ticks", "durationTicks",
      "ttl", "ttlTicks",
      "cooldown", "cooldownTicks");
  private static final Map<String, String> DEPRECATED_DSL_STATEMENTS = java.util.Map.of(
      "strike_lightning", "lightning",
      "chain_lightning", "damage_chain");
  private static final java.util.Set<String> REMOVED_DSL_STATEMENTS = java.util.Set.of(
      "dash");
  private static final java.util.Set<String> LEGACY_COMBAT_ACTION_KEYS = java.util.Set.of(
      "armor_pen_flat",
      "armor_pen_pct",
      "resist_pen_pct",
      "vulnerability_tag",
      "crit_chance",
      "crit_multiplier",
      "min_damage_floor",
      "mitigation_profile",
      "pipeline_tags",
      "snapshot_at_cast");
  private final java.util.Set<String> deprecatedWarnings = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private String unsafePermission;
  public enum EasingId {
    LINEAR,
    IN_OUT_CUBIC,
    OUT_QUAD
  }

  public EffectsYamlAbilities(JavaPlugin plugin, EffectsEngine engine) {
    this(plugin, engine, null,
        ServiceLogManager.fromConfig(plugin).effects(),
        ServiceLogManager.fromConfig(plugin).bindings());
  }

  public EffectsYamlAbilities(JavaPlugin plugin, EffectsEngine engine, EffectsBindings bindings, ServiceLogger effectsLog, ServiceLogger bindingsLog) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.bindings = bindings;
    this.effectsLog = Objects.requireNonNull(effectsLog, "effectsLog");
    this.bindingsLog = Objects.requireNonNull(bindingsLog, "bindingsLog");
    this.globalMinMana = plugin.getConfig().getDouble("mana.minToCast", 0.0);
    this.globalMinManaPct = plugin.getConfig().getDouble("mana.minToCastPct", 0.0);
    this.strictCombatSchema = plugin.getConfig().getBoolean("effects.combat.strictSchema", false);
    this.combatMigrationRequired = plugin.getConfig().getBoolean("effects.combat.migration.required", false);
  }

  public File file() {
    return new File(plugin.getDataFolder(), "effects.yml");
  }

  public File abilitiesDir() {
    return new File(plugin.getDataFolder(), "effects/abilities");
  }

  public EffectsCombatMigrator.MigrationReport migrateCombatSchema(boolean createBackups) {
    return EffectsCombatMigrator.migrate(file(), abilitiesDir(), createBackups);
  }

  public File itemsDir() {
    return new File(plugin.getDataFolder(), "effects/items");
  }

  private final ActionParserContext actionParserContext = new ActionParserContextImpl();

  private final class ActionParserContextImpl implements ActionParserContext {
    @Override
    public Map<String, Object> macro(String id) {
      return macros.get(id);
    }

    @Override
    public dev.patric.dungeonsreborn.effects.actions.Action compileAction(Map<String, Object> node, String path,
        java.util.ArrayDeque<String> includeStack) {
      return EffectsYamlAbilities.this.compileAction(node, path, includeStack);
    }

    @Override
    public dev.patric.dungeonsreborn.effects.conditions.Condition compileCondition(Object raw, String path) {
      return EffectsYamlAbilities.this.compileCondition(raw, path);
    }

    @Override
    public dev.patric.dungeonsreborn.effects.actions.Action findYamlActionGraph(String abilityId) {
      return yamlActionGraphs.get(abilityId);
    }

    @Override
    public Object require(Map<String, Object> node, String key, String path) {
      return YamlReaders.require(node, key, path);
    }

    @Override
    public Map<String, Object> castMap(Object raw, String path) {
      return YamlReaders.castMap(raw, path);
    }

    @Override
    public List<?> mapList(Map<String, Object> node, String key, String path) {
      return YamlReaders.mapList(node, key, path);
    }

    @Override
    public String requireString(Map<String, Object> node, String key, String path) {
      return YamlReaders.requireString(node, key, path);
    }

    @Override
    public String string(Map<String, Object> node, String key, String def) {
      return YamlReaders.string(node, key, def);
    }

    @Override
    public boolean bool(Map<String, Object> node, String key, boolean def) {
      return YamlReaders.bool(node, key, def);
    }

    @Override
    public int intValue(Map<String, Object> node, String key, int def) {
      return YamlReaders.intValue(node, key, def);
    }

    @Override
    public NumValue numValue(Map<String, Object> node, String key, double def, String path) {
      return EffectsYamlAbilities.this.numValue(node, key, def, path);
    }

    @Override
    public NumValue requireNumValue(Map<String, Object> node, String key, String path) {
      return EffectsYamlAbilities.this.requireNumValue(node, key, path);
    }

    @Override
    public EasingId easingId(Map<String, Object> node, String path) {
      String raw = YamlReaders.string(node, "easing", EasingId.IN_OUT_CUBIC.name());
      try {
        return EasingId.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (Exception ex) {
        return EasingId.IN_OUT_CUBIC;
      }
    }

    @Override
    public java.util.function.DoubleUnaryOperator easingFromId(EasingId id) {
      return switch (id) {
        case LINEAR -> Easings::linear;
        case IN_OUT_CUBIC -> Easings::inOutCubic;
        case OUT_QUAD -> Easings::outQuad;
      };
    }

    @Override
    public VarScope parseVarScope(String raw, String path, VarScope def) {
      return EffectsYamlAbilities.parseVarScope(raw, path, def);
    }

    @Override
    public ValueSupplier varValue(Object raw, String path) {
      return EffectsYamlAbilities.this.varValue(raw, path);
    }

    @Override
    public Map<String, Object> vars(CastContext ctx, VarScope scope) {
      return EffectsYamlAbilities.this.vars(ctx, scope);
    }

    @Override
    public Map<String, Long> varExpirations(CastContext ctx, VarScope scope) {
      return EffectsYamlAbilities.this.varExpirations(ctx, scope);
    }

    @Override
    public void setVar(CastContext ctx, VarScope scope, String key, Object value) {
      EffectsYamlAbilities.this.setVar(ctx, scope, key, value);
    }

    @Override
    public void setVar(CastContext ctx, VarScope scope, String key, Object value, Long ttlTicks) {
      EffectsYamlAbilities.this.setVar(ctx, scope, key, value, ttlTicks);
    }

    @Override
    public long evalTtlTicks(NumValue ttl, CastContext ctx) {
      return EffectsYamlAbilities.this.evalTtlTicks(ttl, ctx);
    }

    @SuppressWarnings("static-access")
    @Override
    public long evalLong(NumValue value, CastContext ctx) {
      return EffectsYamlAbilities.this.evalLong(value, ctx);
    }

    @SuppressWarnings("static-access")
    @Override
    public int evalInt(NumValue value, CastContext ctx) {
      return EffectsYamlAbilities.this.evalInt(value, ctx);
    }

    @SuppressWarnings("static-access")
    @Override
    public double evalDouble(NumValue value, CastContext ctx) {
      return EffectsYamlAbilities.this.evalDouble(value, ctx);
    }

    @Override
    public double numericVar(Object raw, double def) {
      return EffectsYamlAbilities.numericVar(raw, def);
    }

    @Override
    public ActionHandle scheduledHandle(EffectsEngine.ScheduledHandle handle, AtomicBoolean done) {
      return EffectsYamlAbilities.scheduledHandle(handle, done);
    }

    @Override
    public LivingEntity lastEntity(CastContext ctx) {
      return EffectsYamlAbilities.lastEntity(ctx);
    }

    @Override
    public Player targetPlayer(CastContext ctx) {
      return EffectsYamlAbilities.targetPlayer(ctx);
    }

    @Override
    public CastContext followCasterContext(CastContext ctx) {
      return EffectsYamlAbilities.followCasterContext(ctx);
    }

    @Override
    public Location resolveAtWithOffsets(CastContext ctx, Object atMode, NumValue forward, NumValue right, NumValue up) {
      return EffectsYamlAbilities.resolveAtWithOffsets(ctx, (AtMode) atMode, forward, right, up);
    }

    @Override
    public Location resolveAtWithEntity(CastContext ctx, Object atMode) {
      return EffectsYamlAbilities.resolveAtWithEntity(ctx, (AtMode) atMode);
    }

    @Override
    public Object parseAt(String raw, String path) {
      return EffectsYamlAbilities.parseAt(raw, path);
    }

    @Override
    public String yamlLastEntityKey() {
      return YAML_LAST_ENTITY;
    }

    @Override
    public String yamlInvokeStackKey() {
      return YAML_INVOKE_STACK;
    }

    @Override
    public void withTempVar(CastContext ctx, VarScope scope, String key, Object value, Runnable task) {
      EffectsYamlAbilities.this.withTempVar(ctx, scope, key, value, task);
    }

    @Override
    public void withTempVars(CastContext ctx, VarScope scope, Map<String, Object> values, Runnable task) {
      EffectsYamlAbilities.this.withTempVars(ctx, scope, values, task);
    }

    @Override
    public net.kyori.adventure.text.Component renderText(String raw, CastContext ctx) {
      return EffectsYamlAbilities.this.renderText(raw, ctx);
    }

    @Override
    public Actions.MotionMode parseMotionMode(String raw, String path) {
      return EffectsYamlAbilities.parseMotionMode(raw, path);
    }

    @Override
    public Sound soundValue(Map<String, Object> node, String key, String path) {
      return EffectsYamlAbilities.soundValue(node, key, path);
    }
  }

  private boolean checkSchemaVersion(String sourcePath, int schemaVersion, List<String> errors) {
    if (schemaVersion > EFFECTS_SCHEMA_VERSION) {
      errors.add(sourcePath + ": Unsupported schemaVersion=" + schemaVersion + " (max " + EFFECTS_SCHEMA_VERSION + ")");
      return false;
    }
    if (schemaVersion < EFFECTS_SCHEMA_VERSION) {
      warnDeprecatedOnce(
          "schemaVersion:" + sourcePath,
          sourcePath + ": schemaVersion=" + schemaVersion + " is older than supported version " + EFFECTS_SCHEMA_VERSION + "; consider updating");
    }
    return true;
  }

  private void warnDeprecatedOnce(String key, String message) {
    if (!deprecatedWarnings.add(key)) {
      return;
    }
    effectsLog.warn("[Effects] " + message);
  }

  private void migrateDeprecatedKeys(Map<String, Object> node, String path, Map<String, String> deprecatedKeys) {
    for (var entry : deprecatedKeys.entrySet()) {
      String oldKey = entry.getKey();
      if (!node.containsKey(oldKey)) {
        continue;
      }
      if (combatMigrationRequired && LEGACY_COMBAT_ACTION_KEYS.contains(oldKey)) {
        throw new IllegalArgumentException(path + "." + oldKey
            + " is a legacy combat key and migration is required (run /dr effects combat migrate)");
      }
      String newKey = entry.getValue();
      if (node.containsKey(newKey)) {
        warnDeprecatedOnce(path + "." + oldKey, path + "." + oldKey + " is deprecated and ignored because " + newKey + " is already set");
        node.remove(oldKey);
        continue;
      }
      Object value = node.remove(oldKey);
      node.put(newKey, value);
      warnDeprecatedOnce(path + "." + oldKey, path + "." + oldKey + " is deprecated; use " + newKey);
    }
  }

  public ItemStack itemTemplate(String id) {
    if (id == null) {
      return null;
    }
    ItemTemplate template = itemTemplates.get(dev.patric.dungeonsreborn.effects.Ids.normalize(id));
    if (template == null) {
      return null;
    }
    ItemStack item = template.item().clone();
    if (template.durabilityRange() != null) {
      ItemTemplateCompiler.applyDurabilityRange(item, template.durabilityRange(), java.util.concurrent.ThreadLocalRandom.current());
    }
    ItemStatBlock baseStats = template.baseStats();
    ItemTierSpec tierSpec = template.tierSpec();
    if (baseStats != null && tierSpec != null) {
      baseStats = tierSpec.apply(baseStats);
    }
    ItemAffixPool affixPool = template.affixPool();
    if (affixPool != null) {
      List<ItemAffixRoll> affixes = affixPool.roll(java.util.concurrent.ThreadLocalRandom.current());
      ItemStatBlock affixStats = ItemStatBlock.empty();
      List<String> affixIds = new ArrayList<>();
      for (ItemAffixRoll roll : affixes) {
        affixIds.add(roll.id());
        affixStats = affixStats.merge(roll.stats());
      }
      ItemStatBlock combined = baseStats == null ? affixStats : baseStats.merge(affixStats);
      if (!combined.isEmpty()) {
        ItemMarkers.setItemStats(item, combined.values());
      }
      if (!affixIds.isEmpty()) {
        ItemMarkers.setItemAffixes(item, affixIds);
      }
    } else if (baseStats != null && !baseStats.isEmpty()) {
      ItemMarkers.setItemStats(item, baseStats.values());
    }
    return item;
  }

  public ItemTemplateSnapshot itemTemplateSnapshot(String id) {
    if (id == null) {
      return null;
    }
    ItemTemplate template = itemTemplates.get(dev.patric.dungeonsreborn.effects.Ids.normalize(id));
    if (template == null) {
      return null;
    }
    ItemStack base = template.item() == null ? null : template.item().clone();
    ItemStack matchBase = template.matchBase() == null ? null : template.matchBase().clone();
    String rarityId = base == null ? null : ItemMarkers.getItemRarity(base);
    return new ItemTemplateSnapshot(
        template.id(),
        template.version(),
        base,
        matchBase,
        template.durabilityRange(),
        template.baseStats(),
        template.affixPool(),
        template.tierSpec(),
        rarityId,
        template.hooks());
  }

  public List<String> itemTemplateErrors() {
    return lastItemErrors;
  }

  public java.util.List<ItemHookSpec> itemHooks(Player player, ItemStack item, ItemHookType type) {
    if (item == null || type == null) {
      return java.util.List.of();
    }
    ItemTemplate template = findTemplate(player, item);
    if (template == null) {
      return java.util.List.of();
    }
    java.util.List<ItemHookSpec> hooks = template.hooks().get(type);
    return hooks == null ? java.util.List.of() : hooks;
  }

  public Set<String> loadedItemIds() {
    return java.util.Collections.unmodifiableSet(itemTemplates.keySet());
  }

  public Set<String> loadedAbilityIds() {
    return java.util.Collections.unmodifiableSet(loadedAbilityIds);
  }

  public boolean isScriptDebugEnabled() {
    return scriptDebug;
  }

  public void setScriptDebug(boolean enabled) {
    this.scriptDebug = enabled;
  }

  public boolean isScriptTraceEnabled() {
    return scriptTrace;
  }

  public void setScriptTrace(boolean enabled) {
    this.scriptTrace = enabled;
  }

  public void clearPlayerVars(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    playerVars.remove(playerId);
  }

  public void clearEntityVars(UUID entityId) {
    Objects.requireNonNull(entityId, "entityId");
    entityVars.remove(entityId);
  }

  public ReloadResult reload() {
    plugin.getDataFolder().mkdirs();
    new java.io.File(plugin.getDataFolder(), "effects/scripts").mkdirs();
    boolean cancelRunningOnReload = YamlConfiguration.loadConfiguration(file()).getBoolean("options.cancelRunningOnReload", false);
    // Provide a starter file for iteration.
    PluginResources.ensureYamlFile(plugin, file(), "effects.yml", cfg -> {
      cfg.set("schemaVersion", 1);
      cfg.createSection("options");
      cfg.createSection("macros");
      cfg.createSection("abilities");
    }, plugin.getLogger(), "Effects");

    if (cancelRunningOnReload && !loadedAbilityIds.isEmpty()) {
      Set<String> previous = new HashSet<>(loadedAbilityIds);
      int cancelled = engine.cancelCasts(r -> previous.contains(r.abilityId()), true);
      if (cancelled > 0) {
        effectsLog.info("[Effects] YAML cancelled " + cancelled + " running casts on reload");
      }
    }

    Set<String> previousLoadedAbilityIds = new HashSet<>(loadedAbilityIds);
    @SuppressWarnings("unused")
    Set<String> previousLoadedBindingIds = new HashSet<>(loadedBindingIds);
    Map<String, AbilitySpec> previousYamlAbilities = new HashMap<>();
    for (String id : previousLoadedAbilityIds) {
      AbilitySpec spec = engine.abilitySpec(id);
      if (spec != null) {
        previousYamlAbilities.put(id, spec);
      }
    }
    @SuppressWarnings("unused")
    Map<String, AbilitySpec> previousOverridden = new HashMap<>(overriddenCodeAbilities);
    @SuppressWarnings("unused")
    Map<String, ItemTemplate> previousTemplates = new HashMap<>(itemTemplates);
    @SuppressWarnings("unused")
    Map<String, dev.patric.dungeonsreborn.effects.actions.Action> previousYamlGraphs = new HashMap<>(yamlActionGraphs);
    @SuppressWarnings("unused")
    Map<String, java.util.Map<String, Object>> previousMacros = macros;
    @SuppressWarnings("unused")
    List<InteractBinding> previousInteractBindings = bindings == null ? java.util.List.of() : new ArrayList<>(bindings.interactBindings());
    @SuppressWarnings("unused")
    List<PassiveBinding> previousPassiveBindings = bindings == null ? java.util.List.of() : new ArrayList<>(bindings.passiveBindings());

    // Unregister previously-loaded abilities (YAML owned).
    for (String id : loadedAbilityIds) {
      engine.unregisterAbility(id);
    }
    loadedAbilityIds.clear();

    // Restore any code-defined abilities that were overridden by YAML.
    for (var entry : overriddenCodeAbilities.entrySet()) {
      if (!engine.hasAbility(entry.getKey())) {
        engine.registerAbility(entry.getValue());
      }
    }

    // Unregister previously-loaded YAML bindings.
    if (bindings != null) {
      for (String id : loadedBindingIds) {
        bindings.unregister(id);
        bindings.unregisterPassive(id);
        bindings.unregisterEvent(id);
        bindings.unregisterCombatEvent(id);
      }
    }
    loadedBindingIds.clear();
    yamlActionGraphs.clear();
    abilityCombatProfiles.clear();

    ArrayList<String> errors = new ArrayList<>();
    java.util.ArrayList<java.util.Map.Entry<String, YamlConfiguration>> sources = new java.util.ArrayList<>();
    sources.add(java.util.Map.entry(file().getPath(), YamlConfiguration.loadConfiguration(file())));

    File dir = abilitiesDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
    ensureDefaultAbilities(dir);
    File[] extra = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (extra != null) {
      java.util.Arrays.sort(extra, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File f : extra) {
        sources.add(java.util.Map.entry(f.getPath(), YamlConfiguration.loadConfiguration(f)));
      }
    }
    File[] folders = dir.listFiles(File::isDirectory);
    if (folders != null) {
      java.util.Arrays.sort(folders, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File folder : folders) {
        File abilityFile = new File(folder, "ability.yml");
        if (!abilityFile.exists()) {
          abilityFile = new File(folder, "ability.yaml");
        }
        if (abilityFile.exists()) {
          sources.add(java.util.Map.entry(abilityFile.getPath(), YamlConfiguration.loadConfiguration(abilityFile)));
        }
      }
    }

    java.util.LinkedHashMap<String, AbilityEntry> all = new java.util.LinkedHashMap<>();
    java.util.HashMap<String, java.util.Map<String, Object>> macroTmp = new java.util.HashMap<>();
    java.util.HashMap<String, ShapeTemplate> shapeTmp = new java.util.HashMap<>();

    for (var source : sources) {
      String sourcePath = source.getKey();
      YamlConfiguration cfg = source.getValue();

      int schemaVersion = cfg.getInt("schemaVersion", EFFECTS_SCHEMA_VERSION);
      if (!checkSchemaVersion(sourcePath, schemaVersion, errors)) {
        continue;
      }

      // Optional macros (Phase 2): reusable action nodes.
      ConfigurationSection macrosSec = cfg.getConfigurationSection("macros");
      if (macrosSec != null) {
        for (String key : macrosSec.getKeys(false)) {
          if (macroTmp.containsKey(key)) {
            String message = sourcePath + ": macros." + key + ": duplicate macro id";
            errors.add(message);
            effectsLog.warn("[Effects] " + message);
            continue;
          }
          ConfigurationSection m = macrosSec.getConfigurationSection(key);
          if (m == null) {
            errors.add(sourcePath + ": macros." + key + ": must be an object");
            continue;
          }
          macroTmp.put(key, normalizeMap(m.getValues(false)));
        }
      }

      ConfigurationSection shapesSec = cfg.getConfigurationSection("shapes");
      if (shapesSec != null) {
        for (String key : shapesSec.getKeys(false)) {
          if (shapeTmp.containsKey(key)) {
            String message = sourcePath + ": shapes." + key + ": duplicate shape id";
            errors.add(message);
            effectsLog.warn("[Effects] " + message);
            continue;
          }
          ConfigurationSection s = shapesSec.getConfigurationSection(key);
          if (s == null) {
            errors.add(sourcePath + ": shapes." + key + ": must be an object");
            continue;
          }
          try {
            shapeTmp.put(key, parseShapeTemplate(normalizeMap(s.getValues(false)), sourcePath + ": shapes." + key));
          } catch (Exception ex) {
            errors.add(sourcePath + ": shapes." + key + ": " + ex.getMessage());
          }
        }
      }

      ConfigurationSection abilities = cfg.getConfigurationSection("abilities");
      if (abilities == null) {
        continue;
      }

      for (String id : abilities.getKeys(false)) {
        String normalizedId;
        try {
          normalizedId = dev.patric.dungeonsreborn.effects.Ids.normalize(id);
        } catch (Exception ex) {
          errors.add(sourcePath + ": abilities." + id + ": invalid id (" + ex.getMessage() + ")");
          continue;
        }
        if (all.containsKey(normalizedId)) {
          String message = sourcePath + ": abilities." + id + ": duplicate ability id (normalized=" + normalizedId + ")";
          errors.add(message);
          effectsLog.warn("[Effects] " + message);
          continue;
        }

        ConfigurationSection a = abilities.getConfigurationSection(id);
        if (a == null) {
          errors.add(sourcePath + ": abilities." + id + ": must be an object");
          continue;
        }

        all.put(normalizedId, new AbilityEntry(normalizedId, sourcePath + ": abilities." + id, a));
      }
    }

    macros = java.util.Collections.unmodifiableMap(macroTmp);
    shapeTemplates = java.util.Collections.unmodifiableMap(shapeTmp);

    warmScriptCache(errors);
    int loaded = 0;
    int loadedItemBindings = 0;
    for (AbilityEntry entry : all.values()) {
      try {
        AbilitySpec spec = compileAbility(entry.normalizedId(), entry.basePath(), entry.section());
        // Phase 0 merge policy: YAML overrides code-defined abilities.
        AbilitySpec existing = engine.abilitySpec(entry.normalizedId());
        if (existing != null) {
          overriddenCodeAbilities.putIfAbsent(entry.normalizedId(), existing);
          engine.unregisterAbility(entry.normalizedId());
        }
        engine.registerAbility(spec);
        EffectsEngine.AbilityCombatProfile combatProfile = abilityCombatProfiles.get(entry.normalizedId());
        if (combatProfile != null) {
          engine.registerAbilityProfile(entry.normalizedId(), combatProfile);
        }
        if (bindings != null) {
          for (InteractBinding binding : spec.interactBindings()) {
            bindings.register(binding);
            loadedBindingIds.add(binding.id());
          }
        }
        loadedAbilityIds.add(entry.normalizedId());
        loaded++;
      } catch (Exception ex) {
        errors.add(entry.basePath() + ": " + ex.getMessage());
      }
    }

    File itemsDir = itemsDir();
    if (!itemsDir.exists()) {
      itemsDir.mkdirs();
    }
    saveDefaultItemFiles(itemsDir);
    File[] itemFiles = itemsDir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (itemFiles == null) {
      itemFiles = new File[0];
    }
    itemTemplates.clear();
    List<String> itemErrors = new ArrayList<>();
    if (itemFiles != null) {
      java.util.Arrays.sort(itemFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File f : itemFiles) {
        int errorStart = errors.size();
        String fileName = f.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
          errors.add(f.getPath() + ": filename must be <itemId>.yml");
          if (errors.size() > errorStart) {
            itemErrors.addAll(errors.subList(errorStart, errors.size()));
          }
          continue;
        }
        String itemIdRaw = fileName.substring(0, dot);
        String itemId;
        try {
          itemId = dev.patric.dungeonsreborn.effects.Ids.normalize(itemIdRaw);
        } catch (Exception ex) {
          errors.add(f.getPath() + ": invalid item id (" + ex.getMessage() + ")");
          if (errors.size() > errorStart) {
            itemErrors.addAll(errors.subList(errorStart, errors.size()));
          }
          continue;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        int schemaVersion = cfg.getInt("schemaVersion", EFFECTS_SCHEMA_VERSION);
        if (!checkSchemaVersion(f.getPath(), schemaVersion, errors)) {
          if (errors.size() > errorStart) {
            itemErrors.addAll(errors.subList(errorStart, errors.size()));
          }
          continue;
        }
        Map<String, Object> root = normalizeMap(cfg.getValues(false));
        loadItemTemplate(itemId, f.getPath(), cfg, errors);
        loadedItemBindings += compileItemBindings(itemId, f.getPath(), root, errors);
        if (errors.size() > errorStart) {
          itemErrors.addAll(errors.subList(errorStart, errors.size()));
        }
      }
    }
    lastItemErrors = List.copyOf(itemErrors);

    if (!errors.isEmpty()) {
      effectsLog.warn("[Effects] YAML reload had " + errors.size() + " errors (some abilities/bindings may be missing)");
      for (String e : errors) {
        if (isBindingError(e)) {
          bindingsLog.warn("[Bindings] YAML: " + e);
        } else {
          effectsLog.warn("[Effects] YAML: " + e);
        }
      }
    }
    effectsLog.info("[Effects] YAML loaded " + loaded + " abilities");
    bindingsLog.info("[Bindings] YAML loaded " + loadedItemBindings + " item bindings");
    SystemStatusStore.get().record(
        "effects",
        "Effects",
        file().getPath(),
        "abilities=" + loaded,
        errors);
    SystemStatusStore.get().record(
        "bindings",
        "Bindings",
        itemsDir.getPath(),
        "itemBindings=" + loadedItemBindings,
        errors);
    return new ReloadResult(
        loaded,
        loadedItemBindings,
        errors);
  }

  private void ensureDefaultAbilities(File dir) {
    List<String> entries = readResourceIndex("effects/abilities/index.txt");
    if (entries.isEmpty()) {
      entries = listResourceDirectory("effects/abilities/");
    }
    for (String entry : entries) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      if (!trimmed.endsWith(".yml") && !trimmed.endsWith(".yaml")) {
        continue;
      }
      File out = new File(dir, trimmed);
      if (out.exists()) {
        continue;
      }
      String resourcePath = "effects/abilities/" + trimmed;
      if (!PluginResources.saveResourceIfPresent(plugin, resourcePath, false)) {
        effectsLog.warn("[Effects] Missing bundled ability: " + resourcePath + " (skipping copy)");
      }
    }
  }

  private List<String> listResourceDirectory(String prefix) {
    List<String> entries = new ArrayList<>();
    try (JarFile jar = new JarFile(resolvePluginJar())) {
      Enumeration<JarEntry> jarEntries = jar.entries();
      while (jarEntries.hasMoreElements()) {
        JarEntry entry = jarEntries.nextElement();
        String name = entry.getName();
        if (entry.isDirectory()) {
          continue;
        }
        if (!name.startsWith(prefix)) {
          continue;
        }
        if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
          continue;
        }
        entries.add(name.substring(prefix.length()));
      }
    } catch (Exception ex) {
      effectsLog.warn("[Effects] Unable to scan " + prefix + ": " + ex.getMessage());
    }
    return entries;
  }

  private File resolvePluginJar() throws Exception {
    var location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
    return new File(location.toURI());
  }

  private List<String> readResourceIndex(String path) {
    InputStream stream = plugin.getResource(path);
    if (stream == null) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    } catch (Exception ex) {
      effectsLog.warn("[Effects] Unable to read " + path + ": " + ex.getMessage());
      return List.of();
    }
    return lines;
  }

  public record ItemMigrationReport(int updatedTotal, Map<String, Integer> updatedById) {
  }

  public int syncOnlineItems() {
    ItemMigrationReport report = syncOnlineItemsWithReport();
    if (report.updatedTotal() > 0) {
      effectsLog.info("[Effects] Updated " + report.updatedTotal() + " item(s) via migrations: " + report.updatedById());
    }
    return report.updatedTotal();
  }

  public ItemMigrationReport syncOnlineItemsWithReport() {
    Map<String, Integer> updatedById = new HashMap<>();
    int updated = 0;
    for (Player player : Bukkit.getOnlinePlayers()) {
      updated += syncPlayerItems(player, updatedById);
    }
    return new ItemMigrationReport(updated, java.util.Collections.unmodifiableMap(updatedById));
  }

  public int syncPlayerItems(Player player) {
    return syncPlayerItems(player, null);
  }

  private int syncPlayerItems(Player player, Map<String, Integer> updatedById) {
    Objects.requireNonNull(player, "player");
    if (itemTemplates.isEmpty()) {
      return 0;
    }
    int updated = 0;
    var inv = player.getInventory();

    ItemStack[] contents = inv.getContents();
    updated += syncArray(player, contents, updatedById);
    inv.setContents(contents);

    ItemStack[] armor = inv.getArmorContents();
    updated += syncArray(player, armor, updatedById);
    inv.setArmorContents(armor);

    ItemStack offhand = inv.getItemInOffHand();
    ItemStack updatedOffhand = syncItem(player, offhand, updatedById);
    if (updatedOffhand != offhand) {
      inv.setItemInOffHand(updatedOffhand);
      updated++;
    }
    return updated;
  }

  private int syncArray(Player player, ItemStack[] contents, Map<String, Integer> updatedById) {
    int updated = 0;
    for (int i = 0; i < contents.length; i++) {
      ItemStack current = contents[i];
      ItemStack next = syncItem(player, current, updatedById);
      if (next != current) {
        contents[i] = next;
        updated++;
      }
    }
    return updated;
  }

  private ItemStack syncItem(Player player, ItemStack item, Map<String, Integer> updatedById) {
    if (item == null || item.getType().isAir()) {
      return item;
    }
    if (!ItemMarkers.getUpgradeRecords(item).isEmpty()) {
      return item;
    }
    ItemTemplate template = findTemplate(player, item);
    if (template == null) {
      return item;
    }
    int currentVersion = ItemMarkers.getItemVersion(item);
    boolean versionMatches = currentVersion == template.version();
    ItemStack updated = applyTemplate(template, item);
    if (versionMatches && isSameItem(item, updated)) {
      return item;
    }
    if (updatedById != null) {
      updatedById.merge(template.id(), 1, (left, right) -> (left == null ? 0 : left) + (right == null ? 0 : right));
    }
    return updated;
  }

  private ItemTemplate findTemplate(Player player, ItemStack item) {
    String id = ItemMarkers.getItemId(item);
    if (id != null) {
      ItemTemplate template = itemTemplates.get(id);
      if (template != null) {
        return template;
      }
    }
    if (!hasEffectLoreMarker(item)) {
      return null;
    }
    ItemTemplate match = null;
    for (ItemTemplate template : itemTemplates.values()) {
      if (matchesTemplate(template, item)) {
        if (match != null) {
          return null;
        }
        match = template;
      }
    }
    return match;
  }

  private static boolean hasEffectLoreMarker(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }
    List<Component> lore = meta.lore();
    if (lore == null || lore.isEmpty()) {
      return false;
    }
    boolean inBlock = false;
    for (Component line : lore) {
      String plain = line == null ? null : PLAIN.serialize(line);
      if (ITEM_MARKER_START.equals(plain)) {
        inBlock = true;
        continue;
      }
      if (ITEM_MARKER_END.equals(plain)) {
        return inBlock;
      }
    }
    return inBlock;
  }

  private static boolean matchesTemplate(ItemTemplate template, ItemStack item) {
    ItemStack compare = item.clone();
    compare.setAmount(1);
    ItemMarkers.setItemId(compare, null);
    ItemMarkers.setItemVersion(compare, null);
    ItemMarkers.setItemTier(compare, null);
    ItemMarkers.setItemRarity(compare, null);
    stripEffectLore(compare);
    return compare.isSimilar(template.matchBase());
  }

  private static boolean isSameItem(ItemStack a, ItemStack b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.getAmount() != b.getAmount()) {
      return false;
    }
    return a.isSimilar(b);
  }

  private static ItemStack applyTemplate(ItemTemplate template, ItemStack current) {
    ItemStack updated = template.item().clone();
    updated.setAmount(current.getAmount());
    ItemMeta meta = updated.getItemMeta();
    ItemMeta existing = current.getItemMeta();
    if (meta instanceof Damageable dmgNew && existing instanceof Damageable dmgOld) {
      dmgNew.setDamage(dmgOld.getDamage());
      updated.setItemMeta(meta);
    }
    java.util.Map<String, Double> existingStats = ItemMarkers.getItemStats(current);
    if (!existingStats.isEmpty()) {
      ItemMarkers.setItemStats(updated, existingStats);
    }
    List<String> existingAffixes = ItemMarkers.getItemAffixes(current);
    if (!existingAffixes.isEmpty()) {
      ItemMarkers.setItemAffixes(updated, existingAffixes);
    }
    return updated;
  }

  private static void stripEffectLore(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    List<Component> lore = meta.lore();
    if (lore == null || lore.isEmpty()) {
      return;
    }
    List<Component> out = new ArrayList<>();
    boolean inBlock = false;
    for (Component line : lore) {
      String plain = line == null ? null : PLAIN.serialize(line);
      if (ITEM_MARKER_START.equals(plain)) {
        inBlock = true;
        continue;
      }
      if (ITEM_MARKER_END.equals(plain)) {
        inBlock = false;
        continue;
      }
      if (!inBlock) {
        out.add(line);
      }
    }
    meta.lore(out.isEmpty() ? null : out);
    item.setItemMeta(meta);
  }

  private void loadItemTemplate(String itemId, String base, YamlConfiguration cfg, List<String> errors) {
    ItemTemplateCompiler.CompiledTemplate compiled = parseCustomItemTemplate(cfg.getConfigurationSection("item"), base + ".item", errors);
    ItemStack item = compiled == null ? cfg.getItemStack("item") : compiled.item();
    if (item == null || item.getType().isAir()) {
      errors.add(base + ": item is missing or invalid");
      return;
    }
    try {
      ItemStack template = item.clone();
      ItemMarkers.setItemId(template, itemId);
      int version = cfg.getInt("version", cfg.getInt("itemVersion", 1));
      if (version < 0) {
        version = 0;
      }
      ItemMarkers.setItemVersion(template, version);
      String tierId = null;
      String rarityId = null;
      ConfigurationSection mana = cfg.getConfigurationSection("mana");
      if (mana != null) {
        double maxBonus = mana.contains("maxBonus") ? mana.getDouble("maxBonus") : mana.getDouble("max", 0.0);
        double regenBonus = mana.contains("regenBonus") ? mana.getDouble("regenBonus") : mana.getDouble("regen", 0.0);
        double regenMultiplier = mana.getDouble("regenMultiplier", 0.0);
        double regenPercent = mana.getDouble("regenPercent", 0.0);
        String regenMode = YamlValues.string(mana, "regenMode",
            YamlValues.string(mana, "regenCurve", YamlValues.string(mana, "curve", null)));
        double costMultiplier = mana.contains("costMultiplier")
            ? mana.getDouble("costMultiplier")
            : mana.getDouble("costPercent", 0.0);
        double costAdd = mana.contains("costAdd")
            ? mana.getDouble("costAdd")
            : mana.getDouble("costFlat", 0.0);
        double boost = 0.0;
        if (mana.contains("boost")) {
          boost = mana.getDouble("boost");
        } else if (mana.contains("tempBoost")) {
          boost = mana.getDouble("tempBoost");
        } else if (mana.contains("temporaryBoost")) {
          boost = mana.getDouble("temporaryBoost");
        }
        if (Double.isFinite(boost)) {
          maxBonus += boost;
        }
        ItemMarkers.setManaMaxBonus(template, maxBonus);
        ItemMarkers.setManaRegenBonus(template, regenBonus);
        ItemMarkers.setManaRegenMultiplier(template, regenMultiplier);
        ItemMarkers.setManaRegenPercent(template, regenPercent);
        ItemMarkers.setManaRegenMode(template, regenMode);
        ItemMarkers.setManaCostMultiplier(template, costMultiplier);
        ItemMarkers.setManaCostAdd(template, costAdd);
      }
      ItemTierSpec tierSpec = ItemTierSpec.parse(cfg.get("tier"), base + ".tier", errors);
      if (tierSpec != null && tierSpec.id() != null && !tierSpec.id().isBlank()) {
        tierId = Ids.normalize(tierSpec.id());
        ItemMarkers.setItemTier(template, tierId);
      }
      rarityId = readItemRarity(cfg);
      if (rarityId != null && !rarityId.isBlank()) {
        ItemMarkers.setItemRarity(template, Ids.normalize(rarityId));
      }
      List<String> itemTags = readStringList(cfg.get("tags"));
      if (itemTags.isEmpty()) {
        itemTags = readStringList(cfg.get("tag"));
      }
      if (!itemTags.isEmpty()) {
        ItemMarkers.setItemTags(template, itemTags);
      }
      String itemCategory = YamlValues.string(cfg.get("category"), null);
      if (itemCategory == null || itemCategory.isBlank()) {
        itemCategory = YamlValues.string(cfg.get("itemCategory"), null);
      }
      if (itemCategory != null && !itemCategory.isBlank()) {
        ItemMarkers.setItemCategory(template, Ids.normalize(itemCategory));
      }
      Object consumableRaw = cfg.get("consumable");
      if (consumableRaw == null) {
        consumableRaw = cfg.get("consume");
      }
      if (consumableRaw != null) {
        try {
          ItemConsumeMode consumeMode = ItemConsumeMode.NONE;
          int consumeAmount = 1;
          if (consumableRaw instanceof Boolean bool) {
            consumeMode = bool ? ItemConsumeMode.STACK : ItemConsumeMode.NONE;
          } else if (consumableRaw instanceof String rawString) {
            consumeMode = parseConsumeMode(rawString, base + ".consumable");
          } else if (consumableRaw instanceof ConfigurationSection section) {
            String modeRaw = section.getString("mode", section.getString("type", null));
            consumeMode = parseConsumeMode(modeRaw, base + ".consumable.mode");
            consumeAmount = section.getInt("amount", section.getInt("damage", 1));
          } else if (consumableRaw instanceof Map<?, ?> map) {
            Object modeRaw = map.get("mode");
            if (modeRaw == null) {
              modeRaw = map.get("type");
            }
            consumeMode = parseConsumeMode(modeRaw == null ? null : String.valueOf(modeRaw), base + ".consumable.mode");
            Object amountRaw = map.containsKey("amount") ? map.get("amount") : map.get("damage");
            if (amountRaw != null) {
              consumeAmount = Integer.parseInt(String.valueOf(amountRaw));
            }
          } else {
            throw new IllegalArgumentException(base + ".consumable: expected boolean, string, or object");
          }
          if (consumeMode != ItemConsumeMode.NONE) {
            if (consumeAmount <= 0) {
              throw new IllegalArgumentException(base + ".consumable.amount: must be > 0");
            }
            boolean damageable = template.getItemMeta() instanceof Damageable && template.getType().getMaxDurability() > 0;
            if (consumeMode == ItemConsumeMode.DURABILITY && !damageable) {
              throw new IllegalArgumentException(base + ".consumable.mode: durability requires a damageable item");
            }
            ItemMarkers.setConsumeMode(template, consumeMode);
            ItemMarkers.setConsumeAmount(template, consumeAmount);
          }
        } catch (Exception ex) {
          errors.add(base + ": " + ex.getMessage());
        }
      }
      ConfigurationSection upgrades = cfg.getConfigurationSection("upgrades");
      if (upgrades != null) {
        java.util.Map<String, Integer> slots = parseUpgradeSlots(upgrades.get("slots"), base + ".upgrades.slots", errors);
        if (!slots.isEmpty()) {
          ItemMarkers.setUpgradeSlots(template, slots);
        }
        int maxUpgrades = upgrades.getInt("maxUpgrades", upgrades.getInt("maxPerItem", 0));
        if (maxUpgrades < 0) {
          errors.add(base + ".upgrades.maxUpgrades: must be >= 0");
          maxUpgrades = 0;
        }
        if (maxUpgrades > 0) {
          ItemMarkers.setUpgradeMaxCount(template, maxUpgrades);
        }
        int tierBudget = upgrades.getInt("tierBudget", upgrades.getInt("maxTierTotal", 0));
        if (tierBudget < 0) {
          errors.add(base + ".upgrades.tierBudget: must be >= 0");
          tierBudget = 0;
        }
        if (tierBudget > 0) {
          ItemMarkers.setUpgradeTierBudget(template, tierBudget);
        }
      }
      List<Map<String, Object>> bindings = EditorItemYaml.bindings(cfg);
      EditorItemLore.applyAbilityLore(template, bindings, engine);
      ItemStack matchBase = template.clone();
      ItemMarkers.setItemId(matchBase, null);
      ItemMarkers.setItemVersion(matchBase, null);
      ItemMarkers.setItemTier(matchBase, null);
      ItemMarkers.setItemRarity(matchBase, null);
      stripEffectLore(matchBase);
      ItemStatBlock baseStats = ItemStatBlock.parse(cfg.get("stats"), base + ".stats", errors);
      ItemStatBlock effectiveStats = baseStats;
      if (tierSpec != null) {
        effectiveStats = tierSpec.apply(baseStats);
      }
      if (effectiveStats != null && !effectiveStats.isEmpty()) {
        ItemMarkers.setItemStats(template, effectiveStats.values());
      }
      ItemAffixPool affixPool = ItemAffixPool.parse(cfg.get("affixes"), base + ".affixes", errors);
      if (affixPool == null) {
        affixPool = ItemAffixPool.parse(cfg.get("affixPool"), base + ".affixPool", errors);
      }
      java.util.Map<ItemHookType, java.util.List<ItemHookSpec>> hooks = parseItemHooks(cfg, base, errors);
      ItemTemplateCompiler.DurabilityRange durabilityRange = compiled == null ? null : compiled.durabilityRange();
      itemTemplates.put(itemId, new ItemTemplate(itemId, template, matchBase, version, durabilityRange, baseStats, affixPool, tierSpec, hooks));
    } catch (Exception ex) {
      errors.add(base + ": " + ex.getMessage());
    }
  }

  private ItemTemplateCompiler.CompiledTemplate parseCustomItemTemplate(ConfigurationSection section, String path, List<String> errors) {
    return ItemTemplateCompiler.compile(section, path, errors);
  }

  private java.util.Map<ItemHookType, java.util.List<ItemHookSpec>> parseItemHooks(
      YamlConfiguration cfg,
      String base,
      List<String> errors) {
    ConfigurationSection behavior = cfg.getConfigurationSection("behavior");
    if (behavior == null) {
      return java.util.Map.of();
    }
    java.util.Map<ItemHookType, java.util.List<ItemHookSpec>> hooks = new java.util.EnumMap<>(ItemHookType.class);
    parseHookList(hooks, ItemHookType.ON_EQUIP, behavior.get("onEquip"), base + ".behavior.onEquip", errors);
    parseHookList(hooks, ItemHookType.ON_HIT, behavior.get("onHit"), base + ".behavior.onHit", errors);
    parseHookList(hooks, ItemHookType.ON_HURT, behavior.get("onHurt"), base + ".behavior.onHurt", errors);
    parseHookList(hooks, ItemHookType.ON_CONSUME, behavior.get("onConsume"), base + ".behavior.onConsume", errors);
    parseHookList(hooks, ItemHookType.ON_BLOCK_BREAK, behavior.get("onBlockBreak"), base + ".behavior.onBlockBreak", errors);
    return hooks;
  }

  private java.util.Map<String, Integer> parseUpgradeSlots(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return java.util.Map.of();
    }
    java.util.Map<String, Integer> slots = new java.util.LinkedHashMap<>();
    if (raw instanceof ConfigurationSection section) {
      for (String key : section.getKeys(false)) {
        int count = section.getInt(key, 0);
        if (count < 0) {
          errors.add(path + "." + key + ": count must be >= 0");
          continue;
        }
        slots.put(normalizeSlot(key), count);
      }
      return slots;
    }
    if (raw instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
        if (key.isBlank()) {
          continue;
        }
        int count = intValue(entry.getValue(), null, null, 0);
        if (count < 0) {
          errors.add(path + "." + key + ": count must be >= 0");
          continue;
        }
        slots.put(normalizeSlot(key), count);
      }
      return slots;
    }
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Object entry = list.get(i);
        String entryPath = path + "[" + i + "]";
        if (entry instanceof Map<?, ?> slotMap) {
          Object typeRaw = slotMap.get("type");
          if (typeRaw == null) {
            errors.add(entryPath + ".type: missing slot type");
            continue;
          }
          String type = normalizeSlot(String.valueOf(typeRaw));
          int count = intValue(slotMap.get("count"), slotMap.get("slots"), null, 0);
          if (count < 0) {
            errors.add(entryPath + ".count: must be >= 0");
            continue;
          }
          slots.put(type, count);
        } else {
          errors.add(entryPath + ": expected object with type/count");
        }
      }
      return slots;
    }
    errors.add(path + ": expected map, list, or section");
    return java.util.Map.of();
  }

  private String readItemRarity(YamlConfiguration cfg) {
    String rarity = YamlValues.string(cfg.get("rarity"), null);
    if (rarity != null && !rarity.isBlank()) {
      return rarity;
    }
    ConfigurationSection item = cfg.getConfigurationSection("item");
    if (item != null) {
      rarity = YamlValues.string(item.get("rarity"), null);
      if (rarity != null && !rarity.isBlank()) {
        return rarity;
      }
      ConfigurationSection display = item.getConfigurationSection("display");
      if (display != null) {
        rarity = YamlValues.string(display.get("rarity"), null);
        if (rarity != null && !rarity.isBlank()) {
          return rarity;
        }
      }
    }
    return null;
  }

  private String normalizeSlot(String raw) {
    if (raw == null) {
      return "default";
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return "default";
    }
    if (trimmed.equalsIgnoreCase("any") || trimmed.equalsIgnoreCase("*")) {
      return "any";
    }
    return Ids.normalize(trimmed);
  }

  private void parseHookList(
      java.util.Map<ItemHookType, java.util.List<ItemHookSpec>> hooks,
      ItemHookType type,
      Object raw,
      String path,
      List<String> errors) {
    if (raw == null) {
      return;
    }
    List<ItemHookSpec> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        ItemHookSpec spec = parseHookSpec(list.get(i), path + "[" + i + "]", errors);
        if (spec != null) {
          out.add(spec);
        }
      }
    } else {
      ItemHookSpec spec = parseHookSpec(raw, path, errors);
      if (spec != null) {
        out.add(spec);
      }
    }
    if (!out.isEmpty()) {
      hooks.put(type, java.util.Collections.unmodifiableList(out));
    }
  }

  private ItemHookSpec parseHookSpec(Object raw, String path, List<String> errors) {
    List<String> abilities = new ArrayList<>();
    dev.patric.dungeonsreborn.effects.actions.Action action = null;
    long cooldownTicks = 0L;
    double manaCost = 0.0;
    int durabilityCost = 0;
    int consumeAmount = 0;

    if (raw instanceof String abilityId) {
      abilities.add(Ids.normalize(abilityId));
    } else if (raw instanceof Map<?, ?> map) {
      Object abilityRaw = map.get("ability");
      if (abilityRaw instanceof String single) {
        abilities.add(Ids.normalize(single));
      }
      Object abilitiesRaw = map.get("abilities");
      if (abilitiesRaw instanceof List<?> list) {
        for (Object entry : list) {
          if (entry == null) {
            continue;
          }
          abilities.add(Ids.normalize(String.valueOf(entry)));
        }
      } else if (abilitiesRaw instanceof String listAsString) {
        abilities.add(Ids.normalize(listAsString));
      }

      Object soundRaw = map.get("sound");
      dev.patric.dungeonsreborn.effects.actions.Action soundAction = parseSoundAction(soundRaw, path + ".sound", errors);
      Object particleRaw = map.get("particle");
      if (particleRaw == null) {
        particleRaw = map.get("particles");
      }
      dev.patric.dungeonsreborn.effects.actions.Action particleAction = parseParticleAction(particleRaw, path + ".particle", errors);
      if (soundAction != null && particleAction != null) {
        action = Actions.sequence(soundAction, particleAction);
      } else if (soundAction != null) {
        action = soundAction;
      } else if (particleAction != null) {
        action = particleAction;
      }

      cooldownTicks = parseCooldownTicks(map, path);
      manaCost = doubleValue(map.get("manaCost"), map.get("mana"), 0.0);
      durabilityCost = intValue(map.get("durabilityCost"), map.get("durability"), map.get("damage"), 0);
      consumeAmount = intValue(map.get("consumeAmount"), map.get("consume"), map.get("amount"), 0);
    } else {
      errors.add(path + ": expected string or object");
      return null;
    }

    return new ItemHookSpec(
        java.util.Collections.unmodifiableList(abilities),
        action,
        cooldownTicks,
        manaCost,
        durabilityCost,
        consumeAmount);
  }

  private long parseCooldownTicks(Map<?, ?> map, String path) {
    Object ticksRaw = map.get("cooldownTicks");
    if (ticksRaw != null) {
      return Math.max(0L, YamlValues.longValue(ticksRaw, 0L));
    }
    Object secondsRaw = map.containsKey("cooldownSeconds") ? map.get("cooldownSeconds") : map.get("cooldown");
    if (secondsRaw != null) {
      double seconds = YamlValues.doubleValue(secondsRaw, 0.0);
      if (seconds < 0.0) {
        return 0L;
      }
      return Math.round(seconds * 20.0);
    }
    return 0L;
  }

  private dev.patric.dungeonsreborn.effects.actions.Action parseSoundAction(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    String id;
    float volume = 1.0f;
    float pitch = 1.0f;
    if (raw instanceof String value) {
      id = value;
    } else if (raw instanceof Map<?, ?> map) {
      Object idRaw = map.get("id");
      if (idRaw == null) {
        idRaw = map.get("sound");
      }
      if (idRaw == null) {
        errors.add(path + ".id: missing sound id");
        return null;
      }
      id = String.valueOf(idRaw);
      volume = (float) doubleValue(map.get("volume"), null, 1.0);
      pitch = (float) doubleValue(map.get("pitch"), null, 1.0);
    } else {
      errors.add(path + ": expected string or object");
      return null;
    }
    NamespacedKey key = parseSoundKey(id);
    if (key == null) {
      errors.add(path + ": invalid sound key=" + id);
      return null;
    }
    Sound sound = Registry.SOUNDS.get(key);
    if (sound == null) {
      errors.add(path + ": unknown sound=" + id);
      return null;
    }
    return Actions.sound(sound, volume, pitch);
  }

  private static NamespacedKey parseSoundKey(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String normalized = trimmed.toLowerCase(Locale.ROOT);
    NamespacedKey key = NamespacedKey.fromString(normalized);
    if (key != null) {
      return key;
    }
    if (normalized.contains(".")) {
      return NamespacedKey.minecraft(normalized);
    }
    return NamespacedKey.minecraft(normalized.replace('_', '.'));
  }

  private dev.patric.dungeonsreborn.effects.actions.Action parseParticleAction(Object raw, String path, List<String> errors) {
    if (raw == null) {
      return null;
    }
    String type;
    int count = 1;
    double offset = 0.0;
    double extra = 0.0;
    if (raw instanceof String value) {
      type = value;
    } else if (raw instanceof Map<?, ?> map) {
      Object typeRaw = map.get("type");
      if (typeRaw == null) {
        typeRaw = map.get("particle");
      }
      if (typeRaw == null) {
        errors.add(path + ".type: missing particle type");
        return null;
      }
      type = String.valueOf(typeRaw);
      count = intValue(map.get("count"), null, null, 1);
      offset = doubleValue(map.get("offset"), null, 0.0);
      extra = doubleValue(map.get("extra"), null, 0.0);
    } else {
      errors.add(path + ": expected string or object");
      return null;
    }
    try {
      Particle particle = Particle.valueOf(type.trim().toUpperCase(Locale.ROOT));
      return Actions.particlesPoint(particle, count, offset, extra);
    } catch (IllegalArgumentException ex) {
      errors.add(path + ": unknown particle=" + type);
      return null;
    }
  }

  private static double doubleValue(Object primary, Object secondary, double fallback) {
    if (primary != null) {
      return YamlValues.doubleValue(primary, fallback);
    }
    if (secondary != null) {
      return YamlValues.doubleValue(secondary, fallback);
    }
    return fallback;
  }

  private static double doubleValue(Map<String, Object> node, String key, double fallback) {
    if (node == null || key == null) {
      return fallback;
    }
    return YamlReaders.doubleValue(node, key, fallback);
  }

  private static int intValue(Object first, Object second, Object third, int fallback) {
    if (first != null) {
      return YamlValues.intValue(first, fallback);
    }
    if (second != null) {
      return YamlValues.intValue(second, fallback);
    }
    if (third != null) {
      return YamlValues.intValue(third, fallback);
    }
    return fallback;
  }

  private static List<String> readStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object entry : list) {
        if (entry == null) {
          continue;
        }
        String value = String.valueOf(entry).trim();
        if (!value.isEmpty()) {
          out.add(value);
        }
      }
      return out;
    }
    String value = String.valueOf(raw).trim();
    if (value.isEmpty()) {
      return List.of();
    }
    return List.of(value);
  }

  private static int intValue(Map<String, Object> node, String key, int fallback) {
    if (node == null || key == null) {
      return fallback;
    }
    return YamlReaders.intValue(node, key, fallback);
  }

  public LintResult lintScripts() {
    plugin.getDataFolder().mkdirs();
    List<String> errors = new ArrayList<>();
    int scripts = 0;

    java.util.ArrayList<java.util.Map.Entry<String, YamlConfiguration>> sources = new java.util.ArrayList<>();
    sources.add(java.util.Map.entry(file().getPath(), YamlConfiguration.loadConfiguration(file())));

    File dir = abilitiesDir();
    File[] extra = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (extra != null) {
      java.util.Arrays.sort(extra, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File f : extra) {
        sources.add(java.util.Map.entry(f.getPath(), YamlConfiguration.loadConfiguration(f)));
      }
    }
    File[] folders = dir.listFiles(File::isDirectory);
    if (folders != null) {
      java.util.Arrays.sort(folders, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File folder : folders) {
        File abilityFile = new File(folder, "ability.yml");
        if (!abilityFile.exists()) {
          abilityFile = new File(folder, "ability.yaml");
        }
        if (abilityFile.exists()) {
          sources.add(java.util.Map.entry(abilityFile.getPath(), YamlConfiguration.loadConfiguration(abilityFile)));
        }
      }
    }

    for (var source : sources) {
      String sourcePath = source.getKey();
      YamlConfiguration cfg = source.getValue();
      ConfigurationSection abilities = cfg.getConfigurationSection("abilities");
      if (abilities == null) {
        continue;
      }
      for (String id : abilities.getKeys(false)) {
        ConfigurationSection a = abilities.getConfigurationSection(id);
        if (a == null) {
          continue;
        }
        Object scriptObj = a.get("script");
        if (scriptObj == null) {
          continue;
        }
        String basePath = sourcePath + ": abilities." + id + ".script";
        scripts++;
        try {
          compileScript(scriptObj, basePath);
        } catch (IllegalArgumentException ex) {
          errors.add(ex.getMessage());
        }
      }
    }

    File scriptsDir = new File(plugin.getDataFolder(), "effects/scripts");
    if (scriptsDir.exists()) {
      try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(scriptsDir.toPath())) {
        for (java.nio.file.Path path : walk.filter(java.nio.file.Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".es"))
            .toList()) {
          scripts++;
          try {
            compileScriptFileCached(path.toString(), path.toString(), null);
          } catch (IllegalArgumentException ex) {
            errors.add(ex.getMessage());
          }
        }
      } catch (java.io.IOException ex) {
        errors.add("scripts: failed to read scripts folder (" + ex.getMessage() + ")");
      }
    }

    return new LintResult(scripts, errors);
  }

  public LintResult lintScriptFile(String filePath) {
    List<String> errors = new ArrayList<>();
    try {
      compileScriptFileCached(filePath, "lint", null);
    } catch (IllegalArgumentException ex) {
      errors.add(ex.getMessage());
    }
    return new LintResult(1, errors);
  }

  public void runScriptFile(Player player, String filePath) {
    Objects.requireNonNull(player, "player");
    java.nio.file.Path resolved = resolveScriptPath(filePath, "script.run");
    String scriptId = "script:" + resolved.getFileName();
    ScriptHandlers handlers = compileScriptFileCached(resolved.toString(), "script.run", null);
    dev.patric.dungeonsreborn.effects.actions.Action action = buildScriptAction(handlers, scriptId);
    engine.castAction("dsl_playground", player, action);
  }

  private AbilitySpec compileAbility(String id, String base, ConfigurationSection a) {
    AbilitySpec.Builder builder = AbilitySpec.builder(id)
        .name(a.getString("name"))
        .description(a.getString("description"));
    String previousUnsafePermission = unsafePermission;
    String configuredUnsafePermission = a.getString("unsafePermission");
    if (configuredUnsafePermission != null && configuredUnsafePermission.isBlank()) {
      configuredUnsafePermission = null;
    }
    boolean unsafeActions = a.getBoolean("unsafeActions", false);
    if (configuredUnsafePermission == null && unsafeActions) {
      configuredUnsafePermission = DEFAULT_UNSAFE_PERMISSION;
    }
    unsafePermission = configuredUnsafePermission;

    try {
      // Requirements
      var requirements = a.getMapList("requirements");
      for (int i = 0; i < requirements.size(); i++) {
        Map<?, ?> raw = requirements.get(i);
        Map<String, Object> req = castMap(raw, base + ".requirements[" + i + "]");
        String type = requireString(req, "type", base + ".requirements[" + i + "].type");
        Component message = req.containsKey("message") ? richText(String.valueOf(req.get("message"))) : null;

        switch (type.toLowerCase(Locale.ROOT)) {
          case "sneaking" -> builder.require(Conditions.sneaking(), message);
          case "permission" -> builder.require(Conditions.permission(requireString(req, "permission", base + ".requirements[" + i + "].permission")), message);
          case "has_item_tag" -> {
            String keyStr = requireString(req, "key", base + ".requirements[" + i + "].key");
            NamespacedKey key = NamespacedKey.fromString(keyStr);
            if (key == null) {
              throw new IllegalArgumentException(base + ".requirements[" + i + "].key: invalid NamespacedKey: " + keyStr);
            }
            builder.require(Conditions.hasItemTag(key), message);
          }
          default -> throw new IllegalArgumentException(base + ".requirements[" + i + "].type: unknown type: " + type);
        }
      }

      Set<String> allowWorlds = parseStringSet(a.get("allowWorlds"), a.get("allowWorld"), base + ".allowWorlds");
      if (!allowWorlds.isEmpty()) {
        Component message = a.isString("allowWorldsMessage") ? richText(a.getString("allowWorldsMessage")) : null;
        builder.require(ctx -> matchesWorld(ctx.world(), allowWorlds), message);
      }
      Set<String> denyWorlds = parseStringSet(a.get("denyWorlds"), a.get("denyWorld"), base + ".denyWorlds");
      if (!denyWorlds.isEmpty()) {
        Component message = a.isString("denyWorldsMessage") ? richText(a.getString("denyWorldsMessage")) : null;
        builder.require(ctx -> !matchesWorld(ctx.world(), denyWorlds), message);
      }
      List<QuestRegion> allowRegions = parseRegionList(a.get("allowRegions"), base + ".allowRegions");
      if (!allowRegions.isEmpty()) {
        Component message = a.isString("allowRegionsMessage") ? richText(a.getString("allowRegionsMessage")) : null;
        builder.require(ctx -> regionContainsAny(allowRegions, ctx.origin()), message);
      }
      List<QuestRegion> denyRegions = parseRegionList(a.get("denyRegions"), base + ".denyRegions");
      if (!denyRegions.isEmpty()) {
        Component message = a.isString("denyRegionsMessage") ? richText(a.getString("denyRegionsMessage")) : null;
        builder.require(ctx -> !regionContainsAny(denyRegions, ctx.origin()), message);
      }
      Component minManaMessage = a.isString("minManaMessage") ? richText(a.getString("minManaMessage")) : null;
      double minMana = a.contains("minMana") ? a.getDouble("minMana", 0.0) : 0.0;
      double minManaPct = a.contains("minManaPct") ? a.getDouble("minManaPct", 0.0) : 0.0;
      if (globalMinMana > 0.0 || globalMinManaPct > 0.0) {
        addManaMinimumRequirement(builder, base, globalMinMana, globalMinManaPct, null);
      }
      if (minMana > 0.0 || minManaPct > 0.0) {
        addManaMinimumRequirement(builder, base, minMana, minManaPct, minManaMessage);
      }

      // Costs
      var costs = a.getMapList("costs");
      for (int i = 0; i < costs.size(); i++) {
        Map<?, ?> raw = costs.get(i);
        Map<String, Object> cost = castMap(raw, base + ".costs[" + i + "]");
        String type = requireString(cost, "type", base + ".costs[" + i + "].type");
        String costBase = base + ".costs[" + i + "]";
        NumValue multiplier = numValue(cost, "multiplier", 1.0, costBase);
        NumValue add = numValue(cost, "add", 0.0, costBase);
        dev.patric.dungeonsreborn.effects.conditions.Condition condition = null;
        if (cost.containsKey("condition")) {
          condition = compileCondition(cost.get("condition"), costBase + ".condition");
        } else if (cost.containsKey("conditions")) {
          Object conditionsRaw = cost.get("conditions");
          if (conditionsRaw instanceof List<?> list && !list.isEmpty()) {
            List<dev.patric.dungeonsreborn.effects.conditions.Condition> compiled = new ArrayList<>();
            for (int c = 0; c < list.size(); c++) {
              compiled.add(compileCondition(list.get(c), costBase + ".conditions[" + c + "]"));
            }
            condition = ctx -> {
              for (dev.patric.dungeonsreborn.effects.conditions.Condition entry : compiled) {
                if (!entry.test(ctx)) {
                  return false;
                }
              }
              return true;
            };
          } else if (conditionsRaw != null) {
            condition = compileCondition(conditionsRaw, costBase + ".conditions");
          }
        }
        final dev.patric.dungeonsreborn.effects.conditions.Condition costCondition = condition;
        switch (type.toLowerCase(Locale.ROOT)) {
          case "mana" -> {
            NumValue amount = requireNumValue(cost, "amount", costBase + ".amount");
            builder.cost(ctx -> {
              if (costCondition != null && !costCondition.test(ctx)) {
                return null;
              }
              double v = evalDouble(amount, ctx);
              v = v * evalDouble(multiplier, ctx) + evalDouble(add, ctx);
              if (!(v > 0.0)) {
                return Component.text("Invalid mana cost.");
              }
              return Costs.mana(v).tryApply(ctx);
            });
          }
          case "resource" -> {
            String resourceId = requireString(cost, "resource", costBase + ".resource");
            NumValue amount = requireNumValue(cost, "amount", costBase + ".amount");
            builder.cost(ctx -> {
              if (costCondition != null && !costCondition.test(ctx)) {
                return null;
              }
              double v = evalDouble(amount, ctx);
              v = v * evalDouble(multiplier, ctx) + evalDouble(add, ctx);
              if (!(v > 0.0)) {
                return Component.text("Invalid resource cost.");
              }
              return Costs.resource(resourceId, v).tryApply(ctx);
            });
          }
          case "consume_item", "consume_main_hand" -> {
            NumValue amount = requireNumValue(cost, "amount", costBase + ".amount");
            builder.cost(ctx -> {
              if (costCondition != null && !costCondition.test(ctx)) {
                return null;
              }
              int v = evalInt(amount, ctx);
              if (v <= 0) {
                return Component.text("Invalid item cost.");
              }
              return Costs.consumeMainHand(v).tryApply(ctx);
            });
          }
          case "durability", "durability_main_hand" -> {
            NumValue dmg = requireNumValue(cost, "damage", costBase + ".damage");
            boolean allowBreak = bool(cost, "allowBreak", false);
            builder.cost(ctx -> {
              if (costCondition != null && !costCondition.test(ctx)) {
                return null;
              }
              int v = evalInt(dmg, ctx);
              if (v <= 0) {
                return Component.text("Invalid durability cost.");
              }
              return Costs.durabilityMainHand(v, allowBreak).tryApply(ctx);
            });
          }
          default -> throw new IllegalArgumentException(base + ".costs[" + i + "].type: unknown type: " + type);
        }
      }

    // Cooldown
    ConfigurationSection cooldown = a.getConfigurationSection("cooldown");
    if (cooldown != null) {
      long ticks = cooldown.getLong("ticks", 0L);
      if (ticks > 0) {
        builder.cooldownTicks(ticks, cooldown.getString("key"));
      }
    }

    // Progression XP awards (vanilla XP system).
    ConfigurationSection progression = a.getConfigurationSection("progression");
    if (progression != null) {
      int fixedXp = progression.getInt("xp", -1);
      int minXp = progression.getInt("minXp", fixedXp >= 0 ? fixedXp : 0);
      int maxXp = progression.getInt("maxXp", fixedXp >= 0 ? fixedXp : minXp);
      builder.xpAward(minXp, maxXp);
    }

    // Action or Script
    Object scriptObj = a.get("script");
    dev.patric.dungeonsreborn.effects.actions.Action compiled;
    if (scriptObj != null) {
      ScriptHandlers handlers = compileScript(scriptObj, base + ".script");
      compiled = buildScriptAction(handlers, id);
      if (handlers.onCostFail() != null) {
        builder.onCostFail(handlers.onCostFail());
      }
      if (handlers.onCooldownFail() != null) {
        builder.onCooldownFail(handlers.onCooldownFail());
      }
    } else {
      Object actionObj = a.get("action");
      Map<String, Object> actionNode;
      if (actionObj instanceof ConfigurationSection sec) {
        actionNode = sec.getValues(false);
      } else if (actionObj instanceof Map<?, ?> map) {
        actionNode = castMap(map, base + ".action");
      } else {
        throw new IllegalArgumentException(base + ".action: missing or invalid");
      }
      compiled = compileAction(actionNode, base + ".action", new java.util.ArrayDeque<>());
    }
    if (unsafePermission != null && unsafeActions) {
      compiled = wrapUnsafeAction(compiled, "ability", base);
    }
    if (a.getBoolean("profile", false)) {
      compiled = Actions.timed("yaml:" + id, compiled);
    }
    builder.action(compiled);
    yamlActionGraphs.put(id, compiled);

    // Triggers: compile YAML triggers into InteractBindings / CombatEvent bindings.
    var triggers = a.getMapList("triggers");
    for (int i = 0; i < triggers.size(); i++) {
      Map<?, ?> raw = triggers.get(i);
      Map<String, Object> trig = castMap(raw, base + ".triggers[" + i + "]");
      String type = requireString(trig, "type", base + ".triggers[" + i + "].type").trim().toLowerCase(Locale.ROOT);
      if (type.equals("event")) {
        String eventRaw = requireString(trig, "event", base + ".triggers[" + i + "].event");
        String normalizedEvent = eventRaw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        boolean legacyAlias = "ON_HIT".equals(normalizedEvent) || "ON_KILL".equals(normalizedEvent);
        if ((strictCombatSchema || combatMigrationRequired) && legacyAlias) {
          String mode = strictCombatSchema ? "effects.combat.strictSchema=true" : "effects.combat.migration.required=true";
          throw new IllegalArgumentException(base + ".triggers[" + i + "].event: legacy alias value=" + eventRaw
              + " is not allowed when " + mode + " (run /dr effects combat migrate)");
        }
        CombatEventType eventType = CombatEventType.parse(eventRaw);
        String abilityId = requireString(trig, "ability", base + ".triggers[" + i + "].ability");
        if (!engine.hasAbility(abilityId)) {
          throw new IllegalArgumentException(base + ".triggers[" + i + "].ability: ability not registered: " + abilityId);
        }
        String bindingId = trig.containsKey("id") ? String.valueOf(trig.get("id")) : null;
        if (bindingId == null || bindingId.isBlank()) {
          bindingId = "yaml:event:" + id + ":" + i;
        } else if (!bindingId.startsWith("yaml:")) {
          bindingId = "yaml:" + bindingId;
        }

        double chance = trig.containsKey("chance") ? doubleValue(trig, "chance", 1.0) : 1.0;
        String requiredPermission = trig.containsKey("permission") ? String.valueOf(trig.get("permission")) : null;
        if (requiredPermission != null && requiredPermission.isBlank()) {
          requiredPermission = null;
        }
        boolean requireSneaking = bool(trig, "requireSneaking", false);
        long cooldownTicks = longValue(trig, "cooldownTicks", 0L);
        CombatCooldownScope cooldownScope = CombatCooldownScope.PER_PLAYER;
        Object cooldownRaw = trig.get("cooldown");
        if (cooldownRaw instanceof Map<?, ?> || cooldownRaw instanceof ConfigurationSection) {
          Map<String, Object> cooldownNode = castMap(cooldownRaw, base + ".triggers[" + i + "].cooldown");
          cooldownTicks = longValue(cooldownNode, "ticks", cooldownTicks);
          cooldownScope = CombatCooldownScope.parse(string(cooldownNode, "scope", "per_player"), CombatCooldownScope.PER_PLAYER);
        }

        CombatEventTargetBind targetBind = CombatEventTargetBind.EVENT_PRIMARY;
        Object targetRaw = trig.get("target");
        if (targetRaw instanceof Map<?, ?> || targetRaw instanceof ConfigurationSection) {
          Map<String, Object> targetNode = castMap(targetRaw, base + ".triggers[" + i + "].target");
          targetBind = CombatEventTargetBind.parse(string(targetNode, "bind", "event_primary"), CombatEventTargetBind.EVENT_PRIMARY);
        }

        CombatEventFilters filters = CombatEventFilters.none();
        Object filtersRaw = trig.get("filters");
        if (filtersRaw instanceof Map<?, ?> || filtersRaw instanceof ConfigurationSection) {
          Map<String, Object> filtersNode = castMap(filtersRaw, base + ".triggers[" + i + "].filters");
          Set<DamageType> damageTypes = parseEnumSet(filtersNode.get("damageType"), DamageType.class, base + ".triggers[" + i + "].filters.damageType");
          Set<CombatEventSource> sources = parseEnumSet(filtersNode.get("source"), CombatEventSource.class, base + ".triggers[" + i + "].filters.source");
          Set<dev.patric.dungeonsreborn.effects.relations.Relation> relations =
              parseEnumSet(filtersNode.get("victimRelation"), dev.patric.dungeonsreborn.effects.relations.Relation.class,
                  base + ".triggers[" + i + "].filters.victimRelation");
          double minDamage = doubleValue(filtersNode, "minDamage", 0.0);
          boolean critOnly = bool(filtersNode, "critOnly", false);
          boolean blockedOnly = bool(filtersNode, "blockedOnly", false);
          Set<String> ccTypes = stringSet(filtersNode.get("ccType"));
          Set<String> dotTags = stringSet(filtersNode.get("dotTag"));
          String weaponTag = string(filtersNode, "weaponTag", null);
          filters = new CombatEventFilters(weaponTag, damageTypes, sources, relations, minDamage, critOnly, blockedOnly, ccTypes, dotTags);
        }

        CombatEventBinding binding = new CombatEventBinding(
            bindingId,
            abilityId,
            eventType,
            chance,
            cooldownTicks,
            cooldownScope,
            requireSneaking,
            requiredPermission,
            filters,
            targetBind);
        if (bindings != null) {
          bindings.registerCombatEvent(binding);
          loadedBindingIds.add(bindingId);
        }
        continue;
      }

      if (combatMigrationRequired && ("on_hit".equals(type) || "on_kill".equals(type) || "on_dodge".equals(type) || "on_sprint".equals(type))) {
        throw new IllegalArgumentException(base + ".triggers[" + i + "].type: legacy trigger type=" + type
            + " is not allowed when effects.combat.migration.required=true (run /dr effects combat migrate)");
      }

      if (!type.equals("interact") && !type.equals("item_bind") && !type.equals("item-bind")) {
        throw new IllegalArgumentException(base + ".triggers[" + i + "].type: unknown trigger type: " + type);
      }

      String click = string(trig, "click", string(trig, "trigger", "RIGHT_CLICK"));
      InteractTrigger trigger = parseInteractTrigger(click, base + ".triggers[" + i + "].click");
      boolean requireSneaking = bool(trig, "requireSneaking", false);
      boolean cancelEvent = bool(trig, "cancelEvent", true);
      String permission = trig.containsKey("permission") ? String.valueOf(trig.get("permission")) : null;
      if (permission != null && permission.isBlank()) {
        permission = null;
      }

      ItemMatcher matcher = ItemMatchers.anyNonAir();
      if (trig.containsKey("item")) {
        matcher = compileItemMatcher(trig.get("item"), base + ".triggers[" + i + "].item");
      }

      String bindingId = trig.containsKey("id") ? String.valueOf(trig.get("id")) : null;
      if (bindingId == null || bindingId.isBlank()) {
        bindingId = "yaml:" + id + ":" + i;
      } else if (!bindingId.startsWith("yaml:")) {
        bindingId = "yaml:" + bindingId;
      }

      builder.triggerInteract(bindingId, trigger, matcher, requireSneaking, permission, cancelEvent);
    }

    EffectsEngine.AbilityCombatProfile combatProfile = parseAbilityCombatProfile(a, base + ".combat");
    if (combatProfile != null) {
      abilityCombatProfiles.put(id, combatProfile);
    }

      return builder.build();
    } finally {
      unsafePermission = previousUnsafePermission;
    }
  }

  private int compileItemBindings(String itemId, String base, Map<String, Object> root, List<String> errors) {
    Object itemRaw = root.get("item");
    if (itemRaw == null) {
      itemRaw = root.get("matcher");
    }
    if (itemRaw == null) {
      errors.add(base + ": missing item matcher (key: item)");
      return 0;
    }

    ItemMatcher baseMatcher;
    try {
      ItemStack stack = itemRaw instanceof ItemStack ? ((ItemStack) itemRaw).clone() : null;
      if (stack != null) {
        baseMatcher = ItemMatchers.or(ItemMatchers.similar(stack), ItemMatchers.itemId(itemId));
      } else {
        baseMatcher = compileItemMatcher(itemRaw, base + ".item");
      }
    } catch (Exception ex) {
      errors.add(base + ": " + ex.getMessage());
      return 0;
    }
    // Require the itemId tag so material-only matchers don't bind every similar item.
    baseMatcher = ItemMatchers.and(ItemMatchers.itemId(itemId), baseMatcher);

    String bindingKey = "bindings";
    List<?> bindingList = mapList(root, "bindings", base + ".bindings");
    if (bindingList.isEmpty()) {
      bindingList = mapList(root, "triggers", base + ".triggers");
      bindingKey = "triggers";
    }
    if (bindingList.isEmpty()) {
      return 0;
    }

    int loaded = 0;
    for (int i = 0; i < bindingList.size(); i++) {
      String path = base + "." + bindingKey + "[" + i + "]";
      try {
        Map<String, Object> binding = castMap(bindingList.get(i), path);
        String type = string(binding, "type", "interact").trim().toLowerCase(Locale.ROOT);
        String clickRaw = string(binding, "click", null);
        boolean passive = type.equals("passive") || (clickRaw != null && clickRaw.equalsIgnoreCase("passive"));
        if (!passive && !type.equals("interact") && !type.equals("item_bind") && !type.equals("item-bind")) {
          throw new IllegalArgumentException(path + ".type: expected interact or passive");
        }

        String rawAbility = requireString(binding, "ability", path + ".ability");
        String abilityId;
        try {
          abilityId = dev.patric.dungeonsreborn.effects.Ids.normalize(rawAbility);
        } catch (Exception ex) {
          throw new IllegalArgumentException(path + ".ability: invalid id (" + ex.getMessage() + ")");
        }
        if (!engine.hasAbility(abilityId)) {
          throw new IllegalArgumentException(path + ".ability: ability not registered: " + abilityId);
        }

        boolean requireSneaking = bool(binding, "requireSneaking", false);
        String permission = string(binding, "permission", null);
        if (permission != null && permission.isBlank()) {
          permission = null;
        }
        boolean cancelEvent = bool(binding, "cancelEvent", true);

        ItemMatcher matcher = baseMatcher;
        if (binding.containsKey("item")) {
          ItemMatcher extra = compileItemMatcher(binding.get("item"), path + ".item");
          matcher = ItemMatchers.and(matcher, extra);
        }

        String bindingId = string(binding, "id", null);
        if (bindingId == null || bindingId.isBlank()) {
          bindingId = passive ? "yaml:item:passive:" + itemId + ":" + i : "yaml:item:" + itemId + ":" + i;
        } else if (!bindingId.startsWith("yaml:")) {
          bindingId = passive ? "yaml:item:passive:" + bindingId : "yaml:item:" + bindingId;
        }

        if (passive) {
          int periodTicks = intValue(binding, "periodTicks", 20);
          if (periodTicks <= 0) {
            throw new IllegalArgumentException(path + ".periodTicks: must be > 0");
          }
          EnumSet<EquipmentSlot> slots = parsePassiveSlots(binding, path);
          PassiveBinding compiled = new PassiveBinding(bindingId, abilityId, matcher, requireSneaking, permission, periodTicks, slots);
          if (bindings != null) {
            bindings.registerPassive(compiled);
            loadedBindingIds.add(bindingId);
            loaded++;
          }
        } else {
          InteractTrigger trigger = parseInteractTrigger(string(binding, "click", null), path + ".click");
          InteractBinding compiled = InteractBinding.builder(bindingId)
              .trigger(trigger)
              .ability(abilityId)
              .item(matcher)
              .requireSneaking(requireSneaking)
              .permission(permission)
              .cancelEvent(cancelEvent)
              .build();
          if (bindings != null) {
            bindings.register(compiled);
            loadedBindingIds.add(bindingId);
            loaded++;
          }
        }
      } catch (Exception ex) {
        errors.add(path + ": " + ex.getMessage());
      }
    }
    return loaded;
  }

  private static VarScope parseVarScope(String raw, String path, VarScope def) {
    if (raw == null) {
      return def;
    }
    String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (s) {
      case "cast" -> VarScope.CAST;
      case "player" -> VarScope.PLAYER;
      case "entity", "target" -> VarScope.ENTITY;
      case "ability" -> VarScope.ABILITY;
      default -> throw new IllegalArgumentException(path + ": invalid scope=" + raw + " (use cast|player|entity|ability)");
    };
  }

  private static ItemConsumeMode parseConsumeMode(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return ItemConsumeMode.NONE;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "none", "off", "false" -> ItemConsumeMode.NONE;
      case "stack", "item", "item_stack", "itemstack" -> ItemConsumeMode.STACK;
      case "durability", "damage", "durable" -> ItemConsumeMode.DURABILITY;
      default -> throw new IllegalArgumentException(path + ": invalid consumable mode=" + raw + " (use stack|durability)");
    };
  }

  private static Set<String> stringSet(Object raw) {
    if (raw == null) {
      return Set.of();
    }
    if (raw instanceof String s) {
      if (s.isBlank()) {
        return Set.of();
      }
      return Set.of(s.trim());
    }
    if (raw instanceof List<?> list) {
      HashSet<String> out = new HashSet<>();
      for (Object value : list) {
        if (value == null) {
          continue;
        }
        String s = String.valueOf(value).trim();
        if (!s.isEmpty()) {
          out.add(s);
        }
      }
      return Set.copyOf(out);
    }
    return Set.of(String.valueOf(raw));
  }

  private static <E extends Enum<E>> Set<E> parseEnumSet(Object raw, Class<E> type, String path) {
    if (raw == null) {
      return Set.of();
    }
    HashSet<E> out = new HashSet<>();
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        if (value == null) {
          continue;
        }
        out.add(parseEnumValue(String.valueOf(value), type, path));
      }
      return Set.copyOf(out);
    }
    out.add(parseEnumValue(String.valueOf(raw), type, path));
    return Set.copyOf(out);
  }

  private static <E extends Enum<E>> E parseEnumValue(String raw, Class<E> type, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": missing value");
    }
    try {
      return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid value=" + raw);
    }
  }

  private Map<String, Object> vars(CastContext ctx, VarScope scope) {
    Map<String, Object> vars = switch (scope) {
      case CAST -> ctx.variables();
      case PLAYER -> {
        if (!(ctx.caster() instanceof Player)) {
          yield ctx.variables();
        }
        yield playerVars.computeIfAbsent(ctx.caster().getUniqueId(), k -> new java.util.HashMap<>());
      }
      case ENTITY -> {
        LivingEntity entity = lastEntity(ctx);
        if (entity == null) {
          entity = ctx.caster();
        }
        yield entityVars.computeIfAbsent(entity.getUniqueId(), k -> new java.util.HashMap<>());
      }
      case ABILITY -> abilityVars.computeIfAbsent(ctx.abilityId(), k -> new java.util.HashMap<>());
    };
    pruneExpired(vars, varExpirations(ctx, scope), ctx.tick());
    return vars;
  }

  private static int maxVars(VarScope scope) {
    return switch (scope) {
      case CAST -> MAX_CAST_VARS;
      case PLAYER -> MAX_PLAYER_VARS;
      case ENTITY -> MAX_ENTITY_VARS;
      case ABILITY -> MAX_ABILITY_VARS;
    };
  }

  private Map<String, Long> varExpirations(CastContext ctx, VarScope scope) {
    return switch (scope) {
      case CAST -> ctx.state().expirations();
      case PLAYER -> {
        if (!(ctx.caster() instanceof Player)) {
          yield ctx.state().expirations();
        }
        yield playerVarExpirations.computeIfAbsent(ctx.caster().getUniqueId(), k -> new java.util.HashMap<>());
      }
      case ENTITY -> {
        LivingEntity entity = lastEntity(ctx);
        if (entity == null) {
          entity = ctx.caster();
        }
        yield entityVarExpirations.computeIfAbsent(entity.getUniqueId(), k -> new java.util.HashMap<>());
      }
      case ABILITY -> abilityVarExpirations.computeIfAbsent(ctx.abilityId(), k -> new java.util.HashMap<>());
    };
  }

  private void pruneExpired(Map<String, Object> vars, Map<String, Long> expirations, long tick) {
    if (expirations.isEmpty()) {
      return;
    }
    java.util.Iterator<Map.Entry<String, Long>> it = expirations.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Long> entry = it.next();
      Long expiresAt = entry.getValue();
      if (expiresAt != null && expiresAt <= tick) {
        vars.remove(entry.getKey());
        it.remove();
      }
    }
  }

  private static ActionHandle scheduledHandle(EffectsEngine.ScheduledHandle handle, AtomicBoolean done) {
    return new ActionHandle() {
      @Override
      public boolean cancel() {
        boolean cancelled = handle.cancel();
        done.set(true);
        return cancelled;
      }

      @Override
      public boolean isDone() {
        return done.get() || handle.isCancelled();
      }
    };
  }

  private boolean setVar(CastContext ctx, VarScope scope, String key, Object value) {
    return setVar(ctx, scope, key, value, null);
  }

  private boolean setVar(CastContext ctx, VarScope scope, String key, Object value, Long ttlTicks) {
    Map<String, Object> vars = vars(ctx, scope);
    Map<String, Long> expirations = varExpirations(ctx, scope);
    if (value == null) {
      vars.remove(key);
      expirations.remove(key);
      return true;
    }
    if (vars.containsKey(key)) {
      vars.put(key, value);
      updateExpiration(expirations, key, ttlTicks, ctx.tick());
      return true;
    }
    int limit = maxVars(scope);
    if (vars.size() >= limit) {
      if (ctx.engine().isDebugEnabled()) {
        ctx.engine().debug("dsl vars: scope=" + scope.name().toLowerCase(Locale.ROOT) + " cap reached (" + limit + ")");
      }
      return false;
    }
    vars.put(key, value);
    updateExpiration(expirations, key, ttlTicks, ctx.tick());
    return true;
  }

  private void updateExpiration(Map<String, Long> expirations, String key, Long ttlTicks, long tick) {
    if (ttlTicks == null || ttlTicks <= 0) {
      expirations.remove(key);
      return;
    }
    expirations.put(key, tick + ttlTicks);
  }

  private long evalTtlTicks(NumValue ttlValue, CastContext ctx) {
    if (ttlValue == null) {
      return -1L;
    }
    double value = evalDouble(ttlValue, ctx);
    if (!Double.isFinite(value) || value <= 0.0) {
      return -1L;
    }
    return (long) Math.ceil(value);
  }

  private static InteractTrigger parseInteractTrigger(String raw, String path) {
    if (raw == null) {
      return InteractTrigger.RIGHT_CLICK;
    }
    String s = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (s) {
      case "right", "right_click", "rightclick" -> InteractTrigger.RIGHT_CLICK;
      case "left", "left_click", "leftclick" -> InteractTrigger.LEFT_CLICK;
      default -> throw new IllegalArgumentException(path + ": invalid click=" + raw + " (use RIGHT_CLICK or LEFT_CLICK)");
    };
  }

  private static EnumSet<EquipmentSlot> parsePassiveSlots(Map<String, Object> node, String path) {
    String key = node.containsKey("slots") ? "slots" : "slot";
    Object raw = node.get(key);
    if (raw == null) {
      return EnumSet.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);
    }
    List<String> values = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object v : list) {
        values.add(String.valueOf(v));
      }
    } else {
      values.add(String.valueOf(raw));
    }

    EnumSet<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
    for (String value : values) {
      String token = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
      switch (token) {
        case "hand", "main_hand", "main", "primary" -> slots.add(EquipmentSlot.HAND);
        case "off_hand", "offhand", "off", "secondary" -> slots.add(EquipmentSlot.OFF_HAND);
        case "hands", "any_hand", "anyhand" -> {
          slots.add(EquipmentSlot.HAND);
          slots.add(EquipmentSlot.OFF_HAND);
        }
        case "head", "helmet" -> slots.add(EquipmentSlot.HEAD);
        case "chest", "chestplate" -> slots.add(EquipmentSlot.CHEST);
        case "legs", "leggings" -> slots.add(EquipmentSlot.LEGS);
        case "feet", "boots" -> slots.add(EquipmentSlot.FEET);
        case "armor" -> {
          slots.add(EquipmentSlot.HEAD);
          slots.add(EquipmentSlot.CHEST);
          slots.add(EquipmentSlot.LEGS);
          slots.add(EquipmentSlot.FEET);
        }
        case "all", "any" -> {
          slots.add(EquipmentSlot.HAND);
          slots.add(EquipmentSlot.OFF_HAND);
          slots.add(EquipmentSlot.HEAD);
          slots.add(EquipmentSlot.CHEST);
          slots.add(EquipmentSlot.LEGS);
          slots.add(EquipmentSlot.FEET);
        }
        default -> throw new IllegalArgumentException(path + "." + key + ": invalid slot=" + value);
      }
    }
    if (slots.isEmpty()) {
      throw new IllegalArgumentException(path + "." + key + ": no valid slots specified");
    }
    return slots;
  }

  private static ItemMatcher compileItemMatcher(Object raw, String path) {
    Map<String, Object> node = castMap(raw, path);
    String type = requireString(node, "type", path + ".type").trim().toLowerCase(Locale.ROOT);
    ItemMatcher matcher = switch (type) {
      case "any_non_air", "any-non-air", "any_nonair", "any" -> ItemMatchers.anyNonAir();
      case "material" -> {
        String m = requireString(node, "material", path + ".material").trim().toUpperCase(Locale.ROOT);
        Material mat = Material.matchMaterial(m);
        if (mat == null) {
          throw new IllegalArgumentException(path + ".material: unknown material: " + m);
        }
        yield ItemMatchers.material(mat);
      }
      case "custom_model_data", "custom-model-data", "cmd" -> {
        Object v = node.containsKey("value") ? node.get("value") : (node.containsKey("cmd") ? node.get("cmd") : node.get("customModelData"));
        if (v == null) {
          throw new IllegalArgumentException(path + ": missing value/cmd/customModelData");
        }
        int cmd = (int) requireDouble(java.util.Map.of("value", v), "value", path + ".value");
        yield ItemMatchers.customModelData(cmd);
      }
      case "pdc_tag", "pdc-tag", "tag" -> {
        String keyStr = requireString(node, "key", path + ".key");
        NamespacedKey key = NamespacedKey.fromString(keyStr);
        if (key == null) {
          throw new IllegalArgumentException(path + ".key: invalid NamespacedKey: " + keyStr);
        }
        yield ItemMatchers.tag(key);
      }
      case "lore_contains", "lore-contains" -> ItemMatchers.loreContains(requireString(node, "text", path + ".text"));
      case "and" -> {
        List<?> list = mapList(node, "matchers", path + ".matchers");
        if (list.isEmpty()) {
          throw new IllegalArgumentException(path + ".matchers: must not be empty");
        }
        ItemMatcher out = null;
        for (int i = 0; i < list.size(); i++) {
          ItemMatcher m = compileItemMatcher(list.get(i), path + ".matchers[" + i + "]");
          out = out == null ? m : ItemMatchers.and(out, m);
        }
        yield out;
      }
      case "or" -> {
        List<?> list = mapList(node, "matchers", path + ".matchers");
        if (list.isEmpty()) {
          throw new IllegalArgumentException(path + ".matchers: must not be empty");
        }
        ItemMatcher out = null;
        for (int i = 0; i < list.size(); i++) {
          ItemMatcher m = compileItemMatcher(list.get(i), path + ".matchers[" + i + "]");
          out = out == null ? m : ItemMatchers.or(out, m);
        }
        yield out;
      }
      default -> throw new IllegalArgumentException(path + ".type: unknown matcher type: " + type);
    };
    return matcher;
  }

  private dev.patric.dungeonsreborn.effects.conditions.Condition compileCondition(Object raw, String path) {
    Map<String, Object> node = castMap(raw, path);
    String type = requireString(node, "type", path + ".type").trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case "always" -> Conditions.always();
      case "sneaking" -> Conditions.sneaking();
      case "permission" -> Conditions.permission(requireString(node, "permission", path + ".permission"));
      case "has_item_tag", "has-item-tag" -> {
        String keyStr = requireString(node, "key", path + ".key");
        NamespacedKey key = NamespacedKey.fromString(keyStr);
        if (key == null) {
          throw new IllegalArgumentException(path + ".key: invalid NamespacedKey: " + keyStr);
        }
        yield Conditions.hasItemTag(key);
      }
      case "caster_has_tag", "caster-has-tag", "has_tag", "has-tag" -> {
        String tag = requireString(node, "tag", path + ".tag");
        yield Conditions.casterHasTag(tag);
      }
      case "caster_lacks_tag", "caster-lacks-tag", "lacks_tag", "lacks-tag" -> {
        String tag = requireString(node, "tag", path + ".tag");
        yield Conditions.casterLacksTag(tag);
      }
      case "item_match", "item-match", "has_item", "has-item" -> {
        Object itemRaw = require(node, "item", path + ".item");
        ItemMatcher matcher = compileItemMatcher(itemRaw, path + ".item");
        List<EquipmentSlot> slots = parseSlots(node.get("slot"), path + ".slot");
        yield ctx -> {
          if (!(ctx.caster() instanceof Player player)) {
            return false;
          }
          for (EquipmentSlot slot : slots) {
            ItemStack item = equipmentItem(player, slot);
            if (matcher.matches(player, item)) {
              return true;
            }
          }
          return false;
        };
      }
      case "world" -> {
        Set<String> worlds = parseStringSet(node.get("worlds"), node.get("world"), path);
        if (worlds.isEmpty()) {
          throw new IllegalArgumentException(path + ".worlds: expected at least one world");
        }
        yield ctx -> matchesWorld(ctx.world(), worlds);
      }
      case "biome" -> {
        Set<Biome> biomes = parseBiomeSet(node.get("biomes"), node.get("biome"), path);
        if (biomes.isEmpty()) {
          throw new IllegalArgumentException(path + ".biomes: expected at least one biome");
        }
        yield ctx -> {
          World world = ctx.world();
          if (world == null) {
            return false;
          }
          return biomes.contains(world.getBiome(ctx.origin()));
        };
      }
      case "time_between", "time-between" -> {
        NumValue minTime = numValue(node, "minTime", 0.0, path);
        NumValue maxTime = numValue(node, "maxTime", 23999.0, path);
        yield ctx -> {
          World world = ctx.world();
          if (world == null) {
            return false;
          }
          long time = world.getTime();
          long min = Math.round(evalDouble(minTime, ctx));
          long max = Math.round(evalDouble(maxTime, ctx));
          return timeBetween(time, min, max);
        };
      }
      case "region" -> {
        QuestRegion region = parseRegion(node, path);
        yield ctx -> region.contains(ctx.origin());
      }
      case "in_party", "in-party" -> {
        yield ctx -> partyOf(ctx) != null;
      }
      case "party_size_gte", "party-size-gte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> {
          Party party = partyOf(ctx);
          return party != null && party.size() >= Math.round(evalDouble(threshold, ctx));
        };
      }
      case "party_size_lte", "party-size-lte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> {
          Party party = partyOf(ctx);
          return party != null && party.size() <= Math.round(evalDouble(threshold, ctx));
        };
      }
      case "party_leader", "party-leader" -> {
        yield ctx -> {
          Party party = partyOf(ctx);
          return party != null && party.leader().equals(ctx.caster().getUniqueId());
        };
      }
      case "health_gte", "health-gte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> ctx.caster().getHealth() >= evalDouble(threshold, ctx);
      }
      case "health_lte", "health-lte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> ctx.caster().getHealth() <= evalDouble(threshold, ctx);
      }
      case "health_pct_gte", "health-pct-gte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> {
          AttributeInstance attr = ctx.caster().getAttribute(Attribute.MAX_HEALTH);
          double max = attr != null ? attr.getValue() : 20.0;
          return max > 0.0 && (ctx.caster().getHealth() / max) >= evalDouble(threshold, ctx);
        };
      }
      case "health_pct_lte", "health-pct-lte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> {
          AttributeInstance attr = ctx.caster().getAttribute(Attribute.MAX_HEALTH);
          double max = attr != null ? attr.getValue() : 20.0;
          return max > 0.0 && (ctx.caster().getHealth() / max) <= evalDouble(threshold, ctx);
        };
      }
      case "mana_gte", "mana-gte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> manaValue(ctx) >= evalDouble(threshold, ctx);
      }
      case "mana_lte", "mana-lte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> manaValue(ctx) <= evalDouble(threshold, ctx);
      }
      case "mana_pct_gte", "mana-pct-gte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> {
          double max = manaMax(ctx);
          return max > 0.0 && (manaValue(ctx) / max) >= evalDouble(threshold, ctx);
        };
      }
      case "mana_pct_lte", "mana-pct-lte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> {
          double max = manaMax(ctx);
          return max > 0.0 && (manaValue(ctx) / max) <= evalDouble(threshold, ctx);
        };
      }
      case "level_gte", "level-gte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> levelValue(ctx) >= evalDouble(threshold, ctx);
      }
      case "level_lte", "level-lte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> levelValue(ctx) <= evalDouble(threshold, ctx);
      }
      case "xp_gte", "xp-gte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> xpValue(ctx) >= evalDouble(threshold, ctx);
      }
      case "xp_lte", "xp-lte" -> {
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> xpValue(ctx) <= evalDouble(threshold, ctx);
      }
      case "stat_gte", "stat-gte" -> {
        String stat = requireString(node, "stat", path + ".stat");
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> statValue(ctx, stat) >= evalDouble(threshold, ctx);
      }
      case "stat_lte", "stat-lte" -> {
        String stat = requireString(node, "stat", path + ".stat");
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> statValue(ctx, stat) <= evalDouble(threshold, ctx);
      }
      case "has_target", "has-target" -> ctx -> lastEntity(ctx) != null;
      case "chance" -> {
        NumValue p = numValue(node, "probability", 0.5, path);
        yield ctx -> {
          double chance = evalDouble(p, ctx);
          if (chance < 0.0) {
            chance = 0.0;
          } else if (chance > 1.0) {
            chance = 1.0;
          }
          return ctx.rng().nextDouble() < chance;
        };
      }
      case "var_present", "var-present" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        yield ctx -> vars(ctx, scope).get(key) != null;
      }
      case "var_equals", "var-equals" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        ValueSupplier expected = varValue(node.get("value"), path + ".value");
        boolean ignoreCase = bool(node, "ignoreCase", false);
        yield ctx -> {
          Object actual = vars(ctx, scope).get(key);
          Object exp = expected.eval(ctx);
          if (actual == null && exp == null) {
            return true;
          }
          if (actual == null || exp == null) {
            return false;
          }
          if (exp instanceof Number en) {
            double actualNum = numericVar(actual, Double.NaN);
            return !Double.isNaN(actualNum) && Double.compare(actualNum, en.doubleValue()) == 0;
          }
          String as = String.valueOf(actual);
          String es = String.valueOf(exp);
          return ignoreCase ? as.equalsIgnoreCase(es) : as.equals(es);
        };
      }
      case "var_gte", "var-gte" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) >= evalDouble(threshold, ctx);
      }
      case "var_lte", "var-lte" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) <= evalDouble(threshold, ctx);
      }
      case "var_gt", "var-gt" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) > evalDouble(threshold, ctx);
      }
      case "var_lt", "var-lt" -> {
        String key = requireString(node, "key", path + ".key");
        VarScope scope = parseVarScope(string(node, "scope", null), path + ".scope", VarScope.CAST);
        NumValue threshold = requireNumValue(node, "value", path + ".value");
        yield ctx -> numericVar(vars(ctx, scope).get(key), 0.0) < evalDouble(threshold, ctx);
      }
      case "not" -> {
        var inner = compileCondition(require(node, "condition", path + ".condition"), path + ".condition");
        yield ctx -> !inner.test(ctx);
      }
      case "and" -> {
        Object v = require(node, "conditions", path + ".conditions");
        if (!(v instanceof List<?> list) || list.isEmpty()) {
          throw new IllegalArgumentException(path + ".conditions: expected non-empty list");
        }
        var compiled = new ArrayList<dev.patric.dungeonsreborn.effects.conditions.Condition>(list.size());
        for (int i = 0; i < list.size(); i++) {
          compiled.add(compileCondition(list.get(i), path + ".conditions[" + i + "]"));
        }
        yield ctx -> {
          for (var c : compiled) {
            if (!c.test(ctx)) {
              return false;
            }
          }
          return true;
        };
      }
      case "or" -> {
        Object v = require(node, "conditions", path + ".conditions");
        if (!(v instanceof List<?> list) || list.isEmpty()) {
          throw new IllegalArgumentException(path + ".conditions: expected non-empty list");
        }
        var compiled = new ArrayList<dev.patric.dungeonsreborn.effects.conditions.Condition>(list.size());
        for (int i = 0; i < list.size(); i++) {
          compiled.add(compileCondition(list.get(i), path + ".conditions[" + i + "]"));
        }
        yield ctx -> {
          for (var c : compiled) {
            if (c.test(ctx)) {
              return true;
            }
          }
          return false;
        };
      }
      default -> throw new IllegalArgumentException(path + ".type: unknown condition type: " + type);
    };
  }

  private static List<EquipmentSlot> parseSlots(Object raw, String path) {
    if (raw == null) {
      return List.of(EquipmentSlot.HAND);
    }
    List<String> values = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object o : list) {
        if (o != null) {
          values.add(String.valueOf(o));
        }
      }
    } else {
      values.add(String.valueOf(raw));
    }
    ArrayList<EquipmentSlot> slots = new ArrayList<>();
    for (String value : values) {
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      if (normalized.isEmpty()) {
        continue;
      }
      switch (normalized) {
        case "ANY", "ALL" -> {
          slots.add(EquipmentSlot.HAND);
          slots.add(EquipmentSlot.OFF_HAND);
          slots.add(EquipmentSlot.HEAD);
          slots.add(EquipmentSlot.CHEST);
          slots.add(EquipmentSlot.LEGS);
          slots.add(EquipmentSlot.FEET);
        }
        case "ARMOR", "ARMOUR" -> {
          slots.add(EquipmentSlot.HEAD);
          slots.add(EquipmentSlot.CHEST);
          slots.add(EquipmentSlot.LEGS);
          slots.add(EquipmentSlot.FEET);
        }
        case "HAND", "MAIN_HAND", "MAINHAND" -> slots.add(EquipmentSlot.HAND);
        case "OFF_HAND", "OFFHAND" -> slots.add(EquipmentSlot.OFF_HAND);
        case "HEAD", "HELMET" -> slots.add(EquipmentSlot.HEAD);
        case "CHEST", "CHESTPLATE" -> slots.add(EquipmentSlot.CHEST);
        case "LEGS", "LEGGINGS" -> slots.add(EquipmentSlot.LEGS);
        case "FEET", "BOOTS" -> slots.add(EquipmentSlot.FEET);
        default -> throw new IllegalArgumentException(path + ": unknown slot " + value);
      }
    }
    if (slots.isEmpty()) {
      throw new IllegalArgumentException(path + ": expected at least one slot");
    }
    return List.copyOf(slots);
  }

  private static ItemStack equipmentItem(LivingEntity living, EquipmentSlot slot) {
    if (living.getEquipment() == null) {
      return null;
    }
    return switch (slot) {
      case HAND -> living.getEquipment().getItemInMainHand();
      case OFF_HAND -> living.getEquipment().getItemInOffHand();
      case HEAD -> living.getEquipment().getHelmet();
      case CHEST -> living.getEquipment().getChestplate();
      case LEGS -> living.getEquipment().getLeggings();
      case FEET -> living.getEquipment().getBoots();
      default -> null;
    };
  }

  private static Set<Biome> parseBiomeSet(Object primary, Object secondary, String path) {
    Set<String> raw = parseStringSet(primary, secondary, path);
    if (raw.isEmpty()) {
      return Set.of();
    }
    Set<Biome> out = new HashSet<>();
    var biomeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
    for (String value : raw) {
      String trimmed = value.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      NamespacedKey key = NamespacedKey.fromString(trimmed.toLowerCase(Locale.ROOT));
      if (key == null) {
        key = NamespacedKey.fromString("minecraft:" + trimmed.toLowerCase(Locale.ROOT));
      }
      Biome biome = key == null ? null : biomeRegistry.get(key);
      if (biome == null) {
        throw new IllegalArgumentException(path + ": unknown biome " + value);
      }
      out.add(biome);
    }
    return out;
  }

  private static boolean matchesWorld(World world, Set<String> worlds) {
    if (world == null) {
      return false;
    }
    if (worlds.contains(world.getName())) {
      return true;
    }
    return worlds.contains(world.getKey().toString());
  }

  private static boolean timeBetween(long time, long min, long max) {
    if (min <= max) {
      return time >= min && time <= max;
    }
    return time >= min || time <= max;
  }

  private static QuestRegion parseRegion(Map<String, Object> node, String path) {
    Object regionRaw = node.get("region");
    Map<String, Object> data = regionRaw instanceof Map<?, ?> || regionRaw instanceof ConfigurationSection
        ? castMap(regionRaw, path + ".region")
        : node;
    String world = requireString(data, "world", path + ".world");
    double x = requireDouble(data, "x", path + ".x");
    double y = requireDouble(data, "y", path + ".y");
    double z = requireDouble(data, "z", path + ".z");
    double radius = requireDouble(data, "radius", path + ".radius");
    if (radius < 0.0) {
      throw new IllegalArgumentException(path + ".radius: must be >= 0");
    }
    return new QuestRegion(world, x, y, z, radius);
  }

  private static List<QuestRegion> parseRegionList(Object raw, String path) {
    if (raw == null) {
      return List.of();
    }
    List<QuestRegion> regions = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Map<String, Object> regionNode = castMap(list.get(i), path + "[" + i + "]");
        regions.add(parseRegion(regionNode, path + "[" + i + "]"));
      }
    } else {
      Map<String, Object> regionNode = castMap(raw, path);
      regions.add(parseRegion(regionNode, path));
    }
    return regions;
  }

  private static boolean regionContainsAny(List<QuestRegion> regions, Location origin) {
    if (origin == null) {
      return false;
    }
    for (QuestRegion region : regions) {
      if (region.contains(origin)) {
        return true;
      }
    }
    return false;
  }

  private dev.patric.dungeonsreborn.effects.actions.Action wrapUnsafeAction(
      dev.patric.dungeonsreborn.effects.actions.Action action,
      String type,
      String path) {
    String permission = unsafePermission;
    if (permission == null) {
      return action;
    }
    return ctx -> {
      Player player = ctx.caster() instanceof Player p ? p : null;
      if (player == null || !player.hasPermission(permission)) {
        if (ctx.engine().isDebugEnabled()) {
          ctx.engine().debug("blocked unsafe action " + type + " at " + path + " (missing " + permission + ")");
        }
        return;
      }
      action.execute(ctx);
    };
  }

  private Party partyOf(CastContext ctx) {
    if (!(ctx.caster() instanceof Player player)) {
      return null;
    }
    if (plugin instanceof DungeonsRebornPlugin dr) {
      PartyService service = dr.partyService();
      return service == null ? null : service.partyOf(player);
    }
    return null;
  }

  private double manaValue(CastContext ctx) {
    if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
      return ctx.engine().manaProvider().get(player);
    }
    return 0.0;
  }

  private double manaMax(CastContext ctx) {
    if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
      return ctx.engine().manaProvider().getMax(player);
    }
    return 0.0;
  }

  private void addManaMinimumRequirement(AbilitySpec.Builder builder, String base, double minMana, double minManaPct,
      Component message) {
    Component fallback = message != null ? message : Component.text("Not enough mana.");
    if (minMana > 0.0) {
      builder.require(ctx -> manaValue(ctx) >= minMana, fallback);
    }
    if (minManaPct > 0.0) {
      builder.require(ctx -> {
        double max = manaMax(ctx);
        return max > 0.0 && (manaValue(ctx) / max) >= minManaPct;
      }, fallback);
    }
  }

  private double levelValue(CastContext ctx) {
    if (!(ctx.caster() instanceof Player player)) {
      return 0.0;
    }
    if (plugin instanceof DungeonsRebornPlugin dr && dr.customXpService() != null) {
      CustomXpProfile profile = dr.customXpService().getOrCreate(player.getUniqueId());
      return profile.level();
    }
    if (plugin instanceof DungeonsRebornPlugin dr && dr.progressionService() != null) {
      PlayerProgression progression = dr.progressionService().getOrCreate(player.getUniqueId());
      return progression.level();
    }
    return 0.0;
  }

  private double xpValue(CastContext ctx) {
    if (!(ctx.caster() instanceof Player player)) {
      return 0.0;
    }
    if (plugin instanceof DungeonsRebornPlugin dr && dr.customXpService() != null) {
      CustomXpProfile profile = dr.customXpService().getOrCreate(player.getUniqueId());
      return profile.points();
    }
    if (plugin instanceof DungeonsRebornPlugin dr && dr.progressionService() != null) {
      PlayerProgression progression = dr.progressionService().getOrCreate(player.getUniqueId());
      return progression.points();
    }
    return 0.0;
  }

  private double statValue(CastContext ctx, String stat) {
    if (!(ctx.caster() instanceof Player player)) {
      return 0.0;
    }
    if (!(plugin instanceof DungeonsRebornPlugin dr)) {
      return 0.0;
    }
    ProgressionService progression = dr.progressionService();
    if (progression == null) {
      return 0.0;
    }
    PlayerProgression p = progression.getOrCreate(player.getUniqueId());
    String key = stat.trim().toLowerCase(Locale.ROOT);
    return switch (key) {
      case "strength" -> p.strength();
      case "dexterity" -> p.dexterity();
      case "intelligence" -> p.intelligence();
      case "vitality" -> p.vitality();
      case "skill_points", "skillpoints" -> p.skillPoints();
      case "skill_tree_points", "skilltreepoints" -> p.skillTreePoints();
      case "mana_max", "max_mana" -> p.maxMana();
      default -> 0.0;
    };
  }

  private dev.patric.dungeonsreborn.effects.actions.Action compileAction(Map<String, Object> node, String path, java.util.ArrayDeque<String> includeStack) {
    migrateDeprecatedKeys(node, path, DEPRECATED_ACTION_KEYS);
    Object rawType = node.get("type");
    if (rawType == null) {
      throw new IllegalArgumentException(path + ": missing type");
    }
    String type = String.valueOf(rawType);
    if (type.isBlank()) {
      throw new IllegalArgumentException(path + ": type is blank");
    }
    type = type.toLowerCase(Locale.ROOT);

    dev.patric.dungeonsreborn.effects.actions.Action parsed = ActionParsers.parse(actionParserContext, type, node, path, includeStack);
    if (parsed != null) {
      return parsed;
    }

    dev.patric.dungeonsreborn.effects.actions.Action action = switch (type) {
      case "sound" -> {
        Sound sound = soundValue(node, "sound", path + ".sound");
        NumValue volume = numValue(node, "volume", 1.0, path);
        NumValue pitch = numValue(node, "pitch", 1.0, path);
        yield ctx -> Actions.sound(sound, (float) evalDouble(volume, ctx), (float) evalDouble(pitch, ctx)).execute(ctx);
      }
      case "animate" -> {
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Map<String, Object> actionNode = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action tickAction = compileAction(actionNode, path + ".action", includeStack);
        yield ctx -> {
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Actions.animate(duration, period, easing, (tickCtx, t) -> {
            CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
            withTempVar(exec, VarScope.CAST, "t", t, () -> tickAction.execute(exec));
          }).execute(ctx);
        };
      }
      case "animate_shape" -> {
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Object raw = pick(node, "shape", "action");
        if (raw == null) {
          throw new IllegalArgumentException(path + ".shape: missing shape/action");
        }
        Map<String, Object> shapeNode = castMap(raw, path + ".shape");
        dev.patric.dungeonsreborn.effects.actions.Action tickAction = compileAction(shapeNode, path + ".shape", includeStack);
        yield ctx -> {
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Actions.animate(duration, period, easing, (tickCtx, t) -> {
            CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
            withTempVar(exec, VarScope.CAST, "t", t, () -> tickAction.execute(exec));
          }).execute(ctx);
        };
      }
      case "motion" -> {
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        String modeRaw = string(node, "mode", "translate");
        dev.patric.dungeonsreborn.effects.actions.Actions.MotionMode mode = parseMotionMode(modeRaw, path + ".mode");
        NumValue velocityX = numValue(node, "velocityX", 0.0, path);
        NumValue velocityY = numValue(node, "velocityY", 0.0, path);
        NumValue velocityZ = numValue(node, "velocityZ", 0.0, path);
        NumValue radius = numValue(node, "radius", 0.0, path);
        NumValue turns = numValue(node, "turns", 1.0, path);
        NumValue vertical = numValue(node, "vertical", 0.0, path);
        NumValue drift = numValue(node, "drift", 0.0, path);
        NumValue driftVertical = numValue(node, "driftVertical", 0.0, path);
        NumValue driftSpeed = numValue(node, "driftSpeed", 0.35, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        Object raw = pick(node, "action", "shape");
        if (raw == null) {
          throw new IllegalArgumentException(path + ".action: missing action/shape");
        }
        Map<String, Object> actionNode = castMap(raw, path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action inner = compileAction(actionNode, path + ".action", includeStack);
        yield ctx -> {
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Vector vel = new Vector(evalDouble(velocityX, ctx), evalDouble(velocityY, ctx), evalDouble(velocityZ, ctx));
          double radiusVal = evalDouble(radius, ctx);
          double turnsVal = evalDouble(turns, ctx);
          double verticalVal = evalDouble(vertical, ctx);
          double driftVal = evalDouble(drift, ctx);
          double driftVerticalVal = evalDouble(driftVertical, ctx);
          double driftSpeedVal = evalDouble(driftSpeed, ctx);
          java.util.function.Function<CastContext, Location> base = baseCtx -> {
            CastContext ref = followCaster ? followCasterContext(baseCtx) : baseCtx;
            return resolveAtWithOffsets(ref, at, forward, right, up);
          };
          dev.patric.dungeonsreborn.effects.actions.Actions.MotionSpec motion =
              new dev.patric.dungeonsreborn.effects.actions.Actions.MotionSpec(
                  mode,
                  base,
                  vel,
                  radiusVal,
                  turnsVal,
                  verticalVal,
                  driftVal,
                  driftVerticalVal,
                  driftSpeedVal);
          Actions.motion(duration, period, easing, motion, (tickCtx, t) ->
              withTempVar(tickCtx, VarScope.CAST, "t", t, () -> inner.execute(tickCtx))).execute(ctx);
        };
      }
      case "state_machine" -> {
        NumValue chargeTicks = numValue(node, "chargeTicks", 20.0, path);
        NumValue sustainTicks = numValue(node, "sustainTicks", 40.0, path);
        NumValue releaseTicks = numValue(node, "releaseTicks", 20.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Object chargeRaw = node.get("charge");
        Object sustainRaw = node.get("sustain");
        Object releaseRaw = node.get("release");
        if (chargeRaw == null && sustainRaw == null && releaseRaw == null) {
          throw new IllegalArgumentException(path + ": missing charge/sustain/release actions");
        }
        dev.patric.dungeonsreborn.effects.actions.Action chargeAction = chargeRaw == null
            ? Actions.noop()
            : compileAction(castMap(chargeRaw, path + ".charge"), path + ".charge", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action sustainAction = sustainRaw == null
            ? Actions.noop()
            : compileAction(castMap(sustainRaw, path + ".sustain"), path + ".sustain", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action releaseAction = releaseRaw == null
            ? Actions.noop()
            : compileAction(castMap(releaseRaw, path + ".release"), path + ".release", includeStack);
        yield ctx -> {
          long charge = Math.max(0L, evalLong(chargeTicks, ctx));
          long sustain = Math.max(0L, evalLong(sustainTicks, ctx));
          long release = Math.max(0L, evalLong(releaseTicks, ctx));
          long period = Math.max(1L, evalLong(periodTicks, ctx));
          java.util.List<Actions.TimelineEntry> entries = new java.util.ArrayList<>();
          long at = 0L;
          if (charge > 0) {
            dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx -> {
              Actions.animate(charge, period, easing, (tickCtx, t) -> {
                CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
                withTempVar(exec, VarScope.CAST, "phase", "charge",
                    () -> withTempVar(exec, VarScope.CAST, "t", t, () -> chargeAction.execute(exec)));
              }).execute(stepCtx);
            };
            entries.add(new Actions.TimelineEntry(at, step));
            at += charge;
          }
          if (sustain > 0) {
            dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx -> {
              Actions.animate(sustain, period, easing, (tickCtx, t) -> {
                CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
                withTempVar(exec, VarScope.CAST, "phase", "sustain",
                    () -> withTempVar(exec, VarScope.CAST, "t", t, () -> sustainAction.execute(exec)));
              }).execute(stepCtx);
            };
            entries.add(new Actions.TimelineEntry(at, step));
            at += sustain;
          }
          if (release > 0) {
            dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx -> {
              Actions.animate(release, period, easing, (tickCtx, t) -> {
                CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
                withTempVar(exec, VarScope.CAST, "phase", "release",
                    () -> withTempVar(exec, VarScope.CAST, "t", t, () -> releaseAction.execute(exec)));
              }).execute(stepCtx);
            };
            entries.add(new Actions.TimelineEntry(at, step));
          }
          if (entries.isEmpty()) {
            return;
          }
          Actions.timeline(entries).execute(ctx);
        };
      }
      case "burst" -> {
        NumValue timesValue = numValue(node, "times", 6.0, path);
        NumValue spacingValue = numValue(node, "spacingTicks", 0.0, path);
        NumValue delayValue = numValue(node, "delayTicks", 0.0, path);
        Map<String, Object> actionNode = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action inner = compileAction(actionNode, path + ".action", includeStack);
        yield ctx -> {
          int times = Math.max(1, evalInt(timesValue, ctx));
          if (times > MAX_REPEAT_TIMES) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("burst capped: " + times + " -> " + MAX_REPEAT_TIMES);
            }
            times = MAX_REPEAT_TIMES;
          }
          long spacing = Math.max(0L, evalLong(spacingValue, ctx));
          long delay = Math.max(0L, evalLong(delayValue, ctx));
          java.util.List<Actions.TimelineEntry> entries = new java.util.ArrayList<>(times);
          for (int i = 0; i < times; i++) {
            double t = times <= 1 ? 1.0 : i / (double) (times - 1);
            long at = delay + (spacing * i);
            dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx ->
                withTempVar(stepCtx, VarScope.CAST, "t", t, () -> inner.execute(stepCtx));
            entries.add(new Actions.TimelineEntry(at, step));
          }
          Actions.timeline(entries).execute(ctx);
        };
      }
      case "pulse", "loop" -> {
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 10.0, path);
        boolean followCaster = bool(node, "followCaster", false);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Map<String, Object> actionNode = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action tickAction = compileAction(actionNode, path + ".action", includeStack);
        yield ctx -> {
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Actions.animate(duration, period, easing, (tickCtx, t) -> {
            CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
            withTempVar(exec, VarScope.CAST, "t", t, () -> tickAction.execute(exec));
          }).execute(ctx);
        };
      }
      case "trail" -> {
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 2.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Map<String, Object> actionNode = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action tickAction = compileAction(actionNode, path + ".action", includeStack);
        yield ctx -> {
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Actions.animate(duration, period, easing, (tickCtx, t) -> {
            CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
            withTempVar(exec, VarScope.CAST, "t", t, () -> tickAction.execute(exec));
          }).execute(ctx);
        };
      }
      case "attach" -> {
        String anchorRaw = string(node, "anchor", "caster");
        String pointRaw = string(node, "point", null);
        AnchorMode anchor = parseAnchorMode(anchorRaw, path + ".anchor");
        AnchorPoint point = parseAnchorPoint(pointRaw, anchor, path + ".point");
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        Map<String, Object> actionNode = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action inner = compileAction(actionNode, path + ".action", includeStack);
        Frame frame = frameForAnchor(anchor, point);
        yield ctx -> {
          double f = evalDouble(forward, ctx);
          double r = evalDouble(right, ctx);
          double u = evalDouble(up, ctx);
          Actions.attach(inner, Frames.withOffsets(frame, f, r, u)).execute(ctx);
        };
      }
      case "follow", "lock_to_target", "lock_target" -> {
        String anchorRaw = string(node, "anchor", "caster");
        String pointRaw = string(node, "point", null);
        AnchorMode anchor = parseAnchorMode(anchorRaw, path + ".anchor");
        AnchorPoint point = parseAnchorPoint(pointRaw, anchor, path + ".point");
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 2.0, path);
        NumValue smoothing = numValue(node, "smoothing", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        Map<String, Object> actionNode = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action inner = compileAction(actionNode, path + ".action", includeStack);
        Frame frame = frameForAnchor(anchor, point);
        yield new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(1L, evalLong(durationTicks, ctx));
            long period = Math.max(1L, evalLong(periodTicks, ctx));
            double smooth = evalDouble(smoothing, ctx);
            double f = evalDouble(forward, ctx);
            double r = evalDouble(right, ctx);
            double u = evalDouble(up, ctx);
            return Actions.follow(inner, Frames.withOffsets(frame, f, r, u), duration, period, smooth)
                .executeWithHandle(ctx);
          }
        };
      }
      case "animate_realtime", "animate_real_time" -> {
        NumValue durationMillis = numValue(node, "durationMillis", 1000.0, path);
        NumValue periodMillis = numValue(node, "periodMillis", 50.0, path);
        boolean followCaster = bool(node, "followCaster", true);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        Map<String, Object> actionNode = castMap(require(node, "action", path + ".action"), path + ".action");
        dev.patric.dungeonsreborn.effects.actions.Action tickAction = compileAction(actionNode, path + ".action", includeStack);
        yield ctx -> {
          long duration = evalLong(durationMillis, ctx);
          long period = evalLong(periodMillis, ctx);
          if (duration <= 0 || period <= 0) {
            return;
          }
          Actions.animateRealTime(Duration.ofMillis(duration), Duration.ofMillis(period), easing, (tickCtx, t) -> {
            CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
            withTempVar(exec, VarScope.CAST, "t", t, () -> tickAction.execute(exec));
          }).execute(ctx);
        };
      }
      case "particles_ring" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.0, path);
        NumValue points = numValue(node, "points", 24.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final var at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          Location center = resolveAtWithOffsets(ctx, at, forward, right, up);
          var pe = ctx.engine().particles();
          if (center.getWorld() == null) {
            return;
          }
          final var world = center.getWorld();
          double r = evalDouble(radius, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          double off = evalDouble(offset, ctx);
          double ex = evalDouble(extra, ctx);
          if (r < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          dev.patric.dungeonsreborn.effects.particles.ParticleShapes.ring(center, new org.bukkit.util.Vector(0, 1, 0), r, pts,
              loc -> pe.emit(world, loc, particle, emitCount, off, off, off, ex, resolveParticleData(data, ctx, loc)));
        };
      }
      case "particles_point" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final var at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          Location loc = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (loc.getWorld() == null) {
            return;
          }
          int emitCount = evalInt(count, ctx);
          if (emitCount <= 0) {
            return;
          }
          double off = evalDouble(offset, ctx);
          double ex = evalDouble(extra, ctx);
          Object resolved = resolveParticleData(data, ctx, loc);
          ctx.engine().particles().emit(loc.getWorld(), loc, particle, emitCount, off, off, off, ex, resolved);
        };
      }
      case "particles_physics" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 8.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue velocityX = numValue(node, "velocityX", 0.0, path);
        NumValue velocityY = numValue(node, "velocityY", 0.2, path);
        NumValue velocityZ = numValue(node, "velocityZ", 0.0, path);
        NumValue spread = numValue(node, "spread", 0.08, path);
        NumValue gravity = numValue(node, "gravity", 0.03, path);
        NumValue drag = numValue(node, "drag", 0.02, path);
        NumValue steps = numValue(node, "steps", 20.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean collide = bool(node, "collide", false);
        String collisionModeRaw = string(node, "collisionMode", "STOP");
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
        try {
          collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(path + ".collisionMode: unknown mode " + collisionModeRaw);
        }
        NumValue restitution = numValue(node, "restitution", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final var at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          int emitCount = evalInt(count, ctx);
          int totalSteps = evalInt(steps, ctx);
          long period = evalLong(periodTicks, ctx);
          if (emitCount <= 0 || totalSteps <= 0 || period <= 0) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
          Actions.particlesPhysics(
              particle,
              emitCount,
              vel,
              evalDouble(spread, exec),
              evalDouble(gravity, exec),
              evalDouble(drag, exec),
              totalSteps,
              period,
              evalDouble(offset, exec),
              evalDouble(extra, exec),
              data,
              collide,
              collisionMode,
              evalDouble(restitution, exec)).execute(exec);
        };
      }
      case "particles_physics_points" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue velocityX = numValue(node, "velocityX", 0.0, path);
        NumValue velocityY = numValue(node, "velocityY", 0.2, path);
        NumValue velocityZ = numValue(node, "velocityZ", 0.0, path);
        NumValue spread = numValue(node, "spread", 0.08, path);
        NumValue gravity = numValue(node, "gravity", 0.03, path);
        NumValue drag = numValue(node, "drag", 0.02, path);
        NumValue steps = numValue(node, "steps", 20.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean collide = bool(node, "collide", false);
        String collisionModeRaw = string(node, "collisionMode", "STOP");
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
        try {
          collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(path + ".collisionMode: unknown mode " + collisionModeRaw);
        }
        NumValue restitution = numValue(node, "restitution", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        String shapeId = node.containsKey("shape") ? String.valueOf(node.get("shape")) : null;
        List<PointSpec> points;
        if (node.containsKey("points")) {
          Object rawPoints = node.get("points");
          if (!(rawPoints instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(path + ".points: expected a non-empty list");
          }
          points = new ArrayList<>(list.size());
          for (int i = 0; i < list.size(); i++) {
            points.add(pointSpec(list.get(i), path + ".points[" + i + "]"));
          }
        } else if (shapeId != null) {
          ShapeTemplate template = shapeTemplates.get(shapeId);
          if (template == null || template.points().isEmpty()) {
            throw new IllegalArgumentException(path + ".shape: unknown or empty shape=" + shapeId);
          }
          points = template.points();
        } else {
          throw new IllegalArgumentException(path + ": missing points or shape");
        }

        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
        for (PointSpec p : points) {
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          int emitCount = evalInt(count, ctx);
          int totalSteps = evalInt(steps, ctx);
          long period = evalLong(periodTicks, ctx);
          if (emitCount <= 0 || totalSteps <= 0 || period <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
          Actions.particlesPhysicsPoints(
              particle,
              fns,
              emitCount,
              vel,
              evalDouble(spread, exec),
              evalDouble(gravity, exec),
              evalDouble(drag, exec),
              totalSteps,
              period,
              evalDouble(offset, exec),
              evalDouble(extra, exec),
              data,
              collide,
              collisionMode,
              evalDouble(restitution, exec)).execute(exec);
        };
      }
      case "particles_physics_polyline" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue step = numValue(node, "step", 0.5, path);
        NumValue velocityX = numValue(node, "velocityX", 0.0, path);
        NumValue velocityY = numValue(node, "velocityY", 0.2, path);
        NumValue velocityZ = numValue(node, "velocityZ", 0.0, path);
        NumValue spread = numValue(node, "spread", 0.08, path);
        NumValue gravity = numValue(node, "gravity", 0.03, path);
        NumValue drag = numValue(node, "drag", 0.02, path);
        NumValue steps = numValue(node, "steps", 20.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean collide = bool(node, "collide", false);
        String collisionModeRaw = string(node, "collisionMode", "STOP");
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
        try {
          collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(path + ".collisionMode: unknown mode " + collisionModeRaw);
        }
        NumValue restitution = numValue(node, "restitution", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        String shapeId = node.containsKey("shape") ? String.valueOf(node.get("shape")) : null;
        List<PointSpec> points;
        if (node.containsKey("points")) {
          Object rawPoints = node.get("points");
          if (!(rawPoints instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(path + ".points: expected a non-empty list");
          }
          points = new ArrayList<>(list.size());
          for (int i = 0; i < list.size(); i++) {
            points.add(pointSpec(list.get(i), path + ".points[" + i + "]"));
          }
        } else if (shapeId != null) {
          ShapeTemplate template = shapeTemplates.get(shapeId);
          if (template == null || template.points().size() < 2) {
            throw new IllegalArgumentException(path + ".shape: unknown or insufficient shape=" + shapeId);
          }
          points = template.points();
        } else {
          throw new IllegalArgumentException(path + ": missing points or shape");
        }

        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
        for (PointSpec p : points) {
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          int emitCount = evalInt(count, ctx);
          int totalSteps = evalInt(steps, ctx);
          long period = evalLong(periodTicks, ctx);
          double stepValue = evalDouble(step, ctx);
          if (emitCount <= 0 || totalSteps <= 0 || period <= 0 || stepValue <= 0.0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
          Actions.particlesPhysicsPolyline(
              particle,
              fns,
              stepValue,
              emitCount,
              vel,
              evalDouble(spread, exec),
              evalDouble(gravity, exec),
              evalDouble(drag, exec),
              totalSteps,
              period,
              evalDouble(offset, exec),
              evalDouble(extra, exec),
              data,
              collide,
              collisionMode,
              evalDouble(restitution, exec)).execute(exec);
        };
      }
      case "particles_physics_mesh" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue step = numValue(node, "step", 0.75, path);
        NumValue velocityX = numValue(node, "velocityX", 0.0, path);
        NumValue velocityY = numValue(node, "velocityY", 0.2, path);
        NumValue velocityZ = numValue(node, "velocityZ", 0.0, path);
        NumValue spread = numValue(node, "spread", 0.08, path);
        NumValue gravity = numValue(node, "gravity", 0.03, path);
        NumValue drag = numValue(node, "drag", 0.02, path);
        NumValue steps = numValue(node, "steps", 20.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        boolean collide = bool(node, "collide", false);
        String collisionModeRaw = string(node, "collisionMode", "STOP");
        dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
        try {
          collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(path + ".collisionMode: unknown mode " + collisionModeRaw);
        }
        NumValue restitution = numValue(node, "restitution", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        String shapeId = node.containsKey("shape") ? String.valueOf(node.get("shape")) : null;
        List<List<PointSpec>> triangles;
        if (shapeId != null) {
          ShapeTemplate template = shapeTemplates.get(shapeId);
          if (template == null || template.triangles().isEmpty()) {
            throw new IllegalArgumentException(path + ".shape: unknown or empty triangle shape=" + shapeId);
          }
          triangles = template.triangles();
        } else {
          throw new IllegalArgumentException(path + ": missing shape with triangles");
        }

        var fns = new ArrayList<java.util.function.Function<CastContext, Location[]>>(triangles.size());
        for (List<PointSpec> tri : triangles) {
          if (tri.size() < 3) {
            continue;
          }
          PointSpec a = tri.get(0);
          PointSpec b = tri.get(1);
          PointSpec c = tri.get(2);
          fns.add(ctx -> new Location[] { pointAt(ctx, a), pointAt(ctx, b), pointAt(ctx, c) });
        }

        yield ctx -> {
          int emitCount = evalInt(count, ctx);
          int totalSteps = evalInt(steps, ctx);
          long period = evalLong(periodTicks, ctx);
          double stepValue = evalDouble(step, ctx);
          if (emitCount <= 0 || totalSteps <= 0 || period <= 0 || stepValue <= 0.0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
          Actions.particlesPhysicsMesh(
              particle,
              fns,
              stepValue,
              emitCount,
              vel,
              evalDouble(spread, exec),
              evalDouble(gravity, exec),
              evalDouble(drag, exec),
              totalSteps,
              period,
              evalDouble(offset, exec),
              evalDouble(extra, exec),
              data,
              collide,
              collisionMode,
              evalDouble(restitution, exec)).execute(exec);
        };
      }
      case "particles_line" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue length = numValue(node, "length", 10.0, path);
        NumValue step = numValue(node, "step", 0.35, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        String targetRaw = string(node, "targetAt", null);
        final AtMode targetAt = targetRaw == null ? null : parseAt(targetRaw, path + ".targetAt");
        yield ctx -> {
          double len = evalDouble(length, ctx);
          double st = evalDouble(step, ctx);
          int emitCount = evalInt(count, ctx);
          if (len <= 0.0 || st <= 0.0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (targetAt != null) {
            Location target = resolveAt(ctx, targetAt);
            if (target.getWorld() == null || !target.getWorld().equals(origin.getWorld())) {
              return;
            }
            Vector direction = target.toVector().subtract(origin.toVector());
            if (direction.lengthSquared() < 1e-9) {
              return;
            }
            direction.normalize();
            exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                direction,
                ctx.itemInHand());
          } else if (at != AtMode.ORIGIN) {
            exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
          }
          Actions.particlesLine(particle, len, st, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "particles_arc" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.2, path);
        NumValue angleDegrees = numValue(node, "angleDegrees", 90.0, path);
        NumValue points = numValue(node, "points", 24.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || angle <= 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesArc(particle, r, angle, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "particles_disk" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.0, path);
        NumValue rings = numValue(node, "rings", 6.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 42.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int ringCount = evalInt(rings, ctx);
          int ppr = evalInt(pointsPerRing, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || ringCount <= 0 || ppr <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesDisk(particle, r, ringCount, ppr, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "particles_sphere_shell" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.0, path);
        NumValue points = numValue(node, "points", 120.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesSphereShell(particle, r, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "particles_sphere_filled", "particles_sphere_fill", "particles_sphere" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.0, path);
        NumValue points = numValue(node, "points", 120.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesSphereFilled(particle, r, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "particles_helix" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.2, path);
        NumValue length = numValue(node, "length", 6.0, path);
        NumValue turns = numValue(node, "turns", 3.0, path);
        NumValue points = numValue(node, "points", 80.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double len = evalDouble(length, ctx);
          int t = evalInt(turns, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || len < 0.0 || t <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesHelix(particle, r, len, t, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "particles_bezier" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue pointsPerMeter = numValue(node, "pointsPerMeter", 6.0, path);
        NumValue maxPoints = numValue(node, "maxPoints", 180.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        PointSpec p0 = pointSpec(node.get("p0"), path + ".p0");
        PointSpec p1 = pointSpec(node.get("p1"), path + ".p1");
        PointSpec p2 = pointSpec(node.get("p2"), path + ".p2");
        PointSpec p3 = pointSpec(node.get("p3"), path + ".p3");

        yield ctx -> {
          double ppm = evalDouble(pointsPerMeter, ctx);
          int maxPts = evalInt(maxPoints, ctx);
          int emitCount = evalInt(count, ctx);
          if (ppm <= 0.0 || maxPts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesBezier(
              c -> pointAt(c, p0),
              c -> pointAt(c, p1),
              c -> pointAt(c, p2),
              c -> pointAt(c, p3),
              ppm,
              maxPts,
              particle,
              emitCount,
              evalDouble(offset, exec),
              evalDouble(extra, exec),
              data).execute(exec);
        };
      }
      case "particles_spline" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue pointsPerMeter = numValue(node, "pointsPerMeter", 10.0, path);
        NumValue maxPoints = numValue(node, "maxPoints", 320.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        Object rawPoints = require(node, "points", path + ".points");
        if (!(rawPoints instanceof List<?> list) || list.size() < 2) {
          throw new IllegalArgumentException(path + ".points: expected a list with at least 2 point objects");
        }
        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(list.size());
        for (int i = 0; i < list.size(); i++) {
          PointSpec p = pointSpec(list.get(i), path + ".points[" + i + "]");
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          double ppm = evalDouble(pointsPerMeter, ctx);
          int maxPts = evalInt(maxPoints, ctx);
          int emitCount = evalInt(count, ctx);
          if (ppm <= 0.0 || maxPts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesSpline(fns, ppm, maxPts, particle, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "particles_points" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from");
        Object toRaw = pick(node, "endColor", "toColor", "to");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        String shapeId = node.containsKey("shape") ? String.valueOf(node.get("shape")) : null;
        List<PointSpec> points;
        if (node.containsKey("points")) {
          Object rawPoints = node.get("points");
          if (!(rawPoints instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(path + ".points: expected a non-empty list");
          }
          points = new ArrayList<>(list.size());
          for (int i = 0; i < list.size(); i++) {
            points.add(pointSpec(list.get(i), path + ".points[" + i + "]"));
          }
        } else if (shapeId != null) {
          ShapeTemplate template = shapeTemplates.get(shapeId);
          if (template == null || template.points().isEmpty()) {
            throw new IllegalArgumentException(path + ".shape: unknown or empty shape=" + shapeId);
          }
          points = template.points();
        } else {
          throw new IllegalArgumentException(path + ": missing points or shape");
        }

        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
        for (PointSpec p : points) {
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (from != null && to != null) {
            Actions.particlesPointsGradient(
                particle,
                fns,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.particlesPoints(
                particle,
                fns,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data).execute(exec);
          }
        };
      }
      case "particles_polyline" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue step = numValue(node, "step", 0.5, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from");
        Object toRaw = pick(node, "endColor", "toColor", "to");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        String shapeId = node.containsKey("shape") ? String.valueOf(node.get("shape")) : null;
        List<PointSpec> points;
        if (node.containsKey("points")) {
          Object rawPoints = node.get("points");
          if (!(rawPoints instanceof List<?> list) || list.size() < 2) {
            throw new IllegalArgumentException(path + ".points: expected a list with at least 2 point objects");
          }
          points = new ArrayList<>(list.size());
          for (int i = 0; i < list.size(); i++) {
            points.add(pointSpec(list.get(i), path + ".points[" + i + "]"));
          }
        } else if (shapeId != null) {
          ShapeTemplate template = shapeTemplates.get(shapeId);
          if (template == null || template.points().size() < 2) {
            throw new IllegalArgumentException(path + ".shape: unknown or insufficient shape=" + shapeId);
          }
          points = template.points();
        } else {
          throw new IllegalArgumentException(path + ": missing points or shape");
        }

        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
        for (PointSpec p : points) {
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          int emitCount = evalInt(count, ctx);
          double stepValue = evalDouble(step, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (emitCount <= 0 || stepValue <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (from != null && to != null) {
            Actions.particlesPolylineGradient(
                particle,
                fns,
                stepValue,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.particlesPolyline(
                particle,
                fns,
                stepValue,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data).execute(exec);
          }
        };
      }
      case "particles_mesh" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue step = numValue(node, "step", 0.75, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from");
        Object toRaw = pick(node, "endColor", "toColor", "to");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        String shapeId = node.containsKey("shape") ? String.valueOf(node.get("shape")) : null;
        List<List<PointSpec>> triangles = null;
        if (node.containsKey("triangles")) {
          Object rawTriangles = node.get("triangles");
          if (!(rawTriangles instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(path + ".triangles: expected a non-empty list");
          }
          triangles = new ArrayList<>();
          for (int i = 0; i < list.size(); i++) {
            Object triRaw = list.get(i);
            if (!(triRaw instanceof List<?> triList) || triList.size() < 3) {
              throw new IllegalArgumentException(path + ".triangles[" + i + "]: expected a list with 3 point objects");
            }
            var tri = new ArrayList<PointSpec>(3);
            for (int p = 0; p < 3; p++) {
              tri.add(pointSpec(triList.get(p), path + ".triangles[" + i + "][" + p + "]"));
            }
            triangles.add(tri);
          }
        } else if (shapeId != null) {
          ShapeTemplate template = shapeTemplates.get(shapeId);
          if (template == null || template.triangles().isEmpty()) {
            throw new IllegalArgumentException(path + ".shape: unknown or empty triangle shape=" + shapeId);
          }
          triangles = template.triangles();
        } else {
          throw new IllegalArgumentException(path + ": missing triangles or shape");
        }

        var fns = new ArrayList<java.util.function.Function<CastContext, Location[]>>(triangles.size());
        for (List<PointSpec> tri : triangles) {
          if (tri.size() < 3) {
            continue;
          }
          PointSpec a = tri.get(0);
          PointSpec b = tri.get(1);
          PointSpec c = tri.get(2);
          fns.add(ctx -> new Location[] { pointAt(ctx, a), pointAt(ctx, b), pointAt(ctx, c) });
        }

        yield ctx -> {
          int emitCount = evalInt(count, ctx);
          double stepValue = evalDouble(step, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (emitCount <= 0 || stepValue <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (from != null && to != null) {
            Actions.particlesMeshGradient(
                particle,
                fns,
                stepValue,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.particlesMesh(
                particle,
                fns,
                stepValue,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data).execute(exec);
          }
        };
      }
      case "preset_spline_motion" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue pointsPerMeter = numValue(node, "pointsPerMeter", 10.0, path);
        NumValue maxPoints = numValue(node, "maxPoints", 320.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        Object rawPoints = require(node, "points", path + ".points");
        if (!(rawPoints instanceof List<?> list) || list.size() < 2) {
          throw new IllegalArgumentException(path + ".points: expected a list with at least 2 point objects");
        }
        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(list.size());
        for (int i = 0; i < list.size(); i++) {
          PointSpec p = pointSpec(list.get(i), path + ".points[" + i + "]");
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          double ppm = evalDouble(pointsPerMeter, ctx);
          int maxPts = evalInt(maxPoints, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          if (ppm <= 0.0 || maxPts <= 0 || emitCount <= 0 || duration <= 0 || period <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetSplineMotion(fns, ppm, maxPts, duration, period, easing, particle, emitCount,
              evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "particles_cone" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue length = numValue(node, "length", 8.0, path);
        NumValue angleDegrees = numValue(node, "angleDegrees", 70.0, path);
        NumValue rings = numValue(node, "rings", 10.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 18.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double len = evalDouble(length, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          int ringCount = evalInt(rings, ctx);
          int ppr = evalInt(pointsPerRing, ctx);
          int emitCount = evalInt(count, ctx);
          if (len <= 0.0 || angle <= 0.0 || ringCount <= 0 || ppr <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesCone(particle, len, angle, ringCount, ppr, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "particles_cylinder" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.4, path);
        NumValue height = numValue(node, "height", 3.2, path);
        NumValue rings = numValue(node, "rings", 10.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 24.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double h = evalDouble(height, ctx);
          int ringCount = evalInt(rings, ctx);
          int ppr = evalInt(pointsPerRing, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || h < 0.0 || ringCount <= 0 || ppr <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesCylinder(particle, r, h, ringCount, ppr, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "particles_box" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue xRadius = numValue(node, "xRadius", 2.2, path);
        NumValue yRadius = numValue(node, "yRadius", 1.6, path);
        NumValue zRadius = numValue(node, "zRadius", 2.2, path);
        NumValue step = numValue(node, "step", 0.35, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double xr = evalDouble(xRadius, ctx);
          double yr = evalDouble(yRadius, ctx);
          double zr = evalDouble(zRadius, ctx);
          double st = evalDouble(step, ctx);
          int emitCount = evalInt(count, ctx);
          if (xr < 0.0 || yr < 0.0 || zr < 0.0 || st <= 0.0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesBox(particle, xr, yr, zr, st, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "particles_polygon" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.5, path);
        NumValue sides = numValue(node, "sides", 6.0, path);
        NumValue pointsPerEdge = numValue(node, "pointsPerEdge", 10.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int s = evalInt(sides, ctx);
          int ppe = evalInt(pointsPerEdge, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || s <= 0 || ppe <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.particlesPolygon(particle, new Vector(0, 1, 0), r, s, ppe, emitCount, evalDouble(offset, exec),
              evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "preset_shockwave" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue startRadius = numValue(node, "startRadius", 0.5, path);
        NumValue endRadius = numValue(node, "endRadius", 7.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.OUT_QUAD);
        NumValue points = numValue(node, "points", 56.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (start < 0.0 || end < 0.0 || pts <= 0 || emitCount <= 0 || duration <= 0 || period <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetShockwave(particle, start, end, duration, period, easing, pts, emitCount, evalDouble(offset, exec),
              evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "preset_morph_ring", "preset_gradient_ring" -> {
        boolean gradient = type.equals("preset_gradient_ring");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 1.0, path)
            : numValue(node, "radius", 1.0, path);
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 1.0, path)
            : numValue(node, "radius", 1.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue points = numValue(node, "points", 32.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientRing(
                particle,
                start,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphRing(
                particle,
                start,
                end,
                duration,
                period,
                easing,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_line", "preset_gradient_line" -> {
        boolean gradient = type.equals("preset_gradient_line");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseLength = numValue(node, "length", 6.0, path);
        NumValue startLength = node.containsKey("startLength")
            ? numValue(node, "startLength", 0.0, path)
            : baseLength;
        NumValue endLength = node.containsKey("endLength")
            ? numValue(node, "endLength", 6.0, path)
            : baseLength;
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue step = numValue(node, "step", 0.3, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        final AtMode targetAt = node.containsKey("targetAt") ? parseAt(string(node, "targetAt", "origin"), path + ".targetAt") : null;
        yield ctx -> {
          double start = evalDouble(startLength, ctx);
          double end = evalDouble(endLength, ctx);
          double length = evalDouble(baseLength, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          double stepValue = evalDouble(step, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (stepValue <= 0.0 || emitCount <= 0 || start < 0.0 || end < 0.0 || length < 0.0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (targetAt != null) {
            Location target = resolveAt(ctx, targetAt);
            if (target.getWorld() == null || !target.getWorld().equals(origin.getWorld())) {
              return;
            }
            Vector direction = target.toVector().subtract(origin.toVector());
            if (direction.lengthSquared() < 1e-9) {
              return;
            }
            direction.normalize();
            exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                direction,
                ctx.itemInHand());
          }
          if (gradient) {
            Actions.presetGradientLine(
                particle,
                length,
                stepValue,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphLine(
                particle,
                start,
                end,
                duration,
                period,
                easing,
                stepValue,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_arc", "preset_gradient_arc" -> {
        boolean gradient = type.equals("preset_gradient_arc");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseRadius = numValue(node, "radius", 1.2, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 1.2, path)
            : baseRadius;
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 1.2, path)
            : baseRadius;
        NumValue angleDegrees = numValue(node, "angleDegrees", 90.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue points = numValue(node, "points", 24.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseRadius, ctx);
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || angle <= 0.0 || angle > 360.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientArc(
                particle,
                base,
                angle,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphArc(
                particle,
                start,
                end,
                angle,
                duration,
                period,
                easing,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_disk", "preset_gradient_disk" -> {
        boolean gradient = type.equals("preset_gradient_disk");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseRadius = numValue(node, "radius", 2.0, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 0.5, path)
            : baseRadius;
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 2.0, path)
            : baseRadius;
        NumValue rings = numValue(node, "rings", 6.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 24.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseRadius, ctx);
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          int ringCount = evalInt(rings, ctx);
          int perRing = evalInt(pointsPerRing, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || ringCount <= 0 || perRing <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientDisk(
                particle,
                base,
                ringCount,
                perRing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphDisk(
                particle,
                start,
                end,
                ringCount,
                perRing,
                duration,
                period,
                easing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_sphere_shell", "preset_gradient_sphere_shell" -> {
        boolean gradient = type.equals("preset_gradient_sphere_shell");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseRadius = numValue(node, "radius", 2.0, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 1.0, path)
            : baseRadius;
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 2.0, path)
            : baseRadius;
        NumValue points = numValue(node, "points", 90.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseRadius, ctx);
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          int pts = evalInt(points, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientSphereShell(
                particle,
                base,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphSphereShell(
                particle,
                start,
                end,
                duration,
                period,
                easing,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_sphere_filled", "preset_gradient_sphere_filled" -> {
        boolean gradient = type.equals("preset_gradient_sphere_filled");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseRadius = numValue(node, "radius", 1.6, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 0.6, path)
            : baseRadius;
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 1.6, path)
            : baseRadius;
        NumValue points = numValue(node, "points", 120.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseRadius, ctx);
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          int pts = evalInt(points, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientSphereFilled(
                particle,
                base,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphSphereFilled(
                particle,
                start,
                end,
                duration,
                period,
                easing,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_helix", "preset_gradient_helix" -> {
        boolean gradient = type.equals("preset_gradient_helix");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseRadius = numValue(node, "radius", 1.2, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 0.8, path)
            : baseRadius;
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 1.2, path)
            : baseRadius;
        NumValue length = numValue(node, "length", 6.0, path);
        NumValue turns = numValue(node, "turns", 3.0, path);
        NumValue points = numValue(node, "points", 90.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseRadius, ctx);
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          double len = evalDouble(length, ctx);
          int t = evalInt(turns, ctx);
          int pts = evalInt(points, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || len < 0.0 || t <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientHelix(
                particle,
                base,
                len,
                t,
                pts,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphHelix(
                particle,
                start,
                end,
                len,
                t,
                pts,
                duration,
                period,
                easing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_cone", "preset_gradient_cone" -> {
        boolean gradient = type.equals("preset_gradient_cone");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseLength = numValue(node, "length", 6.0, path);
        NumValue startLength = node.containsKey("startLength")
            ? numValue(node, "startLength", 2.0, path)
            : baseLength;
        NumValue endLength = node.containsKey("endLength")
            ? numValue(node, "endLength", 6.0, path)
            : baseLength;
        NumValue angleDegrees = numValue(node, "angleDegrees", 35.0, path);
        NumValue rings = numValue(node, "rings", 5.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 28.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseLength, ctx);
          double start = evalDouble(startLength, ctx);
          double end = evalDouble(endLength, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          int ringCount = evalInt(rings, ctx);
          int perRing = evalInt(pointsPerRing, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || angle <= 0.0 || angle > 89.0 || ringCount <= 0 || perRing <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientCone(
                particle,
                base,
                angle,
                ringCount,
                perRing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphCone(
                particle,
                start,
                end,
                angle,
                ringCount,
                perRing,
                duration,
                period,
                easing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_cylinder", "preset_gradient_cylinder" -> {
        boolean gradient = type.equals("preset_gradient_cylinder");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseRadius = numValue(node, "radius", 2.0, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 1.0, path)
            : baseRadius;
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 2.0, path)
            : baseRadius;
        NumValue height = numValue(node, "height", 4.0, path);
        NumValue rings = numValue(node, "rings", 6.0, path);
        NumValue pointsPerRing = numValue(node, "pointsPerRing", 28.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseRadius, ctx);
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          double h = evalDouble(height, ctx);
          int ringCount = evalInt(rings, ctx);
          int perRing = evalInt(pointsPerRing, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || h < 0.0 || ringCount <= 0 || perRing <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientCylinder(
                particle,
                base,
                h,
                ringCount,
                perRing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphCylinder(
                particle,
                start,
                end,
                h,
                ringCount,
                perRing,
                duration,
                period,
                easing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_box", "preset_gradient_box" -> {
        boolean gradient = type.equals("preset_gradient_box");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseX = numValue(node, "xRadius", 2.0, path);
        NumValue baseY = numValue(node, "yRadius", 2.0, path);
        NumValue baseZ = numValue(node, "zRadius", 2.0, path);
        NumValue startX = node.containsKey("startX")
            ? numValue(node, "startX", 1.0, path)
            : baseX;
        NumValue startY = node.containsKey("startY")
            ? numValue(node, "startY", 1.0, path)
            : baseY;
        NumValue startZ = node.containsKey("startZ")
            ? numValue(node, "startZ", 1.0, path)
            : baseZ;
        NumValue endX = node.containsKey("endX")
            ? numValue(node, "endX", 2.0, path)
            : baseX;
        NumValue endY = node.containsKey("endY")
            ? numValue(node, "endY", 2.0, path)
            : baseY;
        NumValue endZ = node.containsKey("endZ")
            ? numValue(node, "endZ", 2.0, path)
            : baseZ;
        NumValue step = numValue(node, "step", 0.5, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double bx = evalDouble(baseX, ctx);
          double by = evalDouble(baseY, ctx);
          double bz = evalDouble(baseZ, ctx);
          double sx = evalDouble(startX, ctx);
          double sy = evalDouble(startY, ctx);
          double sz = evalDouble(startZ, ctx);
          double ex = evalDouble(endX, ctx);
          double ey = evalDouble(endY, ctx);
          double ez = evalDouble(endZ, ctx);
          double st = evalDouble(step, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (st <= 0.0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientBox(
                particle,
                bx,
                by,
                bz,
                st,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphBox(
                particle,
                sx,
                sy,
                sz,
                ex,
                ey,
                ez,
                st,
                duration,
                period,
                easing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_morph_polygon", "preset_gradient_polygon" -> {
        boolean gradient = type.equals("preset_gradient_polygon");
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue baseRadius = numValue(node, "radius", 1.6, path);
        NumValue startRadius = node.containsKey("startRadius")
            ? numValue(node, "startRadius", 1.0, path)
            : baseRadius;
        NumValue endRadius = node.containsKey("endRadius")
            ? numValue(node, "endRadius", 1.6, path)
            : baseRadius;
        NumValue sides = numValue(node, "sides", 6.0, path);
        NumValue pointsPerEdge = numValue(node, "pointsPerEdge", 5.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 40.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double base = evalDouble(baseRadius, ctx);
          double start = evalDouble(startRadius, ctx);
          double end = evalDouble(endRadius, ctx);
          int s = evalInt(sides, ctx);
          int ppe = evalInt(pointsPerEdge, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (base < 0.0 || start < 0.0 || end < 0.0 || s <= 2 || ppe <= 0 || emitCount <= 0) {
            return;
          }
          if (!gradient && (duration <= 0 || period <= 0)) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          if (gradient) {
            Actions.presetGradientPolygon(
                particle,
                base,
                s,
                ppe,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          } else {
            Actions.presetMorphPolygon(
                particle,
                start,
                end,
                s,
                ppe,
                duration,
                period,
                easing,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          }
        };
      }
      case "preset_gradient_bezier" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue pointsPerMeter = numValue(node, "pointsPerMeter", 6.0, path);
        NumValue maxPoints = numValue(node, "maxPoints", 180.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        PointSpec p0 = pointSpec(node.get("p0"), path + ".p0");
        PointSpec p1 = pointSpec(node.get("p1"), path + ".p1");
        PointSpec p2 = pointSpec(node.get("p2"), path + ".p2");
        PointSpec p3 = pointSpec(node.get("p3"), path + ".p3");

        yield ctx -> {
          double ppm = evalDouble(pointsPerMeter, ctx);
          int maxPts = evalInt(maxPoints, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (ppm <= 0.0 || maxPts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetGradientBezier(
              c -> pointAt(c, p0),
              c -> pointAt(c, p1),
              c -> pointAt(c, p2),
              c -> pointAt(c, p3),
              ppm,
              maxPts,
              particle,
              emitCount,
              evalDouble(offset, exec),
              evalDouble(extra, exec),
              data,
              from,
              to,
              dustSize).execute(exec);
        };
      }
      case "preset_gradient_spline" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue pointsPerMeter = numValue(node, "pointsPerMeter", 10.0, path);
        NumValue maxPoints = numValue(node, "maxPoints", 320.0, path);
        Object fromRaw = pick(node, "startColor", "fromColor", "from", "color");
        Object toRaw = pick(node, "endColor", "toColor", "to", "end");
        org.bukkit.Color from = fromRaw == null ? null : parseColor(fromRaw, path + ".startColor");
        org.bukkit.Color to = toRaw == null ? null : parseColor(toRaw, path + ".endColor");
        NumValue size = numValue(node, "size", 1.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");

        Object rawPoints = require(node, "points", path + ".points");
        if (!(rawPoints instanceof List<?> list) || list.size() < 2) {
          throw new IllegalArgumentException(path + ".points: expected a list with at least 2 point objects");
        }
        var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(list.size());
        for (int i = 0; i < list.size(); i++) {
          PointSpec p = pointSpec(list.get(i), path + ".points[" + i + "]");
          fns.add(ctx -> pointAt(ctx, p));
        }

        yield ctx -> {
          double ppm = evalDouble(pointsPerMeter, ctx);
          int maxPts = evalInt(maxPoints, ctx);
          int emitCount = evalInt(count, ctx);
          float dustSize = (float) evalDouble(size, ctx);
          if (ppm <= 0.0 || maxPts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetGradientSpline(
              fns,
              ppm,
              maxPts,
              particle,
              emitCount,
              evalDouble(offset, exec),
              evalDouble(extra, exec),
              data,
              from,
              to,
              dustSize).execute(exec);
        };
      }
      case "preset_orbit" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.4, path);
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.LINEAR);
        NumValue copies = numValue(node, "copies", 3.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.02, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int c = evalInt(copies, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || duration <= 0 || period <= 0 || c <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetOrbit(particle, r, duration, period, easing, c, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "preset_orbiting_runes" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 2.6, path);
        NumValue durationTicks = numValue(node, "durationTicks", 80.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 2.0, path);
        var easing = easing(node, "easing", EasingId.LINEAR);
        NumValue copies = numValue(node, "copies", 6.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.02, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int c = evalInt(copies, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || duration <= 0 || period <= 0 || c <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetOrbit(particle, r, duration, period, easing, c, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "preset_swirl" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.8, path);
        NumValue height = numValue(node, "height", 2.6, path);
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue points = numValue(node, "points", 22.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double h = evalDouble(height, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || h < 0.0 || duration <= 0 || period <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetSwirl(particle, r, h, duration, period, easing, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "preset_spiral_aura" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue radius = numValue(node, "radius", 1.8, path);
        NumValue height = numValue(node, "height", 3.5, path);
        NumValue durationTicks = numValue(node, "durationTicks", 80.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 2.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue points = numValue(node, "points", 28.0, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.02, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double h = evalDouble(height, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          int pts = evalInt(points, ctx);
          int emitCount = evalInt(count, ctx);
          if (r < 0.0 || h < 0.0 || duration <= 0 || period <= 0 || pts <= 0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetSwirl(particle, r, h, duration, period, easing, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
              .execute(exec);
        };
      }
      case "preset_beam_chargeup" -> {
        Particle particle = enumValue(node, "particle", Particle.class, path + ".particle");
        Object data = parseParticleData(node, particle, path);
        NumValue startLength = numValue(node, "startLength", 0.0, path);
        NumValue endLength = node.containsKey("endLength")
            ? numValue(node, "endLength", 10.0, path)
            : numValue(node, "length", 10.0, path);
        NumValue durationTicks = numValue(node, "durationTicks", 20.0, path);
        NumValue periodTicks = numValue(node, "periodTicks", 1.0, path);
        var easing = easing(node, "easing", EasingId.IN_OUT_CUBIC);
        NumValue step = numValue(node, "step", 0.35, path);
        NumValue count = numValue(node, "count", 1.0, path);
        NumValue offset = numValue(node, "offset", 0.0, path);
        NumValue extra = numValue(node, "extra", 0.0, path);
        NumValue forward = numValue(node, "forward", 0.0, path);
        NumValue right = numValue(node, "right", 0.0, path);
        NumValue up = numValue(node, "up", 0.0, path);
        String atRaw = string(node, "at", "origin");
        final AtMode at = parseAt(atRaw, path + ".at");
        yield ctx -> {
          double start = evalDouble(startLength, ctx);
          double end = evalDouble(endLength, ctx);
          long duration = evalLong(durationTicks, ctx);
          long period = evalLong(periodTicks, ctx);
          double st = evalDouble(step, ctx);
          int emitCount = evalInt(count, ctx);
          if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || st <= 0.0 || emitCount <= 0) {
            return;
          }
          Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
          if (origin.getWorld() == null) {
            return;
          }
          CastContext exec = new CastContext(
              ctx.engine(),
              ctx.plugin(),
              ctx.castId(),
              ctx.abilityId(),
              ctx.tick(),
              ctx.state(),
              ctx.caster(),
              origin.clone(),
              ctx.direction().clone(),
              ctx.itemInHand());
          Actions.presetBeamChargeup(particle, start, end, duration, period, easing, st, emitCount, evalDouble(offset, exec),
              evalDouble(extra, exec), data).execute(exec);
        };
      }
      case "chance" -> {
        NumValue probability = numValue(node, "probability", 0.5, path);
        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action otherwise = Actions.noop();
        if (node.containsKey("otherwise")) {
          otherwise = compileAction(castMap(node.get("otherwise"), path + ".otherwise"), path + ".otherwise", includeStack);
        }
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = otherwise;
        yield ctx -> {
          double p = evalDouble(probability, ctx);
          if (p <= 0.0) {
            elseAction.execute(ctx);
            return;
          }
          if (p >= 1.0) {
            thenAction.execute(ctx);
            return;
          }
          if (ctx.rng().nextDouble() < p) {
            thenAction.execute(ctx);
          } else {
            elseAction.execute(ctx);
          }
        };
      }
      case "debug_log" -> {
        String raw = requireString(node, "message", path + ".message");
        yield ctx -> {
          if (!ctx.engine().isDebugEnabled()) {
            return;
          }
          ctx.engine().debug(interpolate(raw, ctx));
        };
      }
      case "raycast_hit_entity" -> {
        NumValue maxDistance = numValue(node, "maxDistance", 20.0, path);
        NumValue raySize = numValue(node, "raySize", 0.35, path);
        boolean stopOnBlock = bool(node, "stopOnBlock", true);
        boolean ignoreCaster = bool(node, "ignoreCaster", true);
        DamageSpecTemplate damageTemplate = null;
        if (node.containsKey("damage")) {
          Map<String, Object> damageNode = castMap(node.get("damage"), path + ".damage");
          damageTemplate = parseDamageSpecTemplate(damageNode, path + ".damage", DamageCause.DIRECT, DamageType.PHYSICAL);
        }
        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action otherwise = Actions.noop();
        if (node.containsKey("otherwise")) {
          otherwise = compileAction(castMap(node.get("otherwise"), path + ".otherwise"), path + ".otherwise", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOtherwise = otherwise;
        final DamageSpecTemplate finalDamageTemplate = damageTemplate;
        yield ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double size = evalDouble(raySize, ctx);
          if (max <= 0.0 || size < 0.0) {
            finalOtherwise.execute(ctx);
            return;
          }
          var hits = Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, e -> true).select(ctx);
          if (hits.isEmpty()) {
            finalOtherwise.execute(ctx);
            return;
          }
          LivingEntity target = hits.get(0);
          ctx.state().put(YAML_LAST_ENTITY, target);
          if (finalDamageTemplate != null) {
            DamageSpec spec = finalDamageTemplate.toSpec(ctx);
            if (spec != null) {
              ctx.engine().applyDamage(ctx, target, spec);
            }
          }
          thenAction.execute(ctx);
          Object hook = ctx.state().get(DSL_ON_HIT);
          if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action hookAction) {
            hookAction.execute(ctx);
          }
        };
      }
      case "for_each_target" -> {
        Map<String, Object> targeterNode = castMap(require(node, "targeter", path + ".targeter"), path + ".targeter");
        Targeter<LivingEntity> targeter = compileTargeter(targeterNode, path + ".targeter");

        String mode = string(node, "mode", "each").trim().toLowerCase(Locale.ROOT);
        boolean firstOnly = switch (mode) {
          case "each" -> false;
          case "first" -> true;
          default -> throw new IllegalArgumentException(path + ".mode: invalid mode=" + mode + " (use each|first)");
        };

        NumValue maxTargets = numValue(node, "maxTargets", 0.0, path);

        AtMode originAt = parseAt(string(node, "originAt", "origin"), path + ".originAt");

        Map<String, Object> then = castMap(require(node, "then", path + ".then"), path + ".then");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = compileAction(then, path + ".then", includeStack);
        dev.patric.dungeonsreborn.effects.actions.Action otherwise = Actions.noop();
        if (node.containsKey("otherwise")) {
          otherwise = compileAction(castMap(node.get("otherwise"), path + ".otherwise"), path + ".otherwise", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOtherwise = otherwise;

        yield ctx -> {
          CastContext selectCtx = ctx;
          if (originAt != AtMode.ORIGIN) {
            Location origin = resolveAt(ctx, originAt);
            if (origin.getWorld() != null) {
              selectCtx = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  ctx.direction().clone(),
                  ctx.itemInHand());
            }
          }

          List<LivingEntity> targets = targeter.select(selectCtx);
          if (targets.isEmpty()) {
            finalOtherwise.execute(ctx);
            return;
          }

          int limit = targets.size();
          if (firstOnly) {
            limit = 1;
          }
          int max = evalInt(maxTargets, ctx);
          if (max > 0) {
            limit = Math.min(limit, max);
          }

          for (int i = 0; i < limit; i++) {
            ctx.state().put(YAML_LAST_ENTITY, targets.get(i));
            thenAction.execute(ctx);
          }
        };
      }
      case "damage" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue cap = numValue(node, "cap", 0.0, path);
        NumValue maxPercent = numValue(node, "maxPercent", 0.0, path);
        NumValue armorPenFlat = numValue(node, "armorPenFlat", 0.0, path);
        NumValue armorPenPct = numValue(node, "armorPenPct", 0.0, path);
        NumValue resistPenPct = numValue(node, "resistPenPct", 0.0, path);
        NumValue critChance = numValue(node, "critChance", 0.0, path);
        NumValue critMultiplier = numValue(node, "critMultiplier", 1.5, path);
        NumValue minDamageFloor = numValue(node, "minDamageFloor", 0.0, path);
        String vulnerabilityTag = string(node, "vulnerabilityTag", null);
        String mitigationProfile = string(node, "mitigationProfile", null);
        Set<String> pipelineTags = stringSet(node.get("pipelineTags"));
        boolean snapshotAtCast = bool(node, "snapshotAtCast", false);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.DIRECT);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (node.containsKey("onHit")) {
          onHitAction = compileAction(castMap(node.get("onHit"), path + ".onHit"), path + ".onHit", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            double capValue = evalDouble(cap, ctx);
            double maxPercentValue = evalDouble(maxPercent, ctx);
            DamageSpec spec = DamageSpec.flat(dmg, DamageType.PHYSICAL, cause, false, p)
                .withCaps(Math.max(0.0, capValue), Math.max(0.0, maxPercentValue));
            spec = spec.withPipeline(
                Math.max(0.0, evalDouble(armorPenFlat, ctx)),
                Math.max(0.0, evalDouble(armorPenPct, ctx)),
                Math.max(0.0, evalDouble(resistPenPct, ctx)),
                vulnerabilityTag,
                Math.max(0.0, evalDouble(critChance, ctx)),
                Math.max(1.0, evalDouble(critMultiplier, ctx)),
                Math.max(0.0, evalDouble(minDamageFloor, ctx)),
                mitigationProfile,
                pipelineTags,
                snapshotAtCast);
            if (source != null && !source.isBlank()) {
              spec = spec.withSource(source);
            }
            if (tags != null && !tags.isEmpty()) {
              spec = spec.withTags(tags);
            }
            ctx.engine().applyDamage(ctx, target, spec);
            finalOnHit.execute(ctx);
          }
        };
      }
      case "damage_typed" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue cap = numValue(node, "cap", 0.0, path);
        NumValue maxPercent = numValue(node, "maxPercent", 0.0, path);
        NumValue armorPenFlat = numValue(node, "armorPenFlat", 0.0, path);
        NumValue armorPenPct = numValue(node, "armorPenPct", 0.0, path);
        NumValue resistPenPct = numValue(node, "resistPenPct", 0.0, path);
        NumValue critChance = numValue(node, "critChance", 0.0, path);
        NumValue critMultiplier = numValue(node, "critMultiplier", 1.5, path);
        NumValue minDamageFloor = numValue(node, "minDamageFloor", 0.0, path);
        String vulnerabilityTag = string(node, "vulnerabilityTag", null);
        String mitigationProfile = string(node, "mitigationProfile", null);
        Set<String> pipelineTags = stringSet(node.get("pipelineTags"));
        boolean snapshotAtCast = bool(node, "snapshotAtCast", false);
        DamageType dmgType = damageTypeValue(node, path + ".damageType");
        boolean ignoreResistance = bool(node, "ignoreResistance", false);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.DIRECT);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            double capValue = evalDouble(cap, ctx);
            double maxPercentValue = evalDouble(maxPercent, ctx);
            DamageSpec spec = DamageSpec.flat(dmg, dmgType, cause, ignoreResistance, p)
                .withCaps(Math.max(0.0, capValue), Math.max(0.0, maxPercentValue));
            spec = spec.withPipeline(
                Math.max(0.0, evalDouble(armorPenFlat, ctx)),
                Math.max(0.0, evalDouble(armorPenPct, ctx)),
                Math.max(0.0, evalDouble(resistPenPct, ctx)),
                vulnerabilityTag,
                Math.max(0.0, evalDouble(critChance, ctx)),
                Math.max(1.0, evalDouble(critMultiplier, ctx)),
                Math.max(0.0, evalDouble(minDamageFloor, ctx)),
                mitigationProfile,
                pipelineTags,
                snapshotAtCast);
            if (source != null && !source.isBlank()) {
              spec = spec.withSource(source);
            }
            if (tags != null && !tags.isEmpty()) {
              spec = spec.withTags(tags);
            }
            ctx.engine().applyDamage(ctx, target, spec);
          }
        };
      }
      case "set_resistance" -> {
        DamageType dmgType = damageTypeValue(node, path + ".damageType");
        NumValue multiplier = requireNumValue(node, "multiplier", path + ".multiplier");
        NumValue durationTicks = numValue(node, "durationTicks", 0.0, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double mult = evalDouble(multiplier, ctx);
          if (!Double.isFinite(mult) || mult < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), dmgType, mult);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration,
                () -> ctx.engine().restoreResistance(target.getUniqueId(), dmgType, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      case "add_resistance" -> {
        DamageType dmgType = damageTypeValue(node, path + ".damageType");
        NumValue delta = requireNumValue(node, "delta", path + ".delta");
        NumValue durationTicks = numValue(node, "durationTicks", 0.0, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double add = evalDouble(delta, ctx);
          if (!Double.isFinite(add)) {
            return;
          }
          double current = ctx.engine().resistanceMultiplier(target.getUniqueId(), dmgType);
          double next = current + add;
          if (!Double.isFinite(next) || next < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), dmgType, next);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration,
                () -> ctx.engine().restoreResistance(target.getUniqueId(), dmgType, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      case "clear_resistance" -> {
        boolean hasType = hasDamageType(node);
        DamageType dmgType = hasType ? damageTypeValue(node, path + ".damageType") : null;
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          if (dmgType == null) {
            ctx.engine().clearResistances(target.getUniqueId());
          } else {
            ctx.engine().clearResistance(target.getUniqueId(), dmgType);
          }
        };
      }
      case "set_reflect" -> {
        NumValue ratio = numValue(node, "ratio", 0.25, path);
        NumValue flat = numValue(node, "flat", 0.0, path);
        boolean ignoreResistance = bool(node, "ignoreResistance", false);
        DamageType dmgType = hasDamageType(node) ? damageTypeValue(node, path + ".damageType") : null;
        NumValue durationTicks = numValue(node, "durationTicks", 0.0, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double r = evalDouble(ratio, ctx);
          double f = evalDouble(flat, ctx);
          if (!(r > 0.0) && !(f > 0.0)) {
            return;
          }
          long token = ctx.engine().setReflect(target.getUniqueId(),
              new EffectsEngine.ReflectSpec(r, f, dmgType, ignoreResistance, p));
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().clearReflect(target.getUniqueId(), token));
            ctx.state().track(handle);
          }
        };
      }
      case "clear_reflect" -> {
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          ctx.engine().clearReflect(target.getUniqueId());
        };
      }
      case "damage_percent" -> {
        NumValue percent = numValue(node, "percent", 0.15, path);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.PERCENT);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double pct = evalDouble(percent, ctx);
            if (pct <= 0.0) {
              return;
            }
            EntityActions.damagePercent(pct, p, cause, source, tags).execute(ctx, target);
          }
        };
      }
      case "damage_true" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.TRUE);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damageTrue(dmg, p, cause, source, tags).execute(ctx, target);
          }
        };
      }
      case "damage_falloff" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue maxDistance = numValue(node, "maxDistance", 12.0, path);
        NumValue minMultiplier = numValue(node, "minMultiplier", 0.2, path);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.FALLOFF);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            double maxDist = evalDouble(maxDistance, ctx);
            double minMult = evalDouble(minMultiplier, ctx);
            if (dmg <= 0.0 || maxDist <= 0.0) {
              return;
            }
            EntityActions.damageWithFalloff(dmg, maxDist, minMult, p, cause, source, tags).execute(ctx, target);
          }
        };
      }
      case "damage_crit" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue critChance = numValue(node, "critChance", 0.2, path);
        NumValue critMultiplier = numValue(node, "critMultiplier", 1.5, path);
        NumValue headshotMultiplier = numValue(node, "headshotMultiplier", 1.0, path);
        NumValue headshotThreshold = numValue(node, "headshotThreshold", 0.25, path);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.CRIT);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damageCrit(
                dmg,
                evalDouble(critChance, ctx),
                evalDouble(critMultiplier, ctx),
                evalDouble(headshotMultiplier, ctx),
                evalDouble(headshotThreshold, ctx),
                p,
                cause,
                source,
                tags).execute(ctx, target);
          }
        };
      }
      case "damage_lifesteal" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue ratio = numValue(node, "ratio", 0.25, path);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.LIFESTEAL);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            if (dmg <= 0.0) {
              return;
            }
            EntityActions.damageLifesteal(dmg, evalDouble(ratio, ctx), p, cause, source, tags).execute(ctx, target);
          }
        };
      }
      case "damage_dot", "damage_over_time" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue periodTicks = numValue(node, "periodTicks", 10.0, path);
        NumValue times = numValue(node, "times", 5.0, path);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.DOT);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (node.containsKey("onHit")) {
          onHitAction = compileAction(castMap(node.get("onHit"), path + ".onHit"), path + ".onHit", includeStack);
        }
        dev.patric.dungeonsreborn.effects.actions.Action onTickAction = Actions.noop();
        if (node.containsKey("onTick")) {
          onTickAction = compileAction(castMap(node.get("onTick"), path + ".onTick"), path + ".onTick", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnTick = onTickAction;
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            long period = evalLong(periodTicks, ctx);
            int count = evalInt(times, ctx);
            if (dmg <= 0.0 || period <= 0 || count <= 0) {
              return;
            }
            finalOnHit.execute(ctx);
            EntityActions.damageOverTime(dmg, period, count, p, cause, source, tags, (cast, hit) -> {
              Object prev = cast.state().get(YAML_LAST_ENTITY);
              cast.state().put(YAML_LAST_ENTITY, hit);
              try {
                finalOnTick.execute(cast);
              } finally {
                cast.state().put(YAML_LAST_ENTITY, prev);
              }
            }).execute(ctx, target);
          }
        };
      }
      case "damage_chain", "chain_damage", "chain_lightning" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue radius = numValue(node, "radius", 6.0, path);
        NumValue maxJumps = numValue(node, "maxJumps", 4.0, path);
        NumValue delayTicks = numValue(node, "delayTicks", 2.0, path);
        NumValue falloff = numValue(node, "falloff", 0.8, path);
        DamageCause cause = damageCauseValue(node, path + ".damageCause", DamageCause.CHAIN);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (node.containsKey("onHit")) {
          onHitAction = compileAction(castMap(node.get("onHit"), path + ".onHit"), path + ".onHit", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double dmg = evalDouble(amount, ctx);
            double r = evalDouble(radius, ctx);
            int jumps = evalInt(maxJumps, ctx);
            long delay = evalLong(delayTicks, ctx);
            double f = evalDouble(falloff, ctx);
            if (dmg <= 0.0 || r <= 0.0 || jumps <= 0 || delay < 0) {
              return;
            }
            EntityActions.chainDamage(dmg, r, jumps, delay, f, p, cause, source, tags, (cast, hit) -> {
              Object prev = cast.state().get(YAML_LAST_ENTITY);
              cast.state().put(YAML_LAST_ENTITY, hit);
              try {
                finalOnHit.execute(cast);
              } finally {
                cast.state().put(YAML_LAST_ENTITY, prev);
              }
            }).execute(ctx, target);
          }
        };
      }
      case "ground_damage", "damage_ground" -> {
        NumValue radius = numValue(node, "radius", 4.0, path);
        NumValue maxDrop = numValue(node, "maxDrop", 6.0, path);
        boolean ignoreCaster = bool(node, "ignoreCaster", true);
        Map<String, Object> damageNode = castMap(require(node, "damage", path + ".damage"), path + ".damage");
        DamageSpecTemplate damageTemplate = parseDamageSpecTemplate(damageNode, path + ".damage", DamageCause.AOE, DamageType.PHYSICAL);
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (node.containsKey("onHit")) {
          onHitAction = compileAction(castMap(node.get("onHit"), path + ".onHit"), path + ".onHit", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double drop = evalDouble(maxDrop, ctx);
          if (r <= 0.0 || drop < 0.0) {
            return;
          }
          var targets = Targeters.groundSphere(r, drop, ignoreCaster, e -> true).select(ctx);
          if (targets.isEmpty()) {
            return;
          }
          for (LivingEntity target : targets) {
            DamageSpec spec = damageTemplate.toSpec(ctx);
            if (spec != null) {
              ctx.engine().applyDamage(ctx, target, spec);
            }
            Object prev = ctx.state().get(YAML_LAST_ENTITY);
            ctx.state().put(YAML_LAST_ENTITY, target);
            try {
              finalOnHit.execute(ctx);
            } finally {
              ctx.state().put(YAML_LAST_ENTITY, prev);
            }
          }
        };
      }
      case "heal" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        HealType healType = healTypeValue(node, path + ".healType", HealType.DIRECT);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        NumValue cap = numValue(node, "cap", 0.0, path);
        boolean overhealToShield = bool(node, "overhealToShield", false);
        NumValue shieldCap = numValue(node, "shieldCap", 0.0, path);
        NumValue shieldDecayTicks = numValue(node, "shieldDecayTicks", 0.0, path);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double heal = evalDouble(amount, ctx);
            if (heal <= 0.0) {
              return;
            }
            EntityActions.heal(
                heal,
                p,
                healType,
                source,
                tags,
                evalDouble(cap, ctx),
                overhealToShield,
                evalDouble(shieldCap, ctx),
                evalLong(shieldDecayTicks, ctx)).execute(ctx, target);
          }
        };
      }
      case "heal_percent" -> {
        NumValue percent = numValue(node, "percent", 0.15, path);
        HealType healType = healTypeValue(node, path + ".healType", HealType.DIRECT);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        NumValue cap = numValue(node, "cap", 0.0, path);
        boolean overhealToShield = bool(node, "overhealToShield", false);
        NumValue shieldCap = numValue(node, "shieldCap", 0.0, path);
        NumValue shieldDecayTicks = numValue(node, "shieldDecayTicks", 0.0, path);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double pct = evalDouble(percent, ctx);
            if (pct <= 0.0) {
              return;
            }
            EntityActions.healPercent(
                pct,
                p,
                healType,
                source,
                tags,
                evalDouble(cap, ctx),
                overhealToShield,
                evalDouble(shieldCap, ctx),
                evalLong(shieldDecayTicks, ctx)).execute(ctx, target);
          }
        };
      }
      case "heal_over_time", "heal_hot" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue periodTicks = numValue(node, "periodTicks", 10.0, path);
        NumValue times = numValue(node, "times", 5.0, path);
        HealType healType = healTypeValue(node, path + ".healType", HealType.HOT);
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        NumValue cap = numValue(node, "cap", 0.0, path);
        boolean overhealToShield = bool(node, "overhealToShield", false);
        NumValue shieldCap = numValue(node, "shieldCap", 0.0, path);
        NumValue shieldDecayTicks = numValue(node, "shieldDecayTicks", 0.0, path);
        String source = string(node, "source", null);
        java.util.Set<String> tags = damageTagSet(node, path);
        dev.patric.dungeonsreborn.effects.actions.Action onTickAction = Actions.noop();
        if (node.containsKey("onTick")) {
          onTickAction = compileAction(castMap(node.get("onTick"), path + ".onTick"), path + ".onTick", includeStack);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnTick = onTickAction;
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double heal = evalDouble(amount, ctx);
            long period = evalLong(periodTicks, ctx);
            int count = evalInt(times, ctx);
            if (heal <= 0.0 || period <= 0 || count <= 0) {
              return;
            }
            EntityActions.healOverTime(
                heal,
                period,
                count,
                p,
                healType,
                source,
                tags,
                evalDouble(cap, ctx),
                overhealToShield,
                evalDouble(shieldCap, ctx),
                evalLong(shieldDecayTicks, ctx),
                (cast, hit) -> {
                  Object prev = cast.state().get(YAML_LAST_ENTITY);
                  cast.state().put(YAML_LAST_ENTITY, hit);
                  try {
                    finalOnTick.execute(cast);
                  } finally {
                    cast.state().put(YAML_LAST_ENTITY, prev);
                  }
                }).execute(ctx, target);
          }
        };
      }
      case "shield", "absorb" -> {
        NumValue amount = requireNumValue(node, "amount", path + ".amount");
        NumValue cap = numValue(node, "cap", 0.0, path);
        NumValue decayTicks = numValue(node, "decayTicks", 0.0, path);
        HealType healType = "absorb".equals(type) ? HealType.ABSORB : HealType.SHIELD;
        String policy = string(node, "policy", "hostile_default").toLowerCase(Locale.ROOT);
        EntityActions.DamagePolicy p = switch (policy) {
          case "any" -> EntityActions.DamagePolicy.any();
          case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
          case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
          case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
          default -> throw new IllegalArgumentException(path + ".policy: unknown policy: " + policy);
        };
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            double shield = evalDouble(amount, ctx);
            if (shield <= 0.0) {
              return;
            }
            EntityActions.shield(shield, evalDouble(cap, ctx), evalLong(decayTicks, ctx), p, healType).execute(ctx, target);
          }
        };
      }
      case "potion" -> {
        PotionEffectType effect = potionEffectValue(node, "effect", path + ".effect");
        NumValue durationTicks = numValue(node, "durationTicks", 60.0, path);
        NumValue amplifier = numValue(node, "amplifier", 0.0, path);
        boolean ambient = bool(node, "ambient", false);
        boolean particles = bool(node, "particles", true);
        boolean icon = bool(node, "icon", true);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            long ticks = Math.max(1L, evalLong(durationTicks, ctx));
            int amp = Math.max(0, evalInt(amplifier, ctx));
            EntityActions.potion(effect, Duration.ofMillis(ticks * 50L), amp, ambient, particles, icon)
                .execute(ctx, target);
          }
        };
      }
      case "totem" -> {
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            target = ctx.caster();
          }
          if (target != null) {
            target.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
            if (ctx.engine().isDebugEnabled() && target instanceof Player player) {
              ItemStack main = player.getInventory().getItemInMainHand();
              ItemStack off = player.getInventory().getItemInOffHand();
              effectsLog.info("[Effects][Totem] player=" + player.getName()
                  + " main=" + describeItem(main) + " deathProtection=" + hasDeathProtection(main)
                  + " off=" + describeItem(off) + " deathProtection=" + hasDeathProtection(off));
            }
          }
        };
      }
      case "knockback" -> {
        NumValue horizontal = numValue(node, "horizontal", 1.0, path);
        NumValue vertical = numValue(node, "vertical", 0.35, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            EntityActions.knockbackFromOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
          }
        };
      }
      case "pull" -> {
        NumValue horizontal = numValue(node, "horizontal", 0.75, path);
        NumValue vertical = numValue(node, "vertical", 0.08, path);
        yield ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target != null) {
            EntityActions.pullToOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
          }
        };
      }
      case "projectile" -> {
        NumValue speedPerTick = numValue(node, "speedPerTick", 1.3, path);
        NumValue maxDistance = numValue(node, "maxDistance", 24.0, path);
        NumValue hitRadius = numValue(node, "hitRadius", 0.25, path);
        boolean ignoreCaster = bool(node, "ignoreCaster", true);
        String bc = string(node, "blockCollision", "stop").toUpperCase(Locale.ROOT);
        boolean bounce = "BOUNCE".equals(bc);
        boolean passThrough = "PASS_THROUGH".equals(bc) || "PASS-THROUGH".equals(bc) || "PASS".equals(bc);
        Map<String, Object> bounces = bounce
            ? (node.containsKey("bounces") ? castMap(node.get("bounces"), path + ".bounces") : Map.of())
            : Map.of();
        NumValue bounceMax = bounce ? numValue(bounces, "max", 0.0, path + ".bounces") : null;
        NumValue bounceRestitution = bounce ? numValue(bounces, "restitution", 0.9, path + ".bounces") : null;

        boolean hasTrail = node.containsKey("trail");
        Map<String, Object> trail = hasTrail ? castMap(node.get("trail"), path + ".trail") : Map.of();
        Particle trailParticle = hasTrail ? enumValue(trail, "particle", Particle.class, path + ".trail.particle") : Particle.END_ROD;
        NumValue trailCount = hasTrail ? numValue(trail, "count", 1.0, path + ".trail") : null;
        NumValue trailOffset = hasTrail ? numValue(trail, "offset", 0.0, path + ".trail") : null;
        NumValue trailExtra = hasTrail ? numValue(trail, "extra", 0.0, path + ".trail") : null;

        if (node.containsKey("trail")) {
          castMap(node.get("trail"), path + ".trail");
        }

        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (node.containsKey("onHit")) {
          Map<String, Object> onHit = castMap(node.get("onHit"), path + ".onHit");
          onHitAction = compileAction(onHit, path + ".onHit", includeStack);
        }
        DamageSpecTemplate damageTemplate = null;
        if (node.containsKey("damage")) {
          Map<String, Object> damageNode = castMap(node.get("damage"), path + ".damage");
          damageTemplate = parseDamageSpecTemplate(damageNode, path + ".damage", DamageCause.PROJECTILE, DamageType.PHYSICAL);
        }

        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        final DamageSpecTemplate finalDamageTemplate = damageTemplate;
        yield ctx -> {
          double speed = evalDouble(speedPerTick, ctx);
          double max = evalDouble(maxDistance, ctx);
          double radius = evalDouble(hitRadius, ctx);
          if (speed <= 0.0 || max <= 0.0 || radius < 0.0) {
            return;
          }
          ProjectileSpec.Builder b = ProjectileSpec.builder()
              .speedPerTick(speed)
              .maxDistance(max)
              .hitRadius(radius)
              .ignoreCaster(ignoreCaster);
          if (passThrough) {
            b.stopOnBlock(false);
          } else if (bounce) {
            int maxBounce = bounceMax == null ? 0 : Math.max(0, evalInt(bounceMax, ctx));
            double restitution = bounceRestitution == null ? 0.9 : Math.max(0.0, evalDouble(bounceRestitution, ctx));
            b.bounces(maxBounce, restitution);
          } else {
            b.stopOnBlock(true);
          }

          if (hasTrail) {
            int count = trailCount == null ? 1 : Math.max(0, evalInt(trailCount, ctx));
            double offset = trailOffset == null ? 0.0 : Math.max(0.0, evalDouble(trailOffset, ctx));
            double extra = trailExtra == null ? 0.0 : evalDouble(trailExtra, ctx);
            b.trail(trailParticle, count, offset, extra);
          }

          b.onHit(hit -> {
            CastContext cast = hit.cast();
            LivingEntity target = hit.hitEntity();
            if (target != null) {
              cast.state().put(YAML_LAST_ENTITY, target);
              if (finalDamageTemplate != null) {
                DamageSpec spec = finalDamageTemplate.toSpec(cast);
                if (spec != null) {
                  cast.engine().applyDamage(cast, target, spec);
                }
              }
              finalOnHit.execute(cast);
              Object hook = cast.state().get(DSL_ON_HIT);
              if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action hookAction) {
                hookAction.execute(cast);
              }
            }
          });

          Actions.projectile(b.build()).execute(ctx);
        };
      }
      case "minion_summon", "summon_minion", "minions" -> {
        String mobId = requireString(node, "mob", path + ".mob");
        String minionId = string(node, "id", string(node, "minionId", mobId));
        int count = intValue(node, "count", 1);
        long duration = longValue(node, "durationTicks", 20L * 30L);
        double radius = doubleValue(node, "radius", 1.5);
        int waves = Math.max(1, intValue(node, "waves", 1));
        long waveIntervalTicks = longValue(node, "waveIntervalTicks", 0L);
        MinionFormation formation = parseMinionFormation(string(node, "formation", null), path + ".formation");
        double formationRadius = doubleValue(node, "formationRadius", 0.0);
        boolean safeSpawn = bool(node, "safeSpawn", false);
        int maxSpawnAttempts = Math.max(1, intValue(node, "maxSpawnAttempts", 6));
        boolean despawnOnLogout = bool(node, "despawnOnLogout", true);
        boolean persistent = bool(node, "persistent", false);
        boolean sharePotionEffects = bool(node, "sharePotionEffects", false);
        MinionScaling scaling = parseMinionScaling(node.get("scale"), path + ".scale");
        MinionOwnerScalingSpec ownerScaling = parseMinionOwnerScaling(node.get("ownerScaling"), path + ".ownerScaling");
        MinionScalingLimits scalingLimits = parseMinionScalingLimits(node.get("scalingLimits"), path + ".scalingLimits");
        Map<DamageType, Double> resistances = parseResistanceMap(node.get("resistances"), path + ".resistances");
        java.util.Set<DamageType> immunities = parseDamageTypeSet(node.get("immunities"), path + ".immunities");
        dev.patric.dungeonsreborn.effects.minions.MinionMode mode = parseMinionMode(string(node, "mode", null), path + ".mode");
        MinionTargetRules targetRules = parseMinionTargetRules(node, path + ".targeting");
        java.util.List<dev.patric.dungeonsreborn.effects.minions.MinionPassiveSpec> passives = parseMinionPassives(node.get("passives"), path + ".passives");
        java.util.List<dev.patric.dungeonsreborn.effects.minions.MinionSpecialAttackSpec> specialAttacks = parseMinionSpecialAttacks(node.get("specialAttacks"), path + ".specialAttacks");
        Map<Attribute, Double> statOverrides = parseMinionStatOverrides(node.get("statOverrides"), path + ".statOverrides");
        String mainAttackOverride = string(node, "mainAttack", string(node, "mainAttackOverride", null));
        String secondaryAttackOverride = string(node, "secondaryAttack", string(node, "secondaryAttackOverride", null));
        boolean disableBasePassives = bool(node, "disableBasePassives", false);
        boolean disableBaseAttacks = bool(node, "disableBaseAttacks", false);
        boolean disableBaseAi = bool(node, "disableBaseAi", false);
        String nameOverride = string(node, "name", null);
        Boolean glowOverride = node.containsKey("glow") ? bool(node, "glow", false) : null;
        MobParticlesSpec particles = parseMinionParticles(node.get("particles"), path + ".particles");
        long particlesPeriodTicks = longValue(node, "particlesPeriodTicks", particles == null ? 0L : 20L);
        List<MinionSummonCostSpec> summonCosts = parseMinionSummonCosts(node.get("summonCosts"), path + ".summonCosts");
        long summonCooldownTicks = longValue(node, "summonCooldownTicks", 0L);
        String summonCooldownKey = string(node, "summonCooldownKey", minionId == null ? null : "minion:" + minionId);
        if (count <= 0) {
          throw new IllegalArgumentException(path + ".count: must be > 0");
        }
        if (duration <= 0) {
          throw new IllegalArgumentException(path + ".durationTicks: must be > 0");
        }
        MinionManager minions = resolveMinionManager(path);
        yield ctx -> {
          if (!minions.hasMob(mobId)) {
            throw new IllegalArgumentException(path + ".mob: unknown mob id: " + mobId);
          }
          java.util.UUID ownerId = ctx.caster().getUniqueId();
          if (summonCooldownTicks > 0L && ctx.caster() instanceof Player player) {
            String cooldownKey = summonCooldownKey;
            if (cooldownKey == null || cooldownKey.isBlank()) {
              cooldownKey = "minion:" + minionId;
            }
            if (!ctx.engine().tryStartCooldown(player.getUniqueId(), cooldownKey, summonCooldownTicks)) {
              return;
            }
          }
          if (!summonCosts.isEmpty()) {
            if (!applyMinionSummonCosts(ctx, summonCosts)) {
              return;
            }
          }
          MinionSummonSpec summonSpec = new MinionSummonSpec(waves, waveIntervalTicks, formation, formationRadius,
              safeSpawn, maxSpawnAttempts);
          MinionSpec spec = new MinionSpec(minionId, mobId, count, duration, ownerId, radius, summonSpec,
              scaling, resistances, immunities, despawnOnLogout, persistent, mode, targetRules, passives, specialAttacks,
              statOverrides, ownerScaling,
              scalingLimits, Ids.normalize(mainAttackOverride), Ids.normalize(secondaryAttackOverride),
              disableBasePassives, disableBaseAttacks, disableBaseAi,
              sharePotionEffects,
              nameOverride, glowOverride, particles, particlesPeriodTicks);
          java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
          java.util.List<LivingEntity> spawned = minions.summon(spec, ctx.caster().getLocation(), living -> {
            ids.add(living.getUniqueId());
          });
          ctx.state().put(Vars.MINION_ID, minionId);
          ctx.state().put(Vars.MINION_COUNT, spawned.size());
          ctx.state().put(Vars.MINION_IDS, java.util.List.copyOf(ids));
          ctx.state().put(Vars.MINION_DURATION, duration);
        };
      }
      default -> throw new IllegalArgumentException(path + ": unknown action type: " + type);
    };
    if (unsafePermission != null && UNSAFE_ACTION_TYPES.contains(type)) {
      action = wrapUnsafeAction(action, type, path);
    }
    return action;
  }

  public static record TimelineEntrySpec(NumValue delayTicks, dev.patric.dungeonsreborn.effects.actions.Action action) {
  }

  private record DamageSpecTemplate(
      NumValue amount,
      DamageAmountMode mode,
      DamageType type,
      DamageCause cause,
      boolean ignoreResistance,
      EntityActions.DamagePolicy policy,
      String source,
      java.util.Set<String> tags,
      NumValue cap,
      NumValue maxPercent,
      NumValue armorPenFlat,
      NumValue armorPenPct,
      NumValue resistPenPct,
      String vulnerabilityTag,
      NumValue critChance,
      NumValue critMultiplier,
      NumValue minDamageFloor,
      String mitigationProfile,
      Set<String> pipelineTags,
      boolean snapshotAtCast) {
    DamageSpec toSpec(CastContext ctx) {
      double value = evalDouble(amount, ctx);
      if (!(value > 0.0)) {
        return null;
      }
      double capValue = cap == null ? 0.0 : evalDouble(cap, ctx);
      double maxPercentValue = maxPercent == null ? 0.0 : evalDouble(maxPercent, ctx);
      DamageSpec spec;
      switch (mode) {
        case TRUE -> spec = DamageSpec.trueDamage(value, cause, policy);
        case PERCENT_MAX_HEALTH -> spec = DamageSpec.percent(value, type, cause, ignoreResistance, policy);
        case FLAT -> spec = DamageSpec.flat(value, type, cause, ignoreResistance, policy);
        default -> spec = DamageSpec.flat(value, type, cause, ignoreResistance, policy);
      }
      if (capValue > 0.0 || maxPercentValue > 0.0) {
        spec = spec.withCaps(Math.max(0.0, capValue), Math.max(0.0, maxPercentValue));
      }
      if (source != null && !source.isBlank()) {
        spec = spec.withSource(source);
      }
      if (tags != null && !tags.isEmpty()) {
        spec = spec.withTags(tags);
      }
      spec = spec.withPipeline(
          armorPenFlat == null ? 0.0 : Math.max(0.0, evalDouble(armorPenFlat, ctx)),
          armorPenPct == null ? 0.0 : Math.max(0.0, evalDouble(armorPenPct, ctx)),
          resistPenPct == null ? 0.0 : Math.max(0.0, evalDouble(resistPenPct, ctx)),
          vulnerabilityTag,
          critChance == null ? 0.0 : Math.max(0.0, evalDouble(critChance, ctx)),
          critMultiplier == null ? 1.5 : Math.max(1.0, evalDouble(critMultiplier, ctx)),
          minDamageFloor == null ? 0.0 : Math.max(0.0, evalDouble(minDamageFloor, ctx)),
          mitigationProfile,
          pipelineTags,
          snapshotAtCast);
      return spec;
    }
  }

  private MinionManager resolveMinionManager(String path) {
    if (plugin instanceof DungeonsRebornPlugin dr) {
      MinionManager minions = dr.minionManager();
      if (minions != null) {
        return minions;
      }
    }
    throw new IllegalArgumentException(path + ": minion system not available");
  }

  private DamageSpecTemplate parseDamageSpecTemplate(Map<String, Object> node, String path,
      DamageCause defaultCause, DamageType defaultType) {
    NumValue amount = requireNumValue(node, "amount", path + ".amount");
    String modeRaw = string(node, "mode", string(node, "damageMode", string(node, "amountMode", null)));
    DamageAmountMode mode = damageModeValue(modeRaw, path + ".mode", DamageAmountMode.FLAT);
    DamageType type = hasDamageType(node) ? damageTypeValue(node, path + ".damageType") : defaultType;
    DamageCause cause = damageCauseValue(node, path + ".damageCause", defaultCause);
    boolean ignoreResistance = bool(node, "ignoreResistance", false);
    EntityActions.DamagePolicy policy = damagePolicyValue(string(node, "policy", "hostile_default"), path + ".policy");
    String source = string(node, "source", null);
    java.util.Set<String> tags = damageTagSet(node, path);
    NumValue cap = node.containsKey("cap") ? numValue(node, "cap", 0.0, path) : null;
    NumValue maxPercent = node.containsKey("maxPercent") ? numValue(node, "maxPercent", 0.0, path) : null;
    NumValue armorPenFlat = node.containsKey("armorPenFlat") ? numValue(node, "armorPenFlat", 0.0, path) : null;
    NumValue armorPenPct = node.containsKey("armorPenPct") ? numValue(node, "armorPenPct", 0.0, path) : null;
    NumValue resistPenPct = node.containsKey("resistPenPct") ? numValue(node, "resistPenPct", 0.0, path) : null;
    String vulnerabilityTag = string(node, "vulnerabilityTag", null);
    NumValue critChance = node.containsKey("critChance") ? numValue(node, "critChance", 0.0, path) : null;
    NumValue critMultiplier = node.containsKey("critMultiplier") ? numValue(node, "critMultiplier", 1.5, path) : null;
    NumValue minDamageFloor = node.containsKey("minDamageFloor") ? numValue(node, "minDamageFloor", 0.0, path) : null;
    String mitigationProfile = string(node, "mitigationProfile", null);
    Set<String> pipelineTags = stringSet(node.get("pipelineTags"));
    boolean snapshotAtCast = bool(node, "snapshotAtCast", false);
    if (mode == DamageAmountMode.TRUE) {
      type = null;
      ignoreResistance = true;
    } else if (type == null) {
      type = DamageType.PHYSICAL;
    }
    return new DamageSpecTemplate(amount, mode, type, cause, ignoreResistance, policy, source, tags, cap, maxPercent,
        armorPenFlat, armorPenPct, resistPenPct, vulnerabilityTag, critChance, critMultiplier, minDamageFloor,
        mitigationProfile, pipelineTags, snapshotAtCast);
  }

  private EffectsEngine.AbilityCombatProfile parseAbilityCombatProfile(ConfigurationSection section, String path) {
    Object combatRaw = section.get("combat");
    if (combatRaw == null) {
      return null;
    }
    Map<String, Object> combat = combatRaw instanceof ConfigurationSection sec
        ? normalizeMap(sec.getValues(false))
        : castMap(combatRaw, path);

    EffectsEngine.CombatProfile defaults = EffectsEngine.CombatProfile.defaults();
    EffectsEngine.CombatProfilePair baseProfiles = parseCombatProfilePair(combat, path, defaults);

    Map<String, EffectsEngine.CombatProfilePair> worldOverrides = new HashMap<>();
    Object worldsRaw = combat.get("worlds");
    if (worldsRaw instanceof ConfigurationSection || worldsRaw instanceof Map<?, ?>) {
      Map<String, Object> worlds = castMap(worldsRaw, path + ".worlds");
      for (Map.Entry<String, Object> entry : worlds.entrySet()) {
        String worldKey = entry.getKey().toLowerCase(Locale.ROOT);
        Map<String, Object> worldNode = castMap(entry.getValue(), path + ".worlds." + entry.getKey());
        EffectsEngine.CombatProfilePair profiles = parseCombatProfilePair(worldNode, path + ".worlds." + entry.getKey(), defaults);
        worldOverrides.put(worldKey, profiles);
      }
    } else if (worldsRaw != null) {
      throw new IllegalArgumentException(path + ".worlds: expected object");
    }

    List<EffectsEngine.RegionProfile> regionOverrides = new ArrayList<>();
    Object regionsRaw = combat.get("regions");
    if (regionsRaw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Map<String, Object> regionNode = castMap(list.get(i), path + ".regions[" + i + "]");
        QuestRegion region = parseRegion(regionNode, path + ".regions[" + i + "]");
        EffectsEngine.CombatProfilePair profiles = parseCombatProfilePair(regionNode, path + ".regions[" + i + "]", defaults);
        regionOverrides.add(new EffectsEngine.RegionProfile(region, profiles));
      }
    } else if (regionsRaw != null) {
      throw new IllegalArgumentException(path + ".regions: expected list");
    }

    return new EffectsEngine.AbilityCombatProfile(baseProfiles, worldOverrides, regionOverrides);
  }

  private EffectsEngine.CombatProfilePair parseCombatProfilePair(Map<String, Object> node, String path,
      EffectsEngine.CombatProfile defaults) {
    Map<String, Object> pvpNode = mapNode(node.get("pvp"), path + ".pvp");
    Map<String, Object> pveNode = mapNode(node.get("pve"), path + ".pve");
    if (pvpNode == null && pveNode == null) {
      EffectsEngine.CombatProfile profile = parseCombatProfile(node, path, defaults);
      return new EffectsEngine.CombatProfilePair(profile, profile);
    }
    EffectsEngine.CombatProfile pvp = parseCombatProfile(pvpNode, path + ".pvp", defaults);
    EffectsEngine.CombatProfile pve = parseCombatProfile(pveNode, path + ".pve", defaults);
    return new EffectsEngine.CombatProfilePair(pvp, pve);
  }

  private EffectsEngine.CombatProfile parseCombatProfile(Map<String, Object> node, String path,
      EffectsEngine.CombatProfile defaults) {
    if (node == null) {
      return defaults;
    }
    double damageMultiplier = doubleValue(node, "damageMultiplier",
        doubleValue(node, "damageMult", defaults.damageMultiplier()));
    double healMultiplier = doubleValue(node, "healMultiplier",
        doubleValue(node, "healMult", defaults.healMultiplier()));
    double damageCap = doubleValue(node, "damageCap",
        doubleValue(node, "damageLimit", defaults.damageCap()));
    double healCap = doubleValue(node, "healCap",
        doubleValue(node, "healLimit", defaults.healCap()));
    double maxDamagePercent = doubleValue(node, "maxDamagePercent",
        doubleValue(node, "maxDamagePct", defaults.maxDamagePercent()));
    double maxHealPercent = doubleValue(node, "maxHealPercent",
        doubleValue(node, "maxHealPct", defaults.maxHealPercent()));
    boolean allowDamage = bool(node, "allowDamage", defaults.allowDamage());
    boolean allowHeal = bool(node, "allowHeal", defaults.allowHeal());
    if (damageMultiplier < 0.0 || healMultiplier < 0.0) {
      throw new IllegalArgumentException(path + ": multipliers must be >= 0");
    }
    if (damageCap < 0.0 || healCap < 0.0) {
      throw new IllegalArgumentException(path + ": caps must be >= 0");
    }
    if (maxDamagePercent < 0.0 || maxHealPercent < 0.0) {
      throw new IllegalArgumentException(path + ": maxPercent values must be >= 0");
    }
    return new EffectsEngine.CombatProfile(
        damageMultiplier,
        healMultiplier,
        damageCap,
        healCap,
        maxDamagePercent,
        maxHealPercent,
        allowDamage,
        allowHeal);
  }

  private ScriptHandlers compileScript(Object raw, String path) {
    String language = "dsl-v1";
    String source = null;
    String filePath = null;
    Integer explicitVersion = null;
    if (raw instanceof String s) {
      source = s;
    } else if (raw instanceof ConfigurationSection sec) {
      language = sec.getString("language", "dsl-v1");
      explicitVersion = parseScriptVersion(sec.get("version"), path + ".version");
      source = sec.getString("source");
      filePath = sec.getString("file");
    } else if (raw instanceof Map<?, ?> map) {
      Map<String, Object> node = castMap(map, path);
      language = string(node, "language", "dsl-v1");
      explicitVersion = parseScriptVersion(node.get("version"), path + ".version");
      Object src = node.get("source");
      if (src != null) {
        source = String.valueOf(src);
      }
      Object file = node.get("file");
      if (file != null) {
        filePath = String.valueOf(file);
      }
    } else {
      throw new IllegalArgumentException(path + ": expected string or object");
    }

    String lang = language == null ? "dsl-v1" : language.trim().toLowerCase(Locale.ROOT);
    if (!lang.equals("dsl-v1") && !lang.equals("dsl")) {
      throw new IllegalArgumentException(path + ".language: unsupported language: " + language);
    }
    if (filePath != null && !filePath.isBlank()) {
      return compileScriptFileCached(filePath, path + ".file", explicitVersion);
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException(path + ": missing source");
    }
    ScriptSource script = parseScriptSource(source, path, explicitVersion);
    return new DslParser(script.source(), path).parse();
  }

  public dev.patric.dungeonsreborn.effects.actions.Action compileScriptAction(Object raw, String path, String scriptId) {
    ScriptHandlers handlers = compileScript(raw, path);
    String resolvedId = scriptId == null || scriptId.isBlank() ? path : scriptId;
    return buildScriptAction(handlers, resolvedId);
  }

  private void debugScript(String message) {
    if (!scriptDebug && !scriptTrace) {
      return;
    }
    effectsLog.info("[Effects][Script] " + message);
  }

  private record ScriptHandlers(
      dev.patric.dungeonsreborn.effects.actions.Action onCast,
      dev.patric.dungeonsreborn.effects.actions.Action onCancel,
      dev.patric.dungeonsreborn.effects.actions.Action onEnd,
      dev.patric.dungeonsreborn.effects.actions.Action onHit,
      dev.patric.dungeonsreborn.effects.actions.Action onFinish,
      dev.patric.dungeonsreborn.effects.actions.Action onCostFail,
      dev.patric.dungeonsreborn.effects.actions.Action onCooldownFail) {
  }

  private record ScriptSource(String source, int version) {
  }

  private record ScriptCacheEntry(long lastModified, ScriptHandlers handlers, String error, int version) {
  }

  private static final class ScriptMetrics {
    private final java.util.concurrent.atomic.LongAdder executions = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder totalNanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder errors = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.AtomicReference<String> lastError = new java.util.concurrent.atomic.AtomicReference<>();
  }

  public record ScriptMetricSnapshot(String scriptId, long executions, long totalNanos, long errors, String lastError) {
  }

  private ScriptMetrics metricsFor(String scriptId) {
    return scriptMetrics.computeIfAbsent(scriptId, k -> new ScriptMetrics());
  }

  public java.util.List<ScriptMetricSnapshot> scriptMetrics() {
    java.util.List<ScriptMetricSnapshot> out = new java.util.ArrayList<>();
    for (Map.Entry<String, ScriptMetrics> entry : scriptMetrics.entrySet()) {
      ScriptMetrics m = entry.getValue();
      out.add(new ScriptMetricSnapshot(
          entry.getKey(),
          m.executions.sum(),
          m.totalNanos.sum(),
          m.errors.sum(),
          m.lastError.get()));
    }
    out.sort(java.util.Comparator.comparingLong(ScriptMetricSnapshot::totalNanos).reversed());
    return out;
  }

  public void clearScriptMetrics() {
    scriptMetrics.clear();
  }

  private void recordScriptTime(String scriptId, long nanos) {
    ScriptMetrics metrics = metricsFor(scriptId);
    metrics.executions.increment();
    metrics.totalNanos.add(Math.max(0L, nanos));
  }

  private void recordScriptError(String scriptId, Throwable error) {
    ScriptMetrics metrics = metricsFor(scriptId);
    metrics.errors.increment();
    if (error != null) {
      String msg = error.getMessage();
      metrics.lastError.set(msg == null || msg.isBlank() ? error.getClass().getSimpleName() : msg);
    }
  }

  private String scriptId(CastContext ctx) {
    Object id = ctx.state().get(DSL_SCRIPT_ID);
    return id == null ? "dsl" : id.toString();
  }

  private dev.patric.dungeonsreborn.effects.actions.Action buildScriptAction(ScriptHandlers handlers, String scriptId) {
    dev.patric.dungeonsreborn.effects.actions.Action onCast = handlers.onCast() == null ? Actions.noop() : handlers.onCast();
    dev.patric.dungeonsreborn.effects.actions.Action onCancel = handlers.onCancel();
    dev.patric.dungeonsreborn.effects.actions.Action onEnd = handlers.onEnd();
    dev.patric.dungeonsreborn.effects.actions.Action onHit = handlers.onHit();
    dev.patric.dungeonsreborn.effects.actions.Action onFinish = handlers.onFinish();
    dev.patric.dungeonsreborn.effects.actions.Action onCostFail = handlers.onCostFail();
    dev.patric.dungeonsreborn.effects.actions.Action onCooldownFail = handlers.onCooldownFail();
    return ctx -> {
      ctx.state().put(DSL_SCRIPT_ID, scriptId);
      if (onCancel != null) {
        ctx.state().onCancel(() -> onCancel.execute(ctx));
      }
      if (onHit != null) {
        ctx.state().put(DSL_ON_HIT, onHit);
      }
      if (onFinish != null) {
        ctx.state().put(DSL_ON_FINISH, onFinish);
      }
      if (onCostFail != null) {
        ctx.state().put(DSL_ON_COST_FAIL, onCostFail);
      }
      if (onCooldownFail != null) {
        ctx.state().put(DSL_ON_COOLDOWN_FAIL, onCooldownFail);
      }
      long start = System.nanoTime();
      try {
        onCast.execute(ctx);
      } catch (RuntimeException ex) {
        recordScriptError(scriptId, ex);
        throw ex;
      } finally {
        recordScriptTime(scriptId, System.nanoTime() - start);
        ctx.state().put(DSL_CAST_DONE, Boolean.TRUE);
        if (onEnd != null) {
          try {
            onEnd.execute(ctx);
          } catch (RuntimeException ex) {
            recordScriptError(scriptId, ex);
            throw ex;
          }
        }
        runFinishIfReady(ctx);
      }
    };
  }

  private void warmScriptCache(List<String> errors) {
    scriptCache.clear();
    File scriptsDir = new File(plugin.getDataFolder(), "effects/scripts");
    if (!scriptsDir.exists()) {
      return;
    }
    try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(scriptsDir.toPath())) {
      for (java.nio.file.Path path : walk.filter(java.nio.file.Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".es"))
          .toList()) {
        try {
          compileScriptFileCached(path.toString(), path.toString(), null);
        } catch (IllegalArgumentException ex) {
          errors.add(ex.getMessage());
        }
      }
    } catch (java.io.IOException ex) {
      errors.add("scripts: failed to read scripts folder (" + ex.getMessage() + ")");
    }
  }

  private java.nio.file.Path resolveScriptPath(String filePath, String path) {
    String raw = filePath.trim();
    java.nio.file.Path file = java.nio.file.Path.of(raw);
    if (!file.isAbsolute()) {
      java.io.File scriptsDir = new java.io.File(plugin.getDataFolder(), "effects/scripts");
      java.io.File candidate = new java.io.File(scriptsDir, raw);
      if (!candidate.exists()) {
        candidate = new java.io.File(plugin.getDataFolder(), raw);
      }
      file = candidate.toPath();
    }
    if (!java.nio.file.Files.exists(file)) {
      throw new IllegalArgumentException(path + ": file not found: " + file);
    }
    return file.toAbsolutePath().normalize();
  }

  private ScriptSource readScriptFile(java.nio.file.Path file, String path, Integer explicitVersion) {
    try {
      String source = java.nio.file.Files.readString(file);
      return parseScriptSource(source, path, explicitVersion);
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException(path + ": failed to read " + file + " (" + ex.getMessage() + ")");
    }
  }

  private ScriptHandlers compileScriptFileCached(String filePath, String path, Integer explicitVersion) {
    java.nio.file.Path file = resolveScriptPath(filePath, path);
    long lastModified;
    try {
      lastModified = java.nio.file.Files.getLastModifiedTime(file).toMillis();
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException(path + ": failed to read " + file + " (" + ex.getMessage() + ")");
    }
    ScriptCacheEntry cached = scriptCache.get(file);
    if (cached != null && cached.lastModified() == lastModified) {
      if (explicitVersion != null && cached.version() != explicitVersion) {
        throw new IllegalArgumentException(path + ": script version mismatch (expected " + explicitVersion + ", found " + cached.version() + ")");
      }
      if (cached.error() != null) {
        throw new IllegalArgumentException(cached.error());
      }
      return cached.handlers();
    }

    try {
      ScriptSource source = readScriptFile(file, path, explicitVersion);
      ScriptHandlers handlers = new DslParser(source.source(), file.toString()).parse();
      scriptCache.put(file, new ScriptCacheEntry(lastModified, handlers, null, source.version()));
      return handlers;
    } catch (IllegalArgumentException ex) {
      scriptCache.put(file, new ScriptCacheEntry(lastModified, null, ex.getMessage(), explicitVersion == null ? SCRIPT_VERSION : explicitVersion));
      throw ex;
    }
  }

  private ScriptSource parseScriptSource(String source, String path, Integer explicitVersion) {
    Integer headerVersion = detectScriptHeaderVersion(source);
    Integer version = explicitVersion != null ? explicitVersion : headerVersion;
    if (explicitVersion != null && headerVersion != null && !explicitVersion.equals(headerVersion)) {
      throw new IllegalArgumentException(path + ": script version mismatch (header=" + headerVersion + ", expected=" + explicitVersion + ")");
    }
    int resolved = version == null ? SCRIPT_VERSION : version;
    if (resolved != SCRIPT_VERSION) {
      throw new IllegalArgumentException(path + ": unsupported script version: " + resolved + " (expected " + SCRIPT_VERSION + ")");
    }
    return new ScriptSource(source, resolved);
  }

  private Integer detectScriptHeaderVersion(String source) {
    String[] lines = source.split("\\R", -1);
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (!trimmed.startsWith("#")) {
        break;
      }
      Integer version = parseVersionDirective(trimmed);
      if (version != null) {
        return version;
      }
    }
    return null;
  }

  private Integer parseVersionDirective(String line) {
    String text = line.trim();
    if (!text.startsWith("#")) {
      return null;
    }
    text = text.substring(1).trim();
    if (text.startsWith("!")) {
      text = text.substring(1).trim();
    }
    String lowered = text.toLowerCase(Locale.ROOT);
    if (lowered.startsWith("dsl")) {
      lowered = lowered.substring(3).trim();
    } else if (lowered.startsWith("version")) {
      lowered = lowered.substring("version".length()).trim();
    } else {
      return null;
    }
    if (lowered.startsWith(":") || lowered.startsWith("-")) {
      lowered = lowered.substring(1).trim();
    }
    if (lowered.startsWith("v")) {
      lowered = lowered.substring(1).trim();
    }
    if (lowered.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(lowered);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Integer parseScriptVersion(Object raw, String path) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number n) {
      return n.intValue();
    }
    String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    if (text.startsWith("dsl")) {
      text = text.substring(3).trim();
    }
    if (text.startsWith("v")) {
      text = text.substring(1).trim();
    }
    if (text.startsWith(":") || text.startsWith("-")) {
      text = text.substring(1).trim();
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": invalid script version: " + raw);
    }
  }

  private final class DslParser {
    private enum TokenType {
      IDENT,
      NUMBER,
      STRING,
      OP,
      COMP,
      LPAREN,
      RPAREN,
      COMMA,
      COLON,
      LBRACE,
      RBRACE,
      EQUALS,
      EOF
    }

    private record Token(TokenType type, String text, double number, int line, int column) {
    }

    private enum ValueKind {
      STRING,
      NUMBER,
      IDENT,
      EXPR
    }

    private record Value(ValueKind kind, String text, double number, ExprNode expr, int line, int column) {
    }

    private interface ScriptValue {
      Object eval(CastContext ctx);
    }

    private final class LiteralValue implements ScriptValue {
      private final Object value;

      private LiteralValue(Object value) {
        this.value = value;
      }

      @Override
      public Object eval(CastContext ctx) {
        return value;
      }
    }

    private final class ExprValue implements ScriptValue {
      private final ExprNode expr;

      private ExprValue(ExprNode expr) {
        this.expr = expr;
      }

      @Override
      public Object eval(CastContext ctx) {
        return evalExpr(ctx, expr);
      }
    }

    private final class VarValue implements ScriptValue {
      private final String name;

      private VarValue(String name) {
        this.name = name;
      }

      @Override
      public Object eval(CastContext ctx) {
        return resolveValue(name, ctx);
      }
    }

    private final String input;
    private final String path;
    private int pos;
    private int line = 1;
    private int column = 1;
    private Token lookahead;
    private final java.util.Map<String, MacroDef> macros = new java.util.HashMap<>();
    private static final int MAX_REPEAT_TIMES = 10000;
    private static final long MAX_ON_TICK_TICKS = 20L * 60L * 5L;
    private static final int MAX_OPS_PER_TICK = 2000;
    private static final int MAX_MACRO_DEPTH = 32;
    private static final int MAX_PARTICLES_PER_CAST = 20000;
    private static final java.util.List<String> ALLOWED_FUNCTIONS = java.util.List.of(
        "min",
        "max",
        "clamp",
        "lerp",
        "rand",
        "abs",
        "floor",
        "ceil",
        "round");
    private static final java.util.Set<String> ALLOWED_FUNCTION_SET = java.util.Set.copyOf(ALLOWED_FUNCTIONS);

    private record MacroDef(List<String> params, Map<String, ScriptValue> defaults, dev.patric.dungeonsreborn.effects.actions.Action body, Token at) {
    }

    DslParser(String input, String path) {
      this.input = Objects.requireNonNull(input, "input");
      this.path = Objects.requireNonNull(path, "path");
      this.lookahead = nextToken();
    }

    ScriptHandlers parse() {
      dev.patric.dungeonsreborn.effects.actions.Action onCast = null;
      dev.patric.dungeonsreborn.effects.actions.Action onCancel = null;
      dev.patric.dungeonsreborn.effects.actions.Action onEnd = null;
      dev.patric.dungeonsreborn.effects.actions.Action onHit = null;
      dev.patric.dungeonsreborn.effects.actions.Action onFinish = null;
      dev.patric.dungeonsreborn.effects.actions.Action onCostFail = null;
      dev.patric.dungeonsreborn.effects.actions.Action onCooldownFail = null;
      while (lookahead.type != TokenType.EOF) {
        if (lookahead.type == TokenType.IDENT && "macro".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          parseMacro();
          continue;
        }
        String handler = requireIdent("handler").toLowerCase(Locale.ROOT);
        dev.patric.dungeonsreborn.effects.actions.Action action = parseBlock();
        switch (handler) {
          case "on_cast", "oncast" -> {
            if (onCast != null) {
              throw error("duplicate on_cast block");
            }
            onCast = action;
          }
          case "on_cancel", "oncancel" -> {
            if (onCancel != null) {
              throw error("duplicate on_cancel block");
            }
            onCancel = action;
          }
          case "on_end", "onend", "finally" -> {
            if (onEnd != null) {
              throw error("duplicate on_end block");
            }
            onEnd = action;
          }
          case "on_hit", "onhit" -> {
            if (onHit != null) {
              throw error("duplicate on_hit block");
            }
            onHit = action;
          }
          case "on_finish", "onfinish", "on_complete", "oncomplete", "on_done", "ondone" -> {
            if (onFinish != null) {
              throw error("duplicate on_finish block");
            }
            onFinish = action;
          }
          case "on_cost_fail", "oncostfail" -> {
            if (onCostFail != null) {
              throw error("duplicate on_cost_fail block");
            }
            onCostFail = action;
          }
          case "on_cooldown_fail", "oncooldownfail" -> {
            if (onCooldownFail != null) {
              throw error("duplicate on_cooldown_fail block");
            }
            onCooldownFail = action;
          }
          default -> throw error("unsupported handler: " + handler);
        }
      }
      if (onCast == null) {
        throw new IllegalArgumentException(path + ": missing on_cast block");
      }
      return new ScriptHandlers(onCast, onCancel, onEnd, onHit, onFinish, onCostFail, onCooldownFail);
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseBlock() {
      consume(TokenType.LBRACE);
      List<dev.patric.dungeonsreborn.effects.actions.Action> actions = new ArrayList<>();
      while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
        Token stmtToken = lookahead;
        actions.add(guard(parseStatement(), stmtToken));
      }
      consume(TokenType.RBRACE);
      if (actions.isEmpty()) {
        return Actions.noop();
      }
      if (actions.size() == 1) {
        return actions.get(0);
      }
      return Actions.sequence(actions.toArray(dev.patric.dungeonsreborn.effects.actions.Action[]::new));
    }

    private dev.patric.dungeonsreborn.effects.actions.Action guard(dev.patric.dungeonsreborn.effects.actions.Action action, Token at) {
      return ctx -> {
        if (!consumeOps(ctx, 1)) {
          return;
        }
        if (scriptDebug) {
          debugScript("stmt " + pathAt(at) + " ability=" + ctx.abilityId() + " cast=" + ctx.castId());
        }
        long start = scriptTrace ? System.nanoTime() : 0L;
        try {
          action.execute(ctx);
        } catch (RuntimeException ex) {
          recordScriptError(scriptId(ctx), ex);
          String message = ex.getMessage();
          String prefix = pathAt(at) + ": ";
          if (message != null && message.startsWith(pathAt(at))) {
            prefix = "";
          }
          String base = message == null || message.isBlank() ? "DSL runtime error" : message;
          throw new IllegalArgumentException(prefix + base + renderMacroStack(ctx), ex);
        } finally {
          if (scriptTrace) {
            long elapsed = System.nanoTime() - start;
            debugScript("stmt " + pathAt(at) + " took " + (elapsed / 1_000_000.0) + "ms");
          }
        }
      };
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseStatement() {
      Token stmtToken = lookahead;
      String name = requireIdent("statement");
      String normalized = name.toLowerCase(Locale.ROOT);
      String deprecatedReplacement = DEPRECATED_DSL_STATEMENTS.get(normalized);
      if (deprecatedReplacement != null) {
        warnDeprecatedOnce(
            pathAt(stmtToken) + ":statement:" + normalized,
            pathAt(stmtToken) + ": statement '" + normalized + "' is deprecated; use '" + deprecatedReplacement + "'");
        name = deprecatedReplacement;
        normalized = deprecatedReplacement;
      }
      if (REMOVED_DSL_STATEMENTS.contains(normalized)) {
        warnDeprecatedOnce(
            pathAt(stmtToken) + ":statement:" + normalized,
            pathAt(stmtToken) + ": statement '" + normalized + "' was removed");
      }
      if ("if".equalsIgnoreCase(name) || "when".equalsIgnoreCase(name)) {
        var condition = parseCondition();
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          if (condition.test(ctx)) {
            thenAction.execute(ctx);
          } else {
            finalElse.execute(ctx);
          }
        };
      }
      if ("set".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        Map<String, Value> attrs = parseAttributes();
        NumValue ttl = numAttr(attrs, "ttlTicks", -1.0, stmtToken);
        return ctx -> {
          Object v = value.eval(ctx);
          long ttlTicks = evalTtlTicks(ttl, ctx);
          setVar(ctx, target.scope(), target.key(), v, ttlTicks > 0 ? ttlTicks : null);
        };
      }
      if ("inc_var".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numAttr(attrs, "amount", 1.0, stmtToken);
        NumValue def = numAttr(attrs, "default", 0.0, stmtToken);
        NumValue ttl = numAttr(attrs, "ttlTicks", -1.0, stmtToken);
        return ctx -> {
          Object cur = vars(ctx, target.scope()).get(target.key());
          double next = numericVar(cur, evalDouble(def, ctx)) + evalDouble(amount, ctx);
          long ttlTicks = evalTtlTicks(ttl, ctx);
          setVar(ctx, target.scope(), target.key(), next, ttlTicks > 0 ? ttlTicks : null);
        };
      }
      if ("with_var".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return ctx -> {
          Map<String, Object> vars = vars(ctx, target.scope());
          Map<String, Long> expirations = varExpirations(ctx, target.scope());
          boolean had = vars.containsKey(target.key());
          Object prev = vars.get(target.key());
          boolean hadExp = expirations.containsKey(target.key());
          Long prevExp = expirations.get(target.key());
          Object v = value.eval(ctx);
          setVar(ctx, target.scope(), target.key(), v);
          try {
            inner.executeWithHandle(ctx);
          } finally {
            if (!had) {
              vars.remove(target.key());
              expirations.remove(target.key());
            } else {
              vars.put(target.key(), prev);
              if (hadExp) {
                expirations.put(target.key(), prevExp);
              } else {
                expirations.remove(target.key());
              }
            }
          }
        };
      }
      if ("debug_var".equalsIgnoreCase(name)) {
        VarTarget target = parseVarTarget();
        Map<String, Value> attrs = parseAttributes();
        String label = stringAttr(attrs, "label", target.key(), stmtToken);
        return ctx -> {
          if (!ctx.engine().isDebugEnabled()) {
            return;
          }
          Object v = vars(ctx, target.scope()).get(target.key());
          ctx.engine().debug("var(" + target.scope().name().toLowerCase(Locale.ROOT) + "): " + label + "=" + v);
        };
      }
      if ("call".equalsIgnoreCase(name)) {
        return parseMacroCall(stmtToken);
      }
      if (lookahead.type == TokenType.EQUALS) {
        VarTarget target = new VarTarget(VarScope.CAST, name);
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        return ctx -> {
          Object v = value.eval(ctx);
          setVar(ctx, target.scope(), target.key(), v);
        };
      }
      if ("message".equalsIgnoreCase(name)) {
        String raw = requireStringToken("message");
        return ctx -> {
          if (ctx.caster() instanceof Player player) {
            player.sendMessage(renderText(raw, ctx));
          }
        };
      }
      if ("action_bar".equalsIgnoreCase(name) || "actionbar".equalsIgnoreCase(name)) {
        String raw = requireStringToken("action_bar");
        return ctx -> {
          if (ctx.caster() instanceof Player player) {
            player.sendActionBar(renderText(raw, ctx));
          }
        };
      }
      if ("sound".equalsIgnoreCase(name)) {
        Value soundValue = requireValue("sound");
        Map<String, Value> attrs = parseAttributes();
        Sound sound = parseSound(soundValue, stmtToken, "sound");
        NumValue volume = numAttr(attrs, "volume", 1.0, stmtToken);
        NumValue pitch = numAttr(attrs, "pitch", 1.0, stmtToken);
        return ctx -> Actions.sound(sound, (float) evalDouble(volume, ctx), (float) evalDouble(pitch, ctx)).execute(ctx);
      }
      if ("animate".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationTicks, ctx));
            long period = Math.max(0L, evalLong(periodTicks, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            return Actions.animate(duration, period, easingFromId(easingId), (tickCtx, t) -> {
              CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
              withTempVar(exec, VarScope.CAST, "t", t, () -> inner.executeWithHandle(exec));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("animate_shape".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationTicks, ctx));
            long period = Math.max(0L, evalLong(periodTicks, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            return Actions.animate(duration, period, easingFromId(easingId), (tickCtx, t) -> {
              CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
              withTempVar(exec, VarScope.CAST, "t", t, () -> inner.executeWithHandle(exec));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("motion".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        String modeRaw = stringAttr(attrs, "mode", "translate", stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Actions.MotionMode mode = parseMotionMode(modeRaw, pathAt(stmtToken) + ".mode");
        NumValue velocityX = numAttr(attrs, "velocityX", 0.0, stmtToken);
        NumValue velocityY = numAttr(attrs, "velocityY", 0.0, stmtToken);
        NumValue velocityZ = numAttr(attrs, "velocityZ", 0.0, stmtToken);
        NumValue radius = numAttr(attrs, "radius", 0.0, stmtToken);
        NumValue turns = numAttr(attrs, "turns", 1.0, stmtToken);
        NumValue vertical = numAttr(attrs, "vertical", 0.0, stmtToken);
        NumValue drift = numAttr(attrs, "drift", 0.0, stmtToken);
        NumValue driftVertical = numAttr(attrs, "driftVertical", 0.0, stmtToken);
        NumValue driftSpeed = numAttr(attrs, "driftSpeed", 0.35, stmtToken);
        String atRaw = attrs.containsKey("frame")
            ? stringAttr(attrs, "frame", "origin", stmtToken)
            : stringAttr(attrs, "at", "origin", stmtToken);
        AtMode at = parseAt(atRaw, pathAt(stmtToken) + ".at");
        double[] offsets = offsetsFromAttrs(attrs, stmtToken);
        NumValue forward = numAttr(attrs, "forward", offsets == null ? 0.0 : offsets[0], stmtToken);
        NumValue right = numAttr(attrs, "right", offsets == null ? 0.0 : offsets[1], stmtToken);
        NumValue up = numAttr(attrs, "up", offsets == null ? 0.0 : offsets[2], stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationTicks, ctx));
            long period = Math.max(0L, evalLong(periodTicks, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            Vector vel = new Vector(evalDouble(velocityX, ctx), evalDouble(velocityY, ctx), evalDouble(velocityZ, ctx));
            double radiusVal = evalDouble(radius, ctx);
            double turnsVal = evalDouble(turns, ctx);
            double verticalVal = evalDouble(vertical, ctx);
            double driftVal = evalDouble(drift, ctx);
            double driftVerticalVal = evalDouble(driftVertical, ctx);
            double driftSpeedVal = evalDouble(driftSpeed, ctx);
            java.util.function.Function<CastContext, Location> base = baseCtx -> {
              CastContext ref = followCaster ? followCasterContext(baseCtx) : baseCtx;
              return resolveAtWithOffsets(ref, at, forward, right, up);
            };
            dev.patric.dungeonsreborn.effects.actions.Actions.MotionSpec motion =
                new dev.patric.dungeonsreborn.effects.actions.Actions.MotionSpec(
                    mode,
                    base,
                    vel,
                    radiusVal,
                    turnsVal,
                    verticalVal,
                    driftVal,
                    driftVerticalVal,
                    driftSpeedVal);
            return Actions.motion(duration, period, easingFromId(easingId), motion, (tickCtx, t) -> {
              withTempVar(tickCtx, VarScope.CAST, "t", t, () -> inner.executeWithHandle(tickCtx));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("attach".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        String anchorRaw = stringAttr(attrs, "anchor", "caster", stmtToken);
        String pointRaw = stringAttr(attrs, "point", null, stmtToken);
        AnchorMode anchor = parseAnchorMode(anchorRaw, pathAt(stmtToken) + ".anchor");
        AnchorPoint point = parseAnchorPoint(pointRaw, anchor, pathAt(stmtToken) + ".point");
        double[] offsets = offsetsFromAttrs(attrs, stmtToken);
        NumValue forward = numAttr(attrs, "forward", offsets == null ? 0.0 : offsets[0], stmtToken);
        NumValue right = numAttr(attrs, "right", offsets == null ? 0.0 : offsets[1], stmtToken);
        NumValue up = numAttr(attrs, "up", offsets == null ? 0.0 : offsets[2], stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        Frame frame = frameForAnchor(anchor, point);
        return ctx -> {
          double f = evalDouble(forward, ctx);
          double r = evalDouble(right, ctx);
          double u = evalDouble(up, ctx);
          Actions.attach(inner, Frames.withOffsets(frame, f, r, u)).execute(ctx);
        };
      }
      if ("follow".equalsIgnoreCase(name) || "lock_to_target".equalsIgnoreCase(name) || "lock_target".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 2.0, stmtToken);
        NumValue smoothing = numAttr(attrs, "smoothing", 1.0, stmtToken);
        String anchorRaw = stringAttr(attrs, "anchor", "caster", stmtToken);
        String pointRaw = stringAttr(attrs, "point", null, stmtToken);
        AnchorMode anchor = parseAnchorMode(anchorRaw, pathAt(stmtToken) + ".anchor");
        AnchorPoint point = parseAnchorPoint(pointRaw, anchor, pathAt(stmtToken) + ".point");
        double[] offsets = offsetsFromAttrs(attrs, stmtToken);
        NumValue forward = numAttr(attrs, "forward", offsets == null ? 0.0 : offsets[0], stmtToken);
        NumValue right = numAttr(attrs, "right", offsets == null ? 0.0 : offsets[1], stmtToken);
        NumValue up = numAttr(attrs, "up", offsets == null ? 0.0 : offsets[2], stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        Frame frame = frameForAnchor(anchor, point);
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(1L, evalLong(durationTicks, ctx));
            long period = Math.max(1L, evalLong(periodTicks, ctx));
            double smooth = evalDouble(smoothing, ctx);
            double f = evalDouble(forward, ctx);
            double r = evalDouble(right, ctx);
            double u = evalDouble(up, ctx);
            return Actions.follow(inner, Frames.withOffsets(frame, f, r, u), duration, period, smooth)
                .executeWithHandle(ctx);
          }
        };
      }
      if ("state_machine".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue chargeTicks = numAttr(attrs, "chargeTicks", 20.0, stmtToken);
        NumValue sustainTicks = numAttr(attrs, "sustainTicks", 40.0, stmtToken);
        NumValue releaseTicks = numAttr(attrs, "releaseTicks", 20.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);

        String chargeName = requireIdent("charge");
        if (!"charge".equalsIgnoreCase(chargeName)) {
          throw error(stmtToken, "state_machine requires charge block");
        }
        dev.patric.dungeonsreborn.effects.actions.Action chargeAction = parseBlock();

        String sustainName = requireIdent("sustain");
        if (!"sustain".equalsIgnoreCase(sustainName)) {
          throw error(stmtToken, "state_machine requires sustain block");
        }
        dev.patric.dungeonsreborn.effects.actions.Action sustainAction = parseBlock();

        String releaseName = requireIdent("release");
        if (!"release".equalsIgnoreCase(releaseName)) {
          throw error(stmtToken, "state_machine requires release block");
        }
        dev.patric.dungeonsreborn.effects.actions.Action releaseAction = parseBlock();

        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long charge = Math.max(0L, evalLong(chargeTicks, ctx));
            long sustain = Math.max(0L, evalLong(sustainTicks, ctx));
            long release = Math.max(0L, evalLong(releaseTicks, ctx));
            long period = Math.max(1L, evalLong(periodTicks, ctx));
            java.util.List<Actions.TimelineEntry> entries = new java.util.ArrayList<>();
            long at = 0L;
            if (charge > 0) {
              dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx -> {
                Actions.animate(charge, period, easingFromId(easingId), (tickCtx, t) -> {
                  CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
                  withTempVar(exec, VarScope.CAST, "phase", "charge",
                      () -> withTempVar(exec, VarScope.CAST, "t", t, () -> chargeAction.executeWithHandle(exec)));
                }).executeWithHandle(stepCtx);
              };
              entries.add(new Actions.TimelineEntry(at, step));
              at += charge;
            }
            if (sustain > 0) {
              dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx -> {
                Actions.animate(sustain, period, easingFromId(easingId), (tickCtx, t) -> {
                  CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
                  withTempVar(exec, VarScope.CAST, "phase", "sustain",
                      () -> withTempVar(exec, VarScope.CAST, "t", t, () -> sustainAction.executeWithHandle(exec)));
                }).executeWithHandle(stepCtx);
              };
              entries.add(new Actions.TimelineEntry(at, step));
              at += sustain;
            }
            if (release > 0) {
              dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx -> {
                Actions.animate(release, period, easingFromId(easingId), (tickCtx, t) -> {
                  CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
                  withTempVar(exec, VarScope.CAST, "phase", "release",
                      () -> withTempVar(exec, VarScope.CAST, "t", t, () -> releaseAction.executeWithHandle(exec)));
                }).executeWithHandle(stepCtx);
              };
              entries.add(new Actions.TimelineEntry(at, step));
            }
            if (entries.isEmpty()) {
              return ActionHandle.completed();
            }
            return Actions.timeline(entries).executeWithHandle(ctx);
          }
        };
      }
      if ("global_timeline".equalsIgnoreCase(name) || "timeline_global".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        String id = stringAttr(attrs, "id", null, stmtToken);
        if (id == null || id.isBlank()) {
          throw error(stmtToken, "global_timeline requires id");
        }
        NumValue durationTicks = numAttr(attrs, "durationTicks", 200.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
        boolean start = boolAttr(attrs, "start", true, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action onTick = parseBlock();
        return ctx -> {
          long duration = Math.max(1L, evalLong(durationTicks, ctx));
          long period = Math.max(1L, evalLong(periodTicks, ctx));
          EffectsEngine.TimelineHandle timeline = ctx.engine().timeline(id);
          if (timeline == null && start) {
            timeline = ctx.engine().startTimeline(id, duration, period);
          }
          if (timeline == null) {
            return;
          }
          AtomicBoolean cancelled = new AtomicBoolean(false);
          ctx.state().onCancel(() -> cancelled.set(true));
          EffectsEngine.TimelineHandle handleRef = timeline;
          long timelineDuration = Math.max(1L, handleRef.durationTicks());
          timeline.subscribe(tick -> {
            if (cancelled.get()) {
              return;
            }
            double t = Math.min(1.0, Math.max(0.0, tick / (double) timelineDuration));
            Map<String, Object> values = new java.util.HashMap<>();
            values.put("timeline_id", handleRef.id());
            values.put("timeline_tick", tick);
            values.put("timeline_t", t);
            withTempVars(ctx, VarScope.CAST, values, () -> onTick.executeWithHandle(ctx));
          });
        };
      }
      if ("preset_timeline_pulse".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 100.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 5.0, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action onTick = parseBlock();
        return ctx -> {
          long duration = Math.max(1L, evalLong(durationTicks, ctx));
          long period = Math.max(1L, evalLong(periodTicks, ctx));
          Actions.timelinePresetPulse(duration, period, easingFromId(easingId),
              (exec, t) -> withTempVar(exec, VarScope.CAST, "t", t, () -> onTick.executeWithHandle(exec))).execute(ctx);
        };
      }
      if ("burst".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue timesValue = numAttr(attrs, "times", 6.0, stmtToken);
        NumValue spacingValue = numAttr(attrs, "spacingTicks", 0.0, stmtToken);
        NumValue delayValue = numAttr(attrs, "delayTicks", 0.0, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return ctx -> {
          int times = Math.max(1, evalInt(timesValue, ctx));
          if (times > MAX_REPEAT_TIMES) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("dsl burst capped: " + times + " -> " + MAX_REPEAT_TIMES);
            }
            times = MAX_REPEAT_TIMES;
          }
          long spacing = Math.max(0L, evalLong(spacingValue, ctx));
          long delay = Math.max(0L, evalLong(delayValue, ctx));
          java.util.List<Actions.TimelineEntry> entries = new java.util.ArrayList<>(times);
          for (int i = 0; i < times; i++) {
            double t = times <= 1 ? 1.0 : i / (double) (times - 1);
            long at = delay + (spacing * i);
            dev.patric.dungeonsreborn.effects.actions.Action step = stepCtx ->
                withTempVar(stepCtx, VarScope.CAST, "t", t, () -> inner.execute(stepCtx));
            entries.add(new Actions.TimelineEntry(at, step));
          }
          Actions.timeline(entries).execute(ctx);
        };
      }
      if ("pulse".equalsIgnoreCase(name) || "loop".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 10.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", false, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationTicks, ctx));
            long period = Math.max(0L, evalLong(periodTicks, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            return Actions.animate(duration, period, easingFromId(easingId), (tickCtx, t) -> {
              CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
              withTempVar(exec, VarScope.CAST, "t", t, () -> inner.executeWithHandle(exec));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("trail".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 2.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationTicks, ctx));
            long period = Math.max(0L, evalLong(periodTicks, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            return Actions.animate(duration, period, easingFromId(easingId), (tickCtx, t) -> {
              CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
              withTempVar(exec, VarScope.CAST, "t", t, () -> inner.executeWithHandle(exec));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("animate_realtime".equalsIgnoreCase(name) || "animate_real_time".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationMillis = numAttr(attrs, "durationMillis", 1000.0, stmtToken);
        NumValue periodMillis = numAttr(attrs, "periodMillis", 50.0, stmtToken);
        boolean followCaster = boolAttr(attrs, "followCaster", true, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long duration = Math.max(0L, evalLong(durationMillis, ctx));
            long period = Math.max(0L, evalLong(periodMillis, ctx));
            if (duration <= 0L || period <= 0L) {
              return ActionHandle.completed();
            }
            return Actions.animateRealTime(Duration.ofMillis(duration), Duration.ofMillis(period), easingFromId(easingId), (tickCtx, t) -> {
              CastContext exec = followCaster ? followCasterContext(tickCtx) : tickCtx;
              withTempVar(exec, VarScope.CAST, "t", t, () -> inner.executeWithHandle(exec));
            }).executeWithHandle(ctx);
          }
        };
      }
      if ("delay".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue ticksValue = numAttr(attrs, "ticks", 0.0, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long ticks = Math.max(0L, evalLong(ticksValue, ctx));
            incPending(ctx);
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean done = new AtomicBoolean(false);
            Runnable finish = () -> {
              if (finished.compareAndSet(false, true)) {
                decPending(ctx);
                done.set(true);
              }
            };
            if (ticks <= 0L) {
              try {
                inner.executeWithHandle(ctx);
              } finally {
                finish.run();
              }
              return ActionHandle.completed();
            }
            EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
            handle[0] = ctx.engine().runLater(ticks, () -> {
              try {
                if (handle[0] == null || handle[0].isCancelled()) {
                  finish.run();
                  return;
                }
                inner.executeWithHandle(ctx);
              } finally {
                finish.run();
              }
            });
            ctx.state().track(handle[0]);
            return new ActionHandle() {
              @Override
              public boolean cancel() {
                boolean cancelled = handle[0] != null && handle[0].cancel();
                finish.run();
                return cancelled;
              }

              @Override
              public boolean isDone() {
                return done.get() || handle[0] == null || handle[0].isCancelled();
              }
            };
          }
        };
      }
      if ("repeat".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue timesValue = numAttr(attrs, "times", 1.0, stmtToken);
        NumValue everyValue = numAttr(attrs, "every", 1.0, stmtToken);
        NumValue delayValue = numAttr(attrs, "delay", 0.0, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            int times = Math.max(1, evalInt(timesValue, ctx));
            if (times > MAX_REPEAT_TIMES) {
              if (ctx.engine().isDebugEnabled()) {
                ctx.engine().debug("dsl repeat capped: " + times + " -> " + MAX_REPEAT_TIMES);
              }
              times = MAX_REPEAT_TIMES;
            }
            long every = Math.max(1L, evalLong(everyValue, ctx));
            long delay = Math.max(0L, evalLong(delayValue, ctx));
            incPending(ctx);
            final int[] remaining = new int[] { times };
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean done = new AtomicBoolean(false);
            Runnable finish = () -> {
              if (finished.compareAndSet(false, true)) {
                decPending(ctx);
                done.set(true);
              }
            };
            final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
            handle[0] = ctx.engine().runRepeating(delay, every, () -> {
              if (handle[0] == null || handle[0].isCancelled()) {
                finish.run();
                return;
              }
              if (remaining[0]-- <= 0) {
                handle[0].cancel();
                finish.run();
                return;
              }
              inner.executeWithHandle(ctx);
            });
            ctx.state().track(handle[0]);
            return new ActionHandle() {
              @Override
              public boolean cancel() {
                boolean cancelled = handle[0] != null && handle[0].cancel();
                finish.run();
                return cancelled;
              }

              @Override
              public boolean isDone() {
                return done.get() || handle[0] == null || handle[0].isCancelled();
              }
            };
          }
        };
      }
      if ("on_tick".equalsIgnoreCase(name) || "onTick".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue ticksValue = numAttr(attrs, "ticks", 20.0, stmtToken);
        NumValue everyValue = numAttr(attrs, "every", 1.0, stmtToken);
        String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
        EasingId easingId = parseEasing(easingRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action inner = parseBlock();
        return new ActionWithHandle() {
          @Override
          public ActionHandle executeWithHandle(CastContext ctx) {
            long ticks = Math.max(1L, evalLong(ticksValue, ctx));
            if (ticks > MAX_ON_TICK_TICKS) {
              if (ctx.engine().isDebugEnabled()) {
                ctx.engine().debug("dsl on_tick capped: " + ticks + " -> " + MAX_ON_TICK_TICKS);
              }
              ticks = MAX_ON_TICK_TICKS;
            }
            final long totalTicks = ticks;
            long every = Math.max(1L, evalLong(everyValue, ctx));
            incPending(ctx);
            final long start = ctx.engine().tickNow();
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean done = new AtomicBoolean(false);
            Runnable finish = () -> {
              if (finished.compareAndSet(false, true)) {
                decPending(ctx);
                done.set(true);
              }
            };
            final EffectsEngine.ScheduledHandle[] handle = new EffectsEngine.ScheduledHandle[1];
            handle[0] = ctx.engine().runRepeating(0L, every, () -> {
              if (handle[0] == null || handle[0].isCancelled()) {
                finish.run();
                return;
              }
              long elapsed = ctx.engine().tickNow() - start;
              if (elapsed >= totalTicks) {
                handle[0].cancel();
                finish.run();
                return;
              }
              double t = elapsed / (double) totalTicks;
              double eased = easingFromId(easingId).applyAsDouble(t);
              withTempVar(ctx, VarScope.CAST, "t", eased, () -> inner.executeWithHandle(ctx));
            });
            ctx.state().track(handle[0]);
            return new ActionHandle() {
              @Override
              public boolean cancel() {
                boolean cancelled = handle[0] != null && handle[0].cancel();
                finish.run();
                return cancelled;
              }

              @Override
              public boolean isDone() {
                return done.get() || handle[0] == null || handle[0].isCancelled();
              }
            };
          }
        };
      }

      if ("chance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        Value pValue = attrs.containsKey("probability") ? attrs.get("probability") : attrs.get("p");
        NumValue probability = pValue == null ? new ConstNum(0.5) : numFromValue(pValue, "probability", stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          double p = evalDouble(probability, ctx);
          if (p <= 0.0) {
            finalElse.execute(ctx);
            return;
          }
          if (p >= 1.0) {
            thenAction.execute(ctx);
            return;
          }
          if (ctx.rng().nextDouble() < p) {
            thenAction.execute(ctx);
          } else {
            finalElse.execute(ctx);
          }
        };
      }

      if ("invoke".equalsIgnoreCase(name) || "invoke_ability".equalsIgnoreCase(name) || "invokeAbility".equalsIgnoreCase(name)) {
        String rawAbility = null;
        if (lookahead.type == TokenType.STRING) {
          rawAbility = requireStringToken("ability");
        }
        Map<String, Value> attrs = parseAttributes();
        if (rawAbility == null) {
          rawAbility = stringValue(requireAttr(attrs, "ability", stmtToken), "ability", stmtToken);
        }
        String abilityId;
        try {
          abilityId = dev.patric.dungeonsreborn.effects.Ids.normalize(rawAbility);
        } catch (Exception ex) {
          throw error(stmtToken, "invalid ability id: " + rawAbility + " (" + ex.getMessage() + ")");
        }
        String mode = stringAttr(attrs, "mode", "subgraph", stmtToken).trim().toLowerCase(Locale.ROOT);
        NumValue maxDepthValue = numAttr(attrs, "maxDepth", 8.0, stmtToken);
        return ctx -> {
          int maxDepth = Math.max(1, evalInt(maxDepthValue, ctx));
          if ("cast".equals(mode)) {
            if (ctx.engine().hasAbility(abilityId)) {
              ctx.engine().cast(abilityId, ctx.caster());
            } else if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke cast: ability not registered: " + abilityId);
            }
            return;
          }

          dev.patric.dungeonsreborn.effects.actions.Action target = yamlActionGraphs.get(abilityId);
          if (target == null) {
            if (ctx.engine().hasAbility(abilityId)) {
              ctx.engine().cast(abilityId, ctx.caster());
              return;
            }
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke: unknown ability: " + abilityId);
            }
            return;
          }

          Object existing = ctx.state().get(YAML_INVOKE_STACK);
          @SuppressWarnings("unchecked")
          java.util.ArrayDeque<String> stack = existing instanceof java.util.ArrayDeque<?> d ? (java.util.ArrayDeque<String>) d : null;
          if (stack == null) {
            stack = new java.util.ArrayDeque<>();
            stack.addLast(ctx.abilityId());
            ctx.state().put(YAML_INVOKE_STACK, stack);
          } else if (stack.isEmpty()) {
            stack.addLast(ctx.abilityId());
          }

          if (stack.size() >= maxDepth) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke: maxDepth reached (" + maxDepth + "): " + String.join(" -> ", stack));
            }
            return;
          }
          if (stack.contains(abilityId)) {
            if (ctx.engine().isDebugEnabled()) {
              ctx.engine().debug("invoke: cycle detected: " + String.join(" -> ", stack) + " -> " + abilityId);
            }
            return;
          }

          stack.addLast(abilityId);
          try {
            target.execute(ctx);
          } finally {
            stack.removeLast();
          }
        };
      }

      if ("debug_log".equalsIgnoreCase(name) || "debug".equalsIgnoreCase(name)) {
        String raw = requireStringToken("debug_log");
        return ctx -> {
          if (!ctx.engine().isDebugEnabled()) {
            return;
          }
          ctx.engine().debug(interpolate(raw, ctx));
        };
      }

      if ("title".equalsIgnoreCase(name)) {
        String rawTitle = requireStringToken("title");
        Map<String, Value> attrs = parseAttributes();
        String rawSubtitle = attrs.containsKey("subtitle") ? stringValue(attrs.get("subtitle"), "subtitle", stmtToken) : null;
        NumValue fadeIn = attrs.containsKey("fadeInTicks")
            ? numFromValue(attrs.get("fadeInTicks"), "fadeInTicks", stmtToken)
            : numAttr(attrs, "fadeIn", 10.0, stmtToken);
        NumValue stay = attrs.containsKey("stayTicks")
            ? numFromValue(attrs.get("stayTicks"), "stayTicks", stmtToken)
            : numAttr(attrs, "stay", 40.0, stmtToken);
        NumValue fadeOut = attrs.containsKey("fadeOutTicks")
            ? numFromValue(attrs.get("fadeOutTicks"), "fadeOutTicks", stmtToken)
            : numAttr(attrs, "fadeOut", 10.0, stmtToken);
        return ctx -> {
          if (ctx.caster() instanceof Player player) {
            Component title = renderText(rawTitle, ctx);
            Component subtitle = rawSubtitle == null ? Component.empty() : renderText(rawSubtitle, ctx);
            long fi = Math.max(0L, evalLong(fadeIn, ctx));
            long st = Math.max(0L, evalLong(stay, ctx));
            long fo = Math.max(0L, evalLong(fadeOut, ctx));
            Title.Times times = Title.Times.times(Duration.ofMillis(fi * 50L), Duration.ofMillis(st * 50L), Duration.ofMillis(fo * 50L));
            player.showTitle(Title.title(title, subtitle, times));
          }
        };
      }

      if ("overlay".equalsIgnoreCase(name)) {
        String rawTitle = requireStringToken("overlay");
        Map<String, Value> attrs = parseAttributes();
        String rawSubtitle = attrs.containsKey("subtitle") ? stringValue(attrs.get("subtitle"), "subtitle", stmtToken) : null;
        NumValue fadeIn = attrs.containsKey("fadeInTicks")
            ? numFromValue(attrs.get("fadeInTicks"), "fadeInTicks", stmtToken)
            : numAttr(attrs, "fadeIn", 10.0, stmtToken);
        NumValue stay = attrs.containsKey("stayTicks")
            ? numFromValue(attrs.get("stayTicks"), "stayTicks", stmtToken)
            : numAttr(attrs, "stay", 40.0, stmtToken);
        NumValue fadeOut = attrs.containsKey("fadeOutTicks")
            ? numFromValue(attrs.get("fadeOutTicks"), "fadeOutTicks", stmtToken)
            : numAttr(attrs, "fadeOut", 10.0, stmtToken);
        return ctx -> {
          Player player = targetPlayer(ctx);
          if (player == null) {
            return;
          }
          Component title = renderText(rawTitle, ctx);
          Component subtitle = rawSubtitle == null ? Component.empty() : renderText(rawSubtitle, ctx);
          long fi = Math.max(0L, evalLong(fadeIn, ctx));
          long st = Math.max(0L, evalLong(stay, ctx));
          long fo = Math.max(0L, evalLong(fadeOut, ctx));
          Actions.overlay(title, subtitle, Duration.ofMillis(fi * 50L), Duration.ofMillis(st * 50L), Duration.ofMillis(fo * 50L))
              .execute(new CastContext(ctx.engine(), ctx.plugin(), ctx.castId(), ctx.abilityId(), ctx.tick(), ctx.state(),
                  player, ctx.origin(), ctx.direction(), ctx.itemInHand()));
        };
      }

      if ("screen_shake".equalsIgnoreCase(name) || "shake".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 20.0, stmtToken);
        NumValue amplifier = numAttr(attrs, "amplifier", 0.0, stmtToken);
        boolean ambient = boolAttr(attrs, "ambient", false, stmtToken);
        boolean particles = boolAttr(attrs, "particles", true, stmtToken);
        boolean icon = boolAttr(attrs, "icon", true, stmtToken);
        return ctx -> {
          Player player = targetPlayer(ctx);
          if (player == null) {
            return;
          }
          int duration = Math.max(0, evalInt(durationTicks, ctx));
          int amp = Math.max(0, evalInt(amplifier, ctx));
          Actions.screenShake(player, ctx.engine().cinematicSettings(), duration, amp, ambient, particles, icon);
        };
      }

      if ("screen_flash".equalsIgnoreCase(name) || "flash".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        Particle particle = attrs.containsKey("particle")
            ? parseParticle(requireAttr(attrs, "particle", stmtToken), stmtToken, "particle")
            : Particle.FLASH;
        NumValue count = numAttr(attrs, "count", 1.0, stmtToken);
        NumValue offset = numAttr(attrs, "offset", 0.0, stmtToken);
        NumValue extra = numAttr(attrs, "extra", 0.0, stmtToken);
        Value soundAttr = attrs.get("sound");
        Sound sound = soundAttr == null ? null : parseSound(soundAttr, stmtToken, "sound");
        NumValue volume = numAttr(attrs, "volume", 1.0, stmtToken);
        NumValue pitch = numAttr(attrs, "pitch", 1.0, stmtToken);
        return ctx -> {
          Player player = targetPlayer(ctx);
          if (player == null) {
            return;
          }
          int emitCount = evalInt(count, ctx);
          double off = evalDouble(offset, ctx);
          double ex = evalDouble(extra, ctx);
          Actions.screenFlash(player, ctx.engine().cinematicSettings(), particle, emitCount, off, ex);
          if (sound != null) {
            player.getWorld().playSound(player.getLocation(), sound,
                (float) evalDouble(volume, ctx), (float) evalDouble(pitch, ctx));
          }
        };
      }

      if ("damage".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.DIRECT, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (lookahead.type == TokenType.LBRACE) {
          consume(TokenType.LBRACE);
          while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
            String inner = requireIdent("damage hook");
            if (!"on_hit".equalsIgnoreCase(inner) && !"onhit".equalsIgnoreCase(inner)) {
              throw error(stmtToken, "damage only supports on_hit block");
            }
            onHitAction = parseBlock();
          }
          consume(TokenType.RBRACE);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damage(dmg, policy, cause, source, tags).execute(ctx, target);
          finalOnHit.execute(ctx);
        };
      }
      if ("damage_typed".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        DamageType type = parseDamageType(requireAttr(attrs, "type", stmtToken), stmtToken, "type");
        boolean ignoreResistance = boolAttr(attrs, "ignoreResistance", false, stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.DIRECT, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageTyped(dmg, type, ignoreResistance, policy, cause, source, tags).execute(ctx, target);
        };
      }
      if ("set_resistance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        DamageType type = parseDamageType(requireAttr(attrs, "type", stmtToken), stmtToken, "type");
        NumValue multiplier = numFromValue(requireAttr(attrs, "multiplier", stmtToken), "multiplier", stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 0.0, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double mult = evalDouble(multiplier, ctx);
          if (!Double.isFinite(mult) || mult < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), type, mult);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().restoreResistance(target.getUniqueId(), type, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      if ("add_resistance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        DamageType type = parseDamageType(requireAttr(attrs, "type", stmtToken), stmtToken, "type");
        NumValue delta = numFromValue(requireAttr(attrs, "delta", stmtToken), "delta", stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 0.0, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double add = evalDouble(delta, ctx);
          if (!Double.isFinite(add)) {
            return;
          }
          double current = ctx.engine().resistanceMultiplier(target.getUniqueId(), type);
          double next = current + add;
          if (!Double.isFinite(next) || next < 0.0) {
            return;
          }
          var snapshot = ctx.engine().setResistance(target.getUniqueId(), type, next);
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().restoreResistance(target.getUniqueId(), type, snapshot.token(), snapshot.previous()));
            ctx.state().track(handle);
          }
        };
      }
      if ("clear_resistance".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        Value typeValue = attrs.get("type");
        DamageType type = typeValue == null ? null : parseDamageType(typeValue, stmtToken, "type");
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          if (type == null) {
            ctx.engine().clearResistances(target.getUniqueId());
          } else {
            ctx.engine().clearResistance(target.getUniqueId(), type);
          }
        };
      }
      if ("set_reflect".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue ratio = numAttr(attrs, "ratio", 0.25, stmtToken);
        NumValue flat = numAttr(attrs, "flat", 0.0, stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 0.0, stmtToken);
        boolean ignoreResistance = boolAttr(attrs, "ignoreResistance", false, stmtToken);
        DamageType type = attrs.containsKey("type") ? parseDamageType(attrs.get("type"), stmtToken, "type") : null;
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double r = evalDouble(ratio, ctx);
          double f = evalDouble(flat, ctx);
          if (!(r > 0.0) && !(f > 0.0)) {
            return;
          }
          long token = ctx.engine().setReflect(target.getUniqueId(), new EffectsEngine.ReflectSpec(r, f, type, ignoreResistance, policy));
          long duration = evalLong(durationTicks, ctx);
          if (duration > 0) {
            var handle = ctx.engine().runLater(duration, () -> ctx.engine().clearReflect(target.getUniqueId(), token));
            ctx.state().track(handle);
          }
        };
      }
      if ("clear_reflect".equalsIgnoreCase(name)) {
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          ctx.engine().clearReflect(target.getUniqueId());
        };
      }
      if ("damage_percent".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue percent = numAttr(attrs, "percent", 0.15, stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.PERCENT, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double pct = evalDouble(percent, ctx);
          if (pct <= 0.0) {
            return;
          }
          EntityActions.damagePercent(pct, policy, cause, source, tags).execute(ctx, target);
        };
      }
      if ("damage_true".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.TRUE, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageTrue(dmg, policy, cause, source, tags).execute(ctx, target);
        };
      }
      if ("damage_falloff".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue maxDistance = numAttr(attrs, "maxDistance", 12.0, stmtToken);
        NumValue minMultiplier = numAttr(attrs, "minMultiplier", 0.2, stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.FALLOFF, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          double maxDist = evalDouble(maxDistance, ctx);
          double minMult = evalDouble(minMultiplier, ctx);
          if (dmg <= 0.0 || maxDist <= 0.0) {
            return;
          }
          EntityActions.damageWithFalloff(dmg, maxDist, minMult, policy, cause, source, tags).execute(ctx, target);
        };
      }
      if ("damage_crit".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue critChance = numAttr(attrs, "critChance", 0.2, stmtToken);
        NumValue critMultiplier = numAttr(attrs, "critMultiplier", 1.5, stmtToken);
        NumValue headshotMultiplier = numAttr(attrs, "headshotMultiplier", 1.0, stmtToken);
        NumValue headshotThreshold = numAttr(attrs, "headshotThreshold", 0.25, stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.CRIT, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageCrit(
              dmg,
              evalDouble(critChance, ctx),
              evalDouble(critMultiplier, ctx),
              evalDouble(headshotMultiplier, ctx),
              evalDouble(headshotThreshold, ctx),
              policy,
              cause,
              source,
              tags).execute(ctx, target);
        };
      }
      if ("damage_lifesteal".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue ratio = numAttr(attrs, "ratio", 0.25, stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.LIFESTEAL, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          if (dmg <= 0.0) {
            return;
          }
          EntityActions.damageLifesteal(dmg, evalDouble(ratio, ctx), policy, cause, source, tags).execute(ctx, target);
        };
      }
      if ("damage_dot".equalsIgnoreCase(name) || "damage_over_time".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 10.0, stmtToken);
        NumValue times = numAttr(attrs, "times", 5.0, stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.DOT, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        dev.patric.dungeonsreborn.effects.actions.Action onTickAction = Actions.noop();
        if (lookahead.type == TokenType.LBRACE) {
          consume(TokenType.LBRACE);
          while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
            String inner = requireIdent("damage_dot hook");
            if ("on_hit".equalsIgnoreCase(inner) || "onhit".equalsIgnoreCase(inner)) {
              onHitAction = parseBlock();
              continue;
            }
            if ("on_tick".equalsIgnoreCase(inner) || "ontick".equalsIgnoreCase(inner)) {
              onTickAction = parseBlock();
              continue;
            }
            throw error(stmtToken, "damage_dot supports on_hit or on_tick blocks");
          }
          consume(TokenType.RBRACE);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnTick = onTickAction;
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          long period = evalLong(periodTicks, ctx);
          int count = evalInt(times, ctx);
          if (dmg <= 0.0 || period <= 0 || count <= 0) {
            return;
          }
          finalOnHit.execute(ctx);
          EntityActions.damageOverTime(dmg, period, count, policy, cause, source, tags, (cast, hit) -> {
            Object prev = cast.state().get(YAML_LAST_ENTITY);
            cast.state().put(YAML_LAST_ENTITY, hit);
            try {
              finalOnTick.execute(cast);
            } finally {
              cast.state().put(YAML_LAST_ENTITY, prev);
            }
          }).execute(ctx, target);
        };
      }
      if ("damage_chain".equalsIgnoreCase(name) || "chain_damage".equalsIgnoreCase(name) || "chain_lightning".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue radius = numAttr(attrs, "radius", 6.0, stmtToken);
        NumValue maxJumps = numAttr(attrs, "maxJumps", 4.0, stmtToken);
        NumValue delayTicks = numAttr(attrs, "delayTicks", 2.0, stmtToken);
        NumValue falloff = numAttr(attrs, "falloff", 0.8, stmtToken);
        DamageCause cause = damageCauseAttr(attrs, DamageCause.CHAIN, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (lookahead.type == TokenType.LBRACE) {
          onHitAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double dmg = evalDouble(amount, ctx);
          double r = evalDouble(radius, ctx);
          int jumps = evalInt(maxJumps, ctx);
          long delay = evalLong(delayTicks, ctx);
          double f = evalDouble(falloff, ctx);
          if (dmg <= 0.0 || r <= 0.0 || jumps <= 0 || delay < 0) {
            return;
          }
          EntityActions.chainDamage(dmg, r, jumps, delay, f, policy, cause, source, tags, (cast, hit) -> {
            Object prev = cast.state().get(YAML_LAST_ENTITY);
            cast.state().put(YAML_LAST_ENTITY, hit);
            try {
              finalOnHit.execute(cast);
            } finally {
              cast.state().put(YAML_LAST_ENTITY, prev);
            }
          }).execute(ctx, target);
        };
      }
      if ("lightning".equalsIgnoreCase(name) || "strike_lightning".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        boolean effectOnly = boolAttr(attrs, "effectOnly", true, stmtToken);
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          EntityActions.strikeLightning(effectOnly, policy).execute(ctx, target);
        };
      }

      if ("heal".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        HealType healType = attrs.containsKey("type")
            ? parseHealType(requireAttr(attrs, "type", stmtToken), stmtToken, "type")
            : HealType.DIRECT;
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        NumValue cap = numAttr(attrs, "cap", 0.0, stmtToken);
        boolean overhealToShield = boolAttr(attrs, "overhealToShield", false, stmtToken);
        NumValue shieldCap = numAttr(attrs, "shieldCap", 0.0, stmtToken);
        NumValue shieldDecayTicks = numAttr(attrs, "shieldDecayTicks", 0.0, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double heal = evalDouble(amount, ctx);
          if (heal <= 0.0) {
            return;
          }
          EntityActions.heal(
              heal,
              policy,
              healType,
              source,
              tags,
              evalDouble(cap, ctx),
              overhealToShield,
              evalDouble(shieldCap, ctx),
              evalLong(shieldDecayTicks, ctx)).execute(ctx, target);
        };
      }

      if ("heal_percent".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue percent = numAttr(attrs, "percent", 0.15, stmtToken);
        HealType healType = attrs.containsKey("type")
            ? parseHealType(requireAttr(attrs, "type", stmtToken), stmtToken, "type")
            : HealType.DIRECT;
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        NumValue cap = numAttr(attrs, "cap", 0.0, stmtToken);
        boolean overhealToShield = boolAttr(attrs, "overhealToShield", false, stmtToken);
        NumValue shieldCap = numAttr(attrs, "shieldCap", 0.0, stmtToken);
        NumValue shieldDecayTicks = numAttr(attrs, "shieldDecayTicks", 0.0, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double pct = evalDouble(percent, ctx);
          if (pct <= 0.0) {
            return;
          }
          EntityActions.healPercent(
              pct,
              policy,
              healType,
              source,
              tags,
              evalDouble(cap, ctx),
              overhealToShield,
              evalDouble(shieldCap, ctx),
              evalLong(shieldDecayTicks, ctx)).execute(ctx, target);
        };
      }

      if ("heal_hot".equalsIgnoreCase(name) || "heal_over_time".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue periodTicks = numAttr(attrs, "periodTicks", 10.0, stmtToken);
        NumValue times = numAttr(attrs, "times", 5.0, stmtToken);
        HealType healType = attrs.containsKey("type")
            ? parseHealType(requireAttr(attrs, "type", stmtToken), stmtToken, "type")
            : HealType.HOT;
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        NumValue cap = numAttr(attrs, "cap", 0.0, stmtToken);
        boolean overhealToShield = boolAttr(attrs, "overhealToShield", false, stmtToken);
        NumValue shieldCap = numAttr(attrs, "shieldCap", 0.0, stmtToken);
        NumValue shieldDecayTicks = numAttr(attrs, "shieldDecayTicks", 0.0, stmtToken);
        String source = stringAttr(attrs, "source", null, stmtToken);
        java.util.Set<String> tags = tagSetAttr(attrs, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action onTickAction = Actions.noop();
        if (lookahead.type == TokenType.LBRACE) {
          consume(TokenType.LBRACE);
          while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
            String inner = requireIdent("heal_hot hook");
            if (!"on_tick".equalsIgnoreCase(inner) && !"ontick".equalsIgnoreCase(inner)) {
              throw error(stmtToken, "heal_hot supports on_tick block");
            }
            onTickAction = parseBlock();
          }
          consume(TokenType.RBRACE);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnTick = onTickAction;
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double heal = evalDouble(amount, ctx);
          long period = evalLong(periodTicks, ctx);
          int count = evalInt(times, ctx);
          if (heal <= 0.0 || period <= 0 || count <= 0) {
            return;
          }
          EntityActions.healOverTime(
              heal,
              period,
              count,
              policy,
              healType,
              source,
              tags,
              evalDouble(cap, ctx),
              overhealToShield,
              evalDouble(shieldCap, ctx),
              evalLong(shieldDecayTicks, ctx),
              (cast, hit) -> {
                Object prev = cast.state().get(YAML_LAST_ENTITY);
                cast.state().put(YAML_LAST_ENTITY, hit);
                try {
                  finalOnTick.execute(cast);
                } finally {
                  cast.state().put(YAML_LAST_ENTITY, prev);
                }
              }).execute(ctx, target);
        };
      }

      if ("shield".equalsIgnoreCase(name) || "absorb".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue amount = numFromValue(requireAttr(attrs, "amount", stmtToken), "amount", stmtToken);
        NumValue cap = numAttr(attrs, "cap", 0.0, stmtToken);
        NumValue decayTicks = numAttr(attrs, "decayTicks", 0.0, stmtToken);
        HealType healType = "absorb".equalsIgnoreCase(name) ? HealType.ABSORB : HealType.SHIELD;
        String policyRaw = stringAttr(attrs, "policy", "hostile_default", stmtToken);
        EntityActions.DamagePolicy policy = parseDamagePolicy(policyRaw, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          double shield = evalDouble(amount, ctx);
          if (shield <= 0.0) {
            return;
          }
          EntityActions.shield(
              shield,
              evalDouble(cap, ctx),
              evalLong(decayTicks, ctx),
              policy,
              healType).execute(ctx, target);
        };
      }

      if ("potion".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        PotionEffectType effect = parsePotionEffect(requireAttr(attrs, "effect", stmtToken), stmtToken, "effect");
        NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
        NumValue amplifier = numAttr(attrs, "amplifier", 0.0, stmtToken);
        boolean ambient = boolAttr(attrs, "ambient", false, stmtToken);
        boolean particles = boolAttr(attrs, "particles", true, stmtToken);
        boolean icon = boolAttr(attrs, "icon", true, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          long ticks = Math.max(1L, evalLong(durationTicks, ctx));
          int amp = Math.max(0, evalInt(amplifier, ctx));
          EntityActions.potion(effect, Duration.ofMillis(ticks * 50L), amp, ambient, particles, icon).execute(ctx, target);
        };
      }

      if ("ignite".equalsIgnoreCase(name) || "fire".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 60.0, stmtToken);
        if (attrs.containsKey("ticks")) {
          durationTicks = numFromValue(requireAttr(attrs, "ticks", stmtToken), "ticks", stmtToken);
        }
        NumValue finalDuration = durationTicks;
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          int ticks = (int) Math.max(1L, evalLong(finalDuration, ctx));
          EntityActions.ignite(ticks).execute(ctx, target);
        };
      }

      if ("freeze".equalsIgnoreCase(name) || "freeze_ticks".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue durationTicks = numAttr(attrs, "durationTicks", 80.0, stmtToken);
        if (attrs.containsKey("ticks")) {
          durationTicks = numFromValue(requireAttr(attrs, "ticks", stmtToken), "ticks", stmtToken);
        }
        NumValue finalDuration = durationTicks;
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          int ticks = (int) Math.max(0L, evalLong(finalDuration, ctx));
          int capped = Math.min(ticks, target.getMaxFreezeTicks());
          target.setFreezeTicks(capped);
        };
      }

      if ("knockback".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue horizontal = numAttr(attrs, "horizontal", 1.0, stmtToken);
        NumValue vertical = numAttr(attrs, "vertical", 0.35, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          EntityActions.knockbackFromOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
        };
      }

      if ("pull".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue horizontal = numAttr(attrs, "horizontal", 0.75, stmtToken);
        NumValue vertical = numAttr(attrs, "vertical", 0.08, stmtToken);
        return ctx -> {
          LivingEntity target = lastEntity(ctx);
          if (target == null) {
            return;
          }
          EntityActions.pullToOrigin(evalDouble(horizontal, ctx), evalDouble(vertical, ctx)).execute(ctx, target);
        };
      }

      if ("raycast_hit_entity".equalsIgnoreCase(name) || "raycast_hit".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue maxDistance = numAttr(attrs, "maxDistance", 20.0, stmtToken);
        NumValue raySize = numAttr(attrs, "raySize", 0.35, stmtToken);
        boolean stopOnBlock = boolAttr(attrs, "stopOnBlock", true, stmtToken);
        boolean ignoreCaster = boolAttr(attrs, "ignoreCaster", true, stmtToken);
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double size = evalDouble(raySize, ctx);
          if (max <= 0.0 || size < 0.0) {
            finalElse.execute(ctx);
            return;
          }
          var hits = Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, e -> true).select(ctx);
          if (hits.isEmpty()) {
            finalElse.execute(ctx);
            return;
          }
          LivingEntity target = hits.get(0);
          ctx.state().put(YAML_LAST_ENTITY, target);
          thenAction.execute(ctx);
          Object hook = ctx.state().get(DSL_ON_HIT);
          if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
            action.execute(ctx);
          }
        };
      }

      if ("for_each_target".equalsIgnoreCase(name) || "targets".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        Targeter<LivingEntity> targeter = parseTargeter(attrs, stmtToken);
        String mode = stringAttr(attrs, "mode", "each", stmtToken).trim().toLowerCase(Locale.ROOT);
        boolean firstOnly = switch (mode) {
          case "each" -> false;
          case "first" -> true;
          default -> throw error(stmtToken, "invalid mode: " + mode + " (use each|first)");
        };
        NumValue maxTargets = numAttr(attrs, "maxTargets", 0.0, stmtToken);
        String sortRaw = stringAttr(attrs, "sort", "none", stmtToken).trim().toLowerCase(Locale.ROOT);
        AtMode originAt = parseAt(stringAttr(attrs, "originAt", "origin", stmtToken), pathAt(stmtToken) + ".originAt");
        dev.patric.dungeonsreborn.effects.actions.Action thenAction = parseBlock();
        dev.patric.dungeonsreborn.effects.actions.Action elseAction = Actions.noop();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          elseAction = parseBlock();
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalElse = elseAction;
        return ctx -> {
          CastContext selectCtx = ctx;
          if (originAt != AtMode.ORIGIN) {
            Location origin = resolveAt(ctx, originAt);
            if (origin.getWorld() != null) {
              selectCtx = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  ctx.direction().clone(),
                  ctx.itemInHand());
            }
          }
          List<LivingEntity> targets = new java.util.ArrayList<>(targeter.select(selectCtx));
          if (targets.isEmpty()) {
            finalElse.execute(ctx);
            return;
          }
          if (!"none".equals(sortRaw) && targets.size() > 1) {
            Location origin = selectCtx.origin();
            switch (sortRaw) {
              case "nearest" -> targets.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)));
              case "farthest" -> targets.sort(java.util.Comparator.comparingDouble((LivingEntity e) -> e.getLocation().distanceSquared(origin)).reversed());
              case "lowest_health" -> targets.sort(java.util.Comparator.comparingDouble(LivingEntity::getHealth));
              case "highest_health" -> targets.sort(java.util.Comparator.comparingDouble(LivingEntity::getHealth).reversed());
              case "random" -> java.util.Collections.shuffle(targets, ctx.rng());
              default -> throw error(stmtToken, "invalid sort: " + sortRaw + " (use none|nearest|farthest|lowest_health|highest_health|random)");
            }
          }
          int limit = targets.size();
          if (firstOnly) {
            limit = 1;
          }
          int max = evalInt(maxTargets, ctx);
          if (max > 0) {
            limit = Math.min(limit, max);
          }
          for (int i = 0; i < limit; i++) {
            ctx.state().put(YAML_LAST_ENTITY, targets.get(i));
            thenAction.execute(ctx);
          }
        };
      }

      if ("projectile".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        NumValue speedPerTick = numAttr(attrs, "speedPerTick", 1.3, stmtToken);
        NumValue maxDistance = numAttr(attrs, "maxDistance", 24.0, stmtToken);
        NumValue hitRadius = numAttr(attrs, "hitRadius", 0.25, stmtToken);
        boolean ignoreCaster = boolAttr(attrs, "ignoreCaster", true, stmtToken);
        String bc = stringAttr(attrs, "blockCollision", "stop", stmtToken).toUpperCase(Locale.ROOT);
        boolean bounce = "BOUNCE".equals(bc);
        boolean passThrough = "PASS_THROUGH".equals(bc) || "PASS-THROUGH".equals(bc) || "PASS".equals(bc);
        NumValue bounceMaxTmp = null;
        NumValue bounceRestitutionTmp = null;
        if (bounce) {
          Value maxAttr = attrs.getOrDefault("bounceMax", attrs.get("bouncesMax"));
          if (maxAttr != null) {
            bounceMaxTmp = numFromValue(maxAttr, "bounceMax", stmtToken);
          }
          Value restAttr = attrs.getOrDefault("bounceRestitution", attrs.get("bouncesRestitution"));
          if (restAttr != null) {
            bounceRestitutionTmp = numFromValue(restAttr, "bounceRestitution", stmtToken);
          }
        }
        final NumValue bounceMax = bounceMaxTmp;
        final NumValue bounceRestitution = bounceRestitutionTmp;
        Value trailParticleAttr = attrs.get("trailParticle");
        boolean hasTrail = trailParticleAttr != null;
        Particle trailParticle = hasTrail ? parseParticle(trailParticleAttr, stmtToken, "trailParticle") : null;
        NumValue trailCount = hasTrail ? numAttr(attrs, "trailCount", 1.0, stmtToken) : null;
        NumValue trailOffset = hasTrail ? numAttr(attrs, "trailOffset", 0.0, stmtToken) : null;
        NumValue trailExtra = hasTrail ? numAttr(attrs, "trailExtra", 0.0, stmtToken) : null;

        dev.patric.dungeonsreborn.effects.actions.Action onHitAction = Actions.noop();
        if (lookahead.type == TokenType.LBRACE) {
          consume(TokenType.LBRACE);
          while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
            String inner = requireIdent("projectile hook");
            if (!"on_hit".equalsIgnoreCase(inner) && !"onhit".equalsIgnoreCase(inner)) {
              throw error(stmtToken, "projectile only supports on_hit block");
            }
            onHitAction = parseBlock();
          }
          consume(TokenType.RBRACE);
        }
        final dev.patric.dungeonsreborn.effects.actions.Action finalOnHit = onHitAction;
        return ctx -> {
          double speed = evalDouble(speedPerTick, ctx);
          double max = evalDouble(maxDistance, ctx);
          double radius = evalDouble(hitRadius, ctx);
          if (speed <= 0.0 || max <= 0.0 || radius < 0.0) {
            return;
          }
          if (hasTrail) {
            int count = trailCount == null ? 1 : Math.max(0, evalInt(trailCount, ctx));
            if (count > 0) {
              long steps = (long) Math.ceil(max / Math.max(1e-6, speed)) + 1L;
              long total = steps * count;
              if (!consumeParticles(ctx, total)) {
                return;
              }
            }
          }
          ProjectileSpec.Builder b = ProjectileSpec.builder()
              .speedPerTick(speed)
              .maxDistance(max)
              .hitRadius(radius)
              .ignoreCaster(ignoreCaster);
          if (passThrough) {
            b.stopOnBlock(false);
          } else if (bounce) {
            int maxBounce = bounceMax == null ? 0 : Math.max(0, evalInt(bounceMax, ctx));
            double restitution = bounceRestitution == null ? 0.9 : Math.max(0.0, evalDouble(bounceRestitution, ctx));
            b.bounces(maxBounce, restitution);
          } else {
            b.stopOnBlock(true);
          }
          if (hasTrail) {
            int count = trailCount == null ? 1 : Math.max(0, evalInt(trailCount, ctx));
            double offset = trailOffset == null ? 0.0 : Math.max(0.0, evalDouble(trailOffset, ctx));
            double extra = trailExtra == null ? 0.0 : evalDouble(trailExtra, ctx);
            b.trail(trailParticle, count, offset, extra);
          }
          b.frameOut(frame -> ctx.state().put(Vars.PROJECTILE_FRAME, frame));
          b.onHit(hit -> {
            CastContext cast = hit.cast();
            cast.state().put(YAML_LAST_ENTITY, hit.hitEntity());
            finalOnHit.execute(cast);
            Object hook = cast.state().get(DSL_ON_HIT);
            if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
              action.execute(cast);
            }
          });
          Actions.projectile(b.build()).execute(ctx);
        };
      }

      if ("summon_minion".equalsIgnoreCase(name) || "minion_summon".equalsIgnoreCase(name) || "minions".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        String mobId = stringValue(requireAttr(attrs, "mob", stmtToken), "mob", stmtToken);
        String minionId = attrs.containsKey("id") ? stringValue(attrs.get("id"), "id", stmtToken) : mobId;
        NumValue count = numAttr(attrs, "count", 1.0, stmtToken);
        NumValue durationTicks = numAttr(attrs, "durationTicks", 20.0 * 30.0, stmtToken);
        NumValue radius = numAttr(attrs, "radius", 1.5, stmtToken);
        NumValue waves = numAttr(attrs, "waves", 1.0, stmtToken);
        NumValue waveIntervalTicks = numAttr(attrs, "waveIntervalTicks", 0.0, stmtToken);
        String formationRaw = stringAttr(attrs, "formation", null, stmtToken);
        MinionFormation formation = parseMinionFormation(formationRaw, pathAt(stmtToken) + ".formation");
        NumValue formationRadius = numAttr(attrs, "formationRadius", 0.0, stmtToken);
        boolean safeSpawn = boolAttr(attrs, "safeSpawn", false, stmtToken);
        NumValue maxSpawnAttempts = numAttr(attrs, "maxSpawnAttempts", 6.0, stmtToken);
        boolean despawnOnLogout = boolAttr(attrs, "despawnOnLogout", true, stmtToken);
        boolean persistent = boolAttr(attrs, "persistent", false, stmtToken);
        String modeRaw = stringAttr(attrs, "mode", null, stmtToken);
        MinionMode mode = parseMinionMode(modeRaw, pathAt(stmtToken) + ".mode");
        boolean allowPvp = boolAttr(attrs, "allowPvp", false, stmtToken);
        boolean allowPartyTargets = boolAttr(attrs, "allowPartyTargets", false, stmtToken);
        boolean shareOwnerAggro = boolAttr(attrs, "shareOwnerAggro", true, stmtToken);
        NumValue maxDistanceFromOwner = numAttr(attrs, "maxDistanceFromOwner", 0.0, stmtToken);
        boolean sharePotionEffects = boolAttr(attrs, "sharePotionEffects", false, stmtToken);
        NumValue summonMana = numAttr(attrs, "summonMana", 0.0, stmtToken);
        NumValue summonCooldownTicks = numAttr(attrs, "summonCooldownTicks", 0.0, stmtToken);
        String summonCooldownKey = stringAttr(attrs, "summonCooldownKey", null, stmtToken);
        NumValue ownerLevelScale = numAttr(attrs, "ownerLevel", 0.0, stmtToken);
        NumValue ownerStrengthScale = numAttr(attrs, "ownerStrength", 0.0, stmtToken);
        NumValue ownerDexterityScale = numAttr(attrs, "ownerDexterity", 0.0, stmtToken);
        NumValue ownerIntelligenceScale = numAttr(attrs, "ownerIntelligence", 0.0, stmtToken);
        NumValue ownerVitalityScale = numAttr(attrs, "ownerVitality", 0.0, stmtToken);
        NumValue ownerMaxHealthScale = numAttr(attrs, "ownerMaxHealth", 0.0, stmtToken);
        NumValue ownerMaxManaScale = numAttr(attrs, "ownerMaxMana", 0.0, stmtToken);
        NumValue bonusHealthCap = numAttr(attrs, "bonusHealthCap", 0.0, stmtToken);
        NumValue bonusDamageCap = numAttr(attrs, "bonusDamageCap", 0.0, stmtToken);
        NumValue decayExponent = numAttr(attrs, "decayExponent", 0.0, stmtToken);
        NumValue healthPerLevel = numAttrAlias(attrs, "scale_healthPerLevel", "healthPerLevel", 0.0, stmtToken);
        NumValue damagePerLevel = numAttrAlias(attrs, "scale_damagePerLevel", "damagePerLevel", 0.0, stmtToken);
        NumValue healthPerMaxHealth = numAttrAlias(attrs, "scale_healthPerMaxHealth", "healthPerMaxHealth", 0.0, stmtToken);
        NumValue damagePerMaxHealth = numAttrAlias(attrs, "scale_damagePerMaxHealth", "damagePerMaxHealth", 0.0, stmtToken);
        NumValue healthPerManaMax = numAttrAlias(attrs, "scale_healthPerManaMax", "healthPerManaMax", 0.0, stmtToken);
        NumValue damagePerManaMax = numAttrAlias(attrs, "scale_damagePerManaMax", "damagePerManaMax", 0.0, stmtToken);

        Map<DamageType, NumValue> resistanceDefs = new java.util.EnumMap<>(DamageType.class);
        java.util.Set<DamageType> immunities = new java.util.HashSet<>();
        for (Map.Entry<String, Value> entry : attrs.entrySet()) {
          String key = entry.getKey();
          if (key.startsWith("resistance_") || key.startsWith("resist_")) {
            String typeRaw = key.substring(key.indexOf('_') + 1);
            DamageType type = parseDamageTypeKey(typeRaw, pathAt(stmtToken) + "." + key);
            resistanceDefs.put(type, numFromValue(entry.getValue(), key, stmtToken));
          }
          if (key.startsWith("immune_") || key.startsWith("immunity_")) {
            String typeRaw = key.substring(key.indexOf('_') + 1);
            if (boolAttr(attrs, key, false, stmtToken)) {
              immunities.add(parseDamageTypeKey(typeRaw, pathAt(stmtToken) + "." + key));
            }
          }
        }

        String passivesRaw = stringAttr(attrs, "passives", null, stmtToken);
        if (passivesRaw == null) {
          passivesRaw = stringAttr(attrs, "passive", null, stmtToken);
        }
        List<String> passiveIds = splitIdList(passivesRaw);
        NumValue passivePeriod = numAttr(attrs, "passivePeriodTicks", 40.0, stmtToken);

        String specialsRaw = stringAttr(attrs, "specialAttacks", null, stmtToken);
        if (specialsRaw == null) {
          specialsRaw = stringAttr(attrs, "specials", null, stmtToken);
        }
        List<String> specialIds = splitIdList(specialsRaw);
        NumValue specialCooldown = numAttr(attrs, "specialCooldownTicks", 60.0, stmtToken);
        NumValue specialChance = numAttr(attrs, "specialChance", 1.0, stmtToken);
        NumValue specialCostMultiplier = numAttr(attrs, "specialCostMultiplier", 1.0, stmtToken);
        NumValue specialCostAdd = numAttr(attrs, "specialCostAdd", 0.0, stmtToken);
        boolean specialRequireTarget = boolAttr(attrs, "specialRequireTarget", true, stmtToken);

        MinionManager minions = resolveMinionManager(pathAt(stmtToken));
        return ctx -> {
          if (!minions.hasMob(Ids.normalize(mobId))) {
            throw error(stmtToken, "unknown mob id: " + mobId);
          }
          int c = Math.max(0, evalInt(count, ctx));
          long dur = (long) Math.max(1L, Math.round(evalDouble(durationTicks, ctx)));
          double r = Math.max(0.0, evalDouble(radius, ctx));
          if (c <= 0 || dur <= 0) {
            return;
          }
          int waveCount = Math.max(1, evalInt(waves, ctx));
          long waveEvery = Math.max(0L, evalLong(waveIntervalTicks, ctx));
          double formationRadiusValue = Math.max(0.0, evalDouble(formationRadius, ctx));
          boolean safeSpawnValue = safeSpawn;
          int maxSpawnAttemptsValue = Math.max(1, evalInt(maxSpawnAttempts, ctx));
          MinionScaling scaling = new MinionScaling(
              evalDouble(healthPerLevel, ctx),
              evalDouble(damagePerLevel, ctx),
              evalDouble(healthPerMaxHealth, ctx),
              evalDouble(damagePerMaxHealth, ctx),
              evalDouble(healthPerManaMax, ctx),
              evalDouble(damagePerManaMax, ctx));

          Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
          for (Map.Entry<DamageType, NumValue> entry : resistanceDefs.entrySet()) {
            double value = evalDouble(entry.getValue(), ctx);
            if (Double.isFinite(value) && value >= 0.0) {
              resistances.put(entry.getKey(), value);
            }
          }
          java.util.Set<DamageType> immuneTypes = java.util.Set.copyOf(immunities);

          long passiveTicks = Math.max(1L, Math.round(evalDouble(passivePeriod, ctx)));
          List<MinionPassiveSpec> passives = new java.util.ArrayList<>();
          for (String abilityId : passiveIds) {
            passives.add(new MinionPassiveSpec(abilityId, passiveTicks));
          }

          long specialTicks = Math.max(1L, Math.round(evalDouble(specialCooldown, ctx)));
          double chance = Math.max(0.0, Math.min(1.0, evalDouble(specialChance, ctx)));
          double costMultiplier = Math.max(0.0, evalDouble(specialCostMultiplier, ctx));
          double costAdd = evalDouble(specialCostAdd, ctx);
          List<MinionSpecialAttackSpec> specials = new java.util.ArrayList<>();
          for (String abilityId : specialIds) {
            specials.add(new MinionSpecialAttackSpec(abilityId, specialTicks, chance, specialRequireTarget,
                costMultiplier, costAdd));
          }

          java.util.UUID ownerId = ctx.caster().getUniqueId();
          double summonManaValue = Math.max(0.0, evalDouble(summonMana, ctx));
          long summonCooldownValue = Math.max(0L, evalLong(summonCooldownTicks, ctx));
          if (summonCooldownValue > 0L && ctx.caster() instanceof Player player) {
            String key = summonCooldownKey == null || summonCooldownKey.isBlank()
                ? "minion:" + minionId
                : summonCooldownKey;
            if (!ctx.engine().tryStartCooldown(player.getUniqueId(), key, summonCooldownValue)) {
              return;
            }
          }
          if (summonManaValue > 0.0) {
            Component fail = Costs.mana(summonManaValue).tryApply(ctx);
            if (fail != null) {
              if (ctx.caster() instanceof Player player) {
                player.sendMessage(fail);
              }
              return;
            }
          }
          MinionSummonSpec summonSpec = new MinionSummonSpec(waveCount, waveEvery, formation,
              formationRadiusValue, safeSpawnValue, maxSpawnAttemptsValue);
          MinionOwnerScalingSpec ownerScaling = new MinionOwnerScalingSpec(
              evalDouble(ownerLevelScale, ctx),
              evalDouble(ownerStrengthScale, ctx),
              evalDouble(ownerDexterityScale, ctx),
              evalDouble(ownerIntelligenceScale, ctx),
              evalDouble(ownerVitalityScale, ctx),
              evalDouble(ownerMaxManaScale, ctx),
              evalDouble(ownerMaxHealthScale, ctx));
          MinionScalingLimits limits = new MinionScalingLimits(
              Math.max(0.0, evalDouble(bonusHealthCap, ctx)),
              Math.max(0.0, evalDouble(bonusDamageCap, ctx)),
              Math.max(0.0, evalDouble(decayExponent, ctx)));
          MinionSpec spec = new MinionSpec(minionId, Ids.normalize(mobId), c, dur, ownerId, r, summonSpec,
              scaling, resistances, immuneTypes, despawnOnLogout, persistent, mode,
              new MinionTargetRules(allowPvp, allowPartyTargets, shareOwnerAggro,
                  Math.max(0.0, evalDouble(maxDistanceFromOwner, ctx))),
              passives, specials, Map.of(), ownerScaling, limits, null, null, false, false, false,
              sharePotionEffects, null,
              null, null, 0L);
          java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
          java.util.List<LivingEntity> spawned = minions.summon(spec, ctx.caster().getLocation(), living -> {
            ids.add(living.getUniqueId());
          });
          ctx.state().put(Vars.MINION_ID, minionId);
          ctx.state().put(Vars.MINION_COUNT, spawned.size());
          ctx.state().put(Vars.MINION_IDS, java.util.List.copyOf(ids));
          ctx.state().put(Vars.MINION_DURATION, dur);
        };
      }

      if ("choice".equalsIgnoreCase(name)) {
        if (!(lookahead.type == TokenType.IDENT && "weighted".equalsIgnoreCase(lookahead.text))) {
          throw error(stmtToken, "choice requires 'weighted' block");
        }
        consume(TokenType.IDENT);
        return parseWeightedChoice(stmtToken);
      }

      if ("preset_beam_chargeup".equalsIgnoreCase(name)) {
        Map<String, Value> attrs = parseAttributes();
        return parseParticles("beam_chargeup", attrs, stmtToken);
      }

      if (name.toLowerCase(Locale.ROOT).startsWith("particles.")) {
        String shape = name.substring("particles.".length()).toLowerCase(Locale.ROOT);
        Map<String, Value> attrs = parseAttributes();
        return parseParticles(shape, attrs, stmtToken);
      }

      throw error(stmtToken, "unknown statement: " + name);
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseParticles(String shape, Map<String, Value> attrs, Token stmtToken) {
      Particle particle = parseParticle(requireAttr(attrs, "particle", stmtToken), stmtToken, "particle");
      NumValue count = numAttr(attrs, "count", 1.0, stmtToken);
      NumValue offset = numAttr(attrs, "offset", 0.0, stmtToken);
      NumValue extra = numAttr(attrs, "extra", 0.0, stmtToken);
      String atRaw = attrs.containsKey("frame")
          ? stringAttr(attrs, "frame", "origin", stmtToken)
          : stringAttr(attrs, "at", "origin", stmtToken);
      AtMode at = parseAt(atRaw, pathAt(stmtToken) + ".at");
      double[] offsets = offsetsFromAttrs(attrs, stmtToken);
      NumValue forward = numAttr(attrs, "forward", offsets == null ? 0.0 : offsets[0], stmtToken);
      NumValue right = numAttr(attrs, "right", offsets == null ? 0.0 : offsets[1], stmtToken);
      NumValue up = numAttr(attrs, "up", offsets == null ? 0.0 : offsets[2], stmtToken);
      Object data = particleDataFromAttrs(particle, attrs, stmtToken);

      switch (shape) {
        case "point" -> {
          return ctx -> {
            Location loc = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (loc.getWorld() == null) {
              return;
            }
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (emitCount == 0) {
              return;
            }
            if (!consumeParticles(ctx, emitCount)) {
              return;
            }
            double off = evalDouble(offset, ctx);
            double ex = evalDouble(extra, ctx);
            Object resolved = resolveParticleData(data, ctx, loc);
            ctx.engine().particles().emit(loc.getWorld(), loc, particle, emitCount, off, off, off, ex, resolved);
          };
        }
        case "physics" -> {
          NumValue velocityX = numAttr(attrs, "velocityX", 0.0, stmtToken);
          NumValue velocityY = numAttr(attrs, "velocityY", 0.2, stmtToken);
          NumValue velocityZ = numAttr(attrs, "velocityZ", 0.0, stmtToken);
          NumValue spread = numAttr(attrs, "spread", 0.08, stmtToken);
          NumValue gravity = numAttr(attrs, "gravity", 0.03, stmtToken);
          NumValue drag = numAttr(attrs, "drag", 0.02, stmtToken);
          NumValue steps = numAttr(attrs, "steps", 20.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          boolean collide = boolAttr(attrs, "collide", false, stmtToken);
          String collisionModeRaw = stringAttr(attrs, "collisionMode", "STOP", stmtToken);
          dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
          try {
            collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException ex) {
            throw error(stmtToken, "invalid collisionMode=" + collisionModeRaw);
          }
          NumValue restitution = numAttr(attrs, "restitution", 0.0, stmtToken);
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            int totalSteps = Math.max(0, evalInt(steps, ctx));
            long period = evalLong(periodTicks, ctx);
            if (emitCount == 0 || totalSteps == 0 || period <= 0) {
              return;
            }
            long total = (long) emitCount * totalSteps;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
            Actions.particlesPhysics(
                particle,
                emitCount,
                vel,
                evalDouble(spread, exec),
                evalDouble(gravity, exec),
                evalDouble(drag, exec),
                totalSteps,
                period,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                collide,
                collisionMode,
                evalDouble(restitution, exec)).execute(exec);
          };
        }
        case "physics_points" -> {
          NumValue velocityX = numAttr(attrs, "velocityX", 0.0, stmtToken);
          NumValue velocityY = numAttr(attrs, "velocityY", 0.2, stmtToken);
          NumValue velocityZ = numAttr(attrs, "velocityZ", 0.0, stmtToken);
          NumValue spread = numAttr(attrs, "spread", 0.08, stmtToken);
          NumValue gravity = numAttr(attrs, "gravity", 0.03, stmtToken);
          NumValue drag = numAttr(attrs, "drag", 0.02, stmtToken);
          NumValue steps = numAttr(attrs, "steps", 20.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          boolean collide = boolAttr(attrs, "collide", false, stmtToken);
          String collisionModeRaw = stringAttr(attrs, "collisionMode", "STOP", stmtToken);
          dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
          try {
            collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException ex) {
            throw error(stmtToken, "invalid collisionMode=" + collisionModeRaw);
          }
          NumValue restitution = numAttr(attrs, "restitution", 0.0, stmtToken);
          String shapeId = attrs.containsKey("shape") ? stringAttr(attrs, "shape", null, stmtToken) : null;
          java.util.List<PointSpec> points;
          if (shapeId != null) {
            ShapeTemplate template = shapeTemplates.get(shapeId);
            if (template == null || template.points().isEmpty()) {
              throw error(stmtToken, "unknown or empty shape=" + shapeId);
            }
            points = template.points();
          } else {
            points = splinePointsFromAttrs(attrs, stmtToken);
            if (points.isEmpty()) {
              throw error(stmtToken, "particles.physics_points requires p0_*/p1_* (and more) point attributes");
            }
          }
          var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
          for (PointSpec spec : points) {
            fns.add(ctx -> pointAt(ctx, spec));
          }
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            int totalSteps = Math.max(0, evalInt(steps, ctx));
            long period = evalLong(periodTicks, ctx);
            if (emitCount == 0 || totalSteps == 0 || period <= 0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
            Actions.particlesPhysicsPoints(
                particle,
                fns,
                emitCount,
                vel,
                evalDouble(spread, exec),
                evalDouble(gravity, exec),
                evalDouble(drag, exec),
                totalSteps,
                period,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                collide,
                collisionMode,
                evalDouble(restitution, exec)).execute(exec);
          };
        }
        case "physics_polyline" -> {
          NumValue step = numAttr(attrs, "step", 0.5, stmtToken);
          NumValue velocityX = numAttr(attrs, "velocityX", 0.0, stmtToken);
          NumValue velocityY = numAttr(attrs, "velocityY", 0.2, stmtToken);
          NumValue velocityZ = numAttr(attrs, "velocityZ", 0.0, stmtToken);
          NumValue spread = numAttr(attrs, "spread", 0.08, stmtToken);
          NumValue gravity = numAttr(attrs, "gravity", 0.03, stmtToken);
          NumValue drag = numAttr(attrs, "drag", 0.02, stmtToken);
          NumValue steps = numAttr(attrs, "steps", 20.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          boolean collide = boolAttr(attrs, "collide", false, stmtToken);
          String collisionModeRaw = stringAttr(attrs, "collisionMode", "STOP", stmtToken);
          dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
          try {
            collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException ex) {
            throw error(stmtToken, "invalid collisionMode=" + collisionModeRaw);
          }
          NumValue restitution = numAttr(attrs, "restitution", 0.0, stmtToken);
          String shapeId = attrs.containsKey("shape") ? stringAttr(attrs, "shape", null, stmtToken) : null;
          java.util.List<PointSpec> points;
          if (shapeId != null) {
            ShapeTemplate template = shapeTemplates.get(shapeId);
            if (template == null || template.points().size() < 2) {
              throw error(stmtToken, "unknown or insufficient shape=" + shapeId);
            }
            points = template.points();
          } else {
            points = splinePointsFromAttrs(attrs, stmtToken);
            if (points.size() < 2) {
              throw error(stmtToken, "particles.physics_polyline requires p0_*/p1_* (and more) point attributes");
            }
          }
          var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
          for (PointSpec spec : points) {
            fns.add(ctx -> pointAt(ctx, spec));
          }
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            int totalSteps = Math.max(0, evalInt(steps, ctx));
            long period = evalLong(periodTicks, ctx);
            double stepValue = evalDouble(step, ctx);
            if (emitCount == 0 || totalSteps == 0 || period <= 0 || stepValue <= 0.0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
            Actions.particlesPhysicsPolyline(
                particle,
                fns,
                stepValue,
                emitCount,
                vel,
                evalDouble(spread, exec),
                evalDouble(gravity, exec),
                evalDouble(drag, exec),
                totalSteps,
                period,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                collide,
                collisionMode,
                evalDouble(restitution, exec)).execute(exec);
          };
        }
        case "physics_mesh" -> {
          NumValue step = numAttr(attrs, "step", 0.75, stmtToken);
          NumValue velocityX = numAttr(attrs, "velocityX", 0.0, stmtToken);
          NumValue velocityY = numAttr(attrs, "velocityY", 0.2, stmtToken);
          NumValue velocityZ = numAttr(attrs, "velocityZ", 0.0, stmtToken);
          NumValue spread = numAttr(attrs, "spread", 0.08, stmtToken);
          NumValue gravity = numAttr(attrs, "gravity", 0.03, stmtToken);
          NumValue drag = numAttr(attrs, "drag", 0.02, stmtToken);
          NumValue steps = numAttr(attrs, "steps", 20.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          boolean collide = boolAttr(attrs, "collide", false, stmtToken);
          String collisionModeRaw = stringAttr(attrs, "collisionMode", "STOP", stmtToken);
          dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode collisionMode;
          try {
            collisionMode = dev.patric.dungeonsreborn.effects.particles.ParticlePhysics.CollisionMode.valueOf(collisionModeRaw.toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException ex) {
            throw error(stmtToken, "invalid collisionMode=" + collisionModeRaw);
          }
          NumValue restitution = numAttr(attrs, "restitution", 0.0, stmtToken);
          String shapeId = attrs.containsKey("shape") ? stringAttr(attrs, "shape", null, stmtToken) : null;
          java.util.List<List<PointSpec>> triangles;
          if (shapeId != null) {
            ShapeTemplate template = shapeTemplates.get(shapeId);
            if (template == null || template.triangles().isEmpty()) {
              throw error(stmtToken, "unknown or empty triangle shape=" + shapeId);
            }
            triangles = template.triangles();
          } else {
            throw error(stmtToken, "particles.physics_mesh requires shape=<id> with triangles");
          }
          var fns = new ArrayList<java.util.function.Function<CastContext, Location[]>>(triangles.size());
          for (List<PointSpec> tri : triangles) {
            if (tri.size() < 3) {
              continue;
            }
            PointSpec a = tri.get(0);
            PointSpec b = tri.get(1);
            PointSpec c = tri.get(2);
            fns.add(ctx -> new Location[] { pointAt(ctx, a), pointAt(ctx, b), pointAt(ctx, c) });
          }
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            int totalSteps = Math.max(0, evalInt(steps, ctx));
            long period = evalLong(periodTicks, ctx);
            double stepValue = evalDouble(step, ctx);
            if (emitCount == 0 || totalSteps == 0 || period <= 0 || stepValue <= 0.0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Vector vel = new Vector(evalDouble(velocityX, exec), evalDouble(velocityY, exec), evalDouble(velocityZ, exec));
            Actions.particlesPhysicsMesh(
                particle,
                fns,
                stepValue,
                emitCount,
                vel,
                evalDouble(spread, exec),
                evalDouble(gravity, exec),
                evalDouble(drag, exec),
                totalSteps,
                period,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                collide,
                collisionMode,
                evalDouble(restitution, exec)).execute(exec);
          };
        }
        case "ring" -> {
          NumValue radius = numAttr(attrs, "radius", 1.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 24.0, stmtToken);
          return ctx -> {
            Location center = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (center.getWorld() == null) {
              return;
            }
            var pe = ctx.engine().particles();
            double r = evalDouble(radius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            double off = evalDouble(offset, ctx);
            double ex = evalDouble(extra, ctx);
            dev.patric.dungeonsreborn.effects.particles.ParticleShapes.ring(center, new Vector(0, 1, 0), r, pts,
                loc -> {
                  Object resolved = resolveParticleData(data, ctx, loc);
                  pe.emit(center.getWorld(), loc, particle, emitCount, off, off, off, ex, resolved);
                });
          };
        }
        case "morph_ring", "gradient_ring" -> {
          boolean gradient = "gradient_ring".equals(shape);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 1.0, stmtToken)
              : numAttr(attrs, "radius", 1.0, stmtToken);
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 1.0, stmtToken)
              : numAttr(attrs, "radius", 1.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue points = numAttr(attrs, "points", 32.0, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (start < 0.0 || end < 0.0 || pts == 0 || emitCount == 0 || (!gradient && (duration <= 0 || period <= 0))) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if (gradient) {
              Actions.presetGradientRing(
                  particle,
                  start,
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphRing(
                  particle,
                  start,
                  end,
                  duration,
                  period,
                  easingFromId(easingId),
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_line", "gradient_line" -> {
          NumValue baseLength = numAttr(attrs, "length", 6.0, stmtToken);
          NumValue startLength = attrs.containsKey("startLength")
              ? numAttr(attrs, "startLength", 0.0, stmtToken)
              : baseLength;
          NumValue endLength = attrs.containsKey("endLength")
              ? numAttr(attrs, "endLength", 6.0, stmtToken)
              : baseLength;
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue step = numAttr(attrs, "step", 0.3, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          final AtMode targetAt = attrs.containsKey("targetAt")
              ? parseAt(stringAttr(attrs, "targetAt", "origin", stmtToken), pathAt(stmtToken) + ".targetAt")
              : null;
          return ctx -> {
            double base = evalDouble(baseLength, ctx);
            double start = evalDouble(startLength, ctx);
            double end = evalDouble(endLength, ctx);
            double stepValue = evalDouble(step, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || stepValue <= 0.0 || emitCount == 0) {
              return;
            }
            if ("morph_line".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if (targetAt != null) {
              Location target = resolveAt(ctx, targetAt);
              if (target.getWorld() == null || !target.getWorld().equals(origin.getWorld())) {
                return;
              }
              Vector direction = target.toVector().subtract(origin.toVector());
              if (direction.lengthSquared() < 1e-9) {
                return;
              }
              direction.normalize();
              exec = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  direction,
                  ctx.itemInHand());
            }
            if ("gradient_line".equals(shape)) {
              Actions.presetGradientLine(
                  particle,
                  base,
                  stepValue,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphLine(
                  particle,
                  start,
                  end,
                  duration,
                  period,
                  easingFromId(easingId),
                  stepValue,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_arc", "gradient_arc" -> {
          NumValue baseRadius = numAttr(attrs, "radius", 1.2, stmtToken);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 1.2, stmtToken)
              : baseRadius;
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 1.2, stmtToken)
              : baseRadius;
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 90.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue points = numAttr(attrs, "points", 24.0, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseRadius, ctx);
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || angle <= 0.0 || angle > 360.0 || pts == 0 || emitCount == 0) {
              return;
            }
            if ("morph_arc".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_arc".equals(shape)) {
              Actions.presetGradientArc(
                  particle,
                  base,
                  angle,
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphArc(
                  particle,
                  start,
                  end,
                  angle,
                  duration,
                  period,
                  easingFromId(easingId),
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_disk", "gradient_disk" -> {
          NumValue baseRadius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 0.5, stmtToken)
              : baseRadius;
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 2.0, stmtToken)
              : baseRadius;
          NumValue rings = numAttr(attrs, "rings", 6.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 24.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseRadius, ctx);
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int perRing = Math.max(0, evalInt(pointsPerRing, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || ringCount == 0 || perRing == 0 || emitCount == 0) {
              return;
            }
            if ("morph_disk".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_disk".equals(shape)) {
              Actions.presetGradientDisk(
                  particle,
                  base,
                  ringCount,
                  perRing,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphDisk(
                  particle,
                  start,
                  end,
                  ringCount,
                  perRing,
                  duration,
                  period,
                  easingFromId(easingId),
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_sphere_shell", "gradient_sphere_shell" -> {
          NumValue baseRadius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 1.0, stmtToken)
              : baseRadius;
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 2.0, stmtToken)
              : baseRadius;
          NumValue points = numAttr(attrs, "points", 90.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseRadius, ctx);
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            if ("morph_sphere_shell".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_sphere_shell".equals(shape)) {
              Actions.presetGradientSphereShell(
                  particle,
                  base,
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphSphereShell(
                  particle,
                  start,
                  end,
                  duration,
                  period,
                  easingFromId(easingId),
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_sphere_filled", "gradient_sphere_filled" -> {
          NumValue baseRadius = numAttr(attrs, "radius", 1.6, stmtToken);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 0.6, stmtToken)
              : baseRadius;
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 1.6, stmtToken)
              : baseRadius;
          NumValue points = numAttr(attrs, "points", 120.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseRadius, ctx);
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            if ("morph_sphere_filled".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_sphere_filled".equals(shape)) {
              Actions.presetGradientSphereFilled(
                  particle,
                  base,
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphSphereFilled(
                  particle,
                  start,
                  end,
                  duration,
                  period,
                  easingFromId(easingId),
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_helix", "gradient_helix" -> {
          NumValue baseRadius = numAttr(attrs, "radius", 1.2, stmtToken);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 0.8, stmtToken)
              : baseRadius;
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 1.2, stmtToken)
              : baseRadius;
          NumValue length = numAttr(attrs, "length", 6.0, stmtToken);
          NumValue turns = numAttr(attrs, "turns", 3.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 90.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseRadius, ctx);
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            double len = evalDouble(length, ctx);
            int t = Math.max(0, evalInt(turns, ctx));
            int pts = Math.max(0, evalInt(points, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || len < 0.0 || t == 0 || pts == 0 || emitCount == 0) {
              return;
            }
            if ("morph_helix".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_helix".equals(shape)) {
              Actions.presetGradientHelix(
                  particle,
                  base,
                  len,
                  t,
                  pts,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphHelix(
                  particle,
                  start,
                  end,
                  len,
                  t,
                  pts,
                  duration,
                  period,
                  easingFromId(easingId),
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_cone", "gradient_cone" -> {
          NumValue baseLength = numAttr(attrs, "length", 6.0, stmtToken);
          NumValue startLength = attrs.containsKey("startLength")
              ? numAttr(attrs, "startLength", 2.0, stmtToken)
              : baseLength;
          NumValue endLength = attrs.containsKey("endLength")
              ? numAttr(attrs, "endLength", 6.0, stmtToken)
              : baseLength;
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 35.0, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 5.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 28.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseLength, ctx);
            double start = evalDouble(startLength, ctx);
            double end = evalDouble(endLength, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int perRing = Math.max(0, evalInt(pointsPerRing, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || angle <= 0.0 || angle > 89.0 || ringCount == 0 || perRing == 0 || emitCount == 0) {
              return;
            }
            if ("morph_cone".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_cone".equals(shape)) {
              Actions.presetGradientCone(
                  particle,
                  base,
                  angle,
                  ringCount,
                  perRing,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphCone(
                  particle,
                  start,
                  end,
                  angle,
                  ringCount,
                  perRing,
                  duration,
                  period,
                  easingFromId(easingId),
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_cylinder", "gradient_cylinder" -> {
          NumValue baseRadius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 1.0, stmtToken)
              : baseRadius;
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 2.0, stmtToken)
              : baseRadius;
          NumValue height = numAttr(attrs, "height", 4.0, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 6.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 28.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseRadius, ctx);
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            double h = evalDouble(height, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int perRing = Math.max(0, evalInt(pointsPerRing, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || h < 0.0 || ringCount == 0 || perRing == 0 || emitCount == 0) {
              return;
            }
            if ("morph_cylinder".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_cylinder".equals(shape)) {
              Actions.presetGradientCylinder(
                  particle,
                  base,
                  h,
                  ringCount,
                  perRing,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphCylinder(
                  particle,
                  start,
                  end,
                  h,
                  ringCount,
                  perRing,
                  duration,
                  period,
                  easingFromId(easingId),
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_box", "gradient_box" -> {
          NumValue baseX = numAttr(attrs, "xRadius", 2.0, stmtToken);
          NumValue baseY = numAttr(attrs, "yRadius", 2.0, stmtToken);
          NumValue baseZ = numAttr(attrs, "zRadius", 2.0, stmtToken);
          NumValue startX = attrs.containsKey("startX")
              ? numAttr(attrs, "startX", 1.0, stmtToken)
              : baseX;
          NumValue startY = attrs.containsKey("startY")
              ? numAttr(attrs, "startY", 1.0, stmtToken)
              : baseY;
          NumValue startZ = attrs.containsKey("startZ")
              ? numAttr(attrs, "startZ", 1.0, stmtToken)
              : baseZ;
          NumValue endX = attrs.containsKey("endX")
              ? numAttr(attrs, "endX", 2.0, stmtToken)
              : baseX;
          NumValue endY = attrs.containsKey("endY")
              ? numAttr(attrs, "endY", 2.0, stmtToken)
              : baseY;
          NumValue endZ = attrs.containsKey("endZ")
              ? numAttr(attrs, "endZ", 2.0, stmtToken)
              : baseZ;
          NumValue step = numAttr(attrs, "step", 0.5, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double bx = evalDouble(baseX, ctx);
            double by = evalDouble(baseY, ctx);
            double bz = evalDouble(baseZ, ctx);
            double sx = evalDouble(startX, ctx);
            double sy = evalDouble(startY, ctx);
            double sz = evalDouble(startZ, ctx);
            double ex = evalDouble(endX, ctx);
            double ey = evalDouble(endY, ctx);
            double ez = evalDouble(endZ, ctx);
            double st = evalDouble(step, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (st <= 0.0 || emitCount == 0) {
              return;
            }
            if ("morph_box".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_box".equals(shape)) {
              Actions.presetGradientBox(
                  particle,
                  bx,
                  by,
                  bz,
                  st,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphBox(
                  particle,
                  sx,
                  sy,
                  sz,
                  ex,
                  ey,
                  ez,
                  st,
                  duration,
                  period,
                  easingFromId(easingId),
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "morph_polygon", "gradient_polygon" -> {
          NumValue baseRadius = numAttr(attrs, "radius", 1.6, stmtToken);
          NumValue startRadius = attrs.containsKey("startRadius")
              ? numAttr(attrs, "startRadius", 1.0, stmtToken)
              : baseRadius;
          NumValue endRadius = attrs.containsKey("endRadius")
              ? numAttr(attrs, "endRadius", 1.6, stmtToken)
              : baseRadius;
          NumValue sides = numAttr(attrs, "sides", 6.0, stmtToken);
          NumValue pointsPerEdge = numAttr(attrs, "pointsPerEdge", 5.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          return ctx -> {
            double base = evalDouble(baseRadius, ctx);
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            int s = Math.max(0, evalInt(sides, ctx));
            int ppe = Math.max(0, evalInt(pointsPerEdge, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (base < 0.0 || start < 0.0 || end < 0.0 || s <= 2 || ppe == 0 || emitCount == 0) {
              return;
            }
            if ("morph_polygon".equals(shape) && (duration <= 0 || period <= 0)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if ("gradient_polygon".equals(shape)) {
              Actions.presetGradientPolygon(
                  particle,
                  base,
                  s,
                  ppe,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.presetMorphPolygon(
                  particle,
                  start,
                  end,
                  s,
                  ppe,
                  duration,
                  period,
                  easingFromId(easingId),
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            }
          };
        }
        case "gradient_bezier" -> {
          NumValue pointsPerMeter = numAttr(attrs, "pointsPerMeter", 6.0, stmtToken);
          NumValue maxPoints = numAttr(attrs, "maxPoints", 180.0, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          PointSpec p0 = pointSpecFromAttrs(attrs, "p0", stmtToken, true);
          PointSpec p1 = pointSpecFromAttrs(attrs, "p1", stmtToken, true);
          PointSpec p2 = pointSpecFromAttrs(attrs, "p2", stmtToken, true);
          PointSpec p3 = pointSpecFromAttrs(attrs, "p3", stmtToken, true);
          return ctx -> {
            double ppm = evalDouble(pointsPerMeter, ctx);
            int maxPts = Math.max(0, evalInt(maxPoints, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (ppm <= 0.0 || maxPts == 0 || emitCount == 0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.presetGradientBezier(
                c -> pointAt(c, p0),
                c -> pointAt(c, p1),
                c -> pointAt(c, p2),
                c -> pointAt(c, p3),
                ppm,
                maxPts,
                particle,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          };
        }
        case "gradient_spline" -> {
          NumValue pointsPerMeter = numAttr(attrs, "pointsPerMeter", 10.0, stmtToken);
          NumValue maxPoints = numAttr(attrs, "maxPoints", 320.0, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          java.util.List<PointSpec> points = splinePointsFromAttrs(attrs, stmtToken);
          return ctx -> {
            double ppm = evalDouble(pointsPerMeter, ctx);
            int maxPts = Math.max(0, evalInt(maxPoints, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (ppm <= 0.0 || maxPts == 0 || emitCount == 0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
            for (PointSpec point : points) {
              fns.add(c -> pointAt(c, point));
            }
            Actions.presetGradientSpline(
                fns,
                ppm,
                maxPts,
                particle,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data,
                from,
                to,
                dustSize).execute(exec);
          };
        }
        case "line" -> {
          NumValue length = numAttr(attrs, "length", 10.0, stmtToken);
          NumValue step = numAttr(attrs, "step", 0.35, stmtToken);
          final AtMode targetAt = attrs.containsKey("targetAt")
              ? parseAt(stringAttr(attrs, "targetAt", "origin", stmtToken), pathAt(stmtToken) + ".targetAt")
              : null;
          return ctx -> {
            double len = evalDouble(length, ctx);
            double st = evalDouble(step, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (len <= 0.0 || st <= 0.0 || emitCount == 0) {
              return;
            }
            int pts = (int) Math.floor(len / st) + 1;
            long total = (long) Math.max(0, pts) * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if (targetAt != null) {
              Location target = resolveAt(ctx, targetAt);
              if (target.getWorld() == null || !target.getWorld().equals(origin.getWorld())) {
                return;
              }
              Vector direction = target.toVector().subtract(origin.toVector());
              if (direction.lengthSquared() < 1e-9) {
                return;
              }
              direction.normalize();
              exec = new CastContext(
                  ctx.engine(),
                  ctx.plugin(),
                  ctx.castId(),
                  ctx.abilityId(),
                  ctx.tick(),
                  ctx.state(),
                  ctx.caster(),
                  origin.clone(),
                  direction,
                  ctx.itemInHand());
            }
            Actions.particlesLine(particle, len, st, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "arc" -> {
          NumValue radius = numAttr(attrs, "radius", 1.2, stmtToken);
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 90.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 24.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || angle <= 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesArc(particle, r, angle, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "disk" -> {
          NumValue radius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 6.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 42.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int ppr = Math.max(0, evalInt(pointsPerRing, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || ringCount == 0 || ppr == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = 1;
            for (int i = 1; i <= ringCount; i++) {
              int ringPts = Math.max(6, (int) Math.round(ppr * (i / (double) ringCount)));
              totalPoints += ringPts;
            }
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesDisk(particle, r, ringCount, ppr, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "sphere_shell", "sphere-shell", "sphere" -> {
          NumValue radius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 120.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesSphereShell(particle, r, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "sphere_filled", "sphere-filled", "sphere_fill" -> {
          NumValue radius = numAttr(attrs, "radius", 2.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 160.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesSphereFilled(particle, r, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "helix" -> {
          NumValue radius = numAttr(attrs, "radius", 1.2, stmtToken);
          NumValue length = numAttr(attrs, "length", 6.0, stmtToken);
          NumValue turns = numAttr(attrs, "turns", 3.0, stmtToken);
          NumValue points = numAttr(attrs, "points", 80.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double len = evalDouble(length, ctx);
            int t = Math.max(0, evalInt(turns, ctx));
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || len < 0.0 || t == 0 || pts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) pts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesHelix(particle, r, len, t, pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "bezier" -> {
          NumValue pointsPerMeter = numAttr(attrs, "pointsPerMeter", 6.0, stmtToken);
          NumValue maxPoints = numAttr(attrs, "maxPoints", 180.0, stmtToken);
          PointSpec p0 = pointSpecFromAttrs(attrs, "p0", stmtToken, true);
          PointSpec p1 = pointSpecFromAttrs(attrs, "p1", stmtToken, true);
          PointSpec p2 = pointSpecFromAttrs(attrs, "p2", stmtToken, true);
          PointSpec p3 = pointSpecFromAttrs(attrs, "p3", stmtToken, true);
          return ctx -> {
            double ppm = evalDouble(pointsPerMeter, ctx);
            int maxPts = Math.max(0, evalInt(maxPoints, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (ppm <= 0.0 || maxPts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) maxPts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesBezier(
                c -> pointAt(c, p0),
                c -> pointAt(c, p1),
                c -> pointAt(c, p2),
                c -> pointAt(c, p3),
                ppm,
                maxPts,
                particle,
                emitCount,
                evalDouble(offset, exec),
                evalDouble(extra, exec),
                data).execute(exec);
          };
        }
        case "spline" -> {
          NumValue pointsPerMeter = numAttr(attrs, "pointsPerMeter", 10.0, stmtToken);
          NumValue maxPoints = numAttr(attrs, "maxPoints", 320.0, stmtToken);
          java.util.List<PointSpec> points = splinePointsFromAttrs(attrs, stmtToken);
          var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
          for (PointSpec spec : points) {
            fns.add(ctx -> pointAt(ctx, spec));
          }
          return ctx -> {
            double ppm = evalDouble(pointsPerMeter, ctx);
            int maxPts = Math.max(0, evalInt(maxPoints, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (ppm <= 0.0 || maxPts == 0 || emitCount == 0) {
              return;
            }
            long total = (long) maxPts * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesSpline(fns, ppm, maxPts, particle, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
                .execute(exec);
          };
        }
        case "spline_motion" -> {
          NumValue pointsPerMeter = numAttr(attrs, "pointsPerMeter", 10.0, stmtToken);
          NumValue maxPoints = numAttr(attrs, "maxPoints", 320.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          java.util.List<PointSpec> points = splinePointsFromAttrs(attrs, stmtToken);
          var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
          for (PointSpec spec : points) {
            fns.add(ctx -> pointAt(ctx, spec));
          }
          return ctx -> {
            double ppm = evalDouble(pointsPerMeter, ctx);
            int maxPts = Math.max(0, evalInt(maxPoints, ctx));
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (ppm <= 0.0 || maxPts == 0 || emitCount == 0 || duration <= 0 || period <= 0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.presetSplineMotion(fns, ppm, maxPts, duration, period, easingFromId(easingId), particle, emitCount,
                evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "cone" -> {
          NumValue length = numAttr(attrs, "length", 8.0, stmtToken);
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 70.0, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 10.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 18.0, stmtToken);
          return ctx -> {
            double len = evalDouble(length, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int ppr = Math.max(0, evalInt(pointsPerRing, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (len <= 0.0 || angle <= 0.0 || ringCount == 0 || ppr == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = 1L + (long) ringCount * ppr;
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesCone(particle, len, angle, ringCount, ppr, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
                .execute(exec);
          };
        }
        case "cylinder" -> {
          NumValue radius = numAttr(attrs, "radius", 2.4, stmtToken);
          NumValue height = numAttr(attrs, "height", 3.2, stmtToken);
          NumValue rings = numAttr(attrs, "rings", 10.0, stmtToken);
          NumValue pointsPerRing = numAttr(attrs, "pointsPerRing", 24.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double h = evalDouble(height, ctx);
            int ringCount = Math.max(0, evalInt(rings, ctx));
            int ppr = Math.max(0, evalInt(pointsPerRing, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || h < 0.0 || ringCount == 0 || ppr == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = (long) (ringCount + 1) * ppr;
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesCylinder(particle, r, h, ringCount, ppr, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data)
                .execute(exec);
          };
        }
        case "box" -> {
          NumValue xRadius = numAttr(attrs, "xRadius", 2.2, stmtToken);
          NumValue yRadius = numAttr(attrs, "yRadius", 1.6, stmtToken);
          NumValue zRadius = numAttr(attrs, "zRadius", 2.2, stmtToken);
          NumValue step = numAttr(attrs, "step", 0.35, stmtToken);
          return ctx -> {
            double xr = evalDouble(xRadius, ctx);
            double yr = evalDouble(yRadius, ctx);
            double zr = evalDouble(zRadius, ctx);
            double st = evalDouble(step, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (xr < 0.0 || yr < 0.0 || zr < 0.0 || st <= 0.0 || emitCount == 0) {
              return;
            }
            long px = (long) Math.ceil((2.0 * xr) / st) + 1L;
            long py = (long) Math.ceil((2.0 * yr) / st) + 1L;
            long pz = (long) Math.ceil((2.0 * zr) / st) + 1L;
            long totalPoints = 4L * (px + py + pz);
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesBox(particle, xr, yr, zr, st, emitCount, evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "polygon" -> {
          NumValue radius = numAttr(attrs, "radius", 2.5, stmtToken);
          NumValue sides = numAttr(attrs, "sides", 6.0, stmtToken);
          NumValue pointsPerEdge = numAttr(attrs, "pointsPerEdge", 10.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            int s = Math.max(0, evalInt(sides, ctx));
            int ppe = Math.max(0, evalInt(pointsPerEdge, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || s == 0 || ppe == 0 || emitCount == 0) {
              return;
            }
            long totalPoints = (long) s * (ppe + 1L);
            long total = totalPoints * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.particlesPolygon(particle, new Vector(0, 1, 0), r, s, ppe, emitCount, evalDouble(offset, exec), evalDouble(extra, exec),
                data)
                .execute(exec);
          };
        }
        case "orbit", "orbiting_runes", "orbiting-runes" -> {
          boolean runes = "orbiting_runes".equals(shape) || "orbiting-runes".equals(shape);
          NumValue radius = numAttr(attrs, "radius", runes ? 2.6 : 2.4, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", runes ? 80.0 : 60.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", runes ? 2.0 : 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.LINEAR.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue copies = numAttr(attrs, "copies", runes ? 6.0 : 3.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int c = Math.max(0, evalInt(copies, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || duration <= 0 || period <= 0 || c == 0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) c * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.presetOrbit(particle, r, duration, period, easingFromId(easingId), c, emitCount, evalDouble(offset, exec), evalDouble(extra, exec),
                data)
                .execute(exec);
          };
        }
        case "swirl", "spiral_aura", "spiral-aura" -> {
          boolean aura = "spiral_aura".equals(shape) || "spiral-aura".equals(shape);
          NumValue radius = numAttr(attrs, "radius", aura ? 1.8 : 1.8, stmtToken);
          NumValue height = numAttr(attrs, "height", aura ? 3.5 : 2.6, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", aura ? 80.0 : 60.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", aura ? 2.0 : 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue points = numAttr(attrs, "points", aura ? 28.0 : 22.0, stmtToken);
          return ctx -> {
            double r = evalDouble(radius, ctx);
            double h = evalDouble(height, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (r < 0.0 || h < 0.0 || duration <= 0 || period <= 0 || pts == 0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) pts * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.presetSwirl(particle, r, h, duration, period, easingFromId(easingId), pts, emitCount, evalDouble(offset, exec), evalDouble(extra, exec),
                data)
                .execute(exec);
          };
        }
        case "shockwave" -> {
          NumValue startRadius = numAttr(attrs, "startRadius", 0.5, stmtToken);
          NumValue endRadius = numAttr(attrs, "endRadius", 7.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 40.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.OUT_QUAD.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue points = numAttr(attrs, "points", 56.0, stmtToken);
          return ctx -> {
            double start = evalDouble(startRadius, ctx);
            double end = evalDouble(endRadius, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            int pts = Math.max(0, evalInt(points, ctx));
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || pts == 0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) pts * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.presetShockwave(particle, start, end, duration, period, easingFromId(easingId), pts, emitCount,
                evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "beam_chargeup", "beam-chargeup" -> {
          NumValue startLength = numAttr(attrs, "startLength", 0.0, stmtToken);
          NumValue endLength = attrs.containsKey("endLength")
              ? numAttr(attrs, "endLength", 10.0, stmtToken)
              : numAttr(attrs, "length", 10.0, stmtToken);
          NumValue durationTicks = numAttr(attrs, "durationTicks", 20.0, stmtToken);
          NumValue periodTicks = numAttr(attrs, "periodTicks", 1.0, stmtToken);
          String easingRaw = stringAttr(attrs, "easing", EasingId.IN_OUT_CUBIC.name(), stmtToken);
          EasingId easingId = parseEasing(easingRaw, stmtToken);
          NumValue step = numAttr(attrs, "step", 0.35, stmtToken);
          return ctx -> {
            double start = evalDouble(startLength, ctx);
            double end = evalDouble(endLength, ctx);
            long duration = evalLong(durationTicks, ctx);
            long period = evalLong(periodTicks, ctx);
            double st = evalDouble(step, ctx);
            int emitCount = Math.max(0, evalInt(count, ctx));
            if (start < 0.0 || end < 0.0 || duration <= 0 || period <= 0 || st <= 0.0 || emitCount == 0) {
              return;
            }
            long ticks = (long) Math.ceil(duration / (double) period);
            long perTick = (long) Math.ceil(Math.max(start, end) / st) * emitCount;
            long total = ticks * perTick;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            Actions.presetBeamChargeup(particle, start, end, duration, period, easingFromId(easingId), st, emitCount,
                evalDouble(offset, exec), evalDouble(extra, exec), data).execute(exec);
          };
        }
        case "points" -> {
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          TransformSpec transform = transformSpecFromAttrs(attrs, stmtToken);
          String shapeId = attrs.containsKey("shape") ? stringAttr(attrs, "shape", null, stmtToken) : null;
          java.util.List<PointSpec> points;
          if (shapeId != null) {
            ShapeTemplate template = shapeTemplates.get(shapeId);
            if (template == null || template.points().isEmpty()) {
              throw error(stmtToken, "unknown or empty shape=" + shapeId);
            }
            points = template.points();
          } else {
            points = splinePointsFromAttrs(attrs, stmtToken);
            if (points.isEmpty()) {
              throw error(stmtToken, "particles.points requires p0_*/p1_* (and more) point attributes");
            }
          }
          var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
          for (PointSpec spec : points) {
            fns.add(ctx -> applyTransform(ctx, pointAt(ctx, spec), transform));
          }
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            float dustSize = (float) evalDouble(size, ctx);
            if (emitCount == 0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if (from != null && to != null) {
              Actions.particlesPointsGradient(
                  particle,
                  fns,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.particlesPoints(
                  particle,
                  fns,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data).execute(exec);
            }
          };
        }
        case "polyline" -> {
          NumValue step = numAttr(attrs, "step", 0.5, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          TransformSpec transform = transformSpecFromAttrs(attrs, stmtToken);
          String shapeId = attrs.containsKey("shape") ? stringAttr(attrs, "shape", null, stmtToken) : null;
          java.util.List<PointSpec> points;
          if (shapeId != null) {
            ShapeTemplate template = shapeTemplates.get(shapeId);
            if (template == null || template.points().size() < 2) {
              throw error(stmtToken, "unknown or insufficient shape=" + shapeId);
            }
            points = template.points();
          } else {
            points = splinePointsFromAttrs(attrs, stmtToken);
            if (points.size() < 2) {
              throw error(stmtToken, "particles.polyline requires p0_*/p1_* (and more) point attributes");
            }
          }
          var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points.size());
          for (PointSpec spec : points) {
            fns.add(ctx -> applyTransform(ctx, pointAt(ctx, spec), transform));
          }
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            double stepValue = evalDouble(step, ctx);
            float dustSize = (float) evalDouble(size, ctx);
            if (emitCount == 0 || stepValue <= 0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if (from != null && to != null) {
              Actions.particlesPolylineGradient(
                  particle,
                  fns,
                  stepValue,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.particlesPolyline(
                  particle,
                  fns,
                  stepValue,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data).execute(exec);
            }
          };
        }
        case "mesh" -> {
          NumValue step = numAttr(attrs, "step", 0.75, stmtToken);
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          TransformSpec transform = transformSpecFromAttrs(attrs, stmtToken);
          String shapeId = attrs.containsKey("shape") ? stringAttr(attrs, "shape", null, stmtToken) : null;
          java.util.List<List<PointSpec>> triangles;
          if (shapeId != null) {
            ShapeTemplate template = shapeTemplates.get(shapeId);
            if (template == null || template.triangles().isEmpty()) {
              throw error(stmtToken, "unknown or empty triangle shape=" + shapeId);
            }
            triangles = template.triangles();
          } else {
            throw error(stmtToken, "particles.mesh requires shape=<id> with triangles");
          }
          var fns = new ArrayList<java.util.function.Function<CastContext, Location[]>>(triangles.size());
          for (List<PointSpec> tri : triangles) {
            if (tri.size() < 3) {
              continue;
            }
            PointSpec a = tri.get(0);
            PointSpec b = tri.get(1);
            PointSpec c = tri.get(2);
            fns.add(ctx -> new Location[] {
                applyTransform(ctx, pointAt(ctx, a), transform),
                applyTransform(ctx, pointAt(ctx, b), transform),
                applyTransform(ctx, pointAt(ctx, c), transform)
            });
          }
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            double stepValue = evalDouble(step, ctx);
            float dustSize = (float) evalDouble(size, ctx);
            if (emitCount == 0 || stepValue <= 0) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            if (from != null && to != null) {
              Actions.particlesMeshGradient(
                  particle,
                  fns,
                  stepValue,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.particlesMesh(
                  particle,
                  fns,
                  stepValue,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data).execute(exec);
            }
          };
        }
        case "parametric" -> {
          NumValue pointsValue = numAttr(attrs, "points", 64.0, stmtToken);
          NumValue tMin = numAttr(attrs, "tMin", 0.0, stmtToken);
          NumValue tMax = numAttr(attrs, "tMax", 1.0, stmtToken);
          Value xValue = requireAttr(attrs, "x", stmtToken);
          Value yValue = requireAttr(attrs, "y", stmtToken);
          Value zValue = requireAttr(attrs, "z", stmtToken);
          NumValue xExpr = numFromValue(xValue, "x", stmtToken);
          NumValue yExpr = numFromValue(yValue, "y", stmtToken);
          NumValue zExpr = numFromValue(zValue, "z", stmtToken);
          TransformSpec transform = transformSpecFromAttrs(attrs, stmtToken);
          Value startColorValue = attrs.getOrDefault("startColor", attrs.getOrDefault("from", attrs.get("color")));
          Value endColorValue = attrs.getOrDefault("endColor", attrs.getOrDefault("toColor", attrs.get("to")));
          org.bukkit.Color from = startColorValue == null ? null : parseColor(rawValue(startColorValue, "startColor", stmtToken), pathAt(stmtToken) + ".startColor");
          org.bukkit.Color to = endColorValue == null ? null : parseColor(rawValue(endColorValue, "endColor", stmtToken), pathAt(stmtToken) + ".endColor");
          NumValue size = numAttr(attrs, "size", 1.0, stmtToken);
          return ctx -> {
            int emitCount = Math.max(0, evalInt(count, ctx));
            int points = Math.max(0, evalInt(pointsValue, ctx));
            if (emitCount == 0 || points <= 0) {
              return;
            }
            long total = (long) points * emitCount;
            if (!consumeParticles(ctx, total)) {
              return;
            }
            Location origin = resolveAtWithOffsets(ctx, at, forward, right, up);
            if (origin.getWorld() == null) {
              return;
            }
            CastContext exec = new CastContext(
                ctx.engine(),
                ctx.plugin(),
                ctx.castId(),
                ctx.abilityId(),
                ctx.tick(),
                ctx.state(),
                ctx.caster(),
                origin.clone(),
                ctx.direction().clone(),
                ctx.itemInHand());
            double tStart = evalDouble(tMin, exec);
            double tEnd = evalDouble(tMax, exec);
            if (Math.abs(tEnd - tStart) < 1e-9) {
              tEnd = tStart + 1.0;
            }
            var fns = new ArrayList<java.util.function.Function<CastContext, Location>>(points);
            for (int i = 0; i < points; i++) {
              double t = points == 1 ? tStart : (tStart + (tEnd - tStart) * (i / (double) (points - 1)));
              fns.add(pointCtx -> evalParametricPoint(pointCtx, t, xExpr, yExpr, zExpr, transform));
            }
            float dustSize = (float) evalDouble(size, exec);
            if (from != null && to != null) {
              Actions.particlesPointsGradient(
                  particle,
                  fns,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data,
                  from,
                  to,
                  dustSize).execute(exec);
            } else {
              Actions.particlesPoints(
                  particle,
                  fns,
                  emitCount,
                  evalDouble(offset, exec),
                  evalDouble(extra, exec),
                  data).execute(exec);
            }
          };
        }
        default -> throw error(stmtToken, "unknown particles shape: " + shape);
      }
    }

    private record TransformSpec(
        NumValue scale,
        NumValue rotateYaw,
        NumValue rotatePitch,
        NumValue rotateRoll,
        NumValue offsetX,
        NumValue offsetY,
        NumValue offsetZ) {
    }

    private TransformSpec transformSpecFromAttrs(Map<String, Value> attrs, Token stmtToken) {
      boolean has =
          attrs.containsKey("scale")
              || attrs.containsKey("rotateYaw")
              || attrs.containsKey("rotatePitch")
              || attrs.containsKey("rotateRoll")
              || attrs.containsKey("rotateX")
              || attrs.containsKey("rotateY")
              || attrs.containsKey("rotateZ")
              || attrs.containsKey("offsetX")
              || attrs.containsKey("offsetY")
              || attrs.containsKey("offsetZ");
      if (!has) {
        return null;
      }
      NumValue scale = numAttr(attrs, "scale", 1.0, stmtToken);
      NumValue rotateYaw = numAttrAlias(attrs, "rotateYaw", "rotateY", 0.0, stmtToken);
      NumValue rotatePitch = numAttrAlias(attrs, "rotatePitch", "rotateX", 0.0, stmtToken);
      NumValue rotateRoll = numAttrAlias(attrs, "rotateRoll", "rotateZ", 0.0, stmtToken);
      NumValue offsetX = numAttr(attrs, "offsetX", 0.0, stmtToken);
      NumValue offsetY = numAttr(attrs, "offsetY", 0.0, stmtToken);
      NumValue offsetZ = numAttr(attrs, "offsetZ", 0.0, stmtToken);
      return new TransformSpec(scale, rotateYaw, rotatePitch, rotateRoll, offsetX, offsetY, offsetZ);
    }

    private Location applyTransform(CastContext ctx, Location location, TransformSpec spec) {
      if (location == null || spec == null) {
        return location;
      }
      Location origin = ctx.origin();
      if (origin.getWorld() == null) {
        return location;
      }
      Vector delta = location.toVector().subtract(origin.toVector());
      double scale = evalDouble(spec.scale(), ctx);
      if (Math.abs(scale - 1.0) > 1e-9) {
        delta.multiply(scale);
      }
      double yaw = Math.toRadians(evalDouble(spec.rotateYaw(), ctx));
      double pitch = Math.toRadians(evalDouble(spec.rotatePitch(), ctx));
      double roll = Math.toRadians(evalDouble(spec.rotateRoll(), ctx));
      if (Math.abs(yaw) > 1e-9) {
        delta = dev.patric.dungeonsreborn.effects.particles.ParticleTransforms.rotateAroundAxis(delta, new Vector(0, 1, 0), yaw);
      }
      if (Math.abs(pitch) > 1e-9) {
        delta = dev.patric.dungeonsreborn.effects.particles.ParticleTransforms.rotateAroundAxis(delta, new Vector(1, 0, 0), pitch);
      }
      if (Math.abs(roll) > 1e-9) {
        delta = dev.patric.dungeonsreborn.effects.particles.ParticleTransforms.rotateAroundAxis(delta, new Vector(0, 0, 1), roll);
      }
      double ox = evalDouble(spec.offsetX(), ctx);
      double oy = evalDouble(spec.offsetY(), ctx);
      double oz = evalDouble(spec.offsetZ(), ctx);
      return origin.clone().add(delta).add(ox, oy, oz);
    }

    private Location evalParametricPoint(CastContext ctx, double t, NumValue xExpr, NumValue yExpr, NumValue zExpr, TransformSpec spec) {
      final double[] coords = new double[3];
      withTempVar(ctx, VarScope.CAST, "t", t, () -> {
        coords[0] = evalDouble(xExpr, ctx);
        coords[1] = evalDouble(yExpr, ctx);
        coords[2] = evalDouble(zExpr, ctx);
      });
      Location origin = ctx.origin();
      if (origin.getWorld() == null) {
        return null;
      }
      Location raw = origin.clone().add(coords[0], coords[1], coords[2]);
      return applyTransform(ctx, raw, spec);
    }

    private double[] offsetsFromAttrs(Map<String, Value> attrs, Token at) {
      Value rawOffsets = attrs.get("offsets");
      if (rawOffsets == null) {
        return null;
      }
      Object raw = rawValue(rawOffsets, "offsets", at);
      String text = String.valueOf(raw).trim();
      if (text.isEmpty()) {
        throw error(at, "offsets: expected 'forward,right,up' or 'forward right up'");
      }
      String[] parts = text.split("[,\\s]+");
      if (parts.length != 3) {
        throw error(at, "offsets: expected 3 values (forward,right,up)");
      }
      try {
        return new double[] {
            Double.parseDouble(parts[0]),
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2])
        };
      } catch (NumberFormatException ex) {
        throw error(at, "offsets: invalid number format");
      }
    }

    private boolean hasPointAttrs(Map<String, Value> attrs, String prefix) {
      String base = prefix + "_";
      for (String key : attrs.keySet()) {
        if (key.startsWith(base)) {
          return true;
        }
      }
      return false;
    }

    private PointSpec pointSpecFromAttrs(Map<String, Value> attrs, String prefix, Token stmtToken, boolean required) {
      if (required && !hasPointAttrs(attrs, prefix)) {
        throw error(stmtToken, "missing " + prefix + "_* attributes");
      }
      String base = prefix + "_";
      NumValue forward = numAttr(attrs, base + "forward", 0.0, stmtToken);
      NumValue right = numAttr(attrs, base + "right", 0.0, stmtToken);
      NumValue up = numAttr(attrs, base + "up", 0.0, stmtToken);
      NumValue x = numAttr(attrs, base + "x", 0.0, stmtToken);
      NumValue y = numAttr(attrs, base + "y", 0.0, stmtToken);
      NumValue z = numAttr(attrs, base + "z", 0.0, stmtToken);
      return new PointSpec(forward, right, up, x, y, z);
    }

    private java.util.List<PointSpec> splinePointsFromAttrs(Map<String, Value> attrs, Token stmtToken) {
      java.util.Set<Integer> indices = new java.util.TreeSet<>();
      for (String key : attrs.keySet()) {
        if (key.length() < 3 || key.charAt(0) != 'p' || !Character.isDigit(key.charAt(1))) {
          continue;
        }
        int idx = 1;
        while (idx < key.length() && Character.isDigit(key.charAt(idx))) {
          idx++;
        }
        if (idx >= key.length() || key.charAt(idx) != '_') {
          continue;
        }
        try {
          int value = Integer.parseInt(key.substring(1, idx));
          indices.add(value);
        } catch (NumberFormatException ignored) {
        }
      }
      if (indices.size() < 2) {
        throw error(stmtToken, "particles.spline requires p0_*/p1_* (and more) point attributes");
      }
      java.util.List<PointSpec> points = new java.util.ArrayList<>(indices.size());
      for (int index : indices) {
        points.add(pointSpecFromAttrs(attrs, "p" + index, stmtToken, true));
      }
      return points;
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseWeightedChoice(Token stmtToken) {
      consume(TokenType.LBRACE);
      record Choice(ExprNode weight, dev.patric.dungeonsreborn.effects.actions.Action action) {
      }
      List<Choice> choices = new ArrayList<>();
      while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
        ExprNode weightExpr = parseExpression();
        if (lookahead.type != TokenType.COLON) {
          throw error(stmtToken, "choice weighted: expected ':' after weight");
        }
        consume(TokenType.COLON);
        Token choiceToken = lookahead;
        dev.patric.dungeonsreborn.effects.actions.Action action = guard(parseStatement(), choiceToken);
        choices.add(new Choice(weightExpr, action));
      }
      consume(TokenType.RBRACE);
      if (choices.isEmpty()) {
        throw error(stmtToken, "choice weighted: empty block");
      }
      return ctx -> {
        double totalWeight = 0.0;
        double[] weights = new double[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
          double w = evalExpr(ctx, choices.get(i).weight());
          if (w > 0.0) {
            weights[i] = w;
            totalWeight += w;
          } else {
            weights[i] = 0.0;
          }
        }
        if (!(totalWeight > 0.0)) {
          if (ctx.engine().isDebugEnabled()) {
            ctx.engine().debug("dsl choice weighted: no positive weights");
          }
          return;
        }
        double r = ctx.rng().nextDouble() * totalWeight;
        double acc = 0.0;
        for (int i = 0; i < choices.size(); i++) {
          acc += weights[i];
          if (r <= acc) {
            choices.get(i).action().execute(ctx);
            return;
          }
        }
        choices.get(choices.size() - 1).action().execute(ctx);
      };
    }

    private void parseMacro() {
      Token at = lookahead;
      String name = requireIdent("macro");
      if (macros.containsKey(name)) {
        throw error(at, "duplicate macro: " + name);
      }
      List<String> params = new ArrayList<>();
      Map<String, ScriptValue> defaults = new java.util.HashMap<>();
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        if (lookahead.type != TokenType.RPAREN) {
          String param = requireIdent("param");
          if (params.contains(param)) {
            throw error(at, "duplicate macro param: " + param);
          }
          params.add(param);
          if (lookahead.type == TokenType.EQUALS) {
            consume(TokenType.EQUALS);
            defaults.put(param, parseAssignValue());
          }
          while (lookahead.type == TokenType.COMMA) {
            consume(TokenType.COMMA);
            String nextParam = requireIdent("param");
            if (params.contains(nextParam)) {
              throw error(at, "duplicate macro param: " + nextParam);
            }
            params.add(nextParam);
            if (lookahead.type == TokenType.EQUALS) {
              consume(TokenType.EQUALS);
              defaults.put(nextParam, parseAssignValue());
            }
          }
        }
        consume(TokenType.RPAREN);
      }
      dev.patric.dungeonsreborn.effects.actions.Action body = parseBlock();
      macros.put(name, new MacroDef(List.copyOf(params), java.util.Collections.unmodifiableMap(defaults), body, at));
    }

    private dev.patric.dungeonsreborn.effects.actions.Action parseMacroCall(Token stmtToken) {
      String name = requireIdent("macro");
      MacroDef def = macros.get(name);
      if (def == null) {
        throw error(stmtToken, "unknown macro: " + name);
      }
      Map<String, ScriptValue> args = parseCallArgs();
      for (String param : def.params()) {
        if (!args.containsKey(param) && !def.defaults().containsKey(param)) {
          throw error(stmtToken, "missing macro arg: " + param);
        }
      }
      for (String arg : args.keySet()) {
        if (!def.params().contains(arg)) {
          throw error(stmtToken, "unexpected macro arg: " + arg);
        }
      }
      return ctx -> {
        String frame = name + "@" + pathAt(stmtToken);
        java.util.ArrayDeque<String> stack = macroStack(ctx);
        if (stack.size() >= MAX_MACRO_DEPTH) {
          throw new IllegalArgumentException(pathAt(stmtToken) + ": macro depth exceeded (" + MAX_MACRO_DEPTH + ")");
        }
        stack.addLast(frame);
        Map<String, Object> values = new java.util.HashMap<>();
        for (String param : def.params()) {
          ScriptValue value = args.containsKey(param) ? args.get(param) : def.defaults().get(param);
          values.put(param, value == null ? null : value.eval(ctx));
        }
        try {
          withTempVars(ctx, VarScope.CAST, values, () -> def.body().execute(ctx));
        } finally {
          stack.removeLast();
          if (stack.isEmpty()) {
            ctx.state().put(DSL_MACRO_STACK, null);
          }
        }
      };
    }

    private Map<String, ScriptValue> parseCallArgs() {
      Map<String, ScriptValue> args = new java.util.HashMap<>();
      while (lookahead.type == TokenType.IDENT && peekNextIs(TokenType.EQUALS)) {
        String key = requireIdent("arg");
        consume(TokenType.EQUALS);
        ScriptValue value = parseAssignValue();
        args.put(key, value);
      }
      return args;
    }

    private java.util.ArrayDeque<String> macroStack(CastContext ctx) {
      Object current = ctx.state().get(DSL_MACRO_STACK);
      if (current instanceof java.util.ArrayDeque<?> deque) {
        @SuppressWarnings("unchecked")
        java.util.ArrayDeque<String> stack = (java.util.ArrayDeque<String>) deque;
        return stack;
      }
      java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
      ctx.state().put(DSL_MACRO_STACK, stack);
      return stack;
    }

    private String renderMacroStack(CastContext ctx) {
      Object current = ctx.state().get(DSL_MACRO_STACK);
      if (!(current instanceof java.util.ArrayDeque<?> deque) || deque.isEmpty()) {
        return "";
      }
      String joined = deque.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" -> "));
      return " (macro stack: " + joined + ")";
    }

    private boolean consumeOps(CastContext ctx, int cost) {
      long tick = ctx.engine().tickNow();
      Object lastTickObj = ctx.state().get(DSL_OPS_TICK);
      long lastTick = lastTickObj instanceof Number n ? n.longValue() : -1L;
      int used = (int) Math.floor(numericVar(ctx.state().get(DSL_OPS_USED), 0.0));
      if (lastTick != tick) {
        used = 0;
        ctx.state().put(DSL_OPS_TICK, tick);
      }
      used += Math.max(1, cost);
      if (used > MAX_OPS_PER_TICK) {
        Object warnTick = ctx.state().get(DSL_OPS_WARN_TICK);
        long lastWarn = warnTick instanceof Number n ? n.longValue() : -1L;
        if (lastWarn != tick && ctx.engine().isDebugEnabled()) {
          ctx.engine().debug("dsl op budget exceeded (" + used + " > " + MAX_OPS_PER_TICK + ")");
          ctx.state().put(DSL_OPS_WARN_TICK, tick);
        }
        return false;
      }
      ctx.state().put(DSL_OPS_USED, used);
      return true;
    }

    private boolean consumeParticles(CastContext ctx, long count) {
      if (count <= 0) {
        return true;
      }
      long used = (long) Math.floor(numericVar(ctx.state().get(DSL_PARTICLE_USED), 0.0));
      long next = used + count;
      if (next > MAX_PARTICLES_PER_CAST) {
        if (ctx.engine().isDebugEnabled()) {
          ctx.engine().debug("dsl particle budget exceeded (" + next + " > " + MAX_PARTICLES_PER_CAST + ")");
        }
        return false;
      }
      ctx.state().put(DSL_PARTICLE_USED, next);
      return true;
    }

    private void incPending(CastContext ctx) {
      int pending = (int) Math.floor(numericVar(ctx.state().get(DSL_PENDING), 0.0));
      ctx.state().put(DSL_PENDING, pending + 1);
    }

    private void decPending(CastContext ctx) {
      int pending = (int) Math.floor(numericVar(ctx.state().get(DSL_PENDING), 0.0));
      pending = Math.max(0, pending - 1);
      ctx.state().put(DSL_PENDING, pending);
      runFinishIfReady(ctx);
    }

    private EntityActions.DamagePolicy parseDamagePolicy(String raw, Token at) {
      if (raw == null) {
        return EntityActions.DamagePolicy.hostileDefault();
      }
      String policy = raw.trim().toLowerCase(Locale.ROOT);
      return switch (policy) {
        case "any" -> EntityActions.DamagePolicy.any();
        case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
        case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
        case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
        default -> throw error(at, "unknown damage policy: " + raw);
      };
    }

    private DamageType parseDamageType(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected damage type");
      }
      String raw = value.text();
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      try {
        return DamageType.valueOf(normalized);
      } catch (IllegalArgumentException ex) {
        String suggestion = suggestEnumValue(raw, DamageType.class);
        String msg = "invalid " + key + "=" + raw;
        if (suggestion != null) {
          msg += " (did you mean " + suggestion + "?)";
        }
        throw error(at, msg);
      }
    }

    private HealType parseHealType(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected heal type");
      }
      String raw = value.text();
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      try {
        return HealType.valueOf(normalized);
      } catch (IllegalArgumentException ex) {
        String suggestion = suggestEnumValue(raw, HealType.class);
        String msg = "invalid " + key + "=" + raw;
        if (suggestion != null) {
          msg += " (did you mean " + suggestion + "?)";
        }
        throw error(at, msg);
      }
    }

    private DamageCause parseDamageCause(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected damage cause");
      }
      String raw = value.text();
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      try {
        return DamageCause.valueOf(normalized);
      } catch (IllegalArgumentException ex) {
        String suggestion = suggestEnumValue(raw, DamageCause.class);
        String msg = "invalid " + key + "=" + raw;
        if (suggestion != null) {
          msg += " (did you mean " + suggestion + "?)";
        }
        throw error(at, msg);
      }
    }

    private PotionEffectType parsePotionEffect(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected potion effect");
      }
      Map<String, Object> map = java.util.Map.of(key, value.text);
      return potionEffectValue(map, key, pathAt(at) + "." + key);
    }

    private String stringValue(Value value, String key, Token at) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected string");
      }
      return value.text();
    }

    private Object rawValue(Value value, String key, Token at) {
      return switch (value.kind()) {
        case NUMBER -> value.number();
        case STRING, IDENT -> value.text();
        default -> throw error(at, "invalid " + key + ": expected string or number");
      };
    }

    private Object particleDataFromAttrs(Particle particle, Map<String, Value> attrs, Token at) {
      if (particle == Particle.DUST) {
        Value colorValue = attrs.getOrDefault("color", attrs.get("colour"));
        if (colorValue == null) {
          throw error(at, "missing color for DUST particle");
        }
        Color color = parseColor(rawValue(colorValue, "color", at), pathAt(at) + ".color");
        NumValue size = numAttr(attrs, "size", 1.0, at);
        return (java.util.function.BiFunction<CastContext, Location, Object>) (ctx, loc) ->
            new Particle.DustOptions(color, (float) evalDouble(size, ctx));
      }
      if (particle == Particle.DUST_COLOR_TRANSITION) {
        Value fromValue = attrs.getOrDefault("color", attrs.get("from"));
        Value toValue = attrs.get("toColor");
        if (fromValue == null || toValue == null) {
          throw error(at, "DUST_COLOR_TRANSITION requires color and toColor");
        }
        Color from = parseColor(rawValue(fromValue, "color", at), pathAt(at) + ".color");
        Color to = parseColor(rawValue(toValue, "toColor", at), pathAt(at) + ".toColor");
        NumValue size = numAttr(attrs, "size", 1.0, at);
        return (java.util.function.BiFunction<CastContext, Location, Object>) (ctx, loc) ->
            new Particle.DustTransition(from, to, (float) evalDouble(size, ctx));
      }
      return null;
    }

    private boolean boolAttr(Map<String, Value> attrs, String key, boolean def, Token at) {
      Value v = attrs.get(key);
      if (v == null) {
        return def;
      }
      switch (v.kind()) {
        case NUMBER -> {
          return Math.abs(v.number()) > 1e-9;
        }
        case STRING, IDENT -> {
          String raw = v.text().trim();
          if ("true".equalsIgnoreCase(raw)) {
            return true;
          }
          if ("false".equalsIgnoreCase(raw)) {
            return false;
          }
          throw error(at, "invalid " + key + ": expected boolean");
        }
        default -> throw error(at, "invalid " + key + ": expected boolean");
      }
    }

    private Targeter<LivingEntity> parseTargeter(Map<String, Value> attrs, Token at) {
      Value typeValue = requireAttr(attrs, "type", at);
      String type = stringValue(typeValue, "type", at).trim().toLowerCase(Locale.ROOT);
      boolean ignoreCaster = boolAttr(attrs, "ignoreCaster", true, at);
      String filterRaw = stringAttr(attrs, "filter", "any", at).trim().toLowerCase(Locale.ROOT);
      java.util.function.Predicate<LivingEntity> filter = switch (filterRaw) {
        case "any" -> e -> true;
        case "players" -> e -> e instanceof Player;
        case "mobs", "non_players", "non-players" -> e -> !(e instanceof Player);
        default -> throw error(at, "invalid filter: " + filterRaw + " (use any|players|mobs)");
      };

      return switch (type) {
        case "self" -> ctx -> {
          LivingEntity caster = ctx.caster();
          return filter.test(caster) ? List.of(caster) : List.of();
        };
        case "projectile_hit", "projectile-hit" -> Targeters.projectileHit();
        case "look_ray", "look-ray" -> {
          NumValue maxDistance = numAttr(attrs, "maxDistance", 20.0, at);
          NumValue raySize = numAttr(attrs, "raySize", 0.35, at);
          boolean stopOnBlock = boolAttr(attrs, "stopOnBlock", true, at);
          yield ctx -> {
            double max = evalDouble(maxDistance, ctx);
            double size = evalDouble(raySize, ctx);
            if (max <= 0.0 || size < 0.0) {
              return List.of();
            }
            return Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, filter).select(ctx);
          };
        }
        case "sphere" -> {
          NumValue radius = numAttr(attrs, "radius", 5.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            if (r < 0.0) {
              return List.of();
            }
            return Targeters.sphere(r, ignoreCaster, filter).select(ctx);
          };
        }
        case "nearest" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            if (r < 0.0) {
              return List.of();
            }
            return Targeters.nearest(r, ignoreCaster, filter).select(ctx);
          };
        }
        case "nearest_within_angle", "nearest-within-angle", "nearest_angle", "nearest-angle" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 60.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            if (r < 0.0 || angle <= 0.0 || angle > 180.0) {
              return List.of();
            }
            return Targeters.nearestWithinAngle(r, angle, ignoreCaster, filter).select(ctx);
          };
        }
        case "cone" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          NumValue angleDegrees = numAttr(attrs, "angleDegrees", 90.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            double angle = evalDouble(angleDegrees, ctx);
            if (r < 0.0 || angle <= 0.0 || angle > 180.0) {
              return List.of();
            }
            return Targeters.cone(r, angle, ignoreCaster, filter).select(ctx);
          };
        }
        case "line_of_sight", "line-of-sight", "los" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            if (r < 0.0) {
              return List.of();
            }
            return Targeters.lineOfSight(r, ignoreCaster, filter).select(ctx);
          };
        }
        case "ground_sphere", "ground-sphere", "ground_snap", "ground-snap" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          NumValue maxDrop = numAttr(attrs, "maxDrop", 24.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            double drop = evalDouble(maxDrop, ctx);
            if (r < 0.0 || drop < 0.0) {
              return List.of();
            }
            return Targeters.groundSphere(r, drop, ignoreCaster, filter).select(ctx);
          };
        }
        case "box" -> {
          NumValue xRadius = numAttr(attrs, "xRadius", 6.0, at);
          NumValue yRadius = numAttr(attrs, "yRadius", 3.0, at);
          NumValue zRadius = numAttr(attrs, "zRadius", 6.0, at);
          yield ctx -> {
            double xr = evalDouble(xRadius, ctx);
            double yr = evalDouble(yRadius, ctx);
            double zr = evalDouble(zRadius, ctx);
            if (xr < 0.0 || yr < 0.0 || zr < 0.0) {
              return List.of();
            }
            return Targeters.box(xr, yr, zr, ignoreCaster, filter).select(ctx);
          };
        }
        case "cylinder" -> {
          NumValue radius = numAttr(attrs, "radius", 6.0, at);
          NumValue halfHeight = attrs.containsKey("halfHeight")
              ? numAttr(attrs, "halfHeight", 2.0, at)
              : numAttr(attrs, "height", 4.0, at);
          boolean useHalfHeight = attrs.containsKey("halfHeight");
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            double hh = evalDouble(halfHeight, ctx);
            if (!useHalfHeight) {
              hh = hh / 2.0;
            }
            if (r < 0.0 || hh < 0.0) {
              return List.of();
            }
            return Targeters.cylinder(r, hh, ignoreCaster, filter).select(ctx);
          };
        }
        case "capsule_ray", "capsule-ray" -> {
          NumValue maxDistance = numAttr(attrs, "maxDistance", 10.0, at);
          NumValue radius = numAttr(attrs, "radius", 1.5, at);
          boolean stopOnBlock = boolAttr(attrs, "stopOnBlock", true, at);
          yield ctx -> {
            double max = evalDouble(maxDistance, ctx);
            double r = evalDouble(radius, ctx);
            if (max <= 0.0 || r < 0.0) {
              return List.of();
            }
            return Targeters.capsuleRay(max, r, stopOnBlock, ignoreCaster, filter).select(ctx);
          };
        }
        case "chain" -> {
          NumValue radius = numAttr(attrs, "radius", 6.0, at);
          NumValue maxTargets = numAttr(attrs, "maxTargets", 3.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            int count = (int) Math.round(evalDouble(maxTargets, ctx));
            if (r < 0.0 || count < 0) {
              return List.of();
            }
            return Targeters.chain(r, count, ignoreCaster, filter).select(ctx);
          };
        }
        case "random", "random_n", "random-n" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          NumValue count = numAttr(attrs, "count", 3.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            int size = (int) Math.round(evalDouble(count, ctx));
            if (r < 0.0 || size <= 0) {
              return List.of();
            }
            return Targeters.sample(Targeters.sphere(r, ignoreCaster, filter), size, Targeters.SampleMode.RANDOM).select(ctx);
          };
        }
        case "weighted_distance", "weighted-distance" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          NumValue count = numAttr(attrs, "count", 3.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            int size = (int) Math.round(evalDouble(count, ctx));
            if (r < 0.0 || size <= 0) {
              return List.of();
            }
            return Targeters.sample(Targeters.sphere(r, ignoreCaster, filter), size, Targeters.SampleMode.WEIGHT_DISTANCE).select(ctx);
          };
        }
        case "weighted_threat", "weighted-threat" -> {
          NumValue radius = numAttr(attrs, "radius", 8.0, at);
          NumValue count = numAttr(attrs, "count", 3.0, at);
          yield ctx -> {
            double r = evalDouble(radius, ctx);
            int size = (int) Math.round(evalDouble(count, ctx));
            if (r < 0.0 || size <= 0) {
              return List.of();
            }
            return Targeters.sample(Targeters.sphere(r, ignoreCaster, filter), size, Targeters.SampleMode.WEIGHT_THREAT).select(ctx);
          };
        }
        case "union", "intersection", "difference" -> {
          Map<String, Value> leftAttrs = prefixedTargeterAttrs(attrs, "left", at);
          Map<String, Value> rightAttrs = prefixedTargeterAttrs(attrs, "right", at);
          Targeter<LivingEntity> left = parseTargeter(leftAttrs, at);
          Targeter<LivingEntity> right = parseTargeter(rightAttrs, at);
          yield switch (type) {
            case "union" -> Targeters.union(left, right);
            case "intersection" -> Targeters.intersection(left, right);
            default -> Targeters.difference(left, right);
          };
        }
        default -> throw error(at, "unknown targeter type: " + type);
      };
    }

    private Map<String, Value> prefixedTargeterAttrs(Map<String, Value> attrs, String prefix, Token at) {
      String typeKey = prefix + "Type";
      Value typeValue = attrs.get(typeKey);
      if (typeValue == null) {
        String altKey = prefix + "_type";
        typeValue = attrs.get(altKey);
      }
      if (typeValue == null) {
        throw error(at, "missing " + typeKey + " for " + prefix + " targeter");
      }
      Map<String, Value> out = new java.util.HashMap<>();
      out.put("type", typeValue);
      for (var entry : attrs.entrySet()) {
        String key = entry.getKey();
        if (key.equalsIgnoreCase(typeKey) || key.equalsIgnoreCase(prefix + "_type")) {
          continue;
        }
        String stripped = stripPrefix(key, prefix);
        if (stripped == null || stripped.isEmpty()) {
          continue;
        }
        out.put(stripped, entry.getValue());
      }
      if (!out.containsKey("ignoreCaster") && attrs.containsKey("ignoreCaster")) {
        out.put("ignoreCaster", attrs.get("ignoreCaster"));
      }
      if (!out.containsKey("filter") && attrs.containsKey("filter")) {
        out.put("filter", attrs.get("filter"));
      }
      return out;
    }

    private String stripPrefix(String key, String prefix) {
      if (key.startsWith(prefix + "_")) {
        return toCamel(key.substring(prefix.length() + 1));
      }
      if (key.startsWith(prefix) && key.length() > prefix.length()) {
        String raw = key.substring(prefix.length());
        return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
      }
      return null;
    }

    private String toCamel(String raw) {
      String[] parts = raw.split("_");
      if (parts.length == 0) {
        return raw;
      }
      StringBuilder out = new StringBuilder(parts[0]);
      for (int i = 1; i < parts.length; i++) {
        String p = parts[i];
        if (p.isEmpty()) {
          continue;
        }
        out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
      }
      return out.toString();
    }

    private java.util.function.DoubleUnaryOperator easingFromId(EasingId id) {
      return switch (id) {
        case LINEAR -> Easings::linear;
        case IN_OUT_CUBIC -> Easings::inOutCubic;
        case OUT_QUAD -> Easings::outQuad;
      };
    }

    private EasingId parseEasing(String raw, Token token) {
      if (raw == null) {
        return EasingId.IN_OUT_CUBIC;
      }
      String normalized = raw.trim().toUpperCase(Locale.ROOT);
      try {
        return EasingId.valueOf(normalized);
      } catch (Exception ex) {
        String suggestion = suggestEnumValue(raw, EasingId.class);
        if (suggestion != null) {
          throw error(token, "unknown easing: " + raw + " (did you mean " + suggestion + "?)");
        }
        throw error(token, "unknown easing: " + raw);
      }
    }

    private Map<String, Value> parseAttributes() {
      Map<String, Value> attrs = new java.util.HashMap<>();
      while (lookahead.type == TokenType.IDENT && peekNextIs(TokenType.EQUALS)) {
        String key = requireIdent("attribute");
        consume(TokenType.EQUALS);
        Value value = parseValue();
        attrs.put(key, value);
      }
      return attrs;
    }

    private Value parseValue() {
      Token t = lookahead;
      if (t.type == TokenType.NUMBER) {
        if (peekNextIs(TokenType.OP)) {
          ExprNode expr = parseExpression();
          return new Value(ValueKind.EXPR, null, 0.0, expr, t.line, t.column);
        }
        consume(TokenType.NUMBER);
        return new Value(ValueKind.NUMBER, t.text, t.number, null, t.line, t.column);
      }
      if (t.type == TokenType.STRING) {
        consume(TokenType.STRING);
        return new Value(ValueKind.STRING, t.text, t.number, null, t.line, t.column);
      }
      if (t.type == TokenType.IDENT) {
        if (peekNextIs(TokenType.OP) || peekNextIs(TokenType.LPAREN)) {
          ExprNode expr = parseExpression();
          return new Value(ValueKind.EXPR, null, 0.0, expr, t.line, t.column);
        }
        consume(TokenType.IDENT);
        return new Value(ValueKind.IDENT, t.text, t.number, null, t.line, t.column);
      }
      if (t.type == TokenType.OP || t.type == TokenType.LPAREN) {
        ExprNode expr = parseExpression();
        return new Value(ValueKind.EXPR, null, 0.0, expr, t.line, t.column);
      }
      throw error("expected value");
    }

    private Value requireAttr(Map<String, Value> attrs, String key, Token at) {
      Value value = attrs.get(key);
      if (value == null) {
        throw new IllegalArgumentException(missingAttrMessage(attrs, key, at));
      }
      return value;
    }

    private String missingAttrMessage(Map<String, Value> attrs, String key, Token at) {
      StringBuilder msg = new StringBuilder(pathAt(at)).append(": missing ").append(key);
      String suggestion = suggestClosest(key, attrs.keySet());
      if (suggestion != null) {
        msg.append(" (did you mean ").append(suggestion).append("?)");
      } else if (!attrs.isEmpty()) {
        msg.append(" (available: ").append(formatKeys(attrs.keySet(), 8)).append(")");
      }
      return msg.toString();
    }

    private String requireIdent(String label) {
      Token t = lookahead;
      if (t.type != TokenType.IDENT) {
        throw error("expected " + label);
      }
      consume(TokenType.IDENT);
      return t.text;
    }

    private String requireStringToken(String label) {
      Token t = lookahead;
      if (t.type != TokenType.STRING) {
        throw error("expected " + label + " string");
      }
      consume(TokenType.STRING);
      return t.text;
    }

    private Value requireValue(String label) {
      if (lookahead.type != TokenType.STRING && lookahead.type != TokenType.IDENT) {
        throw error("expected " + label);
      }
      return parseValue();
    }

    private NumValue numAttr(Map<String, Value> attrs, String key, double def, Token at) {
      Value v = attrs.get(key);
      if (v == null) {
        return new ConstNum(def);
      }
      return numFromValue(v, key, at);
    }

    private NumValue numAttrAlias(Map<String, Value> attrs, String primary, String secondary, double def, Token at) {
      if (attrs.containsKey(primary)) {
        return numAttr(attrs, primary, def, at);
      }
      if (secondary != null && attrs.containsKey(secondary)) {
        return numAttr(attrs, secondary, def, at);
      }
      return new ConstNum(def);
    }

    private String stringAttr(Map<String, Value> attrs, String key, String def, Token at) {
      Value v = attrs.get(key);
      if (v == null) {
        return def;
      }
      if (v.kind() == ValueKind.EXPR || v.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected string");
      }
      return v.text;
    }

    private DamageCause damageCauseAttr(Map<String, Value> attrs, DamageCause def, Token at) {
      Value v = attrs.get("cause");
      String key = "cause";
      if (v == null) {
        v = attrs.get("damageCause");
        key = "damageCause";
      }
      if (v == null) {
        v = attrs.get("damage_cause");
        key = "damage_cause";
      }
      if (v == null) {
        return def;
      }
      return parseDamageCause(v, at, key);
    }

    private java.util.Set<String> tagSetAttr(Map<String, Value> attrs, Token at) {
      Value v = attrs.get("tags");
      if (v == null) {
        v = attrs.get("tag");
      }
      if (v == null) {
        return java.util.Set.of();
      }
      if (v.kind() == ValueKind.EXPR || v.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid tags: expected string");
      }
      return splitTagList(v.text());
    }

    private List<String> splitIdList(String raw) {
      if (raw == null || raw.isBlank()) {
        return List.of();
      }
      String[] parts = raw.split(",");
      List<String> out = new ArrayList<>();
      for (String part : parts) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          out.add(Ids.normalize(trimmed));
        }
      }
      return List.copyOf(out);
    }

    private java.util.Set<String> splitTagList(String raw) {
      if (raw == null || raw.isBlank()) {
        return java.util.Set.of();
      }
      String[] parts = raw.split(",");
      java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
      for (String part : parts) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          out.add(trimmed);
        }
      }
      if (out.isEmpty()) {
        return java.util.Set.of();
      }
      return java.util.Set.copyOf(out);
    }

    private Particle parseParticle(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected particle name");
      }
      Map<String, Object> map = java.util.Map.of(key, value.text);
      return enumValue(map, key, Particle.class, pathAt(at));
    }

    private Sound parseSound(Value value, Token at, String key) {
      if (value.kind() == ValueKind.EXPR || value.kind() == ValueKind.NUMBER) {
        throw error(at, "invalid " + key + ": expected sound name");
      }
      Map<String, Object> map = java.util.Map.of(key, value.text);
      return soundValue(map, key, pathAt(at));
    }

    private record VarTarget(VarScope scope, String key) {
    }

    private VarTarget parseVarTarget() {
      if (lookahead.type == TokenType.IDENT && "var".equalsIgnoreCase(lookahead.text)) {
        consume(TokenType.IDENT);
        consume(TokenType.LPAREN);
        Token at = lookahead;
        String rawKey = requireStringToken("var");
        consume(TokenType.RPAREN);
        return parseVarTarget(rawKey, at);
      }
      Token at = lookahead;
      String rawKey = requireIdent("var");
      return parseVarTarget(rawKey, at);
    }

    private VarTarget parseVarTarget(String rawKey, Token at) {
      String trimmed = rawKey.trim();
      if (trimmed.isEmpty()) {
        throw error(at, "empty var key");
      }
      VarScope scope = VarScope.CAST;
      String key = trimmed;
      int idx = trimmed.indexOf(':');
      if (idx > 0) {
        String prefix = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
        String rest = trimmed.substring(idx + 1).trim();
        if (rest.isEmpty()) {
          throw error(at, "empty var key");
        }
        switch (prefix) {
          case "cast" -> scope = VarScope.CAST;
          case "player" -> scope = VarScope.PLAYER;
          case "entity", "target" -> scope = VarScope.ENTITY;
          case "ability" -> scope = VarScope.ABILITY;
          default -> {
          }
        }
        key = rest;
      }
      return new VarTarget(scope, key);
    }

    private ScriptValue parseAssignValue() {
      if (lookahead.type == TokenType.STRING) {
        String raw = requireStringToken("value");
        return new LiteralValue(raw);
      }
      if (lookahead.type == TokenType.IDENT) {
        if ("true".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.TRUE);
        }
        if ("false".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.FALSE);
        }
        if ("var".equalsIgnoreCase(lookahead.text) && peekNextIs(TokenType.LPAREN)) {
          consume(TokenType.IDENT);
          consume(TokenType.LPAREN);
          String rawKey = requireStringToken("var");
          consume(TokenType.RPAREN);
          return new VarValue(rawKey);
        }
      }
      ExprNode expr = parseExpression();
      return new ExprValue(expr);
    }

    private interface Condition {
      boolean test(CastContext ctx);
    }

    private Condition parseCondition() {
      return parseConditionOr();
    }

    private Condition parseConditionOr() {
      Condition left = parseConditionAnd();
      while (lookahead.type == TokenType.IDENT && "or".equalsIgnoreCase(lookahead.text)) {
        consume(TokenType.IDENT);
        Condition right = parseConditionAnd();
        Condition prev = left;
        left = ctx -> prev.test(ctx) || right.test(ctx);
      }
      return left;
    }

    private Condition parseConditionAnd() {
      Condition left = parseConditionUnary();
      while (lookahead.type == TokenType.IDENT && "and".equalsIgnoreCase(lookahead.text)) {
        consume(TokenType.IDENT);
        Condition right = parseConditionUnary();
        Condition prev = left;
        left = ctx -> prev.test(ctx) && right.test(ctx);
      }
      return left;
    }

    private Condition parseConditionUnary() {
      if (lookahead.type == TokenType.IDENT && "not".equalsIgnoreCase(lookahead.text)) {
        consume(TokenType.IDENT);
        Condition inner = parseConditionUnary();
        return ctx -> !inner.test(ctx);
      }
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        Condition inner = parseConditionOr();
        consume(TokenType.RPAREN);
        return inner;
      }
      return parseConditionAtom();
    }

    private Condition parseConditionAtom() {
      ScriptValue left = parseConditionValue();
      if (lookahead.type == TokenType.COMP) {
        String op = lookahead.text;
        consume(TokenType.COMP);
        ScriptValue right = parseConditionValue();
        return ctx -> compare(left.eval(ctx), right.eval(ctx), op);
      }
      return ctx -> truthy(left.eval(ctx));
    }

    private ScriptValue parseConditionValue() {
      if (lookahead.type == TokenType.STRING) {
        String raw = requireStringToken("value");
        return new LiteralValue(raw);
      }
      if (lookahead.type == TokenType.IDENT && "var".equalsIgnoreCase(lookahead.text) && peekNextIs(TokenType.LPAREN)) {
        consume(TokenType.IDENT);
        consume(TokenType.LPAREN);
        String rawKey = requireStringToken("var");
        consume(TokenType.RPAREN);
        return new VarValue(rawKey);
      }
      if (lookahead.type == TokenType.IDENT) {
        if ("true".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.TRUE);
        }
        if ("false".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          return new LiteralValue(Boolean.FALSE);
        }
        if (peekNextIs(TokenType.COMP)) {
          String name = requireIdent("value");
          return new VarValue(name);
        }
      }
      ExprNode expr = parseExpression();
      return new ExprValue(expr);
    }

    private boolean truthy(Object value) {
      if (value == null) {
        return false;
      }
      if (value instanceof Boolean b) {
        return b;
      }
      if (value instanceof Number n) {
        return Math.abs(n.doubleValue()) > 1e-9;
      }
      String s = String.valueOf(value);
      return !s.isBlank() && !"false".equalsIgnoreCase(s);
    }

    private boolean compare(Object left, Object right, String op) {
      Double ln = toNumber(left);
      Double rn = toNumber(right);
      if (ln != null && rn != null) {
        return switch (op) {
          case "==" -> Double.compare(ln, rn) == 0;
          case "!=" -> Double.compare(ln, rn) != 0;
          case ">" -> ln > rn;
          case ">=" -> ln >= rn;
          case "<" -> ln < rn;
          case "<=" -> ln <= rn;
          default -> false;
        };
      }
      String ls = String.valueOf(left == null ? "" : left);
      String rs = String.valueOf(right == null ? "" : right);
      return switch (op) {
        case "==" -> ls.equals(rs);
        case "!=" -> !ls.equals(rs);
        default -> false;
      };
    }

    private Double toNumber(Object value) {
      if (value == null) {
        return null;
      }
      if (value instanceof Number n) {
        return n.doubleValue();
      }
      try {
        return Double.parseDouble(String.valueOf(value));
      } catch (Exception ignored) {
        return null;
      }
    }

    private Object resolveValue(String name, CastContext ctx) {
      if (name == null) {
        return null;
      }
      String trimmed = name.trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      String lower = trimmed.toLowerCase(Locale.ROOT);
      if (lower.startsWith("var:")) {
        String[] parts = trimmed.split(":", 3);
        if (parts.length == 2) {
          String key = parts[1].trim();
          if (!key.isEmpty()) {
            Object v = vars(ctx, VarScope.CAST).get(key);
            if (v == null) {
              v = vars(ctx, VarScope.ABILITY).get(key);
            }
            if (v == null) {
              v = vars(ctx, VarScope.PLAYER).get(key);
            }
            if (v == null) {
              v = vars(ctx, VarScope.ENTITY).get(key);
            }
            return v;
          }
        } else if (parts.length == 3) {
          VarScope scope = parseVarScope(parts[1].trim(), "dsl", VarScope.CAST);
          String key = parts[2].trim();
          if (!key.isEmpty()) {
            return vars(ctx, scope).get(key);
          }
        }
      }
      int idx = trimmed.indexOf(':');
      if (idx > 0) {
        String prefix = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
        String key = trimmed.substring(idx + 1).trim();
        if (!key.isEmpty()) {
          return switch (prefix) {
            case "cast" -> vars(ctx, VarScope.CAST).get(key);
            case "ability" -> vars(ctx, VarScope.ABILITY).get(key);
            case "player" -> vars(ctx, VarScope.PLAYER).get(key);
            case "entity", "target" -> vars(ctx, VarScope.ENTITY).get(key);
            default -> null;
          };
        }
      }
      Object v = vars(ctx, VarScope.CAST).get(trimmed);
      if (v != null) {
        return v;
      }
      v = vars(ctx, VarScope.ABILITY).get(trimmed);
      if (v != null) {
        return v;
      }
      v = vars(ctx, VarScope.PLAYER).get(trimmed);
      if (v != null) {
        return v;
      }
      v = vars(ctx, VarScope.ENTITY).get(trimmed);
      if (v != null) {
        return v;
      }
      return resolveVar(trimmed, ctx);
    }

    private NumValue numFromValue(Value value, String key, Token at) {
      switch (value.kind()) {
        case NUMBER -> {
          return new ConstNum(value.number());
        }
        case IDENT -> {
          return exprValue(value.text(), pathAt(at) + "." + key);
        }
        case EXPR -> {
          ExprNode expr = value.expr();
          return ctx -> evalExpr(ctx, expr);
        }
        case STRING -> {
          String raw = value.text();
          if (raw.trim().startsWith("expr:")) {
            return exprValue(raw.trim().substring("expr:".length()).trim(), pathAt(at) + "." + key);
          }
          try {
            return new ConstNum(Double.parseDouble(raw.trim()));
          } catch (NumberFormatException ex) {
            throw error(at, "invalid number for " + key + ": " + raw);
          }
        }
        default -> throw error(at, "invalid number for " + key);
      }
    }

    private double evalExpr(CastContext ctx, ExprNode expr) {
      double v = expr.eval(ctx, EffectsYamlAbilities.this::resolveVar);
      if (!Double.isFinite(v)) {
        return 0.0;
      }
      return v;
    }

    private ExprNode parseExpression() {
      ExprNode expr = parseTerm();
      while (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parseTerm();
        expr = binary(op, expr, right);
      }
      return expr;
    }

    private ExprNode parseTerm() {
      ExprNode left = parsePower();
      while (lookahead.type == TokenType.OP && ("*".equals(lookahead.text) || "/".equals(lookahead.text) || "%".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary(op, left, right);
      }
      return left;
    }

    private ExprNode parsePower() {
      ExprNode left = parseUnary();
      if (lookahead.type == TokenType.OP && "^".equals(lookahead.text)) {
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary("^", left, right);
      }
      return left;
    }

    private ExprNode parseUnary() {
      if (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode inner = parseUnary();
        return unary(op, inner);
      }
      return parsePrimary();
    }

    private ExprNode parsePrimary() {
      if (lookahead.type == TokenType.NUMBER) {
        double v = lookahead.number;
        consume(TokenType.NUMBER);
        return (ctx, vars) -> v;
      }
      if (lookahead.type == TokenType.IDENT) {
        Token nameToken = lookahead;
        String name = nameToken.text;
        consume(TokenType.IDENT);
        if (lookahead.type == TokenType.LPAREN) {
          consume(TokenType.LPAREN);
          List<ExprNode> args = new ArrayList<>();
          if (lookahead.type != TokenType.RPAREN) {
            args.add(parseExpression());
            while (lookahead.type == TokenType.COMMA) {
              consume(TokenType.COMMA);
              args.add(parseExpression());
            }
          }
          consume(TokenType.RPAREN);
          return function(nameToken, name, args);
        }
        return (ctx, vars) -> vars.resolve(name, ctx);
      }
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        ExprNode expr = parseExpression();
        consume(TokenType.RPAREN);
        return expr;
      }
      throw error("expected expression");
    }

    private ExprNode binary(String op, ExprNode a, ExprNode b) {
      return switch (op) {
        case "+" -> (ctx, vars) -> a.eval(ctx, vars) + b.eval(ctx, vars);
        case "-" -> (ctx, vars) -> a.eval(ctx, vars) - b.eval(ctx, vars);
        case "*" -> (ctx, vars) -> a.eval(ctx, vars) * b.eval(ctx, vars);
        case "/" -> (ctx, vars) -> a.eval(ctx, vars) / b.eval(ctx, vars);
        case "%" -> (ctx, vars) -> a.eval(ctx, vars) % b.eval(ctx, vars);
        case "^" -> (ctx, vars) -> Math.pow(a.eval(ctx, vars), b.eval(ctx, vars));
        default -> (ctx, vars) -> 0.0;
      };
    }

    private ExprNode unary(String op, ExprNode inner) {
      return switch (op) {
        case "-" -> (ctx, vars) -> -inner.eval(ctx, vars);
        case "+" -> (ctx, vars) -> inner.eval(ctx, vars);
        default -> (ctx, vars) -> inner.eval(ctx, vars);
      };
    }

    private ExprNode function(Token at, String name, List<ExprNode> args) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (!ALLOWED_FUNCTION_SET.contains(lower)) {
        throw error(at, "unknown function: " + name + " (use " + String.join("|", ALLOWED_FUNCTIONS) + ")");
      }
      return switch (lower) {
        case "min" -> (ctx, vars) -> {
          if (args.isEmpty()) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.min(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "max" -> (ctx, vars) -> {
          if (args.isEmpty()) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.max(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "clamp" -> (ctx, vars) -> {
          if (args.size() < 3) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          double min = args.get(1).eval(ctx, vars);
          double max = args.get(2).eval(ctx, vars);
          return Math.max(min, Math.min(max, v));
        };
        case "lerp" -> (ctx, vars) -> {
          if (args.size() < 3) {
            return 0.0;
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          double t = args.get(2).eval(ctx, vars);
          return a + (b - a) * t;
        };
        case "rand" -> (ctx, vars) -> {
          if (args.size() < 2) {
            return 0.0;
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          double r = ctx.rng().nextDouble();
          return a + r * (b - a);
        };
        case "abs" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.abs(args.get(0).eval(ctx, vars));
        case "floor" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.floor(args.get(0).eval(ctx, vars));
        case "ceil" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.ceil(args.get(0).eval(ctx, vars));
        case "round" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.round(args.get(0).eval(ctx, vars));
        default -> (ctx, vars) -> 0.0;
      };
    }

    private String pathAt(Token token) {
      return path + " (" + token.line + ":" + token.column + ")";
    }

    private boolean peekNextIs(TokenType type) {
      Token current = lookahead;
      Token next = nextTokenInternal(current);
      return next.type == type;
    }

    private Token nextTokenInternal(Token current) {
      int savedPos = pos;
      int savedLine = line;
      int savedCol = column;
      Token savedLookahead = lookahead;
      lookahead = current;
      Token next = nextToken();
      pos = savedPos;
      line = savedLine;
      column = savedCol;
      lookahead = savedLookahead;
      return next;
    }

    private void consume(TokenType type) {
      if (lookahead.type != type) {
        throw error("expected " + type.name().toLowerCase(Locale.ROOT));
      }
      lookahead = nextToken();
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(path + " (" + line + ":" + column + "): " + message);
    }

    private IllegalArgumentException error(Token token, String message) {
      return new IllegalArgumentException(path + " (" + token.line + ":" + token.column + "): " + message);
    }

    private Token nextToken() {
      int len = input.length();
      while (pos < len) {
        char c = input.charAt(pos);
        if (c == '#') {
          skipLineComment();
          continue;
        }
        if (Character.isWhitespace(c)) {
          advance(c);
          continue;
        }
        int startLine = line;
        int startCol = column;
        if (c == '{') {
          advance(c);
          return new Token(TokenType.LBRACE, "{", 0.0, startLine, startCol);
        }
        if (c == '}') {
          advance(c);
          return new Token(TokenType.RBRACE, "}", 0.0, startLine, startCol);
        }
        if (c == '(') {
          advance(c);
          return new Token(TokenType.LPAREN, "(", 0.0, startLine, startCol);
        }
        if (c == ')') {
          advance(c);
          return new Token(TokenType.RPAREN, ")", 0.0, startLine, startCol);
        }
        if (c == ',') {
          advance(c);
          return new Token(TokenType.COMMA, ",", 0.0, startLine, startCol);
        }
        if (c == ':') {
          advance(c);
          return new Token(TokenType.COLON, ":", 0.0, startLine, startCol);
        }
        if (c == '=') {
          if (peekChar() == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "==", 0.0, startLine, startCol);
          }
          advance(c);
          return new Token(TokenType.EQUALS, "=", 0.0, startLine, startCol);
        }
        if (c == '!') {
          if (peekChar() == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "!=", 0.0, startLine, startCol);
          }
          throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): unexpected char: " + c);
        }
        if (c == '<' || c == '>') {
          char next = peekChar();
          if (next == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "" + c + next, 0.0, startLine, startCol);
          }
          advance(c);
          return new Token(TokenType.COMP, String.valueOf(c), 0.0, startLine, startCol);
        }
        if (c == '"' ) {
          String text = readString();
          return new Token(TokenType.STRING, text, 0.0, startLine, startCol);
        }
        if (c == '-' || c == '+') {
          char next = peekChar();
          if (Character.isDigit(next) || next == '.') {
            String num = readNumber();
            try {
              double value = Double.parseDouble(num);
              return new Token(TokenType.NUMBER, num, value, startLine, startCol);
            } catch (NumberFormatException ex) {
              throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): invalid number: " + num);
            }
          }
          advance(c);
          return new Token(TokenType.OP, String.valueOf(c), 0.0, startLine, startCol);
        }
        if (c == '*' || c == '/' || c == '%' || c == '^') {
          advance(c);
          return new Token(TokenType.OP, String.valueOf(c), 0.0, startLine, startCol);
        }
        if (Character.isDigit(c) || c == '.') {
          String num = readNumber();
          try {
            double value = Double.parseDouble(num);
            return new Token(TokenType.NUMBER, num, value, startLine, startCol);
          } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): invalid number: " + num);
          }
        }
        if (Character.isLetter(c) || c == '_' ) {
          String ident = readIdent();
          return new Token(TokenType.IDENT, ident, 0.0, startLine, startCol);
        }
        throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): unexpected char: " + c);
      }
      return new Token(TokenType.EOF, "", 0.0, line, column);
    }

    private char peekChar() {
      if (pos + 1 >= input.length()) {
        return '\0';
      }
      return input.charAt(pos + 1);
    }

    private void advance(char c) {
      pos++;
      if (c == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }

    private void skipLineComment() {
      while (pos < input.length()) {
        char c = input.charAt(pos);
        advance(c);
        if (c == '\n') {
          return;
        }
      }
    }

    private String readString() {
      StringBuilder out = new StringBuilder();
      char quote = input.charAt(pos);
      advance(quote);
      while (pos < input.length()) {
        char c = input.charAt(pos);
        advance(c);
        if (c == quote) {
          return out.toString();
        }
        if (c == '\\' && pos < input.length()) {
          char next = input.charAt(pos);
          advance(next);
          switch (next) {
            case 'n' -> out.append('\n');
            case 't' -> out.append('\t');
            case '"' -> out.append('"');
            case '\\' -> out.append('\\');
            default -> out.append(next);
          }
          continue;
        }
        out.append(c);
      }
      throw new IllegalArgumentException(path + " (" + line + ":" + column + "): unterminated string");
    }

    private String readNumber() {
      int start = pos;
      if (pos < input.length()) {
        char c = input.charAt(pos);
        if (c == '-' || c == '+') {
          advance(c);
        }
      }
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isDigit(c) || c == '.') {
          advance(c);
          continue;
        }
        break;
      }
      return input.substring(start, pos);
    }

    private String readIdent() {
      int start = pos;
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == ':') {
          advance(c);
          continue;
        }
        break;
      }
      return input.substring(start, pos);
    }
  }

  private void runFinishIfReady(CastContext ctx) {
    if (ctx.state().isCancelled()) {
      return;
    }
    if (!Boolean.TRUE.equals(ctx.state().get(DSL_CAST_DONE))) {
      return;
    }
    int pending = (int) Math.floor(numericVar(ctx.state().get(DSL_PENDING), 0.0));
    if (pending > 0) {
      return;
    }
    Object hook = ctx.state().get(DSL_ON_FINISH);
    if (hook instanceof dev.patric.dungeonsreborn.effects.actions.Action action) {
      ctx.state().put(DSL_ON_FINISH, null);
      action.execute(ctx);
    }
  }

  private void withTempVar(CastContext ctx, VarScope scope, String key, Object value, Runnable action) {
    Map<String, Object> vars = vars(ctx, scope);
    Map<String, Long> expirations = varExpirations(ctx, scope);
    boolean had = vars.containsKey(key);
    Object prev = vars.get(key);
    boolean hadExp = expirations.containsKey(key);
    Long prevExp = expirations.get(key);
    setVar(ctx, scope, key, value);
    try {
      action.run();
    } finally {
      if (!had) {
        vars.remove(key);
        expirations.remove(key);
      } else {
        vars.put(key, prev);
        if (hadExp) {
          expirations.put(key, prevExp);
        } else {
          expirations.remove(key);
        }
      }
    }
  }

  private void withTempVars(CastContext ctx, VarScope scope, Map<String, Object> values, Runnable action) {
    Map<String, Object> vars = vars(ctx, scope);
    Map<String, Object> prev = new java.util.HashMap<>();
    Map<String, Long> prevExp = new java.util.HashMap<>();
    Set<String> had = new java.util.HashSet<>();
    Set<String> hadExp = new java.util.HashSet<>();
    Set<String> touched = new java.util.HashSet<>();
    Map<String, Long> expirations = varExpirations(ctx, scope);
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String key = entry.getKey();
      if (vars.containsKey(key)) {
        had.add(key);
        prev.put(key, vars.get(key));
      }
      if (expirations.containsKey(key)) {
        hadExp.add(key);
        prevExp.put(key, expirations.get(key));
      }
      Object value = entry.getValue();
      if (setVar(ctx, scope, key, value)) {
        touched.add(key);
      }
    }
    try {
      action.run();
    } finally {
      for (String key : touched) {
        if (had.contains(key)) {
          vars.put(key, prev.get(key));
        } else {
          vars.remove(key);
        }
        if (hadExp.contains(key)) {
          expirations.put(key, prevExp.get(key));
        } else {
          expirations.remove(key);
        }
      }
    }
  }

  private static CastContext followCasterContext(CastContext ctx) {
    Location origin;
    Vector direction;
    if (ctx.caster() instanceof Player player) {
      origin = player.getEyeLocation();
      direction = origin.getDirection();
    } else {
      origin = ctx.caster().getLocation();
      direction = origin.getDirection();
    }
    return new CastContext(
        ctx.engine(),
        ctx.plugin(),
        ctx.castId(),
        ctx.abilityId(),
        ctx.engine().tickNow(),
        ctx.state(),
        ctx.caster(),
        origin.clone(),
        direction.clone(),
        ctx.itemInHand());
  }

  private Targeter<LivingEntity> compileTargeter(Map<String, Object> node, String path) {
    String type = requireString(node, "type", path + ".type").trim().toLowerCase(Locale.ROOT);
    boolean ignoreCaster = bool(node, "ignoreCaster", true);

    String filterRaw = string(node, "filter", "any").trim().toLowerCase(Locale.ROOT);
    java.util.function.Predicate<LivingEntity> filter = switch (filterRaw) {
      case "any" -> e -> true;
      case "players" -> e -> e instanceof org.bukkit.entity.Player;
      case "mobs", "non_players", "non-players" -> e -> !(e instanceof org.bukkit.entity.Player);
      default -> throw new IllegalArgumentException(path + ".filter: invalid filter=" + filterRaw + " (use any|players|mobs)");
    };

    boolean cacheable = true;
    Targeter<LivingEntity> base = switch (type) {
      case "self" -> Targeters.self();
      case "context_target", "context-target" -> {
        String key = string(node, "key", Vars.MOB_TARGET);
        yield Targeters.contextTarget(key);
      }
      case "projectile_hit", "projectile-hit" -> Targeters.projectileHit();
      case "look_ray", "look-ray" -> {
        NumValue maxDistance = numValue(node, "maxDistance", 20.0, path);
        NumValue raySize = numValue(node, "raySize", 0.35, path);
        cacheable = cacheable && maxDistance.isConstant() && raySize.isConstant();
        boolean stopOnBlock = bool(node, "stopOnBlock", true);
        yield ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double size = evalDouble(raySize, ctx);
          if (max <= 0.0 || size < 0.0) {
            return List.of();
          }
          return Targeters.lookRay(max, size, stopOnBlock, ignoreCaster, filter).select(ctx);
        };
      }
      case "sphere" -> {
        NumValue radius = numValue(node, "radius", 5.0, path);
        cacheable = cacheable && radius.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          if (r < 0.0) {
            return List.of();
          }
          return Targeters.sphere(r, ignoreCaster, filter).select(ctx);
        };
      }
      case "nearest" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        cacheable = cacheable && radius.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          if (r < 0.0) {
            return List.of();
          }
          return Targeters.nearest(r, ignoreCaster, filter).select(ctx);
        };
      }
      case "nearest_within_angle", "nearest-within-angle", "nearest_angle", "nearest-angle" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        NumValue angleDegrees = numValue(node, "angleDegrees", 60.0, path);
        cacheable = cacheable && radius.isConstant() && angleDegrees.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          if (r < 0.0 || angle <= 0.0 || angle > 180.0) {
            return List.of();
          }
          return Targeters.nearestWithinAngle(r, angle, ignoreCaster, filter).select(ctx);
        };
      }
      case "cone" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        NumValue angleDegrees = numValue(node, "angleDegrees", 90.0, path);
        cacheable = cacheable && radius.isConstant() && angleDegrees.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double angle = evalDouble(angleDegrees, ctx);
          if (r < 0.0 || angle <= 0.0 || angle > 180.0) {
            return List.of();
          }
          return Targeters.cone(r, angle, ignoreCaster, filter).select(ctx);
        };
      }
      case "line_of_sight", "line-of-sight", "los" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        cacheable = cacheable && radius.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          if (r < 0.0) {
            return List.of();
          }
          return Targeters.lineOfSight(r, ignoreCaster, filter).select(ctx);
        };
      }
      case "ground_sphere", "ground-sphere", "ground_snap", "ground-snap" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        NumValue maxDrop = numValue(node, "maxDrop", 24.0, path);
        cacheable = cacheable && radius.isConstant() && maxDrop.isConstant();
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double drop = evalDouble(maxDrop, ctx);
          if (r < 0.0 || drop < 0.0) {
            return List.of();
          }
          return Targeters.groundSphere(r, drop, ignoreCaster, filter).select(ctx);
        };
      }
      case "box" -> {
        NumValue xRadius = numValue(node, "xRadius", 6.0, path);
        NumValue yRadius = numValue(node, "yRadius", 3.0, path);
        NumValue zRadius = numValue(node, "zRadius", 6.0, path);
        cacheable = cacheable && xRadius.isConstant() && yRadius.isConstant() && zRadius.isConstant();
        yield ctx -> {
          double xr = evalDouble(xRadius, ctx);
          double yr = evalDouble(yRadius, ctx);
          double zr = evalDouble(zRadius, ctx);
          if (xr < 0.0 || yr < 0.0 || zr < 0.0) {
            return List.of();
          }
          return Targeters.box(xr, yr, zr, ignoreCaster, filter).select(ctx);
        };
      }
      case "cylinder" -> {
        NumValue radius = numValue(node, "radius", 6.0, path);
        NumValue halfHeight = node.containsKey("halfHeight")
            ? numValue(node, "halfHeight", 2.0, path)
            : numValue(node, "height", 4.0, path);
        cacheable = cacheable && radius.isConstant() && halfHeight.isConstant();
        boolean useHalfHeight = node.containsKey("halfHeight");
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          double hh = evalDouble(halfHeight, ctx);
          if (!useHalfHeight) {
            hh = hh / 2.0;
          }
          if (r < 0.0 || hh < 0.0) {
            return List.of();
          }
          return Targeters.cylinder(r, hh, ignoreCaster, filter).select(ctx);
        };
      }
      case "capsule_ray", "capsule-ray" -> {
        NumValue maxDistance = numValue(node, "maxDistance", 10.0, path);
        NumValue radius = numValue(node, "radius", 1.5, path);
        cacheable = cacheable && maxDistance.isConstant() && radius.isConstant();
        boolean stopOnBlock = bool(node, "stopOnBlock", true);
        yield ctx -> {
          double max = evalDouble(maxDistance, ctx);
          double r = evalDouble(radius, ctx);
          if (max <= 0.0 || r < 0.0) {
            return List.of();
          }
          return Targeters.capsuleRay(max, r, stopOnBlock, ignoreCaster, filter).select(ctx);
        };
      }
      case "chain" -> {
        NumValue radius = numValue(node, "radius", 6.0, path);
        NumValue maxTargets = numValue(node, "maxTargets", 3.0, path);
        cacheable = false;
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int count = (int) Math.round(evalDouble(maxTargets, ctx));
          if (r < 0.0 || count < 0) {
            return List.of();
          }
          return Targeters.chain(r, count, ignoreCaster, filter).select(ctx);
        };
      }
      case "random", "random_n", "random-n" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        NumValue count = numValue(node, "count", 3.0, path);
        cacheable = false;
        Targeter<LivingEntity> source = node.containsKey("source")
            ? compileTargeter(castMap(node.get("source"), path + ".source"), path + ".source")
            : null;
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int size = (int) Math.round(evalDouble(count, ctx));
          if (size <= 0) {
            return List.of();
          }
          Targeter<LivingEntity> baseTargeter = source != null ? source : Targeters.sphere(Math.max(0.0, r), ignoreCaster, filter);
          return Targeters.sample(baseTargeter, size, Targeters.SampleMode.RANDOM).select(ctx);
        };
      }
      case "weighted_distance", "weighted-distance" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        NumValue count = numValue(node, "count", 3.0, path);
        cacheable = false;
        Targeter<LivingEntity> source = node.containsKey("source")
            ? compileTargeter(castMap(node.get("source"), path + ".source"), path + ".source")
            : null;
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int size = (int) Math.round(evalDouble(count, ctx));
          if (size <= 0) {
            return List.of();
          }
          Targeter<LivingEntity> baseTargeter = source != null ? source : Targeters.sphere(Math.max(0.0, r), ignoreCaster, filter);
          return Targeters.sample(baseTargeter, size, Targeters.SampleMode.WEIGHT_DISTANCE).select(ctx);
        };
      }
      case "weighted_threat", "weighted-threat" -> {
        NumValue radius = numValue(node, "radius", 8.0, path);
        NumValue count = numValue(node, "count", 3.0, path);
        cacheable = false;
        Targeter<LivingEntity> source = node.containsKey("source")
            ? compileTargeter(castMap(node.get("source"), path + ".source"), path + ".source")
            : null;
        yield ctx -> {
          double r = evalDouble(radius, ctx);
          int size = (int) Math.round(evalDouble(count, ctx));
          if (size <= 0) {
            return List.of();
          }
          Targeter<LivingEntity> baseTargeter = source != null ? source : Targeters.sphere(Math.max(0.0, r), ignoreCaster, filter);
          return Targeters.sample(baseTargeter, size, Targeters.SampleMode.WEIGHT_THREAT).select(ctx);
        };
      }
      case "union", "intersection", "difference" -> {
        cacheable = false;
        Map<String, Object> leftNode = node.containsKey("left")
            ? castMap(node.get("left"), path + ".left")
            : extractPrefixedTargeterNode(node, "left", path);
        Map<String, Object> rightNode = node.containsKey("right")
            ? castMap(node.get("right"), path + ".right")
            : extractPrefixedTargeterNode(node, "right", path);
        Targeter<LivingEntity> left = compileTargeter(leftNode, path + ".left");
        Targeter<LivingEntity> right = compileTargeter(rightNode, path + ".right");
        yield switch (type) {
          case "union" -> Targeters.union(left, right);
          case "intersection" -> Targeters.intersection(left, right);
          default -> Targeters.difference(left, right);
        };
      }
      default -> throw new IllegalArgumentException(path + ".type: unknown type: " + type);
    };

    return cacheable ? Targeters.cachedPerTick("yaml:" + path, base) : base;
  }

  private static Map<String, Object> extractPrefixedTargeterNode(Map<String, Object> node, String prefix, String path) {
    String typeKey = prefix + "Type";
    Object typeValue = node.get(typeKey);
    if (typeValue == null) {
      typeValue = node.get(prefix + "_type");
    }
    if (typeValue == null) {
      throw new IllegalArgumentException(path + ": missing " + typeKey);
    }
    java.util.HashMap<String, Object> out = new java.util.HashMap<>();
    out.put("type", typeValue);
    for (var entry : node.entrySet()) {
      String key = entry.getKey();
      if (key.equalsIgnoreCase(typeKey) || key.equalsIgnoreCase(prefix + "_type")) {
        continue;
      }
      String stripped = stripPrefix(key, prefix);
      if (stripped == null || stripped.isBlank()) {
        continue;
      }
      out.put(stripped, entry.getValue());
    }
    if (!out.containsKey("ignoreCaster") && node.containsKey("ignoreCaster")) {
      out.put("ignoreCaster", node.get("ignoreCaster"));
    }
    if (!out.containsKey("filter") && node.containsKey("filter")) {
      out.put("filter", node.get("filter"));
    }
    return out;
  }

  private static String stripPrefix(String key, String prefix) {
    if (key.startsWith(prefix + "_")) {
      return toCamel(key.substring(prefix.length() + 1));
    }
    if (key.startsWith(prefix) && key.length() > prefix.length()) {
      String raw = key.substring(prefix.length());
      return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
    }
    return null;
  }

  private static String toCamel(String raw) {
    String[] parts = raw.split("_");
    if (parts.length == 0) {
      return raw;
    }
    StringBuilder out = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      String part = parts[i];
      if (part.isEmpty()) {
        continue;
      }
      out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    return out.toString();
  }

  public interface NumValue {
    double eval(CastContext ctx);

    default boolean isConstant() {
      return false;
    }
  }

  private record ConstNum(double value) implements NumValue {
    @Override
    public double eval(CastContext ctx) {
      return value;
    }

    @Override
    public boolean isConstant() {
      return true;
    }
  }

  public interface ValueSupplier {
    Object eval(CastContext ctx);
  }

  private interface VarResolver {
    double resolve(String name, CastContext ctx);
  }

  private interface ExprNode {
    double eval(CastContext ctx, VarResolver vars);
  }

  private NumValue numValue(Map<String, Object> node, String key, double def, String path) {
    return numValue(node.get(key), def, path + "." + key);
  }

  private NumValue requireNumValue(Map<String, Object> node, String key, String path) {
    if (!node.containsKey(key)) {
      throw new IllegalArgumentException(missingKeyMessage(node, key, path));
    }
    return numValue(node.get(key), 0.0, path);
  }

  private NumValue numValue(Object raw, double def, String path) {
    if (raw == null) {
      return new ConstNum(def);
    }
    if (raw instanceof Number n) {
      return new ConstNum(n.doubleValue());
    }
    if (raw instanceof Map<?, ?> map) {
      Object expr = map.get("expr");
      if (expr == null) {
        throw new IllegalArgumentException(path + ": expected number or expr");
      }
      return exprValue(String.valueOf(expr), path + ".expr");
    }
    String s = String.valueOf(raw).trim();
    if (s.startsWith("expr:")) {
      return exprValue(s.substring("expr:".length()).trim(), path);
    }
    try {
      return new ConstNum(Double.parseDouble(s));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected number or expr");
    }
  }

  private static double evalDouble(NumValue value, CastContext ctx) {
    double v = value.eval(ctx);
    if (!Double.isFinite(v)) {
      if (ctx.engine().isDebugEnabled()) {
        ctx.engine().debug("expr produced non-finite value: " + v);
      }
      return 0.0;
    }
    return v;
  }

  private static int evalInt(NumValue value, CastContext ctx) {
    return (int) Math.round(evalDouble(value, ctx));
  }

  private static long evalLong(NumValue value, CastContext ctx) {
    return Math.round(evalDouble(value, ctx));
  }

  private ValueSupplier varValue(Object raw, String path) {
    if (raw == null) {
      return ctx -> null;
    }
    if (raw instanceof Map<?, ?> map && map.containsKey("expr")) {
      NumValue value = numValue(raw, 0.0, path);
      return ctx -> evalDouble(value, ctx);
    }
    if (raw instanceof String s && s.trim().startsWith("expr:")) {
      NumValue value = numValue(raw, 0.0, path);
      return ctx -> evalDouble(value, ctx);
    }
    return ctx -> raw;
  }

  private NumValue exprValue(String expr, String path) {
    ExprNode node = new ExprParser(expr, path).parse();
    return ctx -> node.eval(ctx, this::resolveVar);
  }

  private double resolveVar(String name, CastContext ctx) {
    String lower = name.toLowerCase(Locale.ROOT);
    switch (lower) {
      case "mana" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return ctx.engine().manaProvider().get(player);
        }
        return 0.0;
      }
      case "mana_max", "manamax" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return ctx.engine().manaProvider().getMax(player);
        }
        return 0.0;
      }
      case "caster_health", "casterhealth" -> {
        return ctx.caster().getHealth();
      }
      case "caster_max_health", "castermaxhealth" -> {
        AttributeInstance attribute = ctx.caster().getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
      }
      case "t" -> {
        return numericVar(vars(ctx, VarScope.CAST).get("t"), 0.0);
      }
      case "distance" -> {
        LivingEntity target = lastEntity(ctx);
        if (target == null) {
          return 0.0;
        }
        return target.getLocation().distance(ctx.origin());
      }
      default -> {
      }
    }

    if (lower.startsWith("var:")) {
      String[] parts = name.split(":", 3);
      if (parts.length == 2) {
        String key = parts[1].trim();
        if (key.isEmpty()) {
          return 0.0;
        }
        Object v = vars(ctx, VarScope.CAST).get(key);
        if (v == null) {
          v = vars(ctx, VarScope.ABILITY).get(key);
        }
        if (v == null) {
          v = vars(ctx, VarScope.PLAYER).get(key);
        }
        if (v == null) {
          v = vars(ctx, VarScope.ENTITY).get(key);
        }
        return numericVar(v, 0.0);
      }
      if (parts.length == 3) {
        VarScope scope = parseVarScope(parts[1].trim(), "expr", VarScope.CAST);
        String key = parts[2].trim();
        if (key.isEmpty()) {
          return 0.0;
        }
        Object v = vars(ctx, scope).get(key);
        return numericVar(v, 0.0);
      }
    }

    Object direct = vars(ctx, VarScope.CAST).get(name);
    if (direct != null) {
      return numericVar(direct, 0.0);
    }
    direct = vars(ctx, VarScope.ABILITY).get(name);
    if (direct != null) {
      return numericVar(direct, 0.0);
    }
    direct = vars(ctx, VarScope.PLAYER).get(name);
    if (direct != null) {
      return numericVar(direct, 0.0);
    }
    direct = vars(ctx, VarScope.ENTITY).get(name);
    if (direct != null) {
      return numericVar(direct, 0.0);
    }

    return 0.0;
  }

  private static final class ExprParser {
    private enum TokenType {
      NUMBER,
      IDENT,
      OP,
      LPAREN,
      RPAREN,
      COMMA,
      END
    }

    private record Token(TokenType type, String text, double number) {
    }

    private final String input;
    private final String path;
    private int pos;
    private Token lookahead;

    ExprParser(String input, String path) {
      this.input = Objects.requireNonNull(input, "input");
      this.path = Objects.requireNonNull(path, "path");
      this.lookahead = nextToken();
    }

    ExprNode parse() {
      ExprNode expr = parseExpression();
      if (lookahead.type != TokenType.END) {
        throw new IllegalArgumentException(path + ": unexpected token: " + lookahead.text);
      }
      return expr;
    }

    private ExprNode parseExpression() {
      ExprNode left = parseTerm();
      while (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parseTerm();
        left = binary(op, left, right);
      }
      return left;
    }

    private ExprNode parseTerm() {
      ExprNode left = parsePower();
      while (lookahead.type == TokenType.OP && ("*".equals(lookahead.text) || "/".equals(lookahead.text) || "%".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary(op, left, right);
      }
      return left;
    }

    private ExprNode parsePower() {
      ExprNode left = parseUnary();
      if (lookahead.type == TokenType.OP && "^".equals(lookahead.text)) {
        consume(TokenType.OP);
        ExprNode right = parsePower();
        left = binary("^", left, right);
      }
      return left;
    }

    private ExprNode parseUnary() {
      if (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        String op = lookahead.text;
        consume(TokenType.OP);
        ExprNode inner = parseUnary();
        return unary(op, inner);
      }
      return parsePrimary();
    }

    private ExprNode parsePrimary() {
      if (lookahead.type == TokenType.NUMBER) {
        double v = lookahead.number;
        consume(TokenType.NUMBER);
        return (ctx, vars) -> v;
      }
      if (lookahead.type == TokenType.IDENT) {
        String name = lookahead.text;
        consume(TokenType.IDENT);
        if (lookahead.type == TokenType.LPAREN) {
          consume(TokenType.LPAREN);
          List<ExprNode> args = new ArrayList<>();
          if (lookahead.type != TokenType.RPAREN) {
            args.add(parseExpression());
            while (lookahead.type == TokenType.COMMA) {
              consume(TokenType.COMMA);
              args.add(parseExpression());
            }
          }
          consume(TokenType.RPAREN);
          return function(name, args);
        }
        return (ctx, vars) -> vars.resolve(name, ctx);
      }
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        ExprNode expr = parseExpression();
        consume(TokenType.RPAREN);
        return expr;
      }
      throw new IllegalArgumentException(path + ": unexpected token: " + lookahead.text);
    }

    private Token nextToken() {
      int len = input.length();
      while (pos < len) {
        char c = input.charAt(pos);
        if (Character.isWhitespace(c)) {
          pos++;
          continue;
        }
        if (Character.isDigit(c) || c == '.') {
          int start = pos;
          pos++;
          while (pos < len) {
            char ch = input.charAt(pos);
            if (Character.isDigit(ch) || ch == '.') {
              pos++;
              continue;
            }
            break;
          }
          String text = input.substring(start, pos);
          try {
            double v = Double.parseDouble(text);
            return new Token(TokenType.NUMBER, text, v);
          } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(path + ": invalid number: " + text);
          }
        }
        if (Character.isLetter(c) || c == '_') {
          int start = pos;
          pos++;
          while (pos < len) {
            char ch = input.charAt(pos);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == ':' || ch == '.') {
              pos++;
              continue;
            }
            break;
          }
          String text = input.substring(start, pos);
          return new Token(TokenType.IDENT, text, 0.0);
        }
        pos++;
        switch (c) {
          case '+', '-', '*', '/', '%', '^' -> {
            return new Token(TokenType.OP, String.valueOf(c), 0.0);
          }
          case '(' -> {
            return new Token(TokenType.LPAREN, "(", 0.0);
          }
          case ')' -> {
            return new Token(TokenType.RPAREN, ")", 0.0);
          }
          case ',' -> {
            return new Token(TokenType.COMMA, ",", 0.0);
          }
          default -> throw new IllegalArgumentException(path + ": invalid character: " + c);
        }
      }
      return new Token(TokenType.END, "<end>", 0.0);
    }

    private void consume(TokenType type) {
      if (lookahead.type != type) {
        throw new IllegalArgumentException(path + ": expected " + type + " but got " + lookahead.text);
      }
      lookahead = nextToken();
    }

    private ExprNode unary(String op, ExprNode inner) {
      if ("-".equals(op)) {
        return (ctx, vars) -> -inner.eval(ctx, vars);
      }
      return inner;
    }

    private ExprNode binary(String op, ExprNode a, ExprNode b) {
      return switch (op) {
        case "+" -> (ctx, vars) -> a.eval(ctx, vars) + b.eval(ctx, vars);
        case "-" -> (ctx, vars) -> a.eval(ctx, vars) - b.eval(ctx, vars);
        case "*" -> (ctx, vars) -> a.eval(ctx, vars) * b.eval(ctx, vars);
        case "/" -> (ctx, vars) -> a.eval(ctx, vars) / b.eval(ctx, vars);
        case "%" -> (ctx, vars) -> a.eval(ctx, vars) % b.eval(ctx, vars);
        case "^" -> (ctx, vars) -> Math.pow(a.eval(ctx, vars), b.eval(ctx, vars));
        default -> throw new IllegalArgumentException(path + ": unknown operator: " + op);
      };
    }

    private ExprNode function(String name, List<ExprNode> args) {
      String fn = name.toLowerCase(Locale.ROOT);
      return switch (fn) {
        case "min" -> (ctx, vars) -> {
          if (args.size() < 2) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.min(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "max" -> (ctx, vars) -> {
          if (args.size() < 2) {
            return 0.0;
          }
          double v = args.get(0).eval(ctx, vars);
          for (int i = 1; i < args.size(); i++) {
            v = Math.max(v, args.get(i).eval(ctx, vars));
          }
          return v;
        };
        case "clamp" -> (ctx, vars) -> {
          if (args.size() != 3) {
            return 0.0;
          }
          double x = args.get(0).eval(ctx, vars);
          double lo = args.get(1).eval(ctx, vars);
          double hi = args.get(2).eval(ctx, vars);
          return Math.max(lo, Math.min(hi, x));
        };
        case "lerp" -> (ctx, vars) -> {
          if (args.size() != 3) {
            return 0.0;
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          double t = args.get(2).eval(ctx, vars);
          return a + (b - a) * t;
        };
        case "rand" -> (ctx, vars) -> {
          double r = ctx.rng().nextDouble();
          if (args.isEmpty()) {
            return r;
          }
          if (args.size() == 1) {
            return r * args.get(0).eval(ctx, vars);
          }
          double a = args.get(0).eval(ctx, vars);
          double b = args.get(1).eval(ctx, vars);
          return a + r * (b - a);
        };
        case "abs" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.abs(args.get(0).eval(ctx, vars));
        case "floor" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.floor(args.get(0).eval(ctx, vars));
        case "ceil" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.ceil(args.get(0).eval(ctx, vars));
        case "round" -> (ctx, vars) -> args.isEmpty() ? 0.0 : Math.round(args.get(0).eval(ctx, vars));
        default -> (ctx, vars) -> 0.0;
      };
    }
  }

  private static double numericVar(Object raw, double def) {
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (Exception ignored) {
      return def;
    }
  }

  @SuppressWarnings("unused")
  private static EasingId easingId(Map<String, Object> node, String path) {
    String raw = string(node, "easing", EasingId.IN_OUT_CUBIC.name());
    try {
      return EasingId.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      return EasingId.IN_OUT_CUBIC;
    }
  }

  private static java.util.function.DoubleUnaryOperator easing(Map<String, Object> node, String key, EasingId def) {
    String raw = string(node, key, def.name());
    EasingId id;
    try {
      id = EasingId.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      id = def;
    }
    return switch (id) {
      case LINEAR -> Easings::linear;
      case IN_OUT_CUBIC -> Easings::inOutCubic;
      case OUT_QUAD -> Easings::outQuad;
    };
  }

  @SuppressWarnings("unused")
  private java.util.function.DoubleUnaryOperator easingFromId(EasingId id) {
    return switch (id) {
      case LINEAR -> Easings::linear;
      case IN_OUT_CUBIC -> Easings::inOutCubic;
      case OUT_QUAD -> Easings::outQuad;
    };
  }

  private record PointSpec(NumValue forward, NumValue right, NumValue up, NumValue x, NumValue y, NumValue z) {
  }

  private PointSpec pointSpec(Object raw, String path) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object with offsets (forward/right/up or x/y/z)");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> node = (Map<String, Object>) map;
    NumValue forward = numValue(node, "forward", 0.0, path);
    NumValue right = numValue(node, "right", 0.0, path);
    NumValue up = numValue(node, "up", 0.0, path);
    NumValue x = numValue(node, "x", 0.0, path);
    NumValue y = numValue(node, "y", 0.0, path);
    NumValue z = numValue(node, "z", 0.0, path);
    return new PointSpec(forward, right, up, x, y, z);
  }

  private ShapeTemplate parseShapeTemplate(Map<String, Object> node, String path) {
    Object rawPoints = node.get("points");
    Object rawTriangles = node.get("triangles");
    List<PointSpec> points = java.util.Collections.emptyList();
    List<List<PointSpec>> triangles = java.util.Collections.emptyList();

    if (rawPoints != null) {
      if (!(rawPoints instanceof List<?> list) || list.isEmpty()) {
        throw new IllegalArgumentException(path + ".points: expected a non-empty list");
      }
      var parsed = new ArrayList<PointSpec>(list.size());
      for (int i = 0; i < list.size(); i++) {
        parsed.add(pointSpec(list.get(i), path + ".points[" + i + "]"));
      }
      points = java.util.Collections.unmodifiableList(parsed);
    }

    if (rawTriangles != null) {
      if (!(rawTriangles instanceof List<?> list) || list.isEmpty()) {
        throw new IllegalArgumentException(path + ".triangles: expected a non-empty list");
      }
      var parsed = new ArrayList<List<PointSpec>>(list.size());
      for (int i = 0; i < list.size(); i++) {
        Object triRaw = list.get(i);
        if (!(triRaw instanceof List<?> triList) || triList.size() < 3) {
          throw new IllegalArgumentException(path + ".triangles[" + i + "]: expected a list with 3 point objects");
        }
        var tri = new ArrayList<PointSpec>(3);
        for (int p = 0; p < 3; p++) {
          tri.add(pointSpec(triList.get(p), path + ".triangles[" + i + "][" + p + "]"));
        }
        parsed.add(java.util.Collections.unmodifiableList(tri));
      }
      triangles = java.util.Collections.unmodifiableList(parsed);
    }

    if (points.isEmpty() && triangles.isEmpty()) {
      throw new IllegalArgumentException(path + ": shape requires points or triangles");
    }

    return new ShapeTemplate(points, triangles);
  }

  private static Location pointAt(CastContext ctx, PointSpec spec) {
    Vector forward = ctx.direction().clone();
    if (forward.lengthSquared() < 1e-9) {
      forward.setX(0).setY(0).setZ(1);
    }
    forward.normalize();

    Vector up = new Vector(0, 1, 0);
    Vector right = forward.clone().crossProduct(up);
    if (right.lengthSquared() < 1e-9) {
      right = new Vector(1, 0, 0);
    } else {
      right.normalize();
    }

    Location out = ctx.origin().clone();
    out.add(forward.clone().multiply(evalDouble(spec.forward(), ctx)));
    out.add(right.clone().multiply(evalDouble(spec.right(), ctx)));
    out.add(0, evalDouble(spec.up(), ctx), 0);
    out.add(evalDouble(spec.x(), ctx), evalDouble(spec.y(), ctx), evalDouble(spec.z(), ctx));
    return out;
  }

  private enum AtMode {
    ORIGIN,
    LAST_HIT,
    LAST_ENTITY
  }

  private enum AnchorMode {
    ORIGIN,
    CASTER,
    LAST_ENTITY,
    LAST_HIT,
    PROJECTILE
  }

  private enum AnchorPoint {
    ORIGIN,
    BODY,
    EYES,
    HEAD,
    MAIN_HAND,
    OFF_HAND,
    HIT,
    BLOCK_FACE
  }

  private static AtMode parseAt(String raw, String path) {
    String s = (raw == null ? "origin" : raw).trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "origin" -> AtMode.ORIGIN;
      case "last_hit", "last-hit" -> AtMode.LAST_HIT;
      case "last_entity", "last-entity" -> AtMode.LAST_ENTITY;
      default -> throw new IllegalArgumentException(path + ": invalid at=" + raw + " (use origin|last_hit|last_entity)");
    };
  }

  private static AnchorMode parseAnchorMode(String raw, String path) {
    String s = (raw == null ? "origin" : raw).trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "origin" -> AnchorMode.ORIGIN;
      case "caster", "self" -> AnchorMode.CASTER;
      case "target", "last_entity", "last-entity" -> AnchorMode.LAST_ENTITY;
      case "last_hit", "last-hit", "projectile_hit", "projectile-hit" -> AnchorMode.LAST_HIT;
      case "projectile", "projectile_frame", "projectile-frame" -> AnchorMode.PROJECTILE;
      default -> throw new IllegalArgumentException(path + ": invalid anchor=" + raw
          + " (use origin|caster|last_entity|last_hit|projectile)");
    };
  }

  private static AnchorPoint parseAnchorPoint(String raw, AnchorMode anchor, String path) {
    if (raw == null || raw.isBlank()) {
      return switch (anchor) {
        case ORIGIN -> AnchorPoint.ORIGIN;
        case LAST_HIT -> AnchorPoint.HIT;
        default -> AnchorPoint.BODY;
      };
    }
    String s = raw.trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "origin" -> AnchorPoint.ORIGIN;
      case "body" -> AnchorPoint.BODY;
      case "eyes", "eye" -> AnchorPoint.EYES;
      case "head" -> AnchorPoint.HEAD;
      case "main_hand", "mainhand", "hand", "right_hand" -> AnchorPoint.MAIN_HAND;
      case "off_hand", "offhand", "left_hand" -> AnchorPoint.OFF_HAND;
      case "hit" -> AnchorPoint.HIT;
      case "block_face", "blockface", "face" -> AnchorPoint.BLOCK_FACE;
      default -> throw new IllegalArgumentException(path + ": invalid point=" + raw
          + " (use body|eyes|head|main_hand|off_hand|hit|block_face)");
    };
  }

  private static dev.patric.dungeonsreborn.effects.actions.Actions.MotionMode parseMotionMode(String raw, String path) {
    String s = (raw == null ? "translate" : raw).trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "translate" -> dev.patric.dungeonsreborn.effects.actions.Actions.MotionMode.TRANSLATE;
      case "follow" -> dev.patric.dungeonsreborn.effects.actions.Actions.MotionMode.FOLLOW;
      case "orbit" -> dev.patric.dungeonsreborn.effects.actions.Actions.MotionMode.ORBIT;
      case "drift" -> dev.patric.dungeonsreborn.effects.actions.Actions.MotionMode.DRIFT;
      default -> throw new IllegalArgumentException(path + ": invalid mode=" + raw + " (use translate|follow|orbit|drift)");
    };
  }

  private static Location resolveAt(CastContext ctx, AtMode mode) {
    return switch (mode) {
      case ORIGIN -> ctx.origin();
      case LAST_ENTITY -> {
        Object v = ctx.state().get(YAML_LAST_ENTITY);
        if (v instanceof LivingEntity living) {
          yield living.getLocation();
        }
        yield ctx.origin();
      }
      case LAST_HIT -> {
        Object hit = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
        if (hit instanceof dev.patric.dungeonsreborn.effects.projectile.ProjectileHit ph) {
          yield ph.location();
        }
        yield ctx.origin();
      }
    };
  }

  private static Frame frameForAnchor(AnchorMode anchor, AnchorPoint point) {
    return new Frame() {
      @Override
      public Location location(CastContext ctx) {
        return switch (anchor) {
          case ORIGIN -> ctx.origin().clone();
          case CASTER -> entityAnchorLocation(ctx.caster(), point);
          case LAST_ENTITY -> {
            LivingEntity entity = lastEntity(ctx);
            if (entity != null) {
              yield entityAnchorLocation(entity, point);
            }
            yield ctx.origin().clone();
          }
          case LAST_HIT -> {
            Object hit = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
            if (hit instanceof dev.patric.dungeonsreborn.effects.projectile.ProjectileHit ph) {
              yield hitAnchorLocation(ph, point);
            }
            yield ctx.origin().clone();
          }
          case PROJECTILE -> {
            Object frame = ctx.state().get(Vars.PROJECTILE_FRAME);
            if (frame instanceof Frame live) {
              Location loc = live.location(ctx);
              if (loc != null) {
                yield loc;
              }
            }
            yield ctx.origin().clone();
          }
        };
      }

      @Override
      public Vector direction(CastContext ctx) {
        return switch (anchor) {
          case ORIGIN -> ctx.direction().clone();
          case CASTER -> entityAnchorDirection(ctx.caster(), point);
          case LAST_ENTITY -> {
            LivingEntity entity = lastEntity(ctx);
            if (entity != null) {
              yield entityAnchorDirection(entity, point);
            }
            yield ctx.direction().clone();
          }
          case LAST_HIT -> {
            Object hit = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
            if (hit instanceof dev.patric.dungeonsreborn.effects.projectile.ProjectileHit ph) {
              yield safeDirection(ph.direction(), ctx.direction());
            }
            yield ctx.direction().clone();
          }
          case PROJECTILE -> {
            Object frame = ctx.state().get(Vars.PROJECTILE_FRAME);
            if (frame instanceof Frame live) {
              Vector dir = live.direction(ctx);
              if (dir != null) {
                yield safeDirection(dir, ctx.direction());
              }
            }
            yield ctx.direction().clone();
          }
        };
      }
    };
  }

  private static Location entityAnchorLocation(LivingEntity entity, AnchorPoint point) {
    return switch (point) {
      case ORIGIN, BODY -> entity.getLocation().clone();
      case EYES, HEAD -> {
        if (entity instanceof Player player) {
          yield player.getEyeLocation();
        }
        Location base = entity.getLocation();
        yield base.add(0.0, entity.getHeight() * 0.9, 0.0);
      }
      case MAIN_HAND -> handLocation(entity, false);
      case OFF_HAND -> handLocation(entity, true);
      case HIT, BLOCK_FACE -> entity.getLocation().clone();
    };
  }

  private static Vector entityAnchorDirection(LivingEntity entity, AnchorPoint point) {
    if (point == AnchorPoint.EYES || point == AnchorPoint.HEAD) {
      if (entity instanceof Player player) {
        return safeDirection(player.getEyeLocation().getDirection(), entity.getLocation().getDirection());
      }
    }
    return safeDirection(entity.getLocation().getDirection(), new Vector(0, 0, 1));
  }

  private static Location handLocation(LivingEntity entity, boolean offHand) {
    Location base = entity.getLocation();
    Vector dir = safeDirection(base.getDirection(), new Vector(0, 0, 1));
    Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
    if (right.lengthSquared() < 1e-9) {
      right = new Vector(1, 0, 0);
    } else {
      right.normalize();
    }
    double side = offHand ? 0.35 : -0.35;
    Location out = base.clone().add(0.0, entity.getHeight() * 0.75, 0.0);
    out.add(right.multiply(side));
    out.add(dir.multiply(0.2));
    return out;
  }

  private static Location hitAnchorLocation(dev.patric.dungeonsreborn.effects.projectile.ProjectileHit hit, AnchorPoint point) {
    if (point == AnchorPoint.BLOCK_FACE && hit.hitBlock() != null) {
      Location center = hit.hitBlock().getLocation().add(0.5, 0.5, 0.5);
      Vector dir = safeDirection(hit.direction(), new Vector(0, 0, 1));
      return center.add(dir.multiply(0.5));
    }
    return hit.location().clone();
  }

  private static Vector safeDirection(Vector dir, Vector fallback) {
    if (dir == null) {
      return fallback.clone();
    }
    if (dir.lengthSquared() < 1e-9) {
      return fallback.clone();
    }
    return dir.clone().normalize();
  }

  private static Location resolveAtWithOffsets(CastContext ctx, AtMode mode, NumValue forward, NumValue right, NumValue up) {
    Location base = resolveAt(ctx, mode);
    if (base == null) {
      return null;
    }
    double f = evalDouble(forward, ctx);
    double r = evalDouble(right, ctx);
    double u = evalDouble(up, ctx);
    if (f == 0.0 && r == 0.0 && u == 0.0) {
      return base;
    }
    Vector dir = ctx.direction().clone();
    if (dir.lengthSquared() < 1e-9) {
      dir = new Vector(0, 0, 1);
    } else {
      dir.normalize();
    }
    Vector upVec = new Vector(0, 1, 0);
    Vector rightVec = dir.clone().crossProduct(upVec);
    if (rightVec.lengthSquared() < 1e-9) {
      rightVec = new Vector(1, 0, 0);
    } else {
      rightVec.normalize();
    }
    Location out = base.clone();
    out.add(dir.clone().multiply(f));
    out.add(rightVec.clone().multiply(r));
    out.add(0, u, 0);
    return out;
  }

  private static LivingEntity lastEntity(CastContext ctx) {
    Object v = ctx.state().get(YAML_LAST_ENTITY);
    if (v instanceof LivingEntity living) {
      return living;
    }
    Object hit = ctx.state().get(Vars.PROJECTILE_LAST_HIT);
    if (hit instanceof dev.patric.dungeonsreborn.effects.projectile.ProjectileHit ph && ph.hitEntity() != null) {
      return ph.hitEntity();
    }
    return null;
  }

  private static Player targetPlayer(CastContext ctx) {
    LivingEntity target = lastEntity(ctx);
    if (target instanceof Player player) {
      return player;
    }
    if (ctx.caster() instanceof Player player) {
      return player;
    }
    return null;
  }

  private static PotionEffectType potionEffectValue(Map<String, Object> node, String key, String path) {
    String raw = requireString(node, key, path);
    String s = raw.trim();
    NamespacedKey ns;
    if (s.contains(":")) {
      ns = NamespacedKey.fromString(s);
    } else {
      ns = NamespacedKey.fromString("minecraft:" + s.toLowerCase(Locale.ROOT));
    }
    if (ns == null) {
      throw new IllegalArgumentException(path + ": invalid effect key: " + raw);
    }
    PotionEffectType type = Registry.EFFECT.get(ns);
    if (type == null) {
      throw new IllegalArgumentException(path + ": unknown effect: " + raw);
    }
    return type;
  }

  private static final MiniMessage MINI = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private static Component richText(String raw) {
    if (raw == null) {
      return Component.empty();
    }
    // If user explicitly uses legacy codes, honor them.
    if (raw.indexOf('§') >= 0) {
      return LEGACY.deserialize(raw);
    }
    try {
      return MINI.deserialize(raw);
    } catch (Exception ignored) {
      return LEGACY.deserialize(raw.replace('&', '§'));
    }
  }

  private Component renderText(String raw, CastContext ctx) {
    return richText(interpolate(raw, ctx));
  }

  private String interpolate(String raw, CastContext ctx) {
    if (raw == null || raw.indexOf('{') < 0) {
      return raw;
    }
    StringBuilder out = new StringBuilder(raw.length() + 16);
    int i = 0;
    while (i < raw.length()) {
      int open = raw.indexOf('{', i);
      if (open < 0) {
        out.append(raw, i, raw.length());
        break;
      }
      int close = raw.indexOf('}', open + 1);
      if (close < 0) {
        out.append(raw, i, raw.length());
        break;
      }
      out.append(raw, i, open);
      String token = raw.substring(open + 1, close).trim();
      out.append(resolveToken(token, ctx, raw.substring(open, close + 1)));
      i = close + 1;
    }
    return out.toString();
  }

  private String resolveToken(String token, CastContext ctx, String fallback) {
    if (token.isBlank()) {
      return fallback;
    }
    String lower = token.toLowerCase(Locale.ROOT);
    switch (lower) {
      case "ability", "abilityid" -> {
        return ctx.abilityId();
      }
      case "cast", "castid" -> {
        return ctx.castId().toString();
      }
      case "tick" -> {
        return String.valueOf(ctx.engine().tickNow());
      }
      case "player", "caster" -> {
        return ctx.caster().getName();
      }
      case "target" -> {
        LivingEntity target = lastEntity(ctx);
        return target == null ? "" : target.getName();
      }
      case "target_type", "targettype" -> {
        LivingEntity target = lastEntity(ctx);
        return target == null ? "" : target.getType().name();
      }
      case "mana" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return String.valueOf((int) Math.floor(ctx.engine().manaProvider().get(player)));
        }
        return "";
      }
      case "mana_max", "manamax" -> {
        if (ctx.caster() instanceof Player player && ctx.engine().manaProvider() != null) {
          return String.valueOf((int) Math.floor(ctx.engine().manaProvider().getMax(player)));
        }
        return "";
      }
      default -> {
      }
    }

    if (lower.startsWith("var:")) {
      String[] parts = token.split(":", 3);
      if (parts.length == 2) {
        String key = parts[1].trim();
        if (key.isBlank()) {
          return fallback;
        }
        Object v = vars(ctx, VarScope.CAST).get(key);
        if (v == null) {
          v = vars(ctx, VarScope.PLAYER).get(key);
        }
        return v == null ? "" : String.valueOf(v);
      }
      if (parts.length == 3) {
        VarScope scope = parseVarScope(parts[1].trim(), "token", VarScope.CAST);
        String key = parts[2].trim();
        if (key.isBlank()) {
          return fallback;
        }
        Object v = vars(ctx, scope).get(key);
        return v == null ? "" : String.valueOf(v);
      }
    }

    return fallback;
  }

  private static Object parseParticleData(Map<String, Object> node, Particle particle, String path) {
    if (!node.containsKey("data")) {
      if (particleRequiresData(particle)) {
        throw new IllegalArgumentException(path + ".data: required for " + particle.name());
      }
      return null;
    }
    return parseParticleDataValue(node.get("data"), particle, path + ".data");
  }

  private static boolean particleRequiresData(Particle particle) {
    return switch (particle) {
      case DUST, DUST_COLOR_TRANSITION,
           ITEM,
           BLOCK, FALLING_DUST, DUST_PILLAR, BLOCK_CRUMBLE, BLOCK_MARKER,
           VIBRATION, TRAIL -> true;
      default -> false;
    };
  }

  private static Object resolveParticleData(Object data, CastContext ctx, Location loc) {
    if (data instanceof java.util.function.BiFunction<?, ?, ?> fn) {
      @SuppressWarnings("unchecked")
      java.util.function.BiFunction<CastContext, Location, Object> resolver =
          (java.util.function.BiFunction<CastContext, Location, Object>) fn;
      return resolver.apply(ctx, loc);
    }
    return data;
  }

  private static Object parseParticleDataValue(Object raw, Particle particle, String path) {
    return switch (particle) {
      case DUST -> parseDustOptions(raw, path);
      case DUST_COLOR_TRANSITION -> parseDustTransition(raw, path);
      case ENTITY_EFFECT, FLASH, TINTED_LEAVES -> parseColor(raw, path);
      case DRAGON_BREATH, SCULK_CHARGE -> parseFloatData(raw, path);
      case SHRIEK -> parseIntData(raw, path);
      case ITEM -> parseParticleItem(raw, path);
      case BLOCK, FALLING_DUST, DUST_PILLAR, BLOCK_CRUMBLE, BLOCK_MARKER -> parseBlockData(raw, path);
      case VIBRATION -> parseVibrationData(raw, path);
      case TRAIL -> parseTrailData(raw, path);
      case EFFECT, INSTANT_EFFECT -> throw new IllegalArgumentException(
          path + ": particle " + particle.name() + " uses Particle.Spell, which is not available in Paper 1.21.8");
      default -> throw new IllegalArgumentException(path + ": particle " + particle.name() + " does not support data");
    };
  }

  private static java.util.function.BiFunction<CastContext, Location, Object> parseVibrationData(Object raw, String path) {
    Map<String, Object> data = castMap(raw, path);
    Object arrivalRaw = pick(data, "arrivalTicks", "arrival", "ticks");
    int arrivalTicks = arrivalRaw == null ? 20 : parseInt(arrivalRaw, path + ".arrivalTicks");
    String destRaw = pick(data, "destinationAt", "destination", "dest", "at") instanceof Object v ? String.valueOf(v) : "origin";
    AtMode at = parseAt(destRaw, path + ".destinationAt");
    boolean preferEntity = bool(data, "preferEntity", true);
    if (arrivalTicks < 0) {
      throw new IllegalArgumentException(path + ".arrivalTicks: must be >= 0");
    }
    return (ctx, loc) -> {
      Vibration.Destination destination;
      if (preferEntity && at == AtMode.LAST_ENTITY) {
        LivingEntity entity = lastEntity(ctx);
        if (entity != null) {
          destination = new Vibration.Destination.EntityDestination(entity);
          return new Vibration(destination, arrivalTicks);
        }
      }
      Location target = resolveAtWithEntity(ctx, at);
      if (target == null || target.getWorld() == null) {
        target = loc == null ? ctx.origin() : loc;
      }
      destination = new Vibration.Destination.BlockDestination(target);
      return new Vibration(destination, arrivalTicks);
    };
  }

  private static java.util.function.BiFunction<CastContext, Location, Object> parseTrailData(Object raw, String path) {
    Map<String, Object> data = castMap(raw, path);
    Object colorRaw = pick(data, "color", "colour");
    Color color = parseColor(colorRaw != null ? colorRaw : data, path + ".color");
    int duration = intValue(data, "durationTicks", intValue(data, "duration", 20));
    if (duration <= 0) {
      throw new IllegalArgumentException(path + ".durationTicks: must be > 0");
    }
    String targetRaw = pick(data, "targetAt", "destinationAt", "target", "at") instanceof Object v ? String.valueOf(v) : "origin";
    AtMode at = parseAt(targetRaw, path + ".targetAt");
    return (ctx, loc) -> {
      Location target = resolveAtWithEntity(ctx, at);
      if (target == null || target.getWorld() == null) {
        target = loc == null ? ctx.origin() : loc;
      }
      return new Particle.Trail(target, color, duration);
    };
  }

  private static Location resolveAtWithEntity(CastContext ctx, AtMode mode) {
    if (mode == AtMode.LAST_ENTITY) {
      LivingEntity entity = lastEntity(ctx);
      if (entity != null) {
        return entity.getLocation();
      }
    }
    return resolveAt(ctx, mode);
  }

  private static Map<DamageType, Double> parseResistanceMap(Object raw, String path) {
    if (raw == null) {
      return Map.of();
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseResistanceMap(sec.getValues(false), path);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object");
    }
    Map<DamageType, Double> out = new java.util.EnumMap<>(DamageType.class);
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = String.valueOf(entry.getKey());
      DamageType type = parseDamageTypeKey(key, path + "." + key);
      double value = doubleFrom(entry.getValue(), path + "." + key);
      out.put(type, value);
    }
    return out;
  }

  private static Map<Attribute, Double> parseMinionStatOverrides(Object raw, String path) {
    if (raw == null) {
      return Map.of();
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseMinionStatOverrides(sec.getValues(false), path);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object");
    }
    Map<String, Object> node = normalizeMap(map);
    Map<Attribute, Double> out = new java.util.HashMap<>();
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      String key = entry.getKey();
      Attribute attr = parseAttributeKey(key, path + "." + key);
      double value = doubleFrom(entry.getValue(), path + "." + key);
      out.put(attr, value);
    }
    return out;
  }

  private static Attribute parseAttributeKey(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": empty attribute");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    List<String> candidates = new ArrayList<>();
    if (normalized.contains(":")) {
      candidates.add(normalized);
      String[] parts = normalized.split(":", 2);
      if (parts.length == 2) {
        candidates.add(parts[0] + ":" + parts[1].replace('_', '.'));
      }
    } else {
      candidates.add("minecraft:" + normalized);
      if (normalized.contains("_")) {
        candidates.add("minecraft:" + normalized.replace('_', '.'));
      }
      if (normalized.contains(".")) {
        candidates.add("minecraft:" + normalized.replace('.', '_'));
      }
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
    if (registry != null) {
      for (String candidate : candidates) {
        NamespacedKey key = NamespacedKey.fromString(candidate);
        if (key == null) {
          continue;
        }
        Attribute attr = registry.get(key);
        if (attr != null) {
          return attr;
        }
      }
    }
    String needle = normalized;
    if (needle.contains(":")) {
      String[] parts = needle.split(":", 2);
      needle = parts.length == 2 ? parts[1] : needle;
    }
    String needleUnderscore = needle.replace('.', '_');
    String needleDot = needle.replace('_', '.');
    String needleNoGeneric = needle;
    if (needleNoGeneric.startsWith("generic.")) {
      needleNoGeneric = needleNoGeneric.substring("generic.".length());
    } else if (needleNoGeneric.startsWith("generic_")) {
      needleNoGeneric = needleNoGeneric.substring("generic_".length());
    }
    String suggestion = null;
    if (registry != null) {
      for (Attribute attr : registry) {
        NamespacedKey key = attr.getKey();
        if (key == null) {
          continue;
        }
        String keyPath = key.getKey().toLowerCase(Locale.ROOT);
        String keyUnderscore = keyPath.replace('.', '_');
        String keyDot = keyPath.replace('_', '.');
        String keyNoGeneric = keyPath;
        if (keyNoGeneric.startsWith("generic.")) {
          keyNoGeneric = keyNoGeneric.substring("generic.".length());
        } else if (keyNoGeneric.startsWith("generic_")) {
          keyNoGeneric = keyNoGeneric.substring("generic_".length());
        }
        if (keyPath.equals(needle) || keyPath.equals(needleUnderscore) || keyPath.equals(needleDot)
            || keyUnderscore.equals(needle) || keyUnderscore.equals(needleUnderscore) || keyUnderscore.equals(needleDot)
            || keyDot.equals(needle) || keyDot.equals(needleUnderscore) || keyDot.equals(needleDot)
            || keyNoGeneric.equals(needleNoGeneric) || keyNoGeneric.equals(needleUnderscore)
            || keyNoGeneric.equals(needleDot)) {
          return attr;
        }
        if (suggestion == null && (keyPath.contains(needleNoGeneric) || keyUnderscore.contains(needleNoGeneric)
            || keyDot.contains(needleNoGeneric))) {
          suggestion = key.getKey();
        }
      }
    }
    String msg = path + ": invalid attribute=" + raw;
    if (suggestion != null) {
      msg += " (did you mean " + suggestion + "?)";
    }
    throw new IllegalArgumentException(msg);
  }

  private static MobParticlesSpec parseMinionParticles(Object raw, String path) {
    if (raw == null) {
      return null;
    }
    Map<String, Object> node;
    if (raw instanceof ConfigurationSection sec) {
      node = normalizeMap(sec.getValues(false));
    } else if (raw instanceof Map<?, ?> map) {
      node = normalizeMap(map);
    } else {
      throw new IllegalArgumentException(path + ": expected object");
    }
    String particleRaw = requireString(node, "particle", path + ".particle");
    org.bukkit.Particle particle;
    try {
      particle = org.bukkit.Particle.valueOf(particleRaw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(particleRaw, org.bukkit.Particle.class);
      String msg = path + ".particle: invalid particle=" + particleRaw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
    int count = intValue(node, "count", 1);
    double offsetX = doubleValue(node, "offsetX", 0.0);
    double offsetY = doubleValue(node, "offsetY", 0.0);
    double offsetZ = doubleValue(node, "offsetZ", 0.0);
    double extra = doubleValue(node, "extra", 0.0);
    return new MobParticlesSpec(particle, count, offsetX, offsetY, offsetZ, extra);
  }

  private static MinionScaling parseMinionScaling(Object raw, String path) {
    if (raw == null) {
      return MinionScaling.NONE;
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseMinionScaling(sec.getValues(false), path);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object");
    }
    Map<String, Object> scale = normalizeMap(map);
    double healthPerLevel = doubleValue(scale, "healthPerLevel", 0.0);
    double damagePerLevel = doubleValue(scale, "damagePerLevel", 0.0);
    double healthPerMaxHealth = doubleValue(scale, "healthPerMaxHealth", 0.0);
    double damagePerMaxHealth = doubleValue(scale, "damagePerMaxHealth", 0.0);
    double healthPerManaMax = doubleValue(scale, "healthPerManaMax", 0.0);
    double damagePerManaMax = doubleValue(scale, "damagePerManaMax", 0.0);
    return new MinionScaling(healthPerLevel, damagePerLevel, healthPerMaxHealth, damagePerMaxHealth, healthPerManaMax, damagePerManaMax);
  }

  private static MinionOwnerScalingSpec parseMinionOwnerScaling(Object raw, String path) {
    if (raw == null) {
      return MinionOwnerScalingSpec.NONE;
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseMinionOwnerScaling(sec.getValues(false), path);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object");
    }
    Map<String, Object> node = normalizeMap(map);
    double levelMultiplier = doubleValue(node, "levelMultiplier", doubleValue(node, "level", 0.0));
    double strengthMultiplier = doubleValue(node, "strengthMultiplier", doubleValue(node, "strength", 0.0));
    double dexterityMultiplier = doubleValue(node, "dexterityMultiplier", doubleValue(node, "dexterity", 0.0));
    double intelligenceMultiplier = doubleValue(node, "intelligenceMultiplier", doubleValue(node, "intelligence", 0.0));
    double vitalityMultiplier = doubleValue(node, "vitalityMultiplier", doubleValue(node, "vitality", 0.0));
    double maxManaMultiplier = doubleValue(node, "maxManaMultiplier", doubleValue(node, "maxMana", 0.0));
    double maxHealthMultiplier = doubleValue(node, "maxHealthMultiplier", doubleValue(node, "maxHealth", 0.0));
    return new MinionOwnerScalingSpec(levelMultiplier, strengthMultiplier, dexterityMultiplier,
        intelligenceMultiplier, vitalityMultiplier, maxManaMultiplier, maxHealthMultiplier);
  }

  private static MinionScalingLimits parseMinionScalingLimits(Object raw, String path) {
    if (raw == null) {
      return MinionScalingLimits.NONE;
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseMinionScalingLimits(sec.getValues(false), path);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": expected object");
    }
    Map<String, Object> node = normalizeMap(map);
    double maxBonusHealth = doubleValue(node, "maxBonusHealth", 0.0);
    double maxBonusDamage = doubleValue(node, "maxBonusDamage", 0.0);
    double decayExponent = doubleValue(node, "decayExponent", 0.0);
    return new MinionScalingLimits(maxBonusHealth, maxBonusDamage, decayExponent);
  }

  private static MinionFormation parseMinionFormation(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return MinionFormation.RANDOM;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MinionFormation.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(raw, MinionFormation.class);
      String msg = path + ": invalid formation=" + raw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private record MinionSummonCostSpec(String type, String resourceId, NumValue amount, NumValue multiplier,
                                      NumValue add, boolean allowBreak) {
  }

  private java.util.List<MinionSummonCostSpec> parseMinionSummonCosts(Object raw, String path) {
    if (raw == null) {
      return java.util.List.of();
    }
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (!(raw instanceof Iterable<?> list)) {
      throw new IllegalArgumentException(path + ": expected list");
    }
    java.util.List<MinionSummonCostSpec> out = new java.util.ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String base = path + "[" + index + "]";
      Map<String, Object> node = castMap(entry, base);
      String type = requireString(node, "type", base + ".type").toLowerCase(Locale.ROOT);
      NumValue multiplier = numValue(node, "multiplier", 1.0, base + ".multiplier");
      NumValue add = numValue(node, "add", 0.0, base + ".add");
      switch (type) {
        case "mana" -> {
          NumValue amount = requireNumValue(node, "amount", base + ".amount");
          out.add(new MinionSummonCostSpec(type, null, amount, multiplier, add, false));
        }
        case "resource" -> {
          String resourceId = requireString(node, "resource", base + ".resource");
          NumValue amount = requireNumValue(node, "amount", base + ".amount");
          out.add(new MinionSummonCostSpec(type, resourceId, amount, multiplier, add, false));
        }
        case "consume_item", "consume_main_hand" -> {
          NumValue amount = requireNumValue(node, "amount", base + ".amount");
          out.add(new MinionSummonCostSpec(type, null, amount, multiplier, add, false));
        }
        case "durability", "durability_main_hand" -> {
          NumValue amount = requireNumValue(node, "damage", base + ".damage");
          boolean allowBreak = bool(node, "allowBreak", false);
          out.add(new MinionSummonCostSpec(type, null, amount, multiplier, add, allowBreak));
        }
        default -> throw new IllegalArgumentException(base + ".type: unknown type: " + type);
      }
      index++;
    }
    return java.util.List.copyOf(out);
  }

  private static boolean applyMinionSummonCosts(CastContext ctx, java.util.List<MinionSummonCostSpec> costs) {
    if (costs == null || costs.isEmpty()) {
      return true;
    }
    for (MinionSummonCostSpec cost : costs) {
      double value = evalDouble(cost.amount(), ctx);
      value = value * evalDouble(cost.multiplier(), ctx) + evalDouble(cost.add(), ctx);
      if (!(value > 0.0)) {
        continue;
      }
      dev.patric.dungeonsreborn.effects.costs.Cost entry;
      switch (cost.type()) {
        case "mana" -> entry = Costs.mana(value);
        case "resource" -> entry = Costs.resource(cost.resourceId(), value);
        case "consume_item", "consume_main_hand" -> entry = Costs.consumeMainHand(Math.max(1, (int) Math.round(value)));
        case "durability", "durability_main_hand" ->
            entry = Costs.durabilityMainHand(Math.max(1, (int) Math.round(value)), cost.allowBreak());
        default -> throw new IllegalArgumentException("Unsupported summon cost: " + cost.type());
      }
      Component fail = entry.tryApply(ctx);
      if (fail != null) {
        if (ctx.caster() instanceof Player player) {
          player.sendMessage(fail);
        }
        return false;
      }
    }
    return true;
  }

  private static MinionMode parseMinionMode(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MinionMode.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(raw, MinionMode.class);
      String msg = path + ": invalid mode=" + raw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static MinionTargetRules parseMinionTargetRules(Map<String, Object> node, String path) {
    if (node == null) {
      return MinionTargetRules.DEFAULT;
    }
    Object raw = node.get("targeting");
    if (raw instanceof ConfigurationSection section) {
      raw = section.getValues(false);
    }
    if (raw instanceof Map<?, ?> values) {
      Map<String, Object> map = castMap(values, path);
      boolean allowPvp = bool(map, "allowPvp", bool(node, "allowPvp", false));
      boolean allowPartyTargets = bool(map, "allowPartyTargets", bool(node, "allowPartyTargets", false));
      boolean shareOwnerAggro = bool(map, "shareOwnerAggro", bool(node, "shareOwnerAggro", true));
      double maxDistanceFromOwner = doubleValue(map, "maxDistanceFromOwner", doubleValue(node, "maxDistanceFromOwner", 0.0));
      return new MinionTargetRules(allowPvp, allowPartyTargets, shareOwnerAggro, maxDistanceFromOwner);
    }
    boolean allowPvp = bool(node, "allowPvp", false);
    boolean allowPartyTargets = bool(node, "allowPartyTargets", false);
    boolean shareOwnerAggro = bool(node, "shareOwnerAggro", true);
    double maxDistanceFromOwner = doubleValue(node, "maxDistanceFromOwner", 0.0);
    return new MinionTargetRules(allowPvp, allowPartyTargets, shareOwnerAggro, maxDistanceFromOwner);
  }

  private static java.util.List<MinionPassiveSpec> parseMinionPassives(Object raw, String path) {
    if (raw == null) {
      return java.util.List.of();
    }
    if (!(raw instanceof Iterable<?> list)) {
      throw new IllegalArgumentException(path + ": expected list");
    }
    java.util.List<MinionPassiveSpec> out = new java.util.ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String base = path + "[" + index + "]";
      if (entry instanceof String abilityId) {
        if (abilityId.isBlank()) {
          throw new IllegalArgumentException(base + ": ability is blank");
        }
        out.add(new MinionPassiveSpec(Ids.normalize(abilityId), 40L));
      } else if (entry instanceof Map<?, ?> map) {
        Map<String, Object> node = castMap(map, base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long period = longValue(node, "periodTicks", 40L);
        if (period <= 0) {
          throw new IllegalArgumentException(base + ".periodTicks: must be > 0");
        }
        out.add(new MinionPassiveSpec(Ids.normalize(abilityId), period));
      } else if (entry instanceof ConfigurationSection sec) {
        Map<String, Object> node = castMap(sec.getValues(false), base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long period = longValue(node, "periodTicks", 40L);
        if (period <= 0) {
          throw new IllegalArgumentException(base + ".periodTicks: must be > 0");
        }
        out.add(new MinionPassiveSpec(Ids.normalize(abilityId), period));
      } else {
        throw new IllegalArgumentException(base + ": expected string or object");
      }
      index++;
    }
    return java.util.List.copyOf(out);
  }

  private static java.util.List<MinionSpecialAttackSpec> parseMinionSpecialAttacks(Object raw, String path) {
    if (raw == null) {
      return java.util.List.of();
    }
    if (!(raw instanceof Iterable<?> list)) {
      throw new IllegalArgumentException(path + ": expected list");
    }
    java.util.List<MinionSpecialAttackSpec> out = new java.util.ArrayList<>();
    int index = 0;
    for (Object entry : list) {
      String base = path + "[" + index + "]";
      if (entry instanceof String abilityId) {
        if (abilityId.isBlank()) {
          throw new IllegalArgumentException(base + ": ability is blank");
        }
        out.add(new MinionSpecialAttackSpec(Ids.normalize(abilityId), 60L, 1.0, true, 1.0, 0.0));
      } else if (entry instanceof Map<?, ?> map) {
        Map<String, Object> node = castMap(map, base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long cooldown = longValue(node, "cooldownTicks", 60L);
        double chance = doubleValue(node, "chance", 1.0);
        boolean requireTarget = bool(node, "requireTarget", true);
        double costMultiplier = doubleValue(node, "costMultiplier", 1.0);
        double costAdd = doubleValue(node, "costAdd", 0.0);
        if (cooldown <= 0) {
          throw new IllegalArgumentException(base + ".cooldownTicks: must be > 0");
        }
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
          throw new IllegalArgumentException(base + ".chance: must be in [0,1]");
        }
        out.add(new MinionSpecialAttackSpec(Ids.normalize(abilityId), cooldown, chance, requireTarget,
            costMultiplier, costAdd));
      } else if (entry instanceof ConfigurationSection sec) {
        Map<String, Object> node = castMap(sec.getValues(false), base);
        String abilityId = requireString(node, "ability", base + ".ability");
        long cooldown = longValue(node, "cooldownTicks", 60L);
        double chance = doubleValue(node, "chance", 1.0);
        boolean requireTarget = bool(node, "requireTarget", true);
        double costMultiplier = doubleValue(node, "costMultiplier", 1.0);
        double costAdd = doubleValue(node, "costAdd", 0.0);
        if (cooldown <= 0) {
          throw new IllegalArgumentException(base + ".cooldownTicks: must be > 0");
        }
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
          throw new IllegalArgumentException(base + ".chance: must be in [0,1]");
        }
        out.add(new MinionSpecialAttackSpec(Ids.normalize(abilityId), cooldown, chance, requireTarget,
            costMultiplier, costAdd));
      } else {
        throw new IllegalArgumentException(base + ": expected string or object");
      }
      index++;
    }
    return java.util.List.copyOf(out);
  }

  private static java.util.Set<DamageType> parseDamageTypeSet(Object raw, String path) {
    if (raw == null) {
      return java.util.Set.of();
    }
    java.util.EnumSet<DamageType> out = java.util.EnumSet.noneOf(DamageType.class);
    if (raw instanceof Iterable<?> list) {
      for (Object item : list) {
        if (item == null) {
          continue;
        }
        String key = String.valueOf(item);
        out.add(parseDamageTypeKey(key, path));
      }
      return java.util.Set.copyOf(out);
    }
    String text = String.valueOf(raw);
    if (text.indexOf(',') >= 0) {
      for (String part : text.split(",")) {
        String key = part.trim();
        if (!key.isEmpty()) {
          out.add(parseDamageTypeKey(key, path));
        }
      }
      return java.util.Set.copyOf(out);
    }
    out.add(parseDamageTypeKey(text, path));
    return java.util.Set.copyOf(out);
  }

  private static double doubleFrom(Object raw, String path) {
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(path + ": expected number");
    }
  }

  private static DamageType parseDamageTypeKey(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": damage type is blank");
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return DamageType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(raw, DamageType.class);
      String msg = path + ": invalid damage type=" + raw;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static boolean hasDamageType(Map<String, Object> node) {
    return node.containsKey("damageType")
        || node.containsKey("damage_type")
        || node.containsKey("dmgType")
        || node.containsKey("dmg_type");
  }

  private static boolean hasHealType(Map<String, Object> node) {
    return node.containsKey("healType")
        || node.containsKey("heal_type")
        || node.containsKey("type");
  }

  private static boolean hasDamageCause(Map<String, Object> node) {
    return node.containsKey("damageCause")
        || node.containsKey("damage_cause")
        || node.containsKey("cause")
        || node.containsKey("dmgCause")
        || node.containsKey("dmg_cause");
  }

  private static DamageType damageTypeValue(Map<String, Object> node, String path) {
    String key = null;
    Object raw = null;
    if (node.containsKey("damageType")) {
      key = "damageType";
      raw = node.get(key);
    } else if (node.containsKey("damage_type")) {
      key = "damage_type";
      raw = node.get(key);
    } else if (node.containsKey("dmgType")) {
      key = "dmgType";
      raw = node.get(key);
    } else if (node.containsKey("dmg_type")) {
      key = "dmg_type";
      raw = node.get(key);
    }
    if (raw == null) {
      throw new IllegalArgumentException(missingKeyMessage(node, "damageType", path));
    }
    String value = String.valueOf(raw);
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    try {
      return DamageType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(value, DamageType.class);
      String msg = path + ": invalid " + key + "=" + value;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static HealType healTypeValue(Map<String, Object> node, String path, HealType def) {
    if (!hasHealType(node)) {
      return def;
    }
    String key = null;
    Object raw = null;
    if (node.containsKey("healType")) {
      key = "healType";
      raw = node.get(key);
    } else if (node.containsKey("heal_type")) {
      key = "heal_type";
      raw = node.get(key);
    } else if (node.containsKey("type")) {
      key = "type";
      raw = node.get(key);
    }
    if (raw == null) {
      throw new IllegalArgumentException(missingKeyMessage(node, "healType", path));
    }
    String value = String.valueOf(raw);
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.equals("HEAL")) {
      return HealType.DIRECT;
    }
    if (normalized.equals("HEAL_OVER_TIME") || normalized.equals("HEAL_HOT") || normalized.equals("HOT")) {
      return HealType.HOT;
    }
    try {
      return HealType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(value, HealType.class);
      String msg = path + ": invalid " + key + "=" + value;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static boolean hasDeathProtection(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) {
      return false;
    }
    return item.getData(DataComponentTypes.DEATH_PROTECTION) != null;
  }

  private static String describeItem(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) {
      return "EMPTY";
    }
    ItemMeta meta = item.getItemMeta();
    String name = null;
    if (meta != null && meta.hasDisplayName()) {
      name = String.valueOf(meta.displayName());
    }
    String base = item.getType().name() + "x" + item.getAmount();
    if (name == null || name.isBlank()) {
      return base;
    }
    return base + " name=" + name;
  }

  private static DamageCause damageCauseValue(Map<String, Object> node, String path, DamageCause def) {
    if (!hasDamageCause(node)) {
      return def;
    }
    String key = null;
    Object raw = null;
    if (node.containsKey("damageCause")) {
      key = "damageCause";
      raw = node.get(key);
    } else if (node.containsKey("damage_cause")) {
      key = "damage_cause";
      raw = node.get(key);
    } else if (node.containsKey("cause")) {
      key = "cause";
      raw = node.get(key);
    } else if (node.containsKey("dmgCause")) {
      key = "dmgCause";
      raw = node.get(key);
    } else if (node.containsKey("dmg_cause")) {
      key = "dmg_cause";
      raw = node.get(key);
    }
    if (raw == null) {
      throw new IllegalArgumentException(missingKeyMessage(node, "damageCause", path));
    }
    String value = String.valueOf(raw);
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    try {
      return DamageCause.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      String suggestion = suggestEnumValue(value, DamageCause.class);
      String msg = path + ": invalid " + key + "=" + value;
      if (suggestion != null) {
        msg += " (did you mean " + suggestion + "?)";
      }
      throw new IllegalArgumentException(msg);
    }
  }

  private static DamageAmountMode damageModeValue(String raw, String path, DamageAmountMode def) {
    if (raw == null) {
      return def;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "flat", "amount", "value" -> DamageAmountMode.FLAT;
      case "percent", "percent_max", "percent_max_health", "percent_maxhealth", "percent_health", "percent_health_max" ->
          DamageAmountMode.PERCENT_MAX_HEALTH;
      case "true", "true_damage" -> DamageAmountMode.TRUE;
      default -> throw new IllegalArgumentException(path + ": invalid damage mode: " + raw);
    };
  }

  private static EntityActions.DamagePolicy damagePolicyValue(String raw, String path) {
    String policy = raw == null ? "hostile_default" : raw.trim().toLowerCase(Locale.ROOT);
    return switch (policy) {
      case "any" -> EntityActions.DamagePolicy.any();
      case "pve_only" -> EntityActions.DamagePolicy.pveOnly();
      case "pvp_only" -> EntityActions.DamagePolicy.pvpOnly();
      case "hostile_default" -> EntityActions.DamagePolicy.hostileDefault();
      default -> throw new IllegalArgumentException(path + ": unknown policy: " + raw);
    };
  }

  private static java.util.Set<String> damageTagSet(Map<String, Object> node, String path) {
    Object raw = null;
    if (node.containsKey("tags")) {
      raw = node.get("tags");
    } else if (node.containsKey("tag")) {
      raw = node.get("tag");
    }
    if (raw == null) {
      return java.util.Set.of();
    }
    return parseStringSet(raw, path + ".tags");
  }

  private static Sound soundValue(Map<String, Object> node, String key, String path) {
    String raw = requireString(node, key, path);
    String s = raw.trim();

    NamespacedKey ns;
    if (s.contains(":")) {
      ns = NamespacedKey.fromString(s);
    } else {
      // Accept legacy-ish enum-like values (e.g. ENTITY_BLAZE_SHOOT) by converting to a minecraft key.
      String k = s.toLowerCase(Locale.ROOT).replace('_', '.');
      ns = NamespacedKey.fromString("minecraft:" + k);
    }
    if (ns == null) {
      throw new IllegalArgumentException(path + ": invalid sound key: " + raw);
    }
    Sound sound = Registry.SOUNDS.get(ns);
    if (sound == null) {
      throw new IllegalArgumentException(path + ": unknown sound: " + raw);
    }
    return sound;
  }

  private static boolean isBindingError(String message) {
    if (message == null) {
      return false;
    }
    String lower = message.toLowerCase(Locale.ROOT);
    return lower.contains("bindings")
        || lower.contains("effects/items")
        || lower.contains("/items/")
        || lower.contains("\\items\\");
  }

  @SuppressWarnings("unused")
  private static List<File> listYamlFiles(File folder) {
    List<File> out = new ArrayList<>();
    File[] entries = folder.listFiles();
    if (entries == null) {
      return out;
    }
    for (File entry : entries) {
      if (entry.isDirectory()) {
        continue;
      }
      String name = entry.getName().toLowerCase(Locale.ROOT);
      if (name.endsWith(".yml") || name.endsWith(".yaml")) {
        out.add(entry);
      }
    }
    out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    return out;
  }

  private void saveDefaultItemFiles(File folder) {
    List<String> bundled = listBundledItemFiles();
    for (String file : bundled) {
      File target = new File(folder, file);
      if (target.exists()) {
        continue;
      }
      String resourcePath = "effects/items/" + file;
      if (!PluginResources.saveResourceIfPresent(plugin, resourcePath, false)) {
        effectsLog.warn("[Effects] Missing bundled item template: " + resourcePath + " (skipping copy)");
      }
    }
  }

  private List<String> listBundledItemFiles() {
    try {
      java.net.URL url = plugin.getClass().getClassLoader().getResource("effects/items");
      if (url == null) {
        return List.of();
      }
      String protocol = url.getProtocol();
      if ("file".equalsIgnoreCase(protocol)) {
        File dir = new File(url.toURI());
        List<String> names = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries == null) {
          return List.of();
        }
        for (File entry : entries) {
          if (!entry.isFile()) {
            continue;
          }
          String name = entry.getName();
          String lower = name.toLowerCase(Locale.ROOT);
          if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            names.add(name);
          }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
      }
      if ("jar".equalsIgnoreCase(protocol)) {
        java.net.JarURLConnection conn = (java.net.JarURLConnection) url.openConnection();
        try (java.util.jar.JarFile jar = conn.getJarFile()) {
          List<String> names = new ArrayList<>();
          java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
          while (entries.hasMoreElements()) {
            java.util.jar.JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith("effects/items/") || entry.isDirectory()) {
              continue;
            }
            String base = name.substring("effects/items/".length());
            String lower = base.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
              names.add(base);
            }
          }
          names.sort(String.CASE_INSENSITIVE_ORDER);
          return names;
        }
      }
      return List.of();
    } catch (Exception ignored) {
      return List.of();
    }
  }
}
