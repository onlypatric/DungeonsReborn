from __future__ import annotations

import re
from pathlib import Path
import unittest


class V2NoLegacyImportsTests(unittest.TestCase):
    def test_v2_has_no_parent_legacy_imports(self) -> None:
        root = Path(__file__).resolve().parents[1] / "dungeonsreborn_builder" / "v2"
        pattern = re.compile(r"^\s*from\s+\.\.[\w.]*\s+import\s+", re.MULTILINE)
        offenders: list[str] = []
        for path in sorted(root.rglob("*.py")):
            text = path.read_text(encoding="utf-8")
            if pattern.search(text):
                offenders.append(str(path.relative_to(root)))
        self.assertEqual(offenders, [], f"Legacy parent imports found: {offenders}")

    def test_authoring_has_no_path_override_calls(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        targets = [
            repo_root / "CONFIGPLAN",
            repo_root / "plugin_builder" / "templates",
            repo_root / "plugin_builder" / "tests",
        ]
        needle = ".over" + "ride("
        offenders: list[str] = []
        for base in targets:
            for path in sorted(base.rglob("*.py")):
                text = path.read_text(encoding="utf-8")
                if needle in text:
                    offenders.append(str(path.relative_to(repo_root)))
        self.assertEqual(
            offenders,
            [],
            f"Path override callsites are forbidden in strict typed authoring: {offenders}",
        )


if __name__ == "__main__":
    unittest.main()
