package dev.patric.dungeonsreborn.effects.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;

public final class EditorAbilityImporter {
  private final EffectsYamlAbilities yaml;
  private final EditorDraftStore drafts;

  public EditorAbilityImporter(EffectsYamlAbilities yaml, EditorDraftStore drafts) {
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.drafts = Objects.requireNonNull(drafts, "drafts");
  }

  public Optional<EditorAbilityDraft> importAbility(String abilityId) {
    String normalized = Ids.normalize(abilityId);
    Optional<ConfigurationSection> section = findAbilitySection(normalized);
    if (section.isEmpty()) {
      return Optional.empty();
    }
    EditorAbilityDraft draft = buildDraft(normalized, section.get());
    drafts.save(draft);
    return Optional.of(draft);
  }

  private Optional<ConfigurationSection> findAbilitySection(String normalizedId) {
    YamlConfiguration main = YamlConfiguration.loadConfiguration(yaml.file());
    Optional<ConfigurationSection> fromMain = findAbilityInConfig(main, normalizedId);
    if (fromMain.isPresent()) {
      return fromMain;
    }
    File dir = yaml.abilitiesDir();
    File[] extra = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    if (extra == null) {
      return Optional.empty();
    }
    java.util.Arrays.sort(extra, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
    for (File file : extra) {
      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
      Optional<ConfigurationSection> found = findAbilityInConfig(cfg, normalizedId);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private Optional<ConfigurationSection> findAbilityInConfig(YamlConfiguration cfg, String normalizedId) {
    ConfigurationSection abilities = cfg.getConfigurationSection("abilities");
    if (abilities == null) {
      return Optional.empty();
    }
    for (String key : abilities.getKeys(false)) {
      ConfigurationSection section = abilities.getConfigurationSection(key);
      if (section == null) {
        continue;
      }
      try {
        String normalized = Ids.normalize(key);
        if (normalized.equals(normalizedId)) {
          return Optional.of(section);
        }
      } catch (IllegalArgumentException ignored) {
      }
    }
    return Optional.empty();
  }

  private EditorAbilityDraft buildDraft(String id, ConfigurationSection source) {
    YamlConfiguration yamlDraft = new YamlConfiguration();
    ConfigurationSection abilities = yamlDraft.createSection("abilities");
    ConfigurationSection dest = abilities.createSection(id);
    copySection(source, dest);

    EditorAbilityDraft draft = new EditorAbilityDraft(id, yamlDraft);
    Instant now = Instant.now();
    draft.setEditorSchemaVersion(EditorDraftStore.EDITOR_SCHEMA_VERSION);
    draft.setCreatedAt(now);
    draft.setUpdatedAt(now);
    parseScript(draft, dest);
    return draft;
  }

  private void copySection(ConfigurationSection source, ConfigurationSection target) {
    for (String key : source.getKeys(false)) {
      Object value = source.get(key);
      if (value instanceof ConfigurationSection sub) {
        ConfigurationSection child = target.createSection(key);
        copySection(sub, child);
      } else {
        target.set(key, value);
      }
    }
  }

  private void parseScript(EditorAbilityDraft draft, ConfigurationSection ability) {
    Object raw = ability.get("script");
    if (raw == null) {
      draft.clearScript();
      return;
    }
    if (raw instanceof String inline) {
      draft.setInlineScript(inline);
      return;
    }
    if (raw instanceof ConfigurationSection sec) {
      String source = sec.getString("source");
      if (source != null) {
        draft.setInlineScript(source);
        return;
      }
      String file = sec.getString("file");
      if (file != null) {
        draft.setScriptMode(EditorAbilityDraft.ScriptMode.FILE);
        draft.setScriptFile(file);
        draft.setScriptSource(readScriptFile(file));
        return;
      }
    }
    draft.clearScript();
  }

  private String readScriptFile(String relativePath) {
    File scriptsRoot = new File(drafts.draftsDir().getParentFile(), "scripts");
    Path root = scriptsRoot.toPath().toAbsolutePath().normalize();
    Path resolved = root.resolve(relativePath).normalize();
    if (!resolved.startsWith(root)) {
      return null;
    }
    File file = resolved.toFile();
    if (!file.exists()) {
      return null;
    }
    try {
      return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return null;
    }
  }
}
