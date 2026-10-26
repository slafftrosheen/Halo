package com.halo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.halo.app.ui.presence.LocalShellHaloOrb

/** Root work surfaces reveal the one shell-owned backdrop. Cards, paper, dialogs and
 * standalone hosts keep their own opaque surfaces; the global color scheme never changes.
 */
@Composable
fun haloWorkspaceBackground(): Color =
    if (LocalShellHaloOrb.current) Color.Transparent else MaterialTheme.colorScheme.background
