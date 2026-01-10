# Spells/Effects Engine - Developer Guide

This document explains how the spell engine is wired in code, how YAML/DSL are compiled,
where the runtime lives, and how to extend the system safely.

## Scope

- Code architecture and execution flow.
- Where YAML/DSL enter the runtime.
- Action/condition/targeter catalogs (as implemented).
- Expression + variable system used by YAML/DSL.
- Item binding integration, mana, minions, and mobs integration points.

## Runtime Entry Points

- `dev.patric.dungeonsreborn.DungeonsRebornPlugin`
  - Constructs the `EffectsEngine`, YAML loader, bindings, and listeners.
  - Calls reloads on enable and provides command hooks (`/effects`, `/mobs`).

- `dev.patric.dungeonsreborn.effects.EffectsEngine`
  - Registers and stores `AbilitySpec` instances.
  - Handles `cast(...)` and schedules actions via `runLater`/`runRepeating`.
  - Owns shared services: particle emitter, mana provider, debug flag.

- `dev.patric.dungeonsreborn.effects.config.EffectsYamlAbilities`
  - Loads YAML abilities, macros, item bindings, and scripts.
  - Compiles YAML into `AbilitySpec` + executable `Action` graphs.
  - Validates and reports reload errors with full paths.

## Core Data Types

- `AbilitySpec`
  - Name, description, requirements, costs, cooldown, action graph, triggers.
- `CastContext`
  - Holds caster, origin, RNG, engine, variables, and execution state.
- `Action` / `ActionWithHandle`
  - Executable node in the action graph, optionally cancellable.
- `ActionHandle`
  - Cancellation state for scheduled/looped actions.
- `CastState` + variables
  - Per-cast state and temp variables used by actions.

## YAML/DSL Compilation Pipeline

1. `EffectsYamlAbilities.reload()` loads:
   - `effects.yml` (template + macros + options)
   - `effects/abilities/*.yml` (per-ability files)
   - `effects/scripts/*.es` (DSL scripts, cached)
   - `effects/items/*.yml` (item bindings)
2. Each ability is compiled:
   - Requirements -> `AbilitySpec.Builder.require(...)`
   - Costs -> `AbilitySpec.Builder.cost(...)`
   - Cooldown -> `AbilitySpec.Builder.cooldownTicks(...)`
   - Action graph -> compiled `Action`
   - Triggers -> `InteractBinding` registration
3. YAML overrides code if IDs collide.

## Action Types (YAML)

Flow and control:
- `include` (macro)
- `sequence`
- `delay`
- `repeat_ticks`
- `when`
- `random_choice_weighted`
- `invoke_ability`

Variables:
- `set_var`
- `inc_var`
- `with_var`
- `debug_var`

Player feedback:
- `message`
- `action_bar`
- `title`
- `sound`

Animation helpers:
- `animate`
- `animate_realtime`

Particles (all accept `particle`, `count`, `offset`, `extra`):
- `particles_point`
- `particles_ring`
- `particles_line`
- `particles_arc`
- `particles_disk`
- `particles_sphere_shell`
- `particles_sphere_filled` (aliases: `particles_sphere`, `particles_sphere_fill`)
- `particles_helix`
- `particles_bezier`
- `particles_spline`
- `particles_cone`
- `particles_cylinder`
- `particles_box`
- `particles_polygon`

Presets:
- `preset_shockwave`
- `preset_orbit`
- `preset_swirl`
- `preset_beam_chargeup`

Targeting and projectiles:
- `raycast_hit_entity`
- `projectile`
- `for_each_target`

Entity effects:
- `potion`
- `knockback`
- `pull`
- `heal`

Damage actions:
- `damage`
- `damage_percent`
- `damage_true`
- `damage_typed`
- `damage_over_time` (alias: `damage_dot`)
- `damage_crit`
- `damage_falloff`
- `damage_chain` (aliases: `chain_damage`, `chain_lightning`)
- `damage_lifesteal`

