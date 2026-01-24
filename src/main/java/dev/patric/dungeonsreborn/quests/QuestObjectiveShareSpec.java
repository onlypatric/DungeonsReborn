package dev.patric.dungeonsreborn.quests;

public record QuestObjectiveShareSpec(Boolean enabled,
                                      Double radius,
                                      Integer minContributors,
                                      Boolean leaderOnly,
                                      Long idleTimeoutSeconds) {
  public static QuestObjectiveShareSpec none() {
    return new QuestObjectiveShareSpec(null, null, null, null, null);
  }

  public QuestPartyShareSpec apply(QuestPartyShareSpec base) {
    if (base == null) {
      base = QuestPartyShareSpec.none();
    }
    boolean enabledValue = enabled != null ? enabled.booleanValue() : base.enabled();
    double radiusValue = radius != null ? Math.max(0.0, radius.doubleValue()) : base.radius();
    int minContributorsValue = minContributors != null ? Math.max(0, minContributors.intValue())
        : base.minContributors();
    boolean leaderOnlyValue = leaderOnly != null ? leaderOnly.booleanValue() : base.leaderOnly();
    long idleTimeoutValue = idleTimeoutSeconds != null ? Math.max(0L, idleTimeoutSeconds.longValue())
        : base.idleTimeoutSeconds();
    return new QuestPartyShareSpec(enabledValue, radiusValue, minContributorsValue, leaderOnlyValue, idleTimeoutValue);
  }
}
