from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (
    ConsumeMainHandCost,
    DamagePolicy,
    HealthLteRequirement,
    Material,
    custom_material,
)
from dungeonsreborn_builder.v2.enums import (
    CostType,
    RequirementType,
    coerce_cost_type,
    coerce_damage_policy,
    coerce_material,
    coerce_requirement_type,
)


class V2TypeTests(unittest.TestCase):
    def test_known_enum_values_are_coerced(self) -> None:
        self.assertEqual(coerce_material(Material.STONE, field="test.material"), "STONE")
        self.assertEqual(coerce_damage_policy(DamagePolicy.HOSTILE_DEFAULT, field="test.policy"), "HOSTILE_DEFAULT")

    def test_custom_tokens_are_explicit(self) -> None:
        token = custom_material("NETHERITE_SWORD")
        self.assertEqual(coerce_material(token, field="test.material"), "NETHERITE_SWORD")

    def test_invalid_material_fails_with_hint(self) -> None:
        with self.assertRaises(ValueError):
            coerce_material("STON", field="test.material")

    def test_requirement_type_enum_and_alias_are_coerced(self) -> None:
        self.assertEqual(
            coerce_requirement_type(RequirementType.HEALTH_LTE, field="test.requirement"),
            "health_lte",
        )
        requirement = HealthLteRequirement(15.0)
        self.assertEqual(requirement.to_dict()["type"], "health_lte")

    def test_cost_type_enum_and_alias_are_coerced(self) -> None:
        self.assertEqual(
            coerce_cost_type(CostType.CONSUME_MAIN_HAND, field="test.cost"),
            "consume_main_hand",
        )
        cost = ConsumeMainHandCost(amount=1)
        self.assertEqual(cost.to_dict()["type"], "consume_main_hand")

    def test_invalid_requirement_and_cost_type_fail(self) -> None:
        with self.assertRaises(ValueError):
            coerce_requirement_type("health_lt", field="test.requirement")
        with self.assertRaises(ValueError):
            coerce_cost_type("man", field="test.cost")


if __name__ == "__main__":
    unittest.main()
