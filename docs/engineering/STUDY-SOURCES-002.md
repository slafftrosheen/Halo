# FEATURE-069 / STUDY-SOURCES-002

Implemented 2026-10-08 on the existing StudyRepository, TutorStage, use cases, review policy, narration and AI router. This is source-grounded teaching, not a separate agent or chat store.

## Material and teaching

Study Home selects up to four distinct active Workspace objects in order, imports through the normal Workspace file repository, or starts a topic without sources. Notes and supported UTF-8 text/JSON files use actual contents. Notes resolve canonical ordered blocks (legacy body-only notes use the editor's canonical initialization). Lexical selection can reach later passages; no embeddings are installed. Each passage is at most 1,600 characters, at most three per source, with a combined 8,000-character source budget. File reads consider at most 2 MiB and 100,000 decoded characters; incomplete material reports partial coverage and invalid UTF-8 is unsupported.

Images require exact verified TEXT+IMAGE model evidence. PDF support counts pages and renders one selected page lazily through Android PdfRenderer, with dimensions bounded at 1,600 before the existing image preparer applies its own limits. A PDF page is visual input: PDF text extraction and OCR are **not installed**. Previous/Next controls choose the actual page supplied. One active image/page accompanies bounded text in a tutor turn; multiple visual sources require explicit selection. No image/PDF bytes, page caches or source snapshots enter lesson persistence.

Source-only is the default for material-backed lessons. Material + general explanation is explicit and the prompt requires separate disclosure. Topic-only lessons use general knowledge and question routing. Learner level controls vocabulary and response length; Guided is default, with Practice and Explain alternatives. Assignment, explanation and strongest languages remain independent. Strongest language is for brief clarification; original assignment terms remain unchanged. The full solution still requires an explicit confirmed reveal. Narration remains opt-in and speaks final text only.

## Provenance and privacy

StudySession schema 3 and Room migration 25→26 add ordered per-source string versions, learner level, support style and knowledge policy. Legacy numeric sourceRevision belongs only to the first historically covered source, never every source. File hashes are strings, not invented numeric revisions. Attempts, feedback, review events and prior lesson replies are retained. Reviewing a card does not update its source identity. Source changes require an explicit continue/restart decision. Removing active material affects future turns only.

The gateway assigns ephemeral S1–S4 markers. Only markers present in the visible final response create citations; unknown markers cannot fabricate provenance and zero markers produce zero citations. Durable citations retain exact object/version, locator, bounded excerpt and title. UI uses human labels and opens the existing source route. PDF citation buttons say the referenced page; they do not claim an external viewer was focused to that page. Citation-backed model feedback remains UNVERIFIED without a deterministic checker or trusted answer key.

Workspace access policy, includeImages, allowCloudImages and allowCloudWorkspace gate requests. Local-only objects cannot become remote requests or remote fallback. Excluded objects are refused. Image preparation happens only after an eligible verified target is found. No tools run in Study, no ambient camera event starts a lesson, and importing does not initiate inference.

External files without a stored hash use stable Workspace record metadata as their version. Editing such a file outside Workspace without a relink/update is not physical content verification and may not be detected. Selected PDF pages are process-local and reopen at page 1. Recent lesson context retains source-origin IDs for access checks; unknown legacy origins are omitted from model context, and removing a source does not allow its historical local-only text to reach a remote provider.

## Practice and operational limits

Create practice cards uses the same source/policy/router path and accepts only a strict bounded JSON batch: at most five cards, question <=500 characters, answer <=1,000, and known supplied source markers. Invalid batches create no cards. Accepted batches are inserted transactionally as unscheduled drafts with exact source version/locator. Keep is explicit. Edit makes the card user-owned; regeneration does not silently overwrite edits. Replacement drafts do not rewrite the original card or review history.

Lesson controls are grouped in Material, Languages, Setup, Schedule and Progress sheets. Text-only Find in material selects lexical passages; it does not pretend to search PDF text. Workspace files expose Study this through the same durable start use case as tools. Passive System Check reports saved lesson/review counts and supported source modes without reading files or generating. Study was not in the existing Brain archive scope; this batch does not invent an archive body or duplicate payload store.

## Validation

Required automated gate: runtime SDK tests, cognition tests, app tests including the 25→26 migration, and debug APK assembly, serial with one worker and no daemon. Regression cases cover later passages, combined budgets, UTF-8 cap/BOM/errors, URI loss, source versions, marker citations, strict drafts, explicit solution gate, learner/language prompts, topic lessons, exact vision eligibility, local-only remote exclusion, one selected page, and historical card identity.

The consolidated gate passed: runtime SDK 48, cognition 216 and app 318 tests, totaling 582 with zero failures/errors/skips. Room migration tests passed and the debug APK assembled. `git diff --check` passed. The APK installed successfully on the development phone and Halo's process started; keyguard was showing with the screen off, so the compact manual feature pass remains pending. Physical provider quality, PDF visual recognition, multilingual teaching and card acceptance are separate evidence, not inferred from fake provider tests or installation.
