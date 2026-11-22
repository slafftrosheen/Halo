package com.halo.app.ui.screens.study

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.study.StudySourceReference
import com.halo.app.ui.cards.HaloCardAction
import com.halo.app.ui.cards.HaloCardBody
import com.halo.app.ui.cards.HaloCardData
import com.halo.app.ui.cards.HaloCardDeck
import com.halo.app.ui.cards.HaloCardPicture
import com.halo.app.ui.cards.HaloCardRow
import com.halo.app.ui.cards.HaloCardSize
import com.halo.app.ui.cards.HaloCardSource
import com.halo.app.ui.cards.HaloCardType
import com.halo.app.ui.cards.cardSpeech
import com.halo.app.ui.cards.cardPracticeAnswer
import com.halo.app.ui.cards.haloCardSteps
import com.halo.app.ui.cards.haloCardTimeline
import com.halo.app.ui.cards.homeHaloCards
import com.halo.sdk.runtime.study.TutorStage

private val labelSection = Regex("(?im)^#{1,5}\\s+(?:label it|назови части|atzīmē daļas|podpisz części)\\s*:?$")
private val nextSection = Regex("(?m)^#{1,5}\\s+")
private val labelItem = Regex("^[-*•]\\s+(.+)$")

private fun explicitLabels(turn: StudyTurn, sources: List<StudySourceReference>): HaloCardBody.Labels? {
    val labelsStart = labelSection.find(turn.text)?.range?.last?.plus(1) ?: return null
    val lines = turn.text.substring(labelsStart).trimStart()
    val end = nextSection.find(lines)?.range?.first ?: lines.length
    val labels = lines.take(end).lineSequence().mapNotNull {
        labelItem.matchEntire(it.trim())?.groupValues?.get(1)?.let { label -> HaloCardRow(label) }
    }.take(8).toList()
    val visual = sources.firstOrNull { source ->
        !source.stale && (source.resolved as? com.halo.app.study.ResolvedStudySource.Visual)?.locator is com.halo.app.study.StudySourceLocator.Image &&
            source.version != null && turn.sourceVersions?.get(source.objectId) == source.version
    } ?: return null
    val picture = visual.resolved as com.halo.app.study.ResolvedStudySource.Visual
    return if (labels.size < 2) null else HaloCardBody.Labels(
        HaloCardPicture(visual.objectId, visual.title, visual.version, picture.asset), labels)
}

private fun studyCards(
    turn: StudyTurn?,
    state: StudyUiState,
    size: HaloCardSize,
    title: String,
    unverified: String,
    reflectPrompt: String,
    helpOpen: Boolean,
): List<HaloCardData> {
    val session = state.session ?: return emptyList()
    val citation = turn?.citations?.firstOrNull { cited ->
        cited.sourceVersion != null && state.sources.any { it.objectId == cited.sourceObjectId && it.version == cited.sourceVersion && !it.stale }
    }
    val citedTitle = citation?.sourceTitle ?: state.sources.firstOrNull { it.objectId == citation?.sourceObjectId }?.title
    val source = HaloCardSource(citedTitle ?: unverified, citation?.locator)
    val validTurn = turn?.takeIf { it.failure == null && it.text.isNotBlank() }
    fun build(type: HaloCardType, body: HaloCardBody, cardSource: HaloCardSource = source) =
        HaloCardData("${session.id}:${validTurn?.id}:${type.name}", type, title, body, cardSource,
            cardSpeech(body), validTurn?.uncertainty, size)
    return when (learnPathStep(state.journey.stage, helpActive = helpOpen, finished = state.isFinished)) {
        LearnPathStep.LOOK -> if (validTurn == null) emptyList() else buildList {
            haloCardTimeline(validTurn.text).takeIf { it.size >= 2 }?.let {
                add(build(HaloCardType.TIMELINE, HaloCardBody.Items(it)))
            }
            explicitLabels(validTurn, state.sources)?.let { labels ->
                add(build(HaloCardType.LABEL_IT, labels, HaloCardSource(labels.picture.caption ?: source.label)))
            }
        }
        LearnPathStep.TRY -> if (validTurn == null) emptyList() else buildList {
            if (validTurn.stage == TutorStage.HINT) {
                add(build(HaloCardType.HELP, HaloCardBody.Summary(validTurn.text)))
            } else {
                haloCardSteps(validTurn.text).takeIf { it.size >= 2 }?.let {
                    add(build(HaloCardType.WORKED_EXAMPLE, HaloCardBody.Items(it)))
                }
                addAll(homeHaloCards(validTurn.id, title, validTurn.text, null, source)
                    .filter { it.type == HaloCardType.QUICK_CHECK }.map { it.copy(size = size, uncertainty = validTurn.uncertainty) })
            }
        }
        LearnPathStep.KEEP -> if (state.isFinished) emptyList() else listOf(
            build(HaloCardType.EXPLAIN_BACK, HaloCardBody.Summary(reflectPrompt,
                state.journey.reflection.takeIf(String::isNotBlank))))
        LearnPathStep.HELP -> if (validTurn?.stage == TutorStage.HINT)
            listOf(build(HaloCardType.HELP, HaloCardBody.Summary(validTurn.text))) else emptyList()
        else -> emptyList()
    }
}

@Composable
internal fun StudyDynamicCards(turn: StudyTurn?, state: StudyUiState, presentation: StudyPresentation,
    viewModel: StudyViewModel, helpOpen: Boolean, onExplainBack: () -> Unit, onCheck: (HaloCardData) -> Unit) {
    val unverified = stringResource(R.string.dynamic_card_study_source)
    val title = state.journey.currentBranch ?: state.session?.goal ?: uiText("Study")
    val prompt = stringResource(R.string.dynamic_card_reflect)
    val size = when (presentation.size) {
        StudyPresentationSize.PICTURES -> HaloCardSize.PICTURES
        StudyPresentationSize.SIMPLE -> HaloCardSize.SIMPLE
        StudyPresentationSize.FULL -> HaloCardSize.FULL
    }
    val cards = remember(turn, state.session, state.sources, state.journey, state.isFinished, size, title, unverified, prompt, helpOpen) {
        studyCards(turn, state, size, title, unverified, prompt, helpOpen)
    }
    if (cards.isEmpty()) return
    val canSave = turn != null && turn.failure == null && !state.loading && !state.isGenerating && !state.isSubmitting
    val actions = buildList {
        if (canSave && turn != null) add(HaloCardAction(stringResource(R.string.dynamic_card_study_keep),
            { it.type != HaloCardType.EXPLAIN_BACK && it.type != HaloCardType.HELP }) { card ->
            viewModel.saveTutorCard(turn.id, card.title, cardPracticeAnswer(card))
        })
        add(HaloCardAction(stringResource(R.string.dynamic_card_reflect_action), { it.type == HaloCardType.EXPLAIN_BACK }) {
            onExplainBack()
        })
    }
    HaloCardDeck(cards, actions,
        onRead = if (state.narrationAvailable) ({ card, speech ->
            if (turn == null || card.type == HaloCardType.EXPLAIN_BACK) viewModel.readLearningPrompt(speech)
            else viewModel.readTutorCardAloud(turn, speech)
        }) else null,
        onCheck = onCheck)
}
