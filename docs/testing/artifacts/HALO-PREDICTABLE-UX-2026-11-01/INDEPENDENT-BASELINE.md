Method: dual-agent (A: `/root/ux_ui_review/design_assessment` · B: `/root/ux_ui_review/detector_assessment`)

Status: HISTORICAL REPORT — independent observations at the source snapshot described below.

# Independent predictable-UX implementation baseline

Date: 1 November 2026. Assessed baseline: `261e7d68174758c1708e5f633974903f8bd479a3`. Reviewer: `/root/ux_ui_review`. This is a baseline gap matrix, not acceptance of the implementation being edited concurrently. All line references below refer to the assessed baseline; subsequent edits may move them.

Scope: the [roadmap](../../../design/halo-zone5/ROADMAP-TO-10-2026-11-01.md), [workspace contract](../../../design/halo-zone5/WORKSPACE-TOOL-CONTRACT.md), source owners, and committed baseline images. No phone interaction, provider call, participant testing or production change was performed by the reviewer. Root owns implementation and subsequent verification.

The interface is recognizably Halo: thick open ring, quiet working surfaces, accent-led instrument symbols, companion file ink and a consistent lower command Zone. Preserve that identity. The largest opportunity is trustworthy completion and recovery, followed by clearer commands and retained context.

## Scores and their limits

The evidence-bounded baseline remains **29/40 = 7.3/10 usability** and **8.0/10 visual clarity**, at medium confidence. These are expert-review judgments, not observed population usability rates. No increase is justified by a proposed fix, a source diff, or a clean detector result. No 10/10 assessment is currently defensible.

| Usability heuristic | /4 | Why it is not yet 4/4 |
|---|---:|---|
| System status | 3 | Durable draft, committed version, exported output and unknown outcome are not consistently distinguished |
| Real-world match | 3 | Families are recognizable; Home/Screen purposes and some document wording remain ambiguous |
| Control and freedom | 3 | Canonical Back/Undo exist; Notes has a source-level leave-before-durability hazard |
| Consistency | 3 | Shared action leases exist, but compact groups and outcome vocabulary differ |
| Error prevention | 3 | Revision and child boundaries exist; outcome certainty and interrupted operations need stronger handling |
| Recognition | 3 | Main shell orb is visibly unlabeled; compact commands are a mixed list |
| Efficiency | 3 | Notes export detours and process-recreated Library context add avoidable work |
| Minimalism | 3 | Working area is quiet; compact list and duplicate command locations require scanning |
| Recovery | 2 | Studio and file tools collapse different failures; Notes/Study contain unsupported safety wording |
| Help | 3 | Safe local cue exists; requested explanation and deliberate guided-task branches are missing |
| **Total** | **29/40** | **Same ten-heuristic scale as the roadmap** |

| Visual dimension | /2 |
|---|---:|
| Hierarchy/content focus | 1.7 |
| Typography/readability | 1.8 |
| Control/file/tool recognition | 1.4 |
| Geometry/cohesion | 1.7 |
| Visible state/orientation | 1.4 |
| **Total** | **8.0/10** |

These visual scores describe the inspected historical frames. New captions, recovery panels or grouped actions require new rendered evidence before rescoring. The roadmap's full 10/10 also requires direct task, comprehension, assistive and device evidence. Implementation completeness and missing empirical acceptance must be reported separately without changing that rubric.

## Prioritized implementation-gap matrix

Source paths below are relative to `app/src/main/java/com/halo/app/`, except the manifest. Status is baseline status, not a verdict on concurrent changes.

