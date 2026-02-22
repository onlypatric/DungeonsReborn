# DungeonsReborn Plugin Builder

Python tooling for generating YAML/DSL config files used by the DungeonsReborn plugin.
Admins can build content by writing Python code that emits YAML files.

Supported targets:
- effects/abilities (YAML + DSL)
- items (matchers, meta, bindings, GUI previews)
- mobs (spawners, eggs, loot, mana)
- trial spawners + vault profiles (mob/vault runtime schema)
- upgrades
- loot pools
- shops
- quests
- classes
- heads registry

Conventions:
See `plugin_builder/CONVENTIONS.md` for YAML layout rules and export paths.

Enums:
Use enum-style accessors for vanilla identifiers:
- `Material.DIAMOND_SWORD`
- `EntityType.ZOMBIE`
- `Particle.FLAME`

Enum update workflow + deprecation policy:
See `plugin_builder/ENUMS_AND_DEPRECATIONS.md`.

GUI icon kit:
Use semantic head IDs for UI icons.
```python
from dungeonsreborn_builder import GuiIcon, gui_icon_head

head_id = gui_icon_head(GuiIcon.NAV_BACK)
```

## Templates (Effects)

Prebuilt templates live in `plugin_builder/templates/effects/`. Each file is editable
and writes a single ability YAML when executed. See `plugin_builder/templates/effects/README.md`
for the current catalog.

Template packs (multi-ability) live under `plugin_builder/templates/` and emit a single
`effects.yml` or batch of ability files.

## Templates (Items)

Item builder examples live in `plugin_builder/templates/items/`:

- `weapon_example.py`
- `armor_example.py`
- `material_example.py`
- `validate_items.py` (lintable example for CI checks)

### Item Textures + Mob Models

Item builder APIs support `visual` PNG textures, while mob full replacement is ModelEngine-only:

```python
from dungeonsreborn_builder import ItemBuilder, ItemVisualSpec, MobBuilder, Material, EntityType

item = (
    ItemBuilder("item_example_texture")
    .material(Material.PAPER)
    .visual_texture("items/custom/my_scroll.png")
    .visual_model_key("dungeonsreborn:items/custom/my_scroll")
)

mob_full_model = (
    MobBuilder("mob_example_full_model")
    .mob_type(EntityType.ZOMBIE)
    .model_full_replacement(
        model_id="dr_zombie_frozen",
        animations={
            "walk": "dr_zombie_frozen_walk",
            "attack": "dr_zombie_frozen_attack",
        },
    )
)
```

## Trial Spawners + Vaults

`MobExporter.write_batch(...)` supports `trial_spawners` and `vaults` sections:

```python
from dungeonsreborn_builder import (
    MobExporter,
    TrialSpawnerBuilder,
    TrialSpawnerProfileSpec,
    VaultBuilder,
    weighted_mob,
    weighted_item,
)

trial = (
    TrialSpawnerBuilder("trial_lowlands_normal")
    .mob("mob_lowlands_scout", weight=3.0)
    .mob("mob_lowlands_brute", weight=1.0)
    .waves(3)
    .simultaneous(2)
    .cooldown_ticks(100)
    .required_players(1)
    .activation_range(14.0)
    .key_loot_pool("pool_trial_keys_lowlands_normal")
    .ominous_profile(
        TrialSpawnerProfileSpec(
            mob_pool=[weighted_mob("mob_lowlands_scout", 2.0), weighted_mob("mob_lowlands_ominous_brute", 2.0)],
            waves=4,
            simultaneous=3,
            cooldown_ticks=80,
            key_loot_pool="pool_trial_keys_lowlands_ominous",
        )
    )
)

vault = (
    VaultBuilder("vault_lowlands_normal")
    .key_item("item_trial_key_lowlands")
    .loot_pools("pool_vault_lowlands_normal", "pool_vault_lowlands_ominous")
    .activation_range(5.0)
    .deactivation_range(8.0)
    .display_item("item_trial_key_lowlands", weight=3.0)
    .display_item("item_trial_supply_lowlands", weight=1.0)
)

MobExporter("out/mobs").write_batch(
    builders=[],
    filename="trial_vault_profiles.yml",
    trial_spawners=[trial],
    vaults=[vault],
)
```

