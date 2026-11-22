package com.halo.app.ui.screens.kids

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.study.ResolvedStudySource
import com.halo.app.ui.screens.study.StudyHomeViewModel
import com.halo.app.ui.workspace.WorkspaceObjectPicker

/** General learning material remains under the existing authority. Homework photos go straight to the local coach. */
@Composable
internal fun KidsLearningSources(model: StudyHomeViewModel, openHomework: (() -> Unit)? = null) {
    val selected by model.selectedIds.collectAsStateWithLifecycle()
    val candidates by model.candidates.collectAsStateWithLifecycle()
    val previews by model.previews.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf(false) }
    var fileAfterUnlock by remember { mutableStateOf(false) }
    var permissionNotice by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importMaterial)
    }
    val chooseFile = rememberKidsGrownUpGate(onVerified = { fileAfterUnlock = true },
        onUnavailable = { permissionNotice = true })
    LaunchedEffect(fileAfterUnlock, lifecycleState) {
        if (fileAfterUnlock && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            fileAfterUnlock = false
            file.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        }
    }
    val chooseNotes = rememberKidsGrownUpGate(onVerified = { picker = true },
        onUnavailable = { permissionNotice = true })
    if (picker) WorkspaceObjectPicker(candidates.filter { it.id.value !in selected },
        title = uiText("My notes"), onPick = { model.select(it.id.value); picker = false },
        onDismiss = { picker = false })
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Material"), style = MaterialTheme.typography.titleMedium)
        Text(uiText("Choose material, check what it says, then tell halo which exercise to work on."),
            style = MaterialTheme.typography.bodyMedium)
        if (openHomework != null) OutlinedButton(onClick = openHomework, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.halo11_coach_homework))
        }
        OutlinedButton(onClick = chooseFile, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("Choose files"))
        }
        OutlinedButton(onClick = chooseNotes, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("My notes"))
        }
        previews.forEach { source ->
            Text(source.title, style = MaterialTheme.typography.titleSmall)
            when (source) {
                is ResolvedStudySource.Text -> Text(source.passages.joinToString("\n\n") { it.text }.take(4000))
                is ResolvedStudySource.Unsupported -> Text(uiText(source.reason))
                else -> Text(uiText("This page needs an on-device vision model. If halo cannot read it, type or dictate the exercise instead."))
            }
            TextButton(onClick = { model.remove(source.objectId) }, enabled = !busy) { Text(uiText("Remove")) }
        }
        if (permissionNotice) Text(uiText("Ask a grown-up to set a device PIN or password in Android settings first."),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
