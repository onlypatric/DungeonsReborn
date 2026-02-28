"""Concise crafting declarations for builder v2."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Optional, Sequence

from .core import BuildContext, Ref
from .enums import (
    CraftingMatchType,
    CraftingMatchTypeLike,
    MaterialLike,
    coerce_crafting_match_type,
    coerce_material,
)


@dataclass(frozen=True)
class RecipeIngredientSpec:
    match_type: CraftingMatchTypeLike
    value: Any = None

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, Any]:
        match = coerce_crafting_match_type(self.match_type, field=f"{field}.match_type")
        if match == CraftingMatchType.ITEM_ID.value:
            return {"itemId": ctx.resolve(self.value, domain="item", field=f"{field}.item")}
        if match == CraftingMatchType.UPGRADE_ID.value:
            return {"upgradeId": ctx.resolve(self.value, domain="upgrade", field=f"{field}.upgrade")}
        if match == CraftingMatchType.MATERIAL.value:
            return {"material": coerce_material(self.value, field=f"{field}.material")}
        if match == CraftingMatchType.TAG.value:
            token = str(self.value).strip()
            if not token:
                raise ValueError(f"{field}.tag: cannot be empty")
            return {"tag": token}
        if match == CraftingMatchType.CATEGORY.value:
            token = str(self.value).strip().lower()
            if not token:
                raise ValueError(f"{field}.category: cannot be empty")
            return {"category": token}
        if match == CraftingMatchType.ANY.value:
            return {"any": True}
        raise ValueError(f"{field}: unsupported crafting ingredient match type {match!r}")


class recipe_ingredient:
    @staticmethod
    def item(item_ref: Ref | str) -> RecipeIngredientSpec:
        return RecipeIngredientSpec(CraftingMatchType.ITEM_ID, item_ref)

    @staticmethod
    def upgrade(upgrade_ref: Ref | str) -> RecipeIngredientSpec:
        return RecipeIngredientSpec(CraftingMatchType.UPGRADE_ID, upgrade_ref)

    @staticmethod
    def material(material: MaterialLike) -> RecipeIngredientSpec:
        return RecipeIngredientSpec(CraftingMatchType.MATERIAL, material)

    @staticmethod
    def tag(tag_id: str) -> RecipeIngredientSpec:
        return RecipeIngredientSpec(CraftingMatchType.TAG, tag_id)

    @staticmethod
    def category(category_id: str) -> RecipeIngredientSpec:
        return RecipeIngredientSpec(CraftingMatchType.CATEGORY, category_id)

    @staticmethod
    def any() -> RecipeIngredientSpec:
        return RecipeIngredientSpec(CraftingMatchType.ANY, None)


@dataclass
class RecipeKeyMapSpec:
    entries: dict[str, RecipeIngredientSpec] = field(default_factory=dict)

    def slot(self, key: str, ingredient: RecipeIngredientSpec) -> "RecipeKeyMapSpec":
        token = str(key)
        if len(token) != 1:
            raise ValueError(f"recipe key must be one character: {key!r}")
        if not isinstance(ingredient, RecipeIngredientSpec):
            raise ValueError(
                f"crafting.keys[{token!r}]: expected RecipeIngredientSpec; "
                "use recipe_ingredient.material/item/tag/category/upgrade/any"
            )
        self.entries[token] = ingredient
        return self


class RecipeV2:
    domain = "recipe"

    def __init__(
        self,
        ctx: BuildContext,
        *,
        id: str,
        symbol: str,
        output_item_id: str,
        pattern: Sequence[str],
        keys: RecipeKeyMapSpec,
        name: Optional[str] = None,
    ) -> None:
        self.ctx = ctx
        self.id = id
        self.symbol = symbol
        self._spec: dict[str, Any] = {
            "name": name,
            "variants": [
                {
                    "grid": {
                        "pattern": list(pattern),
                        # Runtime loader expects `grid.key` first and logs an error if absent,
                        # even when `grid.keys` is present.
                        "key": self._resolve_keys(keys),
                    },
                    "strict": True,
                }
            ],
            "outputs": [
                {
                    "itemId": output_item_id,
                    "amount": 1,
                }
            ],
        }

    @classmethod
    def for_item(
        cls,
        ctx: BuildContext,
        item_ref: Ref | str,
        *,
        pattern: Sequence[str],
        keys: RecipeKeyMapSpec,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
        name: Optional[str] = None,
    ) -> "RecipeV2":
        output_item_id = ctx.resolve(item_ref, domain="item", field="crafting.output")
        recipe_id, recipe_symbol = ctx.register(
            "recipe",
            symbol=symbol or f"recipe.craft.{output_item_id}",
            id_override=id,
            parts=["craft", output_item_id],
        )
        return cls(
            ctx,
            id=recipe_id,
            symbol=recipe_symbol,
            output_item_id=output_item_id,
            pattern=pattern,
            keys=keys,
            name=name,
        )

    def discovery(
        self,
        *,
        show_in_book: bool = True,
        unlock_on_craft: bool = False,
        hidden: bool = False,
    ) -> "RecipeV2":
        self._spec["discovery"] = {
            "showInBook": bool(show_in_book),
            "unlockOnCraft": bool(unlock_on_craft),
            "hidden": bool(hidden),
        }
        return self

    def output_amount(self, amount: int) -> "RecipeV2":
        self._spec["outputs"][0]["amount"] = max(1, int(amount))
        return self

    def build(self) -> dict[str, Any]:
        payload = dict(self._spec)
        if payload.get("name") is None:
            payload.pop("name", None)
        return payload

    def _resolve_keys(self, keys: RecipeKeyMapSpec) -> dict[str, dict[str, Any]]:
        if not isinstance(keys, RecipeKeyMapSpec):
            raise ValueError(
                "crafting.keys: expected RecipeKeyMapSpec; use recipe.keys().slot(...).slot(...)"
            )
        resolved: dict[str, dict[str, Any]] = {}
        for key, value in keys.entries.items():
            token = str(key)
            if len(token) != 1:
                raise ValueError(f"recipe key must be one character: {key!r}")
            if not isinstance(value, RecipeIngredientSpec):
                raise ValueError(
                    f"crafting.keys[{token!r}]: expected RecipeIngredientSpec; "
                    "use recipe_ingredient.material/item/tag/category/upgrade/any"
                )
            resolved[token] = value.build(self.ctx, field=f"crafting.keys.{token}")
        return resolved


class recipe:
    @staticmethod
    def keys() -> RecipeKeyMapSpec:
        return RecipeKeyMapSpec()

    @staticmethod
    def for_item(
        ctx: BuildContext,
        item_ref: Ref | str,
        *,
        pattern: Sequence[str],
        keys: RecipeKeyMapSpec,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
        name: Optional[str] = None,
    ) -> RecipeV2:
        return RecipeV2.for_item(
            ctx,
            item_ref,
            pattern=pattern,
            keys=keys,
            id=id,
            symbol=symbol,
            name=name,
        )

__all__ = ["RecipeIngredientSpec", "RecipeKeyMapSpec", "RecipeV2", "recipe_ingredient", "recipe"]
