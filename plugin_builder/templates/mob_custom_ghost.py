"""Custom ghost mob export (zombie base + textured ghost head) for local test server."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder import (
    Action,
    AbilityBuilder,
    EntityType,
    EffectsExporter,
    Item,
    ItemExporter,
    Material,
    Mob,
    MobAiGoalSpec,
    MobAiGoalType,
    MobAiProfile,
    MobExporter,
    MobSoundSpec,
    Particle,
    Sound,
    auto_ability_id,
    for_each_target,
    particles_sphere_shell,
    sequence,
    sound,
    skull_texture,
    targeter_context_target,
)

GHOST_HEAD_TEXTURE = (
    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzExZGU5NmMz"
    "ZTdiNTJmYTVlNGM3OTRiNGJlMTRhMDVmYzY3Njk0Y2E1ZWZmOWM5ZWI4YmFhYzY3MmQyMWMwOSJ9fX0="
)
GHOST_HEAD_ITEM_ID = "item_ghost_head_custom"
GHOST_MOB_ID = "mob_custom_ghost"
GHOST_INVIS_ABILITY_ID = auto_ability_id("ability", GHOST_MOB_ID, "invisibility")
GHOST_HIT_SMOKE_ABILITY_ID = auto_ability_id("ability", GHOST_MOB_ID, "hit_smoke")
GHOST_HURT_SOUND_ABILITY_ID = auto_ability_id("ability", GHOST_MOB_ID, "hurt_sound")
GHOST_TARGET_SOUND_ABILITY_ID = auto_ability_id("ability", GHOST_MOB_ID, "target_sound")
GHOST_IDLE_SOUND_ABILITY_ID = auto_ability_id("ability", GHOST_MOB_ID, "idle_sound")
GHOST_DESPAWN_SOUND_ABILITY_ID = auto_ability_id("ability", GHOST_MOB_ID, "despawn_sound")


def build_ghost_head_item():
    return (
        Item(GHOST_HEAD_ITEM_ID)
        .material(Material.PLAYER_HEAD)
        .display_name("<aqua>Ghost Visage</aqua>")
        .meta_skull(skull_texture(GHOST_HEAD_TEXTURE))
    )


def build_ghost_mob():
    return (
        Mob(GHOST_MOB_ID)
        .advanced(True)
        .mob_type(EntityType.ZOMBIE)
        .name("<aqua>Restless Ghost</aqua>")
        .show_name(True)
        .stats(health=26, damage=4, armor=1, speed=0.30)
        .ai_profile_v3(
            MobAiProfile.AGGRESSIVE,
            aggro_radius=18.0,
            chase_speed=0.32,
            open_doors=False,
            call_for_help_radius=0.0,
        )
        .ai_selector(
            "ghost_hunt_player",
            base_score=110,
            actions=[MobAiGoalSpec(MobAiGoalType.CHASE, priority=10, speed=0.32)],
        )
        .spawn_sound(MobSoundSpec(sound=Sound.ENTITY_ENDERMAN_TELEPORT, volume=0.65, pitch=0.75))
        .death_sound(MobSoundSpec(sound=Sound.ENTITY_PHANTOM_DEATH, volume=0.9, pitch=0.55))
        .head({"itemId": GHOST_HEAD_ITEM_ID})
        .override("silent", True)
        .override("events.onSpawnTick.ability", GHOST_INVIS_ABILITY_ID)
        .override("events.onSpawnTick.intervalTicks", 40)
        .override("events.onHit", GHOST_HIT_SMOKE_ABILITY_ID)
        .override("events.onHurt", GHOST_HURT_SOUND_ABILITY_ID)
        .override("events.onTarget", GHOST_TARGET_SOUND_ABILITY_ID)
        .override("events.onIdle.ability", GHOST_IDLE_SOUND_ABILITY_ID)
        .override("events.onIdle.intervalTicks", 70)
        .override("events.onDespawn", GHOST_DESPAWN_SOUND_ABILITY_ID)
    )


def build_ghost_invisibility_ability():
    return (
        AbilityBuilder(GHOST_INVIS_ABILITY_ID)
        .name("<gray>Ghost Veil</gray>")
        .description("Keeps the ghost permanently invisible.")
        .action(
            for_each_target(
                {"type": "self"},
                Action(
                    "potion",
                    {
                        "effect": "invisibility",
                        "durationTicks": 120,
                        "amplifier": 0,
                        "ambient": True,
                        "particles": False,
                        "icon": False,
                    },
                ),
            )
        )
        .build()
    )


def build_ghost_hit_smoke_ability():
    return (
        AbilityBuilder(GHOST_HIT_SMOKE_ABILITY_ID)
        .name("<gray>Ghost Impact Smoke</gray>")
        .description("Creates a short gray smoke sphere on the hit target.")
        .action(
            for_each_target(
                mode="first",
                targeter_spec=targeter_context_target("mob_target"),
                then=sequence(
                    sound(Sound.ENTITY_PHANTOM_BITE, volume=0.8, pitch=0.9),
                    particles_sphere_shell(
                        Particle.CLOUD,
                        radius=1.15,
                        points=24,
                        count=1,
                        offset=0.02,
                        extra=0.0,
                        at="last_entity",
                    ),
                ),
            )
        )
        .build()
    )


def build_sound_ability(ability_id: str, name: str, sound_id: Sound, volume: float, pitch: float):
    return (
        AbilityBuilder(ability_id)
        .name(name)
        .action(sound(sound_id, volume=volume, pitch=pitch))
        .build()
    )


def main() -> None:
    ability_exporter = EffectsExporter("server/plugins/DungeonsReborn/effects/abilities")
    item_exporter = ItemExporter("server/plugins/DungeonsReborn/effects/items")
    mob_exporter = MobExporter("server/plugins/DungeonsReborn/mobs")
    invis_path = ability_exporter.write_ability(
        build_ghost_invisibility_ability(), filename=f"{GHOST_INVIS_ABILITY_ID}.yml"
    )
    smoke_path = ability_exporter.write_ability(
        build_ghost_hit_smoke_ability(), filename=f"{GHOST_HIT_SMOKE_ABILITY_ID}.yml"
    )
    hurt_sound_path = ability_exporter.write_ability(
        build_sound_ability(
            GHOST_HURT_SOUND_ABILITY_ID,
            "<gray>Ghost Wail</gray>",
            Sound.ENTITY_GHAST_HURT,
            0.55,
            1.65,
        ),
        filename=f"{GHOST_HURT_SOUND_ABILITY_ID}.yml",
    )
    target_sound_path = ability_exporter.write_ability(
        build_sound_ability(
            GHOST_TARGET_SOUND_ABILITY_ID,
            "<gray>Ghost Lock</gray>",
            Sound.ENTITY_ENDERMAN_STARE,
            0.7,
            0.6,
        ),
        filename=f"{GHOST_TARGET_SOUND_ABILITY_ID}.yml",
    )
    idle_sound_path = ability_exporter.write_ability(
        build_sound_ability(
            GHOST_IDLE_SOUND_ABILITY_ID,
            "<gray>Ghost Whisper</gray>",
            Sound.AMBIENT_CAVE,
            0.35,
            1.25,
        ),
        filename=f"{GHOST_IDLE_SOUND_ABILITY_ID}.yml",
    )
    despawn_sound_path = ability_exporter.write_ability(
        build_sound_ability(
            GHOST_DESPAWN_SOUND_ABILITY_ID,
            "<gray>Ghost Fade</gray>",
            Sound.ENTITY_PHANTOM_AMBIENT,
            0.4,
            0.5,
        ),
        filename=f"{GHOST_DESPAWN_SOUND_ABILITY_ID}.yml",
    )
    item_path = item_exporter.write_item(build_ghost_head_item(), filename=f"{GHOST_HEAD_ITEM_ID}.yml")
    mob_path = mob_exporter.write_batch([build_ghost_mob()], filename=f"{GHOST_MOB_ID}.yml")
    print(f"exported: {invis_path}")
    print(f"exported: {smoke_path}")
    print(f"exported: {hurt_sound_path}")
    print(f"exported: {target_sound_path}")
    print(f"exported: {idle_sound_path}")
    print(f"exported: {despawn_sound_path}")
    print(f"exported: {item_path}")
    print(f"exported: {mob_path}")


if __name__ == "__main__":
    main()
