package com.halo.app.ui.screens.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.cards.HaloCardAction
import com.halo.app.ui.cards.HaloCardData
import com.halo.app.ui.cards.HaloCardDeck
import com.halo.app.ui.cards.HaloCardSource
import com.halo.app.ui.cards.homeHaloCards
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.ui.screens.kids.rememberKidsGrownUpGate
import com.halo.sdk.provider.RemoteDataCategory

internal enum class AnswerCardDestination { STUDY, PLANNER, KIDS }
private val externalAddress = Regex("(?i)https?://|www\\.")

internal fun kidsAnswerSentence(card: HaloCardData): String? {
    val text = card.speak.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    val end = Regex("[.!?](?=\\s|$)").find(text)?.range?.last
    val sentence = if (end == null) text else text.take(end + 1)
    return sentence.takeIf { it.length in 1..240 && !externalAddress.containsMatchIn(it) && !externalAddress.containsMatchIn(card.title) }
}

@Composable
internal fun HomeAnswerCards(
    response: PresentedAssistantResponse,
    question: String?,
    providerLabel: String?,
    viewModel: HomeViewModel,
    enabled: Boolean,
    onCheck: (String) -> Unit,
    delayEntry: Boolean = false,
) {
    val turnId = response.turnId ?: return
    val source = stringResource(R.string.dynamic_card_halo_source)
    val cards = remember(turnId, question, response.text, response.structured, source, providerLabel) {
        homeHaloCards(turnId, question, response.text, response.structured, HaloCardSource(source, providerLabel))
    }
    if (cards.isEmpty()) {
        response.structured?.let { com.halo.app.interaction.StructuredPresentationRenderer(it) }
        return
    }
    var review by remember(turnId) { mutableStateOf<Pair<AnswerCardDestination, HaloCardData>?>(null) }
    var gateCard by remember(turnId) { mutableStateOf<HaloCardData?>(null) }
    var gateUnavailable by remember(turnId) { mutableStateOf(false) }
    var readText by remember(turnId) { mutableStateOf("") }
    var readRequest by remember(turnId) { mutableLongStateOf(0L) }
    val grownUpGate = rememberKidsGrownUpGate(onVerified = {
        gateCard?.let { if (enabled) viewModel.keepAnswerCard(response, it, AnswerCardDestination.KIDS) }
        gateCard = null
    }, onUnavailable = { gateCard = null; gateUnavailable = true })
    val actions = if (enabled) listOf(
        HaloCardAction(stringResource(R.string.dynamic_card_keep)) { viewModel.saveResponse(response) },
        HaloCardAction(uiText("Study")) { review = AnswerCardDestination.STUDY to it },
        HaloCardAction(uiText("Planner")) { review = AnswerCardDestination.PLANNER to it },
        HaloCardAction(uiText("Kids"), { kidsAnswerSentence(it) != null }) { review = AnswerCardDestination.KIDS to it },
    ) else emptyList()
    val checkLabel = stringResource(R.string.dynamic_card_verify_prompt)
    HaloCardDeck(cards, actions,
        onRead = if (enabled) ({ _, speech -> readText = speech; readRequest = System.nanoTime().coerceAtLeast(1) }) else null,
        onCheck = if (enabled) ({ card -> onCheck("$checkLabel\n\n${card.title}\n${card.speak}") }) else null,
        delayEntry = delayEntry)
    if (readRequest > 0) HaloVoiceTextActions(text = "", onText = {}, allowDictation = false, readText = readText,
        readRequest = readRequest, showReadAction = false,
        readCategory = RemoteDataCategory.CONVERSATION, readSourceIds = setOf(response.conversationId))
    if (gateUnavailable) AlertDialog(onDismissRequest = { gateUnavailable = false },
        title = { Text(uiText("Grown-up space")) },
        text = { Text(uiText("Ask a grown-up to set a device PIN or password in Android settings first.")) },
        confirmButton = { TextButton({ gateUnavailable = false }) { Text(uiText("OK")) } })
    review?.let { (destination, card) ->
        val detail = when (destination) {
            AnswerCardDestination.STUDY -> R.string.dynamic_card_to_study
            AnswerCardDestination.PLANNER -> R.string.dynamic_card_to_planner
            AnswerCardDestination.KIDS -> R.string.dynamic_card_to_kids
        }
        AlertDialog(onDismissRequest = { review = null }, title = { Text(card.title) },
            text = {
                val summary = kidsAnswerSentence(card).takeIf { destination == AnswerCardDestination.KIDS }
                Text(listOfNotNull(stringResource(detail), summary).joinToString("\n\n"), style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = { TextButton(enabled = enabled, onClick = {
                review = null
                if (destination == AnswerCardDestination.KIDS) {
                    gateCard = card
                    grownUpGate()
                } else viewModel.keepAnswerCard(response, card, destination)
            }) { Text(stringResource(R.string.dynamic_card_continue)) } },
            dismissButton = { TextButton({ review = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
