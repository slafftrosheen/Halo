package com.halo.runtime.scheduler

import com.halo.sdk.runtime.RuntimeScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultScheduler : RuntimeScheduler {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val compute: CoroutineDispatcher = Dispatchers.Default
    override val realtime: CoroutineDispatcher = Dispatchers.Default
    override val ui: CoroutineDispatcher = Dispatchers.Main.immediate
}
