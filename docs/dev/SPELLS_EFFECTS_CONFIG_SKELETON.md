# Spells & Effects: Config Skeleton (YAML-first)

This document proposes a **future** YAML format for defining abilities/effects outside Java code.

Important: the core engine stays **code-first**. A YAML loader/compiler should live in the plugin layer and output `AbilitySpec` + `Actions.*` graphs.

This document is **YAML-first**:
- Phase 1 focuses on defining **effects/abilities** that can be cast (e.g., via `/effects cast <id>`).
- Item-binding / triggers (ExecutableItems-style) are intentionally **later** (Phase 3), once the effect language is solid.

## Roadmap Checklist (Phases)

### Phase 0 — Authoring workflow decisions
- [x] Decide file layout:
  - [x] single `effects.yml` (all abilities)
  - [x] `effects/abilities/*.yml` (extra ability files)
  - [x] `effects/abilities/<id>/ability.yml` (folder per ability)
- [x] Decide merging policy for config vs code abilities:
  - [x] config can override code (same id)
  - [ ] code always wins, config ids must be unique
- [x] Decide reload story:
  - [ ] startup-only OR
  - [ ] `/effects reload` hot reload (recommended)

### Phase 1 — YAML schema v1 (minimal but useful)
- [x] Schema/versioning:
  - [x] `schemaVersion: 1`
- [x] clear error paths (file + yaml path)
- [x] Ability metadata:
  - [x] `id` (key), `name`, `description`
- [x] Requirements/costs/cooldowns:
  - [x] `requirements`: `sneaking`, `permission`, `has_item_tag` (+ `message`)
  - [x] `costs`: `mana`, `durability`, `consume_item`
  - [x] `cooldown`: `ticks`, optional `key`
- [ ] Minimal action graph:
  - [x] `sequence`, `delay`, `repeat_ticks`
  - [x] `message`, `action_bar`, `sound`
  - [x] `title`
  - [x] `animate`/`animate_realtime`
  - [x] `particles_*`: ring/line/arc/disk/sphere/helix/cone/cylinder/box/polygon
  - [x] `particles_*`: bezier/spline
  - [x] combat: `damage` (last-hit context)
  - [x] combat: `heal`, `potion`, `knockback`, `pull` (last-hit context)
  - [x] targeting actions: `projectile`
  - [x] targeting actions: `raycast_hit_entity`
  - [x] targeting actions: `for_each_target`
- [ ] Targeters:
  - [x] `self`, `look_ray`, `sphere`, `cone`, `box`, `cylinder`, `capsule_ray`, `projectile_hit`, `nearest`
- [ ] Validation (fail-fast):
  - [x] unknown `type` → hard error
  - [x] invalid enum/particle/sound → hard error
  - [x] missing required fields → hard error

### Phase 2 — Authoring ergonomics (less boilerplate)
- [ ] Good defaults (reduce YAML size):
  - [x] particle defaults (count/offset/extra)
  - [x] ray defaults (raySize/stopOnBlock/ignoreCaster)
- [ ] “Presets”:
  - [x] `preset_shockwave`, `preset_orbit`, `preset_swirl`
  - [x] `preset_beam_chargeup`
- [ ] Reuse without scripting:
  - [x] `macros:` (named action nodes)
  - [x] `include:` (reference another action node by id)
- [ ] Better error reporting:
  - [ ] show nearest valid enum values
  - [ ] show missing keys

### Phase 3 — Triggers & bindings (ExecutableItems-style)
- [x] Trigger schema:
  - [x] `item_bind`/`interact` (RIGHT_CLICK / LEFT_CLICK)
  - [x] `interact` options: cancel event, sneak-only, permission, custom binding id
- [x] Item matchers in YAML:
  - [x] `material`
  - [x] `custom_model_data`
  - [x] `pdc_tag` (NamespacedKey)
  - [x] `lore_contains` (discouraged but supported)
  - [x] `and` / `or`
- [x] Compile YAML triggers into `InteractBinding` registrations
- [x] Debugging:
  - [x] `/effects explain <right|left>` (shows item marker casts + binding checks)

### Phase 4 — Hot reload & safety
- [x] `/effects reload`:
  - [x] re-parse YAML
  - [x] unregister old YAML abilities (bindings later)
  - [x] register new ones
  - [x] show a summary + error list
- [x] Item file layout:
  - [x] folder: `plugins/DungeonsReborn/effects/items/`
  - [x] one file per item: `plugins/DungeonsReborn/effects/items/<itemId>.yml`
  - [x] filename is the item id (no separate `id:` field required)
  - [x] supports hot reload:
    - [x] add/remove/update items without restart
    - [x] binds one or more abilities to the item’s triggers (Phase 3 schema)
