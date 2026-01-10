package dev.patric.dungeonsreborn.effects.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TypeRegistry<T extends NodeType> {
  private final String kind;
  private final Map<String, T> byId = new LinkedHashMap<>();

  public TypeRegistry(String kind) {
    this.kind = Objects.requireNonNull(kind, "kind");
  }

  public void register(T type) {
    Objects.requireNonNull(type, "type");
    String id = normalizeId(type.id());
    if (byId.containsKey(id)) {
      throw new IllegalArgumentException("Duplicate " + kind + " type id: " + id);
    }
    byId.put(id, type);
  }

  public T get(String id) {
    Objects.requireNonNull(id, "id");
    return byId.get(normalizeId(id));
  }

  public boolean has(String id) {
    Objects.requireNonNull(id, "id");
    return byId.containsKey(normalizeId(id));
  }

  public Set<String> ids() {
    return Collections.unmodifiableSet(byId.keySet());
  }

  private static String normalizeId(String id) {
    return dev.patric.dungeonsreborn.effects.Ids.normalize(id);
  }
}
