package com.halo.app.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import com.halo.app.ui.zone5.*

/** The artboard owns the working area; manual tools and the current layer have stable positions. */
@Composable
internal fun StudioWorkbench(service: StudioService, project: StudioProject, busy: Boolean,
    onDraw: () -> Unit, onText: () -> Unit, onShapes: () -> Unit, onImage: () -> Unit,
    onLayers: () -> Unit, onTransform: () -> Unit, onSelect: (String) -> Unit, onEdit: (StudioLayer) -> Unit,
    modifier: Modifier = Modifier,onClear:()->Unit={},onGestureEdit:((StudioLayer)->Unit)?=null,
    fitToken:Int=0,onPick:((Int)->Unit)?=null,onPointPick:((Int,Int)->Unit)?=null) {
    StudioCanvasSurface(service,project,busy,{id->if(id==null) onClear() else onSelect(id)},
        {layer->onGestureEdit?.invoke(layer) ?: onEdit(layer)},onEdit,modifier,fitToken,onPick,onPointPick)
}
