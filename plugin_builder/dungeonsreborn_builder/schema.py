"""Schema snapshot/export helpers for docs."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional

from .base import ExporterBase
from .effects import Ability, Action, EffectsDocument, ShapeTemplate, shape_point_xyz, shape_triangle
from .items import ItemBuilder
from .heads import HeadSpec, HeadsDocument
from .mobs import (
    MobBossBarSpec,
    MobBuilder,
    MobEquipmentSpec,
    MobGuiPreviewSpec,
    MobManaDrainSpec,
    MobManaDropSpec,
    MobManaRange,
    MobManaTier,
    MobEgg,
    MobProgressionSpec,
    MobSpawnSpec,
    MobSpawnerBlock,
    MobSpawnerTemplateSpec,
    TrialSpawnerSpec,
    TrialSpawnerMobEntry,
    TrialSpawnerProfileSpec,
    VaultSpec,
    VaultDisplayItemEntry,
    MobStyleSpec,
    MobSummonSpec,
    Vec3,
)
from .enums import DamageType
from .quests import QuestObjectiveSpec, QuestRequirementsSpec, QuestRewardsSpec, QuestSpec
from .shops import (
    ShopAvailabilitySpec,
    ShopCurrencySpec,
    ShopDocument,
    ShopIngredientSpec,
    ShopPricingSpec,
    ShopRegionPriceSpec,
    ShopRegionSpec,
    ShopRequirementSpec,
    ShopStockSpec,
    ShopTimeWindowSpec,
    ShopTokenSpec,
    ShopTokenTierSpec,
    ShopTradeSpec,
    ShopValueSpec,
    ShopSpec,
)
from .classes import ClassSpec, SkillNode
from .upgrades import UpgradeBuilder
from .loot import LootPool, LootItem
from .crafting import (
    CraftingCostSpec,
    CraftingDiscoverySpec,
    CraftingGridSpec,
    CraftingGuiPreviewSpec,
    CraftingHookEntry,
    CraftingHookSpec,
    CraftingIngredientSpec,
    CraftingOutputSpec,
    CraftingRecipeSpec,
    CraftingRecipeVariant,
    CraftingRequirementSpec,
)
from .vanilla import Material, EntityType


def effects_schema_snapshot() -> EffectsDocument:
    return EffectsDocument(
        abilities=[
            Ability(
                ability_id="schema_example",
                name="<gold>Schema Example</gold>",
                description="<gray>Example for docs.</gray>",
                action=Action(
                    type="sequence",
                    params={
                        "actions": [
                            {
                                "type": "sound",
                                "sound": "minecraft:entity.evoker.cast_spell",
                                "volume": 1.0,
                                "pitch": 1.0,
                            },
                            {
                                "type": "particles.ring",
                                "particle": "DUST",
                                "radius": 2.0,
                                "points": 36,
                                "color": "#55ccff",
                                "size": 1.0,
                            },
                        ]
                    },
                ),
            )
        ],
        shapes={
            "schema_triangle": ShapeTemplate(
                points=[shape_point_xyz(0.0, 0.0, 0.0)],
                triangles=[
                    shape_triangle(
                        shape_point_xyz(0.0, 0.0, 0.0),
                        shape_point_xyz(1.0, 0.0, 0.0),
                        shape_point_xyz(0.0, 0.0, 1.0),
                    )
                ],
            )
        },
        macros={
            "schema_macro": {
                "type": "particles.point",
                "particle": "FLAME",
                "count": 10,
            }
        },
    )


def items_schema_snapshot() -> dict:
    item = (
        ItemBuilder("schema_item")
        .material(Material.STONE)
        .display_name("<gray>Schema Item</gray>")
        .display_description("<gray>Example item schema.</gray>")
        .bind("RIGHT_CLICK", ability="schema_ability")
    )
    return {"items": {"schema_item": item.build()}}


def mobs_schema_snapshot() -> dict:
    mob = (
        MobBuilder("schema_mob")
        .mob_type(EntityType.ZOMBIE)
        .name("<dark_green>Schema Mob</dark_green>")
        .show_name(True)
        .style(MobStyleSpec(name="<gray>Schema Style</gray>", show_name=True))
        .bossbar(MobBossBarSpec(title="<red>Schema Boss</red>", color="RED", overlay="PROGRESS"))
        .equipment(MobEquipmentSpec(main_hand={"item": "schema_item", "amount": 1}))
        .gui_preview(MobGuiPreviewSpec(icon="ICON_MOBS", head="ICON_MOBS", description="Schema mob preview."))
        .stats(health=20, damage=4)
        .resistance(DamageType.FIRE, 0.5)
        .immunity(DamageType.POISON)
        .progression(MobProgressionSpec(min_xp=5, max_xp=10))
        .summon(MobSummonSpec(enabled=True, despawn_when_owner_offline=True))
        .mana_drop(
            MobManaDropSpec(
                killer=MobManaRange(1.0, 2.0),
                nearby=MobManaRange(0.5, 1.0),
                radius=8.0,
                cap=10.0,
                tiers=[MobManaTier(weight=1.0, min_multiplier=1.0, max_multiplier=1.5)],
            )
        )
        .mana_drain(MobManaDrainSpec(amount=2.0, chance=0.25, cooldown_ticks=40))
    )
    spawner = MobSpawnerBlock(
        spawner_id="schema_spawner",
        mob_id="schema_mob",
        material=Material.SPAWNER,
        spawn=MobSpawnerTemplateSpec(count=1, max_alive=3, respawn_ticks=200, activation_radius=16),
    )
    egg = MobEgg(egg_id="schema_egg", mob_id="schema_mob", material=Material.ZOMBIE_SPAWN_EGG, amount=1)
    spawn = MobSpawnSpec(
        spawn_id="schema_spawn",
        mob_id="schema_mob",
        world="world",
        location=Vec3(0.0, 64.0, 0.0),
        count=1,
        max_alive=3,
    )
    trial_spawner = TrialSpawnerSpec(
        trial_spawner_id="schema_trial_spawner",
        mob_pool=[TrialSpawnerMobEntry(mob_id="schema_mob", weight=1.0)],
        waves=3,
        simultaneous=2,
        cooldown_ticks=100,
        required_players=1,
        activation_range=12.0,
        key_loot_pool="schema_pool",
        ominous_profile=TrialSpawnerProfileSpec(
            mob_pool=[TrialSpawnerMobEntry(mob_id="schema_mob", weight=2.0)],
            waves=4,
            simultaneous=3,
            cooldown_ticks=80,
            key_loot_pool="schema_pool",
        ),
    )
    vault = VaultSpec(
        vault_id="schema_vault",
        key_item="schema_item",
        loot_pool_normal="schema_pool",
        loot_pool_ominous="schema_pool",
        activation_range=5.0,
        deactivation_range=8.0,
        displayed_item_pool=[VaultDisplayItemEntry(item_id="schema_item", weight=1.0)],
    )
    return {
        "schemaVersion": 1,
        "mobs": {"schema_mob": mob.build()},
        "spawnerBlocks": {spawner.spawner_id: spawner.to_dict()},
        "eggs": {egg.egg_id: egg.to_dict()},
        "spawns": {spawn.spawn_id: spawn.to_dict()},
        "trialSpawners": {trial_spawner.trial_spawner_id: trial_spawner.to_dict()},
        "vaults": {vault.vault_id: vault.to_dict()},
    }


def shops_schema_snapshot() -> dict:
    token = ShopTokenSpec(
        marker_key="dungeonsreborn:shop_token",
        material=Material.SUNFLOWER,
        name="<gold><bold>Token</bold></gold>",
        lore=["<gray>Schema token currency.</gray>"],
    )
    tiers = [
        ShopTokenTierSpec(
            tier_id="compressed",
            material=Material.SUNFLOWER,
            name="<yellow><bold>Compressed Token</bold></yellow>",
        )
    ]
    currency = ShopCurrencySpec(
        currency_id="coins",
        material=Material.GOLD_NUGGET,
        name="<gold>Coins</gold>",
    )
    values = [
        ShopValueSpec(
            ingredient=ShopIngredientSpec(ingredient_type="token", amount=1),
            value=1,
        )
    ]
    trade = ShopTradeSpec(
        buys=[ShopIngredientSpec(ingredient_type="token", amount=5)],
        sells=[ShopIngredientSpec(ingredient_type="item_id", item_id="schema_item", amount=1)],
        min_level=1,
        requirements=[ShopRequirementSpec.level_req(1)],
        availability=ShopAvailabilitySpec(
            timezone="UTC",
            windows=[ShopTimeWindowSpec(start="09:00", end="18:00", days=["MONDAY", "TUESDAY"])],
        ),
        stock=ShopStockSpec(min=0, max=10, restock_seconds=3600, scope="global"),
    )
    shop = ShopSpec(
        shop_id="schema_shop",
        title="<gold>Schema Shop</gold>",
        icon=ShopIngredientSpec(ingredient_type="item_id", item_id="schema_item", amount=1),
        trades=[trade],
        pricing=ShopPricingSpec(tax_rate=0.05, world_multipliers={"world": 1.0}),
    )
    doc = ShopDocument(
        shops=[shop],
        token=token,
        token_tiers=tiers,
        currencies=[currency],
        values=values,
    )
    return doc.to_dict()


def quests_schema_snapshot() -> dict:
    quest = QuestSpec(
        quest_id="schema_quest",
        name="<gold>Schema Quest</gold>",
        description=["<gray>Example quest schema.</gray>"],
        objectives=[QuestObjectiveSpec("kill_mob", mob_id="schema_mob", count=3)],
        requirements=QuestRequirementsSpec(level=1),
        rewards=QuestRewardsSpec(tokens=5),
    )
    return {"quests": {quest.quest_id: quest.to_dict()}}


def classes_schema_snapshot() -> dict:
    node = SkillNode("schema_node", "Schema Node", bonuses={"mana": 5})
    spec = ClassSpec("schema_class", "Schema Class", nodes=[node])
    return {"classes": {spec.class_id: spec.to_dict()}}


def upgrades_schema_snapshot() -> dict:
    upgrade = UpgradeBuilder("schema_upgrade").name("<gold>Schema Upgrade</gold>").compatibility("rune")
    return {"upgrades": {"schema_upgrade": upgrade.build()}}


def loot_schema_snapshot() -> dict:
    pool = LootPool("schema_pool", [LootItem(Material.STONE, 1.0, 1, 1)])
    return {"lootPools": {pool.pool_id: pool.to_dict()}}


def heads_schema_snapshot() -> dict:
    doc = HeadsDocument().add(HeadSpec(head_id="schema_head", name="Schema Head", texture="<base64>"))
    return doc.to_dict()


def crafting_schema_snapshot() -> dict:
    ingredient = CraftingIngredientSpec(item_id="schema_item", amount=2)
    grid = CraftingGridSpec(
        pattern=["AA", " A"],
        keys={"A": ingredient},
        mirror=True,
    )
    variant = CraftingRecipeVariant(grid=grid, strict=True)
    output = CraftingOutputSpec(item_id="schema_item", amount=1, chance=1.0)
    spec = CraftingRecipeSpec(
        recipe_id="schema_recipe",
        name="Schema Recipe",
        description="Example crafting schema.",
        permissions=["dungeonsreborn.crafting.schema"],
        cooldown_seconds=5.0,
        requirements=[CraftingRequirementSpec.level(1)],
        costs=[CraftingCostSpec.mana(5.0)],
        variants=[variant],
        outputs=[output],
        hooks=CraftingHookSpec(post=CraftingHookEntry(abilities=["schema_ability"])),
        discovery=CraftingDiscoverySpec(unlock_on_craft=True),
        gui=CraftingGuiPreviewSpec(icon="ICON_CRAFTING", head="ICON_CRAFTING", title_key="gui.crafting.example.title"),
    )
    data: Dict[str, Any] = {"schemaVersion": 1}
    data.update(spec.to_dict())
    return data


@dataclass
class SchemaExporter(ExporterBase):
    def write_effects_snapshot(self, filename: str = "effects_schema_snapshot.yml") -> str:
        doc = effects_schema_snapshot()
        return self.write_yaml(filename, doc.to_dict())

    def write_items_snapshot(self, filename: str = "items_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, items_schema_snapshot())

    def write_mobs_snapshot(self, filename: str = "mobs_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, mobs_schema_snapshot())

    def write_shops_snapshot(self, filename: str = "shops_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, shops_schema_snapshot())

    def write_quests_snapshot(self, filename: str = "quests_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, quests_schema_snapshot())

    def write_classes_snapshot(self, filename: str = "classes_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, classes_schema_snapshot())

    def write_upgrades_snapshot(self, filename: str = "upgrades_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, upgrades_schema_snapshot())

    def write_loot_snapshot(self, filename: str = "loot_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, loot_schema_snapshot())

    def write_heads_snapshot(self, filename: str = "heads_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, heads_schema_snapshot())

    def write_crafting_snapshot(self, filename: str = "crafting_schema_snapshot.yml") -> str:
        return self.write_yaml(filename, crafting_schema_snapshot())
