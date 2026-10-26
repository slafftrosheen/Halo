package com.halo.app.ui.presence

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Keep the Activity in the base-context chain; only Material-owned strings use app resources. */
internal class MaterialLocaleContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
    override fun getAssets(): AssetManager = localized.assets
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalizedMaterialBottomSheet(onDismissRequest: () -> Unit, sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit) {
    val activityContext = LocalContext.current
    val resources = LocalResources.current
    val parentDensity = LocalDensity.current
    val parentLayoutDirection = LocalLayoutDirection.current
    val materialContext = remember(activityContext, resources) { MaterialLocaleContext(activityContext, resources) }
    val parentComposition = rememberCompositionContext()
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val currentSheetState by rememberUpdatedState(sheetState)
    val currentContent by rememberUpdatedState(content)
    val currentDensity by rememberUpdatedState(parentDensity)
    val currentLayoutDirection by rememberUpdatedState(parentLayoutDirection)
    // Material creates its dialog from LocalView.context, not LocalContext. An actual attached
    // host preserves lifecycle/saved-state ancestry and secure-window lookup; a proxy View would not.
    key(materialContext) {
        AndroidView(modifier = Modifier.size(0.dp), factory = {
            ComposeView(materialContext).apply {
                setParentCompositionContext(parentComposition)
                setContent {
                    CompositionLocalProvider(LocalDensity provides currentDensity,
                        LocalLayoutDirection provides currentLayoutDirection) {
                        ModalBottomSheet(onDismissRequest = { currentDismiss() }, sheetState = currentSheetState) {
                            CompositionLocalProvider(LocalContext provides activityContext,
                                LocalDensity provides currentDensity, LocalLayoutDirection provides currentLayoutDirection) { currentContent() }
                        }
                    }
                }
            }
        }, onRelease = { it.disposeComposition() })
    }
}
