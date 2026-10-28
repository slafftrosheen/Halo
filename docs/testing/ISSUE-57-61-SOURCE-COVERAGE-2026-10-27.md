# Context Bridge, Study and Kids source coverage — 2026-10-27

Live issue bodies inspected: https://github.com/slafftrosheen/Halo/issues/57 and
https://github.com/slafftrosheen/Halo/issues/61. This is a source inventory and implementation
checkpoint, not acceptance or issue closure. All builds, tests, lint, device, speech, provider,
restart injection and accessibility execution are **NOT_RUN**, as requested. Existing uncommitted
work is retained. No commits, pushes, issue mutations, provider calls or downloads were performed.

Paths below are relative to `app/src/main/java/com/halo/app/` unless otherwise stated. Source-ready
means an implementation exists in the stated owner, not that it compiles or satisfies empirical
acceptance. Prior detailed source contracts remain in `ISSUE-57-CODING-2026-10-26.md` and
`ISSUE-61-CODING-2026-10-26.md`.

## Issue 57 work packages

| Package | Canonical source paths and contract | Source status and boundary |
| --- | --- | --- |
| A — source map | `data/WorkspaceContextStore.kt`, `context/AndroidInteractionContextAdapter.kt`, `study/WorkspaceStudySourceResolver.kt`, `data/study/StudyModels.kt`; SDK `StudySession.kt` distinguishes saved object versions and exact note anchors. | Source map available; note copies versus live references remain explicit. Workspace owns files/media; Study stores references, not a second document store. Physical source chains still need qualification. |
| B — anchors | `study/WorkspaceStudySourceResolver.kt`, `study/StudyUseCases.kt`, `ui/screens/notebook/NoteEditorViewModel.kt`; SDK `StudyTextAnchor` and persisted Study source JSON. | Source-ready for genuine saved note selections and supported image/PDF/Canvas/Ink resolver outputs. This batch permits restarting an unchanged anchored lesson while preserving the exact anchor; changed/denied anchors refuse. Unsupported PDF text extraction or absent region capability stays unavailable, never whole-object substitution. |
| C — assembly/receipts | `context/ContextSourceReceipts.kt`, `di/tools/ContextReceiptTools.kt`, `context/AndroidInteractionContextAdapter.kt`; runtime `cognition/context/DefaultContextAssembler.kt`. | Existing bounded references/receipts and permission checks retained. Study source resolution now denies excluded objects before reading their content. No model prose establishes an active lesson identity. Remote negative-case proof remains deferred. |
| D — handoffs | `study/StudyUseCases.kt`, `study/ActiveStudyToolsOwner.kt`, `di/tools/StudySessionTools.kt`, `ui/screens/study/StudyViewModel.kt`; canonical Room repository and Notebook owners. | Source-ready native Study actions retain exact attached-session tokens and use existing owners. New image/page selection and read-aloud controls are callable through the scoped Study tool. New current-view pagination keeps complete JSON below 3800 characters and never cuts IDs or versions. Restart/process-loss/competing-edit behavior requires later execution. |
| E — memory | `data/memory/RoomMemoryRetriever.kt`, `data/memory/PersonalMemoryPersistence.kt`, `ui/screens/notebook/PersonalMemorySection.kt`; runtime `cognition/memory/MemoryRetrieval.kt`. | Existing opt-in Keep/correction/forget and query/dereference revalidation remain the source implementation. This batch does not create memories or rewrite prior user files. Real review→Keep→retrieve→forget qualification remains deferred. |
| F — UX | `ui/screens/study/StudyScreen.kt`, `ui/screens/study/LearningJourneyPanel.kt`, `language/StudyJourneyCopy.kt`, existing source inspector and Notebook selection controls. | Existing source/open-original/stale recovery controls retained. Saved PDF focus and summary locator now preserve the learner's selected page. New selection-persistence notice has EN/RU/LV/PL copy. Screen-reader/font/translation acceptance remains deferred. |

## Issue 61 work packages

