"""Check Polish Android string coverage and argument compatibility without building."""
import argparse
import json
import re
from pathlib import Path
from xml.etree import ElementTree
from localization_format import format_signature


def check_static_lookups(root):
    """Catch literal uiText labels missing from the shared translation source."""
    source = root / "app/src/main/localization/ui.tsv"
    if not source.exists():
        return []
    known = {line.split("|||")[0].replace(r"\n", "\n")
             for line in source.read_text(encoding="utf-8").splitlines() if line.strip()}
    overrides = source.with_name("ui_overrides.json")
    if overrides.exists():
        known.update(json.loads(overrides.read_text(encoding="utf-8")).get("aliases", {}))
    errors = []
    for path in sorted((root / "app/src/main/java").rglob("*.kt")):
        for match in re.finditer(r'\buiText\(\s*("(?:\\.|[^"\\])*")', path.read_text(encoding="utf-8")):
            literal = match.group(1)
            # Interpolated source/model text is deliberately outside this catalogue.
            if re.search(r"(?<!\\)\$", literal):
                continue
            label = json.loads(literal.replace(r"\$", "$"))
            if label not in known:
                errors.append(f"Uncatalogued UI label in {path.relative_to(root)}: {label!r}")
    return errors


def read_strings(folder):
    strings = {}
    required = set()
    for path in sorted(folder.glob("*.xml")):
        for element in ElementTree.parse(path).getroot():
            if element.tag != "string":
                continue
            name = element.attrib["name"]
            if name in strings:
                raise ValueError(f"Duplicate string {name} in {folder.name}")
            text = "".join(element.itertext())
            if not text.strip() or "\ufffd" in text:
                raise ValueError(f"Empty or damaged string {name} in {folder.name}")
            strings[name] = text
            if element.get("translatable", "true") != "false":
                required.add(name)
    return strings, required


def check(root):
    resources = root / "app/src/main/res"
    english, required = read_strings(resources / "values")
    polish, _ = read_strings(resources / "values-pl")
    errors = check_static_lookups(root)
    errors += [f"Missing Polish string: {name}" for name in sorted(required - polish.keys())]
    errors += [f"Unknown Polish string: {name}" for name in sorted(polish.keys() - english.keys())]
    for name in sorted(required & polish.keys()):
        if format_signature(english[name]) != format_signature(polish[name]):
            errors.append(f"Polish formatting arguments differ: {name}")
    if errors:
        raise ValueError("\n".join(errors))
    return len(required)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    options = parser.parse_args()
    print(f"Checked {check(options.root)} Polish Android strings")
