package dev.patric.dungeonsreborn.crafting;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class CraftingDiscoveryService {
  private static final String ROOT = "players";
  private static final String UNLOCKED_KEY = "unlocked";
  private static final String RESEARCH_KEY = "research";

  private record DiscoveryProfile(Set<String> unlocked, Map<String, Long> research) {
  }

  private final JavaPlugin plugin;
  private final ServiceLogger logger;
  private final CraftingYamlRegistry registry;
  private final Map<UUID, DiscoveryProfile> profiles = new ConcurrentHashMap<>();

  public CraftingDiscoveryService(JavaPlugin plugin, ServiceLogger logger, CraftingYamlRegistry registry) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public void load() {
    File file = file();
    if (!file.exists()) {
      return;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection players = yaml.getConfigurationSection(ROOT);
    if (players == null) {
      return;
    }
    for (String uuidRaw : players.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(uuidRaw);
        ConfigurationSection player = players.getConfigurationSection(uuidRaw);
        if (player == null) {
          continue;
        }
        Set<String> unlocked = new HashSet<>();
        for (String entry : player.getStringList(UNLOCKED_KEY)) {
          if (entry == null || entry.isBlank()) {
            continue;
          }
          unlocked.add(Ids.normalize(entry));
        }
        Map<String, Long> research = new HashMap<>();
        ConfigurationSection researchSection = player.getConfigurationSection(RESEARCH_KEY);
        if (researchSection != null) {
          for (String recipeId : researchSection.getKeys(false)) {
            long value = researchSection.getLong(recipeId);
            if (value > 0L) {
              research.put(Ids.normalize(recipeId), value);
            }
          }
        }
        profiles.put(uuid, new DiscoveryProfile(unlocked, research));
      } catch (Exception ex) {
        logger.warn("[Crafting] Failed to read discovery for " + uuidRaw + ": " + ex.getMessage());
      }
    }
  }

  public void save() {
    File file = file();
    YamlConfiguration yaml = new YamlConfiguration();
    ConfigurationSection players = yaml.createSection(ROOT);
    for (Map.Entry<UUID, DiscoveryProfile> entry : profiles.entrySet()) {
      String uuid = entry.getKey().toString();
      DiscoveryProfile profile = entry.getValue();
      ConfigurationSection player = players.createSection(uuid);
      List<String> unlocked = new ArrayList<>(profile.unlocked());
      unlocked.sort(String.CASE_INSENSITIVE_ORDER);
      player.set(UNLOCKED_KEY, unlocked);
      if (!profile.research().isEmpty()) {
        ConfigurationSection research = player.createSection(RESEARCH_KEY);
        for (Map.Entry<String, Long> researchEntry : profile.research().entrySet()) {
          research.set(researchEntry.getKey(), researchEntry.getValue());
        }
      }
    }
    try {
      yaml.save(file);
    } catch (Exception ex) {
      logger.warn("[Crafting] Failed to save discovery file: " + ex.getMessage());
    }
  }

  public boolean isUnlocked(UUID playerId, String recipeId) {
    if (playerId == null || recipeId == null) {
      return false;
    }
    DiscoveryProfile profile = profiles.get(playerId);
    if (profile == null) {
      return false;
    }
    return profile.unlocked().contains(Ids.normalize(recipeId));
  }

  public boolean isVisible(Player player, CraftingRecipeSpec spec) {
    if (spec == null) {
      return false;
    }
    CraftingDiscoverySpec discovery = spec.discovery();
    if (discovery == null) {
      return true;
    }
    if (!discovery.hidden()) {
      return true;
    }
    return player != null && isUnlocked(player.getUniqueId(), spec.id());
  }

  public boolean isAvailable(Player player, CraftingRecipeSpec spec) {
    if (spec == null) {
      return false;
    }
    CraftingDiscoverySpec discovery = spec.discovery();
    if (discovery == null) {
      return true;
    }
    if (discovery.hidden() && (player == null || !isUnlocked(player.getUniqueId(), spec.id()))) {
      return false;
    }
    if (player == null) {
      return discovery.requires().isEmpty();
    }
    return hasPrereqs(player.getUniqueId(), discovery.requires());
  }

  public boolean hasPrereqs(UUID playerId, List<String> requires) {
    if (requires == null || requires.isEmpty()) {
      return true;
    }
    for (String requirement : requires) {
      if (requirement == null || requirement.isBlank()) {
        continue;
      }
      if (!isUnlocked(playerId, requirement)) {
        return false;
      }
    }
    return true;
  }

  public long researchRemaining(UUID playerId, String recipeId) {
    DiscoveryProfile profile = profiles.get(playerId);
    if (profile == null) {
      return 0L;
    }
    Long until = profile.research().get(Ids.normalize(recipeId));
    if (until == null) {
      return 0L;
    }
    long remaining = until - System.currentTimeMillis();
    return Math.max(0L, remaining);
  }

  public boolean beginResearch(UUID playerId, CraftingRecipeSpec spec) {
    if (playerId == null || spec == null) {
      return false;
    }
    CraftingDiscoverySpec discovery = spec.discovery();
    if (discovery == null || discovery.researchSeconds() <= 0) {
      return unlock(playerId, spec.id(), "research");
    }
    DiscoveryProfile profile = profile(playerId);
    String normalized = Ids.normalize(spec.id());
    if (profile.unlocked().contains(normalized)) {
      return false;
    }
    long unlockAt = System.currentTimeMillis() + discovery.researchSeconds() * 1000L;
    profile.research().put(normalized, unlockAt);
    return true;
  }

  public boolean unlock(UUID playerId, String recipeId, String source) {
    if (playerId == null || recipeId == null) {
      return false;
    }
    DiscoveryProfile profile = profile(playerId);
    String normalized = Ids.normalize(recipeId);
    if (profile.unlocked().contains(normalized)) {
      return false;
    }
    profile.unlocked().add(normalized);
    profile.research().remove(normalized);
    logger.info("[Crafting] Discovery unlocked recipe=" + normalized + " player=" + playerId + " source=" + source);
    return true;
  }

  public void unlockFromCraft(Player player, CraftingRecipeSpec spec) {
    if (player == null || spec == null) {
      return;
    }
    CraftingDiscoverySpec discovery = spec.discovery();
    if (discovery == null) {
      return;
    }
    if (discovery.unlockOnCraft()) {
      unlock(player.getUniqueId(), spec.id(), "craft");
    }
    for (String grant : discovery.grants()) {
      if (grant == null || grant.isBlank()) {
        continue;
      }
      CraftingRecipeSpec target = registry.recipe(grant);
      if (target == null) {
        continue;
      }
      if (target.discovery() != null && target.discovery().researchSeconds() > 0) {
        beginResearch(player.getUniqueId(), target);
      } else {
        unlock(player.getUniqueId(), target.id(), "craft");
      }
    }
  }

  public void unlockFromQuest(Player player, String questId) {
    if (player == null || questId == null || questId.isBlank()) {
      return;
    }
    String normalizedQuest = questId.trim().toLowerCase(Locale.ROOT);
    for (CraftingRecipeTemplate template : registry.recipes().values()) {
      CraftingRecipeSpec spec = template.spec();
      CraftingDiscoverySpec discovery = spec.discovery();
      if (discovery == null || discovery.questUnlocks().isEmpty()) {
        continue;
      }
      boolean matches = false;
      for (String entry : discovery.questUnlocks()) {
        if (entry != null && entry.trim().toLowerCase(Locale.ROOT).equals(normalizedQuest)) {
          matches = true;
          break;
        }
      }
      if (!matches) {
        continue;
      }
      if (discovery.researchSeconds() > 0) {
        beginResearch(player.getUniqueId(), spec);
      } else {
        unlock(player.getUniqueId(), spec.id(), "quest");
      }
    }
  }

  public void unlockFromDrop(Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir()) {
      return;
    }
    String itemId = ItemMarkers.getItemId(item);
    String materialName = item.getType().name();
    for (CraftingRecipeTemplate template : registry.recipes().values()) {
      CraftingRecipeSpec spec = template.spec();
      CraftingDiscoverySpec discovery = spec.discovery();
      if (discovery == null) {
        continue;
      }
      boolean match = false;
      if (itemId != null && !discovery.dropItemIds().isEmpty()) {
        for (String entry : discovery.dropItemIds()) {
          if (entry != null && Ids.normalize(entry).equals(Ids.normalize(itemId))) {
            match = true;
            break;
          }
        }
      }
      if (!match && !discovery.dropMaterials().isEmpty()) {
        for (String entry : discovery.dropMaterials()) {
          if (entry != null && entry.equalsIgnoreCase(materialName)) {
            match = true;
            break;
          }
        }
      }
      if (!match) {
        continue;
      }
      if (discovery.researchSeconds() > 0) {
        beginResearch(player.getUniqueId(), spec);
      } else {
        unlock(player.getUniqueId(), spec.id(), "drop");
      }
    }
  }

  public void tick() {
    long now = System.currentTimeMillis();
    boolean changed = false;
    for (Map.Entry<UUID, DiscoveryProfile> entry : profiles.entrySet()) {
      DiscoveryProfile profile = entry.getValue();
      if (profile.research().isEmpty()) {
        continue;
      }
      List<String> unlocked = new ArrayList<>();
      for (Map.Entry<String, Long> research : profile.research().entrySet()) {
        if (research.getValue() <= now) {
          unlocked.add(research.getKey());
        }
      }
      if (!unlocked.isEmpty()) {
        for (String recipeId : unlocked) {
          profile.research().remove(recipeId);
          profile.unlocked().add(recipeId);
        }
        changed = true;
      }
    }
    if (changed) {
      save();
    }
  }

  private DiscoveryProfile profile(UUID playerId) {
    return profiles.computeIfAbsent(playerId, key -> new DiscoveryProfile(new HashSet<>(), new HashMap<>()));
  }

  private File file() {
    return new File(plugin.getDataFolder(), "crafting_discovery.yml");
  }

  public void unload(UUID playerId) {
    if (playerId == null) {
      return;
    }
    profiles.remove(playerId);
  }

  public void loadOnlinePlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      profile(player.getUniqueId());
    }
  }
}
