package dev.patric.dungeonsreborn.effects.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

import dev.patric.dungeonsreborn.effects.Ids;
import dev.patric.dungeonsreborn.effects.items.ItemMarkers;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class EditorItemStore {
  private static final int SCHEMA_VERSION = 1;

  private final ServiceLogger logger;
  private final File itemsDir;

  public EditorItemStore(JavaPlugin plugin, ServiceLogger logger) {
    Objects.requireNonNull(plugin, "plugin");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.itemsDir = new File(plugin.getDataFolder(), "effects/items");
    ensureDir();
  }

  public File itemsDir() {
    return itemsDir;
  }

  public EditorItemDraft create(String id) {
    String normalized = Ids.normalize(id);
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("schemaVersion", SCHEMA_VERSION);
    return new EditorItemDraft(normalized, itemFile(normalized), yaml);
  }

  public Optional<EditorItemDraft> load(String id) {
    String normalized = Ids.normalize(id);
    File file = itemFile(normalized);
    if (!file.exists()) {
      return Optional.empty();
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    return Optional.of(parseDraft(file, yaml, normalized));
  }

  public List<EditorItemDraft> loadAll() {
    ensureDir();
    File[] files = itemsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
        || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
    List<EditorItemDraft> out = new ArrayList<>();
    if (files == null) {
      return out;
    }
    java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
    for (File file : files) {
      String fileId = fileId(file);
      if (fileId == null) {
        continue;
      }
      try {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        out.add(parseDraft(file, yaml, fileId));
      } catch (IllegalArgumentException ex) {
        logger.warn("[Effects][Editor] Skipping item: " + file.getPath() + " (" + ex.getMessage() + ")");
      }
    }
    return out;
  }

  public void save(EditorItemDraft draft) {
    Objects.requireNonNull(draft, "draft");
    ensureDir();
    YamlConfiguration yaml = draft.yaml();
    yaml.set("schemaVersion", SCHEMA_VERSION);
    ItemStack item = draft.item();
    if (item != null && !item.getType().isAir()) {
      ItemMarkers.setItemId(item, draft.id());
      draft.setItem(item);
    }
    try {
      yaml.save(draft.file());
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to save item: " + draft.file().getPath(), ex);
    }
  }

  public boolean delete(String id) {
    String normalized = Ids.normalize(id);
    File file = itemFile(normalized);
    return !file.exists() || file.delete();
  }

  private void ensureDir() {
    if (!itemsDir.exists()) {
      itemsDir.mkdirs();
    }
  }

  private File itemFile(String id) {
    return new File(itemsDir, id + ".yml");
  }

  private String fileId(File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    if (dot <= 0) {
      return null;
    }
    String raw = name.substring(0, dot);
    try {
      return Ids.normalize(raw);
    } catch (Exception ex) {
      logger.warn("[Effects][Editor] Invalid item id: " + raw + " (" + ex.getMessage() + ")");
      return null;
    }
  }

  private EditorItemDraft parseDraft(File file, YamlConfiguration yaml, String id) {
    int schema = yaml.getInt("schemaVersion", SCHEMA_VERSION);
    if (schema != SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported schemaVersion=" + schema + " (expected " + SCHEMA_VERSION + ")");
    }
    return new EditorItemDraft(id, file, yaml);
  }
}
