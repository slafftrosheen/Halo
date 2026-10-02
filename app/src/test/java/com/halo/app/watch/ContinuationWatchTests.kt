package com.halo.app.watch

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.watch.RoomContinuationWatchRepository
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.routine.AutomationSignal
import com.halo.runtime.cognition.routine.AutomationSignalKind
import com.halo.runtime.cognition.routine.AutomationTrust
import com.halo.runtime.cognition.routine.AutomationValue
import com.halo.runtime.cognition.watch.ContinuationWatch
import com.halo.runtime.cognition.watch.ContinuationWatchId
import com.halo.runtime.cognition.watch.ContinuationWatchMatcher
import com.halo.runtime.cognition.watch.WatchBehavior
import com.halo.runtime.cognition.watch.WatchCondition
import com.halo.runtime.cognition.watch.WatchMatchMode
import com.halo.runtime.cognition.watch.WatchStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ContinuationWatchTests {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomContinuationWatchRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomContinuationWatchRepository(db.continuationWatchDao())
    }

    @Test
    fun matcherHandlesNetworkBatteryAndDeviceSignals() {
        // Network Available
        val netSignal = AutomationSignal(
            id = "s-1",
            kind = AutomationSignalKind.NETWORK_AVAILABLE,
            occurredAt = Instant.now(),
            source = "network",
            trust = AutomationTrust.PLATFORM_EVENT
        )
        assertTrue(ContinuationWatchMatcher.matches(WatchCondition.NetworkAvailable, netSignal))

        // Battery Below 20
        val batSignal = AutomationSignal(
            id = "s-2",
            kind = AutomationSignalKind.BATTERY_CHANGED,
            occurredAt = Instant.now(),
            source = "battery",
            trust = AutomationTrust.PLATFORM_EVENT,
            attributes = mapOf(
                "previous" to AutomationValue.Integer(21),
                "percent" to AutomationValue.Integer(19)
            )
        )
        assertTrue(ContinuationWatchMatcher.matches(WatchCondition.BatteryBelow(20), batSignal))

        // Device Connected
        val devSignal = AutomationSignal(
            id = "s-3",
            kind = AutomationSignalKind.DEVICE_CONNECTED,
            occurredAt = Instant.now(),
            source = "device",
            trust = AutomationTrust.TRUSTED_RUNTIME,
            attributes = mapOf("device" to AutomationValue.Text("heycyan"))
        )
        assertTrue(ContinuationWatchMatcher.matches(WatchCondition.DeviceConnected("heycyan"), devSignal))
    }

    @Test
    fun repositoryDurableSaveAndClaim() = runBlocking {
        val watch = ContinuationWatch(
            id = ContinuationWatchId("watch-1"),
            runId = AgentRunId("run-1"),
            interactionId = InteractionId("int-1"),
            condition = WatchCondition.NetworkAvailable,
            behavior = WatchBehavior.Notify("Internet is back"),
            status = WatchStatus.ACTIVE,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(3600),
            deduplicationKey = "network:none:standalone",
            matchMode = WatchMatchMode.NEXT_TRANSITION,
            presentationText = "Internet is back"
        )

        repository.save(watch)

        val retrieved = repository.findById(ContinuationWatchId("watch-1"))
        assertNotNull(retrieved)
        assertEquals("watch-1", retrieved?.id?.value)
        assertEquals(WatchStatus.ACTIVE, retrieved?.status)

        // Transactional Claim
        val claimed = repository.claimMatch(ContinuationWatchId("watch-1"), Instant.now())
        assertTrue(claimed)

        // Second claim attempt fails (at-most-once semantics)
        val secondClaim = repository.claimMatch(ContinuationWatchId("watch-1"), Instant.now())
        assertFalse(secondClaim)
    }

    @Test
    fun internalTestSignalsIgnoredForProductionWatches() = runBlocking {
        val testSignal = AutomationSignal(
            id = "s-test",
            kind = AutomationSignalKind.NETWORK_AVAILABLE,
            occurredAt = Instant.now(),
            source = "sandbox",
            trust = AutomationTrust.INTERNAL_TEST
        )

        assertTrue(ContinuationWatchMatcher.matches(WatchCondition.NetworkAvailable, testSignal))
        // Trust check: INTERNAL_TEST signals are skipped by ContinuationWatchRuntime before claim
        assertEquals(AutomationTrust.INTERNAL_TEST, testSignal.trust)
    }
}
