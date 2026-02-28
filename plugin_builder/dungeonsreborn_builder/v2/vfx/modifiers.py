"""Compile-time VFX modifiers."""

from __future__ import annotations

import math
import random
from types import MappingProxyType
from typing import Any, Mapping

from .model import ModifierSpec, VfxModifierId, VfxModifierLike, VfxPhaseKind


def _mut_number(payload: dict[str, Any], key: str, factor: float) -> None:
    if key in payload and isinstance(payload[key], (int, float)):
        payload[key] = type(payload[key])(payload[key] * factor)


def _mut_many(payload: dict[str, Any], keys: tuple[str, ...], factor: float) -> None:
    for key in keys:
        _mut_number(payload, key, factor)


def _jitter(payload: dict[str, Any], amount: float, seed: int) -> None:
    rng = random.Random(seed)
    for key in ("forward", "right", "up"):
        if key in payload and isinstance(payload[key], (int, float)):
            payload[key] = float(payload[key]) + rng.uniform(-amount, amount)


def _fade_in(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if phase == VfxPhaseKind.ANTICIPATION:
        _mut_many(payload, ("count", "points"), float(params.get("factor", 0.75)))
    return payload


def _fade_out(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if phase in {VfxPhaseKind.DECAY, VfxPhaseKind.RESIDUAL}:
        _mut_many(payload, ("count", "points"), float(params.get("factor", 0.65)))
    return payload


def _pulse_amp(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if phase == VfxPhaseKind.ACTIVATION:
        _mut_many(payload, ("radius", "length", "height"), float(params.get("factor", 1.15)))
    return payload


def _scale_ramp(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    factor = float(params.get("factor", 1.2 if phase == VfxPhaseKind.ACTIVATION else 0.95))
    _mut_many(payload, ("radius", "length", "height", "xRadius", "yRadius", "zRadius"), factor)
    return payload


def _density_ramp(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    factor = float(params.get("factor", 1.15 if phase == VfxPhaseKind.ACTIVATION else 1.0))
    _mut_many(payload, ("count", "points", "rings", "pointsPerRing", "pointsPerEdge"), factor)
    return payload


def _jitter_soft(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _jitter(payload, float(params.get("amount", 0.05)), int(params.get("seed", 17)))
    return payload


def _jitter_hard(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _jitter(payload, float(params.get("amount", 0.12)), int(params.get("seed", 29)))
    return payload


def _rotate_y(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    angle = math.radians(float(params.get("degrees", 18.0)))
    forward = float(payload.get("forward", 0.0))
    right = float(payload.get("right", 0.0))
    payload["forward"] = forward * math.cos(angle) - right * math.sin(angle)
    payload["right"] = forward * math.sin(angle) + right * math.cos(angle)
    return payload


def _rotate_full(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    payload = _rotate_y(payload, {"degrees": params.get("degrees", 35.0)}, phase)
    _mut_number(payload, "up", 1.0)
    return payload


def _gradient_shift(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if "startColor" in payload and "endColor" in payload:
        payload["startColor"] = str(params.get("start_color", payload["startColor"]))
        payload["endColor"] = str(params.get("end_color", payload["endColor"]))
    return payload


def _palette_swap(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if "palette" in params:
        payload["paletteTag"] = str(params["palette"])
    return payload


def _noise_pos(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _jitter(payload, float(params.get("amount", 0.08)), int(params.get("seed", 47)))
    return payload


def _noise_time(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if "periodTicks" in payload and isinstance(payload["periodTicks"], (int, float)):
        jitter = float(params.get("jitter", 0.15))
        payload["periodTicks"] = max(1, int(round(float(payload["periodTicks"]) * (1.0 + jitter))))
    return payload


def _speed_ramp(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _mut_many(payload, ("velocityX", "velocityY", "velocityZ", "driftSpeed"), float(params.get("factor", 1.15)))
    return payload


def _drag_boost(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _mut_number(payload, "drag", float(params.get("factor", 1.25)))
    return payload


def _gravity_flip(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if "gravity" in payload and isinstance(payload["gravity"], (int, float)):
        payload["gravity"] = -abs(float(payload["gravity"]))
    return payload


def _anchor_lag(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    payload["anchorLag"] = int(params.get("ticks", 2))
    return payload


def _phase_gate(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    allowed = params.get("allowed")
    if allowed is None:
        return payload
    allowed_tokens = {str(entry).strip().lower() for entry in allowed}
    if phase.value not in allowed_tokens:
        payload["_skip"] = True
    return payload


def _time_stretch(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _mut_number(payload, "durationTicks", float(params.get("factor", 1.35)))
    _mut_number(payload, "periodTicks", float(params.get("period_factor", 1.1)))
    return payload


def _time_compress(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _mut_number(payload, "durationTicks", float(params.get("factor", 0.75)))
    _mut_number(payload, "periodTicks", float(params.get("period_factor", 0.9)))
    return payload


def _amplitude_wobble(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    factor = float(params.get("factor", 1.12))
    _mut_many(payload, ("radius", "length", "height", "xRadius", "yRadius", "zRadius"), factor)
    return payload


def _radius_pingpong(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if phase in {VfxPhaseKind.ANTICIPATION, VfxPhaseKind.DECAY}:
        _mut_number(payload, "radius", float(params.get("low", 0.85)))
    else:
        _mut_number(payload, "radius", float(params.get("high", 1.2)))
    return payload


def _phase_offset(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    payload["phaseOffset"] = int(params.get("ticks", 2))
    return payload


def _seed_jitter(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _jitter(payload, float(params.get("amount", 0.03)), int(params.get("seed", 101)))
    return payload


def _turbulence_soft(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _jitter(payload, float(params.get("amount", 0.09)), int(params.get("seed", 137)))
    _mut_number(payload, "spread", float(params.get("spread_factor", 1.1)))
    return payload


def _turbulence_hard(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _jitter(payload, float(params.get("amount", 0.16)), int(params.get("seed", 211)))
    _mut_number(payload, "spread", float(params.get("spread_factor", 1.3)))
    return payload


def _color_cycle(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    payload["colorCycle"] = str(params.get("palette", "arcane"))
    return payload


def _color_pingpong(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    payload["colorPingpong"] = True
    return payload


def _alpha_fade_by_phase(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    alpha = {
        VfxPhaseKind.ANTICIPATION: 0.55,
        VfxPhaseKind.ACTIVATION: 1.0,
        VfxPhaseKind.DECAY: 0.75,
        VfxPhaseKind.RESIDUAL: 0.45,
    }[phase]
    payload["alpha"] = float(params.get("alpha", alpha))
    return payload


def _line_step_lod(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    if "step" in payload and isinstance(payload["step"], (int, float)):
        payload["step"] = float(payload["step"]) * float(params.get("factor", 1.15))
    return payload


def _count_lod(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _mut_many(payload, ("count", "points", "rings", "pointsPerRing", "pointsPerEdge"), float(params.get("factor", 0.85)))
    return payload


def _physics_dampen(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _mut_number(payload, "drag", float(params.get("drag", 1.35)))
    _mut_number(payload, "gravity", float(params.get("gravity", 1.1)))
    return payload


def _physics_explode(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    _mut_many(payload, ("velocityX", "velocityY", "velocityZ"), float(params.get("factor", 1.4)))
    _mut_number(payload, "spread", float(params.get("spread", 1.5)))
    return payload


def _anchor_trail_lag(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    payload["anchorTrailLag"] = int(params.get("ticks", 3))
    return payload


def _snap_ground(payload: dict[str, Any], params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    payload["up"] = float(params.get("up", 0.05))
    payload["at"] = str(params.get("at", payload.get("at", "origin"))).lower()
    return payload


_MODIFIERS: dict[str, ModifierSpec] = {
    VfxModifierId.FADE_IN.value: ModifierSpec(VfxModifierId.FADE_IN, "Fade in", _fade_in),
    VfxModifierId.FADE_OUT.value: ModifierSpec(VfxModifierId.FADE_OUT, "Fade out", _fade_out),
    VfxModifierId.PULSE_AMP.value: ModifierSpec(VfxModifierId.PULSE_AMP, "Pulse amp", _pulse_amp),
    VfxModifierId.SCALE_RAMP.value: ModifierSpec(VfxModifierId.SCALE_RAMP, "Scale ramp", _scale_ramp),
    VfxModifierId.DENSITY_RAMP.value: ModifierSpec(VfxModifierId.DENSITY_RAMP, "Density ramp", _density_ramp),
    VfxModifierId.JITTER_SOFT.value: ModifierSpec(VfxModifierId.JITTER_SOFT, "Jitter soft", _jitter_soft),
    VfxModifierId.JITTER_HARD.value: ModifierSpec(VfxModifierId.JITTER_HARD, "Jitter hard", _jitter_hard),
    VfxModifierId.ROTATE_Y.value: ModifierSpec(VfxModifierId.ROTATE_Y, "Rotate y", _rotate_y),
    VfxModifierId.ROTATE_FULL.value: ModifierSpec(VfxModifierId.ROTATE_FULL, "Rotate full", _rotate_full),
    VfxModifierId.GRADIENT_SHIFT.value: ModifierSpec(VfxModifierId.GRADIENT_SHIFT, "Gradient shift", _gradient_shift),
    VfxModifierId.PALETTE_SWAP.value: ModifierSpec(VfxModifierId.PALETTE_SWAP, "Palette swap", _palette_swap),
    VfxModifierId.NOISE_POS.value: ModifierSpec(VfxModifierId.NOISE_POS, "Noise pos", _noise_pos),
    VfxModifierId.NOISE_TIME.value: ModifierSpec(VfxModifierId.NOISE_TIME, "Noise time", _noise_time),
    VfxModifierId.SPEED_RAMP.value: ModifierSpec(VfxModifierId.SPEED_RAMP, "Speed ramp", _speed_ramp),
    VfxModifierId.DRAG_BOOST.value: ModifierSpec(VfxModifierId.DRAG_BOOST, "Drag boost", _drag_boost),
    VfxModifierId.GRAVITY_FLIP.value: ModifierSpec(VfxModifierId.GRAVITY_FLIP, "Gravity flip", _gravity_flip),
    VfxModifierId.ANCHOR_LAG.value: ModifierSpec(VfxModifierId.ANCHOR_LAG, "Anchor lag", _anchor_lag),
    VfxModifierId.PHASE_GATE.value: ModifierSpec(VfxModifierId.PHASE_GATE, "Phase gate", _phase_gate),
    VfxModifierId.TIME_STRETCH.value: ModifierSpec(VfxModifierId.TIME_STRETCH, "Time stretch", _time_stretch),
    VfxModifierId.TIME_COMPRESS.value: ModifierSpec(VfxModifierId.TIME_COMPRESS, "Time compress", _time_compress),
    VfxModifierId.AMPLITUDE_WOBBLE.value: ModifierSpec(VfxModifierId.AMPLITUDE_WOBBLE, "Amplitude wobble", _amplitude_wobble),
    VfxModifierId.RADIUS_PINGPONG.value: ModifierSpec(VfxModifierId.RADIUS_PINGPONG, "Radius pingpong", _radius_pingpong),
    VfxModifierId.PHASE_OFFSET.value: ModifierSpec(VfxModifierId.PHASE_OFFSET, "Phase offset", _phase_offset),
    VfxModifierId.SEED_JITTER.value: ModifierSpec(VfxModifierId.SEED_JITTER, "Seed jitter", _seed_jitter),
    VfxModifierId.TURBULENCE_SOFT.value: ModifierSpec(VfxModifierId.TURBULENCE_SOFT, "Turbulence soft", _turbulence_soft),
    VfxModifierId.TURBULENCE_HARD.value: ModifierSpec(VfxModifierId.TURBULENCE_HARD, "Turbulence hard", _turbulence_hard),
    VfxModifierId.COLOR_CYCLE.value: ModifierSpec(VfxModifierId.COLOR_CYCLE, "Color cycle", _color_cycle),
    VfxModifierId.COLOR_PINGPONG.value: ModifierSpec(VfxModifierId.COLOR_PINGPONG, "Color pingpong", _color_pingpong),
    VfxModifierId.ALPHA_FADE_BY_PHASE.value: ModifierSpec(VfxModifierId.ALPHA_FADE_BY_PHASE, "Alpha fade by phase", _alpha_fade_by_phase),
    VfxModifierId.LINE_STEP_LOD.value: ModifierSpec(VfxModifierId.LINE_STEP_LOD, "Line step lod", _line_step_lod),
    VfxModifierId.COUNT_LOD.value: ModifierSpec(VfxModifierId.COUNT_LOD, "Count lod", _count_lod),
    VfxModifierId.PHYSICS_DAMPEN.value: ModifierSpec(VfxModifierId.PHYSICS_DAMPEN, "Physics dampen", _physics_dampen),
    VfxModifierId.PHYSICS_EXPLODE.value: ModifierSpec(VfxModifierId.PHYSICS_EXPLODE, "Physics explode", _physics_explode),
    VfxModifierId.ANCHOR_TRAIL_LAG.value: ModifierSpec(VfxModifierId.ANCHOR_TRAIL_LAG, "Anchor trail lag", _anchor_trail_lag),
    VfxModifierId.SNAP_GROUND.value: ModifierSpec(VfxModifierId.SNAP_GROUND, "Snap ground", _snap_ground),
}


def resolve_modifier(modifier_id: VfxModifierLike) -> ModifierSpec:
    token = modifier_id.value if isinstance(modifier_id, VfxModifierId) else str(modifier_id).strip()
    entry = _MODIFIERS.get(token)
    if entry is None:
        known = ", ".join(sorted(_MODIFIERS))
        raise ValueError(f"vfx.modifier: unknown modifier_id={modifier_id!r}. Known: {known}")
    return entry


def apply_modifier(payload: dict[str, Any], modifier_id: VfxModifierLike, params: Mapping[str, Any], phase: VfxPhaseKind) -> dict[str, Any]:
    spec = resolve_modifier(modifier_id)
    return spec.apply(payload, params, phase)


def catalog_modifiers() -> Mapping[str, ModifierSpec]:
    return MappingProxyType(dict(_MODIFIERS))


__all__ = [
    "resolve_modifier",
    "apply_modifier",
    "catalog_modifiers",
]
