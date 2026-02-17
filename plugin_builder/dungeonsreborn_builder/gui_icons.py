"""GUI icon kit helpers for heads.yml."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable, List, Optional

from .heads import HeadSpec, HeadsDocument, HeadsExporter, head_spec

_ICON_KIT_CACHE: dict[str, HeadsDocument] = {}


class GuiIcon(str, Enum):
    NAV_LEFT = "NAV_LEFT"
    NAV_RIGHT = "NAV_RIGHT"
    NAV_UP = "NAV_UP"
    NAV_DOWN = "NAV_DOWN"
    NAV_BACK = "NAV_BACK"
    NAV_CLOSE = "NAV_CLOSE"
    NAV_CONFIRM = "NAV_CONFIRM"
    NAV_CANCEL = "NAV_CANCEL"
    NAV_REFRESH = "NAV_REFRESH"
    NAV_HOME = "NAV_HOME"
    NAV_SEARCH = "NAV_SEARCH"
    NAV_FILTER = "NAV_FILTER"
    NAV_SORT = "NAV_SORT"
    NAV_INFO = "NAV_INFO"
    NAV_HELP = "NAV_HELP"
    NAV_WARNING = "NAV_WARNING"
    NAV_ERROR = "NAV_ERROR"

    STATE_ON = "STATE_ON"
    STATE_OFF = "STATE_OFF"
    STATE_LOCKED = "STATE_LOCKED"
    STATE_UNLOCKED = "STATE_UNLOCKED"
    STATE_DISABLED = "STATE_DISABLED"
    STATE_ENABLED = "STATE_ENABLED"
    STATE_LOADING = "STATE_LOADING"
    STATE_DONE = "STATE_DONE"
    STATE_FAILED = "STATE_FAILED"
    STATE_NEW = "STATE_NEW"
    STATE_EDIT = "STATE_EDIT"
    STATE_DELETE = "STATE_DELETE"

    ICON_TOKENS = "ICON_TOKENS"
    ICON_TOKENS_COMPRESSED = "ICON_TOKENS_COMPRESSED"
    ICON_TOKENS_PALLET = "ICON_TOKENS_PALLET"
    ICON_XP = "ICON_XP"
    ICON_MANA = "ICON_MANA"
    ICON_HEART = "ICON_HEART"
    ICON_ARMOR = "ICON_ARMOR"
    ICON_SPEED = "ICON_SPEED"
    ICON_ATTACK = "ICON_ATTACK"
    ICON_DEFENSE = "ICON_DEFENSE"

    ICON_CLASSES = "ICON_CLASSES"
    ICON_SKILLS = "ICON_SKILLS"
    ICON_UPGRADES = "ICON_UPGRADES"
    ICON_ITEMS = "ICON_ITEMS"
    ICON_CRAFTING = "ICON_CRAFTING"
    ICON_SHOPS = "ICON_SHOPS"
    ICON_QUESTS = "ICON_QUESTS"
    ICON_PARTY = "ICON_PARTY"
    ICON_DUNGEONS = "ICON_DUNGEONS"
    ICON_MOBS = "ICON_MOBS"
    ICON_MINIONS = "ICON_MINIONS"
    ICON_ADVANCEMENTS = "ICON_ADVANCEMENTS"
    ICON_SETTINGS = "ICON_SETTINGS"
    ICON_LOCALE = "ICON_LOCALE"

    ICON_ADD = "ICON_ADD"
    ICON_REMOVE = "ICON_REMOVE"
    ICON_DUPLICATE = "ICON_DUPLICATE"
    ICON_SAVE = "ICON_SAVE"
    ICON_EDIT = "ICON_EDIT"
    ICON_CLEAR = "ICON_CLEAR"
    ICON_CONFIRM = "ICON_CONFIRM"
    ICON_CANCEL = "ICON_CANCEL"
    ICON_TEST = "ICON_TEST"
    ICON_PREVIEW = "ICON_PREVIEW"
    ICON_RESET = "ICON_RESET"


def _title_from_id(value: str) -> str:
    return value.replace("_", " ").title()


def gui_icon_head(icon: GuiIcon | str) -> str:
    return icon.value if isinstance(icon, GuiIcon) else str(icon)


def gui_icon_spec(icon: GuiIcon, name: Optional[str] = None) -> HeadSpec:
    head_id = gui_icon_head(icon)
    label = name or _title_from_id(head_id)
    return head_spec(head_id=head_id, name=label)


def gui_icon_spec_for_theme(icon: GuiIcon, theme: str, name: Optional[str] = None) -> HeadSpec:
    head_id = gui_icon_head(icon)
    label = name or _title_from_id(head_id)
    return head_spec(head_id=head_id, name=label, categories=[theme, "gui"])


def gui_icon_kit_document(icons: Optional[Iterable[GuiIcon]] = None) -> HeadsDocument:
    key = "default"
    if key in _ICON_KIT_CACHE:
        return _ICON_KIT_CACHE[key]
    doc = HeadsDocument()
    for icon in icons or list(GuiIcon):
        doc.add(gui_icon_spec(icon))
    _ICON_KIT_CACHE[key] = doc
    return doc


def gui_icon_kit_document_for_theme(theme: str, icons: Optional[Iterable[GuiIcon]] = None) -> HeadsDocument:
    normalized = theme.replace(" ", "_").lower()
    key = f"theme:{normalized}"
    if key in _ICON_KIT_CACHE:
        return _ICON_KIT_CACHE[key]
    doc = HeadsDocument()
    for icon in icons or list(GuiIcon):
        doc.add(gui_icon_spec_for_theme(icon, normalized))
    _ICON_KIT_CACHE[key] = doc
    return doc


@dataclass
class GuiIconKitExporter:
    exporter: HeadsExporter

    def write_gui_icon_kit(self, filename: str = "heads_gui.yml", icons: Optional[Iterable[GuiIcon]] = None) -> str:
        document = gui_icon_kit_document(icons)
        return self.exporter.write_heads(document, filename)

    def write_theme_kit(
        self,
        theme: str,
        filename: Optional[str] = None,
        icons: Optional[Iterable[GuiIcon]] = None,
    ) -> str:
        normalized = theme.replace(" ", "_").lower()
        target = filename or f"heads_gui_{normalized}.yml"
        document = gui_icon_kit_document_for_theme(normalized, icons)
        return self.exporter.write_heads(document, target)
