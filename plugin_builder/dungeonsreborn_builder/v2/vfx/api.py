"""Public high-level VFX API namespace."""

from __future__ import annotations

from types import MappingProxyType
from typing import Any, Mapping, Sequence

from .anchors import catalog_anchors, resolve_anchor
from .archetypes import catalog_archetypes
from .afflict import afflict as afflict_api
from .clips import catalog_clips
from .compiler import compile_vfx
from .modifiers import catalog_modifiers
from .model import (
    AnchorSpec,
    ArchetypeInstance,
    ArchetypeSpec,
    BudgetSpec,
    ClipInstance,
    ModifierInstance,
    PhaseSpec,
    TimelineSpec,
    VariationSpec,
    VfxAnchorLike,
    VfxArchetypeLike,
    VfxClipLike,
    VfxLod,
    VfxModifierLike,
    VfxPhaseKind,
)


class _VfxApi:
    afflict = afflict_api

    def clip(self, clip_id: VfxClipLike, **params: Any) -> ClipInstance:
        return ClipInstance(clip_id=clip_id, params=params)

    def modifier(self, modifier_id: VfxModifierLike, **params: Any) -> ModifierInstance:
        return ModifierInstance(modifier_id=modifier_id, params=params)

    def timeline(
        self,
        *,
        anticipation: Sequence[ClipInstance] = (),
        activation: Sequence[ClipInstance] = (),
        decay: Sequence[ClipInstance] = (),
        residual: Sequence[ClipInstance] = (),
        anticipation_modifiers: Sequence[ModifierInstance] = (),
        activation_modifiers: Sequence[ModifierInstance] = (),
        decay_modifiers: Sequence[ModifierInstance] = (),
        residual_modifiers: Sequence[ModifierInstance] = (),
        anticipation_ticks: int = 8,
        activation_ticks: int = 14,
        decay_ticks: int = 10,
        residual_ticks: int = 8,
        period_ticks: int = 1,
    ) -> TimelineSpec:
        return TimelineSpec(
            anticipation=PhaseSpec(
                kind=VfxPhaseKind.ANTICIPATION,
                clips=list(anticipation),
                modifiers=list(anticipation_modifiers),
                duration_ticks=anticipation_ticks,
            )
            if anticipation
            else None,
            activation=PhaseSpec(
                kind=VfxPhaseKind.ACTIVATION,
                clips=list(activation),
                modifiers=list(activation_modifiers),
                duration_ticks=activation_ticks,
            )
            if activation
            else None,
            decay=PhaseSpec(
                kind=VfxPhaseKind.DECAY,
                clips=list(decay),
                modifiers=list(decay_modifiers),
                duration_ticks=decay_ticks,
            )
            if decay
            else None,
            residual=PhaseSpec(
                kind=VfxPhaseKind.RESIDUAL,
                clips=list(residual),
                modifiers=list(residual_modifiers),
                duration_ticks=residual_ticks,
            )
            if residual
            else None,
            period_ticks=period_ticks,
        )

    def archetype(
        self,
        archetype_id: VfxArchetypeLike,
        *,
        lod: VfxLod | None = None,
        anchor: VfxAnchorLike | None = None,
        budget: BudgetSpec | None = None,
        modifiers: Sequence[ModifierInstance] = (),
        variation: VariationSpec | None = None,
    ) -> ArchetypeInstance:
        return ArchetypeInstance(
            archetype_id=archetype_id,
            lod=lod,
            anchor=anchor,
            budget=budget,
            modifiers=list(modifiers),
            variation=variation or VariationSpec(),
        )

    def compile(
        self,
        source: TimelineSpec | ArchetypeSpec | ArchetypeInstance | VfxArchetypeLike,
        *,
        lod: VfxLod | None = None,
        anchor: VfxAnchorLike | None = None,
        budget: BudgetSpec | None = None,
        modifiers: Sequence[ModifierInstance] = (),
        variation: VariationSpec | None = None,
    ):
        return compile_vfx(
            source,
            lod=lod,
            anchor=anchor,
            budget=budget,
            modifiers=modifiers,
            variation=variation,
        )

    def anchor(self, anchor_id: VfxAnchorLike) -> AnchorSpec:
        return resolve_anchor(anchor_id)

    def catalog(self) -> Mapping[str, Any]:
        clips = catalog_clips()
        modifiers = catalog_modifiers()
        anchors = catalog_anchors()
        archetypes = catalog_archetypes()
        return MappingProxyType(
            {
                "counts": {
                    "clips": len(clips),
                    "modifiers": len(modifiers),
                    "anchors": len(anchors),
                    "archetypes": len(archetypes),
                },
                "clips": tuple(sorted(clips.keys())),
                "modifiers": tuple(sorted(modifiers.keys())),
                "anchors": tuple(sorted(anchors.keys())),
                "archetypes": tuple(sorted(archetypes.keys())),
            }
        )


vfx = _VfxApi()


__all__ = ["vfx"]
