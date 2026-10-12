package com.halo.app.ui.screens.settings

import androidx.compose.runtime.saveable.Saver
import com.halo.app.data.routine.RoutineCodec
import com.halo.runtime.cognition.routine.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Editing state only. Restoring cannot recover approval, enable execution or schedule a run. */
internal fun encodeRoutineDraft(draft: RoutineDraft): String {
    val routine = draft.routine
    val codec = RoutineCodec()
    return JSONObject().put("id", routine.id.value).put("name", routine.name)
        .put("original", draft.originalId?.value).put("trigger", codec.encodeTrigger(routine.trigger))
        .put("conditions", codec.encodeConditions(routine.conditions))
        .put("actions", JSONArray(routine.actions.map {
            JSONObject().put("tool", it.toolId).put("arguments", it.argumentsJson)
        })).put("delivery", codec.encodeDelivery(routine.delivery))
        .put("minimumInterval", routine.minimumIntervalSeconds)
        .put("created", routine.createdAt.toString()).put("updated", routine.updatedAt.toString()).toString()
}

internal fun decodeRoutineDraft(saved: String): RoutineDraft? = runCatching {
    require(saved.length <= 120_000)
    val json = JSONObject(saved)
    val codec = RoutineCodec()
    val actions = json.getJSONArray("actions")
    require(actions.length() <= 10)
    RoutineDraft(Routine(RoutineId(json.getString("id")), json.getString("name"), enabled = false,
        trigger = codec.decodeTrigger(json.getString("trigger"), 1),
        conditions = codec.decodeConditions(json.getString("conditions"), 1),
        actions = (0 until actions.length()).map { index -> actions.getJSONObject(index).let {
            RoutineAction(it.getString("tool"), it.getString("arguments"))
        } }, createdAt = Instant.parse(json.getString("created")), updatedAt = Instant.parse(json.getString("updated")),
        delivery = codec.decodeDelivery(json.getString("delivery")),
        minimumIntervalSeconds = json.getLong("minimumInterval"), grantDigest = null),
        originalId = json.optString("original").takeIf { it.isNotBlank() }?.let(::RoutineId))
}.getOrNull()

internal val routineDraftStateSaver = Saver<RoutineDraft, String>(save = { encodeRoutineDraft(it) }, restore = ::decodeRoutineDraft)
internal val nullableRoutineDraftStateSaver = Saver<RoutineDraft?, String>(save = { it?.let(::encodeRoutineDraft) }, restore = ::decodeRoutineDraft)
internal val routineEditStateSaver = Saver<Routine, String>(
    save = { encodeRoutineDraft(RoutineDraft(it)) }, restore = { decodeRoutineDraft(it)?.routine })
