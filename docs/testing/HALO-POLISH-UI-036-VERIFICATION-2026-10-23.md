# Complete Polish UI source coverage — #36 / #35

Base main: `ca93d4037ab2ed40de8b8bb53b354df2912d36d2`.

Polish was selectable but most UI strings silently used English fallback. This batch completes the Polish source catalogue and direct Android resources. It changes presentation and localization checks, not model qualification, action authority, provider routing, user/source text or speech capabilities.

## Implementation

- Added the 2,794 missing Polish catalogue entries and 25 missing direct Polish resource files. Existing Polish files remain authoritative.
- The static Kotlin lookup audit found 36 additional labels absent from the catalogue, including one escaped format separator. These are now present in EN/RU/LV/PL.
- Total: 3,997 shared catalogue resources and 781 direct Android strings, **4,778 required strings with Polish coverage**. All 3,961 existing English source keys and their English/Russian/Latvian columns are unchanged; no keys removed. Legacy lookup aliases are recorded separately below.
- The generator requires four nonempty languages and compatible formatting arguments before writing outputs. Indexed argument order may change; unindexed argument order may not. Generated drift is checked without overwriting it.
- `check_polish_localization.py` rejects missing/unknown/damaged/duplicate native Polish strings, incompatible format arguments and uncatalogued static Kotlin `uiText` literals. Interpolated source/model text is outside the static catalogue.
- The Wear capability array is explicitly nontranslatable: its `halo_phone_endpoint` entry is a protocol identifier.
- Existing generated files contained display edits and lookup aliases absent from the old generator source. `ui_overrides.json` now preserves those 66 resource overrides and 22 aliases, including Full Power, Workspace, mode-profile and Quick capture identities. Polish display text follows those existing product terms. Exact XML parity confirms all existing EN/RU/LV generated values unchanged, and all 3,965 existing lookup mappings/resource IDs retained.
- Counts use label-first Polish wording where the existing resource has no plural forms. Product/provider identifiers stay intact. Three catalogue prefix translations intentionally retain trailing spaces, matching their English concatenation role; whitespace checks disable only blank-at-EOL reporting for these source rows.

## Source and desktop evidence

All translation, generator, coverage and regression sources were completed before the Android gate. A read-only review of the initial batch and final additions found no actionable defects; this is not a native-speaker language review.

Passed before Android compilation:

- Full resource/static lookup coverage: 4,778 strings.
- Generator regression fixtures: 8 cases, including display overrides, canonical aliases and invalid targets/arguments.
- Native resource/static lookup regression fixtures: 5 cases.
- Generated output drift and existing EN/RU/LV/key parity checks.

Focused Android gate: **PASS**, 8m46s. All nine cases passed (AppLanguageTest 4, AppLanguageUiTest 2, StudyProgressLocalizationTest 3), zero failures/errors/skips. Both app and instrumentation APKs assembled. The initial build was deliberately cancelled before installation when generated-file review exposed the pre-existing source drift described above; that cancelled attempt is retained separately and is not a passing gate. No CI/Actions.

Raw logs, XML results and exact parity snapshots: [artifacts](artifacts/HALO-POLISH-UI-036-2026-10-23/desktop).

## Physical phone evidence

**PASS on the connected phone**, `f01fe673`, model `23124RA7EO`, Android API 35. App and test installation both succeeded; installed app SHA-256 readback matches the built APK. Android reports MainActivity as the resumed activity after the run.

| Artifact | SHA-256 |
| --- | --- |
| App APK | `484174eecbf9f0b1d9ab9fce548fa74dbdee68d840f06898813c0080ff828ce4` |
| Test APK | `bd4d247379031bbf0df74ae73ef9660c3f8b0a9cda1535fde75ea1491c3f42df` |

Ten native cases passed in 181.217s, zero skips:

- Polish switching at 200% text: compiled shared/native/formatted resources, scroll-to visibility, preserved unsent draft and Activity/picker ownership.
- Five existing workspace cases: PDF annotations/page-image export preserve original bytes; RU dialogs/table at 200%; photo → Studio → Canvas → Study → Chat retains the saved result; registered Canvas/attached-Study owners.
- Three model acquisition cases: session-token trimming/header-injection rejection, real installed-artifact content-URI import/deduplication using isolated copies, unsupported import without orphan partial files.
- Installed inventory: 20 artifacts verified against recorded fingerprints, one existing partial retained, no native model inspection. The export's catalog eligibility flag is not a physical inference qualification.

Separately, the existing Supertonic preview fixture passed in 17.263s: EN/RU/LV voice 0 produced nonzero PCM while the saved pack selection/approval stayed unchanged. This does **not** establish audible voice quality, Polish speech support, parallel pipeline performance or a completed conversation.

Raw installation identities, native results, inventory and launch evidence: [phone artifacts](artifacts/HALO-POLISH-UI-036-2026-10-23/phone). Fixtures use synthetic content/isolated files and do not select new user model roles or perform remote provider calls. The installed app contains the earlier #39/#38/#36/#35/#29/#33 source corrections as well, but these selected native cases are not full acceptance of those issues.

## Acceptance boundary

Polish source/resource coverage can complete the localization implementation item in #36. It does not close #36, #35 or #43: human linguistic review, real model/language suitability, actual spoken action matrix, physical latency/energy/low-RAM measurements, permitted provider flows and other original acceptance requirements remain separate. Do not infer that a translated language is qualified for inference or speech.
