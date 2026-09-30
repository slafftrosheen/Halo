package com.halo.runtime.cognition.routine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.util.UUID

/** Content-free service bridge. No event history, no accessibility tree, no notification prose. */
object AutomationServiceSignals {
    val foreground = ServiceSignalSource()
    val notifications = ServiceSignalSource(AutomationSourceStatus.REQUIRES_PERMISSION)
    @Volatile var foregroundPackage: String? = null
        private set
    @Synchronized fun foregrounded(packageName: String) {
        if (packageName.length !in 1..256) return
        val previous = foregroundPackage
        foregroundPackage = packageName
        if (previous != null && previous != packageName) foreground.emit(AutomationSignal(UUID.randomUUID().toString(),
            AutomationSignalKind.APP_FOREGROUNDED, Instant.now(), "foreground", AutomationTrust.PLATFORM_EVENT,
            mapOf("package" to AutomationValue.Text(packageName))))
    }
    @Synchronized fun foregroundDisconnected() { foregroundPackage = null; foreground.availability.value = AutomationSourceStatus.REQUIRES_SERVICE }
}
class ServiceSignalSource(initial: AutomationSourceStatus = AutomationSourceStatus.REQUIRES_SERVICE) : AutomationSignalSource {
    override val availability = MutableStateFlow(initial)
    override val signals = MutableSharedFlow<AutomationSignal>(extraBufferCapacity = 64)
    fun emit(signal: AutomationSignal) { signals.tryEmit(signal) }
}
