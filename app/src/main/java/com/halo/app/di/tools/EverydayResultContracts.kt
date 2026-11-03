package com.halo.app.di.tools

import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.briefing.*
import com.halo.runtime.cognition.tools.ToolOperation
import org.json.JSONArray
import org.json.JSONObject

/** Actual successful payload shapes. Rejections/unknown completion use the existing Runtime envelope. */
object EverydayResultContracts {
    private fun text(max: Int)=EverydayToolContracts.text(max)
    private fun integer(min: Long=0,max: Long=Long.MAX_VALUE)=EverydayToolContracts.number(min,max)
    private fun decimal(min: Double,max: Double)=mapOf("type" to "number","minimum" to min,"maximum" to max)
    private fun choice(values: List<String>)=EverydayToolContracts.choice(values)
    private fun obj(fields: Map<String,Any>,required: List<String> = fields.keys.toList())=EverydayToolContracts.obj(fields,required)
    private fun variants(fields: Map<String,Any>,vararg required: List<String>) = obj(fields,emptyList())+
        mapOf("anyOf" to required.map {mapOf("required" to it)})
    private fun array(item: Map<String,Any>,max: Int)=EverydayToolContracts.array(item,max)
    private fun nullable(value: Map<String,Any>)=mapOf("anyOf" to listOf(value,mapOf("type" to "null")))
    private val bool=mapOf("type" to "boolean")
    private val id=text(256)
    private val version=integer(1)
    private val instant=text(40)
    private val epoch=integer(Long.MIN_VALUE)
    private val summaryFields=mapOf("id" to id,"version" to version,"kind" to choice(OrganizerKind.entries.map {it.name}),
        "title" to text(200),"status" to choice(OrganizerStatus.entries.map {it.name}),"priority" to choice(TaskPriority.entries.map {it.name}),
        "start" to text(32),"zoneId" to text(100),"repeating" to bool)
    private val summary=obj(summaryFields,summaryFields.keys.filter {it !in setOf("start","zoneId")})
    private val occurrence=obj(summaryFields+mapOf("originalStart" to instant,"startInstant" to instant,"endInstant" to instant,"completed" to bool),
        summaryFields.keys.filter {it !in setOf("start","zoneId")}+listOf("originalStart","startInstant","completed"))
    private val recordTime=obj(mapOf("start" to text(32),"end" to text(32),"zone" to text(100),"allDay" to bool,"floating" to bool),listOf("start","zone","allDay","floating"))
    private val recordRepeat=obj(mapOf("frequency" to choice(RepeatFrequency.entries.map {it.name}),"interval" to integer(1,366),
        "weekdays" to array(choice(java.time.DayOfWeek.entries.map {it.name}),7),"count" to integer(1,10000),"until" to epoch),listOf("frequency","interval","weekdays"))
    private val record=obj(mapOf("schema" to integer(1,1),"id" to id,"version" to version,"kind" to choice(OrganizerKind.entries.map {it.name}),
        "title" to text(200),"details" to text(16000),"priority" to choice(TaskPriority.entries.map {it.name}),"status" to choice(OrganizerStatus.entries.map {it.name}),
        "parentId" to id,"notifyBeforeMinutes" to integer(0,10080),"createdAt" to epoch,"updatedAt" to epoch,"deletedAt" to epoch,
        "sourceUrl" to text(2048),"sourceLabel" to text(200),"location" to text(1000),"calendarUid" to text(2000),"time" to recordTime,"repeat" to recordRepeat,
        "timer" to obj(mapOf("duration" to integer(1000,31L*86400000),"remaining" to integer(0,31L*86400000),"started" to epoch,
            "elapsed" to integer(),"boot" to text(200),"runId" to text(200)),listOf("duration","remaining","runId")),
        "links" to array(obj(mapOf("id" to id,"relation" to choice(listOf("RELATED_TO","REFERENCES","SOURCE_OF")))),50)),
        listOf("schema","id","version","kind","title","details","priority","status","createdAt","updatedAt","location","links"))
    private val receipt=obj(mapOf("occurrenceKey" to text(450),"recordVersion" to version,"triggerAt" to instant,
        "state" to choice(DeliveryState.entries.map {it.name}),"updatedAt" to instant,"snoozedUntil" to instant),
        listOf("occurrenceKey","recordVersion","triggerAt","state","updatedAt"))
    private val savedFields=mapOf("id" to id,"version" to version,"record" to summary,"saved" to bool,"deliveryConfirmed" to bool)
    private val saved=obj(savedFields)
    private val worldClock=obj(mapOf("id" to id,"version" to version,"label" to text(120),"zoneId" to text(100),"position" to integer(0,1000),"deleted" to bool))
    private val sourceFields=mapOf("id" to text(160),"version" to version,"title" to text(200),"publisher" to text(200),
        "kind" to choice(CollectorKind.entries.map {it.name}),"category" to choice(SourceCategory.entries.map {it.name}),"language" to text(40),
        "selected" to bool,"enabled" to bool,"muted" to bool,"childApproved" to bool,"refreshMinutes" to integer(15,1440),
        "contentRevision" to text(200),"retrievedAt" to instant,"expiresAt" to instant,"attempt" to choice(CollectorAttemptState.entries.map {it.name}),
        "attemptedAt" to instant,"nextAllowedAt" to instant,"stale" to bool,"mode" to choice(listOf("Local","Power")),"cached" to bool)
    private val sourceRequired=sourceFields.keys.filter {it !in setOf("contentRevision","retrievedAt","expiresAt","attemptedAt","nextAllowedAt")}
    private val source=obj(sourceFields,sourceRequired)
    private val payloadItem=obj(mapOf("id" to text(2000),"title" to text(1000),"text" to text(8000),"summary" to text(1000),
        "url" to text(4096),"publisher" to text(200),"published" to epoch,"language" to text(40),"categories" to array(text(200),20),
        "area" to text(2000),"severity" to text(200),"onset" to epoch,"expires" to epoch,"kind" to text(100),"route" to text(4096),"revision" to text(4000),"at" to epoch),listOf("id","title"))
    private val content=obj(mapOf("schema" to integer(1,1),"type" to choice(listOf("articles","warnings","local","weather")),"items" to array(payloadItem,5),
        "place" to obj(mapOf("name" to text(200),"lat" to decimal(-90.0,90.0),"lon" to decimal(-180.0,180.0),"zone" to text(100))),
        "observed" to epoch,"temperature" to decimal(-100.0,80.0),"code" to integer(0,99),"attribution" to text(2000),"attributionUrl" to text(4096),
        "days" to array(obj(mapOf("date" to text(10),"max" to decimal(-100.0,80.0),"min" to decimal(-100.0,80.0),"rain" to integer(0,100),"code" to integer(0,99)),listOf("date")),16)),listOf("schema","type"))
    private val briefFields=mapOf("id" to text(64),"period" to choice(BriefPeriod.entries.map {it.name}),"audience" to choice(BriefAudience.entries.map {it.name}),
        "date" to text(10),"zoneId" to text(100),"generatedAt" to instant)
    private val briefSummary=obj(briefFields)
    private val briefDetailFields=briefFields+mapOf("facts" to array(obj(mapOf("id" to text(2500),"kind" to choice(BriefFactKind.entries.map {it.name}),
        "title" to text(200),"detail" to text(400),"sourceId" to text(200),"revision" to text(4000),"at" to instant,"url" to text(4096)),
        listOf("id","kind","title","detail","sourceId","revision")),30),
        "sources" to array(obj(mapOf("id" to text(200),"version" to integer(),"publisher" to text(200),"retrievedAt" to instant,"expiresAt" to instant,
            "attempt" to choice(CollectorAttemptState.entries.map {it.name})),listOf("id","version","publisher","attempt")),101),
        "baselineMissing" to bool,"changes" to array(obj(mapOf("kind" to choice(BriefChangeKind.entries.map {it.name}),"factId" to text(2500),"sourceId" to text(200))),30))
    private val settings=variants(mapOf("schema" to integer(1,1),"version" to integer(),"proactive" to bool,"morning" to text(18),"evening" to text(18),
        "quiet" to bool,"quietStart" to text(18),"quietEnd" to text(18),"headlines" to integer(0,10),"tasks" to integer(1,20),
        "pinned" to array(id,100),"childRecords" to array(id,100),"sharedRecordCount" to integer(0,100)),listOf("schema","version","proactive","morning","evening","quiet","quietStart","quietEnd","headlines","tasks","pinned","childRecords"),listOf("sharedRecordCount"))
    private val config=obj((listOf("kind","zoneId","cityLabel","sourceId","headlineSourceId","recordId","priority","limit","hidePrivate","theme","sections")
        .associateWith {text(if(it=="sections") 200 else 256)}),listOf("kind","cityLabel","limit","hidePrivate","theme","sections"))

