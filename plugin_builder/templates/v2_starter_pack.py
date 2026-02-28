"""Starter v2 content pack template."""

from __future__ import annotations

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


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    strike = ability(
        ctx,
        symbol="ability.starter.strike",
        name="Starter Strike",
        action=fx.damage(5.0, policy=DamagePolicy.HOSTILE_DEFAULT),
    )

    blade = (
        item.create(ctx, symbol="item.starter.blade", name="Starter Blade", material=Material.GOLDEN_SWORD)
        .weapon_basic(attack_damage=5.0, attack_speed=1.1)
        .bind(bind.use(Ref("ability.starter.strike")))
    )

    pig = (
        mob.create(ctx, symbol="mob.starter.pig", name="Starter Pig", mob_type=EntityType.PIG)
        .show_name(True)
        .stats(health=20, damage=3, armor=0, speed=0.28)
    )

    craft = recipe.for_item(
        ctx,
        Ref("item.starter.blade"),
        symbol="recipe.starter.blade",
        pattern=[" GG", " SG", "S  "],
        keys=recipe.keys()
        .slot("G", recipe_ingredient.material(Material.GOLD_INGOT))
        .slot("S", recipe_ingredient.material(Material.STICK)),
        name="Craft Starter Blade",
    ).discovery(show_in_book=True)

    pack.add(strike, blade, pig, craft)
    return pack


if __name__ == "__main__":
    build_v2().export("./out")
