package com.halo.app.data.everyday

import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.briefing.*

/** Runs in Notebook's existing purge transaction; derived excerpts cannot resurrect a purged note. */
internal object EverydayLocalDataCleanup {
    suspend fun notePurged(db: AppDatabase,noteId: String) {
        com.halo.app.everyday.EverydayReadAuthority.invalidate()
        db.collectorDao().sources().forEach {row ->
            val stored=db.collectorDao().snapshot(row.id) ?: return@forEach
            val payload=runCatching {CollectorCodec.payload(stored.payload)}.getOrNull()
            if(payload==null || payload is CollectorPayload.Local && payload.items.any {it.id==noteId}) {
                db.collectorDao().removeSnapshot(row.id)
                val source=runCatching {CollectorCodec.source(row.body)}.getOrNull()
                val next=source?.takeIf {it.version<Long.MAX_VALUE}?.copy(version=source.version+1)
                if(next!=null) check(db.collectorDao().update(CollectorSourceEntity(next.id,next.version,CollectorCodec.encode(next)))==1)
                db.collectorDao().putAttempt(CollectorCodec.entity(next?.version ?: row.version,
                    CollectorAttempt(row.id,null,CollectorAttemptState.NEVER,message="Local source changed; reload saved data")))
            }
        }
        var changed=false
        db.briefDao().recent(60).forEach {row ->
            val document=runCatching {BriefCodec.document(row.body)}.getOrNull()
            if(document==null || (document.facts+document.changes.mapNotNull {it.fact}).any {it.kind==BriefFactKind.NOTE && it.values["localId"]==noteId}) {
                db.briefDao().remove(row.id);changed=true
            }
        }
        if(changed) db.briefDao().clearBaselines()
    }
}
