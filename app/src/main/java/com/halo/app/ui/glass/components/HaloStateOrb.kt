package com.halo.app.ui.glass.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloRuntimeState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Halo's primary presence and interaction surface.
 *
 * Entirely procedural: no bitmaps, no animation assets, no external
 * rendering dependencies.  ~600 particles on a Fibonacci sphere are
 * continuously deformed, Z-sorted, and projected with perspective.
 *
 * Each [HaloRuntimeState] drives a unique combination of:
 *   - organic deformation geometry
 *   - shape-morph target (2D glyph forms)
 *   - multi-layered reactive halo glow
 *   - three-tone particle coloration with depth mapping
 *   - motion speed, turbulence, expansion, and pulse characteristics
 *
 * The particle set is created once and never reallocated.
 * Nothing inside the draw loop allocates.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HaloStateOrb(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String = "Halo"
) {
    val transition = rememberInfiniteTransition(label = "halo_orb")

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "orb_press_scale"
    )

    val profile = haloOrbProfile(state)

    // --- Animated profile fields (spring-based for organic feel) ---

    val shapeAmount by animateFloatAsState(
        targetValue = profile.shape,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 120f),
        label = "orb_shape"
    )
    val turbulence by animateFloatAsState(
        targetValue = profile.turbulence,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 120f),
        label = "orb_turbulence"
    )
    val expansion by animateFloatAsState(
        targetValue = profile.expansion,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 100f),
        label = "orb_expansion"
    )
    val motionAmount by animateFloatAsState(
        targetValue = profile.motion,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 120f),
        label = "orb_motion"
    )
    val glyphMorph by animateFloatAsState(
        targetValue = profile.glyphMorph,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 80f),
        label = "orb_glyph"
    )

    val primaryColor by animateColorAsState(
        targetValue = profile.primary,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "orb_primary_color"
    )
    val secondaryColor by animateColorAsState(
        targetValue = profile.secondary,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "orb_secondary_color"
    )
    val tertiaryColor by animateColorAsState(
        targetValue = profile.tertiary,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "orb_tertiary_color"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = profile.glowIntensity,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 100f),
        label = "orb_glow_alpha"
    )

    /*
     * Single continuously-advancing phase drives all motion.
     * States multiply by different speed coefficients so transitions
     * never produce a visible discontinuity.
     */
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )

    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = profile.pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_pulse"
    )

    /*
     * Secondary slow phase for the halo glow rotation — decoupled from
     * the particle phase so the glow drifts independently.
     */
    val glowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_glow_phase"
    )

    val particles = remember { createHaloParticles(PARTICLE_COUNT) }
    /*
     * Reusable buffers — avoids allocating every frame.
     * sortIndices: Z-sort order.
     * projX/Y/Z/Depth: projected screen coordinates per particle.
     * glyphXY: 2-element scratch for glyph target output (avoids Pair boxing).
     */
    val sortIndices = remember { IntArray(PARTICLE_COUNT) { it } }
    val projX = remember { FloatArray(PARTICLE_COUNT) }
    val projY = remember { FloatArray(PARTICLE_COUNT) }
    val projZ = remember { FloatArray(PARTICLE_COUNT) }
    val projDepth = remember { FloatArray(PARTICLE_COUNT) }
    val glyphXY = remember { FloatArray(2) }

    Canvas(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick
                        )
                        .semantics {
                            this.contentDescription = contentDescription
                            role = Role.Button
                        }
                } else {
                    Modifier
                }
            )
    ) {
        drawHaloOrb(
            particles = particles,
            sortIndices = sortIndices,
            projX = projX,
            projY = projY,
            projZ = projZ,
            projDepth = projDepth,
            glyphXY = glyphXY,
            phase = phase,
            pulse = pulse,
            glowPhase = glowPhase,
            shapeAmount = shapeAmount,
            turbulence = turbulence,
            expansion = expansion,
            motionAmount = motionAmount,
            glyphMorph = glyphMorph,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            tertiaryColor = tertiaryColor,
            glowAlpha = glowAlpha,
            profile = profile,
            state = state
        )
    }
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private const val PARTICLE_COUNT = 600
private const val TWO_PI = (PI * 2.0).toFloat()

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

