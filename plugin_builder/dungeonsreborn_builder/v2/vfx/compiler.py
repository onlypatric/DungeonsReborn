"""Compiler for high-level VFX model -> low-level ActionSpec graph."""

from __future__ import annotations

from typing import Any, Mapping, Sequence

from dungeonsreborn_builder.v2.effects import ActionSpec, fx
from .anchors import apply_anchor_defaults, resolve_anchor
from .archetypes import resolve_archetype
from .clips import build_clip, resolve_clip
from .modifiers import apply_modifier
from .model import (
    AnchorSpec,
    ArchetypeInstance,
    ArchetypeSpec,
    BudgetSpec,
    ModifierInstance,
    PhaseSpec,
    TimelineSpec,
    VariationSpec,
    VfxAnchorId,
    VfxAnchorLike,
    VfxArchetypeLike,
    VfxBudgetTier,
    VfxLod,
    VfxPhaseKind,
)


def _scale_params(params: dict[str, Any], variation: VariationSpec) -> dict[str, Any]:
    out = dict(params)
    scale = float(variation.scale)
    density = float(variation.density)
    if scale != 1.0:
        for key in ("radius", "length", "height", "x_radius", "xRadius", "y_radius", "yRadius", "z_radius", "zRadius"):
            if key in out and isinstance(out[key], (int, float)):
                out[key] = float(out[key]) * scale
    if density != 1.0:
        for key in ("count", "points", "rings", "points_per_ring", "pointsPerRing", "points_per_edge", "pointsPerEdge"):
            if key in out and isinstance(out[key], (int, float)):
                out[key] = max(1, int(round(float(out[key]) * density)))
    if variation.jitter > 0.0:
        base_seed = int(variation.seed or 0)
        jitter = float(variation.jitter)
        for index, key in enumerate(("forward", "right", "up")):
            if key in out and isinstance(out[key], (int, float)):
                sign = -1.0 if ((base_seed + index) % 2 == 0) else 1.0
                out[key] = float(out[key]) + sign * jitter
    if variation.palette:
        out.setdefault("palette", variation.palette)
    return out


def _resolve_lod(*, requested: VfxLod | None, budget: BudgetSpec) -> VfxLod:
    return requested or budget.lod


def _allow_clip_for_lod(*, clip_tier: VfxBudgetTier, lod: VfxLod) -> bool:
    if lod == VfxLod.HIGH:
        return True
    if lod == VfxLod.MEDIUM:
        return True
    return clip_tier != VfxBudgetTier.HIGH


def _compile_clip(
    clip: ClipInstance,
    *,
    phase_kind: VfxPhaseKind,
    anchor: AnchorSpec,
    variation: VariationSpec,
    modifiers: Sequence[ModifierInstance],
    lod: VfxLod,
) -> ActionSpec | None:
    spec = resolve_clip(clip.clip_id)
    if not _allow_clip_for_lod(clip_tier=spec.budget, lod=lod):
        return None

    payload = dict(clip.params)
    payload = apply_anchor_defaults(payload, anchor)
    payload = _scale_params(payload, variation)

    for entry in modifiers:
        payload = apply_modifier(payload, entry.modifier_id, entry.params, phase_kind)

    if bool(payload.pop("_skip", False)):
        return None

    return build_clip(clip.clip_id, params=payload)


def _phase_duration_ticks(phase: PhaseSpec | None, variation: VariationSpec) -> int:
    if phase is None:
        return 0
    return max(0, int(round(float(phase.duration_ticks) * float(variation.duration_scale))))


def _compile_phase(
    phase: PhaseSpec | None,
    *,
    anchor: AnchorSpec,
    variation: VariationSpec,
    modifiers: Sequence[ModifierInstance],
    lod: VfxLod,
    budget: BudgetSpec,
) -> ActionSpec | None:
    if phase is None:
        return None

    layered_modifiers = [*modifiers, *phase.modifiers]
    clip_actions: list[ActionSpec] = []
    for clip in phase.clips:
        if len(clip_actions) >= int(budget.max_layers):
            break
        compiled = _compile_clip(
            clip,
            phase_kind=phase.kind,
            anchor=anchor,
            variation=variation,
            modifiers=layered_modifiers,
            lod=lod,
        )
        if compiled is not None:
            clip_actions.append(compiled)

    if not clip_actions:
        return None
    if len(clip_actions) == 1:
        return clip_actions[0]
    return fx.sequence(*clip_actions)


