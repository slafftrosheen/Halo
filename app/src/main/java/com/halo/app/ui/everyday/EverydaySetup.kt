package com.halo.app.ui.everyday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.presentation.*
import com.halo.app.language.uiText
import com.halo.app.screen.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*

data class EverydaySetupDraft(val sourceIds: Set<String> = emptySet(),val cards: List<String> = listOf("clock","upcoming","organizer_tasks","briefing"),
    val minutes: Int=60,val cityId: String?=null,val applyScreen: Boolean=true,val language: String?=null,val category: String?=null,
    val versions: Map<String,Long> = emptyMap(),val screenRevision: String="") {
    fun withWeatherCity(id: String?)=copy(cityId=id,sourceIds=sourceIds.filterNot {it.startsWith("weather:setup:")}.toSet())
    fun encode()=JSONObject().put("sources",JSONArray(sourceIds.sorted())).put("cards",JSONArray(cards)).put("minutes",minutes).put("city",cityId)
        .put("applyScreen",applyScreen).put("language",language).put("category",category).put("versions",JSONObject(versions)).put("screenRevision",screenRevision).toString()
    companion object {
        fun decode(raw: String): EverydaySetupDraft {
            val j=SafeCollectorJson.objectValue(raw,24000)
            fun strings(key: String)=j.getJSONArray(key).let {a ->require(a.length()<=100);(0 until a.length()).map(a::getString)}
            val v=j.getJSONObject("versions");require(v.length()<=100)
            return EverydaySetupDraft(strings("sources").toSet(),strings("cards"),j.getInt("minutes"),j.optString("city").takeUnless {it.isBlank() || it=="null"},
                j.getBoolean("applyScreen"),j.optString("language").takeUnless {it.isBlank() || it=="null"},j.optString("category").takeUnless {it.isBlank() || it=="null"},
                v.keys().asSequence().associateWith {v.getLong(it)},j.getString("screenRevision")).also {d ->
                require(d.sourceIds.size<=100 && d.sourceIds.all {it.matches(Regex("[A-Za-z0-9_.:-]{1,160}"))})
                require(d.cards.size<=13 && d.cards.distinct()==d.cards && d.cards.all {it in EverydayCards.screenKinds})
                require(d.minutes in listOf(15,60,180,1440) && (d.cityId==null || d.cityId.matches(Regex("[0-9]{1,12}"))))
                require(d.category==null || d.category in SourceCategory.entries.map {it.name})
                require(d.language==null || d.language in listOf("lv","ru","en"))
                require(d.versions.keys.all {it.matches(Regex("[A-Za-z0-9_.:-]{1,160}"))} && d.versions.values.all {it>0})
                require(d.screenRevision.length<=64)
            }
        }
    }
}
data class EverydaySetupState(val loading: Boolean=true,val busy: Boolean=false,val kids: Boolean=true,val draft: EverydaySetupDraft?=null,
    val sources: List<CollectorState> = emptyList(),val city: OfflineCity?=null,val error: String?=null,val saved: Boolean=false,val preview: SourcePreview?=null)

