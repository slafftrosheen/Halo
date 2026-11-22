package com.halo.app.data.everyday

import com.halo.app.data.AppDatabase
import com.halo.app.data.workspace.privateLibraryClaim
import com.halo.app.everyday.briefing.*

/** Checked inside Room's publication transaction, after any asynchronous brief assembly. */
internal object EverydayBriefPublicationAuthority {
    suspend fun requireCurrent(db: AppDatabase,document: EverydayBrief) {
        document.sources.filter {it.id!="halo:attention"}.forEach {source ->
            require(db.collectorDao().source(source.id)?.version==source.version) {"Source changed while building the brief"}
        }
        (document.facts+document.changes.mapNotNull {it.fact}).filter {it.kind==BriefFactKind.NOTE}.forEach {fact ->
            require(fact.values["localId"]?.let {db.notebookDao().getWorkspaceObject(it)?.lifecycle=="ACTIVE"}==true) {"A note changed while building the brief"}
        }
        val plans = (document.facts + document.changes.mapNotNull { it.fact }).mapNotNull { it.values["recordId"] }.distinct()
        val privateLibrary = db.privateLibraryDao()
        for (batch in plans.map(::privateLibraryClaim).chunked(400)) {
            require(privateLibrary.claims(batch).isEmpty()) { "A plan changed while building the brief" }
        }
    }
}
