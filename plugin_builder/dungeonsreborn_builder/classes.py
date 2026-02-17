"""Classes + progression builder."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Optional

from .base import ExporterBase


@dataclass
class SkillNode:
    node_id: str
    name: str
    description: Optional[str] = None
    cost: int = 1
    requires: Optional[str] = None
    bonuses: Optional[Dict[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "name": self.name,
            "cost": self.cost,
        }
        if self.description:
            data["description"] = self.description
        if self.requires:
            data["requires"] = self.requires
        if self.bonuses:
            data["bonuses"] = self.bonuses
        return data


@dataclass
class ClassSpec:
    class_id: str
    name: str
    description: Optional[str] = None
    min_level: int = 0
    unlock: Optional[Dict[str, Any]] = None
    nodes: List[SkillNode] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "name": self.name,
            "minLevel": self.min_level,
        }
        if self.description:
            data["description"] = self.description
        if self.unlock:
            data["unlock"] = dict(self.unlock)
        if self.nodes:
            data["nodes"] = {node.node_id: node.to_dict() for node in self.nodes}
        return data


class ClassExporter(ExporterBase):
    def write_classes(self, classes: Iterable[ClassSpec], filename: str = "classes.yml") -> str:
        data = {"classes": {cls.class_id: cls.to_dict() for cls in classes}}
        return self.write_yaml(filename, data)


def elemental_class_pack(prefix: str = "elemental") -> List[ClassSpec]:
    base_nodes = [
        SkillNode("core_1", "Nucleo I", bonuses={"mana": 5}),
        SkillNode("core_2", "Nucleo II", requires="core_1", bonuses={"mana": 8}),
    ]
    return [
        ClassSpec(f"{prefix}_fire", "Araldo del Fuoco", nodes=base_nodes),
        ClassSpec(f"{prefix}_frost", "Custode del Gelo", nodes=base_nodes),
        ClassSpec(f"{prefix}_storm", "Figlio della Tempesta", nodes=base_nodes),
        ClassSpec(f"{prefix}_earth", "Guardiano della Terra", nodes=base_nodes),
        ClassSpec(f"{prefix}_arcane", "Astromante Arcano", nodes=base_nodes),
    ]
