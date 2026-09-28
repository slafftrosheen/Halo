# RFC: Notebook editor and Study companion

Status: PARTIALLY IMPLEMENTED IN SOURCE / UNVALIDATED. 2026-09-28.

> Implementation record (source-only, no build or test run):
> - NBS-01 — `NotebookDocumentRepository` is now the single, revision-checked document mutation
>   owner (`NotebookDocumentModel.kt`, `NotebookDocument.kt`); commits are a SQL
>   compare-and-swap that also refuses trashed notes; recovery drafts carry structure, base
>   revision and edit sequence; one codec (`NotebookDocumentCodec.kt`) produces both the block
>   document and the derived `notes.body` projection; the legacy body writer re-routes through the
>   command path when a note owns blocks.
> - NBS-02 — typed `DocumentCommand`/`DocumentReducer` (undo/redo source), find/replace, inline
>   emphasis, list indent/outdent, read/source modes with lossy-conversion refusal, outline, real
>   object references, and a command-driven editor UI (`NoteEditorViewModel.kt`,
>   `NoteEditorScreen.kt`). The bounded renderer comparison is still open: this pass extends Compose.
> - NBS-04 (note-source portion) — AI results are attributable proposals bound to a base revision
>   and selection; application is revision checked, undoable and never silent.
> - NBS-05 (core) — durable Study tables, repository and guided use cases
>   (`data/study/`, `study/`, `ui/screens/study/`), a real `ASK -> … -> SOLUTION` transition policy,
>   idempotent attempts, revision-bound feedback with citations, a review policy with a fake-clock
>   friendly clock injection, and `halo_study_with_halo` now either persists a session or refuses.
> - Still open: PDF/OCR breadth (NBS-03), practice scheduling UI polish and reminders (NBS-06), the
>   renderer spike, and every acceptance gate. Nothing here is device- or test-verified.

