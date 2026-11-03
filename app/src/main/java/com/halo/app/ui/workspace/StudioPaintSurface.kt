package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import com.halo.app.ui.screens.notebook.CanvasInkSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Same Ink input/session owner as Notes and the Studio inspector; no second paint storage. */
@Composable
internal fun StudioPaintSurface(service:StudioService,session:StudioInkSession,tool:String,onSaveCopy:()->Unit,modifier:Modifier=Modifier) {
    val project=session.initial
    val layer=project.session.current.layers.single { it.id==session.layerId }
    val failed by session.failed.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var selectionJob by remember(session) { mutableStateOf<Job?>(null) }
    var previewFailed by remember(session) { mutableStateOf(false) }
    val background by produceState<Bitmap?>(null,session) {
        try { value=service.inkBackdrop(project.session.current,layer) }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(_:Exception) { previewFailed=true }
    }
    DisposableEffect(session) { onDispose { selectionJob?.cancel() } }
    when {
        failed->Column(modifier) {
            Text(uiText("Paint could not be kept. Save a recovery copy before continuing."),color=MaterialTheme.colorScheme.error)
            TextButton(onSaveCopy) {Text(uiText("Save recovery copy"))}
        }
        previewFailed->Text(uiText("Preview unavailable. Your project is kept."),color=MaterialTheme.colorScheme.error)
        background==null->CircularProgressIndicator()
        else->CanvasInkSurface(layer.ink,session::submit,background=background?.asImageBitmap(),
            selectionOwnerId=project.id,selectionPageId=layer.id,selectionBaseRevision=project.revision.toString(),
            strokeColor=Color(layer.color).copy(alpha=layer.opacity/1000f),
            strokeWidthFraction=service.inkWidthFraction(project.session.current,layer),
            modifier=modifier.fillMaxSize(),showControls=false,externalMode=tool,
            onSelectionChanged={ context->
                selectionJob?.cancel()
                if(context==null) service.clearSelection(project.id,session.ownerToken)
                else selectionJob=scope.launch {
                    if(!session.awaitPersisted() || !service.publishSelection(project.id,layer.id,project.revision,
                            session.ownerToken,context,session.persistedRevision)) service.clearSelection(project.id,session.ownerToken)
                }
            })
    }
}
