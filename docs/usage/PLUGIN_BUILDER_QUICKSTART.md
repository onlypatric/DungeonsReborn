# Plugin Builder Quickstart (V2)

Use only V2 imports:

```python
from dungeonsreborn_builder.v2 import BuildContext, pack_v2
```

## Minimal Pack

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
)


def build_v2():
    ctx = BuildContext(strict=True, profile="dev")
    pack = pack_v2(ctx)

    proc = ability(
        ctx,
        symbol="ability.quick.proc",
        name="Quick Proc",
        action=fx.damage(3.0, policy=DamagePolicy.HOSTILE_DEFAULT),
    )

    wand = (
        item.create(ctx, symbol="item.quick.wand", name="Quick Wand", material=Material.STICK)
        .bind(bind.use(Ref("ability.quick.proc")))
    )

    pack.add(proc, wand)
    return pack
```

## Export

```bash
python -m dungeonsreborn_builder build-v2 ./pack.py -o ./out
```
