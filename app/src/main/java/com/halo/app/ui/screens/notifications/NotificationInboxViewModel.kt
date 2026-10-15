package com.halo.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.notification.*
import com.halo.app.notification.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

/** UI-safe facts only. Live Android objects never leave their owning registry. */
data class NotificationInboxItem(
    val key: String, val packageName: String, val appLabel: String,
    val title: String?, val body: String?, val category: String, val postedAt: Instant,
    val active: Boolean, val dismissible: Boolean, val reply: ReplyCapability,
)
sealed interface ReplyCapability {
    data object Unsupported : ReplyCapability
    data object NoLongerLive : ReplyCapability
    data class Available(val label: String, val fingerprint: String) : ReplyCapability
}
data class NotificationInboxState(val items: List<NotificationInboxItem> = emptyList(), val connected: Boolean = false)

fun projectNotification(n: HaloNotification, policy: NotificationAccessPolicy, connected: Boolean,
    live: Boolean, replyLabel: String? = null, fingerprint: String? = null): NotificationInboxItem? {
    if (policy != NotificationAccessPolicy.ALLOW) return null
    val actionable = connected && n.active && live
    return NotificationInboxItem(n.key, n.packageName, n.appLabel ?: "App", n.content.title, n.content.body,
        when (n.category) { "msg" -> "Messages"; "event" -> "Calendar"; "call" -> "Calls"; "transport" -> "Media"; else -> "Other" },
        n.postedAt, n.active, actionable, when {
            !actionable -> ReplyCapability.NoLongerLive
            replyLabel != null && fingerprint != null -> ReplyCapability.Available(replyLabel, fingerprint)
            else -> ReplyCapability.Unsupported
        })
}
fun filterInbox(items: List<NotificationInboxItem>, query: String, app: String?): List<NotificationInboxItem> =
    items.filter { (app == null || it.packageName == app) &&
        listOfNotNull(it.appLabel, it.title, it.body).any { text -> text.contains(query.trim(), ignoreCase = true) } }

/** Reuses Room, policy, listener and registry authorities; no inference dependency. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationInboxSource(
    private val repository: NotificationRepository, private val policies: NotificationPolicyManager,
    private val access: NotificationAccessController, private val registry: NotificationReplyRegistry,
    scope: CoroutineScope,
) {
    private val refreshes = MutableStateFlow(0L)
    private val applied = MutableStateFlow(0L)
    val appliedRefresh: StateFlow<Long> = applied.asStateFlow()
    val state = combine(repository.observeRecent(), policies.changes, access.state, registry.changes, refreshes) { rows, _, _, _, generation -> rows to generation }
        .transformLatest { (rows, generation) ->
            while (true) {
                access.refreshIfBridge()
                val now = Instant.now()
                val connected = access.state.value == NotificationListenerState.Connected && LiveNotificationOwner.connected()
                val live = if (connected) LiveNotificationOwner.snapshot()?.map { it.key }?.toSet().orEmpty() else emptySet()
                val items = rows.filter { it.postedAt.plusSeconds(86400) > now }.mapNotNull { row ->
                    val reply = registry.replyActionFor(row.key)
                    projectNotification(row, policies.policyForApp(row.packageName), connected, row.key in live, reply?.label, reply?.actionFingerprint)
                }
                emit(NotificationInboxState(items, connected))
                // Acknowledge only after current policy + listener projection is computed.
                applied.value = generation
                val deadlines = listOfNotNull(registry.nextExpiryMs(), rows.map { it.postedAt.plusSeconds(86400).toEpochMilli() }.filter { it > now.toEpochMilli() }.minOrNull())
                val next = deadlines.minOrNull() ?: break
                delay((next - System.currentTimeMillis()).coerceAtLeast(1))
            }
        }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.WhileSubscribed(5000), NotificationInboxState())
    fun refresh(): Long {
        val next = refreshes.value + 1L
        refreshes.value = next
        return next
    }
    suspend fun currentActionable(key: String): NotificationInboxItem? {
        access.refreshIfBridge()
        val connected = access.state.value == NotificationListenerState.Connected && LiveNotificationOwner.connected()
        val row = repository.observeRecent().first().firstOrNull { it.key == key && it.postedAt.plusSeconds(86400) > Instant.now() } ?: return null
        val live = connected && LiveNotificationOwner.snapshot()?.any { it.key == key } == true
        val reply = registry.replyActionFor(key)
        return projectNotification(row, policies.policyForApp(row.packageName), connected, live, reply?.label, reply?.actionFingerprint)
    }
    fun openAccess() = access.openNotificationAccessSettings()
    suspend fun digest(minutes: Long): NotificationDigest {
        val now = Instant.now(); val from = now.minusSeconds(minutes * 60)
        return DeterministicNotificationDigestSummarizer().summarize(NotificationDigestInput("inbox", from, now, repository.recentForDigest(from, 500)))
    }
}
/** The spinner remains visible until the requested projection was actually rebuilt. */
internal fun notificationRefreshPending(request: Long, applied: Long): Boolean = request > applied

private fun NotificationAccessController.refreshIfBridge() { (this as? NotificationIngestionBridge)?.refresh() }

class NotificationInboxViewModel(val source: NotificationInboxSource) : ViewModel() {
    val query = MutableStateFlow("")
    val app = MutableStateFlow<String?>(null)
    val state = source.state
    val visible = combine(state, query, app) { state, query, app -> filterInbox(state.items, query, app) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val digest = MutableStateFlow<NotificationDigest?>(null)
    val message = MutableStateFlow<String?>(null)
    fun digest(minutes: Long) { viewModelScope.launch(Dispatchers.IO) {
        try { digest.value = source.digest(minutes) } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message.value = "Could not build the local summary" }
    } }
    class Factory(private val source: NotificationInboxSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationInboxViewModel(source) as T
    }
}
