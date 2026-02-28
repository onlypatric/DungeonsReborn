from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (
    BuildContext,
    CombatEventType,
    DamagePolicy,
    Particle,
    ProjectileFamily,
    ability,
    custom_projectile_kind,
    fx,
)


class V2ProjectileTriggerTests(unittest.TestCase):
    def test_cancel_pre_trigger_can_skip_ability(self) -> None:
        ctx = BuildContext(strict=True)
        gate = ability(ctx, symbol="ability.test.gate", name="Gate", action=fx.sequence())
        gate.on_projectile_pre(
            CombatEventType.ON_PROJECTILE_LAUNCH_PRE,
            cancel_event=True,
            projectile_family=ProjectileFamily.VANILLA,
            shooter_is_player=True,
        )
        payload = gate.build()
        self.assertIn("triggers", payload)
        trigger = payload["triggers"][0]
        self.assertEqual(trigger["event"], "ON_PROJECTILE_LAUNCH_PRE")
        self.assertTrue(trigger["cancelEvent"])
        self.assertNotIn("ability", trigger)
        self.assertEqual(trigger["filters"]["projectileFamily"], ["VANILLA"])
        self.assertTrue(trigger["filters"]["shooterIsPlayer"])

    def test_projectile_hit_trigger_serializes_filters(self) -> None:
        ctx = BuildContext(strict=True)
        proc = ability(
            ctx,
            symbol="ability.test.proc",
            name="Projectile Proc",
            action=fx.damage(3.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        )
        driver = ability(ctx, symbol="ability.test.driver", name="Driver", action=fx.sequence())
        driver.on_projectile_hit(
            ability=proc,
            projectile_types=["ARROW", "SPECTRAL_ARROW"],
            projectile_kind=custom_projectile_kind("smoke_round"),
            distance_min=4.0,
            speed_max=3.5,
            hit_block_tags=["stone"],
        )
        trigger = driver.build()["triggers"][0]
        self.assertEqual(trigger["event"], "ON_PROJECTILE_HIT_ENTITY")
        self.assertEqual(trigger["ability"], proc.id)
        self.assertEqual(trigger["filters"]["projectileType"], ["ARROW", "SPECTRAL_ARROW"])
        self.assertEqual(trigger["filters"]["projectileKind"], ["smoke_round"])
        self.assertEqual(trigger["filters"]["distanceMin"], 4.0)
        self.assertEqual(trigger["filters"]["speedMax"], 3.5)
        self.assertEqual(trigger["filters"]["hitBlockTag"], ["stone"])

    def test_projectile_action_shape_matches_runtime(self) -> None:
        action = fx.projectile(
            kind=custom_projectile_kind("smoke_round"),
            speed_per_tick=1.5,
            max_distance=30.0,
            hit_radius=0.3,
            block_collision="pass_through",
            max_pierces=2,
            travel_step_enabled=True,
            travel_step_interval_ticks=2,
            trail_particle=Particle.SMOKE,
            trail_count=2,
            damage_amount=6.0,
            on_hit=fx.particles_sphere_shell(Particle.SMOKE, radius=1.1, points=16),
        ).to_dict()
        self.assertEqual(action["type"], "projectile")
        self.assertEqual(action["kind"], "smoke_round")
        self.assertEqual(action["speedPerTick"], 1.5)
        self.assertEqual(action["maxDistance"], 30.0)
        self.assertEqual(action["hitRadius"], 0.3)
        self.assertEqual(action["blockCollision"], "PASS_THROUGH")
        self.assertEqual(action["maxPierces"], 2)
        self.assertTrue(action["travelStepEnabled"])
        self.assertEqual(action["travelStepIntervalTicks"], 2)
        self.assertIn("trail", action)
        self.assertIn("damage", action)
        self.assertIn("onHit", action)

    def test_auto_trigger_ids_are_deterministic(self) -> None:
        ctx = BuildContext(strict=True)
        proc = ability(ctx, symbol="ability.test.proc2", name="Proc2", action=fx.damage(2.0))
        owner = ability(ctx, symbol="ability.test.owner", name="Owner", action=fx.sequence())
        owner.on_projectile_hit(ability=proc)
        owner.on_projectile_hit(ability=proc, block=True)
        triggers = owner.build()["triggers"]
        self.assertEqual(triggers[0]["id"], f"{owner.id}_on_projectile_hit_entity_1")
        self.assertEqual(triggers[1]["id"], f"{owner.id}_on_projectile_hit_block_2")


if __name__ == "__main__":
    unittest.main()
