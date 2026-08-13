package com.halo.app.ui.glass.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Data model for a navigation item.
 */
data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Glass navigation bar for the main application shell.
 */
@Composable
fun HaloGlassNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    items: List<NavigationItem>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    fun getPrimaryRoute(route: String?): String? {
        return when (route) {
            "ai_settings", "permissions", "protocol_lab", "console", "inspector" -> "settings"
            "discovery" -> "devices"
            else -> route
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 12.dp
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(HaloGlassColors.BackgroundSecondary.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isDirectMatch = currentDestination?.hierarchy?.any { it.route == item.route } == true
                val isPrimaryMatch = getPrimaryRoute(currentRoute) == item.route
                val selected = isDirectMatch || isPrimaryMatch
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            Log.d("HaloGlassNavigation", "Navigating to: ${item.route}")
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) HaloGlassColors.Accent else HaloGlassColors.TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        color = if (selected) HaloGlassColors.TextPrimary else HaloGlassColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
