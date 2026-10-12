package com.halo.runtime.cognition.ui

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.isTerminal
import com.halo.sdk.runtime.*
import java.security.MessageDigest

enum class UiActionProtocolVersion { V1 }
enum class UiOperation {
    ACTIVATE, SET_TEXT, SET_CHECKED, SCROLL, LONG_PRESS, BACK, HOME, RECENTS, NOTIFICATIONS,
    QUICK_SETTINGS, POWER_DIALOG, TOGGLE_SPLIT_SCREEN, LOCK_SCREEN, TAKE_SCREENSHOT,
    HEADSET_HOOK, ACCESSIBILITY_BUTTON, ACCESSIBILITY_BUTTON_CHOOSER, ACCESSIBILITY_SHORTCUT,
    ALL_APPS, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER,
    MENU, DISMISS_NOTIFICATION_SHADE, MEDIA_PLAY_PAUSE;
    val isGlobal: Boolean get() = ordinal >= BACK.ordinal
}
enum class UiExpectedEffect { NONE, NAVIGATION, STATE_CHANGE, DISMISS }
enum class UiConsequenceHint { NORMAL, COMMUNICATION, DESTRUCTIVE, PURCHASE_LIKE, PERMISSION_LIKE }
enum class UiFailure { ACCESSIBILITY_DISABLED, NO_ACTIVE_WINDOW, PACKAGE_BLOCKED, SYSTEM_PERMISSION_SURFACE,
    SNAPSHOT_NOT_FOUND, SNAPSHOT_EXPIRED, SNAPSHOT_SUPERSEDED, TOKEN_NOT_FOUND, TARGET_DISABLED,
    TARGET_SENSITIVE, ACTION_NOT_SUPPORTED, AMBIGUOUS_TARGET, OBSERVATION_REQUIRED, USER_TAKEOVER_REQUIRED, ACTION_BUDGET_EXHAUSTED }
enum class UiVerification { CONFIRMED, NOT_CONFIRMED, UNOBSERVABLE, TARGET_LOST, SUPERSEDED }
enum class UiDispatchStatus { NOT_DISPATCHED, DISPATCHED, ALREADY_SATISFIED, UNKNOWN }
enum class UiSettleStatus { SETTLED, SETTLE_TIMEOUT, UNOBSERVABLE }
enum class UiWorkflowPhase { OBSERVING, READY, WAITING_FOR_APPROVAL, ACTING, SETTLING, VERIFYING,
    WAITING_FOR_MODEL, COMPLETED, FAILED }

data class ResolvedUiTarget(val snapshotId: String, val uiRevision: Long, val packageName: String?,
    val token: String, val semanticType: UiSemanticType, val label: String?, val region: UiScreenRegion?,
    val enabled: Boolean, val clickable: Boolean, val editable: Boolean, val scrollable: Boolean,
    val checkable: Boolean, val checked: Boolean?, val sensitive: Boolean, val stableFingerprint: String, val longClickable: Boolean = false)
