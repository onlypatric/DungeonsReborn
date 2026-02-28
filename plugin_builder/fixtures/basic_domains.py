"""Fixtures for tests: minimal v2 content for each domain."""

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
    quest,
    recipe,
    recipe_ingredient,
    shop,
    upgrade,
)


def build_v2(output_dir: str) -> list[str]:
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    ping = ability(ctx, symbol="ability.fixture.ping", name="Ping", action=fx.damage(1.0, policy=DamagePolicy.HOSTILE_DEFAULT))
    blade = item.create(ctx, symbol="item.fixture.blade", name="Fixture Blade", material=Material.STONE).bind(
        bind.use(Ref("ability.fixture.ping"))
    )
    beast = (
        mob.create(ctx, symbol="mob.fixture.beast", name="Fixture Beast", mob_type=EntityType.ZOMBIE)
        .stats(health=20, damage=3, armor=1, speed=0.28)
        .events(on_hit=Ref("ability.fixture.ping"))
    )
    craft = recipe.for_item(
        ctx,
        Ref("item.fixture.blade"),
        symbol="recipe.fixture.blade",
        pattern=[" S ", " S ", " S "],
        keys=recipe.keys().slot("S", recipe_ingredient.material(Material.STICK)),
    )
    mission = quest.create(ctx, symbol="quest.fixture.hunt", name="Fixture Hunt").kill_mob(Ref("mob.fixture.beast"), count=1)
    vendor = shop.create(ctx, symbol="shop.fixture.vendor", title="Fixture Vendor").sell(Ref("item.fixture.blade"), cost_tokens=1)
    tune = upgrade.create(ctx, symbol="upgrade.fixture.blade", name="Fixture Upgrade").for_item(Ref("item.fixture.blade"))

    pack.add(ping, blade, beast, craft, mission, vendor, tune)
    return pack.export(output_dir)


if __name__ == "__main__":
    build_v2("./out")
