package dev.patric.dungeonsreborn.effects.editor;

import java.util.Objects;

import dev.patric.dungeonsreborn.effects.EffectsEngine;
import dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities;

public record EditorServices(
    EffectsEngine engine,
    EffectsYamlAbilities yaml,
    EditorDraftStore drafts,
    EditorAccessController access,
    EditorLockManager locks,
    EditorAuditLogger audit) {
  public EditorServices {
    Objects.requireNonNull(engine, "engine");
    Objects.requireNonNull(drafts, "drafts");
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(locks, "locks");
    Objects.requireNonNull(audit, "audit");
  }
}
