from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import BuildContext, BuildValidationError, Ref


class V2CoreTests(unittest.TestCase):
    def test_auto_id_deterministic(self) -> None:
        ctx = BuildContext(strict=True)
        first = ctx.auto_id("ability", "Ghost Hit Smoke")
        second = ctx.auto_id("ability", "Ghost Hit Smoke")
        self.assertEqual(first, "ability_ghost_hit_smoke")
        self.assertEqual(first, second)

    def test_collision_hard_fail(self) -> None:
        ctx = BuildContext(strict=True)
        ctx.register("ability", symbol="ability.ghost.hit", id_override="ability_ghost_hit")
        with self.assertRaises(BuildValidationError):
            ctx.register("ability", symbol="ability.ghost.other", id_override="ability_ghost_hit")

    def test_unresolved_ref_fails(self) -> None:
        ctx = BuildContext(strict=True)
        with self.assertRaises(BuildValidationError):
            ctx.resolve(Ref("ability.missing.hit"), domain="ability", field="test.ref")

    def test_ambiguous_suffix_ref_fails(self) -> None:
        ctx = BuildContext(strict=True)
        ctx.register("ability", symbol="ability.alpha.hit", id_override="ability_alpha_hit")
        ctx.register("ability", symbol="ability.beta.hit", id_override="ability_beta_hit")
        with self.assertRaises(BuildValidationError):
            ctx.resolve(Ref("ability.hit"), domain="ability", field="test.ref")


if __name__ == "__main__":
    unittest.main()
