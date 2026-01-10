# Spells & Special Effects Engine (Design Spec)

Goal: build a reusable “effects + spells” library for Paper/Bukkit that lets developers author content (items/spells/skills) without constantly dealing with event quirks, tick scheduling, particle math, and version details.

This is intended to cover the “base” of the plugin *ExecutableItems* (item-bound abilities + configurable effects), but implemented as a clean internal library for this codebase.

## Scope

### MVP (Base Library)
- [x] A runtime engine to **cast** “actions” with **targets**, **conditions**, **cooldowns**, and **scheduling**
- [x] A **particle + sound** rendering layer (server-authoritative) with reusable math primitives
- [x] A **projectile/beam/AOE** toolkit (raycast, hitboxes, collision, filters)
- [x] A code-first **authoring API** to define spells/abilities + effect graphs
- [x] A developer-facing **Java API** for custom actions/targets/conditions
- [x] Debug tooling (visualization + logs) to quickly diagnose complex skills

### Later (Nice-to-Have)
- [ ] In-game editor / GUI authoring
- [x] Scriptable expressions (mini DSL) for advanced formulas
- [x] Item-based mana modifiers (max mana bonus + regen bonus while held/equipped)

## Non-Goals
- Not a full RPG framework (classes, quests, economies) unless needed by content
- Not replacing Minecraft combat; it orchestrates effects and integrates with existing damage/attributes
- Not a general “scripting engine” (keep it typed and safe first)

## Terminology

- **Ability**: a named definition (config) that can be executed (a spell/skill).
- **Cast**: one run of an ability (has state, timestamps, source, parameters).
- **Action**: a node that does something (spawn particles, damage, heal, apply potion, knockback, play sound, etc.).
- **Targeter**: selects entities/locations/blocks for an action (self, nearest, cone, ray, area, projectile hits).
- **Condition**: gates execution (has permission, has item, cooldown ready, line-of-sight, predicate checks).
- **Timeline**: scheduled steps over time (delays, repeats, “for duration”, animations).
- **Emitter**: particle generator (ring, helix, line, burst) that can be attached to a moving frame.

## Architecture Overview

### Modules (Logical)
- **core-runtime**
  - [x] `Engine` tick loop + task scheduler abstraction
  - [x] `CastContext` (caster, origin, direction, cast id, tick)
  - [x] `CastState` (cooldowns, per-cast variables, running timelines, cancellation)
  - [x] Registries: `ActionType`, `TargeterType`, `ConditionType`
- **math-geometry**
  - [x] `Vec3`/`Ray` helpers, lerp/slerp, noise
  - [x] Bezier curve helpers + sampling
  - [x] Shapes: sphere, cylinder, box, cone, capsule (for AOEs/hitboxes)
  - [x] Sampling: points on ring/arc/sphere/bezier with density controls (bezier implemented)
- **particles-audio**
  - [x] Particle emitters (basic line/ring helpers)
  - [x] Sound helpers (simple play-at-origin helper)
- **combat-effects**
  - [x] Damage/heal with cause attribution + PvP/PvE filters
  - [x] Basic damage/heal actions
  - [x] Knockback, pulls, velocity caps, i-frames/anti-multi-hit windows
  - [x] Basic knockback/pull actions
  - [x] Status effects (potion, immunity groups)
  - [x] Status effects (custom tags)
  - [x] Basic potion effect action
- **integration**
  - [x] Bukkit/Paper event binding (use item -> detect trigger -> cast)
  - [x] Compatibility shims for version-specific API differences

### Execution Model
- [x] Everything runs from the **main server thread** by default.
- [x] Optional async precomputation allowed only for pure math (no Bukkit access).
- [x] Engine supports two scheduling styles:
  - [x] **Tick timeline** (animate helper)
  - [x] **Real-time durations** (converted to ticks with drift handling)

## Authoring Model (Code-First)

The library does not load YAML itself. Abilities/actions/targeters/conditions are registered by developers (via Java) during plugin startup.

### Ability Definition (Java)
- [x] `id`, `name`, `description` (metadata)
- [x] registration hooks for `triggers`, `requirements`, `costs` (library-provided building blocks)
- [x] an executable definition (action graph / timeline / custom action code)

