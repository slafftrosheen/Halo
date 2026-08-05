package com.halo.runtime.bus

/**
 * The QueryBus handles requests for information ("What is this?").
 * Queries have exactly one handler and return data without mutating state.
 */
interface QueryBus {
    suspend fun <R> ask(query: HaloQuery<R>): QueryResult<R>
    
    fun <Q : HaloQuery<R>, R> registerHandler(
        queryType: Class<Q>, 
        handler: QueryHandler<Q, R>
    )
}

interface HaloQuery<R>

interface QueryHandler<Q : HaloQuery<R>, R> {
    suspend fun handle(query: Q): R
}

sealed class QueryResult<out T> {
    data class Success<out T>(val data: T) : QueryResult<T>()
    data class Failure(val reason: String, val throwable: Throwable? = null) : QueryResult<Nothing>()
}
