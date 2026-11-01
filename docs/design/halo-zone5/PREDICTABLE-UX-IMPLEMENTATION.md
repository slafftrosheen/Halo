# Predictable Halo implementation

Status: Known local implementation findings closed. Final 217-test host run and 13-test authorized Samsung run pass on the matching installed APK. Independent expert rating: 8.5/10 UX and 9.4/10 visual clarity; 10/10 participant/assistive acceptance remains unestablished.

Started 1 November 2026 from `261e7d681`, branch `codex/predictable-ux`.

Scope: [approved roadmap](ROADMAP-TO-10-2026-11-01.md). Latest user instruction: “u have the phone.finalise the task”, superseding the earlier wait for hardware instruction. Samsung SM-G990B2 / Android 16 is connected. Host evidence does not replace device or participant acceptance.

## Work register

| Batch | Implementation | Current verification |
|---|---|---|
| 0 | Supported task/owner inventory and acceptance matrix below; baseline retained | Implemented; independent baseline and follow-ups recorded |
| 1 | Typed Studio/file recovery; owner-backed saved/draft/unknown wording; Notes structured/source recovery and leave gate; bounded Study answers | Implemented; recovery, failure and interruption checks pass |
| 2 | Visible orb action/activity; shared grouped/searchable Actions; measured 75/25 relocation; full identity reflow; retained Library query/filter/sort/selection/return item; Home/Screen purposes; adult Workspace instrument group | Implemented; expanded host/native checks pass, final wording/tab corrections implemented; matching renders checked |
| 3 | Canonical type routing; direct Notes export; verified external export and explicit outside-app open; Android content-file import; existing local/manual workflows through actual owners | Implemented; immutable picker registration, journal recovery and outside-app dispatch checks pass |
| 4 | Receipt-based completion; persistent dismissal; optional contextual reading; requested task steps and pause/exit; independent guidance amount | Implemented; receipt/disabled/cancel/background checks pass; current-entry disclosure regression passed |
| 5 | Shared press/guide/wallpaper timings; one guide lap then static; task/help priority over wallpaper; existing reduced-motion/contrast/power/thermal/lifecycle gates retained | Implemented; host motion/persistence checks pass; physical timing deferred |
| 6 | Independent findings tracked; final matching host/native/phone verification | Known local findings closed; 8.5/10 UX and 9.4/10 visual clarity. Participant/assistive acceptance remains open |
| Later acceptance | Real phone/system picker/provider/assistive input and participants | Bounded authorized Samsung journeys passed; full participant, assistive, provider and device matrix remains open |

## Supported task and state owners

| Surface/profile | Supported essential journey | Canonical owner and durable finish | Recovery / limitations |
|---|---|---|---|
| Home, local/manual | Type, preserve draft, cancel, return; select a conversation | HomeViewModel, composer/submission and conversation repositories | Recovery is distinct from provider completion; voice/model use requires configured capability and existing consent |
| Screen | Open existing cards, arrange and change appearance | Screen layout/preferences repositories | Unimplemented calendar/alarm capabilities are not counted as successful tasks |
| Library | Search/filter/sort/collection, open typed object, return to retained context | NotebookViewModel + entry SavedStateHandle; Room workspace/organization owners | Last-opened identity is resolved in the retained view; unavailable/deleted items do not clear filters |
| HaloNotes | Create/edit structured text or supported literal source, inspect, close/reopen, export | NotebookDocumentRepository optimistic revisions + recovery record; WorkspaceTextDocuments output receipt | Failed draft does not surrender input to stored observations. Older-base recovery reopens as a conflict. External copy has a separate verification receipt |
| HaloStudio | Manual shape/text/image/ink, layer properties, Undo, checkpoint, reopen, PNG/JPEG export | StudioProjectRepository/StudioService and canonical workspace file owner | Retry preserves original revision; output-creating operations never auto-replay. Interrupted result remains unknown |
| HaloNotes files | Open text/CSV/PDF/image metadata; supported format-specific operations | WorkspaceFilesRepository, WorkspaceFileTools and source version guard | Editable text is a deliberate local copy; images route to Studio. Original source is preserved |
| HaloStudy | Chosen material → goal/style/options → configured lesson → answers/help → resume/review | StudyUseCases/repository and StudyLessonStore recovery | Generation requires available local/provider capability. Bounded, verified recovery protects prior readable answers; model failures do not count as completion |
| HaloMemory | Review/edit/forget saved context with explicit source explanation | MemoryRepository and NotebookMemoryReview owner | Forgetting memory is not represented as deleting the source file; stale revisions and failed mutations stay visible |
| HaloCanvas | Add references/manual content, select/adjust/Undo, keep/reopen, continue/export to note | NotebookCanvasRepository and CanvasEditorViewModel | Linked sources versus generated copies retain provenance and version checks; drawing drafts retain their actual owner |
| Kids | Permitted Type/Help/Stop, learning, child-owned notes/canvas and caregiver handoff | Kids scoped binding, child ownership, real parent gate, Study/Notes/Canvas owners | Adult menu/export/provider permissions are never granted by appearance/help preferences. Complete real Kids journey remains a phone acceptance task |
| Android file handoff | Eligible content URI → owned import → canonical editor; explicit destination export → readback → outside-app open | WorkspaceUseCases import; WorkspaceExternalExport and journal; Android URI permission owner | Cancellation/failed/unknown/verified differ. Journal is never an automatic write queue. External intent excludes Halo and rechecks current authority |

