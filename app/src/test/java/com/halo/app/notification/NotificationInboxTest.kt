package com.halo.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.notification.*
import com.halo.app.ui.screens.notifications.*
import com.halo.app.ui.shell.primaryNavigationRoute
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NotificationInboxTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun row(active: Boolean = true) = HaloNotification("key/with?special%chars", "example.app", "Example",
        UntrustedNotificationContent("Private title", "Private body"), "msg", Instant.now(), active)
    @Test fun onlyAllowProjectsIndividualContentAndSearch() {
        val projections = NotificationAccessPolicy.entries.mapNotNull { projectNotification(row(), it, true, true, "Respond", "opaque") }
        assertEquals(1, projections.size)
        assertEquals("Private title", projections.single().title)
        assertEquals(1, filterInbox(projections, "private", null).size)
        assertTrue(filterInbox(projections, "private", "blocked.app").isEmpty())
        assertTrue(filterInbox(projections, "unmatched", null).isEmpty())
    }
    @Test fun removedAndDisconnectedRowsHaveNoLiveActions() {
        listOf(projectNotification(row(false), NotificationAccessPolicy.ALLOW, true, true, "Respond", "id"),
            projectNotification(row(), NotificationAccessPolicy.ALLOW, false, true, "Respond", "id"),
            projectNotification(row(), NotificationAccessPolicy.ALLOW, true, false, "Respond", "id")).forEach { item ->
            assertFalse(item!!.dismissible); assertEquals(ReplyCapability.NoLongerLive, item.reply)
        }
    }
    @Test fun exactAppActionLabelIsProjected() {
        val item = projectNotification(row(), NotificationAccessPolicy.ALLOW, true, true, "Answer privately", "id")!!
        assertEquals(ReplyCapability.Available("Answer privately", "id"), item.reply)
        assertTrue(item.dismissible)
    }
    @Test fun restoredHistoryCannotInventReplySupport() {
        assertEquals(ReplyCapability.NoLongerLive, projectNotification(row(), NotificationAccessPolicy.ALLOW, true, false)!!.reply)
        assertEquals(ReplyCapability.Unsupported, projectNotification(row(), NotificationAccessPolicy.ALLOW, true, true)!!.reply)
    }
    private fun action(): Notification.Action {
        val pending = PendingIntent.getBroadcast(context, 19, Intent("test.REPLY"), PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(0, "Answer privately", pending)
            .addRemoteInput(RemoteInput.Builder("reply").setAllowFreeFormInput(true).build()).build()
    }
    @Test fun registryExpiryChangesCapabilityAndRevision() {
        var now = 100L
        val registry = NotificationReplyRegistry(ttlMs = 20, clock = { now })
        registry.replace("key", listOf(action()))
        assertEquals("Answer privately", registry.replyActionFor("key")!!.label)
        val revision = registry.changes.value
        now = 120
        assertNull(registry.nextExpiryMs()) // Includes entries for filtered-out apps.
        assertNull(registry.replyActionFor("key"))
        assertTrue(registry.changes.value > revision)
    }
    @Test fun replacementInvalidatesConfirmedActionEvenWithSameLabel() {
        val registry = NotificationReplyRegistry()
        registry.replace("key", listOf(action()))
        val before = registry.replyActionFor("key")!!.actionFingerprint
        registry.replace("key", listOf(action()))
        assertNotEquals(before, registry.replyActionFor("key")!!.actionFingerprint)
        assertEquals(NotificationReplyOutcome.NO_REPLY_ACTION, NotificationReplyDispatcher(registry).dispatch(context, "key", "hello", before))
    }
    @Test fun dispatchUsesTruthfulCopyAndNeverDeliveryClaims() {
        val registry = NotificationReplyRegistry()
        registry.replace("key", listOf(action()))
        val outcome = NotificationReplyDispatcher(registry).dispatch(context, "key", "hello", registry.replyActionFor("key")!!.actionFingerprint)
        assertEquals(NotificationReplyOutcome.DISPATCHED, outcome)
        assertEquals("Reply handed to Example\nDelivery not confirmed", replyOutcomeCopy(outcome, "Example"))
        NotificationReplyOutcome.entries.filter { it != NotificationReplyOutcome.DISPATCHED }.forEach { failure ->
            val copy = replyOutcomeCopy(failure, "Example").lowercase()
            assertFalse(copy.contains("sent")); assertFalse(copy.contains("delivered")); assertFalse(copy.contains("handed"))
        }
    }
    @Test fun expiredDispatchAndEmptyDraftNeverDispatch() {
        val dispatcher = NotificationReplyDispatcher(NotificationReplyRegistry())
        assertEquals(NotificationReplyOutcome.NOT_LIVE, dispatcher.dispatch(context, "missing", "hello"))
        assertEquals(NotificationReplyOutcome.EMPTY_REPLY, dispatcher.dispatch(context, "missing", " "))
        assertEquals(NotificationReplyOutcome.EMPTY_REPLY, dispatcher.dispatch(context, "missing", "x".repeat(501)))
    }
    @Test fun deterministicDigestCountsCategoriesWithoutBodies() = runBlocking {
        val digest = DeterministicNotificationDigestSummarizer().summarize(NotificationDigestInput("local", Instant.now().minusSeconds(3600), Instant.now(),
            listOf(row(), row().copy(key = "other", category = "call"))))
        assertEquals(2, digest.notificationCount); assertEquals(1, digest.sourceCount)
        assertEquals(setOf("Messages", "Calls"), digest.sections.map { it.label }.toSet())
        assertFalse(digest.text().contains("Private body")); assertFalse(digest.text().contains("Private title"))
        assertEquals(NotificationSummaryMode.DETERMINISTIC, digest.summaryMode)
    }
    @Test fun secondaryRoutesKeepHomeSelected() {
        assertEquals("home", primaryNavigationRoute("notifications"))
        assertEquals("home", primaryNavigationRoute("notifications/{notificationKey}"))
    }

    @Test fun repositoryPolicyChangeImmediatelyRemovesRowsButSummarizeOnlyStillCounts() = runBlocking {
        val policies = NotificationPolicyManager(context)
        val dao = FakeDao()
        val repository = RoomNotificationRepository(dao, policies, "halo")
        policies.setPolicyForApp("example.app", NotificationAccessPolicy.ALLOW)
        repository.ingest(IncomingNotification("key", "example.app", "Example", "Private title", "Private body", "msg", Instant.now(), true))
        assertEquals(1, repository.observeRecent().first().size)
        repository.setPolicy("example.app", NotificationAccessPolicy.SUMMARIZE_ONLY)
        assertTrue(repository.observeRecent().first().isEmpty())
        assertEquals(1, repository.recentForDigest(Instant.now().minusSeconds(60), 500).size)
        repository.setPolicy("example.app", NotificationAccessPolicy.HIDE_CONTENT)
        assertTrue(repository.observeRecent().first().isEmpty()); assertNull(dao.rows.value.single().text)
        assertTrue(repository.recentForDigest(Instant.now().minusSeconds(60), 500).isEmpty())
        repository.setPolicy("example.app", NotificationAccessPolicy.BLOCK)
        assertTrue(dao.rows.value.isEmpty())
    }
    private class FakeDao : NotificationDao {
        val rows = MutableStateFlow<List<NotificationEntity>>(emptyList())
        override suspend fun activeByKey(key: String) = rows.value.firstOrNull { it.key == key && it.active }
        override suspend fun upsert(entity: NotificationEntity) { rows.value = rows.value.filterNot { it.key == entity.key } + entity }
        override suspend fun markRemoved(key: String, removedAt: Long) { rows.value = rows.value.map { if (it.key == key) it.copy(active = false, removedAt = removedAt) else it } }
        override fun observeActive() = rows.map { it.filter { row -> row.active } }
        override fun observeRecent(): Flow<List<NotificationEntity>> = rows
        override suspend fun recentSince(since: Long, limit: Int) = rows.value.filter { it.postedAt >= since }.take(limit)
        override suspend fun deleteOlderThan(timestamp: Long) { rows.value = rows.value.filter { it.postedAt >= timestamp } }
        override suspend fun deletePackage(pkg: String) { rows.value = rows.value.filterNot { it.packageName == pkg } }
        override suspend fun redactPackage(pkg: String) { rows.value = rows.value.map { if (it.packageName == pkg) it.copy(title = null, text = null) else it } }
        override suspend fun deleteKey(key: String) { rows.value = rows.value.filterNot { it.key == key } }
        override suspend fun reconcileActive(keys: List<String>, at: Long) { rows.value = rows.value.map { if (it.key !in keys) it.copy(active = false) else it } }
        override suspend fun packages() = rows.value.map { it.packageName }.distinct()
        override suspend fun clear() { rows.value = emptyList() }
    }
}
