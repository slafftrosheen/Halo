package com.halo.app.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.data.studio.*
import com.halo.app.language.uiText

/** Temporary inspector uses the same composited input surface and retained writer as direct Paint. */
@Composable
internal fun StudioInkDialog(service:StudioService,session:StudioInkSession,onSaveCopy:()->Unit,
    onDismiss:()->Unit,busy:Boolean=false) {
    var tool by remember(session) {mutableStateOf("DRAW")}
    StudioAlertDialog(onDismiss,{Text(session.initialLayer.name)},{
        Column(Modifier.heightIn(max=560.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("DRAW" to "Brush","ERASE" to "Eraser","SELECT" to "Selection","PAN" to "Pan").forEach { (id,label)->
                    FilterChip(tool==id,{tool=id},enabled=!busy,label={Text(uiText(label))},modifier=Modifier.heightIn(min=48.dp))
                }
            }
            StudioPaintSurface(service,session,tool,onSaveCopy,Modifier.fillMaxWidth().height(400.dp),busy)
        }
    },{}, {TextButton(onDismiss,enabled=!busy) {Text(uiText("Done"))}})
}
