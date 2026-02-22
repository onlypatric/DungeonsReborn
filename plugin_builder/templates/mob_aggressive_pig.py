"""Aggressive pig mob export for local test server."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder import (
    EntityType,
    Mob,
    MobAiGoalSpec,
    MobAiGoalType,
    MobAiProfile,
    MobExporter,
)


def build_mob():
    return (
        Mob("mob_custom_aggressive_pig")
        .mob_type(EntityType.PIG)
        .name("<red>Aggressive Pig</red>")
        .tier("TEST")
        .show_name(True)
        .stats(health=24, damage=5, armor=2, speed=0.32)
        .ai_profile_v3(
            MobAiProfile.AGGRESSIVE,
            aggro_radius=18.0,
            chase_speed=0.34,
            call_for_help_radius=0.0,
            open_doors=False,
        )
        .ai_selector(
            "pig_hunt_player",
            base_score=120,
            actions=[MobAiGoalSpec(MobAiGoalType.CHASE, priority=10, speed=0.34)],
        )
    )


def main() -> None:
    exporter = MobExporter("server/plugins/DungeonsReborn/mobs")
    exporter.write_batch([build_mob()], filename="mob_custom_aggressive_pig.yml")
    print("exported: server/plugins/DungeonsReborn/mobs/mob_custom_aggressive_pig.yml")


if __name__ == "__main__":
    main()

