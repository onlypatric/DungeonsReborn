package dev.patric.dungeonsreborn.mobs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.damage.DamageType;

import net.kyori.adventure.bossbar.BossBar;

public final class MobYamlRegistry {
  public record ReloadResult(int loadedMobs, int loadedSpawns, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final EffectsEngine engine;
  private final EffectsYamlAbilities yamlAbilities;
  private final MobRegistry registry;
  private final MobSpawnManager spawns;
  private final Set<String> loadedIds = new HashSet<>();
  private final Set<String> loadedScriptAbilityIds = new HashSet<>();
  private final Map<String, MobEggSpec> eggSpecs = new HashMap<>();
  private List<String> lastErrors = List.of();

  public MobYamlRegistry(JavaPlugin plugin, EffectsEngine engine, EffectsYamlAbilities yamlAbilities, MobRegistry registry, MobSpawnManager spawns) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.yamlAbilities = Objects.requireNonNull(yamlAbilities, "yamlAbilities");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.spawns = Objects.requireNonNull(spawns, "spawns");
  }

  public File file() {
    return new File(plugin.getDataFolder(), "mobs.yml");
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public JavaPlugin plugin() {
    return plugin;
  }

  public MobEggSpec eggSpec(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    return eggSpecs.get(Ids.normalize(id));
  }

  public MobEggSpec eggFromItem(ItemStack item) {
    String eggId = MobItemMarkers.getEggId(item);
    if (eggId == null || eggId.isBlank()) {
      return null;
    }
    return eggSpecs.get(eggId);
  }

  public ItemStack eggItem(String id) {
    MobEggSpec spec = eggSpec(id);
    if (spec == null) {
      return null;
    }
    return spec.item().clone();
  }

  public ItemStack eggItemForMob(String mobId) {
    if (mobId == null || mobId.isBlank()) {
      return null;
    }
    String normalized = Ids.normalize(mobId);
    MobEggSpec direct = eggSpecs.get(normalized);
    if (direct != null) {
      return direct.item().clone();
    }
    for (MobEggSpec spec : eggSpecs.values()) {
      if (normalized.equals(spec.mobId())) {
        return spec.item().clone();
      }
    }
    return null;
  }

  public ReloadResult reload() {
    plugin.getDataFolder().mkdirs();
    File file = file();
    if (!file.exists()) {
      try {
        plugin.saveResource("mobs.yml", false);
      } catch (IllegalArgumentException ignored) {
      }
    }
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
    List<String> errors = new ArrayList<>();

    for (String abilityId : loadedScriptAbilityIds) {
      engine.unregisterAbility(abilityId);
    }
    loadedScriptAbilityIds.clear();
    for (String id : loadedIds) {
      registry.unregister(id);
    }
    loadedIds.clear();
    eggSpecs.clear();

    Map<String, MobSpec> specs = new HashMap<>();
    ConfigurationSection mobsSec = cfg.getConfigurationSection("mobs");
    if (mobsSec != null) {
      for (String rawId : mobsSec.getKeys(false)) {
        String base = "mobs." + rawId;
        try {
          String id = Ids.normalize(rawId);
          if (specs.containsKey(id)) {
            errors.add(base + ": duplicate mob id");
            continue;
          }
          ConfigurationSection node = mobsSec.getConfigurationSection(rawId);
          if (node == null) {
            errors.add(base + ": must be an object");
            continue;
          }
          MobSpec spec = parseMobSpec(id, node, errors, base);
          specs.put(id, spec);
        } catch (Exception ex) {
          errors.add(base + ": " + ex.getMessage());
        }
      }
    }

    int loaded = 0;
    for (MobSpec spec : specs.values()) {
      try {
        registry.register(spec);
        loadedIds.add(spec.id());
        loaded++;
      } catch (Exception ex) {
        errors.add("mobs." + spec.id() + ": " + ex.getMessage());
      }
    }

    int loadedEggs = parseEggs(cfg, specs, errors);
    List<MobSpawnSpec> spawnSpecs = parseSpawns(cfg, errors);
    Set<String> enabledWorlds = parseEnabledWorlds(cfg);
    boolean despawnOnReload = cfg.getBoolean("options.despawnOnReload", false);
    spawns.reload(spawnSpecs, enabledWorlds, despawnOnReload);

    if (!errors.isEmpty()) {
      plugin.getLogger().warning("[Mobs] YAML reload had " + errors.size() + " errors (some mobs/spawns may be missing)");
      for (String error : errors) {
        plugin.getLogger().warning("[Mobs] YAML: " + error);
      }
    } else {
      plugin.getLogger().info("[Mobs] YAML loaded " + loaded + " mobs, " + loadedEggs + " eggs, and " + spawns.activeSpawns() + " spawns");
    }
    lastErrors = List.copyOf(errors);

    return new ReloadResult(loaded, spawns.activeSpawns(), errors);
  }

  private MobSpec parseMobSpec(String id, ConfigurationSection node, List<String> errors, String base) {
    String typeRaw = requireString(node, "type", base + ".type");
    EntityType type;
    try {
      type = EntityType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("unknown entity type: " + typeRaw);
    }
    if (!type.isAlive()) {
      throw new IllegalArgumentException("entity type is not alive: " + typeRaw);
    }

    MobSpec.Builder builder = MobSpec.builder(id, type);
    String name = string(node, "name", null);
    if (name != null) {
      builder.displayName(name);
      builder.showName(node.getBoolean("showName", true));
    } else if (node.contains("showName")) {
      builder.showName(node.getBoolean("showName", false));
    }

    ConfigurationSection bossbar = node.getConfigurationSection("bossbar");
    if (bossbar != null && bossbar.getBoolean("enabled", true)) {
      String title = requireString(bossbar, "title", base + ".bossbar.title");
      BossBar.Color color = parseBossBarColor(string(bossbar, "color", "RED"), base + ".bossbar.color");
      BossBar.Overlay overlay = parseBossBarOverlay(string(bossbar, "overlay", "PROGRESS"), base + ".bossbar.overlay");
      MobBossBarAudience audience = parseBossBarAudience(string(bossbar, "audience", "ALL_PLAYERS"), base + ".bossbar.audience");
      builder.bossBar(new MobBossBarSpec(MobText.parse(title), color, overlay, audience));
    }

    ConfigurationSection spawnFx = node.getConfigurationSection("spawnFx");
    if (spawnFx != null) {
      builder.spawnParticles(parseParticles(spawnFx.getConfigurationSection("particles"), base + ".spawnFx.particles"));
      builder.spawnSound(parseSound(spawnFx.getConfigurationSection("sound"), base + ".spawnFx.sound"));
    }
    ConfigurationSection deathFx = node.getConfigurationSection("deathFx");
    if (deathFx != null) {
      builder.deathParticles(parseParticles(deathFx.getConfigurationSection("particles"), base + ".deathFx.particles"));
      builder.deathSound(parseSound(deathFx.getConfigurationSection("sound"), base + ".deathFx.sound"));
    }

    ConfigurationSection equipment = node.getConfigurationSection("equipment");
    if (equipment != null) {
      builder.mainHand(itemStack(equipment, "mainHand"));
      builder.offHand(itemStack(equipment, "offHand"));
      builder.head(itemStack(equipment, "head"));
      builder.chest(itemStack(equipment, "chest"));
      builder.legs(itemStack(equipment, "legs"));
      builder.feet(itemStack(equipment, "feet"));
    }

    ConfigurationSection stats = node.getConfigurationSection("stats");
    if (stats != null) {
      for (String key : stats.getKeys(false)) {
        Attribute attr = parseAttribute(key, base + ".stats." + key);
        double value = stats.getDouble(key);
        builder.attribute(attr, value);
      }
    }

    List<Map<?, ?>> variants = node.getMapList("variants");
    for (int i = 0; i < variants.size(); i++) {
      Map<?, ?> raw = variants.get(i);
      String variantId = string(raw, "id", "variant_" + i);
      double weight = doubleValue(raw, "weight", 1.0);
      String variantName = string(raw, "name", null);
      String prefix = string(raw, "namePrefix", null);
      String suffix = string(raw, "nameSuffix", null);
      double health = doubleValue(raw, "healthMultiplier", 1.0);
      double damage = doubleValue(raw, "damageMultiplier", 1.0);
      double speed = doubleValue(raw, "speedMultiplier", 1.0);
      double follow = doubleValue(raw, "followRangeMultiplier", 1.0);
      builder.addVariant(new MobVariantSpec(Ids.normalize(variantId), weight, variantName, prefix, suffix, health, damage, speed, follow));
    }

    ConfigurationSection resistances = node.getConfigurationSection("resistances");
    if (resistances != null) {
      for (String key : resistances.getKeys(false)) {
        DamageType resistType = parseDamageType(key, base + ".resistances." + key);
        double multiplier = resistances.getDouble(key);
        if (!Double.isFinite(multiplier) || multiplier < 0.0) {
          throw new IllegalArgumentException(base + ".resistances." + key + ": multiplier must be >= 0");
        }
        builder.resistance(resistType, multiplier);
      }
    }
    List<String> immunities = node.getStringList("immunities");
    for (String raw : immunities) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      DamageType immuneType = parseDamageType(raw, base + ".immunities");
      builder.resistance(immuneType, 0.0);
    }

    ConfigurationSection loot = node.getConfigurationSection("loot");
    if (loot != null) {
      boolean clearVanilla = loot.getBoolean("clearVanilla", false);
      List<MobDropSpec> drops = new ArrayList<>();
      List<Map<?, ?>> rawDrops = loot.getMapList("drops");
      for (int i = 0; i < rawDrops.size(); i++) {
        Map<?, ?> raw = rawDrops.get(i);
        String path = base + ".loot.drops[" + i + "]";
        ItemStack item = itemFromMap(raw, path);
        if (item == null) {
          throw new IllegalArgumentException(path + ".item: missing item");
        }
        double chance = doubleValue(raw, "chance", 1.0);
        int minAmount = intValue(raw, "min", item.getAmount());
        int maxAmount = intValue(raw, "max", minAmount);
        drops.add(new MobDropSpec(item, chance, minAmount, maxAmount));
      }
      builder.loot(new MobLootSpec(clearVanilla, drops));
    }

    ConfigurationSection summon = node.getConfigurationSection("summon");
    if (summon != null) {
      boolean enabled = summon.getBoolean("enabled", true);
      boolean despawnOffline = summon.getBoolean("despawnWhenOwnerOffline", true);
      double despawnDistance = summon.getDouble("despawnDistance", 0.0);
      double teleportDistance = summon.getDouble("teleportDistance", 0.0);
      builder.summonSpec(new MobSummonSpec(enabled, despawnOffline, despawnDistance, teleportDistance));
    }

    ConfigurationSection ai = node.getConfigurationSection("ai");
    if (ai != null) {
      MobAiSpec.Builder aiBuilder = MobAiSpec.builder()
          .enabled(ai.getBoolean("enabled", true))
          .overrideDefault(ai.getBoolean("overrideDefault", false))
          .aggroRadius(ai.getDouble("aggroRadius", 12.0))
          .leashRadius(ai.getDouble("leashRadius", 24.0))
          .leashTeleportRadius(ai.getDouble("leashTeleportRadius", 36.0))
          .preferLastAttacker(ai.getBoolean("preferLastAttacker", true))
          .targetSwitchCooldownTicks(ai.getLong("targetSwitchCooldownTicks", 40L))
          .fleeHealthRatio(ai.getDouble("fleeHealthRatio", 0.0))
          .fleeSpeed(ai.getDouble("fleeSpeed", 0.35))
          .idleWanderRadius(ai.getDouble("idleWanderRadius", 6.0))
          .idleWanderIntervalTicks(ai.getLong("idleWanderIntervalTicks", 80L))
          .kiteMinRange(ai.getDouble("kiteMinRange", 4.0))
          .kiteSpeed(ai.getDouble("kiteSpeed", 0.25));
      String mode = string(ai, "aggroTargetMode", "NEAREST_PLAYER");
      aiBuilder.aggroTargetMode(parseTargetMode(mode, base + ".ai.aggroTargetMode"));
      builder.aiSpec(aiBuilder.build());
    }

    ConfigurationSection attacks = node.getConfigurationSection("attacks");
    if (attacks != null) {
      ConfigurationSection main = attacks.getConfigurationSection("main");
      if (main != null) {
        builder.mainAttack(parseAttack(main, base + ".attacks.main", id, "main"));
      }
      ConfigurationSection secondary = attacks.getConfigurationSection("secondary");
      if (secondary != null) {
        builder.secondaryAttack(parseAttack(secondary, base + ".attacks.secondary", id, "secondary"));
      }
    }

    List<Map<?, ?>> passives = node.getMapList("passives");
    for (int i = 0; i < passives.size(); i++) {
      Map<?, ?> raw = passives.get(i);
      String path = base + ".passives[" + i + "]";
      String ability = string(raw, "ability", null);
      Object scriptObj = raw.get("script");
      if (scriptObj != null) {
        if (ability != null) {
          throw new IllegalArgumentException(path + ": provide either ability or script, not both");
        }
        String scriptId = "mob:" + id + ":passive:" + i;
        Action action = yamlAbilities.compileScriptAction(scriptObj, path + ".script", scriptId);
        ability = registerScriptAbility(scriptId, action, path + ".script");
      }
      if (ability == null || ability.isBlank()) {
        throw new IllegalArgumentException(path + ".ability: missing ability");
      }
      long period = longValue(raw, "periodTicks", 20L);
      builder.addPassive(new MobPassiveSpec(ability, period));
    }

    List<Map<?, ?>> phases = node.getMapList("phases");
    for (int i = 0; i < phases.size(); i++) {
      Map<?, ?> raw = phases.get(i);
      String path = base + ".phases[" + i + "]";
      builder.addPhase(parsePhase(raw, path, id, i));
    }

    MobManaDropSpec manaDrop = parseManaDrops(node, base + ".manaDrops");
    if (manaDrop != null && !manaDrop.isEmpty()) {
      builder.manaDrop(manaDrop);
    }

    return builder.build();
  }

