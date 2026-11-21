package com.halo.app.ui.zone6

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import kotlinx.coroutines.delay

internal data class Zone6Start(val label: String, val glyph: HaloGlyph, val onStart: () -> Unit)

@Composable
internal fun Zone6RingEmptyState(title: String, message: String, glyph: HaloGlyph,
                                 starts: List<Zone6Start> = emptyList(), modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = size.minDimension * .058f
                val inset = size.minDimension * .13f
                drawArc(colors.primary, -27f, 324f, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            HaloIcon(glyph, null, Modifier.size(26.dp), colors.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, Modifier.widthIn(max = 340.dp), style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        starts.take(3).forEach { start ->
            OutlinedButton(start.onStart, Modifier.widthIn(min = 172.dp).heightIn(min = 48.dp)) {
                HaloIcon(start.glyph, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(start.label)
            }
        }
    }
}

@Composable
internal fun Zone6DelayedLoading(loading: Boolean, modifier: Modifier = Modifier) {
    var phase by remember(loading) { mutableIntStateOf(0) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(400)
            phase = 1
            delay(2600)
            phase = 2
        }
    }
    if (!loading || phase == 0) return
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(.92f, .76f, .64f).forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(14.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(6.dp)))
        }
        if (phase == 2) Text(androidx.compose.ui.res.stringResource(R.string.z6_loading_slow),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Zone6OfflineLine(message: String) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val blue = if (dark) Color(0xFFB6DFE2) else Color(0xFF205A78)
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp), color = blue.copy(alpha = .13f), contentColor = blue) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloIcon(HaloGlyph.OFFLINE, null, Modifier.size(20.dp), blue)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
