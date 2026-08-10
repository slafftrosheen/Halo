package com.halo.app.ui.glass.material

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing

object HaloSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object HaloGlassShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(18.dp)
    val large = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(percent = 50)
    val circle = CircleShape
}

object HaloGlassElevation {
    val level0 = 0.dp
    val level1 = 4.dp
    val level2 = 8.dp
    val level3 = 12.dp
    val level4 = 20.dp
    val level5 = 32.dp
}

object HaloGlassMotion {
    val immediate = 120  // ms
    val standard = 200
    val emphasized = 300
    
    val spring = SpringSpec(
        dampingRatio = 0.8f,
        stiffness = 400f
    )
    
    val easingStandard = tween(durationMillis = standard)
    val easingImmediate = tween(durationMillis = immediate)
    val easingEmphasized = tween(durationMillis = emphasized)
}

// Extension modifiers for glass effects
fun Modifier.glassElevation(elevation: Dp = HaloGlassElevation.level1) = 
    this.graphicsLayer { 
        elevationZ = elevation 
        translationZ = elevation 
    }

fun Modifier.rippleGlass() = 
    this.then(
        androidx.compose.foundation.clickable {
            // Placeholder - actual implementation in components
        }
    )