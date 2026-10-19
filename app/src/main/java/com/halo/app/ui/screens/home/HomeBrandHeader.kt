package com.halo.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.fx.HaloRipple
import com.halo.app.ui.fx.haloAura
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation

@Composable
internal fun HomeBrandHeader(onUpdates: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Image(painterResource(if (scheme.background.luminance() < .5f)
            R.drawable.halo_wordmark_dark else R.drawable.halo_wordmark_light),
            contentDescription = uiText("halo"), modifier = Modifier.width(64.dp).height(28.dp),
            contentScale = ContentScale.Fit)
        FilledTonalIconButton(onClick = onUpdates, modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = scheme.surfaceContainerHigh)) {
            Icon(painterResource(R.drawable.halo_ic_notifications), uiText("Updates"), Modifier.size(24.dp))
        }
    }
}

/** Real state, large presence on arrival, compact during replies or enlarged text. */
@Composable
internal fun HomePresenceHeader(
    signal: HaloSignalPresentation,
    title: String,
    supporting: String? = null,
    compact: Boolean = false,
    micOpen: Boolean = false,
    inputLevel: Float = 0f,
    aura: Boolean = false,
    orbModifier: Modifier = Modifier,
) {
    val folded = compact || LocalDensity.current.fontScale >= 1.5f
    // H01: an on-page presence, not the full-screen onboarding hero.
    // The smaller ring leaves the feed visible at 360dp and 200% text.
    val size = if (folded) 48.dp else 112.dp
    val presence: @Composable () -> Unit = {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            HomeVoiceSummonFx(micOpen)
            HaloRipple(micOpen = micOpen, level = inputLevel, ringSize = size,
                accent = MaterialTheme.colorScheme.primary)
            HaloSignalFace(signal, size = size,
                modifier = orbModifier.then(if (aura)
                    Modifier.haloAura(MaterialTheme.colorScheme.primaryContainer) else Modifier))
        }
    }
    val words: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, horizontalAlignment = if (folded) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium,
                textAlign = if (folded) TextAlign.Start else TextAlign.Center)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (folded) TextAlign.Start else TextAlign.Center) }
        }
    }
    if (folded) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        presence(); words(Modifier.weight(1f))
    } else Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presence(); words(Modifier.fillMaxWidth())
    }
}
