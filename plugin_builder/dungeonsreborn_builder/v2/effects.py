"""Strict, class-based effects DSL for builder v2."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Optional, Sequence

from .core import BuildContext, Ref
from .enums import (
    CombatCooldownScope,
    CombatCooldownScopeLike,
    CombatEventOriginBind,
    CombatEventOriginBindLike,
    CombatEventTargetBind,
    CombatEventTargetBindLike,
    CombatEventType,
    CombatEventTypeLike,
    DamagePolicy,
    DamagePolicyLike,
    DamageType,
    DamageTypeLike,
    DamageCause,
    DamageCauseLike,
    HealType,
    HealTypeLike,
    AfflictionRefreshPolicy,
    AfflictionRefreshPolicyLike,
    AfflictionAudience,
    AfflictionAudienceLike,
    ActionType,
    ForEachMode,
    Easing,
    EasingLike,
    AtMode,
    AtModeLike,
    AnchorMode,
    AnchorModeLike,
    AnchorPoint,
    AnchorPointLike,
    MotionMode,
    MotionModeLike,
    MaterialLike,
    Particle,
    ParticleLike,
    PotionEffectLike,
    ProjectileFamily,
    ProjectileFamilyLike,
    ProjectileKindLike,
    ParticlePhysicsCollisionMode,
    ParticlePhysicsCollisionModeLike,
    SoundLike,
    TargetAnchor,
    coerce_combat_cooldown_scope,
    coerce_combat_event_type,
    coerce_combat_origin_bind,
    coerce_combat_target_bind,
    coerce_damage_policy,
    coerce_damage_type,
    coerce_damage_cause,
    coerce_heal_type,
    coerce_affliction_refresh_policy,
    coerce_affliction_audience,
    coerce_enum,
    coerce_easing,
    coerce_at_mode,
    coerce_anchor_mode,
    coerce_anchor_point,
    coerce_motion_mode,
    coerce_material,
    coerce_particle,
    coerce_potion_effect,
    coerce_projectile_family,
    coerce_projectile_kind,
    coerce_particle_physics_collision_mode,
    coerce_sound,
)


def _forbid_plain_string(value: Any, *, field: str) -> None:
    if type(value) is str:
        raise ValueError(
            f"{field}: plain string tokens are forbidden in typed APIs; use enum or custom_* token"
        )


class ActionSpec(ABC):
    @abstractmethod
    def to_dict(self) -> dict[str, Any]:
        raise NotImplementedError


class RequirementSpec(ABC):
    @abstractmethod
    def to_dict(self) -> dict[str, Any]:
        raise NotImplementedError


class CostSpec(ABC):
    @abstractmethod
    def to_dict(self) -> dict[str, Any]:
        raise NotImplementedError


# Back-compat type names for callers that import Action/Requirement/Cost.
Action = ActionSpec
Requirement = RequirementSpec
Cost = CostSpec


class TargeterSpec(ABC):
    @abstractmethod
    def to_dict(self) -> dict[str, Any]:
        raise NotImplementedError


@dataclass(frozen=True)
class SelfTargeter(TargeterSpec):
    def to_dict(self) -> dict[str, Any]:
        return {"type": "self"}


@dataclass(frozen=True)
class SphereTargeter(TargeterSpec):
    radius: float
    ignore_caster: bool = True

    def to_dict(self) -> dict[str, Any]:
        if self.radius <= 0.0:
            raise ValueError("effects.targeter.sphere.radius: must be > 0")
        return {
            "type": "sphere",
            "radius": float(self.radius),
            "ignoreCaster": bool(self.ignore_caster),
        }


@dataclass(frozen=True)
class ContextTargeter(TargeterSpec):
    key: str = "mob_target"

    def to_dict(self) -> dict[str, Any]:
        token = self.key.strip()
        if not token:
            raise ValueError("effects.targeter.context.key: cannot be empty")
        return {
            "type": "context_target",
            "key": token,
        }


@dataclass(frozen=True)
class LookRayTargeter(TargeterSpec):
    max_distance: float = 24.0

    def to_dict(self) -> dict[str, Any]:
        if self.max_distance <= 0.0:
            raise ValueError("effects.targeter.look_ray.max_distance: must be > 0")
        return {
            "type": "look_ray",
            "maxDistance": float(self.max_distance),
        }


@dataclass(frozen=True)
class EventFiltersSpec:
    payload: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return dict(self.payload)


@dataclass(frozen=True)
class ProjectileFiltersSpec(EventFiltersSpec):
    projectile_types: Sequence[str] | None = None
    projectile_family: ProjectileFamilyLike | None = None
    projectile_kind: ProjectileKindLike | None = None
    distance_min: float | None = None
    distance_max: float | None = None
    speed_min: float | None = None
    speed_max: float | None = None
    draw_force_min: float | None = None
    draw_force_max: float | None = None
    in_ground_ticks_min: int | None = None
    in_ground_ticks_max: int | None = None
    is_critical: bool | None = None
    is_charged: bool | None = None
    is_piercing: bool | None = None
    shot_from_crossbow: bool | None = None
    shooter_is_player: bool | None = None
    hit_block_materials: Sequence[MaterialLike] | None = None
    hit_block_tags: Sequence[str] | None = None

    def to_dict(self) -> dict[str, Any]:
        out = dict(self.payload)
        if self.projectile_types:
            normalized = [str(entry).strip().upper() for entry in self.projectile_types if str(entry).strip()]
            if normalized:
                out["projectileType"] = list(dict.fromkeys(normalized))
        if self.projectile_family is not None:
            _forbid_plain_string(self.projectile_family, field="effects.filters.projectile_family")
            out["projectileFamily"] = [
                coerce_projectile_family(
                    self.projectile_family,
                    field="effects.filters.projectile_family",
                )
            ]
        if self.projectile_kind is not None:
            _forbid_plain_string(self.projectile_kind, field="effects.filters.projectile_kind")
            out["projectileKind"] = [
                coerce_projectile_kind(
                    self.projectile_kind,
                    field="effects.filters.projectile_kind",
                )
            ]
        if self.distance_min is not None:
            out["distanceMin"] = float(self.distance_min)
        if self.distance_max is not None:
            out["distanceMax"] = float(self.distance_max)
        if self.speed_min is not None:
            out["speedMin"] = float(self.speed_min)
        if self.speed_max is not None:
            out["speedMax"] = float(self.speed_max)
        if self.draw_force_min is not None:
            out["drawForceMin"] = float(self.draw_force_min)
        if self.draw_force_max is not None:
            out["drawForceMax"] = float(self.draw_force_max)
        if self.in_ground_ticks_min is not None:
            out["inGroundTicksMin"] = int(self.in_ground_ticks_min)
        if self.in_ground_ticks_max is not None:
            out["inGroundTicksMax"] = int(self.in_ground_ticks_max)
        if self.is_critical is not None:
            out["isCritical"] = bool(self.is_critical)
        if self.is_charged is not None:
            out["isCharged"] = bool(self.is_charged)
        if self.is_piercing is not None:
            out["isPiercing"] = bool(self.is_piercing)
        if self.shot_from_crossbow is not None:
            out["shotFromCrossbow"] = bool(self.shot_from_crossbow)
        if self.shooter_is_player is not None:
            out["shooterIsPlayer"] = bool(self.shooter_is_player)
        if self.hit_block_materials:
            normalized_materials = []
            for entry in self.hit_block_materials:
                _forbid_plain_string(entry, field="effects.filters.hit_block_materials")
                normalized_materials.append(
                    coerce_material(entry, field="effects.filters.hit_block_materials")
                )
            out["hitBlockMaterial"] = list(dict.fromkeys(normalized_materials))
        if self.hit_block_tags:
            normalized_tags = [str(entry).strip().lower() for entry in self.hit_block_tags if str(entry).strip()]
            if normalized_tags:
                out["hitBlockTag"] = list(dict.fromkeys(normalized_tags))
        return out


@dataclass(frozen=True)
class SequenceAction(ActionSpec):
    actions: Sequence[ActionSpec] = field(default_factory=tuple)

    def to_dict(self) -> dict[str, Any]:
        return {
            "type": "sequence",
            "actions": [entry.to_dict() for entry in self.actions],
        }


@dataclass(frozen=True)
class AnimateActionSpec(ActionSpec):
    action: ActionSpec
    duration_ticks: int = 40
    period_ticks: int = 1
    follow_caster: bool = True
    easing: EasingLike = Easing.IN_OUT_CUBIC

    def to_dict(self) -> dict[str, Any]:
        if int(self.duration_ticks) <= 0:
            raise ValueError("effects.animate.duration_ticks: must be > 0")
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.animate.period_ticks: must be > 0")
        _forbid_plain_string(self.easing, field="effects.animate.easing")
        return {
            "type": "animate",
            "durationTicks": int(self.duration_ticks),
            "periodTicks": int(self.period_ticks),
            "followCaster": bool(self.follow_caster),
            "easing": coerce_easing(self.easing, field="effects.animate.easing"),
            "action": self.action.to_dict(),
        }


@dataclass(frozen=True)
class StateMachineActionSpec(ActionSpec):
    charge_ticks: int = 20
    sustain_ticks: int = 40
    release_ticks: int = 20
    period_ticks: int = 1
    follow_caster: bool = True
    easing: EasingLike = Easing.IN_OUT_CUBIC
    charge: ActionSpec | None = None
    sustain: ActionSpec | None = None
    release: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.state_machine.period_ticks: must be > 0")
        if int(self.charge_ticks) < 0 or int(self.sustain_ticks) < 0 or int(self.release_ticks) < 0:
            raise ValueError("effects.state_machine.*_ticks: must be >= 0")
        if self.charge is None and self.sustain is None and self.release is None:
            raise ValueError("effects.state_machine: at least one of charge/sustain/release is required")
        _forbid_plain_string(self.easing, field="effects.state_machine.easing")
        payload: dict[str, Any] = {
            "type": "state_machine",
            "chargeTicks": int(self.charge_ticks),
            "sustainTicks": int(self.sustain_ticks),
            "releaseTicks": int(self.release_ticks),
            "periodTicks": int(self.period_ticks),
            "followCaster": bool(self.follow_caster),
            "easing": coerce_easing(self.easing, field="effects.state_machine.easing"),
        }
        if self.charge is not None:
            payload["charge"] = self.charge.to_dict()
        if self.sustain is not None:
            payload["sustain"] = self.sustain.to_dict()
        if self.release is not None:
            payload["release"] = self.release.to_dict()
        return payload


@dataclass(frozen=True)
class BurstActionSpec(ActionSpec):
    action: ActionSpec
    times: int = 6
    spacing_ticks: int = 0
    delay_ticks: int = 0

    def to_dict(self) -> dict[str, Any]:
        if int(self.times) <= 0:
            raise ValueError("effects.burst.times: must be > 0")
        if int(self.spacing_ticks) < 0 or int(self.delay_ticks) < 0:
            raise ValueError("effects.burst.spacing_ticks/delay_ticks: must be >= 0")
        return {
            "type": "burst",
            "times": int(self.times),
            "spacingTicks": int(self.spacing_ticks),
            "delayTicks": int(self.delay_ticks),
            "action": self.action.to_dict(),
        }


@dataclass(frozen=True)
class PulseActionSpec(ActionSpec):
    action: ActionSpec
    duration_ticks: int = 60
    period_ticks: int = 10
    follow_caster: bool = False
    easing: EasingLike = Easing.IN_OUT_CUBIC

    def to_dict(self) -> dict[str, Any]:
        if int(self.duration_ticks) <= 0:
            raise ValueError("effects.pulse.duration_ticks: must be > 0")
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.pulse.period_ticks: must be > 0")
        _forbid_plain_string(self.easing, field="effects.pulse.easing")
        return {
            "type": "pulse",
            "durationTicks": int(self.duration_ticks),
            "periodTicks": int(self.period_ticks),
            "followCaster": bool(self.follow_caster),
            "easing": coerce_easing(self.easing, field="effects.pulse.easing"),
            "action": self.action.to_dict(),
        }


@dataclass(frozen=True)
class LoopActionSpec(ActionSpec):
    action: ActionSpec
    duration_ticks: int = 60
    period_ticks: int = 10
    follow_caster: bool = False
    easing: EasingLike = Easing.IN_OUT_CUBIC

    def to_dict(self) -> dict[str, Any]:
        if int(self.duration_ticks) <= 0:
            raise ValueError("effects.loop.duration_ticks: must be > 0")
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.loop.period_ticks: must be > 0")
        _forbid_plain_string(self.easing, field="effects.loop.easing")
        return {
            "type": "loop",
            "durationTicks": int(self.duration_ticks),
            "periodTicks": int(self.period_ticks),
            "followCaster": bool(self.follow_caster),
            "easing": coerce_easing(self.easing, field="effects.loop.easing"),
            "action": self.action.to_dict(),
        }


@dataclass(frozen=True)
class TrailActionSpec(ActionSpec):
    action: ActionSpec
    duration_ticks: int = 60
    period_ticks: int = 2
    follow_caster: bool = True
    easing: EasingLike = Easing.IN_OUT_CUBIC

    def to_dict(self) -> dict[str, Any]:
        if int(self.duration_ticks) <= 0:
            raise ValueError("effects.trail.duration_ticks: must be > 0")
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.trail.period_ticks: must be > 0")
        _forbid_plain_string(self.easing, field="effects.trail.easing")
        return {
            "type": "trail",
            "durationTicks": int(self.duration_ticks),
            "periodTicks": int(self.period_ticks),
            "followCaster": bool(self.follow_caster),
            "easing": coerce_easing(self.easing, field="effects.trail.easing"),
            "action": self.action.to_dict(),
        }


@dataclass(frozen=True)
class AttachActionSpec(ActionSpec):
    action: ActionSpec
    anchor: AnchorModeLike = AnchorMode.CASTER
    point: AnchorPointLike | None = None
    forward: float = 0.0
    right: float = 0.0
    up: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.anchor, field="effects.attach.anchor")
        payload: dict[str, Any] = {
            "type": "attach",
            "anchor": coerce_anchor_mode(self.anchor, field="effects.attach.anchor"),
            "forward": float(self.forward),
            "right": float(self.right),
            "up": float(self.up),
            "action": self.action.to_dict(),
        }
        point = coerce_anchor_point(self.point, field="effects.attach.point")
        if point is not None:
            payload["point"] = point
        return payload


@dataclass(frozen=True)
class FollowActionSpec(ActionSpec):
    action: ActionSpec
    anchor: AnchorModeLike = AnchorMode.CASTER
    point: AnchorPointLike | None = None
    duration_ticks: int = 60
    period_ticks: int = 2
    smoothing: float = 1.0
    forward: float = 0.0
    right: float = 0.0
    up: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        if int(self.duration_ticks) <= 0:
            raise ValueError("effects.follow.duration_ticks: must be > 0")
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.follow.period_ticks: must be > 0")
        _forbid_plain_string(self.anchor, field="effects.follow.anchor")
        payload: dict[str, Any] = {
            "type": "follow",
            "anchor": coerce_anchor_mode(self.anchor, field="effects.follow.anchor"),
            "durationTicks": int(self.duration_ticks),
            "periodTicks": int(self.period_ticks),
            "smoothing": float(self.smoothing),
            "forward": float(self.forward),
            "right": float(self.right),
            "up": float(self.up),
            "action": self.action.to_dict(),
        }
        point = coerce_anchor_point(self.point, field="effects.follow.point")
        if point is not None:
            payload["point"] = point
        return payload


@dataclass(frozen=True)
class MotionActionSpec(ActionSpec):
    action: ActionSpec
    mode: MotionModeLike = MotionMode.TRANSLATE
    at: AtModeLike = AtMode.ORIGIN
    duration_ticks: int = 40
    period_ticks: int = 1
    follow_caster: bool = True
    easing: EasingLike = Easing.IN_OUT_CUBIC
    velocity_x: float = 0.0
    velocity_y: float = 0.0
    velocity_z: float = 0.0
    radius: float = 0.0
    turns: float = 1.0
    vertical: float = 0.0
    drift: float = 0.0
    drift_vertical: float = 0.0
    drift_speed: float = 0.35
    forward: float = 0.0
    right: float = 0.0
    up: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        if int(self.duration_ticks) <= 0:
            raise ValueError("effects.motion.duration_ticks: must be > 0")
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.motion.period_ticks: must be > 0")
        _forbid_plain_string(self.mode, field="effects.motion.mode")
        _forbid_plain_string(self.at, field="effects.motion.at")
        _forbid_plain_string(self.easing, field="effects.motion.easing")
        return {
            "type": "motion",
            "durationTicks": int(self.duration_ticks),
            "periodTicks": int(self.period_ticks),
            "followCaster": bool(self.follow_caster),
            "easing": coerce_easing(self.easing, field="effects.motion.easing"),
            "mode": coerce_motion_mode(self.mode, field="effects.motion.mode"),
            "velocityX": float(self.velocity_x),
            "velocityY": float(self.velocity_y),
            "velocityZ": float(self.velocity_z),
            "radius": float(self.radius),
            "turns": float(self.turns),
            "vertical": float(self.vertical),
            "drift": float(self.drift),
            "driftVertical": float(self.drift_vertical),
            "driftSpeed": float(self.drift_speed),
            "forward": float(self.forward),
            "right": float(self.right),
            "up": float(self.up),
            "at": coerce_at_mode(self.at, field="effects.motion.at"),
            "action": self.action.to_dict(),
        }


@dataclass(frozen=True)
class AnimateRealtimeActionSpec(ActionSpec):
    action: ActionSpec
    duration_millis: int = 1000
    period_millis: int = 50
    follow_caster: bool = True
    easing: EasingLike = Easing.IN_OUT_CUBIC

    def to_dict(self) -> dict[str, Any]:
        if int(self.duration_millis) <= 0:
            raise ValueError("effects.animate_realtime.duration_millis: must be > 0")
        if int(self.period_millis) <= 0:
            raise ValueError("effects.animate_realtime.period_millis: must be > 0")
        _forbid_plain_string(self.easing, field="effects.animate_realtime.easing")
        return {
            "type": "animate_realtime",
            "durationMillis": int(self.duration_millis),
            "periodMillis": int(self.period_millis),
            "followCaster": bool(self.follow_caster),
            "easing": coerce_easing(self.easing, field="effects.animate_realtime.easing"),
            "action": self.action.to_dict(),
        }


@dataclass(frozen=True)
class SoundAction(ActionSpec):
    sound: SoundLike
    volume: float = 1.0
    pitch: float = 1.0

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.sound, field="effects.sound.sound")
        return {
            "type": "sound",
            "sound": coerce_sound(self.sound, field="effects.sound.sound"),
            "volume": float(self.volume),
            "pitch": float(self.pitch),
        }


@dataclass(frozen=True)
class DustOptionsSpec:
    color: str
    size: float = 1.0

    def to_dict(self) -> dict[str, Any]:
        token = str(self.color).strip()
        if not token:
            raise ValueError("effects.dust.color: cannot be empty")
        if float(self.size) <= 0.0:
            raise ValueError("effects.dust.size: must be > 0")
        return {
            "color": token,
            "size": float(self.size),
        }


@dataclass(frozen=True)
class BlockDataSpec:
    material: MaterialLike
    states: dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.material, field="effects.particle_data.block.material")
        payload: dict[str, Any] = {
            "material": coerce_material(self.material, field="effects.particle_data.block.material"),
        }
        if self.states:
            payload["states"] = {str(k): str(v) for k, v in self.states.items()}
        return payload


@dataclass(frozen=True)
class ItemDataSpec:
    material: MaterialLike
    amount: int = 1

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.material, field="effects.particle_data.item.material")
        return {
            "material": coerce_material(self.material, field="effects.particle_data.item.material"),
            "amount": max(1, int(self.amount)),
        }


@dataclass(frozen=True)
class TrailDataSpec:
    color: str
    duration_ticks: int = 20
    target_at: TargetAnchor = TargetAnchor.ORIGIN

    def to_dict(self) -> dict[str, Any]:
        token = str(self.color).strip()
        if not token:
            raise ValueError("effects.particle_data.trail.color: cannot be empty")
        if int(self.duration_ticks) <= 0:
            raise ValueError("effects.particle_data.trail.duration_ticks: must be > 0")
        return {
            "color": token,
            "durationTicks": int(self.duration_ticks),
            "targetAt": coerce_enum(self.target_at, TargetAnchor, field="effects.particle_data.trail.target_at"),
        }


@dataclass(frozen=True)
class VibrationDataSpec:
    destination_at: TargetAnchor = TargetAnchor.ORIGIN
    arrival_ticks: int = 20
    prefer_entity: bool = True

    def to_dict(self) -> dict[str, Any]:
        if int(self.arrival_ticks) < 0:
            raise ValueError("effects.particle_data.vibration.arrival_ticks: must be >= 0")
        return {
            "destinationAt": coerce_enum(
                self.destination_at,
                TargetAnchor,
                field="effects.particle_data.vibration.destination_at",
            ),
            "arrivalTicks": int(self.arrival_ticks),
            "preferEntity": bool(self.prefer_entity),
        }


ParticleDataLike = DustOptionsSpec | BlockDataSpec | ItemDataSpec | TrailDataSpec | VibrationDataSpec | dict[str, Any]


@dataclass(frozen=True)
class Point3Spec:
    x: float = 0.0
    y: float = 0.0
    z: float = 0.0
    forward: float = 0.0
    right: float = 0.0
    up: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        return {
            "x": float(self.x),
            "y": float(self.y),
            "z": float(self.z),
            "forward": float(self.forward),
            "right": float(self.right),
            "up": float(self.up),
        }


@dataclass(frozen=True)
class PolylineSpec:
    points: Sequence[Point3Spec]

    def to_dict(self) -> list[dict[str, Any]]:
        if len(self.points) < 2:
            raise ValueError("effects.polyline.points: expected at least 2 points")
        return [p.to_dict() for p in self.points]


@dataclass(frozen=True)
class ControlPointsSpec:
    points: Sequence[Point3Spec]

    def to_dict(self) -> list[dict[str, Any]]:
        if len(self.points) < 2:
            raise ValueError("effects.control_points.points: expected at least 2 points")
        return [p.to_dict() for p in self.points]


@dataclass(frozen=True)
class TriangleSpec:
    a: Point3Spec
    b: Point3Spec
    c: Point3Spec

    def to_dict(self) -> list[dict[str, Any]]:
        return [self.a.to_dict(), self.b.to_dict(), self.c.to_dict()]


@dataclass(frozen=True)
class MeshSpec:
    triangles: Sequence[TriangleSpec]

    def to_dict(self) -> list[list[dict[str, Any]]]:
        if len(self.triangles) < 1:
            raise ValueError("effects.mesh.triangles: expected at least 1 triangle")
        return [tri.to_dict() for tri in self.triangles]


@dataclass(frozen=True)
class VelocitySpec:
    x: float = 0.0
    y: float = 0.2
    z: float = 0.0


@dataclass(frozen=True)
class PhysicsSpec:
    spread: float = 0.08
    gravity: float = 0.03
    drag: float = 0.02
    steps: int = 20
    period_ticks: int = 1
    collide: bool = False
    collision_mode: ParticlePhysicsCollisionModeLike = ParticlePhysicsCollisionMode.STOP
    restitution: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        if int(self.steps) <= 0:
            raise ValueError("effects.physics.steps: must be > 0")
        if int(self.period_ticks) <= 0:
            raise ValueError("effects.physics.period_ticks: must be > 0")
        _forbid_plain_string(self.collision_mode, field="effects.physics.collision_mode")
        return {
            "spread": float(self.spread),
            "gravity": float(self.gravity),
            "drag": float(self.drag),
            "steps": int(self.steps),
            "periodTicks": int(self.period_ticks),
            "collide": bool(self.collide),
            "collisionMode": coerce_particle_physics_collision_mode(
                self.collision_mode,
                field="effects.physics.collision_mode",
            ),
            "restitution": float(self.restitution),
        }


@dataclass(frozen=True)
class GradientSpec:
    start_color: str
    end_color: str
    size: float = 1.0

    def to_dict(self) -> dict[str, Any]:
        start = str(self.start_color).strip()
        end = str(self.end_color).strip()
        if not start or not end:
            raise ValueError("effects.gradient: start_color/end_color cannot be empty")
        if float(self.size) <= 0.0:
            raise ValueError("effects.gradient.size: must be > 0")
        return {
            "startColor": start,
            "endColor": end,
            "size": float(self.size),
        }


@dataclass(frozen=True)
class ParticleEmitSpec:
    count: int = 1
    offset: float = 0.0
    extra: float = 0.0
    at: TargetAnchor = TargetAnchor.ORIGIN
    forward: float = 0.0
    right: float = 0.0
    up: float = 0.0
    particle_data: ParticleDataLike | None = None

    def to_action_fields(self, *, field: str) -> dict[str, Any]:
        if int(self.count) <= 0:
            raise ValueError(f"{field}.count: must be > 0")
        return {
            "count": int(self.count),
            "offset": float(self.offset),
            "extra": float(self.extra),
            "at": coerce_enum(self.at, TargetAnchor, field=f"{field}.at"),
            "forward": float(self.forward),
            "right": float(self.right),
            "up": float(self.up),
            **_particle_data_payload(self.particle_data),
        }


def _particle_data_payload(data: ParticleDataLike | None) -> dict[str, Any]:
    if data is None:
        return {}
    if isinstance(data, dict):
        raise ValueError("typed particle helpers do not accept raw dict particle data; use typed specs")
    return {"data": data.to_dict()}


def _normalize_tags(tags: Sequence[str] | None) -> list[str]:
    if not tags:
        return []
    out: list[str] = []
    for raw in tags:
        token = str(raw).strip()
        if token:
            out.append(token)
    return list(dict.fromkeys(out))


@dataclass(frozen=True)
class ParticlesActionSpec(ActionSpec):
    action_type: ActionType
    particle: ParticleLike
    params: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.particle, field=f"effects.{self.action_type.value}.particle")
        payload = {
            "type": self.action_type.value,
            "particle": coerce_particle(self.particle, field=f"effects.{self.action_type.value}.particle"),
        }
        payload.update(self.params)
        return payload


@dataclass(frozen=True)
class SphereShellAction(ActionSpec):
    particle: ParticleLike
    radius: float
    points: int
    count: int = 1
    offset: float = 0.0
    extra: float = 0.0
    at: TargetAnchor = TargetAnchor.ORIGIN
    dust: DustOptionsSpec | None = None
    scale: float = 1.0

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.particle, field="effects.sphere_shell.particle")
        if float(self.scale) <= 0.0:
            raise ValueError("effects.sphere_shell.scale: must be > 0")
        scaled_radius = float(self.radius) * float(self.scale)
        scaled_points = max(1, int(round(float(self.points) * float(self.scale))))
        scaled_count = max(1, int(round(float(self.count) * float(self.scale))))
        scaled_offset = float(self.offset) * float(self.scale)
        payload = {
            "type": "particles_sphere_shell",
            "particle": coerce_particle(self.particle, field="effects.sphere_shell.particle"),
            "radius": scaled_radius,
            "points": scaled_points,
            "count": scaled_count,
            "offset": scaled_offset,
            "extra": float(self.extra),
            "at": coerce_enum(self.at, TargetAnchor, field="effects.sphere_shell.at"),
        }
        if self.dust is not None:
            particle_token = coerce_particle(self.particle, field="effects.sphere_shell.particle")
            if particle_token != Particle.DUST.value:
                raise ValueError("effects.sphere_shell.dust: dust data can only be used with Particle.DUST")
            dust_data = self.dust.to_dict()
            if float(self.scale) != 1.0:
                dust_data["size"] = float(dust_data.get("size", 1.0)) * float(self.scale)
            payload["data"] = dust_data
        return payload


@dataclass(frozen=True)
class DamageAction(ActionSpec):
    amount: float
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage.policy")
        return {
            "type": "damage",
            "amount": float(self.amount),
            "policy": coerce_damage_policy(self.policy, field="effects.damage.policy"),
        }


@dataclass(frozen=True)
class HealAction(ActionSpec):
    amount: float
    policy: DamagePolicyLike = DamagePolicy.ALWAYS

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.heal.policy")
        return {
            "type": "heal",
            "amount": float(self.amount),
            "policy": coerce_damage_policy(self.policy, field="effects.heal.policy"),
        }


@dataclass(frozen=True)
class DamageTemplateSpec:
    amount: float
    damage_type: DamageTypeLike = DamageType.PHYSICAL
    damage_cause: DamageCauseLike = DamageCause.DIRECT
    ignore_resistance: bool = False
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)
    cap: float = 0.0
    max_percent: float = 0.0
    armor_pen_flat: float = 0.0
    armor_pen_pct: float = 0.0
    resist_pen_pct: float = 0.0
    crit_chance: float = 0.0
    crit_multiplier: float = 1.5
    min_damage_floor: float = 0.0
    vulnerability_tag: str | None = None
    mitigation_profile: str | None = None
    pipeline_tags: Sequence[str] = field(default_factory=tuple)
    snapshot_at_cast: bool = False

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.damage_type, field="effects.damage_template.damage_type")
        _forbid_plain_string(self.damage_cause, field="effects.damage_template.damage_cause")
        _forbid_plain_string(self.policy, field="effects.damage_template.policy")
        payload: dict[str, Any] = {
            "amount": float(self.amount),
            "damageType": coerce_damage_type(self.damage_type, field="effects.damage_template.damage_type"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_template.damage_cause"),
            "ignoreResistance": bool(self.ignore_resistance),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_template.policy"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        if float(self.cap) > 0.0:
            payload["cap"] = float(self.cap)
        if float(self.max_percent) > 0.0:
            payload["maxPercent"] = float(self.max_percent)
        if float(self.armor_pen_flat) != 0.0:
            payload["armorPenFlat"] = float(self.armor_pen_flat)
        if float(self.armor_pen_pct) != 0.0:
            payload["armorPenPct"] = float(self.armor_pen_pct)
        if float(self.resist_pen_pct) != 0.0:
            payload["resistPenPct"] = float(self.resist_pen_pct)
        if float(self.crit_chance) != 0.0:
            payload["critChance"] = float(self.crit_chance)
        if float(self.crit_multiplier) != 1.5:
            payload["critMultiplier"] = float(self.crit_multiplier)
        if float(self.min_damage_floor) != 0.0:
            payload["minDamageFloor"] = float(self.min_damage_floor)
        if self.vulnerability_tag:
            payload["vulnerabilityTag"] = str(self.vulnerability_tag).strip()
        if self.mitigation_profile:
            payload["mitigationProfile"] = str(self.mitigation_profile).strip()
        pipeline_tags = _normalize_tags(self.pipeline_tags)
        if pipeline_tags:
            payload["pipelineTags"] = pipeline_tags
        if self.snapshot_at_cast:
            payload["snapshotAtCast"] = True
        return payload


@dataclass(frozen=True)
class DamageTypedAction(ActionSpec):
    amount: float
    damage_type: DamageTypeLike
    ignore_resistance: bool = False
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.DIRECT
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.damage_type, field="effects.damage_typed.damage_type")
        _forbid_plain_string(self.policy, field="effects.damage_typed.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_typed.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_typed",
            "amount": float(self.amount),
            "damageType": coerce_damage_type(self.damage_type, field="effects.damage_typed.damage_type"),
            "ignoreResistance": bool(self.ignore_resistance),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_typed.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_typed.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        return payload


@dataclass(frozen=True)
class SetResistanceAction(ActionSpec):
    damage_type: DamageTypeLike
    multiplier: float
    duration_ticks: int = 0

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.damage_type, field="effects.set_resistance.damage_type")
        payload: dict[str, Any] = {
            "type": "set_resistance",
            "damageType": coerce_damage_type(self.damage_type, field="effects.set_resistance.damage_type"),
            "multiplier": float(self.multiplier),
        }
        if int(self.duration_ticks) > 0:
            payload["durationTicks"] = int(self.duration_ticks)
        return payload


@dataclass(frozen=True)
class AddResistanceAction(ActionSpec):
    damage_type: DamageTypeLike
    delta: float
    duration_ticks: int = 0

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.damage_type, field="effects.add_resistance.damage_type")
        payload: dict[str, Any] = {
            "type": "add_resistance",
            "damageType": coerce_damage_type(self.damage_type, field="effects.add_resistance.damage_type"),
            "delta": float(self.delta),
        }
        if int(self.duration_ticks) > 0:
            payload["durationTicks"] = int(self.duration_ticks)
        return payload


@dataclass(frozen=True)
class ClearResistanceAction(ActionSpec):
    damage_type: DamageTypeLike | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {"type": "clear_resistance"}
        if self.damage_type is not None:
            _forbid_plain_string(self.damage_type, field="effects.clear_resistance.damage_type")
            payload["damageType"] = coerce_damage_type(self.damage_type, field="effects.clear_resistance.damage_type")
        return payload


@dataclass(frozen=True)
class SetReflectAction(ActionSpec):
    ratio: float = 0.25
    flat: float = 0.0
    ignore_resistance: bool = False
    damage_type: DamageTypeLike | None = None
    duration_ticks: int = 0
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.set_reflect.policy")
        payload: dict[str, Any] = {
            "type": "set_reflect",
            "ratio": float(self.ratio),
            "flat": float(self.flat),
            "ignoreResistance": bool(self.ignore_resistance),
            "policy": coerce_damage_policy(self.policy, field="effects.set_reflect.policy"),
        }
        if self.damage_type is not None:
            _forbid_plain_string(self.damage_type, field="effects.set_reflect.damage_type")
            payload["damageType"] = coerce_damage_type(self.damage_type, field="effects.set_reflect.damage_type")
        if int(self.duration_ticks) > 0:
            payload["durationTicks"] = int(self.duration_ticks)
        return payload


@dataclass(frozen=True)
class ClearReflectAction(ActionSpec):
    def to_dict(self) -> dict[str, Any]:
        return {"type": "clear_reflect"}


@dataclass(frozen=True)
class DamagePercentAction(ActionSpec):
    percent: float
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.PERCENT
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage_percent.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_percent.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_percent",
            "percent": float(self.percent),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_percent.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_percent.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        return payload


@dataclass(frozen=True)
class DamageTrueAction(ActionSpec):
    amount: float
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.TRUE
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage_true.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_true.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_true",
            "amount": float(self.amount),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_true.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_true.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        return payload


@dataclass(frozen=True)
class DamageFalloffAction(ActionSpec):
    amount: float
    max_distance: float
    min_multiplier: float
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.FALLOFF
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage_falloff.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_falloff.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_falloff",
            "amount": float(self.amount),
            "maxDistance": float(self.max_distance),
            "minMultiplier": float(self.min_multiplier),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_falloff.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_falloff.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        return payload


@dataclass(frozen=True)
class DamageCritAction(ActionSpec):
    amount: float
    crit_chance: float
    crit_multiplier: float = 1.5
    headshot_multiplier: float = 1.0
    headshot_threshold: float = 0.15
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.CRIT
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage_crit.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_crit.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_crit",
            "amount": float(self.amount),
            "critChance": float(self.crit_chance),
            "critMultiplier": float(self.crit_multiplier),
            "headshotMultiplier": float(self.headshot_multiplier),
            "headshotThreshold": float(self.headshot_threshold),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_crit.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_crit.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        return payload


@dataclass(frozen=True)
class DamageLifestealAction(ActionSpec):
    amount: float
    ratio: float
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.LIFESTEAL
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage_lifesteal.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_lifesteal.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_lifesteal",
            "amount": float(self.amount),
            "ratio": float(self.ratio),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_lifesteal.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_lifesteal.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        return payload


@dataclass(frozen=True)
class DamageDotAction(ActionSpec):
    amount: float
    period_ticks: int
    times: int
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.DOT
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)
    on_tick: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage_dot.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_dot.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_dot",
            "amount": float(self.amount),
            "periodTicks": int(self.period_ticks),
            "times": int(self.times),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_dot.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_dot.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        if self.on_tick is not None:
            payload["onTick"] = self.on_tick.to_dict()
        return payload


@dataclass(frozen=True)
class DamageChainAction(ActionSpec):
    amount: float
    radius: float
    max_jumps: int
    delay_ticks: int = 0
    falloff: float = 0.85
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT
    damage_cause: DamageCauseLike = DamageCause.CHAIN
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)
    on_hit: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.damage_chain.policy")
        _forbid_plain_string(self.damage_cause, field="effects.damage_chain.damage_cause")
        payload: dict[str, Any] = {
            "type": "damage_chain",
            "amount": float(self.amount),
            "radius": float(self.radius),
            "maxJumps": int(self.max_jumps),
            "delayTicks": int(self.delay_ticks),
            "falloff": float(self.falloff),
            "policy": coerce_damage_policy(self.policy, field="effects.damage_chain.policy"),
            "damageCause": coerce_damage_cause(self.damage_cause, field="effects.damage_chain.damage_cause"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        if self.on_hit is not None:
            payload["onHit"] = self.on_hit.to_dict()
        return payload


@dataclass(frozen=True)
class GroundDamageAction(ActionSpec):
    radius: float
    damage: DamageTemplateSpec
    max_drop: float = 6.0
    ignore_caster: bool = True
    on_hit: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "type": "ground_damage",
            "radius": float(self.radius),
            "maxDrop": float(self.max_drop),
            "ignoreCaster": bool(self.ignore_caster),
            "damage": self.damage.to_dict(),
        }
        if self.on_hit is not None:
            payload["onHit"] = self.on_hit.to_dict()
        return payload


@dataclass(frozen=True)
class HealPercentAction(ActionSpec):
    percent: float
    policy: DamagePolicyLike = DamagePolicy.ALWAYS
    heal_type: HealTypeLike = HealType.DIRECT
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)
    cap: float = 0.0
    overheal_to_shield: bool = False
    shield_cap: float = 0.0
    shield_decay_ticks: int = 0

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.heal_percent.policy")
        _forbid_plain_string(self.heal_type, field="effects.heal_percent.heal_type")
        payload: dict[str, Any] = {
            "type": "heal_percent",
            "percent": float(self.percent),
            "policy": coerce_damage_policy(self.policy, field="effects.heal_percent.policy"),
            "healType": coerce_heal_type(self.heal_type, field="effects.heal_percent.heal_type"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        if float(self.cap) > 0.0:
            payload["cap"] = float(self.cap)
        if self.overheal_to_shield:
            payload["overhealToShield"] = True
            if float(self.shield_cap) > 0.0:
                payload["shieldCap"] = float(self.shield_cap)
            if int(self.shield_decay_ticks) > 0:
                payload["shieldDecayTicks"] = int(self.shield_decay_ticks)
        return payload


@dataclass(frozen=True)
class HealOverTimeAction(ActionSpec):
    amount: float
    period_ticks: int
    times: int
    policy: DamagePolicyLike = DamagePolicy.ALWAYS
    heal_type: HealTypeLike = HealType.HOT
    source: str | None = None
    tags: Sequence[str] = field(default_factory=tuple)
    cap: float = 0.0
    overheal_to_shield: bool = False
    shield_cap: float = 0.0
    shield_decay_ticks: int = 0
    on_tick: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.heal_over_time.policy")
        _forbid_plain_string(self.heal_type, field="effects.heal_over_time.heal_type")
        payload: dict[str, Any] = {
            "type": "heal_over_time",
            "amount": float(self.amount),
            "periodTicks": int(self.period_ticks),
            "times": int(self.times),
            "policy": coerce_damage_policy(self.policy, field="effects.heal_over_time.policy"),
            "healType": coerce_heal_type(self.heal_type, field="effects.heal_over_time.heal_type"),
        }
        if self.source:
            payload["source"] = str(self.source).strip()
        tags = _normalize_tags(self.tags)
        if tags:
            payload["tags"] = tags
        if float(self.cap) > 0.0:
            payload["cap"] = float(self.cap)
        if self.overheal_to_shield:
            payload["overhealToShield"] = True
            if float(self.shield_cap) > 0.0:
                payload["shieldCap"] = float(self.shield_cap)
            if int(self.shield_decay_ticks) > 0:
                payload["shieldDecayTicks"] = int(self.shield_decay_ticks)
        if self.on_tick is not None:
            payload["onTick"] = self.on_tick.to_dict()
        return payload


@dataclass(frozen=True)
class ShieldAction(ActionSpec):
    amount: float
    cap: float = 0.0
    decay_ticks: int = 0
    policy: DamagePolicyLike = DamagePolicy.ALWAYS

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.shield.policy")
        payload: dict[str, Any] = {
            "type": "shield",
            "amount": float(self.amount),
            "cap": float(self.cap),
            "decayTicks": int(self.decay_ticks),
            "policy": coerce_damage_policy(self.policy, field="effects.shield.policy"),
        }
        return payload


@dataclass(frozen=True)
class AbsorbAction(ActionSpec):
    amount: float
    cap: float = 0.0
    decay_ticks: int = 0
    policy: DamagePolicyLike = DamagePolicy.ALWAYS

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.absorb.policy")
        payload: dict[str, Any] = {
            "type": "absorb",
            "amount": float(self.amount),
            "cap": float(self.cap),
            "decayTicks": int(self.decay_ticks),
            "policy": coerce_damage_policy(self.policy, field="effects.absorb.policy"),
        }
        return payload


@dataclass(frozen=True)
class TotemAction(ActionSpec):
    def to_dict(self) -> dict[str, Any]:
        return {"type": "totem"}


@dataclass(frozen=True)
class KnockbackAction(ActionSpec):
    horizontal: float = 1.0
    vertical: float = 0.35

    def to_dict(self) -> dict[str, Any]:
        return {
            "type": "knockback",
            "horizontal": float(self.horizontal),
            "vertical": float(self.vertical),
        }


@dataclass(frozen=True)
class PullAction(ActionSpec):
    horizontal: float = 0.75
    vertical: float = 0.08

    def to_dict(self) -> dict[str, Any]:
        return {
            "type": "pull",
            "horizontal": float(self.horizontal),
            "vertical": float(self.vertical),
        }


@dataclass(frozen=True)
class AfflictApplyAction(ActionSpec):
    affliction_id: str
    stacks: int = 1
    max_stacks: int = 5
    duration_ticks: int = 100
    refresh_policy: AfflictionRefreshPolicyLike = AfflictionRefreshPolicy.RESET_DURATION
    audience: AfflictionAudienceLike = AfflictionAudience.PVE_ONLY
    tick_every_ticks: int = 0
    on_tick: ActionSpec | None = None
    on_apply: ActionSpec | None = None
    on_stack: ActionSpec | None = None
    on_expire: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        token = str(self.affliction_id).strip().lower()
        if not token:
            raise ValueError("effects.afflict_apply.id: cannot be empty")
        _forbid_plain_string(self.refresh_policy, field="effects.afflict_apply.refresh_policy")
        _forbid_plain_string(self.audience, field="effects.afflict_apply.audience")
        payload: dict[str, Any] = {
            "type": "afflict_apply",
            "id": token,
            "stacks": max(1, int(self.stacks)),
            "maxStacks": max(1, int(self.max_stacks)),
            "durationTicks": max(1, int(self.duration_ticks)),
            "refreshPolicy": coerce_affliction_refresh_policy(
                self.refresh_policy,
                field="effects.afflict_apply.refresh_policy",
            ),
            "audience": coerce_affliction_audience(
                self.audience,
                field="effects.afflict_apply.audience",
            ),
        }
        if int(self.tick_every_ticks) > 0:
            payload["tickEveryTicks"] = int(self.tick_every_ticks)
        if self.on_tick is not None:
            payload["onTick"] = self.on_tick.to_dict()
        if self.on_apply is not None:
            payload["onApply"] = self.on_apply.to_dict()
        if self.on_stack is not None:
            payload["onStack"] = self.on_stack.to_dict()
        if self.on_expire is not None:
            payload["onExpire"] = self.on_expire.to_dict()
        return payload


@dataclass(frozen=True)
class AfflictClearAction(ActionSpec):
    affliction_id: str | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {"type": "afflict_clear"}
        if self.affliction_id is not None:
            token = str(self.affliction_id).strip().lower()
            if not token:
                raise ValueError("effects.afflict_clear.id: cannot be empty")
            payload["id"] = token
        return payload


@dataclass(frozen=True)
class AfflictConsumeAction(ActionSpec):
    affliction_id: str
    stacks: int = 1
    require_at_least: int = 1
    on_success: ActionSpec | None = None
    on_failure: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        token = str(self.affliction_id).strip().lower()
        if not token:
            raise ValueError("effects.afflict_consume.id: cannot be empty")
        payload: dict[str, Any] = {
            "type": "afflict_consume",
            "id": token,
            "stacks": max(1, int(self.stacks)),
            "requireAtLeast": max(1, int(self.require_at_least)),
        }
        if self.on_success is not None:
            payload["onSuccess"] = self.on_success.to_dict()
        if self.on_failure is not None:
            payload["onFailure"] = self.on_failure.to_dict()
        return payload


@dataclass(frozen=True)
class ChanceAction(ActionSpec):
    probability: float
    then_action: ActionSpec
    otherwise_action: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "type": "chance",
            "probability": float(self.probability),
            "then": self.then_action.to_dict(),
        }
        if self.otherwise_action is not None:
            payload["otherwise"] = self.otherwise_action.to_dict()
        return payload


@dataclass(frozen=True)
class DebugLogAction(ActionSpec):
    message: str

    def to_dict(self) -> dict[str, Any]:
        token = str(self.message)
        if not token.strip():
            raise ValueError("effects.debug_log.message: cannot be empty")
        return {
            "type": "debug_log",
            "message": token,
        }


@dataclass(frozen=True)
class RaycastHitEntityAction(ActionSpec):
    then_action: ActionSpec
    max_distance: float = 20.0
    ray_size: float = 0.35
    stop_on_block: bool = True
    ignore_caster: bool = True
    damage: DamageTemplateSpec | None = None
    otherwise_action: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "type": "raycast_hit_entity",
            "maxDistance": float(self.max_distance),
            "raySize": float(self.ray_size),
            "stopOnBlock": bool(self.stop_on_block),
            "ignoreCaster": bool(self.ignore_caster),
            "then": self.then_action.to_dict(),
        }
        if self.damage is not None:
            payload["damage"] = self.damage.to_dict()
        if self.otherwise_action is not None:
            payload["otherwise"] = self.otherwise_action.to_dict()
        return payload


@dataclass(frozen=True)
class PresetActionSpec(ActionSpec):
    action_type: ActionType
    particle: ParticleLike
    params: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.particle, field=f"effects.{self.action_type.value}.particle")
        payload: dict[str, Any] = {
            "type": self.action_type.value,
            "particle": coerce_particle(self.particle, field=f"effects.{self.action_type.value}.particle"),
        }
        payload.update(self.params)
        return payload


@dataclass(frozen=True)
class PotionAction(ActionSpec):
    effect: PotionEffectLike
    duration_ticks: int
    amplifier: int = 0
    ambient: bool = True
    particles: bool = False
    icon: bool = False

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.effect, field="effects.potion.effect")
        return {
            "type": "potion",
            "effect": coerce_potion_effect(self.effect, field="effects.potion.effect"),
            "durationTicks": int(self.duration_ticks),
            "amplifier": int(self.amplifier),
            "ambient": bool(self.ambient),
            "particles": bool(self.particles),
            "icon": bool(self.icon),
        }


@dataclass(frozen=True)
class ForEachTargetAction(ActionSpec):
    targeter: TargeterSpec
    then_action: ActionSpec
    mode: ForEachMode = ForEachMode.EACH
    max_targets: int = 0
    origin_at: TargetAnchor = TargetAnchor.ORIGIN
    otherwise: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "type": "for_each_target",
            "targeter": self.targeter.to_dict(),
            "then": self.then_action.to_dict(),
            "mode": coerce_enum(self.mode, ForEachMode, field="effects.for_each_target.mode"),
            "originAt": coerce_enum(self.origin_at, TargetAnchor, field="effects.for_each_target.origin_at"),
        }
        if self.max_targets > 0:
            payload["maxTargets"] = int(self.max_targets)
        if self.otherwise is not None:
            payload["otherwise"] = self.otherwise.to_dict()
        return payload


@dataclass(frozen=True)
class ProjectileTrailSpec:
    particle: ParticleLike
    count: int = 1
    offset: float = 0.0
    extra: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.particle, field="effects.projectile.trail.particle")
        return {
            "particle": coerce_particle(self.particle, field="effects.projectile.trail.particle"),
            "count": max(0, int(self.count)),
            "offset": float(self.offset),
            "extra": float(self.extra),
        }


@dataclass(frozen=True)
class ProjectileDamageSpec:
    amount: float
    policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT

    def to_dict(self) -> dict[str, Any]:
        _forbid_plain_string(self.policy, field="effects.projectile.damage.policy")
        return {
            "amount": float(self.amount),
            "policy": coerce_damage_policy(self.policy, field="effects.projectile.damage.policy"),
        }


@dataclass(frozen=True)
class ProjectileActionSpec(ActionSpec):
    speed_per_tick: float = 1.3
    max_distance: float = 24.0
    hit_radius: float = 0.25
    ignore_caster: bool = True
    block_collision: str = "stop"
    kind: ProjectileKindLike | None = None
    max_pierces: int = 0
    travel_step_enabled: bool | None = None
    travel_step_interval_ticks: int | None = None
    trail: ProjectileTrailSpec | None = None
    damage: ProjectileDamageSpec | None = None
    on_hit: ActionSpec | None = None
    on_launch: ActionSpec | None = None
    on_step: ActionSpec | None = None
    on_expire: ActionSpec | None = None
    on_bounce: ActionSpec | None = None
    on_pierce: ActionSpec | None = None

    def to_dict(self) -> dict[str, Any]:
        normalized_collision = str(self.block_collision).strip().upper().replace("-", "_")
        if normalized_collision not in {"STOP", "BOUNCE", "PASS_THROUGH"}:
            raise ValueError(
                "effects.projectile.block_collision: invalid value "
                f"{self.block_collision!r}; expected stop|bounce|pass_through"
            )
        payload: dict[str, Any] = {
            "type": "projectile",
            "speedPerTick": float(self.speed_per_tick),
            "maxDistance": float(self.max_distance),
            "hitRadius": float(self.hit_radius),
            "ignoreCaster": bool(self.ignore_caster),
            "blockCollision": normalized_collision,
        }
        if self.kind is not None:
            _forbid_plain_string(self.kind, field="effects.projectile.kind")
            payload["kind"] = coerce_projectile_kind(self.kind, field="effects.projectile.kind")
        if int(self.max_pierces) > 0:
            payload["maxPierces"] = int(self.max_pierces)
        if self.travel_step_enabled is not None:
            payload["travelStepEnabled"] = bool(self.travel_step_enabled)
        if self.travel_step_interval_ticks is not None:
            payload["travelStepIntervalTicks"] = max(1, int(self.travel_step_interval_ticks))
        if self.trail is not None:
            payload["trail"] = self.trail.to_dict()
        if self.damage is not None:
            payload["damage"] = self.damage.to_dict()
        if self.on_hit is not None:
            payload["onHit"] = self.on_hit.to_dict()
        if self.on_launch is not None:
            payload["onLaunch"] = self.on_launch.to_dict()
        if self.on_step is not None:
            payload["onStep"] = self.on_step.to_dict()
        if self.on_expire is not None:
            payload["onExpire"] = self.on_expire.to_dict()
        if self.on_bounce is not None:
            payload["onBounce"] = self.on_bounce.to_dict()
        if self.on_pierce is not None:
            payload["onPierce"] = self.on_pierce.to_dict()
        return payload


@dataclass(frozen=True)
class ProjectileAutoAimNearestAction(ActionSpec):
    radius: float = 20.0
    y_offset: float = 0.0
    include_players: bool = True
    include_mobs: bool = True
    require_line_of_sight: bool = True
    ignore_caster: bool = True

    def to_dict(self) -> dict[str, Any]:
        if self.radius <= 0.0:
            raise ValueError("effects.projectile_auto_aim_nearest.radius: must be > 0")
        return {
            "type": "projectile_auto_aim_nearest",
            "radius": float(self.radius),
            "yOffset": float(self.y_offset),
            "includePlayers": bool(self.include_players),
            "includeMobs": bool(self.include_mobs),
            "requireLineOfSight": bool(self.require_line_of_sight),
            "ignoreCaster": bool(self.ignore_caster),
        }


@dataclass(frozen=True)
class HealthLteRequirement(RequirementSpec):
    value: float

    def to_dict(self) -> dict[str, Any]:
        return {"type": "health_lte", "value": float(self.value)}


@dataclass(frozen=True)
class HealthGteRequirement(RequirementSpec):
    value: float

    def to_dict(self) -> dict[str, Any]:
        return {"type": "health_gte", "value": float(self.value)}


@dataclass(frozen=True)
class HealthPctLteRequirement(RequirementSpec):
    value: float

    def to_dict(self) -> dict[str, Any]:
        return {"type": "health_pct_lte", "value": float(self.value)}


@dataclass(frozen=True)
class HealthPctGteRequirement(RequirementSpec):
    value: float

    def to_dict(self) -> dict[str, Any]:
        return {"type": "health_pct_gte", "value": float(self.value)}


@dataclass(frozen=True)
class PermissionRequirement(RequirementSpec):
    permission: str

    def to_dict(self) -> dict[str, Any]:
        token = self.permission.strip()
        if not token:
            raise ValueError("effects.requirement.permission: cannot be empty")
        return {"type": "permission", "permission": token}


@dataclass(frozen=True)
class SneakingRequirement(RequirementSpec):
    def to_dict(self) -> dict[str, Any]:
        return {"type": "sneaking"}


@dataclass(frozen=True)
class AfflictPresentRequirement(RequirementSpec):
    affliction_id: str

    def to_dict(self) -> dict[str, Any]:
        token = str(self.affliction_id).strip().lower()
        if not token:
            raise ValueError("effects.requirement.afflict_present.id: cannot be empty")
        return {"type": "afflict_present", "id": token}


@dataclass(frozen=True)
class AfflictStacksGteRequirement(RequirementSpec):
    affliction_id: str
    stacks: int

    def to_dict(self) -> dict[str, Any]:
        token = str(self.affliction_id).strip().lower()
        if not token:
            raise ValueError("effects.requirement.afflict_stacks_gte.id: cannot be empty")
        return {"type": "afflict_stacks_gte", "id": token, "stacks": max(1, int(self.stacks))}


@dataclass(frozen=True)
class AfflictStacksLteRequirement(RequirementSpec):
    affliction_id: str
    stacks: int

    def to_dict(self) -> dict[str, Any]:
        token = str(self.affliction_id).strip().lower()
        if not token:
            raise ValueError("effects.requirement.afflict_stacks_lte.id: cannot be empty")
        return {"type": "afflict_stacks_lte", "id": token, "stacks": max(0, int(self.stacks))}


@dataclass(frozen=True)
class AfflictRemainingLteRequirement(RequirementSpec):
    affliction_id: str
    remaining_ticks: int

    def to_dict(self) -> dict[str, Any]:
        token = str(self.affliction_id).strip().lower()
        if not token:
            raise ValueError("effects.requirement.afflict_remaining_lte.id: cannot be empty")
        return {
            "type": "afflict_remaining_lte",
            "id": token,
            "remainingTicks": max(0, int(self.remaining_ticks)),
        }


@dataclass(frozen=True)
class ManaCost(CostSpec):
    amount: float

    def to_dict(self) -> dict[str, Any]:
        return {"type": "mana", "amount": float(self.amount)}


@dataclass(frozen=True)
class ResourceCost(CostSpec):
    resource: str
    amount: float

    def to_dict(self) -> dict[str, Any]:
        token = self.resource.strip().lower()
        if not token:
            raise ValueError("effects.cost.resource.resource: cannot be empty")
        return {"type": "resource", "resource": token, "amount": float(self.amount)}


@dataclass(frozen=True)
class ConsumeItemCost(CostSpec):
    item_id: Ref | str
    amount: int = 1

    def to_dict(self) -> dict[str, Any]:
        return {"type": "consume_item", "itemId": self.item_id, "amount": max(1, int(self.amount))}


@dataclass(frozen=True)
class ConsumeMainHandCost(CostSpec):
    amount: int = 1

    def to_dict(self) -> dict[str, Any]:
        return {"type": "consume_main_hand", "amount": max(1, int(self.amount))}


@dataclass(frozen=True)
class DurabilityCost(CostSpec):
    amount: int = 1

    def to_dict(self) -> dict[str, Any]:
        return {"type": "durability", "amount": max(1, int(self.amount))}


@dataclass(frozen=True)
class DurabilityMainHandCost(CostSpec):
    amount: int = 1

    def to_dict(self) -> dict[str, Any]:
        return {"type": "durability_main_hand", "amount": max(1, int(self.amount))}


class fx:
    """Static namespace with strict typed helpers for v2 authoring."""

    @staticmethod
    def sequence(*actions: ActionSpec) -> SequenceAction:
        return SequenceAction(actions=list(actions))

    @staticmethod
    def animate(
        *,
        action: ActionSpec,
        duration_ticks: int = 40,
        period_ticks: int = 1,
        follow_caster: bool = True,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
    ) -> AnimateActionSpec:
        return AnimateActionSpec(
            action=action,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            follow_caster=follow_caster,
            easing=easing,
        )

    @staticmethod
    def state_machine(
        *,
        charge: ActionSpec | None = None,
        sustain: ActionSpec | None = None,
        release: ActionSpec | None = None,
        charge_ticks: int = 20,
        sustain_ticks: int = 40,
        release_ticks: int = 20,
        period_ticks: int = 1,
        follow_caster: bool = True,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
    ) -> StateMachineActionSpec:
        return StateMachineActionSpec(
            charge_ticks=charge_ticks,
            sustain_ticks=sustain_ticks,
            release_ticks=release_ticks,
            period_ticks=period_ticks,
            follow_caster=follow_caster,
            easing=easing,
            charge=charge,
            sustain=sustain,
            release=release,
        )

    @staticmethod
    def burst(*, action: ActionSpec, times: int = 6, spacing_ticks: int = 0, delay_ticks: int = 0) -> BurstActionSpec:
        return BurstActionSpec(action=action, times=times, spacing_ticks=spacing_ticks, delay_ticks=delay_ticks)

    @staticmethod
    def pulse(
        *,
        action: ActionSpec,
        duration_ticks: int = 60,
        period_ticks: int = 10,
        follow_caster: bool = False,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
    ) -> PulseActionSpec:
        return PulseActionSpec(
            action=action,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            follow_caster=follow_caster,
            easing=easing,
        )

    @staticmethod
    def loop(
        *,
        action: ActionSpec,
        duration_ticks: int = 60,
        period_ticks: int = 10,
        follow_caster: bool = False,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
    ) -> LoopActionSpec:
        return LoopActionSpec(
            action=action,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            follow_caster=follow_caster,
            easing=easing,
        )

    @staticmethod
    def trail(
        *,
        action: ActionSpec,
        duration_ticks: int = 60,
        period_ticks: int = 2,
        follow_caster: bool = True,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
    ) -> TrailActionSpec:
        return TrailActionSpec(
            action=action,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            follow_caster=follow_caster,
            easing=easing,
        )

    @staticmethod
    def attach(
        *,
        action: ActionSpec,
        anchor: AnchorModeLike = AnchorMode.CASTER,
        point: AnchorPointLike | None = None,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
    ) -> AttachActionSpec:
        return AttachActionSpec(
            action=action,
            anchor=anchor,
            point=point,
            forward=forward,
            right=right,
            up=up,
        )

    @staticmethod
    def follow(
        *,
        action: ActionSpec,
        anchor: AnchorModeLike = AnchorMode.CASTER,
        point: AnchorPointLike | None = None,
        duration_ticks: int = 60,
        period_ticks: int = 2,
        smoothing: float = 1.0,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
    ) -> FollowActionSpec:
        return FollowActionSpec(
            action=action,
            anchor=anchor,
            point=point,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            smoothing=smoothing,
            forward=forward,
            right=right,
            up=up,
        )

    @staticmethod
    def motion(
        *,
        action: ActionSpec,
        mode: MotionModeLike = MotionMode.TRANSLATE,
        at: AtModeLike = AtMode.ORIGIN,
        duration_ticks: int = 40,
        period_ticks: int = 1,
        follow_caster: bool = True,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
        velocity_x: float = 0.0,
        velocity_y: float = 0.0,
        velocity_z: float = 0.0,
        radius: float = 0.0,
        turns: float = 1.0,
        vertical: float = 0.0,
        drift: float = 0.0,
        drift_vertical: float = 0.0,
        drift_speed: float = 0.35,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
    ) -> MotionActionSpec:
        return MotionActionSpec(
            action=action,
            mode=mode,
            at=at,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            follow_caster=follow_caster,
            easing=easing,
            velocity_x=velocity_x,
            velocity_y=velocity_y,
            velocity_z=velocity_z,
            radius=radius,
            turns=turns,
            vertical=vertical,
            drift=drift,
            drift_vertical=drift_vertical,
            drift_speed=drift_speed,
            forward=forward,
            right=right,
            up=up,
        )

    @staticmethod
    def animate_realtime(
        *,
        action: ActionSpec,
        duration_millis: int = 1000,
        period_millis: int = 50,
        follow_caster: bool = True,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
    ) -> AnimateRealtimeActionSpec:
        return AnimateRealtimeActionSpec(
            action=action,
            duration_millis=duration_millis,
            period_millis=period_millis,
            follow_caster=follow_caster,
            easing=easing,
        )

    @staticmethod
    def sound(sound_id: SoundLike, *, volume: float = 1.0, pitch: float = 1.0) -> SoundAction:
        return SoundAction(sound=sound_id, volume=volume, pitch=pitch)

    @staticmethod
    def sphere_shell(
        particle: ParticleLike,
        *,
        radius: float,
        points: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        dust: DustOptionsSpec | None = None,
        scale: float = 1.0,
    ) -> SphereShellAction:
        raise ValueError("fx.sphere_shell is disabled in strict mode; use fx.particles_sphere_shell(...)")

    @staticmethod
    def dust(*, color: str, size: float = 1.0) -> DustOptionsSpec:
        return DustOptionsSpec(color=color, size=size)

    @staticmethod
    def particles_sphere_shell(
        particle: ParticleLike,
        *,
        radius: float,
        points: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        dust: DustOptionsSpec | None = None,
        scale: float = 1.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        if dust is not None and particle_data is not None:
            raise ValueError("effects.particles_sphere_shell: use either dust or particle_data")
        emit = ParticleEmitSpec(
            count=count,
            offset=offset,
            extra=extra,
            at=at,
            forward=forward,
            right=right,
            up=up,
            particle_data=(dust if dust is not None else particle_data),
        ).to_action_fields(field="effects.particles_sphere_shell")
        if float(scale) <= 0.0:
            raise ValueError("effects.particles_sphere_shell.scale: must be > 0")
        emit["radius"] = float(radius) * float(scale)
        emit["points"] = max(1, int(round(float(points) * float(scale))))
        emit["count"] = max(1, int(round(int(emit["count"]) * float(scale))))
        emit["offset"] = float(emit["offset"]) * float(scale)
        if dust is not None and "data" in emit:
            dust_data = dict(emit["data"])
            dust_data["size"] = float(dust_data.get("size", 1.0)) * float(scale)
            emit["data"] = dust_data
        return ParticlesActionSpec(ActionType.PARTICLES_SPHERE_SHELL, particle=particle, params=emit)

    @staticmethod
    def particles_sphere_filled(
        particle: ParticleLike,
        *,
        radius: float,
        points: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_sphere_filled"
        )
        emit["radius"] = float(radius)
        emit["points"] = int(points)
        return ParticlesActionSpec(ActionType.PARTICLES_SPHERE_FILLED, particle=particle, params=emit)

    @staticmethod
    def particles_ring(
        particle: ParticleLike,
        *,
        radius: float,
        points: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_ring"
        )
        emit["radius"] = float(radius)
        emit["points"] = int(points)
        return ParticlesActionSpec(ActionType.PARTICLES_RING, particle=particle, params=emit)

    @staticmethod
    def particles_point(
        particle: ParticleLike,
        *,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_point"
        )
        return ParticlesActionSpec(ActionType.PARTICLES_POINT, particle=particle, params=emit)

    @staticmethod
    def particles_line(
        particle: ParticleLike,
        *,
        length: float,
        step: float = 0.35,
        target_at: TargetAnchor = TargetAnchor.CASTER,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_line"
        )
        emit["length"] = float(length)
        emit["step"] = float(step)
        emit["targetAt"] = coerce_enum(target_at, TargetAnchor, field="effects.particles_line.target_at")
        return ParticlesActionSpec(ActionType.PARTICLES_LINE, particle=particle, params=emit)

    @staticmethod
    def particles_arc(
        particle: ParticleLike,
        *,
        radius: float,
        angle_degrees: float,
        points: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_arc"
        )
        emit["radius"] = float(radius)
        emit["angleDegrees"] = float(angle_degrees)
        emit["points"] = int(points)
        return ParticlesActionSpec(ActionType.PARTICLES_ARC, particle=particle, params=emit)

    @staticmethod
    def particles_disk(
        particle: ParticleLike,
        *,
        radius: float,
        rings: int,
        points_per_ring: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_disk"
        )
        emit["radius"] = float(radius)
        emit["rings"] = int(rings)
        emit["pointsPerRing"] = int(points_per_ring)
        return ParticlesActionSpec(ActionType.PARTICLES_DISK, particle=particle, params=emit)

    @staticmethod
    def particles_helix(
        particle: ParticleLike,
        *,
        radius: float,
        turns: float,
        length: float,
        points: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_helix"
        )
        emit["radius"] = float(radius)
        emit["turns"] = float(turns)
        emit["length"] = float(length)
        emit["points"] = int(points)
        return ParticlesActionSpec(ActionType.PARTICLES_HELIX, particle=particle, params=emit)

    @staticmethod
    def particles_points(
        particle: ParticleLike,
        *,
        points: Sequence[Point3Spec],
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        size: float | None = None,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        if len(points) < 1:
            raise ValueError("effects.particles_points.points: expected at least 1 point")
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_points"
        )
        emit["points"] = [p.to_dict() for p in points]
        if size is not None:
            emit["size"] = float(size)
        return ParticlesActionSpec(ActionType.PARTICLES_POINTS, particle=particle, params=emit)

    @staticmethod
    def particles_polyline(
        particle: ParticleLike,
        *,
        polyline: PolylineSpec,
        step: float = 0.5,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        gradient: GradientSpec | None = None,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_polyline"
        )
        emit["points"] = polyline.to_dict()
        emit["step"] = float(step)
        if gradient is not None:
            emit.update(gradient.to_dict())
        return ParticlesActionSpec(ActionType.PARTICLES_POLYLINE, particle=particle, params=emit)

    @staticmethod
    def particles_mesh(
        particle: ParticleLike,
        *,
        mesh: MeshSpec,
        step: float = 0.75,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        gradient: GradientSpec | None = None,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_mesh"
        )
        emit["triangles"] = mesh.to_dict()
        emit["step"] = float(step)
        if gradient is not None:
            emit.update(gradient.to_dict())
        return ParticlesActionSpec(ActionType.PARTICLES_MESH, particle=particle, params=emit)

    @staticmethod
    def particles_bezier(
        particle: ParticleLike,
        *,
        p0: Point3Spec,
        p1: Point3Spec,
        p2: Point3Spec,
        p3: Point3Spec,
        points_per_meter: float = 10.0,
        max_points: int = 320,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_bezier"
        )
        emit.update(
            {
                "p0": p0.to_dict(),
                "p1": p1.to_dict(),
                "p2": p2.to_dict(),
                "p3": p3.to_dict(),
                "pointsPerMeter": float(points_per_meter),
                "maxPoints": int(max_points),
            }
        )
        return ParticlesActionSpec(ActionType.PARTICLES_BEZIER, particle=particle, params=emit)

    @staticmethod
    def particles_spline(
        particle: ParticleLike,
        *,
        control_points: ControlPointsSpec,
        points_per_meter: float = 10.0,
        max_points: int = 320,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_spline"
        )
        emit.update(
            {
                "points": control_points.to_dict(),
                "pointsPerMeter": float(points_per_meter),
                "maxPoints": int(max_points),
            }
        )
        return ParticlesActionSpec(ActionType.PARTICLES_SPLINE, particle=particle, params=emit)

    @staticmethod
    def particles_cone(
        particle: ParticleLike,
        *,
        length: float,
        angle_degrees: float,
        rings: int,
        points_per_ring: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_cone"
        )
        emit["length"] = float(length)
        emit["angleDegrees"] = float(angle_degrees)
        emit["rings"] = int(rings)
        emit["pointsPerRing"] = int(points_per_ring)
        return ParticlesActionSpec(ActionType.PARTICLES_CONE, particle=particle, params=emit)

    @staticmethod
    def particles_cylinder(
        particle: ParticleLike,
        *,
        radius: float,
        height: float,
        rings: int,
        points_per_ring: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_cylinder"
        )
        emit["radius"] = float(radius)
        emit["height"] = float(height)
        emit["rings"] = int(rings)
        emit["pointsPerRing"] = int(points_per_ring)
        return ParticlesActionSpec(ActionType.PARTICLES_CYLINDER, particle=particle, params=emit)

    @staticmethod
    def particles_box(
        particle: ParticleLike,
        *,
        x_radius: float,
        y_radius: float,
        z_radius: float,
        step: float,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_box"
        )
        emit["xRadius"] = float(x_radius)
        emit["yRadius"] = float(y_radius)
        emit["zRadius"] = float(z_radius)
        emit["step"] = float(step)
        return ParticlesActionSpec(ActionType.PARTICLES_BOX, particle=particle, params=emit)

    @staticmethod
    def particles_polygon(
        particle: ParticleLike,
        *,
        radius: float,
        sides: int,
        points_per_edge: int,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_polygon"
        )
        emit["radius"] = float(radius)
        emit["sides"] = int(sides)
        emit["pointsPerEdge"] = int(points_per_edge)
        return ParticlesActionSpec(ActionType.PARTICLES_POLYGON, particle=particle, params=emit)

    @staticmethod
    def particles_physics(
        particle: ParticleLike,
        *,
        velocity: VelocitySpec = VelocitySpec(),
        physics: PhysicsSpec = PhysicsSpec(),
        count: int = 8,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_physics"
        )
        emit["velocityX"] = float(velocity.x)
        emit["velocityY"] = float(velocity.y)
        emit["velocityZ"] = float(velocity.z)
        emit.update(physics.to_dict())
        return ParticlesActionSpec(ActionType.PARTICLES_PHYSICS, particle=particle, params=emit)

    @staticmethod
    def particles_physics_points(
        particle: ParticleLike,
        *,
        points: Sequence[Point3Spec],
        velocity: VelocitySpec = VelocitySpec(),
        physics: PhysicsSpec = PhysicsSpec(),
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        if len(points) < 1:
            raise ValueError("effects.particles_physics_points.points: expected at least 1 point")
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_physics_points"
        )
        emit["points"] = [p.to_dict() for p in points]
        emit["velocityX"] = float(velocity.x)
        emit["velocityY"] = float(velocity.y)
        emit["velocityZ"] = float(velocity.z)
        emit.update(physics.to_dict())
        return ParticlesActionSpec(ActionType.PARTICLES_PHYSICS_POINTS, particle=particle, params=emit)

    @staticmethod
    def particles_physics_polyline(
        particle: ParticleLike,
        *,
        polyline: PolylineSpec,
        step: float = 0.5,
        velocity: VelocitySpec = VelocitySpec(),
        physics: PhysicsSpec = PhysicsSpec(),
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_physics_polyline"
        )
        emit["points"] = polyline.to_dict()
        emit["step"] = float(step)
        emit["velocityX"] = float(velocity.x)
        emit["velocityY"] = float(velocity.y)
        emit["velocityZ"] = float(velocity.z)
        emit.update(physics.to_dict())
        return ParticlesActionSpec(ActionType.PARTICLES_PHYSICS_POLYLINE, particle=particle, params=emit)

    @staticmethod
    def particles_physics_mesh(
        particle: ParticleLike,
        *,
        mesh: MeshSpec,
        step: float = 0.75,
        velocity: VelocitySpec = VelocitySpec(),
        physics: PhysicsSpec = PhysicsSpec(),
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> ParticlesActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data).to_action_fields(
            field="effects.particles_physics_mesh"
        )
        emit["triangles"] = mesh.to_dict()
        emit["step"] = float(step)
        emit["velocityX"] = float(velocity.x)
        emit["velocityY"] = float(velocity.y)
        emit["velocityZ"] = float(velocity.z)
        emit.update(physics.to_dict())
        return ParticlesActionSpec(ActionType.PARTICLES_PHYSICS_MESH, particle=particle, params=emit)

    @staticmethod
    def _preset_particle(
        action_type: ActionType,
        particle: ParticleLike,
        *,
        emit: ParticleEmitSpec,
        params: dict[str, Any] | None = None,
    ) -> PresetActionSpec:
        payload = emit.to_action_fields(field=f"effects.{action_type.value}")
        if params:
            payload.update(params)
        return PresetActionSpec(action_type=action_type, particle=particle, params=payload)

    @staticmethod
    def preset_shockwave(
        particle: ParticleLike,
        *,
        start_radius: float = 0.5,
        end_radius: float = 4.0,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        points: int = 24,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        _forbid_plain_string(easing, field="effects.preset_shockwave.easing")
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_particle(
            ActionType.PRESET_SHOCKWAVE,
            particle,
            emit=emit,
            params={
                "startRadius": float(start_radius),
                "endRadius": float(end_radius),
                "durationTicks": int(duration_ticks),
                "periodTicks": int(period_ticks),
                "points": int(points),
                "easing": coerce_easing(easing, field="effects.preset_shockwave.easing"),
            },
        )

    @staticmethod
    def preset_beam_chargeup(
        particle: ParticleLike,
        *,
        start_length: float = 0.0,
        end_length: float = 10.0,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        step: float = 0.35,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        _forbid_plain_string(easing, field="effects.preset_beam_chargeup.easing")
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_particle(
            ActionType.PRESET_BEAM_CHARGEUP,
            particle,
            emit=emit,
            params={
                "startLength": float(start_length),
                "endLength": float(end_length),
                "durationTicks": int(duration_ticks),
                "periodTicks": int(period_ticks),
                "step": float(step),
                "easing": coerce_easing(easing, field="effects.preset_beam_chargeup.easing"),
            },
        )

    @staticmethod
    def preset_orbit(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        height: float = 0.0,
        duration_ticks: int = 40,
        period_ticks: int = 1,
        points: int = 24,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_particle(
            ActionType.PRESET_ORBIT,
            particle,
            emit=emit,
            params={
                "radius": float(radius),
                "height": float(height),
                "durationTicks": int(duration_ticks),
                "periodTicks": int(period_ticks),
                "points": int(points),
            },
        )

    @staticmethod
    def preset_orbiting_runes(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        duration_ticks: int = 40,
        period_ticks: int = 2,
        points: int = 20,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_particle(
            ActionType.PRESET_ORBITING_RUNES,
            particle,
            emit=emit,
            params={
                "radius": float(radius),
                "durationTicks": int(duration_ticks),
                "periodTicks": int(period_ticks),
                "points": int(points),
            },
        )

    @staticmethod
    def preset_swirl(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        height: float = 2.0,
        duration_ticks: int = 30,
        period_ticks: int = 1,
        points: int = 24,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        _forbid_plain_string(easing, field="effects.preset_swirl.easing")
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_particle(
            ActionType.PRESET_SWIRL,
            particle,
            emit=emit,
            params={
                "radius": float(radius),
                "height": float(height),
                "durationTicks": int(duration_ticks),
                "periodTicks": int(period_ticks),
                "points": int(points),
                "easing": coerce_easing(easing, field="effects.preset_swirl.easing"),
            },
        )

    @staticmethod
    def preset_spiral_aura(
        particle: ParticleLike,
        *,
        radius: float = 1.0,
        height: float = 2.4,
        duration_ticks: int = 40,
        period_ticks: int = 2,
        points: int = 24,
        easing: EasingLike = Easing.IN_OUT_CUBIC,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        _forbid_plain_string(easing, field="effects.preset_spiral_aura.easing")
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_particle(
            ActionType.PRESET_SPIRAL_AURA,
            particle,
            emit=emit,
            params={
                "radius": float(radius),
                "height": float(height),
                "durationTicks": int(duration_ticks),
                "periodTicks": int(period_ticks),
                "points": int(points),
                "easing": coerce_easing(easing, field="effects.preset_spiral_aura.easing"),
            },
        )

    @staticmethod
    def _preset_shape(
        action_type: ActionType,
        particle: ParticleLike,
        *,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        start_color: str | None = None,
        end_color: str | None = None,
        emit: ParticleEmitSpec,
        params: dict[str, Any] | None = None,
    ) -> PresetActionSpec:
        payload = emit.to_action_fields(field=f"effects.{action_type.value}")
        payload["startScale"] = float(start_scale)
        payload["endScale"] = float(end_scale)
        payload["durationTicks"] = int(duration_ticks)
        payload["periodTicks"] = int(period_ticks)
        if start_color is not None:
            payload["startColor"] = str(start_color).strip()
        if end_color is not None:
            payload["endColor"] = str(end_color).strip()
        if params:
            payload.update(params)
        return PresetActionSpec(action_type=action_type, particle=particle, params=payload)

    @staticmethod
    def preset_morph_ring(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        points: int = 24,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_RING,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "points": int(points)},
        )

    @staticmethod
    def preset_gradient_ring(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        points: int = 24,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_RING,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "points": int(points)},
        )

    @staticmethod
    def preset_morph_line(
        particle: ParticleLike,
        *,
        length: float = 2.4,
        step: float = 0.35,
        target_at: TargetAnchor = TargetAnchor.CASTER,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_LINE,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"length": float(length), "step": float(step), "targetAt": coerce_enum(target_at, TargetAnchor, field="effects.preset_morph_line.target_at")},
        )

    @staticmethod
    def preset_gradient_line(
        particle: ParticleLike,
        *,
        length: float = 2.4,
        step: float = 0.35,
        target_at: TargetAnchor = TargetAnchor.CASTER,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_LINE,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"length": float(length), "step": float(step), "targetAt": coerce_enum(target_at, TargetAnchor, field="effects.preset_gradient_line.target_at")},
        )

    @staticmethod
    def preset_morph_arc(
        particle: ParticleLike,
        *,
        radius: float = 1.4,
        angle_degrees: float = 90.0,
        points: int = 24,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_ARC,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "angleDegrees": float(angle_degrees), "points": int(points)},
        )

    @staticmethod
    def preset_gradient_arc(
        particle: ParticleLike,
        *,
        radius: float = 1.4,
        angle_degrees: float = 90.0,
        points: int = 24,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_ARC,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "angleDegrees": float(angle_degrees), "points": int(points)},
        )

    @staticmethod
    def preset_morph_disk(
        particle: ParticleLike,
        *,
        radius: float = 1.6,
        rings: int = 4,
        points_per_ring: int = 12,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_DISK,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "rings": int(rings), "pointsPerRing": int(points_per_ring)},
        )

    @staticmethod
    def preset_gradient_disk(
        particle: ParticleLike,
        *,
        radius: float = 1.6,
        rings: int = 4,
        points_per_ring: int = 12,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_DISK,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "rings": int(rings), "pointsPerRing": int(points_per_ring)},
        )

    @staticmethod
    def preset_morph_sphere_shell(
        particle: ParticleLike,
        *,
        radius: float = 1.4,
        points: int = 30,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_SPHERE_SHELL,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "points": int(points)},
        )

    @staticmethod
    def preset_gradient_sphere_shell(
        particle: ParticleLike,
        *,
        radius: float = 1.4,
        points: int = 30,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_SPHERE_SHELL,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "points": int(points)},
        )

    @staticmethod
    def preset_morph_sphere_filled(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        points: int = 40,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_SPHERE_FILLED,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "points": int(points)},
        )

    @staticmethod
    def preset_gradient_sphere_filled(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        points: int = 40,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_SPHERE_FILLED,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "points": int(points)},
        )

    @staticmethod
    def preset_morph_helix(
        particle: ParticleLike,
        *,
        radius: float = 0.8,
        turns: float = 2.0,
        length: float = 2.2,
        points: int = 28,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_HELIX,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "turns": float(turns), "length": float(length), "points": int(points)},
        )

    @staticmethod
    def preset_gradient_helix(
        particle: ParticleLike,
        *,
        radius: float = 0.8,
        turns: float = 2.0,
        length: float = 2.2,
        points: int = 28,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_HELIX,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "turns": float(turns), "length": float(length), "points": int(points)},
        )

    @staticmethod
    def preset_morph_cone(
        particle: ParticleLike,
        *,
        length: float = 2.6,
        angle_degrees: float = 70.0,
        rings: int = 4,
        points_per_ring: int = 10,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_CONE,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"length": float(length), "angleDegrees": float(angle_degrees), "rings": int(rings), "pointsPerRing": int(points_per_ring)},
        )

    @staticmethod
    def preset_gradient_cone(
        particle: ParticleLike,
        *,
        length: float = 2.6,
        angle_degrees: float = 70.0,
        rings: int = 4,
        points_per_ring: int = 10,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_CONE,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"length": float(length), "angleDegrees": float(angle_degrees), "rings": int(rings), "pointsPerRing": int(points_per_ring)},
        )

    @staticmethod
    def preset_morph_cylinder(
        particle: ParticleLike,
        *,
        radius: float = 1.0,
        height: float = 2.0,
        rings: int = 4,
        points_per_ring: int = 10,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_CYLINDER,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "height": float(height), "rings": int(rings), "pointsPerRing": int(points_per_ring)},
        )

    @staticmethod
    def preset_gradient_cylinder(
        particle: ParticleLike,
        *,
        radius: float = 1.0,
        height: float = 2.0,
        rings: int = 4,
        points_per_ring: int = 10,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_CYLINDER,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "height": float(height), "rings": int(rings), "pointsPerRing": int(points_per_ring)},
        )

    @staticmethod
    def preset_morph_box(
        particle: ParticleLike,
        *,
        x_radius: float = 1.0,
        y_radius: float = 0.7,
        z_radius: float = 1.0,
        step: float = 0.35,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_BOX,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"xRadius": float(x_radius), "yRadius": float(y_radius), "zRadius": float(z_radius), "step": float(step)},
        )

    @staticmethod
    def preset_gradient_box(
        particle: ParticleLike,
        *,
        x_radius: float = 1.0,
        y_radius: float = 0.7,
        z_radius: float = 1.0,
        step: float = 0.35,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_BOX,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"xRadius": float(x_radius), "yRadius": float(y_radius), "zRadius": float(z_radius), "step": float(step)},
        )

    @staticmethod
    def preset_morph_polygon(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        sides: int = 6,
        points_per_edge: int = 8,
        start_scale: float = 0.7,
        end_scale: float = 1.3,
        duration_ticks: int = 20,
        period_ticks: int = 1,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_MORPH_POLYGON,
            particle,
            start_scale=start_scale,
            end_scale=end_scale,
            duration_ticks=duration_ticks,
            period_ticks=period_ticks,
            emit=emit,
            params={"radius": float(radius), "sides": int(sides), "pointsPerEdge": int(points_per_edge)},
        )

    @staticmethod
    def preset_gradient_polygon(
        particle: ParticleLike,
        *,
        radius: float = 1.2,
        sides: int = 6,
        points_per_edge: int = 8,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_POLYGON,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={"radius": float(radius), "sides": int(sides), "pointsPerEdge": int(points_per_edge)},
        )

    @staticmethod
    def preset_gradient_bezier(
        particle: ParticleLike,
        *,
        p0: Point3Spec,
        p1: Point3Spec,
        p2: Point3Spec,
        p3: Point3Spec,
        points_per_meter: float = 10.0,
        max_points: int = 320,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_BEZIER,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={
                "p0": p0.to_dict(),
                "p1": p1.to_dict(),
                "p2": p2.to_dict(),
                "p3": p3.to_dict(),
                "pointsPerMeter": float(points_per_meter),
                "maxPoints": int(max_points),
            },
        )

    @staticmethod
    def preset_gradient_spline(
        particle: ParticleLike,
        *,
        control_points: ControlPointsSpec,
        points_per_meter: float = 10.0,
        max_points: int = 320,
        start_color: str = "#FFFFFF",
        end_color: str = "#FFD700",
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_shape(
            ActionType.PRESET_GRADIENT_SPLINE,
            particle,
            start_color=start_color,
            end_color=end_color,
            emit=emit,
            params={
                "points": control_points.to_dict(),
                "pointsPerMeter": float(points_per_meter),
                "maxPoints": int(max_points),
            },
        )

    @staticmethod
    def preset_spline_motion(
        particle: ParticleLike,
        *,
        control_points: ControlPointsSpec,
        points_per_meter: float = 10.0,
        max_points: int = 320,
        count: int = 1,
        offset: float = 0.0,
        extra: float = 0.0,
        at: TargetAnchor = TargetAnchor.ORIGIN,
        forward: float = 0.0,
        right: float = 0.0,
        up: float = 0.0,
        particle_data: ParticleDataLike | None = None,
    ) -> PresetActionSpec:
        emit = ParticleEmitSpec(count=count, offset=offset, extra=extra, at=at, forward=forward, right=right, up=up, particle_data=particle_data)
        return fx._preset_particle(
            ActionType.PRESET_SPLINE_MOTION,
            particle,
            emit=emit,
            params={
                "points": control_points.to_dict(),
                "pointsPerMeter": float(points_per_meter),
                "maxPoints": int(max_points),
            },
        )

    @staticmethod
    def damage(amount: float, *, policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT) -> DamageAction:
        return DamageAction(amount=amount, policy=policy)

    @staticmethod
    def heal(amount: float, *, policy: DamagePolicyLike = DamagePolicy.ALWAYS) -> HealAction:
        return HealAction(amount=amount, policy=policy)

    @staticmethod
    def damage_typed(
        amount: float,
        *,
        damage_type: DamageTypeLike,
        ignore_resistance: bool = False,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.DIRECT,
        source: str | None = None,
        tags: Sequence[str] = (),
    ) -> DamageTypedAction:
        return DamageTypedAction(
            amount=amount,
            damage_type=damage_type,
            ignore_resistance=ignore_resistance,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
        )

    @staticmethod
    def set_resistance(
        *,
        damage_type: DamageTypeLike,
        multiplier: float,
        duration_ticks: int = 0,
    ) -> SetResistanceAction:
        return SetResistanceAction(damage_type=damage_type, multiplier=multiplier, duration_ticks=duration_ticks)

    @staticmethod
    def add_resistance(
        *,
        damage_type: DamageTypeLike,
        delta: float,
        duration_ticks: int = 0,
    ) -> AddResistanceAction:
        return AddResistanceAction(damage_type=damage_type, delta=delta, duration_ticks=duration_ticks)

    @staticmethod
    def clear_resistance(*, damage_type: DamageTypeLike | None = None) -> ClearResistanceAction:
        return ClearResistanceAction(damage_type=damage_type)

    @staticmethod
    def set_reflect(
        *,
        ratio: float = 0.25,
        flat: float = 0.0,
        ignore_resistance: bool = False,
        damage_type: DamageTypeLike | None = None,
        duration_ticks: int = 0,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
    ) -> SetReflectAction:
        return SetReflectAction(
            ratio=ratio,
            flat=flat,
            ignore_resistance=ignore_resistance,
            damage_type=damage_type,
            duration_ticks=duration_ticks,
            policy=policy,
        )

    @staticmethod
    def clear_reflect() -> ClearReflectAction:
        return ClearReflectAction()

    @staticmethod
    def damage_percent(
        percent: float,
        *,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.PERCENT,
        source: str | None = None,
        tags: Sequence[str] = (),
    ) -> DamagePercentAction:
        return DamagePercentAction(
            percent=percent,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
        )

    @staticmethod
    def damage_true(
        amount: float,
        *,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.TRUE,
        source: str | None = None,
        tags: Sequence[str] = (),
    ) -> DamageTrueAction:
        return DamageTrueAction(
            amount=amount,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
        )

    @staticmethod
    def damage_falloff(
        amount: float,
        *,
        max_distance: float,
        min_multiplier: float,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.FALLOFF,
        source: str | None = None,
        tags: Sequence[str] = (),
    ) -> DamageFalloffAction:
        return DamageFalloffAction(
            amount=amount,
            max_distance=max_distance,
            min_multiplier=min_multiplier,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
        )

    @staticmethod
    def damage_crit(
        amount: float,
        *,
        crit_chance: float,
        crit_multiplier: float = 1.5,
        headshot_multiplier: float = 1.0,
        headshot_threshold: float = 0.15,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.CRIT,
        source: str | None = None,
        tags: Sequence[str] = (),
    ) -> DamageCritAction:
        return DamageCritAction(
            amount=amount,
            crit_chance=crit_chance,
            crit_multiplier=crit_multiplier,
            headshot_multiplier=headshot_multiplier,
            headshot_threshold=headshot_threshold,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
        )

    @staticmethod
    def damage_lifesteal(
        amount: float,
        *,
        ratio: float,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.LIFESTEAL,
        source: str | None = None,
        tags: Sequence[str] = (),
    ) -> DamageLifestealAction:
        return DamageLifestealAction(
            amount=amount,
            ratio=ratio,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
        )

    @staticmethod
    def damage_dot(
        amount: float,
        *,
        period_ticks: int,
        times: int,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.DOT,
        source: str | None = None,
        tags: Sequence[str] = (),
        on_tick: ActionSpec | None = None,
    ) -> DamageDotAction:
        return DamageDotAction(
            amount=amount,
            period_ticks=period_ticks,
            times=times,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
            on_tick=on_tick,
        )

    @staticmethod
    def damage_over_time(
        amount: float,
        *,
        period_ticks: int,
        times: int,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.DOT,
        source: str | None = None,
        tags: Sequence[str] = (),
        on_tick: ActionSpec | None = None,
    ) -> DamageDotAction:
        return fx.damage_dot(
            amount,
            period_ticks=period_ticks,
            times=times,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=tags,
            on_tick=on_tick,
        )

    @staticmethod
    def damage_chain(
        amount: float,
        *,
        radius: float,
        max_jumps: int,
        delay_ticks: int = 0,
        falloff: float = 0.85,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.CHAIN,
        source: str | None = None,
        tags: Sequence[str] = (),
        on_hit: ActionSpec | None = None,
    ) -> DamageChainAction:
        return DamageChainAction(
            amount=amount,
            radius=radius,
            max_jumps=max_jumps,
            delay_ticks=delay_ticks,
            falloff=falloff,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=list(tags),
            on_hit=on_hit,
        )

    @staticmethod
    def chain_damage(
        amount: float,
        *,
        radius: float,
        max_jumps: int,
        delay_ticks: int = 0,
        falloff: float = 0.85,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        damage_cause: DamageCauseLike = DamageCause.CHAIN,
        source: str | None = None,
        tags: Sequence[str] = (),
        on_hit: ActionSpec | None = None,
    ) -> DamageChainAction:
        return fx.damage_chain(
            amount,
            radius=radius,
            max_jumps=max_jumps,
            delay_ticks=delay_ticks,
            falloff=falloff,
            policy=policy,
            damage_cause=damage_cause,
            source=source,
            tags=tags,
            on_hit=on_hit,
        )

    @staticmethod
    def chain_lightning(
        amount: float,
        *,
        radius: float,
        max_jumps: int,
        delay_ticks: int = 0,
        falloff: float = 0.85,
        policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        source: str | None = None,
        tags: Sequence[str] = (),
        on_hit: ActionSpec | None = None,
    ) -> DamageChainAction:
        return fx.damage_chain(
            amount,
            radius=radius,
            max_jumps=max_jumps,
            delay_ticks=delay_ticks,
            falloff=falloff,
            policy=policy,
            damage_cause=DamageCause.CHAIN,
            source=source,
            tags=tags,
            on_hit=on_hit,
        )

    @staticmethod
    def ground_damage(
        *,
        radius: float,
        damage: DamageTemplateSpec,
        max_drop: float = 6.0,
        ignore_caster: bool = True,
        on_hit: ActionSpec | None = None,
    ) -> GroundDamageAction:
        return GroundDamageAction(
            radius=radius,
            damage=damage,
            max_drop=max_drop,
            ignore_caster=ignore_caster,
            on_hit=on_hit,
        )

    @staticmethod
    def damage_ground(
        *,
        radius: float,
        damage: DamageTemplateSpec,
        max_drop: float = 6.0,
        ignore_caster: bool = True,
        on_hit: ActionSpec | None = None,
    ) -> GroundDamageAction:
        return fx.ground_damage(
            radius=radius,
            damage=damage,
            max_drop=max_drop,
            ignore_caster=ignore_caster,
            on_hit=on_hit,
        )

    @staticmethod
    def heal_percent(
        percent: float,
        *,
        policy: DamagePolicyLike = DamagePolicy.ALWAYS,
        heal_type: HealTypeLike = HealType.DIRECT,
        source: str | None = None,
        tags: Sequence[str] = (),
        cap: float = 0.0,
        overheal_to_shield: bool = False,
        shield_cap: float = 0.0,
        shield_decay_ticks: int = 0,
    ) -> HealPercentAction:
        return HealPercentAction(
            percent=percent,
            policy=policy,
            heal_type=heal_type,
            source=source,
            tags=list(tags),
            cap=cap,
            overheal_to_shield=overheal_to_shield,
            shield_cap=shield_cap,
            shield_decay_ticks=shield_decay_ticks,
        )

    @staticmethod
    def heal_over_time(
        amount: float,
        *,
        period_ticks: int,
        times: int,
        policy: DamagePolicyLike = DamagePolicy.ALWAYS,
        heal_type: HealTypeLike = HealType.HOT,
        source: str | None = None,
        tags: Sequence[str] = (),
        cap: float = 0.0,
        overheal_to_shield: bool = False,
        shield_cap: float = 0.0,
        shield_decay_ticks: int = 0,
        on_tick: ActionSpec | None = None,
    ) -> HealOverTimeAction:
        return HealOverTimeAction(
            amount=amount,
            period_ticks=period_ticks,
            times=times,
            policy=policy,
            heal_type=heal_type,
            source=source,
            tags=list(tags),
            cap=cap,
            overheal_to_shield=overheal_to_shield,
            shield_cap=shield_cap,
            shield_decay_ticks=shield_decay_ticks,
            on_tick=on_tick,
        )

    @staticmethod
    def heal_hot(
        amount: float,
        *,
        period_ticks: int,
        times: int,
        policy: DamagePolicyLike = DamagePolicy.ALWAYS,
        heal_type: HealTypeLike = HealType.HOT,
        source: str | None = None,
        tags: Sequence[str] = (),
        cap: float = 0.0,
        overheal_to_shield: bool = False,
        shield_cap: float = 0.0,
        shield_decay_ticks: int = 0,
        on_tick: ActionSpec | None = None,
    ) -> HealOverTimeAction:
        return fx.heal_over_time(
            amount,
            period_ticks=period_ticks,
            times=times,
            policy=policy,
            heal_type=heal_type,
            source=source,
            tags=tags,
            cap=cap,
            overheal_to_shield=overheal_to_shield,
            shield_cap=shield_cap,
            shield_decay_ticks=shield_decay_ticks,
            on_tick=on_tick,
        )

    @staticmethod
    def shield(
        amount: float,
        *,
        cap: float = 0.0,
        decay_ticks: int = 0,
        policy: DamagePolicyLike = DamagePolicy.ALWAYS,
    ) -> ShieldAction:
        return ShieldAction(amount=amount, cap=cap, decay_ticks=decay_ticks, policy=policy)

    @staticmethod
    def absorb(
        amount: float,
        *,
        cap: float = 0.0,
        decay_ticks: int = 0,
        policy: DamagePolicyLike = DamagePolicy.ALWAYS,
    ) -> AbsorbAction:
        return AbsorbAction(amount=amount, cap=cap, decay_ticks=decay_ticks, policy=policy)

    @staticmethod
    def totem() -> TotemAction:
        return TotemAction()

    @staticmethod
    def knockback(*, horizontal: float = 1.0, vertical: float = 0.35) -> KnockbackAction:
        return KnockbackAction(horizontal=horizontal, vertical=vertical)

    @staticmethod
    def pull(*, horizontal: float = 0.75, vertical: float = 0.08) -> PullAction:
        return PullAction(horizontal=horizontal, vertical=vertical)

    @staticmethod
    def afflict_apply(
        *,
        affliction_id: str,
        stacks: int = 1,
        max_stacks: int = 5,
        duration_ticks: int = 100,
        refresh_policy: AfflictionRefreshPolicyLike = AfflictionRefreshPolicy.RESET_DURATION,
        audience: AfflictionAudienceLike = AfflictionAudience.PVE_ONLY,
        tick_every_ticks: int = 0,
        on_tick: ActionSpec | None = None,
        on_apply: ActionSpec | None = None,
        on_stack: ActionSpec | None = None,
        on_expire: ActionSpec | None = None,
    ) -> AfflictApplyAction:
        return AfflictApplyAction(
            affliction_id=affliction_id,
            stacks=stacks,
            max_stacks=max_stacks,
            duration_ticks=duration_ticks,
            refresh_policy=refresh_policy,
            audience=audience,
            tick_every_ticks=tick_every_ticks,
            on_tick=on_tick,
            on_apply=on_apply,
            on_stack=on_stack,
            on_expire=on_expire,
        )

    @staticmethod
    def afflict_clear(*, affliction_id: str | None = None) -> AfflictClearAction:
        return AfflictClearAction(affliction_id=affliction_id)

    @staticmethod
    def afflict_consume(
        *,
        affliction_id: str,
        stacks: int = 1,
        require_at_least: int = 1,
        on_success: ActionSpec | None = None,
        on_failure: ActionSpec | None = None,
    ) -> AfflictConsumeAction:
        return AfflictConsumeAction(
            affliction_id=affliction_id,
            stacks=stacks,
            require_at_least=require_at_least,
            on_success=on_success,
            on_failure=on_failure,
        )

    @staticmethod
    def chance(
        *,
        probability: float,
        then_action: ActionSpec,
        otherwise_action: ActionSpec | None = None,
    ) -> ChanceAction:
        return ChanceAction(probability=probability, then_action=then_action, otherwise_action=otherwise_action)

    @staticmethod
    def debug_log(message: str) -> DebugLogAction:
        return DebugLogAction(message=message)

    @staticmethod
    def raycast_hit_entity(
        *,
        then_action: ActionSpec,
        max_distance: float = 20.0,
        ray_size: float = 0.35,
        stop_on_block: bool = True,
        ignore_caster: bool = True,
        damage: DamageTemplateSpec | None = None,
        otherwise_action: ActionSpec | None = None,
    ) -> RaycastHitEntityAction:
        return RaycastHitEntityAction(
            then_action=then_action,
            max_distance=max_distance,
            ray_size=ray_size,
            stop_on_block=stop_on_block,
            ignore_caster=ignore_caster,
            damage=damage,
            otherwise_action=otherwise_action,
        )

    @staticmethod
    def potion(
        effect: PotionEffectLike,
        *,
        duration_ticks: int,
        amplifier: int = 0,
        ambient: bool = True,
        particles: bool = False,
        icon: bool = False,
    ) -> PotionAction:
        return PotionAction(
            effect=effect,
            duration_ticks=duration_ticks,
            amplifier=amplifier,
            ambient=ambient,
            particles=particles,
            icon=icon,
        )

    @staticmethod
    def for_each_target(
        *,
        targeter: TargeterSpec,
        then: ActionSpec,
        mode: ForEachMode = ForEachMode.EACH,
        max_targets: int = 0,
        origin_at: TargetAnchor = TargetAnchor.ORIGIN,
        otherwise: Optional[ActionSpec] = None,
    ) -> ForEachTargetAction:
        return ForEachTargetAction(
            targeter=targeter,
            then_action=then,
            mode=mode,
            max_targets=max_targets,
            origin_at=origin_at,
            otherwise=otherwise,
        )

    @staticmethod
    def projectile(
        *,
        speed_per_tick: float = 1.3,
        max_distance: float = 24.0,
        hit_radius: float = 0.25,
        ignore_caster: bool = True,
        block_collision: str = "stop",
        kind: ProjectileKindLike | None = None,
        max_pierces: int = 0,
        travel_step_enabled: Optional[bool] = None,
        travel_step_interval_ticks: Optional[int] = None,
        trail_particle: Optional[ParticleLike] = None,
        trail_count: int = 1,
        trail_offset: float = 0.0,
        trail_extra: float = 0.0,
        damage_amount: Optional[float] = None,
        damage_policy: DamagePolicyLike = DamagePolicy.HOSTILE_DEFAULT,
        on_hit: Optional[ActionSpec] = None,
        on_launch: Optional[ActionSpec] = None,
        on_step: Optional[ActionSpec] = None,
        on_expire: Optional[ActionSpec] = None,
        on_bounce: Optional[ActionSpec] = None,
        on_pierce: Optional[ActionSpec] = None,
    ) -> ProjectileActionSpec:
        trail = None
        if trail_particle is not None:
            trail = ProjectileTrailSpec(
                particle=trail_particle,
                count=trail_count,
                offset=trail_offset,
                extra=trail_extra,
            )
        damage = None
        if damage_amount is not None:
            damage = ProjectileDamageSpec(amount=damage_amount, policy=damage_policy)
        return ProjectileActionSpec(
            speed_per_tick=speed_per_tick,
            max_distance=max_distance,
            hit_radius=hit_radius,
            ignore_caster=ignore_caster,
            block_collision=block_collision,
            kind=kind,
            max_pierces=max_pierces,
            travel_step_enabled=travel_step_enabled,
            travel_step_interval_ticks=travel_step_interval_ticks,
            trail=trail,
            damage=damage,
            on_hit=on_hit,
            on_launch=on_launch,
            on_step=on_step,
            on_expire=on_expire,
            on_bounce=on_bounce,
            on_pierce=on_pierce,
        )

    @staticmethod
    def target_self() -> SelfTargeter:
        return SelfTargeter()

    @staticmethod
    def target_sphere(*, radius: float, ignore_caster: bool = True) -> SphereTargeter:
        return SphereTargeter(radius=radius, ignore_caster=ignore_caster)

    @staticmethod
    def target_context(key: str = "mob_target") -> ContextTargeter:
        return ContextTargeter(key=key)

    @staticmethod
    def target_look_ray(*, max_distance: float = 24.0) -> LookRayTargeter:
        return LookRayTargeter(max_distance=max_distance)

    @staticmethod
    def projectile_auto_aim_nearest(
        *,
        radius: float = 20.0,
        y_offset: float = 0.0,
        include_players: bool = True,
        include_mobs: bool = True,
        require_line_of_sight: bool = True,
        ignore_caster: bool = True,
    ) -> ProjectileAutoAimNearestAction:
        return ProjectileAutoAimNearestAction(
            radius=radius,
            y_offset=y_offset,
            include_players=include_players,
            include_mobs=include_mobs,
            require_line_of_sight=require_line_of_sight,
            ignore_caster=ignore_caster,
        )


@dataclass(frozen=True)
class EventTrigger:
    payload: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return dict(self.payload)


@dataclass
class AbilityV2:
    ctx: BuildContext
    name: str
    action: ActionSpec
    id: Optional[str] = None
    symbol: Optional[str] = None
    description: Optional[str] = None
    requirements: list[RequirementSpec] = field(default_factory=list)
    costs: list[CostSpec] = field(default_factory=list)
    cooldown_ticks: Optional[int] = None
    triggers: list[EventTrigger] = field(default_factory=list)
    _trigger_auto_index: int = field(default=0, init=False, repr=False)

    def __post_init__(self) -> None:
        self.id, self.symbol = self.ctx.register(
            "ability",
            symbol=self.symbol or self.name,
            id_override=self.id,
            parts=[self.name],
        )

    def requirement(self, *requirements: RequirementSpec) -> "AbilityV2":
        self.requirements.extend(requirements)
        return self

    def cost(self, *costs: CostSpec) -> "AbilityV2":
        self.costs.extend(costs)
        return self

    def cooldown(self, ticks: int) -> "AbilityV2":
        self.cooldown_ticks = int(ticks)
        return self

    def on_event(
        self,
        event: CombatEventTypeLike,
        *,
        ability: Optional["AbilityV2 | Ref | str"] = None,
        trigger_id: Optional[str] = None,
        chance: Optional[float] = None,
        cooldown_ticks: Optional[int] = None,
        cooldown_scope: CombatCooldownScopeLike = CombatCooldownScope.PER_PLAYER,
        require_sneaking: bool = False,
        permission: Optional[str] = None,
        target_bind: CombatEventTargetBindLike = CombatEventTargetBind.EVENT_PRIMARY,
        origin_bind: CombatEventOriginBindLike = CombatEventOriginBind.IMPACT,
        cancel_event: bool = False,
        phase: Optional[str] = None,
        filters: Optional[EventFiltersSpec] = None,
    ) -> "AbilityV2":
        _forbid_plain_string(event, field="effects.trigger.event")
        _forbid_plain_string(cooldown_scope, field="effects.trigger.cooldown.scope")
        _forbid_plain_string(target_bind, field="effects.trigger.target.bind")
        _forbid_plain_string(origin_bind, field="effects.trigger.target.origin_bind")

        event_token = coerce_combat_event_type(event, field="effects.trigger.event")
        if phase is None:
            phase_token = "PRE" if event_token.endswith("_PRE") else "POST"
        else:
            phase_token = str(phase).strip().upper()
            if phase_token not in {"PRE", "POST"}:
                raise ValueError("effects.trigger.phase: invalid phase; expected PRE|POST")
        if event_token.endswith("_PRE") and phase_token != "PRE":
            raise ValueError("effects.trigger.phase: PRE events require phase=PRE")
        if not event_token.endswith("_PRE") and phase_token == "PRE":
            raise ValueError("effects.trigger.phase: non-PRE events cannot use phase=PRE")
        if cancel_event and not event_token.endswith("_PRE"):
            raise ValueError("effects.trigger.cancel_event: only *_PRE events can be cancelled")

        if ability is None:
            if not cancel_event:
                raise ValueError("effects.trigger.ability: ability is required unless cancel_event=True")
            ability_id = None
        elif isinstance(ability, AbilityV2):
            ability_id = ability.id
        elif isinstance(ability, Ref):
            ability_id = self.ctx.resolve(ability, domain="ability", field="effects.trigger.ability")
        else:
            ability_id = self.ctx.resolve(str(ability), domain="ability", field="effects.trigger.ability")

        if trigger_id is None:
            trigger_id = self._next_trigger_id(event_token)

        payload: dict[str, Any] = {
            "type": "event",
            "event": event_token,
            "id": trigger_id,
            "phase": phase_token,
            "target": {
                "bind": coerce_combat_target_bind(target_bind, field="effects.trigger.target.bind"),
                "originBind": coerce_combat_origin_bind(origin_bind, field="effects.trigger.target.originBind"),
            },
        }
        if ability_id:
            payload["ability"] = ability_id
        if chance is not None:
            payload["chance"] = float(chance)
        if cooldown_ticks is not None and int(cooldown_ticks) > 0:
            payload["cooldown"] = {
                "ticks": int(cooldown_ticks),
                "scope": coerce_combat_cooldown_scope(
                    cooldown_scope,
                    field="effects.trigger.cooldown.scope",
                ),
            }
        if require_sneaking:
            payload["requireSneaking"] = True
        if permission:
            payload["permission"] = permission
        if cancel_event:
            payload["cancelEvent"] = True
        if filters:
            payload["filters"] = filters.to_dict()
        self.triggers.append(EventTrigger(payload))
        return self

    def on_projectile(
        self,
        event: CombatEventTypeLike,
        *,
        ability: Optional["AbilityV2 | Ref | str"] = None,
        trigger_id: Optional[str] = None,
        cancel_event: bool = False,
        phase: Optional[str] = None,
        filters: Optional[ProjectileFiltersSpec] = None,
        projectile_types: Sequence[str] | None = None,
        projectile_family: ProjectileFamilyLike | None = None,
        projectile_kind: ProjectileKindLike | None = None,
        distance_min: float | None = None,
        distance_max: float | None = None,
        speed_min: float | None = None,
        speed_max: float | None = None,
        draw_force_min: float | None = None,
        draw_force_max: float | None = None,
        in_ground_ticks_min: int | None = None,
        in_ground_ticks_max: int | None = None,
        is_critical: bool | None = None,
        is_charged: bool | None = None,
        is_piercing: bool | None = None,
        shot_from_crossbow: bool | None = None,
        shooter_is_player: bool | None = None,
        hit_block_materials: Sequence[MaterialLike] | None = None,
        hit_block_tags: Sequence[str] | None = None,
        target_bind: CombatEventTargetBindLike = CombatEventTargetBind.EVENT_PRIMARY,
        origin_bind: CombatEventOriginBindLike = CombatEventOriginBind.IMPACT,
        chance: Optional[float] = None,
        cooldown_ticks: Optional[int] = None,
        cooldown_scope: CombatCooldownScopeLike = CombatCooldownScope.PER_PLAYER,
        require_sneaking: bool = False,
        permission: Optional[str] = None,
    ) -> "AbilityV2":
        if filters is not None and not isinstance(filters, ProjectileFiltersSpec):
            raise ValueError("effects.trigger.filters: expected ProjectileFiltersSpec")
        auto_filters = ProjectileFiltersSpec(
            projectile_types=projectile_types,
            projectile_family=projectile_family,
            projectile_kind=projectile_kind,
            distance_min=distance_min,
            distance_max=distance_max,
            speed_min=speed_min,
            speed_max=speed_max,
            draw_force_min=draw_force_min,
            draw_force_max=draw_force_max,
            in_ground_ticks_min=in_ground_ticks_min,
            in_ground_ticks_max=in_ground_ticks_max,
            is_critical=is_critical,
            is_charged=is_charged,
            is_piercing=is_piercing,
            shot_from_crossbow=shot_from_crossbow,
            shooter_is_player=shooter_is_player,
            hit_block_materials=hit_block_materials,
            hit_block_tags=hit_block_tags,
        )
        auto_filters_dict = auto_filters.to_dict()
        if filters is None:
            effective_filters = auto_filters if auto_filters_dict else None
        else:
            merged_payload = filters.to_dict()
            if auto_filters_dict:
                merged_payload.update(auto_filters_dict)
            effective_filters = ProjectileFiltersSpec(payload=merged_payload)
        return self.on_event(
            event,
            ability=ability,
            trigger_id=trigger_id,
            chance=chance,
            cooldown_ticks=cooldown_ticks,
            cooldown_scope=cooldown_scope,
            require_sneaking=require_sneaking,
            permission=permission,
            target_bind=target_bind,
            origin_bind=origin_bind,
            cancel_event=cancel_event,
            phase=phase,
            filters=effective_filters,
        )

    def on_projectile_pre(
        self,
        event: CombatEventTypeLike = CombatEventType.ON_PROJECTILE_COLLIDE_ENTITY_PRE,
        *,
        ability: Optional["AbilityV2 | Ref | str"] = None,
        trigger_id: Optional[str] = None,
        cancel_event: bool = True,
        filters: Optional[ProjectileFiltersSpec] = None,
        **kwargs: Any,
    ) -> "AbilityV2":
        return self.on_projectile(
            event,
            ability=ability,
            trigger_id=trigger_id,
            cancel_event=cancel_event,
            phase="PRE",
            filters=filters,
            **kwargs,
        )

    def on_projectile_hit(
        self,
        *,
        ability: Optional["AbilityV2 | Ref | str"],
        block: bool = False,
        trigger_id: Optional[str] = None,
        filters: Optional[ProjectileFiltersSpec] = None,
        **kwargs: Any,
    ) -> "AbilityV2":
        event = (
            CombatEventType.ON_PROJECTILE_HIT_BLOCK
            if block
            else CombatEventType.ON_PROJECTILE_HIT_ENTITY
        )
        return self.on_projectile(
            event,
            ability=ability,
            trigger_id=trigger_id,
            filters=filters,
            **kwargs,
        )

    def on_projectile_launch(
        self,
        *,
        ability: Optional["AbilityV2 | Ref | str"] = None,
        pre: bool = False,
        trigger_id: Optional[str] = None,
        cancel_event: bool = False,
        filters: Optional[ProjectileFiltersSpec] = None,
        **kwargs: Any,
    ) -> "AbilityV2":
        event = (
            CombatEventType.ON_PROJECTILE_LAUNCH_PRE
            if pre
            else CombatEventType.ON_PROJECTILE_LAUNCH
        )
        return self.on_projectile(
            event,
            ability=ability,
            trigger_id=trigger_id,
            cancel_event=cancel_event,
            filters=filters,
            **kwargs,
        )

    def build(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "name": self.name,
            "action": self.action.to_dict(),
        }
        if self.description:
            payload["description"] = self.description
        if self.requirements:
            payload["requirements"] = [entry.to_dict() for entry in self.requirements]
        if self.costs:
            payload["costs"] = [entry.to_dict() for entry in self.costs]
        if self.cooldown_ticks is not None:
            payload["cooldown"] = {"ticks": int(self.cooldown_ticks)}
        if self.triggers:
            payload["triggers"] = [entry.to_dict() for entry in self.triggers]
        return payload

    def _next_trigger_id(self, event_token: str) -> str:
        self._trigger_auto_index += 1
        return f"{self.id}_{event_token.lower()}_{self._trigger_auto_index}"


def ability(
    ctx: BuildContext,
    *,
    name: str,
    action: ActionSpec,
    id: Optional[str] = None,
    symbol: Optional[str] = None,
    description: Optional[str] = None,
) -> AbilityV2:
    return AbilityV2(ctx=ctx, name=name, action=action, id=id, symbol=symbol, description=description)


__all__ = [
    "ActionSpec",
    "RequirementSpec",
    "CostSpec",
    "Action",
    "Requirement",
    "Cost",
    "TargeterSpec",
    "SelfTargeter",
    "SphereTargeter",
    "ContextTargeter",
    "LookRayTargeter",
    "EventFiltersSpec",
    "ProjectileFiltersSpec",
    "SequenceAction",
    "AnimateActionSpec",
    "StateMachineActionSpec",
    "BurstActionSpec",
    "PulseActionSpec",
    "LoopActionSpec",
    "TrailActionSpec",
    "AttachActionSpec",
    "FollowActionSpec",
    "MotionActionSpec",
    "AnimateRealtimeActionSpec",
    "SoundAction",
    "DustOptionsSpec",
    "BlockDataSpec",
    "ItemDataSpec",
    "TrailDataSpec",
    "VibrationDataSpec",
    "Point3Spec",
    "PolylineSpec",
    "ControlPointsSpec",
    "TriangleSpec",
    "MeshSpec",
    "VelocitySpec",
    "PhysicsSpec",
    "GradientSpec",
    "ParticleEmitSpec",
    "ParticlesActionSpec",
    "SphereShellAction",
    "DamageAction",
    "HealAction",
    "DamageTemplateSpec",
    "DamageTypedAction",
    "SetResistanceAction",
    "AddResistanceAction",
    "ClearResistanceAction",
    "SetReflectAction",
    "ClearReflectAction",
    "DamagePercentAction",
    "DamageTrueAction",
    "DamageFalloffAction",
    "DamageCritAction",
    "DamageLifestealAction",
    "DamageDotAction",
    "DamageChainAction",
    "GroundDamageAction",
    "HealPercentAction",
    "HealOverTimeAction",
    "ShieldAction",
    "AbsorbAction",
    "TotemAction",
    "KnockbackAction",
    "PullAction",
    "AfflictApplyAction",
    "AfflictClearAction",
    "AfflictConsumeAction",
    "ChanceAction",
    "DebugLogAction",
    "RaycastHitEntityAction",
    "PresetActionSpec",
    "PotionAction",
    "ForEachTargetAction",
    "ProjectileTrailSpec",
    "ProjectileDamageSpec",
    "ProjectileActionSpec",
    "ProjectileAutoAimNearestAction",
    "HealthLteRequirement",
    "HealthGteRequirement",
    "HealthPctLteRequirement",
    "HealthPctGteRequirement",
    "PermissionRequirement",
    "SneakingRequirement",
    "AfflictPresentRequirement",
    "AfflictStacksGteRequirement",
    "AfflictStacksLteRequirement",
    "AfflictRemainingLteRequirement",
    "ManaCost",
    "ResourceCost",
    "ConsumeItemCost",
    "ConsumeMainHandCost",
    "DurabilityCost",
    "DurabilityMainHandCost",
    "EventTrigger",
    "fx",
    "AbilityV2",
    "ability",
]
