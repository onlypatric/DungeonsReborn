"""Central mapping of runtime enum domains to builder enums."""

from __future__ import annotations

from .effects_ids import ACTION_TYPES, CONDITION_TYPES, COST_TYPES, REQUIREMENT_TYPES, TARGETER_TYPES
from .items import ItemClick, ItemHookType, ItemConsumeMode
from .mobs import (
    MobAttackTrigger,
    MobTargetMode,
    MobLocomotionMode,
    MobBehaviorState,
    MobAiGoalType,
    MobAttackAoEShape,
    MobTargetFilter,
    MobSpawnTetherAction,
    MobPartyRule,
)
from .vanilla import (
    Attribute,
    Enchantment,
    EntityType,
    ItemFlag,
    Material,
    Particle,
    PatternType,
    PotionEffectType,
    PotionType,
    Sound,
    TrimMaterial,
    TrimPattern,
)


def list_enum_domains() -> dict[str, list[str]]:
    return {
        "effects.action_types": list(ACTION_TYPES),
        "effects.targeter_types": list(TARGETER_TYPES),
        "effects.condition_types": list(CONDITION_TYPES),
        "effects.requirement_types": list(REQUIREMENT_TYPES),
        "effects.cost_types": list(COST_TYPES),
        "items.click_types": [e.value for e in ItemClick],
        "items.hook_types": [e.value for e in ItemHookType],
        "items.consume_modes": [e.value for e in ItemConsumeMode],
        "mobs.attack_triggers": [e.value for e in MobAttackTrigger],
        "mobs.target_modes": [e.value for e in MobTargetMode],
        "mobs.locomotion_modes": [e.value for e in MobLocomotionMode],
        "mobs.behavior_states": [e.value for e in MobBehaviorState],
        "mobs.ai_goal_types": [e.value for e in MobAiGoalType],
        "mobs.attack_aoe_shapes": [e.value for e in MobAttackAoEShape],
        "mobs.target_filters": [e.value for e in MobTargetFilter],
        "mobs.spawn_tether_actions": [e.value for e in MobSpawnTetherAction],
        "mobs.party_rules": [e.value for e in MobPartyRule],
        "vanilla.materials": [e.name for e in Material],
        "vanilla.particles": [e.name for e in Particle],
        "vanilla.sounds": [e.name for e in Sound],
        "vanilla.attributes": [e.name for e in Attribute],
        "vanilla.entity_types": [e.name for e in EntityType],
        "vanilla.enchantments": [e.name for e in Enchantment],
        "vanilla.item_flags": [e.name for e in ItemFlag],
        "vanilla.potion_types": [e.name for e in PotionType],
        "vanilla.potion_effect_types": [e.name for e in PotionEffectType],
        "vanilla.banner_patterns": [e.name for e in PatternType],
        "vanilla.trim_materials": [e.name for e in TrimMaterial],
        "vanilla.trim_patterns": [e.name for e in TrimPattern],
    }
