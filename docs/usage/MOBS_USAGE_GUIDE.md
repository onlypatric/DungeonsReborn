# Mob System - Usage Guide

This guide explains how to configure custom mobs in `mobs.yml` without writing code.
It includes full examples and explains every important section.

--------------------------------------------------------------------------------
## Chapter 1 - Files and Commands

### 1.1 File location

- `plugins/DungeonsReborn/mobs.yml`

### 1.2 Reloading

```
/dr mobs reload
```

### 1.3 Useful commands

- `/dr mobs editor` (open editor GUI)
- `/dr mobs list` (list active mobs)
- `/dr mobs spawn <id>` (spawn a mob at your location)
- `/dr mobs egg <id>` (give yourself a mob egg)

Permissions:
- `dungeonsreborn.mobs.reload`
- `dungeonsreborn.mobs.spawn`
- `dungeonsreborn.mobs.egg.give`
- `dungeonsreborn.mobs.editor`

--------------------------------------------------------------------------------
## Chapter 2 - Base YAML Structure

Minimal skeleton:

```yaml
schemaVersion: 1
options:
  despawnOnReload: true

mobs: {}
spawns: {}
eggs: {}
```

Notes:
- `despawnOnReload` removes active mobs when reloading.
- `spawns` is optional.
- `eggs` is optional.

--------------------------------------------------------------------------------
## Chapter 3 - A Minimal Mob

```yaml
mobs:
  forest_gremlin:
    type: ZOMBIE
    name: "<green>Forest Gremlin</green>"
    showName: true
    stats:
      health: 20
      damage: 3
      speed: 0.25
```

This creates a named zombie with basic stats.

--------------------------------------------------------------------------------
## Chapter 4 - Full Mob Example

```yaml
mobs:
  thunder_knight:
    type: ZOMBIE
    name: "<yellow>Thunder Knight</yellow>"
    showName: true

    bossbar:
      enabled: true
      title: "<yellow>Thunder Knight</yellow>"
      color: YELLOW
      overlay: PROGRESS
      audience: ALL_PLAYERS

    stats:
      health: 80
      damage: 9
      speed: 0.25
      armor: 6
      range: 20

    equipment:
      mainHand: DIAMOND_SWORD
      head: DIAMOND_HELMET
      chest: DIAMOND_CHESTPLATE
      legs: DIAMOND_LEGGINGS
      feet: DIAMOND_BOOTS

    spawnFx:
      particles:
        particle: ELECTRIC_SPARK
        count: 12
        offset: 0.2
      sound:
        sound: minecraft:entity.lightning_bolt.thunder
        volume: 0.9
        pitch: 1.0

    deathFx:
      particles:
        particle: EXPLOSION
        count: 1
        offset: 0.1
      sound:
        sound: minecraft:entity.generic.explode
        volume: 1.0
        pitch: 0.9

    resistances:
      LIGHTNING: 0.4
    immunities:
      - VOID

    ai:
      enabled: true
      aggroRadius: 14
      aggroTargetMode: NEAREST_PLAYER
      leashRadius: 30
      leashTeleportRadius: 45
      idleWanderRadius: 8
      idleWanderIntervalTicks: 80
      fleeHealthRatio: 0.15
      fleeSpeed: 0.4
      kiteMinRange: 3
      kiteSpeed: 0.25

    attacks:
      main:
        ability: thunder_strike
        trigger: RANGED
        cooldownTicks: 60
        range: 18
        chance: 0.8
      secondary:
        ability: shield_bash
        trigger: MELEE
        cooldownTicks: 30
        range: 4
        chance: 1.0

    passives:
      - ability: lightning_aura
        periodTicks: 40

    phases:
      - id: enraged
        healthBelow: 0.5
        attacks:
          main:
            ability: thunder_strike
            trigger: RANGED
            cooldownTicks: 40
            range: 20
            chance: 1.0
        passives:
          - ability: lightning_aura
            periodTicks: 20

    loot:
      clearVanilla: true
      drops:
        - material: DIAMOND
          chance: 0.25
          min: 1
          max: 2
        - material: GOLD_INGOT
          chance: 1.0
          min: 2
          max: 5

    manaDrops:
      killer: { min: 8, max: 16 }
      nearby: { radius: 6, min: 2, max: 5 }
```

