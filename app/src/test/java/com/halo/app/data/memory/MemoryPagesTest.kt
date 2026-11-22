package com.halo.app.data.memory

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.app.di.tools.MemoryToolExecutor
import com.halo.runtime.cognition.memory.InteractionWorkingMemory
import com.halo.runtime.cognition.memory.LearningPreferences
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolFeatureOperability
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.ToolCall
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MemoryPagesTest {
    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try { block(db) } finally { db.close() }
    }

    @Test fun linksAndTagsSurviveCorrectionAndDisappearWhenAPageIsForgotten() = runBlocking {
        withDatabase { db ->
                val clock = AtomicLong(1000)
                val repository = RoomMemoryRepository(db, clock::incrementAndGet)
                val page = repository.promoteCandidate(MemoryCandidate("Orchard notes from spring"))
                assertNull(repository.linkNewPage(page.id, page.updatedAt - 1, "Storage cupboard"))
                val target = requireNotNull(repository.linkNewPage(page.id, page.updatedAt, "Storage cupboard"))
                assertTrue(repository.tagPage(page.id, page.updatedAt, "#Дом", true))
                assertTrue(repository.tagPage(page.id, page.updatedAt, "ДОМ", true))
                assertTrue(repository.correctMemory(page.id, "Orchard notes from summer"))

                val metadata = repository.observePageMetadata().first()
                assertEquals(listOf(MemoryPageLinkEntity(page.id, target.id)), metadata.links)
                assertEquals(listOf("дом"), metadata.tagsOf(page.id).map { it.key })
                assertFalse(repository.linkPage(page.id, page.updatedAt, target.id, target.updatedAt, false))
                assertTrue(repository.forgetMemory(target.id))
                assertTrue(repository.observePageMetadata().first().links.isEmpty())
                assertTrue(repository.forgetMemory(page.id))
                assertTrue(repository.observePageMetadata().first().tags.isEmpty())
            }
    }

    @Test fun mapFollowsBothDirectionsAndHonorsDepthAndPageAccess() {
        val links = listOf(
            MemoryPageLinkEntity("a", "b"),
            MemoryPageLinkEntity("c", "b"),
            MemoryPageLinkEntity("d", "c"),
            MemoryPageLinkEntity("hidden", "b"),
        )
        val visible = setOf("a", "b", "c", "d")
        assertEquals(mapOf("a" to 0, "b" to 1), memoryPageNeighborhood("a", links, visible, 1).distance)
        val map = memoryPageNeighborhood("a", links, visible, 3)
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 3), map.distance)
        assertEquals(links.slice(0..2), map.links)
        assertTrue(memoryPageNeighborhood("a", links, visible, 3, limit = 2).truncated)
    }

    @Test fun registeredGraphToolFiltersOutPagesOutsideTheCurrentScope() = runBlocking {
        withDatabase { db ->
                val repository = RoomMemoryRepository(db)
                val source = repository.promoteCandidate(MemoryCandidate("Garden notes"))
                val hidden = repository.promoteCandidate(MemoryCandidate("Project pencil drawings",
                    scope = MemoryScope.PROJECT, scopeId = "another-project"))
                assertTrue(repository.linkPage(source.id, source.updatedAt, hidden.id, hidden.updatedAt, true))

                val executor = MemoryToolExecutor(repository, BoundedMemoryContextSource(repository),
                    PersonalLearningRepository(db, repository), { LearningPreferences() }, { emptySet() }, { false }, InteractionWorkingMemory())
                val registry = ToolRegistry(listOf(executor), executor.definitions().map { requireNotNull(HaloToolDescriptors.memory(it)) })
                val names = setOf("halo_memory_page_connections", "halo_memory_page_link", "halo_memory_page_tag")
                assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("memory-pages", names))).isEmpty())
                assertEquals(ConfirmationPolicy.ALWAYS, registry.descriptor("halo_memory_page_link")!!.security.confirmation)

                val result = executor.execute(ToolCall("memory-map", "halo_memory_page_connections", mapOf("id" to source.id)))
                    as ToolExecutionResult.Success
                val map = JSONObject(result.output)
                assertEquals(1, map.getJSONArray("pages").length())
                assertEquals(0, map.getJSONArray("links").length())
                assertEquals(source.updatedAt, map.getLong("version"))
            }
    }
}
