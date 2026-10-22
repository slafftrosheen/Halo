# Halo alpha acceptance matrix — 2026-10-22

**Source baseline:** `205a30f07d14949b9045678dfe54be1432f20c21` (`main`).
**Overall alpha status: NOT TESTED.** This matrix records end-to-end acceptance,
not whether implementation work exists. A passing source test does not pass a
physical user journey.

| Gate | Status | Current evidence and remaining proof |
| --- | --- | --- |
| Continuous voice, 10+ turns (#33, #38) | NOT TESTED | Home has explicit foreground Voice Mode with capture-correlated re-listen after a completed spoken reply and stop behavior on rejection, playback failure, cancellation, or leaving Home. Notebook and both Study surfaces expose a Start Voice Mode route through Home; pending starts survive the microphone permission prompt. The active session has separate Pause/Resume Listening and routed Pause/Resume Reply controls plus End; Home can replay the last capture-correlated spoken reply in its original conversation and route while keeping the microphone paused. No repeatable physical session record exists; denied-permission recovery, offline/provider transitions, actual playback pause/repeat behavior, interruption reliability, device/RAM, model SHA and timing still need owner acceptance. |
| Safe ask → act → respond (#36, #40) | NOT TESTED | Historical source tests and APK installation are recorded at older revisions; they did not confirm foreground interaction, real-model inference, approvals, side effects, or no-duplicate behavior. A 48-case EN/RU/LV/PL voice/keyboard matrix is prepared in [the classification matrix](HALO-INTENT-150-CLASSIFICATION-MATRIX.md), but every case is NOT RUN. Repeat the full action loop on device. |
| Study: grounded answer, hint, practice, save/resume (#34) | NOT TESTED | No current physical acceptance record demonstrates both Kids and adult journeys from a genuine source through guided practice and resume. |
| Core consumer navigation and final UX (#37) | NOT TESTED | Screen source includes persistent layouts/cards, per-record cache recovery, EN/RU/LV/PL labels for native widgets and dynamic states, bounded Polish add/hide/enlarge/reorder commands, local automation results, sourced research, refresh, read-aloud, archive/restore, ambient settings and a research-to-Notebook handoff. Recent source batches completed the Screen card labels and catalogued dynamic labels; locale generation and the literal UI audit pass. No current physical evidence confirms the full Screen journey, Home/Chat non-regression, accessibility, ambient safeguards, or approved final navigation/design handoff. |
| Setup, model selection, and recovery (#39) | NOT TESTED | Starter-pack storage preflight now accounts for each payload on its destination root and combines needs when roots share a filesystem. No fresh-install, download recovery, restart, offline, permission-denial, or model-removal journey is qualified on device. |
| Privacy, ownership, and provider boundaries (#41, #42) | NOT TESTED | The user confirmed a paid Google Play listing for the base app, targeting about US $4.99 once, with no Halo subscription; #42 records this decision. No current end-to-end evidence covers local-only behavior, cloud consent/denial, provider traffic, billing disclosure, Play Console pricing/availability, purchase/reinstall access, or the applicable refund/support path. |
| Pen input and saved ink (#28) | NOT TESTED | [Ink verification](HALO-INK-028-VERIFICATION-2026-10-22.md) covers source and synthetic pointer tests only. Physical pressure/eraser/palm behavior, saved-v1 visual preservation, tablet layout, rotation, and process-death recovery remain unqualified. |
| Ink as referenced cognitive context (#29) | NOT TESTED | Source includes stable stroke IDs, accessible box selection, persistent Canvas/PDF selection bookmarks, source/page/revision binding, stale-source rejection, bounded crops, and Ask / Add to working set / Study actions. The voice/text turn path snapshots conversation-scoped references at submission and resolves the selected crop against that turn's interaction ID; stale source revisions are rejected. No live mixed voice-and-ink turn has been exercised. Provider/consent boundaries end to end, source fidelity through physical edits/orientation, guided Study quality, and phone/tablet performance remain unverified. |
| Multimodal retrieval (#35) | NOT TESTED | Source has separate local text/image/audio/video indexes, Notebook picture/audio/sampled-video search, Canvas image search, selected Study/text sources, and voice memo discovery/indexing behind explicit local-search selection. Search guidance now describes the four-frame video limit and says video audio is not processed. The 25 literal labels in the Notebook local-search sheet have Polish translations; this does not certify whole-app Polish completeness. Static source review and authored regression cases do not establish cross-modal relevance or physical accuracy/latency/memory. Exact-artifact behavior, cancellation/model switching, and physical qualification remain open. |

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

This update records source state at
`205a30f07d14949b9045678dfe54be1432f20c21` only. Per #43, coding agents do not
run builds, tests, CI/Actions, or physical acceptance; owner/alpha testers must fill the
run artifacts on real devices. No new builds, tests, or physical runs are
claimed here. Historical verification records linked above retain their
original revision and scope.
