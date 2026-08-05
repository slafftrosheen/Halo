package com.halo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.theme.*

@Composable
fun HaloCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = HaloCard,
        border = BorderStroke(1.dp, HaloDivider)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun HaloButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) HaloPrimary else HaloSurface,
            contentColor = if (isPrimary) HaloBackground else HaloTextPrimary
        ),
        border = if (!isPrimary) BorderStroke(1.dp, HaloDivider) else null
    ) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun HaloStatusChip(
    text: String,
    color: Color = HaloPrimary
) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun HaloTag(text: String) {
    Box(
        modifier = Modifier
            .background(HaloSurface, RoundedCornerShape(4.dp))
            .border(1.dp, HaloDivider, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = HaloTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun HaloDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = HaloDivider
    )
}

@Composable
fun HaloSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = HaloTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
fun HaloMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    HaloCard(modifier = modifier.fillMaxWidth()) {
        Text(label, color = HaloTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = HaloTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun HaloEngineCard(
    name: String,
    status: String,
    isReady: Boolean = true
) {
    HaloCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, color = HaloTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            HaloStatusChip(status, color = if (isReady) HaloPrimary else HaloDanger)
        }
    }
}

@Composable
fun HaloProviderCard(
    name: String,
    status: String,
    isOnline: Boolean = false
) {
    HaloCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, color = HaloTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(status, color = if (isOnline) HaloPrimary else HaloTextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun HaloTimelineItem(
    time: String,
    title: String,
    description: String,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(HaloSurface, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, color = HaloTextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(time, color = HaloTextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = HaloTextSecondary, fontSize = 14.sp)
        }
    }
}

// Legacy compatibility for screens that haven't been migrated yet
@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    HaloSection(title = title.uppercase()) {
        if (subtitle != null) {
            Text(text = subtitle, color = HaloTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = HaloTextSecondary, fontSize = 14.sp)
        Text(text = value, color = HaloTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun HaloDividerLine() {
    HaloDivider(modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
fun ConsoleSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HaloSurface, shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = HaloTextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun ConsoleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = HaloTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(value, color = HaloTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    }
}

