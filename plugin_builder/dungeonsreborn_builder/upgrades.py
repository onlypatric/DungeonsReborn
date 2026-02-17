"""Upgrades builder (MVP with abstractions)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Optional

from .base import BuilderBase, ExporterBase


@dataclass
class UpgradeSpell:
    ability: str
    activator: str = "RIGHT_CLICK"

    def to_dict(self) -> Dict[str, Any]:
        return {"ability": self.ability, "activator": self.activator}


class UpgradeBuilder(BuilderBase):
    def __init__(self, upgrade_id: str) -> None:
        super().__init__(_id=upgrade_id)
        self._rarity: Optional[str] = None
        self._compatibility: List[str] = []
        self._conflicts: List[str] = []
        self._spell: Optional[UpgradeSpell] = None
        self._secondary: List[str] = []

    def rarity(self, value: str) -> "UpgradeBuilder":
        self._rarity = value
        return self

    def compatibility(self, *item_types: str) -> "UpgradeBuilder":
        self._compatibility.extend(item_types)
        return self

    def conflicts(self, *upgrade_ids: str) -> "UpgradeBuilder":
        self._conflicts.extend(upgrade_ids)
        return self

    def spell(self, ability: str, activator: str = "RIGHT_CLICK") -> "UpgradeBuilder":
        self._spell = UpgradeSpell(ability, activator)
        return self

    def secondary(self, *ability_ids: str) -> "UpgradeBuilder":
        self._secondary.extend(ability_ids)
        return self

    def build(self) -> Dict[str, Any]:
        upgrade_id = self._ensure_id("upgrade_id")
        self._ensure_name()
        payload: Dict[str, Any] = {"name": self._name}
        if self._description:
            payload["description"] = self._description
        if self._rarity:
            payload["rarity"] = self._rarity
        if self._compatibility:
            payload["compatibility"] = list(self._compatibility)
        if self._conflicts:
            payload["conflicts"] = list(self._conflicts)
        if self._spell:
            payload["spell"] = self._spell.to_dict()
        if self._secondary:
            payload.setdefault("behaviors", {})["secondaryAbilities"] = list(self._secondary)
        return self._apply_overrides(payload, upgrade_id)


class UpgradesExporter(ExporterBase):
    def write_upgrade(self, builder: UpgradeBuilder, filename: Optional[str] = None) -> str:
        data = builder.build()
        name = filename or f"{builder._id}.yml"
        return self.write_yaml(name, data)

    def write_batch(self, builders: Iterable[UpgradeBuilder], filename: str) -> str:
        data = {"upgrades": {builder._id: builder.build() for builder in builders}}
        return self.write_yaml(filename, data)


def basic_rune_pack(prefix: str = "basic_rune") -> List[UpgradeBuilder]:
    return [
        UpgradeBuilder(f"{prefix}_carica_i").name("<gold>Carica I</gold>").compatibility("rune"),
        UpgradeBuilder(f"{prefix}_durata_i").name("<green>Durata I</green>").compatibility("rune"),
    ]


def elemental_rune_pack(element: str, prefix: Optional[str] = None) -> List[UpgradeBuilder]:
    key = prefix or f"elemental_{element}_rune"
    return [
        UpgradeBuilder(f"{key}_i").name(f"<{element}>Runa {element.title()} I</{element}>").compatibility("rune"),
        UpgradeBuilder(f"{key}_ii").name(f"<{element}>Runa {element.title()} II</{element}>").compatibility("rune"),
    ]