sealed interface UiTargetOutcome {
    data class Resolved(val target: ResolvedUiTarget) : UiTargetOutcome
    data class Rejected(val failure: UiFailure) : UiTargetOutcome
}
sealed interface UiActionIntent {
    val snapshotId: String
    val uiRevision: Long
    val target: ResolvedUiTarget?
    val operation: UiOperation
    val argumentDigest: String
    data class Activate(override val target: ResolvedUiTarget, val expectedEffect: UiExpectedEffect = UiExpectedEffect.NONE, val longPress: Boolean = false) : UiActionIntent {
        override val snapshotId = target.snapshotId; override val uiRevision = target.uiRevision
        override val operation = if (longPress) UiOperation.LONG_PRESS else UiOperation.ACTIVATE; override val argumentDigest = uiDigest("${expectedEffect.name}:$longPress")
    }
    data class SetText(override val target: ResolvedUiTarget, val textLength: Int, val textDigest: String) : UiActionIntent {
        override val snapshotId = target.snapshotId; override val uiRevision = target.uiRevision
        override val operation = UiOperation.SET_TEXT; override val argumentDigest = textDigest
    }
    data class SetChecked(override val target: ResolvedUiTarget, val checked: Boolean) : UiActionIntent {
        override val snapshotId = target.snapshotId; override val uiRevision = target.uiRevision
        override val operation = UiOperation.SET_CHECKED; override val argumentDigest = uiDigest(checked.toString())
    }
    data class Scroll(override val target: ResolvedUiTarget, val forward: Boolean) : UiActionIntent {
        override val snapshotId = target.snapshotId; override val uiRevision = target.uiRevision
        override val operation = UiOperation.SCROLL; override val argumentDigest = uiDigest(forward.toString())
    }
    data class Global(override val snapshotId: String, override val uiRevision: Long,
        override val operation: UiOperation) : UiActionIntent {
        override val target: ResolvedUiTarget? = null; override val argumentDigest = uiDigest(operation.name)
    }
}
data class UiTargetPreview(val appLabel: String, val packageName: String?, val action: String,
    val targetLabel: String?, val role: String?, val region: String?, val textLength: Int? = null,
    val consequence: UiConsequenceHint = UiConsequenceHint.NORMAL) {
    val description: String get() = listOfNotNull(appLabel, action, targetLabel, role, region,
        textLength?.let { "$it characters" }).joinToString(" · ")
    val forceConfirmation: Boolean get() = consequence != UiConsequenceHint.NORMAL
}
data class UiSettlePolicy(val quietWindowMs: Long = 180, val maxWaitMs: Long = 1500,
    val minimumObservationDelayMs: Long = 180) {
    init { require(quietWindowMs in 50..500 && maxWaitMs in 200..2500 && minimumObservationDelayMs in 0..500) }
    companion object {
        fun forOperation(operation: UiOperation) = if (operation in setOf(UiOperation.ACTIVATE, UiOperation.LONG_PRESS) || operation.isGlobal)
            UiSettlePolicy(250, 2000, 250) else UiSettlePolicy()
    }
}
data class UiChangeSummary(val packageChanged: Boolean = false, val rootChanged: Boolean = false,
    val dialogAppeared: Boolean = false, val dialogDisappeared: Boolean = false, val targetStillPresent: Boolean = false,
    val targetStateChanged: Boolean = false, val editableValueMatched: Boolean = false,
    val scrollContentChanged: Boolean = false, val nodeCountDelta: Int = 0, val inspectabilityChanged: Boolean = false)
data class UiActionReceipt(val actionId: String, val operation: UiOperation, val targetFingerprint: String?,
    val dispatchStatus: UiDispatchStatus, val verificationStatus: UiVerification, val actionTruth: ActionTruth,
    val beforeSnapshotId: String, val afterSnapshotId: String?, val changeKind: String,
    val failureCode: UiFailure? = null, val settleMs: Long = 0, val timestamp: Long = System.currentTimeMillis(),
    val protocol: UiActionProtocolVersion = UiActionProtocolVersion.V1, val changes: UiChangeSummary = UiChangeSummary(),
    val requestedChecked: Boolean? = null, val observedChecked: Boolean? = null,
    val settleStatus: UiSettleStatus = UiSettleStatus.UNOBSERVABLE,
    val sourcePackage: String? = null, val destinationPackage: String? = null)

fun uiDigest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and 255) }
fun ScreenContextNode.safeTargetLabel(): String? = if (sensitive) null else
    (if (editable) labelHint ?: viewId?.substringAfterLast('/') ?: "text field" else text ?: contentDescription ?: viewId?.substringAfterLast('/'))
        ?.replace(Regex("\\s+"), " ")?.trim()?.take(100)
fun semanticIdentity(packageName: String?, node: ScreenContextNode): String = uiDigest(listOf(packageName.orEmpty(),
    node.viewId.orEmpty(), node.className.orEmpty(), node.semanticType.name, node.safeTargetLabel().orEmpty(), node.region?.name.orEmpty()).joinToString("|"))

