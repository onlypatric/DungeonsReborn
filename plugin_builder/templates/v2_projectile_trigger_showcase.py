"""Projectile trigger showcase pack: multiple bows/crossbows with vanilla + custom lifecycle hooks."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder.v2 import (
    BuildContext,
    CombatEventType,
    ItemClick,
    Material,
    Particle,
    ProjectileFamily,
    Ref,
    Sound,
    ability,
    bind,
    custom_projectile_kind,
    fx,
    item,
    pack_v2,
    recipe,
    recipe_ingredient,
)


def stage_proc(
    ctx: BuildContext,
    *,
    key: str,
    title: str,
    sound: Sound,
    particle: Particle,
    radius: float = 0.9,
    points: int = 12,
    volume: float = 0.6,
    pitch: float = 1.0,
):
    return ability(
        ctx,
        symbol=f"ability.projectiles.showcase.proc.{key}",
        name=title,
        action=fx.sequence(
            fx.sound(sound, volume=volume, pitch=pitch),
            fx.particles_sphere_shell(
                particle,
                radius=radius,
                points=points,
                count=1,
            ),
        ),
    )


def build_pack():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)
    custom_round_kind = custom_projectile_kind("showcase_custom_round")
    cancel_round_kind = custom_projectile_kind("showcase_cancel_round")
    self_aim_kind = custom_projectile_kind("item_projectiles_showcase_self_aim_bow")

    procs = {
        "launch_pre": stage_proc(
            ctx,
            key="launch_pre",
            title="Projectile Launch Pre",
            sound=Sound.ENTITY_BLAZE_SHOOT,
            particle=Particle.CRIT,
            radius=0.55,
            points=10,
            pitch=1.8,
        ),
        "launch": stage_proc(
            ctx,
            key="launch",
            title="Projectile Launch",
            sound=Sound.ENTITY_ARROW_SHOOT,
            particle=Particle.CLOUD,
            radius=0.8,
            points=14,
            pitch=1.25,
        ),
        "travel": stage_proc(
            ctx,
            key="travel",
            title="Projectile Travel Step",
            sound=Sound.ENTITY_BAT_TAKEOFF,
            particle=Particle.SMOKE,
            radius=0.35,
            points=8,
            volume=0.2,
            pitch=1.6,
        ),
        "collide_entity_pre": stage_proc(
            ctx,
            key="collide_entity_pre",
            title="Projectile Collide Entity PRE",
            sound=Sound.BLOCK_CHAIN_HIT,
            particle=Particle.CRIT,
            radius=0.75,
            points=12,
            pitch=1.45,
        ),
        "collide_block_pre": stage_proc(
            ctx,
            key="collide_block_pre",
            title="Projectile Collide Block PRE",
            sound=Sound.BLOCK_STONE_HIT,
            particle=Particle.SMOKE,
            radius=0.7,
            points=12,
            pitch=1.15,
        ),
        "hit_entity": stage_proc(
            ctx,
            key="hit_entity",
            title="Projectile Hit Entity",
            sound=Sound.ENTITY_PLAYER_ATTACK_STRONG,
            particle=Particle.SWEEP_ATTACK,
            radius=1.1,
            points=18,
            pitch=1.0,
        ),
        "hit_block": stage_proc(
            ctx,
            key="hit_block",
            title="Projectile Hit Block",
            sound=Sound.BLOCK_STONE_BREAK,
            particle=Particle.CLOUD,
            radius=0.9,
            points=14,
            pitch=0.9,
        ),
        "pierce": stage_proc(
            ctx,
            key="pierce",
            title="Projectile Pierce",
            sound=Sound.ITEM_TRIDENT_HIT,
            particle=Particle.CRIT,
            radius=0.95,
            points=14,
            pitch=1.2,
        ),
        "bounce": stage_proc(
            ctx,
            key="bounce",
            title="Projectile Bounce",
            sound=Sound.ENTITY_PLAYER_ATTACK_WEAK,
            particle=Particle.ITEM_SLIME,
            radius=0.95,
            points=16,
            pitch=1.1,
        ),
        "stuck": stage_proc(
            ctx,
            key="stuck",
            title="Projectile Stuck",
            sound=Sound.BLOCK_WOOD_PLACE,
            particle=Particle.DUST_PLUME,
            radius=0.65,
            points=10,
            pitch=0.85,
        ),
        "expire": stage_proc(
            ctx,
            key="expire",
            title="Projectile Expire",
            sound=Sound.BLOCK_CANDLE_EXTINGUISH,
            particle=Particle.SMOKE,
            radius=0.7,
            points=12,
            pitch=0.8,
        ),
        "deflect": stage_proc(
            ctx,
            key="deflect",
            title="Projectile Deflect",
            sound=Sound.ITEM_SHIELD_BLOCK,
            particle=Particle.ELECTRIC_SPARK,
            radius=0.9,
            points=14,
            pitch=1.4,
        ),
        "blocked_shield": stage_proc(
            ctx,
            key="blocked_shield",
            title="Projectile Blocked Shield",
            sound=Sound.ITEM_SHIELD_BLOCK,
            particle=Particle.WAX_OFF,
            radius=0.85,
            points=12,
            pitch=0.95,
        ),
        "cancelled": stage_proc(
            ctx,
            key="cancelled",
            title="Projectile Cancelled",
            sound=Sound.BLOCK_BEACON_DEACTIVATE,
            particle=Particle.WHITE_SMOKE,
            radius=0.8,
            points=12,
            pitch=1.4,
        ),
    }

    vanilla_hooks = ability(
        ctx,
        symbol="ability.projectiles.showcase.vanilla_hooks",
        name="Vanilla Projectile Hooks",
        action=fx.sequence(),
    )
    vanilla_common = {
        "projectile_family": ProjectileFamily.VANILLA,
        "projectile_types": ["ARROW", "SPECTRAL_ARROW"],
        "shooter_is_player": True,
    }
    vanilla_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_LAUNCH_PRE,
        ability=procs["launch_pre"],
        cancel_event=False,
        **vanilla_common,
    )
    vanilla_hooks.on_projectile_launch(ability=procs["launch"], **vanilla_common)
    vanilla_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_TRAVEL_STEP,
        ability=procs["travel"],
        cooldown_ticks=4,
        **vanilla_common,
    )
    vanilla_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_COLLIDE_ENTITY_PRE,
        ability=procs["collide_entity_pre"],
        cancel_event=False,
        **vanilla_common,
    )
    vanilla_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_COLLIDE_BLOCK_PRE,
        ability=procs["collide_block_pre"],
        cancel_event=False,
        **vanilla_common,
    )
    vanilla_hooks.on_projectile_hit(ability=procs["hit_entity"], **vanilla_common)
    vanilla_hooks.on_projectile_hit(ability=procs["hit_block"], block=True, **vanilla_common)
    vanilla_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_BLOCKED_SHIELD,
        ability=procs["blocked_shield"],
        **vanilla_common,
    )

    crossbow_hooks = ability(
        ctx,
        symbol="ability.projectiles.showcase.crossbow_hooks",
        name="Crossbow Projectile Hooks",
        action=fx.sequence(),
    )
    crossbow_common = dict(vanilla_common)
    crossbow_common["shot_from_crossbow"] = True
    crossbow_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_LAUNCH_PRE,
        ability=procs["launch_pre"],
        cancel_event=False,
        **crossbow_common,
    )
    crossbow_hooks.on_projectile_launch(ability=procs["launch"], **crossbow_common)
    crossbow_hooks.on_projectile_hit(ability=procs["hit_entity"], **crossbow_common)
    crossbow_hooks.on_projectile_hit(ability=procs["hit_block"], block=True, **crossbow_common)

    custom_hooks = ability(
        ctx,
        symbol="ability.projectiles.showcase.custom_hooks",
        name="Custom Projectile Hooks",
        action=fx.sequence(),
    )
    custom_common = {
        "projectile_family": ProjectileFamily.CUSTOM,
        "projectile_kind": custom_round_kind,
        "shooter_is_player": True,
    }
    custom_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_LAUNCH_PRE,
        ability=procs["launch_pre"],
        cancel_event=False,
        **custom_common,
    )
    custom_hooks.on_projectile_launch(ability=procs["launch"], **custom_common)
    custom_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_TRAVEL_STEP,
        ability=procs["travel"],
        cooldown_ticks=2,
        **custom_common,
    )
    custom_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_COLLIDE_ENTITY_PRE,
        ability=procs["collide_entity_pre"],
        cancel_event=False,
        **custom_common,
    )
    custom_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_COLLIDE_BLOCK_PRE,
        ability=procs["collide_block_pre"],
        cancel_event=False,
        **custom_common,
    )
    custom_hooks.on_projectile_hit(ability=procs["hit_entity"], **custom_common)
    custom_hooks.on_projectile_hit(ability=procs["hit_block"], block=True, **custom_common)
    custom_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_PIERCE,
        ability=procs["pierce"],
        **custom_common,
    )
    custom_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_BOUNCE,
        ability=procs["bounce"],
        **custom_common,
    )
    custom_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_STUCK,
        ability=procs["stuck"],
        **custom_common,
    )
    custom_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_EXPIRE,
        ability=procs["expire"],
        **custom_common,
    )
    custom_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_DEFLECT,
        ability=procs["deflect"],
        **custom_common,
    )

    cancel_hooks = ability(
        ctx,
        symbol="ability.projectiles.showcase.cancel_hooks",
        name="Projectile Cancel Hooks",
        action=fx.sequence(),
    )
    cancel_common = {
        "projectile_family": ProjectileFamily.CUSTOM,
        "projectile_kind": cancel_round_kind,
        "shooter_is_player": True,
    }
    cancel_hooks.on_projectile_pre(
        CombatEventType.ON_PROJECTILE_LAUNCH_PRE,
        ability=procs["launch_pre"],
        cancel_event=True,
        **cancel_common,
    )
    cancel_hooks.on_projectile(
        CombatEventType.ON_PROJECTILE_CANCELLED,
        ability=procs["cancelled"],
        **cancel_common,
    )

    self_aim_launch = ability(
        ctx,
        symbol="ability.projectiles.showcase.self_aim_launch",
        name="Self Aim Arrow Launch",
        action=fx.sequence(
            fx.sound(Sound.ENTITY_ARROW_HIT_PLAYER, volume=0.35, pitch=1.35),
            fx.projectile_auto_aim_nearest(
                radius=26.0,
                y_offset=-0.1,
                include_players=False,
                include_mobs=True,
                require_line_of_sight=False,
                ignore_caster=True,
            ),
        ),
    )

    self_aim_hooks = ability(
        ctx,
        symbol="ability.projectiles.showcase.self_aim_hooks",
        name="Self Aim Arrow Hooks",
        action=fx.sequence(),
    )
    self_aim_hooks.on_projectile_launch(
        ability=self_aim_launch,
        projectile_family=ProjectileFamily.VANILLA,
        projectile_types=["ARROW", "SPECTRAL_ARROW"],
        projectile_kind=self_aim_kind,
        shooter_is_player=True,
    )

    custom_bow_launch = ability(
        ctx,
        symbol="ability.projectiles.showcase.custom_bow_launch",
        name="Custom Bow Launch",
        action=fx.projectile(
            kind=custom_round_kind,
            speed_per_tick=1.45,
            max_distance=26.0,
            hit_radius=0.28,
            max_pierces=1,
            block_collision="bounce",
            travel_step_enabled=True,
            travel_step_interval_ticks=2,
            trail_particle=Particle.SMOKE,
            trail_count=2,
            trail_offset=0.025,
            damage_amount=5.0,
            on_hit=fx.particles_sphere_shell(Particle.SMOKE, radius=1.15, points=16, count=2),
        ),
    ).cooldown(16)

    custom_crossbow_launch = ability(
        ctx,
        symbol="ability.projectiles.showcase.custom_crossbow_launch",
        name="Custom Crossbow Launch",
        action=fx.projectile(
            kind=custom_round_kind,
            speed_per_tick=1.8,
            max_distance=34.0,
            hit_radius=0.26,
            max_pierces=2,
            block_collision="bounce",
            travel_step_enabled=True,
            travel_step_interval_ticks=2,
            trail_particle=Particle.CRIT,
            trail_count=1,
            trail_offset=0.02,
            damage_amount=6.0,
            on_hit=fx.particles_sphere_shell(Particle.CLOUD, radius=1.25, points=18, count=2),
        ),
    ).cooldown(14)

    cancel_crossbow_launch = ability(
        ctx,
        symbol="ability.projectiles.showcase.cancel_crossbow_launch",
        name="Cancel Crossbow Launch",
        action=fx.projectile(
            kind=cancel_round_kind,
            speed_per_tick=1.6,
            max_distance=28.0,
            hit_radius=0.25,
            block_collision="stop",
            trail_particle=Particle.WHITE_SMOKE,
            trail_count=1,
            damage_amount=4.0,
        ),
    ).cooldown(10)

    vanilla_bow = (
        item.create(
            ctx,
            symbol="item.projectiles.showcase.vanilla_bow",
            name="<green>Showcase Bow (Vanilla)</green>",
            material=Material.BOW,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            "<gray>Use normal arrows.</gray>",
            "<gray>Demonstrates vanilla projectile lifecycle hooks.</gray>",
        )
    )

    vanilla_crossbow = (
        item.create(
            ctx,
            symbol="item.projectiles.showcase.vanilla_crossbow",
            name="<aqua>Showcase Crossbow (Vanilla)</aqua>",
            material=Material.CROSSBOW,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            "<gray>Use normal arrows/fireworks.</gray>",
            "<gray>Demonstrates crossbow-specific vanilla hooks.</gray>",
        )
    )

    custom_bow = (
        item.create(
            ctx,
            symbol="item.projectiles.showcase.custom_bow",
            name="<gold>Showcase Bow (Custom Projectile)</gold>",
            material=Material.BOW,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            "<gray>Right-click shoots a custom bouncing projectile.</gray>",
            "<gray>Triggers custom lifecycle: launch, step, hit, pierce, bounce, expire.</gray>",
        )
        .bind(bind.use(Ref("ability.projectiles.showcase.custom_bow_launch"), click=ItemClick.SHOOT, cancel_event=True))
    )

    custom_crossbow = (
        item.create(
            ctx,
            symbol="item.projectiles.showcase.custom_crossbow",
            name="<yellow>Showcase Crossbow (Custom Projectile)</yellow>",
            material=Material.CROSSBOW,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            "<gray>Right-click shoots a faster custom projectile.</gray>",
            "<gray>Demonstrates pierce + bounce + deflect/stuck cases.</gray>",
        )
        .bind(
            bind.use(
                Ref("ability.projectiles.showcase.custom_crossbow_launch"),
                click=ItemClick.SHOOT,
                cancel_event=True,
            )
        )
    )

    cancel_crossbow = (
        item.create(
            ctx,
            symbol="item.projectiles.showcase.cancel_crossbow",
            name="<red>Showcase Crossbow (Cancel Demo)</red>",
            material=Material.CROSSBOW,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            "<gray>Right-click launch is cancelled by ON_PROJECTILE_LAUNCH_PRE.</gray>",
            "<gray>Demonstrates cancellable pre-triggers and ON_PROJECTILE_CANCELLED.</gray>",
        )
        .bind(
            bind.use(
                Ref("ability.projectiles.showcase.cancel_crossbow_launch"),
                click=ItemClick.SHOOT,
                cancel_event=True,
            )
        )
    )

    self_aim_bow = (
        item.create(
            ctx,
            symbol="item.projectiles.showcase.self_aim_bow",
            name="<light_purple>Showcase Bow (Self-Aim Arrow)</light_purple>",
            material=Material.BOW,
        )
        .weapon_basic(attack_damage=0.0, attack_speed=4.0)
        .lore(
            "<gray>Vanilla arrow is auto-aimed on release.</gray>",
            "<gray>Tracks nearest mob within range.</gray>",
        )
    )

    bow_recipe = recipe.for_item(
        ctx,
        Ref("item.projectiles.showcase.vanilla_bow"),
        symbol="recipe.projectiles.showcase.vanilla_bow",
        name="Showcase Bow",
        pattern=[" ST", "SRT", " ST"],
        keys=recipe.keys()
        .slot("S", recipe_ingredient.material(Material.STICK))
        .slot("T", recipe_ingredient.material(Material.STRING))
        .slot("R", recipe_ingredient.material(Material.REDSTONE)),
    ).discovery(show_in_book=True, unlock_on_craft=True, hidden=False)

    crossbow_recipe = recipe.for_item(
        ctx,
        Ref("item.projectiles.showcase.vanilla_crossbow"),
        symbol="recipe.projectiles.showcase.vanilla_crossbow",
        name="Showcase Crossbow",
        pattern=["IRI", "RBR", " I "],
        keys=recipe.keys()
        .slot("I", recipe_ingredient.material(Material.IRON_INGOT))
        .slot("R", recipe_ingredient.material(Material.REDSTONE))
        .slot("B", recipe_ingredient.material(Material.BOW)),
    ).discovery(show_in_book=True, unlock_on_craft=True, hidden=False)

    custom_bow_recipe = recipe.for_item(
        ctx,
        Ref("item.projectiles.showcase.custom_bow"),
        symbol="recipe.projectiles.showcase.custom_bow",
        name="Custom Projectile Bow",
        pattern=["SBT", "RER", " T "],
        keys=recipe.keys()
        .slot("S", recipe_ingredient.material(Material.SLIME_BALL))
        .slot("B", recipe_ingredient.material(Material.BOW))
        .slot("T", recipe_ingredient.material(Material.STRING))
        .slot("R", recipe_ingredient.material(Material.REDSTONE))
        .slot("E", recipe_ingredient.material(Material.ENDER_PEARL)),
    ).discovery(show_in_book=True, unlock_on_craft=True, hidden=False)

    custom_crossbow_recipe = recipe.for_item(
        ctx,
        Ref("item.projectiles.showcase.custom_crossbow"),
        symbol="recipe.projectiles.showcase.custom_crossbow",
        name="Custom Projectile Crossbow",
        pattern=["QIQ", "RCR", " P "],
        keys=recipe.keys()
        .slot("Q", recipe_ingredient.material(Material.QUARTZ))
        .slot("I", recipe_ingredient.material(Material.IRON_INGOT))
        .slot("R", recipe_ingredient.material(Material.REDSTONE))
        .slot("C", recipe_ingredient.material(Material.CROSSBOW))
        .slot("P", recipe_ingredient.material(Material.PRISMARINE_SHARD)),
    ).discovery(show_in_book=True, unlock_on_craft=True, hidden=False)

    cancel_crossbow_recipe = recipe.for_item(
        ctx,
        Ref("item.projectiles.showcase.cancel_crossbow"),
        symbol="recipe.projectiles.showcase.cancel_crossbow",
        name="Cancel Demo Crossbow",
        pattern=["OIO", "RCR", " O "],
        keys=recipe.keys()
        .slot("O", recipe_ingredient.material(Material.OBSIDIAN))
        .slot("I", recipe_ingredient.material(Material.IRON_INGOT))
        .slot("R", recipe_ingredient.material(Material.REDSTONE))
        .slot("C", recipe_ingredient.material(Material.CROSSBOW)),
    ).discovery(show_in_book=True, unlock_on_craft=True, hidden=False)

    self_aim_bow_recipe = recipe.for_item(
        ctx,
        Ref("item.projectiles.showcase.self_aim_bow"),
        symbol="recipe.projectiles.showcase.self_aim_bow",
        name="Self-Aim Bow",
        pattern=["ERE", "RBR", "ETE"],
        keys=recipe.keys()
        .slot("E", recipe_ingredient.material(Material.ENDER_PEARL))
        .slot("R", recipe_ingredient.material(Material.REDSTONE))
        .slot("B", recipe_ingredient.material(Material.BOW))
        .slot("T", recipe_ingredient.material(Material.SPECTRAL_ARROW)),
    ).discovery(show_in_book=True, unlock_on_craft=True, hidden=False)

    pack.add(
        *procs.values(),
        vanilla_hooks,
        crossbow_hooks,
        custom_hooks,
        cancel_hooks,
        self_aim_launch,
        self_aim_hooks,
        custom_bow_launch,
        custom_crossbow_launch,
        cancel_crossbow_launch,
        vanilla_bow,
        vanilla_crossbow,
        custom_bow,
        custom_crossbow,
        cancel_crossbow,
        self_aim_bow,
        bow_recipe,
        crossbow_recipe,
        custom_bow_recipe,
        custom_crossbow_recipe,
        cancel_crossbow_recipe,
        self_aim_bow_recipe,
    )
    return pack


def main() -> None:
    out_dir = "server/plugins/DungeonsReborn"
    paths = build_pack().export(out_dir)
    print("exported files:")
    for path in paths:
        if any(token in path for token in ("/ability_projectiles_showcase_", "/item_projectiles_showcase_", "/recipe_projectiles_showcase_")):
            print(path)


if __name__ == "__main__":
    main()