    fun schema(name: String): Map<String,Any> = when(name) {
        "halo_organizer_query" -> variants(mapOf("records" to array(summary,50),"occurrences" to array(occurrence,50),"nextOffset" to nullable(integer(0,10050))),listOf("records","nextOffset"),listOf("occurrences","nextOffset"))
        "halo_organizer_get" -> obj(mapOf("id" to id,"version" to version,"record" to record,"overrides" to array(obj(mapOf("originalStart" to instant,
            "disposition" to choice(OccurrenceDisposition.entries.map {it.name}),"version" to version,"title" to text(200),"replacementStart" to text(32)),listOf("originalStart","disposition","version")),100),
            "omittedOverrides" to integer(),"deliveries" to array(receipt,20),"occurrence" to occurrence),listOf("id","version","record","overrides","omittedOverrides","deliveries"))
        "halo_organizer_create","halo_organizer_alarm","halo_organizer_edit","halo_organizer_action","halo_organizer_occurrence" -> saved
        "halo_organizer_delivery" -> variants(savedFields+mapOf("dismissed" to bool,"occurrenceKey" to text(450)),savedFields.keys.toList()+"occurrenceKey",listOf("dismissed","occurrenceKey"))
        "halo_organizer_clock_action" -> variants(savedFields+mapOf("dismissedOccurrences" to array(text(450),20),"dismissedCount" to integer(0,20)),savedFields.keys.toList(),listOf("dismissedOccurrences","dismissedCount"))
        "halo_organizer_calendar_preview" -> obj(mapOf("objectId" to id,"fileVersion" to text(200),"zoneId" to text(100),"reviewToken" to text(64),
            "records" to array(summary,50),"issues" to array(obj(mapOf("item" to integer(1,2000),"reasonCode" to choice(CalendarImportReason.entries.map {it.name}),
                "arguments" to array(text(100),1),"warning" to bool,"seriesWithheld" to bool)),50),"recordCount" to integer(0,2000),"issueCount" to integer(0,10000),"nextOffset" to nullable(integer(0,10050))))
        "halo_organizer_calendar_import" -> obj(mapOf("added" to array(id,50),"alreadyPresent" to array(id,50),"saved" to bool,"deliveryConfirmed" to bool))
        "halo_organizer_calendar_export" -> obj(mapOf("objectId" to id,"fileVersion" to text(200),"name" to text(200),"mimeType" to text(100),"saved" to bool,"exportedExternally" to bool))
        "halo_clock_query" -> obj(mapOf("phoneZoneId" to text(100),"clocks" to array(worldClock,24),"cities" to array(obj(mapOf("cityId" to text(20),
            "label" to text(200),"countryCode" to text(2),"zoneId" to text(100),"latitude" to decimal(-90.0,90.0),"longitude" to decimal(-180.0,180.0))),20),"cityAttribution" to text(1000)))
        "halo_clock_save" -> obj(mapOf("id" to id,"clock" to worldClock,"saved" to bool))
        "halo_clock_action" -> variants(mapOf("id" to id,"clock" to worldClock,"saved" to bool),listOf("id","clock","saved"),listOf("saved"))
        "halo_source_query" -> obj(mapOf("sources" to array(source,100)))
        "halo_source_get" -> obj(sourceFields+mapOf("content" to nullable(content),"calendarItems" to integer(0,100)),sourceRequired+listOf("content","calendarItems"))
        "halo_source_preview" -> obj(mapOf("validated" to bool,"saved" to bool,"title" to text(200),"endpoint" to text(4096),"result" to text(4000)),listOf("validated","saved","title","result"))
        "halo_source_save" -> obj(sourceFields+mapOf("saved" to bool,"fetched" to bool),sourceRequired+listOf("saved","fetched"))
        "halo_source_action" -> variants(sourceFields+mapOf("admitted" to bool,"available" to bool,"cancellationRequested" to bool,"cleared" to bool,"removed" to bool),sourceRequired+"admitted",listOf("available"),listOf("cancellationRequested"),listOf("cleared"),listOf("removed"))
        "halo_brief_query" -> variants(briefDetailFields+mapOf("briefs" to array(briefSummary,30),"settings" to settings),briefDetailFields.keys.toList(),listOf("briefs","settings"))
        "halo_brief_action" -> variants(briefDetailFields+mapOf("saved" to bool,"fetched" to bool,"playbackRequested" to bool,"audible" to bool,"completed" to bool,
            "stopRequested" to bool,"removed" to bool,"cleared" to bool),briefDetailFields.keys.toList()+listOf("saved","fetched"),listOf("playbackRequested","audible","completed"),listOf("stopRequested"),listOf("removed"),listOf("cleared"))
        "halo_brief_settings" -> obj(mapOf("settings" to settings,"saved" to bool))
        "halo_everyday_open" -> obj(mapOf("dispatched" to bool,"viewed" to bool))
        "halo_widget_query" -> obj(mapOf("instances" to array(obj(mapOf("id" to integer(1,Int.MAX_VALUE.toLong()),"version" to integer(),"config" to config)),100),
            "omittedInstances" to integer(),"pinSupported" to bool))
        "halo_widget_configure" -> obj(mapOf("id" to integer(1,Int.MAX_VALUE.toLong()),"version" to version,"saved" to bool,"painted" to bool,"humanViewed" to bool))
        "halo_widget_pin" -> obj(mapOf("requested" to bool,"placed" to bool,"receipt" to choice(listOf("REQUESTED"))))
        else -> error("Missing Everyday result contract: $name")
    }

