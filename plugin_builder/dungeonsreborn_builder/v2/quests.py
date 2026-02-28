"""Strict typed quest declarations for builder v2."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from .core import BuildContext, Ref
from .enums import QuestObjectiveType


class QuestObjectiveSpec:
    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        raise NotImplementedError


@dataclass(frozen=True)
class KillMobObjective(QuestObjectiveSpec):
    mob: Ref | str
    count: int = 1

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        return {
            "type": QuestObjectiveType.KILL_MOB.value,
            "mobId": ctx.resolve(self.mob, domain="mob", field=f"{field}.mob"),
            "count": max(1, int(self.count)),
        }


@dataclass(frozen=True)
class CraftItemObjective(QuestObjectiveSpec):
    recipe: Ref | str
    count: int = 1

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        return {
            "type": QuestObjectiveType.CRAFT_ITEM.value,
            "recipeId": ctx.resolve(self.recipe, domain="recipe", field=f"{field}.recipe"),
            "count": max(1, int(self.count)),
        }


@dataclass(frozen=True)
class CollectItemObjective(QuestObjectiveSpec):
    item: Ref | str
    count: int = 1

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, object]:
        return {
            "type": QuestObjectiveType.COLLECT_ITEM.value,
            "itemId": ctx.resolve(self.item, domain="item", field=f"{field}.item"),
            "count": max(1, int(self.count)),
        }


class quest_objective:
    @staticmethod
    def kill_mob(mob_ref: Ref | str, *, count: int = 1) -> QuestObjectiveSpec:
        return KillMobObjective(mob=mob_ref, count=count)

    @staticmethod
    def craft_item(recipe_ref: Ref | str, *, count: int = 1) -> QuestObjectiveSpec:
        return CraftItemObjective(recipe=recipe_ref, count=count)

    @staticmethod
    def collect_item(item_ref: Ref | str, *, count: int = 1) -> QuestObjectiveSpec:
        return CollectItemObjective(item=item_ref, count=count)


class QuestV2:
    domain = "quest"

    def __init__(
        self,
        ctx: BuildContext,
        *,
        name: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> None:
        self.ctx = ctx
        self.id, self.symbol = ctx.register("quest", symbol=symbol or name, id_override=id, parts=[name])
        self._spec: dict[str, object] = {
            "name": name,
            "objectives": [],
            "rewards": {},
        }

    def objective(self, objective: QuestObjectiveSpec) -> "QuestV2":
        objectives = self._spec["objectives"]
        assert isinstance(objectives, list)
        index = len(objectives)
        objectives.append(objective.build(self.ctx, field=f"quests.{self.id}.objectives[{index}]"))
        return self

    def kill_mob(self, mob_ref: Ref | str, *, count: int = 1) -> "QuestV2":
        return self.objective(quest_objective.kill_mob(mob_ref, count=count))

    def craft_item(self, recipe_ref: Ref | str, *, count: int = 1) -> "QuestV2":
        return self.objective(quest_objective.craft_item(recipe_ref, count=count))

    def collect_item(self, item_ref: Ref | str, *, count: int = 1) -> "QuestV2":
        return self.objective(quest_objective.collect_item(item_ref, count=count))

    def reward_xp(self, amount: int) -> "QuestV2":
        rewards = self._spec.setdefault("rewards", {})
        assert isinstance(rewards, dict)
        rewards["xp"] = int(amount)
        return self

    def reward_tokens(self, amount: int) -> "QuestV2":
        rewards = self._spec.setdefault("rewards", {})
        assert isinstance(rewards, dict)
        rewards["tokens"] = int(amount)
        return self

    def reward_item(self, item_ref: Ref | str, *, amount: int = 1) -> "QuestV2":
        item_id = self.ctx.resolve(item_ref, domain="item", field=f"quests.{self.id}.reward_item")
        rewards = self._spec.setdefault("rewards", {})
        assert isinstance(rewards, dict)
        entries = rewards.setdefault("items", [])
        assert isinstance(entries, list)
        entries.append({"itemId": item_id, "amount": max(1, int(amount))})
        return self

    def require_level(self, level: int) -> "QuestV2":
        requirements = self._spec.setdefault("requirements", {})
        assert isinstance(requirements, dict)
        requirements["level"] = int(level)
        return self

    def build(self) -> dict[str, object]:
        return dict(self._spec)


class quest:
    @staticmethod
    def create(
        ctx: BuildContext,
        *,
        name: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> QuestV2:
        return QuestV2(ctx=ctx, name=name, id=id, symbol=symbol)


__all__ = [
    "QuestObjectiveSpec",
    "KillMobObjective",
    "CraftItemObjective",
    "CollectItemObjective",
    "quest_objective",
    "QuestV2",
    "quest",
]
