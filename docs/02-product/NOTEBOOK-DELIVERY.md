# Notebook delivery and reconciliation

Status: NB-01 IN PROGRESS. Updated 2026-09-18.

The [user blueprint](NOTEBOOK-WORKSPACE-BLUEPRINT.md) defines Notebook as the persistent
cognitive workspace and user-facing management of Halo's information and memory.
Use the [mockup guide](../03-design/HALO-WORKSPACE-DESIGN.md) for visual direction.
Neither is implemented by the current four-tab app.

## Product decisions carried into the plan

- Activity records what happened; Notebook contains persistent objects to continue working on.
- WorkspaceObject is a proposed shared identity/metadata/provenance/lifecycle contract,
  with type-specific content repositories. Avoid a giant generic record or repository.
- Notebook storage is not automatically runtime memory. Notes/files/conversations can be
  searchable, explicitly selected as context, or explicitly promoted to memory.
- Working Set is visible user-controlled context. A snapshot is assembled for a request;
  removing an item affects subsequent retrieval, with in-flight behavior defined explicitly.
- Memory needs provenance, correction and forgetting. Derived information must retain
  source links and be distinguishable from directly authored facts.
- Editing, search and organization work without an AI provider. AI edits are attributed
  proposals until accepted; they do not silently replace user text.
- Device captures enter Inbox through host events/application use cases. Drivers do not
  write Notebook tables or depend on its UI.

## Reconcile with current code before implementation

Current storage contains a Room Memory entity and separate in-memory cognitive/conversation
stores. Inventory usage and migration before creating another MemoryRepository or model.
Use [current persistence](../04-architecture/DATABASE.md) as the baseline.

The blueprint's core/workspace-api, runtime/memory and storage/* module tree is illustrative;
those proposed modules are not the present Gradle layout. Start with the smallest explicit
contracts/use cases/storage implementation that fit current boundaries. Use the RFC process
for substantial new contracts. Blueprint Kotlin is pseudocode, not ready-to-compile API code.

Resolve these contract details in the implementation RFC:

1. Workspace identity, content references and transactions linking object creation to events.
2. Crash-safe autosave, revisions/conflict handling and process/reboot restoration.
3. Imported-file ownership and durable Android URI grants; revoked/moved/missing sources.
4. Search indexing lifecycle, lexical-first query behavior and indexing failure/retry.
5. Context selection, request budget, source revision and per-provider sharing policy.
6. Archive/trash/permanent-delete semantics; exclude trashed/forgotten data from retrieval,
   indexes, working sets and caches; define retention for revisions and backups.
7. Idempotent media ingestion using stable capture identity; no duplicate imports on retry.
8. Memory correction provenance. Keep a minimal correction trail in alpha if corrections
   ship; rich revision browsing can wait until beta. Do not postpone correctness of edits.
9. Export: establish a versioned format and basic note export early; complete workspace
   import/export can remain beta. Do not claim the full portability criterion before delivery.

An external file referenced by URI is not owned by Halo. Removing its Notebook reference
must not silently delete the original. Avoid storing large media as Room blobs.

## Delivery slices

| Slice | Scope | Acceptance |
|---|---|---|
| NB-01 Foundation + notes | **In progress:** object identity/metadata, persistent notes, Inbox, notebook route, editor/autosave with durable recovery drafts, archive/trash, and local lexical search are implemented; richer Markdown editing and full device/accessibility acceptance remain | Works offline and after process death/restart; edit/delete/search agree |
| NB-02 Memory + Working Set | Explicit promotion, provenance, correction/forgetting, context snapshot interface, Home summary | Note -> memory -> Working Set -> contextual answer -> Activity link -> original note works; forgotten data excluded |
| NB-03 Conversations + files | Durable typed conversation records, import/share via Android URIs, indexing states, source references | Reopen content; handle revoked grants and provider failure without blocking local editing |
| NB-04 Device + alpha acceptance | Verified media event -> Inbox, Activity links, filters and management flows | Real media receipt precedes import success; duplicates/cancellation/failure handled |
| NB-05 Beta | Canvas, richer project/collection relationships, semantic search, richer previews, full export/import | Each capability ships with working storage, controls and independent acceptance |

NB-01 is an incremental foundation, not completion of the blueprint's full alpha.
Full alpha requires NB-01–04 and the blueprint acceptance criteria. Canvas is beta;
its mockup filter must not imply a working Canvas before implementation.
Projects/collections are beta in this plan despite project examples in the picture.
Notes and memory can have tags and provenance without a full graph/board editor.

## Minimum useful integrated flow

Create “Halo UI ideas” -> write “Use soft pixel cells for Halo Presence” -> select Remember
-> provenance points to the note -> add note to Working Set -> Home shows the working context
-> ask to continue -> context source provides permitted note/memory -> answer and action receipt
-> Activity links back to the note. Test with a deterministic fake provider, then a selected
configured provider. Basic editing and lexical search must still work with no provider.

Additional acceptance: memory correction/removal, duplicate media, process death mid-autosave,
index failure, large file, revoked URI permission, cloud context disabled, dark/light themes,
large text, TalkBack, and references to archived/trashed/deleted objects.

## Presence clarification

The blueprint further specifies soft spatial cells and rejects mechanical rings/radial gauges.
Interpret the earlier request for a halo as optional soft depth/light around those cells,
not an obligatory segmented mechanical orbit. Keep progress in readable task cards.
Content stays primary in Notebook; a compact presence indicator appears only when useful.
