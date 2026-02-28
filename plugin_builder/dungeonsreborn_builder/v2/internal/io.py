"""IO primitives for builder v2."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import yaml


def write_yaml(path: str | os.PathLike[str], payload: Any) -> str:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        yaml.safe_dump(payload, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    return str(target)
