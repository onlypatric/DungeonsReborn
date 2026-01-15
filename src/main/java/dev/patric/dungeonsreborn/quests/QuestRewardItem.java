package dev.patric.dungeonsreborn.quests;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record QuestRewardItem(QuestRewardItemType type, String itemId, Material material, ItemStack item, int amount) {
}
