package dev.patric.dungeonsreborn.effects.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EffectsCombatMigrator {
  private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
  private static final Map<String, String> LEGACY_EVENT_ALIASES = Map.of(
      "ON_HIT", "ON_ATTACK_HIT",
      "ON_KILL", "ON_ATTACK_KILL");
  private static final Map<String, String> LEGACY_TRIGGER_TYPES = Map.of(
      "on_hit", "ON_ATTACK_HIT",
      "on_kill", "ON_ATTACK_KILL",
      "on_dodge", "ON_DODGE",
      "on_sprint", "ON_SPRINT");
  private static final Map<String, String> LEGACY_DAMAGE_KEYS = Map.ofEntries(
      Map.entry("armor_pen_flat", "armorPenFlat"),
      Map.entry("armor_pen_pct", "armorPenPct"),
      Map.entry("resist_pen_pct", "resistPenPct"),
      Map.entry("vulnerability_tag", "vulnerabilityTag"),
      Map.entry("crit_chance", "critChance"),
      Map.entry("crit_multiplier", "critMultiplier"),
      Map.entry("min_damage_floor", "minDamageFloor"),
      Map.entry("mitigation_profile", "mitigationProfile"),
      Map.entry("pipeline_tags", "pipelineTags"),
      Map.entry("snapshot_at_cast", "snapshotAtCast"));

  private EffectsCombatMigrator() {
  }

  public static MigrationReport migrate(File effectsFile, File abilitiesDir, boolean createBackups) {
    Objects.requireNonNull(effectsFile, "effectsFile");
    Objects.requireNonNull(abilitiesDir, "abilitiesDir");

    List<File> files = new ArrayList<>();
    if (effectsFile.isFile()) {
      files.add(effectsFile);
    }
    if (abilitiesDir.isDirectory()) {
      try (var stream = Files.walk(abilitiesDir.toPath())) {
        stream.filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
            .map(java.nio.file.Path::toFile)
            .forEach(files::add);
      } catch (IOException ignored) {
      }
    }

    int filesChanged = 0;
    int nodesChanged = 0;
    int unresolved = 0;
    List<String> details = new ArrayList<>();

    for (File file : files) {
      FileResult result = migrateFile(file, createBackups);
      if (result.changedNodes > 0) {
        filesChanged++;
        nodesChanged += result.changedNodes;
      }
      unresolved += result.unresolvedNodes;
      details.addAll(result.details);
    }
    return new MigrationReport(files.size(), filesChanged, nodesChanged, unresolved, List.copyOf(details));
  }

  private static FileResult migrateFile(File file, boolean createBackups) {
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection abilities = yaml.getConfigurationSection("abilities");
    if (abilities == null || abilities.getKeys(false).isEmpty()) {
      return FileResult.NONE;
    }

    int changedNodes = 0;
    int unresolvedNodes = 0;
    List<String> details = new ArrayList<>();

    for (String abilityId : abilities.getKeys(false)) {
      ConfigurationSection ability = abilities.getConfigurationSection(abilityId);
      if (ability == null) {
        continue;
      }
      String base = "abilities." + abilityId;
      TriggerResult triggers = migrateTriggers(ability, base);
      changedNodes += triggers.changed;
      unresolvedNodes += triggers.unresolved;
      details.addAll(triggers.details);
      RewriteResult actionRewrite = rewriteObject(ability.get("action"), base + ".action");
      if (actionRewrite.changed > 0) {
        ability.set("action", actionRewrite.value);
        changedNodes += actionRewrite.changed;
        details.addAll(actionRewrite.details);
      }
      unresolvedNodes += actionRewrite.unresolved;
    }

    if (changedNodes > 0) {
      if (createBackups) {
        createBackup(file, details);
      }
      try {
        yaml.save(file);
      } catch (IOException ex) {
        details.add(file.getPath() + ": failed to save migrated file (" + ex.getMessage() + ")");
      }
    }
    return new FileResult(changedNodes, unresolvedNodes, details);
  }

  private static TriggerResult migrateTriggers(ConfigurationSection ability, String base) {
    List<Map<?, ?>> input = ability.getMapList("triggers");
    if (input.isEmpty()) {
      return TriggerResult.NONE;
    }
    int changed = 0;
    int unresolved = 0;
    List<String> details = new ArrayList<>();
    List<Map<String, Object>> output = new ArrayList<>(input.size());

    for (int i = 0; i < input.size(); i++) {
      Map<?, ?> raw = input.get(i);
      Map<String, Object> trigger = new LinkedHashMap<>();
      for (var e : raw.entrySet()) {
        if (e.getKey() != null) {
          trigger.put(String.valueOf(e.getKey()), e.getValue());
        }
      }
      String path = base + ".triggers[" + i + "]";
      String type = string(trigger.get("type"));
      String event = string(trigger.get("event"));

      if ((type == null || type.isBlank()) && event != null && !event.isBlank()) {
        trigger.put("type", "event");
        changed++;
        details.add(path + ": set type=event");
        type = "event";
      }

      if (type != null) {
        String mappedEvent = LEGACY_TRIGGER_TYPES.get(type.toLowerCase(Locale.ROOT));
        if (mappedEvent != null) {
          trigger.put("type", "event");
          trigger.put("event", mappedEvent);
          changed++;
          details.add(path + ": migrated legacy trigger type " + type + " -> type=event,event=" + mappedEvent);
          type = "event";
          event = mappedEvent;
        }
      }

      if ("event".equalsIgnoreCase(type) && event != null) {
        String normalized = event.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        String migrated = LEGACY_EVENT_ALIASES.get(normalized);
        if (migrated != null) {
          trigger.put("event", migrated);
          changed++;
          details.add(path + ": migrated event " + event + " -> " + migrated);
        } else if ("ON_SPRINT".equals(normalized)) {
          unresolved++;
          details.add(path + ": unresolved legacy event ON_SPRINT (manual decision required)");
        }
      }

      Object cooldownTicks = trigger.remove("cooldownTicks");
      Object cooldownScope = trigger.remove("cooldownScope");
      if (cooldownTicks != null || cooldownScope != null) {
        Map<String, Object> cooldown = new LinkedHashMap<>();
        Object existing = trigger.get("cooldown");
        if (existing instanceof Map<?, ?> m) {
          for (var e : m.entrySet()) {
            if (e.getKey() != null) {
              cooldown.put(String.valueOf(e.getKey()), e.getValue());
            }
          }
        }
        if (cooldownTicks != null && !cooldown.containsKey("ticks")) {
          cooldown.put("ticks", cooldownTicks);
        }
        if (cooldownScope != null && !cooldown.containsKey("scope")) {
          cooldown.put("scope", cooldownScope);
        }
        trigger.put("cooldown", cooldown);
        changed++;
        details.add(path + ": migrated cooldownTicks/cooldownScope into cooldown.*");
      }
      output.add(trigger);
    }

    if (changed > 0) {
      ability.set("triggers", output);
    }
    return new TriggerResult(changed, unresolved, details);
  }

  private static RewriteResult rewriteObject(Object value, String path) {
    if (value == null) {
      return RewriteResult.NONE;
    }
    if (value instanceof ConfigurationSection section) {
      return rewriteObject(section.getValues(false), path);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      int changed = 0;
      int unresolved = 0;
      List<String> details = new ArrayList<>();
      for (var entry : map.entrySet()) {
        if (entry.getKey() == null) {
          continue;
        }
        String key = String.valueOf(entry.getKey());
        String migratedKey = LEGACY_DAMAGE_KEYS.getOrDefault(key, key);
        if (!migratedKey.equals(key)) {
          changed++;
          details.add(path + "." + key + " -> " + migratedKey);
        }
        RewriteResult child = rewriteObject(entry.getValue(), path + "." + migratedKey);
        out.put(migratedKey, child.value);
        changed += child.changed;
        unresolved += child.unresolved;
        details.addAll(child.details);
      }
      return new RewriteResult(out, changed, unresolved, details);
    }
    if (value instanceof List<?> list) {
      ArrayList<Object> out = new ArrayList<>(list.size());
      int changed = 0;
      int unresolved = 0;
      List<String> details = new ArrayList<>();
      for (int i = 0; i < list.size(); i++) {
        RewriteResult child = rewriteObject(list.get(i), path + "[" + i + "]");
        out.add(child.value);
        changed += child.changed;
        unresolved += child.unresolved;
        details.addAll(child.details);
      }
      return new RewriteResult(out, changed, unresolved, details);
    }
    return new RewriteResult(value, 0, 0, List.of());
  }

  private static void createBackup(File file, List<String> details) {
    String stamp = LocalDateTime.now().format(BACKUP_TS);
    File backup = new File(file.getParentFile(), file.getName() + ".combat-migrate." + stamp + ".bak");
    try {
      Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      details.add(file.getPath() + ": backup written -> " + backup.getName());
    } catch (IOException ex) {
      details.add(file.getPath() + ": backup failed (" + ex.getMessage() + ")");
    }
  }

  private static String string(Object value) {
    if (value == null) {
      return null;
    }
    String out = String.valueOf(value);
    return out.isBlank() ? null : out;
  }

  public record MigrationReport(
      int filesScanned,
      int filesChanged,
      int nodesChanged,
      int unresolvedNodes,
      List<String> details) {
  }

  private record FileResult(int changedNodes, int unresolvedNodes, List<String> details) {
    private static final FileResult NONE = new FileResult(0, 0, List.of());
  }

  private record TriggerResult(int changed, int unresolved, List<String> details) {
    private static final TriggerResult NONE = new TriggerResult(0, 0, List.of());
  }

  private record RewriteResult(Object value, int changed, int unresolved, List<String> details) {
    private static final RewriteResult NONE = new RewriteResult(null, 0, 0, List.of());
  }
}