private data class HaloParticle(
    val x: Float,       // unit sphere x
    val y: Float,       // unit sphere y
    val z: Float,       // unit sphere z
    val theta: Float,   // azimuthal angle
    val latitude: Float,// same as y, kept for readability
    val phase: Float,   // per-particle phase offset [0..1)
    val size: Float,    // base radius multiplier
    val brightness: Float, // base brightness [0.42..1)
    val ring: Int        // concentric ring index (0 = core, higher = outer)
)

private data class HaloOrbProfile(
    val shape: Float,          // organic deformation amplitude
    val turbulence: Float,     // surface roughness
    val expansion: Float,      // overall scale
    val motion: Float,         // wave animation speed multiplier
    val rotation: Float,       // Y-axis rotation speed
    val pulseStrength: Float,  // breathing intensity
    val asymmetry: Float,      // directional distortion
    val glyphMorph: Float,     // 0 = organic, 1 = full glyph form
    val glowIntensity: Float,  // halo glow strength
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,       // third tone for depth
    val pulseDuration: Int,
    val glyphType: GlyphType   // which 2D form to morph toward
)

/**
 * The 2D glyph targets that particles can morph toward.
 * Each is generated algorithmically — no image assets.
 */
private enum class GlyphType {
    NONE,           // pure organic field
    EAR,            // listening — open arc
    LENS,           // recognizing — concentric focus
    CONSTELLATION,  // reasoning/planning — scattered nodes + links
    ARROW,          // executing — directional chevron
    WAVE,           // speaking — horizontal waveform
    SCATTER         // recovering — fragmented explosion
}

// ---------------------------------------------------------------------------
// State → profile mapping
// ---------------------------------------------------------------------------

