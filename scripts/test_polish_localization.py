"""Regression fixtures for the locale coverage gate; no Android runtime is required."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class PolishResourceCoverageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.resources = self.root / "app/src/main/res"
        for locale in ("values", "values-pl"):
            (self.resources / locale).mkdir(parents=True)
        self.write("values", '<string name="page">Page %1$d of %2$d</string>')
        self.write("values-pl", '<string name="page">Strona %1$d z %2$d</string>')

    def write(self, locale, content):
        (self.resources / locale / "fixture.xml").write_text("<resources>" + content + "</resources>", encoding="utf-8")

    def run_check(self):
        return subprocess.run([sys.executable, str(Path(__file__).with_name("check_polish_localization.py")),
            "--root", str(self.root)], capture_output=True, text=True)

    def test_missing_resource_cannot_silently_fall_back_to_english(self):
        self.write("values-pl", "")
        result = self.run_check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Missing Polish string: page", result.stderr)

    def test_argument_type_change_is_rejected(self):
        self.write("values-pl", '<string name="page">Strona %1$s z %2$d</string>')
        result = self.run_check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Polish formatting arguments differ: page", result.stderr)

    def test_indexed_word_order_and_invariant_protocol_resources_are_allowed(self):
        self.write("values", '<string name="page">Page %1$d of %2$d</string>'
            '<string name="protocol" translatable="false">fixed_protocol_id</string>')
        self.write("values-pl", '<string name="page">Z %2$d stron: %1$d</string>')
        self.assertEqual(0, self.run_check().returncode)

    def test_unknown_key_is_reported_instead_of_hiding_a_typo(self):
        self.write("values-pl", '<string name="paeg">Strona %1$d z %2$d</string>')
        result = self.run_check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Unknown Polish string: paeg", result.stderr)

    def test_static_label_cannot_bypass_the_catalogue(self):
        catalogue = self.root / "app/src/main/localization/ui.tsv"
        catalogue.parent.mkdir(parents=True)
        catalogue.write_text("Ready|||Готово|||Gatavs|||Gotowe\n", encoding="utf-8")
        source = self.root / "app/src/main/java/Fixture.kt"
        source.parent.mkdir(parents=True)
        source.write_text('uiText("Missing label")', encoding="utf-8")
        result = self.run_check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Uncatalogued UI label", result.stderr)
        source.write_text('uiText("Ready"); uiText("$modelResponse")', encoding="utf-8")
        self.assertEqual(0, self.run_check().returncode)


if __name__ == "__main__":
    unittest.main()
