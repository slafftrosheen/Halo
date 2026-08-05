package com.halo.libraries.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogCategory {
    Runtime,
    Speech,
    Reasoning,
    Planning,
    Execution,
    Memory,
    Providers,
    Android,
    UI
}

enum class LogSeverity {
    Verbose, Debug, Info, Warn, Error, Fatal
}

object HaloLogger {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(
        category: LogCategory,
        severity: LogSeverity,
        message: String,
        correlationId: String? = null,
        throwable: Throwable? = null
    ) {
        val timestamp = dateFormat.format(Date())
        val threadName = Thread.currentThread().name
        val cid = correlationId ?: UUID.randomUUID().toString().take(8)
        
        val formattedMessage = "[$timestamp] [$threadName] [${category.name}] [$cid] $message"

        when (severity) {
            LogSeverity.Verbose -> Log.v("HALO", formattedMessage, throwable)
            LogSeverity.Debug -> Log.d("HALO", formattedMessage, throwable)
            LogSeverity.Info -> Log.i("HALO", formattedMessage, throwable)
            LogSeverity.Warn -> Log.w("HALO", formattedMessage, throwable)
            LogSeverity.Error -> Log.e("HALO", formattedMessage, throwable)
            LogSeverity.Fatal -> Log.wtf("HALO", formattedMessage, throwable)
        }
    }

    fun v(category: LogCategory, message: String, correlationId: String? = null) = 
        log(category, LogSeverity.Verbose, message, correlationId)
        
    fun d(category: LogCategory, message: String, correlationId: String? = null) = 
        log(category, LogSeverity.Debug, message, correlationId)
        
    fun i(category: LogCategory, message: String, correlationId: String? = null) = 
        log(category, LogSeverity.Info, message, correlationId)
        
    fun w(category: LogCategory, message: String, correlationId: String? = null, throwable: Throwable? = null) = 
        log(category, LogSeverity.Warn, message, correlationId, throwable)
        
    fun e(category: LogCategory, message: String, correlationId: String? = null, throwable: Throwable? = null) = 
        log(category, LogSeverity.Error, message, correlationId, throwable)
}
