# Polish role qualification source — #39 / #36

Base main `c20de8278f857bd1586a6508046041452157bf34`, fetched0/0 clean. Both the role-fixture owner and host runner previously rejected Polish, preventing a real Polish model qualification campaign despite the selectable UI language.

## Implementation

- Added Polish to the existing15-case FAST/CHAT/REASONING diagnostic fixture owner, including all instructions, lexical answers and corrected conversation history. Same stable case IDs and exact-answer matcher; five cases per role, three repetitions by default.
- Polish inflected answer contracts explicitly request the required grammatical form where needed (day/city names and color choices). Numeric arithmetic, code-copy, grounding, unknown answers and corrected-history outcomes remain equivalent to the existing cases.
- All22 original localized EN/LV/RU argument triplets are byte-for-byte unchanged. No language fallback, model quality or speech claim follows from translated test inputs.
- Extended fixture suite is now2. The existing native runner records that version; host evidence filtering requires suite2 and the exact supported EN/LV/RU/PL language identity. Version1 rows remain historical and cannot complete/resume a current campaign.
- The canonical host runner accepts/defaults to all four languages. Unknown languages and mismatched-language rows cannot supply recommendations; missing Polish roles remain NOT_TESTED.
- Existing native English-only publication into the legacy language-less production qualification store is unchanged. Polish, Latvian and Russian observations stay in scoped exports. No role binding, model selection, runtime owner, inference tuning or new UI controls.

## Verification

All production/regression sources completed before the focused gate. Read-only review found no actionable defects. New tests exercise four-language role coverage, Polish prompts/answers/history and strict scoring, matching PL evidence, wrong-language/legacy-suite rejection, missing-role projection and host runner language/default metadata without ADB execution.

**Focused source gate PASS** (completed2026-10-24, batch started2026-10-23): first Gradle run6m22s, five JVM fixture tests, zero failures/errors/skips, plus production/native-test source compilation. Host evidence regression script passed all four groups: identity/completion, bounded subprocess output/timeout, missing-role/interruption/latest-negative projection, Polish identity/no-language-borrowing/legacy rejection/four-language metadata. [Raw logs, XML and parity](artifacts/HALO-POLISH-ROLE-039-2026-10-23). No APK assembly/install, native model inference, CI or Actions in this batch.

## Remaining acceptance

Phone absent in the current ADB inventory. Real per-artifact/backend/device Polish inference, latency, memory, native-speaker language quality, speech, complete onboarding and the original #39/#36 acceptance remain unverified. Do not close either issue based on this executable qualification support. Rebuild/install both matching APKs before running the updated role suite; the currently installed app predates these Polish role fixtures.
