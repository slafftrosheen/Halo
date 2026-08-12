package com.halo.drivers.heycyan.debug

/**
 * Debug logger for HeyCyan transport tracing.
 * Uses android.util.Log in production, and can be overridden in tests.
 */
object HeyCyanLogger {
    var logger: (tag: String, msg: String) -> Unit = { tag, msg ->
        android.util.Log.i(tag, msg)
    }

    fun i(tag: String, msg: String) {
        logger(tag, msg)
    }
}
