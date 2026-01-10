# Spells & Effects: Animation Toolkit (Design Doc)

This document describes the *next layer* on top of the current spells/effects engine: a toolkit that makes “complex animations” (like rotating arcs, spirals, expanding rings, trails, orbiting particles, staged bursts) practical to author without writing error-prone per-tick math in every ability.

The theme: **authors should describe motion/shape/easing**, not manage tick scheduling and position math manually.

## Principles

- **Declarative over imperative**: provide building blocks like `animate(duration, easing, emitShape(...))`.
- **Frame-based**: every animation is rendered relative to a `Frame` (caster eyes, entity, projectile, world point, etc.).
- **Layered**: complex visuals are built by composing multiple simple emitters.
- **Deterministic-ish**: consistent output for a given cast when using the cast RNG.
- **Budget-aware**: any emitter must respect global quality and per-viewer budgets.

## Core Concepts

### 1) Timeline / Animator

An animator runs a callback over time and provides a normalized parameter `t` in `[0..1]`.

Recommended API shape:
- `Actions.animate(durationTicks, periodTicks, easing, (ctx, t) -> { ... })`
- returns a handle / registers under the current `CastState` so it can be cancelled cleanly

Common patterns:
- expanding radius: `r = lerp(r0, r1, t)`
- rotation: `angle = lerp(0, 2π, t)`
- pulsing: `r = base + sin(t*freq)*amp`

### 2) Easing

Easing functions map linear time to “feel”:
- linear, ease-in/out, cubic, back, elastic (later)

Recommended API:
- `Easings.linear(t)`, `Easings.inOutCubic(t)`…

### 3) Frames

A `Frame` provides:
- `location(ctx)` (origin)
- `direction(ctx)` (orientation)

Minimum frames:
- cast origin (static)
- caster eyes (moves with player)
- entity (moves with entity)
- projectile (moves with projectile instance)
- world point (static location)

### 4) Emitters (Shape + Motion)

An emitter is:
- a shape sampler (ring/arc/disk/sphere/helix/line/bezier)
- plus a motion transform over time (rotate/orbit/translate/scale)
- plus rendering params (particle type, count, offsets, color/dust data)

Recommended split:
- `ParticleShapes` generate points
- `ParticleEngine` handles batching/filtering/budgets
- `ParticleTransforms` apply rotations/translations relative to frames (future)

### 5) Paths (for “spell VFX” feel)

Common needs:
- bezier curves (start → control → end)
- spline paths (multi-point)
- “boomerang” paths (out + return)
- orbit paths (circle around frame origin)

A simple path API should provide:
- `pointAt(t)` and `tangentAt(t)`

## Concrete “Hard Animation” Examples

### Rotating Arc Around Player (what you tested)

Instead of hand-rolled math inside the ability, the target API should look like:

```java
Actions.animate(60, 1, Easings.linear(), (ctx, t) -> {
  double rot = (Math.PI * 2) * t;
  Emit.arc()
    .frame(Frames.casterEyes())
    .radius(2.3)
    .spanDegrees(90)
    .rotateAroundY(rot)
    .particle(Particle.END_ROD)
    .render(ctx);
});
```

### Projectile With Attached Trail + Impact Burst

```java
Actions.projectile(p -> p
  .frameOut(frame -> Actions.particlesTrail(frame, 60, 1, Particle.END_ROD, 1, 0, 0))
  .onHit(hit -> Emit.sphereShell().at(hit.location()).render(...))
);
```

## Engineering Tasks (Next Iterations)

- [x] Add `CastState` with cancellation + per-cast variables + RNG
- [x] Add `Actions.animate(...)` (timeline helper) with optional easing
- [x] Provide a projectile frame output (`frameOut`) so effects can attach to moving projectiles
- [x] Add transform helpers (rotate/orbit/scale) for points relative to a frame
- [x] Add bezier/spline path sampling
- [x] Add standard “presets” (swirl, shockwave, orbit, beam charge-up)
- [x] Add debug visualizers for frames, hitboxes, and sampled points
