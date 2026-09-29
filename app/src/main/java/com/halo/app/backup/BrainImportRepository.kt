package com.halo.app.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.Memory
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.storage.HaloStorageLayout
import com.halo.sdk.runtime.Storage
import com.halo.host.android.reminder.ReminderRecord
import com.halo.host.android.reminder.ReminderRecordStore
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.DeviceRecord
import com.halo.sdk.runtime.embodiment.PersistentDeviceRegistry
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

interface BrainImportRepository {
    suspend fun stage(source: Uri): File
    suspend fun inspect(stagedArchive: File, password: CharArray? = null): BrainArchiveValidation
    suspend fun apply(
        stagedArchive: File,
        mode: BrainImportMode,
        password: CharArray? = null,
        conflictPolicies: Map<String, ImportConflictPolicy> = emptyMap(),
    ): BrainImportResult
}

class StagedBrainImporter(
    private val context: Context,
    private val layout: HaloStorageLayout,
    private val database: AppDatabase,
    private val exporter: BrainExportRepository,
    private val settingsStorage: Storage,
    moshi: Moshi,
    private val reconciler: RestoreReconciler,
    private val limits: ArchiveSecurityLimits = ArchiveSecurityLimits(),
) : BrainImportRepository {
    private val manifestAdapter = moshi.adapter(HaloBrainManifest::class.java)
    private val completionAdapter = moshi.adapter(ArchiveCompletion::class.java)
    private val settingsAdapter = moshi.adapter(UserSettingsSnapshot::class.java)
    private val adapters: Map<Class<*>, JsonAdapter<*>> = listOf(
        WorkspaceObjectSnapshot::class.java, NoteSnapshot::class.java, ConversationSnapshot::class.java,
        TurnSnapshot::class.java, MemorySnapshot::class.java, LegacyMemorySnapshot::class.java, MemoryRevisionSnapshot::class.java,
        WorkingSetSnapshot::class.java, ProvenanceSnapshot::class.java, RelationSnapshot::class.java,
        FileSnapshot::class.java, AgentRunSnapshot::class.java, AgentStepSnapshot::class.java,
        ReminderSnapshot::class.java, DeviceConfigurationSnapshot::class.java,
    ).associateWith { moshi.adapter(it) }

    override suspend fun stage(source: Uri): File = withContext(Dispatchers.IO) {
        layout.restoreStaging.mkdirs()
        val target = File(layout.restoreStaging, "${UUID.randomUUID()}.halobrain")
        try {
            val input = context.contentResolver.openInputStream(source) ?: error("Unable to open selected archive")
            input.use { sourceStream ->
                BufferedOutputStream(FileOutputStream(target)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = sourceStream.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= limits.maxArchiveBytes) { "Archive exceeds the allowed size" }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        target
    }

    override suspend fun inspect(stagedArchive: File, password: CharArray?): BrainArchiveValidation = withContext(Dispatchers.IO) {
        try {
            val prepared = prepareZip(stagedArchive, password)
            val parsed = try { readAndValidate(prepared) } catch (error: Throwable) {
                if (prepared.encrypted) prepared.file.delete()
                throw error
            }
            if (parsed.manifest.schemaVersion > BrainArchiveSchema.CURRENT) {
                parsed.zipFile.close()
                if (prepared.encrypted) prepared.file.delete()
                return@withContext BrainArchiveValidation.UnsupportedVersion(parsed.manifest.schemaVersion)
            }
            val localObjects = database.brainSnapshotDao().workspaceObjects().associateBy { it.id }
            val localNotes = database.brainSnapshotDao().notes().associateBy { it.objectId }
            val create = mutableListOf<ImportOperation>()
            val update = mutableListOf<ImportOperation>()
            val conflicts = mutableListOf<ImportConflict>()
            parsed.snapshot.workspace.forEach { incoming ->
                val local = localObjects[incoming.id]
                if (local == null) create += ImportOperation(incoming.id, incoming.type, incoming.title)
                else {
                    update += ImportOperation(incoming.id, incoming.type, incoming.title)
                    val incomingRevision = parsed.snapshot.notes.firstOrNull { it.objectId == incoming.id }?.revision
                    val localRevision = localNotes[incoming.id]?.revision
                    if (incoming.updatedAt != local.updatedAt || incomingRevision != localRevision) {
                        conflicts += ImportConflict(incoming.id, incoming.type, localRevision, incomingRevision, local.updatedAt, incoming.updatedAt)
                    }
                }
            }
            val localMemories = database.brainSnapshotDao().memories().associateBy { it.id }
            parsed.snapshot.memories.filter { it.id !in localObjects }.forEach { incoming ->
                val local = localMemories[incoming.id]
                if (local == null) create += ImportOperation(incoming.id, "MEMORY", incoming.kind)
                else {
                    update += ImportOperation(incoming.id, "MEMORY", incoming.kind)
                    if (local.updatedAt != incoming.updatedAt || local.content != incoming.content) {
                        conflicts += ImportConflict(incoming.id, "MEMORY", null, null, local.updatedAt, incoming.updatedAt)
                    }
                }
            }
            val localConversations = database.brainSnapshotDao().conversations().associateBy { it.id }
            parsed.snapshot.conversations.filter { it.id !in localObjects }.forEach { incoming ->
                val local = localConversations[incoming.id]
                if (local == null) create += ImportOperation(incoming.id, "CONVERSATION", incoming.title)
                else {
                    update += ImportOperation(incoming.id, "CONVERSATION", incoming.title)
                    if (local.updatedAt != incoming.updatedAt) {
                        conflicts += ImportConflict(incoming.id, "CONVERSATION", null, null, local.updatedAt, incoming.updatedAt)
                    }
                }
            }
            val missing = parsed.snapshot.files.filter { file ->
                file.ownership == "HALO_OWNED" && parsed.contentEntries[file.objectId] == null && parsed.contentEntries[safeSegment(file.objectId)] == null
            }.map(FileSnapshot::displayName)
            val info = BrainArchiveInfo(parsed.manifest, stagedArchive.length(), parsed.encrypted)
            val plan = BrainImportPlan(info, create, update, conflicts, emptyList(), missing)
            parsed.zipFile.close()
            if (prepared.encrypted) prepared.file.delete()
            BrainArchiveValidation.Valid(info, plan)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            when {
                error is AEADBadTagException || error.cause is AEADBadTagException -> BrainArchiveValidation.WrongPassword
                error.message?.contains("password", ignoreCase = true) == true -> BrainArchiveValidation.WrongPassword
                else -> BrainArchiveValidation.Corrupt(error.message ?: "Archive validation failed")
            }
        } finally {
            password?.fill('\u0000')
        }
    }

    override suspend fun apply(
        stagedArchive: File,
        mode: BrainImportMode,
        password: CharArray?,
        conflictPolicies: Map<String, ImportConflictPolicy>,
    ): BrainImportResult = withContext(Dispatchers.IO) {
        var decryptedFile: File? = null
        val parsed = try {
            val prepared = prepareZip(stagedArchive, password)
            if (prepared.encrypted) decryptedFile = prepared.file
            readAndValidate(prepared)
        } catch (error: Throwable) {
            decryptedFile?.delete()
            throw error
        } finally {
            password?.fill('\u0000')
        }
        if (parsed.manifest.schemaVersion > BrainArchiveSchema.CURRENT) {
            parsed.zipFile.close()
            decryptedFile?.delete()
            error("This backup requires a newer Halo version")
        }
        if (mode == BrainImportMode.REPLACE) {
            try { createSafetySnapshot() } catch (error: Throwable) {
                parsed.zipFile.close()
                decryptedFile?.delete()
                throw error
            }
        }
        val dao = database.brainSnapshotDao()
        val existingIds = dao.workspaceObjects().mapTo(mutableSetOf()) { it.id }
        val existingMemoryIds = dao.memories().mapTo(mutableSetOf()) { it.id }
        val existingLegacyMemories = database.memoryDao().snapshot()
        val existingConversationIds = dao.conversations().mapTo(mutableSetOf()) { it.id }
        val skippedIds = if (mode == BrainImportMode.MERGE) {
            existingIds.filterTo(mutableSetOf()) { id -> conflictPolicies[id] != ImportConflictPolicy.USE_BACKUP }
        } else mutableSetOf()
        val duplicateIds = conflictPolicies.filterValues { it == ImportConflictPolicy.DUPLICATE }.keys
        val idMap = duplicateIds.associateWith { UUID.randomUUID().toString() }
        var snapshot = remap(parsed.snapshot, idMap)
        if (mode == BrainImportMode.MERGE) {
            snapshot = filterSkipped(snapshot, skippedIds - duplicateIds)
            val keptMemories = existingMemoryIds.filterTo(mutableSetOf()) { conflictPolicies[it] != ImportConflictPolicy.USE_BACKUP }
            val keptConversations = existingConversationIds.filterTo(mutableSetOf()) { conflictPolicies[it] != ImportConflictPolicy.USE_BACKUP }
            snapshot = snapshot.copy(
                conversations = snapshot.conversations.filterNot { it.id in keptConversations }.map { it.copy(active = false) },
                turns = snapshot.turns.filterNot { it.conversationId in keptConversations },
                memories = snapshot.memories.filterNot { it.id in keptMemories },
                memoryRevisions = snapshot.memoryRevisions.filterNot { it.memoryId in keptMemories },
                legacyMemories = snapshot.legacyMemories.filterNot { incoming ->
                    existingLegacyMemories.any { it.content == incoming.content && it.timestamp == incoming.timestamp }
                },
            )
        }

        try {
            val materializedFiles = materializeOwnedFiles(snapshot.files, parsed.zipFile, parsed.contentEntries, idMap.entries.associate { it.value to it.key })
            val fileUris = snapshot.files.zip(materializedFiles).associate { (before, after) -> before.uri to after.uri }
            snapshot = snapshot.copy(
                files = materializedFiles,
                notes = snapshot.notes.map { note ->
                    val originalUri = snapshot.files.firstOrNull { it.objectId == note.objectId }?.uri
                    val replacement = fileUris[originalUri]
                    if (originalUri.isNullOrBlank() || replacement.isNullOrBlank()) note
                    else note.copy(body = note.body.replace(originalUri, replacement))
                },
            )
            val room = snapshot.toRoomContent()
            database.withTransaction {
                if (mode == BrainImportMode.REPLACE) clearBrain(dao)
                dao.putWorkspaceObjects(room.workspace)
                dao.putNotes(room.notes)
                dao.putConversations(room.conversations)
                dao.putTurns(room.turns)
                dao.putMemories(room.memories)
                snapshot.legacyMemories.forEach { legacy ->
                    val id = if (mode == BrainImportMode.REPLACE || existingLegacyMemories.none { it.id == legacy.legacyId }) legacy.legacyId else 0
                    database.memoryDao().insertMemory(Memory(id, legacy.content, legacy.timestamp, legacy.sourceObjectId, legacy.sourceRevision))
                }
                dao.putMemoryRevisions(room.memoryRevisions)
                dao.putWorkingSet(room.workingSet)
                dao.putProvenance(room.provenance)
                dao.putRelations(room.relations)
                dao.putFiles(room.files)
                val normalizedAgentRuns = room.agentRuns.map { run ->
                    if (run.state in setOf("EXECUTING", "PLANNING", "WAITING_FOR_APPROVAL", "WAITING_FOR_CLARIFICATION", "CREATED")) {
                        run.copy(state = "INTERRUPTED")
                    } else run
                }
                dao.putAgentRuns(normalizedAgentRuns)
                dao.putAgentSteps(room.agentSteps)
            }
            val portableStrings = snapshot.settings.values.filterKeys { it in PORTABLE_STRING_KEYS }
            portableStrings.filterKeys { it !in AI_SELECTION_KEYS }.forEach(settingsStorage::putString)
            if (portableStrings.keys.any { it in AI_SELECTION_KEYS }) {
                val previousProvider = settingsStorage.getString("active_ai_provider")
                val previousModel = settingsStorage.getString("active_ai_model")
                val previousPolicy = settingsStorage.getString("ai_routing_policy")
                settingsStorage.putBatch(
                    strings = mapOf(
                        "active_ai_provider" to portableStrings["active_ai_provider"],
                        "active_ai_model" to portableStrings["active_ai_model"],
                        "ai_routing_policy" to (portableStrings["ai_routing_policy"] ?: "AUTOMATIC"),
                    ),
                    booleans = mapOf("active_ai_selection_migrated_v1" to true),
                )
                Log.i(TAG, "HALO_AI_SELECTION_WRITE source=IMPORT_RESTORE " +
                    "previousProvider=$previousProvider previousModel=$previousModel previousPolicy=$previousPolicy " +
                    "newProvider=${portableStrings["active_ai_provider"]} newModel=${portableStrings["active_ai_model"]} " +
                    "newPolicy=${portableStrings["ai_routing_policy"] ?: "AUTOMATIC"}")
            }
            snapshot.settings.booleanValues.forEach { (key, value) ->
                if (key in PORTABLE_BOOLEAN_KEYS) settingsStorage.putBoolean(key, value)
            }
            snapshot.settings.integerValues.forEach { (key, value) ->
                if (key in PORTABLE_INTEGER_KEYS) settingsStorage.putInt(key, value)
            }
            snapshot.settings.appearanceMode?.takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") }?.let { mode ->
                context.getSharedPreferences("halo_appearance", Context.MODE_PRIVATE).edit().putString("mode", mode).apply()
            }
            snapshot.settings.skillEnabled.forEach { (id, enabled) ->
                if (id.matches(Regex("[a-zA-Z0-9._-]{1,80}"))) settingsStorage.putBoolean("skill_enabled_$id", enabled)
            }
            val reminderStore = ReminderRecordStore(context)
            val incomingReminders = snapshot.reminders.map { ReminderRecord(it.id, it.message, it.triggerAtEpochMs, it.precision) }
            if (mode == BrainImportMode.REPLACE) reminderStore.replace(incomingReminders)
            else incomingReminders.filter { imported -> reminderStore.all().none { it.id == imported.id } }.forEach(reminderStore::save)
            val registry = PersistentDeviceRegistry(settingsStorage)
            val savedDevices = registry.getSavedDevices().mapTo(mutableSetOf()) { it.identity.deviceId }
            if (mode == BrainImportMode.REPLACE) savedDevices.forEach { registry.removeDevice(it) }
            snapshot.devices.forEach { device ->
                if (mode == BrainImportMode.REPLACE || device.id !in savedDevices) {
                    registry.saveDevice(DeviceRecord(
                        identity = DeviceIdentity(device.id, device.deviceType, device.displayName),
                        autoConnect = false,
                        firmwareVersion = device.firmwareVersion,
                        lastSeenMs = 0L,
                    ))
                }
            }
        } finally {
            parsed.zipFile.close()
            decryptedFile?.delete()
        }
        val created = snapshot.workspace.count { it.id !in existingIds }
        reconciler.reconcile()
        BrainImportResult(created, snapshot.workspace.size - created, skippedIds.size)
    }

    private suspend fun createSafetySnapshot() {
        layout.restoreSafety.mkdirs()
        val target = File(layout.restoreSafety, "restore-safety_${System.currentTimeMillis()}.halobrain")
        FileOutputStream(target).use { exporter.export(it, BrainExportRequest(BrainExportProfile.BRAIN_WITH_FILES)) }
        layout.restoreSafety.listFiles { file -> file.extension == "halobrain" }.orEmpty()
            .sortedByDescending(File::lastModified).drop(3).forEach(File::delete)
    }

    private fun prepareZip(source: File, password: CharArray?): PreparedFile {
        require(source.isFile && source.length() <= limits.maxArchiveBytes) { "Archive exceeds the allowed size" }
        val encrypted = FileInputStream(source).use { input ->
            val magic = ByteArray(BrainArchiveCrypto.MAGIC.size)
            input.read(magic) == magic.size && magic.contentEquals(BrainArchiveCrypto.MAGIC)
        }
        if (!encrypted) return PreparedFile(source, false)
        require(password != null && password.isNotEmpty()) { "Password required" }
        val decrypted = File(layout.restoreStaging, "${UUID.randomUUID()}.zip")
        FileInputStream(source).use { raw ->
            BrainArchiveCrypto.decrypting(BufferedInputStream(raw), password!!).use { input ->
                FileOutputStream(decrypted).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= limits.maxTotalExpandedBytes) { "Decrypted archive is too large" }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        return PreparedFile(decrypted, true)
    }

    private fun readAndValidate(prepared: PreparedFile): ParsedArchive {
        val zip = ZipFile(prepared.file)
        try {
        val names = mutableSetOf<String>()
        var entries = 0
        var expanded = 0L
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            entries++
            require(entries <= limits.maxEntries) { "Archive has too many entries" }
            validateEntryName(entry.name)
            require(names.add(entry.name)) { "Duplicate archive entry: ${entry.name}" }
            require(entry.size in 0L..limits.maxSingleEntryBytes) { "Archive entry is too large: ${entry.name}" }
            expanded += entry.size
            require(expanded <= limits.maxTotalExpandedBytes) { "Archive expands beyond the allowed size" }
            if (entry.compressedSize > 0 && entry.size / entry.compressedSize > limits.maxCompressionRatio) {
                error("Suspicious compression ratio: ${entry.name}")
            }
        }
        require("manifest.json" in names && "archive.complete" in names)
        val manifestBytes = readRequired(zip, "manifest.json", 1024 * 1024)
        val manifest = requireNotNull(manifestAdapter.fromJson(manifestBytes.toString(Charsets.UTF_8)))
        require(manifest.format == "halo-brain") { "Not a Halo Brain archive" }
        require(manifest.schemaVersion >= 1 && manifest.state == "COMPLETE") { "Incomplete or unsupported archive" }
        val checksumsBytes = readRequired(zip, "checksums.sha256", 8 * 1024 * 1024)
        val completion = requireNotNull(completionAdapter.fromJson(readRequired(zip, "archive.complete", 64 * 1024).toString(Charsets.UTF_8)))
        require(completion.archiveId == manifest.archiveId)
        require(completion.manifestSha256 == sha256(manifestBytes)) { "Manifest checksum mismatch" }
        require(completion.checksumListSha256 == sha256(checksumsBytes)) { "Checksum list mismatch" }
        val checkedPaths = mutableSetOf<String>()
        checksumsBytes.toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank).forEach { line ->
            val separator = line.indexOf("  ")
            require(separator == 64) { "Malformed checksum entry" }
            val expected = line.substring(0, separator)
            val path = line.substring(separator + 2)
            require(checkedPaths.add(path)) { "Duplicate checksum entry" }
            val entry = requireNotNull(zip.getEntry(path)) { "Missing checked entry: $path" }
            require(zip.getInputStream(entry).use(::digestStream) == expected) { "Checksum mismatch: $path" }
        }
        require(checkedPaths == names - setOf("checksums.sha256", "archive.complete")) { "Archive contains unchecked entries" }
        if (manifest.schemaVersion > BrainArchiveSchema.CURRENT) {
            return ParsedArchive(zip, manifest, emptySnapshot(manifest), emptyMap(), prepared.encrypted)
        }
        val snapshot = readSnapshot(zip, manifest)
        require(snapshot.notes.all { note -> snapshot.workspace.any { it.id == note.objectId } }) { "Note references missing workspace object" }
        require(snapshot.turns.all { turn -> snapshot.conversations.any { it.id == turn.conversationId } }) { "Turn references missing conversation" }
        require(snapshot.files.all { file -> snapshot.workspace.any { it.id == file.objectId } }) { "File references missing workspace object" }
        val content = names.filter { it.startsWith("files/content/") }.associateBy { it.split('/').getOrNull(2).orEmpty() }
        require(snapshot.files.all { file -> file.ownedCopyPath == null || file.ownedCopyPath.startsWith("archive:files/content/") }) {
            "Archive contains an unsafe file reference"
        }
        require(snapshot.files.none { file -> file.ownership == "HALO_OWNED" && file.ownedCopyPath != null && content[safeSegment(file.objectId)] == null }) {
            "Archive is missing owned workspace content"
        }
        return ParsedArchive(zip, manifest, snapshot, content, prepared.encrypted)
        } catch (error: Throwable) {
            zip.close()
            throw error
        }
    }

    private fun readSnapshot(zip: ZipFile, manifest: HaloBrainManifest): BrainSnapshot = BrainSnapshot(
        snapshotId = manifest.archiveId,
        createdAt = manifest.createdAtEpochMs,
        workspace = readLines(zip, "workspace/objects.jsonl", WorkspaceObjectSnapshot::class.java),
        notes = readLines(zip, "notes/metadata.jsonl", NoteSnapshot::class.java),
        conversations = readLines(zip, "conversations/conversations.jsonl", ConversationSnapshot::class.java),
        turns = readLines(zip, "conversations/turns.jsonl", TurnSnapshot::class.java),
        memories = readLines(zip, "memory/memories.jsonl", MemorySnapshot::class.java),
        legacyMemories = readLines(zip, "memory/legacy.jsonl", LegacyMemorySnapshot::class.java),
        memoryRevisions = readLines(zip, "memory/revisions.jsonl", MemoryRevisionSnapshot::class.java),
        workingSet = readLines(zip, "working-set/entries.jsonl", WorkingSetSnapshot::class.java),
        provenance = readLines(zip, "workspace/provenance.jsonl", ProvenanceSnapshot::class.java),
        relations = readLines(zip, "relations/relations.jsonl", RelationSnapshot::class.java),
        files = readLines(zip, "files/index.jsonl", FileSnapshot::class.java),
        agentRuns = readLines(zip, "agent/runs.jsonl", AgentRunSnapshot::class.java),
        agentSteps = readLines(zip, "agent/steps.jsonl", AgentStepSnapshot::class.java),
        reminders = readLines(zip, "reminders/reminders.jsonl", ReminderSnapshot::class.java),
        devices = readLines(zip, "devices/configuration.jsonl", DeviceConfigurationSnapshot::class.java),
        settings = zip.getEntry("settings/user-settings.json")?.let { entry ->
            settingsAdapter.fromJson(zip.getInputStream(entry).bufferedReader().use { it.readText() })
        } ?: UserSettingsSnapshot(emptyMap(), emptyMap()),
    )

    private fun emptySnapshot(manifest: HaloBrainManifest) = BrainSnapshot(
        manifest.archiveId, manifest.createdAtEpochMs,
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        UserSettingsSnapshot(emptyMap(), emptyMap()),
    )

    private fun <T : Any> readLines(zip: ZipFile, path: String, type: Class<T>): List<T> {
        val entry = zip.getEntry(path) ?: return emptyList()
        require(entry.size in 0L..(128L * 1024 * 1024)) { "Structured section is too large: $path" }
        @Suppress("UNCHECKED_CAST") val adapter = adapters.getValue(type) as JsonAdapter<T>
        return zip.getInputStream(entry).bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).map { line ->
                require(line.length <= 16 * 1024 * 1024) { "Archive record is too large" }
                requireNotNull(adapter.fromJson(line))
            }.toList()
        }
    }

    private fun materializeOwnedFiles(files: List<FileSnapshot>, zip: ZipFile, entries: Map<String, String>, originalIds: Map<String, String>): List<FileSnapshot> = files.map { file ->
        val archiveId = originalIds[file.objectId] ?: file.objectId
        val entryPath = entries[archiveId] ?: entries[safeSegment(archiveId)]
        if (file.ownership != "HALO_OWNED" || entryPath == null) {
            if (file.ownership == "EXTERNAL_REFERENCE") file.copy(permissionPersisted = false, availability = WorkspaceFileAvailability.NEEDS_RELINK.name) else file
        } else {
            val destination = File(layout.managedWorkspaceFiles, "${safeSegment(file.objectId)}/${UUID.randomUUID()}_${safeSegment(file.displayName)}")
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, ".${destination.name}.restoring")
            zip.getInputStream(requireNotNull(zip.getEntry(entryPath))).use { input -> FileOutputStream(temporary).use(input::copyTo) }
            check(temporary.renameTo(destination)) { "Unable to restore ${file.displayName}" }
            val restoredUri = FileProvider.getUriForFile(context, "${context.packageName}.captures", destination)
            file.copy(uri = restoredUri.toString(), ownedCopyPath = destination.absolutePath, permissionPersisted = false, availability = WorkspaceFileAvailability.OWNED_LOCAL.name)
        }
    }

    private fun remap(source: BrainSnapshot, ids: Map<String, String>): BrainSnapshot {
        fun id(value: String) = ids[value] ?: value
        return source.copy(
            workspace = source.workspace.map { it.copy(id = id(it.id), contentRef = it.contentRef.replaceAfter(':', id(it.id))) },
            notes = source.notes.map { it.copy(objectId = id(it.objectId)) },
            conversations = source.conversations.map { it.copy(id = id(it.id)) },
            turns = source.turns.map { it.copy(id = if (it.conversationId in ids) UUID.randomUUID().toString() else it.id, conversationId = id(it.conversationId)) },
            memories = source.memories.map { it.copy(id = id(it.id), sourceObjectId = it.sourceObjectId?.let(::id)) },
            memoryRevisions = source.memoryRevisions.map { it.copy(id = if (it.memoryId in ids) UUID.randomUUID().toString() else it.id, memoryId = id(it.memoryId)) },
            workingSet = source.workingSet.map { it.copy(objectId = id(it.objectId)) },
            provenance = source.provenance.map { it.copy(objectId = id(it.objectId), sourceId = id(it.sourceId)) },
            relations = source.relations.map { it.copy(id = if (it.fromId in ids || it.toId in ids) UUID.randomUUID().toString() else it.id, fromId = id(it.fromId), toId = id(it.toId)) },
            files = source.files.map { it.copy(objectId = id(it.objectId)) },
        )
    }

    private fun filterSkipped(source: BrainSnapshot, skipped: Set<String>) = source.copy(
        workspace = source.workspace.filterNot { it.id in skipped }, notes = source.notes.filterNot { it.objectId in skipped },
        conversations = source.conversations.filterNot { it.id in skipped }, turns = source.turns.filterNot { it.conversationId in skipped },
        memories = source.memories.filterNot { it.id in skipped }, memoryRevisions = source.memoryRevisions.filterNot { it.memoryId in skipped },
        workingSet = source.workingSet.filterNot { it.objectId in skipped }, provenance = source.provenance.filterNot { it.objectId in skipped },
        relations = source.relations.filterNot { it.fromId in skipped || it.toId in skipped }, files = source.files.filterNot { it.objectId in skipped },
    )

    private suspend fun clearBrain(dao: BrainSnapshotDao) {
        dao.clearAgentSteps(); dao.clearAgentRuns(); dao.clearFiles(); dao.clearRelations(); dao.clearProvenance(); dao.clearWorkingSet()
        dao.clearMemoryRevisions(); dao.clearMemories(); dao.clearTurns(); dao.clearConversations(); dao.clearDrafts(); dao.clearNotes(); dao.clearWorkspaceObjects()
        dao.clearLegacyMemories()
    }

    private fun readRequired(zip: ZipFile, path: String, maxBytes: Int): ByteArray {
        val entry = requireNotNull(zip.getEntry(path)) { "Missing $path" }
        require(entry.size in 0L..maxBytes.toLong()) { "$path is too large" }
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun digestStream(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val read = it.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        }
        return digest.digest().toHex()
    }

    private fun validateEntryName(name: String) {
        require(name.isNotBlank() && name.length <= limits.maxPathLength)
        require(!name.startsWith('/') && !name.startsWith('\\') && '\\' !in name)
        require(name.split('/').none { it == ".." || it.isBlank() })
        require(!Regex("^[A-Za-z]:").containsMatchIn(name))
    }

    private fun safeSegment(value: String) = value.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100).ifBlank { "file" }

    private data class PreparedFile(val file: File, val encrypted: Boolean)
    private data class ParsedArchive(val zipFile: ZipFile, val manifest: HaloBrainManifest, val snapshot: BrainSnapshot, val contentEntries: Map<String, String>, val encrypted: Boolean)

    private companion object {
        const val TAG = "BrainImportRepository"
        val AI_SELECTION_KEYS = setOf("active_ai_provider", "active_ai_model", "ai_routing_policy")
        val PORTABLE_STRING_KEYS = setOf(
            "active_ai_provider", "active_ai_model", "ai_routing_policy",
            "ollama_endpoint", "speech_locale",
        )
        val PORTABLE_BOOLEAN_KEYS = setOf(
            "policy_conversation", "policy_device_state", "policy_recent_events", "policy_memories",
            "policy_tools", "policy_location", "policy_images", "policy_audio", "policy_video",
            "policy_cloud_workspace",
        )
        val PORTABLE_INTEGER_KEYS = setOf("litert_context", "ai_temperature")
    }
}
