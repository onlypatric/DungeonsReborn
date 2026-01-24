package dev.patric.dungeonsreborn.classes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.patric.dungeonsreborn.classes.skills.ClassSkillService;
import dev.patric.dungeonsreborn.classes.skills.SkillAttributeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillNodeSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillPotionSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillStatSpec;
import dev.patric.dungeonsreborn.classes.skills.SkillSynergySpec;
import dev.patric.dungeonsreborn.classes.skills.SkillTreeSpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.mana.SessionManaProvider;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

public final class ClassBonusService {
  private record AppliedAttribute(Attribute attribute, NamespacedKey key) {
  }

  private record AppliedResistance(DamageType type, EffectsEngine.ResistanceSnapshot snapshot) {
  }

  private record StatMapping(Attribute attribute, double amountPerPoint, AttributeModifier.Operation operation) {
  }

  private record TuningProfile(Double statDefault, Double manaMaxDefault, Double manaRegenDefault,
      Double attributeDefault, Double resistanceDefault, Double capDefault,
      Map<String, Double> stat, Map<String, Double> attribute, Map<String, Double> cap,
      Map<DamageType, Double> resistance) {
    double statMultiplier(String key) {
      return resolve(stat, key, statDefault);
    }

    double attributeMultiplier(String key) {
      return resolve(attribute, key, attributeDefault);
    }

    double capMultiplier(String key) {
      return resolve(cap, key, capDefault);
    }

    double resistanceMultiplier(DamageType type) {
      return resolve(resistance, type, resistanceDefault);
    }

    double manaMaxMultiplier() {
      return manaMaxDefault == null ? 1.0 : manaMaxDefault;
    }

    double manaRegenMultiplier() {
      return manaRegenDefault == null ? 1.0 : manaRegenDefault;
    }

    private static <K> double resolve(Map<K, Double> map, K key, Double fallback) {
      if (map != null && key != null) {
        Double value = map.get(key);
        if (value != null && Double.isFinite(value)) {
          return value;
        }
      }
      return fallback == null ? 1.0 : fallback;
    }

    TuningProfile merged(TuningProfile override) {
      if (override == null) {
        return this;
      }
      Map<String, Double> statMerged = mergeMaps(stat, override.stat);
      Map<String, Double> attributeMerged = mergeMaps(attribute, override.attribute);
      Map<String, Double> capMerged = mergeMaps(cap, override.cap);
      Map<DamageType, Double> resistanceMerged = mergeMaps(resistance, override.resistance);
      return new TuningProfile(
          override.statDefault != null ? override.statDefault : statDefault,
          override.manaMaxDefault != null ? override.manaMaxDefault : manaMaxDefault,
          override.manaRegenDefault != null ? override.manaRegenDefault : manaRegenDefault,
          override.attributeDefault != null ? override.attributeDefault : attributeDefault,
          override.resistanceDefault != null ? override.resistanceDefault : resistanceDefault,
          override.capDefault != null ? override.capDefault : capDefault,
          statMerged, attributeMerged, capMerged, resistanceMerged);
    }

    private static <K> Map<K, Double> mergeMaps(Map<K, Double> base, Map<K, Double> override) {
      if ((base == null || base.isEmpty()) && (override == null || override.isEmpty())) {
        return Map.of();
      }
      Map<K, Double> out = new HashMap<>();
      if (base != null) {
        out.putAll(base);
      }
      if (override != null) {
        out.putAll(override);
      }
      return Map.copyOf(out);
    }
  }

  private static final class TuningConfig {
    private final String profile;
    private final Map<String, TuningProfile> profiles;
    private final Map<String, Map<String, TuningProfile>> classProfiles;

    private TuningConfig(String profile, Map<String, TuningProfile> profiles,
        Map<String, Map<String, TuningProfile>> classProfiles) {
      this.profile = profile == null ? "pve" : profile;
      this.profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
      this.classProfiles = classProfiles == null ? Map.of() : Map.copyOf(classProfiles);
    }

