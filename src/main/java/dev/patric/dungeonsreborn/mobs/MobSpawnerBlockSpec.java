package dev.patric.dungeonsreborn.mobs;

import org.bukkit.inventory.ItemStack;

public record MobSpawnerBlockSpec(
    String id,
    String mobId,
    ItemStack item,
    MobSpawnerTemplate template) {
}
