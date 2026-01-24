# AGENTS.md

This project is a Paper (Minecraft) plugin named **DungeonsReborn**. It provides:
- A YAML/DSL-driven spell/effects engine (actions, conditions, targeters, costs, cooldowns, vars).
- An in-game GUI editor for effects and items (node-based editor, not a wizard).
- An item binding system (items in YAML bind to effects; interactions trigger casts).
- A mob library (custom mob specs, AI hooks, spawn eggs).
- A minion system built on top of the mob library.
- A mana system (costs, regen, drops).
- A custom item crafting library (recipe YAML + in-game recipe editor + discovery).

This file is the **only** place where checklists are referenced.

--------------------------------------------------------------------------------
## Repo Layout (High-Level)

- `src/main/java/dev/patric/dungeonsreborn/`
  - `DungeonsRebornPlugin.java` is the plugin entry point.
- `src/main/java/dev/patric/dungeonsreborn/effects/`
  - `EffectsEngine` + runtime casting pipeline.
  - Actions/conditions/targeters registries in `effects/registry/`.
  - YAML/DSL loader in `effects/config/EffectsYamlAbilities.java`.
  - Item bindings/integration in `effects/integration/` and `effects/items/`.
  - Mana in `effects/mana/`.
  - Damage mechanics in `effects/damage/`.
  - Minions in `effects/minions/`.
- `src/main/java/dev/patric/dungeonsreborn/gui/`
  - GUI framework: windows, components, inputs, and window stack manager.
- `src/main/java/dev/patric/dungeonsreborn/effects/editor/`
  - In-game effects + item editor (menus under `effects/editor/menu/`).
- `src/main/java/dev/patric/dungeonsreborn/mobs/`
  - Mob registry, YAML loader, spawn manager, targeting/AI hooks, spawn eggs.
- `src/main/java/dev/patric/dungeonsreborn/crafting/`
  - Crafting recipe YAML loader, matchers, inventory planner.
- `src/main/java/dev/patric/dungeonsreborn/menus/`
  - Crafting GUI, recipe editor, and recipe discovery menus.

--------------------------------------------------------------------------------
## Docs Structure (No checklist references outside this file)

- Developer guides live in `docs/dev/`.
- Usage guides live in `docs/usage/`.
- Developer guides are intentionally ignored by `.gitignore` (usage guides are kept).
- Checklists (implementation tracking): `docs/checklists/`
- Reference: `docs/reference/` (Paper API javadocs + particle list)
- `docs/README.md` only lists guides and references (no checklist links).

--------------------------------------------------------------------------------
## Configuration Files (Runtime)

The plugin uses **YAML + optional DSL** for effects, items, and mobs.

### Effects
- Main template: `plugins/DungeonsReborn/effects.yml`
- Optional per-ability files:
  - `plugins/DungeonsReborn/effects/abilities/*.yml`
- Scripts (DSL):
  - `plugins/DungeonsReborn/effects/scripts/*.es`

YAML merge policy: **YAML overrides code** (if ids collide, YAML wins).

### Items
- Item definitions:
  - `plugins/DungeonsReborn/effects/items/<itemId>.yml`
- Each item file includes:
  - `item:` matcher (type, material, PDC, custom model data, lore match, etc.)
  - `bindings:` list that maps click types to abilities
  - optional `mana:` bonuses (max/regen)

### Mobs
- Mob specs:
  - `plugins/DungeonsReborn/mobs.yml`
- Supports stats, AI, attacks/passives, phases, bossbars, spawn eggs, and mana drops.

### Crafting Recipes
- Recipe files:
  - `plugins/DungeonsReborn/recipes/<recipeId>.yml`
- Recipes support:
  - multiple outputs, permissions, cooldowns, and variants
  - matching by custom item id, material, tag, or category

--------------------------------------------------------------------------------
## Bundled Defaults (Resources)

- `src/main/resources/effects.yml`: template only (comments, schemaVersion, options, macros empty).
- `src/main/resources/mobs.yml`: skeleton only (no mobs or eggs).
- No bundled abilities/items/scripts by default in resources.

