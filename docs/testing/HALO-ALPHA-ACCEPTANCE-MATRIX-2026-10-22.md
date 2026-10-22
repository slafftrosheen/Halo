# Halo alpha acceptance matrix — 2026-10-22

**Source baseline:** `0636f2e384fa781ca23f66311fdf5bf7ed939713` (`main`).
**Overall alpha status: NOT TESTED.** This matrix records end-to-end acceptance,
not whether implementation work exists. A passing source test does not pass a
physical user journey.

| Gate | Status | Current evidence and remaining proof |
| --- | --- | --- |
| Continuous voice, 10+ turns (#33, #38) | NOT TESTED | No repeatable physical session record for the current baseline. Record device, RAM, locale, provider/model SHA, start/stop, barge-in, offline behavior, and timing. |
| Safe ask → act → respond (#36, #40) | NOT TESTED | Historical source tests and APK installation are recorded at older revisions; they did not confirm foreground interaction, real-model inference, approvals, side effects, or no-duplicate behavior. A 48-case EN/RU/LV/PL voice/keyboard matrix is prepared in [the classification matrix](HALO-INTENT-150-CLASSIFICATION-MATRIX.md), but every case is NOT RUN. Repeat the full action loop on device. |
| Study: grounded answer, hint, practice, save/resume (#34) | NOT TESTED | No current physical acceptance record demonstrates both Kids and adult journeys from a genuine source through guided practice and resume. |
| Core consumer navigation and final UX (#37) | NOT TESTED | Issue #37 remains planned; this checkout has no acceptance evidence for the Screen journey, Home/Chat non-regression, or the approved final navigation/design handoff. |
| Setup, model selection, and recovery (#39) | NOT TESTED | Issue #39 records source progress, but no fresh-install, download recovery, restart, offline, permission-denial, or model-removal journey is qualified on device. |
| Privacy, ownership, and provider boundaries (#41, #42) | NOT TESTED | No current end-to-end evidence covers local-only behavior, cloud consent/denial, provider traffic, billing disclosure, or restore/refund flows. Billing product choice is still unresolved in #42. |
| Pen input and saved ink (#28) | NOT TESTED | [Ink verification](HALO-INK-028-VERIFICATION-2026-10-22.md) covers source and synthetic pointer tests only. Physical pressure/eraser/palm behavior, saved-v1 visual preservation, tablet layout, rotation, and process-death recovery remain unqualified. |
| Ink as referenced cognitive context (#29) | NOT TESTED | Portable selection and stale-reference rejection are in source at this baseline, but selection UI, source/page binding, Ask/Add/Study actions, privacy-controlled crops, and voice/Study integration are incomplete. |
| Multimodal retrieval (#35) | NOT TESTED | Source now has separate local text/image/audio/video indexes, Notebook image/audio/video search, Canvas image search, and Study retrieval for selected Notebook notes and supported text files. Static source progress and authored regression cases do not establish cross-modal relevance or physical accuracy/latency/memory. Voice-memo-specific integration, remaining consumer coverage, complete Polish localization, and physical qualification remain open. |

## Evidence policy

- Mark a gate **PASS** only after its entire listed user journey is reproduced on
  the stated environment and the user-visible outcome is observed.
- Mark **FAIL** when a required behavior is reproduced and fails. Keep the exact
  revision, device, locale, model artifact, and non-sensitive diagnostic evidence.
- Mark **BLOCKED** only when an external dependency or owner decision prevents the
  specific check; name that dependency. Do not use it for work that has not yet
  been attempted.
- Use **NOT TESTED** when no adequate evidence exists. Historical source tests,
  synthetic events, APK installation, or a successful dispatch alone do not
  establish end-to-end acceptance.
- Do not run CI or GitHub Actions. Preserve separate source, emulator, physical
  device, provider, and product-approval evidence.

## Current execution environment

This update records source state only. Per #43, coding agents do not run builds,
tests, CI/Actions, or physical acceptance; owner/alpha testers must fill the
run artifacts on real devices. No new builds, tests, or physical runs are
claimed here. Historical verification records linked above retain their
original revision and scope.
