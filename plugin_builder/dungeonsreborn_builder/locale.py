"""Locale key registry and placeholder exporters."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple

from .base import ExporterBase


KEY_FIELDS = {
    "nameKey",
    "titleKey",
    "descriptionKey",
    "subtitleKey",
    "loreKeys",
    "summaryKeys",
    "rarityLineKey",
    "flavorKey",
    "displayNameKey",
}

LOCALE_PREFIXES = ("key:", "i18n:", "locale:")


@dataclass
class LocaleKeyRegistry:
    keys: Dict[str, Set[str]] = field(default_factory=dict)

    def add(self, key: str, source: Optional[str] = None) -> str:
        if not key:
            return key
        sources = self.keys.setdefault(key, set())
        if source:
            sources.add(source)
        return key

    def extend(self, keys: Iterable[str], source: Optional[str] = None) -> None:
        for key in keys:
            self.add(key, source)

    def all_keys(self) -> List[str]:
        return sorted(self.keys.keys())

    def duplicates(self) -> List[Tuple[str, List[str]]]:
        duplicates = []
        for key, sources in self.keys.items():
            if len(sources) > 1:
                duplicates.append((key, sorted(sources)))
        return duplicates


@dataclass
class LocaleKeyEmitter:
    prefix: str = ""
    registry: Optional[LocaleKeyRegistry] = None

    def key(self, *parts: str) -> str:
        raw = ".".join(part.strip(".") for part in parts if part)
        key = f"{self.prefix}.{raw}".strip(".")
        if self.registry is not None:
            self.registry.add(key, source="emitter")
        return key

    def name_key(self, entity_id: str) -> str:
        return self.key(entity_id, "name")

    def title_key(self, entity_id: str) -> str:
        return self.key(entity_id, "title")

    def description_key(self, entity_id: str, index: int) -> str:
        return self.key(entity_id, "description", str(index))

    def description_keys(self, entity_id: str, count: int) -> List[str]:
        return [self.description_key(entity_id, idx) for idx in range(1, count + 1)]

    def lore_key(self, entity_id: str, index: int) -> str:
        return self.key(entity_id, "lore", str(index))

    def lore_keys(self, entity_id: str, count: int) -> List[str]:
        return [self.lore_key(entity_id, idx) for idx in range(1, count + 1)]


def locale_value(key: str) -> str:
    return f"locale:{key}"


def is_locale_value(value: Optional[str]) -> bool:
    if value is None:
        return False
    lowered = value.lower().strip()
    return any(lowered.startswith(prefix) for prefix in LOCALE_PREFIXES)


def strip_locale_prefix(value: str) -> str:
    lowered = value.lower()
    for prefix in LOCALE_PREFIXES:
        if lowered.startswith(prefix):
            return value[len(prefix):].strip()
    return value


def placeholder_for_key(key: str, placeholder_prefix: str = "TODO") -> str:
    human = key.replace("_", " ").replace(".", " ").strip()
    if human:
        human = human[0].upper() + human[1:]
    return f"{placeholder_prefix}: {human}".strip()


def register_locale_entry(
    entries: Dict[str, str],
    key: str,
    value: Optional[str] = None,
    placeholder_prefix: str = "TODO",
) -> None:
    if not key:
        return
    if key in entries:
        return
    if value is not None and value != "":
        entries[key] = value
        return
    entries[key] = placeholder_for_key(key, placeholder_prefix=placeholder_prefix)


def ensure_locale_value(
    value: Optional[str],
    key: str,
    entries: Dict[str, str],
    placeholder_prefix: str = "TODO",
) -> Optional[str]:
    if value is None:
        return None
    if is_locale_value(value):
        register_locale_entry(entries, strip_locale_prefix(value), None, placeholder_prefix)
        return value
    register_locale_entry(entries, key, value, placeholder_prefix)
    return locale_value(key)


def ensure_locale_list(
    values: Sequence[str],
    key_base: str,
    entries: Dict[str, str],
    placeholder_prefix: str = "TODO",
) -> List[str]:
    out: List[str] = []
    for idx, value in enumerate(values, start=1):
        key = f"{key_base}.{idx}"
        out.append(ensure_locale_value(value, key, entries, placeholder_prefix) or "")
    return out


def collect_locale_keys(data: Any, registry: Optional[LocaleKeyRegistry] = None) -> List[str]:
    collector = registry or LocaleKeyRegistry()
    _collect_locale_keys(data, collector)
    return collector.all_keys()


def _collect_locale_keys(data: Any, registry: LocaleKeyRegistry) -> None:
    if isinstance(data, Mapping):
        for key, value in data.items():
            if key in KEY_FIELDS:
                if isinstance(value, str):
                    registry.add(value)
                elif isinstance(value, Sequence):
                    for entry in value:
                        if isinstance(entry, str):
                            registry.add(entry)
            _collect_locale_keys(value, registry)
        return
    if isinstance(data, Sequence) and not isinstance(data, (str, bytes)):
        for entry in data:
            _collect_locale_keys(entry, registry)
        return
    if isinstance(data, str):
        lowered = data.lower().strip()
        if any(lowered.startswith(prefix) for prefix in LOCALE_PREFIXES):
            registry.add(strip_locale_prefix(data))


def build_placeholders(keys: Iterable[str], placeholder_prefix: str = "TODO") -> Dict[str, str]:
    values: Dict[str, str] = {}
    for key in keys:
        human = key.replace("_", " ").replace(".", " ").strip()
        if human:
            human = human[0].upper() + human[1:]
        values[key] = f"{placeholder_prefix}: {human}".strip()
    return values


class LocaleExporter(ExporterBase):
    def write_placeholders(
        self,
        keys: Iterable[str],
        filename: str,
        placeholder_prefix: str = "TODO",
    ) -> str:
        data = build_placeholders(keys, placeholder_prefix=placeholder_prefix)
        return self.write_yaml(filename, data)
