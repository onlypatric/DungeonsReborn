"""Archetype library (36 entries) built from reusable clips/modifiers/anchors."""

from __future__ import annotations

from types import MappingProxyType
from typing import Mapping, Sequence

from .model import (
    ArchetypeSpec,
    BudgetSpec,
    ClipInstance,
    ModifierInstance,
    PhaseSpec,
    TimelineSpec,
    VfxAnchorId,
    VfxArchetypeId,
    VfxArchetypeLike,
    VfxBudgetTier,
    VfxBucket,
    VfxClipId,
    VfxLod,
    VfxModifierId,
    VfxPhaseKind,
    VfxReadiness,
)


def _ci(clip_id: VfxClipId, **params) -> ClipInstance:
    return ClipInstance(clip_id=clip_id, params=params)


def _mi(modifier_id: VfxModifierId, **params) -> ModifierInstance:
    return ModifierInstance(modifier_id=modifier_id, params=params)


def _phase(kind: VfxPhaseKind, clips: Sequence[ClipInstance], *, modifiers: Sequence[ModifierInstance] = (), duration: int = 20) -> PhaseSpec:
    return PhaseSpec(kind=kind, clips=list(clips), modifiers=list(modifiers), duration_ticks=duration)


def _timeline(
    *,
    anticipation: Sequence[ClipInstance] = (),
    activation: Sequence[ClipInstance] = (),
    decay: Sequence[ClipInstance] = (),
    residual: Sequence[ClipInstance] = (),
    anticipation_mods: Sequence[ModifierInstance] = (),
    activation_mods: Sequence[ModifierInstance] = (),
    decay_mods: Sequence[ModifierInstance] = (),
    residual_mods: Sequence[ModifierInstance] = (),
    anticipation_ticks: int = 8,
    activation_ticks: int = 14,
    decay_ticks: int = 10,
    residual_ticks: int = 8,
) -> TimelineSpec:
    return TimelineSpec(
        anticipation=_phase(VfxPhaseKind.ANTICIPATION, anticipation, modifiers=anticipation_mods, duration=anticipation_ticks)
        if anticipation
        else None,
        activation=_phase(VfxPhaseKind.ACTIVATION, activation, modifiers=activation_mods, duration=activation_ticks)
        if activation
        else None,
        decay=_phase(VfxPhaseKind.DECAY, decay, modifiers=decay_mods, duration=decay_ticks)
        if decay
        else None,
        residual=_phase(VfxPhaseKind.RESIDUAL, residual, modifiers=residual_mods, duration=residual_ticks)
        if residual
        else None,
    )


def _a(
    archetype_id: VfxArchetypeId,
    bucket: VfxBucket,
    intent: str,
    anchor: VfxAnchorId,
    timeline: TimelineSpec,
    budget_tier: VfxBudgetTier,
    *,
    modifiers: Sequence[ModifierInstance] = (),
    readiness: VfxReadiness = VfxReadiness.READY,
) -> ArchetypeSpec:
    return ArchetypeSpec(
        archetype_id=archetype_id,
        bucket=bucket,
        intent=intent,
        timeline=timeline,
        anchor=anchor,
        budget=BudgetSpec(tier=budget_tier, lod=VfxLod.MEDIUM, max_layers=3, fallback_lod=VfxLod.LOW),
        modifiers=list(modifiers),
        readiness=readiness,
    )


