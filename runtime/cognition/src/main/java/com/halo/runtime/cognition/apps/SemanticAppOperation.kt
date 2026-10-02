package com.halo.runtime.cognition.apps

import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.runtime.cognition.tools.ToolRiskClass
import java.time.Instant
import java.util.UUID

sealed interface AppTarget {
    data object CurrentApp : AppTarget
    data class Package(val name: String) : AppTarget
    data class Application(val name: String) : AppTarget
}
@JvmInline value class SemanticActionId(val value: String)
enum class AppOperationRoute { HALO_NATIVE, APP_FUNCTION, DIRECT_ACTION, ANDROID_INTENT, ACCESSIBILITY, MANUAL_TAKEOVER }
enum class DispatchTruth { NOT_DISPATCHED, DISPATCHED }
enum class EffectTruth { CONFIRMED, UNCONFIRMED, UNKNOWN }
enum class SemanticOperationFailure { FEATURE_UNAVAILABLE, CALLER_NOT_AUTHORIZED, TARGET_PERMISSION_REQUIRED,
    DISABLED, NOT_FOUND, PACKAGE_INVISIBLE, INVALID_ARGUMENTS, TARGET_REJECTED, TIMEOUT, TARGET_CRASHED,
    PLATFORM_ERROR, UNKNOWN, STALE_GENERATION, NEEDS_APPROVAL, MANUAL_TAKEOVER, CANCELLED }
enum class SemanticOperationPhase { RESOLVING, PREFLIGHT, WAITING_FOR_APPROVAL, DISPATCHING, WAITING_FOR_APP,
    VERIFYING, WAITING_FOR_USER, COMPLETED, FAILED }
enum class CapabilityScope { GLOBAL, CURRENT_ACTIVITY }
enum class SemanticValueType { STRING, INTEGER, NUMBER, BOOLEAN }
sealed interface SemanticValue {
    data class Text(val value: String) : SemanticValue
    data class Integer(val value: Long) : SemanticValue
    data class Number(val value: Double) : SemanticValue
    data class Boolean(val value: kotlin.Boolean) : SemanticValue
}
data class SemanticAppOperation(val interactionId: String, val target: AppTarget, val action: SemanticActionId,
    val arguments: Map<String, SemanticValue> = emptyMap(), val origin: InteractionOrigin? = null,
    val operationId: String = UUID.randomUUID().toString())
data class SemanticParameter(val name: String, val type: SemanticValueType, val required: Boolean)
data class SemanticAppCapability(val id: String, val packageName: String, val action: SemanticActionId,
    val actionName: String, val description: String?, val parameters: List<SemanticParameter>,
    val route: AppOperationRoute, val scope: CapabilityScope = CapabilityScope.GLOBAL,
    val enabled: Boolean = true, val activityIdentity: String? = null, val packageRevision: Long = 0,
    val trust: String = "UNTRUSTED_CAPABILITY_METADATA")
data class ActiveAppSemanticContext(val packageName: String, val activityIdentity: String?,
    val appFunctions: List<SemanticAppCapability>, val directActions: List<SemanticAppCapability>,
    val accessibilityInspectable: Boolean)
data class AppCapabilityRouteSet(val semanticAction: SemanticActionId, val preferred: SemanticAppCapability,
    val fallbacks: List<SemanticAppCapability>)
data class SemanticOperationReceipt(val operationId: String, val semanticAction: SemanticActionId,
    val route: AppOperationRoute, val targetPackage: String?, val dispatchTruth: DispatchTruth,
    val effectTruth: EffectTruth, val failure: SemanticOperationFailure?, val startedAt: Instant,
    val completedAt: Instant? = null, val risk: ToolRiskClass = ToolRiskClass.SENSITIVE,
    val phase: SemanticOperationPhase = if (failure == null) SemanticOperationPhase.COMPLETED else SemanticOperationPhase.FAILED) {
    fun label(): String = when {
        phase == SemanticOperationPhase.WAITING_FOR_APPROVAL -> "Needs approval"
        route == AppOperationRoute.MANUAL_TAKEOVER -> "Please complete this step. Halo can continue afterward."
        dispatchTruth == DispatchTruth.NOT_DISPATCHED -> "Action not dispatched"
        effectTruth == EffectTruth.CONFIRMED -> "Effect confirmed"
        else -> "Dispatched · effect not verified"
    }
}
data class SemanticArgumentShape(val name: String, val type: SemanticValueType, val present: Boolean, val length: Int)
fun SemanticAppOperation.argumentShapes() = arguments.entries.take(24).map { (name, value) ->
    SemanticArgumentShape(name.take(64), when (value) { is SemanticValue.Text -> SemanticValueType.STRING
        is SemanticValue.Integer -> SemanticValueType.INTEGER; is SemanticValue.Number -> SemanticValueType.NUMBER
        is SemanticValue.Boolean -> SemanticValueType.BOOLEAN }, true, if (value is SemanticValue.Text) value.value.length else 1)
}
object AppOperationDeadlines { const val DISCOVERY_MS = 3_000L; const val STATE_MS = 3_000L
    const val EXECUTION_MS = 15_000L; const val APP_SETTLE_MS = 5_000L; const val DIRECT_ACTION_MS = 5_000L }
