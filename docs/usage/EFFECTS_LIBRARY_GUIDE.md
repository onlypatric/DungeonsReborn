# Effects Library Guide

This document explains how to define and use effects (abilities) and item bindings in DungeonsReborn.
It covers the YAML format, the DSL scripting hook, and the runtime commands used for testing.

## File Layout

- `plugins/DungeonsReborn/effects.yml`
  - Main ability library.
  - Contains `abilities:` and optional `macros:`.
- `plugins/DungeonsReborn/effects/abilities/*.yml`
  - Optional additional ability files.
  - You can also create a folder per ability with `ability.yml`.
- `plugins/DungeonsReborn/effects/scripts/*.es`
  - DSL scripts referenced from YAML (`script.file`).
- `plugins/DungeonsReborn/effects/items/*.yml`
  - Item definitions and their bindings to abilities.

## Commands

- `/dr effects reload`
  - Reloads YAML abilities, scripts, and item bindings.
- `/dr effects cast <ability_id>`
  - Manually cast an ability to test it.
- `/dr effects debug on`
  - Enables verbose logging for casts and interactions.

## Ability Definition (YAML)

Top level schema:

```yaml
schemaVersion: 1

abilities:
  my_ability_id:
    name: "<gold><bold>My Ability</bold></gold>"
    description: |-
      <gray>Multi-line description.</gray>
      <dark_gray>MiniMessage supported.</dark_gray>
    cooldown:
      ticks: 60
      key: "optional-cooldown-key"
    requirements:
      - type: permission
        permission: "dungeonsreborn.ability.my_ability_id"
        message: "<red>You cannot use this.</red>"
    costs:
      - type: mana
        amount: 10
      - type: durability
        damage: 2
        allowBreak: false
    action:
      type: sequence
      actions:
        - type: particles_ring
          particle: END_ROD
          radius: 1.6
          points: 32
          count: 1
        - type: sound
          sound: minecraft:block.note_block.pling
          volume: 0.9
          pitch: 1.4
```

Notes:
- `name` and `description` are MiniMessage compatible. If MiniMessage parsing fails, legacy `§` color codes are used.
- `description` can be multi-line via `|-`.
- `cooldown.key` lets you share cooldowns across abilities.
- `requirements` block a cast with an optional message.
- `costs` apply on cast and can show an action bar when failing.

### Requirements

Supported requirement types:

- `sneaking`
- `permission`
- `has_item_tag` (PDC tag)

Example:

```yaml
requirements:
  - type: sneaking
    message: "<yellow>Hold shift.</yellow>"
  - type: has_item_tag
    key: "dungeonsreborn:spell_focus"
```

### Costs

Supported cost types:

- `mana`
- `consume_item` / `consume_main_hand`
- `durability` / `durability_main_hand`

Example:

```yaml
costs:
  - type: mana
    amount: 15
  - type: consume_item
    amount: 1
```

## Triggers (Ability-Level Interactions)

You can register direct interactions for an ability via `triggers:`. These become `InteractBinding`s.

```yaml
triggers:
  - type: interact
    click: RIGHT_CLICK
    requireSneaking: false
    cancelEvent: true
    permission: "dungeonsreborn.cast.my_ability"
    item:
      type: material
      material: STICK
```

Supported `click` values:
- `RIGHT_CLICK`
- `LEFT_CLICK`

## Item Bindings (Item YAML)

Item files live in `plugins/DungeonsReborn/effects/items/*.yml`.

```yaml
item:
  ==: org.bukkit.inventory.ItemStack
  type: DIAMOND_SWORD
  meta:
    display-name: "<gold>Sunblade</gold>"
    lore:
      - "<gray>Right click to cast.</gray>"

bindings:
  - type: interact
    click: RIGHT_CLICK
    ability: my_ability_id
    requireSneaking: false
    cancelEvent: true

  - type: passive
    ability: passive_shield
    periodTicks: 40
    slots: [hand, off_hand]
```

Notes:
- `bindings` (or `triggers`) is required and must not be empty.
- `passive` bindings support `periodTicks` and `slots`.
- `slots` supports: `hand`, `off_hand`, `head`, `chest`, `legs`, `feet`, `armor`, `hands`, `all`.
- `ability` must exist at reload time.

## Actions

Actions are declared with `type: ...` under `action`.
Most actions accept numeric values and support inline expressions.

Core action categories:

- Flow and timing:
  - `sequence`, `delay`, `repeat`, `chance`, `random_choice`, `random_choice_weighted`
- Messaging:
  - `message`, `action_bar`, `title`
- Targeting + casting:
  - `target`, `for_each_target`, `raycast_hit_entity`, `raycast_hit_block`, `projectile`
- Movement:
  - `teleport`, `dash`, `set_velocity`
- Damage/heal:
  - `damage`, `damage_true`, `damage_percent`, `damage_chain`, `damage_dot`, `damage_crit`
  - `heal`
- Potions and status:
  - `potion`, `area_effect_cloud`
- Particles + sound:
  - `particles_point`, `particles_line`, `particles_ring`, `particles_arc`, `particles_disk`
  - `particles_sphere_shell`, `particles_sphere_filled`, `particles_helix`
  - `particles_bezier`, `particles_spline`, `particles_cone`, `particles_cylinder`, `particles_box`, `particles_polygon`
  - `preset_shockwave`, `preset_orbit`, `preset_swirl`, `preset_beam_chargeup`
  - `sound`, `sound_at`
- Projectiles and summonables:
  - `launch_wither_skull`, `launch_fireball`, `launch_dragon_fireball`
  - `arrow_volley`, `throw_trident`, `splash_potion`
  - `evoker_fangs_line`, `strike_lightning`, `explode_at`
