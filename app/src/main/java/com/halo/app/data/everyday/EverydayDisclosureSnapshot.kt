package com.halo.app.data.everyday

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase

/** Small canonical authority facts. No saved feed/record bodies are loaded for a disclosure fence. */
internal data class EverydayDisclosureSnapshot(val records: List<OrganizerAuthorityRow>,val sources: List<Pair<String,Long>>,
    val settingsVersion: Long,val childLibrary: String?) {
    companion object {
        suspend fun capture(db: AppDatabase,childLibrary: String?=null)=db.withTransaction {
            EverydayDisclosureSnapshot(db.organizerDao().authorityRows(),db.collectorDao().sources().map {it.id to it.version}.sortedBy {it.first},
                db.briefDao().settings()?.version ?: 0,childLibrary)
        }
    }
}
