# AGENTS.md

This document is the canonical orientation guide for coding agents working in this repository.
Use it as a source-of-truth map for:
- Runtime architecture
- User-facing and admin APIs
- YAML authoring surfaces
- Builder V2 authoring and export workflow

This file is the only place where checklist files are referenced.

--------------------------------------------------------------------------------
## 1) Project Purpose

`DungeonsReborn` is a Paper 1.21.8 plugin focused on data-driven RPG gameplay.

Core systems:
- Effects engine (abilities, actions, conditions, targeters, cooldowns, costs, vars, combat triggers)
- Item binding and item templates (including edible components via data components)
- Mob library (custom mob specs, AI V3, attacks/passives, phases, loot, eggs/spawners/vault/trial spawners)
- Crafting system (vanilla crafting table + 2x2 crafting inventory bridge)
- Shops, quests, upgrades, classes, kits, dungeons, party, progression, locales, textures
- Builder authoring toolkit (Python, V2-only)

--------------------------------------------------------------------------------
## 2) Current Runtime Entry Points

Main plugin class:
- `src/main/java/dev/patric/dungeonsreborn/DungeonsRebornPlugin.java`

Command roots (registered aliases):
- `/dr`
- `/droam`
- `/dungeonroam`

Primary command router:
- `src/main/java/dev/patric/dungeonsreborn/commands/DungeonsRebornCommand.java`

Top-level command branches under `/dr`:
- `help`
- `input`
- `user`
- `crafting`
- `settings`
- `admin`
- `effects`
- `mobs`
- `shop`
- `kits`
- `classes`
- `dungeon`
- `quests`
- `party`
- `chat`
- `upgrades`

Subsystem command classes:
- `src/main/java/dev/patric/dungeonsreborn/commands/EffectsCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/MobsCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/ShopsCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/ClassesCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/KitsCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/DungeonCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/QuestsCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/PartyCommand.java`
- `src/main/java/dev/patric/dungeonsreborn/commands/ChatCommand.java`

--------------------------------------------------------------------------------
## 3) Runtime Data APIs (YAML + Folder Surfaces)

Effects:
- Root file: `plugins/DungeonsReborn/effects.yml`
- Split ability files: `plugins/DungeonsReborn/effects/abilities/*.yml`
- DSL scripts: `plugins/DungeonsReborn/effects/scripts/*.es`
- Item templates/bindings: `plugins/DungeonsReborn/effects/items/*.yml`
- Loader/compiler: `src/main/java/dev/patric/dungeonsreborn/effects/config/EffectsYamlAbilities.java`

Mobs:
- Root file: `plugins/DungeonsReborn/mobs.yml`
- Split mob files: `plugins/DungeonsReborn/mobs/*.yml`
- Loot files: `plugins/DungeonsReborn/loot/*.yml`
- Loader: `src/main/java/dev/patric/dungeonsreborn/mobs/MobYamlRegistry.java`

Crafting:
- Recipe files: `plugins/DungeonsReborn/recipes/*.yml`
- Loader: `src/main/java/dev/patric/dungeonsreborn/crafting/CraftingYamlRegistry.java`
- Runtime bridge: `src/main/java/dev/patric/dungeonsreborn/crafting/vanilla/VanillaCraftingBridge.java`

Shops:
- Root file: `plugins/DungeonsReborn/shops.yml`
- Split files: `plugins/DungeonsReborn/shops/*.yml`
- Loader: `src/main/java/dev/patric/dungeonsreborn/shops/ShopYamlRegistry.java`

Quests:
- Root file: `plugins/DungeonsReborn/quests.yml`
- Split files: `plugins/DungeonsReborn/quests/*.yml`
- Givers file: `plugins/DungeonsReborn/quest_givers.yml`
- Loaders:
  - `src/main/java/dev/patric/dungeonsreborn/quests/QuestYamlRegistry.java`
  - `src/main/java/dev/patric/dungeonsreborn/quests/QuestGiverYamlRegistry.java`

Upgrades:
- Split files: `plugins/DungeonsReborn/effects/upgrades/*.yml`
- Loader: `src/main/java/dev/patric/dungeonsreborn/effects/upgrades/UpgradeYamlRegistry.java`

Classes:
- Root file: `plugins/DungeonsReborn/classes.yml`
- Loader: `src/main/java/dev/patric/dungeonsreborn/classes/ClassYamlRegistry.java`

