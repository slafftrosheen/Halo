package com.halo.providers.litert.models

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.sample

/** Persistent public acquisition; input is identity only, never a URL, path, hash or secret. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class LiteRtDownloadWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = coroutineScope {
        val owner = inputData.getString("acquisitionOwner")
        if (owner != null && runCatching { requireModelAcquisitionOwner(owner) }.isFailure) return@coroutineScope Result.failure()
        val manager=LiteRtModelManager(applicationContext)
        val model=manager.catalogModel(inputData.getString("modelId").orEmpty())
            ?.takeIf { it.directDownloadAllowed } ?: return@coroutineScope Result.failure()
        try { setForeground(foreground(model.displayName)) }
        catch(_: Exception) { return@coroutineScope Result.failure(workDataOf("message" to "Android couldn't start the model download. Open Halo and retry.")) }
        val reporter=launch {
            manager.downloadStates.sample(250).collect { states ->
                val state=states[model.id]
                val bytes=when(state) { is DownloadState.Downloading -> state.downloadedBytes; is DownloadState.Verifying -> state.downloadedBytes; else -> 0L }
                setProgress(workDataOf("bytes" to bytes,"phase" to if(state is DownloadState.Verifying) "Verifying" else "Downloading"))
            }
        }
        try {
            manager.runDownload(model, acquisitionOwner = owner)
            when(val state=manager.downloadStates.value[model.id]) {
                DownloadState.Installed -> Result.success()
                is DownloadState.Failed -> Result.failure(workDataOf("kind" to state.kind.name,"message" to state.userMessage))
                else -> Result.failure(workDataOf("message" to "Model installation did not finish. Retry to continue."))
            }
        } finally { reporter.cancelAndJoin() }
    }
    private fun foreground(model: String): ForegroundInfo {
        val notifications=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,applicationContext.getString(com.halo.providers.litert.R.string.model_download_channel),NotificationManager.IMPORTANCE_LOW))
        val notification=Notification.Builder(applicationContext,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(com.halo.providers.litert.R.string.model_download_title)).setContentText(model).setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,applicationContext.getString(com.halo.providers.litert.R.string.model_download_pause),WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build())
            .build()
        return if(android.os.Build.VERSION.SDK_INT>=29) ForegroundInfo(id.hashCode() and Int.MAX_VALUE,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(id.hashCode() and Int.MAX_VALUE,notification)
    }
    companion object {
        const val TAG="halo-litert-download"
        private const val CHANNEL="halo-model-downloads"
        fun request(model: LiteRtCatalogModel, acquisitionOwner: String? = null): OneTimeWorkRequest {
            acquisitionOwner?.let(::requireModelAcquisitionOwner)
            require(model.directDownloadAllowed) { "Gated credentials must not be persisted in background work" }
            return OneTimeWorkRequestBuilder<LiteRtDownloadWorker>()
                .setInputData(Data.Builder().putString("modelId", model.id)
                    .apply { acquisitionOwner?.let { putString("acquisitionOwner", it) } }.build())
                .apply { acquisitionOwner?.let { addTag("halo-model-owner:$it:${model.id}") } }
                .addTag(TAG).addTag("halo-model:${model.id}").addTag("halo-model-time:${System.currentTimeMillis()}")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        }
    }
}
