#!/usr/bin/env python3
"""Inspect Halo brand artwork locally and write reproducible visual evidence.

Never uses OCR, guesses an approved palette, or changes source assets.
Dependencies: pip install Pillow pymupdf
Run from a checked-out Halo repository:
  python scripts/brand/audit_brandkit.py --output build/reports/halo-brand-audit.json

This tool is necessary because the GitHub connector exposes the brand kit's
identity and checksums but cannot stream its binary PDF or PNG image data.
"""
import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KIT = "HaloBrandKit (1).pdf"
IMAGES = [f"HaloBrandKit ({i}).png" for i in range(1, 5)] + ["HaloDesignMockup.png"]


def fingerprint(path: Path) -> dict:
    return {"name": path.name, "sha256": sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}


def png_report(path: Path, Image) -> dict:
    # Dominant colours are evidence for *review*, not authoritative design tokens.
    with Image.open(path) as input_image:
        rgb = input_image.convert("RGB")
        thumbnail = rgb.copy()
        thumbnail.thumbnail((220, 220))
        palette = Counter(thumbnail.getdata())
        choices = [
            {"hex": "#%02X%02X%02X" % colour, "pixels": count}
            for colour, count in palette.most_common(16)
        ]
        return {**fingerprint(path), "width": rgb.width, "height": rgb.height,
                "dominant_sampled_colours": choices}


def pdf_report(path: Path, fitz) -> dict:
    pages = []
    with fitz.open(path) as pdf:
        for n, page in enumerate(pdf):
            font_names = set()
            for block in page.get_text("dict").get("blocks", []):
                for line in block.get("lines", []):
                    for span in line.get("spans", []):
                        name = span.get("font")
                        if name:
                            font_names.add(name)
            pages.append({
                "page": n + 1,
                "width_pt": round(page.rect.width, 2),
                "height_pt": round(page.rect.height, 2),
                "declared_fonts": sorted(font_names),
                "raster_image_count": len(page.get_images(full=True)),
            })
    return {**fingerprint(path), "pages": pages}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--brand-dir", type=Path, default=ROOT,
                        help="directory containing the untouched PDF and five PNG references")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "build/reports/halo-brand-audit.json")
    args = parser.parse_args()
    try:
        from PIL import Image
        import fitz
    except ImportError as error:
        parser.error("Install offline inspection dependencies: pip install Pillow pymupdf")
    brand = args.brand_dir.resolve()
    missing = [p for p in [KIT, *IMAGES] if not (brand / p).is_file()]
    if missing:
        parser.error("Required original brand files missing: " + ", ".join(missing))
    audit = {
        "source": str(brand),
        "brandkit": pdf_report(brand / KIT, fitz),
        "references": [png_report(brand / image, Image) for image in IMAGES],
        "limitations": [
            "PNG dominant colour counts include layout backgrounds and are NOT approved tokens.",
            "The PDF font names are declarations; actual fonts must still be licensed and bundled.",
            "Screenshots need human review at equal density and device scale.",
            "No visual acceptance, model behavior, or hardware test is implied by this report.",
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(audit, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Audit recorded:", args.output)
    print("Review the original artwork and compare to app screenshots before approving tokens or logo.")


if __name__ == "__main__":
    main()
