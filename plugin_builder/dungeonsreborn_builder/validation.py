"""Lightweight validation helpers for builder outputs."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Iterable, List, Optional, Sequence

from .effects import Ability, EffectsDocument
from .crafting import CraftingRecipeSpec
from .items import (
    ItemBuilder,
    ItemHookType,
    MetaSpec,
    _enum_or_str,
)
from .mobs import MobBuilder, MobLocomotionMode
from .quests import (
    QuestDocument,
    QuestObjectiveGroup,
    QuestObjectiveSpec,
    QuestRewardEntrySpec,
    QuestRewardPoolSpec,
    QuestRewardScalingSpec,
    QuestRewardTitleSpec,
    QuestRewardsSpec,
    QuestSpec,
)
from .shops import (
    ShopAvailabilitySpec,
    ShopDocument,
    ShopDynamicPriceSpec,
    ShopIngredientSpec,
    ShopPriceModifiers,
    ShopPricingSpec,
    ShopRequirementSpec,
    ShopSpec,
    ShopStockSpec,
    ShopTradeSpec,
)
from .utils import suggest_values
from .families import FamilyTemplateBase
from .presets import BiomePreset


class Severity(Enum):
    ERROR = "error"
    WARN = "warn"
    INFO = "info"


@dataclass(frozen=True)
class ValidationIssue:
    path: str
    message: str
    severity: Severity = Severity.ERROR
    hint: Optional[str] = None
    suggestion: Optional[str] = None


@dataclass
class ValidationReport:
    issues: List[ValidationIssue] = field(default_factory=list)

    def add(self, issue: ValidationIssue) -> None:
        self.issues.append(issue)

    def extend(self, issues: Iterable[ValidationIssue]) -> None:
        self.issues.extend(list(issues))

    def has_errors(self) -> bool:
        return any(issue.severity == Severity.ERROR for issue in self.issues)

    def warnings(self) -> List[ValidationIssue]:
        return [issue for issue in self.issues if issue.severity == Severity.WARN]

    def errors(self) -> List[ValidationIssue]:
        return [issue for issue in self.issues if issue.severity == Severity.ERROR]


def _capture(validation_callable, path: str, issues: List[ValidationIssue]) -> None:
    try:
        validation_callable()
    except Exception as exc:  # noqa: BLE001 - surface builder errors as issues
        issues.append(ValidationIssue(path=path, message=str(exc), severity=Severity.ERROR))


def validate_ability(ability: Ability) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    _capture(ability.validate, f"ability:{ability.ability_id}", issues)
    return issues


def validate_effects_document(document: EffectsDocument) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if not document.abilities:
        issues.append(
            ValidationIssue(
                path="effects",
                message="EffectsDocument must include at least one ability",
                severity=Severity.ERROR,
            )
        )
        return issues
    for ability in document.abilities:
        issues.extend(validate_ability(ability))
    return issues


def validate_item(builder: ItemBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []

    def _validate_item() -> None:
        builder.build()

    _capture(_validate_item, f"item:{builder._id or '<missing>'}", issues)
    issues.extend(_validate_item_bindings(builder))
    issues.extend(_validate_item_behaviors(builder))
    issues.extend(_validate_item_meta(builder))
    issues.extend(_warn_item_best_practices(builder))
    return issues


def _warn_item_best_practices(builder: ItemBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    path = f"item:{builder._id or '<missing>'}"
    if builder._display is None and builder._name is None:
        issues.append(
            ValidationIssue(
                path,
                "item has no display name; UI may look unfinished",
                severity=Severity.WARN,
                hint="Set .name(...) or .display_name(...)",
            )
        )
    return issues


def validate_mob(builder: MobBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []

    def _validate_mob() -> None:
        builder.build()

    _capture(_validate_mob, f"mob:{builder._id or '<missing>'}", issues)
    issues.extend(_validate_mob_stats(builder))
    issues.extend(_validate_mob_attacks(builder))
    issues.extend(_validate_mob_ai(builder))
    issues.extend(_warn_mob_best_practices(builder))
    return issues


def _warn_mob_best_practices(builder: MobBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    path = f"mob:{builder._id or '<missing>'}"
    if builder._attacks and builder._loot is None:
        issues.append(
            ValidationIssue(
                path,
                "mob has attacks but no loot; consider adding drops or rewards",
                severity=Severity.WARN,
            )
        )
    if builder._bossbar is not None and not builder._show_name:
        issues.append(
            ValidationIssue(
                path,
                "bossbar configured but showName is false",
                severity=Severity.WARN,
            )
        )
    return issues


def validate_shop_document(document: ShopDocument) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if not document.shops:
        issues.append(
            ValidationIssue(
                path="shops",
                message="ShopDocument must include at least one shop",
                severity=Severity.ERROR,
            )
        )
        return issues
    for shop in document.shops:
        issues.extend(validate_shop(shop))
    return issues


def validate_quest_document(document: QuestDocument) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if not document.quests:
        issues.append(
            ValidationIssue(
                path="quests",
                message="QuestDocument must include at least one quest",
                severity=Severity.ERROR,
            )
        )
        return issues
    for quest in document.quests:
        issues.extend(validate_quest(quest))
    return issues


def validate_family_template(template: FamilyTemplateBase) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []

    def _validate() -> None:
        template.validate()

    _capture(_validate, f"family:{template.template_id or '<missing>'}", issues)
    return issues


def validate_biome_preset(preset: BiomePreset) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []

    def _validate() -> None:
        preset.validate()

    _capture(_validate, f"preset:{preset.biome_id or '<missing>'}", issues)
    return issues


def validate_quest(quest: QuestSpec) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    path = f"quest:{quest.quest_id or '<missing>'}"
    if not quest.quest_id:
        issues.append(ValidationIssue(path, "quest id is required"))
    if not quest.name:
        issues.append(ValidationIssue(path, "quest name is required"))
    if not quest.objectives:
        issues.append(ValidationIssue(path, "quest must include at least one objective"))
    for idx, objective in enumerate(quest.objectives):
        issues.extend(_validate_objective(objective, f"{path}.objectives[{idx}]"))
    if quest.rewards is not None:
        issues.extend(_validate_rewards(quest.rewards, f"{path}.rewards"))
    if quest.enabled and not quest.objectives:
        issues.append(
            ValidationIssue(
                path,
                "quest has no objectives",
                severity=Severity.ERROR,
            )
        )
    if quest.rewards is None:
        issues.append(
            ValidationIssue(
                path,
                "quest has no rewards configured",
                severity=Severity.WARN,
            )
        )
    return issues


def _validate_objective(objective: QuestObjectiveSpec | QuestObjectiveGroup, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if isinstance(objective, QuestObjectiveGroup):
        if not objective.objectives:
            issues.append(ValidationIssue(path, "objective group must include at least one objective"))
        if objective.mode not in {"all_of", "any_of", "sequence", "optional"}:
            issues.append(ValidationIssue(path, f"unknown objective group mode {objective.mode}"))
        for idx, entry in enumerate(objective.objectives):
            issues.extend(_validate_objective(entry, f"{path}.{objective.mode}[{idx}]"))
        return issues

    objective_type = (objective.objective_type or "").lower()
    if not objective_type:
        issues.append(ValidationIssue(path, "objective type is required"))
        return issues
    if objective.count <= 0:
        issues.append(ValidationIssue(path, "objective count must be > 0"))
    if objective_type == "kill_mob":
        if not objective.mob_id and objective.entity_type is None and not objective.mob_tags:
            issues.append(ValidationIssue(path, "kill_mob requires mob_id, entity_type, or mob_tags"))
    elif objective_type == "use_item":
        if (
            not objective.item_id
            and objective.material is None
            and not objective.item_tags
            and not objective.lore_contains
            and not objective.item_pdc
            and objective.custom_model_data is None
        ):
            issues.append(ValidationIssue(path, "use_item requires item_id, material, tags, lore, pdc, or custom_model_data"))
    elif objective_type == "visit_region":
        if objective.region is None and not objective.worlds and not objective.biomes and not objective.structures:
            issues.append(ValidationIssue(path, "visit_region requires region, worlds, biomes, or structures"))
        if objective.region is not None and not objective.region.world:
            issues.append(ValidationIssue(path, "visit_region.region requires world"))
    elif objective_type == "craft_item":
        if not objective.item_id and objective.material is None and not objective.recipe_id:
            issues.append(ValidationIssue(path, "craft_item requires item_id, material, or recipe_id"))
    elif objective_type in {"break_block", "place_block"}:
        if objective.material is None:
            issues.append(ValidationIssue(path, f"{objective_type} requires material"))
    else:
        issues.append(ValidationIssue(path, f"unknown objective type {objective.objective_type}"))
    return issues


def _validate_rewards(rewards: QuestRewardsSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if rewards.xp < 0 or rewards.tokens < 0 or rewards.compressed < 0 or rewards.pallet < 0:
        issues.append(ValidationIssue(path, "xp/tokens/compressed/pallet rewards must be >= 0"))
    if rewards.mana < 0:
        issues.append(ValidationIssue(path, "mana reward must be >= 0"))
    for idx, item in enumerate(rewards.items):
        if item.amount <= 0:
            issues.append(ValidationIssue(f"{path}.items[{idx}]", "item amount must be > 0"))
    for idx, entry in enumerate(rewards.entries):
        issues.extend(_validate_reward_entry(entry, f"{path}.entries[{idx}]"))
    for idx, pool in enumerate(rewards.pools):
        issues.extend(_validate_reward_pool(pool, f"{path}.pools[{idx}]"))
    if rewards.scaling is not None:
        issues.extend(_validate_reward_scaling(rewards.scaling, f"{path}.scale"))
    return issues


def _validate_reward_entry(entry: QuestRewardEntrySpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if not entry.entry_type:
        issues.append(ValidationIssue(path, "reward entry type is required"))
    if entry.weight <= 0:
        issues.append(ValidationIssue(path, "reward entry weight must be > 0"))
    if entry.chance < 0 or entry.chance > 1:
        issues.append(ValidationIssue(path, "reward entry chance must be between 0 and 1"))
    if entry.entry_type == "item" and entry.item is None:
        issues.append(ValidationIssue(path, "item entry requires item"))
    if entry.entry_type == "title" and entry.title is None:
        issues.append(ValidationIssue(path, "title entry requires title"))
    if entry.entry_type == "buff" and entry.buff is None:
        issues.append(ValidationIssue(path, "buff entry requires buff"))
    if entry.entry_type not in {"item", "title", "buff"} and not entry.entry_id:
        issues.append(ValidationIssue(path, "entry type requires id for non-item/title/buff entries"))
    return issues


def _validate_reward_pool(pool: QuestRewardPoolSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if not pool.pool_id:
        issues.append(ValidationIssue(path, "reward pool id is required"))
    if pool.rolls <= 0:
        issues.append(ValidationIssue(path, "reward pool rolls must be > 0"))
    if not pool.entries:
        issues.append(ValidationIssue(path, "reward pool must include entries"))
    for idx, entry in enumerate(pool.entries):
        issues.extend(_validate_reward_entry(entry, f"{path}.entries[{idx}]"))
    return issues


def _validate_reward_scaling(scale: QuestRewardScalingSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if scale.min_multiplier <= 0 or scale.max_multiplier <= 0:
        issues.append(ValidationIssue(path, "scale multipliers must be > 0"))
    if scale.min_multiplier > scale.max_multiplier:
        issues.append(ValidationIssue(path, "minMultiplier cannot exceed maxMultiplier"))
    return issues


def validate_shop(shop: ShopSpec) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    path = f"shop:{shop.shop_id or '<missing>'}"
    if not shop.shop_id:
        issues.append(ValidationIssue(path, "shop id is required"))
    if not shop.title:
        issues.append(ValidationIssue(path, "shop title is required"))
    if not shop.trades:
        issues.append(ValidationIssue(path, "shop must include at least one trade"))
    if shop.stock is not None:
        issues.extend(_validate_shop_stock(shop.stock, f"{path}.stock"))
    if shop.pricing is not None:
        issues.extend(_validate_shop_pricing(shop.pricing, f"{path}.pricing"))
    if shop.price_modifiers is not None:
        issues.extend(_validate_price_modifiers(shop.price_modifiers, f"{path}.priceModifiers"))
    issues.extend(_validate_requirements(shop.requirements, f"{path}.requirements"))
    issues.extend(_validate_requirements(shop.visibility, f"{path}.visibility"))
    if shop.availability is not None:
        issues.extend(_validate_availability(shop.availability, f"{path}.availability"))
    for idx, trade in enumerate(shop.trades):
        issues.extend(_validate_trade(trade, f"{path}.trades[{idx}]"))
    if not shop.trades:
        issues.append(
            ValidationIssue(
                path,
                "shop has no trades",
                severity=Severity.ERROR,
            )
        )
    return issues


def _validate_trade(trade: ShopTradeSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if not trade.buys:
        issues.append(ValidationIssue(path, "trade requires at least one buy ingredient"))
    if not trade.sells:
        issues.append(ValidationIssue(path, "trade requires at least one sell ingredient"))
    if trade.max_uses is not None and trade.max_uses < 0:
        issues.append(ValidationIssue(path, "maxUses must be >= 0"))
    if trade.min_level is not None and trade.min_level < 0:
        issues.append(ValidationIssue(path, "minLevel must be >= 0"))
    if trade.price_multiplier is not None and trade.price_multiplier < 0:
        issues.append(ValidationIssue(path, "priceMultiplier must be >= 0"))
    if trade.dynamic_price is not None:
        issues.extend(_validate_dynamic_price(trade.dynamic_price, f"{path}.dynamicPrice"))
    if trade.stock is not None:
        issues.extend(_validate_shop_stock(trade.stock, f"{path}.stock"))
    if trade.price_modifiers is not None:
        issues.extend(_validate_price_modifiers(trade.price_modifiers, f"{path}.priceModifiers"))
    issues.extend(_validate_requirements(trade.requirements, f"{path}.requirements"))
    issues.extend(_validate_requirements(trade.visibility, f"{path}.visibility"))
    if trade.availability is not None:
        issues.extend(_validate_availability(trade.availability, f"{path}.availability"))
    for idx, ingredient in enumerate(trade.buys):
        issues.extend(_validate_ingredient(ingredient, f"{path}.buy[{idx}]"))
    for idx, ingredient in enumerate(trade.sells):
        issues.extend(_validate_ingredient(ingredient, f"{path}.sell[{idx}]"))
    return issues


def _validate_ingredient(ingredient: ShopIngredientSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if ingredient.amount <= 0:
        issues.append(ValidationIssue(path, "amount must be > 0"))
    ingredient_type = (ingredient.ingredient_type or "").lower()
    if not ingredient_type:
        issues.append(ValidationIssue(path, "ingredient type is required"))
        return issues
    if ingredient_type in {"item_id", "itemid", "id"}:
        if not ingredient.item_id and not ingredient.upgrade_id:
            issues.append(ValidationIssue(path, "item_id type requires item_id or upgrade_id"))
    elif ingredient_type == "currency":
        if not ingredient.currency_id:
            issues.append(ValidationIssue(path, "currency type requires currency_id"))
    elif ingredient_type == "material":
        if ingredient.material is None:
            issues.append(ValidationIssue(path, "material type requires material"))
    elif ingredient_type in {"item", "itemstack"}:
        if ingredient.item is None:
            issues.append(ValidationIssue(path, "itemstack type requires item"))
    elif ingredient_type == "tag":
        if not ingredient.tag:
            issues.append(ValidationIssue(path, "tag type requires tag"))
    elif ingredient_type == "category":
        if not ingredient.category:
            issues.append(ValidationIssue(path, "category type requires category"))
    elif ingredient_type == "matcher":
        if ingredient.matcher is None:
            issues.append(ValidationIssue(path, "matcher type requires matcher"))
    elif ingredient_type in {"token", "xp", "custom_xp"}:
        pass
    else:
        issues.append(ValidationIssue(path, f"unknown ingredient type {ingredient.ingredient_type}"))
    return issues


def _id_issue(
    path: str,
    message: str,
    missing: Optional[str],
    known: Sequence[str],
    severity: Severity = Severity.ERROR,
    hint: Optional[str] = None,
) -> ValidationIssue:
    suggestion = None
    if missing and known:
        matches = suggest_values(known, missing)
        if matches:
            suggestion = ", ".join(matches)
    return ValidationIssue(path, message, severity=severity, hint=hint, suggestion=suggestion)


def validate_pack(
    items: Sequence[ItemBuilder],
    mobs: Sequence[MobBuilder],
    quests: Sequence[QuestSpec],
    shops: Sequence[ShopSpec],
    abilities: Sequence[Ability],
) -> ValidationReport:
    report = ValidationReport()
    item_ids = {builder._id for builder in items if builder._id}
    mob_ids = {builder._id for builder in mobs if builder._id}
    quest_ids = {quest.quest_id for quest in quests if quest.quest_id}
    ability_ids = {ability.ability_id for ability in abilities if ability.ability_id}

    for item in items:
        report.extend(validate_item(item))
        for binding in item._bindings:
            if binding.ability and binding.ability not in ability_ids:
                report.add(
                    _id_issue(
                        f"item:{item._id}.bindings",
                        f"unknown ability id '{binding.ability}'",
                        binding.ability,
                        sorted(ability_ids),
                        hint="Define the ability or fix the id.",
                    )
                )

    for mob in mobs:
        report.extend(validate_mob(mob))
        for attack in mob._attacks.values():
            if attack.ability and attack.ability not in ability_ids:
                report.add(
                    _id_issue(
                        f"mob:{mob._id}.attacks",
                        f"unknown ability id '{attack.ability}'",
                        attack.ability,
                        sorted(ability_ids),
                        hint="Define the ability or update the mob attack.",
                    )
                )

    for quest in quests:
        report.extend(validate_quest(quest))
        for objective in quest.objectives:
            if isinstance(objective, QuestObjectiveSpec):
                if objective.item_id and objective.item_id not in item_ids:
                    report.add(
                        _id_issue(
                            f"quest:{quest.quest_id}.objectives",
                            f"unknown item id '{objective.item_id}'",
                            objective.item_id,
                            sorted(item_ids),
                        )
                    )
                if objective.mob_id and objective.mob_id not in mob_ids:
                    report.add(
                        _id_issue(
                            f"quest:{quest.quest_id}.objectives",
                            f"unknown mob id '{objective.mob_id}'",
                            objective.mob_id,
                            sorted(mob_ids),
                        )
                    )

    for shop in shops:
        report.extend(validate_shop(shop))
        for trade in shop.trades:
            for ingredient in trade.sells + trade.buys:
                if ingredient.item_id and ingredient.item_id not in item_ids:
                    report.add(
                        _id_issue(
                            f"shop:{shop.shop_id}.trades",
                            f"unknown item id '{ingredient.item_id}'",
                            ingredient.item_id,
                            sorted(item_ids),
                        )
                    )

    return report


def render_report(report: ValidationReport) -> str:
    if not report.issues:
        return "No validation issues found."
    lines: List[str] = []
    for issue in report.issues:
        lines.append(f"[{issue.severity.value.upper()}] {issue.path}: {issue.message}")
        if issue.hint:
            lines.append(f"  hint: {issue.hint}")
        if issue.suggestion:
            lines.append(f"  suggestion: {issue.suggestion}")
    return "\n".join(lines)


def _validate_requirements(requirements: List[ShopRequirementSpec], path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    for idx, requirement in enumerate(requirements):
        issues.extend(_validate_requirement(requirement, f"{path}[{idx}]"))
    return issues


def _validate_requirement(requirement: ShopRequirementSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    req_type = (requirement.requirement_type or "").lower()
    if not req_type:
        issues.append(ValidationIssue(path, "requirement type is required"))
        return issues
    if req_type == "permission":
        if not requirement.permission:
            issues.append(ValidationIssue(path, "permission requirement requires permission"))
    elif req_type == "level":
        if requirement.min_level is None:
            issues.append(ValidationIssue(path, "level requirement requires min_level"))
    elif req_type in {"custom_xp", "customxp"}:
        if requirement.min_custom_level is None and requirement.min_custom_points is None:
            issues.append(ValidationIssue(path, "custom_xp requirement requires min_level or min_points"))
    elif req_type == "quest":
        if not requirement.quest_id:
            issues.append(ValidationIssue(path, "quest requirement requires quest_id"))
    elif req_type == "class":
        if not requirement.classes:
            issues.append(ValidationIssue(path, "class requirement requires classes list"))
    elif req_type == "region":
        if not requirement.regions:
            issues.append(ValidationIssue(path, "region requirement requires regions list"))
    elif req_type == "faction":
        if not requirement.faction_id:
            issues.append(ValidationIssue(path, "faction requirement requires faction_id"))
    else:
        issues.append(ValidationIssue(path, f"unknown requirement type {requirement.requirement_type}"))
    return issues


def _validate_availability(availability: ShopAvailabilitySpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if not availability.windows:
        issues.append(ValidationIssue(path, "availability requires at least one window"))
    for idx, window in enumerate(availability.windows):
        if not window.start or not window.end:
            issues.append(ValidationIssue(f"{path}.windows[{idx}]", "window requires start and end"))
    return issues


def _validate_shop_stock(stock: ShopStockSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if stock.min < 0 or stock.max < 0:
        issues.append(ValidationIssue(path, "stock min/max must be >= 0"))
    if stock.max > 0 and stock.min > stock.max:
        issues.append(ValidationIssue(path, "stock min cannot exceed max"))
    if stock.restock_seconds < 0:
        issues.append(ValidationIssue(path, "restockSeconds must be >= 0"))
    return issues


def _validate_shop_pricing(pricing: ShopPricingSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if pricing.tax_rate < 0:
        issues.append(ValidationIssue(path, "taxRate must be >= 0"))
    for world, mult in pricing.world_multipliers.items():
        if mult < 0:
            issues.append(ValidationIssue(f"{path}.worldMultipliers.{world}", "multiplier must be >= 0"))
    for idx, region in enumerate(pricing.regions):
        if region.multiplier < 0:
            issues.append(ValidationIssue(f"{path}.regions[{idx}]", "multiplier must be >= 0"))
        if region.tax_rate < 0:
            issues.append(ValidationIssue(f"{path}.regions[{idx}]", "taxRate must be >= 0"))
    return issues


def _validate_dynamic_price(dynamic: ShopDynamicPriceSpec, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    if dynamic.min_multiplier < 0 or dynamic.max_multiplier < 0:
        issues.append(ValidationIssue(path, "multipliers must be >= 0"))
    if dynamic.max_multiplier < dynamic.min_multiplier:
        issues.append(ValidationIssue(path, "maxMultiplier must be >= minMultiplier"))
    if dynamic.period_seconds < 0:
        issues.append(ValidationIssue(path, "periodSeconds must be >= 0"))
    return issues


def _validate_price_modifiers(modifiers: ShopPriceModifiers, path: str) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    for key, value in modifiers.tier_multipliers.items():
        if value < 0:
            issues.append(ValidationIssue(f"{path}.tier.{key}", "multiplier must be >= 0"))
    for key, value in modifiers.rarity_multipliers.items():
        if value < 0:
            issues.append(ValidationIssue(f"{path}.rarity.{key}", "multiplier must be >= 0"))
    if modifiers.default_tier_multiplier < 0 or modifiers.default_rarity_multiplier < 0:
        issues.append(ValidationIssue(path, "default multipliers must be >= 0"))
    return issues


def _enum_label(value: Any) -> str:
    if isinstance(value, Enum):
        return value.value
    return str(value)


def _validate_mob_stats(builder: MobBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    stats = builder._stats
    if not stats or "MAX_HEALTH" not in stats:
        issues.append(
            ValidationIssue(
                f"mob:{builder._id or '<missing>'}.stats",
                "MAX_HEALTH is required for mob stats",
                severity=Severity.ERROR,
            )
        )
    if builder._attacks and "ATTACK_DAMAGE" not in stats:
        issues.append(
            ValidationIssue(
                f"mob:{builder._id or '<missing>'}.stats",
                "ATTACK_DAMAGE is required when attacks are defined",
                severity=Severity.ERROR,
            )
        )
    return issues


def _validate_mob_attacks(builder: MobBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    for key, attack in builder._attacks.items():
        path = f"mob:{builder._id or '<missing>'}.attacks.{key}"
        if not attack.ability:
            issues.append(ValidationIssue(path, "attack ability is required"))
        if attack.cooldown_ticks <= 0:
            issues.append(ValidationIssue(path, "cooldownTicks must be > 0", severity=Severity.ERROR))
        if attack.range_blocks <= 0:
            issues.append(ValidationIssue(path, "range must be > 0", severity=Severity.ERROR))
        if attack.chance < 0 or attack.chance > 1:
            issues.append(ValidationIssue(path, "chance must be between 0 and 1", severity=Severity.ERROR))
        if attack.aoe is not None and attack.aoe.radius <= 0:
            issues.append(ValidationIssue(path, "aoe.radius must be > 0", severity=Severity.ERROR))
        if _enum_label(attack.trigger) == "RANGED" and attack.range_blocks < 2:
            issues.append(
                ValidationIssue(
                    path,
                    "ranged attacks should use range >= 2",
                    severity=Severity.WARN,
                    hint="Increase range_blocks for ranged attacks",
                )
            )
    return issues


def _validate_mob_ai(builder: MobBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    ai = builder._ai
    if ai is None:
        return issues
    if ai.enabled is False and (builder._attacks or builder._passives):
        issues.append(
            ValidationIssue(
                f"mob:{builder._id or '<missing>'}.ai",
                "ai.enabled=false with attacks/passives may prevent combat",
                severity=Severity.WARN,
            )
        )
    locomotion = ai.locomotion_mode
    if locomotion is not None and ai.prefer_ground:
        locomotion_value = _enum_label(locomotion)
        if locomotion_value in {
            _enum_label(MobLocomotionMode.FLY),
            _enum_label(MobLocomotionMode.SWIM),
            _enum_label(MobLocomotionMode.CLIMB),
            _enum_label(MobLocomotionMode.BURROW),
        }:
            issues.append(
                ValidationIssue(
                    f"mob:{builder._id or '<missing>'}.ai",
                    "preferGround should be false for non-ground locomotion modes",
                    severity=Severity.WARN,
                )
            )
    return issues


def _material_name(builder: ItemBuilder) -> str | None:
    if builder._material is None:
        return None
    return builder._material.name


def _validate_item_bindings(builder: ItemBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    for index, binding in enumerate(builder._bindings):
        path = f"item:{builder._id or '<missing>'}.bindings[{index}]"
        if not binding.ability:
            issues.append(ValidationIssue(path, "binding ability is required", severity=Severity.ERROR))
            continue
        if binding.binding_type == "passive":
            period = binding.period_ticks if binding.period_ticks is not None else binding.interval_ticks
            if period is None:
                issues.append(
                    ValidationIssue(path, "passive binding requires periodTicks", severity=Severity.ERROR)
                )
            elif period <= 0:
                issues.append(ValidationIssue(path, "periodTicks must be > 0", severity=Severity.ERROR))
            if binding.click is not None and _enum_or_str(binding.click, "click").lower() != "passive":
                issues.append(
                    ValidationIssue(
                        path,
                        "passive binding should use click=PASSIVE",
                        severity=Severity.WARN,
                    )
                )
        else:
            if binding.click is None:
                issues.append(ValidationIssue(path, "interact binding requires click", severity=Severity.ERROR))
            elif _enum_or_str(binding.click, "click").lower() == "passive":
                issues.append(
                    ValidationIssue(path, "interact binding cannot use click=PASSIVE", severity=Severity.ERROR)
                )
        if binding.cancel_event is not None and binding.binding_type == "passive":
            issues.append(
                ValidationIssue(path, "cancelEvent is ignored for passive bindings", severity=Severity.WARN)
            )
    return issues


def _validate_item_behaviors(builder: ItemBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    for hook_type, hooks in builder._behavior.items():
        for index, hook in enumerate(hooks):
            path = f"item:{builder._id or '<missing>'}.behavior.{_enum_or_str(hook_type, 'hook')}"
            path = f"{path}[{index}]"
            if not hook.abilities and hook.sound is None and hook.particle is None:
                issues.append(ValidationIssue(path, "behavior hook should include ability/sound/particle"))
            if hook.cooldown_ticks is not None and hook.cooldown_seconds is not None:
                issues.append(ValidationIssue(path, "use cooldownTicks or cooldownSeconds, not both"))
            if hook.consume_amount is not None and hook.consume_amount < 0:
                issues.append(ValidationIssue(path, "consumeAmount must be >= 0"))
            if hook.durability_cost is not None and hook.durability_cost < 0:
                issues.append(ValidationIssue(path, "durabilityCost must be >= 0"))
    return issues


def _validate_item_meta(builder: ItemBuilder) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    meta: MetaSpec | None = builder._meta
    if meta is None:
        return issues
    material = _material_name(builder)
    if meta.damage is not None and (meta.damage_min is not None or meta.damage_max is not None or meta.durability):
        issues.append(
            ValidationIssue(
                f"item:{builder._id or '<missing>'}.meta",
                "damage overrides damageMin/damageMax/durability; keep one style",
            )
        )

    def _require_material(valid: Iterable[str], label: str) -> None:
        if material is None:
            return
        if material not in valid:
            issues.append(
                ValidationIssue(
                    f"item:{builder._id or '<missing>'}.meta.{label}",
                    f"material {material} does not support {label} meta",
                )
            )

    if meta.book is not None:
        _require_material({"WRITTEN_BOOK", "WRITABLE_BOOK"}, "book")
    if meta.potion is not None:
        _require_material({"POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW"}, "potion")
    if meta.suspicious_stew is not None:
        _require_material({"SUSPICIOUS_STEW"}, "suspicious_stew")
    if meta.banner is not None:
        if material is not None and not material.endswith("_BANNER"):
            issues.append(
                ValidationIssue(
                    f"item:{builder._id or '<missing>'}.meta.banner",
                    f"material {material} does not support banner meta",
                )
            )
    if meta.shield is not None:
        _require_material({"SHIELD"}, "shield")
    if meta.firework is not None:
        _require_material({"FIREWORK_ROCKET"}, "firework")
    if meta.firework_charge is not None:
        _require_material({"FIREWORK_STAR"}, "firework_charge")
    if meta.map_meta is not None:
        _require_material({"MAP", "FILLED_MAP"}, "map")
    if meta.skull is not None:
        _require_material({"PLAYER_HEAD", "PLAYER_WALL_HEAD"}, "skull")
    if meta.trim is not None and material is not None:
        if not (
            material.endswith("_HELMET")
            or material.endswith("_CHESTPLATE")
            or material.endswith("_LEGGINGS")
            or material.endswith("_BOOTS")
            or material == "ELYTRA"
        ):
            issues.append(
                ValidationIssue(
                    f"item:{builder._id or '<missing>'}.meta.trim",
                    f"material {material} may not support armor trims",
                )
            )
    if meta.firework is not None and meta.firework_charge is not None:
        issues.append(
            ValidationIssue(
                f"item:{builder._id or '<missing>'}.meta",
                "firework and firework_charge should not be combined",
            )
        )
    return issues


def summarize_issues(issues: Iterable[ValidationIssue]) -> str:
    lines = [f"{issue.path}: {issue.message}" for issue in issues]
    return "\n".join(lines)


def validate_crafting_recipe(recipe: CraftingRecipeSpec) -> List[ValidationIssue]:
    issues: List[ValidationIssue] = []
    path = f"recipe:{recipe.recipe_id}"
    if not recipe.recipe_id:
        issues.append(ValidationIssue(path, "recipe_id is required"))
    if not recipe.variants:
        issues.append(ValidationIssue(path, "variants must not be empty"))
    if not recipe.outputs:
        issues.append(ValidationIssue(path, "outputs must not be empty"))
    for index, variant in enumerate(recipe.variants):
        v_path = f"{path}.variants[{index}]"
        if variant.grid is None and not variant.inputs and not variant.slots:
            issues.append(ValidationIssue(v_path, "variant requires grid, inputs, or slots"))
        if variant.grid is not None and not variant.grid.keys:
            issues.append(ValidationIssue(f"{v_path}.grid", "grid requires key mappings"))
    for index, output in enumerate(recipe.outputs):
        o_path = f"{path}.outputs[{index}]"
        defined = sum(
            1
            for value in (output.item_id, output.material, output.item_stack, output.template)
            if value is not None
        )
        if defined == 0:
            issues.append(ValidationIssue(o_path, "output must include item, material, itemStack, or template"))
        if output.min_amount is not None and output.max_amount is not None:
            if output.min_amount > output.max_amount:
                issues.append(ValidationIssue(o_path, "min_amount must be <= max_amount"))
        if output.amount <= 0:
            issues.append(ValidationIssue(o_path, "amount must be > 0"))
        if output.chance is not None and (output.chance < 0 or output.chance > 1):
            issues.append(ValidationIssue(o_path, "chance must be between 0 and 1"))
    return issues
