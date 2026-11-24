package com.halo.app.ui.onboarding.v13

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.AppLanguage
import com.halo.app.language.uiText
import com.halo.app.ui.adaptive.v13.ExperienceLevel
import com.halo.app.ui.adaptive.v13.StartAnswer
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.VisualPreferences
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.app.ui.theme.v13.Halo13Animation
import com.halo.app.ui.theme.v13.Halo13Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val HALO13_WELCOME_ROUTE = "welcome_13"

private val welcomeSaver = Saver<OnboardingState, ArrayList<String>>(
    save = { arrayListOf(it.step.name, it.language, it.comfort.joinToString(","), it.whoFor.joinToString(","),
        it.start?.name.orEmpty(), it.thinking?.name.orEmpty()) },
    restore = {
        OnboardingState(
            step = Step.valueOf(it[0]), language = it[1],
            comfort = it[2].split(',').filter(String::isNotEmpty).map(Comfort::valueOf).toSet(),
            whoFor = it[3].split(',').filter(String::isNotEmpty).map(WhoFor::valueOf).toSet(),
            start = StartAnswer.entries.firstOrNull { entry -> entry.name == it[4] },
            thinking = Thinking.entries.firstOrNull { entry -> entry.name == it[5] },
        )
    },
)

private data class Choice<T>(val value: T, @StringRes val title: Int, @StringRes val summary: Int, @DrawableRes val icon: Int)

private val whoChoices = listOf(
    Choice(WhoFor.ME, R.string.ob13_who_me, R.string.ob13_who_me_sub, R.drawable.halo_ic_for_me),
    Choice(WhoFor.MY_CHILD, R.string.ob13_who_child, R.string.ob13_who_child_sub, R.drawable.halo_ic_for_child),
    Choice(WhoFor.FAMILY, R.string.ob13_who_family, R.string.ob13_who_family_sub, R.drawable.halo_ic_family),
    Choice(WhoFor.STUDYING, R.string.ob13_who_study, R.string.ob13_who_study_sub, R.drawable.halo_ic_studying),
    Choice(WhoFor.CREATING, R.string.ob13_who_create, R.string.ob13_who_create_sub, R.drawable.halo_ic_creating),
)
private val depthChoices = listOf(
    Choice(StartAnswer.KEEP_IT_SIMPLE, R.string.f13_level_simple, R.string.f13_level_simple_sub, R.drawable.halo_ic_level_simple),
    Choice(StartAnswer.A_BIT_OF_EVERYTHING, R.string.f13_level_standard, R.string.f13_level_standard_sub, R.drawable.halo_ic_level_standard),
    Choice(StartAnswer.SHOW_ME_EVERYTHING, R.string.f13_level_pro, R.string.f13_level_pro_sub, R.drawable.halo_ic_level_pro),
)
private val thinkingChoices = listOf(
    Choice(Thinking.ON_THIS_PHONE, R.string.ob13_think_phone, R.string.ob13_think_phone_setup, R.drawable.halo_ic_on_device),
    Choice(Thinking.ONLINE_SERVICE, R.string.ob13_think_online, R.string.ob13_think_online_sub, R.drawable.halo_ic_cloud),
    Choice(Thinking.DECIDE_LATER, R.string.ob13_think_later, R.string.ob13_think_later_sub, R.drawable.halo_ic_finish_later),
)

