package com.halo.app.ui.everyday

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.collectors.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.ui.components.HaloReadAloud
import com.halo.app.ui.toolkit.HaloAlbumIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.BreakIterator
import java.util.Locale

/** Article views only decode assets already admitted by the selected source. */
@Composable
internal fun SavedArticleBody(state: CollectorState, article: ArticleFact, time: String?, kids: Boolean) {
    val picturesVisible by Graph.articlePictureSettings.visible.collectAsStateWithLifecycle()
    val bodyState by produceState<Pair<String?, ArticleContent?>?>(null, article.contentKey) {
        value = article.contentKey to withContext(Dispatchers.IO) { Graph.articleContentStore.read(article.contentKey) }
    }
    val body = bodyState?.takeIf { it.first == article.contentKey }?.second
    val text = body?.text ?: article.text
    val pictures = article.gallery(picturesVisible, kids)
    val locale = LocalConfiguration.current.locales[0]
    val minutes = if (text.isBlank()) null else (text.split(Regex("\\s+")).size + 219) / 220
    val summary = remember(article.text, text, article.title, locale) {
        articleExcerpt(article.text.ifBlank { text }, article.title, locale)
    }
    var grid by remember(article.id, article.contentKey) { mutableStateOf(false) }
    var viewing by remember(article.id, article.contentKey) { mutableStateOf<Int?>(null) }
    var slideshow by remember(article.id, article.contentKey) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(listOfNotNull(article.publisher, time, minutes?.let { uiText("%s min read", it) }).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge)
        pictures.firstOrNull()?.let { lead ->
            Box(Modifier.fillMaxWidth().aspectRatio(1.75f).clip(MaterialTheme.shapes.large).clickable { viewing = 0 }) {
                ArticleThumbnail(lead, Modifier.fillMaxSize(), large = true)
                Surface(Modifier.align(Alignment.BottomEnd).padding(12.dp), color = Color.Black.copy(alpha = .75f),
                    contentColor = Color.White, shape = MaterialTheme.shapes.small) {
                    Text("1 / ${pictures.size}", Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        Text(article.title, style = MaterialTheme.typography.headlineMedium)
        if (summary.isNotBlank()) Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiText("In short · by halo"), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (pictures.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Pictures from the article"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton({ grid = true }) { Text(uiText("See all %s", pictures.size)) }
            }
            ArticleThumbnailStrip(pictures, onClick = { viewing = it })
        }
        if ((body?.status ?: article.contentStatus) != "PAGE_CAPTURED") {
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_article_partial),
                style = MaterialTheme.typography.bodySmall)
        }
        Text(text.ifBlank { uiText("The publisher supplied a title only. Open the publisher to read more.") })
    }
    if (pictures.isNotEmpty()) {
        val opened = viewing?.takeIf { it in pictures.indices }
        if (opened != null) ArticleViewer(state, article, pictures, opened, kids, slideshow) { viewing = null; slideshow = false }
        else if (grid) ArticlePictureGrid(pictures, article.publisher, onPicture = { viewing = it },
            onPlay = { slideshow = true; viewing = 0 }, onClose = { grid = false })
    }
    LaunchedEffect(picturesVisible) { if (!picturesVisible) { grid = false; viewing = null; slideshow = false } }
}

@Composable
internal fun ArticleSourcePreview(article: ArticleFact, picturesVisible: Boolean, kids: Boolean) {
    val images = article.gallery(picturesVisible, kids)
    images.firstOrNull()?.let { lead ->
        ArticleHeadlinePicture(lead)
        ArticleHeadlineStrip(images.drop(1))
    }
}

@Composable
internal fun ArticleHeadlinePicture(lead: ArticleImage) {
    ArticleThumbnail(lead, Modifier.fillMaxWidth().aspectRatio(2f).clip(MaterialTheme.shapes.medium), large = true)
}

@Composable
internal fun ArticleHeadlineStrip(pictures: List<ArticleImage>) {
    if (pictures.isNotEmpty()) ArticleThumbnailStrip(pictures)
}