Kits:
- Root file: `plugins/DungeonsReborn/kits.yml`
- Loader: `src/main/java/dev/patric/dungeonsreborn/kits/KitYamlRegistry.java`

Dungeons:
- Root file: `plugins/DungeonsReborn/dungeon.yml`
- Loader: `src/main/java/dev/patric/dungeonsreborn/dungeons/DungeonYamlRegistry.java`

Notes:
- Loaders are resilient to missing files via `PluginResources.ensureYamlFile(...)` where supported.
- Some registries still optionally copy bundled resources if present.
- Runtime YAML overrides code definitions when IDs collide (effects ability IDs especially).

--------------------------------------------------------------------------------
## 4) Effects Engine API (Authoring + Runtime)

Core classes:
- Engine: `src/main/java/dev/patric/dungeonsreborn/effects/EffectsEngine.java`
- YAML compiler: `src/main/java/dev/patric/dungeonsreborn/effects/config/EffectsYamlAbilities.java`
- Runtime bindings: `src/main/java/dev/patric/dungeonsreborn/effects/integration/EffectsBindings.java`
- Combat runtime package: `src/main/java/dev/patric/dungeonsreborn/effects/combat/`
- Projectile runtime package: `src/main/java/dev/patric/dungeonsreborn/effects/projectile/`

### 4.1 Ability authoring model
An ability supports:
- metadata (`name`, `description`)
- `requirements`
- `costs`
- `cooldown`
- `action` tree (root action node)
- `triggers` (interact and event)

### 4.2 Interact trigger API
Interact triggers map items to abilities.
Supported trigger clicks:
- `RIGHT_CLICK`
- `LEFT_CLICK`
- `SHOOT`
- plus shift variants in item binding APIs

Core trigger enum:
- `src/main/java/dev/patric/dungeonsreborn/effects/integration/InteractTrigger.java`

### 4.3 Combat/event trigger API
Canonical event enum:
- `src/main/java/dev/patric/dungeonsreborn/effects/combat/CombatEventType.java`

Available combat events:
- `ON_ATTACK_ATTEMPT`
- `ON_ATTACK_HIT`
- `ON_ATTACK_CRIT`
- `ON_ATTACK_KILL`
- `ON_HIT_TAKEN`
- `ON_BLOCK`
- `ON_PARRY`
- `ON_DODGE`
- `ON_PROJECTILE_LAUNCH_PRE`
- `ON_PROJECTILE_LAUNCH`
- `ON_PROJECTILE_TRAVEL_STEP`
- `ON_PROJECTILE_COLLIDE_ENTITY_PRE`
- `ON_PROJECTILE_COLLIDE_BLOCK_PRE`
- `ON_PROJECTILE_HIT_ENTITY`
- `ON_PROJECTILE_HIT_BLOCK`
- `ON_PROJECTILE_PIERCE`
- `ON_PROJECTILE_BOUNCE`
- `ON_PROJECTILE_STUCK`
- `ON_PROJECTILE_EXPIRE`
- `ON_PROJECTILE_DEFLECT`
- `ON_PROJECTILE_BLOCKED_SHIELD`
- `ON_PROJECTILE_CANCELLED`
- `ON_DOT_APPLY`
- `ON_DOT_TICK`
- `ON_DOT_EXPIRE`
- `ON_CC_APPLY`
- `ON_CC_EXPIRE`
- `ON_EXECUTE_THRESHOLD`
- `ON_SPRINT`

Projectile/deep filter model is implemented in:
- `src/main/java/dev/patric/dungeonsreborn/effects/combat/CombatEventFilters.java`

### 4.4 YAML action API IDs (EffectsYamlAbilities)
Authoritative parser switch:
- `src/main/java/dev/patric/dungeonsreborn/effects/config/EffectsYamlAbilities.java` (parse action switch)