This keeps the jar clean; admins author their own data in the plugin folder.

--------------------------------------------------------------------------------
## Effects Engine Overview

### Core Concepts
- **Ability**: An `AbilitySpec` with optional requirements, costs, cooldown, and a root action.
- **Action**: Executable node (sound, particles, projectile, delay, sequence, etc.).
- **Targeter**: How targets are found (raycast, nearby, cone, etc.).
- **Condition/Requirement**: Guards that block casts (permission, sneaking, etc.).
- **Costs**: Mana, durability, item consumption, etc.
- **Vars**: Cast and player variables used in formulas or scripts.

### YAML Loader
- `EffectsYamlAbilities` compiles YAML into `AbilitySpec` and registers it.
- IDs are normalized via `effects/Ids`.
- Errors are collected during reload and logged with source paths.
- YAML scripts can be inline or file-based (`effects/scripts/*.es`).

### DSL
- DSL is self-contained (no external scripting runtime).
- DSL is referenced by `script:` blocks in YAML with `language: dsl-v1`.
- DSL is executed by the YAML loader/compiler pipeline.

### Particle Customization
- Supports particle data for ITEM, BLOCK, FALLING_DUST, DUST_PILLAR, BLOCK_CRUMBLE, BLOCK_MARKER.
- Supports VIBRATION and TRAIL data.
- EFFECT/INSTANT_EFFECT data customization is unavailable in Paper 1.21.8 (no Particle.Spell type).
- Particle data is resolved at runtime in `Actions` as well as by YAML for ring/point actions.

--------------------------------------------------------------------------------
## Item Binding System

- Items are matched via `ItemMatcher` types:
  - material, custom_model_data, pdc_tag, lore_contains, and/or composite matchers.
- Bindings specify trigger types:
  - LEFT_CLICK, RIGHT_CLICK, SHIFT_LEFT, SHIFT_RIGHT, PASSIVE (periodic)
- Passive bindings can be limited by equipment slot and interval.
- Binding system supports multiple effects per interaction key.

--------------------------------------------------------------------------------
## Mana System

- Session-based mana (resets on logout).
- Regeneration runs on a fixed period (not per tick).
- Casts display action bar mana info when costs apply.
- Mana drop system in mobs:
  - Custom mobs can specify killer/nearby mana drops.
  - Normal mob drops are computed based on health (log-based rule).

--------------------------------------------------------------------------------
## Mob System

- `MobRegistry` stores active custom mobs and their specs.
- `MobYamlRegistry` loads `mobs.yml` and registers specs.
- `MobSpawnManager` handles spawn logic and reload behavior.
- Supports:
  - Stats (health, damage, speed, armor, range)
  - Resistances/immunities
  - Spawn/death FX (particles + sound)
  - Attacks and passives mapped to abilities
  - Multi-phase behavior (health thresholds)
  - Bossbar configuration
  - Spawn eggs (custom items that spawn a mob)

--------------------------------------------------------------------------------
## Minions

- Minions are spawned via a YAML action (e.g., `minion_summon`).
- Minions are standard custom mobs with minion markers (PDC tags).
- Minions can have passive and special attacks, scaling, and lifespan.
- Minion system integrates with the effects engine for abilities and with the mob system for specs.

--------------------------------------------------------------------------------
## GUI System + In-Game Editors

- GUI library uses a window stack (push/pop) with components.
- Inputs include text, chat prompts, and selection windows.
- Editors include:
  - Ability list/detail editor
  - Action graph editor
  - Item editor + bindings
  - Mob editor
  - Crafting recipe editor + discovery list

--------------------------------------------------------------------------------
## Commands (Key)

Effects:
- `/dr effects reload` (reload abilities + item bindings)
- `/dr effects cast <id>` (manual cast)
- `/dr effects debug on|off` (verbose logging)
- `/dr effects particles ...` (particle quality/budget controls)

Mobs:
- `/dr mobs reload` (reload mob specs)
- `/dr mobs editor` (open mob editor)

GUI:
- `/dr gui` (showcase menu)

