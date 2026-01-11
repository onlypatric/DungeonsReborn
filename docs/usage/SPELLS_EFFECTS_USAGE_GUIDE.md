# Spells/Effects Engine - Usage Guide

This guide is written for server admins and content creators. It explains how to create
abilities (effects), how to bind them to items, and how to test them in-game. No coding
knowledge is required.

--------------------------------------------------------------------------------
## Chapter 1 - File Layout and Reloading

### 1.1 Where files live

- Main template file:
  - `plugins/DungeonsReborn/effects.yml`
- Optional per-ability files:
  - `plugins/DungeonsReborn/effects/abilities/*.yml`
- Optional scripts (DSL):
  - `plugins/DungeonsReborn/effects/scripts/*.es`
- Item bindings:
  - `plugins/DungeonsReborn/effects/items/*.yml`

Tip: you can keep one ability per file in `effects/abilities/` to keep things organized.

### 1.2 Reloading and testing

After edits:

```
/dr effects reload
```

Test an ability directly:

```
/dr effects cast <ability_id>
```

Enable debug logs (useful while building):

```
/dr effects debug on
```

### 1.3 Logging levels (config.yml)

You can control service logs (GUI, effects, mobs, item bindings) in `plugins/DungeonsReborn/config.yml`:

```yaml
logging:
  gui: INFO
  effects: INFO
  mobs: INFO
  bindings: INFO
```

Supported levels: `DEBUG`, `INFO`, `WARNING`, `ERROR`.

After editing, reload logging without restarting:

```
/dr effects logging reload
```

--------------------------------------------------------------------------------
## Chapter 2 - Ability Basics

An ability has:
- An `id` (the key under `abilities:`)
- `name` and `description`
- optional `requirements` (who can cast)
- optional `costs` (mana, durability, items)
- optional `cooldown`
- a main `action` (or a `script`)

### 2.1 Minimal ability example

```yaml
abilities:
  fire_pulse:
    name: "<red>Fire Pulse"
    description: "<gray>A short fiery blast.</gray>"
    action:
      type: particles_ring
      particle: FLAME
      radius: 1.4
      points: 28
      count: 1
```

### 2.2 Full example (ready to use)

```yaml
abilities:
  frost_bolt:
    name: "<aqua>Frost Bolt"
    description: |-
      <gray>Launch a bolt of ice.</gray>
      <dark_gray>Slows enemies on hit.</dark_gray>
    requirements:
      - type: permission
        permission: "dungeonsreborn.spells.frost_bolt"
        message: "<red>No permission.</red>"
    costs:
      - type: mana
        amount: 12
    cooldown:
      ticks: 40
    action:
      type: raycast_hit_entity
      range: 14
      onHit:
        type: sequence
        actions:
          - type: sound
            sound: minecraft:entity.snow_golem.shoot
            volume: 1.0
            pitch: 1.2
          - type: potion
            effect: SLOW
            durationTicks: 60
            amplifier: 0
          - type: particles_ring
            particle: SNOWFLAKE
            radius: 1.2
            points: 18
            count: 1
```

--------------------------------------------------------------------------------
## Chapter 3 - Requirements and Costs

### 3.1 Requirements

Supported:
- `permission`
- `sneaking`
- `has_item_tag` (NamespacedKey)

Example:

```yaml
requirements:
  - type: sneaking
    message: "<red>Sneak to cast.</red>"
```

### 3.2 Costs

Supported:
- `mana`
- `consume_item` (main hand)
- `durability` (main hand)

Example:

```yaml
costs:
  - type: mana
    amount: 8
  - type: durability
    damage: 2
    allowBreak: false
```

### 3.3 Cooldowns

```yaml
cooldown:
  ticks: 60
  key: "shared_group"  # optional
```

--------------------------------------------------------------------------------
## Chapter 4 - Binding Abilities to Clicks

You can bind abilities in two ways:

### 4.1 Bind inside the ability

```yaml
triggers:
  - type: interact
    click: RIGHT_CLICK
    requireSneaking: false
    cancelEvent: true
    item:
      type: material
      material: DIAMOND_SWORD
```

Shift clicks:
- Shift + Right Click = `click: RIGHT_CLICK` + `requireSneaking: true`
- Shift + Left Click = `click: LEFT_CLICK` + `requireSneaking: true`

### 4.2 Bind from item files

See Chapter 10 for full item binding files.

