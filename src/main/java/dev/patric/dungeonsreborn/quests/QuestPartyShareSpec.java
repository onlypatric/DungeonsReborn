package dev.patric.dungeonsreborn.quests;

public record QuestPartyShareSpec(boolean enabled,
                                  double radius,
                                  int minContributors,
                                  boolean leaderOnly,
                                  long idleTimeoutSeconds) {
  public QuestPartyShareSpec {
    if (radius < 0.0) {
      radius = 0.0;
    }
    if (minContributors < 0) {
      minContributors = 0;
    }
    if (idleTimeoutSeconds < 0L) {
      idleTimeoutSeconds = 0L;
    }
  }

  public static QuestPartyShareSpec none() {
    return new QuestPartyShareSpec(false, 0.0, 0, false, 0L);
  }
}
