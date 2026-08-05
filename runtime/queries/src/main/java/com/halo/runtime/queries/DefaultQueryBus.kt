package com.halo.runtime.queries

import com.halo.sdk.runtime.Query
import com.halo.sdk.runtime.QueryBus


class DefaultQueryBus : QueryBus {
    // Basic stub. Real implementation maps queries to handlers.
    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> execute(query: Query<R>): R {
        // Safe fallback instead of crash
        return null as R
    }
}
