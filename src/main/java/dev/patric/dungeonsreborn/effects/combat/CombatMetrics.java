package dev.patric.dungeonsreborn.effects.combat;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class CombatMetrics {
  private final LongAdder dispatchCalls = new LongAdder();
  private final LongAdder dispatchedBindings = new LongAdder();
  private final LongAdder droppedByGuardrail = new LongAdder();
  private final LongAdder droppedByQueue = new LongAdder();
  private final LongAdder stalePlans = new LongAdder();
  private final LongAdder asyncSubmitted = new LongAdder();
  private final LongAdder asyncCompleted = new LongAdder();
  private final LongAdder cancelledPreEvents = new LongAdder();
  private final LongAdder droppedProjectileGuardrail = new LongAdder();
  private final LongAdder travelStepDropped = new LongAdder();
  private final LongAdder staleProjectilesCleaned = new LongAdder();
  private final AtomicLong activeProjectiles = new AtomicLong();
  private final Map<CombatEventType, LongAdder> eventsByType = new EnumMap<>(CombatEventType.class);

  public CombatMetrics() {
    for (CombatEventType type : CombatEventType.values()) {
      eventsByType.put(type, new LongAdder());
    }
  }

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

  public void incEventType(CombatEventType type) {
    if (type == null) {
      return;
    }
    LongAdder counter = eventsByType.get(type);
    if (counter != null) {
      counter.increment();
    }
  }

  public void incCancelledPreEvents() {
    cancelledPreEvents.increment();
  }

  public void incDroppedProjectileGuardrail() {
    droppedProjectileGuardrail.increment();
  }

  public void incTravelStepDropped() {
    travelStepDropped.increment();
  }

  public void addStaleProjectilesCleaned(long count) {
    if (count > 0) {
      staleProjectilesCleaned.add(count);
    }
  }

  public void setActiveProjectiles(long count) {
    activeProjectiles.set(Math.max(0L, count));
  }

  public String summary() {
    StringBuilder eventSummary = new StringBuilder();
    for (CombatEventType type : CombatEventType.values()) {
      long count = eventsByType.get(type).sum();
      if (count <= 0L) {
        continue;
      }
      if (!eventSummary.isEmpty()) {
        eventSummary.append(',');
      }
      eventSummary.append(type.name()).append('=').append(count);
    }
    if (eventSummary.isEmpty()) {
      eventSummary.append("none");
    }
    return "dispatchCalls=" + dispatchCalls.sum()
        + " dispatchedBindings=" + dispatchedBindings.sum()
        + " droppedGuardrail=" + droppedByGuardrail.sum()
        + " droppedQueue=" + droppedByQueue.sum()
        + " stalePlans=" + stalePlans.sum()
        + " asyncSubmitted=" + asyncSubmitted.sum()
        + " asyncCompleted=" + asyncCompleted.sum()
        + " preCancelled=" + cancelledPreEvents.sum()
        + " projectileGuardrailDrops=" + droppedProjectileGuardrail.sum()
        + " travelStepDropped=" + travelStepDropped.sum()
        + " activeProjectiles=" + activeProjectiles.get()
        + " staleProjectilesCleaned=" + staleProjectilesCleaned.sum()
        + " eventsByType={" + eventSummary + "}";
  }
}
