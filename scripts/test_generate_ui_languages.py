"""Regression checks for localization generation; runs without Android or dependencies."""
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree


class UiLanguageGenerationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        (self.root / "app/src/main/localization").mkdir(parents=True)
        (self.root / "app/src/main/java/com/halo/app/language").mkdir(parents=True)
        self.script = self.root / "scripts/generate_ui_languages.py"
        shutil.copyfile(Path(__file__).with_name("generate_ui_languages.py"), self.script)
        (self.root / "app/src/main/localization/ui.tsv").write_text(
            'Guidance level: %1$s|||Уровень: %1$s|||Līmenis: %1$s\n'
            'Quote "Halo"\\nNext|||Цитата "Halo"\\nДалее|||Citāts "Halo"\\nTālāk\n',
            encoding="utf-8",
        )

    def run_generator(self, *args):
        return subprocess.run([sys.executable, str(self.script), *args], capture_output=True, text=True)

    def test_catalogue_escapes_dollars_and_keeps_same_keys_in_all_languages(self):
        self.assertEqual(0, self.run_generator().returncode)
        catalogue = (self.root / "app/src/main/java/com/halo/app/language/UiCatalogue.kt").read_text(encoding="utf-8")
        self.assertIn(r"%1\$s", catalogue)
        keys = []
        for language in ("values", "values-ru", "values-lv"):
            resources = ElementTree.parse(self.root / f"app/src/main/res/{language}/ui_strings.xml")
            keys.append({element.attrib["name"] for element in resources.getroot()})
        self.assertEqual(keys[0], keys[1])
        self.assertEqual(keys[0], keys[2])
        self.assertEqual(2, len(keys[0]))
        self.assertEqual(0, self.run_generator("--check").returncode)

    def test_check_detects_manual_drift_without_overwriting_it(self):
        self.assertEqual(0, self.run_generator().returncode)
        target = self.root / "app/src/main/java/com/halo/app/language/UiCatalogue.kt"
        target.write_text("manual drift", encoding="utf-8")
        result = self.run_generator("--check")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("UiCatalogue.kt", result.stderr)
        self.assertEqual("manual drift", target.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
