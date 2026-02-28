"""Generate a 10-gun test pack (items + abilities + crafting recipes) for local server."""

from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder.v2 import (
    Action,
    BuildContext,
    DamagePolicy,
    ForEachMode,
    ItemClick,
    Material,
    Particle,
    Ref,
    Sound,
    TargetAnchor,
    ability,
    bind,
    custom_projectile_kind,
    fx,
    item,
    pack_v2,
    recipe,
    recipe_ingredient,
)


@dataclass(frozen=True)
class GunSpec:
    key: str
    label: str
    material: Material
    base_damage: float
    max_distance: float
    aoe_radius: float
    smoke_particle: Particle
    smoke_points: int


BASE_GUNS: tuple[GunSpec, ...] = (
    GunSpec(
        key="wooden",
        label="Wooden",
        material=Material.WOODEN_HOE,
        base_damage=2.0,
        max_distance=10.0,
        aoe_radius=2.0,
        smoke_particle=Particle.SMOKE,
        smoke_points=18,
    ),
    GunSpec(
        key="golden",
        label="Golden",
        material=Material.GOLDEN_HOE,
        base_damage=4.0,
        max_distance=14.0,
        aoe_radius=2.2,
        smoke_particle=Particle.SMOKE,
        smoke_points=18,
    ),
    GunSpec(
        key="iron",
        label="Iron",
        material=Material.IRON_HOE,
        base_damage=6.0,
        max_distance=18.0,
        aoe_radius=2.8,
        smoke_particle=Particle.SMOKE,
        smoke_points=24,
    ),
    GunSpec(
        key="diamond",
        label="Diamond",
        material=Material.DIAMOND_HOE,
        base_damage=8.0,
        max_distance=24.0,
        aoe_radius=3.0,
        smoke_particle=Particle.SMOKE,
        smoke_points=24,
    ),
    GunSpec(
        key="netherite",
        label="Netherite",
        material=Material.NETHERITE_HOE,
        base_damage=10.0,
        max_distance=30.0,
        aoe_radius=3.8,
        smoke_particle=Particle.WHITE_SMOKE,
        smoke_points=30,
    ),
)


HEAVY_COMPONENTS: dict[str, Material] = {
    "wooden": Material.OAK_PLANKS,
    "golden": Material.GOLD_INGOT,
    "iron": Material.IRON_INGOT,
    "diamond": Material.DIAMOND,
    "netherite": Material.NETHERITE_INGOT,
}


def projectile_action(
    *,
    direct_damage: float,
    max_distance: float,
    on_hit: Action,
    trail_particle: Particle,
    trail_count: int,
    speed_per_tick: float,
) -> Action:
    return fx.projectile(
        kind=custom_projectile_kind("gun_round"),
        speed_per_tick=speed_per_tick,
        max_distance=max_distance,
        hit_radius=0.26,
        ignore_caster=True,
        block_collision="stop",
        trail_particle=trail_particle,
        trail_count=trail_count,
        trail_offset=0.025,
        trail_extra=0.0,
        damage_amount=direct_damage,
        damage_policy=DamagePolicy.HOSTILE_DEFAULT,
        on_hit=on_hit,
    )


def heavy_on_hit(spec: GunSpec) -> Action:
    splash_damage = spec.base_damage * 0.5
    return fx.sequence(
        fx.particles_sphere_shell(
            spec.smoke_particle,
            radius=1.2 if spec.key in {"wooden", "golden"} else (1.6 if spec.key in {"iron", "diamond"} else 2.1),
            points=spec.smoke_points,
            count=2,
            offset=0.03,
            extra=0.0,
            at=TargetAnchor.LAST_ENTITY,
        ),
        # Nearby entities around the hit receive splash-half damage.
        fx.for_each_target(
            mode=ForEachMode.EACH,
            origin_at=TargetAnchor.LAST_ENTITY,
            targeter=fx.target_sphere(radius=spec.aoe_radius, ignore_caster=True),
            then=fx.damage(splash_damage, policy=DamagePolicy.HOSTILE_DEFAULT),
        ),
    )


def quick_on_hit(spec: GunSpec) -> Action:
    return fx.particles_sphere_shell(
        spec.smoke_particle,
        radius=1.0 if spec.key in {"wooden", "golden"} else (1.3 if spec.key in {"iron", "diamond"} else 1.7),
        points=16 if spec.key in {"wooden", "golden"} else (20 if spec.key in {"iron", "diamond"} else 24),
        count=1,
        offset=0.02,
        extra=0.0,
        at=TargetAnchor.LAST_ENTITY,
    )


