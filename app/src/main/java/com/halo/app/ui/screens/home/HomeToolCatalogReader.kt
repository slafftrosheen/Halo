package com.halo.app.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.core.content.ContextCompat
import com.halo.app.ai.PrivacyMode
import com.halo.app.ai.ProviderConnectionState
import com.halo.app.ai.ProviderControlRow
import com.halo.app.online.OnlineProblem
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.AiExecutionLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads existing owners. Discovery never connects a provider, grants access or executes a tool. */
internal class HomeToolCatalogReader(
    private val context: Context,
    private val registry: ToolRegistry,
    private val availability: ToolAvailabilityResolver,
    private val selectedProvider: () -> ProviderControlRow?,
    private val answerModelAvailable: () -> Boolean,
    private val privacyMode: () -> PrivacyMode,
    private val familyAllowsOnline: () -> Boolean,
    private val webAvailability: () -> OnlineProblem?,
    private val researchEnabled: () -> Boolean,
    private val speechReady: () -> Boolean,
    private val attachmentsAvailable: () -> Boolean,
    private val imageConfigured: () -> Boolean,
) {
    suspend fun read(request: HomeToolCatalogRequest): HomeToolCatalogSnapshot = withContext(Dispatchers.IO) {
        val bindings = registry.descriptors().associateBy { it.name }
        val states = HomeToolCatalog.descriptors.flatMap { it.toolIds }.distinct().associateWith { id ->
            val descriptor = bindings[id]
            when {
                descriptor == null -> HomeRuntimeToolReadiness(false)
                descriptor.requirements.filterIsInstance<ToolRequirement.Permission>().any {
                    ContextCompat.checkSelfPermission(context, it.permission) != PackageManager.PERMISSION_GRANTED
                } -> HomeRuntimeToolReadiness(false, HomeToolReason.ACCESS, HomeToolRecovery.PERMISSIONS)
                (descriptor.dependencies + descriptor.requirements.filterIsInstance<ToolRequirement.Tool>().map { it.id })
                    .any { !registry.contains(it.value) } -> HomeRuntimeToolReadiness(false)
                else -> when (val result = availability.checkAvailability(descriptor)) {
                    ToolAvailability.Available -> HomeRuntimeToolReadiness(true)
                    is ToolAvailability.Disabled -> HomeRuntimeToolReadiness(false, HomeToolReason.DISABLED, HomeToolRecovery.SKILLS)
                    is ToolAvailability.MissingCapabilities -> {
                        val device = result.missing.any { !it.value.startsWith("host.") }
                        HomeRuntimeToolReadiness(false, if (device) HomeToolReason.DEVICE else HomeToolReason.ACCESS,
                            if (device) HomeToolRecovery.DEVICES else HomeToolRecovery.PERMISSIONS)
                    }
                    is ToolAvailability.Degraded -> HomeRuntimeToolReadiness(false, HomeToolReason.UNAVAILABLE)
                }
            }
        }
        val provider = selectedProvider()
        val readiness = when {
            provider?.configured != true || !answerModelAvailable() -> HomeAnswerReadiness.NEEDS_SETUP
            provider.connection == ProviderConnectionState.READY -> HomeAnswerReadiness.READY
            provider.connection in setOf(ProviderConnectionState.CONFIGURED, ProviderConnectionState.CHECKING) -> HomeAnswerReadiness.UNCHECKED
            provider.connection in setOf(ProviderConnectionState.OFFLINE, ProviderConnectionState.RATE_LIMITED) -> HomeAnswerReadiness.OFFLINE
            else -> HomeAnswerReadiness.NEEDS_SETUP
        }
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = if (manager != null) manager.getNetworkCapabilities(manager.activeNetwork) else null
        val connected = network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val mapHandler = listOf("geo:0,0", "https://www.google.com/maps/").any { target ->
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).resolveActivity(context.packageManager) != null
        }
        HomeToolCatalog.project(HomeToolCatalogInput(states,
            privateMode = privacyMode() == PrivacyMode.PRIVATE,
            onlineAllowedByFamily = familyAllowsOnline(), connected = connected,
            answerReadiness = readiness,
            answerOffDevice = provider?.descriptor?.location?.let { it != AiExecutionLocation.ON_DEVICE } == true,
            answerRequiresInternet = provider?.descriptor?.location == AiExecutionLocation.REMOTE,
            webProblem = webAvailability(), mapHandler = mapHandler,
            researchEnabled = researchEnabled(), speechReady = speechReady(),
            microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            voiceModeActive = request.voiceModeActive, repeatVoiceAvailable = request.repeatVoiceAvailable,
            voiceStartAllowed = request.voiceStartAllowed,
            attachmentsAvailable = attachmentsAvailable(), imageConfigured = imageConfigured(),
            answerProviderName = provider?.descriptor?.displayName?.take(80)))
    }
}
