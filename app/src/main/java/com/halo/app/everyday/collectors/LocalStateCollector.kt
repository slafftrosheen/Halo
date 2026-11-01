package com.halo.app.everyday.collectors

import java.security.MessageDigest

/** Reads only an existing local owner. It never requests notifications, location, models or network. */
class LocalStateCollector(override val kind: CollectorKind, private val read: suspend () -> List<LocalFact>) : Collector {
    init { require(kind.name.startsWith("LOCAL_")) }
    override suspend fun fetch(source: CollectorSource, cached: CollectorSnapshot?): CollectorFetchResult {
        require(source.kind == kind && source.selected && source.enabled && !source.external)
        val facts = read().distinctBy { it.id }.take(CollectorRefreshPolicy.MAX_ITEMS)
        val digest = MessageDigest.getInstance("SHA-256")
        facts.forEach { fact ->
            listOf(fact.id, fact.kind, fact.title, fact.summary, fact.route, fact.revision.orEmpty(), fact.occurredAt?.toString().orEmpty()).forEach {
                val bytes = it.toByteArray(Charsets.UTF_8)
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
            }
        }
        return CollectorFetchResult.Updated(CollectorPayload.Local(facts), digest.digest().joinToString("") { "%02x".format(it) }, null, null, null)
    }
}
