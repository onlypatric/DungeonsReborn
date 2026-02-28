from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (
    BuildContext,
    BuildValidationError,
    DamagePolicy,
    EntityType,
    MobAiAuthority,
    MobAiIntentType,
    MobAiMovementPolicy,
    MobAiMode,
    MobAiProfile,
    MobAiRuntimeModel,
    MobAiTargetSourceType,
    Ref,
    ability,
    ai_condition,
    fx,
    mob,
)


class V2MobAiV4Tests(unittest.TestCase):
    def test_new_ai_enums_and_profiles_available(self) -> None:
        self.assertEqual(MobAiProfile.SUPPORT.value, "SUPPORT")
        self.assertEqual(MobAiProfile.SCOUT.value, "SCOUT")
        self.assertEqual(MobAiProfile.BERSERKER.value, "BERSERKER")
        self.assertEqual(MobAiMode.FULL_OVERRIDE.value, "FULL_OVERRIDE")
        self.assertEqual(MobAiAuthority.ABILITY_DRIVEN.value, "ABILITY_DRIVEN")
        self.assertEqual(MobAiIntentType.CHASE_AND_CAST.value, "CHASE_AND_CAST")
        self.assertEqual(MobAiRuntimeModel.NATURAL_V1.value, "NATURAL_V1")
        self.assertEqual(MobAiMovementPolicy.PATHFINDER_FIRST.value, "PATHFINDER_FIRST")
        self.assertEqual(MobAiTargetSourceType.LAST_ATTACKER.value, "LAST_ATTACKER")

    def test_ai_v4_serializes_expected_shape(self) -> None:
        ctx = BuildContext(strict=True)
        entry = (
            mob.create(ctx, symbol="mob.test.v4_bat", name="V4 Bat", mob_type=EntityType.BAT)
            .ai_v4(
                engine="V3",
                mode=MobAiMode.FULL_OVERRIDE,
                authority=MobAiAuthority.ABILITY_DRIVEN,
                profile=MobAiProfile.SCOUT,
                aggro_radius=24.0,
                chase_speed=0.52,
            )
            .ai_selector(
                MobAiIntentType.CHASE,
                selector_id="chase",
                priority=10,
                when=ai_condition.has_target(True),
                speed=0.55,
            )
        )
        payload = entry.build()
        self.assertEqual(payload["ai"]["version"], "V4")
        self.assertEqual(payload["ai"]["engine"], "V3")
        self.assertEqual(payload["ai"]["control"]["mode"], "FULL_OVERRIDE")
        self.assertEqual(payload["ai"]["combat"]["authority"], "ABILITY_DRIVEN")
        self.assertEqual(payload["ai"]["profile"], "SCOUT")
        self.assertEqual(payload["ai"]["targeting"]["aggroRadius"], 24.0)
        self.assertEqual(payload["ai"]["navigation"]["chaseSpeed"], 0.52)
        self.assertEqual(payload["ai"]["selectors"][0]["id"], "chase")
        self.assertEqual(payload["ai"]["selectors"][0]["intent"]["type"], "CHASE")

    def test_ai_selector_cast_resolves_ability_ref(self) -> None:
        ctx = BuildContext(strict=True)
        ping = ability(
            ctx,
            symbol="ability.test.v4_ping",
            name="Ping",
            action=fx.damage(2.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        )
        entry = (
            mob.create(ctx, symbol="mob.test.v4_cast", name="V4 Cast", mob_type=EntityType.BAT)
            .ai_v4()
            .ai_selector_cast(
                Ref("ability.test.v4_ping"),
                selector_id="cast",
                priority=5,
                intent=MobAiIntentType.CAST_ONLY,
                cast_cooldown_ticks=12,
            )
        )
        payload = entry.build()
        self.assertEqual(payload["ai"]["selectors"][0]["intent"]["ability"], ping.id)
        self.assertEqual(payload["ai"]["selectors"][0]["intent"]["castCooldownTicks"], 12)

    def test_override_is_hard_blocked_in_strict_mode(self) -> None:
        ctx = BuildContext(strict=True)
        entry = mob.create(ctx, symbol="mob.test.override", name="Override Mob", mob_type=EntityType.BAT)
        with self.assertRaises(BuildValidationError):
            getattr(entry, "override")("ai.version", "V4")

    def test_template_smoke_preview(self) -> None:
        template_path = ROOT / "plugin_builder" / "templates" / "v2_full_override_bat.py"
        spec = importlib.util.spec_from_file_location("v2_full_override_bat", template_path)
        self.assertIsNotNone(spec)
        module = importlib.util.module_from_spec(spec)  # type: ignore[arg-type]
        assert spec is not None and spec.loader is not None
        spec.loader.exec_module(module)
        pack = module.build_v2()
        preview = pack.preview_export()
        self.assertFalse(preview["errors"])
        self.assertEqual(preview["counts"]["abilities"], 1)
        self.assertEqual(preview["counts"]["mobs"], 1)

    def test_runtime_model_policy_and_target_sources_serialize(self) -> None:
        ctx = BuildContext(strict=True)
        entry = (
            mob.create(ctx, symbol="mob.test.v4_natural", name="V4 Natural", mob_type=EntityType.DONKEY)
            .ai_v4(profile=MobAiProfile.PASSIVE)
            .ai_runtime_model(MobAiRuntimeModel.NATURAL_V1)
            .ai_movement_policy(MobAiMovementPolicy.PATHFINDER_FIRST)
            .ai_target_source(MobAiTargetSourceType.LAST_ATTACKER, memory_ticks=80, priority=10)
            .ai_target_source(MobAiTargetSourceType.PROXIMITY_PLAYER, radius=10.0, memory_ticks=30, priority=20)
        )
        payload = entry.build()
        self.assertEqual(payload["ai"]["runtimeModel"], "NATURAL_V1")
        self.assertEqual(payload["ai"]["movementPolicy"], "PATHFINDER_FIRST")
        self.assertEqual(payload["ai"]["targeting"]["sources"][0]["type"], "LAST_ATTACKER")
        self.assertEqual(payload["ai"]["targeting"]["sources"][1]["type"], "PROXIMITY_PLAYER")


if __name__ == "__main__":
    unittest.main()
