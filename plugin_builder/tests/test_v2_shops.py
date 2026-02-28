from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import BuildContext, Material, item, shop  # noqa: E402


class V2ShopsTests(unittest.TestCase):
    def test_sell_serializes_buy_token_ingredient(self) -> None:
        ctx = BuildContext(strict=True)
        blade = item.create(ctx, symbol="item.test.shop.blade", name="Blade", material=Material.IRON_SWORD)
        entry = shop.create(ctx, symbol="shop.test.vendor", title="Vendor").sell(blade, cost_tokens=7)

        payload = entry.build()
        trade = payload["trades"][0]

        self.assertIn("buy", trade)
        self.assertEqual(trade["buy"][0]["type"], "token")
        self.assertEqual(trade["buy"][0]["amount"], 7)
        self.assertEqual(trade["sell"]["itemId"], blade.id)

    def test_sell_requires_at_least_one_cost(self) -> None:
        ctx = BuildContext(strict=True)
        blade = item.create(ctx, symbol="item.test.shop.nocost", name="Blade", material=Material.IRON_SWORD)

        with self.assertRaises(ValueError):
            shop.create(ctx, symbol="shop.test.nocost", title="NoCost").sell(blade)


if __name__ == "__main__":
    unittest.main()