Supported action IDs:
- `sound`
- `animate`
- `animate_shape`
- `motion`
- `state_machine`
- `burst`
- `pulse`
- `loop`
- `trail`
- `attach`
- `follow`
- `lock_to_target`
- `lock_target`
- `animate_realtime`
- `animate_real_time`
- `particles_ring`
- `particles_point`
- `particles_physics`
- `particles_physics_points`
- `particles_physics_polyline`
- `particles_physics_mesh`
- `particles_line`
- `particles_arc`
- `particles_disk`
- `particles_sphere_shell`
- `particles_sphere_filled`
- `particles_sphere_fill`
- `particles_sphere`
- `particles_helix`
- `particles_bezier`
- `particles_spline`
- `particles_points`
- `particles_polyline`
- `particles_mesh`
- `preset_spline_motion`
- `particles_cone`
- `particles_cylinder`
- `particles_box`
- `particles_polygon`
- `preset_shockwave`
- `preset_morph_ring`
- `preset_gradient_ring`
- `preset_morph_line`
- `preset_gradient_line`
- `preset_morph_arc`
- `preset_gradient_arc`
- `preset_morph_disk`
- `preset_gradient_disk`
- `preset_morph_sphere_shell`
- `preset_gradient_sphere_shell`
- `preset_morph_sphere_filled`
- `preset_gradient_sphere_filled`
- `preset_morph_helix`
- `preset_gradient_helix`
- `preset_morph_cone`
- `preset_gradient_cone`
- `preset_morph_cylinder`
- `preset_gradient_cylinder`
- `preset_morph_box`
- `preset_gradient_box`
- `preset_morph_polygon`
- `preset_gradient_polygon`
- `preset_gradient_bezier`
- `preset_gradient_spline`
- `preset_orbit`
- `preset_orbiting_runes`
- `preset_swirl`
- `preset_spiral_aura`
- `preset_beam_chargeup`
- `chance`
- `debug_log`
- `raycast_hit_entity`
- `for_each_target`
- `damage`
- `damage_typed`
- `set_resistance`
- `add_resistance`
- `clear_resistance`
- `set_reflect`
- `clear_reflect`
- `damage_percent`
- `damage_true`
- `damage_falloff`
- `damage_crit`
- `damage_lifesteal`
- `damage_dot`
- `damage_over_time`
- `damage_chain`
- `chain_damage`
- `chain_lightning`
- `ground_damage`
- `damage_ground`
- `heal`
- `heal_percent`
- `heal_over_time`
- `heal_hot`
- `shield`
- `absorb`
- `potion`
- `totem`
- `knockback`
- `pull`
- `projectile`
- `projectile_auto_aim_nearest`
- `projectile_autoaim_nearest`
- `projectile_homing_nearest`
- `minion_summon`
- `summon_minion`
- `minions`

### 4.5 YAML targeter API IDs
Authoritative parser:
- `src/main/java/dev/patric/dungeonsreborn/effects/config/EffectsYamlAbilities.java` (parse targeter switch)

Supported targeter IDs:
- `self`
- `context_target`
- `context-target`
- `projectile_hit`
- `projectile-hit`
- `look_ray`
- `look-ray`
- `sphere`
- `nearest`
- `nearest_within_angle`
- `nearest-within-angle`
- `nearest_angle`
- `nearest-angle`
- `cone`
- `line_of_sight`
- `line-of-sight`
- `los`
- `ground_sphere`
- `ground-sphere`
- `ground_snap`
- `ground-snap`
- `box`
- `cylinder`
- `capsule_ray`
- `capsule-ray`
- `chain`
- `random`
- `random_n`
- `random-n`
- `weighted_distance`
- `weighted-distance`
- `weighted_threat`
- `weighted-threat`
- `union`
- `intersection`
- `difference`

### 4.6 YAML condition API IDs
Authoritative parser:
- `src/main/java/dev/patric/dungeonsreborn/effects/config/EffectsYamlAbilities.java` (parse condition switch)