  private MobAttackSpec parseAttack(ConfigurationSection node, String path, String mobId, String attackKey) {
    String ability = string(node, "ability", null);
    Object scriptObj = node.get("script");
    if (scriptObj != null) {
      if (ability != null) {
        throw new IllegalArgumentException(path + ": provide either ability or script, not both");
      }
      String scriptId = "mob:" + mobId + ":" + attackKey;
      Action action = yamlAbilities.compileScriptAction(scriptObj, path + ".script", scriptId);
      ability = registerScriptAbility(scriptId, action, path + ".script");
    }
    if (ability == null || ability.isBlank()) {
      throw new IllegalArgumentException(path + ".ability: missing ability");
    }
    MobAttackSpec.Builder builder = MobAttackSpec.builder(ability)
        .cooldownTicks(node.getLong("cooldownTicks", 40L))
        .trigger(parseTrigger(string(node, "trigger", "MELEE"), path + ".trigger"))
        .targetMode(parseTargetMode(string(node, "targetMode", "NEAREST_PLAYER"), path + ".targetMode"))
        .range(node.getDouble("range", 10.0))
        .chance(node.getDouble("chance", 1.0))
        .requireLineOfSight(node.getBoolean("requireLineOfSight", true))
        .requireTarget(node.getBoolean("requireTarget", true));
    if (!engine.hasAbility(ability)) {
      throw new IllegalArgumentException(path + ".ability: ability not registered: " + ability);
    }
    return builder.build();
  }

