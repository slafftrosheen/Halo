# Issue 60 — Screen source coding, 2026-10-26

Baseline: `7257b4706`. Source work only, as requested. No build, test, ADB, spoken-input,
model/provider invocation, commit, push or physical acceptance was performed for this batch.
The issue remains open for those deferred acceptance gates.

## Current-state audit and implementation

| Requirement | Existing implementation retained | Coding in this batch / acceptance still deferred |
| --- | --- | --- |
| A: audit existing surface | `screen/HaloScreen.kt`, `ScreenWorkspaceGrid`, `ScreenWorkspaceChrome`, `LocalScreenStore`, `ScreenLayoutRequests`, `ScreenToolExecutor`, existing shell route | Extended these owners. Historical dashboard implementation was not recreated. No device assertion. |
| B: real native cards | Clock/date from local time; battery/charging from Android; calendar from permission-gated CalendarContract; tasks from daily owner; Workspace and Study from Room-backed owners; routine results from RoutineExecutor publication | Workspace/Study cards retain the exact observed source record and show its real modification timestamp. Targeted cards honor stored IDs, refuse missing objects and reread the authoritative source before navigation. Activity now presents actual Home task/Orb state while work is active. Research preserves actual Failed/Offline/Needs setup states. Weather remains honestly Needs setup without a configured data owner. |
| C: personalization and safe proposals | Native widget whitelist, bounded two-axis grid, accessible move/resize buttons, typography bounds, saved layouts/presets, preview/apply/cancel/reset, configuration-revision checked tool preview tokens | Undo restores presentation with a new monotonically increasing layout version, so an old preview cannot become valid again after undo. Added `follow_up` through the same whitelist; no HTML/JS/code rendering. Native local layout-request aliases include follow-ups. |
| D: same assistant owner | Screen entry callbacks and shell Orb use existing Home/CognitiveSession; Screen tools use existing LocalScreenStore | Observation of Home state starts no turn or recording. Continue rereads Workspace/Study/Routine/Follow-up source through their existing owners, checks the observed revision/record, then checks latest interaction/privacy and OS keyguard before navigation. No second memory/conversation engine. |
| E: ambient/dock | `ScreenAmbientEnvironment`, `ScreenWindowPolicy`: foreground/unlocked visibility, charging opt-in, conservative unknown battery/thermal handling, power-save/low-battery/thermal limits, dimming, local offset, scoped secure flag/window restoration, persistent exit | Retained owners and Android API boundaries. Foreground recording/hotword are never started by new card/projection code. Real portrait/landscape, brightness/window restoration and battery/thermal acceptance are deferred. |
| F: privacy | Default hide-private setting; inactive/noninteractive/locked masking; secure ambient snapshots | Mask transitions also dismiss sensitive topic/layout/detail sheets and cancel pending provider-triggering research UI. All follow-up cards are sensitive; no notification payload enters a public card. Continuation checks fail closed on unknown keyguard state. |
| G: existing results/follow-ups | RoutineExecutor publishes a stable `automation:<routineId>` cache key | Native `follow_up` cards observe persisted `followUpRepository.observeRecentWatches()` directly; one card can bind an exact watch ID, updates from the same Room record and survives restart through the existing layout plus watch owner. Condition matched/readiness is distinct from action completion. Reread guards compare the exact watch record. Parent batch wires the exact `automation?watchId=...` route into the existing AutomationScreen. Routine result publications now persist optional `sourceRevision` from the actual executed routine revision; legacy cache remains readable and opens general automation rather than pretending to possess an exact revision. Automation auto-binding and picker filter actual AUTOMATION sources. |
| H: presentation | Existing native theme, grid font-scale reflow, keyboard/touch controls, reduced-motion owner and localized copy pipeline | New follow-up and stale-source strings have EN/LV/RU/PL resources. Device/font scale/TalkBack/light-dark/tablet captures remain deferred. Existing card presentation was reused. |

## Data and concurrency details

- `ScreenContinuation.kt` holds ephemeral reviewed identity. It copies no document bodies into
  preferences and performs no source mutation. Routine resolution uses the read-only management
  getter; source revision is the producing routine's actual `updatedAt`, not a guessed current revision.
- `ScreenItem.sourceRevision` is an optional appended field for backward-compatible JSON. Failed
  refresh keeps the last successful result and its producing source revision, retaining provenance.
- `LocalScreenStore.markFailed` now reads and writes under the same upsert lock, preserving the
  latest successful payload during concurrent publication. Persisted item reads and summary,
  attribution, source identifier/revision lengths are bounded.
- No watch is created, resumed, cancelled or granted execution authority by Screen. Reconcile-only
  watches continue to belong to the watch runtime; a card is presentation, never dispatch authority.
- Existing provider, Kids, Private/Full Power, permission and approval owners remain authoritative.
  The new code neither changes those settings nor supplies remote context.

## Deferred acceptance

`NOT_RUN` by user instruction: Kotlin/resource compilation, automated checks, process-death and
restart behavior, stale-preview/undo/concurrent-source scenarios, typed versus actual spoken layout
requests through the registered runtime, actual routine/watch execution, permission denial/revocation,
keyguard/dock transitions, 360/412dp/200% fonts/TalkBack, tablet, portrait/landscape, device captures
and physical battery/thermal measurements. Code presence is not proof these gates passed.

No universal Android AOD, launcher, keyguard replacement, remote Screen sync or hardware acceptance
is claimed. Optional weather/reading source setup stays explicit until a real configured owner exists.
