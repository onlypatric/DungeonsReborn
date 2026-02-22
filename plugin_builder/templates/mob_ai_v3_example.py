"""Mob AI V3 showcase template."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder import (
    EntityType,
    Mob,
    MobAiCombatSpec,
    MobAiEnvironmentSpec,
    MobAiGoalSpec,
    MobAiGoalType,
    MobAiGroupSpec,
    MobAiHooksSpec,
    MobAiPerceptionSpec,
    MobAiProfile,
    MobAiSchedulerSpec,
    MobAiSpec,
    MobAiPhaseMergeMode,
    MobExporter,
    MobPhase,
)


def build_examples():
    v3_guard = (
        Mob("mob_v3_guard")
        .mob_type(EntityType.ZOMBIE)
        .name("<aqua>V3 Guard</aqua>")
        .tier("V3")
        .stats(health=32, damage=6, armor=4, speed=0.27)
        .ai_profile_v3(
            MobAiProfile.DEFENSIVE,
            perception=MobAiPerceptionSpec(aggro_radius=14.0, retarget_cooldown_ticks=20),
            combat=MobAiCombatSpec(flee_health_ratio=0.2, rage_health_ratio=0.60, chase_speed=0.30),
            group=MobAiGroupSpec(assist_radius=12.0, call_for_help_radius=10.0, focus_fire=True),
            environment=MobAiEnvironmentSpec(avoid_lava=True, avoid_powder_snow=True, open_doors=True),
            scheduler=MobAiSchedulerSpec(state_transition_cooldown_ticks=10),
            hooks=MobAiHooksSpec(on_enter_engage="ability_mob_v3_guard_engage"),
        )
        .ai_selector(
            "engage_primary",
            base_score=100,
            actions=[
                MobAiGoalSpec(MobAiGoalType.CHASE, priority=20, speed=0.30),
                MobAiGoalSpec(MobAiGoalType.CALL_HELP, priority=30),
            ],
        )
        .phase(MobPhase("phase_enraged", health_below=0.45))
        .phase_ai_v3(
            "phase_enraged",
            MobAiSpec(
                profile=MobAiProfile.BERSERKER,
                combat=MobAiCombatSpec(rage_health_ratio=0.80, chase_speed=0.36),
                goals=[MobAiGoalSpec(MobAiGoalType.CHASE, priority=10, speed=0.36)],
            ),
            merge_mode=MobAiPhaseMergeMode.PATCH,
        )
    )

    v3_scout = (
        Mob("mob_v3_scout")
        .mob_type(EntityType.SKELETON)
        .name("<yellow>V3 Scout</yellow>")
        .tier("V3")
        .stats(health=20, damage=5, armor=2, speed=0.33)
        .ai_profile_v3(
            MobAiProfile.SCOUT,
            perception=MobAiPerceptionSpec(aggro_radius=10.0, retarget_cooldown_ticks=12),
            combat=MobAiCombatSpec(chase_speed=0.34, kite_min_range=7.0, kite_speed=0.32),
            environment=MobAiEnvironmentSpec(avoid_lava=True, avoid_cactus=True),
        )
        .ai_selector(
            "keep_distance",
            base_score=90,
            actions=[MobAiGoalSpec(MobAiGoalType.HOLD_RANGE, priority=15, min_range=7.0, max_range=14.0, speed=0.32)],
        )
    )

    return [v3_guard, v3_scout]


def main() -> None:
    exporter = MobExporter("server/plugins/DungeonsReborn/mobs")
    exporter.write_batch(build_examples(), filename="mob_ai_v3_example.yml")
    print("exported: server/plugins/DungeonsReborn/mobs/mob_ai_v3_example.yml")


if __name__ == "__main__":
    main()