- [ ] Reload safety options:
  - [x] cancel running YAML casts on reload (optional): `options.cancelRunningOnReload: true`
  - [x] keep mana/cooldowns intact across reload
- [ ] Basic profiling hooks:
  - [x] `profile: true` per ability wraps root action with `Actions.timed(...)`

### Phase 5 — “ExecutableItems-like” flexibility (still YAML-first)
- [x] Variables/state:
  - [x] `set_var`, `inc_var`, `with_var` helpers
  - [x] explicit scopes: per-cast vs per-player vs per-entity
- [x] Branching:
  - [x] `chance` and `random_choice_weighted`
  - [x] `when`/`when_else` conditions
- [x] Formulas (restricted expressions) in leaf fields:
  - [x] `expr:` with whitelist functions (`min/max/clamp/lerp/rand/abs/floor/ceil/round`)
  - [x] whitelist variables (`mana`, `mana_max`, `caster_health`, `caster_max_health`, `t`, `distance`, `var:*`)
  - [x] strict sandbox (no reflection, no I/O)
  - [x] `animate`/`animate_realtime` inject `t` (eased 0..1) into cast vars for each tick
- [x] Subgraphs:
  - [x] `invoke_ability` with recursion guard / depth cap

### Phase 6 — Script layer (optional, last resort)
- [ ] If expressions aren’t enough: add a **sandboxed scripting layer** (still config-driven).

#### Phase 6.1 — Language + compiler choices
- [x] embed a tiny DSL (recommended)
- [x] embed a sandboxed JS/Lua (dropped)
- [x] decide file extension: `.es` (effects script) or `.yml` `script:` block
- [x] define a formal grammar and AST (parser + validator)
  - [x] parser implemented (AST implicit via action graph)
- [x] define a bytecode/IR for fast execution (dropped)
- [x] **self-contained executor**: no external runtime, no external scripting engine
- [x] **self-contained parser**: hand-rolled or internal parser (no heavy dependencies)
- [x] allow inline `script.source` or external `script.file` (relative to `effects/scripts/`)

#### Phase 6.2 — Capabilities & parity with YAML
- [x] define reusable helpers/functions (like “macros”, but programmable)
- [x] manipulate variables/state (per-cast/per-player/per-entity scopes)
- [x] branching + loops (if/else + repeat)
  - [x] weighted randomness (`choice weighted { ... }`)
  - [x] `chance` gate with optional `else`
  - [x] repeat loop (`repeat times=... every=... { ... }`)
- [x] call engine primitives (particles/sounds/projectiles/damage/potions/targeters)
  - [x] core primitives: `message`, `action_bar`, `sound`, `delay`, `repeat`, `on_tick`, `particles.*`
  - [x] combat/utility: `damage`, `heal`, `potion`, `knockback`, `pull`, `projectile`
  - [x] targeting: `raycast_hit_entity`, `for_each_target`
- [x] invoke existing abilities (`invoke` with `mode=subgraph|cast`)
- [x] build higher-level animations (orbits, swirls, shockwaves)
- [x] optionally implement custom target selection (filters, sorting by distance, etc.)
- [ ] map cleanly to existing YAML action nodes to keep parity

#### Phase 6.3 — Lifecycle hooks
- [x] `on_cast` (main thread)
- [x] `on_tick` (scheduled)
- [x] `on_hit` (projectile/raycast callbacks)
- [x] `on_end` / `finally` (cleanup / cancellation-safe)
- [x] `on_cancel` (explicit cleanup for interrupted casts)
- [x] `on_cooldown_fail` / `on_cost_fail` hooks (optional)
- [x] `on_finish` (fires when scheduled DSL tasks complete)

#### Phase 6.4 — Safety model (non-negotiables)
- [x] no filesystem/network access
- [x] no reflection / classloading
- [x] strict per-node caps (repeat/on_tick limits with debug warnings)
- [x] deterministic RNG via cast seed
- [x] per-tick DSL op budget (guarded execution)
- [x] only expose a whitelisted API surface (no raw Bukkit objects)
- [x] recursion depth cap + loop iteration caps
- [x] memory caps for arrays/maps (avoid runaway allocations)
- [x] forbid async/thread creation
- [x] explicit whitelist of math + string functions (no hidden globals)
- [x] prevent unbounded recursion via tail-call or hard depth checks
- [x] per-cast particle budget enforcement
- [x] **no external process/runtime** (fully in-process, fully controlled)

