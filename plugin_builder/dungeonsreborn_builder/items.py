"""Items builder (effects/items YAML)."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, Iterable, List, Mapping, Optional

from .base import BuilderBase, ExporterBase, snake_case
from .gui import GuiTileSpec
from .vanilla import (
    Attribute,
    Enchantment,
    EnumValue,
    ItemFlag,
    Material,
    Particle,
    normalize_enum_name,
    parse_enum,
)


def _require_enum(value: EnumValue | str, label: str, enum_cls: type[Enum] | None = None) -> str:
    if isinstance(value, Enum):
        return normalize_enum_name(value.name)
    if isinstance(value, str) and enum_cls is not None:
        parsed = parse_enum(enum_cls, value, label=label)
        return normalize_enum_name(parsed.name)
    raise ValueError(f"{label} must be provided as a vanilla enum value")


def _enum_or_str(value: Any, label: str) -> str:
    if isinstance(value, Enum):
        return value.name
    if isinstance(value, str):
        return value
    raise ValueError(f"{label} must be provided as an enum value or string")


def _looks_like_base64_texture(value: str) -> bool:
    trimmed = value.strip()
    return trimmed.startswith("eyJ") and len(trimmed) > 80


class ItemClick(Enum):
    LEFT_CLICK = "LEFT_CLICK"
    RIGHT_CLICK = "RIGHT_CLICK"
    SHIFT_LEFT = "SHIFT_LEFT"
    SHIFT_RIGHT = "SHIFT_RIGHT"
    PASSIVE = "PASSIVE"


class ItemHookType(Enum):
    ON_EQUIP = "onEquip"
    ON_HIT = "onHit"
    ON_HURT = "onHurt"
    ON_CONSUME = "onConsume"
    ON_BLOCK_BREAK = "onBlockBreak"


class ItemConsumeMode(Enum):
    NONE = "none"
    STACK = "stack"
    DURABILITY = "durability"


@dataclass
class ItemMatcherSpec:
    matcher_type: str
    data: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        payload = {"type": self.matcher_type}
        payload.update(self.data)
        return payload


def item_matcher_any() -> ItemMatcherSpec:
    return ItemMatcherSpec("any")


def item_matcher_material(material: Material | str) -> ItemMatcherSpec:
    return ItemMatcherSpec("material", {"material": _require_enum(material, "material", Material)})


def item_matcher_custom_model_data(value: int) -> ItemMatcherSpec:
    return ItemMatcherSpec("custom_model_data", {"value": int(value)})


def item_matcher_pdc_tag(key: str) -> ItemMatcherSpec:
    return ItemMatcherSpec("pdc_tag", {"key": key})


def item_matcher_lore_contains(text: str) -> ItemMatcherSpec:
    return ItemMatcherSpec("lore_contains", {"text": text})


def item_matcher_and(*matchers: ItemMatcherSpec) -> ItemMatcherSpec:
    return ItemMatcherSpec("and", {"matchers": [m.to_dict() for m in matchers]})


def item_matcher_or(*matchers: ItemMatcherSpec) -> ItemMatcherSpec:
    return ItemMatcherSpec("or", {"matchers": [m.to_dict() for m in matchers]})


@dataclass
class ColorSpec:
    hex: Optional[str] = None
    value: Optional[str] = None
    r: Optional[int] = None
    g: Optional[int] = None
    b: Optional[int] = None

    @classmethod
    def from_rgb(cls, r: int, g: int, b: int) -> "ColorSpec":
        return cls(r=r, g=g, b=b)

    @classmethod
    def from_hex(cls, value: str) -> "ColorSpec":
        return cls(hex=value)

    def to_dict(self) -> Dict[str, Any]:
        if self.hex:
            return {"hex": self.hex}
        if self.value:
            return {"value": self.value}
        if self.r is not None and self.g is not None and self.b is not None:
            return {"r": self.r, "g": self.g, "b": self.b}
        return {}


@dataclass
class ItemBinding:
    click: Optional[ItemClick | str]
    ability: str
    binding_type: Optional[str] = None
    require_sneaking: Optional[bool] = None
    permission: Optional[str] = None
    cancel_event: Optional[bool] = None
    period_ticks: Optional[int] = None
    interval_ticks: Optional[int] = None
    slot: Optional[str] = None
    slots: List[str] = field(default_factory=list)
    item: Optional[ItemMatcherSpec] = None
    binding_id: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"ability": self.ability}
        if self.binding_id:
            data["id"] = self.binding_id
        if self.click is not None:
            data["click"] = _enum_or_str(self.click, "click")
        resolved_type = self.binding_type
        if resolved_type is None and self.click is not None:
            if _enum_or_str(self.click, "click").lower() == "passive":
                resolved_type = "passive"
        if resolved_type:
            data["type"] = resolved_type
        if self.require_sneaking is not None:
            data["requireSneaking"] = self.require_sneaking
        if self.permission:
            data["permission"] = self.permission
        if self.cancel_event is not None:
            data["cancelEvent"] = self.cancel_event
        period = self.period_ticks
        if period is None and self.interval_ticks is not None:
            period = self.interval_ticks
        if period is not None:
            data["periodTicks"] = period
        if self.slot:
            data["slot"] = self.slot
        if self.slots:
            data["slots"] = list(self.slots)
        if self.item is not None:
            data["item"] = self.item.to_dict()
        return data


@dataclass
class ManaBonus:
    max: Optional[int] = None
    regen: Optional[float] = None
    boost: Optional[float] = None
    temp_boost: Optional[float] = None
    temporary_boost: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.max is not None:
            data["max"] = self.max
        if self.regen is not None:
            data["regen"] = self.regen
        if self.boost is not None:
            data["boost"] = self.boost
        if self.temp_boost is not None:
            data["tempBoost"] = self.temp_boost
        if self.temporary_boost is not None:
            data["temporaryBoost"] = self.temporary_boost
        return data


@dataclass
class EnchantmentSpec:
    enchantment: Enchantment
    level: int = 1

    def to_dict(self) -> Dict[str, Any]:
        return {"id": _require_enum(self.enchantment, "enchantment", Enchantment), "level": self.level}


@dataclass
class ItemAttributeModifierSpec:
    attribute: Attribute
    amount: float
    operation: str = "add_number"
    key: Optional[str] = None
    slot: Optional[str] = None
    slot_group: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "attribute": _require_enum(self.attribute, "attribute", Attribute),
            "amount": float(self.amount),
        }
        if self.operation:
            data["operation"] = self.operation
        if self.key:
            data["key"] = self.key
        if self.slot:
            data["slot"] = self.slot
        if self.slot_group:
            data["slotGroup"] = self.slot_group
        return data


@dataclass
class DurabilityRange:
    min_damage: int
    max_damage: int

    def to_dict(self) -> Dict[str, Any]:
        return {"min": self.min_damage, "max": self.max_damage}


@dataclass
class DisplaySpec:
    name: Optional[str] = None
    name_key: Optional[str] = None
    lore: List[str] = field(default_factory=list)
    lore_keys: List[str] = field(default_factory=list)
    subtitle: Optional[str] = None
    subtitle_key: Optional[str] = None
    description: Optional[str] = None
    description_key: Optional[str] = None
    rarity_line: Optional[str] = None
    rarity_line_key: Optional[str] = None
    flavor: Optional[str] = None
    flavor_key: Optional[str] = None
    placeholders: Dict[str, Any] = field(default_factory=dict)
    custom_model_data: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.name:
            data["name"] = self.name
        if self.name_key:
            data["nameKey"] = self.name_key
        if self.lore:
            data["lore"] = list(self.lore)
        if self.lore_keys:
            data["loreKeys"] = list(self.lore_keys)
        if self.subtitle:
            data["subtitle"] = self.subtitle
        if self.subtitle_key:
            data["subtitleKey"] = self.subtitle_key
        if self.description:
            data["description"] = self.description
        if self.description_key:
            data["descriptionKey"] = self.description_key
        if self.rarity_line:
            data["rarityLine"] = self.rarity_line
        if self.rarity_line_key:
            data["rarityLineKey"] = self.rarity_line_key
        if self.flavor:
            data["flavor"] = self.flavor
        if self.flavor_key:
            data["flavorKey"] = self.flavor_key
        if self.placeholders:
            data["placeholders"] = dict(self.placeholders)
        if self.custom_model_data is not None:
            data["custom_model_data"] = self.custom_model_data
        return data


@dataclass
class MetaSpec:
    display_name: Optional[str] = None
    display_name_key: Optional[str] = None
    lore: List[str] = field(default_factory=list)
    lore_keys: List[str] = field(default_factory=list)
    unbreakable: Optional[bool] = None
    enchants: Dict[Enchantment, int] = field(default_factory=dict)
    enchantments: List[EnchantmentSpec] = field(default_factory=list)
    flags: List[ItemFlag] = field(default_factory=list)
    attributes: List[ItemAttributeModifierSpec] = field(default_factory=list)
    pdc: Dict[str, Any] = field(default_factory=dict)
    durability: Optional[DurabilityRange] = None
    damage_min: Optional[int] = None
    damage_max: Optional[int] = None
    damage: Optional[int] = None
    repair_cost: Optional[int] = None
    max_damage: Optional[int] = None
    placeholders: Dict[str, Any] = field(default_factory=dict)
    custom_model_data: Optional[int] = None
    book: Optional["BookMetaSpec"] = None
    potion: Optional["PotionMetaSpec"] = None
    suspicious_stew: Optional["SuspiciousStewMetaSpec"] = None
    banner: Optional["BannerMetaSpec"] = None
    shield: Optional["ShieldMetaSpec"] = None
    firework: Optional["FireworkMetaSpec"] = None
    firework_charge: Optional["FireworkChargeMetaSpec"] = None
    map_meta: Optional["MapMetaSpec"] = None
    skull: Optional["SkullMetaSpec"] = None
    trim: Optional["TrimMetaSpec"] = None
    custom_model_data_component: Optional["CustomModelDataComponentSpec"] = None
    components: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.display_name:
            data["display-name"] = self.display_name
        if self.display_name_key:
            data["displayNameKey"] = self.display_name_key
        if self.lore:
            data["lore"] = list(self.lore)
        if self.lore_keys:
            data["loreKeys"] = list(self.lore_keys)
        if self.unbreakable is not None:
            data["unbreakable"] = self.unbreakable
        if self.enchants:
            data["enchants"] = {
                _require_enum(enchant, "enchantment", Enchantment): level
                for enchant, level in self.enchants.items()
            }
        if self.enchantments:
            data["enchantments"] = [entry.to_dict() for entry in self.enchantments]
        if self.flags:
            data["flags"] = [_require_enum(flag, "item flag", ItemFlag) for flag in self.flags]
        if self.attributes:
            data["attributes"] = [entry.to_dict() for entry in self.attributes]
        if self.pdc:
            data["pdc"] = dict(self.pdc)
        if self.durability:
            data["durability"] = self.durability.to_dict()
        if self.damage_min is not None:
            data["damageMin"] = self.damage_min
        if self.damage_max is not None:
            data["damageMax"] = self.damage_max
        if self.damage is not None:
            data["damage"] = self.damage
        if self.repair_cost is not None:
            data["repair_cost"] = self.repair_cost
        if self.max_damage is not None:
            data["max_damage"] = self.max_damage
        if self.placeholders:
            data["placeholders"] = dict(self.placeholders)
        if self.custom_model_data is not None:
            data["custom_model_data"] = self.custom_model_data
        if self.book is not None:
            book = self.book.to_dict()
            if book:
                data["book"] = book
        if self.potion is not None:
            potion = self.potion.to_dict()
            if potion:
                data["potion"] = potion
        if self.suspicious_stew is not None:
            stew = self.suspicious_stew.to_dict()
            if stew:
                data["suspicious_stew"] = stew
        if self.banner is not None:
            banner = self.banner.to_dict()
            if banner:
                data["banner"] = banner
        if self.shield is not None:
            shield = self.shield.to_dict()
            if shield:
                data["shield"] = shield
        if self.firework is not None:
            firework = self.firework.to_dict()
            if firework:
                data["firework"] = firework
        if self.firework_charge is not None:
            charge = self.firework_charge.to_dict()
            if charge:
                data["firework_charge"] = charge
        if self.map_meta is not None:
            map_meta = self.map_meta.to_dict()
            if map_meta:
                data["map"] = map_meta
        if self.skull is not None:
            skull = self.skull.to_dict()
            if skull:
                data["skull"] = skull
        if self.trim is not None:
            trim = self.trim.to_dict()
            if trim:
                data["trim"] = trim
        if self.custom_model_data_component is not None:
            component = self.custom_model_data_component.to_dict()
            if component:
                data.setdefault("components", {})["custom_model_data"] = component
        if self.components:
            data.setdefault("components", {}).update(self.components)
        return data


@dataclass
class BookMetaSpec:
    title: Optional[str] = None
    author: Optional[str] = None
    pages: List[str] = field(default_factory=list)
    generation: Optional[str] = None
    signed: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.title:
            data["title"] = self.title
        if self.author:
            data["author"] = self.author
        if self.pages:
            data["pages"] = list(self.pages)
        if self.generation:
            data["generation"] = self.generation
        if self.signed is not None:
            data["signed"] = self.signed
        return data


@dataclass
class PotionEffectSpec:
    effect_type: str
    duration: Optional[int] = None
    amplifier: Optional[int] = None
    ambient: Optional[bool] = None
    particles: Optional[bool] = None
    icon: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"type": self.effect_type}
        if self.duration is not None:
            data["duration"] = self.duration
        if self.amplifier is not None:
            data["amplifier"] = self.amplifier
        if self.ambient is not None:
            data["ambient"] = self.ambient
        if self.particles is not None:
            data["particles"] = self.particles
        if self.icon is not None:
            data["icon"] = self.icon
        return data


@dataclass
class PotionMetaSpec:
    base: Optional[str] = None
    color: Optional[ColorSpec] = None
    effects: List[PotionEffectSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.base:
            data["base"] = self.base
        if self.color is not None:
            color = self.color.to_dict()
            if color:
                data["color"] = color
        if self.effects:
            data["effects"] = [effect.to_dict() for effect in self.effects]
        return data


@dataclass
class SuspiciousStewMetaSpec:
    effects: List[PotionEffectSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        if not self.effects:
            return {}
        return {"effects": [effect.to_dict() for effect in self.effects]}


@dataclass
class BannerPatternSpec:
    pattern: str
    color: str

    def to_dict(self) -> Dict[str, Any]:
        return {"pattern": self.pattern, "color": self.color}


@dataclass
class BannerMetaSpec:
    patterns: List[BannerPatternSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        if not self.patterns:
            return {}
        return {"patterns": [pattern.to_dict() for pattern in self.patterns]}


@dataclass
class ShieldMetaSpec:
    base_color: Optional[str] = None
    patterns: List[BannerPatternSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.base_color:
            data["base_color"] = self.base_color
        if self.patterns:
            data["patterns"] = [pattern.to_dict() for pattern in self.patterns]
        return data


@dataclass
class FireworkEffectSpec:
    effect_type: str
    colors: List[str] = field(default_factory=list)
    fades: List[str] = field(default_factory=list)
    flicker: Optional[bool] = None
    trail: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"type": self.effect_type}
        if self.colors:
            data["colors"] = list(self.colors)
        if self.fades:
            data["fades"] = list(self.fades)
        if self.flicker is not None:
            data["flicker"] = self.flicker
        if self.trail is not None:
            data["trail"] = self.trail
        return data


@dataclass
class FireworkMetaSpec:
    power: Optional[int] = None
    effects: List[FireworkEffectSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.power is not None:
            data["power"] = self.power
        if self.effects:
            data["effects"] = [effect.to_dict() for effect in self.effects]
        return data


@dataclass
class FireworkChargeMetaSpec:
    effect: Optional[FireworkEffectSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        if self.effect is None:
            return {}
        effect = self.effect.to_dict()
        return {"effect": effect} if effect else {}


@dataclass
class MapMetaSpec:
    color: Optional[ColorSpec] = None
    scale: Optional[bool] = None
    location_name: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.color is not None:
            color = self.color.to_dict()
            if color:
                data["color"] = color
        if self.scale is not None:
            data["scale"] = self.scale
        if self.location_name:
            data["location_name"] = self.location_name
        return data


@dataclass
class SkullProfileSpec:
    name: Optional[str] = None
    uuid: Optional[str] = None
    texture: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.name:
            data["name"] = self.name
        if self.uuid:
            data["uuid"] = self.uuid
        if self.texture:
            data["texture"] = self.texture
        return data


@dataclass
class SkullMetaSpec:
    head: Optional[str] = None
    texture: Optional[str] = None
    owner: Optional[str] = None
    profile: Optional[SkullProfileSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.head:
            data["head"] = self.head
        if self.texture:
            data["texture"] = self.texture
        if self.owner:
            data["owner"] = self.owner
        if self.profile is not None:
            profile = self.profile.to_dict()
            if profile:
                data["profile"] = profile
        return data


@dataclass
class TrimMetaSpec:
    material: str
    pattern: str

    def to_dict(self) -> Dict[str, Any]:
        return {"material": self.material, "pattern": self.pattern}


@dataclass
class CustomModelDataComponentSpec:
    value: Optional[float] = None
    floats: List[float] = field(default_factory=list)
    strings: List[str] = field(default_factory=list)
    flags: List[bool] = field(default_factory=list)
    colors: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.value is not None:
            data["value"] = self.value
        if self.floats:
            data["floats"] = list(self.floats)
        if self.strings:
            data["strings"] = list(self.strings)
        if self.flags:
            data["flags"] = list(self.flags)
        if self.colors:
            data["colors"] = list(self.colors)
        return data


@dataclass
class GuiPreviewSpec:
    head: Optional[str] = None
    icon: Optional[str] = None
    summary_keys: List[str] = field(default_factory=list)
    title_key: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.head:
            data["head"] = self.head
        if self.icon:
            data["icon"] = self.icon
        if self.summary_keys:
            data["summaryKeys"] = list(self.summary_keys)
        if self.title_key:
            data["titleKey"] = self.title_key
        return data


@dataclass
class AffixRange:
    min: float
    max: float

    def to_dict(self) -> Any:
        if self.min == self.max:
            return self.min
        return {"min": self.min, "max": self.max}


@dataclass
class AffixSpec:
    affix_id: str
    stat_ranges: Dict[str, AffixRange]
    affix_type: Optional[str] = None
    weight: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"id": self.affix_id, "weight": self.weight}
        if self.affix_type:
            data["type"] = self.affix_type
        data["stats"] = {key: value.to_dict() for key, value in self.stat_ranges.items()}
        return data


@dataclass
class AffixPool:
    affixes: List[AffixSpec]
    rolls: int = 0
    allow_duplicates: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "rolls": self.rolls,
            "allowDuplicates": self.allow_duplicates,
            "pool": [affix.to_dict() for affix in self.affixes],
        }


@dataclass
class ItemStatCaps:
    soft: Dict[str, float] = field(default_factory=dict)
    hard: Dict[str, float] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.soft:
            data["softCaps"] = dict(self.soft)
        if self.hard:
            data["hardCaps"] = dict(self.hard)
        return data


@dataclass
class ItemTierSpec:
    id: Optional[str] = None
    scale: Optional[float] = None
    caps: Optional[ItemStatCaps] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.id:
            data["id"] = self.id
        if self.scale is not None:
            data["scale"] = self.scale
        if self.caps is not None:
            data.update(self.caps.to_dict())
        return data


@dataclass
class ItemConsumable:
    mode: ItemConsumeMode
    amount: int = 1

    def to_dict(self) -> Dict[str, Any]:
        return {"mode": _enum_or_str(self.mode, "consume mode"), "amount": self.amount}


@dataclass
class ItemUpgrades:
    slots: Dict[str, int] = field(default_factory=dict)
    max_upgrades: Optional[int] = None
    tier_budget: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.slots:
            data["slots"] = dict(self.slots)
        if self.max_upgrades is not None:
            data["maxUpgrades"] = self.max_upgrades
        if self.tier_budget is not None:
            data["tierBudget"] = self.tier_budget
        return data


@dataclass
class SoundSpec:
    id: str
    volume: float = 1.0
    pitch: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        return {"id": self.id, "volume": self.volume, "pitch": self.pitch}


@dataclass
class ParticleSpec:
    type: Particle
    count: int = 1
    offset: float = 0.0
    extra: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "type": _enum_or_str(self.type, "particle"),
            "count": self.count,
            "offset": self.offset,
            "extra": self.extra,
        }


@dataclass
class ItemBehaviorHook:
    abilities: List[str] = field(default_factory=list)
    sound: Optional[SoundSpec] = None
    particle: Optional[ParticleSpec] = None
    cooldown_ticks: Optional[int] = None
    cooldown_seconds: Optional[float] = None
    mana_cost: Optional[float] = None
    durability_cost: Optional[int] = None
    consume_amount: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if len(self.abilities) == 1:
            data["ability"] = self.abilities[0]
        elif self.abilities:
            data["abilities"] = list(self.abilities)
        if self.sound is not None:
            data["sound"] = self.sound.to_dict()
        if self.particle is not None:
            data["particle"] = self.particle.to_dict()
        if self.cooldown_ticks is not None:
            data["cooldownTicks"] = self.cooldown_ticks
        if self.cooldown_seconds is not None:
            data["cooldownSeconds"] = self.cooldown_seconds
        if self.mana_cost is not None:
            data["manaCost"] = self.mana_cost
        if self.durability_cost is not None:
            data["durabilityCost"] = self.durability_cost
        if self.consume_amount is not None:
            data["consumeAmount"] = self.consume_amount
        return data


class ItemBuilder(BuilderBase):
    def __init__(self, item_id: str) -> None:
        super().__init__(_id=item_id)
        self._material: Optional[Material] = None
        self._amount: int = 1
        self._display: Optional[DisplaySpec] = None
        self._meta: Optional[MetaSpec] = None
        self._bindings: List[ItemBinding] = []
        self._mana: Optional[ManaBonus] = None
        self._consumable: Optional[ItemConsumable] = None
        self._upgrades: Optional[ItemUpgrades] = None
        self._behavior: Dict[ItemHookType, List[ItemBehaviorHook]] = {}
        self._stats: Dict[str, float] = {}
        self._affix_pool: Optional[AffixPool] = None
        self._tier: Optional[str] = None
        self._tier_spec: Optional[ItemTierSpec] = None
        self._rarity: Optional[str] = None
        self._version: Optional[int] = None
        self._attributes: Dict[Attribute, float] = {}
        self._gui_preview: Optional[GuiPreviewSpec] = None

    def material(self, material: Material | str) -> "ItemBuilder":
        if isinstance(material, str):
            self._material = parse_enum(Material, material, label="material")
        else:
            _require_enum(material, "material", Material)
            self._material = material
        return self

    def amount(self, amount: int) -> "ItemBuilder":
        self._amount = max(1, amount)
        return self

    def display(self, display: DisplaySpec) -> "ItemBuilder":
        self._display = display
        return self

    def meta(self, meta: MetaSpec) -> "ItemBuilder":
        self._meta = meta
        return self

    def gui_preview(self, preview: GuiPreviewSpec) -> "ItemBuilder":
        self._gui_preview = preview
        return self

    def auto_gui_preview(self) -> "ItemBuilder":
        if self._gui_preview is None and self._display and self._display.name_key:
            self._gui_preview = GuiPreviewSpec(title_key=self._display.name_key)
        return self

    def gui_preview_head(self, head_id: str) -> "ItemBuilder":
        preview = self._gui_preview or GuiPreviewSpec()
        preview.head = head_id
        self._gui_preview = preview
        return self

    def gui_preview_icon(self, icon: str) -> "ItemBuilder":
        preview = self._gui_preview or GuiPreviewSpec()
        preview.icon = icon
        self._gui_preview = preview
        return self

    def gui_preview_summary_keys(self, *keys: str) -> "ItemBuilder":
        preview = self._gui_preview or GuiPreviewSpec()
        preview.summary_keys.extend(keys)
        self._gui_preview = preview
        return self

    def gui_preview_title_key(self, key: str) -> "ItemBuilder":
        preview = self._gui_preview or GuiPreviewSpec()
        preview.title_key = key
        self._gui_preview = preview
        return self

    def gui_preview_tile(self, tile: GuiTileSpec) -> "ItemBuilder":
        preview = self._gui_preview or GuiPreviewSpec()
        if tile.head:
            preview.head = tile.head
        if tile.icon:
            preview.icon = tile.icon
        if tile.title_key:
            preview.title_key = tile.title_key
        if tile.summary_keys:
            preview.summary_keys.extend(tile.summary_keys)
        self._gui_preview = preview
        return self

    def display_name(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.name = value
        self._display = display
        return self

    def display_name_key(self, key: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.name_key = key
        self._display = display
        return self

    def display_lore(self, *lines: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.lore.extend(lines)
        self._display = display
        return self

    def display_lore_keys(self, *keys: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.lore_keys.extend(keys)
        self._display = display
        return self

    def display_subtitle(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.subtitle = value
        self._display = display
        return self

    def display_subtitle_key(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.subtitle_key = value
        self._display = display
        return self

    def display_description(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.description = value
        self._display = display
        return self

    def display_description_key(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.description_key = value
        self._display = display
        return self

    def display_rarity_line(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.rarity_line = value
        self._display = display
        return self

    def display_rarity_line_key(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.rarity_line_key = value
        self._display = display
        return self

    def display_flavor(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.flavor = value
        self._display = display
        return self

    def display_flavor_key(self, value: str) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.flavor_key = value
        self._display = display
        return self

    def display_placeholder(self, key: str, value: Any) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.placeholders[key] = value
        self._display = display
        return self

    def custom_model_data(self, value: int) -> "ItemBuilder":
        display = self._display or DisplaySpec()
        display.custom_model_data = value
        self._display = display
        return self

    def meta_display_name(self, value: str) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.display_name = value
        self._meta = meta
        return self

    def meta_display_name_key(self, key: str) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.display_name_key = key
        self._meta = meta
        return self

    def meta_lore(self, *lines: str) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.lore.extend(lines)
        self._meta = meta
        return self

    def meta_lore_keys(self, *keys: str) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.lore_keys.extend(keys)
        self._meta = meta
        return self

    def unbreakable(self, value: bool = True) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.unbreakable = value
        self._meta = meta
        return self

    def enchant(self, enchantment: Enchantment, level: int = 1) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.enchants[enchantment] = level
        self._meta = meta
        return self

    def enchantment(self, enchantment: Enchantment, level: int = 1) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.enchantments.append(EnchantmentSpec(enchantment=enchantment, level=level))
        self._meta = meta
        return self

    def flag(self, *flags: ItemFlag) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        for flag in flags:
            _require_enum(flag, "item flag", ItemFlag)
            meta.flags.append(flag)
        self._meta = meta
        return self

    def pdc_tag(self, key: str, value: Any = True) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.pdc[key] = value
        self._meta = meta
        return self

    def durability_range(self, min_damage: int, max_damage: int) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.durability = DurabilityRange(min_damage=min_damage, max_damage=max_damage)
        self._meta = meta
        return self

    def meta_damage_range(self, min_damage: int, max_damage: int) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.damage_min = min_damage
        meta.damage_max = max_damage
        self._meta = meta
        return self

    def meta_damage(self, damage: int) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.damage = damage
        self._meta = meta
        return self

    def meta_repair_cost(self, cost: int) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.repair_cost = cost
        self._meta = meta
        return self

    def meta_max_damage(self, max_damage: int) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.max_damage = max_damage
        self._meta = meta
        return self

    def meta_placeholder(self, key: str, value: Any) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.placeholders[key] = value
        self._meta = meta
        return self

    def meta_custom_model_data(self, value: int) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.custom_model_data = value
        self._meta = meta
        return self

    def meta_custom_model_data_component(self, component: CustomModelDataComponentSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.custom_model_data_component = component
        self._meta = meta
        return self

    def meta_book(self, book: BookMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.book = book
        self._meta = meta
        return self

    def meta_potion(self, potion: PotionMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.potion = potion
        self._meta = meta
        return self

    def meta_suspicious_stew(self, stew: SuspiciousStewMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.suspicious_stew = stew
        self._meta = meta
        return self

    def meta_banner(self, banner: BannerMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.banner = banner
        self._meta = meta
        return self

    def meta_shield(self, shield: ShieldMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.shield = shield
        self._meta = meta
        return self

    def meta_firework(self, firework: FireworkMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.firework = firework
        self._meta = meta
        return self

    def meta_firework_charge(self, charge: FireworkChargeMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.firework_charge = charge
        self._meta = meta
        return self

    def meta_map(self, map_meta: MapMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.map_meta = map_meta
        self._meta = meta
        return self

    def meta_skull(self, skull: SkullMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.skull = skull
        self._meta = meta
        return self

    def meta_trim(self, trim: TrimMetaSpec) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.trim = trim
        self._meta = meta
        return self

    def weapon(self) -> "ItemBuilder":
        return self

    def trinket(self) -> "ItemBuilder":
        return self

    def bind_use(self, ability: str, **kwargs: Any) -> "ItemBuilder":
        return self.bind("RIGHT_CLICK", ability=ability, **kwargs)

    def on_use(self, ability: str, **kwargs: Any) -> "ItemBuilder":
        """Alias for bind_use to keep novice APIs natural."""
        return self.bind_use(ability, **kwargs)

    def meta_attribute_modifier(
        self,
        attribute: Attribute,
        amount: float,
        operation: str = "add_number",
        key: Optional[str] = None,
        slot: Optional[str] = None,
        slot_group: Optional[str] = None,
    ) -> "ItemBuilder":
        if isinstance(attribute, str):
            attribute = parse_enum(Attribute, attribute, label="attribute")
        _require_enum(attribute, "attribute", Attribute)
        meta = self._meta or MetaSpec()
        meta.attributes.append(
            ItemAttributeModifierSpec(
                attribute=attribute,
                amount=float(amount),
                operation=operation,
                key=key,
                slot=slot,
                slot_group=slot_group,
            )
        )
        self._meta = meta
        return self

    def meta_attribute_modifiers(
        self, modifiers: Iterable[ItemAttributeModifierSpec]
    ) -> "ItemBuilder":
        meta = self._meta or MetaSpec()
        meta.attributes.extend(list(modifiers))
        self._meta = meta
        return self

    def bind(self, click: ItemClick | str, ability: str, **kwargs: Any) -> "ItemBuilder":
        self._bindings.append(ItemBinding(click=click, ability=snake_case(ability), **kwargs))
        return self

    def bind_interact(self, click: ItemClick | str, ability: str, **kwargs: Any) -> "ItemBuilder":
        kwargs.setdefault("binding_type", "interact")
        return self.bind(click, ability, **kwargs)

    def bind_passive(
        self,
        ability: str,
        period_ticks: int = 20,
        slots: Optional[List[str]] = None,
        **kwargs: Any,
    ) -> "ItemBuilder":
        payload = dict(kwargs)
        payload.setdefault("binding_type", "passive")
        payload.setdefault("period_ticks", period_ticks)
        if slots:
            payload.setdefault("slots", list(slots))
        self._bindings.append(ItemBinding(click="PASSIVE", ability=ability, **payload))
        return self

    def mana_bonus(
        self,
        max: Optional[int] = None,
        regen: Optional[float] = None,
        boost: Optional[float] = None,
        temp_boost: Optional[float] = None,
        temporary_boost: Optional[float] = None,
    ) -> "ItemBuilder":
        self._mana = ManaBonus(
            max=max,
            regen=regen,
            boost=boost,
            temp_boost=temp_boost,
            temporary_boost=temporary_boost,
        )
        return self

    def consume(self, mode: ItemConsumeMode, amount: int = 1) -> "ItemBuilder":
        self._consumable = ItemConsumable(mode=mode, amount=max(1, amount))
        return self

    def upgrade_slots(self, slots: Mapping[str, int]) -> "ItemBuilder":
        upgrades = self._upgrades or ItemUpgrades()
        upgrades.slots.update({key: int(value) for key, value in slots.items()})
        self._upgrades = upgrades
        return self

    def upgrade_slot(self, slot: str, count: int) -> "ItemBuilder":
        upgrades = self._upgrades or ItemUpgrades()
        upgrades.slots[slot] = int(count)
        self._upgrades = upgrades
        return self

    def upgrade_max(self, max_upgrades: int) -> "ItemBuilder":
        upgrades = self._upgrades or ItemUpgrades()
        upgrades.max_upgrades = max(0, int(max_upgrades))
        self._upgrades = upgrades
        return self

    def upgrade_tier_budget(self, tier_budget: int) -> "ItemBuilder":
        upgrades = self._upgrades or ItemUpgrades()
        upgrades.tier_budget = max(0, int(tier_budget))
        self._upgrades = upgrades
        return self

    def upgrades(
        self,
        slots: Optional[Mapping[str, int]] = None,
        max_upgrades: Optional[int] = None,
        tier_budget: Optional[int] = None,
    ) -> "ItemBuilder":
        if slots:
            self.upgrade_slots(slots)
        if max_upgrades is not None:
            self.upgrade_max(max_upgrades)
        if tier_budget is not None:
            self.upgrade_tier_budget(tier_budget)
        return self

    def behavior(self, hook_type: ItemHookType, hook: ItemBehaviorHook) -> "ItemBuilder":
        self._behavior.setdefault(hook_type, []).append(hook)
        return self

    def on_equip(self, hook: ItemBehaviorHook) -> "ItemBuilder":
        return self.behavior(ItemHookType.ON_EQUIP, hook)

    def on_hit(self, hook: ItemBehaviorHook) -> "ItemBuilder":
        return self.behavior(ItemHookType.ON_HIT, hook)

    def on_hurt(self, hook: ItemBehaviorHook) -> "ItemBuilder":
        return self.behavior(ItemHookType.ON_HURT, hook)

    def on_consume(self, hook: ItemBehaviorHook) -> "ItemBuilder":
        return self.behavior(ItemHookType.ON_CONSUME, hook)

    def on_block_break(self, hook: ItemBehaviorHook) -> "ItemBuilder":
        return self.behavior(ItemHookType.ON_BLOCK_BREAK, hook)

    def stats(self, values: Mapping[str, float]) -> "ItemBuilder":
        self._stats = {key: float(value) for key, value in values.items()}
        return self

    def stat(self, key: str, value: float) -> "ItemBuilder":
        self._stats[key] = float(value)
        return self

    def attribute(self, attribute: Attribute, value: float) -> "ItemBuilder":
        if isinstance(attribute, str):
            attribute = parse_enum(Attribute, attribute, label="attribute")
        _require_enum(attribute, "attribute", Attribute)
        self._attributes[attribute] = float(value)
        return self

    def attribute_modifier(
        self,
        attribute: Attribute,
        amount: float,
        operation: str = "add_number",
        key: Optional[str] = None,
        slot: Optional[str] = None,
        slot_group: Optional[str] = None,
    ) -> "ItemBuilder":
        return self.meta_attribute_modifier(
            attribute=attribute,
            amount=amount,
            operation=operation,
            key=key,
            slot=slot,
            slot_group=slot_group,
        )

    def affix_pool(self, pool: AffixPool) -> "ItemBuilder":
        self._affix_pool = pool
        return self

    def tier(self, tier: str) -> "ItemBuilder":
        self._tier = tier
        return self

    def tier_spec(self, tier: ItemTierSpec) -> "ItemBuilder":
        self._tier_spec = tier
        return self

    def rarity(self, rarity: str) -> "ItemBuilder":
        self._rarity = rarity
        return self

    def version(self, version: int) -> "ItemBuilder":
        self._version = version
        return self

    def build(self) -> Dict[str, Any]:
        self._ensure_id("item_id")
        self._ensure_name()
        if not self._material:
            raise ValueError("material is required")

        display = self._display
        if self._name or self._description:
            display = display or DisplaySpec()
            if self._name and not display.name and not display.name_key:
                display.name = self._name
            if self._description and not display.description:
                display.description = self._description

        item: Dict[str, Any] = {
            "type": "material",
            "material": _require_enum(self._material, "material", Material),
        }
        if self._amount != 1:
            item["amount"] = self._amount
        if display is not None:
            display_dict = display.to_dict()
            if display_dict:
                item["display"] = display_dict
        if self._meta is not None:
            meta_dict = self._meta.to_dict()
            if meta_dict:
                item["meta"] = meta_dict

        data: Dict[str, Any] = {"item": item}
        if self._bindings:
            data["bindings"] = [binding.to_dict() for binding in self._bindings]
        if self._mana:
            data["mana"] = self._mana.to_dict()
        if self._consumable:
            data["consumable"] = self._consumable.to_dict()
        if self._upgrades:
            upgrades = self._upgrades.to_dict()
            if upgrades:
                data["upgrades"] = upgrades
        if self._behavior:
            behavior: Dict[str, Any] = {}
            for hook_type, hooks in self._behavior.items():
                behavior[_enum_or_str(hook_type, "hook type")] = [hook.to_dict() for hook in hooks]
            if behavior:
                data["behavior"] = behavior
        if self._stats:
            data["stats"] = dict(self._stats)
        if self._attributes:
            data["attributes"] = {
                _require_enum(attr, "attribute", Attribute): value for attr, value in self._attributes.items()
            }
        if self._affix_pool:
            data["affixes"] = self._affix_pool.to_dict()
        if self._tier_spec:
            data["tier"] = self._tier_spec.to_dict()
        elif self._tier:
            data["tier"] = {"id": self._tier}
        if self._rarity:
            data["rarity"] = self._rarity
        if self._gui_preview:
            preview = self._gui_preview.to_dict()
            if preview:
                data["gui"] = preview
        if self._version is not None:
            data["version"] = self._version
        return self._apply_overrides(data, f"item:{self._id}")


def skull_head(head_id: str) -> SkullMetaSpec:
    return SkullMetaSpec(head=head_id)


def skull_texture(texture: str) -> SkullMetaSpec:
    return SkullMetaSpec(texture=texture)


def skull_ref(value: str) -> SkullMetaSpec:
    if _looks_like_base64_texture(value):
        return SkullMetaSpec(texture=value)
    return SkullMetaSpec(head=value)


def item_ref(item_id: str) -> Dict[str, Any]:
    return {"itemId": item_id}


def item_map(
    material: Material | str,
    name: Optional[str] = None,
    lore: Optional[Iterable[str]] = None,
) -> Dict[str, Any]:
    payload: Dict[str, Any] = {"material": _require_enum(material, "material", Material)}
    if name:
        payload["name"] = name
    if lore:
        payload["lore"] = list(lore)
    return payload


def Item(item_id: str) -> ItemBuilder:
    return ItemBuilder(item_id)


class ItemExporter(ExporterBase):
    def write_item(self, builder: ItemBuilder, filename: Optional[str] = None) -> str:
        name = filename or f"{builder._id}.yml"
        return self.write_yaml(name, builder.build())


# Macros

def bound_weapon(item_id: str, material: Material, ability: str) -> ItemBuilder:
    return (
        ItemBuilder(item_id)
        .material(material)
        .display_name(f"<gold>{item_id}</gold>")
        .bind("RIGHT_CLICK", ability=ability)
    )


def weapon_item(item_id: str, material: Material, name: Optional[str] = None, damage: float = 0.0) -> ItemBuilder:
    builder = ItemBuilder(item_id).material(material)
    builder.display_name(name or f"<gold>{item_id}</gold>")
    if damage:
        builder.stat("damage", damage)
    return builder


def armor_item(item_id: str, material: Material, name: Optional[str] = None, armor: float = 0.0) -> ItemBuilder:
    builder = ItemBuilder(item_id).material(material)
    builder.display_name(name or f"<gold>{item_id}</gold>")
    if armor:
        builder.stat("armor", armor)
    return builder


def trinket_item(item_id: str, material: Material, name: Optional[str] = None) -> ItemBuilder:
    builder = ItemBuilder(item_id).material(material)
    builder.display_name(name or f"<gold>{item_id}</gold>")
    return builder


def material_item(item_id: str, material: Material, name: Optional[str] = None) -> ItemBuilder:
    builder = ItemBuilder(item_id).material(material)
    builder.display_name(name or f"<yellow>{item_id}</yellow>")
    return builder