/** Optional resumable setup. Applying choices uses existing owners; opening/previewing metadata never fetches. */
class EverydaySetupViewModel(application: Application,private val savedState: SavedStateHandle): AndroidViewModel(application) {
    private val preferences=application.getSharedPreferences("everyday-setup",0)
    private val draftWriter=Mutex()
    private val draftRevision=java.util.concurrent.atomic.AtomicLong()
    private val mutable=MutableStateFlow(EverydaySetupState())
    val state=mutable.asStateFlow()
    init {load()}
    fun load(reviewCurrent: Boolean=false) {
        if(state.value.busy) return
        viewModelScope.launch {
            try {
                (getApplication<Application>() as HaloApplication).awaitOrganizerBootstrap()
                Graph.collectorBootstrap.ensureInitialized()
                val rows=withContext(Dispatchers.IO) {Graph.collectorRepository.states()}
                val screen=Graph.screenStore.withToolState {it}
                val retained=if(reviewCurrent) null else (savedState.get<String>("draft") ?: preferences.getString("draft",null))?.let {runCatching {EverydaySetupDraft.decode(it)}.getOrNull()}
                val draft=retained ?: EverydaySetupDraft(sourceIds=rows.filter {it.source.external && it.source.selected}.map {it.source.id}.toSet(),
                    cityId=rows.filter {it.source.selected && it.source.id.startsWith("weather:setup:")}.singleOrNull()?.source?.id?.substringAfterLast(':'),
                    applyScreen=screen.layout==null,versions=rows.associate {it.source.id to it.source.version},screenRevision=screen.revision)
                mutable.value=EverydaySetupState(loading=false,kids=Graph.kidsPreferences.getEnabled(),draft=draft,sources=rows,
                    city=draft.cityId?.let {Graph.offlineCities.get(it)})
            } catch(cancelled: CancellationException) {throw cancelled}
              catch(_: Exception) {mutable.value=state.value.copy(loading=false,error="Everyday setup could not be opened. Your saved choices are kept.")}
        }
    }
    fun change(draft: EverydaySetupDraft) {
        if(state.value.busy) return
        val raw=draft.encode();EverydaySetupDraft.decode(raw);savedState["draft"]=raw
        mutable.value=state.value.copy(draft=draft,saved=false,error=null)
        val revision=draftRevision.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {draftWriter.withLock {
            if(draftRevision.get()==revision) preferences.edit().putString("draft",raw).commit()
        }}
    }
    fun city(city: OfflineCity) {change(requireNotNull(state.value.draft).withWeatherCity(city.id));mutable.value=state.value.copy(city=city)}
    fun removeCity() {change(requireNotNull(state.value.draft).withWeatherCity(null));mutable.value=state.value.copy(city=null)}
    fun preview(source: CollectorSource) {
        if(state.value.busy) return
        mutable.value=state.value.copy(preview=SourcePreview(source,CollectorFetchResult.Unavailable("Preview shows saved source settings. Fetching a preview is optional and requires Power.")))
    }
    fun dismissPreview() {mutable.value=state.value.copy(preview=null)}
    fun fetchPreview()=work {
        val source=requireNotNull(state.value.preview).candidate
        val result=Graph.collectorPreview.inspect(requireNotNull(source.endpoint),source.kind,source.language)
        EverydayWriteAuthority.requireCurrent()
        mutable.value=state.value.copy(preview=result)
    }
    fun apply()=work {
        val before=state.value;val draft=requireNotNull(before.draft)
        var sourcesSaved=false
        try {
            val applied=withContext(Dispatchers.IO) {
                EverydaySetupChoices.apply(Graph.appDatabase,Graph.collectorRepository,Graph.screenStore,draft,before.sources,before.city) {sourcesSaved=true}
            }
            draftRevision.incrementAndGet()
            withContext(Dispatchers.IO) {draftWriter.withLock {check(preferences.edit().remove("draft").putBoolean("completed",true).commit())}}
            savedState.remove<String>("draft")
            mutable.value=state.value.copy(saved=true,error=null,draft=applied.draft,sources=applied.sources)
        } catch(cancelled: CancellationException) {throw cancelled}
          catch(error: Exception) {
            mutable.value=state.value.copy(error=if(sourcesSaved) "Source choices were saved. The Screen layout changed or could not be saved. Review saved choices before trying again."
                else "These choices changed or could not be saved. Reload saved choices, review them and try again.")
        }
    }
    private fun work(action: suspend ()->Unit) {
        if(state.value.busy) return
        mutable.value=state.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try {val epoch=Graph.kidsPreferences.adultActionRevision()
                EverydayWriteAuthority.run({Graph.kidsPreferences.requireAdultAction(epoch)},action)
            } catch(cancelled: CancellationException) {throw cancelled}
              catch(_: Exception) {mutable.value=state.value.copy(error="This action could not finish. Saved choices are kept; review the current mode and try again.")}
            finally {mutable.value=state.value.copy(busy=false)}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EverydaySetupPage(model: EverydaySetupViewModel=viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val family by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val power by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    var cityPicker by remember {mutableStateOf(false)}
    if(state.loading) {LinearProgressIndicator(Modifier.fillMaxWidth());return}
    if(family) {Text(uiText("Ask a grown-up to choose sources and cards. You can continue setup without changing them."));return}
    val draft=state.draft
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(uiText("Your clock, organizer and daily brief work in Local. External sources are optional; only selected sources refresh in Power."))
        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {EverydayMessage(it,true)}
        if(draft!=null) {
            Text(uiText("Source language"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf<String?>(null,"lv","ru","en").forEach {language ->
                FilterChip(draft.language==language,{model.change(draft.copy(language=language))},enabled=!state.busy,label={Text(language?.uppercase() ?: uiText("All languages"))})}}
            Text(uiText("Source category"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(draft.category==null,{model.change(draft.copy(category=null))},enabled=!state.busy,label={Text(uiText("All categories"))})
                SourceCategory.entries.filter {it !in setOf(SourceCategory.PERSONAL,SourceCategory.CUSTOM,SourceCategory.CALENDAR)}.forEach {category ->
                    FilterChip(draft.category==category.name,{model.change(draft.copy(category=category.name))},enabled=!state.busy,label={Text(uiText(sourceCategoryLabel(category)))})
                }
            }
            state.sources.filter {it.source.external && (draft.language==null || it.source.language==draft.language) && (draft.category==null || it.source.category.name==draft.category)}.forEach {row ->
                EverydayToggle(row.source.title,row.source.id in draft.sourceIds,!state.busy) {selected ->model.change(draft.copy(sourceIds=if(selected) draft.sourceIds+row.source.id else draft.sourceIds-row.source.id))}
                TextButton({model.preview(row.source)},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(uiText("Preview source")+" · "+row.source.title)}
            }
            Text(uiText("Refresh interval"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(15,60,180,1440).forEach {minutes ->
                FilterChip(draft.minutes==minutes,{model.change(draft.copy(minutes=minutes))},enabled=!state.busy,label={Text(uiText("%1\$d minutes",minutes))})}}
            Text(uiText("Weather city"),style=MaterialTheme.typography.titleMedium)
            Text(state.city?.let {"${it.name} · ${it.countryCode}"} ?: uiText("No weather city selected"))
            TextButton({cityPicker=true},enabled=!state.busy,modifier=Modifier.heightIn(min=48.dp)) {Text(uiText("Choose city offline"))}
            if(state.city!=null) TextButton(model::removeCity,enabled=!state.busy) {Text(uiText("Remove weather city"))}
            Text(uiText("Choose initial Screen cards"),style=MaterialTheme.typography.titleMedium)
            EverydayCards.screenKinds.forEach { (type,kind) ->EverydayToggle(uiText(EverydayCards.title(kind)),type in draft.cards,!state.busy) {selected ->
                model.change(draft.copy(cards=if(selected) draft.cards+type else draft.cards-type))}}
            EverydayToggle(uiText("Use these cards on Screen"),draft.applyScreen,!state.busy) {model.change(draft.copy(applyScreen=it))}
            Text(uiText("Applying Screen cards replaces its current layout. You can undo the layout change in Screen. Android launcher placement remains your choice."),style=MaterialTheme.typography.bodySmall)
            Button(model::apply,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(uiText("Save everyday choices"))}
            if(state.saved) EverydayMessage("Everyday choices saved. You can change sources and cards later.")
        }
        TextButton({model.load(true)},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(uiText("Reload saved choices"))}
    }
    if(cityPicker) OfflineCityPicker(state.busy,onDismiss={cityPicker=false}) {model.city(it);cityPicker=false}
    state.preview?.let {preview ->
        com.halo.app.ui.components.HaloAlertDialog(onDismissRequest=model::dismissPreview,title={Text(preview.candidate.title)},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(preview.candidate.publisher);Text(preview.candidate.language.uppercase());Text(preview.candidate.endpoint.orEmpty())
                when(val result=preview.result) {
                    is CollectorFetchResult.Updated -> {
                        Text(uiText("A real source preview was retrieved. Saving your choices is a separate step."))
                        when(val payload=result.payload) {
                            is CollectorPayload.Articles -> payload.items.take(3).forEach {Text(it.title);Text(it.publisher,style=MaterialTheme.typography.bodySmall)}
                            is CollectorPayload.Warnings -> payload.items.take(3).forEach {Text(it.title);Text(it.publisher,style=MaterialTheme.typography.bodySmall)}
                            is CollectorPayload.Calendar -> payload.preview.records.take(3).forEach {Text(it.title)}
                            else -> Unit
                        }
                    }
                    is CollectorFetchResult.Unavailable -> Text(uiText(result.reason))
                    else -> Text(uiText("No saved preview"))
                }
            }
        },confirmButton={TextButton(model::fetchPreview,enabled=!state.busy && power==com.halo.app.ai.PrivacyMode.CLOUD && preview.candidate.kind in setOf(CollectorKind.FEED,CollectorKind.ALERTS,CollectorKind.PUBLIC_CALENDAR)) {Text(uiText("Fetch preview in Power"))}},
            dismissButton={TextButton(model::dismissPreview) {Text(uiText("Close"))}})
    }
}
