from __future__ import annotations

import sys
import tempfile
from pathlib import Path
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import BuildContext, EntityType, Material, Ref, item, mob, pack_v2


class V2MobsExportDocumentShapeTests(unittest.TestCase):
    def test_export_writes_multi_root_mob_document(self) -> None:
        ctx = BuildContext(strict=True)
        pack = pack_v2(ctx)

        key_item = item.create(
            ctx,
            symbol="item.test.export.key",
            name="Export Key",
            material=Material.TRIPWIRE_HOOK,
        )
        loot_item = item.create(
            ctx,
            symbol="item.test.export.loot",
            name="Export Loot",
            material=Material.PRISMARINE_CRYSTALS,
        )

        enemy = (
            mob.create(ctx, symbol="mob.test.export.doc", name="Export Doc Mob", mob_type=EntityType.ZOMBIE)
            .loot_pool("pool_export")
            .loot_drop_item(Ref("item.test.export.loot"), chance=15.0)
            .egg("egg_export", material=Material.ZOMBIE_SPAWN_EGG)
            .spawner_block("spawner_export", count=1, max_alive=2)
            .trial_spawner("trial_export", key_loot_pool="pool_export")
            .vault_profile(
                "vault_export",
                key_item=Ref("item.test.export.key"),
                loot_pool_normal="pool_export",
                loot_pool_ominous="pool_export",
            )
            .spawn_point("spawn_export", world="world", x=12.0, y=71.0, z=-4.0)
        )

        pack.add(key_item, loot_item, enemy)

        with tempfile.TemporaryDirectory() as tmp:
            pack.export(tmp)
            mob_file = Path(tmp) / "mobs" / f"{enemy.id}.yml"
            data = yaml.safe_load(mob_file.read_text(encoding="utf-8"))

        self.assertIn("mobs", data)
        self.assertIn(enemy.id, data["mobs"])
        self.assertIn("lootPools", data)
        self.assertIn("pool_export", data["lootPools"])
        self.assertIn("eggs", data)
        self.assertIn("spawnerBlocks", data)
        self.assertIn("trialSpawners", data)
        self.assertIn("vaults", data)
        self.assertIn("spawns", data)

        self.assertEqual(data["vaults"]["vault_export"]["keyItem"], key_item.id)
        self.assertEqual(data["trialSpawners"]["trial_export"]["keyLootPool"], "pool_export")


if __name__ == "__main__":
    unittest.main()
