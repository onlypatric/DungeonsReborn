package dev.patric.dungeonsreborn.mobs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffectType;

import dev.patric.dungeonsreborn.effects.AbilitySpec;
import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.actions.Action;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;
import dev.patric.dungeonsreborn.effects.damage.DamageType;
import dev.patric.dungeonsreborn.effects.upgrades.UpgradeYamlRegistry;
import dev.patric.dungeonsreborn.logging.ServiceLogger;
import dev.patric.dungeonsreborn.shops.ShopYamlRegistry;
import dev.patric.dungeonsreborn.system.SystemStatusStore;
import dev.patric.dungeonsreborn.util.YamlValues;
import net.kyori.adventure.text.Component;

import net.kyori.adventure.bossbar.BossBar;

public final class MobYamlRegistry {
  private static final String DEFAULT_BOSS_BROADCAST =
      "<gold>{player}</gold> defeated <red>{mob}</red>!";
  private static final String[] DEFAULT_MOB_FILES = {
      "undead_t1_bonewalker.yml",
      "undead_t1_boneshiver.yml",
      "undead_t1_cinderbrand.yml",
      "undead_t1_cryptleap.yml",
      "undead_t1_dustcaller.yml",
      "undead_t1_gloomthrall.yml",
      "undead_t1_gravebound.yml",
      "undead_t1_gravetouched.yml",
      "undead_t1_ironjaw.yml",
      "undead_t1_marshrot.yml",
      "undead_t1_nightmoan.yml",
      "undead_t1_tombwarden.yml",
      "undead_t2_blightshot.yml",
      "undead_t2_boneguard.yml",
      "undead_t2_cryptarcher.yml",
      "undead_t2_dreadmonger.yml",
      "undead_t2_frostvein.yml",
      "undead_t2_graveknight.yml",
      "undead_t2_grimcharger.yml",
      "undead_t2_marrowmage.yml",
      "undead_t2_mirestalker.yml",
      "undead_t2_plaguebearer.yml",
      "undead_t2_rotfang.yml",
      "undead_t2_wailreaper.yml",
      "undead_t3_boneward.yml",
      "undead_t3_boneshard.yml",
      "undead_t3_cinderwretch.yml",
      "undead_t3_cryptstalker.yml",
      "undead_t3_dreadcaster.yml",
      "undead_t3_frostmarshal.yml",
      "undead_t3_gravewarden.yml",
      "undead_t3_gravetide.yml",
      "undead_t3_marshhexer.yml",
      "undead_t3_nightgaunt.yml",
      "undead_t3_rotreaver.yml",
      "undead_t3_wailbringer.yml"
  };
  public record ReloadResult(int loadedMobs, int loadedSpawns, List<String> errors) {
  }
  private record YamlSource(String source, YamlConfiguration cfg) {
  }
  private record LootSource(String source, YamlConfiguration cfg, String poolId) {
  }

  private final JavaPlugin plugin;
  private final EffectsEngine engine;
  private final EffectsYamlAbilities yamlAbilities;
  private final ShopYamlRegistry shopRegistry;
  private UpgradeYamlRegistry upgradeRegistry;
  private final MobRegistry registry;
  private final MobSpawnManager spawns;
  private final ServiceLogger logger;
  private final Set<String> loadedIds = new HashSet<>();
  private final Set<String> loadedScriptAbilityIds = new HashSet<>();
  private final Map<String, MobEggSpec> eggSpecs = new HashMap<>();
  private final Map<String, MobSpawnerBlockSpec> spawnerBlocks = new HashMap<>();
  private final Map<String, MobLootSpec> lootPools = new HashMap<>();
  private final Map<String, MobStyleSpec> stylePresets = new HashMap<>();
  private List<String> lastErrors = List.of();

