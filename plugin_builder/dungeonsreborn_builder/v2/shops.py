"""Strict typed shop declarations for builder v2."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from .core import BuildContext, Ref
from .enums import MaterialLike, ShopIngredientType, coerce_material


@dataclass(frozen=True)
class ShopIngredientSpec:
    ingredient_type: ShopIngredientType
    amount: int = 1
    currency: str | None = None
    item: Ref | str | None = None
    material: MaterialLike | None = None

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        if self.amount <= 0:
            raise ValueError(f"{field}.amount: must be > 0")
        payload: dict[str, object] = {
            "type": self.ingredient_type.value,
            "amount": int(self.amount),
        }
        if self.ingredient_type is ShopIngredientType.TOKEN:
            return payload
        if self.ingredient_type is ShopIngredientType.CURRENCY:
            token = (self.currency or "").strip().lower()
            if not token:
                raise ValueError(f"{field}.currency: cannot be empty")
            payload["currency"] = token
            return payload
        if self.ingredient_type is ShopIngredientType.ITEM_ID:
            if self.item is None:
                raise ValueError(f"{field}.item: is required")
            payload["itemId"] = ctx.resolve(self.item, domain="item", field=f"{field}.item")
            return payload
        if self.ingredient_type is ShopIngredientType.MATERIAL:
            if self.material is None:
                raise ValueError(f"{field}.material: is required")
            payload["material"] = coerce_material(self.material, field=f"{field}.material")
            return payload
        raise ValueError(f"{field}.type: unsupported typed ingredient {self.ingredient_type.value}")


@dataclass(frozen=True)
class ShopSellSpec:
    item: Ref | str
    amount: int = 1

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        return {
            "type": "item_id",
            "itemId": ctx.resolve(self.item, domain="item", field=f"{field}.item"),
            "amount": max(1, int(self.amount)),
        }


@dataclass(frozen=True)
class ShopTradeSpec:
    buy: list[ShopIngredientSpec]
    sell: ShopSellSpec

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        if not self.buy:
            raise ValueError(f"{field}.buy: missing ingredient")
        return {
            "buy": [entry.build(ctx, field=f"{field}.buy[{index}]") for index, entry in enumerate(self.buy)],
            "sell": self.sell.build(ctx, field=f"{field}.sell"),
        }


class shop_ingredient:
    @staticmethod
    def token(amount: int) -> ShopIngredientSpec:
        return ShopIngredientSpec(ingredient_type=ShopIngredientType.TOKEN, amount=amount)

    @staticmethod
    def currency(currency: str, amount: int) -> ShopIngredientSpec:
        return ShopIngredientSpec(
            ingredient_type=ShopIngredientType.CURRENCY,
            amount=amount,
            currency=currency,
        )

    @staticmethod
    def item(item_ref: Ref | str, amount: int = 1) -> ShopIngredientSpec:
        return ShopIngredientSpec(
            ingredient_type=ShopIngredientType.ITEM_ID,
            amount=amount,
            item=item_ref,
        )

    @staticmethod
    def material(material: MaterialLike, amount: int = 1) -> ShopIngredientSpec:
        return ShopIngredientSpec(
            ingredient_type=ShopIngredientType.MATERIAL,
            amount=amount,
            material=material,
        )


class ShopV2:
    domain = "shop"

    def __init__(
        self,
        ctx: BuildContext,
        *,
        title: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> None:
        self.ctx = ctx
        self.id, self.symbol = ctx.register("shop", symbol=symbol or title, id_override=id, parts=[title])
        self._spec: dict[str, object] = {
            "title": title,
            "trades": [],
        }

    def trade(self, trade: ShopTradeSpec) -> "ShopV2":
        payload = trade.build(self.ctx, field=f"shops.{self.id}.trades[{len(self._spec['trades'])}]")
        trades = self._spec.get("trades")
        assert isinstance(trades, list)
        trades.append(payload)
        return self

    def sell(
        self,
        item_ref: Ref | str,
        *,
        amount: int = 1,
        cost_tokens: Optional[int] = None,
        cost_gold: Optional[int] = None,
    ) -> "ShopV2":
        buys: list[ShopIngredientSpec] = []
        if cost_tokens is not None:
            buys.append(shop_ingredient.token(int(cost_tokens)))
        if cost_gold is not None:
            buys.append(shop_ingredient.currency("gold", int(cost_gold)))
        if not buys:
            raise ValueError(f"shops.{self.id}.sell: at least one cost is required (cost_tokens or cost_gold)")

        return self.trade(ShopTradeSpec(buy=buys, sell=ShopSellSpec(item=item_ref, amount=amount)))

    def build(self) -> dict[str, object]:
        return dict(self._spec)


class shop:
    @staticmethod
    def create(
        ctx: BuildContext,
        *,
        title: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> ShopV2:
        return ShopV2(ctx=ctx, title=title, id=id, symbol=symbol)


__all__ = [
    "ShopIngredientSpec",
    "ShopSellSpec",
    "ShopTradeSpec",
    "shop_ingredient",
    "ShopV2",
    "shop",
]
