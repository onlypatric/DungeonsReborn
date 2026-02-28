from __future__ import annotations

from pathlib import Path
import unittest


class V2CliTemplateTests(unittest.TestCase):
    def test_required_templates_exist(self) -> None:
        root = Path(__file__).resolve().parents[1] / "templates"
        required = [
            "v2_starter_pack.py",
            "v2_mob_quickstart.py",
            "v2_weapon_bundle.py",
            "v2_shop_pack.py",
            "v2_campaign_pack.py",
        ]
        missing = [name for name in required if not (root / name).exists()]
        self.assertEqual(missing, [], f"Missing templates: {missing}")


if __name__ == "__main__":
    unittest.main()
