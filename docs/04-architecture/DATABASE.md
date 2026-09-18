# Persistence implementation

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

[AppDatabase](../../app/src/main/java/com/halo/app/data/AppDatabase.kt) declares a single
Room Memory entity, version 1, exportSchema=false.
[MemoryRepository](../../app/src/main/java/com/halo/app/data/MemoryRepository.kt) provides
DAO insert/update/delete and all-memories flow. This does not establish integration
with the cognitive session.

[ManualDependencyGraph](../../app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt)
constructs InMemoryConversationStore.
[Cognitive memory stores](../../engines/memory/src/main/java/com/halo/engines/memory/store/StoreImplementations.kt)
use maps; some searches return all values or are marked as mocked retrieval.

[AndroidStorage](../../host-android/src/main/java/com/halo/host/android/AndroidStorage.kt)
uses ordinary private SharedPreferences. Provider configuration and the persistent device
registry use Storage. IntelligenceViewModel writes the OpenRouter key through this path;
application-level encryption is not provided by AndroidStorage.

Previously listed Devices/Providers/Plugins/Automations/History/Settings Room tables,
SQLCipher support and universal JSON/CSV export were proposals, not this schema.
Durable conversation integration, migrations, backup rules and comprehensive export/delete
remain to be specified and validated.

The proposed [Notebook workspace schema](../02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md)
is future design. Its workspace objects, working sets, relations and search indexes
must not be added to the current-schema description before implementation.
