package dev.patric.dungeonsreborn.quests;

import java.util.Arrays;

public final class QuestPlayerQuest {
  private final String questId;
  private QuestStatus status;
  private long startedAt;
  private long completedAt;
  private long cooldownUntil;
  private int dailyCount;
  private int weeklyCount;
  private long dailyResetAt;
  private long weeklyResetAt;
  private int[] progress;

  public QuestPlayerQuest(String questId, QuestStatus status, long startedAt, long completedAt, long cooldownUntil,
      int dailyCount, int weeklyCount, long dailyResetAt, long weeklyResetAt, int[] progress) {
    this.questId = questId;
    this.status = status;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.cooldownUntil = cooldownUntil;
    this.dailyCount = dailyCount;
    this.weeklyCount = weeklyCount;
    this.dailyResetAt = dailyResetAt;
    this.weeklyResetAt = weeklyResetAt;
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

  public int dailyCount() {
    return dailyCount;
  }

  public void dailyCount(int dailyCount) {
    this.dailyCount = dailyCount;
  }

  public int weeklyCount() {
    return weeklyCount;
  }

  public void weeklyCount(int weeklyCount) {
    this.weeklyCount = weeklyCount;
  }

  public long dailyResetAt() {
    return dailyResetAt;
  }

  public void dailyResetAt(long dailyResetAt) {
    this.dailyResetAt = dailyResetAt;
  }

  public long weeklyResetAt() {
    return weeklyResetAt;
  }

  public void weeklyResetAt(long weeklyResetAt) {
    this.weeklyResetAt = weeklyResetAt;
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
