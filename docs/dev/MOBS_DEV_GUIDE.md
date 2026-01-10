# Mob System - Developer Guide

This document explains how the custom mob system is implemented in code,
where YAML is loaded, and how mobs interact with the effects engine.

--------------------------------------------------------------------------------
## 1. High-Level Architecture

Main flow:

1) `MobYamlRegistry` loads `plugins/DungeonsReborn/mobs.yml`.
2) YAML mobs are compiled into `MobSpec` objects.
3) `MobRegistry` registers specs and handles runtime mob behavior.
4) `MobSpawnManager` manages timed spawns from `spawns:`.
5) `MobEggListener` handles custom spawn eggs from `eggs:`.

The mob system is tightly integrated with the effects engine:
- Attacks and passives cast ability IDs.
- YAML/DSL scripts can be embedded and compiled into abilities.

--------------------------------------------------------------------------------
## 2. Core Classes

### 2.1 `MobYamlRegistry`

Responsibilities:
- Loads and validates `mobs.yml`.
- Parses mobs, phases, attacks, passives, loot, mana drops, spawns, and eggs.
- Compiles `script:` blocks using `EffectsYamlAbilities.compileScriptAction(...)`.
- Registers script abilities with `EffectsEngine` (IDs like `mob:<mobId>:...`).

Key methods:
- `reload()` -> loads YAML and registers specs.
- `parseMobSpec(...)`, `parseAttack(...)`, `parsePhase(...)`.
- `parseSpawns(...)` and `parseEggs(...)`.

### 2.2 `MobRegistry`

Responsibilities:
- Holds `MobSpec` registry.
- Spawns mobs by ID.
- Applies attributes, equipment, variants, and resistances.
- Manages runtime state (`MobState`) for AI, phases, cooldowns.
- Performs periodic tick updates (attack triggers, passives, bossbars).
- Hooks death/removal events to drop loot and mana.

Integrations:
- Uses `EffectsEngine.castWithContext(...)` to trigger abilities.
- Writes `Vars.MOB_ID`, `Vars.MOB_OWNER`, `Vars.MOB_TARGET`, `Vars.MOB_ATTACK` into cast state.

### 2.3 `MobSpawnManager`

Responsibilities:
- Loads spawn points from YAML.
- Keeps per-spawn state: alive UUIDs and respawn timers.
- Periodically spawns mobs on a fixed tick interval (20 ticks).
- Can despawn all on reload if configured.

### 2.4 `MobEggListener` + `MobEggSpec`

Responsibilities:
- Reads egg specs from YAML and creates marker-tagged items.
- Detects egg use and spawns the linked mob.
- Enforces egg cooldowns and permissions.

### 2.5 Data Markers

PersistentDataContainer keys:
- `MobMarkers.MOB_ID` -> mob id
- `MobMarkers.MOB_OWNER` -> owner UUID
- `MobMarkers.MOB_VARIANT` -> variant id
- `MobMarkers.MINION_ID` -> minion id

Egg item markers:
- `MobItemMarkers.EGG_ID`
- `MobItemMarkers.EGG_MOB_ID`

--------------------------------------------------------------------------------
## 3. Mob Spec Model

`MobSpec` contains:
- Display: name, showName, bossbar.
- FX: spawn/death particles and sounds.
- Equipment: main/offhand, armor slots.
- Stats: attributes (health, damage, speed, armor, etc.).
- AI spec: targeting, leash, idle, flee, kite settings.
- Attacks: main + secondary (`MobAttackSpec`).
- Passives: periodic abilities (`MobPassiveSpec`).
- Phases: health-based overrides (`MobPhaseSpec`).
- Variants: weighted alternate names and stat multipliers.
- Resistances and immunities.
- Loot and mana drops.
- Summon spec (owner-aware despawn/teleport settings).

--------------------------------------------------------------------------------
## 4. Attacks and Effects

- `MobAttackSpec` references an ability ID.
- Attack triggers:
  - `MELEE`
  - `RANGED`
- Target modes:
  - `NEAREST_PLAYER`
  - `NEAREST_HOSTILE`
  - `LAST_ATTACKER`

Attacks call:
`EffectsEngine.castWithContext(abilityId, caster, origin, direction, item, ctx -> {...})`

The mob system sets these vars for ability actions:
- `Vars.MOB_ID`
- `Vars.MOB_OWNER`
- `Vars.MOB_TARGET`
- `Vars.MOB_ATTACK`

--------------------------------------------------------------------------------
## 5. Phases and Variants

### 5.1 Phases

- Each phase has `healthBelow` (0..1).
- Phases can override `attacks` and `passives`.
- Phase switching is evaluated during the tick loop.

### 5.2 Variants

- Weighted random selection at spawn.
- Can modify name and apply stat multipliers.
- `variantId` is stored in PDC for visibility/debugging.

--------------------------------------------------------------------------------
## 6. Loot and Mana Drops

### 6.1 Loot

- Optional `loot` block with `clearVanilla` and `drops` list.
- Drops use `ItemStack` serialization or a simple `material`.

### 6.2 Mana Drops

- `manaDrops.killer` and `manaDrops.nearby` ranges are supported.
- `manaDrops.radius` defines nearby range.

--------------------------------------------------------------------------------
## 7. Boss Bars

- `MobBossBarSpec` defines title, color, overlay, audience.
- Audience values: `ALL_PLAYERS`, `OWNER_ONLY`.
- If using WITHER or ENDER_DRAGON, avoid double bossbars by disabling the custom one.

--------------------------------------------------------------------------------
## 8. AI Controller Hooks

`MobAiSpec` allows:
- Aggro target selection and leash behavior.
- Idle wandering and flee behavior.
- Kite behavior (min range + speed).

A custom `MobAiController` can be attached programmatically if needed.

--------------------------------------------------------------------------------
## 9. Extending the System

- Add new YAML fields in `MobYamlRegistry.parseMobSpec(...)`.
- Add new behavior in `MobRegistry.tick()`.
- Keep error messages path-based for YAML debugging.
- Ensure new features are reflected in both dev + usage docs.
