"""Minion summon builder helpers."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, Iterable, List, Mapping, Optional

from .enums import DamageType
from .mobs import MobParticlesSpec
from .vanilla import Attribute, EnumValue, normalize_enum_name


def _enum_or_str(value: Any, label: str) -> str:
    if isinstance(value, Enum):
        return value.name
    if isinstance(value, EnumValue):
        return normalize_enum_name(value.name)
    if isinstance(value, str):
        return value
    raise ValueError(f"{label} must be provided as an enum value or string")


class MinionMode(str, Enum):
    AGGRESSIVE = "AGGRESSIVE"
    DEFENSIVE = "DEFENSIVE"
    PASSIVE = "PASSIVE"
    FOLLOW = "FOLLOW"
    GUARD = "GUARD"
    HOLD = "HOLD"
    ASSIST = "ASSIST"
    AVOID = "AVOID"


class MinionFormation(str, Enum):
    RANDOM = "RANDOM"
    CIRCLE = "CIRCLE"
    LINE = "LINE"
    CONE = "CONE"
    GRID = "GRID"


@dataclass
class MinionScaling:
    health_per_level: float = 0.0
    damage_per_level: float = 0.0
    health_per_max_health: float = 0.0
    damage_per_max_health: float = 0.0
    health_per_mana_max: float = 0.0
    damage_per_mana_max: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "healthPerLevel": self.health_per_level,
            "damagePerLevel": self.damage_per_level,
            "healthPerMaxHealth": self.health_per_max_health,
            "damagePerMaxHealth": self.damage_per_max_health,
            "healthPerManaMax": self.health_per_mana_max,
            "damagePerManaMax": self.damage_per_mana_max,
        }


@dataclass
class MinionOwnerScalingSpec:
    level_multiplier: float = 0.0
    strength_multiplier: float = 0.0
    dexterity_multiplier: float = 0.0
    intelligence_multiplier: float = 0.0
    vitality_multiplier: float = 0.0
    max_mana_multiplier: float = 0.0
    max_health_multiplier: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "levelMultiplier": self.level_multiplier,
            "strengthMultiplier": self.strength_multiplier,
            "dexterityMultiplier": self.dexterity_multiplier,
            "intelligenceMultiplier": self.intelligence_multiplier,
            "vitalityMultiplier": self.vitality_multiplier,
            "maxManaMultiplier": self.max_mana_multiplier,
            "maxHealthMultiplier": self.max_health_multiplier,
        }


@dataclass
class MinionScalingLimits:
    max_bonus_health: float = 0.0
    max_bonus_damage: float = 0.0
    decay_exponent: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "maxBonusHealth": self.max_bonus_health,
            "maxBonusDamage": self.max_bonus_damage,
            "decayExponent": self.decay_exponent,
        }


@dataclass
class MinionSummonSpec:
    waves: int = 1
    wave_interval_ticks: int = 0
    formation: MinionFormation | str = MinionFormation.RANDOM
    formation_radius: float = 0.0
    safe_spawn: bool = False
    max_spawn_attempts: int = 6

    def to_dict(self) -> Dict[str, Any]:
        return {
            "waves": self.waves,
            "waveIntervalTicks": self.wave_interval_ticks,
            "formation": _enum_or_str(self.formation, "formation"),
            "formationRadius": self.formation_radius,
            "safeSpawn": self.safe_spawn,
            "maxSpawnAttempts": self.max_spawn_attempts,
        }


@dataclass
class MinionTargetRules:
    allow_pvp: bool = False
    allow_party_targets: bool = False
    share_owner_aggro: bool = True
    max_distance_from_owner: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "allowPvp": self.allow_pvp,
            "allowPartyTargets": self.allow_party_targets,
            "shareOwnerAggro": self.share_owner_aggro,
            "maxDistanceFromOwner": self.max_distance_from_owner,
        }


@dataclass
class MinionPassiveSpec:
    ability: str
    period_ticks: int = 40

    def to_dict(self) -> Dict[str, Any]:
        return {"ability": self.ability, "periodTicks": self.period_ticks}


@dataclass
class MinionSpecialAttackSpec:
    ability: str
    cooldown_ticks: int = 60
    chance: float = 1.0
    require_target: bool = True
    cost_multiplier: float = 1.0
    cost_add: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "ability": self.ability,
            "cooldownTicks": self.cooldown_ticks,
            "chance": self.chance,
            "requireTarget": self.require_target,
            "costMultiplier": self.cost_multiplier,
            "costAdd": self.cost_add,
        }


@dataclass
class MinionSummonCostSpec:
    cost_type: str
    amount: float
    multiplier: float = 1.0
    add: float = 0.0
    resource: Optional[str] = None
    allow_break: bool = False

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "type": self.cost_type,
            "amount": self.amount,
            "multiplier": self.multiplier,
            "add": self.add,
        }
        if self.resource is not None:
            payload["resource"] = self.resource
        if self.allow_break:
            payload["allowBreak"] = self.allow_break
        return payload


def minion_stat_overrides(values: Mapping[Attribute | str, float]) -> Dict[str, float]:
    return {_enum_or_str(key, "attribute"): value for key, value in values.items()}


def minion_resistances(values: Mapping[DamageType | str, float]) -> Dict[str, float]:
    return {_enum_or_str(key, "damage_type"): value for key, value in values.items()}


def minion_immunities(values: Iterable[DamageType | str]) -> List[str]:
    return [_enum_or_str(value, "damage_type") for value in values]

