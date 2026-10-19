# HALO-SEARCH-145 / HALO-UX-146 — Private Notebook Search and Home Productization

Date: 2026-10-19. Target: Android `main`.
State: source committed. **No Gradle build, tests, CI or device acceptance run in this batch.**

## What changed

### Notebook and Library: private search

- Added `NotebookLocalSearchSheet` on both Notebook Home and Library.
- Shows *real* active Note and Canvas workspace objects and active Study sessions. Unsupported files, conversations, archived/trash records are not silently treated as embeddable text.
- The source picker is scoped to visible Notebook/Library records (plus active Study). It is intentionally limited to 64 IDs; clear Library filters to widen the candidates.
- A user must explicitly choose installed and physically verified embedding model bytes. `WorkspaceSemanticService.prepareSearchModel` inspects native `ModelInfo.Embedding` metadata, confirms CPU TEXT support and valid dimensions, then records a precise embedding role. This is an on-demand user operation, not a benchmark or an automatic download.
- The user must explicitly choose source IDs, allow access and tap **Allow sources and build local search**. This reuses the existing `LocalSemanticIndex` and `FileSemanticIndexStore`, with native memory arbitration, document revision checks and fingerprint-bound embedding-space identity.
- Query results show bounded source-backed text excerpts; opening a hit navigates through the existing Notebook/Study navigation owner to the original source, not a synthetic preview document.
- **Turn off local search and clear index** revokes permission and deletes cached vectors. Updating a source allowlist prunes cached unselected chunks synchronously. Switching between incompatible embedding model/dimension identities also clears the obsolete index.
- Persistent search preferences/roles are distinct from private index data and source content.
- User-facing query/search, busy/cancel, missing model and privacy/recovery copy has reviewed EN/RU/LV translations in `ui.tsv` and regenerated resource/catalog entries.

### Separate semantic permissions

- Search permission (`semantic_enabled` plus `semantic_source_ids`) allows only explicitly selected local indexing/search.
- New `semantic_assistant_context_enabled` is **false unless separately opted into**. It permits excerpts from the selected local search index to be used in local Halo answers.
- `CognitiveTurnPreparer` already calls the semantic-context callback only when the target execution location is `ON_DEVICE`; remote replies do not receive automatic semantic excerpts.
- Disabling search resets the assistant-context bit and clears stored index data.
- Existing `WorkspaceEmbeddingRoutingTest` now asserts that search consent alone yields no automatic chat context, then tests explicit assistant consent and full revocation.

### Home

- Response mode selector moved from a prominent technical Home widget to user-facing **How Halo helps** inside the pinned composer Tools menu. AUTO/ASK/PLAN/AGENT selection continues to use the existing runtime state; changes are not admitted during active voice/model interaction.
- Chat feed follows the latest response only while the user remains near the bottom. Explicit upward scroll releases auto-follow; returning to the bottom resumes it.
- Earlier HALO-UX-143 work kept attachments, Tools and the input permanently accessible at the bottom and showed recent conversation turns directly.

### Tests added as source, not run

- `NotebookLocalSearchSourcesTest`: only active, text-backed Note/Canvas records are offered; duplicate/unsupported/trash rows excluded; user-facing errors do not leak raw private paths.
- `WorkspaceEmbeddingRoutingTest`: new separate assistant-context consent and revocation assertion.
- Localization source/generated map and EN/RU/LV XML counts were checked for structural consistency using repository reads, not a build.

## Known limitations / next work

1. EmbeddingGemma **2** publisher supports text/image/audio/video, but Halo's `LiteRtEmbeddingProvider` still executes only `InputData.Text`. Native cross-modal adapters, image/audio/video decoders, index-space metadata and actual SDK/device qualification remain tracked in **#35**. Do not show those as working in the UI.
2. Source chooser currently covers active notes, Canvas text nodes and accepted Study practice content. PDF binary parsing, visual attachment embedding and media retrieval remain distinct tasks.
3. Runtime/physical acceptance: native metadata read on actual devices; phone memory pressure; cancellation during indexing; multiple source revisions; delete/move-to-trash; mute/voice use during search; disable and reenable; model upgrade; process death; deep links from results; sharing constraints.
4. Polish is supported in the Supertonic voice stack but the existing global Android UI translation generator only publishes EN/RU/LV. **Full Polish UI localization is not complete**.
5. Release polish: test large font/small devices; keyboard insets and the 620dp search bottom sheet; prevent unexpected automatic scroll when user reads history; audit strings introduced before HALO-SEARCH-145 and update the brandbook copy.
6. No backend/CI workflow change. Phone testing is left to alpha testers and project owner.

## Source references

- `app/src/main/java/com/halo/app/ai/WorkspaceSemanticService.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NotebookLocalSearchSheet.kt`
- `app/src/main/java/com/halo/app/ui/screens/notebook/NotebookScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/localization/ui.tsv`
