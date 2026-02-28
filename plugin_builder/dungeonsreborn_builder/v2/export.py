"""Unified v2 export pipeline."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Any

import yaml

from .bundles import BundleV2
from .classes import ClassV2
from .core import BuildContext, BuildValidationError
from .crafting import RecipeV2
from .effects import AbilityV2
from .internal.io import write_yaml
from .internal.validate import ValidationReport, render_report, validate_pack
from .items import ItemV2
from .mobs import MobV2
from .quests import QuestV2
from .shops import ShopV2
from .upgrades import UpgradeV2


@dataclass
class PackV2:
    ctx: BuildContext = field(default_factory=lambda: BuildContext(strict=True, profile="dev"))
    abilities: list[AbilityV2] = field(default_factory=list)
    items: list[ItemV2] = field(default_factory=list)
    mobs: list[MobV2] = field(default_factory=list)
    recipes: list[RecipeV2] = field(default_factory=list)
    shops: list[ShopV2] = field(default_factory=list)
    quests: list[QuestV2] = field(default_factory=list)
    upgrades: list[UpgradeV2] = field(default_factory=list)
    classes: list[ClassV2] = field(default_factory=list)

    def add(self, *entries: Any) -> "PackV2":
        for entry in entries:
            self._add_one(entry)
        return self

    def _add_one(self, entry: Any) -> None:
        if isinstance(entry, BundleV2):
            data = entry.all_entries()
            self.add(
                data["abilities"],
                data["items"],
                data["mobs"],
                data["recipes"],
                data["shops"],
                data["quests"],
                data["upgrades"],
                data.get("classes", []),
            )
            return
        if isinstance(entry, (list, tuple, set)):
            for nested in entry:
                self._add_one(nested)
            return
        if isinstance(entry, AbilityV2):
            self._add_ability_unique(entry)
            return
        if isinstance(entry, ItemV2):
            self.items.append(entry)
            for bound in entry.bound_abilities():
                if isinstance(bound, AbilityV2):
                    self._add_ability_unique(bound)
            for recipe_entry in entry.bound_recipes():
                if isinstance(recipe_entry, RecipeV2):
                    self._add_recipe_unique(recipe_entry)
            return
        if isinstance(entry, MobV2):
            self.mobs.append(entry)
            for bound in entry.bound_abilities():
                if isinstance(bound, AbilityV2):
                    self._add_ability_unique(bound)
            return
        if isinstance(entry, RecipeV2):
            self.recipes.append(entry)
            return
        if isinstance(entry, ShopV2):
            self.shops.append(entry)
            return
        if isinstance(entry, QuestV2):
            self.quests.append(entry)
            return
        if isinstance(entry, UpgradeV2):
            self.upgrades.append(entry)
            return
        if isinstance(entry, ClassV2):
            self.classes.append(entry)
            return
        raise ValueError(f"unsupported pack entry: {entry!r}")

    def _add_ability_unique(self, entry: AbilityV2) -> None:
        entry_id = entry.id or ""
        if not entry_id:
            self.abilities.append(entry)
            return
        for existing in self.abilities:
            if (existing.id or "") == entry_id:
                return
        self.abilities.append(entry)

    def _add_recipe_unique(self, entry: RecipeV2) -> None:
        entry_id = entry.id or ""
        if not entry_id:
            self.recipes.append(entry)
            return
        for existing in self.recipes:
            if (existing.id or "") == entry_id:
                return
        self.recipes.append(entry)

    def id_map(self) -> dict[str, str]:
        return self.ctx.id_map()

    def _build_abilities(self) -> list[tuple[str, dict[str, Any]]]:
        return [(entry.id or "", entry.build()) for entry in self.abilities]

    def _build_items(self) -> list[tuple[str, dict[str, Any]]]:
        return [(entry.id or "", entry.build()) for entry in self.items]

    def _build_mobs(self) -> list[tuple[str, dict[str, Any]]]:
        built: list[tuple[str, dict[str, Any]]] = []
        for entry in self.mobs:
            builder = getattr(entry, "build_document", None)
            if callable(builder):
                payload = builder()
            else:
                payload = {"mobs": {entry.id or "": entry.build()}}
            built.append((entry.id or "", payload))
        return built

    def _build_recipes(self) -> list[tuple[str, dict[str, Any]]]:
        return [(entry.id, entry.build()) for entry in self.recipes]

    def _build_shops(self) -> list[tuple[str, dict[str, Any]]]:
        return [(entry.id, entry.build()) for entry in self.shops]

    def _build_quests(self) -> list[tuple[str, dict[str, Any]]]:
        return [(entry.id, entry.build()) for entry in self.quests]

    def _build_upgrades(self) -> list[tuple[str, dict[str, Any]]]:
        return [(entry.id, entry.build()) for entry in self.upgrades]

    def _build_classes(self) -> list[tuple[str, dict[str, Any]]]:
        return [(entry.id, entry.build()) for entry in self.classes]

    def validate(self) -> ValidationReport:
        self.ctx.assert_valid()
        return validate_pack(
            [
                ("abilities", self.abilities),
                ("items", self.items),
                ("mobs", self.mobs),
                ("recipes", self.recipes),
                ("shops", self.shops),
                ("quests", self.quests),
                ("upgrades", self.upgrades),
                ("classes", self.classes),
            ]
        )

    def preview_export(self) -> dict[str, Any]:
        report = self.validate()
        return {
            "counts": {
                "abilities": len(self.abilities),
                "items": len(self.items),
                "mobs": len(self.mobs),
                "recipes": len(self.recipes),
                "shops": len(self.shops),
                "quests": len(self.quests),
                "upgrades": len(self.upgrades),
                "classes": len(self.classes),
            },
            "errors": [issue.message for issue in report.errors()],
            "warnings": [issue.message for issue in report.warnings()],
        }

    def export(self, output_dir: str) -> list[str]:
        report = self.validate()
        if report.has_errors():
            raise BuildValidationError(render_report(report))

        abilities = self._build_abilities()
        items = self._build_items()
        mobs = self._build_mobs()
        recipes = self._build_recipes()
        shops = self._build_shops()
        quests = self._build_quests()
        upgrades = self._build_upgrades()
        classes = self._build_classes()

        dirs = {
            "abilities": os.path.join(output_dir, "effects", "abilities"),
            "items": os.path.join(output_dir, "effects", "items"),
            "mobs": os.path.join(output_dir, "mobs"),
            "recipes": os.path.join(output_dir, "recipes"),
            "shops": os.path.join(output_dir, "shops"),
            "quests": os.path.join(output_dir, "quests"),
            "upgrades": os.path.join(output_dir, "upgrades"),
        }
        for path in dirs.values():
            os.makedirs(path, exist_ok=True)

        paths: list[str] = []

        for ability_id, payload in abilities:
            paths.append(write_yaml(os.path.join(dirs["abilities"], f"{ability_id}.yml"), {"abilities": {ability_id: payload}}))

        for item_id, payload in items:
            paths.append(write_yaml(os.path.join(dirs["items"], f"{item_id}.yml"), payload))

        for mob_id, payload in mobs:
            paths.append(write_yaml(os.path.join(dirs["mobs"], f"{mob_id}.yml"), payload))

        for recipe_id, payload in recipes:
            paths.append(write_yaml(os.path.join(dirs["recipes"], f"{recipe_id}.yml"), payload))

        for shop_id, payload in shops:
            paths.append(write_yaml(os.path.join(dirs["shops"], f"{shop_id}.yml"), {"shops": {shop_id: payload}}))

        for quest_id, payload in quests:
            paths.append(write_yaml(os.path.join(dirs["quests"], f"{quest_id}.yml"), {"quests": {quest_id: payload}}))

        for upgrade_id, payload in upgrades:
            paths.append(write_yaml(os.path.join(dirs["upgrades"], f"{upgrade_id}.yml"), {"upgrades": {upgrade_id: payload}}))

        classes_path = os.path.join(output_dir, "classes.yml")
        existing_root: dict[str, Any] = {}
        if os.path.exists(classes_path):
            try:
                with open(classes_path, "r", encoding="utf-8") as handle:
                    loaded = yaml.safe_load(handle)
            except Exception:
                loaded = {}
            if isinstance(loaded, dict):
                existing_root = dict(loaded)
        existing_classes = existing_root.get("classes")
        merged_classes: dict[str, Any] = dict(existing_classes) if isinstance(existing_classes, dict) else {}
        for class_id, payload in classes:
            merged_classes[class_id] = payload
        existing_root["classes"] = merged_classes
        paths.append(write_yaml(classes_path, existing_root))

        id_map_path = os.path.join(output_dir, "v2_id_map.yml")
        paths.append(write_yaml(id_map_path, {"ids": self.id_map()}))

        summary_path = os.path.join(output_dir, "v2_pack.yml")
        paths.append(
            write_yaml(
                summary_path,
                {
                    "counts": {
                        "abilities": len(abilities),
                        "items": len(items),
                        "mobs": len(mobs),
                        "recipes": len(recipes),
                        "shops": len(shops),
                        "quests": len(quests),
                        "upgrades": len(upgrades),
                        "classes": len(classes),
                    },
                    "warnings": [issue.message for issue in report.warnings()],
                    "strict": self.ctx.strict,
                    "profile": self.ctx.profile,
                },
            )
        )

        return paths


def pack_v2(ctx: BuildContext | None = None) -> PackV2:
    return PackV2(ctx=ctx or BuildContext(strict=True, profile="dev"))


__all__ = ["PackV2", "pack_v2"]
