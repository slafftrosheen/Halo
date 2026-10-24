package com.halo.app.data.notebook

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RemoteInkCheckpointTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: NotebookCanvasRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val file = "remote-ink-checkpoint.db"
    private val ink = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(20, 30, pressure = 650)), "stroke")))
    private val original = InkDocument(listOf(InkStroke(InkTool.FINGER, listOf(InkPoint(1, 1)), "original")))
    @Before fun setup() { context.deleteDatabase(file); open() }
    private fun open() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, file).allowMainThreadQueries().build()
        repository = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao(),
            remoteInkActivation = InkActivationPolicy { true })
    }
    @After fun close() { db.close(); context.deleteDatabase(file) }
    private suspend fun request(): InkCheckpointRequest {
        val board = repository.create("Ink board")
        val node = repository.addSketchNode(board, 0f, 0f, InkCodec.encode(original))
        return InkCheckpointRequest(InkSession("session", "tablet", "phone", "phone", "interaction",
            board, node, 1, 1000), 1, repository.remoteInkRevision(board, node)!!, "a".repeat(64), ink)
    }

    @Test fun saveAndIdenticalRetrySurviveDatabaseReopenWithoutDuplicatingStrokes() = runTest {
        val request = request()
        val saved = repository.commitRemoteInk(request)
        assertEquals(InkReceiveStatus.SAVED, saved.status)
        db.close(); open()
        val duplicate = repository.commitRemoteInk(request)
        assertEquals(InkReceiveStatus.DUPLICATE, duplicate.status)
        assertEquals(saved.revision, duplicate.revision)
        val node = db.notebookCanvasDao().getNode(request.session.boardId, request.session.documentId)!!
        assertEquals(ink, InkCodec.decode(node.text))
        assertEquals(saved.revision, repository.remoteInkRevision(request.session.boardId, request.session.documentId))
    }

    @Test fun concurrentLocalEditAndChangedRetryFailWithoutOverwriting() = runTest {
        val request = request()
        val board = request.session.boardId; val node = request.session.documentId
        repository.updateText(board, node, InkCodec.encode(ink))
        assertEquals(InkReceiveStatus.CONFLICT, repository.commitRemoteInk(request).status)
        val next = request.copy(expectedRevision = repository.remoteInkRevision(board, node)!!)
        assertEquals(InkReceiveStatus.SAVED, repository.commitRemoteInk(next).status)
        assertEquals(InkReceiveStatus.CONFLICT,
            repository.commitRemoteInk(next.copy(payloadSha256 = "b".repeat(64), document = InkDocument())).status)
        assertEquals(ink, InkCodec.decode(db.notebookCanvasDao().getNode(board, node)!!.text))
    }

    @Test fun receiptFailureRollsBackBothInkAndBoardRevision() = runTest {
        val request = request()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_ink_receipt BEFORE INSERT ON object_provenance BEGIN SELECT RAISE(ABORT, 'Receipt rejected'); END")
        assertTrue(runCatching { repository.commitRemoteInk(request) }.isFailure)
        assertEquals(request.expectedRevision, repository.remoteInkRevision(request.session.boardId, request.session.documentId))
        assertEquals(original, InkCodec.decode(db.notebookCanvasDao().getNode(request.session.boardId, request.session.documentId)!!.text))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_ink_receipt")
        assertEquals(InkReceiveStatus.SAVED, repository.commitRemoteInk(request).status)
    }

    @Test fun productionGateAndDeletedSketchRefuseDurableMutation() = runTest {
        val request = request()
        val closed = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        assertEquals(InkReceiveStatus.DISABLED, closed.commitRemoteInk(request).status)
        repository.deleteNode(request.session.boardId, request.session.documentId)
        assertEquals(InkReceiveStatus.CONFLICT, repository.commitRemoteInk(request).status)
    }

    @Test fun localRecoveryCreatedAfterOfferBlocksRemoteCommitAndKeepsItsPayload() = runTest {
        val request = request()
        val draft = NoteDraftEntity(request.session.boardId, "local recovery", 1000)
        db.notebookDao().upsertDraft(draft)
        assertEquals(InkReceiveStatus.CONFLICT, repository.commitRemoteInk(request).status)
        assertEquals(draft, db.notebookDao().getDraft(request.session.boardId))
        assertEquals(original, InkCodec.decode(db.notebookCanvasDao().getNode(request.session.boardId, request.session.documentId)!!.text))
    }
}
