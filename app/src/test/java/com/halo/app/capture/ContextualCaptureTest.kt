package com.halo.app.capture

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], manifest=Config.NONE, application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContextualCaptureTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
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
    private fun image(): MediaAssetContent {
        val output=java.io.ByteArrayOutputStream()
        Bitmap.createBitmap(16,8,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,output)
        return MediaAssetContent(output.toByteArray(),"image/png")
    }
    private suspend fun fixture(block:suspend (EphemeralMediaAssetStore,CoroutineScope,AppDatabase,Preferences)->Unit) {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val root=File(context.cacheDir,"capture-test-${java.util.UUID.randomUUID()}")
        try { block(EphemeralMediaAssetStore(root,scope=scope,eventBus=Events()),scope,db,Preferences()) }
        finally {scope.cancel();db.close();root.deleteRecursively()}
    }
    @Test fun repeatedAttachmentIsIdempotentAndRetainsExactImage()=runBlocking {
        fixture { store,_,_,_->
            val x=store.ingest(image(),MediaProvenance.PHONE_CAMERA,"review:X")
            val y=store.ingest(image(),MediaProvenance.PHONE_CAMERA,"review:Y")
            var selections=0
            val attachment=VisualAttachmentStore(context,store,onSelected={ selections++ })
            val first=attachment.attachReference(x,"X")
            assertEquals(first,attachment.attachReference(x,"X"))
            assertEquals(1,selections)
            assertTrue(runCatching { attachment.attachReference(y,"Y") }.isFailure)
            assertEquals(x.id,attachment.state.value?.ref?.id)
            attachment.remove()
            assertNotNull(store.metadata(x))
            assertNull(store.metadata(first.ref))
        }
    }

    @Test fun failedSelectionReleasesOnlyNewAttachmentLease()=runBlocking {
        fixture { store,_,_,_->
            val x=store.ingest(image(),MediaProvenance.PHONE_CAMERA,"review:X")
            var failedLease: MediaAssetRef?=null
            val attachment=VisualAttachmentStore(context,store,onSelected={ failedLease=it.ref;error("Selection rejected") })
            assertTrue(runCatching { attachment.attachReference(x,"X") }.isFailure)
            assertNull(attachment.state.value)
            assertNull(store.metadata(failedLease!!))
            assertNotNull(store.metadata(x))
        }
    }

    @Test fun failedTurnReleaseKeepsReviewBytesWithoutCopy()=runBlocking {
        fixture { store,_,_,_->
            val content=image(); val review=store.ingest(content,MediaProvenance.PHONE_CAMERA,"review")
            val turn=store.transferOwnership(review,"turn")
            assertEquals(review.id,turn.id)
            store.releaseInteraction("turn")
            assertArrayEquals(content.bytes,store.open(review).bytes)
            assertNull(store.metadata(turn))
            store.releaseInteraction("review");assertNull(store.metadata(review))
        }
    }
    @Test fun releasingReviewDoesNotDeleteActiveInteraction()=runBlocking {
        fixture {store,_,_,_->
            val ref=store.ingest(image(),MediaProvenance.PHONE_CAMERA,"review")
            val turn=store.transferOwnership(ref,"turn")
            store.releaseInteraction("review")
            assertNull(store.metadata(ref));assertNotNull(store.metadata(turn))
        }
    }
    @Test fun forgedIdentityAndOwnerAreRejected()=runBlocking {
        fixture {store,_,_,_->
            val ref=store.ingest(image(),MediaProvenance.PHONE_CAMERA,"review")
            assertNull(store.metadata(ref.copy(interactionId="other")))
            assertNull(store.metadata(ref.copy(sha256="fake")))
        }
    }
    @Test fun duplicateEventProducesOneReviewAndNoWorkspaceItem()=runBlocking {
        fixture {store,scope,db,prefs->
            val events=Events();val notes=RoomNotebookRepository(db.notebookDao())
            val assets=NotebookAssetRepository(context,db)
            val controller=ContextualCaptureController(context,store,LocalCaptureStore(context,db,notes,events),VisualAttachmentStore(context,store),assets,scope,WorkspaceMediaAiPolicy(db,prefs))
            controller.receive(image(),CaptureSource.PHONE_CAMERA,"Phone", "event")
            val first=controller.state.value!!
            controller.receive(image(),CaptureSource.PHONE_CAMERA,"Phone", "event")
            assertEquals(first,controller.state.value)
            assertTrue(notes.observeNotes().first().isEmpty())
            controller.discard().join();assertNull(controller.state.value);assertNull(store.metadata(first.mediaRef))
        }
    }
    @Test fun invalidReceiptCannotBecomeReviewable()=runBlocking {
        fixture {store,scope,db,prefs->
            val controller=ContextualCaptureController(context,store,LocalCaptureStore(context,db,RoomNotebookRepository(db.notebookDao()),Events()),VisualAttachmentStore(context,store),NotebookAssetRepository(context,db),scope,WorkspaceMediaAiPolicy(db,prefs))
            assertTrue(runCatching {controller.receive(MediaAssetContent(byteArrayOf(1,2,3),"image/png"),CaptureSource.PHONE_CAMERA,"Phone")}.isFailure)
            assertNull(controller.state.value)
        }
    }
    @Test fun temporaryCloudNeedsExplicitGrantAndSavedPolicyWins()=runBlocking {
        fixture {store,_,db,prefs->
            val ref=store.ingest(image(),MediaProvenance.PHONE_CAMERA,"review")
            val policy=WorkspaceMediaAiPolicy(db,prefs)
            assertTrue(policy.permits(ref,true));assertFalse(policy.permits(ref,false))
            policy.allowTemporaryCloud(ref.id,true);assertTrue(policy.permits(ref,false))
            val note=RoomNotebookRepository(db.notebookDao()).createNote()
            policy.associateSaved(ref.id,note.id)
            assertFalse(policy.permits(ref,false))
            policy.setAccess(note.id,WorkspaceAiAccess.EXCLUDE_FROM_AI)
            assertFalse(policy.permits(ref,true))
            policy.allowTemporaryCloud(ref.id,true);assertFalse(policy.permits(ref,false))
            policy.releaseTemporary(ref.id)
            assertFalse(policy.permits(ref,true))
            assertFalse(policy.permits(ref,false))
        }
    }
    @Test fun textOnlyHeuristicAndDeniedCloudAreRejected() {
        val vision=AiModel("concrete","Model","provider",setOf(AiCapability.TEXT,AiCapability.IMAGE),4096,false,false,
            capabilityEvidence=mapOf(AiCapability.IMAGE to CapabilityEvidence.AUTHORITATIVE_METADATA))
        val policy=AiContextPolicy(allowCloudImages=true)
        assertTrue(CaptureWorkflowPolicy.permitsModel(vision,policy,true))
        assertFalse(CaptureWorkflowPolicy.permitsModel(vision.copy(inputCapabilities=setOf(AiCapability.TEXT)),policy,true))
        assertFalse(CaptureWorkflowPolicy.permitsModel(vision.copy(capabilityEvidence=mapOf(AiCapability.IMAGE to CapabilityEvidence.HEURISTIC)),policy,true))
        assertFalse(CaptureWorkflowPolicy.permitsModel(vision,policy.copy(allowCloudImages=false),true))
        assertFalse(CaptureWorkflowPolicy.permitsModel(vision,policy,false))
    }
    @Test fun notebookCaptureIsAnExactVisualStudySourceAfterReopening()=runBlocking {
        fixture {store,_,db,_->
            val notes=RoomNotebookRepository(db.notebookDao());val note=notes.createNote("",title="Homework photo")
            val content=image();val path=File(context.cacheDir,"saved-source.png").apply {writeBytes(content.bytes)}
            try {
                db.workspaceAssetDao().insertAsset(WorkspaceAssetEntity("asset",note.id,"IMAGE","image/png","MANAGED_FILE",path.absolutePath,true,"Homework",content.bytes.size.toLong(),16,8,null,1))
                db.workspaceAssetDao().insertAttachment(NoteAttachmentEntity("attachment",note.id,"asset",0,null,1))
                val files=RoomWorkspaceFileRepository(context,db,context.cacheDir)
                val assets=NotebookAssetRepository(context,db)
                val pdf=object: com.halo.app.study.StudyPdfPages {
                    override suspend fun pageCount(file:WorkspaceFile):Int=error("Not PDF")
                    override suspend fun render(file:WorkspaceFile,page:Int):PreparedImageInput=error("Not PDF")
                }
                val resolver=com.halo.app.study.WorkspaceStudySourceResolver(context,NotebookDocumentRepository(db.notebookDocumentDao(),db),RoomWorkspaceRepository(db.notebookDao()),files,pdf,assets)
                val source=resolver.resolve(note.id,"") as com.halo.app.study.ResolvedStudySource.Visual
                assertEquals("attachment:${note.id}:asset",source.asset.id)
                val durable=WorkspaceMediaAssetResolver(context,files,store,NotebookAssetRepository(context,db),RoomWorkspaceRepository(db.notebookDao()))
                assertArrayEquals(content.bytes,durable.open(source.asset).bytes)
                val bound=WorkspaceInteractionMediaStore(store,durable).transferOwnership(source.asset,"new-question")
                assertEquals(note.id,bound.workspaceObjectId)
                assertArrayEquals(content.bytes,durable.open(bound).bytes)
                val target=notes.createNote("Keep this body",title="Existing note")
                val original=assets.observe(note.id).first().single()
                assets.attachExistingImage(target.id,original);assets.attachExistingImage(target.id,original)
                assertEquals(1,assets.observe(target.id).first().size)
                assertEquals("Keep this body",notes.getNote(target.id)?.body)
                assertEquals(original.pathOrUri,assets.observe(target.id).first().single().pathOrUri)
            } finally {path.delete()}
        }
    }

    @Test fun duplicateStillRequestDoesNotRetakeAndKeepsActualImageMetadata()=runBlocking {
        fixture {store,scope,db,prefs->
            val notes=RoomNotebookRepository(db.notebookDao())
            val controller=ContextualCaptureController(context,store,LocalCaptureStore(context,db,notes,Events()),VisualAttachmentStore(context,store),NotebookAssetRepository(context,db),scope,WorkspaceMediaAiPolicy(db,prefs))
            var calls=0;val content=image()
            val camera=object:com.halo.sdk.driver.StillImageSource {
                override suspend fun captureStill(request:com.halo.sdk.driver.StillCaptureRequest):Result<com.halo.sdk.driver.CapturedAsset> {
                    calls++
                    return Result.success(com.halo.sdk.driver.CapturedAsset("asset","device","image/png",1,content.bytes,width=999,height=999))
                }
            }
            val request=com.halo.sdk.driver.StillCaptureRequest(com.halo.sdk.driver.VisualCaptureId("request"))
            controller.captureStill(camera,request,"Future camera",CaptureQuality.AI_THUMBNAIL)
            controller.captureStill(camera,request,"Future camera",CaptureQuality.AI_THUMBNAIL)
            assertEquals(1,calls);assertEquals(16,controller.state.value?.width);assertEquals(8,controller.state.value?.height)
            assertEquals("asset",controller.state.value?.sourceAssetId)
            assertEquals(CaptureQuality.AI_THUMBNAIL,controller.state.value?.quality)
            assertTrue(notes.observeNotes().first().isEmpty())
        }
    }
    @Test fun successfulAcknowledgementWithoutMediaIsNotReceiptOrRetried()=runBlocking {
        fixture {store,scope,db,prefs->
            val controller=ContextualCaptureController(context,store,LocalCaptureStore(context,db,RoomNotebookRepository(db.notebookDao()),Events()),VisualAttachmentStore(context,store),NotebookAssetRepository(context,db),scope,WorkspaceMediaAiPolicy(db,prefs))
            var calls=0
            val camera=object:com.halo.sdk.driver.StillImageSource {
                override suspend fun captureStill(request:com.halo.sdk.driver.StillCaptureRequest):Result<com.halo.sdk.driver.CapturedAsset> {
                    calls++;return Result.success(com.halo.sdk.driver.CapturedAsset("ack","device","image/jpeg",1))
                }
            }
            val request=com.halo.sdk.driver.StillCaptureRequest(com.halo.sdk.driver.VisualCaptureId("possible-dispatch"))
            repeat(2) {assertTrue(runCatching {controller.captureStill(camera,request,"Camera")}.isFailure)}
            assertEquals(1,calls);assertNull(controller.state.value)
        }
    }

    @Test fun failedFilePreparationLeavesTheOnlyImageReviewable()=runBlocking {
        fixture {store,scope,db,prefs->
            val notes=RoomNotebookRepository(db.notebookDao())
            val controller=ContextualCaptureController(context,store,LocalCaptureStore(context,db,notes,Events()),VisualAttachmentStore(context,store),NotebookAssetRepository(context,db),scope,WorkspaceMediaAiPolicy(db,prefs))
            val content=image()
            controller.receive(content,CaptureSource.PHONE_CAMERA,"Phone")
            val ref=controller.state.value!!.mediaRef
            // This fixture has no FileProvider: durable file preparation must fail honestly.
            assertTrue(runCatching {controller.save()}.isFailure)
            assertEquals(CaptureStatus.FAILED,controller.state.value?.status)
            assertTrue(controller.state.value?.error.orEmpty().contains("image is still available"))
            assertArrayEquals(content.bytes,store.open(ref).bytes)
            assertTrue(notes.observeNotes().first().isEmpty())
        }
    }

    @Test fun unsavedRestartNoticeDoesNotFabricateADurableSource()=runBlocking {
        fixture {store,scope,db,prefs->
            val notes=RoomNotebookRepository(db.notebookDao())
            fun controller()=ContextualCaptureController(context,store,LocalCaptureStore(context,db,notes,Events()),VisualAttachmentStore(context,store),NotebookAssetRepository(context,db),scope,WorkspaceMediaAiPolicy(db,prefs),prefs)
            val first=controller()
            first.receive(image(),CaptureSource.PHONE_CAMERA,"Phone")
            store.releaseInteraction(first.state.value!!.mediaRef.interactionId!!)
            val restored=controller()
            assertNull(restored.state.value)
            assertTrue(restored.notice.value!!.contains("temporary image is no longer available"))
            assertTrue(notes.observeNotes().first().isEmpty())
            restored.discard().join()
            assertNull(controller().notice.value)
        }
    }

}
