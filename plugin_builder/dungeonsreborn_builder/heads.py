"""Heads registry builder (heads.yml)."""

from __future__ import annotations

from dataclasses import dataclass, field
import re
from typing import Any, Dict, Iterable, List, Optional

from .base import ExporterBase, snake_case


_HEAD_ID_PATTERN = re.compile(r"^[a-z0-9_.:-]+$")


def normalize_head_id(value: str) -> str:
    normalized = value.strip().lower()
    if not normalized:
        raise ValueError("head id is empty")
    if not _HEAD_ID_PATTERN.match(normalized):
        raise ValueError(f"Invalid head id: {value}")
    return normalized


@dataclass
class HeadProfileSpec:
    name: Optional[str] = None
    uuid: Optional[str] = None
    texture: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.name:
            data["name"] = self.name
        if self.uuid:
            data["uuid"] = self.uuid
        if self.texture:
            data["texture"] = self.texture
        return data


@dataclass
class HeadSpec:
    head_id: str
    name: Optional[str] = None
    texture: Optional[str] = None
    owner: Optional[str] = None
    profile: Optional[HeadProfileSpec] = None
    tags: List[str] = field(default_factory=list)
    categories: List[str] = field(default_factory=list)

    def normalized_id(self) -> str:
        return snake_case(self.head_id)

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {}
        if self.name:
            data["name"] = self.name
        if self.owner:
            data["owner"] = self.owner
        if self.texture:
            data["texture"] = self.texture
        if self.profile is not None:
            profile = self.profile.to_dict()
            if profile:
                data["profile"] = profile
        if self.tags:
            data["tags"] = list(self.tags)
        if self.categories:
            data["categories"] = list(self.categories)
        return data


@dataclass
class HeadsDocument:
    heads: Dict[str, HeadSpec] = field(default_factory=dict)

    def add(self, spec: HeadSpec) -> "HeadsDocument":
        self.heads[spec.normalized_id()] = spec
        return self

    def to_dict(self) -> Dict[str, Any]:
        return {"heads": {key: spec.to_dict() for key, spec in self.heads.items()}}


def head_spec(
    head_id: str,
    name: Optional[str] = None,
    texture: Optional[str] = None,
    owner: Optional[str] = None,
    profile: Optional[HeadProfileSpec] = None,
    tags: Optional[Iterable[str]] = None,
    categories: Optional[Iterable[str]] = None,
) -> HeadSpec:
    return HeadSpec(
        head_id=head_id,
        name=name,
        texture=texture,
        owner=owner,
        profile=profile,
        tags=list(tags or []),
        categories=list(categories or []),
    )


def heads_document(*specs: HeadSpec) -> HeadsDocument:
    doc = HeadsDocument()
    for spec in specs:
        doc.add(spec)
    return doc


class HeadsExporter(ExporterBase):
    _payload_cache: Dict[str, Dict[str, Any]] = {}

    def write_heads(self, document: HeadsDocument, filename: str = "heads.yml") -> str:
        payload = document.to_dict()
        path = f"{self.output_dir}/{filename}"
        cached = self._payload_cache.get(path)
        if cached == payload:
            return path
        self._payload_cache[path] = payload
        return self.write_yaml(filename, payload)
