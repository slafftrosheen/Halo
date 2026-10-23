package com.halo.app.ai

import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

enum class PrivacyMode { PRIVATE, CLOUD }
const val PRIVACY_MODE_KEY = "ai_privacy_mode_v1"

/** Null is legacy data. Invalid saved modes fail closed. */
fun Storage.savedPrivacyMode(): PrivacyMode? = getString(PRIVACY_MODE_KEY)?.let {
    runCatching { PrivacyMode.valueOf(it) }.getOrDefault(PrivacyMode.PRIVATE)
}
/** One durable sharing decision. It grants neither Android permissions nor action/spend approval. */
class PrivacyModeStore(private val backing: Storage) : RemoteDispatchPolicy {
    private val lock = Any()
    private var activating = false
    private var loadError: String? = null
    private var snapshot: ModeProfiles = try {
        backing.getString(MODE_PROFILES_KEY)?.let(ModeProfiles::decode) ?: ModeProfiles.migrate(backing)
    } catch (_: Exception) {
        loadError = "Mode profiles could not be restored. Private mode is active. Review Mode profiles."
        ModeProfiles(PrivacyMode.PRIVATE, ModeProfiles.defaults(PrivacyMode.PRIVATE),
            ModeProfiles.defaults(PrivacyMode.CLOUD), false, 0)
    }
    private val leases = mutableSetOf<Lease>()
    private val current = MutableStateFlow(snapshot.active)
    val mode = current.asStateFlow()
    private val changes = MutableStateFlow(snapshot.revision)
    val profileChanges = changes.asStateFlow()
    val storage: Storage = ModeProfileStorage(backing, this)
    val activity = RemoteActivityHistory(backing)
    val error: String? get() = synchronized(lock) { loadError }
    val sharingAcknowledged: Boolean get() = synchronized(lock) { snapshot.sharingAcknowledged }
    override val revision: Long get() = synchronized(lock) { snapshot.revision }

    /** Additional existing parental authority, composed once; never persisted as consent. */
    var remoteAllowed: () -> Boolean = { false }
    var configuredProviders: () -> Set<String> = { emptySet() }

    init {
        // The old mode setter granted this automatically. Its legacy true value is not
        // evidence of a separate spending choice. Do this only during migration.
        val migrating = runCatching { backing.getString(MODE_PROFILES_KEY) == null }.getOrDefault(true) || loadError != null
        try { backing.putBatch(strings = mapOf(MODE_PROFILES_KEY to snapshot.encode(),
            PRIVACY_MODE_KEY to snapshot.active.name), booleans = if (migrating)
            mapOf(PaidMediaPolicy.CONSENT_KEY to false) else emptyMap()) }
        catch (_: Exception) {
            snapshot = snapshot.copy(active = PrivacyMode.PRIVATE, sharingAcknowledged = false)
            current.value = PrivacyMode.PRIVATE
            loadError = "Mode profiles could not be saved. Private mode is active. Check available storage and retry."
        }
    }

