package dev.patric.dungeonsreborn.classes;

import java.util.Optional;
import java.util.UUID;

public interface ClassSelectionRepository {
  record Selection(String classId, long lastUpdateMillis) {
  }

  Optional<String> load(UUID uuid);

  Optional<Selection> loadSelection(UUID uuid);

  void save(UUID uuid, String classId, long whenMillis);

  void recordHistory(UUID uuid, String fromClassId, String toClassId, long whenMillis, String reason);

  void clear(UUID uuid);
}
