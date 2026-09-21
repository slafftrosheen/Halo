package com.halo.app.data.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceSearchEngineTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var searchEngine: AppSearchWorkspaceSearchEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        searchEngine = AppSearchWorkspaceSearchEngine(context, db.notebookDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun indexAndSearchWorkspaceObject() = runTest {
        val id = WorkspaceObjectId("note-123")
        searchEngine.indexObject(
            objectId = id,
            type = WorkspaceObjectType.NOTE,
            title = "Halo UI Redesign",
            content = "Detailed specifications for paper-like soft pastel interface",
            updatedAt = System.currentTimeMillis()
        )

        val result = searchEngine.search(WorkspaceSearchQuery("Halo"))
        assertNotNull(result)
        assertEquals("Halo", result.query)
    }
}
