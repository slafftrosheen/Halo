package com.halo.app.everyday.knowledge

import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.*
import java.time.Instant

/** Metadata projection only. Corpus bodies remain in the canonical library owner. */
class LocalReferenceCollector(private val database: AppDatabase) : Collector {
    override val kind = CollectorKind.LOCAL_LIBRARY
    override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult {
        val library = database.referenceDao().library(source.id)
            ?: return CollectorFetchResult.Unavailable("Review and import this library first")
        val generation = library.generation ?: return CollectorFetchResult.Unavailable("Import is not ready; previous library content is unchanged")
        val fact = LocalFact(source.id, "REFERENCE_LIBRARY", library.title, "${library.entryCount} locally indexed entries",
            "halo://screen/organizer/libraries?sourceId=${source.id}", generation, library.importedAt?.let(Instant::ofEpochMilli))
        return CollectorFetchResult.Updated(CollectorPayload.Local(listOf(fact)), generation, null, null, null)
    }
}