Example skeleton (conceptual):
```java
engine.registerAbility(
    AbilitySpec.builder("fire_bolt")
        .trigger(Trigger.rightClick())
        .cooldown(Duration.ofSeconds(2))
        .action(Actions.projectile(p -> p.speed(1.4).maxDistance(30)
            .onHit(Actions.damage(6))
            .onHit(Actions.particlesRing(Particle.FLAME, 1.2, 24))))
        .build()
);
```

### Item Binding (Optional Layer)
- [x] Match items via:
  - [x] custom `NamespacedKey` tags
  - [x] material + custom model data
  - [x] lore markers (discouraged, but supported)
- [x] Map: item → ability list (or “ability set”)
  - [x] Right-click abilities (“primary”)
  - [x] Left-click abilities (“secondary”)

## Core Runtime Contracts

### CastContext
- [x] `LivingEntity caster` (players supported)
- [x] `Location origin` + `Vector direction`
- [x] `ItemStack itemInHand` snapshot (optional)
- [x] `Map<String, Object> variables` (typed wrapper strongly preferred)
- [x] `Random rng` seeded per cast (replayable visuals if desired)
- [x] `EngineClock` timestamp (tick)

### Actions
Each action should:
  - [ ] be pure(ish): given inputs, produce deterministic results (except rng when used)
  - [x] support cancellation (cast-scoped scheduled handles)
  - [x] return either:
  - [x] immediate completion
  - [x] a running handle (timeline, projectile, beam, repeating emitter)

Minimum action set (MVP):
  - [x] `delay` (tick-based)
  - [x] `sequence` (same-tick composition)
  - [x] `repeat` (tick-based, count)
  - [x] `particles.*` (line, ring)
  - [x] `sound`
  - [x] `damage` (basic)
  - [x] `heal` (basic)
  - [x] `potion` (basic)
  - [x] `knockback` / `pull` (basic)
  - [x] `raycast` / `beam` (entity raycast + beam rendering helper)
  - [x] `projectile` (physics-lite with collision + onHit callback)
  - [x] `area` (shape-based target selection; sphere/nearest)

Additional actions (wishlist):
  - [x] `message` (send chat message to caster)
  - [x] `action_bar` (send action bar to caster)
  - [x] `title` (send title/subtitle)
  - [x] `teleport` (teleport caster/target to location/frame)
  - [x] `dash` (impulse in facing direction with caps + collision safety)
  - [x] `set_velocity` (add/set velocity with caps)
  - [x] `play_sound_at` (play sound at arbitrary frame/target)
  - [x] `particles_cone` (cone spray emitter)
  - [x] `particles_cylinder` (cylinder shell/fill emitter)
  - [x] `particles_box` (box outline/fill emitter)
  - [x] `particles_polygon` (N-gon ring/filled polygon)
  - [x] `random_choice` (weighted random branch between actions)
  - [x] `chance` (probabilistic gate for an action)
  - [x] `set_var` / `get_var` helpers (typed var convenience)
  - [x] `debug_log` (structured debug message with castId + tick)

Damage actions (mechanics-backed wishlist):
  - [x] `launch_wither_skull` (spawn a wither skull projectile towards a target; allow-miss flag)
  - [x] `launch_fireball` (spawn a ghast/fireball projectile towards a target; explosion rules configurable; allow-miss flag)
  - [x] `launch_dragon_fireball` (spawn dragon fireball towards a target; lingering damage; allow-miss flag)
  - [x] `arrow_volley` (spawn a burst of arrows/spectral arrows towards target(s) with spread; allow-miss flag)
  - [x] `throw_trident` (spawn a trident projectile towards a target; optional channeling; allow-miss flag)
  - [x] `splash_potion` (throw a splash potion at target location; e.g. harming/poison/weakness)
  - [x] `area_effect_cloud` (spawn AEC at target with potion effects; duration/radius growth)
  - [x] `evoker_fangs_line` (spawn evoker fangs in a line/path towards target; allow-miss flag)
  - [x] `strike_lightning` (strike lightning at target; damage/fire policy configurable; allow-miss flag)
  - [x] `explode_at` (server-side explosion at target location; damage vs block-break toggles; allow-miss flag)

