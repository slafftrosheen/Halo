package com.halo.app.ui.instruments

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.adaptive.AdaptiveSurfaceControl
import com.halo.app.ui.adaptive.rememberAdaptiveSurfaceLayout
import com.halo.app.ui.adaptive.v13.Block
import com.halo.app.ui.adaptive.v13.ExperienceLevel
import com.halo.app.ui.adaptive.v13.Slot
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.theme.HaloGuidanceAmount
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.rememberVisualPreferences
import kotlinx.coroutines.delay

data class HaloInstrumentAction(
    val run: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val working: Boolean = false,
    val keyboardAction: ((AndroidKeyEvent) -> Unit)? = null,
)

private val lilac = Color(0xFFBDA9F6)
private val mint = Color(0xFF55D6B2)
private val adaptiveInstrumentAliases = mapOf(
    "canvas" to mapOf("draw" to "canvas.pen_highlighter", "brushes" to "canvas.pen_highlighter",
        "shapes" to "canvas.shape", "layers_peek" to "canvas.layers_panel", "layers_panel" to "canvas.layers_panel"),
    "studio" to mapOf("adjust" to "studio.fix_the_light_halo", "crop" to "studio.crop", "text" to "studio.text",
        "layers" to "studio.layers_panel", "selection" to "studio.rectangle_ellipse_select", "fill" to "studio.fill",
        "menus" to "studio.layers_panel"),
    "notes" to mapOf("speak" to "notes.dictate", "photo" to "notes.insert_picture", "sketch" to "notes.draw",
        "lists" to "notes.lists", "links" to "notes.link", "templates" to "notes.templates"),
    "planner" to mapOf("today_list" to "planner.today_back_next", "add_by_voice" to "planner.add_by_talking",
        "week" to "planner.views", "leave_by" to "planner.leave_by_time", "month" to "planner.mini_month",
        "calendars" to "planner.calendars", "tomorrow" to "planner.tomorrow"),
    "translate" to mapOf("talk" to "translate.talk", "point_camera" to "translate.camera", "type" to "translate.type",
        "phrasebook" to "translate.phrasebook", "offline_packs" to "translate.offline"),
    "scan" to mapOf("scan" to "scan.capture", "pages" to "scan.rotate_reorder", "tidy" to "scan.edges",
        "file_to" to "scan.save_pdf", "ocr_layers" to "scan.found_text"),
    "study" to mapOf("today_step" to "study.lesson_view", "ask_coach" to "study.hint", "plan" to "study.course_outline",
        "cards" to "study.flashcards", "short_review" to "study.flashcards"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HaloInstrumentBar(
    tool: String,
    actions: Map<String, HaloInstrumentAction>,
    modifier: Modifier = Modifier,
    primary: String? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = remember(context) { context.instrumentActivity() }
    val visual = rememberVisualPreferences()
    val motion = HaloInstrumentMotion.level()
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    val prefs = remember(context) { context.getSharedPreferences("halo_instrument_guidance", Context.MODE_PRIVATE) }
    val instruments = remember(tool) { HaloInstrumentCatalogue.entries(context.resources, tool) }
    if (instruments.isEmpty()) return
    val toolTint = instrumentTint(tool)
    val actionsById by rememberUpdatedState(actions)
    val usable = instruments.filter { it.id in actions }
    val defaultLevel = when (visual.viewMode) {
        HaloViewMode.EASY -> InstrumentLevel.EASY
        HaloViewMode.STANDARD -> InstrumentLevel.STANDARD
        HaloViewMode.COMPACT -> InstrumentLevel.PRO
    }
    var chosenLevel by rememberSaveable(tool) {
        mutableStateOf(prefs.getString("$tool.level", null))
    }
    val loadedFamily by Graph.kidsPreferences.loadedState.collectAsState()
    val kids by Graph.kidsPreferences.enabled.collectAsState()
    val layoutLocked = com.halo.app.ui.theme.rememberHaloLayoutLocked()
    val configuredLevel = if (kids) InstrumentLevel.EASY else InstrumentLevel.entries.find { it.name == chosenLevel } ?: defaultLevel
    var showAll by rememberSaveable(tool) { mutableStateOf(false) }
    val level = if (showAll && !kids) InstrumentLevel.PRO else configuredLevel
    val depthHistory = remember(tool, prefs) { InstrumentDepthHistory(prefs, tool) }
    var levelOffer by remember(tool) { mutableStateOf<com.halo.app.ui.adaptive.v13.LevelDecision.Offer?>(null) }
    var why by remember(tool) { mutableStateOf(false) }
    var disableOffers by rememberSaveable(tool) { mutableStateOf(prefs.getBoolean("$tool.depth.quiet", false)) }
    val canOffer = loadedFamily && !kids && !layoutLocked && !disableOffers && visual.guidanceAmount != HaloGuidanceAmount.OFF
    fun assessDepth(advanced: Boolean = false, askedForHelp: Boolean = false) {
        if (!canOffer) return
        val today = java.time.LocalDate.now()
        depthHistory.visit(today, advanced, askedForHelp)
        if (levelOffer == null) {
            levelOffer = depthHistory.offer(today, configuredLevel, chosenLevel != null)
            if (levelOffer != null) depthHistory.shown(today)
        }
    }
    LaunchedEffect(tool, canOffer) {
        if (canOffer) assessDepth() else levelOffer = null
    }
    val haptic = LocalHapticFeedback.current
    var openGroup by rememberSaveable(tool) { mutableStateOf<String?>(null) }
    var toolsOpen by rememberSaveable(tool) { mutableStateOf(false) }
    var help by rememberSaveable(tool) { mutableStateOf(false) }
    var demonstration by rememberSaveable(tool) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(tool) { mutableStateOf("") }
    var target by rememberSaveable(tool) { mutableStateOf<String?>(null) }
    val experienceLevel = ExperienceLevel.entries[level.ordinal]
    val slots = usable.map { spec ->
        Block(spec.id, Slot.TOOLS, primary = spec.id == primary, availableAtLevel = spec.isVisible(level) || spec.id == target)
    }
    val layout = rememberAdaptiveSurfaceLayout(tool, slots, adaptiveInstrumentAliases[tool].orEmpty(),
        experienceLevel, layoutLocked || kids || !loadedFamily,
        availableTargets = actions.filterValues { it.enabled && !it.working }.keys)
    val specsById = usable.associateBy(InstrumentSpec::id)
    val shown = layout.result.visible.mapNotNull { specsById[it.id] }
    val groups = shown.groupBy { it.group }
    var levelChanged by remember(tool) { mutableStateOf(false) }
    var tag by remember(tool) { mutableStateOf<String?>(null) }
    var confirmation by remember(tool) { mutableStateOf<String?>(null) }
    var receipt by remember(tool) { mutableStateOf<String?>(null) }
    var tour by rememberSaveable(tool) { mutableIntStateOf(-1) }
    var nudges by rememberSaveable(tool) { mutableStateOf(prefs.getBoolean("$tool.nudges", true)) }
    var taskActive by rememberSaveable(tool) { mutableStateOf(prefs.getBoolean("$tool.task", false)) }
    var taskChecked by remember(tool) { mutableStateOf(prefs.getStringSet("$tool.steps", null).orEmpty().toSet()) }
    val attempts = remember(tool) { mutableMapOf<String, Int>() }
    val task = remember(usable.map { it.id }, primary) {
        val preferred = if (tool == "sheets") listOf("sheets.cell_select_range", "sheets.formula_bar", "sheets.add_up_button", "sheets.charts")
            else listOfNotNull(primary)
        (preferred.mapNotNull { id -> usable.find { it.id == id } } + usable)
            .distinctBy { it.id }.filterNot { it.hardStop }.take(4)
    }
    val primaryInstrument = specsById[layout.result.primary?.id] ?: shown.firstOrNull() ?: usable.firstOrNull()

    fun instrumentName(spec: InstrumentSpec) = if (russian) spec.russianName else spec.name
    fun instrumentDescription(spec: InstrumentSpec) = if (russian) spec.russianDescription else spec.description
    fun selectLevel(value: InstrumentLevel) {
        if (kids || layoutLocked) return
        showAll = false
        levelOffer = null
        if (configuredLevel == value && chosenLevel == value.name) return
        chosenLevel = value.name
        openGroup = null
        target = null
        tag = null
        levelChanged = true
        prefs.edit().putString("$tool.level", value.name).apply()
    }
    fun finishTour() {
        prefs.edit().putBoolean("$tool.toured", true).apply()
        tour = -1
    }
    fun openHelp() {
        assessDepth(askedForHelp = true)
        query = ""
        toolsOpen = false
        help = true
    }
    fun reveal(spec: InstrumentSpec) {
        openGroup = spec.group
        help = false
        target = spec.id
        tag = spec.id
        receipt = null
    }
    fun activate(spec: InstrumentSpec, confirmed: Boolean = false, keyEvent: AndroidKeyEvent? = null) {
        val state = actionsById[spec.id] ?: return
        if (!state.enabled || state.working) return
        if (spec.hardStop && !confirmed) {
            confirmation = spec.id
            return
        }
        val shortcutAction = state.keyboardAction
        if (keyEvent != null && shortcutAction != null) shortcutAction(keyEvent) else state.run()
        toolsOpen = false
        if (!spec.isVisible(configuredLevel)) assessDepth(advanced = true)
        if (visual.haptics && motion != HaloLookMotion.STILL) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (taskActive && task.any { it.id == spec.id }) {
            taskChecked = taskChecked + spec.id
            prefs.edit().putStringSet("$tool.steps", taskChecked).apply()
        }
        val count = attempts.getOrDefault(spec.id, 0) + 1
        attempts[spec.id] = count
        if (target == spec.id) {
            target = null
            tag = null
            receipt = if (russian) "Понятно" else "Got it"
        } else if (count == 3 && nudges && visual.guidanceAmount != HaloGuidanceAmount.OFF && !prefs.getBoolean("$tool.nudged", false)) {
            prefs.edit().putBoolean("$tool.nudged", true).apply()
            tag = spec.id
        }
    }
    fun handleShortcut(event: AndroidKeyEvent): Boolean {
        if (help || confirmation != null || event.action != AndroidKeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        val spec = usable.firstOrNull {
            val state = actionsById[it.id]
            state?.enabled == true && !state.working && it.matchesShortcut(event)
        } ?: return false
        activate(spec, keyEvent = event)
        return true
    }
    val latestShortcut by rememberUpdatedState<(AndroidKeyEvent) -> Boolean>(::handleShortcut)
    val latestHelp by rememberUpdatedState<() -> Unit>(::openHelp)
    DisposableEffect(activity, lifecycle, tool) {
        val unregister = activity?.let {
            HaloInstrumentKeyboard.register(it,
                active = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
                handler = { latestShortcut(it) }, openHelp = { latestHelp() })
        }
        onDispose { unregister?.invoke() }
    }
    LaunchedEffect(receipt) {
        if (receipt != null && target == null) {
            delay(HaloInstrumentMotion.UNDO_NOTICE)
            receipt = null
        }
    }

    val wide = LocalConfiguration.current.screenWidthDp >= 650
    val selectedShape = RoundedCornerShape(18.dp)
    val adjustedIds = layout.result.applied.map { it.change.blockId }.toSet()
    val quick = (listOfNotNull(specsById[target]) + shown.filter { it.id in adjustedIds } +
        listOfNotNull(primaryInstrument) + shown.filter { actions[it.id]?.selected == true } + shown)
        .distinctBy { it.id }.take(5)
    Row(modifier.fillMaxWidth().onPreviewKeyEvent { handleShortcut(it.nativeKeyEvent) }, verticalAlignment = Alignment.CenterVertically) {
        CandidateRow(quick, level, null, actions, toolTint, motion, russian, target,
            { activate(it) }, { tag = it; toolsOpen = true }, showLabels = level == InstrumentLevel.EASY,
            modifier = Modifier.weight(1f))
        TextButton(onClick = { toolsOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Tools")) }
    }
    if (toolsOpen) ModalBottomSheet(onDismissRequest = { toolsOpen = false }, scrimColor = Color.Black.copy(alpha = .40f)) {
    Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState())
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f), selectedShape)
        .padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (!kids) InstrumentLevel.entries.forEach { item ->
                val label = stringResource(when (item) {
                    InstrumentLevel.EASY -> R.string.f13_depth_simple
                    InstrumentLevel.STANDARD -> R.string.f13_depth_standard
                    InstrumentLevel.PRO -> R.string.f13_depth_pro
                })
                FilterChip(selected = item == configuredLevel, onClick = { selectLevel(item) }, enabled = !layoutLocked, label = { Text(label) },
                    modifier = Modifier.padding(end = 6.dp))
            }
            TextButton(onClick = ::openHelp) { Text(stringResource(R.string.shell_find_action)) }
        }
        if (!kids && configuredLevel != InstrumentLevel.PRO && usable.any { !it.isVisible(configuredLevel) }) {
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (showAll) R.string.f13_show_less else R.string.f13_show_more))
            }
        }
        val offer = levelOffer
        if (offer != null && canOffer) {
            val up = offer.to.ordinal > offer.from.ordinal
            Text(stringResource(if (up) R.string.f13_offer_up_title else R.string.f13_offer_down_title),
                style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (up) R.string.f13_offer_up_body else R.string.f13_offer_down_body),
                style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { selectLevel(InstrumentLevel.entries[offer.to.ordinal]) }) {
                    Text(stringResource(R.string.f13_offer_yes))
                }
                TextButton(onClick = { levelOffer = null }) { Text(stringResource(R.string.f13_offer_no)) }
                TextButton(onClick = { why = true }) { Text(stringResource(R.string.f13_why_title)) }
            }
            if (why) AlertDialog(onDismissRequest = { why = false },
                title = { Text(stringResource(R.string.f13_why_title)) },
                text = { Text(stringResource(if (up) R.string.f13_offer_up_body else R.string.f13_offer_down_body)) },
                confirmButton = { TextButton(onClick = {
                    disableOffers = true
                    prefs.edit().putBoolean("$tool.depth.quiet", true).apply()
                    levelOffer = null
                    why = false
                }) { Text(stringResource(R.string.f13_stop_changing)) } },
                dismissButton = { TextButton(onClick = { why = false }) { Text(stringResource(android.R.string.cancel)) } })
        }

        AdaptiveSurfaceControl(layout)
        val keyboardMenus = tool == "studio" && layout.result.applied.any { it.id == "KEYBOARD:menus" }
        OrderedToolMenus(tool, level == InstrumentLevel.EASY && !keyboardMenus, shown, { it.menu }, { instrumentName(it) },
            enabled = { actions[it.id]?.let { action -> action.enabled && !action.working } == true },
            onAction = { activate(it) }, onHelp = ::openHelp)

        Crossfade(level, animationSpec = HaloInstrumentMotion.spec(HaloInstrumentMotion.CALM, motion), label = "Instrument level") { areaLevel ->
            CandidateRow(if (areaLevel == level) shown else usable.filter { it.isVisible(areaLevel) || it.id == target }, areaLevel,
                if (areaLevel == InstrumentLevel.PRO && wide) openGroup else null,
                actions, toolTint, motion, russian, target, { spec -> activate(spec) }, { tag = it }, showLabels = areaLevel == InstrumentLevel.EASY)
        }
        val revealed = usable.firstOrNull { it.id == target && !it.isVisible(level) }
        if (revealed != null) TextButton(
            onClick = { activate(revealed) }, enabled = actions[revealed.id]?.let { it.enabled && !it.working } == true,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(instrumentName(revealed)) }
        if (levelChanged) {
            LaunchedEffect(level) { delay(1200); levelChanged = false }
            com.halo.app.ui.theme.v13.Halo13Animation(com.halo.app.ui.theme.v13.Halo13Motion.LEVEL_SHIFT,
                Modifier.fillMaxWidth().height(32.dp))
        }

        if (groups.size > 1 && (wide || level != InstrumentLevel.EASY)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { (group, _) ->
                    FilterChip(selected = openGroup == group, onClick = { openGroup = group.takeUnless { it == openGroup } },
                        label = { Text(instrumentGroup(group, russian)) })
                }
            }
        }
        AnimatedVisibility(openGroup != null && openGroup in groups && !(wide && level == InstrumentLevel.PRO),
            enter = HaloInstrumentMotion.menuEnter(motion), exit = HaloInstrumentMotion.panelExit(motion)) {
            CandidateRow(groups[openGroup].orEmpty(), level, null, actions, toolTint, motion, russian, target,
                { spec -> activate(spec) }, { tag = it }, showLabels = true)
        }

        val tagged = instruments.firstOrNull { it.id == tag }
        InstrumentNameTag(tagged, target, actions, russian, motion,
            expandedHelp = visual.guidanceAmount == HaloGuidanceAmount.EXPLANATIONS,
            onClose = { tag = null; target = null; receipt = null })

        receipt?.takeIf { target == null }?.let {
            Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
        }

        if (tour in 0..4) {
            val copy = tourSentence(tour, primaryInstrument?.let(::instrumentName), russian)
            InstrumentTourStep(tour, copy, russian, onRead = { readGuidance(tool, copy, russian) }, onSkip = ::finishTour,
                onNext = {
                    if (tour == 1 && primaryInstrument != null) reveal(primaryInstrument)
                    if (tour == 4) finishTour() else tour++
                })
        }

        if (taskActive && task.isNotEmpty()) {
            GuidancePanel {
                Text(if (russian) "Попробуйте по шагам" else "Try it step by step", fontWeight = FontWeight.SemiBold)
                task.forEach { spec ->
                    TextButton(onClick = { reveal(spec) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text((if (spec.id in taskChecked) "✓  " else "○  ") + instrumentName(spec))
                    }
                }
            }
        }
    }
    }

    if (help) InstrumentHelpSheet(
        tool, usable, actions.keys, russian, query, { query = it },
        taskAvailable = task.isNotEmpty(), taskActive = taskActive, nudges = nudges,
        onToggleTask = {
            taskActive = !taskActive
            prefs.edit().putBoolean("$tool.task", taskActive).apply()
            help = false
        }, onReplay = { help = false; tour = 0; toolsOpen = true }, onToggleNudges = {
            nudges = !nudges
            prefs.edit().putBoolean("$tool.nudges", nudges).apply()
        }, onClose = { help = false }, onReveal = { spec, watch ->
            if (watch) {
                help = false
                demonstration = spec.id
            } else reveal(spec)
        })

    usable.firstOrNull { it.id == demonstration }?.let { spec ->
        InstrumentDemonstration(spec, toolTint, motion, russian,
            onRead = { readGuidance(tool, instrumentDescription(spec), russian) },
            onClose = { demonstration = null; help = true },
            onTry = { demonstration = null; reveal(spec) })
    }

    val toConfirm = instruments.firstOrNull { it.id == confirmation }
    if (toConfirm != null) InstrumentConfirmSheet(instrumentName(toConfirm), instrumentDescription(toConfirm),
        onClose = { confirmation = null }, onConfirm = { confirmation = null; activate(toConfirm, confirmed = true) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentDemonstration(
    spec: InstrumentSpec, tint: Color, motion: HaloLookMotion, russian: Boolean,
    onRead: () -> Unit, onClose: () -> Unit, onTry: () -> Unit,
) {
    var replay by remember(spec.id) { mutableIntStateOf(0) }
    var step by remember(spec.id) { mutableIntStateOf(0) }
    LaunchedEffect(spec.id, replay) {
        step = 0
        delay(550)
        step = 1
        delay(850)
        step = 2
    }
    val name = if (russian) spec.russianName else spec.name
    val background by animateColorAsState(if (step == 2) tint.copy(alpha = .28f) else Color.Transparent,
        HaloInstrumentMotion.spec(HaloInstrumentMotion.QUICK, motion), label = "Preview surface")
    val border by animateColorAsState(if (step == 1) lilac else Color.Transparent,
        HaloInstrumentMotion.spec(HaloInstrumentMotion.QUICK, motion), label = "Preview focus")
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 550.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (russian) "Посмотреть и попробовать" else "Watch, then try", style = MaterialTheme.typography.titleLarge)
            Text(if (russian) spec.russianDescription else spec.description)
            val shape = RoundedCornerShape(12.dp)
            Row(Modifier.heightIn(min = 48.dp).background(background, shape).border(2.dp, border, shape)
                .padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(spec.icon), null, Modifier.size(24.dp))
                Text(name, style = MaterialTheme.typography.labelLarge)
            }
            Crossfade(step, animationSpec = HaloInstrumentMotion.spec(HaloInstrumentMotion.QUICK, motion), label = "Demo step") { current ->
                Text(when (current) {
                    0 -> if (russian) "Найдите эту кнопку" else "Find this button"
                    1 -> if (russian) "Коснитесь кнопки" else "Tap the button"
                    else -> if (russian) "Попробуйте в редакторе" else "Try it in the editor"
                }, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onTry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (russian) "Попробовать" else "Try it")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { replay++ }) { Text(if (russian) "Повторить" else "Replay") }
                TextButton(onClick = onRead) { Text(uiText("Read aloud")) }
            }
        }
    }
}