private fun haloOrbProfile(state: HaloRuntimeState): HaloOrbProfile = when (state) {
    HaloRuntimeState.Idle -> HaloOrbProfile(
        shape = 0.08f,
        turbulence = 0.08f,
        expansion = 0.92f,
        motion = 0.18f,
        rotation = 0.14f,
        pulseStrength = 0.06f,
        asymmetry = 0.06f,
        glyphMorph = 0f,
        glowIntensity = 0.10f,
        primary = HaloGlassColors.Accent,
        secondary = Color(0xFF6D55FF),
        tertiary = Color(0xFF2A3A55),
        pulseDuration = 3400,
        glyphType = GlyphType.NONE
    )

    HaloRuntimeState.Listening,
    HaloRuntimeState.Wakeword -> HaloOrbProfile(
        shape = 0.20f,
        turbulence = 0.28f,
        expansion = 1.06f,
        motion = 0.85f,
        rotation = 0.55f,
        pulseStrength = 0.22f,
        asymmetry = 0.24f,
        glyphMorph = 0.35f,
        glowIntensity = 0.28f,
        primary = Color(0xFF00D9FF),
        secondary = Color(0xFF6D4DFF),
        tertiary = Color(0xFF00445A),
        pulseDuration = 1800,
        glyphType = GlyphType.EAR
    )

    HaloRuntimeState.Capturing -> HaloOrbProfile(
        shape = 0.30f,
        turbulence = 0.42f,
        expansion = 1.12f,
        motion = 1.10f,
        rotation = 0.80f,
        pulseStrength = 0.32f,
        asymmetry = 0.38f,
        glyphMorph = 0.50f,
        glowIntensity = 0.38f,
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFF4D63FF),
        tertiary = Color(0xFF003848),
        pulseDuration = 1100,
        glyphType = GlyphType.EAR
    )

    HaloRuntimeState.Recognizing -> HaloOrbProfile(
        shape = 0.22f,
        turbulence = 0.45f,
        expansion = 0.98f,
        motion = 1.35f,
        rotation = 1.20f,
        pulseStrength = 0.18f,
        asymmetry = 0.48f,
        glyphMorph = 0.55f,
        glowIntensity = 0.25f,
        primary = Color(0xFF43B8FF),
        secondary = Color(0xFF765CFF),
        tertiary = Color(0xFF1A2840),
        pulseDuration = 1400,
        glyphType = GlyphType.LENS
    )

    HaloRuntimeState.Reasoning,
    HaloRuntimeState.Planning -> HaloOrbProfile(
        shape = 0.48f,
        turbulence = 0.75f,
        expansion = 1.00f,
        motion = 1.20f,
        rotation = 1.50f,
        pulseStrength = 0.20f,
        asymmetry = 0.74f,
        glyphMorph = 0.40f,
        glowIntensity = 0.32f,
        primary = Color(0xFF00D7FF),
        secondary = Color(0xFFA044FF),
        tertiary = Color(0xFF1A0A3A),
        pulseDuration = 1500,
        glyphType = GlyphType.CONSTELLATION
    )

    HaloRuntimeState.Executing -> HaloOrbProfile(
        shape = 0.36f,
        turbulence = 0.58f,
        expansion = 1.08f,
        motion = 1.80f,
        rotation = 2.00f,
        pulseStrength = 0.30f,
        asymmetry = 0.88f,
        glyphMorph = 0.55f,
        glowIntensity = 0.42f,
        primary = Color(0xFF00E1FF),
        secondary = Color(0xFF5367FF),
        tertiary = Color(0xFF001830),
        pulseDuration = 950,
        glyphType = GlyphType.ARROW
    )

    HaloRuntimeState.Speaking -> HaloOrbProfile(
        shape = 0.24f,
        turbulence = 0.32f,
        expansion = 1.02f,
        motion = 0.85f,
        rotation = 0.65f,
        pulseStrength = 0.44f,
        asymmetry = 0.30f,
        glyphMorph = 0.45f,
        glowIntensity = 0.35f,
        primary = Color(0xFF19DFFF),
        secondary = Color(0xFF8A55FF),
        tertiary = Color(0xFF0A2838),
        pulseDuration = 1050,
        glyphType = GlyphType.WAVE
    )

    HaloRuntimeState.Recovering -> HaloOrbProfile(
        shape = 0.58f,
        turbulence = 0.95f,
        expansion = 1.06f,
        motion = 1.40f,
        rotation = 0.90f,
        pulseStrength = 0.28f,
        asymmetry = 1.00f,
        glyphMorph = 0.50f,
        glowIntensity = 0.30f,
        primary = HaloGlassColors.Danger,
        secondary = Color(0xFF8D4DFF),
        tertiary = Color(0xFF3A1020),
        pulseDuration = 750,
        glyphType = GlyphType.SCATTER
    )

    HaloRuntimeState.Stopped -> HaloOrbProfile(
        shape = 0.03f,
        turbulence = 0.03f,
        expansion = 0.85f,
        motion = 0.04f,
        rotation = 0.03f,
        pulseStrength = 0f,
        asymmetry = 0f,
        glyphMorph = 0f,
        glowIntensity = 0.04f,
        primary = HaloGlassColors.TextMuted,
        secondary = HaloGlassColors.TextMuted,
        tertiary = Color(0xFF1A1A1A),
        pulseDuration = 4500,
        glyphType = GlyphType.NONE
    )
}

// ---------------------------------------------------------------------------
// Glyph target generators — mathematical point sets, no assets
// ---------------------------------------------------------------------------

/**
 * Compute the target XY position for a particle when morphing into a
 * 2D glyph shape.  Writes (x, y) in [-1..1] space into [out][0] and [out][1].
 *
 * The coordinate is deterministic for each particle index so the
 * assignment is stable across frames.  Writing into a shared buffer
 * eliminates Pair object boxing (~600 per frame).
 */
