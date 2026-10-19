# Halo Round 2 visual alignment РІР‚вЂќ 2026-10-19

## Source and scope

`main` at `e500b2aff470b3791b6d4b1e88b1b6aeae1a2d6d`; `git pull --ff-only` succeeded, already up to date. Existing local work preserved. Authority: `halo-devkit-v1.4/IMPLEMENTATION.md`, Round 2 references and supplied BrandBook 2.3. This pass covers Home and shared header/field geometry; it is not an all-screen pixel-parity sign-off.

## Implemented

- Home: 64dp supplied wordmark, 48dp notification target, 192dp central presence. Actual runtime state remains the source of the title and artwork. Responses/action review and text at 150% or larger use compact presence so content retains space.
- Home: compact editable suggestion chips replace the large introduction and stacked starter cards. Selecting a chip prepares the existing draft; it does not send it.
- Home: one-row pill composer with separate 56dp conversational microphone and stable 48dp Send target. Talk retains the direct conversation callback and does not submit a typed draft or open field dictation. Runtime Stop/retry/review controls remain.
- Shared header: supplied back icon in a 48dp tonal circular target. Shared voice field radius aligns with the specific 14dp v1.4 field token. Existing 64dp floating navigation already matched and is retained.

## Issue status audit

| Issue | Current state | Completion decision |
| --- | --- | --- |
| #33 РІР‚вЂќ runtime voice/CPU/GPU/residency | Open | Leave open. Matched end-to-end baseline/candidate latency, concurrent stage traces, full scheduling/residency/thermal/cancellation acceptance are not established by the existing model smoke tests or this UI pass. |
| #34 РІР‚вЂќ unified Kids/Study | Closed by prior owner request | Preserve the existing closure. `docs/ux/ISSUE-34-IMPLEMENTATION.md` explicitly retains implementation/acceptance gaps, including full authorship history, grounded diagnostic adaptation, complete per-surface voice/read coverage, pre-reader arrival/chapter coverage, localization/theme matrix and all four real-provider journeys. Do not represent closure as full acceptance. |

The earlier incoherent Russian tutor output remains unresolved. The owner cannot test a live spoken turn now; automated dispatch proof is separate from physical recognition/answer quality. No provider/model choices or child privacy policies are changed by this visual pass.

## Verification

The compact-height regression failed against the previous composer (169px at the test density), then passed with the one-row composer. The first broader run passed the Home/Mini behavioral checks and all four new Home renders, but found a translated Send semantics assertion plus three host-dependent old Settings renders. Its workspace snapshot then spun in Robolectric native HardwareRenderer; a thread dump identified that renderer and the worker was stopped. The final focused run is recorded separately after moving Send semantics to the complete button and using the existing hostless Settings test theme. Mechanical UI detector: no findings. Final build/deployment results follow below. Evidence directory: `artifacts/HALO-PIXEL-2026-10-19/`.

## Device visual check

The first packaged visual build installed successfully on Redmi `f01fe673` and launched. `home-visual-check.png` / XML show the real Russian Home state, central ring, compact starter chips, direct microphone, single-row composer and retained floating navigation. The existing task-paused state is displayed truthfully. This intermediate visual check precedes the final Send accessibility-label build; it is not the final installed artifact receipt.

## Final artifact receipt

Final APK built and installed successfully with `adb install -r`; app launched on `f01fe673`. Built APK and installed `base.apk` both SHA-256 `7af3bc4156ad05bd02007cc0259d86d333b578048d5c0e033fd5bc5f23e04e95`. Final screenshot/hierarchy: `home-installed.png` / XML. Android exposes Russian Send on a 132px (48dp) square and Talk on a 154px (56dp) square. No app-data clear, model selection change or hosted CI.

The focused rerun initially passed 86/87 tests; Settings renders passed. An isolated semantics dump showed correct Russian resource labels, but replacement characters in the new test's literals. Root cause: the Python edit used Windows-1251 defaults. The test file is now explicitly UTF-8; this correction does not alter the installed APK. Final focused test result follows.

## Final verification result

Focused app suite: **87 tests, 0 failures, 0 errors**, `BUILD SUCCESSFUL in 2m 12s`. Covers Home presentation/interaction, direct Talk and typed draft preservation, Mini, shared icon sizing, reader recovery, brand Settings and fresh light/dark/large-text renders. Final APK packaging succeeded in the preceding combined run; that run's overall test failure was corrected by this test-only UTF-8 change. `git diff --check` and changed Kotlin/resource UTF-8 checks pass. Final native UI detector has no findings.

No claim of complete screen parity, live speech acceptance, matched runtime p95 improvement or complete Kids/Study acceptance. Changes remain local, uncommitted and unpushed.
