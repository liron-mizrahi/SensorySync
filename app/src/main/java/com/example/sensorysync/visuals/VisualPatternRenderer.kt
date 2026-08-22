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

    // Jellyfish physics tentacles (6 tentacles, 10 joints each)
    private val numTentacles = 6
    private val jointsPerTentacle = 10
    private val tentacleJoints = Array(numTentacles) { Array(jointsPerTentacle) { Offset(0.5f, 0.5f) } }

    private val spores = List(120) {
        Spore(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            vx = ((Math.random() - 0.5) * 0.0004).toFloat(),
            vy = ((Math.random() - 0.5) * 0.0004).toFloat(),
            size = (1.5f + Math.random() * 3f).toFloat(),
            alpha = (0.2f + Math.random() * 0.5f).toFloat(),
            hueOffset = (Math.random() * 50f).toFloat()
        )
    }

    private val sparkles = mutableListOf<Sparkle>()

    private var animTime = 0f

    // Jellyfish position and motion dynamics
    private var jellyX = 0.5f
    private var jellyY = 0.5f
    private var jellyHeading = 0f
    private var targetHeading = 0f
    private var swimSpeed = 0.0012f

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

        // 1. Update Autonomous Swimming Path (Side to Side Smooth Organic Wandering)
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

        // Consider focus if gaze is within the jellyfish bell / aura area (radius ~0.16)
        isCurrentlyFocused = state.gazeData.isFaceDetected && gazeJellyDistance < 0.16f

        // 3. Render Background Floating Plankton Spores
        renderSpores(drawScope, state, width, height)

        // 4. Render Focus Sparkle Bursts (Reward feedback when looking at jellyfish)
        if (isCurrentlyFocused) {
            emitFocusSparkles(jx, jy, state.primaryHue)
        }
        renderSparkles(drawScope, dt)

        // 5. Render Bioluminescent Cosmic Jellyfish
        renderJellyfish(drawScope, state, jx, jy, width, height)

        // 6. Render Real-Time Eye Gaze Tracking Point
        if (state.gazeData.isFaceDetected) {
            renderGazeReticle(drawScope, gazePx, gazePy, isCurrentlyFocused, state.primaryHue)
        }
    }

    private fun updateJellyfishMovement(dt: Float, speedMul: Float, pulseFreq: Float) {
        // Pulse cycle: contraction & glide
        val freq = pulseFreq.coerceIn(0.2f, 3.0f)
        val pulsePhase = (animTime * freq * 2.0 * PI)
        val pulsePower = sin(pulsePhase).toFloat()

        // Organic undulating wander (Perlin-like smooth continuous curves)
        val wanderAngle = (
            sin(animTime * 0.35f) * 1.2f +
            cos(animTime * 0.22f) * 0.9f +
            sin(animTime * 0.65f) * 0.4f
        )
        targetHeading = wanderAngle

        // Smooth heading turn
        val angleDiff = (targetHeading - jellyHeading)
        jellyHeading += angleDiff * (dt * 1.5f)

        // Forward thrust during contraction pulse
        val thrust = if (pulsePower > 0f) (pulsePower * pulsePower * 1.8f) else 0.25f
        val currentSpeed = swimSpeed * speedMul * (0.4f + thrust)

        // Compute velocity
        val vx = cos(jellyHeading) * currentSpeed * 1.2f // gentle side-to-side emphasis
        val vy = (sin(jellyHeading) * currentSpeed * 0.7f) - 0.00015f // subtle natural upward drift

        jellyX += vx
        jellyY += vy

        // Smooth screen boundary rebound (keep jellyfish swimming naturally within visible canvas)
        if (jellyX < 0.12f) {
            jellyX = 0.12f
            jellyHeading = abs(jellyHeading)
        } else if (jellyX > 0.88f) {
            jellyX = 0.88f
            jellyHeading = PI.toFloat() - abs(jellyHeading)
        }

        if (jellyY < 0.14f) {
            jellyY = 0.14f
            jellyHeading = (PI * 0.5).toFloat()
        } else if (jellyY > 0.82f) {
            jellyY = 0.82f
            jellyHeading = -(PI * 0.5).toFloat()
        }
    }

    private fun renderSpores(drawScope: DrawScope, state: ControlState, width: Float, height: Float) {
        val baseHue = state.primaryHue
        for (spore in spores) {
            spore.x = (spore.x + spore.vx + 1f) % 1f
            spore.y = (spore.y + spore.vy + 1f) % 1f

            val sx = spore.x * width
            val sy = spore.y * height
            val color = Color.hsv((baseHue + spore.hueOffset) % 360f, 0.6f, 0.9f).copy(alpha = spore.alpha)

            drawScope.drawCircle(
                color = color,
                radius = spore.size,
                center = Offset(sx, sy)
            )
        }
    }

    private fun emitFocusSparkles(centerX: Float, centerY: Float, baseHue: Float) {
        if (sparkles.size > 80) return
        for (i in 0..2) {
            val angle = (Math.random() * 2.0 * PI).toFloat()
            val spd = (30f + Math.random() * 90f).toFloat()
            val hue = (baseHue + (Math.random() * 80f - 40f).toFloat() + 360f) % 360f
            sparkles.add(
                Sparkle(
                    x = centerX + (Math.random() * 40f - 20f).toFloat(),
                    y = centerY + (Math.random() * 30f - 15f).toFloat(),
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd,
                    life = 1.0f,
                    maxLife = (0.6f + Math.random() * 0.5f).toFloat(),
                    size = (2.5f + Math.random() * 3.5f).toFloat(),
                    color = Color.hsv(hue, 0.5f, 1.0f)
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
            s.vy += 15f * dt // gentle gravitational fall

            val alpha = (s.life).coerceIn(0f, 1f)
            drawScope.drawCircle(
                color = s.color.copy(alpha = alpha),
                radius = s.size * alpha,
                center = Offset(s.x, s.y)
            )
        }
    }

    private fun renderJellyfish(
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

        // Contraction/expansion geometry
        val bellWidth = 85f * (1f + (1f - pulseVal) * 0.35f)
        val bellHeight = 65f * (1f + pulseVal * 0.35f)
        val focusGlowBoost = if (isCurrentlyFocused) 0.35f else 0.0f

        val rotationDeg = (jellyHeading * (180f / PI.toFloat()) + 90f)

        drawScope.rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
            // A. Radiant Bioluminescent Ambient Aura behind the Jellyfish
            val auraColor = Color.hsv(baseHue, 0.7f, 0.9f)
            val auraRadius = bellWidth * (1.6f + focusGlowBoost)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = 0.35f + focusGlowBoost),
                        auraColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = Offset(centerX, centerY)
            )

            // B. Trailing Physics Tentacles (6 Tentacles)
            val tentacleBaseY = centerY + bellHeight * 0.3f
            for (t in 0 until numTentacles) {
                val tOffsetNorm = (t.toFloat() / (numTentacles - 1) - 0.5f) * 2f // -1.0 to 1.0
                val rootX = centerX + tOffsetNorm * (bellWidth * 0.65f)
                val rootY = tentacleBaseY

                val tentaclePath = Path()
                tentaclePath.moveTo(rootX, rootY)

                val joints = tentacleJoints[t]
                joints[0] = Offset(rootX, rootY)

                val tentacleLength = 140f + abs(tOffsetNorm) * 30f
                val segmentLen = tentacleLength / jointsPerTentacle

                for (j in 1 until jointsPerTentacle) {
                    val prevJoint = joints[j - 1]
                    val wave = sin(animTime * 4f + t * 0.8f + j * 0.6f) * (8f + j * 2.2f)
                    val dragOffset = (1f - pulseVal) * 6f

                    val targetX = prevJoint.x + wave * 0.35f
                    val targetY = prevJoint.y + segmentLen + dragOffset

                    // Damped follow
                    val currentJ = joints[j]
                    val newX = currentJ.x + (targetX - currentJ.x) * 0.45f
                    val newY = currentJ.y + (targetY - currentJ.y) * 0.45f
                    joints[j] = Offset(newX, newY)

                    tentaclePath.lineTo(newX, newY)
                }

                val tentacleHue = (baseHue + t * 18f + animTime * 10f) % 360f
                val tentacleColor = Color.hsv(tentacleHue, 0.7f, 0.95f)
                val tentacleAlpha = (0.55f + focusGlowBoost).coerceIn(0.2f, 1f)

                drawPath(
                    path = tentaclePath,
                    color = tentacleColor.copy(alpha = tentacleAlpha),
                    style = Stroke(width = if (t in 2..3) 3.5f else 2.2f, cap = StrokeCap.Round)
                )
            }

            // C. Inner Lacy Oral Arms (Center Floating Frills)
            val oralPath = Path()
            oralPath.moveTo(centerX - bellWidth * 0.25f, tentacleBaseY)
            for (k in 1..8) {
                val frillX = centerX + sin(animTime * 5f + k * 0.9f) * (bellWidth * 0.22f)
                val frillY = tentacleBaseY + k * 14f
                oralPath.lineTo(frillX, frillY)
            }
            drawPath(
                path = oralPath,
                color = Color.hsv((baseHue + 40f) % 360f, 0.5f, 1.0f).copy(alpha = 0.65f),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
            )

            // D. Translucent Bioluminescent Outer Bell (Dome)
            val bellPath = Path().apply {
                val topY = centerY - bellHeight * 0.7f
                val bottomY = centerY + bellHeight * 0.3f
                val leftX = centerX - bellWidth
                val rightX = centerX + bellWidth

                moveTo(leftX, bottomY)
                // Left curve up to crown
                cubicTo(
                    leftX * 0.9f + centerX * 0.1f, centerY - bellHeight * 0.4f,
                    centerX - bellWidth * 0.45f, topY,
                    centerX, topY
                )
                // Right curve down to margin
                cubicTo(
                    centerX + bellWidth * 0.45f, topY,
                    rightX * 0.9f + centerX * 0.1f, centerY - bellHeight * 0.4f,
                    rightX, bottomY
                )
                // Undulating bottom rim frills (Lappet Margin)
                val scallops = 6
                for (s in scallops downTo 1) {
                    val x1 = leftX + (s - 0.5f) * (bellWidth * 2f / scallops)
                    val y1 = bottomY - 6f * sin((s.toFloat() / scallops) * PI.toFloat() + animTime * 3f)
                    val x2 = leftX + (s - 1f) * (bellWidth * 2f / scallops)
                    val y2 = bottomY
                    quadraticBezierTo(x1, y1, x2, y2)
                }
                close()
            }

            // Bell fill gradient (Translucent luminous cyan -> deep violet)
            val bellGradient = Brush.verticalGradient(
                colors = listOf(
                    Color.hsv(baseHue, 0.65f, 1.0f).copy(alpha = 0.55f + focusGlowBoost),
                    Color.hsv((baseHue + 45f) % 360f, 0.75f, 0.95f).copy(alpha = 0.35f + focusGlowBoost * 0.5f),
                    Color.hsv((baseHue + 90f) % 360f, 0.8f, 0.8f).copy(alpha = 0.2f)
                ),
                startY = centerY - bellHeight * 0.7f,
                endY = centerY + bellHeight * 0.3f
            )
            drawPath(path = bellPath, brush = bellGradient)

            // Bell outer edge stroke
            val strokeColor = Color.hsv(baseHue, 0.4f, 1.0f).copy(alpha = 0.85f)
            drawPath(path = bellPath, color = strokeColor, style = Stroke(width = 2.5f))

            // E. Inner Glowing Organ Nucleus Core
            val coreRadius = (16f + pulseVal * 6f) * (1f + focusGlowBoost)
            val coreColor = if (isCurrentlyFocused) Color(0xFFFFD54F) else Color.hsv((baseHue + 20f) % 360f, 0.5f, 1.0f)
            drawCircle(
                color = coreColor.copy(alpha = 0.85f),
                radius = coreRadius,
                center = Offset(centerX, centerY - bellHeight * 0.15f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = coreRadius * 0.5f,
                center = Offset(centerX, centerY - bellHeight * 0.15f)
            )
        }
    }

    private fun renderGazeReticle(
        drawScope: DrawScope,
        gazeX: Float,
        gazeY: Float,
        isFocused: Boolean,
        baseHue: Float
    ) {
        val reticleColor = if (isFocused) Color(0xFF00E676) else Color.hsv(baseHue, 0.8f, 1.0f)
        val radius = if (isFocused) 18f else 12f

        // Soft outer glow ring
        drawScope.drawCircle(
            color = reticleColor.copy(alpha = if (isFocused) 0.5f else 0.25f),
            radius = radius + 8f,
            center = Offset(gazeX, gazeY),
            style = Stroke(width = 3f)
        )

        // Center reticle dot
        drawScope.drawCircle(
            color = reticleColor.copy(alpha = 0.9f),
            radius = if (isFocused) 6f else 4f,
            center = Offset(gazeX, gazeY)
        )

        // Crosshair ticks
        val tickLen = 6f
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.8f),
            start = Offset(gazeX - radius - tickLen, gazeY),
            end = Offset(gazeX - radius + 2f, gazeY),
            strokeWidth = 2f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.8f),
            start = Offset(gazeX + radius - 2f, gazeY),
            end = Offset(gazeX + radius + tickLen, gazeY),
            strokeWidth = 2f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.8f),
            start = Offset(gazeX, gazeY - radius - tickLen),
            end = Offset(gazeX, gazeY - radius + 2f),
            strokeWidth = 2f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.8f),
            start = Offset(gazeX, gazeY + radius - 2f),
            end = Offset(gazeX, gazeY + radius + tickLen),
            strokeWidth = 2f
        )
    }
}
