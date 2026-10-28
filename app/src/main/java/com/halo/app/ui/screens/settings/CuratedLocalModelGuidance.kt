package com.halo.app.ui.screens.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ai.CuratedLocalCatalog
import com.halo.app.ai.CuratedLocalRole
import com.halo.app.ai.LocalModelDeviceFit
import com.halo.providers.litert.models.LiteRtCatalogModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LocalModelCapacity(
    val availMem: Long = -1,
    val totalMem: Long = 0,
    val freeStorage: Long = -1,
)

/** Read-only snapshot on IO. Missing measurements stay unknown; this starts no download. */
@Composable
internal fun rememberLocalModelCapacity(context: Context, revision: Any?): State<LocalModelCapacity> =
    produceState(LocalModelCapacity(), context.applicationContext, revision) {
        value = withContext(Dispatchers.IO) {
            val memory = try {
                ActivityManager.MemoryInfo().also {
                    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        ?: return@also
                    manager.getMemoryInfo(it)
                }
            } catch (_: Exception) { null }
            val space = try { context.noBackupFilesDir.usableSpace } catch (_: Exception) { -1L }
            LocalModelCapacity(memory?.availMem ?: -1, memory?.totalMem ?: 0, space)
        }
    }

@Composable
internal fun CuratedLocalModelGuidance(model: LiteRtCatalogModel, fit: LocalModelDeviceFit.Verdict) {
    val candidate = CuratedLocalCatalog.entry(model)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(if (candidate != null) R.string.curated_models_candidate else R.string.curated_models_owned),
            style = MaterialTheme.typography.labelMedium)
        candidate?.let {
            Text(stringResource(when (it.role) {
                CuratedLocalRole.INTENT_CANDIDATE -> R.string.curated_models_intent
                CuratedLocalRole.LOCAL_SEARCH_CANDIDATE -> R.string.curated_models_search
                CuratedLocalRole.CONVERSATION_CANDIDATE -> R.string.offline59_chat_candidate
            }), style = MaterialTheme.typography.bodySmall)
        }
        if (CuratedLocalCatalog.isEnglishFirst(model)) {
            Text(stringResource(R.string.curated_models_lfm), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(when (fit) {
            LocalModelDeviceFit.Verdict.ESTIMATED_FIT -> R.string.curated_models_fit_estimate
            LocalModelDeviceFit.Verdict.MEMORY_PRESSURE -> R.string.curated_models_fit_memory
            LocalModelDeviceFit.Verdict.STORAGE_SHORTAGE -> R.string.curated_models_fit_storage
            LocalModelDeviceFit.Verdict.UNKNOWN -> R.string.curated_models_fit_unknown
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