Crafting:
- `/dr crafting` (crafting test GUI)
- `/dr crafting editor` (recipe editor)
- `/dr crafting reload` (reload recipes)

--------------------------------------------------------------------------------
## Local Testing Layout

- The repository includes a `server/` folder for local testing.
- Runtime YAML lives under `server/plugins/DungeonsReborn/` when using the local server.
- The plugin **does not** overwrite existing YAML files during reloads.

--------------------------------------------------------------------------------
## Checklists (Only referenced here)

- `docs/checklists/MVP/ADVANCEMENTS_CHECKLIST.md`
- `docs/checklists/MVP/CUSTOM_ITEM_CRAFTING_GUI_CHECKLIST.md`
- `docs/checklists/MVP/GUI_LIBRARY_CHECKLIST.md`
- `docs/checklists/MVP/GUI_LIBRARY_INSPECTION_CHECKLIST.md`
- `docs/checklists/MVP/MINIONS_CHECKLIST.md`
- `docs/checklists/MVP/MINIONS_TEST_CHECKLIST.md`
- `docs/checklists/MVP/MOB_DESIGN_CHECKLIST.md`
- `docs/checklists/MVP/MOB_LIBRARY_CHECKLIST.md`
- `docs/checklists/MVP/MOB_SPAWNER_CHECKLIST.md`
- `docs/checklists/MVP/PARTICLE_CUSTOMIZATION_CHECKLIST.md`
- `docs/checklists/MVP/PLUGIN_TEST_CHECKLIST.md`
- `docs/checklists/MVP/RPG_PLUGIN_ROADMAP.md`
- `docs/checklists/MVP/RPG_PLUGIN_TEST_CHECKLIST.md`
- `docs/checklists/MVP/RPG_POLISH_AND_GUI_RETHINK_CHECKLIST.md`
- `docs/checklists/MVP/SHOP_GUI_CHECKLIST.md`
- `docs/checklists/MVP/SPELL_ITEM_UPGRADE_CHECKLIST.md`
- `docs/checklists/MVP/SPELLS_EFFECTS_INGAME_EDITOR_CHECKLIST.md`
- `docs/checklists/V1/BOOK_UPGRADES_CHECKLIST.md`
- `docs/checklists/V1/BOOK_UPGRADES_IMPLEMENTATION.md`
- `docs/checklists/V1/CLASS_SYSTEM_PLAN.md`
- `docs/checklists/V1/COMMANDS_AND_ADMIN_REWORK_PLAN.md`
- `docs/checklists/V1/CUSTOM_XP_MIGRATION_PLAN.md`
- `docs/checklists/V1/USER_MENU_REWORK_PLAN.md`
- `docs/checklists/V1/XP_LEVEL_GATING_CHECKLIST.md`
- `docs/checklists/V2/ULTIMATE_ELEMENTAL_UPGRADES.md`
- `docs/checklists/V3/PLUGIN_BUILDER_PLAN.md`
- `docs/checklists/V3/PLUGIN_BUILDER_VFX_UPDATES.md`
- `docs/checklists/V3/SPELL_ENGINE_VFX_EVOLUTION.md`
- `docs/checklists/V3/SPELL_ENGINE_VFX_PHASE0.md`
- `docs/checklists/V3/VFX_CORE_EXPANSION_PLAN.md`
- `docs/checklists/V4/SPELL_ENGINE_EXPANSION_PLAN.md`
- `docs/checklists/V4/MOB_SYSTEM_COMPLETENESS_PLAN.md`
- `docs/checklists/V4/ITEM_SYSTEM_COMPLETENESS_PLAN.md`

--------------------------------------------------------------------------------
## Conventions / Policies

- YAML overrides code for ability IDs.
- Avoid adding hard-coded debug abilities; prefer YAML/DSL.
- Use ASCII unless a file already uses Unicode.
- Server folder changes are for local testing only.

--------------------------------------------------------------------------------
## Upcoming Item Work

- Effect upgrades: add a way to attach upgrade tiers to item-bound abilities (design TBD).
