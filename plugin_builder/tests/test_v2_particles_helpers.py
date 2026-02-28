from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (  # noqa: E402
    ActionType,
    Particle,
    Point3Spec,
    PolylineSpec,
    ControlPointsSpec,
    TriangleSpec,
    MeshSpec,
    VelocitySpec,
    PhysicsSpec,
    GradientSpec,
    ParticlePhysicsCollisionMode,
    fx,
)


class V2ParticlesHelpersTests(unittest.TestCase):
    def _assert_type(self, action, expected: ActionType) -> None:
        built = action.to_dict()
        self.assertEqual(built["type"], expected.value)
        self.assertIn("particle", built)

    def test_particles_helpers_minimal(self) -> None:
        p0 = Point3Spec(x=0.0, y=0.0, z=0.0)
        p1 = Point3Spec(x=1.0, y=0.0, z=0.0)
        p2 = Point3Spec(x=1.0, y=1.0, z=0.0)
        p3 = Point3Spec(x=2.0, y=0.0, z=0.0)
        poly = PolylineSpec(points=[p0, p1])
        controls = ControlPointsSpec(points=[p0, p1, p2, p3])
        mesh = MeshSpec(triangles=[TriangleSpec(a=p0, b=p1, c=p2)])
        physics = PhysicsSpec(collision_mode=ParticlePhysicsCollisionMode.STOP)
        vel = VelocitySpec(x=0.1, y=0.2, z=0.3)
        gradient = GradientSpec(start_color="#00FF00", end_color="#FF0000", size=1.0)

        self._assert_type(fx.particles_ring(Particle.CLOUD, radius=1.0, points=16), ActionType.PARTICLES_RING)
        self._assert_type(fx.particles_point(Particle.CLOUD), ActionType.PARTICLES_POINT)
        self._assert_type(fx.particles_physics(Particle.CLOUD, velocity=vel, physics=physics), ActionType.PARTICLES_PHYSICS)
        self._assert_type(
            fx.particles_physics_points(Particle.CLOUD, points=[p0], velocity=vel, physics=physics),
            ActionType.PARTICLES_PHYSICS_POINTS,
        )
        self._assert_type(
            fx.particles_physics_polyline(Particle.CLOUD, polyline=poly, velocity=vel, physics=physics),
            ActionType.PARTICLES_PHYSICS_POLYLINE,
        )
        self._assert_type(
            fx.particles_physics_mesh(Particle.CLOUD, mesh=mesh, velocity=vel, physics=physics),
            ActionType.PARTICLES_PHYSICS_MESH,
        )
        self._assert_type(fx.particles_line(Particle.CLOUD, length=3.0), ActionType.PARTICLES_LINE)
        self._assert_type(fx.particles_arc(Particle.CLOUD, radius=2.0, angle_degrees=90.0, points=18), ActionType.PARTICLES_ARC)
        self._assert_type(
            fx.particles_disk(Particle.CLOUD, radius=1.2, rings=4, points_per_ring=12),
            ActionType.PARTICLES_DISK,
        )
        self._assert_type(
            fx.particles_sphere_shell(Particle.CLOUD, radius=1.1, points=20),
            ActionType.PARTICLES_SPHERE_SHELL,
        )
        self._assert_type(
            fx.particles_sphere_filled(Particle.CLOUD, radius=1.1, points=20),
            ActionType.PARTICLES_SPHERE_FILLED,
        )
        self._assert_type(
            fx.particles_helix(Particle.CLOUD, radius=0.8, turns=2.0, length=3.0, points=24),
            ActionType.PARTICLES_HELIX,
        )
        self._assert_type(
            fx.particles_bezier(Particle.CLOUD, p0=p0, p1=p1, p2=p2, p3=p3),
            ActionType.PARTICLES_BEZIER,
        )
        self._assert_type(
            fx.particles_spline(Particle.CLOUD, control_points=controls),
            ActionType.PARTICLES_SPLINE,
        )
        self._assert_type(fx.particles_points(Particle.CLOUD, points=[p0]), ActionType.PARTICLES_POINTS)
        self._assert_type(
            fx.particles_polyline(Particle.CLOUD, polyline=poly, gradient=gradient),
            ActionType.PARTICLES_POLYLINE,
        )
        self._assert_type(
            fx.particles_mesh(Particle.CLOUD, mesh=mesh, gradient=gradient),
            ActionType.PARTICLES_MESH,
        )
        self._assert_type(
            fx.particles_cone(Particle.CLOUD, length=3.0, angle_degrees=70.0, rings=4, points_per_ring=10),
            ActionType.PARTICLES_CONE,
        )
        self._assert_type(
            fx.particles_cylinder(Particle.CLOUD, radius=1.2, height=2.0, rings=4, points_per_ring=10),
            ActionType.PARTICLES_CYLINDER,
        )
        self._assert_type(
            fx.particles_box(Particle.CLOUD, x_radius=1.0, y_radius=0.7, z_radius=1.0, step=0.3),
            ActionType.PARTICLES_BOX,
        )
        self._assert_type(
            fx.particles_polygon(Particle.CLOUD, radius=1.0, sides=6, points_per_edge=8),
            ActionType.PARTICLES_POLYGON,
        )


if __name__ == "__main__":
    unittest.main()
