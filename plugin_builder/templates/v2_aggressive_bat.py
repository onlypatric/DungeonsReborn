"""Aggressive bat mob template exported to the local test server."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder.v2 import (
    BuildContext,
    DamagePolicy,
    EntityType,
    ForEachMode,
    MobAiProfile,
    MobSoundProfile,
    Ref,
    ability,
    custom_mob_tier,
    fx,
    mob,
    pack_v2,
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    bite = ability(
        ctx,
        symbol="ability.mob.aggressive_bat.bite",
        name="Aggressive Bat: Bite",
        action=fx.for_each_target(
            mode=ForEachMode.FIRST,
            targeter=fx.target_sphere(radius=1.9, ignore_caster=True),
            then=fx.damage(3.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        ),
    )

    aggressive_bat = (
        mob.create(
            ctx,
            symbol="mob.showcase.aggressive_bat",
            name="<dark_gray>Fangwing Bat</dark_gray>",
            mob_type=EntityType.BAT,
        )
        .tier(custom_mob_tier("T1"))
        .show_name(True)
        .stats(
            health=20,
            damage=4,
            armor=0,
            speed=0.44,
            follow_range=24.0,
        )
        .ai_quick(
            MobAiProfile.AGGRESSIVE,
            aggro_radius=22.0,
            chase_speed=0.46,
            call_for_help_radius=8.0,
            open_doors=False,
        )
        .silent(True)
        .sounds(MobSoundProfile.GHOST)
        .events(on_spawn_tick=(Ref("ability.mob.aggressive_bat.bite"), 8))
    )

    pack.add(bite, aggressive_bat)
    return pack


def main() -> None:
    output = "server/plugins/DungeonsReborn"
    paths = build_v2().export(output)
    print("exported files:")
    for path in paths:
        if "aggressive_bat" in path or "ability_mob_aggressive_bat_bite" in path:
            print(path)


if __name__ == "__main__":
    main()
