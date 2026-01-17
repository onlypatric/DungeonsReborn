package dev.patric.dungeonsreborn.progression.custom;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CustomXpRepository {
  Optional<CustomXpProfile> load(UUID uuid);

  void save(CustomXpProfile profile);

  void saveAll(Collection<CustomXpProfile> profiles);
}
