from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder.v2 import (  # noqa: E402
    ActionType,
    AnchorMode,
    AnchorPoint,
    AtMode,
    Easing,
    MotionMode,
    Particle,
    fx,
)


class V2EffectsOrchestrationActionsTests(unittest.TestCase):
    def test_orchestration_actions_serialize(self) -> None:
        base = fx.particles_point(Particle.END_ROD)

        self.assertEqual(
            fx.animate(action=base, duration_ticks=20, period_ticks=1, easing=Easing.LINEAR).to_dict()["type"],
            ActionType.ANIMATE.value,
        )
        self.assertEqual(
            fx.state_machine(charge=base, sustain=base, release=base).to_dict()["type"],
            ActionType.STATE_MACHINE.value,
        )
        self.assertEqual(fx.burst(action=base, times=3).to_dict()["type"], ActionType.BURST.value)
        self.assertEqual(fx.pulse(action=base).to_dict()["type"], ActionType.PULSE.value)
        self.assertEqual(fx.loop(action=base).to_dict()["type"], ActionType.LOOP.value)
        self.assertEqual(fx.trail(action=base).to_dict()["type"], ActionType.TRAIL.value)
        self.assertEqual(
            fx.attach(action=base, anchor=AnchorMode.CASTER, point=AnchorPoint.HEAD).to_dict()["type"],
            ActionType.ATTACH.value,
        )
        self.assertEqual(
            fx.follow(action=base, anchor=AnchorMode.CASTER, point=AnchorPoint.BODY).to_dict()["type"],
            ActionType.FOLLOW.value,
        )
        self.assertEqual(
            fx.motion(action=base, mode=MotionMode.TRANSLATE, at=AtMode.ORIGIN).to_dict()["type"],
            ActionType.MOTION.value,
        )
        self.assertEqual(
            fx.animate_realtime(action=base, duration_millis=600, period_millis=50).to_dict()["type"],
            ActionType.ANIMATE_REALTIME.value,
        )


if __name__ == "__main__":
    unittest.main()
