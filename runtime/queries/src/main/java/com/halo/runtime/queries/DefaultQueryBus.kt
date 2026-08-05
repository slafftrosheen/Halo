package com.halo.runtime.queries

import com.halo.sdk.runtime.Query
import com.halo.sdk.runtime.QueryBus

class DefaultQueryBus : QueryBus {
    // Basic stub. Real implementation maps queries to handlers.
    override suspend fun <R> execute(query: Query<R>): R {
        throw NotImplementedError("Query handlers not yet registered for ${query::class.simpleName}")
    }
}