| ID / priority | Baseline gap and consequence | Canonical source / evidence | Required implementation and meaningful verification |
|---|---|---|---|
| R01 / P1 | Notes Back may leave while required persistence is still owned by the exiting ViewModel. This is a cancellation hazard, not an observed lost-work incident. | `ui/screens/notebook/NoteEditorScreen.kt:285` calls `flush()` then `onBack()`; `NoteEditorViewModel.kt:1207` launches the flush in `viewModelScope`. | Await the durability needed to leave, using the real note owner. Keep the editor and recovery choices if neither current draft nor committed version is confirmed. Test immediate edit→Back, edits during commit, failed recovery write, rotation and reopened latest text. |
| R02 / P1 | Notes claims a draft is kept even when its recovery write has no confirmed receipt; some raw exception text reaches UI. | `NoteEditorViewModel.kt:1065–1077` launches `saveRecovery` without local failure state; `:1178` appends raw outcome text and unconditional local-safety wording. `NoteEditorScreen.kt:538` repeats unconditional safety. | Track current-edit recovery acknowledgement separately from committed revision. Use operation-specific localized errors, preserve input, and expose appropriate retry. Tests must distinguish recovery-write failure, commit failure after a durable draft, conflict, and unknown/interrupted results. |
| R03 / P1 | Studio cannot explain which operation failed; Retry merely reopens a project. | `ui/workspace/StudioViewModel.kt:17`, `:249`, `:298`: `error: Boolean`, reopen-only retry, broad exception collapse. | Typed open/create/edit/save/import/export/restore/source failures, matching recovery and current-object/revision guards. Reopening must not be presented as a repeated export or save. Read-back must substantiate saved/draft claims. Test successful and failed retry, stale callback, unavailable project/source and interrupted outcomes. |
| R04 / P1 | File tools have the same generic outcome problem, including cancellation after possible partial writes. | `ui/workspace/WorkspaceFileToolsViewModel.kt:17`, `:167–176`: error/cancelled booleans around heterogeneous operations. | Map real operation outcomes, including partial/unknown results, into the shared presentation grammar. Reuse existing batch receipts and cancellation refresh; never blindly replay partially completed writes. |
| R05 / P1 | Study's safety wording exceeds current storage guarantees for some drafts. | `ui/screens/study/StudyViewModel.kt:1371` stores card answers only in memory; `:1427` says the text is saved. `:503` draft save has no exposed acknowledgement; `:690` and `:930` promise kept/saved work independently. | Persist recoverable card answers through the existing lesson owner or describe the exact in-memory state. Gate durable-safety copy on matching receipts. Test answer/check failure→recreation, draft storage failure, no-provider path, cancelled turn and resumed lesson. |
| R06 / P1 | Android file entry and chosen-destination completion are incomplete. | `app/src/main/AndroidManifest.xml:238–255` supports shares but lacks `ACTION_VIEW`; `MainActivity.kt:374` handles shares; no `CreateDocument` usage in inspected UI. | Add only supported Open-with/import types, route through current canonical owners, preserve granted access or explain owned copies, and keep child policy authoritative. Add destination picker, safe cancellation, verified output and opening action. Test MIME rejection, cancelled picker, revoked destination, background return and output read-back; actual Files/other-app effects remain a phone gate. |
| R07 / P2 | Note export requires a detour into another surface. | `ui/screens/notebook/NoteEditorScreen.kt:215–218` sends `page.note.export` through `onOpenOffice`. | Use the existing text export owner from the note's supported export choices; retain current editor/revision and return context. Test saved source version, exported copy identity and failed export without false save claims. |
| R08 / P2 | The orb lacks a visible action caption in the branch used by the actual shell. | `ui/presence/HaloZone5OrbLauncher.kt:112–136`; alternate branches have text but inline/compact does not. Confirmed in adult and Kids baseline frames. | Always display callback-derived Talk/Send/Stop/Review wording with separate activity semantics. Measure the whole Zone, not only button alignment. Retain 48dp adult/56dp child targets, exact normal quarter height and content-sized large-text fallback. |
| R09 / P2 | Compact Actions is a mixed flat list; destructive and conversation actions compete with document operations. | `HaloZone5OrbLauncher.kt:197–220`; `accepted-studio-compact-exports.png`. | Reuse File/Edit/Add/View/More groups for instruments and purpose-specific groups elsewhere. Preserve live action leases, unavailable reasons and stable order. Distinguish destructive actions, keep status once, add scoped search only where volume warrants. Test stale owner, disabled action, search results and child allowlist. |
| R10 / P2 | Library filter/query/sort/collection/selection reset after process recreation even though scroll is separately saveable. | `ui/screens/notebook/NotebookViewModel.kt:119–144` uses fresh state-flow defaults; `NotebookScreen.kt:137–143` has saveable list state. | Restore the route's view configuration and reconcile selected/edited item and list anchor after results change. Ordinary same-entry Back already benefits from the retained VM; do not label it wholly broken. Test SavedState recreation and navigation return with filter/sort preserved. |
| R11 / P2 | Files do not always open directly in their supported instrument. | `NotebookViewModel.kt:493–506` routes FILE/MEDIA to generic OpenFile; `ui/shell/AppShell.kt:636–642` repeats type routing. | Route canonical metadata through the existing continuity/type owner, retaining a truthful detail fallback for unsupported types. Avoid inferring format solely from filename. Test supported text/image/PDF and unknown format, source revisions and child restrictions. |
| R12 / P2 | Guidance treats command invocation as completion; opening an inspector can permanently suppress an unfinished step. | `ui/shell/HaloZone.kt:93`; `ShellNextStep` holds only id/action/message. | Branching local guidance tied to entry/object/version and a real owner-confirmed goal. Pause during inspectors; complete after Apply/result/receipt, before old-version invalidation. Revalidate action eligibility and never dispatch because it was highlighted. Test successful Apply, failed Apply, stale object, cancel and dismissal. |
| R13 / P2 | Guidance lacks requested Help and deliberate guided tasks; the inline cue has no visible touch dismiss control. | `ui/zone5/Zone5Guidance.kt`: persisted suppression and fixed 24dp one-line cue with accessibility-only dismissal. | Add persistent requested explanation/example, explicit guided-task start/pause/exit and a visible dismiss route, using existing live actions. Dismissed versions remain suppressed; requested Help stays available. No nudges toward paid/provider/parent/destructive/external actions. Test all suppression states and reopen behavior. |
| R14 / P2 | Shared status vocabulary and independent guidance amount are incomplete. | Owner/status bindings across Studio/Notes/Canvas/Memory/Study; `ui/theme/VisualPreferences.kt` exposes contrast, motion and boolean guide light. | Map actual per-instrument ownership into Editing / Draft kept / Saved / Exported copy / Waiting / Failed / Cancelled / Unknown. Expose independent guidance amount and requested reading; preserve existing font/contrast/motion settings and TalkBack cooperation. Do not invent a common persistence owner or unnecessary Save button. |
| R15 / P2 | Page purpose and direct Workspace access need clarification and consistent wording. | `ui/shell/HaloScreenCatalog.kt` and global menu/launcher presentation; current Workspace Library/Tools flows. | Add literal Home/Screen purpose labels while retaining names/IDs; prototype an adult Workspace group and keep it outside child navigation. Remove visible Office/Notebook/File tools wording when HaloNotes is the actual family. Verify stable menu hierarchy and permission-filtered search/shortcuts. |
| R16 / P3 | Motion tokens and attention precedence are only partial, while most ambient comfort behavior already works. | `ui/design/HaloMotion.kt`; `ui/presence/HaloSignalFace.kt:67,104`; `ui/theme/HaloAmbientBackdrop.kt`. | Consolidate cue/success/interaction timings and ensure recovery outranks hint, which outranks ambient movement. Preserve one-shot idle and existing ~209s, 1–2% wallpaper drift with foreground/power/thermal/reduced/high-contrast gates. Test state clarity with movement disabled. Actual performance still needs devices. |

