# Android acceptance continuation — 2026-10-25

[#55](https://github.com/slafftrosheen/Halo/issues/55) remains **IN PROGRESS**. This continues the historical [2026-10-24 ledger](ISSUE-55-ANDROID-ACCEPTANCE-2026-10-24.md); earlier voice/BT/cloud results belong to their earlier APK fingerprints.

Baseline main for this continuation: `8a66b5e756587650da2671fd489075c2f2ef6d3d`. Physical Redmi23124RA7EO / Android15/API35 / serialf01fe673. Prior tested APK `ff00a95371eb747f3041d0972aa8288b8560412aa8e61323c77a02fb744aeeaf`.

| Actual Android-owned scenario | Status | Evidence / boundary |
|---|---|---|
| Create and rename a literal note; autosave | PASS | `HALO-ACCEPT-20261025-NOTE-A`, exact synthetic body read back |
| Rotate note to actual landscape; force-stop/relaunch/search/reopen | PASS | Exact title and body survived; search had one matching item |
| Create separate named Canvas; link exact fixture note | PASS | `HALO-ACCEPT-20261025-BOARD-A`, one reference card and the intended note title |
| Export Canvas to a new note | PASS | UI readback includes full literal source body and actual source ID `0343d899-48f7-4656-827a-c587e8e9e8a1` |
| Study setup from the exported note | PASS for setup only | Material step displays the exact exported title and Text ready; no tutor request/lesson-quality claim |
| Keep Canvas/reference through APK upgrade | PASS | Exact named board and intended source reference read back after replacement install |
| Workspace Search in landscape | FAIL then FIXED | Before: field below Tools/organize, requiring scroll. After: field immediately visible without scroll in measured rotation1 |
| Three assistant typed/spoken cloud workflows | NOT_RUN here | Manual UI writes are not relabelled as AI or cloud journeys |
| Positive current-binary spoken input / audible output / barge-in | NOT_ESTABLISHED | Private acoustic attempt and previous-fingerprint evidence are in the Interface4/historical ledgers |
| Cloud provider end-to-end, Kids, wider state/permission recovery | NOT_RUN in this continuation | No invented PASS |
| Stylus/tablet/TalkBack/real Play-owner transactions | NOT_RUN / external equipment-owner conditions | No fixture substitutes for hardware/store acceptance |

The grouped presentation recovery moves existing Library controls directly after its header while preserving Hub order/callbacks; uses the shorter Russian Import label; and adds reviewed RU/LV/PL copies for six stable speech-rejection reasons that the real microphone attempt had exposed as untranslated detail. No routing, permissions, sharing, model or cognition owner changed.

Checkpoint13 command:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "com.halo.app.ui.screens.notebook.*" --tests "com.halo.app.ui.screens.home.*" --tests "com.halo.app.language.*" :app:assembleDebug :app:assembleDebugAndroidTest "-Proborazzi.test.record=true" --max-workers=2 --console=plain --continue -I .artifacts/interface4/exclude-unfinished-draft.init.gradle
```

Actual: **309 affected cases, zero failures/errors/skips; both APKs built**. Native actual Main repeated/resumed navigation and isolated Polish large-text/draft/IME context fixture: **2 PASS**. The locale fixture uses memory preferences and does not change the user's language. These tests overlap earlier gates and are not summed as unique coverage. Generated catalogue check:4095 entries per EN/RU/LV/PL; Polish Android resources:4922.

Installed main SHA-256 `6a627749e9cda5b1bb15f15b9ab87b7b9e11cb396bc209c71107565bf9e644ab`; instrumentation `c1654921e7ac3210d6cf4ce48a1f891d7086a9b15d75fe381d788740746a2d42`. Both installed base APKs were read back. This checkpoint used one successful main and one test replacement install. The prior Interface4 checkpoint history remains separately counted.

[Compact sanitized results and artifact identity](issue55/2026-10-25/) retain synthetic fixture content only. Raw UI hierarchies/screenshots remain ignored locally. The source was built with the preserved untracked draft stub present and its unfinished test excluded, as already documented in the [Interface4 provenance ledger](INTERFACE-4-VERIFICATION.md). Neither file was changed in this presentation batch.

Power mode, font1.0, physical density440, free sensor rotation/default fixed policy, accelerometer1/userrotation0, original unset animator scale and existing OS grants were preserved/restored. No paid requests, credential reads, model downloads, new sharing acknowledgement, source-data reset, external message or new permission grant occurred. Test-created note/board/export objects remain clearly named for inspection; existing source objects were not edited.

## Newly reproduced Android-owned P0

On checkpoint13, the empty Home composer accepted the literal unsent fixture `HALO-UNSENT-DRAFT-20261025`; no Send/model/provider request occurred. After background → force-stop → relaunch, the composer was empty: **FAIL_ANDROID_DRAFT_LOSS** ([exact result](issue55/2026-10-25/draft-restart-baseline.json)). Saved note/Canvas objects survived separately. Home and Orb editors currently keep independent rememberSaveable maps; the pre-existing untracked ComposerDraftStore is an unreferenced save/load stub. This presentation commit preserves those files; the next recovery must complete the intended shared owner with encrypted local persistence and stale-write/load/accepted-clear guards. No draft persistence PASS is claimed.
