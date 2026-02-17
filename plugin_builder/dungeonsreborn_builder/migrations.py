"""Migration helpers for legacy compatibility outputs."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Iterable, List, Mapping, Optional, Tuple


@dataclass(frozen=True)
class MigrationMap:
    """Configuration for compatibility output."""

    key_renames: Mapping[str, str] = field(default_factory=dict)
    enum_aliases: Mapping[str, Mapping[str, str]] = field(default_factory=dict)


DEFAULT_MIGRATIONS = MigrationMap(
    key_renames={
        "delayTicks": "delay",
        "periodTicks": "period",
        "durationTicks": "ticks",
        "ttlTicks": "ttl",
        "cooldownTicks": "cooldown",
    },
    enum_aliases={},
)


@dataclass
class MigrationReport:
    renames: List[Tuple[str, str, str]] = field(default_factory=list)
    enum_aliases: List[Tuple[str, str, str, str]] = field(default_factory=list)
    schema_steps: List[str] = field(default_factory=list)

    def add_rename(self, path: str, new_key: str, old_key: str) -> None:
        self.renames.append((path, new_key, old_key))

    def add_enum_alias(self, path: str, key: str, value: str, alias: str) -> None:
        self.enum_aliases.append((path, key, value, alias))

    def add_schema_step(self, name: str) -> None:
        self.schema_steps.append(name)


@dataclass(frozen=True)
class SchemaAdapter:
    name: str
    from_version: int
    to_version: int
    description: str
    apply: Callable[[Dict[str, Any]], Dict[str, Any]]


def _apply_enum_aliases(key: str, value: Any, migrations: MigrationMap, prefer_legacy: bool) -> Any:
    if not prefer_legacy:
        return value
    if not isinstance(value, str):
        return value
    aliases = migrations.enum_aliases.get(key)
    if not aliases:
        return value
    return aliases.get(value, value)


def apply_legacy_compat(value: Any, migrations: Optional[MigrationMap], prefer_legacy_enums: bool = False) -> Any:
    if migrations is None:
        return value
    if isinstance(value, dict):
        out: Dict[str, Any] = {}
        for key, entry in value.items():
            migrated_entry = apply_legacy_compat(entry, migrations, prefer_legacy_enums)
            migrated_entry = _apply_enum_aliases(key, migrated_entry, migrations, prefer_legacy_enums)
            out[key] = migrated_entry
            legacy_key = migrations.key_renames.get(key)
            if legacy_key and legacy_key not in out:
                out[legacy_key] = migrated_entry
        return out
    if isinstance(value, list):
        return [apply_legacy_compat(entry, migrations, prefer_legacy_enums) for entry in value]
    return value


def apply_legacy_compat_with_report(
    value: Any,
    migrations: Optional[MigrationMap],
    prefer_legacy_enums: bool = False,
    path: str = "",
    report: Optional[MigrationReport] = None,
) -> Tuple[Any, MigrationReport]:
    report = report or MigrationReport()
    if migrations is None:
        return value, report
    if isinstance(value, dict):
        out: Dict[str, Any] = {}
        for key, entry in value.items():
            next_path = f"{path}.{key}" if path else key
            migrated_entry, report = apply_legacy_compat_with_report(
                entry, migrations, prefer_legacy_enums, next_path, report
            )
            aliased_entry = _apply_enum_aliases(key, migrated_entry, migrations, prefer_legacy_enums)
            if aliased_entry != migrated_entry and isinstance(migrated_entry, str):
                report.add_enum_alias(next_path, key, migrated_entry, aliased_entry)
            out[key] = aliased_entry
            legacy_key = migrations.key_renames.get(key)
            if legacy_key and legacy_key not in out:
                out[legacy_key] = aliased_entry
                report.add_rename(path or "<root>", legacy_key, key)
        return out, report
    if isinstance(value, list):
        migrated_list = []
        for index, entry in enumerate(value):
            next_path = f"{path}[{index}]"
            migrated_entry, report = apply_legacy_compat_with_report(
                entry, migrations, prefer_legacy_enums, next_path, report
            )
            migrated_list.append(migrated_entry)
        return migrated_list, report
    return value, report


def apply_schema_adapters(
    data: Dict[str, Any],
    current_version: Optional[int],
    adapters: Iterable[SchemaAdapter],
    report: Optional[MigrationReport] = None,
) -> Tuple[Dict[str, Any], MigrationReport]:
    report = report or MigrationReport()
    if current_version is None:
        return data, report
    version = current_version
    pending = sorted(adapters, key=lambda adapter: adapter.from_version)
    for adapter in pending:
        if adapter.from_version == version:
            data = adapter.apply(data)
            version = adapter.to_version
            report.add_schema_step(f"{adapter.name}: {adapter.description}")
    return data, report
