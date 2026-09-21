# Persistence implementation

Status: CURRENT SOURCE MAP. Reviewed 2026-09-21.

[AppDatabase](../../app/src/main/java/com/halo/app/data/AppDatabase.kt) is Room version 3,
exportSchema=false. It retains the original Memory entity and adds the NB-01
`workspace_objects` metadata table, a type-specific `notes` table, and a short-lived
`note_drafts` recovery table. Migration 1 -> 2 preserves existing Memory rows and creates the
Notebook object/note tables and indexes; migration 2 -> 3 adds draft recovery.
[MemoryRepository](../../app/src/main/java/com/halo/app/data/MemoryRepository.kt) provides
DAO insert/update/delete and all-memories flow. This does not establish integration
with the cognitive session.

[RoomNotebookRepository](../../app/src/main/java/com/halo/app/data/notebook/NotebookRepository.kt)
provides persistent note creation, recoverable drafts, atomic metadata/content edits with
revisions, literal local lexical search, Inbox membership, archive/trash/restore, and permanent
deletion restricted to trashed objects. Committing a note removes its recovery draft; permanent
deletion cascades to both. Notebook notes are not automatically memory or model context. File storage,
FTS indexing, conversations, promoted memory, Working Set, retention and export remain future
delivery slices.

[ManualDependencyGraph](../../app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt)
constructs InMemoryConversationStore.
[Cognitive memory stores](../../engines/memory/src/main/java/com/halo/engines/memory/store/StoreImplementations.kt)
use maps; some searches return all values or are marked as mocked retrieval.

[AndroidStorage](../../host-android/src/main/java/com/halo/host/android/AndroidStorage.kt)
uses ordinary private SharedPreferences. Provider configuration and the persistent device
registry use Storage. IntelligenceViewModel writes the OpenRouter key through this path;
application-level encryption is not provided by AndroidStorage.

`HaloSettingsRepository` is the typed asynchronous DataStore boundary introduced for user settings.
Speech language mode/version/user-selection now use it with one-time migration from `halo_storage`.
Provider credentials and several legacy settings still use AndroidStorage, so the migration is
incremental rather than a claim that SharedPreferences has been eliminated repository-wide.

Previously listed Devices/Providers/Plugins/Automations/History/Settings Room tables,
SQLCipher support and universal JSON/CSV export were proposals, not this schema.
Durable conversation integration, migrations, backup rules and comprehensive export/delete
remain to be specified and validated.

The broader [Notebook workspace schema](../02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md)
remains a design target beyond this narrow NB-01 foundation. See the
[foundation RFC](../rfc/0001-notebook-foundation.md) for implemented decisions and open work.
