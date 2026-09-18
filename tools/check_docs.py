"""Check documentation labels and local Markdown targets; no network or factual certification."""
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
PRESERVED = ("research/evidence/", "docs/protocols/heycyan/vendor/")


def documents():
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.md"],
        cwd=ROOT, capture_output=True, check=True,
    )
    names = set(result.stdout.decode("utf-8").split("\0"))
    return sorted(name for name in names if name and not name.startswith('.') and (ROOT / name).is_file())


def main():
    errors = []
    historical_links = []
    counts = {"managed": 0, "preserved": 0}
    for name in documents():
        if name.startswith(PRESERVED):
            counts["preserved"] += 1
            continue
        counts["managed"] += 1
        body = (ROOT / name).read_text(encoding="utf-8-sig")
        status = re.search(r"(?im)^(?:> )?(?:\*\*)?Status(?:\*\*)?\s*:(.*)", body[:1800])
        if not status:
            errors.append(f"{name}: missing document status near the start")
        historical = bool(status and ('HISTORICAL RESEARCH' in status.group(1) or 'HISTORICAL REPORT' in status.group(1)))
        fence = False
        for number, line in enumerate(body.splitlines(), 1):
            if re.match(r"\s*(?:```|~~~)", line):
                fence = not fence
                continue
            if fence:
                continue
            for match in re.finditer(r"!?\[[^\]]*\]\((<[^>]+>|[^)]+)\)", line):
                target = match.group(1).strip().strip("<>")
                if re.match(r"(?:[a-zA-Z][a-zA-Z0-9+.-]*:|#)", target):
                    continue
                target = target.split(' "', 1)[0].split("#", 1)[0].split("?", 1)[0]
                target = re.sub(r":\d+(?:-\d+)?$", "", unquote(target))
                if not target:
                    continue
                destination = (ROOT / name).parent / target
                if not destination.exists():
                    issue = f"{name}:{number}: missing local target {target}"
                    (historical_links if historical else errors).append(issue)
    print(f"Documents: {counts['managed']} managed, {counts['preserved']} preserved evidence/vendor files")
    for issue in errors:
        print("ERROR", issue)
    for issue in historical_links:
        print("HISTORICAL LINK", issue)
    print(f"Result: {len(errors)} errors; {len(historical_links)} historical unresolved links")
    print("Scope: status labels and inline local links only; no factual, anchor or remote-link certification.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
