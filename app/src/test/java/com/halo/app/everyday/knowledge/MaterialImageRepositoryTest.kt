package com.halo.app.everyday.knowledge

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.RoomCollectorRepository
import com.halo.app.data.knowledge.ReferenceLibraryRepository
import com.halo.app.everyday.collectors.*
import java.io.*
import java.time.Instant
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class MaterialImageRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val sources = RoomCollectorRepository(db)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val libraries = ReferenceLibraryRepository(db, sources, { error("Not a file import") }, { Long.MAX_VALUE }, { 0 }, { {} }, { false }, scope)
    private var calls = 0; private var power = false; private var child = false
    private var failed = false
    private var onImage: suspend () -> Unit = {}
    private val transfer = object : ReferenceTransfer {
        override suspend fun fetch(url: HttpUrl, maxBytes: Long, sink: OutputStream, checkIndexing: Boolean, imageOnly: Boolean): String? {
            calls++
            if (url.encodedPath == "/robots.txt") { sink.write("User-agent: *\nAllow: /".toByteArray()); return null }
            onImage()
            if (failed) error("Image unavailable")
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink) } finally { bitmap.recycle() }
            return null
        }
    }
    private val owner = MaterialImageRepository(context, db, sources, libraries, transfer, { {} }, { child }, { power })
    private suspend fun target(): MaterialImageTarget {
        val source = sources.save(CollectorSource("image-fixture", "News", "Publisher", CollectorKind.FEED, SourceCategory.WORLD, "en", "https://example.org/feed", selected = true), null)
        val now = Instant.now()
        val reservation = (sources.reserve(source.id, source.version, now, true) as CollectorAdmission.Accepted).reservation
        val body = CollectorPayload.Articles(listOf(ArticleFact("one", "News", "Original retained text", "https://example.org/story", "Publisher", null, "en", images = listOf(SourceImage("https://example.org/picture.png", "Owned fixture diagram")))))
        assertTrue(sources.finish(reservation, CollectorSnapshot(source.id, source.version, body, "rev-one", now, now, now.plusSeconds(3600)), CollectorAttempt(source.id, now, CollectorAttemptState.CURRENT)))
        return MaterialImageTarget(source.id, source.version, "rev-one", "one")
    }
    @After fun close() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin(); db.close()
        // Isolated Robolectric application files, never the physical phone.
        File(context.filesDir, "material-images").deleteRecursively()
    }
    @Test fun imageToolsHaveCompleteRegisteredOperabilityAndReplayGuards() {
        val tools = com.halo.app.di.tools.MaterialImageTools(owner)
        val descriptors = tools.definitions().map { com.halo.app.di.tools.HaloToolDescriptors.assistant(it)!! }
        val registry = com.halo.runtime.cognition.tools.ToolRegistry(listOf(tools), descriptors)
        assertTrue(registry.operabilityReport(listOf(com.halo.runtime.cognition.tools.ToolFeatureOperability("material-images", com.halo.app.di.tools.MaterialImageContracts.names))).isEmpty())
        val fetch = descriptors.single { it.name == "halo_material_image_fetch" }
        assertEquals(com.halo.runtime.cognition.tools.ConfirmationPolicy.ALWAYS, fetch.security.confirmation)
        assertEquals(com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY, fetch.security.replaySafety)
    }
    @Test fun localReaderMakesZeroRequestsAndPowerSavesOnlyStaticOwnedFiles() = runBlocking {
        val target = target()
        assertNull(owner.local(target).single().file); assertEquals(0, calls)
        assertTrue(runCatching { owner.fetch(target) }.isFailure); assertEquals(0, calls)
        power = true
        val receipt = owner.fetch(target); assertEquals(1, receipt.saved); assertEquals(0, receipt.failed)
        power = false
        val file = owner.local(target).single().file!!; assertTrue(file.isFile)
        assertEquals(2, calls)
        assertEquals("Original retained text", (sources.state(target.sourceId)!!.snapshot!!.payload as CollectorPayload.Articles).items.single().text)
    }
    @Test fun sourceRevocationDuringTransferCannotPublishOrExposeTheImage() = runBlocking {
        val target = target(); power = true
        onImage = { val source = sources.source(target.sourceId)!!; sources.save(source.copy(selected = false), source.version) }
        assertTrue(runCatching { owner.fetch(target) }.isFailure)
        assertTrue(db.referenceDao().images().isEmpty())
        assertTrue(runCatching { owner.local(target) }.isFailure)
    }
    @Test fun optionalPictureFailureKeepsArticleAndReportsPartialOutcome() = runBlocking {
        val target = target(); power = true; failed = true
        val receipt = owner.fetch(target)
        assertEquals(0, receipt.saved); assertEquals(1, receipt.failed)
        assertNotNull(sources.state(target.sourceId)?.snapshot)
        assertNull(owner.local(target).single().file)
    }
}
