package com.halo.app.backup

import com.halo.app.BuildConfig
import com.halo.app.data.AppDatabase
import com.halo.app.storage.HaloStorageLayout
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ZipBrainExporter(
    private val sources: BrainSnapshotSource,
    private val database: AppDatabase,
    private val layout: HaloStorageLayout,
    moshi: Moshi,
) : BrainExportRepository {
    private val manifestAdapter = moshi.adapter(HaloBrainManifest::class.java)
    private val completionAdapter = moshi.adapter(ArchiveCompletion::class.java)
    private val settingsAdapter = moshi.adapter(UserSettingsSnapshot::class.java)
    private val adapters: Map<Class<*>, JsonAdapter<*>> = listOf(
        WorkspaceObjectSnapshot::class.java, NoteSnapshot::class.java, ConversationSnapshot::class.java,
        TurnSnapshot::class.java, MemorySnapshot::class.java, LegacyMemorySnapshot::class.java, MemoryRevisionSnapshot::class.java,
        EpisodeSnapshot::class.java, SuggestionDecisionSnapshot::class.java, ReflectionStatusSnapshot::class.java,
        WorkingSetSnapshot::class.java, ProvenanceSnapshot::class.java, RelationSnapshot::class.java,
        StudioProjectSnapshot::class.java, StudioVersionSnapshot::class.java,
        FileSnapshot::class.java, AgentRunSnapshot::class.java, AgentStepSnapshot::class.java,
        ReminderSnapshot::class.java, DeviceConfigurationSnapshot::class.java,
    ).associateWith { moshi.adapter(it) }

    override suspend fun export(
        destination: OutputStream,
        request: BrainExportRequest,
        progress: (BrainExportProgress) -> Unit,
    ): BrainExportResult = withContext(Dispatchers.IO) {
        require(!request.encrypt || (request.password != null && request.password.isNotEmpty())) { "Password required for encrypted export" }
        progress(BrainExportProgress.Preparing)
        val snapshot = sources.snapshot()
        val includeFiles = request.profile != BrainExportProfile.BRAIN_ONLY
        val sections = buildList {
            add(BrainArchiveSection("workspace", itemCount = snapshot.workspace.size))
            add(BrainArchiveSection("studio", itemCount = snapshot.studioProjects.size + snapshot.studioVersions.size))
            add(BrainArchiveSection("notes", itemCount = snapshot.notes.size))
            add(BrainArchiveSection("conversations", itemCount = snapshot.conversations.size + snapshot.turns.size))
            add(BrainArchiveSection("memory", itemCount = snapshot.memories.size + snapshot.legacyMemories.size + snapshot.memoryRevisions.size))
            add(BrainArchiveSection("working-set", itemCount = snapshot.workingSet.size))
            add(BrainArchiveSection("relations", itemCount = snapshot.relations.size))
            add(BrainArchiveSection("settings", itemCount = 1))
            add(BrainArchiveSection("reminders", itemCount = snapshot.reminders.size))
            add(BrainArchiveSection("devices", itemCount = snapshot.devices.size))
            add(BrainArchiveSection("files", itemCount = snapshot.files.size))
            if (request.includeActivityHistory || request.profile == BrainExportProfile.FULL_USER_BACKUP) {
                add(BrainArchiveSection("agent", itemCount = snapshot.agentRuns.size + snapshot.agentSteps.size))
            }
        }
        val manifest = HaloBrainManifest(
            archiveId = snapshot.snapshotId,
            createdAtEpochMs = snapshot.createdAt,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            databaseSchemaVersion = database.openHelper.readableDatabase.version,
            profile = request.profile,
            encrypted = request.encrypt,
            sections = sections,
        )
        val manifestBytes = manifestAdapter.indent("  ").toJson(manifest).toByteArray(Charsets.UTF_8)
        val rawDigest = MessageDigest.getInstance("SHA-256")
        val counting = CountingOutputStream(DigestingOutputStream(destination, rawDigest))
        val archiveOutput = if (request.encrypt) {
            val password = requireNotNull(request.password)
            try { BrainArchiveCrypto.encrypting(counting, password) } finally { password.fill('\u0000') }
        } else counting
        val checksums = mutableListOf<ArchiveChecksum>()

        val zip = ZipOutputStream(BufferedOutputStream(archiveOutput))
        try {
            writeBytes(zip, "manifest.json", manifestBytes, checksums)
            writeBytes(zip, "README.md", readme(manifest).toByteArray(Charsets.UTF_8), checksums)
            writeJsonLines(zip, "workspace/objects.jsonl", snapshot.workspace, checksums, progress)
            writeJsonLines(zip, "studio/projects.jsonl", snapshot.studioProjects, checksums, progress)
            writeJsonLines(zip, "studio/versions.jsonl", snapshot.studioVersions, checksums, progress)
            writeJsonLines(zip, "notes/metadata.jsonl", snapshot.notes, checksums, progress)
            val notePaths = mutableSetOf<String>()
            snapshot.notes.forEach { note ->
                coroutineContext.ensureActive()
                val objectInfo = snapshot.workspace.firstOrNull { it.id == note.objectId }
                val base = "notes/${safeName(objectInfo?.title ?: "note")}_${safeId(note.objectId)}"
                val path = uniquePath(base, notePaths)
                writeBytes(zip, path, noteMarkdown(objectInfo?.title, note).toByteArray(Charsets.UTF_8), checksums)
            }
            writeJsonLines(zip, "conversations/conversations.jsonl", snapshot.conversations, checksums, progress)
            writeJsonLines(zip, "conversations/turns.jsonl", snapshot.turns, checksums, progress)
            val conversationPaths = mutableSetOf<String>()
            val turnsByConversation = snapshot.turns.groupBy(TurnSnapshot::conversationId)
            snapshot.conversations.forEach { conversation ->
                coroutineContext.ensureActive()
                val turns = turnsByConversation[conversation.id].orEmpty()
                val base = "conversations/${safeName(conversation.title)}_${safeId(conversation.id)}"
                val path = uniquePath(base, conversationPaths)
                writeBytes(zip, path, conversationMarkdown(conversation, turns).toByteArray(Charsets.UTF_8), checksums)
            }
            writeJsonLines(zip, "memory/memories.jsonl", snapshot.memories, checksums, progress)
            writeJsonLines(zip, "memory/legacy.jsonl", snapshot.legacyMemories, checksums, progress)
            writeJsonLines(zip, "memory/revisions.jsonl", snapshot.memoryRevisions, checksums, progress)
            writeJsonLines(zip,"memory/episodes.jsonl",snapshot.episodes,checksums,progress)
            writeJsonLines(zip,"memory/suggestion-decisions.jsonl",snapshot.suggestionDecisions,checksums,progress)
            writeJsonLines(zip,"memory/reflection-statuses.jsonl",snapshot.reflectionStatuses,checksums,progress)
            writeBytes(zip, "memory/memories.md", memoryMarkdown(snapshot.memories, snapshot.legacyMemories).toByteArray(Charsets.UTF_8), checksums)
            writeJsonLines(zip, "working-set/entries.jsonl", snapshot.workingSet, checksums, progress)
            writeJsonLines(zip, "relations/relations.jsonl", snapshot.relations, checksums, progress)
            writeJsonLines(zip, "workspace/provenance.jsonl", snapshot.provenance, checksums, progress)
            writeBytes(zip, "settings/user-settings.json", settingsAdapter.indent("  ").toJson(snapshot.settings).toByteArray(Charsets.UTF_8), checksums)
            writeJsonLines(zip, "reminders/reminders.jsonl", snapshot.reminders, checksums, progress)
            writeJsonLines(zip, "devices/configuration.jsonl", snapshot.devices, checksums, progress)

            val fileIndex = snapshot.files.map { file ->
                val source = file.ownedCopyPath?.let(::File)
                if (file.ownership == "HALO_OWNED" && (!includeFiles || source?.isFile != true || !isHaloOwnedFile(source))) {
                    file.copy(uri = "", ownedCopyPath = null, availability = "MISSING")
                } else if (file.ownership == "HALO_OWNED") {
                    file.copy(ownedCopyPath = "archive:${contentPath(file)}")
                } else file.copy(ownedCopyPath = null)
            }
            writeJsonLines(zip, "files/index.jsonl", fileIndex, checksums, progress)
            if (includeFiles) {
                val filePaths = mutableSetOf<String>()
                fileIndex.filter { it.ownership == "HALO_OWNED" && !it.ownedCopyPath.isNullOrBlank() }.forEachIndexed { index, file ->
                    coroutineContext.ensureActive()
                    val source = File(requireNotNull(snapshot.files.firstOrNull { it.objectId == file.objectId }?.ownedCopyPath))
                    if (source.isFile && isHaloOwnedFile(source)) {
                        val base = contentPath(file)
                        var path = base
                        var suffix = 2
                        while (!filePaths.add(path)) path = "${base}_${suffix++}"
                        writeFile(zip, path, source, checksums) { written ->
                            progress(BrainExportProgress.WritingFile(file.displayName, written, source.length()))
                        }
                    }
                    progress(BrainExportProgress.WritingSection("files", index + 1, snapshot.files.size))
                }
            }
            if (request.includeActivityHistory || request.profile == BrainExportProfile.FULL_USER_BACKUP) {
                writeJsonLines(zip, "agent/runs.jsonl", snapshot.agentRuns, checksums, progress)
                writeJsonLines(zip, "agent/steps.jsonl", snapshot.agentSteps, checksums, progress)
            }

            progress(BrainExportProgress.Finalizing)
            val checksumBytes = checksums.joinToString("\n", postfix = "\n") { "${it.sha256}  ${it.path}" }.toByteArray(Charsets.UTF_8)
            writeRaw(zip, "checksums.sha256", checksumBytes)
            val completion = ArchiveCompletion(
                archiveId = manifest.archiveId,
                manifestSha256 = sha256(manifestBytes),
                checksumListSha256 = sha256(checksumBytes),
            )
            writeRaw(zip, "archive.complete", completionAdapter.toJson(completion).toByteArray(Charsets.UTF_8))
            zip.finish()
            zip.flush()
            archiveOutput.flush()
        } finally {
            zip.close()
        }
        BrainExportResult(manifest.archiveId, counting.count, rawDigest.digest().toHex())
    }

    private suspend fun <T : Any> writeJsonLines(zip: ZipOutputStream, path: String, values: List<T>, checksums: MutableList<ArchiveChecksum>, progress: (BrainExportProgress) -> Unit) {
        validateArchivePath(path)
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(path))
        values.forEachIndexed { index, value ->
            coroutineContext.ensureActive()
            @Suppress("UNCHECKED_CAST") val adapter = adapters.getValue(value.javaClass) as JsonAdapter<T>
            val bytes = (adapter.toJson(value) + "\n").toByteArray(Charsets.UTF_8)
            digest.update(bytes)
            zip.write(bytes)
            progress(BrainExportProgress.WritingSection(path.substringBefore('/'), index + 1, values.size))
        }
        zip.closeEntry()
        checksums += ArchiveChecksum(path, digest.digest().toHex())
    }

    private fun writeBytes(zip: ZipOutputStream, path: String, bytes: ByteArray, checksums: MutableList<ArchiveChecksum>) {
        writeRaw(zip, path, bytes)
        checksums += ArchiveChecksum(path, sha256(bytes))
    }

    private fun writeRaw(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        validateArchivePath(path)
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private suspend fun writeFile(zip: ZipOutputStream, path: String, file: File, checksums: MutableList<ArchiveChecksum>, progress: (Long) -> Unit) {
        validateArchivePath(path)
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(path))
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var written = 0L
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                written += read
                progress(written)
            }
        }
        zip.closeEntry()
        checksums += ArchiveChecksum(path, digest.digest().toHex())
    }

    private fun readme(manifest: HaloBrainManifest) = """# Halo Brain Export

Created: ${DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(manifest.createdAtEpochMs))}

This archive contains user-owned Halo workspace data.

Portable content includes notes, conversations, memory, the Working Set, relations, and non-secret settings.

Not included: API keys, credentials, AI or speech models, diagnostics, indexes, and transient caches.
"""

    private fun noteMarkdown(title: String?, note: NoteSnapshot) = "# ${title?.ifBlank { "Untitled note" } ?: "Untitled note"}\n\n${note.body}\n"
    private fun conversationMarkdown(conversation: ConversationSnapshot, turns: List<TurnSnapshot>) = buildString {
        append("# ").append(conversation.title.ifBlank { "Conversation" }).append("\n\n")
        turns.forEach { append("## User\n\n").append(it.userText).append("\n\n## Halo\n\n").append(it.assistantText).append("\n\n") }
    }
    private fun memoryMarkdown(memories: List<MemorySnapshot>, legacy: List<LegacyMemorySnapshot>) = buildString {
        append("# Halo Memory\n\n")
        memories.forEach { append("## ").append(it.kind.lowercase().replaceFirstChar(Char::uppercase)).append("\n\nScope: ").append(it.scope).append("\n\n").append(it.content).append("\n\n") }
        legacy.filter { old -> memories.none { it.id == "mem_${old.legacyId}" && it.content == old.content } }
            .forEach { append("## Remembered\n\n").append(it.content).append("\n\n") }
    }

    private fun safeName(value: String): String = value.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').take(80).ifBlank { "item" }
    private fun contentPath(file: FileSnapshot): String = "files/content/${safeId(file.objectId)}/${safeName(file.displayName)}"
    private fun isHaloOwnedFile(source: File): Boolean {
        val allowedRoots = listOf(layout.managedWorkspaceFiles, layout.generatedArtifacts, File(layout.managedWorkspaceFiles.parentFile.parentFile, "captures"))
        val path = source.canonicalPath
        return allowedRoots.any { root -> path.startsWith(root.canonicalPath + File.separator) }
    }
    private fun safeId(value: String): String = value.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(100)
    private fun uniquePath(base: String, paths: MutableSet<String>): String {
        var candidate = "$base.md"
        var suffix = 2
        while (!paths.add(candidate)) candidate = "${base}_${suffix++}.md"
        return candidate
    }
}

internal fun validateArchivePath(path: String) {
    require(path.isNotBlank() && path.length <= 240)
    require(!path.startsWith('/') && !path.startsWith('\\'))
    require('\\' !in path)
    require(path.split('/').none { it == ".." || it.isBlank() })
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private class DigestingOutputStream(output: OutputStream, private val digest: MessageDigest) : FilterOutputStream(output) {
    override fun write(value: Int) { digest.update(value.toByte()); out.write(value) }
    override fun write(bytes: ByteArray, offset: Int, length: Int) { digest.update(bytes, offset, length); out.write(bytes, offset, length) }
}

private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    var count: Long = 0L; private set
    override fun write(value: Int) { out.write(value); count++ }
    override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length); count += length }
}
