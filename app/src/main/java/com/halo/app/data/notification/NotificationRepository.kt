package com.halo.app.data.notification

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.Duration

data class IncomingNotification(val key: String, val packageName: String, val appLabel: String?,
    val title: String?, val body: String?, val category: String?, val postedAt: Instant,
    val publicContent: Boolean, val foregroundService: Boolean = false, val groupSummary: Boolean = false)
data class HaloNotification(val key: String, val packageName: String, val appLabel: String?,
    val content: UntrustedNotificationContent, val category: String?, val postedAt: Instant, val active: Boolean)
interface NotificationRepository {
    fun observeRecent(): Flow<List<HaloNotification>>
    suspend fun ingest(notification: IncomingNotification)
    suspend fun markRemoved(key: String, removedAt: Instant)
    suspend fun recentForDigest(since: Instant, limit: Int): List<HaloNotification>
    suspend fun prune(before: Instant)
}

/** No AI, tool, memory or conversation dependency is permitted at this boundary. */
class RoomNotificationRepository(private val dao: NotificationDao,
    private val policies: NotificationPolicyManager, private val ownPackage: String) : NotificationRepository {
    private val mutex = Mutex()
    override fun observeRecent() = dao.observeRecent().map { rows -> rows.filter {
        policies.policyForApp(it.packageName) == NotificationAccessPolicy.ALLOW &&
            it.postedAt >= Instant.now().minus(RETENTION).toEpochMilli()
    }.map(::domain) }
    override suspend fun ingest(notification: IncomingNotification) = mutex.withLock {
        val n = notification
        val policy = policies.policyForApp(n.packageName)
        if (n.packageName == ownPackage || policy == NotificationAccessPolicy.BLOCK ||
            n.foregroundService || n.groupSummary || (n.title.isNullOrBlank() && n.body.isNullOrBlank())) {
            dao.deleteKey(n.key)
        } else {
            val content = n.publicContent && policy != NotificationAccessPolicy.HIDE_CONTENT
            dao.upsert(NotificationEntity(n.key, n.packageName, n.appLabel?.take(200),
                n.title?.take(512).takeIf { content }, n.body?.take(4096).takeIf { content }, n.category,
                n.postedAt.toEpochMilli(), null, true, if (n.publicContent) "NORMAL" else "REDACTED"))
        }
        dao.deleteOlderThan(Instant.now().minus(RETENTION).toEpochMilli())
    }
    suspend fun setPolicy(pkg: String, policy: NotificationAccessPolicy) = mutex.withLock {
        policies.setPolicyForApp(pkg, policy)
        when (policy) {
            NotificationAccessPolicy.BLOCK -> dao.deletePackage(pkg)
            NotificationAccessPolicy.HIDE_CONTENT -> dao.redactPackage(pkg)
            else -> Unit
        }
    }
    suspend fun reconcilePolicies() = mutex.withLock {
        dao.packages().forEach { pkg ->
            when (policies.policyForApp(pkg)) {
                NotificationAccessPolicy.BLOCK -> dao.deletePackage(pkg)
                NotificationAccessPolicy.HIDE_CONTENT -> dao.redactPackage(pkg)
                else -> Unit
            }
        }
        prune(Instant.now().minus(RETENTION))
    }
    override suspend fun markRemoved(key: String, removedAt: Instant) = dao.markRemoved(key, removedAt.toEpochMilli())
    suspend fun reconcileActive(keys: List<String>) = dao.reconcileActive(keys, Instant.now().toEpochMilli())
    override suspend fun recentForDigest(since: Instant, limit: Int): List<HaloNotification> = mutex.withLock {
        val cutoff = Instant.now().minus(RETENTION)
        prune(cutoff)
        dao.recentSince(maxOf(since, cutoff).toEpochMilli(), limit.coerceIn(1, 500))
            .filter { policies.policyForApp(it.packageName) in setOf(NotificationAccessPolicy.ALLOW, NotificationAccessPolicy.SUMMARIZE_ONLY) }
            .map(::domain)
    }
    override suspend fun prune(before: Instant) = dao.deleteOlderThan(before.toEpochMilli())
    private fun domain(n: NotificationEntity): HaloNotification {
        val visible = policies.policyForApp(n.packageName) in setOf(NotificationAccessPolicy.ALLOW, NotificationAccessPolicy.SUMMARIZE_ONLY)
        return HaloNotification(n.key, n.packageName, n.appLabel,
            UntrustedNotificationContent(n.title.takeIf { visible }, n.text.takeIf { visible }), n.category,
            Instant.ofEpochMilli(n.postedAt), n.active)
    }
    companion object { val RETENTION: Duration = Duration.ofHours(24) }
}
