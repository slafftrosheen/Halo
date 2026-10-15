package com.halo.app.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.halo.app.language.AppLanguage
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

private val languageOrder = listOf(AppLanguage.ENGLISH, AppLanguage.LATVIAN, AppLanguage.RUSSIAN)

private fun AppLanguage.label() = when (this) {
    AppLanguage.ENGLISH -> "English"
    AppLanguage.LATVIAN -> "Latvian"
    AppLanguage.RUSSIAN -> "Russian"
}

/** Presentation only: selecting a language uses the existing persisted preference. */
@Composable
internal fun LanguageSettingsScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val labels = languageOrder.associateWith { uiText(it.label()) }
    val results = languageOrder.filter { option ->
        val term = query.trim()
        listOf(option.nativeName, option.label(), labels.getValue(option), option.tag)
            .any { it.contains(term, ignoreCase = true) }
    }
    BackHandler(onBack = onBack)
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentPadding = PaddingValues(horizontal = HaloSpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HaloScreenHeader(uiText("Language"), uiText("halo’s words and replies"), navigation = onBack)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(uiText("Search languages")) },
                leadingIcon = { HaloIcon(HaloGlyph.SEARCH, contentDescription = null, modifier = Modifier.size(24.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(uiText("Clear")) }
                },
                shape = HaloShapes.pill,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary),
            )
        }
        item {
            Surface(
                shape = HaloShapes.card,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 16.dp)) {
                    if (results.isEmpty()) {
                        Text(uiText("No languages found."), Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    results.forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .selectable(selected = option == language, role = Role.RadioButton,
                                    onClick = { onLanguage(option) })
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(option.nativeName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text(labels.getValue(option), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (option == language) HaloIcon(HaloGlyph.SUCCESS, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        item {
            Text(uiText("Choose the language for halo’s interface and default replies."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(uiText("You can ask for a reply in another language at any time."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
