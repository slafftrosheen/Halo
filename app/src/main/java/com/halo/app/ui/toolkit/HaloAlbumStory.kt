package com.halo.app.ui.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileAvailability
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

internal const val ALBUM_STORY_SCENE_MS = 3200L
internal data class AlbumStory(val photos: List<HaloAlbumPhoto>, val title: String, val subtitle: String, val music: Boolean)
internal data class AlbumStoryVideo(val draft: AlbumStory, val file: File, val durationMs: Long)

private const val frameSize = 720
private const val soundRate = 16_000
private const val storyFolder = "album-story-staging"

internal suspend fun requireCurrentAlbumPhoto(photo: HaloAlbumPhoto): WorkspaceFile {
    val header = Graph.appDatabase.notebookDao().getWorkspaceObject(photo.id)
    val file = requireNotNull(Graph.workspaceUseCases.getFile(photo.id)) { "Album source is unavailable" }
    check(header?.lifecycle == "ACTIVE" && file.ownership == ContentOwnership.HALO_OWNED &&
        file.availability == WorkspaceFileAvailability.OWNED_LOCAL && file.mimeType.startsWith("image/") &&
        file.uri == photo.uri && file.version == photo.version) { "Album source changed" }
    return file
}

internal suspend fun AlbumStory.requireCurrentSources() {
    for (photo in photos) requireCurrentAlbumPhoto(photo)
}

internal fun albumSourceProvenance(kind: String, photos: List<HaloAlbumPhoto>) =
    JSONObject().put("kind", kind).put("sources", JSONArray(photos.map { photo ->
        JSONObject().put("id", photo.id).put("version", requireNotNull(photo.version))
    }))

/** The actual encoded file is the playback source, local export and reviewed share attachment. */
@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun renderAlbumStory(context: Context, draft: AlbumStory, adultRevision: Long): AlbumStoryVideo {
    require(draft.photos.size in 1..5 && draft.title.isNotBlank() && draft.title.length <= 80)
    val id = UUID.randomUUID().toString()
    val inputs = File(context.cacheDir, "album-encode-$id")
    val outputDir = File(context.cacheDir, storyFolder)
    val output = File(outputDir, "story-$id.mp4")
    val duration = draft.photos.size * ALBUM_STORY_SCENE_MS
    var complete = false
    try {
        val composition = withContext(Dispatchers.IO) {
            Graph.kidsPreferences.requireAdultAction(adultRevision)
            check(inputs.mkdir() && (outputDir.isDirectory || outputDir.mkdirs()))
            val cutoff = System.currentTimeMillis() - 72L * 60 * 60 * 1000
            outputDir.listFiles().orEmpty().filter { it.name.startsWith("story-") && it.lastModified() < cutoff }.forEach(File::delete)
            val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            draft.photos.forEachIndexed { index, photo ->
                ensureActive()
                Graph.kidsPreferences.requireAdultAction(adultRevision)
                val file = requireCurrentAlbumPhoto(photo)
                val image = requireNotNull(loadAlbumBitmap(context, file.uri, frameSize * 2))
                val scene = File(inputs, "scene-$index.jpg")
                try { writeStoryScene(scene, image, draft.title, draft.subtitle, index, draft.photos.size) }
                finally { image.recycle() }
                val media = MediaItem.Builder().setUri(Uri.fromFile(scene)).setMimeType(MimeTypes.IMAGE_JPEG)
                    .setImageDurationMs(ALBUM_STORY_SCENE_MS).build()
                builder.addItem(EditedMediaItem.Builder(media).setFrameRate(24).build())
            }
            val sequence = builder.build()
            if (!draft.music) Composition.Builder(sequence).build()
            else {
                val musicFile = File(inputs, "music.wav")
                writeStoryMusic(musicFile, duration)
                val soundtrack = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(musicFile))).build()
                val audio = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addItem(soundtrack).build()
                Composition.Builder(sequence, audio).build()
            }
        }
        Graph.kidsPreferences.requireAdultAction(adultRevision)
        val result = encodeAlbumStory(context, composition, output)
        Graph.kidsPreferences.requireAdultAction(adultRevision)
        draft.requireCurrentSources()
        check(output.isFile && output.length() > 0 && result.videoFrameCount > 0)
        if (draft.music) check(result.audioMimeType != null)
        val encodedDuration = withContext(Dispatchers.IO) {
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(output.absolutePath)
                check(!draft.music || metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
                requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()).also { check(it > 0) }
            } finally { metadata.release() }
        }
        Graph.kidsPreferences.requireAdultAction(adultRevision)
        draft.requireCurrentSources()
        complete = true
        return AlbumStoryVideo(draft, output, encodedDuration)
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            inputs.deleteRecursively()
            if (!complete) output.delete()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun encodeAlbumStory(context: Context, composition: Composition, output: File): ExportResult =
    withContext(Dispatchers.Main.immediate) {
        var transformer: Transformer? = null
        var finished = false
        try {
            suspendCancellableCoroutine { continuation ->
                val exporter = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            finished = true
                            continuation.resume(exportResult)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            finished = true
                            continuation.resumeWithException(exportException)
                        }
                    }).build()
                transformer = exporter
                exporter.start(composition, output.absolutePath)
            }
        } finally {
            if (!finished) transformer?.cancel()
        }
    }

