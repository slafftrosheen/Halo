package com.halo.app.ui.everyday

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.presentation.*
import com.halo.app.screen.*
import java.util.UUID

/** Local setup transaction adapter. It has no transport or OS widget-placement dependency. */
internal object EverydaySetupChoices {
    data class Applied(val draft: EverydaySetupDraft,val sources: List<CollectorState>)
    suspend fun apply(db: AppDatabase,sources: CollectorRepository,screen: LocalScreenStore,draft: EverydaySetupDraft,
        reviewedSources: List<CollectorState>,city: OfflineCity?,sourcesSaved: ()->Unit = {}): Applied {
        EverydaySetupDraft.decode(draft.encode())
        require(city?.id==draft.cityId)
        require(draft.sourceIds.all {id -> reviewedSources.any {it.source.id==id && it.source.external}})
        val saved=db.withTransaction {
            reviewedSources.filter {it.source.external && it.source.id!=city?.let {c -> "weather:setup:${c.id}"}}.forEach {row ->
                val current=requireNotNull(sources.state(row.source.id)).source
                val desired=row.source.copy(selected=row.source.id in draft.sourceIds,
                    refreshMinutes=if(row.source.id in draft.sourceIds) draft.minutes else row.source.refreshMinutes)
                if(current.copy(version=desired.version)!=desired) {
                    if(current.version!=draft.versions[row.source.id]) throw CollectorConflict("Source changed")
                    sources.save(desired,current.version)
                }
            }
            city?.let {
                val candidate=StarterSourceCatalogue.weather(it.weatherPlace(),"weather:setup:${it.id}").copy(refreshMinutes=draft.minutes)
                val current=sources.state(candidate.id)?.source
                if(current==null) sources.save(candidate,null)
                else if(current.copy(version=1)!=candidate) {
                    if(current.version!=draft.versions[candidate.id]) throw CollectorConflict("Source changed")
                    sources.save(candidate,current.version)
                }
            }
            EverydayWriteAuthority.requireCurrent()
            sources.states()
        }
        sourcesSaved()
        val revision=if(draft.applyScreen) screen.withToolState(draft.screenRevision) {state ->
            EverydayWriteAuthority.requireCurrent()
            val layout=ScreenLayout(UUID.randomUUID().toString(),"Your day",draft.cards.map {type ->
                val kind=EverydayCards.screenKinds.getValue(type)
                val config=EverydayCardConfig(kind,sourceId=city?.takeIf {kind in setOf(EverydayCardKind.WEATHER,EverydayCardKind.CLOCK_WEATHER,EverydayCardKind.DASHBOARD)}?.let {"weather:setup:${it.id}"})
                ScreenCard(type,null,CardSize.STANDARD,false,config=config.fields(),presentationId=UUID.randomUUID().toString())
            },0,state.layout?.appearance ?: ScreenAppearance())
            screen.saveLayout(layout);check(screen.confirmToolPersistence())
            screen.withToolState {it.revision}
        } else draft.screenRevision
        return Applied(draft.copy(sourceIds=saved.filter {it.source.external && it.source.selected}.map {it.source.id}.toSet(),
            versions=saved.associate {it.source.id to it.source.version},screenRevision=revision),saved)
    }
}