--------------------------------------------------------------------------------
## Chapter 5 - Particle Actions (Visuals)

These are the most common actions for effects. All particle actions support:
- `particle`
- `count`
- `offset`
- `extra`

### 5.1 Ring (basic aura)

```yaml
- type: particles_ring
  particle: END_ROD
  radius: 1.6
  points: 32
  count: 1
```

### 5.2 Line (beam)

```yaml
- type: particles_line
  particle: CRIT
  length: 10
  step: 0.3
  count: 1
```

### 5.3 Disk (ground circle)

```yaml
- type: particles_disk
  particle: SPORE_BLOSSOM_AIR
  radius: 2.4
  rings: 6
  pointsPerRing: 24
  count: 1
```

### 5.4 Sphere (shield)

```yaml
- type: particles_sphere_shell
  particle: END_ROD
  radius: 2.0
  points: 120
  count: 1
```

### 5.5 Helix (spiral)

```yaml
- type: particles_helix
  particle: ENCHANT
  radius: 1.1
  length: 5.0
  turns: 3
  points: 80
  count: 1
```

### 5.6 Bezier (curved beam)

```yaml
- type: particles_bezier
  particle: ELECTRIC_SPARK
  pointsPerMeter: 6
  maxPoints: 160
  count: 1
  p0: { forward: 0.0, up: 1.0 }
  p1: { forward: 2.0, up: 2.0 }
  p2: { forward: 4.0, up: 1.5 }
  p3: { forward: 6.0, up: 0.5 }
```

### 5.7 Other particle shapes

Available:
- `particles_arc`
- `particles_box`
- `particles_cone`
- `particles_cylinder`
- `particles_polygon`
- `particles_spline`

--------------------------------------------------------------------------------
## Chapter 6 - Sound, Messages, Titles

```yaml
- type: sound
  sound: minecraft:block.note_block.pling
  volume: 1.0
  pitch: 1.4

- type: message
  text: "<gold>You feel energized!"

- type: action_bar
  text: "<green>Mana: {mana}/{mana_max}"

- type: title
  title: "<red>Power Surge"
  subtitle: "<gray>Unleashed"
  fadeInTicks: 10
  stayTicks: 40
  fadeOutTicks: 10
```

Placeholders:
- `{player}`, `{ability}`, `{castId}`, `{tick}`
- `{target}`, `{target_type}`
- `{mana}`, `{mana_max}`
- `{var:key}` or `{var:cast:key}` / `{var:player:key}`

--------------------------------------------------------------------------------
## Chapter 7 - Targeting and Projectiles

### 7.1 Raycast (hit the first entity in front)

```yaml
- type: raycast_hit_entity
  range: 12
  onHit:
    type: damage
    amount: 6
```

### 7.2 Projectiles

```yaml
- type: projectile
  speed: 1.1
  lifetimeTicks: 60
  onHit:
    type: damage
    amount: 8
  onTick:
    type: particles_point
    particle: CRIT
    count: 1
```

### 7.3 For-each target (AOE)

```yaml
- type: for_each_target
  targeter:
    type: sphere
    radius: 5
  action:
    type: damage
    amount: 4
```

--------------------------------------------------------------------------------
## Chapter 8 - Damage, Healing, Knockback

### 8.1 Basic damage

```yaml
- type: damage
  amount: 6
```

### 8.2 Typed damage (resistances apply)

```yaml
- type: damage_typed
  amount: 10
  damageType: FIRE
```

### 8.3 Percent damage

```yaml
- type: damage_percent
  percent: 0.15
```

### 8.4 Damage over time

```yaml
- type: damage_over_time
  amount: 2
  ticks: 100
  periodTicks: 20
```

### 8.5 Chain damage

```yaml
- type: damage_chain
  amount: 4
  maxBounces: 4
  range: 6
```

### 8.6 Healing

```yaml
- type: heal
  amount: 6
```

### 8.7 Pull / Knockback

```yaml
- type: pull
  strength: 1.0
  radius: 4
```

```yaml
- type: knockback
  strength: 0.8
```

--------------------------------------------------------------------------------
## Chapter 9 - Variables and Expressions

You can use math expressions in numeric fields:

```yaml
radius: "expr: lerp(0.5, 3.0, t)"
```

Supported math:
- Operators: `+ - * / % ^`
- Functions: `min`, `max`, `clamp`, `lerp`, `rand`, `abs`, `floor`, `ceil`, `round`

