# Plugin Builder Cookbook (V2)

## Quando usare `vfx.*` vs `fx.particles_*`
- Usa `vfx.*` per effetti riusabili con timeline/archetype/modifier.
- Usa `fx.particles_*` quando ti serve controllo low-level puntuale.

## 1) Ability con `vfx.archetype(...)`

```python
from dungeonsreborn_builder.v2 import (
    DamagePolicy,
    Sound,
    VfxAnchorId,
    VfxArchetypeId,
    VfxLod,
    ability,
    fx,
    vfx,
)

burst_vfx = vfx.compile(
    vfx.archetype(
        VfxArchetypeId.IMPACT_HEAVY_BURST,
        lod=VfxLod.MEDIUM,
        anchor=VfxAnchorId.ORIGIN_STATIC,
    )
)

ability(
    ctx,
    symbol="ability.cookbook.burst",
    name="Burst",
    action=fx.sequence(
        fx.sound(Sound.ENTITY_GENERIC_EXPLODE, volume=0.9, pitch=1.0),
        burst_vfx,
        fx.damage(5.0, policy=DamagePolicy.HOSTILE_DEFAULT),
    ),
)
```

## 2) Timeline custom con `vfx.clip(...)`

```python
from dungeonsreborn_builder.v2 import (
    Particle,
    VfxClipId,
    VfxModifierId,
    ability,
    fx,
    vfx,
)

custom_vfx = vfx.compile(
    vfx.timeline(
        anticipation=[vfx.clip(VfxClipId.RING_PULSE, particle=Particle.SMOKE, radius=1.0)],
        activation=[vfx.clip(VfxClipId.IMPACT_CORE, radius=1.2)],
        decay=[vfx.clip(VfxClipId.POINT_FLASH, particle=Particle.END_ROD)],
        activation_modifiers=[vfx.modifier(VfxModifierId.SCALE_RAMP, factor=1.2)],
    )
)

ability(
    ctx,
    symbol="ability.cookbook.timeline",
    name="Timeline Demo",
    action=fx.sequence(custom_vfx),
)
```

## 3) Item bind inline ability

```python
from dungeonsreborn_builder.v2 import Material, bind, item

item.create(ctx, symbol="item.cookbook.blade", name="Cookbook Blade", material=Material.GOLDEN_SWORD).bind(
    bind.use(
        ability(
            ctx,
            symbol="ability.cookbook.inline",
            name="Inline",
            action=fx.damage(4.0),
        )
    )
)
```

## 4) Mob con evento ability inline

```python
from dungeonsreborn_builder.v2 import EntityType, mob

mob.create(ctx, symbol="mob.cookbook.guard", name="Guard", mob_type=EntityType.ZOMBIE).events(
    on_hit=ability(
        ctx,
        symbol="ability.cookbook.guard_hit",
        name="Guard Hit",
        action=fx.damage(2.0),
    )
)
```

## 5) Particelle low-level (fallback)

```python
from dungeonsreborn_builder.v2 import Particle, fx

low_level = fx.particles_sphere_shell(
    Particle.DUST,
    radius=1.2,
    points=24,
    dust=fx.dust(color="#FFD700", size=1.1),
)
```
