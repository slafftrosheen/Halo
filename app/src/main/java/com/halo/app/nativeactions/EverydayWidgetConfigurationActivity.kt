package com.halo.app.nativeactions

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.presentation.*
import com.halo.app.language.uiText
import com.halo.app.ui.everyday.OfflineCityPicker
import com.halo.app.ui.theme.*
import kotlinx.coroutines.*

/** Android configuration result rules: cancelled until one exact durable instance save succeeds. */
class EverydayWidgetConfigurationActivity : ComponentActivity() {
    private var snapshot by mutableStateOf<EverydaySnapshot?>(null)
    private var instance by mutableStateOf<EverydayWidgetInstance?>(null)
    private var error by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private val applyOperation: EverydayWidgetApplyOperation by viewModels()
    private var expected: Long? = null
    private var loaded = false
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var kind: EverydayCardKind? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        kind = EverydayWidgetProviders.kind(this,widgetId)
        if (kind == null) { finish(); return }
        savedInstanceState?.getString("widget.reviewedBase")?.let { raw ->
            runCatching { EverydayWidgetReview.restore(raw) }.getOrNull()?.takeIf { it.id==widgetId }?.let { base ->
                instance=base;expected=base.version.takeIf { it>0 };loaded=true
            }
        }
        Graph.init(this)
        applyOperation.bind(widgetId)
        lifecycleScope.launch {
            applyOperation.state.collect { operation ->
                busy=operation.busy
                if(operation.error) error="This widget or its source changed. Cancel and reopen configuration to review current settings."
                if(operation.completed) {
                    setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish()
                }
            }
        }
        setContent { com.halo.app.language.AppLanguageProvider(Graph.appLanguagePreferences) {
            BackHandler(busy) { /* Keep the durable Apply operation attached to its result. */ }
            val appearance by Graph.appearancePreferences.mode.collectAsStateWithLifecycle()
            val palettes by Graph.palettePreferences.state.collectAsStateWithLifecycle()
            val assistant by Graph.assistantPreferences.state.collectAsStateWithLifecycle()
            val family by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
            val ready by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
            val dark = when (appearance) { AppearanceMode.LIGHT -> false; AppearanceMode.DARK -> true; else -> isSystemInDarkTheme() }
            HaloTheme(dark,assistant.fontScale,if (dark) palettes.dark else palettes.light) {
                Surface(Modifier.fillMaxSize()) {
                    if (!ready || snapshot == null) Column(Modifier.padding(24.dp)) {
                        Text(uiText("Configure widget"),style=MaterialTheme.typography.headlineMedium)
                        if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                            Text(uiText(error!!),color=MaterialTheme.colorScheme.error)
                            TextButton(::load) { Text(uiText("Retry")) }
                        }
                        TextButton(::cancelConfiguration,enabled=!busy) { Text(uiText("Cancel")) }
                    } else if (family) Column(Modifier.padding(24.dp)) {
                        Text(uiText("Ask a grown-up to configure this widget."))
                        TextButton(::cancelConfiguration,Modifier.heightIn(min=56.dp),enabled=!busy) { Text(uiText("Close")) }
                    } else instance?.let { current -> EverydayWidgetConfigurationForm(current,snapshot!!,busy,error,
                        cancel=::cancelConfiguration,save=::save,openSources={ startActivity(com.halo.app.ui.mini.MiniHaloEntry.intent(this,"screen/sources")) },palettes=palettes) }
                }
            }
        } }
    }
    override fun onResume() { super.onResume(); if (kind != null) load() }
    override fun onSaveInstanceState(outState: Bundle) {
        instance?.let { outState.putString("widget.reviewedBase",EverydayWidgetReview.encode(it)) }
        super.onSaveInstanceState(outState)
    }
    private fun load() { lifecycleScope.launch {
        try {
            (application as HaloApplication).awaitOrganizerBootstrap()
            val data = readEverydaySnapshot(this@EverydayWidgetConfigurationActivity)
            if (!loaded) {
                val current = withContext(Dispatchers.IO) { EverydayWidgetStore(this@EverydayWidgetConfigurationActivity).get(widgetId) }
                expected = current?.version; instance = current?.let { if(it.config.kind==kind) it else it.copy(config=EverydayCardConfig(kind!!)) }
                    ?: EverydayWidgetInstance(widgetId,0,EverydayCardConfig(kind!!)); loaded=true
            }
            snapshot=data; error=null
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { error="Widget settings could not be opened. Your saved settings are kept." }
    } }
    private fun cancelConfiguration() {
        if(applyOperation.state.value.completed) {
            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish()
        } else if(!applyOperation.state.value.busy) finish()
    }
    private fun save(config: EverydayCardConfig) {
        if(busy) return
        error=null
        applyOperation.submit(requireNotNull(kind),expected,config,snapshot?.sources.orEmpty().associate {it.source.id to it.source.version})
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EverydayWidgetConfigurationForm(instance: EverydayWidgetInstance, snapshot: EverydaySnapshot, busy: Boolean, error: String?,
    cancel: () -> Unit, save: (EverydayCardConfig) -> Unit, openSources: () -> Unit, launcher: Boolean = true, palettes: HaloPalettes = HaloPalettes()) {
    var zone by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.zoneId) }
    var city by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.cityLabel) }
    var source by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.sourceId) }
    var headlineSource by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.headlineSourceId) }
    var recordId by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.recordId) }
    var recordQuery by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.limit) }
    var priority by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.priority?.name) }
    var theme by rememberSaveable(instance.id,instance.version) { mutableStateOf(instance.config.theme) }
    var sections by rememberSaveable(instance.id,instance.version) { mutableStateOf(ArrayList(instance.config.sections.map { it.name })) }
    var picker by rememberSaveable { mutableStateOf(false) }
    val config=instance.config.copy(zoneId=zone,cityLabel=city,sourceId=source,recordId=recordId,limit=limit,priority=priority?.let(com.halo.app.everyday.organizer.TaskPriority::valueOf),theme=if(launcher) theme else "INHERIT",sections=sections.map(EverydayCardKind::valueOf),headlineSourceId=headlineSource)
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val resources=androidx.compose.ui.platform.LocalContext.current.resources
    val face=EverydayCards.project(config,snapshot,locale,externalHost=launcher,text={com.halo.app.language.resolveUiText(resources,it)})
    val previewDark=when(theme) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val visual=rememberVisualPreferences()
    val previewColors=if (launcher && theme != "INHERIT") haloColorScheme(previewDark,if(previewDark) palettes.dark else palettes.light).let {colors ->
        if(visual.higherContrast) colors.copy(onSurfaceVariant=colors.onSurface,outline=colors.onSurface.copy(alpha=.72f),outlineVariant=colors.onSurface.copy(alpha=.38f)) else colors
    } else MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(uiText(if(launcher) "Configure widget" else "Configure this card"),style=MaterialTheme.typography.headlineMedium)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(uiText(EverydayCards.title(config.kind)),style=MaterialTheme.typography.titleLarge)
        MaterialTheme(colorScheme=previewColors) { Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(uiText("Preview"),style=MaterialTheme.typography.labelLarge)
                Text(face.value,style=MaterialTheme.typography.headlineSmall)
                face.rows.forEach { Text(listOf(it.title,it.detail).filter(String::isNotBlank).joinToString(" · ")) }
                Text(face.status,style=MaterialTheme.typography.bodySmall)
            }
        } }
        if(launcher) Text(uiText("Personal titles and details stay inside Halo. The widget shows safe summaries and opens the original item."),style=MaterialTheme.typography.bodySmall)
        if (config.kind in setOf(EverydayCardKind.CLOCK,EverydayCardKind.CLOCK_WEATHER,EverydayCardKind.DASHBOARD)) {
            Text(city.ifBlank { zone ?: uiText("Phone time zone") })
            TextButton({ picker=true },enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) { Text(uiText("Choose clock city")) }
            TextButton({ zone=null;city="" },enabled=!busy) { Text(uiText("Use phone time zone")) }
        }
        val sourceKinds=when(config.kind) {
            EverydayCardKind.WEATHER,EverydayCardKind.CLOCK_WEATHER -> listOf(CollectorKind.WEATHER)
            EverydayCardKind.HEADLINES -> listOf(CollectorKind.FEED)
            EverydayCardKind.DASHBOARD -> listOf(CollectorKind.WEATHER,CollectorKind.FEED)
            else -> emptyList()
        }
        sourceKinds.forEach { sourceKind ->
            val selectedId=if(config.kind==EverydayCardKind.DASHBOARD && sourceKind==CollectorKind.FEED) headlineSource else source
            fun select(id: String?) { if(config.kind==EverydayCardKind.DASHBOARD && sourceKind==CollectorKind.FEED) headlineSource=id else source=id }
            Text(uiText(if(sourceKind==CollectorKind.WEATHER) "Weather source" else "Headline source"),style=MaterialTheme.typography.titleMedium)
            val choices=snapshot.sources.filter { it.source.kind == sourceKind && it.source.selected && it.source.enabled && !it.source.muted }
            FilterChip(selectedId==null,{select(null)},enabled=!busy,label={Text(uiText(if(sourceKind==CollectorKind.WEATHER) "First selected weather city" else "All selected feeds"))})
            choices.forEach { row -> FilterChip(selectedId==row.source.id,{select(row.source.id)},enabled=!busy,label={Text(row.source.title)},modifier=Modifier.heightIn(min=48.dp)) }
            if (selectedId != null && choices.none { it.source.id==selectedId }) Text(uiText("The selected source is unavailable. Choose another source."),color=MaterialTheme.colorScheme.error)
            TextButton(openSources,enabled=!busy) { Text(uiText("Manage sources and weather cities")) }
            Text(uiText("Source selection does not fetch in Local. Selected external sources refresh only in Power."),style=MaterialTheme.typography.bodySmall)
        }
        if (config.kind == EverydayCardKind.TASKS) {
            Text(uiText("Priority filter"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(priority==null,{priority=null},enabled=!busy,label={Text(uiText("All priorities"))})
                com.halo.app.everyday.organizer.TaskPriority.entries.forEach { value -> FilterChip(priority==value.name,{priority=value.name},enabled=!busy,label={Text(uiText(when(value) {
                    com.halo.app.everyday.organizer.TaskPriority.LOW -> "Low"; com.halo.app.everyday.organizer.TaskPriority.NORMAL -> "Normal";
                    com.halo.app.everyday.organizer.TaskPriority.HIGH -> "High"; com.halo.app.everyday.organizer.TaskPriority.URGENT -> "Urgent"
                }))}) }
            }
        }
        if(config.kind in setOf(EverydayCardKind.UPCOMING,EverydayCardKind.AGENDA,EverydayCardKind.TASKS,EverydayCardKind.REMINDERS,EverydayCardKind.ALARMS_TIMERS)) {
            val kinds=when(config.kind) {
                EverydayCardKind.UPCOMING,EverydayCardKind.AGENDA -> setOf(com.halo.app.everyday.organizer.OrganizerKind.EVENT)
                EverydayCardKind.ALARMS_TIMERS -> setOf(com.halo.app.everyday.organizer.OrganizerKind.ALARM,com.halo.app.everyday.organizer.OrganizerKind.TIMER)
                EverydayCardKind.REMINDERS -> setOf(com.halo.app.everyday.organizer.OrganizerKind.REMINDER)
                else -> setOf(com.halo.app.everyday.organizer.OrganizerKind.TASK,com.halo.app.everyday.organizer.OrganizerKind.REMINDER)
            }
            Text(uiText("Item filter"),style=MaterialTheme.typography.titleMedium)
            FilterChip(recordId==null,{recordId=null},enabled=!busy,label={Text(uiText("All matching items"))})
            OutlinedTextField(recordQuery,{recordQuery=it.take(200)},enabled=!busy,label={Text(uiText("Find an organizer item"))},modifier=Modifier.fillMaxWidth())
            val records=snapshot.records.filter { it.kind in kinds && it.status==com.halo.app.everyday.organizer.OrganizerStatus.ACTIVE &&
                (it.id==recordId || it.title.contains(recordQuery,true)) }.sortedBy { it.id!=recordId }
            records.take(30).forEach { row -> FilterChip(row.id==recordId,{recordId=row.id},enabled=!busy,label={Text(row.title)}) }
            if(records.size>30) Text(uiText("Refine the search to find more items."),style=MaterialTheme.typography.bodySmall)
            if(recordId != null && snapshot.records.none { it.id==recordId }) Text(uiText("The configured item is unavailable. Choose another item or all matching items."),color=MaterialTheme.colorScheme.error)
        }
        if(config.kind==EverydayCardKind.DASHBOARD) {
            Text(uiText("Dashboard sections"),style=MaterialTheme.typography.titleMedium)
            Text(uiText("Sections appear in this order"),style=MaterialTheme.typography.bodySmall)
            sections.forEachIndexed { index,name ->
                val sectionTitle=uiText(EverydayCards.title(EverydayCardKind.valueOf(name)))
                Text("${index+1}. $sectionTitle",style=MaterialTheme.typography.titleSmall)
                Column(Modifier.fillMaxWidth()) {
                    TextButton({ sections=ArrayList(sections).apply { java.util.Collections.swap(this,index,index-1) } },enabled=!busy && index>0,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Move up")+" · "+sectionTitle) }
                    TextButton({ sections=ArrayList(sections).apply { java.util.Collections.swap(this,index,index+1) } },enabled=!busy && index<sections.lastIndex,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Move down")+" · "+sectionTitle) }
                }
            }
            listOf(EverydayCardKind.TASKS,EverydayCardKind.UPCOMING,EverydayCardKind.WEATHER,EverydayCardKind.BRIEF,EverydayCardKind.HEADLINES,EverydayCardKind.DEVICE,EverydayCardKind.ALARMS_TIMERS).forEach { section ->
                com.halo.app.ui.everyday.EverydayToggle(uiText(EverydayCards.title(section)),section.name in sections,
                    !busy && if(section.name in sections) sections.size>1 else sections.size<if(launcher) 3 else 5) { selected ->
                    sections=ArrayList(if(selected) sections+section.name else sections-section.name)
                }
            }
        }
        if(config.kind !in setOf(EverydayCardKind.CLOCK,EverydayCardKind.CLOCK_WEATHER,EverydayCardKind.WEATHER,EverydayCardKind.DEVICE)) {
            Text(uiText("Visible items"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { (1..if(launcher) 3 else 5).forEach { count -> FilterChip(limit==count,{limit=count},enabled=!busy,label={Text(count.toString())}) } }
        }
        if(launcher) Text(uiText("Widget theme"),style=MaterialTheme.typography.titleMedium)
        if(launcher) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("INHERIT" to "Follow Halo","LIGHT" to "Light","DARK" to "Dark").forEach { (value,label) -> FilterChip(theme==value,{theme=value},enabled=!busy,label={Text(uiText(label))}) }
        }
        Text(uiText(if(launcher) "Android controls refresh timing. Clock display is managed by the launcher; other information is a saved snapshot. Resize the widget for more details."
            else "These card settings apply when you save the Screen layout."),style=MaterialTheme.typography.bodySmall)
        error?.let { Text(uiText(it),color=MaterialTheme.colorScheme.error) }
        Button({save(config)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText(if(launcher) "Apply to this widget" else "Apply to this card")) }
        TextButton(cancel,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(uiText("Cancel")) }
    }
    if (picker) OfflineCityPicker(busy,onDismiss={picker=false}) { selected -> zone=selected.zoneId;city="${selected.name} · ${selected.countryCode}".take(120);picker=false }
}
