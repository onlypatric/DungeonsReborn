package dev.patric.dungeonsreborn.quests;

import java.util.Objects;

import org.bukkit.potion.PotionEffectType;

public record QuestRewardBuff(PotionEffectType type,
                              int durationTicks,
                              int amplifier,
                              boolean ambient,
                              boolean particles,
                              boolean icon) {
  public QuestRewardBuff {
    Objects.requireNonNull(type, "type");
    durationTicks = Math.max(0, durationTicks);
    amplifier = Math.max(0, amplifier);
  }
}
