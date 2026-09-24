"""Check documentation labels and local Markdown targets; no network or factual certification."""
from pathlib import Path
import re
import subprocess
import sys
import hashlib
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
PRESERVED = ("research/evidence/", "docs/protocols/heycyan/vendor/", "docs/archive/", "archive/")

CANONICAL_DOCS = [
    "CURRENT-STATE.md",
    "PROJECT-DIRECTION.md",
    "docs/01-vision/VISION.md",
    "docs/04-architecture/ARCHITECTURE.md",
    "docs/03-design/HALO-WORKSPACE-DESIGN.md",
    "docs/14-roadmap/ROADMAP.md",
    "docs/protocols/heycyan/IMPLEMENTATION_STATUS.md"
]

def documents():
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.md"],
        cwd=ROOT, capture_output=True, check=True,
    )
    names = set(result.stdout.decode("utf-8").split("\0"))
    return sorted(name for name in names if name and not name.startswith('.') and (ROOT / name).is_file())

def check_repeated_sections(name, body):
    errors = []
    # simplistic check for repeated headings/body
    sections = {}
    current_heading = None
    current_content = []
    for line in body.splitlines():
        if re.match(r'^#{1,3}\s', line):
            if current_heading:
                content_hash = hashlib.md5("\n".join(current_content).strip().encode('utf-8')).hexdigest()
                # only care if content is substantial
                if len("\n".join(current_content).strip()) > 100:
                    if content_hash in sections:
                        errors.append(f"{name}: repeated substantial section similar to '{sections[content_hash]}'")
                    else:
                        sections[content_hash] = current_heading
            current_heading = line.strip()
            current_content = []
        else:
            if current_heading:
                current_content.append(line)
    return errors

def main():
    errors = []
    historical_links = []
    counts = {"managed": 0, "preserved": 0}

    for doc in CANONICAL_DOCS:
        if not (ROOT / doc).exists():
            errors.append(f"Missing canonical document: {doc}")

    seen_hashes = {}

    for name in documents():
        is_preserved = False
        for prefix in PRESERVED:
            if name.startswith(prefix):
                is_preserved = True
                break

        if is_preserved:
            counts["preserved"] += 1
            continue

        counts["managed"] += 1
        body = (ROOT / name).read_text(encoding="utf-8-sig")

        # Duplicate detection (normalized)
        normalized_body = re.sub(r'\s+', ' ', body).strip()
        body_hash = hashlib.md5(normalized_body.encode('utf-8')).hexdigest()
        if len(normalized_body) > 100:
            if body_hash in seen_hashes:
                errors.append(f"Duplicate document content: {name} and {seen_hashes[body_hash]}")
            else:
                seen_hashes[body_hash] = name

        # Repeated sections
        section_errors = check_repeated_sections(name, body)
        errors.extend(section_errors)

        status = re.search(r"(?im)^(?:> )?(?:\*\*)?Status(?:\*\*)?\s*:(.*)", body[:1800])
        if not status:
            errors.append(f"{name}: missing document status near the start")

        historical = bool(status and ('HISTORICAL RESEARCH' in status.group(1) or 'HISTORICAL REPORT' in status.group(1) or 'ARCHIVED' in status.group(1)))
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
    print("Scope: status labels, inline local links, duplicate documents, and canonical paths.")
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main())
