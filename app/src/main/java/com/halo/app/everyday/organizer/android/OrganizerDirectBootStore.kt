package com.halo.app.everyday.organizer.android

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.halo.app.data.everyday.OrganizerCodec
import com.halo.app.everyday.organizer.*
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

data class LockedOrganizerEntry(val record: OrganizerRecord, val exceptions: List<OccurrenceOverride>, val receipts: List<DeliveryReceipt>)

/** Device-encrypted mirror contains timing/opaque IDs only; never titles, notes, links or source URLs. */
class OrganizerDirectBootStore(private val context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext().getSharedPreferences("organizer_boot_v1", Context.MODE_PRIVATE)
    fun entries(): List<LockedOrganizerEntry> = synchronized(lock) {
        preferences.all.filterKeys { it.startsWith("record:") }.values.map { decode(it as String) }
    }
    fun get(id: String): LockedOrganizerEntry? = synchronized(lock) { preferences.getString("record:$id", null)?.let(::decode) }
    fun invalidate(id: String) = synchronized(lock) {
        // A record edit revokes locked-boot authority before the credential-encrypted transaction commits.
        check(preferences.edit().remove("record:$id").remove("journal:$id")
            .putString("generation:$id", java.util.UUID.randomUUID().toString()).commit())
        OrganizerAlarmQueue.revoke(context, id)
    }
    fun generation(id: String): String? = synchronized(lock) { preferences.getString("generation:$id", null) }
    fun saveIfGeneration(entry: LockedOrganizerEntry, generation: String?): Boolean = synchronized(lock) {
        if (this.generation(entry.record.id) != generation) return@synchronized false
        save(entry)
        true
    }
    fun revokeVersion(id: String, version: Long) = synchronized(lock) {
        if (get(id)?.record?.version == version) invalidate(id)
    }
    fun save(entry: LockedOrganizerEntry) = synchronized(lock) {
        check(preferences.edit().putString("record:${entry.record.id}", encode(redact(entry))).commit())
    }
    fun receipt(receipt: DeliveryReceipt, journal: Boolean = false) = synchronized(lock) {
        val current = get(receipt.recordId) ?: return@synchronized
        if (current.record.version != receipt.recordVersion) return@synchronized
        val next = current.copy(receipts = (current.receipts.filterNot { it.occurrenceKey == receipt.occurrenceKey } + receipt)
            )
        val encoded = encode(redact(next))
        val editor = preferences.edit().putString("record:${receipt.recordId}", encoded)
        if (journal) editor.putString("journal:${receipt.recordId}", encoded)
        check(editor.commit())
    }
    fun journals(): Map<String, String> = synchronized(lock) {
        preferences.all.filterKeys { it.startsWith("journal:") }.mapKeys { it.key.removePrefix("journal:") }.mapValues { it.value as String }
    }
    fun acknowledgeJournal(id: String, encoded: String) = synchronized(lock) {
        if (preferences.getString("journal:$id", null) == encoded) check(preferences.edit().remove("journal:$id").commit())
    }
    fun decode(body: String): LockedOrganizerEntry {
        val json = JSONObject(body)
        require(json.getInt("schema") == 1)
        val exceptions = json.getJSONArray("exceptions")
        val receipts = json.getJSONArray("receipts")
        return LockedOrganizerEntry(OrganizerCodec.decode(json.getString("record")),
            (0 until exceptions.length()).map { OrganizerCodec.override(exceptions.getString(it)) },
            (0 until receipts.length()).map { receipt(receipts.getJSONObject(it)) })
    }
    private fun redact(entry: LockedOrganizerEntry) = entry.copy(record = entry.record.copy(title = "Halo", details = "", location = "",
        links = emptyList(), sourceUrl = null, sourceLabel = null, calendarUid = null, parentId = null, priority = TaskPriority.NORMAL),
        exceptions = entry.exceptions.map { it.copy(replacementTitle = null, replacementDetails = null, replacementLocation = null, sourceUrlOverride = null) },
        receipts = entry.receipts.map { it.copy(detail = null) })
    private fun encode(entry: LockedOrganizerEntry) = JSONObject().put("schema", 1).put("record", OrganizerCodec.encode(entry.record))
        .put("exceptions", JSONArray(entry.exceptions.map(OrganizerCodec::encode)))
        .put("receipts", JSONArray(entry.receipts.map(::json))).toString()
    private fun json(value: DeliveryReceipt) = JSONObject().put("key", value.occurrenceKey).put("id", value.recordId)
        .put("version", value.recordVersion).put("at", value.triggerAt.toEpochMilli()).put("state", value.state.name)
        .put("updated", value.updatedAt.toEpochMilli()).put("snooze", value.snoozedUntil?.toEpochMilli())
        .put("elapsed", value.snoozeElapsedDeadlineMs).put("boot", value.snoozeBootId)
    private fun receipt(json: JSONObject) = DeliveryReceipt(json.getString("key"), json.getString("id"), json.getLong("version"),
        Instant.ofEpochMilli(json.getLong("at")), DeliveryState.valueOf(json.getString("state")), Instant.ofEpochMilli(json.getLong("updated")),
        snoozedUntil = if (json.isNull("snooze")) null else Instant.ofEpochMilli(json.getLong("snooze")),
        snoozeElapsedDeadlineMs = if (json.isNull("elapsed")) null else json.getLong("elapsed"),
        snoozeBootId = if (json.isNull("boot")) null else json.getString("boot"))
    private companion object { val lock = Any() }
}