data class UiSearchResult(val candidates: List<ScreenContextNode>, val ambiguous: Boolean, val scrollAvailable: Boolean)
object UiTargetSearch {
    fun find(snapshot: ScreenContextSnapshot, query: String, role: UiSemanticType? = null, region: UiScreenRegion? = null,
        interactiveOnly: Boolean = false, editableOnly: Boolean = false): UiSearchResult {
        val key = query.trim().lowercase().take(200)
        fun score(node: ScreenContextNode): Int {
            val text = node.text?.lowercase().takeUnless { node.editable }.orEmpty()
            val hint = node.contentDescription?.lowercase().orEmpty()
            val fieldHint = node.labelHint?.lowercase().orEmpty()
            val id = node.viewId?.substringAfterLast('/')?.lowercase().orEmpty()
            return when {
                text == key -> 6; fieldHint == key -> 6; hint == key -> 5; id == key -> 4
                listOf(text, fieldHint, hint, id).any { it.isNotBlank() && it.startsWith(key) } -> 3
                listOf(text, fieldHint, hint, id).any { it.isNotBlank() && it.contains(key) } -> 2
                node.semanticType.name.equals(key, true) -> 1; else -> 0
            }
        }
        val ranked = snapshot.nodes.withIndex().filter { (_, n) -> !n.sensitive && n.enabled &&
            (role == null || n.semanticType == role) && (region == null || n.region == region) &&
            (!interactiveOnly || n.clickable || n.longClickable || n.editable || n.checkable || n.scrollable) && (!editableOnly || n.editable) }
            .map { it to score(it.value) }.filter { it.second > 0 && key.isNotBlank() }
            .sortedWith(compareByDescending<Pair<IndexedValue<ScreenContextNode>, Int>> { it.second }
                .thenBy { it.first.value.region?.ordinal ?: Int.MAX_VALUE }.thenBy { it.first.value.depth }.thenBy { it.first.index })
        return UiSearchResult(ranked.take(20).map { it.first.value }, ranked.size > 1 && ranked[0].second == ranked[1].second,
            snapshot.nodes.any { it.scrollable && it.enabled && !it.sensitive })
    }
}

