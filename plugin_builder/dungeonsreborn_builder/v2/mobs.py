"""Concise mob authoring for builder v2."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any, Optional

from .core import BuildContext, BuildValidationError, Ref
from .enums import (
    EntityType,
    EntityTypeLike,
    Material,
    MaterialLike,
    MobAiAuthority,
    MobAiAuthorityLike,
    MobAiIntentType,
    MobAiIntentTypeLike,
    MobAiMovementPolicy,
    MobAiMovementPolicyLike,
    MobAiMode,
    MobAiModeLike,
    MobAiProfile,
    MobAiRuntimeModel,
    MobAiRuntimeModelLike,
    MobAiTargetSourceType,
    MobAiTargetSourceTypeLike,
    MobTierLike,
    MobSoundProfile,
    Sound,
    SoundLike,
    coerce_entity_type,
    coerce_enum,
    coerce_material,
    coerce_mob_ai_authority,
    coerce_mob_ai_intent_type,
    coerce_mob_ai_movement_policy,
    coerce_mob_ai_mode,
    coerce_mob_ai_runtime_model,
    coerce_mob_ai_target_source_type,
    coerce_mob_tier,
    coerce_sound,
)
from .internal.normalize import snake_case


@dataclass(frozen=True)
class TimedAbility:
    ability: Ref | str
    interval_ticks: int


@dataclass(frozen=True)
class BossbarSpec:
    title: str | None = None
    color: str | None = None
    style: str | None = None
    enabled: bool | None = None

    def build(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.title is not None:
            token = self.title.strip()
            if not token:
                raise ValueError("mobs.style.bossbar.title: cannot be empty")
            payload["title"] = token
        if self.color is not None:
            token = self.color.strip().upper()
            if not token:
                raise ValueError("mobs.style.bossbar.color: cannot be empty")
            payload["color"] = token
        if self.style is not None:
            token = self.style.strip().upper()
            if not token:
                raise ValueError("mobs.style.bossbar.style: cannot be empty")
            payload["style"] = token
        if self.enabled is not None:
            payload["enabled"] = bool(self.enabled)
        return payload


@dataclass(frozen=True)
class StyleSpec:
    name: str | None = None
    show_name: bool | None = None
    bossbar: BossbarSpec | None = None

    def build(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.name is not None:
            label = str(self.name).strip()
            if not label:
                raise ValueError("mobs.style.name: cannot be empty")
            payload["name"] = label
        if self.show_name is not None:
            payload["showName"] = bool(self.show_name)
        if self.bossbar is not None:
            payload["bossBar"] = self.bossbar.build()
        return payload


class AiConditionSpec:
    def build(self) -> Mapping[str, Any] | bool:
        raise NotImplementedError


@dataclass(frozen=True)
class AiBool(AiConditionSpec):
    value: bool

    def build(self) -> Mapping[str, Any] | bool:
        return bool(self.value)


@dataclass(frozen=True)
class AiPredicate(AiConditionSpec):
    key: str
    value: Any

    def build(self) -> Mapping[str, Any] | bool:
        token = self.key.strip()
        if not token:
            raise ValueError("mobs.ai.condition.predicate.key: cannot be empty")
        return {token: _deep_copy_value(self.value)}


@dataclass(frozen=True)
class AiAll(AiConditionSpec):
    parts: list[AiConditionSpec]

    def build(self) -> Mapping[str, Any] | bool:
        if not self.parts:
            raise ValueError("mobs.ai.condition.all: requires at least one part")
        return {"all": [entry.build() for entry in self.parts]}


@dataclass(frozen=True)
class AiAny(AiConditionSpec):
    parts: list[AiConditionSpec]

    def build(self) -> Mapping[str, Any] | bool:
        if not self.parts:
            raise ValueError("mobs.ai.condition.any: requires at least one part")
        return {"any": [entry.build() for entry in self.parts]}


@dataclass(frozen=True)
class AiNot(AiConditionSpec):
    part: AiConditionSpec

    def build(self) -> Mapping[str, Any] | bool:
        return {"not": self.part.build()}


class ai_condition:
    @staticmethod
    def bool(value: bool) -> AiConditionSpec:
        return AiBool(value=value)

    @staticmethod
    def predicate(key: str, value: Any) -> AiConditionSpec:
        return AiPredicate(key=key, value=value)

    @staticmethod
    def has_target(value: bool = True) -> AiConditionSpec:
        return AiPredicate(key="hasTarget", value=bool(value))

    @staticmethod
    def health_ratio_lte(value: float) -> AiConditionSpec:
        return AiPredicate(key="healthRatioLte", value=float(value))

    @staticmethod
    def health_ratio_gte(value: float) -> AiConditionSpec:
        return AiPredicate(key="healthRatioGte", value=float(value))

    @staticmethod
    def target_distance_lte(value: float) -> AiConditionSpec:
        return AiPredicate(key="targetDistanceLte", value=float(value))

    @staticmethod
    def target_distance_gte(value: float) -> AiConditionSpec:
        return AiPredicate(key="targetDistanceGte", value=float(value))

    @staticmethod
    def behavior_state(value: str) -> AiConditionSpec:
        token = str(value).strip()
        if not token:
            raise ValueError("mobs.ai.condition.behavior_state: cannot be empty")
        return AiPredicate(key="behaviorState", value=token)

    @staticmethod
    def random_chance(value: float) -> AiConditionSpec:
        return AiPredicate(key="randomChance", value=float(value))

    @staticmethod
    def all(*parts: AiConditionSpec) -> AiConditionSpec:
        return AiAll(parts=list(parts))

    @staticmethod
    def any(*parts: AiConditionSpec) -> AiConditionSpec:
        return AiAny(parts=list(parts))

    @staticmethod
    def not_(part: AiConditionSpec) -> AiConditionSpec:
        return AiNot(part=part)


@dataclass(frozen=True)
class AiIntentSpec:
    intent_type: MobAiIntentTypeLike
    speed: float | None = None
    radius: float | None = None
    min_range: float | None = None
    max_range: float | None = None
    interval_ticks: int | None = None
    ability: Ref | str | None = None
    cast_cooldown_ticks: int | None = None
    require_target: bool | None = None

    def build(self, ctx: BuildContext, *, field: str) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "type": coerce_mob_ai_intent_type(self.intent_type, field=f"{field}.type"),
        }
        if self.speed is not None:
            payload["speed"] = float(self.speed)
        if self.radius is not None:
            payload["radius"] = float(self.radius)
        if self.min_range is not None:
            payload["minRange"] = float(self.min_range)
        if self.max_range is not None:
            payload["maxRange"] = float(self.max_range)
        if self.interval_ticks is not None:
            payload["intervalTicks"] = int(self.interval_ticks)
        if self.ability is not None:
            payload["ability"] = ctx.resolve(self.ability, domain="ability", field=f"{field}.ability")
        if self.cast_cooldown_ticks is not None:
            payload["castCooldownTicks"] = int(self.cast_cooldown_ticks)
        if self.require_target is not None:
            payload["requireTarget"] = bool(self.require_target)
        return payload


@dataclass(frozen=True)
class AiSelectorSpec:
    intent: AiIntentSpec
    selector_id: str | None = None
    priority: int = 100
    when: AiConditionSpec | None = None

    def build(self, ctx: BuildContext, *, index: int, field: str) -> dict[str, Any]:
        return {
            "id": self.selector_id or f"selector_{index + 1}",
            "priority": int(self.priority),
            "when": True if self.when is None else self.when.build(),
            "intent": self.intent.build(ctx, field=f"{field}.intent"),
        }


@dataclass(frozen=True)
class AiTargetSourceSpec:
    source_type: MobAiTargetSourceTypeLike
    radius: float | None = None
    memory_ticks: int | None = None
    cooldown_ticks: int | None = None
    priority: int | None = None

    def build(self, *, field: str) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "type": coerce_mob_ai_target_source_type(self.source_type, field=f"{field}.type"),
        }
        if self.radius is not None:
            payload["radius"] = float(self.radius)
        if self.memory_ticks is not None:
            payload["memoryTicks"] = int(self.memory_ticks)
        if self.cooldown_ticks is not None:
            payload["cooldownTicks"] = int(self.cooldown_ticks)
        if self.priority is not None:
            payload["priority"] = int(self.priority)
        return payload


@dataclass(frozen=True)
class LootDropSpec:
    item: Ref | str | None = None
    material: MaterialLike | None = None
    chance: float = 100.0
    min_amount: int = 1
    max_amount: int | None = None
    tier: str | None = None
    guaranteed: bool = False
    amount: int = 1


@dataclass(frozen=True)
class SpawnSpec:
    spawn_id: str
    world: str
    x: float
    y: float
    z: float
    yaw: float = 0.0
    pitch: float = 0.0
    count: int = 1
    max_alive: int = 1
    respawn_ticks: int = 200
    radius: float = 0.0
    enabled: bool = True


_SOUND_PROFILES: dict[MobSoundProfile, dict[str, Sound]] = {
    MobSoundProfile.GHOST: {
        "spawn": Sound.ENTITY_ENDERMAN_TELEPORT,
        "death": Sound.ENTITY_PHANTOM_DEATH,
    },
    MobSoundProfile.UNDEAD: {
        "spawn": Sound.ENTITY_ZOMBIE_AMBIENT,
        "death": Sound.ENTITY_ZOMBIE_DEATH,
    },
}

_ALLOWED_AI_ENGINES = {"LEGACY", "V2", "V3"}
AbilityLike = Ref | str | Any


class MobV2:
    domain = "mob"

    def __init__(
        self,
        ctx: BuildContext,
        *,
        name: str,
        mob_type: EntityTypeLike,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> None:
        self.ctx = ctx
        self.id, self.symbol = ctx.register("mob", symbol=symbol or name, id_override=id, parts=[name])
        self._mob: dict[str, Any] = {
            "type": coerce_entity_type(mob_type, field=f"mobs.{self.id}.type"),
            "name": name,
            "showName": False,
        }
        self._loot_pools: dict[str, dict[str, Any]] = {}
        self._eggs: dict[str, dict[str, Any]] = {}
        self._spawner_blocks: dict[str, dict[str, Any]] = {}
        self._trial_spawners: dict[str, dict[str, Any]] = {}
        self._vault_profiles: dict[str, dict[str, Any]] = {}
        self._spawns: dict[str, dict[str, Any]] = {}
        self._ai_templates: dict[str, dict[str, Any]] = {}
        self._active_loot_pool_id: str | None = None
        self._bound_abilities: list[Any] = []

    def tier(self, value: MobTierLike) -> "MobV2":
        self._mob["tier"] = coerce_mob_tier(value, field=f"mobs.{self.id}.tier")
        return self

    def name(self, value: str) -> "MobV2":
        token = str(value).strip()
        if not token:
            raise ValueError(f"mobs.{self.id}.name: cannot be empty")
        self._mob["name"] = token
        return self

    def show_name(self, value: bool = True) -> "MobV2":
        self._mob["showName"] = bool(value)
        return self

    def style_preset(self, preset_id: str) -> "MobV2":
        token = str(preset_id).strip()
        if not token:
            raise ValueError(f"mobs.{self.id}.stylePreset: cannot be empty")
        self._mob["stylePreset"] = token
        return self

    def style(
        self,
        *,
        name: str | None = None,
        show_name: bool | None = None,
        bossbar: BossbarSpec | None = None,
    ) -> "MobV2":
        if bossbar is not None and not isinstance(bossbar, BossbarSpec):
            raise ValueError(f"mobs.{self.id}.style.bossbar: expected BossbarSpec")
        return self.style_spec(StyleSpec(name=name, show_name=show_name, bossbar=bossbar))

    def style_spec(self, spec: StyleSpec) -> "MobV2":
        style = self._mob.setdefault("style", {})
        if not isinstance(style, dict):
            raise ValueError(f"mobs.{self.id}.style: expected mapping")
        style.update(spec.build())
        return self

    def stats(
        self,
        *,
        health: Optional[float] = None,
        damage: Optional[float] = None,
        armor: Optional[float] = None,
        speed: Optional[float] = None,
        follow_range: Optional[float] = None,
    ) -> "MobV2":
        stats = self._mob.setdefault("stats", {})
        if health is not None:
            stats["MAX_HEALTH"] = float(health)
        if damage is not None:
            stats["ATTACK_DAMAGE"] = float(damage)
        if armor is not None:
            stats["ARMOR"] = float(armor)
        if speed is not None:
            stats["MOVEMENT_SPEED"] = float(speed)
        if follow_range is not None:
            stats["FOLLOW_RANGE"] = float(follow_range)
        return self

    def scale_variance(self, value: float) -> "MobV2":
        variance = float(value)
        if variance < 0.0:
            raise ValueError(f"mobs.{self.id}.scale_variance: must be >= 0")
        self._mob["scaleVariance"] = variance
        return self

    def scale_range(self, min_scale: float, max_scale: float) -> "MobV2":
        min_v = float(min_scale)
        max_v = float(max_scale)
        if min_v <= 0.0 or max_v <= 0.0:
            raise ValueError(f"mobs.{self.id}.scale_range: min/max must be > 0")
        if min_v > max_v:
            raise ValueError(f"mobs.{self.id}.scale_range: min_scale cannot be greater than max_scale")
        center = (min_v + max_v) / 2.0
        if abs(center - 1.0) > 1e-6:
            raise ValueError(
                f"mobs.{self.id}.scale_range: only symmetric ranges around 1.0 are supported by current runtime "
                "(example: 0.92-1.08)"
            )
        return self.scale_variance((max_v - min_v) / 2.0)

    def ai_quick(
        self,
        profile: MobAiProfile,
        *,
        aggro_radius: Optional[float] = None,
        chase_speed: Optional[float] = None,
        call_for_help_radius: Optional[float] = None,
        open_doors: Optional[bool] = None,
    ) -> "MobV2":
        ai: dict[str, Any] = {
            "version": "V3",
            "profile": coerce_enum(profile, MobAiProfile, field=f"mobs.{self.id}.ai.profile"),
        }
        if aggro_radius is not None:
            ai["aggroRadius"] = float(aggro_radius)
        if chase_speed is not None:
            ai["chaseSpeed"] = float(chase_speed)
        if call_for_help_radius is not None:
            ai["callForHelpRadius"] = float(call_for_help_radius)
        if open_doors is not None:
            ai["openDoors"] = bool(open_doors)
        self._mob["ai"] = ai
        return self

    def ai_v4(
        self,
        *,
        engine: str = "V3",
        mode: MobAiModeLike = MobAiMode.FULL_OVERRIDE,
        authority: MobAiAuthorityLike = MobAiAuthority.ABILITY_DRIVEN,
        profile: MobAiProfile | None = None,
        aggro_radius: Optional[float] = None,
        chase_speed: Optional[float] = None,
        flee_speed: Optional[float] = None,
        kite_speed: Optional[float] = None,
        kite_min_range: Optional[float] = None,
        call_for_help_radius: Optional[float] = None,
        assist_radius: Optional[float] = None,
        open_doors: Optional[bool] = None,
    ) -> "MobV2":
        ai = self._ensure_ai_v4()
        ai["engine"] = self._coerce_ai_engine(engine)
        ai.setdefault("control", {})["mode"] = coerce_mob_ai_mode(mode, field=f"mobs.{self.id}.ai.control.mode")
        ai.setdefault("combat", {})["authority"] = coerce_mob_ai_authority(
            authority, field=f"mobs.{self.id}.ai.combat.authority"
        )
        if profile is not None:
            ai["profile"] = coerce_enum(profile, MobAiProfile, field=f"mobs.{self.id}.ai.profile")
        if aggro_radius is not None:
            ai.setdefault("targeting", {})["aggroRadius"] = float(aggro_radius)
        if chase_speed is not None:
            ai.setdefault("navigation", {})["chaseSpeed"] = float(chase_speed)
        if flee_speed is not None:
            ai.setdefault("navigation", {})["fleeSpeed"] = float(flee_speed)
        if kite_speed is not None:
            ai.setdefault("navigation", {})["kiteSpeed"] = float(kite_speed)
        if kite_min_range is not None:
            ai.setdefault("navigation", {})["kiteMinRange"] = float(kite_min_range)
        if call_for_help_radius is not None:
            ai.setdefault("group", {})["callForHelpRadius"] = float(call_for_help_radius)
        if assist_radius is not None:
            ai.setdefault("group", {})["assistRadius"] = float(assist_radius)
        if open_doors is not None:
            ai.setdefault("navigation", {}).setdefault("interactions", {})["openDoors"] = bool(open_doors)
        return self

    def ai_runtime_model(self, model: MobAiRuntimeModelLike) -> "MobV2":
        ai = self._ensure_ai_v4()
        ai["runtimeModel"] = coerce_mob_ai_runtime_model(model, field=f"mobs.{self.id}.ai.runtimeModel")
        return self

    def ai_movement_policy(self, policy: MobAiMovementPolicyLike) -> "MobV2":
        ai = self._ensure_ai_v4()
        ai["movementPolicy"] = coerce_mob_ai_movement_policy(policy, field=f"mobs.{self.id}.ai.movementPolicy")
        return self

    def ai_target_source(
        self,
        source_type: AiTargetSourceSpec | MobAiTargetSourceTypeLike,
        *,
        radius: float | None = None,
        memory_ticks: int | None = None,
        cooldown_ticks: int | None = None,
        priority: int | None = None,
    ) -> "MobV2":
        ai = self._ensure_ai_v4()
        targeting = ai.setdefault("targeting", {})
        sources = targeting.setdefault("sources", [])
        if not isinstance(sources, list):
            raise ValueError(f"mobs.{self.id}.ai.targeting.sources: expected list")
        if isinstance(source_type, AiTargetSourceSpec):
            payload = source_type.build(field=f"mobs.{self.id}.ai.targeting.sources[{len(sources)}]")
        else:
            payload = AiTargetSourceSpec(
                source_type,
                radius=radius,
                memory_ticks=memory_ticks,
                cooldown_ticks=cooldown_ticks,
                priority=priority,
            ).build(field=f"mobs.{self.id}.ai.targeting.sources[{len(sources)}]")
        sources.append(payload)
        return self

    def ai_template(self, name: str) -> "MobV2":
        token = str(name).strip()
        if not token:
            raise ValueError(f"mobs.{self.id}.ai_template: name cannot be empty")
        ai = self._mob.get("ai")
        if not isinstance(ai, Mapping):
            raise ValueError(f"mobs.{self.id}.ai_template: mob has no ai mapping to export as template")
        self._ai_templates[token] = _deep_copy_value(ai)
        return self

    def ai_inherit_template(self, name: str) -> "MobV2":
        token = str(name).strip()
        if not token:
            raise ValueError(f"mobs.{self.id}.aiTemplate: name cannot be empty")
        self._mob["aiTemplate"] = token
        return self

    def ai_selector(
        self,
        intent: AiSelectorSpec | MobAiIntentTypeLike,
        *,
        selector_id: Optional[str] = None,
        priority: int = 100,
        when: AiConditionSpec | None = None,
        speed: Optional[float] = None,
        radius: Optional[float] = None,
        min_range: Optional[float] = None,
        max_range: Optional[float] = None,
        interval_ticks: Optional[int] = None,
        ability: Ref | str | None = None,
        cast_cooldown_ticks: Optional[int] = None,
        require_target: Optional[bool] = None,
    ) -> "MobV2":
        ai = self._ensure_ai_v4()
        selectors = ai.setdefault("selectors", [])
        if not isinstance(selectors, list):
            raise ValueError(f"mobs.{self.id}.ai.selectors: expected list")
        if when is not None and not isinstance(when, AiConditionSpec):
            raise ValueError(
                f"mobs.{self.id}.ai.selectors.when: expected AiConditionSpec "
                "(use ai_condition helpers)"
            )
        selector_index = len(selectors)
        if isinstance(intent, AiSelectorSpec):
            payload = intent.build(
                self.ctx,
                index=selector_index,
                field=f"mobs.{self.id}.ai.selectors[{selector_index}]",
            )
            selectors.append(payload)
            return self

        selector = AiSelectorSpec(
            intent=AiIntentSpec(
                intent,
                speed=speed,
                radius=radius,
                min_range=min_range,
                max_range=max_range,
                interval_ticks=interval_ticks,
                ability=ability,
                cast_cooldown_ticks=cast_cooldown_ticks,
                require_target=require_target,
            ),
            selector_id=selector_id,
            priority=priority,
            when=when,
        )
        selectors.append(
            selector.build(
                self.ctx,
                index=selector_index,
                field=f"mobs.{self.id}.ai.selectors[{selector_index}]",
            )
        )
        return self

    def ai_selector_cast(
        self,
        ability: Ref | str,
        *,
        selector_id: Optional[str] = None,
        priority: int = 100,
        when: AiConditionSpec | None = None,
        intent: MobAiIntentTypeLike = MobAiIntentType.CHASE_AND_CAST,
        speed: Optional[float] = None,
        radius: Optional[float] = None,
        min_range: Optional[float] = None,
        max_range: Optional[float] = None,
        interval_ticks: Optional[int] = None,
        cast_cooldown_ticks: int = 20,
        require_target: bool = True,
    ) -> "MobV2":
        return self.ai_selector(
            intent=intent,
            selector_id=selector_id,
            priority=priority,
            when=when,
            speed=speed,
            radius=radius,
            min_range=min_range,
            max_range=max_range,
            interval_ticks=interval_ticks,
            ability=ability,
            cast_cooldown_ticks=cast_cooldown_ticks,
            require_target=require_target,
        )

    def ai_v4_raw(self, mapping: Mapping[str, Any]) -> "MobV2":
        if not isinstance(mapping, Mapping):
            raise ValueError(f"mobs.{self.id}.ai: ai_v4_raw mapping must be a dict-like object")
        ai = self._ensure_ai_v4()
        _deep_merge(ai, mapping)
        ai["version"] = "V4"
        return self

    def ai_copy_from(self, other: "MobV2") -> "MobV2":
        if not isinstance(other, MobV2):
            raise ValueError(f"mobs.{self.id}.ai_copy_from: expected MobV2")
        source_ai = other._mob.get("ai")
        if not isinstance(source_ai, Mapping):
            raise ValueError(f"mobs.{self.id}.ai_copy_from: source mob has no ai mapping")
        self._mob["ai"] = _deep_copy_value(source_ai)
        return self

    def ai_passive_flee(
        self,
        *,
        aggro_radius: float = 10.0,
        flee_speed: float = 0.35,
        wander_speed: float = 0.22,
        engine: str = "V3",
    ) -> "MobV2":
        self.ai_v4(
            engine=engine,
            mode=MobAiMode.FULL_OVERRIDE,
            authority=MobAiAuthority.ABILITY_DRIVEN,
            profile=MobAiProfile.PASSIVE,
            aggro_radius=aggro_radius,
            flee_speed=flee_speed,
        )
        self.ai_runtime_model(MobAiRuntimeModel.NATURAL_V1)
        self.ai_movement_policy(MobAiMovementPolicy.PATHFINDER_FIRST)
        self.ai_target_source(
            MobAiTargetSourceType.LAST_ATTACKER,
            memory_ticks=80,
            priority=10,
        )
        self.ai_target_source(
            MobAiTargetSourceType.PROXIMITY_PLAYER,
            radius=aggro_radius,
            memory_ticks=30,
            priority=20,
        )
        self.ai_target_source(
            MobAiTargetSourceType.CURRENT_TARGET,
            memory_ticks=20,
            priority=50,
        )
        self.ai_selector(
            MobAiIntentType.FLEE,
            selector_id="passive_flee",
            priority=10,
            when=ai_condition.has_target(True),
            speed=flee_speed,
            require_target=True,
        )
        self.ai_selector(
            MobAiIntentType.WANDER,
            selector_id="passive_wander",
            priority=100,
            when=ai_condition.bool(True),
            speed=wander_speed,
            require_target=False,
        )
        return self

    def ai_passive_wander(
        self,
        *,
        wander_speed: float = 0.22,
        engine: str = "V3",
    ) -> "MobV2":
        self.ai_v4(
            engine=engine,
            mode=MobAiMode.FULL_OVERRIDE,
            authority=MobAiAuthority.ABILITY_DRIVEN,
            profile=MobAiProfile.PASSIVE,
        )
        self.ai_runtime_model(MobAiRuntimeModel.NATURAL_V1)
        self.ai_movement_policy(MobAiMovementPolicy.PATHFINDER_FIRST)
        self.ai_selector(
            MobAiIntentType.WANDER,
            selector_id="passive_wander",
            priority=100,
            when=ai_condition.bool(True),
            speed=wander_speed,
            require_target=False,
        )
        return self

    def silent(self, value: bool = True) -> "MobV2":
        self._mob["silent"] = bool(value)
        return self

    def collidable(self, value: bool) -> "MobV2":
        self._mob["collidable"] = bool(value)
        return self

    def invulnerable(self, value: bool) -> "MobV2":
        self._mob["invulnerable"] = bool(value)
        return self

    def sounds(self, profile: MobSoundProfile) -> "MobV2":
        profile_value = coerce_enum(profile, MobSoundProfile, field=f"mobs.{self.id}.sounds.profile")
        key = MobSoundProfile(profile_value)
        config = _SOUND_PROFILES.get(key)
        if not config:
            raise ValueError(f"unknown sound profile: {profile}")
        return self.sound_overrides(spawn=config.get("spawn"), death=config.get("death"))

    def sound_overrides(
        self,
        *,
        spawn: Optional[SoundLike] = None,
        death: Optional[SoundLike] = None,
        volume: float = 1.0,
        pitch: float = 1.0,
    ) -> "MobV2":
        if spawn is not None:
            self._mob.setdefault("spawnFx", {}).setdefault("sound", {}).update(
                {
                    "sound": coerce_sound(spawn, field=f"mobs.{self.id}.spawnFx.sound"),
                    "volume": float(volume),
                    "pitch": float(pitch),
                }
            )
        if death is not None:
            self._mob.setdefault("deathFx", {}).setdefault("sound", {}).update(
                {
                    "sound": coerce_sound(death, field=f"mobs.{self.id}.deathFx.sound"),
                    "volume": float(volume),
                    "pitch": float(pitch),
                }
            )
        return self

    def look_skin_head(self, texture: str) -> "MobV2":
        self._mob.setdefault("equipment", {})["head"] = {
            "material": "PLAYER_HEAD",
            "meta": {"skull": {"texture": texture}},
        }
        return self

    def equip_main_hand(self, material: MaterialLike | str) -> "MobV2":
        self._mob.setdefault("equipment", {})["mainHand"] = {
            "material": coerce_material(material, field=f"mobs.{self.id}.equipment.mainHand.material")
        }
        return self

    def equip_off_hand(self, material: MaterialLike | str) -> "MobV2":
        self._mob.setdefault("equipment", {})["offHand"] = {
            "material": coerce_material(material, field=f"mobs.{self.id}.equipment.offHand.material")
        }
        return self

    def equip_head(self, material: MaterialLike | str) -> "MobV2":
        self._mob.setdefault("equipment", {})["head"] = {
            "material": coerce_material(material, field=f"mobs.{self.id}.equipment.head.material")
        }
        return self

    def equip_chest(self, material: MaterialLike | str) -> "MobV2":
        self._mob.setdefault("equipment", {})["chest"] = {
            "material": coerce_material(material, field=f"mobs.{self.id}.equipment.chest.material")
        }
        return self

    def equip_legs(self, material: MaterialLike | str) -> "MobV2":
        self._mob.setdefault("equipment", {})["legs"] = {
            "material": coerce_material(material, field=f"mobs.{self.id}.equipment.legs.material")
        }
        return self

    def equip_feet(self, material: MaterialLike | str) -> "MobV2":
        self._mob.setdefault("equipment", {})["feet"] = {
            "material": coerce_material(material, field=f"mobs.{self.id}.equipment.feet.material")
        }
        return self

    def equip_hands(
        self,
        *,
        main_hand: MaterialLike | str | None = None,
        off_hand: MaterialLike | str | None = None,
    ) -> "MobV2":
        if main_hand is not None:
            self.equip_main_hand(main_hand)
        if off_hand is not None:
            self.equip_off_hand(off_hand)
        return self

    def equip_armor(
        self,
        *,
        head: MaterialLike | str | None = None,
        chest: MaterialLike | str | None = None,
        legs: MaterialLike | str | None = None,
        feet: MaterialLike | str | None = None,
    ) -> "MobV2":
        if head is not None:
            self.equip_head(head)
        if chest is not None:
            self.equip_chest(chest)
        if legs is not None:
            self.equip_legs(legs)
        if feet is not None:
            self.equip_feet(feet)
        return self

    def events(
        self,
        *,
        on_hit: Optional[AbilityLike] = None,
        on_hurt: Optional[AbilityLike] = None,
        on_target: Optional[AbilityLike] = None,
        on_kill: Optional[AbilityLike] = None,
        on_despawn: Optional[AbilityLike] = None,
        on_idle: Optional[TimedAbility | tuple[AbilityLike, int]] = None,
        on_spawn_tick: Optional[TimedAbility | tuple[AbilityLike, int]] = None,
    ) -> "MobV2":
        events = self._mob.setdefault("events", {})
        if on_hit is not None:
            self._track_bound_ability(on_hit)
            events["onHit"] = self._ability_id(on_hit, field="on_hit")
        if on_hurt is not None:
            self._track_bound_ability(on_hurt)
            events["onHurt"] = self._ability_id(on_hurt, field="on_hurt")
        if on_target is not None:
            self._track_bound_ability(on_target)
            events["onTarget"] = self._ability_id(on_target, field="on_target")
        if on_kill is not None:
            self._track_bound_ability(on_kill)
            events["onKill"] = self._ability_id(on_kill, field="on_kill")
        if on_despawn is not None:
            self._track_bound_ability(on_despawn)
            events["onDespawn"] = self._ability_id(on_despawn, field="on_despawn")

        if on_idle is not None:
            ability, interval = self._timed_ability(on_idle)
            self._track_bound_ability(ability)
            events["onIdle"] = {
                "ability": self._ability_id(ability, field="on_idle.ability"),
                "intervalTicks": interval,
            }

        if on_spawn_tick is not None:
            ability, interval = self._timed_ability(on_spawn_tick)
            self._track_bound_ability(ability)
            events["onSpawnTick"] = {
                "ability": self._ability_id(ability, field="on_spawn_tick.ability"),
                "intervalTicks": interval,
            }

        return self

    def loot_pool(
        self,
        pool_id: str,
        *,
        clear_vanilla: bool = False,
        rolls: int = 1,
        bonus_rolls: int = 0,
        luck_multiplier: float = 0.0,
        deterministic: Optional[bool] = None,
        seed: int | str | None = None,
        announce_tiers: list[str] | None = None,
        announce_template: str | None = None,
    ) -> "MobV2":
        pool_key = self._normalize_doc_id(pool_id, field="loot_pool.pool_id")
        payload: dict[str, Any] = {
            "clearVanilla": bool(clear_vanilla),
            "rolls": max(0, int(rolls)),
            "bonusRolls": max(0, int(bonus_rolls)),
            "luckMultiplier": float(luck_multiplier),
            "drops": [],
            "guaranteed": [],
        }
        if deterministic is not None:
            payload["deterministic"] = bool(deterministic)
        if seed is not None:
            payload["seed"] = str(seed)
        if announce_tiers:
            payload["announceTiers"] = [str(entry) for entry in announce_tiers if str(entry).strip()]
        if announce_template is not None and announce_template.strip():
            payload["announceTemplate"] = announce_template.strip()
        self._loot_pools[pool_key] = payload
        self._active_loot_pool_id = pool_key
        return self

    def loot_drop_spec(self, spec: LootDropSpec, *, pool_id: str | None = None) -> "MobV2":
        if spec.guaranteed:
            if spec.item is None:
                raise ValueError(f"mobs.{self.id}.loot_drop_spec: guaranteed drops require item")
            return self.loot_guaranteed_item(spec.item, pool_id=pool_id, amount=spec.amount, tier=spec.tier)
        if spec.item is not None:
            return self.loot_drop_item(
                spec.item,
                pool_id=pool_id,
                chance=spec.chance,
                min_amount=spec.min_amount,
                max_amount=spec.max_amount,
                tier=spec.tier,
            )
        if spec.material is not None:
            return self.loot_drop_material(
                spec.material,
                pool_id=pool_id,
                chance=spec.chance,
                min_amount=spec.min_amount,
                max_amount=spec.max_amount,
                tier=spec.tier,
            )
        raise ValueError(f"mobs.{self.id}.loot_drop_spec: expected item or material")

    def loot_drop_item(
        self,
        item: Ref | str,
        *,
        pool_id: str | None = None,
        chance: float = 100.0,
        min_amount: int = 1,
        max_amount: Optional[int] = None,
        tier: str | None = None,
    ) -> "MobV2":
        payload: dict[str, Any] = {
            "itemId": self.ctx.resolve(item, domain="item", field=f"mobs.{self.id}.loot_drop_item.item"),
            "chance": float(chance),
            "min": max(1, int(min_amount)),
            "max": max(1, int(max_amount if max_amount is not None else min_amount)),
        }
        if tier is not None and tier.strip():
            payload["tier"] = tier.strip()
        self._append_loot_drop(payload, pool_id=pool_id, guaranteed=False)
        return self

    def loot_drop_material(
        self,
        material: MaterialLike | str,
        *,
        pool_id: str | None = None,
        chance: float = 100.0,
        min_amount: int = 1,
        max_amount: Optional[int] = None,
        tier: str | None = None,
    ) -> "MobV2":
        payload: dict[str, Any] = {
            "material": coerce_material(material, field=f"mobs.{self.id}.loot_drop_material.material"),
            "chance": float(chance),
            "min": max(1, int(min_amount)),
            "max": max(1, int(max_amount if max_amount is not None else min_amount)),
        }
        if tier is not None and tier.strip():
            payload["tier"] = tier.strip()
        self._append_loot_drop(payload, pool_id=pool_id, guaranteed=False)
        return self

    def loot_guaranteed_item(
        self,
        item: Ref | str,
        *,
        pool_id: str | None = None,
        amount: int = 1,
        tier: str | None = None,
    ) -> "MobV2":
        payload: dict[str, Any] = {
            "itemId": self.ctx.resolve(item, domain="item", field=f"mobs.{self.id}.loot_guaranteed_item.item"),
            "amount": max(1, int(amount)),
        }
        if tier is not None and tier.strip():
            payload["tier"] = tier.strip()
        self._append_loot_drop(payload, pool_id=pool_id, guaranteed=True)
        return self

    def mana_drop(
        self,
        *,
        resource: str = "mana",
        killer_min: float = 0.0,
        killer_max: Optional[float] = None,
        nearby_min: float = 0.0,
        nearby_max: Optional[float] = None,
        nearby_radius: float = 0.0,
        cap: float = 0.0,
    ) -> "MobV2":
        resource_id = snake_case(resource)
        if not resource_id:
            raise ValueError(f"mobs.{self.id}.mana_drop.resource: cannot be empty")
        self._mob["manaDrops"] = {
            "resource": resource_id,
            "cap": max(0.0, float(cap)),
            "killer": {
                "min": float(killer_min),
                "max": float(killer_max if killer_max is not None else killer_min),
            },
            "nearby": {
                "radius": max(0.0, float(nearby_radius)),
                "min": float(nearby_min),
                "max": float(nearby_max if nearby_max is not None else nearby_min),
            },
        }
        return self

    def phase(
        self,
        *,
        phase_id: str,
        health_below: float,
        scale_multiplier: Optional[float] = None,
        collidable: Optional[bool] = None,
        ai: Mapping[str, Any] | None = None,
        ai_merge: Optional[str] = None,
    ) -> "MobV2":
        phase_key = self._normalize_doc_id(phase_id, field="phase.phase_id")
        payload: dict[str, Any] = {
            "id": phase_key,
            "healthBelow": float(health_below),
        }
        if scale_multiplier is not None:
            payload["scaleMultiplier"] = float(scale_multiplier)
        if collidable is not None:
            payload["collidable"] = bool(collidable)
        if ai is not None:
            payload["ai"] = _deep_copy_value(ai)
        if ai_merge is not None and ai_merge.strip():
            payload.setdefault("ai", {})["merge"] = ai_merge.strip().upper()
        self._mob.setdefault("phases", []).append(payload)
        return self

    def egg(
        self,
        egg_id: str | None = None,
        *,
        material: MaterialLike = Material.PIG_SPAWN_EGG,
        amount: int = 1,
        permission: str | None = None,
        cooldown_ticks: int | None = None,
    ) -> "MobV2":
        egg_key = self._normalize_doc_id(egg_id or f"{self.id}_egg", field="egg.egg_id")
        payload: dict[str, Any] = {
            "mob": self.id,
            "material": coerce_material(material, field=f"mobs.{self.id}.egg.material"),
            "amount": max(1, int(amount)),
        }
        if permission is not None and permission.strip():
            payload["permission"] = permission.strip()
        if cooldown_ticks is not None:
            payload["cooldownTicks"] = max(0, int(cooldown_ticks))
        self._eggs[egg_key] = payload
        return self

    def spawner_block(
        self,
        spawner_id: str | None = None,
        *,
        material: MaterialLike = Material.SPAWNER,
        count: Optional[int] = None,
        max_alive: Optional[int] = None,
        respawn_ticks: Optional[int] = None,
        radius: Optional[float] = None,
        enabled: Optional[bool] = None,
        activation_radius: Optional[float] = None,
    ) -> "MobV2":
        block_key = self._normalize_doc_id(spawner_id or f"{self.id}_spawner", field="spawner_block.spawner_id")
        payload: dict[str, Any] = {
            "mob": self.id,
            "material": coerce_material(material, field=f"mobs.{self.id}.spawner_block.material"),
        }
        spawn: dict[str, Any] = {}
        if count is not None:
            spawn["count"] = max(1, int(count))
        if max_alive is not None:
            spawn["maxAlive"] = max(1, int(max_alive))
        if respawn_ticks is not None:
            spawn["respawnTicks"] = max(0, int(respawn_ticks))
        if radius is not None:
            spawn["radius"] = max(0.0, float(radius))
        if enabled is not None:
            spawn["enabled"] = bool(enabled)
        if activation_radius is not None:
            spawn["activationRadius"] = max(0.0, float(activation_radius))
        if spawn:
            payload["spawn"] = spawn
        self._spawner_blocks[block_key] = payload
        return self

    def trial_spawner(
        self,
        trial_id: str,
        *,
        key_loot_pool: str,
        waves: int = 3,
        simultaneous: int = 1,
        cooldown_ticks: int = 80,
        required_players: int = 1,
        activation_range: float = 20.0,
        mob_pool: list[tuple[Ref | str, float]] | None = None,
        ominous_profile: Mapping[str, Any] | None = None,
    ) -> "MobV2":
        trial_key = self._normalize_doc_id(trial_id, field="trial_spawner.trial_id")
        entries = self._build_trial_mob_pool(mob_pool)
        payload: dict[str, Any] = {
            "mobPool": entries,
            "waves": max(1, int(waves)),
            "simultaneous": max(1, int(simultaneous)),
            "cooldownTicks": max(0, int(cooldown_ticks)),
            "requiredPlayers": max(1, int(required_players)),
            "activationRange": max(0.1, float(activation_range)),
            "keyLootPool": self._normalize_doc_id(key_loot_pool, field="trial_spawner.key_loot_pool"),
        }
        if ominous_profile is not None:
            payload["ominousProfile"] = _deep_copy_value(ominous_profile)
        self._trial_spawners[trial_key] = payload
        return self

    def vault_profile(
        self,
        vault_id: str,
        *,
        key_item: Ref | str,
        loot_pool_normal: str,
        loot_pool_ominous: str,
        activation_range: float = 8.0,
        deactivation_range: float = 16.0,
        displayed_item_pool: list[tuple[Ref | str, float]] | None = None,
    ) -> "MobV2":
        vault_key = self._normalize_doc_id(vault_id, field="vault_profile.vault_id")
        payload: dict[str, Any] = {
            "keyItem": self.ctx.resolve(key_item, domain="item", field=f"mobs.{self.id}.vault_profile.key_item"),
            "lootPoolNormal": self._normalize_doc_id(loot_pool_normal, field="vault_profile.loot_pool_normal"),
            "lootPoolOminous": self._normalize_doc_id(loot_pool_ominous, field="vault_profile.loot_pool_ominous"),
            "activationRange": max(0.1, float(activation_range)),
            "deactivationRange": max(float(deactivation_range), float(activation_range) + 0.1),
        }
        if displayed_item_pool:
            payload["displayedItemPool"] = [
                {
                    "itemId": self.ctx.resolve(item_ref, domain="item", field=f"mobs.{self.id}.vault_profile.displayed_item_pool"),
                    "weight": float(weight),
                }
                for item_ref, weight in displayed_item_pool
            ]
        self._vault_profiles[vault_key] = payload
        return self

    def spawn_point(
        self,
        spawn_id: str,
        *,
        world: str,
        x: float,
        y: float,
        z: float,
        yaw: float = 0.0,
        pitch: float = 0.0,
        count: int = 1,
        max_alive: int = 1,
        respawn_ticks: int = 200,
        radius: float = 0.0,
        enabled: bool = True,
    ) -> "MobV2":
        return self.spawn_point_spec(
            SpawnSpec(
                spawn_id=spawn_id,
                world=world,
                x=x,
                y=y,
                z=z,
                yaw=yaw,
                pitch=pitch,
                count=count,
                max_alive=max_alive,
                respawn_ticks=respawn_ticks,
                radius=radius,
                enabled=enabled,
            )
        )

    def spawn_point_spec(self, spec: SpawnSpec) -> "MobV2":
        spawn_key = self._normalize_doc_id(spec.spawn_id, field="spawn_point.spawn_id")
        world_name = str(spec.world).strip()
        if not world_name:
            raise ValueError(f"mobs.{self.id}.spawn_point.world: cannot be empty")
        self._spawns[spawn_key] = {
            "mob": self.id,
            "world": world_name,
            "x": float(spec.x),
            "y": float(spec.y),
            "z": float(spec.z),
            "yaw": float(spec.yaw),
            "pitch": float(spec.pitch),
            "count": max(1, int(spec.count)),
            "maxAlive": max(1, int(spec.max_alive)),
            "respawnTicks": max(0, int(spec.respawn_ticks)),
            "radius": max(0.0, float(spec.radius)),
            "enabled": bool(spec.enabled),
        }
        return self

    def override(self, path: str, value: Any) -> "MobV2":
        raise BuildValidationError(
            f"mobs.{self.id}.override: path-based override is disabled in strict typed mode. "
            "Use typed APIs like .equip_head(...), .scale_range(...), .collidable(...), .invulnerable(...). "
            "For unsupported edge cases use .unsafe_raw_patch(...)."
        )

    def unsafe_raw_patch(self, mapping: Mapping[str, Any]) -> "MobV2":
        if not isinstance(mapping, Mapping):
            raise ValueError(f"mobs.{self.id}.unsafe_raw_patch: expected mapping")
        _deep_merge(self._mob, mapping)
        return self

    def clone(
        self,
        *,
        symbol: str,
        name: str,
        id: str | None = None,
    ) -> "MobV2":
        symbol_token = str(symbol).strip()
        if not symbol_token:
            raise ValueError("mobs.clone.symbol: cannot be empty")
        name_token = str(name).strip()
        if not name_token:
            raise ValueError("mobs.clone.name: cannot be empty")

        mob_type = self._mob.get("type")
        if not isinstance(mob_type, str) or not mob_type.strip():
            raise ValueError("mobs.clone.type: source mob has invalid type")
        mob_type_token = mob_type.strip().upper()
        try:
            mob_type_enum = EntityType[mob_type_token]
        except KeyError as exc:
            raise ValueError(
                f"mobs.clone.type: source mob type {mob_type!r} is not a known EntityType token"
            ) from exc

        cloned = MobV2(
            ctx=self.ctx,
            name=name_token,
            mob_type=mob_type_enum,
            id=id,
            symbol=symbol_token,
        )
        cloned._mob = _deep_copy_value(self._mob)
        cloned._mob["name"] = name_token
        cloned._ai_templates = _deep_copy_value(self._ai_templates)
        cloned._loot_pools = _deep_copy_value(self._loot_pools)
        cloned._active_loot_pool_id = self._active_loot_pool_id
        cloned._eggs = _deep_copy_value(self._eggs)
        cloned._spawner_blocks = _deep_copy_value(self._spawner_blocks)
        cloned._trial_spawners = _deep_copy_value(self._trial_spawners)
        cloned._vault_profiles = _deep_copy_value(self._vault_profiles)
        cloned._spawns = _deep_copy_value(self._spawns)
        cloned._bound_abilities = list(self._bound_abilities)
        return cloned

    def build(self) -> dict[str, Any]:
        return _deep_copy_value(self._mob)

    def build_document(self) -> dict[str, Any]:
        payload: dict[str, Any] = {"mobs": {self.id: self.build()}}
        if self._ai_templates:
            payload["aiTemplates"] = _deep_copy_value(self._ai_templates)
        if self._loot_pools:
            payload["lootPools"] = _deep_copy_value(self._loot_pools)
        if self._eggs:
            payload["eggs"] = _deep_copy_value(self._eggs)
        if self._spawner_blocks:
            payload["spawnerBlocks"] = _deep_copy_value(self._spawner_blocks)
        if self._trial_spawners:
            payload["trialSpawners"] = _deep_copy_value(self._trial_spawners)
        if self._vault_profiles:
            payload["vaults"] = _deep_copy_value(self._vault_profiles)
        if self._spawns:
            payload["spawns"] = _deep_copy_value(self._spawns)
        return payload

    def _ability_id(self, value: AbilityLike, *, field: str) -> str:
        return self.ctx.resolve(value, domain="ability", field=f"mobs.{self.id}.events.{field}")

    def _timed_ability(self, value: TimedAbility | tuple[AbilityLike, int]) -> tuple[AbilityLike, int]:
        if isinstance(value, TimedAbility):
            return value.ability, int(value.interval_ticks)
        if isinstance(value, tuple) and len(value) == 2:
            return value[0], int(value[1])
        raise ValueError("timed ability must be TimedAbility or tuple(ability, interval_ticks)")

    def _track_bound_ability(self, ability: AbilityLike) -> None:
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

    def _ensure_ai_v4(self) -> dict[str, Any]:
        ai = self._mob.setdefault("ai", {})
        if not isinstance(ai, dict):
            raise ValueError(f"mobs.{self.id}.ai: expected mapping")
        ai["version"] = "V4"
        ai.setdefault("engine", "V3")
        control = ai.setdefault("control", {})
        if not isinstance(control, dict):
            raise ValueError(f"mobs.{self.id}.ai.control: expected mapping")
        control.setdefault("mode", MobAiMode.FULL_OVERRIDE.value)
        combat = ai.setdefault("combat", {})
        if not isinstance(combat, dict):
            raise ValueError(f"mobs.{self.id}.ai.combat: expected mapping")
        combat.setdefault("authority", MobAiAuthority.ABILITY_DRIVEN.value)
        selectors = ai.get("selectors")
        if not isinstance(selectors, list):
            ai["selectors"] = []
        return ai

    def _coerce_ai_engine(self, value: str) -> str:
        raw = str(value).strip().upper()
        if raw not in _ALLOWED_AI_ENGINES:
            raise ValueError(
                f"mobs.{self.id}.ai.engine: invalid value={value!r}. Expected one of {sorted(_ALLOWED_AI_ENGINES)!r}"
            )
        return raw

    def _normalize_doc_id(self, raw: str, *, field: str) -> str:
        token = snake_case(raw)
        if not token:
            raise ValueError(f"mobs.{self.id}.{field}: cannot be empty")
        return token

    def _active_loot_pool(self, pool_id: str | None) -> str:
        if pool_id is not None:
            normalized = self._normalize_doc_id(pool_id, field="loot.pool_id")
            if normalized not in self._loot_pools:
                raise ValueError(f"mobs.{self.id}.loot.pool_id: unknown loot pool {normalized!r}")
            return normalized
        if self._active_loot_pool_id is not None and self._active_loot_pool_id in self._loot_pools:
            return self._active_loot_pool_id
        default = f"{self.id}_loot"
        self.loot_pool(default)
        return default

    def _append_loot_drop(self, drop_payload: dict[str, Any], *, pool_id: str | None, guaranteed: bool) -> None:
        active_pool = self._active_loot_pool(pool_id)
        target = self._loot_pools[active_pool].setdefault("guaranteed" if guaranteed else "drops", [])
        target.append(drop_payload)

    def _build_trial_mob_pool(self, entries: list[tuple[Ref | str, float]] | None) -> list[dict[str, Any]]:
        if not entries:
            return [{"mobId": self.id, "weight": 1.0}]
        out: list[dict[str, Any]] = []
        for index, (mob_ref, weight) in enumerate(entries):
            out.append(
                {
                    "mobId": self.ctx.resolve(
                        mob_ref,
                        domain="mob",
                        field=f"mobs.{self.id}.trial_spawner.mob_pool[{index}]",
                        allow_external=True,
                    ),
                    "weight": float(weight),
                }
            )
        return out


def _deep_copy_value(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {str(k): _deep_copy_value(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_deep_copy_value(v) for v in value]
    return value


def _deep_merge(target: dict[str, Any], patch: Mapping[str, Any]) -> None:
    for key, value in patch.items():
        key_str = str(key)
        if isinstance(value, Mapping) and isinstance(target.get(key_str), dict):
            _deep_merge(target[key_str], value)
            continue
        target[key_str] = _deep_copy_value(value)


def _set_deep_path(root: dict[str, Any], path: str, value: Any) -> None:
    tokens = _parse_path(path)
    current: Any = root
    for index, token in enumerate(tokens):
        last = index == len(tokens) - 1
        next_token = None if last else tokens[index + 1]
        if isinstance(token, str):
            if not isinstance(current, dict):
                raise ValueError(f"override path {path!r}: cannot set key {token!r} on non-mapping segment")
            if last:
                current[token] = value
                return
            expected_list = isinstance(next_token, int)
            existing = current.get(token)
            if expected_list:
                if not isinstance(existing, list):
                    current[token] = []
            else:
                if not isinstance(existing, dict):
                    current[token] = {}
            current = current[token]
            continue

        if not isinstance(current, list):
            raise ValueError(f"override path {path!r}: cannot set list index on non-list segment")
        while len(current) <= token:
            current.append(None)
        if last:
            current[token] = value
            return
        expected_list = isinstance(next_token, int)
        existing = current[token]
        if expected_list:
            if not isinstance(existing, list):
                current[token] = []
        else:
            if not isinstance(existing, dict):
                current[token] = {}
        current = current[token]


def _parse_path(path: str) -> list[str | int]:
    tokens: list[str | int] = []
    part = ""
    i = 0
    while i < len(path):
        ch = path[i]
        if ch == ".":
            if part:
                tokens.append(part)
                part = ""
            elif not tokens or (i > 0 and path[i - 1] == "."):
                raise ValueError(f"invalid override path {path!r}")
            i += 1
            continue
        if ch == "[":
            if part:
                tokens.append(part)
                part = ""
            end = path.find("]", i + 1)
            if end <= i + 1:
                raise ValueError(f"invalid override path {path!r}")
            index_raw = path[i + 1 : end].strip()
            if not index_raw.isdigit():
                raise ValueError(f"invalid override path {path!r}: list index must be numeric")
            tokens.append(int(index_raw))
            i = end + 1
            continue
        part += ch
        i += 1
    if part:
        tokens.append(part)
    if not tokens:
        raise ValueError(f"invalid override path {path!r}")
    return tokens


class mob:
    @staticmethod
    def create(
        ctx: BuildContext,
        *,
        name: str,
        mob_type: EntityTypeLike,
        id: Optional[str] = None,
        symbol: Optional[str] = None,
    ) -> MobV2:
        return MobV2(ctx=ctx, name=name, mob_type=mob_type, id=id, symbol=symbol)


__all__ = [
    "BossbarSpec",
    "StyleSpec",
    "AiConditionSpec",
    "AiBool",
    "AiPredicate",
    "AiAll",
    "AiAny",
    "AiNot",
    "ai_condition",
    "AiIntentSpec",
    "AiSelectorSpec",
    "AiTargetSourceSpec",
    "LootDropSpec",
    "SpawnSpec",
    "MobV2",
    "TimedAbility",
    "mob",
]
