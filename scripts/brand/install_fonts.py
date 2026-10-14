#!/usr/bin/env python3
"""Check or locally refresh Halo's bundled SIL OFL 1.1 fonts.

Font binaries and original notices already ship in app/src/main.
No network, build-time download or font provider is used.

  python -m pip install fonttools
  python scripts/brand/install_fonts.py
  python scripts/brand/install_fonts.py --haloweb-dir ../HaloWeb
  python scripts/brand/install_fonts.py --haloweb-dir ../HaloWeb --sync

--sync explicitly copies from a LOCAL HaloWeb checkout. The generated
Kotlin resource mappings in BrandFonts.kt remain source-controlled.
"""
import argparse
import hashlib
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
FONT_DIR = ROOT / "app/src/main/res/font"
LICENSE_DIR = ROOT / "app/src/main/assets/licenses"

ASSETS = (
    ("sora-variable.ttf", "sora_variable.ttf", "Sora"),
    ("atkinson-hyperlegible-next-variable.ttf", "atkinson_hyperlegible_next_variable.ttf", "Atkinson Hyperlegible Next"),
    ("atkinson-hyperlegible-next-italic-variable.ttf", "atkinson_hyperlegible_next_italic_variable.ttf", "Atkinson Hyperlegible Next"),
    ("onest-cyrillic-variable.ttf", "onest_cyrillic_variable.ttf", "Onest"),
)
LICENSES = (
    "OFL-Sora.txt",
    "OFL-Atkinson-Hyperlegible-Next.txt",
    "OFL-Onest.txt",
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_font(path: Path, expected_family: str) -> None:
    if not path.is_file() or path.stat().st_size < 1000:
        raise ValueError(f"Missing or invalid TTF font: {path}")
    try:
        from fontTools.ttLib import TTFont
    except ImportError as exc:
        raise RuntimeError("Install fonttools: python -m pip install fonttools") from exc
    font = TTFont(path, lazy=True)
    try:
        names = {
            entry.toUnicode().casefold()
            for entry in font["name"].names
            if entry.nameID in (1, 4, 6, 16)
        }
        if not any(expected_family.casefold() in value.replace("-", " ") for value in names):
            raise ValueError(f"Font is not {expected_family}: {path}; names: {sorted(names)[:5]}")
    finally:
        font.close()


def verify_license(path: Path) -> None:
    if not path.is_file():
        raise ValueError(f"Missing SIL OFL notice: {path}")
    text = path.read_text("utf-8-sig")
    if "SIL OPEN FONT LICENSE" not in text.upper() or len(text) < 500:
        raise ValueError(f"Invalid or incomplete OFL notice: {path}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--haloweb-dir", type=Path,
                        help="local checkout of HaloWeb; enables source checksum comparison")
    parser.add_argument("--sync", action="store_true",
                        help="explicitly refresh Android resources from local HaloWeb")
    args = parser.parse_args()
    if args.sync and args.haloweb_dir is None:
        parser.error("--sync requires --haloweb-dir; never download fonts automatically")
    source = args.haloweb_dir.resolve() / "assets/fonts" if args.haloweb_dir else None
    if args.sync:
        # Check EVERY source before modifying tracked files.
        for filename, _, family in ASSETS:
            verify_font(source / filename, family)
        for filename in LICENSES:
            verify_license(source / filename)
        FONT_DIR.mkdir(parents=True, exist_ok=True)
        LICENSE_DIR.mkdir(parents=True, exist_ok=True)
        for src, dest, _ in ASSETS:
            shutil.copyfile(source / src, FONT_DIR / dest)
        for filename in LICENSES:
            shutil.copyfile(source / filename, LICENSE_DIR / filename)

    for src, dest, family in ASSETS:
        packaged = FONT_DIR / dest
        verify_font(packaged, family)
        if source is not None:
            original = source / src
            verify_font(original, family)
            if digest(original) != digest(packaged):
                raise ValueError(f"Font SHA256 differs from HaloWeb source: {dest}")
        print(f"{dest}  SHA256={digest(packaged)}")
    for filename in LICENSES:
        packaged = LICENSE_DIR / filename
        verify_license(packaged)
        if source is not None:
            original = source / filename
            verify_license(original)
            if digest(original) != digest(packaged):
                raise ValueError(f"OFL license differs from HaloWeb: {filename}")
        print(f"{filename}  SHA256={digest(packaged)}")
    print("Halo offline brand fonts and SIL OFL notices verified.")


if __name__ == "__main__":
    main()
