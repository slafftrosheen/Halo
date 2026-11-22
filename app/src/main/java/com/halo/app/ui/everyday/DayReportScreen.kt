package com.halo.app.ui.everyday

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.withTransaction
import coil.compose.AsyncImage
import com.halo.app.HaloApplication
import com.halo.app.data.memory.MemoryCandidate
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.EverydayReadAuthority
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.briefing.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import com.halo.app.ui.theme.HaloDeepMint
import com.halo.app.ui.theme.HaloMint
import com.halo.app.ui.theme.HaloSunriseApricot
import com.halo.app.ui.zone5.Zone5PageHeading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

private data class ReportSentence(val text: String, val tone: DayMomentTone = DayMomentTone.ORDINARY)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayReportScreen(onBack: () -> Unit, onBriefs: () -> Unit, onOpen: (String) -> Unit) {
    val service = Graph.dayReportService
    val choices by service.settings.choices.collectAsStateWithLifecycle()
    val familyReady by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val sources by Graph.collectorRepository.observe().collectAsStateWithLifecycle(emptyList())
    val plans by Graph.organizerRepository.observe().collectAsStateWithLifecycle(emptyList())
    val notificationPolicy by Graph.notificationPolicies.changes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selected by rememberSaveable { mutableStateOf(DayReportPeriod.TODAY.name) }
    val period = DayReportPeriod.valueOf(selected)
    var refresh by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var editingChoices by rememberSaveable { mutableStateOf(false) }
    var explanation by remember { mutableStateOf<DayReportCard?>(null) }
    var keepBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val reading by Graph.readAloud.state.collectAsStateWithLifecycle()
    var session by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf<Pair<DayReport, String>?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { tick++; delay(60_000) }
        }
    }
    val loaded by produceState<DayReport?>(null, period, choices, familyReady, kids, refresh, tick,
        sources, plans, notificationPolicy) {
        if (!familyReady || kids) { value = null; return@produceState }
        error = false
        try {
            (context.applicationContext as HaloApplication).awaitOrganizerBootstrap()
            Graph.collectorBootstrap.ensureInitialized()
            if (value == null && period == DayReportPeriod.TODAY) value = service.prepared()
            value = service.load(period)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { value = null; error = true }
    }
    fun matchesDate(report: DayReport): Boolean {
        val today = java.time.LocalDate.now()
        return report.zone == java.time.ZoneId.systemDefault() &&
            report.date == if (report.period == DayReportPeriod.YESTERDAY) today.minusDays(1) else today
    }
    val report = loaded?.takeIf {
        familyReady && !kids && it.period == period && it.choices == choices && matchesDate(it) &&
            it.choicesRevision == EverydayReadAuthority.capture() &&
            (it.notificationRevision == null || it.notificationRevision == notificationPolicy && Graph.notificationBridge.isAccessGranted()) &&
            (it.screenMinutes == null || screenTimeAvailable(context))
    }
    val sentences = report?.let { daySentences(it) }.orEmpty()
    val dateLabel = report?.let { day ->
        when (day.period) {
            DayReportPeriod.WEEK -> uiText("Your week, in short")
            else -> uiText("%s, in short", day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
        }
    } ?: uiText("Your day in halo")
    val spoken = sentences.joinToString(" ") { it.text }
    val owner = ShellPageOwner("everyday_brief", sourceIdentity = "report:$period:${report?.date}:${report?.choicesRevision}:$kids",
        helpDescription = uiText("See what was captured on your phone and choose what goes in your report."),
        guidanceBlocked = report == null)

    fun stillCurrent(day: DayReport): Boolean =
        Graph.kidsPreferences.loadedState.value && !Graph.kidsPreferences.enabled.value &&
            service.settings.choices.value == day.choices && EverydayReadAuthority.capture() == day.choicesRevision && matchesDate(day) &&
            (day.notificationRevision == null || day.notificationRevision == Graph.notificationPolicies.changes.value && Graph.notificationBridge.isAccessGranted()) &&
            (day.screenMinutes == null || screenTimeAvailable(context))

    fun speak() {
        val day = report ?: return
        if (!stillCurrent(day) || spoken.isBlank()) return
        session = Graph.readAloud.start("your-day:${day.date}:${day.period}", spoken, "provider.android.tts",
            cloudApproved = false, language = locale.toLanguageTag(), beforeSentence = { stillCurrent(day) })
    }

    fun keep() {
        val day = report ?: return
        if (keepBusy || spoken.isBlank() || !stillCurrent(day)) return
        keepBusy = true
        scope.launch {
            try {
                val adult = Graph.kidsPreferences.adultActionRevision()
                EverydayWriteAuthority.run({ Graph.kidsPreferences.requireAdultAction(adult); check(stillCurrent(day)) }) {
                    Graph.appDatabase.withTransaction {
                        EverydayWriteAuthority.requireCurrent()
                        Graph.memoryRepository.promoteCandidate(MemoryCandidate(content = "$dateLabel · ${day.date}\n$spoken"))
                        EverydayWriteAuthority.requireCurrent()
                    }
                }
                status = "Kept in Memory."
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { status = "Could not keep this report. Try again." }
              finally { keepBusy = false }
        }
    }

    DisposableEffect(Unit) {
        onDispose { session?.let(Graph.readAloud::stopSession) }
    }
    LaunchedEffect(report, reading?.sessionToken) {
        if (report == null) session?.let(Graph.readAloud::stopSession)
    }
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.day.read", uiText("Read it to me"), HaloGlyph.SPEAK, report != null) { speak() },
        ShellPageAction("page.day.keep", uiText("Keep"), HaloGlyph.NOTE, report != null && !keepBusy) { keep() },
        ShellPageAction("page.day.choices", uiText("What goes in your day"), HaloGlyph.SETTINGS, familyReady && !kids) { editingChoices = true },
        ShellPageAction("page.day.briefs", uiText("Saved briefs"), HaloGlyph.ACTIVITY, familyReady && !kids) { onBriefs() },
    ))
    if (LocalShellHaloOrb.current) BindShellUtilityBand(owner) { ToolZoneHeader(uiText("Your day in halo"), onBack) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            if (!LocalShellHaloOrb.current) TextButton(onBack) { Text(uiText("Back")) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayReportPeriod.entries.forEach { value ->
                    FilterChip(period == value, { selected = value.name; status = null; session?.let(Graph.readAloud::stopSession) },
                        label = { Text(uiText(value.label)) })
                }
            }
            Zone5PageHeading(dateLabel)
            if (sentences.isNotEmpty()) {
                val accent = dayAccentColors()
                val annotated = buildAnnotatedString {
                    sentences.forEachIndexed { index, sentence ->
                        if (index > 0) append(' ')
                        val tone = sentence.tone
                        if (tone == DayMomentTone.ORDINARY) append(sentence.text)
                        else withStyle(SpanStyle(color = if (tone == DayMomentTone.DONE) accent.first else accent.second)) {
                            append(sentence.text)
                        }
                    }
                }
                Text(annotated, style = MaterialTheme.typography.bodyLarge)
            } else if (!error) Text(uiText("Putting your day together…"), style = MaterialTheme.typography.bodyMedium)
            Text(uiText("Made on this phone · only you see it"), Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) TextButton({ refresh++ }) { Text(uiText("Could not open your report. Try again.")) }
        }
        if (report != null) {
            dayReportCards(
                day = report,
                hidden = choices.hidden,
                onWhy = { explanation = it },
                onHide = { card ->
                    if (stillCurrent(report)) {
                        val result = runCatching { service.settings.save(choices.copy(hidden = choices.hidden + card)) }
                        if (result.isFailure) status = "Could not hide this card. Try again."
                    }
                },
                onOpen = onOpen,
                onOpenPhoto = { photo ->
                    if (stillCurrent(report)) onOpen("notebook/note/" + android.net.Uri.encode(photo.id))
                },
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report != null) {
                    val readingNow = reading?.sessionToken != null && reading?.sessionToken == session
                    OutlinedButton({ if (readingNow) session?.let(Graph.readAloud::stopSession) else speak() },
                        Modifier.heightIn(min = 48.dp)) { Text(uiText(if (readingNow) "Stop reading" else "Read it to me")) }
                    Button(::keep, Modifier.heightIn(min = 48.dp), enabled = !keepBusy) { Text(uiText("Keep")) }
                    TextButton({ report?.takeIf(::stillCurrent)?.let { sharing = it to "$dateLabel\n$spoken" } },
                        Modifier.heightIn(min = 48.dp)) { Text(uiText("Share")) }
                }
                TextButton({ editingChoices = true }, Modifier.heightIn(min = 48.dp)) { Text(uiText("What goes in your day")) }
                TextButton(onBriefs, Modifier.heightIn(min = 48.dp)) { Text(uiText("Saved briefs")) }
            }
            status?.let { Text(uiText(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
    if (editingChoices && familyReady && !kids) ReportChoicesSheet(choices, onClose = { editingChoices = false }) { value ->
        if (!Graph.kidsPreferences.loadedState.value || Graph.kidsPreferences.enabled.value) return@ReportChoicesSheet
        if (runCatching { service.settings.save(value) }.isSuccess) editingChoices = false
        else status = "Could not save your choices. Try again."
    }
    explanation?.takeIf { familyReady && !kids }?.let { card ->
        HaloAlertDialog(onDismissRequest = { explanation = null }, title = { Text(uiText("Why am I seeing this?")) },
            text = { Text(uiText(card.explanation)) },
            confirmButton = { TextButton({ explanation = null }) { Text(uiText("Close")) } })
    }
    sharing?.takeIf { stillCurrent(it.first) }?.let { (day, preview) ->
        HaloAlertDialog(onDismissRequest = { sharing = null }, title = { Text(uiText("Share this summary?")) },
            text = { Text(preview) },
            confirmButton = {
                TextButton({
                    sharing = null
                    if (stillCurrent(day)) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, preview)
                        if (runCatching { context.startActivity(Intent.createChooser(send, null)) }.isFailure)
                            status = "Could not open sharing."
                    }
                }) { Text(uiText("Choose where to share")) }
            },
            dismissButton = { TextButton({ sharing = null }) { Text(uiText("Cancel")) } })
    }
}

private fun LazyListScope.dayReportCards(
    day: DayReport,
    hidden: Set<DayReportCard>,
    onWhy: (DayReportCard) -> Unit,
    onHide: (DayReportCard) -> Unit,
    onOpen: (String) -> Unit,
    onOpenPhoto: (DayPhoto) -> Unit,
) {
    fun visible(card: DayReportCard) = card !in hidden
    if (day.timeline.isNotEmpty() && visible(DayReportCard.TIMELINE)) item {
        ReportCard(DayReportCard.TIMELINE, onWhy, onHide) { DayMoments(day, day.timeline, onOpen) }
    }
    if (day.places.isNotEmpty() && visible(DayReportCard.PLACES)) item {
        ReportCard(DayReportCard.PLACES, onWhy, onHide) {
            day.places.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text(uiText("From location tags in your saved captures."), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (day.photos.isNotEmpty() && visible(DayReportCard.PHOTOS)) item {
        ReportCard(DayReportCard.PHOTOS, onWhy, onHide) {
            DayReportPhotos(day.photos, onOpenPhoto)
        }
    }
    if ((day.focusMinutes > 0 || (day.screenMinutes ?: 0) > 0) && visible(DayReportCard.FOCUS)) item {
        ReportCard(DayReportCard.FOCUS, onWhy, onHide) {
            if (day.focusMinutes > 0) Text(uiText("%s min focused", day.focusMinutes), style = MaterialTheme.typography.titleLarge)
            day.screenMinutes?.takeIf { it > 0 }?.let { Text(uiText("%s min screen active", it)) }
        }
    }
    if (day.needs.isNotEmpty() && visible(DayReportCard.NEEDS)) item {
        ReportCard(DayReportCard.NEEDS, onWhy, onHide) { DayMoments(day, day.needs, onOpen) }
    }
    if (day.tomorrow.isNotEmpty() && visible(DayReportCard.TOMORROW)) item {
        ReportCard(DayReportCard.TOMORROW, onWhy, onHide,
            heading = if (day.period == DayReportPeriod.YESTERDAY) uiText("Plans after this day") else null) {
            DayMoments(day, day.tomorrow, onOpen)
        }
    }
    if (day.news.isNotEmpty() && visible(DayReportCard.NEWS)) item {
        ReportCard(DayReportCard.NEWS, onWhy, onHide) { DayMoments(day, day.news, onOpen) }
    }
}

@Composable
private fun DayReportPhotos(photos: List<DayPhoto>, onOpen: (DayPhoto) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.take(3).forEach { photo ->
            AsyncImage(photo.uri, photo.title,
                Modifier.weight(1f).aspectRatio(1.25f).clip(MaterialTheme.shapes.medium).clickable { onOpen(photo) },
                contentScale = ContentScale.Crop)
        }
        repeat(3 - photos.size.coerceAtMost(3)) { Spacer(Modifier.weight(1f)) }
    }
    Text(uiText("%s saved captures", photos.size), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun daySentences(day: DayReport): List<ReportSentence> {
    val observations = buildList {
        if (day.scheduled > 0) add(ReportSentence(if (day.scheduled == 1) uiText("You had one event on your plan.")
            else uiText("You had %s events on your plan.", day.scheduled)))
        if (day.completed > 0) add(ReportSentence(if (day.completed == 1) uiText("You checked off one task.")
            else uiText("You checked off %s tasks.", day.completed), DayMomentTone.DONE))
        if (day.photos.isNotEmpty()) add(ReportSentence(if (day.photos.size == 1) uiText("You saved one picture from your camera.")
            else uiText("You saved %s pictures from your camera.", day.photos.size)))
        if (day.focusMinutes > 0) add(ReportSentence(uiText("You spent %s minutes in focus.", day.focusMinutes)))
        if ((day.screenMinutes ?: 0) > 0) add(ReportSentence(uiText("Your screen was active for %s minutes.", requireNotNull(day.screenMinutes))))
        if (day.places.isNotEmpty()) add(ReportSentence(uiText("Your saved photos have location tags.")))
        if (day.news.isNotEmpty()) add(ReportSentence(uiText("There are saved headlines from your selected sources.")))
        if (day.tomorrow.isNotEmpty()) add(ReportSentence(uiText("You have plans for the following day.")))
        if (day.needs.isNotEmpty()) add(ReportSentence(if (day.needs.size == 1) uiText("One item still needs you.")
            else uiText("%s items still need you.", day.needs.size), DayMomentTone.NEEDS))
    }
    if (observations.isEmpty()) return listOf(ReportSentence(uiText("There are no saved moments for this period from the sources you chose.")))
    val firstTwo = observations.filter { it.tone != DayMomentTone.NEEDS }.take(2)
    return (firstTwo + observations.filter { it.tone == DayMomentTone.NEEDS }).ifEmpty { observations.take(2) }
}

@Composable
private fun dayAccentColors(): Pair<Color, Color> = if (MaterialTheme.colorScheme.surface.luminance() < .35f)
    HaloMint to HaloSunriseApricot else HaloDeepMint to Color(0xFF914511)

@Composable
private fun ReportCard(card: DayReportCard, onWhy: (DayReportCard) -> Unit, onHide: (DayReportCard) -> Unit,
    heading: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(heading ?: uiText(card.label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Box {
                    TextButton({ menu = true }, Modifier.heightIn(min = 48.dp)) { Text(uiText("More")) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(uiText("Why am I seeing this?")) }, { menu = false; onWhy(card) })
                        DropdownMenuItem({ Text(uiText("Hide this card")) }, { menu = false; onHide(card) })
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun DayMoments(report: DayReport, moments: List<DayMoment>, navigate: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val style = if (report.period == DayReportPeriod.WEEK) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val formatter = style.withLocale(locale).withZone(report.zone)
    val colors = dayAccentColors()
    var expanded by rememberSaveable(moments.firstOrNull()?.id, report.period) { mutableStateOf(false) }
    moments.take(if (expanded) 150 else 6).forEach { moment ->
        val route = moment.route
        val accent = when (moment.tone) {
            DayMomentTone.DONE -> colors.first
            DayMomentTone.NEEDS -> colors.second
            DayMomentTone.ORDINARY -> MaterialTheme.colorScheme.outline
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (route == null) Modifier else Modifier.clickable { navigate(route) }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Column {
                Text(moment.title, style = MaterialTheme.typography.bodyLarge)
                val stamp = if (moment.allDay) uiText("All day") else moment.at?.let { formatter.format(it) }
                val tone = when (moment.tone) { DayMomentTone.DONE -> uiText("Checked off"); DayMomentTone.NEEDS -> uiText("Needs you"); else -> null }
                Text(listOfNotNull(stamp, moment.detail, tone).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (moments.size > 6) TextButton({ expanded = !expanded }) {
        Text(uiText(if (expanded) "Show less" else "Show more"))
    }
}

@Composable
private fun ReportChoicesSheet(value: DayReportChoices, onClose: () -> Unit, onSave: (DayReportChoices) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    val context = LocalContext.current
    EverydaySheet(onDismiss = onClose) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(uiText("What goes in your day"), style = MaterialTheme.typography.headlineSmall)
                EverydayToggle(uiText("Plans and ticks"), draft.plans, true) { draft = draft.copy(plans = it) }
                EverydayToggle(uiText("Messages that need you"), draft.messages, true) { draft = draft.copy(messages = it) }
                EverydayToggle(uiText("Photos you took"), draft.photos, true) { draft = draft.copy(photos = it) }
                EverydayToggle(uiText("Where you went"), draft.places, true) { draft = draft.copy(places = it) }
                EverydayToggle(uiText("Focus and screen time"), draft.focus, true) { draft = draft.copy(focus = it) }
                if (draft.focus && android.os.Build.VERSION.SDK_INT >= 28 && !screenTimeAvailable(context)) {
                    Text(uiText("Choose Usage access in Android settings to include screen time."), style = MaterialTheme.typography.bodySmall)
                    TextButton({
                        runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    }) { Text(uiText("Open Usage access")) }
                }
                EverydayToggle(uiText("News from your sources"), draft.news, true) { draft = draft.copy(news = it) }
                if (draft.hidden.isNotEmpty()) TextButton({ draft = draft.copy(hidden = emptySet()) }) { Text(uiText("Show hidden cards again")) }
                Text(uiText("Ready after 21:00 · or ask “How was my day?” any time."), style = MaterialTheme.typography.bodySmall)
                Button({ onSave(draft) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Save")) }
            }
        }
    }
}
