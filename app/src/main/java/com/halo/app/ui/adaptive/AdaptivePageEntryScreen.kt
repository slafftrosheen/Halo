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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.workspace.AdaptivePageDestination
import com.halo.app.data.workspace.AdaptivePageEntry
import com.halo.app.data.workspace.AdaptivePageStore
import com.halo.app.data.workspace.AdaptiveRoutineExport
import com.halo.app.data.workspace.AdaptiveWidgetExport
import com.halo.app.data.workspace.SavedAdaptivePage
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.nativeactions.AdaptiveWidgetReview
import com.halo.app.nativeactions.EverydayWidgetPicker
import com.halo.app.ui.screens.home.HaloTodayScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AdaptivePageEntryScreen(request: AdaptivePageEntry, open: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val result by produceState<Result<SavedAdaptivePage>?>(null, context, request) {
        value = try {
            val page = withContext(Dispatchers.IO) {
                Graph.kidsPreferences.requireAdultAction(request.familyRevision)
                AdaptivePageStore(context).requirePage(request.page)
            }
            Graph.kidsPreferences.requireAdultAction(request.familyRevision)
            Result.success(page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    val page = result?.getOrNull()
    if (page != null) {
        when (request.destination) {
            AdaptivePageDestination.OPEN -> {
                HaloTodayScreen(open, savedPages = true, onBack = onBack, initialPage = page)
                return
            }
            AdaptivePageDestination.ROUTINE -> {
                val plan = remember(page.layout) { AdaptiveRoutineExport.from(page.layout) }
                AdaptiveRoutineReview(plan, page.title, request.familyRevision, open, onBack)
                return
            }
            AdaptivePageDestination.WIDGET -> {
                val plan = remember(page.layout) { AdaptiveWidgetExport.from(page.layout) }
                if (plan.available.isNotEmpty()) {
                    EverydayWidgetPicker(adaptiveReview = AdaptiveWidgetReview(plan, plan.unsupported.map { it.localizedLabel(resources) }),
                        onDismiss = onBack)
                    return
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        TextButton(onClick = onBack) { Text(uiText("Back")) }
        if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else Text(stringResource(if (page == null) R.string.adaptive_page_reopen else R.string.adaptive_widget_no_sections),
            color = if (page == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}
