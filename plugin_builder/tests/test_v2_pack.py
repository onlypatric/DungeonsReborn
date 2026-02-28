from __future__ import annotations

import sys
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (
    BuildContext,
    DamagePolicy,
    EntityType,
    Material,
    Ref,
    ability,
    bind,
    fx,
    item,
    mob,
    pack_v2,
    recipe,
    recipe_ingredient,
)


class V2PackTests(unittest.TestCase):
    def test_pack_preview_and_id_map(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)

        hit = ability(
            ctx,
            symbol="ability.demo.hit",
            name="Demo Hit",
            action=fx.damage(4.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        )
        blade = item.create(
            ctx,
            symbol="item.demo.blade",
            name="Demo Blade",
            material=Material.GOLDEN_SWORD,
            binds=[bind.use(Ref("ability.demo.hit"))],
        )
        pig = mob.create(ctx, symbol="mob.demo.pig", name="Demo Pig", mob_type=EntityType.PIG).stats(
            health=20, damage=4, armor=1, speed=0.3
        )
        craft = recipe.for_item(
            ctx,
            Ref("item.demo.blade"),
            pattern=[" GG", " SG", "S  "],
            keys=recipe.keys()
            .slot("G", recipe_ingredient.material(Material.GOLD_INGOT))
            .slot("S", recipe_ingredient.material(Material.STICK)),
            symbol="recipe.demo.blade",
        ).discovery(show_in_book=True)

        pack.add(hit, blade, pig, craft)
        preview = pack.preview_export()

        self.assertEqual(preview["counts"]["abilities"], 1)
        self.assertEqual(preview["counts"]["items"], 1)
        self.assertEqual(preview["counts"]["mobs"], 1)
        self.assertEqual(preview["counts"]["recipes"], 1)
        self.assertFalse(preview["errors"])

        mapping = pack.id_map()
        self.assertEqual(mapping["ability.demo.hit"], "ability_demo_hit")
        self.assertEqual(mapping["item.demo.blade"], "item_demo_blade")

    def test_export_writes_domain_files(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        pack.add(
            ability(
                ctx,
                symbol="ability.demo.proc",
                name="Proc",
                action=fx.damage(2.0, policy=DamagePolicy.HOSTILE_DEFAULT),
            )
        )
        with tempfile.TemporaryDirectory() as tmp:
            paths = pack.export(tmp)
            self.assertTrue(any(path.endswith("ability_demo_proc.yml") for path in paths))
            self.assertTrue(any(path.endswith("v2_pack.yml") for path in paths))

    def test_item_inline_ability_is_auto_exported(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        potion = item.create(
            ctx,
            symbol="item.demo.inline_bind",
            name="Inline Bind Item",
            material=Material.POTION,
        ).bind(
            bind.use(
                ability(
                    ctx,
                    symbol="ability.demo.inline_bind",
                    name="Inline Bind Ability",
                    action=fx.damage(1.0, policy=DamagePolicy.HOSTILE_DEFAULT),
                ).cooldown(10)
            )
        )
        pack.add(potion)

        preview = pack.preview_export()
        self.assertEqual(preview["counts"]["items"], 1)
        self.assertEqual(preview["counts"]["abilities"], 1)

        with tempfile.TemporaryDirectory() as tmp:
            paths = pack.export(tmp)
            self.assertTrue(any(path.endswith("item_demo_inline_bind.yml") for path in paths))
            self.assertTrue(any(path.endswith("ability_demo_inline_bind.yml") for path in paths))

    def test_item_inline_ability_dedup_across_items(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        shared = ability(
            ctx,
            symbol="ability.demo.shared_inline",
            name="Shared Inline",
            action=fx.damage(1.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        )
        a = item.create(ctx, symbol="item.demo.inline_a", name="Inline A", material=Material.STICK).bind(bind.use(shared))
        b = item.create(ctx, symbol="item.demo.inline_b", name="Inline B", material=Material.BLAZE_ROD).bind(bind.use(shared))
        pack.add(a, b)
        self.assertEqual(pack.preview_export()["counts"]["abilities"], 1)

    def test_item_inline_recipe_is_auto_exported(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        potion = (
            item.create(ctx, symbol="item.demo.inline_recipe", name="Inline Recipe Item", material=Material.POTION)
            .recipe(
                symbol="recipe.demo.inline_recipe",
                pattern=["SS", "SS"],
                keys=recipe.keys().slot("S", recipe_ingredient.material(Material.STICK)),
            )
        )
        pack.add(potion)

        preview = pack.preview_export()
        self.assertEqual(preview["counts"]["items"], 1)
        self.assertEqual(preview["counts"]["recipes"], 1)

        with tempfile.TemporaryDirectory() as tmp:
            paths = pack.export(tmp)
            self.assertTrue(any(path.endswith("item_demo_inline_recipe.yml") for path in paths))
            self.assertTrue(any(path.endswith("recipe_demo_inline_recipe.yml") for path in paths))

    def test_item_inline_recipe_dedup_on_repeat_add(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        crafted = (
            item.create(ctx, symbol="item.demo.inline_recipe_repeat", name="Inline Recipe Repeat", material=Material.PAPER)
            .recipe(
                symbol="recipe.demo.inline_recipe_repeat",
                pattern=["S"],
                keys=recipe.keys().slot("S", recipe_ingredient.material(Material.STICK)),
            )
        )
        pack.add(crafted, crafted)
        self.assertEqual(pack.preview_export()["counts"]["recipes"], 1)

    def test_mob_inline_event_ability_is_auto_exported(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        mob_entry = mob.create(
            ctx,
            symbol="mob.demo.inline_event",
            name="Inline Event Mob",
            mob_type=EntityType.ZOMBIE,
        ).events(
            on_hit=ability(
                ctx,
                symbol="ability.demo.mob_inline_event",
                name="Mob Inline Event",
                action=fx.damage(1.0, policy=DamagePolicy.HOSTILE_DEFAULT),
            ).cooldown(10)
        )
        pack.add(mob_entry)

        preview = pack.preview_export()
        self.assertEqual(preview["counts"]["mobs"], 1)
        self.assertEqual(preview["counts"]["abilities"], 1)

        with tempfile.TemporaryDirectory() as tmp:
            paths = pack.export(tmp)
            self.assertTrue(any(path.endswith("mob_demo_inline_event.yml") for path in paths))
            self.assertTrue(any(path.endswith("ability_demo_mob_inline_event.yml") for path in paths))


if __name__ == "__main__":
    unittest.main()