object OrganizerAndroidClock {
    private val processFallback = java.util.UUID.randomUUID().toString()
    fun elapsedMs() = SystemClock.elapsedRealtime()
    fun bootId(context: Context): String = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }
        .getOrNull()?.let { "boot:$it" } ?: "process:$processFallback" // Conservative wall fallback if an OEM hides boot count.
}

/** Normal-process adapter keeps the redacted mirror in step with the durable credential-encrypted owner. */
class MirroredOrganizerDelivery(private val delegate: OrganizerDeliveryPort, private val repository: OrganizerRepository,
    private val mirror: OrganizerDirectBootStore) : OrganizerDeliveryPort {
    override suspend fun resetSchedules() = delegate.resetSchedules()
    override suspend fun revokeVersion(recordId: String, version: Long) { mirror.revokeVersion(recordId, version) }
    override suspend fun cancelSchedule(recordId: String) = delegate.cancelSchedule(recordId)
    override suspend fun schedule(record: OrganizerRecord, delivery: PlannedDelivery): DeliveryOutcome {
        val generation = mirror.generation(record.id)
        val current = repository.get(record.id)
        if (current?.version != record.version || current.deletedAt != null || current.status != OrganizerStatus.ACTIVE)
            return DeliveryOutcome(DeliveryState.FAILED, "The item changed before scheduling")
        if (!mirror.saveIfGeneration(LockedOrganizerEntry(record, repository.overrides(record.id), repository.receipts(record.id)), generation))
            return DeliveryOutcome(DeliveryState.FAILED, "The item changed before scheduling")
        val result = delegate.schedule(record, delivery)
        repository.receipts(record.id).find { it.occurrenceKey == delivery.occurrenceKey }?.let {
            mirror.receipt(it.copy(state = result.state))
        }
        return result
    }
    override suspend fun present(record: OrganizerRecord, receipt: DeliveryReceipt, recovered: Boolean): DeliveryOutcome {
        mirror.receipt(receipt.copy(state = DeliveryState.DELIVERING))
        val result = delegate.present(record, receipt, recovered)
        mirror.receipt(receipt.copy(state = result.state))
        return result
    }
    override suspend fun dismiss(occurrenceKey: String, recordVersion: Long) {
        delegate.dismiss(occurrenceKey, recordVersion)
        mirror.get(occurrenceKey.substringBefore('@'))?.receipts?.find { it.occurrenceKey == occurrenceKey && it.recordVersion == recordVersion }
            ?.takeIf { it.state in setOf(DeliveryState.FIRED, DeliveryState.DELIVERING) }?.let {
                mirror.receipt(it.copy(state = DeliveryState.DISMISSED, updatedAt = Instant.now()))
            }
    }
}
