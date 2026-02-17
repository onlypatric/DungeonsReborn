# Builder Conventions

These rules keep builder output consistent with the plugin runtime loader.

## Output Paths

- Effects/abilities: `plugins/DungeonsReborn/effects/abilities/`
- Effects scripts: `plugins/DungeonsReborn/effects/scripts/`
- Item bindings: `plugins/DungeonsReborn/effects/items/`
- Mobs: `plugins/DungeonsReborn/mobs/` (one file per mob)
- Loot pools: `plugins/DungeonsReborn/loot/`
- Quests: `plugins/DungeonsReborn/quests/`
- Shops: `plugins/DungeonsReborn/shops.yml`
- Classes: `plugins/DungeonsReborn/classes.yml`

## YAML Structure

- All files should be valid YAML.
- Multiline text should use `|-` blocks.
- For effects, prefer one ability per file and keep `schemaVersion: 1` at the top.

## Naming

- IDs use lowercase snake_case.
- File names match their main ID (`<id>.yml`).

## MiniMessage

`name`, `description`, and UI strings support MiniMessage.

## Vanilla Enums

Use enum-style accessors for vanilla identifiers instead of raw strings:

- `Material.DIAMOND_SWORD`
- `EntityType.ZOMBIE`
- `Particle.FLAME`
