package com.example.sensorysync.visuals

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.sensorysync.model.ControlState
import kotlin.math.*

class VisualPatternRenderer {

    // Background floating plankton spores
    private class Spore(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var alpha: Float,
        var hueOffset: Float
    )

    // Sparkle particles emitted when the child's gaze focuses on the jellyfish
    private class Sparkle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        var size: Float,
        var color: Color
    )

    // Dense 3D Silky Filaments (40 ultra-fine tentacles, 18 joints each)
    private val numTentacles = 40
    private val jointsPerTentacle = 18
    private val tentacleJoints = Array(numTentacles) { Array(jointsPerTentacle) { Offset(0.5f, 0.5f) } }

    private val spores = List(180) {
        Spore(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            vx = ((Math.random() - 0.5) * 0.00025).toFloat(),
            vy = ((Math.random() - 0.5) * 0.00025).toFloat(),
            size = (1.2f + Math.random() * 3.2f).toFloat(),
            alpha = (0.15f + Math.random() * 0.50f).toFloat(),
            hueOffset = (Math.random() * 60f).toFloat()
        )
    }

    private val sparkles = mutableListOf<Sparkle>()

    private var animTime = 0f

    // Jellyfish continuous trajectory (restricted to small turns <= 20 deg)
    private var jellyX = 0.5f
    private var jellyY = 0.5f
    private var jellyVx = 0f
    private var jellyVy = 0f
    private var jellyHeading = -0.3f // Base trajectory angle
    private var targetHeading = -0.3f
    private var swimBaseSpeed = 0.0012f
    private var jellyTiltZ = 0f
    private var timeOffScreenSec = 0f

    // Focus state output
    var latestJellyfishPos = Offset(0.5f, 0.5f)
        private set
    var isCurrentlyFocused = false
        private set
    var gazeJellyDistance = 1.0f
        private set

    fun render(drawScope: DrawScope, state: ControlState, frameDeltaTime: Float) {
        val dt = frameDeltaTime.coerceIn(0.001f, 0.05f)
        animTime += dt * state.speedMultiplier

        val width = drawScope.size.width
        val height = drawScope.size.height

        // 1. Update Autonomous Trajectory (Allows screen exit, re-enters within 3s, turns <= 20 deg)
        updateJellyfishMovement(dt, state.speedMultiplier, state.strobeFrequencyHz)

        val jx = jellyX * width
        val jy = jellyY * height
        latestJellyfishPos = Offset(jellyX, jellyY)

        // 2. Gaze Focus Calculation
        val gazeNormX = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.x else 0.5f
        val gazeNormY = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.y else 0.5f
        val gazePx = gazeNormX * width
        val gazePy = gazeNormY * height

        val dxNorm = (gazeNormX - jellyX)
        val dyNorm = (gazeNormY - jellyY) * (height / width.coerceAtLeast(1f))
        gazeJellyDistance = sqrt(dxNorm * dxNorm + dyNorm * dyNorm)

        // Focus condition: gaze near the 3D jellyfish bell while on-screen
        val isOnScreen = jellyX in -0.05f..1.05f && jellyY in -0.05f..1.05f
        isCurrentlyFocused = isOnScreen && state.gazeData.isFaceDetected && gazeJellyDistance < 0.22f

        // 3. Render Deep Space Plankton Spores
        renderSpores(drawScope, state, width, height)

        // 4. Render Focus Reward Starbursts
        if (isCurrentlyFocused) {
            emitFocusSparkles(jx, jy, state.primaryHue)
        }
        renderSparkles(drawScope, dt)

        // 5. Render 3D High-Fidelity Photorealistic Cosmic Jellyfish
        render3DJellyfish(drawScope, state, jx, jy, width, height)

        // 6. Render Real-Time Eye Gaze Tracking Point (Scaled to 0.3x)
        if (state.gazeData.isFaceDetected) {
            renderGazeReticleSmall(drawScope, gazePx, gazePy, isCurrentlyFocused, state.primaryHue)
        }
    }

    private fun updateJellyfishMovement(dt: Float, speedMul: Float, pulseFreq: Float) {
        val freq = pulseFreq.coerceIn(0.2f, 3.0f)
        val pulsePhase = (animTime * freq * 2.0 * PI)
        val pulsePower = ((sin(pulsePhase) + 1.0) / 2.0).toFloat()

        // 1. Check if off-screen (beyond visible bounds [-0.25, 1.25])
        val isOffScreen = jellyX < -0.22f || jellyX > 1.22f || jellyY < -0.22f || jellyY > 1.22f

        if (isOffScreen) {
            timeOffScreenSec += dt
            // Don't let the jellyfish stay off-screen more than 2.2 seconds (< 3 seconds guarantee)
            if (timeOffScreenSec >= 2.0f) {
                reenterFromRandomBoundary()
                timeOffScreenSec = 0f
            }
        } else {
            timeOffScreenSec = 0f
        }

        // 2. Very gentle organic path undulation: strictly capped to small angle turns <= 20 deg (0.35 rad)
        val baseWanderDelta = (sin(animTime * 0.22f) * 0.14f + cos(animTime * 0.15f) * 0.10f) // <= 0.24 rad (~14 deg)
        val desiredAngle = targetHeading + baseWanderDelta

        // Strict Angular Slew Rate Guard (Max ~15 deg/sec turn rate)
        val maxTurnRate = 0.28f // radians per sec (~16 deg/sec)
        var angleDiff = desiredAngle - jellyHeading
        while (angleDiff > PI) angleDiff -= (2.0 * PI).toFloat()
        while (angleDiff < -PI) angleDiff += (2.0 * PI).toFloat()

        val maxStep = maxTurnRate * dt
        val clampedTurn = angleDiff.coerceIn(-maxStep, maxStep)
        jellyHeading += clampedTurn

        // 3. Smooth forward propulsion with pulse power stroke
        val thrust = if (pulsePower > 0.35f) ((pulsePower - 0.35f) * 1.5f) else 0.15f
        val currentSpeed = swimBaseSpeed * speedMul * (0.5f + thrust)

        val desiredVx = cos(jellyHeading) * currentSpeed * 1.25f
        val desiredVy = sin(jellyHeading) * currentSpeed * 0.85f

        val accel = (2.0f * dt).coerceIn(0.01f, 0.2f)
        jellyVx += (desiredVx - jellyVx) * accel
        jellyVy += (desiredVy - jellyVy) * accel

        jellyX += jellyVx
        jellyY += jellyVy

        // Smooth gentle banking tilt
        val targetTilt = (clampedTurn / maxStep.coerceAtLeast(0.001f)) * 6.0f
        jellyTiltZ += (targetTilt - jellyTiltZ) * (2.0f * dt)
    }

    private fun reenterFromRandomBoundary() {
        // Pick a random edge: 0 = Left, 1 = Right, 2 = Top, 3 = Bottom
        val edge = (Math.random() * 4).toInt()
        val targetCenterX = 0.5f + (Math.random() * 0.3f - 0.15f).toFloat()
        val targetCenterY = 0.5f + (Math.random() * 0.3f - 0.15f).toFloat()

        when (edge) {
            0 -> { // Left Edge -> swimming Right
                jellyX = -0.20f
                jellyY = (0.2f + Math.random() * 0.6f).toFloat()
            }
            1 -> { // Right Edge -> swimming Left
                jellyX = 1.20f
                jellyY = (0.2f + Math.random() * 0.6f).toFloat()
            }
            2 -> { // Top Edge -> swimming Down
                jellyX = (0.2f + Math.random() * 0.6f).toFloat()
                jellyY = -0.20f
            }
            3 -> { // Bottom Edge -> swimming Up
                jellyX = (0.2f + Math.random() * 0.6f).toFloat()
                jellyY = 1.20f
            }
        }

        // Set heading aimed inward toward the screen center with a subtle variation <= 15 deg
        val inwardAngle = atan2(targetCenterY - jellyY, targetCenterX - jellyX)
        val jitter = ((Math.random() - 0.5) * 0.25).toFloat() // +/- 7 deg
        targetHeading = inwardAngle + jitter
        jellyHeading = targetHeading

        // Seamlessly reset tentacle joints behind the entry point so they stream smoothly
        val backDirX = -cos(jellyHeading)
        val backDirY = -sin(jellyHeading)
        for (t in 0 until numTentacles) {
            val joints = tentacleJoints[t]
            for (j in 0 until jointsPerTentacle) {
                joints[j] = Offset(
                    jellyX * 1000f + backDirX * (j * 15f),
                    jellyY * 1000f + backDirY * (j * 15f)
                )
            }
        }
    }

    private fun renderSpores(drawScope: DrawScope, state: ControlState, width: Float, height: Float) {
        val baseHue = state.primaryHue
        for (spore in spores) {
            spore.x = (spore.x + spore.vx + 1f) % 1f
            spore.y = (spore.y + spore.vy + 1f) % 1f

            val sx = spore.x * width
            val sy = spore.y * height
            val color = Color.hsv((baseHue + spore.hueOffset) % 360f, 0.7f, 1.0f).copy(alpha = spore.alpha)

            drawScope.drawCircle(
                color = color,
                radius = spore.size,
                center = Offset(sx, sy)
            )
        }
    }

    private fun emitFocusSparkles(centerX: Float, centerY: Float, baseHue: Float) {
        if (sparkles.size > 140) return
        for (i in 0..3) {
            val angle = (Math.random() * 2.0 * PI).toFloat()
            val spd = (35f + Math.random() * 110f).toFloat()
            val hue = (baseHue + (Math.random() * 80f - 40f).toFloat() + 360f) % 360f
            sparkles.add(
                Sparkle(
                    x = centerX + (Math.random() * 80f - 40f).toFloat(),
                    y = centerY + (Math.random() * 60f - 30f).toFloat(),
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd,
                    life = 1.0f,
                    maxLife = (0.7f + Math.random() * 0.6f).toFloat(),
                    size = (2.8f + Math.random() * 4.2f).toFloat(),
                    color = Color.hsv(hue, 0.4f, 1.0f)
                )
            )
        }
    }

    private fun renderSparkles(drawScope: DrawScope, dt: Float) {
        val it = sparkles.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life -= dt / s.maxLife
            if (s.life <= 0f) {
                it.remove()
                continue
            }

            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vy += 12f * dt

            val alpha = (s.life).coerceIn(0f, 1f)
            drawScope.drawCircle(
                color = s.color.copy(alpha = alpha),
                radius = s.size * alpha,
                center = Offset(s.x, s.y)
            )
        }
    }

    private fun render3DJellyfish(
        drawScope: DrawScope,
        state: ControlState,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float
    ) {
        val baseHue = state.primaryHue
        val pulsePhase = (animTime * state.strobeFrequencyHz.coerceIn(0.2f, 3.0f) * 2.0 * PI)
        val pulseVal = ((sin(pulsePhase) + 1.0) / 2.0).toFloat()

        // Responsive Scale for prominent 3D appearance
        val minDim = width.coerceAtMost(height)
        val baseScale = minDim * 0.17f

        val bellRadiusX = baseScale * (1f + (1f - pulseVal) * 0.28f)
        val bellRadiusY = baseScale * 0.82f * (1f + pulseVal * 0.28f)
        val focusGlowBoost = if (isCurrentlyFocused) 0.50f else 0.0f

        val rotationDeg = (jellyHeading * (180f / PI.toFloat()) + 90f) + jellyTiltZ

        drawScope.rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
            val topApexY = centerY - bellRadiusY * 0.90f
            val rimBaseY = centerY + bellRadiusY * 0.28f

            // A. Deep Ambient Bioluminescent Glow Fog (Outer Volumetric Bloom)
            val glowColor = Color.hsv(baseHue, 0.8f, 1.0f)
            val auraRadius = bellRadiusX * (2.0f + focusGlowBoost)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.52f + focusGlowBoost),
                        Color.hsv((baseHue + 40f) % 360f, 0.85f, 0.95f).copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY - bellRadiusY * 0.1f),
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = Offset(centerX, centerY - bellRadiusY * 0.1f)
            )

            // B. BACK LAYER SILKY FILAMENTS (Depth z < 0) - Rendered behind the dome
            renderTentacleLayer(drawScope, baseHue, centerX, rimBaseY, bellRadiusX, baseScale, pulseVal, focusGlowBoost, isFront = false)

            // C. 3D Subumbrella Cavity (Deep Translucent Interior Cavern)
            val cavityPath = Path().apply {
                val rimRx = bellRadiusX * 0.94f
                val rimRy = bellRadiusY * 0.32f
                moveTo(centerX - rimRx, rimBaseY)
                cubicTo(
                    centerX - rimRx, rimBaseY - rimRy * 2.4f,
                    centerX + rimRx, rimBaseY - rimRy * 2.4f,
                    centerX + rimRx, rimBaseY
                )
                cubicTo(
                    centerX + rimRx, rimBaseY + rimRy * 1.4f,
                    centerX - rimRx, rimBaseY + rimRy * 1.4f,
                    centerX - rimRx, rimBaseY
                )
                close()
            }
            drawScope.drawPath(
                path = cavityPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF880E4F).copy(alpha = 0.85f), // Deep Rose/Magenta Internal Shadow
                        Color.hsv((baseHue + 60f) % 360f, 0.95f, 0.55f).copy(alpha = 0.65f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, rimBaseY - bellRadiusY * 0.2f),
                    radius = bellRadiusX
                )
            )

            // D. Inner Bioluminescent Gonads (4 Glowing Rose/Magenta Horseshoe Rings matching reference visual)
            val gonadRadius = (baseScale * 0.32f + pulseVal * 12f) * (1f + focusGlowBoost * 0.45f)
            val gonadCenter = Offset(centerX, centerY - bellRadiusY * 0.22f)
            val magentaHue = 330f // Vibrant Magenta/Pink like Gemini generated figure

            for (i in 0 until 4) {
                val ang = (i * PI * 0.5 + animTime * 0.4).toFloat()
                val ox = gonadCenter.x + cos(ang) * (gonadRadius * 0.62f)
                val oy = gonadCenter.y + sin(ang) * (gonadRadius * 0.48f)

                // Horseshoe organ ring
                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF80AB).copy(alpha = 0.98f), // Soft luminous pink highlight
                            Color.hsv(magentaHue, 0.90f, 1.0f).copy(alpha = 0.88f),
                            Color.Transparent
                        ),
                        center = Offset(ox, oy),
                        radius = gonadRadius * 0.58f
                    ),
                    radius = gonadRadius * 0.58f,
                    center = Offset(ox, oy)
                )
            }

            // Central Luminous Manubrium Core
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f),
                        Color(0xFF00E5FF).copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    center = gonadCenter,
                    radius = gonadRadius * 0.45f
                ),
                radius = gonadRadius * 0.45f,
                center = gonadCenter
            )

            // E. Cascading Ruffled Silk Oral Arms (4 Central Lacy Curtains with Flutter)
            for (arm in 0 until 4) {
                val armPath = Path()
                val armOffset = (arm - 1.5f) * (bellRadiusX * 0.24f)
                val startX = centerX + armOffset
                val startY = rimBaseY - 6f

                armPath.moveTo(startX, startY)
                val armLen = baseScale * 1.75f + (arm % 2) * 45f
                val segments = 12
                for (s in 1..segments) {
                    val progress = s.toFloat() / segments
                    val wave = sin(animTime * 4.8f + arm * 1.3f + s * 0.75f) * (20f * (1f + progress))
                    val curX = startX + wave
                    val curY = startY + progress * armLen
                    armPath.lineTo(curX, curY)
                }

                // Ruffled silk gradient (Magenta to Cyan)
                val armColor = if (arm % 2 == 0) Color(0xFFFF4081) else Color(0xFF00E5FF)
                drawScope.drawPath(
                    path = armPath,
                    color = armColor.copy(alpha = (0.68f + focusGlowBoost).coerceIn(0.2f, 0.98f)),
                    style = Stroke(width = 6.5f - arm * 0.6f, cap = StrokeCap.Round)
                )
            }

            // F. Translucent 3D Glass Dome (Outer Exumbrella Cap with Glassmorphic Shading)
            val outerCapPath = Path().apply {
                val leftX = centerX - bellRadiusX
                val rightX = centerX + bellRadiusX
                val bottomY = rimBaseY

                moveTo(leftX, bottomY)
                // Left profile curve up to crown
                cubicTo(
                    leftX * 0.94f + centerX * 0.06f, centerY - bellRadiusY * 0.52f,
                    centerX - bellRadiusX * 0.52f, topApexY,
                    centerX, topApexY
                )
                // Right profile curve down to margin
                cubicTo(
                    centerX + bellRadiusX * 0.52f, topApexY,
                    rightX * 0.94f + centerX * 0.06f, centerY - bellRadiusY * 0.52f,
                    rightX, bottomY
                )
                // Undulating Scalloped Lappet Margin (16 rim scallops)
                val scallops = 16
                val scallopW = (bellRadiusX * 2f) / scallops
                for (sc in scallops downTo 1) {
                    val x1 = leftX + (sc - 0.5f) * scallopW
                    val y1 = bottomY - 6.5f * sin((sc.toFloat() / scallops) * PI.toFloat() * 2f + animTime * 4f)
                    val x2 = leftX + (sc - 1f) * scallopW
                    val y2 = bottomY
                    quadraticTo(x1, y1, x2, y2)
                }
                close()
            }

            // 3D Translucent Glass Shading (Photorealistic Cyan -> Soft Rose/Violet Gradient with Specular Crown)
            val capGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f + focusGlowBoost * 0.15f), // Specular Crown
                    Color(0xFF80DEEA).copy(alpha = 0.65f + focusGlowBoost * 0.2f),
                    Color(0xFF00E5FF).copy(alpha = 0.45f),
                    Color(0xFFAB47BC).copy(alpha = 0.35f), // Rose violet sub-surface hue
                    Color(0xFF1A237E).copy(alpha = 0.20f)
                ),
                center = Offset(centerX, topApexY + bellRadiusY * 0.28f),
                radius = bellRadiusX * 1.2f
            )
            drawScope.drawPath(path = outerCapPath, brush = capGradient)

            // G. 3D Radial Meridians (16 Luminous Neural Fibers / Striations)
            for (m in 0..16) {
                val u = (m.toFloat() / 16f - 0.5f) * 2f // -1.0 to 1.0
                val meridianX = centerX + u * (bellRadiusX * 0.92f)
                val ribPath = Path().apply {
                    moveTo(centerX, topApexY + 4f)
                    cubicTo(
                        centerX * 0.35f + meridianX * 0.65f, topApexY + bellRadiusY * 0.36f,
                        meridianX, centerY,
                        meridianX, rimBaseY
                    )
                }
                val ribAlpha = (1f - abs(u) * 0.45f) * (0.55f + focusGlowBoost * 0.3f)
                drawScope.drawPath(
                    path = ribPath,
                    color = Color.White.copy(alpha = ribAlpha.coerceIn(0.18f, 0.98f)),
                    style = Stroke(width = if (m % 4 == 0) 2.6f else 1.4f, cap = StrokeCap.Round)
                )
            }

            // H. Glowing Neon Rim Margin Stroke & Marginal Lappet Beads
            val rimGlowColor = Color(0xFF00E5FF).copy(alpha = 0.98f)
            drawScope.drawPath(path = outerCapPath, color = rimGlowColor, style = Stroke(width = 3.8f))

            // I. FOREGROUND LAYER SILKY FILAMENTS (Depth z > 0)
            renderTentacleLayer(drawScope, baseHue, centerX, rimBaseY, bellRadiusX, baseScale, pulseVal, focusGlowBoost, isFront = true)
        }
    }

    private fun renderTentacleLayer(
        drawScope: DrawScope,
        baseHue: Float,
        centerX: Float,
        rimBaseY: Float,
        bellRadiusX: Float,
        baseScale: Float,
        pulseVal: Float,
        focusGlowBoost: Float,
        isFront: Boolean
    ) {
        val halfTentacles = numTentacles / 2
        val startIdx = if (isFront) halfTentacles else 0
        val endIdx = if (isFront) numTentacles else halfTentacles

        for (t in startIdx until endIdx) {
            val tFrac = t.toFloat() / (numTentacles - 1)
            val angleOnRim = tFrac * 2.0 * PI
            val rootX = centerX + (cos(angleOnRim) * (bellRadiusX * 0.88f)).toFloat()
            val rootY = rimBaseY + (sin(angleOnRim) * 10f).toFloat()

            val joints = tentacleJoints[t]
            val tentacleLength = baseScale * 2.4f + (t % 7) * 35f
            val segmentLen = tentacleLength / jointsPerTentacle

            // Smooth initialization / teleport safety
            val distToRoot = hypot(joints[1].x - rootX, joints[1].y - rootY)
            if (distToRoot > segmentLen * 4f || (joints[1].x == 0.5f && joints[1].y == 0.5f) || joints[1] == Offset.Zero) {
                for (j in 0 until jointsPerTentacle) {
                    joints[j] = Offset(rootX, rootY + j * segmentLen)
                }
            } else {
                joints[0] = Offset(rootX, rootY)
            }

            val tentaclePath = Path()
            tentaclePath.moveTo(rootX, rootY)

            for (j in 1 until jointsPerTentacle) {
                val prevJoint = joints[j - 1]
                val wavePhase = animTime * 4.2f + t * 0.35f + j * 0.45f
                val waveAmplitude = (7.5f + j * 2.4f) * (1f + (1f - pulseVal) * 0.35f)
                val waveX = sin(wavePhase) * waveAmplitude
                val dragY = segmentLen + (1f - pulseVal) * 4.5f

                val targetX = prevJoint.x + waveX * 0.35f
                val targetY = prevJoint.y + dragY

                // Damped spring physics (silky smooth flow)
                val currentJ = joints[j]
                val newX = currentJ.x + (targetX - currentJ.x) * 0.38f
                val newY = currentJ.y + (targetY - currentJ.y) * 0.38f
                joints[j] = Offset(newX, newY)

                tentaclePath.lineTo(newX, newY)
            }

            // Silky fiber-optic gradient (Cyan -> Magenta -> Violet)
            val isPinkStrand = (t % 3 == 0)
            val filamentColor = if (isPinkStrand) Color(0xFFFF4081) else Color.hsv((baseHue + t * 8f) % 360f, if (isFront) 0.65f else 0.85f, 1.0f)
            val baseAlpha = if (isFront) (0.80f + focusGlowBoost) else (0.35f + focusGlowBoost * 0.5f)

            drawScope.drawPath(
                path = tentaclePath,
                color = filamentColor.copy(alpha = baseAlpha.coerceIn(0.18f, 1.0f)),
                style = Stroke(width = if (isFront) 2.0f else 1.2f, cap = StrokeCap.Round)
            )

            // Micro glowing light bead traveling along the tentacle
            val beadJointIdx = ((animTime * 7f + t) % jointsPerTentacle).toInt().coerceIn(1, jointsPerTentacle - 1)
            val beadPos = joints[beadJointIdx]
            drawScope.drawCircle(
                color = Color.White.copy(alpha = if (isFront) 0.98f else 0.55f),
                radius = if (isFront) 2.8f else 1.8f,
                center = beadPos
            )
        }
    }

    private fun renderGazeReticleSmall(
        drawScope: DrawScope,
        gazeX: Float,
        gazeY: Float,
        isFocused: Boolean,
        baseHue: Float
    ) {
        // Scaled to 0.3x (delicate, non-intrusive)
        val reticleColor = if (isFocused) Color(0xFF00E676) else Color.hsv(baseHue, 0.85f, 1.0f)
        val radius = if (isFocused) 6.5f else 4.2f

        // Soft outer glow ring
        drawScope.drawCircle(
            color = reticleColor.copy(alpha = if (isFocused) 0.65f else 0.30f),
            radius = radius + 3.0f,
            center = Offset(gazeX, gazeY),
            style = Stroke(width = 1.0f)
        )

        // Center reticle dot
        drawScope.drawCircle(
            color = reticleColor.copy(alpha = 0.98f),
            radius = if (isFocused) 2.0f else 1.3f,
            center = Offset(gazeX, gazeY)
        )

        // Delicate Crosshair ticks
        val tickLen = 2.5f
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX - radius - tickLen, gazeY),
            end = Offset(gazeX - radius + 1f, gazeY),
            strokeWidth = 1.0f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX + radius - 1f, gazeY),
            end = Offset(gazeX + radius + tickLen, gazeY),
            strokeWidth = 1.0f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX, gazeY - radius - tickLen),
            end = Offset(gazeX, gazeY - radius + 1f),
            strokeWidth = 1.0f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX, gazeY + radius - 1f),
            end = Offset(gazeX, gazeY + radius + tickLen),
            strokeWidth = 1.0f
        )
    }
}
