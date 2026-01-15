package dev.patric.dungeonsreborn.classes;

import java.util.Optional;
import java.util.UUID;

public interface ClassSelectionRepository {
  Optional<String> load(UUID uuid);

  void save(UUID uuid, String classId, long whenMillis);

  void clear(UUID uuid);
}
