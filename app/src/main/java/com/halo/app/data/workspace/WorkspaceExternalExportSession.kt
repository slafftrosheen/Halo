package com.halo.app.data.workspace

import android.net.Uri
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class ExternalExportPhase { CHOOSE, PICKER, WRITING, VERIFYING, VERIFIED, CANCELLED, FAILED, UNKNOWN }
data class ExternalExportSession(
    val id: String, val version: String, val name: String, val mime: String,
    val phase: ExternalExportPhase = ExternalExportPhase.CHOOSE,
    val destination: String? = null, val outcome: ExternalExportOutcome? = null,
    val pickerToken: String? = null, val resumePhase: ExternalExportPhase? = null,
    val pickerCancelled: Boolean = false, val priorDestinations: List<String> = emptyList(),
) {
    val busy get() = phase in setOf(ExternalExportPhase.PICKER, ExternalExportPhase.WRITING, ExternalExportPhase.VERIFYING)
    val source get() = WorkspaceFileVersion(id, version)
}

/** One explicit transfer at a time. The journal records uncertainty, never a command to replay. */
class WorkspaceExternalExportSession(
    private val storage: Storage, private val exporter: WorkspaceExternalExport,
    private val scope: CoroutineScope, private val allowed: () -> Boolean,
) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow<ExternalExportSession?>(null)
    val state = mutable.asStateFlow()
    private var loaded = false
    @Volatile private var handedOffPicker: String? = null
    private val key = "workspace.externalExport.receipt.v1"
    fun isAllowed(): Boolean = allowed()

    suspend fun load() = withContext(Dispatchers.IO) { lock.withLock { loadLocked() } }
    private fun loadLocked() {
        if (loaded) return
        val raw = storage.getString(key)
        if (raw != null) {
            require(raw.length <= 20_000)
            val json = JSONObject(raw)
            val phase = ExternalExportPhase.valueOf(json.getString("phase"))
            val previous = json.optString("resumePhase").takeIf(String::isNotBlank)?.let(ExternalExportPhase::valueOf)
            val session = ExternalExportSession(json.getString("id"), json.getString("version"), json.getString("name"), json.getString("mime"),
                when (phase) {
                    ExternalExportPhase.WRITING, ExternalExportPhase.VERIFYING -> ExternalExportPhase.UNKNOWN
                    ExternalExportPhase.PICKER -> previous?.takeIf { it != ExternalExportPhase.CHOOSE } ?: ExternalExportPhase.CANCELLED
                    else -> phase
                }, json.optString("destination").takeIf { it.isNotEmpty() },
                json.optString("outcome").takeIf { it.isNotEmpty() }?.let(ExternalExportOutcome::valueOf),
                json.optString("pickerToken").takeIf(String::isNotBlank), previous, json.optBoolean("pickerCancelled"),
                json.optJSONArray("priorDestinations")?.let { array ->
                    require(array.length() <= 8)
                    (0 until array.length()).map { array.getString(it).also { value -> require(value.length <= 2048 && Uri.parse(value).scheme == "content") } }
                }.orEmpty())
            session.source // Validate journal identifiers before presentation or recovery.
            require(session.name.length <= 240 && session.mime.length <= 100)
            require(session.destination == null || session.destination.length <= 2048 && Uri.parse(session.destination).scheme == "content")
            mutable.value = session
        }
        loaded = true
    }
    private fun persist(value: ExternalExportSession?) {
        val raw = value?.let { JSONObject().put("id", it.id).put("version", it.version).put("name", it.name)
            .put("mime", it.mime).put("phase", it.phase.name).put("destination", it.destination.orEmpty())
            .put("outcome", it.outcome?.name.orEmpty()).put("pickerToken", it.pickerToken.orEmpty())
            .put("resumePhase", it.resumePhase?.name.orEmpty()).put("pickerCancelled", it.pickerCancelled)
            .put("priorDestinations", org.json.JSONArray(it.priorDestinations)).toString() }
        require(raw == null || raw.length <= 20_000)
        storage.putString(key, raw)
        check(storage.getString(key) == raw)
        mutable.value = value
    }
    suspend fun request(file: WorkspaceFile) = withContext(Dispatchers.IO) { lock.withLock {
        check(allowed()); loadLocked()
        check(mutable.value == null) // Review/dismiss the prior receipt before replacing it.
        require(file.ownership == ContentOwnership.HALO_OWNED && file.sha256 != null)
        persist(ExternalExportSession(file.objectId.value, requireNotNull(file.version), file.displayName.take(240), file.mimeType))
    } }
    suspend fun picking(): ExternalExportSession = withContext(Dispatchers.IO) { lock.withLock {
        check(allowed())
        val current = requireNotNull(mutable.value)
        check(!current.busy && current.priorDestinations.size < 8)
        val next = current.copy(phase = ExternalExportPhase.PICKER, resumePhase = current.phase,
            pickerToken = java.util.UUID.randomUUID().toString(), pickerCancelled = false)
        persist(next)
        next
    } }
    /** Called on the UI thread immediately before launch; there is no suspension before system handoff. */
    fun markPickerLaunched(token: String): Boolean {
        val current = mutable.value
        if (!allowed() || current?.phase != ExternalExportPhase.PICKER || current.pickerToken != token) return false
        handedOffPicker = token
        return true
    }
    /** A disposed host cannot strand a persisted admission that never opened system UI. */
    fun abandonUnlaunchedPicker() {
        val abandoned = mutable.value?.takeIf { it.phase == ExternalExportPhase.PICKER }?.pickerToken ?: return
        scope.launch(Dispatchers.IO) { lock.withLock {
            val current = mutable.value ?: return@withLock
            if (current.phase != ExternalExportPhase.PICKER || current.pickerToken != abandoned || current.pickerToken == handedOffPicker) return@withLock
            val cancelled = current.copy(phase = current.resumePhase?.takeIf { current.destination != null } ?: ExternalExportPhase.CANCELLED,
                pickerToken = null, resumePhase = null, pickerCancelled = true)
            runCatching { persist(cancelled) }.onFailure { mutable.value = cancelled.copy(phase = ExternalExportPhase.UNKNOWN) }
        } }
    }
    fun picked(uri: Uri?, token: String?) {
        scope.launch(Dispatchers.IO) {
            val next = lock.withLock {
                try { loadLocked() } catch (_: Exception) { return@launch }
                val current = mutable.value ?: return@launch
                if (token == null || token != current.pickerToken || current.phase in setOf(ExternalExportPhase.WRITING, ExternalExportPhase.VERIFYING)) return@launch
                if (uri == null || !allowed()) {
                    runCatching { persist(current.copy(phase = current.resumePhase?.takeIf { current.destination != null } ?: ExternalExportPhase.CANCELLED,
                        pickerToken = null, pickerCancelled = true)) }
                        .onFailure { mutable.value = current.copy(phase = ExternalExportPhase.UNKNOWN) }
                    return@launch
                }
                if (uri.scheme != "content" || uri.toString().length > 2048) {
                    val failed = current.copy(phase = if (current.destination != null) current.resumePhase ?: ExternalExportPhase.UNKNOWN else ExternalExportPhase.FAILED,
                        outcome = if (current.destination != null) current.outcome else ExternalExportOutcome.ACCESS_DENIED,
                        pickerToken = null, resumePhase = null, pickerCancelled = true)
                    runCatching { persist(failed) }.onFailure {
                        mutable.value = failed.copy(phase = if (current.destination != null) ExternalExportPhase.UNKNOWN else ExternalExportPhase.FAILED)
                    }
                    return@launch
                }
                val next = current.copy(phase = ExternalExportPhase.WRITING, destination = uri.toString(), outcome = null,
                    pickerToken = null, resumePhase = null, priorDestinations =
                        (current.priorDestinations + listOfNotNull(current.destination)).distinct())
                try { persist(next) }
                catch (_: Exception) { mutable.value = next.copy(phase = ExternalExportPhase.UNKNOWN); return@launch }
                next
            }
            finish(next) { exporter.write(next.source, uri!!) }
        }
    }
    /** A new explicit check reads only; choosing a different location uses a fresh system picker. */
    fun verify() {
        scope.launch(Dispatchers.IO) {
            val next = lock.withLock {
                val current = mutable.value ?: return@launch
                if (current.busy || current.destination == null || !allowed()) return@launch
                val next = current.copy(phase = ExternalExportPhase.VERIFYING)
                try { persist(next) }
                catch (_: Exception) { mutable.value = current.copy(phase = ExternalExportPhase.UNKNOWN); return@launch }
                next
            }
            finish(next) { exporter.verify(next.source, Uri.parse(next.destination)) }
        }
    }
    private suspend fun finish(expected: ExternalExportSession, operation: suspend () -> ExternalExportReceipt) {
        try {
            val receipt = operation()
            lock.withLock {
                if (mutable.value != expected) return@withLock
                val next = expected.copy(phase = when (receipt.outcome) {
                    ExternalExportOutcome.VERIFIED -> ExternalExportPhase.VERIFIED
                    ExternalExportOutcome.UNKNOWN -> ExternalExportPhase.UNKNOWN
                    else -> ExternalExportPhase.FAILED
                }, outcome = receipt.outcome)
                try { persist(next) }
                catch (_: Exception) { mutable.value = next.copy(phase = ExternalExportPhase.UNKNOWN) }
            }
        } catch (cancelled: CancellationException) {
            mutable.value = expected.copy(phase = ExternalExportPhase.UNKNOWN)
            throw cancelled // Pending journal survives; no automatic write on restart.
        }
    }
    suspend fun dismiss() = withContext(Dispatchers.IO) { lock.withLock {
        check(mutable.value?.busy != true)
        persist(null)
    } }
}
