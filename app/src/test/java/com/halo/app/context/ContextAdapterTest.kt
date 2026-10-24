package com.halo.app.context

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.halo.app.data.*
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.capture.EphemeralMediaAssetStore
import com.halo.app.capture.VisualAttachmentStore
import com.halo.sdk.runtime.ink.*
import com.halo.app.study.*
import com.halo.runtime.cognition.context.*
import com.halo.runtime.cognition.turn.CognitiveTurnRequest
import com.halo.runtime.cognition.vision.DefaultInteractionMediaBinder
import com.halo.cognition.language.InteractionId
import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.multimodal.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class)
class ContextAdapterTest {
    @Test fun explicitCanvasContextUsesCanonicalBoardIdentityAndRejectsChangedRevision() = runBlocking {
        fixture { adapter, conversations, _, db, notes, policy, _ ->
            val canvases = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
            val board = canvases.create()
            canvases.addTextNode(board, 0f, 0f, "Literal board text")
            val working = WorkspaceContextStore(db, notes, Preferences())
            val version = requireNotNull(working.versionFor(board))
            adapter.selectWorkspace(board, version)
            val conversation = requireNotNull(conversations.activeConversationId())
            val refs = adapter.owner.current(conversation).map { it.reference }
            assertEquals(listOf(ContextReference.WorkspaceObject(board, version)), refs)
            val bound = adapter.resolve(turn(conversation, refs))
            assertEquals(board, bound.metadata["contextWorkspaceObjectId"])
            assertEquals(version, bound.metadata["contextWorkspaceVersion"])
            assertTrue(bound.explicitWorkspaceText!!.contains("Literal board text"))
            assertNull(bound.explicitImage)
            canvases.addTextNode(board, 50f, 0f, "Changed")
            assertTrue(runCatching { adapter.resolve(turn(conversation, refs)) }.isFailure)
            assertTrue(runCatching { adapter.selectWorkspace(board, version) }.isFailure)
            policy.setAccess(board, WorkspaceAiAccess.EXCLUDE_FROM_AI)
            assertTrue(runCatching { adapter.selectWorkspace(board) }.isFailure)
        }
    }
    @Test fun changedInkSelectionDuringContextTransferNeverPublishesOldCropOrRetainsLeases() = runBlocking {
        for (mutation in listOf("deselect", "replace", "cancel")) {
            val entered = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            var transferred: MediaAssetRef? = null
            var derived: MediaAssetRef? = null
            var guardedMedia: MediaAssetStore? = null
            fixture(mediaOverride = { actual ->
                object : MediaAssetStore by actual {
                    override suspend fun ingest(content: MediaAssetContent, provenance: MediaProvenance,
                        interactionId: String, sourceDeviceId: String?, workspaceObjectId: String?): MediaAssetRef =
                        actual.ingest(content, provenance, interactionId, sourceDeviceId, workspaceObjectId).also { derived = it }
                    override suspend fun transferOwnership(ref: MediaAssetRef, interactionId: String): MediaAssetRef {
                        val retained = actual.transferOwnership(ref, interactionId)
                        if (interactionId.startsWith("context:")) {
                            transferred = retained
                            entered.complete(Unit)
                            resume.await()
                        }
                        return retained
                    }
                }.also { guardedMedia = it }
            }) { adapter, conversations, actual, db, notes, _, _ ->
                val canvas = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
                val canvasId = canvas.create()
                val document = InkDocument(listOf(InkStroke(InkTool.STYLUS,
                    listOf(InkPoint(100, 500), InkPoint(700, 500)), "selected-stroke")))
                val encoded = InkCodec.encode(document)
                val sketchId = canvas.addSketchNode(canvasId, 0f, 0f, encoded)
                val revision = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("\n" + encoded).toByteArray()).joinToString("") { "%02x".format(it) }
                val selection = requireNotNull(InkSelections.create(document, canvasId, sketchId, revision,
                    InkSelectionBounds(400, 400, 600, 600), InkSelectionOrigin.BOX, 1_000))
                val bookmarkId = canvas.addInkSelection(canvasId, selection)
                val working = WorkspaceContextStore(db, notes, Preferences())
                working.selectInkSelection(canvasId, bookmarkId, selection)
                val untouched = actual.ingest(MediaAssetContent(byteArrayOf(2), "image/png"), MediaProvenance.IMPORTED, "unrelated")
                val attachments = VisualAttachmentStore(context, requireNotNull(guardedMedia), onSelected = {
                    adapter.selectImage(it.ref, it.source, it.contextText, it.commitSource)
                })
                var failure: Throwable? = null
                val selecting = launch(Dispatchers.Default) {
                    failure = runCatching {
                        attachments.attachGeneratedImage(byteArrayOf(1), "Canvas selection", canvasId,
                            InkSelectionBookmarkCodec.referenceText(selection),
                            validateSource = { working.requireCurrentInkSelection(canvasId, bookmarkId, selection) },
                            commitSource = { commit -> working.withCurrentInkSelection(canvasId, bookmarkId, selection, commit) })
                    }.exceptionOrNull()
                }
                try {
                    withTimeout(5_000) { entered.await() }
                    when (mutation) {
                        "deselect" -> working.remove(canvasId)
                        "replace" -> assertTrue(canvas.updateText(canvasId, bookmarkId,
                            InkSelectionBookmarkCodec.encode(selection.copy(
                                bounds = InkSelectionBounds(450, 400, 650, 600), updatedAtMs = 2_000))))
                        else -> selecting.cancel()
                    }
                    resume.complete(Unit)
                    withTimeout(5_000) { selecting.join() }
                    assertNotNull(mutation, failure)
                    assertNull(attachments.state.value)
                    assertTrue(adapter.owner.current(requireNotNull(conversations.activeConversationId()))
                        .none { it.category == ContextCategory.VISUAL })
                    assertNull(actual.metadata(requireNotNull(transferred)))
                    assertNull(actual.metadata(requireNotNull(derived)))
                    assertNotNull(actual.metadata(untouched))
                } finally {
                    resume.complete(Unit)
                    selecting.cancelAndJoin()
                }
            }
        }
    }

    @Test fun completedVisualTurnRetainsExactImageForExplicitFollowUp() = runBlocking {
        var publish: suspend (HaloEvent) -> Unit = {}
        fixture(eventAction = { publish = it::publish }) { adapter, conversations, store, _, _, _, _ ->
            val image = store.ingest(MediaAssetContent(byteArrayOf(1), "image/png"), MediaProvenance.IMPORTED, "X")
            adapter.selectImage(image, "Original photo")
            val c = conversations.activeConversationId()!!
            adapter.resolve(turn(c, adapter.owner.current(c).map { it.reference }, "first"))
            publish(InteractionTurnChanged(state = InteractionTurnState.Completed("first")))
            assertEquals(ContextReferenceState.SELECTED, adapter.owner.current(c).single().state)
            val next = adapter.resolve(turn(c, adapter.owner.current(c).map { it.reference }, "follow-up"))
            assertEquals(image.id, next.explicitImage?.assetId)
        }
    }
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private class Events : EventBus {
        private val flow = MutableSharedFlow<HaloEvent>(extraBufferCapacity=32)
        override suspend fun publish(event: HaloEvent) { flow.emit(event) }
        override fun events(): Flow<HaloEvent> = flow
        override fun <T: HaloEvent> observe(type: Class<T>): Flow<T> = flow.filter { type.isInstance(it) }.map { type.cast(it)!! }
    }
    private class Preferences : Storage {
        val values = mutableMapOf<String,String>()
        override fun getString(key:String, defaultValue:String?)=values[key]?:defaultValue
        override fun putString(key:String,value:String?) { if(value==null) values.remove(key) else values[key]=value }
        override fun getBoolean(key:String,defaultValue:Boolean)=values[key]?.toBooleanStrictOrNull()?:defaultValue
        override fun putBoolean(key:String,value:Boolean) {values[key]="$value"}
        override fun getInt(key:String,defaultValue:Int)=values[key]?.toIntOrNull()?:defaultValue
        override fun putInt(key:String,value:Int) {values[key]="$value"}
        override fun remove(key:String) {values.remove(key)}
        override fun clear() {values.clear()}
    }

    private suspend fun fixture(eventAction: (Events) -> Unit = {},
        mediaOverride: (EphemeralMediaAssetStore) -> MediaAssetStore = { it }, block:suspend (AndroidInteractionContextAdapter,RoomConversationStore,EphemeralMediaAssetStore,AppDatabase,RoomNotebookRepository,WorkspaceMediaAiPolicy,()->Unit)->Unit) {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val root=File(context.cacheDir,"context-test-${java.util.UUID.randomUUID()}")
        val events=Events();val prefs=Preferences()
        val store=EphemeralMediaAssetStore(root,scope=scope,eventBus=events)
        val notes=RoomNotebookRepository(db.notebookDao())
        val conversations=RoomConversationStore(db,scope);conversations.awaitReady()
        var now=System.currentTimeMillis()
        val policy=WorkspaceMediaAiPolicy(db,prefs)
        val sources=StudySourceResolver { id,_ -> val note=notes.getNote(id)
            if(note==null) ResolvedStudySource.Unsupported(id,"Missing","Unavailable") else
                ResolvedStudySource.Text(id,note.revision.toString(),note.title,listOf(StudyPassage(note.body,StudySourceLocator.NoteBlocks(1,1))),note.body.length,SourceCoverage.FULL) }
        val adapter=AndroidInteractionContextAdapter(InteractionContextContinuity { now },conversations,db,
            WorkspaceContextStore(db,notes,prefs),sources,mediaOverride(store),DefaultInteractionMediaBinder(store,AssistantContextRepository()),policy,scope,{ now },events)
        eventAction(events)
        try { block(adapter,conversations,store,db,notes,policy,{now += 120_001}) }
        finally { scope.cancel();db.close();root.deleteRecursively() }
    }
    private fun turn(conversation:String,refs:List<ContextReference>,id:String="new",modality:String="TEXT")=CognitiveTurnRequest(
        InteractionId(id),"request:$id",AiRequest(text="What is beside it?"),AiContextPolicy.CONSERVATIVE,
        ResponseDelivery.VISUAL_ONLY,null,mapOf("inputModality" to modality),conversationId=conversation,contextReferences=refs)
    @Test fun exactFollowUpKeepsXAfterYArrivesAndVoiceModalityIsUnchanged()=runBlocking {
        fixture { adapter,conversations,store,_,_,_,_->
            val x=store.ingest(MediaAssetContent(byteArrayOf(1,2),"image/png"),MediaProvenance.PHONE_CAMERA,"review:X")
            adapter.selectImage(x,"Circuit board")
            store.ingest(MediaAssetContent(byteArrayOf(3,4),"image/png"),MediaProvenance.PHONE_CAMERA,"review:Y")
            val conversation=conversations.activeConversationId()!!
            val refs=adapter.owner.current(conversation).map { it.reference }
            val first=adapter.resolve(turn(conversation,refs,"first"))
            store.releaseInteraction("first")
            val next=adapter.resolve(turn(conversation,refs,"second","VOICE"))
            assertEquals(x.id,first.explicitImage?.assetId);assertEquals(x.id,next.explicitImage?.assetId)
            assertEquals("second",next.explicitImage?.interactionId);assertEquals("VOICE",next.metadata["inputModality"])
            assertArrayEquals(byteArrayOf(1,2),store.open(next.explicitImage!!.mediaRef!!).bytes)
        }
    }
    @Test fun secondExplicitImageCannotReplaceSelectedImage()=runBlocking {
        fixture { adapter,conversations,store,_,_,_,_->
            val x=store.ingest(MediaAssetContent(byteArrayOf(1),"image/png"),MediaProvenance.IMPORTED,"X")
            val y=store.ingest(MediaAssetContent(byteArrayOf(2),"image/png"),MediaProvenance.IMPORTED,"Y")
            adapter.selectImage(x,"X")
            assertTrue(runCatching { adapter.selectImage(y,"Y") }.isFailure)
            val c=conversations.activeConversationId()!!
            val result=adapter.resolve(turn(c,adapter.owner.current(c).map { it.reference }))
            assertEquals(x.id,result.explicitImage?.assetId)
        }
    }

    @Test fun cancelledTurnRetainsExactSelectedImageForRetry()=runBlocking {
        var publish: suspend (HaloEvent) -> Unit = {}
        fixture(eventAction = { publish = it::publish }) { adapter,conversations,store,_,_,_,_->
            val x=store.ingest(MediaAssetContent(byteArrayOf(1),"image/png"),MediaProvenance.IMPORTED,"X")
            adapter.selectImage(x,"X")
            val c=conversations.activeConversationId()!!
            adapter.resolve(turn(c,adapter.owner.current(c).map { it.reference },"cancelled"))
            publish(InteractionTurnChanged(state=InteractionTurnState.Cancelled("cancelled","User stopped")))
            assertEquals(ContextReferenceState.SELECTED,adapter.owner.current(c).single().state)
            assertEquals(x.id,adapter.resolve(turn(c,adapter.owner.current(c).map { it.reference },"retry")).explicitImage?.assetId)
        }
    }

    @Test fun clearingPreviousConversationKeepsCurrentSelection()=runBlocking {
        fixture { adapter,conversations,store,_,_,_,_->
            val a=conversations.activeConversationId()!!
            conversations.newConversation()
            val b=conversations.activeConversationId()!!
            val x=store.ingest(MediaAssetContent(byteArrayOf(1),"image/png"),MediaProvenance.IMPORTED,"X")
            adapter.selectImage(x,"X");adapter.clearConversation(a)
            assertEquals(x.id,adapter.resolve(turn(b,adapter.owner.current(b).map { it.reference })).explicitImage?.assetId)
        }
    }

    @Test fun newConversationDoesNotInheritImage()=runBlocking {
        fixture { adapter,conversations,store,_,_,_,_->
            val x=store.ingest(MediaAssetContent(byteArrayOf(1),"image/png"),MediaProvenance.IMPORTED,"review")
            adapter.selectImage(x,"X");conversations.newConversation()
            val b=conversations.activeConversationId()!!
            assertTrue(adapter.owner.current(b).isEmpty());assertNull(adapter.resolve(turn(b,emptyList())).explicitImage)
        }
    }
    @Test fun expiryFailsInsteadOfCapturingOrReplacing()=runBlocking {
        fixture { adapter,conversations,store,_,_,_,expire->
            val x=store.ingest(MediaAssetContent(byteArrayOf(1),"image/png"),MediaProvenance.IMPORTED,"review")
            adapter.selectImage(x,"X");val c=conversations.activeConversationId()!!
            val refs=adapter.owner.current(c).map { it.reference };expire()
            assertTrue(runCatching { adapter.resolve(turn(c,refs)) }.isFailure)
            assertTrue(adapter.message.value!!.contains("no longer available"))
            assertEquals(ContextReferenceState.EXPIRED,adapter.owner.current(c).single().state)
        }
    }
    @Test fun noteSelectionDoesNotWriteWorkingSetAndChangedRevisionRequiresReselection()=runBlocking {
        fixture { adapter,conversations,_,db,notes,_,_->
            val note=notes.createNote("exact source")
            adapter.selectWorkspace(note.id);val c=conversations.activeConversationId()!!
            val refs=adapter.owner.current(c).map { it.reference }
            assertEquals("exact source",adapter.resolve(turn(c,refs)).explicitWorkspaceText)
            assertTrue(db.workspaceContextDao().selectedIds().isEmpty())
            notes.saveNote(note.id,"updated source")
            assertTrue(runCatching { adapter.resolve(turn(c,refs)) }.isFailure)
            adapter.selectWorkspace(note.id)
            assertEquals("updated source",adapter.resolve(turn(c,adapter.owner.current(c).map { it.reference })).explicitWorkspaceText)
        }
    }
    @Test fun objectPrivacyStillRejectsRemoteAndDeletionRejectsStaleReference()=runBlocking {
        fixture { adapter,conversations,_,_,notes,policy,_->
            val note=notes.createNote("private exact source");adapter.selectWorkspace(note.id)
            assertFalse(adapter.permitsWorkspace(note.id,false));assertTrue(adapter.permitsWorkspace(note.id,true))
            policy.setAccess(note.id,WorkspaceAiAccess.EXCLUDE_FROM_AI)
            assertFalse(adapter.permitsWorkspace(note.id,true))
            notes.setLifecycle(note.id,WorkspaceLifecycle.TRASHED)
            val c=conversations.activeConversationId()!!
            assertTrue(runCatching { adapter.resolve(turn(c,adapter.owner.current(c).map { it.reference })) }.isFailure)
        }
    }
    @Test fun reviewedResultDoubleSaveCreatesOneObjectAndKeepsExactConversation()=runBlocking {
        fixture { _,conversations,_,db,notes,_,_->
            val c=conversations.activeConversationId()!!
            val original=conversations.append("question","exact completed answer")
            val save=ContextResultSaveOwner(db,notes)
            val review=save.review(c,original.id)!!
            conversations.newConversation()
            val first=save.confirm(review);val second=save.confirm(review)
            assertEquals(first,second);assertEquals("exact completed answer",notes.getNote(first)?.body)
            assertEquals(1,notes.observeNotes().first().size)
            assertTrue(db.workspaceContextDao().provenanceMetadata(first)!!.contains("MODEL_GENERATED_UNVERIFIED"))
            assertNull(save.review(conversations.activeConversationId()!!,original.id))
        }
    }
    @Test fun replacingAnAttachmentInvalidatesSelectionWithoutChangingNoteTextRevision()=runBlocking {
        fixture { adapter,conversations,_,db,notes,_,_->
            val note=notes.createNote("unchanged source body")
            val assets=db.workspaceAssetDao()
            assets.insertAsset(WorkspaceAssetEntity("X",note.id,"IMAGE","image/png","MANAGED_FILE","unused-X",true,"X",1,1,1,null,1))
            assets.insertAttachment(NoteAttachmentEntity("attachment-X",note.id,"X",0,null,1))
            adapter.selectWorkspace(note.id)
            val c=conversations.activeConversationId()!!
            val refs=adapter.owner.current(c).map { it.reference }
            assets.deleteAttachment(note.id,"attachment-X")
            assets.insertAsset(WorkspaceAssetEntity("Y",note.id,"IMAGE","image/png","MANAGED_FILE","unused-Y",true,"Y",1,1,1,null,2))
            assets.insertAttachment(NoteAttachmentEntity("attachment-Y",note.id,"Y",0,null,2))
            assertEquals(note.revision,notes.getNote(note.id)!!.revision)
            assertTrue(runCatching { adapter.resolve(turn(c,refs)) }.isFailure)
            assertTrue(adapter.message.value!!.contains("version changed"))
        }
    }
}
