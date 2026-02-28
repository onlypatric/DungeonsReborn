"""Typed classes domain for builder v2."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import Any, Optional

from .core import BuildContext, Ref
from .enums import (
    AttributeOperation,
    AttributeOperationLike,
    ClassAbilityTrigger,
    ClassAbilityTriggerLike,
    ClassNodeType,
    ClassNodeTypeLike,
    ClassScalingCurve,
    ClassScalingCurveLike,
    ClassScalingMode,
    ClassScalingModeLike,
    DamageTypeLike,
    Material,
    MaterialLike,
    PotionEffectLike,
    coerce_attribute_operation,
    coerce_class_ability_trigger,
    coerce_class_node_type,
    coerce_class_scaling_curve,
    coerce_class_scaling_mode,
    coerce_damage_type,
    coerce_material,
    coerce_potion_effect,
)
from .internal.normalize import snake_case


def _deep_copy_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): _deep_copy_value(entry) for key, entry in value.items()}
    if isinstance(value, list):
        return [_deep_copy_value(entry) for entry in value]
    if isinstance(value, tuple):
        return [_deep_copy_value(entry) for entry in value]
    return value


def _coerce_scaling_mode(value: ClassScalingModeLike | None, *, field: str) -> str:
    if value is None:
        return ClassScalingMode.FLAT.value
    return coerce_class_scaling_mode(value, field=field)


def _coerce_curve(value: ClassScalingCurveLike | None, *, field: str) -> str:
    if value is None:
        return ClassScalingCurve.LINEAR.value
    return coerce_class_scaling_curve(value, field=field)


@dataclass(frozen=True)
class ClassUnlockCurrencyV2:
    currency: str
    amount: int = 1

    def build(self, *, field: str) -> dict[str, Any]:
        currency_id = snake_case(self.currency)
        if not currency_id:
            raise ValueError(f"{field}.currency: currency id is required")
        return {
            "currency": currency_id,
            "amount": max(1, int(self.amount)),
        }


@dataclass(frozen=True)
class ClassItemMatcherSpec:
    item: Ref | str | None = None
    material: MaterialLike | None = None
    custom_model_data: int | None = None
    tag: str | None = None
    category: str | None = None
    lore_contains: str | None = None

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, Any]:
        if self.item is not None:
            return {
                "itemId": ctx.resolve(self.item, domain="item", field=f"{field}.item", allow_external=True)
            }
        if self.material is not None:
            return {
                "material": coerce_material(self.material, field=f"{field}.material")
            }
        if self.custom_model_data is not None:
            return {"customModelData": int(self.custom_model_data)}
        if self.tag:
            return {"tag": self.tag.strip()}
        if self.category:
            return {"category": snake_case(self.category)}
        if self.lore_contains:
            return {"loreContains": self.lore_contains}
        raise ValueError(f"{field}: expected at least one item matcher field")


@dataclass(frozen=True)
class ClassUnlockItemV2:
    amount: int = 1
    label: str | None = None
    matcher: ClassItemMatcherSpec = field(default_factory=ClassItemMatcherSpec)

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "amount": max(1, int(self.amount)),
        }
        if self.label:
            payload["label"] = self.label

        payload.update(self.matcher.build(ctx, field=f"{field}.matcher"))
        return payload


@dataclass
class ClassUnlockV2:
    level: int = 0
    tokens: int = 0
    quests: list[Ref | str] = field(default_factory=list)
    items: list[ClassUnlockItemV2 | str] = field(default_factory=list)
    currencies: list[ClassUnlockCurrencyV2 | str] = field(default_factory=list)

    def add_quests(self, *entries: Ref | str) -> "ClassUnlockV2":
        self.quests.extend(entries)
        return self

    def add_items(self, *entries: ClassUnlockItemV2 | str) -> "ClassUnlockV2":
        self.items.extend(entries)
        return self

    def add_currencies(self, *entries: ClassUnlockCurrencyV2 | str) -> "ClassUnlockV2":
        self.currencies.extend(entries)
        return self

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.level > 0:
            payload["level"] = int(self.level)
        if self.tokens > 0:
            payload["tokens"] = int(self.tokens)

        if self.quests:
            payload["quests"] = [
                ctx.resolve(entry, domain="quest", field=f"{field}.quests", allow_external=True)
                for entry in self.quests
            ]

        built_items: list[dict[str, Any]] = []
        for index, entry in enumerate(self.items):
            item_field = f"{field}.items[{index}]"
            if isinstance(entry, ClassUnlockItemV2):
                built_items.append(entry.build(ctx, field=item_field))
                continue
            if isinstance(entry, str):
                built_items.append(
                    ClassUnlockItemV2(matcher=ClassItemMatcherSpec(item=entry)).build(ctx, field=item_field)
                )
                continue
            raise ValueError(f"{item_field}: unsupported unlock item type {type(entry).__name__}")
        if built_items:
            payload["items"] = built_items

        built_currencies: list[dict[str, Any]] = []
        for index, entry in enumerate(self.currencies):
            currency_field = f"{field}.currencies[{index}]"
            if isinstance(entry, ClassUnlockCurrencyV2):
                built_currencies.append(entry.build(field=currency_field))
                continue
            if isinstance(entry, str):
                built_currencies.append(ClassUnlockCurrencyV2(entry).build(field=currency_field))
                continue
            raise ValueError(f"{currency_field}: unsupported unlock currency type {type(entry).__name__}")
        if built_currencies:
            payload["currencies"] = built_currencies

        return payload


@dataclass
class ClassBonusV2:
    strength: int = 0
    dexterity: int = 0
    intelligence: int = 0
    vitality: int = 0
    mana_resource: str | None = None
    mana_max: float = 0.0
    mana_regen: float = 0.0
    attributes: list[dict[str, Any]] = field(default_factory=list)
    potions: list[dict[str, Any]] = field(default_factory=list)
    resistances: dict[str, float] = field(default_factory=dict)
    attribute_caps: dict[str, float] = field(default_factory=dict)

    def stats(
        self,
        *,
        strength: Optional[int] = None,
        dexterity: Optional[int] = None,
        intelligence: Optional[int] = None,
        vitality: Optional[int] = None,
    ) -> "ClassBonusV2":
        if strength is not None:
            self.strength = max(0, int(strength))
        if dexterity is not None:
            self.dexterity = max(0, int(dexterity))
        if intelligence is not None:
            self.intelligence = max(0, int(intelligence))
        if vitality is not None:
            self.vitality = max(0, int(vitality))
        return self

    def mana(self, *, resource: str | None = None, max: Optional[float] = None, regen: Optional[float] = None) -> "ClassBonusV2":
        if resource is not None:
            token = resource.strip().lower()
            if not token:
                raise ValueError("class.bonuses.mana.resource: cannot be empty")
            self.mana_resource = token
        if max is not None:
            self.mana_max = float(max)
        if regen is not None:
            self.mana_regen = float(regen)
        return self

    def attribute(
        self,
        attribute: str,
        amount: float,
        *,
        operation: AttributeOperationLike = AttributeOperation.ADD_NUMBER,
    ) -> "ClassBonusV2":
        token = attribute.strip()
        if not token:
            raise ValueError("class.bonuses.attributes.attribute: cannot be empty")
        self.attributes.append(
            {
                "attribute": token,
                "amount": float(amount),
                "operation": coerce_attribute_operation(operation, field="class.bonuses.attributes.operation"),
            }
        )
        return self

    def potion(
        self,
        effect: PotionEffectLike,
        *,
        amplifier: int = 0,
        ambient: bool = True,
        particles: bool = True,
        icon: bool = True,
    ) -> "ClassBonusV2":
        token = coerce_potion_effect(effect, field="class.bonuses.potions.effect")
        self.potions.append(
            {
                "effect": token,
                "amplifier": int(amplifier),
                "ambient": bool(ambient),
                "particles": bool(particles),
                "icon": bool(icon),
            }
        )
        return self

    def resistance(self, damage_type: DamageTypeLike, multiplier: float) -> "ClassBonusV2":
        token = coerce_damage_type(damage_type, field="class.bonuses.resistances")
        self.resistances[token] = float(multiplier)
        return self

    def attribute_cap(self, attribute: str, limit: float) -> "ClassBonusV2":
        token = attribute.strip()
        if not token:
            raise ValueError("class.bonuses.attribute_caps.attribute: cannot be empty")
        self.attribute_caps[token] = float(limit)
        return self

    def build(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if any([self.strength, self.dexterity, self.intelligence, self.vitality]):
            payload["stats"] = {
                "strength": int(self.strength),
                "dexterity": int(self.dexterity),
                "intelligence": int(self.intelligence),
                "vitality": int(self.vitality),
            }
        if self.mana_resource is not None or self.mana_max != 0.0 or self.mana_regen != 0.0:
            mana_payload: dict[str, Any] = {}
            if self.mana_resource is not None:
                mana_payload["resource"] = self.mana_resource
            if self.mana_max != 0.0:
                mana_payload["max"] = float(self.mana_max)
            if self.mana_regen != 0.0:
                mana_payload["regen"] = float(self.mana_regen)
            payload["mana"] = mana_payload
        if self.attributes:
            payload["attributes"] = [_deep_copy_value(entry) for entry in self.attributes]
        if self.potions:
            payload["potions"] = [_deep_copy_value(entry) for entry in self.potions]
        if self.resistances:
            payload["resistances"] = dict(self.resistances)
        if self.attribute_caps:
            payload["caps"] = {"attributes": dict(self.attribute_caps)}
        return payload


@dataclass
class ClassSynergyV2:
    id: str
    requires: list[str]
    bonuses: ClassBonusV2

    def build(self) -> dict[str, Any]:
        synergy_id = snake_case(self.id)
        if not synergy_id:
            raise ValueError("class.synergy.id: cannot be empty")
        requires = [snake_case(entry) for entry in self.requires if snake_case(entry)]
        if not requires:
            raise ValueError(f"class.synergy.{synergy_id}.requires: expected at least one node id")
        bonus_payload = self.bonuses.build()
        if not bonus_payload:
            raise ValueError(f"class.synergy.{synergy_id}.bonuses: expected at least one bonus")
        return {
            "id": synergy_id,
            "requires": requires,
            "bonuses": bonus_payload,
        }


@dataclass(frozen=True)
class ClassRegionSpec:
    world: str
    x: float
    y: float
    z: float
    radius: float

    def build(self, *, field: str) -> dict[str, Any]:
        world = self.world.strip()
        if not world:
            raise ValueError(f"{field}.world: is required")
        if self.radius <= 0.0:
            raise ValueError(f"{field}.radius: must be > 0")
        return {
            "world": world,
            "x": float(self.x),
            "y": float(self.y),
            "z": float(self.z),
            "radius": float(self.radius),
        }


@dataclass
class ClassConditionalBonusV2:
    worlds: list[str] = field(default_factory=list)
    regions: list[ClassRegionSpec] = field(default_factory=list)
    bonuses: ClassBonusV2 = field(default_factory=ClassBonusV2)

    def build(self, *, field: str) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        world_tokens = [entry.strip() for entry in self.worlds if entry and entry.strip()]
        if world_tokens:
            payload["worlds"] = world_tokens

        region_entries: list[dict[str, Any]] = []
        for index, region in enumerate(self.regions):
            region_entries.append(region.build(field=f"{field}.regions[{index}]"))
        if region_entries:
            payload["regions"] = region_entries

        if not world_tokens and not region_entries:
            raise ValueError(f"{field}: expected worlds and/or regions")

        bonuses_payload = self.bonuses.build()
        if not bonuses_payload:
            raise ValueError(f"{field}.bonuses: expected at least one bonus")
        payload["bonuses"] = bonuses_payload
        return payload


@dataclass
class ClassNodeV2:
    id: str
    name: str
    node_type: ClassNodeTypeLike = ClassNodeType.CUSTOM
    description_lines: list[str] = field(default_factory=list)
    cost: int = 1
    max_rank: int = 1
    requires: list[str] = field(default_factory=list)
    icon_material: MaterialLike | str = Material.PAPER
    name_key: str | None = None
    description_key: str | None = None
    stat: dict[str, Any] | None = None
    attribute: dict[str, Any] | None = None
    potion: dict[str, Any] | None = None
    ability: dict[str, Any] | None = None

    def set_stat(
        self,
        key: str,
        amount: float,
        *,
        scaling: ClassScalingModeLike | None = None,
        curve: ClassScalingCurveLike | None = None,
        curve_scale: float = 1.0,
        curve_offset: float = 0.0,
    ) -> "ClassNodeV2":
        token = key.strip()
        if not token:
            raise ValueError("class.node.stat.key: cannot be empty")
        self.node_type = ClassNodeType.STAT
        self.stat = {
            "key": token,
            "amount": float(amount),
            "scaling": _coerce_scaling_mode(scaling, field="class.node.stat.scaling"),
            "curve": _coerce_curve(curve, field="class.node.stat.curve"),
            "curveScale": float(curve_scale),
            "curveOffset": float(curve_offset),
        }
        return self

    def set_attribute(
        self,
        attribute: str,
        amount: float,
        *,
        operation: AttributeOperationLike = AttributeOperation.ADD_NUMBER,
        scaling: ClassScalingModeLike | None = None,
        curve: ClassScalingCurveLike | None = None,
        curve_scale: float = 1.0,
        curve_offset: float = 0.0,
    ) -> "ClassNodeV2":
        token = attribute.strip()
        if not token:
            raise ValueError("class.node.attribute.attribute: cannot be empty")
        self.node_type = ClassNodeType.ATTRIBUTE
        self.attribute = {
            "attribute": token,
            "amount": float(amount),
            "operation": coerce_attribute_operation(operation, field="class.node.attribute.operation"),
            "scaling": _coerce_scaling_mode(scaling, field="class.node.attribute.scaling"),
            "curve": _coerce_curve(curve, field="class.node.attribute.curve"),
            "curveScale": float(curve_scale),
            "curveOffset": float(curve_offset),
        }
        return self

    def set_potion(
        self,
        effect: PotionEffectLike,
        *,
        amplifier: int = 0,
        ambient: bool = True,
        particles: bool = True,
    ) -> "ClassNodeV2":
        token = coerce_potion_effect(effect, field="class.node.potion.effect")
        self.node_type = ClassNodeType.POTION
        self.potion = {
            "effect": token,
            "amplifier": int(amplifier),
            "ambient": bool(ambient),
            "particles": bool(particles),
        }
        return self

    def set_ability(
        self,
        ability: Ref | str,
        *,
        trigger: ClassAbilityTriggerLike = ClassAbilityTrigger.PASSIVE,
        require_sneaking: bool = False,
        permission: str | None = None,
        period_ticks: int = 20,
        cancel_event: bool = True,
    ) -> "ClassNodeV2":
        self.node_type = ClassNodeType.CUSTOM
        self.ability = {
            "id": ability,
            "trigger": coerce_class_ability_trigger(trigger, field="class.node.ability.trigger"),
            "requireSneaking": bool(require_sneaking),
            "periodTicks": max(1, int(period_ticks)),
            "cancelEvent": bool(cancel_event),
        }
        if permission:
            self.ability["permission"] = permission
        return self

    def build(self, ctx: BuildContext, *, class_id: str, index: int) -> dict[str, Any]:
        node_id = snake_case(self.id)
        if not node_id:
            raise ValueError(f"classes.{class_id}.path.nodes[{index}].id: cannot be empty")
        payload: dict[str, Any] = {
            "id": node_id,
            "name": self.name,
            "type": coerce_class_node_type(self.node_type, field=f"classes.{class_id}.path.nodes[{index}].type"),
            "cost": max(0, int(self.cost)),
            "maxRank": max(1, int(self.max_rank)),
            "material": coerce_material(self.icon_material, field=f"classes.{class_id}.path.nodes[{index}].material"),
        }
        if self.description_lines:
            payload["description"] = list(self.description_lines)
        if self.name_key:
            payload["nameKey"] = self.name_key
        if self.description_key:
            payload["descriptionKey"] = self.description_key

        requires = [snake_case(entry) for entry in self.requires if snake_case(entry)]
        if requires:
            payload["requires"] = requires

        if self.stat is not None:
            payload["stat"] = _deep_copy_value(self.stat)
        if self.attribute is not None:
            payload["attribute"] = _deep_copy_value(self.attribute)
        if self.potion is not None:
            payload["potion"] = _deep_copy_value(self.potion)
        if self.ability is not None:
            ability_payload = _deep_copy_value(self.ability)
            ability_payload["id"] = ctx.resolve(
                ability_payload["id"],
                domain="ability",
                field=f"classes.{class_id}.path.nodes[{index}].ability.id",
                allow_external=True,
            )
            payload["ability"] = ability_payload

        return payload


@dataclass
class ClassSkillTreeV2:
    nodes: list[ClassNodeV2] = field(default_factory=list)
    edges: list[tuple[str, str]] = field(default_factory=list)
    synergies: list[ClassSynergyV2] = field(default_factory=list)
    respec_tokens: int = 0
    respec_points: int = 0

    def build(self, ctx: BuildContext, *, class_id: str) -> dict[str, Any]:
        payload: dict[str, Any] = {}

        if self.nodes:
            built_nodes = [
                node.build(ctx, class_id=class_id, index=index)
                for index, node in enumerate(self.nodes)
            ]
            node_ids = {entry["id"] for entry in built_nodes}
            if len(node_ids) != len(built_nodes):
                raise ValueError(f"classes.{class_id}.path.nodes: duplicate node id detected")
            payload["nodes"] = built_nodes

            built_edges: list[dict[str, str]] = []
            for index, (src, dst) in enumerate(self.edges):
                src_id = snake_case(src)
                dst_id = snake_case(dst)
                if not src_id or not dst_id:
                    raise ValueError(f"classes.{class_id}.path.edges[{index}]: from/to are required")
                built_edges.append({"from": src_id, "to": dst_id})
            if built_edges:
                payload["edges"] = built_edges

            built_synergies = [entry.build() for entry in self.synergies]
            if built_synergies:
                payload["synergies"] = built_synergies

        if self.respec_tokens > 0 or self.respec_points > 0:
            payload["respec"] = {
                "tokens": max(0, int(self.respec_tokens)),
                "points": max(0, int(self.respec_points)),
            }

        return payload


class ClassV2:
    domain = "class"

    def __init__(
        self,
        ctx: BuildContext,
        *,
        name: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> None:
        self.ctx = ctx
        self.id, self.symbol = ctx.register("class", symbol=symbol or name, id_override=id, parts=[name])
        self._name = name
        self._enabled = True
        self._name_key: str | None = None
        self._description_key: str | None = None
        self._description_lines: list[str] = []
        self._icon: dict[str, Any] | None = None

        self._unlock = ClassUnlockV2()
        self._bonuses = ClassBonusV2()
        self._tree = ClassSkillTreeV2()
        self._conditional_bonuses: list[ClassConditionalBonusV2] = []

    def enabled(self, value: bool) -> "ClassV2":
        self._enabled = bool(value)
        return self

    def name_key(self, key: str) -> "ClassV2":
        token = key.strip()
        if not token:
            raise ValueError(f"classes.{self.id}.name_key: cannot be empty")
        self._name_key = token
        return self

    def description_key(self, key: str) -> "ClassV2":
        token = key.strip()
        if not token:
            raise ValueError(f"classes.{self.id}.description_key: cannot be empty")
        self._description_key = token
        return self

    def description_lines(self, lines: Sequence[str]) -> "ClassV2":
        self._description_lines = [str(entry) for entry in lines if str(entry).strip()]
        return self

    def icon(
        self,
        value: MaterialLike | Ref | str,
        *,
        amount: int = 1,
        name: str | None = None,
        lore: Sequence[str] | None = None,
        fallback_material: MaterialLike | str = Material.BOOK,
    ) -> "ClassV2":
        if isinstance(value, Ref) or (hasattr(value, "symbol") and hasattr(value, "id")):
            item_id = self.ctx.resolve(value, domain="item", field=f"classes.{self.id}.icon.item", allow_external=True)
            self._icon = {
                "material": coerce_material(fallback_material, field=f"classes.{self.id}.icon.material"),
                "name": name or f"Class Icon: {item_id}",
                "lore": [f"Source item: {item_id}"],
                "amount": max(1, int(amount)),
            }
            return self

        raw = str(value).strip()
        if not raw:
            raise ValueError(f"classes.{self.id}.icon: value cannot be empty")
        try:
            material = coerce_material(raw, field=f"classes.{self.id}.icon.material")
            self._icon = {
                "material": material,
                "amount": max(1, int(amount)),
            }
            if name:
                self._icon["name"] = name
            if lore:
                self._icon["lore"] = [str(entry) for entry in lore]
            return self
        except Exception:
            item_id = self.ctx.resolve(raw, domain="item", field=f"classes.{self.id}.icon.item", allow_external=True)
            self._icon = {
                "material": coerce_material(fallback_material, field=f"classes.{self.id}.icon.material"),
                "name": name or f"Class Icon: {item_id}",
                "lore": [f"Source item: {item_id}"],
                "amount": max(1, int(amount)),
            }
            return self

    def icon_item(
        self,
        item_ref: Ref | str,
        *,
        fallback_material: MaterialLike | str = Material.BOOK,
        name: str | None = None,
        lore: Sequence[str] | None = None,
    ) -> "ClassV2":
        item_id = self.ctx.resolve(item_ref, domain="item", field=f"classes.{self.id}.icon.item", allow_external=True)
        self._icon = {
            "material": coerce_material(fallback_material, field=f"classes.{self.id}.icon.material"),
            "name": name or f"Class Icon: {item_id}",
            "lore": [*(list(lore) if lore else []), f"Source item: {item_id}"],
        }
        return self

    def unlock_level(self, value: int) -> "ClassV2":
        self._unlock.level = max(0, int(value))
        return self

    def unlock_tokens(self, value: int) -> "ClassV2":
        self._unlock.tokens = max(0, int(value))
        return self

    def unlock_quests(self, entries: Sequence[Ref | str]) -> "ClassV2":
        self._unlock.add_quests(*entries)
        return self

    def unlock_items(self, entries: Sequence[ClassUnlockItemV2 | str]) -> "ClassV2":
        self._unlock.add_items(*entries)
        return self

    def unlock_currencies(self, entries: Sequence[ClassUnlockCurrencyV2 | str]) -> "ClassV2":
        self._unlock.add_currencies(*entries)
        return self

    def stats(
        self,
        *,
        strength: Optional[int] = None,
        dexterity: Optional[int] = None,
        intelligence: Optional[int] = None,
        vitality: Optional[int] = None,
    ) -> "ClassV2":
        self._bonuses.stats(
            strength=strength,
            dexterity=dexterity,
            intelligence=intelligence,
            vitality=vitality,
        )
        return self

    def mana(self, *, resource: str | None = None, max: Optional[float] = None, regen: Optional[float] = None) -> "ClassV2":
        self._bonuses.mana(resource=resource, max=max, regen=regen)
        return self

    def attribute(
        self,
        attribute: str,
        amount: float,
        *,
        operation: AttributeOperationLike = AttributeOperation.ADD_NUMBER,
    ) -> "ClassV2":
        self._bonuses.attribute(attribute, amount, operation=operation)
        return self

    def potion(
        self,
        effect: PotionEffectLike,
        *,
        amplifier: int = 0,
        ambient: bool = True,
        particles: bool = True,
        icon: bool = True,
    ) -> "ClassV2":
        self._bonuses.potion(
            effect,
            amplifier=amplifier,
            ambient=ambient,
            particles=particles,
            icon=icon,
        )
        return self

    def resistance(self, damage_type: DamageTypeLike, multiplier: float) -> "ClassV2":
        self._bonuses.resistance(damage_type, multiplier)
        return self

    def attribute_cap(self, attribute: str, limit: float) -> "ClassV2":
        self._bonuses.attribute_cap(attribute, limit)
        return self

    def tree_respec(self, *, tokens: int = 0, points: int = 0) -> "ClassV2":
        self._tree.respec_tokens = max(0, int(tokens))
        self._tree.respec_points = max(0, int(points))
        return self

    def node(
        self,
        node: ClassNodeV2 | None = None,
        *,
        id: str | None = None,
        name: str | None = None,
        description_lines: Sequence[str] | None = None,
        material: MaterialLike | str = Material.PAPER,
        node_type: ClassNodeTypeLike = ClassNodeType.CUSTOM,
        cost: int = 1,
        max_rank: int = 1,
        requires: Sequence[str] | None = None,
        stat_key: str | None = None,
        stat_amount: float = 0.0,
        stat_scaling: ClassScalingModeLike | None = None,
        stat_curve: ClassScalingCurveLike | None = None,
        stat_curve_scale: float = 1.0,
        stat_curve_offset: float = 0.0,
        attribute_key: str | None = None,
        attribute_amount: float = 0.0,
        attribute_operation: AttributeOperationLike = AttributeOperation.ADD_NUMBER,
        attribute_scaling: ClassScalingModeLike | None = None,
        attribute_curve: ClassScalingCurveLike | None = None,
        attribute_curve_scale: float = 1.0,
        attribute_curve_offset: float = 0.0,
        potion_effect: PotionEffectLike | None = None,
        potion_amplifier: int = 0,
        potion_ambient: bool = True,
        potion_particles: bool = True,
        ability: Ref | str | None = None,
        ability_trigger: ClassAbilityTriggerLike = ClassAbilityTrigger.PASSIVE,
        ability_require_sneaking: bool = False,
        ability_permission: str | None = None,
        ability_period_ticks: int = 20,
        ability_cancel_event: bool = True,
        name_key: str | None = None,
        description_key: str | None = None,
    ) -> "ClassV2":
        if node is None:
            if id is None or name is None:
                raise ValueError(f"classes.{self.id}.path.nodes: id and name are required")
            node = ClassNodeV2(
                id=id,
                name=name,
                node_type=node_type,
                description_lines=list(description_lines or []),
                cost=cost,
                max_rank=max_rank,
                requires=[snake_case(entry) for entry in (requires or []) if snake_case(entry)],
                icon_material=material,
                name_key=name_key,
                description_key=description_key,
            )
            if stat_key is not None:
                node.set_stat(
                    stat_key,
                    stat_amount,
                    scaling=stat_scaling,
                    curve=stat_curve,
                    curve_scale=stat_curve_scale,
                    curve_offset=stat_curve_offset,
                )
            if attribute_key is not None:
                node.set_attribute(
                    attribute_key,
                    attribute_amount,
                    operation=attribute_operation,
                    scaling=attribute_scaling,
                    curve=attribute_curve,
                    curve_scale=attribute_curve_scale,
                    curve_offset=attribute_curve_offset,
                )
            if potion_effect is not None:
                node.set_potion(
                    potion_effect,
                    amplifier=potion_amplifier,
                    ambient=potion_ambient,
                    particles=potion_particles,
                )
            if ability is not None:
                node.set_ability(
                    ability,
                    trigger=ability_trigger,
                    require_sneaking=ability_require_sneaking,
                    permission=ability_permission,
                    period_ticks=ability_period_ticks,
                    cancel_event=ability_cancel_event,
                )

        self._tree.nodes.append(node)
        return self

    def edge(self, from_node: str, to_node: str) -> "ClassV2":
        self._tree.edges.append((from_node, to_node))
        return self

    def synergy(
        self,
        synergy: ClassSynergyV2 | None = None,
        *,
        id: str | None = None,
        requires: Sequence[str] | None = None,
        bonuses: ClassBonusV2 | None = None,
    ) -> "ClassV2":
        if synergy is None:
            if id is None:
                raise ValueError(f"classes.{self.id}.path.synergies: id is required")
            synergy = ClassSynergyV2(id=id, requires=list(requires or []), bonuses=bonuses or ClassBonusV2())
        self._tree.synergies.append(synergy)
        return self

    def conditional_bonus(
        self,
        conditional: ClassConditionalBonusV2 | None = None,
        *,
        worlds: Sequence[str] | None = None,
        regions: Sequence[ClassRegionSpec] | None = None,
        bonuses: ClassBonusV2 | None = None,
    ) -> "ClassV2":
        if conditional is None:
            conditional = ClassConditionalBonusV2(
                worlds=[str(entry) for entry in (worlds or [])],
                regions=[entry for entry in (regions or [])],
                bonuses=bonuses or ClassBonusV2(),
            )
        self._conditional_bonuses.append(conditional)
        return self

    def build(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "enabled": bool(self._enabled),
            "name": self._name,
        }
        if self._name_key:
            payload["nameKey"] = self._name_key
        if self._description_key:
            payload["descriptionKey"] = self._description_key
        if self._description_lines:
            payload["description"] = list(self._description_lines)
        if self._icon:
            payload["icon"] = _deep_copy_value(self._icon)

        unlock_payload = self._unlock.build(self.ctx, field=f"classes.{self.id}.unlock")
        if unlock_payload:
            payload["unlock"] = unlock_payload

        path_payload = self._tree.build(self.ctx, class_id=self.id)
        if path_payload:
            payload["path"] = path_payload

        bonuses_payload = self._bonuses.build()
        if self._conditional_bonuses:
            bonuses_payload.setdefault("conditional", [])
            for index, entry in enumerate(self._conditional_bonuses):
                bonuses_payload["conditional"].append(
                    entry.build(field=f"classes.{self.id}.bonuses.conditional[{index}]")
                )
        if bonuses_payload:
            payload["bonuses"] = bonuses_payload

        return payload


class rpg_class:
    @staticmethod
    def create(
        ctx: BuildContext,
        *,
        name: str,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> ClassV2:
        return ClassV2(ctx=ctx, name=name, id=id, symbol=symbol)


__all__ = [
    "ClassV2",
    "ClassUnlockV2",
    "ClassBonusV2",
    "ClassSkillTreeV2",
    "ClassNodeV2",
    "ClassSynergyV2",
    "ClassConditionalBonusV2",
    "ClassItemMatcherSpec",
    "ClassRegionSpec",
    "ClassUnlockItemV2",
    "ClassUnlockCurrencyV2",
    "rpg_class",
]
