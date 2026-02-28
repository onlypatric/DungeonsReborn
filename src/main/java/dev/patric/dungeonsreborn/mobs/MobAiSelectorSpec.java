package dev.patric.dungeonsreborn.mobs;

import java.util.Objects;

public final class MobAiSelectorSpec {
  private final String id;
  private final int priority;
  private final MobAiConditionSpec condition;
  private final MobAiIntentSpec intent;

  public MobAiSelectorSpec(String id, int priority, MobAiConditionSpec condition, MobAiIntentSpec intent) {
    this.id = id == null || id.isBlank() ? "selector" : id.trim();
    this.priority = priority;
    this.condition = condition == null ? MobAiConditionSpec.always() : condition;
    this.intent = Objects.requireNonNull(intent, "intent");
  }

  public String id() {
    return id;
  }

  public int priority() {
    return priority;
  }

  public MobAiConditionSpec condition() {
    return condition;
  }

  public MobAiIntentSpec intent() {
    return intent;
  }
}