Useful variables:
- `t` (animation time from 0 to 1)
- `mana`, `mana_max`
- `caster_health`, `caster_max_health`
- `distance`
- `var:<key>` (cast or player scope)

--------------------------------------------------------------------------------
## Chapter 10 - Item Files (Bindings)

Example item file:

```yaml
schemaVersion: 1

item:
  type: material
  material: NETHERITE_SWORD

bindings:
  - click: RIGHT_CLICK
    ability: frost_bolt
  - click: LEFT_CLICK
    ability: flame_slash
  - click: RIGHT_CLICK
    requireSneaking: true
    ability: arcane_burst
  - type: passive
    ability: stone_skin
    periodTicks: 40
    slots: [HAND, OFF_HAND]
```

Optional mana bonuses from items:

```yaml
mana:
  maxBonus: 20
  regenBonus: 1.5
```

--------------------------------------------------------------------------------
## Chapter 11 - Minions (Summon Helpers)

If enabled, you can summon minions with a YAML action:

```yaml
- type: minion_summon
  mob: "test_minion"
  count: 2
  durationTicks: 200
```

Minions are defined in `mobs.yml` like any other custom mob.

--------------------------------------------------------------------------------
## Chapter 12 - DSL Scripts (Optional)

DSL is fully documented in:

`docs/usage/SPELLS_EFFECTS_DSL_GUIDE.md`

Use it when you want to script abilities instead of writing YAML action graphs.

--------------------------------------------------------------------------------
## Chapter 13 - Troubleshooting

- No particles? Check settings:
  - `/dr effects particles quality 1`
  - `/dr effects particles budget 25000`
- Ability not found? Check console reload errors.
- Item not triggering? Verify the matcher and click type.
- YAML errors? Check indentation and use quotes around strings with `:`.

--------------------------------------------------------------------------------
## Chapter 14 - Quick Start Checklist

1. Create an ability under `abilities:`.
2. Add a trigger or create an item file.
3. Run `/dr effects reload`.
4. Test with `/dr effects cast <abilityId>`.

--------------------------------------------------------------------------------
## Chapter 15 - Full YAML Showcase (Everything in One Place)

Comment before the YAML block: this is a complete `effects.yml` showcase that uses
every major block you can configure (options, macros, requirements, costs, cooldown,
triggers, actions, particles, targeting, branching, minions, and scripts).

