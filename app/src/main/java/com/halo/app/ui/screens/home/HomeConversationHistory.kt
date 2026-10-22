package com.halo.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.PersistedConversation
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeConversationHistory(conversations: List<PersistedConversation>, currentId: String?,
    onSelect: (String) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(uiText("Conversation history"), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onNew, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("New conversation")) }
            if (conversations.isEmpty()) Text(stringResource(R.string.home_history_empty), Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    ListItem(
                        headlineContent = { Text(conversation.title.ifBlank { uiText("New conversation") },
                            maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
                                .format(Date(conversation.updatedAt.takeIf { it > 0 } ?: conversation.createdAt)))
                        },
                        trailingContent = if (conversation.id == currentId) ({
                            HaloIcon(HaloGlyph.SUCCESS, stringResource(R.string.home_current_chat))
                        }) else null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onSelect(conversation.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
