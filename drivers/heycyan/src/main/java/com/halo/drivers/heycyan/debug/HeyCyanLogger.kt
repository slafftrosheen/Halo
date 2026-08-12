package com.halo.drivers.heycyan.debug

import java.time.Instant

/**
 * Debug logger for HeyCyan transport tracing.
 * Uses android.util.Log in production, and matches ConsoleLogger format for visibility.
 */
object HeyCyanLogger {
    var logger: (tag: String, msg: String) -> Unit = { tag, msg ->
        android.util.Log.i(tag, msg)
        // Match ConsoleLogger format: [INFO ] [TIMESTAMP] [HALO-tag] msg
        println("[INFO ] [${Instant.now()}] [HALO-$tag] $msg")
    }

    fun i(tag: String, msg: String) {
        logger(tag, msg)
    }
}
