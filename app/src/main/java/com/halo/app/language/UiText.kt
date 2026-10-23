package com.halo.app.language

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/** Only call for app-authored UI labels, never for source text, model replies or tool output. */
@Composable
fun uiText(english: String, vararg arguments: Any): String {
    return resolveUiText(LocalResources.current, english, *arguments)
}

/** Same formatting contract for Compose and native Android host surfaces. */
internal fun resolveUiText(resources: Resources, english: String, vararg arguments: Any): String {
    val id = uiCatalogue[english]
    return if (id == null) {
        if (arguments.isEmpty()) english else String.format(resources.configuration.locales[0], english, *arguments)
    } else if (arguments.isEmpty()) resources.getString(id) else resources.getString(id, *arguments)
}
