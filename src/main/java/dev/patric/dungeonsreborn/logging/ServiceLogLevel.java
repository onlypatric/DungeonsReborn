package dev.patric.dungeonsreborn.logging;

import java.util.Locale;

public enum ServiceLogLevel {
  DEBUG,
  INFO,
  WARNING,
  ERROR;

  public boolean allows(ServiceLogLevel messageLevel) {
    return messageLevel.ordinal() >= this.ordinal();
  }

  public static ServiceLogLevel parse(String raw, ServiceLogLevel def) {
    if (raw == null || raw.isBlank()) {
      return def;
    }
    String value = raw.trim().toUpperCase(Locale.ROOT);
    return switch (value) {
      case "DEBUG" -> DEBUG;
      case "INFO" -> INFO;
      case "WARN", "WARNING" -> WARNING;
      case "ERROR" -> ERROR;
      default -> def;
    };
  }
}
