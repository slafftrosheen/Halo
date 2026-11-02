package com.halo.app.backup

import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.*
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.briefing.*
import com.squareup.moshi.JsonClass
import org.json.*
import java.time.*

@JsonClass(generateAdapter=true)
data class EverydayArchiveEntry(val kind: String,val id: String,val body: String,val schema: Int=1)

/** Portable domain facts, not Android permissions, running jobs, launcher IDs or live delivery claims. */
internal object EverydayArchive {
    private val kinds=setOf("ORGANIZER","OCCURRENCE","DELIVERY","SOURCE","CLOCK","BRIEF","BRIEF_SETTINGS","SCREEN")
    fun key(entry: EverydayArchiveEntry)="everyday:${entry.kind}:${entry.id}"
    private fun row(kind: String,id: String,value: String)=EverydayArchiveEntry(kind,id,value)
    suspend fun snapshot(db: AppDatabase,screen: String? = null): List<EverydayArchiveEntry> = buildList {
        screen?.let {add(row("SCREEN","1",it))}
        db.organizerDao().records(true).forEach {record ->
            add(row("ORGANIZER",record.id,record.body))
            db.organizerDao().overrides(record.id).forEach {add(row("OCCURRENCE","${it.recordId}@${it.originalStart}",it.body))}
            db.organizerDao().receipts(record.id).forEach {r ->add(row("DELIVERY",r.occurrenceKey,JSONObject().put("key",r.occurrenceKey).put("recordId",r.recordId)
                .put("version",r.recordVersion).put("triggerAt",r.triggerAt).put("state",r.state).put("updatedAt",r.updatedAt).toString()))}
        }
        db.collectorDao().sources().forEach {source ->
            val snapshot=db.collectorDao().snapshot(source.id)?.takeIf {it.sourceVersion==source.version}
            add(row("SOURCE",source.id,JSONObject().put("source",source.body).put("snapshot",snapshot?.let {s -> JSONObject()
                .put("revision",s.contentRevision).put("retrievedAt",s.retrievedAt).put("verifiedAt",s.verifiedAt).put("expiresAt",s.expiresAt)
                .put("payload",s.payload).put("etag",s.etag).put("lastModified",s.lastModified).put("effectiveUrl",s.effectiveUrl)}).toString()))
        }
        db.worldClockDao().all().forEach {c ->add(row("CLOCK",c.id,JSONObject().put("id",c.id).put("label",c.label).put("zoneId",c.zoneId)
            .put("cityId",c.cityId).put("position",c.position).put("version",c.version).toString()))}
        db.briefDao().recent(60).forEach {add(row("BRIEF",it.id,it.body))}
        db.briefDao().settings()?.let {add(row("BRIEF_SETTINGS","1",it.body))}
    }.also(::validate)

    fun validate(entries: List<EverydayArchiveEntry>) {
        require(entries.size<=100000 && entries.distinctBy(::key).size==entries.size) {"Invalid Everyday archive identities"}
        require(entries.sumOf {it.body.toByteArray(Charsets.UTF_8).size.toLong()}<=128L*1024*1024)
        entries.forEach {require(it.schema==1 && it.kind in kinds && it.id.length in 1..450 && it.body.length<=8*1024*1024);json(it.body)}
        val records=entries.filter {it.kind=="ORGANIZER"}.associate {it.id to OrganizerCodec.decode(it.body)}
        require(records.size<=10000 && entries.count {it.kind=="BRIEF"}<=60)
        val sources=entries.filter {it.kind=="SOURCE"}.associate {it.id to CollectorCodec.source(json(it.body).getString("source"))}
        require(sources.size<=CollectorRefreshPolicy.MAX_SOURCE_COUNT)
        entries.forEach {entry ->
            require(entry.schema==1 && entry.kind in kinds && entry.id.length in 1..450 && entry.body.length<=8*1024*1024)
            when(entry.kind) {
                "ORGANIZER" -> {val r=records.getValue(entry.id);require(r.id==entry.id && r.version<Long.MAX_VALUE);r.validate()
                    var parent=r.parentId;val visited=mutableSetOf(r.id)
                    while(parent!=null) {require(visited.add(parent) && visited.size<=16);val p=requireNotNull(records[parent]);require(p.kind==OrganizerKind.TASK);parent=p.parentId}}
                "OCCURRENCE" -> {val o=OrganizerCodec.override(entry.body);val r=requireNotNull(records[o.recordId])
                    require(entry.id=="${o.recordId}@${o.originalStart.toEpochMilli()}" && OrganizerRecurrence.contains(r,o.originalStart,ZoneId.of(requireNotNull(r.time).zoneId)))}
                "DELIVERY" -> {val r=receipt(entry.body);val owner=requireNotNull(records[r.recordId]);require(entry.id==r.occurrenceKey && r.recordVersion<=owner.version)
                    require(r.occurrenceKey.startsWith("${r.recordId}@") && r.recordVersion>0)}
                "SOURCE" -> {val source=sources.getValue(entry.id);require(source.id==entry.id && source.version<Long.MAX_VALUE);source.endpoint?.let(CollectorUrlPolicy::validate)
                    json(entry.body).optJSONObject("snapshot")?.let {s -> snapshot(source,s).also {CollectorPayloadLimits.validateKind(source.kind,it.payload)}}}
                "CLOCK" -> require(worldClock(entry.body).let {it.id==entry.id && it.version<Long.MAX_VALUE})
                "BRIEF" -> require(BriefCodec.document(entry.body).id==entry.id)
                "BRIEF_SETTINGS" -> {require(entry.id=="1" && BriefCodec.settings(entry.body).version<Long.MAX_VALUE)}
                "SCREEN" -> require(entry.id=="1" && json(entry.body).getInt("schema")==1)
            }
        }
    }
    suspend fun localEntries(db: AppDatabase)=snapshot(db).associateBy(::key)
    fun title(entry: EverydayArchiveEntry): String=when(entry.kind) {
        "ORGANIZER" -> OrganizerCodec.decode(entry.body).title
        "SOURCE" -> CollectorCodec.source(json(entry.body).getString("source")).title
        "CLOCK" -> worldClock(entry.body).label
        "BRIEF" -> "${BriefCodec.document(entry.body).date} · ${BriefCodec.document(entry.body).period}"
        else -> "Everyday ${entry.kind.lowercase().replace('_',' ')}"
    }

