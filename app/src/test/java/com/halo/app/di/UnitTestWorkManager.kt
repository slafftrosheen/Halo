package com.halo.app.di

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Only graph integration tests need background-download observation. Initialize it
 * before graph construction, while the Robolectric application is still bound. */
internal fun initializeUnitTestWorkManager(context: Context) = runBlocking(Dispatchers.IO) {
    // Robolectric may reuse the SDK sandbox between these integration fixtures.
    if (runCatching { WorkManager.getInstance(context) }.isSuccess) return@runBlocking
    val synchronous = Executor { command -> command.run() }
    WorkManager.initialize(context, Configuration.Builder()
        .setExecutor(synchronous)
        .setTaskExecutor(synchronous)
        .build())
}
