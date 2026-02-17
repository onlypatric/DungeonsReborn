from __future__ import annotations

import sys
from pathlib import Path
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder import schema

GOLDEN_DIR = Path(__file__).resolve().parent / "golden"


class GoldenTemplateTests(unittest.TestCase):
    def _assert_yaml_matches(self, filename: str, actual: dict) -> None:
        path = GOLDEN_DIR / filename
        self.assertTrue(path.exists(), f"Golden file missing: {path}")
        expected = yaml.safe_load(path.read_text(encoding="utf-8"))
        self.assertEqual(expected, actual)

    def test_effects_schema_snapshot(self) -> None:
        self._assert_yaml_matches("effects_schema.yml", schema.effects_schema_snapshot().to_dict())

    def test_items_schema_snapshot(self) -> None:
        self._assert_yaml_matches("items_schema.yml", schema.items_schema_snapshot())


if __name__ == "__main__":
    unittest.main()
