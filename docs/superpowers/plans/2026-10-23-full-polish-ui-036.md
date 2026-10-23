# Complete Polish UI source coverage #36 / #35

Base main `ca93d4037ab2ed40de8b8bb53b354df2912d36d2`. Polish is already selectable; incomplete resource coverage currently falls back to English. Audited inventory: 4,742 translatable default strings, including 3,961 shared English lookups and 781 direct Android strings. The static lookup audit also found 36 uncatalogued labels; add these in all four languages, yielding 4,778 strings. Preserve existing English keys, resource IDs, formatting arguments, model/user/source text, policy behavior and EN/RU/LV translations.

- [x] Audit the selectable locale, authoritative ui.tsv/generator and direct resource files.
- [x] Complete all missing Polish columns in authoritative ui.tsv, using consistent plain Polish and existing product names.
- [x] Complete all translatable direct Android resource files in values-pl; preserve Android formatting/escaping and brand spelling.
- [x] Require all supported locales and matching formatting placeholders in the generator; author coverage/drift regressions and focused Android locale/UI cases before gates.
- [x] Regenerate source outputs, review exact key/format parity, then run a focused validation/build gate after the full source batch is finished. Initial gate cancelled on pre-existing generator drift; corrected source/alias regressions passed before the successful gate (9 Android desktop cases, both APKs).
- [x] Publish-ready code/evidence: 13 Python + 9 Android desktop cases and 11 physical phone cases passed; app installed/readback verified/launched. Keep human language review, real speech/model language matrix and hardware acceptance distinct. Direct main publication follows the completed gate.

Glossary: Workspace = Przestrzeń robocza; Working Set = Zestaw roboczy; Memory = Pamięć; Study = Nauka. Canvas, Studio, Power and provider/model names retain product identity. App-authored brand spelling is halo. Counts use label-first wording when no plural resource exists, avoiding incorrect fixed inflections.

No Gradle build or tests during translation authoring. Source generation is an implementation step. No automatic/online translation service or private document content is used.
