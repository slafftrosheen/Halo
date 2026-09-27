# Notebook and Study reference mining

Status: SOURCE-REVIEWED IDEAS / PLANNED, NOT IMPLEMENTED. 2026-09-28.

Next product target requested by the user: Notebook as an all-in-one editor and Study as
a strong learning companion. This pass mines the supplied archives, reconciles Halo's actual
source and produces [RFC 0002 and its delivery sequence](../rfc/0002-notebook-editor-and-study.md).
It does not implement features or claim runtime acceptance.

## Evidence and scope

Halo baseline: `87bdba84` plus the existing local working tree. Existing uncommitted runtime,
phone-control and mining work is preserved. References are the local extracted projects in
`build/refs/OneBox-main` and `build/refs/zafiro-main`; no external source is copied or imported.
The user's “openboz” reference is interpreted as the supplied OneBox archive, consistent with
the previous mining project. The user subsequently added
[Enlighten](https://github.com/mohammedfazilamer-hash/enlighten); source inspected at commit
`bf50c73ea7651cf0bcb3de1afe6644076b900863`, with a local reference checkout in
`build/refs/enlighten`. No reference application was built or run in this pass.

| Archive | SHA-256 |
|---|---|
| `OneBox-main (1).zip` | `0e649f07c7c87a334eea2b649639a5523c37f56488c925bbbb746dc9c2b80fbf` |
| `zafiro-main.zip` | `6775b582b7b86c48b5c411974408a9a8ad1b6af483618165d14ec55e25fcc571` |

Paths below are relative to the named extracted project. They identify inspected implementation,
not promises taken from a README. Study pedagogy, practice scheduling and assessment design below
are Halo proposals. Enlighten supplies concrete study flows, but its inspected source does not
establish durable spaced repetition, evidence-based mastery or citation-aware whole-document tutoring.

## Harvest decisions

| ID | Inspected reference | Useful evidence | Halo decision / delivery |
|---|---|---|---|
| NS-M01 | OneBox `web/milkdown-editor/editor.src.js` | CommonMark/GFM, history, clipboard, indentation, code and math plugins; selection-aware commands | ADAPT: one document editing surface with real formatting, undo/redo and selection commands; choose renderer through a bounded spike, not a wholesale transplant. NBS-02/03 |
| NS-M02 | Same file, `toggleSourceView`, `setContent`, `updateToolbarState` | Rich/source switching, initialization guard, deliberate empty-content handling and mode-specific controls | ADAPT behavior; reject timer-based readiness as a correctness boundary. Both modes must share one revision and preserve unsupported syntax. NBS-01/02 |
| NS-M03 | Same file, draft functions; `feature/markdown-edit/src/main/java/com/wanbaohe/markdown/edit/webview/WebViewMarkdownEditorState.kt` | Local recovery draft, clear-after-save API; bridge can return empty when unavailable | ADAPT recovery intent, REJECT browser localStorage as durable truth and unavailable-as-empty. Room owns acknowledged saves; missing renderer cannot erase a note. NBS-01 |
| NS-M04 | OneBox `feature/markdown-edit/src/main/java/com/wanbaohe/markdown/edit/webview/MarkdownEditorBridge.kt` | Explicit ready/change/picker/link/clipboard callbacks | ADAPT a small typed host adapter only if a WebView wins the spike. No arbitrary JS commands, paths or generated HTML may invoke runtime tools. NBS-02 |
| NS-M05 | OneBox `feature/ai/src/main/java/com/shifenmiao/ai/upload/AttachmentContentResolver.kt` | Metadata/reference separated from transient media payload; missing cache resolved at use time | ADAPT asset references and explicit availability; strengthen with bounded reads, ownership, source revision and extraction status. NBS-03/04 |
| NS-M06 | Zafiro `app/src/main/java/com/niki914/zafiro/app/ui/content/FullScreenContentEditor.kt` | Full-page content, disabled/loading action and inline errors | ADAPT focus and truthful save/error affordances; a full-page text field alone does not meet Notebook's editor scope. NBS-01/02 |
| NS-M07 | Zafiro `libs/okia/src/main/java/com/niki914/okia/conversation/SessionCodec.kt` and `JsonSessionCodec.kt` | Serializable session snapshot with identity, version and current position; codec separate from storage | ADAPT versioned Study state and exact resume position. Reuse Halo's conversation and journal ownership; do not adopt Okia's conversation engine/tree. NBS-05 |
| NS-M08 | Zafiro `agent-runtime/src/main/java/com/niki914/zafiro/chat/agentic/buildin/impl/ViewImageBuiltin.kt` | Structured distinctions for missing, empty, oversized, unsupported and undecodable media | ADAPT typed ingestion outcomes for learning sources. REJECT arbitrary absolute-path and privileged-shell recovery as the Notebook access model. NBS-03/04 |
| NS-M09 | Zafiro `agent-runtime/src/main/java/com/niki914/zafiro/settings/MemoryMutationResult.kt` | Success, missing and ambiguous are separate outcomes | ADAPT explicit proposal/application and source-resolution results, adding revision conflict and permission denial where needed. NBS-01/04 |

The decision is to borrow capabilities and failure contracts, not either application's full UI,
tool collection, storage model or agent runtime. OneBox's math/diagram affordances are candidates;
scientific correctness, accessibility and offline behavior still need Halo acceptance.

## Enlighten: direct Study reference

All source paths below are relative to `app/src/main/java/com/example/studyreader/` at the
[inspected commit](https://github.com/mohammedfazilamer-hash/enlighten/tree/bf50c73ea7651cf0bcb3de1afe6644076b900863).
This is a useful reference for completing a learning flow within one surface. Reuse Halo's
provider, speech, persistence and runtime owners when adapting it.

| ID | Inspected source | Evidence and decision |
|---|---|---|
| NS-M10 | `data/DocumentTextExtractor.kt` | PDF pages render to bitmaps for local Latin-script OCR, report progress and cap pages/text; DOCX reads bounded document XML with external entities disabled. ADAPT local ingestion/progress and plain-text DOCX import, but retain page/region provenance and add total pixel/byte/time budgets. Prefer native PDF text where available; OCR is a fallback. NBS-03/04 |
| NS-M11 | `ui/main/SpeechText.kt` and `ui/main/MainScreen.kt` | Sentence/chunk offsets plus utterance range callbacks drive highlighting, next/previous and pause/resume; source narration can hand off to an explanation. ADAPT a reading companion through Halo's speech host and resource leases, with opt-in handoff and source-revision/playback-generation checks. NBS-05 |
| NS-M12 | `data/StudySetStore.kt` | Source text, explanation, cards and tutor messages are saved together with identity and timestamps through an AtomicFile/mutex. ADAPT the coherent reopenable study bundle, implemented with Halo Room records/relationships; do not introduce another whole-library JSON store. NBS-05 |
| NS-M13 | `ui/main/FlashcardStudyTools.kt` | Editable cards, hidden answers, learner response, reveal, review-again and self-check affordances. ADAPT interaction flow; its remembered `masteredIds` list is not durable mastery evidence or spaced repetition. Halo labels self-check separately and persists review history. NBS-06 |
| NS-M14 | `data/OllamaClient.kt` | Passage-focused tutor prompt asks for insufficient-information responses; card parser supports JSON and simple text pairs and rejects an empty usable set. ADAPT capability-aware output validation into one typed practice-item contract, bounded field/count budgets and user-reviewed drafts. A grounding prompt is not proof of a cited answer. NBS-04/06 |
| NS-M15 | `data/AiTutorCoordinator.kt` | Result reports execution provider; cancellation is rethrown; Auto tries the computer after phone failure. ADAPT location/failure transparency through existing Halo routing, REJECT silent expansion from phone-only data to LAN. Every fallback must satisfy the original sharing scope. NBS-04/05 |
| NS-M16 | `ui/main/MainScreenViewModel.kt`, `explain` and `updateStudyText` | Source edits clear derived material, but an in-flight explanation later updates current state without a matching source revision. REJECT this race; attach source/session/request identities and discard or quarantine stale results before display, storage or speech. NBS-04/05 |

Specific limitations guide stronger Halo acceptance: PDF OCR joins nonempty page text without
retaining page locators; width-only bitmap sizing is not a total pixel budget; Latin OCR does not
cover Halo's multilingual requirement. DOCX text extraction does not imply Word editing fidelity.
Generation of exactly six cards is a reference prompt choice, not a Halo requirement. Narration
must not turn a stale or unaccepted explanation into automatic speech.

## Halo source reconciliation

Paths here are relative to Halo. Findings are source observations, not reproduced device failures.

| Finding | Source evidence | Consequence |
|---|---|---|
| Notebook exceeds the old NB-01 inventory | `app/.../ui/shell/AppShell.kt` wires note/block/assets, Canvas, file and memory routes; `ManualDependencyGraph.kt` constructs their repositories; `NotebookScreen.kt` opens `WorkingSetSheet` | Do not recreate those foundations from the September 18 delivery table. Audit and extend them. This wiring does not certify their completeness. |
| Competing document writers | `app/.../ui/screens/notebook/NoteEditorViewModel.kt` writes recovery/body and launches block mutations separately; `app/.../data/notebook/NotebookDocument.kt` independently serializes into `notes.body` | Stale body snapshots can compete with newer structural changes; serialize commands through one revisioned owner before richer editing. |
| Serialization and recovery are lossy | ViewModel's private `serialize` differs from `NotebookDocumentCodec.serialize`; `replaceFromText` replaces the document with one paragraph | Recover structure, stable block IDs and selection anchors, not just rendered text. |
| Save metadata and lifecycle checks are bypassed | `RoomNotebookRepository.saveNote` calls `updateNoteBody` then deletes drafts; `NotebookDocumentRepository` also calls that body-only DAO operation. `NotebookDao.updateNote` already offers a metadata/lifecycle transaction but these paths do not use it | Reconcile title policy, modified time, revision, lifecycle and draft acknowledgement atomically. |
| Editor controls overstate operations | `NoteEditorScreen` maps bullet/number/link helpers to a plain paragraph in block mode; `applyPreviewReplace` appends a paragraph; `requestSummary` reads UI body, takes 12,000 characters and calls a provider directly | Implement real selection actions and explicit proposal operations; use committed revision snapshots and existing context/runtime policy. |
| Assets exist, extraction is separate work | `NotebookAssetRepository` stores URI/managed-file assets and attachments; its fallback copy uses unbounded `copyTo`. `WorkspaceContextStore.resolveContent(FILE)` returns filename/MIME | An attachment is not yet evidence that the tutor read its pages. Bound ingestion and implement source extraction with locators. |
| Working Set exists without the proposed typed snapshot | `WorkspaceContextStore.content` returns bounded strings, rather than object/revision/locator records | Extend the current path; do not create a second context assembler. |
| Search implementation already exists | `AppSearchWorkspaceSearchEngine` combines Room hits with an AppSearch adapter | Reconcile update/removal/index failure wiring instead of automatically adding another FTS engine. |
| Study currently reports a start it does not persist | `app/.../di/tools/WorkspaceToolExecutor.kt`, `halo_study_with_halo`: constructs a local `StudySession`, returns success text, stores nothing and starts no tutor. `sdk/.../study/StudySession.kt` only holds metadata/stage | Fix capability truth first. Starting Study must persist and expose a resumable session, not merely allocate a UUID. |
| Study language contract is incomplete | Existing `StudySession` has assignment/explanation language but no strongest-language field | Add the third independent preference during versioned session evolution. |

## Priority change

Notebook/Study now lead the next product mining pass. The previous RUNTIME-047/045b/044b queue
remains recorded in [the runtime mining document](ONEBOX-ZAFIRO-MINING.md); it is not silently
completed or a prerequisite to beginning this plan. Take runtime fixes only when they block a
specific editor or Study acceptance case.

Next concrete batch: **NBS-01 document correctness and truthful Study availability**, followed
by **NBS-02 editor essentials**. The complete sequence, ownership, acceptance gates and deferred
scope live in RFC 0002. Builds and tests were not run for this documentation-only pass.