## Validation Helpers

Use validation helpers to collect issues without raising exceptions:

```python
from dungeonsreborn_builder import validate_effects_document, summarize_issues

issues = validate_effects_document(doc)
if issues:
    print(summarize_issues(issues))
```

## Schema Snapshot Export

Generate a YAML snapshot for docs/onboarding:

```python
from dungeonsreborn_builder import SchemaExporter

SchemaExporter(output_dir="out").write_effects_snapshot()
```

## Tooling Scripts

Builder audits and snapshots (run from repo root):

- `scripts/builder_schema_dump.py` -> writes schema snapshots under `docs/reference/builder_schema/`
- `scripts/builder_audit.py --data <dir>` -> basic type/count audit of builder outputs
- `scripts/builder_locale_audit.py --data <dir>` -> locale key coverage audit
- `scripts/check_builder_heads.py --data <dir>` -> head id registry coverage
- `scripts/check_builder_locale_keys.py --data <dir>` -> locale key coverage (legacy)

## GUI Previews + Heads

Items, mobs, quests, and shops can emit GUI preview metadata and head ids:
```python
from dungeonsreborn_builder import GuiTileSpec
from dungeonsreborn_builder import ItemBuilder, Material

tile = GuiTileSpec(head="ICON_ITEMS", title_key="gui.items.index.title")
item = ItemBuilder("gui_item").material(Material.STONE).gui_preview_tile(tile)
```

## Example (Effects)

```python
from dungeonsreborn_builder.effects import Ability, EffectsExporter, particles_ring
from dungeonsreborn_builder import Particle

ability = Ability(
    ability_id="fire_pulse",
    name="<red>Fire Pulse</red>",
    description="<gray>A short fiery blast.</gray>",
    action=particles_ring(Particle.FLAME, radius=1.4, points=28, count=1),
)

exporter = EffectsExporter("./plugins/DungeonsReborn/effects/abilities")
exporter.write_ability(ability)
exporter.write_index(["fire_pulse.yml"])

# Builder chaining + quick pipeline
from dungeonsreborn_builder.effects import AbilityBuilder, sound, particles_ring
from dungeonsreborn_builder import Particle

AbilityBuilder("shock_pulse") \
    .name("<blue>Shock Pulse</blue>") \
    .with_cooldown(40) \
    .action(particles_ring(Particle.ELECTRIC_SPARK, radius=2.0, points=28)) \
    .pipeline() \
    .write(exporter)

# Batch presets
from dungeonsreborn_builder.effects import ability_pack_undead_t1

builders = ability_pack_undead_t1()
filenames = []
for builder in builders:
    ability = builder.build()
    filenames.append(f"{ability.ability_id}.yml")
    exporter.write_ability(ability)
exporter.write_index(filenames, index_name="undead_t1_index.txt")
```

High-level VFX helpers (builder + DSL):

```python
from dungeonsreborn_builder.effects import (
    ability_from_vfx,
    EffectsDocument,
    ShapeTemplate,
)
from dungeonsreborn_builder.animation import AnimationBuilder
from dungeonsreborn_builder.vfx import (
    dsl_ring,
    dsl_helix,
    dsl_mesh,
    dsl_morph_helix,
    dsl_gradient_ring,
    orbiting_runes,
)
from dungeonsreborn_builder import Particle

# Compose VFX timelines with minimal glue.
timeline = (
    AnimationBuilder()
    .burst(dsl_ring(Particle.ELECTRIC_SPARK, radius=2.4, points=36))
    .schedule(20, dsl_morph_helix(Particle.END_ROD, radius=2.6, length=5.2, turns=5))
    .loop(times=8, every=8, value=orbiting_runes(Particle.ENCHANT, copies=6))
)

ability = ability_from_vfx("storm_chant", "<aqua>Canto della Tempesta</aqua>", timeline)

# Shapes + abilities in one file.
document = EffectsDocument(
    shapes={
        "sigil": ShapeTemplate(
            shape="mesh",
            points=[
                [0, 0, 0],
                [0.4, 0, 0.2],
                [0.8, 0, 0],
                [0.4, 0, -0.2],
            ],
        )
    },
    abilities={"storm_chant": ability.build()},
)
```

