package com.halo.runtime.bus

/**
 * The CommandBus handles imperative actions ("Do this").
 * Commands have exactly one handler and return a result.
 */
interface CommandBus {
    suspend fun <R> execute(command: HaloCommand<R>): CommandResult<R>
    
    fun <C : HaloCommand<R>, R> registerHandler(
        commandType: Class<C>, 
        handler: CommandHandler<C, R>
    )
}

interface HaloCommand<R>

interface CommandHandler<C : HaloCommand<R>, R> {
    suspend fun handle(command: C): R
}

sealed class CommandResult<out T> {
    data class Success<out T>(val data: T) : CommandResult<T>()
    data class Failure(val reason: String, val throwable: Throwable? = null) : CommandResult<Nothing>()
}
