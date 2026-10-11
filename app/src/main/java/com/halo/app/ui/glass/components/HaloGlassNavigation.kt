package com.halo.app.ui.glass.components

import com.halo.app.language.uiText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.app.ui.shell.navigateToPrimary

data class NavigationItem(val route: String, val label: String, val glyph: HaloGlyph)

@Composable
fun HaloNavigation(modifier: Modifier = Modifier, navController: NavHostController, items: List<NavigationItem>) {
    val entry by navController.currentBackStackEntryAsState()
    val showLabels = LocalDensity.current.fontScale < 1.5f
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEach { item ->
            NavigationBarItem(
                selected = primaryNavigationRoute(entry?.destination?.route) == item.route,
                onClick = { navController.navigateToPrimary(item.route) },
                alwaysShowLabel = showLabels,
                icon = { HaloIcon(item.glyph, contentDescription = if (showLabels) null else uiText(item.label), modifier = Modifier.size(24.dp)) },
                // At accessibility sizes, retain full spoken names without squeezing words into tall columns.
                label = if (!showLabels) null else { { Text(uiText(item.label), style = MaterialTheme.typography.labelMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (primaryNavigationRoute(entry?.destination?.route) == item.route) FontWeight.SemiBold else FontWeight.Normal) } },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
