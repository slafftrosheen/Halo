"""Reproducible before/after route and callback index; not semantic/device proof."""
import argparse
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT).decode("utf-8")


def callbacks(source):
    # Capture the callback binding, not arbitrary content rendered by the screen.
    return [{"line": source.count("\n", 0, match.start()) + 1,
             "binding": match.group(0).strip()[:180]}
            for match in re.finditer(r"\bon(?:Click|LongClick|ValueChange|CheckedChange|DismissRequest)\s*=\s*[^\n]+", source)]


def routes(source):
    result = []
    for match in re.finditer(r"\bcomposable\(\s*(\"[^\"]+\"|Screen\.\w+\.route|\w+Route)", source):
        result.append({"routeExpression": match.group(1), "line": source.count("\n", 0, match.start()) + 1})
    return result


def inventory(baseline):
    shell = "app/src/main/java/com/halo/app/ui/shell/AppShell.kt"
    files = git("ls-tree", "-r", "--name-only", baseline, "app/src/main/java/com/halo/app").splitlines()
    files = [p for p in files if p.endswith(".kt") and
             ("/ui/screens/" in p or "/ui/workspace/" in p or "/screen/" in p or "/ui/mini/" in p or "/ui/components/" in p)]
    baseline_files = set(files)
    for folder in ("ui/screens", "ui/workspace", "screen", "ui/mini", "ui/components"):
        files.extend(p.relative_to(ROOT).as_posix() for p in (ROOT / "app/src/main/java/com/halo/app" / folder).rglob("*.kt"))
    rows = []
    for name in sorted(set(files)):
        before = git("show", f"{baseline}:{name}") if name in baseline_files else ""
        after = (ROOT / name).read_text(encoding="utf-8") if (ROOT / name).exists() else ""
        old, new = callbacks(before), callbacks(after)
        if old or new:
            rows.append({"file": name, "before": old, "after": new,
                         "review": "See INTERFACE-4-SCREEN-PARITY.md and existing typed operability contracts; callback counts alone do not prove preservation."})
    return {"schema": 1, "baseline": baseline,
            "scope": "Static route/callback index. Not complete behavioral, semantic, accessibility, voice or device qualification.",
            "routesBefore": routes(git("show", f"{baseline}:{shell}")),
            "routesAfter": routes((ROOT / shell).read_text(encoding="utf-8")), "screens": rows}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--output", type=Path, required=True)
    options = parser.parse_args()
    result = inventory(options.baseline)
    options.output.parent.mkdir(parents=True, exist_ok=True)
    options.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Indexed {len(result['screens'])} interactive source files and {len(result['routesAfter'])} route declarations")
