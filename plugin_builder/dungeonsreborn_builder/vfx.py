"""VFX presets for effects builder."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Tuple, Union, Literal

from .effects import Action, particles_ring
from .animation import AnimationBuilder, VFXPreset
from .vanilla import EnumValue, Particle

AtTarget = Union[Literal["origin", "last_hit", "last_entity"], EnumValue]
Easing = Union[
    Literal[
        "LINEAR",
        "IN",
        "OUT",
        "IN_OUT",
        "IN_OUT_CUBIC",
        "IN_OUT_QUAD",
        "IN_OUT_QUART",
        "IN_OUT_QUINT",
        "IN_OUT_SINE",
        "IN_OUT_EXPO",
        "IN_OUT_BACK",
        "IN_OUT_ELASTIC",
        "IN_OUT_BOUNCE",
        "IN_CUBIC",
        "OUT_CUBIC",
        "IN_QUAD",
        "OUT_QUAD",
        "IN_QUART",
        "OUT_QUART",
        "IN_QUINT",
        "OUT_QUINT",
        "IN_SINE",
        "OUT_SINE",
        "IN_EXPO",
        "OUT_EXPO",
        "IN_BACK",
        "OUT_BACK",
        "IN_ELASTIC",
        "OUT_ELASTIC",
        "IN_BOUNCE",
        "OUT_BOUNCE",
    ],
    EnumValue,
]
MotionMode = Union[Literal["translate", "orbit", "helix", "spiral"], EnumValue]

_ALLOWED_AT = {"origin", "last_hit", "last_entity"}


@dataclass(frozen=True)
class VfxPresetMeta:
    name: str
    preset: VFXPreset
    tags: List[str] = field(default_factory=list)
    description: str = ""

    def to_dict(self) -> Dict[str, object]:
        payload: Dict[str, object] = {"name": self.name, "lines": list(self.preset.lines)}
        if self.tags:
            payload["tags"] = list(self.tags)
        if self.description:
            payload["description"] = self.description
        return payload


class VfxRegistry:
    def __init__(self) -> None:
        self._presets: Dict[str, VfxPresetMeta] = {}

    def register(self, meta: VfxPresetMeta) -> "VfxRegistry":
        self._presets[meta.name] = meta
        return self

    def register_preset(
        self,
        name: str,
        preset: VFXPreset,
        tags: Optional[List[str]] = None,
        description: str = "",
    ) -> "VfxRegistry":
        return self.register(VfxPresetMeta(name=name, preset=preset, tags=tags or [], description=description))

    def get(self, name: str) -> Optional[VfxPresetMeta]:
        return self._presets.get(name)

    def list(self) -> List[VfxPresetMeta]:
        return list(self._presets.values())

    def to_catalog(self) -> Dict[str, Dict[str, object]]:
        return {name: meta.to_dict() for name, meta in self._presets.items()}


def combo(*presets: VFXPreset) -> VFXPreset:
    """Merge multiple presets into a single block."""
    lines: List[str] = []
    for preset in presets:
        lines.extend(preset.lines)
    return VFXPreset(lines)


def timeline(*presets: VFXPreset) -> AnimationBuilder:
    """Quick timeline helper that bursts each preset in order."""
    builder = AnimationBuilder()
    for preset in presets:
        builder.burst(preset)
    return builder


def ring(particle: EnumValue, radius: float, points: int = 24, count: int = 1) -> Action:
    return particles_ring(particle, radius, points, count=count)


def shockwave(particle: EnumValue, radius: float = 2.0, points: int = 28) -> Action:
    return particles_ring(particle, radius, points, count=1)


def orbit(particle: EnumValue, radius: float = 2.4, duration_ticks: int = 60, period_ticks: int = 2, copies: int = 3) -> Action:
    return Action(
        "particles.orbit",
        {
            "particle": str(particle),
            "radius": radius,
            "durationTicks": duration_ticks,
            "periodTicks": period_ticks,
            "copies": copies,
            "count": 1,
        },
    )


def helix(particle: EnumValue, radius: float = 2.0, length: float = 4.0, turns: int = 4, points: int = 80) -> Action:
    return Action(
        "particles.helix",
        {
            "particle": str(particle),
            "radius": radius,
            "length": length,
            "turns": turns,
            "points": points,
            "count": 1,
        },
    )


def swirl(particle: EnumValue, radius: float = 2.0, height: float = 3.0, duration_ticks: int = 60, period_ticks: int = 2, points: int = 28) -> Action:
    return Action(
        "particles.swirl",
        {
            "particle": str(particle),
            "radius": radius,
            "height": height,
            "durationTicks": duration_ticks,
            "periodTicks": period_ticks,
            "points": points,
            "count": 1,
        },
    )


def stack(*actions: Action) -> Action:
    return Action("sequence", {"actions": [action.to_dict() for action in actions]})


def stagger(actions: List[Action], gap_ticks: int = 2) -> Action:
    seq = []
    for action in actions:
        seq.append(Action("delay", {"ticks": gap_ticks, "action": action.to_dict()}))
    return Action("sequence", {"actions": [item.to_dict() for item in seq]})


def palette_fire(primary: str = "#ff6a00", secondary: str = "#ffd166") -> List[str]:
    return [primary, secondary]


def palette_frost(primary: str = "#7fd4ff", secondary: str = "#bde7ff") -> List[str]:
    return [primary, secondary]


def palette_storm(primary: str = "#5ad1ff", secondary: str = "#9b59ff") -> List[str]:
    return [primary, secondary]


def palette_earth(primary: str = "#6b8e23", secondary: str = "#8b6f47") -> List[str]:
    return [primary, secondary]


def palette_arcane(primary: str = "#b168ff", secondary: str = "#7fd4ff") -> List[str]:
    return [primary, secondary]


def preset_fire_core(radius: float = 2.2) -> Action:
    return stack(
        ring(Particle.FLAME, radius, 28),
        orbit(Particle.FLAME, radius=radius + 0.4, copies=5),
    )


def preset_frost_core(radius: float = 2.2) -> Action:
    return stack(
        ring(Particle.SNOWFLAKE, radius, 28),
        orbit(Particle.END_ROD, radius=radius + 0.4, copies=4),
    )


def preset_storm_core(radius: float = 2.2) -> Action:
    return stack(
        ring(Particle.ELECTRIC_SPARK, radius, 28),
        swirl(Particle.CLOUD, radius=radius + 0.3, height=3.2),
    )


def preset_earth_core(radius: float = 2.2) -> Action:
    return stack(
        ring(Particle.ASH, radius, 28),
        orbit(Particle.SMOKE, radius=radius + 0.4, copies=4),
    )


def preset_arcane_core(radius: float = 2.2) -> Action:
    return stack(
        ring(Particle.END_ROD, radius, 26),
        helix(Particle.END_ROD, radius=radius - 0.4, length=3.8, turns=5),
    )


def cloud(particle: EnumValue, radius: float = 2.4, height: float = 1.2, points: int = 36) -> Action:
    return Action(
        "particles.cylinder",
        {
            "particle": str(particle),
            "radius": radius,
            "height": height,
            "points": points,
            "count": 1,
        },
    )


def sphere(particle: EnumValue, radius: float = 2.4, points: int = 60, filled: bool = False) -> Action:
    return Action(
        "particles.sphere_filled" if filled else "particles.sphere_shell",
        {
            "particle": str(particle),
            "radius": radius,
            "points": points,
            "count": 1,
        },
    )


def cone_forward(particle: EnumValue, radius: float = 2.0, height: float = 3.0, points: int = 36) -> Action:
    return Action(
        "particles.cone",
        {
            "particle": str(particle),
            "radius": radius,
            "height": height,
            "points": points,
            "count": 1,
        },
    )


def arc_side(particle: EnumValue, radius: float = 2.2, angle_deg: float = 140.0, points: int = 24) -> Action:
    return Action(
        "particles.arc",
        {
            "particle": str(particle),
            "radius": radius,
            "angle": angle_deg,
            "points": points,
            "count": 1,
        },
    )


def preset_fire_signature(radius: float = 2.6) -> Action:
    return stack(
        helix(Particle.FLAME, radius=radius - 0.3, length=4.2, turns=6),
        orbit(Particle.SOUL_FIRE_FLAME, radius=radius + 0.4, copies=6),
    )


def preset_frost_signature(radius: float = 2.6) -> Action:
    return stack(
        helix(Particle.SNOWFLAKE, radius=radius - 0.3, length=4.2, turns=6),
        orbit(Particle.END_ROD, radius=radius + 0.4, copies=5),
    )


def preset_storm_signature(radius: float = 2.6) -> Action:
    return stack(
        swirl(Particle.ELECTRIC_SPARK, radius=radius - 0.2, height=3.6, points=30),
        orbit(Particle.CLOUD, radius=radius + 0.5, copies=6),
    )


def preset_earth_signature(radius: float = 2.6) -> Action:
    return stack(
        helix(Particle.ASH, radius=radius - 0.2, length=3.8, turns=5),
        orbit(Particle.SMOKE, radius=radius + 0.5, copies=5),
    )


def preset_arcane_signature(radius: float = 2.6) -> Action:
    return stack(
        helix(Particle.END_ROD, radius=radius - 0.2, length=4.0, turns=6),
        orbit(Particle.ENCHANT, radius=radius + 0.5, copies=6),
    )


def dsl_ring(
    particle: EnumValue,
    radius: float,
    points: int = 24,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.ring particle={particle} radius={radius} points={points} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_orbit(
    particle: EnumValue,
    radius: float,
    duration_ticks: int = 60,
    period_ticks: int = 2,
    copies: int = 3,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.orbit particle={particle} radius={radius} durationTicks={duration_ticks} periodTicks={period_ticks} copies={copies} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_helix(
    particle: EnumValue,
    radius: float,
    length: float,
    turns: int = 6,
    points: int = 120,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.helix particle={particle} radius={radius} length={length} turns={turns} points={points} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def _point_attrs(points: Iterable[Tuple[float, float, float]]) -> str:
    parts: List[str] = []
    for idx, (x, y, z) in enumerate(points):
        parts.append(f"p{idx}_x={x}")
        parts.append(f"p{idx}_y={y}")
        parts.append(f"p{idx}_z={z}")
    return " ".join(parts)


def _frame_suffix(
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> str:
    parts: List[str] = []
    normalized_at = _normalize_at(at)
    if normalized_at:
        parts.append(f"at={normalized_at}")
    if forward:
        parts.append(f"forward={forward}")
    if right:
        parts.append(f"right={right}")
    if up:
        parts.append(f"up={up}")
    return " ".join(parts)


def _normalize_at(at: Optional[AtTarget]) -> Optional[str]:
    if at is None:
        return None
    if isinstance(at, EnumValue):
        value = at.name
    else:
        value = at
    if value not in _ALLOWED_AT:
        raise ValueError(f"Invalid at='{value}'. Allowed: {', '.join(sorted(_ALLOWED_AT))}.")
    return value


def _normalize_easing(easing: Easing) -> str:
    if isinstance(easing, EnumValue):
        return easing.name
    return str(easing)


def _append_suffix(line: str, suffix: str) -> str:
    if not suffix:
        return line
    return f"{line} {suffix}"


def dsl_point(
    particle: EnumValue,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.point particle={particle} count={count} offset={offset} extra={extra}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_line(
    particle: EnumValue,
    length: float = 6.0,
    step: float = 0.3,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.line particle={particle} length={length} step={step} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_arc(
    particle: EnumValue,
    radius: float = 2.0,
    angle_degrees: float = 140.0,
    points: int = 24,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.arc particle={particle} radius={radius} angleDegrees={angle_degrees} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_disk(
    particle: EnumValue,
    radius: float = 2.2,
    points: int = 36,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.disk particle={particle} radius={radius} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_sphere_shell(
    particle: EnumValue,
    radius: float = 2.2,
    points: int = 60,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.sphere_shell particle={particle} radius={radius} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_sphere_filled(
    particle: EnumValue,
    radius: float = 2.2,
    points: int = 60,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.sphere_filled particle={particle} radius={radius} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_bezier(
    particle: EnumValue,
    points: Iterable[Tuple[float, float, float]],
    step: float = 0.2,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = _point_attrs(points)
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.bezier particle={particle} step={step} count={count} {attrs}".strip()
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_spline(
    particle: EnumValue,
    points: Iterable[Tuple[float, float, float]],
    points_per_meter: float = 10.0,
    max_points: int = 320,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = _point_attrs(points)
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.spline particle={particle} pointsPerMeter={points_per_meter} maxPoints={max_points} count={count} {attrs}".strip()
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_spline_motion(
    particle: EnumValue,
    points: Iterable[Tuple[float, float, float]],
    duration_ticks: int = 40,
    period_ticks: int = 1,
    points_per_meter: float = 10.0,
    max_points: int = 320,
    easing: Easing = "IN_OUT_CUBIC",
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = _point_attrs(points)
    suffix = _frame_suffix(at, forward, right, up)
    return VFXPreset(
        [
            _append_suffix(
                f"particles.spline_motion particle={particle} durationTicks={duration_ticks} periodTicks={period_ticks} easing={_normalize_easing(easing)} pointsPerMeter={points_per_meter} maxPoints={max_points} count={count} {attrs}".strip(),
                suffix,
            )
        ]
    )


def dsl_cone(
    particle: EnumValue,
    radius: float = 2.0,
    height: float = 3.0,
    points: int = 36,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.cone particle={particle} radius={radius} height={height} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_cylinder(
    particle: EnumValue,
    radius: float = 2.2,
    height: float = 3.2,
    points: int = 36,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.cylinder particle={particle} radius={radius} height={height} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_box(
    particle: EnumValue,
    x_radius: float = 2.0,
    y_radius: float = 2.0,
    z_radius: float = 2.0,
    step: float = 0.5,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.box particle={particle} xRadius={x_radius} yRadius={y_radius} zRadius={z_radius} step={step} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_polygon(
    particle: EnumValue,
    radius: float = 1.6,
    sides: int = 6,
    points_per_edge: int = 5,
    count: int = 1,
    color: Optional[str] = None,
    size: Optional[float] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.polygon particle={particle} radius={radius} sides={sides} pointsPerEdge={points_per_edge} count={count}"
    if color:
        line += f" color=\"{color}\""
    if size is not None:
        line += f" size={size}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_swirl(
    particle: EnumValue,
    radius: float = 2.0,
    height: float = 3.0,
    duration_ticks: int = 60,
    period_ticks: int = 2,
    points: int = 28,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.swirl particle={particle} radius={radius} height={height} durationTicks={duration_ticks} periodTicks={period_ticks} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_shockwave(
    particle: EnumValue,
    radius: float = 2.2,
    points: int = 28,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.shockwave particle={particle} radius={radius} points={points} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_beam_chargeup(
    particle: EnumValue,
    duration_ticks: int = 40,
    count: int = 1,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.beam_chargeup particle={particle} durationTicks={duration_ticks} count={count}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_points(
    particle: EnumValue,
    points: Optional[Iterable[Tuple[float, float, float]]] = None,
    shape: Optional[str] = None,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    start_color: Optional[str] = None,
    end_color: Optional[str] = None,
    color: Optional[str] = None,
    size: float = 1.0,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = [f"particles.points particle={particle} count={count} offset={offset} extra={extra} size={size}"]
    if shape:
        attrs.append(f"shape={shape}")
    elif points is not None:
        attrs.append(_point_attrs(points))
    if start_color:
        attrs.append(f"startColor=\"{start_color}\"")
    if end_color:
        attrs.append(f"endColor=\"{end_color}\"")
    if color is None:
        color = start_color
    if color:
        attrs.append(f"color=\"{color}\"")
    suffix = _frame_suffix(at, forward, right, up)
    if suffix:
        attrs.append(suffix)
    return VFXPreset([" ".join(attrs).strip()])


def dsl_polyline(
    particle: EnumValue,
    points: Optional[Iterable[Tuple[float, float, float]]] = None,
    shape: Optional[str] = None,
    step: float = 0.5,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    start_color: Optional[str] = None,
    end_color: Optional[str] = None,
    color: Optional[str] = None,
    size: float = 1.0,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = [f"particles.polyline particle={particle} step={step} count={count} offset={offset} extra={extra} size={size}"]
    if shape:
        attrs.append(f"shape={shape}")
    elif points is not None:
        attrs.append(_point_attrs(points))
    if start_color:
        attrs.append(f"startColor=\"{start_color}\"")
    if end_color:
        attrs.append(f"endColor=\"{end_color}\"")
    if color is None:
        color = start_color
    if color:
        attrs.append(f"color=\"{color}\"")
    suffix = _frame_suffix(at, forward, right, up)
    if suffix:
        attrs.append(suffix)
    return VFXPreset([" ".join(attrs).strip()])


def dsl_mesh(
    particle: EnumValue,
    shape: str,
    step: float = 0.75,
    count: int = 1,
    offset: float = 0.0,
    extra: float = 0.0,
    start_color: Optional[str] = None,
    end_color: Optional[str] = None,
    color: Optional[str] = None,
    size: float = 1.0,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = [f"particles.mesh particle={particle} shape={shape} step={step} count={count} offset={offset} extra={extra} size={size}"]
    if start_color:
        attrs.append(f"startColor=\"{start_color}\"")
    if end_color:
        attrs.append(f"endColor=\"{end_color}\"")
    if color is None:
        color = start_color
    if color:
        attrs.append(f"color=\"{color}\"")
    suffix = _frame_suffix(at, forward, right, up)
    if suffix:
        attrs.append(suffix)
    return VFXPreset([" ".join(attrs).strip()])


def dsl_physics_points(
    particle: EnumValue,
    points: Optional[Iterable[Tuple[float, float, float]]] = None,
    shape: Optional[str] = None,
    count: int = 1,
    velocity: Tuple[float, float, float] = (0.0, 0.2, 0.0),
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
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = [
        f"particles.physics_points particle={particle} count={count}",
        f"velocityX={velocity[0]} velocityY={velocity[1]} velocityZ={velocity[2]}",
        f"spread={spread} gravity={gravity} drag={drag}",
        f"steps={steps} periodTicks={period_ticks}",
        f"collide={'true' if collide else 'false'} collisionMode={collision_mode} restitution={restitution}",
        f"offset={offset} extra={extra}",
    ]
    if shape:
        attrs.append(f"shape={shape}")
    elif points is not None:
        attrs.append(_point_attrs(points))
    if color:
        attrs.append(f"color=\"{color}\"")
    suffix = _frame_suffix(at, forward, right, up)
    if suffix:
        attrs.append(suffix)
    return VFXPreset([" ".join(attrs).strip()])


def dsl_physics_polyline(
    particle: EnumValue,
    points: Optional[Iterable[Tuple[float, float, float]]] = None,
    shape: Optional[str] = None,
    step: float = 0.5,
    count: int = 1,
    velocity: Tuple[float, float, float] = (0.0, 0.2, 0.0),
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
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = [
        f"particles.physics_polyline particle={particle} step={step} count={count}",
        f"velocityX={velocity[0]} velocityY={velocity[1]} velocityZ={velocity[2]}",
        f"spread={spread} gravity={gravity} drag={drag}",
        f"steps={steps} periodTicks={period_ticks}",
        f"collide={'true' if collide else 'false'} collisionMode={collision_mode} restitution={restitution}",
        f"offset={offset} extra={extra}",
    ]
    if shape:
        attrs.append(f"shape={shape}")
    elif points is not None:
        attrs.append(_point_attrs(points))
    if color:
        attrs.append(f"color=\"{color}\"")
    suffix = _frame_suffix(at, forward, right, up)
    if suffix:
        attrs.append(suffix)
    return VFXPreset([" ".join(attrs).strip()])


def dsl_physics_mesh(
    particle: EnumValue,
    shape: str,
    step: float = 0.75,
    count: int = 1,
    velocity: Tuple[float, float, float] = (0.0, 0.2, 0.0),
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
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    attrs = [
        f"particles.physics_mesh particle={particle} shape={shape} step={step} count={count}",
        f"velocityX={velocity[0]} velocityY={velocity[1]} velocityZ={velocity[2]}",
        f"spread={spread} gravity={gravity} drag={drag}",
        f"steps={steps} periodTicks={period_ticks}",
        f"collide={'true' if collide else 'false'} collisionMode={collision_mode} restitution={restitution}",
        f"offset={offset} extra={extra}",
    ]
    if color:
        attrs.append(f"color=\"{color}\"")
    suffix = _frame_suffix(at, forward, right, up)
    if suffix:
        attrs.append(suffix)
    return VFXPreset([" ".join(attrs).strip()])


def dsl_orbiting_runes(
    particle: EnumValue,
    radius: float = 2.2,
    copies: int = 6,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.orbiting_runes particle={particle} radius={radius} copies={copies} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_spiral_aura(
    particle: EnumValue,
    radius: float = 1.8,
    height: float = 4.0,
    duration_ticks: int = 60,
    period_ticks: int = 2,
    color: Optional[str] = None,
    size: Optional[float] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.spiral_aura particle={particle} radius={radius} height={height} durationTicks={duration_ticks} periodTicks={period_ticks} count=1"
    if color:
        line += f" color=\"{color}\""
    if size is not None:
        line += f" size={size}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_motion(
    inner: VFXPreset,
    mode: MotionMode = "translate",
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Easing = "IN_OUT_CUBIC",
    velocity: Tuple[float, float, float] = (0.0, 0.0, 0.0),
    radius: float = 0.0,
    turns: float = 1.0,
    vertical: float = 0.0,
    drift: float = 0.0,
    drift_vertical: float = 0.0,
    drift_speed: float = 0.35,
    at: AtTarget = "origin",
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    normalized_at = _normalize_at(at)
    header = (
        f"motion mode={mode} durationTicks={duration_ticks} periodTicks={period_ticks} easing={_normalize_easing(easing)} "
        f"velocityX={velocity[0]} velocityY={velocity[1]} velocityZ={velocity[2]} "
        f"radius={radius} turns={turns} vertical={vertical} drift={drift} driftVertical={drift_vertical} driftSpeed={drift_speed} "
        f"at={normalized_at} forward={forward} right={right} up={up}"
    )
    lines = [header + " {"]
    lines.extend([f"  {line}" for line in inner.lines])
    lines.append("}")
    return VFXPreset(lines)


def dsl_state_machine(
    charge: VFXPreset,
    sustain: VFXPreset,
    release: VFXPreset,
    charge_ticks: int = 20,
    sustain_ticks: int = 40,
    release_ticks: int = 20,
    period_ticks: int = 1,
    easing: Easing = "IN_OUT_CUBIC",
) -> VFXPreset:
    lines = [
        f"state_machine chargeTicks={charge_ticks} sustainTicks={sustain_ticks} releaseTicks={release_ticks} periodTicks={period_ticks} easing={_normalize_easing(easing)}",
        "charge {",
    ]
    lines.extend([f"  {line}" for line in charge.lines])
    lines.append("}")
    lines.append("sustain {")
    lines.extend([f"  {line}" for line in sustain.lines])
    lines.append("}")
    lines.append("release {")
    lines.extend([f"  {line}" for line in release.lines])
    lines.append("}")
    return VFXPreset(lines)


def dsl_morph_ring(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    points: int = 32,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_ring particle={particle} startRadius={start_radius} endRadius={end_radius} durationTicks={duration_ticks} periodTicks={period_ticks} points={points} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_ring(
    particle: EnumValue,
    radius: float,
    points: int = 32,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_ring particle={particle} radius={radius} points={points} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_line(
    particle: EnumValue,
    start_length: float,
    end_length: float,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    step: float = 0.3,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
    target_at: Optional[AtTarget] = None,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_line particle={particle} startLength={start_length} endLength={end_length} durationTicks={duration_ticks} periodTicks={period_ticks} step={step} easing={_normalize_easing(easing)} count=1"
    if target_at:
        line += f" targetAt={target_at}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_line(
    particle: EnumValue,
    length: float,
    step: float = 0.3,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
    target_at: Optional[AtTarget] = None,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_line particle={particle} length={length} step={step} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    if color is None:
        color = start_color
    if color:
        line += f" color=\"{color}\""
    if target_at:
        line += f" targetAt={target_at}"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_arc(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    angle_degrees: float = 140.0,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    points: int = 24,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_arc particle={particle} startRadius={start_radius} endRadius={end_radius} angleDegrees={angle_degrees} durationTicks={duration_ticks} periodTicks={period_ticks} points={points} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_arc(
    particle: EnumValue,
    radius: float,
    angle_degrees: float = 140.0,
    points: int = 24,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_arc particle={particle} radius={radius} angleDegrees={angle_degrees} points={points} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    if color is None:
        color = start_color
    if color:
        line += f" color=\"{color}\""
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_disk(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    points: int = 36,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_disk particle={particle} startRadius={start_radius} endRadius={end_radius} durationTicks={duration_ticks} periodTicks={period_ticks} points={points} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_disk(
    particle: EnumValue,
    radius: float,
    points: int = 36,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_disk particle={particle} radius={radius} points={points} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    if color is None:
        color = start_color
    if color:
        line += f" color=\"{color}\""
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_sphere_shell(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    points: int = 60,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_sphere_shell particle={particle} startRadius={start_radius} endRadius={end_radius} durationTicks={duration_ticks} periodTicks={period_ticks} points={points} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_sphere_shell(
    particle: EnumValue,
    radius: float,
    points: int = 60,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_sphere_shell particle={particle} radius={radius} points={points} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    if color is None:
        color = start_color
    if color:
        line += f" color=\"{color}\""
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_sphere_filled(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    points: int = 60,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_sphere_filled particle={particle} startRadius={start_radius} endRadius={end_radius} durationTicks={duration_ticks} periodTicks={period_ticks} points={points} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_sphere_filled(
    particle: EnumValue,
    radius: float,
    points: int = 60,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_sphere_filled particle={particle} radius={radius} points={points} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    if color is None:
        color = start_color
    if color:
        line += f" color=\"{color}\""
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_helix(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    length: float = 4.0,
    turns: int = 6,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    points: int = 120,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_helix particle={particle} startRadius={start_radius} endRadius={end_radius} length={length} turns={turns} durationTicks={duration_ticks} periodTicks={period_ticks} points={points} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_helix(
    particle: EnumValue,
    radius: float,
    length: float = 4.0,
    turns: int = 6,
    points: int = 120,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    color: Optional[str] = None,
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_helix particle={particle} radius={radius} length={length} turns={turns} points={points} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    if color is None:
        color = start_color
    if color:
        line += f" color=\"{color}\""
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_cone(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    angle_degrees: float = 35.0,
    rings: int = 6,
    points_per_ring: int = 24,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_cone particle={particle} startRadius={start_radius} endRadius={end_radius} angleDegrees={angle_degrees} rings={rings} pointsPerRing={points_per_ring} durationTicks={duration_ticks} periodTicks={period_ticks} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_cone(
    particle: EnumValue,
    radius: float,
    angle_degrees: float = 35.0,
    rings: int = 6,
    points_per_ring: int = 24,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_cone particle={particle} radius={radius} angleDegrees={angle_degrees} rings={rings} pointsPerRing={points_per_ring} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_cylinder(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    height: float = 4.0,
    rings: int = 6,
    points_per_ring: int = 28,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_cylinder particle={particle} startRadius={start_radius} endRadius={end_radius} height={height} rings={rings} pointsPerRing={points_per_ring} durationTicks={duration_ticks} periodTicks={period_ticks} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_cylinder(
    particle: EnumValue,
    radius: float,
    height: float = 4.0,
    rings: int = 6,
    points_per_ring: int = 28,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_cylinder particle={particle} radius={radius} height={height} rings={rings} pointsPerRing={points_per_ring} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_box(
    particle: EnumValue,
    start_x: float,
    start_y: float,
    start_z: float,
    end_x: float,
    end_y: float,
    end_z: float,
    step: float = 0.5,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_box particle={particle} startX={start_x} startY={start_y} startZ={start_z} endX={end_x} endY={end_y} endZ={end_z} step={step} durationTicks={duration_ticks} periodTicks={period_ticks} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_box(
    particle: EnumValue,
    x_radius: float,
    y_radius: float,
    z_radius: float,
    step: float = 0.5,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_box particle={particle} xRadius={x_radius} yRadius={y_radius} zRadius={z_radius} step={step} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_morph_polygon(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    sides: int = 6,
    points_per_edge: int = 5,
    duration_ticks: int = 40,
    period_ticks: int = 1,
    easing: Easing = "IN_OUT_CUBIC",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.morph_polygon particle={particle} startRadius={start_radius} endRadius={end_radius} sides={sides} pointsPerEdge={points_per_edge} durationTicks={duration_ticks} periodTicks={period_ticks} easing={_normalize_easing(easing)} count=1"
    return VFXPreset([_append_suffix(line, suffix)])


def dsl_gradient_polygon(
    particle: EnumValue,
    radius: float,
    sides: int = 6,
    points_per_edge: int = 5,
    start_color: str = "#ff6a00",
    end_color: str = "#7fd4ff",
    at: Optional[AtTarget] = None,
    forward: float = 0.0,
    right: float = 0.0,
    up: float = 0.0,
) -> VFXPreset:
    suffix = _frame_suffix(at, forward, right, up)
    line = f"particles.gradient_polygon particle={particle} radius={radius} sides={sides} pointsPerEdge={points_per_edge} startColor=\"{start_color}\" endColor=\"{end_color}\" count=1"
    return VFXPreset([_append_suffix(line, suffix)])

def preset_dsl_fire_core(radius: float = 2.2) -> VFXPreset:
    return dsl_ring(Particle.FLAME, radius, 28) + dsl_orbit(Particle.FLAME, radius + 0.4, copies=5)


def preset_dsl_frost_core(radius: float = 2.2) -> VFXPreset:
    return dsl_ring(Particle.SNOWFLAKE, radius, 28) + dsl_orbit(Particle.END_ROD, radius + 0.4, copies=4)


def staged_transition(presets: List[VFXPreset], step_ticks: int = 4) -> AnimationBuilder:
    builder = AnimationBuilder()
    if not presets:
        return builder
    builder.burst(presets[0])
    for index, preset in enumerate(presets[1:], start=1):
        builder.schedule(step_ticks * index, preset)
    return builder


def aura_timeline(
    particle: EnumValue,
    radius: float = 2.2,
    points: int = 28,
    orbit_radius: Optional[float] = None,
    orbit_copies: int = 5,
    orbit_duration_ticks: int = 60,
    orbit_period_ticks: int = 2,
) -> AnimationBuilder:
    """High-level aura: ring + orbit in a ready-to-cast timeline."""
    orbit_radius = orbit_radius if orbit_radius is not None else radius + 0.4
    return timeline(
        dsl_ring(particle, radius=radius, points=points),
        dsl_orbit(particle, radius=orbit_radius, duration_ticks=orbit_duration_ticks, period_ticks=orbit_period_ticks, copies=orbit_copies),
    )


def _lerp(start: float, end: float, step: int, steps: int) -> float:
    if steps <= 1:
        return end
    return start + (end - start) * (step / (steps - 1))


def _hex_to_rgb(color: str) -> Tuple[int, int, int]:
    value = color.lstrip("#")
    if len(value) == 3:
        value = "".join([c * 2 for c in value])
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def _rgb_to_hex(rgb: Tuple[int, int, int]) -> str:
    return "#%02x%02x%02x" % rgb


def gradient_colors(start: str, end: str, steps: int) -> List[str]:
    start_rgb = _hex_to_rgb(start)
    end_rgb = _hex_to_rgb(end)
    colors = []
    for i in range(steps):
        r = int(_lerp(start_rgb[0], end_rgb[0], i, steps))
        g = int(_lerp(start_rgb[1], end_rgb[1], i, steps))
        b = int(_lerp(start_rgb[2], end_rgb[2], i, steps))
        colors.append(_rgb_to_hex((r, g, b)))
    return colors


def gradient_ring(
    particle: EnumValue,
    radius: float,
    start_color: str,
    end_color: str,
    steps: int = 6,
    points: int = 28,
) -> AnimationBuilder:
    colors = gradient_colors(start_color, end_color, steps)
    presets = [
        VFXPreset(
            [
                f"particles.ring particle={particle} radius={radius} points={points} count=1 color=\"{color}\""
            ]
        )
        for color in colors
    ]
    return staged_transition(presets, step_ticks=4)


def path_color_steps(
    particle: EnumValue,
    points: Iterable[Tuple[float, float, float]],
    start_color: str,
    end_color: str,
    steps: int = 6,
) -> List[VFXPreset]:
    colors = gradient_colors(start_color, end_color, steps)
    path = list(points)
    presets = []
    for color in colors:
        presets.append(
            VFXPreset(
                [
                    f"particles.path particle={particle} points={path} count=1 color=\"{color}\""
                ]
            )
        )
    return presets


def gradient_path(
    particle: EnumValue,
    points: Iterable[Tuple[float, float, float]],
    start_color: str,
    end_color: str,
    steps: int = 6,
    step_ticks: int = 4,
) -> AnimationBuilder:
    presets = path_color_steps(particle, points, start_color, end_color, steps)
    return staged_transition(presets, step_ticks=step_ticks)


def cinematic_title(
    title: str,
    subtitle: str = "",
    fade_in: int = 10,
    stay: int = 40,
    fade_out: int = 10,
) -> VFXPreset:
    line = (
        "title "
        f"text=\"{title}\" "
        f"subtitle=\"{subtitle}\" "
        f"fadeIn={fade_in} stay={stay} fadeOut={fade_out}"
    )
    return VFXPreset([line])


def cinematic_screen_shake(intensity: float = 0.8, duration_ticks: int = 20) -> VFXPreset:
    return VFXPreset([f"screen_shake intensity={intensity} durationTicks={duration_ticks}"])


def cinematic_burst(
    particle: EnumValue,
    title: str,
    subtitle: str = "",
) -> VFXPreset:
    return cinematic_title(title, subtitle) + VFXPreset(
        [f"particles.point particle={particle} count=16 offset=0.2"]
    )


def morph_ring(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    steps: int = 6,
    points: int = 28,
    step_ticks: int = 4,
) -> AnimationBuilder:
    presets = [
        dsl_ring(particle, radius=_lerp(start_radius, end_radius, i, steps), points=points)
        for i in range(steps)
    ]
    return staged_transition(presets, step_ticks=step_ticks)


def morph_ring_to_helix(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    length: float = 4.0,
    turns: int = 6,
    steps: int = 6,
    points: int = 120,
    step_ticks: int = 4,
) -> AnimationBuilder:
    ring_steps = [
        dsl_ring(particle, radius=_lerp(start_radius, end_radius, i, steps), points=max(16, points // 4))
        for i in range(steps)
    ]
    helix_steps = [
        dsl_helix(particle, radius=_lerp(end_radius * 0.6, end_radius, i, steps), length=length, turns=turns, points=points)
        for i in range(steps)
    ]
    return staged_transition(ring_steps + helix_steps, step_ticks=step_ticks)


def charge_sustain_release(
    charge: VFXPreset,
    sustain: VFXPreset,
    release: VFXPreset,
    charge_ticks: int = 20,
    sustain_ticks: int = 60,
    release_ticks: int = 20,
) -> AnimationBuilder:
    builder = AnimationBuilder()
    if charge:
        builder.burst(charge)
    if sustain_ticks > 0:
        builder.loop(times=max(1, sustain_ticks // 4), every=4, value=sustain)
    if release_ticks > 0:
        builder.schedule(charge_ticks + sustain_ticks, release)
    return builder


def charge_sustain_release_ring(
    particle: EnumValue,
    charge_radius: float,
    sustain_radius: float,
    release_radius: float,
    charge_ticks: int = 20,
    sustain_ticks: int = 60,
    release_ticks: int = 20,
    points: int = 28,
) -> AnimationBuilder:
    charge = dsl_ring(particle, charge_radius, points=points)
    sustain = dsl_ring(particle, sustain_radius, points=points)
    release = dsl_ring(particle, release_radius, points=points)
    return charge_sustain_release(
        charge=charge,
        sustain=sustain,
        release=release,
        charge_ticks=charge_ticks,
        sustain_ticks=sustain_ticks,
        release_ticks=release_ticks,
    )


def spiral_rise(
    particle: EnumValue,
    radius: float = 2.0,
    height: float = 4.0,
    turns: int = 6,
    points: int = 120,
) -> Action:
    return helix(particle, radius=radius, length=height, turns=turns, points=points)


def spiral_fall(
    particle: EnumValue,
    radius: float = 2.0,
    height: float = 4.0,
    turns: int = 6,
    points: int = 120,
) -> Action:
    return helix(particle, radius=radius, length=height, turns=turns, points=points)


def pulse_envelope(
    particle: EnumValue,
    start_radius: float,
    end_radius: float,
    points: int = 28,
) -> Action:
    return Action(
        "particles.ring",
        {
            "particle": str(particle),
            "radius": start_radius,
            "points": points,
            "count": 1,
            "radiusTo": end_radius,
        },
    )


def beam_chargeup_release(
    particle: EnumValue,
    duration_ticks: int = 40,
    charge_ticks: int = 20,
) -> Action:
    return Action(
        "sequence",
        {
            "actions": [
                {
                    "type": "particles.beam_chargeup",
                    "particle": str(particle),
                    "durationTicks": charge_ticks,
                    "count": 1,
                },
                {
                    "type": "particles.line",
                    "particle": str(particle),
                    "length": 6.0,
                    "step": 0.25,
                    "count": 1,
                    "durationTicks": duration_ticks,
                },
            ]
        },
    )


def orbital_dual(
    particle_inner: EnumValue,
    particle_outer: EnumValue,
    radius_inner: float = 1.4,
    radius_outer: float = 2.6,
    copies_inner: int = 3,
    copies_outer: int = 5,
) -> Action:
    return stack(
        orbit(particle_inner, radius=radius_inner, copies=copies_inner),
        orbit(particle_outer, radius=radius_outer, copies=copies_outer),
    )


def time_sliced_bursts(
    particle: EnumValue,
    radius: float = 2.4,
    points: int = 24,
    slices: int = 3,
    gap_ticks: int = 4,
) -> Action:
    return stagger([ring(particle, radius, points) for _ in range(slices)], gap_ticks=gap_ticks)


def bezier_path(
    particle: EnumValue,
    points: List[List[float]],
    step: float = 0.2,
) -> Action:
    return Action(
        "particles.bezier",
        {
            "particle": str(particle),
            "points": points,
            "step": step,
            "count": 1,
        },
    )


def spline_path(
    particle: EnumValue,
    points: List[List[float]],
    step: float = 0.2,
) -> Action:
    return Action(
        "particles.spline",
        {
            "particle": str(particle),
            "points": points,
            "step": step,
            "count": 1,
        },
    )


def rotating_rings(
    particle: EnumValue,
    radius: float = 2.2,
    layers: int = 3,
    duration_ticks: int = 60,
    period_ticks: int = 2,
) -> Action:
    return stack(
        *[
            orbit(particle, radius=radius + 0.2 * idx, copies=3, duration_ticks=duration_ticks, period_ticks=period_ticks)
            for idx in range(layers)
        ]
    )


def impact_burst(
    particle: EnumValue,
    radius: float = 1.4,
    points: int = 20,
) -> Action:
    return shockwave(particle, radius=radius, points=points)


def hit_trail(
    particle: EnumValue,
    radius: float = 2.0,
    points: int = 24,
) -> Action:
    return ring(particle, radius=radius, points=points)


def status_halo(
    particle: EnumValue,
    radius: float = 1.6,
    points: int = 22,
) -> Action:
    return ring(particle, radius=radius, points=points)


def crit_flash(
    particle: EnumValue,
    radius: float = 1.2,
    points: int = 18,
) -> Action:
    return ring(particle, radius=radius, points=points)


def parry_sparks(
    particle: EnumValue,
    radius: float = 1.4,
    points: int = 20,
) -> Action:
    return arc_side(particle, radius=radius, angle_deg=120.0, points=points)


def shield_break(
    particle: EnumValue,
    radius: float = 1.8,
    points: int = 24,
) -> Action:
    return stack(shockwave(particle, radius=radius, points=points), arc_side(particle, radius=radius + 0.4, angle_deg=160))
