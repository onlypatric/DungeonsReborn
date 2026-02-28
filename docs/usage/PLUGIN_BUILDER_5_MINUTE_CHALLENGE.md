# Plugin Builder 5 Minute Challenge (V2)

Goal: make one ability, one item, one recipe in under 5 minutes.

```python
from dungeonsreborn_builder.v2 import (
    BuildContext,
    DamagePolicy,
    Material,
    Ref,
    ability,
    bind,
    fx,
    item,
    pack_v2,
    recipe,
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    hit = ability(
        ctx,
        symbol="ability.challenge.hit",
        name="Challenge Hit",
        action=fx.damage(4.0, policy=DamagePolicy.HOSTILE_DEFAULT),
    )

    blade = item.create(ctx, symbol="item.challenge.blade", name="Challenge Blade", material=Material.GOLDEN_SWORD).bind(
        bind.use(Ref("ability.challenge.hit"))
    )

    craft = recipe.for_item(
        ctx,
        Ref("item.challenge.blade"),
        symbol="recipe.challenge.blade",
        pattern=[" GG", " SG", "S  "],
        keys={"G": Material.GOLD_INGOT, "S": Material.STICK},
    ).discovery(show_in_book=True)

    pack.add(hit, blade, craft)
    return pack
```

Build:

```bash
python -m dungeonsreborn_builder build-v2 ./pack.py -o ./out
```
