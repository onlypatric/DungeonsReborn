package dev.patric.dungeonsreborn.effects.editor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EditorAuditEvent(
    EditorAuditAction action,
    UUID actorId,
    String actorName,
    String abilityId,
    String detail,
    Instant timestamp) {
  public EditorAuditEvent {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(actorName, "actorName");
    Objects.requireNonNull(abilityId, "abilityId");
    Objects.requireNonNull(timestamp, "timestamp");
  }

  public static EditorAuditEvent of(EditorAuditAction action, UUID actorId, String actorName, String abilityId, String detail) {
    return new EditorAuditEvent(action, actorId, actorName, abilityId, detail, Instant.now());
  }
}
