package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.notebook.LibraryCollectionSuggestion
import com.halo.app.data.notebook.LibrarySuggestedSource
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.librarySuggestionSourceTypes
import java.util.Locale

private val collectionWord = Regex("[\\p{L}\\p{N}]+")
private val tripPrefixes = listOf(listOf("trip", "to"), listOf("поездка", "в"), listOf("brauciens", "uz"), listOf("wyjazd", "do"))

/** Suggest only an exact phrase found in saved titles; this rule has no location or inference access. */
internal fun suggestLibraryCollection(state: NotebookUiState, dismissed: Set<LibraryCollectionSuggestion>): LibraryCollectionSuggestion? {
    if (!state.isLoaded || state.loadError || state.filter != NotebookFilter.ALL ||
        state.query.isNotBlank() || state.selectedCollectionId != null) return null
    val sources = state.items.asSequence().map { it.objectRef }.filter {
        it.lifecycle == WorkspaceObjectLifecycle.ACTIVE && it.type.name in librarySuggestionSourceTypes &&
            !it.id.value.startsWith("learning:") && !it.contentRef.startsWith("file-tools:")
    }.sortedWith(compareByDescending<com.halo.app.data.notebook.WorkspaceObject> { it.updatedAt }.thenBy { it.id.value })
        .map { it to words(it.title) }.toList()
    for (collection in state.collections) {
        val terms = words(collection.name)
        val prefix = tripPrefixes.firstOrNull { terms.take(it.size) == it }
        val phrase = terms.drop(prefix?.size ?: 0)
        if (phrase.isEmpty() || phrase.sumOf { it.length } < 4 || phrase.none { token -> token.any(Char::isLetter) }) continue
        val members = state.collectionLinks[collection.id].orEmpty()
        val matches = sources.asSequence().filter { (item, title) ->
            item.id.value !in members && title.windowed(phrase.size).any { it == phrase }
        }.map { (item, _) -> LibrarySuggestedSource(item.id.value, item.title, item.updatedAt) }.take(12).toList()
        if (matches.isNotEmpty()) {
            val suggestion = LibraryCollectionSuggestion(collection.id, collection.name, matches)
            if (suggestion !in dismissed) return suggestion
        }
    }
    return null
}

private fun words(value: String): List<String> = collectionWord.findAll(value.lowercase(Locale.ROOT)).map { it.value }.toList()

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LibraryCollectionSuggestionCard(suggestion: LibraryCollectionSuggestion, enabled: Boolean,
    onAdd: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, colors.primary.copy(alpha = .65f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.halo11_library_suggestion_title), style = MaterialTheme.typography.labelLarge,
                color = colors.primary)
            Text(stringResource(R.string.halo11_library_suggestion_message, suggestion.sources.size, suggestion.collectionName),
                style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, enabled = enabled) { Text(stringResource(R.string.halo11_library_suggestion_add)) }
                TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.halo11_library_suggestion_later)) }
            }
        }
    }
}
