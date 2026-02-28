"""Reusable VFX clip registry built on top of fx.particles_* helpers."""

from __future__ import annotations

import math
from types import MappingProxyType
from typing import Any, Mapping

from dungeonsreborn_builder.v2.effects import (
    ActionSpec,
    ControlPointsSpec,
    GradientSpec,
    MeshSpec,
    PhysicsSpec,
    Point3Spec,
    PolylineSpec,
    TriangleSpec,
    VelocitySpec,
    fx,
)
from dungeonsreborn_builder.v2.enums import Particle, TargetAnchor
from .model import ClipSpec, VfxBudgetTier, VfxClipId, VfxClipLike


def _f(params: Mapping[str, Any], key: str, default: float) -> float:
    raw = params.get(key, default)
    return float(raw)


def _i(params: Mapping[str, Any], key: str, default: int) -> int:
    raw = params.get(key, default)
    return int(raw)


def _at(params: Mapping[str, Any]) -> TargetAnchor:
    raw = params.get("at")
    if raw is None:
        return TargetAnchor.ORIGIN
    return raw


def _target_at(params: Mapping[str, Any], *, default: TargetAnchor = TargetAnchor.CASTER) -> TargetAnchor:
    raw = params.get("target_at")
    if raw is None:
        return default
    return raw


def _emit_common(params: Mapping[str, Any]) -> dict[str, Any]:
    return {
        "count": _i(params, "count", 1),
        "offset": _f(params, "offset", 0.0),
        "extra": _f(params, "extra", 0.0),
        "at": _at(params),
        "forward": _f(params, "forward", 0.0),
        "right": _f(params, "right", 0.0),
        "up": _f(params, "up", 0.0),
        "particle_data": params.get("particle_data"),
    }


def _particle(params: Mapping[str, Any], default: Particle) -> Any:
    return params.get("particle", default)


def _default_polyline(length: float = 2.0) -> PolylineSpec:
    return PolylineSpec(
        points=[
            Point3Spec(x=0.0, y=0.0, z=0.0),
            Point3Spec(x=length, y=0.0, z=0.0),
        ]
    )


def _default_points() -> list[Point3Spec]:
    return [
        Point3Spec(x=0.0, y=0.0, z=0.0),
        Point3Spec(x=0.35, y=0.0, z=0.0),
        Point3Spec(x=-0.35, y=0.0, z=0.0),
        Point3Spec(x=0.0, y=0.35, z=0.0),
        Point3Spec(x=0.0, y=0.0, z=0.35),
    ]


def _default_mesh() -> MeshSpec:
    p0 = Point3Spec(x=0.0, y=0.2, z=0.0)
    p1 = Point3Spec(x=-0.4, y=-0.2, z=0.4)
    p2 = Point3Spec(x=0.4, y=-0.2, z=0.4)
    p3 = Point3Spec(x=0.4, y=-0.2, z=-0.4)
    p4 = Point3Spec(x=-0.4, y=-0.2, z=-0.4)
    return MeshSpec(
        triangles=[
            TriangleSpec(a=p0, b=p1, c=p2),
            TriangleSpec(a=p0, b=p2, c=p3),
            TriangleSpec(a=p0, b=p3, c=p4),
            TriangleSpec(a=p0, b=p4, c=p1),
        ]
    )