#### Phase 6.5 — Tooling, packaging, and ops
- [x] great error messages (line/column + macro stack context)
- [x] `/effects lint` and `/effects debug script on`
- [x] script versioning + compatibility strategy
- [x] hot reload with per-script error isolation (bad script doesn’t kill all)
- [x] script cache + warmup on reload (avoid first-cast spikes)
- [x] explain/trace mode to show evaluated nodes and timings
- [x] playground command to run a script in-place without registering
- [x] docs: function catalog + examples + migration notes
- [x] test harness: run script in headless mode + snapshot outputs
- [x] metrics: per-script execution time + error counts
- [x] packaging: optional `scripts/` folder with one file per script id
- [x] offline lint tool (no Bukkit runtime) for quick syntax checks
  - [x] CLI: `./gradlew dslLint --args "path/to/script.es"`
  - [x] Snapshot: `./gradlew dslSnapshot --args "path/to/script.es path/to/snapshot.txt"`

**Script Versioning (current)**
- Default version: `1`
- YAML: `script.version: 1`
- File header (optional): `#dsl-v1` or `#version:1` (commented directive at the top)
- Mismatch between header and YAML `version` fails fast.

**Script Tooling Commands**
- ` /effects lint [script]` (lint all or a single script)
- ` /effects debug script on|off`
- ` /effects debug script trace on|off`
- ` /effects script run <file>` (playground)
- ` /effects script stats` (metrics)

**Scripts Folder Layout**
- `plugins/DungeonsReborn/effects/scripts/`
- One `.es` file per script id is recommended, referenced via `script.file: my_script.es`

#### Phase 6 Examples (Draft)

**DSL Syntax Notes (current)**
- Variables: `foo = 3` (cast scope), `set player:mode = "A"`, `set var("entity:stacks") = 2`
- Expressions: `+ - * / % ^`, functions `min/max/clamp/lerp/rand/abs/floor/ceil/round`
- Variable lookup in expressions: `foo`, `var:player:mana`, `var:entity:stacks` (strings via `var("player:mode")`)
- Numeric attributes accept inline expressions (no `expr:` prefix): `radius=lerp(0.5, 2.5, t)`
- Conditions: `if foo > 2 { ... }`, `if var("player:mode") == "A" { ... }`
- `on_tick` injects `t` (0..1) into cast scope for each tick (easing applied)
- `animate` and `animate_realtime` mirror YAML actions (support `followCaster=true`)
- `with_var`: `with_var var("player:mode") = "A" { ... }`
- `inc_var`: `inc_var var("player:stacks") amount=1 default=0`
- `debug_var`: `debug_var var("player:mana") label="Mana"`
- Weighted choice: `choice weighted { 6: particles.ring ... 3: particles.line ... }`
- Handlers: `on_cast`, `on_cancel`, `on_end`/`finally`, `on_hit`
- Targeters: `for_each_target type=sphere radius=6 sort=nearest { ... }`
- Presets: `particles.orbit`, `particles.swirl`, `particles.shockwave`, `particles.beam_chargeup`
- Chance: `chance probability=0.25 { ... } else { ... }`
- Ability invoke: `invoke "ability_id" mode="subgraph" maxDepth=8`
- Title/debug: `title "Name" subtitle="..." fadeIn=10 stay=40 fadeOut=10`, `debug_log "msg"`
- Damage variants: `damage_percent percent=0.15`, `damage_true amount=6`, `damage_falloff amount=8 maxDistance=12 minMultiplier=0.2`
- Damage variants: `damage_crit amount=6 critChance=0.25 critMultiplier=1.5 headshotMultiplier=2.0`
- Damage variants: `damage_lifesteal amount=6 ratio=0.3`, `damage_dot amount=2 periodTicks=10 times=5`
- Damage variants: `damage_chain amount=4 radius=6 maxJumps=4 delayTicks=2 falloff=0.8 { ... }`
- Typed damage + resist: `damage_typed amount=6 type=FIRE`, `set_resistance type=FIRE multiplier=0.6 durationTicks=100`
- Resist controls: `add_resistance type=ICE delta=-0.2 durationTicks=40`, `clear_resistance type=ICE`
- Reflect: `set_reflect ratio=0.25 flat=0 type=PHYSICAL durationTicks=60`, `clear_reflect`
- Macros: `macro burst(radius=1.2, count=3) { ... }` then `call burst count=5`
- Failure hooks: `on_cost_fail { ... }`, `on_cooldown_fail { ... }`, `on_finish { ... }`
- Bezier/spline points use `p0_*`/`p1_*`/`p2_*`/`p3_*` attrs (e.g. `p0_forward`, `p1_up`, `p2_x`)

