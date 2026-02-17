"""Fluent DSL builder helpers."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional

from .vanilla import EnumValue

_IDENT_RE = re.compile(r"^[A-Za-z0-9_./:-]+$")


@dataclass(frozen=True)
class DslValue:
    """Explicit DSL value wrapper when auto-formatting is not enough."""

    text: str

    @staticmethod
    def raw(text: str) -> "DslValue":
        return DslValue(text=text)


def _format_value(value: Any) -> str:
    if isinstance(value, DslValue):
        return value.text
    if isinstance(value, EnumValue):
        return str(value)
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, str):
        if _IDENT_RE.match(value):
            return value
        escaped = value.replace('"', '\\"')
        return f"\"{escaped}\""
    if isinstance(value, (list, tuple, dict)):
        return json.dumps(value, separators=(",", ":"))
    return str(value)


def _format_args(args: Dict[str, Any]) -> str:
    if not args:
        return ""
    parts = []
    for key, value in args.items():
        if value is None:
            continue
        parts.append(f"{key}={_format_value(value)}")
    return " " + " ".join(parts) if parts else ""


@dataclass
class DslBlock:
    name: str
    args: Dict[str, Any] = field(default_factory=dict)
    entries: List[Any] = field(default_factory=list)

    def stmt(self, name: str, **kwargs: Any) -> "DslBlock":
        self.entries.append((name, kwargs))
        return self

    def block(self, name: str, **kwargs: Any) -> "DslBlock":
        child = DslBlock(name=name, args=kwargs)
        self.entries.append(child)
        return child

    def extend(self, entries: Iterable[Any]) -> "DslBlock":
        self.entries.extend(entries)
        return self

    def _render(self, indent: int = 0) -> List[str]:
        prefix = "  " * indent
        header = f"{prefix}{self.name}{_format_args(self.args)} {{"
        lines = [header]
        for entry in self.entries:
            if isinstance(entry, DslBlock):
                lines.extend(entry._render(indent + 1))
                continue
            name, kwargs = entry
            line = f"{'  ' * (indent + 1)}{name}{_format_args(kwargs)}"
            lines.append(line)
        lines.append(f"{prefix}}}")
        return lines


@dataclass
class DslBuilder:
    blocks: List[DslBlock] = field(default_factory=list)

    def on_cast(self) -> DslBlock:
        block = DslBlock(name="on_cast")
        self.blocks.append(block)
        return block

    def on_tick(self, ticks: int, every: int) -> DslBlock:
        block = DslBlock(name="on_tick", args={"ticks": ticks, "every": every})
        self.blocks.append(block)
        return block

    def repeat(self, times: int, every: int) -> DslBlock:
        block = DslBlock(name="repeat", args={"times": times, "every": every})
        self.blocks.append(block)
        return block

    def for_each_target(self, **kwargs: Any) -> DslBlock:
        block = DslBlock(name="for_each_target", args=kwargs)
        self.blocks.append(block)
        return block

    def add(self, block: DslBlock) -> "DslBuilder":
        self.blocks.append(block)
        return self

    def build(self) -> str:
        lines: List[str] = []
        for block in self.blocks:
            lines.extend(block._render())
        return "\n".join(lines)
