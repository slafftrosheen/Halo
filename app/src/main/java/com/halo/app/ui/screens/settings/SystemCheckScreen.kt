package com.halo.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.icons.HaloIcon

@Composable
fun SystemCheckScreen(
    viewModel: SystemCheckViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        HaloScreenHeader(
            title = "System Check",
            subtitle = "Diagnostic hardware & runtime probe",
            navigation = onBack
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = viewModel::runSafeChecks,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Refresh")
            }

            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(uiState.summaryReport))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Copy Summary")
            }
        }

        Spacer(Modifier.height(20.dp))

    val assistantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.recordAssistantEnrollmentResult(result.resultCode)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        uiState.sections.forEach { section ->
            item {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp, end = 16.dp)
                )
            }
            items(section.items, key = { it.id }) { item ->
                SystemCheckCard(
                    item = item,
                    onAction = {
                        val action = when {
                            item.id == "assistant" -> SystemCheckAction.OpenAssistantSettings
                            item.id == "mic" -> SystemCheckAction.RunSttProbe
                            item.id == "ai_execution" -> SystemCheckAction.RunTextProbe
                            item.id == "agentic_execution" -> SystemCheckAction.RunAgenticProbe
                            item.id == "audio" -> SystemCheckAction.RunTtsProbe
                            item.id == "phone_alarm" -> SystemCheckAction.TestAlarm
                            item.id == "phone_app" -> SystemCheckAction.TestOpenApp
                            item.id == "phone_volume" -> SystemCheckAction.TestVolume
                            item.id.startsWith("remote_") -> SystemCheckAction.Refresh
                            else -> SystemCheckAction.Refresh
                        }
                        if (item.id.startsWith("remote_")) viewModel.refreshRemoteProviders()
                        if (action is SystemCheckAction.OpenAssistantSettings) {
                            viewModel.launchAssistantEnrollment { intent -> assistantLauncher.launch(intent) }
                        } else {
                            viewModel.handleAction(action)
                        }
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun SystemCheckCard(
    item: SystemCheckItem,
    onAction: () -> Unit
) {
    val statusColor = when (item.status) {
        CheckStatus.PASSED -> MaterialTheme.colorScheme.primary
        CheckStatus.FAILED -> MaterialTheme.colorScheme.error
        CheckStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
        CheckStatus.READY -> MaterialTheme.colorScheme.secondary
        CheckStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
        CheckStatus.BLOCKED -> MaterialTheme.colorScheme.error
        CheckStatus.UNSUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                HaloIcon(
                    glyph = item.glyph,
                    contentDescription = item.name,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.durationMs != null) {
                    Text(
                        text = "Completed in ${item.durationMs}ms",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.15f),
                onClick = onAction
            ) {
                Text(
                    text = if (item.id == "ai_execution" && item.status != CheckStatus.RUNNING) {
                        "RUN AI PROBE"
                    } else {
                        item.status.name
                    },
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
