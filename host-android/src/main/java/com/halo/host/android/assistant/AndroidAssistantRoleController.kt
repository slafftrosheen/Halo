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
    ASSIST_GESTURE_SETTINGS,
    GENERAL_SETTINGS,
}

data class AssistantEnrollmentDiagnostics(
    val requestMechanism: AssistantEnrollmentPath? = null,
    val activityResultCode: Int? = null,
    val roleHeldAfterReturn: Boolean? = null,
    val activeInteractorStatus: ActiveInteractorStatus = ActiveInteractorStatus.UNKNOWN,
    val activeInteractorComponent: String? = null,
    val recordedAtMs: Long? = null,
    val settingsFallbackRecommended: Boolean = false,
)

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
    val enrollmentDiagnostics: StateFlow<AssistantEnrollmentDiagnostics>
    fun refresh()
    fun createRoleRequestIntent(): Intent?
    fun resolveEnrollmentAction(): AssistantEnrollmentAction
    fun enrollmentCandidates(): List<AssistantEnrollmentCandidate>
    fun launchEnrollment(launcher: (Intent) -> Unit): AssistantEnrollmentLaunchResult
    fun launchDefaultAssistantSettings(launcher: (Intent) -> Unit): AssistantEnrollmentLaunchResult
    fun recordEnrollmentActivityResult(resultCode: Int?)
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
    private val _enrollmentDiagnostics = MutableStateFlow(AssistantEnrollmentDiagnostics())
    override val enrollmentDiagnostics: StateFlow<AssistantEnrollmentDiagnostics> =
        _enrollmentDiagnostics.asStateFlow()

    private var roleRequestReturnedWithoutOwnership = false

    init { refresh() }

    override fun refresh() {
        val snapshot = inspector.inspect()
        val nextState = inspector.evaluateState(snapshot)
        _integrationState.value = nextState
        if (snapshot.roleHeld) roleRequestReturnedWithoutOwnership = false
        _enrollmentDiagnostics.value = _enrollmentDiagnostics.value.copy(
            roleHeldAfterReturn = snapshot.roleHeld,
            activeInteractorStatus = snapshot.activeInteractorStatus,
            activeInteractorComponent = snapshot.activeInteractorComponent,
            settingsFallbackRecommended = !snapshot.activeVoiceInteractionService &&
                (roleRequestReturnedWithoutOwnership || snapshot.roleHeld),
        )

        val isActive = snapshot.roleHeld && snapshot.activeVoiceInteractionService

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
        val snapshot = inspector.inspect()

        if (!snapshot.roleHeld && !roleRequestReturnedWithoutOwnership &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        ) {
            val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            // RoleManager owns this contract; OEM role activities are not always returned by resolveActivity().
            if (roleIntent != null) {
                list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.ROLE_REQUEST, roleIntent))
            }
        }

        // If the role is held but VoiceInteractionService is not active, the authoritative repair
        // surface is Default Apps. ACTION_VOICE_INPUT_SETTINGS commonly configures only the speech
        // recognizer and previously swallowed the launch before users reached assistant selection.
        val manageAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        if (canResolve(manageAppsIntent)) {
            list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.DEFAULT_APPS_SETTINGS, manageAppsIntent))
        }

        val voiceSettingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        if (list.none { it.path == AssistantEnrollmentPath.VOICE_INPUT_SETTINGS } && canResolve(voiceSettingsIntent)) {
            list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.VOICE_INPUT_SETTINGS, voiceSettingsIntent))
        }

        // Public settings action exists across Android releases but is not exposed as a Settings constant.
        val gestureSettingsIntent = Intent("android.settings.ASSIST_GESTURE_SETTINGS")
        if (canResolve(gestureSettingsIntent)) {
            list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.ASSIST_GESTURE_SETTINGS, gestureSettingsIntent))
        }

        val generalSettingsIntent = Intent(Settings.ACTION_SETTINGS)
        if (canResolve(generalSettingsIntent)) {
            list.add(AssistantEnrollmentCandidate(AssistantEnrollmentPath.GENERAL_SETTINGS, generalSettingsIntent))
        }

        return list
    }

    override fun launchEnrollment(launcher: (Intent) -> Unit): AssistantEnrollmentLaunchResult {
        val available = enrollmentCandidates()
        val roleCandidate = available.firstOrNull { it.path == AssistantEnrollmentPath.ROLE_REQUEST }
        val candidates = if (roleCandidate != null && !roleRequestReturnedWithoutOwnership) {
            listOf(roleCandidate)
        } else {
            available.filter { it.path != AssistantEnrollmentPath.ROLE_REQUEST }
        }
        if (candidates.isEmpty()) {
            Log.e("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_FAILED reason=no_resolvable_candidate")
            return AssistantEnrollmentLaunchResult.Failed("No assistant settings activity found on device")
        }

        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                Log.i("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_TAP path=${candidate.path}")
                recordLaunch(candidate.path)
                launcher(candidate.intent)
                Log.i("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_LAUNCHED path=${candidate.path}")
                return AssistantEnrollmentLaunchResult.Launched(candidate.path)
            } catch (e: Exception) {
                Log.w("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_CANDIDATE_FAILED path=${candidate.path} error=${e.message}")
                lastError = e
            }
        }

        Log.e("HaloAssistant", "HALO_ASSISTANT_ENROLLMENT_FAILED all_candidates_failed error=${lastError?.message}")
        return AssistantEnrollmentLaunchResult.Failed(lastError?.message ?: "All launch candidates failed")
    }

    override fun launchDefaultAssistantSettings(launcher: (Intent) -> Unit): AssistantEnrollmentLaunchResult {
        val candidate = enrollmentCandidates().firstOrNull {
            it.path in setOf(
                AssistantEnrollmentPath.DEFAULT_APPS_SETTINGS,
                AssistantEnrollmentPath.ASSIST_GESTURE_SETTINGS,
                AssistantEnrollmentPath.GENERAL_SETTINGS,
            )
        } ?: return AssistantEnrollmentLaunchResult.Failed(
            "Default assistant settings are not available on this device",
        )
        return try {
            recordLaunch(candidate.path)
            launcher(candidate.intent)
            AssistantEnrollmentLaunchResult.Launched(candidate.path)
        } catch (error: Exception) {
            AssistantEnrollmentLaunchResult.Failed(
                error.message ?: "Could not open default assistant settings",
            )
        }
    }

    override fun recordEnrollmentActivityResult(resultCode: Int?) {
        val snapshot = inspector.inspect()
        val mechanism = _enrollmentDiagnostics.value.requestMechanism
        roleRequestReturnedWithoutOwnership = when {
            snapshot.roleHeld -> false
            mechanism == AssistantEnrollmentPath.ROLE_REQUEST -> true
            else -> roleRequestReturnedWithoutOwnership
        }
        _enrollmentDiagnostics.value = AssistantEnrollmentDiagnostics(
            requestMechanism = mechanism,
            activityResultCode = resultCode,
            roleHeldAfterReturn = snapshot.roleHeld,
            activeInteractorStatus = snapshot.activeInteractorStatus,
            activeInteractorComponent = snapshot.activeInteractorComponent,
            recordedAtMs = System.currentTimeMillis(),
            settingsFallbackRecommended = roleRequestReturnedWithoutOwnership ||
                (snapshot.roleHeld && snapshot.activeInteractorStatus != ActiveInteractorStatus.ACTIVE),
        )
        refresh()
    }

    private fun recordLaunch(path: AssistantEnrollmentPath) {
        _enrollmentDiagnostics.value = AssistantEnrollmentDiagnostics(
            requestMechanism = path,
            recordedAtMs = System.currentTimeMillis(),
            settingsFallbackRecommended = path != AssistantEnrollmentPath.ROLE_REQUEST,
        )
    }

    override fun createRoleRequestIntent(): Intent? {
        return enrollmentCandidates().firstOrNull { it.path == AssistantEnrollmentPath.ROLE_REQUEST }?.intent
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
