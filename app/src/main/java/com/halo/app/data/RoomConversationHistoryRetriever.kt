package com.halo.app.data

import androidx.room.withTransaction
import com.halo.app.data.workspace.privateLibraryClaim
import com.halo.runtime.cognition.conversation.*
import com.halo.runtime.cognition.memory.MemorySensitivity
import com.halo.runtime.cognition.memory.MemorySensitivityClassifier
import java.util.Locale

/** Searches existing durable turns; archived/deleted workspace conversations never become hits. */
class RoomConversationHistoryRetriever(
    private val database: AppDatabase,
    private val permitsReference: suspend (conversationId: String, targetIsLocal: Boolean) -> Boolean = { _, _ -> false },
) : ConversationHistoryRetriever {
    override suspend fun search(currentConversationId: String, query: String, limit: Int): List<ConversationHistoryHit> {
        if (currentConversationId.isBlank() || query.isBlank()) return emptyList()
        val conversation = database.conversationDao().getConversation(currentConversationId) ?: return emptyList()
        if (conversation.archived || database.notebookDao().getWorkspaceObject(currentConversationId)?.lifecycle != "ACTIVE") return emptyList()
        val safe = escapeLike(query.trim().take(120))
        return database.conversationDao().searchCurrent(currentConversationId, "%$safe%", limit.coerceIn(1, 8)).map {
            val content = it.userText + "\n" + it.assistantText
            val index = content.indexOf(query.trim(), ignoreCase = true).coerceAtLeast(0)
            ConversationHistoryHit(it.id, it.conversationId, conversation.title, it.timestamp,
                content.substring((index - 60).coerceAtLeast(0)).take(320))
        }
    }

    override suspend fun referenceOtherChats(currentConversationId: String, query: String, targetIsLocal: Boolean): List<ConversationHistoryHit> {
        if (currentConversationId.isBlank() || !permitsReference(currentConversationId, targetIsLocal)) return emptyList()
        val terms = referenceTerms(query)
        val dao = database.conversationDao()
        val preferenceHits = mutableListOf<ConversationHistoryHit>()
        val usedSubjects = mutableSetOf<String>()
        val preferenceSince = System.currentTimeMillis() - java.time.Duration.ofDays(180).toMillis()
        for (turn in dao.recentPreferenceTurns(currentConversationId, preferenceSince)) {
            val preference = spokenPreference(turn.userText) ?: continue
            if (preference.subject in usedSubjects || !preference.applies(query)) continue
            val hit = referenceHit(turn, turn.userText.indexOf(preference.text), targetIsLocal) ?: continue
            preferenceHits += hit
            usedSubjects += preference.subject
        }
        val pool = terms.take(4).flatMap { term -> dao.searchOtherChats(currentConversationId, "%${escapeLike(term)}%") }
            .distinctBy { it.id }
        val matches = pool.mapNotNull { turn ->
            if (spokenPreference(turn.userText)?.subject in usedSubjects) return@mapNotNull null
            val words = referenceTerms(turn.userText).toSet()
            val matched = terms.filter { it in words }
            if (matched.isEmpty()) null else Triple(turn, matched.size, matched.first())
        }.sortedWith(compareByDescending<Triple<PersistedTurn, Int, String>> { it.second }.thenByDescending { it.first.timestamp })
            .take(16).mapNotNull { (turn, _, match) ->
                val start = (turn.userText.indexOf(match, ignoreCase = true) - 64).coerceAtLeast(0)
                referenceHit(turn, start, targetIsLocal)
            }.distinctBy { it.conversationId }
        return (preferenceHits + matches).distinctBy { it.turnId }.take(3)
    }

    private suspend fun referenceHit(turn: PersistedTurn, start: Int, local: Boolean): ConversationHistoryHit? {
        val chat = database.conversationDao().getConversation(turn.conversationId) ?: return null
        if (chat.archived || !safeText(turn.userText, local) || !permitsReference(chat.id, local)) return null
        val source = database.notebookDao().getWorkspaceObject(chat.id)?.takeIf { it.lifecycle == "ACTIVE" } ?: return null
        val title = chat.title.takeIf { safeText(it, local) }.orEmpty().take(100)
        return ConversationHistoryHit(turn.id, chat.id, title, turn.timestamp, turn.userText.drop(start.coerceAtLeast(0)).take(360),
            source.updatedAt.toString())
    }

    override suspend fun stillReferenceable(hit: ConversationHistoryHit, targetIsLocal: Boolean): Boolean = database.withTransaction {
        val current = database.conversationDao().turnById(hit.turnId) ?: return@withTransaction false
        val chat = database.conversationDao().getConversation(hit.conversationId) ?: return@withTransaction false
        val source = database.notebookDao().getWorkspaceObject(chat.id)?.takeIf { it.lifecycle == "ACTIVE" } ?: return@withTransaction false
        val expectedTitle = chat.title.takeIf { safeText(it, targetIsLocal) }.orEmpty().take(100)
        current.conversationId == chat.id && !chat.archived && source.updatedAt.toString() == hit.conversationVersion && current.timestamp == hit.timestamp &&
            safeText(current.userText, targetIsLocal) && current.userText.contains(hit.snippet) && expectedTitle == hit.title &&
            database.privateLibraryDao().claims(listOf(privateLibraryClaim(chat.id), privateLibraryClaim(current.id))).isEmpty() &&
            permitsReference(chat.id, targetIsLocal)
    }

    private fun safeText(text: String, local: Boolean): Boolean = when (MemorySensitivityClassifier.classify(text)) {
        MemorySensitivity.SECRET -> false
        MemorySensitivity.SENSITIVE -> local
        else -> true
    }

    private fun escapeLike(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun referenceTerms(text: String): List<String> = words.findAll(text.take(2_000).lowercase(Locale.ROOT))
        .map { it.value }.filter { it !in fillerWords }.distinct().take(12).toList()

    private companion object {
        val words = Regex("[\\p{L}\\p{N}]{3,}")
        val fillerWords = setOf("what", "when", "where", "which", "with", "have", "that", "this", "from", "about", "your", "you", "the", "and", "was", "were", "can", "could", "please", "remember", "talked", "said", "мой", "моя", "мои", "как", "где", "что", "это", "мне", "помнишь", "расскажи", "скажи")
    }
}
