package com.halo.app.ui.zone6

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.theme.rememberVisualPreferences

internal class Zone6SheetHost {
    class Sheet(val title: String, val anchorX: Float?, val close: () -> Unit, val blockWorkingArea: Boolean,
        val content: @Composable ColumnScope.() -> Unit)
    var sheet by mutableStateOf<Sheet?>(null)
    var anchorX: Float? = null
    fun dismiss() { sheet?.close?.invoke() }
}

internal val LocalZone6SheetHost = staticCompositionLocalOf<Zone6SheetHost?> { null }

/** Anchored to the working region. The dock and its hardware Back handling remain separate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Zone6ButtonSheet(title: String, onDismiss: () -> Unit, blockWorkingArea: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    val host = LocalZone6SheetHost.current
    if (host == null) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            SheetHeading(title, onDismiss)
            content()
        }
        return
    }
    val currentContent by rememberUpdatedState(content)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    DisposableEffect(host, title, blockWorkingArea) {
        val entry = Zone6SheetHost.Sheet(title, host.anchorX, { currentDismiss() }, blockWorkingArea) {
            val column = this
            CompositionLocalProvider(locals) { currentContent(column) }
        }
        host.sheet = entry
        onDispose { if (host.sheet === entry) host.sheet = null }
    }
}

@Composable
internal fun BoxScope.Zone6SheetLayer(host: Zone6SheetHost) {
    val sheet = host.sheet ?: return
    val closeLabel = uiText("Close")
    val visual = rememberVisualPreferences()
    val reduce by rememberReducedMotion()
    val progress = remember(sheet) { Animatable(0f) }
    val distance = with(LocalDensity.current) { 36.dp.toPx() }
    LaunchedEffect(sheet, reduce, visual.motionLevel) {
        when {
            reduce -> progress.animateTo(1f, tween(150))
            visual.motionLevel == HaloLookMotion.FULL -> progress.animateTo(1f, spring(dampingRatio = .62f, stiffness = 380f))
            else -> progress.animateTo(1f, tween(220))
        }
    }
    BackHandler { sheet.close() }
    if (sheet.blockWorkingArea) {
        Box(Modifier.matchParentSize().background(Color(0xFF030122).copy(alpha = .55f))
            .clickable(onClickLabel = closeLabel, onClick = sheet.close))
    }
    var panelLeft by remember(sheet) { mutableFloatStateOf(0f) }
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp).fillMaxWidth()
        .padding(horizontal = 10.dp).padding(bottom = 8.dp)
        .onGloballyPositioned { panelLeft = it.boundsInWindow().left }
        .graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            if (!reduce) translationY = distance * (1f - progress.value)
        }
        .semantics { paneTitle = sheet.title }) {
        Surface(shape = RoundedCornerShape(30.dp), color = fill,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            Column {
                SheetHeading(sheet.title, sheet.close)
                sheet.content(this)
            }
        }
        sheet.anchorX?.let { anchor ->
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val span = 8.dp.toPx()
                if (size.width > span * 4) {
                    val x = (anchor - panelLeft).coerceIn(span * 2, size.width - span * 2)
                    drawPath(Path().apply { moveTo(x - span, 0f); lineTo(x, size.height); lineTo(x + span, 0f); close() }, fill)
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(title: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 7.dp, end = 7.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(3.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            IconButton(onClose, Modifier.size(48.dp)) { HaloIcon(HaloGlyph.STOP, uiText("Close")) }
        }
    }
}