Resist/reflect:
- `set_resistance`
- `clear_resistance`
- `add_resistance`
- `set_reflect`
- `clear_reflect`

Minions:
- `minion_summon` (aliases: `summon_minion`, `minions`)

## Targeters

Implemented in `compileTargeter(...)`:
- `self`
- `players`
- `mobs`
- `non_players`
- `nearest`
- `any`
- `sphere`
- `box`
- `cylinder`
- `cone`
- `capsule_ray` (alias: `capsule-ray`)
- `look_ray` (alias: `look-ray`)
- `projectile_hit` (alias: `projectile-hit`)

## Conditions

Implemented in `compileCondition(...)`:
- `always`
- `and` / `or` / `not`
- `chance`
- `permission`
- `sneaking`
- `has_item_tag`
- `has_target`
- `var_present`
- `var_equals`
- `var_gt` / `var_gte`
- `var_lt` / `var_lte`

## Requirements + Costs

Requirements (ability-level):
- `permission` (string)
- `sneaking` (boolean)
- `has_item_tag` (NamespacedKey)

Costs (ability-level):
- `mana` (`amount`)
- `consume_item` / `consume_main_hand` (`amount`)
- `durability` / `durability_main_hand` (`damage`, `allowBreak`)

## Expressions + Variables

Numeric fields can be:
- A number literal (`1.5`)
- A string expression: `expr: 1 + lerp(1, 4, t)`

Supported operators: `+ - * / % ^`.

Functions:
- `min`, `max`, `clamp`, `lerp`, `rand`, `abs`, `floor`, `ceil`, `round`.

Built-in variables resolved in expressions:
- `mana`, `mana_max`
- `caster_health`, `caster_max_health`
- `t` (animation time 0..1)
- `distance` (to last entity)
- `var:<key>` or `var:<scope>:<key>` (scope: cast, player, entity)

Variable scopes:
- `cast` (per cast)
- `player` (per player)
- `entity` (current target or caster)

## Particle Data Customization

Parsed in `EffectsYamlAbilities` and resolved at emit time by `Actions`:
- Item data: `ITEM` (ItemStack)
- Block data: `BLOCK`, `FALLING_DUST`, `DUST_PILLAR`, `BLOCK_CRUMBLE`, `BLOCK_MARKER`
- `VIBRATION` and `TRAIL`

`EFFECT` and `INSTANT_EFFECT` data are not supported in Paper 1.21.8.

## Item Binding System

- YAML loader compiles `effects/items/<itemId>.yml` into bindings.
- Bindings register with `EffectsBindings` and `InteractTrigger`.
- Passive bindings are handled by a periodic scheduler and can filter slots.
- Item matching uses `ItemMatcher` (`material`, `custom_model_data`, `pdc_tag`, `lore_contains`, `and/or`).

## Mana System

- `SessionManaProvider` stores mana per player (resets on logout).
- Regen is scheduled on a fixed period (not per tick).
- Mana costs display an action bar on cost failure.

## Minions

- Minion action compiles a `MinionSpec` and uses `MinionManager`.
- Minions are normal custom mobs with PDC tags (`MobMarkers.MINION_ID`).
- Supports minion passives and special attacks in the spec.

## Mob Integration

- Mobs can use abilities as attacks/passives.
- YAML lives in `plugins/DungeonsReborn/mobs.yml`.
- `MobRegistry` + `MobYamlRegistry` manage specs and spawn eggs.

## Debugging and Metrics

- `/effects debug on` enables verbose logs.
- YAML reload reports full path errors for invalid entries.
- Script metrics are tracked per script id (execution count, time, errors).

## Extending the Engine

- Add new action types in `Actions` and wire them in `compileAction(...)`.
- Add new matcher types in `ItemMatchers` + `compileItemMatcher(...)`.
- Add new targeters or conditions in their compile methods.
- Keep YAML schema stable; reject invalid values early with clear paths.
