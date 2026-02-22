"""Mobs builder with abstractions and typed enums."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, Iterable, List, Mapping, Optional

from .base import BuilderBase, ExporterBase, snake_case
from .gui import GuiTileSpec
from .enums import DamageType
from .vanilla import (
    Attribute,
    EntityType,
    EnumValue,
    Material,
    Particle,
    Sound,
    normalize_enum_name,
    parse_enum,
)


class MobEnum(str, Enum):
    def __str__(self) -> str:
        return self.value


def _require_enum(value: EnumValue | str, label: str, enum_cls: type[Enum] | None = None) -> str:
    if isinstance(value, Enum):
        return normalize_enum_name(value.name)
    if isinstance(value, str) and enum_cls is not None:
        parsed = parse_enum(enum_cls, value, label=label)
        return normalize_enum_name(parsed.name)
    raise ValueError(f"{label} must be provided as an enum value")


def _enum_or_str(value: Any, label: str) -> str:
    if isinstance(value, Enum):
        return value.name
    if isinstance(value, str):
        return value
    raise ValueError(f"{label} must be provided as an enum value or string")


class MobAttackTrigger(MobEnum):
    MELEE = "MELEE"
    RANGED = "RANGED"


class MobTargetMode(MobEnum):
    LAST_ATTACKER = "LAST_ATTACKER"
    NEAREST_PLAYER = "NEAREST_PLAYER"
    NEAREST_HOSTILE = "NEAREST_HOSTILE"
    WEIGHT_DISTANCE = "WEIGHT_DISTANCE"
    WEIGHT_THREAT = "WEIGHT_THREAT"
    PARTY_LEADER = "PARTY_LEADER"


class MobLocomotionMode(MobEnum):
    GROUND = "GROUND"
    SWIM = "SWIM"
    FLY = "FLY"
    CLIMB = "CLIMB"
    BURROW = "BURROW"


class MobBehaviorState(MobEnum):
    IDLE = "IDLE"
    ENGAGE = "ENGAGE"
    RETREAT = "RETREAT"
    RAGE = "RAGE"


class MobAiEngine(MobEnum):
    LEGACY = "LEGACY"
    V2 = "V2"
    V3 = "V3"


class MobAiVersion(MobEnum):
    V2 = "V2"
    V3 = "V3"


class MobAiProfile(MobEnum):
    AGGRESSIVE = "AGGRESSIVE"
    DEFENSIVE = "DEFENSIVE"
    NEUTRAL = "NEUTRAL"
    PASSIVE = "PASSIVE"
    SUPPORT = "SUPPORT"
    SCOUT = "SCOUT"
    BERSERKER = "BERSERKER"


class MobAiGoalType(MobEnum):
    AVOID = "AVOID"
    GUARD = "GUARD"
    PATROL = "PATROL"
    RETURN = "RETURN"
    WANDER = "WANDER"
    CHASE = "CHASE"
    HOLD_RANGE = "HOLD_RANGE"
    FLEE = "FLEE"
    ASSIST = "ASSIST"
    CALL_HELP = "CALL_HELP"
    HOLD_POSITION = "HOLD_POSITION"


class MobAiPhaseMergeMode(MobEnum):
    PATCH = "PATCH"
    REPLACE = "REPLACE"


class MobAttackAoEShape(MobEnum):
    SPHERE = "SPHERE"
    CONE = "CONE"
    BOX = "BOX"


class MobTargetFilter(MobEnum):
    ANY = "ANY"
    PLAYERS = "PLAYERS"
    MOBS = "MOBS"
    HOSTILE = "HOSTILE"


class MobSpawnTetherAction(MobEnum):
    NONE = "NONE"
    PULL = "PULL"
    TELEPORT = "TELEPORT"
    DESPAWN = "DESPAWN"


class MobPartyRule(MobEnum):
    NONE = "NONE"
    AVOID_PARTY = "AVOID_PARTY"
    FOCUS_LEADER = "FOCUS_LEADER"


class MobBossBarAudience(MobEnum):
    ALL_PLAYERS = "ALL_PLAYERS"
    OWNER_ONLY = "OWNER_ONLY"


class MobCompositeRole(MobEnum):
    PRIMARY_MOUNT = "PRIMARY_MOUNT"
    PRIMARY_RIDER = "PRIMARY_RIDER"


@dataclass
class Vec3:
    x: float
    y: float
    z: float

    def to_dict(self) -> Dict[str, float]:
        return {"x": float(self.x), "y": float(self.y), "z": float(self.z)}


@dataclass
class MobParticlesSpec:
    particle: Particle
    count: int = 1
    offset_x: float = 0.0
    offset_y: float = 0.0
    offset_z: float = 0.0
    extra: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "particle": _require_enum(self.particle, "particle", Particle),
            "count": self.count,
            "offsetX": self.offset_x,
            "offsetY": self.offset_y,
            "offsetZ": self.offset_z,
            "extra": self.extra,
        }


@dataclass
class MobSoundSpec:
    sound: Sound
    volume: float = 1.0
    pitch: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "sound": _require_enum(self.sound, "sound", Sound),
            "volume": self.volume,
            "pitch": self.pitch,
        }


@dataclass
class MobBossBarSpec:
    title: str
    color: Optional[str | Enum] = None
    overlay: Optional[str | Enum] = None
    audience: Optional[MobBossBarAudience | str] = MobBossBarAudience.ALL_PLAYERS
    enabled: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"title": self.title}
        if self.enabled is not None:
            payload["enabled"] = self.enabled
        if self.color is not None:
            payload["color"] = _enum_or_str(self.color, "color")
        if self.overlay is not None:
            payload["overlay"] = _enum_or_str(self.overlay, "overlay")
        if self.audience is not None:
            payload["audience"] = _enum_or_str(self.audience, "audience")
        return payload


@dataclass
class MobBroadcastSpec:
    enabled: bool = True
    message: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"enabled": self.enabled}
        if self.message is not None:
            payload["message"] = self.message
        return payload


@dataclass
class MobModelSpec:
    model_id: Optional[str] = None
    provider: str = "model_engine"
    replace_visual: bool = False
    hide_base_entity: Optional[bool] = None
    animation: Optional[str] = None
    animation_speed: float = 1.0
    animations: Optional[Mapping[str, str]] = None

    def to_dict(self) -> Dict[str, Any]:
        if not self.model_id:
            raise ValueError("MobModelSpec requires model_id")
        payload: Dict[str, Any] = {}
        if self.model_id:
            payload["id"] = self.model_id
        if self.provider and self.provider.lower() != "model_engine":
            payload["provider"] = self.provider
        if self.replace_visual:
            payload["replaceVisual"] = True
        if self.hide_base_entity is not None:
            payload["hideBaseEntity"] = self.hide_base_entity
        elif self.replace_visual:
            payload["hideBaseEntity"] = True
        if self.animation:
            payload["animation"] = self.animation
        if self.animation_speed != 1.0:
            payload["animationSpeed"] = self.animation_speed
        if self.animations:
            payload["animations"] = {str(k): str(v) for k, v in self.animations.items()}
        return payload


@dataclass
class MobStyleSpec:
    name: Optional[str] = None
    show_name: Optional[bool] = None
    bossbar: Optional[MobBossBarSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.name is not None:
            payload["name"] = self.name
        if self.show_name is not None:
            payload["showName"] = self.show_name
        if self.bossbar is not None:
            payload["bossbar"] = self.bossbar.to_dict()
        return payload


@dataclass
class MobGuiPreviewSpec:
    head: Optional[str] = None
    icon: Optional[str] = None
    description: Optional[str] = None
    description_key: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.head:
            data["head"] = self.head
        if self.icon:
            data["icon"] = self.icon
        if self.description:
            data["description"] = self.description
        if self.description_key:
            data["descriptionKey"] = self.description_key
        return data


@dataclass
class MobEquipmentSpec:
    main_hand: Optional[Any] = None
    off_hand: Optional[Any] = None
    head: Optional[Any] = None
    chest: Optional[Any] = None
    legs: Optional[Any] = None
    feet: Optional[Any] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.main_hand is not None:
            payload["mainHand"] = self.main_hand
        if self.off_hand is not None:
            payload["offHand"] = self.off_hand
        if self.head is not None:
            payload["head"] = self.head
        if self.chest is not None:
            payload["chest"] = self.chest
        if self.legs is not None:
            payload["legs"] = self.legs
        if self.feet is not None:
            payload["feet"] = self.feet
        return payload


@dataclass
class MobVisualSpec:
    texture: str
    slot: str = "head"
    material: Optional[Material | str] = None
    model_key: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "texture": self.texture,
            "slot": self.slot,
        }
        if self.material is not None:
            payload["material"] = _enum_or_str(self.material, "material")
        if self.model_key:
            payload["modelKey"] = self.model_key
        return payload


@dataclass
class MobAiGoalSpec:
    goal_type: MobAiGoalType | str
    priority: int = 0
    radius: float = 0.0
    speed: float = 0.0
    min_range: float = 0.0
    max_range: float = 0.0
    interval_ticks: int = 0
    points: List[Vec3] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "type": _enum_or_str(self.goal_type, "goal_type"),
            "priority": self.priority,
        }
        if self.radius:
            payload["radius"] = self.radius
        if self.speed:
            payload["speed"] = self.speed
        if self.min_range:
            payload["minRange"] = self.min_range
        if self.max_range:
            payload["maxRange"] = self.max_range
        if self.interval_ticks:
            payload["intervalTicks"] = self.interval_ticks
        if self.points:
            payload["points"] = [point.to_dict() for point in self.points]
        return payload


@dataclass
class MobAiHooksSpec:
    on_enter_idle: Optional[str] = None
    on_enter_engage: Optional[str] = None
    on_enter_retreat: Optional[str] = None
    on_enter_rage: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.on_enter_idle:
            payload["onEnterIdle"] = self.on_enter_idle
        if self.on_enter_engage:
            payload["onEnterEngage"] = self.on_enter_engage
        if self.on_enter_retreat:
            payload["onEnterRetreat"] = self.on_enter_retreat
        if self.on_enter_rage:
            payload["onEnterRage"] = self.on_enter_rage
        return payload


@dataclass
class MobAiPerceptionSpec:
    aggro_radius: Optional[float] = None
    retarget_cooldown_ticks: Optional[int] = None
    line_of_sight_required: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.aggro_radius is not None:
            payload["aggroRadius"] = self.aggro_radius
        if self.retarget_cooldown_ticks is not None:
            payload["retargetCooldownTicks"] = self.retarget_cooldown_ticks
        if self.line_of_sight_required is not None:
            payload["lineOfSightRequired"] = self.line_of_sight_required
        return payload


@dataclass
class MobAiCombatSpec:
    flee_health_ratio: Optional[float] = None
    flee_speed: Optional[float] = None
    rage_health_ratio: Optional[float] = None
    chase_speed: Optional[float] = None
    kite_min_range: Optional[float] = None
    kite_speed: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.flee_health_ratio is not None or self.flee_speed is not None:
            flee: Dict[str, Any] = {}
            if self.flee_health_ratio is not None:
                flee["healthRatio"] = self.flee_health_ratio
            if self.flee_speed is not None:
                flee["speed"] = self.flee_speed
            payload["flee"] = flee
        if self.rage_health_ratio is not None:
            payload["rage"] = {"healthRatio": self.rage_health_ratio}
        if self.chase_speed is not None:
            payload["chaseSpeed"] = self.chase_speed
        if self.kite_min_range is not None or self.kite_speed is not None:
            kite: Dict[str, Any] = {}
            if self.kite_min_range is not None:
                kite["minRange"] = self.kite_min_range
            if self.kite_speed is not None:
                kite["speed"] = self.kite_speed
            payload["kite"] = kite
        return payload


@dataclass
class MobAiGroupSpec:
    assist_radius: Optional[float] = None
    call_for_help_radius: Optional[float] = None
    focus_fire: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.assist_radius is not None:
            payload["assistRadius"] = self.assist_radius
        if self.call_for_help_radius is not None:
            payload["callForHelpRadius"] = self.call_for_help_radius
        if self.focus_fire is not None:
            payload["focusFire"] = self.focus_fire
        return payload


@dataclass
class MobAiEnvironmentSpec:
    avoid_lava: Optional[bool] = None
    avoid_water: Optional[bool] = None
    avoid_powder_snow: Optional[bool] = None
    avoid_cactus: Optional[bool] = None
    avoid_sunlight: Optional[bool] = None
    open_doors: Optional[bool] = None
    break_doors: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        avoid: Dict[str, Any] = {}
        if self.avoid_lava is not None:
            avoid["lava"] = self.avoid_lava
        if self.avoid_water is not None:
            avoid["water"] = self.avoid_water
        if self.avoid_powder_snow is not None:
            avoid["powderSnow"] = self.avoid_powder_snow
        if self.avoid_cactus is not None:
            avoid["cactus"] = self.avoid_cactus
        if self.avoid_sunlight is not None:
            avoid["sunlight"] = self.avoid_sunlight
        if avoid:
            payload["avoid"] = avoid
        interactions: Dict[str, Any] = {}
        if self.open_doors is not None:
            interactions["openDoors"] = self.open_doors
        if self.break_doors is not None:
            interactions["breakDoors"] = self.break_doors
        if interactions:
            payload["interactions"] = interactions
        return payload


@dataclass
class MobAiSchedulerSpec:
    state_transition_cooldown_ticks: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.state_transition_cooldown_ticks is not None:
            payload["stateTransitionCooldownTicks"] = self.state_transition_cooldown_ticks
        return payload


@dataclass
class MobAiUtilitySelectorSpec:
    selector_id: str
    base_score: int = 50
    actions: List[MobAiGoalSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.selector_id,
            "score": {"base": self.base_score},
            "actions": [action.to_dict() for action in self.actions],
        }


@dataclass
class MobAiSpec:
    version: Optional[MobAiVersion | str] = None
    engine: Optional[MobAiEngine | str] = None
    profile: Optional[MobAiProfile | str] = None
    enabled: Optional[bool] = None
    override_default: Optional[bool] = None
    aggro_radius: Optional[float] = None
    leash_radius: Optional[float] = None
    leash_teleport_radius: Optional[float] = None
    aggro_target_mode: Optional[MobTargetMode | str] = None
    prefer_last_attacker: Optional[bool] = None
    target_switch_cooldown_ticks: Optional[int] = None
    flee_health_ratio: Optional[float] = None
    flee_speed: Optional[float] = None
    idle_wander_radius: Optional[float] = None
    idle_wander_interval_ticks: Optional[int] = None
    roam_radius: Optional[float] = None
    kite_min_range: Optional[float] = None
    kite_speed: Optional[float] = None
    chase_speed: Optional[float] = None
    locomotion_mode: Optional[MobLocomotionMode | str] = None
    avoid_water: Optional[bool] = None
    avoid_lava: Optional[bool] = None
    open_doors: Optional[bool] = None
    break_doors: Optional[bool] = None
    avoid_sunlight: Optional[bool] = None
    avoid_powder_snow: Optional[bool] = None
    avoid_cactus: Optional[bool] = None
    call_for_help_radius: Optional[float] = None
    assist_radius: Optional[float] = None
    state_transition_cooldown_ticks: Optional[int] = None
    prefer_ground: Optional[bool] = None
    guard_points: List[Vec3] = field(default_factory=list)
    rage_health_ratio: Optional[float] = None
    rage_speed: Optional[float] = None
    party_rule: Optional[MobPartyRule | str] = None
    goals: List[MobAiGoalSpec] = field(default_factory=list)
    hooks: Optional[MobAiHooksSpec] = None
    perception: Optional[MobAiPerceptionSpec] = None
    combat: Optional[MobAiCombatSpec] = None
    group: Optional[MobAiGroupSpec] = None
    environment: Optional[MobAiEnvironmentSpec] = None
    scheduler: Optional[MobAiSchedulerSpec] = None
    utility_selectors: List[MobAiUtilitySelectorSpec] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.version is not None:
            payload["version"] = _enum_or_str(self.version, "version")
        if self.engine is not None:
            payload["engine"] = _enum_or_str(self.engine, "engine")
        if self.profile is not None:
            payload["profile"] = _enum_or_str(self.profile, "profile")
        if self.enabled is not None:
            payload["enabled"] = self.enabled
        if self.override_default is not None:
            payload["overrideDefault"] = self.override_default
        if self.aggro_radius is not None:
            payload["aggroRadius"] = self.aggro_radius
        if self.leash_radius is not None:
            payload["leashRadius"] = self.leash_radius
        if self.leash_teleport_radius is not None:
            payload["leashTeleportRadius"] = self.leash_teleport_radius
        if self.aggro_target_mode is not None:
            payload["aggroTargetMode"] = _enum_or_str(self.aggro_target_mode, "aggro_target_mode")
        if self.prefer_last_attacker is not None:
            payload["preferLastAttacker"] = self.prefer_last_attacker
        if self.target_switch_cooldown_ticks is not None:
            payload["targetSwitchCooldownTicks"] = self.target_switch_cooldown_ticks
        if self.flee_health_ratio is not None:
            payload["fleeHealthRatio"] = self.flee_health_ratio
        if self.flee_speed is not None:
            payload["fleeSpeed"] = self.flee_speed
        if self.idle_wander_radius is not None:
            payload["idleWanderRadius"] = self.idle_wander_radius
        if self.idle_wander_interval_ticks is not None:
            payload["idleWanderIntervalTicks"] = self.idle_wander_interval_ticks
        if self.roam_radius is not None:
            payload["roamRadius"] = self.roam_radius
        if self.kite_min_range is not None:
            payload["kiteMinRange"] = self.kite_min_range
        if self.kite_speed is not None:
            payload["kiteSpeed"] = self.kite_speed
        if self.chase_speed is not None:
            payload["chaseSpeed"] = self.chase_speed
        if self.locomotion_mode is not None:
            payload["locomotion"] = _enum_or_str(self.locomotion_mode, "locomotion_mode")
        if self.avoid_water is not None:
            payload["avoidWater"] = self.avoid_water
        if self.avoid_lava is not None:
            payload["avoidLava"] = self.avoid_lava
        if self.open_doors is not None:
            payload["openDoors"] = self.open_doors
        if self.break_doors is not None:
            payload["breakDoors"] = self.break_doors
        if self.avoid_sunlight is not None:
            payload["avoidSunlight"] = self.avoid_sunlight
        if self.avoid_powder_snow is not None:
            payload["avoidPowderSnow"] = self.avoid_powder_snow
        if self.avoid_cactus is not None:
            payload["avoidCactus"] = self.avoid_cactus
        if self.call_for_help_radius is not None:
            payload["callForHelpRadius"] = self.call_for_help_radius
        if self.assist_radius is not None:
            payload["assistRadius"] = self.assist_radius
        if self.state_transition_cooldown_ticks is not None:
            payload["stateTransitionCooldownTicks"] = self.state_transition_cooldown_ticks
        if self.prefer_ground is not None:
            payload["preferGround"] = self.prefer_ground
        if self.guard_points:
            payload["guardPoints"] = [point.to_dict() for point in self.guard_points]
        if self.rage_health_ratio is not None:
            payload["rageHealthRatio"] = self.rage_health_ratio
        if self.rage_speed is not None:
            payload["rageSpeed"] = self.rage_speed
        if self.party_rule is not None:
            payload["partyRule"] = _enum_or_str(self.party_rule, "party_rule")
        if self.goals:
            payload["goals"] = [goal.to_dict() for goal in self.goals]
        if self.hooks is not None:
            hooks_payload = self.hooks.to_dict()
            if hooks_payload:
                payload["hooks"] = hooks_payload
        if self.perception is not None:
            perception_payload = self.perception.to_dict()
            if perception_payload:
                payload["perception"] = perception_payload
        if self.combat is not None:
            combat_payload = self.combat.to_dict()
            if combat_payload:
                payload["combat"] = combat_payload
        if self.group is not None:
            group_payload = self.group.to_dict()
            if group_payload:
                payload["group"] = group_payload
        if self.environment is not None:
            environment_payload = self.environment.to_dict()
            if environment_payload:
                payload["environment"] = environment_payload
        if self.scheduler is not None:
            scheduler_payload = self.scheduler.to_dict()
            if scheduler_payload:
                payload["scheduler"] = scheduler_payload
        if self.utility_selectors:
            payload["utility"] = {"selectors": [selector.to_dict() for selector in self.utility_selectors]}
        return payload


@dataclass
class MobAttackAoE:
    shape: MobAttackAoEShape | str = MobAttackAoEShape.SPHERE
    radius: float = 3.0
    height: float = 0.0
    angle_degrees: float = 0.0
    max_targets: int = 0
    target_filter: MobTargetFilter | str = MobTargetFilter.ANY

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "shape": _enum_or_str(self.shape, "shape"),
            "radius": self.radius,
        }
        if self.height:
            payload["height"] = self.height
        if self.angle_degrees:
            payload["angleDegrees"] = self.angle_degrees
        if self.max_targets:
            payload["maxTargets"] = self.max_targets
        if self.target_filter is not None:
            payload["filter"] = _enum_or_str(self.target_filter, "target_filter")
        return payload


@dataclass
class MobAttack:
    ability: str
    trigger: MobAttackTrigger | str = MobAttackTrigger.MELEE
    cooldown_ticks: int = 40
    target_mode: MobTargetMode | str = MobTargetMode.NEAREST_PLAYER
    range_blocks: float = 10.0
    chance: float = 1.0
    require_line_of_sight: bool = True
    require_target: bool = True
    priority_weight: float = 1.0
    internal_cooldown_ticks: int = 0
    aoe: Optional[MobAttackAoE] = None

    def to_dict(self) -> Dict[str, Any]:
        payload = {
            "ability": self.ability,
            "trigger": _enum_or_str(self.trigger, "trigger"),
            "cooldownTicks": self.cooldown_ticks,
            "targetMode": _enum_or_str(self.target_mode, "target_mode"),
            "range": self.range_blocks,
            "chance": self.chance,
            "requireLineOfSight": self.require_line_of_sight,
            "requireTarget": self.require_target,
            "priorityWeight": self.priority_weight,
            "internalCooldownTicks": self.internal_cooldown_ticks,
        }
        if self.aoe is not None:
            payload["aoe"] = self.aoe.to_dict()
        return payload


@dataclass
class MobPassive:
    ability: str
    period_ticks: int = 20

    def to_dict(self) -> Dict[str, Any]:
        return {"ability": self.ability, "periodTicks": self.period_ticks}


@dataclass
class MobPhase:
    phase_id: str
    health_below: float
    main_attack: Optional[MobAttack] = None
    secondary_attack: Optional[MobAttack] = None
    passives: List[MobPassive] = field(default_factory=list)
    scale_multiplier: Optional[float] = None
    collidable: Optional[bool] = None
    model: Optional[MobModelSpec] = None
    visual: Optional[MobVisualSpec] = None
    ai: Optional[MobAiSpec] = None
    ai_merge_mode: Optional[MobAiPhaseMergeMode | str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "id": self.phase_id,
            "healthBelow": self.health_below,
        }
        if self.main_attack:
            payload.setdefault("attacks", {})["main"] = self.main_attack.to_dict()
        if self.secondary_attack:
            payload.setdefault("attacks", {})["secondary"] = self.secondary_attack.to_dict()
        if self.passives:
            payload["passives"] = [entry.to_dict() for entry in self.passives]
        if self.scale_multiplier is not None:
            payload["scaleMultiplier"] = self.scale_multiplier
        if self.collidable is not None:
            payload["collidable"] = self.collidable
        if self.model is not None:
            payload["model"] = self.model.to_dict()
        if self.visual is not None:
            payload["visual"] = self.visual.to_dict()
        if self.ai is not None or self.ai_merge_mode is not None:
            ai_payload: Dict[str, Any] = {}
            if self.ai is not None:
                ai_payload.update(self.ai.to_dict())
            if self.ai_merge_mode is not None:
                ai_payload["merge"] = _enum_or_str(self.ai_merge_mode, "ai_merge_mode")
            if ai_payload:
                payload["ai"] = ai_payload
        return payload


@dataclass
class MobVariantSpec:
    variant_id: str
    weight: float = 1.0
    name: Optional[str] = None
    name_prefix: Optional[str] = None
    name_suffix: Optional[str] = None
    health_multiplier: float = 1.0
    damage_multiplier: float = 1.0
    speed_multiplier: float = 1.0
    follow_range_multiplier: float = 1.0
    scale_multiplier: float = 1.0
    collidable: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "id": self.variant_id,
            "weight": self.weight,
            "healthMultiplier": self.health_multiplier,
            "damageMultiplier": self.damage_multiplier,
            "speedMultiplier": self.speed_multiplier,
            "followRangeMultiplier": self.follow_range_multiplier,
            "scaleMultiplier": self.scale_multiplier,
        }
        if self.name is not None:
            payload["name"] = self.name
        if self.name_prefix is not None:
            payload["namePrefix"] = self.name_prefix
        if self.name_suffix is not None:
            payload["nameSuffix"] = self.name_suffix
        if self.collidable is not None:
            payload["collidable"] = self.collidable
        return payload


@dataclass
class MobTraitSpec:
    trait_id: str
    weight: float = 1.0
    name: Optional[str] = None
    name_prefix: Optional[str] = None
    name_suffix: Optional[str] = None
    health_multiplier: float = 1.0
    damage_multiplier: float = 1.0
    speed_multiplier: float = 1.0
    follow_range_multiplier: float = 1.0
    scale_multiplier: float = 1.0
    resistances: Optional[Mapping[DamageType | str, float]] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "id": self.trait_id,
            "weight": self.weight,
            "healthMultiplier": self.health_multiplier,
            "damageMultiplier": self.damage_multiplier,
            "speedMultiplier": self.speed_multiplier,
            "followRangeMultiplier": self.follow_range_multiplier,
            "scaleMultiplier": self.scale_multiplier,
        }
        if self.name is not None:
            payload["name"] = self.name
        if self.name_prefix is not None:
            payload["namePrefix"] = self.name_prefix
        if self.name_suffix is not None:
            payload["nameSuffix"] = self.name_suffix
        if self.resistances:
            payload["resistances"] = {
                _enum_or_str(key, "resistance"): value for key, value in self.resistances.items()
            }
        return payload


@dataclass
class MobCompositeSpec:
    mount_id: str
    rider_id: str
    role: MobCompositeRole | str = MobCompositeRole.PRIMARY_MOUNT
    keep_alive_together: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "mount": self.mount_id,
            "rider": self.rider_id,
            "role": _enum_or_str(self.role, "role"),
            "keepAliveTogether": self.keep_alive_together,
        }


@dataclass
class MobProgressionSpec:
    xp: Optional[int] = None
    min_xp: Optional[int] = None
    max_xp: Optional[int] = None
    max_player_xp: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.xp is not None:
            payload["xp"] = self.xp
        if self.min_xp is not None:
            payload["minXp"] = self.min_xp
        if self.max_xp is not None:
            payload["maxXp"] = self.max_xp
        if self.max_player_xp is not None:
            payload["maxPlayerXp"] = self.max_player_xp
        return payload


@dataclass
class MobAdvancementRewardSpec:
    xp: int = 0
    skill_points: int = 0
    items: List[Mapping[str, Any]] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"xp": self.xp, "skillPoints": self.skill_points}
        if self.items:
            payload["items"] = list(self.items)
        return payload


@dataclass
class MobSummonSpec:
    enabled: bool = True
    despawn_when_owner_offline: bool = True
    despawn_distance: float = 0.0
    teleport_distance: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "enabled": self.enabled,
            "despawnWhenOwnerOffline": self.despawn_when_owner_offline,
            "despawnDistance": self.despawn_distance,
            "teleportDistance": self.teleport_distance,
        }


@dataclass
class MobManaRange:
    minimum: float
    maximum: float

    def to_value(self) -> float | Dict[str, float]:
        if abs(self.maximum - self.minimum) < 1e-9:
            return float(self.maximum)
        return {"min": float(self.minimum), "max": float(self.maximum)}


@dataclass
class MobManaStreak:
    max_stacks: int
    multiplier: float
    window_ticks: int = 0
    window_seconds: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "maxStacks": self.max_stacks,
            "multiplier": self.multiplier,
        }
        if self.window_seconds is not None:
            payload["windowSeconds"] = self.window_seconds
        elif self.window_ticks:
            payload["windowTicks"] = self.window_ticks
        return payload


@dataclass
class MobManaTier:
    weight: float = 1.0
    min_multiplier: float = 1.0
    max_multiplier: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"weight": self.weight}
        max_value = self.min_multiplier if self.max_multiplier is None else self.max_multiplier
        if abs(max_value - self.min_multiplier) < 1e-9:
            payload["multiplier"] = self.min_multiplier
        else:
            payload["minMultiplier"] = self.min_multiplier
            payload["maxMultiplier"] = max_value
        return payload


@dataclass
class MobManaDropSpec:
    resource: str = "mana"
    killer: Optional[MobManaRange] = None
    nearby: Optional[MobManaRange] = None
    radius: float = 0.0
    cap: float = 0.0
    streak: Optional[MobManaStreak] = None
    tiers: List[MobManaTier] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"resource": self.resource}
        if self.cap:
            payload["cap"] = self.cap
        if self.killer is not None:
            payload["killer"] = self.killer.to_value()
        if self.nearby is not None:
            nearby_payload: Dict[str, Any] = {}
            nearby_value = self.nearby.to_value()
            if isinstance(nearby_value, dict):
                nearby_payload.update(nearby_value)
            else:
                nearby_payload["amount"] = nearby_value
            if self.radius:
                nearby_payload["radius"] = self.radius
            payload["nearby"] = nearby_payload if nearby_payload else self.nearby.to_value()
        elif self.radius:
            payload["radius"] = self.radius
        if self.streak is not None:
            payload["streak"] = self.streak.to_dict()
        if self.tiers:
            payload["tiers"] = [tier.to_dict() for tier in self.tiers]
        return payload


@dataclass
class MobManaDrainSpec:
    resource: str = "mana"
    amount: float = 0.0
    chance: float = 1.0
    cooldown_ticks: int = 0

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"resource": self.resource, "amount": self.amount, "chance": self.chance}
        if self.cooldown_ticks:
            payload["cooldownTicks"] = self.cooldown_ticks
        return payload


@dataclass
class MobDropConditions:
    min_level: Optional[int] = None
    max_level: Optional[int] = None
    biomes: List[str] = field(default_factory=list)
    min_time: Optional[int] = None
    max_time: Optional[int] = None
    min_luck: Optional[float] = None
    max_luck: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.min_level is not None:
            payload["minLevel"] = self.min_level
        if self.max_level is not None:
            payload["maxLevel"] = self.max_level
        if self.biomes:
            payload["biomes"] = list(self.biomes)
        if self.min_time is not None:
            payload["minTime"] = self.min_time
        if self.max_time is not None:
            payload["maxTime"] = self.max_time
        if self.min_luck is not None:
            payload["minLuck"] = self.min_luck
        if self.max_luck is not None:
            payload["maxLuck"] = self.max_luck
        return payload


@dataclass
class MobDrop:
    item_id: Optional[str] = None
    item: Optional[str] = None
    token_tier: Optional[str] = None
    upgrade_id: Optional[str] = None
    chance: Optional[float] = None
    amount: Optional[int] = None
    min_amount: Optional[int] = None
    max_amount: Optional[int] = None
    tier: Optional[str] = None
    conditions: Optional[MobDropConditions] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.item_id:
            payload["itemId"] = self.item_id
        elif self.item:
            payload["item"] = self.item
        if self.token_tier:
            payload["token"] = self.token_tier
        if self.upgrade_id:
            payload["upgradeId"] = self.upgrade_id
        if self.chance is not None:
            payload["chance"] = self.chance * 100.0 if self.chance <= 1.0 else self.chance
        if self.amount is not None:
            payload["amount"] = self.amount
        if self.min_amount is not None:
            payload["min"] = self.min_amount
        if self.max_amount is not None:
            payload["max"] = self.max_amount
        if self.tier:
            payload["tier"] = self.tier
        if self.conditions:
            payload["conditions"] = self.conditions.to_dict()
        return payload


@dataclass
class MobLootPoolRef:
    pool_id: str
    chance: float = 1.0
    rolls: Optional[int] = None
    bonus_rolls: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"id": self.pool_id}
        if self.chance != 1.0:
            payload["chance"] = self.chance
        if self.rolls is not None:
            payload["rolls"] = self.rolls
        if self.bonus_rolls is not None:
            payload["bonusRolls"] = self.bonus_rolls
        return payload


@dataclass
class MobLootBundle:
    drops: List[MobDrop] = field(default_factory=list)
    rolls: Optional[int] = None
    bonus_rolls: Optional[int] = None
    chance: Optional[float] = None
    conditions: Optional[MobDropConditions] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"drops": [drop.to_dict() for drop in self.drops]}
        if self.rolls is not None:
            payload["rolls"] = self.rolls
        if self.bonus_rolls is not None:
            payload["bonusRolls"] = self.bonus_rolls
        if self.chance is not None:
            payload["chance"] = self.chance
        if self.conditions:
            payload["conditions"] = self.conditions.to_dict()
        return payload


@dataclass
class MobLootRewards:
    xp: int = 0
    skill_points: int = 0
    tokens: int = 0
    items: List[MobDrop] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "xp": self.xp,
            "skillPoints": self.skill_points,
            "tokens": self.tokens,
        }
        if self.items:
            payload["items"] = [drop.to_dict() for drop in self.items]
        return payload


@dataclass
class MobLootSpec:
    clear_vanilla: Optional[bool] = None
    guaranteed: List[MobDrop] = field(default_factory=list)
    drops: List[MobDrop] = field(default_factory=list)
    pools: List[MobLootPoolRef] = field(default_factory=list)
    bundles: List[MobLootBundle] = field(default_factory=list)
    rewards: Optional[MobLootRewards] = None
    rolls: Optional[int] = None
    bonus_rolls: Optional[int] = None
    luck_multiplier: Optional[float] = None
    announce_tiers: List[str] = field(default_factory=list)
    announce_template: Optional[str] = None
    deterministic: Optional[bool] = None
    seed_salt: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.clear_vanilla is not None:
            payload["clearVanilla"] = self.clear_vanilla
        if self.guaranteed:
            payload["guaranteed"] = [drop.to_dict() for drop in self.guaranteed]
        if self.drops:
            payload["drops"] = [drop.to_dict() for drop in self.drops]
        if self.pools:
            payload["pools"] = [pool.to_dict() for pool in self.pools]
        if self.bundles:
            payload["bundles"] = [bundle.to_dict() for bundle in self.bundles]
        if self.rewards:
            payload["rewards"] = self.rewards.to_dict()
        if self.rolls is not None:
            payload["rolls"] = self.rolls
        if self.bonus_rolls is not None:
            payload["bonusRolls"] = self.bonus_rolls
        if self.luck_multiplier is not None:
            payload["luckMultiplier"] = self.luck_multiplier
        if self.announce_tiers:
            payload["announceTiers"] = list(self.announce_tiers)
        if self.announce_template:
            payload["announceTemplate"] = self.announce_template
        if self.deterministic is not None:
            payload["deterministic"] = self.deterministic
        if self.seed_salt is not None:
            payload["seedSalt"] = self.seed_salt
        return payload


@dataclass
class MobSpawnRules:
    allowed_biomes: List[str] = field(default_factory=list)
    excluded_biomes: List[str] = field(default_factory=list)
    min_y: Optional[int] = None
    max_y: Optional[int] = None
    min_player_level: Optional[int] = None
    max_player_level: Optional[int] = None
    min_party_size: Optional[int] = None
    max_party_size: Optional[int] = None
    min_players: Optional[int] = None
    max_players: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.allowed_biomes:
            payload["allowedBiomes"] = list(self.allowed_biomes)
        if self.excluded_biomes:
            payload["excludedBiomes"] = list(self.excluded_biomes)
        if self.min_y is not None:
            payload["minY"] = self.min_y
        if self.max_y is not None:
            payload["maxY"] = self.max_y
        if self.min_player_level is not None:
            payload["minPlayerLevel"] = self.min_player_level
        if self.max_player_level is not None:
            payload["maxPlayerLevel"] = self.max_player_level
        if self.min_party_size is not None:
            payload["minPartySize"] = self.min_party_size
        if self.max_party_size is not None:
            payload["maxPartySize"] = self.max_party_size
        if self.min_players is not None:
            payload["minPlayers"] = self.min_players
        if self.max_players is not None:
            payload["maxPlayers"] = self.max_players
        return payload


@dataclass
class MobSpawnGroupEntry:
    mob_id: str
    weight: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        return {"mobId": self.mob_id, "weight": self.weight}


@dataclass
class MobSpawnGroup:
    chance: float = 1.0
    count: Optional[int] = None
    mobs: List[MobSpawnGroupEntry] = field(default_factory=list)
    rules: Optional[MobSpawnRules] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"chance": self.chance, "mobs": [entry.to_dict() for entry in self.mobs]}
        if self.count is not None:
            payload["count"] = self.count
        if self.rules:
            payload["rules"] = self.rules.to_dict()
        return payload


@dataclass
class MobSpawnerTemplateSpec:
    count: Optional[int] = None
    max_alive: Optional[int] = None
    group_id: Optional[str] = None
    group_max_alive: Optional[int] = None
    groups: List[MobSpawnGroup] = field(default_factory=list)
    respawn_ticks: Optional[int] = None
    respawn_jitter_ticks: Optional[int] = None
    lifespan_ticks: Optional[int] = None
    spawner_decay_ticks: Optional[int] = None
    radius: Optional[float] = None
    allow_block_damage: Optional[bool] = None
    activation_radius: Optional[float] = None
    beam_enabled: Optional[bool] = None
    beam_particle: Optional[Particle] = None
    beam_step: Optional[float] = None
    respect_difficulty: Optional[bool] = None
    respect_game_rules: Optional[bool] = None
    attack_radius: Optional[float] = None
    attack_ignore_outside_radius: Optional[bool] = None
    attack_ignore_players: Optional[bool] = None
    tether_radius: Optional[float] = None
    tether_action: Optional[MobSpawnTetherAction | str] = None
    tether_pull_speed: Optional[float] = None
    tether_despawn_ticks: Optional[int] = None
    max_alive_per_chunk: Optional[int] = None
    max_alive_per_player: Optional[int] = None
    rules: Optional[MobSpawnRules] = None
    hologram_enabled: Optional[bool] = None
    hologram_offset_y: Optional[float] = None
    hologram_format: Optional[str] = None
    enabled: Optional[bool] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.count is not None:
            payload["count"] = self.count
        if self.max_alive is not None:
            payload["maxAlive"] = self.max_alive
        if self.group_id:
            payload["group"] = self.group_id
        if self.group_max_alive is not None:
            payload["groupMaxAlive"] = self.group_max_alive
        if self.groups:
            payload["groups"] = [group.to_dict() for group in self.groups]
        if self.rules:
            payload["rules"] = self.rules.to_dict()
        if self.max_alive_per_chunk is not None or self.max_alive_per_player is not None:
            density: Dict[str, Any] = {}
            if self.max_alive_per_chunk is not None:
                density["maxAlivePerChunk"] = self.max_alive_per_chunk
            if self.max_alive_per_player is not None:
                density["maxAlivePerPlayer"] = self.max_alive_per_player
            payload["density"] = density
        if self.respawn_ticks is not None:
            payload["respawnTicks"] = self.respawn_ticks
        if self.respawn_jitter_ticks is not None:
            payload["respawnJitterTicks"] = self.respawn_jitter_ticks
        if self.lifespan_ticks is not None:
            payload["lifespanTicks"] = self.lifespan_ticks
        if self.spawner_decay_ticks is not None:
            payload["spawnerDecayTicks"] = self.spawner_decay_ticks
        if self.radius is not None:
            payload["radius"] = self.radius
        if self.allow_block_damage is not None:
            payload["allowBlockDamage"] = self.allow_block_damage
        if self.activation_radius is not None or self.respect_difficulty is not None or self.respect_game_rules is not None:
            activation: Dict[str, Any] = {}
            if self.activation_radius is not None:
                activation["radius"] = self.activation_radius
            if self.respect_difficulty is not None:
                activation["respectDifficulty"] = self.respect_difficulty
            if self.respect_game_rules is not None:
                activation["respectGameRules"] = self.respect_game_rules
            payload["activation"] = activation
        if self.beam_enabled is not None or self.beam_particle is not None or self.beam_step is not None:
            beam: Dict[str, Any] = {}
            if self.beam_enabled is not None:
                beam["enabled"] = self.beam_enabled
            if self.beam_particle is not None:
                beam["particle"] = _require_enum(self.beam_particle, "beam_particle", Particle)
            if self.beam_step is not None:
                beam["step"] = self.beam_step
            payload["beam"] = beam
        if (
            self.attack_radius is not None
            or self.attack_ignore_outside_radius is not None
            or self.attack_ignore_players is not None
        ):
            attack: Dict[str, Any] = {}
            if self.attack_radius is not None:
                attack["radius"] = self.attack_radius
            if self.attack_ignore_outside_radius is not None:
                attack["ignoreOutsideRadius"] = self.attack_ignore_outside_radius
            if self.attack_ignore_players is not None:
                attack["ignorePlayers"] = self.attack_ignore_players
            payload["attack"] = attack
        if (
            self.tether_radius is not None
            or self.tether_action is not None
            or self.tether_pull_speed is not None
            or self.tether_despawn_ticks is not None
        ):
            tether: Dict[str, Any] = {}
            if self.tether_radius is not None:
                tether["radius"] = self.tether_radius
            if self.tether_action is not None:
                tether["action"] = _enum_or_str(self.tether_action, "tether_action")
            if self.tether_pull_speed is not None:
                tether["pullSpeed"] = self.tether_pull_speed
            if self.tether_despawn_ticks is not None:
                tether["despawnTicks"] = self.tether_despawn_ticks
            payload["tether"] = tether
        if (
            self.hologram_enabled is not None
            or self.hologram_offset_y is not None
            or self.hologram_format is not None
        ):
            hologram: Dict[str, Any] = {}
            if self.hologram_enabled is not None:
                hologram["enabled"] = self.hologram_enabled
            if self.hologram_offset_y is not None:
                hologram["offsetY"] = self.hologram_offset_y
            if self.hologram_format is not None:
                hologram["format"] = self.hologram_format
            payload["hologram"] = hologram
        if self.enabled is not None:
            payload["enabled"] = self.enabled
        return payload


@dataclass
class MobSpawnSpec:
    spawn_id: str
    mob_id: str
    world: str
    location: Vec3
    yaw: float = 0.0
    pitch: float = 0.0
    count: int = 1
    max_alive: int = 3
    group_id: Optional[str] = None
    group_max_alive: int = 0
    groups: List[MobSpawnGroup] = field(default_factory=list)
    respawn_ticks: int = 200
    respawn_jitter_ticks: int = 0
    lifespan_ticks: int = 0
    spawner_decay_ticks: int = 0
    radius: float = 0.0
    activation_radius: float = 16.0
    allow_block_damage: bool = False
    beam_enabled: bool = False
    beam_particle: Optional[Particle] = None
    beam_step: float = 0.3
    respect_difficulty: bool = True
    respect_game_rules: bool = True
    attack_radius: float = 0.0
    attack_ignore_outside_radius: bool = False
    attack_ignore_players: bool = False
    tether_radius: float = 0.0
    tether_action: MobSpawnTetherAction | str = MobSpawnTetherAction.NONE
    tether_pull_speed: float = 0.35
    tether_despawn_ticks: int = 0
    max_alive_per_chunk: int = 0
    max_alive_per_player: int = 0
    enabled: bool = True
    rules: Optional[MobSpawnRules] = None
    hologram_enabled: bool = False
    hologram_offset_y: float = 2.3
    hologram_format: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "mob": self.mob_id,
            "world": self.world,
            "x": self.location.x,
            "y": self.location.y,
            "z": self.location.z,
            "yaw": self.yaw,
            "pitch": self.pitch,
            "count": self.count,
            "maxAlive": self.max_alive,
            "group": self.group_id,
            "groupMaxAlive": self.group_max_alive,
            "respawnTicks": self.respawn_ticks,
            "respawnJitterTicks": self.respawn_jitter_ticks,
            "lifespanTicks": self.lifespan_ticks,
            "spawnerDecayTicks": self.spawner_decay_ticks,
            "radius": self.radius,
            "activationRadius": self.activation_radius,
            "allowBlockDamage": self.allow_block_damage,
            "attackRadius": self.attack_radius,
            "attackIgnoreOutsideRadius": self.attack_ignore_outside_radius,
            "attackIgnorePlayers": self.attack_ignore_players,
            "tetherRadius": self.tether_radius,
            "tetherAction": _enum_or_str(self.tether_action, "tether_action"),
            "tetherPullSpeed": self.tether_pull_speed,
            "tetherDespawnTicks": self.tether_despawn_ticks,
            "maxAlivePerChunk": self.max_alive_per_chunk,
            "maxAlivePerPlayer": self.max_alive_per_player,
            "enabled": self.enabled,
        }
        if self.group_id is None:
            payload.pop("group")
        if not self.group_max_alive:
            payload.pop("groupMaxAlive")
        if self.rules:
            payload["rules"] = self.rules.to_dict()
        if self.groups:
            payload["groups"] = [group.to_dict() for group in self.groups]
        if self.beam_enabled or self.beam_particle is not None or abs(self.beam_step - 0.3) > 1e-9:
            beam: Dict[str, Any] = {"enabled": self.beam_enabled}
            if self.beam_particle is not None:
                beam["particle"] = _require_enum(self.beam_particle, "beam_particle", Particle)
            if abs(self.beam_step - 0.3) > 1e-9:
                beam["step"] = self.beam_step
            payload["beam"] = beam
        if self.hologram_enabled or self.hologram_format is not None:
            hologram: Dict[str, Any] = {"enabled": self.hologram_enabled}
            if abs(self.hologram_offset_y - 2.3) > 1e-9:
                hologram["offsetY"] = self.hologram_offset_y
            if self.hologram_format is not None:
                hologram["format"] = self.hologram_format
            payload["hologram"] = hologram
        return payload


@dataclass
class MobSpawnerBlock:
    spawner_id: str
    mob_id: str
    item: Optional[Any] = None
    material: Optional[Any] = None
    spawn: Optional[MobSpawnerTemplateSpec] = None
    count: Optional[int] = None
    max_alive: Optional[int] = None
    respawn_ticks: Optional[int] = None
    activation_radius: Optional[int] = None
    hologram: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"mob": snake_case(self.mob_id)}
        if self.item is not None:
            payload["item"] = self.item
        elif self.material is not None:
            payload["material"] = _enum_or_str(self.material, "material")
        template = self.spawn
        if template is None and any(
            value is not None
            for value in (self.count, self.max_alive, self.respawn_ticks, self.activation_radius, self.hologram)
        ):
            template = MobSpawnerTemplateSpec(
                count=self.count,
                max_alive=self.max_alive,
                respawn_ticks=self.respawn_ticks,
                activation_radius=float(self.activation_radius) if self.activation_radius is not None else None,
                hologram_format=self.hologram,
            )
        if template is not None:
            payload["spawn"] = template.to_dict()
        return payload


@dataclass
class MobEgg:
    egg_id: str
    mob_id: str
    name: Optional[str] = None
    item: Optional[Any] = None
    material: Optional[Any] = None
    amount: Optional[int] = None
    permission: Optional[str] = None
    cooldown_ticks: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"mob": snake_case(self.mob_id)}
        if self.item is not None:
            payload["item"] = self.item
        elif self.material is not None:
            payload["material"] = _enum_or_str(self.material, "material")
        if self.amount is not None:
            payload["amount"] = self.amount
        if self.permission is not None:
            payload["permission"] = self.permission
        if self.cooldown_ticks is not None:
            payload["cooldownTicks"] = self.cooldown_ticks
        return payload


@dataclass
class TrialSpawnerMobEntry:
    mob_id: str
    weight: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        return {"mobId": snake_case(self.mob_id), "weight": float(self.weight)}


@dataclass
class TrialSpawnerProfileSpec:
    mob_pool: List[TrialSpawnerMobEntry] = field(default_factory=list)
    waves: Optional[int] = None
    simultaneous: Optional[int] = None
    cooldown_ticks: Optional[int] = None
    key_loot_pool: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}
        if self.mob_pool:
            payload["mobPool"] = [entry.to_dict() for entry in self.mob_pool]
        if self.waves is not None:
            payload["waves"] = self.waves
        if self.simultaneous is not None:
            payload["simultaneous"] = self.simultaneous
        if self.cooldown_ticks is not None:
            payload["cooldownTicks"] = self.cooldown_ticks
        if self.key_loot_pool:
            payload["keyLootPool"] = snake_case(self.key_loot_pool)
        return payload


@dataclass
class TrialSpawnerSpec:
    trial_spawner_id: str
    mob_pool: List[TrialSpawnerMobEntry] = field(default_factory=list)
    waves: int = 1
    simultaneous: int = 1
    cooldown_ticks: int = 0
    required_players: int = 1
    activation_range: float = 12.0
    key_loot_pool: str = ""
    ominous_profile: Optional[TrialSpawnerProfileSpec] = None

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "mobPool": [entry.to_dict() for entry in self.mob_pool],
            "waves": self.waves,
            "simultaneous": self.simultaneous,
            "cooldownTicks": self.cooldown_ticks,
            "requiredPlayers": self.required_players,
            "activationRange": self.activation_range,
            "keyLootPool": snake_case(self.key_loot_pool),
        }
        if self.ominous_profile is not None:
            ominous = self.ominous_profile.to_dict()
            if ominous:
                payload["ominousProfile"] = ominous
        return payload


@dataclass
class VaultDisplayItemEntry:
    item_id: str
    weight: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        return {"itemId": snake_case(self.item_id), "weight": float(self.weight)}


@dataclass
class VaultSpec:
    vault_id: str
    key_item: str
    loot_pool_normal: str
    loot_pool_ominous: str
    activation_range: float = 5.0
    deactivation_range: float = 8.0
    displayed_item_pool: List[VaultDisplayItemEntry] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "keyItem": snake_case(self.key_item),
            "lootPoolNormal": snake_case(self.loot_pool_normal),
            "lootPoolOminous": snake_case(self.loot_pool_ominous),
            "activationRange": self.activation_range,
            "deactivationRange": self.deactivation_range,
        }
        if self.displayed_item_pool:
            payload["displayedItemPool"] = [entry.to_dict() for entry in self.displayed_item_pool]
        return payload


class TrialSpawnerBuilder(BuilderBase):
    def __init__(self, trial_spawner_id: str) -> None:
        super().__init__(_id=trial_spawner_id)
        self._mob_pool: List[TrialSpawnerMobEntry] = []
        self._waves: int = 1
        self._simultaneous: int = 1
        self._cooldown_ticks: int = 0
        self._required_players: int = 1
        self._activation_range: float = 12.0
        self._key_loot_pool: Optional[str] = None
        self._ominous_profile: Optional[TrialSpawnerProfileSpec] = None

    def mob(self, mob_id: str, weight: float = 1.0) -> "TrialSpawnerBuilder":
        self._mob_pool.append(TrialSpawnerMobEntry(mob_id=mob_id, weight=weight))
        return self

    def mobs(self, entries: Iterable[TrialSpawnerMobEntry]) -> "TrialSpawnerBuilder":
        self._mob_pool.extend(entries)
        return self

    def waves(self, value: int) -> "TrialSpawnerBuilder":
        self._waves = int(value)
        return self

    def simultaneous(self, value: int) -> "TrialSpawnerBuilder":
        self._simultaneous = int(value)
        return self

    def cooldown_ticks(self, value: int) -> "TrialSpawnerBuilder":
        self._cooldown_ticks = int(value)
        return self

    def required_players(self, value: int) -> "TrialSpawnerBuilder":
        self._required_players = int(value)
        return self

    def activation_range(self, value: float) -> "TrialSpawnerBuilder":
        self._activation_range = float(value)
        return self

    def key_loot_pool(self, pool_id: str) -> "TrialSpawnerBuilder":
        self._key_loot_pool = pool_id
        return self

    def ominous_profile(self, profile: TrialSpawnerProfileSpec) -> "TrialSpawnerBuilder":
        self._ominous_profile = profile
        return self

    def build(self) -> Dict[str, Any]:
        trial_spawner_id = self._ensure_id("trial_spawner_id")
        if not self._mob_pool:
            raise ValueError("trial spawner mobPool requires at least one entry")
        if not self._key_loot_pool:
            raise ValueError("trial spawner keyLootPool is required")
        spec = TrialSpawnerSpec(
            trial_spawner_id=trial_spawner_id,
            mob_pool=self._mob_pool,
            waves=self._waves,
            simultaneous=self._simultaneous,
            cooldown_ticks=self._cooldown_ticks,
            required_players=self._required_players,
            activation_range=self._activation_range,
            key_loot_pool=self._key_loot_pool,
            ominous_profile=self._ominous_profile,
        )
        return self._apply_overrides(spec.to_dict(), f"trialSpawner:{trial_spawner_id}")


class VaultBuilder(BuilderBase):
    def __init__(self, vault_id: str) -> None:
        super().__init__(_id=vault_id)
        self._key_item: Optional[str] = None
        self._loot_pool_normal: Optional[str] = None
        self._loot_pool_ominous: Optional[str] = None
        self._activation_range: float = 5.0
        self._deactivation_range: float = 8.0
        self._displayed_item_pool: List[VaultDisplayItemEntry] = []

    def key_item(self, item_id: str) -> "VaultBuilder":
        self._key_item = item_id
        return self

    def loot_pools(self, normal: str, ominous: str) -> "VaultBuilder":
        self._loot_pool_normal = normal
        self._loot_pool_ominous = ominous
        return self

    def activation_range(self, value: float) -> "VaultBuilder":
        self._activation_range = float(value)
        return self

    def deactivation_range(self, value: float) -> "VaultBuilder":
        self._deactivation_range = float(value)
        return self

    def display_item(self, item_id: str, weight: float = 1.0) -> "VaultBuilder":
        self._displayed_item_pool.append(VaultDisplayItemEntry(item_id=item_id, weight=weight))
        return self

    def build(self) -> Dict[str, Any]:
        vault_id = self._ensure_id("vault_id")
        if not self._key_item:
            raise ValueError("vault keyItem is required")
        if not self._loot_pool_normal or not self._loot_pool_ominous:
            raise ValueError("vault loot pools are required")
        spec = VaultSpec(
            vault_id=vault_id,
            key_item=self._key_item,
            loot_pool_normal=self._loot_pool_normal,
            loot_pool_ominous=self._loot_pool_ominous,
            activation_range=self._activation_range,
            deactivation_range=self._deactivation_range,
            displayed_item_pool=self._displayed_item_pool,
        )
        return self._apply_overrides(spec.to_dict(), f"vault:{vault_id}")


def weighted_mob(mob_id: str, weight: float = 1.0) -> TrialSpawnerMobEntry:
    return TrialSpawnerMobEntry(mob_id=mob_id, weight=weight)


def weighted_item(item_id: str, weight: float = 1.0) -> VaultDisplayItemEntry:
    return VaultDisplayItemEntry(item_id=item_id, weight=weight)


class MobBuilder(BuilderBase):
    def __init__(self, mob_id: str) -> None:
        super().__init__(_id=mob_id)
        self._type: Optional[str] = None
        self._tier: Optional[str] = None
        self._show_name: Optional[bool] = None
        self._stats: Dict[str, Any] = {}
        self._style_preset: Optional[str] = None
        self._style: Optional[MobStyleSpec] = None
        self._bossbar: Optional[MobBossBarSpec] = None
        self._model: Optional[MobModelSpec] = None
        self._boss_broadcast: Optional[MobBroadcastSpec] = None
        self._gui_preview: Optional[MobGuiPreviewSpec] = None
        self._collidable: Optional[bool] = None
        self._ai: Optional[MobAiSpec] = None
        self._attacks: Dict[str, MobAttack] = {}
        self._passives: List[MobPassive] = []
        self._loot: Optional[MobLootSpec] = None
        self._phases: List[MobPhase] = []
        self._spawn_particles: Optional[MobParticlesSpec] = None
        self._spawn_sound: Optional[MobSoundSpec] = None
        self._death_particles: Optional[MobParticlesSpec] = None
        self._death_sound: Optional[MobSoundSpec] = None
        self._equipment: Optional[MobEquipmentSpec] = None
        self._visual: Optional[MobVisualSpec] = None
        self._variants: List[MobVariantSpec] = []
        self._traits: List[MobTraitSpec] = []
        self._resistances: Dict[str, float] = {}
        self._immunities: List[str] = []
        self._composite: Optional[MobCompositeSpec] = None
        self._progression: Optional[MobProgressionSpec] = None
        self._advancement_rewards: Optional[MobAdvancementRewardSpec] = None
        self._min_xp_level: Optional[int] = None
        self._summon: Optional[MobSummonSpec] = None
        self._allow_block_damage: Optional[bool] = None
        self._mana_drop: Optional[MobManaDropSpec] = None
        self._mana_drain: Optional[MobManaDrainSpec] = None

    def archetype(self, archetype_id: str) -> "MobBuilder":
        preset = archetype_id.strip().lower()
        if preset == "brute":
            self.stats(health=30, damage=6, armor=2, speed=0.22)
        elif preset == "skirmisher":
            self.stats(health=18, damage=4, armor=1, speed=0.28)
        elif preset == "tank":
            self.stats(health=60, damage=5, armor=6, speed=0.18)
        return self

    def boss_basic(
        self,
        hp: float,
        damage: float,
        element: Optional[str] = None,
        difficulty: Optional[str] = None,
    ) -> "MobBuilder":
        if self._name is None and self._id:
            self._name = self._id
        self.show_name(True)
        self.stats(health=hp, damage=damage)
        if difficulty:
            self.tier(difficulty)
        color_map = {
            "fire": "RED",
            "frost": "BLUE",
            "ice": "BLUE",
            "shadow": "PURPLE",
            "nature": "GREEN",
            "holy": "YELLOW",
        }
        color = color_map.get((element or "").lower())
        title = self._name or (self._id or "Boss")
        self.bossbar(MobBossBarSpec(title=title, color=color))
        return self

    def theme(self, theme_id: str) -> "MobBuilder":
        self._style_preset = theme_id
        return self

    def gear_full(self, tier: str) -> "MobBuilder":
        key = tier.strip().lower()
        material_map = {
            "leather": ("LEATHER", "LEATHER"),
            "chain": ("CHAINMAIL", "CHAINMAIL"),
            "iron": ("IRON", "IRON"),
            "gold": ("GOLDEN", "GOLDEN"),
            "diamond": ("DIAMOND", "DIAMOND"),
            "netherite": ("NETHERITE", "NETHERITE"),
        }
        prefix, sword_prefix = material_map.get(key, ("IRON", "IRON"))
        self.head(getattr(Material, f"{prefix}_HELMET"))
        self.chest(getattr(Material, f"{prefix}_CHESTPLATE"))
        self.legs(getattr(Material, f"{prefix}_LEGGINGS"))
        self.feet(getattr(Material, f"{prefix}_BOOTS"))
        self.main_hand(getattr(Material, f"{sword_prefix}_SWORD"))
        return self

    def loot_bundle(self, bundle_id: str) -> "MobBuilder":
        loot = self._loot or MobLootSpec()
        loot.pools.append(MobLootPoolRef(pool_id=bundle_id))
        self._loot = loot
        return self

    def mana_drop(self, tier: Optional[str] = None, streak: Optional[int] = None) -> "MobBuilder":
        spec = self._mana_drop or MobManaDropSpec()
        if tier:
            tier_key = tier.strip().lower()
            multiplier = {
                "t1": 1.0,
                "t2": 1.5,
                "t3": 2.0,
                "t4": 3.0,
            }.get(tier_key, 1.0)
            spec.tiers = [MobManaTier(weight=1.0, min_multiplier=multiplier)]
        if streak:
            spec.streak = MobManaStreak(max_stacks=int(streak), multiplier=1.1)
        self._mana_drop = spec
        return self

    def mob_type(self, entity_type: EntityType | str) -> "MobBuilder":
        if isinstance(entity_type, str):
            self._type = normalize_enum_name(parse_enum(EntityType, entity_type, label="entity_type").name)
        else:
            self._type = _require_enum(entity_type, "entity_type", EntityType)
        return self

    def tier(self, tier: str) -> "MobBuilder":
        self._tier = tier
        return self

    def show_name(self, value: bool) -> "MobBuilder":
        self._show_name = value
        return self

    def style_preset(self, preset_id: str) -> "MobBuilder":
        self._style_preset = preset_id
        return self

    def style(self, spec: MobStyleSpec) -> "MobBuilder":
        self._style = spec
        return self

    def bossbar(self, spec: MobBossBarSpec) -> "MobBuilder":
        self._bossbar = spec
        return self

    def model(self, spec: MobModelSpec) -> "MobBuilder":
        self._model = spec
        return self

    def model_full_replacement(
        self,
        model_id: Optional[str] = None,
        *,
        provider: str = "model_engine",
        hide_base: bool = True,
        animation: Optional[str] = None,
        animation_speed: float = 1.0,
        animations: Optional[Mapping[str, str]] = None,
    ) -> "MobBuilder":
        if not model_id:
            raise ValueError("model_full_replacement requires model_id")
        self._model = MobModelSpec(
            model_id=model_id,
            provider=provider,
            replace_visual=True,
            hide_base_entity=hide_base,
            animation=animation,
            animation_speed=animation_speed,
            animations=animations,
        )
        return self

    def boss_broadcast(self, spec: MobBroadcastSpec) -> "MobBuilder":
        self._boss_broadcast = spec
        return self

    def gui_preview(self, preview: MobGuiPreviewSpec) -> "MobBuilder":
        self._gui_preview = preview
        return self

    def gui_preview_head(self, head_id: str) -> "MobBuilder":
        preview = self._gui_preview or MobGuiPreviewSpec()
        preview.head = head_id
        self._gui_preview = preview
        return self

    def gui_preview_icon(self, icon: str) -> "MobBuilder":
        preview = self._gui_preview or MobGuiPreviewSpec()
        preview.icon = icon
        self._gui_preview = preview
        return self

    def auto_gui_preview(self) -> "MobBuilder":
        if self._gui_preview is None and self._name:
            self._gui_preview = MobGuiPreviewSpec(description=self._name)
        return self

    def gui_preview_description(self, value: str) -> "MobBuilder":
        preview = self._gui_preview or MobGuiPreviewSpec()
        preview.description = value
        self._gui_preview = preview
        return self

    def gui_preview_description_key(self, key: str) -> "MobBuilder":
        preview = self._gui_preview or MobGuiPreviewSpec()
        preview.description_key = key
        self._gui_preview = preview
        return self

    def gui_preview_tile(self, tile: GuiTileSpec) -> "MobBuilder":
        preview = self._gui_preview or MobGuiPreviewSpec()
        if tile.head:
            preview.head = tile.head
        if tile.icon:
            preview.icon = tile.icon
        if tile.description_key:
            preview.description_key = tile.description_key
        elif tile.description:
            preview.description = tile.description
        self._gui_preview = preview
        return self

    def collidable(self, value: bool) -> "MobBuilder":
        self._collidable = value
        return self

    def stats(
        self,
        *,
        health: Optional[float] = None,
        damage: Optional[float] = None,
        speed: Optional[float] = None,
        armor: Optional[float] = None,
        follow_range: Optional[float] = None,
        attack_speed: Optional[float] = None,
        attack_knockback: Optional[float] = None,
        armor_toughness: Optional[float] = None,
        knockback_resistance: Optional[float] = None,
        jump_strength: Optional[float] = None,
        flying_speed: Optional[float] = None,
        scale: Optional[float] = None,
        step_height: Optional[float] = None,
        luck: Optional[float] = None,
        **values: Any,
    ) -> "MobBuilder":
        """Set mob stats using explicit typed kwargs + free-form aliases.

        Explicit kwargs are the preferred API. Extra kwargs in ``values`` are still
        supported for compatibility and advanced attributes.
        """
        explicit: Dict[Attribute, Optional[float]] = {
            Attribute.MAX_HEALTH: health,
            Attribute.ATTACK_DAMAGE: damage,
            Attribute.MOVEMENT_SPEED: speed,
            Attribute.ARMOR: armor,
            Attribute.FOLLOW_RANGE: follow_range,
            Attribute.ATTACK_SPEED: attack_speed,
            Attribute.ATTACK_KNOCKBACK: attack_knockback,
            Attribute.ARMOR_TOUGHNESS: armor_toughness,
            Attribute.KNOCKBACK_RESISTANCE: knockback_resistance,
            Attribute.JUMP_STRENGTH: jump_strength,
            Attribute.FLYING_SPEED: flying_speed,
            Attribute.SCALE: scale,
            Attribute.STEP_HEIGHT: step_height,
            Attribute.LUCK: luck,
        }
        for attr, value in explicit.items():
            if value is not None:
                self._stats[attr.name] = value

        alias_map: Dict[str, Attribute] = {
            "health": Attribute.MAX_HEALTH,
            "max_health": Attribute.MAX_HEALTH,
            "damage": Attribute.ATTACK_DAMAGE,
            "attack_damage": Attribute.ATTACK_DAMAGE,
            "speed": Attribute.MOVEMENT_SPEED,
            "movement_speed": Attribute.MOVEMENT_SPEED,
            "armor": Attribute.ARMOR,
            "armor_toughness": Attribute.ARMOR_TOUGHNESS,
            "follow_range": Attribute.FOLLOW_RANGE,
            "attack_speed": Attribute.ATTACK_SPEED,
            "attack_knockback": Attribute.ATTACK_KNOCKBACK,
            "knockback_resistance": Attribute.KNOCKBACK_RESISTANCE,
            "jump_strength": Attribute.JUMP_STRENGTH,
            "flying_speed": Attribute.FLYING_SPEED,
            "scale": Attribute.SCALE,
            "step_height": Attribute.STEP_HEIGHT,
            "luck": Attribute.LUCK,
        }

        for key, value in values.items():
            if isinstance(key, Attribute):
                attr = key.name
            else:
                key_str = str(key).strip()
                key_norm = key_str.lower().replace("-", "_")
                mapped = alias_map.get(key_norm)
                if mapped is not None:
                    attr = mapped.name
                else:
                    # Accept raw attribute names too (e.g. "MAX_HEALTH" / "max_health").
                    try:
                        attr = parse_enum(Attribute, key_str, label="attribute").name
                    except Exception:
                        # Keep custom stat keys available for advanced payloads.
                        attr = key_str
            self._stats[attr] = value
        return self

    def attribute(self, attribute: Attribute | str, value: float) -> "MobBuilder":
        self._stats[_enum_or_str(attribute, "attribute")] = value
        return self

    def scale_variance(self, variance: float) -> "MobBuilder":
        self._stats["scaleVariance"] = variance
        return self

    def ai(self, spec: MobAiSpec) -> "MobBuilder":
        self._ai = spec
        return self

    def ai_simple(self, profile: MobAiProfile | str, **overrides: Any) -> "MobBuilder":
        """Configure simple AI from a profile plus optional field overrides."""
        spec = MobAiSpec(
            version=MobAiVersion.V3,
            engine=MobAiEngine.V3,
            profile=profile,
            enabled=True,
        )
        aliases = {
            "locomotion": "locomotion_mode",
            "stateTransitionCooldownTicks": "state_transition_cooldown_ticks",
            "callForHelpRadius": "call_for_help_radius",
            "assistRadius": "assist_radius",
        }
        for key, value in overrides.items():
            target_key = aliases.get(key, key)
            if not hasattr(spec, target_key):
                raise ValueError(f"unknown ai override field: {key}")
            setattr(spec, target_key, value)
        self._ai = spec
        return self

    def ai_advanced(self, spec: MobAiSpec) -> "MobBuilder":
        self._ai = spec
        return self

    def ai_profile_v3(self, profile: MobAiProfile | str, **overrides: Any) -> "MobBuilder":
        spec = MobAiSpec(
            version=MobAiVersion.V3,
            engine=MobAiEngine.V3,
            profile=profile,
            enabled=True,
        )
        for key, value in overrides.items():
            if not hasattr(spec, key):
                raise ValueError(f"unknown ai v3 override field: {key}")
            setattr(spec, key, value)
        self._ai = spec
        return self

    def ai_selector(
        self,
        selector_id: str,
        *,
        base_score: int = 50,
        actions: Optional[List[MobAiGoalSpec]] = None,
    ) -> "MobBuilder":
        spec = self._ai or MobAiSpec(version=MobAiVersion.V3, engine=MobAiEngine.V3, enabled=True)
        selectors = list(spec.utility_selectors)
        selectors.append(
            MobAiUtilitySelectorSpec(
                selector_id=selector_id,
                base_score=base_score,
                actions=list(actions or []),
            )
        )
        spec.utility_selectors = selectors
        self._ai = spec
        return self

    def main_attack(self, attack: MobAttack) -> "MobBuilder":
        self._attacks["main"] = attack
        return self

    def secondary_attack(self, attack: MobAttack) -> "MobBuilder":
        self._attacks["secondary"] = attack
        return self

    def passive(self, passive: MobPassive) -> "MobBuilder":
        self._passives.append(passive)
        return self

    def attack(
        self,
        ability: str,
        trigger: MobAttackTrigger | str = MobAttackTrigger.MELEE,
        cooldown_ticks: int = 40,
        range_blocks: float = 10.0,
        chance: float = 1.0,
    ) -> "MobBuilder":
        """Novice helper: create a default main/secondary attack from an ability id."""
        attack = MobAttack(
            ability=ability,
            trigger=trigger,
            cooldown_ticks=cooldown_ticks,
            range_blocks=range_blocks,
            chance=chance,
        )
        if "main" not in self._attacks:
            return self.main_attack(attack)
        return self.secondary_attack(attack)

    def loot(self, loot: MobLootSpec) -> "MobBuilder":
        self._loot = loot
        return self

    def equipment(self, spec: MobEquipmentSpec) -> "MobBuilder":
        self._equipment = spec
        return self

    def visual(self, spec: MobVisualSpec) -> "MobBuilder":
        self._visual = spec
        return self

    def phase_visual(self, phase_id: str, spec: MobVisualSpec) -> "MobBuilder":
        for phase in self._phases:
            if phase.phase_id == phase_id:
                phase.visual = spec
                return self
        raise ValueError(f"unknown phase_id: {phase_id}")

    def phase_model(self, phase_id: str, spec: MobModelSpec) -> "MobBuilder":
        for phase in self._phases:
            if phase.phase_id == phase_id:
                phase.model = spec
                return self
        raise ValueError(f"unknown phase_id: {phase_id}")

    def phase_ai(
        self,
        phase_id: str,
        spec: MobAiSpec,
        merge_mode: MobAiPhaseMergeMode | str = MobAiPhaseMergeMode.PATCH,
    ) -> "MobBuilder":
        for phase in self._phases:
            if phase.phase_id == phase_id:
                phase.ai = spec
                phase.ai_merge_mode = merge_mode
                return self
        raise ValueError(f"unknown phase_id: {phase_id}")

    def phase_ai_v3(
        self,
        phase_id: str,
        spec: MobAiSpec,
        merge_mode: MobAiPhaseMergeMode | str = MobAiPhaseMergeMode.PATCH,
    ) -> "MobBuilder":
        spec.version = spec.version or MobAiVersion.V3
        spec.engine = spec.engine or MobAiEngine.V3
        return self.phase_ai(phase_id, spec, merge_mode)

    def main_hand(self, item: Any) -> "MobBuilder":
        spec = self._equipment or MobEquipmentSpec()
        spec.main_hand = item
        self._equipment = spec
        return self

    def off_hand(self, item: Any) -> "MobBuilder":
        spec = self._equipment or MobEquipmentSpec()
        spec.off_hand = item
        self._equipment = spec
        return self

    def head(self, item: Any) -> "MobBuilder":
        spec = self._equipment or MobEquipmentSpec()
        spec.head = item
        self._equipment = spec
        return self

    def chest(self, item: Any) -> "MobBuilder":
        spec = self._equipment or MobEquipmentSpec()
        spec.chest = item
        self._equipment = spec
        return self

    def legs(self, item: Any) -> "MobBuilder":
        spec = self._equipment or MobEquipmentSpec()
        spec.legs = item
        self._equipment = spec
        return self

    def feet(self, item: Any) -> "MobBuilder":
        spec = self._equipment or MobEquipmentSpec()
        spec.feet = item
        self._equipment = spec
        return self

    def variant(self, spec: MobVariantSpec) -> "MobBuilder":
        self._variants.append(spec)
        return self

    def trait(self, spec: MobTraitSpec) -> "MobBuilder":
        self._traits.append(spec)
        return self

    def resistance(self, damage_type: DamageType | str, multiplier: float) -> "MobBuilder":
        self._resistances[_enum_or_str(damage_type, "damage_type")] = multiplier
        return self

    def immunity(self, damage_type: DamageType | str) -> "MobBuilder":
        self._immunities.append(_enum_or_str(damage_type, "damage_type"))
        return self

    def composite(self, spec: MobCompositeSpec) -> "MobBuilder":
        self._composite = spec
        return self

    def progression(self, spec: MobProgressionSpec) -> "MobBuilder":
        self._progression = spec
        return self

    def advancement_rewards(self, spec: MobAdvancementRewardSpec) -> "MobBuilder":
        self._advancement_rewards = spec
        return self

    def min_xp_level(self, level: int) -> "MobBuilder":
        self._min_xp_level = level
        return self

    def summon(self, spec: MobSummonSpec) -> "MobBuilder":
        self._summon = spec
        return self

    def allow_block_damage(self, allow: bool) -> "MobBuilder":
        self._allow_block_damage = allow
        return self

    def mana_drop(self, spec: MobManaDropSpec) -> "MobBuilder":
        self._mana_drop = spec
        return self

    def mana_drain(self, spec: MobManaDrainSpec) -> "MobBuilder":
        self._mana_drain = spec
        return self

    def spawn_particles(self, spec: MobParticlesSpec) -> "MobBuilder":
        self._spawn_particles = spec
        return self

    def spawn_sound(self, spec: MobSoundSpec) -> "MobBuilder":
        self._spawn_sound = spec
        return self

    def death_particles(self, spec: MobParticlesSpec) -> "MobBuilder":
        self._death_particles = spec
        return self

    def death_sound(self, spec: MobSoundSpec) -> "MobBuilder":
        self._death_sound = spec
        return self

    def phase(self, phase: MobPhase) -> "MobBuilder":
        self._phases.append(phase)
        return self

    def build(self) -> Dict[str, Any]:
        self._ensure_id("mob_id")
        self._ensure_name()
        if not self._type:
            raise ValueError("mob type is required")
        payload: Dict[str, Any] = {"type": self._type}
        if self._name:
            payload["name"] = self._name
        if self._tier:
            payload["tier"] = self._tier
        if self._show_name is not None:
            payload["showName"] = self._show_name
        if self._style_preset:
            payload["stylePreset"] = self._style_preset
        if self._style:
            payload["style"] = self._style.to_dict()
        if self._bossbar:
            payload["bossbar"] = self._bossbar.to_dict()
        if self._model:
            payload["model"] = self._model.to_dict()
        if self._boss_broadcast:
            payload["bossBroadcast"] = self._boss_broadcast.to_dict()
        if self._gui_preview:
            preview = self._gui_preview.to_dict()
            if preview:
                payload["gui"] = preview
        if self._collidable is not None:
            payload["collidable"] = self._collidable
        if self._stats:
            payload["stats"] = self._stats
        if self._ai:
            payload["ai"] = self._ai.to_dict()
        if self._attacks:
            payload["attacks"] = {key: attack.to_dict() for key, attack in self._attacks.items()}
        if self._passives:
            payload["passives"] = [entry.to_dict() for entry in self._passives]
        if self._loot:
            payload["loot"] = self._loot.to_dict()
        if self._equipment:
            payload["equipment"] = self._equipment.to_dict()
        if self._visual:
            payload["visual"] = self._visual.to_dict()
        if self._variants:
            payload["variants"] = [variant.to_dict() for variant in self._variants]
        if self._traits:
            payload["traits"] = [trait.to_dict() for trait in self._traits]
        if self._resistances:
            payload["resistances"] = dict(self._resistances)
        if self._immunities:
            payload["immunities"] = list(self._immunities)
        if self._composite:
            payload["composite"] = self._composite.to_dict()
        if self._progression:
            payload["progression"] = self._progression.to_dict()
        if self._advancement_rewards:
            payload["advancementRewards"] = self._advancement_rewards.to_dict()
        if self._min_xp_level is not None:
            payload["xpGating"] = {"minLevel": self._min_xp_level}
        if self._summon:
            payload["summon"] = self._summon.to_dict()
        if self._allow_block_damage is not None:
            payload["allowBlockDamage"] = self._allow_block_damage
        if self._mana_drop:
            payload["manaDrops"] = self._mana_drop.to_dict()
        if self._mana_drain:
            payload["manaDrain"] = self._mana_drain.to_dict()
        if self._spawn_particles or self._spawn_sound:
            spawn_fx: Dict[str, Any] = {}
            if self._spawn_particles:
                spawn_fx["particles"] = self._spawn_particles.to_dict()
            if self._spawn_sound:
                spawn_fx["sound"] = self._spawn_sound.to_dict()
            payload["spawnFx"] = spawn_fx
        if self._death_particles or self._death_sound:
            death_fx: Dict[str, Any] = {}
            if self._death_particles:
                death_fx["particles"] = self._death_particles.to_dict()
            if self._death_sound:
                death_fx["sound"] = self._death_sound.to_dict()
            payload["deathFx"] = death_fx
        if self._phases:
            payload["phases"] = [phase.to_dict() for phase in self._phases]
        return self._apply_overrides(payload, f"mob:{self._id}")


class MobExporter(ExporterBase):
    def write_mob(
        self,
        builder: MobBuilder,
        filename: Optional[str] = None,
        style_presets: Optional[Mapping[str, MobStyleSpec]] = None,
    ) -> str:
        data: Dict[str, Any] = {
            "schemaVersion": 1,
            "mobs": {
                builder._id: builder.build(),
            },
        }
        if style_presets:
            data["stylePresets"] = {key: spec.to_dict() for key, spec in style_presets.items()}
        name = filename or f"{builder._id}.yml"
        return self.write_yaml(name, data)

    def write_batch(
        self,
        builders: Iterable[MobBuilder],
        filename: str,
        spawners: Optional[Iterable[MobSpawnerBlock]] = None,
        eggs: Optional[Iterable[MobEgg]] = None,
        spawns: Optional[Iterable[MobSpawnSpec]] = None,
        trial_spawners: Optional[Iterable[TrialSpawnerBuilder | TrialSpawnerSpec | Mapping[str, Any]]] = None,
        vaults: Optional[Iterable[VaultBuilder | VaultSpec | Mapping[str, Any]]] = None,
        style_presets: Optional[Mapping[str, MobStyleSpec]] = None,
    ) -> str:
        mobs_payload = {builder._id: builder.build() for builder in builders}
        data: Dict[str, Any] = {"schemaVersion": 1, "mobs": mobs_payload}
        if style_presets:
            data["stylePresets"] = {key: spec.to_dict() for key, spec in style_presets.items()}
        if spawners:
            data["spawnerBlocks"] = {snake_case(entry.spawner_id): entry.to_dict() for entry in spawners}
        if eggs:
            data["eggs"] = {snake_case(entry.egg_id): entry.to_dict() for entry in eggs}
        if spawns:
            data["spawns"] = {snake_case(entry.spawn_id): entry.to_dict() for entry in spawns}
        if trial_spawners:
            entries: Dict[str, Any] = {}
            for entry in trial_spawners:
                if isinstance(entry, TrialSpawnerBuilder):
                    trial_id = snake_case(entry._id or "")
                    payload = entry.build()
                elif isinstance(entry, TrialSpawnerSpec):
                    trial_id = snake_case(entry.trial_spawner_id)
                    payload = entry.to_dict()
                else:
                    trial_id = snake_case(str(entry.get("id") or entry.get("trialSpawnerId") or ""))
                    payload = dict(entry)
                if trial_id:
                    entries[trial_id] = payload
            if entries:
                data["trialSpawners"] = dict(sorted(entries.items(), key=lambda item: item[0]))
        if vaults:
            entries: Dict[str, Any] = {}
            for entry in vaults:
                if isinstance(entry, VaultBuilder):
                    vault_id = snake_case(entry._id or "")
                    payload = entry.build()
                elif isinstance(entry, VaultSpec):
                    vault_id = snake_case(entry.vault_id)
                    payload = entry.to_dict()
                else:
                    vault_id = snake_case(str(entry.get("id") or entry.get("vaultId") or ""))
                    payload = dict(entry)
                if vault_id:
                    entries[vault_id] = payload
            if entries:
                data["vaults"] = dict(sorted(entries.items(), key=lambda item: item[0]))
        return self.write_yaml(filename, data)


def undead_t1_pack(prefix: str = "undead_t1") -> List[MobBuilder]:
    return [
        MobBuilder(f"{prefix}_bonewalker").mob_type(EntityType.ZOMBIE).name("<gray>Bonewalker</gray>").stats(health=20, damage=4),
        MobBuilder(f"{prefix}_graveshade").mob_type(EntityType.SKELETON).name("<dark_gray>Graveshade</dark_gray>").stats(health=18, damage=5),
    ]


def elemental_fire_pack(prefix: str = "fire_t1") -> List[MobBuilder]:
    return [
        MobBuilder(f"{prefix}_blazeling").mob_type(EntityType.BLAZE).name("<red>Blazeling</red>").stats(health=24, damage=5),
        MobBuilder(f"{prefix}_emberling").mob_type(EntityType.MAGMA_CUBE).name("<gold>Emberling</gold>").stats(health=22, damage=4),
    ]


def forest_t1_pack(prefix: str = "forest_t1") -> List[MobBuilder]:
    return [
        MobBuilder(f"{prefix}_mossling")
        .mob_type(EntityType.ZOMBIE)
        .name("<green>Mossling</green>")
        .tier("T1")
        .stats(health=18, damage=3),
        MobBuilder(f"{prefix}_bark_shade")
        .mob_type(EntityType.SKELETON)
        .name("<dark_green>Bark Shade</dark_green>")
        .tier("T1")
        .stats(health=16, damage=4),
    ]


def desert_t1_pack(prefix: str = "desert_t1") -> List[MobBuilder]:
    return [
        MobBuilder(f"{prefix}_sandskulk")
        .mob_type(EntityType.HUSK)
        .name("<gold>Sandskulk</gold>")
        .tier("T1")
        .stats(health=20, damage=4),
        MobBuilder(f"{prefix}_sun_bleached")
        .mob_type(EntityType.STRAY)
        .name("<yellow>Sun-Bleached</yellow>")
        .tier("T1")
        .stats(health=17, damage=4),
    ]


def nether_t2_pack(prefix: str = "nether_t2") -> List[MobBuilder]:
    return [
        MobBuilder(f"{prefix}_ember_stalker")
        .mob_type(EntityType.BLAZE)
        .name("<red>Ember Stalker</red>")
        .tier("T2")
        .stats(health=32, damage=7),
        MobBuilder(f"{prefix}_ashbone")
        .mob_type(EntityType.WITHER_SKELETON)
        .name("<gray>Ashbone</gray>")
        .tier("T2")
        .stats(health=28, damage=8),
    ]


def Mob(mob_id: str) -> MobBuilder:
    return MobBuilder(mob_id)


def TrialSpawner(trial_spawner_id: str) -> TrialSpawnerBuilder:
    return TrialSpawnerBuilder(trial_spawner_id)


def Vault(vault_id: str) -> VaultBuilder:
    return VaultBuilder(vault_id)
