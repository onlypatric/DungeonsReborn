package dev.patric.dungeonsreborn.effects.afflict;

import java.util.UUID;
import dev.patric.dungeonsreborn.effects.actions.Action;

public final class AfflictionInstance {
  private final String id;
  private int stacks;
  private int maxStacks;
  private long expiresAtTick;
  private AfflictionRefreshPolicy refreshPolicy;
  private AfflictionAudience audience;
  private long tickEveryTicks;
  private long nextTickAt;
  private Action onTick;
  private Action onApply;
  private Action onStack;
  private Action onExpire;
  private UUID sourceCasterId;
  private String sourceAbilityId;

  public AfflictionInstance(
      String id,
      int stacks,
      int maxStacks,
      long expiresAtTick,
      AfflictionRefreshPolicy refreshPolicy,
      AfflictionAudience audience,
      long tickEveryTicks,
      Action onTick,
      Action onApply,
      Action onStack,
      Action onExpire,
      UUID sourceCasterId,
      String sourceAbilityId) {
    this.id = id;
    this.stacks = stacks;
    this.maxStacks = maxStacks;
    this.expiresAtTick = expiresAtTick;
    this.refreshPolicy = refreshPolicy;
    this.audience = audience;
    this.tickEveryTicks = tickEveryTicks;
    this.nextTickAt = tickEveryTicks > 0 ? tickEveryTicks : 0L;
    this.onTick = onTick;
    this.onApply = onApply;
    this.onStack = onStack;
    this.onExpire = onExpire;
    this.sourceCasterId = sourceCasterId;
    this.sourceAbilityId = sourceAbilityId;
  }

  public String id() {
    return id;
  }

  public int stacks() {
    return stacks;
  }

  public void setStacks(int stacks) {
    this.stacks = stacks;
  }

  public int maxStacks() {
    return maxStacks;
  }

  public void setMaxStacks(int maxStacks) {
    this.maxStacks = maxStacks;
  }

  public long expiresAtTick() {
    return expiresAtTick;
  }

  public void setExpiresAtTick(long expiresAtTick) {
    this.expiresAtTick = expiresAtTick;
  }

  public AfflictionRefreshPolicy refreshPolicy() {
    return refreshPolicy;
  }

  public void setRefreshPolicy(AfflictionRefreshPolicy refreshPolicy) {
    this.refreshPolicy = refreshPolicy;
  }

  public AfflictionAudience audience() {
    return audience;
  }

  public void setAudience(AfflictionAudience audience) {
    this.audience = audience;
  }

  public long tickEveryTicks() {
    return tickEveryTicks;
  }

  public void setTickEveryTicks(long tickEveryTicks) {
    this.tickEveryTicks = tickEveryTicks;
  }

  public long nextTickAt() {
    return nextTickAt;
  }

  public void setNextTickAt(long nextTickAt) {
    this.nextTickAt = nextTickAt;
  }

  public Action onTick() {
    return onTick;
  }

  public void setOnTick(Action onTick) {
    this.onTick = onTick;
  }

  public Action onApply() {
    return onApply;
  }

  public void setOnApply(Action onApply) {
    this.onApply = onApply;
  }

  public Action onStack() {
    return onStack;
  }

  public void setOnStack(Action onStack) {
    this.onStack = onStack;
  }

  public Action onExpire() {
    return onExpire;
  }

  public void setOnExpire(Action onExpire) {
    this.onExpire = onExpire;
  }

  public UUID sourceCasterId() {
    return sourceCasterId;
  }

  public void setSourceCasterId(UUID sourceCasterId) {
    this.sourceCasterId = sourceCasterId;
  }

  public String sourceAbilityId() {
    return sourceAbilityId;
  }

  public void setSourceAbilityId(String sourceAbilityId) {
    this.sourceAbilityId = sourceAbilityId;
  }
}
