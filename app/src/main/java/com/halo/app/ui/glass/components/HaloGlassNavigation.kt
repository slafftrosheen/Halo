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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
            .height(68.dp)
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
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (selected) {
                    Row(
                        modifier = Modifier.height(7.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        ).forEachIndexed { index, color ->
                            Box(
                                Modifier
                                    .size(if (index == 1) 5.dp else 4.dp)
                                    .background(color, RoundedCornerShape(1.5.dp))
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(7.dp))
                }

                HaloIcon(
                    glyph = item.glyph,
                    contentDescription = item.label,
                    modifier = Modifier.size(24.dp),
                    tint = tint
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
