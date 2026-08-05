package com.halo.runtime.scheduler

import com.halo.sdk.runtime.Scheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultScheduler : Scheduler {
    override suspend fun immediate(): CoroutineDispatcher = Dispatchers.Main.immediate
    override suspend fun realtime(): CoroutineDispatcher = Dispatchers.Default
    override suspend fun background(): CoroutineDispatcher = Dispatchers.IO
    override suspend fun maintenance(): CoroutineDispatcher = Dispatchers.Default
}
