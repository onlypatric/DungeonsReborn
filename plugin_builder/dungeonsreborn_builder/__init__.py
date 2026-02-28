"""DungeonsReborn builder package (v2-only)."""

from __future__ import annotations

from . import v2

__all__ = ["v2"]


def __getattr__(name: str):
    raise AttributeError(
        "dungeonsreborn_builder V1 API has been removed. "
        "Use `from dungeonsreborn_builder.v2 import ...` instead. "
        f"Missing symbol: {name}"
    )
