"""Difficulty scaling helpers for content packs."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict

from .items import ItemBuilder
from .mobs import MobBuilder
from .pack import ContentPack


@dataclass(frozen=True)
class DifficultyProfile:
    name: str
    mob_health: float = 1.0
    mob_damage: float = 1.0
    mob_speed: float = 1.0
    reward_tokens: float = 1.0
    reward_xp: float = 1.0


DEFAULT_PROFILES: Dict[str, DifficultyProfile] = {
    "easy": DifficultyProfile("easy", mob_health=0.8, mob_damage=0.8, mob_speed=0.95),
    "normal": DifficultyProfile("normal"),
    "hard": DifficultyProfile("hard", mob_health=1.25, mob_damage=1.25, mob_speed=1.05),
    "elite": DifficultyProfile("elite", mob_health=1.6, mob_damage=1.5, mob_speed=1.1, reward_tokens=1.2),
}


def _scale_item(builder: ItemBuilder, profile: DifficultyProfile) -> None:
    for key, value in list(builder._stats.items()):
        if key.lower().endswith("_damage") or key.lower() == "damage":
            builder._stats[key] = value * profile.mob_damage


def _scale_mob(builder: MobBuilder, profile: DifficultyProfile) -> None:
    if "MAX_HEALTH" in builder._stats:
        builder._stats["MAX_HEALTH"] *= profile.mob_health
    if "ATTACK_DAMAGE" in builder._stats:
        builder._stats["ATTACK_DAMAGE"] *= profile.mob_damage
    if "MOVEMENT_SPEED" in builder._stats:
        builder._stats["MOVEMENT_SPEED"] *= profile.mob_speed


def scale_pack(pack: ContentPack, difficulty: str) -> ContentPack:
    profile = DEFAULT_PROFILES.get(difficulty.lower())
    if profile is None:
        raise ValueError(f"Unknown difficulty profile: {difficulty}")
    for item in pack.items:
        _scale_item(item, profile)
    for mob in pack.mobs:
        _scale_mob(mob, profile)
    return pack
