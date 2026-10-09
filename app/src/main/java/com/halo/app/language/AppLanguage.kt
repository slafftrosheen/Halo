package com.halo.app.language

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.sdk.runtime.Storage
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"), RUSSIAN("ru", "Русский"), LATVIAN("lv", "Latviešu");
    companion object {
        fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_') } ?: ENGLISH
    }
}

class AppLanguagePreferences(private val storage: Storage, deviceLanguage: String = Locale.getDefault().language) {
    private val selected = MutableStateFlow(AppLanguage.fromTag(storage.getString(KEY) ?: deviceLanguage))
    val language = selected.asStateFlow()
    fun select(language: AppLanguage) {
        storage.putString(KEY, language.tag)
        selected.value = language
    }
    companion object { const val KEY = "app_language_v1" }
}

/** Changes presentation without restarting a runtime turn or throwing away an unsent draft. */
@Composable
fun AppLanguageProvider(preferences: AppLanguagePreferences, content: @Composable () -> Unit) {
    val language by preferences.language.collectAsStateWithLifecycle()
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(base, configuration, language) {
        base.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) })
    }
    // Preserve the Activity context and its activity-result registry for camera/file pickers.
    CompositionLocalProvider(LocalConfiguration provides localized.resources.configuration,
        LocalResources provides localized.resources, content = content)
}
