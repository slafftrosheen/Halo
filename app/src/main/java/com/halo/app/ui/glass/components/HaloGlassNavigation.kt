package com.halo.app.ui.glass.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.app.ui.shell.navigateToPrimary

data class NavigationItem(val route: String, val label: String, val glyph: HaloGlyph)

/**
 * Developer Kit v1.2, Round 2: a floating 64dp glass pill, 12dp margins and 32dp radius.
 * Compositor-safe translucent fallback rather than an unconditional live blur on low-end phones.
 * Selected destinations have a light accent field, a 4.5dp dot AND heavier text.
 * The full tab name remains available to accessibility even when visual labels are hidden.
 */
@Composable
fun HaloNavigation(modifier: Modifier = Modifier, navController: NavHostController, items: List<NavigationItem>) {
    val entry by navController.currentBackStackEntryAsState()
    val selectedRoute = primaryNavigationRoute(entry?.destination?.route)
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp).height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = scheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                val selected = selectedRoute == item.route
                val label = uiText(item.label)
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Tab, onClickLabel = label) {
                            navController.navigateToPrimary(item.route)
                        },
                    shape = RoundedCornerShape(28.dp),
                    color = if (selected) scheme.primaryContainer.copy(alpha = 0.18f) else Color.Transparent,
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(27.dp), contentAlignment = Alignment.Center) {
                            HaloIcon(item.glyph,
                                contentDescription = if (selected) null else label,
                                modifier = Modifier.size(24.dp))
                            if (selected) {
                                Canvas(Modifier.size(4.5.dp).align(Alignment.TopEnd)) {
                                    drawCircle(scheme.primary)
                                }
                            }
                        }
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
