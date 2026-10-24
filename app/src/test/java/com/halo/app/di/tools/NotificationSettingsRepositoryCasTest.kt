package com.halo.app.di.tools

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.notification.*
import com.halo.app.data.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NotificationSettingsRepositoryCasTest {
    @Test fun policyCasSharesUiOwnerAndRetainsBlockPurgeAndHideRedaction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("halo_notification_policies", 0).edit().clear().commit()
        val policies = NotificationPolicyManager(context)
        val dao = Dao()
        val owner = RoomNotificationRepository(dao, policies, "com.halo")
        owner.setPolicy("com.example.notes", NotificationAccessPolicy.ALLOW)
        owner.ingest(IncomingNotification("notification", "com.example.notes", "Notes", "Private title", "Private body",
            null, Instant.now(), publicContent = true))
        val before = owner.policySnapshot("com.example.notes")
        assertEquals(1, dao.rows.value.size)
        assertFalse(owner.compareAndSetPolicy("com.other.app", before.sourceVersion, NotificationAccessPolicy.ALLOW))
        assertTrue(owner.compareAndSetPolicy("com.example.notes", before.sourceVersion, NotificationAccessPolicy.HIDE_CONTENT))
        assertNull(dao.rows.value.single().title); assertNull(dao.rows.value.single().text)
        assertFalse(owner.compareAndSetPolicy("com.example.notes", before.sourceVersion, NotificationAccessPolicy.ALLOW))
        val current = owner.policySnapshot("com.example.notes")
        assertTrue(owner.compareAndSetPolicy("com.example.notes", current.sourceVersion, NotificationAccessPolicy.BLOCK))
        assertTrue(dao.rows.value.isEmpty()); assertEquals(NotificationAccessPolicy.BLOCK, policies.policyForApp("com.example.notes"))
        val restarted = RoomNotificationRepository(dao, policies, "com.halo")
        assertFalse(restarted.compareAndSetPolicy("com.example.notes", owner.policySnapshot("com.example.notes").sourceVersion, NotificationAccessPolicy.ALLOW))
    }

    @Test fun deliveryCasAtomicallyPersistsBothFieldsAndRejectsUiChangedSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("halo_digest_delivery", 0).edit().clear().commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val owner = SharedPreferencesDigestDeliverySettingsRepository(context, scope)
            withTimeout(3000) { while (!owner.ready) delay(5) }
            val source = owner.state.value.sourceVersion()
            assertTrue(owner.compareAndSet(source, DigestDeliverySettings("wearable", "AA:01")))
            val prefs = context.getSharedPreferences("halo_digest_delivery", 0)
            assertEquals("wearable", prefs.getString("channel", null)); assertEquals("AA:01", prefs.getString("device", null))
            assertFalse(owner.compareAndSet(source, DigestDeliverySettings("android", "")))
            val current = owner.state.value.sourceVersion()
            owner.setChannel("activity") // Existing UI operation shares the same repository mutex/source.
            assertFalse(owner.compareAndSet(current, DigestDeliverySettings("android", "")))
            assertEquals("activity", owner.state.value.channel)
            var invalidRejected = false
            try { owner.compareAndSet(owner.state.value.sourceVersion(), DigestDeliverySettings("wearable", "")) }
            catch (_: IllegalArgumentException) { invalidRejected = true }
            assertTrue(invalidRejected); assertEquals("activity", owner.state.value.channel)
        } finally { scope.cancel() }
    }

    private class Dao : NotificationDao {
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