    /** Called inside the existing Brain import transaction. Merge keeps local rows unless explicitly replaced. */
    suspend fun restore(db: AppDatabase,entries: List<EverydayArchiveEntry>,mode: BrainImportMode,policies: Map<String,ImportConflictPolicy>,workspaceIds: Map<String,String>): Map<String,String> {
        validate(entries)
        require(mode!=BrainImportMode.MERGE || entries.none {it.kind in setOf("SCREEN","BRIEF_SETTINGS") && policies[key(it)]==ImportConflictPolicy.DUPLICATE}) {"Singleton settings cannot be duplicated"}
        val old=localEntries(db)
        val oldClockVersions=entries.filter {it.kind=="CLOCK"}.associate {it.id to (db.worldClockDao().get(it.id)?.version ?: 0)}
        val main=entries.filter {it.kind in setOf("ORGANIZER","SOURCE","CLOCK","BRIEF","BRIEF_SETTINGS")}
        val selected=main.filter {mode==BrainImportMode.REPLACE || key(it) !in old || policies[key(it)] in setOf(ImportConflictPolicy.USE_BACKUP,ImportConflictPolicy.DUPLICATE)}
        val duplicate=selected.filter {mode==BrainImportMode.MERGE && policies[key(it)]==ImportConflictPolicy.DUPLICATE && it.kind!="BRIEF_SETTINGS"}.associate {
            key(it) to java.util.UUID.randomUUID().toString().let {uuid -> if(it.kind=="BRIEF") java.security.MessageDigest.getInstance("SHA-256").digest(uuid.toByteArray()).joinToString("") {b -> "%02x".format(b)} else uuid}
        }
        fun mapped(kind: String,value: String)=duplicate["everyday:$kind:$value"] ?: value
        fun recordId(value: String)=mapped("ORGANIZER",value)
        fun sourceId(value: String)=mapped("SOURCE",value)
        if(mode==BrainImportMode.REPLACE) clear(db)
        val recordVersions=mutableMapOf<String,Long>()
        val authoredVersions=entries.filter {it.kind=="ORGANIZER"}.associate {it.id to OrganizerCodec.decode(it.body).version}
        val selectedRecordIds=selected.filter {it.kind=="ORGANIZER"}.map {it.id}.toSet()
        val incoming=selected.filter {it.kind=="ORGANIZER"}.map {entry ->
            val r=OrganizerCodec.decode(entry.body);val prior=db.organizerDao().get(recordId(r.id))
            // Version changes revoke pending OS intents and stale UI/tool writes, including same-ID replacement.
            val oldVersion=old["everyday:ORGANIZER:${r.id}"]?.let {OrganizerCodec.decode(it.body).version} ?: 0
            val version=nextVersion(r.version,prior?.version ?: 0,oldVersion)
            recordVersions[r.id]=version
            val timer=r.timer?.let {it.copy(remainingMs=it.remaining(clockNow(),0,"portable-restore"),startedAt=null,elapsedStartMs=null,bootId=null)}
            r.copy(id=recordId(r.id),version=version,parentId=r.parentId?.let(::recordId),links=r.links.map {it.copy(objectId=if(it.objectId.startsWith("organizer:")) "organizer:"+recordId(it.objectId.removePrefix("organizer:")) else workspaceIds[it.objectId] ?: it.objectId)},timer=timer,status=if(timer?.remainingMs==0L && r.status==OrganizerStatus.ACTIVE) OrganizerStatus.COMPLETED else r.status,
                calendarUid=if("everyday:ORGANIZER:${r.id}" in duplicate) null else r.calendarUid)
        }
        incoming.forEach {r ->
            val entity=OrganizerCodec.entity(r)
            if(db.organizerDao().get(r.id)==null) db.organizerDao().insert(entity) else {db.organizerDao().clearOverrides(r.id);db.organizerDao().clearReceipts(r.id);check(db.organizerDao().update(entity)==1)}
            db.organizerDao().enqueue(OrganizerScheduleEntity(r.id,r.version))
            db.organizerDao().change(OrganizerChangeEntity(recordId=r.id,version=r.version,action="restored",at=clockNow().toEpochMilli()))
        }
        entries.filter {it.kind=="OCCURRENCE"}.forEach {entry ->
            val o=OrganizerCodec.override(entry.body)
            if(o.recordId in selectedRecordIds) {val mapped=o.copy(recordId=recordId(o.recordId));db.organizerDao().putOverride(OrganizerOccurrenceEntity(mapped.recordId,mapped.originalStart.toEpochMilli(),OrganizerCodec.encode(mapped)))}
        }
        entries.filter {it.kind=="DELIVERY"}.forEach {entry ->
            val r=receipt(entry.body)
            if(r.recordId in selectedRecordIds && "everyday:ORGANIZER:${r.recordId}" !in duplicate) {
                // Imported active dispatch/snooze claims are not portable. Terminal history prevents replay.
                if(r.state in setOf(DeliveryState.FIRED,DeliveryState.DISMISSED,DeliveryState.MISSED) || r.state==DeliveryState.CANCELLED && r.recordVersion==authoredVersions[r.recordId])
                    db.organizerDao().putReceipt(OrganizerCodec.entity(r.copy(recordId=recordId(r.recordId),recordVersion=recordVersions.getValue(r.recordId))))
            }
        }
        val sourceVersions=mutableMapOf<String,Long>()
        selected.filter {it.kind=="SOURCE"}.forEach {entry ->
            val body=json(entry.body);val s=CollectorCodec.source(body.getString("source"));val prior=db.collectorDao().source(sourceId(s.id))
            val oldVersion=old["everyday:SOURCE:${s.id}"]?.let {CollectorCodec.source(json(it.body).getString("source")).version} ?: 0
            val next=s.copy(id=sourceId(s.id),version=nextVersion(s.version,prior?.version ?: 0,oldVersion),enabled=if(s.external) false else s.enabled,childApproved=false)
            sourceVersions[s.id]=next.version
            val entity=CollectorSourceEntity(next.id,next.version,CollectorCodec.encode(next))
            if(prior==null) db.collectorDao().insert(entity) else {db.collectorDao().removeSnapshot(next.id);check(db.collectorDao().update(entity)==1)}
            body.optJSONObject("snapshot")?.takeIf {s.external}?.let {db.collectorDao().putSnapshot(CollectorCodec.entity(snapshot(next,it)))}
            db.collectorDao().putAttempt(CollectorCodec.entity(next.version,CollectorAttempt(next.id,null,CollectorAttemptState.NEVER,message="Restored saved information; review source settings before enabling refresh")))
        }
        selected.filter {it.kind=="CLOCK"}.sortedBy {worldClock(it.body).position}.forEach {entry ->
            val c=worldClock(entry.body);val prior=db.worldClockDao().get(mapped("CLOCK",c.id))
            require(prior!=null || db.worldClockDao().all().size<24)
            val next=c.copy(id=mapped("CLOCK",c.id),position=prior?.position ?: db.worldClockDao().all().size,version=nextVersion(c.version,prior?.version ?: 0,oldClockVersions.getValue(c.id)))
            val entity=WorldClockEntity(next.id,next.label,next.zoneId,next.cityId,next.position,next.version,null)
            if(prior==null) db.worldClockDao().insert(entity) else check(db.worldClockDao().update(entity)==1)
        }
        selected.filter {it.kind=="BRIEF"}.forEach {entry ->
            val b=BriefCodec.document(entry.body)
            fun fact(f: BriefFact)=f.copy(sourceId=sourceId(f.sourceId),
                route=if(f.route.startsWith("halo://sources/${f.sourceId}/")) "halo://sources/${sourceId(f.sourceId)}/"+f.route.removePrefix("halo://sources/${f.sourceId}/") else f.route,
                values=f.values.mapValues { (k,v) -> when(k) {"recordId" -> recordId(v);"localId" -> workspaceIds[v] ?: v;else -> v} })
            val next=b.copy(id=mapped("BRIEF",b.id),facts=b.facts.map(::fact),sources=b.sources.map {it.copy(id=sourceId(it.id),version=sourceVersions[it.id] ?: it.version,childApproved=false)},
                changes=b.changes.map {it.copy(sourceId=sourceId(it.sourceId),fact=it.fact?.let(::fact))})
            db.briefDao().remove(next.id);db.briefDao().insert(BriefEntity(next.id,next.generatedAt.toEpochMilli(),BriefCodec.encode(next)))
        }
        db.briefDao().prune(clockNow().minusSeconds(30L*86400).toEpochMilli())
        selected.firstOrNull {it.kind=="BRIEF_SETTINGS"}?.let {entry ->
            val settings=BriefCodec.settings(entry.body)
            val current=db.briefDao().settings()?.version ?: 0
            val next=settings.copy(version=nextVersion(settings.version,current,old["everyday:BRIEF_SETTINGS:1"]?.let {BriefCodec.settings(it.body).version} ?: 0),proactive=false,pinnedRecordIds=settings.pinnedRecordIds.map(::recordId).toSet(),childRecordIds=emptySet())
            db.briefDao().settings(BriefSettingsEntity(version=next.version,body=BriefCodec.encode(next)))
        }
        val all=db.organizerDao().records(true).map {OrganizerCodec.decode(it.body)}.associateBy {it.id}
        all.values.forEach {r ->var parent=r.parentId;val visited=mutableSetOf(r.id)
            while(parent!=null) {require(visited.add(parent) && visited.size<=16);val p=requireNotNull(all[parent]);require(p.kind==OrganizerKind.TASK)
                require(r.deletedAt!=null || r.status!=OrganizerStatus.ACTIVE || p.deletedAt==null && p.status==OrganizerStatus.ACTIVE) {"A restored subtask needs its active parent. Restore that parent too or keep the local plan."}
                parent=p.parentId}}
        require(db.collectorDao().sources().size<=CollectorRefreshPolicy.MAX_SOURCE_COUNT && all.size<=10000)
        return duplicate
    }
    suspend fun clear(db: AppDatabase) {
        db.organizerDao().clearRecords();db.organizerDao().clearChanges()
        db.collectorDao().clearSources();db.collectorDao().clearBudgets()
        db.worldClockDao().clear();db.briefDao().clearBriefs();db.briefDao().clearBaselines();db.briefDao().clearDeliveries();db.briefDao().clearSettings()
    }
    private fun nextVersion(vararg values: Long): Long=values.max().also {require(it<Long.MAX_VALUE)}+1
    private fun clockNow()=Instant.ofEpochMilli(System.currentTimeMillis())
    private fun json(body: String)=SafeCollectorJson.objectValue(body,8*1024*1024,maximumTokens=1000000)
    private fun JSONObject.nullText(key: String)=if(isNull(key)) null else getString(key)
    private fun worldClock(body: String)=json(body).let {j ->WorldClockRecord(j.getString("id"),j.getString("label"),j.getString("zoneId"),j.nullText("cityId"),j.getInt("position"),j.getLong("version"))}
    private fun receipt(body: String)=json(body).let {j ->DeliveryReceipt(j.getString("key"),j.getString("recordId"),j.getLong("version"),Instant.ofEpochMilli(j.getLong("triggerAt")),
        DeliveryState.valueOf(j.getString("state")),Instant.ofEpochMilli(j.getLong("updatedAt")))}
    private fun snapshot(source: CollectorSource,j: JSONObject): CollectorSnapshot {
        require(j.getString("revision").length in 1..200)
        listOf("etag","lastModified").forEach {key -> j.nullText(key)?.let {require(it.length<=4096 && '\r' !in it && '\n' !in it)}}
        j.nullText("effectiveUrl")?.let(CollectorUrlPolicy::validate)
        return CollectorSnapshot(source.id,source.version,CollectorCodec.payload(j.getString("payload")),j.getString("revision"),
        Instant.ofEpochMilli(j.getLong("retrievedAt")),Instant.ofEpochMilli(j.getLong("verifiedAt")),Instant.ofEpochMilli(j.getLong("expiresAt")),j.nullText("etag"),j.nullText("lastModified"),j.nullText("effectiveUrl"))
    }
}