def _clip_ring_pulse(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_ring(
        _particle(params, Particle.SMOKE),
        radius=_f(params, "radius", 1.2),
        points=_i(params, "points", 28),
        **_emit_common(params),
    )


def _clip_ring_dual(params: Mapping[str, Any]) -> ActionSpec:
    common = _emit_common(params)
    inner = fx.particles_ring(
        _particle(params, Particle.SMOKE),
        radius=_f(params, "radius_inner", 0.95),
        points=_i(params, "points_inner", 22),
        **common,
    )
    outer = fx.particles_ring(
        _particle(params, Particle.SMOKE),
        radius=_f(params, "radius_outer", 1.45),
        points=_i(params, "points_outer", 30),
        **common,
    )
    return fx.sequence(inner, outer)


def _clip_point_flash(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_point(
        _particle(params, Particle.END_ROD),
        **_emit_common(params),
    )


def _clip_line_beam(params: Mapping[str, Any]) -> ActionSpec:
    common = _emit_common(params)
    return fx.particles_line(
        _particle(params, Particle.CRIT),
        length=_f(params, "length", 3.0),
        step=_f(params, "step", 0.35),
        target_at=_target_at(params),
        **common,
    )


def _clip_arc_slash(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_arc(
        _particle(params, Particle.SWEEP_ATTACK),
        radius=_f(params, "radius", 1.6),
        angle_degrees=_f(params, "angle_degrees", 95.0),
        points=_i(params, "points", 24),
        **_emit_common(params),
    )


def _clip_disk_ground(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_disk(
        _particle(params, Particle.CLOUD),
        radius=_f(params, "radius", 1.35),
        rings=_i(params, "rings", 4),
        points_per_ring=_i(params, "points_per_ring", 14),
        **_emit_common(params),
    )


def _clip_shell_pop(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_sphere_shell(
        _particle(params, Particle.SMOKE),
        radius=_f(params, "radius", 1.4),
        points=_i(params, "points", 34),
        **_emit_common(params),
    )


def _clip_fill_burst(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_sphere_filled(
        _particle(params, Particle.CLOUD),
        radius=_f(params, "radius", 1.15),
        points=_i(params, "points", 44),
        **_emit_common(params),
    )


def _clip_helix_channel(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_helix(
        _particle(params, Particle.ENCHANT),
        radius=_f(params, "radius", 0.7),
        turns=_f(params, "turns", 2.5),
        length=_f(params, "length", 2.0),
        points=_i(params, "points", 34),
        **_emit_common(params),
    )


def _clip_polyline_tether(params: Mapping[str, Any]) -> ActionSpec:
    polyline = params.get("polyline")
    if not isinstance(polyline, PolylineSpec):
        polyline = _default_polyline(length=_f(params, "length", 2.6))
    gradient = params.get("gradient")
    if gradient is None:
        gradient = GradientSpec(start_color="#B3E5FF", end_color="#4F89FF", size=1.0)
    return fx.particles_polyline(
        _particle(params, Particle.DUST),
        polyline=polyline,
        step=_f(params, "step", 0.4),
        gradient=gradient,
        **_emit_common(params),
    )


def _clip_mesh_ward(params: Mapping[str, Any]) -> ActionSpec:
    mesh = params.get("mesh")
    if not isinstance(mesh, MeshSpec):
        mesh = _default_mesh()
    gradient = params.get("gradient")
    if gradient is None:
        gradient = GradientSpec(start_color="#3EE6A3", end_color="#0E6D7B", size=1.0)
    return fx.particles_mesh(
        _particle(params, Particle.DUST),
        mesh=mesh,
        step=_f(params, "step", 0.75),
        gradient=gradient,
        **_emit_common(params),
    )


def _clip_cone_warning(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_cone(
        _particle(params, Particle.FLAME),
        length=_f(params, "length", 3.4),
        angle_degrees=_f(params, "angle_degrees", 70.0),
        rings=_i(params, "rings", 4),
        points_per_ring=_i(params, "points_per_ring", 12),
        **_emit_common(params),
    )


def _clip_cylinder_pillar(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_cylinder(
        _particle(params, Particle.END_ROD),
        radius=_f(params, "radius", 1.05),
        height=_f(params, "height", 2.8),
        rings=_i(params, "rings", 5),
        points_per_ring=_i(params, "points_per_ring", 10),
        **_emit_common(params),
    )


def _clip_box_field(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_box(
        _particle(params, Particle.CLOUD),
        x_radius=_f(params, "x_radius", 1.3),
        y_radius=_f(params, "y_radius", 0.7),
        z_radius=_f(params, "z_radius", 1.3),
        step=_f(params, "step", 0.45),
        **_emit_common(params),
    )


def _clip_polygon_sigil(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_polygon(
        _particle(params, Particle.ENCHANT),
        radius=_f(params, "radius", 1.4),
        sides=_i(params, "sides", 6),
        points_per_edge=_i(params, "points_per_edge", 10),
        **_emit_common(params),
    )


def _clip_points_glyph(params: Mapping[str, Any]) -> ActionSpec:
    points = params.get("points")
    if not isinstance(points, list) or not points:
        points = _default_points()
    return fx.particles_points(
        _particle(params, Particle.END_ROD),
        points=points,
        size=params.get("size"),
        **_emit_common(params),
    )


def _clip_bezier_trail(params: Mapping[str, Any]) -> ActionSpec:
    p0 = params.get("p0", Point3Spec(0.0, 0.0, 0.0))
    p1 = params.get("p1", Point3Spec(0.8, 0.35, 0.0))
    p2 = params.get("p2", Point3Spec(1.8, 0.35, 0.0))
    p3 = params.get("p3", Point3Spec(2.7, 0.0, 0.0))
    return fx.particles_bezier(
        _particle(params, Particle.DUST),
        p0=p0,
        p1=p1,
        p2=p2,
        p3=p3,
        points_per_meter=_f(params, "points_per_meter", 10.0),
        max_points=_i(params, "max_points", 300),
        **_emit_common(params),
    )


def _clip_spline_rail(params: Mapping[str, Any]) -> ActionSpec:
    controls = params.get("control_points")
    if not isinstance(controls, ControlPointsSpec):
        controls = ControlPointsSpec(
            points=[
                Point3Spec(0.0, 0.0, 0.0),
                Point3Spec(0.9, 0.25, 0.0),
                Point3Spec(1.8, -0.1, 0.0),
                Point3Spec(2.7, 0.0, 0.0),
            ]
        )
    return fx.particles_spline(
        _particle(params, Particle.DUST),
        control_points=controls,
        points_per_meter=_f(params, "points_per_meter", 9.0),
        max_points=_i(params, "max_points", 280),
        **_emit_common(params),
    )


def _clip_physics_plume(params: Mapping[str, Any]) -> ActionSpec:
    velocity = params.get("velocity")
    if not isinstance(velocity, VelocitySpec):
        velocity = VelocitySpec(x=0.0, y=0.22, z=0.0)
    physics = params.get("physics")
    if not isinstance(physics, PhysicsSpec):
        physics = PhysicsSpec(spread=0.11, gravity=0.03, drag=0.02, steps=16, period_ticks=1, collide=False)
    return fx.particles_physics(
        _particle(params, Particle.CLOUD),
        velocity=velocity,
        physics=physics,
        **_emit_common(params),
    )


def _clip_physics_shards(params: Mapping[str, Any]) -> ActionSpec:
    velocity = params.get("velocity")
    if not isinstance(velocity, VelocitySpec):
        velocity = VelocitySpec(x=0.0, y=0.26, z=0.0)
    physics = params.get("physics")
    if not isinstance(physics, PhysicsSpec):
        physics = PhysicsSpec(spread=0.1, gravity=0.04, drag=0.015, steps=18, period_ticks=1, collide=True)
    return fx.particles_physics_points(
        _particle(params, Particle.CRIT),
        points=params.get("points", _default_points()),
        velocity=velocity,
        physics=physics,
        **_emit_common(params),
    )


def _clip_physics_crack(params: Mapping[str, Any]) -> ActionSpec:
    velocity = params.get("velocity")
    if not isinstance(velocity, VelocitySpec):
        velocity = VelocitySpec(x=0.0, y=0.16, z=0.0)
    physics = params.get("physics")
    if not isinstance(physics, PhysicsSpec):
        physics = PhysicsSpec(spread=0.08, gravity=0.03, drag=0.02, steps=14, period_ticks=1, collide=True)
    polyline = params.get("polyline")
    if not isinstance(polyline, PolylineSpec):
        polyline = _default_polyline(length=_f(params, "length", 2.2))
    return fx.particles_physics_polyline(
        _particle(params, Particle.SMOKE),
        polyline=polyline,
        step=_f(params, "step", 0.35),
        velocity=velocity,
        physics=physics,
        **_emit_common(params),
    )


def _clip_physics_fracture(params: Mapping[str, Any]) -> ActionSpec:
    velocity = params.get("velocity")
    if not isinstance(velocity, VelocitySpec):
        velocity = VelocitySpec(x=0.0, y=0.18, z=0.0)
    physics = params.get("physics")
    if not isinstance(physics, PhysicsSpec):
        physics = PhysicsSpec(spread=0.09, gravity=0.035, drag=0.02, steps=16, period_ticks=1, collide=True)
    mesh = params.get("mesh")
    if not isinstance(mesh, MeshSpec):
        mesh = _default_mesh()
    return fx.particles_physics_mesh(
        _particle(params, Particle.DUST),
        mesh=mesh,
        step=_f(params, "step", 0.7),
        velocity=velocity,
        physics=physics,
        **_emit_common(params),
    )


def _clip_zone_outline(params: Mapping[str, Any]) -> ActionSpec:
    radius = _f(params, "radius", 1.8)
    polyline = params.get("polyline")
    if not isinstance(polyline, PolylineSpec):
        polyline = PolylineSpec(
            points=[
                Point3Spec(x=-radius, y=0.0, z=-radius),
                Point3Spec(x=radius, y=0.0, z=-radius),
                Point3Spec(x=radius, y=0.0, z=radius),
                Point3Spec(x=-radius, y=0.0, z=radius),
                Point3Spec(x=-radius, y=0.0, z=-radius),
            ]
        )
    return fx.particles_polyline(
        _particle(params, Particle.SMOKE),
        polyline=polyline,
        step=_f(params, "step", 0.5),
        gradient=params.get("gradient"),
        **_emit_common(params),
    )


def _clip_impact_core(params: Mapping[str, Any]) -> ActionSpec:
    core = _clip_point_flash({**params, "count": _i(params, "count", 2), "particle": params.get("core_particle", Particle.END_ROD)})
    shell = _clip_shell_pop({**params, "radius": _f(params, "radius", 1.05), "points": _i(params, "points", 24), "particle": params.get("shell_particle", Particle.SMOKE)})
    return fx.sequence(core, shell)


def _polyline(points: list[Point3Spec]) -> PolylineSpec:
    return PolylineSpec(points=points)


def _curve_points(
    fn,
    *,
    count: int,
    radius: float,
    y_scale: float = 0.0,
) -> list[Point3Spec]:
    out: list[Point3Spec] = []
    for i in range(max(3, count)):
        t = (i / max(1, count - 1)) * math.tau
        x, z = fn(t)
        out.append(Point3Spec(x=float(x) * radius, y=math.sin(t) * y_scale, z=float(z) * radius))
    return out


def _clip_curve_lissajous(params: Mapping[str, Any]) -> ActionSpec:
    a = _f(params, "a", 3.0)
    b = _f(params, "b", 2.0)
    d = _f(params, "delta", math.pi / 2.0)
    points = _curve_points(
        lambda t: (math.sin(a * t + d), math.sin(b * t)),
        count=_i(params, "points", 84),
        radius=_f(params, "radius", 1.4),
        y_scale=_f(params, "y_scale", 0.15),
    )
    return fx.particles_polyline(_particle(params, Particle.DUST), polyline=_polyline(points), step=_f(params, "step", 0.3), **_emit_common(params))


def _clip_curve_rose(params: Mapping[str, Any]) -> ActionSpec:
    k = _f(params, "k", 5.0)
    points = _curve_points(
        lambda t: (math.cos(k * t) * math.cos(t), math.cos(k * t) * math.sin(t)),
        count=_i(params, "points", 92),
        radius=_f(params, "radius", 1.35),
    )
    return fx.particles_points(_particle(params, Particle.END_ROD), points=points, **_emit_common(params))


def _clip_curve_spirograph(params: Mapping[str, Any]) -> ActionSpec:
    r1 = _f(params, "r1", 1.0)
    r2 = _f(params, "r2", 0.35)
    d = _f(params, "d", 0.6)
    points = _curve_points(
        lambda t: (
            (r1 - r2) * math.cos(t) + d * math.cos(((r1 - r2) / max(0.0001, r2)) * t),
            (r1 - r2) * math.sin(t) - d * math.sin(((r1 - r2) / max(0.0001, r2)) * t),
        ),
        count=_i(params, "points", 120),
        radius=_f(params, "radius", 0.9),
    )
    return fx.particles_polyline(_particle(params, Particle.DUST), polyline=_polyline(points), step=_f(params, "step", 0.22), gradient=params.get("gradient"), **_emit_common(params))


def _clip_curve_epitrochoid(params: Mapping[str, Any]) -> ActionSpec:
    r = _f(params, "r", 0.5)
    R = _f(params, "R", 1.0)
    d = _f(params, "d", 0.7)
    points = _curve_points(
        lambda t: (
            (R + r) * math.cos(t) - d * math.cos(((R + r) / max(0.0001, r)) * t),
            (R + r) * math.sin(t) - d * math.sin(((R + r) / max(0.0001, r)) * t),
        ),
        count=_i(params, "points", 100),
        radius=_f(params, "radius", 0.8),
    )
    return fx.particles_polyline(_particle(params, Particle.ENCHANT), polyline=_polyline(points), step=_f(params, "step", 0.25), **_emit_common(params))


def _clip_curve_hypotrochoid(params: Mapping[str, Any]) -> ActionSpec:
    r = _f(params, "r", 0.4)
    R = _f(params, "R", 1.0)
    d = _f(params, "d", 0.5)
    points = _curve_points(
        lambda t: (
            (R - r) * math.cos(t) + d * math.cos(((R - r) / max(0.0001, r)) * t),
            (R - r) * math.sin(t) - d * math.sin(((R - r) / max(0.0001, r)) * t),
        ),
        count=_i(params, "points", 100),
        radius=_f(params, "radius", 0.9),
    )
    return fx.particles_polyline(_particle(params, Particle.DUST), polyline=_polyline(points), step=_f(params, "step", 0.23), **_emit_common(params))


def _clip_curve_lemniscate(params: Mapping[str, Any]) -> ActionSpec:
    a = _f(params, "a", 1.0)
    points: list[Point3Spec] = []
    count = _i(params, "points", 88)
    for i in range(max(8, count)):
        t = ((i / max(1, count - 1)) * 2.0 - 1.0) * (math.pi / 2.0)
        denom = 1.0 + math.sin(t) ** 2
        x = (a * math.cos(t)) / denom
        z = (a * math.sin(t) * math.cos(t)) / denom
        points.append(Point3Spec(x=x, y=0.0, z=z))
    return fx.particles_polyline(_particle(params, Particle.END_ROD), polyline=_polyline(points), step=_f(params, "step", 0.2), **_emit_common(params))


def _clip_curve_torus_knot(params: Mapping[str, Any]) -> ActionSpec:
    p = _f(params, "p", 2.0)
    q = _f(params, "q", 3.0)
    r = _f(params, "radius", 1.1)
    tube = _f(params, "tube", 0.32)
    points: list[Point3Spec] = []
    count = _i(params, "points", 110)
    for i in range(max(16, count)):
        t = (i / max(1, count - 1)) * math.tau
        x = (r + tube * math.cos(q * t)) * math.cos(p * t)
        z = (r + tube * math.cos(q * t)) * math.sin(p * t)
        y = tube * math.sin(q * t)
        points.append(Point3Spec(x=x, y=y, z=z))
    return fx.particles_points(_particle(params, Particle.DUST), points=points, **_emit_common(params))


def _clip_curve_log_spiral(params: Mapping[str, Any]) -> ActionSpec:
    a = _f(params, "a", 0.2)
    b = _f(params, "b", 0.2)
    points: list[Point3Spec] = []
    count = _i(params, "points", 90)
    for i in range(max(10, count)):
        t = (i / max(1, count - 1)) * 5.0 * math.pi
        r = a * math.exp(b * t)
        points.append(Point3Spec(x=r * math.cos(t), y=0.0, z=r * math.sin(t)))
    return fx.particles_polyline(_particle(params, Particle.SMOKE), polyline=_polyline(points), step=_f(params, "step", 0.2), **_emit_common(params))


def _clip_volume_torus_shell(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        fx.particles_ring(_particle(params, Particle.DUST), radius=_f(params, "major_radius", 1.25), points=_i(params, "points", 44), **_emit_common(params)),
        fx.particles_helix(_particle(params, Particle.DUST), radius=_f(params, "minor_radius", 0.45), turns=_f(params, "turns", 2.0), length=_f(params, "length", 0.6), points=_i(params, "helix_points", 40), **_emit_common(params)),
    )


def _clip_volume_torus_filled(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        _clip_volume_torus_shell(params),
        fx.particles_sphere_filled(_particle(params, Particle.CLOUD), radius=_f(params, "radius", 0.75), points=_i(params, "fill_points", 50), **_emit_common(params)),
    )


def _clip_volume_capsule_shell(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        fx.particles_cylinder(_particle(params, Particle.END_ROD), radius=_f(params, "radius", 0.55), height=_f(params, "height", 1.9), rings=_i(params, "rings", 5), points_per_ring=_i(params, "points_per_ring", 10), **_emit_common(params)),
        fx.particles_sphere_shell(_particle(params, Particle.END_ROD), radius=_f(params, "cap_radius", 0.55), points=_i(params, "cap_points", 20), up=_f(params, "up", 0.95), **_emit_common(params)),
    )


def _clip_volume_capsule_filled(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        _clip_volume_capsule_shell(params),
        fx.particles_sphere_filled(_particle(params, Particle.CLOUD), radius=_f(params, "radius", 0.6), points=_i(params, "points", 46), **_emit_common(params)),
    )


def _clip_volume_superellipsoid(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_box(
        _particle(params, Particle.DUST),
        x_radius=_f(params, "x_radius", 1.0),
        y_radius=_f(params, "y_radius", 0.8),
        z_radius=_f(params, "z_radius", 1.0),
        step=_f(params, "step", 0.25),
        **_emit_common(params),
    )


def _clip_volume_octahedron_wire(params: Mapping[str, Any]) -> ActionSpec:
    p0 = Point3Spec(0.0, 1.0, 0.0)
    p1 = Point3Spec(1.0, 0.0, 0.0)
    p2 = Point3Spec(0.0, 0.0, 1.0)
    p3 = Point3Spec(-1.0, 0.0, 0.0)
    p4 = Point3Spec(0.0, 0.0, -1.0)
    p5 = Point3Spec(0.0, -1.0, 0.0)
    mesh = MeshSpec(
        triangles=[
            TriangleSpec(p0, p1, p2),
            TriangleSpec(p0, p2, p3),
            TriangleSpec(p0, p3, p4),
            TriangleSpec(p0, p4, p1),
            TriangleSpec(p5, p2, p1),
            TriangleSpec(p5, p3, p2),
            TriangleSpec(p5, p4, p3),
            TriangleSpec(p5, p1, p4),
        ]
    )
    return fx.particles_mesh(_particle(params, Particle.DUST), mesh=mesh, step=_f(params, "step", 0.55), gradient=params.get("gradient"), **_emit_common(params))


def _clip_volume_icosahedron_wire(params: Mapping[str, Any]) -> ActionSpec:
    phi = (1.0 + math.sqrt(5.0)) / 2.0
    points = [
        Point3Spec(-1, phi, 0), Point3Spec(1, phi, 0), Point3Spec(-1, -phi, 0), Point3Spec(1, -phi, 0),
        Point3Spec(0, -1, phi), Point3Spec(0, 1, phi), Point3Spec(0, -1, -phi), Point3Spec(0, 1, -phi),
        Point3Spec(phi, 0, -1), Point3Spec(phi, 0, 1), Point3Spec(-phi, 0, -1), Point3Spec(-phi, 0, 1),
    ]
    return fx.particles_points(_particle(params, Particle.END_ROD), points=points, **_emit_common(params))


def _clip_volume_prism_hex(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        fx.particles_polygon(_particle(params, Particle.ENCHANT), radius=_f(params, "radius", 1.2), sides=6, points_per_edge=_i(params, "points_per_edge", 10), **_emit_common(params)),
        fx.particles_cylinder(_particle(params, Particle.ENCHANT), radius=_f(params, "radius", 1.2), height=_f(params, "height", 1.6), rings=2, points_per_ring=_i(params, "points_per_ring", 8), **_emit_common(params)),
    )


def _clip_field_vortex(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_helix(_particle(params, Particle.SMOKE), radius=_f(params, "radius", 0.9), turns=_f(params, "turns", 4.2), length=_f(params, "length", 2.4), points=_i(params, "points", 72), **_emit_common(params))


def _clip_field_shockfront(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        fx.particles_ring(_particle(params, Particle.CLOUD), radius=_f(params, "radius", 1.0), points=_i(params, "points", 28), **_emit_common(params)),
        fx.particles_ring(_particle(params, Particle.CLOUD), radius=_f(params, "radius2", 1.8), points=_i(params, "points2", 36), **_emit_common(params)),
    )


def _clip_field_orbit_swarm(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        fx.particles_ring(_particle(params, Particle.END_ROD), radius=_f(params, "radius", 1.0), points=_i(params, "points", 18), **_emit_common(params)),
        fx.particles_ring(_particle(params, Particle.END_ROD), radius=_f(params, "radius_outer", 1.6), points=_i(params, "points_outer", 26), **_emit_common(params)),
    )


def _clip_field_ribbon_trail(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_spline(
        _particle(params, Particle.DUST),
        control_points=ControlPointsSpec(
            points=[
                Point3Spec(0.0, 0.0, 0.0),
                Point3Spec(0.5, 0.3, 0.2),
                Point3Spec(1.0, -0.2, 0.4),
                Point3Spec(1.5, 0.1, 0.7),
            ]
        ),
        points_per_meter=_f(params, "points_per_meter", 12.0),
        max_points=_i(params, "max_points", 320),
        **_emit_common(params),
    )


def _clip_field_rain_column(params: Mapping[str, Any]) -> ActionSpec:
    return fx.particles_cylinder(_particle(params, Particle.DRIPPING_WATER), radius=_f(params, "radius", 1.0), height=_f(params, "height", 2.8), rings=_i(params, "rings", 6), points_per_ring=_i(params, "points_per_ring", 10), **_emit_common(params))


def _clip_field_ground_cracks(params: Mapping[str, Any]) -> ActionSpec:
    return _clip_physics_crack({**params, "length": _f(params, "length", 3.4), "step": _f(params, "step", 0.26)})


def _clip_field_charge_core(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_point_flash(params), _clip_shell_pop({**params, "radius": _f(params, "radius", 1.2)}))


def _clip_field_phase_gate(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(
        fx.particles_box(_particle(params, Particle.ENCHANT), x_radius=_f(params, "x_radius", 1.2), y_radius=_f(params, "y_radius", 1.0), z_radius=_f(params, "z_radius", 0.3), step=_f(params, "step", 0.35), **_emit_common(params)),
        fx.particles_polygon(_particle(params, Particle.ENCHANT), radius=_f(params, "radius", 1.2), sides=8, points_per_edge=_i(params, "points_per_edge", 8), **_emit_common(params)),
    )


def _clip_react_ricochet_sparks(params: Mapping[str, Any]) -> ActionSpec:
    return _clip_physics_shards({**params, "particle": params.get("particle", Particle.CRIT), "count": _i(params, "count", 2)})


def _clip_react_chain_arc(params: Mapping[str, Any]) -> ActionSpec:
    return _clip_polyline_tether({**params, "particle": params.get("particle", Particle.DUST), "length": _f(params, "length", 2.2)})


def _clip_react_fracture_bloom(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_physics_fracture(params), _clip_shell_pop({**params, "radius": _f(params, "radius", 1.5)}))


def _clip_react_void_sink(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_field_vortex({**params, "particle": params.get("particle", Particle.PORTAL)}), _clip_fill_burst({**params, "particle": params.get("particle2", Particle.SMOKE), "radius": _f(params, "radius", 1.0)}))


def _clip_react_holy_bloom(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_shell_pop({**params, "particle": params.get("particle", Particle.TOTEM_OF_UNDYING)}), _clip_ring_dual({**params, "particle": params.get("particle2", Particle.END_ROD)}))


def _clip_react_poison_haze(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_fill_burst({**params, "particle": params.get("particle", Particle.SPORE_BLOSSOM_AIR)}), _clip_physics_plume({**params, "particle": params.get("particle2", Particle.CLOUD)}))


def _clip_react_bleed_fan(params: Mapping[str, Any]) -> ActionSpec:
    return _clip_arc_slash({**params, "particle": params.get("particle", Particle.CRIT), "angle_degrees": _f(params, "angle_degrees", 120.0), "radius": _f(params, "radius", 1.6)})


def _clip_react_frost_shatter(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_physics_shards({**params, "particle": params.get("particle", Particle.SNOWFLAKE)}), _clip_point_flash({**params, "particle": params.get("particle2", Particle.END_ROD)}))


def _clip_react_lightning_cage(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_cylinder_pillar({**params, "particle": params.get("particle", Particle.ELECTRIC_SPARK)}), _clip_polygon_sigil({**params, "particle": params.get("particle2", Particle.ELECTRIC_SPARK), "sides": 8}))


def _clip_react_wither_wisp(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_field_vortex({**params, "particle": params.get("particle", Particle.SMOKE)}), _clip_point_flash({**params, "particle": params.get("particle2", Particle.SOUL)}))


def _clip_react_heal_burst(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_shell_pop({**params, "particle": params.get("particle", Particle.HEART)}), _clip_point_flash({**params, "particle": params.get("particle2", Particle.END_ROD)}))


def _clip_react_shield_snap(params: Mapping[str, Any]) -> ActionSpec:
    return fx.sequence(_clip_ring_dual({**params, "particle": params.get("particle", Particle.ENCHANT)}), _clip_mesh_ward({**params, "particle": params.get("particle2", Particle.DUST)}))


_CLIPS: dict[str, ClipSpec] = {
    VfxClipId.RING_PULSE.value: ClipSpec(VfxClipId.RING_PULSE, "Pulse circolare", VfxBudgetTier.LOW, _clip_ring_pulse),
    VfxClipId.RING_DUAL.value: ClipSpec(VfxClipId.RING_DUAL, "Doppio ring", VfxBudgetTier.LOW, _clip_ring_dual),
    VfxClipId.POINT_FLASH.value: ClipSpec(VfxClipId.POINT_FLASH, "Flash puntuale", VfxBudgetTier.LOW, _clip_point_flash),
    VfxClipId.LINE_BEAM.value: ClipSpec(VfxClipId.LINE_BEAM, "Beam lineare", VfxBudgetTier.LOW, _clip_line_beam),
    VfxClipId.ARC_SLASH.value: ClipSpec(VfxClipId.ARC_SLASH, "Slash ad arco", VfxBudgetTier.LOW, _clip_arc_slash),
    VfxClipId.DISK_GROUND.value: ClipSpec(VfxClipId.DISK_GROUND, "Disco a terra", VfxBudgetTier.MEDIUM, _clip_disk_ground),
    VfxClipId.SHELL_POP.value: ClipSpec(VfxClipId.SHELL_POP, "Shell volumetrica", VfxBudgetTier.MEDIUM, _clip_shell_pop),
    VfxClipId.FILL_BURST.value: ClipSpec(VfxClipId.FILL_BURST, "Burst pieno", VfxBudgetTier.HIGH, _clip_fill_burst),
    VfxClipId.HELIX_CHANNEL.value: ClipSpec(VfxClipId.HELIX_CHANNEL, "Canale a elica", VfxBudgetTier.MEDIUM, _clip_helix_channel),
    VfxClipId.POLYLINE_TETHER.value: ClipSpec(VfxClipId.POLYLINE_TETHER, "Tether polyline", VfxBudgetTier.MEDIUM, _clip_polyline_tether),
    VfxClipId.MESH_WARD.value: ClipSpec(VfxClipId.MESH_WARD, "Ward mesh", VfxBudgetTier.HIGH, _clip_mesh_ward),
    VfxClipId.CONE_WARNING.value: ClipSpec(VfxClipId.CONE_WARNING, "Warning conico", VfxBudgetTier.MEDIUM, _clip_cone_warning),
    VfxClipId.CYLINDER_PILLAR.value: ClipSpec(VfxClipId.CYLINDER_PILLAR, "Pilastro cilindrico", VfxBudgetTier.MEDIUM, _clip_cylinder_pillar),
    VfxClipId.BOX_FIELD.value: ClipSpec(VfxClipId.BOX_FIELD, "Campo box", VfxBudgetTier.MEDIUM, _clip_box_field),
    VfxClipId.POLYGON_SIGIL.value: ClipSpec(VfxClipId.POLYGON_SIGIL, "Sigillo poligonale", VfxBudgetTier.LOW, _clip_polygon_sigil),
    VfxClipId.POINTS_GLYPH.value: ClipSpec(VfxClipId.POINTS_GLYPH, "Glyph a punti", VfxBudgetTier.MEDIUM, _clip_points_glyph),
    VfxClipId.BEZIER_TRAIL.value: ClipSpec(VfxClipId.BEZIER_TRAIL, "Trail bezier", VfxBudgetTier.MEDIUM, _clip_bezier_trail),
    VfxClipId.SPLINE_RAIL.value: ClipSpec(VfxClipId.SPLINE_RAIL, "Rail spline", VfxBudgetTier.MEDIUM, _clip_spline_rail),
    VfxClipId.PHYSICS_PLUME.value: ClipSpec(VfxClipId.PHYSICS_PLUME, "Plume fisico", VfxBudgetTier.HIGH, _clip_physics_plume),
    VfxClipId.PHYSICS_SHARDS.value: ClipSpec(VfxClipId.PHYSICS_SHARDS, "Shards fisici", VfxBudgetTier.HIGH, _clip_physics_shards),
    VfxClipId.PHYSICS_CRACK.value: ClipSpec(VfxClipId.PHYSICS_CRACK, "Crack fisico", VfxBudgetTier.HIGH, _clip_physics_crack),
    VfxClipId.PHYSICS_FRACTURE.value: ClipSpec(VfxClipId.PHYSICS_FRACTURE, "Fracture fisica", VfxBudgetTier.HIGH, _clip_physics_fracture),
    VfxClipId.ZONE_OUTLINE.value: ClipSpec(VfxClipId.ZONE_OUTLINE, "Outline di zona", VfxBudgetTier.MEDIUM, _clip_zone_outline),
    VfxClipId.IMPACT_CORE.value: ClipSpec(VfxClipId.IMPACT_CORE, "Nucleo impatto", VfxBudgetTier.MEDIUM, _clip_impact_core),
    VfxClipId.CURVE_LISSAJOUS.value: ClipSpec(VfxClipId.CURVE_LISSAJOUS, "Curva lissajous", VfxBudgetTier.MEDIUM, _clip_curve_lissajous),
    VfxClipId.CURVE_ROSE.value: ClipSpec(VfxClipId.CURVE_ROSE, "Curva rose", VfxBudgetTier.MEDIUM, _clip_curve_rose),
    VfxClipId.CURVE_SPIROGRAPH.value: ClipSpec(VfxClipId.CURVE_SPIROGRAPH, "Curva spirograph", VfxBudgetTier.MEDIUM, _clip_curve_spirograph),
    VfxClipId.CURVE_EPITROCHOID.value: ClipSpec(VfxClipId.CURVE_EPITROCHOID, "Curva epitrochoid", VfxBudgetTier.HIGH, _clip_curve_epitrochoid),
    VfxClipId.CURVE_HYPOTROCHOID.value: ClipSpec(VfxClipId.CURVE_HYPOTROCHOID, "Curva hypotrochoid", VfxBudgetTier.HIGH, _clip_curve_hypotrochoid),
    VfxClipId.CURVE_LEMNISCATE.value: ClipSpec(VfxClipId.CURVE_LEMNISCATE, "Curva lemniscate", VfxBudgetTier.MEDIUM, _clip_curve_lemniscate),
    VfxClipId.CURVE_TORUS_KNOT.value: ClipSpec(VfxClipId.CURVE_TORUS_KNOT, "Curva torus knot", VfxBudgetTier.HIGH, _clip_curve_torus_knot),
    VfxClipId.CURVE_LOG_SPIRAL.value: ClipSpec(VfxClipId.CURVE_LOG_SPIRAL, "Curva log spiral", VfxBudgetTier.MEDIUM, _clip_curve_log_spiral),
    VfxClipId.VOLUME_TORUS_SHELL.value: ClipSpec(VfxClipId.VOLUME_TORUS_SHELL, "Volume torus shell", VfxBudgetTier.HIGH, _clip_volume_torus_shell),
    VfxClipId.VOLUME_TORUS_FILLED.value: ClipSpec(VfxClipId.VOLUME_TORUS_FILLED, "Volume torus filled", VfxBudgetTier.HIGH, _clip_volume_torus_filled),
    VfxClipId.VOLUME_CAPSULE_SHELL.value: ClipSpec(VfxClipId.VOLUME_CAPSULE_SHELL, "Volume capsule shell", VfxBudgetTier.HIGH, _clip_volume_capsule_shell),
    VfxClipId.VOLUME_CAPSULE_FILLED.value: ClipSpec(VfxClipId.VOLUME_CAPSULE_FILLED, "Volume capsule filled", VfxBudgetTier.HIGH, _clip_volume_capsule_filled),
    VfxClipId.VOLUME_SUPERELLIPSOID.value: ClipSpec(VfxClipId.VOLUME_SUPERELLIPSOID, "Volume superellipsoid", VfxBudgetTier.HIGH, _clip_volume_superellipsoid),
    VfxClipId.VOLUME_OCTAHEDRON_WIRE.value: ClipSpec(VfxClipId.VOLUME_OCTAHEDRON_WIRE, "Volume octahedron wire", VfxBudgetTier.MEDIUM, _clip_volume_octahedron_wire),
    VfxClipId.VOLUME_ICOSAHEDRON_WIRE.value: ClipSpec(VfxClipId.VOLUME_ICOSAHEDRON_WIRE, "Volume icosahedron wire", VfxBudgetTier.MEDIUM, _clip_volume_icosahedron_wire),
    VfxClipId.VOLUME_PRISM_HEX.value: ClipSpec(VfxClipId.VOLUME_PRISM_HEX, "Volume prism hex", VfxBudgetTier.MEDIUM, _clip_volume_prism_hex),
    VfxClipId.FIELD_VORTEX.value: ClipSpec(VfxClipId.FIELD_VORTEX, "Field vortex", VfxBudgetTier.MEDIUM, _clip_field_vortex),
    VfxClipId.FIELD_SHOCKFRONT.value: ClipSpec(VfxClipId.FIELD_SHOCKFRONT, "Field shockfront", VfxBudgetTier.MEDIUM, _clip_field_shockfront),
    VfxClipId.FIELD_ORBIT_SWARM.value: ClipSpec(VfxClipId.FIELD_ORBIT_SWARM, "Field orbit swarm", VfxBudgetTier.MEDIUM, _clip_field_orbit_swarm),
    VfxClipId.FIELD_RIBBON_TRAIL.value: ClipSpec(VfxClipId.FIELD_RIBBON_TRAIL, "Field ribbon trail", VfxBudgetTier.MEDIUM, _clip_field_ribbon_trail),
    VfxClipId.FIELD_RAIN_COLUMN.value: ClipSpec(VfxClipId.FIELD_RAIN_COLUMN, "Field rain column", VfxBudgetTier.LOW, _clip_field_rain_column),
    VfxClipId.FIELD_GROUND_CRACKS.value: ClipSpec(VfxClipId.FIELD_GROUND_CRACKS, "Field ground cracks", VfxBudgetTier.HIGH, _clip_field_ground_cracks),
    VfxClipId.FIELD_CHARGE_CORE.value: ClipSpec(VfxClipId.FIELD_CHARGE_CORE, "Field charge core", VfxBudgetTier.MEDIUM, _clip_field_charge_core),
    VfxClipId.FIELD_PHASE_GATE.value: ClipSpec(VfxClipId.FIELD_PHASE_GATE, "Field phase gate", VfxBudgetTier.MEDIUM, _clip_field_phase_gate),
    VfxClipId.REACT_RICOCHET_SPARKS.value: ClipSpec(VfxClipId.REACT_RICOCHET_SPARKS, "React ricochet sparks", VfxBudgetTier.MEDIUM, _clip_react_ricochet_sparks),
    VfxClipId.REACT_CHAIN_ARC.value: ClipSpec(VfxClipId.REACT_CHAIN_ARC, "React chain arc", VfxBudgetTier.MEDIUM, _clip_react_chain_arc),
    VfxClipId.REACT_FRACTURE_BLOOM.value: ClipSpec(VfxClipId.REACT_FRACTURE_BLOOM, "React fracture bloom", VfxBudgetTier.HIGH, _clip_react_fracture_bloom),
    VfxClipId.REACT_VOID_SINK.value: ClipSpec(VfxClipId.REACT_VOID_SINK, "React void sink", VfxBudgetTier.HIGH, _clip_react_void_sink),
    VfxClipId.REACT_HOLY_BLOOM.value: ClipSpec(VfxClipId.REACT_HOLY_BLOOM, "React holy bloom", VfxBudgetTier.MEDIUM, _clip_react_holy_bloom),
    VfxClipId.REACT_POISON_HAZE.value: ClipSpec(VfxClipId.REACT_POISON_HAZE, "React poison haze", VfxBudgetTier.MEDIUM, _clip_react_poison_haze),
    VfxClipId.REACT_BLEED_FAN.value: ClipSpec(VfxClipId.REACT_BLEED_FAN, "React bleed fan", VfxBudgetTier.LOW, _clip_react_bleed_fan),
    VfxClipId.REACT_FROST_SHATTER.value: ClipSpec(VfxClipId.REACT_FROST_SHATTER, "React frost shatter", VfxBudgetTier.MEDIUM, _clip_react_frost_shatter),
    VfxClipId.REACT_LIGHTNING_CAGE.value: ClipSpec(VfxClipId.REACT_LIGHTNING_CAGE, "React lightning cage", VfxBudgetTier.MEDIUM, _clip_react_lightning_cage),
    VfxClipId.REACT_WITHER_WISP.value: ClipSpec(VfxClipId.REACT_WITHER_WISP, "React wither wisp", VfxBudgetTier.MEDIUM, _clip_react_wither_wisp),
    VfxClipId.REACT_HEAL_BURST.value: ClipSpec(VfxClipId.REACT_HEAL_BURST, "React heal burst", VfxBudgetTier.LOW, _clip_react_heal_burst),
    VfxClipId.REACT_SHIELD_SNAP.value: ClipSpec(VfxClipId.REACT_SHIELD_SNAP, "React shield snap", VfxBudgetTier.MEDIUM, _clip_react_shield_snap),
}


def resolve_clip(clip_id: VfxClipLike) -> ClipSpec:
    token = clip_id.value if isinstance(clip_id, VfxClipId) else str(clip_id).strip()
    entry = _CLIPS.get(token)
    if entry is None:
        known = ", ".join(sorted(_CLIPS))
        raise ValueError(f"vfx.clip: unknown clip_id={clip_id!r}. Known: {known}")
    return entry


def build_clip(clip_id: VfxClipLike, *, params: Mapping[str, Any]) -> ActionSpec:
    spec = resolve_clip(clip_id)
    return spec.builder(params)


def catalog_clips() -> Mapping[str, ClipSpec]:
    return MappingProxyType(dict(_CLIPS))


__all__ = [
    "resolve_clip",
    "build_clip",
    "catalog_clips",
]
