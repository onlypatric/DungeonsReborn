package dev.patric.dungeonsreborn.effects.combat;

import java.util.concurrent.atomic.LongAdder;

public final class CombatMetrics {
  private final LongAdder dispatchCalls = new LongAdder();
  private final LongAdder dispatchedBindings = new LongAdder();
  private final LongAdder droppedByGuardrail = new LongAdder();
  private final LongAdder droppedByQueue = new LongAdder();
  private final LongAdder stalePlans = new LongAdder();
  private final LongAdder asyncSubmitted = new LongAdder();
  private final LongAdder asyncCompleted = new LongAdder();

  public void incDispatchCalls() {
    dispatchCalls.increment();
  }

  public void addDispatchedBindings(long count) {
    if (count > 0) {
      dispatchedBindings.add(count);
    }
  }

  public void incDroppedByGuardrail() {
    droppedByGuardrail.increment();
  }

  public void incDroppedByQueue() {
    droppedByQueue.increment();
  }

  public void incStalePlans() {
    stalePlans.increment();
  }

  public void incAsyncSubmitted() {
    asyncSubmitted.increment();
  }

  public void incAsyncCompleted() {
    asyncCompleted.increment();
  }

  public String summary() {
    return "dispatchCalls=" + dispatchCalls.sum()
        + " dispatchedBindings=" + dispatchedBindings.sum()
        + " droppedGuardrail=" + droppedByGuardrail.sum()
        + " droppedQueue=" + droppedByQueue.sum()
        + " stalePlans=" + stalePlans.sum()
        + " asyncSubmitted=" + asyncSubmitted.sum()
        + " asyncCompleted=" + asyncCompleted.sum();
  }
}

