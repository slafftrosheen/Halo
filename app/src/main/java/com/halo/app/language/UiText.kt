package com.halo.app.language

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.halo.app.R
import org.json.JSONObject

/** Only call for app-authored UI labels, never for source text, model replies or tool output. */
@Composable
fun uiText(english: String, vararg arguments: Any): String {
    return resolveUiText(LocalResources.current, english, *arguments)
}

/** Same formatting contract for Compose and native Android host surfaces. */
internal fun resolveUiText(resources: Resources, english: String, vararg arguments: Any): String {
    val id = uiCatalogue[english]
    return if (id == null) {
        val locale = resources.configuration.locales[0]
        val translated = (if (locale.language == "ru") Halo95RussianText.find(resources, english) else null)
            ?: StudyJourneyCopy.translate(english, locale.language)
        if (arguments.isEmpty()) translated else String.format(locale, translated, *arguments)
    } else if (arguments.isEmpty()) resources.getString(id) else resources.getString(id, *arguments)
}

private object Halo95RussianText {
    @Volatile private var entries: Map<String, String>? = null

    fun find(resources: Resources, english: String): String? {
        val dictionary = entries ?: synchronized(this) {
            entries ?: resources.openRawResource(R.raw.halo_95_ru_ui).bufferedReader().use { input ->
                val values = JSONObject(input.readText())
                values.keys().asSequence().associateWith(values::getString)
            }.also { entries = it }
        }
        return dictionary[english]
    }
}
