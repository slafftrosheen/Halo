package com.halo.app.ui.toolkit

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

@Composable
internal fun HaloScanCamera(
    zoom: Float,
    highContrast: Boolean,
    onView: (PreviewView?) -> Unit,
    onAvailable: (Boolean) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val available by rememberUpdatedState(onAvailable)
    val failure by rememberUpdatedState(onFailure)
    val viewChanged by rememberUpdatedState(onView)
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    DisposableEffect(owner, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build()
        var active = true
        var provider: ProcessCameraProvider? = null
        val observer = Observer<PreviewView.StreamState> { available(it == PreviewView.StreamState.STREAMING) }
        previewView.previewStreamState.observe(owner, observer)
        viewChanged(previewView)
        future.addListener({
            if (active) try {
                provider = future.get().also {
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    camera = it.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                }
            } catch (_: Exception) { failure() }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            previewView.previewStreamState.removeObserver(observer)
            provider?.unbind(preview)
            camera = null
            viewChanged(null)
            available(false)
        }
    }
    SideEffect {
        camera?.let { bound ->
            bound.cameraInfo.zoomState.value?.let { state ->
                val target = zoom.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                if (kotlin.math.abs(target - state.zoomRatio) > .02f) bound.cameraControl.setZoomRatio(target)
            }
        }
        previewView.setLayerType(if (highContrast) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
            if (highContrast) Paint().apply { colorFilter = ColorMatrixColorFilter(scanContrastMatrix) } else null)
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

internal val scanContrastMatrix = floatArrayOf(
    .59f, 1.17f, .23f, 0f, -128f, .59f, 1.17f, .23f, 0f, -128f,
    .59f, 1.17f, .23f, 0f, -128f, 0f, 0f, 0f, 1f, 0f,
)

internal fun PreviewView.scanBitmap(): Bitmap? {
    val original = bitmap ?: return null
    val scale = (1440f / maxOf(original.width, original.height)).coerceAtMost(1f)
    if (scale == 1f) return original
    return Bitmap.createScaledBitmap(
        original, (original.width * scale).roundToInt().coerceAtLeast(1),
        (original.height * scale).roundToInt().coerceAtLeast(1), true,
    ).also { if (it !== original) original.recycle() }
}