internal fun ArticleFact.gallery(visible: Boolean, kids: Boolean) = if (!visible) emptyList() else images.filter {
    it.fromArticle && it.assetKey != null && (!kids || it.sensitivity == ArticleImageSensitivity.CLEAR)
}

private val ArticleImage.requiresReveal get() = sensitivity == ArticleImageSensitivity.SENSITIVE

@Composable
private fun ArticleThumbnailStrip(pictures: List<ArticleImage>, onClick: ((Int) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        pictures.take(4).forEachIndexed { index, picture ->
            val size = Modifier.weight(1f).aspectRatio(1f).heightIn(min = 48.dp, max = 90.dp).clip(MaterialTheme.shapes.small)
            ArticleThumbnail(picture, if (onClick == null) size else size.clickable { onClick(index) })
        }
        repeat(4 - pictures.size.coerceAtMost(4)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ArticleThumbnail(picture: ArticleImage, modifier: Modifier, large: Boolean = false) {
    val guarded = picture.requiresReveal
    val load = rememberArticleBitmap(picture, if (guarded) 48 else if (large) 640 else 180)
    val bitmap = load?.bitmap
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bitmap?.takeIf { !guarded || android.os.Build.VERSION.SDK_INT >= 31 }?.let {
            Image(it.asImageBitmap(), if (guarded) null else picture.caption.ifBlank { uiText("Article picture") },
                Modifier.fillMaxSize().then(if (guarded) Modifier.blur(24.dp) else Modifier), contentScale = ContentScale.Crop)
        }
        when {
            guarded -> {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .65f)))
                Text(uiText("Hidden"), Modifier.padding(4.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            load != null && bitmap == null -> Text(uiText("Picture unavailable"), Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class ArticleBitmapLoad(val key: String?, val edge: Int, val bitmap: Bitmap?)

@Composable
private fun rememberArticleBitmap(picture: ArticleImage, edge: Int): ArticleBitmapLoad? {
    val result by produceState<ArticleBitmapLoad?>(null, picture.assetKey, edge) {
        val bitmap = withContext(Dispatchers.IO) {
            val file = Graph.articleContentStore.imageFile(picture.assetKey) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                val largest = maxOf(bounds.outWidth, bounds.outHeight)
                while (largest / inSampleSize > edge) inSampleSize *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
        value = ArticleBitmapLoad(picture.assetKey, edge, bitmap)
    }
    return result?.takeIf { it.key == picture.assetKey && it.edge == edge }
}

@Composable
private fun ArticlePictureGrid(pictures: List<ArticleImage>, publisher: String, onPicture: (Int) -> Unit,
    onPlay: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClose) { Text(uiText("Back")) }
                Text(uiText("All pictures"), style = MaterialTheme.typography.headlineMedium)
                Text(publisher, style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(pictures, key = { _, picture -> picture.url }) { index, picture ->
                        ArticleThumbnail(picture, Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.small).clickable { onPicture(index) })
                    }
                }
                if (pictures.size > 1) FilledTonalButton(onPlay) { Text(uiText("Slideshow")) }
            }
        }
    }
}

@Composable
private fun ArticleViewer(state: CollectorState, article: ArticleFact, pictures: List<ArticleImage>, initial: Int,
    kids: Boolean, autoAdvance: Boolean, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = initial) { pictures.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picture = pictures[pager.currentPage]
    var zoomed by remember(pager.currentPage) { mutableStateOf(false) }
    var exposed by remember(pager.currentPage) { mutableStateOf(false) }
    var notice by remember(pager.currentPage) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var reviewShare by remember(pager.currentPage) { mutableStateOf(false) }
    var playing by remember { mutableStateOf(autoAdvance) }
    val revealed = exposed || !picture.requiresReveal
    val credit = pictureCredit(picture, article.publisher)
    LaunchedEffect(playing, pager.currentPage, zoomed, reviewShare) {
        if (!playing) return@LaunchedEffect
        val next = pager.currentPage + 1
        if (zoomed || reviewShare || picture.requiresReveal || pictures.getOrNull(next) == null) {
            playing = false
            return@LaunchedEffect
        }
        delay(4_000)
        pager.animateScrollToPage(next)
    }
    val canShare = revealed && !kids && article.url != null
    val saveAction: (() -> Unit)? = if (canShare && picture.canSave) ({
        saving = true
        scope.launch {
            try {
                keepArticlePicture(context, state, article, picture, credit)
                notice = "Picture kept in Album."
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { notice = "This picture could not be kept. Open the current article and try again." }
            finally { saving = false }
        }
    }) else null
    ArticleViewerWindow(
        pager, pictures, picture, credit, notice, kids, revealed, exposed, zoomed, playing, saving,
        onClose = onClose, onZoom = { zoomed = it }, onExpose = { exposed = true }, onPause = { playing = false },
        onSave = saveAction, onReviewShare = if (canShare) ({ reviewShare = true }) else null,
    )
    if (reviewShare && revealed && !kids) HaloAlertDialog(onDismissRequest = { reviewShare = false },
        title = { Text(uiText("Share this article link?")) }, text = { Text("${article.title}\n$credit\n${article.url.orEmpty()}") },
        confirmButton = { TextButton({
            reviewShare = false
            scope.launch {
                try {
                    shareArticleLink(context, state, article, credit)
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (_: Exception) { notice = "Could not open sharing for this article." }
            }
        }) { Text(uiText("Choose where to share")) } },
        dismissButton = { TextButton({ reviewShare = false }) { Text(uiText("Cancel")) } })
}

@Composable
private fun ArticleViewerWindow(
    pager: PagerState, pictures: List<ArticleImage>, picture: ArticleImage, credit: String, notice: String?,
    kids: Boolean, revealed: Boolean, exposed: Boolean, zoomed: Boolean, playing: Boolean, saving: Boolean,
    onClose: () -> Unit, onZoom: (Boolean) -> Unit, onExpose: () -> Unit, onPause: () -> Unit,
    onSave: (() -> Unit)?, onReviewShare: (() -> Unit)?,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClose, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text(uiText("Close")) }
                    Text(uiText("%1\$s of %2\$s", pager.currentPage + 1, pictures.size), Modifier.weight(1f))
                    if (onSave != null) {
                        TextButton(onSave, enabled = !saving, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Text(uiText("Save"))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                    val start = (pager.currentPage - 3).coerceIn(0, (pictures.size - 7).coerceAtLeast(0))
                    (start until minOf(start + 7, pictures.size)).forEach { index ->
                        Box(Modifier.size(width = if (index == pager.currentPage) 14.dp else 5.dp, height = 5.dp)
                            .clip(CircleShape).background(if (index == pager.currentPage) Color.White else Color.DarkGray))
                    }
                }
                HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), userScrollEnabled = !zoomed) { index ->
                    if (!pictures[index].requiresReveal || index == pager.currentPage && exposed) {
                        ZoomedArticlePicture(pictures[index]) { if (index == pager.currentPage) onZoom(it) }
                    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ArticleThumbnail(pictures[index], Modifier.fillMaxSize())
                        if (!kids) Button(onClick = onExpose) { Text(uiText("Show")) }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    if (revealed && picture.caption.isNotBlank()) Text(picture.caption,
                        Modifier.heightIn(max = 86.dp).verticalScroll(rememberScrollState()), color = Color.White, fontSize = 15.sp)
                    Text(credit, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                    if (revealed && picture.caption.isNotBlank()) HaloReadAloud("${picture.caption}\n$credit", compact = true)
                    if (onReviewShare != null) TextButton(onReviewShare,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text(uiText("Share article link")) }
                    if (playing) TextButton(onPause, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(uiText("Pause slideshow"))
                    }
                    notice?.let { Text(uiText(it), color = Color.White) }
                }
            }
        }
    }
}

private suspend fun validateArticlePictureSource(state: CollectorState, article: ArticleFact, adult: Long) {
    Graph.kidsPreferences.requireAdultAction(adult)
    check(Graph.articlePictureSettings.visible.value)
    val live = requireNotNull(Graph.collectorRepository.state(state.source.id))
    check(live.source.version == state.source.version && live.snapshot?.contentRevision == state.snapshot?.contentRevision)
    check((live.snapshot?.payload as? CollectorPayload.Articles)?.items?.any { it == article } == true)
}

private suspend fun keepArticlePicture(context: Context, state: CollectorState, article: ArticleFact,
    picture: ArticleImage, credit: String) {
    val adult = Graph.kidsPreferences.adultActionRevision()
    val revision = requireNotNull(state.snapshot?.contentRevision)
    val link = CollectorUrlPolicy.validate(requireNotNull(article.url)).toString()
    val key = requireNotNull(picture.assetKey)
    val bytes = readCachedArticleBytes(key)
    EverydayWriteAuthority.run({ Graph.kidsPreferences.requireAdultAction(adult) }) {
        HaloAlbumIndex(context.applicationContext).keepArticlePicture(article.title.take(140),
            requireNotNull(picture.mimeType), bytes, state.source.id, revision.toString(), key,
            credit, link, requireNotNull(picture.license)) { validateArticlePictureSource(state, article, adult) }
    }
}

private suspend fun readCachedArticleBytes(key: String): ByteArray = withContext(Dispatchers.IO) {
    val file = requireNotNull(Graph.articleContentStore.imageFile(key))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
        bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000)
    file.readBytes()
}

