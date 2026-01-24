package dev.patric.dungeonsreborn.quests;

import java.util.List;

public record QuestValidationReport(
    int schemaVersion,
    int currentSchemaVersion,
    int questCount,
    int rotationPoolCount,
    int errorCount,
    int warningCount,
    List<String> warnings
) {
  public boolean ok() {
    return errorCount == 0;
  }
}
