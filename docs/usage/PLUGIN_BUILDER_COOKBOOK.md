# Plugin Builder Cookbook

Short recipes for common content patterns.

## Basic Item
```python
from dungeonsreborn_builder import Item, Material

sword = Item("iron_saber").name("Iron Saber").material(Material.IRON_SWORD)
```

## Item With Ability
```python
from dungeonsreborn_builder import AbilityBuilder, Item, Material, Particle, damage, particles_point

slash = AbilityBuilder("quick_slash").name("Quick Slash").action(damage(4.0))
blade = Item("quick_blade").name("Quick Blade").material(Material.IRON_SWORD).bind_use("quick_slash")
```

## Simple Mob
```python
from dungeonsreborn_builder import Mob, EntityType

rat = Mob("sewer_rat").name("Sewer Rat").mob_type(EntityType.CAVE_SPIDER).stats(health=10, damage=2)
```

## Mob With Ability Attack
```python
from dungeonsreborn_builder import Mob, EntityType, MobAttack, MobAttackTrigger

archer = (
    Mob("skeleton_archer")
    .name("Skeleton Archer")
    .mob_type(EntityType.SKELETON)
    .main_attack(MobAttack(ability="arc_bolt", trigger=MobAttackTrigger.RANGED))
)
```

## Simple Quest
```python
from dungeonsreborn_builder import Quest

quest = Quest("first_blood", "First Blood").kill_mob("sewer_rat", count=3).reward_tokens(5)
```

## Simple Shop
```python
from dungeonsreborn_builder import Shop, Item, Material

item = Item("health_potion").name("Health Potion").material(Material.POTION)
shop = Shop("consumables", "Consumables").trade(item, cost_tokens=10)
```

## Difficulty Scaling
```python
from dungeonsreborn_builder import ContentPack, scale_pack

pack = ContentPack().add(...)
scale_pack(pack, "hard")
pack.export("./plugins/DungeonsReborn")
```
