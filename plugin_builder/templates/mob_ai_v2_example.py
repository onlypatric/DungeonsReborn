"""Mob AI V2 showcase template (new-content only, legacy mobs untouched)."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder import (
    EntityType,
    Mob,
    MobAiEngine,
    MobAiGoalSpec,
    MobAiGoalType,
    MobAiHooksSpec,
    MobAiPhaseMergeMode,
    MobAiProfile,
    MobAiSpec,
    MobExporter,
    MobPhase,
)


def build_examples():
    aggressive = (
        Mob("mob_v2_aggressive_skirmisher")
        .mob_type(EntityType.ZOMBIE)
        .name("<red>V2 Skirmisher</red>")
        .tier("V2")
        .stats(health=24, damage=5, armor=2, speed=0.30)
        .ai_simple(
            MobAiProfile.AGGRESSIVE,
            engine=MobAiEngine.V2,
            aggro_radius=16.0,
            leash_radius=30.0,
            chase_speed=0.30,
            open_doors=True,
            goals=[
                MobAiGoalSpec(MobAiGoalType.CHASE, priority=20, speed=0.30),
                MobAiGoalSpec(MobAiGoalType.CALL_HELP, priority=35),
            ],
            hooks=MobAiHooksSpec(on_enter_engage="ability_mob_v2_engage"),
        )
    )

    defensive = (
        Mob("mob_v2_defensive_guard")
        .mob_type(EntityType.SKELETON)
        .name("<gold>V2 Guard</gold>")
        .tier("V2")
        .stats(health=34, damage=6, armor=5, speed=0.26)
        .ai_advanced(
            MobAiSpec(
                engine=MobAiEngine.V2,
                profile=MobAiProfile.DEFENSIVE,
                aggro_radius=14.0,
                flee_health_ratio=0.20,
                state_transition_cooldown_ticks=10,
                call_for_help_radius=10.0,
                assist_radius=12.0,
                goals=[
                    MobAiGoalSpec(MobAiGoalType.GUARD, priority=15, radius=7.0, speed=0.24),
                    MobAiGoalSpec(
                        MobAiGoalType.HOLD_RANGE,
                        priority=30,
                        min_range=7.0,
                        max_range=14.0,
                        speed=0.28,
                    ),
                    MobAiGoalSpec(MobAiGoalType.ASSIST, priority=40, radius=12.0, speed=0.30),
                ],
                hooks=MobAiHooksSpec(
                    on_enter_idle="ability_mob_v2_idle",
                    on_enter_retreat="ability_mob_v2_retreat",
                ),
            )
        )
        .phase(MobPhase("phase_enraged", health_below=0.45))
        .phase_ai(
            "phase_enraged",
            MobAiSpec(
                engine=MobAiEngine.V2,
                profile=MobAiProfile.AGGRESSIVE,
                rage_health_ratio=0.70,
                goals=[MobAiGoalSpec(MobAiGoalType.CHASE, priority=10, speed=0.34)],
            ),
            merge_mode=MobAiPhaseMergeMode.PATCH,
        )
    )

    passive = (
        Mob("mob_v2_passive_critter")
        .mob_type(EntityType.SHEEP)
        .name("<green>V2 Critter</green>")
        .tier("V2")
        .stats(health=14, damage=1, armor=0, speed=0.24)
        .ai_simple(
            MobAiProfile.PASSIVE,
            engine=MobAiEngine.V2,
            aggro_radius=0.0,
            flee_health_ratio=0.95,
            goals=[MobAiGoalSpec(MobAiGoalType.WANDER, priority=50, radius=6.0, interval_ticks=80)],
        )
    )

    return [aggressive, defensive, passive]


def main() -> None:
    exporter = MobExporter("server/plugins/DungeonsReborn/mobs")
    exporter.write_batch(
        build_examples(),
        filename="mob_ai_v2_example.yml",
    )
    print("exported: server/plugins/DungeonsReborn/mobs/mob_ai_v2_example.yml")


if __name__ == "__main__":
    main()
