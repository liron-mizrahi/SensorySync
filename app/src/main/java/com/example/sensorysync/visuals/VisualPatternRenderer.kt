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

    // Multi-depth oceanic bokeh spores (Foreground soft bokeh + background sharp stardust)
    private class BokehParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Float,
        var isPink: Boolean,
        var isForegroundBokeh: Boolean
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

    // 64 Ultra-fine Gossamer Filaments with 26 joints for fluid S-curves, loops, and tip curls
    private val numTentacles = 64
    private val jointsPerTentacle = 26
    private val tentacleJoints = Array(numTentacles) { Array(jointsPerTentacle) { Offset(0.5f, 0.5f) } }

    private val bokehParticles = List(180) {
        val isFg = Math.random() < 0.15
        BokehParticle(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            vx = ((Math.random() - 0.5) * 0.00018).toFloat(),
            vy = ((Math.random() - 0.5) * 0.00018).toFloat(),
            radius = if (isFg) (4.5f + Math.random() * 8.0f).toFloat() else (0.8f + Math.random() * 2.2f).toFloat(),
            alpha = if (isFg) (0.10f + Math.random() * 0.25f).toFloat() else (0.20f + Math.random() * 0.55f).toFloat(),
            isPink = Math.random() < 0.28,
            isForegroundBokeh = isFg
        )
    }

    private val sparkles = mutableListOf<Sparkle>()

    private var animTime = 0f

    // Organic swimming trajectory with small angles (<= 20 deg) and screen re-entry
    private var jellyX = 0.5f
    private var jellyY = 0.5f
    private var jellyVx = 0f
    private var jellyVy = 0f
    private var jellyHeading = -0.45f
    private var targetHeading = -0.45f
    private var swimBaseSpeed = 0.0011f
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

        // 1. Update Autonomous Trajectory
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

        val isOnScreen = jellyX in -0.05f..1.05f && jellyY in -0.05f..1.05f
        isCurrentlyFocused = isOnScreen && state.gazeData.isFaceDetected && gazeJellyDistance < 0.22f

        // 3. Render Oceanic Atmospheric Vignette & Background Bokeh
        renderAtmosphericBackground(drawScope, width, height)

        // 4. Render Focus Reward Starbursts
        if (isCurrentlyFocused) {
            emitFocusSparkles(jx, jy)
        }
        renderSparkles(drawScope, dt)

        // 5. Render Photorealistic Bioluminescent Jellyfish (Matching Reference Image)
        renderPhotorealisticJellyfish(drawScope, state, jx, jy, width, height)

        // 6. Render Foreground Floating Bokeh Orbs
        renderForegroundBokeh(drawScope, width, height)

        // 7. Render Real-Time Eye Gaze Tracking Point (Scaled to 0.3x)
        if (state.gazeData.isFaceDetected) {
            renderGazeReticleSmall(drawScope, gazePx, gazePy, isCurrentlyFocused)
        }
    }

    private fun updateJellyfishMovement(dt: Float, speedMul: Float, pulseFreq: Float) {
        val freq = pulseFreq.coerceIn(0.2f, 3.0f)
        val pulsePhase = (animTime * freq * 2.0 * PI)
        val pulsePower = ((sin(pulsePhase) + 1.0) / 2.0).toFloat()

        // Off-screen detection and smooth re-entry (< 3s)
        val isOffScreen = jellyX < -0.22f || jellyX > 1.22f || jellyY < -0.22f || jellyY > 1.22f

        if (isOffScreen) {
            timeOffScreenSec += dt
            if (timeOffScreenSec >= 2.0f) {
                reenterFromRandomBoundary()
                timeOffScreenSec = 0f
            }
        } else {
            timeOffScreenSec = 0f
        }

        // Gentle organic path undulation: strictly capped to small angle turns <= 20 deg
        val baseWanderDelta = (sin(animTime * 0.20f) * 0.12f + cos(animTime * 0.14f) * 0.08f)
        val desiredAngle = targetHeading + baseWanderDelta

        val maxTurnRate = 0.25f
        var angleDiff = desiredAngle - jellyHeading
        while (angleDiff > PI) angleDiff -= (2.0 * PI).toFloat()
        while (angleDiff < -PI) angleDiff += (2.0 * PI).toFloat()

        val maxStep = maxTurnRate * dt
        val clampedTurn = angleDiff.coerceIn(-maxStep, maxStep)
        jellyHeading += clampedTurn

        // Smooth forward propulsion with pulse power stroke
        val thrust = if (pulsePower > 0.35f) ((pulsePower - 0.35f) * 1.5f) else 0.15f
        val currentSpeed = swimBaseSpeed * speedMul * (0.5f + thrust)

        val desiredVx = cos(jellyHeading) * currentSpeed * 1.25f
        val desiredVy = sin(jellyHeading) * currentSpeed * 0.85f

        val accel = (2.0f * dt).coerceIn(0.01f, 0.2f)
        jellyVx += (desiredVx - jellyVx) * accel
        jellyVy += (desiredVy - jellyVy) * accel

        jellyX += jellyVx
        jellyY += jellyVy

        val targetTilt = (clampedTurn / maxStep.coerceAtLeast(0.001f)) * 5.0f
        jellyTiltZ += (targetTilt - jellyTiltZ) * (2.0f * dt)
    }

    private fun reenterFromRandomBoundary() {
        val edge = (Math.random() * 4).toInt()
        val targetCenterX = 0.5f + (Math.random() * 0.3f - 0.15f).toFloat()
        val targetCenterY = 0.5f + (Math.random() * 0.3f - 0.15f).toFloat()

        when (edge) {
            0 -> {
                jellyX = -0.20f
                jellyY = (0.2f + Math.random() * 0.6f).toFloat()
            }
            1 -> {
                jellyX = 1.20f
                jellyY = (0.2f + Math.random() * 0.6f).toFloat()
            }
            2 -> {
                jellyX = (0.2f + Math.random() * 0.6f).toFloat()
                jellyY = -0.20f
            }
            3 -> {
                jellyX = (0.2f + Math.random() * 0.6f).toFloat()
                jellyY = 1.20f
            }
        }

        val inwardAngle = atan2(targetCenterY - jellyY, targetCenterX - jellyX)
        val jitter = ((Math.random() - 0.5) * 0.22).toFloat()
        targetHeading = inwardAngle + jitter
        jellyHeading = targetHeading

        val backDirX = -cos(jellyHeading)
        val backDirY = -sin(jellyHeading)
        for (t in 0 until numTentacles) {
            val joints = tentacleJoints[t]
            for (j in 0 until jointsPerTentacle) {
                joints[j] = Offset(
                    jellyX * 1000f + backDirX * (j * 14f),
                    jellyY * 1000f + backDirY * (j * 14f)
                )
            }
        }
    }

    private fun renderAtmosphericBackground(drawScope: DrawScope, width: Float, height: Float) {
        // Deep oceanic background particles
        for (p in bokehParticles) {
            if (p.isForegroundBokeh) continue
            p.x = (p.x + p.vx + 1f) % 1f
            p.y = (p.y + p.vy + 1f) % 1f

            val sx = p.x * width
            val sy = p.y * height
            val color = if (p.isPink) Color(0xFFFF80AB).copy(alpha = p.alpha * 0.65f) else Color(0xFF80DEEA).copy(alpha = p.alpha * 0.85f)

            drawScope.drawCircle(
                color = color,
                radius = p.radius,
                center = Offset(sx, sy)
            )
        }
    }

    private fun renderForegroundBokeh(drawScope: DrawScope, width: Float, height: Float) {
        // Soft, out-of-focus foreground bokeh orbs
        for (p in bokehParticles) {
            if (!p.isForegroundBokeh) continue
            p.x = (p.x + p.vx * 1.5f + 1f) % 1f
            p.y = (p.y + p.vy * 1.5f + 1f) % 1f

            val sx = p.x * width
            val sy = p.y * height
            val color = if (p.isPink) Color(0xFFFF4081) else Color(0xFF00E5FF)

            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = p.alpha * 0.8f),
                        color.copy(alpha = p.alpha * 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(sx, sy),
                    radius = p.radius
                ),
                radius = p.radius,
                center = Offset(sx, sy)
            )
        }
    }

    private fun emitFocusSparkles(centerX: Float, centerY: Float) {
        if (sparkles.size > 140) return
        for (i in 0..3) {
            val angle = (Math.random() * 2.0 * PI).toFloat()
            val spd = (35f + Math.random() * 110f).toFloat()
            val isCyan = Math.random() < 0.6
            val color = if (isCyan) Color(0xFF80DEEA) else Color(0xFFFF80AB)
            sparkles.add(
                Sparkle(
                    x = centerX + (Math.random() * 80f - 40f).toFloat(),
                    y = centerY + (Math.random() * 60f - 30f).toFloat(),
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd,
                    life = 1.0f,
                    maxLife = (0.7f + Math.random() * 0.6f).toFloat(),
                    size = (2.5f + Math.random() * 3.8f).toFloat(),
                    color = color
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

    private fun renderPhotorealisticJellyfish(
        drawScope: DrawScope,
        state: ControlState,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float
    ) {
        val pulsePhase = (animTime * state.strobeFrequencyHz.coerceIn(0.2f, 3.0f) * 2.0 * PI)
        val pulseVal = ((sin(pulsePhase) + 1.0) / 2.0).toFloat()

        val minDim = width.coerceAtMost(height)
        val baseScale = minDim * 0.18f

        val bellRadiusX = baseScale * (1f + (1f - pulseVal) * 0.18f)
        val bellRadiusY = baseScale * 0.92f * (1f + pulseVal * 0.18f)
        val rimRy = bellRadiusY * 0.28f
        val focusGlowBoost = if (isCurrentlyFocused) 0.45f else 0.0f

        val rotationDeg = (jellyHeading * (180f / PI.toFloat()) + 90f) + jellyTiltZ

        drawScope.rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
            val topApexY = centerY - bellRadiusY * 0.95f
            val rimBaseY = centerY + bellRadiusY * 0.20f

            // A. Atmospheric Cyan Volumetric Halo (Soft photographic bloom)
            val haloRadius = bellRadiusX * (2.4f + focusGlowBoost)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.42f + focusGlowBoost),
                        Color(0xFF0097A7).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY - bellRadiusY * 0.18f),
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = Offset(centerX, centerY - bellRadiusY * 0.18f)
            )

            // B. BACK LAYER GOSSAMER FILAMENTS (Depth z < 0)
            renderGossamerTentacles(drawScope, centerX, rimBaseY, bellRadiusX, rimRy, baseScale, pulseVal, focusGlowBoost, isFront = false)

            // C. 3D Subumbrella Interior Cavern with Rose/Violet Luminescence
            val cavityPath = Path().apply {
                val rimRx = bellRadiusX * 0.92f
                moveTo(centerX - rimRx, rimBaseY)
                cubicTo(
                    centerX - rimRx, rimBaseY - rimRy * 2.4f,
                    centerX + rimRx, rimBaseY - rimRy * 2.4f,
                    centerX + rimRx, rimBaseY
                )
                // 3D elliptical under-margin
                cubicTo(
                    centerX + rimRx * 0.5f, rimBaseY + rimRy * 1.1f,
                    centerX - rimRx * 0.5f, rimBaseY + rimRy * 1.1f,
                    centerX - rimRx, rimBaseY
                )
                close()
            }
            drawScope.drawPath(
                path = cavityPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE91E63).copy(alpha = 0.70f), // Luminous Magenta Interior
                        Color(0xFF880E4F).copy(alpha = 0.48f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, rimBaseY - bellRadiusY * 0.22f),
                    radius = bellRadiusX * 0.95f
                )
            )

            // D. 4 Luminous Horseshoe Gonad Organs (Matching Reference Visual)
            val gonadRadius = (baseScale * 0.32f + pulseVal * 8f) * (1f + focusGlowBoost * 0.35f)
            val gonadCenter = Offset(centerX, centerY - bellRadiusY * 0.26f)
            for (i in 0 until 4) {
                val ang = (i * PI * 0.5 + animTime * 0.25).toFloat()
                val ox = gonadCenter.x + cos(ang) * (gonadRadius * 0.65f)
                val oy = gonadCenter.y + sin(ang) * (gonadRadius * 0.48f)

                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF80AB).copy(alpha = 0.98f),
                            Color(0xFFE91E63).copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        center = Offset(ox, oy),
                        radius = gonadRadius * 0.62f
                    ),
                    radius = gonadRadius * 0.62f,
                    center = Offset(ox, oy)
                )
            }

            // E. Cascading Ruffled Chiffon Silk Oral Arms (Flowing Cloth with Highlights)
            renderRuffledChiffonOralArms(drawScope, centerX, rimBaseY, bellRadiusX, baseScale, pulseVal, focusGlowBoost)

            // F. Translucent 3D Parabolic Glass Bell (Soft Photographic Dome, No Hard Stroke)
            val outerCapPath = Path().apply {
                val leftX = centerX - bellRadiusX
                val rightX = centerX + bellRadiusX
                val bottomY = rimBaseY

                moveTo(leftX, bottomY)
                // Left dome curve up to crown
                cubicTo(
                    leftX * 0.94f + centerX * 0.06f, centerY - bellRadiusY * 0.58f,
                    centerX - bellRadiusX * 0.55f, topApexY,
                    centerX, topApexY
                )
                // Right dome curve down to margin
                cubicTo(
                    centerX + bellRadiusX * 0.55f, topApexY,
                    rightX * 0.94f + centerX * 0.06f, centerY - bellRadiusY * 0.58f,
                    rightX, bottomY
                )
                // 3D Elliptical lower rim
                cubicTo(
                    centerX + bellRadiusX * 0.5f, bottomY + rimRy * 1.1f,
                    centerX - bellRadiusX * 0.5f, bottomY + rimRy * 1.1f,
                    leftX, bottomY
                )
                close()
            }

            // Layer 1: Base Translucent Glass Dome with Cyan Luminescence & Lavender/Rose Subsurface Tint
            val capGradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE0F7FA).copy(alpha = 0.92f + focusGlowBoost * 0.08f), // Apex Specular Crown
                    Color(0xFF00E5FF).copy(alpha = 0.75f + focusGlowBoost * 0.15f), // Cyan Luminescence
                    Color(0xFF4DD0E1).copy(alpha = 0.58f),
                    Color(0xFFF48FB1).copy(alpha = 0.42f), // Rose Subsurface Tint
                    Color(0xFFCE93D8).copy(alpha = 0.28f)  // Lavender Edge
                ),
                center = Offset(centerX, topApexY + bellRadiusY * 0.28f),
                radius = bellRadiusX * 1.18f
            )
            drawScope.drawPath(path = outerCapPath, brush = capGradient)

            // Layer 2: Soft Photographic Edge Bloom (Multi-pass feathered edge instead of hard stroke)
            drawScope.drawPath(
                path = outerCapPath,
                color = Color(0xFF00E5FF).copy(alpha = 0.45f + focusGlowBoost * 0.2f),
                style = Stroke(width = 4.0f)
            )
            drawScope.drawPath(
                path = outerCapPath,
                color = Color(0xFFE0F7FA).copy(alpha = 0.85f),
                style = Stroke(width = 1.4f)
            )

            // G. 24 Fine 3D Radial Parachute Meridians (Curved Neural Striations)
            val numMeridians = 24
            for (m in 0..numMeridians) {
                val u = (m.toFloat() / numMeridians - 0.5f) * 2f // -1.0 to 1.0
                val meridianX = centerX + u * (bellRadiusX * 0.92f)
                val ribPath = Path().apply {
                    moveTo(centerX, topApexY + 2f)
                    cubicTo(
                        centerX * 0.28f + meridianX * 0.72f, topApexY + bellRadiusY * 0.35f,
                        meridianX, centerY - bellRadiusY * 0.1f,
                        meridianX, rimBaseY + (1f - u * u) * (rimRy * 0.55f)
                    )
                }
                val ribAlpha = (1f - abs(u) * 0.4f) * (0.45f + focusGlowBoost * 0.3f)
                drawScope.drawPath(
                    path = ribPath,
                    color = Color(0xFFE0F7FA).copy(alpha = ribAlpha.coerceIn(0.10f, 0.92f)),
                    style = Stroke(width = if (m % 4 == 0) 1.8f else 1.0f, cap = StrokeCap.Round)
                )
            }

            // H. FOREGROUND LAYER GOSSAMER FILAMENTS (Depth z > 0)
            renderGossamerTentacles(drawScope, centerX, rimBaseY, bellRadiusX, rimRy, baseScale, pulseVal, focusGlowBoost, isFront = true)
        }
    }

    private fun renderRuffledChiffonOralArms(
        drawScope: DrawScope,
        centerX: Float,
        rimBaseY: Float,
        bellRadiusX: Float,
        baseScale: Float,
        pulseVal: Float,
        focusGlowBoost: Float
    ) {
        val numArms = 3
        val armLength = baseScale * 3.0f

        for (a in 0 until numArms) {
            val armOffset = (a - 1f) * (bellRadiusX * 0.28f)
            val startX = centerX + armOffset
            val startY = rimBaseY - 8f

            val leftRibbonPath = Path()
            val rightRibbonPath = Path()
            val centerSpine = mutableListOf<Offset>()

            val segments = 20
            for (s in 0..segments) {
                val progress = s.toFloat() / segments
                val wave1 = sin(animTime * 3.5f + a * 1.5f + progress * 4.2f) * (26f * (progress + 0.3f))
                val wave2 = cos(animTime * 2.2f + a * 1.1f + progress * 3.0f) * (16f * (progress + 0.3f))
                val curX = startX + wave1 + wave2
                val curY = startY + progress * armLength
                centerSpine.add(Offset(curX, curY))
            }

            val ribbonWidth = baseScale * 0.24f * (1f - (a % 2) * 0.2f)
            leftRibbonPath.moveTo(centerSpine[0].x - ribbonWidth * 0.5f, centerSpine[0].y)
            rightRibbonPath.moveTo(centerSpine[0].x + ribbonWidth * 0.5f, centerSpine[0].y)

            for (s in 1..segments) {
                val pt = centerSpine[s]
                val progress = s.toFloat() / segments
                // Multi-octave organic frill noise
                val ruff = sin(animTime * 5.0f + s * 0.9f + a) * 7.5f + cos(animTime * 8.0f + s * 1.8f) * 3.5f
                val w = (ribbonWidth * (1f - progress * 0.65f) + ruff).coerceAtLeast(3.5f)

                leftRibbonPath.lineTo(pt.x - w, pt.y)
                rightRibbonPath.lineTo(pt.x + w, pt.y)
            }

            val closedRibbon = Path().apply {
                addPath(leftRibbonPath)
                for (s in segments downTo 0) {
                    val pt = centerSpine[s]
                    val progress = s.toFloat() / segments
                    val ruff = sin(animTime * 5.0f + s * 0.9f + a) * 7.5f + cos(animTime * 8.0f + s * 1.8f) * 3.5f
                    val w = (ribbonWidth * (1f - progress * 0.65f) + ruff).coerceAtLeast(3.5f)
                    lineTo(pt.x + w, pt.y)
                }
                close()
            }

            // Layer 1: Semi-transparent Chiffon Fill (Soft Lavender/Rose)
            val silkColor = if (a == 1) Color(0xFFF48FB1) else Color(0xFFCE93D8)
            drawScope.drawPath(
                path = closedRibbon,
                color = silkColor.copy(alpha = (0.32f + focusGlowBoost * 0.2f).coerceIn(0.1f, 0.75f))
            )

            // Layer 2: Glowing Ruffled Frill Edges (Cyan & Rose)
            drawScope.drawPath(
                path = leftRibbonPath,
                color = Color(0xFF80DEEA).copy(alpha = 0.80f + focusGlowBoost * 0.2f),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
            )
            drawScope.drawPath(
                path = rightRibbonPath,
                color = Color(0xFFFF80AB).copy(alpha = 0.80f + focusGlowBoost * 0.2f),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
            )
        }
    }

    private fun renderGossamerTentacles(
        drawScope: DrawScope,
        centerX: Float,
        rimBaseY: Float,
        bellRadiusX: Float,
        rimRy: Float,
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
            val rootX = centerX + (cos(angleOnRim) * (bellRadiusX * 0.90f)).toFloat()
            val rootY = rimBaseY + (sin(angleOnRim) * rimRy).toFloat()

            val joints = tentacleJoints[t]
            // Varied organic lengths (matching reference where tentacles trail in clusters)
            val lengthFactor = 2.8f + ((t * 7) % 13) * 0.15f
            val tentacleLength = baseScale * lengthFactor
            val segmentLen = tentacleLength / jointsPerTentacle

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
                val jFrac = j.toFloat() / jointsPerTentacle

                // Multi-octave wave eddies
                val wave1 = sin(animTime * 3.2f + t * 0.25f + j * 0.32f) * (4.5f + j * 2.4f)
                val wave2 = cos(animTime * 1.8f + t * 0.55f + j * 0.45f) * (3.0f + j * 1.6f)

                // Organic Tip Curls & Eddies (sweeping swirls near the tail)
                val tipCurlX = if (j > 16) cos(animTime * 2.2f + t * 0.8f) * (j - 16) * 2.2f else 0f
                val tipCurlY = if (j > 16) -sin(animTime * 2.2f + t * 0.8f) * (j - 16) * 1.8f else 0f

                val waveAmplitude = (1f + (1f - pulseVal) * 0.35f)
                val waveX = (wave1 + wave2 + tipCurlX) * waveAmplitude
                val dragY = segmentLen + (1f - pulseVal) * 3.6f + tipCurlY

                val targetX = prevJoint.x + waveX * 0.28f
                val targetY = prevJoint.y + dragY

                val currentJ = joints[j]
                val newX = currentJ.x + (targetX - currentJ.x) * 0.32f
                val newY = currentJ.y + (targetY - currentJ.y) * 0.32f
                joints[j] = Offset(newX, newY)

                tentaclePath.lineTo(newX, newY)
            }

            // Dual-tone color matching reference: Outer Cyan vs Inner Rose/Violet
            val isPinkFilament = (t % 3 == 0)
            val baseColor = if (isPinkFilament) Color(0xFFFF4081) else Color(0xFF00E5FF)
            val baseAlpha = if (isFront) (0.78f + focusGlowBoost * 0.2f) else (0.28f + focusGlowBoost * 0.15f)

            drawScope.drawPath(
                path = tentaclePath,
                color = baseColor.copy(alpha = baseAlpha.coerceIn(0.12f, 0.95f)),
                style = Stroke(width = if (isFront) 1.4f else 0.9f, cap = StrokeCap.Round)
            )

            // Gossamer traveling light bead
            if (t % 2 == 0) {
                val beadJointIdx = ((animTime * 6f + t * 2) % jointsPerTentacle).toInt().coerceIn(1, jointsPerTentacle - 1)
                val beadPos = joints[beadJointIdx]
                drawScope.drawCircle(
                    color = Color.White.copy(alpha = if (isFront) 0.95f else 0.45f),
                    radius = if (isFront) 2.0f else 1.2f,
                    center = beadPos
                )
            }
        }
    }

    private fun renderGazeReticleSmall(
        drawScope: DrawScope,
        gazeX: Float,
        gazeY: Float,
        isFocused: Boolean
    ) {
        val reticleColor = if (isFocused) Color(0xFF00E676) else Color(0xFF00E5FF)
        val radius = if (isFocused) 6.5f else 4.2f

        drawScope.drawCircle(
            color = reticleColor.copy(alpha = if (isFocused) 0.65f else 0.30f),
            radius = radius + 3.0f,
            center = Offset(gazeX, gazeY),
            style = Stroke(width = 1.0f)
        )

        drawScope.drawCircle(
            color = reticleColor.copy(alpha = 0.98f),
            radius = if (isFocused) 2.0f else 1.3f,
            center = Offset(gazeX, gazeY)
        )

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