DSL builder (no raw strings):

```python
from dungeonsreborn_builder.dsl import DslBuilder
from dungeonsreborn_builder.effects import AbilityBuilder
from dungeonsreborn_builder import Particle

dsl = DslBuilder()
on_cast = dsl.on_cast()
on_cast.stmt("potion", effect="SPEED", durationTicks=80, amplifier=0)
tick = on_cast.block("on_tick", ticks=80, every=4)
tick.stmt("particles.ring", particle=Particle.ELECTRIC_SPARK, radius=2.2, points=28, count=1)

ability = AbilityBuilder("spark_pulse").name("<aqua>Scintilla</aqua>").dsl(dsl).build()
```

More presets:

```python
from dungeonsreborn_builder.effects import aoe_pulse, effect_bolt, status_aura
from dungeonsreborn_builder import Particle

aoe_pulse("ice_wave", "<aqua>Ice Wave</aqua>", Particle.SNOWFLAKE, radius=2.4, damage_amount=3, effect="SLOWNESS", duration_ticks=60)
effect_bolt("frost_bolt", "<aqua>Frost Bolt</aqua>", Particle.SNOWFLAKE, range_blocks=14, effect="SLOWNESS", duration_ticks=60, sound_id="minecraft:block.glass.break")
status_aura("warding_aura", "<green>Warding Aura</green>", Particle.END_ROD, radius=2.2, effect="REGENERATION", duration_ticks=80)
```

VFX presets:

```python
from dungeonsreborn_builder import vfx, Particle

vfx.preset_fire_core(radius=2.4)
vfx.preset_frost_core(radius=2.4)
vfx.preset_storm_core(radius=2.4)
vfx.preset_earth_core(radius=2.4)
vfx.preset_arcane_core(radius=2.4)

# Volumetric + directional
vfx.cloud(Particle.CLOUD, radius=2.6, height=1.6)
vfx.sphere(Particle.END_ROD, radius=2.8, filled=False)
vfx.cone_forward(Particle.FLAME, radius=2.4, height=3.2)
vfx.arc_side(Particle.ELECTRIC_SPARK, radius=2.2, angle_deg=160)

# Signature presets
vfx.preset_fire_signature(radius=2.8)
vfx.preset_frost_signature(radius=2.8)
vfx.preset_storm_signature(radius=2.8)
vfx.preset_earth_signature(radius=2.8)
vfx.preset_arcane_signature(radius=2.8)

# Advanced motion
vfx.spiral_rise(Particle.END_ROD, radius=2.2, height=4.6)
vfx.spiral_fall(Particle.ASH, radius=2.2, height=4.6)
vfx.pulse_envelope(Particle.ELECTRIC_SPARK, start_radius=1.2, end_radius=3.0)
vfx.beam_chargeup_release(Particle.END_ROD, duration_ticks=40, charge_ticks=20)
vfx.orbital_dual(Particle.END_ROD, Particle.FLAME)
vfx.time_sliced_bursts(Particle.FLAME, radius=2.2, slices=4, gap_ticks=4)
vfx.bezier_path(Particle.END_ROD, points=[[0, 0, 0], [0.5, 1.2, 0], [1.4, 0.6, 0], [2.0, 0, 0]])
vfx.spline_path(Particle.END_ROD, points=[[0, 0, 0], [0.6, 1.0, 0.3], [1.2, 0.4, 0.6], [1.8, 0, 0.2]])
vfx.rotating_rings(Particle.ELECTRIC_SPARK, radius=2.2, layers=3)

# Reactive VFX (Phase 3)
vfx.impact_burst(Particle.CRIT, radius=1.4)
vfx.hit_trail(Particle.END_ROD, radius=2.0)
vfx.status_halo(Particle.ENCHANT, radius=1.6)
vfx.crit_flash(Particle.ELECTRIC_SPARK, radius=1.2)
vfx.parry_sparks(Particle.CRIT, radius=1.4)
vfx.shield_break(Particle.CLOUD, radius=1.8)
```

Preset registry metadata:

