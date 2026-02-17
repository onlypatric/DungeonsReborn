"""QoS helpers for VFX budgets and scaling."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional

from .effects import Action


@dataclass
class VfxBudget:
    max_particles_per_ability: int = 2000
    max_particles_per_tick: int = 300
    max_actions_per_tick: int = 30

    def to_dict(self) -> Dict[str, int]:
        return {
            "maxParticlesPerAbility": self.max_particles_per_ability,
            "maxParticlesPerTick": self.max_particles_per_tick,
            "maxActionsPerTick": self.max_actions_per_tick,
        }


@dataclass
class VfxDegradationStep:
    multiplier: float
    label: str = ""

    def to_dict(self) -> Dict[str, object]:
        payload: Dict[str, object] = {"multiplier": self.multiplier}
        if self.label:
            payload["label"] = self.label
        return payload


@dataclass
class VfxLiteMode:
    enabled: bool = True
    preset_swaps: Dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, object]:
        return {
            "enabled": self.enabled,
            "presetSwaps": dict(self.preset_swaps),
        }


@dataclass
class VfxQoSConfig:
    global_multiplier: float = 1.0
    per_player_toggle_key: str = "vfx"
    budget: VfxBudget = field(default_factory=VfxBudget)
    degradation_steps: List[VfxDegradationStep] = field(
        default_factory=lambda: [
            VfxDegradationStep(multiplier=0.7, label="warm"),
            VfxDegradationStep(multiplier=0.4, label="hot"),
            VfxDegradationStep(multiplier=0.2, label="overload"),
        ]
    )
    lite_mode: Optional[VfxLiteMode] = field(default_factory=VfxLiteMode)

    def to_dict(self) -> Dict[str, object]:
        payload: Dict[str, object] = {
            "globalMultiplier": self.global_multiplier,
            "perPlayerToggleKey": self.per_player_toggle_key,
            "budget": self.budget.to_dict(),
            "degradation": [step.to_dict() for step in self.degradation_steps],
        }
        if self.lite_mode:
            payload["liteMode"] = self.lite_mode.to_dict()
        return payload


def _scale_int(value: int, multiplier: float, minimum: int = 1) -> int:
    return max(minimum, int(round(value * multiplier)))


def _scale_action_params(params: Dict[str, object], multiplier: float) -> Dict[str, object]:
    scaled = dict(params)
    for key in ("count", "points", "copies"):
        value = scaled.get(key)
        if isinstance(value, int):
            scaled[key] = _scale_int(value, multiplier)
    if "action" in scaled and isinstance(scaled["action"], dict):
        scaled["action"] = _scale_action_dict(scaled["action"], multiplier)
    if "actions" in scaled and isinstance(scaled["actions"], Iterable):
        actions = []
        for item in scaled["actions"]:
            if isinstance(item, dict):
                actions.append(_scale_action_dict(item, multiplier))
            else:
                actions.append(item)
        scaled["actions"] = actions
    return scaled


def _scale_action_dict(action_dict: Dict[str, object], multiplier: float) -> Dict[str, object]:
    if "type" not in action_dict:
        return action_dict
    params = {k: v for k, v in action_dict.items() if k != "type"}
    return {"type": action_dict["type"], **_scale_action_params(params, multiplier)}


def scale_action(action: Action, multiplier: float) -> Action:
    return Action(action.type, _scale_action_params(action.params, multiplier))


def scale_actions(actions: Iterable[Action], multiplier: float) -> List[Action]:
    return [scale_action(action, multiplier) for action in actions]

