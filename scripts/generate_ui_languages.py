"""Generate the complete EN/RU/LV/PL Android UI catalogue from reviewed UTF-8 translations."""
from pathlib import Path
from hashlib import sha256
from xml.sax.saxutils import escape
import json
import argparse
import re
from xml.etree import ElementTree
from localization_format import format_signature

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check", action="store_true", help="Fail if generated files differ from ui.tsv, without writing files")
args = parser.parse_args()
stale = []

def emit(target, content):
    if args.check:
        if not target.exists() or target.read_text(encoding="utf-8") != content:
            stale.append(str(target.relative_to(root)))
    else:
        target.write_text(content, encoding="utf-8")

root = Path(__file__).resolve().parents[1]
rows = {}
for line_number, line in enumerate((root / "app/src/main/localization/ui.tsv").read_text(encoding="utf-8").splitlines(), 1):
    if not line.strip(): continue
    values = line.split("|||")
    if len(values) != 4 or not all(values):
        raise ValueError(f"ui.tsv:{line_number}: every entry needs nonempty English, Russian, Latvian and Polish translations")
    for language, translated in zip(("Russian", "Latvian", "Polish"), values[1:]):
        if format_signature(values[0]) != format_signature(translated):
            raise ValueError(f"ui.tsv:{line_number}: {language} formatting arguments differ from English")
    en, ru, lv = (v.replace(r"\n", "\n") for v in values[:3])
    pl = values[3].replace(r"\n", "\n")
    if en in rows and rows[en] != (ru, lv, pl):
        raise ValueError(f"ui.tsv:{line_number}: conflicting duplicate translation")
    rows[en] = (ru, lv, pl)

def key(text): return "ui_" + sha256(text.encode("utf-8")).hexdigest()[:12]

# Display terminology and legacy lookup aliases were historically patched directly
# into generated files. Keep those reviewed changes in an explicit source instead.
overrides_file = root / "app/src/main/localization/ui_overrides.json"
overrides = json.loads(overrides_file.read_text(encoding="utf-8")) if overrides_file.exists() else {}
display = overrides.get("text", {})
aliases = overrides.get("aliases", {})
by_id = {key(en): (en, *translated) for en, translated in rows.items()}
for resource_id, translations in display.items():
    if resource_id not in by_id or not translations or not translations.keys() <= {"en", "ru", "lv", "pl"}:
        raise ValueError(f"Unknown display override: {resource_id}")
    for language, value in translations.items():
        if not value or format_signature(value) != format_signature(by_id[resource_id][0]):
            raise ValueError(f"Invalid {language} display override: {resource_id}")
known_text = {resource_id: values[0] for resource_id, values in by_id.items()}
for path in (root / "app/src/main/res/values").glob("*.xml"):
    if path.name != "ui_strings.xml":
        known_text.update({element.attrib["name"]: "".join(element.itertext())
                           for element in ElementTree.parse(path).getroot() if element.tag == "string"})
if any(not label or target not in known_text for label, target in aliases.items()):
    raise ValueError("Unknown catalogue alias target")
if any(format_signature(label) != format_signature(known_text[target]) for label, target in aliases.items()):
    raise ValueError("Catalogue alias formatting arguments differ")

def displayed(en, language, value):
    return display.get(key(en), {}).get(language, value)
def xml(text):
    return escape(text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n"))
for index, folder in enumerate(("values", "values-ru", "values-lv")):
    target = root / "app/src/main/res" / folder / "ui_strings.xml"
    target.parent.mkdir(parents=True, exist_ok=True)
    content = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for en, translated in rows.items():
        # Final brand spelling applies only to app-authored resources. Lookup keys,
        # formatting arguments and user/source/model content remain untouched.
        value = displayed(en, ("en", "ru", "lv")[index], (en, translated[0], translated[1])[index])
        # Preserve explicit reviewed override spelling as it appeared in the app.
        if ("en", "ru", "lv")[index] not in display.get(key(en), {}):
            value = re.sub(r"\bHalo\b", "halo", value)
        content.append(f'    <string name="{key(en)}" formatted="false">"{xml(value)}"</string>')
    content.append('</resources>')
    emit(target, "\n".join(content) + "\n")
polish_rows = {en: translated[2] for en, translated in rows.items()}
target = root / "app/src/main/res/values-pl/ui_strings.xml"
target.parent.mkdir(parents=True, exist_ok=True)
content = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
for en, pl in polish_rows.items():
    value = re.sub(r"\bHalo\b", "halo", displayed(en, "pl", pl))
    content.append(f'    <string name="{key(en)}" formatted="false">"{xml(value)}"</string>')
content.append('</resources>')
emit(target, "\n".join(content) + "\n")
target = root / "app/src/main/java/com/halo/app/language/UiCatalogue.kt"
catalogue_entries = list(rows.items())
catalogue_chunk_size = 300
catalogue_chunks = [catalogue_entries[i:i + catalogue_chunk_size]
                    for i in range(0, len(catalogue_entries), catalogue_chunk_size)]
lines = ['// Generated by scripts/generate_ui_languages.py; edit the reviewed ui.tsv instead.',
         'package com.halo.app.language', 'import com.halo.app.R',
         'internal val uiCatalogue: Map<String, Int> by lazy {',
         f'    buildMap({len(set(rows) | set(aliases))}) {{']
for index in range(len(catalogue_chunks)):
    lines.append(f'        putAll(uiCataloguePart{index}())')
if aliases:
    lines.append('        putAll(uiCatalogueAliases())')
lines.extend(['    }', '}', ''])
for index, chunk in enumerate(catalogue_chunks):
    lines.append(f'private fun uiCataloguePart{index}(): Map<String, Int> = mapOf(')
    for en, _ in chunk:
        literal = json.dumps(en, ensure_ascii=False).replace('$', chr(92) + '$')
        lines.append(f'    {literal} to R.string.{key(en)},')
    lines.extend([')', ''])
if aliases:
    lines.append('private fun uiCatalogueAliases(): Map<String, Int> = mapOf(')
    for label, resource_id in aliases.items():
        literal = json.dumps(label, ensure_ascii=False).replace('$', chr(92) + '$')
        lines.append(f'    {literal} to R.string.{resource_id},')
    lines.extend([')', ''])
lines.pop()
emit(target, "\n".join(lines) + "\n")
if stale:
    raise SystemExit("Generated UI files are stale; run python scripts/generate_ui_languages.py:\n" + "\n".join(stale))
print(f"{'Checked' if args.check else 'Generated'} {len(rows)} EN/RU/LV strings and {len(polish_rows)} Polish UI strings")
