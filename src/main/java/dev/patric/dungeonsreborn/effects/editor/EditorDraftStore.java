package dev.patric.dungeonsreborn.effects.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.patric.dungeonsreborn.effects.Ids;

public final class EditorDraftStore {
  public static final int EDITOR_SCHEMA_VERSION = 1;
  public static final int ENGINE_SCHEMA_VERSION = 1;
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_INSTANT;

  private final JavaPlugin plugin;
  private final File draftsDir;
  private final File abilitiesDir;
  private final File scriptsRoot;
  private final File draftScriptsDir;

  public EditorDraftStore(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.draftsDir = new File(plugin.getDataFolder(), "effects/drafts");
    this.abilitiesDir = new File(draftsDir, "abilities");
    this.scriptsRoot = new File(plugin.getDataFolder(), "effects/scripts");
    this.draftScriptsDir = new File(scriptsRoot, "drafts");
    ensureDirs();
  }

  public File draftsDir() {
    return draftsDir;
  }

  public File abilitiesDir() {
    return abilitiesDir;
  }

  public File draftScriptsDir() {
    return draftScriptsDir;
  }

  public EditorAbilityDraft create(String id) {
    String normalized = Ids.normalize(id);
    YamlConfiguration yaml = new YamlConfiguration();
    EditorAbilityDraft draft = new EditorAbilityDraft(normalized, yaml);
    Instant now = Instant.now();
    draft.setEditorSchemaVersion(EDITOR_SCHEMA_VERSION);
    draft.setCreatedAt(now);
    draft.setUpdatedAt(now);
    draft.abilitySection();
    return draft;
  }

  public Optional<EditorAbilityDraft> load(String id) {
    String normalized = Ids.normalize(id);
    File file = draftFile(normalized);
    if (!file.exists()) {
      return Optional.empty();
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    return Optional.of(parseDraft(file, yaml, normalized));
  }

  public List<EditorAbilityDraft> loadAll() {
    ensureDirs();
    File[] files = abilitiesDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
        || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    List<EditorAbilityDraft> out = new ArrayList<>();
    if (files == null) {
      return out;
    }
    java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
    for (File file : files) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      String fileId = fileId(file);
      if (fileId == null) {
        continue;
      }
      try {
        out.add(parseDraft(file, yaml, fileId));
      } catch (IllegalArgumentException ex) {
        plugin.getLogger().warning("[Effects][Editor] Skipping draft: " + file.getPath() + " (" + ex.getMessage() + ")");
      }
    }
    return out;
  }

  public void save(EditorAbilityDraft draft) {
    Objects.requireNonNull(draft, "draft");
    ensureDirs();
    YamlConfiguration yaml = draft.yaml();

    yaml.set("schemaVersion", ENGINE_SCHEMA_VERSION);
    yaml.set("editor.schemaVersion", EDITOR_SCHEMA_VERSION);
    yaml.set("editor.id", draft.id());

    Instant now = Instant.now();
    if (draft.createdAt() == null) {
      draft.setCreatedAt(now);
    }
    draft.setUpdatedAt(now);
    yaml.set("editor.createdAt", TIME_FORMAT.format(draft.createdAt()));
    yaml.set("editor.updatedAt", TIME_FORMAT.format(draft.updatedAt()));

    ConfigurationSection ability = draft.abilitySection();
    applyScript(draft, ability);

    File file = draftFile(draft.id());
    try {
      yaml.save(file);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to save draft: " + file.getPath(), ex);
    }

    if (draft.scriptMode() == EditorAbilityDraft.ScriptMode.FILE) {
      writeScriptFile(draft.scriptFile(), draft.scriptSource());
    }
  }

  public boolean delete(String id) {
    String normalized = Ids.normalize(id);
    boolean removed = false;
    File file = draftFile(normalized);
    if (file.exists()) {
      removed = file.delete();
    }
    File scriptFile = resolveScriptFile("drafts/" + normalized + ".es");
    if (scriptFile.exists()) {
      removed = scriptFile.delete() || removed;
    }
    return removed;
  }

