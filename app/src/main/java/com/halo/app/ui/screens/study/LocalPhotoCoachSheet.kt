package com.halo.app.ui.screens.study

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.screens.notebook.CanvasInkSurface
import com.halo.app.ui.screens.notebook.CanvasSketchOcr
import com.halo.host.ink.CoachInkContent
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class CoachStep { MODE, GIVEN, IDEA, PLACE, WORKING, RESPONSE, CHECKED, SHOWN }
internal data class CoachLessonTarget(val id: String, val title: String, val updatedAt: Long)
internal enum class CoachHintArea { IDEA, CARRYING, BORROWING, CALCULATING }
internal data class CoachResultSummary(val done: Int, val total: Int, val usedHints: Int, val areas: Set<CoachHintArea> = emptySet())

/** In-memory only. Contains bounded question data and progress for the current homework window. */
internal class CoachWorksheetProgress {
    val problems = mutableStateOf<List<LocalCoachProblem>>(emptyList())
    val selection = mutableIntStateOf(-1)
    val solved = mutableStateOf(emptySet<Int>())
    val hints = mutableStateOf(emptySet<Int>())
    val hintAreas = mutableStateOf(emptySet<CoachHintArea>())

    fun replace(with: List<LocalCoachProblem>) {
        problems.value = with
        selection.intValue = -1
        solved.value = emptySet()
        hints.value = emptySet()
        hintAreas.value = emptySet()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalPhotoCoachSheet(
    child: Boolean,
    allowed: () -> Boolean,
    onDismiss: () -> Unit,
    onProgress: ((CoachResultSummary) -> Unit)? = null,
    targets: List<CoachLessonTarget> = emptyList(),
    keepCard: (suspend (CoachLessonTarget, String, String) -> Boolean)? = null,
    reviewedPhotoTicket: String? = null,
    onReviewedPhotoConsumed: () -> Unit = {},
    keepHomeworkPhoto: (suspend (Bitmap) -> Unit)? = null,
    onStudyText: (suspend (String) -> Unit)? = null,
    acceptsQuestion: (LocalCoachProblem) -> Boolean = { true },
    retainedWorksheet: CoachWorksheetProgress? = null,
) {
    val worksheet = retainedWorksheet ?: remember { CoachWorksheetProgress() }
    var problems by worksheet.problems
    var selection by worksheet.selection
    var solved by worksheet.solved
    var hints by worksheet.hints
    var hintAreas by worksheet.hintAreas
    var problemRevision by remember { mutableIntStateOf(0) }
    val currentProblem = problems.getOrNull(selection)
    ModalBottomSheet(onDismissRequest = onDismiss,
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState())
            .navigationBarsPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (child) R.string.halo11_coach_homework else R.string.halo11_coach_heading),
                style = MaterialTheme.typography.headlineSmall)
            when {
                problems.isEmpty() -> CoachPhotoIntake(child, allowed, reviewedPhotoTicket, onReviewedPhotoConsumed,
                    keepHomeworkPhoto, onStudyText, acceptsQuestion) { incoming ->
                    worksheet.replace(incoming)
                    if (allowed()) onProgress?.invoke(CoachResultSummary(0, incoming.size, 0))
                }
                currentProblem == null -> {
                    Text(stringResource(R.string.halo11_coach_pick_question), style = MaterialTheme.typography.titleMedium)
                    problems.forEachIndexed { index, problem ->
                        OutlinedButton(onClick = { if (allowed()) { selection = index; problemRevision++ } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text((if (index in solved) "✓  " else "") + problemLabel(problem))
                        }
                    }
                    TextButton(onClick = { worksheet.replace(emptyList()) }) { Text(stringResource(R.string.halo11_coach_take_photo)) }
                }
                else -> key(problemRevision) {
                    val next = problems.indices.firstOrNull { it !in solved && it != selection }
                    CoachProblemContent(currentProblem, child, allowed,
                        onHint = { area ->
                            if (allowed() && (selection !in hints || area !in hintAreas)) {
                                hints = hints + selection
                                hintAreas = hintAreas + area
                                onProgress?.invoke(CoachResultSummary(solved.size, problems.size, hints.size, hintAreas))
                            }
                        },
                        onCorrect = {
                            if (allowed() && selection !in solved) {
                                solved = solved + selection
                                onProgress?.invoke(CoachResultSummary(solved.size, problems.size, hints.size, hintAreas))
                            }
                        },
                        onSimilar = if (child) null else {
                            {
                                if (allowed()) {
                                    problems = problems.toMutableList().also { it[selection] = currentProblem.similar() }
                                    solved = solved - selection
                                    hints = hints - selection
                                    problemRevision++
                                }
                            }
                        },
                        onNext = if (next == null) null else { { if (allowed()) { selection = next; problemRevision++ } } },
                        targets = targets, keepCard = keepCard)
                    TextButton(onClick = { selection = -1 }) { Text(stringResource(R.string.halo11_coach_pick_question)) }
                }
            }
            TextButton(onClick = onDismiss) { Text(uiText("Close")) }
        }
    }
}

