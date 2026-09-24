package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloHeaderAction
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.runtime.cognition.routine.Routine
import com.halo.runtime.cognition.routine.RoutineId
import com.halo.runtime.cognition.routine.RoutineTrigger
import java.time.Instant

@Composable
fun RoutinesScreen(
    routines: List<Routine> = emptyList(),
    onToggleRoutine: (RoutineId, Boolean) -> Unit = { _, _ -> },
    onCreateRoutine: () -> Unit = {},
    onOpenRoutine: (RoutineId) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = HaloSpacing.screen)
    ) {
        Spacer(Modifier.height(16.dp))
        HaloScreenHeader(
            title = "Routines",
            subtitle = "Automate local tasks",
            primaryAction = HaloHeaderAction("New", onCreateRoutine)
        )
        Spacer(Modifier.height(16.dp))

        if (routines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No routines configured yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCreateRoutine) {
                        Text("Create routine")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(routines, key = { it.id.value }) { routine ->
                    RoutineRow(
                        routine = routine,
                        onToggle = { enabled -> onToggleRoutine(routine.id, enabled) },
                        onClick = { onOpenRoutine(routine.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineRow(
    routine: Routine,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = triggerSummary(routine.trigger) + "\nNext: ${routine.nextRunAt ?: "Not scheduled"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${routine.actions.size} action${if (routine.actions.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = routine.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

private fun triggerSummary(trigger: RoutineTrigger): String = when (trigger) {
    is RoutineTrigger.AtTime -> "At ${trigger.localTime}"
    is RoutineTrigger.Once -> "Once at ${trigger.at}"
    is RoutineTrigger.DeviceConnected -> "When device connects (${trigger.deviceId})"
    is RoutineTrigger.DeviceDisconnected -> "When device disconnects (${trigger.deviceId})"
    is RoutineTrigger.AppStarted -> "When ${trigger.appPackage} starts"
}
