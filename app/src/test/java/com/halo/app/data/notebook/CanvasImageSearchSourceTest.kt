package com.halo.app.data.notebook

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.workspace.WorkspaceFileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasImageSearchSourceTest {
    private lateinit var database: AppDatabase
    private lateinit var canvas: NotebookCanvasRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        canvas = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
    }

    @After fun tearDown() = database.close()

    @Test fun listsOnlyActiveFileBackedPngAndJpegImageNodes() = runBlocking {
        val boardId = canvas.create("Board")
        addMedia("jpeg", "diagram.jpg", "image/jpeg")
        addMedia("png", "chart.png", "IMAGE/PNG")
        addMedia("gif", "animation.gif", "image/gif")
        val jpegNode = canvas.addReference(boardId, "jpeg", 0f, 0f)
        val pngNode = canvas.addReference(boardId, "png", 0f, 0f)
        canvas.addReference(boardId, "gif", 0f, 0f)

        assertEquals(setOf(
            CanvasImageSearchSource(boardId, pngNode, "png", "Board", "chart.png", "IMAGE/PNG"),
            CanvasImageSearchSource(boardId, jpegNode, "jpeg", "Board", "diagram.jpg", "image/jpeg"),
        ), database.notebookCanvasDao().observeImageSearchSources().first().toSet())
    }

    private suspend fun addMedia(id: String, name: String, mimeType: String) {
        database.notebookDao().insertObject(WorkspaceObjectEntity(
            id = id, type = WorkspaceObjectType.MEDIA.name, title = name,
            createdAt = 1L, updatedAt = 1L, source = WorkspaceObjectSource.IMPORT.name,
            lifecycle = WorkspaceObjectLifecycle.ACTIVE.name, inInbox = false, indexingState = "READY",
            contentRef = "file:$id"))
        database.workspaceFileDao().insertFile(WorkspaceFileEntity(
            objectId = id, uri = "content://test/$name", displayName = name, mimeType = mimeType,
            sizeBytes = 100L, modifiedAt = 1L, ownedCopyPath = null, permissionPersisted = true))
    }
}
