package dev.patric.dungeonsreborn.progression;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.mana.ManaProvider;
import dev.patric.dungeonsreborn.effects.mana.SessionManaProvider;

public final class ProgressionStatService {
  private enum StatKey {
    STRENGTH,
    DEXTERITY,
    INTELLIGENCE,
    VITALITY
  }

  private record StatMapping(Attribute attribute, double amountPerPoint, AttributeModifier.Operation operation,
      NamespacedKey key) {
  }

  private final JavaPlugin plugin;
  private final ProgressionService progressionService;
  private final EffectsEngine effectsEngine;
  private final Predicate<World> worldAllowed;
  private final Map<StatKey, StatMapping> mappings;
  private final double manaBase;
  private final double manaPerLevel;
  private final double manaPerInt;

  public ProgressionStatService(JavaPlugin plugin, ProgressionService progressionService, EffectsEngine effectsEngine,
      Predicate<World> worldAllowed, Configuration config) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.progressionService = Objects.requireNonNull(progressionService, "progressionService");
    this.effectsEngine = Objects.requireNonNull(effectsEngine, "effectsEngine");
    this.worldAllowed = worldAllowed;
    this.mappings = loadMappings(config);
    ConfigurationSection mana = config == null ? null : config.getConfigurationSection("progression.mana");
    this.manaBase = mana == null ? 100.0 : mana.getDouble("base", 100.0);
    this.manaPerLevel = mana == null ? 0.0 : mana.getDouble("perLevel", 0.0);
    this.manaPerInt = mana == null ? 0.0 : mana.getDouble("perIntelligence", 0.0);
  }

  public void apply(Player player) {
    if (player == null) {
      return;
    }
    if (!isWorldAllowed(player.getWorld())) {
      clear(player);
      return;
    }
    progressionService.syncFromPlayer(player);
    PlayerProgression progression = progressionService.getOrCreate(player.getUniqueId());
    applyAttributes(player, progression);
    applyMana(player, progression);
  }

  public void clear(Player player) {
    if (player == null) {
      return;
    }
    removeModifiers(player);
    applyBaseMana(player, manaBase);
  }

  public void clearAll() {
    for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
      clear(player);
    }
  }

  private void applyAttributes(Player player, PlayerProgression progression) {
    removeModifiers(player);
    int strength = progression.strength();
    int dexterity = progression.dexterity();
    int intelligence = progression.intelligence();
    int vitality = progression.vitality();
    applyMapping(player, StatKey.STRENGTH, strength);
    applyMapping(player, StatKey.DEXTERITY, dexterity);
    applyMapping(player, StatKey.INTELLIGENCE, intelligence);
    applyMapping(player, StatKey.VITALITY, vitality);
  }

  private void applyMapping(Player player, StatKey key, int points) {
    StatMapping mapping = mappings.get(key);
    if (mapping == null || points <= 0) {
      return;
    }
    double amount = mapping.amountPerPoint() * points;
    if (!Double.isFinite(amount) || Math.abs(amount) < 1e-9) {
      return;
    }
    AttributeInstance instance = player.getAttribute(mapping.attribute());
    if (instance == null) {
      return;
    }
    AttributeModifier modifier = new AttributeModifier(mapping.key(), amount, mapping.operation());
    instance.addModifier(modifier);
  }

  private void removeModifiers(Player player) {
    for (StatMapping mapping : mappings.values()) {
      AttributeInstance instance = player.getAttribute(mapping.attribute());
      if (instance != null) {
        instance.removeModifier(mapping.key());
      }
    }
  }

  private void applyMana(Player player, PlayerProgression progression) {
    double base = Math.max(manaBase, progression.maxMana());
    base += manaPerLevel * progression.level() + manaPerInt * progression.intelligence();
    applyBaseMana(player, base);
  }

  private void applyBaseMana(Player player, double base) {
    ManaProvider provider = effectsEngine.manaProvider();
    if (provider instanceof SessionManaProvider session) {
      if (base > 0.0) {
        session.setMax(player, base);
      }
    }
  }

  private boolean isWorldAllowed(World world) {
    return worldAllowed == null || worldAllowed.test(world);
  }

  private Map<StatKey, StatMapping> loadMappings(Configuration config) {
    Map<StatKey, StatMapping> out = new EnumMap<>(StatKey.class);
    ConfigurationSection stats = config == null ? null : config.getConfigurationSection("progression.stats");
    loadMapping(out, StatKey.STRENGTH, stats, "strength", Attribute.ATTACK_DAMAGE, 0.2);
    loadMapping(out, StatKey.DEXTERITY, stats, "dexterity", Attribute.ATTACK_SPEED, 0.02);
    loadMapping(out, StatKey.VITALITY, stats, "vitality", Attribute.MAX_HEALTH, 1.0);
    loadMapping(out, StatKey.INTELLIGENCE, stats, "intelligence", null, 0.0);
    return out;
  }

  private void loadMapping(Map<StatKey, StatMapping> out, StatKey key, ConfigurationSection root, String path,
      Attribute defaultAttribute, double defaultAmount) {
    if (root == null) {
      if (defaultAttribute != null && defaultAmount != 0.0) {
        out.put(key, new StatMapping(defaultAttribute, defaultAmount, AttributeModifier.Operation.ADD_NUMBER,
            new NamespacedKey(plugin, "stat_" + path)));
      }
      return;
    }
    ConfigurationSection section = root.getConfigurationSection(path);
    if (section == null) {
      if (defaultAttribute != null && defaultAmount != 0.0) {
        out.put(key, new StatMapping(defaultAttribute, defaultAmount, AttributeModifier.Operation.ADD_NUMBER,
            new NamespacedKey(plugin, "stat_" + path)));
      }
      return;
    }
    String attrName = section.getString("attribute", defaultAttribute == null ? null : defaultAttribute.getKey().asString());
    Attribute attribute = attrName == null ? null : parseAttribute(attrName);
    double amount = section.getDouble("amountPerPoint", defaultAmount);
    String opRaw = section.getString("operation", "ADD_NUMBER");
    AttributeModifier.Operation operation = parseOperation(opRaw);
    if (attribute != null && Math.abs(amount) > 1e-9) {
      out.put(key, new StatMapping(attribute, amount, operation, new NamespacedKey(plugin, "stat_" + path)));
    }
  }

  private static Attribute parseAttribute(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    NamespacedKey key = NamespacedKey.fromString(trimmed);
    if (key != null) {
      return Registry.ATTRIBUTE.get(key);
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

  private static AttributeModifier.Operation parseOperation(String raw) {
    if (raw == null) {
      return AttributeModifier.Operation.ADD_NUMBER;
    }
    try {
      return AttributeModifier.Operation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      return AttributeModifier.Operation.ADD_NUMBER;
    }
  }
}