_ARCHETYPES: dict[str, ArchetypeSpec] = {
    # Impact Dynamics
    VfxArchetypeId.IMPACT_CRISP_STRIKE.value: _a(
        VfxArchetypeId.IMPACT_CRISP_STRIKE,
        VfxBucket.IMPACT,
        "Hit netto e leggibile",
        VfxAnchorId.LAST_ENTITY,
        _timeline(
            anticipation=[_ci(VfxClipId.ARC_SLASH, radius=1.2, angle_degrees=65.0)],
            activation=[_ci(VfxClipId.POINT_FLASH, count=2)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=1.0)],
            decay_mods=[_mi(VfxModifierId.FADE_OUT)],
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.IMPACT_HEAVY_BURST.value: _a(
        VfxArchetypeId.IMPACT_HEAVY_BURST,
        VfxBucket.IMPACT,
        "Impatto pesante con plume",
        VfxAnchorId.SEGMENT_END,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_DUAL, radius_inner=0.8, radius_outer=1.3)],
            activation=[_ci(VfxClipId.SHELL_POP, radius=1.9, points=52), _ci(VfxClipId.PHYSICS_PLUME, count=2)],
            decay=[_ci(VfxClipId.POINT_FLASH, count=2)],
            activation_mods=[_mi(VfxModifierId.SCALE_RAMP), _mi(VfxModifierId.DRAG_BOOST)],
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.IMPACT_SHOCK_CORE.value: _a(
        VfxArchetypeId.IMPACT_SHOCK_CORE,
        VfxBucket.IMPACT,
        "Core energetico ad alto contrasto",
        VfxAnchorId.TARGET_LOCK,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_PULSE, radius=1.0)],
            activation=[_ci(VfxClipId.IMPACT_CORE, radius=1.4), _ci(VfxClipId.FILL_BURST, radius=1.5, points=60)],
            decay=[_ci(VfxClipId.SHELL_POP, radius=1.1)],
            activation_mods=[_mi(VfxModifierId.PULSE_AMP)],
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.IMPACT_FRACTURE_LINE.value: _a(
        VfxArchetypeId.IMPACT_FRACTURE_LINE,
        VfxBucket.IMPACT,
        "Frattura lineare",
        VfxAnchorId.SEGMENT_END,
        _timeline(
            anticipation=[_ci(VfxClipId.LINE_BEAM, length=2.2)],
            activation=[_ci(VfxClipId.PHYSICS_CRACK, length=2.8), _ci(VfxClipId.POINTS_GLYPH)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=1.25)],
            activation_mods=[_mi(VfxModifierId.NOISE_POS)],
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.IMPACT_GEOM_STAMP.value: _a(
        VfxArchetypeId.IMPACT_GEOM_STAMP,
        VfxBucket.IMPACT,
        "Stamp runico geometrico",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.POLYGON_SIGIL, sides=6)],
            activation=[_ci(VfxClipId.DISK_GROUND, radius=1.5)],
            decay=[_ci(VfxClipId.POINT_FLASH, count=1)],
            activation_mods=[_mi(VfxModifierId.GRADIENT_SHIFT, start_color="#e9f58a", end_color="#d6801f")],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.IMPACT_ECHO_DOUBLE.value: _a(
        VfxArchetypeId.IMPACT_ECHO_DOUBLE,
        VfxBucket.IMPACT,
        "Doppio eco dopo impatto",
        VfxAnchorId.LAST_ENTITY,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_PULSE, radius=0.9)],
            activation=[_ci(VfxClipId.POINT_FLASH, count=2)],
            decay=[_ci(VfxClipId.RING_DUAL, radius_inner=0.9, radius_outer=1.45)],
            decay_mods=[_mi(VfxModifierId.PHASE_GATE, allowed=["decay", "residual"])],
        ),
        VfxBudgetTier.LOW,
    ),

    # Projectile Motion Language
    VfxArchetypeId.PROJ_CLEAN_BEAM.value: _a(
        VfxArchetypeId.PROJ_CLEAN_BEAM,
        VfxBucket.PROJECTILE,
        "Beam proiettile pulito",
        VfxAnchorId.SEGMENT_START,
        _timeline(
            activation=[_ci(VfxClipId.LINE_BEAM, length=3.3), _ci(VfxClipId.POINT_FLASH)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=0.9)],
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.PROJ_CURVED_SHOT.value: _a(
        VfxArchetypeId.PROJ_CURVED_SHOT,
        VfxBucket.PROJECTILE,
        "Shot curvo guidato",
        VfxAnchorId.SEGMENT_START,
        _timeline(
            anticipation=[_ci(VfxClipId.BEZIER_TRAIL)],
            activation=[_ci(VfxClipId.SHELL_POP, radius=1.15)],
            activation_mods=[_mi(VfxModifierId.SPEED_RAMP)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.PROJ_SPIRAL_DART.value: _a(
        VfxArchetypeId.PROJ_SPIRAL_DART,
        VfxBucket.PROJECTILE,
        "Dardo a spirale",
        VfxAnchorId.SEGMENT_START,
        _timeline(
            activation=[_ci(VfxClipId.HELIX_CHANNEL, turns=2.0, length=2.4), _ci(VfxClipId.POINT_FLASH, count=2)],
            activation_mods=[_mi(VfxModifierId.ROTATE_Y)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.PROJ_TETHER_TRACE.value: _a(
        VfxArchetypeId.PROJ_TETHER_TRACE,
        VfxBucket.PROJECTILE,
        "Traccia tether",
        VfxAnchorId.CHAIN_LINKS,
        _timeline(
            activation=[_ci(VfxClipId.POLYLINE_TETHER, length=3.0)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.0)],
            activation_mods=[_mi(VfxModifierId.GRADIENT_SHIFT, start_color="#99d8ff", end_color="#4d62ff")],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.PROJ_ASH_TRAIL.value: _a(
        VfxArchetypeId.PROJ_ASH_TRAIL,
        VfxBucket.PROJECTILE,
        "Trail ceneri",
        VfxAnchorId.SEGMENT_START,
        _timeline(
            activation=[_ci(VfxClipId.LINE_BEAM, length=2.8), _ci(VfxClipId.PHYSICS_PLUME, count=2)],
            decay=[_ci(VfxClipId.SHELL_POP, radius=0.95)],
            activation_mods=[_mi(VfxModifierId.DRAG_BOOST)],
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.PROJ_FRAG_PATH.value: _a(
        VfxArchetypeId.PROJ_FRAG_PATH,
        VfxBucket.PROJECTILE,
        "Path frammentato",
        VfxAnchorId.SEGMENT_START,
        _timeline(
            activation=[_ci(VfxClipId.PHYSICS_SHARDS, count=2), _ci(VfxClipId.SPLINE_RAIL)],
            activation_mods=[_mi(VfxModifierId.NOISE_TIME)],
            decay=[_ci(VfxClipId.POINT_FLASH)],
        ),
        VfxBudgetTier.HIGH,
    ),

    # Zone Presence
    VfxArchetypeId.ZONE_CLEAN_RING.value: _a(
        VfxArchetypeId.ZONE_CLEAN_RING,
        VfxBucket.ZONE,
        "Perimetro zona leggibile",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.ZONE_OUTLINE, radius=1.8)],
            activation=[_ci(VfxClipId.RING_DUAL, radius_inner=1.4, radius_outer=2.0)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.7)],
            residual_mods=[_mi(VfxModifierId.PULSE_AMP)],
            residual_ticks=14,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.ZONE_GROUND_FIELD.value: _a(
        VfxArchetypeId.ZONE_GROUND_FIELD,
        VfxBucket.ZONE,
        "Campo a terra persistente",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.POLYGON_SIGIL, radius=1.2)],
            activation=[_ci(VfxClipId.DISK_GROUND, radius=1.9), _ci(VfxClipId.POINTS_GLYPH)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.9)],
            residual_mods=[_mi(VfxModifierId.FADE_OUT)],
            residual_ticks=16,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.ZONE_PILLAR_LOCK.value: _a(
        VfxArchetypeId.ZONE_PILLAR_LOCK,
        VfxBucket.ZONE,
        "Pilastro area lock",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            activation=[_ci(VfxClipId.CYLINDER_PILLAR, height=3.1), _ci(VfxClipId.POINT_FLASH, count=2)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.3)],
            residual_ticks=12,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.ZONE_CUBIC_GATE.value: _a(
        VfxArchetypeId.ZONE_CUBIC_GATE,
        VfxBucket.ZONE,
        "Gate cubico",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.BOX_FIELD, x_radius=1.2, y_radius=0.8, z_radius=1.2)],
            activation=[_ci(VfxClipId.LINE_BEAM, length=2.0)],
            residual=[_ci(VfxClipId.ZONE_OUTLINE, radius=1.3)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.ZONE_DOME_WARNING.value: _a(
        VfxArchetypeId.ZONE_DOME_WARNING,
        VfxBucket.ZONE,
        "Cupola warning",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_PULSE, radius=1.4)],
            activation=[_ci(VfxClipId.SHELL_POP, radius=2.2, points=64)],
            decay=[_ci(VfxClipId.DISK_GROUND, radius=1.4)],
            activation_mods=[_mi(VfxModifierId.SCALE_RAMP)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.ZONE_FRACTURED_HAZARD.value: _a(
        VfxArchetypeId.ZONE_FRACTURED_HAZARD,
        VfxBucket.ZONE,
        "Hazard fratturato",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.ZONE_OUTLINE, radius=1.5)],
            activation=[_ci(VfxClipId.PHYSICS_FRACTURE), _ci(VfxClipId.DISK_GROUND, radius=1.8)],
            residual=[_ci(VfxClipId.POINT_FLASH)],
            activation_mods=[_mi(VfxModifierId.NOISE_POS)],
        ),
        VfxBudgetTier.HIGH,
    ),

    # Support/Aura
    VfxArchetypeId.AURA_PERSONAL_SOFT.value: _a(
        VfxArchetypeId.AURA_PERSONAL_SOFT,
        VfxBucket.AURA,
        "Aura personale soft",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_PULSE, radius=1.0)],
            activation=[_ci(VfxClipId.POINT_FLASH, count=2)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.2)],
            residual_ticks=12,
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.AURA_HALO_GUARD.value: _a(
        VfxArchetypeId.AURA_HALO_GUARD,
        VfxBucket.AURA,
        "Halo difensivo",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_DUAL, radius_inner=0.9, radius_outer=1.35)],
            activation=[_ci(VfxClipId.SHELL_POP, radius=1.4)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.4)],
            residual_mods=[_mi(VfxModifierId.PULSE_AMP)],
            residual_ticks=14,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.AURA_CHANNEL_HELIX.value: _a(
        VfxArchetypeId.AURA_CHANNEL_HELIX,
        VfxBucket.AURA,
        "Canale helix",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.HELIX_CHANNEL, length=2.4, turns=2.7), _ci(VfxClipId.POINT_FLASH)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.1)],
            activation_mods=[_mi(VfxModifierId.ANCHOR_LAG)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.AURA_SIGIL_SUPPORT.value: _a(
        VfxArchetypeId.AURA_SIGIL_SUPPORT,
        VfxBucket.AURA,
        "Sigillo support",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            anticipation=[_ci(VfxClipId.POLYGON_SIGIL, radius=1.0)],
            activation=[_ci(VfxClipId.POINTS_GLYPH)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.0)],
            activation_mods=[_mi(VfxModifierId.PALETTE_SWAP, palette="support")],
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.AURA_GROUP_BEACON.value: _a(
        VfxArchetypeId.AURA_GROUP_BEACON,
        VfxBucket.AURA,
        "Beacon di gruppo",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_PULSE, radius=1.4)],
            activation=[_ci(VfxClipId.CYLINDER_PILLAR, height=3.4), _ci(VfxClipId.SHELL_POP, radius=1.2)],
            residual=[_ci(VfxClipId.RING_DUAL, radius_inner=1.2, radius_outer=1.8)],
            activation_mods=[_mi(VfxModifierId.DENSITY_RAMP)],
            residual_ticks=15,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.AURA_STABLE_FIELD.value: _a(
        VfxArchetypeId.AURA_STABLE_FIELD,
        VfxBucket.AURA,
        "Campo stabile anti-caos",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            activation=[_ci(VfxClipId.DISK_GROUND, radius=1.6), _ci(VfxClipId.POLYLINE_TETHER, length=2.0)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.5)],
            activation_mods=[_mi(VfxModifierId.NOISE_TIME, jitter=0.05)],
            residual_ticks=14,
        ),
        VfxBudgetTier.MEDIUM,
    ),

    # Mobility Signatures
    VfxArchetypeId.MOVE_DASH_CLEAN.value: _a(
        VfxArchetypeId.MOVE_DASH_CLEAN,
        VfxBucket.MOBILITY,
        "Dash leggibile",
        VfxAnchorId.CASTER_FORWARD,
        _timeline(
            activation=[_ci(VfxClipId.POINT_FLASH), _ci(VfxClipId.LINE_BEAM, length=2.4)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=0.8)],
            decay_mods=[_mi(VfxModifierId.FADE_OUT)],
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.MOVE_BLINK_ARCANE.value: _a(
        VfxArchetypeId.MOVE_BLINK_ARCANE,
        VfxBucket.MOBILITY,
        "Blink arcano",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            anticipation=[_ci(VfxClipId.POINTS_GLYPH)],
            activation=[_ci(VfxClipId.SHELL_POP, radius=1.3)],
            decay=[_ci(VfxClipId.POINT_FLASH)],
            activation_mods=[_mi(VfxModifierId.PALETTE_SWAP, palette="arcane")],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MOVE_PATH_PREVIEW.value: _a(
        VfxArchetypeId.MOVE_PATH_PREVIEW,
        VfxBucket.MOBILITY,
        "Preview percorso",
        VfxAnchorId.GROUND_PATH,
        _timeline(
            anticipation=[_ci(VfxClipId.SPLINE_RAIL)],
            activation=[_ci(VfxClipId.POINT_FLASH)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=0.9)],
            activation_mods=[_mi(VfxModifierId.PHASE_GATE, allowed=["activation"])],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MOVE_JUMP_WAVE.value: _a(
        VfxArchetypeId.MOVE_JUMP_WAVE,
        VfxBucket.MOBILITY,
        "Wave atterraggio",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            activation=[_ci(VfxClipId.DISK_GROUND, radius=1.3), _ci(VfxClipId.RING_PULSE, radius=1.2)],
            decay=[_ci(VfxClipId.POINT_FLASH)],
            activation_mods=[_mi(VfxModifierId.SCALE_RAMP, factor=1.1)],
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.MOVE_EVASIVE_SPIRAL.value: _a(
        VfxArchetypeId.MOVE_EVASIVE_SPIRAL,
        VfxBucket.MOBILITY,
        "Spirale evasiva",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.HELIX_CHANNEL, turns=2.2, length=2.6), _ci(VfxClipId.LINE_BEAM, length=1.8)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=1.0)],
            activation_mods=[_mi(VfxModifierId.ROTATE_FULL)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MOVE_HEAVY_STRIDE.value: _a(
        VfxArchetypeId.MOVE_HEAVY_STRIDE,
        VfxBucket.MOBILITY,
        "Passo pesante",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            activation=[_ci(VfxClipId.PHYSICS_SHARDS), _ci(VfxClipId.DISK_GROUND, radius=1.1)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=1.0)],
            activation_mods=[_mi(VfxModifierId.DRAG_BOOST)],
        ),
        VfxBudgetTier.HIGH,
    ),

    # Boss Telegraph Grammar
    VfxArchetypeId.BOSS_CONE_THREAT.value: _a(
        VfxArchetypeId.BOSS_CONE_THREAT,
        VfxBucket.BOSS,
        "Telegraph cono boss",
        VfxAnchorId.CASTER_FORWARD,
        _timeline(
            anticipation=[_ci(VfxClipId.CONE_WARNING, angle_degrees=70.0, length=4.0)],
            activation=[_ci(VfxClipId.ARC_SLASH, radius=2.2, angle_degrees=95.0)],
            decay=[_ci(VfxClipId.RING_PULSE, radius=1.4)],
            anticipation_mods=[_mi(VfxModifierId.FADE_IN)],
            anticipation_ticks=16,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.BOSS_ZONE_LOCK.value: _a(
        VfxArchetypeId.BOSS_ZONE_LOCK,
        VfxBucket.BOSS,
        "Lock area boss",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.BOX_FIELD, x_radius=2.0, y_radius=1.0, z_radius=2.0)],
            activation=[_ci(VfxClipId.ZONE_OUTLINE, radius=2.0)],
            residual=[_ci(VfxClipId.RING_DUAL, radius_inner=1.8, radius_outer=2.2)],
            residual_ticks=16,
            anticipation_ticks=16,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.BOSS_RUNE_CHARGE.value: _a(
        VfxArchetypeId.BOSS_RUNE_CHARGE,
        VfxBucket.BOSS,
        "Charge runico",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.POLYGON_SIGIL, radius=1.8, sides=8)],
            activation=[_ci(VfxClipId.FILL_BURST, radius=1.9, points=72)],
            decay=[_ci(VfxClipId.SHELL_POP, radius=1.2)],
            activation_mods=[_mi(VfxModifierId.GRADIENT_SHIFT, start_color="#f6d27a", end_color="#c15120")],
            anticipation_ticks=18,
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.BOSS_IMPACT_CALL.value: _a(
        VfxArchetypeId.BOSS_IMPACT_CALL,
        VfxBucket.BOSS,
        "Call impatto boss",
        VfxAnchorId.SEGMENT_END,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_DUAL, radius_inner=1.2, radius_outer=1.9)],
            activation=[_ci(VfxClipId.SHELL_POP, radius=2.0, points=70), _ci(VfxClipId.PHYSICS_PLUME, count=3)],
            decay=[_ci(VfxClipId.POINT_FLASH, count=2)],
            activation_mods=[_mi(VfxModifierId.SCALE_RAMP), _mi(VfxModifierId.DENSITY_RAMP)],
            anticipation_ticks=14,
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.BOSS_PILLAR_SWEEP.value: _a(
        VfxArchetypeId.BOSS_PILLAR_SWEEP,
        VfxBucket.BOSS,
        "Sweep pilastri",
        VfxAnchorId.CHAIN_LINKS,
        _timeline(
            anticipation=[_ci(VfxClipId.POLYLINE_TETHER, length=3.0)],
            activation=[_ci(VfxClipId.CYLINDER_PILLAR, height=3.2), _ci(VfxClipId.POLYLINE_TETHER, length=3.2)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.5)],
            activation_mods=[_mi(VfxModifierId.SPEED_RAMP)],
            anticipation_ticks=14,
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.BOSS_FRACTURE_MESH.value: _a(
        VfxArchetypeId.BOSS_FRACTURE_MESH,
        VfxBucket.BOSS,
        "Frattura mesh finale",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.MESH_WARD)],
            activation=[_ci(VfxClipId.PHYSICS_FRACTURE), _ci(VfxClipId.FILL_BURST, radius=1.5)],
            decay=[_ci(VfxClipId.POINT_FLASH, count=2)],
            activation_mods=[_mi(VfxModifierId.NOISE_POS), _mi(VfxModifierId.NOISE_TIME)],
            anticipation_ticks=16,
        ),
        VfxBudgetTier.HIGH,
        readiness=VfxReadiness.NEEDS_TUNING,
    ),
    # Magic Offense
    VfxArchetypeId.MAGIC_OFFENSE_ARCANE_LANCE.value: _a(
        VfxArchetypeId.MAGIC_OFFENSE_ARCANE_LANCE,
        VfxBucket.MAGIC_OFFENSE,
        "Lancia arcana lineare ad alta leggibilita",
        VfxAnchorId.CASTER_HAND_MAIN,
        _timeline(
            anticipation=[_ci(VfxClipId.CURVE_LISSAJOUS, radius=0.7)],
            activation=[_ci(VfxClipId.LINE_BEAM, length=3.8), _ci(VfxClipId.REACT_CHAIN_ARC)],
            decay=[_ci(VfxClipId.REACT_RICOCHET_SPARKS)],
            activation_mods=[_mi(VfxModifierId.COLOR_CYCLE, palette="arcane")],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_OFFENSE_VOID_NOVA.value: _a(
        VfxArchetypeId.MAGIC_OFFENSE_VOID_NOVA,
        VfxBucket.MAGIC_OFFENSE,
        "Nova void a implosione",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.FIELD_VORTEX)],
            activation=[_ci(VfxClipId.REACT_VOID_SINK), _ci(VfxClipId.VOLUME_TORUS_FILLED)],
            decay=[_ci(VfxClipId.SHELL_POP, radius=1.8)],
            activation_mods=[_mi(VfxModifierId.TURBULENCE_HARD)],
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.MAGIC_OFFENSE_EMBER_SPEAR.value: _a(
        VfxArchetypeId.MAGIC_OFFENSE_EMBER_SPEAR,
        VfxBucket.MAGIC_OFFENSE,
        "Spear di brace con scia fisica",
        VfxAnchorId.CASTER_HAND_MAIN,
        _timeline(
            activation=[_ci(VfxClipId.LINE_BEAM, particle=VfxClipId.LINE_BEAM), _ci(VfxClipId.PHYSICS_PLUME)],
            decay=[_ci(VfxClipId.FIELD_SHOCKFRONT)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_OFFENSE_CHAIN_SURGE.value: _a(
        VfxArchetypeId.MAGIC_OFFENSE_CHAIN_SURGE,
        VfxBucket.MAGIC_OFFENSE,
        "Surge concatenato elettrico",
        VfxAnchorId.CHAIN_LINKS,
        _timeline(
            activation=[_ci(VfxClipId.REACT_CHAIN_ARC), _ci(VfxClipId.REACT_LIGHTNING_CAGE)],
            decay=[_ci(VfxClipId.REACT_RICOCHET_SPARKS)],
            activation_mods=[_mi(VfxModifierId.SPEED_RAMP)],
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.MAGIC_OFFENSE_RUPTURE_SPIKE.value: _a(
        VfxArchetypeId.MAGIC_OFFENSE_RUPTURE_SPIKE,
        VfxBucket.MAGIC_OFFENSE,
        "Spike con frattura a terra",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.FIELD_GROUND_CRACKS)],
            activation=[_ci(VfxClipId.REACT_FRACTURE_BLOOM)],
            decay=[_ci(VfxClipId.POINT_FLASH)],
        ),
        VfxBudgetTier.HIGH,
    ),
    # Magic Control
    VfxArchetypeId.MAGIC_CONTROL_FROST_SNARE.value: _a(
        VfxArchetypeId.MAGIC_CONTROL_FROST_SNARE,
        VfxBucket.MAGIC_CONTROL,
        "Snare gelo con anello progressivo",
        VfxAnchorId.TARGET_LOCK,
        _timeline(
            anticipation=[_ci(VfxClipId.CURVE_ROSE, radius=1.0)],
            activation=[_ci(VfxClipId.REACT_FROST_SHATTER), _ci(VfxClipId.ZONE_OUTLINE, radius=1.6)],
            residual=[_ci(VfxClipId.RING_DUAL, radius_inner=1.0, radius_outer=1.6)],
            residual_ticks=16,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_CONTROL_GRAVITY_WELL.value: _a(
        VfxArchetypeId.MAGIC_CONTROL_GRAVITY_WELL,
        VfxBucket.MAGIC_CONTROL,
        "Well gravitazionale con sink centrale",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            activation=[_ci(VfxClipId.FIELD_VORTEX), _ci(VfxClipId.VOLUME_SUPERELLIPSOID)],
            decay=[_ci(VfxClipId.REACT_VOID_SINK)],
            activation_mods=[_mi(VfxModifierId.TIME_STRETCH)],
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.MAGIC_CONTROL_SILENCE_RING.value: _a(
        VfxArchetypeId.MAGIC_CONTROL_SILENCE_RING,
        VfxBucket.MAGIC_CONTROL,
        "Ring di silenzio con telegraph pulito",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.RING_PULSE, radius=1.3)],
            activation=[_ci(VfxClipId.RING_DUAL, radius_inner=1.2, radius_outer=1.9)],
            residual=[_ci(VfxClipId.CURVE_LEMNISCATE)],
            residual_ticks=14,
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.MAGIC_CONTROL_FEAR_WAVE.value: _a(
        VfxArchetypeId.MAGIC_CONTROL_FEAR_WAVE,
        VfxBucket.MAGIC_CONTROL,
        "Wave di fear ad arco frontale",
        VfxAnchorId.CASTER_FORWARD,
        _timeline(
            anticipation=[_ci(VfxClipId.CONE_WARNING, angle_degrees=78.0)],
            activation=[_ci(VfxClipId.ARC_SLASH, angle_degrees=130.0), _ci(VfxClipId.FIELD_SHOCKFRONT)],
            decay=[_ci(VfxClipId.REACT_WITHER_WISP)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_CONTROL_STASIS_CUBE.value: _a(
        VfxArchetypeId.MAGIC_CONTROL_STASIS_CUBE,
        VfxBucket.MAGIC_CONTROL,
        "Cubo stasi con gate interno",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.BOX_FIELD)],
            activation=[_ci(VfxClipId.FIELD_PHASE_GATE), _ci(VfxClipId.VOLUME_OCTAHEDRON_WIRE)],
            residual=[_ci(VfxClipId.POINTS_GLYPH)],
            residual_ticks=12,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    # Magic Support
    VfxArchetypeId.MAGIC_SUPPORT_SANCTUM_PULSE.value: _a(
        VfxArchetypeId.MAGIC_SUPPORT_SANCTUM_PULSE,
        VfxBucket.MAGIC_SUPPORT,
        "Pulse santuario ad area",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.REACT_HOLY_BLOOM), _ci(VfxClipId.FIELD_SHOCKFRONT)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.5)],
            residual_ticks=14,
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.MAGIC_SUPPORT_REGEN_STREAM.value: _a(
        VfxArchetypeId.MAGIC_SUPPORT_REGEN_STREAM,
        VfxBucket.MAGIC_SUPPORT,
        "Stream rigenerativo continuo",
        VfxAnchorId.BETWEEN_CASTER_TARGET,
        _timeline(
            activation=[_ci(VfxClipId.FIELD_RIBBON_TRAIL), _ci(VfxClipId.REACT_HEAL_BURST)],
            residual=[_ci(VfxClipId.POINT_FLASH)],
            residual_ticks=12,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_SUPPORT_AEGIS_LINK.value: _a(
        VfxArchetypeId.MAGIC_SUPPORT_AEGIS_LINK,
        VfxBucket.MAGIC_SUPPORT,
        "Link protettivo caster-target",
        VfxAnchorId.BETWEEN_CASTER_TARGET,
        _timeline(
            activation=[_ci(VfxClipId.POLYLINE_TETHER, length=2.8), _ci(VfxClipId.REACT_SHIELD_SNAP)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.1)],
            residual_ticks=12,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_SUPPORT_CLEANSE_BLOOM.value: _a(
        VfxArchetypeId.MAGIC_SUPPORT_CLEANSE_BLOOM,
        VfxBucket.MAGIC_SUPPORT,
        "Bloom cleanse ad impulso",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.REACT_HOLY_BLOOM), _ci(VfxClipId.CURVE_ROSE)],
            decay=[_ci(VfxClipId.REACT_HEAL_BURST)],
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.MAGIC_SUPPORT_HASTE_FIELD.value: _a(
        VfxArchetypeId.MAGIC_SUPPORT_HASTE_FIELD,
        VfxBucket.MAGIC_SUPPORT,
        "Campo haste dinamico",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.FIELD_ORBIT_SWARM), _ci(VfxClipId.CURVE_SPIROGRAPH)],
            residual=[_ci(VfxClipId.RING_DUAL, radius_inner=1.2, radius_outer=1.6)],
            residual_ticks=14,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    # Magic Mobility
    VfxArchetypeId.MAGIC_MOBILITY_BLINK_TRACE.value: _a(
        VfxArchetypeId.MAGIC_MOBILITY_BLINK_TRACE,
        VfxBucket.MAGIC_MOBILITY,
        "Blink con traccia curva",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.CURVE_LOG_SPIRAL), _ci(VfxClipId.POINT_FLASH)],
            decay=[_ci(VfxClipId.RING_PULSE)],
        ),
        VfxBudgetTier.LOW,
    ),
    VfxArchetypeId.MAGIC_MOBILITY_DASH_AFTERIMAGE.value: _a(
        VfxArchetypeId.MAGIC_MOBILITY_DASH_AFTERIMAGE,
        VfxBucket.MAGIC_MOBILITY,
        "Dash con afterimage a ribbon",
        VfxAnchorId.CASTER_FORWARD,
        _timeline(
            activation=[_ci(VfxClipId.FIELD_RIBBON_TRAIL), _ci(VfxClipId.LINE_BEAM, length=2.6)],
            decay=[_ci(VfxClipId.POINT_FLASH)],
            activation_mods=[_mi(VfxModifierId.TIME_COMPRESS)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_MOBILITY_PHASE_STEP.value: _a(
        VfxArchetypeId.MAGIC_MOBILITY_PHASE_STEP,
        VfxBucket.MAGIC_MOBILITY,
        "Step dimensionale con phase gate",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            anticipation=[_ci(VfxClipId.FIELD_PHASE_GATE)],
            activation=[_ci(VfxClipId.REACT_VOID_SINK), _ci(VfxClipId.POINT_FLASH)],
            decay=[_ci(VfxClipId.CURVE_LEMNISCATE)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_MOBILITY_LAUNCH_SPIRAL.value: _a(
        VfxArchetypeId.MAGIC_MOBILITY_LAUNCH_SPIRAL,
        VfxBucket.MAGIC_MOBILITY,
        "Launch verticale a spirale",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.HELIX_CHANNEL, length=3.0), _ci(VfxClipId.FIELD_VORTEX)],
            decay=[_ci(VfxClipId.FIELD_SHOCKFRONT)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_MOBILITY_SHADOW_SLIDE.value: _a(
        VfxArchetypeId.MAGIC_MOBILITY_SHADOW_SLIDE,
        VfxBucket.MAGIC_MOBILITY,
        "Slide ombra con wisps",
        VfxAnchorId.CASTER_FORWARD,
        _timeline(
            activation=[_ci(VfxClipId.FIELD_RIBBON_TRAIL), _ci(VfxClipId.REACT_WITHER_WISP)],
            decay=[_ci(VfxClipId.POINT_FLASH)],
        ),
        VfxBudgetTier.LOW,
    ),
    # Magic Defense
    VfxArchetypeId.MAGIC_DEFENSE_BARRIER_SHELL.value: _a(
        VfxArchetypeId.MAGIC_DEFENSE_BARRIER_SHELL,
        VfxBucket.MAGIC_DEFENSE,
        "Barrier a guscio",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.VOLUME_CAPSULE_SHELL), _ci(VfxClipId.REACT_SHIELD_SNAP)],
            residual=[_ci(VfxClipId.RING_PULSE, radius=1.3)],
            residual_ticks=14,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_DEFENSE_REFLECT_PRISM.value: _a(
        VfxArchetypeId.MAGIC_DEFENSE_REFLECT_PRISM,
        VfxBucket.MAGIC_DEFENSE,
        "Prisma riflettente",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.VOLUME_PRISM_HEX), _ci(VfxClipId.REACT_RICOCHET_SPARKS)],
            decay=[_ci(VfxClipId.POINT_FLASH)],
        ),
        VfxBudgetTier.MEDIUM,
    ),
    VfxArchetypeId.MAGIC_DEFENSE_ABSORB_CORE.value: _a(
        VfxArchetypeId.MAGIC_DEFENSE_ABSORB_CORE,
        VfxBucket.MAGIC_DEFENSE,
        "Core assorbimento",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            activation=[_ci(VfxClipId.FIELD_CHARGE_CORE), _ci(VfxClipId.VOLUME_TORUS_SHELL)],
            residual=[_ci(VfxClipId.FIELD_VORTEX)],
            residual_ticks=12,
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.MAGIC_DEFENSE_WARD_COLUMNS.value: _a(
        VfxArchetypeId.MAGIC_DEFENSE_WARD_COLUMNS,
        VfxBucket.MAGIC_DEFENSE,
        "Colonne ward persistenti",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            activation=[_ci(VfxClipId.CYLINDER_PILLAR), _ci(VfxClipId.CYLINDER_PILLAR, right=1.2), _ci(VfxClipId.CYLINDER_PILLAR, right=-1.2)],
            residual=[_ci(VfxClipId.RING_DUAL, radius_inner=1.0, radius_outer=1.9)],
            residual_ticks=16,
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.MAGIC_DEFENSE_GUARD_ORBIT.value: _a(
        VfxArchetypeId.MAGIC_DEFENSE_GUARD_ORBIT,
        VfxBucket.MAGIC_DEFENSE,
        "Orbit guard attorno al caster",
        VfxAnchorId.ORBIT_CASTER,
        _timeline(
            activation=[_ci(VfxClipId.FIELD_ORBIT_SWARM), _ci(VfxClipId.CURVE_TORUS_KNOT)],
            residual=[_ci(VfxClipId.RING_PULSE)],
            residual_ticks=12,
        ),
        VfxBudgetTier.MEDIUM,
    ),
    # Magic Ultimate
    VfxArchetypeId.MAGIC_ULTIMATE_STARFALL.value: _a(
        VfxArchetypeId.MAGIC_ULTIMATE_STARFALL,
        VfxBucket.MAGIC_ULTIMATE,
        "Pioggia stellare con impatti seriali",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.FIELD_RAIN_COLUMN)],
            activation=[_ci(VfxClipId.REACT_RICOCHET_SPARKS), _ci(VfxClipId.REACT_HOLY_BLOOM)],
            decay=[_ci(VfxClipId.FIELD_SHOCKFRONT)],
            anticipation_ticks=18,
        ),
        VfxBudgetTier.HIGH,
        readiness=VfxReadiness.NEEDS_TUNING,
    ),
    VfxArchetypeId.MAGIC_ULTIMATE_CATACLYSM_DISC.value: _a(
        VfxArchetypeId.MAGIC_ULTIMATE_CATACLYSM_DISC,
        VfxBucket.MAGIC_ULTIMATE,
        "Disc cataclysm espansivo",
        VfxAnchorId.GROUND_SNAP,
        _timeline(
            anticipation=[_ci(VfxClipId.DISK_GROUND, radius=1.2)],
            activation=[_ci(VfxClipId.FIELD_SHOCKFRONT), _ci(VfxClipId.REACT_FRACTURE_BLOOM)],
            residual=[_ci(VfxClipId.ZONE_OUTLINE, radius=2.2)],
            residual_ticks=18,
        ),
        VfxBudgetTier.HIGH,
        readiness=VfxReadiness.NEEDS_TUNING,
    ),
    VfxArchetypeId.MAGIC_ULTIMATE_ENTROPY_MAELSTROM.value: _a(
        VfxArchetypeId.MAGIC_ULTIMATE_ENTROPY_MAELSTROM,
        VfxBucket.MAGIC_ULTIMATE,
        "Maelstrom entropico ad alta densita",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.FIELD_VORTEX)],
            activation=[_ci(VfxClipId.REACT_VOID_SINK), _ci(VfxClipId.PHYSICS_FRACTURE)],
            residual=[_ci(VfxClipId.FIELD_ORBIT_SWARM)],
            residual_ticks=20,
            activation_mods=[_mi(VfxModifierId.TURBULENCE_HARD)],
        ),
        VfxBudgetTier.HIGH,
        readiness=VfxReadiness.EXPERIMENTAL,
    ),
    VfxArchetypeId.MAGIC_ULTIMATE_SOLAR_VORTEX.value: _a(
        VfxArchetypeId.MAGIC_ULTIMATE_SOLAR_VORTEX,
        VfxBucket.MAGIC_ULTIMATE,
        "Vortice solare con bloom",
        VfxAnchorId.ORIGIN_STATIC,
        _timeline(
            anticipation=[_ci(VfxClipId.CURVE_ROSE, radius=1.6)],
            activation=[_ci(VfxClipId.REACT_HOLY_BLOOM), _ci(VfxClipId.FIELD_VORTEX)],
            residual=[_ci(VfxClipId.FIELD_CHARGE_CORE)],
            residual_ticks=16,
        ),
        VfxBudgetTier.HIGH,
    ),
    VfxArchetypeId.MAGIC_ULTIMATE_CHRONA_RESET.value: _a(
        VfxArchetypeId.MAGIC_ULTIMATE_CHRONA_RESET,
        VfxBucket.MAGIC_ULTIMATE,
        "Reset temporale con compressione",
        VfxAnchorId.CASTER_CENTER,
        _timeline(
            anticipation=[_ci(VfxClipId.FIELD_PHASE_GATE), _ci(VfxClipId.CURVE_SPIROGRAPH)],
            activation=[_ci(VfxClipId.REACT_SHIELD_SNAP), _ci(VfxClipId.POINT_FLASH, count=3)],
            decay=[_ci(VfxClipId.CURVE_LOG_SPIRAL)],
            activation_mods=[_mi(VfxModifierId.TIME_COMPRESS)],
        ),
        VfxBudgetTier.MEDIUM,
        readiness=VfxReadiness.EXPERIMENTAL,
    ),
}


def resolve_archetype(archetype_id: VfxArchetypeLike) -> ArchetypeSpec:
    token = archetype_id.value if isinstance(archetype_id, VfxArchetypeId) else str(archetype_id).strip()
    entry = _ARCHETYPES.get(token)
    if entry is None:
        known = ", ".join(sorted(_ARCHETYPES))
        raise ValueError(f"vfx.archetype: unknown archetype_id={archetype_id!r}. Known: {known}")
    return entry


def catalog_archetypes() -> Mapping[str, ArchetypeSpec]:
    return MappingProxyType(dict(_ARCHETYPES))


__all__ = [
    "resolve_archetype",
    "catalog_archetypes",
]
