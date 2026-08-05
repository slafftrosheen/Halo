package com.halo.sdk.runtime

interface Command<R>

interface CommandBus {
    suspend fun <R> dispatch(command: Command<R>): R
}
