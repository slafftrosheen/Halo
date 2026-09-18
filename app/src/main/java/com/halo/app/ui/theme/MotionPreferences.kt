package com.halo.app.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Observes Android's global animator scale. A value of zero corresponds to the platform's
 * remove-animations/reduced-motion behavior. Halo Presence uses this to make state changes
 * immediate instead of running its transition animation.
 */
@Composable
fun rememberReducedMotion(): State<Boolean> {
    val context = LocalContext.current
    val resolver = context.contentResolver

    fun read(): Boolean =
        runCatching {
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)

    val reduced = remember(context) { mutableStateOf(read()) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = read()
            }
        }

        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )

        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }

    return reduced
}
