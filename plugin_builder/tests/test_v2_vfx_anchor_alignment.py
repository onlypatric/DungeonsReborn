from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import VfxAnchorId, vfx  # noqa: E402


class V2VfxAnchorAlignmentTests(unittest.TestCase):
    def test_known_anchor_resolves(self) -> None:
        anchor = vfx.anchor(VfxAnchorId.CASTER_CENTER)
        self.assertEqual(anchor.anchor_id, VfxAnchorId.CASTER_CENTER)

    def test_unknown_anchor_fails(self) -> None:
        with self.assertRaises(ValueError):
            vfx.anchor("anchor.not_existing")


if __name__ == "__main__":
    unittest.main()