**Function Catalog (DSL)**
- Math: `min`, `max`, `clamp`, `lerp`, `rand`, `abs`, `floor`, `ceil`, `round`

**Migration Notes**
- v1 is the only supported DSL version right now.
- If a future v2 appears, the loader will require `script.version` or a header directive to prevent silent breakage.

**Example A — Basic on_cast sequence**
```
on_cast {
  sound "ENTITY_BLAZE_SHOOT" volume=0.7 pitch=1.6
  particles.ring particle=CRIT radius=1.2 points=24 count=1
}
```

**Example B — Variables + branching**
```
on_cast {
  if var("player:mode") == "A" {
    set var("player:mode") = "B"
    message "<green>Mode A</green>"
  } else {
    set var("player:mode") = "A"
    message "<aqua>Mode B</aqua>"
  }
}
```

**Example C — Timed animation with easing**
```
on_tick every=1 ticks=40 easing="in_out_cubic" {
  r = lerp(0.5, 3.0, t)
  particles.ring particle=END_ROD radius=r points=32 count=1
}
```

**Example D — Targeting + per-entity state**
```
on_cast {
  targets = target.sphere radius=6 filter="mobs" max=4
  for each target in targets {
    set var("entity:stacks") = (var("entity:stacks") or 0) + 1
    damage target amount=3
    particles.point at=target particle=CRIT count=2
  }
}
```

**Example E — Projectile hook**
```
on_cast {
  projectile speed=1.3 max=24 hitRadius=0.35 {
    on_hit {
      damage target amount=6
      particles.ring at=hit particle=CRIT radius=1.1 points=24 count=2
    }
  }
}
```

**Example F — Cooldown gate + fallback message**
```
on_cast {
  if cooldown_ready key="rift" ticks=80 {
    message "<green>Rift ready.</green>"
  } else {
    message "<gray>Rift is cooling down.</gray>"
  }
}
```

**Example G — Weighted randomness**
```
on_cast {
  choice weighted {
    6: particles.ring particle=END_ROD radius=1.6 points=28 count=1
    3: particles.sphere_shell particle=ELECTRIC_SPARK radius=1.4 points=64 count=1
    1: particles.swirl particle=SOUL_FIRE_FLAME radius=1.2 height=2.0 points=22 count=1
  }
}
```

**Example H — Multi-step combo with delayed follow-up**
```
on_cast {
  sound "ENTITY_PLAYER_ATTACK_SWEEP" volume=0.7 pitch=1.2
  delay ticks=6 {
    particles.arc particle=SWEEP_ATTACK radius=1.8 angleDegrees=120 points=24 count=1
  }
  delay ticks=14 {
    particles.ring particle=CRIT radius=2.4 points=32 count=1
  }
}
```

**Example I — Arc beam that ramps up with time**
```
on_tick every=1 ticks=30 easing="out_quad" {
  len = lerp(2.0, 10.0, t)
  particles.line particle=END_ROD length=len step=0.3 count=1
}
```

**Example J — Nested invocation**
```
on_cast {
  invoke "rift_open"
  delay ticks=20 { invoke "rift_burst" }
}
```

**Example K — Per-player toggle with var scopes**
```
on_cast {
  if var("player:mode") == "A" {
    set var("player:mode") = "B"
    action_bar "<aqua>Mode B</aqua>"
  } else {
    set var("player:mode") = "A"
    action_bar "<green>Mode A</green>"
  }
}
```

**Example L — Multi-target chain with falloff**
```
on_cast {
  targets = target.sphere radius=8 filter="mobs" max=6
  i = 0
  for each target in targets {
    damage target amount=max(1, 6 - i)
    particles.point at=target particle=CRIT count=1
    i = i + 1
  }
}
```

**Example M — Conditional based on distance**
```
on_cast {
  target = target.look_ray maxDistance=20 raySize=0.35
  if distance_to target > 8 {
    particles.line particle=END_ROD length=8 step=0.25 count=1
  } else {
    particles.ring particle=CRIT radius=1.2 points=24 count=1
  }
}
```

**Example N — Structured timelines (phases)**
```
on_cast {
  phase "charge" {
    on_tick every=1 ticks=30 easing="in_out_cubic" {
      r = lerp(0.5, 2.5, t)
      particles.sphere_shell particle=END_ROD radius=r points=90 count=1
    }
  }
  phase "release" {
    on_tick every=1 ticks=12 easing="out_quad" {
      r = lerp(2.0, 6.0, t)
      particles.ring particle=SWEEP_ATTACK radius=r points=72 count=1
    }
  }
}
```

