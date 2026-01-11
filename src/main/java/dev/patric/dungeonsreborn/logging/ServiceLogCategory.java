package dev.patric.dungeonsreborn.logging;

public enum ServiceLogCategory {
  GUI("gui"),
  EFFECTS("effects"),
  MOBS("mobs"),
  BINDINGS("bindings");

  private final String configKey;

  ServiceLogCategory(String configKey) {
    this.configKey = configKey;
  }

  public String configKey() {
    return configKey;
  }
}
