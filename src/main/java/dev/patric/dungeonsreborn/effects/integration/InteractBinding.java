package dev.patric.dungeonsreborn.effects.integration;

import java.util.Objects;

public record InteractBinding(
    String id,
    InteractTrigger trigger,
    String abilityId,
    ItemMatcher itemMatcher,
    boolean requireSneaking,
    String requiredPermission,
    boolean cancelEvent) {

  public InteractBinding {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(trigger, "trigger");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(itemMatcher, "itemMatcher");
  }

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public static final class Builder {
    private final String id;
    private InteractTrigger trigger = InteractTrigger.RIGHT_CLICK;
    private String abilityId;
    private ItemMatcher itemMatcher = ItemMatchers.anyNonAir();
    private boolean requireSneaking;
    private String requiredPermission;
    private boolean cancelEvent = true;

    private Builder(String id) {
      this.id = Objects.requireNonNull(id, "id");
    }

    public Builder trigger(InteractTrigger trigger) {
      this.trigger = Objects.requireNonNull(trigger, "trigger");
      return this;
    }

    public Builder ability(String abilityId) {
      this.abilityId = Objects.requireNonNull(abilityId, "abilityId");
      return this;
    }

    public Builder item(ItemMatcher matcher) {
      this.itemMatcher = Objects.requireNonNull(matcher, "matcher");
      return this;
    }

    public Builder requireSneaking(boolean requireSneaking) {
      this.requireSneaking = requireSneaking;
      return this;
    }

    public Builder permission(String permission) {
      this.requiredPermission = permission;
      return this;
    }

    public Builder cancelEvent(boolean cancelEvent) {
      this.cancelEvent = cancelEvent;
      return this;
    }

    public InteractBinding build() {
      if (abilityId == null || abilityId.isBlank()) {
        throw new IllegalStateException("abilityId not set");
      }
      return new InteractBinding(id, trigger, abilityId, itemMatcher, requireSneaking, requiredPermission, cancelEvent);
    }
  }
}