@Composable
private fun InstrumentNameTag(spec: InstrumentSpec?, target: String?, actions: Map<String, HaloInstrumentAction>,
    russian: Boolean, motion: HaloLookMotion, expandedHelp: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(spec != null, enter = HaloInstrumentMotion.panelEnter(motion), exit = HaloInstrumentMotion.panelExit(motion)) {
        if (spec == null) return@AnimatedVisibility
        val active = target == spec.id
        val name = if (russian) spec.russianName else spec.name
        GuidancePanel {
            if (expandedHelp) key(spec.id) {
                com.halo.app.ui.theme.v13.Halo13Animation(com.halo.app.ui.theme.v13.Halo13Motion.EXPLAIN_BLOOM,
                    Modifier.size(44.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text((if (active) "↑  " else "") + name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                val shortcut = spec.shortcut?.let { instrumentShortcutLabel(it, russian) }
                if (shortcut != null) Text(shortcut, style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onClose) { Text(uiText("Close")) }
            }
            Text(uiText(if (russian) spec.russianDescription else spec.description), style = MaterialTheme.typography.bodySmall)
            if (active && actions[spec.id]?.enabled != true) {
                Text(if (russian) "Станет доступно, когда будет выбран подходящий материал." else "Available when this tool has suitable material.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (spec.parameters != "—" && expandedHelp) Text(uiText(spec.parameters), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InstrumentTourStep(step: Int, copy: String, russian: Boolean, onRead: () -> Unit, onSkip: () -> Unit, onNext: () -> Unit) {
    GuidancePanel {
        Text(if (russian) "Знакомство с инструментом · ${step + 1} из 5" else "Tool tour · ${step + 1} of 5", fontWeight = FontWeight.SemiBold)
        Text(copy, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRead) { Text(uiText("Read aloud")) }
            TextButton(onClick = onSkip) { Text(if (russian) "Пропустить" else "Skip") }
            TextButton(onClick = onNext) { Text(if (step == 4) uiText("Done") else uiText("Next")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InstrumentHelpSheet(tool: String, instruments: List<InstrumentSpec>, bindingIds: Set<String>, russian: Boolean, query: String,
    onQuery: (String) -> Unit, taskAvailable: Boolean, taskActive: Boolean, nudges: Boolean, onToggleTask: () -> Unit,
    onReplay: () -> Unit, onToggleNudges: () -> Unit, onClose: () -> Unit, onReveal: (InstrumentSpec, Boolean) -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, scrimColor = Color.Black.copy(alpha = .40f)) {
        val matches = instruments.filter { it.matches(query) }.sortedBy { it.id !in bindingIds }
        Column(Modifier.fillMaxWidth().heightIn(max = 580.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.shell_find_action), style = MaterialTheme.typography.titleLarge)
            HaloTextField(query, onQuery, VoiceFieldKind.SEARCH,
                label = { Text(if (russian) "Найти инструмент" else "Find an instrument") }, maxChars = 100)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReplay) { Text(if (russian) "Повторить знакомство" else "Replay tour") }
                if (taskAvailable) TextButton(onClick = onToggleTask) { Text(if (taskActive) uiText("Hide guided task") else uiText("Guided task")) }
                FilterChip(selected = nudges, onClick = onToggleNudges, label = { Text(if (russian) "Мягкие подсказки" else "Gentle nudges") })
            }
            if (matches.isEmpty()) Text(if (russian) "Ничего не найдено" else "No instruments found")
            matches.forEach { spec ->
                val hasControl = spec.id in bindingIds
                GuidancePanel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(spec.icon), null, Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (russian) spec.russianName else spec.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(spec.menu.title(tool), instrumentGroup(spec.group, russian),
                                spec.shortcut?.let { instrumentShortcutLabel(it, russian) }).distinct().joinToString(" › "),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (hasControl) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onReveal(spec, false) }) { Text(uiText("Show me")) }
                        TextButton(onClick = { onReveal(spec, true) }) {
                            Text(if (russian) "Посмотреть и попробовать" else "Watch, then try")
                        }
                    } else Text(if (russian) "Недоступно в этом редакторе" else "Unavailable in this editor", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentConfirmSheet(name: String, description: String, onClose: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, scrimColor = Color.Black.copy(alpha = .40f)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(uiText(description))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) { Text(uiText("Cancel")) }
                Button(onClick = onConfirm) { Text(uiText("Continue")) }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    values: List<InstrumentSpec>, level: InstrumentLevel, group: String?, actions: Map<String, HaloInstrumentAction>,
    tint: Color, motion: HaloLookMotion, russian: Boolean, target: String?,
    activate: (InstrumentSpec) -> Unit, showTag: (String) -> Unit, showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val filtered = if (group == null) values else values.filter { it.group == group }
    val controls = when (level) {
        InstrumentLevel.EASY -> filtered
        InstrumentLevel.STANDARD -> if (showLabels) filtered else filtered.take(7) + filtered.drop(7).filter { it.id == target || actions[it.id]?.selected == true }
        InstrumentLevel.PRO -> filtered
    }
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (spec in controls) {
            val state = actions[spec.id] ?: continue
            key(spec.id) {
                InstrumentControl(spec, state, tint, motion, russian, target == spec.id, showLabels,
                    onActivate = { activate(spec) }, showTag = { showTag(spec.id) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstrumentControl(
    spec: InstrumentSpec, state: HaloInstrumentAction, tint: Color, motion: HaloLookMotion, russian: Boolean,
    targeted: Boolean, showLabel: Boolean, onActivate: () -> Unit, showTag: () -> Unit,
) {
    val label = if (russian) spec.russianName else spec.name
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bring = remember { BringIntoViewRequester() }
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(when {
        state.working -> mint.copy(alpha = .24f)
        state.selected -> tint.copy(alpha = .28f)
        hovered -> tint.copy(alpha = .15f)
        else -> Color.Transparent
    }, HaloInstrumentMotion.spec(HaloInstrumentMotion.QUICK, motion), label = "Instrument state")
    val movingEdge by animateDpAsState(if (state.selected) 3.dp else 0.dp,
        if (motion == HaloLookMotion.FULL) spring(dampingRatio = .8f, stiffness = 400f)
        else HaloInstrumentMotion.spec(HaloInstrumentMotion.QUICK, motion), label = "Selected instrument edge")
    val edge = if (motion == HaloLookMotion.STILL) (if (state.selected) 3.dp else 0.dp) else movingEdge
    LaunchedEffect(hovered) { if (hovered) { delay(500); showTag() } }
    LaunchedEffect(targeted) { if (targeted) bring.bringIntoView() }
    Row(Modifier.bringIntoViewRequester(bring).heightIn(min = 48.dp).widthIn(min = 48.dp)
        .clip(shape).background(background)
        .then(if (focused || targeted) Modifier.border(2.dp, lilac, shape) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .hoverable(interaction)
        .combinedClickable(interactionSource = interaction, indication = LocalIndication.current,
            enabled = state.enabled && !state.working, role = Role.Button, onClick = onActivate, onLongClick = showTag)
        .semantics {
            selected = state.selected
            stateDescription = when {
                state.working -> if (russian) "Выполняется" else "Working"
                state.selected -> if (russian) "Выбрано" else "Selected"
                else -> spec.shortcut.orEmpty()
            }
        }, verticalAlignment = Alignment.CenterVertically) {
        if (edge > 0.dp) Box(Modifier.width(edge).height(32.dp).background(tint, RoundedCornerShape(3.dp)))
        Row(Modifier.alpha(if (state.enabled || state.working) 1f else .35f).padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(spec.icon), if (showLabel) null else label, Modifier.size(24.dp),
                tint = if (state.working) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            if (showLabel) Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GuidancePanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(lilac.copy(alpha = .15f), RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

private fun readGuidance(tool: String, text: String, russian: Boolean) {
    Graph.readAloud.start("instrument-guide:$tool", text, "provider.android.tts", cloudApproved = false,
        language = if (russian) "ru" else "en")
}

private fun tourSentence(step: Int, main: String?, russian: Boolean): String = if (russian) when (step) {
    0 -> "Название редактора находится над рабочей областью."
    1 -> "Меню группируют инструменты по тому, что они делают."
    2 -> main?.let { "Найдите «$it» и попробуйте на своём материале." } ?: "Откройте список инструментов рядом с рабочей областью."
    3 -> "Переключайте набор инструментов, не закрывая работу."
    else -> "Нажмите кольцо, чтобы спросить halo о работе в редакторе."
} else when (step) {
    0 -> "The editor name is above your work."
    1 -> "Menus group instruments by what they do."
    2 -> main?.let { "Find $it and try it on your material." } ?: "Open the instruments beside your work."
    3 -> "Change the instrument level while keeping your work open."
    else -> "Tap the ring to ask halo about your work."
}

private fun instrumentTint(tool: String) = when (tool) {
    "studio" -> Color(0xFFE89DBB)
    "notes", "study", "coach" -> Color(0xFFBDA9F6)
    "sheets", "planner", "maps" -> Color(0xFFEACC94)
    "canvas", "scan", "measure", "translate" -> Color(0xFF8CB6EF)
    "record", "album" -> Color(0xFFEEAB89)
    else -> Color(0xFF76C5B3)
}

private fun instrumentShortcutLabel(shortcut: String, russian: Boolean): String =
    if (!russian) shortcut else when (shortcut) {
        "arrows / ⇧" -> "Стрелки / ⇧"
        "hold" -> "Удерживать"
        "long-press" -> "Долгое нажатие"
        "Space + drag" -> "Пробел + перетаскивание"
        "Space flip" -> "Пробел — перевернуть"
        "tap / ⇧" -> "Касание / ⇧"
        else -> shortcut.replace("Space", "Пробел")
    }

private fun instrumentGroup(group: String, russian: Boolean): String = if (!russian) group else when (group) {
    "Select" -> "Выделение"
    "Paint" -> "Рисование"
    "Colour" -> "Цвет"
    "Draw" -> "Фигуры"
    "Edit" -> "Правка"
    "Image" -> "Изображение"
    "Layers" -> "Слои"
    "View" -> "Вид"
    "Home" -> "Главное"
    "Insert" -> "Вставка"
    "Layout" -> "Макет"
    "Review" -> "Проверка"
    "Tools" -> "Инструменты"
    "File" -> "Файл"
    "Grid" -> "Сетка"
    "Format" -> "Формат"
    "Data" -> "Данные"
    "Easy" -> "Просто"
    "Arrange" -> "Расположение"
    "Capture" -> "Съёмка"
    "Page" -> "Страница"
    "Text" -> "Текст"
    "Transport" -> "Воспроизведение"
    "Markers" -> "Метки"
    "Share" -> "Поделиться"
    "Go" -> "Переход"
    "Event" -> "Событие"
    "Tasks" -> "Задачи"
    "Course" -> "Курс"
    "Learn" -> "Учёба"
    "Help" -> "Помощь"
    "Try" -> "Практика"
    "Write" -> "Письмо"
    "Find" -> "Поиск"
    "Privacy" -> "Конфиденциальность"
    "Browse" -> "Обзор"
    "Lock" -> "Замок"
    "Add" -> "Добавить"
    "Plan" -> "План"
    "Keep" -> "Сохранение"
    "Think" -> "Подсказки"
    "Check" -> "Проверка"
    "Answer" -> "Ответ"
    "Practise" -> "Практика"
    "Family" -> "Семья"
    "Calculate" -> "Расчёты"
    "Convert" -> "Перевод величин"
    "Graph" -> "График"
    "Measure" -> "Измерения"
    "Talk" -> "Разговор"
    "See" -> "Распознавание"
    "Settings" -> "Настройки"
    "Time" -> "Время"
    "Calm" -> "Отдых"
    else -> group
}
