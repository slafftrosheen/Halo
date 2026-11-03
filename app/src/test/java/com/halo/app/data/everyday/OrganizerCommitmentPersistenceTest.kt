package com.halo.app.data.everyday

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.organizer.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrganizerCommitmentPersistenceTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = RoomOrganizerRepository(db, clock)
    private val useCases = OrganizerUseCases(repository, clock, { ZoneOffset.UTC }, { 5000 }, { "boot" })
    @After fun close() = db.close()

    @Test fun legacyRecordsDefaultToHumanWithoutInventingExecution() {
        val record = OrganizerRecord("old", OrganizerKind.TASK, "Legacy", createdAt = now)
        val json = OrganizerCodec.encode(record)
        assertFalse(json.contains("commitment"))
        val restored = OrganizerCodec.decode(json)
        assertEquals(CommitmentOwner.ME, restored.commitment.owner)
        assertNull(restored.commitment.execution); assertNull(restored.commitment.result)
    }
    @Test fun responsibilityPlanResultAndLinksSurviveRepositoryRecreation() = runBlocking {
        val commitment = OrganizerCommitment(CommitmentOwner.SHARED, intent = "Prepare options", state = HaloWorkState.NEEDS_USER,
            actions = listOf(OrganizerPlannedAction("summarize", "{\"period\":\"ON_DEMAND\"}")),
            execution = OrganizerExecutionBinding("plan", "routine", "version", "run", "SCHEDULED", 0, listOf("evidence")),
            result = OrganizerWorkResult("Confirmed preparation", now, listOf("evidence")), nextHumanAction = "Choose an option")
        val saved = useCases.create(OrganizerKind.TASK, "Choose", links = listOf(OrganizerLink("studio-project")), commitment = commitment)
        assertEquals(saved, RoomOrganizerRepository(db, clock).get(saved.id))
        assertEquals(commitment.requiredCapabilities, setOf("summarize"))
    }
    @Test fun humanCompletionCannotForgeADelegatedResult() = runBlocking {
        val saved = useCases.create(OrganizerKind.TASK, "Delegated", commitment = OrganizerCommitment(CommitmentOwner.HALO, state = HaloWorkState.DRAFT))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { useCases.completeTask(saved.id, saved.version) } }
        assertNull(repository.get(saved.id)!!.commitment.result)
        assertThrows(IllegalArgumentException::class.java) { saved.copy(commitment = saved.commitment.copy(state = HaloWorkState.COMPLETED)).validate() }
    }
    @Test fun ownershipTransferIsExactVersionAndInvalidatesTheOldPlan() = runBlocking {
        val saved = useCases.create(OrganizerKind.TASK, "Human")
        val delegated = useCases.transfer(saved.id, saved.version, CommitmentOwner.HALO)
        assertEquals(HaloWorkState.DRAFT, delegated.commitment.state)
        assertThrows(OrganizerConflict::class.java) { runBlocking { useCases.transfer(saved.id, saved.version, CommitmentOwner.SHARED) } }
        val taken = useCases.transfer(delegated.id, delegated.version, CommitmentOwner.ME)
        assertEquals(HaloWorkState.READY, taken.commitment.state); assertNull(taken.commitment.execution)
    }
    @Test fun reviewedPlanContextCannotBeSilentlyEditedAndRevocationPersistsFirst() = runBlocking {
        val saved = useCases.create(OrganizerKind.TASK, "Bound", commitment = OrganizerCommitment(CommitmentOwner.HALO,
            execution = OrganizerExecutionBinding("plan", "routine")))
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.save(saved.copy(title = "Changed instruction"), saved.version) } }
        val stopped = repository.save(saved.copy(commitment = saved.commitment.copy(execution = null, state = HaloWorkState.NEEDS_USER,
            nextHumanAction = "Inspect interrupted work")), saved.version)
        assertFalse(OrganizerResponsibility.mayExecute(stopped, "plan"))
        assertNull(RoomOrganizerRepository(db, clock).get(stopped.id)!!.commitment.execution)
    }
    @Test fun inboxCapturePersistsUnstructuredIntentWithoutClaimingASchedule() = runBlocking {
        val saved = useCases.capture("Friday buy material", "Friday buy material for the project")
        val restored = repository.get(saved.id)!!
        assertEquals(CommitmentPurpose.INBOX, restored.commitment.purpose); assertEquals(HaloWorkState.DRAFT, restored.commitment.state)
        assertNull(restored.time); assertNull(restored.commitment.execution)
    }
    @Test fun stopwatchMonotonicClockWinsAndPausedStateSurvivesStorage() = runBlocking {
        val saved = useCases.create(OrganizerKind.TASK, "Stopwatch", commitment = OrganizerCommitment(purpose = CommitmentPurpose.STOPWATCH, stopwatch = StopwatchState()))
        val running = useCases.stopwatch(saved.id, saved.version, "START")
        val stopwatch = running.commitment.stopwatch!!
        assertEquals(2500L, stopwatch.elapsed(now.minusSeconds(3600), 7500, "boot"))
        assertEquals(0L, stopwatch.elapsed(now.minusSeconds(3600), 10, "new-boot"))
        val paused = stopwatch.pause(now.plusSeconds(5), 10000, "boot").lapFailureIsExplicit()
        val stored = repository.save(running.copy(commitment = running.commitment.copy(stopwatch = paused)), running.version)
        assertEquals(5000L, RoomOrganizerRepository(db, clock).get(stored.id)!!.commitment.stopwatch!!.elapsed(now.plusSeconds(100), 99999, "other"))
    }
    private fun StopwatchState.lapFailureIsExplicit(): StopwatchState {
        assertThrows(IllegalArgumentException::class.java) { lap(now, 10000, "boot") }; return this
    }
    @Test fun portableStopwatchDoesNotOverflowOrMoveBeforeItsLastRecordedLap() {
        val running = StopwatchState().start(now, 100, "boot").lap(now, 5100, "boot")
        assertEquals(5000L, running.elapsed(now.minusSeconds(3600), 0, "new-boot"))
        assertEquals(Long.MAX_VALUE, running.elapsed(Instant.MAX, 0, "new-boot"))
        assertEquals(2, running.lap(now.minusSeconds(3600), 0, "new-boot").laps.size)
    }
    @Test fun todaySeparatesOwnersAndDoesNotInventCancelledRecurrenceOccurrences() {
        fun task(id: String, owner: CommitmentOwner) = OrganizerRecord(id, OrganizerKind.TASK, id,
            time = OrganizerTime(LocalDateTime.ofInstant(now, ZoneOffset.UTC), zoneId = "UTC"), commitment = OrganizerCommitment(owner), createdAt = now)
        val human = task("human", CommitmentOwner.ME)
        val halo = task("halo", CommitmentOwner.HALO)
        val shared = task("shared", CommitmentOwner.SHARED)
        val future = human.copy(id = "future", time = human.time!!.copy(start = human.time.start.plusDays(10)))
        val cancelledOccurrence = human.copy(id = "recurring", repeat = RepeatRule(RepeatFrequency.DAILY))
        val rows = OrganizerSections.records(OrganizerSection.TODAY, listOf(human, halo, shared, future, cancelledOccurrence), now, ZoneOffset.UTC, emptySet())
        val grouped = OrganizerSections.responsibility(rows)
        assertEquals(listOf("human"), grouped[CommitmentOwner.ME]!!.map { it.id })
        assertEquals(listOf("halo"), grouped[CommitmentOwner.HALO]!!.map { it.id })
        assertEquals(listOf("shared"), grouped[CommitmentOwner.SHARED]!!.map { it.id })
    }
}