--------------------------------------------------------------------------------
## Chapter 5 - Names and Boss Bars

### 5.1 Names

- `name` uses MiniMessage formatting.
- Legacy `§` colors also work.

### 5.2 Boss bars

```yaml
bossbar:
  enabled: true
  title: "<red>Boss</red>"
  color: RED
  overlay: PROGRESS
  audience: ALL_PLAYERS
```

Audience values:
- `ALL_PLAYERS`
- `OWNER_ONLY`

Tip: If your mob is a WITHER or ENDER_DRAGON, consider disabling the custom bossbar
so you do not see two bossbars.

--------------------------------------------------------------------------------
## Chapter 6 - Stats

You can use these keys in `stats:`:
- `health`
- `damage`
- `speed`
- `range`
- `armor`
- `armor_toughness`
- `knockback_resistance`

Example:

```yaml
stats:
  health: 60
  damage: 7
  speed: 0.26
  range: 18
  armor: 4
```

--------------------------------------------------------------------------------
## Chapter 7 - Equipment

You can equip mobs using item names:

```yaml
equipment:
  mainHand: DIAMOND_SWORD
  offHand: SHIELD
  head: DIAMOND_HELMET
  chest: DIAMOND_CHESTPLATE
  legs: DIAMOND_LEGGINGS
  feet: DIAMOND_BOOTS
```

Advanced: you can also use full ItemStack serialization.

--------------------------------------------------------------------------------
## Chapter 8 - Spawn and Death FX

Particles:

```yaml
spawnFx:
  particles:
    particle: FLAME
    count: 12
    offset: 0.2
```

Sounds:

```yaml
deathFx:
  sound:
    sound: minecraft:entity.generic.explode
    volume: 1.0
    pitch: 0.9
```

Particle options:
- `count`, `offset` or `offsetX/Y/Z`, `extra`.

--------------------------------------------------------------------------------
## Chapter 9 - AI Settings

AI block:

```yaml
ai:
  enabled: true
  overrideDefault: false
  aggroRadius: 12
  leashRadius: 24
  leashTeleportRadius: 36
  aggroTargetMode: NEAREST_PLAYER
  preferLastAttacker: true
  targetSwitchCooldownTicks: 40
  fleeHealthRatio: 0.0
  fleeSpeed: 0.35
  idleWanderRadius: 6
  idleWanderIntervalTicks: 80
  kiteMinRange: 4
  kiteSpeed: 0.25
```

Target modes:
- `NEAREST_PLAYER`
- `NEAREST_HOSTILE`
- `LAST_ATTACKER`

--------------------------------------------------------------------------------
## Chapter 10 - Attacks and Passives

### 10.1 Attacks

```yaml
attacks:
  main:
    ability: thunder_strike
    trigger: RANGED
    cooldownTicks: 60
    range: 18
    chance: 0.8
    requireLineOfSight: true
    requireTarget: true

  secondary:
    ability: shield_bash
    trigger: MELEE
    cooldownTicks: 30
    range: 4
    chance: 1.0
```

Triggers:
- `MELEE`
- `RANGED`

### 10.2 Passives

```yaml
passives:
  - ability: lightning_aura
    periodTicks: 40
```

### 10.3 Scripted attacks (optional)

You can embed a DSL script instead of an ability ID:

```yaml
attacks:
  main:
    script:
      language: "dsl-v1"
      source: |-
        on_cast {
          particles.ring particle=END_ROD radius=1.2 points=22 count=1
          damage amount=5
        }
    trigger: RANGED
    cooldownTicks: 40
    range: 12
```

DSL is documented in:
`docs/guides/SPELLS_EFFECTS_DSL_GUIDE.md`

--------------------------------------------------------------------------------
## Chapter 11 - Phases

Phases switch the mob’s attacks/passives at a health threshold.

```yaml
phases:
  - id: phase_1
    healthBelow: 0.7
    attacks:
      main:
        ability: phase1_attack
        trigger: RANGED
        cooldownTicks: 60
  - id: phase_2
    healthBelow: 0.4
    attacks:
      main:
        ability: phase2_attack
        trigger: RANGED
        cooldownTicks: 40
    passives:
      - ability: phase2_aura
        periodTicks: 20
```

`healthBelow` must be between 0 and 1.