## Verification evidence

Evidence directory: `docs/testing/artifacts/HALO-PREDICTABLE-UX-2026-11-01/`.

- Independent baseline: `INDEPENDENT-BASELINE.md`; follow-ups retain historical findings rather than silently replacing them.
- `fourth-host-results.json`: 95 tests / 14 suites / 0 failures / 0 errors. Includes structured Notes failure/reopen, Study encoded-size preservation, stale Studio retry, export readback/cancellation/restoration, guidance receipts, canonical continuity and native shell layouts.
- `verify-recovery-shell.ps1` records the focused command. `verify-all-host.ps1` runs all app unit tests, debug assembly and Android-test Kotlin compilation. The broad result is recorded below; it predates the final fixes.
- `broad-host-results.json`: 2,388 tests / 501 suites, with one Kids layout failure. That failure was corrected by the measured horizontal Type/Help strip and complete compact identity summary; the test itself was retained.
- `sixth-host-results.json`: 144 tests / 23 suites / 0 failures / 0 errors, plus successful debug assembly and Android-test Kotlin compilation (`sixth-recovery-shell-host.log`). This run includes the two guided inspector/lifecycle regressions. The final wording, tab reflow and current-entry observer changes receive a subsequent run rather than inheriting that result.
- `ninth-host-results.json`: 153 tests / 25 suites / zero failures/errors/skips, with debug assembly and Android-test Kotlin compilation. Includes actual TextLayoutResult density assertions for the external and Notes export dialogs at 200%, local export byte verification, cancellation and preference restoration through a fresh owner.
- Shared dialog content now retains Halo's selected reading scale. The eight uncovered catalog labels and the HaloNotes/Polish wording changes are represented in the canonical translation source; generation parity and all 5,332 Polish Android string checks pass.
- `final-host-results.json`: the final regenerated-translation snapshot passes 153 tests / 25 suites with zero failures/errors/skips; debug assembly and Android-test Kotlin compilation pass (`final-recovery-shell-host.log`, 13m 10s). `final-junit-xml.zip` preserves the XML. `final-capture-manifest.json` ties 36 matching successful testcase captures to source hashes and the APK SHA-256; the release copy matches.
- `INDEPENDENT-PRE-DEVICE-REVIEW.md`: no unresolved local finding in its bounded scope; original rubric is **33/40 = 8.3/10 usability and 9.3/10 visual clarity**. Phone, assistive and participant acceptance remains open.
- `final-broad-host-results.json`: 2,408 tests / 505 suites / one failure / zero errors or skips (`final-all-host.log`, 24m 43s). The only failure expected a separate Kids title instead of the intentionally combined title/status summary. The test was updated to assert the full displayed identity and reject any remaining old lesson text, with no production changes.
- `kids-summary-host-results.json`: all four Kids binding tests pass after that assertion correction (`kids-summary-host.log`, 3m 10s). The correction's source hash and XML are archived separately. All 103 source hashes in the focused manifest still match, and the APK hash is unchanged. The broad run itself is retained as a one-failure result, not relabelled green. No local test failure remains unresolved.
- Catalog generation parity, all 5,332 Polish resource checks, six feature-file key/format comparisons across four languages, changed-document checks and whitespace checks pass. The repository-wide documentation checker separately references an absent baseline `PROJECT-DIRECTION.md`; the scoped document check does not certify that unrelated requirement.
- Native-rendered geometry is host evidence, not physical touch, nonlinear system font or TalkBack acceptance.