private suspend fun shareArticleLink(context: Context, state: CollectorState, article: ArticleFact, credit: String) {
    val adult = Graph.kidsPreferences.adultActionRevision()
    validateArticlePictureSource(state, article, adult)
    val link = CollectorUrlPolicy.validate(requireNotNull(article.url)).toString()
    Graph.kidsPreferences.requireAdultAction(adult)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "${article.title}\n$credit\n$link")
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
private fun ZoomedArticlePicture(picture: ArticleImage, onZoom: (Boolean) -> Unit) {
    val load = rememberArticleBitmap(picture, 1600)
    var scale by remember(picture.url) { mutableFloatStateOf(1f) }
    var offset by remember(picture.url) { mutableStateOf(Offset.Zero) }
    val zoomCallback by rememberUpdatedState(onZoom)
    Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).pointerInput(picture.url) {
        detectTapGestures(onDoubleTap = {
            scale = if (scale > 1f) 1f else 2f
            offset = Offset.Zero
            zoomCallback(scale > 1f)
        })
    }.pointerInput(picture.url) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                    scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                    val bound = Offset(size.width * (scale - 1f) / 2, size.height * (scale - 1f) / 2)
                    val target = offset + event.calculatePan()
                    offset = Offset(target.x.coerceIn(-bound.x, bound.x), target.y.coerceIn(-bound.y, bound.y))
                    zoomCallback(scale > 1f)
                    event.changes.filter { it.position != it.previousPosition }.forEach { it.consume() }
                }
            }
        }
    }, contentAlignment = Alignment.Center) {
        val bitmap = load?.bitmap
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), picture.caption.ifBlank { uiText("Article picture") },
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                }, contentScale = ContentScale.Fit)
        } else if (load == null) CircularProgressIndicator()
        else Text(uiText("Picture unavailable"), color = Color.White)
    }
}

@Composable
private fun pictureCredit(image: ArticleImage, publisher: String): String {
    val kind = uiText(when (image.kind) {
        ArticleImageKind.PHOTO -> "Photo"
        ArticleImageKind.ILLUSTRATION -> "Illustration"
        ArticleImageKind.IMAGE -> "Picture"
    })
    return if (image.credit != null) uiText("%1\$s: %2\$s / %3\$s · from the article", kind, image.credit, publisher)
        else uiText("%1\$s: %2\$s · from the article · creator uncredited", kind, publisher)
}

private fun articleExcerpt(source: String, title: String, locale: Locale): String {
    val cleaned = source.replace(Regex("\\s+"), " ").trim().removePrefix(title).trim()
    if (cleaned.isEmpty()) return ""
    val sentences = BreakIterator.getSentenceInstance(locale).apply { setText(cleaned) }
    val first = sentences.next()
    val second = if (first != BreakIterator.DONE) sentences.next() else BreakIterator.DONE
    val end = listOf(second, first).firstOrNull { it in 1..320 }
    return if (end != null) cleaned.take(end).trim() else cleaned.take(317).substringBeforeLast(' ', cleaned.take(317)) + "…"
}