  private MobPhaseSpec parsePhase(Map<?, ?> raw, String path, String mobId, int index) {
    String id = string(raw, "id", "phase_" + (index + 1));
    double healthBelow = doubleValue(raw, "healthBelow", doubleValue(raw, "healthRatio", -1.0));
    if (healthBelow <= 0.0 || healthBelow > 1.0) {
      throw new IllegalArgumentException(path + ".healthBelow: must be in (0, 1]");
    }
    String phaseId = Ids.normalize(id);
    MobAttackSpec main = null;
    MobAttackSpec secondary = null;
    Object attacksRaw = raw.get("attacks");
    if (attacksRaw instanceof Map<?, ?> attacks) {
      Object mainRaw = attacks.get("main");
      if (mainRaw instanceof Map<?, ?> mainMap) {
        main = parseAttackMap(mainMap, path + ".attacks.main", mobId, "phase:" + phaseId + ":main");
      }
      Object secondaryRaw = attacks.get("secondary");
      if (secondaryRaw instanceof Map<?, ?> secondaryMap) {
        secondary = parseAttackMap(secondaryMap, path + ".attacks.secondary", mobId, "phase:" + phaseId + ":secondary");
      }
    }

    List<MobPassiveSpec> passives = null;
    if (raw.containsKey("passives")) {
      passives = new ArrayList<>();
      Object passivesRaw = raw.get("passives");
      if (passivesRaw instanceof List<?> list) {
        for (int i = 0; i < list.size(); i++) {
          Object entry = list.get(i);
          if (!(entry instanceof Map<?, ?> passiveMap)) {
            throw new IllegalArgumentException(path + ".passives[" + i + "]: must be an object");
          }
          String passivePath = path + ".passives[" + i + "]";
          String ability = string(passiveMap, "ability", null);
          Object scriptObj = passiveMap.get("script");
          if (scriptObj != null) {
            if (ability != null) {
              throw new IllegalArgumentException(passivePath + ": provide either ability or script, not both");
            }
            String scriptId = "mob:" + mobId + ":phase:" + phaseId + ":passive:" + i;
            Action action = yamlAbilities.compileScriptAction(scriptObj, passivePath + ".script", scriptId);
            ability = registerScriptAbility(scriptId, action, passivePath + ".script");
          }
          if (ability == null || ability.isBlank()) {
            throw new IllegalArgumentException(passivePath + ".ability: missing ability");
          }
          long period = longValue(passiveMap, "periodTicks", 20L);
          passives.add(new MobPassiveSpec(ability, period));
        }
      } else {
        throw new IllegalArgumentException(path + ".passives: must be a list");
      }
    }
    return new MobPhaseSpec(phaseId, healthBelow, main, secondary, passives);
  }