```yaml
# FULL effects.yml example (kitchen sink). Copy what you need, delete the rest.
schemaVersion: 1 # integer >= 1

# Global reload behavior.
options:
  cancelRunningOnReload: false # boolean (true/false)

# Reusable action snippets (macros) referenced by abilities.
macros:
  sparkle_burst:
    type: particles_ring # action type id
    particle: END_ROD # Particle enum or minecraft:<key>
    radius: 1.6 # double >= 0.0
    points: 28 # int > 0
    count: 1 # int >= 1
  impact_flash:
    type: sequence # action type id
    actions:
      - type: sound # action type id
        sound: minecraft:entity.player.attack.crit # namespaced key or SOUND enum
        volume: 0.9 # float >= 0.0 (typical 0.0-2.0)
        pitch: 1.4 # float > 0.0 (typical 0.5-2.0)
      - type: particles_ring # action type id
        particle: CRIT # Particle enum or minecraft:<key>
        radius: 1.0 # double >= 0.0
        points: 20 # int > 0
        count: 1 # int >= 1

# All abilities live under this block.
abilities:
  arcane_blast:
    # Name + description are MiniMessage (legacy fallback on parse failure).
    name: "<gradient:blue:aqua><bold>Arcane Blast</bold></gradient>" # MiniMessage string
    description: |- # MiniMessage, multiline allowed
      <gray>Unleash a burst of arcane energy.</gray>
      <dark_gray>Right click to cast, sneak-left for a burst volley.</dark_gray>

    # Who can cast.
    requirements:
      - type: permission # requirement id
        permission: "dungeonsreborn.spells.arcane_blast" # permission node string
        message: "<red>No permission.</red>" # MiniMessage string
      - type: sneaking # requirement id
        message: "<red>You must sneak for the alt cast.</red>" # MiniMessage string

    # What it costs.
    costs:
      - type: mana # cost id
        amount: 12 # number > 0
      - type: durability # cost id
        damage: 2 # int >= 0
        allowBreak: false # boolean

    # Cooldown (shared by key if you want).
    cooldown:
      ticks: 60 # long > 0
      key: "arcane_group" # string group id (optional)

    # Direct click bindings (optional, avoids separate item files).
    triggers:
      - type: interact # trigger id
        click: RIGHT_CLICK # LEFT_CLICK | RIGHT_CLICK
        requireSneaking: false # boolean
        cancelEvent: true # boolean
        item:
          type: material # matcher id
          material: DIAMOND_SWORD # Bukkit Material enum
      - type: interact # trigger id
        click: LEFT_CLICK # LEFT_CLICK | RIGHT_CLICK
        requireSneaking: true # boolean
        cancelEvent: true # boolean
        item:
          type: and # matcher id
          matchers:
            - type: material # matcher id
              material: DIAMOND_SWORD # Bukkit Material enum
            - type: lore_contains # matcher id
              text: "Arcane" # substring match (case-sensitive)

    # The action graph.
    action:
      type: sequence # action type id
      actions:
        - type: include # action type id
          macro: sparkle_burst # macro id string

        - type: message # action type id
          text: "<aqua>Arcane Blast!</aqua>" # MiniMessage string
        - type: action_bar # action type id
          text: "<gray>Mana: {mana}/{mana_max}</gray>" # MiniMessage string
        - type: title # action type id
          title: "<gold>Power Surge</gold>" # MiniMessage string
          subtitle: "<gray>Channeling...</gray>" # MiniMessage string
          fadeInTicks: 10 # long >= 0
          stayTicks: 30 # long >= 0
          fadeOutTicks: 10 # long >= 0

        # Timed burst.
        - type: delay # action type id
          ticks: 10 # long > 0
          then:
            type: particles_ring # action type id
            particle: ELECTRIC_SPARK # Particle enum or minecraft:<key>
            radius: 2.2 # double >= 0.0
            points: 36 # int > 0
            count: 1 # int >= 1

        # Repeating visual beam.
        - type: repeat_ticks # action type id
          periodTicks: 2 # long > 0
          times: 8 # int > 0
          action:
            type: particles_line # action type id
            particle: ELECTRIC_SPARK # Particle enum or minecraft:<key>
            length: 8.0 # double > 0.0
            step: 0.35 # double > 0.0
            count: 1 # int >= 1

        # Chance-based branch.
        - type: chance # action type id
          probability: 0.25 # double in [0.0, 1.0]
          then: { type: message, text: "<green>Lucky crit!</green>" } # MiniMessage string
          otherwise: { type: message, text: "<gray>No crit.</gray>" } # MiniMessage string

        # Target selection + per-target effects.
        - type: for_each_target # action type id
          targeter:
            type: sphere # self|look_ray|sphere|nearest|cone|box|cylinder|capsule_ray
            radius: 5.0 # double >= 0.0
            filter: mobs # any|players|mobs
            ignoreCaster: true # boolean
          originAt: origin # origin|last_hit|last_entity
          mode: each # each|first
          maxTargets: 6 # int >= 0 (0 = unlimited)
          then:
            type: sequence # action type id
            actions:
              - type: particles_ring # action type id
                particle: CRIT # Particle enum or minecraft:<key>
                radius: 0.9 # double >= 0.0
                points: 16 # int > 0
                count: 1 # int >= 1
                at: last_entity # origin|last_hit|last_entity
              - type: damage # action type id
                amount: 5 # double > 0.0
                policy: hostile_default # damage policy id (engine-defined)
              - type: potion # action type id
                effect: "minecraft:slowness" # PotionEffectType key or enum
                durationTicks: 40 # long > 0
                amplifier: 0 # int >= 0

        # Raycast hit.
        - type: raycast_hit_entity # action type id
          maxDistance: 16 # double > 0.0
          raySize: 0.35 # double > 0.0
          stopOnBlock: true # boolean
          ignoreCaster: true # boolean
          then:
            type: sequence # action type id
            actions:
              - type: include # action type id
                macro: impact_flash # macro id string
              - type: knockback # action type id
                horizontal: 1.1 # double >= 0.0
                vertical: 0.3 # double >= 0.0
          otherwise:
            type: message # action type id
            text: "<gray>No target in sight.</gray>" # MiniMessage string

        # Projectile hit (with trail).
        - type: projectile # action type id
          speedPerTick: 1.4 # double > 0.0
          maxDistance: 24 # double > 0.0
          hitRadius: 0.35 # double > 0.0
          ignoreCaster: true # boolean
          blockCollision: STOP # STOP|PASS_THROUGH|BOUNCE
          trail:
            particle: END_ROD # Particle enum or minecraft:<key>
            count: 1 # int >= 1
            offset: 0.0 # double >= 0.0
            extra: 0.0 # double >= 0.0
          onHit:
            type: sequence # action type id
            actions:
              - type: particles_ring # action type id
                particle: CRIT # Particle enum or minecraft:<key>
                radius: 1.0 # double >= 0.0
                points: 20 # int > 0
                count: 1 # int >= 1
                at: last_hit # origin|last_hit|last_entity
              - type: damage # action type id
                amount: 7 # double > 0.0
                policy: hostile_default # damage policy id (engine-defined)

        # Minions (requires the minion system to be enabled).
        - type: minion_summon # action type id
          mob: "arcane_minion" # mob id string (must exist in mobs.yml)
          id: "arcane_blast_minions" # minion group id string
          count: 2 # int > 0
          durationTicks: 200 # long > 0
          radius: 1.6 # double >= 0.0
          mode: DEFENSIVE # AGGRESSIVE|DEFENSIVE|PASSIVE
          passives:
            - ability: "minion_pulse" # ability id string
              periodTicks: 40 # long > 0
          specialAttacks:
            - ability: "minion_bolt" # ability id string
              cooldownTicks: 60 # long > 0
              chance: 0.5 # double in [0.0, 1.0]
              requireTarget: true # boolean

        # Call another ability (subgraph).
        - type: invoke_ability # action type id
          ability: "arcane_echo" # ability id string
          mode: subgraph # subgraph|cast

  arcane_echo:
    name: "<aqua>Arcane Echo</aqua>" # MiniMessage string
    description: "<gray>Secondary pulse triggered by Arcane Blast.</gray>" # MiniMessage string
    action:
      type: sequence # action type id
      actions:
        - type: particles_ring # action type id
          particle: END_ROD # Particle enum or minecraft:<key>
          radius: 1.2 # double >= 0.0
          points: 20 # int > 0
          count: 1 # int >= 1
        - type: sound # action type id
          sound: minecraft:block.amethyst_block.chime # namespaced key or SOUND enum
          volume: 0.8 # float >= 0.0 (typical 0.0-2.0)
          pitch: 1.6 # float > 0.0 (typical 0.5-2.0)

  scripted_storm:
    # Example of YAML ability that uses a DSL script instead of action.
    name: "<light_purple>Scripted Storm</light_purple>" # MiniMessage string
    description: "<gray>Uses the DSL for custom logic.</gray>" # MiniMessage string
    script:
      language: "dsl-v1" # fixed literal for DSL v1
      file: "scripted_storm.es" # filename under effects/scripts/
```