```python
from dungeonsreborn_builder.vfx import VfxRegistry, VfxPresetMeta, preset_dsl_fire_core

registry = VfxRegistry()
registry.register_preset(
    "fire_core",
    preset_dsl_fire_core(2.2),
    tags=["fire", "core", "loop"],
    description="Core fire ring with orbit.",
)

catalog = registry.to_catalog()
```

AnimationBuilder:

```python
from dungeonsreborn_builder.animation import AnimationBuilder, VFXParams
from dungeonsreborn_builder.vfx import dsl_ring, dsl_orbit, preset_dsl_fire_core
from dungeonsreborn_builder import Particle

combo = preset_dsl_fire_core(2.2) + dsl_orbit(Particle.FLAME, radius=2.8, copies=6)

animation = (
    AnimationBuilder()
    .pack(VFXParams(color="#ff6a00", size=1.0, density=2))
    .burst(combo)
    .schedule(20, dsl_ring(Particle.END_ROD, radius=3.2, points=40))
    .loop(times=6, every=10, value=dsl_ring(Particle.ELECTRIC_SPARK, radius=2.6, points=32))
)

script = animation.build_script()
```

VFX QoS (budgets + scaling):

```python
from dungeonsreborn_builder.vfx_qos import VfxQoSConfig, VfxLiteMode, VfxBudget, scale_action
from dungeonsreborn_builder.effects import particles_ring
from dungeonsreborn_builder import Particle

qos = VfxQoSConfig(
    global_multiplier=0.8,
    budget=VfxBudget(max_particles_per_ability=1500, max_particles_per_tick=220),
    lite_mode=VfxLiteMode(
        enabled=True,
        preset_swaps={"preset_fire_signature": "preset_fire_core"},
    ),
)

action = particles_ring(Particle.FLAME, radius=2.6, points=40, count=2)
scaled_action = scale_action(action, qos.global_multiplier)
```

## Example (Mobs)

```python
from dungeonsreborn_builder.mobs import MobAttack, MobBuilder, MobExporter, undead_t1_pack
from dungeonsreborn_builder import EntityType

mob = (
    MobBuilder("undead_t1_bonewalker")
    .mob_type(EntityType.ZOMBIE)
    .name("<gray>Bonewalker</gray>")
    .stats(health=20, damage=4, speed=0.26)
    .main_attack(MobAttack(ability="mob_basic_slash", cooldown_ticks=30))
    .loot_pool("undead_t1")
)

exporter = MobExporter("./plugins/DungeonsReborn/mobs")
exporter.write_mob(mob)

# Batch preset
batch = undead_t1_pack()
exporter.write_batch(batch, "undead_t1_pack.yml")
```

Mob AI V2 (simple stances + advanced goals + phase override):

```python
from dungeonsreborn_builder import (
    EntityType,
    Mob,
    MobAiEngine,
    MobAiGoalSpec,
    MobAiGoalType,
    MobAiHooksSpec,
    MobAiPhaseMergeMode,
    MobAiProfile,
    MobAiSpec,
    MobPhase,
)

v2 = (
    Mob("mob_v2_guardian")
    .mob_type(EntityType.ZOMBIE)
    .name("<aqua>V2 Guardian</aqua>")
    .stats(health=30, damage=6, armor=4, speed=0.27)
    .ai_simple(
        MobAiProfile.DEFENSIVE,
        aggro_radius=14.0,
        call_for_help_radius=10.0,
        open_doors=True,
        hooks=MobAiHooksSpec(on_enter_engage="ability_mob_guardian_engage"),
        goals=[
            MobAiGoalSpec(MobAiGoalType.GUARD, priority=20, radius=8, speed=0.24),
            MobAiGoalSpec(MobAiGoalType.CHASE, priority=40, speed=0.30),
        ],
    )
    .phase(MobPhase(phase_id="phase_2", health_below=0.5))
    .phase_ai(
        "phase_2",
        MobAiSpec(
            engine=MobAiEngine.V2,
            profile=MobAiProfile.AGGRESSIVE,
            rage_health_ratio=0.65,
            goals=[MobAiGoalSpec(MobAiGoalType.CHASE, priority=10, speed=0.34)],
        ),
        merge_mode=MobAiPhaseMergeMode.PATCH,
    )
)
```

Mob AI V3 (module-based + utility selectors):

