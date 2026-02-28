"""Template showing a linked weapon bundle."""

from __future__ import annotations

from dungeonsreborn_builder.v2 import BuildContext, Material, WeaponBundle, pack_v2


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)
    pack.add(WeaponBundle.create(ctx, weapon_name="Storm Saber", material=Material.IRON_SWORD))
    return pack


if __name__ == "__main__":
    build_v2().export("./out")
