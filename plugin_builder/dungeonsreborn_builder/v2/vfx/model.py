"""High-level reusable VFX model (timeline + clip + modifier + anchor)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Mapping, Sequence

from dungeonsreborn_builder.v2.enums import (
    AnchorMode,
    AnchorModeLike,
    AnchorPoint,
    AnchorPointLike,
    AtMode,
    AtModeLike,
    Easing,
    EasingLike,
    StrEnum,
    TargetAnchor,
)


class VfxClipId(StrEnum):
    RING_PULSE = "clip.ring_pulse"
    RING_DUAL = "clip.ring_dual"
    POINT_FLASH = "clip.point_flash"
    LINE_BEAM = "clip.line_beam"
    ARC_SLASH = "clip.arc_slash"
    DISK_GROUND = "clip.disk_ground"
    SHELL_POP = "clip.shell_pop"
    FILL_BURST = "clip.fill_burst"
    HELIX_CHANNEL = "clip.helix_channel"
    POLYLINE_TETHER = "clip.polyline_tether"
    MESH_WARD = "clip.mesh_ward"
    CONE_WARNING = "clip.cone_warning"
    CYLINDER_PILLAR = "clip.cylinder_pillar"
    BOX_FIELD = "clip.box_field"
    POLYGON_SIGIL = "clip.polygon_sigil"
    POINTS_GLYPH = "clip.points_glyph"
    BEZIER_TRAIL = "clip.bezier_trail"
    SPLINE_RAIL = "clip.spline_rail"
    PHYSICS_PLUME = "clip.physics_plume"
    PHYSICS_SHARDS = "clip.physics_shards"
    PHYSICS_CRACK = "clip.physics_crack"
    PHYSICS_FRACTURE = "clip.physics_fracture"
    ZONE_OUTLINE = "clip.zone_outline"
    IMPACT_CORE = "clip.impact_core"
    CURVE_LISSAJOUS = "clip.curve_lissajous"
    CURVE_ROSE = "clip.curve_rose"
    CURVE_SPIROGRAPH = "clip.curve_spirograph"
    CURVE_EPITROCHOID = "clip.curve_epitrochoid"
    CURVE_HYPOTROCHOID = "clip.curve_hypotrochoid"
    CURVE_LEMNISCATE = "clip.curve_lemniscate"
    CURVE_TORUS_KNOT = "clip.curve_torus_knot"
    CURVE_LOG_SPIRAL = "clip.curve_log_spiral"
    VOLUME_TORUS_SHELL = "clip.volume_torus_shell"
    VOLUME_TORUS_FILLED = "clip.volume_torus_filled"
    VOLUME_CAPSULE_SHELL = "clip.volume_capsule_shell"
    VOLUME_CAPSULE_FILLED = "clip.volume_capsule_filled"
    VOLUME_SUPERELLIPSOID = "clip.volume_superellipsoid"
    VOLUME_OCTAHEDRON_WIRE = "clip.volume_octahedron_wire"
    VOLUME_ICOSAHEDRON_WIRE = "clip.volume_icosahedron_wire"
    VOLUME_PRISM_HEX = "clip.volume_prism_hex"
    FIELD_VORTEX = "clip.field_vortex"
    FIELD_SHOCKFRONT = "clip.field_shockfront"
    FIELD_ORBIT_SWARM = "clip.field_orbit_swarm"
    FIELD_RIBBON_TRAIL = "clip.field_ribbon_trail"
    FIELD_RAIN_COLUMN = "clip.field_rain_column"
    FIELD_GROUND_CRACKS = "clip.field_ground_cracks"
    FIELD_CHARGE_CORE = "clip.field_charge_core"
    FIELD_PHASE_GATE = "clip.field_phase_gate"
    REACT_RICOCHET_SPARKS = "clip.react_ricochet_sparks"
    REACT_CHAIN_ARC = "clip.react_chain_arc"
    REACT_FRACTURE_BLOOM = "clip.react_fracture_bloom"
    REACT_VOID_SINK = "clip.react_void_sink"
    REACT_HOLY_BLOOM = "clip.react_holy_bloom"
    REACT_POISON_HAZE = "clip.react_poison_haze"
    REACT_BLEED_FAN = "clip.react_bleed_fan"
    REACT_FROST_SHATTER = "clip.react_frost_shatter"
    REACT_LIGHTNING_CAGE = "clip.react_lightning_cage"
    REACT_WITHER_WISP = "clip.react_wither_wisp"
    REACT_HEAL_BURST = "clip.react_heal_burst"
    REACT_SHIELD_SNAP = "clip.react_shield_snap"


class VfxModifierId(StrEnum):
    FADE_IN = "mod.fade_in"
    FADE_OUT = "mod.fade_out"
    PULSE_AMP = "mod.pulse_amp"
    SCALE_RAMP = "mod.scale_ramp"
    DENSITY_RAMP = "mod.density_ramp"
    JITTER_SOFT = "mod.jitter_soft"
    JITTER_HARD = "mod.jitter_hard"
    ROTATE_Y = "mod.rotate_y"
    ROTATE_FULL = "mod.rotate_full"
    GRADIENT_SHIFT = "mod.gradient_shift"
    PALETTE_SWAP = "mod.palette_swap"
    NOISE_POS = "mod.noise_pos"
    NOISE_TIME = "mod.noise_time"
    SPEED_RAMP = "mod.speed_ramp"
    DRAG_BOOST = "mod.drag_boost"
    GRAVITY_FLIP = "mod.gravity_flip"
    ANCHOR_LAG = "mod.anchor_lag"
    PHASE_GATE = "mod.phase_gate"
    TIME_STRETCH = "mod.time_stretch"
    TIME_COMPRESS = "mod.time_compress"
    AMPLITUDE_WOBBLE = "mod.amplitude_wobble"
    RADIUS_PINGPONG = "mod.radius_pingpong"
    PHASE_OFFSET = "mod.phase_offset"
    SEED_JITTER = "mod.seed_jitter"
    TURBULENCE_SOFT = "mod.turbulence_soft"
    TURBULENCE_HARD = "mod.turbulence_hard"
    COLOR_CYCLE = "mod.color_cycle"
    COLOR_PINGPONG = "mod.color_pingpong"
    ALPHA_FADE_BY_PHASE = "mod.alpha_fade_by_phase"
    LINE_STEP_LOD = "mod.line_step_lod"
    COUNT_LOD = "mod.count_lod"
    PHYSICS_DAMPEN = "mod.physics_dampen"
    PHYSICS_EXPLODE = "mod.physics_explode"
    ANCHOR_TRAIL_LAG = "mod.anchor_trail_lag"
    SNAP_GROUND = "mod.snap_ground"


class VfxAnchorId(StrEnum):
    ORIGIN_STATIC = "anchor.origin_static"
    CASTER_CENTER = "anchor.caster_center"
    CASTER_FORWARD = "anchor.caster_forward"
    TARGET_LOCK = "anchor.target_lock"
    LAST_ENTITY = "anchor.last_entity"
    GROUND_SNAP = "anchor.ground_snap"
    GROUND_PATH = "anchor.ground_path"
    ORBIT_CASTER = "anchor.orbit_caster"
    ORBIT_TARGET = "anchor.orbit_target"
    SEGMENT_START = "anchor.segment_start"
    SEGMENT_END = "anchor.segment_end"
    CHAIN_LINKS = "anchor.chain_links"
    TARGET_PREDICTED = "anchor.target_predicted"
    SURFACE_NORMAL = "anchor.surface_normal"
    PROJECTILE_PATH_HEAD = "anchor.projectile_path_head"
    PROJECTILE_PATH_TAIL = "anchor.projectile_path_tail"
    BETWEEN_CASTER_TARGET = "anchor.between_caster_target"
    CASTER_HAND_MAIN = "anchor.caster_hand_main"
    CASTER_HAND_OFF = "anchor.caster_hand_off"
    HIT_BLOCK_FACE = "anchor.hit_block_face"


class VfxArchetypeId(StrEnum):
    IMPACT_CRISP_STRIKE = "arch.impact.crisp_strike"
    IMPACT_HEAVY_BURST = "arch.impact.heavy_burst"
    IMPACT_SHOCK_CORE = "arch.impact.shock_core"
    IMPACT_FRACTURE_LINE = "arch.impact.fracture_line"
    IMPACT_GEOM_STAMP = "arch.impact.geom_stamp"
    IMPACT_ECHO_DOUBLE = "arch.impact.echo_double"

    PROJ_CLEAN_BEAM = "arch.proj.clean_beam"
    PROJ_CURVED_SHOT = "arch.proj.curved_shot"
    PROJ_SPIRAL_DART = "arch.proj.spiral_dart"
    PROJ_TETHER_TRACE = "arch.proj.tether_trace"
    PROJ_ASH_TRAIL = "arch.proj.ash_trail"
    PROJ_FRAG_PATH = "arch.proj.frag_path"

    ZONE_CLEAN_RING = "arch.zone.clean_ring"
    ZONE_GROUND_FIELD = "arch.zone.ground_field"
    ZONE_PILLAR_LOCK = "arch.zone.pillar_lock"
    ZONE_CUBIC_GATE = "arch.zone.cubic_gate"
    ZONE_DOME_WARNING = "arch.zone.dome_warning"
    ZONE_FRACTURED_HAZARD = "arch.zone.fractured_hazard"

    AURA_PERSONAL_SOFT = "arch.aura.personal_soft"
    AURA_HALO_GUARD = "arch.aura.halo_guard"
    AURA_CHANNEL_HELIX = "arch.aura.channel_helix"
    AURA_SIGIL_SUPPORT = "arch.aura.sigil_support"
    AURA_GROUP_BEACON = "arch.aura.group_beacon"
    AURA_STABLE_FIELD = "arch.aura.stable_field"

    MOVE_DASH_CLEAN = "arch.move.dash_clean"
    MOVE_BLINK_ARCANE = "arch.move.blink_arcane"
    MOVE_PATH_PREVIEW = "arch.move.path_preview"
    MOVE_JUMP_WAVE = "arch.move.jump_wave"
    MOVE_EVASIVE_SPIRAL = "arch.move.evasive_spiral"
    MOVE_HEAVY_STRIDE = "arch.move.heavy_stride"

    BOSS_CONE_THREAT = "arch.boss.cone_threat"
    BOSS_ZONE_LOCK = "arch.boss.zone_lock"
    BOSS_RUNE_CHARGE = "arch.boss.rune_charge"
    BOSS_IMPACT_CALL = "arch.boss.impact_call"
    BOSS_PILLAR_SWEEP = "arch.boss.pillar_sweep"
    BOSS_FRACTURE_MESH = "arch.boss.fracture_mesh"
    MAGIC_OFFENSE_ARCANE_LANCE = "arch.magic.offense.arcane_lance"
    MAGIC_OFFENSE_VOID_NOVA = "arch.magic.offense.void_nova"
    MAGIC_OFFENSE_EMBER_SPEAR = "arch.magic.offense.ember_spear"
    MAGIC_OFFENSE_CHAIN_SURGE = "arch.magic.offense.chain_surge"
    MAGIC_OFFENSE_RUPTURE_SPIKE = "arch.magic.offense.rupture_spike"
    MAGIC_CONTROL_FROST_SNARE = "arch.magic.control.frost_snare"
    MAGIC_CONTROL_GRAVITY_WELL = "arch.magic.control.gravity_well"
    MAGIC_CONTROL_SILENCE_RING = "arch.magic.control.silence_ring"
    MAGIC_CONTROL_FEAR_WAVE = "arch.magic.control.fear_wave"
    MAGIC_CONTROL_STASIS_CUBE = "arch.magic.control.stasis_cube"
    MAGIC_SUPPORT_SANCTUM_PULSE = "arch.magic.support.sanctum_pulse"
    MAGIC_SUPPORT_REGEN_STREAM = "arch.magic.support.regen_stream"
    MAGIC_SUPPORT_AEGIS_LINK = "arch.magic.support.aegis_link"
    MAGIC_SUPPORT_CLEANSE_BLOOM = "arch.magic.support.cleanse_bloom"
    MAGIC_SUPPORT_HASTE_FIELD = "arch.magic.support.haste_field"
    MAGIC_MOBILITY_BLINK_TRACE = "arch.magic.mobility.blink_trace"
    MAGIC_MOBILITY_DASH_AFTERIMAGE = "arch.magic.mobility.dash_afterimage"
    MAGIC_MOBILITY_PHASE_STEP = "arch.magic.mobility.phase_step"
    MAGIC_MOBILITY_LAUNCH_SPIRAL = "arch.magic.mobility.launch_spiral"
    MAGIC_MOBILITY_SHADOW_SLIDE = "arch.magic.mobility.shadow_slide"
    MAGIC_DEFENSE_BARRIER_SHELL = "arch.magic.defense.barrier_shell"
    MAGIC_DEFENSE_REFLECT_PRISM = "arch.magic.defense.reflect_prism"
    MAGIC_DEFENSE_ABSORB_CORE = "arch.magic.defense.absorb_core"
    MAGIC_DEFENSE_WARD_COLUMNS = "arch.magic.defense.ward_columns"
    MAGIC_DEFENSE_GUARD_ORBIT = "arch.magic.defense.guard_orbit"
    MAGIC_ULTIMATE_STARFALL = "arch.magic.ultimate.starfall"
    MAGIC_ULTIMATE_CATACLYSM_DISC = "arch.magic.ultimate.cataclysm_disc"
    MAGIC_ULTIMATE_ENTROPY_MAELSTROM = "arch.magic.ultimate.entropy_maelstrom"
    MAGIC_ULTIMATE_SOLAR_VORTEX = "arch.magic.ultimate.solar_vortex"
    MAGIC_ULTIMATE_CHRONA_RESET = "arch.magic.ultimate.chrona_reset"


class VfxBucket(StrEnum):
    IMPACT = "impact"
    PROJECTILE = "projectile"
    ZONE = "zone"
    AURA = "aura"
    MOBILITY = "mobility"
    BOSS = "boss"
    MAGIC_OFFENSE = "magic.offense"
    MAGIC_CONTROL = "magic.control"
    MAGIC_SUPPORT = "magic.support"
    MAGIC_MOBILITY = "magic.mobility"
    MAGIC_DEFENSE = "magic.defense"
    MAGIC_ULTIMATE = "magic.ultimate"


class VfxLod(StrEnum):
    HIGH = "high"
    MEDIUM = "medium"
    LOW = "low"


class VfxBudgetTier(StrEnum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class VfxPhaseKind(StrEnum):
    ANTICIPATION = "anticipation"
    ACTIVATION = "activation"
    DECAY = "decay"
    RESIDUAL = "residual"


class VfxReadiness(StrEnum):
    READY = "ready"
    NEEDS_TUNING = "needs_tuning"
    EXPERIMENTAL = "experimental"


VfxClipLike = VfxClipId | str
VfxModifierLike = VfxModifierId | str
VfxAnchorLike = VfxAnchorId | str
VfxArchetypeLike = VfxArchetypeId | str


@dataclass(frozen=True)
class ClipInstance:
    clip_id: VfxClipLike
    params: Mapping[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ModifierInstance:
    modifier_id: VfxModifierLike
    params: Mapping[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class PhaseSpec:
    kind: VfxPhaseKind
    clips: Sequence[ClipInstance] = field(default_factory=tuple)
    modifiers: Sequence[ModifierInstance] = field(default_factory=tuple)
    duration_ticks: int = 20
    delay_ticks: int = 0

    def __post_init__(self) -> None:
        if int(self.duration_ticks) < 0:
            raise ValueError(f"vfx.phase.{self.kind.value}.duration_ticks: must be >= 0")
        if int(self.delay_ticks) < 0:
            raise ValueError(f"vfx.phase.{self.kind.value}.delay_ticks: must be >= 0")


@dataclass(frozen=True)
class TimelineSpec:
    anticipation: PhaseSpec | None = None
    activation: PhaseSpec | None = None
    decay: PhaseSpec | None = None
    residual: PhaseSpec | None = None
    period_ticks: int = 1
    follow_caster: bool = True
    easing: EasingLike = Easing.IN_OUT_CUBIC

    def __post_init__(self) -> None:
        if int(self.period_ticks) <= 0:
            raise ValueError("vfx.timeline.period_ticks: must be > 0")
        if (
            self.anticipation is None
            and self.activation is None
            and self.decay is None
            and self.residual is None
        ):
            raise ValueError("vfx.timeline: at least one phase is required")


@dataclass(frozen=True)
class AnchorSpec:
    anchor_id: VfxAnchorLike
    mode: AnchorModeLike = AnchorMode.ORIGIN
    point: AnchorPointLike | None = None
    at_mode: AtModeLike = AtMode.ORIGIN
    particle_at: TargetAnchor = TargetAnchor.ORIGIN
    line_target_at: TargetAnchor | None = None
    forward: float = 0.0
    right: float = 0.0
    up: float = 0.0


@dataclass(frozen=True)
class VariationSpec:
    seed: int | None = None
    palette: str | None = None
    scale: float = 1.0
    density: float = 1.0
    duration_scale: float = 1.0
    jitter: float = 0.0

    def __post_init__(self) -> None:
        if float(self.scale) <= 0.0:
            raise ValueError("vfx.variation.scale: must be > 0")
        if float(self.density) <= 0.0:
            raise ValueError("vfx.variation.density: must be > 0")
        if float(self.duration_scale) <= 0.0:
            raise ValueError("vfx.variation.duration_scale: must be > 0")
        if float(self.jitter) < 0.0:
            raise ValueError("vfx.variation.jitter: must be >= 0")


@dataclass(frozen=True)
class BudgetSpec:
    tier: VfxBudgetTier = VfxBudgetTier.MEDIUM
    lod: VfxLod = VfxLod.MEDIUM
    max_layers: int = 3
    fallback_lod: VfxLod = VfxLod.LOW

    def __post_init__(self) -> None:
        if int(self.max_layers) <= 0:
            raise ValueError("vfx.budget.max_layers: must be > 0")


@dataclass(frozen=True)
class ClipSpec:
    clip_id: VfxClipId
    intent: str
    budget: VfxBudgetTier
    builder: Callable[[Mapping[str, Any]], Any]


@dataclass(frozen=True)
class ModifierSpec:
    modifier_id: VfxModifierId
    intent: str
    apply: Callable[[dict[str, Any], Mapping[str, Any], VfxPhaseKind], dict[str, Any]]


@dataclass(frozen=True)
class ArchetypeSpec:
    archetype_id: VfxArchetypeId
    bucket: VfxBucket
    intent: str
    timeline: TimelineSpec
    anchor: VfxAnchorLike
    budget: BudgetSpec
    modifiers: Sequence[ModifierInstance] = field(default_factory=tuple)
    readiness: VfxReadiness = VfxReadiness.READY


@dataclass(frozen=True)
class ArchetypeInstance:
    archetype_id: VfxArchetypeLike
    lod: VfxLod | None = None
    anchor: VfxAnchorLike | None = None
    budget: BudgetSpec | None = None
    modifiers: Sequence[ModifierInstance] = field(default_factory=tuple)
    variation: VariationSpec = field(default_factory=VariationSpec)


__all__ = [
    "VfxClipId",
    "VfxModifierId",
    "VfxAnchorId",
    "VfxArchetypeId",
    "VfxBucket",
    "VfxLod",
    "VfxBudgetTier",
    "VfxPhaseKind",
    "VfxReadiness",
    "VfxClipLike",
    "VfxModifierLike",
    "VfxAnchorLike",
    "VfxArchetypeLike",
    "ClipInstance",
    "ModifierInstance",
    "PhaseSpec",
    "TimelineSpec",
    "AnchorSpec",
    "VariationSpec",
    "BudgetSpec",
    "ClipSpec",
    "ModifierSpec",
    "ArchetypeSpec",
    "ArchetypeInstance",
]
