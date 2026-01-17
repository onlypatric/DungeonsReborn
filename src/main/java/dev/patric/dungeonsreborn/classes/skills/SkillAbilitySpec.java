package dev.patric.dungeonsreborn.classes.skills;

public record SkillAbilitySpec(
    String abilityId,
    SkillAbilityTrigger trigger,
    boolean requireSneaking,
    String requiredPermission,
    long periodTicks,
    boolean cancelEvent) {
}
