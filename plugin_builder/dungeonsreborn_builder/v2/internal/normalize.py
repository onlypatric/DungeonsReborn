"""Normalization helpers for builder v2."""

from __future__ import annotations

import re
from difflib import get_close_matches
from typing import Iterable

_NON_ALNUM = re.compile(r"[^a-z0-9]+")


def snake_case(value: object) -> str:
    raw = str(value or "").strip().lower()
    if not raw:
        return ""
    token = _NON_ALNUM.sub("_", raw)
    token = re.sub(r"_+", "_", token)
    return token.strip("_")


def normalize_token(value: object) -> str:
    raw = str(value or "").strip().upper()
    raw = raw.replace("-", "_").replace(" ", "_")
    raw = re.sub(r"[^A-Z0-9_]+", "_", raw)
    raw = re.sub(r"_+", "_", raw)
    return raw.strip("_")


def suggest_token(options: Iterable[str], value: str, *, max_results: int = 3) -> list[str]:
    return get_close_matches(value, list(options), n=max_results)