@Composable
private fun problemLabel(problem: LocalCoachProblem): String = when (problem) {
    is LocalCoachProblem.Arithmetic -> problem.equation
    is LocalCoachProblem.Fraction -> problem.equation
    is LocalCoachProblem.Acceleration -> stringResource(R.string.halo11_coach_acceleration,
        showNumber(problem.startSpeed), showNumber(problem.endSpeed), showNumber(problem.seconds))
}

private fun showNumber(number: Double): String =
    BigDecimal.valueOf(number).setScale(5, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

@StringRes
private fun ideaOf(operation: CoachOperation): Int = when (operation) {
    CoachOperation.ADD -> R.string.halo11_coach_add
    CoachOperation.SUBTRACT -> R.string.halo11_coach_subtract
    CoachOperation.MULTIPLY -> R.string.halo11_coach_multiply
    CoachOperation.DIVIDE -> R.string.halo11_coach_divide
    CoachOperation.ACCELERATION -> R.string.halo11_coach_acceleration_idea
}

@StringRes
private fun hintFor(operation: CoachOperation): Int = when (operation) {
    CoachOperation.ADD -> R.string.halo11_coach_smaller_add
    CoachOperation.SUBTRACT -> R.string.halo11_coach_smaller_sub
    CoachOperation.MULTIPLY -> R.string.halo11_coach_smaller_multiply
    CoachOperation.DIVIDE -> R.string.halo11_coach_smaller_divide
    CoachOperation.ACCELERATION -> R.string.halo11_coach_smaller_acceleration
}

@StringRes
private fun explanationFor(operation: CoachOperation): Int = when (operation) {
    CoachOperation.ADD -> R.string.halo11_coach_explain_add
    CoachOperation.SUBTRACT -> R.string.halo11_coach_explain_subtract
    CoachOperation.MULTIPLY -> R.string.halo11_coach_explain_multiply
    CoachOperation.DIVIDE -> R.string.halo11_coach_explain_divide
    CoachOperation.ACCELERATION -> R.string.halo11_coach_explain_acceleration
}

@Composable
private fun CoachProblemContent(
    problem: LocalCoachProblem,
    child: Boolean,
    allowed: () -> Boolean,
    onHint: (CoachHintArea) -> Unit,
    onCorrect: () -> Unit,
    onSimilar: (() -> Unit)?,
    onNext: (() -> Unit)?,
    targets: List<CoachLessonTarget>,
    keepCard: (suspend (CoachLessonTarget, String, String) -> Boolean)?,
) {
    var step by remember { mutableStateOf(if (child) CoachStep.GIVEN else CoachStep.MODE) }
    var attempts by remember { mutableIntStateOf(0) }
    var exampleOpen by remember { mutableStateOf(false) }
    var exampleSeen by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    var smaller by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var showKeepReview by remember { mutableStateOf(false) }
    var kept by remember { mutableStateOf(false) }
    var workingIndex by remember { mutableIntStateOf(0) }
    var checkedWorking by remember { mutableIntStateOf(0) }
    val working = remember(problem, child) { localCoachWorking(problem, child) }
    val answerFocus = remember { FocusRequester() }
    val label = problemLabel(problem)
    val place = (problem as? LocalCoachProblem.Arithmetic)?.carryOrBorrow
    fun advance(next: CoachStep) { if (allowed()) { feedback = null; smaller = false; step = next } }
    fun startWorking() {
        workingIndex = 0
        checkedWorking = 0
        advance(if (working.isEmpty()) CoachStep.RESPONSE else CoachStep.WORKING)
    }
    fun hint() {
        if (!allowed()) return
        smaller = true
        val area = when {
            step == CoachStep.IDEA -> CoachHintArea.IDEA
            step == CoachStep.PLACE && problem.operation == CoachOperation.ADD -> CoachHintArea.CARRYING
            step == CoachStep.PLACE -> CoachHintArea.BORROWING
            step == CoachStep.WORKING && working.getOrNull(workingIndex)?.kind == CoachWorkingKind.ADD_TENS_WITH_CARRY -> CoachHintArea.CARRYING
            step == CoachStep.WORKING && working.getOrNull(workingIndex)?.kind == CoachWorkingKind.TRADE_TEN -> CoachHintArea.BORROWING
            else -> CoachHintArea.CALCULATING
        }
        onHint(area)
    }
    fun wrong() {
        attempts++
        feedback = if (!child) R.string.halo11_coach_wrong else if (attempts >= 2)
            R.string.coach_tricky_break else R.string.coach_not_yet
        hint()
    }
    fun submit() {
        if (allowed() && answer.isNotBlank()) when (checkLocalCoachAnswer(problem, answer, unit)) {
            CoachCheck.CORRECT -> { onCorrect(); advance(CoachStep.CHECKED) }
            CoachCheck.NUMBER -> wrong()
            CoachCheck.UNITS -> { feedback = R.string.halo11_coach_wrong_units; hint() }
        }
    }
    val done = step in setOf(CoachStep.CHECKED, CoachStep.SHOWN)
    val reviewedResult = if (done && !child && allowed()) {
        showNumber(problem.answer) + if (problem.unitsRequired) " m/s²" else ""
    } else null
    Text(label, style = MaterialTheme.typography.titleLarge)
    if (child) {
        val stage = when (step) {
            CoachStep.MODE, CoachStep.GIVEN -> R.string.coach_step_notice
            CoachStep.IDEA, CoachStep.PLACE -> R.string.coach_step_idea
            CoachStep.WORKING, CoachStep.RESPONSE -> R.string.coach_step_try
            CoachStep.CHECKED, CoachStep.SHOWN -> R.string.coach_step_check
        }
        Text(stringResource(stage), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    val tabletWriting = if (!child && allowed()) {
        val phase = when (step) {
            CoachStep.MODE -> stringResource(R.string.halo11_coach_coach_mode)
            CoachStep.GIVEN -> stringResource(if (problem.unitsRequired) R.string.halo11_coach_givens_speed else R.string.halo11_coach_givens)
            CoachStep.IDEA -> stringResource(R.string.halo11_coach_idea)
            CoachStep.PLACE -> coachPlacePrompt(problem)
            CoachStep.WORKING -> coachWorkingPrompt(working[workingIndex])
            CoachStep.RESPONSE -> stringResource(R.string.halo11_coach_answer)
            CoachStep.CHECKED -> stringResource(R.string.halo11_coach_checked_adult)
            CoachStep.SHOWN -> stringResource(R.string.halo11_coach_show_answer)
        }
        val steps = buildList {
            for (accepted in working.take(checkedWorking)) {
                add(stringResource(R.string.halo11_coach_ink_mirror_step, coachWorkingPrompt(accepted), showNumber(accepted.expected)))
            }
            if (explain && !done) add(stringResource(explanationFor(problem.operation)))
            feedback?.let { add(stringResource(it)) }
            if (smaller && !done) add(stringResource(if (step == CoachStep.PLACE) R.string.halo11_coach_smaller else hintFor(problem.operation)))
            if (reviewedResult != null) add(stringResource(R.string.halo11_coach_result, reviewedResult))
        }
        CoachInkMirrorControls(CoachInkContent(label, phase, steps, step == CoachStep.RESPONSE), allowed)
    } else null
    when (step) {
        CoachStep.MODE -> CoachModeOptions(onCoach = { advance(CoachStep.GIVEN) },
            onExplain = { if (allowed()) { explain = true; advance(CoachStep.GIVEN) } },
            onAnswer = { advance(CoachStep.SHOWN) })
        CoachStep.GIVEN -> CoachGivens(problem) { advance(CoachStep.IDEA) }
        CoachStep.IDEA -> CoachIdeas(problem, child) { chosen ->
            if (allowed()) {
                if (chosen == problem.operation) {
                    if (place != null) advance(CoachStep.PLACE) else startWorking()
                }
                else wrong()
            }
        }
        CoachStep.PLACE -> {
            Text(coachPlacePrompt(problem))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in listOf(true, false)) OutlinedButton(onClick = {
                    if (allowed()) { if (option == place) startWorking() else wrong() }
                }) { Text(uiText(if (option) "Yes" else "No")) }
            }
        }
        CoachStep.WORKING -> key(workingIndex) {
            val prompt = working[workingIndex]
            CoachWorkingInput(prompt, workingIndex + 1, working.size) { attempted ->
                if (allowed()) {
                    if (checkLocalCoachWorking(prompt, attempted)) {
                        checkedWorking = workingIndex + 1
                        if (workingIndex < working.lastIndex) { workingIndex++; advance(CoachStep.WORKING) }
                        else advance(CoachStep.RESPONSE)
                    } else wrong()
                }
            }
        }
        CoachStep.RESPONSE -> CoachAnswerInput(problem, child, answer, { answer = it.take(32) }, unit,
            { unit = it.take(16) }, answerFocus, allowed, tabletWriting, ::submit)
        CoachStep.CHECKED -> Text(stringResource(if (child) R.string.halo11_coach_done else R.string.halo11_coach_checked_adult),
            style = MaterialTheme.typography.headlineSmall)
        CoachStep.SHOWN -> Unit
    }
    if (done) {
        if (reviewedResult != null) {
            Text(stringResource(R.string.halo11_coach_result, reviewedResult), style = MaterialTheme.typography.titleLarge)
            if (keepCard != null) CoachKeepCard(label, reviewedResult, targets, allowed, keepCard,
                showKeepReview, { showKeepReview = it }, kept, { kept = true })
            if (onSimilar != null) OutlinedButton(onSimilar) { Text(stringResource(R.string.halo11_coach_similar)) }
        }
        if (onNext != null) Button(onNext) { Text(stringResource(R.string.halo11_coach_next)) }
    } else if (step != CoachStep.MODE) {
        val message = feedback
        if (message != null) Text(stringResource(message))
        if (explain && !child) {
            Text(stringResource(explanationFor(problem.operation)), style = MaterialTheme.typography.bodyLarge)
        }
        if (smaller) {
            val hint = if (step == CoachStep.PLACE) R.string.halo11_coach_smaller else hintFor(problem.operation)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(hint), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    CoachHintIllustration(problem, child)
                }
            }
        }
        CoachHelp(child, onHint = ::hint, onExplain = { if (allowed()) explain = !explain }, onAsk = {
            if (child && allowed()) { exampleOpen = true; onHint(CoachHintArea.IDEA) }
            else if (!child) advance(CoachStep.SHOWN)
        })
    }
    if (child && exampleSeen && !done) Text(stringResource(R.string.coach_now_try))
    if (child && exampleOpen && !done && allowed()) {
        val example = remember(problem) { safeWorkedExample(problem) }
        AlertDialog(onDismissRequest = { exampleOpen = false }, title = { Text(stringResource(R.string.coach_similar)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.coach_just_tell_no))
                    if (example != null) {
                        Text(problemLabel(example), style = MaterialTheme.typography.titleMedium)
                        localCoachWorking(example, child = false).forEach { work ->
                            Text(coachWorkingPrompt(work) + " · " + showNumber(work.expected))
                        }
                        Text(stringResource(R.string.halo11_coach_result, showNumber(example.answer)))
                    }
                }
            }, confirmButton = {
                TextButton(onClick = { exampleOpen = false; exampleSeen = true }) { Text(stringResource(R.string.coach_ill_try)) }
            })
    }
    if (!child) HaloInstrumentBar("coach", buildMap {
        if (!done) {
            put("coach.knowns", HaloInstrumentAction({ advance(CoachStep.GIVEN) }, selected = step == CoachStep.GIVEN))
            put("coach.steps", HaloInstrumentAction({ advance(CoachStep.GIVEN) }, selected = step != CoachStep.MODE))
            put("coach.smaller", HaloInstrumentAction({
                if (step == CoachStep.MODE) advance(CoachStep.GIVEN)
                hint()
            }, selected = smaller))
            put("coach.show_answer", HaloInstrumentAction({ advance(CoachStep.SHOWN) }))
        }
        if (step == CoachStep.RESPONSE) {
            put("coach.answer_box", HaloInstrumentAction({ if (allowed()) answerFocus.requestFocus() }))
            put("coach.check", HaloInstrumentAction(::submit, enabled = answer.isNotBlank()))
        }
        if (done) {
            if (onSimilar != null) put("coach.similar", HaloInstrumentAction(onSimilar))
            if (keepCard != null && targets.isNotEmpty()) put("coach.to_card", HaloInstrumentAction({
                if (allowed() && !kept) showKeepReview = true
            }, enabled = !kept, selected = showKeepReview))
        }
    }, primary = if (done) "coach.similar" else "coach.steps")
}