private fun writeStoryScene(file: File, source: Bitmap, title: String, subtitle: String, index: Int, total: Int) {
    val output = Bitmap.createBitmap(frameSize, frameSize, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val size = frameSize.toFloat()
        val scale = max(size / source.width, size / source.height)
        val left = (size - source.width * scale) / 2
        val top = (size - source.height * scale) / 2
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(source, null, RectF(left, top, size - left, size - top), paint)
        paint.shader = LinearGradient(0f, size * .43f, 0f, size, Color.TRANSPARENT, 0xEB080614.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, size * .43f, size, size, paint)
        paint.shader = null
        val indicator = (size - 72f - 10f * (total - 1)) / total
        repeat(total) { at ->
            val x = 36f + at * (indicator + 10f)
            paint.color = if (at == index) Color.WHITE else 0x99FFFFFF.toInt()
            canvas.drawRoundRect(x, 32f, x + indicator, 38f, 3f, 3f, paint)
        }
        val headline = storyText(title, 54f, Typeface.DEFAULT_BOLD, 2)
        val sub = storyText(subtitle, 28f, Typeface.DEFAULT, 1)
        canvas.save()
        canvas.translate(36f, size - 52f - sub.height - headline.height)
        headline.draw(canvas)
        canvas.translate(0f, headline.height + 8f)
        sub.draw(canvas)
        canvas.restore()
        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
    } finally { output.recycle() }
}

private fun storyText(text: String, size: Float, type: Typeface, lines: Int): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = size
        typeface = type
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, frameSize - 72)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
}

private fun writeStoryMusic(file: File, durationMs: Long) {
    val notes = doubleArrayOf(261.63, 329.63, 392.0, 440.0, 392.0, 329.63, 293.66, 261.63)
    val beat = soundRate * 2 / 3
    val count = (durationMs * soundRate / 1000).toInt()
    DataOutputStream(file.outputStream().buffered()).use { output ->
        fun int(value: Int) = output.writeInt(Integer.reverseBytes(value))
        fun short(value: Int) = output.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
        output.writeBytes("RIFF"); int(36 + count * 2); output.writeBytes("WAVEfmt ")
        int(16); short(1); short(1); int(soundRate); int(soundRate * 2); short(2); short(16)
        output.writeBytes("data"); int(count * 2)
        repeat(count) { position ->
            val withinBeat = position % beat
            val note = notes[(position / beat) % notes.size]
            val fraction = withinBeat.toDouble() / beat
            val angle = 2 * PI * note * withinBeat / soundRate
            val envelope = min(fraction * 10, (1 - fraction) * (1 - fraction))
            val end = ((count - position).toDouble() / (soundRate / 4)).coerceAtMost(1.0)
            short(((sin(angle) + .18 * sin(angle * 2)) * envelope * end * 1750).toInt())
        }
    }
}
