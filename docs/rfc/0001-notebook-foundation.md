# RFC: Notebook foundation

- Status: IMPLEMENTING NB-01
- Start Date: 2026-09-18
- Target Milestone: Notebook NB-01
- RFC PR: N/A (local implementation)
- Implementation Issue: N/A

## Summary

Add an app-local persistent Notebook foundation without changing Halo's runtime, host,
driver, provider, or SDK contracts. A metadata-only workspace object owns stable identity,
provenance fields and lifecycle; a type-specific note table owns note content. Application
use cases mediate between Compose presentation and a Room repository.

## Motivation

The current app has no durable human-facing workspace. NB-01 needs a useful offline path:
create a note, edit with autosave, find it with lexical search, and manage its lifecycle
after process restart. This foundation must not conflate Notebook content with cognitive
memory and must not make local editing depend on an AI provider.

## Detailed design

### Boundaries

The first implementation stays in the Android app module because that is where the existing
Room database and Compose shell live. `ManualDependencyGraph` remains the composition root.
Notebook does not depend on runtime internals, and device drivers do not depend on Notebook.
Promotion to a reusable module is deferred until a second consumer creates a real boundary.

### Identity, content, and transactions

`workspace_objects` stores a UUID string, object type, title, timestamps, source, lifecycle,
Inbox membership, indexing state and a content reference. `notes` stores body and revision,
keyed by the workspace object ID with cascade deletion. Creation and updates are Room
transactions so metadata and content cannot diverge. Other content types receive their own
tables and repositories rather than growing the note record into a generic blob.

### Autosave and revisions

The editor debounces ordinary changes for 400 ms and flushes when leaving or stopping the
screen. Each committed edit atomically updates metadata/body and increments the note revision.
Room provides crash-safe transaction boundaries; a killed process can lose only an uncommitted
draft inside the debounce window. Durable draft recovery and conflict UI remain NB-01 follow-up
work before full process-death acceptance is claimed.

### Search and indexing

NB-01 search is local SQL lexical matching over title and note body. User `%`, `_`, and escape
characters are treated literally. Search excludes lifecycle states other than the explicitly
selected one. `indexingState` reserves the indexing lifecycle contract; an FTS index, retry
queue, and failure UI are follow-up work before large-workspace acceptance.

### Lifecycle and deletion

Lifecycle is exactly one of ACTIVE, ARCHIVED, or TRASHED. The default list shows ACTIVE only.
Permanent deletion succeeds only for a TRASHED object and cascades to its owned note content.
Inbox membership is orthogonal and may be cleared without changing lifecycle. Future retrieval,
working-set, cache, and export paths must exclude trashed objects by default.

### Imported files and ownership

NB-01 does not import files. NB-03 will store external URI references and persist grants where
available. Removing a reference will never delete an external original. Halo-owned copies will
use private file storage with explicit ownership metadata, never large Room blobs.

### Context and provider sharing

Notebook storage is not runtime memory or automatic context. NB-02 will build immutable context
snapshots from explicit Working Set selection, recording source object and revision. Provider
sharing will be denied by default when cloud context is disabled and evaluated before snapshot
content reaches a provider.

### Device ingestion

NB-04 will accept media through an app use case keyed by a stable capture identity. A unique
ingestion key and one transaction will make retries idempotent. Drivers will publish host/runtime
events and will not write Notebook tables directly.

### Memory correction provenance

The existing Room `Memory` entity remains untouched and is not silently connected to notes.
NB-02 must define a distinct promoted-memory contract with source object/revision, correction
metadata, and forgetting that removes the item from subsequent retrieval.

### Export

Basic note export will use a versioned envelope containing workspace metadata and Markdown body.
Full workspace import/export remains beta. No portability claim is made by this foundation.

## Drawbacks

Keeping the first contracts in the app module postpones reuse and creates a later extraction
cost. SQL `LIKE` search will not scale as well as FTS. Debounced autosave does not yet preserve
the final few hundred milliseconds of an edit under abrupt process kill.

## Alternatives

A single generic Room record was rejected because it couples unrelated content schemas. A new
Gradle module tree was rejected because the current project has one Android database consumer.
Direct integration with cognitive memory was rejected because persistence and promoted memory
have different consent, provenance, correction, and forgetting rules.

## Unresolved questions

- Whether note draft recovery should use SavedStateHandle, a dedicated draft table, or immediate writes.
- When to introduce FTS and how indexing failures should be exposed and retried.
- The final retention window for trashed objects and historical revisions.
- The versioned note export envelope and Android share UX.