Advanced damage mechanics:
  - [x] `damage_percent` (percent of target max health)
  - [x] `damage_true` (armor-bypassing damage)
  - [x] `damage_falloff` (distance-based scaling)
  - [x] `damage_crit` (crit chance + optional headshot modifier)
  - [x] `damage_lifesteal` (damage + heal caster by ratio)
  - [x] `damage_dot` (damage over time)
  - [x] `damage_chain` (chain lightning / bouncing hits)
  - [x] typed damage + resistances (elemental/physical profiles)
  - [x] reflect damage (temporary return damage buff)

### Targeters
Minimum targeters (MVP):
- [x] `self`
- [x] `look_ray` (block/entity ray)
- [x] `nearest` (radius + filters)
- [x] `area_sphere`
- [x] `area_cone`
- [x] `area_box`
- [x] `projectile_hit` (from projectile action; entity)

### Conditions
Minimum conditions (MVP):
- [x] `cooldown_ready`
- [x] `permission`
- [x] `sneaking`
- [x] `has_item_tag`
- [x] `line_of_sight` (as target condition/filter)
- [x] `health_above/below` (as target condition/filter)

## Particles & Math (Key “engine” value)

### Particle Abstraction
- [x] A higher-level API that avoids Bukkit’s per-particle spam pitfalls:
  - [x] batching per tick
  - [x] per-viewer filtering (distance, world, permissions)
  - [x] per-viewer budget/rate limiting
- [x] Supports “frames”:
  - [x] anchored to a static location
  - [x] attached to an entity bone-ish approximation (eyes/body)
  - [x] attached to a moving projectile

### Emitter Primitives
- [x] Point
- [x] Line
- [x] Arc
- [x] Ring (2D)
- [x] Circle, disk fill (disk helper)
- [x] Sphere shell + filled sphere
- [x] Helix along axis
- [x] Trail (samples path over time; frame-based)
- [x] Parametric curve (bezier)

### Determinism & Density
- [x] Define density as “points per meter” and “max points per tick” to prevent lag spikes.
- [x] Provide a quality setting (global multiplier) for scaling visuals under load.

## Collision, Hitboxes, Filters

- [x] Entity filtering:
  - [x] include/exclude players/mobs
  - [x] teams/parties/factions hook point
  - [x] ignore caster (built into raycast action)
- [x] Block collision modes:
  - [x] stop on solid (raycast clamps to first block hit)
  - [x] pass-through (raycast can ignore blocks)
  - [x] bounce/reflect (projectile block hits)
- [x] Hitbox shapes:
  - [x] ray thickness (capsule)
  - [x] sphere/cylinder for AOEs

## Cooldowns, Costs, and Safety

- [x] Cooldowns stored per-player, per-key (ability id recommended).
- [x] Costs implemented as a pluggable layer:
  - [x] durability cost
  - [x] consume item
  - [x] custom “mana” provider API
- [x] Mana regeneration (session-based)
  - [x] In-memory mana store (resets on relog)
  - [x] Regen loop (e.g. every 20t +5 mana, clamped)
- [x] All effects must be **server-authoritative** and avoid any “client trust”.
- [x] Anti-dupe principles:
  - [x] never modify inventory off-thread
  - [x] avoid re-entrancy during input/inventory events

## Debug Tooling (Must-Have)

- [x] `/effects debug on|off`
- [x] `/effects cast <ability> [player]`
- [x] `/effects list`
- [x] Visualizers:
  - [x] render hitboxes in particles
  - [x] show raycasts/impact points
  - [x] print action graph with timings
- [x] Structured logging with cast id + tick stamps (basic engine debug logging)

## Extensibility (Developer API)

- [x] Ability registry so plugins/modules can register abilities.
- [x] Action/targeter/condition registries for custom nodes.
- [x] Strongly typed parameter parsing with helpful config errors.
- [x] Simple Java usage:
  - [x] `engine.cast("fire_bolt", player)`
  - [x] `engine.registerAbility("fire_bolt", ctx -> { ... })`
  - [x] `engine.registerAction("my_action", MyAction::new)`

## Performance Targets

- [x] Per-tick budgeted particle emission (cap points per viewer per tick).
- [x] Avoid per-entity scans where possible (use spatial partition or short radius queries; start simple).
- [x] Minimize allocations in tick paths (reuse buffers).

## Suggested Milestones

1. **Core runtime + debug cast command**
2. **Particles engine + basic emitters**
3. **Raycast/beam + entity filtering**
4. **Projectile action + onHit pipeline**
5. **Cooldowns + item trigger integration**
6. **Stability pass + soak testing + profiling**
