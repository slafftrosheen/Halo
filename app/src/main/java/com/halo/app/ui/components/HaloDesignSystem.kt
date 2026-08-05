package com.halo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.HaloBase
import com.halo.app.ui.theme.HaloDivider
import com.halo.app.ui.theme.HaloSurface
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = HaloTextPrimary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = HaloTextSecondary
            )
        }
    }
}

@Composable
fun StateTile(title: String, value: String, accentText: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HaloSurface, RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = HaloTextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (accentText) HaloAccent else HaloTextPrimary
        )
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = HaloTextPrimary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
    }
}

@Composable
fun CapabilityChip(text: String, isAccent: Boolean = false) {
    Box(
        modifier = Modifier
            .background(if (isAccent) HaloAccent.copy(alpha = 0.1f) else HaloSurface, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isAccent) HaloAccent else HaloTextSecondary
        )
    }
}

@Composable
fun TimelineEntry(time: String, action: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(text = time, style = MaterialTheme.typography.labelMedium, color = HaloTextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = action, style = MaterialTheme.typography.bodyLarge, color = HaloTextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Divider(color = HaloDivider, thickness = 1.dp)
    }
}

@Composable
fun HaloDividerLine() {
    Divider(color = HaloDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
}
