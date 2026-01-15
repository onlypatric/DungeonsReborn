package dev.patric.dungeonsreborn.quests;

import java.util.Arrays;

public final class QuestPlayerQuest {
  private final String questId;
  private QuestStatus status;
  private long startedAt;
  private long completedAt;
  private long cooldownUntil;
  private int[] progress;

  public QuestPlayerQuest(String questId, QuestStatus status, long startedAt, long completedAt, long cooldownUntil, int[] progress) {
    this.questId = questId;
    this.status = status;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.cooldownUntil = cooldownUntil;
    this.progress = progress == null ? new int[0] : progress;
  }

  public String questId() {
    return questId;
  }

  public QuestStatus status() {
    return status;
  }

  public void status(QuestStatus status) {
    this.status = status;
  }

  public long startedAt() {
    return startedAt;
  }

  public void startedAt(long startedAt) {
    this.startedAt = startedAt;
  }

  public long completedAt() {
    return completedAt;
  }

  public void completedAt(long completedAt) {
    this.completedAt = completedAt;
  }

  public long cooldownUntil() {
    return cooldownUntil;
  }

  public void cooldownUntil(long cooldownUntil) {
    this.cooldownUntil = cooldownUntil;
  }

  public int progress(int index) {
    if (index < 0 || index >= progress.length) {
      return 0;
    }
    return progress[index];
  }

  public void progress(int index, int value) {
    ensureProgressLength(index + 1);
    progress[index] = value;
  }

  public int[] progress() {
    return progress;
  }

  public void resetProgress(int size) {
    progress = new int[Math.max(0, size)];
  }

  public void ensureProgressLength(int size) {
    if (size <= progress.length) {
      return;
    }
    progress = Arrays.copyOf(progress, size);
  }

  public boolean isCooldownActive(long nowMillis) {
    return cooldownUntil > 0 && nowMillis < cooldownUntil;
  }
}
