package com.halo.app.nativeactions

import android.content.Context
import android.content.res.Configuration
import com.halo.app.di.ManualDependencyGraph

/** Reuse Halo's explicit app language for native review dialogs. */
internal fun Context.nativeUi(english: String, vararg arguments: Any): String {
    val locale = java.util.Locale.forLanguageTag(ManualDependencyGraph.appLanguagePreferences.language.value.tag)
    val localized = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })
    return com.halo.app.language.resolveUiText(localized.resources, english, *arguments)
}
