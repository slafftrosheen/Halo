package com.halo.app.ui.toolkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AlbumPhotoAdjustments(
    val ratio: Float? = null,
    val light: Float = 0f,
    val colour: Float = 0f,
    val warmth: Float = 0f,
    val rotation: Float = 0f,
) {
    fun render(original: Bitmap): Bitmap {
        val rotated = if (rotation == 0f) original else Bitmap.createBitmap(original, 0, 0,
            original.width, original.height, Matrix().apply { postRotate(rotation.coerceIn(-45f, 45f)) }, true)
        val targetRatio = ratio ?: rotated.width.toFloat() / rotated.height
        val width = minOf(rotated.width, (rotated.height * targetRatio).toInt()).coerceAtLeast(1)
        val height = minOf(rotated.height, (rotated.width / targetRatio).toInt()).coerceAtLeast(1)
        val exposure = light.coerceIn(-100f, 100f) * 1.1f
        val temperature = warmth.coerceIn(-100f, 100f) * .5f
        val matrix = ColorMatrix().apply {
            setSaturation(1f + colour.coerceIn(-100f, 100f) / 100f)
            postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,exposure+temperature,
                0f,1f,0f,0f,exposure, 0f,0f,1f,0f,exposure-temperature, 0f,0f,0f,1f,0f)))
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(rotated, (width - rotated.width) / 2f, (height - rotated.height) / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        }
    }
}

@Composable
internal fun HaloAlbumPhotoEditor(
    photo: HaloAlbumPhoto,
    startWithCrop: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (AlbumPhotoAdjustments) -> Unit,
) {
    val context = LocalContext.current
    var edits by remember(photo.id) { mutableStateOf(AlbumPhotoAdjustments()) }
    var crop by remember(photo.id) { mutableStateOf(startWithCrop) }
    var originalOnly by remember(photo.id) { mutableStateOf(false) }
    val original by produceState<Bitmap?>(null, photo.uri) {
        try { value = withContext(Dispatchers.IO) { loadAlbumBitmap(context, photo.uri, 960) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    val preview by produceState(original, original, edits, originalOnly) {
        val bitmap = original ?: return@produceState
        value = if (originalOnly || edits == AlbumPhotoAdjustments()) bitmap
            else withContext(Dispatchers.Default) { edits.render(bitmap) }
    }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(uiText("Edit photo")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                preview?.let { Image(it.asImageBitmap(), photo.name, Modifier.fillMaxWidth().height(184.dp), contentScale = ContentScale.Fit) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(crop, onClick = { crop = !crop }, label = { Text(uiText("Crop")) })
                    FilterChip(originalOnly, onClick = { originalOnly = !originalOnly }, label = { Text(uiText("Original")) })
                    TextButton(onClick = { edits = AlbumPhotoAdjustments(); originalOnly = false }, enabled = !busy) { Text(uiText("Revert")) }
                }
                if (crop) listOf(null to "Free", 1f to "1:1", .8f to "4:5", 16f / 9f to "16:9").forEach { (ratio, label) ->
                    TextButton(onClick = { edits = edits.copy(ratio = ratio); originalOnly = false }, enabled = !busy) {
                        Text((if (ratio == edits.ratio) "✓ " else "") + uiText(label))
                    }
                } else {
                    AdjustmentSlider("Light", edits.light, -100f..100f, !busy) { edits = edits.copy(light = it); originalOnly = false }
                    AdjustmentSlider("Colour", edits.colour, -100f..100f, !busy) { edits = edits.copy(colour = it); originalOnly = false }
                    AdjustmentSlider("Warmth", edits.warmth, -100f..100f, !busy) { edits = edits.copy(warmth = it); originalOnly = false }
                    AdjustmentSlider("Straighten", edits.rotation, -45f..45f, !busy) { edits = edits.copy(rotation = it); originalOnly = false }
                }
            }
        }, confirmButton = { TextButton(onClick = { onSave(edits) }, enabled = !busy && original != null && edits != AlbumPhotoAdjustments()) {
            Text(uiText(if (busy) "Saving…" else "Save a copy"))
        } }, dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(uiText("Cancel")) } })
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, update: (Float) -> Unit) {
    Text("${uiText(label)} · ${value.toInt()}")
    Slider(value, update, valueRange = range, enabled = enabled)
}
