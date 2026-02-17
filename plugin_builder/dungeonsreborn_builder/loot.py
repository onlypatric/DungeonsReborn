"""Loot builder (shared pools + custom items)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Optional

from .base import ExporterBase
from .vanilla import EnumValue, Material, normalize_enum_name


@dataclass
class LootItem:
    material: Material
    chance: float
    min_amount: int = 1
    max_amount: int = 1
    custom_item: Optional[str] = None

    def __post_init__(self) -> None:
        if not isinstance(self.material, EnumValue):
            raise ValueError("material must be provided as a vanilla enum value")

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "material": normalize_enum_name(self.material.name),
            "chance": self.chance,
            "min": self.min_amount,
            "max": self.max_amount,
        }
        if self.custom_item:
            # Use itemId so MobYamlRegistry resolves custom items via item templates.
            data["itemId"] = self.custom_item
        return data


@dataclass
class LootPool:
    pool_id: str
    items: List[LootItem]

    def to_dict(self) -> Dict[str, Any]:
        # DungeonsReborn loot pools expect a "drops" list.
        return {"drops": [item.to_dict() for item in self.items]}


class LootExporter(ExporterBase):
    def write_pool(self, pool: LootPool, filename: Optional[str] = None) -> str:
        name = filename or f"{pool.pool_id}.yml"
        data = {"loot": pool.to_dict()}
        return self.write_yaml(name, data)

    def write_batch(self, pools: Iterable[LootPool], filename: str) -> str:
        data = {"lootPools": {pool.pool_id: pool.to_dict() for pool in pools}}
        return self.write_yaml(filename, data)


def undead_basic_pool(pool_id: str = "undead_basic") -> LootPool:
    return LootPool(
        pool_id,
        [
            LootItem(Material.ROTTEN_FLESH, 0.45, 1, 3),
            LootItem(Material.BONE, 0.25, 1, 2),
            LootItem(Material.COAL, 0.08, 1, 1),
        ],
    )
