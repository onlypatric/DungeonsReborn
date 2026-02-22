package dev.patric.dungeonsreborn.mobs.ai.v3;

import java.util.concurrent.atomic.LongAdder;

public final class MobAiV3Metrics {
  private final LongAdder submitted = new LongAdder();
  private final LongAdder completed = new LongAdder();
  private final LongAdder discardedStale = new LongAdder();
  private final LongAdder droppedBackpressure = new LongAdder();
  private final LongAdder failed = new LongAdder();

  public void markSubmitted() {
    submitted.increment();
  }

  public void markCompleted() {
    completed.increment();
  }

  public void markDiscardedStale() {
    discardedStale.increment();
  }

  public void markDroppedBackpressure() {
    droppedBackpressure.increment();
  }

  public void markFailed() {
    failed.increment();
  }

  public long submitted() {
    return submitted.sum();
  }

  public long completed() {
    return completed.sum();
  }

  public long discardedStale() {
    return discardedStale.sum();
  }

  public long droppedBackpressure() {
    return droppedBackpressure.sum();
  }

  public long failed() {
    return failed.sum();
  }
}

