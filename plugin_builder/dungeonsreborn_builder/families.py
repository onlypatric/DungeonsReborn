"""Template specs for world progress families."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class NumericBand:
    minimum: float
    maximum: float

    def validate(self, label: str) -> None:
        if self.minimum < 0 or self.maximum < 0:
            raise ValueError(f"{label} must be >= 0")
        if self.maximum < self.minimum:
            raise ValueError(f"{label} max must be >= min")


@dataclass
class FamilyTemplateBase:
    template_id: str
    tier: int
    count: int
    rarity_band: Optional[NumericBand] = None
    token_band: Optional[NumericBand] = None
    level_band: Optional[NumericBand] = None
    notes: Optional[str] = None

    def validate(self) -> None:
        if not self.template_id:
            raise ValueError("template_id is required")
        if self.tier < 0:
            raise ValueError("tier must be >= 0")
        if self.count < 0:
            raise ValueError("count must be >= 0")
        if self.rarity_band is not None:
            self.rarity_band.validate("rarity_band")
        if self.token_band is not None:
            self.token_band.validate("token_band")
        if self.level_band is not None:
            self.level_band.validate("level_band")


@dataclass
class MobFamilyTemplate(FamilyTemplateBase):
    upgrade_pool: Optional[int] = None

    def validate(self) -> None:
        super().validate()
        if self.upgrade_pool is not None and self.upgrade_pool < 0:
            raise ValueError("upgrade_pool must be >= 0")


@dataclass
class LootFamilyTemplate(FamilyTemplateBase):
    pass


@dataclass
class UpgradeFamilyTemplate(FamilyTemplateBase):
    pass


@dataclass
class ConsumableFamilyTemplate(FamilyTemplateBase):
    pass


@dataclass
class QuestFamilyTemplate(FamilyTemplateBase):
    pass


@dataclass
class ShopFamilyTemplate(FamilyTemplateBase):
    pass
