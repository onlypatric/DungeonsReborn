package dev.patric.dungeonsreborn.logging;

public enum ServiceLogCategory {
  GUI("gui"),
  EFFECTS("effects"),
  MOBS("mobs"),
  BINDINGS("bindings"),
  UPGRADES("upgrades"),
  SHOPS("shops"),
  PARTY("party"),
  DUNGEONS("dungeons"),
  LOCALES("locales"),
  ADVANCEMENTS("advancements");

  private final String configKey;

  ServiceLogCategory(String configKey) {
    this.configKey = configKey;
  }

  public String configKey() {
    return configKey;
  }
}
