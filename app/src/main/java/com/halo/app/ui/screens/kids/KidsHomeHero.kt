package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation

/** K01 chrome; the credential gate is owned by the caller. */
@Composable
internal fun KidsHomeHero(signal: HaloSignalPresentation, onListen: () -> Unit,
    onGrownUp: () -> Unit, onStop: () -> Unit, showStop: Boolean) {
    val enlarged = LocalDensity.current.fontScale >= 1.5f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("Hi there!"), Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp,
                    lineHeight = 30.sp, fontWeight = FontWeight.SemiBold))
            if (showStop) IconButton(onStop, Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.halo_ic_stop), uiText("Stop"))
            }
            IconButton(onGrownUp, Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.halo_ic_privacy), uiText("Grown-up space"))
            }
        }
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current)
            HaloSignalFace(signal, kids = true, size = if (enlarged) 64.dp else 96.dp,
                contentDescription = uiText("Talk to halo"), onClick = onListen)
        Text(uiText("What shall we do today?"), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp,
                lineHeight = 32.sp, fontWeight = FontWeight.SemiBold))
    }
}
