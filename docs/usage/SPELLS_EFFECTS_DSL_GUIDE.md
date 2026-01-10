# Spells/Effects DSL Guide

This guide explains how to write the custom DSL used by the spell engine. It is
self-contained and intended for creators who prefer scripting over YAML action graphs.

--------------------------------------------------------------------------------
## Chapter 1 - Quick Start

### 1.1 Minimal script

```text
on_cast {
  particles.ring particle=END_ROD radius=1.4 points=26 count=1
}
```

### 1.2 Hooked script (full)

```text
# dsl:1

macro burst(radius=2.4) {
  particles.ring particle=CRIT radius=radius points=32 count=1
  sound name=minecraft:block.note_block.pling volume=1 pitch=1.2
}

on_cast {
  call burst radius=1.6
  if mana >= 10 {
    damage amount=6
  } else {
    message "<red>Not enough mana.</red>"
  }
}

on_cancel {
  message "<gray>Cast cancelled.</gray>"
}

on_finish {
  action_bar "<green>Done.</green>"
}
```

--------------------------------------------------------------------------------
## Chapter 2 - Script Structure

A script is a list of top-level blocks:

- `on_cast { ... }` (required)
- `on_cancel { ... }` (optional)
- `on_end { ... }` or `finally { ... }` (optional)
- `on_hit { ... }` (optional; used by raycast/projectile)
- `on_finish { ... }` (optional; runs after all delayed work completes)
- `on_cost_fail { ... }` (optional)
- `on_cooldown_fail { ... }` (optional)

Notes:
- `on_cast` is required; without it, the script is invalid.
- `on_end` runs after `on_cast` finishes (even if it scheduled delayed tasks).
- `on_finish` runs once all scheduled tasks from `delay`, `repeat`, or `on_tick` complete.

--------------------------------------------------------------------------------
## Chapter 3 - Syntax Basics

### 3.1 Comments

Lines starting with `#` are comments.

### 3.2 Blocks

Blocks use `{ ... }` and statements are separated by whitespace/newlines.

### 3.3 Strings

Use double quotes:

```text
message "<gold>Hello" 
```

Escapes supported: `\n`, `\t`, `\"`, `\\`.

### 3.4 Attributes

Statements can accept attributes:

```text
sound name=minecraft:block.note_block.pling volume=1 pitch=1.2
```

Attributes are written as `key=value` pairs.

### 3.5 Numbers and Expressions

Numbers can be literal or expressions:

```text
radius=lerp(1, 3, t)
```

--------------------------------------------------------------------------------
## Chapter 4 - Variables and Expressions

### 4.1 Variable scopes

Variables can live in these scopes:
- `cast` (per cast)
- `player` (per player)
- `entity` (current target or caster)

### 4.2 Setting variables

```text
set foo = 5
set player:mana_bonus = 3
set var("entity:mark") = 1
```

### 4.3 Increment variables

```text
inc_var foo amount=1 default=0
inc_var player:combo amount=2
```

### 4.4 Temporary variables

```text
with_var cast:temp = 2 {
  damage amount=10
}
```

### 4.5 Expression functions

Supported functions:
- `min`, `max`, `clamp`, `lerp`, `rand`, `abs`, `floor`, `ceil`, `round`

Operators: `+ - * / % ^`

### 4.6 Built-in variables

You can use these names in expressions:
- `mana`, `mana_max`
- `caster_health`, `caster_max_health`
- `t` (0..1 during animate/on_tick)
- `distance` (to last entity)
- `var:<key>` or `var:<scope>:<key>`

--------------------------------------------------------------------------------
## Chapter 5 - Flow Control

### 5.1 If / When

```text
if mana >= 10 {
  damage amount=6
} else {
  message "<red>No mana.</red>"
}
```

### 5.2 Chance block

```text
chance p=0.3 {
  sound name=minecraft:entity.experience_orb.pickup
} else {
  message "<gray>No luck.</gray>"
}
```

### 5.3 Weighted choice

```text
choice weighted {
  0.2: message "<gold>Rare!"
  1.0: message "<gray>Common."
}
```

Tip: use macros if you need multiple actions per choice.

### 5.4 Invoke other abilities

You can call another ability from a script:

```text
invoke ability=\"some_other_ability\" mode=subgraph maxDepth=8
```

Modes:
- `subgraph` (default): executes the other ability’s action graph in-place.
- `cast`: performs a full cast (costs/cooldown apply).

`maxDepth` prevents recursive loops.

--------------------------------------------------------------------------------
## Chapter 6 - Timing and Scheduling