- Start Date: 2026-09-28
- Target Milestone: Notebook and Study, NBS-01 through NBS-07
- RFC PR / Implementation Issue: none; local plan
- Evidence: [Notebook/Study mining](../engineering/NOTEBOOK-STUDY-MINING.md)
- References: supplied OneBox/Zafiro archives plus user-added
  [Enlighten](https://github.com/mohammedfazilamer-hash/enlighten/tree/bf50c73ea7651cf0bcb3de1afe6644076b900863).
  Enlighten contributes reading, import and practice flows; the mining ledger records their limits.
- Product authority: [Notebook blueprint](../02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md),
  [Study](../02-product/STUDY.md), [project direction](../../PROJECT-DIRECTION.md)

## Summary

Notebook becomes one place to write, organize, read sources, annotate, work with Halo and reuse
the result. Study turns selected material into a persistent learning session: understand the
task, try it, request help, check reasoning, practice again and resume later. Both use existing
workspace identities, repositories, context policy, conversations and runtime execution.

“All-in-one” means continuity of work across these activities. It does not require a full office
suite, arbitrary executable documents or real-time collaboration in the first delivery.

## Motivation and first complete experience

Open a note or import an assignment -> write alongside the source -> select a passage and choose
Study -> choose explanation language and a learning goal -> answer a diagnostic question -> ask
for a hint -> submit an attempt -> inspect feedback linked to the source -> save an accepted
explanation or practice card -> close Halo -> resume the same exercise with its source and work.

The first Study release must complete this loop with a note as its source. PDF/image support
extends the same contracts; it must not postpone useful text-based learning indefinitely.
Provider failure leaves the source, note, learner attempt and session available locally.

## Current baseline and blockers

Source inspection on 2026-09-28 found notes, typed blocks, attachments, Canvas, file/memory
routes, Working Set and search adapters wired beyond the old delivery inventory. Their
existence is not proof of full acceptance. See the mining document for exact source owners.

Before adding editor features, resolve competing body/block writers, lossy recovery, duplicated
serialization, stale editor body used by summary, missing save metadata/lifecycle enforcement
and toolbar actions that create generic paragraphs rather than the advertised structure.
The Study tool currently returns success without persisting or executing a Study workflow.

## Detailed design

### 1. Document ownership and dependable editing

Extend `NotebookDocumentRepository` and the existing application use cases as the single
document mutation owner. Keep the initial implementation in the current app/storage boundary;
do not introduce a speculative module tree or replace the runtime.

- A document snapshot has object ID, schema version, committed revision, ordered stable block
  IDs, typed content and asset/object references. `notes.body` becomes a derived text projection
  for existing search/context/export consumers, not an independent mutable document.
- Route typing, formatting, reorder, checklist changes, paste, import, AI acceptance and legacy
  body callers through the same command path. Serialize mutations per document and compare the
  expected revision. A stale command returns a conflict with recoverable local content.
- Commit document, derived text, revision, modified time and matching draft acknowledgement in
  one transaction. Reject writes to trashed/deleted documents in the repository, not only UI.
  Keep user-assigned titles; derive a title only under an explicit automatic-title policy.
- Recovery drafts contain structure, base revision and edit sequence. Never clear a newer draft
  while acknowledging an older save. Preserve pending edits on storage failure and renderer loss.
  “Saved” means durable acknowledgement; distinguish saving, recovered, conflict and failed.
- Reuse one codec. Migrate existing body-only notes and block notes deterministically, preserving
  original text and unknown content. Legacy recovery text must not silently flatten newer blocks.
- Selection contains stable block anchors and text offsets. Editor state handles IME composition,
  focus and cursor restoration without treating every database emission as replacement typing.
- Undo/redo groups user actions, includes accepted AI changes and keeps explicit session history
  semantics. Durable revisions/recovery are separate from the undo stack; full revision browsing
  may follow without delaying data-loss protection.

### 2. One useful editing surface

Phone: content fills the screen, a compact selection-aware toolbar sits above the keyboard,
and secondary actions live in a sheet. Larger layouts can show source and editor side by side.
Keep the current five main tabs; Study is a Notebook action/session, not a sixth app silo.
Use current Halo tokens, glyphs and Signal Face; content stays visually primary.

First editor scope: headings, paragraphs, bold/italic/strike, lists and nested lists,
checklists, quotes, code, external links and workspace references, undo/redo, find/replace,
read mode, outline and Markdown source view. Controls operate at the caret/selection.
An object picker inserts a real object reference instead of an `[[object-id]]` placeholder.
Keyboard shortcuts, accessible labels, large text and TalkBack belong to this scope.

Next: tables, inline/display math, images with captions, attachments and source annotations.
Existing Canvas remains a separate object linked from a document; do not embed a second drawing
engine into each text field. Audio/video attachments may open/play before transcription exists;
the UI must describe the actual available operation.

Renderer decision: first run a bounded comparison between the existing Compose editor and an
isolated locally bundled rich editor inspired by OneBox. Evaluate Android IME, TalkBack,
selection, long-document behavior, round trips and maintenance. Prefer extending Compose unless
it cannot meet these gates; no renderer is selected merely because the reference uses Milkdown.
Check primary upstream documentation and licenses before adopting any new dependency.

If a WebView is selected, expose typed ready/edit/selection/save-ack messages with document,
session and revision identity. Room remains authoritative. Load trusted packaged editor code;
document HTML/scripts, remote navigation and embedded resources cannot acquire bridge powers.
Unavailable content retrieval is an error, never an empty document. A renderer crash reopens the
last acknowledged revision plus the recovery draft. The source fallback must preserve unfamiliar
syntax and stable references; disable a destructive mode conversion rather than silently losing
content. Markdown is an interchange view, not permission to flatten the canonical document.

### 3. Sources, assets and portable context

Extend current asset/file repositories; separate reference ownership from extraction.
Imported files keep stable asset IDs, MIME/size, ownership, access state and content fingerprint.
Managed copies use bounded/cancellable staging and atomic finalization with orphan cleanup.
Removing an external reference never deletes its original.

Source extraction is incremental and bounded by bytes/pages/pixels/time. Distinguish queued,
processing, ready, partial, failed, unsupported and access revoked. Keep original sources and
extracted derivatives separate. A scanned PDF requires OCR or an explicit unsupported result;
a filename is not extracted content. Reuse available Halo host/provider capabilities first.

Offer an extraction review before tutoring: view/correct OCR beside the original, retaining the
unaltered source and an attributed correction. Qualify script/language support explicitly.
Plain-text DOCX import is useful alongside PDF/TXT; preserve paragraph provenance, bound archive
expansion/XML work and distinguish content import from preservation of Word layout. Batch image
imports retain order and individual failures rather than collapsing into one anonymous text blob.

A source reference carries object ID, source revision/fingerprint, locator (block/range, PDF
page/region or media time span), excerpt and extraction provenance. Source-origin differences
end here: camera, screen, file, voice and web material enter the same Study input contract.
Mark edited/relinked sources stale rather than quietly retargeting citations.

Extend the existing Working Set/context assembly with typed immutable snapshots and omission
metadata. Budgets select relevant passages and report incomplete coverage; do not silently slice
the first characters and describe that as a whole-document answer. Cloud/LAN sharing uses the
existing target and context policy. Importing a file is not blanket transmission permission.
Lifecycle and sharing are checked again before dispatch and before applying results. Removing a
source excludes future turns; already sent content cannot be retroactively unsent. Cancel queued
work on revocation and prevent late proposals from reintroducing removed data.

### 4. AI assistance as attributable proposals

Select text -> Ask / Rewrite / Explain / Translate / Turn into tasks / Study. Document actions
include summarize, continue, outline and create practice material. Use the existing runtime turn,
context planner, cancellation and journal, replacing direct editor-to-provider calls.

Each proposal records operation, document/base revision, selection anchors, selected source
references, generation identity and proposed content. Show before/after or a distinct preview;
offer Apply to selection, Insert below, Save as new note and Discard as appropriate. Replace must
replace, append must append. Apply is revision checked, transactional, idempotent and undoable.
If the source changed, keep the proposal for review and require re-selection or regeneration.
No model output can choose an arbitrary document target or execute generated UI callbacks.

Bind every generation to session, source revision and request identity. Recheck identity before
displaying, saving or reading a response aloud; changing the active passage must not receive an
older passage's late explanation. Validate generated exercise structure with bounded counts and
field lengths. Model-specific JSON or text formats may normalize to one practice-item contract;
malformed or empty output is a visible failure, never an empty successful lesson.

### 5. Durable guided Study

Evolve the existing `sdk/runtime-sdk/.../study/StudySession.kt` rather than declaring a rival
session type. Keep the portable contract Android-free. Persist learning state through app
repositories; the runtime journal remains execution truth, not a second Study agent engine.

Proposed durable records:

- Session: version, ID, linked conversation/run, goal, source snapshot, independent assignment,
  explanation and strongest-language preferences, lifecycle and current exercise/stage.
- Attempt: exercise ID, learner response, timestamp, help already revealed and submission ID.
- Feedback: linked attempt, source citations, checked reasoning, uncertainty and generation ID.
- Practice item: question/answer or rubric, source revision, user acceptance and review history.

Use persistent transitions and idempotent request IDs. Starting only succeeds after the session
is saved and openable; resume restores the exact exercise and accepted attempt. Process death
during generation is an interrupted turn to reconcile through existing recovery, not authority
to replay submissions or claim a completed lesson. Local session state is available without AI.

Default teaching progression follows the existing product policy:
`ASK -> HINT -> EXPLAIN -> ANALOGY -> CHECK ATTEMPT -> PARTIAL SOLUTION -> SOLUTION`.
Implement this as guided transitions, not an automatic march to the answer. ASK establishes the
goal/prior knowledge; learners may submit an attempt at any stage. They can repeat a hint,
request a simpler explanation, change language, go back or explicitly ask for the full solution.
Store the stage actually used; completion is a separate fact from revealing the solution.

Study should check an attempt before inventing another lecture, identify the step that needs
work, invite self-correction and give one useful next step. Keep original assignment terms when
switching explanation language. Source quotations and generated explanation are distinguishable.
Feedback can say insufficient evidence or needs review; a model's confident verdict is not a
verified grade. Deterministic checks may validate supported exact answers through bounded Halo
capabilities; arbitrary generated code is not executed by the editor.

Present the source, learner work and tutor response together. Wearable/voice input uses existing
host capture and speech routing; confirm ambiguous transcriptions before grading an attempt.
Respect the user's selected output route and allow a silent reading session.

Add a persistent reading panel inspired by Enlighten: read the selected passage or explanation,
adjust speed, pause/resume, previous/next sentence and follow the spoken range. Store the reading
anchor against source revision; utterance/playback generation IDs reject late callbacks. Use word
highlighting only when the speech provider supplies reliable timing, otherwise highlight the
sentence. Locale-aware segmentation uses the material's language. Source-to-explanation narration
is opt-in and waits for a valid current response. Stop, source edit, route loss or session change
cancels the handoff. Implement this through existing speech routing/resource ownership, not a
screen-local competing TTS/MediaPlayer engine. A phone-only session cannot silently switch to LAN
inference or LAN narration after an on-device failure.

### 6. Practice and progress that help learning

After the core loop works, add accepted flashcards, short quizzes, worked-example comparisons,
mistake review and a due queue. Keep generated cards as drafts until accepted, attach their
sources and flag them when sources change. Keep answers hidden until an attempt/reveal.

Start with a small versioned deterministic review policy, tested with a fake clock; record its
inputs and resulting due dates. Let the learner choose pace and skip/retire cards. Notifications
use the existing reminder capability only after opt-in. No new automation scheduler is needed.

Show concrete evidence: attempts, help used, later unaided answers, topics to revisit and due
items. Do not equate chat length, time spent, streaks or one AI-marked answer with mastery.
Study stores session preferences locally; promoting a learner profile into general Halo memory
is an explicit separate action.

## Delivery order and acceptance gates

These identifiers refine the existing NB slices; they do not assert NB-01–05 completion.

| Batch | Work and owner | Exit evidence |
|---|---|---|
| NBS-01 Correctness first | Document repository/use cases, editor ViewModel, codec/DAO migration; Study tool availability | Rapid typing + reorder + late save cannot overwrite the newest edit; recovery preserves block IDs; save metadata agrees; trash rejects all writes. Study reports unavailable until a durable start path exists. |
| NBS-02 Editor essentials | Editor command/state adapter and Compose screens; bounded renderer spike | Real selection formatting and links; undo/redo; list indent/outdent; find/replace; source/read mode round trips; keyboard/TalkBack/large text; no-provider editing. Record renderer decision with measured tradeoffs. |
| NBS-03 Rich material | Document schema + existing asset/file repositories + host rendering | Tables/math/media; owned/reference distinction; revoked source recovery; bounded import/cancellation; original text and assets survive export/reimport. |
| NBS-04 Source-aware assistance | Existing context assembly/runtime, source resolver and proposal use cases | Note passages plus supported PDF/image extraction carry locators; visible partial/unsupported states; correct sharing gates; stale proposals cannot overwrite; accepted edits undo cleanly. |
| NBS-05 Study core | Existing Study contract, app persistence, speech host and Notebook session UI | Create/resume a note-based session; ask/hint/attempt/feedback loop; all three language preferences; source-linked response; offline attempt preservation; truthful tool receipts; revision-bound read-along and opt-in explanation handoff. |
| NBS-06 Practice and review | Study repository, practice UI and existing reminders | Accept/edit cards; quiz attempts and source-backed feedback; deterministic due queue; source revision invalidation; opt-in reminders; export/delete review data. |
| NBS-07 Integrated acceptance | Notebook, Study, runtime and host owners | Full capture/read/write/study/review loop, large sources, process death, privacy/lifecycle, accessibility, device/voice and performance acceptance. |

Build order is NBS-01 -> NBS-02 -> NBS-03/04 -> NBS-05 -> NBS-06 -> NBS-07.
After NBS-02 and the note-source/proposal portion of NBS-04, the note-only NBS-05 loop may ship
before advanced PDF/math work; source extraction breadth must not block that vertical slice.
Canvas polish, collaboration and the unrelated runtime mining backlog do not gate this slice.

## Validation plan

Implementation batches run targeted compile/tests; no build or test was run for this plan.
Use existing Notebook repository/ViewModel/migration tests as starting points. Add meaningful
regressions for reordered writes, stale acknowledgements, failed transactions, restoration,
mode round trips, source revocation and proposal conflicts. Test the real wired repository path,
not only a simplified fake that omits the competing save paths.

Study uses a deterministic fake provider/clock for stage transitions, language preservation,
submission deduplication, interrupted generation, citations, deletion and review scheduling.
Add late-result-after-source-switch, stale speech callback, malformed generated cards, unsupported
OCR script, OCR correction provenance and forbidden phone-to-LAN fallback regressions.
Then check configured local/cloud targets separately; no provider is required for local editing.

Device scenarios: IME composing text, rotation/background/process death, renderer crash if used,
TalkBack, hardware keyboard, 1.3x/2x text, light/dark, cancelled import, no network and revoked URI.
Initial stress fixtures: a 100k-character note, 1,000 blocks and a 100-page PDF with explicit
extraction budgets. Record device, workload, frame/input latency and memory; do not claim smoothness
from code inspection. At each stage record commit, command/scenario, result and remaining limits.

## Drawbacks and alternatives

One mutation owner and revision migration cost time before visible editor improvements, but
adding formatting over competing writers would multiply data-loss cases. A web editor supplies
many commands quickly but adds bridge, IME, accessibility and renderer recovery costs. Pure
Compose preserves current integration but may need substantial selection/rich-text work.
The spike resolves that tradeoff before committing to a library.

Rejected: replacing Notebook with a chat-history screen, adopting either reference runtime,
copying reference source, auto-saving generated answers over learner work, storing files as Room
blobs, building a new vector database before lexical/source retrieval works, and counting a
constructed Study data class as a working session.

Deferred: real-time collaboration, full Word/spreadsheet compatibility, arbitrary HTML or code
execution, public sharing, autonomous courses, full Canvas redesign and generalized scheduling.

## Decisions left to bounded implementation work

The renderer spike chooses the editor engine. NBS-01 documents the migration and revision/draft
retention policy with recoverable original content. NBS-04 qualifies local PDF/OCR capabilities
and budgets on the target phone. NBS-06 chooses and versions the initial review intervals using
observable attempt/reveal history. These do not require another broad product-discovery phase.
