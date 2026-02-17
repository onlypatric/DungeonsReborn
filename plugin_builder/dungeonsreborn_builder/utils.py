"""Utility helpers for YAML output."""

from __future__ import annotations

from typing import Any, Iterable, List, Sequence, Tuple, Type, Union

import difflib
import json
import os
import time


def _format_scalar(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value)
    if text == "":
        return '""'
    needs_quotes = any(ch in text for ch in [":", "#", "{", "}", "[", "]", ",", "\"", "'", "\n", "\t", "\r"])
    if text.strip() != text:
        needs_quotes = True
    if needs_quotes:
        escaped = text.replace("\\", "\\\\").replace('"', "\\\"")
        return f'"{escaped}"'
    return text


def dump_yaml(data: Any, indent: int = 0) -> str:
    prefix = "  " * indent
    if isinstance(data, dict):
        lines = []
        for key, value in data.items():
            key_str = _format_scalar(key)
            if isinstance(value, (dict, list)):
                lines.append(f"{prefix}{key_str}:")
                lines.append(dump_yaml(value, indent + 1))
            elif isinstance(value, str) and "\n" in value:
                lines.append(f"{prefix}{key_str}: |-")
                for line in value.splitlines():
                    lines.append(f"{'  ' * (indent + 1)}{line}")
            else:
                lines.append(f"{prefix}{key_str}: {_format_scalar(value)}")
        return "\n".join(lines)
    if isinstance(data, list):
        lines = []
        for item in data:
            if isinstance(item, (dict, list)):
                lines.append(f"{prefix}-")
                lines.append(dump_yaml(item, indent + 1))
            elif isinstance(item, str) and "\n" in item:
                lines.append(f"{prefix}- |-")
                for line in item.splitlines():
                    lines.append(f"{'  ' * (indent + 1)}{line}")
            else:
                lines.append(f"{prefix}- {_format_scalar(item)}")
        return "\n".join(lines)
    return f"{prefix}{_format_scalar(data)}"


def write_yaml(path: str, data: Any) -> None:
    content = dump_yaml(data)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
        handle.write("\n")


def log_event(event: str, **fields: Any) -> None:
    if os.environ.get("DR_BUILDER_LOG_JSON", "").lower() not in {"1", "true", "yes"}:
        return
    payload = {"event": event, "ts": time.time(), **fields}
    print(json.dumps(payload, ensure_ascii=True))


def normalize_token(value: str) -> str:
    cleaned = []
    for ch in value.strip():
        if ch.isalnum():
            cleaned.append(ch.upper())
        else:
            cleaned.append("_")
    out = "".join(cleaned)
    while "__" in out:
        out = out.replace("__", "_")
    return out.strip("_")


def suggest_enum(enum_cls: Type[Any], token: str, limit: int = 3) -> list[str]:
    names = [str(name) for name in getattr(enum_cls, "__members__", {}).keys()]
    return difflib.get_close_matches(token, names, n=limit, cutoff=0.5)


def suggest_values(values: Iterable[str], token: str, limit: int = 3) -> list[str]:
    return difflib.get_close_matches(token, list(values), n=limit, cutoff=0.5)


def title_case_from_id(value: str) -> str:
    return value.replace("_", " ").strip().title()


def deep_merge(left: dict, right: dict) -> dict:
    for key, value in right.items():
        if isinstance(value, dict) and isinstance(left.get(key), dict):
            deep_merge(left[key], value)
        else:
            left[key] = value
    return left


def _parse_path(path: str) -> List[Union[str, int]]:
    tokens: List[Union[str, int]] = []
    for part in path.split("."):
        if "[" in part and part.endswith("]"):
            name, index_str = part[:-1].split("[", 1)
            if name:
                tokens.append(name)
            tokens.append(int(index_str))
        else:
            tokens.append(part)
    return tokens


def set_path(target: dict, path: str, value: Any) -> None:
    tokens = _parse_path(path)
    current: Any = target
    for idx, token in enumerate(tokens[:-1]):
        next_token = tokens[idx + 1]
        if isinstance(token, str):
            if token not in current or current[token] is None:
                current[token] = [] if isinstance(next_token, int) else {}
            current = current[token]
            continue
        if not isinstance(current, list):
            current_list: List[Any] = []
            current = current_list
        while len(current) <= token:
            current.append({} if isinstance(next_token, str) else [])
        current = current[token]
    last = tokens[-1]
    if isinstance(last, int):
        if not isinstance(current, list):
            current_list = []
            current = current_list
        while len(current) <= last:
            current.append(None)
        current[last] = value
    else:
        current[last] = value


def apply_overrides(
    data: dict,
    merges: Sequence[dict],
    sets: Sequence[Tuple[str, Any]],
) -> dict:
    for mapping in merges:
        deep_merge(data, mapping)
    for path, value in sets:
        set_path(data, path, value)
    return data
