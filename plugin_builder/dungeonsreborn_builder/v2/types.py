"""Shared typing aliases for builder v2."""

from __future__ import annotations

from typing import Literal, NewType

DomainName = Literal["ability", "item", "mob", "recipe", "shop", "quest", "upgrade", "class", "bundle"]
BuildProfile = Literal["dev", "prod"]
TriggerPhase = Literal["PRE", "POST"]
ProjectileBlockCollision = Literal["stop", "bounce", "pass_through"]

Identifier = NewType("Identifier", str)
Symbol = NewType("Symbol", str)
FieldPath = NewType("FieldPath", str)

__all__ = [
    "DomainName",
    "BuildProfile",
    "TriggerPhase",
    "ProjectileBlockCollision",
    "Identifier",
    "Symbol",
    "FieldPath",
]
