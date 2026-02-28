from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (  # noqa: E402
    BuildContext,
    ItemUseAnimation,
    Material,
    PotionEffect,
    Sound,
    consume_fx,
    consume_status,
    item,
)


class V2ItemsEdibleTests(unittest.TestCase):
    def test_edible_serializes_components(self) -> None:
        ctx = BuildContext(strict=True)
        tonic = (
            item.create(ctx, symbol="item.test.tonic", name="Tonic", material=Material.POTION)
            .edible(
                nutrition=1,
                saturation=0.2,
                can_always_eat=True,
                consume_seconds=1.0,
                animation=ItemUseAnimation.DRINK,
                sound=Sound.ENTITY_GENERIC_DRINK,
                has_particles=False,
                cooldown_seconds=2.0,
                cooldown_group="dungeonsreborn:tonic",
                remainder_material=Material.GLASS_BOTTLE,
                remainder_amount=1,
            )
        )

        payload = tonic.build()
        components = payload["item"]["meta"]["components"]

        self.assertEqual(components["food"]["nutrition"], 1)
        self.assertAlmostEqual(components["food"]["saturation"], 0.2)
        self.assertTrue(components["food"]["canAlwaysEat"])
        self.assertEqual(components["consumable"]["consumeSeconds"], 1.0)
        self.assertEqual(components["consumable"]["animation"], "DRINK")
        self.assertEqual(components["consumable"]["sound"], "ENTITY_GENERIC_DRINK")
        self.assertFalse(components["consumable"]["hasConsumeParticles"])
        self.assertEqual(components["use_cooldown"]["seconds"], 2.0)
        self.assertEqual(components["use_cooldown"]["group"], "dungeonsreborn:tonic")
        self.assertEqual(components["use_remainder"]["material"], "GLASS_BOTTLE")
        self.assertEqual(components["use_remainder"]["amount"], 1)

    def test_consume_effect_helpers(self) -> None:
        ctx = BuildContext(strict=True)
        ration = (
            item.create(ctx, symbol="item.test.ration", name="Ration", material=Material.BREAD)
            .consumable(
                effects=[
                    consume_fx.play_sound(Sound.ENTITY_GENERIC_EAT),
                    consume_fx.teleport_randomly(diameter=5.0),
                    consume_fx.remove_status_effects(PotionEffect.POISON, PotionEffect.WITHER),
                    consume_fx.clear_all_status_effects(),
                    consume_fx.apply_status_effects(
                        [consume_status.effect(PotionEffect.SPEED, duration_ticks=60, amplifier=1)],
                        probability=0.5,
                    ),
                ]
            )
        )
        effects = ration.build()["item"]["meta"]["components"]["consumable"]["effects"]
        self.assertEqual(effects[0]["type"], "PLAY_SOUND")
        self.assertEqual(effects[1]["type"], "TELEPORT_RANDOMLY")
        self.assertEqual(effects[2]["type"], "REMOVE_STATUS_EFFECTS")
        self.assertEqual(effects[3]["type"], "CLEAR_ALL_STATUS_EFFECTS")
        self.assertEqual(effects[4]["type"], "APPLY_STATUS_EFFECTS")
        self.assertEqual(effects[4]["probability"], 0.5)

    def test_invalid_animation_rejected(self) -> None:
        ctx = BuildContext(strict=True)
        with self.assertRaises(ValueError):
            item.create(ctx, symbol="item.test.bad", name="Bad", material=Material.APPLE).consumable(
                animation="NOT_REAL"
            )

    def test_consumable_basic_kept_as_compat_shim(self) -> None:
        ctx = BuildContext(strict=True)
        potion = item.create(ctx, symbol="item.test.compat", name="Compat", material=Material.POTION).consumable_basic(
            stack_size=3
        )
        payload = potion.build()
        self.assertEqual(payload["item"]["amount"], 3)
        self.assertEqual(payload["item"]["meta"]["components"]["consumable"]["animation"], "DRINK")
        self.assertEqual(payload["item"]["meta"]["components"]["food"]["nutrition"], 1)


if __name__ == "__main__":
    unittest.main()
