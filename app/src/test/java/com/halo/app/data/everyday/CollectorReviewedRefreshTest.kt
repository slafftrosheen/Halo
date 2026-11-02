package com.halo.app.data.everyday

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class CollectorReviewedRefreshTest {
    @Test fun queuedReviewedRefreshCannotFetchAnEditedEndpointAfterThePreviousRequestFinishes()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(),AppDatabase::class.java).allowMainThreadQueries().build()
        try {withTimeout(10000) {
            lateinit var coordinator: CollectorCoordinator
            val repository=RoomCollectorRepository(db,sourceChanging={coordinator.cancel(it)})
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val versions=mutableListOf<Long>()
            val adapter=object: Collector {
                override val kind=CollectorKind.FEED
                override suspend fun fetch(source: CollectorSource,cached: CollectorSnapshot?): CollectorFetchResult {
                    versions+=source.version;entered.complete(Unit)
                    withContext(NonCancellable) {release.await()}
                    return CollectorFetchResult.Updated(CollectorPayload.Articles(emptyList()),"synthetic",null,null,source.endpoint)
                }
            }
            val policy=object: RemoteDispatchPolicy {
                override val revision=0L
                override fun acquire(request: RemoteDispatchRequest,expectedRevision: Long,recordActivity: Boolean,cancel: ()->Unit)=object: RemoteDispatchLease {
                    override fun requireCurrent() { }
                    override fun finish(outcome: RemoteDispatchOutcome,providerReportedCostUsd: String?) { }
                }
            }
            coordinator=CollectorCoordinator(repository,CollectorRegistry(listOf(adapter)),policy)
            val first=repository.save(StarterSourceCatalogue.external().first().copy(selected=true),null)
            val running=async {coordinator.refreshDetailed(first.id,manual=true)}
            entered.await()
            val queued=async(start=CoroutineStart.UNDISPATCHED) {runCatching {coordinator.refreshDetailed(first.id,manual=true,expectedVersion=first.version)}}
            val edited=repository.save(first.copy(endpoint="https://example.org/changed-feed"),first.version)
            release.complete(Unit);running.await()
            assertTrue(queued.await().exceptionOrNull() is CollectorConflict)
            assertEquals(listOf(first.version),versions)
            assertEquals(edited.version,repository.state(first.id)!!.source.version)
        }} finally {db.close()}
    }
}
