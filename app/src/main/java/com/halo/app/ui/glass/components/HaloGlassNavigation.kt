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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.app.ui.shell.navigateToPrimary

data class NavigationItem(val route: String, val label: String, val glyph: HaloGlyph)

internal fun navigationLabelsVisible(fontScale: Float): Boolean = fontScale < 1.3f

/**
 * Developer Kit v1.2, Round 2: a floating 64dp glass pill, 12dp margins and 32dp radius.
 * Compositor-safe translucent fallback rather than an unconditional live blur on low-end phones.
 * Selected destinations have a light accent field, a 4.5dp dot AND heavier text.
 * At large font scales, use icon-only tabs instead of ellipsizing five translated names.
 * Full names remain accessible; navigation-bar insets sit outside the floating pill.
 */
@Composable
fun HaloNavigation(modifier: Modifier = Modifier, navController: NavHostController, items: List<NavigationItem>) {
    val entry by navController.currentBackStackEntryAsState()
    val selectedRoute = primaryNavigationRoute(entry?.destination?.route)
    val scheme = MaterialTheme.colorScheme
    val showLabels = navigationLabelsVisible(LocalDensity.current.fontScale)
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp).height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = scheme.surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                val selected = selectedRoute == item.route
                val label = uiText(item.label)
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .semantics { this.selected = selected; contentDescription = label }
                        .clickable(role = Role.Tab, onClickLabel = label) {
                            navController.navigateToPrimary(item.route)
                        },
                    shape = RoundedCornerShape(26.dp),
                    color = if (selected) scheme.primaryContainer.copy(alpha = 0.17f) else Color.Transparent,
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(if (showLabels) 24.dp else 32.dp),
                            contentAlignment = Alignment.Center) {
                            HaloIcon(item.glyph, contentDescription = null,
                                modifier = Modifier.size(if (showLabels) 22.dp else 28.dp),
                                tint = if (selected) scheme.primary else scheme.onSurfaceVariant)
                            if (selected) {
                                Canvas(Modifier.size(4.5.dp).align(Alignment.TopEnd)) {
                                    drawCircle(scheme.primary)
                                }
                            }
                        }
                        if (showLabels) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
