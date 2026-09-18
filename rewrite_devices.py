import re
with open('C:/Users/alisi/Eco/Halo/app/src/main/java/com/halo/app/ui/screens/devices/DevicesScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

idx = content.find('@Composable\nfun DevicesScreen(')
if idx != -1:
    top = content[:idx]

    import_block = '''import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.ChevronRight
'''
    top = top.replace('import androidx.compose.ui.unit.dp', 'import androidx.compose.ui.unit.dp\n' + import_block)

    bottom = """@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
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
                    text = "Devices",
                    fontSize = 28.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect. Configure. Control.",
                    fontSize = 14.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                androidx.compose.material3.IconButton(onClick = { viewModel.navigateTo("discovery") }) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Device",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No devices connected.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.devices, key = { it.id }) { device ->
                    DeviceCard(device) {
                        viewModel.navigateTo("device/${device.id}")
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Devices",
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Edit",
                            fontSize = 14.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { /* TODO */ }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    SavedDeviceRow("Phone (This device)", "Local", Icons.Default.PhoneAndroid)
                    SavedDeviceRow("MSStack", "Last seen 2 days ago", Icons.Default.Devices)
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: GenericDeviceUiState, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(24.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (device.kind.name.contains("GLASSES")) Icons.Default.SmartToy else Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (device.isConnected) Color(0xFF00C853) else Color(0xFFF0B86B))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (device.isConnected) "Connected • ${device.battery}" else "Offline",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                androidx.compose.material3.IconButton(onClick = onClick) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DeviceAction(Icons.Default.CameraAlt, "Camera")
                DeviceAction(Icons.Default.Mic, "Audio")
                DeviceAction(Icons.Default.Gamepad, "Controls")
                DeviceAction(Icons.Default.Settings, "Settings")
            }
        }
    }
}

@Composable
fun DeviceAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Surface(
            shape = CircleShape,
            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SavedDeviceRow(name: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = status,
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
"""
    with open('C:/Users/alisi/Eco/Halo/app/src/main/java/com/halo/app/ui/screens/devices/DevicesScreen.kt', 'w', encoding='utf-8') as f:
        f.write(top + bottom)
