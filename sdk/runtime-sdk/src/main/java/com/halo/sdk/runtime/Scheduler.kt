package com.halo.sdk.runtime

import kotlinx.coroutines.CoroutineDispatcher

interface Scheduler {
    suspend fun immediate(): CoroutineDispatcher
    suspend fun realtime(): CoroutineDispatcher
    suspend fun background(): CoroutineDispatcher
    suspend fun maintenance(): CoroutineDispatcher
}
