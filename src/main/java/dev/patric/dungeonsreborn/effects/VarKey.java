package dev.patric.dungeonsreborn.effects;

import java.util.Objects;

public record VarKey<T>(String name, Class<T> type) {
  public VarKey {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }

  public static <T> VarKey<T> of(String name, Class<T> type) {
    return new VarKey<>(name, type);
  }
}