**Example O — Target filters + custom selection**
```
on_cast {
  targets = target.sphere radius=10 filter="mobs" sort="nearest" max=3
  for each target in targets {
    potion target effect=SLOW duration=60 amplifier=1
    particles.point at=target particle=ELECTRIC_SPARK count=2
  }
}
```

#### Phase 6 Data Shape (YAML-hosted)
```yml
script:
  language: "dsl-v1"
  source: |-
    on_cast { sound "ENTITY_BLAZE_SHOOT" volume=0.7 pitch=1.6 }
```

```yml
script:
  language: "dsl-v1"
  file: "my_spell.es"   # resolved relative to plugins/DungeonsReborn/effects/scripts/
```

## Design Goals

- Human-friendly authoring for content developers.
- YAML maps 1:1 to engine concepts:
  - Ability metadata, triggers, requirements, costs, cooldown.
  - A typed action graph (sequence/delay/repeat/animate/forEach/particles/damage/etc).
- Safe evaluation:
  - Prefer typed parameters (numbers/booleans/enums) over arbitrary scripting.
  - Add expressions later, only in leaf fields.

## YAML Schema (Draft)

### Top-level skeleton

```yml
schemaVersion: 1

abilities:
  fire_bolt:
    name: "Fire Bolt"
    description: "Shoot a fiery projectile."

    # Note: triggers/bindings are Phase 3. For Phase 1, you cast via `/effects cast <id>`.
    # triggers:
    #   - type: item_bind
    #     click: RIGHT_CLICK   # RIGHT_CLICK / LEFT_CLICK

    requirements:
      - type: sneaking
        message: "&cSneak to cast."
      - type: permission
        permission: "dungeonsreborn.spells.firebolt"

    costs:
      - type: mana
        amount: 12

    cooldown:
      ticks: 40
      key: "fire_bolt" # optional override

    action:
      type: sequence
      actions:
        - type: sound
          sound: ENTITY_BLAZE_SHOOT
          volume: 0.7
          pitch: 1.6

        - type: projectile
          speedPerTick: 1.35
          maxDistance: 30
          hitRadius: 0.35
          blockCollision: STOP   # STOP / PASS_THROUGH / BOUNCE
          bounces:
            max: 0
            restitution: 0.9
          trail:
            particle: END_ROD
            count: 1
            offset: 0.0
            extra: 0.0
          onHit:
            type: sequence
            actions:
              - type: particles_ring
                particle: CRIT
                radius: 1.1
                points: 24
                count: 2
              - type: damage
                amount: 6
                policy: hostile_default
```

### Item file skeleton (Phase 4)

Each item is one file in `plugins/DungeonsReborn/effects/items/<itemId>.yml`.

```yml
schemaVersion: 1

# Item matcher (Phase 3 matcher schema)
item:
  type: and
  matchers:
    - type: material
      material: BLAZE_ROD
    - type: custom_model_data
      value: 101

# Bind one or more abilities to this item
bindings:
  - type: interact
    click: RIGHT_CLICK
    ability: debug_spark
    cancelEvent: true
  - type: interact
    click: LEFT_CLICK
    ability: debug_beam
    requireSneaking: true
```

### Action nodes (pattern)

Every action node uses:
- `type`: action identifier
- action-specific fields
- optional nested nodes (e.g., `then`, `actions`, `onHit`, `otherwise`)

Examples:

```yml
- type: delay
  ticks: 10
  then:
    type: particles_ring
    particle: END_ROD
    radius: 2.0
    points: 32
    count: 1

- type: chance
  probability: 0.25
  then: { type: message, text: "&aLucky!" }
  otherwise: { type: message, text: "&7Nope." }
```

### Expressions (`expr:`)

Most numeric leaf fields accept a restricted expression syntax:

```yml
- type: particles_ring
  particle: END_ROD
  radius: "expr: 1.5 + var:power"
  points: { expr: "clamp(24 + var:bonus, 8, 64)" }
```

## Mapping to Java Engine

Suggested pipeline:

1. Load YAML in plugin layer.
2. Validate schema + normalize ids.
3. Compile nodes into Java lambdas:
   - `type: particles_ring` → `Actions.particlesRing(...)`
   - `type: sequence` → `Actions.sequence(...)`
   - `type: projectile` → `Actions.projectile(builder -> ...)`
4. Register as `AbilitySpec` (and optionally bindings) on startup or reload.