Supported condition IDs:
- `always`
- `sneaking`
- `permission`
- `has_item_tag`
- `has-item-tag`
- `caster_has_tag`
- `caster-has-tag`
- `has_tag`
- `has-tag`
- `caster_lacks_tag`
- `caster-lacks-tag`
- `lacks_tag`
- `lacks-tag`
- `item_match`
- `item-match`
- `has_item`
- `has-item`
- `world`
- `biome`
- `time_between`
- `time-between`
- `region`
- `in_party`
- `in-party`
- `party_size_gte`
- `party-size-gte`
- `party_size_lte`
- `party-size-lte`
- `party_leader`
- `party-leader`
- `health_gte`
- `health-gte`
- `health_lte`
- `health-lte`
- `health_pct_gte`
- `health-pct-gte`
- `health_pct_lte`
- `health-pct-lte`
- `mana_gte`
- `mana-gte`
- `mana_lte`
- `mana-lte`
- `mana_pct_gte`
- `mana-pct-gte`
- `mana_pct_lte`
- `mana-pct-lte`
- `level_gte`
- `level-gte`
- `level_lte`
- `level-lte`
- `xp_gte`
- `xp-gte`
- `xp_lte`
- `xp-lte`
- `stat_gte`
- `stat-gte`
- `stat_lte`
- `stat-lte`
- `has_target`
- `has-target`
- `chance`
- `var_present`
- `var-present`
- `var_equals`
- `var-equals`
- `var_gte`
- `var-gte`
- `var_lte`
- `var-lte`
- `var_gt`
- `var-gt`
- `var_lt`
- `var-lt`
- `not`
- `and`
- `or`

### 4.7 Item matcher API IDs
From item binding parser in `EffectsYamlAbilities`:
- `any_non_air` / aliases (`any-non-air`, `any_nonair`, `any`)
- `material`
- `custom_model_data` / aliases (`custom-model-data`, `cmd`)
- `pdc_tag` / aliases (`pdc-tag`, `tag`)
- `lore_contains` / alias (`lore-contains`)
- `and`
- `or`

--------------------------------------------------------------------------------
## 5) Items System API

Primary runtime classes:
- `src/main/java/dev/patric/dungeonsreborn/effects/items/ItemTemplateCompiler.java`
- `src/main/java/dev/patric/dungeonsreborn/effects/items/ItemMarkers.java`
- `src/main/java/dev/patric/dungeonsreborn/effects/integration/EffectsBindings.java`

### 5.1 Binding model
Item files in `plugins/DungeonsReborn/effects/items/*.yml` support:
- `item:` matcher/template
- `bindings:` click/event bindings to ability IDs
- `stats:` numeric item stat map
- `mana:` bonuses
- behavior hooks

### 5.2 Edible/food data components
True edible behavior is implemented via data components in `ItemTemplateCompiler`:
- `FOOD`
- `CONSUMABLE`
- `USE_COOLDOWN`
- `USE_REMAINDER`

Canonical YAML path:
- `item.meta.components.*`

Authoring alias:
- `item.edible.*` (compiled into canonical components)

Supported consume effect types:
- `PLAY_SOUND`
- `TELEPORT_RANDOMLY`
- `REMOVE_STATUS_EFFECTS`
- `CLEAR_ALL_STATUS_EFFECTS`
- `APPLY_STATUS_EFFECTS`

### 5.3 PDC markers used by runtime
Authoritative constants:
- `src/main/java/dev/patric/dungeonsreborn/effects/items/ItemMarkers.java`

Important keys:
- `effects_item_id`
- `effects_item_version`
- `effects_right_click_abilities`
- `effects_left_click_abilities`
- `effects_shift_right_click_abilities`
- `effects_shift_left_click_abilities`
- `effects_passive_abilities`
- `effects_item_stats`
- `effects_item_affixes`
- `effects_item_tier`
- `effects_item_rarity`
- `effects_item_tags`
- `effects_item_category`
- mana-related keys (`effects_mana_*`)
- upgrade-related keys (`effects_upgrade_*`)

Threading contract:
- Item marker write helpers enforce primary thread usage.

--------------------------------------------------------------------------------
## 6) Mob System API

Core classes:
- `src/main/java/dev/patric/dungeonsreborn/mobs/MobYamlRegistry.java`
- `src/main/java/dev/patric/dungeonsreborn/mobs/MobRegistry.java`
- `src/main/java/dev/patric/dungeonsreborn/mobs/MobSpawnManager.java`
- AI package: `src/main/java/dev/patric/dungeonsreborn/mobs/ai/`

Supported mob features:
- base entity type, name, nameplate toggle
- stats (health/damage/armor/speed/range)
- resistances, reflections, immunities
- phases by health threshold
- event abilities (`onHit`, `onHurt`, `onTarget`, `onKill`, timed hooks)
- spawn/death FX (particle/sound)
- equipment
- loot
- mana drops
- eggs
- spawner blocks
- trial spawner profiles
- vault profiles

