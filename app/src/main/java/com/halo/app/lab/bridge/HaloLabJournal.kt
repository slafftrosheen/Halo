package com.halo.app.lab.bridge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class HaloLabAttempt(
    val attemptId: String,
    val requestHash: String,
    val modelId: String,
    val artifactSha256: String,
    val state: String,
    val lastSequence: Long,
    val terminal: Boolean,
    val payload: JSONObject,
)

/** Halo-owned journal: accepted intent precedes worker execution, and terminal rows are immutable. */
class HaloLabJournal(context: Context) : SQLiteOpenHelper(context, "halo_lab_bridge_v1.db", null, 1) {
    val serverEpoch: String by lazy {
        val prefs = context.applicationContext.getSharedPreferences("halo_lab_bridge_epoch", Context.MODE_PRIVATE)
        prefs.getString(KEY_EPOCH, null) ?: UUID.randomUUID().toString().also { created ->
            check(prefs.edit().putString(KEY_EPOCH, created).commit()) { "SERVER_EPOCH_PERSIST_FAILED" }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE attempts (
            attempt_id TEXT PRIMARY KEY NOT NULL,
            request_hash TEXT NOT NULL,
            model_id TEXT NOT NULL,
            artifact_sha256 TEXT NOT NULL,
            state TEXT NOT NULL,
            terminal INTEGER NOT NULL DEFAULT 0,
            payload_json TEXT NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE events (
            attempt_id TEXT NOT NULL,
            sequence INTEGER NOT NULL,
            payload_json TEXT NOT NULL,
            payload_sha256 TEXT NOT NULL,
            PRIMARY KEY(attempt_id, sequence),
            FOREIGN KEY(attempt_id) REFERENCES attempts(attempt_id)
        )""".trimIndent())
        db.execSQL("CREATE TABLE cursors (attempt_id TEXT PRIMARY KEY NOT NULL, ack_sequence INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No destructive Halo Lab journal migration is defined")
    }

    @Synchronized
    fun accept(
        attemptId: String,
        requestHash: String,
        modelId: String,
        artifactSha256: String,
        stageBudgets: LabAttemptStageBudgets,
        acceptedAtElapsedMs: Long,
    ): String {
        require(stageBudgets.valid())
        val deadlineBudgetMs = requireNotNull(stageBudgets.totalMs)
        require(deadlineBudgetMs in 1L..LabAttemptDeadlinePolicy.MAX_ATTEMPT_MS)
        require(acceptedAtElapsedMs > 0L)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = getAttempt(db, attemptId)
            if (existing != null) {
                if (existing.requestHash != requestHash) return "REQUEST_ID_CONFLICT"
                db.setTransactionSuccessful()
                return "EXISTING"
            }
            val outstanding = db.rawQuery("SELECT attempt_id FROM attempts WHERE terminal=0 LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (outstanding != null) return "RECOVERY_REQUIRED"
            val payload = JSONObject()
                .put("kind", "SESSION_ACCEPTED")
                .put("artifactId", modelId)
                .put("modelId", modelId)
                .put("artifactSha256", artifactSha256)
                .put("origin", "DEVICE_MEASUREMENT")
                .put("eventCode", "ACCEPTED")
                .put("deadlineBudgetMs", deadlineBudgetMs)
                .put("stageBudgets", stageBudgets.toJson())
                .put("acceptedAtElapsedMs", acceptedAtElapsedMs)
            db.execSQL(
                "INSERT INTO attempts(attempt_id,request_hash,model_id,artifact_sha256,state,terminal,payload_json,updated_at_epoch_ms) VALUES(?,?,?,?,?,0,?,?)",
                arrayOf(attemptId, requestHash, modelId, artifactSha256, "ACCEPTED", payload.toString(), System.currentTimeMillis()),
            )
            appendEvent(db, attemptId, payload)
            db.setTransactionSuccessful()
            return "CREATED"
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun stage(attemptId: String, stage: String, eventCode: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val changed = db.update("attempts", android.content.ContentValues().apply {
                put("state", stage)
                put("updated_at_epoch_ms", System.currentTimeMillis())
            }, "attempt_id=? AND terminal=0", arrayOf(attemptId))
            if (changed == 1) appendEvent(db, attemptId, JSONObject()
                .put("kind", "ATTEMPT_STAGE")
                .put("attemptId", attemptId)
                .put("stage", stage)
                .put("eventCode", eventCode))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun terminal(attemptId: String, result: JSONObject): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val state = result.optString("status", "FAILED")
            val values = android.content.ContentValues().apply {
                put("state", "TERMINAL")
                put("terminal", 1)
                put("payload_json", result.toString())
                put("updated_at_epoch_ms", System.currentTimeMillis())
            }
            val changed = db.update("attempts", values, "attempt_id=? AND terminal=0", arrayOf(attemptId))
            if (changed == 1) {
                val rawOutcome = result.optString("outcome", "UNKNOWN")
                val outcome = when (rawOutcome) {
                    "PASS", "PASSED" -> "PASSED"
                    "FAIL", "FAILED" -> "FAILED"
                    "TIMED_OUT" -> "TIMED_OUT"
                    "CANCELLED" -> "CANCELLED"
                    "SKIPPED" -> "SKIPPED"
                    else -> "INTERRUPTED"
                }
                val event = JSONObject()
                    .put("kind", "ATTEMPT_TERMINAL")
                    .put("attemptId", attemptId)
                    .put("eventCode", state)
                    .put("outcome", outcome)
                    .put("validity", result.optString("validity", "UNKNOWN"))
                putIfPresent(event, "failureCode", result, "failureCode")
                putIfPresent(event, "failureDomain", result, "failureDomain")
                putIfPresent(event, "workerInstanceId", result, "workerInstanceId")
                putIfPresent(event, "artifactId", result, "artifactId")
                if (!event.has("artifactId")) putIfPresent(event, "artifactId", result, "modelId")
                putIfPresent(event, "artifactSha256", result, "artifactSha256")
                putIfPresent(event, "unavailableReason", result, "unavailableReason")
                val bytes = result.optLong("artifactBytes", -1L)
                if (bytes >= 0L) event.put("artifactBytes", bytes)
                val response = if (result.has("response") && !result.isNull("response")) result.optString("response") else null
                if (response != null) {
                    val preview = response.take(MAX_OUTPUT_PREVIEW_CHARS)
                    val previewBytes = preview.toByteArray(Charsets.UTF_8)
                    event.put("outputPreview", preview)
                        .put("outputBytes", previewBytes.size)
                        .put("outputSha256", sha256(preview))
                        .put("outputTruncated", result.optBoolean("outputTruncated", response.length > MAX_OUTPUT_PREVIEW_CHARS))
                }
                copyMetric(event, result, "loadMs")
                copyMetric(event, result, "timeToFirstTokenMs")
                copyMetric(event, result, "generationMs")
                putIfPresent(event, "effectiveBackendCode", result, "effectiveBackend")
                putIfPresent(event, "runtimeVersion", result, "runtimeVersion")
                appendEvent(db, attemptId, event)
            }
            db.setTransactionSuccessful()
            return changed == 1
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun query(attemptId: String): HaloLabAttempt? = getAttempt(readableDatabase, attemptId)

    @Synchronized
    fun openAttempts(): List<HaloLabAttempt> {
        val ids = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT attempt_id FROM attempts WHERE terminal=0 ORDER BY updated_at_epoch_ms,attempt_id", null)
            .use { cursor -> while (cursor.moveToNext()) ids += cursor.getString(0) }
        return ids.mapNotNull { query(it) }
    }

    @Synchronized
    fun readEvents(attemptId: String, afterSequence: Long, maximum: Int): JSONArray {
        val result = JSONArray()
        readableDatabase.rawQuery(
            "SELECT sequence,payload_json,payload_sha256 FROM events WHERE attempt_id=? AND sequence>? ORDER BY sequence LIMIT ?",
            arrayOf(attemptId, afterSequence.toString(), maximum.coerceIn(1, 64).toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) result.put(JSONObject()
                .put("serverEpoch", serverEpoch)
                .put("remoteRunId", attemptId)
                .put("sequence", cursor.getLong(0))
                .put("payloadSha256", cursor.getString(2))
                .put("payload", JSONObject(cursor.getString(1))))
        }
        return result
    }

    @Synchronized
    fun acknowledge(attemptId: String, sequence: Long): Boolean {
        val last = lastSequence(readableDatabase, attemptId) ?: return false
        if (sequence < 0L || sequence > last) return false
        writableDatabase.execSQL(
            "INSERT INTO cursors(attempt_id,ack_sequence) VALUES(?,?) ON CONFLICT(attempt_id) DO UPDATE SET ack_sequence=MAX(ack_sequence,excluded.ack_sequence)",
            arrayOf(attemptId, sequence),
        )
        return true
    }

    private fun getAttempt(db: SQLiteDatabase, attemptId: String): HaloLabAttempt? = db.rawQuery(
        "SELECT request_hash,model_id,artifact_sha256,state,terminal,payload_json FROM attempts WHERE attempt_id=?",
        arrayOf(attemptId),
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else HaloLabAttempt(
            attemptId, cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
            lastSequence(db, attemptId) ?: 0L, cursor.getInt(4) != 0, JSONObject(cursor.getString(5)),
        )
    }

    private fun lastSequence(db: SQLiteDatabase, attemptId: String): Long? = db.rawQuery(
        "SELECT MAX(sequence) FROM events WHERE attempt_id=?", arrayOf(attemptId),
    ).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }

    private fun appendEvent(db: SQLiteDatabase, attemptId: String, payload: JSONObject) {
        val sequence = (lastSequence(db, attemptId) ?: 0L) + 1L
        val canonical = canonicalPayload(payload)
        db.execSQL(
            "INSERT INTO events(attempt_id,sequence,payload_json,payload_sha256) VALUES(?,?,?,?)",
            arrayOf(attemptId, sequence, canonical, sha256(canonical)),
        )
    }

    /** Byte-for-byte key order and explicit nulls match Lab BridgeEventPayload.canonicalJson(). */
    private fun canonicalPayload(value: JSONObject): String = buildString {
        fun nullable(key: String): String = if (!value.has(key) || value.isNull(key)) "null" else quote(value.optString(key))
        fun number(key: String): String =
            if (value.has(key) && !value.isNull(key)) value.optLong(key).toString() else "null"
        append('{')
        append("\"kind\":").append(quote(value.optString("kind"))).append(',')
        append("\"attemptId\":").append(nullable("attemptId")).append(',')
        append("\"stage\":").append(nullable("stage")).append(',')
        append("\"eventCode\":").append(quote(value.optString("eventCode"))).append(',')
        append("\"outcome\":").append(nullable("outcome")).append(',')
        append("\"validity\":").append(nullable("validity")).append(',')
        append("\"failureCode\":").append(nullable("failureCode")).append(',')
        append("\"failureDomain\":").append(nullable("failureDomain")).append(',')
        append("\"stopDisposition\":").append(nullable("stopDisposition")).append(',')
        append("\"workerInstanceId\":").append(nullable("workerInstanceId")).append(',')
        append("\"artifactId\":").append(nullable("artifactId")).append(',')
        append("\"artifactBytes\":").append(if (value.has("artifactBytes") && !value.isNull("artifactBytes")) value.optLong("artifactBytes") else "null").append(',')
        append("\"artifactSha256\":").append(nullable("artifactSha256")).append(',')
        append("\"unavailableReason\":").append(nullable("unavailableReason")).append(',')
        append("\"outputPreview\":").append(nullable("outputPreview")).append(',')
        append("\"outputBytes\":").append(if (value.has("outputBytes") && !value.isNull("outputBytes")) value.optLong("outputBytes") else "null").append(',')
        append("\"outputSha256\":").append(nullable("outputSha256")).append(',')
        append("\"outputTruncated\":").append(if (value.has("outputTruncated") && !value.isNull("outputTruncated")) value.optBoolean("outputTruncated") else "null").append(',')
        append("\"loadMs\":").append(number("loadMs")).append(',')
        append("\"timeToFirstTokenMs\":").append(number("timeToFirstTokenMs")).append(',')
        append("\"generationMs\":").append(number("generationMs")).append(',')
        append("\"effectiveBackendCode\":").append(nullable("effectiveBackendCode")).append(',')
        append("\"runtimeVersion\":").append(nullable("runtimeVersion"))
        append('}')
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun putIfPresent(target: JSONObject, targetKey: String, source: JSONObject, sourceKey: String) {
        if (source.has(sourceKey) && !source.isNull(sourceKey)) target.put(targetKey, source.get(sourceKey))
    }

    private fun copyMetric(target: JSONObject, source: JSONObject, key: String) {
        val value = source.optLong(key, -1L)
        if (value >= 0L) target.put(key, value)
    }

    companion object {
        private const val KEY_EPOCH = "server_epoch"
        private const val MAX_OUTPUT_PREVIEW_CHARS = 512
        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
