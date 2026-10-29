package com.halo.app.ui.screens.notebook

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID
import com.halo.sdk.runtime.resource.*

/**
 * Foreground-only capture. The caller owns RECORD_AUDIO permission and calls stop
 * on screen backgrounding; the microphone is never acquired by a background service.
 * Staging files live beneath the existing FileProvider's workspace-owned root.
 */
internal class CanvasVoiceMemoRecorder(private val context: Context, private val resources: ResourceLeaseManager) {
    private var active: MediaRecorder? = null
    private var output: File? = null
    private var lease: ResourceLease? = null
    private var limitReached = false
    val isRecording: Boolean get() = active != null

    fun start(onLimit: () -> Unit = {}) {
        // API26-27 cannot guarantee the explicitly selected phone microphone.
        if (Build.VERSION.SDK_INT < 28) throw IllegalStateException("Phone microphone routing requires Android 9 or newer")
        check(!isRecording)
        limitReached = false
        val id = "workspace-memo:${UUID.randomUUID()}"
        val granted = resources.tryAcquire(ResourceLeaseRequest(id, ResourceHolder(id), setOf(
            ResourceClaim(ResourceKey(HaloResourceIds.AudioInput, ResourceEndpointId("host:primary"))),
            ResourceClaim(ResourceKey(HaloResourceIds.AudioSession, ResourceEndpointId("host:android-audio"))),
        ), preemption = ResourcePreemptionPolicy.NEVER))
        check(granted is ResourceAcquireResult.Granted) { "The microphone is busy" }
        lease = granted.lease
        val dir = File(context.filesDir, "workspace/owned/voice-inbox")
        if (!(dir.exists() || dir.mkdirs())) { lease?.release(); lease = null; error("Recording storage unavailable") }
        if ((dir.listFiles()?.size ?: 0) >= 20) { lease?.release(); lease = null; error("Retry or discard saved memos first") }
        val file = File(dir, "memo_${UUID.randomUUID()}.m4a")
        var recorder: MediaRecorder? = null
        try {
            @Suppress("DEPRECATION")
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            recorder = created
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(96_000)
            created.setAudioSamplingRate(44_100)
            created.setAudioChannels(1)
            created.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    limitReached = true
                    onLimit()
                }
            }
            created.setMaxDuration(120_000)
            created.setMaxFileSize(12_000_000)
            created.setOutputFile(file.absolutePath)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val phoneMic = audio.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC }
            check(phoneMic != null && created.setPreferredDevice(phoneMic)) { "Phone microphone unavailable" }
            created.prepare()
            created.start()
            output = file
            active = created
        } catch (error: Exception) {
            runCatching { recorder?.release() }
            file.delete()
            lease?.release(); lease = null
            throw error
        }
    }

    /** null for aborted/too-short recordings; only a completed file can be imported. */
    fun stop(): File? {
        val recorder = active ?: return null
        val file = output
        active = null
        output = null
        var completed = false
        try {
            recorder.stop()
            completed = file != null && file.isFile && file.length() >= 1024
        } catch (_: RuntimeException) {
            // Android may already have stopped at the enforced limit; only retain non-empty capped output.
            completed = limitReached && file?.isFile == true && file.length() in 1024..12_100_000
        } finally {
            runCatching { recorder.release() }
            lease?.release(); lease = null
        }
        if (!completed) file?.delete()
        return file?.takeIf { completed }
    }

    fun cancel() {
        if (isRecording) stop()?.delete()
        output?.delete()
        output = null
    }
}
