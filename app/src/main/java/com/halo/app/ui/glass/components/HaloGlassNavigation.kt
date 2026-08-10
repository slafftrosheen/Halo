package com.halo.app.ui.glass.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassShapes
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Data class representing a navigation item for Halo Glass navigation.
 */
data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Glass navigation bar component for bottom navigation.
 * Fully glass-styled to match Halo Glass design language.
 */
@Composable
fun HaloGlassNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    items: List<NavigationItem>,
    selectedItemIndex: Int = 0,
    onItemSelected: (Int) -> Unit = {}
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        ?: items.getOrNull(0)?.route ?: return
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(HaloGlassColors.Background)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = item.route == currentRoute
                val animateIn by remember { androidx.compose.runtime.derivedStateOf { isSelected } }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!isSelected) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isSelected) HaloGlassColors.Accent else HaloGlassColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = item.label,
                        color = if (isSelected) HaloGlassColors.Accent else HaloGlassColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun NavController.currentBackStackEntryAsState(): androidx.compose.runtime.State<androidx.navigation.NavBackStackEntry?> {
    return androidx.navigation.compose.currentBackStackEntryAsState(navController = this)
}

@Composable
private fun findStartDestination(graph: androidx.navigation.NavGraph): androidx.navigation.NavDestination {
    return graph.findStartDestination()
}