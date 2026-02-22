"""Smoke gun item + ability export for local test server."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dungeonsreborn_builder import (
    AbilityBuilder,
    Action,
    CraftingDiscoverySpec,
    CraftingExporter,
    CraftingGridSpec,
    CraftingRecipeSpec,
    CraftingRecipeVariant,
    EffectsExporter,
    Item,
    ItemExporter,
    Material,
    Particle,
    sequence,
)
from dungeonsreborn_builder.crafting import ingredient_material, output_item


ABILITY_ID = "ability_item_gun_smoke_demo"
ITEM_ID = "item_gun_smoke_demo"
RECIPE_ID = "recipe_item_gun_smoke_demo"


def build_ability():
    on_hit = sequence(
        Action(
            "particles_sphere_shell",
            {
                "particle": Particle.CLOUD.name,
                "radius": 1.8,
                "points": 42,
                "count": 1,
                "at": "last_entity",
            },
        ),
        Action("damage", {"amount": 5.0}),
    )
    projectile = Action(
        "projectile",
        {
            "speedPerTick": 1.6,
            "maxDistance": 40.0,
            "hitRadius": 0.35,
            "ignoreCaster": True,
            "trail": {
                "particle": Particle.SMOKE.name,
                "count": 2,
                "offset": 0.02,
                "extra": 0.0,
            },
            "onHit": on_hit.to_dict(),
        },
    )
    return (
        AbilityBuilder(ABILITY_ID)
        .name("<gray>Smoke Shot</gray>")
        .description("Fires a fast smoke round that bursts on target.")
        .cooldown(8)
        .action(projectile)
        .build()
    )


def build_item():
    return (
        Item(ITEM_ID)
        .material(Material.GOLDEN_HOE)
        .display_name("<gold>Prototype Smoke Gun</gold>")
        .display_lore(
            "<gray>Right click to fire.</gray>",
            "<dark_gray>Leaves smoke and bursts on impact.</dark_gray>",
        )
        .bind_use(ABILITY_ID, cancel_event=True)
    )


def build_recipe():
    grid = CraftingGridSpec(
        pattern=[
            " IG",
            " SF",
            "S  ",
        ],
        keys={
            "I": ingredient_material(Material.IRON_INGOT, 1),
            "G": ingredient_material(Material.GOLD_INGOT, 1),
            "S": ingredient_material(Material.STICK, 1),
            "F": ingredient_material(Material.FLINT, 1),
        },
    )
    return CraftingRecipeSpec(
        recipe_id=RECIPE_ID,
        name="Prototype Smoke Gun",
        description="Craft a basic smoke-emitting test gun.",
        variants=[CraftingRecipeVariant(grid=grid, strict=True)],
        outputs=[output_item(ITEM_ID, amount=1)],
        discovery=CraftingDiscoverySpec(show_in_book=True),
    )


def main() -> None:
    ability_exporter = EffectsExporter("server/plugins/DungeonsReborn/effects/abilities")
    item_exporter = ItemExporter("server/plugins/DungeonsReborn/effects/items")
    crafting_exporter = CraftingExporter("server/plugins/DungeonsReborn/recipes")
    ability_path = ability_exporter.write_ability(build_ability(), filename=f"{ABILITY_ID}.yml")
    item_path = item_exporter.write_item(build_item(), filename=f"{ITEM_ID}.yml")
    recipe_path = crafting_exporter.write_recipe(build_recipe(), filename=f"{RECIPE_ID}.yml")
    print(f"exported: {ability_path}")
    print(f"exported: {item_path}")
    print(f"exported: {recipe_path}")


if __name__ == "__main__":
    main()