def heavy_ability(ctx: BuildContext, spec: GunSpec):
    return (
        ability(
            ctx,
            symbol=f"ability.guns.{spec.key}.heavy_shot",
            name=f"{spec.label} Gun Shot",
            action=fx.sequence(
                fx.sound(Sound.ENTITY_BLAZE_SHOOT, volume=0.8, pitch=1.1),
                projectile_action(
                    direct_damage=spec.base_damage,
                    max_distance=spec.max_distance,
                    on_hit=heavy_on_hit(spec),
                    trail_particle=Particle.SMOKE,
                    trail_count=2,
                    speed_per_tick=1.45,
                ),
            ),
        )
        .cooldown(20)
    )


def quick_ability(ctx: BuildContext, spec: GunSpec):
    quick_damage = spec.base_damage * 0.5
    return (
        ability(
            ctx,
            symbol=f"ability.guns.{spec.key}.quick_shot",
            name=f"{spec.label} Quick Gun Shot",
            action=fx.sequence(
                fx.sound(Sound.ENTITY_BLAZE_SHOOT, volume=0.7, pitch=1.35),
                projectile_action(
                    direct_damage=quick_damage,
                    max_distance=spec.max_distance * 0.5,
                    on_hit=quick_on_hit(spec),
                    trail_particle=Particle.SMOKE,
                    trail_count=1,
                    speed_per_tick=1.75,
                ),
            ),
        )
        .cooldown(5)
    )


def heavy_item(ctx: BuildContext, spec: GunSpec):
    return (
        item.create(
            ctx,
            symbol=f"item.guns.{spec.key}.heavy",
            name=f"<gold>{spec.label} Smoke Gun</gold>",
            material=spec.material,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            f"<gray>Damage: {spec.base_damage:.0f}</gray>",
            f"<gray>Range: {spec.max_distance:.0f}</gray>",
            "<gray>On-hit: smoke burst + 50% splash</gray>",
            "<gray>Cooldown: 1.0s</gray>",
        )
        .bind(bind.use(Ref(f"ability.guns.{spec.key}.heavy_shot"), click=ItemClick.RIGHT_CLICK, cancel_event=True))
    )


def quick_item(ctx: BuildContext, spec: GunSpec):
    quick_damage = spec.base_damage * 0.5
    quick_range = spec.max_distance * 0.5
    return (
        item.create(
            ctx,
            symbol=f"item.guns.{spec.key}.quick",
            name=f"<yellow>{spec.label} Quick Gun</yellow>",
            material=spec.material,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            f"<gray>Damage: {quick_damage:.0f}</gray>",
            f"<gray>Range: {quick_range:.0f}</gray>",
            "<gray>On-hit: smoke burst (no splash)</gray>",
            "<gray>Cooldown: 0.25s</gray>",
        )
        .bind(bind.use(Ref(f"ability.guns.{spec.key}.quick_shot"), click=ItemClick.RIGHT_CLICK, cancel_event=True))
    )


def heavy_recipe(ctx: BuildContext, spec: GunSpec):
    return (
        recipe.for_item(
            ctx,
            Ref(f"item.guns.{spec.key}.heavy"),
            symbol=f"recipe.guns.{spec.key}.heavy",
            name=f"{spec.label} Smoke Gun Recipe",
            pattern=[
                "FTR",
                "SHH",
                "S  ",
            ],
            keys=recipe.keys()
            .slot("F", recipe_ingredient.material(Material.FLINT))
            .slot("T", recipe_ingredient.material(HEAVY_COMPONENTS[spec.key]))
            .slot("R", recipe_ingredient.material(Material.REDSTONE))
            .slot("S", recipe_ingredient.material(Material.STICK))
            .slot("H", recipe_ingredient.material(spec.material)),
        )
        .discovery(show_in_book=True, unlock_on_craft=True, hidden=False)
    )


def quick_recipe(ctx: BuildContext, spec: GunSpec):
    return (
        recipe.for_item(
            ctx,
            Ref(f"item.guns.{spec.key}.quick"),
            symbol=f"recipe.guns.{spec.key}.quick",
            name=f"{spec.label} Quick Gun Recipe",
            pattern=[
                " R ",
                "SHS",
                " S ",
            ],
            keys=recipe.keys()
            .slot("R", recipe_ingredient.material(Material.REDSTONE))
            .slot("S", recipe_ingredient.material(Material.STICK))
            .slot("H", recipe_ingredient.material(spec.material)),
        )
        .discovery(show_in_book=True, unlock_on_craft=True, hidden=False)
    )


def build_pack():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)
    for spec in BASE_GUNS:
        pack.add(
            heavy_ability(ctx, spec),
            quick_ability(ctx, spec),
            heavy_item(ctx, spec),
            quick_item(ctx, spec),
            heavy_recipe(ctx, spec),
            quick_recipe(ctx, spec),
        )
    return pack


def main() -> None:
    out_dir = "server/plugins/DungeonsReborn"
    paths = build_pack().export(out_dir)
    print("exported files:")
    for path in paths:
        if any(prefix in path for prefix in ("/effects/abilities/ability_guns_", "/effects/items/item_guns_", "/recipes/recipe_guns_")):
            print(path)


if __name__ == "__main__":
    main()
