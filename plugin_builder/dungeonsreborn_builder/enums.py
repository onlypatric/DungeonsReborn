"""Enums for common builder inputs."""

from __future__ import annotations

from enum import Enum
import re

from .effects_ids import ACTION_TYPES, CONDITION_TYPES, COST_TYPES, REQUIREMENT_TYPES, TARGETER_TYPES


class Easing(str, Enum):
    LINEAR = "LINEAR"
    IN_OUT_CUBIC = "IN_OUT_CUBIC"
    OUT_QUAD = "OUT_QUAD"


class At(str, Enum):
    ORIGIN = "origin"
    LAST_HIT = "last_hit"
    LAST_ENTITY = "last_entity"


class RequirementType(str, Enum):
    SNEAKING = "sneaking"
    PERMISSION = "permission"
    HAS_ITEM_TAG = "has_item_tag"


class CostType(str, Enum):
    MANA = "mana"
    RESOURCE = "resource"
    CONSUME_ITEM = "consume_item"
    CONSUME_MAIN_HAND = "consume_main_hand"
    DURABILITY = "durability"
    DURABILITY_MAIN_HAND = "durability_main_hand"


def _enum_members(values: list[str]) -> dict[str, str]:
    members: dict[str, str] = {}
    for value in values:
        key = re.sub(r"[^A-Z0-9_]", "_", value.upper().replace("-", "_"))
        if key in members:
            suffix = 2
            while f"{key}_{suffix}" in members:
                suffix += 1
            key = f"{key}_{suffix}"
        members[key] = value
    return members


# Auto-synced ID enums (generated from EffectsYamlAbilities.java)
ActionType = Enum("ActionType", _enum_members(ACTION_TYPES), type=str)
TargeterType = Enum("TargeterType", _enum_members(TARGETER_TYPES), type=str)
ConditionType = Enum("ConditionType", _enum_members(CONDITION_TYPES), type=str)

_KNOWN_REQUIREMENT_TYPES = tuple(REQUIREMENT_TYPES)
_KNOWN_COST_TYPES = tuple(COST_TYPES)


class DamageMode(str, Enum):
    FLAT = "FLAT"
    PERCENT_MAX_HEALTH = "PERCENT_MAX_HEALTH"
    TRUE = "TRUE"


class DamageType(str, Enum):
    PHYSICAL = "PHYSICAL"
    MAGIC = "MAGIC"
    FIRE = "FIRE"
    ICE = "ICE"
    LIGHTNING = "LIGHTNING"
    POISON = "POISON"
    WITHER = "WITHER"
    BLEED = "BLEED"
    HOLY = "HOLY"
    ARCANE = "ARCANE"
    VOID = "VOID"
    EXPLOSION = "EXPLOSION"
    PROJECTILE = "PROJECTILE"


class DamageCause(str, Enum):
    DIRECT = "DIRECT"
    PROJECTILE = "PROJECTILE"
    DOT = "DOT"
    AOE = "AOE"
    CHAIN = "CHAIN"
    CRIT = "CRIT"
    LIFESTEAL = "LIFESTEAL"
    FALLOFF = "FALLOFF"
    PERCENT = "PERCENT"
    TRUE = "TRUE"
    EXPLOSION = "EXPLOSION"
    ENVIRONMENT = "ENVIRONMENT"


class DamagePolicy(str, Enum):
    ANY = "any"
    PVE_ONLY = "pve_only"
    PVP_ONLY = "pvp_only"
    HOSTILE_DEFAULT = "hostile_default"


class HealType(str, Enum):
    DIRECT = "DIRECT"
    HOT = "HOT"
    SHIELD = "SHIELD"
    ABSORB = "ABSORB"
