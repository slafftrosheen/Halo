package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.PersistedTurn
import com.halo.app.data.RoomConversationStore
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader

@Composable
fun ConversationsListScreen(
    conversationStore: RoomConversationStore,
    onOpenConversation: (String) -> Unit,
    onBack: () -> Unit
) {
    val history by conversationStore.history.collectAsStateWithLifecycle()
    val summaries by conversationStore.conversations.collectAsStateWithLifecycle()
    val loaded by conversationStore.historyLoaded.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val conversations = remember(history, summaries, query) {
        val titles = summaries.associate { it.id to it.title }
        history
            .groupBy { it.conversationId }
            .map { (id, turns) -> Triple(id, titles[id].orEmpty(), turns.sortedBy { it.timestamp }) }
            .filter { (_, title, turns) ->
                query.isBlank() || title.contains(query, ignoreCase = true) || turns.any {
                    it.userText.contains(query, ignoreCase = true) ||
                        it.assistantText.contains(query, ignoreCase = true)
                }
            }
            .sortedByDescending { (_, _, turns) -> turns.lastOrNull()?.timestamp ?: 0L }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        HaloWorkspaceHeader(
            title = uiText("Saved chats"),
            subtitle = uiText("Browse and continue earlier conversations"),
            onBack = onBack
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(uiText("Search chats")) }
        )

        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            conversations.isEmpty() -> EmptyConversations(hasQuery = query.isNotBlank())

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conversations, key = { it.first }) { (id, savedTitle, turns) ->
                    ConversationCard(savedTitle = savedTitle, turns = turns, onOpen = { onOpenConversation(id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyConversations(hasQuery: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (hasQuery) Icons.Rounded.Search else Icons.Rounded.Forum,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = uiText(if (hasQuery) "No matching chats" else "No saved chats yet"),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = uiText(if (hasQuery) "Try a different word or phrase." else "Chats you start with halo will appear here."),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConversationCard(savedTitle: String, turns: List<PersistedTurn>, onOpen: () -> Unit) {
    val firstTurn = turns.firstOrNull()
    val lastTurn = turns.lastOrNull()
    val title = savedTitle.takeUnless { it.isBlank() || it == "New conversation" }
        ?: firstTurn?.userText?.trim()?.take(80)?.ifBlank { null }
        ?: "Conversation"
    val timestamp = lastTurn?.timestamp?.let {
        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }

    Surface(
        onClick = onOpen,
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = com.halo.app.ui.design.HaloShapes.control,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.Forum,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                lastTurn?.assistantText?.takeIf { it.isNotBlank() }?.let { preview ->
                    Text(
                        text = preview,
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = listOfNotNull(
                        uiText("Messages: %1\$d", turns.size),
                        timestamp
                    ).joinToString(" · "),
                    modifier = Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 10.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ConversationDetailScreen(
    conversationId: String,
    savedTitle: String?,
    turns: List<PersistedTurn>,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val orderedTurns = remember(conversationId, turns) { turns.sortedBy { it.timestamp } }
    val title = savedTitle?.takeUnless { it.isBlank() || it == "New conversation" }
        ?: orderedTurns.firstOrNull()?.userText?.trim()?.take(60)?.ifBlank { null }
        ?: "Saved chat"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        HaloWorkspaceHeader(
            title = title,
            subtitle = uiText("Messages: %1\$d", orderedTurns.size),
            onBack = onBack,
            trailing = {
                TextButton(onClick = onContinue, enabled = orderedTurns.isNotEmpty()) { Text(uiText("Continue")) }
            }
        )

        if (orderedTurns.isEmpty()) {
            HaloWorkspaceCard(modifier = Modifier.padding(top = 20.dp)) {
                Text(uiText("This chat is empty"), style = MaterialTheme.typography.titleMedium)
                Text(
                    uiText("There are no saved messages to display."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(orderedTurns, key = { it.id }) { turn ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        MessageBubble(
                            text = turn.userText,
                            fromUser = true,
                            modifier = Modifier.align(Alignment.End)
                        )
                        MessageBubble(
                            text = turn.assistantText,
                            fromUser = false,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, fromUser: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(0.88f).widthIn(max = 560.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
