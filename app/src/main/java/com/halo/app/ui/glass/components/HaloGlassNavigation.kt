package com.halo.app.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

data class NavigationItem(
    val route: String,
    val label: String,
    val glyph: HaloGlyph
)

/**
 * Halo's low-profile primary navigation.
 *
 * The selected destination carries a three-cell signal instead of a large pill or glow.
 */
@Composable
fun HaloNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    items: List<NavigationItem>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    fun primaryRoute(route: String?): String? = when {
        route == null -> null
        route.startsWith("notebook/") -> "notebook"
        route in setOf("ai_settings", "voice_settings", "permissions") -> "settings"
        route == "discovery" || route.startsWith("device/") -> "devices"
        else -> route
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .height(72.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected =
                currentDestination?.hierarchy?.any { it.route == item.route } == true ||
                    primaryRoute(currentRoute) == item.route
            val tint = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            val destination = navController.currentDestination
                            if (primaryRoute(destination?.route) != item.route) {
                                runCatching {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    )
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 30.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(11.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    HaloIcon(
                        glyph = item.glyph,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else tint,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.height(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .size(if (selected && index == 1) 4.dp else 3.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(1.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}
