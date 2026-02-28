"""Invisible ghost zombie template exported to the local test server."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder.v2 import (
    BuildContext,
    EntityType,
    MobAiProfile,
    Particle,
    PotionEffect,
    Ref,
    Sound,
    TargetAnchor,
    ability,
    custom_mob_tier,
    fx,
    mob,
    pack_v2,
)

GHOST_TEXTURE = (
    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUv"
    "YzExZGU5NmMzZTdiNTJmYTVlNGM3OTRiNGJlMTRhMDVmYzY3Njk0Y2E1ZWZmOWM5ZWI4YmFhYzY3MmQyMWMwOSJ9fX0="
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    refresh_invisibility = ability(
        ctx,
        symbol="ability.mob.ghost_zombie.refresh_invisibility",
        name="Ghost Zombie: Refresh Invisibility",
        action=fx.for_each_target(
            targeter=fx.target_self(),
            then=fx.potion(
                PotionEffect.INVISIBILITY,
                duration_ticks=120,
                amplifier=0,
                ambient=True,
                particles=False,
                icon=False,
            ),
        ),
    )

    ambient_whisper = ability(
        ctx,
        symbol="ability.mob.ghost_zombie.ambient_whisper",
        name="Ghost Zombie: Ambient Whisper",
        action=fx.sequence(
            fx.sound(Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, volume=0.75, pitch=0.55),
            fx.particles_sphere_shell(
                Particle.SOUL_FIRE_FLAME,
                radius=0.55,
                points=10,
                count=1,
                offset=0.02,
                extra=0.0,
                at=TargetAnchor.ORIGIN,
            ),
        ),
    )

    hurt_wail = ability(
        ctx,
        symbol="ability.mob.ghost_zombie.hurt_wail",
        name="Ghost Zombie: Hurt Wail",
        action=fx.sequence(
            fx.sound(Sound.ENTITY_VEX_HURT, volume=1.0, pitch=0.55),
            fx.particles_sphere_shell(
                Particle.SCULK_SOUL,
                radius=0.6,
                points=9,
                count=1,
                offset=0.02,
                extra=0.0,
                at=TargetAnchor.ORIGIN,
            ),
        ),
    )

    attack_chitter = ability(
        ctx,
        symbol="ability.mob.ghost_zombie.attack_chitter",
        name="Ghost Zombie: Attack Chitter",
        action=fx.sound(Sound.ENTITY_PHANTOM_BITE, volume=0.85, pitch=0.8),
    )

    aggro_screech = ability(
        ctx,
        symbol="ability.mob.ghost_zombie.aggro_screech",
        name="Ghost Zombie: Aggro Screech",
        action=fx.sound(Sound.ENTITY_WARDEN_NEARBY_CLOSE, volume=0.95, pitch=1.85),
    )

    ghost = (
        mob.create(
            ctx,
            symbol="mob.showcase.invisible_ghost_zombie",
            name="<gray>Veilbound Ghost</gray>",
            mob_type=EntityType.ZOMBIE,
        )
        .tier(custom_mob_tier("ELITE"))
        .show_name(True)
        .stats(health=54, damage=7, armor=4, speed=0.34, follow_range=30.0)
        .ai_quick(
            MobAiProfile.AGGRESSIVE,
            aggro_radius=26.0,
            chase_speed=0.36,
            call_for_help_radius=12.0,
            open_doors=False,
        )
        .silent(True)
        .look_skin_head(GHOST_TEXTURE)
        .sound_overrides(
            spawn=Sound.ENTITY_ENDERMAN_TELEPORT,
            death=Sound.ENTITY_ALLAY_DEATH,
            volume=1.0,
            pitch=0.9,
        )
        .events(
            on_hurt=Ref("ability.mob.ghost_zombie.hurt_wail"),
            on_hit=Ref("ability.mob.ghost_zombie.attack_chitter"),
            on_target=Ref("ability.mob.ghost_zombie.aggro_screech"),
            on_idle=(Ref("ability.mob.ghost_zombie.ambient_whisper"), 60),
            on_spawn_tick=(Ref("ability.mob.ghost_zombie.refresh_invisibility"), 20),
        )
    )

    pack.add(
        refresh_invisibility,
        ambient_whisper,
        hurt_wail,
        attack_chitter,
        aggro_screech,
        ghost,
    )
    return pack


def main() -> None:
    output = "server/plugins/DungeonsReborn"
    paths = build_v2().export(output)
    print("exported files:")
    for path in paths:
        if "ghost_zombie" in path or "invisible_ghost" in path:
            print(path)


if __name__ == "__main__":
    main()