    TuningProfile profileFor(String classId) {
      String name = profile == null ? "pve" : profile.toLowerCase(Locale.ROOT);
      TuningProfile base = profiles.getOrDefault(name, defaultProfile());
      if (classId == null) {
        return base;
      }
      Map<String, TuningProfile> overrides = classProfiles.get(classId.toLowerCase(Locale.ROOT));
      if (overrides == null) {
        return base;
      }
      TuningProfile override = overrides.get(name);
      return base.merged(override);
    }

    static TuningProfile defaultProfile() {
      return new TuningProfile(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, Map.of(), Map.of(), Map.of(), Map.of());
    }
  }

  private static final int POTION_TICKS = 20 * 10;

  private final JavaPlugin plugin;
  private final ClassService classService;
  private final ClassSkillService skills;
  private final EffectsEngine effectsEngine;
  private final Predicate<World> worldAllowed;
  private final Map<String, StatMapping> statMappings;
  private final boolean conditionalBonusesEnabled;
  private final TuningConfig tuningConfig;
  private final Map<UUID, State> states = new ConcurrentHashMap<>();

  private static final class State {
    private String classId;
    private List<AppliedAttribute> attributes = List.of();
    private List<AppliedResistance> resistances = List.of();
    private String manaResourceId = dev.patric.dungeonsreborn.effects.mana.ManaProvider.DEFAULT_RESOURCE;
  }