AI:
- Config default engine currently set to `V3` in `config.yml`
- Async planner and guardrails exist under `mobs.ai.*`
- Runtime observability via `/dr mobs ai ...`

Model visuals:
- `mobs.models.enabled` is currently false in default config
- Model bridge can fall back to vanilla visuals

--------------------------------------------------------------------------------
## 7) Crafting API

Current system uses vanilla crafting interfaces (not custom crafting GUI execution path):
- 3x3 crafting table (`InventoryType.WORKBENCH`)
- 2x2 player inventory crafting (`InventoryType.CRAFTING`) where recipe shape permits

Runtime bridge:
- `src/main/java/dev/patric/dungeonsreborn/crafting/vanilla/VanillaCraftingBridge.java`

Key components:
- `VanillaRecipeRegistrar` (register representable recipes with Bukkit API)
- `CraftingMatcher` (authoritative matching for plugin semantics)
- `CraftingRuleEngine` (discovery, requirements, cooldowns, hooks)
- `CraftingCostExecutor` (cost consumption)
- `CraftingCooldownStore` (cooldown tracking)

Recipe semantics preserved:
- discovery locking and recipe book visibility
- requirements/costs/cooldowns
- pre/post hooks
- extra outputs/byproducts
- shift-click crafting loops with safety checks

--------------------------------------------------------------------------------
## 8) Other Domain APIs

Shops:
- Loader: `ShopYamlRegistry`
- Runtime sessions/trading: `ShopSessionManager`, `ShopTradeListener`, `ShopStockManager`

Quests:
- Loaders: `QuestYamlRegistry`, `QuestGiverYamlRegistry`
- Runtime: `QuestService`, `QuestListener`

Upgrades:
- Loader: `UpgradeYamlRegistry`
- Runtime: `UpgradeService`, `UpgradeOnDamagedListener`

Classes:
- Loader: `ClassYamlRegistry`
- Runtime: `ClassService`, `ClassSkillService`, `ClassBonusService`

Kits:
- Loader: `KitYamlRegistry`
- Runtime: `KitService`

Dungeons:
- Loader: `DungeonYamlRegistry`
- Runtime: queue/session services

Party:
- Runtime services/listeners under `src/main/java/dev/patric/dungeonsreborn/party/`

Progression:
- Runtime services under `src/main/java/dev/patric/dungeonsreborn/progression/`

Textures:
- Runtime build/delivery under `src/main/java/dev/patric/dungeonsreborn/textures/`

--------------------------------------------------------------------------------
## 9) Effects Runtime Vars API

Authoritative constants:
- `src/main/java/dev/patric/dungeonsreborn/effects/Vars.java`

Important var keys:
- `projectile_last_hit`
- `projectile_frame`
- `combat_event_type`
- `combat_event_source`
- `combat_event_damage`
- `combat_event_attacker`
- `combat_event_victim`
- projectile telemetry keys (`combat_event_projectile_*`)
- mob/minion keys (`mob_target`, `mob_owner`, `mob_id`, `minion_*`)

--------------------------------------------------------------------------------
## 10) Builder API (V2-Only)

Package status:
- `dungeonsreborn_builder` is v2-only.
- Root package exports only `v2`.
- Old v1 imports raise explicit migration errors.

Import pattern:
- `from dungeonsreborn_builder.v2 import ...`

Canonical files:
- `plugin_builder/dungeonsreborn_builder/v2/__init__.py`
- `plugin_builder/dungeonsreborn_builder/v2/*.py`

### 10.1 Public v2 export surface
Core:
- `BuildContext`
- `BuildValidationError`
- `KNOWN_DOMAINS`
- `Ref`
- `as_ref`

Types:
- `DomainName`
- `BuildProfile`
- `Identifier`
- `Symbol`
- `FieldPath`
- `TriggerPhase`
- `ProjectileBlockCollision`

Enums and token helpers:
- `Material`, `EntityType`, `Sound`, `Particle`, `PotionEffect`
- `ItemClick`, `ItemUseAnimation`, `PassiveSlot`
- `DamagePolicy`
- `CombatEventType`, `CombatCooldownScope`, `CombatEventTargetBind`, `CombatEventOriginBind`
- `ProjectileFamily`
- `MobAiProfile`, `MobSoundProfile`
- `UpgradeRarity`, `UpgradeActivator`
- custom token helpers: `custom_material`, `custom_entity`, `custom_sound`, `custom_particle`, `custom_potion_effect`