/** Typed matching evidence; arbitrary changed text never verifies an action. */
class UiPostconditionVerifier {
    fun verify(actionId: String, intent: UiActionIntent, before: ScreenContextSnapshot, after: ScreenContextSnapshot?,
        dispatch: UiDispatchStatus, settle: UiSettleStatus, settleMs: Long, intendedText: String? = null,
        launcherPackage: String? = null, blockedDestination: Boolean = false): UiActionReceipt {
        val fingerprint = intent.target?.stableFingerprint
        val matches = after?.nodes?.filter { it.stableFingerprint == fingerprint && !it.sensitive }.orEmpty()
        val old = before.nodes.singleOrNull { it.stableFingerprint == fingerprint }
        val target = matches.singleOrNull()
        val oldDialogs = before.nodes.filter { it.semanticType == UiSemanticType.DIALOG }.map { it.stableFingerprint }.toSet()
        val newDialogs = after?.nodes?.filter { it.semanticType == UiSemanticType.DIALOG }?.map { it.stableFingerprint }?.toSet().orEmpty()
        val rowKeysBefore = old?.visibleChildKeys.orEmpty(); val rowKeysAfter = target?.visibleChildKeys.orEmpty()
        val sharedKey = rowKeysBefore.firstOrNull { it in rowKeysAfter }
        val movement = sharedKey?.let { rowKeysBefore.indexOf(it) - rowKeysAfter.indexOf(it) } ?: 0
        val beforeRows = old?.visibleRowIndices.orEmpty(); val afterRows = target?.visibleRowIndices.orEmpty()
        val indexedMovement = beforeRows.isNotEmpty() && afterRows.isNotEmpty() && intent is UiActionIntent.Scroll &&
            (if (intent.forward) afterRows.first() > beforeRows.first() && afterRows.last() >= beforeRows.last()
                else afterRows.first() < beforeRows.first() && afterRows.last() <= beforeRows.last())
        val scrolled = intent is UiActionIntent.Scroll && rowKeysBefore != rowKeysAfter && rowKeysBefore.size > 1 &&
            rowKeysAfter.size > 1 && (indexedMovement || if (intent.forward) movement > 0 else movement < 0)
        fun normalized(text: String) = text.replace(Regex("\\s+"), " ").trim()
        val actualText = target?.text
        val textMatches = intent is UiActionIntent.SetText && target?.editable == true && target.valueFullyExposed &&
            actualText != null && intendedText != null && normalized(actualText) == normalized(intendedText)
        val change = UiChangeSummary(after != null && before.packageName != after.packageName,
            after != null && before.navigationFingerprint != null && after.navigationFingerprint != before.navigationFingerprint,
            (newDialogs - oldDialogs).isNotEmpty(), (oldDialogs - newDialogs).isNotEmpty(), target != null,
            target != null && old != null && (target.checked != old.checked || target.selected != old.selected || target.focused != old.focused),
            textMatches, scrolled, (after?.nodes?.size ?: before.nodes.size) - before.nodes.size,
            after != null && before.inspectability != after.inspectability)
        val disappeared = after != null && !after.truncated && matches.isEmpty() && fingerprint != null &&
            after.inspectability == UiInspectability.INSPECTABLE
        val confirmed = after != null && !blockedDestination && (settle == UiSettleStatus.SETTLED && when (intent) {
            is UiActionIntent.SetText -> textMatches
            is UiActionIntent.SetChecked -> target?.checkable == true && target.checked == intent.checked
            is UiActionIntent.Scroll -> scrolled
            is UiActionIntent.Activate -> when (intent.expectedEffect) {
                UiExpectedEffect.NAVIGATION -> change.packageChanged || change.rootChanged || change.dialogAppeared ||
                    (old?.semanticType == UiSemanticType.TAB && change.targetStateChanged)
                UiExpectedEffect.STATE_CHANGE -> change.targetStateChanged
                UiExpectedEffect.DISMISS -> disappeared || change.dialogDisappeared
                UiExpectedEffect.NONE -> change.packageChanged || change.rootChanged || change.dialogAppeared || change.targetStateChanged || disappeared
            }
            is UiActionIntent.Global -> when (intent.operation) {
                UiOperation.HOME -> launcherPackage != null && after.packageName == launcherPackage
                UiOperation.BACK -> change.packageChanged || change.rootChanged || change.dialogDisappeared
                // A changed screen alone cannot prove which system action took effect.
                else -> false
            }
        })
        val verification = when {
            dispatch == UiDispatchStatus.NOT_DISPATCHED -> UiVerification.NOT_CONFIRMED
            blockedDestination || after == null -> UiVerification.UNOBSERVABLE
            confirmed -> UiVerification.CONFIRMED
            disappeared -> UiVerification.TARGET_LOST
            else -> UiVerification.NOT_CONFIRMED
        }
        val truth = when {
            dispatch == UiDispatchStatus.NOT_DISPATCHED -> ActionTruth.FAILED
            blockedDestination -> ActionTruth.DISPATCHED
            confirmed -> ActionTruth.CONFIRMED
            after == null || dispatch == UiDispatchStatus.UNKNOWN -> ActionTruth.UNKNOWN
            else -> ActionTruth.DISPATCHED
        }
        val kind = when {
            dispatch == UiDispatchStatus.ALREADY_SATISFIED && confirmed -> "already_in_requested_state"
            blockedDestination -> "blocked_destination"
            textMatches -> "editable_value_matched"
            intent is UiActionIntent.SetChecked && confirmed -> "checked_state_matched"
            scrolled -> "scroll_rows_moved"
            change.packageChanged -> "package_changed"
            change.rootChanged -> "navigation_changed"
            change.dialogAppeared -> "dialog_opened"
            change.dialogDisappeared -> "dialog_dismissed"
            change.targetStateChanged -> "target_state_changed"
            disappeared -> "target_disappeared"
            settle == UiSettleStatus.SETTLE_TIMEOUT -> "settle_timeout"
            else -> "effect_unconfirmed"
        }
        return UiActionReceipt(actionId, intent.operation, fingerprint, dispatch, verification, truth, before.snapshotId,
            after?.snapshotId, kind, if (blockedDestination) UiFailure.PACKAGE_BLOCKED else
                if (dispatch == UiDispatchStatus.NOT_DISPATCHED) UiFailure.ACTION_NOT_SUPPORTED else null, settleMs = settleMs,
            changes = change, requestedChecked = (intent as? UiActionIntent.SetChecked)?.checked, observedChecked = target?.checked,
            settleStatus = settle, sourcePackage = before.packageName, destinationPackage = after?.packageName)
    }
}

enum class UiWaitCondition { PACKAGE_CHANGED, TARGET_APPEARED, TARGET_DISAPPEARED, SCREEN_CHANGED }
enum class UiSearchStatus { FOUND, NOT_FOUND, AMBIGUOUS }
data class UiWorkflowState(val runId: String, val interactionId: String, val currentPackage: String?,
    val lastSnapshotId: String?, val lastUiRevision: Long?, val actionCount: Int, val scrollCount: Int,
    val lastReceipt: UiActionReceipt?, val state: UiWorkflowPhase, val preview: UiTargetPreview?)
data class UiOperatorMetrics(val screenReadCount: Long = 0, val actionDispatchCount: Long = 0, val confirmedCount: Long = 0,
    val dispatchedUnconfirmedCount: Long = 0, val unknownCount: Long = 0, val staleSnapshotCount: Long = 0,
    val ambiguousTargetCount: Long = 0, val blockedPackageCount: Long = 0, val settleTimeoutCount: Long = 0)
