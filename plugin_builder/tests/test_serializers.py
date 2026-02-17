from __future__ import annotations

import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "plugin_builder"))

from dungeonsreborn_builder import DisplaySpec, HeadSpec, HeadsDocument, ItemBuilder, Material
from dungeonsreborn_builder.gui_icons import GuiIcon, gui_icon_kit_document


class SerializerTests(unittest.TestCase):
    def test_item_builder_serialization(self) -> None:
        item = (
            ItemBuilder("schema_test_item")
            .material(Material.STONE)
            .display(DisplaySpec(name="<gray>Schema</gray>"))
        )
        payload = item.build()
        self.assertEqual(payload["material"], "STONE")
        self.assertIn("display", payload)
        self.assertEqual(payload["display"]["name"], "<gray>Schema</gray>")

    def test_heads_document_serialization(self) -> None:
        doc = HeadsDocument().add(HeadSpec(head_id="schema_head", name="Schema Head"))
        data = doc.to_dict()
        self.assertIn("heads", data)
        self.assertIn("schema_head", data["heads"])
        self.assertEqual(data["heads"]["schema_head"]["name"], "Schema Head")

    def test_gui_icon_kit_document(self) -> None:
        doc = gui_icon_kit_document()
        data = doc.to_dict()
        self.assertIn(GuiIcon.NAV_BACK.value.lower(), data["heads"])


if __name__ == "__main__":
    unittest.main()
