# Screen workspace — issue #37

Screen extends the existing native layout and local source store. It now has a
responsive two-dimensional grid, independent width/height resizing, long-press
movement, labelled accessibility actions, keyboard equivalents, per-card text
emphasis, bundled font choices, restrained backgrounds and optional translucent
surfaces. Rows expand for actual text; enlarged text reflows to one column while
retaining canonical horizontal geometry. Moving a narrow card on a phone does
not silently widen its saved tablet layout.

Customization stays in a retained preview until Apply. Cancel preserves the
applied layout, Apply rejects stale bases, Undo persists across restart, and
named presets open another explicit preview. Legacy size/configuration/source,
archive and pin fields survive migration. Stable presentation identities also
preserve focus when legacy duplicate-source cards are reordered.

Clock, calendar, tasks, Notebook/Study, routine outputs and sourced research keep
their existing owners. Device state observes the real Android battery status.
Weather/interests retain Needs setup until an actual source exists. Layout
proposals use bounded registered native changes with independent selection;
research executes only after explicit Apply through the existing handoff.
EN/LV/RU use native resources; unsupported PL additions use the honest fallback.

Desk mode remains in-app, with an explicit Exit, default private-card masks,
scoped secure-window protection, dimming, charging keep-awake, battery/thermal
limits, lifecycle handling and restrained burn-in movement. It adds no AOD,
overlay, hotword, provider, executor or approval owner. Screen reuses #46's orb
and #45's canonical tools catalog. Quick note uses Home's existing skill and
Notebook paths and returns to Screen from the editor.

## Verification and corrected findings

Source and regression cases were completed and independently reviewed before
each serial build/test gate. Review corrected draft loss on opening Appearance,
the Compose custom-actions API, reorder focus identity and canonical width
preservation. Native inspection then found dark translucent-card text inheriting
the wrong content colour; explicit onSurface content and header visibility
assertions fixed that case.

Physical quick-note testing found two existing approval-path defects. Home's
coroutine returned at WaitingForInput and missed navigation after approval.
The host now observes only that original run, requires a confirmed result for
the exact tool/call and an existing note, and never repeats execution. Durable
refusal releases that presentation wait.

The `bee36a54…` APK then **failed** the real Decline check with an
InteractionSurfaceCoordinator assertion against its pre-dismissal snapshot.
Reconciliation now accepts that resolved-last-request ordering. The canonical
native resume guard also accepts exact durable refusals, allowing the existing
tool authority to record rejection before dispatch and terminalize the run.
Matching invocation, tool, arguments digest and a canonical resolution remain
mandatory. Regressions cover all four resolutions, zero dispatch on refusal,
malformed/unrelated evidence, continued projection and replay prevention.

The final combined gate passed in **10m 23s**:

- **128 app tests, 20 classes**, zero failures/errors/skips.
- **13 runtime tests, 3 classes**, zero failures/errors/skips, including native
  authority, approval contracts and surface reconciliation.
- **24 native renders**, including ten Screen layouts/sheets. Screen inspection
  covers light/dark, RU/LV 200%, wide placement, short landscape, edit controls,
  privacy masks and permission/setup/offline/error states.

The gate used one worker, no parallel tasks, a local 3 GB heap and native
Roborazzi recording. No GitHub Actions were used. No source changed after this
gate. The manifest records the compiled app and runtime/cognition Git trees.

Final APK: **292,026,581 bytes**, SHA256
`dcf574348846ba3ad771104a5686aab4fdbdece74e719af8fc8a67c2ac4da991`.
Installed read-back matched on Samsung SM_G990B2, Android API 36. The binary is
not committed.

Evidence: [manifest](artifacts/SCREEN-WORKSPACE-037-2026-10-23/manifest.json),
[test XML](artifacts/SCREEN-WORKSPACE-037-2026-10-23/tests),
[native renders](artifacts/SCREEN-WORKSPACE-037-2026-10-23/renders), and
[sanitized phone observations](artifacts/SCREEN-WORKSPACE-037-2026-10-23/device/device-observations.json).

## Named phone observations

On the earlier verified `74907a83…` APK, the existing four-card/version-5 layout
opened after upgrade. A clock resize followed by Settings → Appearance retained
the pending change. Saved layout bytes stayed identical before Apply. Applying
Atkinson/Mint/height 2, force-stopping and restarting preserved presentation;
persisted Undo restored original source/configuration fields. A temporary named
preset saved, opened in preview, cancelled without changing the applied layout,
and was removed. A real resize drag independently saved width 6/height 1.

That APK also showed private masks and reachable Exit in portrait and confirmed
landscape/200%. Window read-back showed secure protection and nighttime dimming.
On USB charge, battery 100%, thermal status 0, explicit keep-awake enabled the
flag. Background released keep-awake and brightness while retaining secure
protection; Exit released secure protection. The charging setting was restored.

On the **final `dcf57434…` APK**:

- Decline survived; a new explicit tap reached approval. Approval created one
  confirmed note and opened the canonical editor. Back returned to Screen.
  Runtime records distinguish rejected and confirmed outcomes.
- Physical width resizing, taller sizing and long-press movement saved clock
  column 6, row 1, width 6, height 2, with all four cards retained. The geometry
  survived process restart; Undo restored the original presentation.
- Atkinson/Mint persisted through the real app Dark/System theme changes.
  Dark Screen remained readable at approximately 412dp/200% text, with settings,
  orb and shortcuts visible. Ambient Exit worked there and in confirmed
  rotation-1 landscape/200%. Screen → Find opened the canonical tools catalog
  without executing a tool.
- One transient UIAutomator null-root restart capture was rejected, then a fresh
  hierarchy succeeded. Missing/offscreen control matches were also rejected;
  later actions used fresh valid nodes.

Original four-card source references/configuration, default styling/geometry,
System theme, orb selections, charging preference and OS settings were checked
again: physical density 480/no override, font 1.0, rotation free, auto-rotation 1,
user rotation 0. The two empty test notes were moved to Trash through the normal
UI and checked by their exact recorded identities. The test query was cleared.

The most recent test-style Undo entry and one suspended record from the earlier
crash remain as test artifacts. A guarded attempt to clear only test preference
keys stopped when the background process restarted; **no raw preference writes
were performed**. No original record was reset or deleted. Read-only temporary
database copies were removed. Raw personal hierarchies, screenshots and window
dumps remain outside Git.

## Qualification limits

Real low-battery/severe-thermal behaviour, prolonged burn-in/power endurance,
physical TalkBack/keyboard use, locked-device capture/privacy, live calendar and
provider-backed research, and actual microphone/voice/interruption/Stop sessions
remain **NOT TESTED physically**. Unit/native tests cover their bounded policies
and controls; that does not establish hardware/provider qualification. One
synthetic Stop draft was cleared without a confirmed native Stop outcome and
is not accepted as a Stop test. No provider question or spending approval was
sent. Two one-time local empty-note approvals were exercised.

The implementation and these named observations do not claim system AOD,
overlay feasibility, hotword availability, production/provider qualification or
release acceptance. Remaining qualification stays explicit in #37 and #43.
