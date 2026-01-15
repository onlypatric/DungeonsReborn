package dev.patric.dungeonsreborn.logging;

public enum ServiceLogCategory {
  GUI("gui"),
  EFFECTS("effects"),
  MOBS("mobs"),
  BINDINGS("bindings"),
  UPGRADES("upgrades"),
  SHOPS("shops"),
  DUNGEONS("dungeons"),
  LOCALES("locales");

  private final String configKey;

  ServiceLogCategory(String configKey) {
    this.configKey = configKey;
  }

  public String configKey() {
    return configKey;
  }
}
