package com.halo.app.data.agent

import com.halo.runtime.cognition.apps.*
import com.halo.runtime.cognition.tools.ToolRiskClass
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant

/** Structure only. This format has no field capable of carrying argument contents. */
object SemanticOperationCodec {
    fun encode(r: SemanticOperationReceipt, shapes: List<SemanticArgumentShape>): JSONObject = JSONObject()
        .put("version", 1).put("operationId", r.operationId).put("action", r.semanticAction.value).put("route", r.route.name)
        .put("package", r.targetPackage).put("dispatch", r.dispatchTruth.name).put("effect", r.effectTruth.name)
        .put("failure", r.failure?.name).put("risk", r.risk.name).put("phase", r.phase.name)
        .put("startedAt", r.startedAt.toEpochMilli()).put("completedAt", r.completedAt?.toEpochMilli())
        .put("arguments", JSONArray(shapes.take(24).map { JSONObject().put("field", it.name.take(64)).put("type", it.type.name)
            .put("present", it.present).put("length", it.length) }))
    fun decode(p: JSONObject): Pair<SemanticOperationReceipt, List<SemanticArgumentShape>> {
        require(p.getInt("version") == 1)
        val r = SemanticOperationReceipt(p.getString("operationId"), SemanticActionId(p.getString("action")),
            AppOperationRoute.valueOf(p.getString("route")), p.optString("package").takeIf { it.isNotBlank() && it != "null" },
            DispatchTruth.valueOf(p.getString("dispatch")), EffectTruth.valueOf(p.getString("effect")),
            p.optString("failure").takeIf { it.isNotBlank() && it != "null" }?.let(SemanticOperationFailure::valueOf),
            Instant.ofEpochMilli(p.getLong("startedAt")), if (p.isNull("completedAt")) null else Instant.ofEpochMilli(p.getLong("completedAt")),
            ToolRiskClass.valueOf(p.getString("risk")), SemanticOperationPhase.valueOf(p.getString("phase")))
        val shapes = p.getJSONArray("arguments")
        require(shapes.length() <= 24)
        return r to (0 until shapes.length()).map { val shape = shapes.getJSONObject(it)
            SemanticArgumentShape(shape.getString("field"), SemanticValueType.valueOf(shape.getString("type")), shape.getBoolean("present"), shape.getInt("length")) }
    }
}
