# DungeonsReborn Builder (V2)

`dungeonsreborn_builder` is now **V2-only**.

Use:

```python
from dungeonsreborn_builder.v2 import ...
```

Legacy V1 root imports are removed.

## Quick Start

```python
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
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    hit = ability(
        ctx,
        symbol="ability.demo.hit",
        name="Demo Hit",
        action=fx.damage(4.0, policy=DamagePolicy.HOSTILE_DEFAULT),
    )

    blade = (
        item.create(ctx, symbol="item.demo.blade", name="Demo Blade", material=Material.GOLDEN_SWORD)
        .bind(bind.use(Ref("ability.demo.hit")))
    )

    pig = (
        mob.create(ctx, symbol="mob.demo.pig", name="Demo Pig", mob_type=EntityType.PIG)
        .stats(health=20, damage=4, armor=1, speed=0.30)
    )

    craft = recipe.for_item(
        ctx,
        Ref("item.demo.blade"),
        symbol="recipe.demo.blade",
        pattern=[" GG", " SG", "S  "],
        keys={"G": Material.GOLD_INGOT, "S": Material.STICK},
    ).discovery(show_in_book=True)

    pack.add(hit, blade, pig, craft)
    return pack


if __name__ == "__main__":
    build_v2().export("./out")
```

## CLI

```bash
python -m dungeonsreborn_builder build-v2 ./pack.py -o ./out
python -m dungeonsreborn_builder validate-v2 ./pack.py --strict
python -m dungeonsreborn_builder preview ./pack.py
python -m dungeonsreborn_builder id-map ./pack.py
python -m dungeonsreborn_builder new ./my_pack --template v2_starter_pack.py
python -m dungeonsreborn_builder migrate-v1-to-v2 ./scripts
```

`build` and `validate` are temporary aliases to V2 commands.

## Typed Enums and Explicit Tokens

Closed sets use enums (`Material`, `EntityType`, `Sound`, `Particle`, `MobAiProfile`, etc.).

If you need a token not present in enum constants, use explicit custom token helpers:

```python
from dungeonsreborn_builder.v2 import custom_material, custom_sound

custom_material("NETHERITE_SWORD")
custom_sound("ENTITY_WARDEN_SONIC_BOOM")
```

## Edible Items (V2)

Builder v2 supports true edible components (food + consumable + cooldown + remainder):

```python
from dungeonsreborn_builder.v2 import ItemUseAnimation, Material, Sound, consume_fx, consume_status, item

tonic = (
    item.create(ctx, symbol="item.demo.tonic", name="Demo Tonic", material=Material.POTION)
    .edible(
        nutrition=1,
        saturation=0.2,
        can_always_eat=True,
        consume_seconds=1.0,
        animation=ItemUseAnimation.DRINK,
        sound=Sound.ENTITY_GENERIC_DRINK,
        effects=[
            consume_fx.apply_status_effects(
                [consume_status.effect("SPEED", duration_ticks=120, amplifier=1)],
                probability=1.0,
            )
        ],
        cooldown_seconds=2.0,
        cooldown_group="dungeonsreborn:tonic",
        remainder_material=Material.GLASS_BOTTLE,
    )
)
```

Note: top-level YAML `consumable: stack|durability` is still the cast-cost behavior for item bindings.  
True eating behavior is configured with `item.meta.components.*` (or `item.edible` alias in YAML input).

## Bundles

Available built-in bundles:

- `GhostBundle`
- `WeaponBundle`
- `ConsumableBundle`
- `EliteMobBundle`
- `TrialRewardBundle`

Use with `pack.add(Bundle.create(ctx, ...))`.

## Templates

The following templates are bundled under `plugin_builder/templates/`:

- `v2_starter_pack.py`
- `v2_mob_quickstart.py`
- `v2_weapon_bundle.py`
- `v2_shop_pack.py`
- `v2_campaign_pack.py`
- `v2_consumable_showcase.py`
