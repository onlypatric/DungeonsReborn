from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

import dungeonsreborn_builder


class V2RootGuardTests(unittest.TestCase):
    def test_root_legacy_symbol_is_blocked(self) -> None:
        with self.assertRaises(AttributeError) as exc:
            _ = dungeonsreborn_builder.Material
        self.assertIn("V1 API has been removed", str(exc.exception))


if __name__ == "__main__":
    unittest.main()
