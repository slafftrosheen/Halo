package com.halo.runtime.cognition.apps

import com.halo.runtime.cognition.tools.ToolRiskClass
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PreparedSemanticAppOperation(val operation: SemanticAppOperation, val generationId: String,
    val candidates: List<SemanticAppCapability>, val risk: ToolRiskClass, val preparedId: String = UUID.randomUUID().toString())
sealed interface SemanticOperationResolution {
    data class Prepared(val value: PreparedSemanticAppOperation) : SemanticOperationResolution
    data class NeedsUser(val failure: SemanticOperationFailure) : SemanticOperationResolution
}
data class SemanticRouteOutcome(val dispatch: DispatchTruth, val effect: EffectTruth,
    val failure: SemanticOperationFailure? = null)
fun interface SemanticRouteExecutor {
    /** Mark immediately before entering any API that may dispatch. Cancellation thereafter is UNKNOWN. */
    suspend fun execute(operation: SemanticAppOperation, capability: SemanticAppCapability, markDispatched: () -> Unit): SemanticRouteOutcome
}

class SemanticAppActionBroker(private val index: AppFunctionCapabilityIndex,
    private val nativeCapabilities: (SemanticAppOperation) -> List<SemanticAppCapability> = { emptyList() },
    private val intentCapabilities: (SemanticAppOperation) -> List<SemanticAppCapability> = { emptyList() },
    private val executors: Map<AppOperationRoute, SemanticRouteExecutor>,
    private val authorize: suspend (PreparedSemanticAppOperation) -> Boolean,
    private val record: suspend (SemanticOperationReceipt, List<SemanticArgumentShape>) -> Unit = { _, _ -> },
    private val now: () -> Instant = Instant::now) {
    private val mutex = Mutex()
    private val consumed = LinkedHashSet<String>()
    fun resolve(operation: SemanticAppOperation): SemanticOperationResolution {
        val snapshot = index.state.value
        val pkg = when (val target = operation.target) { AppTarget.CurrentApp -> snapshot.currentPackage
            is AppTarget.Package -> target.name; is AppTarget.Application -> null }
        println("[HALO_APP_OPERATION_RESOLVED] interactionId=${operation.interactionId} action=${operation.action.value} package=${pkg ?: "none"}")
        val candidates = (nativeCapabilities(operation) + snapshot.capabilities + intentCapabilities(operation))
            .filter { it.action == operation.action && it.enabled && (pkg == null && operation.target !is AppTarget.CurrentApp || it.packageName == pkg) &&
                (it.scope == CapabilityScope.GLOBAL || it.activityIdentity != null && it.activityIdentity == snapshot.activityIdentity) }
            .filter { it.route != AppOperationRoute.ACCESSIBILITY || !ManualTakeoverPolicy.blocked(pkg, operation.action.value) }
            .sortedBy { it.route.ordinal }.distinctBy { it.route to it.id }.take(16)
        if (candidates.isEmpty()) return SemanticOperationResolution.NeedsUser(SemanticOperationFailure.MANUAL_TAKEOVER)
        if (!validArguments(operation, candidates.first())) return SemanticOperationResolution.NeedsUser(SemanticOperationFailure.INVALID_ARGUMENTS)
        return SemanticOperationResolution.Prepared(PreparedSemanticAppOperation(operation, snapshot.generationId, candidates,
            riskFor(operation.action.value)))
    }
    suspend fun executePrepared(prepared: PreparedSemanticAppOperation): SemanticOperationReceipt = mutex.withLock {
        val op = prepared.operation
        val start = now()
        suspend fun receipt(capability: SemanticAppCapability, result: SemanticRouteOutcome, phase: SemanticOperationPhase? = null): SemanticOperationReceipt {
            val value = SemanticOperationReceipt(op.operationId, op.action, capability.route, capability.packageName,
                result.dispatch, result.effect, result.failure, start, now(), prepared.risk,
                phase ?: if (result.failure == null) SemanticOperationPhase.COMPLETED else SemanticOperationPhase.FAILED)
            record(value, op.argumentShapes())
            println("[HALO_APP_OPERATION_RESULT] operationId=${op.operationId} route=${value.route} dispatch=${value.dispatchTruth} effect=${value.effectTruth} failure=${value.failure}")
            return value
        }
        val first = prepared.candidates.first()
        if (prepared.generationId != index.state.value.generationId || prepared.preparedId in consumed)
            return@withLock receipt(first, SemanticRouteOutcome(DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.STALE_GENERATION))
        if (!authorize(prepared)) return@withLock receipt(first, SemanticRouteOutcome(DispatchTruth.NOT_DISPATCHED,
            EffectTruth.UNKNOWN, SemanticOperationFailure.NEEDS_APPROVAL), SemanticOperationPhase.WAITING_FOR_APPROVAL)
        consumed.add(prepared.preparedId)
        while (consumed.size > 256) consumed.remove(consumed.first())
        for ((position, capability) in prepared.candidates.withIndex()) {
            if (prepared.generationId != index.state.value.generationId)
                return@withLock receipt(capability, SemanticRouteOutcome(DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.STALE_GENERATION))
            if (!validArguments(op, capability)) continue
            val executor = executors[capability.route] ?: continue
            var dispatched = false
            val outcome = try { withTimeout(AppOperationDeadlines.EXECUTION_MS) { executor.execute(op, capability) {
                dispatched = true
                println("[HALO_APP_OPERATION_DISPATCH] operationId=${op.operationId} route=${capability.route}")
            } } } catch (_: TimeoutCancellationException) {
                SemanticRouteOutcome(if (dispatched) DispatchTruth.DISPATCHED else DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.TIMEOUT)
            } catch (cancelled: CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { receipt(capability, SemanticRouteOutcome(
                    if (dispatched) DispatchTruth.DISPATCHED else DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.CANCELLED)) }
                throw cancelled
            } catch (_: Exception) {
                SemanticRouteOutcome(if (dispatched) DispatchTruth.DISPATCHED else DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.PLATFORM_ERROR)
            }
            // Neither a timeout nor an executor's optimistic error can clear a dispatch claim.
            val truth = if (dispatched && outcome.dispatch == DispatchTruth.NOT_DISPATCHED)
                outcome.copy(dispatch = DispatchTruth.DISPATCHED, effect = EffectTruth.UNKNOWN) else outcome
            if (truth.dispatch == DispatchTruth.DISPATCHED || truth.failure == null || truth.failure !in PRE_DISPATCH_REJECTIONS)
                return@withLock receipt(capability, truth)
            if (position < prepared.candidates.lastIndex)
                println("[HALO_APP_OPERATION_FALLBACK] operationId=${op.operationId} from=${capability.route} reason=${truth.failure}")
        }
        receipt(first, SemanticRouteOutcome(DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.FEATURE_UNAVAILABLE))
    }
    companion object {
        val PRE_DISPATCH_REJECTIONS = setOf(SemanticOperationFailure.FEATURE_UNAVAILABLE, SemanticOperationFailure.DISABLED,
            SemanticOperationFailure.NOT_FOUND, SemanticOperationFailure.PACKAGE_INVISIBLE, SemanticOperationFailure.CALLER_NOT_AUTHORIZED,
            SemanticOperationFailure.TARGET_PERMISSION_REQUIRED, SemanticOperationFailure.INVALID_ARGUMENTS, SemanticOperationFailure.TARGET_REJECTED)
        fun riskFor(action: String): ToolRiskClass = when (action.substringBefore('_')) {
            "get", "list", "read", "search", "inspect" -> ToolRiskClass.READ_ONLY
            "open", "draft", "prepare", "volume", "brightness", "ringer", "media" -> ToolRiskClass.REVERSIBLE
            "create", "update", "send", "reply", "archive", "mark" -> ToolRiskClass.EXTERNAL_SIDE_EFFECT
            "delete", "remove", "purchase", "pay", "transfer" -> ToolRiskClass.DESTRUCTIVE
            else -> ToolRiskClass.SENSITIVE
        }
        fun validArguments(operation: SemanticAppOperation, capability: SemanticAppCapability): Boolean =
            operation.arguments.size <= 24 && operation.arguments.keys.all { k -> capability.parameters.any { it.name == k } } &&
                capability.parameters.all { p -> val v = operation.arguments[p.name]
                    if (v == null) !p.required else when (p.type) { SemanticValueType.STRING -> v is SemanticValue.Text && v.value.length <= 4096 && !v.value.startsWith("file://", ignoreCase = true)
                        SemanticValueType.INTEGER -> v is SemanticValue.Integer; SemanticValueType.NUMBER -> v is SemanticValue.Number && v.value.isFinite()
                        SemanticValueType.BOOLEAN -> v is SemanticValue.Boolean } }
    }
}
object ManualTakeoverPolicy {
    fun blocked(packageName: String?, action: String): Boolean = packageName?.lowercase()?.let { p ->
        listOf("bank", "wallet", "password", "authenticator", "permissioncontroller", "keychain").any { it in p }
    } == true || listOf("captcha", "password", "payment", "permission", "recovery").any { it in action.lowercase() }
}
