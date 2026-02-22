package dev.patric.dungeonsreborn.mobs.ai;

public final class MobAiRuntimeMetrics {
  private final int stepsLastTick;
  private final int pathMutationsLastTick;
  private final long totalSteps;
  private final long totalPathMutations;
  private final long guardrailTrips;
  private final long fallbackTicks;
  private final long sampleWindowTicks;

  public MobAiRuntimeMetrics(
      int stepsLastTick,
      int pathMutationsLastTick,
      long totalSteps,
      long totalPathMutations,
      long guardrailTrips,
      long fallbackTicks,
      long sampleWindowTicks) {
    this.stepsLastTick = Math.max(0, stepsLastTick);
    this.pathMutationsLastTick = Math.max(0, pathMutationsLastTick);
    this.totalSteps = Math.max(0L, totalSteps);
    this.totalPathMutations = Math.max(0L, totalPathMutations);
    this.guardrailTrips = Math.max(0L, guardrailTrips);
    this.fallbackTicks = Math.max(0L, fallbackTicks);
    this.sampleWindowTicks = Math.max(1L, sampleWindowTicks);
  }

  public int stepsLastTick() {
    return stepsLastTick;
  }

  public int pathMutationsLastTick() {
    return pathMutationsLastTick;
  }

  public long totalSteps() {
    return totalSteps;
  }

  public long totalPathMutations() {
    return totalPathMutations;
  }

  public long guardrailTrips() {
    return guardrailTrips;
  }

  public long fallbackTicks() {
    return fallbackTicks;
  }

  public long sampleWindowTicks() {
    return sampleWindowTicks;
  }
}
