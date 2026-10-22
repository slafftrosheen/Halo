#!/usr/bin/env python3
"""Find literal Compose UI labels that are missing translations or uiText wiring.

This is a conservative, read-only audit, not a full Kotlin parser. Dynamic UI
strings and provider diagnostics require manual review.
Run: python scripts/audit_ui_strings.py [--fail-on-missing] [--limit 25]
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/localization/ui.tsv"
UI = ROOT / "app/src/main/java/com/halo/app/ui"
CALLS = (
    "Text", "HaloWorkspaceButton", "HaloWorkspaceSection", "HaloWorkspaceHeader",
    "HaloGlassButton", "HaloGlassPage", "HaloGlassLazyPage", "HaloGlassHeader",
    "HaloGlassSection", "HaloGlassEmptyState", "HaloScreenHeader", "HaloGlassSettingRow",
)
CALL = re.compile(
    r'\b(?:' + '|'.join(CALLS) +
    r')\s*\(\s*(?:(?:text|title)\s*=\s*)?"([^"\\\n]+)"'
)
ACCESSIBILITY = re.compile(r'\bcontentDescription\s*=\s*"([^"\\\n]+)"')
ENGLISH = re.compile(r"[A-Za-z]{2,}")


def read_catalogue() -> set[str]:
    result: set[str] = set()
    for index, line in enumerate(SOURCE.read_text(encoding="utf-8").splitlines(), 1):
        if not line:
            continue
        fields = line.split("|||")
        if len(fields) not in (3, 4) or not all(fields[:3]) or (len(fields) == 4 and not fields[3]):
            raise ValueError(f"Malformed EN/RU/LV translation, ui.tsv:{index}")
        en, ru, lv = fields[:3]
        if en in result:
            raise ValueError(f"Duplicate English key, ui.tsv:{index}: {en!r}")
        result.add(en.replace(r"\n", "\n"))
    return result


def scan():
    catalogue = read_catalogue()
    untranslated: dict[str, set[str]] = defaultdict(set)
    not_wired: dict[str, set[str]] = defaultdict(set)
    candidates = 0
    for file in sorted(UI.rglob("*.kt")):
        source = file.read_text(encoding="utf-8")
        path = str(file.relative_to(ROOT))
        for pattern in (CALL, ACCESSIBILITY):
            for match in pattern.finditer(source):
                label = match.group(1)
                if not ENGLISH.search(label) or "$" in label or "://" in label:
                    continue
                if label in catalogue:
                    not_wired[path].add(label)
                else:
                    untranslated[path].add(label)
                candidates += 1
    return catalogue, untranslated, not_wired, candidates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fail-on-missing", action="store_true")
    parser.add_argument("--limit", type=int, default=25)
    args = parser.parse_args()
    catalogue, untranslated, not_wired, candidates = scan()
    print(f"EN/RU/LV catalogue entries: {len(catalogue)}; literal UI candidates outside uiText: {candidates}")
    for label, records in (
        ("MISSING TRANSLATION", untranslated), ("CATALOGUED BUT NOT WIRED", not_wired)
    ):
        print(f"\n{label} ({sum(map(len, records.values()))} distinct per-file literals)")
        for path, labels in sorted(records.items()):
            print(f"  {path} ({len(labels)})")
            for literal in sorted(labels)[:max(0, args.limit)]:
                print(f"    {literal}")
    print("\nNote: conditional/dynamic UI strings and provider diagnostics need manual review.")
    if args.fail_on_missing and (untranslated or not_wired):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