    fun profile(mode: PrivacyMode): ModeProfile = synchronized(lock) { snapshot.profile(mode) }
    internal fun activeProfile(): ModeProfile = synchronized(lock) { snapshot.profile(snapshot.active) }
    internal fun activeMode(): PrivacyMode = synchronized(lock) { snapshot.active }
    internal fun permitsRemoteCategories(provenance: RemoteDispatchProvenance): Boolean = synchronized(lock) {
        !activating && snapshot.active == PrivacyMode.CLOUD && snapshot.sharingAcknowledged &&
            provenance.revision == snapshot.revision && snapshot.powerProfile.enabledRemoteCategories().containsAll(provenance.categories)
    }
    internal fun effectiveContextPolicy(): AiContextPolicy = synchronized(lock) {
        val values = snapshot.profile(snapshot.active).booleans
        val remote = snapshot.active == PrivacyMode.CLOUD && snapshot.sharingAcknowledged && !activating
        AiContextPolicy(includeConversation = values[KEY_CONVERSATION] == true,
            includeDeviceState = values[KEY_DEVICE_STATE] == true, includeRecentEvents = values[KEY_RECENT_EVENTS] == true,
            includeMemories = values[KEY_MEMORIES] == true, includeAvailableTools = values[KEY_TOOLS] == true,
            includeLocation = values[KEY_LOCATION] == true, includeImages = values[KEY_IMAGES] == true,
            includeAudio = values[KEY_AUDIO] == true, includeVideo = values[KEY_VIDEO] == true,
            includeWorkspace = values[KEY_WORKSPACE] == true,
            allowCloudWorkspace = remote && values[KEY_CLOUD_WORKSPACE] == true,
            allowCloudScreenContext = remote && values[KEY_CLOUD_SCREEN] == true,
            allowCloudImages = remote && values[KEY_CLOUD_IMAGES] == true,
            allowCloudMemories = remote && values["policy_cloud_memories"] == true)
    }
    internal fun editActive(change: (ModeProfile) -> ModeProfile) = synchronized(lock) {
        update(snapshot.replacing(snapshot.active, change(snapshot.profile(snapshot.active))))
    }
    fun edit(mode: PrivacyMode, prepare: () -> Unit = {}, recoverPrivate: () -> Unit = {},
        change: (ModeProfile) -> ModeProfile) = synchronized(lock) {
        val profile = change(snapshot.profile(mode))
        require(profile.strings.keys.all { it in profileStringKeys } && profile.booleans.keys == profileBooleanKeys)
        require(mode != PrivacyMode.PRIVATE || profile.onlineFeatures.isEmpty())
        val active = mode == snapshot.active
        if (active) activating = true
        try {
            update(snapshot.replacing(mode, profile))
            if (active) prepare()
            if (active) loadError = null
        } catch (failure: Exception) {
            if (active) {
                snapshot = snapshot.copy(active = PrivacyMode.PRIVATE, revision = snapshot.revision + 1)
                current.value = PrivacyMode.PRIVATE
                loadError = "Profile could not be applied. Private mode is active. Review Mode profiles."
                revoke()
                runCatching { persist(snapshot) }
                changes.value = snapshot.revision
                runCatching(recoverPrivate)
            }
            throw failure
        } finally { if (active) activating = false }
    }
    fun acknowledgeSharing() = synchronized(lock) { update(snapshot.copy(sharingAcknowledged = true)) }
    internal fun clearAll() = synchronized(lock) {
        activating = true
        revoke()
        snapshot = ModeProfiles(PrivacyMode.PRIVATE, ModeProfiles.defaults(PrivacyMode.PRIVATE),
            ModeProfiles.defaults(PrivacyMode.CLOUD), false, snapshot.revision + 1)
        current.value = PrivacyMode.PRIVATE
        changes.value = snapshot.revision
        try { backing.clear(); persist(snapshot); activity.clear(); loadError = null }
        catch (failure: Exception) { loadError = "Settings could not be cleared completely. Private mode is active."; throw failure }
        finally { activating = false }
    }
    fun invalidateAuthority() = synchronized(lock) {
        // Revocation is immediate even if the disk cannot accept a new revision.
        revoke()
        try { update(snapshot) }
        catch (_: Exception) {
            snapshot = snapshot.copy(active = PrivacyMode.PRIVATE, revision = snapshot.revision + 1)
            current.value = PrivacyMode.PRIVATE
            changes.value = snapshot.revision
            loadError = "Policy could not be saved. Private mode is active."
        }
    }

    /** Preparation happens behind a closed dispatch gate; publication follows durable success. */
    fun set(mode: PrivacyMode, recoverPrivate: () -> Unit = {}, prepare: () -> Unit = {}) = synchronized(lock) {
        check(mode != PrivacyMode.CLOUD || snapshot.sharingAcknowledged) {
            "Review the Full Power sharing explanation first."
        }
        activating = true
        revoke()
        try {
            update(snapshot.copy(active = mode))
            prepare()
            loadError = null
            current.value = mode
        } catch (failure: Exception) {
            // Keep dispatch closed even when persistence itself is unavailable.
            snapshot = snapshot.copy(active = PrivacyMode.PRIVATE, revision = snapshot.revision + 1)
            current.value = PrivacyMode.PRIVATE
            loadError = "Mode activation failed. Private mode is active. Review Mode profiles."
            runCatching { persist(snapshot) }
            changes.value = snapshot.revision
            runCatching(recoverPrivate)
            throw failure
        } finally { activating = false }
    }

