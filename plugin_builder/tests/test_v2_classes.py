from __future__ import annotations

import importlib.util
import sys
import tempfile
from pathlib import Path
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (  # noqa: E402
    AttributeOperation,
    BuildContext,
    ClassAbilityTrigger,
    DamagePolicy,
    ClassNodeType,
    ClassScalingMode,
    DamageType,
    KNOWN_DOMAINS,
    ClassBonusV2,
    ClassConditionalBonusV2,
    ClassItemMatcherSpec,
    ClassNodeV2,
    ClassUnlockCurrencyV2,
    ClassUnlockItemV2,
    Material,
    PotionEffect,
    Ref,
    ability,
    fx,
    pack_v2,
    quest,
    rpg_class,
)


class V2ClassesTests(unittest.TestCase):
    def test_domain_registration_contains_class(self) -> None:
        self.assertIn("class", KNOWN_DOMAINS)
        ctx = BuildContext(strict=True)
        class_id, symbol = ctx.register("class", symbol="class.demo.test", id_override="class_demo_test")
        self.assertEqual(class_id, "class_demo_test")
        self.assertEqual(symbol, "class.demo.test")

    def test_class_typed_serialization(self) -> None:
        ctx = BuildContext(strict=True)
        trigger = ability(
            ctx,
            symbol="ability.test.class.trigger",
            name="Class Trigger",
            action=fx.damage(2.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        )
        unlock_quest = quest.create(ctx, symbol="quest.test.class.unlock", name="Unlock").reward_tokens(5)

        entry = (
            rpg_class.create(ctx, symbol="class.test.guerriero", id="guerriero", name="Guerriero")
            .enabled(True)
            .name_key("classes.guerriero.name")
            .description_key("classes.guerriero.description")
            .description_lines(["Placeholder class spec"])
            .icon(Material.IRON_SWORD)
            .unlock_level(5)
            .unlock_tokens(10)
            .unlock_quests([Ref("quest.test.class.unlock")])
            .unlock_items([ClassUnlockItemV2(matcher=ClassItemMatcherSpec(item="external_token"), amount=1)])
            .unlock_currencies([ClassUnlockCurrencyV2("renown", 20)])
            .stats(strength=2, vitality=1)
            .mana(resource="arcana", max=10.0, regen=0.4)
            .attribute("generic.attack_damage", 0.5, operation=AttributeOperation.ADD_NUMBER)
            .potion(PotionEffect.STRENGTH, amplifier=0, ambient=True, particles=True, icon=True)
            .resistance(DamageType.PROJECTILE, 0.95)
            .attribute_cap("generic.attack_damage", 32.0)
            .tree_respec(tokens=2, points=1)
            .node(
                id="core_steady",
                name="Core Steady",
                node_type=ClassNodeType.STAT,
                stat_key="strength",
                stat_amount=1.0,
                stat_scaling=ClassScalingMode.PER_RANK,
                cost=1,
                max_rank=3,
            )
            .node(
                id="offense_trigger",
                name="Offense Trigger",
                node_type=ClassNodeType.CUSTOM,
                requires=["core_steady"],
                ability=Ref("ability.test.class.trigger"),
                ability_trigger=ClassAbilityTrigger.ON_HIT,
                ability_period_ticks=30,
                ability_cancel_event=False,
            )
            .edge("core_steady", "offense_trigger")
            .synergy(
                id="synergy_test",
                requires=["core_steady", "offense_trigger"],
                bonuses=ClassBonusV2().attribute("generic.attack_speed", 0.02, operation=AttributeOperation.ADD_SCALAR),
            )
            .conditional_bonus(
                ClassConditionalBonusV2(
                    worlds=["world"],
                    bonuses=ClassBonusV2().resistance(DamageType.PHYSICAL, 0.97),
                )
            )
        )

        payload = entry.build()
        self.assertEqual(payload["name"], "Guerriero")
        self.assertEqual(payload["unlock"]["level"], 5)
        self.assertEqual(payload["unlock"]["tokens"], 10)
        self.assertEqual(payload["unlock"]["quests"], [unlock_quest.id])
        self.assertEqual(payload["unlock"]["currencies"][0]["currency"], "renown")
        self.assertEqual(payload["path"]["nodes"][0]["type"], "STAT")
        self.assertEqual(payload["path"]["nodes"][1]["ability"]["id"], trigger.id)
        self.assertEqual(payload["path"]["nodes"][1]["ability"]["trigger"], "ON_HIT")
        self.assertEqual(payload["path"]["synergies"][0]["id"], "synergy_test")
        self.assertEqual(payload["bonuses"]["conditional"][0]["worlds"], ["world"])

    def test_pack_preview_and_export_include_classes(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        pack.add(rpg_class.create(ctx, symbol="class.demo.alpha", id="alpha", name="Alpha"))

        preview = pack.preview_export()
        self.assertEqual(preview["counts"]["classes"], 1)

        with tempfile.TemporaryDirectory() as tmp:
            paths = pack.export(tmp)
            self.assertTrue(any(path.endswith("classes.yml") for path in paths))
            classes_path = Path(tmp) / "classes.yml"
            data = yaml.safe_load(classes_path.read_text(encoding="utf-8"))
            self.assertIn("classes", data)
            self.assertIn("alpha", data["classes"])

    def test_classes_merge_policy_preserves_and_overwrites(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        pack.add(rpg_class.create(ctx, symbol="class.demo.generated", id="class_demo", name="Generated New"))

        with tempfile.TemporaryDirectory() as tmp:
            classes_path = Path(tmp) / "classes.yml"
            classes_path.write_text(
                yaml.safe_dump(
                    {
                        "classes": {
                            "external_class": {"name": "External"},
                            "class_demo": {"name": "Generated Old"},
                        }
                    },
                    sort_keys=False,
                    allow_unicode=True,
                ),
                encoding="utf-8",
            )

            pack.export(tmp)
            merged = yaml.safe_load(classes_path.read_text(encoding="utf-8"))
            self.assertEqual(merged["classes"]["external_class"]["name"], "External")
            self.assertEqual(merged["classes"]["class_demo"]["name"], "Generated New")

    def test_p00_classes_master_module_smoke(self) -> None:
        module_path = ROOT / "CONFIGPLAN" / "p00_concetto_globale" / "p00_classes_master.py"
        spec = importlib.util.spec_from_file_location("p00_classes_master", module_path)
        self.assertIsNotNone(spec)
        module = importlib.util.module_from_spec(spec)  # type: ignore[arg-type]
        assert spec is not None and spec.loader is not None
        spec.loader.exec_module(module)

        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)
        module.register_resources(pack)
        preview = pack.preview_export()

        self.assertFalse(preview["errors"])
        self.assertEqual(preview["counts"]["classes"], 0)
        self.assertEqual(preview["counts"]["abilities"], 0)


if __name__ == "__main__":
    unittest.main()