@Composable
internal fun Halo13Welcome(onBackup: () -> Unit, onFinish: (OnboardingState) -> Unit) {
    val context = LocalContext.current
    val language by Graph.appLanguagePreferences.language.collectAsStateWithLifecycle()
    val font by Graph.assistantPreferences.state.collectAsStateWithLifecycle()
    val visual = rememberVisualPreferences()
    val preferences = remember(context.applicationContext) { VisualPreferences.get(context) }
    val scope = rememberCoroutineScope()
    val touchExploration = context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    val large = LocalDensity.current.fontScale >= 1.25f || touchExploration
    var state by rememberSaveable(stateSaver = welcomeSaver) { mutableStateOf(OnboardingState(language = language.tag)) }
    var languages by rememberSaveable { mutableStateOf(false) }
    var transfer by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun change(event: OnboardingEvent) { state = Onboarding.reduce(state, event) }
    fun finish(event: OnboardingEvent) {
        if (pending) return
        val answers = if (state.start == null && large) state.copy(start = StartAnswer.KEEP_IT_SIMPLE) else state
        val finished = Onboarding.reduce(answers, event).copy(language = language.tag)
        pending = true
        error = false
        scope.launch {
            try {
                val mode = when (Onboarding.level(finished)) {
                    ExperienceLevel.SIMPLE -> HaloViewMode.EASY
                    ExperienceLevel.STANDARD -> HaloViewMode.STANDARD
                    ExperienceLevel.PRO -> HaloViewMode.COMPACT
                }
                if (preferences.state.value.viewMode != mode) preferences.update { it.withViewMode(mode) }
                onFinish(finished)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally {
                pending = false
            }
        }
    }
    fun comfort(option: Comfort, value: Boolean) {
        if (pending) return
        pending = true
        error = false
        scope.launch {
            try {
                when (option) {
                    Comfort.LARGER_TEXT -> Graph.assistantPreferences.update(if (value) 1.3f else 0f, null)
                    Comfort.HIGH_CONTRAST -> preferences.update { it.copy(higherContrast = value) }
                    Comfort.LESS_MOTION -> preferences.update { it.copy(lessMotion = value) }
                    Comfort.READ_ALOUD -> Unit
                }
                change(OnboardingEvent.SetComfort(option, value))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally {
                pending = false
            }
        }
    }
    BackHandler {
        if (!pending && state.step != Step.HELLO) change(OnboardingEvent.Back)
        else if (!pending) finish(OnboardingEvent.JustOpenHalo)
    }
    val title = stringResource(when (state.step) {
        Step.HELLO -> R.string.ob13_hello_title
        Step.WHO -> R.string.ob13_who_title
        Step.DEPTH -> R.string.f13_level_title
        Step.THINKING -> R.string.ob13_think_title
    })
    val description = homeDescription(state.step)
    val steps = Step.entries
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.step == Step.HELLO) Spacer(Modifier.width(48.dp))
                else TextButton({ change(OnboardingEvent.Back) }, enabled = !pending) { Text(uiText("Back")) }
                TextButton({ finish(OnboardingEvent.JustOpenHalo) }, enabled = !pending) {
                    Text(stringResource(R.string.ob13_just_open))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEach { step ->
                    val current = step == state.step
                    Spacer(Modifier.size(if (current) 24.dp else 7.dp, 7.dp).background(
                        if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.shapes.extraLarge))
                }
            }
            if (state.step == Step.HELLO) Halo13Animation(Halo13Motion.HELLO,
                Modifier.align(Alignment.CenterHorizontally).size(116.dp))
            val hello = state.step == Step.HELLO
            val alignment = if (hello) TextAlign.Center else TextAlign.Start
            Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, textAlign = alignment)
            if (description.isNotEmpty()) Text(description, Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = alignment)
            when (state.step) {
                Step.HELLO -> {
                    Text(stringResource(R.string.ob13_comfort_title), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(font.fontScale >= 1.25f, { comfort(Comfort.LARGER_TEXT, font.fontScale < 1.25f) },
                            { Text(stringResource(R.string.ob13_comfort_larger)) }, Modifier.weight(1f), enabled = !pending)
                        FilterChip(Comfort.READ_ALOUD in state.comfort,
                            { comfort(Comfort.READ_ALOUD, Comfort.READ_ALOUD !in state.comfort) },
                            { Text(stringResource(R.string.ob13_comfort_read)) }, Modifier.weight(1f), enabled = !pending)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(visual.higherContrast, { comfort(Comfort.HIGH_CONTRAST, !visual.higherContrast) },
                            { Text(stringResource(R.string.ob13_comfort_contrast)) }, Modifier.weight(1f), enabled = !pending)
                        FilterChip(visual.lessMotion, { comfort(Comfort.LESS_MOTION, !visual.lessMotion) },
                            { Text(stringResource(R.string.ob13_comfort_motion)) }, Modifier.weight(1f), enabled = !pending)
                    }
                }
                Step.WHO -> whoChoices.forEach { choice ->
                    WelcomeChoice(choice, choice.value in state.whoFor, Role.Checkbox) {
                        change(OnboardingEvent.Toggle(choice.value))
                    }
                }
                Step.DEPTH -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        depthChoices.forEachIndexed { index, choice ->
                            val selected = choice.value == (state.start ?: if (large) StartAnswer.KEEP_IT_SIMPLE else StartAnswer.A_BIT_OF_EVERYTHING)
                            Surface(Modifier.weight(1f).height(64.dp), shape = MaterialTheme.shapes.medium,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(index + 1) {
                                        Spacer(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.primary.copy(alpha = .3f),
                                            MaterialTheme.shapes.extraSmall))
                                    }
                                }
                            }
                        }
                    }
                    depthChoices.forEach { choice ->
                        WelcomeChoice(choice, choice.value == (state.start ?: if (large) StartAnswer.KEEP_IT_SIMPLE else StartAnswer.A_BIT_OF_EVERYTHING)) {
                            change(OnboardingEvent.Depth(choice.value))
                        }
                    }
                }
                Step.THINKING -> {
                    thinkingChoices.forEach { choice ->
                        WelcomeChoice(choice, choice.value == (state.thinking ?: Thinking.DECIDE_LATER)) {
                            change(OnboardingEvent.Think(choice.value))
                        }
                    }
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(painterResource(R.drawable.halo_ic_privacy), null)
                            Text(stringResource(R.string.ob13_think_consent), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (Comfort.READ_ALOUD in state.comfort) {
                val choices = when (state.step) {
                    Step.HELLO -> emptyList()
                    Step.WHO -> whoChoices.map { it.title }
                    Step.DEPTH -> depthChoices.map { it.title }
                    Step.THINKING -> thinkingChoices.map { it.title }
                }.map { stringResource(it) }
                val read = (listOf(title, description) + choices).filter(String::isNotBlank).joinToString(". ")
                HaloVoiceTextActions(read, onText = {}, allowDictation = false, readText = read,
                    readCategory = com.halo.sdk.provider.RemoteDataCategory.TEXT)
            }
            if (error) Text(stringResource(R.string.ob13_save_failed), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    if (state.step == Step.THINKING) finish(OnboardingEvent.Next)
                    else change(OnboardingEvent.Next)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = !pending,
            ) {
                Text(when (state.step) {
                    Step.HELLO -> stringResource(R.string.ob13_hello_start)
                    Step.THINKING -> stringResource(R.string.ob13_done)
                    else -> uiText("Next")
                })
            }
            if (state.step == Step.HELLO) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ transfer = true }, modifier = Modifier.weight(1f), enabled = !pending) {
                        Text(stringResource(R.string.ob13_hello_before))
                    }
                    TextButton({ languages = true }, modifier = Modifier.weight(1f), enabled = !pending) { Text(language.nativeName) }
                }
                DropdownMenu(languages, { languages = false }) {
                    AppLanguage.entries.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.nativeName) }, onClick = {
                            Graph.appLanguagePreferences.select(choice)
                            languages = false
                        })
                    }
                }
            }
        }
    }
    if (transfer) AlertDialog(
        onDismissRequest = { transfer = false },
        title = { Text(stringResource(R.string.ob13_move_title)) },
        text = { Halo13Animation(Halo13Motion.MOVE_PHONE, Modifier.fillMaxWidth().height(128.dp)) },
        confirmButton = { TextButton({ transfer = false; onBackup() }) { Text(stringResource(R.string.ob13_move_file)) } },
        dismissButton = { TextButton({ transfer = false }) { Text(stringResource(R.string.ob13_move_fresh)) } },
    )
}

