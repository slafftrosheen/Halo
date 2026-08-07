package com.halo.libraries.logging

import com.halo.sdk.runtime.RuntimeLogger
import java.time.Instant

class ConsoleLogger : RuntimeLogger {
    override fun trace(category: String, message: String) {
        println("[TRACE] [${Instant.now()}] [HALO-$category] $message")
    }

    override fun debug(category: String, message: String) {
        println("[DEBUG] [${Instant.now()}] [HALO-$category] $message")
    }

    override fun info(category: String, message: String) {
        println("[INFO ] [${Instant.now()}] [HALO-$category] $message")
    }

    override fun warn(category: String, message: String) {
        System.err.println("[WARN ] [${Instant.now()}] [HALO-$category] $message")
    }

    override fun error(category: String, message: String, throwable: Throwable?) {
        System.err.println("[ERROR] [${Instant.now()}] [HALO-$category] $message")
        throwable?.printStackTrace()
    }
}
