package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyCourseOutlineSheet(read: StudyCourseRead, activeId: String?, busy: Boolean,
    onOpen: (String) -> Unit, onMove: (String, String, Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("Course outline"), style = MaterialTheme.typography.headlineSmall)
            when (read) {
                StudyCourseRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                StudyCourseRead.Unavailable -> Text(uiText("The course outline could not be opened."), color = MaterialTheme.colorScheme.error)
                is StudyCourseRead.Ready -> {
                    val outline = read.outline
                    if (outline.lessons.isEmpty()) Text(uiText("Save a lesson to start your course outline."))
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(outline.lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
                            StudyCourseLessonRow(lesson, index, outline.lessons.lastIndex, activeId, busy,
                                onOpen = { onOpen(lesson.id) }, onMove = { movement -> onMove(outline.version, lesson.id, movement) })
                        }
                    }
                }
            }
            TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Close")) }
        }
    }
}

@Composable
private fun StudyCourseLessonRow(lesson: StudyCourseLesson, index: Int, lastIndex: Int, activeId: String?,
    busy: Boolean, onOpen: () -> Unit, onMove: (Int) -> Unit) {
    val title = lesson.title ?: uiText("Untitled lesson")
    val up = uiText("Move lesson up: %s", title)
    val down = uiText("Move lesson down: %s", title)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onOpen, enabled = !busy && lesson.id != activeId, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(uiText(lesson.progress.label), style = MaterialTheme.typography.labelMedium,
                        color = if (lesson.progress == StudyCourseProgress.NOW) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { onMove(-1) }, enabled = !busy && index > 0,
                modifier = Modifier.semantics { contentDescription = up }) { Text("↑") }
            IconButton(onClick = { onMove(1) }, enabled = !busy && index < lastIndex,
                modifier = Modifier.semantics { contentDescription = down }) { Text("↓") }
        }
    }
}
