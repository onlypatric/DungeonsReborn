"""Quests builder (requirements, objectives, rewards, rotations)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Union

from .base import BuilderBase, ExporterBase, snake_case
from .utils import apply_overrides
from .shops import ShopAvailabilitySpec, ShopTimeWindowSpec
from .gui import GuiTileSpec
from .vanilla import EnumValue, Material, EntityType, PotionEffectType, normalize_enum_name


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


@dataclass
class QuestRegionSpec:
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
class QuestStageRequirement:
    quest_id: str
    stage: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return {"quest": self.quest_id, "stage": self.stage}


@dataclass
class QuestRequirementsSpec:
    level: int = 0
    quests: List[str] = field(default_factory=list)
    permissions: List[str] = field(default_factory=list)
    classes: List[str] = field(default_factory=list)
    skill_nodes: List[str] = field(default_factory=list)
    custom_level: int = 0
    custom_points: int = 0
    faction_id: Optional[str] = None
    min_faction_rank: int = 0
    quest_stages: List[QuestStageRequirement] = field(default_factory=list)
    accept_worlds: List[str] = field(default_factory=list)
    accept_regions: List[QuestRegionSpec] = field(default_factory=list)
    turnin_worlds: List[str] = field(default_factory=list)
    turnin_regions: List[QuestRegionSpec] = field(default_factory=list)
    availability: Optional[ShopAvailabilitySpec] = None
    turnin_availability: Optional[ShopAvailabilitySpec] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.level:
            data["level"] = self.level
        if self.quests:
            data["quests"] = list(self.quests)
        if self.permissions:
            data["permissions"] = list(self.permissions)
        if self.classes:
            data["classes"] = list(self.classes)
        if self.skill_nodes:
            data["skillNodes"] = list(self.skill_nodes)
        if self.custom_level or self.custom_points:
            data["customXp"] = {
                "level": self.custom_level,
                "points": self.custom_points,
            }
        if self.faction_id:
            data["faction"] = {"id": self.faction_id, "rank": self.min_faction_rank}
        if self.quest_stages:
            data["questStages"] = [entry.to_dict() for entry in self.quest_stages]
        if self.accept_worlds or self.accept_regions or self.availability is not None:
            accept: Dict[str, Any] = {}
            if self.accept_worlds:
                accept["worlds"] = list(self.accept_worlds)
            if self.accept_regions:
                accept["regions"] = [region.to_dict() for region in self.accept_regions]
            if self.availability is not None:
                accept["availability"] = self.availability.to_dict()
            data["accept"] = accept
        if self.turnin_worlds or self.turnin_regions or self.turnin_availability is not None:
            turnin: Dict[str, Any] = {}
            if self.turnin_worlds:
                turnin["worlds"] = list(self.turnin_worlds)
            if self.turnin_regions:
                turnin["regions"] = [region.to_dict() for region in self.turnin_regions]
            if self.turnin_availability is not None:
                turnin["availability"] = self.turnin_availability.to_dict()
            data["turnIn"] = turnin
        return data


@dataclass
class QuestRewardItemSpec:
    item_type: Optional[str] = None
    item_id: Optional[str] = None
    material: Optional[Material | str] = None
    item: Optional[Any] = None
    amount: int = 1

    def __post_init__(self) -> None:
        if self.item_id:
            self.item_id = snake_case(self.item_id)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.item_type:
            data["type"] = self.item_type
        if self.item_id:
            data["id"] = snake_case(self.item_id)
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.item is not None:
            data["item"] = _item_to_dict(self.item)
        if self.amount and self.amount != 1:
            data["amount"] = self.amount
        return data


@dataclass
class QuestRewardTitleSpec:
    title: str
    subtitle: Optional[str] = None
    fade_in_ticks: int = 0
    stay_ticks: int = 0
    fade_out_ticks: int = 0

    def to_dict(self) -> Dict[str, Any]:
        data = {"title": self.title}
        if self.subtitle:
            data["subtitle"] = self.subtitle
        if self.fade_in_ticks:
            data["fadeInTicks"] = self.fade_in_ticks
        if self.stay_ticks:
            data["stayTicks"] = self.stay_ticks
        if self.fade_out_ticks:
            data["fadeOutTicks"] = self.fade_out_ticks
        return data


@dataclass
class QuestRewardBuffSpec:
    effect: PotionEffectType | str
    duration_ticks: int
    amplifier: int = 0
    ambient: bool = False
    particles: bool = True
    icon: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "type": _enum_or_str(self.effect, "effect"),
            "durationTicks": self.duration_ticks,
            "amplifier": self.amplifier,
            "ambient": self.ambient,
            "particles": self.particles,
            "icon": self.icon,
        }


@dataclass
class QuestRewardEntrySpec:
    entry_type: str
    entry_id: Optional[str] = None
    amount: float = 0.0
    item: Optional[QuestRewardItemSpec] = None
    title: Optional[QuestRewardTitleSpec] = None
    buff: Optional[QuestRewardBuffSpec] = None
    weight: int = 1
    chance: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "type": self.entry_type,
            "weight": self.weight,
            "chance": self.chance,
        }
        if self.entry_id:
            data["id"] = self.entry_id
        if self.amount:
            data["amount"] = self.amount
        if self.item is not None:
            data["item"] = self.item.to_dict()
        if self.title is not None:
            data["title"] = self.title.to_dict()
        if self.buff is not None:
            data["buff"] = self.buff.to_dict()
        return data


@dataclass
class QuestRewardPoolSpec:
    pool_id: str
    rolls: int = 1
    unique: bool = False
    entries: List[QuestRewardEntrySpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.pool_id,
            "rolls": self.rolls,
            "unique": self.unique,
            "entries": [entry.to_dict() for entry in self.entries],
        }


@dataclass
class QuestRewardScalingSpec:
    level_factor: float = 0.0
    party_factor: float = 0.0
    min_multiplier: float = 1.0
    max_multiplier: float = 1.0
    apply_to_items: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "levelFactor": self.level_factor,
            "partyFactor": self.party_factor,
            "minMultiplier": self.min_multiplier,
            "maxMultiplier": self.max_multiplier,
            "applyToItems": self.apply_to_items,
        }


@dataclass
class QuestRewardsSpec:
    xp: int = 0
    tokens: int = 0
    compressed: int = 0
    pallet: int = 0
    mana: float = 0.0
    resources: Dict[str, float] = field(default_factory=dict)
    items: List[QuestRewardItemSpec] = field(default_factory=list)
    entries: List[QuestRewardEntrySpec] = field(default_factory=list)
    pools: List[QuestRewardPoolSpec] = field(default_factory=list)
    scaling: Optional[QuestRewardScalingSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "xp": self.xp,
            "tokens": self.tokens,
            "compressed": self.compressed,
            "pallet": self.pallet,
            "mana": self.mana,
        }
        if self.resources:
            data["resources"] = dict(self.resources)
        if self.items:
            data["items"] = [item.to_dict() for item in self.items]
        if self.entries:
            data["entries"] = [entry.to_dict() for entry in self.entries]
        if self.pools:
            data["pools"] = [pool.to_dict() for pool in self.pools]
        if self.scaling is not None:
            data["scale"] = self.scaling.to_dict()
        return data


@dataclass
class QuestObjectiveShareSpec:
    enabled: Optional[bool] = None
    radius: Optional[float] = None
    min_contributors: Optional[int] = None
    leader_only: Optional[bool] = None
    idle_timeout_seconds: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.enabled is not None:
            data["enabled"] = self.enabled
        if self.radius is not None:
            data["radius"] = self.radius
        if self.min_contributors is not None:
            data["minContributors"] = self.min_contributors
        if self.leader_only is not None:
            data["leaderOnly"] = self.leader_only
        if self.idle_timeout_seconds is not None:
            data["idleTimeoutSeconds"] = self.idle_timeout_seconds
        return data


@dataclass
class QuestObjectiveSpec:
    objective_type: str
    mob_id: Optional[str] = None
    entity_type: Optional[EntityType | str] = None
    mob_tier: Optional[str] = None
    mob_phase: Optional[str] = None
    mob_variant: Optional[str] = None
    mob_trait: Optional[str] = None
    mob_tags: List[str] = field(default_factory=list)
    item_id: Optional[str] = None
    material: Optional[Material | str] = None
    item_tags: List[str] = field(default_factory=list)
    item_pdc: Dict[str, str] = field(default_factory=dict)
    lore_contains: List[str] = field(default_factory=list)
    custom_model_data: Optional[int] = None
    region: Optional[QuestRegionSpec] = None
    worlds: List[str] = field(default_factory=list)
    biomes: List[str] = field(default_factory=list)
    structures: List[str] = field(default_factory=list)
    recipe_id: Optional[str] = None
    count: int = 1
    party_role: Optional[str] = None
    group_id: Optional[str] = None
    group_mode: Optional[str] = None
    order: Optional[int] = None
    optional: Optional[bool] = None
    stage: Optional[int] = None
    time_limit_seconds: Optional[int] = None
    share: Optional[QuestObjectiveShareSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"type": self.objective_type, "count": self.count}
        if self.mob_id:
            data["mob"] = snake_case(self.mob_id)
        if self.entity_type is not None:
            data["entity"] = _enum_or_str(self.entity_type, "entity")
        if self.mob_tier:
            data["tier"] = self.mob_tier
        if self.mob_phase:
            data["phase"] = self.mob_phase
        if self.mob_variant:
            data["variant"] = self.mob_variant
        if self.mob_trait:
            data["trait"] = self.mob_trait
        if self.mob_tags:
            data["mobTags"] = list(self.mob_tags)
        if self.item_id:
            data["itemId"] = snake_case(self.item_id)
        if self.material is not None:
            data["material"] = _enum_or_str(self.material, "material")
        if self.item_tags:
            data["itemTags"] = list(self.item_tags)
        if self.item_pdc:
            data["pdc"] = dict(self.item_pdc)
        if self.lore_contains:
            data["loreContains"] = list(self.lore_contains)
        if self.custom_model_data is not None:
            data["customModelData"] = self.custom_model_data
        if self.region is not None:
            data["region"] = self.region.to_dict()
        if self.worlds:
            data["worlds"] = list(self.worlds)
        if self.biomes:
            data["biomes"] = list(self.biomes)
        if self.structures:
            data["structures"] = list(self.structures)
        if self.recipe_id:
            data["recipeId"] = snake_case(self.recipe_id)
        if self.party_role:
            data["party"] = self.party_role
        if self.group_id:
            data["groupId"] = self.group_id
        if self.group_mode:
            data["groupMode"] = self.group_mode
        if self.order is not None:
            data["order"] = self.order
        if self.optional is not None:
            data["optional"] = self.optional
        if self.stage is not None:
            data["stage"] = self.stage
        if self.time_limit_seconds is not None:
            data["timeLimitSeconds"] = self.time_limit_seconds
        if self.share is not None:
            data["share"] = self.share.to_dict()
        return data


@dataclass
class QuestObjectiveGroup:
    mode: str
    objectives: List["QuestObjectiveEntry"] = field(default_factory=list)
    optional: bool = False
    stage: Optional[int] = None
    time_limit_seconds: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {self.mode: [entry.to_dict() for entry in self.objectives]}
        if self.optional:
            data["optional"] = True
        if self.stage is not None:
            data["stage"] = self.stage
        if self.time_limit_seconds is not None:
            data["timeLimitSeconds"] = self.time_limit_seconds
        return data


QuestObjectiveEntry = Union[QuestObjectiveSpec, QuestObjectiveGroup]


def objective_all_of(*entries: QuestObjectiveEntry) -> QuestObjectiveGroup:
    return QuestObjectiveGroup(mode="all_of", objectives=list(entries))


def objective_any_of(*entries: QuestObjectiveEntry) -> QuestObjectiveGroup:
    return QuestObjectiveGroup(mode="any_of", objectives=list(entries))


def objective_sequence(*entries: QuestObjectiveEntry) -> QuestObjectiveGroup:
    return QuestObjectiveGroup(mode="sequence", objectives=list(entries))


def objective_optional(entry: QuestObjectiveEntry) -> QuestObjectiveGroup:
    return QuestObjectiveGroup(mode="optional", objectives=[entry], optional=True)


@dataclass
class QuestVisibilityConditionSpec:
    quest_id: str
    status: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data = {"quest": self.quest_id}
        if self.status:
            data["status"] = self.status
        return data


@dataclass
class QuestVisibilitySpec:
    hidden: bool = False
    show_in_log: bool = True
    show_in_giver: bool = True
    hints: List[str] = field(default_factory=list)
    reveal_on: List[str] = field(default_factory=list)
    requires: List[QuestVisibilityConditionSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "hidden": self.hidden,
            "showInLog": self.show_in_log,
            "showInGiver": self.show_in_giver,
        }
        if self.hints:
            data["hints"] = list(self.hints)
        if self.reveal_on:
            data["revealOn"] = list(self.reveal_on)
        if self.requires:
            data["requires"] = [entry.to_dict() for entry in self.requires]
        return data


@dataclass
class QuestRepeatSpec:
    daily_limit: int = 0
    weekly_limit: int = 0

    def to_dict(self) -> Dict[str, Any]:
        if self.daily_limit == 0 and self.weekly_limit == 0:
            return {}
        return {"daily": self.daily_limit, "weekly": self.weekly_limit}


@dataclass
class QuestPartyShareSpec:
    enabled: bool = False
    radius: float = 0.0
    min_contributors: int = 0
    leader_only: bool = False
    idle_timeout_seconds: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "enabled": self.enabled,
            "radius": self.radius,
            "minContributors": self.min_contributors,
            "leaderOnly": self.leader_only,
            "idleTimeoutSeconds": self.idle_timeout_seconds,
        }


@dataclass
class QuestFailSpec:
    fail_on_death: bool = False
    fail_on_leave_region: bool = False
    timeout_seconds: int = 0
    region: Optional[QuestRegionSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "failOnDeath": self.fail_on_death,
            "failOnLeaveRegion": self.fail_on_leave_region,
            "timeoutSeconds": self.timeout_seconds,
        }
        if self.region is not None:
            data["region"] = self.region.to_dict()
        return data


@dataclass
class QuestGuiPreviewSpec:
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


@dataclass
class QuestSpec:
    quest_id: str
    name: str
    objectives: List[QuestObjectiveEntry]
    enabled: bool = True
    description: List[str] = field(default_factory=list)
    icon: Optional[str] = None
    head: Optional[str] = None
    gui: Optional[QuestGuiPreviewSpec] = None
    requirements: Optional[QuestRequirementsSpec] = None
    rewards: Optional[QuestRewardsSpec] = None
    cooldown_seconds: int = 0
    rotation: Optional[str] = None
    repeat: Optional[QuestRepeatSpec] = None
    progress_throttle_seconds: int = 0
    party_share: Optional[QuestPartyShareSpec] = None
    party_locked: bool = False
    rotation_pool: Optional[str] = None
    branch_id: Optional[str] = None
    branch_lockout: Optional[str] = None
    fail: Optional[QuestFailSpec] = None
    visibility: Optional[QuestVisibilitySpec] = None
    categories: List[str] = field(default_factory=list)
    tier: Optional[str] = None
    tags: List[str] = field(default_factory=list)
    overrides: List[Dict[str, Any]] = field(default_factory=list)
    override_paths: List[tuple[str, Any]] = field(default_factory=list)
    override_warnings: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {
            "name": self.name,
            "enabled": self.enabled,
            "objectives": [entry.to_dict() for entry in self.objectives],
        }
        if self.description:
            data["description"] = list(self.description)
        if self.icon:
            data["icon"] = self.icon
        if self.head:
            data["head"] = self.head
        if self.gui is not None:
            gui_payload = self.gui.to_dict()
            if gui_payload:
                data["gui"] = gui_payload
        if self.requirements is not None:
            data["requirements"] = self.requirements.to_dict()
        if self.rewards is not None:
            data["rewards"] = self.rewards.to_dict()
        if self.cooldown_seconds:
            data["cooldownSeconds"] = self.cooldown_seconds
        if self.rotation:
            data["rotation"] = self.rotation
        if self.repeat is not None:
            data["repeat"] = self.repeat.to_dict()
        if self.progress_throttle_seconds:
            data["progressThrottleSeconds"] = self.progress_throttle_seconds
        if self.party_share is not None:
            data["partyShare"] = self.party_share.to_dict()
        if self.party_locked:
            data["partyLocked"] = True
        if self.rotation_pool:
            data["rotationPool"] = self.rotation_pool
        if self.branch_id or self.branch_lockout:
            branch: Dict[str, Any] = {}
            if self.branch_id:
                branch["id"] = self.branch_id
            if self.branch_lockout:
                branch["lockout"] = self.branch_lockout
            data["branch"] = branch
        if self.fail is not None:
            data["fail"] = self.fail.to_dict()
        if self.visibility is not None:
            data["visibility"] = self.visibility.to_dict()
        if self.categories:
            data["categories"] = list(self.categories)
        if self.tier:
            data["tier"] = self.tier
        if self.tags:
            data["tags"] = list(self.tags)
        if self.overrides or self.override_paths:
            apply_overrides(data, self.overrides, self.override_paths)
        return data


@dataclass
class QuestRotationPoolSpec:
    pool_id: str
    rotation: Optional[str] = None
    scope: Optional[str] = None
    size: int = 0
    quest_ids: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.rotation:
            data["rotation"] = self.rotation
        if self.scope:
            data["scope"] = self.scope
        if self.size:
            data["size"] = self.size
        if self.quest_ids:
            data["quests"] = list(self.quest_ids)
        return data


@dataclass
class QuestDocument:
    quests: List[QuestSpec] = field(default_factory=list)
    rotation_pools: List[QuestRotationPoolSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"quests": {quest.quest_id: quest.to_dict() for quest in self.quests}}
        if self.rotation_pools:
            data["rotationPools"] = {pool.pool_id: pool.to_dict() for pool in self.rotation_pools}
        return data


class QuestExporter(ExporterBase):
    def write_quests(self, quests: Iterable[QuestSpec], filename: str = "quests.yml") -> str:
        data = {"quests": {quest.quest_id: quest.to_dict() for quest in quests}}
        return self.write_yaml(filename, data)

    def write_document(self, document: QuestDocument, filename: str = "quests.yml") -> str:
        return self.write_yaml(filename, document.to_dict())


def kill_mob_quest(quest_id: str, name: str, mob_id: str, count: int, reward_xp: int) -> QuestSpec:
    objective = QuestObjectiveSpec("kill_mob", mob_id=snake_case(mob_id), count=count)
    rewards = QuestRewardsSpec(xp=reward_xp)
    return QuestSpec(quest_id=quest_id, name=name, objectives=[objective], rewards=rewards)


def craft_item_quest(quest_id: str, name: str, item_id: str, count: int, reward_tokens: int) -> QuestSpec:
    objective = QuestObjectiveSpec("craft_item", item_id=snake_case(item_id), count=count)
    rewards = QuestRewardsSpec(tokens=reward_tokens)
    return QuestSpec(quest_id=quest_id, name=name, objectives=[objective], rewards=rewards)


class QuestStep:
    def __init__(self, step_id: str) -> None:
        self.step_id = step_id
        self.objective: Optional[QuestObjectiveSpec] = None

    def kill(self, mob_id: str, count: int = 1) -> "QuestStep":
        self.objective = QuestObjectiveSpec("kill_mob", mob_id=snake_case(mob_id), count=count)
        return self

    def collect(self, item_id: str, count: int = 1) -> "QuestStep":
        self.objective = QuestObjectiveSpec("use_item", item_id=snake_case(item_id), count=count)
        return self

    def craft(self, recipe_id: str, count: int = 1) -> "QuestStep":
        self.objective = QuestObjectiveSpec("craft_item", recipe_id=snake_case(recipe_id), count=count)
        return self


class QuestBuilder(BuilderBase):
    def __init__(self, quest_id: str, name: Optional[str] = None) -> None:
        super().__init__(_id=quest_id)
        self._title = name or quest_id
        self._objectives: List[QuestObjectiveEntry] = []
        self._rewards = QuestRewardsSpec()
        self._requirements = QuestRequirementsSpec()

    def name(self, value: str) -> "QuestBuilder":
        self._title = value
        return self

    def fetch_item(self, item_id: str, count: int = 1) -> "QuestBuilder":
        self._objectives.append(QuestObjectiveSpec("use_item", item_id=snake_case(item_id), count=count))
        return self

    def collect_item(self, item_id: str, count: int = 1) -> "QuestBuilder":
        return self.fetch_item(item_id, count=count)

    def kill_mob(self, mob_id: str, count: int = 1) -> "QuestBuilder":
        self._objectives.append(QuestObjectiveSpec("kill_mob", mob_id=snake_case(mob_id), count=count))
        return self

    def craft_item(self, recipe_id: str, count: int = 1) -> "QuestBuilder":
        self._objectives.append(QuestObjectiveSpec("craft_item", recipe_id=snake_case(recipe_id), count=count))
        return self

    def chain(self, *steps: QuestStep) -> "QuestBuilder":
        order = len(self._objectives)
        for step in steps:
            if step.objective is None:
                continue
            step.objective.order = order
            order += 1
            self._objectives.append(step.objective)
        return self

    def reward_xp(self, amount: int) -> "QuestBuilder":
        self._rewards.xp = amount
        return self

    def reward_tokens(self, amount: int) -> "QuestBuilder":
        self._rewards.tokens = amount
        return self

    def reward_item(self, item_id: str, amount: int = 1) -> "QuestBuilder":
        self._rewards.items.append(QuestRewardItemSpec(item_id=snake_case(item_id), amount=amount))
        return self

    def require_level(self, level: int) -> "QuestBuilder":
        self._requirements.level = level
        return self

    def build_spec(self) -> QuestSpec:
        self._ensure_id("quest_id")
        if not self._title:
            self._ensure_name()
            self._title = self._name
        return QuestSpec(
            quest_id=self._id or "",
            name=self._title or (self._id or ""),
            objectives=self._objectives,
            requirements=self._requirements,
            rewards=self._rewards,
            overrides=[mapping for mapping, _ in self._raw_overrides],
            override_paths=[(path, value) for path, value, _ in self._path_overrides],
            override_warnings=self._format_override_warnings(f"quest:{self._id}"),
        )


def Quest(quest_id: str, name: Optional[str] = None) -> QuestBuilder:
    return QuestBuilder(quest_id, name=name)


def step(step_id: str) -> QuestStep:
    return QuestStep(step_id)


def kill_mob_objective(
    mob_id: Optional[str] = None,
    entity_type: Optional[EntityType | str] = None,
    count: int = 1,
    mob_tier: Optional[str] = None,
    mob_phase: Optional[str] = None,
    mob_variant: Optional[str] = None,
    mob_trait: Optional[str] = None,
    mob_tags: Optional[Sequence[str]] = None,
) -> QuestObjectiveSpec:
    return QuestObjectiveSpec(
        "kill_mob",
        mob_id=snake_case(mob_id) if mob_id else None,
        entity_type=entity_type,
        mob_tier=mob_tier,
        mob_phase=mob_phase,
        mob_variant=mob_variant,
        mob_trait=mob_trait,
        mob_tags=list(mob_tags or []),
        count=count,
    )


def use_item_objective(
    item_id: Optional[str] = None,
    material: Optional[Material | str] = None,
    count: int = 1,
    item_tags: Optional[Sequence[str]] = None,
    lore_contains: Optional[Sequence[str]] = None,
    custom_model_data: Optional[int] = None,
    item_pdc: Optional[Mapping[str, str]] = None,
) -> QuestObjectiveSpec:
    return QuestObjectiveSpec(
        "use_item",
        item_id=snake_case(item_id) if item_id else None,
        material=material,
        item_tags=list(item_tags or []),
        lore_contains=list(lore_contains or []),
        custom_model_data=custom_model_data,
        item_pdc=dict(item_pdc or {}),
        count=count,
    )


def visit_region_objective(
    region: Optional[QuestRegionSpec] = None,
    worlds: Optional[Sequence[str]] = None,
    biomes: Optional[Sequence[str]] = None,
    structures: Optional[Sequence[str]] = None,
) -> QuestObjectiveSpec:
    return QuestObjectiveSpec(
        "visit_region",
        region=region,
        worlds=list(worlds or []),
        biomes=list(biomes or []),
        structures=list(structures or []),
        count=1,
    )


def craft_item_objective(
    item_id: Optional[str] = None,
    material: Optional[Material | str] = None,
    recipe_id: Optional[str] = None,
    count: int = 1,
    item_tags: Optional[Sequence[str]] = None,
    lore_contains: Optional[Sequence[str]] = None,
    custom_model_data: Optional[int] = None,
    item_pdc: Optional[Mapping[str, str]] = None,
) -> QuestObjectiveSpec:
    return QuestObjectiveSpec(
        "craft_item",
        item_id=snake_case(item_id) if item_id else None,
        material=material,
        recipe_id=snake_case(recipe_id) if recipe_id else None,
        item_tags=list(item_tags or []),
        lore_contains=list(lore_contains or []),
        custom_model_data=custom_model_data,
        item_pdc=dict(item_pdc or {}),
        count=count,
    )


def break_block_objective(material: Material | str, count: int = 1) -> QuestObjectiveSpec:
    return QuestObjectiveSpec("break_block", material=material, count=count)


def place_block_objective(material: Material | str, count: int = 1) -> QuestObjectiveSpec:
    return QuestObjectiveSpec("place_block", material=material, count=count)


def quest_name_key(quest_id: str) -> str:
    return f"quests.{quest_id}.name"


def quest_description_key(quest_id: str, index: int) -> str:
    return f"quests.{quest_id}.description.{index}"


def quest_description_keys(quest_id: str, count: int) -> List[str]:
    return [quest_description_key(quest_id, idx) for idx in range(1, count + 1)]


def quest_gui_preview(tile: GuiTileSpec) -> QuestGuiPreviewSpec:
    return QuestGuiPreviewSpec(
        head=tile.head,
        icon=tile.icon,
        title=tile.title,
        title_key=tile.title_key,
        description=tile.description,
        description_key=tile.description_key,
        summary=list(tile.summary),
        summary_keys=list(tile.summary_keys),
    )


def starter_quest_pack(prefix: str = "starter") -> List[QuestSpec]:
    hunt = QuestSpec(
        quest_id=f"{prefix}_hunt",
        name=quest_name_key(f"{prefix}_hunt"),
        description=quest_description_keys(f"{prefix}_hunt", 2),
        icon="ICON_QUESTS",
        objectives=[kill_mob_objective(entity_type="ZOMBIE", count=5)],
        rewards=QuestRewardsSpec(xp=25, tokens=2),
    )
    craft = QuestSpec(
        quest_id=f"{prefix}_craft",
        name=quest_name_key(f"{prefix}_craft"),
        description=quest_description_keys(f"{prefix}_craft", 2),
        icon="ICON_CRAFTING",
        objectives=[craft_item_objective(material="CRAFTING_TABLE", count=1)],
        rewards=QuestRewardsSpec(xp=20, tokens=1),
    )
    explore = QuestSpec(
        quest_id=f"{prefix}_explore",
        name=quest_name_key(f"{prefix}_explore"),
        description=quest_description_keys(f"{prefix}_explore", 2),
        icon="ICON_MAP",
        objectives=[visit_region_objective(worlds=["world"])],
        rewards=QuestRewardsSpec(xp=30, tokens=2),
    )
    return [hunt, craft, explore]


def reward_item_id(item_id: str, amount: int = 1) -> QuestRewardItemSpec:
    return QuestRewardItemSpec(item_id=snake_case(item_id), amount=amount)


def reward_item_material(material: Material | str, amount: int = 1) -> QuestRewardItemSpec:
    return QuestRewardItemSpec(material=material, amount=amount)


def reward_item(item: Any, amount: int = 1) -> QuestRewardItemSpec:
    return QuestRewardItemSpec(item=item, amount=amount)


def reward_title(
    title: str,
    subtitle: Optional[str] = None,
    fade_in_ticks: int = 0,
    stay_ticks: int = 0,
    fade_out_ticks: int = 0,
) -> QuestRewardTitleSpec:
    return QuestRewardTitleSpec(
        title=title,
        subtitle=subtitle,
        fade_in_ticks=fade_in_ticks,
        stay_ticks=stay_ticks,
        fade_out_ticks=fade_out_ticks,
    )


def reward_buff(
    effect: PotionEffectType | str,
    duration_ticks: int,
    amplifier: int = 0,
    ambient: bool = False,
    particles: bool = True,
    icon: bool = True,
) -> QuestRewardBuffSpec:
    return QuestRewardBuffSpec(
        effect=effect,
        duration_ticks=duration_ticks,
        amplifier=amplifier,
        ambient=ambient,
        particles=particles,
        icon=icon,
    )


def reward_entry_item(
    item: QuestRewardItemSpec,
    weight: int = 1,
    chance: float = 0.0,
) -> QuestRewardEntrySpec:
    return QuestRewardEntrySpec(
        entry_type="item",
        item=item,
        weight=weight,
        chance=chance,
    )


def reward_entry_title(
    title: QuestRewardTitleSpec,
    weight: int = 1,
    chance: float = 0.0,
) -> QuestRewardEntrySpec:
    return QuestRewardEntrySpec(
        entry_type="title",
        title=title,
        weight=weight,
        chance=chance,
    )


def reward_entry_buff(
    buff: QuestRewardBuffSpec,
    weight: int = 1,
    chance: float = 0.0,
) -> QuestRewardEntrySpec:
    return QuestRewardEntrySpec(
        entry_type="buff",
        buff=buff,
        weight=weight,
        chance=chance,
    )


def reward_entry_id(
    entry_type: str,
    entry_id: str,
    amount: float = 0.0,
    weight: int = 1,
    chance: float = 0.0,
) -> QuestRewardEntrySpec:
    return QuestRewardEntrySpec(
        entry_type=entry_type,
        entry_id=entry_id,
        amount=amount,
        weight=weight,
        chance=chance,
    )


def reward_pool(pool_id: str, entries: Sequence[QuestRewardEntrySpec], rolls: int = 1, unique: bool = False) -> QuestRewardPoolSpec:
    return QuestRewardPoolSpec(pool_id=pool_id, entries=list(entries), rolls=rolls, unique=unique)


def reward_scale(
    level_factor: float = 0.0,
    party_factor: float = 0.0,
    min_multiplier: float = 1.0,
    max_multiplier: float = 1.0,
    apply_to_items: bool = False,
) -> QuestRewardScalingSpec:
    return QuestRewardScalingSpec(
        level_factor=level_factor,
        party_factor=party_factor,
        min_multiplier=min_multiplier,
        max_multiplier=max_multiplier,
        apply_to_items=apply_to_items,
    )
