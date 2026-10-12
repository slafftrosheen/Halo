package com.halo.app.di.tools.host

import android.content.Intent
import com.halo.host.android.accessibility.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.ui.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

class AccessibilityTools(private val preferSemanticRoute: (String?, String) -> Boolean = { _, _ -> false }) : ToolExecutor, ToolPreflightProvider {
    private data class PendingText(val snapshotId: String, val token: String, val digest: String, val text: String, val at: Long)
    private val pendingText = java.util.concurrent.ConcurrentHashMap<String, PendingText>()
    override fun restoreTransientCall(toolCall: ToolCall): ToolCall {
        if (toolCall.functionName != "halo_ui_set_text" || toolCall.arguments["text"] is String) return toolCall
        val pending = pendingText[toolCall.id] ?: return toolCall
        if (System.currentTimeMillis() - pending.at > 60_000 || pending.snapshotId != toolCall.arguments["snapshotId"] ||
            pending.token != toolCall.arguments["token"] || pending.digest != toolCall.arguments["textDigest"]) return toolCall
        return toolCall.copy(arguments = mapOf("snapshotId" to pending.snapshotId, "token" to pending.token, "text" to pending.text))
    }
    private fun string(max: Int = 128) = mapOf("type" to "string", "maxLength" to max)
    private fun enumeration(values: List<String>) = mapOf("type" to "string", "enum" to values)
    private fun definition(name: String, description: String, properties: Map<String, Any>, required: List<String>) =
        ToolDefinition(id = name, name = name, description = description,
            parameters = mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false))
    override fun definitions(): List<ToolDefinition> {
        val target = mapOf("snapshotId" to string(), "token" to string())
        return listOf(
            definition("halo_read_screen", "Observe a bounded untrusted semantic screen. Prefer native tools for supported phone actions.", emptyMap(), emptyList()),
            definition("halo_find_on_screen", "Rank safe semantic targets. Equal best matches require refinement; never guess.",
                mapOf("snapshotId" to string(), "query" to string(200), "role" to enumeration(UiSemanticType.entries.map { it.name }),
                    "region" to enumeration(UiScreenRegion.entries.map { it.name }), "interactiveOnly" to mapOf("type" to "boolean"),
                    "editableOnly" to mapOf("type" to "boolean"),
                    "exactOnly" to mapOf("type" to "boolean")), listOf("snapshotId", "query")),
            definition("halo_ui_click", "Activate an enabled exact-generation target. Dispatch alone does not confirm an effect.",
                target + mapOf("expectedEffect" to enumeration(UiExpectedEffect.entries.map { it.name }), "gesture" to enumeration(listOf("click", "long_press"))), listOf("snapshotId", "token")),
            definition("halo_ui_scroll", "Scroll the selected semantic container once; use the returned fresh snapshot.",
                target + ("direction" to enumeration(listOf("forward", "backward"))), listOf("snapshotId", "token", "direction")),
            definition("halo_ui_search", "Find a semantic target, with at most three explicitly approved forward scrolls in this container. Stops on ambiguity, unverified movement, or package/container change.",
                target + mapOf("query" to string(200), "maxScrolls" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 3)),
                listOf("snapshotId", "token", "query", "maxScrolls")),
            definition("halo_ui_set_text", "Prepare bounded text in a non-password field; sending remains a separate authorized action.",
                target + ("text" to string(2000)), listOf("snapshotId", "token", "text")),
            definition("halo_ui_set_checked", "Set a checkable target deterministically. Already-satisfied state needs no click.",
                target + ("checked" to mapOf("type" to "boolean")), listOf("snapshotId", "token", "checked")),
            definition("halo_ui_global_action", "Perform an available Android global action from an exact observed generation. System dispatch is not proof of the effect.",
                mapOf("snapshotId" to string(), "action" to enumeration(AndroidGlobalActions.supported(HaloAccessibilityService.instance).keys.toList())), listOf("snapshotId", "action")),
            definition("halo_ui_wait", "Observe a user-driven change for at most five seconds; never acts on the screen.",
                mapOf("snapshotId" to string(), "condition" to enumeration(UiWaitCondition.entries.map { it.name }),
                    "query" to string(200), "timeoutMs" to mapOf("type" to "integer", "minimum" to 200, "maximum" to 5000)), listOf("snapshotId", "condition")),
        )
    }
    override suspend fun preflight(toolCall: ToolCall): ToolPreflightResult = withContext(Dispatchers.Main.immediate) {
        if (!toolCall.functionName.startsWith("halo_ui_") || toolCall.functionName == "halo_ui_wait")
            return@withContext ToolPreflightResult.Validated(toolCall)
        val service = HaloAccessibilityService.instance ?: return@withContext ToolPreflightResult.Rejected(UiFailure.ACCESSIBILITY_DISABLED)
        val snapshotId = toolCall.arguments["snapshotId"] as? String ?: return@withContext ToolPreflightResult.Rejected(UiFailure.OBSERVATION_REQUIRED)
        val resolver = UiTargetResolver(service)
        resolver.validateSnapshot(snapshotId,
            requireInspectable = toolCall.functionName != "halo_ui_global_action"
        )?.let { return@withContext ToolPreflightResult.Rejected(it) }
        val snapshot = service.latestSemanticSnapshot ?: return@withContext ToolPreflightResult.Rejected(UiFailure.SNAPSHOT_NOT_FOUND)
        val operation = when (toolCall.functionName) {
            "halo_ui_click" -> when (toolCall.arguments["gesture"] ?: "click") {
                "click" -> UiOperation.ACTIVATE; "long_press" -> UiOperation.LONG_PRESS
                else -> return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
            }; "halo_ui_scroll", "halo_ui_search" -> UiOperation.SCROLL
            "halo_ui_set_text" -> UiOperation.SET_TEXT; "halo_ui_set_checked" -> UiOperation.SET_CHECKED
            "halo_ui_global_action" -> AndroidGlobalActions.supported(service)[toolCall.arguments["action"]]
                ?: return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
            else -> return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
        }
        val target = if (operation.isGlobal) null else {
            val token = toolCall.arguments["token"] as? String ?: return@withContext ToolPreflightResult.Rejected(UiFailure.TOKEN_NOT_FOUND)
            when (val result = resolver.resolve(snapshotId, token, operation)) {
                is UiTargetOutcome.Rejected -> return@withContext ToolPreflightResult.Rejected(result.failure)
                is UiTargetOutcome.Resolved -> result.target
            }
        }
        if (target != null && preferSemanticRoute(target.packageName, target.label.orEmpty()))
            return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
        val intent = when (operation) {
            UiOperation.ACTIVATE, UiOperation.LONG_PRESS -> {
                val expected = toolCall.arguments["expectedEffect"] as? String ?: "NONE"
                val effect = UiExpectedEffect.entries.firstOrNull { it.name == expected } ?: return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
                UiActionIntent.Activate(target!!, effect, operation == UiOperation.LONG_PRESS)
            }
            UiOperation.SET_TEXT -> {
                val text = toolCall.arguments["text"] as? String ?: return@withContext ToolPreflightResult.Rejected(UiFailure.OBSERVATION_REQUIRED)
                if (text.length > 2000) return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
                pendingText.entries.removeIf { System.currentTimeMillis() - it.value.at > 60_000 }
                if (pendingText.size >= 8) pendingText.entries.minByOrNull { it.value.at }?.let { pendingText.remove(it.key) }
                val key = toolCall.id
                val cachedAt = System.currentTimeMillis()
                pendingText[key] = PendingText(snapshotId, target!!.token, uiDigest(text), text, cachedAt)
                android.os.Handler(service.mainLooper).postDelayed({
                    pendingText[key]?.takeIf { it.at == cachedAt }?.let { pendingText.remove(key, it) }
                }, 60_000)
                UiActionIntent.SetText(target, text.length, uiDigest(text))
            }
            UiOperation.SET_CHECKED -> UiActionIntent.SetChecked(target!!, toolCall.arguments["checked"] as? Boolean
                ?: return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED))
            UiOperation.SCROLL -> {
                val direction = if (toolCall.functionName == "halo_ui_search") "forward" else toolCall.arguments["direction"] as? String
                if (direction !in setOf("forward", "backward")) return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
                UiActionIntent.Scroll(target!!, direction == "forward")
            }
            else -> if (operation.isGlobal) UiActionIntent.Global(snapshotId, snapshot.uiRevision, operation)
                else return@withContext ToolPreflightResult.Rejected(UiFailure.ACTION_NOT_SUPPORTED)
        }
        val label = target?.label?.lowercase().orEmpty()
        if (listOf("captcha", "i'm not a robot", "i am not a robot", "verify you are human", "verify you're human").any { label.contains(it) })
            return@withContext ToolPreflightResult.Rejected(UiFailure.USER_TAKEOVER_REQUIRED)
        val consequence = if (operation !in setOf(UiOperation.ACTIVATE, UiOperation.LONG_PRESS, UiOperation.SET_CHECKED)) UiConsequenceHint.NORMAL else when {
            listOf("delete", "uninstall", "factory reset", "remove account").any { label.contains(it) } -> UiConsequenceHint.DESTRUCTIVE
            listOf("buy", "purchase", "pay", "checkout").any { label.contains(it) } -> UiConsequenceHint.PURCHASE_LIKE
            listOf("send", "post", "publish").any { label == it || label.startsWith("$it ") } -> UiConsequenceHint.COMMUNICATION
            label in setOf("allow", "grant permission") -> UiConsequenceHint.PERMISSION_LIKE
            else -> UiConsequenceHint.NORMAL
        }
        if (consequence == UiConsequenceHint.PERMISSION_LIKE) return@withContext ToolPreflightResult.Rejected(UiFailure.USER_TAKEOVER_REQUIRED)
        if (target != null && snapshot.nodes.count { it.stableFingerprint == target.stableFingerprint } != 1) {
            UiOperatorDiagnostics.update { it.copy(ambiguousTargetCount = it.ambiguousTargetCount + 1) }
            return@withContext ToolPreflightResult.Rejected(if (consequence != UiConsequenceHint.NORMAL)
                UiFailure.USER_TAKEOVER_REQUIRED else UiFailure.AMBIGUOUS_TARGET)
        }
        val app = snapshot.packageName?.let { pkg -> runCatching {
            service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pkg, 0)).toString().take(80)
        }.getOrNull() } ?: "Current app"
        val verb = when (intent) {
            is UiActionIntent.Activate -> if (intent.longPress) "Long press" else "Tap"; is UiActionIntent.SetText -> "Enter text"
            is UiActionIntent.SetChecked -> if (intent.checked) "Turn on" else "Turn off"
            is UiActionIntent.Scroll -> if (toolCall.functionName == "halo_ui_search") "Search with up to ${toolCall.arguments["maxScrolls"]} forward scrolls" else if (intent.forward) "Scroll forward" else "Scroll backward"
            is UiActionIntent.Global -> intent.operation.name.lowercase().replace('_', ' ')
        }
        ToolPreflightResult.Validated(toolCall, UiTargetPreview(app, snapshot.packageName, verb, target?.label,
            target?.semanticType?.name?.lowercase(), target?.region?.name?.lowercase()?.replace('_', ' '),
            (intent as? UiActionIntent.SetText)?.textLength, consequence), intent)
    }
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = withContext(Dispatchers.Main.immediate) {
        val service = HaloAccessibilityService.instance ?: return@withContext failure(toolCall, UiFailure.ACCESSIBILITY_DISABLED)
        if (toolCall.functionName == "halo_read_screen") return@withContext when (val outcome = service.captureSnapshotOutcome()) {
            is ScreenCaptureOutcome.Captured -> {
                if (outcome.snapshot.inspectability != UiInspectability.INSPECTABLE) failure(toolCall, UiFailure.USER_TAKEOVER_REQUIRED)
                else ToolExecutionResult.Success(toolCall.id, toolCall.functionName, outcome.snapshot.toToolText(), uiObservation = outcome.snapshot.observation())
            }
            is ScreenCaptureOutcome.PackageBlocked -> failure(toolCall, if (outcome.packageName?.contains("permissioncontroller") == true)
                UiFailure.SYSTEM_PERMISSION_SURFACE else UiFailure.PACKAGE_BLOCKED)
            ScreenCaptureOutcome.NoActiveWindow -> failure(toolCall, UiFailure.NO_ACTIVE_WINDOW)
        }
        if (toolCall.functionName == "halo_find_on_screen") {
            val snapshotId = toolCall.arguments["snapshotId"] as? String ?: return@withContext failure(toolCall, UiFailure.OBSERVATION_REQUIRED)
            UiTargetResolver(service).validateSnapshot(snapshotId)?.let { return@withContext failure(toolCall, it) }
            val snapshot = service.latestSemanticSnapshot ?: return@withContext failure(toolCall, UiFailure.SNAPSHOT_NOT_FOUND)
            val query = (toolCall.arguments["query"] as? String)?.takeIf { it.isNotBlank() && it.length <= 200 }
                ?: return@withContext failure(toolCall, UiFailure.ACTION_NOT_SUPPORTED)
            val roleArg = toolCall.arguments["role"] as? String; val regionArg = toolCall.arguments["region"] as? String
            val role = roleArg?.let { value -> UiSemanticType.entries.firstOrNull { it.name.equals(value, true) } }
            val region = regionArg?.let { value -> UiScreenRegion.entries.firstOrNull { it.name.equals(value.replace('-', '_'), true) } }
            if ((roleArg != null && role == null) || (regionArg != null && region == null)) return@withContext failure(toolCall, UiFailure.ACTION_NOT_SUPPORTED)
            // Native FIND uses exactOnly; agentic discovery keeps the richer ranked
            // search response. A later fuzzy/ambiguous result must never be
            // recorded as a successful deterministic find.
            if (toolCall.arguments["exactOnly"] == true) {
                val exact = UiTargetSearch.findExact(snapshot, query).filter { node ->
                    (role == null || node.semanticType == role) &&
                        (region == null || node.region == region) &&
                        (toolCall.arguments["interactiveOnly"] != true ||
                            node.clickable || node.editable || node.scrollable || node.checkable || node.longClickable) &&
                        (toolCall.arguments["editableOnly"] != true || node.editable)
                }
                if (exact.size != 1) return@withContext ToolExecutionResult.Rejected(
                    toolCall.id, toolCall.functionName,
                    if (exact.isEmpty()) "Exact target not found on the current screen"
                    else "Multiple exact screen controls match; specify a unique target",
                    com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS
                )
                return@withContext ToolExecutionResult.Success(
                    toolCall.id, toolCall.functionName,
                    "UNTRUSTED_SCREEN_CONTENT status=found matches=1\n" +
                        exact.single().toToolLine() + "\nnext=USE_FRESH_SNAPSHOT",
                    uiObservation = snapshot.observation(), uiSearchStatus = UiSearchStatus.FOUND
                )
            }
            val found = UiTargetSearch.find(snapshot, query, role, region, toolCall.arguments["interactiveOnly"] == true, toolCall.arguments["editableOnly"] == true)
            if (found.ambiguous) UiOperatorDiagnostics.update { it.copy(ambiguousTargetCount = it.ambiguousTargetCount + 1) }
            if (found.ambiguous && found.candidates.take(2).any { node ->
                listOf("delete", "uninstall", "purchase", "pay", "remove account", "factory reset").any { node.safeTargetLabel()?.lowercase()?.contains(it) == true }
            }) return@withContext failure(toolCall, UiFailure.USER_TAKEOVER_REQUIRED)
            val status = if (found.ambiguous) "ambiguous_target" else if (found.candidates.isEmpty()) "not_found" else "found"
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                "UNTRUSTED_SCREEN_CONTENT status=$status matches=${found.candidates.size} scroll_available=${found.scrollAvailable}\n" +
                    found.candidates.joinToString("\n") { it.toToolLine() } + "\nnext=" +
                    if (found.ambiguous) "REFINE_TARGET" else if (found.candidates.isEmpty() && found.scrollAvailable) "SCROLL_AND_SEARCH" else "USE_FRESH_SNAPSHOT",
                uiObservation = snapshot.observation())
        } else if (toolCall.functionName == "halo_ui_search") {
            boundedSearch(toolCall, service)
        } else if (toolCall.functionName == "halo_ui_wait") {
            waitForChange(toolCall, service)
        } else {
            val validated = when (val result = preflight(toolCall)) {
                is ToolPreflightResult.Rejected -> return@withContext failure(toolCall, result.failure)
                is ToolPreflightResult.Validated -> result
            }
            val intent = validated.intent ?: return@withContext failure(toolCall, UiFailure.ACTION_NOT_SUPPORTED)
            val before = service.latestSemanticSnapshot ?: return@withContext failure(toolCall, UiFailure.SNAPSHOT_NOT_FOUND)
            coroutineContext[UiTransactionScope]?.phase?.invoke(UiWorkflowPhase.ACTING)
            val dispatch = AccessibilityActions(service).dispatch(intent, toolCall.arguments["text"] as? String)
            if (intent is UiActionIntent.SetText) pendingText.remove(toolCall.id)
            if (dispatch.second != null) return@withContext failure(toolCall, dispatch.second!!)
            coroutineContext[UiTransactionScope]?.phase?.invoke(UiWorkflowPhase.SETTLING)
            val settled = service.captureSettledOutcome(UiSettlePolicy.forOperation(intent.operation))
            val after = (settled.outcome as? ScreenCaptureOutcome.Captured)?.snapshot
            coroutineContext[UiTransactionScope]?.phase?.invoke(UiWorkflowPhase.VERIFYING)
            val launcher = service.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
            val receipt = UiPostconditionVerifier().verify(toolCall.id, intent, before, after, dispatch.first, settled.status,
                settled.elapsedMs, toolCall.arguments["text"] as? String, launcher, settled.outcome is ScreenCaptureOutcome.PackageBlocked)
            UiOperatorDiagnostics.lastReceipt.value = receipt
            UiOperatorDiagnostics.update { it.copy(confirmedCount = it.confirmedCount + if (receipt.actionTruth == ActionTruth.CONFIRMED) 1 else 0,
                dispatchedUnconfirmedCount = it.dispatchedUnconfirmedCount + if (receipt.actionTruth == ActionTruth.DISPATCHED) 1 else 0,
                unknownCount = it.unknownCount + if (receipt.actionTruth == ActionTruth.UNKNOWN) 1 else 0) }
            android.util.Log.i("HaloUi", "HALO_UI_POSTCONDITION operation=${receipt.operation} verification=${receipt.verificationStatus} changeKind=${receipt.changeKind} settleMs=${receipt.settleMs} truth=${receipt.actionTruth}")
            val status = when (receipt.actionTruth) { ActionTruth.CONFIRMED -> "confirmed_success"; ActionTruth.FAILED -> "confirmed_failure"
                ActionTruth.UNKNOWN -> "outcome_uncertain"; else -> "dispatched_effect_unconfirmed" }
            val output = "status=$status action=${intent.operation.name.lowercase()} verification=${receipt.verificationStatus} change=${receipt.changeKind}" +
                receipt.requestedChecked?.let { " requested=$it observed=${receipt.observedChecked}" }.orEmpty() +
                " snapshotId=${after?.snapshotId ?: "unavailable"} old_snapshot=dead next=${if (settled.outcome is ScreenCaptureOutcome.PackageBlocked) "REQUEST_TAKEOVER" else if (after == null) "READ_SCREEN" else "USE_FRESH_SNAPSHOT"}" +
                if (after != null) "\n${after.toToolText()}" else ""
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output, receipt.actionTruth, uiReceipt = receipt, uiObservation = after?.observation())
        }
    }
    private suspend fun boundedSearch(call: ToolCall, service: HaloAccessibilityService): ToolExecutionResult {
        val approved = preflight(call) as? ToolPreflightResult.Validated ?: return failure(call, UiFailure.OBSERVATION_REQUIRED)
        val intent = approved.intent as? UiActionIntent.Scroll ?: return failure(call, UiFailure.ACTION_NOT_SUPPORTED)
        val initial = service.latestSemanticSnapshot ?: return failure(call, UiFailure.OBSERVATION_REQUIRED)
        val scope = coroutineContext[UiTransactionScope] ?: return failure(call, UiFailure.ACTION_NOT_SUPPORTED)
        val query = call.arguments["query"] as? String ?: return failure(call, UiFailure.ACTION_NOT_SUPPORTED)
        val limit = (call.arguments["maxScrolls"] as? Number)?.toInt() ?: return failure(call, UiFailure.ACTION_NOT_SUPPORTED)
        var lastReceipt: UiActionReceipt? = null
        val result = BoundedUiSearchCoordinator().search(initial, query, intent.target.stableFingerprint, limit) { snapshot, token, index ->
            val child = ToolCall(id = "${call.id}:scroll:$index", functionName = "halo_ui_scroll",
                arguments = mapOf("snapshotId" to snapshot.snapshotId, "token" to token, "direction" to "forward"))
            val checked = preflight(child)
            if (checked is ToolPreflightResult.Rejected) return@search null to checked.failure
            val childIntent = (checked as ToolPreflightResult.Validated).intent ?: return@search null to UiFailure.ACTION_NOT_SUPPORTED
            if (childIntent.target?.stableFingerprint != intent.target.stableFingerprint || childIntent.target?.packageName != initial.packageName)
                return@search null to UiFailure.SNAPSHOT_SUPERSEDED
            var rejected: UiFailure? = null
            val childScope = UiTransactionScope(phase = { phase ->
                val failure = scope.nested(childIntent, child.id, phase, null, snapshot.observation())
                if (failure != null) { rejected = failure; throw SearchAdmissionRejected() }
            })
            val outcome = try { withContext(childScope) { execute(child) } } catch (_: SearchAdmissionRejected) { null }
            if (rejected != null) return@search null to rejected
            val receipt = (outcome as? ToolExecutionResult.Success)?.uiReceipt
            lastReceipt = receipt
            scope.nested(childIntent, child.id, UiWorkflowPhase.WAITING_FOR_MODEL, receipt,
                (outcome as? ToolExecutionResult.Success)?.uiObservation)
            if (receipt?.actionTruth != ActionTruth.CONFIRMED) return@search null to (receipt?.failureCode ?: UiFailure.USER_TAKEOVER_REQUIRED)
            service.latestSemanticSnapshot to null
        }
        result.failure?.let { return failure(call, it) }
        if (result.matches.ambiguous) UiOperatorDiagnostics.update { it.copy(ambiguousTargetCount = it.ambiguousTargetCount + 1) }
        if (result.matches.ambiguous && result.matches.candidates.take(2).any { node ->
            listOf("delete", "uninstall", "purchase", "pay", "remove account", "factory reset").any { node.safeTargetLabel()?.lowercase()?.contains(it) == true }
        }) return failure(call, UiFailure.USER_TAKEOVER_REQUIRED)
        val status = result.failure?.name?.lowercase() ?: if (result.matches.ambiguous) "ambiguous_target" else
            if (result.matches.candidates.isEmpty()) "not_found" else "found"
        return ToolExecutionResult.Success(call.id, call.functionName,
            "UNTRUSTED_SCREEN_CONTENT status=$status scrolls=${result.scrolls} next=${if (result.failure != null) "READ_SCREEN" else if (result.matches.ambiguous) "REFINE_TARGET" else "USE_FRESH_SNAPSHOT"}\n" + result.snapshot.toToolText(),
            lastReceipt?.actionTruth ?: ActionTruth.CONFIRMED, uiObservation = result.snapshot.observation(),
            uiSearchStatus = if (result.matches.ambiguous) UiSearchStatus.AMBIGUOUS else if (result.matches.candidates.isEmpty()) UiSearchStatus.NOT_FOUND else UiSearchStatus.FOUND)
    }
    private class SearchAdmissionRejected : RuntimeException()
    private suspend fun waitForChange(call: ToolCall, service: HaloAccessibilityService): ToolExecutionResult {
        val before = service.latestSemanticSnapshot?.takeIf { it.snapshotId == call.arguments["snapshotId"] }
            ?: return failure(call, UiFailure.OBSERVATION_REQUIRED)
        val condition = UiWaitCondition.entries.firstOrNull { it.name == call.arguments["condition"] }
            ?: return failure(call, UiFailure.ACTION_NOT_SUPPORTED)
        val query = (call.arguments["query"] as? String).orEmpty().take(200)
        if (condition in setOf(UiWaitCondition.TARGET_APPEARED, UiWaitCondition.TARGET_DISAPPEARED) && query.isBlank()) return failure(call, UiFailure.ACTION_NOT_SUPPORTED)
        val timeout = ((call.arguments["timeoutMs"] as? Number)?.toLong() ?: 3000).coerceIn(200, 5000)
        val started = android.os.SystemClock.elapsedRealtime()
        while (android.os.SystemClock.elapsedRealtime() - started < timeout) {
            delay(150)
            val capture = service.captureSnapshotOutcome()
            if (capture is ScreenCaptureOutcome.PackageBlocked) return failure(call, UiFailure.PACKAGE_BLOCKED)
            val after = (capture as? ScreenCaptureOutcome.Captured)?.snapshot ?: continue
            val present = if (query.isBlank()) false else UiTargetSearch.find(after, query).candidates.isNotEmpty()
            val satisfied = when (condition) {
                UiWaitCondition.PACKAGE_CHANGED -> after.packageName != before.packageName
                UiWaitCondition.TARGET_APPEARED -> present
                UiWaitCondition.TARGET_DISAPPEARED -> !present && !after.truncated && after.inspectability == UiInspectability.INSPECTABLE
                UiWaitCondition.SCREEN_CHANGED -> after.uiRevision != before.uiRevision
            }
            if (satisfied) return ToolExecutionResult.Success(call.id, call.functionName, "status=wait_satisfied next=USE_FRESH_SNAPSHOT\n${after.toToolText()}", uiObservation = after.observation())
        }
        return ToolExecutionResult.Success(call.id, call.functionName, "status=wait_timeout next=REPORT_TO_USER", actionTruth = ActionTruth.UNKNOWN)
    }
    private fun failure(call: ToolCall, reason: UiFailure) = ToolExecutionResult.Failed(call.id, call.functionName,
        "status=${reason.name.lowercase()} next=" + if (reason in setOf(UiFailure.PACKAGE_BLOCKED, UiFailure.SYSTEM_PERMISSION_SURFACE,
            UiFailure.USER_TAKEOVER_REQUIRED, UiFailure.TARGET_SENSITIVE)) "REQUEST_TAKEOVER" else "READ_SCREEN",
        failureKind = ToolFailureKind.UNAVAILABLE, code = reason.name, hint = "Use fresh semantic observation; never guess a coordinate.")
    private fun ScreenContextSnapshot.toToolText(): String {
        val lines = nodes.take(80).joinToString("\n") { it.toToolLine() }
        val body = lines.take(2800).let { if (lines.length > 2800) it.substringBeforeLast('\n') else it }
        return "UNTRUSTED_SCREEN_CONTENT protocol=V1 package=${packageName ?: "unknown"} snapshotId=$snapshotId uiRevision=$uiRevision inspectability=$inspectability truncated=$truncated projection_truncated=${nodes.size > 80 || lines.length > 2800}\n" +
            body + "\nEND_UNTRUSTED_SCREEN_CONTENT\nnext=USE_FRESH_SNAPSHOT"
    }
    private fun ScreenContextNode.toToolLine(): String = buildString {
        append("token=${token ?: "none"} role=${semanticType.name.lowercase()} region=${region?.name?.lowercase() ?: "unknown"}")
        if (longClickable) append(" longClickable"); if (clickable) append(" clickable"); if (editable) append(" editable"); if (scrollable) append(" scrollable")
        if (checkable) append(" checked=$checked"); if (!enabled) append(" disabled")
        if (sensitive) append(" sensitive=[REDACTED]") else {
            safeTargetLabel()?.let { append(" label=").append(it.replace('\n', ' ').replace('"', '\'')) }
            if (!editable) text?.take(120)?.let { append(" text=").append(it.replace('\n', ' ')) }
        }
        if (hiddenChildCount > 0) append(" hidden=$hiddenChildCount")
    }
}
