"""Shared builder abstractions."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Tuple

from .utils import apply_overrides, title_case_from_id, write_yaml
from .migrations import (
    DEFAULT_MIGRATIONS,
    MigrationMap,
    MigrationReport,
    SchemaAdapter,
    apply_legacy_compat,
    apply_legacy_compat_with_report,
    apply_schema_adapters,
)


def _prune_empty(value: Any) -> Any:
    if isinstance(value, dict):
        cleaned: Dict[str, Any] = {}
        for key, entry in value.items():
            if entry is None:
                continue
            pruned = _prune_empty(entry)
            if pruned is None:
                continue
            if isinstance(pruned, (dict, list)) and not pruned:
                continue
            cleaned[key] = pruned
        return cleaned
    if isinstance(value, list):
        cleaned_list = []
        for entry in value:
            if entry is None:
                continue
            pruned = _prune_empty(entry)
            if pruned is None:
                continue
            if isinstance(pruned, (dict, list)) and not pruned:
                continue
            cleaned_list.append(pruned)
        return cleaned_list
    return value


def snake_case(value: str) -> str:
    cleaned = []
    for ch in value.strip():
        if ch.isalnum():
            cleaned.append(ch.lower())
        else:
            cleaned.append("_")
    out = "".join(cleaned)
    while "__" in out:
        out = out.replace("__", "_")
    return out.strip("_")


@dataclass
class BuilderBase:
    _id: Optional[str] = None
    _name: Optional[str] = None
    _description: Optional[str] = None
    _advanced: bool = False
    _raw_overrides: List[Tuple[Dict[str, Any], Optional[str]]] = field(default_factory=list)
    _path_overrides: List[Tuple[str, Any, Optional[str]]] = field(default_factory=list)
    _override_warnings: List[str] = field(default_factory=list)

    def __post_init__(self) -> None:
        if self._id:
            self._id = snake_case(self._id)

    def id(self, value: str) -> "BuilderBase":
        self._id = snake_case(value)
        return self

    def name(self, value: str) -> "BuilderBase":
        self._name = value
        return self

    def description(self, value: str) -> "BuilderBase":
        self._description = value
        return self

    def advanced(self, enabled: bool = True) -> "BuilderBase":
        self._advanced = enabled
        return self

    def raw_override(self, mapping: Dict[str, Any], note: Optional[str] = None) -> "BuilderBase":
        if not self._advanced:
            raise ValueError("raw_override requires .advanced(True)")
        self._raw_overrides.append((mapping, note))
        return self

    def override(self, path: str, value: Any, note: Optional[str] = None) -> "BuilderBase":
        if not self._advanced:
            raise ValueError("override requires .advanced(True)")
        self._path_overrides.append((path, value, note))
        return self

    def _ensure_id(self, label: str) -> str:
        if not self._id and self._name:
            self._id = snake_case(self._name)
        if not self._id:
            raise ValueError(f"{label} is required")
        self._id = snake_case(self._id)
        return self._id

    def _ensure_name(self) -> None:
        if not self._name and self._id:
            self._name = title_case_from_id(self._id)

    def _apply_overrides(self, data: Dict[str, Any], label: str) -> Dict[str, Any]:
        if not self._raw_overrides and not self._path_overrides:
            return data
        merges = [entry for entry, _ in self._raw_overrides]
        sets = [(path, value) for path, value, _ in self._path_overrides]
        apply_overrides(data, merges, sets)
        self._override_warnings.extend(self._format_override_warnings(label))
        return data

    def _format_override_warnings(self, label: str) -> List[str]:
        warnings: List[str] = []
        for _, note in self._raw_overrides:
            suffix = f" ({note})" if note else ""
            warnings.append(f"{label}: advanced override merge applied{suffix}")
        for path, _, note in self._path_overrides:
            suffix = f" ({note})" if note else ""
            warnings.append(f"{label}: advanced override set {path}{suffix}")
        return warnings

    def override_warnings(self) -> List[str]:
        return list(self._override_warnings)

    def build(self) -> Dict[str, Any]:
        raise NotImplementedError


@dataclass
class Pipeline:
    """Small helper to provide .write() for built objects."""

    data: Dict[str, Any]

    def write(self, exporter: "ExporterBase", filename: str) -> str:
        return exporter.write_yaml(filename, self.data)


@dataclass
class ExporterBase:
    output_dir: str
    legacy_compat: bool = False
    migration_map: MigrationMap = field(default_factory=lambda: DEFAULT_MIGRATIONS)
    prefer_legacy_enums: bool = False
    schema_version: Optional[int] = None
    schema_adapters: List[SchemaAdapter] = field(default_factory=list)

    def write_yaml(self, filename: str, data: Any) -> str:
        payload = data
        if self.legacy_compat:
            payload = apply_legacy_compat(payload, self.migration_map, self.prefer_legacy_enums)
        path = f"{self.output_dir}/{filename}"
        write_yaml(path, _prune_empty(payload))
        return path

    def write_yaml_with_report(self, filename: str, data: Any) -> Tuple[str, MigrationReport]:
        payload = data
        report = MigrationReport()
        if isinstance(payload, dict):
            payload, report = apply_schema_adapters(
                payload,
                self.schema_version,
                self.schema_adapters,
                report,
            )
        if self.legacy_compat:
            payload, report = apply_legacy_compat_with_report(
                payload, self.migration_map, self.prefer_legacy_enums, report=report
            )
        path = f"{self.output_dir}/{filename}"
        write_yaml(path, _prune_empty(payload))
        return path, report

    def write_index(self, filenames: Iterable[str], index_name: str = "index.txt") -> str:
        path = f"{self.output_dir}/{index_name}"
        with open(path, "w", encoding="utf-8") as handle:
            for filename in filenames:
                handle.write(str(filename))
                handle.write("\n")
        return path

    def dry_run(self, filename: str, data: Any) -> str:
        return f"[dry-run] {self.output_dir}/{filename}"
