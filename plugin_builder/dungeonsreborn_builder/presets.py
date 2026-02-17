"""Biome presets for world progress generation."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional

from .families import NumericBand


@dataclass
class BiomePreset:
    biome_id: str
    tier: int
    mob_count: int
    upgrade_count: int
    consumable_count: int
    token_band: Optional[NumericBand] = None
    level_band: Optional[NumericBand] = None
    rarity_band: Optional[NumericBand] = None
    notes: Optional[str] = None

    def validate(self) -> None:
        if not self.biome_id:
            raise ValueError("biome_id is required")
        if self.tier < 0:
            raise ValueError("tier must be >= 0")
        for label, value in (
            ("mob_count", self.mob_count),
            ("upgrade_count", self.upgrade_count),
            ("consumable_count", self.consumable_count),
        ):
            if value < 0:
                raise ValueError(f"{label} must be >= 0")
        if self.token_band is not None:
            self.token_band.validate("token_band")
        if self.level_band is not None:
            self.level_band.validate("level_band")
        if self.rarity_band is not None:
            self.rarity_band.validate("rarity_band")


def _band_from(value: Any, label: str) -> Optional[NumericBand]:
    if value is None:
        return None
    if isinstance(value, NumericBand):
        return value
    if isinstance(value, (list, tuple)) and len(value) == 2:
        return NumericBand(float(value[0]), float(value[1]))
    raise ValueError(f"{label} must be a NumericBand or (min, max)")


def load_biome_presets(source: Dict[str, Dict[str, Any]]) -> Dict[str, BiomePreset]:
    presets: Dict[str, BiomePreset] = {}
    for biome_id, entry in source.items():
        if entry is None:
            raise ValueError(f"{biome_id} preset is missing")
        preset = BiomePreset(
            biome_id=biome_id,
            tier=int(entry.get("tier", 0)),
            mob_count=int(entry.get("mob_count", 0)),
            upgrade_count=int(entry.get("upgrade_count", 0)),
            consumable_count=int(entry.get("consumable_count", 0)),
            token_band=_band_from(entry.get("token_band"), f"{biome_id}.token_band"),
            level_band=_band_from(entry.get("level_band"), f"{biome_id}.level_band"),
            rarity_band=_band_from(entry.get("rarity_band"), f"{biome_id}.rarity_band"),
            notes=entry.get("notes"),
        )
        preset.validate()
        presets[biome_id] = preset
    return presets
