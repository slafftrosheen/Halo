package com.halo.app.data.agent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentRunRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: RoomAgentRunRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAgentRunRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun startRunAddStepsAndComplete() = runTest {
        val run = repository.startRun("Find my HeyCyan note")
        assertNotNull(run.id)
        assertEquals("Find my HeyCyan note", run.request)

        repository.addStep(
            AgentStep.Reasoning(
                id = "step-1",
                runId = run.id,
                timestamp = System.currentTimeMillis(),
                summary = "Searching Notebook for HeyCyan"
            )
        )

        repository.completeRun(run.id, AgentRunState.COMPLETED)

        val runs = repository.observeRuns().first()
        assertEquals(1, runs.size)
        assertEquals(AgentRunState.COMPLETED, runs.first().state)
    }
}