## Coverage across all roadmap batches

| Batch / area | Existing foundation to preserve | Remaining implementation or acceptance work |
|---|---|---|
| 0 — task inventory | Five families and canonical owners already documented | Record supported formats/tasks and success/failure owners; keep unsupported capabilities explicit. This matrix supplies defects, not a claim that every journey was executed. |
| 1 — trust | Revision checks, local repositories, conflict handling, draft stores, existing batch receipts | R01–R05; operation-specific failures across all exposed workflows and no unsupported safety claims. |
| 2 — shell | Exact 75/25 contract, compact fallback, shared live page lease, tool/file grammar | R08–R11, R14–R15; render the combined caption/status/menu budget at small normal, compact, 200%, narrow and translated extremes. |
| 3 — instruments | Notes structured editor; Studio manual layers/artboard/history; Study source/practice/review; Memory source/scope/correction; Canvas linked cards and board operations | R06–R07 and lifecycle completion across each real owner. Manual workflows need current save→Back→reopen→export evidence. Do not replace existing data models merely to unify copy. |
| 4 — contextual learning | Safe-action allowlist, versioned persisted dismissal, idle/suppression gates | R12–R14; add requested Help/guided branches and real-result completion. |
| 5 — comfort | Shared semantic theme, still background, contrast/less-motion preferences, one-shot idle and gated ambient drift | R16, finite contrast-role checks, new rendered states, and device performance/assistive evidence. |
| 6 — validation | Historical host/native/device artifacts and explicit evidence limits | Fresh host checks/rendered fixtures first; real phone, assistive operation, real Kids, provider-enabled and participant scenarios later. |

Screen calendar/plans/weather/clock/alarms are explicitly separate functional work in the roadmap and contract. Their current unavailable projections must remain honest. No implementation or task-completion credit is assigned to future storage/scheduling/location/notification capabilities. The implementation owner must list their scope explicitly rather than silently counting a placeholder as delivered.

