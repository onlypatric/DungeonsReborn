package dev.patric.dungeonsreborn.mobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.inventory.ItemStack;

public record MobAdvancementRewardSpec(int xp, int skillPoints, List<ItemStack> items) {
  public MobAdvancementRewardSpec {
    int safeXp = Math.max(0, xp);
    int safeSkills = Math.max(0, skillPoints);
    List<ItemStack> safeItems = items == null ? List.of() : new ArrayList<>(items);
    for (int i = 0; i < safeItems.size(); i++) {
      ItemStack stack = safeItems.get(i);
      safeItems.set(i, stack == null ? null : stack.clone());
    }
    items = Collections.unmodifiableList(safeItems);
    xp = safeXp;
    skillPoints = safeSkills;
  }
}
