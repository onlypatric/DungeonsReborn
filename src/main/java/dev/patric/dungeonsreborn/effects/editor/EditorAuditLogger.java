package dev.patric.dungeonsreborn.effects.editor;

import java.util.Objects;
import dev.patric.dungeonsreborn.logging.ServiceLogger;

public final class EditorAuditLogger {
  private final ServiceLogger logger;

  public EditorAuditLogger(ServiceLogger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  public void log(EditorAuditEvent event) {
    Objects.requireNonNull(event, "event");
    String detail = event.detail();
    if (detail == null || detail.isBlank()) {
      detail = "-";
    }
    logger.info("[Effects][Editor] " + event.action()
        + " actor=" + event.actorName()
        + " ability=" + event.abilityId()
        + " detail=" + detail);
  }
}
