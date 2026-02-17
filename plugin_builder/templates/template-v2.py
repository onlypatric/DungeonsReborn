"""Template v2: user progression baseline + dynamic per-biome mob ranges."""

from __future__ import annotations

import argparse
import hashlib
import re
from dataclasses import dataclass
from enum import Enum
from pathlib import Path


class ZoneSize(str, Enum):
    SMALL = "small"
    MEDIUM = "medium"
    LARGE = "large"
    EXTREME = "extremely_large"


@dataclass(frozen=True)
class UserStats:
    hp: int
    armor: int
    attack_damage: int
    attack_speed: int

    @property
    def dps(self) -> float:
        return round(self.attack_damage * self.attack_speed, 2)


@dataclass(frozen=True)
class BiomeTier:
    id: str
    label: str
    tier: int
    rank: float
    size: ZoneSize
    hp_factor: float = 1.0
    armor_factor: float = 1.0


class MobRole(str, Enum):
    PASSIVE = "passive"
    TRASH = "trash"
    RANGED = "ranged"
    BRUISER = "bruiser"
    ELITE = "elite"
    BOSS = "boss"


@dataclass(frozen=True)
class RoleProfile:
    hp_pct: float
    armor_pct: float
    dmg_pct: float
    atk_speed_delta: int = 0


BASELINE = UserStats(hp=16, armor=1, attack_damage=4, attack_speed=1)
MAX_USER_HP = 60
MAX_USER_ARMOR = 60

BIOME_TIERS: tuple[BiomeTier, ...] = (
    BiomeTier("plains", "Plains", 1, 1.0, ZoneSize.LARGE, hp_factor=1.00, armor_factor=1.00),
    BiomeTier("mini_ocean_monument", "Mini Ocean Monument", 1, 1.5, ZoneSize.SMALL, hp_factor=1.00, armor_factor=1.05),
    BiomeTier("desert", "Desert", 2, 2.0, ZoneSize.EXTREME, hp_factor=0.98, armor_factor=1.08),
    BiomeTier("limbo", "Limbo", 3, 3.0, ZoneSize.LARGE, hp_factor=0.97, armor_factor=1.10),
    BiomeTier("nether", "Nether", 3, 3.2, ZoneSize.LARGE, hp_factor=0.96, armor_factor=1.12),
    BiomeTier("dark_oak", "Dark Oak", 4, 4.0, ZoneSize.LARGE, hp_factor=0.95, armor_factor=1.15),
    BiomeTier("swamp", "Swamp", 5, 5.0, ZoneSize.SMALL, hp_factor=0.95, armor_factor=1.16),
    BiomeTier("mushroom", "Mushroom", 5, 5.2, ZoneSize.SMALL, hp_factor=0.95, armor_factor=1.18),
    BiomeTier("highlands", "Highlands", 6, 6.0, ZoneSize.MEDIUM, hp_factor=0.95, armor_factor=1.20),
    BiomeTier("lowlands", "Lowlands", 7, 7.0, ZoneSize.LARGE, hp_factor=0.95, armor_factor=1.22),
    BiomeTier("snow", "Snow", 8, 8.0, ZoneSize.EXTREME, hp_factor=1.00, armor_factor=1.00),
)

ROLE_PROFILES: dict[MobRole, RoleProfile] = {
    MobRole.PASSIVE: RoleProfile(hp_pct=0.70, armor_pct=0.45, dmg_pct=0.10, atk_speed_delta=0),
    MobRole.TRASH: RoleProfile(hp_pct=0.85, armor_pct=0.65, dmg_pct=0.25, atk_speed_delta=0),
    MobRole.RANGED: RoleProfile(hp_pct=0.75, armor_pct=0.55, dmg_pct=0.35, atk_speed_delta=0),
    MobRole.BRUISER: RoleProfile(hp_pct=1.15, armor_pct=1.00, dmg_pct=0.45, atk_speed_delta=0),
    MobRole.ELITE: RoleProfile(hp_pct=1.45, armor_pct=1.25, dmg_pct=0.65, atk_speed_delta=0),
    MobRole.BOSS: RoleProfile(hp_pct=1.00, armor_pct=1.00, dmg_pct=0.85, atk_speed_delta=1),
}

