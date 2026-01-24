package dev.patric.dungeonsreborn.quests;

import dev.patric.dungeonsreborn.quests.QuestService.QuestEntryStatus;

public record QuestGiverFilter(
    boolean showAvailable,
    boolean showActive,
    boolean showTurnIn,
    boolean showCompleted,
    boolean showFailed,
    boolean showCooldown,
    boolean showLocked
) {
  public static QuestGiverFilter all() {
    return new QuestGiverFilter(true, true, true, true, true, true, true);
  }

  public boolean allows(QuestEntryStatus status, boolean readyToTurnIn) {
    if (readyToTurnIn) {
      return showTurnIn;
    }
    if (status == null) {
      return showLocked;
    }
    return switch (status) {
      case AVAILABLE -> showAvailable;
      case ACTIVE -> showActive;
      case COMPLETED -> showCompleted;
      case FAILED -> showFailed;
      case COOLDOWN -> showCooldown;
      case LOCKED -> showLocked;
    };
  }
}
