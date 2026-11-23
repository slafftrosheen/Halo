package com.halo.app.ui.adaptive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.workspace.AdaptivePageStore
import com.halo.app.data.workspace.AdaptiveWidgetEntry
import com.halo.app.data.workspace.AdaptiveWidgetExport
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.nativeactions.AdaptiveWidgetReview
import com.halo.app.nativeactions.EverydayWidgetPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves the exact saved page before showing the same launcher review used by the editor. */
@Composable
internal fun AdaptiveWidgetReviewEntry(request: AdaptiveWidgetEntry, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val result by produceState<Result<AdaptiveWidgetExport>?>(null, context, request) {
        value = try {
            val plan = withContext(Dispatchers.IO) {
                Graph.kidsPreferences.requireAdultAction(request.familyRevision)
                val page = checkNotNull(AdaptivePageStore(context).pages().find { it.ref == request.page })
                AdaptiveWidgetExport.from(page.layout)
            }
            Graph.kidsPreferences.requireAdultAction(request.familyRevision)
            Result.success(plan)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
    val plan = result?.getOrNull()
    if (plan != null && plan.available.isNotEmpty()) {
        EverydayWidgetPicker(adaptiveReview = AdaptiveWidgetReview(plan, plan.unsupported.map { it.localizedLabel(resources) }),
            onDismiss = onBack)
    } else Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        TextButton(onClick = onBack) { Text(uiText("Back")) }
        if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else Text(stringResource(if (plan == null) R.string.adaptive_widget_changed else R.string.adaptive_widget_no_sections),
            color = if (plan == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}