  private MobAttackSpec parseAttackMap(Map<?, ?> node, String path, String mobId, String attackKey) {
    String ability = string(node, "ability", null);
    Object scriptObj = node.get("script");
    if (scriptObj != null) {
      if (ability != null) {
        throw new IllegalArgumentException(path + ": provide either ability or script, not both");
      }
      String scriptId = "mob:" + mobId + ":" + attackKey;
      Action action = yamlAbilities.compileScriptAction(scriptObj, path + ".script", scriptId);
      ability = registerScriptAbility(scriptId, action, path + ".script");
    }
    if (ability == null || ability.isBlank()) {
      throw new IllegalArgumentException(path + ".ability: missing ability");
    }
    MobAttackSpec.Builder builder = MobAttackSpec.builder(ability)
        .cooldownTicks(longValue(node, "cooldownTicks", 40L))
        .trigger(parseTrigger(string(node, "trigger", "MELEE"), path + ".trigger"))
        .targetMode(parseTargetMode(string(node, "targetMode", "NEAREST_PLAYER"), path + ".targetMode"))
        .range(doubleValue(node, "range", 10.0))
        .chance(doubleValue(node, "chance", 1.0))
        .requireLineOfSight(boolValue(node, "requireLineOfSight", true))
        .requireTarget(boolValue(node, "requireTarget", true));
    if (!engine.hasAbility(ability)) {
      throw new IllegalArgumentException(path + ".ability: ability not registered: " + ability);
    }
    return builder.build();
  }

