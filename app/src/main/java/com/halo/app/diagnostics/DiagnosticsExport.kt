package com.halo.app.diagnostics

import android.content.Context
import android.os.Build
import com.halo.app.di.ManualDependencyGraph
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiagnosticsExport aggregates system state, provider statuses, and connected device
 * states into a single exportable text report for TestFlight participants.
 */
object DiagnosticsExport {

    fun generateReport(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "halo_diagnostics_$timestamp.txt")
        
        val sb = StringBuilder()
        sb.append("==== HALO TESTFLIGHT DIAGNOSTICS ====\n")
        sb.append("Timestamp: ${Date()}\n")
        sb.append("OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n")

        sb.append("--- PROVIDER STATUS ---\n")
        val router = ManualDependencyGraph.aiProviderRouter
        
        sb.append("Active LLM: ${router.selectedProviderId.value ?: "None"}\n")
        sb.append("Providers:\n")
        val providers = router.getAllProviders()

        providers.forEach { p ->
            val status = p.status.value
            sb.append("  - ${p.displayName} [${p.id}]: $status\n")
        }
        sb.append("\n")

        sb.append("--- DEVICE SESSIONS ---\n")
        val sessionManager = ManualDependencyGraph.deviceSessionManager
        val sessions = sessionManager.activeSessions.value
        if (sessions.isEmpty()) {
            sb.append("No active device sessions.\n")
        } else {
            sessions.forEach { s ->
                sb.append("  - ${s.identity.displayName} (${s.identity.deviceId})\n")
                sb.append("    Type: ${s.identity.deviceType}\n")
                sb.append("    State: ${s.state.value}\n")
            }
        }
        sb.append("\n")

        sb.append("--- END OF REPORT ---\n")
        
        file.writeText(sb.toString())
        return file
    }
}
