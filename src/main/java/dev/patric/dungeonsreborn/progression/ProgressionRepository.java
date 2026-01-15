package dev.patric.dungeonsreborn.progression;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProgressionRepository {
  Optional<PlayerProgression> load(UUID uuid);

  void save(PlayerProgression progression);

  void saveAll(Collection<PlayerProgression> progressions);
}
