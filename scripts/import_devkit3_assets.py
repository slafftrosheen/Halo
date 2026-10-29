"""Vendor the supplied runtime artwork without shipping reference boards or films."""
from pathlib import Path
import hashlib
import json
import shutil

from brand.devkit_icons import convert_svg_to_vector

ROOT = Path(__file__).resolve().parents[1]
KIT = ROOT / "docs/15-brand/v4"
PRODUCT = KIT / "product"
ASSETS = ROOT / "app/src/main/assets/halo"
receipts = []


def copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    receipts.append({
        "source": source.relative_to(ROOT).as_posix(),
        "asset": destination.relative_to(ASSETS).as_posix(),
        "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    })


for source in sorted((PRODUCT / "B/source/a/lottie").glob("*.json")):
    copy(source, ASSETS / ("fx" if source.name.startswith("halo_fx_") else "") / source.name)
for source in sorted((PRODUCT / "B/source/a/fx").glob("*.svg")):
    copy(source, ASSETS / "fx/static" / source.name)
for package in ("B", "C", "D", "E", "F", "G", "SCREEN"):
    for source in sorted((PRODUCT / package / "lottie").glob("*.json")):
        copy(source, ASSETS / "fx" / source.name)
    for source in sorted((PRODUCT / package / "lottie/static").glob("*.svg")):
        copy(source, ASSETS / "fx/static" / source.name)
for folder in ("ic", "orb", "wm", "vr"):
    for source in sorted((PRODUCT / "B/source/a" / folder).glob("*.svg")):
        copy(source, ASSETS / "artwork" / folder / source.name)

for folder in ("fx", "learning", "voice"):
    for source in sorted((KIT / folder / "lottie").glob("*.json")):
        copy(source, ASSETS / "fx" / source.name)
    for source in sorted((KIT / folder / "static").glob("*.svg")):
        copy(source, ASSETS / "fx/static" / source.name)
for source in sorted((KIT / "orb/lottie").glob("*.json")):
    copy(source, ASSETS / source.name)
for source in sorted((KIT / "kids/lottie").glob("*.json")):
    copy(source, ASSETS / source.name)
for folder, target in (("icons/svg", "ic"), ("orb/svg", "orb"), ("orb/micro", "micro"), ("kids/orb_svg", "kids"), ("wordmark", "wm")):
    for source in sorted((KIT / folder).glob("*.svg")):
        copy(source, ASSETS / "artwork" / target / source.name)

# Icons are native VectorDrawables: no network, asynchronous SVG decode or substitute glyphs.
for source in sorted((KIT / "icons/svg").glob("*.svg")):
    (ROOT / "app/src/main/res/drawable" / (source.stem + ".xml")).write_text(
        convert_svg_to_vector(source.read_text(encoding="utf-8")), encoding="utf-8")
(ASSETS / "artwork/manifest.json").write_text(
    json.dumps({"source": "Brand Book 3.0 / DevKit 4.0", "files": receipts}, indent=2) + "\n",
    encoding="utf-8",
)
print(f"Imported {len(receipts)} supplied runtime assets")
