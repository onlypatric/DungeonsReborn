from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (  # noqa: E402
    VfxAnchorId,
    VfxArchetypeId,
    VfxClipId,
    VfxLod,
    vfx,
)


class V2VfxCompilerTests(unittest.TestCase):
    def test_compile_archetype_to_state_machine(self) -> None:
        action = vfx.compile(
            vfx.archetype(
                VfxArchetypeId.IMPACT_HEAVY_BURST,
                lod=VfxLod.MEDIUM,
                anchor=VfxAnchorId.ORIGIN_STATIC,
            )
        )
        built = action.to_dict()
        self.assertEqual(built["type"], "state_machine")
        self.assertIn("charge", built)
        self.assertIn("sustain", built)

    def test_compile_timeline_merges_residual_into_release(self) -> None:
        timeline = vfx.timeline(
            activation=[vfx.clip(VfxClipId.POINT_FLASH)],
            decay=[vfx.clip(VfxClipId.RING_PULSE)],
            residual=[vfx.clip(VfxClipId.POINT_FLASH)],
        )
        built = vfx.compile(timeline).to_dict()
        self.assertEqual(built["type"], "state_machine")
        self.assertIn("release", built)


if __name__ == "__main__":
    unittest.main()
