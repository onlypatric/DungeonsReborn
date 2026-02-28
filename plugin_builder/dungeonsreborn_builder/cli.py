"""CLI entrypoints for the v2 builder."""

from __future__ import annotations

import argparse
import importlib.util
import re
import sys
import time
from pathlib import Path
from typing import Any, Callable, Optional

from .v2.internal.validate import ValidationReport, render_report


def _load_module(path: Path) -> Any:
    spec = importlib.util.spec_from_file_location("builder_script", path)
    if spec is None or spec.loader is None:
        raise ValueError(f"Unable to load script: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["builder_script"] = module
    spec.loader.exec_module(module)
    return module


def _is_pack_object(value: Any) -> bool:
    return hasattr(value, "export") and callable(value.export)


def _resolve_pack(module: Any) -> Optional[Any]:
    factories = ["build_v2", "build_pack_v2", "build_pack"]
    for name in factories:
        if hasattr(module, name) and callable(getattr(module, name)):
            candidate = getattr(module, name)()
            if _is_pack_object(candidate):
                return candidate

    values = ["v2_pack", "V2_PACK", "pack", "PACK"]
    for name in values:
        if hasattr(module, name):
            candidate = getattr(module, name)
            if _is_pack_object(candidate):
                return candidate

    return None


def _resolve_build(module: Any) -> Optional[Callable[[str], Any]]:
    if hasattr(module, "build_v2") and callable(module.build_v2):
        return module.build_v2
    if hasattr(module, "build") and callable(module.build):
        return module.build
    if hasattr(module, "export") and callable(module.export):
        return module.export
    return None


def _print_validation(value: Any) -> bool:
    if isinstance(value, ValidationReport):
        output = render_report(value)
        if output:
            print(output)
        return value.has_errors()
    if hasattr(value, "errors") and callable(value.errors) and hasattr(value, "warnings") and callable(value.warnings):
        # Compatibility with report-like objects.
        errors = list(value.errors())
        warnings = list(value.warnings())
        for warning in warnings:
            print(f"[WARN] {getattr(warning, 'message', warning)}")
        for error in errors:
            print(f"[ERROR] {getattr(error, 'message', error)}")
        return bool(errors)
    if isinstance(value, str):
        if value:
            print(value)
        return False
    if isinstance(value, dict):
        errors = value.get("errors") or []
        warnings = value.get("warnings") or []
        counts = value.get("counts") or {}
        if counts:
            print("counts:", counts)
        for warning in warnings:
            print(f"[WARN] {warning}")
        for error in errors:
            print(f"[ERROR] {error}")
        return bool(errors)
    if isinstance(value, list):
        has_errors = False
        for entry in value:
            print(str(entry))
            if "error" in str(entry).lower():
                has_errors = True
        return has_errors
    if value is None:
        return False
    print(str(value))
    return False


def _run_build(script: Path, output_dir: str) -> int:
    module = _load_module(script)
    pack = _resolve_pack(module)
    if pack is not None:
        pack.export(output_dir)
        return 0
    build = _resolve_build(module)
    if build is None:
        print("No v2 build/export function found.", file=sys.stderr)
        return 2
    build(output_dir)
    return 0


def _run_preview(script: Path) -> int:
    module = _load_module(script)
    pack = _resolve_pack(module)
    if pack is None:
        print("Preview requires build_v2()/build_pack_v2() or v2 pack variable.", file=sys.stderr)
        return 2
    if not hasattr(pack, "preview_export") or not callable(pack.preview_export):
        print("Selected pack does not support preview_export().", file=sys.stderr)
        return 2
    preview = pack.preview_export()
    print(preview)
    return 0


def _run_validate(script: Path, strict: bool = False) -> int:
    if strict:
        static_issues = _strict_authoring_issues(script)
        if static_issues:
            for issue in static_issues:
                print(f"[ERROR] {issue}")
            return 1

    module = _load_module(script)
    pack = _resolve_pack(module)
    if pack is None:
        print("Validate requires build_v2()/build_pack_v2() or v2 pack variable.", file=sys.stderr)
        return 2
    if not hasattr(pack, "validate") or not callable(pack.validate):
        print("Selected pack does not support validate().", file=sys.stderr)
        return 2
    try:
        result = pack.validate()
    except Exception as exc:  # noqa: BLE001
        print(str(exc), file=sys.stderr)
        return 1
    has_errors = _print_validation(result)
    if strict and has_errors:
        return 1
    return 0


def _strict_authoring_issues(script: Path) -> list[str]:
    text = script.read_text(encoding="utf-8")
    checks: list[tuple[str, str]] = [
        (r"\.raw_[a-zA-Z0-9_]*\(", "raw API call is forbidden in strict typed authoring"),
        (r"\.override\(", "mob/item path override is forbidden in strict typed authoring; use typed APIs"),
        (r"\bfx\.sphere_shell\(", "legacy fx.sphere_shell is forbidden; use fx.particles_sphere_shell(...) or vfx.*"),
        (r"\bAction\(", "generic Action(...) is forbidden; use concrete ActionSpec helpers"),
        (r"\bRequirement\(", "generic Requirement(...) is forbidden; use concrete RequirementSpec helpers"),
        (r"\bCost\(", "generic Cost(...) is forbidden; use concrete CostSpec helpers"),
        (r"keys\s*=\s*\{", "recipe keys dict is forbidden; use recipe.keys().slot(...)"),
    ]
    issues: list[str] = []
    lines = text.splitlines()
    for pattern, message in checks:
        compiled = re.compile(pattern)
        for index, line in enumerate(lines, start=1):
            if compiled.search(line):
                issues.append(f"{script}:{index}: {message}")
    return issues


def _run_id_map(script: Path) -> int:
    module = _load_module(script)
    pack = _resolve_pack(module)
    if pack is None:
        print("id-map requires build_v2()/build_pack_v2() or v2 pack variable.", file=sys.stderr)
        return 2
    if not hasattr(pack, "id_map") or not callable(pack.id_map):
        print("Selected pack does not expose id_map().", file=sys.stderr)
        return 2
    mapping = pack.id_map()
    if not mapping:
        print("No generated IDs.")
        return 0
    for symbol in sorted(mapping.keys()):
        print(f"{symbol} -> {mapping[symbol]}")
    return 0


def _copy_template(template: str, dest: Path) -> None:
    template_dir = Path(__file__).resolve().parents[1] / "templates"
    source = template_dir / template
    if not source.exists():
        raise FileNotFoundError(f"Unknown template: {template}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


def _run_new(dest_dir: Path, template: str) -> int:
    dest_dir.mkdir(parents=True, exist_ok=True)
    script_path = dest_dir / "pack.py"
    if script_path.exists():
        print(f"{script_path} already exists.", file=sys.stderr)
        return 2
    _copy_template(template, script_path)
    print(f"Created {script_path}")
    return 0


def _run_watch(script: Path, output_dir: str, interval: float) -> int:
    last_mtime = 0.0
    while True:
        try:
            mtime = script.stat().st_mtime
        except FileNotFoundError:
            mtime = 0.0
        if mtime != last_mtime:
            last_mtime = mtime
            print("Rebuilding...")
            _run_build(script, output_dir)
        time.sleep(interval)


def _run_migrate(path: Path, backup: bool = True) -> int:
    from .v2.migrate import iter_python_files, migrate_many

    files = iter_python_files(path)
    if not files:
        print(f"No Python files found in {path}", file=sys.stderr)
        return 2
    summary = migrate_many(files, backup=backup)
    for result in summary.files:
        status = "changed" if result.changed else "unchanged"
        print(f"{status}: {result.path} (rewrites={result.rewrites})")
        for flag in result.manual_flags:
            print(f"  manual: {flag}")
    print(
        f"summary: files={len(summary.files)} changed={summary.changed_files} "
        f"rewrites={summary.rewrite_count} manual_flags={summary.manual_flags}"
    )
    return 0


def _run_migrate_typed(path: Path, backup: bool = True) -> int:
    from .v2.migrate import iter_python_files, migrate_typed_many

    files = iter_python_files(path)
    if not files:
        print(f"No Python files found in {path}", file=sys.stderr)
        return 2
    summary = migrate_typed_many(files, backup=backup)
    for result in summary.files:
        status = "changed" if result.changed else "unchanged"
        print(f"{status}: {result.path} (rewrites={result.rewrites})")
        for flag in result.manual_flags:
            print(f"  manual: {flag}")
    print(
        f"summary: files={len(summary.files)} changed={summary.changed_files} "
        f"rewrites={summary.rewrite_count} manual_flags={summary.manual_flags}"
    )
    return 0


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(prog="dungeonsreborn-builder")
    sub = parser.add_subparsers(dest="command", required=True)

    build_v2 = sub.add_parser("build-v2", help="Build and export a v2 pack")
    build_v2.add_argument("script", type=Path, help="Python script with build_v2()/build_pack_v2()")
    build_v2.add_argument("-o", "--output", default="./out", help="Output folder")

    validate_v2 = sub.add_parser("validate-v2", help="Validate a v2 content pack script")
    validate_v2.add_argument("script", type=Path)
    validate_v2.add_argument("--strict", action="store_true", help="Exit non-zero on errors")

    # Transitional aliases.
    build = sub.add_parser("build", help="Alias of build-v2 (transitional)")
    build.add_argument("script", type=Path)
    build.add_argument("-o", "--output", default="./out")

    validate = sub.add_parser("validate", help="Alias of validate-v2 (transitional)")
    validate.add_argument("script", type=Path)
    validate.add_argument("--strict", action="store_true")

    preview = sub.add_parser("preview", help="Preview export summary (no files written)")
    preview.add_argument("script", type=Path)

    id_map = sub.add_parser("id-map", help="Print v2 generated symbol -> id mappings")
    id_map.add_argument("script", type=Path)

    new = sub.add_parser("new", help="Create a new v2 builder project from a template")
    new.add_argument("dest", type=Path)
    new.add_argument(
        "--template",
        default="v2_starter_pack.py",
        help="Template filename (v2_starter_pack.py, v2_mob_quickstart.py, v2_weapon_bundle.py, v2_shop_pack.py, v2_campaign_pack.py, v2_consumable_showcase.py)",
    )

    migrate = sub.add_parser("migrate-v1-to-v2", help="Rewrite imports and flag manual migration zones")
    migrate.add_argument("path", type=Path, help="Python file or directory")
    migrate.add_argument("--no-backup", action="store_true", help="Do not emit .v1.bak backups")

    migrate_typed = sub.add_parser(
        "migrate-v2-typed",
        help="Rewrite enum/string callsites toward v2 typed APIs and flag manual zones",
    )
    migrate_typed.add_argument("path", type=Path, help="Python file or directory")
    migrate_typed.add_argument("--no-backup", action="store_true", help="Do not emit .v2typed.bak backups")

    watch = sub.add_parser("watch", help="Watch a script and rebuild on changes")
    watch.add_argument("script", type=Path)
    watch.add_argument("-o", "--output", default="./out")
    watch.add_argument("--interval", type=float, default=1.0)

    args = parser.parse_args(argv)

    if args.command in {"build-v2", "build"}:
        return _run_build(args.script, args.output)
    if args.command in {"validate-v2", "validate"}:
        return _run_validate(args.script, strict=args.strict)
    if args.command == "preview":
        return _run_preview(args.script)
    if args.command == "id-map":
        return _run_id_map(args.script)
    if args.command == "new":
        return _run_new(args.dest, args.template)
    if args.command == "migrate-v1-to-v2":
        return _run_migrate(args.path, backup=not args.no_backup)
    if args.command == "migrate-v2-typed":
        return _run_migrate_typed(args.path, backup=not args.no_backup)
    if args.command == "watch":
        return _run_watch(args.script, args.output, args.interval)
    return 0