object UiOperatorDiagnostics {
    val accessibilityConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val metrics = kotlinx.coroutines.flow.MutableStateFlow(UiOperatorMetrics())
    private var sink: RuntimeMetricsSink = NoopRuntimeMetrics
    private fun counters(value: UiOperatorMetrics) = mapOf("screenReadCount" to value.screenReadCount,
        "actionDispatchCount" to value.actionDispatchCount, "confirmedCount" to value.confirmedCount,
        "dispatchedUnconfirmedCount" to value.dispatchedUnconfirmedCount, "unknownCount" to value.unknownCount,
        "staleSnapshotCount" to value.staleSnapshotCount, "ambiguousTargetCount" to value.ambiguousTargetCount,
        "blockedPackageCount" to value.blockedPackageCount, "settleTimeoutCount" to value.settleTimeoutCount)
    @Synchronized fun attachMetricsSink(value: RuntimeMetricsSink) {
        if (sink === value) return
        sink = value
        counters(metrics.value).forEach { (key, count) -> if (count > 0) sink.increment("ui.$key", count) }
    }
    @Synchronized fun update(action: (UiOperatorMetrics) -> UiOperatorMetrics) {
        val before = counters(metrics.value)
        val next = action(metrics.value)
        counters(next).forEach { (key, count) -> val delta = count - before.getValue(key); if (delta > 0) sink.increment("ui.$key", delta) }
        metrics.value = next
    }
    val lastReceipt = kotlinx.coroutines.flow.MutableStateFlow<UiActionReceipt?>(null)
}
data class UiObservation(val snapshotId: String, val revision: Long, val packageName: String?,
    val clickable: Boolean, val editable: Boolean, val checkable: Boolean, val scrollable: Boolean)
fun ScreenContextSnapshot.observation() = UiObservation(snapshotId, uiRevision, packageName,
    nodes.any { (it.clickable || it.longClickable) && it.enabled && !it.sensitive }, nodes.any { it.editable && it.enabled && !it.sensitive },
    nodes.any { it.checkable && it.enabled && !it.sensitive }, nodes.any { it.scrollable && it.enabled && !it.sensitive })
class UiTransactionScope(val phase: suspend (UiWorkflowPhase) -> Unit,
    val nested: suspend (UiActionIntent, String, UiWorkflowPhase, UiActionReceipt?, UiObservation?) -> UiFailure? = { _, _, _, _, _ -> UiFailure.ACTION_NOT_SUPPORTED }
) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<UiTransactionScope>
}
data class UiTransactionRecord(val toolCallId: String, val phase: UiWorkflowPhase,
    val observation: UiObservation? = null, val preview: UiTargetPreview? = null,
    val operation: UiOperation? = null, val targetFingerprint: String? = null,
    val argumentDigest: String? = null, val textLength: Int? = null, val requestedChecked: Boolean? = null,
    val receipt: UiActionReceipt? = null, val protocol: UiActionProtocolVersion = UiActionProtocolVersion.V1,
    val searchStatus: UiSearchStatus? = null)
data class UiRecoveryObservation(val snapshot: ScreenContextSnapshot, val receipt: UiActionReceipt?)
fun ScreenContextSnapshot.compactUiText(): String = "UNTRUSTED_SCREEN_CONTENT protocol=V1 snapshotId=$snapshotId uiRevision=$uiRevision package=$packageName\n" +
    nodes.take(80).filter { !it.sensitive }.joinToString("\n") {
        "token=${it.token} role=${it.semanticType} label=${it.safeTargetLabel().orEmpty()} clickable=${it.clickable} longClickable=${it.longClickable} editable=${it.editable} checkable=${it.checkable} checked=${it.checked} scrollable=${it.scrollable}"
    }.take(6000) + "\nnext=USE_FRESH_SNAPSHOT"
