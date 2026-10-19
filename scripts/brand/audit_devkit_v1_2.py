#!/usr/bin/env python3
"""Check the v1.2 Android asset wiring against the canonical source pack.

Run in a checked-out Halo repository:
    python scripts/brand/audit_devkit_v1_2.py
No network, no build tool, no source mutations. Nonzero exit on missing/stale artwork.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KIT = ROOT / "docs/15-brand/reference-v2"
if not KIT.is_dir():
    KIT = ROOT / "DevKit_v1.2"
APP = ROOT / "app/src/main"
errors: list[str] = []


def verify(source: Path, target: Path) -> None:
    if not target.is_file():
        errors.append(f"missing {target.relative_to(ROOT)}")
        return
    # Git may check text assets out with CRLF on Windows. Compare content, not
    # checkout line endings; bitmap assets still require exact byte identity.
    original, installed = source.read_bytes(), target.read_bytes()
    if source.suffix.lower() in {".xml", ".json", ".svg"}:
        original, installed = original.replace(b"\r\n", b"\n"), installed.replace(b"\r\n", b"\n")
    if hashlib.sha256(original).digest() != hashlib.sha256(installed).digest():
        errors.append(f"stale {target.relative_to(ROOT)}")


icons = list((KIT / "icons/android").glob("halo_ic_*.xml"))
for icon in icons:
    verify(icon, APP / "res/drawable" / icon.name)

for folder, destination in (
    ("orb/lottie", APP / "assets/halo"),
    ("kids/lottie", APP / "assets/halo"),
    ("fx/lottie", APP / "assets/halo/fx"),
    ("learning/lottie", APP / "assets/halo/fx"),
    ("voice/lottie", APP / "assets/halo/fx"),
):
    for asset in (KIT / folder).glob("*.json"):
        verify(asset, destination / asset.name)
        try:
            lottie = json.loads(asset.read_text(encoding="utf-8"))
            if lottie.get("fr") != 30 or not isinstance(lottie.get("layers"), list):
                errors.append(f"invalid Lottie metadata: {asset.relative_to(ROOT)}")
            if lottie.get("assets"):
                errors.append(f"unexpected external image assets: {asset.relative_to(ROOT)}")
            if folder == "fx/lottie" and ("_tool_" in asset.name or "_device_connect_" in asset.name):
                expected = ("connecting", "connected") if "_device_connect_" in asset.name else ("start", "working", "done")
                marker_names = [m.get("cm") for m in lottie.get("markers", [])]
                if not all(m in marker_names for m in expected):
                    errors.append(f"missing runtime phase markers: {asset.relative_to(ROOT)}")
        except (ValueError, OSError) as exc:
            errors.append(f"unreadable Lottie {asset.relative_to(ROOT)}: {exc}")

for theme in ("dark", "light"):
    verify(KIT / "wordmark" / f"halo_wordmark_{theme}.png",
           APP / "res/drawable-nodpi" / f"halo_wordmark_{theme}.png")

assert len(icons) == 140, "Unexpected DevKit icon inventory"
assert len(list((KIT / "fx/lottie").glob("*.json"))) == 58, "Unexpected DevKit FX inventory"
if errors:
    print("\n".join(errors))
    raise SystemExit(f"DevKit asset audit failed: {len(errors)} error(s)")
print("DevKit base artwork and available learning/voice additions match.")
