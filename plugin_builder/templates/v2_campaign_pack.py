"""Template demonstrating multiple domain bundles in one pack."""

from __future__ import annotations

from dungeonsreborn_builder.v2 import (
    BuildContext,
    ConsumableBundle,
    EliteMobBundle,
    EntityType,
    GhostBundle,
    TrialRewardBundle,
    WeaponBundle,
    pack_v2,
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)
    pack.add(
        GhostBundle.create(ctx, name="Restless Spirit", mob_type=EntityType.ZOMBIE),
        WeaponBundle.create(ctx, weapon_name="Iron Fang"),
        ConsumableBundle.create(ctx, name="Focus Draught"),
        EliteMobBundle.create(ctx, name="Arena Boar", mob_type=EntityType.PIG),
        TrialRewardBundle.create(ctx, name="Trial Sigil"),
    )
    return pack


if __name__ == "__main__":
    build_v2().export("./out")
