package com.halo.app.ui.screens.kids

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader

/** Child commands keep their current owners, including the credential-gated parent action. */
@Composable
internal fun KidsZoneBinding(destination: KidsDestination, lessonTitle: String?, status: String,
    saving: Boolean, busy: Boolean, onType: () -> Unit, onHelp: () -> Unit,
    onStop: () -> Unit, onGrownUp: () -> Unit) {
    val owner = ShellPageOwner("kids", sourceIdentity = destination.name, statusLabel = uiText(status),
        nextStep = if (busy || saving) null else ShellNextStep("zone5.kids.type.v1", "page.kids.type", uiText("Type instead")))
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.kids.type", "Type instead", HaloGlyph.NOTE, !saving, onType),
        ShellPageAction("page.kids.help", "What can I say?", HaloGlyph.QUESTION, onInvoke = onHelp),
        ShellPageAction("page.kids.stop", "Stop", HaloGlyph.STOP, busy, onStop),
        ShellPageAction("page.kids.grown-up", "Grown-up space", HaloGlyph.SECURITY, onInvoke = onGrownUp),
    ))
    val title = when (destination) {
        KidsDestination.HOME -> uiText("Kids")
        KidsDestination.CREATE -> uiText("My project")
        else -> lessonTitle?.takeIf(String::isNotBlank) ?: uiText("Learn something")
    }
    BindShellUtilityBand(owner) {
        KidsBrandTheme { ToolZoneHeader(title, onBack = {}, subtitle = uiText(status)) }
    }
}