REQUESTED_MOBS: tuple[str, ...] = (
    "BLAZE", "CREEPER", "DROWNED", "ELDER_GUARDIAN", "ENDERMITE", "EVOKER", "GHAST", "GUARDIAN",
    "HOGLIN", "HUSK", "MAGMA_CUBE", "PHANTOM", "PIGLIN_BRUTE", "PILLAGER", "RAVAGER", "SHULKER",
    "SILVERFISH", "SKELETON", "SLIME", "SPIDER", "CAVE_SPIDER", "STRAY", "VEX", "VINDICATOR",
    "WITCH", "WITHER_SKELETON", "WARDEN", "ZOGLIN", "ZOMBIE", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN",
    "ILLUSIONER", "BEE", "FOX", "LLAMA", "PANDA", "POLAR_BEAR", "PIGLIN", "WOLF", "TRADER_LLAMA",
    "ALLAY", "AXOLOTL", "BAT", "CAT", "CHICKEN", "COW", "DONKEY", "HORSE", "MULE", "MOOSHROOM",
    "OCELOT", "PIG", "RABBIT", "SHEEP", "TURTLE", "VILLAGER", "WANDERING_TRADER",
    # Added from local Paper docs.
    "ARMADILLO", "BOGGED", "BREEZE", "CAMEL", "COD", "CREAKING", "DOLPHIN", "ENDERMAN", "FROG", "GIANT",
    "GLOW_SQUID", "GOAT", "HAPPY_GHAST", "IRON_GOLEM", "PARROT", "PUFFERFISH", "SALMON", "SKELETON_HORSE",
    "SNIFFER", "SNOW_GOLEM", "SQUID", "STRIDER", "TADPOLE", "TROPICAL_FISH", "ZOMBIE_HORSE",
)

PASSIVE_MOBS: set[str] = {
    "ALLAY", "ARMADILLO", "AXOLOTL", "BAT", "BEE", "CAMEL", "CAT", "CHICKEN", "COD", "COW", "DOLPHIN",
    "DONKEY", "FOX", "FROG", "GLOW_SQUID", "GOAT", "HORSE", "LLAMA", "MOOSHROOM", "MULE", "OCELOT", "PANDA",
    "PARROT", "PIG", "POLAR_BEAR", "PUFFERFISH", "RABBIT", "SALMON", "SHEEP", "SKELETON_HORSE", "SNIFFER",
    "SQUID", "STRIDER", "TADPOLE", "TROPICAL_FISH", "TURTLE", "VILLAGER", "WANDERING_TRADER", "ZOMBIE_HORSE",
}


def mobs_from_local_paper_docs() -> set[str]:
    path = Path("docs/reference/paper-api-1.21.8-javadoc/org/bukkit/entity/EntityType.html")
    if not path.exists():
        return set()
    html = path.read_text(encoding="utf-8")
    names = set(re.findall(r'href="#([A-Z0-9_]+)" class="member-name-link"', html))
    blocked_exact = {
        "UNKNOWN", "PLAYER", "ENDER_DRAGON", "WITHER", "EYE_OF_ENDER", "LINGERING_POTION", "SPLASH_POTION",
        "AREA_EFFECT_CLOUD", "ARMOR_STAND", "LIGHTNING_BOLT", "INTERACTION", "ITEM", "ITEM_FRAME", "PAINTING",
        "LEASH_KNOT", "MARKER", "FALLING_BLOCK", "OMINOUS_ITEM_SPAWNER",
    }
    blocked_fragments = ("BOAT", "MINECART", "DISPLAY", "ARROW", "POTION", "FIREBALL", "WIND_CHARGE", "SKULL")
    out: set[str] = set()
    for name in names:
        if name in blocked_exact:
            continue
        if any(fragment in name for fragment in blocked_fragments):
            continue
        out.add(name)
    return out


ALL_MOBS: tuple[str, ...] = tuple(
    sorted(set(REQUESTED_MOBS) & mobs_from_local_paper_docs())
)
NON_PASSIVE_ROLES = (
    MobRole.TRASH,
    MobRole.RANGED,
    MobRole.BRUISER,
    MobRole.ELITE,
    MobRole.BOSS,
)


def progress_for_rank(rank: float) -> float:
    return max(0.0, min(1.0, (rank - 1.0) / 7.0))


def user_stats_for_biome(biome: BiomeTier) -> UserStats:
    progress = progress_for_rank(biome.rank)
    hp = BASELINE.hp + ((MAX_USER_HP - BASELINE.hp) * progress)
    armor = BASELINE.armor + ((MAX_USER_ARMOR - BASELINE.armor) * progress)
    damage = BASELINE.attack_damage * (1.24 ** (biome.rank - 1.0))
    speed = min(2.0, BASELINE.attack_speed + ((biome.rank - 1.0) * 0.10))
    return UserStats(
        hp=min(MAX_USER_HP, max(1, int(round(hp * biome.hp_factor)))),
        armor=min(MAX_USER_ARMOR, max(0, int(round(armor * biome.armor_factor)))),
        attack_damage=max(1, int(round(damage))),
        attack_speed=max(1, int(round(speed))),
    )


