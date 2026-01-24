package dev.patric.dungeonsreborn.mobs;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

public record MobDropConditions(
    Integer minMobLevel,
    Integer maxMobLevel,
    Set<String> biomes,
    Integer minTime,
    Integer maxTime,
    Double minLuck,
    Double maxLuck) {

  public MobDropConditions {
    if (biomes == null) {
      biomes = Set.of();
    } else {
      biomes = Set.copyOf(biomes);
    }
  }

  public boolean matches(MobSpec spec, Location location, Player killer) {
    if (spec != null) {
      int level = spec.minXpLevel();
      if (minMobLevel != null && level < minMobLevel) {
        return false;
      }
      if (maxMobLevel != null && level > maxMobLevel) {
        return false;
      }
    }
    if (location != null) {
      if (!biomes.isEmpty()) {
        Biome biome = location.getBlock().getBiome();
        String key = normalizeBiome(biome);
        if (!biomes.contains(key)) {
          return false;
        }
      }
      if (minTime != null || maxTime != null) {
        long time = location.getWorld() == null ? 0L : location.getWorld().getTime();
        int t = (int) (time % 24000L);
        int min = minTime == null ? 0 : normalizeTime(minTime);
        int max = maxTime == null ? 23999 : normalizeTime(maxTime);
        if (min <= max) {
          if (t < min || t > max) {
            return false;
          }
        } else {
          if (t > max && t < min) {
            return false;
          }
        }
      }
    }
    if (minLuck != null || maxLuck != null) {
      double luck = resolveLuck(killer);
      if (minLuck != null && luck < minLuck) {
        return false;
      }
      if (maxLuck != null && luck > maxLuck) {
        return false;
      }
    }
    return true;
  }

  private static int normalizeTime(int value) {
    if (value < 0) {
      return 0;
    }
    if (value > 23999) {
      return value % 24000;
    }
    return value;
  }

  private static String normalizeBiome(Biome biome) {
    if (biome == null) {
      return "unknown";
    }
    try {
      NamespacedKey key = biome.getKey();
      if (key != null && key.getKey() != null) {
        return key.getKey().toLowerCase(Locale.ROOT);
      }
    } catch (Throwable ignored) {
    }
    try {
      NamespacedKey key = RegistryAccess.registryAccess()
          .getRegistry(RegistryKey.BIOME)
          .getKey(biome);
      if (key != null && key.getKey() != null) {
        return key.getKey().toLowerCase(Locale.ROOT);
      }
    } catch (Throwable ignored) {
    }
    return "unknown";
  }

  private static double resolveLuck(Player player) {
    if (player == null) {
      return 0.0;
    }
    AttributeInstance luck = player.getAttribute(Attribute.LUCK);
    if (luck == null) {
      return 0.0;
    }
    return luck.getValue();
  }

  public static MobDropConditions none() {
    return new MobDropConditions(null, null, Set.of(), null, null, null, null);
  }

  public boolean isEmpty() {
    return minMobLevel == null
        && maxMobLevel == null
        && (biomes == null || biomes.isEmpty())
        && minTime == null
        && maxTime == null
        && minLuck == null
        && maxLuck == null;
  }
}
