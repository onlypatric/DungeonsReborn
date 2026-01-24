package dev.patric.dungeonsreborn.effects.items;

import dev.patric.dungeonsreborn.effects.actions.Action;

import java.util.List;

public record ItemHookSpec(
    List<String> abilities,
    Action action,
    long cooldownTicks,
    double manaCost,
    int durabilityCost,
    int consumeAmount) {
}