--------------------------------------------------------------------------------
## Chapter 12 - Variants

Variants add randomness with weights and multipliers.

```yaml
variants:
  - id: "elite"
    weight: 0.2
    namePrefix: "<gold>Elite </gold>"
    healthMultiplier: 1.6
    damageMultiplier: 1.4
  - id: "weak"
    weight: 1.0
    nameSuffix: " <gray>(Weak)</gray>"
    healthMultiplier: 0.7
    damageMultiplier: 0.7
```

--------------------------------------------------------------------------------
## Chapter 13 - Resistances and Immunities

```yaml
resistances:
  FIRE: 0.5
  ARCANE: 0.7

immunities:
  - VOID
```

- Multiplier < 1 reduces damage.
- Multiplier > 1 increases damage.
- Immunity sets multiplier to 0.

--------------------------------------------------------------------------------
## Chapter 14 - Loot Drops

```yaml
loot:
  clearVanilla: true
  drops:
    - material: DIAMOND
      chance: 0.25
      min: 1
      max: 2
    - material: GOLD_INGOT
      chance: 1.0
      min: 2
      max: 5
```

You can also use full ItemStack serialization.

--------------------------------------------------------------------------------
## Chapter 15 - Mana Drops

```yaml
manaDrops:
  killer: { min: 8, max: 16 }
  nearby: { radius: 6, min: 2, max: 5 }
```

- `killer` gives mana to the player who killed the mob.
- `nearby` gives mana to nearby players.

--------------------------------------------------------------------------------
## Chapter 16 - Summoned Mob Settings

For mobs spawned as minions or owned summons:

```yaml
summon:
  enabled: true
  despawnWhenOwnerOffline: true
  despawnDistance: 0
  teleportDistance: 0
```

--------------------------------------------------------------------------------
## Chapter 17 - Spawn Eggs

```yaml
eggs:
  thunder_knight:
    mob: thunder_knight
    material: NETHER_STAR
    amount: 1
    permission: "dungeonsreborn.mobs.egg.thunder_knight"
    cooldownTicks: 40
```

Use `/dr mobs egg thunder_knight` to give yourself the egg.

--------------------------------------------------------------------------------
## Chapter 18 - World Spawns

```yaml
spawns:
  thunder_knight_spawn:
    mob: thunder_knight
    world: world
    x: 100
    y: 65
    z: -20
    yaw: 0
    pitch: 0
    count: 2
    maxAlive: 4
    respawnTicks: 200
    radius: 8
    enabled: true
```

--------------------------------------------------------------------------------
## Chapter 19 - Troubleshooting

- Reload errors appear in console with a full path (e.g., `mobs.my_mob.stats.health`).
- If an ability is missing, ensure it is registered in the effects system.
- If a mob is not spawning, check the world name and `enabledWorlds`/`disabledWorlds`.

--------------------------------------------------------------------------------
## Chapter 20 - Full YAML Showcase (Everything in One Place)

Comment before the YAML block: this full `mobs.yml` example includes every major
block (options, mobs, bossbar, stats, AI, attacks, phases, loot, mana drops, eggs,
and spawns) so you can see the complete structure in one file.