  public ClassBonusService(JavaPlugin plugin, ClassService classService, ClassSkillService skills, EffectsEngine effectsEngine,
      Predicate<World> worldAllowed, Configuration config) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.classService = Objects.requireNonNull(classService, "classService");
    this.skills = skills;
    this.effectsEngine = Objects.requireNonNull(effectsEngine, "effectsEngine");
    this.worldAllowed = worldAllowed;
    this.statMappings = loadStatMappings(config);
    this.conditionalBonusesEnabled = config == null
        || config.getBoolean("classes.conditionalBonuses.enabled", true);
    this.tuningConfig = loadTuningConfig(config);
  }

  public void apply(Player player) {
    if (player == null) {
      return;
    }
    if (!isWorldAllowed(player.getWorld())) {
      clear(player);
      return;
    }
    ClassSpec spec = classService.currentClass(player.getUniqueId());
    if (spec == null || !spec.enabled()) {
      clear(player);
      return;
    }
    State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
    if (state.classId == null || !state.classId.equals(spec.id())) {
      clear(player);
      state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
      state.classId = spec.id();
    }
    applyBonuses(player, spec, state);
  }

  public void clear(Player player) {
    if (player == null) {
      return;
    }
    State state = states.remove(player.getUniqueId());
    if (state == null) {
      clearManaBonus(player, dev.patric.dungeonsreborn.effects.mana.ManaProvider.DEFAULT_RESOURCE);
      return;
    }
    for (AppliedAttribute applied : state.attributes) {
      AttributeInstance instance = player.getAttribute(applied.attribute());
      if (instance != null) {
        instance.removeModifier(applied.key());
      }
    }
    for (AppliedResistance entry : state.resistances) {
      effectsEngine.restoreResistance(player.getUniqueId(), entry.type(), entry.snapshot.token(), entry.snapshot.previous());
    }
    clearManaBonus(player, state.manaResourceId);
  }

  public void clearAll() {
    for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
      clear(player);
    }
    states.clear();
  }

  private void applyBonuses(Player player, ClassSpec spec, State state) {
    clearApplied(state, player);
    TuningProfile tuning = tuningConfig.profileFor(spec.id());
    ClassBonusSpec bonuses = scaleBonusSpec(spec.bonusesOrEmpty(), tuning);

    List<AttributeSpec> attributeSpecs = new ArrayList<>();
    Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
    List<PotionSpec> potions = new ArrayList<>();
    Map<Attribute, Double> additiveCaps = new HashMap<>();
    String manaResourceId = normalizeResourceId(bonuses.manaResourceId());
    double manaMaxBonus = 0.0;
    double manaRegenBonus = 0.0;

    manaMaxBonus += bonuses.manaMaxBonus();
    manaRegenBonus += bonuses.manaRegenBonus();
    applyBonusSpec(bonuses, attributeSpecs, potions, resistances, additiveCaps);
    manaResourceId = mergeManaResource(manaResourceId, bonuses.manaResourceId());

    if (conditionalBonusesEnabled) {
      for (ClassConditionalBonusSpec conditional : spec.conditionalBonusesOrEmpty()) {
        if (conditional == null || !conditional.matches(player.getLocation())) {
          continue;
        }
        ClassBonusSpec conditionalBonus = scaleBonusSpec(conditional.bonusesOrEmpty(), tuning);
        manaMaxBonus += conditionalBonus.manaMaxBonus();
        manaRegenBonus += conditionalBonus.manaRegenBonus();
        applyBonusSpec(conditionalBonus, attributeSpecs, potions, resistances, additiveCaps);
        manaResourceId = mergeManaResource(manaResourceId, conditionalBonus.manaResourceId());
      }
    }

    if (skills != null) {
      java.util.Map<String, Integer> ranks = skills.nodeRanks(player.getUniqueId(), spec.id());
      SkillTreeSpec tree = spec.skillTreeOrEmpty();
      for (SkillNodeSpec node : tree.nodes()) {
        if (node == null) {
          continue;
        }
        int rank = ranks.getOrDefault(node.id(), 0);
        if (rank <= 0) {
          continue;
        }
        SkillStatSpec stat = node.stat();
        if (stat != null) {
          double scaled = scaleAmount(stat.amount(), stat.scalingOrDefault(), rank);
          scaled *= tuning.statMultiplier(stat.stat());
          addStatBonus(attributeSpecs, stat.stat(), scaled, dev.patric.dungeonsreborn.classes.skills.SkillScalingSpec.flat(), 1);
        }
        SkillAttributeSpec attr = node.attribute();
        if (attr != null) {
          Attribute attribute = parseAttribute(attr.attribute());
          if (attribute != null) {
            AttributeModifier.Operation op = parseOperation(attr.operation());
            double scaled = scaleAmount(attr.amount(), attr.scalingOrDefault(), rank);
            scaled *= tuning.attributeMultiplier(normalizeAttributeKey(attr.attribute()));
            attributeSpecs.add(new AttributeSpec(attribute, scaled, op));
          }
        }
        SkillPotionSpec potion = node.potion();
        if (potion != null) {
          PotionEffectType type = parsePotionEffect(potion.effect());
          if (type != null) {
            potions.add(new PotionSpec(type, potion.amplifier(), potion.ambient(), potion.particles(), true));
          }
        }
      }
      for (SkillSynergySpec synergy : skills.activeSynergies(player.getUniqueId(), spec)) {
        if (synergy == null) {
          continue;
        }
        ClassBonusSpec synergyBonus = scaleBonusSpec(synergy.bonuses(), tuning);
        if (synergyBonus == null) {
          continue;
        }
        manaMaxBonus += synergyBonus.manaMaxBonus();
        manaRegenBonus += synergyBonus.manaRegenBonus();
        applyBonusSpec(synergyBonus, attributeSpecs, potions, resistances, additiveCaps);
        manaResourceId = mergeManaResource(manaResourceId, synergyBonus.manaResourceId());
      }
    }

    state.attributes = applyAttributes(player, attributeSpecs, additiveCaps);
    state.resistances = applyResistances(player, resistances);
    applyPotions(player, potions);
    clearManaBonus(player, state.manaResourceId);
    state.manaResourceId = manaResourceId;
    applyManaBonus(player, manaResourceId, manaMaxBonus, manaRegenBonus);
  }

  private void applyBonusSpec(ClassBonusSpec bonuses, List<AttributeSpec> attributeSpecs, List<PotionSpec> potions,
      Map<DamageType, Double> resistances, Map<Attribute, Double> caps) {
    if (bonuses == null) {
      return;
    }
    addStatBonus(attributeSpecs, "strength", bonuses.strength());
    addStatBonus(attributeSpecs, "dexterity", bonuses.dexterity());
    addStatBonus(attributeSpecs, "intelligence", bonuses.intelligence());
    addStatBonus(attributeSpecs, "vitality", bonuses.vitality());

    for (ClassAttributeBonus bonus : bonuses.attributesOrEmpty()) {
      Attribute attribute = parseAttribute(bonus.attribute());
      if (attribute == null) {
        continue;
      }
      AttributeModifier.Operation op = parseOperation(bonus.operation());
      attributeSpecs.add(new AttributeSpec(attribute, bonus.amount(), op));
    }

    for (ClassPotionBonus bonus : bonuses.potionsOrEmpty()) {
      PotionEffectType type = parsePotionEffect(bonus.effect());
      if (type == null) {
        continue;
      }
      potions.add(new PotionSpec(type, bonus.amplifier(), bonus.ambient(), bonus.particles(), bonus.icon()));
    }

    mergeResistances(resistances, bonuses.resistancesOrEmpty());
    mergeCaps(caps, bonuses.attributeCapsOrEmpty());
  }

  private void mergeResistances(Map<DamageType, Double> target, Map<DamageType, Double> source) {
    if (source == null || source.isEmpty()) {
      return;
    }
    for (Map.Entry<DamageType, Double> entry : source.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      if (target.containsKey(entry.getKey())) {
        target.put(entry.getKey(), target.get(entry.getKey()) * entry.getValue());
      } else {
        target.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private void mergeCaps(Map<Attribute, Double> target, Map<String, Double> source) {
    if (source == null || source.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Double> entry : source.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      Attribute attribute = parseAttribute(entry.getKey());
      if (attribute == null) {
        continue;
      }
      double limit = entry.getValue();
      if (!(limit > 0.0)) {
        continue;
      }
      Double current = target.get(attribute);
      if (current == null) {
        target.put(attribute, limit);
      } else {
        target.put(attribute, Math.min(current, limit));
      }
    }
  }

  private String normalizeResourceId(String resourceId) {
    if (resourceId == null || resourceId.isBlank()) {
      return dev.patric.dungeonsreborn.effects.mana.ManaProvider.DEFAULT_RESOURCE;
    }
    return resourceId.trim().toLowerCase(Locale.ROOT);
  }

  private String mergeManaResource(String current, String candidate) {
    String normalized = normalizeResourceId(candidate);
    if (current == null || current.isBlank()
        || current.equals(dev.patric.dungeonsreborn.effects.mana.ManaProvider.DEFAULT_RESOURCE)) {
      return normalized;
    }
    if (normalized.equals(current)) {
      return current;
    }
    return current;
  }

  private void clearApplied(State state, Player player) {
    for (AppliedAttribute applied : state.attributes) {
      AttributeInstance instance = player.getAttribute(applied.attribute());
      if (instance != null) {
        instance.removeModifier(applied.key());
      }
    }
    for (AppliedResistance entry : state.resistances) {
      effectsEngine.restoreResistance(player.getUniqueId(), entry.type(), entry.snapshot.token(), entry.snapshot.previous());
    }
    state.attributes = List.of();
    state.resistances = List.of();
  }

  private List<AppliedAttribute> applyAttributes(Player player, List<AttributeSpec> specs, Map<Attribute, Double> caps) {
    if (specs.isEmpty()) {
      return List.of();
    }
    List<AppliedAttribute> applied = new ArrayList<>();
    Map<Attribute, Double> used = new HashMap<>();
    int index = 0;
    for (AttributeSpec spec : specs) {
      double amount = spec.amount();
      if (!Double.isFinite(amount) || Math.abs(amount) < 1e-9) {
        continue;
      }
      if (spec.operation() == AttributeModifier.Operation.ADD_NUMBER) {
        Double cap = caps.get(spec.attribute());
        if (cap != null && cap > 0.0) {
          double current = used.getOrDefault(spec.attribute(), 0.0);
          double remaining = cap - current;
          if (remaining <= 0.0) {
            continue;
          }
          if (amount > remaining) {
            amount = remaining;
          }
          used.put(spec.attribute(), current + amount);
        }
      }
      AttributeInstance instance = player.getAttribute(spec.attribute());
      if (instance == null) {
        continue;
      }
      NamespacedKey key = new NamespacedKey(plugin, "class_bonus_" + index++);
      AttributeModifier modifier = new AttributeModifier(key, amount, spec.operation());
      instance.addModifier(modifier);
      applied.add(new AppliedAttribute(spec.attribute(), key));
    }
    return List.copyOf(applied);
  }

  private List<AppliedResistance> applyResistances(Player player, Map<DamageType, Double> resistances) {
    if (resistances.isEmpty()) {
      return List.of();
    }
    List<AppliedResistance> applied = new ArrayList<>();
    for (Map.Entry<DamageType, Double> entry : resistances.entrySet()) {
      DamageType type = entry.getKey();
      double multiplier = entry.getValue();
      if (type == null || !Double.isFinite(multiplier) || multiplier < 0.0) {
        continue;
      }
      EffectsEngine.ResistanceSnapshot snapshot = effectsEngine.setResistance(player.getUniqueId(), type, multiplier);
      applied.add(new AppliedResistance(type, snapshot));
    }
    return List.copyOf(applied);
  }

  private void applyPotions(Player player, List<PotionSpec> potions) {
    if (potions.isEmpty()) {
      return;
    }
    Map<PotionEffectType, PotionSpec> merged = new HashMap<>();
    for (PotionSpec spec : potions) {
      PotionSpec existing = merged.get(spec.type());
      if (existing == null || spec.amplifier() > existing.amplifier()) {
        merged.put(spec.type(), spec);
        continue;
      }
      if (spec.amplifier() == existing.amplifier()) {
        merged.put(spec.type(), existing.merge(spec));
      }
    }
    for (PotionSpec spec : merged.values()) {
      PotionEffect effect = new PotionEffect(spec.type(), POTION_TICKS, spec.amplifier(), spec.ambient(),
          spec.particles(), spec.icon());
      player.addPotionEffect(effect);
    }
  }

  private void applyManaBonus(Player player, String resourceId, double maxBonus, double regenBonus) {
    if (!(effectsEngine.manaProvider() instanceof SessionManaProvider session)) {
      return;
    }
    session.setClassMaxBonus(player, resourceId, maxBonus);
    session.setClassRegenBonus(player, resourceId, regenBonus);
  }

  private void clearManaBonus(Player player, String resourceId) {
    if (!(effectsEngine.manaProvider() instanceof SessionManaProvider session)) {
      return;
    }
    String id = resourceId == null || resourceId.isBlank()
        ? dev.patric.dungeonsreborn.effects.mana.ManaProvider.DEFAULT_RESOURCE
        : resourceId;
    session.setClassMaxBonus(player, id, 0.0);
    session.setClassRegenBonus(player, id, 0.0);
  }

  private boolean isWorldAllowed(World world) {
    return worldAllowed == null || worldAllowed.test(world);
  }

  private void addStatBonus(List<AttributeSpec> specs, String stat, int amount) {
    if (amount <= 0) {
      return;
    }
    StatMapping mapping = statMappings.get(stat.toLowerCase(Locale.ROOT));
    if (mapping == null || mapping.attribute() == null) {
      return;
    }
    double total = mapping.amountPerPoint() * amount;
    if (Math.abs(total) < 1e-9) {
      return;
    }
    specs.add(new AttributeSpec(mapping.attribute(), total, mapping.operation()));
  }

  private void addStatBonus(List<AttributeSpec> specs, String stat, double amount,
      dev.patric.dungeonsreborn.classes.skills.SkillScalingSpec scaling, int rank) {
    if (!Double.isFinite(amount) || Math.abs(amount) < 1e-9) {
      return;
    }
    StatMapping mapping = statMappings.get(stat.toLowerCase(Locale.ROOT));
    if (mapping == null || mapping.attribute() == null) {
      return;
    }
    double scaledAmount = scaleAmount(amount, scaling, rank);
    double total = mapping.amountPerPoint() * scaledAmount;
    if (Math.abs(total) < 1e-9) {
      return;
    }
    specs.add(new AttributeSpec(mapping.attribute(), total, mapping.operation()));
  }

  private double scaleAmount(double base, dev.patric.dungeonsreborn.classes.skills.SkillScalingSpec scaling, int rank) {
    if (!Double.isFinite(base)) {
      return 0.0;
    }
    if (rank <= 0) {
      return 0.0;
    }
    dev.patric.dungeonsreborn.classes.skills.SkillScalingSpec safe =
        scaling == null ? dev.patric.dungeonsreborn.classes.skills.SkillScalingSpec.flat() : scaling;
    double multiplier = safe.multiplier(rank);
    return base * multiplier;
  }

  private TuningConfig loadTuningConfig(Configuration config) {
    if (config == null) {
      return new TuningConfig("pve", Map.of("pve", TuningConfig.defaultProfile()), Map.of());
    }
    ConfigurationSection tuning = config.getConfigurationSection("classes.tuning");
    if (tuning == null) {
      return new TuningConfig("pve", Map.of("pve", TuningConfig.defaultProfile()), Map.of());
    }
    String profile = tuning.getString("profile", "pve");
    Map<String, TuningProfile> profiles = new HashMap<>();
    ConfigurationSection profilesSec = tuning.getConfigurationSection("profiles");
    if (profilesSec != null) {
      for (String key : profilesSec.getKeys(false)) {
        ConfigurationSection profileSec = profilesSec.getConfigurationSection(key);
        TuningProfile parsed = parseTuningProfile(profileSec);
        if (parsed != null) {
          profiles.put(key.toLowerCase(Locale.ROOT), parsed);
        }
      }
    }
    if (profiles.isEmpty()) {
      profiles.put("pve", TuningConfig.defaultProfile());
    }
    Map<String, Map<String, TuningProfile>> classProfiles = new HashMap<>();
    ConfigurationSection classSec = tuning.getConfigurationSection("classes");
    if (classSec != null) {
      for (String classId : classSec.getKeys(false)) {
        ConfigurationSection classProfileSec = classSec.getConfigurationSection(classId);
        if (classProfileSec == null) {
          continue;
        }
        Map<String, TuningProfile> overrides = new HashMap<>();
        for (String key : classProfileSec.getKeys(false)) {
          ConfigurationSection overrideSec = classProfileSec.getConfigurationSection(key);
          if (overrideSec == null) {
            continue;
          }
          TuningProfile parsed = parseTuningProfile(overrideSec);
          if (parsed != null) {
            overrides.put(key.toLowerCase(Locale.ROOT), parsed);
          }
        }
        if (!overrides.isEmpty()) {
          classProfiles.put(classId.toLowerCase(Locale.ROOT), Map.copyOf(overrides));
        }
      }
    }
    return new TuningConfig(profile, profiles, classProfiles);
  }

  private TuningProfile parseTuningProfile(ConfigurationSection section) {
    if (section == null) {
      return null;
    }
    Double statDefault = readOptionalDouble(section, "defaults.stat");
    Double manaMaxDefault = readOptionalDouble(section, "defaults.manaMax");
    Double manaRegenDefault = readOptionalDouble(section, "defaults.manaRegen");
    Double attributeDefault = readOptionalDouble(section, "defaults.attribute");
    Double resistanceDefault = readOptionalDouble(section, "defaults.resistance");
    Double capDefault = readOptionalDouble(section, "defaults.cap");

    ConfigurationSection mana = section.getConfigurationSection("mana");
    if (mana != null) {
      Double manaMax = readOptionalDouble(mana, "max");
      Double manaRegen = readOptionalDouble(mana, "regen");
      if (manaMax != null) {
        manaMaxDefault = manaMax;
      }
      if (manaRegen != null) {
        manaRegenDefault = manaRegen;
      }
    }

    Map<String, Double> stat = readStringDoubleMap(section.getConfigurationSection("stats"), key -> key.toLowerCase(Locale.ROOT));
    Map<String, Double> attribute = readStringDoubleMap(section.getConfigurationSection("attributes"), this::normalizeAttributeKey);
    Map<String, Double> cap = readStringDoubleMap(section.getConfigurationSection("caps"), this::normalizeAttributeKey);
    Map<DamageType, Double> resistance = readResistanceMap(section.getConfigurationSection("resistances"));

    return new TuningProfile(statDefault, manaMaxDefault, manaRegenDefault, attributeDefault, resistanceDefault,
        capDefault, stat, attribute, cap, resistance);
  }

  private Map<String, Double> readStringDoubleMap(ConfigurationSection section, java.util.function.Function<String, String> keyMapper) {
    if (section == null || section.getKeys(false).isEmpty()) {
      return Map.of();
    }
    Map<String, Double> out = new HashMap<>();
    for (String key : section.getKeys(false)) {
      if (!section.isSet(key)) {
        continue;
      }
      double value = section.getDouble(key);
      if (!Double.isFinite(value)) {
        continue;
      }
      String normalized = keyMapper == null ? key : keyMapper.apply(key);
      if (normalized == null || normalized.isBlank()) {
        continue;
      }
      out.put(normalized, value);
    }
    return Map.copyOf(out);
  }

  private Map<DamageType, Double> readResistanceMap(ConfigurationSection section) {
    if (section == null || section.getKeys(false).isEmpty()) {
      return Map.of();
    }
    Map<DamageType, Double> out = new java.util.EnumMap<>(DamageType.class);
    for (String key : section.getKeys(false)) {
      if (!section.isSet(key)) {
        continue;
      }
      DamageType type = parseDamageType(key);
      if (type == null) {
        continue;
      }
      double value = section.getDouble(key);
      if (!Double.isFinite(value)) {
        continue;
      }
      out.put(type, value);
    }
    return Map.copyOf(out);
  }

  private DamageType parseDamageType(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    try {
      return DamageType.valueOf(trimmed.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private Double readOptionalDouble(ConfigurationSection section, String path) {
    if (section == null || path == null || !section.isSet(path)) {
      return null;
    }
    double value = section.getDouble(path);
    return Double.isFinite(value) ? value : null;
  }

  private ClassBonusSpec scaleBonusSpec(ClassBonusSpec bonuses, TuningProfile tuning) {
    if (bonuses == null || tuning == null) {
      return bonuses;
    }
    int strength = scaleInt(bonuses.strength(), tuning.statMultiplier("strength"));
    int dexterity = scaleInt(bonuses.dexterity(), tuning.statMultiplier("dexterity"));
    int intelligence = scaleInt(bonuses.intelligence(), tuning.statMultiplier("intelligence"));
    int vitality = scaleInt(bonuses.vitality(), tuning.statMultiplier("vitality"));
    double manaMax = bonuses.manaMaxBonus() * tuning.manaMaxMultiplier();
    double manaRegen = bonuses.manaRegenBonus() * tuning.manaRegenMultiplier();

    List<ClassAttributeBonus> attributes = new ArrayList<>();
    for (ClassAttributeBonus bonus : bonuses.attributesOrEmpty()) {
      if (bonus == null) {
        continue;
      }
      String key = normalizeAttributeKey(bonus.attribute());
      double multiplier = tuning.attributeMultiplier(key);
      attributes.add(new ClassAttributeBonus(bonus.attribute(), bonus.amount() * multiplier, bonus.operation()));
    }

    Map<DamageType, Double> resistances = new java.util.EnumMap<>(DamageType.class);
    for (Map.Entry<DamageType, Double> entry : bonuses.resistancesOrEmpty().entrySet()) {
      DamageType type = entry.getKey();
      Double value = entry.getValue();
      if (type == null || value == null) {
        continue;
      }
      double scaled = value * tuning.resistanceMultiplier(type);
      if (Double.isFinite(scaled)) {
        resistances.put(type, scaled);
      }
    }

    Map<String, Double> caps = new HashMap<>();
    for (Map.Entry<String, Double> entry : bonuses.attributeCapsOrEmpty().entrySet()) {
      String key = normalizeAttributeKey(entry.getKey());
      Double value = entry.getValue();
      if (key == null || value == null) {
        continue;
      }
      double scaled = value * tuning.capMultiplier(key);
      if (Double.isFinite(scaled)) {
        caps.put(key, scaled);
      }
    }

    return new ClassBonusSpec(strength, dexterity, intelligence, vitality,
        bonuses.manaResourceId(), manaMax, manaRegen,
        List.copyOf(attributes), bonuses.potionsOrEmpty(),
        Map.copyOf(resistances), Map.copyOf(caps));
  }

  private int scaleInt(int value, double multiplier) {
    if (!Double.isFinite(multiplier)) {
      return value;
    }
    return (int) Math.round(value * multiplier);
  }

  private String normalizeAttributeKey(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(trimmed);
    if (key != null) {
      if ("minecraft".equalsIgnoreCase(key.getNamespace())) {
        return key.getKey();
      }
      return key.asString();
    }
    if (trimmed.startsWith("generic_")) {
      return "generic." + trimmed.substring("generic_".length());
    }
    if (!trimmed.contains(".")) {
      return "generic." + trimmed;
    }
    return trimmed;
  }

  private Map<String, StatMapping> loadStatMappings(Configuration config) {
    Map<String, StatMapping> out = new HashMap<>();
    ConfigurationSection stats = config == null ? null : config.getConfigurationSection("progression.stats");
    loadMapping(out, "strength", stats, Attribute.ATTACK_DAMAGE, 0.2);
    loadMapping(out, "dexterity", stats, Attribute.ATTACK_SPEED, 0.02);
    loadMapping(out, "vitality", stats, Attribute.MAX_HEALTH, 1.0);
    loadMapping(out, "intelligence", stats, null, 0.0);
    return out;
  }

  private void loadMapping(Map<String, StatMapping> out, String key, ConfigurationSection root,
      Attribute defaultAttribute, double defaultAmount) {
    ConfigurationSection section = root == null ? null : root.getConfigurationSection(key);
    if (section == null) {
      if (defaultAttribute != null && defaultAmount != 0.0) {
        out.put(key, new StatMapping(defaultAttribute, defaultAmount, AttributeModifier.Operation.ADD_NUMBER));
      }
      return;
    }
    String attrName = section.getString("attribute", defaultAttribute == null ? null : defaultAttribute.getKey().asString());
    Attribute attribute = attrName == null ? null : parseAttribute(attrName);
    double amount = section.getDouble("amountPerPoint", defaultAmount);
    String opRaw = section.getString("operation", "ADD_NUMBER");
    AttributeModifier.Operation operation = parseOperation(opRaw);
    if (attribute != null && Math.abs(amount) > 1e-9) {
      out.put(key, new StatMapping(attribute, amount, operation));
    }
  }

  private Attribute parseAttribute(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(trimmed);
    if (key != null) {
      Attribute direct = Registry.ATTRIBUTE.get(key);
      if (direct != null) {
        return direct;
      }
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    Attribute direct = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(lower));
    if (direct != null) {
      return direct;
    }
    if (lower.startsWith("generic_")) {
      lower = "generic." + lower.substring("generic_".length());
    } else if (!lower.contains(".")) {
      lower = "generic." + lower;
    }
    return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(lower));
  }

  private AttributeModifier.Operation parseOperation(String raw) {
    if (raw == null) {
      return AttributeModifier.Operation.ADD_NUMBER;
    }
    try {
      return AttributeModifier.Operation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      return AttributeModifier.Operation.ADD_NUMBER;
    }
  }

  private PotionEffectType parsePotionEffect(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
    if (key == null) {
      return null;
    }
    var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
    return registry.get(key);
  }

  private record AttributeSpec(Attribute attribute, double amount, AttributeModifier.Operation operation) {
  }

  private record PotionSpec(PotionEffectType type, int amplifier, boolean ambient, boolean particles, boolean icon) {
    private PotionSpec merge(PotionSpec other) {
      boolean nextAmbient = ambient || other.ambient;
      boolean nextParticles = particles || other.particles;
      boolean nextIcon = icon || other.icon;
      int nextAmp = Math.max(amplifier, other.amplifier);
      return new PotionSpec(type, nextAmp, nextAmbient, nextParticles, nextIcon);
    }
  }
}
