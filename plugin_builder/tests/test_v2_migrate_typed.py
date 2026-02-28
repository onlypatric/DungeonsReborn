from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2.migrate import migrate_source_typed  # noqa: E402


class V2MigrateTypedTests(unittest.TestCase):
    def test_requirement_and_cost_are_rewritten_to_specs(self) -> None:
        source = """
from dungeonsreborn_builder.v2 import Requirement, Cost
r = Requirement("health-lte", {"value": 10})
c = Cost("consume-main-hand", {"amount": 1})
"""
        migrated, rewrites, flags = migrate_source_typed(source)
        self.assertIn("HealthLteRequirement(10)", migrated)
        self.assertIn("ConsumeMainHandCost(amount=1)", migrated)
        self.assertGreaterEqual(rewrites, 2)
        self.assertEqual(flags, [])

    def test_class_tokens_are_rewritten(self) -> None:
        source = """
from dungeonsreborn_builder.v2 import rpg_class
x = rpg_class
x.attribute("generic.attack_damage", 1.0, operation="ADD_SCALAR")
x.node(id="n1", name="N1", node_type="STAT", ability_trigger="ON_HIT", stat_scaling="PER_RANK", stat_curve="LINEAR")
x.resistance("PROJECTILE", 0.95)
"""
        migrated, _, _ = migrate_source_typed(source)
        self.assertIn("operation=AttributeOperation.ADD_SCALAR", migrated)
        self.assertIn("node_type=ClassNodeType.STAT", migrated)
        self.assertIn("ability_trigger=ClassAbilityTrigger.ON_HIT", migrated)
        self.assertIn("stat_scaling=ClassScalingMode.PER_RANK", migrated)
        self.assertIn("stat_curve=ClassScalingCurve.LINEAR", migrated)
        self.assertIn(".resistance(DamageType.PROJECTILE", migrated)


if __name__ == "__main__":
    unittest.main()
