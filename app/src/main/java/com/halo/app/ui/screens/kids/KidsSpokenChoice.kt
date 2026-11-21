package com.halo.app.ui.screens.kids

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.components.VoiceFieldCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private object SpokenChoiceSelection {
    val selected = MutableStateFlow<Any?>(null)
    val epoch = MutableStateFlow(-1L)
    var ticket = 0L
}
internal data class KidsSpokenChoice(val onClick: () -> Unit, val heard: Boolean)

/** Only reversible content choices use this helper. TalkBack always keeps its own interaction model. */
@Composable
internal fun rememberKidsSpokenChoice(label: String, alwaysRead: Boolean = false, onChoose: () -> Unit): KidsSpokenChoice {
    val settings = if (androidx.compose.ui.platform.LocalInspectionMode.current) null
        else Graph.kidsPreferences.appearance.collectAsStateWithLifecycle().value
    val enabled = settings != null && (alwaysRead || (settings.preReader && settings.readAloud))
    val choice = remember(label) { Any() }
    val context = LocalContext.current
    val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    var talkBack by remember(accessibility) { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { talkBack = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    val selected by SpokenChoiceSelection.selected.collectAsStateWithLifecycle()
    val epoch by VoiceFieldCancellation.epoch.collectAsStateWithLifecycle()
    val armedEpoch by SpokenChoiceSelection.epoch.collectAsStateWithLifecycle()
    val action by rememberUpdatedState(onChoose)
    val scope = rememberCoroutineScope()
    DisposableEffect(choice) {
        onDispose {
            if (SpokenChoiceSelection.selected.value === choice) {
                SpokenChoiceSelection.ticket += 1
                SpokenChoiceSelection.selected.value = null
            }
        }
    }
    val heard = enabled && !talkBack && selected === choice && armedEpoch == epoch
    return KidsSpokenChoice(onClick = {
        if (!enabled || talkBack || heard) {
            SpokenChoiceSelection.ticket += 1
            SpokenChoiceSelection.selected.value = null
            if (heard) VoiceFieldCancellation.stop()
            action()
        } else {
            val ticket = ++SpokenChoiceSelection.ticket
            SpokenChoiceSelection.selected.value = choice
            SpokenChoiceSelection.epoch.value = -1L
            scope.launch {
                val requested = readKidsText("kids-choice:$ticket", label)
                if (SpokenChoiceSelection.ticket == ticket) {
                    if (requested) SpokenChoiceSelection.epoch.value = VoiceFieldCancellation.epoch.value
                    else SpokenChoiceSelection.selected.value = null
                }

            }
        }
    }, heard = heard)
}
