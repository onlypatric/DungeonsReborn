"""Crafting builder (recipes, variants, costs, requirements)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence

from .base import ExporterBase, snake_case
from .vanilla import EnumValue, Material, normalize_enum_name


def _enum_or_str(value: Any, label: str) -> str:
    if isinstance(value, EnumValue):
        return normalize_enum_name(value.name)
    if isinstance(value, str):
        return value
    raise ValueError(f"{label} must be provided as an enum value or string")


@dataclass
class CraftingIngredientSpec:
    type: Optional[str] = None
    item_id: Optional[str] = None
    upgrade_id: Optional[str] = None
    tag: Optional[str] = None
    material: Optional[Material | str] = None
    category: Optional[str] = None
    amount: int = 1
    predicate: Optional[Mapping[str, Any]] = None
    return_item: Optional[Mapping[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.type:
            data["type"] = self.type
        if self.item_id:
            data["item"] = snake_case(self.item_id)
        if self.upgrade_id:
            data["upgradeId"] = snake_case(self.upgrade_id)
        if self.tag:
            data["tag"] = self.tag
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.category:
            data["category"] = self.category
        if self.amount and self.amount != 1:
            data["amount"] = self.amount
        if self.predicate:
            data.update(dict(self.predicate))
        if self.return_item:
            data["return"] = dict(self.return_item)
        return data


@dataclass
class CraftingSlotIngredientSpec:
    slot: int
    ingredient: CraftingIngredientSpec

    def to_dict(self) -> Dict[str, Any]:
        data = self.ingredient.to_dict()
        data["slot"] = self.slot
        return data


@dataclass
class CraftingGridSpec:
    pattern: List[str]
    keys: Dict[str, CraftingIngredientSpec] = field(default_factory=dict)
    mirror: bool = False
    rotate: bool = False
    width: Optional[int] = None
    height: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"pattern": list(self.pattern)}
        if self.width is not None:
            data["width"] = self.width
        if self.height is not None:
            data["height"] = self.height
        if self.mirror:
            data["mirror"] = True
        if self.rotate:
            data["rotate"] = True
        if self.keys:
            data["key"] = {key: spec.to_dict() for key, spec in self.keys.items()}
        return data


@dataclass
class CraftingRecipeVariant:
    inputs: List[CraftingIngredientSpec] = field(default_factory=list)
    slots: List[CraftingSlotIngredientSpec] = field(default_factory=list)
    grid: Optional[CraftingGridSpec] = None
    strict: bool = False
    allow_overflow: Optional[bool] = None
    priority: int = 0

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.inputs:
            data["inputs"] = [entry.to_dict() for entry in self.inputs]
        if self.slots:
            data["slots"] = [entry.to_dict() for entry in self.slots]
        if self.grid is not None:
            data["grid"] = self.grid.to_dict()
        if self.strict:
            data["strict"] = True
        if self.allow_overflow is not None:
            data["allowOverflow"] = self.allow_overflow
        if self.priority:
            data["priority"] = self.priority
        return data


@dataclass
class CraftingOutputSpec:
    item_id: Optional[str] = None
    material: Optional[Material | str] = None
    amount: int = 1
    min_amount: Optional[int] = None
    max_amount: Optional[int] = None
    chance: Optional[float] = None
    pool: Optional[str] = None
    weight: Optional[int] = None
    byproduct: Optional[bool] = None
    scale: Optional[Any] = None
    mutation: Optional[Mapping[str, Any]] = None
    item_stack: Optional[Any] = None
    template: Optional[Mapping[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.item_stack is not None:
            data["itemStack"] = self.item_stack
        if self.template is not None:
            data["template"] = dict(self.template)
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.item_id is not None:
            data["item"] = snake_case(self.item_id)
        if self.min_amount is not None or self.max_amount is not None:
            amount: Dict[str, Any] = {}
            if self.min_amount is not None:
                amount["min"] = self.min_amount
            if self.max_amount is not None:
                amount["max"] = self.max_amount
            data["amount"] = amount
        elif self.amount and self.amount != 1:
            data["amount"] = self.amount
        if self.chance is not None:
            data["chance"] = self.chance
        if self.pool is not None:
            data["pool"] = self.pool
        if self.weight is not None:
            data["weight"] = self.weight
        if self.byproduct is not None:
            data["byproduct"] = self.byproduct
        if self.scale is not None:
            if isinstance(self.scale, OutputScaleSpec):
                data["scale"] = self.scale.to_dict()
            else:
                data["scale"] = self.scale
        if self.mutation is not None:
            data["mutation"] = (
                self.mutation.to_dict() if isinstance(self.mutation, OutputMutationSpec) else dict(self.mutation)
            )
        return data


@dataclass
class CraftingRequirementSpec:
    type: str
    message: Optional[str] = None
    permission: Optional[str] = None
    min_level: Optional[int] = None
    min_points: Optional[int] = None
    quest_id: Optional[str] = None
    quest_status: Optional[str] = None
    class_ids: List[str] = field(default_factory=list)
    regions: List[Mapping[str, Any]] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"type": self.type}
        if self.message is not None:
            data["message"] = self.message
        if self.permission is not None:
            data["permission"] = self.permission
        if self.min_level is not None:
            data["minLevel"] = self.min_level
        if self.min_points is not None:
            data["minPoints"] = self.min_points
        if self.quest_id is not None:
            data["quest"] = self.quest_id
        if self.quest_status is not None:
            data["status"] = self.quest_status
        if self.class_ids:
            data["classes"] = list(self.class_ids)
        if self.regions:
            data["regions"] = [dict(region) for region in self.regions]
        return data

    @staticmethod
    def permission(permission: str, message: Optional[str] = None) -> "CraftingRequirementSpec":
        return CraftingRequirementSpec(type="permission", permission=permission, message=message)

    @staticmethod
    def level(min_level: int, message: Optional[str] = None) -> "CraftingRequirementSpec":
        return CraftingRequirementSpec(type="level", min_level=min_level, message=message)

    @staticmethod
    def custom_xp(min_level: int, min_points: int, message: Optional[str] = None) -> "CraftingRequirementSpec":
        return CraftingRequirementSpec(
            type="custom_xp",
            min_level=min_level,
            min_points=min_points,
            message=message,
        )

    @staticmethod
    def quest(quest_id: str, status: str, message: Optional[str] = None) -> "CraftingRequirementSpec":
        return CraftingRequirementSpec(type="quest", quest_id=quest_id, quest_status=status, message=message)

    @staticmethod
    def classes(class_ids: List[str], message: Optional[str] = None) -> "CraftingRequirementSpec":
        return CraftingRequirementSpec(type="class", class_ids=list(class_ids), message=message)

    @staticmethod
    def region(regions: List[Mapping[str, Any]], message: Optional[str] = None) -> "CraftingRequirementSpec":
        return CraftingRequirementSpec(type="region", regions=list(regions), message=message)


@dataclass
class CraftingCostSpec:
    type: str
    message: Optional[str] = None
    amount: Optional[float] = None
    resource: Optional[str] = None
    token_tier: Optional[str] = None
    allow_break: Optional[bool] = None
    ingredient: Optional[CraftingIngredientSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"type": self.type}
        if self.message is not None:
            data["message"] = self.message
        if self.amount is not None:
            data["amount"] = self.amount
        if self.resource is not None:
            data["resource"] = self.resource
        if self.token_tier is not None:
            data["tokenTier"] = self.token_tier
        if self.allow_break is not None:
            data["allowBreak"] = self.allow_break
        if self.ingredient is not None:
            data["item"] = self.ingredient.to_dict()
        return data

    @staticmethod
    def mana(amount: float, message: Optional[str] = None) -> "CraftingCostSpec":
        return CraftingCostSpec(type="mana", amount=amount, message=message)

    @staticmethod
    def resource(resource: str, amount: float, message: Optional[str] = None) -> "CraftingCostSpec":
        return CraftingCostSpec(type="resource", resource=resource, amount=amount, message=message)

    @staticmethod
    def tokens(token_tier: str, amount: float, message: Optional[str] = None) -> "CraftingCostSpec":
        return CraftingCostSpec(type="tokens", token_tier=token_tier, amount=amount, message=message)

    @staticmethod
    def durability(amount: float, allow_break: bool = False, message: Optional[str] = None) -> "CraftingCostSpec":
        return CraftingCostSpec(type="durability", amount=amount, allow_break=allow_break, message=message)

    @staticmethod
    def item(ingredient: CraftingIngredientSpec, message: Optional[str] = None) -> "CraftingCostSpec":
        return CraftingCostSpec(type="item", ingredient=ingredient, message=message)


@dataclass
class CraftingScriptSpec:
    file: Optional[str] = None
    inline: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        if self.file and self.inline:
            return {"file": self.file, "code": self.inline}
        if self.file:
            return {"file": self.file}
        if self.inline:
            return {"code": self.inline}
        return {}


@dataclass
class CraftingHookEntry:
    file: Optional[str] = None
    inline: Optional[str] = None
    abilities: List[str] = field(default_factory=list)
    deny: bool = False
    message: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.file:
            data["file"] = self.file
        if self.inline:
            data["code"] = self.inline
        if self.abilities:
            data["abilities"] = list(self.abilities)
        if self.deny:
            data["deny"] = True
        if self.message is not None:
            data["message"] = self.message
        return data


@dataclass
class CraftingHookSpec:
    pre: Optional[CraftingHookEntry] = None
    post: Optional[CraftingHookEntry] = None
    preview: Optional[CraftingHookEntry] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.pre:
            data["pre"] = self.pre.to_dict()
        if self.post:
            data["post"] = self.post.to_dict()
        if self.preview:
            data["preview"] = self.preview.to_dict()
        return data


@dataclass
class CraftingDiscoverySpec:
    hidden: bool = False
    requires: List[str] = field(default_factory=list)
    grants: List[str] = field(default_factory=list)
    unlock_on_craft: bool = False
    research_seconds: int = 0
    unlock_quests: List[str] = field(default_factory=list)
    unlock_drop_item_ids: List[str] = field(default_factory=list)
    unlock_drop_materials: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.hidden:
            data["hidden"] = True
        if self.requires:
            data["requires"] = list(self.requires)
        if self.grants:
            data["grants"] = list(self.grants)
        if self.unlock_on_craft:
            data["unlockOnCraft"] = True
        if self.research_seconds:
            data["researchSeconds"] = self.research_seconds
        unlock: Dict[str, Any] = {}
        if self.unlock_quests:
            unlock["quests"] = [snake_case(q) for q in self.unlock_quests]
        if self.unlock_drop_item_ids or self.unlock_drop_materials:
            unlock["drops"] = {}
            if self.unlock_drop_item_ids:
                unlock["drops"]["itemIds"] = [snake_case(item_id) for item_id in self.unlock_drop_item_ids]
            if self.unlock_drop_materials:
                unlock["drops"]["materials"] = list(self.unlock_drop_materials)
        if unlock:
            data["unlock"] = unlock
        return data


@dataclass
class CraftingGuiPreviewSpec:
    head: Optional[str] = None
    icon: Optional[str] = None
    title_key: Optional[str] = None
    description_key: Optional[str] = None
    summary_keys: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.head:
            data["head"] = self.head
        if self.icon:
            data["icon"] = self.icon
        if self.title_key:
            data["titleKey"] = self.title_key
        if self.description_key:
            data["descriptionKey"] = self.description_key
        if self.summary_keys:
            data["summaryKeys"] = list(self.summary_keys)
        return data


@dataclass
class CraftingRecipeSpec:
    recipe_id: str
    name: Optional[str] = None
    description: Optional[str] = None
    permissions: List[str] = field(default_factory=list)
    cooldown_seconds: float = 0.0
    requirements: List[CraftingRequirementSpec] = field(default_factory=list)
    costs: List[CraftingCostSpec] = field(default_factory=list)
    variants: List[CraftingRecipeVariant] = field(default_factory=list)
    outputs: List[CraftingOutputSpec] = field(default_factory=list)
    hooks: Optional[CraftingHookSpec] = None
    discovery: Optional[CraftingDiscoverySpec] = None
    script: Optional[CraftingScriptSpec] = None
    gui: Optional[CraftingGuiPreviewSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"id": snake_case(self.recipe_id)}
        if self.name:
            data["name"] = self.name
        if self.description:
            data["description"] = self.description
        if self.permissions:
            data["permissions"] = list(self.permissions)
        if self.cooldown_seconds:
            data["cooldownSeconds"] = self.cooldown_seconds
        if self.requirements:
            data["requirements"] = [req.to_dict() for req in self.requirements]
        if self.costs:
            data["costs"] = [cost.to_dict() for cost in self.costs]
        if self.variants:
            data["variants"] = [variant.to_dict() for variant in self.variants]
        if self.outputs:
            data["outputs"] = [output.to_dict() for output in self.outputs]
        if self.hooks:
            hooks = self.hooks.to_dict()
            if hooks:
                data["hooks"] = hooks
        if self.discovery:
            discovery = self.discovery.to_dict()
            if discovery:
                data["discovery"] = discovery
        if self.script:
            script = self.script.to_dict()
            if script:
                data["script"] = script
        if self.gui:
            gui = self.gui.to_dict()
            if gui:
                data["gui"] = gui
        return data


@dataclass
class OutputScaleRule:
    permission: Optional[str] = None
    multiplier: float = 1.0
    add: int = 0

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.permission:
            data["permission"] = self.permission
        if self.multiplier != 1.0:
            data["multiplier"] = self.multiplier
        if self.add:
            data["add"] = self.add
        return data


@dataclass
class OutputScaleSpec:
    rules: List[OutputScaleRule] = field(default_factory=list)
    per_permission: Dict[str, float] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.per_permission:
            data["per_permission"] = dict(self.per_permission)
        if self.rules:
            data_list = [rule.to_dict() for rule in self.rules if rule.to_dict()]
            if data_list:
                data["rules"] = data_list
        return data


@dataclass
class OutputMutationSpec:
    name: Optional[str] = None
    lore: List[str] = field(default_factory=list)
    lore_add: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.name:
            data["name"] = self.name
        if self.lore:
            data["lore"] = list(self.lore)
        if self.lore_add:
            data["lore_add"] = list(self.lore_add)
        return data


@dataclass
class AttributeRequirement:
    attribute: str
    min: Optional[float] = None
    max: Optional[float] = None
    operation: Optional[str] = None
    slot_group: Optional[str] = None
    slot: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"attribute": self.attribute}
        if self.min is not None:
            data["min"] = self.min
        if self.max is not None:
            data["max"] = self.max
        if self.operation is not None:
            data["operation"] = self.operation
        if self.slot_group is not None:
            data["slotGroup"] = self.slot_group
        if self.slot is not None:
            data["slot"] = self.slot
        return data


@dataclass
class DurabilityRequirement:
    min_remaining: Optional[int] = None
    max_remaining: Optional[int] = None
    min_damage: Optional[int] = None
    max_damage: Optional[int] = None
    min_percent: Optional[float] = None
    max_percent: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.min_remaining is not None:
            data["min_remaining"] = self.min_remaining
        if self.max_remaining is not None:
            data["max_remaining"] = self.max_remaining
        if self.min_damage is not None:
            data["min_damage"] = self.min_damage
        if self.max_damage is not None:
            data["max_damage"] = self.max_damage
        if self.min_percent is not None:
            data["min_percent"] = self.min_percent
        if self.max_percent is not None:
            data["max_percent"] = self.max_percent
        return data


@dataclass
class PotionEffectRequirement:
    type: str
    min_amplifier: Optional[int] = None
    max_amplifier: Optional[int] = None
    min_duration: Optional[int] = None
    max_duration: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"type": self.type}
        if self.min_amplifier is not None:
            data["minAmplifier"] = self.min_amplifier
        if self.max_amplifier is not None:
            data["maxAmplifier"] = self.max_amplifier
        if self.min_duration is not None:
            data["minDuration"] = self.min_duration
        if self.max_duration is not None:
            data["maxDuration"] = self.max_duration
        return data


@dataclass
class PotionRequirement:
    type: Optional[str] = None
    effects: List[PotionEffectRequirement] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.type:
            data["type"] = self.type
        if self.effects:
            data["effects"] = [effect.to_dict() for effect in self.effects]
        return data


@dataclass
class PdcRequirement:
    key: str
    type: Optional[str] = None
    value: Optional[Any] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"key": self.key}
        if self.type is not None:
            data["type"] = self.type
        if self.value is not None:
            data["value"] = self.value
        return data


def predicate_custom_model_data(
    exact: Optional[int] = None,
    min: Optional[int] = None,
    max: Optional[int] = None,
) -> Dict[str, Any]:
    if exact is not None:
        return {"custom_model_data": exact}
    data: Dict[str, Any] = {}
    if min is not None:
        data["min"] = min
    if max is not None:
        data["max"] = max
    return {"custom_model_data": data} if data else {}


def predicate_name_plain(value: str) -> Dict[str, Any]:
    return {"name": value}


def predicate_name_mini(value: str) -> Dict[str, Any]:
    return {"name_mm": value}


def predicate_lore_contains(lines: Sequence[str]) -> Dict[str, Any]:
    return {"lore_contains": list(lines)}


def predicate_lore_exact(lines: Sequence[str]) -> Dict[str, Any]:
    return {"lore_exact": list(lines)}


def predicate_enchant_all(enchantments: Mapping[str, int]) -> Dict[str, Any]:
    return {"enchantments": dict(enchantments)}


def predicate_enchant_any(enchantments: Mapping[str, int]) -> Dict[str, Any]:
    return {"enchantments_any": dict(enchantments)}


def predicate_attributes(requirements: Sequence[AttributeRequirement]) -> Dict[str, Any]:
    return {"attributes": [req.to_dict() for req in requirements]}


def predicate_durability(requirement: DurabilityRequirement) -> Dict[str, Any]:
    return {"durability": requirement.to_dict()}


def predicate_potion(requirement: PotionRequirement) -> Dict[str, Any]:
    return {"potion": requirement.to_dict()}


def predicate_pdc(requirements: Sequence[PdcRequirement]) -> Dict[str, Any]:
    return {"pdc": [req.to_dict() for req in requirements]}


def predicate_all_of(predicates: Sequence[Mapping[str, Any]]) -> Dict[str, Any]:
    return {"all_of": [dict(entry) for entry in predicates]}


def predicate_any_of(predicates: Sequence[Mapping[str, Any]]) -> Dict[str, Any]:
    return {"any_of": [dict(entry) for entry in predicates]}


def predicate_not(predicate: Mapping[str, Any]) -> Dict[str, Any]:
    return {"not": dict(predicate)}


def merge_predicates(*predicates: Mapping[str, Any]) -> Dict[str, Any]:
    data: Dict[str, Any] = {}
    for entry in predicates:
        data.update(entry)
    return data


class CraftingExporter(ExporterBase):
    def write_recipe(self, recipe: CraftingRecipeSpec, filename: Optional[str] = None) -> str:
        data: Dict[str, Any] = {"schemaVersion": 1}
        data.update(recipe.to_dict())
        name = filename or f"{recipe.recipe_id}.yml"
        return self.write_yaml(name, data)

    def write_recipes(self, recipes: Iterable[CraftingRecipeSpec]) -> List[str]:
        filenames: List[str] = []
        for recipe in recipes:
            filenames.append(self.write_recipe(recipe))
        return filenames


def ingredient_item(item_id: str, amount: int = 1) -> CraftingIngredientSpec:
    return CraftingIngredientSpec(item_id=item_id, amount=amount)


def ingredient_upgrade(upgrade_id: str, amount: int = 1) -> CraftingIngredientSpec:
    return CraftingIngredientSpec(upgrade_id=upgrade_id, amount=amount)


def ingredient_tag(tag: str, amount: int = 1) -> CraftingIngredientSpec:
    return CraftingIngredientSpec(tag=tag, amount=amount)


def ingredient_material(material: Material | str, amount: int = 1) -> CraftingIngredientSpec:
    return CraftingIngredientSpec(material=material, amount=amount)


def ingredient_category(category: str, amount: int = 1) -> CraftingIngredientSpec:
    return CraftingIngredientSpec(category=category, amount=amount)


def output_item(item_id: str, amount: int = 1) -> CraftingOutputSpec:
    return CraftingOutputSpec(item_id=item_id, amount=amount)


def output_material(material: Material | str, amount: int = 1) -> CraftingOutputSpec:
    return CraftingOutputSpec(material=material, amount=amount)


def output_item_stack(item_stack: Any) -> CraftingOutputSpec:
    return CraftingOutputSpec(item_stack=item_stack)


def output_template(template: Mapping[str, Any]) -> CraftingOutputSpec:
    return CraftingOutputSpec(template=dict(template))


def output_amount_range(min_amount: int, max_amount: int) -> Dict[str, Any]:
    return {"min": min_amount, "max": max_amount}


def basic_recipe_pack(prefix: str = "recipe") -> List[CraftingRecipeSpec]:
    recipes: List[CraftingRecipeSpec] = []
    recipe_id = f"{prefix}_basic_shaped"
    ingredient = ingredient_material(Material.COBBLESTONE, 1)
    grid = CraftingGridSpec(pattern=["AA", "AA"], keys={"A": ingredient})
    output = output_material(Material.STONE, 1)
    recipes.append(
        CraftingRecipeSpec(
            recipe_id=recipe_id,
            name="Basic Shaped Recipe",
            description="Example shaped recipe.",
            variants=[CraftingRecipeVariant(grid=grid, strict=True)],
            outputs=[output],
            discovery=CraftingDiscoverySpec(unlock_on_craft=True),
            gui=CraftingGuiPreviewSpec(icon="ICON_CRAFTING", head="ICON_CRAFTING", title_key="gui.crafting.example.title"),
        )
    )
    recipe_id = f"{prefix}_basic_shapeless"
    output = output_material(Material.TORCH, 4)
    recipes.append(
        CraftingRecipeSpec(
            recipe_id=recipe_id,
            name="Basic Shapeless Recipe",
            description="Example shapeless recipe.",
            variants=[
                CraftingRecipeVariant(
                    inputs=[
                        ingredient_material(Material.COAL, 1),
                        ingredient_material(Material.STICK, 1),
                    ],
                )
            ],
            outputs=[output],
            discovery=CraftingDiscoverySpec(unlock_on_craft=True),
            gui=CraftingGuiPreviewSpec(icon="ICON_CRAFTING", head="ICON_CRAFTING", title_key="gui.crafting.example.title"),
        )
    )
    return recipes
