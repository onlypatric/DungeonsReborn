package dev.patric.dungeonsreborn.effects.editor;

import java.time.Instant;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EditorAbilityDraft {
  public enum ScriptMode {
    NONE,
    INLINE,
    FILE
  }

  private final String id;
  private final YamlConfiguration yaml;
  private EditorAbilityState state;
  private ScriptMode scriptMode;
  private String scriptSource;
  private String scriptFile;
  private Instant createdAt;
  private Instant updatedAt;
  private int editorSchemaVersion;

  EditorAbilityDraft(String id, YamlConfiguration yaml) {
    this.id = Objects.requireNonNull(id, "id");
    this.yaml = Objects.requireNonNull(yaml, "yaml");
    this.state = EditorAbilityState.DRAFT;
    this.scriptMode = ScriptMode.NONE;
  }

  public String id() {
    return id;
  }

  public YamlConfiguration yaml() {
    return yaml;
  }

  public EditorAbilityState state() {
    return state;
  }

  public void setState(EditorAbilityState state) {
    this.state = Objects.requireNonNull(state, "state");
  }

  public ScriptMode scriptMode() {
    return scriptMode;
  }

  public String scriptSource() {
    return scriptSource;
  }

  public String scriptFile() {
    return scriptFile;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public int editorSchemaVersion() {
    return editorSchemaVersion;
  }

  public void setInlineScript(String source) {
    scriptMode = ScriptMode.INLINE;
    scriptSource = Objects.requireNonNull(source, "source");
    scriptFile = null;
  }

  public void setFileScript(String relativePath, String source) {
    scriptMode = ScriptMode.FILE;
    scriptFile = Objects.requireNonNull(relativePath, "relativePath");
    scriptSource = Objects.requireNonNull(source, "source");
  }

  public void clearScript() {
    scriptMode = ScriptMode.NONE;
    scriptSource = null;
    scriptFile = null;
  }

  public ConfigurationSection abilitySection() {
    ConfigurationSection abilities = yaml.getConfigurationSection("abilities");
    if (abilities == null) {
      abilities = yaml.createSection("abilities");
    }
    ConfigurationSection ability = abilities.getConfigurationSection(id);
    if (ability == null) {
      ability = abilities.createSection(id);
    }
    return ability;
  }

  void setScriptMode(ScriptMode scriptMode) {
    this.scriptMode = Objects.requireNonNull(scriptMode, "scriptMode");
  }

  void setScriptSource(String scriptSource) {
    this.scriptSource = scriptSource;
  }

  void setScriptFile(String scriptFile) {
    this.scriptFile = scriptFile;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  void setEditorSchemaVersion(int editorSchemaVersion) {
    this.editorSchemaVersion = editorSchemaVersion;
  }
}
