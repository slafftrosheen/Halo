package com.halo.app.di.tools

import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.host.android.capability.resolveOccurrence
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.presentation.*
import com.halo.app.data.everyday.OrganizerCodec
import com.halo.app.data.everyday.BriefCodec
import com.halo.app.data.everyday.CollectorCodec
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import org.json.*
import java.time.*
import java.util.UUID

private class OrganizerTimeClarification(message: String): IllegalArgumentException(message)

/** Runtime adapter only: repositories/services remain the sole mutation, scheduling and network owners. */
class EverydayToolExecutor(
    private val organizer: OrganizerUseCases,
    private val clocks: WorldClockRepository,
    private val cities: OfflineCityCatalogue,
    private val sources: CollectorRepository,
    private val coordinator: CollectorCoordinator,
    private val preview: CollectorPreview,
    private val briefs: BriefRepository,
    private val briefService: EverydayBriefService,
    private val readBrief: suspend (String)->Unit,
    private val stopReading: ()->Unit,
    private val clearSource: suspend (String,Long)->Unit,
    private val widgetAction: suspend (String,Map<String,Any?>)->JSONObject,
    private val open: suspend (EverydayTarget)->Boolean,
    private val canAccess: ()->Boolean,
    private val kids: ()->Boolean,
    private val authority: ()->(()->Unit),
    private val elapsedMs: ()->Long,
    private val bootId: ()->String,
    private val delivery: OrganizerDeliveryCoordinator,
    private val calendars: com.halo.app.data.everyday.OrganizerCalendarService,
    private val clock: Clock = Clock.systemUTC(),
    private val power: ()->Boolean = {false},
    private val ready: suspend ()->Unit = {},
    private val readState: (suspend ()->Any)? = null,
) : ToolExecutor {
    override fun definitions()=EverydayToolContracts.coreDefinitions
    override suspend fun execute(toolCall: ToolCall)=execute(toolCall,ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall,context: ToolExecutionContext): ToolExecutionResult = withContext(Dispatchers.IO) {
        var mutationStarted=false
        fun rejected(kind: ToolRejectionKind)=ToolExecutionResult.Rejected(toolCall.id,toolCall.functionName,
            "Everyday action unavailable or changed. Read the exact current record/source/configuration and review again; never replay an uncertain mutation.",kind)
        try {
            val definition=definitions().find {it.name==toolCall.functionName} ?: return@withContext rejected(ToolRejectionKind.TOOL_UNKNOWN)
            EverydayToolContracts.validate(toolCall.arguments,definition.parameters)
            ready()
            if(!canAccess()) return@withContext rejected(ToolRejectionKind.PERMISSION_REQUIRED)
            val check=authority();check()
            val child=kids()
            val a=toolCall.arguments
            val childAction=toolCall.functionName=="halo_everyday_open" || toolCall.functionName=="halo_brief_action" && a["action"] in setOf("BUILD","READ","STOP")
            if(child && (toolCall.functionName in setOf("halo_widget_query","halo_organizer_calendar_preview") || toolCall.functionName !in EverydayToolContracts.queries && !childAction)) return@withContext rejected(ToolRejectionKind.PERMISSION_REQUIRED)
            if(toolCall.functionName !in EverydayToolContracts.queries && context.interactionId.isNullOrBlank()) return@withContext rejected(ToolRejectionKind.PERMISSION_REQUIRED)
            val disclosure=if(toolCall.functionName in EverydayToolContracts.queries || toolCall.functionName=="halo_brief_action" && a["action"] in setOf("BUILD","READ"))
                com.halo.app.everyday.EverydayReadAuthority.capture() else null
            val canonical=if(disclosure!=null) readDisclosureState() else null
            val rawResult=EverydayWriteAuthority.run(check) {
                // Registry descriptors authorize execution and produce content-free traces. This adapter never parses chat text.
                mutationStarted=toolCall.functionName !in EverydayToolContracts.queries && toolCall.functionName !in setOf("halo_source_preview","halo_everyday_open")
                dispatch(toolCall.functionName,a,child)
            }
            if (a["version"] != null && rawResult.has("version") && toolCall.functionName in EverydayToolContracts.queries)
                require((a["version"] as Number).toLong() == rawResult.getLong("version"))
            val result = if (toolCall.functionName in EverydayToolContracts.queries)
                EverydayToolReadback.project(toolCall.functionName, a, rawResult, "$canonical|$disclosure") else EverydayToolReadback.receipt(toolCall.functionName, rawResult)
            val encoded=result.toString()
            check(encoded.length<=128000) {"Everyday result exceeds the declared output bound"}
            check(runCatching {EverydayResultContracts.validate(toolCall.functionName,result)}.isSuccess) {"Everyday result contract changed"}
            if(disclosure!=null) check(readDisclosureState()==canonical) {"Everyday disclosure authority changed"}
            check();ensureActive()
            disclosure?.let(com.halo.app.everyday.EverydayReadAuthority::requireCurrent)
            ToolExecutionResult.Success(toolCall.id,toolCall.functionName,encoded)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            when {
                error is OrganizerTimeClarification -> ToolExecutionResult.Rejected(toolCall.id,toolCall.functionName,
                    error.message ?: "Choose an exact alarm time",ToolRejectionKind.INVALID_ARGUMENTS)
                error is OrganizerConflict || error is CollectorConflict -> rejected(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                error is IllegalArgumentException -> rejected(ToolRejectionKind.INVALID_ARGUMENTS)
                mutationStarted -> ToolExecutionResult.Failed(toolCall.id,toolCall.functionName,
                    "Everyday outcome is unknown. Read the exact current state before continuing; do not repeat this mutation.",completionUnknown=true,code="EVERYDAY_UNCERTAIN")
                else -> rejected(ToolRejectionKind.RESOURCE_UNAVAILABLE)
            }
        }
    }

    private suspend fun readDisclosureState(): Any = readState?.invoke() ?: Triple(briefs.settings().version,
        sources.states().map {Triple(it.source.id,it.source.version,it.snapshot?.contentRevision)}.sortedBy {it.first},
        organizer.repository.records(true).map {it.id to it.version}.sortedBy {it.first})

    private suspend fun record(id: String,child: Boolean): OrganizerRecord {
        val value=requireNotNull(organizer.repository.get(id))
        if(child) require(value.deletedAt==null && id in briefs.settings().childRecordIds)
        return value
    }
    private suspend fun source(id: String,version: Long,child: Boolean,content: Boolean=false): CollectorState {
        val value=requireNotNull(sources.state(id));if(value.source.version!=version) throw CollectorConflict("Source changed")
        if(child) require(value.source.childApproved && (value.source.external || value.source.kind == CollectorKind.LOCAL_LIBRARY))
        if(content || child) require(value.source.selected && value.source.enabled && !value.source.muted)
        return value
    }
    private suspend fun dispatch(name: String,a: Map<String,Any?>,child: Boolean): JSONObject {
        fun id()=a.string("id")
        fun version()=a.long("version")
        return when(name) {
            "halo_organizer_query" -> {
                val offset=a.optionalLong("offset")?.toInt() ?: 0
                val kinds=a.optionalString("kind")?.let {setOf(OrganizerKind.valueOf(it))} ?: OrganizerKind.entries.toSet()
                val grants=if(child) briefs.settings().childRecordIds else null
                if(a.containsKey("from") || a.containsKey("until")) {
                    val from=Instant.parse(a.string("from"));val until=Instant.parse(a.string("until"))
                    require(until>from && Duration.between(from,until).toDays()<=366)
                    val rows=organizer.agenda(from,until,kinds).filter {grants==null || it.record.id in grants}
                    EverydayToolReadback.pageRows(rows.map { occurrence(it) }, "occurrences", a)
                } else {
                    val rows=organizer.search(a.optionalString("query").orEmpty()).filter {it.kind in kinds && (grants==null || it.id in grants)}
                        .map {if(child) it.copy(parentId=it.parentId?.takeIf {id -> id in grants.orEmpty()}) else it}
                    EverydayToolReadback.pageRows(rows.map { summary(it) }, "records", a)
                }
            }
            "halo_organizer_get" -> {
                val value=record(id(),child)
                val overrides=organizer.repository.overrides(value.id)
                val disclosed=if(child) value.copy(links=emptyList(),parentId=value.parentId?.takeIf {it in briefs.settings().childRecordIds}) else value
                JSONObject().put("id",value.id).put("version",value.version).put("record",JSONObject(OrganizerCodec.encode(disclosed)).apply { if (child) remove("commitment") })
                    .put("overrides",JSONArray(overrides.map { JSONObject().put("originalStart",it.originalStart.toString()).put("disposition",it.disposition.name)
                        .put("version",it.version).put("title",it.replacementTitle).put("replacementStart",it.replacementTime?.start?.toString()) }))
                    .put("omittedOverrides",0)
                    .put("deliveries",JSONArray(organizer.repository.receipts(value.id).sortedWith(compareByDescending<DeliveryReceipt> { it.updatedAt }.thenBy { it.occurrenceKey }).map { receipt(it) }))
                    .apply {a.optionalString("originalStart")?.let {at -> put("occurrence",occurrence(requireNotNull(OrganizerRecurrence.exact(value,overrides,Instant.parse(at),ZoneId.systemDefault()))))}}
            }
            "halo_organizer_create" -> {
                val p=a.map("record");val kind=OrganizerKind.valueOf(p.string("kind"))
                val value=organizer.create(kind,p.string("title"),p.optionalString("details").orEmpty(),p.optionalMap("time")?.let(::time),p.optionalMap("repeat")?.let(::repeat),
                    p.optionalString("priority")?.let(TaskPriority::valueOf) ?: TaskPriority.NORMAL,p.optionalString("parentId"),p.optionalLong("notifyBeforeMinutes")?.toInt(),
                    p.optionalLong("timerDurationMs"),links(p),p.optionalString("sourceUrl"),p.optionalString("sourceLabel"),p.optionalString("location").orEmpty())
                saved(value)
            }
            "halo_organizer_alarm" -> {
                val zone=ZoneId.systemDefault()
                val now=clock.instant()
                val label=a.optionalString("label") ?: "Alarm"
                val days=(a["repeatDays"] as? List<*>)?.map {(it as Number).toInt()}.orEmpty()
                require(days.distinct()==days)
                val resolved=a.optionalMap("resolvedTime")?.let {time -> when(time.string("type")) {
                    "CLOCK" -> {
                        require(time.keys.all {it in setOf("type","time","dayRelation")})
                        com.halo.cognition.language.TemporalValue.TimeOfDay(LocalTime.parse(time.string("time")),
                            time.optionalString("dayRelation")?.let(com.halo.cognition.language.DayRelation::valueOf))
                    }
                    else -> {require(time.keys==setOf("type","instant"));com.halo.cognition.language.TemporalValue.InstantValue(Instant.parse(time.string("instant")))}
                }}
                val plan=com.halo.host.android.capability.AlarmCommandResolver(clock,zone).resolve(resolved,a.string("timeExpression"),label)
                var at=if(resolved is com.halo.cognition.language.TemporalValue.TimeOfDay) resolved.resolveOccurrence(clock,zone) else when(plan) {
                    is com.halo.host.android.capability.AlarmExecutionPlan.SystemAlarm -> {
                        val time=LocalTime.of(plan.hour,plan.minute)
                        val today=now.atZone(zone).toLocalDate().atTime(time).atZone(zone)
                        if(today.toInstant()>now) today else today.plusDays(1)
                    }
                    is com.halo.host.android.capability.AlarmExecutionPlan.HaloExactAlarm -> plan.triggerAt.atZone(zone)
                    is com.halo.host.android.capability.AlarmExecutionPlan.Clarification -> throw OrganizerTimeClarification(plan.prompt)
                }
                require(at.toInstant()>now && at.toLocalDateTime().atZone(zone).toInstant()==at.toInstant())
                val weekdays=days.map {DayOfWeek.of((it+5)%7+1)}.toSet()
                if(weekdays.isNotEmpty()) while(at.dayOfWeek !in weekdays) at=at.plusDays(1)
                saved(organizer.create(OrganizerKind.ALARM,label,time=OrganizerTime(at.toLocalDateTime(),zoneId=zone.id,followDeviceZone=true),
                    repeat=weekdays.takeIf {it.isNotEmpty()}?.let {RepeatRule(RepeatFrequency.WEEKLY,weekdays=it)}))
            }
            "halo_organizer_clock_action" -> clockAction(a)
            "halo_organizer_calendar_preview" -> {
                val source=com.halo.app.data.workspace.WorkspaceFileVersion(a.string("objectId"),a.string("fileVersion"))
                val review=calendars.preview(source,ZoneId.of(a.string("zoneId")))
                val records = review.preview.records.map(::summary)
                val issues = review.preview.issues.map { issue -> JSONObject().put("item", issue.item)
                    .put("reasonCode", issue.reasonCode.name).put("arguments", JSONArray(issue.reasonArguments)).put("warning", issue.warning).put("seriesWithheld", issue.seriesWithheld) }
                EverydayToolReadback.pageCollections(linkedMapOf("records" to records, "issues" to issues), a)
                    .put("objectId",source.objectId).put("fileVersion",source.version).put("zoneId",a.string("zoneId"))
                    .put("reviewToken",review.token).put("recordCount",records.size).put("issueCount",issues.size)
            }
            "halo_organizer_calendar_import" -> {
                val result=calendars.import(com.halo.app.data.workspace.WorkspaceFileVersion(a.string("objectId"),a.string("fileVersion")),
                    ZoneId.of(a.string("zoneId")),a.string("reviewToken"),(a["recordIds"] as List<*>).filterIsInstance<String>())
                JSONObject().put("added",JSONArray(result.added)).put("alreadyPresent",JSONArray(result.alreadyPresent)).put("saved",true).put("deliveryConfirmed",false)
            }
            "halo_organizer_calendar_export" -> {
                val rows=a.maps("records");require(rows.isNotEmpty() && rows.map {it.string("id")}.distinct().size==rows.size)
                val file=calendars.prepareExport(rows.associate {it.string("id") to it.long("version")})
                JSONObject().put("objectId",file.objectId.value).put("fileVersion",file.version).put("name",file.displayName)
                    .put("mimeType",file.mimeType).put("saved",true).put("exportedExternally",false)
            }
            "halo_organizer_edit" -> {
                val value=organizer.requireCurrent(id(),version());val p=a.map("patch");require(p.isNotEmpty())
                val clear=(a["clear"] as? List<*>)?.filterIsInstance<String>().orEmpty().toSet();require(clear.intersect(p.keys).isEmpty())
                fun <T> field(key: String,current: T?,parse: ()->T): T? = if(key in clear) null else if(key in p) parse() else current
                val next=value.copy(title=p.optionalString("title") ?: value.title,details=p.optionalString("details") ?: value.details,
                    time=field("time",value.time){time(p.map("time"))},repeat=field("repeat",value.repeat){repeat(p.map("repeat"))},
                    parentId=field("parentId",value.parentId){p.string("parentId")},notifyBeforeMinutes=field("notifyBeforeMinutes",value.notifyBeforeMinutes){p.long("notifyBeforeMinutes").toInt()},
                    sourceUrl=field("sourceUrl",value.sourceUrl){p.string("sourceUrl")},sourceLabel=field("sourceLabel",value.sourceLabel){p.string("sourceLabel")},
                    priority=p.optionalString("priority")?.let(TaskPriority::valueOf) ?: value.priority,location=p.optionalString("location") ?: value.location,
                    links=if("links" in p) links(p) else value.links)
                saved(organizer.repository.save(next,value.version,a["resetOccurrences"]==true))
            }
            "halo_organizer_action" -> {
                val at=a.optionalString("originalStart")?.let(Instant::parse)
                val value=when(a.string("action")) {
                    "COMPLETE" -> organizer.completeTask(id(),version(),at)
                    "REOPEN" -> organizer.reopenTask(id(),version(),at)
                    "CANCEL" -> organizer.cancel(id(),version())
                    "TRASH" -> organizer.repository.delete(id(),version())
                    "RESTORE" -> organizer.repository.restore(id(),version())
                    "PAUSE" -> organizer.pauseTimer(id(),version())
                    "RESUME" -> organizer.resumeTimer(id(),version())
                    else -> organizer.stopTimer(id(),version())
                };saved(value)
            }
            "halo_organizer_occurrence" -> {
                val value=organizer.requireCurrent(id(),version());require(value.repeat!=null)
                val at=Instant.parse(a.string("originalStart"));val exceptions=organizer.repository.overrides(id())
                // Validate against authored slots, including a currently cancelled occurrence being reset.
                require(OrganizerRecurrence.contains(value,at,ZoneId.systemDefault()))
                val action=a.string("action")
                if(action=="RESET") saved(organizer.repository.removeOverride(id(),at,version()))
                else {
                    val prior=exceptions.find {it.originalStart==at}
                    val replacement=a.optionalMap("time")?.let(::time)
                    require(action!="REPLACE" || replacement!=null)
                    saved(organizer.repository.saveOverride(OccurrenceOverride(id(),at,when(action){"REPLACE" -> if(prior?.disposition==OccurrenceDisposition.COMPLETED) OccurrenceDisposition.COMPLETED else OccurrenceDisposition.REPLACED;"COMPLETE" -> OccurrenceDisposition.COMPLETED;else -> OccurrenceDisposition.CANCELLED},
                        replacement ?: prior?.replacementTime,a.optionalString("title") ?: prior?.replacementTitle,prior?.version ?: 1,
                        a.optionalString("details") ?: prior?.replacementDetails,a.optionalString("location") ?: prior?.replacementLocation,a.optionalString("sourceUrl") ?: prior?.sourceUrlOverride),version()))
                }
            }
            "halo_organizer_delivery" -> {
                organizer.requireCurrent(id(),version())
                val receipt=requireNotNull(organizer.repository.receipts(id()).find {it.occurrenceKey==a.string("occurrenceKey") && it.recordVersion==version()})
                if(a.string("action")=="SNOOZE") saved(delivery.snooze(receipt.occurrenceKey,version(),Duration.ofMinutes(a.long("minutes")),receipt.triggerAt)).put("occurrenceKey",receipt.occurrenceKey)
                else {
                    if(!delivery.dismiss(receipt.occurrenceKey,version(),receipt.triggerAt)) throw OrganizerConflict("This delivery is no longer active")
                    JSONObject().put("dismissed",true).put("occurrenceKey",receipt.occurrenceKey)
                }
            }
            "halo_clock_query" -> {
                val rows=if(child) emptyList() else clocks.all()
                JSONObject().put("phoneZoneId",ZoneId.systemDefault().id).put("clocks",JSONArray(rows.map {worldClock(it)}))
                    .put("cities",JSONArray(a.optionalString("query")?.let {cities.search(it,limit=20)}.orEmpty().map {city -> JSONObject().put("cityId",city.id).put("label",city.name).put("countryCode",city.countryCode).put("zoneId",city.zoneId).put("latitude",city.latitude).put("longitude",city.longitude)}))
                    .put("cityAttribution",OfflineCityCatalogue.ATTRIBUTION)
            }
            "halo_clock_save" -> {
                require(a.containsKey("id")==a.containsKey("version"))
                val value=WorldClockRecord(a.optionalString("id") ?: UUID.randomUUID().toString(),a.string("label"),a.string("zoneId"))
                JSONObject().put("clock",worldClock(clocks.save(value,a.optionalLong("version")))).put("id",value.id).put("saved",true)
            }
            "halo_clock_action" -> {
                when(a.string("action")) {
                    "REMOVE" -> JSONObject().put("clock",worldClock(clocks.remove(id(),version()))).put("id",id()).put("saved",true)
                    "RESTORE" -> JSONObject().put("clock",worldClock(clocks.restore(id(),version()))).put("id",id()).put("saved",true)
                    else -> {val rows=a.maps("clocks");require(rows.map {it.string("id")}.distinct().size==rows.size)
                        clocks.reorder(rows.map {it.string("id")},rows.associate {it.string("id") to it.long("version")});JSONObject().put("saved",true)}
                }
            }
            "halo_source_query" -> EverydayToolReadback.pageRows(sources.states().filter { (!child || it.source.childApproved && (it.source.external || it.source.kind == CollectorKind.LOCAL_LIBRARY) && it.source.selected && it.source.enabled && !it.source.muted) &&
                (a["selectedOnly"] != true || it.source.selected) && (a["category"] == null || it.source.category.name == a["category"]) && (a["language"] == null || it.source.language == a["language"]) }.sortedBy { it.source.id }.map { sourceSummary(it) }, "sources", a)
            "halo_source_get" -> {
                val value=source(id(),version(),child,true);val cached=value.snapshot?.takeIf {it.sourceVersion==value.source.version}
                require(a["contentRevision"]==null || cached?.contentRevision==a["contentRevision"])
                val body=when(val payload=cached?.payload) {
                    is CollectorPayload.Articles -> CollectorPayload.Articles(if(a["articleId"]!=null) listOf(requireNotNull(payload.items.find {it.id==a["articleId"]})) else payload.items)
                    is CollectorPayload.Local -> payload
                    is CollectorPayload.Warnings -> payload
                    is CollectorPayload.Calendar -> payload // Read-only evidence; importing remains an explicit reviewed operation.
                    else -> payload
                }
                sourceSummary(value).put("content",body?.let {JSONObject(CollectorCodec.encode(it))} ?: JSONObject.NULL)
                    .put("calendarItems",(cached?.payload as? CollectorPayload.Calendar)?.preview?.records?.size ?: 0)
            }
            "halo_source_preview" -> {
                val value=preview.inspect(a.string("url"),CollectorKind.valueOf(a.string("kind")),a.string("language"))
                JSONObject().put("validated",value.result is CollectorFetchResult.Updated).put("saved",false).put("title",value.candidate.title)
                    .put("endpoint",value.candidate.endpoint).put("result",when(val result=value.result){is CollectorFetchResult.Unavailable -> result.reason;else -> "Preview available"})
            }
            "halo_source_save" -> {
                val p=a.map("source");require(a.containsKey("id")==a.containsKey("version"))
                val old=a.optionalString("id")?.let {source(it,version(),false).source}
                val kind=p.optionalString("kind")?.let(CollectorKind::valueOf) ?: requireNotNull(old).kind
                require(kind in setOf(CollectorKind.FEED,CollectorKind.ALERTS,CollectorKind.PUBLIC_CALENDAR,CollectorKind.WEATHER) || old!=null && "kind" !in p)
                val place=p.optionalMap("place")?.let {WeatherPlace(it.string("name"),(it["latitude"] as Number).toDouble(),(it["longitude"] as Number).toDouble(),it.string("zoneId"))} ?: old?.place
                val endpoint=if(kind==CollectorKind.WEATHER) OpenMeteoParser.endpoint(requireNotNull(place)) else p.optionalString("endpoint") ?: old?.endpoint
                endpoint?.let(CollectorUrlPolicy::validate)
                val next=CollectorSource(old?.id ?: "custom:${UUID.randomUUID()}",p.optionalString("title") ?: requireNotNull(old).title,
                    p.optionalString("publisher") ?: old?.publisher ?: "User-selected source",kind,p.optionalString("category")?.let(SourceCategory::valueOf) ?: old?.category ?: SourceCategory.CUSTOM,
                    p.optionalString("language") ?: old?.language ?: "en",endpoint,old?.attributionUrl,p["selected"] as? Boolean ?: old?.selected ?: false,
                    p["enabled"] as? Boolean ?: old?.enabled ?: true,p["muted"] as? Boolean ?: old?.muted ?: false,p.optionalLong("refreshMinutes")?.toInt() ?: old?.refreshMinutes ?: 60,
                    place,p["childApproved"] as? Boolean ?: old?.childApproved ?: false,old?.version ?: 1,old?.research)
                val saved=sources.save(next,old?.version)
                sourceSummary(requireNotNull(sources.state(saved.id))).put("saved",true).put("fetched",false)
            }
            "halo_source_action" -> {
                val value=source(id(),version(),child)
                when(a.string("action")) {
                    "REFRESH" -> {require(value.source.selected && value.source.enabled);val receipt=coordinator.refreshDetailed(id(),manual=true,expectedVersion=value.source.version)
                        receipt.state?.let(::sourceSummary)?.put("admitted",receipt.admitted) ?: JSONObject().put("available",false)}
                    "CANCEL" -> {coordinator.cancel(id());JSONObject().put("cancellationRequested",true)}
                    "CLEAR_CACHE" -> {clearSource(id(),version());JSONObject().put("cleared",true)}
                    else -> {require(value.source.external || value.source.kind == CollectorKind.LOCAL_LIBRARY);sources.remove(id(),version());JSONObject().put("removed",true)}
                }
            }
            "halo_brief_query" -> {
                val value=a.optionalString("id")?.let {requireNotNull(briefService.visible(it))}
                if(value!=null) brief(value)
                else JSONObject().put("briefs",JSONArray(briefs.recent(30).filter {(!child || it.audience==BriefAudience.KIDS) && briefService.visible(it.id)!=null}.map {briefSummary(it)}))
                    .put("settings",if(child) JSONObject().put("sharedRecordCount",briefs.settings().childRecordIds.size) else JSONObject(BriefCodec.encode(briefs.settings())))
            }
            "halo_brief_action" -> when(a.string("action")) {
                "BUILD" -> brief(briefService.build(a.optionalString("period")?.let(BriefPeriod::valueOf) ?: BriefPeriod.ON_DEMAND,
                    if(child) BriefAudience.KIDS else a.optionalString("audience")?.let(BriefAudience::valueOf) ?: BriefAudience.EVERYDAY)).put("saved",true).put("fetched",false)
                "READ" -> {val value=requireNotNull(briefService.visible(id()));readBrief(value.id);JSONObject().put("playbackRequested",true).put("audible",false).put("completed",false)}
                "STOP" -> {stopReading();JSONObject().put("stopRequested",true)}
                "REMOVE" -> {briefs.remove(id());JSONObject().put("removed",true)}
                else -> {briefs.clear();JSONObject().put("cleared",true)}
            }
            "halo_brief_settings" -> {
                val old=briefs.settings();check(old.version==version());val p=a.map("settings");require(p.isNotEmpty())
                fun at(key: String,original: LocalTime)=p.optionalString(key)?.let(LocalTime::parse) ?: original
                fun ids(key: String,current: Set<String>)=if(key in p) (p[key] as List<*>).filterIsInstance<String>().toSet() else current
                val next=old.copy(proactive=p["proactive"] as? Boolean ?: old.proactive,morning=at("morning",old.morning),evening=at("evening",old.evening),
                    quietEnabled=p["quietEnabled"] as? Boolean ?: old.quietEnabled,quietStart=at("quietStart",old.quietStart),quietEnd=at("quietEnd",old.quietEnd),
                    headlineLimit=p.optionalLong("headlineLimit")?.toInt() ?: old.headlineLimit,taskLimit=p.optionalLong("taskLimit")?.toInt() ?: old.taskLimit,
                    pinnedRecordIds=ids("pinnedRecordIds",old.pinnedRecordIds),childRecordIds=ids("childRecordIds",old.childRecordIds), sections = if ("sections" in p) (p["sections"] as List<*>).map { BriefSection.valueOf(it as String) }.toSet() else old.sections)
                require((next.pinnedRecordIds+next.childRecordIds).all {organizer.repository.get(it)?.deletedAt==null && organizer.repository.get(it)!=null})
                JSONObject().put("settings",JSONObject(BriefCodec.encode(briefs.saveSettings(next,old.version)))).put("saved",true)
            }
            "halo_everyday_open" -> {
                val target=when(a.string("instrument")) {
                    "ORGANIZER" -> if(a["id"]==null) EverydayTarget.Organizer else {val row=record(id(),child);require(row.deletedAt==null && row.version==version());EverydayTarget.Record(row.id,row.version,a.optionalString("originalStart")?.let(Instant::parse))}
                    "LIBRARIES" -> if(a["id"] == null) EverydayTarget.Libraries else {
                        val row = source(id(), version(), child, true); require(row.source.kind == CollectorKind.LOCAL_LIBRARY)
                        EverydayTarget.LibraryEntry(id(), version(), a.string("generation"), a.string("entryId"))
                    }
                    "SOURCE" -> {val row=source(id(),version(),child,true);EverydayTarget.Source(row.source.id,row.source.version,row.snapshot?.contentRevision,a.optionalString("articleId"))}
                    "BRIEF" -> if(a["id"]==null) EverydayTarget.BriefLatest else EverydayTarget.Brief(requireNotNull(briefService.visible(id())).id)
                    else -> if(a["zoneId"]!=null) EverydayTarget.Clock(ZoneId.of(a.string("zoneId")).id,a.optionalString("label").orEmpty()) else EverydayTarget.Clocks
                }
                JSONObject().put("dispatched",open(target)).put("viewed",false)
            }
            else -> widgetAction(name,a)
        }
    }
    private suspend fun clockAction(a: Map<String,Any?>): JSONObject {
        val action=a.string("action")
        val kind=if(action=="DISMISS_EXPIRED_TIMERS") OrganizerKind.TIMER else OrganizerKind.ALARM
        val records=organizer.repository.records().filter {it.kind==kind && it.status==OrganizerStatus.ACTIVE && it.deletedAt==null}
        val ringing=records.flatMap {record -> organizer.repository.receipts(record.id).filter {
            it.recordVersion==record.version && it.state in setOf(DeliveryState.FIRED,DeliveryState.DELIVERING)
        }}
        if(action=="SNOOZE_ALARM") {
            require(ringing.size==1) {"Choose one exact ringing Halo alarm"}
            val receipt=ringing.single()
            return saved(delivery.snooze(receipt.occurrenceKey,receipt.recordVersion,Duration.ofMinutes(a.optionalLong("minutes") ?: 10),receipt.triggerAt))
        }
        if(ringing.isNotEmpty()) {
            require(if(kind==OrganizerKind.TIMER) ringing.size<=20 else ringing.size==1) {"Choose exact Halo alert occurrences"}
            val dismissed=mutableListOf<String>()
            try {ringing.forEach {receipt ->
                if(!delivery.dismiss(receipt.occurrenceKey,receipt.recordVersion,receipt.triggerAt)) throw OrganizerConflict("Delivery changed")
                dismissed+=receipt.occurrenceKey
            }} catch(cancelled: CancellationException) {throw cancelled}
              catch(error: Exception) {
                if(dismissed.isNotEmpty()) throw OrganizerDeliveryUncertain(error)
                throw error
            }
            return JSONObject().put("dismissedOccurrences",JSONArray(dismissed)).put("dismissedCount",dismissed.size)
        }
        if(action=="DISMISS_EXPIRED_TIMERS") return JSONObject().put("dismissedOccurrences",JSONArray()).put("dismissedCount",0)
        val now=clock.instant()
        val next=organizer.agenda(now,now.plusSeconds(366L*86400),setOf(OrganizerKind.ALARM))
            .filter {it.record.status==OrganizerStatus.ACTIVE && !it.completed}
        require(next.isNotEmpty() && next.count {it.start==next.first().start}==1) {"Choose one exact upcoming Halo alarm"}
        val occurrence=next.first()
        return saved(if(occurrence.record.repeat==null) organizer.cancel(occurrence.record.id,occurrence.record.version)
            else organizer.repository.saveOverride(OccurrenceOverride(occurrence.record.id,occurrence.originalStart,OccurrenceDisposition.CANCELLED),occurrence.record.version))
    }
    private fun summary(r: OrganizerRecord)=JSONObject().put("id",r.id).put("version",r.version).put("kind",r.kind.name).put("title",r.title).put("status",r.status.name)
        .put("priority",r.priority.name).put("start",r.time?.start?.toString()).put("zoneId",r.time?.zoneId).put("repeating",r.repeat!=null)
    private fun saved(r: OrganizerRecord)=JSONObject().put("id",r.id).put("version",r.version).put("record",summary(r)).put("saved",true).put("deliveryConfirmed",false)
    private fun occurrence(o: OrganizerOccurrence)=summary(o.record).put("originalStart",o.originalStart.toString()).put("startInstant",o.start.toString()).put("endInstant",o.end?.toString()).put("completed",o.completed)
    private fun receipt(r: DeliveryReceipt)=JSONObject().put("occurrenceKey",r.occurrenceKey).put("recordVersion",r.recordVersion).put("triggerAt",r.triggerAt.toString()).put("state",r.state.name).put("updatedAt",r.updatedAt.toString()).put("snoozedUntil",r.snoozedUntil?.toString())
    private fun worldClock(r: WorldClockRecord)=JSONObject().put("id",r.id).put("version",r.version).put("label",r.label).put("zoneId",r.zoneId).put("position",r.position).put("deleted",r.deletedAt!=null)
    private fun sourceSummary(s: CollectorState)=JSONObject().put("id",s.source.id).put("version",s.source.version).put("title",s.source.title).put("publisher",s.source.publisher)
        .put("kind",s.source.kind.name).put("category",s.source.category.name).put("language",s.source.language).put("selected",s.source.selected).put("enabled",s.source.enabled)
        .put("muted",s.source.muted).put("childApproved",s.source.childApproved).put("refreshMinutes",s.source.refreshMinutes).put("contentRevision",s.snapshot?.contentRevision)
        .put("retrievedAt",s.snapshot?.retrievedAt?.toString()).put("expiresAt",s.snapshot?.expiresAt?.toString()).put("attempt",s.attempt.state.name)
        .put("attemptedAt",s.attempt.at?.toString()).put("nextAllowedAt",s.attempt.nextAllowedAt?.toString()).put("stale",s.snapshot?.stale(clock.instant(),s.source.external,power()) ?: true)
        .put("mode",if(power()) "Power" else "Local").put("cached",s.snapshot!=null)
        .put("researchQuery",s.source.research?.query).put("researchProvider",s.source.research?.providerId)
    private fun briefSummary(b: EverydayBrief)=JSONObject().put("id",b.id).put("period",b.period.name).put("audience",b.audience.name).put("date",b.date.toString()).put("zoneId",b.zoneId).put("generatedAt",b.generatedAt.toString())
    private fun brief(b: EverydayBrief)=briefSummary(b).put("facts",JSONArray(b.facts.take(30).map {f -> JSONObject().put("id",f.id).put("kind",f.kind.name).put("title",f.title.take(200)).put("detail",f.detail.take(400)).put("sourceId",f.sourceId).put("revision",f.revision).put("at",f.at?.toString()).put("url",f.externalUrl)}))
        .put("sources",JSONArray(b.sources.map {s -> JSONObject().put("id",s.id).put("version",s.version).put("publisher",s.publisher).put("retrievedAt",s.retrievedAt?.toString()).put("expiresAt",s.expiresAt?.toString()).put("attempt",s.attempt.name)}))
        .put("baselineMissing",b.baselineMissing).put("changes",JSONArray(b.changes.take(30).map {c -> JSONObject().put("kind",c.kind.name).put("factId",c.factId).put("sourceId",c.sourceId)}))
    private fun time(p: Map<String,Any?>)=OrganizerTime(LocalDateTime.parse(p.string("start")),p.optionalString("end")?.let(LocalDateTime::parse),p.string("zoneId"),p["allDay"]==true,p["followDeviceZone"]==true).also {it.validate()}
    private fun repeat(p: Map<String,Any?>)=RepeatRule(RepeatFrequency.valueOf(p.string("frequency")),p.optionalLong("interval")?.toInt() ?: 1,
        (p["weekdays"] as? List<*>)?.map {DayOfWeek.valueOf(it as String)}?.toSet().orEmpty(),p.optionalLong("count")?.toInt(),p.optionalString("until")?.let(Instant::parse))
    private fun links(p: Map<String,Any?>)=p.optionalMaps("links").map {OrganizerLink(it.string("objectId"),it.optionalString("relation") ?: "RELATED_TO")}
}

internal fun Map<String,Any?>.string(key: String)=get(key) as? String ?: throw IllegalArgumentException("Missing $key")
internal fun Map<String,Any?>.optionalString(key: String)=get(key) as? String
internal fun Map<String,Any?>.long(key: String)=(get(key) as? Number)?.toLong() ?: throw IllegalArgumentException("Missing $key")
internal fun Map<String,Any?>.optionalLong(key: String)=(get(key) as? Number)?.toLong()
@Suppress("UNCHECKED_CAST") internal fun Map<String,Any?>.map(key: String)=get(key) as? Map<String,Any?> ?: throw IllegalArgumentException("Missing $key")
@Suppress("UNCHECKED_CAST") internal fun Map<String,Any?>.optionalMap(key: String)=get(key) as? Map<String,Any?>
@Suppress("UNCHECKED_CAST") internal fun Map<String,Any?>.maps(key: String)=(get(key) as? List<Map<String,Any?>>) ?: throw IllegalArgumentException("Missing $key")
@Suppress("UNCHECKED_CAST") internal fun Map<String,Any?>.optionalMaps(key: String)=(get(key) as? List<Map<String,Any?>>).orEmpty()
