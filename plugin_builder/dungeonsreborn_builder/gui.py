"""GUI preview helpers for builder outputs."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class GuiTileSpec:
    head: Optional[str] = None
    icon: Optional[str] = None
    title: Optional[str] = None
    title_key: Optional[str] = None
    description: Optional[str] = None
    description_key: Optional[str] = None
    summary: List[str] = field(default_factory=list)
    summary_keys: List[str] = field(default_factory=list)
    tags: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, object]:
        data: Dict[str, object] = {}
        if self.head:
            data["head"] = self.head
        if self.icon:
            data["icon"] = self.icon
        if self.title:
            data["title"] = self.title
        if self.title_key:
            data["titleKey"] = self.title_key
        if self.description:
            data["description"] = self.description
        if self.description_key:
            data["descriptionKey"] = self.description_key
        if self.summary:
            data["summary"] = list(self.summary)
        if self.summary_keys:
            data["summaryKeys"] = list(self.summary_keys)
        if self.tags:
            data["tags"] = list(self.tags)
        return data