Effects DSL:
- `Action`, `Requirement`, `Cost`, `EventTrigger`
- `fx` helper namespace
- `AbilityV2`, `ability(...)`

Mob API:
- `MobV2`
- `TimedAbility`
- `mob.create(...)`

Item API:
- `ItemV2`
- `ItemBindSpec`
- `MetaPreset`
- `ConsumeEffectSpec`
- `ConsumeStatusEffectSpec`
- helper namespaces: `bind`, `consume_fx`, `consume_status`, `meta`, `item.create(...)`

Other domains:
- `RecipeV2`, `recipe.for_item(...)`
- `ShopV2`, `shop.create(...)`
- `QuestV2`, `quest.create(...)`
- `UpgradeV2`, `upgrade.create(...)`

Bundles:
- `BundleV2`
- `GhostBundle`
- `EliteMobBundle`
- `WeaponBundle`
- `ConsumableBundle`
- `TrialRewardBundle`

Export pipeline:
- `PackV2`
- `pack_v2(...)`

Migration helpers:
- `migrate_source`
- `migrate_file`
- `migrate_many`
- `iter_python_files`

### 10.2 Builder V2 domain method map
AbilityV2:
- `.requirement(...)`
- `.cost(...)`
- `.cooldown(ticks)`
- `.on_event(...)`
- `.on_projectile(...)`
- `.on_projectile_pre(...)`
- `.on_projectile_hit(...)`
- `.on_projectile_launch(...)`
- `.build()`

fx helpers (key ones):
- `fx.sequence(...)`
- `fx.for_each_target(...)`
- `fx.sound(...)`
- `fx.sphere_shell(...)`
- `fx.projectile(...)`
- `fx.projectile_auto_aim_nearest(...)`
- `fx.damage(...)`
- `fx.potion(...)`
- `fx.targeter(...)`
- `fx.target_context(...)`

MobV2:
- `.tier(...)`
- `.show_name(...)`
- `.stats(...)`
- `.ai_quick(...)`
- `.silent(...)`
- `.sounds(...)`
- `.sound_overrides(...)`
- `.look_skin_head(...)`
- `.equip_main_hand(...)`
- `.equip_off_hand(...)`
- `.equip_hands(...)`
- `.events(...)`
- `.override(...)`
- `.build()`

ItemV2:
- `.lore(...)`
- `.visual(...)`
- `.head_texture(...)`
- `.bind(...)`
- `.bind_use(...)`
- `.food(...)`
- `.consumable(...)`
- `.use_cooldown(...)`
- `.use_remainder(...)`
- `.edible(...)`
- `.meta_preset(...)`
- `.weapon_basic(...)`
- `.consumable_basic(...)`
- `.build()`

RecipeV2:
- `.discovery(show_in_book, unlock_on_craft, hidden)`
- `.output_amount(...)`
- `.build()`

ShopV2:
- `.sell(...)`
- `.build()`

QuestV2:
- `.kill_mob(...)`
- `.craft_item(...)`
- `.collect_item(...)`
- `.reward_xp(...)`
- `.reward_tokens(...)`
- `.reward_item(...)`
- `.require_level(...)`
- `.build()`

UpgradeV2:
- `.rarity(...)`
- `.for_item(...)`
- `.spell(...)`
- `.secondary(...)`
- `.build()`

PackV2:
- `.add(...)`
- `.id_map()`
- `.validate()`
- `.preview_export()`
- `.export(output_dir)`

### 10.3 Builder CLI API
Entry:
- `python -m dungeonsreborn_builder <command>`
- CLI implementation: `plugin_builder/dungeonsreborn_builder/cli.py`

Commands:
- `build-v2 <script> -o <output>`
- `validate-v2 <script> [--strict]`
- `build <script> -o <output>` (transitional alias)
- `validate <script> [--strict]` (transitional alias)
- `preview <script>`
- `id-map <script>`
- `new <dest> [--template ...]`
- `migrate-v1-to-v2 <path> [--no-backup]`
- `watch <script> -o <output> [--interval ...]`

### 10.4 Builder templates available
Path: `plugin_builder/templates/`

