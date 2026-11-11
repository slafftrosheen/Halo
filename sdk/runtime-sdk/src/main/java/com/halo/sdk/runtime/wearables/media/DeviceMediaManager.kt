package com.halo.sdk.runtime.wearables.media

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

/** Device names/filenames are data, never paths or instructions. */
data class DeviceMediaEntry(val id: String, val name: String, val kind: String,
    val size: Long? = null, val createdAt: Long? = null)
data class DeviceMediaSelection(val token: String, val file: DeviceMediaEntry)
data class DeviceMediaDownload(val localPath: String, val sha256: String, val bytes: Long)
data class DeviceMediaSavedFile(val objectId: String, val sourceVersion: String, val name: String, val mimeType: String,
    val alreadyImported: Boolean = false, val inTrash: Boolean = false)
data class DeviceMediaState(val deviceId: String? = null, val sourceVersion: String = UUID.randomUUID().toString(),
    val files: List<DeviceMediaSelection> = emptyList(), val operationId: String? = null,
    val phase: String = "IDLE", val currentItem: Int = 0, val totalItems: Int = 0,
    val bytesReceived: Long = 0, val totalBytes: Long? = null,
    val saved: List<DeviceMediaSavedFile> = emptyList(), val failures: Int = 0,
    val failedFiles: List<DeviceMediaEntry> = emptyList()) {
    val busy: Boolean get() = phase in setOf("CONNECTING", "LISTING", "DOWNLOADING", "SAVING", "CANCELLING")
}
interface DeviceMediaSession {
    val files: List<DeviceMediaEntry>
    suspend fun download(file: DeviceMediaEntry, progress: (Long, Long?) -> Unit): DeviceMediaDownload
}
interface DeviceMediaTransport {
    suspend fun <T> withSession(deviceId: String, allowed: () -> Boolean, block: suspend (DeviceMediaSession) -> T): T
}
fun interface DeviceMediaImporter {
    suspend fun save(deviceId: String, source: DeviceMediaEntry, downloaded: DeviceMediaDownload,
        allowed: () -> Boolean): DeviceMediaSavedFile
}

/** One selected device transfer at a time. Completed Library saves survive a later
 * cancellation; partial downloads never become Library objects. No implicit reconnect,
 * inference, deletion, or restoration of interrupted work after process death. */
