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

    // Background floating marine snow / cosmic spores
    private class Spore(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var alpha: Float,
        var isPink: Boolean
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

    // 64 Ultra-fine Gossamer Tentacle Filaments (24 joints each for fluid organic S-curves)
    private val numTentacles = 64
    private val jointsPerTentacle = 24
    private val tentacleJoints = Array(numTentacles) { Array(jointsPerTentacle) { Offset(0.5f, 0.5f) } }

    private val spores = List(160) {
        Spore(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            vx = ((Math.random() - 0.5) * 0.0002).toFloat(),
            vy = ((Math.random() - 0.5) * 0.0002).toFloat(),
            size = (1.0f + Math.random() * 2.5f).toFloat(),
            alpha = (0.15f + Math.random() * 0.45f).toFloat(),
            isPink = Math.random() < 0.25
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

        val isOnScreen = jellyX in -0.05f..1.05f && jellyY in -0.05f..1.05f
        isCurrentlyFocused = isOnScreen && state.gazeData.isFaceDetected && gazeJellyDistance < 0.22f

        // 3. Render Deep Sea Ambient Spores
        renderSpores(drawScope, width, height)

        // 4. Render Focus Reward Starbursts
        if (isCurrentlyFocused) {
            emitFocusSparkles(jx, jy)
        }
        renderSparkles(drawScope, dt)

        // 5. Render Photorealistic 3D Bioluminescent Jellyfish (Matching Reference)
        renderPhotorealisticJellyfish(drawScope, state, jx, jy, width, height)

        // 6. Render Real-Time Eye Gaze Tracking Point (Scaled to 0.3x)
        if (state.gazeData.isFaceDetected) {
            renderGazeReticleSmall(drawScope, gazePx, gazePy, isCurrentlyFocused)
        }
    }

    private fun updateJellyfishMovement(dt: Float, speedMul: Float, pulseFreq: Float) {
        val freq = pulseFreq.coerceIn(0.2f, 3.0f)
        val pulsePhase = (animTime * freq * 2.0 * PI)
        val pulsePower = ((sin(pulsePhase) + 1.0) / 2.0).toFloat()

        // 1. Off-screen detection and smooth re-entry (< 3s)
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

        // 2. Gentle organic path undulation: strictly capped to small angle turns <= 20 deg
        val baseWanderDelta = (sin(animTime * 0.20f) * 0.12f + cos(animTime * 0.14f) * 0.08f)
        val desiredAngle = targetHeading + baseWanderDelta

        val maxTurnRate = 0.25f
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
                    jellyX * 1000f + backDirX * (j * 15f),
                    jellyY * 1000f + backDirY * (j * 15f)
                )
            }
        }
    }

    private fun renderSpores(drawScope: DrawScope, width: Float, height: Float) {
        for (spore in spores) {
            spore.x = (spore.x + spore.vx + 1f) % 1f
            spore.y = (spore.y + spore.vy + 1f) % 1f

            val sx = spore.x * width
            val sy = spore.y * height
            val color = if (spore.isPink) Color(0xFFFF80AB).copy(alpha = spore.alpha * 0.7f) else Color(0xFF00E5FF).copy(alpha = spore.alpha)

            drawScope.drawCircle(
                color = color,
                radius = spore.size,
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

        val bellRadiusX = baseScale * (1f + (1f - pulseVal) * 0.20f)
        val bellRadiusY = baseScale * 0.90f * (1f + pulseVal * 0.20f)
        val rimRy = bellRadiusY * 0.26f // 3D perspective rim height
        val focusGlowBoost = if (isCurrentlyFocused) 0.45f else 0.0f

        val rotationDeg = (jellyHeading * (180f / PI.toFloat()) + 90f) + jellyTiltZ

        drawScope.rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
            val topApexY = centerY - bellRadiusY * 0.94f
            val rimBaseY = centerY + bellRadiusY * 0.20f

            // A. Atmospheric Cyan Volumetric Halo
            val haloRadius = bellRadiusX * (2.2f + focusGlowBoost)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.38f + focusGlowBoost),
                        Color(0xFF0097A7).copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY - bellRadiusY * 0.15f),
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = Offset(centerX, centerY - bellRadiusY * 0.15f)
            )

            // B. BACK LAYER GOSSAMER FILAMENTS (Depth z < 0)
            renderGossamerTentacles(drawScope, centerX, rimBaseY, bellRadiusX, rimRy, baseScale, pulseVal, focusGlowBoost, isFront = false)

            // C. 3D Subumbrella Cavity with Deep Rose/Violet Glow
            val cavityPath = Path().apply {
                val rimRx = bellRadiusX * 0.94f
                moveTo(centerX - rimRx, rimBaseY)
                cubicTo(
                    centerX - rimRx, rimBaseY - rimRy * 2.4f,
                    centerX + rimRx, rimBaseY - rimRy * 2.4f,
                    centerX + rimRx, rimBaseY
                )
                // 3D elliptical under-margin
                cubicTo(
                    centerX + rimRx * 0.5f, rimBaseY + rimRy,
                    centerX - rimRx * 0.5f, rimBaseY + rimRy,
                    centerX - rimRx, rimBaseY
                )
                close()
            }
            drawScope.drawPath(
                path = cavityPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE91E63).copy(alpha = 0.65f),
                        Color(0xFF880E4F).copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, rimBaseY - bellRadiusY * 0.2f),
                    radius = bellRadiusX * 0.9f
                )
            )

            // D. Inner Glowing Horseshoe Gonads (Matching Reference Visual)
            val gonadRadius = (baseScale * 0.30f + pulseVal * 8f) * (1f + focusGlowBoost * 0.35f)
            val gonadCenter = Offset(centerX, centerY - bellRadiusY * 0.25f)
            for (i in 0 until 4) {
                val ang = (i * PI * 0.5 + animTime * 0.3).toFloat()
                val ox = gonadCenter.x + cos(ang) * (gonadRadius * 0.65f)
                val oy = gonadCenter.y + sin(ang) * (gonadRadius * 0.48f)

                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF80AB).copy(alpha = 0.95f),
                            Color(0xFFE91E63).copy(alpha = 0.80f),
                            Color.Transparent
                        ),
                        center = Offset(ox, oy),
                        radius = gonadRadius * 0.60f
                    ),
                    radius = gonadRadius * 0.60f,
                    center = Offset(ox, oy)
                )
            }

            // E. Cascading Ruffled Translucent Silk Oral Arms (3 Wide Flowing Ribbon Curtains)
            renderRuffledOralArms(drawScope, centerX, rimBaseY, bellRadiusX, baseScale, pulseVal, focusGlowBoost)

            // F. Translucent 3D Mushroom Exumbrella Dome (Smooth Parabolic Crown)
            val outerCapPath = Path().apply {
                val leftX = centerX - bellRadiusX
                val rightX = centerX + bellRadiusX
                val bottomY = rimBaseY

                moveTo(leftX, bottomY)
                // Left profile curve up to crown
                cubicTo(
                    leftX * 0.95f + centerX * 0.05f, centerY - bellRadiusY * 0.55f,
                    centerX - bellRadiusX * 0.55f, topApexY,
                    centerX, topApexY
                )
                // Right profile curve down to margin
                cubicTo(
                    centerX + bellRadiusX * 0.55f, topApexY,
                    rightX * 0.95f + centerX * 0.05f, centerY - bellRadiusY * 0.55f,
                    rightX, bottomY
                )
                // 3D Elliptical lower rim
                cubicTo(
                    centerX + bellRadiusX * 0.5f, bottomY + rimRy,
                    centerX - bellRadiusX * 0.5f, bottomY + rimRy,
                    leftX, bottomY
                )
                close()
            }

            // 3D Multi-Stop Glass Dome Gradient (Glowing Cyan Crown -> Translucent Lavender/Rose)
            val capGradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE0F7FA).copy(alpha = 0.90f + focusGlowBoost * 0.10f), // Apex Specular Glint
                    Color(0xFF00E5FF).copy(alpha = 0.72f + focusGlowBoost * 0.15f), // Cyan Luminescence
                    Color(0xFF4DD0E1).copy(alpha = 0.55f),
                    Color(0xFFF48FB1).copy(alpha = 0.40f), // Rose Subsurface Tint
                    Color(0xFFCE93D8).copy(alpha = 0.25f)  // Soft Lavender Edge
                ),
                center = Offset(centerX, topApexY + bellRadiusY * 0.26f),
                radius = bellRadiusX * 1.15f
            )
            drawScope.drawPath(path = outerCapPath, brush = capGradient)

            // G. 24 Fine 3D Radial Parachute Meridians (Neural Striations from Reference)
            val numMeridians = 24
            for (m in 0..numMeridians) {
                val u = (m.toFloat() / numMeridians - 0.5f) * 2f // -1.0 to 1.0
                val meridianX = centerX + u * (bellRadiusX * 0.92f)
                val ribPath = Path().apply {
                    moveTo(centerX, topApexY + 3f)
                    cubicTo(
                        centerX * 0.30f + meridianX * 0.70f, topApexY + bellRadiusY * 0.35f,
                        meridianX, centerY - bellRadiusY * 0.1f,
                        meridianX, rimBaseY + (1f - u * u) * (rimRy * 0.5f)
                    )
                }
                val ribAlpha = (1f - abs(u) * 0.4f) * (0.45f + focusGlowBoost * 0.3f)
                drawScope.drawPath(
                    path = ribPath,
                    color = Color(0xFFE0F7FA).copy(alpha = ribAlpha.coerceIn(0.12f, 0.92f)),
                    style = Stroke(width = if (m % 4 == 0) 2.0f else 1.1f, cap = StrokeCap.Round)
                )
            }

            // H. Luminous Cyan Rim Edge Stroke
            drawScope.drawPath(
                path = outerCapPath,
                color = Color(0xFF00E5FF).copy(alpha = 0.92f),
                style = Stroke(width = 2.8f)
            )

            // I. FOREGROUND LAYER GOSSAMER FILAMENTS (Depth z > 0)
            renderGossamerTentacles(drawScope, centerX, rimBaseY, bellRadiusX, rimRy, baseScale, pulseVal, focusGlowBoost, isFront = true)
        }
    }

    private fun renderRuffledOralArms(
        drawScope: DrawScope,
        centerX: Float,
        rimBaseY: Float,
        bellRadiusX: Float,
        baseScale: Float,
        pulseVal: Float,
        focusGlowBoost: Float
    ) {
        val numArms = 3
        val armLength = baseScale * 2.8f

        for (a in 0 until numArms) {
            val armOffset = (a - 1f) * (bellRadiusX * 0.28f)
            val startX = centerX + armOffset
            val startY = rimBaseY - 10f

            val leftRibbonPath = Path()
            val rightRibbonPath = Path()
            val centerSpine = mutableListOf<Offset>()

            val segments = 16
            for (s in 0..segments) {
                val progress = s.toFloat() / segments
                val wave1 = sin(animTime * 3.8f + a * 1.5f + progress * 4.5f) * (24f * (progress + 0.3f))
                val wave2 = cos(animTime * 2.6f + a * 1.2f + progress * 3.2f) * (14f * (progress + 0.3f))
                val curX = startX + wave1 + wave2
                val curY = startY + progress * armLength
                centerSpine.add(Offset(curX, curY))
            }

            val ribbonWidth = baseScale * 0.22f * (1f - (a % 2) * 0.2f)
            leftRibbonPath.moveTo(centerSpine[0].x - ribbonWidth * 0.5f, centerSpine[0].y)
            rightRibbonPath.moveTo(centerSpine[0].x + ribbonWidth * 0.5f, centerSpine[0].y)

            for (s in 1..segments) {
                val pt = centerSpine[s]
                val progress = s.toFloat() / segments
                val ruff = sin(animTime * 5.5f + s * 0.8f + a) * 8f
                val w = (ribbonWidth * (1f - progress * 0.6f) + ruff).coerceAtLeast(4f)

                leftRibbonPath.lineTo(pt.x - w, pt.y)
                rightRibbonPath.lineTo(pt.x + w, pt.y)
            }

            val closedRibbon = Path().apply {
                addPath(leftRibbonPath)
                for (s in segments downTo 0) {
                    val pt = centerSpine[s]
                    val progress = s.toFloat() / segments
                    val ruff = sin(animTime * 5.5f + s * 0.8f + a) * 8f
                    val w = (ribbonWidth * (1f - progress * 0.6f) + ruff).coerceAtLeast(4f)
                    lineTo(pt.x + w, pt.y)
                }
                close()
            }

            val silkColor = if (a == 1) Color(0xFFF48FB1) else Color(0xFFCE93D8)
            drawScope.drawPath(
                path = closedRibbon,
                color = silkColor.copy(alpha = (0.35f + focusGlowBoost * 0.2f).coerceIn(0.1f, 0.75f))
            )

            drawScope.drawPath(
                path = leftRibbonPath,
                color = Color(0xFF80DEEA).copy(alpha = 0.75f + focusGlowBoost * 0.2f),
                style = Stroke(width = 1.6f, cap = StrokeCap.Round)
            )
            drawScope.drawPath(
                path = rightRibbonPath,
                color = Color(0xFFFF80AB).copy(alpha = 0.75f + focusGlowBoost * 0.2f),
                style = Stroke(width = 1.6f, cap = StrokeCap.Round)
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
            val tentacleLength = baseScale * 3.6f + (t % 13) * 26f
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
                val wavePhase = animTime * 3.4f + t * 0.25f + j * 0.35f
                val tipCurl = if (j > jointsPerTentacle - 5) sin(animTime * 2.5f + t) * 12f else 0f
                val waveAmplitude = (5.5f + j * 2.6f) * (1f + (1f - pulseVal) * 0.35f)
                val waveX = sin(wavePhase) * waveAmplitude + tipCurl
                val dragY = segmentLen + (1f - pulseVal) * 3.8f

                val targetX = prevJoint.x + waveX * 0.30f
                val targetY = prevJoint.y + dragY

                val currentJ = joints[j]
                val newX = currentJ.x + (targetX - currentJ.x) * 0.34f
                val newY = currentJ.y + (targetY - currentJ.y) * 0.34f
                joints[j] = Offset(newX, newY)

                tentaclePath.lineTo(newX, newY)
            }

            val isPinkFilament = (t % 3 == 0)
            val baseColor = if (isPinkFilament) Color(0xFFFF4081) else Color(0xFF00E5FF)
            val baseAlpha = if (isFront) (0.75f + focusGlowBoost * 0.2f) else (0.28f + focusGlowBoost * 0.15f)

            drawScope.drawPath(
                path = tentaclePath,
                color = baseColor.copy(alpha = baseAlpha.coerceIn(0.12f, 0.95f)),
                style = Stroke(width = if (isFront) 1.5f else 1.0f, cap = StrokeCap.Round)
            )

            if (t % 2 == 0) {
                val beadJointIdx = ((animTime * 6f + t * 2) % jointsPerTentacle).toInt().coerceIn(1, jointsPerTentacle - 1)
                val beadPos = joints[beadJointIdx]
                drawScope.drawCircle(
                    color = Color.White.copy(alpha = if (isFront) 0.95f else 0.45f),
                    radius = if (isFront) 2.2f else 1.4f,
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
