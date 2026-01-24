package dev.patric.dungeonsreborn.effects.mana;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.items.ItemMarkers;

public record ManaSourcesConfig(KillSource kills, PickupSource pickups, TimedSource timed, QuestSource quests) {

  public static ManaSourcesConfig fromConfig(FileConfiguration config) {
    Objects.requireNonNull(config, "config");
    ConfigurationSection sources = config.getConfigurationSection("mana.sources");
    KillSource kills = KillSource.fromConfig(sources == null ? null : sources.getConfigurationSection("kills"));
    PickupSource pickups = PickupSource.fromConfig(sources == null ? null : sources.getConfigurationSection("pickups"));
    TimedSource timed = TimedSource.fromConfig(sources == null ? null : sources.getConfigurationSection("timed"));
    QuestSource quests = QuestSource.fromConfig(sources == null ? null : sources.getConfigurationSection("quests"));
    return new ManaSourcesConfig(kills, pickups, timed, quests);
  }

  public record KillSource(boolean enabled, String resourceId, Mode mode, double multiplier, double min, double max) {
    public enum Mode {
      LOG_MAX_HEALTH,
      LINEAR_MAX_HEALTH
    }

    public static KillSource fromConfig(ConfigurationSection section) {
      if (section == null) {
        return new KillSource(true, ManaProvider.DEFAULT_RESOURCE, Mode.LOG_MAX_HEALTH, 1.0, 0.0, 0.0);
      }
      boolean enabled = section.getBoolean("enabled", true);
      String resource = section.getString("resource", ManaProvider.DEFAULT_RESOURCE);
      String modeRaw = section.getString("mode", "log_max_health");
      Mode mode = "linear_max_health".equalsIgnoreCase(modeRaw) ? Mode.LINEAR_MAX_HEALTH : Mode.LOG_MAX_HEALTH;
      double multiplier = section.getDouble("multiplier", 1.0);
      double min = section.getDouble("min", 0.0);
      double max = section.getDouble("max", 0.0);
      return new KillSource(enabled, resource == null ? ManaProvider.DEFAULT_RESOURCE : resource.trim(), mode, multiplier, min, max);
    }

    public double computeAmount(double maxHealth, java.util.Random rng) {
      if (!Double.isFinite(maxHealth) || maxHealth <= 0.0) {
        return 0.0;
      }
      double base = switch (mode) {
        case LINEAR_MAX_HEALTH -> maxHealth;
        case LOG_MAX_HEALTH -> Math.log(maxHealth);
      };
      if (!Double.isFinite(base) || base <= 0.0) {
        return 0.0;
      }
      double amount = rng.nextDouble() * base * multiplier;
      if (min > 0.0) {
        amount = Math.max(min, amount);
      }
      if (max > 0.0) {
        amount = Math.min(max, amount);
      }
      return amount;
    }
  }

  public record PickupSource(boolean enabled, String resourceId, boolean consume, double defaultAmount,
                             boolean scaleByStack, Map<String, Double> itemIds, Map<Material, Double> materials) {
    public static PickupSource fromConfig(ConfigurationSection section) {
      if (section == null) {
        return new PickupSource(false, ManaProvider.DEFAULT_RESOURCE, true, 0.0, true, Map.of(), Map.of());
      }
      boolean enabled = section.getBoolean("enabled", false);
      String resource = section.getString("resource", ManaProvider.DEFAULT_RESOURCE);
      boolean consume = section.getBoolean("consume", true);
      boolean scale = section.getBoolean("scaleByStack", true);
      double amount = section.getDouble("amount", 0.0);
      Map<String, Double> itemIds = new LinkedHashMap<>();
      ConfigurationSection ids = section.getConfigurationSection("itemIds");
      if (ids != null) {
        for (String key : ids.getKeys(false)) {
          double value = ids.getDouble(key, 0.0);
          if (value > 0.0) {
            itemIds.put(key.trim().toLowerCase(Locale.ROOT), value);
          }
        }
      }
      Map<Material, Double> materials = new LinkedHashMap<>();
      ConfigurationSection mats = section.getConfigurationSection("materials");
      if (mats != null) {
        for (String key : mats.getKeys(false)) {
          Material material = Material.matchMaterial(key);
          if (material == null) {
            continue;
          }
          double value = mats.getDouble(key, 0.0);
          if (value > 0.0) {
            materials.put(material, value);
          }
        }
      }
      return new PickupSource(enabled, resource == null ? ManaProvider.DEFAULT_RESOURCE : resource.trim(), consume,
          amount, scale, Map.copyOf(itemIds), Map.copyOf(materials));
    }

    public double amountFor(ItemStack stack) {
      if (stack == null) {
        return 0.0;
      }
      double amount = 0.0;
      String itemId = ItemMarkers.getItemId(stack);
      if (itemId != null) {
        amount = itemIds.getOrDefault(itemId.trim().toLowerCase(Locale.ROOT), 0.0);
      }
      if (amount <= 0.0) {
        amount = materials.getOrDefault(stack.getType(), 0.0);
      }
      if (amount <= 0.0) {
        amount = defaultAmount;
      }
      if (amount <= 0.0) {
        return 0.0;
      }
      if (scaleByStack) {
        amount *= Math.max(1, stack.getAmount());
      }
      return amount;
    }
  }

  public record TimedSource(boolean enabled, String resourceId, long periodTicks, double amount) {
    public static TimedSource fromConfig(ConfigurationSection section) {
      if (section == null) {
        return new TimedSource(false, ManaProvider.DEFAULT_RESOURCE, 0L, 0.0);
      }
      boolean enabled = section.getBoolean("enabled", false);
      String resource = section.getString("resource", ManaProvider.DEFAULT_RESOURCE);
      long periodTicks = Math.max(0L, section.getLong("periodTicks", 0L));
      double amount = section.getDouble("amount", 0.0);
      return new TimedSource(enabled, resource == null ? ManaProvider.DEFAULT_RESOURCE : resource.trim(), periodTicks, amount);
    }
  }

  public record QuestSource(boolean enabled, String resourceId) {
    public static QuestSource fromConfig(ConfigurationSection section) {
      if (section == null) {
        return new QuestSource(true, ManaProvider.DEFAULT_RESOURCE);
      }
      boolean enabled = section.getBoolean("enabled", true);
      String resource = section.getString("resource", ManaProvider.DEFAULT_RESOURCE);
      return new QuestSource(enabled, resource == null ? ManaProvider.DEFAULT_RESOURCE : resource.trim());
    }
  }
}