class DeviceMediaManager(private val transport: DeviceMediaTransport, private val importer: DeviceMediaImporter,
    private val canAccess: () -> Boolean) {
    private val workflow = Mutex()
    private val guard = Any()
    private val mutableState = MutableStateFlow(DeviceMediaState())
    val state: StateFlow<DeviceMediaState> = mutableState.asStateFlow()
    private var activeJob: Job? = null
    private fun version() = UUID.randomUUID().toString()
    private fun access(extra: () -> Boolean) { check(canAccess() && extra()) { "Device file access changed" } }
    private fun update(transform: (DeviceMediaState) -> DeviceMediaState) = synchronized(guard) { mutableState.value = transform(state.value) }

    suspend fun refresh(deviceId: String, allowed: () -> Boolean = { true }): DeviceMediaState {
        require(deviceId.length in 1..200 && deviceId.none(Char::isISOControl)); access(allowed)
        check(workflow.tryLock()) { "A device file operation is already running" }
        try {
            start(deviceId, "CONNECTING")
            transport.withSession(deviceId, { canAccess() && allowed() }) { session ->
                access(allowed)
                val files = validate(session.files)
                update { it.copy(sourceVersion = version(), files = files.map { file -> DeviceMediaSelection(version(), file) }, phase = "LISTING") }
            }
            access(allowed)
            update { it.copy(phase = "READY") }
            return state.value
        } catch (cancelled: CancellationException) { update { it.copy(phase = "CANCELLED", files = emptyList(), sourceVersion = version()) }; throw cancelled }
        catch (error: Exception) { update { it.copy(phase = "FAILED", files = emptyList(), sourceVersion = version()) }; throw error }
        finally { synchronized(guard) { activeJob = null }; workflow.unlock() }
    }
    suspend fun importSelected(deviceId: String, sourceVersion: String, tokens: Set<String>,
        allowed: () -> Boolean = { true }): DeviceMediaState {
        require(tokens.size in 1..MAX_SELECTION); access(allowed)
        check(workflow.tryLock()) { "A device file operation is already running" }
        try {
            val before = state.value
            check(before.deviceId == deviceId && before.sourceVersion == sourceVersion && !before.busy) { "Refresh the selected device files" }
            val selected = tokens.map { token -> before.files.singleOrNull { it.token == token }?.file ?: error("Stale device file selection") }
            check(selected.all { it.size == null || it.size in 1..MAX_FILE_BYTES }) { "Device file exceeds size limit" }
            start(deviceId, "CONNECTING", selected.size, retainFiles = true)
            transport.withSession(deviceId, { canAccess() && allowed() }) { session ->
                val current = validate(session.files).associateBy { it.id }
                check(selected.all { current[it.id] == it }) { "Device manifest changed; refresh before importing" }
                for ((index, file) in selected.withIndex()) {
                    currentCoroutineContext().ensureActive(); access(allowed)
                    update { it.copy(phase = "DOWNLOADING", currentItem = index + 1, bytesReceived = 0, totalBytes = file.size) }
                    try {
                        val downloaded = session.download(file) { received, total ->
                            access(allowed)
                            check(received in 0..MAX_FILE_BYTES && (total == null || total in 1..MAX_FILE_BYTES))
                            update { it.copy(bytesReceived = received, totalBytes = total) }
                        }
                        require(downloaded.bytes in 1..MAX_FILE_BYTES && downloaded.sha256.matches(Regex("[a-f0-9]{64}")))
                        currentCoroutineContext().ensureActive(); access(allowed)
                        update { it.copy(phase = "SAVING") }
                        val saved = importer.save(deviceId, file, downloaded) { canAccess() && allowed() }
                        // A committed Library object must remain visible even if cancellation won the race afterwards.
                        update { it.copy(saved = it.saved + saved) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        access(allowed)
                        update { it.copy(failures = it.failures + 1, failedFiles = it.failedFiles + file) }
                    }
                }
            }
            currentCoroutineContext().ensureActive(); access(allowed)
            update { it.copy(phase = if (it.failures == 0) "COMPLETED" else "PARTIAL") }
            return state.value
        } catch (cancelled: CancellationException) { update { it.copy(phase = "CANCELLED") }; throw cancelled }
        catch (error: Exception) {
            // An invalid/stale request before start must not overwrite an earlier receipt.
            if (state.value.busy) update { it.copy(phase = "FAILED") }
            throw error
        } finally { synchronized(guard) { activeJob = null }; workflow.unlock() }
    }
    private suspend fun start(deviceId: String, phase: String, total: Int = 0, retainFiles: Boolean = false) {
        val job = currentCoroutineContext()[Job] ?: error("Transfer needs an owned job")
        synchronized(guard) {
            activeJob = job
            mutableState.value = DeviceMediaState(deviceId = deviceId, sourceVersion = version(),
                files = if (retainFiles) state.value.files else emptyList(), operationId = version(), phase = phase, totalItems = total)
        }
    }
    /** Cancellation is requested, not proof that a just-committed file was rolled back. */
    fun cancel(deviceId: String, operationId: String): Boolean = synchronized(guard) {
        if (!canAccess() || state.value.deviceId != deviceId || state.value.operationId != operationId || !state.value.busy) return false
        mutableState.value = state.value.copy(phase = "CANCELLING")
        activeJob?.cancel(CancellationException("Device file transfer cancelled by user"))
        true
    }
    private fun validate(files: List<DeviceMediaEntry>): List<DeviceMediaEntry> {
        require(files.size <= MAX_FILES && files.map { it.id }.toSet().size == files.size) { "Invalid or oversized device manifest" }
        files.forEach { file ->
            require(file.id.length in 1..512 && file.id.none(Char::isISOControl) && file.name.length in 1..255 && file.name.none(Char::isISOControl))
            require(file.name != "." && file.name != ".." && '/' !in file.name && '\\' !in file.name)
            require(file.kind in setOf("PHOTO", "VIDEO", "AUDIO", "UNKNOWN"))
            require(file.size == null || file.size > 0)
        }
        return files
    }
    companion object { const val MAX_FILES = 5000; const val MAX_SELECTION = 16; const val MAX_FILE_BYTES = 256L * 1024 * 1024 }
}