@Composable
private fun homeDescription(step: Step): String = when (step) {
    Step.HELLO -> stringResource(R.string.ob13_hello_body)
    Step.WHO -> stringResource(R.string.ob13_who_sub)
    Step.DEPTH -> stringResource(R.string.f13_level_change_any_time)
    Step.THINKING -> ""
}

@Composable
private fun <T> WelcomeChoice(choice: Choice<T>, selected: Boolean, role: Role = Role.RadioButton, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(selected, role = role, onValueChange = { onSelect() })
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(choice.icon), null, Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(choice.title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(choice.summary), style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun Halo13FirstTry(suggestion: String?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val (label, route) = when (suggestion) {
        "try_plan_today" -> R.string.ob13_try_planner to com.halo.app.ui.everyday.EverydayRoutes.ORGANIZER
        "try_explain_photo" -> R.string.ob13_try_study to "study"
        "try_first_drawing" -> R.string.ob13_try_drawing to "canvases"
        "kids_handover" -> R.string.ob13_try_family to "settings"
        else -> return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Halo13Animation(Halo13Motion.READY, Modifier.align(Alignment.CenterHorizontally).size(56.dp))
            Button({ onDismiss(); onOpen(route) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(label))
            }
            TextButton(onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ob13_card_later)) }
        }
    }
}

@Composable
internal fun Halo13FinishThinking(thinking: Thinking?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val (label, route) = when (thinking) {
        Thinking.ON_THIS_PHONE -> R.string.ob13_think_phone to "settings/intelligence/on-device"
        Thinking.ONLINE_SERVICE -> R.string.ob13_think_online to "settings/intelligence/providers"
        else -> return
    }
    WelcomeSetupCard(label, R.drawable.halo_ic_on_device, { onDismiss(); onOpen(route) }, onDismiss)
}

@Composable
private fun WelcomeSetupCard(@StringRes title: Int, @DrawableRes icon: Int, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(24.dp))
            TextButton(onOpen, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            }
            TextButton(onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ob13_card_later))
            }
        }
    }
}
