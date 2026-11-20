package com.halo.app.ui.everyday

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.ArticleContent
import com.halo.app.everyday.collectors.ArticleFact
import com.halo.app.language.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only Halo's already saved assets are decoded. Opening an article never starts a network request. */
@Composable
internal fun SavedArticleBody(article: ArticleFact) {
    val body by produceState<ArticleContent?>(null, article.contentKey) {
        value = withContext(Dispatchers.IO) { Graph.articleContentStore.read(article.contentKey) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val status = body?.status ?: article.contentStatus
        Text(uiText(if (status == "PAGE_CAPTURED") "Saved article text" else "Some article content may be missing. The saved text is shown below."),
            style = MaterialTheme.typography.labelLarge)
        body?.retrievedAt?.let { Text(uiText("Retrieved %1\$s", it), style = MaterialTheme.typography.bodySmall) }
        Text((body?.text ?: article.text).ifBlank { uiText("The publisher supplied a title only. Open the publisher to read more.") })
        (body?.images ?: article.images).forEach { image ->
            val bitmap by produceState<android.graphics.Bitmap?>(null, image.assetKey) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = Graph.articleContentStore.imageFile(image.assetKey) ?: return@runCatching null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 1
                            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
                        }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                    }.getOrNull()
                }
            }
            bitmap?.let { Image(it.asImageBitmap(), image.caption.ifBlank { article.title }, Modifier.fillMaxWidth().heightIn(max = 480.dp)) }
                ?: Text(uiText("This image has not been saved."), style = MaterialTheme.typography.bodySmall)
            if (image.caption.isNotBlank()) Text(image.caption, style = MaterialTheme.typography.bodySmall)
        }
        if ((body?.omittedImages ?: 0) > 0 || (body?.images ?: article.images).size >= com.halo.app.everyday.collectors.ArticleExtractor.MAX_IMAGES)
            Text(uiText("Up to eight images are saved per article. Additional images may be available from the publisher."), style = MaterialTheme.typography.bodySmall)
    }
}
