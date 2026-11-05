package com.halo.app.nativeactions

import com.halo.app.everyday.presentation.*
import com.halo.app.everyday.organizer.OrganizerKind
import org.json.JSONObject

/** Content-free typed click payload. Exact target and source version survive host-cached clicks. */
internal object EverydayWidgetTargets {
    fun encode(target: EverydayTarget): String = JSONObject().apply {
        when (target) {
            EverydayTarget.Organizer -> put("type","organizer"); EverydayTarget.Sources -> put("type","sources")
            EverydayTarget.Clocks -> put("type","clocks"); EverydayTarget.Devices -> put("type","devices")
            EverydayTarget.Libraries -> put("type", "libraries")
            is EverydayTarget.LibraryEntry -> { put("type", "library-entry"); put("id",target.id); put("version",target.version); put("generation",target.generation); put("entry",target.entryId) }
            EverydayTarget.BriefLatest -> put("type","brief-latest")
            is EverydayTarget.Record -> { put("type","record"); put("id",target.id); put("version",target.version); put("original",target.originalStart?.toString()) }
            is EverydayTarget.Clock -> { put("type","clock-zone");put("zone",target.zoneId);put("label",target.label) }
            is EverydayTarget.Source -> { put("type","source"); put("id",target.id); put("version",target.version); put("revision",target.revision); put("article",target.articleId) }
            is EverydayTarget.Brief -> { put("type","brief"); put("id",target.id) }
            is EverydayTarget.Create -> { put("type","create"); put("kind",target.kind.name) }
        }
    }.toString()
    fun decode(raw: String): EverydayTarget {
        require(raw.length <= 4096)
        val json = JSONObject(raw)
        fun id() = json.getString("id").also { require(it.length in 1..500) }
        fun optional(name: String) = json.optString(name).takeUnless { it.isBlank() || it == "null" }
        return when (json.getString("type")) {
            "organizer" -> EverydayTarget.Organizer; "sources" -> EverydayTarget.Sources; "clocks" -> EverydayTarget.Clocks; "devices" -> EverydayTarget.Devices
            "libraries" -> EverydayTarget.Libraries
            "library-entry" -> EverydayTarget.LibraryEntry(id().also { require(it.length <= 160) }, json.getLong("version").also { require(it > 0) },
                json.getString("generation").also { require(it.length in 1..64) }, json.getString("entry").also { require(it.length in 1..256) })
            "brief-latest" -> EverydayTarget.BriefLatest
            "record" -> EverydayTarget.Record(id(),json.getLong("version"),optional("original")?.let(java.time.Instant::parse))
            "clock-zone" -> EverydayTarget.Clock(java.time.ZoneId.of(json.getString("zone")).id,json.getString("label").also { require(it.length<=120) })
            "source" -> EverydayTarget.Source(id(),json.getLong("version"),optional("revision"),optional("article"))
            "brief" -> EverydayTarget.Brief(id().also { require(it.matches(Regex("[a-f0-9]{64}"))) })
            "create" -> EverydayTarget.Create(OrganizerKind.valueOf(json.getString("kind")))
            else -> error("Unknown widget target")
        }
    }
}

suspend fun resolveEverydayWidgetEntry(context: android.content.Context, id: Int, version: Long, payload: String): String {
    val kind = requireNotNull(EverydayWidgetProviders.kind(context,id))
    val current = EverydayWidgetStore(context).get(id) ?: EverydayWidgetInstance(id,0,EverydayCardConfig(kind))
    require(current.version == version)
    val target = EverydayWidgetTargets.decode(payload)
    val snapshot = readEverydaySnapshot(context)
    val face = EverydayCards.project(current.config,snapshot,java.util.Locale.getDefault(),externalHost = true)
    require(target == face.target || face.rows.any { it.target == target })
    return target.route()
}
