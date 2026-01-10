package dev.patric.dungeonsreborn.effects.editor;

import java.util.Objects;
import java.util.logging.Logger;

public final class EditorAuditLogger {
  private final Logger logger;

  public EditorAuditLogger(Logger logger) {
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
