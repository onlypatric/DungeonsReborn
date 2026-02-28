"""Anchor behavior registry for high-level VFX."""

from __future__ import annotations

from types import MappingProxyType
from typing import Any, Mapping

from dungeonsreborn_builder.v2.enums import AnchorMode, AtMode, TargetAnchor
from .model import AnchorSpec, VfxAnchorId, VfxAnchorLike


_ANCHORS: dict[str, AnchorSpec] = {
    VfxAnchorId.ORIGIN_STATIC.value: AnchorSpec(
        anchor_id=VfxAnchorId.ORIGIN_STATIC,
        mode=AnchorMode.ORIGIN,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.ORIGIN,
    ),
    VfxAnchorId.CASTER_CENTER.value: AnchorSpec(
        anchor_id=VfxAnchorId.CASTER_CENTER,
        mode=AnchorMode.CASTER,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.CASTER,
    ),
    VfxAnchorId.CASTER_FORWARD.value: AnchorSpec(
        anchor_id=VfxAnchorId.CASTER_FORWARD,
        mode=AnchorMode.CASTER,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.CASTER,
        forward=1.2,
    ),
    VfxAnchorId.TARGET_LOCK.value: AnchorSpec(
        anchor_id=VfxAnchorId.TARGET_LOCK,
        mode=AnchorMode.LAST_ENTITY,
        at_mode=AtMode.LAST_ENTITY,
        particle_at=TargetAnchor.LAST_ENTITY,
        line_target_at=TargetAnchor.LAST_ENTITY,
    ),
    VfxAnchorId.LAST_ENTITY.value: AnchorSpec(
        anchor_id=VfxAnchorId.LAST_ENTITY,
        mode=AnchorMode.LAST_ENTITY,
        at_mode=AtMode.LAST_ENTITY,
        particle_at=TargetAnchor.LAST_ENTITY,
        line_target_at=TargetAnchor.LAST_ENTITY,
    ),
    VfxAnchorId.GROUND_SNAP.value: AnchorSpec(
        anchor_id=VfxAnchorId.GROUND_SNAP,
        mode=AnchorMode.ORIGIN,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.ORIGIN,
        up=0.05,
    ),
    VfxAnchorId.GROUND_PATH.value: AnchorSpec(
        anchor_id=VfxAnchorId.GROUND_PATH,
        mode=AnchorMode.ORIGIN,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.ORIGIN,
        forward=0.5,
    ),
    VfxAnchorId.ORBIT_CASTER.value: AnchorSpec(
        anchor_id=VfxAnchorId.ORBIT_CASTER,
        mode=AnchorMode.CASTER,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.CASTER,
    ),
    VfxAnchorId.ORBIT_TARGET.value: AnchorSpec(
        anchor_id=VfxAnchorId.ORBIT_TARGET,
        mode=AnchorMode.LAST_ENTITY,
        at_mode=AtMode.LAST_ENTITY,
        particle_at=TargetAnchor.LAST_ENTITY,
    ),
    VfxAnchorId.SEGMENT_START.value: AnchorSpec(
        anchor_id=VfxAnchorId.SEGMENT_START,
        mode=AnchorMode.ORIGIN,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.ORIGIN,
    ),
    VfxAnchorId.SEGMENT_END.value: AnchorSpec(
        anchor_id=VfxAnchorId.SEGMENT_END,
        mode=AnchorMode.LAST_ENTITY,
        at_mode=AtMode.LAST_ENTITY,
        particle_at=TargetAnchor.LAST_ENTITY,
        line_target_at=TargetAnchor.LAST_ENTITY,
    ),
    VfxAnchorId.CHAIN_LINKS.value: AnchorSpec(
        anchor_id=VfxAnchorId.CHAIN_LINKS,
        mode=AnchorMode.ORIGIN,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.ORIGIN,
    ),
    VfxAnchorId.TARGET_PREDICTED.value: AnchorSpec(
        anchor_id=VfxAnchorId.TARGET_PREDICTED,
        mode=AnchorMode.LAST_ENTITY,
        at_mode=AtMode.LAST_ENTITY,
        particle_at=TargetAnchor.LAST_ENTITY,
        forward=0.4,
    ),
    VfxAnchorId.SURFACE_NORMAL.value: AnchorSpec(
        anchor_id=VfxAnchorId.SURFACE_NORMAL,
        mode=AnchorMode.LAST_HIT,
        at_mode=AtMode.LAST_HIT,
        particle_at=TargetAnchor.LAST_HIT,
        up=0.2,
    ),
    VfxAnchorId.PROJECTILE_PATH_HEAD.value: AnchorSpec(
        anchor_id=VfxAnchorId.PROJECTILE_PATH_HEAD,
        mode=AnchorMode.PROJECTILE,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.ORIGIN,
        forward=0.3,
    ),
    VfxAnchorId.PROJECTILE_PATH_TAIL.value: AnchorSpec(
        anchor_id=VfxAnchorId.PROJECTILE_PATH_TAIL,
        mode=AnchorMode.PROJECTILE,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.ORIGIN,
        forward=-0.4,
    ),
    VfxAnchorId.BETWEEN_CASTER_TARGET.value: AnchorSpec(
        anchor_id=VfxAnchorId.BETWEEN_CASTER_TARGET,
        mode=AnchorMode.CASTER,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.CASTER,
        forward=0.8,
    ),
    VfxAnchorId.CASTER_HAND_MAIN.value: AnchorSpec(
        anchor_id=VfxAnchorId.CASTER_HAND_MAIN,
        mode=AnchorMode.CASTER,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.CASTER,
        right=0.35,
        up=0.2,
    ),
    VfxAnchorId.CASTER_HAND_OFF.value: AnchorSpec(
        anchor_id=VfxAnchorId.CASTER_HAND_OFF,
        mode=AnchorMode.CASTER,
        at_mode=AtMode.ORIGIN,
        particle_at=TargetAnchor.CASTER,
        right=-0.35,
        up=0.2,
    ),
    VfxAnchorId.HIT_BLOCK_FACE.value: AnchorSpec(
        anchor_id=VfxAnchorId.HIT_BLOCK_FACE,
        mode=AnchorMode.LAST_HIT,
        at_mode=AtMode.LAST_HIT,
        particle_at=TargetAnchor.LAST_HIT,
        up=0.1,
    ),
}


def resolve_anchor(anchor_id: VfxAnchorLike) -> AnchorSpec:
    token = anchor_id.value if isinstance(anchor_id, VfxAnchorId) else str(anchor_id).strip()
    entry = _ANCHORS.get(token)
    if entry is None:
        known = ", ".join(sorted(_ANCHORS))
        raise ValueError(f"vfx.anchor: unknown anchor_id={anchor_id!r}. Known: {known}")
    return entry


def apply_anchor_defaults(params: Mapping[str, Any], anchor: AnchorSpec) -> dict[str, Any]:
    out = dict(params)
    out.setdefault("at", anchor.particle_at)
    if anchor.line_target_at is not None:
        out.setdefault("target_at", anchor.line_target_at)
    out.setdefault("forward", 0.0)
    out.setdefault("right", 0.0)
    out.setdefault("up", 0.0)
    out["forward"] = float(out["forward"]) + float(anchor.forward)
    out["right"] = float(out["right"]) + float(anchor.right)
    out["up"] = float(out["up"]) + float(anchor.up)
    return out


def catalog_anchors() -> Mapping[str, AnchorSpec]:
    return MappingProxyType(dict(_ANCHORS))


__all__ = [
    "resolve_anchor",
    "apply_anchor_defaults",
    "catalog_anchors",
]
