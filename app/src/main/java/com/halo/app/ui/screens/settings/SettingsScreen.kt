package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.HaloGlassDivider

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personal. Private. Powerful.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = { /* TODO: Search */ }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SettingsRow(
            icon = Icons.Default.Search, // Replace with specific AI Icon
            iconTint = Color(0xFF4EA8FF),
            title = "AI Providers",
            subtitle = uiState.provider,
            onClick = { viewModel.openIntelligence() }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with specific Speech Icon
            iconTint = Color(0xFF9C27B0),
            title = "Speech",
            subtitle = uiState.voiceStatus,
            onClick = { viewModel.openVoice() }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))

        SettingsRow(
            icon = Icons.Default.Search, // Replace with Memory Icon
            iconTint = Color(0xFFFF9100),
            title = "Memory",
            subtitle = "Manage what Halo remembers",
            onClick = { /* TODO */ }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with Privacy Icon
            iconTint = Color(0xFF00C853),
            title = "Privacy",
            subtitle = "Your data stays with you",
            onClick = { /* TODO */ }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with Appearance Icon
            iconTint = Color(0xFF4EA8FF),
            title = "Appearance",
            subtitle = "Light / Dark / System",
            onClick = { /* TODO */ }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with Devices Icon
            iconTint = Color(0xFF4EA8FF),
            title = "Devices",
            subtitle = uiState.deviceName,
            onClick = { viewModel.openDevices() }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with Devices Icon
            iconTint = Color(0xFF4EA8FF),
            title = "Permissions",
            subtitle = "Manage Android system access",
            onClick = { viewModel.openPermissions() }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with Notifications Icon
            iconTint = Color(0xFFFF4081),
            title = "Notifications",
            subtitle = "Events, Alerts, Quiet hours",
            onClick = { /* TODO */ }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with Storage Icon
            iconTint = Color(0xFFFFD600),
            title = "Storage",
            subtitle = "Files, Cache, Offline data",
            onClick = { /* TODO */ }
        )
        HaloGlassDivider(modifier = Modifier.padding(start = 56.dp))
        
        SettingsRow(
            icon = Icons.Default.Search, // Replace with About Icon
            iconTint = Color(0xFF4EA8FF),
            title = "About",
            subtitle = "Version ${uiState.version}",
            onClick = { /* TODO */ }
        )

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.padding(10.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}
