"""Shop builder (tokens, currencies, trades, vendors)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence

from .base import BuilderBase, ExporterBase, snake_case
from .utils import apply_overrides
from .items import ItemMatcherSpec
from .vanilla import EnumValue, Material, normalize_enum_name
from .gui import GuiTileSpec


def _enum_or_str(value: Any, label: str) -> str:
    if isinstance(value, EnumValue):
        return normalize_enum_name(value.name)
    if isinstance(value, str):
        return value
    raise ValueError(f"{label} must be provided as an enum value or string")


def _item_to_dict(value: Any) -> Any:
    if value is None:
        return None
    if hasattr(value, "build"):
        return value.build()
    if isinstance(value, Mapping):
        return dict(value)
    return value


def _matcher_to_dict(value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, ItemMatcherSpec):
        return value.to_dict()
    if isinstance(value, Mapping):
        return dict(value)
    return value


@dataclass
class ShopRegionSpec:
    world: str
    x: float
    y: float
    z: float
    radius: float

    def to_dict(self) -> Dict[str, Any]:
        return {
            "world": self.world,
            "x": self.x,
            "y": self.y,
            "z": self.z,
            "radius": self.radius,
        }


@dataclass
class ShopRegionPriceSpec:
    region: ShopRegionSpec
    multiplier: float = 1.0
    tax_rate: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        data = self.region.to_dict()
        if self.multiplier != 1.0:
            data["multiplier"] = self.multiplier
        if self.tax_rate:
            data["taxRate"] = self.tax_rate
        return data


@dataclass
class ShopTimeWindowSpec:
    start: str
    end: str
    days: Optional[Sequence[str]] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"start": self.start, "end": self.end}
        if self.days:
            data["days"] = list(self.days)
        return data


@dataclass
class ShopAvailabilitySpec:
    windows: List[ShopTimeWindowSpec] = field(default_factory=list)
    timezone: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"windows": [window.to_dict() for window in self.windows]}
        if self.timezone:
            data["timezone"] = self.timezone
        return data


@dataclass
class ShopStockSpec:
    min: int = 0
    max: int = 0
    restock_seconds: int = 3600
    scope: Optional[str] = None

    def enabled(self) -> bool:
        return self.max > 0

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "min": self.min,
            "max": self.max,
            "restockSeconds": self.restock_seconds,
        }
        if self.scope:
            data["scope"] = self.scope
        return data


@dataclass
class ShopPriceModifiers:
    tier_multipliers: Dict[str, float] = field(default_factory=dict)
    rarity_multipliers: Dict[str, float] = field(default_factory=dict)
    default_tier_multiplier: float = 1.0
    default_rarity_multiplier: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.tier_multipliers:
            data["tier"] = dict(self.tier_multipliers)
        if self.rarity_multipliers:
            data["rarity"] = dict(self.rarity_multipliers)
        if self.default_tier_multiplier != 1.0:
            data["defaultTierMultiplier"] = self.default_tier_multiplier
        if self.default_rarity_multiplier != 1.0:
            data["defaultRarityMultiplier"] = self.default_rarity_multiplier
        return data


@dataclass
class ShopDynamicPriceSpec:
    mode: str = "stock"
    min_multiplier: float = 0.0
    max_multiplier: float = 0.0
    period_seconds: int = 3600

    def to_dict(self) -> Dict[str, Any]:
        return {
            "mode": self.mode,
            "minMultiplier": self.min_multiplier,
            "maxMultiplier": self.max_multiplier,
            "periodSeconds": self.period_seconds,
        }


@dataclass
class ShopPricingSpec:
    tax_rate: float = 0.0
    world_multipliers: Dict[str, float] = field(default_factory=dict)
    regions: List[ShopRegionPriceSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.tax_rate:
            data["taxRate"] = self.tax_rate
        if self.world_multipliers:
            data["worldMultipliers"] = dict(self.world_multipliers)
        if self.regions:
            data["regions"] = [region.to_dict() for region in self.regions]
        return data


@dataclass
class ShopRequirementSpec:
    requirement_type: str
    permission: Optional[str] = None
    min_level: Optional[int] = None
    min_custom_level: Optional[int] = None
    min_custom_points: Optional[int] = None
    quest_id: Optional[str] = None
    quest_status: Optional[str] = None
    classes: List[str] = field(default_factory=list)
    regions: List[ShopRegionSpec] = field(default_factory=list)
    faction_id: Optional[str] = None
    min_faction_rank: Optional[int] = None
    message: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"type": self.requirement_type}
        if self.permission:
            data["permission"] = self.permission
        if self.min_level is not None:
            data["minLevel"] = self.min_level
        if self.min_custom_level is not None:
            data["minLevel"] = self.min_custom_level
        if self.min_custom_points is not None:
            data["minPoints"] = self.min_custom_points
        if self.quest_id:
            data["quest"] = snake_case(self.quest_id)
        if self.quest_status:
            data["status"] = self.quest_status
        if self.classes:
            data["classes"] = [snake_case(value) for value in self.classes]
        if self.regions:
            data["regions"] = [region.to_dict() for region in self.regions]
        if self.faction_id:
            data["faction"] = snake_case(self.faction_id)
        if self.min_faction_rank is not None:
            data["minRank"] = self.min_faction_rank
        if self.message:
            data["message"] = self.message
        return data

    @staticmethod
    def permission_req(permission: str, message: Optional[str] = None) -> "ShopRequirementSpec":
        return ShopRequirementSpec("permission", permission=permission, message=message)

    @staticmethod
    def level_req(min_level: int, message: Optional[str] = None) -> "ShopRequirementSpec":
        return ShopRequirementSpec("level", min_level=min_level, message=message)

    @staticmethod
    def custom_xp_req(
        min_level: int,
        min_points: int,
        message: Optional[str] = None,
    ) -> "ShopRequirementSpec":
        return ShopRequirementSpec(
            "custom_xp",
            min_custom_level=min_level,
            min_custom_points=min_points,
            message=message,
        )

    @staticmethod
    def quest_req(
        quest_id: str,
        status: str = "completed",
        message: Optional[str] = None,
    ) -> "ShopRequirementSpec":
        return ShopRequirementSpec("quest", quest_id=quest_id, quest_status=status, message=message)

    @staticmethod
    def class_req(classes: Sequence[str], message: Optional[str] = None) -> "ShopRequirementSpec":
        return ShopRequirementSpec("class", classes=list(classes), message=message)

    @staticmethod
    def region_req(regions: Sequence[ShopRegionSpec], message: Optional[str] = None) -> "ShopRequirementSpec":
        return ShopRequirementSpec("region", regions=list(regions), message=message)

    @staticmethod
    def faction_req(
        faction_id: str,
        min_rank: int = 0,
        message: Optional[str] = None,
    ) -> "ShopRequirementSpec":
        return ShopRequirementSpec(
            "faction",
            faction_id=faction_id,
            min_faction_rank=min_rank,
            message=message,
        )


def shop_requirement_permission(permission: str, message: Optional[str] = None) -> ShopRequirementSpec:
    return ShopRequirementSpec.permission_req(permission, message)


def shop_requirement_level(min_level: int, message: Optional[str] = None) -> ShopRequirementSpec:
    return ShopRequirementSpec.level_req(min_level, message)


def shop_requirement_custom_xp(
    min_level: int,
    min_points: int,
    message: Optional[str] = None,
) -> ShopRequirementSpec:
    return ShopRequirementSpec.custom_xp_req(min_level, min_points, message)


def shop_requirement_quest(
    quest_id: str,
    status: str = "completed",
    message: Optional[str] = None,
) -> ShopRequirementSpec:
    return ShopRequirementSpec.quest_req(quest_id, status, message)


def shop_requirement_class(classes: Sequence[str], message: Optional[str] = None) -> ShopRequirementSpec:
    return ShopRequirementSpec.class_req(classes, message)


def shop_requirement_region(regions: Sequence[ShopRegionSpec], message: Optional[str] = None) -> ShopRequirementSpec:
    return ShopRequirementSpec.region_req(regions, message)


def shop_requirement_faction(
    faction_id: str,
    min_rank: int = 0,
    message: Optional[str] = None,
) -> ShopRequirementSpec:
    return ShopRequirementSpec.faction_req(faction_id, min_rank, message)


@dataclass
class ShopIngredientSpec:
    ingredient_type: Optional[str] = None
    item_id: Optional[str] = None
    upgrade_id: Optional[str] = None
    currency_id: Optional[str] = None
    material: Optional[Material | str] = None
    item: Optional[Any] = None
    amount: int = 1
    tag: Optional[str] = None
    category: Optional[str] = None
    matcher: Optional[ItemMatcherSpec | Mapping[str, Any]] = None
    label: Optional[str] = None
    extra: Dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.item_id:
            self.item_id = snake_case(self.item_id)
        if self.upgrade_id:
            self.upgrade_id = snake_case(self.upgrade_id)
        if self.currency_id:
            self.currency_id = snake_case(self.currency_id)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.ingredient_type:
            data["type"] = self.ingredient_type
        if self.upgrade_id:
            data["upgradeId"] = snake_case(self.upgrade_id)
        if self.currency_id:
            data["currency"] = self.currency_id
        if self.item_id:
            data["itemId"] = snake_case(self.item_id)
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.item is not None:
            data["item"] = _item_to_dict(self.item)
        if self.amount and self.amount != 1:
            data["amount"] = self.amount
        if self.tag:
            data["tag"] = self.tag
        if self.category:
            data["category"] = self.category
        if self.matcher is not None:
            data["matcher"] = _matcher_to_dict(self.matcher)
        if self.label:
            data["label"] = self.label
        if self.extra:
            data.update(dict(self.extra))
        return data


@dataclass
class ShopValueSpec:
    ingredient: ShopIngredientSpec
    value: int

    def to_dict(self) -> Dict[str, Any]:
        data = self.ingredient.to_dict()
        data["value"] = self.value
        return data


@dataclass
class ShopTokenSpec:
    marker_key: Optional[str] = None
    item: Optional[Any] = None
    material: Optional[Material | str] = None
    name: Optional[str] = None
    lore: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.marker_key:
            data["markerKey"] = self.marker_key
        if self.item is not None:
            data["item"] = _item_to_dict(self.item)
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.name:
            data["name"] = self.name
        if self.lore:
            data["lore"] = list(self.lore)
        return data


@dataclass
class ShopTokenTierSpec:
    tier_id: str
    marker_key: Optional[str] = None
    item: Optional[Any] = None
    material: Optional[Material | str] = None
    name: Optional[str] = None
    lore: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.marker_key:
            data["markerKey"] = self.marker_key
        if self.item is not None:
            data["item"] = _item_to_dict(self.item)
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.name:
            data["name"] = self.name
        if self.lore:
            data["lore"] = list(self.lore)
        return data


@dataclass
class ShopCurrencySpec:
    currency_id: str
    marker_key: Optional[str] = None
    item: Optional[Any] = None
    material: Optional[Material | str] = None
    name: Optional[str] = None
    lore: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.marker_key:
            data["markerKey"] = self.marker_key
        if self.item is not None:
            data["item"] = _item_to_dict(self.item)
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.name:
            data["name"] = self.name
        if self.lore:
            data["lore"] = list(self.lore)
        return data


@dataclass
class ShopTradeSpec:
    buys: List[ShopIngredientSpec]
    sells: List[ShopIngredientSpec]
    max_uses: Optional[int] = None
    min_level: Optional[int] = None
    requirements: List[ShopRequirementSpec] = field(default_factory=list)
    visibility: List[ShopRequirementSpec] = field(default_factory=list)
    availability: Optional[ShopAvailabilitySpec] = None
    experience_reward: Optional[bool] = None
    price_multiplier: Optional[float] = None
    preview_lore: List[str] = field(default_factory=list)
    dynamic_price: Optional[ShopDynamicPriceSpec] = None
    price_modifiers: Optional[ShopPriceModifiers] = None
    stock: Optional[ShopStockSpec] = None
    buyback: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "buy": [entry.to_dict() for entry in self.buys],
            "sell": [entry.to_dict() for entry in self.sells],
        }
        if self.max_uses is not None:
            data["maxUses"] = self.max_uses
        if self.min_level is not None:
            data["minLevel"] = self.min_level
        if self.requirements:
            data["requirements"] = [req.to_dict() for req in self.requirements]
        if self.visibility:
            data["visibility"] = [req.to_dict() for req in self.visibility]
        if self.availability is not None:
            data["availability"] = self.availability.to_dict()
        if self.experience_reward is not None:
            data["experienceReward"] = self.experience_reward
        if self.price_multiplier is not None:
            data["priceMultiplier"] = self.price_multiplier
        if self.preview_lore:
            data["previewLore"] = list(self.preview_lore)
        if self.dynamic_price is not None:
            data["dynamicPrice"] = self.dynamic_price.to_dict()
        if self.price_modifiers is not None:
            data["priceModifiers"] = self.price_modifiers.to_dict()
        if self.stock is not None:
            data["stock"] = self.stock.to_dict()
        if self.buyback is not None:
            data["buyback"] = self.buyback
        return data


@dataclass
class ShopSpec:
    shop_id: str
    title: str
    trades: List[ShopTradeSpec]
    enabled: Optional[bool] = None
    icon: Optional[ShopIngredientSpec] = None
    gui: Optional["ShopGuiPreviewSpec"] = None
    permission: Optional[str] = None
    requirements: List[ShopRequirementSpec] = field(default_factory=list)
    visibility: List[ShopRequirementSpec] = field(default_factory=list)
    availability: Optional[ShopAvailabilitySpec] = None
    cooldown_seconds: Optional[float] = None
    worlds: List[str] = field(default_factory=list)
    stock: Optional[ShopStockSpec] = None
    pricing: Optional[ShopPricingSpec] = None
    price_modifiers: Optional[ShopPriceModifiers] = None
    overrides: List[Dict[str, Any]] = field(default_factory=list)
    override_paths: List[tuple[str, Any]] = field(default_factory=list)
    override_warnings: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "title": self.title,
            "trades": [trade.to_dict() for trade in self.trades],
        }
        if self.enabled is not None:
            data["enabled"] = self.enabled
        if self.icon is not None:
            data["icon"] = self.icon.to_dict()
        if self.gui is not None:
            gui_payload = self.gui.to_dict()
            if gui_payload:
                data["gui"] = gui_payload
        if self.permission:
            data["permission"] = self.permission
        if self.requirements:
            data["requirements"] = [req.to_dict() for req in self.requirements]
        if self.visibility:
            data["visibility"] = [req.to_dict() for req in self.visibility]
        if self.availability is not None:
            data["availability"] = self.availability.to_dict()
        if self.cooldown_seconds is not None:
            data["cooldownSeconds"] = self.cooldown_seconds
        if self.worlds:
            data["worlds"] = list(self.worlds)
        if self.stock is not None:
            data["stock"] = self.stock.to_dict()
        if self.pricing is not None:
            data["pricing"] = self.pricing.to_dict()
        if self.price_modifiers is not None:
            data["priceModifiers"] = self.price_modifiers.to_dict()
        if self.overrides or self.override_paths:
            apply_overrides(data, self.overrides, self.override_paths)
        return data


@dataclass
class ShopDocument:
    shops: List[ShopSpec] = field(default_factory=list)
    token: Optional[ShopTokenSpec] = None
    token_tiers: List[ShopTokenTierSpec] = field(default_factory=list)
    currencies: List[ShopCurrencySpec] = field(default_factory=list)
    values: List[ShopValueSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.token is not None:
            data["token"] = self.token.to_dict()
        if self.token_tiers:
            data["tokenTiers"] = {tier.tier_id: tier.to_dict() for tier in self.token_tiers}
        if self.currencies:
            data["currencies"] = {currency.currency_id: currency.to_dict() for currency in self.currencies}
        if self.values:
            data["values"] = [value.to_dict() for value in self.values]
        data["shops"] = {shop.shop_id: shop.to_dict() for shop in self.shops}
        return data


def shop_icon_item_id(item_id: str, amount: int = 1, label: Optional[str] = None) -> ShopIngredientSpec:
    return ShopIngredientSpec(ingredient_type="item_id", item_id=item_id, amount=amount, label=label)


def shop_icon_head(head_id: str, amount: int = 1, label: Optional[str] = None) -> ShopIngredientSpec:
    return ShopIngredientSpec(ingredient_type="item_id", item_id=head_id, amount=amount, label=label)


def shop_icon_material(material: Material | str, amount: int = 1, label: Optional[str] = None) -> ShopIngredientSpec:
    return ShopIngredientSpec(ingredient_type="material", material=material, amount=amount, label=label)


def shop_icon_currency(currency_id: str, amount: int = 1, label: Optional[str] = None) -> ShopIngredientSpec:
    return ShopIngredientSpec(ingredient_type="currency", currency_id=currency_id, amount=amount, label=label)


def shop_icon_token(amount: int = 1, label: Optional[str] = None) -> ShopIngredientSpec:
    return ShopIngredientSpec(ingredient_type="token", amount=amount, label=label)


class ShopExporter(ExporterBase):
    def write_shops(self, shops: Iterable[ShopSpec], filename: str = "shops.yml") -> str:
        data = {"shops": {shop.shop_id: shop.to_dict() for shop in shops}}
        return self.write_yaml(filename, data)

    def write_document(self, doc: ShopDocument, filename: str = "shops.yml") -> str:
        return self.write_yaml(filename, doc.to_dict())


def upgrade_vendor(shop_id: str, title: str, upgrade_ids: List[str], min_level: int) -> ShopSpec:
    trades: List[ShopTradeSpec] = []
    for upgrade_id in upgrade_ids:
        trades.append(
            ShopTradeSpec(
                buys=[ShopIngredientSpec(ingredient_type="token", amount=1)],
                sells=[ShopIngredientSpec(ingredient_type="item_id", upgrade_id=upgrade_id, amount=1)],
                min_level=min_level,
            )
        )
    return ShopSpec(shop_id=shop_id, title=title, trades=trades)


def sample_shops_pack() -> ShopDocument:
    upgrade_shop = ShopSpec(
        shop_id="upgrades_basic",
        title="<gold>Upgrade Vendor</gold>",
        icon=shop_icon_head("ICON_UPGRADES"),
        trades=[
            ShopTradeSpec(
                buys=[ShopIngredientSpec(ingredient_type="token", amount=8)],
                sells=[ShopIngredientSpec(ingredient_type="item_id", item_id="upgrade_rune_fire", amount=1)],
                min_level=5,
                preview_lore=["<gray>Basic fire rune.</gray>"],
            )
        ],
    )
    gear_shop = ShopSpec(
        shop_id="gear_basic",
        title="<gold>Gear Merchant</gold>",
        icon=shop_icon_head("ICON_ITEMS"),
        trades=[
            ShopTradeSpec(
                buys=[ShopIngredientSpec(ingredient_type="token", amount=6)],
                sells=[ShopIngredientSpec(ingredient_type="item_id", item_id="gear_basic_sword", amount=1)],
                preview_lore=["<gray>Starter sword.</gray>"],
            ),
            ShopTradeSpec(
                buys=[ShopIngredientSpec(ingredient_type="token", amount=10)],
                sells=[ShopIngredientSpec(ingredient_type="item_id", item_id="gear_basic_armor", amount=1)],
                preview_lore=["<gray>Starter armor.</gray>"],
            ),
        ],
    )
    consumable_shop = ShopSpec(
        shop_id="consumables",
        title="<gold>Consumables</gold>",
        icon=shop_icon_head("ICON_CONSUMABLES"),
        trades=[
            ShopTradeSpec(
                buys=[ShopIngredientSpec(ingredient_type="token", amount=2)],
                sells=[ShopIngredientSpec(ingredient_type="item_id", item_id="consumable_health_potion", amount=1)],
                preview_lore=["<gray>Restores health.</gray>"],
            )
        ],
    )
    return ShopDocument(shops=[upgrade_shop, gear_shop, consumable_shop])


@dataclass
class ShopGuiPreviewSpec:
    head: Optional[str] = None
    icon: Optional[str] = None
    title: Optional[str] = None
    title_key: Optional[str] = None
    description: Optional[str] = None
    description_key: Optional[str] = None
    summary: List[str] = field(default_factory=list)
    summary_keys: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.head:
            data["head"] = self.head
        if self.icon:
            data["icon"] = self.icon
        if self.title:
            data["title"] = self.title
        if self.title_key:
            data["titleKey"] = self.title_key
        if self.description:
            data["description"] = self.description
        if self.description_key:
            data["descriptionKey"] = self.description_key
        if self.summary:
            data["summary"] = list(self.summary)
        if self.summary_keys:
            data["summaryKeys"] = list(self.summary_keys)
        return data


def shop_gui_preview(tile: GuiTileSpec) -> ShopGuiPreviewSpec:
    return ShopGuiPreviewSpec(
        head=tile.head,
        icon=tile.icon,
        title=tile.title,
        title_key=tile.title_key,
        description=tile.description,
        description_key=tile.description_key,
        summary=list(tile.summary),
        summary_keys=list(tile.summary_keys),
    )


def item(item_id: str, amount: int = 1) -> ShopIngredientSpec:
    return ShopIngredientSpec(ingredient_type="item_id", item_id=item_id, amount=amount)


def currency(currency_id: str, amount: int) -> ShopIngredientSpec:
    return ShopIngredientSpec(ingredient_type="currency", currency_id=currency_id, amount=amount)


class ShopBuilder(BuilderBase):
    def __init__(self, shop_id: str, title: Optional[str] = None) -> None:
        super().__init__(_id=shop_id)
        self._title = title or shop_id
        self._trades: List[ShopTradeSpec] = []
        self._requirements: List[ShopRequirementSpec] = []

    def title(self, title: str) -> "ShopBuilder":
        self._title = title
        return self

    def trade(
        self,
        sell: Any,
        cost_tokens: Optional[int] = None,
        cost_gold: Optional[int] = None,
        costs: Optional[List[ShopIngredientSpec]] = None,
    ) -> "ShopBuilder":
        sells = [_as_ingredient(sell)]
        buys: List[ShopIngredientSpec] = []
        if cost_tokens is not None:
            buys.append(currency("tokens", cost_tokens))
        if cost_gold is not None:
            buys.append(currency("gold", cost_gold))
        if costs:
            buys.extend(costs)
        self._trades.append(ShopTradeSpec(buys=buys, sells=sells))
        return self

    def requirement(self, requirement: ShopRequirementSpec) -> "ShopBuilder":
        self._requirements.append(requirement)
        return self

    def build_spec(self) -> ShopSpec:
        self._ensure_id("shop_id")
        if not self._title:
            self._ensure_name()
            self._title = self._name
        return ShopSpec(
            shop_id=self._id or "",
            title=self._title or (self._id or ""),
            trades=self._trades,
            requirements=self._requirements,
            overrides=[mapping for mapping, _ in self._raw_overrides],
            override_paths=[(path, value) for path, value, _ in self._path_overrides],
            override_warnings=self._format_override_warnings(f"shop:{self._id}"),
        )


def Shop(shop_id: str, title: Optional[str] = None) -> ShopBuilder:
    return ShopBuilder(shop_id, title=title)


def _as_ingredient(value: Any) -> ShopIngredientSpec:
    from .items import ItemBuilder

    if isinstance(value, ShopIngredientSpec):
        if value.ingredient_type is None:
            if value.item_id:
                value.ingredient_type = "item_id"
            elif value.currency_id:
                value.ingredient_type = "currency"
        return value
    if isinstance(value, ItemBuilder):
        return ShopIngredientSpec(ingredient_type="item_id", item_id=value._id)
    if isinstance(value, str):
        return ShopIngredientSpec(ingredient_type="item_id", item_id=value)
    if isinstance(value, dict):
        return ShopIngredientSpec(item=value)
    raise ValueError("Unsupported shop ingredient type")
