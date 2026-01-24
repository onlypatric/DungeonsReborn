package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestVisibilitySpec(
    boolean hidden,
    boolean showInLog,
    boolean showInGiver,
    List<String> hints,
    List<QuestRequiredStatus> revealOn,
    List<QuestVisibilityCondition> requires
) {
  public static QuestVisibilitySpec visible() {
    return new QuestVisibilitySpec(false, true, true, List.of(), List.of(), List.of());
  }

  public QuestVisibilitySpec {
    hints = hints == null ? List.of() : List.copyOf(hints);
    revealOn = revealOn == null ? List.of() : List.copyOf(revealOn);
    requires = requires == null ? List.of() : List.copyOf(requires);
  }
}
