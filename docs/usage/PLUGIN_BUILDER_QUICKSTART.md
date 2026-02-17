# Plugin Builder Quickstart

Goal: build and export content without writing YAML.

## Install/Run
- Use the builder via `python -m dungeonsreborn_builder` from the repo root.

## 1-Minute Example (Item + Ability)
```python
from dungeonsreborn_builder import AbilityBuilder, ContentPack, Item, Material, Particle, damage, particles_point, sound

spark = (
    AbilityBuilder("spark_burst")
    .name("<aqua>Spark Burst</aqua>")
    .action(sound("minecraft:block.note_block.pling"))
    .action(particles_point(Particle.END_ROD, count=8))
    .action(damage(2.0))
)

wand = (
    Item("spark_wand")
    .name("<gold>Spark Wand</gold>")
    .material(Material.STICK)
    .display_lore("<yellow>Right-click</yellow> to spark.")
    .bind_use("spark_burst")
)

pack = ContentPack().add(spark, wand)
pack.export("./plugins/DungeonsReborn")
```

## CLI
- Build: `python -m dungeonsreborn_builder build path/to/pack.py -o ./plugins/DungeonsReborn`
- Validate: `python -m dungeonsreborn_builder validate path/to/pack.py --strict`
- Preview: `python -m dungeonsreborn_builder preview path/to/pack.py`
- Watch: `python -m dungeonsreborn_builder watch path/to/pack.py -o ./plugins/DungeonsReborn`
- New project: `python -m dungeonsreborn_builder new ./my_pack --template starter_kit_pack.py`

## Where Files Go
- Abilities: `effects/abilities/*.yml`
- Items: `effects/items/*.yml`
- Mobs: `mobs/*.yml`
- Quests: `quests/*.yml`
- Shops: `shops/*.yml`
- Locale: `locales/en/builder.yml`
- GUI icons: `heads_gui_<theme>.yml`
