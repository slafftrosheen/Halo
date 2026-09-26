package com.halo.host.android.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AssistantEnrollmentState {
    data object Active : AssistantEnrollmentState
    data object RoleRequestAvailable : AssistantEnrollmentState
    data object SystemSettingsRequired : AssistantEnrollmentState
    data class Unavailable(val reason: String) : AssistantEnrollmentState
}

sealed interface AssistantEnrollmentAction {
    data class Launch(val intent: Intent) : AssistantEnrollmentAction
    data class Unavailable(val reason: String) : AssistantEnrollmentAction
}

@Deprecated("Replaced by AssistantEnrollmentState")
sealed interface AssistantRoleState {
    data object Unsupported : AssistantRoleState
    data object Available : AssistantRoleState
    data object Active : AssistantRoleState
}

interface AssistantRoleController {
    val state: StateFlow<AssistantRoleState>
    val enrollmentState: StateFlow<AssistantEnrollmentState>
    fun refresh()
    fun createRoleRequestIntent(): Intent?
    fun resolveEnrollmentAction(): AssistantEnrollmentAction
}

class AndroidAssistantRoleController(
    private val context: Context,
    private val serviceComponent: ComponentName = ComponentName(context, "com.halo.app.assistant.HaloVoiceInteractionService")
) : AssistantRoleController {
    private val appContext = context.applicationContext
    private val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appContext.getSystemService(RoleManager::class.java)
    } else null

    private val mutableLegacyState = MutableStateFlow<AssistantRoleState>(AssistantRoleState.Unsupported)
    override val state: StateFlow<AssistantRoleState> = mutableLegacyState.asStateFlow()

    private val mutableEnrollmentState = MutableStateFlow<AssistantEnrollmentState>(AssistantEnrollmentState.SystemSettingsRequired)
    override val enrollmentState: StateFlow<AssistantEnrollmentState> = mutableEnrollmentState.asStateFlow()

    init { refresh() }

    override fun refresh() {
        val isActive = VoiceInteractionService.isActiveService(appContext, serviceComponent)

        if (isActive) {
            mutableEnrollmentState.value = AssistantEnrollmentState.Active
            @Suppress("DEPRECATION")
            mutableLegacyState.value = AssistantRoleState.Active
            return
        }

        val manager = roleManager
        val isRoleAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)

        if (isRoleAvailable) {
            val roleIntent = manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            if (canResolve(roleIntent)) {
                mutableEnrollmentState.value = AssistantEnrollmentState.RoleRequestAvailable
                @Suppress("DEPRECATION")
                mutableLegacyState.value = AssistantRoleState.Available
                return
            }
        }

        val voiceSettingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        if (canResolve(voiceSettingsIntent)) {
            mutableEnrollmentState.value = AssistantEnrollmentState.SystemSettingsRequired
            mutableLegacyState.value = AssistantRoleState.Available
            return
        }

        val manageAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        if (canResolve(manageAppsIntent)) {
            mutableEnrollmentState.value = AssistantEnrollmentState.SystemSettingsRequired
            mutableLegacyState.value = AssistantRoleState.Available
            return
        }

        mutableEnrollmentState.value = AssistantEnrollmentState.Unavailable("Device settings for default assistant are not accessible on this device.")
        mutableLegacyState.value = AssistantRoleState.Unsupported
    }

    override fun createRoleRequestIntent(): Intent? {
        val action = resolveEnrollmentAction()
        return (action as? AssistantEnrollmentAction.Launch)?.intent
    }

    override fun resolveEnrollmentAction(): AssistantEnrollmentAction {
        val manager = roleManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && manager != null && manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            val roleIntent = manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            if (roleIntent != null && canResolve(roleIntent)) {
                return AssistantEnrollmentAction.Launch(roleIntent)
            }
        }

        val voiceSettingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        if (canResolve(voiceSettingsIntent)) {
            return AssistantEnrollmentAction.Launch(voiceSettingsIntent)
        }

        val manageAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        if (canResolve(manageAppsIntent)) {
            return AssistantEnrollmentAction.Launch(manageAppsIntent)
        }

        return AssistantEnrollmentAction.Unavailable("Unable to open Android assistant settings")
    }

    private fun canResolve(intent: Intent): Boolean {
        return appContext.packageManager.resolveActivity(intent, 0) != null
    }
}
