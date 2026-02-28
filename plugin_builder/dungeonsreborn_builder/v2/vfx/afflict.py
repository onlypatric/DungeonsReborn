"""High-level affliction DSL built on top of afflict_* low-level actions."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from dungeonsreborn_builder.v2.effects import (
    ActionSpec,
    AfflictApplyAction,
    AfflictClearAction,
    AfflictConsumeAction,
    AfflictPresentRequirement,
    AfflictRemainingLteRequirement,
    AfflictStacksGteRequirement,
    AfflictStacksLteRequirement,
    RequirementSpec,
    fx,
)
from dungeonsreborn_builder.v2.enums import (
    AfflictionAudience,
    AfflictionAudienceLike,
    AfflictionRefreshPolicy,
    AfflictionRefreshPolicyLike,
    StrEnum,
)


class AfflictionId(StrEnum):
    BLEED = "bleed"
    BURN = "burn"
    FROSTBITE = "frostbite"
    SHOCK = "shock"
    POISON = "poison"
    WITHER_MARK = "wither_mark"
    VULNERABILITY = "vulnerability"
    WEAKEN = "weaken"
    SILENCE = "silence"
    CRIPPLE = "cripple"
    SOUL_MARK = "soul_mark"
    RADIANT_MARK = "radiant_mark"


@dataclass(frozen=True)
class CustomAfflictionId:
    value: str


AfflictionIdLike = AfflictionId | CustomAfflictionId


def custom_affliction_id(value: str) -> CustomAfflictionId:
    token = str(value).strip().lower()
    if not token:
        raise ValueError("vfx.afflict.id: token cannot be empty")
    return CustomAfflictionId(value=token)


def _coerce_affliction_id(value: AfflictionIdLike, *, field: str) -> str:
    if isinstance(value, AfflictionId):
        return value.value
    if isinstance(value, CustomAfflictionId):
        token = str(value.value).strip().lower()
        if not token:
            raise ValueError(f"{field}: token cannot be empty")
        return token
    raise ValueError(
        f"{field}: plain string tokens are forbidden in typed APIs; "
        "use AfflictionId or custom_affliction_id(...)"
    )


@dataclass(frozen=True)
class AfflictionTickSpec:
    tick_every_ticks: int = 20
    on_tick: ActionSpec | None = None


@dataclass(frozen=True)
class AfflictionSpec:
    affliction_id: AfflictionIdLike
    stacks: int = 1
    max_stacks: int = 5
    duration_ticks: int = 100
    refresh_policy: AfflictionRefreshPolicyLike = AfflictionRefreshPolicy.RESET_DURATION
    audience: AfflictionAudienceLike = AfflictionAudience.PVE_ONLY
    tick: AfflictionTickSpec | None = None
    on_apply: ActionSpec | None = None
    on_stack: ActionSpec | None = None
    on_expire: ActionSpec | None = None

    def to_action(self) -> AfflictApplyAction:
        tick_every = 0
        on_tick = None
        if self.tick is not None:
            tick_every = int(self.tick.tick_every_ticks)
            on_tick = self.tick.on_tick
        return fx.afflict_apply(
            affliction_id=_coerce_affliction_id(self.affliction_id, field="vfx.afflict.apply.id"),
            stacks=int(self.stacks),
            max_stacks=int(self.max_stacks),
            duration_ticks=int(self.duration_ticks),
            refresh_policy=self.refresh_policy,
            audience=self.audience,
            tick_every_ticks=tick_every,
            on_tick=on_tick,
            on_apply=self.on_apply,
            on_stack=self.on_stack,
            on_expire=self.on_expire,
        )


_PRESETS: dict[AfflictionId, AfflictionSpec] = {
    AfflictionId.BLEED: AfflictionSpec(
        affliction_id=AfflictionId.BLEED,
        stacks=1,
        max_stacks=5,
        duration_ticks=80,
        refresh_policy=AfflictionRefreshPolicy.RESET_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.BURN: AfflictionSpec(
        affliction_id=AfflictionId.BURN,
        stacks=1,
        max_stacks=6,
        duration_ticks=90,
        refresh_policy=AfflictionRefreshPolicy.EXTEND_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.FROSTBITE: AfflictionSpec(
        affliction_id=AfflictionId.FROSTBITE,
        stacks=1,
        max_stacks=4,
        duration_ticks=100,
        refresh_policy=AfflictionRefreshPolicy.RESET_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.SHOCK: AfflictionSpec(
        affliction_id=AfflictionId.SHOCK,
        stacks=1,
        max_stacks=5,
        duration_ticks=70,
        refresh_policy=AfflictionRefreshPolicy.EXTEND_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.POISON: AfflictionSpec(
        affliction_id=AfflictionId.POISON,
        stacks=1,
        max_stacks=7,
        duration_ticks=110,
        refresh_policy=AfflictionRefreshPolicy.RESET_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.WITHER_MARK: AfflictionSpec(
        affliction_id=AfflictionId.WITHER_MARK,
        stacks=1,
        max_stacks=5,
        duration_ticks=100,
        refresh_policy=AfflictionRefreshPolicy.MAX_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.VULNERABILITY: AfflictionSpec(
        affliction_id=AfflictionId.VULNERABILITY,
        stacks=1,
        max_stacks=3,
        duration_ticks=80,
        refresh_policy=AfflictionRefreshPolicy.RESET_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.WEAKEN: AfflictionSpec(
        affliction_id=AfflictionId.WEAKEN,
        stacks=1,
        max_stacks=3,
        duration_ticks=90,
        refresh_policy=AfflictionRefreshPolicy.RESET_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.SILENCE: AfflictionSpec(
        affliction_id=AfflictionId.SILENCE,
        stacks=1,
        max_stacks=2,
        duration_ticks=60,
        refresh_policy=AfflictionRefreshPolicy.KEEP_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.CRIPPLE: AfflictionSpec(
        affliction_id=AfflictionId.CRIPPLE,
        stacks=1,
        max_stacks=4,
        duration_ticks=90,
        refresh_policy=AfflictionRefreshPolicy.RESET_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.SOUL_MARK: AfflictionSpec(
        affliction_id=AfflictionId.SOUL_MARK,
        stacks=1,
        max_stacks=6,
        duration_ticks=120,
        refresh_policy=AfflictionRefreshPolicy.EXTEND_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
    AfflictionId.RADIANT_MARK: AfflictionSpec(
        affliction_id=AfflictionId.RADIANT_MARK,
        stacks=1,
        max_stacks=4,
        duration_ticks=100,
        refresh_policy=AfflictionRefreshPolicy.MAX_DURATION,
        audience=AfflictionAudience.PVE_ONLY,
    ),
}


class _AfflictApi:
    def preset(self, affliction_id: AfflictionId) -> AfflictionSpec:
        base = _PRESETS[affliction_id]
        return AfflictionSpec(
            affliction_id=base.affliction_id,
            stacks=base.stacks,
            max_stacks=base.max_stacks,
            duration_ticks=base.duration_ticks,
            refresh_policy=base.refresh_policy,
            audience=base.audience,
            tick=base.tick,
            on_apply=base.on_apply,
            on_stack=base.on_stack,
            on_expire=base.on_expire,
        )

    def define(
        self,
        *,
        affliction_id: AfflictionIdLike,
        stacks: int = 1,
        max_stacks: int = 5,
        duration_ticks: int = 100,
        refresh_policy: AfflictionRefreshPolicyLike = AfflictionRefreshPolicy.RESET_DURATION,
        audience: AfflictionAudienceLike = AfflictionAudience.PVE_ONLY,
        tick: AfflictionTickSpec | None = None,
        on_apply: ActionSpec | None = None,
        on_stack: ActionSpec | None = None,
        on_expire: ActionSpec | None = None,
    ) -> AfflictionSpec:
        return AfflictionSpec(
            affliction_id=affliction_id,
            stacks=stacks,
            max_stacks=max_stacks,
            duration_ticks=duration_ticks,
            refresh_policy=refresh_policy,
            audience=audience,
            tick=tick,
            on_apply=on_apply,
            on_stack=on_stack,
            on_expire=on_expire,
        )

    def apply(self, spec: AfflictionSpec) -> AfflictApplyAction:
        return spec.to_action()

    def clear(self, *, affliction_id: AfflictionIdLike | None = None) -> AfflictClearAction:
        token = _coerce_affliction_id(affliction_id, field="vfx.afflict.clear.id") if affliction_id is not None else None
        return fx.afflict_clear(affliction_id=token)

    def consume(
        self,
        *,
        affliction_id: AfflictionIdLike,
        stacks: int = 1,
        require_at_least: int = 1,
        on_success: ActionSpec | None = None,
        on_failure: ActionSpec | None = None,
    ) -> AfflictConsumeAction:
        return fx.afflict_consume(
            affliction_id=_coerce_affliction_id(affliction_id, field="vfx.afflict.consume.id"),
            stacks=stacks,
            require_at_least=require_at_least,
            on_success=on_success,
            on_failure=on_failure,
        )

    def require_present(self, *, affliction_id: AfflictionIdLike) -> RequirementSpec:
        return AfflictPresentRequirement(
            affliction_id=_coerce_affliction_id(affliction_id, field="vfx.afflict.require_present.id")
        )

    def require_stacks_gte(self, *, affliction_id: AfflictionIdLike, stacks: int) -> RequirementSpec:
        return AfflictStacksGteRequirement(
            affliction_id=_coerce_affliction_id(affliction_id, field="vfx.afflict.require_stacks_gte.id"),
            stacks=stacks,
        )

    def require_stacks_lte(self, *, affliction_id: AfflictionIdLike, stacks: int) -> RequirementSpec:
        return AfflictStacksLteRequirement(
            affliction_id=_coerce_affliction_id(affliction_id, field="vfx.afflict.require_stacks_lte.id"),
            stacks=stacks,
        )

    def require_remaining_lte(self, *, affliction_id: AfflictionIdLike, remaining_ticks: int) -> RequirementSpec:
        return AfflictRemainingLteRequirement(
            affliction_id=_coerce_affliction_id(affliction_id, field="vfx.afflict.require_remaining_lte.id"),
            remaining_ticks=remaining_ticks,
        )

    def catalog(self) -> dict[str, Any]:
        return {
            "presets": tuple(entry.value for entry in AfflictionId),
            "defaultAudience": AfflictionAudience.PVE_ONLY.value,
        }


afflict = _AfflictApi()


__all__ = [
    "AfflictionId",
    "CustomAfflictionId",
    "AfflictionIdLike",
    "custom_affliction_id",
    "AfflictionTickSpec",
    "AfflictionSpec",
    "afflict",
]
