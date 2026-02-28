"""Template showing item + shop linkage."""

from __future__ import annotations

from dungeonsreborn_builder.v2 import BuildContext, Material, Ref, item, pack_v2, shop


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    tonic = (
        item.create(ctx, symbol="item.shop.tonic", name="Healing Tonic", material=Material.POTION)
        .edible(
            nutrition=1,
            saturation=0.2,
            can_always_eat=True,
            animation="DRINK",
            sound="ENTITY_GENERIC_DRINK",
            cooldown_seconds=1.5,
            remainder_material=Material.GLASS_BOTTLE,
            remainder_amount=1,
        )
    )
    vendor = shop.create(ctx, symbol="shop.shop.tonics", title="Tonic Vendor").sell(Ref("item.shop.tonic"), cost_tokens=12)

    pack.add(tonic, vendor)
    return pack


if __name__ == "__main__":
    build_v2().export("./out")
