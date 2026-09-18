package com.halo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.presence.HaloPresence
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloTheme
import com.halo.app.ui.theme.HaloWarning
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceRow
import com.halo.app.ui.workspace.HaloWorkspaceSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class HaloVisualLanguageSnapshotTest {

    @Test
    fun lightWorkspaceContract() {
        captureRoboImage {
            HaloTheme(darkTheme = false) {
                VisualLanguageFixture()
            }
        }
    }

    @Test
    fun darkWorkspaceContract() {
        captureRoboImage {
            HaloTheme(darkTheme = true) {
                VisualLanguageFixture()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun VisualLanguageFixture() {
    Box(
        modifier = Modifier
            .width(390.dp)
            .height(820.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "halo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "visual language contract",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HaloSignalGlyph(
                    kind = HaloSignalKind.HALO,
                    size = 38.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                HaloPresence(
                    state = HaloRuntimeState.Reasoning,
                    size = 132.dp,
                    reducedMotion = true
                )
            }

            HaloWorkspaceCard(accent = HaloPastelMint) {
                Text(
                    text = "Thinking with local context",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Presence carries runtime state; content remains the primary surface.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HaloWorkspaceSection(title = "Workspace") {
                HaloWorkspaceRow(
                    title = "Notebook",
                    subtitle = "Persistent notes and working context",
                    trailingText = "12"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HaloWorkspaceRow(
                    title = "HeyCyan glasses",
                    subtitle = "Connected device",
                    trailingText = "Ready",
                    selected = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HaloWorkspaceButton(
                    text = "Primary action",
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
                HaloWorkspaceButton(
                    text = "Secondary",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    primary = false
                )
            }

            HaloWorkspaceCard(accent = HaloWarning) {
                Text(
                    text = "Forward blocked",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HaloWarning
                )
                Text(
                    text = "Semantic warning color is independent from pastel object identity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.size(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    HaloSignalKind.NOTE to "Note",
                    HaloSignalKind.DEVICE to "Device",
                    HaloSignalKind.FILE to "File",
                    HaloSignalKind.ERROR to "Error"
                ).forEach { (kind, label) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HaloSignalGlyph(kind = kind, size = 34.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HaloPastelBlue.copy(alpha = 0.28f))
            )

            Text(
                text = "Danger stays semantic · ${MaterialTheme.colorScheme.error}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
