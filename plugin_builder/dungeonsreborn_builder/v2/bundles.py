"""Cross-domain linked bundles for builder v2."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Optional

from .core import BuildContext, Ref
from .crafting import RecipeV2, recipe, recipe_ingredient
from .effects import AbilityV2, ability, fx
from .enums import (
    DamagePolicy,
    EntityType,
    EntityTypeLike,
    ForEachMode,
    Material,
    MaterialLike,
    MobAiProfile,
    MobSoundProfile,
    Particle,
    PotionEffect,
    Sound,
    TargetAnchor,
)
from .items import ItemV2, bind, item
from .mobs import MobV2, TimedAbility, mob
from .quests import QuestV2, quest
from .shops import ShopV2, shop
from .upgrades import UpgradeV2, upgrade


@dataclass
class BundleV2:
    ctx: BuildContext
    name: str
    id: Optional[str] = None
    symbol: Optional[str] = None
    abilities: list[AbilityV2] = field(default_factory=list)
    items: list[ItemV2] = field(default_factory=list)
    mobs: list[MobV2] = field(default_factory=list)
    recipes: list[RecipeV2] = field(default_factory=list)
    shops: list[ShopV2] = field(default_factory=list)
    quests: list[QuestV2] = field(default_factory=list)
    upgrades: list[UpgradeV2] = field(default_factory=list)

    def __post_init__(self) -> None:
        self.id, self.symbol = self.ctx.register(
            "bundle",
            symbol=self.symbol or self.name,
            id_override=self.id,
            parts=[self.name],
        )

    def add(self, *entries: Any) -> "BundleV2":
        for entry in entries:
            self._add_one(entry)
        return self

    def _add_one(self, entry: Any) -> None:
        if isinstance(entry, AbilityV2):
            self.abilities.append(entry)
            return
        if isinstance(entry, ItemV2):
            self.items.append(entry)
            return
        if isinstance(entry, MobV2):
            self.mobs.append(entry)
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
        if isinstance(entry, (list, tuple, set)):
            for nested in entry:
                self._add_one(nested)
            return
        raise ValueError(f"unsupported bundle entry: {entry!r}")

    def all_entries(self) -> dict[str, list[Any]]:
        return {
            "abilities": list(self.abilities),
            "items": list(self.items),
            "mobs": list(self.mobs),
            "recipes": list(self.recipes),
            "shops": list(self.shops),
            "quests": list(self.quests),
            "upgrades": list(self.upgrades),
        }


class GhostBundle(BundleV2):
    @classmethod
    def create(
        cls,
        ctx: BuildContext,
        *,
        name: str = "Restless Ghost",
        mob_type: EntityTypeLike = EntityType.ZOMBIE,
        head_texture: Optional[str] = None,
    ) -> "GhostBundle":
        base_symbol = "ghost"
        bundle = cls(ctx=ctx, name=f"{name} Bundle", symbol=f"bundle.{base_symbol}")

        hit_fx = ability(
            ctx,
            symbol=f"ability.{base_symbol}.hit_smoke",
            name=f"{name} Hit Smoke",
            action=fx.for_each_target(
                targeter=fx.target_context("mob_target"),
                mode=ForEachMode.FIRST,
                then=fx.sequence(
                    fx.sound(Sound.ENTITY_PHANTOM_BITE, volume=0.8, pitch=0.9),
                    fx.particles_sphere_shell(Particle.CLOUD, radius=1.15, points=24, at=TargetAnchor.LAST_ENTITY),
                ),
            ),
        )

        invis_fx = ability(
            ctx,
            symbol=f"ability.{base_symbol}.veil",
            name=f"{name} Veil",
            action=fx.for_each_target(
                targeter=fx.target_self(),
                then=fx.potion(PotionEffect.INVISIBILITY, duration_ticks=120, ambient=True, particles=False, icon=False),
            ),
        )

        ghost = (
            mob.create(ctx, symbol=f"mob.{base_symbol}.restless", name=name, mob_type=mob_type)
            .show_name(True)
            .stats(health=24, damage=4, armor=1, speed=0.30)
            .ai_quick(MobAiProfile.AGGRESSIVE, aggro_radius=18.0, chase_speed=0.32, open_doors=False)
            .silent(True)
            .events(
                on_hit=Ref(hit_fx.symbol or "ability.ghost.hit_smoke"),
                on_spawn_tick=TimedAbility(Ref(invis_fx.symbol or "ability.ghost.veil"), 40),
            )
            .sounds(MobSoundProfile.GHOST)
        )
        if head_texture:
            ghost.look_skin_head(head_texture)

        bundle.add(hit_fx, invis_fx, ghost)
        return bundle


class WeaponBundle(BundleV2):
    @classmethod
    def create(
        cls,
        ctx: BuildContext,
        *,
        weapon_name: str,
        material: MaterialLike = Material.IRON_SWORD,
    ) -> "WeaponBundle":
        base = weapon_name.strip() or "weapon"
        symbol_base = base.lower().replace(" ", "_")
        bundle = cls(ctx=ctx, name=f"{weapon_name} Bundle", symbol=f"bundle.{symbol_base}")

        strike = ability(
            ctx,
            symbol=f"ability.{symbol_base}.strike",
            name=f"{weapon_name} Strike",
            action=fx.damage(6.0, policy=DamagePolicy.HOSTILE_DEFAULT),
        )

        weapon = (
            item.create(ctx, symbol=f"item.{symbol_base}.core", name=weapon_name, material=material)
            .weapon_basic(attack_damage=6.0, attack_speed=1.1)
            .bind(bind.use(Ref(strike.symbol or f"ability.{symbol_base}.strike")))
        )

        recipe_entry = recipe.for_item(
            ctx,
            Ref(weapon.symbol or f"item.{symbol_base}.core"),
            pattern=[" II", " SI", "S  "],
            keys=recipe.keys()
            .slot("I", recipe_ingredient.material(Material.IRON_INGOT))
            .slot("S", recipe_ingredient.material(Material.STICK)),
            symbol=f"recipe.{symbol_base}.craft",
            name=f"Craft {weapon_name}",
        ).discovery(show_in_book=True, unlock_on_craft=True)

        bundle.add(strike, weapon, recipe_entry)
        return bundle


class ConsumableBundle(BundleV2):
    @classmethod
    def create(cls, ctx: BuildContext, *, name: str, material: MaterialLike = Material.POTION) -> "ConsumableBundle":
        base = name.strip() or "consumable"
        symbol_base = base.lower().replace(" ", "_")
        bundle = cls(ctx=ctx, name=f"{name} Bundle", symbol=f"bundle.{symbol_base}")

        consume_fx = ability(
            ctx,
            symbol=f"ability.{symbol_base}.consume",
            name=f"{name} Use",
            action=fx.sound(Sound.ENTITY_GENERIC_DRINK, volume=0.85, pitch=1.2),
        )

        consumable = (
            item.create(ctx, symbol=f"item.{symbol_base}.core", name=name, material=material)
            .consumable_basic(stack_size=1)
            .bind(bind.use(Ref(consume_fx.symbol or f"ability.{symbol_base}.consume")))
        )

        craft = recipe.for_item(
            ctx,
            Ref(consumable.symbol or f"item.{symbol_base}.core"),
            pattern=[" G ", " B ", " G "],
            keys=recipe.keys()
            .slot("G", recipe_ingredient.material(Material.GLASS_BOTTLE))
            .slot("B", recipe_ingredient.material(Material.BEETROOT)),
            symbol=f"recipe.{symbol_base}.craft",
            name=f"Craft {name}",
        ).discovery(show_in_book=True)

        bundle.add(consume_fx, consumable, craft)
        return bundle


class EliteMobBundle(BundleV2):
    @classmethod
    def create(cls, ctx: BuildContext, *, name: str, mob_type: EntityTypeLike) -> "EliteMobBundle":
        base = name.strip() or "elite"
        symbol_base = base.lower().replace(" ", "_")
        bundle = cls(ctx=ctx, name=f"{name} Bundle", symbol=f"bundle.{symbol_base}")

        elite = (
            mob.create(ctx, symbol=f"mob.{symbol_base}.elite", name=name, mob_type=mob_type)
            .show_name(True)
            .stats(health=60, damage=10, armor=8, speed=0.28)
            .ai_quick(MobAiProfile.AGGRESSIVE, aggro_radius=20.0, chase_speed=0.33, call_for_help_radius=8.0)
        )

        quest_entry = (
            quest.create(ctx, symbol=f"quest.{symbol_base}.hunt", name=f"Hunt {name}")
            .kill_mob(Ref(elite.symbol or f"mob.{symbol_base}.elite"), count=1)
            .reward_xp(150)
            .reward_tokens(20)
        )

        bundle.add(elite, quest_entry)
        return bundle


class TrialRewardBundle(BundleV2):
    @classmethod
    def create(cls, ctx: BuildContext, *, name: str) -> "TrialRewardBundle":
        base = name.strip() or "trial_reward"
        symbol_base = base.lower().replace(" ", "_")
        bundle = cls(ctx=ctx, name=f"{name} Bundle", symbol=f"bundle.{symbol_base}")

        reward_item = item.create(ctx, symbol=f"item.{symbol_base}.reward", name=name, material=Material.GOLD_INGOT)

        reward_shop = (
            shop.create(ctx, symbol=f"shop.{symbol_base}.vendor", title=f"{name} Vendor")
            .sell(Ref(reward_item.symbol or f"item.{symbol_base}.reward"), cost_tokens=25)
        )

        reward_upgrade = upgrade.create(ctx, symbol=f"upgrade.{symbol_base}.core", name=f"{name} Upgrade").for_item(
            Ref(reward_item.symbol or f"item.{symbol_base}.reward")
        )

        bundle.add(reward_item, reward_shop, reward_upgrade)
        return bundle


__all__ = [
    "BundleV2",
    "GhostBundle",
    "EliteMobBundle",
    "WeaponBundle",
    "ConsumableBundle",
    "TrialRewardBundle",
]
