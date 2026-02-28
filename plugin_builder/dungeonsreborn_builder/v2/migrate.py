"""Lightweight migration helpers from legacy builder imports to v2."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from .internal.normalize import normalize_token

_IMPORT_REWRITES = {
    "from dungeonsreborn_builder import": "from dungeonsreborn_builder.v2 import",
    "from dungeonsreborn_builder.effects import": "from dungeonsreborn_builder.v2 import",
    "from dungeonsreborn_builder.mobs import": "from dungeonsreborn_builder.v2 import",
    "from dungeonsreborn_builder.items import": "from dungeonsreborn_builder.v2 import",
    "from dungeonsreborn_builder.crafting import": "from dungeonsreborn_builder.v2 import",
    "from dungeonsreborn_builder.shops import": "from dungeonsreborn_builder.v2 import",
    "from dungeonsreborn_builder.quests import": "from dungeonsreborn_builder.v2 import",
    "from dungeonsreborn_builder.upgrades import": "from dungeonsreborn_builder.v2 import",
}


@dataclass
class MigrationResult:
    path: str
    changed: bool
    rewrites: int = 0
    manual_flags: list[str] = field(default_factory=list)


@dataclass
class MigrationSummary:
    files: list[MigrationResult] = field(default_factory=list)

    @property
    def changed_files(self) -> int:
        return sum(1 for entry in self.files if entry.changed)

    @property
    def rewrite_count(self) -> int:
        return sum(entry.rewrites for entry in self.files)

    @property
    def manual_flags(self) -> int:
        return sum(len(entry.manual_flags) for entry in self.files)


def migrate_source(source: str) -> tuple[str, int, list[str]]:
    result = source
    rewrites = 0
    for old, new in _IMPORT_REWRITES.items():
        if old in result:
            count = result.count(old)
            result = result.replace(old, new)
            rewrites += count

    manual_flags: list[str] = []
    if ".advanced(" in result:
        manual_flags.append("contains .advanced(...): migrate to v2 first-class APIs")
    if "Action(" in result and "fx." not in result:
        manual_flags.append("contains raw Action(...): migrate to fx.* helpers where possible")
    if ".override(" in result:
        manual_flags.append("contains .override(...): check for v2 shorthand equivalents")
    return result, rewrites, manual_flags


def migrate_source_typed(source: str) -> tuple[str, int, list[str]]:
    result = source
    rewrites = 0
    manual_flags: list[str] = []
    imports_needed: set[str] = set()

    def _sub(pattern: str, repl_func) -> None:
        nonlocal result, rewrites
        compiled = re.compile(pattern)
        matches = list(compiled.finditer(result))
        if not matches:
            return
        result = compiled.sub(repl_func, result)
        rewrites += len(matches)

    def _enum_name(token: str) -> str:
        return normalize_token(token)

    requirement_patterns: list[tuple[str, str, str]] = [
        (
            r'Requirement\(\s*["\']health[-_]lte["\']\s*,\s*\{\s*["\']value["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "HealthLteRequirement",
            "{klass}({value})",
        ),
        (
            r'Requirement\(\s*["\']health[-_]gte["\']\s*,\s*\{\s*["\']value["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "HealthGteRequirement",
            "{klass}({value})",
        ),
        (
            r'Requirement\(\s*["\']health[-_]pct[-_]lte["\']\s*,\s*\{\s*["\']value["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "HealthPctLteRequirement",
            "{klass}({value})",
        ),
        (
            r'Requirement\(\s*["\']health[-_]pct[-_]gte["\']\s*,\s*\{\s*["\']value["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "HealthPctGteRequirement",
            "{klass}({value})",
        ),
    ]

    for pattern, klass, template in requirement_patterns:
        def _req_spec_repl(match: re.Match[str], *, _klass=klass, _template=template) -> str:
            imports_needed.add(_klass)
            return _template.format(klass=_klass, value=match.group(1).strip())

        _sub(pattern, _req_spec_repl)

    cost_patterns: list[tuple[str, str, str]] = [
        (
            r'Cost\(\s*["\']consume[-_]main[-_]hand["\']\s*,\s*\{\s*["\']amount["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "ConsumeMainHandCost",
            "{klass}(amount={value})",
        ),
        (
            r'Cost\(\s*["\']durability[-_]main[-_]hand["\']\s*,\s*\{\s*["\']amount["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "DurabilityMainHandCost",
            "{klass}(amount={value})",
        ),
        (
            r'Cost\(\s*["\']durability["\']\s*,\s*\{\s*["\']amount["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "DurabilityCost",
            "{klass}(amount={value})",
        ),
        (
            r'Cost\(\s*["\']mana["\']\s*,\s*\{\s*["\']amount["\']\s*:\s*([^}]+?)\s*\}\s*\)',
            "ManaCost",
            "{klass}(amount={value})",
        ),
    ]

    for pattern, klass, template in cost_patterns:
        def _cost_spec_repl(match: re.Match[str], *, _klass=klass, _template=template) -> str:
            imports_needed.add(_klass)
            return _template.format(klass=_klass, value=match.group(1).strip())

        _sub(pattern, _cost_spec_repl)

    enum_replacements: list[tuple[str, str, str]] = [
        (r'operation\s*=\s*["\']([A-Z_]+)["\']', "AttributeOperation", "operation"),
        (r'node_type\s*=\s*["\']([A-Z_]+)["\']', "ClassNodeType", "node_type"),
        (r'ability_trigger\s*=\s*["\']([A-Z_]+)["\']', "ClassAbilityTrigger", "ability_trigger"),
        (r'stat_scaling\s*=\s*["\']([A-Z_]+)["\']', "ClassScalingMode", "stat_scaling"),
        (r'attribute_scaling\s*=\s*["\']([A-Z_]+)["\']', "ClassScalingMode", "attribute_scaling"),
        (r'stat_curve\s*=\s*["\']([A-Z_]+)["\']', "ClassScalingCurve", "stat_curve"),
        (r'attribute_curve\s*=\s*["\']([A-Z_]+)["\']', "ClassScalingCurve", "attribute_curve"),
    ]

    for pattern, enum_name, key_name in enum_replacements:
        def _enum_repl(match: re.Match[str], *, _enum_name=enum_name, _key_name=key_name) -> str:
            token = _enum_name and normalize_token(match.group(1))
            imports_needed.add(_enum_name)
            return f"{_key_name}={_enum_name}.{token}"

        _sub(pattern, _enum_repl)

    def _resistance_repl(match: re.Match[str]) -> str:
        token = _enum_name(match.group(1))
        imports_needed.add("DamageType")
        return f".resistance(DamageType.{token}"

    _sub(r'\.resistance\(\s*["\']([A-Z_]+)["\']', _resistance_repl)

    def _consume_status_repl(match: re.Match[str]) -> str:
        token = _enum_name(match.group(1))
        imports_needed.add("PotionEffect")
        return f"consume_status.effect(PotionEffect.{token}"

    _sub(r'consume_status\.effect\(\s*["\']([A-Z_]+)["\']', _consume_status_repl)

    def _projectile_kind_repl(match: re.Match[str]) -> str:
        token = _enum_name(match.group(1))
        imports_needed.add("ProjectileKind")
        return f"kind=ProjectileKind.{token}"

    _sub(r'kind\s*=\s*["\']([A-Z_]+)["\']', _projectile_kind_repl)

    if "recipe.for_item(" in result and "recipe_ingredient" not in result:
        manual_flags.append("recipe.for_item keys may require recipe_ingredient.* conversion")
    if ".objective(" in result and "quest_objective" not in result:
        manual_flags.append("quest objective calls can be migrated to quest_objective.* helper")

    if imports_needed and "from dungeonsreborn_builder.v2 import" in result:
        import_line = "from dungeonsreborn_builder.v2 import " + ", ".join(sorted(imports_needed))
        if import_line not in result:
            lines = result.splitlines()
            insert_at = 0
            for idx, line in enumerate(lines):
                if line.startswith("from dungeonsreborn_builder.v2 import"):
                    insert_at = idx + 1
            lines.insert(insert_at, import_line)
            result = "\n".join(lines) + ("\n" if source.endswith("\n") else "")
            rewrites += 1

    return result, rewrites, manual_flags


def migrate_file(path: Path, *, backup: bool = True) -> MigrationResult:
    original = path.read_text(encoding="utf-8")
    migrated, rewrites, flags = migrate_source(original)
    changed = migrated != original
    if changed:
        if backup:
            backup_path = path.with_suffix(path.suffix + ".v1.bak")
            backup_path.write_text(original, encoding="utf-8")
        path.write_text(migrated, encoding="utf-8")
    return MigrationResult(path=str(path), changed=changed, rewrites=rewrites, manual_flags=flags)


def migrate_many(paths: Iterable[Path], *, backup: bool = True) -> MigrationSummary:
    summary = MigrationSummary()
    for path in paths:
        summary.files.append(migrate_file(path, backup=backup))
    return summary


def migrate_typed_file(path: Path, *, backup: bool = True) -> MigrationResult:
    original = path.read_text(encoding="utf-8")
    migrated, rewrites, flags = migrate_source_typed(original)
    changed = migrated != original
    if changed:
        if backup:
            backup_path = path.with_suffix(path.suffix + ".v2typed.bak")
            backup_path.write_text(original, encoding="utf-8")
        path.write_text(migrated, encoding="utf-8")
    return MigrationResult(path=str(path), changed=changed, rewrites=rewrites, manual_flags=flags)


def migrate_typed_many(paths: Iterable[Path], *, backup: bool = True) -> MigrationSummary:
    summary = MigrationSummary()
    for path in paths:
        summary.files.append(migrate_typed_file(path, backup=backup))
    return summary


def iter_python_files(path: Path) -> list[Path]:
    if path.is_file() and path.suffix == ".py":
        return [path]
    if path.is_dir():
        return sorted(child for child in path.rglob("*.py") if child.is_file())
    return []


__all__ = [
    "MigrationResult",
    "MigrationSummary",
    "migrate_source",
    "migrate_file",
    "migrate_many",
    "migrate_source_typed",
    "migrate_typed_file",
    "migrate_typed_many",
    "iter_python_files",
]
