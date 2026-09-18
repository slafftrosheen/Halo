package com.halo.app.ui.glass.material

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

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
    val immediate = 120
    val standard = 200
    val emphasized = 300
    
    val spring = SpringSpec<Float>(
        dampingRatio = 0.8f,
        stiffness = 400f
    )
    
    val easingStandard = tween<Float>(durationMillis = standard)
    val easingImmediate = tween<Float>(durationMillis = immediate)
    val easingEmphasized = tween<Float>(durationMillis = emphasized)
}

object HaloGlassTokens {
    // Spacing
    val xxs = HaloSpacing.xxs
    val xs = HaloSpacing.xs
    val sm = HaloSpacing.sm
    val md = HaloSpacing.md
    val lg = HaloSpacing.lg
    val xl = HaloSpacing.xl
    val xxl = HaloSpacing.xxl
    val xxxl = HaloSpacing.xxxl

    // Shapes
    val smallShape: Shape = HaloGlassShapes.small
    val mediumShape: Shape = HaloGlassShapes.medium
    val largeShape: Shape = HaloGlassShapes.large
    val xlShape: Shape = HaloGlassShapes.xl
    val pillShape: Shape = HaloGlassShapes.pill
    val circleShape: Shape = HaloGlassShapes.circle

    // Elevation
    val level0 = HaloGlassElevation.level0
    val level1 = HaloGlassElevation.level1
    val level2 = HaloGlassElevation.level2
    val level3 = HaloGlassElevation.level3
    val level4 = HaloGlassElevation.level4
    val level5 = HaloGlassElevation.level5
}

// Extension modifiers for glass effects
fun Modifier.glassElevation(elevation: Dp = HaloGlassTokens.level1) = 
    this.graphicsLayer { 
        shadowElevation = elevation.toPx()
    }

