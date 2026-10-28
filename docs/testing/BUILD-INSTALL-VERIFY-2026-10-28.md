# Build, install and verification — 2026-10-28

Scope: the current local implementation for open issues #55–#62. Baseline commit: `7257b47069bcc91c993f2a78e6b4e86343f7def6`, with the existing uncommitted work preserved. This report records a verification pass, not issue closure or a clean committed release.

## Delivered fixes

- Fixed the workflow argument compiler error by reading the nullable cross-module property into a stable local value before validation.
- Added the missing “Selected passage” entry in English, Russian, Latvian and Polish and regenerated the UI catalogue/resources.
- Changed the Screen attention count to the existing localized “Updates: %1$d” label, avoiding “1 updates.” The installed Russian UI displayed “Обновления: 1.”
- Updated obsolete test fixtures for the six workflow disclosure entries, the optional pinned conversation candidate, monotonic undo revisions, exact tutor reply/request identities, and asynchronous reader opening. The offline recovery fixture now uses the public source-review acknowledgement API instead of a private key.

## Build and device

Final build command ran the eight affected app test classes, then `:app:assembleDebug` and `:app:assembleDebugAndroidTest`, serially. Result: **BUILD SUCCESSFUL**, 10m 51s.

Phone: authorized Redmi `23124RA7EO`, serial `f01fe673`, Android 15. Both APKs were installed with `adb install -r`; both returned **Success**. The installed base APK checksum was independently read from the phone and matched the local final artifact.

| Artifact | SHA-256 |
|---|---|
| Final app APK | `f96e736d25650da0a06b1771b6aa0d7c04e0940574dab0dd18207d21ef50cf7b` |
| Instrumentation APK | `412630bb8c96b9e8127f64015af95f87f86b8c55d6c5dda65d710e43c219da18` |
| App APK used for the user-confirmed speech check | `0dc9e9e2541e0bde2ed7a31c95d599a6be822e27f7b34b266385b601fd62f637` |

The final pre-build source witness covers 129 changed/untracked files and was checked unchanged after the build. Since the speech check, production changes were limited to the Screen count label and a trailing blank-line cleanup; voice implementation was unchanged.

## Results

| Check | Result | Evidence boundary |
|---|---|---|
| Full app unit run | 2,206 executed: 2,199 passed, seven failed | Failures identified obsolete catalogue, undo, tutor-ID and reader-timing fixtures. This full run was not repeated after their correction. |
| Corrected/affected app suites | **69 passed**, zero failures/errors/skips | All seven previously failing cases passed in this rerun. Also covers the offline recovery fixture correction. |
| Runtime cognition | **394 passed**, zero failures/errors/skips | Includes the corrected privacy-policy catalogue count and the new workflow contracts. |
| OpenRouter provider | **27 passed**, zero failures/errors/skips | Local provider tests; not live provider qualification. |
| Speech installer | **34 passed**, zero failures/errors/skips | Installer tests; not physical model quality/latency qualification. |
| Localization generator regressions | **15 passed** | Python test suites. |
| Final resource audits | **PASS** | 5,072 Polish Android strings audited; 4,169 catalogue entries checked across EN/RU/LV/PL. |
| Whitespace audit | **PASS** | `git diff --check`. |
| Final APK physical instrumentation | **21 passed**, zero failures, 78.368s | Ring input/accessibility and large-text fixtures; Workspace/Studio/file/PDF/Canvas/Note/Study isolated owners; Polish locale fixture; one real MainActivity navigation test. |
| Actual screen inspection | **PASS for inspected views** | Home, Workspace, Study, Skills and Screen rendered on the phone. Fixture passes do not establish every production journey. |
| Real speech | **PASS, user confirmed** | User followed the “Open Workspace” check and answered: “Workspace opened and I heard a reply.” No separate latency or recorded-audio claim. |
| Actual 200% system text | **Visual check passed** | Compact Home controls remained visible in the inspected Russian UI. Font scale restored to `1.0`. |
| Landscape | **NOT_EXERCISED** | The requested rotation retained portrait; the portrait capture is not landscape acceptance. |

The selected conversation model was `nvidia/nemotron-3.5-lightning:free`. Public OpenRouter metadata checked during this pass reported prompt/completion prices of `0` and tool support. No paid fallback was selected for testing.

## Restoration and remaining scope

Original appearance left by the earlier QA session was restored and read back: Light / Mint / Cool / Halo forms. Automatic rotation was restored to `1`; final system font scale was `1.0`. Existing app data, models and credentials were retained. The current persisted Russian app-language choice was preserved.

Issues remain **KEEP_OPEN** for their complete acceptance matrices. This pass does not qualify every offline model, every language/provider, wearables, every workflow, or landscape operation. No issues were closed and no commit or push was made.

Raw logs, XML results, source witnesses and phone captures are machine-local at `C:\Users\slavd\HaloEco\verification-2026-10-28`. Phone screenshots and user content were not copied into this repository. Key files: `final-build-tests-01.log`, `full-app-unit-results/`, `device-final-01.log`, `source-witness-03.json`, and `free-model-metadata.json`.

Subsequent acceptance, newer APKs, the full 2,206-case rerun, reproduced idle-rendering/Canvas-reference failures, and the latest phone-restoration state are recorded in [the closure acceptance continuation](ISSUE-CLOSURE-ACCEPTANCE-2026-10-28.md). Read that later ledger before resuming device work; this report describes the earlier verification pass.
