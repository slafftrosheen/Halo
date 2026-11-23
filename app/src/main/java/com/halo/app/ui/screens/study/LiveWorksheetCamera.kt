package com.halo.app.ui.screens.study

import android.graphics.RectF
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.R
import com.halo.app.ui.toolkit.HaloScanCamera
import com.halo.app.ui.toolkit.scanBitmap
import com.halo.app.ui.toolkit.scanFrameText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class LiveQuestion(val problem: LocalCoachProblem, val bounds: RectF)

@Composable
internal fun LiveWorksheetCamera(
    allowed: () -> Boolean,
    accepts: (LocalCoachProblem) -> Boolean,
    onChoose: (LocalCoachProblem) -> Unit,
    onClose: () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val liveAuthority by rememberUpdatedState(allowed)
    val accepted by rememberUpdatedState(accepts)
    var camera by remember { mutableStateOf<PreviewView?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf(emptyList<LiveQuestion>()) }
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(owner, camera, streaming) {
        val preview = camera
        if (preview == null || !streaming) {
            questions = emptyList()
            return@LaunchedEffect
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (isActive && liveAuthority()) {
                    val frame = preview.scanBitmap()
                    if (frame != null) try {
                        val width = frame.width.toFloat()
                        val height = frame.height.toFloat()
                        val lines = scanFrameText(frame)
                        if (!liveAuthority()) break
                        questions = lines.mapNotNull { line ->
                            val rect = line.position ?: return@mapNotNull null
                            if (rect.isEmpty || rect.left < 0 || rect.top < 0 || rect.right > width || rect.bottom > height)
                                return@mapNotNull null
                            val problem = localCoachQuestions(listOf(line.text), child = true).singleOrNull()
                                ?.takeIf(accepted) ?: return@mapNotNull null
                            LiveQuestion(problem, RectF(rect.left / width, rect.top / height, rect.right / width, rect.bottom / height))
                        }.take(12)
                        tried = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { questions = emptyList(); tried = true }
                    finally { frame.recycle() }
                    delay(900)
                }
            } finally { questions = emptyList() }
        }
    }
    fun select(question: LiveQuestion) {
        if (liveAuthority() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            question in questions && accepted(question.problem)) onChoose(question.problem)
    }
    if (failed) Text(stringResource(R.string.halo11_coach_camera_unavailable))
    else {
        val outline = MaterialTheme.colorScheme.primary
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            HaloScanCamera(1f, false, { camera = it }, { streaming = it }, { failed = true }, Modifier.matchParentSize())
            Canvas(Modifier.matchParentSize().pointerInput(questions) {
                detectTapGestures { touch ->
                    if (size.width > 0 && size.height > 0)
                        questions.firstOrNull { it.bounds.contains(touch.x / size.width, touch.y / size.height) }?.let(::select)
                }
            }) {
                questions.forEach { item ->
                    val rect = item.bounds
                    drawRect(outline, Offset(rect.left * size.width, rect.top * size.height),
                        Size(rect.width() * size.width, rect.height() * size.height), style = Stroke(3.dp.toPx()))
                }
            }
        }
        if (questions.isNotEmpty()) {
            Text(stringResource(R.string.coach_which_one, questions.size))
            questions.forEachIndexed { index, question ->
                OutlinedButton(onClick = { select(question) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.coach_live_question, index + 1))
                }
            }
        } else if (tried) Text(stringResource(R.string.coach_live_unclear))
    }
    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
        Text(com.halo.app.language.uiText("Close"))
    }
}
