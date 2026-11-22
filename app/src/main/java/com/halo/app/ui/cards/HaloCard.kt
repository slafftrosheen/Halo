package com.halo.app.ui.cards

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.halo.app.R
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.fx.rememberHaloReducedMotion
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.brandAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class HaloCardAction(val label: String, val available: (HaloCardData) -> Boolean = { true },
    val run: (HaloCardData) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HaloCardDeck(
    cards: List<HaloCardData>,
    actions: List<HaloCardAction> = emptyList(),
    onRead: ((HaloCardData, String) -> Unit)? = null,
    onCheck: ((HaloCardData) -> Unit)? = null,
    delayEntry: Boolean = false,
) {
    if (cards.isEmpty()) return
    val deck = cards.take(3)
    val still = rememberHaloReducedMotion()
    val pager = rememberPagerState { deck.size }
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(deck.first().id) { mutableStateOf(false) }
    var visibleCount by rememberSaveable(deck.first().id) { mutableIntStateOf(0) }
    LaunchedEffect(deck.first().id, deck.size, still, delayEntry) {
        if (!delayEntry || still) visibleCount = deck.size
        else for (index in visibleCount until deck.size) {
            delay(if (index == 0) 360L else 60L)
            visibleCount = index + 1
        }
    }
    if (visibleCount == 0) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (deck.size == 1) {
            HaloCard(deck.first(), still, onExpand = { expanded = true }, actions, onRead, onCheck)
        } else {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), pageSpacing = 12.dp,
                userScrollEnabled = visibleCount == deck.size, verticalAlignment = Alignment.Top, key = { deck[it].id }) { page ->
                if (page < visibleCount) HaloCard(deck[page], still, onExpand = { expanded = true }, actions, onRead, onCheck)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { scope.launch { if (still) pager.scrollToPage(pager.currentPage - 1) else pager.animateScrollToPage(pager.currentPage - 1) } },
                    enabled = pager.currentPage > 0) { HaloIcon(HaloGlyph.PREVIOUS, uiText("Previous card")) }
                Text(stringResource(R.string.dynamic_card_position, pager.currentPage + 1, deck.size),
                    style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { scope.launch { if (still) pager.scrollToPage(pager.currentPage + 1) else pager.animateScrollToPage(pager.currentPage + 1) } },
                    enabled = pager.currentPage < visibleCount - 1) { HaloIcon(HaloGlyph.NEXT, uiText("Next card")) }
            }
        }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            HaloCard(deck[pager.currentPage.coerceIn(deck.indices)], still, onExpand = null,
                actions = actions.map { action -> action.copy(run = { card -> expanded = false; action.run(card) }) },
                onRead = onRead, onCheck = onCheck?.let { check -> { card -> expanded = false; check(card) } })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HaloCard(
    card: HaloCardData,
    still: Boolean,
    onExpand: (() -> Unit)?,
    actions: List<HaloCardAction>,
    onRead: ((HaloCardData, String) -> Unit)?,
    onCheck: ((HaloCardData) -> Unit)?,
) {
    var actionMenu by remember { mutableStateOf(false) }
    var flipped by rememberSaveable(card.id) { mutableStateOf(false) }
    val cardActions = actions.filter { it.available(card) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < .4f
    val help = brandAccent(dark, HaloAccent.LILAC)
    val isHelp = card.type == HaloCardType.HELP
    val target = Modifier.heightIn(min = 48.dp)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = if (isHelp) help.fill else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isHelp) help.onFill else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HaloIcon(card.type.glyph, null, Modifier.size(16.dp))
                Text(cardTypeLabel(card.type).uppercase(), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth().then(if (onExpand != null) target.clickable(
                onClickLabel = stringResource(R.string.dynamic_card_open), onClick = onExpand) else Modifier),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.title, modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = if (onExpand != null) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                if (onExpand != null) HaloIcon(HaloGlyph.OPEN_APP, null, Modifier.size(20.dp))
            }
            CardBody(card, still, expanded = onExpand == null, flipped = flipped, onFlip = { flipped = !flipped })
            Text(stringResource(R.string.dynamic_card_source, listOfNotNull(card.source.label, card.source.locator).joinToString(" · ")),
                style = MaterialTheme.typography.labelSmall)
            card.uncertainty?.let { uncertainty ->
                Text(uncertainty.ifBlank { stringResource(R.string.dynamic_card_uncertain) }, style = MaterialTheme.typography.bodySmall)
            }
            if (onRead != null || cardActions.isNotEmpty() || (onCheck != null && card.uncertainty != null)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (onRead != null) TextButton(onClick = {
                        val visibleSide = (card.body as? HaloCardBody.Flip)?.let { if (flipped) it.back else it.front }
                        val speech = visibleSide ?: card.speak
                        onRead(card, listOf(card.title, speech).filter(String::isNotBlank).distinct().joinToString(". "))
                    }, modifier = target) {
                        HaloIcon(HaloGlyph.SPEAK, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("Read aloud"))
                    }
                    if (onCheck != null && card.uncertainty != null) TextButton(onClick = { onCheck(card) }, modifier = target) {
                        Text(stringResource(R.string.dynamic_card_check))
                    }
                    if (cardActions.isNotEmpty()) Box {
                        val menuLabel = stringResource(R.string.dynamic_card_destinations)
                        IconButton(onClick = { actionMenu = true }, modifier = Modifier.size(48.dp)) {
                            HaloIcon(HaloGlyph.ADD_DEVICE, menuLabel, Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                            cardActions.forEach { action -> DropdownMenuItem(text = { Text(action.label) }, onClick = {
                                actionMenu = false
                                action.run(card)
                            }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBody(card: HaloCardData, still: Boolean, expanded: Boolean, flipped: Boolean, onFlip: () -> Unit) {
    val pics = card.size == HaloCardSize.PICTURES
    val limit = if (expanded || card.size == HaloCardSize.FULL) 12 else if (pics) 1 else 3
    val style = if (pics) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    when (val body = card.body) {
        is HaloCardBody.Summary -> {
            if (card.type == HaloCardType.FACT) FactNumber(card.id, body.value, still || expanded)
            else Text(body.value, style = if (card.type == HaloCardType.WORD) MaterialTheme.typography.headlineSmall else style)
            if (!pics || expanded) body.note?.let { Text(it, style = style, maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis) }
        }
        is HaloCardBody.Items -> CardItems(card.id, card.type, body.rows, limit, style, still)
        is HaloCardBody.Compare -> body.rows.take(limit).forEach { row ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    body.columns.zip(row).forEach { (heading, value) ->
                        Text(heading, style = MaterialTheme.typography.labelSmall)
                        Text(value, style = style)
                    }
                }
            }
        }
        is HaloCardBody.Pictures -> body.items.take(if (expanded) 6 else 1).forEach { CardPicture(it) }
        is HaloCardBody.Flip -> {
            val rotation = remember(card.id) { Animatable(if (flipped) 180f else 0f) }
            LaunchedEffect(flipped, still) {
                val target = if (flipped) 180f else 0f
                if (still) rotation.snapTo(target) else rotation.animateTo(target, tween(420))
            }
            val side: @Composable (Boolean) -> Unit = { reverse ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (reverse) body.back else body.front, style = style)
                    Text(stringResource(R.string.dynamic_card_flip), style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedButton(onFlip, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                if (still) Crossfade(flipped, animationSpec = tween(120), label = "card side", content = side)
                else Box(Modifier.graphicsLayer {
                    rotationY = if (rotation.value < 90f) rotation.value else rotation.value - 180f
                    cameraDistance = 14 * density
                }) { side(rotation.value >= 90f) }
            }
        }
        is HaloCardBody.Chart -> CardChart(body.points)
        is HaloCardBody.Quiz -> HaloQuickCheck(card.id, body.options, still)
        is HaloCardBody.Labels -> {
            CardPicture(body.picture)
            var showing by rememberSaveable(card.id) { mutableStateOf(false) }
            TextButton(onClick = { showing = !showing }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (showing) R.string.dynamic_card_hide_labels else R.string.dynamic_card_show_labels))
            }
            if (showing) body.labels.take(limit).forEach { Text(it.text, style = style) }
        }
    }
}

@Composable
private fun CardItems(id: String, type: HaloCardType, rows: List<HaloCardRow>, limit: Int,
    style: androidx.compose.ui.text.TextStyle, still: Boolean) {
    val worked = type == HaloCardType.WORKED_EXAMPLE
    val tickable = type in setOf(HaloCardType.STEPS, HaloCardType.CHECKLIST, HaloCardType.PLAN)
    var revealed by rememberSaveable(id) { mutableIntStateOf(1) }
    var toggled by rememberSaveable(id) { mutableIntStateOf(0) }
    val count = if (worked) revealed else limit
    rows.take(count).forEachIndexed { index, row ->
        val bit = 1 shl index
        val checked = row.checked != (toggled and bit != 0)
        val fill by animateColorAsState(if (checked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            animationSpec = tween(if (still) 0 else 240), label = "step completion")
        val change = { toggled = toggled xor bit }
        Surface(shape = RoundedCornerShape(12.dp), color = fill) {
            Row(Modifier.fillMaxWidth().then(if (tickable) Modifier.heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { change() }) else Modifier).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tickable) Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(40.dp))
                else Text(row.label ?: "${index + 1}", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium)
                Text(row.text, style = style)
            }
        }
    }
    if (worked && revealed < rows.size) OutlinedButton({ revealed++ }, Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.dynamic_card_next_step))
    }
}