object UiWorkflowProjector {
    fun from(run: com.halo.runtime.cognition.agent.AgentRun): UiWorkflowState? {
        val records = run.steps.filterIsInstance<com.halo.runtime.cognition.agent.AgentStep.UiTransactionRecorded>().map { it.transaction }
        val semantic = run.steps.filterIsInstance<com.halo.runtime.cognition.agent.AgentStep.SemanticAppOperationRecorded>().lastOrNull()?.receipt
        if (records.isEmpty() && semantic != null) return UiWorkflowState(run.id.value, run.interactionId.value, semantic.targetPackage,
            null, null, 1, 0, null, when (semantic.phase) {
                com.halo.runtime.cognition.apps.SemanticOperationPhase.WAITING_FOR_APPROVAL -> UiWorkflowPhase.WAITING_FOR_APPROVAL
                com.halo.runtime.cognition.apps.SemanticOperationPhase.DISPATCHING -> UiWorkflowPhase.ACTING
                com.halo.runtime.cognition.apps.SemanticOperationPhase.WAITING_FOR_APP -> UiWorkflowPhase.SETTLING
                com.halo.runtime.cognition.apps.SemanticOperationPhase.VERIFYING -> UiWorkflowPhase.VERIFYING
                com.halo.runtime.cognition.apps.SemanticOperationPhase.COMPLETED -> UiWorkflowPhase.COMPLETED
                com.halo.runtime.cognition.apps.SemanticOperationPhase.FAILED -> UiWorkflowPhase.FAILED
                else -> UiWorkflowPhase.READY
            }, null)
        val latest = records.lastOrNull() ?: return null
        val observation = records.mapNotNull { it.observation }.lastOrNull()
        val phase = if (run.state.isTerminal) { if (run.state == com.halo.runtime.cognition.agent.AgentRunState.COMPLETED)
            UiWorkflowPhase.COMPLETED else UiWorkflowPhase.FAILED } else latest.phase
        val actions = records.filter { it.phase == UiWorkflowPhase.ACTING }.distinctBy { it.toolCallId }
        return UiWorkflowState(run.id.value, run.interactionId.value, observation?.packageName, observation?.snapshotId,
            observation?.revision, actions.size, actions.count { it.operation == UiOperation.SCROLL },
            records.mapNotNull { it.receipt }.lastOrNull(), phase, records.mapNotNull { it.preview }.lastOrNull())
    }
    fun summary(run: com.halo.runtime.cognition.agent.AgentRun): String? {
        val transactions = run.steps.filterIsInstance<com.halo.runtime.cognition.agent.AgentStep.UiTransactionRecorded>().map { it.transaction }
        val records = transactions.filter { it.operation != null }.groupBy { it.toolCallId }.values.map { history ->
            history.last().copy(receipt = history.mapNotNull { it.receipt }.lastOrNull(), preview = history.mapNotNull { it.preview }.lastOrNull())
        }.filter { record -> record.searchStatus != null || transactions.none { it.toolCallId.startsWith("${record.toolCallId}:scroll:") } }.takeLast(6)
        if (records.isEmpty()) return run.steps.filterIsInstance<com.halo.runtime.cognition.agent.AgentStep.SemanticAppOperationRecorded>()
            .lastOrNull()?.receipt?.let { "${it.semanticAction.value.replace('_', ' ')} · ${it.label()}" }
        val actions = records.joinToString("\n") { record ->
            val description = record.preview?.let { "${it.action} ${it.targetLabel.orEmpty()} · ${it.appLabel}" } ?: "Screen action"
            "$description · " + (record.searchStatus?.let { when (it) {
                UiSearchStatus.FOUND -> "Target found"
                UiSearchStatus.NOT_FOUND -> "Target not found within the limit"
                UiSearchStatus.AMBIGUOUS -> "Refine the target"
            } } ?: when (record.receipt?.actionTruth) {
                ActionTruth.CONFIRMED -> "Verified"
                ActionTruth.FAILED -> "Failed"
                null -> "Not completed"
                else -> "Effect could not be confirmed"
            })
        }
        val incomplete = transactions.filter { it.operation != null }.map { it.toolCallId }.distinct().any { id ->
            transactions.none { it.toolCallId == id && (it.receipt != null || it.searchStatus != null) } && transactions.none { it.toolCallId.startsWith("$id:scroll:") && it.receipt != null } }
        val interrupted = run.steps.filterIsInstance<com.halo.runtime.cognition.agent.AgentStep.ToolOutcomeRecorded>().any {
            it.toolId.startsWith("halo_ui_") && it.actionTruth in setOf(ActionTruth.UNKNOWN, ActionTruth.FAILED) }
        return actions + if (incomplete || interrupted || run.steps.any { it is com.halo.runtime.cognition.agent.AgentStep.RunGuardTriggered })
            "\nSome requested steps were not completed." else ""
    }
}
