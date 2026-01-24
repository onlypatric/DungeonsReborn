package dev.patric.dungeonsreborn.effects.items;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.util.YamlValues;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class HeadRegistry {
  public record HeadSpec(String id, String displayName, String owner, UUID uuid, String profileName, String texture) {
  }

  public record ReloadResult(int heads, List<String> errors) {
  }

  private final JavaPlugin plugin;
  private final Logger logger;
  private final Map<String, HeadSpec> heads = new HashMap<>();

  public HeadRegistry(JavaPlugin plugin, Logger logger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public HeadSpec head(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    return heads.get(Ids.normalize(id));
  }

  public Map<String, HeadSpec> heads() {
    return Collections.unmodifiableMap(heads);
  }

  public ReloadResult reload() {
    List<String> errors = new ArrayList<>();
    File file = new File(plugin.getDataFolder(), "heads.yml");
    if (!file.exists()) {
      plugin.getDataFolder().mkdirs();
      try {
        plugin.saveResource("heads.yml", false);
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (!file.exists()) {
      errors.add("heads.yml: missing");
      heads.clear();
      logErrors(errors);
      return new ReloadResult(0, errors);
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection section = yaml.getConfigurationSection("heads");
    if (section == null) {
      heads.clear();
      return new ReloadResult(0, errors);
    }
    Map<String, HeadSpec> next = new HashMap<>();
    for (String rawId : section.getKeys(false)) {
      String path = "heads." + rawId;
      ConfigurationSection entry = section.getConfigurationSection(rawId);
      if (entry == null) {
        errors.add(path + ": expected section");
        continue;
      }
      String id;
      try {
        id = Ids.normalize(rawId);
      } catch (IllegalArgumentException ex) {
        errors.add(path + ": " + ex.getMessage());
        continue;
      }
      String displayName = YamlValues.string(entry, "name", rawId);
      String owner = YamlValues.string(entry, "owner", null);
      String texture = YamlValues.string(entry, "texture", null);
      String profileName = null;
      UUID uuid = null;
      ConfigurationSection profile = entry.getConfigurationSection("profile");
      if (profile != null) {
        profileName = YamlValues.string(profile, "name", null);
        String uuidRaw = YamlValues.string(profile, "uuid", null);
        if (uuidRaw != null) {
          try {
            uuid = UUID.fromString(uuidRaw);
          } catch (IllegalArgumentException ex) {
            errors.add(path + ".profile.uuid: invalid uuid=" + uuidRaw);
          }
        }
        String textureOverride = YamlValues.string(profile, "texture", null);
        if (textureOverride != null) {
          texture = textureOverride;
        }
      }
      next.put(id, new HeadSpec(id, displayName, owner, uuid, profileName, texture));
    }
    heads.clear();
    heads.putAll(next);
    logErrors(errors);
    return new ReloadResult(heads.size(), errors);
  }

  private void logErrors(List<String> errors) {
    if (errors.isEmpty()) {
      return;
    }
    logger.warning("[Heads] reload had " + errors.size() + " errors");
    for (String error : errors) {
      logger.warning("[Heads] " + error);
    }
  }

  public static boolean applyTo(SkullMeta skull, HeadSpec spec, List<String> errors) {
    if (skull == null || spec == null) {
      return false;
    }
    if (spec.texture() != null && !spec.texture().isBlank()) {
      return applyTexture(skull, spec.uuid(), spec.profileName(), spec.texture(), errors);
    }
    if (spec.owner() != null && !spec.owner().isBlank()) {
      skull.setOwningPlayer(Bukkit.getOfflinePlayer(spec.owner()));
      return true;
    }
    if ((spec.profileName() != null && !spec.profileName().isBlank()) || spec.uuid() != null) {
      PlayerProfile profile = spec.uuid() == null
          ? Bukkit.createProfile(spec.profileName())
          : Bukkit.createProfile(spec.uuid(), spec.profileName());
      skull.setPlayerProfile(profile);
      return true;
    }
    return false;
  }

  public static boolean applyTexture(SkullMeta skull, UUID uuid, String name, String texture, List<String> errors) {
    if (skull == null) {
      return false;
    }
    if (texture == null || texture.isBlank()) {
      return false;
    }
    PlayerProfile profile = uuid == null ? Bukkit.createProfile(name) : Bukkit.createProfile(uuid, name);
    try {
      profile.setProperty(new ProfileProperty("textures", texture));
    } catch (Throwable ex) {
      if (errors != null) {
        errors.add("skull.texture: failed to set texture");
      }
      return false;
    }
    skull.setPlayerProfile(profile);
    return true;
  }
}