private fun glyphTarget(
    type: GlyphType,
    index: Int,
    count: Int,
    time: Float,
    out: FloatArray
) {
    val t = index.toFloat() / count.toFloat()
    when (type) {
        GlyphType.NONE -> { out[0] = 0f; out[1] = 0f }

        GlyphType.EAR -> {
            val angle = -PI.toFloat() * 0.7f + t * PI.toFloat() * 1.4f
            val r = 0.65f + sin(t * PI.toFloat() * 3f) * 0.08f
            out[0] = cos(angle) * r * 0.85f
            out[1] = sin(angle) * r
        }

        GlyphType.LENS -> {
            val ring = (t * 4f).toInt().coerceAtMost(3)
            val withinRing = (t * 4f) - ring
            val ringRadius = 0.20f + ring * 0.20f
            val angle = withinRing * TWO_PI + ring * 0.5f
            out[0] = cos(angle) * ringRadius
            out[1] = sin(angle) * ringRadius
        }

        GlyphType.CONSTELLATION -> {
            val nodeCount = 7
            val nodeIndex = (t * nodeCount).toInt().coerceAtMost(nodeCount - 1)
            val withinNode = (t * nodeCount) - nodeIndex
            val nodeAngle = nodeIndex.toFloat() / nodeCount * TWO_PI + 0.3f
            val nodeR = 0.35f + (nodeIndex % 3) * 0.18f
            val nx = cos(nodeAngle) * nodeR
            val ny = sin(nodeAngle) * nodeR
            val scatter = 0.12f * (1f - withinNode * 0.5f)
            val sa = withinNode * TWO_PI * 2.5f + index * 0.1f
            out[0] = nx + cos(sa) * scatter
            out[1] = ny + sin(sa) * scatter
        }

        GlyphType.ARROW -> {
            val row = (t * 12f).toInt().coerceAtMost(11)
            val withinRow = (t * 12f) - row
            val yPos = 0.7f - row * 0.12f
            val halfWidth = if (row < 6) (row + 1) * 0.08f else 0.15f
            out[0] = -halfWidth + withinRow * halfWidth * 2f
            out[1] = yPos
        }

        GlyphType.WAVE -> {
            val x = -0.8f + t * 1.6f
            val amp = 0.3f + sin(time * 1.5f + t * PI.toFloat()) * 0.12f
            out[0] = x
            out[1] = sin(t * PI.toFloat() * 3f + time * 2f) * amp
        }

        GlyphType.SCATTER -> {
            val seed = index * 0.75487766f
            val angle = fract(seed * 7.13f) * TWO_PI
            val r = 0.4f + fract(seed * 3.71f) * 0.5f
            val jitter = sin(time * 3f + index * 0.3f) * 0.08f
            out[0] = cos(angle) * (r + jitter)
            out[1] = sin(angle) * (r + jitter)
        }
    }
}

// ---------------------------------------------------------------------------
// Particle creation — Fibonacci sphere (deterministic)
// ---------------------------------------------------------------------------

private fun createHaloParticles(count: Int): List<HaloParticle> {
    val particles = ArrayList<HaloParticle>(count)
    val goldenAngle = PI * (3.0 - sqrt(5.0))

    for (i in 0 until count) {
        val normalized = if (count == 1) 0.5f else i.toFloat() / (count - 1).toFloat()
        val y = 1f - normalized * 2f
        val radius = sqrt((1f - y * y).coerceAtLeast(0f))
        val theta = (goldenAngle * i).toFloat()
        val x = cos(theta) * radius
        val z = sin(theta) * radius
        val seed = i * 0.75487766f
        // Assign concentric ring (0–4) based on distance from pole
        val ring = ((1f - abs(y)) * 5f).toInt().coerceIn(0, 4)

        particles += HaloParticle(
            x = x, y = y, z = z,
            theta = theta,
            latitude = y,
            phase = fract(seed * 17.123f),
            size = 0.60f + fract(seed * 5.71f) * 1.15f,
            brightness = 0.40f + fract(seed * 11.37f) * 0.60f,
            ring = ring
        )
    }
    return particles
}

