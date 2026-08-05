package com.halo.runtime.commands

import com.halo.sdk.runtime.Command
import com.halo.sdk.runtime.CommandBus

class DefaultCommandBus : CommandBus {
    // Basic stub. A real implementation needs a way to register handlers for specific command types.
    override suspend fun <R> dispatch(command: Command<R>): R {
        throw NotImplementedError("Command handlers not yet registered for ${command::class.simpleName}")
    }
}