Comment after the YAML block: this single file demonstrates every major top-level
section (`schemaVersion`, `options`, `macros`, `abilities`). The `arcane_blast`
ability shows requirements, costs, cooldowns, triggers, and an action graph with
branching, targeting, raycast/projectile hits, particles, sounds, and minions. It
also calls another ability (`invoke_ability`). `scripted_storm` shows how to switch
to DSL instead of YAML actions.

### 15.1 Full item binding file (manual item + bindings)

Comment before the YAML block: this item file shows a readable matcher, multiple
bindings, passive ticks, permission, and mana bonuses.

```yaml
# FULL item file example (plugins/DungeonsReborn/effects/items/arcane_blade.yml)
schemaVersion: 1

# Item matcher for binding (you can also use a serialized ItemStack).
item:
  type: and
  matchers:
    - type: material
      material: DIAMOND_SWORD
    - type: lore_contains
      text: "Arcane"

# Optional mana bonuses while held/equipped.
mana:
  maxBonus: 20
  regenBonus: 1.5

# What abilities trigger on click or passive tick.
bindings:
  - type: interact
    click: RIGHT_CLICK
    ability: arcane_blast
    requireSneaking: false
    cancelEvent: true
  - type: interact
    click: LEFT_CLICK
    ability: arcane_blast
    requireSneaking: true
    cancelEvent: true
    permission: "dungeonsreborn.spells.arcane_blast.alt"
  - type: passive
    ability: arcane_echo
    periodTicks: 40
    slots:
      - HAND
```

Comment after the YAML block: this file shows the “item binding” path (separate from
`triggers` inside abilities). Use it when you want reusable items that cast abilities
on specific click types or passive intervals.