### 6.1 Delay

```text
delay ticks=20 {
  sound name=minecraft:entity.experience_orb.pickup
}
```

### 6.2 Repeat

```text
repeat times=5 every=10 delay=0 {
  particles.point particle=CRIT count=1
}
```

Limits:
- `times` is capped at 10,000.

### 6.3 On-tick

```text
on_tick ticks=80 every=1 easing=IN_OUT_CUBIC {
  particles.ring particle=END_ROD radius=lerp(0.6, 2.8, t) points=24 count=1
}
```

Limits:
- `ticks` is capped at 5 minutes (6000 ticks).

--------------------------------------------------------------------------------
## Chapter 7 - Messages, Sounds, Titles

```text
message "<gold>You feel stronger."

action_bar "<green>Mana: {mana}/{mana_max}"

sound name=minecraft:block.note_block.pling volume=1 pitch=1.2

title "<red>Power" subtitle="<gray>Unleashed" fadeIn=10 stay=40 fadeOut=10

debug_log "<gray>debug: hit target {target}</gray>"
```

Text uses MiniMessage; if parsing fails, legacy `§` colors are used.

--------------------------------------------------------------------------------
## Chapter 8 - Particles

Particles are written as `particles.<shape>`.

Common shapes:
- `particles.point`
- `particles.ring`
- `particles.line`
- `particles.arc`
- `particles.disk`
- `particles.sphere_shell`
- `particles.sphere_filled`
- `particles.helix`
- `particles.bezier`
- `particles.spline`
- `particles.cone`
- `particles.cylinder`
- `particles.box`
- `particles.polygon`
- `particles.orbit`
- `particles.swirl`
- `particles.shockwave`
- `particles.beam_chargeup`

Alias:
- `preset_beam_chargeup` (same as `particles.beam_chargeup`)

Example (ring):

```text
particles.ring particle=END_ROD radius=1.4 points=24 count=1
```

Example (orbit preset):

```text
particles.orbit particle=END_ROD radius=2.4 durationTicks=60 periodTicks=1 copies=3 count=1
```

Common attributes:
- `particle`, `count`, `offset`, `extra`
- `at=origin|last_hit|last_entity`

--------------------------------------------------------------------------------
## Chapter 9 - Targeting and Projectiles

### 9.1 Raycast

```text
raycast_hit_entity maxDistance=12 raySize=0.35 stopOnBlock=true ignoreCaster=true {
  damage amount=6
} else {
  message "<gray>Missed."
}
```

### 9.2 Projectile

```text
projectile speedPerTick=1.2 maxDistance=24 hitRadius=0.25 blockCollision=STOP {
  on_hit {
    damage amount=8
    particles.point particle=CRIT count=1
  }
}
```

Optional projectile attributes:
- `ignoreCaster=true|false`
- `blockCollision=STOP|BOUNCE|PASS_THROUGH`
- `bounceMax` / `bounceRestitution` (when using BOUNCE)
- `trailParticle`, `trailCount`, `trailOffset`, `trailExtra`

### 9.3 For-each target

```text
for_each_target type=sphere radius=5 mode=each maxTargets=0 sort=nearest originAt=origin {
  damage amount=4
}
```

Targeter types:
- `self`, `nearest`, `sphere`, `box`, `cylinder`, `cone`
- `look_ray`, `capsule_ray`, `projectile_hit`

Filters:
- `filter=any|players|mobs`

--------------------------------------------------------------------------------
## Chapter 10 - Damage and Effects

```text
damage amount=6

damage_typed amount=10 type=FIRE

damage_percent percent=0.15

damage_true amount=8

damage_falloff amount=10 maxDistance=12 minMultiplier=0.2

damage_crit amount=7 critChance=0.2 critMultiplier=1.5 headshotMultiplier=1.0 headshotThreshold=0.25

damage_lifesteal amount=6 ratio=0.25

damage_over_time amount=2 periodTicks=20 times=5

damage_chain amount=4 radius=6 maxJumps=4 delayTicks=2 falloff=0.8

heal amount=6

potion effect=SLOW durationTicks=60 amplifier=0

knockback horizontal=1 vertical=0.35

pull horizontal=0.75 vertical=0.08
```

Policies:
- `policy=hostile_default|pve_only|pvp_only|any`

--------------------------------------------------------------------------------
## Chapter 11 - Resistances and Reflect

```text
set_resistance type=FIRE multiplier=0.5 durationTicks=200
add_resistance type=ARCANE delta=-0.1 durationTicks=100
clear_resistance type=FIRE

set_reflect ratio=0.25 flat=0 durationTicks=200 policy=hostile_default
clear_reflect
```

