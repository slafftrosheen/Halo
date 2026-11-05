package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.knowledge.*
import com.halo.app.language.uiText
import kotlinx.coroutines.*

/** Coil receives owned files only. Composing a news card must never make an HTTP request. */
@Composable
internal fun MaterialImagesPanel(target: MaterialImageTarget, kids: Boolean, compact: Boolean = false) {
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    key(target, kids, mode) {
        var images by remember { mutableStateOf<List<SavedMaterialImage>>(emptyList()) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var expanded by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(target) {
            try { images = Graph.materialImages.local(target) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { images = emptyList() }
        }
        if (images.isNotEmpty()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val visible = if (compact) images.take(1) else images
            visible.forEach { saved -> saved.file?.let { file ->
                AsyncImage(model = file, contentDescription = saved.image.caption.ifBlank { uiText("Source image") },
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp, max = if (expanded) 600.dp else if (compact) 160.dp else 280.dp))
                if (!compact) {
                    if (saved.image.caption.isNotBlank()) Text(saved.image.caption, style = MaterialTheme.typography.bodySmall)
                    saved.image.credit?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(saved.image.license ?: uiText("Image rights are separate from article text; see the original source."), style = MaterialTheme.typography.bodySmall)
                }
            } }
            if (!compact) {
                if (images.any { it.file != null }) TextButton({ expanded = !expanded }) { Text(uiText(if (expanded) "Compact images" else "Enlarge images")) }
                if (!kids && images.any { it.file == null }) TextButton(enabled = !busy && mode == PrivacyMode.CLOUD, onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            val result = Graph.materialImages.fetch(target)
                            images = Graph.materialImages.local(target)
                            message = if (result.failed > 0) "Some images could not be saved. The article text is kept." else "Images saved for offline reading"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "Images could not be confirmed. Read the current source before trying again." }
                        finally { busy = false }
                    }
                }) { Text(uiText("Save images for offline reading")) }
                if (!kids && images.any { it.file == null } && mode != PrivacyMode.CLOUD) Text(uiText("Saving source images requires Power"), style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