  private MobManaDropSpec parseManaDrops(ConfigurationSection node, String path) {
    ConfigurationSection mana = node.getConfigurationSection("manaDrops");
    if (mana == null) {
      mana = node.getConfigurationSection("manaDrop");
    }
    if (mana == null) {
      return null;
    }
    Object killerRaw = mana.get("killer");
    Object nearbyRaw = mana.get("nearby");
    MobManaDropSpec.MobManaRange killer = parseManaRange(killerRaw, path + ".killer");
    MobManaDropSpec.MobManaRange nearby = parseManaRange(nearbyRaw, path + ".nearby");
    double radius = mana.getDouble("radius", 0.0);
    if (nearbyRaw instanceof ConfigurationSection nearbySec) {
      radius = nearbySec.getDouble("radius", radius);
    } else if (nearbyRaw instanceof Map<?, ?> nearbyMap) {
      radius = doubleValue(nearbyMap, "radius", radius);
    }
    if (radius < 0.0) {
      throw new IllegalArgumentException(path + ".nearby.radius: must be >= 0");
    }
    return new MobManaDropSpec(killer, nearby, radius);
  }

  private static MobManaDropSpec.MobManaRange parseManaRange(Object raw, String path) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number n) {
      double v = n.doubleValue();
      return new MobManaDropSpec.MobManaRange(v, v);
    }
    if (raw instanceof ConfigurationSection sec) {
      double min = sec.contains("min") ? sec.getDouble("min") : sec.getDouble("amount", 0.0);
      double max = sec.contains("max") ? sec.getDouble("max") : min;
      return new MobManaDropSpec.MobManaRange(min, max);
    }
    if (raw instanceof Map<?, ?> map) {
      double min = map.containsKey("min") ? doubleValue(map, "min", 0.0) : doubleValue(map, "amount", 0.0);
      double max = map.containsKey("max") ? doubleValue(map, "max", min) : min;
      return new MobManaDropSpec.MobManaRange(min, max);
    }
    throw new IllegalArgumentException(path + ": must be a number or object");
  }

  private List<MobSpawnSpec> parseSpawns(YamlConfiguration cfg, List<String> errors) {
    List<MobSpawnSpec> out = new ArrayList<>();
    ConfigurationSection spawnsSec = cfg.getConfigurationSection("spawns");
    if (spawnsSec == null) {
      return out;
    }
    for (String key : spawnsSec.getKeys(false)) {
      ConfigurationSection node = spawnsSec.getConfigurationSection(key);
      String base = "spawns." + key;
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(key);
        String mobId = Ids.normalize(requireString(node, "mob", base + ".mob"));
        String world = requireString(node, "world", base + ".world");
        double x = node.getDouble("x");
        double y = node.getDouble("y");
        double z = node.getDouble("z");
        float yaw = (float) node.getDouble("yaw", 0.0);
        float pitch = (float) node.getDouble("pitch", 0.0);
        Location location = new Location(null, x, y, z, yaw, pitch);
        int count = (int) node.getLong("count", 1L);
        if (count <= 0) {
          throw new IllegalArgumentException(base + ".count: must be > 0");
        }
        int maxAlive = (int) node.getLong("maxAlive", count);
        if (maxAlive < count) {
          maxAlive = count;
        }
        long respawnTicks = node.getLong("respawnTicks", 200L);
        double radius = node.getDouble("radius", 0.0);
        boolean enabled = node.getBoolean("enabled", true);
        out.add(new MobSpawnSpec(id, mobId, world, location, count, maxAlive, respawnTicks, radius, enabled));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private int parseEggs(YamlConfiguration cfg, Map<String, MobSpec> specs, List<String> errors) {
    ConfigurationSection eggsSec = cfg.getConfigurationSection("eggs");
    if (eggsSec == null) {
      return 0;
    }
    int loaded = 0;
    for (String rawId : eggsSec.getKeys(false)) {
      String base = "eggs." + rawId;
      ConfigurationSection node = eggsSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        if (eggSpecs.containsKey(id)) {
          errors.add(base + ": duplicate egg id");
          continue;
        }
        String mobId = requireString(node, "mob", base + ".mob");
        if (!specs.containsKey(mobId) && !registry.has(mobId)) {
          throw new IllegalArgumentException(base + ".mob: unknown mob id: " + mobId);
        }
        ItemStack item = itemStack(node, "item");
        if (item == null) {
          String material = string(node, "material", null);
          if (material != null) {
            item = new ItemStack(parseMaterial(material, base + ".material"));
          }
        }
        if (item == null) {
          throw new IllegalArgumentException(base + ".item: missing item");
        }
        int amount = (int) node.getLong("amount", item.getAmount());
        if (amount <= 0) {
          throw new IllegalArgumentException(base + ".amount: must be > 0");
        }
        item = item.clone();
        item.setAmount(amount);
        item = MobItemMarkers.setEggId(item, id);
        item = MobItemMarkers.setMobId(item, mobId);
        String permission = string(node, "permission", null);
        long cooldownTicks = node.getLong("cooldownTicks", 0L);
        if (cooldownTicks < 0L) {
          throw new IllegalArgumentException(base + ".cooldownTicks: must be >= 0");
        }
        eggSpecs.put(id, new MobEggSpec(id, mobId, item, permission, cooldownTicks));
        loaded++;
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return loaded;
  }

  private Set<String> parseEnabledWorlds(YamlConfiguration cfg) {
    List<String> enabled = cfg.getStringList("options.enabledWorlds");
    List<String> disabled = cfg.getStringList("options.disabledWorlds");
    Set<String> out = new HashSet<>();
    for (String name : enabled) {
      if (name != null && !name.isBlank()) {
        out.add(name.toLowerCase(Locale.ROOT));
      }
    }
    List<String> disabledLower = new ArrayList<>();
    for (String name : disabled) {
      if (name != null && !name.isBlank()) {
        disabledLower.add(name.toLowerCase(Locale.ROOT));
      }
    }
    if (out.isEmpty() && !disabledLower.isEmpty()) {
      for (org.bukkit.World world : plugin.getServer().getWorlds()) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        if (!disabledLower.contains(name)) {
          out.add(name);
        }
      }
    }
    return out;
  }

  private static ItemStack itemStack(ConfigurationSection sec, String key) {
    Object raw = sec.get(key);
    if (raw == null) {
      return null;
    }
    if (raw instanceof ItemStack stack) {
      return stack.getType() == Material.AIR ? null : stack.clone();
    }
    if (raw instanceof String str) {
      Material material = parseMaterial(str, sec.getCurrentPath() + "." + key);
      return material == Material.AIR ? null : new ItemStack(material);
    }
    if (raw instanceof ConfigurationSection child) {
      Map<String, Object> map = child.getValues(false);
      if (!map.containsKey("material") && map.containsKey("type")) {
        map.put("material", map.get("type"));
      }
      ItemStack item = itemFromMap(map, sec.getCurrentPath() + "." + key);
      if (item == null || item.getType() == Material.AIR) {
        return null;
      }
      int amount = intValue(map, "amount", item.getAmount());
      item.setAmount(Math.max(1, amount));
      return item;
    }
    return null;
  }

  private static Material parseMaterial(String raw, String path) {
    try {
      return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": unknown material=" + raw);
    }
  }

  private static String requireString(ConfigurationSection sec, String key, String path) {
    String value = sec.getString(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(path + ": missing " + key);
    }
    return value;
  }

  private static String string(ConfigurationSection sec, String key, String def) {
    String value = sec.getString(key);
    return value == null || value.isBlank() ? def : value;
  }

  private static String string(Map<?, ?> map, String key, String def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    String value = String.valueOf(raw);
    return value.isBlank() ? def : value;
  }

  private static int intValue(Map<?, ?> map, String key, int def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected integer");
    }
  }

  private static long longValue(Map<?, ?> map, String key, long def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  private static double doubleValue(Map<?, ?> map, String key, double def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    if (raw instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(key + ": expected number");
    }
  }

  private static boolean boolValue(Map<?, ?> map, String key, boolean def) {
    Object raw = map.get(key);
    if (raw == null) {
      return def;
    }
    if (raw instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(raw));
  }

  private static Attribute parseAttribute(String raw, String path) {
    String key = raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    return switch (key) {
      case "maxhealth", "health" -> Attribute.MAX_HEALTH;
      case "attackdamage", "damage" -> Attribute.ATTACK_DAMAGE;
      case "movementspeed", "speed" -> Attribute.MOVEMENT_SPEED;
      case "followrange", "range" -> Attribute.FOLLOW_RANGE;
      case "knockbackresistance" -> Attribute.KNOCKBACK_RESISTANCE;
      case "armor" -> Attribute.ARMOR;
      case "armortoughness" -> Attribute.ARMOR_TOUGHNESS;
      default -> throw new IllegalArgumentException(path + ": unknown stat " + raw);
    };
  }

  private static DamageType parseDamageType(String raw, String path) {
    String key = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return DamageType.valueOf(key);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid damage type " + raw);
    }
  }

  private static ItemStack itemFromMap(Map<?, ?> map, String path) {
    Object raw = map.get("item");
    if (raw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (raw instanceof Map<?, ?> mapItem) {
      return ItemStack.deserialize(castMap(mapItem));
    }
    Object materialRaw = map.get("material");
    if (materialRaw == null) {
      materialRaw = map.get("type");
    }
    if (materialRaw != null) {
      Material material = parseMaterial(String.valueOf(materialRaw), path + ".material");
      return new ItemStack(material);
    }
    return null;
  }

  private static Map<String, Object> castMap(Map<?, ?> map) {
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      out.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return out;
  }

  private static MobAttackTrigger parseTrigger(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MobAttackTrigger.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid trigger=" + raw);
    }
  }

  private static MobTargetMode parseTargetMode(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MobTargetMode.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid targetMode=" + raw);
    }
  }

  private static MobBossBarAudience parseBossBarAudience(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MobBossBarAudience.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid audience=" + raw);
    }
  }

  private static BossBar.Color parseBossBarColor(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return BossBar.Color.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid color=" + raw);
    }
  }

  private static BossBar.Overlay parseBossBarOverlay(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return BossBar.Overlay.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid overlay=" + raw);
    }
  }

  private static MobParticlesSpec parseParticles(ConfigurationSection sec, String path) {
    if (sec == null) {
      return null;
    }
    String particleRaw = requireString(sec, "particle", path + ".particle");
    Particle particle;
    String token = particleRaw.contains(":") ? particleRaw.substring(particleRaw.indexOf(':') + 1) : particleRaw;
    try {
      particle = Particle.valueOf(token.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ".particle: unknown particle=" + particleRaw);
    }
    int count = sec.getInt("count", 10);
    double offsetX = sec.getDouble("offsetX", sec.getDouble("offset", 0.2));
    double offsetY = sec.getDouble("offsetY", sec.getDouble("offset", 0.2));
    double offsetZ = sec.getDouble("offsetZ", sec.getDouble("offset", 0.2));
    double extra = sec.getDouble("extra", 0.0);
    return new MobParticlesSpec(particle, count, offsetX, offsetY, offsetZ, extra);
  }

  private static MobSoundSpec parseSound(ConfigurationSection sec, String path) {
    if (sec == null) {
      return null;
    }
    String raw = requireString(sec, "sound", path + ".sound");
    NamespacedKey key = raw.contains(":")
        ? NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT))
        : NamespacedKey.fromString("minecraft:" + raw.toLowerCase(Locale.ROOT));
    if (key == null) {
      throw new IllegalArgumentException(path + ".sound: invalid key=" + raw);
    }
    Sound sound = Registry.SOUND_EVENT.get(key);
    if (sound == null) {
      throw new IllegalArgumentException(path + ".sound: unknown sound=" + raw);
    }
    float volume = (float) sec.getDouble("volume", 1.0);
    float pitch = (float) sec.getDouble("pitch", 1.0);
    return new MobSoundSpec(sound, volume, pitch);
  }

  private String registerScriptAbility(String abilityId, Action action, String path) {
    String normalized = Ids.normalize(abilityId);
    try {
      engine.registerAbility(AbilitySpec.builder(normalized).action(action).build());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(path + ": " + ex.getMessage());
    }
    loadedScriptAbilityIds.add(normalized);
    return normalized;
  }
}
