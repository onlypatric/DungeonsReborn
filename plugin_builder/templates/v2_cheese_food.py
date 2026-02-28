"""Cheese food item showcase using the v2 edible item system."""

from __future__ import annotations

from dungeonsreborn_builder.v2 import BuildContext, ItemUseAnimation, Material, Sound, item, pack_v2

CHEESE_TEXTURE = (
    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUv"
    "YjUzOWNjMTcwNjZiMmFmYjE2MWE4YTNmYTk4YWFjZDY1MGYyNjk1ZjUyMWEyNDc2ZmIzY2ViN2ZiMjg2NzY3YiJ9fX0="
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    cheese = (
        item.create(
            ctx,
            symbol="item.food.cheese_head",
            name="<gold>Cheese Wheel Slice</gold>",
            material=Material.PLAYER_HEAD,
        )
        .lore(
            "<gray>A rich, aged cheese carved into a snack-sized chunk.</gray>",
            "<yellow>Restores hunger and saturation.</yellow>",
        )
        .head_texture(CHEESE_TEXTURE)
        .edible(
            nutrition=7,
            saturation=8.6,
            can_always_eat=True,
            consume_seconds=1.4,
            animation=ItemUseAnimation.EAT,
            sound=Sound.ENTITY_GENERIC_EAT,
            has_particles=True,
            cooldown_seconds=0.8,
            cooldown_group="dungeonsreborn:food_cheese",
        )
    )

    pack.add(cheese)
    return pack


def main() -> None:
    output = "server/plugins/DungeonsReborn"
    paths = build_v2().export(output)
    for path in paths:
        if path.endswith("item_food_cheese_head.yml"):
            print(f"exported: {path}")


if __name__ == "__main__":
    main()
