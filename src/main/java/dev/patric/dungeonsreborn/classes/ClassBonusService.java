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

  private static final int POTION_TICKS = 20 * 10;

  private final JavaPlugin plugin;
  private final ClassService classService;
  private final ClassSkillService skills;
  private final EffectsEngine effectsEngine;
  private final Predicate<World> worldAllowed;
  private final Map<String, StatMapping> statMappings;
  private final Map<UUID, State> states = new ConcurrentHashMap<>();

  private static final class State {
    private String classId;
    private List<AppliedAttribute> attributes = List.of();
    private List<AppliedResistance> resistances = List.of();
  }

  public ClassBonusService(JavaPlugin plugin, ClassService classService, ClassSkillService skills, EffectsEngine effectsEngine,
      Predicate<World> worldAllowed, Configuration config) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.classService = Objects.requireNonNull(classService, "classService");
    this.skills = skills;
    this.effectsEngine = Objects.requireNonNull(effectsEngine, "effectsEngine");
    this.worldAllowed = worldAllowed;
    this.statMappings = loadStatMappings(config);
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
      clearManaBonus(player);
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
    clearManaBonus(player);
  }

  public void clearAll() {
    for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
      clear(player);
    }
    states.clear();
  }

  private void applyBonuses(Player player, ClassSpec spec, State state) {
    clearApplied(state, player);
    ClassBonusSpec bonuses = spec.bonusesOrEmpty();

    List<AttributeSpec> attributeSpecs = new ArrayList<>();
    Map<DamageType, Double> resistances = bonuses.resistancesOrEmpty();
    List<PotionSpec> potions = new ArrayList<>();
    Map<Attribute, Double> additiveCaps = parseCaps(bonuses.attributeCapsOrEmpty());

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

    if (skills != null) {
      List<String> unlocked = new ArrayList<>(skills.unlockedNodes(player.getUniqueId(), spec.id()));
      SkillTreeSpec tree = spec.skillTreeOrEmpty();
      for (SkillNodeSpec node : tree.nodes()) {
        if (node == null || !unlocked.contains(node.id())) {
          continue;
        }
        SkillStatSpec stat = node.stat();
        if (stat != null) {
          addStatBonus(attributeSpecs, stat.stat(), stat.amount());
        }
        SkillAttributeSpec attr = node.attribute();
        if (attr != null) {
          Attribute attribute = parseAttribute(attr.attribute());
          if (attribute != null) {
            AttributeModifier.Operation op = parseOperation(attr.operation());
            attributeSpecs.add(new AttributeSpec(attribute, attr.amount(), op));
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
    }

    state.attributes = applyAttributes(player, attributeSpecs, additiveCaps);
    state.resistances = applyResistances(player, resistances);
    applyPotions(player, potions);
    applyManaBonus(player, bonuses.manaMaxBonus(), bonuses.manaRegenBonus());
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

  private void applyManaBonus(Player player, double maxBonus, double regenBonus) {
    if (!(effectsEngine.manaProvider() instanceof SessionManaProvider session)) {
      return;
    }
    session.setClassMaxBonus(player, maxBonus);
    session.setClassRegenBonus(player, regenBonus);
  }

  private void clearManaBonus(Player player) {
    if (!(effectsEngine.manaProvider() instanceof SessionManaProvider session)) {
      return;
    }
    session.setClassMaxBonus(player, 0.0);
    session.setClassRegenBonus(player, 0.0);
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

  private Map<Attribute, Double> parseCaps(Map<String, Double> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<Attribute, Double> out = new HashMap<>();
    for (Map.Entry<String, Double> entry : raw.entrySet()) {
      Attribute attribute = parseAttribute(entry.getKey());
      if (attribute == null) {
        continue;
      }
      double value = entry.getValue() == null ? 0.0 : entry.getValue();
      if (value > 0.0) {
        out.put(attribute, value);
      }
    }
    return out;
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