    fun operation(name: String): ToolOperation = when(name) {
        in EverydayToolContracts.queries -> ToolOperation.QUERY
        "halo_organizer_create","halo_organizer_alarm","halo_organizer_calendar_import" -> ToolOperation.CREATE
        "halo_organizer_calendar_export" -> ToolOperation.EXPORT
        "halo_everyday_open","halo_widget_pin","halo_source_preview" -> ToolOperation.PRESENT
        else -> ToolOperation.EDIT
    }
    fun identityFields(name: String): Set<String> = when(name) {
        "halo_organizer_calendar_preview","halo_organizer_calendar_export" -> setOf("objectId")
        "halo_organizer_calendar_import" -> setOf("added","alreadyPresent")
        "halo_organizer_delivery" -> setOf("occurrenceKey")
        "halo_organizer_get","halo_organizer_create","halo_organizer_alarm","halo_organizer_edit","halo_organizer_action","halo_organizer_occurrence",
        "halo_organizer_clock_action","halo_clock_save","halo_clock_action","halo_source_get","halo_source_save","halo_brief_query","halo_brief_action","halo_widget_configure" -> setOf("id")
        else -> emptySet()
    }
    private fun value(raw: Any?): Any? = when(raw) {
        null,JSONObject.NULL -> null
        is JSONObject -> raw.keys().asSequence().associateWith {value(raw.get(it))}
        is JSONArray -> (0 until raw.length()).map {value(raw.get(it))}
        else -> raw
    }
    fun validate(name: String,result: JSONObject) = EverydayToolContracts.validate(value(result),schema(name))
}
