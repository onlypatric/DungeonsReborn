package dev.patric.dungeonsreborn.mobs.ai;

public record MobAiHooksSpec(
    String onEnterIdle,
    String onEnterEngage,
    String onEnterRetreat,
    String onEnterRage) {

  public MobAiHooksSpec {
    onEnterIdle = sanitize(onEnterIdle);
    onEnterEngage = sanitize(onEnterEngage);
    onEnterRetreat = sanitize(onEnterRetreat);
    onEnterRage = sanitize(onEnterRage);
  }

  public static MobAiHooksSpec empty() {
    return new MobAiHooksSpec(null, null, null, null);
  }

  public boolean isEmpty() {
    return onEnterIdle == null && onEnterEngage == null && onEnterRetreat == null && onEnterRage == null;
  }

  public String forState(String stateKey) {
    if (stateKey == null || stateKey.isBlank()) {
      return null;
    }
    return switch (stateKey.trim().toUpperCase(java.util.Locale.ROOT)) {
      case "IDLE" -> onEnterIdle;
      case "ENGAGE" -> onEnterEngage;
      case "RETREAT" -> onEnterRetreat;
      case "RAGE" -> onEnterRage;
      default -> null;
    };
  }

  private static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