  private void applyScript(EditorAbilityDraft draft, ConfigurationSection ability) {
    ability.set("script", null);
    if (draft.scriptMode() == EditorAbilityDraft.ScriptMode.NONE) {
      if (draft.scriptFile() != null) {
        File file = resolveScriptFile(draft.scriptFile());
        if (file.exists()) {
          file.delete();
        }
      }
      return;
    }

    ConfigurationSection script = ability.createSection("script");
    script.set("language", "dsl-v1");
    if (draft.scriptMode() == EditorAbilityDraft.ScriptMode.INLINE) {
      script.set("source", draft.scriptSource());
      return;
    }

    String filePath = draft.scriptFile();
    if (filePath == null || filePath.isBlank()) {
      filePath = "drafts/" + draft.id() + ".es";
      draft.setScriptFile(filePath);
    }
    script.set("file", filePath);
    script.set("version", 1);
  }

  private void writeScriptFile(String relativePath, String source) {
    if (relativePath == null || source == null) {
      return;
    }
    File file = resolveScriptFile(relativePath);
    file.getParentFile().mkdirs();
    try {
      Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to save draft script: " + file.getPath(), ex);
    }
  }

  private EditorAbilityDraft parseDraft(File file, YamlConfiguration yaml, String fileId) {
    int editorVersion = yaml.getInt("editor.schemaVersion", 1);
    if (editorVersion > EDITOR_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported editor.schemaVersion=" + editorVersion + " (expected <= " + EDITOR_SCHEMA_VERSION + ")");
    }
    if (editorVersion < EDITOR_SCHEMA_VERSION) {
      editorVersion = migrateDraft(yaml, editorVersion);
    }

    ConfigurationSection abilities = yaml.getConfigurationSection("abilities");
    if (abilities == null) {
      throw new IllegalArgumentException("Missing abilities section");
    }

    String abilityId = null;
    if (abilities.isConfigurationSection(fileId)) {
      abilityId = fileId;
    } else if (abilities.getKeys(false).size() == 1) {
      abilityId = abilities.getKeys(false).iterator().next();
    }
    if (abilityId == null) {
      throw new IllegalArgumentException("Draft must contain exactly one ability section");
    }

    EditorAbilityDraft draft = new EditorAbilityDraft(abilityId, yaml);
    draft.setEditorSchemaVersion(editorVersion);
    draft.setCreatedAt(parseInstant(yaml.getString("editor.createdAt")));
    draft.setUpdatedAt(parseInstant(yaml.getString("editor.updatedAt")));

    ConfigurationSection ability = abilities.getConfigurationSection(abilityId);
    if (ability == null) {
      ability = abilities.createSection(abilityId);
    }
    parseScript(draft, ability);
    return draft;
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

  private int migrateDraft(YamlConfiguration yaml, int version) {
    int current = version;
    while (current < EDITOR_SCHEMA_VERSION) {
      if (current == 1) {
        break;
      }
      current++;
    }
    yaml.set("editor.schemaVersion", EDITOR_SCHEMA_VERSION);
    return EDITOR_SCHEMA_VERSION;
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception ex) {
      return null;
    }
  }

  private String readScriptFile(String relativePath) {
    File file = resolveScriptFile(relativePath);
    if (!file.exists()) {
      return null;
    }
    try {
      return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read draft script: " + file.getPath(), ex);
    }
  }

  private File resolveScriptFile(String relativePath) {
    Path root = scriptsRoot.toPath().toAbsolutePath().normalize();
    Path resolved = root.resolve(relativePath).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Script path escapes scripts dir: " + relativePath);
    }
    return resolved.toFile();
  }

  private File draftFile(String id) {
    return new File(abilitiesDir, id + ".yml");
  }

  private String fileId(File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    if (dot <= 0) {
      return null;
    }
    return Ids.normalize(name.substring(0, dot));
  }

  private void ensureDirs() {
    abilitiesDir.mkdirs();
    scriptsRoot.mkdirs();
    draftScriptsDir.mkdirs();
  }
}
