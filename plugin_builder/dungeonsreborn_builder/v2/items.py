"""Concise item authoring for builder v2."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping, Optional, Sequence

from .core import BuildContext, Ref
from .enums import (
    ItemClick,
    ItemUseAnimation,
    ItemUseAnimationLike,
    MaterialLike,
    PassiveSlot,
    PotionEffectLike,
    Sound,
    SoundLike,
    coerce_enum,
    coerce_item_use_animation,
    coerce_material,
    coerce_potion_effect,
    coerce_sound,
)

AbilityLike = Ref | str | Any


@dataclass(frozen=True)
class ItemBindSpec:
    click: ItemClick
    ability: AbilityLike
    cancel_event: bool = True
    period_ticks: int | None = None
    slots: list[PassiveSlot] | None = None


@dataclass(frozen=True)
class FoodSpec:
    nutrition: int
    saturation: float
    can_always_eat: bool = False

    def build(self, *, field: str) -> dict[str, Any]:
        if self.nutrition < 0:
            raise ValueError(f"{field}.nutrition: must be >= 0")
        if self.saturation < 0:
            raise ValueError(f"{field}.saturation: must be >= 0")
        return {
            "nutrition": int(self.nutrition),
            "saturation": float(self.saturation),
            "canAlwaysEat": bool(self.can_always_eat),
        }


@dataclass(frozen=True)
class ConsumeStatusEffectSpec:
    effect: PotionEffectLike
    duration_ticks: int
    amplifier: int = 0
    ambient: bool = False
    particles: bool = True
    icon: bool = True

    def build(self, *, field: str) -> dict[str, Any]:
        if self.duration_ticks <= 0:
            raise ValueError(f"{field}.duration_ticks: must be > 0")
        if self.amplifier < 0:
            raise ValueError(f"{field}.amplifier: must be >= 0")
        return {
            "effect": coerce_potion_effect(self.effect, field=f"{field}.effect"),
            "durationTicks": int(self.duration_ticks),
            "amplifier": int(self.amplifier),
            "ambient": bool(self.ambient),
            "particles": bool(self.particles),
            "icon": bool(self.icon),
        }


@dataclass(frozen=True)
class ConsumeEffectSpec:
    effect_type: str
    params: dict[str, Any] = field(default_factory=dict)
    raw: bool = False

    def __post_init__(self) -> None:
        token = str(self.effect_type).strip().upper()
        if not token:
            raise ValueError("items.consume.effects.type: cannot be empty")
        if not self.raw and token not in {
            "PLAY_SOUND",
            "TELEPORT_RANDOMLY",
            "REMOVE_STATUS_EFFECTS",
            "CLEAR_ALL_STATUS_EFFECTS",
            "APPLY_STATUS_EFFECTS",
        }:
            raise ValueError(
                f"items.consume.effects.type: unsupported typed consume effect {self.effect_type!r}; "
                "use consume_fx.raw(...) for custom effect types"
            )
        object.__setattr__(self, "effect_type", token)

    def build(self) -> dict[str, Any]:
        return {"type": self.effect_type, **self.params}


@dataclass(frozen=True)
class ConsumableSpec:
    consume_seconds: float | None = None
    animation: ItemUseAnimationLike | None = None
    sound: SoundLike | None = None
    has_particles: bool | None = None
    effects: Sequence[ConsumeEffectSpec] | None = None

    def build(self, *, field: str) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.consume_seconds is not None:
            if self.consume_seconds < 0:
                raise ValueError(f"{field}.consume_seconds: must be >= 0")
            payload["consumeSeconds"] = float(self.consume_seconds)
        if self.animation is not None:
            payload["animation"] = coerce_item_use_animation(self.animation, field=f"{field}.animation")
        if self.sound is not None:
            payload["sound"] = coerce_sound(self.sound, field=f"{field}.sound")
        if self.has_particles is not None:
            payload["hasConsumeParticles"] = bool(self.has_particles)
        if self.effects is not None:
            payload["effects"] = [entry.build() for entry in self.effects]
        return payload


@dataclass(frozen=True)
class UseCooldownSpec:
    seconds: float
    group: str | None = None

    def build(self, *, field: str) -> dict[str, Any]:
        if self.seconds <= 0:
            raise ValueError(f"{field}.seconds: must be > 0")
        payload: dict[str, Any] = {"seconds": float(self.seconds)}
        if self.group is not None:
            token = self.group.strip()
            if not token:
                raise ValueError(f"{field}.group: cannot be empty")
            payload["group"] = token
        return payload


@dataclass(frozen=True)
class UseRemainderSpec:
    material: MaterialLike
    amount: int = 1

    def build(self, *, field: str) -> dict[str, Any]:
        if self.amount <= 0:
            raise ValueError(f"{field}.amount: must be >= 1")
        return {
            "material": coerce_material(self.material, field=f"{field}.material"),
            "amount": int(self.amount),
        }


@dataclass(frozen=True)
class MetaSpec:
    preset: str
    params: dict[str, Any] = field(default_factory=dict)


class consume_status:
    @staticmethod
    def effect(
        effect: PotionEffectLike,
        *,
        duration_ticks: int,
        amplifier: int = 0,
        ambient: bool = False,
        particles: bool = True,
        icon: bool = True,
    ) -> ConsumeStatusEffectSpec:
        return ConsumeStatusEffectSpec(
            effect=effect,
            duration_ticks=duration_ticks,
            amplifier=amplifier,
            ambient=ambient,
            particles=particles,
            icon=icon,
        )


class consume_fx:
    @staticmethod
    def play_sound(sound: SoundLike | str) -> ConsumeEffectSpec:
        return ConsumeEffectSpec(
            "PLAY_SOUND",
            {"sound": coerce_sound(sound, field="items.consume_fx.sound")},
        )

    @staticmethod
    def teleport_randomly(*, diameter: float) -> ConsumeEffectSpec:
        if diameter < 0:
            raise ValueError("items.consume_fx.teleport_randomly.diameter: must be >= 0")
        return ConsumeEffectSpec("TELEPORT_RANDOMLY", {"diameter": float(diameter)})

    @staticmethod
    def remove_status_effects(*effects: PotionEffectLike | str) -> ConsumeEffectSpec:
        resolved = [
            coerce_potion_effect(entry, field="items.consume_fx.remove_status_effects.effects")
            for entry in effects
        ]
        if not resolved:
            raise ValueError("items.consume_fx.remove_status_effects.effects: expected at least one effect")
        return ConsumeEffectSpec("REMOVE_STATUS_EFFECTS", {"effects": resolved})

    @staticmethod
    def clear_all_status_effects() -> ConsumeEffectSpec:
        return ConsumeEffectSpec("CLEAR_ALL_STATUS_EFFECTS")

    @staticmethod
    def apply_status_effects(
        effects: Sequence[ConsumeStatusEffectSpec],
        *,
        probability: float = 1.0,
    ) -> ConsumeEffectSpec:
        if probability < 0.0 or probability > 1.0:
            raise ValueError("items.consume_fx.apply_status_effects.probability: must be between 0 and 1")
        built = [
            entry.build(field=f"items.consume_fx.apply_status_effects.effects[{index}]")
            for index, entry in enumerate(effects)
        ]
        if not built:
            raise ValueError("items.consume_fx.apply_status_effects.effects: expected at least one status effect")
        return ConsumeEffectSpec(
            "APPLY_STATUS_EFFECTS",
            {"probability": float(probability), "effects": built},
        )

    @staticmethod
    def raw(effect_type: str, **params: Any) -> ConsumeEffectSpec:
        return ConsumeEffectSpec(effect_type, dict(params), raw=True)


class bind:
    @staticmethod
    def use(
        ability: AbilityLike,
        *,
        cancel_event: bool = True,
        click: ItemClick = ItemClick.RIGHT_CLICK,
    ) -> ItemBindSpec:
        return ItemBindSpec(
            click=click,
            ability=ability,
            cancel_event=bool(cancel_event),
        )

    @staticmethod
    def passive(
        ability: AbilityLike,
        *,
        period_ticks: int = 20,
        slots: Optional[list[PassiveSlot]] = None,
    ) -> ItemBindSpec:
        return ItemBindSpec(
            click=ItemClick.PASSIVE,
            ability=ability,
            cancel_event=True,
            period_ticks=max(1, int(period_ticks)),
            slots=list(slots) if slots else None,
        )


MetaPreset = MetaSpec


class meta:
    @staticmethod
    def weapon_basic(*, attack_damage: float = 5.0, attack_speed: float = 1.0) -> MetaSpec:
        return MetaSpec("weapon_basic", {"attack_damage": float(attack_damage), "attack_speed": float(attack_speed)})

    @staticmethod
    def consumable_basic(*, stack_size: int = 1) -> MetaSpec:
        return MetaSpec("consumable_basic", {"stack_size": max(1, int(stack_size))})


class ItemV2:
    domain = "item"

    _DRINK_MATERIALS = {
        "POTION",
        "SPLASH_POTION",
        "LINGERING_POTION",
        "HONEY_BOTTLE",
        "MILK_BUCKET",
    }

    def __init__(
        self,
        ctx: BuildContext,
        *,
        name: str,
        material: MaterialLike,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> None:
        self.ctx = ctx
        self.id, self.symbol = ctx.register("item", symbol=symbol or name, id_override=id, parts=[name])
        self._item: dict[str, Any] = {
            "type": "material",
            "material": coerce_material(material, field=f"items.{self.id}.material"),
            "display": {"name": name},
        }
        self._stats: dict[str, Any] = {}
        self._bindings: list[dict[str, Any]] = []
        self._bound_abilities: list[Any] = []
        self._bound_recipes: list[Any] = []

    def _meta_section(self) -> dict[str, Any]:
        return self._item.setdefault("meta", {})

    def _components_section(self) -> dict[str, Any]:
        return self._meta_section().setdefault("components", {})

    def _food_component(self) -> dict[str, Any]:
        return self._components_section().setdefault("food", {})

    def _consumable_component(self) -> dict[str, Any]:
        return self._components_section().setdefault("consumable", {})

    def lore(self, *lines: str) -> "ItemV2":
        if lines:
            self._item.setdefault("display", {})["lore"] = list(lines)
        return self

    def visual(self, *, texture: Optional[str] = None, model_key: Optional[str] = None, apply: Optional[bool] = None) -> "ItemV2":
        visual = self._item.setdefault("visual", {})
        if texture is not None:
            visual["texture"] = texture
        if model_key is not None:
            visual["modelKey"] = model_key
        if apply is not None:
            visual["apply"] = bool(apply)
        return self

    def head_texture(self, texture: str) -> "ItemV2":
        if self._item.get("material") != "PLAYER_HEAD":
            raise ValueError(f"items.{self.id}.head_texture: material must be PLAYER_HEAD")
        value = texture.strip()
        if not value:
            raise ValueError(f"items.{self.id}.head_texture: texture cannot be empty")
        skull = self._meta_section().setdefault("skull", {})
        skull["texture"] = value
        return self

    def bind(self, *bindings: ItemBindSpec) -> "ItemV2":
        for entry in bindings:
            self._track_bound_ability(entry.ability)
            ability_id = self.ctx.resolve(entry.ability, domain="ability", field=f"items.{self.id}.bindings")
            payload: dict[str, Any] = {
                "click": coerce_enum(entry.click, ItemClick, field=f"items.{self.id}.bindings.click"),
                "ability": ability_id,
                "cancelEvent": bool(entry.cancel_event),
            }
            if entry.period_ticks is not None:
                payload["periodTicks"] = max(1, int(entry.period_ticks))
            if entry.slots:
                payload["slots"] = [
                    coerce_enum(slot, PassiveSlot, field=f"items.{self.id}.bindings.slots")
                    for slot in entry.slots
                ]
            self._bindings.append(payload)
        return self

    def _track_bound_ability(self, ability: AbilityLike) -> None:
        # Inline ability object support: capture it for pack auto-export.
        if not hasattr(ability, "id") or not hasattr(ability, "build"):
            return
        ability_id = getattr(ability, "id", None)
        if not isinstance(ability_id, str) or not ability_id:
            return
        for existing in self._bound_abilities:
            if getattr(existing, "id", None) == ability_id:
                return
        self._bound_abilities.append(ability)

    def bound_abilities(self) -> list[Any]:
        return list(self._bound_abilities)

    def _track_bound_recipe(self, recipe_obj: Any) -> None:
        if not hasattr(recipe_obj, "id") or not hasattr(recipe_obj, "build"):
            return
        recipe_id = getattr(recipe_obj, "id", None)
        if not isinstance(recipe_id, str) or not recipe_id:
            return
        for existing in self._bound_recipes:
            if getattr(existing, "id", None) == recipe_id:
                return
        self._bound_recipes.append(recipe_obj)

    def bound_recipes(self) -> list[Any]:
        return list(self._bound_recipes)

    def bind_use(
        self,
        ability: AbilityLike,
        *,
        cancel_event: bool = True,
        click: ItemClick = ItemClick.RIGHT_CLICK,
    ) -> "ItemV2":
        return self.bind(bind.use(ability, cancel_event=cancel_event, click=click))

    def recipe(
        self,
        *,
        pattern: Sequence[str],
        keys: Any,
        id: str | None = None,
        symbol: str | None = None,
        name: str | None = None,
        output_amount: int = 1,
        show_in_book: bool = True,
        unlock_on_craft: bool = False,
        hidden: bool = False,
    ) -> "ItemV2":
        from .crafting import RecipeKeyMapSpec, recipe

        if not isinstance(keys, RecipeKeyMapSpec):
            raise ValueError(
                f"items.{self.id}.recipe.keys: expected RecipeKeyMapSpec; use recipe.keys().slot(...).slot(...)"
            )
        recipe_obj = recipe.for_item(
            self.ctx,
            Ref(self.symbol),
            pattern=pattern,
            keys=keys,
            id=id,
            symbol=symbol,
            name=name,
        ).output_amount(output_amount).discovery(
            show_in_book=show_in_book,
            unlock_on_craft=unlock_on_craft,
            hidden=hidden,
        )
        self._track_bound_recipe(recipe_obj)
        return self

    def food(self, *, nutrition: int, saturation: float, can_always_eat: bool = False) -> "ItemV2":
        return self.food_spec(FoodSpec(nutrition=nutrition, saturation=saturation, can_always_eat=can_always_eat))

    def food_spec(self, spec: FoodSpec) -> "ItemV2":
        component = self._food_component()
        component.update(spec.build(field=f"items.{self.id}.food"))
        return self

    def consumable(
        self,
        *,
        consume_seconds: float | None = None,
        animation: ItemUseAnimationLike | None = None,
        sound: SoundLike | None = None,
        has_particles: bool | None = None,
        effects: Sequence[ConsumeEffectSpec] | None = None,
    ) -> "ItemV2":
        spec = ConsumableSpec(
            consume_seconds=consume_seconds,
            animation=animation,
            sound=sound,
            has_particles=has_particles,
            effects=effects,
        )
        return self.consumable_spec(spec)

    def consumable_spec(self, spec: ConsumableSpec) -> "ItemV2":
        component = self._consumable_component()
        component.update(spec.build(field=f"items.{self.id}.consumable"))
        return self

    def use_cooldown(self, *, seconds: float, group: str | None = None) -> "ItemV2":
        return self.use_cooldown_spec(UseCooldownSpec(seconds=seconds, group=group))

    def use_cooldown_spec(self, spec: UseCooldownSpec) -> "ItemV2":
        self._components_section()["use_cooldown"] = spec.build(field=f"items.{self.id}.use_cooldown")
        return self

    def use_remainder(self, material: MaterialLike, *, amount: int = 1) -> "ItemV2":
        return self.use_remainder_spec(UseRemainderSpec(material=material, amount=amount))

    def use_remainder_spec(self, spec: UseRemainderSpec) -> "ItemV2":
        self._components_section()["use_remainder"] = spec.build(field=f"items.{self.id}.use_remainder")
        return self

    def edible(
        self,
        *,
        nutrition: int,
        saturation: float,
        can_always_eat: bool = False,
        consume_seconds: float = 1.6,
        animation: ItemUseAnimationLike = ItemUseAnimation.EAT,
        sound: SoundLike = Sound.ENTITY_GENERIC_EAT,
        has_particles: bool = True,
        effects: Sequence[ConsumeEffectSpec] | None = None,
        cooldown_seconds: float | None = None,
        cooldown_group: str | None = None,
        remainder_material: MaterialLike | None = None,
        remainder_amount: int = 1,
    ) -> "ItemV2":
        self.food(nutrition=nutrition, saturation=saturation, can_always_eat=can_always_eat)
        self.consumable(
            consume_seconds=consume_seconds,
            animation=animation,
            sound=sound,
            has_particles=has_particles,
            effects=effects,
        )
        if cooldown_seconds is not None:
            self.use_cooldown(seconds=cooldown_seconds, group=cooldown_group)
        if remainder_material is not None:
            self.use_remainder(remainder_material, amount=remainder_amount)
        return self

    def meta_preset(self, preset: MetaSpec) -> "ItemV2":
        return self.meta_spec(preset)

    def meta_spec(self, spec: MetaSpec) -> "ItemV2":
        if spec.preset == "weapon_basic":
            # Builder presets should never modify the matcher shape under `item`.
            self._stats["attackDamage"] = float(spec.params.get("attack_damage", 5.0))
            self._stats["attackSpeed"] = float(spec.params.get("attack_speed", 1.0))
            return self
        if spec.preset == "consumable_basic":
            self._item["amount"] = max(1, int(spec.params.get("stack_size", 1)))
            default_animation, default_sound = self._default_consumable_profile()
            self.edible(
                nutrition=1,
                saturation=0.1,
                can_always_eat=True,
                consume_seconds=1.6,
                animation=default_animation,
                sound=default_sound,
                has_particles=True,
            )
            return self
        raise ValueError(f"unknown meta preset: {spec.preset}")

    def _default_consumable_profile(self) -> tuple[ItemUseAnimation, Sound]:
        material = str(self._item.get("material", "")).strip().upper()
        if material in self._DRINK_MATERIALS:
            return ItemUseAnimation.DRINK, Sound.ENTITY_GENERIC_DRINK
        return ItemUseAnimation.EAT, Sound.ENTITY_GENERIC_EAT

    def weapon_basic(self, *, attack_damage: float = 5.0, attack_speed: float = 1.0) -> "ItemV2":
        return self.meta_preset(meta.weapon_basic(attack_damage=attack_damage, attack_speed=attack_speed))

    def consumable_basic(self, *, stack_size: int = 1) -> "ItemV2":
        return self.meta_preset(meta.consumable_basic(stack_size=stack_size))

    def build(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "item": dict(self._item),
            "bindings": list(self._bindings),
        }
        if self._stats:
            payload["stats"] = dict(self._stats)
        return payload


class item:
    @staticmethod
    def create(
        ctx: BuildContext,
        *,
        name: str,
        material: MaterialLike,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
        binds: Optional[list[ItemBindSpec]] = None,
    ) -> ItemV2:
        obj = ItemV2(ctx=ctx, name=name, material=material, id=id, symbol=symbol)
        if binds:
            obj.bind(*binds)
        return obj


__all__ = [
    "FoodSpec",
    "ConsumableSpec",
    "UseCooldownSpec",
    "UseRemainderSpec",
    "MetaSpec",
    "ConsumeEffectSpec",
    "ConsumeStatusEffectSpec",
    "ItemV2",
    "ItemBindSpec",
    "MetaPreset",
    "bind",
    "consume_fx",
    "consume_status",
    "item",
    "meta",
]