// ---------------------------------------------------------------------------
// Draw
// ---------------------------------------------------------------------------

private fun DrawScope.drawHaloOrb(
    particles: List<HaloParticle>,
    sortIndices: IntArray,
    projX: FloatArray,
    projY: FloatArray,
    projZ: FloatArray,
    projDepth: FloatArray,
    glyphXY: FloatArray,
    phase: Float,
    pulse: Float,
    glowPhase: Float,
    shapeAmount: Float,
    turbulence: Float,
    expansion: Float,
    motionAmount: Float,
    glyphMorph: Float,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    glowAlpha: Float,
    profile: HaloOrbProfile,
    state: HaloRuntimeState
) {
    val width = size.width
    val height = size.height
    val center = Offset(width * 0.5f, height * 0.5f)
    val baseRadius = minOf(width, height) * 0.46f

    val time = phase * profile.rotation
    val cameraDistance = 2.6f
    val perspectiveStrength = 0.70f

    // -----------------------------------------------------------------------
    // Pass 1: Compute projected positions and Z for sorting
    // -----------------------------------------------------------------------

    for (idx in 0 until PARTICLE_COUNT) {
        val particle = particles[idx]
        val x0 = particle.x
        val y0 = particle.y
        val z0 = particle.z

        // --- Glyph morph target (writes into glyphXY to avoid Pair boxing) ---
        glyphTarget(profile.glyphType, idx, PARTICLE_COUNT, time, glyphXY)
        val gx = glyphXY[0]; val gy = glyphXY[1]
        // Smoothly morph between organic sphere position and flat glyph
        val morphedX0 = x0 + (gx - x0) * glyphMorph
        val morphedY0 = y0 + (gy - y0) * glyphMorph
        val morphedZ0 = z0 * (1f - glyphMorph * 0.75f) // flatten Z toward glyph

        // --- Organic deformation (three independent frequencies) ---
        val waveA = sin(
            particle.theta * 3.0f +
                particle.latitude * 4.5f +
                time * (1.0f + motionAmount)
        )
        val waveB = sin(
            particle.theta * 7.0f -
                particle.latitude * 2.5f -
                time * (1.7f + motionAmount * 0.5f) +
                particle.phase * TWO_PI
        )
        val waveC = sin(
            (x0 + y0 + z0) * 8.0f + time * 0.7f
        )
        // Fourth frequency — subtle mid-range detail
        val waveD = sin(
            particle.theta * 5.0f +
                time * 1.3f +
                particle.phase * 3.14f
        ) * 0.12f

        val asymmetryTerm =
            x0 * y0 * profile.asymmetry * 0.40f +
                z0 * x0 * profile.asymmetry * 0.26f

        val organicDeform = shapeAmount * (
            waveA * 0.44f + waveB * 0.26f + waveC * 0.14f + waveD + asymmetryTerm
            )
        // Reduce organic deformation when morphed toward a glyph
        val deformation = 1f + organicDeform * (1f - glyphMorph * 0.6f)

        var x = morphedX0 * deformation
        var y = morphedY0 * deformation
        var z = morphedZ0 * deformation

        // --- Breathing ---
        val breathe = 1f + sin(phase * 1.15f + particle.phase * TWO_PI) *
            profile.pulseStrength * 0.10f

        // --- State-specific field effects ---
        val listeningPulse =
            if (state == HaloRuntimeState.Listening ||
                state == HaloRuntimeState.Wakeword ||
                state == HaloRuntimeState.Capturing
            ) {
                1f + pulse * profile.pulseStrength * 0.14f
            } else 1f

        val speakingWave =
            if (state == HaloRuntimeState.Speaking) {
                1f + sin(pulse * TWO_PI + particle.latitude * 3.2f) * 0.05f
            } else 1f

        // Executing: directional push forward (positive Y bias)
        val executingPush =
            if (state == HaloRuntimeState.Executing) {
                val forward = (particle.latitude + 1f) * 0.5f // 0 at bottom, 1 at top
                1f + forward * pulse * 0.06f
            } else 1f

        // Recovering: chaotic per-particle jitter
        val recoveringJitter =
            if (state == HaloRuntimeState.Recovering) {
                1f + sin(phase * 4f + particle.phase * 20f) * 0.04f
            } else 1f

        val scaleAll = expansion * breathe * listeningPulse * speakingWave *
            executingPush * recoveringJitter

        x *= scaleAll
        y *= scaleAll
        z *= scaleAll

        // --- Rotation ---
        val yAngle = time * 0.40f
        val cosYa = cos(yAngle); val sinYa = sin(yAngle)
        val rx = x * cosYa - z * sinYa
        val rz = x * sinYa + z * cosYa
        x = rx; z = rz

        val zAngle = time * 0.10f + profile.asymmetry * sin(time * 0.40f) * 0.5f
        val cosZa = cos(zAngle); val sinZa = sin(zAngle)
        val rx2 = x * cosZa - y * sinZa
        val ry2 = x * sinZa + y * cosZa
        x = rx2; y = ry2

        // Small X-axis tilt for depth
        val xTilt = sin(time * 0.08f) * 0.06f
        val cosXt = cos(xTilt); val sinXt = sin(xTilt)
        val ry3 = y * cosXt - z * sinXt
        val rz3 = y * sinXt + z * cosXt
        y = ry3; z = rz3

        // --- Perspective ---
        val depth = cameraDistance /
            (cameraDistance - z * perspectiveStrength).coerceAtLeast(1.20f)
        val screenX = center.x + x * baseRadius * 0.90f * depth
        val screenY = center.y + y * baseRadius * 0.90f * depth

        projX[idx] = screenX
        projY[idx] = screenY
        projZ[idx] = z
        projDepth[idx] = depth
        sortIndices[idx] = idx
    }

    // -----------------------------------------------------------------------
    // Z-sort: back-to-front (insertion sort — fast on nearly-sorted data)
    // -----------------------------------------------------------------------
    for (i in 1 until PARTICLE_COUNT) {
        val key = sortIndices[i]
        val keyZ = projZ[key]
        var j = i - 1
        while (j >= 0 && projZ[sortIndices[j]] > keyZ) {
            sortIndices[j + 1] = sortIndices[j]
            j--
        }
        sortIndices[j + 1] = key
    }

    // -----------------------------------------------------------------------
    // Halo glow layers (drawn behind particles)
    // -----------------------------------------------------------------------
    drawHaloGlow(
        center = center,
        baseRadius = baseRadius,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        tertiaryColor = tertiaryColor,
        glowAlpha = glowAlpha,
        glowPhase = glowPhase,
        pulse = pulse,
        state = state
    )

    // -----------------------------------------------------------------------
    // Pass 2: Draw particles back-to-front
    // -----------------------------------------------------------------------
    for (si in 0 until PARTICLE_COUNT) {
        val idx = sortIndices[si]
        val particle = particles[idx]
        val screenX = projX[idx]
        val screenY = projY[idx]
        val z = projZ[idx]

        // Cull
        if (screenX < -10f || screenX > width + 10f ||
            screenY < -10f || screenY > height + 10f
        ) continue

        val depthBrightness = ((z + 1f) * 0.5f).coerceIn(0f, 1f)
        val brightness = (particle.brightness * (0.35f + depthBrightness * 0.78f))
            .coerceIn(0.06f, 1f)

        // Three-tone color mixing based on depth and phase
        val baseMix = (
            0.15f +
                particle.phase * 0.48f +
                (z + 1f) * 0.18f +
                sin(time + particle.phase * 5f) * 0.10f
            ).coerceIn(0f, 1f)

        val color = if (baseMix < 0.5f) {
            mixColor(primaryColor, secondaryColor, baseMix * 2f)
        } else {
            mixColor(secondaryColor, tertiaryColor, (baseMix - 0.5f) * 2f)
        }

        val particleRadius = (
            particle.size *
                (0.68f + depthBrightness * 0.80f) *
                (0.88f + turbulence * 0.18f)
            ).coerceIn(0.50f, 2.8f)

        // Soft glow on brighter particles (back particles get less)
        if (particle.brightness > 0.78f && depthBrightness > 0.30f) {
            drawCircle(
                color = color.copy(alpha = (brightness * 0.065f * depthBrightness)
                    .coerceAtMost(0.08f)),
                radius = particleRadius * 3.5f,
                center = Offset(screenX, screenY)
            )
        }

        // Core particle
        drawCircle(
            color = color.copy(alpha = (brightness * 0.78f).coerceAtMost(0.85f)),
            radius = particleRadius,
            center = Offset(screenX, screenY)
        )

        // Specular highlight on brightest front-facing particles
        if (depthBrightness > 0.72f && particle.brightness > 0.85f) {
            drawCircle(
                color = Color.White.copy(
                    alpha = (brightness * 0.30f * (depthBrightness - 0.72f) / 0.28f)
                        .coerceIn(0f, 0.32f)
                ),
                radius = particleRadius * 0.36f,
                center = Offset(screenX, screenY)
            )
        }
    }

    // -----------------------------------------------------------------------
    // State-specific overlays (drawn on top)
    // -----------------------------------------------------------------------
    drawStateOverlay(
        center = center,
        baseRadius = baseRadius,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        pulse = pulse,
        phase = phase,
        state = state,
        profile = profile
    )
}