| Package | Canonical source paths and contract | Source status and boundary |
| --- | --- | --- |
| A — inventory/simplify | `ui/screens/study/StudyHomeScreen.kt`, `StudyHomeViewModel.kt`, `StudyScreen.kt`, `StudyLearningSetup.kt`; `ui/screens/kids/KidsModeScreen.kt`, `KidsLearningSources.kt`; `data/study/StudyRepository.kt`. | Adult Study and Kids Learn share canonical sessions; Kids Create keeps its existing project owner. Setup is learning configuration, not a developer console. No additional quiz/editor/session database introduced. This inventory is not a claim of a whole-app visual audit. |
| B — probe/depth | `ui/screens/study/LearningJourneyPanel.kt`, `StudyExplanationOptions.kt`, `StudyViewModel.kt`, `StudyLessonStore.kt`; `study/StudyTutorGateway.kt`. | Existing optional baseline, skip, simpler/balanced/deeper, topic branches, persisted preferences and reflection retained. Exact source versions in saved turns are no longer shortened on reload. Actual pedagogical/model quality remains empirical. |
| C — grounded sources | `study/WorkspaceStudySourceResolver.kt`, `StudyUseCases.kt`, `StudyTutorGateway.kt`; `ui/screens/study/StudyViewModel.kt`. | Sources checked before inference and acceptance. New persisted image/PDF focus binds object, version and page; a stale restored focus refuses generation until explicitly selected again. PDF bounds checked. Continue refuses unavailable/denied anchors instead of returning success without refreshing. No OCR or unsupported region inference added. |
| D — voice continuity | `study/ActiveStudyToolsOwner.kt`, `di/tools/StudySessionTools.kt`, `ui/screens/study/StudyViewModel.kt`, `StudyLessonStore.kt`; existing narration/STT/CognitiveSession adapters. | Added select_visual/select_pdf_page/read_source/read_reply and reader play-pause/next/previous/close through the same native lesson. Source reading validates saved source identity/access before opening; stopping invalidates pending reader opens. Persisted focus survives owner recreation without silently changing the page. Real spoken input, audio focus, background/process death and language availability remain NOT_RUN. |
| E — practice/agency | `ui/screens/study/StudyViewModel.kt`, `StudyLessonStore.kt`; `study/StudyTutorGateway.kt`, canonical repository/review scheduler. | Existing reviewed cards, hints, reveal and self-assessment retained. Accepted hint counts and prompt history now require the same image/PDF-page focus as well as source versions/exercise; hints on another page cannot unlock a Kids solution. Legacy unknown-focus history remains visible but is not reused. Provider adherence requires qualification. |
| F — artifacts | `ui/screens/study/StudyViewModel.kt`, `StudyLessonStore.kt`, `study/StudyUseCases.kt`, Notebook/provenance owners. | Existing transactional summary receipt/revision conflict handling retained. Export identifies the actual active PDF page when its version matches. Exact anchors survive fresh lesson creation. Workspace/Canvas/Studio export continues through existing owners. No new export engine or duplicate document database. |
| G — child/privacy | `ui/screens/kids/KidsLearningSources.kt`, `KidsModeScreen.kt`; `study/StudyTutorGateway.kt`, `StudyUseCases.kt`; existing Workspace AI policy. | Intake remains review-first with existing grown-up/media gates. Per-object exclusion now also precedes Study resolver reads. New tool operations do not grant narration/provider/media permissions. Private/cloud, revoke-during-response and external media negatives remain empirical. |
| H — consumer UI | `ui/screens/kids/KidsModeScreen.kt`, `ui/screens/study/StudyScreen.kt`, `LearningJourneyPanel.kt`, `StudyExplanationOptions.kt`, `language/StudyJourneyCopy.kt`. | Existing Learn/Create separation, large child controls, native reader and reduced-motion learning feedback retained; new focus recovery is localized. No unrequested redesign. 360/412dp, 200% fonts, TalkBack and linguistic review are NOT_RUN. |

## Current-view pagination and precise limits

`halo_study_current` accepts optional `collection` (`sourceCandidates`, `attemptIds`, `turnIds`,
`practiceIds`) and `offset`. It includes complete active sources and one collection page, plus
`collectionCounts`, `totalCount`, `nextOffset` and `truncated`. Other collection arrays are empty
on that page; callers must follow the counts and continuation. Recent tutor prose is an explicitly
bounded excerpt. The entire escaped JSON envelope is at most 3800 characters. Metadata that cannot
fit even one item rejects with a native-inspection recovery message instead of corrupting identity
or emitting registry-truncated JSON. Pages reflect current state, not an immutable multi-call snapshot.
The existing candidate pool remains bounded to the first 30 eligible Workspace rows; this is not a
claim of global Workspace search. Saved turn history remains the existing last 30 accepted responses.

## Remaining qualification versus source gaps

No new missing source implementation was identified in the focused contracts above after these
edits. This does **not** establish 100% implementation of every interpretation of either broad issue:
the full cross-tool chains and all interactions need the deferred integration/acceptance review.
Raw PDF paragraph extraction is unavailable where no extractor exists; exact saved note selections
provide the issue's permitted genuine-source route. Unsupported image/Ink regions refuse through the
existing capability boundary. Physical stylus, real provider-language support, OS permission behavior,
cloud zero-outbound evidence and actual process-death results cannot be established by reading code.

Shared Study operability result schemas are integrated by the parent change owner; no DI or shared
descriptor file was edited by this source-workstream owner.

## Follow-up source review corrections

- Tutor and practice generation now use one focus resolver that returns the exact validated PDF
  page. Explicit stale/missing focus throws before either provider path, including single-visual
  lessons. An explicit reselection preserves a page only for the same source version and valid
  bounds; otherwise it chooses and saves the current PDF's valid starting page. No stale page is
  carried into a new source revision.
- Listen then explain now awaits source validation and a successful reader open in the same
  coroutine before starting its tutor handoff. Stop/new selections invalidate pending opens;
  session identity and the opened passage/version are checked. An old reader or unavailable
  narration route cannot stand in for the requested source.
- `data/study/StudyRepository.kt` allows summary artifacts for ACTIVE and COMPLETED sessions,
  matching the native and semantic export controls. Missing/deleted and ARCHIVED sessions still
  refuse; the existing transaction, artifact identity and user-edit conflict checks remain.

These are source corrections only; all execution remains **NOT_RUN**.
