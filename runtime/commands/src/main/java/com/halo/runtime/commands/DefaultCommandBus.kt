package com.halo.runtime.commands

import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.RuntimeCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class DefaultCommandBus : CommandBus {
    private val _commands = MutableSharedFlow<RuntimeCommand>()
    
    override suspend fun dispatch(command: RuntimeCommand) {
        _commands.emit(command)
    }

    override fun commands(): Flow<RuntimeCommand> = _commands
}