```python
from dungeonsreborn_builder import (
    Mob,
    EntityType,
    MobAiProfile,
    MobAiPerceptionSpec,
    MobAiCombatSpec,
    MobAiGroupSpec,
    MobAiEnvironmentSpec,
    MobAiSchedulerSpec,
    MobAiGoalSpec,
    MobAiGoalType,
)

mob = (
    Mob("mob_v3_guard")
    .mob_type(EntityType.ZOMBIE)
    .name("<aqua>V3 Guard</aqua>")
    .stats(health=32, damage=6, armor=4, speed=0.27)
    .ai_profile_v3(
        MobAiProfile.DEFENSIVE,
        perception=MobAiPerceptionSpec(aggro_radius=14.0, retarget_cooldown_ticks=20),
        combat=MobAiCombatSpec(flee_health_ratio=0.2, rage_health_ratio=0.65, chase_speed=0.30),
        group=MobAiGroupSpec(assist_radius=12.0, call_for_help_radius=10.0, focus_fire=True),
        environment=MobAiEnvironmentSpec(avoid_lava=True, avoid_powder_snow=True, open_doors=True),
        scheduler=MobAiSchedulerSpec(state_transition_cooldown_ticks=10),
    )
    .ai_selector(
        "engage_primary",
        base_score=100,
        actions=[MobAiGoalSpec(MobAiGoalType.CHASE, priority=20, speed=0.30)],
    )
)
```

## Example (Upgrades)

```python
from dungeonsreborn_builder.upgrades import UpgradeBuilder, UpgradesExporter, elemental_rune_pack

upgrade = (
    UpgradeBuilder("basic_rune_carica_i")
    .name("<gold>Carica I</gold>")
    .description("<gray>Aumenta la carica.</gray>")
    .compatibility("rune")
    .spell("upgrade_basic_rune_carica", activator="RIGHT_CLICK")
)

exporter = UpgradesExporter("./plugins/DungeonsReborn/effects/upgrades")
exporter.write_upgrade(upgrade)

# Batch preset
exporter.write_batch(elemental_rune_pack("fire"), "elemental_fire_runes.yml")
```

## Example (Loot)

```python
from dungeonsreborn_builder.loot import LootExporter, undead_basic_pool
from dungeonsreborn_builder import Material

exporter = LootExporter("./plugins/DungeonsReborn/loot")
exporter.write_pool(undead_basic_pool())
```

## Example (Shops)

```python
from dungeonsreborn_builder.shops import ShopExporter, upgrade_vendor

shops = [
    upgrade_vendor("vendor_runes", "<gold>Mercante delle Rune</gold>", ["basic_rune_carica_i"], min_level=1000),
]

exporter = ShopExporter("./plugins/DungeonsReborn")
exporter.write_shops(shops)
```

## Example (Quests)

```python
from dungeonsreborn_builder.quests import QuestExporter, kill_mob_quest

quests = [
    kill_mob_quest("quest_undead_t1_001", "<gray>Caccia Sepolcrale</gray>", "undead_t1_bonewalker", 5, 50),
]

exporter = QuestExporter("./plugins/DungeonsReborn/quests")
exporter.write_quests(quests, "undead_t1.yml")
```

## Example (Classes)

```python
from dungeonsreborn_builder.classes import ClassExporter, elemental_class_pack

exporter = ClassExporter("./plugins/DungeonsReborn")
exporter.write_classes(elemental_class_pack())
```

## Example (Items)

```python
from dungeonsreborn_builder.items import ItemBuilder, ItemExporter, bound_weapon
from dungeonsreborn_builder import Material

item = (
    ItemBuilder("ember_blade")
    .material(Material.DIAMOND_SWORD)
    .name("<red>Lama del Fuoco</red>")
    .lore("<gray>Brucia i nemici.</gray>")
    .custom_model_data(1201)
    .pdc_tag("dr:ember_blade")
    .bind("RIGHT_CLICK", ability="fire_pulse")
    .mana_bonus(max=10, regen=0.2)
)

exporter = ItemExporter("./plugins/DungeonsReborn/effects/items")
exporter.write_item(item)

# Shortcut preset
exporter.write_item(bound_weapon("training_sword", Material.IRON_SWORD, "basic_slash"))
```