def compile_timeline(
    timeline: TimelineSpec,
    *,
    anchor: AnchorSpec,
    variation: VariationSpec | None = None,
    modifiers: Sequence[ModifierInstance] = (),
    budget: BudgetSpec | None = None,
    lod: VfxLod | None = None,
) -> ActionSpec:
    applied_variation = variation or VariationSpec()
    applied_budget = budget or BudgetSpec()
    applied_lod = _resolve_lod(requested=lod, budget=applied_budget)

    charge = _compile_phase(
        timeline.anticipation,
        anchor=anchor,
        variation=applied_variation,
        modifiers=modifiers,
        lod=applied_lod,
        budget=applied_budget,
    )
    sustain = _compile_phase(
        timeline.activation,
        anchor=anchor,
        variation=applied_variation,
        modifiers=modifiers,
        lod=applied_lod,
        budget=applied_budget,
    )
    decay = _compile_phase(
        timeline.decay,
        anchor=anchor,
        variation=applied_variation,
        modifiers=modifiers,
        lod=applied_lod,
        budget=applied_budget,
    )
    residual = _compile_phase(
        timeline.residual,
        anchor=anchor,
        variation=applied_variation,
        modifiers=modifiers,
        lod=applied_lod,
        budget=applied_budget,
    )

    release: ActionSpec | None = None
    if decay is not None and residual is not None:
        release = fx.sequence(decay, residual)
    elif decay is not None:
        release = decay
    elif residual is not None:
        release = residual

    charge_ticks = _phase_duration_ticks(timeline.anticipation, applied_variation)
    sustain_ticks = _phase_duration_ticks(timeline.activation, applied_variation)
    release_ticks = _phase_duration_ticks(timeline.decay, applied_variation) + _phase_duration_ticks(
        timeline.residual,
        applied_variation,
    )

    return fx.state_machine(
        charge=charge,
        sustain=sustain,
        release=release,
        charge_ticks=charge_ticks,
        sustain_ticks=sustain_ticks,
        release_ticks=release_ticks,
        period_ticks=timeline.period_ticks,
        follow_caster=timeline.follow_caster,
        easing=timeline.easing,
    )


def compile_archetype(
    source: ArchetypeSpec | ArchetypeInstance | VfxArchetypeLike,
    *,
    lod: VfxLod | None = None,
    anchor: VfxAnchorLike | None = None,
    budget: BudgetSpec | None = None,
    modifiers: Sequence[ModifierInstance] = (),
    variation: VariationSpec | None = None,
) -> ActionSpec:
    if isinstance(source, ArchetypeSpec):
        spec = source
        instance = ArchetypeInstance(archetype_id=spec.archetype_id)
    elif isinstance(source, ArchetypeInstance):
        spec = resolve_archetype(source.archetype_id)
        instance = source
    else:
        spec = resolve_archetype(source)
        instance = ArchetypeInstance(archetype_id=spec.archetype_id)

    applied_budget = budget or instance.budget or spec.budget
    applied_anchor = resolve_anchor(anchor or instance.anchor or spec.anchor)
    applied_lod = lod or instance.lod or applied_budget.lod
    applied_variation = instance.variation if variation is None else variation
    applied_modifiers = [*spec.modifiers, *instance.modifiers, *modifiers]

    return compile_timeline(
        spec.timeline,
        anchor=applied_anchor,
        variation=applied_variation,
        modifiers=applied_modifiers,
        budget=applied_budget,
        lod=applied_lod,
    )


def compile_vfx(
    source: TimelineSpec | ArchetypeSpec | ArchetypeInstance | VfxArchetypeLike,
    *,
    lod: VfxLod | None = None,
    anchor: VfxAnchorLike | None = None,
    budget: BudgetSpec | None = None,
    modifiers: Sequence[ModifierInstance] = (),
    variation: VariationSpec | None = None,
) -> ActionSpec:
    if isinstance(source, TimelineSpec):
        resolved_anchor = resolve_anchor(anchor or VfxAnchorId.ORIGIN_STATIC)
        return compile_timeline(
            source,
            anchor=resolved_anchor,
            variation=variation,
            modifiers=modifiers,
            budget=budget,
            lod=lod,
        )
    return compile_archetype(
        source,
        lod=lod,
        anchor=anchor,
        budget=budget,
        modifiers=modifiers,
        variation=variation,
    )


__all__ = [
    "compile_timeline",
    "compile_archetype",
    "compile_vfx",
]