@Composable
private fun coachPlacePrompt(problem: LocalCoachProblem) =
    stringResource(if (problem.operation == CoachOperation.ADD) R.string.halo11_coach_ones_add else R.string.halo11_coach_ones_sub)

@Composable
private fun coachWorkingPrompt(work: CoachWorkingCheck) = when (work.kind) {
    CoachWorkingKind.COMBINE_ONES -> stringResource(R.string.halo11_coach_work_combine, work.first, work.second)
    CoachWorkingKind.WRITE_ONES -> stringResource(R.string.halo11_coach_work_ones)
    CoachWorkingKind.ADD_TENS -> stringResource(R.string.halo11_coach_work_tens_add)
    CoachWorkingKind.ADD_TENS_WITH_CARRY -> stringResource(R.string.halo11_coach_work_tens_carry)
    CoachWorkingKind.TRADE_TEN -> stringResource(R.string.halo11_coach_work_trade)
    CoachWorkingKind.TAKE_AWAY_ONES -> stringResource(R.string.halo11_coach_work_take_ones, work.second)
    CoachWorkingKind.SUBTRACT_TENS -> stringResource(R.string.halo11_coach_work_tens_sub)
    CoachWorkingKind.CHANGE_IN_SPEED -> stringResource(R.string.halo11_coach_work_speed)
    CoachWorkingKind.ITEMS_PER_GROUP -> stringResource(R.string.halo11_coach_work_group_items)
    CoachWorkingKind.NUMBER_OF_GROUPS -> stringResource(R.string.halo11_coach_work_groups)
}