// ---------------------------------------------------------------------------
// Halo glow — multi-layered, state-reactive
// ---------------------------------------------------------------------------

private fun DrawScope.drawHaloGlow(
    center: Offset,
    baseRadius: Float,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    glowAlpha: Float,
    glowPhase: Float,
    pulse: Float,
    state: HaloRuntimeState
) {
    if (glowAlpha < 0.01f) return

    // Layer 1: Large soft outer halo
    val outerRadius = baseRadius * 1.35f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = glowAlpha * 0.22f),
                secondaryColor.copy(alpha = glowAlpha * 0.08f),
                tertiaryColor.copy(alpha = glowAlpha * 0.03f),
                Color.Transparent
            ),
            center = center,
            radius = outerRadius
        ),
        radius = outerRadius,
        center = center
    )

    // Layer 2: Tighter core glow with slight offset for asymmetry
    val coreOffsetX = sin(glowPhase * 0.7f) * baseRadius * 0.04f
    val coreOffsetY = cos(glowPhase * 0.5f) * baseRadius * 0.03f
    val coreCenter = Offset(center.x + coreOffsetX, center.y + coreOffsetY)
    val coreRadius = baseRadius * 0.72f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = glowAlpha * 0.18f),
                secondaryColor.copy(alpha = glowAlpha * 0.06f),
                Color.Transparent
            ),
            center = coreCenter,
            radius = coreRadius
        ),
        radius = coreRadius,
        center = coreCenter
    )

    // Layer 3: Pulsing ring — only for active states
    if (state != HaloRuntimeState.Idle && state != HaloRuntimeState.Stopped) {
        val ringPulse = (sin(glowPhase * 1.2f) * 0.5f + 0.5f)
        val ringRadius = baseRadius * (0.85f + ringPulse * 0.12f)
        val ringAlpha = glowAlpha * 0.10f * (1f - ringPulse * 0.5f)
        drawCircle(
            color = primaryColor.copy(alpha = ringAlpha.coerceIn(0f, 0.12f)),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 0.8f + ringPulse * 0.4f)
        )
    }

    // Layer 4: Breathing expansion ring for listening states
    if (state == HaloRuntimeState.Listening ||
        state == HaloRuntimeState.Wakeword ||
        state == HaloRuntimeState.Capturing
    ) {
        val breathRadius = baseRadius * (0.90f + pulse * 0.35f)
        val breathAlpha = glowAlpha * 0.16f * (1f - pulse)
        drawCircle(
            color = primaryColor.copy(alpha = breathAlpha.coerceAtMost(0.18f)),
            radius = breathRadius,
            center = center,
            style = Stroke(width = 1.4f)
        )
    }
}

