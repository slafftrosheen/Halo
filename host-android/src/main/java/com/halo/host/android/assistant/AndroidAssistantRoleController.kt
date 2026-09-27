package com.halo.host.android.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantEnrollmentPath {
    ROLE_REQUEST,
    DEFAULT_APPS_SETTINGS,
    VOICE_INPUT_SETTINGS,
    GENERAL_SETTINGS,
}

data class AssistantEnrollmentCandidate(
    val path: AssistantEnrollmentPath,
    val intent: Intent,
)

sealed interface AssistantEnrollmentLaunchResult {
    data class Launched(val path: AssistantEnrollmentPath) : AssistantEnrollmentLaunchResult
    data class Failed(val reason: String) : AssistantEnrollmentLaunchResult
}

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
    val integrationState: StateFlow<AssistantIntegrationState>
    fun refresh()
    fun createRoleRequestIntent(): Intent?
    fun resolveEnrollmentAction(): AssistantEnrollmentAction
    fun enrollmentCandidates(): List<AssistantEnrollmentCandidate>
    fun launchEnrollment(launcher: (Intent) -> Unit): AssistantEnrollmentLaunchResult
}

class AndroidAssistantRoleController(
    private val context: Context,
    private val serviceComponent: ComponentName = ComponentName(context, "com.halo.app.assistant.HaloVoiceInteractionService")
) : AssistantRoleController {
    private val appContext = context.applicationContext
    private val inspector = AssistantEligibilityInspector(appContext, voiceServiceComponent = serviceComponent)
    private val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appContext.getSystemService(RoleManager::class.java)
    } else null

    private val mutableLegacyState = MutableStateFlow<AssistantRoleState>(AssistantRoleState.Unsupported)
    override val state: StateFlow<AssistantRoleState> = mutableLegacyState.asStateFlow()

    private val mutableEnrollmentState = MutableStateFlow<AssistantEnrollmentState>(AssistantEnrollmentState.SystemSettingsRequired)
    override val enrollmentState: StateFlow<AssistantEnrollmentState> = mutableEnrollmentState.asStateFlow()

    private val _integrationState = MutableStateFlow<AssistantIntegrationState>(AssistantIntegrationState.Checking)
    override val integrationState: StateFlow<AssistantIntegrationState> = _integrationState.asStateFlow()

    init { refresh() }

    override fun refresh() {
        val snapshot = inspector.inspect()
        val nextState = inspector.evaluateState(snapshot)
        _integrationState.value = nextState

        val isActive = snapshot.activeVoiceInteractionService

        if (isActive) {
            mutableEnrollmentState.value = AssistantEnrollmentState.Active
            @Suppress("DEPRECATION")
            mutableLegacyState.value = AssistantRoleState.Active
            return
        }

        val candidates = enrollmentCandidates()
        if (candidates.any { it.path == AssistantEnrollmentPath.ROLE_REQUEST }) {
            mutableEnrollmentState.value = AssistantEnrollmentState.RoleRequestAvailable
            @Suppress("DEPRECATION")
            mutableLegacyState.value = AssistantRoleState.Available
            return
        }

        if (candidates.isNotEmpty()) {
            mutableEnrollmentState.value = AssistantEnrollmentState.SystemSettingsRequired
            @Suppress("DEPRECATION")
            mutableLegacyState.value = AssistantRoleState.Available
            return
        }

        mutableEnrollmentState.value = AssistantEnrollmentState.Unavailable("Device settings for default assistant are not accessible on this device.")
        @Suppress("DEPRECATION")
        mutableLegacyState.value = AssistantRoleState.Unsupported
    }

    override fun enrollmentCandidates(): List<AssistantEnrollmentCandidate> {
        val list = mutableListOf<AssistantEnrollmentCandidate>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            if (roleIntent != null && canResolve(roleIntent)) {
                list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.ROLE_REQUEST, roleIntent))
            }
        }

        val manageAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        if (canResolve(manageAppsIntent)) {
            list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.DEFAULT_APPS_SETTINGS, manageAppsIntent))
        }

        val voiceSettingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        if (canResolve(voiceSettingsIntent)) {
            list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.VOICE_INPUT_SETTINGS, voiceSettingsIntent))
        }

        val generalSettingsIntent = Intent(Settings.ACTION_SETTINGS)
        if (canResolve(generalSettingsIntent)) {
            list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.GENERAL_SETTINGS, generalSettingsIntent))
        }

        return list
    }

    override fun launchEnrollment(launcher: (Intent) -> Unit): AssistantEnrollmentLaunchResult {
        val candidates = enrollmentCandidates()
        if (candidates.isEmpty()) {
            Log.e("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_FAILED reason=no_resolvable_candidate")
            return AssistantEnrollmentLaunchResult.Failed("No assistant settings activity found on device")
        }

        val first = candidates.first()
        return try {
            Log.i("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_TAP path=${first.path}")
            launcher(first.intent)
            Log.i("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_LAUNCHED path=${first.path}")
            AssistantEnrollmentLaunchResult.Launched(first.path)
        } catch (e: Exception) {
            Log.e("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_FAILED path=${first.path} error=${e.message}")
            AssistantEnrollmentLaunchResult.Failed(e.message ?: "Launch failed")
        }
    }

    override fun createRoleRequestIntent(): Intent? {
        val action = resolveEnrollmentAction()
        return (action as? AssistantEnrollmentAction.Launch)?.intent
    }

    override fun resolveEnrollmentAction(): AssistantEnrollmentAction {
        val candidate = enrollmentCandidates().firstOrNull()
        return if (candidate != null) {
            AssistantEnrollmentAction.Launch(candidate.intent)
        } else {
            AssistantEnrollmentAction.Unavailable("Unable to open Android assistant settings")
        }
    }

    private fun canResolve(intent: Intent): Boolean {
        return appContext.packageManager.resolveActivity(intent, 0) != null
    }
}
