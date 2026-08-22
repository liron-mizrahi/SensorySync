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

        // 5. Render Fluid Hydrodynamic Jellyfish with Dynamic Breathing/Swimming Smoke Oral Arms
        renderFluidHydrodynamicJellyfish(drawScope, state, jx, jy, width, height)

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

        val baseWanderDelta = (sin(animTime * 0.20f) * 0.12f + cos(animTime * 0.14f) * 0.08f)
        val desiredAngle = targetHeading + baseWanderDelta

        val maxTurnRate = 0.25f
        var angleDiff = desiredAngle - jellyHeading
        while (angleDiff > PI) angleDiff -= (2.0 * PI).toFloat()
        while (angleDiff < -PI) angleDiff += (2.0 * PI).toFloat()

        val maxStep = maxTurnRate * dt
        val clampedTurn = angleDiff.coerceIn(-maxStep, maxStep)
        jellyHeading += clampedTurn

        val thrust = if (pulsePower > 0.35f) ((pulsePower - 0.35f) * 1.6f) else 0.12f
        val currentSpeed = swimBaseSpeed * speedMul * (0.5f + thrust)

        val desiredVx = cos(jellyHeading) * currentSpeed * 1.25f
        val desiredVy = sin(jellyHeading) * currentSpeed * 0.85f

        val accel = (2.0f * dt).coerceIn(0.01f, 0.2f)
        jellyVx += (desiredVx - jellyVx) * accel
        jellyVy += (desiredVy - jellyVy) * accel

        jellyX += jellyVx
        jellyY += jellyVy

        val targetTilt = (clampedTurn / maxStep.coerceAtLeast(0.001f)) * 6.0f
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

    private fun renderFluidHydrodynamicJellyfish(
        drawScope: DrawScope,
        state: ControlState,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float
    ) {
        val freq = state.strobeFrequencyHz.coerceIn(0.2f, 3.0f)
        val cycle = animTime * freq * 2.0 * PI

        val crownPulse = sin(cycle).toFloat()
        val flankPulse = sin(cycle - 0.75).toFloat()
        val rimPulse = sin(cycle - 1.50).toFloat()

        val minDim = width.coerceAtMost(height)
        val baseScale = minDim * 0.18f * state.jellyfishScale.coerceIn(0.2f, 2.0f)

        val crownSquashY = crownPulse * (baseScale * 0.10f)
        val shoulderBulgeX = (1.0f - crownPulse * 0.15f)
        val flankBulgeX = (1.0f - flankPulse * 0.22f)
        val rimExpansionX = (1.0f - rimPulse * 0.28f)

        val bellRadiusX = baseScale * 1.05f
        val bellHeight = baseScale * 0.95f
        val rimRy = bellHeight * (0.24f + rimPulse * 0.06f)
        val focusGlowBoost = if (isCurrentlyFocused) 0.45f else 0.0f

        val rotationDeg = (jellyHeading * (180f / PI.toFloat()) + 90f) + jellyTiltZ

        drawScope.rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
            val topApexY = centerY - bellHeight * 0.90f + crownSquashY
            val rimBaseY = centerY + bellHeight * 0.18f

            val apexPt = Offset(centerX, topApexY)
            val leftShoulder = Offset(centerX - bellRadiusX * 0.65f * shoulderBulgeX, centerY - bellHeight * 0.55f)
            val rightShoulder = Offset(centerX + bellRadiusX * 0.65f * shoulderBulgeX, centerY - bellHeight * 0.55f)
            val leftFlank = Offset(centerX - bellRadiusX * 0.95f * flankBulgeX, centerY - bellHeight * 0.15f)
            val rightFlank = Offset(centerX + bellRadiusX * 0.95f * flankBulgeX, centerY - bellHeight * 0.15f)
            val leftSkirt = Offset(centerX - bellRadiusX * rimExpansionX, rimBaseY)
            val rightSkirt = Offset(centerX + bellRadiusX * rimExpansionX, rimBaseY)

            // A. Atmospheric Cyan Volumetric Halo
            val haloRadius = bellRadiusX * (2.4f + focusGlowBoost)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.42f + focusGlowBoost),
                        Color(0xFF0097A7).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY - bellHeight * 0.18f),
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = Offset(centerX, centerY - bellHeight * 0.18f)
            )

            // B. BACK LAYER GOSSAMER FILAMENTS (Depth z < 0)
            renderGossamerTentacles(drawScope, centerX, rimBaseY, bellRadiusX * rimExpansionX, rimRy, baseScale, rimPulse, focusGlowBoost, isFront = false)

            // C. 3D Subumbrella Interior Cavern with Dynamic Fluid Volume
            val cavityPath = Path().apply {
                val rimRx = bellRadiusX * rimExpansionX * 0.92f
                moveTo(centerX - rimRx, rimBaseY)
                cubicTo(
                    centerX - rimRx * 0.95f, rimBaseY - rimRy * 2.5f - flankPulse * 10f,
                    centerX + rimRx * 0.95f, rimBaseY - rimRy * 2.5f - flankPulse * 10f,
                    centerX + rimRx, rimBaseY
                )
                cubicTo(
                    centerX + rimRx * 0.5f, rimBaseY + rimRy * 1.1f + sin(animTime * 3.5f) * 4f,
                    centerX - rimRx * 0.5f, rimBaseY + rimRy * 1.1f + sin(animTime * 3.5f + PI.toFloat()) * 4f,
                    centerX - rimRx, rimBaseY
                )
                close()
            }
            drawScope.drawPath(
                path = cavityPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE91E63).copy(alpha = 0.70f),
                        Color(0xFF880E4F).copy(alpha = 0.48f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, rimBaseY - bellHeight * 0.22f),
                    radius = bellRadiusX * 0.95f
                )
            )

            // D. 4 Luminous Horseshoe Gonad Organs
            val gonadRadius = (baseScale * 0.32f + (1f - flankPulse) * 7f) * (1f + focusGlowBoost * 0.35f)
            val gonadCenter = Offset(centerX, centerY - bellHeight * 0.24f + crownSquashY * 0.5f)
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

            // E. DYNAMIC BREATHING & SWIMMING SMOKE ORAL ARMS
            renderDynamicBreathingSmokeArms(drawScope, centerX, rimBaseY, bellRadiusX * rimExpansionX, baseScale, cycle, freq, focusGlowBoost)

            // F. Fluid 18-Point Hydrodynamic Bell Surface
            val fluidBellPath = Path().apply {
                moveTo(leftSkirt.x, leftSkirt.y)

                cubicTo(
                    leftFlank.x * 1.02f, leftFlank.y + bellHeight * 0.12f,
                    leftFlank.x, leftFlank.y,
                    leftShoulder.x, leftShoulder.y
                )
                cubicTo(
                    leftShoulder.x + bellRadiusX * 0.15f, leftShoulder.y - bellHeight * 0.18f,
                    apexPt.x - bellRadiusX * 0.35f, apexPt.y,
                    apexPt.x, apexPt.y
                )

                cubicTo(
                    apexPt.x + bellRadiusX * 0.35f, apexPt.y,
                    rightShoulder.x - bellRadiusX * 0.15f, rightShoulder.y - bellHeight * 0.18f,
                    rightShoulder.x, rightShoulder.y
                )
                cubicTo(
                    rightFlank.x, rightFlank.y,
                    rightFlank.x * 1.02f, rightFlank.y + bellHeight * 0.12f,
                    rightSkirt.x, rightSkirt.y
                )

                val numLappets = 16
                val rimW = (rightSkirt.x - leftSkirt.x)
                for (lp in numLappets downTo 1) {
                    val u = lp.toFloat() / numLappets
                    val nextU = (lp - 1).toFloat() / numLappets
                    val ang = u * PI.toFloat()
                    val nextAng = nextU * PI.toFloat()

                    val x1 = leftSkirt.x + u * rimW
                    val y1 = rimBaseY + sin(ang) * rimRy + sin(animTime * 4.5f + u * 12.0f) * (4.5f * (1f - rimPulse))

                    val x2 = leftSkirt.x + nextU * rimW
                    val y2 = rimBaseY + sin(nextAng) * rimRy

                    quadraticTo(x1, y1, x2, y2)
                }
                close()
            }

            val capGradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE0F7FA).copy(alpha = 0.92f + focusGlowBoost * 0.08f),
                    Color(0xFF00E5FF).copy(alpha = 0.75f + focusGlowBoost * 0.15f),
                    Color(0xFF4DD0E1).copy(alpha = 0.58f),
                    Color(0xFFF48FB1).copy(alpha = 0.42f),
                    Color(0xFFCE93D8).copy(alpha = 0.28f)
                ),
                center = Offset(centerX, topApexY + bellHeight * 0.28f),
                radius = bellRadiusX * 1.25f
            )
            drawScope.drawPath(path = fluidBellPath, brush = capGradient)

            drawScope.drawPath(
                path = fluidBellPath,
                color = Color(0xFF00E5FF).copy(alpha = 0.45f + focusGlowBoost * 0.2f),
                style = Stroke(width = 4.0f)
            )
            drawScope.drawPath(
                path = fluidBellPath,
                color = Color(0xFFE0F7FA).copy(alpha = 0.85f),
                style = Stroke(width = 1.4f)
            )

            // G. 24 Radial Meridians Tracking Fluid Spline Mesh
            val numMeridians = 24
            for (m in 0..numMeridians) {
                val u = (m.toFloat() / numMeridians - 0.5f) * 2f
                val meridianFlankX = centerX + u * (bellRadiusX * 0.92f * (1.0f - flankPulse * 0.18f))
                val meridianSkirtX = centerX + u * (bellRadiusX * rimExpansionX * 0.90f)

                val ribPath = Path().apply {
                    moveTo(centerX, topApexY + 2f)
                    cubicTo(
                        centerX * 0.25f + meridianFlankX * 0.75f, topApexY + bellHeight * 0.35f,
                        meridianFlankX, centerY - bellHeight * 0.1f,
                        meridianSkirtX, rimBaseY + (1f - u * u) * (rimRy * 0.60f)
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
            renderGossamerTentacles(drawScope, centerX, rimBaseY, bellRadiusX * rimExpansionX, rimRy, baseScale, rimPulse, focusGlowBoost, isFront = true)
        }
    }

    private fun renderDynamicBreathingSmokeArms(
        drawScope: DrawScope,
        centerX: Float,
        rimBaseY: Float,
        bellRadiusX: Float,
        baseScale: Float,
        cycle: Double,
        pulseFreq: Float,
        focusGlowBoost: Float
    ) {
        val numPlumes = 4
        val basePlumeLength = baseScale * 3.2f

        for (p in 0 until numPlumes) {
            val rootOffsetX = (p - 1.5f) * (bellRadiusX * 0.26f)
            val rootX = centerX + rootOffsetX
            val rootY = rimBaseY - 10f

            // 1. Dynamic Breathing/Swimming Hydrodynamic Smoke Puffs
            val numPuffs = 10
            for (i in 0 until numPuffs) {
                val progress = i.toFloat() / (numPuffs - 1)

                // Propagating Jet Pulse Wave down the smoke column
                val wavePhase = (cycle - progress * 2.8 - p * 0.35)
                val breathPulse = sin(wavePhase).toFloat()
                val powerThrust = max(breathPulse, 0.0f) // Jet ejection power
                val inhaleRecoil = max(-breathPulse, 0.0f) // Recovery expansion

                // Dynamic Longitudinal Stretch / Compression
                val currentPlumeLength = basePlumeLength * (1.0f + powerThrust * 0.35f - inhaleRecoil * 0.15f)
                val waveX = sin(animTime * 3.2f + p * 1.4f + progress * 3.8f) * (20f * (progress + 0.3f) * (1f + inhaleRecoil * 0.6f))
                val waveY = cos(animTime * 2.0f + p * 0.9f + progress * 2.5f) * (8f + powerThrust * 12f)
                val puffX = rootX + waveX
                val puffY = rootY + progress * currentPlumeLength + waveY

                // Dynamic Transverse Mushrooming (Smoke billows wide on inhale, constricts into jet on thrust)
                val billowFactor = (1.0f + inhaleRecoil * 0.65f - powerThrust * 0.25f)
                val puffRadius = baseScale * (0.16f + progress * 0.26f) * billowFactor

                // Dynamic Luminescent Surge during swimming stroke
                val surgeAlpha = 1.0f + powerThrust * 0.40f
                val puffAlpha = ((0.28f * (1.0f - progress * 0.50f) * surgeAlpha) + focusGlowBoost * 0.15f).coerceIn(0.05f, 0.70f)

                val puffColor = if (p % 2 == 0) Color(0xFFF48FB1) else Color(0xFFCE93D8)
                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            puffColor.copy(alpha = puffAlpha),
                            puffColor.copy(alpha = puffAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(puffX, puffY),
                        radius = puffRadius
                    ),
                    radius = puffRadius,
                    center = Offset(puffX, puffY)
                )
            }

            // 2. Interweaving Breathing Smoke Wisps with Dynamic Jet Vortices (12 Strands per Plume)
            val numWisps = 12
            for (w in 0 until numWisps) {
                val wispPath = Path()
                val wispOffset = (w - (numWisps / 2f)) * 3.0f
                wispPath.moveTo(rootX + wispOffset, rootY)

                val segments = 24
                for (s in 1..segments) {
                    val progress = s.toFloat() / segments
                    val wavePhase = (cycle - progress * 2.8 - p * 0.35)
                    val breathPulse = sin(wavePhase).toFloat()
                    val powerThrust = max(breathPulse, 0.0f)
                    val inhaleRecoil = max(-breathPulse, 0.0f)

                    val currentPlumeLength = basePlumeLength * (1.0f + powerThrust * 0.35f - inhaleRecoil * 0.15f)

                    // Dynamic turbulence and vortex eddy curls
                    val vortexPower = (14f + powerThrust * 20f) * progress
                    val noise1 = sin(animTime * (2.8f + w * 0.2f) + p * 1.5f + progress * (4.2f + w * 0.3f)) * (16f * (progress + 0.25f) * (1f + inhaleRecoil * 0.5f))
                    val noise2 = cos(animTime * 1.6f + progress * 3.0f + w * 0.6f) * (12f * (progress + 0.25f))
                    val dynamicVortex = if (progress > 0.5f) sin(animTime * 4.5f + s * 0.9f + w) * vortexPower else 0f

                    val wx = rootX + wispOffset * (1f + progress * 2.5f * (1f + inhaleRecoil * 0.6f)) + noise1 + noise2 + dynamicVortex
                    val wy = rootY + progress * currentPlumeLength
                    wispPath.lineTo(wx, wy)
                }

                val isCyanSmoke = (w % 3 == 0)
                val wispColor = if (isCyanSmoke) Color(0xFF80DEEA) else if (p % 2 == 0) Color(0xFFFF80AB) else Color(0xFFE1BEE7)
                val wispAlpha = (0.24f - (w % 3) * 0.04f + focusGlowBoost * 0.15f).coerceIn(0.04f, 0.48f)
                val strokeW = if (w % 2 == 0) 2.2f else 1.1f

                drawScope.drawPath(
                    path = wispPath,
                    color = wispColor.copy(alpha = wispAlpha),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }
    }

    private fun renderGossamerTentacles(
        drawScope: DrawScope,
        centerX: Float,
        rimBaseY: Float,
        bellRadiusX: Float,
        rimRy: Float,
        baseScale: Float,
        rimPulse: Float,
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

                val wave1 = sin(animTime * 3.2f + t * 0.25f + j * 0.32f) * (4.5f + j * 2.4f)
                val wave2 = cos(animTime * 1.8f + t * 0.55f + j * 0.45f) * (3.0f + j * 1.6f)

                val tipCurlX = if (j > 16) cos(animTime * 2.2f + t * 0.8f) * (j - 16) * 2.2f else 0f
                val tipCurlY = if (j > 16) -sin(animTime * 2.2f + t * 0.8f) * (j - 16) * 1.8f else 0f

                val waveAmplitude = (1f + (1f - rimPulse) * 0.35f)
                val waveX = (wave1 + wave2 + tipCurlX) * waveAmplitude
                val dragY = segmentLen + (1f - rimPulse) * 3.6f + tipCurlY

                val targetX = prevJoint.x + waveX * 0.28f
                val targetY = prevJoint.y + dragY

                val currentJ = joints[j]
                val newX = currentJ.x + (targetX - currentJ.x) * 0.32f
                val newY = currentJ.y + (targetY - currentJ.y) * 0.32f
                joints[j] = Offset(newX, newY)

                tentaclePath.lineTo(newX, newY)
            }

            val isPinkFilament = (t % 3 == 0)
            val baseColor = if (isPinkFilament) Color(0xFFFF4081) else Color(0xFF00E5FF)
            val baseAlpha = if (isFront) (0.78f + focusGlowBoost * 0.2f) else (0.28f + focusGlowBoost * 0.15f)

            drawScope.drawPath(
                path = tentaclePath,
                color = baseColor.copy(alpha = baseAlpha.coerceIn(0.12f, 0.95f)),
                style = Stroke(width = if (isFront) 1.4f else 0.9f, cap = StrokeCap.Round)
            )

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