@Composable
private fun CoachWorkingInput(work: CoachWorkingCheck, step: Int, total: Int, check: (String) -> Unit) {
    var working by remember { mutableStateOf("") }
    Text(stringResource(R.string.halo11_coach_work_step, step, total), style = MaterialTheme.typography.labelLarge)
    Text(coachWorkingPrompt(work), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(working, { working = it.take(32) }, label = { Text(stringResource(R.string.halo11_coach_work_value)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().testTag("coach.working"))
    Button(onClick = { check(working) }, enabled = working.isNotBlank()) { Text(stringResource(R.string.halo11_coach_check)) }
}

@Composable
private fun CoachHintIllustration(problem: LocalCoachProblem, child: Boolean) {
    when (problem) {
        is LocalCoachProblem.Arithmetic -> {
            if (problem.operation == CoachOperation.ADD || problem.operation == CoachOperation.SUBTRACT) {
                val firstOnes = problem.first % 10
                if (firstOnes > 0 && (!child || firstOnes.toDouble() != problem.answer)) Text(stringResource(R.string.halo11_coach_ones_illustration,
                    "▣ ".repeat(firstOnes).trim()), style = MaterialTheme.typography.bodyMedium)
            }
        }
        is LocalCoachProblem.Acceleration -> Text("v₀ → v₁    Δt", style = MaterialTheme.typography.bodyMedium)
        is LocalCoachProblem.Fraction -> Text(stringResource(R.string.coach_fraction_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CoachModeOptions(onCoach: () -> Unit, onExplain: () -> Unit, onAnswer: () -> Unit) {
    Text(stringResource(R.string.halo11_coach_choose_mode), style = MaterialTheme.typography.titleMedium)
    Button(onCoach, modifier = Modifier.testTag("coach.steps")) { Text(stringResource(R.string.halo11_coach_coach_mode)) }
    OutlinedButton(onExplain) { Text(stringResource(R.string.halo11_coach_explain_mode)) }
    TextButton(onAnswer, modifier = Modifier.testTag("coach.show_answer")) { Text(stringResource(R.string.halo11_coach_show_answer)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoachGivens(problem: LocalCoachProblem, onContinue: () -> Unit) {
    val values = when (problem) {
        is LocalCoachProblem.Arithmetic -> listOf(problem.first.toString(), problem.second.toString())
        is LocalCoachProblem.Fraction -> listOf(problem.first.toString() + "/" + problem.denominator, problem.second.toString() + "/" + problem.divisor)
        is LocalCoachProblem.Acceleration -> listOf(
            stringResource(R.string.halo11_coach_initial_speed, showNumber(problem.startSpeed)),
            stringResource(R.string.halo11_coach_final_speed, showNumber(problem.endSpeed)),
            stringResource(R.string.halo11_coach_time_value, showNumber(problem.seconds)),
        )
    }
    var marked by remember { mutableStateOf(emptySet<Int>()) }
    var extra by remember { mutableStateOf(false) }
    var needsReview by remember { mutableStateOf(false) }
    Text(stringResource(if (problem.unitsRequired) R.string.halo11_coach_givens_speed else R.string.halo11_coach_givens))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { index, value ->
            FilterChip(index in marked, { marked = if (index in marked) marked - index else marked + index }, label = { Text(value) })
        }
        if (problem.unitsRequired) FilterChip(extra, { extra = !extra; needsReview = false },
            label = { Text(stringResource(R.string.halo11_coach_distance)) })
    }
    if (needsReview) Text(stringResource(R.string.halo11_coach_givens_review))
    Button({ if (!extra && marked.size == values.size) onContinue() else needsReview = true }, enabled = marked.isNotEmpty()) {
        Text(uiText("Continue"))
    }
}

@Composable
private fun CoachIdeas(problem: LocalCoachProblem, child: Boolean, onChoice: (CoachOperation) -> Unit) {
    val options = remember(problem, child) {
        if (problem.unitsRequired) listOf(CoachOperation.MULTIPLY, CoachOperation.ACCELERATION, CoachOperation.SUBTRACT)
        else CoachOperation.entries.filter { it != CoachOperation.ACCELERATION }.let { arithmetic ->
            if (!child) arithmetic else arithmetic.filter { it == problem.operation || it in arithmetic.filterNot { other ->
                other == problem.operation }.take(2) }
        }
    }
    Text(stringResource(R.string.halo11_coach_idea), style = MaterialTheme.typography.titleMedium)
    for (option in options) OutlinedButton({ onChoice(option) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(ideaOf(option)))
    }
}

@Composable
private fun CoachAnswerInput(problem: LocalCoachProblem, child: Boolean,
    answer: String, onAnswer: (String) -> Unit, unit: String, onUnit: (String) -> Unit,
    focus: FocusRequester, allowed: () -> Boolean, tabletWriting: InkDocument?, check: () -> Unit) {
    Text(stringResource(if (child) R.string.halo11_coach_own_answer else R.string.halo11_coach_check_steps))
    OutlinedTextField(answer, onAnswer, label = { Text(stringResource(R.string.halo11_coach_answer)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (problem is LocalCoachProblem.Fraction) KeyboardType.Text
            else KeyboardType.Decimal, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("coach.answer_box"))
    if (problem.unitsRequired) OutlinedTextField(unit, onUnit,
        label = { Text(stringResource(R.string.halo11_coach_units)) }, singleLine = true)
    if (!child) CoachInkAnswer(allowed, tabletWriting, onAnswer)
    Button(onClick = check, enabled = answer.isNotBlank(), modifier = Modifier.testTag("coach.check")) {
        Text(stringResource(R.string.halo11_coach_check))
    }
}

@Composable
private fun CoachInkAnswer(allowed: () -> Boolean, tabletWriting: InkDocument?, onAnswer: (String) -> Unit) {
    var writing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val stillAllowed by rememberUpdatedState(allowed)
    var ink by remember { mutableStateOf("") }
    var sheetRevision by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf(false) }
    var recognized by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<InkDocument?>(null) }
    fun reset() {
        ink = ""
        recognized = null
        failed = false
        imported = null
        sheetRevision++
    }
    LaunchedEffect(tabletWriting) {
        if (imported != null && imported != tabletWriting) {
            reset()
            writing = false
        }
    }
    TextButton(onClick = {
        if (stillAllowed()) {
            if (writing) reset()
            writing = !writing
        }
    }) {
        Text(stringResource(if (writing) R.string.halo11_coach_ink_close else R.string.halo11_coach_ink_open))
    }
    if (tabletWriting != null) TextButton(enabled = !reading, onClick = {
        if (stillAllowed()) {
            reset()
            imported = tabletWriting
            ink = InkCodec.encode(tabletWriting)
            writing = true
        }
    }) { Text(stringResource(R.string.halo11_coach_ink_review_tablet)) }
    if (!writing) return
    Text(stringResource(R.string.halo11_coach_ink_prompt))
    key(sheetRevision) {
        val initial = remember { ink }
        CanvasInkSurface(initial = initial, aspectMilli = 2100, onChanged = { strokes -> ink = strokes; recognized = null })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = ink.isNotEmpty() && !reading, onClick = {
            if (stillAllowed()) reset()
        }) { Text(stringResource(R.string.halo11_coach_ink_clear)) }
        OutlinedButton(enabled = ink.isNotEmpty() && !reading, onClick = {
            if (stillAllowed() && !reading) {
                val expected = ink
                reading = true
                failed = false
                scope.launch {
                    try {
                        val draft = CanvasSketchOcr.recognize(expected).trim().replace('−', '-')
                        if (writing && stillAllowed() && expected == ink) {
                            recognized = draft.takeIf { it.length <= 32 && it.matches(Regex("[+-]?[0-9]+(?:[.,][0-9]+)?")) }
                            failed = recognized == null
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { reading = false }
                }
            }
        }) { Text(stringResource(R.string.halo11_coach_ink_recognize)) }
    }
    if (reading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (failed) Text(stringResource(R.string.halo11_coach_ink_unreadable), style = MaterialTheme.typography.bodySmall)
    val proposed = recognized
    if (proposed != null) OutlinedButton(onClick = {
        if (stillAllowed()) { onAnswer(proposed); reset(); writing = false }
    }) { Text(stringResource(R.string.halo11_coach_ink_use, proposed)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoachHelp(child: Boolean, onHint: () -> Unit, onExplain: () -> Unit, onAsk: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onHint, modifier = Modifier.testTag("coach.smaller")) {
            Text(if (child) stringResource(R.string.halo11_coach_stuck) else uiText("Hint"))
        }
        if (!child) TextButton(onExplain) { Text(stringResource(R.string.halo11_coach_explain_mode)) }
        TextButton(onAsk) { Text(stringResource(if (child) R.string.halo11_coach_tell else R.string.halo11_coach_show_answer)) }
    }
}

@Composable
private fun CoachKeepCard(question: String, answer: String, targets: List<CoachLessonTarget>, allowed: () -> Boolean,
    keep: suspend (CoachLessonTarget, String, String) -> Boolean,
    choose: Boolean, onChoose: (Boolean) -> Unit, kept: Boolean, onKept: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    if (targets.isEmpty()) {
        Text(stringResource(R.string.halo11_coach_no_lesson), style = MaterialTheme.typography.bodySmall)
        return
    }
    if (!choose) Button(onClick = { onChoose(true) }, enabled = !saving && !kept) {
        Text(stringResource(R.string.halo11_coach_keep_card))
    } else {
        Text(stringResource(R.string.halo11_coach_approved_card))
        Text(stringResource(R.string.halo11_coach_pick_lesson), style = MaterialTheme.typography.titleSmall)
        for (target in targets) OutlinedButton(enabled = !saving, onClick = {
            if (allowed() && !saving) {
                saving = true
                scope.launch {
                    try {
                        val saved = keep(target, question, answer)
                        status = if (saved) R.string.halo11_coach_kept_card else R.string.halo11_coach_keep_failed
                        if (saved) { onKept(); onChoose(false) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { status = R.string.halo11_coach_keep_failed }
                    finally { saving = false }
                }
            }
        }) { Text(target.title) }
    }
    val result = status
    if (result != null) Text(stringResource(result), style = MaterialTheme.typography.bodySmall)
}
