"""Exercise the real offline audit against isolated canonical-artwork fixtures."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
A = "{http://schemas.android.com/apk/res/android}"


class DevkitIconAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        scripts = self.root / "scripts/brand"
        scripts.mkdir(parents=True)
        for name in ("audit_devkit_v1_2.py", "devkit_icons.py"):
            source = ROOT / "scripts/brand" / name
            if source.exists():
                shutil.copyfile(source, scripts / name)
        self.audit = scripts / "audit_devkit_v1_2.py"
        self.kit = self.root / "docs/15-brand/reference-v2"
        for relative in ("icons/android", "fx/lottie", "wordmark"):
            (self.kit / relative).mkdir(parents=True)
        svg = self.root / "docs/15-brand/v4/icons/svg"
        svg.mkdir(parents=True)
        self.drawables = self.root / "app/src/main/res/drawable"
        self.drawables.mkdir(parents=True)
        for source in (ROOT / "docs/15-brand/reference-v2/icons/android").glob("halo_ic_*.xml"):
            shutil.copyfile(source, self.kit / "icons/android" / source.name)
            shutil.copyfile(ROOT / "app/src/main/res/drawable" / source.name,
                            self.drawables / source.name)
            shutil.copyfile(ROOT / "docs/15-brand/v4/icons/svg" / (source.stem + ".svg"),
                            svg / (source.stem + ".svg"))
        assets = self.root / "app/src/main/assets/halo/fx"
        assets.mkdir(parents=True)
        for i in range(58):
            name = f"fixture_{i}.json"
            data = '{"fr":30,"layers":[]}'
            (self.kit / "fx/lottie" / name).write_text(data)
            (assets / name).write_text(data)
        bitmap = self.root / "app/src/main/res/drawable-nodpi"
        bitmap.mkdir()
        for theme in ("dark", "light"):
            name = f"halo_wordmark_{theme}.png"
            (self.kit / "wordmark" / name).write_bytes(b"fixture bitmap")
            (bitmap / name).write_bytes(b"fixture bitmap")

    def run_audit(self):
        return subprocess.run([sys.executable, str(self.audit)], capture_output=True, text=True)

    def assert_passes(self):
        result = self.run_audit()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def assert_icon_fails(self):
        result = self.run_audit()
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("halo_ic_accent", result.stdout + result.stderr)
        self.assertIn("failed: 1 error(s)", result.stdout + result.stderr)

    def mutate(self, attribute, value, root=False):
        file = self.drawables / "halo_ic_accent.xml"
        tree = ET.parse(file)
        element = tree.getroot() if root else tree.getroot().find("path")
        element.set(A + attribute, value)
        tree.write(file, encoding="unicode")

    def test_all_current_converted_icons_pass(self):
        self.assert_passes()

    def test_geometry_mutation_fails(self):
        self.mutate("pathData", "M0 0L24 24")
        self.assert_icon_fails()

    def test_stroke_width_mutation_fails(self):
        self.mutate("strokeWidth", "2")
        self.assert_icon_fails()

    def test_alpha_mutation_fails(self):
        self.mutate("strokeAlpha", "0.5")
        self.assert_icon_fails()

    def test_viewport_mutation_fails(self):
        self.mutate("viewportWidth", "25", root=True)
        self.assert_icon_fails()

    def test_paint_mutations_fail(self):
        file = self.drawables / "halo_ic_accent.xml"
        original = file.read_bytes()
        for attribute, value in (("strokeColor", "#00000000"),
                                 ("fillColor", "#FFFFFFFF"),
                                 ("strokeColor", "#FFFF0000")):
            with self.subTest(attribute=attribute, value=value):
                file.write_bytes(original)
                self.mutate(attribute, value)
                self.assert_icon_fails()

    def test_missing_icon_fails(self):
        (self.drawables / "halo_ic_accent.xml").unlink()
        self.assert_icon_fails()

    def test_formatting_and_explicit_defaults_pass(self):
        file = self.drawables / "halo_ic_accent.xml"
        tree = ET.parse(file)
        tree.getroot().set(A + "viewportWidth", "24")
        tree.getroot().set(A + "viewportHeight", "24.000")
        for path in tree.getroot().iter("path"):
            path.attrib.pop(A + "fillAlpha", None)
            path.attrib.pop(A + "strokeAlpha", None)
            if path.get(A + "fillColor") == "#00000000":
                path.attrib.pop(A + "fillColor")
        ET.indent(tree, space="    ")
        tree.write(file, encoding="unicode", xml_declaration=True)
        self.assert_passes()

    def test_arbitrary_attribute_mutation_fails(self):
        self.mutate("trimPathEnd", "0.5")
        self.assert_icon_fails()

    def test_lottie_byte_mutation_still_fails(self):
        (self.root / "app/src/main/assets/halo/fx/fixture_0.json").write_text('{}')
        result = self.run_audit()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("fixture_0.json", result.stdout)

    def test_bitmap_byte_mutation_still_fails(self):
        (self.root / "app/src/main/res/drawable-nodpi/halo_wordmark_dark.png").write_bytes(b"changed")
        result = self.run_audit()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("halo_wordmark_dark.png", result.stdout)

    def test_converter_preserves_all_installed_icon_output(self):
        from scripts.brand.devkit_icons import convert_svg_to_vector
        sources = list((ROOT / "docs/15-brand/v4/icons/svg").glob("halo_ic_*.svg"))
        self.assertEqual(140, len(sources))
        for source in sources:
            with self.subTest(icon=source.stem):
                installed = ROOT / "app/src/main/res/drawable" / (source.stem + ".xml")
                self.assertEqual(installed.read_text(encoding="utf-8"),
                                 convert_svg_to_vector(source.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