Templates:
- `v2_starter_pack.py`
- `v2_mob_quickstart.py`
- `v2_weapon_bundle.py`
- `v2_shop_pack.py`
- `v2_campaign_pack.py`
- `v2_consumable_showcase.py`
- `v2_ghost_zombie.py`
- `v2_gun_test_pack.py`
- `v2_ninja_blade.py`
- `v2_projectile_trigger_showcase.py`
- `v2_cheese_food.py`

### 10.5 Builder export output layout
`PackV2.export(output_dir)` writes:
- `effects/abilities/<abilityId>.yml`
- `effects/items/<itemId>.yml`
- `mobs/<mobId>.yml`
- `recipes/<recipeId>.yml`
- `shops/<shopId>.yml`
- `quests/<questId>.yml`
- `upgrades/<upgradeId>.yml`
- `v2_id_map.yml`
- `v2_pack.yml`

--------------------------------------------------------------------------------
## 11) Enum and Token Coverage Notes

`plugin_builder/dungeonsreborn_builder/v2/enums.py` contains exhaustive runtime-oriented enums for:
- Materials
- Entity types
- Sounds
- Particles
- Potion effects

It also includes:
- strict coercion helpers
- nearest-token suggestions on invalid values
- explicit custom token escape hatches (`custom_*`)

For unknown tokens in closed sets, use explicit custom tokens rather than raw loose strings.

--------------------------------------------------------------------------------
## 12) Recommended Workflow for Another LLM

1. Read this file first.
2. For effects YAML/API changes, inspect:
   - `EffectsYamlAbilities`
   - `CombatEventType`
   - `CombatEventFilters`
   - `EffectsCommand`
3. For item behavior, inspect:
   - `ItemTemplateCompiler`
   - `ItemMarkers`
   - `EffectsBindings`
4. For crafting behavior, inspect:
   - `CraftingYamlRegistry`
   - `VanillaCraftingBridge`
   - `CraftingRuleEngine`
5. For mobs/AI, inspect:
   - `MobYamlRegistry`
   - `MobRegistry`
   - `mobs/ai/*`
6. For content authoring, use builder v2:
   - `from dungeonsreborn_builder.v2 import ...`
   - export to `server/plugins/DungeonsReborn/`
7. Validate by running command surfaces:
   - `/dr effects reload`
   - `/dr mobs reload`
   - `/dr crafting reload` or `/dr admin reload`
   - check startup/reload warnings in console

--------------------------------------------------------------------------------
## 13) Docs Structure (No checklist references outside this file)

- Developer guides: `docs/dev/`
- Usage guides: `docs/usage/`
- References: `docs/reference/` (Paper API docs, particles, etc.)
- Checklists: `docs/checklists/`
- `docs/README.md` should not include checklist links.

--------------------------------------------------------------------------------
## 14) Bundled Defaults in `src/main/resources`

Current bundled defaults are intentionally minimal:
- `config.yml`
- `effects.yml` (template)
- `mobs.yml` (template)
- `plugin.yml`

No bundled full gameplay datasets are required for first startup.

--------------------------------------------------------------------------------
## 15) Local Testing Layout

- Local server folder: `server/`
- Runtime plugin data for tests: `server/plugins/DungeonsReborn/`
- Builder scripts often export directly into that folder for immediate reload testing.

--------------------------------------------------------------------------------
## 16) Checklists (Only referenced here)

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
- `docs/checklists/V4/PLUGIN_BUILDER_COMPLETENESS_PLAN.md`
- `docs/checklists/V4/MOB_SYSTEM_COMPLETENESS_PLAN.md`
- `docs/checklists/V4/ITEM_SYSTEM_COMPLETENESS_PLAN.md`
- `docs/checklists/V5/TEMPLATE_V2_300H_GAMEPLAY_CHECKLIST.md`
- `docs/checklists/V5/TRIAL_SPAWNER_VAULT_SYSTEM_CHECKLIST.md`

--------------------------------------------------------------------------------
## 17) Conventions / Policies

- YAML ability IDs from YAML override code-defined ability IDs.
- Prefer data-driven content over hardcoded debug content.
- Use ASCII unless a file already uses non-ASCII.
- Server folder edits are for local testing.
- Keep Bukkit world/entity mutation on main thread.
- For projectile/combat async logic, off-thread work must use immutable snapshots only.