## Later phone and participant acceptance

Before each phone session record and restore that session’s actual preferences. Use synthetic local files and distinguish local/manual, provider-enabled, offline/unavailable and child-restricted capability profiles. Run the roadmap’s twelve required scenarios with normal, narrow/short, keyboard, 200% font, EN/RU/LV/PL, light/dark, contrast and motion-off extremes. Verify external Files import/picker cancellation/readback/outside opening, return position, interruption, real Kids ownership and caregiver authentication. Measure actual input acknowledgment and frame behavior; animation constants are not latency evidence.

Recruit by reading, phone experience, vision, dexterity and assistive needs, including child–caregiver pairs. Start formative rounds of roughly 6–8 per selected need pattern. First-use and repeat-use results stay separate. Record actor, profile, allowed support, durable completion, raw counts, wrong destinations, backtracking, time, provider waiting, ease, help use and recovery separately from original task failure. Do not omit unsupported capabilities from the declared profile. Size a separate validation study before claiming population-level completion rates.

The original rubric remains ten usability heuristics at 4/4 and five visual dimensions at 2/2. The historical 7.3/10 usability and 8.0/10 visual review are not updated by assertion. A 10/10 acceptance claim needs independent assessment and the roadmap’s declared evidence; screenshots alone do not establish it.

## Independent finding closure map

Historical findings remain intact in `INDEPENDENT-BASELINE.md` and follow-ups 01–04. These are implementation mechanisms, not inferred participant success.

| Baseline | Delivered mechanism | Meaningful local checks |
|---|---|---|
| R01–R02 | Awaited Notes leave; exact structured/source recovery; failed local input survives stored observations; older-base conflict reopens | NoteStructuredRecoveryTest, NoteEditorViewModelTest, NotebookDocumentRepositoryTest |
| R03 | Typed Studio outcomes, original-revision retry, honest interruption and distinct reopen | Zone5CreativeActionsTest, StudioLocalWorkflowTest |
| R04 | File operation/access/partial/cancel/unknown recovery, retained canonical source | Workspace file/format/recovery suites in broad run; localized recovery resources |
| R05 | Verified bounded Study recovery, encoded-size admission before write and preserved answers | StudyLessonCountTest and broad Study owner suites |
| R06–R07 | Canonical file opening, direct note output, Android VIEW import, explicit verified external copy and outside-app opening | WorkspaceContinuityTest, WorkspaceExternalExportTest, WorkspaceExternalExportHostTest; later real Files journey remains required |
| R08–R09 | Whole measured quarter budget, complete orb action/activity target, equivalent grouped Actions and full identity | HaloZoneLayoutTest, Zone5WorkingAreaTest, HaloZone5InteractionTest, Zone5WorkspaceMenusTest, Kids controls |
| R10–R11 | Saved entry context plus settled-query/late-binding return identity and canonical metadata routing | NotebookLibrarySearchOrderTest, WorkspaceContinuityTest |
| R12–R14 | Owner receipt completion; requested Help; independent guidance amount; current-entry pause/resume and persistent dismissals | Zone5GuideCompletionTest, Zone5PredictableHelpTest, VisualComfortPreferencesTest |
| R15 | Home/Screen purpose labels, adult-only collapsed instrument group, consistent HaloNotes label, complete Polish shell strings | ShellOrbCatalogTest, HaloZone5InteractionTest, native locale fixtures and resource-parity.json |
| R16 | Shared timings, single lap/static guidance, real-feedback priority over ambient, existing foreground/power/thermal/comfort gates | HaloAmbientMotionTest, broad palette/FX tests; physical latency/frame tests deferred |

The local work cannot establish the roadmap’s population completion rates, user comprehension, physical input timing, assistive operation or real Kids end-to-end acceptance. Those are the declared next validation phase, not software features that can be marked successful without observation.

## Continued requirement audit

