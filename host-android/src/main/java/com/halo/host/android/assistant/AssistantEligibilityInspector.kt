package com.halo.host.android.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService

class AssistantEligibilityInspector(
    private val context: Context,
    private val voiceServiceComponent: ComponentName = ComponentName(context.packageName, "com.halo.app.assistant.HaloVoiceInteractionService"),
    private val sessionServiceComponent: ComponentName = ComponentName(context.packageName, "com.halo.app.assistant.HaloVoiceInteractionSessionService")
) {

    fun inspect(): AssistantEligibilitySnapshot {
        val problems = mutableListOf<String>()
        val pm = context.packageManager

        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
        } else null

        val roleAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        val roleHeld = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)

        val queryIntent = Intent(VoiceInteractionService.SERVICE_INTERFACE).setPackage(context.packageName)
        val resolveInfos = pm.queryIntentServices(queryIntent, PackageManager.GET_META_DATA)
        val discoveredSelfServices = resolveInfos.map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }

        val selfServiceInfo = resolveInfos.firstOrNull { it.serviceInfo.name == voiceServiceComponent.className }?.serviceInfo

        val voiceInteractionServiceDeclared = selfServiceInfo != null
        if (!voiceInteractionServiceDeclared) problems.add("HaloVoiceInteractionService not found in package manager query")

        val voiceInteractionServiceDiscoverable = discoveredSelfServices.contains(voiceServiceComponent)
        val bindPermissionCorrect = selfServiceInfo?.permission == "android.permission.BIND_VOICE_INTERACTION"
        if (selfServiceInfo != null && !bindPermissionCorrect) {
            problems.add("Service permission is '${selfServiceInfo.permission}', expected 'android.permission.BIND_VOICE_INTERACTION'")
        }

        val serviceMetadataPresent = selfServiceInfo?.metaData?.containsKey("android.voice_interaction") == true
        if (selfServiceInfo != null && !serviceMetadataPresent) {
            problems.add("Voice interaction service metadata 'android.voice_interaction' is missing")
        }

        val sessionServiceInfo = runCatching {
            pm.getServiceInfo(sessionServiceComponent, 0)
        }.getOrNull()

        val sessionServiceDeclared = sessionServiceInfo != null
        val sessionServiceClassValid = sessionServiceDeclared && sessionServiceInfo?.permission == "android.permission.BIND_VOICE_INTERACTION"
        if (!sessionServiceDeclared) {
            problems.add("HaloVoiceInteractionSessionService is not declared")
        } else if (!sessionServiceClassValid) {
            problems.add("Voice interaction session service must require android.permission.BIND_VOICE_INTERACTION")
        }

        val activeVoiceInteractionService = runCatching {
            VoiceInteractionService.isActiveService(context, voiceServiceComponent)
        }.getOrDefault(false)

        val roleRequestIntent = if (roleAvailable && roleManager != null) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        } else null
        val roleRequestResolvable = roleRequestIntent != null && pm.resolveActivity(roleRequestIntent, 0) != null

        val defaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        val defaultAppsSettingsResolvable = pm.resolveActivity(defaultAppsIntent, 0) != null

        val voiceSettingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        val voiceSettingsResolvable = pm.resolveActivity(voiceSettingsIntent, 0) != null

        return AssistantEligibilitySnapshot(
            roleAvailable = roleAvailable,
            roleHeld = roleHeld,
            voiceInteractionServiceDeclared = voiceInteractionServiceDeclared,
            voiceInteractionServiceDiscoverable = voiceInteractionServiceDiscoverable,
            bindPermissionCorrect = bindPermissionCorrect,
            serviceMetadataPresent = serviceMetadataPresent,
            sessionServiceDeclared = sessionServiceDeclared,
            sessionServiceClassValid = sessionServiceClassValid,
            activeVoiceInteractionService = activeVoiceInteractionService,
            roleRequestResolvable = roleRequestResolvable,
            defaultAppsSettingsResolvable = defaultAppsSettingsResolvable,
            voiceSettingsResolvable = voiceSettingsResolvable,
            discoveredSelfServices = discoveredSelfServices,
            problems = problems
        )
    }

    fun evaluateState(snapshot: AssistantEligibilitySnapshot): AssistantIntegrationState {
        // Android's active-service truth is authoritative even if an OEM omits query metadata.
        if (snapshot.activeVoiceInteractionService) {
            return AssistantIntegrationState.Active(snapshot)
        }

        if (
            !snapshot.voiceInteractionServiceDeclared ||
            !snapshot.voiceInteractionServiceDiscoverable ||
            !snapshot.bindPermissionCorrect ||
            !snapshot.serviceMetadataPresent ||
            !snapshot.sessionServiceDeclared ||
            !snapshot.sessionServiceClassValid
        ) {
            return AssistantIntegrationState.NotEligible(snapshot)
        }

        if (snapshot.roleHeld) {
            return AssistantIntegrationState.RoleHeldNotActive(snapshot)
        }

        return AssistantIntegrationState.EligibleNotSelected(snapshot)
    }
}
