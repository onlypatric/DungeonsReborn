"""Strict typed upgrades declarations for builder v2."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from .core import BuildContext, Ref
from .enums import (
    UpgradeActivator,
    UpgradeActivatorLike,
    UpgradeRarity,
    UpgradeRarityLike,
    coerce_upgrade_activator,
    coerce_upgrade_rarity,
)


@dataclass(frozen=True)
class UpgradeSpellSpec:
    ability: Ref | str
    activator: UpgradeActivatorLike = UpgradeActivator.RIGHT_CLICK

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        return {
            "ability": ctx.resolve(self.ability, domain="ability", field=f"{field}.ability"),
            "activator": coerce_upgrade_activator(self.activator, field=f"{field}.activator"),
        }


class UpgradeV2:
    domain = "upgrade"

    def __init__(
        self,
        ctx: BuildContext,
        *,
        name: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> None:
        self.ctx = ctx
        self.id, self.symbol = ctx.register("upgrade", symbol=symbol or name, id_override=id, parts=[name])
        self._spec: dict[str, object] = {
            "name": name,
        }

    def rarity(self, value: UpgradeRarityLike) -> "UpgradeV2":
        self._spec["rarity"] = coerce_upgrade_rarity(value, field=f"upgrades.{self.id}.rarity")
        return self

    def for_item(self, item_ref: Ref | str) -> "UpgradeV2":
        item_id = self.ctx.resolve(item_ref, domain="item", field=f"upgrades.{self.id}.for_item")
        compatibility = self._spec.setdefault("compatibility", [])
        assert isinstance(compatibility, list)
        compatibility.append(item_id)
        return self

    def spell(
        self,
        ability_ref: Ref | str,
        *,
        activator: UpgradeActivatorLike = UpgradeActivator.RIGHT_CLICK,
    ) -> "UpgradeV2":
        self._spec["spell"] = UpgradeSpellSpec(ability=ability_ref, activator=activator).build(
            self.ctx,
            field=f"upgrades.{self.id}.spell",
        )
        return self

    def spell_spec(self, spec: UpgradeSpellSpec) -> "UpgradeV2":
        self._spec["spell"] = spec.build(self.ctx, field=f"upgrades.{self.id}.spell")
        return self

    def secondary(self, *ability_refs: Ref | str) -> "UpgradeV2":
        resolved = [
            self.ctx.resolve(entry, domain="ability", field=f"upgrades.{self.id}.secondary")
            for entry in ability_refs
        ]
        entries = self._spec.setdefault("secondary", [])
        assert isinstance(entries, list)
        entries.extend(resolved)
        return self

    def build(self) -> dict[str, object]:
        return dict(self._spec)


class upgrade:
    @staticmethod
    def create(
        ctx: BuildContext,
        *,
        name: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> UpgradeV2:
        return UpgradeV2(ctx=ctx, name=name, id=id, symbol=symbol)


__all__ = ["UpgradeSpellSpec", "UpgradeV2", "upgrade"]
