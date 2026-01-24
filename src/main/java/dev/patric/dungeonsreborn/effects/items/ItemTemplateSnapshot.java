package dev.patric.dungeonsreborn.effects.items;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

public record ItemTemplateSnapshot(
    String id,
    int version,
    ItemStack baseItem,
    ItemStack matchBase,
    ItemTemplateCompiler.DurabilityRange durabilityRange,
    ItemStatBlock baseStats,
    ItemAffixPool affixPool,
    ItemTierSpec tierSpec,
    String rarityId,
    Map<ItemHookType, List<ItemHookSpec>> hooks) {
}