// ---------------------------------------------------------------------------
// State-specific overlays
// ---------------------------------------------------------------------------

private fun DrawScope.drawStateOverlay(
    center: Offset,
    baseRadius: Float,
    primaryColor: Color,
    secondaryColor: Color,
    pulse: Float,
    phase: Float,
    state: HaloRuntimeState,
    profile: HaloOrbProfile
) {
    when (state) {
        HaloRuntimeState.Speaking -> {
            // Dual expanding wave rings — staggered
            for (i in 0..1) {
                val p = (pulse + i * 0.45f) % 1f
                val waveRadius = baseRadius * (0.50f + p * 0.55f)
                val alpha = 0.10f * (1f - p).pow(1.5f)
                drawCircle(
                    color = primaryColor.copy(alpha = alpha.coerceIn(0f, 0.12f)),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = 1.0f + (1f - p) * 0.6f)
                )
            }
        }

        HaloRuntimeState.Executing -> {
            // Subtle directional sweep — a thin arc that rotates quickly
            val sweepAngle = phase * profile.rotation * 50f
            val arcRadius = baseRadius * 0.82f
            drawArc(
                color = primaryColor.copy(alpha = 0.10f),
                startAngle = sweepAngle,
                sweepAngle = 60f,
                useCenter = false,
                style = Stroke(width = 1.0f),
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f)
            )
        }

        HaloRuntimeState.Recovering -> {
            // Fragmented outer ring — discontinuous arcs
            val fragmentCount = 5
            for (i in 0 until fragmentCount) {
                val startAngle = i * (360f / fragmentCount) + phase * 15f
                val gap = sin(phase * 2f + i * 1.2f) * 15f
                drawArc(
                    color = primaryColor.copy(alpha = 0.08f),
                    startAngle = startAngle + gap,
                    sweepAngle = 35f,
                    useCenter = false,
                    style = Stroke(width = 0.8f),
                    topLeft = Offset(
                        center.x - baseRadius * 0.88f,
                        center.y - baseRadius * 0.88f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        baseRadius * 1.76f,
                        baseRadius * 1.76f
                    )
                )
            }
        }

        HaloRuntimeState.Recognizing -> {
            // Three subtle orbiting focus dots
            for (i in 0..2) {
                val angle = phase * 1.4f + i * (TWO_PI / 3f)
                val orbitR = baseRadius * 0.75f
                val dotX = center.x + cos(angle) * orbitR
                val dotY = center.y + sin(angle) * orbitR
                drawCircle(
                    color = primaryColor.copy(alpha = 0.15f),
                    radius = 2.0f,
                    center = Offset(dotX, dotY)
                )
            }
        }

        else -> { /* No overlay for Idle, Stopped, Listening, Wakeword, Capturing,
                      Reasoning, Planning — the particle field alone carries the state */ }
    }
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

private fun mixColor(first: Color, second: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = first.red + (second.red - first.red) * t,
        green = first.green + (second.green - first.green) * t,
        blue = first.blue + (second.blue - first.blue) * t,
        alpha = first.alpha + (second.alpha - first.alpha) * t
    )
}

private fun fract(value: Float): Float = value - floor(value)

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview
@Composable
private fun PreviewHaloStateOrb() {
    Column(
        modifier = Modifier
            .background(HaloGlassColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HaloStateOrb(HaloRuntimeState.Idle, size = 100.dp)
            HaloStateOrb(HaloRuntimeState.Listening, size = 100.dp)
            HaloStateOrb(HaloRuntimeState.Reasoning, size = 100.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HaloStateOrb(HaloRuntimeState.Executing, size = 100.dp)
            HaloStateOrb(HaloRuntimeState.Speaking, size = 100.dp)
            HaloStateOrb(HaloRuntimeState.Recovering, size = 100.dp)
        }
    }
}
