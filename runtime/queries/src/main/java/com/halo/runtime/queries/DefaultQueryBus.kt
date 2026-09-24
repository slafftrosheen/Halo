package com.halo.runtime.queries

import com.halo.sdk.runtime.Query
import com.halo.sdk.runtime.QueryBus
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

fun interface QueryHandler<Q : Query<R>, R> {
    suspend fun handle(query: Q): R
}

class DefaultQueryBus : QueryBus {
    private val handlers = ConcurrentHashMap<KClass<*>, suspend (Query<*>) -> Any?>()

    /** Register one exact query type. Replacing a handler is explicit and deterministic. */
    fun <Q : Query<R>, R> register(type: KClass<Q>, handler: QueryHandler<Q, R>) {
        handlers[type] = { query ->
            @Suppress("UNCHECKED_CAST")
            handler.handle(query as Q)
        }
    }

    fun unregister(type: KClass<out Query<*>>) {
        handlers.remove(type)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> execute(query: Query<R>): R {
        val handler = handlers[query::class]
            ?: throw IllegalStateException("No query handler registered for ${query::class.qualifiedName}")
        return handler(query) as R
    }
}
