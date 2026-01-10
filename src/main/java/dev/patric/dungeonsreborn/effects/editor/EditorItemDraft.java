package dev.patric.dungeonsreborn.effects.editor;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class EditorItemDraft {
  private final String id;
  private final File file;
  private final YamlConfiguration yaml;

  EditorItemDraft(String id, File file, YamlConfiguration yaml) {
    this.id = Objects.requireNonNull(id, "id");
    this.file = Objects.requireNonNull(file, "file");
    this.yaml = Objects.requireNonNull(yaml, "yaml");
  }

  public String id() {
    return id;
  }

  public File file() {
    return file;
  }

  public YamlConfiguration yaml() {
    return yaml;
  }

  public ItemStack item() {
    ItemStack item = yaml.getItemStack("item");
    return item == null ? null : item.clone();
  }

  public void setItem(ItemStack item) {
    yaml.set("item", item == null ? null : item.clone());
  }

  public List<Map<String, Object>> bindings() {
    return EditorItemYaml.bindings(yaml);
  }

  public void setBindings(List<Map<String, Object>> bindings) {
    yaml.set("bindings", bindings == null || bindings.isEmpty() ? null : bindings);
  }
}
