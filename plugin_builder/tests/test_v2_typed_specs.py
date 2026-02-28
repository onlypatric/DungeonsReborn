from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (  # noqa: E402
    BuildContext,
    Material,
    Particle,
    QuestObjectiveType,
    ShopIngredientType,
    UpgradeActivator,
    fx,
    quest_objective,
    recipe_ingredient,
    shop_ingredient,
)
from dungeonsreborn_builder.v2.shops import ShopSellSpec, ShopTradeSpec  # noqa: E402
from dungeonsreborn_builder.v2.upgrades import UpgradeSpellSpec  # noqa: E402


class V2TypedSpecsTests(unittest.TestCase):
    def test_recipe_ingredient_material_serializes(self) -> None:
        ctx = BuildContext(strict=True)
        built = recipe_ingredient.material(Material.STICK).build(ctx, field="crafting.keys.S")
        self.assertEqual(built, {"material": "STICK"})

    def test_shop_trade_spec_serializes(self) -> None:
        ctx = BuildContext(strict=True)
        sell_item_id, _ = ctx.register("item", symbol="item.test.shop_sell", id_override="item_test_shop_sell")
        trade = ShopTradeSpec(
            buy=[shop_ingredient.token(5), shop_ingredient.currency("gold", 10)],
            sell=ShopSellSpec(item=sell_item_id, amount=2),
        ).build(ctx, field="shops.shop_test.trades[0]")
        self.assertEqual(trade["buy"][0]["type"], ShopIngredientType.TOKEN.value)
        self.assertEqual(trade["buy"][1]["type"], ShopIngredientType.CURRENCY.value)
        self.assertEqual(trade["sell"]["itemId"], "item_test_shop_sell")

    def test_quest_objective_spec_serializes(self) -> None:
        ctx = BuildContext(strict=True)
        mob_id, _ = ctx.register("mob", symbol="mob.test.target", id_override="mob_test_target")
        built = quest_objective.kill_mob(mob_id, count=3).build(ctx, field="quests.test.objectives[0]")
        self.assertEqual(built["type"], QuestObjectiveType.KILL_MOB.value)
        self.assertEqual(built["mobId"], "mob_test_target")
        self.assertEqual(built["count"], 3)

    def test_upgrade_spell_spec_supports_alias_activator(self) -> None:
        ctx = BuildContext(strict=True)
        ability_id, _ = ctx.register("ability", symbol="ability.test.cast", id_override="ability_test_cast")
        built = UpgradeSpellSpec(ability=ability_id, activator=UpgradeActivator.SHIFT_RIGHT_CLICK).build(
            ctx,
            field="upgrades.test.spell",
        )
        self.assertEqual(built["ability"], "ability_test_cast")
        self.assertEqual(built["activator"], "SHIFT_RIGHT_CLICK")

    def test_sphere_shell_dust_serializes_data(self) -> None:
        built = fx.particles_sphere_shell(
            Particle.DUST,
            radius=1.1,
            points=24,
            count=1,
            dust=fx.dust(color="#FFD700", size=1.2),
        ).to_dict()
        self.assertEqual(built["particle"], "DUST")
        self.assertEqual(built["data"]["color"], "#FFD700")
        self.assertEqual(built["data"]["size"], 1.2)

    def test_sphere_shell_scale_scales_shape_and_dust(self) -> None:
        built = fx.particles_sphere_shell(
            Particle.DUST,
            radius=1.0,
            points=20,
            count=2,
            offset=0.1,
            dust=fx.dust(color="#FFD700", size=1.0),
            scale=1.5,
        ).to_dict()
        self.assertEqual(built["radius"], 1.5)
        self.assertEqual(built["points"], 30)
        self.assertEqual(built["count"], 3)
        self.assertAlmostEqual(built["offset"], 0.15, places=6)
        self.assertAlmostEqual(built["data"]["size"], 1.5, places=6)


if __name__ == "__main__":
    unittest.main()
