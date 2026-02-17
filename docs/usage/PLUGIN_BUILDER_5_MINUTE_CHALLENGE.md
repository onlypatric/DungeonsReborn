# Plugin Builder 5-Minute Challenge

Goal: create a complex pack in under 5 minutes.

## Checklist
1) Create a 60-second ability with particles + damage.
2) Create an item bound to the ability.
3) Create a mob that uses the ability.
4) Create a quest to defeat the mob.
5) Export the pack.

## Reference Script
```python
from dungeonsreborn_builder import (
    AbilityBuilder,
    ContentPack,
    Item,
    Material,
    Mob,
    EntityType,
    MobAttack,
    MobAttackTrigger,
    Quest,
    Particle,
    damage,
    particles_ring,
    sound,
)

storm = (
    AbilityBuilder("storm_cycle")
    .name("<aqua>Storm Cycle</aqua>")
    .action(sound("minecraft:entity.lightning_bolt.thunder"))
    .action(particles_ring(Particle.ELECTRIC_SPARK, count=20))
    .action(damage(6.0))
)

wand = Item("storm_wand").name("Storm Wand").material(Material.BLAZE_ROD).bind_use("storm_cycle")
mob = (
    Mob("storm_walker")
    .name("Storm Walker")
    .mob_type(EntityType.STRAY)
    .main_attack(MobAttack(ability="storm_cycle", trigger=MobAttackTrigger.RANGED, cooldown_ticks=60))
    .stats(health=40, damage=5)
)
quest = Quest("storm_trial", "Storm Trial").kill_mob("storm_walker", count=3).reward_tokens(25)

ContentPack().add(storm, wand, mob, quest).export("./plugins/DungeonsReborn")
```
