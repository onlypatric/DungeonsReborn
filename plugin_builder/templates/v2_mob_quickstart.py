"""Quickstart template focused on a single custom mob."""

from __future__ import annotations

from dungeonsreborn_builder.v2 import (
    BuildContext,
    EntityType,
    MobAiProfile,
    MobSoundProfile,
    custom_mob_tier,
    pack_v2,
    mob,
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    ghost = (
        mob.create(ctx, symbol="mob.quick.ghost", name="Quick Ghost", mob_type=EntityType.ZOMBIE)
        .show_name(True)
        .tier(custom_mob_tier("TEST"))
        .stats(health=24, damage=5, armor=1, speed=0.30)
        .ai_quick(MobAiProfile.AGGRESSIVE, aggro_radius=18.0, chase_speed=0.33)
        .silent(True)
        .sounds(MobSoundProfile.GHOST)
    )

    pack.add(ghost)
    return pack


if __name__ == "__main__":
    build_v2().export("./out")
