"""High-level pack export helpers."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Any, Iterable, List, Optional

from .effects import Ability, AbilityBuilder, EffectsExporter
from .gui_icons import GuiIcon, GuiIconKitExporter, gui_icon_head
from .heads import HeadsExporter
from .items import ItemBuilder
from .items import GuiPreviewSpec
from .locale import (
    LocaleKeyEmitter,
    ensure_locale_list,
    ensure_locale_value,
    is_locale_value,
    register_locale_entry,
    strip_locale_prefix,
)
from .mobs import MobBuilder, MobExporter, MobGuiPreviewSpec
from .quests import QuestBuilder, QuestExporter, QuestGuiPreviewSpec, QuestSpec
from .shops import ShopBuilder, ShopExporter, ShopGuiPreviewSpec, ShopSpec
from .utils import log_event, title_case_from_id, write_yaml
from .validation import Severity, ValidationReport, render_report, validate_pack


def _flatten(objects: Iterable[Any]) -> List[Any]:
    flattened: List[Any] = []
    for entry in objects:
        if isinstance(entry, (list, tuple, set)):
            flattened.extend(_flatten(entry))
        else:
            flattened.append(entry)
    return flattened


_DEFAULT_DOMAIN_ICON = {
    "items": GuiIcon.ICON_ITEMS,
    "mobs": GuiIcon.ICON_MOBS,
    "quests": GuiIcon.ICON_QUESTS,
    "shops": GuiIcon.ICON_SHOPS,
    "abilities": GuiIcon.ICON_SKILLS,
}


def _locale_key_from_value(value: Optional[str]) -> Optional[str]:
    if value and is_locale_value(value):
        return strip_locale_prefix(value)
    return None


def _localize_item(
    item: ItemBuilder,
    emitter: LocaleKeyEmitter,
    entries: dict[str, str],
    placeholder_prefix: str,
    apply_gui: bool = True,
    apply_locale: bool = True,
) -> None:
    item_id = item._id or ""
    display = item._display
    if display is not None and apply_locale:
        if display.name_key:
            register_locale_entry(entries, display.name_key, display.name, placeholder_prefix)
        elif display.name:
            key = emitter.key("items", item_id, "name")
            display.name = ensure_locale_value(display.name, key, entries, placeholder_prefix)
        if display.lore_keys:
            for key in display.lore_keys:
                register_locale_entry(entries, key, None, placeholder_prefix)
        elif display.lore:
            key_base = emitter.key("items", item_id, "lore")
            display.lore = ensure_locale_list(display.lore, key_base, entries, placeholder_prefix)
        if display.subtitle_key:
            register_locale_entry(entries, display.subtitle_key, display.subtitle, placeholder_prefix)
        elif display.subtitle:
            key = emitter.key("items", item_id, "subtitle")
            display.subtitle = ensure_locale_value(display.subtitle, key, entries, placeholder_prefix)
        if display.description_key:
            register_locale_entry(entries, display.description_key, display.description, placeholder_prefix)
        elif display.description:
            key = emitter.key("items", item_id, "description")
            display.description = ensure_locale_value(display.description, key, entries, placeholder_prefix)
        if display.rarity_line_key:
            register_locale_entry(entries, display.rarity_line_key, display.rarity_line, placeholder_prefix)
        elif display.rarity_line:
            key = emitter.key("items", item_id, "rarity_line")
            display.rarity_line = ensure_locale_value(display.rarity_line, key, entries, placeholder_prefix)
        if display.flavor_key:
            register_locale_entry(entries, display.flavor_key, display.flavor, placeholder_prefix)
        elif display.flavor:
            key = emitter.key("items", item_id, "flavor")
            display.flavor = ensure_locale_value(display.flavor, key, entries, placeholder_prefix)
    meta = item._meta
    if meta is not None and apply_locale:
        display_name_key = getattr(meta, "display_name_key", None)
        display_name = getattr(meta, "display_name", None)
        lore_keys = getattr(meta, "lore_keys", None)
        lore = getattr(meta, "lore", None)
        if display_name_key:
            register_locale_entry(entries, display_name_key, display_name, placeholder_prefix)
        elif display_name:
            key = emitter.key("items", item_id, "meta", "name")
            meta.display_name = ensure_locale_value(display_name, key, entries, placeholder_prefix)
        if lore_keys:
            for key in lore_keys:
                register_locale_entry(entries, key, None, placeholder_prefix)
        elif lore:
            key_base = emitter.key("items", item_id, "meta", "lore")
            meta.lore = ensure_locale_list(lore, key_base, entries, placeholder_prefix)
    if not apply_gui:
        return
    preview = item._gui_preview
    name_key = None
    if display is not None:
        name_key = display.name_key or _locale_key_from_value(display.name)
    if preview is None:
        preview = GuiPreviewSpec()
    if not preview.title_key and name_key:
        preview.title_key = name_key
    if not preview.head and not preview.icon:
        preview.head = gui_icon_head(_DEFAULT_DOMAIN_ICON["items"])
    item._gui_preview = preview


def _localize_mob(
    mob: MobBuilder,
    emitter: LocaleKeyEmitter,
    entries: dict[str, str],
    placeholder_prefix: str,
    apply_gui: bool = True,
    apply_locale: bool = True,
) -> None:
    mob_id = mob._id or ""
    if mob._name and mob._name.startswith("raw:"):
        mob._name = mob._name[len("raw:") :]
        apply_locale = False
    if mob._name and apply_locale:
        key = emitter.key("mobs", mob_id, "name")
        mob._name = ensure_locale_value(mob._name, key, entries, placeholder_prefix)
    if not apply_gui:
        return
    preview = mob._gui_preview
    description_key = _locale_key_from_value(mob._name)
    if preview is None:
        preview = MobGuiPreviewSpec()
    if not preview.description_key and description_key:
        preview.description_key = description_key
    if not preview.head and not preview.icon:
        preview.head = gui_icon_head(_DEFAULT_DOMAIN_ICON["mobs"])
    mob._gui_preview = preview


def _localize_quest(
    quest: QuestSpec,
    emitter: LocaleKeyEmitter,
    entries: dict[str, str],
    placeholder_prefix: str,
    apply_gui: bool = True,
    apply_locale: bool = True,
) -> None:
    quest_id = quest.quest_id
    if quest.name and apply_locale:
        key = emitter.key("quests", quest_id, "name")
        quest.name = ensure_locale_value(quest.name, key, entries, placeholder_prefix) or quest.name
    if quest.description and apply_locale:
        key_base = emitter.key("quests", quest_id, "description")
        quest.description = ensure_locale_list(quest.description, key_base, entries, placeholder_prefix)
    if not apply_gui:
        return
    preview = quest.gui
    title_key = _locale_key_from_value(quest.name)
    description_key = None
    if quest.description:
        description_key = _locale_key_from_value(quest.description[0])
    if preview is None:
        preview = QuestGuiPreviewSpec()
    if not preview.title_key and title_key:
        preview.title_key = title_key
    if not preview.description_key and description_key:
        preview.description_key = description_key
    if not preview.head and not preview.icon:
        preview.head = gui_icon_head(_DEFAULT_DOMAIN_ICON["quests"])
    quest.gui = preview


def _localize_shop(
    shop: ShopSpec,
    emitter: LocaleKeyEmitter,
    entries: dict[str, str],
    placeholder_prefix: str,
    apply_gui: bool = True,
    apply_locale: bool = True,
) -> None:
    shop_id = shop.shop_id
    if shop.title and apply_locale:
        key = emitter.key("shops", shop_id, "title")
        shop.title = ensure_locale_value(shop.title, key, entries, placeholder_prefix) or shop.title
    if apply_locale:
        for idx, trade in enumerate(shop.trades, start=1):
            if trade.preview_lore:
                key_base = emitter.key("shops", shop_id, "trades", str(idx), "preview")
                trade.preview_lore = ensure_locale_list(trade.preview_lore, key_base, entries, placeholder_prefix)
    if not apply_gui:
        return
    preview = shop.gui
    title_key = _locale_key_from_value(shop.title)
    if preview is None:
        preview = ShopGuiPreviewSpec()
    if not preview.title_key and title_key:
        preview.title_key = title_key
    if not preview.head and not preview.icon:
        preview.head = gui_icon_head(_DEFAULT_DOMAIN_ICON["shops"])
    shop.gui = preview


def _localize_ability(
    ability: Ability,
    emitter: LocaleKeyEmitter,
    entries: dict[str, str],
    placeholder_prefix: str,
    apply_locale: bool = True,
) -> None:
    if ability.name and apply_locale:
        key = emitter.key("abilities", ability.ability_id, "name")
        ability.name = ensure_locale_value(ability.name, key, entries, placeholder_prefix) or ability.name


@dataclass
class ContentPack:
    items: List[ItemBuilder] = field(default_factory=list)
    mobs: List[MobBuilder] = field(default_factory=list)
    quests: List[QuestSpec] = field(default_factory=list)
    shops: List[ShopSpec] = field(default_factory=list)
    abilities: List[Ability] = field(default_factory=list)
    safe_defaults: bool = False
    warnings: List[str] = field(default_factory=list)
    emit_locales: bool = True
    emit_item_locales: bool = True
    locale_prefix: str = "builder"
    locale_placeholder_prefix: str = "TODO"
    emit_gui_icons: bool = True
    gui_theme: str = "fantasy"
    emit_gui_previews: bool = True
    export_layout: str = "domain"
    build_profile: str = "dev"
    last_summary: dict[str, Any] = field(default_factory=dict)

    def add(self, *objects: Any) -> "ContentPack":
        for entry in _flatten(objects):
            if isinstance(entry, ItemBuilder):
                self.items.append(entry)
            elif isinstance(entry, MobBuilder):
                self.mobs.append(entry)
            elif isinstance(entry, QuestBuilder):
                self.quests.append(entry.build_spec())
            elif isinstance(entry, QuestSpec):
                self.quests.append(entry)
            elif isinstance(entry, ShopBuilder):
                self.shops.append(entry.build_spec())
            elif isinstance(entry, ShopSpec):
                self.shops.append(entry)
            elif isinstance(entry, AbilityBuilder):
                self.abilities.append(entry.build())
            elif isinstance(entry, Ability):
                self.abilities.append(entry)
        return self

    def with_safe_defaults(self, enabled: bool = True) -> "ContentPack":
        self.safe_defaults = enabled
        return self

    def with_locale_emission(
        self,
        enabled: bool = True,
        prefix: Optional[str] = None,
        placeholder_prefix: Optional[str] = None,
    ) -> "ContentPack":
        self.emit_locales = enabled
        if prefix is not None:
            self.locale_prefix = prefix
        if placeholder_prefix is not None:
            self.locale_placeholder_prefix = placeholder_prefix
        return self

    def with_gui_icons(self, enabled: bool = True, theme: Optional[str] = None) -> "ContentPack":
        self.emit_gui_icons = enabled
        if theme is not None:
            self.gui_theme = theme
        return self

    def with_gui_previews(self, enabled: bool = True) -> "ContentPack":
        self.emit_gui_previews = enabled
        return self

    def with_layout(self, layout: str) -> "ContentPack":
        self.export_layout = layout
        return self

    def with_profile(self, profile: str) -> "ContentPack":
        self.build_profile = profile
        return self

    def _apply_safe_defaults(self) -> None:
        for item in self.items:
            if item._name is None and item._id:
                item.name(title_case_from_id(item._id))
        for mob in self.mobs:
            if mob._name is None and mob._id:
                mob.name(title_case_from_id(mob._id))
            if mob._attacks and "MAX_HEALTH" not in mob._stats:
                mob.stats(health=20, damage=4)
        for quest in self.quests:
            if not quest.name and quest.quest_id:
                quest.name = title_case_from_id(quest.quest_id)
        for shop in self.shops:
            if not shop.title and shop.shop_id:
                shop.title = title_case_from_id(shop.shop_id)

    def _apply_locale_and_gui(self) -> dict[str, str]:
        entries: dict[str, str] = {}
        if not self.emit_locales and not self.emit_gui_previews:
            return entries
        emitter = LocaleKeyEmitter(prefix=self.locale_prefix if self.emit_locales else "")
        apply_locale = self.emit_locales
        apply_item_locale = self.emit_locales and self.emit_item_locales
        for ability in self.abilities:
            _localize_ability(
                ability,
                emitter,
                entries,
                self.locale_placeholder_prefix,
                apply_locale=apply_locale,
            )
        for item in self.items:
            _localize_item(
                item,
                emitter,
                entries,
                self.locale_placeholder_prefix,
                apply_gui=self.emit_gui_previews,
                apply_locale=apply_item_locale,
            )
        for mob in self.mobs:
            _localize_mob(
                mob,
                emitter,
                entries,
                self.locale_placeholder_prefix,
                apply_gui=self.emit_gui_previews,
                apply_locale=apply_locale,
            )
        for quest in self.quests:
            _localize_quest(
                quest,
                emitter,
                entries,
                self.locale_placeholder_prefix,
                apply_gui=self.emit_gui_previews,
                apply_locale=apply_locale,
            )
        for shop in self.shops:
            _localize_shop(
                shop,
                emitter,
                entries,
                self.locale_placeholder_prefix,
                apply_gui=self.emit_gui_previews,
                apply_locale=apply_locale,
            )
        return entries

    def export(self, output_dir: str) -> List[str]:
        if self.safe_defaults:
            self._apply_safe_defaults()
        locale_entries = self._apply_locale_and_gui()
        report = self.validate()
        if self.build_profile == "prod" and report.has_errors():
            raise ValueError(render_report(report))
        paths: List[str] = []
        effects_dir, items_dir, mobs_dir, quests_dir, shops_dir = self._resolve_output_dirs(output_dir)
        for path in (effects_dir, items_dir, mobs_dir, quests_dir, shops_dir):
            if path:
                os.makedirs(path, exist_ok=True)
        if self.emit_locales:
            locales_dir = os.path.join(output_dir, "locales", "en")
            os.makedirs(locales_dir, exist_ok=True)
            from .locale import LocaleExporter

            exporter = LocaleExporter(locales_dir)
            paths.append(exporter.write_yaml("builder.yml", locale_entries))
        if self.emit_gui_icons:
            icons_exporter = GuiIconKitExporter(HeadsExporter(output_dir))
            paths.append(icons_exporter.write_theme_kit(self.gui_theme))

        if self.abilities:
            exporter = EffectsExporter(effects_dir)
            for ability in self.abilities:
                paths.append(exporter.write_ability(ability, filename=self._filename_for("ability", ability.ability_id)))
                if getattr(ability, "override_warnings", None):
                    self.warnings.extend(ability.override_warnings)
        if self.items:
            exporter = ItemBuilderExporter(items_dir)
            for item in self.items:
                paths.append(exporter.write_item(item, filename=self._filename_for("item", item._id or "item")))
                self.warnings.extend(item.override_warnings())
        if self.mobs:
            exporter = MobExporter(mobs_dir)
            for mob in self.mobs:
                paths.append(exporter.write_mob(mob, filename=self._filename_for("mob", mob._id or "mob")))
                self.warnings.extend(mob.override_warnings())
        if self.quests:
            exporter = QuestExporter(quests_dir)
            for quest in self.quests:
                paths.append(
                    exporter.write_yaml(
                        self._filename_for("quest", quest.quest_id),
                        {"quests": {quest.quest_id: quest.to_dict()}},
                    )
                )
                if quest.override_warnings:
                    self.warnings.extend(quest.override_warnings)
        if self.shops:
            exporter = ShopExporter(shops_dir)
            for shop in self.shops:
                paths.append(
                    exporter.write_yaml(
                        self._filename_for("shop", shop.shop_id),
                        {"shops": {shop.shop_id: shop.to_dict()}},
                    )
                )
                if shop.override_warnings:
                    self.warnings.extend(shop.override_warnings)

        pack_manifest = {
            "counts": {
                "abilities": len(self.abilities),
                "items": len(self.items),
                "mobs": len(self.mobs),
                "quests": len(self.quests),
                "shops": len(self.shops),
            }
        }
        if self.warnings:
            pack_manifest["warnings"] = list(self.warnings)
        summary = self._summary_from_report(report)
        pack_manifest["summary"] = summary
        manifest_path = os.path.join(output_dir, "pack.yml")
        write_yaml(manifest_path, pack_manifest)
        paths.append(manifest_path)
        self.last_summary = summary
        log_event(
            "builder.export",
            output=output_dir,
            counts=summary.get("counts", {}),
            warnings=len(summary.get("warnings", [])),
            errors=len(summary.get("errors", [])),
            layout=self.export_layout,
            profile=self.build_profile,
        )
        return paths

    def validate(self) -> ValidationReport:
        return validate_pack(self.items, self.mobs, self.quests, self.shops, self.abilities)

    def dry_run(self) -> str:
        report = self.validate()
        return render_report(report)

    def preview_export(self) -> dict[str, Any]:
        if self.safe_defaults:
            self._apply_safe_defaults()
        self._apply_locale_and_gui()
        report = self.validate()
        summary = self._summary_from_report(report)
        self.last_summary = summary
        return {
            "counts": summary.get("counts", {}),
            "warnings": summary.get("warnings", []),
            "errors": summary.get("errors", []),
        }

    def _resolve_output_dirs(self, output_dir: str) -> tuple[str, str, str, str, str]:
        if self.export_layout == "flat":
            base = output_dir
            return (base, base, base, base, base)
        return (
            os.path.join(output_dir, "effects", "abilities"),
            os.path.join(output_dir, "effects", "items"),
            os.path.join(output_dir, "mobs"),
            os.path.join(output_dir, "quests"),
            os.path.join(output_dir, "shops"),
        )

    def _filename_for(self, domain: str, entity_id: str) -> str:
        safe_id = entity_id or domain
        if self.export_layout == "flat":
            return f"{domain}_{safe_id}.yml"
        return f"{safe_id}.yml"

    def _summary_from_report(self, report: ValidationReport) -> dict[str, Any]:
        counts = {
            "abilities": len(self.abilities),
            "items": len(self.items),
            "mobs": len(self.mobs),
            "quests": len(self.quests),
            "shops": len(self.shops),
        }
        warnings = [entry.message for entry in report.issues if entry.severity == Severity.WARN]
        errors = [entry.message for entry in report.issues if entry.severity == Severity.ERROR]
        return {
            "counts": counts,
            "warnings": warnings,
            "errors": errors,
        }


class ItemBuilderExporter:
    def __init__(self, output_dir: str) -> None:
        from .items import ItemExporter

        self._exporter = ItemExporter(output_dir)

    def write_item(self, builder: ItemBuilder, filename: str | None = None) -> str:
        return self._exporter.write_item(builder, filename=filename)


def export(output_dir: str, *objects: Any) -> List[str]:
    return ContentPack().add(*objects).export(output_dir)
