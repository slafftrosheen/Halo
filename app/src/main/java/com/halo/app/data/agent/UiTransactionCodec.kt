package com.halo.app.data.agent

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.ui.*
import org.json.JSONObject

/** Safe semantic transaction data only; no screen tree, token, entered text or raw arguments. */
internal object UiTransactionCodec {
    private fun JSONObject.optional(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }
    fun encode(value: UiTransactionRecord): JSONObject = JSONObject()
        .put("toolCallId", value.toolCallId).put("phase", value.phase.name).put("protocol", value.protocol.name)
        .put("searchStatus", value.searchStatus?.name)
        .put("operation", value.operation?.name).put("fingerprint", value.targetFingerprint)
        .put("argumentDigest", value.argumentDigest).put("textLength", value.textLength).put("requestedChecked", value.requestedChecked)
        .put("observation", value.observation?.let { JSONObject().put("snapshotId", it.snapshotId).put("revision", it.revision)
            .put("package", it.packageName).put("clickable", it.clickable).put("editable", it.editable).put("checkable", it.checkable).put("scrollable", it.scrollable) })
        .put("preview", value.preview?.let { JSONObject().put("app", it.appLabel).put("package", it.packageName).put("action", it.action)
            .put("target", it.targetLabel).put("role", it.role).put("region", it.region).put("textLength", it.textLength).put("consequence", it.consequence.name) })
        .put("receipt", value.receipt?.let { JSONObject().put("actionId", it.actionId).put("operation", it.operation.name)
            .put("fingerprint", it.targetFingerprint).put("dispatch", it.dispatchStatus.name).put("verification", it.verificationStatus.name)
            .put("truth", it.actionTruth.name).put("before", it.beforeSnapshotId).put("after", it.afterSnapshotId)
            .put("change", it.changeKind).put("failure", it.failureCode?.name).put("settleMs", it.settleMs).put("timestamp", it.timestamp)
            .put("requestedChecked", it.requestedChecked).put("observedChecked", it.observedChecked).put("protocol", it.protocol.name)
            .put("settleStatus", it.settleStatus.name).put("sourcePackage", it.sourcePackage).put("destinationPackage", it.destinationPackage)
            .put("changes", JSONObject().put("package", it.changes.packageChanged).put("root", it.changes.rootChanged)
                .put("dialogAppeared", it.changes.dialogAppeared).put("dialogDisappeared", it.changes.dialogDisappeared)
                .put("targetPresent", it.changes.targetStillPresent).put("targetState", it.changes.targetStateChanged)
                .put("textMatched", it.changes.editableValueMatched).put("scroll", it.changes.scrollContentChanged)
                .put("nodeDelta", it.changes.nodeCountDelta).put("inspectability", it.changes.inspectabilityChanged)) })
    fun decode(value: JSONObject): UiTransactionRecord {
        require(value.optString("protocol", "V1") == "V1") { "OBSERVATION_REQUIRED: incompatible UI protocol" }
        require(value.optJSONObject("receipt")?.optString("protocol", "V1")?.let { it == "V1" } != false) { "OBSERVATION_REQUIRED: incompatible receipt protocol" }
        val observation = value.optJSONObject("observation")?.let { UiObservation(it.getString("snapshotId"), it.getLong("revision"),
            it.optional("package"), it.optBoolean("clickable"), it.optBoolean("editable"), it.optBoolean("checkable"), it.optBoolean("scrollable")) }
        val preview = value.optJSONObject("preview")?.let { UiTargetPreview(it.getString("app"), it.optional("package"),
            it.getString("action"), it.optional("target"), it.optional("role"), it.optional("region"),
            it.optInt("textLength").takeIf { _ -> it.has("textLength") && !it.isNull("textLength") },
            UiConsequenceHint.valueOf(it.getString("consequence"))) }
        val receipt = value.optJSONObject("receipt")?.let { row ->
            val change = row.optJSONObject("changes") ?: JSONObject()
            UiActionReceipt(row.getString("actionId"), UiOperation.valueOf(row.getString("operation")), row.optional("fingerprint"),
                UiDispatchStatus.valueOf(row.getString("dispatch")), UiVerification.valueOf(row.getString("verification")),
                ActionTruth.valueOf(row.getString("truth")), row.getString("before"), row.optional("after"), row.getString("change"),
                row.optional("failure")?.let(UiFailure::valueOf), row.optLong("settleMs"), row.getLong("timestamp"),
                UiActionProtocolVersion.valueOf(row.optString("protocol", "V1")),
                UiChangeSummary(change.optBoolean("package"), change.optBoolean("root"), change.optBoolean("dialogAppeared"),
                    change.optBoolean("dialogDisappeared"), change.optBoolean("targetPresent"), change.optBoolean("targetState"),
                    change.optBoolean("textMatched"), change.optBoolean("scroll"), change.optInt("nodeDelta"), change.optBoolean("inspectability")),
                row.optBoolean("requestedChecked").takeIf { row.has("requestedChecked") && !row.isNull("requestedChecked") },
                row.optBoolean("observedChecked").takeIf { row.has("observedChecked") && !row.isNull("observedChecked") },
                UiSettleStatus.valueOf(row.optString("settleStatus", "UNOBSERVABLE")), row.optional("sourcePackage"), row.optional("destinationPackage"))
        }
        return UiTransactionRecord(value.getString("toolCallId"), UiWorkflowPhase.valueOf(value.getString("phase")), observation, preview,
            value.optional("operation")?.let(UiOperation::valueOf), value.optional("fingerprint"), value.optional("argumentDigest"),
            value.optInt("textLength").takeIf { value.has("textLength") && !value.isNull("textLength") },
            value.optBoolean("requestedChecked").takeIf { value.has("requestedChecked") && !value.isNull("requestedChecked") }, receipt,
            UiActionProtocolVersion.valueOf(value.optString("protocol", "V1")), value.optional("searchStatus")?.let(UiSearchStatus::valueOf))
    }
}