The prior no-remaining-local-finding conclusion was bounded by the inspected scope. A subsequent direct roadmap audit found additional locally implementable gaps, so it does not establish complete local delivery:

- Roadmap §9 / §14: durable user-selected current-page action pins are missing in active Zone 5; the legacy catalog shortcut branch is bypassed. Implement optional adult-only family-scoped ID preferences in the existing preference owner, presented within Actions and dispatched only through the live lease.
- Roadmap §4: command search currently matches localized titles only; add useful localized presentation synonyms over the current action list without new execution authority.
- Roadmap §5: Canvas pending text/position writes lack observable Editing/Saving/Saved/failed Zone state. Derive state from real persistence, preserve failed pending edits and provide recovery.
- Roadmap §4: visible File tools / Notebook wording remains in continuation/catalog/memory paths. Use HaloNotes for the actual instrument and Library/Workspace for shared-object destinations, retaining compatibility identifiers.

The final source/host snapshots above predate these follow-up changes. They must not be reused as proof of the new implementation.

## Follow-up implementation snapshot (verification in progress)

Q01–Q16 corrections cover adult family-scoped command pins; localized current-command aliases; Canvas owner-derived save states and retry; visible instrument names; destination-specific handoff descriptions; Help on daily pages; one compact save status; exact-revision Studio discard confirmation; retained/revalidated Study selection and read recovery; unavailable-action explanations; busy Canvas admission; semantic selection; discrete Canvas navigation/movement with live child sizing; retained Notes reading/editing positions; and complete wrapped child-page identity.

The independent source follow-up also corrected route reseeding, mismatched Study Start eligibility/reasons, stale pinned/grouped dispatch, Canvas selection after deselect/pan, Notes Read-to-Edit and unapplied source discard, compound search aliases, and the production child-profile path. New regressions exercise actual owners and UI boundaries. Source review found no additional concrete defect in its bounded follow-up; this is not a test pass or a new score.

New host evidence uses `q01-q16-*` names. The first compile preflight and host1 failed compilation and are retained as diagnostic history. Earlier `final-*` artifacts refer only to their recorded snapshot. Phone execution begins only after the current package is built; install will preserve application data.

## Final implementation and authorized Samsung verification

All Q01–Q16, S01–S09 and P01–P06 known local findings are addressed. Final source adds the empty-only Studio/Canvas creation choice through existing owners, stale Canvas organization approval protection and recoverable scope instructions, with selected-card and whole-board recovery regressions. The last uncatalogued Canvas status has four translations.

The frozen final run passes **217 tests / 34 suites / zero failures, errors or skips**, app/test APK assembly, and **47 matching rendered captures**. `phone-final-capture-manifest.json` ties 158 changed-source hashes to APK SHA-256 `01f9d8c407badd602b85399f2ad1bc47bf69aa67ec88e533dd498ca4c42a2673`. The installed Samsung base.apk and release copy match. All **13** bounded physical tests pass (10 native-owner/layout tests and 3 actual MainActivity tests); the earlier Studio entry failure is preserved as history and its unchanged regression now passes. Catalog generation, 5,413 Polish Android strings and current four-feature-file EN/RU/LV/PL key/format parity pass.

The later user instruction authorized phone testing. Actual Samsung journeys cover Notes create/edit/save/cold reopen; TXT export/cancel/retry/readback/external opening; selected Study material and pin continuity through real system 200% recreation; Studio preview/cancel at 200% with IME; and manual Studio create/text-layer edit/Undo/Redo/save/PNG export/external opening/cold reopen. Original font scale 1.0 and temporary pin selection were restored. No app crash was present in the scoped final-run crash buffer. See [phone acceptance](../../testing/artifacts/HALO-PREDICTABLE-UX-2026-11-01/PHONE-ACCEPTANCE.md) for exact artifacts and limits.

The final independent expert recommendation is **34/40 = 8.5/10 UX and 9.4/10 visual clarity**. No known local production blocker remains. This does **not** meet the requested 10/10 acceptance target: participant comprehension/completion, human assistive operation, a real permitted child session and broader device/performance evidence are not established by these checks. Further speculative code changes cannot substitute for those observations. The earlier proposed phone-wait status is superseded; the remaining research/assistive scope is kept explicit.
