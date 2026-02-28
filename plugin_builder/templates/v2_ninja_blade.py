"""Netherite ninja blade export for local test server."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder.v2 import (
    BuildContext,
    DamagePolicy,
    ForEachMode,
    ItemClick,
    Material,
    Particle,
    ProjectileKind,
    Ref,
    Sound,
    TargetAnchor,
    ability,
    bind,
    fx,
    item,
    pack_v2,
)


def build_ninja_slash_ability(ctx: BuildContext):
    slash_hit = fx.sequence(
        fx.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, volume=0.65, pitch=1.2),
        fx.particles_sphere_shell(
            Particle.SWEEP_ATTACK,
            radius=1.8,
            points=18,
            count=1,
            offset=0.02,
            extra=0.0,
            at=TargetAnchor.ORIGIN,
        ),
        fx.for_each_target(
            mode=ForEachMode.EACH,
            origin_at=TargetAnchor.ORIGIN,
            targeter=fx.target_sphere(radius=2.7, ignore_caster=True),
            then=fx.damage(3.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        ),
    )

    return (
        ability(
            ctx,
            symbol="ability.weapon.ninja_blade.right_click",
            name="Ninja Blade: Shadow Rush",
            action=fx.sequence(
                fx.sound(Sound.ENTITY_ENDERMAN_TELEPORT, volume=0.75, pitch=1.35),
                fx.projectile(
                    kind=ProjectileKind.SNOWBALL,
                    speed_per_tick=1.8,
                    max_distance=8.0,
                    hit_radius=0.6,
                    ignore_caster=True,
                    on_hit=slash_hit,
                ),
            ),
            description="Leap forward and carve everything in your path.",
        )
        .cooldown(200)
    )


def build_ninja_blade_item(ctx: BuildContext):
    return (
        item.create(
            ctx,
            symbol="item.weapon.ninja_blade",
            name="<dark_gray>Nethershadow Blade</dark_gray>",
            material=Material.NETHERITE_SWORD,
        )
        .lore(
            "<gray>Right Click: <white>Shadow Rush</white></gray>",
            "<gray>Leap: <white>8 blocks forward, 3 blocks up</white></gray>",
            "<gray>Effect: <white>Ninja slashes in path + vicinity</white></gray>",
            "<gray>Cooldown: <white>10.0s</white></gray>",
        )
        .bind(
            bind.use(
                Ref("ability.weapon.ninja_blade.right_click"),
                click=ItemClick.RIGHT_CLICK,
                cancel_event=True,
            )
        )
    )


def build_pack():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)
    pack.add(build_ninja_slash_ability(ctx), build_ninja_blade_item(ctx))
    return pack


def main() -> None:
    out_dir = "server/plugins/DungeonsReborn"
    paths = build_pack().export(out_dir)
    print("exported files:")
    for path in paths:
        if "ninja_blade" in path:
            print(path)


if __name__ == "__main__":
    main()
