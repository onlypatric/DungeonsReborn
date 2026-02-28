from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import vfx  # noqa: E402


class V2VfxRegistryTests(unittest.TestCase):
    def test_registry_counts(self) -> None:
        catalog = vfx.catalog()
        self.assertEqual(catalog["counts"]["clips"], 24)
        self.assertEqual(catalog["counts"]["modifiers"], 18)
        self.assertEqual(catalog["counts"]["anchors"], 12)
        self.assertEqual(catalog["counts"]["archetypes"], 36)


if __name__ == "__main__":
    unittest.main()
