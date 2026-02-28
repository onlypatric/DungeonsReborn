"""Template showcasing edible/consumable item authoring in builder v2."""

from __future__ import annotations

from dungeonsreborn_builder.v2 import (
    BuildContext,
    ItemUseAnimation,
    Material,
    PotionEffect,
    Sound,
    consume_fx,
    consume_status,
    item,
    pack_v2,
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    ration = (
        item.create(ctx, symbol="item.showcase.field_ration", name="Field Ration", material=Material.BREAD)
        .edible(
            nutrition=5,
            saturation=5.2,
            consume_seconds=1.2,
            animation=ItemUseAnimation.EAT,
            sound=Sound.ENTITY_GENERIC_EAT,
            has_particles=True,
            cooldown_seconds=2.0,
        )
    )

    tonic = (
        item.create(ctx, symbol="item.showcase.arcane_tonic", name="Arcane Tonic", material=Material.POTION)
        .edible(
            nutrition=1,
            saturation=0.2,
            can_always_eat=True,
            consume_seconds=1.0,
            animation=ItemUseAnimation.DRINK,
            sound=Sound.ENTITY_GENERIC_DRINK,
            effects=[
                consume_fx.play_sound(Sound.ENTITY_PLAYER_LEVELUP),
                consume_fx.apply_status_effects(
                    [
                        consume_status.effect(PotionEffect.SPEED, duration_ticks=160, amplifier=1),
                        consume_status.effect(PotionEffect.REGENERATION, duration_ticks=80, amplifier=0),
                    ],
                    probability=1.0,
                ),
            ],
            cooldown_seconds=3.0,
            cooldown_group="dungeonsreborn:arcane_tonic",
            remainder_material=Material.GLASS_BOTTLE,
            remainder_amount=1,
        )
    )

    purge_draught = (
        item.create(ctx, symbol="item.showcase.purge_draught", name="Purge Draught", material=Material.HONEY_BOTTLE)
        .food(nutrition=2, saturation=0.4, can_always_eat=True)
        .consumable(
            consume_seconds=1.3,
            animation=ItemUseAnimation.DRINK,
            sound=Sound.ENTITY_GENERIC_DRINK,
            effects=[
                consume_fx.clear_all_status_effects(),
                consume_fx.remove_status_effects(PotionEffect.POISON, PotionEffect.WITHER),
            ],
        )
        .use_remainder(Material.GLASS_BOTTLE, amount=1)
    )

    pack.add(ration, tonic, purge_draught)
    return pack


if __name__ == "__main__":
    build_v2().export("./out")
