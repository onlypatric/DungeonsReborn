"""Animation builder for DSL timelines."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, List, Optional, Union


@dataclass(frozen=True)
class VFXPreset:
    lines: List[str]

    def __add__(self, other: "VFXPreset") -> "VFXPreset":
        return VFXPreset(self.lines + other.lines)


@dataclass(frozen=True)
class VFXParams:
    size: Optional[float] = None
    color: Optional[str] = None
    density: Optional[int] = None


def _normalize_lines(value: Union[str, VFXPreset, Iterable[str]]) -> List[str]:
    if isinstance(value, VFXPreset):
        return list(value.lines)
    if isinstance(value, str):
        return [value]
    return list(value)


def _format_color(color: str) -> str:
    if color.startswith(("\"", "'")):
        return color
    if color.startswith("#"):
        return f"\"{color}\""
    return color


def _apply_params_line(line: str, params: Optional[VFXParams]) -> str:
    if not params:
        return line
    parts = [line]
    if params.color and "color=" not in line:
        parts.append(f"color={_format_color(params.color)}")
    if params.size is not None and "size=" not in line:
        parts.append(f"size={params.size}")
    if params.density is not None and "count=" not in line:
        parts.append(f"count={params.density}")
    return " ".join(parts)


def apply_params(preset: VFXPreset, params: VFXParams) -> VFXPreset:
    return VFXPreset([_apply_params_line(line, params) for line in preset.lines])


class AnimationBuilder:
    def __init__(self) -> None:
        self._blocks: List[str] = []
        self._params: Optional[VFXParams] = None

    def add(self, value: Union[str, VFXPreset, Iterable[str]]) -> "AnimationBuilder":
        lines = _normalize_lines(value)
        self._blocks.extend([_apply_params_line(line, self._params) for line in lines])
        return self

    def burst(self, value: Union[str, VFXPreset, Iterable[str]]) -> "AnimationBuilder":
        return self.add(value)

    def pack(self, params: VFXParams) -> "AnimationBuilder":
        self._params = params
        return self

    def schedule(self, delay_ticks: int, value: Union[str, VFXPreset, Iterable[str]]) -> "AnimationBuilder":
        lines = [_apply_params_line(line, self._params) for line in _normalize_lines(value)]
        block = ["delay ticks=%d {" % delay_ticks]
        block.extend([f"  {line}" for line in lines])
        block.append("}")
        self._blocks.extend(block)
        return self

    def loop(self, times: int, every: int, value: Union[str, VFXPreset, Iterable[str]]) -> "AnimationBuilder":
        lines = [_apply_params_line(line, self._params) for line in _normalize_lines(value)]
        block = ["repeat times=%d every=%d {" % (times, every)]
        block.extend([f"  {line}" for line in lines])
        block.append("}")
        self._blocks.extend(block)
        return self

    def to_preset(self) -> VFXPreset:
        return VFXPreset(list(self._blocks))

    def build_script(self) -> str:
        body = "\n  ".join(self._blocks) if self._blocks else ""
        return f"on_cast {{\n  {body}\n}}"
