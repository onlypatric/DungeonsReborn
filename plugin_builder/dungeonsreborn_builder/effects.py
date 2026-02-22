"""Effects/abilities builder."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Union

from .base import BuilderBase, ExporterBase, snake_case
from .animation import AnimationBuilder, VFXPreset
from .dsl import DslBuilder
from .utils import apply_overrides
from .enums import (
    ActionType,
    At,
    CostType,
    DamageCause,
    DamageMode,
    DamagePolicy,
    DamageType,
    Easing,
    HealType,
    RequirementType,
    TargeterType,
    _KNOWN_COST_TYPES,
    _KNOWN_REQUIREMENT_TYPES,
)
from .effects_ids import ACTION_TYPES, TARGETER_TYPES
from .minions import (
    MinionFormation,
    MinionMode,
    MinionOwnerScalingSpec,
    MinionPassiveSpec,
    MinionScaling,
    MinionScalingLimits,
    MinionSpecialAttackSpec,
    MinionSummonCostSpec,
    MinionSummonSpec,
    MinionTargetRules,
    minion_immunities,
    minion_resistances,
    minion_stat_overrides,
)
from .vanilla import EnumValue, Particle, normalize_enum_name
from .vanilla import Attribute
from .mobs import MobParticlesSpec


def _as_text(value: object) -> object:
    if isinstance(value, EnumValue):
        return normalize_enum_name(value.name)
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, str):
        lowered = value.lower()
        if lowered in {"heal_over_time", "heal_hot", "hot"}:
            return "HOT"
        if lowered == "direct":
            return "DIRECT"
        if lowered == "shield":
            return "SHIELD"
        if lowered == "absorb":
            return "ABSORB"
    return value

def _particle_value(particle: EnumValue) -> str:
    if not isinstance(particle, EnumValue):
        raise ValueError("particle must be provided as a vanilla enum value")
    return particle.name



@dataclass
class Requirement:
    type: Union[str, RequirementType]
    data: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {"type": _as_text(self.type), **self.data}

    def validate(self) -> None:
        if not self.type:
            raise ValueError("Requirement type is required")
        if isinstance(self.type, str) and self.type not in _KNOWN_REQUIREMENT_TYPES:
            raise ValueError(f"Unknown requirement type: {self.type}")


@dataclass
class Cost:
    type: Union[str, CostType]
    data: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {"type": _as_text(self.type), **self.data}

    def validate(self) -> None:
        if not self.type:
            raise ValueError("Cost type is required")
        if isinstance(self.type, str) and self.type not in _KNOWN_COST_TYPES:
            raise ValueError(f"Unknown cost type: {self.type}")


@dataclass
class Cooldown:
    ticks: int
    key: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload = {"ticks": self.ticks}
        if self.key:
            payload["key"] = self.key
        return payload


@dataclass
class Action:
    type: Union[str, EnumValue]
    params: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {"type": _as_text(self.type), **self.params}

    def validate(self) -> None:
        if not self.type:
            raise ValueError("Action type is required")
        raw = _as_text(self.type)
        if isinstance(raw, str) and raw not in ACTION_TYPES:
            raise ValueError(f"Unknown action type: {raw}")


@dataclass
class Ability:
    ability_id: str
    name: str
    description: Optional[str] = None
    requirements: List[Requirement] = field(default_factory=list)
    costs: List[Cost] = field(default_factory=list)
    cooldown: Optional[Cooldown] = None
    action: Optional[Action] = None
    script: Optional[str] = None
    allow_worlds: List[str] = field(default_factory=list)
    deny_worlds: List[str] = field(default_factory=list)
    allow_regions: List[Dict[str, Any]] = field(default_factory=list)
    deny_regions: List[Dict[str, Any]] = field(default_factory=list)
    allow_worlds_message: Optional[str] = None
    deny_worlds_message: Optional[str] = None
    allow_regions_message: Optional[str] = None
    deny_regions_message: Optional[str] = None
    unsafe_actions: bool = False
    unsafe_permission: Optional[str] = None
    overrides: List[Dict[str, Any]] = field(default_factory=list)
    override_paths: List[tuple[str, Any]] = field(default_factory=list)
    override_warnings: List[str] = field(default_factory=list)

    def validate(self) -> None:
        if not self.ability_id:
            raise ValueError("ability_id is required")
        if not self.name:
            raise ValueError("name is required")
        if self.action and self.script:
            raise ValueError("Ability cannot define both action and script")
        if not self.action and not self.script:
            raise ValueError("Ability must define either action or script")
        for requirement in self.requirements:
            requirement.validate()
        for cost in self.costs:
            cost.validate()
        if self.action:
            self.action.validate()

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"name": self.name}
        if self.description:
            payload["description"] = self.description
        if self.requirements:
            payload["requirements"] = [req.to_dict() for req in self.requirements]
        if self.costs:
            payload["costs"] = [cost.to_dict() for cost in self.costs]
        if self.cooldown:
            payload["cooldown"] = self.cooldown.to_dict()
        if self.action:
            payload["action"] = self.action.to_dict()
        if self.script:
            payload["script"] = {"language": "dsl-v1", "source": self.script}
        if self.allow_worlds:
            payload["allowWorlds"] = list(self.allow_worlds)
        if self.deny_worlds:
            payload["denyWorlds"] = list(self.deny_worlds)
        if self.allow_regions:
            payload["allowRegions"] = list(self.allow_regions)
        if self.deny_regions:
            payload["denyRegions"] = list(self.deny_regions)
        if self.allow_worlds_message:
            payload["allowWorldsMessage"] = self.allow_worlds_message
        if self.deny_worlds_message:
            payload["denyWorldsMessage"] = self.deny_worlds_message
        if self.allow_regions_message:
            payload["allowRegionsMessage"] = self.allow_regions_message
        if self.deny_regions_message:
            payload["denyRegionsMessage"] = self.deny_regions_message
        if self.unsafe_actions:
            payload["unsafeActions"] = True
        if self.unsafe_permission:
            payload["unsafePermission"] = self.unsafe_permission
        if self.overrides or self.override_paths:
            apply_overrides(payload, self.overrides, self.override_paths)
        return payload


@dataclass
class ShapeTemplate:
    points: List[Dict[str, float]] = field(default_factory=list)
    triangles: List[List[Dict[str, float]]] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.points:
            payload["points"] = list(self.points)
        if self.triangles:
            payload["triangles"] = list(self.triangles)
        return payload


@dataclass
class EffectsDocument:
    abilities: List[Ability] = field(default_factory=list)
    shapes: Dict[str, ShapeTemplate] = field(default_factory=dict)
    macros: Dict[str, Dict[str, Any]] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"schemaVersion": 1}
        if self.shapes:
            payload["shapes"] = {key: shape.to_dict() for key, shape in self.shapes.items()}
        if self.macros:
            payload["macros"] = {key: value for key, value in self.macros.items()}
        abilities: Dict[str, Any] = {}
        for ability in self.abilities:
            abilities[ability.ability_id] = ability.to_dict()
        payload["abilities"] = abilities
        return payload


def shape_point_xyz(x: float, y: float, z: float) -> Dict[str, float]:
    return {"x": x, "y": y, "z": z}


def shape_point_offsets(forward: float, right: float, up: float) -> Dict[str, float]:
    return {"forward": forward, "right": right, "up": up}


def shape_triangle(a: Dict[str, float], b: Dict[str, float], c: Dict[str, float]) -> List[Dict[str, float]]:
    return [a, b, c]


class EffectsExporter(ExporterBase):
    def write_ability(self, ability: Ability, filename: Optional[str] = None) -> str:
        ability.validate()
        name = filename or f"{ability.ability_id}.yml"
        data = {
            "schemaVersion": 1,
            "abilities": {
                ability.ability_id: ability.to_dict(),
            },
        }
        return self.write_yaml(name, data)

    def write_document(self, document: EffectsDocument, filename: str = "effects.yml") -> str:
        if not document.abilities:
            raise ValueError("EffectsDocument must include at least one ability")
        for ability in document.abilities:
            ability.validate()
        return self.write_yaml(filename, document.to_dict())


class AbilityBuilder(BuilderBase):
    def __init__(self, ability_id: Optional[str] = None) -> None:
        super().__init__(_id=ability_id)
        self._requirements: List[Requirement] = []
        self._costs: List[Cost] = []
        self._cooldown: Optional[Cooldown] = None
        self._action: Optional[Action] = None
        self._script: Optional[str] = None
        self._allow_worlds: List[str] = []
        self._deny_worlds: List[str] = []
        self._allow_regions: List[Dict[str, Any]] = []
        self._deny_regions: List[Dict[str, Any]] = []
        self._allow_worlds_message: Optional[str] = None
        self._deny_worlds_message: Optional[str] = None
        self._allow_regions_message: Optional[str] = None
        self._deny_regions_message: Optional[str] = None
        self._unsafe_actions: bool = False
        self._unsafe_permission: Optional[str] = None

    def requirements(self, *requirements: Requirement) -> "AbilityBuilder":
        self._requirements.extend(requirements)
        return self

    def costs(self, *costs: Cost) -> "AbilityBuilder":
        self._costs.extend(costs)
        return self

    def with_requirements(self, *requirements: Requirement) -> "AbilityBuilder":
        return self.requirements(*requirements)

    def with_costs(self, *costs: Cost) -> "AbilityBuilder":
        return self.costs(*costs)

    def with_cooldown(self, ticks: int, key: Optional[str] = None) -> "AbilityBuilder":
        return self.cooldown(ticks=ticks, key=key)

    def cooldown(self, ticks: int, key: Optional[str] = None) -> "AbilityBuilder":
        self._cooldown = Cooldown(ticks=ticks, key=key)
        return self

    def action(self, action: Action) -> "AbilityBuilder":
        self._action = action
        return self

    def script(self, source: str) -> "AbilityBuilder":
        self._script = source
        return self

    def animation(self, builder: AnimationBuilder) -> "AbilityBuilder":
        self._script = builder.build_script()
        return self

    def dsl(self, builder: DslBuilder) -> "AbilityBuilder":
        """Attach a DSL builder script without writing raw strings."""
        self._script = builder.build()
        return self

    def allow_worlds(self, *worlds: str, message: Optional[str] = None) -> "AbilityBuilder":
        self._allow_worlds.extend(worlds)
        if message is not None:
            self._allow_worlds_message = message
        return self

    def deny_worlds(self, *worlds: str, message: Optional[str] = None) -> "AbilityBuilder":
        self._deny_worlds.extend(worlds)
        if message is not None:
            self._deny_worlds_message = message
        return self

    def allow_regions(self, *regions: Dict[str, Any], message: Optional[str] = None) -> "AbilityBuilder":
        self._allow_regions.extend(regions)
        if message is not None:
            self._allow_regions_message = message
        return self

    def deny_regions(self, *regions: Dict[str, Any], message: Optional[str] = None) -> "AbilityBuilder":
        self._deny_regions.extend(regions)
        if message is not None:
            self._deny_regions_message = message
        return self

    def unsafe_actions(self, permission: Optional[str] = None) -> "AbilityBuilder":
        self._unsafe_actions = True
        if permission is not None:
            self._unsafe_permission = permission
        return self

    def build(self) -> Ability:
        self._ensure_id("ability_id")
        self._ensure_name()
        ability = Ability(
            ability_id=self._id,
            name=self._name,
            description=self._description,
            requirements=self._requirements,
            costs=self._costs,
            cooldown=self._cooldown,
            action=self._action,
            script=self._script,
            allow_worlds=self._allow_worlds,
            deny_worlds=self._deny_worlds,
            allow_regions=self._allow_regions,
            deny_regions=self._deny_regions,
            allow_worlds_message=self._allow_worlds_message,
            deny_worlds_message=self._deny_worlds_message,
            allow_regions_message=self._allow_regions_message,
            deny_regions_message=self._deny_regions_message,
            unsafe_actions=self._unsafe_actions,
            unsafe_permission=self._unsafe_permission,
            overrides=[mapping for mapping, _ in self._raw_overrides],
            override_paths=[(path, value) for path, value, _ in self._path_overrides],
            override_warnings=self._format_override_warnings(f"ability:{self._id}"),
        )
        ability.validate()
        return ability

    def pipeline(self) -> "AbilityPipeline":
        return AbilityPipeline(self.build())

    def trigger_message(self, message: str) -> "AbilityBuilder":
        self._action = sequence(self._action, Action("message", {"message": message})) if self._action else Action("message", {"message": message})
        return self


def sequence(*actions: Action) -> Action:
    return Action("sequence", {"actions": [action.to_dict() for action in actions]})


def auto_ability_id(prefix: str, *parts: str) -> str:
    tokens = [prefix, *parts]
    return snake_case("_".join(token for token in tokens if token))


def requirement_sneaking(message: Optional[str] = None) -> Requirement:
    data: Dict[str, Any] = {}
    if message:
        data["message"] = message
    return Requirement(RequirementType.SNEAKING, data)


def requirement_permission(permission: str, message: Optional[str] = None) -> Requirement:
    data: Dict[str, Any] = {"permission": permission}
    if message:
        data["message"] = message
    return Requirement(RequirementType.PERMISSION, data)


def requirement_has_item_tag(tag: str, message: Optional[str] = None) -> Requirement:
    data: Dict[str, Any] = {"key": tag}
    if message:
        data["message"] = message
    return Requirement(RequirementType.HAS_ITEM_TAG, data)


def cost_mana(amount: float) -> Cost:
    return Cost(CostType.MANA, {"amount": amount})


def cost_consume_main_hand(amount: float) -> Cost:
    return Cost(CostType.CONSUME_MAIN_HAND, {"amount": amount})


def cost_durability(amount: float, allow_break: bool = False) -> Cost:
    return Cost(CostType.DURABILITY_MAIN_HAND, {"damage": amount, "allowBreak": allow_break})


def region(world: str, x: float, y: float, z: float, radius: float) -> Dict[str, Any]:
    return {"world": world, "x": x, "y": y, "z": z, "radius": radius}


def targeter(targeter_type: str | TargeterType, **params: Any) -> Dict[str, Any]:
    raw = _as_text(targeter_type)
    if raw not in TARGETER_TYPES:
        raise ValueError(f"Unknown targeter type: {raw}")
    return {"type": raw, **params}


def targeter_nearby(radius: float, ignore_caster: bool = True, target_filter: str = "mobs") -> Dict[str, Any]:
    return {"type": "sphere", "radius": radius, "ignoreCaster": ignore_caster, "filter": target_filter}


def targeter_cone(radius: float, angle: float, ignore_caster: bool = True, target_filter: str = "mobs") -> Dict[str, Any]:
    return {
        "type": "cone",
        "radius": radius,
        "angleDegrees": angle,
        "ignoreCaster": ignore_caster,
        "filter": target_filter,
    }


def targeter_raycast(range_blocks: float, size: float = 0.25, stop_on_block: bool = True, ignore_caster: bool = True) -> Dict[str, Any]:
    return {
        "type": "look_ray",
        "maxDistance": range_blocks,
        "raySize": size,
        "stopOnBlock": stop_on_block,
        "ignoreCaster": ignore_caster,
    }


def targeter_context_target(key: str = "mob_target") -> Dict[str, Any]:
    return {"type": "context_target", "key": key}


def for_each_target(
    targeter_spec: Dict[str, Any],
    then: Action,
    mode: str = "each",
    max_targets: int = 0,
    origin_at: Union[str, At] = At.ORIGIN,
    otherwise: Optional[Action] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "targeter": targeter_spec,
        "then": then.to_dict(),
        "mode": mode,
        "originAt": _as_text(origin_at),
    }
    if max_targets:
        payload["maxTargets"] = max_targets
    if otherwise:
        payload["otherwise"] = otherwise.to_dict()
    return Action("for_each_target", payload)


def animation_script(builder: AnimationBuilder) -> str:
    return builder.build_script()


def ability_from_vfx(
    ability_id: str,
    name: str,
    vfx: Union[VFXPreset, AnimationBuilder],
    description: Optional[str] = None,
) -> AbilityBuilder:
    """High-level helper: build an ability from a VFX preset or timeline."""
    builder = AbilityBuilder(ability_id).name(name)
    if description:
        builder = builder.description(description)
    if isinstance(vfx, AnimationBuilder):
        return builder.animation(vfx)
    return builder.script(AnimationBuilder().burst(vfx).build_script())

def global_timeline(builder: AnimationBuilder, offsets: List[tuple[int, AnimationBuilder]]) -> AnimationBuilder:
    combined = AnimationBuilder().burst(builder.to_preset())
    for offset_ticks, child in offsets:
        combined.schedule(offset_ticks, child.to_preset())
    return combined


def animation_state_machine(
    charge: AnimationBuilder,
    sustain: AnimationBuilder,
    release: AnimationBuilder,
    charge_ticks: int = 20,
    sustain_ticks: int = 60,
    release_ticks: int = 20,
    sustain_every: int = 4,
) -> AnimationBuilder:
    timeline = AnimationBuilder()
    timeline.burst(charge.to_preset())
    if sustain_ticks > 0:
        timeline.schedule(charge_ticks, sustain.to_preset())
        timeline.loop(
            times=max(1, sustain_ticks // sustain_every),
            every=sustain_every,
            value=sustain.to_preset(),
        )
    if release_ticks > 0:
        timeline.schedule(charge_ticks + sustain_ticks, release.to_preset())
    return timeline


def particles_point(
    particle: EnumValue,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {"particle": _particle_value(particle), "count": count, "offset": offset, "extra": extra}
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_point", payload)


def particles_ring(
    particle: EnumValue,
    radius: float,
    points: int,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "radius": radius,
        "points": points,
        "count": count,
        "offset": offset,
        "extra": extra,
    }
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_ring", payload)


def particles_sphere_shell(
    particle: EnumValue,
    radius: float,
    points: int,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "radius": radius,
        "points": points,
        "count": count,
        "offset": offset,
        "extra": extra,
    }
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_sphere_shell", payload)


def particles_points(
    particle: EnumValue,
    points: Optional[List[List[float]]] = None,
    shape: Optional[str] = None,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    start_color: Optional[str] = None,
    end_color: Optional[str] = None,
    size: float = 1.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "count": count,
        "offset": offset,
        "extra": extra,
        "size": size,
    }
    if points is not None:
        payload["points"] = points
    if shape:
        payload["shape"] = shape
    if start_color:
        payload["startColor"] = start_color
    if end_color:
        payload["endColor"] = end_color
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_points", payload)


def particles_polyline(
    particle: EnumValue,
    points: Optional[List[List[float]]] = None,
    shape: Optional[str] = None,
    step: float = 0.5,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    start_color: Optional[str] = None,
    end_color: Optional[str] = None,
    size: float = 1.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "step": step,
        "count": count,
        "offset": offset,
        "extra": extra,
        "size": size,
    }
    if points is not None:
        payload["points"] = points
    if shape:
        payload["shape"] = shape
    if start_color:
        payload["startColor"] = start_color
    if end_color:
        payload["endColor"] = end_color
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_polyline", payload)


def particles_mesh(
    particle: EnumValue,
    shape: str,
    step: float = 0.75,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    start_color: Optional[str] = None,
    end_color: Optional[str] = None,
    size: float = 1.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "shape": shape,
        "step": step,
        "count": count,
        "offset": offset,
        "extra": extra,
        "size": size,
    }
    if start_color:
        payload["startColor"] = start_color
    if end_color:
        payload["endColor"] = end_color
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_mesh", payload)


def particles_physics_points(
    particle: EnumValue,
    points: Optional[List[List[float]]] = None,
    shape: Optional[str] = None,
    count: int = 1,
    velocity: tuple[float, float, float] = (0.0, 0.2, 0.0),
    spread: float = 0.08,
    gravity: float = 0.03,
    drag: float = 0.02,
    steps: int = 20,
    period_ticks: int = 1,
    collide: bool = False,
    collision_mode: str = "STOP",
    restitution: float = 0.0,
    offset: float = 0.0,
    extra: float = 0.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "count": count,
        "velocityX": velocity[0],
        "velocityY": velocity[1],
        "velocityZ": velocity[2],
        "spread": spread,
        "gravity": gravity,
        "drag": drag,
        "steps": steps,
        "periodTicks": period_ticks,
        "collide": collide,
        "collisionMode": collision_mode,
        "restitution": restitution,
        "offset": offset,
        "extra": extra,
    }
    if points is not None:
        payload["points"] = points
    if shape:
        payload["shape"] = shape
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_physics_points", payload)


def particles_physics_polyline(
    particle: EnumValue,
    points: Optional[List[List[float]]] = None,
    shape: Optional[str] = None,
    step: float = 0.5,
    count: int = 1,
    velocity: tuple[float, float, float] = (0.0, 0.2, 0.0),
    spread: float = 0.08,
    gravity: float = 0.03,
    drag: float = 0.02,
    steps: int = 20,
    period_ticks: int = 1,
    collide: bool = False,
    collision_mode: str = "STOP",
    restitution: float = 0.0,
    offset: float = 0.0,
    extra: float = 0.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "step": step,
        "count": count,
        "velocityX": velocity[0],
        "velocityY": velocity[1],
        "velocityZ": velocity[2],
        "spread": spread,
        "gravity": gravity,
        "drag": drag,
        "steps": steps,
        "periodTicks": period_ticks,
        "collide": collide,
        "collisionMode": collision_mode,
        "restitution": restitution,
        "offset": offset,
        "extra": extra,
    }
    if points is not None:
        payload["points"] = points
    if shape:
        payload["shape"] = shape
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_physics_polyline", payload)


def particles_physics_mesh(
    particle: EnumValue,
    shape: str,
    step: float = 0.75,
    count: int = 1,
    velocity: tuple[float, float, float] = (0.0, 0.2, 0.0),
    spread: float = 0.08,
    gravity: float = 0.03,
    drag: float = 0.02,
    steps: int = 20,
    period_ticks: int = 1,
    collide: bool = False,
    collision_mode: str = "STOP",
    restitution: float = 0.0,
    offset: float = 0.0,
    extra: float = 0.0,
    at: Optional[str] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    payload: Dict[str, Any] = {
        "particle": _particle_value(particle),
        "shape": shape,
        "step": step,
        "count": count,
        "velocityX": velocity[0],
        "velocityY": velocity[1],
        "velocityZ": velocity[2],
        "spread": spread,
        "gravity": gravity,
        "drag": drag,
        "steps": steps,
        "periodTicks": period_ticks,
        "collide": collide,
        "collisionMode": collision_mode,
        "restitution": restitution,
        "offset": offset,
        "extra": extra,
    }
    if at:
        payload["at"] = at
    if forward:
        payload["forward"] = forward
    if right:
        payload["right"] = right
    if up:
        payload["up"] = up
    return Action("particles_physics_mesh", payload)


def delay(ticks: int, action: Action) -> Action:
    return Action("delay", {"delayTicks": ticks, "then": action.to_dict()})


def sound(sound_id: str | EnumValue, volume: float = 1.0, pitch: float = 1.0) -> Action:
    return Action(
        "sound",
        {"sound": _as_text(sound_id), "volume": volume, "pitch": pitch},
    )


def potion(effect: str, duration_ticks: int, amplifier: int = 0) -> Action:
    return Action(
        "potion",
        {"effect": effect, "durationTicks": duration_ticks, "amplifier": amplifier},
    )


def raycast_hit_entity(range_blocks: float, on_hit: Action) -> Action:
    return Action(
        "raycast_hit_entity",
        {"range": range_blocks, "onHit": on_hit.to_dict()},
    )


def animate(
    action: Action,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Union[str, Easing] = Easing.IN_OUT_CUBIC,
    follow_caster: bool = True,
) -> Action:
    return Action(
        "animate",
        {
            "durationTicks": duration_ticks,
            "periodTicks": period_ticks,
            "easing": _as_text(easing),
            "followCaster": follow_caster,
            "action": action.to_dict(),
        },
    )


def animate_shape(
    action: Action,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Union[str, Easing] = Easing.IN_OUT_CUBIC,
    follow_caster: bool = True,
) -> Action:
    return Action(
        "animate_shape",
        {
            "durationTicks": duration_ticks,
            "periodTicks": period_ticks,
            "easing": _as_text(easing),
            "followCaster": follow_caster,
            "shape": action.to_dict(),
        },
    )


def motion(
    action: Action,
    mode: str = "translate",
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Union[str, Easing] = Easing.IN_OUT_CUBIC,
    follow_caster: bool = True,
    velocity: tuple[float, float, float] = (0.0, 0.0, 0.0),
    radius: float = 0.0,
    turns: float = 1.0,
    vertical: float = 0.0,
    drift: float = 0.0,
    drift_vertical: float = 0.0,
    drift_speed: float = 0.35,
    at: str = "origin",
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> Action:
    return Action(
        "motion",
        {
            "mode": mode,
            "durationTicks": duration_ticks,
            "periodTicks": period_ticks,
            "easing": _as_text(easing),
            "followCaster": follow_caster,
            "velocityX": velocity[0],
            "velocityY": velocity[1],
            "velocityZ": velocity[2],
            "radius": radius,
            "turns": turns,
            "vertical": vertical,
            "drift": drift,
            "driftVertical": drift_vertical,
            "driftSpeed": drift_speed,
            "at": _as_text(at),
            "forward": forward,
            "right": right,
            "up": up,
            "action": action.to_dict(),
        },
    )


def minion_summon(
    mob_id: str,
    *,
    minion_id: Optional[str] = None,
    count: int = 1,
    duration_ticks: int = 20 * 30,
    radius: float = 1.5,
    summon: Optional[MinionSummonSpec] = None,
    despawn_on_logout: bool = True,
    persistent: bool = False,
    share_potion_effects: bool = False,
    scaling: Optional[MinionScaling] = None,
    owner_scaling: Optional[MinionOwnerScalingSpec] = None,
    scaling_limits: Optional[MinionScalingLimits] = None,
    resistances: Optional[Dict[DamageType | str, float]] = None,
    immunities: Optional[List[DamageType | str]] = None,
    mode: Optional[MinionMode | str] = None,
    target_rules: Optional[MinionTargetRules] = None,
    passives: Optional[List[MinionPassiveSpec | str]] = None,
    special_attacks: Optional[List[MinionSpecialAttackSpec | str]] = None,
    stat_overrides: Optional[Dict[Attribute | str, float]] = None,
    main_attack: Optional[str] = None,
    secondary_attack: Optional[str] = None,
    disable_base_passives: bool = False,
    disable_base_attacks: bool = False,
    disable_base_ai: bool = False,
    name_override: Optional[str] = None,
    glow: Optional[bool] = None,
    particles: Optional[MobParticlesSpec] = None,
    particles_period_ticks: Optional[int] = None,
    summon_costs: Optional[List[MinionSummonCostSpec]] = None,
    summon_cooldown_ticks: Optional[int] = None,
    summon_cooldown_key: Optional[str] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "mob": mob_id,
        "count": count,
        "durationTicks": duration_ticks,
        "radius": radius,
        "despawnOnLogout": despawn_on_logout,
        "persistent": persistent,
        "sharePotionEffects": share_potion_effects,
        "disableBasePassives": disable_base_passives,
        "disableBaseAttacks": disable_base_attacks,
        "disableBaseAi": disable_base_ai,
    }
    if minion_id is not None:
        payload["id"] = minion_id
    if summon is not None:
        payload.update(summon.to_dict())
    if scaling is not None:
        payload["scale"] = scaling.to_dict()
    if owner_scaling is not None:
        payload["ownerScaling"] = owner_scaling.to_dict()
    if scaling_limits is not None:
        payload["scalingLimits"] = scaling_limits.to_dict()
    if resistances:
        payload["resistances"] = minion_resistances(resistances)
    if immunities:
        payload["immunities"] = minion_immunities(immunities)
    if mode is not None:
        payload["mode"] = _as_text(mode)
    if target_rules is not None:
        payload["targeting"] = target_rules.to_dict()
    if passives:
        payload["passives"] = [
            entry.to_dict() if isinstance(entry, MinionPassiveSpec) else entry for entry in passives
        ]
    if special_attacks:
        payload["specialAttacks"] = [
            entry.to_dict() if isinstance(entry, MinionSpecialAttackSpec) else entry for entry in special_attacks
        ]
    if stat_overrides:
        payload["statOverrides"] = minion_stat_overrides(stat_overrides)
    if main_attack:
        payload["mainAttack"] = main_attack
    if secondary_attack:
        payload["secondaryAttack"] = secondary_attack
    if name_override is not None:
        payload["name"] = name_override
    if glow is not None:
        payload["glow"] = glow
    if particles is not None:
        payload["particles"] = particles.to_dict()
        if particles_period_ticks is None:
            particles_period_ticks = 20
    if particles_period_ticks is not None:
        payload["particlesPeriodTicks"] = particles_period_ticks
    if summon_costs:
        payload["summonCosts"] = [entry.to_dict() for entry in summon_costs]
    if summon_cooldown_ticks is not None:
        payload["summonCooldownTicks"] = summon_cooldown_ticks
    if summon_cooldown_key is not None:
        payload["summonCooldownKey"] = summon_cooldown_key
    return Action("minion_summon", payload)


def state_machine(
    charge: Action,
    sustain: Action,
    release: Action,
    charge_ticks: int = 20,
    sustain_ticks: int = 40,
    release_ticks: int = 20,
    period_ticks: int = 1,
    easing: Union[str, Easing] = Easing.IN_OUT_CUBIC,
    follow_caster: bool = True,
) -> Action:
    return Action(
        "state_machine",
        {
            "chargeTicks": charge_ticks,
            "sustainTicks": sustain_ticks,
            "releaseTicks": release_ticks,
            "periodTicks": period_ticks,
            "easing": _as_text(easing),
            "followCaster": follow_caster,
            "charge": charge.to_dict(),
            "sustain": sustain.to_dict(),
            "release": release.to_dict(),
        },
    )


def burst(
    action: Action,
    times: int = 6,
    spacing_ticks: int = 0,
    delay_ticks: int = 0,
) -> Action:
    return Action(
        "burst",
        {
            "times": times,
            "spacingTicks": spacing_ticks,
            "delayTicks": delay_ticks,
            "action": action.to_dict(),
        },
    )


def pulse(
    action: Action,
    duration_ticks: int = 60,
    period_ticks: int = 10,
    easing: Union[str, Easing] = Easing.IN_OUT_CUBIC,
    follow_caster: bool = True,
) -> Action:
    return Action(
        "pulse",
        {
            "durationTicks": duration_ticks,
            "periodTicks": period_ticks,
            "easing": _as_text(easing),
            "followCaster": follow_caster,
            "action": action.to_dict(),
        },
    )


def damage(
    amount: float,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
) -> Action:
    payload: Dict[str, Any] = {"amount": amount, "policy": _as_text(policy)}
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("damage", payload)


def damage_typed(
    amount: float,
    damage_type: Union[str, DamageType] = DamageType.PHYSICAL,
    damage_cause: Union[str, DamageCause] = DamageCause.DIRECT,
    ignore_resistance: bool = False,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
    mode: Union[str, DamageMode] = DamageMode.FLAT,
) -> Action:
    payload: Dict[str, Any] = {
        "amount": amount,
        "damageType": _as_text(damage_type),
        "damageCause": _as_text(damage_cause),
        "ignoreResistance": ignore_resistance,
        "policy": _as_text(policy),
        "mode": _as_text(mode),
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("damage_typed", payload)


def damage_percent(
    percent: float,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    damage_cause: Union[str, DamageCause] = DamageCause.PERCENT,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "percent": percent,
        "policy": _as_text(policy),
        "damageCause": _as_text(damage_cause),
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("damage_percent", payload)


def damage_true(
    amount: float,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    damage_cause: Union[str, DamageCause] = DamageCause.TRUE,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "amount": amount,
        "policy": _as_text(policy),
        "damageCause": _as_text(damage_cause),
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("damage_true", payload)


def damage_chain(
    amount: float,
    radius: float,
    jumps: int = 4,
    falloff: float = 0.9,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    damage_type: Union[str, DamageType] = DamageType.LIGHTNING,
    damage_cause: Union[str, DamageCause] = DamageCause.CHAIN,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "amount": amount,
        "radius": radius,
        "jumps": jumps,
        "falloff": falloff,
        "policy": _as_text(policy),
        "damageType": _as_text(damage_type),
        "damageCause": _as_text(damage_cause),
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("damage_chain", payload)


def damage_dot(
    amount: float,
    period_ticks: int,
    times: int,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    damage_type: Union[str, DamageType] = DamageType.POISON,
    damage_cause: Union[str, DamageCause] = DamageCause.DOT,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "amount": amount,
        "periodTicks": period_ticks,
        "times": times,
        "policy": _as_text(policy),
        "damageType": _as_text(damage_type),
        "damageCause": _as_text(damage_cause),
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("damage_dot", payload)


def heal(
    amount: float,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    heal_type: Union[str, HealType] = HealType.DIRECT,
    cap: float = 0.0,
    overheal_to_shield: bool = False,
    shield_cap: float = 0.0,
    shield_decay_ticks: int = 0,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "amount": amount,
        "policy": _as_text(policy),
        "healType": _as_text(heal_type),
        "cap": cap,
        "overhealToShield": overheal_to_shield,
        "shieldCap": shield_cap,
        "shieldDecayTicks": shield_decay_ticks,
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("heal", payload)


def heal_percent(
    percent: float,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    heal_type: Union[str, HealType] = HealType.DIRECT,
    cap: float = 0.0,
    overheal_to_shield: bool = False,
    shield_cap: float = 0.0,
    shield_decay_ticks: int = 0,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "percent": percent,
        "policy": _as_text(policy),
        "healType": _as_text(heal_type),
        "cap": cap,
        "overhealToShield": overheal_to_shield,
        "shieldCap": shield_cap,
        "shieldDecayTicks": shield_decay_ticks,
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    return Action("heal_percent", payload)


def heal_over_time(
    amount: float,
    period_ticks: int,
    times: int,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    heal_type: Union[str, HealType] = HealType.HOT,
    cap: float = 0.0,
    overheal_to_shield: bool = False,
    shield_cap: float = 0.0,
    shield_decay_ticks: int = 0,
    source: Optional[str] = None,
    tags: Optional[List[str]] = None,
    on_tick: Optional[Action] = None,
) -> Action:
    payload: Dict[str, Any] = {
        "amount": amount,
        "periodTicks": period_ticks,
        "times": times,
        "policy": _as_text(policy),
        "healType": _as_text(heal_type),
        "cap": cap,
        "overhealToShield": overheal_to_shield,
        "shieldCap": shield_cap,
        "shieldDecayTicks": shield_decay_ticks,
    }
    if source:
        payload["source"] = source
    if tags:
        payload["tags"] = tags
    if on_tick:
        payload["onTick"] = on_tick.to_dict()
    return Action("heal_over_time", payload)


def shield(
    amount: float,
    cap: float = 0.0,
    decay_ticks: int = 0,
    policy: Union[str, DamagePolicy] = DamagePolicy.HOSTILE_DEFAULT,
    heal_type: Union[str, HealType] = HealType.SHIELD,
) -> Action:
    payload: Dict[str, Any] = {
        "amount": amount,
        "cap": cap,
        "decayTicks": decay_ticks,
        "policy": _as_text(policy),
        "healType": _as_text(heal_type),
    }
    return Action("shield", payload)


def projectile_spell(
    ability_id: str,
    name: str,
    particle: str,
    range_blocks: float,
    on_hit: Action,
) -> AbilityBuilder:
    return (
        AbilityBuilder(ability_id)
        .name(name)
        .action(raycast_hit_entity(range_blocks, on_hit))
        .requirements()
    )


def aura_field(
    ability_id: str,
    name: str,
    ring_particle: str,
    ring_radius: float,
    ring_points: int,
) -> AbilityBuilder:
    return (
        AbilityBuilder(ability_id)
        .name(name)
        .action(particles_ring(ring_particle, ring_radius, ring_points, count=1))
    )


def aura_pulse(
    ability_id: str,
    name: str,
    particle: EnumValue,
    radius: float,
    points: int,
    duration_ticks: int = 80,
    period_ticks: int = 10,
) -> AbilityBuilder:
    ring = particles_ring(particle, radius, points, count=1)
    return (
        AbilityBuilder(ability_id)
        .name(name)
        .action(pulse(ring, duration_ticks=duration_ticks, period_ticks=period_ticks))
    )


def projectile_burst_spell(
    ability_id: str,
    name: str,
    range_blocks: float,
    on_hit: Action,
    shots: int = 5,
    spacing_ticks: int = 2,
) -> AbilityBuilder:
    return (
        AbilityBuilder(ability_id)
        .name(name)
        .action(burst(raycast_hit_entity(range_blocks, on_hit), times=shots, spacing_ticks=spacing_ticks))
    )


def chain_spell(
    ability_id: str,
    name: str,
    amount: float,
    radius: float,
    jumps: int = 4,
    particle: EnumValue = Particle.ELECTRIC_SPARK,
) -> AbilityBuilder:
    chain = sequence(
        particles_ring(particle, radius=1.4, points=18, count=1),
        damage_chain(amount=amount, radius=radius, jumps=jumps),
    )
    return AbilityBuilder(ability_id).name(name).action(chain)


def shockwave(
    ability_id: str,
    name: str,
    particle: str,
    radius: float,
    points: int,
) -> AbilityBuilder:
    return (
        AbilityBuilder(ability_id)
        .name(name)
        .action(particles_ring(particle, radius, points, count=1))
    )


def beam_spell(
    ability_id: str,
    name: str,
    particle: str,
    range_blocks: float,
    on_hit: Action,
    ) -> AbilityBuilder:
    return (
        AbilityBuilder(ability_id)
        .name(name)
        .action(raycast_hit_entity(range_blocks, on_hit))
    )


def effect_bolt(
    ability_id: str,
    name: str,
    particle: EnumValue,
    range_blocks: float,
    effect: str,
    duration_ticks: int,
    amplifier: int = 0,
    sound_id: Optional[str] = None,
) -> AbilityBuilder:
    hit = sequence(
        potion(effect, duration_ticks, amplifier),
        particles_ring(particle, radius=1.2, points=18, count=1),
    )
    if sound_id:
        hit = sequence(sound(sound_id), hit)
    return projectile_spell(ability_id, name, particle, range_blocks, hit)


def aoe_pulse(
    ability_id: str,
    name: str,
    particle: EnumValue,
    radius: float,
    damage_amount: float = 0.0,
    effect: Optional[str] = None,
    duration_ticks: int = 0,
    amplifier: int = 0,
) -> AbilityBuilder:
    actions = [particles_ring(particle, radius=radius, points=24, count=1)]
    if damage_amount > 0:
        actions.append(Action("damage", {"amount": damage_amount}))
    if effect:
        actions.append(Action("potion", {"effect": effect, "durationTicks": duration_ticks, "amplifier": amplifier}))
    return AbilityBuilder(ability_id).name(name).action(sequence(*actions))


def status_aura(
    ability_id: str,
    name: str,
    particle: EnumValue,
    radius: float,
    effect: str,
    duration_ticks: int,
    amplifier: int = 0,
) -> AbilityBuilder:
    aura_action = sequence(
        particles_ring(particle, radius=radius, points=24, count=1),
        Action("potion", {"effect": effect, "durationTicks": duration_ticks, "amplifier": amplifier}),
    )
    return AbilityBuilder(ability_id).name(name).action(aura_action)


class AbilityPipeline:
    def __init__(self, ability: Ability) -> None:
        self.ability = ability

    def write(self, exporter: EffectsExporter, filename: Optional[str] = None) -> str:
        return exporter.write_ability(self.ability, filename=filename)


def ability_pack_undead_t1(prefix: str = "undead_t1") -> List[AbilityBuilder]:
    return [
        shockwave(f"{prefix}_pulse", "<gray>Undead Pulse</gray>", Particle.SMOKE, 1.6, 24),
        aura_field(f"{prefix}_aura", "<dark_gray>Grave Aura</dark_gray>", Particle.ASH, 1.8, 28),
    ]


def ability_pack_elemental_fire(prefix: str = "fire_t1") -> List[AbilityBuilder]:
    return [
        projectile_spell(
            f"{prefix}_bolt",
            "<red>Fire Bolt</red>",
            Particle.FLAME,
            14.0,
            potion("SLOWNESS", 40, 0),
        ),
        shockwave(f"{prefix}_blast", "<gold>Ember Blast</gold>", Particle.FLAME, 2.0, 30),
    ]
