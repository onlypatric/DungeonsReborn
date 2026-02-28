"""Full-override bat template (V4 schema) exported to the local test server."""

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
    MobAiAuthority,
    MobAiIntentType,
    MobAiMovementPolicy,
    MobAiMode,
    MobAiProfile,
    MobAiRuntimeModel,
    MobAiTargetSourceType,
    Ref,
    Sound,
    ability,
    ai_condition,
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
        symbol="ability.mob.full_override_bat.bite",
        name="Override Bat: Bite",
        action=fx.for_each_target(
            mode=ForEachMode.FIRST,
            targeter=fx.target_sphere(radius=2.2, ignore_caster=True),
            then=fx.damage(5.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        ),
    )

    bat = (
        mob.create(
            ctx,
            symbol="mob.showcase.full_override_bat",
            name="<dark_gray>Night Talon</dark_gray>",
            mob_type=EntityType.BAT,
        )
        .tier(custom_mob_tier("T2"))
        .show_name(True)
        .stats(health=26, damage=6, armor=1, speed=0.42, follow_range=30.0)
        .ai_v4(
            engine="V3",
            mode=MobAiMode.FULL_OVERRIDE,
            authority=MobAiAuthority.ABILITY_DRIVEN,
            profile=MobAiProfile.SCOUT,
            aggro_radius=26.0,
            chase_speed=0.52,
            flee_speed=0.65,
        )
        .ai_runtime_model(MobAiRuntimeModel.NATURAL_V1)
        .ai_movement_policy(MobAiMovementPolicy.PATHFINDER_FIRST)
        .ai_target_source(MobAiTargetSourceType.CURRENT_TARGET, memory_ticks=30, priority=10)
        .ai_selector(
            MobAiIntentType.CHASE,
            selector_id="chase_target",
            priority=10,
            when=ai_condition.has_target(True),
            speed=0.56,
        )
        .ai_selector_cast(
            Ref("ability.mob.full_override_bat.bite"),
            selector_id="bite_close",
            priority=5,
            when=ai_condition.all(
                ai_condition.has_target(True),
                ai_condition.target_distance_lte(2.4),
            ),
            intent=MobAiIntentType.CAST_ONLY,
            cast_cooldown_ticks=10,
            require_target=True,
        )
        .ai_selector(
            MobAiIntentType.WANDER,
            selector_id="air_wander",
            priority=100,
            when=ai_condition.not_(ai_condition.has_target(True)),
            interval_ticks=14,
            radius=8.0,
            speed=0.36,
        )
        .sound_overrides(
            spawn=Sound.ENTITY_PHANTOM_FLAP,
            death=Sound.ENTITY_PHANTOM_DEATH,
            volume=0.85,
            pitch=1.15,
        )
    )

    pack.add(bite, bat)
    return pack


def main() -> None:
    output = "server/plugins/DungeonsReborn"
    paths = build_v2().export(output)
    print("exported files:")
    for path in paths:
        if "full_override_bat" in path or "ability_mob_full_override_bat_bite" in path:
            print(path)


if __name__ == "__main__":
    main()