@Composable
internal fun HaloQuickCheck(id: String, options: List<HaloCardChoice>, still: Boolean) {
    var answer by rememberSaveable(id) { mutableIntStateOf(-1) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < .4f
    val mint = brandAccent(dark, HaloAccent.MINT)
    val lilac = brandAccent(dark, HaloAccent.LILAC)
    options.forEachIndexed { index, option ->
        val selected = answer == index
        val right = selected && option.correct
        val fill by animateColorAsState(if (right) mint.fill else MaterialTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(if (still) 0 else 240), label = "answer feedback")
        OutlinedButton(onClick = { answer = index }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = fill, contentColor = if (right) mint.onFill else MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected && !right) lilac.fill else MaterialTheme.colorScheme.outlineVariant)) {
            Text(option.text)
        }
    }
    options.getOrNull(answer)?.let { option ->
        Text(if (option.correct) option.why.orEmpty().ifBlank { stringResource(R.string.dynamic_card_right) }
            else stringResource(R.string.dynamic_card_try_again),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FactNumber(id: String, value: String, still: Boolean) {
    val initial = remember(value) { Regex("^\\d{1,6}(?=\\s|$)").find(value)?.value }
    var played by rememberSaveable(id) { mutableStateOf(false) }
    val counter = remember(id) { Animatable(0f) }
    val goal = initial?.toIntOrNull()
    LaunchedEffect(id, still, goal) {
        if (!played && !still && goal != null) counter.animateTo(goal.toFloat(), tween(360))
        played = true
    }
    val visible = if (initial != null && goal != null && !played && !still)
        value.replaceRange(0, initial.length, counter.value.toInt().toString()) else value
    Text(visible, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun CardChart(points: List<Pair<String, Double>>) {
    val tint = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val max = points.maxOf { it.second }.coerceAtLeast(0.0)
    val min = points.minOf { it.second }.coerceAtMost(0.0)
    val range = (max - min).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val width = size.width / points.size
        val baseline = (max / range).toFloat() * size.height
        drawLine(track, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
        points.forEachIndexed { index, (_, value) ->
            val center = width * (index + .5f)
            drawLine(tint, Offset(center, baseline), Offset(center, ((max - value) / range).toFloat() * size.height), width * .56f)
        }
    }
    points.forEach { (label, value) -> Text("$label · $value", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun CardPicture(picture: HaloCardPicture) {
    var unavailable by remember(picture.assetId) { mutableStateOf(false) }
    val file by produceState<Pair<Any, String>?>(null, picture) {
        try {
            val graph = ManualDependencyGraph
            val ref = picture.studyAsset
            if (ref != null) {
                val current = graph.studySourceResolver.resolve(picture.assetId, "") as? com.halo.app.study.ResolvedStudySource.Visual
                value = if (current != null && current.revision == picture.version && current.asset == ref) {
                    graph.mediaAssetResolver.open(ref).bytes to current.title
                } else null
            } else {
                val id = WorkspaceObjectId(picture.assetId)
                val owner = graph.workspaceRepository.get(id)
                value = if (owner?.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE)
                    graph.workspaceFileRepository.getFile(id)?.takeIf {
                        it.mimeType in setOf("image/png", "image/jpeg", "image/webp", "image/gif") &&
                            it.uri.scheme in setOf("content", "file") &&
                            it.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL) &&
                            (picture.version == null || it.version == picture.version)
                    }?.let { it.uri to it.displayName } else null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
        unavailable = value == null
    }
    val failure = stringResource(R.string.dynamic_card_picture_unavailable)
    file?.let { (model, credit) ->
        SubcomposeAsyncImage(model = model, contentDescription = picture.caption ?: credit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp).clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Fit, error = { Text(failure) })
        Text(stringResource(R.string.dynamic_card_image_source, credit), style = MaterialTheme.typography.labelSmall)
    }
    if (unavailable) Text(failure, style = MaterialTheme.typography.bodySmall)
    picture.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun cardTypeLabel(type: HaloCardType): String = stringResource(when (type) {
    HaloCardType.FACT -> R.string.dynamic_type_fact
    HaloCardType.STEPS -> R.string.dynamic_type_steps
    HaloCardType.COMPARE -> R.string.dynamic_type_compare
    HaloCardType.TIMELINE -> R.string.dynamic_type_timeline
    HaloCardType.PLACE -> R.string.dynamic_type_place
    HaloCardType.PICTURES -> R.string.dynamic_type_pictures
    HaloCardType.QUICK_CHECK -> R.string.dynamic_type_quick_check
    HaloCardType.FLIP -> R.string.dynamic_type_flip
    HaloCardType.CHART -> R.string.dynamic_type_chart
    HaloCardType.CHECKLIST -> R.string.dynamic_type_checklist
    HaloCardType.WORD -> R.string.dynamic_type_word
    HaloCardType.PLAN -> R.string.dynamic_type_plan
    HaloCardType.LABEL_IT -> R.string.dynamic_type_label_it
    HaloCardType.EXPLAIN_BACK -> R.string.dynamic_type_explain_back
    HaloCardType.WORKED_EXAMPLE -> R.string.dynamic_type_worked_example
    HaloCardType.HELP -> R.string.dynamic_type_help
})
