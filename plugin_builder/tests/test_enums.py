from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.vanilla import normalize_enum_name
from dungeonsreborn_builder import Material


class EnumTests(unittest.TestCase):
    def test_normalize_enum_name(self) -> None:
        self.assertEqual(normalize_enum_name("GENERIC_ATTACK_DAMAGE"), "ATTACK_DAMAGE")
        self.assertEqual(normalize_enum_name("SPEED"), "SPEED")

    def test_enum_value_str(self) -> None:
        self.assertEqual(str(Material.STONE), "STONE")


if __name__ == "__main__":
    unittest.main()