- Minions:
  - `minion_spawn` (requires mob system enabled)

If you need the exact signature of a specific action, check:
- `docs/guides/SPELLS_EFFECTS_ENGINE_SPEC.md`
- `src/main/java/dev/patric/dungeonsreborn/effects/config/EffectsYamlAbilities.java`

## Particles and Custom Data

Most particle actions accept `particle` and `data`.
Data is required for some particle types.

Examples:

```yaml
- type: particles_point
  particle: DUST
  data:
    color: "#ff66cc"
    size: 1.4

- type: particles_point
  particle: BLOCK
  data: "minecraft:stone"

- type: particles_point
  particle: ITEM
  data:
    type: NETHER_STAR
    amount: 1
```

Customizable particles are listed in:

## Variables and Expressions

Numeric fields support inline expressions (e.g., `radius: lerp(1, 5, t)`).
There are scoped variables:

- `cast:` for the current cast
- `player:` for the caster
- `entity:` for the current target or last entity

Example:

```yaml
- type: set_var
  scope: player
  key: "charge"
  value: min(var("player:charge") + 1, 5)
```

## DSL Scripts

Instead of `action`, you can attach a script:

```yaml
script:
  language: dsl-v1
  file: "my_spell.es"
```

Or inline:

```yaml
script:
  language: dsl-v1
  source: |
    on_cast {
      particles.ring particle=END_ROD radius=2 points=32 count=1
    }
```

Scripts are loaded from `plugins/DungeonsReborn/effects/scripts`.

## Debugging and Errors

- YAML errors are logged with full paths.
- Ability ids are normalized to `[a-z0-9_.:-]`.
- If an ability or binding fails to load, it is skipped and logged.

## Quick Test Loop

1) Edit `plugins/DungeonsReborn/effects.yml`.
2) `/dr effects reload`
3) `/dr effects cast <ability_id>`

--------------------------------------------------------------------------------
## Full YAML Showcase (Library Authoring)

Comment before the YAML block: this complete `effects.yml` example shows the
full authoring flow (macros, abilities, triggers, actions, and a script-backed ability).

```yaml
# FULL effects.yml example (authoring showcase).
schemaVersion: 1 # integer >= 1

# Optional reload behavior.
options:
  cancelRunningOnReload: false # boolean

# Reusable macros.
macros:
  flash:
    type: particles_ring # action type id
    particle: END_ROD # Particle enum or minecraft:<key>
    radius: 1.2 # double >= 0.0
    points: 20 # int > 0
    count: 1 # int >= 1

abilities:
  library_showcase:
    name: "<gold>Library Showcase</gold>" # MiniMessage string
    description: "<gray>Demonstrates macros, triggers, and actions.</gray>" # MiniMessage string
    requirements:
      - type: permission # requirement id
        permission: "dungeonsreborn.spells.library_showcase" # permission node string
        message: "<red>No permission.</red>" # MiniMessage string
    costs:
      - type: mana # cost id
        amount: 8 # number > 0
    cooldown:
      ticks: 40 # long > 0
    triggers:
      - type: interact # trigger id
        click: RIGHT_CLICK # LEFT_CLICK | RIGHT_CLICK
        requireSneaking: false # boolean
        cancelEvent: true # boolean
        item:
          type: material # matcher id
          material: DIAMOND_SWORD # Bukkit Material enum
    action:
      type: sequence # action type id
      actions:
        - type: include # action type id
          macro: flash # macro id string
        - type: sound # action type id
          sound: minecraft:entity.player.attack.sweep # namespaced key or SOUND enum
          volume: 1.0 # float >= 0.0 (typical 0.0-2.0)
          pitch: 1.2 # float > 0.0 (typical 0.5-2.0)
        - type: particles_line # action type id
          particle: ELECTRIC_SPARK # Particle enum or minecraft:<key>
          length: 8.0 # double > 0.0
          step: 0.35 # double > 0.0
          count: 1 # int >= 1

  library_scripted:
    # Example of a script-backed ability (DSL).
    name: "<aqua>Library Scripted</aqua>" # MiniMessage string
    description: "<gray>Uses the DSL instead of YAML actions.</gray>" # MiniMessage string
    script:
      language: "dsl-v1" # fixed literal for DSL v1
      file: "library_scripted.es" # filename under effects/scripts/
```

Comment after the YAML block: use this as a template when authoring abilities. It
shows how to define reusable macros, bind an ability to a click trigger directly,
and how to swap an action graph for a DSL script.

### Full item binding file (manual bindings)

Comment before the YAML block: this item file shows how to bind abilities without
embedding triggers in the ability itself.

```yaml
# FULL item binding example (plugins/DungeonsReborn/effects/items/library-blade.yml)
schemaVersion: 1 # integer >= 1
item:
  type: material # matcher id
  material: DIAMOND_SWORD # Bukkit Material enum
bindings:
  - type: interact # binding type
    click: RIGHT_CLICK # LEFT_CLICK | RIGHT_CLICK
    ability: library_showcase # ability id string
    requireSneaking: false # boolean
    cancelEvent: true # boolean
  - type: passive # binding type
    ability: library_scripted # ability id string
    periodTicks: 40 # long > 0
    slots:
      - HAND # HAND|OFF_HAND|HEAD|CHEST|LEGS|FEET
```

Comment after the YAML block: this file demonstrates the standalone item-binding
format. Use it when you want an item to cast abilities on click or on a passive
interval without modifying the ability definitions.