Notes:
- Omit `type` in `clear_resistance` to clear all resistances.
- `set_reflect` supports `type`, `ignoreResistance`, and `policy`.

--------------------------------------------------------------------------------
## Chapter 12 - Minions (Optional)

```text
minion_summon mob="test_minion" count=2 durationTicks=200 radius=1.5
```

Optional attributes:
- `id`, `mode=AGGRESSIVE|DEFENSIVE|PASSIVE`, `despawnOnLogout`
- `passives`, `passivePeriodTicks`
- `specialAttacks`, `specialCooldownTicks`, `specialChance`
- `resistance_<TYPE>`, `immune_<TYPE>`
- Scaling:
  - `scale_healthPerLevel`, `scale_damagePerLevel`
  - `scale_healthPerMaxHealth`, `scale_damagePerMaxHealth`
  - `scale_healthPerManaMax`, `scale_damagePerManaMax`

Lists (like `passives` or `specialAttacks`) are comma-separated ability IDs.

--------------------------------------------------------------------------------
## Chapter 13 - Macros

Define macros at the top level:

```text
macro burst(radius=1.6, particle=END_ROD) {
  particles.ring particle=particle radius=radius points=24 count=1
  sound name=minecraft:block.note_block.pling volume=1 pitch=1.2
}
```

Call them in scripts:

```text
call burst radius=2.4 particle=CRIT
```

Macro arguments become temporary cast variables while the macro runs.

--------------------------------------------------------------------------------
## Chapter 14 - Limits and Budgets

To prevent abuse:
- Max operations per tick: 2000
- Max particles per cast: 20000
- Max repeat times: 10,000
- Max on_tick duration: 5 minutes

If a limit is exceeded, the script silently stops that statement and logs a debug message if debug is enabled.

--------------------------------------------------------------------------------
## Chapter 15 - Using the DSL from YAML

Inline script:

```yaml
script:
  language: "dsl-v1"
  source: |-
    on_cast {
      particles.ring particle=END_ROD radius=1.6 points=24 count=1
    }
```

File script:

```yaml
script:
  language: "dsl-v1"
  file: "my_spell.es"
```

Optional version header in scripts:

```text
# dsl:1
```

--------------------------------------------------------------------------------
## Chapter 16 - Troubleshooting

- Syntax errors show file:line:column in console.
- Unknown action? Check spelling and statement name.
- Missing attributes? The error message suggests the closest key.
- Particle overload? Reduce `count` or `points`.

--------------------------------------------------------------------------------
## Chapter 17 - Full YAML Showcase (DSL Integration)

Comment before the YAML block: this is a complete `effects.yml` snippet that shows
every supported DSL integration path (inline source and file reference), along with
typical ability metadata and triggers.

```yaml
# FULL effects.yml example focused on DSL integration.
schemaVersion: 1 # integer >= 1

# Abilities that use scripts instead of action graphs.
abilities:
  dsl_inline_example:
    name: "<aqua>Inline Script Example</aqua>" # MiniMessage string
    description: "<gray>Uses inline DSL source.</gray>" # MiniMessage string
    costs:
      - type: mana # cost id
        amount: 6 # number > 0
    cooldown:
      ticks: 30 # long > 0
    triggers:
      - type: interact # trigger id
        click: RIGHT_CLICK # LEFT_CLICK | RIGHT_CLICK
        cancelEvent: true # boolean
        item:
          type: material # matcher id
          material: STICK # Bukkit Material enum
    script:
      language: "dsl-v1" # fixed literal for DSL v1
      source: |- # inline DSL script (multiline string)
        on_cast {
          particles.ring particle=END_ROD radius=1.4 points=24 count=1
          sound sound=minecraft:entity.experience_orb.pickup volume=0.7 pitch=1.6
        }

  dsl_file_example:
    name: "<light_purple>File Script Example</light_purple>" # MiniMessage string
    description: "<gray>Uses a script file from effects/scripts.</gray>" # MiniMessage string
    requirements:
      - type: permission # requirement id
        permission: "dungeonsreborn.spells.dsl_file_example" # permission node string
        message: "<red>No permission.</red>" # MiniMessage string
    script:
      language: "dsl-v1" # fixed literal for DSL v1
      file: "dsl_file_example.es" # filename under effects/scripts/
```

Comment after the YAML block: this shows the two ways to attach DSL to an ability.
Use `source` for small scripts and `file` for larger, reusable scripts stored in
`plugins/DungeonsReborn/effects/scripts/`.
