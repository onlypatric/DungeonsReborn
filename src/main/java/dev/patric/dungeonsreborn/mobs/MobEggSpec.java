package dev.patric.dungeonsreborn.mobs;

import org.bukkit.inventory.ItemStack;

public record MobEggSpec(String id, String mobId, ItemStack item, String permission, long cooldownTicks) {
}
