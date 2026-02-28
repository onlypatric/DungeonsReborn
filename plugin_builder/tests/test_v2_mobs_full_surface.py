from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (
    BossbarSpec,
    BuildContext,
    EntityType,
    Material,
    MobTier,
    MobAiIntentType,
    Ref,
    ai_condition,
    custom_mob_tier,
    item,
    mob,
)


class V2MobsFullSurfaceTests(unittest.TestCase):
    def test_full_surface_serialization_and_legacy_build(self) -> None:
        ctx = BuildContext(strict=True)

        key_item = item.create(
            ctx,
            symbol="item.test.mobs.vault_key",
            name="Vault Key",
            material=Material.TRIPWIRE_HOOK,
        )
        relic_item = item.create(
            ctx,
            symbol="item.test.mobs.relic",
            name="Relic",
            material=Material.PRISMARINE_CRYSTALS,
        )

        entry = (
            mob.create(ctx, symbol="mob.test.full_surface", name="Full Surface", mob_type=EntityType.ZOMBIE)
            .loot_pool("pool_test_shared", rolls=2, bonus_rolls=1)
            .loot_drop_material(Material.ROTTEN_FLESH, chance=85.0, min_amount=1, max_amount=2)
            .loot_drop_item(Ref("item.test.mobs.relic"), chance=20.0, min_amount=1)
            .loot_guaranteed_item(Ref("item.test.mobs.vault_key"), amount=1)
            .mana_drop(resource="mana", killer_min=2.0, killer_max=4.0, nearby_min=1.0, nearby_max=2.0, nearby_radius=6.0)
            .phase(phase_id="phase_enraged", health_below=0.5, scale_multiplier=1.1)
            .egg("egg_full_surface", material=Material.ZOMBIE_SPAWN_EGG, amount=1)
            .spawner_block("spawner_full_surface", count=1, max_alive=2, respawn_ticks=120, radius=10.0)
            .trial_spawner("trial_full_surface", key_loot_pool="pool_test_shared", waves=3, simultaneous=1)
            .vault_profile(
                "vault_full_surface",
                key_item=Ref("item.test.mobs.vault_key"),
                loot_pool_normal="pool_test_shared",
                loot_pool_ominous="pool_test_shared",
                displayed_item_pool=[(Ref("item.test.mobs.relic"), 1.0)],
            )
            .spawn_point("spawn_full_surface", world="world", x=0.0, y=70.0, z=0.0, count=1, max_alive=2)
        )

        legacy_payload = entry.build()
        self.assertIn("type", legacy_payload)
        self.assertNotIn("mobs", legacy_payload)

        document = entry.build_document()
        self.assertIn("mobs", document)
        self.assertIn("lootPools", document)
        self.assertIn("eggs", document)
        self.assertIn("spawnerBlocks", document)
        self.assertIn("trialSpawners", document)
        self.assertIn("vaults", document)
        self.assertIn("spawns", document)

        self.assertIn(entry.id, document["mobs"])
        self.assertIn("pool_test_shared", document["lootPools"])
        self.assertEqual(document["eggs"]["egg_full_surface"]["mob"], entry.id)
        self.assertEqual(document["trialSpawners"]["trial_full_surface"]["keyLootPool"], "pool_test_shared")
        self.assertEqual(document["vaults"]["vault_full_surface"]["keyItem"], key_item.id)
        self.assertEqual(document["vaults"]["vault_full_surface"]["displayedItemPool"][0]["itemId"], relic_item.id)

    def test_style_and_passive_helpers(self) -> None:
        ctx = BuildContext(strict=True)
        passive = (
            mob.create(ctx, symbol="mob.test.passive", name="Passive", mob_type=EntityType.COW)
            .style_preset("starter_passive")
            .style(name="<green>Passive</green>", show_name=True, bossbar=BossbarSpec(enabled=False))
            .ai_passive_flee(aggro_radius=12.0, flee_speed=0.38, wander_speed=0.24)
        )

        payload = passive.build()
        self.assertEqual(payload["stylePreset"], "starter_passive")
        self.assertIn("style", payload)
        self.assertEqual(payload["style"]["showName"], True)
        self.assertIn("ai", payload)
        self.assertEqual(payload["ai"]["profile"], "PASSIVE")
        self.assertEqual(payload["ai"]["runtimeModel"], "NATURAL_V1")
        self.assertEqual(payload["ai"]["movementPolicy"], "PATHFINDER_FIRST")
        self.assertIn("sources", payload["ai"]["targeting"])
        selectors = payload["ai"]["selectors"]
        self.assertEqual(len(selectors), 2)
        self.assertEqual(selectors[0]["intent"]["type"], "FLEE")
        self.assertEqual(selectors[1]["intent"]["type"], "WANDER")

        ambient = mob.create(ctx, symbol="mob.test.ambient", name="Ambient", mob_type=EntityType.SHEEP).ai_passive_wander()
        ambient_payload = ambient.build()
        self.assertEqual(ambient_payload["ai"]["profile"], "PASSIVE")
        self.assertEqual(len(ambient_payload["ai"]["selectors"]), 1)
        self.assertEqual(ambient_payload["ai"]["selectors"][0]["intent"]["type"], "WANDER")

        copied = mob.create(ctx, symbol="mob.test.copy", name="Copy", mob_type=EntityType.DONKEY).ai_copy_from(passive)
        copied_payload = copied.build()
        self.assertEqual(copied_payload["ai"], payload["ai"])

    def test_ai_template_export_and_inherit_surface(self) -> None:
        ctx = BuildContext(strict=True)
        template_owner = (
            mob.create(ctx, symbol="mob.test.template.owner", name="Owner", mob_type=EntityType.COW)
            .ai_passive_flee()
            .ai_template("passive_fauna_natural")
        )
        inheritor = (
            mob.create(ctx, symbol="mob.test.template.inherit", name="Inherit", mob_type=EntityType.DONKEY)
            .ai_inherit_template("passive_fauna_natural")
            .ai_v4()
            .ai_selector(
                MobAiIntentType.WANDER,
                selector_id="wander_only",
                priority=100,
                when=ai_condition.bool(True),
                require_target=False,
            )
        )
        owner_doc = template_owner.build_document()
        inherit_payload = inheritor.build()

        self.assertIn("aiTemplates", owner_doc)
        self.assertIn("passive_fauna_natural", owner_doc["aiTemplates"])
        self.assertEqual(inherit_payload["aiTemplate"], "passive_fauna_natural")

    def test_clone_creates_new_id_symbol_and_preserves_spec(self) -> None:
        ctx = BuildContext(strict=True)
        base = (
            mob.create(ctx, symbol="mob.test.clone.base", name="Clone Base", mob_type=EntityType.ZOMBIE)
            .tier(MobTier.P01)
            .stats(health=20.0, damage=4.0, armor=1.0, speed=0.25)
            .equip_main_hand(Material.WOODEN_SWORD)
            .equip_head(Material.LEATHER_HELMET)
            .scale_range(0.92, 1.08)
        )
        cloned = base.clone(symbol="mob.test.clone.variant", name="Clone Variant")

        self.assertNotEqual(base.id, cloned.id)
        self.assertEqual(cloned.symbol, "mob.test.clone.variant")
        self.assertEqual(cloned.build()["name"], "Clone Variant")
        self.assertEqual(cloned.build()["type"], base.build()["type"])
        self.assertEqual(cloned.build()["equipment"]["mainHand"]["material"], "WOODEN_SWORD")
        self.assertEqual(cloned.build()["equipment"]["head"]["material"], "LEATHER_HELMET")
        self.assertAlmostEqual(cloned.build()["scaleVariance"], 0.08, places=6)

    def test_scale_range_and_equipment_helpers(self) -> None:
        ctx = BuildContext(strict=True)
        entry = (
            mob.create(ctx, symbol="mob.test.scale_equip", name="Scale Equip", mob_type=EntityType.ZOMBIE)
            .name("Scale Equip Renamed")
            .scale_range(0.92, 1.08)
            .collidable(False)
            .invulnerable(False)
            .equip_armor(
                head=Material.LEATHER_HELMET,
                chest=Material.LEATHER_CHESTPLATE,
                legs=Material.LEATHER_LEGGINGS,
                feet=Material.LEATHER_BOOTS,
            )
        )
        payload = entry.build()
        self.assertEqual(payload["name"], "Scale Equip Renamed")
        self.assertEqual(payload["collidable"], False)
        self.assertEqual(payload["invulnerable"], False)
        self.assertAlmostEqual(payload["scaleVariance"], 0.08, places=6)
        self.assertEqual(payload["equipment"]["head"]["material"], "LEATHER_HELMET")
        self.assertEqual(payload["equipment"]["chest"]["material"], "LEATHER_CHESTPLATE")
        self.assertEqual(payload["equipment"]["legs"]["material"], "LEATHER_LEGGINGS")
        self.assertEqual(payload["equipment"]["feet"]["material"], "LEATHER_BOOTS")

    def test_tier_typed_and_custom_token(self) -> None:
        ctx = BuildContext(strict=True)
        standard = mob.create(ctx, symbol="mob.test.tier.standard", name="Tier Standard", mob_type=EntityType.ZOMBIE).tier(
            MobTier.P01
        )
        custom = mob.create(ctx, symbol="mob.test.tier.custom", name="Tier Custom", mob_type=EntityType.ZOMBIE).tier(
            custom_mob_tier("T5_BOSS")
        )
        self.assertEqual(standard.build()["tier"], "P01")
        self.assertEqual(custom.build()["tier"], "T5_BOSS")


if __name__ == "__main__":
    unittest.main()