## Existing behavior not to rebuild

- The live `ShellPageActions` lease rejects stale page/object/navigation/metadata dispatch. Search, hints and shortcuts should remain presentation over it.
- Memory already distinguishes approved memories and suggestions, exposes source/scope/cloud implications, and increments successful-save presentation only after repository writes. Preserve these semantics while testing correction/forget/source effects.
- Study already contains sources, goals, material availability/change handling, activities, help, review and progress. The identified recovery issues do not mean the entire learning workflow is absent.
- Canvas already distinguishes linked sources from local cards, and deletion wording preserves linked source items. Its actual mutation/read-back owners should drive status.
- Kids already has draft/kept-project owners and Android credential boundaries. Shared shell presentation must not widen its permissions.
- Independent text size, contrast, less-motion and still-background choices already exist. Guidance amount/requested assistance extends these rather than replacing them.

## Detector and image evidence

Assessment B ran the bundled `impeccable detect --json` once against ten existing Kotlin files: HaloZone, HaloZone5OrbLauncher, ShellPageActions, Zone5Guidance, StudioScreen, StudioPanels, StudioWorkbench, NotebookScreen, NoteEditorScreen and Zone5Workspace. Exit **0**, result **`[]`**, zero reported rules/locations. The tool provides no inspected-file manifest. An empty regex scan cannot prove native geometry, accessibility, persistence or task completion. No false positives were present because there were no findings.

Both assessments independently inspected committed native images. B inspected nine: `accepted-studio-artboard.png`, `accepted-studio-compact-exports.png`, `accepted-studio-landscape-200.png`, `accepted-tools-200.png`, `kids-blue.png`, `kids-compact-200.png`, `kids-parent-boundary.png`, `accepted-tools-dark-mint.png`, `accepted-tools-light-rose.png`. A independently inspected the artboard, compact export and compact 200% Kids frames. All are in `../HALO-ZONE5-DEVICE-POLISH-2026-11-01/`.

The manifest identifies actual-app image source `aa53260fb645` and native fixture source `9dce7a3bc`. Kids images are synthetic fixtures. `accepted-adaptive-results.json` states `no_content_edits: true`. These images support quiet hierarchy, coordinated themes, readable sampled labels, the missing caption and flat compact list. They do not establish current manual file effects or actual Kids-owner recovery. Mint fixture capture anomalies documented in the earlier review remain excluded; complete actual-app Mint/Rose images were used.

Browser DOM inspection and overlay injection do not apply to native Android Compose. No browser overlay, new native render or fresh phone evidence is claimed.

## Cognitive load, emotional journey and personas

Five stable instrument menu groups are understandable organization; their count alone is not a defect. The mixed compact list is the problematic choice load. It combines template, version, export, destructive and conversation commands without hierarchy. The unlabeled orb adds inference. Losing Library context on recreation makes returning work harder.

The quiet work surface supports confidence. Ambiguous saved states and generic errors create the largest confidence drop. A verified output name/location/open action gives the workflow a clear ending.

- First-time manual creator: Add is recognizable; an unlabeled orb and export detour require guessing.
- Large-text/low-vision user: inspected labels wrap, but a mixed compact list requires substantial scrolling. New captions require fresh full-Zone layout verification.
- Child/caregiver: the shared shell is familiar; synthetic controls cannot establish actual Keep→Back→reopen or credential handoff, and requested reading must cooperate with TalkBack.

## Later acceptance gates — not substitutable by code review

1. Real Notes and Studio create/edit/save/Back/reopen/export, chosen destination, outside-app output reopening and cancellation; introduced source/permission/write failures.
2. Actual Kids learning/manual-creation session through its real ViewModel, draft/keep owner and parent credential gate. No gate bypass for evidence.
3. 200% Android text (including actual platform scaling), narrow/short/landscape, keyboard transitions, translations and restored focus. Geometry assertions are useful but do not replace assistive operation.
4. TalkBack, Switch Access/keyboard, reading cooperation, magnification and comfortable touch separation through essential tasks.
5. Theme/contrast/background and active/inactive preference restart persistence, measured foreground/power/thermal motion behavior and input/frame performance on declared devices.
6. Participant task comprehension, first/repeat completion and ease with counts and uncertainty under the unchanged roadmap targets. A successful recovery is reported separately from the initial failed task.

Questions skipped: the implementation mandate and deferred phone-testing boundary are explicit. The next review should inspect the completed change set and fresh host/rendered evidence, preserving the original scoring scales and the limits above.