def mob_stats_for_role(user: UserStats, role: MobRole) -> UserStats:
    rp = ROLE_PROFILES[role]
    return UserStats(
        hp=max(1, int(round(user.hp * rp.hp_pct))),
        armor=max(0, int(round(user.armor * rp.armor_pct))),
        attack_damage=max(1, int(round(user.attack_damage * rp.dmg_pct))),
        attack_speed=max(1, user.attack_speed + rp.atk_speed_delta),
    )


def _stable_bucket(value: str, modulo: int) -> int:
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % modulo


def role_for_mob(mob: str, biome_id: str) -> MobRole:
    if mob in PASSIVE_MOBS:
        return MobRole.PASSIVE
    idx = _stable_bucket(f"{biome_id}:{mob}", len(NON_PASSIVE_ROLES))
    return NON_PASSIVE_ROLES[idx]


def _range_from_center(value: int, spread: float, min_value: int = 0) -> tuple[int, int]:
    lo = max(min_value, int(round(value * (1.0 - spread))))
    hi = max(lo, int(round(value * (1.0 + spread))))
    return lo, hi


def _mob_range_for_biome(user: UserStats, role: MobRole, rank: float) -> tuple[int, int, int, int, float, float]:
    base = mob_stats_for_role(user, role)
    progress = progress_for_rank(rank)
    spread = 0.25 - (0.10 * progress)  # wider ranges early, tighter late
    hp_min, hp_max = _range_from_center(base.hp, spread, min_value=1)
    armor_min, armor_max = _range_from_center(base.armor, spread, min_value=0)
    dmg_min, dmg_max = _range_from_center(base.attack_damage, spread, min_value=1)
    dps_min = round(dmg_min * base.attack_speed, 2)
    dps_max = round(dmg_max * base.attack_speed, 2)
    return hp_min, hp_max, armor_min, armor_max, dps_min, dps_max


def iter_mob_rows():
    for biome in BIOME_TIERS:
        user = user_stats_for_biome(biome)
        for mob in ALL_MOBS:
            role = role_for_mob(mob, biome.id)
            yield (biome.id, mob, role, *_mob_range_for_biome(user, role, biome.rank))


def validate_mob_coverage() -> set[str]:
    return set(REQUESTED_MOBS) - set(ALL_MOBS)


def print_tier_table() -> None:
    print(
        "BASELINE",
        f"hp={BASELINE.hp}",
        f"armor={BASELINE.armor}",
        f"damage={BASELINE.attack_damage}",
        f"attack_speed={BASELINE.attack_speed}",
        f"dps={BASELINE.dps}",
    )
    for biome in BIOME_TIERS:
        stats = user_stats_for_biome(biome)
        print(
            f"{biome.label} (T{biome.tier}, rank={biome.rank}, {biome.size.value})",
            f"hp={stats.hp}",
            f"armor={stats.armor}",
            f"dmg={stats.attack_damage}",
            f"atkSpd={stats.attack_speed}",
            f"dps={stats.dps}",
        )


def print_mob_table() -> None:
    missing = validate_mob_coverage()
    if missing:
        print("COVERAGE_ERROR", f"missing={sorted(missing)}", f"generated={len(ALL_MOBS)}")
    else:
        print("COVERAGE_OK", f"requested={len(REQUESTED_MOBS)}", f"generated={len(ALL_MOBS)}")
    for biome_id, mob, role, hp_min, hp_max, armor_min, armor_max, dps_min, dps_max in iter_mob_rows():
        print(
            biome_id,
            mob,
            f"role={role.value}",
            f"hp={hp_min}-{hp_max}",
            f"armor={armor_min}-{armor_max}",
            f"dps={dps_min}-{dps_max}",
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="Template v2 user baseline")
    parser.add_argument("--print-tiers", action="store_true", help="Print user progression table")
    parser.add_argument("--print-mobs", action="store_true", help="Print tiered mob-role design + stats")
    parser.add_argument("--validate-mobs", action="store_true", help="Validate requested mob coverage")
    args = parser.parse_args()

    if args.print_tiers:
        print_tier_table()
        return
    if args.print_mobs:
        print_mob_table()
        return
    if args.validate_mobs:
        missing = validate_mob_coverage()
        if not missing:
            print(
                "OK",
                f"all requested mobs covered ({len(REQUESTED_MOBS)})",
                f"total_mobs={len(ALL_MOBS)}",
            )
        else:
            print("ERROR", f"missing={sorted(missing)}")
        return

    print("template-v2 now contains user progression + tiered mob role design.")
    print("Use --print-tiers, --print-mobs or --validate-mobs.")


if __name__ == "__main__":
    main()
