package dev.patric.dungeonsreborn.mobs;

public record MobBroadcastSpec(boolean enabled, String message) {
  public MobBroadcastSpec {
    if (message == null) {
      message = "";
    }
  }
}
