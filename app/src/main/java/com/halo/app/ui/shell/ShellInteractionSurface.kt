package com.halo.app.ui.shell

import androidx.compose.runtime.Composable
import com.halo.app.interaction.InteractionSurfaceRenderer
import com.halo.runtime.cognition.interaction.InteractionContent
import com.halo.runtime.cognition.interaction.InteractionResponse
import com.halo.runtime.cognition.interaction.InteractionSurface

/** Input dialogs remain global. Passive run progress is already owned by Activity.
 * Mounting its inline card behind the Scaffold still runs an invisible frame clock.
 * This changes presentation only: no request is dismissed, answered or cancelled.
 */
@Composable
internal fun ShellInteractionSurface(surface: InteractionSurface, onRespond: (InteractionResponse) -> Unit) {
    if (surface.content is InteractionContent.Progress) return
    InteractionSurfaceRenderer(surface, onRespond)
}
