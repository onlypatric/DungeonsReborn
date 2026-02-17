"""CLI entrypoints for the builder."""

from __future__ import annotations

import argparse
import importlib.util
import os
import sys
import time
from pathlib import Path
from typing import Any, Callable, Optional

from .pack import ContentPack
from .validation import render_report


def _load_module(path: Path) -> Any:
    spec = importlib.util.spec_from_file_location("builder_script", path)
    if spec is None or spec.loader is None:
        raise ValueError(f"Unable to load script: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["builder_script"] = module
    spec.loader.exec_module(module)
    return module


def _resolve_pack(module: Any) -> Optional[ContentPack]:
    if hasattr(module, "build_pack") and callable(module.build_pack):
        pack = module.build_pack()
        if isinstance(pack, ContentPack):
            return pack
    if hasattr(module, "pack"):
        pack = module.pack
        if isinstance(pack, ContentPack):
            return pack
    if hasattr(module, "PACK"):
        pack = module.PACK
        if isinstance(pack, ContentPack):
            return pack
    return None


def _resolve_build(module: Any) -> Optional[Callable[[str], Any]]:
    if hasattr(module, "build") and callable(module.build):
        return module.build
    if hasattr(module, "export") and callable(module.export):
        return module.export
    return None


def _run_build(script: Path, output_dir: str) -> int:
    module = _load_module(script)
    pack = _resolve_pack(module)
    if pack is not None:
        pack.export(output_dir)
        return 0
    build = _resolve_build(module)
    if build is None:
        print("No build/export function found. Provide build(output_dir) or build_pack().", file=sys.stderr)
        return 2
    build(output_dir)
    return 0


def _run_preview(script: Path) -> int:
    module = _load_module(script)
    pack = _resolve_pack(module)
    if pack is None:
        print("Preview requires build_pack() or pack variable in the script.", file=sys.stderr)
        return 2
    preview = pack.preview_export()
    print(preview)
    return 0


def _run_validate(script: Path, strict: bool = False) -> int:
    module = _load_module(script)
    pack = _resolve_pack(module)
    if pack is None:
        print("Validate requires build_pack() or pack variable in the script.", file=sys.stderr)
        return 2
    report = pack.validate()
    text = render_report(report)
    if text:
        print(text)
    if strict and report.has_errors():
        return 1
    return 0


def _copy_template(template: str, dest: Path) -> None:
    template_dir = Path(__file__).resolve().parent.parent / "templates"
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


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(prog="dungeonsreborn-builder")
    sub = parser.add_subparsers(dest="command", required=True)

    build = sub.add_parser("build", help="Build and export a content pack")
    build.add_argument("script", type=Path, help="Python script with build() or build_pack()")
    build.add_argument("-o", "--output", default="./out", help="Output folder")

    validate = sub.add_parser("validate", help="Validate a content pack script")
    validate.add_argument("script", type=Path)
    validate.add_argument("--strict", action="store_true", help="Exit non-zero on errors")

    lint = sub.add_parser("lint", help="Lint a content pack script (alias of validate)")
    lint.add_argument("script", type=Path)
    lint.add_argument("--strict", action="store_true", help="Exit non-zero on errors")

    preview = sub.add_parser("preview", help="Preview export summary (no files written)")
    preview.add_argument("script", type=Path)

    new = sub.add_parser("new", help="Create a new builder project from a template")
    new.add_argument("dest", type=Path)
    new.add_argument(
        "--template",
        default="starter_kit_pack.py",
        help="Template filename (starter_kit_pack.py, dungeon_pack.py, palette_pack.py, golden_path_pack.py)",
    )

    watch = sub.add_parser("watch", help="Watch a script and rebuild on changes")
    watch.add_argument("script", type=Path)
    watch.add_argument("-o", "--output", default="./out")
    watch.add_argument("--interval", type=float, default=1.0)

    wizard = sub.add_parser("wizard", help="Interactive wizard to bootstrap a pack")
    wizard.add_argument("dest", type=Path)

    args = parser.parse_args(argv)

    if args.command == "build":
        return _run_build(args.script, args.output)
    if args.command == "validate":
        return _run_validate(args.script, strict=args.strict)
    if args.command == "lint":
        return _run_validate(args.script, strict=args.strict)
    if args.command == "preview":
        return _run_preview(args.script)
    if args.command == "new":
        return _run_new(args.dest, args.template)
    if args.command == "watch":
        return _run_watch(args.script, args.output, args.interval)
    if args.command == "wizard":
        print("Choose a template:")
        templates = ["starter_kit_pack.py", "dungeon_pack.py", "palette_pack.py", "golden_path_pack.py"]
        for idx, template in enumerate(templates, start=1):
            print(f"{idx}) {template}")
        selection = input("Template [1]: ").strip() or "1"
        try:
            template = templates[int(selection) - 1]
        except (ValueError, IndexError):
            template = templates[0]
        return _run_new(args.dest, template)
    return 0