  public MobYamlRegistry(JavaPlugin plugin, EffectsEngine engine, EffectsYamlAbilities yamlAbilities,
      ShopYamlRegistry shopRegistry, MobRegistry registry, MobSpawnManager spawns, ServiceLogger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.engine = Objects.requireNonNull(engine, "engine");
    this.yamlAbilities = Objects.requireNonNull(yamlAbilities, "yamlAbilities");
    this.shopRegistry = shopRegistry;
    this.upgradeRegistry = null;
    this.registry = Objects.requireNonNull(registry, "registry");
    this.spawns = Objects.requireNonNull(spawns, "spawns");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void setUpgradeRegistry(UpgradeYamlRegistry upgradeRegistry) {
    this.upgradeRegistry = upgradeRegistry;
  }

  public File file() {
    return new File(plugin.getDataFolder(), "mobs.yml");
  }

  public File folder() {
    return new File(plugin.getDataFolder(), "mobs");
  }

  public File lootFolder() {
    return new File(plugin.getDataFolder(), "loot");
  }

  public List<String> lastErrors() {
    return lastErrors;
  }

  public JavaPlugin plugin() {
    return plugin;
  }

  public ServiceLogger logger() {
    return logger;
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

  public MobSpawnerBlockSpec spawnerBlockSpec(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    return spawnerBlocks.get(Ids.normalize(id));
  }

  public ItemStack spawnerBlockItem(String id) {
    MobSpawnerBlockSpec spec = spawnerBlockSpec(id);
    if (spec == null) {
      return null;
    }
    return MobSpawnerItems.createSpawnerBlockItem(spec);
  }

  public ItemStack spawnerBlockItemForMob(String mobId) {
    if (mobId == null || mobId.isBlank()) {
      return null;
    }
    String normalized = Ids.normalize(mobId);
    for (MobSpawnerBlockSpec spec : spawnerBlocks.values()) {
      if (spec == null || spec.mobId() == null) {
        continue;
      }
      if (normalized.equals(Ids.normalize(spec.mobId()))) {
        return MobSpawnerItems.createSpawnerBlockItem(spec);
      }
    }
    return null;
  }

  public MobSpawnerBlockSpec spawnerBlockSpecForMob(String mobId) {
    if (mobId == null || mobId.isBlank()) {
      return null;
    }
    String normalized = Ids.normalize(mobId);
    for (MobSpawnerBlockSpec spec : spawnerBlocks.values()) {
      if (spec == null || spec.mobId() == null) {
        continue;
      }
      if (normalized.equals(Ids.normalize(spec.mobId()))) {
        return spec;
      }
    }
    return null;
  }

  public Set<String> spawnerBlockIds() {
    return Set.copyOf(spawnerBlocks.keySet());
  }

  public Set<String> lootPoolIds() {
    return Set.copyOf(lootPools.keySet());
  }

  public MobLootSpec lootPool(String id) {
    if (id == null) {
      return null;
    }
    return lootPools.get(Ids.normalize(id));
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
    List<YamlSource> sources = new ArrayList<>();
    sources.add(new YamlSource(file.getPath(), cfg));
    File folder = folder();
    if (!folder.exists()) {
      folder.mkdirs();
    }
    if (listYamlFiles(folder).isEmpty()) {
      saveDefaultMobFiles(folder);
    }
    for (File extra : listYamlFiles(folder)) {
      YamlConfiguration extraCfg = YamlConfiguration.loadConfiguration(extra);
      sources.add(new YamlSource(extra.getPath(), extraCfg));
    }
    File lootFolder = lootFolder();
    if (!lootFolder.exists()) {
      lootFolder.mkdirs();
    }
    if (listYamlFiles(lootFolder).isEmpty()) {
      saveDefaultLootFiles(lootFolder);
    }
    List<LootSource> lootSources = new ArrayList<>();
    for (File extra : listYamlFiles(lootFolder)) {
      YamlConfiguration extraCfg = YamlConfiguration.loadConfiguration(extra);
      lootSources.add(new LootSource(extra.getPath(), extraCfg, poolIdFromFile(extra)));
    }

    Set<String> previousLoadedIds = new HashSet<>(loadedIds);
    Map<String, MobSpec> previousSpecs = new LinkedHashMap<>();
    for (String id : previousLoadedIds) {
      MobSpec spec = registry.get(id);
      if (spec != null) {
        previousSpecs.put(id, spec);
      }
    }
    Set<String> previousScriptAbilityIds = new HashSet<>(loadedScriptAbilityIds);
    Map<String, AbilitySpec> previousScriptAbilities = new HashMap<>();
    for (String id : previousScriptAbilityIds) {
      AbilitySpec spec = engine.abilitySpec(id);
      if (spec != null) {
        previousScriptAbilities.put(id, spec);
      }
    }

    for (String abilityId : loadedScriptAbilityIds) {
      engine.unregisterAbility(abilityId);
    }
    loadedScriptAbilityIds.clear();

    Map<String, MobLootSpec> nextLootPools = new HashMap<>();
    for (YamlSource source : sources) {
      parseLootPools(source.cfg(), nextLootPools, errors, source.source(), null);
    }
    for (LootSource source : lootSources) {
      parseLootPools(source.cfg(), nextLootPools, errors, source.source(), source.poolId());
    }
    lootPools.clear();
    lootPools.putAll(nextLootPools);
    int loadedLootPools = nextLootPools.size();

    Map<String, MobStyleSpec> nextStylePresets = new HashMap<>();
    for (YamlSource source : sources) {
      parseStylePresets(source.cfg(), nextStylePresets, errors, source.source());
    }
    stylePresets.clear();
    stylePresets.putAll(nextStylePresets);

    Map<String, MobSpec> specs = new HashMap<>();
    for (YamlSource source : sources) {
      parseMobs(source.cfg(), specs, errors, source.source());
    }

    Map<String, MobEggSpec> nextEggs = new HashMap<>();
    Map<String, MobSpawnerBlockSpec> nextBlocks = new HashMap<>();
    int loadedEggs = 0;
    int loadedBlocks = 0;
    List<MobSpawnSpec> spawnSpecs = new ArrayList<>();
    for (YamlSource source : sources) {
      loadedEggs += parseEggs(source.cfg(), specs, nextEggs, errors, source.source());
      loadedBlocks += parseSpawnerBlocks(source.cfg(), specs, nextBlocks, errors, source.source());
      spawnSpecs.addAll(parseSpawns(source.cfg(), specs, errors, source.source()));
    }
    Set<String> enabledWorlds = parseEnabledWorlds(cfg);
    boolean despawnOnReload = cfg.getBoolean("options.despawnOnReload", false);
    int maxSpawnersPerTick = Math.max(0, cfg.getInt("options.maxSpawnersPerTick", 0));

    for (String id : previousLoadedIds) {
      registry.unregister(id);
    }
    loadedIds.clear();
    int loaded = 0;
    for (MobSpec spec : specs.values()) {
      registry.register(spec);
      loadedIds.add(spec.id());
      loaded++;
    }
    eggSpecs.clear();
    eggSpecs.putAll(nextEggs);
    spawnerBlocks.clear();
    spawnerBlocks.putAll(nextBlocks);
    spawns.setMaxSpawnersPerTick(maxSpawnersPerTick);
    spawns.reload(spawnSpecs, enabledWorlds, despawnOnReload);

    if (!errors.isEmpty()) {
      logger.warn("[Mobs] YAML reload had " + errors.size() + " errors (some mobs/spawns may be missing)");
      for (String error : errors) {
        logger.warn("[Mobs] YAML: " + error);
      }
    } else {
      logger.info("[Mobs] YAML loaded " + loaded + " mobs, " + loadedEggs + " eggs, "
          + loadedBlocks + " spawner blocks, and " + spawns.activeSpawns() + " spawns");
    }
    lastErrors = List.copyOf(errors);
    SystemStatusStore.get().record(
        "mobs",
        "Mobs",
        file().getPath(),
        "mobs=" + loaded + ", spawns=" + spawns.activeSpawns() + ", eggs=" + loadedEggs,
        errors);
    SystemStatusStore.get().record(
        "mobLoot",
        "Mob Loot",
        lootFolder().getPath(),
        "pools=" + loadedLootPools,
        filterLootErrors(errors));

    return new ReloadResult(loaded, spawns.activeSpawns(), errors);
  }

  private static List<String> filterLootErrors(List<String> errors) {
    if (errors == null || errors.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String error : errors) {
      if (error == null) {
        continue;
      }
      String lower = error.toLowerCase();
      if (lower.contains("/loot/") || lower.contains("\\loot\\") || lower.contains(".loot")
          || lower.contains("lootpools") || lower.contains("loot pool")) {
        out.add(error);
      }
    }
    return out;
  }

  public String createSpawn(String desiredId, String mobId, Location location) {
    return createSpawn(desiredId, mobId, location, null);
  }

  public String createSpawnFromTemplate(MobSpawnerBlockSpec spec, String desiredId, Location location) {
    if (spec == null) {
      throw new IllegalArgumentException("spawner block spec is null");
    }
    return createSpawn(desiredId, spec.mobId(), location, spec.template());
  }

  public String createSpawn(String desiredId, String mobId, Location location, MobSpawnerTemplate template) {
    Objects.requireNonNull(mobId, "mobId");
    Objects.requireNonNull(location, "location");
    String normalizedMob = Ids.normalize(mobId);
    if (!registry.has(normalizedMob)) {
      throw new IllegalArgumentException("unknown mob id: " + normalizedMob);
    }
    String id = desiredId == null || desiredId.isBlank()
        ? generateSpawnId(normalizedMob)
        : Ids.normalize(desiredId);
    YamlConfiguration cfg = loadConfig();
    ConfigurationSection spawnsSec = cfg.getConfigurationSection("spawns");
    if (spawnsSec == null) {
      spawnsSec = cfg.createSection("spawns");
    }
    if (spawnsSec.contains(id)) {
      throw new IllegalArgumentException("spawn id already exists: " + id);
    }
    ConfigurationSection node = spawnsSec.createSection(id);
    node.set("mob", normalizedMob);
    node.set("world", location.getWorld() == null ? "world" : location.getWorld().getName());
    node.set("x", location.getX());
    node.set("y", location.getY());
    node.set("z", location.getZ());
    node.set("yaw", location.getYaw());
    node.set("pitch", location.getPitch());
    node.set("count", 1);
    node.set("maxAlive", 1);
    node.set("respawnTicks", 200);
    node.set("radius", 0.0);
    node.set("enabled", true);
    applyTemplate(node, template, normalizedMob);
    saveConfig(cfg);
    reload();
    return id;
  }

  public boolean removeSpawn(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    String normalized = Ids.normalize(id);
    YamlConfiguration cfg = loadConfig();
    ConfigurationSection spawnsSec = cfg.getConfigurationSection("spawns");
    if (spawnsSec == null || !spawnsSec.contains(normalized)) {
      return false;
    }
    spawnsSec.set(normalized, null);
    saveConfig(cfg);
    reload();
    return true;
  }

  public boolean relocateSpawn(String id, Location location, String mobId) {
    if (id == null || id.isBlank()) {
      return false;
    }
    Objects.requireNonNull(location, "location");
    String normalized = Ids.normalize(id);
    YamlConfiguration cfg = loadConfig();
    ConfigurationSection spawnsSec = cfg.getConfigurationSection("spawns");
    if (spawnsSec == null) {
      return false;
    }
    ConfigurationSection node = spawnsSec.getConfigurationSection(normalized);
    if (node == null) {
      return false;
    }
    if (mobId != null && !mobId.isBlank()) {
      String normalizedMob = Ids.normalize(mobId);
      String existingMob = YamlValues.string(node, "mob", null);
      if (existingMob != null && !Ids.normalize(existingMob).equals(normalizedMob)) {
        throw new IllegalArgumentException("spawn mob mismatch: expected " + existingMob + " got " + normalizedMob);
      }
      node.set("mob", normalizedMob);
    }
    node.set("world", location.getWorld() == null ? "world" : location.getWorld().getName());
    node.set("x", location.getX());
    node.set("y", location.getY());
    node.set("z", location.getZ());
    node.set("yaw", location.getYaw());
    node.set("pitch", location.getPitch());
    saveConfig(cfg);
    reload();
    return true;
  }

  private void applyTemplate(ConfigurationSection node, MobSpawnerTemplate template, String mobId) {
    if (template == null) {
      return;
    }
    if (template.count() != null) {
      if (template.count() <= 0) {
        throw new IllegalArgumentException("spawn.count must be > 0");
      }
      node.set("count", template.count());
    }
    if (template.maxAlive() != null) {
      if (template.maxAlive() <= 0) {
        throw new IllegalArgumentException("spawn.maxAlive must be > 0");
      }
      node.set("maxAlive", template.maxAlive());
    }
    if (template.groupId() != null && !template.groupId().isBlank()) {
      node.set("group", Ids.normalize(template.groupId()));
    }
    if (template.groupMaxAlive() != null) {
      if (template.groupMaxAlive() < 0) {
        throw new IllegalArgumentException("spawn.groupMaxAlive must be >= 0");
      }
      node.set("groupMaxAlive", template.groupMaxAlive());
    }
    if (template.groups() != null && !template.groups().isEmpty()) {
      node.set("groups", serializeSpawnerGroups(template.groups()));
    }
    if (template.rules() != null && !template.rules().isEmpty()) {
      node.set("rules", serializeSpawnRules(template.rules()));
    }
    if (template.maxAlivePerChunk() != null) {
      if (template.maxAlivePerChunk() < 0) {
        throw new IllegalArgumentException("spawn.maxAlivePerChunk must be >= 0");
      }
      node.set("maxAlivePerChunk", template.maxAlivePerChunk());
    }
    if (template.maxAlivePerPlayer() != null) {
      if (template.maxAlivePerPlayer() < 0) {
        throw new IllegalArgumentException("spawn.maxAlivePerPlayer must be >= 0");
      }
      node.set("maxAlivePerPlayer", template.maxAlivePerPlayer());
    }
    if (template.respawnTicks() != null) {
      if (template.respawnTicks() < 0L) {
        throw new IllegalArgumentException("spawn.respawnTicks must be >= 0");
      }
      node.set("respawnTicks", template.respawnTicks());
    }
    if (template.respawnJitterTicks() != null) {
      if (template.respawnJitterTicks() < 0L) {
        throw new IllegalArgumentException("spawn.respawnJitterTicks must be >= 0");
      }
      node.set("respawnJitterTicks", template.respawnJitterTicks());
    }
    if (template.lifespanTicks() != null) {
      if (template.lifespanTicks() < 0L) {
        throw new IllegalArgumentException("spawn.lifespanTicks must be >= 0");
      }
      node.set("lifespanTicks", template.lifespanTicks());
    }
    if (template.spawnerDecayTicks() != null) {
      if (template.spawnerDecayTicks() < 0L) {
        throw new IllegalArgumentException("spawn.spawnerDecayTicks must be >= 0");
      }
      node.set("spawnerDecayTicks", template.spawnerDecayTicks());
    }
    if (template.radius() != null) {
      if (template.radius() < 0.0) {
        throw new IllegalArgumentException("spawn.radius must be >= 0");
      }
      node.set("radius", template.radius());
    }
    if (template.allowBlockDamage() != null) {
      node.set("allowBlockDamage", template.allowBlockDamage());
    }
    if (template.activationRadius() != null) {
      if (template.activationRadius() < 0.0) {
        throw new IllegalArgumentException("spawn.activationRadius must be >= 0");
      }
      node.set("activationRadius", template.activationRadius());
    }
    boolean hasBeam = template.beamEnabled() != null
        || template.beamParticle() != null
        || template.beamStep() != null;
    if (hasBeam) {
      ConfigurationSection beam = node.getConfigurationSection("beam");
      if (beam == null) {
        beam = node.createSection("beam");
      }
      if (template.beamEnabled() != null) {
        beam.set("enabled", template.beamEnabled());
      }
      if (template.beamParticle() != null) {
        beam.set("particle", template.beamParticle().name());
      }
      if (template.beamStep() != null) {
        beam.set("step", template.beamStep());
      }
    }
    if (template.respectDifficulty() != null) {
      node.set("respectDifficulty", template.respectDifficulty());
    }
    if (template.respectGameRules() != null) {
      node.set("respectGameRules", template.respectGameRules());
    }
    if (template.attackRadius() != null) {
      if (template.attackRadius() < 0.0) {
        throw new IllegalArgumentException("spawn.attackRadius must be >= 0");
      }
      node.set("attackRadius", template.attackRadius());
    }
    if (template.attackIgnoreOutsideRadius() != null) {
      node.set("attackIgnoreOutsideRadius", template.attackIgnoreOutsideRadius());
    }
    if (template.attackIgnorePlayers() != null) {
      node.set("attackIgnorePlayers", template.attackIgnorePlayers());
    }
    boolean hasTether = template.tetherRadius() != null
        || template.tetherAction() != null
        || template.tetherPullSpeed() != null
        || template.tetherDespawnTicks() != null;
    if (hasTether) {
      ConfigurationSection tether = node.getConfigurationSection("tether");
      if (tether == null) {
        tether = node.createSection("tether");
      }
      if (template.tetherRadius() != null) {
        if (template.tetherRadius() < 0.0) {
          throw new IllegalArgumentException("spawn.tether.radius must be >= 0");
        }
        tether.set("radius", template.tetherRadius());
      }
      if (template.tetherAction() != null) {
        tether.set("action", template.tetherAction().name().toLowerCase(Locale.ROOT));
      }
      if (template.tetherPullSpeed() != null) {
        if (template.tetherPullSpeed() < 0.0) {
          throw new IllegalArgumentException("spawn.tether.pullSpeed must be >= 0");
        }
        tether.set("pullSpeed", template.tetherPullSpeed());
      }
      if (template.tetherDespawnTicks() != null) {
        if (template.tetherDespawnTicks() < 0L) {
          throw new IllegalArgumentException("spawn.tether.despawnTicks must be >= 0");
        }
        tether.set("despawnTicks", template.tetherDespawnTicks());
      }
    }
    boolean hasHologram = template.hologramEnabled() != null
        || template.hologramOffsetY() != null
        || template.hologramFormat() != null;
    if (hasHologram) {
      ConfigurationSection hologram = node.getConfigurationSection("hologram");
      if (hologram == null) {
        hologram = node.createSection("hologram");
      }
      if (template.hologramEnabled() != null) {
        hologram.set("enabled", template.hologramEnabled());
      }
      if (template.hologramOffsetY() != null) {
        hologram.set("offsetY", template.hologramOffsetY());
      }
      if (template.hologramFormat() != null) {
        hologram.set("format", template.hologramFormat());
      }
    }
    if (template.enabled() != null) {
      node.set("enabled", template.enabled());
    }
  }

  public boolean setSpawnEnabled(String id, boolean enabled) {
    if (id == null || id.isBlank()) {
      return false;
    }
    String normalized = Ids.normalize(id);
    YamlConfiguration cfg = loadConfig();
    ConfigurationSection spawnsSec = cfg.getConfigurationSection("spawns");
    if (spawnsSec == null) {
      return false;
    }
    ConfigurationSection node = spawnsSec.getConfigurationSection(normalized);
    if (node == null) {
      return false;
    }
    node.set("enabled", enabled);
    saveConfig(cfg);
    reload();
    return true;
  }

  private YamlConfiguration loadConfig() {
    plugin.getDataFolder().mkdirs();
    return YamlConfiguration.loadConfiguration(file());
  }

  private void saveConfig(YamlConfiguration cfg) {
    try {
      cfg.save(file());
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to save mobs.yml: " + ex.getMessage(), ex);
    }
  }

  private String generateSpawnId(String mobId) {
    String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
    return Ids.normalize("spawn_" + mobId + "_" + suffix);
  }

  private int parseSpawnerBlocks(YamlConfiguration cfg, Map<String, MobSpec> specs, Map<String, MobSpawnerBlockSpec> out,
                                 List<String> errors, String source) {
    ConfigurationSection blocksSec = cfg.getConfigurationSection("spawnerBlocks");
    if (blocksSec == null) {
      return 0;
    }
    int loaded = 0;
    for (String rawId : blocksSec.getKeys(false)) {
      String base = prefix(source) + "spawnerBlocks." + rawId;
      ConfigurationSection node = blocksSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        if (out.containsKey(id)) {
          errors.add(base + ": duplicate spawner block id");
          continue;
        }
        String mobId = Ids.normalize(YamlValues.requireString(node, "mob", base + ".mob"));
        if (!specs.containsKey(mobId) && !registry.has(mobId)) {
          throw new IllegalArgumentException(base + ".mob: unknown mob id: " + mobId);
        }
        ItemStack item = itemStack(node, "item");
        if (item == null) {
          String material = YamlValues.string(node, "material", null);
          if (material != null) {
            item = new ItemStack(parseMaterial(material, base + ".material"));
          }
        }
        MobSpawnerTemplate template = null;
        ConfigurationSection spawn = node.getConfigurationSection("spawn");
        if (spawn != null) {
          template = parseSpawnerTemplate(spawn, base + ".spawn");
        }
        MobSpawnerBlockSpec spec = new MobSpawnerBlockSpec(id, mobId, item, template);
        out.put(id, spec);
        loaded++;
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return loaded;
  }

  private MobSpawnerTemplate parseSpawnerTemplate(ConfigurationSection node, String base) {
    Integer count = node.contains("count") ? node.getInt("count") : null;
    Integer maxAlive = node.contains("maxAlive") ? node.getInt("maxAlive") : null;
    String groupId = YamlValues.string(node, "group", null);
    if (groupId == null) {
      groupId = YamlValues.string(node, "groupId", null);
    }
    Integer groupMaxAlive = node.contains("groupMaxAlive") ? node.getInt("groupMaxAlive") : null;
    List<MobSpawnGroupSpec> groups = parseSpawnerGroups(node.getMapList("groups"), base + ".groups");
    MobSpawnRulesSpec rules = parseSpawnRules(node.get("rules"), base + ".rules");
    Integer maxAlivePerChunk = node.contains("maxAlivePerChunk") ? node.getInt("maxAlivePerChunk") : null;
    Integer maxAlivePerPlayer = node.contains("maxAlivePerPlayer") ? node.getInt("maxAlivePerPlayer") : null;
    ConfigurationSection density = node.getConfigurationSection("density");
    if (density != null) {
      if (density.contains("maxAlivePerChunk")) {
        maxAlivePerChunk = density.getInt("maxAlivePerChunk");
      }
      if (density.contains("maxAlivePerPlayer")) {
        maxAlivePerPlayer = density.getInt("maxAlivePerPlayer");
      }
    }
    Long respawnTicks = node.contains("respawnTicks") ? node.getLong("respawnTicks") : null;
    Long respawnJitterTicks = node.contains("respawnJitterTicks") ? node.getLong("respawnJitterTicks") : null;
    if (node.contains("respawnJitterSeconds")) {
      respawnJitterTicks = Math.round(node.getDouble("respawnJitterSeconds") * 20.0);
    } else if (node.contains("respawnJitter")) {
      respawnJitterTicks = Math.round(node.getDouble("respawnJitter") * 20.0);
    }
    Long lifespanTicks = node.contains("lifespanTicks") ? node.getLong("lifespanTicks") : null;
    if (lifespanTicks == null && node.contains("despawnTicks")) {
      lifespanTicks = node.getLong("despawnTicks");
    }
    Long spawnerDecayTicks = node.contains("spawnerDecayTicks") ? node.getLong("spawnerDecayTicks") : null;
    if (spawnerDecayTicks == null && node.contains("decayTicks")) {
      spawnerDecayTicks = node.getLong("decayTicks");
    }
    Double radius = node.contains("radius") ? node.getDouble("radius") : null;
    Boolean allowBlockDamage = node.contains("allowBlockDamage") ? node.getBoolean("allowBlockDamage") : null;
    if (allowBlockDamage == null && node.contains("blockDamage")) {
      allowBlockDamage = node.getBoolean("blockDamage");
    }
    if (allowBlockDamage == null && node.contains("noBlockDamage")) {
      allowBlockDamage = !node.getBoolean("noBlockDamage");
    }
    Double activationRadius = node.contains("activationRadius") ? node.getDouble("activationRadius") : null;
    Boolean beamEnabled = null;
    Particle beamParticle = null;
    Double beamStep = null;
    ConfigurationSection beam = node.getConfigurationSection("beam");
    if (beam != null) {
      if (beam.contains("enabled")) {
        beamEnabled = beam.getBoolean("enabled");
      }
      if (beam.contains("particle")) {
        beamParticle = parseParticleType(YamlValues.requireString(beam, "particle", base + ".beam.particle"), base + ".beam.particle");
      }
      if (beam.contains("step")) {
        beamStep = beam.getDouble("step");
      }
    }
    if (beamStep != null && beamStep <= 0.0) {
      throw new IllegalArgumentException(base + ".beam.step: must be > 0");
    }
    Boolean respectDifficulty = node.contains("respectDifficulty") ? node.getBoolean("respectDifficulty") : null;
    Boolean respectGameRules = node.contains("respectGameRules") ? node.getBoolean("respectGameRules") : null;
    Double attackRadius = node.contains("attackRadius") ? node.getDouble("attackRadius") : null;
    Boolean attackIgnoreOutsideRadius = node.contains("attackIgnoreOutsideRadius") ? node.getBoolean("attackIgnoreOutsideRadius") : null;
    Boolean attackIgnorePlayers = node.contains("attackIgnorePlayers") ? node.getBoolean("attackIgnorePlayers") : null;
    ConfigurationSection attack = node.getConfigurationSection("attack");
    if (attack != null) {
      if (attack.contains("radius")) {
        attackRadius = attack.getDouble("radius");
      }
      if (attack.contains("ignoreOutsideRadius")) {
        attackIgnoreOutsideRadius = attack.getBoolean("ignoreOutsideRadius");
      }
      if (attack.contains("ignorePlayers")) {
        attackIgnorePlayers = attack.getBoolean("ignorePlayers");
      }
    }
    Double tetherRadius = node.contains("tetherRadius") ? node.getDouble("tetherRadius") : null;
    MobSpawnTetherAction tetherAction = null;
    Double tetherPullSpeed = node.contains("tetherPullSpeed") ? node.getDouble("tetherPullSpeed") : null;
    Long tetherDespawnTicks = node.contains("tetherDespawnTicks") ? node.getLong("tetherDespawnTicks") : null;
    ConfigurationSection tether = node.getConfigurationSection("tether");
    if (tether != null) {
      if (tether.contains("radius")) {
        tetherRadius = tether.getDouble("radius");
      }
      if (tether.contains("action")) {
        tetherAction = MobSpawnTetherAction.parse(YamlValues.string(tether, "action", null), base + ".tether.action");
      }
      if (tether.contains("pullSpeed")) {
        tetherPullSpeed = tether.getDouble("pullSpeed");
      }
      if (tether.contains("despawnTicks")) {
        tetherDespawnTicks = tether.getLong("despawnTicks");
      }
    }
    Boolean hologramEnabled = null;
    Double hologramOffsetY = null;
    String hologramFormat = null;
    String hologramTitle = null;
    ConfigurationSection hologram = node.getConfigurationSection("hologram");
    if (hologram != null) {
      if (hologram.contains("enabled")) {
        hologramEnabled = hologram.getBoolean("enabled");
      }
      if (hologram.contains("offsetY")) {
        hologramOffsetY = hologram.getDouble("offsetY");
      }
      if (hologram.contains("title")) {
        hologramTitle = YamlValues.string(hologram, "title", null);
      }
      if (hologram.contains("format")) {
        hologramFormat = YamlValues.string(hologram, "format", null);
      }
    }
    if ((hologramFormat == null || hologramFormat.isBlank()) && hologramTitle != null && !hologramTitle.isBlank()) {
      hologramFormat = "<gold>" + hologramTitle + "</gold> <gray>({alive}/{cap})</gray>";
    }
    Boolean enabled = node.contains("enabled") ? node.getBoolean("enabled") : null;
    return new MobSpawnerTemplate(
        count,
        maxAlive,
        groupId,
        groupMaxAlive,
        groups,
        respawnTicks,
        respawnJitterTicks,
        lifespanTicks,
        spawnerDecayTicks,
        radius,
        allowBlockDamage,
        activationRadius,
        beamEnabled,
        beamParticle,
        beamStep,
        respectDifficulty,
        respectGameRules,
        attackRadius,
        attackIgnoreOutsideRadius,
        attackIgnorePlayers,
        tetherRadius,
        tetherAction,
        tetherPullSpeed,
        tetherDespawnTicks,
        maxAlivePerChunk,
        maxAlivePerPlayer,
        rules,
        hologramEnabled,
        hologramOffsetY,
        hologramFormat,
        enabled);
  }

  private MobSpec parseMobSpec(String id, ConfigurationSection node, List<String> errors, String base) {
    String typeRaw = YamlValues.requireString(node, "type", base + ".type");
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
    String tier = YamlValues.string(node, "tier", null);
    if (tier != null) {
      builder.tier(tier);
    }
    String name = YamlValues.string(node, "name", null);
    if (name != null) {
      builder.displayName(name);
      builder.showName(node.getBoolean("showName", true));
    } else if (node.contains("showName")) {
      builder.showName(node.getBoolean("showName", false));
    }
    MobStyleSpec style = resolveStyleSpec(node, base, tier);
    if (style != null) {
      builder.style(style);
    }

    ConfigurationSection bossbar = node.getConfigurationSection("bossbar");
    if (bossbar != null && bossbar.getBoolean("enabled", true)) {
      String title = YamlValues.requireString(bossbar, "title", base + ".bossbar.title");
      BossBar.Color color = parseBossBarColor(YamlValues.string(bossbar, "color", "RED"), base + ".bossbar.color");
      BossBar.Overlay overlay = parseBossBarOverlay(YamlValues.string(bossbar, "overlay", "PROGRESS"), base + ".bossbar.overlay");
      MobBossBarAudience audience = parseBossBarAudience(YamlValues.string(bossbar, "audience", "ALL_PLAYERS"), base + ".bossbar.audience");
      builder.bossBar(new MobBossBarSpec(MobText.parse(title), color, overlay, audience));
    }

    MobModelSpec modelSpec = parseModelSpec(node.getConfigurationSection("model"), base + ".model");
    if (modelSpec != null) {
      builder.modelSpec(modelSpec);
    }
    if (node.contains("collidable")) {
      builder.collidable(node.getBoolean("collidable"));
    }

    ConfigurationSection bossBroadcast = node.getConfigurationSection("bossBroadcast");
    if (bossBroadcast != null) {
      boolean enabled = bossBroadcast.getBoolean("enabled", true);
      String message = YamlValues.string(bossBroadcast, "message", DEFAULT_BOSS_BROADCAST);
      builder.bossBroadcast(new MobBroadcastSpec(enabled, message));
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
      ItemStack mainHand = itemStack(equipment, "mainHand");
      if (mainHand == null) {
        mainHand = itemStack(equipment, "mainhand");
      }
      builder.mainHand(mainHand);
      builder.offHand(itemStack(equipment, "offHand"));
      builder.head(itemStack(equipment, "head"));
      builder.chest(itemStack(equipment, "chest"));
      builder.legs(itemStack(equipment, "legs"));
      builder.feet(itemStack(equipment, "feet"));
    }

    ConfigurationSection stats = node.getConfigurationSection("stats");
    if (stats != null) {
      for (String key : stats.getKeys(false)) {
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (normalized.equals("scalevariance") || normalized.equals("scalevar")) {
          double variance = stats.getDouble(key);
          builder.scaleVariance(variance);
          continue;
        }
        Attribute attr = parseAttribute(key, base + ".stats." + key);
        double value = stats.getDouble(key);
        builder.attribute(attr, value);
      }
    }

    List<Map<?, ?>> variants = node.getMapList("variants");
    for (int i = 0; i < variants.size(); i++) {
      Map<?, ?> raw = variants.get(i);
      String variantId = YamlValues.string(raw, "id", "variant_" + i);
      double weight = YamlValues.doubleValueStrict(raw, "weight", 1.0);
      String variantName = YamlValues.string(raw, "name", null);
      String prefix = YamlValues.string(raw, "namePrefix", null);
      String suffix = YamlValues.string(raw, "nameSuffix", null);
      double health = YamlValues.doubleValueStrict(raw, "healthMultiplier", 1.0);
      double damage = YamlValues.doubleValueStrict(raw, "damageMultiplier", 1.0);
      double speed = YamlValues.doubleValueStrict(raw, "speedMultiplier", 1.0);
      double follow = YamlValues.doubleValueStrict(raw, "followRangeMultiplier", 1.0);
      double scale = YamlValues.doubleValueStrict(raw, "scaleMultiplier",
          YamlValues.doubleValueStrict(raw, "hitboxScale", 1.0));
      Boolean collidable = raw.containsKey("collidable") ? YamlValues.bool(raw.get("collidable"), true) : null;
      builder.addVariant(new MobVariantSpec(Ids.normalize(variantId), weight, variantName, prefix, suffix, health, damage,
          speed, follow, scale, collidable));
    }

    List<Map<?, ?>> traits = node.getMapList("traits");
    for (int i = 0; i < traits.size(); i++) {
      Map<?, ?> raw = traits.get(i);
      String traitId = YamlValues.string(raw, "id", "trait_" + i);
      double weight = YamlValues.doubleValueStrict(raw, "weight", 1.0);
      String traitName = YamlValues.string(raw, "name", null);
      String prefix = YamlValues.string(raw, "namePrefix", null);
      String suffix = YamlValues.string(raw, "nameSuffix", null);
      double health = YamlValues.doubleValueStrict(raw, "healthMultiplier", 1.0);
      double damage = YamlValues.doubleValueStrict(raw, "damageMultiplier", 1.0);
      double speed = YamlValues.doubleValueStrict(raw, "speedMultiplier", 1.0);
      double follow = YamlValues.doubleValueStrict(raw, "followRangeMultiplier", 1.0);
      double scale = YamlValues.doubleValueStrict(raw, "scaleMultiplier", 1.0);
      Map<DamageType, Double> traitResistances = new java.util.EnumMap<>(DamageType.class);
      Object resistRaw = raw.get("resistances");
      if (resistRaw instanceof Map<?, ?> resistMap) {
        for (Map.Entry<?, ?> entry : resistMap.entrySet()) {
          String key = entry.getKey() == null ? null : entry.getKey().toString();
          if (key == null || key.isBlank()) {
            continue;
          }
          DamageType resistType = parseDamageType(key, base + ".traits[" + i + "].resistances." + key);
          double value = YamlValues.doubleValueStrict(resistMap, key, 1.0);
          if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(base + ".traits[" + i + "].resistances." + key + ": multiplier must be >= 0");
          }
          traitResistances.put(resistType, value);
        }
      }
      builder.addTrait(new MobTraitSpec(Ids.normalize(traitId), weight, traitName, prefix, suffix, health, damage, speed, follow,
          scale, java.util.Map.copyOf(traitResistances)));
    }

    ConfigurationSection composite = node.getConfigurationSection("composite");
    if (composite != null) {
      String mountId = YamlValues.string(composite, "mount", null);
      String riderId = YamlValues.string(composite, "rider", null);
      if (mountId == null || mountId.isBlank()) {
        throw new IllegalArgumentException(base + ".composite.mount: missing mob id");
      }
      if (riderId == null || riderId.isBlank()) {
        throw new IllegalArgumentException(base + ".composite.rider: missing mob id");
      }
      MobCompositeRole role = parseCompositeRole(YamlValues.string(composite, "role", "PRIMARY_MOUNT"), base + ".composite.role");
      boolean keepAlive = composite.getBoolean("keepAliveTogether", true);
      builder.composite(new MobCompositeSpec(Ids.normalize(mountId), Ids.normalize(riderId), role, keepAlive));
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
    String poolId = null;
    boolean mergePool = true;
    if (loot != null) {
      poolId = loot.getString("pool", loot.getString("lootPool", null));
      mergePool = loot.getBoolean("merge", true);
    }
    if (poolId == null && node.contains("lootPool")) {
      poolId = node.getString("lootPool");
    }
    MobLootSpec poolSpec = null;
    if (poolId != null && !poolId.isBlank()) {
      poolSpec = lootPools.get(Ids.normalize(poolId));
      if (poolSpec == null) {
        errors.add(base + ".lootPool: unknown loot pool id: " + poolId);
      }
    }
    MobLootSpec localSpec = null;
    if (loot != null && hasLootData(loot)) {
      localSpec = parseLootSpec(loot, base + ".loot");
    }
    if (poolSpec != null) {
      if (localSpec != null && mergePool) {
        builder.loot(mergeLootSpec(poolSpec, localSpec, loot));
      } else if (localSpec != null && !mergePool) {
        builder.loot(localSpec);
      } else {
        builder.loot(poolSpec);
      }
    } else if (localSpec != null) {
      builder.loot(localSpec);
    }

    ConfigurationSection progression = node.getConfigurationSection("progression");
    if (progression != null) {
      int fixedXp = progression.getInt("xp", -1);
      int minXp = progression.getInt("minXp", fixedXp >= 0 ? fixedXp : 0);
      int maxXp = progression.getInt("maxXp", fixedXp >= 0 ? fixedXp : minXp);
      int maxPlayerXp = progression.getInt("maxPlayerXp", 0);
      builder.progressionSpec(new MobProgressionSpec(minXp, maxXp, maxPlayerXp));
    }

    ConfigurationSection advancementRewards = node.getConfigurationSection("advancementRewards");
    if (advancementRewards != null) {
      int xp = Math.max(0, advancementRewards.getInt("xp", 0));
      int skillPoints = Math.max(0, advancementRewards.getInt("skillPoints",
          advancementRewards.getInt("skillpoints", 0)));
      List<ItemStack> rewardItems = new ArrayList<>();
      List<Map<?, ?>> rawItems = advancementRewards.getMapList("items");
      for (int i = 0; i < rawItems.size(); i++) {
        Map<?, ?> raw = rawItems.get(i);
        String path = base + ".advancementRewards.items[" + i + "]";
        ItemStack item = itemFromMap(raw, path);
        if (item == null) {
          throw new IllegalArgumentException(path + ".item: missing item");
        }
        int amount = YamlValues.intValueStrict(raw, "amount", item.getAmount());
        item.setAmount(Math.max(1, amount));
        rewardItems.add(item);
      }
      builder.advancementRewards(new MobAdvancementRewardSpec(xp, skillPoints, rewardItems));
    }

    ConfigurationSection xpGating = node.getConfigurationSection("xpGating");
    if (xpGating != null) {
      int minLevel = xpGating.contains("minLevel") ? xpGating.getInt("minLevel") : 0;
      if (!xpGating.contains("minLevel")) {
        minLevel = xpGating.contains("minXpLevel") ? xpGating.getInt("minXpLevel")
            : xpGating.getInt("minXp", 0);
      }
      if (minLevel < 0) {
        throw new IllegalArgumentException(base + ".xpGating.minLevel: must be >= 0");
      }
      builder.minXpLevel(minLevel);
    }

    ConfigurationSection summon = node.getConfigurationSection("summon");
    if (summon != null) {
      boolean enabled = summon.getBoolean("enabled", true);
      boolean despawnOffline = summon.getBoolean("despawnWhenOwnerOffline", true);
      double despawnDistance = summon.getDouble("despawnDistance", 0.0);
      double teleportDistance = summon.getDouble("teleportDistance", 0.0);
      builder.summonSpec(new MobSummonSpec(enabled, despawnOffline, despawnDistance, teleportDistance));
    }

    boolean allowBlockDamage = true;
    if (node.contains("allowBlockDamage")) {
      allowBlockDamage = node.getBoolean("allowBlockDamage", true);
    } else if (node.contains("blockDamage")) {
      allowBlockDamage = node.getBoolean("blockDamage", true);
    } else if (node.contains("noBlockDamage")) {
      allowBlockDamage = !node.getBoolean("noBlockDamage", false);
    }
    builder.allowBlockDamage(allowBlockDamage);

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
          .roamRadius(ai.getDouble("roamRadius", 0.0))
          .kiteMinRange(ai.getDouble("kiteMinRange", 0.0))
          .kiteSpeed(ai.getDouble("kiteSpeed", 0.0))
          .chaseSpeed(ai.getDouble("chaseSpeed", 0.25))
          .rageHealthRatio(ai.getDouble("rageHealthRatio", 0.0))
          .rageSpeed(ai.getDouble("rageSpeed", 0.35))
          .locomotionMode(parseLocomotionMode(YamlValues.string(ai, "locomotion", "GROUND"),
              base + ".ai.locomotion"))
          .avoidWater(ai.getBoolean("avoidWater", false))
          .avoidLava(ai.getBoolean("avoidLava", false))
          .preferGround(ai.getBoolean("preferGround", true));
      String mode = YamlValues.string(ai, "aggroTargetMode", "NEAREST_PLAYER");
      aiBuilder.aggroTargetMode(parseTargetMode(mode, base + ".ai.aggroTargetMode"));
      String partyRule = YamlValues.string(ai, "partyRule", "NONE");
      aiBuilder.partyRule(parsePartyRule(partyRule, base + ".ai.partyRule"));
      List<Map<?, ?>> goals = ai.getMapList("goals");
      for (int i = 0; i < goals.size(); i++) {
        Map<?, ?> raw = goals.get(i);
        String goalPath = base + ".ai.goals[" + i + "]";
        aiBuilder.addGoal(parseAiGoal(raw, goalPath));
      }
      List<Map<?, ?>> guardPoints = ai.getMapList("guardPoints");
      for (int i = 0; i < guardPoints.size(); i++) {
        Map<?, ?> pointMap = guardPoints.get(i);
        @SuppressWarnings("unused")
        String pointPath = base + ".ai.guardPoints[" + i + "]";
        double x = YamlValues.doubleValueStrict(pointMap, "x", 0.0);
        double y = YamlValues.doubleValueStrict(pointMap, "y", 0.0);
        double z = YamlValues.doubleValueStrict(pointMap, "z", 0.0);
        aiBuilder.addGuardPoint(new Vector(x, y, z));
      }
      builder.aiSpec(aiBuilder.build());
    }

    ConfigurationSection events = node.getConfigurationSection("events");
    if (events != null) {
      MobEventSpec.Builder eventBuilder = MobEventSpec.builder();
      eventBuilder.onHit(parseEventAbility(events, "onHit", base + ".events.onHit", id));
      eventBuilder.onHurt(parseEventAbility(events, "onHurt", base + ".events.onHurt", id));
      eventBuilder.onTarget(parseEventAbility(events, "onTarget", base + ".events.onTarget", id));
      eventBuilder.onKill(parseEventAbility(events, "onKill", base + ".events.onKill", id));
      eventBuilder.onPhaseChange(parseEventAbility(events, "onPhaseChange", base + ".events.onPhaseChange", id));
      eventBuilder.onDespawn(parseEventAbility(events, "onDespawn", base + ".events.onDespawn", id));
      ConfigurationSection onIdle = events.getConfigurationSection("onIdle");
      if (onIdle != null) {
        String ability = parseEventAbility(onIdle, "ability", base + ".events.onIdle", id);
        eventBuilder.onIdle(ability, onIdle.getLong("intervalTicks", 80L));
      } else {
        String ability = parseEventAbility(events, "onIdle", base + ".events.onIdle", id);
        if (ability != null) {
          eventBuilder.onIdle(ability, events.getLong("onIdleIntervalTicks", 80L));
        }
      }

      ConfigurationSection onStuck = events.getConfigurationSection("onStuck");
      if (onStuck != null) {
        String ability = parseEventAbility(onStuck, "ability", base + ".events.onStuck", id);
        eventBuilder.onStuck(ability, onStuck.getLong("intervalTicks", 60L),
            onStuck.getDouble("distance", 0.35));
      } else {
        String ability = parseEventAbility(events, "onStuck", base + ".events.onStuck", id);
        if (ability != null) {
          eventBuilder.onStuck(ability, events.getLong("onStuckIntervalTicks", 60L),
              events.getDouble("onStuckDistance", 0.35));
        }
      }

      ConfigurationSection onNear = events.getConfigurationSection("onNear");
      if (onNear != null) {
        String ability = parseEventAbility(onNear, "ability", base + ".events.onNear", id);
        eventBuilder.onNear(ability, onNear.getDouble("radius", 6.0), onNear.getLong("cooldownTicks", 40L));
      } else {
        String ability = parseEventAbility(events, "onNear", base + ".events.onNear", id);
        if (ability != null) {
          eventBuilder.onNear(ability, events.getDouble("onNearRadius", 6.0),
              events.getLong("onNearCooldownTicks", 40L));
        }
      }

      ConfigurationSection onSpawnTick = events.getConfigurationSection("onSpawnTick");
      if (onSpawnTick != null) {
        String ability = parseEventAbility(onSpawnTick, "ability", base + ".events.onSpawnTick", id);
        eventBuilder.onSpawnTick(ability, onSpawnTick.getLong("intervalTicks", 20L));
      } else {
        String ability = parseEventAbility(events, "onSpawnTick", base + ".events.onSpawnTick", id);
        if (ability != null) {
          eventBuilder.onSpawnTick(ability, events.getLong("onSpawnTickIntervalTicks", 20L));
        }
      }

      MobEventSpec built = eventBuilder.build();
      if (!built.isEmpty()) {
        builder.events(built);
      }
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
      String ability = YamlValues.string(raw, "ability", null);
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
      long period = YamlValues.longValueStrict(raw, "periodTicks", 20L);
      builder.addPassive(new MobPassiveSpec(ability, period));
    }

    List<Map<?, ?>> phases = node.getMapList("phases");
    for (int i = 0; i < phases.size(); i++) {
      Map<?, ?> raw = phases.get(i);
      String path = base + ".phases[" + i + "]";
      builder.addPhase(parsePhase(raw, path, id, i, tier));
    }

    MobManaDropSpec manaDrop = parseManaDrops(node, base + ".manaDrops");
    if (manaDrop != null && !manaDrop.isEmpty()) {
      builder.manaDrop(manaDrop);
    }
    MobManaDrainSpec manaDrain = parseManaDrain(node, base + ".manaDrain");
    if (manaDrain != null && !manaDrain.isEmpty()) {
      builder.manaDrain(manaDrain);
    }

    MobCombatSpec combat = parseCombat(node.getConfigurationSection("combat"), base + ".combat");
    if (combat != null && !combat.isEmpty()) {
      builder.combatSpec(combat);
    }
    List<Map<?, ?>> bonuses = node.getMapList("damageBonuses");
    for (int i = 0; i < bonuses.size(); i++) {
      builder.addDamageBonus(parseDamageBonus(bonuses.get(i), base + ".damageBonuses[" + i + "]"));
    }

    return builder.build();
  }

  private MobAttackSpec parseAttack(ConfigurationSection node, String path, String mobId, String attackKey) {
    String ability = YamlValues.string(node, "ability", null);
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
        .internalCooldownTicks(node.getLong("internalCooldownTicks", 0L))
        .trigger(parseTrigger(YamlValues.string(node, "trigger", "MELEE"), path + ".trigger"))
        .targetMode(parseTargetMode(YamlValues.string(node, "targetMode", "NEAREST_PLAYER"), path + ".targetMode"))
        .range(node.getDouble("range", 10.0))
        .chance(node.getDouble("chance", 1.0))
        .requireLineOfSight(node.getBoolean("requireLineOfSight", true))
        .requireTarget(node.getBoolean("requireTarget", true))
        .priorityWeight(node.getDouble("priorityWeight", 1.0));
    ConfigurationSection aoe = node.getConfigurationSection("aoe");
    if (aoe != null) {
      builder.aoe(parseAttackAoE(aoe, path + ".aoe"));
    }
    if (!engine.hasAbility(ability)) {
      throw new IllegalArgumentException(path + ".ability: ability not registered: " + ability);
    }
    return builder.build();
  }

  private MobPhaseSpec parsePhase(Map<?, ?> raw, String path, String mobId, int index, String tier) {
    String id = YamlValues.string(raw, "id", "phase_" + (index + 1));
    double healthBelow = YamlValues.doubleValueStrict(raw, "healthBelow", YamlValues.doubleValueStrict(raw, "healthRatio", -1.0));
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
          String ability = YamlValues.string(passiveMap, "ability", null);
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
          long period = YamlValues.longValueStrict(passiveMap, "periodTicks", 20L);
          passives.add(new MobPassiveSpec(ability, period));
        }
      } else {
        throw new IllegalArgumentException(path + ".passives: must be a list");
      }
    }
    ItemStack mainHand = null;
    ItemStack offHand = null;
    ItemStack head = null;
    ItemStack chest = null;
    ItemStack legs = null;
    ItemStack feet = null;
    Object equipmentRaw = raw.get("equipment");
    if (equipmentRaw instanceof Map<?, ?> equipment) {
      mainHand = itemFromEquipment(equipment.get("mainHand"), path + ".equipment.mainHand");
      if (mainHand == null) {
        mainHand = itemFromEquipment(equipment.get("mainhand"), path + ".equipment.mainhand");
      }
      offHand = itemFromEquipment(equipment.get("offHand"), path + ".equipment.offHand");
      head = itemFromEquipment(equipment.get("head"), path + ".equipment.head");
      chest = itemFromEquipment(equipment.get("chest"), path + ".equipment.chest");
      legs = itemFromEquipment(equipment.get("legs"), path + ".equipment.legs");
      feet = itemFromEquipment(equipment.get("feet"), path + ".equipment.feet");
    }
    Double scaleMultiplier = null;
    if (raw.containsKey("scaleMultiplier") || raw.containsKey("scale") || raw.containsKey("hitboxScale")) {
      double scale = YamlValues.doubleValueStrict(raw, "scaleMultiplier",
          YamlValues.doubleValueStrict(raw, "hitboxScale", YamlValues.doubleValueStrict(raw, "scale", 1.0)));
      if (!Double.isFinite(scale) || scale <= 0.0) {
        throw new IllegalArgumentException(path + ".scaleMultiplier: must be > 0");
      }
      scaleMultiplier = scale;
    }
    Boolean collidable = raw.containsKey("collidable") ? YamlValues.bool(raw.get("collidable"), true) : null;
    MobModelSpec modelSpec = null;
    Object modelRaw = raw.get("model");
    if (modelRaw instanceof ConfigurationSection section) {
      modelSpec = parseModelSpec(section, path + ".model");
    } else if (modelRaw instanceof Map<?, ?> modelMap) {
      modelSpec = parseModelSpec(modelMap, path + ".model");
    }
    MobStyleSpec style = resolveStyleSpec(raw, path, tier);
    return new MobPhaseSpec(phaseId, healthBelow, main, secondary, passives, mainHand, offHand, head, chest, legs, feet,
        scaleMultiplier, collidable, modelSpec, style);
  }

  private static MobCompositeRole parseCompositeRole(String raw, String path) {
    String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    return switch (key) {
      case "PRIMARY_MOUNT", "MOUNT", "PRIMARYMOUNT" -> MobCompositeRole.PRIMARY_MOUNT;
      case "PRIMARY_RIDER", "RIDER", "PRIMARYRIDER" -> MobCompositeRole.PRIMARY_RIDER;
      default -> throw new IllegalArgumentException(path + ": invalid composite role " + raw);
    };
  }

  private MobAttackSpec parseAttackMap(Map<?, ?> node, String path, String mobId, String attackKey) {
    String ability = YamlValues.string(node, "ability", null);
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
        .cooldownTicks(YamlValues.longValueStrict(node, "cooldownTicks", 40L))
        .trigger(parseTrigger(YamlValues.string(node, "trigger", "MELEE"), path + ".trigger"))
        .targetMode(parseTargetMode(YamlValues.string(node, "targetMode", "NEAREST_PLAYER"), path + ".targetMode"))
        .range(YamlValues.doubleValueStrict(node, "range", 10.0))
        .chance(YamlValues.doubleValueStrict(node, "chance", 1.0))
        .requireLineOfSight(YamlValues.bool(node, "requireLineOfSight", true))
        .requireTarget(YamlValues.bool(node, "requireTarget", true));
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
    String resourceId = YamlValues.string(mana, "resource", "mana");
    double cap = mana.getDouble("cap", 0.0);
    Object killerRaw = mana.get("killer");
    Object nearbyRaw = mana.get("nearby");
    MobManaDropSpec.MobManaRange killer = parseManaRange(killerRaw, path + ".killer");
    MobManaDropSpec.MobManaRange nearby = parseManaRange(nearbyRaw, path + ".nearby");
    double radius = mana.getDouble("radius", 0.0);
    if (nearbyRaw instanceof ConfigurationSection nearbySec) {
      radius = nearbySec.getDouble("radius", radius);
    } else if (nearbyRaw instanceof Map<?, ?> nearbyMap) {
      radius = YamlValues.doubleValueStrict(nearbyMap, "radius", radius);
    }
    if (radius < 0.0) {
      throw new IllegalArgumentException(path + ".nearby.radius: must be >= 0");
    }
    MobManaDropSpec.MobManaStreak streak = parseManaStreak(mana.get("streak"), path + ".streak");
    List<MobManaDropSpec.MobManaTier> tiers = parseManaTiers(mana.get("tiers"), path + ".tiers");
    return new MobManaDropSpec(resourceId, killer, nearby, radius, cap, streak, tiers);
  }

  private MobManaDrainSpec parseManaDrain(ConfigurationSection node, String path) {
    ConfigurationSection drain = node.getConfigurationSection("manaDrain");
    if (drain == null) {
      drain = node.getConfigurationSection("resourceDrain");
    }
    if (drain == null) {
      return null;
    }
    String resourceId = YamlValues.string(drain, "resource", "mana");
    double amount = drain.getDouble("amount", 0.0);
    double chance = drain.getDouble("chance", 1.0);
    long cooldownTicks = 0L;
    if (drain.contains("cooldownTicks")) {
      cooldownTicks = Math.max(0L, drain.getLong("cooldownTicks", 0L));
    } else if (drain.contains("cooldownSeconds") || drain.contains("cooldown")) {
      double seconds = drain.getDouble("cooldownSeconds", drain.getDouble("cooldown", 0.0));
      cooldownTicks = Math.max(0L, Math.round(seconds * 20.0));
    }
    return new MobManaDrainSpec(resourceId, amount, chance, cooldownTicks);
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
      double min = map.containsKey("min") ? YamlValues.doubleValueStrict(map, "min", 0.0) : YamlValues.doubleValueStrict(map, "amount", 0.0);
      double max = map.containsKey("max") ? YamlValues.doubleValueStrict(map, "max", min) : min;
      return new MobManaDropSpec.MobManaRange(min, max);
    }
    throw new IllegalArgumentException(path + ": must be a number or object");
  }

  private static MobManaDropSpec.MobManaStreak parseManaStreak(Object raw, String path) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ConfigurationSection sec) {
      raw = sec.getValues(false);
    }
    if (!(raw instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(path + ": must be an object");
    }
    int maxStacks = Math.max(0, YamlValues.intValue(map.get("maxStacks"), YamlValues.intValue(map.get("max"), 0)));
    double multiplier = YamlValues.doubleValue(map.get("multiplier"), 0.0);
    long windowTicks = Math.max(0L, YamlValues.longValue(map.get("windowTicks"), 0L));
    if (windowTicks <= 0L && map.containsKey("windowSeconds")) {
      windowTicks = Math.max(0L, Math.round(YamlValues.doubleValue(map.get("windowSeconds"), 0.0) * 20.0));
    }
    if (maxStacks <= 0 || multiplier <= 0.0) {
      return null;
    }
    return new MobManaDropSpec.MobManaStreak(maxStacks, multiplier, windowTicks);
  }

  private static List<MobManaDropSpec.MobManaTier> parseManaTiers(Object raw, String path) {
    if (raw == null) {
      return List.of();
    }
    List<?> list = raw instanceof List<?> items ? items : List.of(raw);
    if (list.isEmpty()) {
      return List.of();
    }
    List<MobManaDropSpec.MobManaTier> tiers = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      String entryPath = path + "[" + i + "]";
      if (entry instanceof ConfigurationSection sec) {
        entry = sec.getValues(false);
      }
      if (!(entry instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException(entryPath + ": must be an object");
      }
      double weight = YamlValues.doubleValue(map.get("weight"), 1.0);
      double min = YamlValues.doubleValue(map.get("minMultiplier"), YamlValues.doubleValue(map.get("min"), 1.0));
      double max = YamlValues.doubleValue(map.get("maxMultiplier"), YamlValues.doubleValue(map.get("max"), min));
      if (map.containsKey("multiplier")) {
        min = YamlValues.doubleValue(map.get("multiplier"), min);
        max = min;
      }
      tiers.add(new MobManaDropSpec.MobManaTier(weight, min, max));
    }
    return List.copyOf(tiers);
  }

  private List<MobSpawnSpec> parseSpawns(YamlConfiguration cfg, Map<String, MobSpec> specs, List<String> errors, String source) {
    List<MobSpawnSpec> out = new ArrayList<>();
    ConfigurationSection spawnsSec = cfg.getConfigurationSection("spawns");
    if (spawnsSec == null) {
      return out;
    }
    for (String key : spawnsSec.getKeys(false)) {
      ConfigurationSection node = spawnsSec.getConfigurationSection(key);
      String base = prefix(source) + "spawns." + key;
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(key);
        String mobId = YamlValues.string(node, "mob", null);
        if (mobId != null && !mobId.isBlank()) {
          mobId = Ids.normalize(mobId);
          if (!specs.containsKey(mobId) && !registry.has(mobId)) {
            throw new IllegalArgumentException(base + ".mob: unknown mob id: " + mobId);
          }
        }
        String world = YamlValues.requireString(node, "world", base + ".world");
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
        String groupId = YamlValues.string(node, "group", null);
        if (groupId == null) {
          groupId = YamlValues.string(node, "groupId", null);
        }
        if (groupId != null && !groupId.isBlank()) {
          groupId = Ids.normalize(groupId);
        } else {
          groupId = null;
        }
        int groupMaxAlive = (int) node.getLong("groupMaxAlive", 0L);
        List<MobSpawnGroupSpec> groups = parseSpawnerGroups(node.getMapList("groups"), base + ".groups");
        long respawnTicks = node.getLong("respawnTicks", 200L);
        long respawnJitterTicks = node.getLong("respawnJitterTicks", 0L);
        if (node.contains("respawnJitterSeconds")) {
          respawnJitterTicks = Math.round(node.getDouble("respawnJitterSeconds", 0.0) * 20.0);
        } else if (node.contains("respawnJitter")) {
          respawnJitterTicks = Math.round(node.getDouble("respawnJitter", 0.0) * 20.0);
        }
        long lifespanTicks = node.getLong("lifespanTicks", node.getLong("despawnTicks", 0L));
        long spawnerDecayTicks = node.getLong("spawnerDecayTicks", node.getLong("decayTicks", 0L));
        double radius = node.getDouble("radius", 0.0);
        double attackRadius = node.contains("attackRadius") ? node.getDouble("attackRadius") : 0.0;
        ConfigurationSection attack = node.getConfigurationSection("attack");
        if (attack != null && attack.contains("radius")) {
          attackRadius = attack.getDouble("radius", attackRadius);
        }
        boolean attackIgnoreOutsideRadius = false;
        boolean attackIgnorePlayers = false;
        if (node.contains("attackIgnoreOutsideRadius")) {
          attackIgnoreOutsideRadius = node.getBoolean("attackIgnoreOutsideRadius", attackIgnoreOutsideRadius);
        }
        if (node.contains("attackIgnorePlayers")) {
          attackIgnorePlayers = node.getBoolean("attackIgnorePlayers", false);
        }
        if (attack != null) {
          if (attack.contains("ignoreOutsideRadius")) {
            attackIgnoreOutsideRadius = attack.getBoolean("ignoreOutsideRadius", attackIgnoreOutsideRadius);
          }
          if (attack.contains("ignorePlayers")) {
            attackIgnorePlayers = attack.getBoolean("ignorePlayers", false);
          }
        }
        double tetherRadius = node.contains("tetherRadius") ? node.getDouble("tetherRadius") : 0.0;
        ConfigurationSection tether = node.getConfigurationSection("tether");
        if (tether != null && tether.contains("radius")) {
          tetherRadius = tether.getDouble("radius", tetherRadius);
        }
        String actionRaw = YamlValues.string(node, "tetherAction", null);
        if (tether != null && tether.contains("action")) {
          actionRaw = YamlValues.string(tether, "action", actionRaw);
        }
        MobSpawnTetherAction tetherAction = actionRaw == null ? null : MobSpawnTetherAction.parse(actionRaw, base + ".tetherAction");
        if (tetherAction == null && tetherRadius > 0.0) {
          tetherAction = MobSpawnTetherAction.PULL;
        }
        double tetherPullSpeed = node.getDouble("tetherPullSpeed", 0.35);
        if (tether != null && tether.contains("pullSpeed")) {
          tetherPullSpeed = tether.getDouble("pullSpeed", tetherPullSpeed);
        }
        long tetherDespawnTicks = node.getLong("tetherDespawnTicks", 0L);
        if (tether != null && tether.contains("despawnTicks")) {
          tetherDespawnTicks = tether.getLong("despawnTicks", tetherDespawnTicks);
        }
        boolean enabled = node.getBoolean("enabled", true);
        boolean hologramEnabled = false;
        double hologramOffsetY = 2.3;
        String hologramFormat = null;
        String hologramTitle = null;
        ConfigurationSection hologram = node.getConfigurationSection("hologram");
        if (node.contains("hologramEnabled")) {
          hologramEnabled = node.getBoolean("hologramEnabled", hologramEnabled);
        }
        if (node.contains("hologramOffsetY")) {
          hologramOffsetY = node.getDouble("hologramOffsetY", hologramOffsetY);
        }
        if (node.contains("hologramFormat")) {
          hologramFormat = YamlValues.string(node, "hologramFormat", hologramFormat);
        }
        if (hologram != null) {
          if (hologram.contains("enabled")) {
            hologramEnabled = hologram.getBoolean("enabled", hologramEnabled);
          }
          if (hologram.contains("offsetY")) {
            hologramOffsetY = hologram.getDouble("offsetY", hologramOffsetY);
          }
          if (hologram.contains("title")) {
            hologramTitle = YamlValues.string(hologram, "title", hologramTitle);
          }
          if (hologram.contains("format")) {
            hologramFormat = YamlValues.string(hologram, "format", hologramFormat);
          }
        }
        if ((hologramFormat == null || hologramFormat.isBlank()) && hologramTitle != null && !hologramTitle.isBlank()) {
          hologramFormat = "<gold>" + hologramTitle + "</gold> <gray>({alive}/{cap})</gray>";
        }
        if (groupMaxAlive < 0) {
          throw new IllegalArgumentException(base + ".groupMaxAlive: must be >= 0");
        }
        if (respawnTicks < 0L) {
          throw new IllegalArgumentException(base + ".respawnTicks: must be >= 0");
        }
        if (respawnJitterTicks < 0L) {
          throw new IllegalArgumentException(base + ".respawnJitterTicks: must be >= 0");
        }
        if (lifespanTicks < 0L) {
          throw new IllegalArgumentException(base + ".lifespanTicks: must be >= 0");
        }
        if (spawnerDecayTicks < 0L) {
          throw new IllegalArgumentException(base + ".spawnerDecayTicks: must be >= 0");
        }
        if (radius < 0.0) {
          throw new IllegalArgumentException(base + ".radius: must be >= 0");
        }
        boolean allowBlockDamage = true;
        if (node.contains("allowBlockDamage")) {
          allowBlockDamage = node.getBoolean("allowBlockDamage", allowBlockDamage);
        } else if (node.contains("blockDamage")) {
          allowBlockDamage = node.getBoolean("blockDamage", allowBlockDamage);
        } else if (node.contains("noBlockDamage")) {
          allowBlockDamage = !node.getBoolean("noBlockDamage", false);
        }
        double activationRadius = node.getDouble("activationRadius", 0.0);
        boolean beamEnabled = false;
        Particle beamParticle = null;
        double beamStep = 0.3;
        ConfigurationSection beam = node.getConfigurationSection("beam");
        if (beam != null) {
          if (beam.contains("enabled")) {
            beamEnabled = beam.getBoolean("enabled", beamEnabled);
          }
          if (beam.contains("particle")) {
            beamParticle = parseParticleType(YamlValues.requireString(beam, "particle", base + ".beam.particle"), base + ".beam.particle");
          }
          if (beam.contains("step")) {
            beamStep = beam.getDouble("step");
          }
        }
        ConfigurationSection activation = node.getConfigurationSection("activation");
        if (activation != null && activation.contains("radius")) {
          activationRadius = activation.getDouble("radius", activationRadius);
        }
        if (activationRadius < 0.0) {
          throw new IllegalArgumentException(base + ".activationRadius: must be >= 0");
        }
        if (beamEnabled && beamParticle == null) {
          throw new IllegalArgumentException(base + ".beam.particle: missing particle");
        }
        if (beamStep <= 0.0) {
          throw new IllegalArgumentException(base + ".beam.step: must be > 0");
        }
        boolean respectDifficulty = node.getBoolean("respectDifficulty", true);
        boolean respectGameRules = node.getBoolean("respectGameRules", true);
        MobSpawnRulesSpec rules = parseSpawnRules(node.get("rules"), base + ".rules");
        int maxAlivePerChunk = node.getInt("maxAlivePerChunk", 0);
        int maxAlivePerPlayer = node.getInt("maxAlivePerPlayer", 0);
        ConfigurationSection density = node.getConfigurationSection("density");
        if (density != null) {
          if (density.contains("maxAlivePerChunk")) {
            maxAlivePerChunk = density.getInt("maxAlivePerChunk", maxAlivePerChunk);
          }
          if (density.contains("maxAlivePerPlayer")) {
            maxAlivePerPlayer = density.getInt("maxAlivePerPlayer", maxAlivePerPlayer);
          }
        }
        if (activation != null) {
          if (activation.contains("respectDifficulty")) {
            respectDifficulty = activation.getBoolean("respectDifficulty", respectDifficulty);
          }
          if (activation.contains("respectGameRules")) {
            respectGameRules = activation.getBoolean("respectGameRules", respectGameRules);
          }
        }
        if (attackRadius < 0.0) {
          throw new IllegalArgumentException(base + ".attackRadius: must be >= 0");
        }
        if (tetherRadius < 0.0) {
          throw new IllegalArgumentException(base + ".tetherRadius: must be >= 0");
        }
        if (tetherPullSpeed < 0.0) {
          throw new IllegalArgumentException(base + ".tetherPullSpeed: must be >= 0");
        }
        if (tetherDespawnTicks < 0L) {
          throw new IllegalArgumentException(base + ".tetherDespawnTicks: must be >= 0");
        }
        if (maxAlivePerChunk < 0) {
          throw new IllegalArgumentException(base + ".maxAlivePerChunk: must be >= 0");
        }
        if (maxAlivePerPlayer < 0) {
          throw new IllegalArgumentException(base + ".maxAlivePerPlayer: must be >= 0");
        }
        validateSpawnerGroups(groups, specs, base + ".groups");
        if ((mobId == null || mobId.isBlank()) && (groups == null || groups.isEmpty())) {
          throw new IllegalArgumentException(base + ".mob: missing mob id (no groups configured)");
        }
        out.add(new MobSpawnSpec(id, mobId == null ? "" : mobId, world, location, count, maxAlive, groupId, groupMaxAlive,
            groups, respawnTicks,
            respawnJitterTicks, lifespanTicks, spawnerDecayTicks, radius, allowBlockDamage, activationRadius, beamEnabled, beamParticle, beamStep,
            respectDifficulty, respectGameRules, attackRadius, attackIgnoreOutsideRadius, attackIgnorePlayers, tetherRadius,
            tetherAction == null ? MobSpawnTetherAction.NONE : tetherAction, tetherPullSpeed, tetherDespawnTicks,
            maxAlivePerChunk, maxAlivePerPlayer, rules,
            hologramEnabled, hologramOffsetY, hologramFormat, enabled));
      } catch (Exception ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
    return out;
  }

  private void parseMobs(YamlConfiguration cfg, Map<String, MobSpec> specs, List<String> errors, String source) {
    ConfigurationSection mobsSec = cfg.getConfigurationSection("mobs");
    if (mobsSec == null) {
      return;
    }
    for (String rawId : mobsSec.getKeys(false)) {
      String base = prefix(source) + "mobs." + rawId;
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

  private void parseLootPools(YamlConfiguration cfg, Map<String, MobLootSpec> pools, List<String> errors,
      String source, String fallbackPoolId) {
    ConfigurationSection poolsSec = cfg.getConfigurationSection("lootPools");
    if (poolsSec != null) {
      for (String rawId : poolsSec.getKeys(false)) {
        String base = prefix(source) + "lootPools." + rawId;
        try {
          String id = Ids.normalize(rawId);
          if (pools.containsKey(id)) {
            errors.add(base + ": duplicate loot pool id");
            continue;
          }
          ConfigurationSection node = poolsSec.getConfigurationSection(rawId);
          if (node == null) {
            errors.add(base + ": must be an object");
            continue;
          }
          MobLootSpec spec = parseLootSpec(node, base);
          pools.put(id, spec);
        } catch (Exception ex) {
          errors.add(base + ": " + ex.getMessage());
        }
      }
    }
    if (fallbackPoolId != null && poolsSec == null) {
      ConfigurationSection node = cfg.getConfigurationSection("loot");
      if (node != null) {
        String base = prefix(source) + "loot";
        try {
          String id = Ids.normalize(fallbackPoolId);
          if (pools.containsKey(id)) {
            errors.add(base + ": duplicate loot pool id");
            return;
          }
          MobLootSpec spec = parseLootSpec(node, base);
          pools.put(id, spec);
        } catch (Exception ex) {
          errors.add(base + ": " + ex.getMessage());
        }
      }
    }
  }

  private static String poolIdFromFile(File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static String prefix(String source) {
    if (source == null || source.isBlank()) {
      return "";
    }
    return source + ": ";
  }

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

  private void saveDefaultMobFiles(File folder) {
    List<String> bundled = listBundledMobFiles();
    if (bundled.isEmpty()) {
      bundled = java.util.Arrays.asList(DEFAULT_MOB_FILES);
    }
    for (String file : bundled) {
      File target = new File(folder, file);
      if (target.exists()) {
        continue;
      }
      try {
        plugin.saveResource("mobs/" + file, false);
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  private void saveDefaultLootFiles(File folder) {
    List<String> bundled = listBundledLootFiles();
    for (String file : bundled) {
      File target = new File(folder, file);
      if (target.exists()) {
        continue;
      }
      try {
        plugin.saveResource("loot/" + file, false);
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  private List<String> listBundledMobFiles() {
    try {
      java.net.URL url = plugin.getClass().getClassLoader().getResource("mobs");
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
            if (!name.startsWith("mobs/") || entry.isDirectory()) {
              continue;
            }
            String base = name.substring("mobs/".length());
            String lower = base.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
              names.add(base);
            }
          }
          names.sort(String.CASE_INSENSITIVE_ORDER);
          return names;
        }
      }
    } catch (Exception ignored) {
    }
    return List.of();
  }

  private List<String> listBundledLootFiles() {
    try {
      java.net.URL url = plugin.getClass().getClassLoader().getResource("loot");
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
            if (!name.startsWith("loot/") || entry.isDirectory()) {
              continue;
            }
            String base = name.substring("loot/".length());
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

  private int parseEggs(YamlConfiguration cfg, Map<String, MobSpec> specs, Map<String, MobEggSpec> out,
                        List<String> errors, String source) {
    ConfigurationSection eggsSec = cfg.getConfigurationSection("eggs");
    if (eggsSec == null) {
      return 0;
    }
    int loaded = 0;
    for (String rawId : eggsSec.getKeys(false)) {
      String base = prefix(source) + "eggs." + rawId;
      ConfigurationSection node = eggsSec.getConfigurationSection(rawId);
      if (node == null) {
        errors.add(base + ": must be an object");
        continue;
      }
      try {
        String id = Ids.normalize(rawId);
        if (out.containsKey(id)) {
          errors.add(base + ": duplicate egg id");
          continue;
        }
        String mobId = YamlValues.requireString(node, "mob", base + ".mob");
        if (!specs.containsKey(mobId) && !registry.has(mobId)) {
          throw new IllegalArgumentException(base + ".mob: unknown mob id: " + mobId);
        }
        ItemStack item = itemStack(node, "item");
        if (item == null) {
          String material = YamlValues.string(node, "material", null);
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
        String permission = YamlValues.string(node, "permission", null);
        long cooldownTicks = node.getLong("cooldownTicks", 0L);
        if (cooldownTicks < 0L) {
          throw new IllegalArgumentException(base + ".cooldownTicks: must be >= 0");
        }
        out.put(id, new MobEggSpec(id, mobId, item, permission, cooldownTicks));
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

  private ItemStack itemStack(ConfigurationSection sec, String key) {
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
      int amount = YamlValues.intValueStrict(map, "amount", item.getAmount());
      item.setAmount(Math.max(1, amount));
      return item;
    }
    return null;
  }

  private ItemStack itemFromEquipment(Object raw, String path) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ItemStack stack) {
      return stack.getType() == Material.AIR ? null : stack.clone();
    }
    if (raw instanceof String str) {
      Material material = parseMaterial(str, path);
      return material == Material.AIR ? null : new ItemStack(material);
    }
    if (raw instanceof Map<?, ?> map) {
      ItemStack item = itemFromMap(map, path);
      return item == null || item.getType() == Material.AIR ? null : item;
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
      case "scale", "size" -> Attribute.SCALE;
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

  private ItemStack itemFromMap(Map<?, ?> map, String path) {
    Object raw = map.get("item");
    if (raw instanceof ItemStack stack) {
      return stack.clone();
    }
    if (raw instanceof Map<?, ?> mapItem) {
      return ItemStack.deserialize(castMap(mapItem));
    }
    if (raw instanceof String rawItem) {
      ItemStack template = yamlAbilities.itemTemplate(rawItem);
      if (template != null) {
        return template;
      }
      Material material = parseMaterial(rawItem, path + ".item");
      return new ItemStack(material);
    }
    String itemId = YamlValues.string(map, "itemId", YamlValues.string(map, "id", null));
    if (itemId != null && !itemId.isBlank()) {
      ItemStack template = yamlAbilities.itemTemplate(itemId);
      if (template == null || template.getType() == Material.AIR) {
        throw new IllegalArgumentException(path + ".itemId: unknown item id " + itemId);
      }
      return template;
    }
    String upgradeId = YamlValues.string(map, "upgradeId", YamlValues.string(map, "upgrade", null));
    if (upgradeId != null && !upgradeId.isBlank()) {
      if (upgradeRegistry == null) {
        throw new IllegalArgumentException(path + ".upgradeId: upgrades not available");
      }
      ItemStack upgradeItem = upgradeRegistry.upgradeItem(upgradeId);
      if (upgradeItem == null || upgradeItem.getType() == Material.AIR) {
        throw new IllegalArgumentException(path + ".upgradeId: unknown upgrade id " + upgradeId);
      }
      return upgradeItem;
    }
    String tokenId = YamlValues.string(map, "token", YamlValues.string(map, "tokenTier", null));
    if (tokenId != null && !tokenId.isBlank()) {
      if (shopRegistry == null) {
        throw new IllegalArgumentException(path + ".token: token tiers not configured");
      }
      ItemStack token = shopRegistry.resolveTokenItem(tokenId);
      if (token == null || token.getType() == Material.AIR) {
        throw new IllegalArgumentException(path + ".token: unknown token tier " + tokenId);
      }
      return token;
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

  private MobLootSpec parseLootSpec(ConfigurationSection loot, String base) {
    boolean clearVanilla = loot.getBoolean("clearVanilla", false);
    int rolls = loot.getInt("rolls", 1);
    int bonusRolls = loot.getInt("bonusRolls", 0);
    double luckMultiplier = loot.getDouble("luckMultiplier", 0.0);
    boolean deterministic = loot.getBoolean("deterministic", false);
    long seedSalt = parseSeedSalt(loot.get("seed"), base + ".seed");
    List<String> announceTiers = loot.getStringList("announceTiers");
    String announceTemplate = loot.getString("announceTemplate", null);
    List<Map<?, ?>> rawGuaranteed = loot.getMapList("guaranteed");
    if (rawGuaranteed.isEmpty()) {
      rawGuaranteed = loot.getMapList("guaranteedDrops");
    }
    List<MobDropSpec> guaranteed = parseDrops(rawGuaranteed, base + ".guaranteed", true);
    List<MobDropSpec> drops = parseDrops(loot.getMapList("drops"), base + ".drops", false);
    List<MobLootPoolRef> pools = parseLootPoolRefs(loot.getMapList("pools"), base + ".pools");
    List<MobLootBundleSpec> bundles = parseLootBundles(loot.getMapList("bundles"), base + ".bundles");
    MobLootRewardSpec rewards = parseLootRewards(loot.getConfigurationSection("rewards"), base + ".rewards");
    return new MobLootSpec(clearVanilla, guaranteed, drops, pools, bundles, rewards, rolls, bonusRolls,
        luckMultiplier, new java.util.LinkedHashSet<>(announceTiers), announceTemplate, deterministic, seedSalt);
  }

  private static boolean hasLootData(ConfigurationSection loot) {
    return loot.contains("drops")
        || loot.contains("guaranteed")
        || loot.contains("guaranteedDrops")
        || loot.contains("rolls")
        || loot.contains("bonusRolls")
        || loot.contains("luckMultiplier")
        || loot.contains("clearVanilla")
        || loot.contains("announceTiers")
        || loot.contains("announceTemplate")
        || loot.contains("deterministic")
        || loot.contains("seed")
        || loot.contains("pools")
        || loot.contains("bundles")
        || loot.contains("rewards");
  }

  private void parseStylePresets(YamlConfiguration cfg, Map<String, MobStyleSpec> presets, List<String> errors,
      String source) {
    ConfigurationSection section = cfg.getConfigurationSection("stylePresets");
    if (section == null) {
      return;
    }
    for (String rawId : section.getKeys(false)) {
      ConfigurationSection presetSec = section.getConfigurationSection(rawId);
      if (presetSec == null) {
        continue;
      }
      String id = Ids.normalize(rawId);
      String base = prefix(source) + "stylePresets." + rawId;
      try {
        presets.put(id, parseStyleSpec(presetSec, base));
      } catch (RuntimeException ex) {
        errors.add(base + ": " + ex.getMessage());
      }
    }
  }

  private MobStyleSpec resolveStyleSpec(ConfigurationSection node, String base, String tier) {
    if (node == null) {
      return null;
    }
    String presetId = YamlValues.string(node, "stylePreset", null);
    if (presetId == null && tier != null) {
      presetId = tier;
    }
    MobStyleSpec preset = null;
    if (presetId != null) {
      MobStyleSpec resolved = stylePresets.get(Ids.normalize(presetId));
      if (resolved == null) {
        throw new IllegalArgumentException(base + ".stylePreset: unknown preset " + presetId);
      }
      preset = resolved;
    }
    ConfigurationSection styleSection = node.getConfigurationSection("style");
    MobStyleSpec local = styleSection == null ? null : parseStyleSpec(styleSection, base + ".style");
    if (preset == null) {
      return local;
    }
    if (local == null) {
      return preset;
    }
    Component nameplate = local.nameplate() != null ? local.nameplate() : preset.nameplate();
    Boolean showName = local.showName() != null ? local.showName() : preset.showName();
    MobBossBarSpec bossBar = local.bossBar() != null ? local.bossBar() : preset.bossBar();
    return new MobStyleSpec(nameplate, showName, bossBar);
  }

  private MobStyleSpec resolveStyleSpec(Map<?, ?> raw, String base, String tier) {
    if (raw == null) {
      return null;
    }
    String presetId = YamlValues.string(raw, "stylePreset", null);
    if (presetId == null && tier != null) {
      presetId = tier;
    }
    MobStyleSpec preset = null;
    if (presetId != null) {
      MobStyleSpec resolved = stylePresets.get(Ids.normalize(presetId));
      if (resolved == null) {
        throw new IllegalArgumentException(base + ".stylePreset: unknown preset " + presetId);
      }
      preset = resolved;
    }
    MobStyleSpec local = null;
    Object styleRaw = raw.get("style");
    if (styleRaw instanceof ConfigurationSection section) {
      local = parseStyleSpec(section, base + ".style");
    } else if (styleRaw instanceof Map<?, ?> styleMap) {
      local = parseStyleSpec(styleMap, base + ".style");
    }
    if (preset == null) {
      return local;
    }
    if (local == null) {
      return preset;
    }
    Component nameplate = local.nameplate() != null ? local.nameplate() : preset.nameplate();
    Boolean showName = local.showName() != null ? local.showName() : preset.showName();
    MobBossBarSpec bossBar = local.bossBar() != null ? local.bossBar() : preset.bossBar();
    return new MobStyleSpec(nameplate, showName, bossBar);
  }

  private MobStyleSpec parseStyleSpec(ConfigurationSection section, String base) {
    String name = YamlValues.string(section, "name", null);
    Component nameplate = name == null ? null : MobText.parse(name);
    Boolean showName = null;
    if (section.contains("showName")) {
      showName = section.getBoolean("showName");
    } else if (name != null) {
      showName = Boolean.TRUE;
    }
    MobBossBarSpec bossBar = parseStyleBossBar(section.getConfigurationSection("bossbar"),
        base + ".bossbar");
    return new MobStyleSpec(nameplate, showName, bossBar);
  }

  private MobStyleSpec parseStyleSpec(Map<?, ?> map, String base) {
    String name = YamlValues.string(map, "name", null);
    Component nameplate = name == null ? null : MobText.parse(name);
    Boolean showName = null;
    if (map.containsKey("showName")) {
      showName = YamlValues.bool(map.get("showName"), true);
    } else if (name != null) {
      showName = Boolean.TRUE;
    }
    MobBossBarSpec bossBar = null;
    Object bossRaw = map.get("bossbar");
    if (bossRaw instanceof ConfigurationSection section) {
      bossBar = parseStyleBossBar(section, base + ".bossbar");
    } else if (bossRaw instanceof Map<?, ?> bossMap) {
      bossBar = parseStyleBossBar(bossMap, base + ".bossbar");
    }
    return new MobStyleSpec(nameplate, showName, bossBar);
  }

  private MobBossBarSpec parseStyleBossBar(ConfigurationSection section, String base) {
    if (section == null) {
      return null;
    }
    if (section.contains("enabled") && !section.getBoolean("enabled", true)) {
      return null;
    }
    String title = YamlValues.string(section, "title", null);
    if (title == null || title.isBlank()) {
      return null;
    }
    BossBar.Color color = parseBossBarColor(YamlValues.string(section, "color", "RED"), base + ".color");
    BossBar.Overlay overlay = parseBossBarOverlay(YamlValues.string(section, "overlay", "PROGRESS"), base + ".overlay");
    MobBossBarAudience audience = parseBossBarAudience(YamlValues.string(section, "audience", "ALL_PLAYERS"),
        base + ".audience");
    return new MobBossBarSpec(MobText.parse(title), color, overlay, audience);
  }

  private MobBossBarSpec parseStyleBossBar(Map<?, ?> map, String base) {
    if (map == null) {
      return null;
    }
    Boolean enabled = map.containsKey("enabled") ? YamlValues.bool(map.get("enabled"), true) : null;
    if (enabled != null && !enabled) {
      return null;
    }
    String title = YamlValues.string(map, "title", null);
    if (title == null || title.isBlank()) {
      return null;
    }
    BossBar.Color color = parseBossBarColor(YamlValues.string(map, "color", "RED"), base + ".color");
    BossBar.Overlay overlay = parseBossBarOverlay(YamlValues.string(map, "overlay", "PROGRESS"), base + ".overlay");
    MobBossBarAudience audience = parseBossBarAudience(YamlValues.string(map, "audience", "ALL_PLAYERS"),
        base + ".audience");
    return new MobBossBarSpec(MobText.parse(title), color, overlay, audience);
  }

  private MobModelSpec parseModelSpec(ConfigurationSection section, String path) {
    if (section == null) {
      return null;
    }
    String id = YamlValues.string(section, "id", YamlValues.string(section, "modelId", null));
    if (id == null || id.isBlank()) {
      return null;
    }
    String animation = YamlValues.string(section, "animation", YamlValues.string(section, "animationId", null));
    double speed = section.getDouble("animationSpeed", 1.0);
    return new MobModelSpec(id, animation, speed);
  }

  private MobModelSpec parseModelSpec(Map<?, ?> map, String path) {
    if (map == null) {
      return null;
    }
    String id = YamlValues.string(map, "id", YamlValues.string(map, "modelId", null));
    if (id == null || id.isBlank()) {
      return null;
    }
    String animation = YamlValues.string(map, "animation", YamlValues.string(map, "animationId", null));
    double speed = YamlValues.doubleValue(map.get("animationSpeed"), 1.0);
    return new MobModelSpec(id, animation, speed);
  }

  private static MobLootSpec mergeLootSpec(MobLootSpec pool, MobLootSpec local, ConfigurationSection loot) {
    boolean clearVanilla = loot.contains("clearVanilla") ? local.clearVanilla() : pool.clearVanilla();
    int rolls = loot.contains("rolls") ? local.rolls() : pool.rolls();
    int bonusRolls = loot.contains("bonusRolls") ? local.bonusRolls() : pool.bonusRolls();
    double luckMultiplier = loot.contains("luckMultiplier") ? local.luckMultiplier() : pool.luckMultiplier();
    String announceTemplate = loot.contains("announceTemplate") ? local.announceTemplate() : pool.announceTemplate();
    boolean deterministic = loot.contains("deterministic") ? local.deterministic() : pool.deterministic();
    long seedSalt = loot.contains("seed") ? local.seedSalt() : pool.seedSalt();
    MobLootRewardSpec rewards = loot.contains("rewards") ? local.rewards() : pool.rewards();
    java.util.LinkedHashSet<String> announceTiers = new java.util.LinkedHashSet<>(pool.announceTiers());
    if (loot.contains("announceTiers")) {
      announceTiers.clear();
      announceTiers.addAll(local.announceTiers());
    }
    List<MobDropSpec> guaranteed = new ArrayList<>(pool.guaranteed());
    guaranteed.addAll(local.guaranteed());
    List<MobDropSpec> drops = new ArrayList<>(pool.drops());
    drops.addAll(local.drops());
    List<MobLootPoolRef> pools = new ArrayList<>(pool.pools());
    pools.addAll(local.pools());
    List<MobLootBundleSpec> bundles = new ArrayList<>(pool.bundles());
    bundles.addAll(local.bundles());
    return new MobLootSpec(clearVanilla, guaranteed, drops, pools, bundles, rewards, rolls, bonusRolls, luckMultiplier,
        announceTiers, announceTemplate, deterministic, seedSalt);
  }

  private List<MobLootPoolRef> parseLootPoolRefs(List<Map<?, ?>> rawPools, String basePath) {
    List<MobLootPoolRef> pools = new ArrayList<>();
    for (int i = 0; i < rawPools.size(); i++) {
      Map<?, ?> raw = rawPools.get(i);
      String path = basePath + "[" + i + "]";
      String poolId = YamlValues.string(raw, "id",
          YamlValues.string(raw, "pool", YamlValues.string(raw, "lootPool", null)));
      if (poolId == null || poolId.isBlank()) {
        throw new IllegalArgumentException(path + ".id: missing loot pool id");
      }
      String normalized = Ids.normalize(poolId);
      Integer rolls = readInt(raw.get("rolls"));
      Integer bonusRolls = readInt(raw.get("bonusRolls"));
      Double luckMultiplier = readDouble(raw.get("luckMultiplier"));
      double chance = normalizeChance(
          YamlValues.doubleValueStrict(raw, "chance", 100.0), path + ".chance");
      Boolean deterministic = raw.containsKey("deterministic")
          ? Boolean.valueOf(String.valueOf(raw.get("deterministic"))) : null;
      Long seedSalt = raw.containsKey("seed") ? parseSeedSalt(raw.get("seed"), path + ".seed") : null;
      MobDropConditions conditions = parseDropConditions(raw.get("conditions"), path + ".conditions");
      pools.add(new MobLootPoolRef(normalized, rolls, bonusRolls, luckMultiplier, chance, deterministic, seedSalt,
          conditions));
    }
    return pools;
  }

  private List<MobLootBundleSpec> parseLootBundles(List<Map<?, ?>> rawBundles, String basePath) {
    List<MobLootBundleSpec> bundles = new ArrayList<>();
    for (int i = 0; i < rawBundles.size(); i++) {
      Map<?, ?> raw = rawBundles.get(i);
      String path = basePath + "[" + i + "]";
      List<MobDropSpec> drops = parseDrops(castMapList(raw.get("drops")), path + ".drops", false);
      int rolls = YamlValues.intValueStrict(raw, "rolls", 1);
      int bonusRolls = YamlValues.intValueStrict(raw, "bonusRolls", 0);
      double chance = normalizeChance(
          YamlValues.doubleValueStrict(raw, "chance", 100.0), path + ".chance");
      MobDropConditions conditions = parseDropConditions(raw.get("conditions"), path + ".conditions");
      bundles.add(new MobLootBundleSpec(drops, rolls, bonusRolls, chance, conditions));
    }
    return bundles;
  }

  private MobLootRewardSpec parseLootRewards(ConfigurationSection rewards, String base) {
    if (rewards == null) {
      return null;
    }
    int xp = Math.max(0, rewards.getInt("xp", 0));
    int skillPoints = Math.max(0, rewards.getInt("skillPoints", rewards.getInt("skillpoints", 0)));
    int tokens = Math.max(0, rewards.getInt("tokens", rewards.getInt("token", 0)));
    List<ItemStack> rewardItems = new ArrayList<>();
    List<Map<?, ?>> rawItems = rewards.getMapList("items");
    for (int i = 0; i < rawItems.size(); i++) {
      Map<?, ?> raw = rawItems.get(i);
      String path = base + ".items[" + i + "]";
      ItemStack item = itemFromMap(raw, path);
      if (item == null) {
        throw new IllegalArgumentException(path + ".item: missing item");
      }
      int amount = YamlValues.intValueStrict(raw, "amount", item.getAmount());
      item.setAmount(Math.max(1, amount));
      rewardItems.add(item);
    }
    return new MobLootRewardSpec(xp, skillPoints, tokens, rewardItems);
  }

  private List<MobDropSpec> parseDrops(List<Map<?, ?>> rawDrops, String basePath, boolean guaranteed) {
    List<MobDropSpec> drops = new ArrayList<>();
    for (int i = 0; i < rawDrops.size(); i++) {
      Map<?, ?> raw = rawDrops.get(i);
      String path = basePath + "[" + i + "]";
      ItemStack item = itemFromMap(raw, path);
      if (item == null) {
        throw new IllegalArgumentException(path + ".item: missing item");
      }
      String tier = YamlValues.string(raw, "tier", null);
      String tokenId = YamlValues.string(raw, "token", YamlValues.string(raw, "tokenTier", null));
      boolean tokenDrop = tokenId != null && !tokenId.isBlank();
      MobDropConditions conditions = parseDropConditions(raw.get("conditions"), path + ".conditions");
      int[] durabilityRange = parseDurabilityRange(raw, path, item);
      double chance = guaranteed ? 1.0 : normalizeChance(
          YamlValues.doubleValueStrict(raw, "chance", 100.0),
          path + ".chance");
      int amount = YamlValues.intValueStrict(raw, "amount", -1);
      int minAmount;
      int maxAmount;
      if (amount > 0) {
        minAmount = amount;
        maxAmount = amount;
      } else {
        minAmount = YamlValues.intValueStrict(raw, "min", item.getAmount());
        maxAmount = YamlValues.intValueStrict(raw, "max", minAmount);
      }
      Integer minDamage = durabilityRange == null ? null : durabilityRange[0];
      Integer maxDamage = durabilityRange == null ? null : durabilityRange[1];
      drops.add(new MobDropSpec(item, tier, chance, minAmount, maxAmount, tokenDrop, conditions, minDamage, maxDamage));
    }
    return drops;
  }

  private static MobDropConditions parseDropConditions(Object raw, String path) {
    if (raw == null) {
      return MobDropConditions.none();
    }
    Map<String, Object> map = raw instanceof ConfigurationSection section ? section.getValues(false) : castMap(raw);
    Integer minMob = readInt(map.get("minMobLevel"));
    if (minMob == null) {
      minMob = readInt(map.get("minLevel"));
    }
    Integer maxMob = readInt(map.get("maxMobLevel"));
    if (maxMob == null) {
      maxMob = readInt(map.get("maxLevel"));
    }
    java.util.Set<String> biomes = parseBiomeSet(map.get("biomes"));
    if (biomes.isEmpty()) {
      biomes = parseBiomeSet(map.get("biome"));
    }
    Integer minTime = readInt(map.get("minTime"));
    Integer maxTime = readInt(map.get("maxTime"));
    Double minLuck = readDouble(map.get("minLuck"));
    Double maxLuck = readDouble(map.get("maxLuck"));
    return new MobDropConditions(minMob, maxMob, biomes, minTime, maxTime, minLuck, maxLuck);
  }

  private static int[] parseDurabilityRange(Map<?, ?> raw, String path, ItemStack item) {
    Object durabilityRaw = raw.get("durability");
    if (durabilityRaw == null) {
      durabilityRaw = raw.get("damage");
    }
    Integer min = null;
    Integer max = null;
    if (durabilityRaw instanceof Map<?, ?> range) {
      min = readInt(range.get("min"));
      max = readInt(range.get("max"));
    } else if (durabilityRaw instanceof Number num) {
      min = num.intValue();
      max = min;
    }
    if (min == null && max == null) {
      min = readInt(raw.get("durabilityMin"));
      max = readInt(raw.get("durabilityMax"));
    }
    if (min == null && max == null) {
      min = readInt(raw.get("damageMin"));
      max = readInt(raw.get("damageMax"));
    }
    if (min == null && max == null) {
      return null;
    }
    int maxDurability = item.getType().getMaxDurability();
    if (maxDurability <= 0) {
      throw new IllegalArgumentException(path + ".durability: item is not damageable");
    }
    int minVal = Math.max(0, min == null ? 0 : min);
    int maxVal = Math.max(minVal, max == null ? minVal : max);
    minVal = Math.min(minVal, maxDurability);
    maxVal = Math.min(maxVal, maxDurability);
    return new int[] { minVal, maxVal };
  }

  private static java.util.Set<String> parseBiomeSet(Object raw) {
    if (raw == null) {
      return java.util.Set.of();
    }
    java.util.Set<String> out = new java.util.LinkedHashSet<>();
    if (raw instanceof List<?> list) {
      for (Object entry : list) {
        String value = entry == null ? null : String.valueOf(entry);
        String normalized = normalizeBiome(value);
        if (normalized != null) {
          out.add(normalized);
        }
      }
      return java.util.Set.copyOf(out);
    }
    String normalized = normalizeBiome(String.valueOf(raw));
    if (normalized != null) {
      out.add(normalized);
    }
    return java.util.Set.copyOf(out);
  }

  private static String normalizeBiome(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String key = trimmed.toLowerCase(Locale.ROOT);
    int idx = key.indexOf(':');
    if (idx >= 0) {
      key = key.substring(idx + 1);
    }
    return key;
  }

  private static Integer readInt(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number num) {
      return num.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Double readDouble(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number num) {
      return num.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static long parseSeedSalt(Object raw, String path) {
    if (raw == null) {
      return 0L;
    }
    if (raw instanceof Number num) {
      long value = num.longValue();
      if (value < 0L) {
        throw new IllegalArgumentException(path + ": seed must be >= 0");
      }
      return value;
    }
    String text = String.valueOf(raw).trim();
    if (text.isEmpty()) {
      return 0L;
    }
    try {
      long value = Long.parseLong(text);
      if (value < 0L) {
        throw new IllegalArgumentException(path + ": seed must be >= 0");
      }
      return value;
    } catch (NumberFormatException ex) {
      return Math.abs(text.hashCode());
    }
  }

  private static Map<String, Object> castMap(Object raw) {
    if (!(raw instanceof Map<?, ?> mapRaw)) {
      return java.util.Map.of();
    }
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
      out.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return out;
  }

  private static List<Map<?, ?>> castMapList(Object raw) {
    if (!(raw instanceof List<?> listRaw)) {
      return List.of();
    }
    List<Map<?, ?>> out = new ArrayList<>();
    for (Object entry : listRaw) {
      if (entry instanceof Map<?, ?> map) {
        out.add(map);
      }
    }
    return out;
  }

  private static double normalizeChance(double rawChance, String path) {
    double chance = rawChance;
    if (chance > 1.0) {
      chance = chance / 100.0;
    }
    if (!(chance >= 0.0 && chance <= 1.0)) {
      throw new IllegalArgumentException(path + ": chance must be 0..1 or 0..100%");
    }
    return chance;
  }

  private static MobAttackTrigger parseTrigger(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MobAttackTrigger.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid trigger=" + raw);
    }
  }

  private static MobPartyRule parsePartyRule(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MobPartyRule.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid partyRule=" + raw);
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

  private static MobAiGoalSpec parseAiGoal(Map<?, ?> raw, String path) {
    String typeRaw = YamlValues.string(raw, "type", null);
    if (typeRaw == null || typeRaw.isBlank()) {
      throw new IllegalArgumentException(path + ".type: missing goal type");
    }
    MobAiGoalType type;
    try {
      type = MobAiGoalType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ".type: invalid goal type=" + typeRaw);
    }
    int priority = YamlValues.intValueStrict(raw, "priority", 100);
    double radius = YamlValues.doubleValueStrict(raw, "radius", 6.0);
    double speed = YamlValues.doubleValueStrict(raw, "speed", 0.2);
    long intervalTicks = YamlValues.longValueStrict(raw, "intervalTicks", 80L);
    List<Vector> points = new ArrayList<>();
    Object pointsRaw = raw.get("points");
    if (pointsRaw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Object entry = list.get(i);
        if (!(entry instanceof Map<?, ?> pointMap)) {
          throw new IllegalArgumentException(path + ".points[" + i + "]: expected map");
        }
        double x = YamlValues.doubleValueStrict(pointMap, "x", 0.0);
        double y = YamlValues.doubleValueStrict(pointMap, "y", 0.0);
        double z = YamlValues.doubleValueStrict(pointMap, "z", 0.0);
        points.add(new Vector(x, y, z));
      }
    }
    return new MobAiGoalSpec(type, priority, radius, speed, intervalTicks, points);
  }

  private static MobBossBarAudience parseBossBarAudience(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MobBossBarAudience.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid audience=" + raw);
    }
  }

  private static MobLocomotionMode parseLocomotionMode(String raw, String path) {
    String s = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return MobLocomotionMode.valueOf(s);
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": invalid locomotion=" + raw);
    }
  }

  private MobCombatSpec parseCombat(ConfigurationSection section, String path) {
    if (section == null) {
      return null;
    }
    MobCombatSpec.Builder builder = MobCombatSpec.builder()
        .armorMultiplier(section.getDouble("armorMultiplier", 1.0))
        .blockChance(section.getDouble("blockChance", 0.0))
        .blockMultiplier(section.getDouble("blockMultiplier", 0.5))
        .blockCooldownTicks(section.getLong("blockCooldownTicks", 40L));
    List<String> immune = section.getStringList("immuneEffects");
    for (String raw : immune) {
      PotionEffectType type = parsePotionEffect(raw, path + ".immuneEffects");
      builder.addImmuneEffect(type);
    }
    List<String> cleanse = section.getStringList("cleanseEffects");
    for (String raw : cleanse) {
      PotionEffectType type = parsePotionEffect(raw, path + ".cleanseEffects");
      builder.addCleanseEffect(type);
    }
    return builder.build();
  }

  private MobDamageBonusSpec parseDamageBonus(Map<?, ?> raw, String path) {
    String typeRaw = YamlValues.string(raw, "damageType", null);
    if (typeRaw == null || typeRaw.isBlank()) {
      throw new IllegalArgumentException(path + ".damageType: missing damageType");
    }
    DamageType type = parseDamageType(typeRaw, path + ".damageType");
    String filterRaw = YamlValues.string(raw, "target", "ANY");
    MobTargetFilter filter;
    try {
      filter = MobTargetFilter.valueOf(filterRaw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ".target: invalid target=" + filterRaw);
    }
    double multiplier = YamlValues.doubleValueStrict(raw, "multiplier", 1.0);
    return new MobDamageBonusSpec(type, filter, multiplier);
  }

  private MobAttackAoESpec parseAttackAoE(ConfigurationSection section, String path) {
    String shapeRaw = YamlValues.string(section, "shape", "SPHERE");
    MobAttackAoEShape shape;
    try {
      shape = MobAttackAoEShape.valueOf(shapeRaw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ".shape: invalid shape=" + shapeRaw);
    }
    double radius = section.getDouble("radius", 3.0);
    double height = section.getDouble("height", radius);
    double angle = section.getDouble("angleDegrees", 90.0);
    int maxTargets = section.getInt("maxTargets", 0);
    String filterRaw = YamlValues.string(section, "filter", "ANY");
    MobTargetFilter filter;
    try {
      filter = MobTargetFilter.valueOf(filterRaw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ".filter: invalid filter=" + filterRaw);
    }
    return new MobAttackAoESpec(shape, radius, height, angle, maxTargets, filter);
  }

  private PotionEffectType parsePotionEffect(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": empty potion effect");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    org.bukkit.NamespacedKey key = normalized.contains(":")
        ? org.bukkit.NamespacedKey.fromString(normalized)
        : org.bukkit.NamespacedKey.minecraft(normalized);
    PotionEffectType type = key == null ? null : org.bukkit.Registry.POTION_EFFECT_TYPE.get(key);
    if (type == null) {
      throw new IllegalArgumentException(path + ": invalid potion effect=" + raw);
    }
    return type;
  }

  private String parseEventAbility(ConfigurationSection section, String key, String path, String mobId) {
    if (section == null || !section.contains(key)) {
      return null;
    }
    Object raw = section.get(key);
    if (raw == null) {
      return null;
    }
    if (raw instanceof String text) {
      return text.isBlank() ? null : text;
    }
    if (raw instanceof Map<?, ?> map) {
      Object abilityRaw = map.get("ability");
      if (abilityRaw instanceof String text) {
        return text.isBlank() ? null : text;
      }
    }
    String scriptId = "mob:" + mobId + ":" + key;
    Action action = yamlAbilities.compileScriptAction(raw, path + ".script", scriptId);
    return registerScriptAbility(scriptId, action, path + ".script");
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
    String particleRaw = YamlValues.requireString(sec, "particle", path + ".particle");
    Particle particle = parseParticleType(particleRaw, path + ".particle");
    int count = sec.getInt("count", 10);
    double offsetX = sec.getDouble("offsetX", sec.getDouble("offset", 0.2));
    double offsetY = sec.getDouble("offsetY", sec.getDouble("offset", 0.2));
    double offsetZ = sec.getDouble("offsetZ", sec.getDouble("offset", 0.2));
    double extra = sec.getDouble("extra", 0.0);
    return new MobParticlesSpec(particle, count, offsetX, offsetY, offsetZ, extra);
  }

  private static Particle parseParticleType(String raw, String path) {
    String token = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
    try {
      return Particle.valueOf(token.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException(path + ": unknown particle=" + raw);
    }
  }

  private static MobSoundSpec parseSound(ConfigurationSection sec, String path) {
    if (sec == null) {
      return null;
    }
    String raw = YamlValues.requireString(sec, "sound", path + ".sound");
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

  private List<MobSpawnGroupSpec> parseSpawnerGroups(List<Map<?, ?>> rawGroups, String base) {
    if (rawGroups == null || rawGroups.isEmpty()) {
      return List.of();
    }
    List<MobSpawnGroupSpec> groups = new ArrayList<>();
    for (int i = 0; i < rawGroups.size(); i++) {
      Map<?, ?> raw = rawGroups.get(i);
      if (raw == null) {
        continue;
      }
      String entryBase = base + "[" + i + "]";
      double chance = 1.0;
      Integer count = null;
      Object chanceRaw = raw.get("chance");
      if (chanceRaw instanceof Number num) {
        chance = num.doubleValue();
      }
      Object countRaw = raw.get("count");
      if (countRaw instanceof Number num) {
        count = num.intValue();
      }
      List<MobSpawnGroupEntry> entries = parseSpawnerGroupEntries(raw.get("mobs"), entryBase + ".mobs");
      if (entries.isEmpty()) {
        continue;
      }
      MobSpawnRulesSpec rules = parseSpawnRules(raw.get("rules"), entryBase + ".rules");
      groups.add(new MobSpawnGroupSpec(chance, count, entries, rules));
    }
    return groups;
  }

  private List<MobSpawnGroupEntry> parseSpawnerGroupEntries(Object raw, String base) {
    if (raw == null) {
      return List.of();
    }
    List<MobSpawnGroupEntry> entries = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Object item = list.get(i);
        if (item instanceof String s) {
          String id = Ids.normalize(s);
          entries.add(new MobSpawnGroupEntry(id, 1.0));
        } else if (item instanceof Map<?, ?> map) {
          Object idRaw = map.get("mob");
          if (idRaw == null) {
            idRaw = map.get("id");
          }
          if (!(idRaw instanceof String s) || s.isBlank()) {
            continue;
          }
          String id = Ids.normalize(s);
          double weight = 1.0;
          Object weightRaw = map.get("weight");
          if (weightRaw instanceof Number num) {
            weight = num.doubleValue();
          }
          entries.add(new MobSpawnGroupEntry(id, weight));
        }
      }
    }
    return entries;
  }

  private void validateSpawnerGroups(List<MobSpawnGroupSpec> groups, Map<String, MobSpec> specs, String base) {
    if (groups == null || groups.isEmpty()) {
      return;
    }
    for (int i = 0; i < groups.size(); i++) {
      MobSpawnGroupSpec group = groups.get(i);
      if (group.mobs() == null || group.mobs().isEmpty()) {
        throw new IllegalArgumentException(base + "[" + i + "]: mobs list is empty");
      }
      for (MobSpawnGroupEntry entry : group.mobs()) {
        String mobId = entry.mobId();
        if (!specs.containsKey(mobId) && !registry.has(mobId)) {
          throw new IllegalArgumentException(base + "[" + i + "]: unknown mob id: " + mobId);
        }
      }
    }
  }

  private List<Map<String, Object>> serializeSpawnerGroups(List<MobSpawnGroupSpec> groups) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (MobSpawnGroupSpec group : groups) {
      Map<String, Object> map = new LinkedHashMap<>();
      if (group.chance() != 1.0) {
        map.put("chance", group.chance());
      }
      if (group.count() != null) {
        map.put("count", group.count());
      }
      List<Map<String, Object>> mobs = new ArrayList<>();
      for (MobSpawnGroupEntry entry : group.mobs()) {
        Map<String, Object> entryMap = new LinkedHashMap<>();
        entryMap.put("mob", entry.mobId());
        if (entry.weight() != 1.0) {
          entryMap.put("weight", entry.weight());
        }
        mobs.add(entryMap);
      }
      map.put("mobs", mobs);
      if (group.rules() != null && !group.rules().isEmpty()) {
        map.put("rules", serializeSpawnRules(group.rules()));
      }
      out.add(map);
    }
    return out;
  }

  private MobSpawnRulesSpec parseSpawnRules(Object raw, String base) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof ConfigurationSection sec) {
      return parseSpawnRulesSection(sec, base);
    }
    if (raw instanceof Map<?, ?> map) {
      return parseSpawnRulesMap(map, base);
    }
    throw new IllegalArgumentException(base + ": rules must be a map");
  }

  private MobSpawnRulesSpec parseSpawnRulesSection(ConfigurationSection rules, String base) {
    Set<NamespacedKey> allowBiomes = parseBiomeKeys(rules.get("biomes"), base + ".biomes");
    if (allowBiomes == null) {
      allowBiomes = parseBiomeKeys(rules.get("allowedBiomes"), base + ".allowedBiomes");
    }
    Set<NamespacedKey> denyBiomes = parseBiomeKeys(rules.get("excludeBiomes"), base + ".excludeBiomes");
    if (denyBiomes == null) {
      denyBiomes = parseBiomeKeys(rules.get("deniedBiomes"), base + ".deniedBiomes");
    }
    List<MobSpawnTimeWindow> timeWindows = parseTimeWindows(rules.get("time"), base + ".time");
    if (timeWindows.isEmpty()) {
      timeWindows = parseTimeWindows(rules.get("times"), base + ".times");
    }
    Integer minY = rules.contains("minY") ? rules.getInt("minY") : null;
    Integer maxY = rules.contains("maxY") ? rules.getInt("maxY") : null;
    List<MobSpawnRegionSpec> regions = parseRegions(rules.get("regions"), base + ".regions");
    if (regions.isEmpty()) {
      regions = parseRegions(rules.get("region"), base + ".region");
    }
    int minLevel = rules.getInt("minLevel", 0);
    int maxLevel = rules.getInt("maxLevel", 0);
    int minParty = rules.getInt("minPartySize", 0);
    int maxParty = rules.getInt("maxPartySize", 0);
    int minPlayers = rules.getInt("minPlayers", 0);
    int maxPlayers = rules.getInt("maxPlayers", 0);
    MobSpawnDungeonRule dungeonRule = MobSpawnDungeonRule.parse(rules.getString("dungeon"), base + ".dungeon");
    return new MobSpawnRulesSpec(allowBiomes, denyBiomes, timeWindows, minY, maxY, regions,
        minLevel, maxLevel, minParty, maxParty, minPlayers, maxPlayers, dungeonRule);
  }

  private MobSpawnRulesSpec parseSpawnRulesMap(Map<?, ?> rules, String base) {
    Set<NamespacedKey> allowBiomes = parseBiomeKeys(rules.get("biomes"), base + ".biomes");
    if (allowBiomes == null) {
      allowBiomes = parseBiomeKeys(rules.get("allowedBiomes"), base + ".allowedBiomes");
    }
    Set<NamespacedKey> denyBiomes = parseBiomeKeys(rules.get("excludeBiomes"), base + ".excludeBiomes");
    if (denyBiomes == null) {
      denyBiomes = parseBiomeKeys(rules.get("deniedBiomes"), base + ".deniedBiomes");
    }
    List<MobSpawnTimeWindow> timeWindows = parseTimeWindows(rules.get("time"), base + ".time");
    if (timeWindows.isEmpty()) {
      timeWindows = parseTimeWindows(rules.get("times"), base + ".times");
    }
    Integer minY = rules.containsKey("minY") ? YamlValues.intValueStrict(rules, "minY", 0) : null;
    Integer maxY = rules.containsKey("maxY") ? YamlValues.intValueStrict(rules, "maxY", 0) : null;
    List<MobSpawnRegionSpec> regions = parseRegions(rules.get("regions"), base + ".regions");
    if (regions.isEmpty()) {
      regions = parseRegions(rules.get("region"), base + ".region");
    }
    int minLevel = YamlValues.intValue(rules.get("minLevel"), 0);
    int maxLevel = YamlValues.intValue(rules.get("maxLevel"), 0);
    int minParty = YamlValues.intValue(rules.get("minPartySize"), 0);
    int maxParty = YamlValues.intValue(rules.get("maxPartySize"), 0);
    int minPlayers = YamlValues.intValue(rules.get("minPlayers"), 0);
    int maxPlayers = YamlValues.intValue(rules.get("maxPlayers"), 0);
    MobSpawnDungeonRule dungeonRule = MobSpawnDungeonRule.parse(YamlValues.string(rules, "dungeon", null), base + ".dungeon");
    return new MobSpawnRulesSpec(allowBiomes, denyBiomes, timeWindows, minY, maxY, regions,
        minLevel, maxLevel, minParty, maxParty, minPlayers, maxPlayers, dungeonRule);
  }

  private Set<NamespacedKey> parseBiomeKeys(Object raw, String base) {
    if (raw == null) {
      return null;
    }
    Set<NamespacedKey> out = new HashSet<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Object item = list.get(i);
        if (item instanceof String s) {
          out.add(parseBiomeKey(s, base + "[" + i + "]"));
        }
      }
    } else if (raw instanceof String s) {
      out.add(parseBiomeKey(s, base));
    }
    return out.isEmpty() ? null : out;
  }

  private NamespacedKey parseBiomeKey(String raw, String path) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(path + ": biome key is empty");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (!normalized.contains(":")) {
      normalized = "minecraft:" + normalized;
    }
    NamespacedKey key = NamespacedKey.fromString(normalized);
    if (key == null) {
      throw new IllegalArgumentException(path + ": invalid biome key " + raw);
    }
    return key;
  }

  private List<MobSpawnTimeWindow> parseTimeWindows(Object raw, String base) {
    if (raw == null) {
      return List.of();
    }
    List<MobSpawnTimeWindow> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Object entry = list.get(i);
        out.addAll(parseTimeWindow(entry, base + "[" + i + "]"));
      }
    } else {
      out.addAll(parseTimeWindow(raw, base));
    }
    return out;
  }

  private List<MobSpawnTimeWindow> parseTimeWindow(Object raw, String base) {
    if (raw instanceof Map<?, ?> map) {
      int start = YamlValues.intValue(map.get("start"), 0);
      int end = YamlValues.intValue(map.get("end"), 23999);
      return List.of(new MobSpawnTimeWindow(start, end));
    }
    if (raw instanceof String s) {
      String key = s.trim().toLowerCase(Locale.ROOT);
      return switch (key) {
        case "day" -> List.of(new MobSpawnTimeWindow(0, 12000));
        case "night" -> List.of(new MobSpawnTimeWindow(13000, 23000));
        case "dawn" -> List.of(new MobSpawnTimeWindow(23000, 23999));
        case "dusk" -> List.of(new MobSpawnTimeWindow(12000, 13000));
        default -> throw new IllegalArgumentException(base + ": unknown time window " + s);
      };
    }
    return List.of();
  }

  private List<MobSpawnRegionSpec> parseRegions(Object raw, String base) {
    if (raw == null) {
      return List.of();
    }
    List<MobSpawnRegionSpec> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        out.addAll(parseRegionEntry(list.get(i), base + "[" + i + "]"));
      }
    } else {
      out.addAll(parseRegionEntry(raw, base));
    }
    return out;
  }

  private List<MobSpawnRegionSpec> parseRegionEntry(Object raw, String base) {
    if (raw instanceof Map<?, ?> map) {
      Map<?, ?> min = map.get("min") instanceof Map<?, ?> minMap ? minMap : null;
      Map<?, ?> max = map.get("max") instanceof Map<?, ?> maxMap ? maxMap : null;
      double minX = min != null ? YamlValues.doubleValue(min.get("x"), 0.0) : YamlValues.doubleValue(map.get("minX"), 0.0);
      double minY = min != null ? YamlValues.doubleValue(min.get("y"), 0.0) : YamlValues.doubleValue(map.get("minY"), 0.0);
      double minZ = min != null ? YamlValues.doubleValue(min.get("z"), 0.0) : YamlValues.doubleValue(map.get("minZ"), 0.0);
      double maxX = max != null ? YamlValues.doubleValue(max.get("x"), 0.0) : YamlValues.doubleValue(map.get("maxX"), 0.0);
      double maxY = max != null ? YamlValues.doubleValue(max.get("y"), 0.0) : YamlValues.doubleValue(map.get("maxY"), 0.0);
      double maxZ = max != null ? YamlValues.doubleValue(max.get("z"), 0.0) : YamlValues.doubleValue(map.get("maxZ"), 0.0);
      return List.of(MobSpawnRegionSpec.normalized(minX, minY, minZ, maxX, maxY, maxZ));
    }
    return List.of();
  }

  private Map<String, Object> serializeSpawnRules(MobSpawnRulesSpec rules) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (rules.allowedBiomes() != null && !rules.allowedBiomes().isEmpty()) {
      map.put("biomes", rules.allowedBiomes().stream().map(NamespacedKey::toString).toList());
    }
    if (rules.excludedBiomes() != null && !rules.excludedBiomes().isEmpty()) {
      map.put("excludeBiomes", rules.excludedBiomes().stream().map(NamespacedKey::toString).toList());
    }
    if (rules.timeWindows() != null && !rules.timeWindows().isEmpty()) {
      List<Map<String, Object>> times = new ArrayList<>();
      for (MobSpawnTimeWindow window : rules.timeWindows()) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("start", window.startTick());
        entry.put("end", window.endTick());
        times.add(entry);
      }
      map.put("time", times);
    }
    if (rules.minY() != null) {
      map.put("minY", rules.minY());
    }
    if (rules.maxY() != null) {
      map.put("maxY", rules.maxY());
    }
    if (rules.regions() != null && !rules.regions().isEmpty()) {
      List<Map<String, Object>> regions = new ArrayList<>();
      for (MobSpawnRegionSpec region : rules.regions()) {
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> min = new LinkedHashMap<>();
        Map<String, Object> max = new LinkedHashMap<>();
        min.put("x", region.minX());
        min.put("y", region.minY());
        min.put("z", region.minZ());
        max.put("x", region.maxX());
        max.put("y", region.maxY());
        max.put("z", region.maxZ());
        entry.put("min", min);
        entry.put("max", max);
        regions.add(entry);
      }
      map.put("regions", regions);
    }
    if (rules.minPlayerLevel() > 0) {
      map.put("minLevel", rules.minPlayerLevel());
    }
    if (rules.maxPlayerLevel() > 0) {
      map.put("maxLevel", rules.maxPlayerLevel());
    }
    if (rules.minPartySize() > 0) {
      map.put("minPartySize", rules.minPartySize());
    }
    if (rules.maxPartySize() > 0) {
      map.put("maxPartySize", rules.maxPartySize());
    }
    if (rules.minPlayers() > 0) {
      map.put("minPlayers", rules.minPlayers());
    }
    if (rules.maxPlayers() > 0) {
      map.put("maxPlayers", rules.maxPlayers());
    }
    if (rules.dungeonRule() != null && rules.dungeonRule() != MobSpawnDungeonRule.ANY) {
      map.put("dungeon", rules.dungeonRule().name().toLowerCase(Locale.ROOT));
    }
    return map;
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
