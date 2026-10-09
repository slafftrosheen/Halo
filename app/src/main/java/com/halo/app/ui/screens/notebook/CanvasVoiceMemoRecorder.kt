package com.halo.app.ui.screens.notebook

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Foreground-only capture. The caller owns RECORD_AUDIO permission and calls stop
 * on screen backgrounding; the microphone is never acquired by a background service.
 * Staging files live beneath the existing FileProvider's workspace-owned root.
 */
internal class CanvasVoiceMemoRecorder(private val context: Context) {
    private var active: MediaRecorder? = null
    private var output: File? = null
    val isRecording: Boolean get() = active != null

    fun start() {
        check(!isRecording)
        val dir = File(context.filesDir, "workspace/owned/voice-inbox")
        check(dir.exists() || dir.mkdirs())
        val file = File(dir, "memo_${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            output = file
            active = recorder
        } catch (error: Exception) {
            runCatching { recorder.release() }
            file.delete()
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
            // Android throws when stop follows an interrupted or too-short capture.
        } finally {
            recorder.release()
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