```yaml
# FULL mobs.yml example (kitchen sink). Copy what you need, delete the rest.
schemaVersion: 1 # integer >= 1

# Global reload options for mobs/spawns.
options:
  despawnOnReload: true # boolean
  enabledWorlds:
    - world # world name string
    - world_nether # world name string

# All custom mobs live here.
mobs:
  abyssal_tyrant:
    # Base entity type.
    type: WITHER # Bukkit EntityType enum (must be alive)
    name: "<dark_purple><bold>Abyssal Tyrant</bold></dark_purple>" # MiniMessage string
    showName: true # boolean

    # Bossbar configuration.
    bossbar:
      enabled: true # boolean
      title: "<dark_purple>Abyssal Tyrant</dark_purple>" # MiniMessage string
      color: PURPLE # BossBar color enum (PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE)
      overlay: PROGRESS # PROGRESS|NOTCHED_6|NOTCHED_10|NOTCHED_12|NOTCHED_20
      audience: ALL_PLAYERS # ALL_PLAYERS|OWNER_ONLY

    # Spawn and death visual/audio effects.
    spawnFx:
      particles:
        particle: SOUL # Particle enum or minecraft:<key>
        count: 30 # int >= 1
        offsetX: 0.4 # double >= 0.0
        offsetY: 0.8 # double >= 0.0
        offsetZ: 0.4 # double >= 0.0
        extra: 0.0 # double >= 0.0
      sound:
        sound: minecraft:entity.wither.spawn # namespaced key or SOUND enum
        volume: 1.0 # float >= 0.0 (typical 0.0-2.0)
        pitch: 1.0 # float > 0.0 (typical 0.5-2.0)
    deathFx:
      particles:
        particle: SMOKE # Particle enum or minecraft:<key>
        count: 40 # int >= 1
        offsetX: 0.6 # double >= 0.0
        offsetY: 0.6 # double >= 0.0
        offsetZ: 0.6 # double >= 0.0
        extra: 0.0 # double >= 0.0
      sound:
        sound: minecraft:entity.wither.death # namespaced key or SOUND enum
        volume: 1.0 # float >= 0.0 (typical 0.0-2.0)
        pitch: 0.9 # float > 0.0 (typical 0.5-2.0)

    # Equipment (string = material).
    equipment:
      mainHand: NETHERITE_SWORD # Bukkit Material enum or serialized item
      head: WITHER_SKELETON_SKULL # Bukkit Material enum or serialized item
      chest: NETHERITE_CHESTPLATE # Bukkit Material enum or serialized item
      legs: NETHERITE_LEGGINGS # Bukkit Material enum or serialized item
      feet: NETHERITE_BOOTS # Bukkit Material enum or serialized item

    # Attributes (stats).
    stats:
      health: 300 # double > 0.0
      damage: 12 # double >= 0.0
      speed: 0.28 # double >= 0.0 (typical 0.05-0.4)
      followRange: 32 # double >= 0.0
      armor: 10 # double >= 0.0
      armorToughness: 4 # double >= 0.0
      knockbackResistance: 0.6 # double >= 0.0 (0.0-1.0 typical)

    # Variants (optional).
    variants:
      - id: "ascended" # string id (normalized to [a-z0-9_.:-])
        weight: 0.2 # double > 0.0 (relative weight)
        namePrefix: "<gold>Ascended</gold> " # MiniMessage string
        healthMultiplier: 1.4 # double > 0.0
        damageMultiplier: 1.3 # double > 0.0
        speedMultiplier: 1.1 # double > 0.0

    # Resistances and immunities.
    resistances:
      FIRE: 0.5 # double >= 0.0 (1.0 = normal, 0.0 = immune)
      VOID: 0.2 # double >= 0.0
    immunities:
      - POISON # DamageType enum

    # Loot drops (optional).
    loot:
      clearVanilla: true # boolean
      drops:
        - material: NETHER_STAR # Bukkit Material enum or serialized item
          chance: 0.25 # double in [0.0, 1.0]
          min: 1 # int >= 1
          max: 1 # int >= min
        - material: COAL # Bukkit Material enum or serialized item
          chance: 1.0 # double in [0.0, 1.0]
          min: 4 # int >= 1
          max: 8 # int >= min

    # Summoned mob behavior (when used as a minion).
    summon:
      enabled: true # boolean
      despawnWhenOwnerOffline: true # boolean
      despawnDistance: 40 # double >= 0.0 (0 = disable)
      teleportDistance: 24 # double >= 0.0 (0 = disable)

    # AI settings.
    ai:
      enabled: true # boolean
      overrideDefault: false # boolean
      aggroRadius: 18 # double >= 0.0
      leashRadius: 28 # double >= 0.0
      leashTeleportRadius: 40 # double >= 0.0
      preferLastAttacker: true # boolean
      targetSwitchCooldownTicks: 40 # long >= 0
      fleeHealthRatio: 0.2 # double in [0.0, 1.0]
      fleeSpeed: 0.35 # double >= 0.0
      idleWanderRadius: 6 # double >= 0.0
      idleWanderIntervalTicks: 80 # long >= 0
      kiteMinRange: 5 # double >= 0.0
      kiteSpeed: 0.25 # double >= 0.0
      aggroTargetMode: NEAREST_PLAYER # LAST_ATTACKER|NEAREST_PLAYER|NEAREST_HOSTILE

    # Base attacks (abilities must exist in effects system).
    attacks:
      main:
        ability: abyssal_bolt # ability id string
        trigger: MELEE # MELEE|RANGED
        targetMode: NEAREST_PLAYER # LAST_ATTACKER|NEAREST_PLAYER|NEAREST_HOSTILE
        range: 10 # double >= 0.0
        cooldownTicks: 40 # long > 0
        chance: 1.0 # double in [0.0, 1.0]
        requireLineOfSight: true # boolean
        requireTarget: true # boolean
      secondary:
        ability: abyssal_nova # ability id string
        trigger: RANGED # MELEE|RANGED
        targetMode: NEAREST_PLAYER # LAST_ATTACKER|NEAREST_PLAYER|NEAREST_HOSTILE
        range: 18 # double >= 0.0
        cooldownTicks: 80 # long > 0
        chance: 0.4 # double in [0.0, 1.0]
        requireLineOfSight: false # boolean
        requireTarget: true # boolean

    # Passive effects (periodic abilities).
    passives:
      - ability: abyssal_aura # ability id string
        periodTicks: 40 # long > 0
      - ability: abyssal_regen # ability id string
        periodTicks: 80 # long > 0

    # Phase system (overrides attacks/passives at health thresholds).
    phases:
      - id: "phase_2" # string id (normalized to [a-z0-9_.:-])
        healthBelow: 0.6 # double in (0.0, 1.0]
        attacks:
          main:
            ability: abyssal_bolt_phase2 # ability id string
            trigger: MELEE # MELEE|RANGED
            targetMode: NEAREST_PLAYER # LAST_ATTACKER|NEAREST_PLAYER|NEAREST_HOSTILE
            range: 12 # double >= 0.0
            cooldownTicks: 30 # long > 0
        passives:
          - ability: abyssal_aura_phase2 # ability id string
            periodTicks: 30 # long > 0
      - id: "phase_3" # string id (normalized to [a-z0-9_.:-])
        healthBelow: 0.3 # double in (0.0, 1.0]
        attacks:
          main:
            ability: abyssal_bolt_phase3 # ability id string
            trigger: MELEE # MELEE|RANGED
            targetMode: NEAREST_PLAYER # LAST_ATTACKER|NEAREST_PLAYER|NEAREST_HOSTILE
            range: 14 # double >= 0.0
            cooldownTicks: 20 # long > 0
          secondary:
            ability: abyssal_nova_phase3 # ability id string
            trigger: RANGED # MELEE|RANGED
            targetMode: NEAREST_PLAYER # LAST_ATTACKER|NEAREST_PLAYER|NEAREST_HOSTILE
            range: 20 # double >= 0.0
            cooldownTicks: 60 # long > 0
            chance: 0.8 # double in [0.0, 1.0]
        passives:
          - ability: abyssal_aura_phase3 # ability id string
            periodTicks: 20 # long > 0

    # Mana drop rules (optional).
    manaDrops:
      killer:
        min: 12 # double >= 0.0
        max: 20 # double >= min
      nearby:
        radius: 8 # double >= 0.0
        min: 3 # double >= 0.0
        max: 6 # double >= min

# Spawn eggs for admins/testing.
eggs:
  abyssal_tyrant:
    mob: abyssal_tyrant # mob id string
    material: NETHER_STAR # Bukkit Material enum or item stack
    amount: 1 # int > 0
    permission: "dungeonsreborn.mobs.egg.abyssal_tyrant" # permission node string
    cooldownTicks: 40 # long >= 0

# Fixed world spawns (optional).
spawns:
  abyssal_tyrant_spawn:
    mob: abyssal_tyrant # mob id string
    world: world # world name string
    x: 100 # double (world coordinate)
    y: 70 # double (world coordinate)
    z: -30 # double (world coordinate)
    yaw: 0 # float (degrees)
    pitch: 0 # float (degrees)
    count: 1 # int > 0
    maxAlive: 1 # int >= count
    respawnTicks: 600 # long >= 0
    radius: 8 # double >= 0.0
    enabled: true # boolean
```

Comment after the YAML block: this file demonstrates a fully featured boss mob with
bossbar, spawn/death effects, equipment, stats, AI, phased attacks, passives, loot,
and mana drops. It also includes a spawn egg and a world spawn entry so you can test
immediately after `/dr mobs reload`.