    private fun persist(next: ModeProfiles) = backing.putBatch(strings = mapOf(
        MODE_PROFILES_KEY to next.encode(), PRIVACY_MODE_KEY to next.active.name))
    private fun update(next: ModeProfiles) {
        val versioned = next.copy(revision = snapshot.revision + 1)
        persist(versioned)
        snapshot = versioned
        revoke()
        changes.value = snapshot.revision
    }
    private fun revoke() {
        leases.toList().forEach { lease -> lease.finish(RemoteDispatchOutcome.CANCELLED); runCatching(lease.cancel) }
    }

    override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean,
        cancel: () -> Unit): RemoteDispatchLease = synchronized(lock) {
        if (!permits(request, expectedRevision)) {
            if (recordActivity) runCatching { activity.record(request.copy(providerId = request.providerId.takeIf {
                it in configuredProviders() } ?: "unavailable"), RemoteDispatchOutcome.BLOCKED) }
            throw RemoteDispatchBlocked()
        }
        val receiptId = if (recordActivity) try { activity.record(request, RemoteDispatchOutcome.STARTED) }
            catch (_: Exception) { loadError = "Online activity could not be saved. Check available storage."; throw RemoteDispatchBlocked() }
            else null
        Lease(request, expectedRevision, receiptId, cancel).also(leases::add)
    }

    private fun permits(request: RemoteDispatchRequest, expectedRevision: Long): Boolean {
        return try { permitsChecked(request, expectedRevision) } catch (_: Exception) { false }
    }
    private fun permitsChecked(request: RemoteDispatchRequest, expectedRevision: Long): Boolean {
        if (activating || snapshot.active != PrivacyMode.CLOUD || !snapshot.sharingAcknowledged ||
            snapshot.revision != expectedRevision || !remoteAllowed() || request.providerId !in configuredProviders()) return false
        val profile = snapshot.powerProfile
        val feature = when (request.operation) {
            RemoteOperation.INFERENCE, RemoteOperation.PROVIDER_CHECK -> OnlineFeature.INFERENCE
            RemoteOperation.HOSTED_SPEECH -> OnlineFeature.SPEECH
            RemoteOperation.WEB -> OnlineFeature.WEB
            RemoteOperation.IMAGE -> OnlineFeature.IMAGES
            RemoteOperation.CONNECTED_SERVICE -> OnlineFeature.CONNECTED_SERVICES
        }
        if (feature !in profile.onlineFeatures) return false
        if (RemoteDataCategory.IMAGES in request.categories && OnlineFeature.IMAGES !in profile.onlineFeatures) return false
        if (request.operation == RemoteOperation.INFERENCE && request.categories.any {
            it == RemoteDataCategory.IMAGES || it == RemoteDataCategory.AUDIO || it == RemoteDataCategory.VIDEO
        } && !backing.getBoolean(PaidMediaPolicy.CONSENT_KEY, false)) return false
        return request.categories.all { it in profile.enabledRemoteCategories() }
    }

    private inner class Lease(val request: RemoteDispatchRequest, val version: Long, val receiptId: String?,
        val cancel: () -> Unit) : RemoteDispatchLease {
        var finished = false
        override fun requireCurrent() = synchronized(lock) {
            if (finished || !permits(request, version)) throw RemoteDispatchBlocked()
        }
        override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) = synchronized(lock) {
            if (!finished) {
                finished = true
                leases.remove(this)
                receiptId?.let { id -> runCatching { activity.finish(id, outcome, providerReportedCostUsd) } }
            }
        }
        override fun reportProviderCostUsd(value: String) = synchronized(lock) {
            receiptId?.let { id -> runCatching { activity.reportProviderCost(id, value) } }
            Unit
        }
    }
}

/** A transition to Private cancels the actual cancellable HTTP operation, not just its preview. */
suspend fun <T> whileCloudMode(modes: StateFlow<PrivacyMode>?, onPrivate: () -> Unit = {}, block: suspend () -> T): T {
    if (modes == null) return block()
    return coroutineScope {
        check(modes.value == PrivacyMode.CLOUD) { "Switch to Full Power mode at the top of Halo." }
        val task = async(start = CoroutineStart.LAZY) { block() }
        val watch = launch(start = CoroutineStart.UNDISPATCHED) {
            modes.collect { if (it == PrivacyMode.PRIVATE) {
                runCatching(onPrivate)
                task.cancel(CancellationException("Private mode enabled"))
            } }
        }
        try { task.await() } finally { watch.cancel() }
    }
}
