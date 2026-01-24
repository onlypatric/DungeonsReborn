package dev.patric.dungeonsreborn.classes;

import java.util.List;

public record ClassUnlockSpec(int level, int tokens, List<String> quests,
    List<ClassUnlockItemSpec> items, List<ClassUnlockCurrencySpec> currencies) {
  public static ClassUnlockSpec none() {
    return new ClassUnlockSpec(0, 0, List.of(), List.of(), List.of());
  }
}
