"""Regression checks for localization generation; runs without Android or dependencies."""
import shutil
import subprocess
import sys
import tempfile
import unittest
import json
from hashlib import sha256
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
        shutil.copyfile(Path(__file__).with_name("localization_format.py"), self.script.with_name("localization_format.py"))
        (self.root / "app/src/main/localization/ui.tsv").write_text(
            'Guidance level: %1$s|||Уровень: %1$s|||Līmenis: %1$s|||Poziom wskazówek: %1$s\n'
            'Quote "Halo"\\nNext|||Цитата "Halo"\\nДалее|||Citāts "Halo"\\nTālāk|||Cytat "Halo"\\nDalej\n',
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
        polish = ElementTree.parse(self.root / "app/src/main/res/values-pl/ui_strings.xml")
        polish_keys = {element.attrib["name"] for element in polish.getroot()}
        self.assertEqual(keys[0], polish_keys)
        self.assertEqual(0, self.run_generator("--check").returncode)

    def test_missing_polish_is_rejected_instead_of_shipping_english_fallback(self):
        source = self.root / "app/src/main/localization/ui.tsv"
        source.write_text("Ready|||Готово|||Gatavs\n", encoding="utf-8")
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Polish", result.stderr)
        self.assertFalse((self.root / "app/src/main/res/values-pl/ui_strings.xml").exists())

    def test_changed_polish_argument_type_is_rejected_before_emitting_resources(self):
        source = self.root / "app/src/main/localization/ui.tsv"
        source.write_text("Page %1$d|||Страница %1$d|||Lapa %1$d|||Strona %1$s\n", encoding="utf-8")
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Polish formatting arguments", result.stderr)
        self.assertFalse((self.root / "app/src/main/res/values-pl/ui_strings.xml").exists())

    def test_indexed_arguments_can_move_but_unindexed_types_cannot(self):
        source = self.root / "app/src/main/localization/ui.tsv"
        source.write_text("%1$d cards for %2$s|||%1$d для %2$s|||%1$d: %2$s|||%2$s: karty %1$d\n", encoding="utf-8")
        self.assertEqual(0, self.run_generator().returncode)
        source.write_text("%d cards for %s|||%d для %s|||%d: %s|||%s: karty %d\n", encoding="utf-8")
        result = self.run_generator()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Polish formatting arguments", result.stderr)

    def test_literal_percent_prose_is_not_a_placeholder(self):
        source = self.root / "app/src/main/localization/ui.tsv"
        source.write_text("100% of files|||100% файлов|||100% failu|||100% plików\n", encoding="utf-8")
        self.assertEqual(0, self.run_generator().returncode)

    def test_check_detects_manual_drift_without_overwriting_it(self):
        self.assertEqual(0, self.run_generator().returncode)
        target = self.root / "app/src/main/java/com/halo/app/language/UiCatalogue.kt"
        target.write_text("manual drift", encoding="utf-8")
        result = self.run_generator("--check")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("UiCatalogue.kt", result.stderr)
        self.assertEqual("manual drift", target.read_text(encoding="utf-8"))

    def test_display_overrides_and_canonical_aliases_survive_regeneration(self):
        resource_id = "ui_" + sha256(b"Guidance level: %1$s").hexdigest()[:12]
        native = self.root / "app/src/main/res/values/native.xml"
        native.parent.mkdir(parents=True)
        native.write_text('<resources><string name="native_mode">Mode profiles</string></resources>', encoding="utf-8")
        overrides = self.root / "app/src/main/localization/ui_overrides.json"
        overrides.write_text(json.dumps({"text": {resource_id: {"en": "Help level: %1$s", "pl": "Pomoc: %1$s"}},
            "aliases": {"Mode profiles": "native_mode", "Help level: %1$s": resource_id}}), encoding="utf-8")
        self.assertEqual(0, self.run_generator().returncode)
        english = (self.root / "app/src/main/res/values/ui_strings.xml").read_text(encoding="utf-8")
        self.assertIn('"Help level: %1$s"', english)
        catalogue = (self.root / "app/src/main/java/com/halo/app/language/UiCatalogue.kt").read_text(encoding="utf-8")
        self.assertIn('"Mode profiles" to R.string.native_mode', catalogue)
        self.assertIn('putAll(uiCatalogueAliases())', catalogue)
        self.assertEqual(0, self.run_generator("--check").returncode)

    def test_override_cannot_drop_format_arguments_or_reference_unknown_resources(self):
        resource_id = "ui_" + sha256(b"Guidance level: %1$s").hexdigest()[:12]
        overrides = self.root / "app/src/main/localization/ui_overrides.json"
        overrides.write_text(json.dumps({"text": {resource_id: {"en": "Help"}}}), encoding="utf-8")
        self.assertIn("display override", self.run_generator().stderr)
        overrides.write_text(json.dumps({"aliases": {"Mode profiles": "missing_native_mode"}}), encoding="utf-8")
        self.assertIn("Unknown catalogue alias target", self.run_generator().stderr)
        overrides.write_text(json.dumps({"aliases": {"Help": resource_id}}), encoding="utf-8")
        self.assertIn("alias formatting arguments differ", self.run_generator().stderr)


if __name__ == "__main__":
    unittest.main()
