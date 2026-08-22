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

    // Dense 3D Silky Tentacles (22 filaments, 14 joints each)
    private val numTentacles = 22
    private val jointsPerTentacle = 14
    private val tentacleJoints = Array(numTentacles) { Array(jointsPerTentacle) { Offset(0.5f, 0.5f) } }

    private val spores = List(150) {
        Spore(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            vx = ((Math.random() - 0.5) * 0.0003).toFloat(),
            vy = ((Math.random() - 0.5) * 0.0003).toFloat(),
            size = (1.2f + Math.random() * 2.8f).toFloat(),
            alpha = (0.15f + Math.random() * 0.45f).toFloat(),
            hueOffset = (Math.random() * 60f).toFloat()
        )
    }

    private val sparkles = mutableListOf<Sparkle>()

    private var animTime = 0f

    // Jellyfish position and 3D swimming motion dynamics
    private var jellyX = 0.5f
    private var jellyY = 0.5f
    private var jellyHeading = 0f
    private var targetHeading = 0f
    private var swimSpeed = 0.0011f
    private var jellyTiltZ = 0f

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

        // 1. Update Autonomous 3D Swimming Path (Smooth Wandering from side to side)
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

        // Focus threshold (gaze near 3D jellyfish bell area)
        isCurrentlyFocused = state.gazeData.isFaceDetected && gazeJellyDistance < 0.18f

        // 3. Render Deep Space Plankton Spores
        renderSpores(drawScope, state, width, height)

        // 4. Render Focus Reward Starbursts
        if (isCurrentlyFocused) {
            emitFocusSparkles(jx, jy, state.primaryHue)
        }
        renderSparkles(drawScope, dt)

        // 5. Render 3D Bioluminescent Cosmic Jellyfish
        render3DJellyfish(drawScope, state, jx, jy, width, height)

        // 6. Render Real-Time Eye Gaze Tracking Point
        if (state.gazeData.isFaceDetected) {
            renderGazeReticle(drawScope, gazePx, gazePy, isCurrentlyFocused, state.primaryHue)
        }
    }

    private fun updateJellyfishMovement(dt: Float, speedMul: Float, pulseFreq: Float) {
        val freq = pulseFreq.coerceIn(0.2f, 3.0f)
        val pulsePhase = (animTime * freq * 2.0 * PI)
        val pulsePower = sin(pulsePhase).toFloat()

        // Organic wandering trajectory (Smooth sinusoidal Lissajous curve)
        val wanderAngle = (
            sin(animTime * 0.28f) * 1.15f +
            cos(animTime * 0.18f) * 0.85f +
            sin(animTime * 0.55f) * 0.35f
        )
        targetHeading = wanderAngle

        // Smooth angular steering
        val angleDiff = (targetHeading - jellyHeading)
        jellyHeading += angleDiff * (dt * 1.4f)
        jellyTiltZ = sin(animTime * 1.5f) * 8f

        // Forward thrust during contraction pulse
        val thrust = if (pulsePower > 0f) (pulsePower * pulsePower * 1.75f) else 0.2f
        val currentSpeed = swimSpeed * speedMul * (0.35f + thrust)

        val vx = cos(jellyHeading) * currentSpeed * 1.25f
        val vy = (sin(jellyHeading) * currentSpeed * 0.65f) - 0.00012f

        jellyX += vx
        jellyY += vy

        // Smooth boundaries with soft repulsion
        if (jellyX < 0.14f) {
            jellyX = 0.14f
            jellyHeading = abs(jellyHeading)
        } else if (jellyX > 0.86f) {
            jellyX = 0.86f
            jellyHeading = PI.toFloat() - abs(jellyHeading)
        }

        if (jellyY < 0.15f) {
            jellyY = 0.15f
            jellyHeading = (PI * 0.5).toFloat()
        } else if (jellyY > 0.80f) {
            jellyY = 0.80f
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
            val color = Color.hsv((baseHue + spore.hueOffset) % 360f, 0.65f, 0.95f).copy(alpha = spore.alpha)

            drawScope.drawCircle(
                color = color,
                radius = spore.size,
                center = Offset(sx, sy)
            )
        }
    }

    private fun emitFocusSparkles(centerX: Float, centerY: Float, baseHue: Float) {
        if (sparkles.size > 90) return
        for (i in 0..2) {
            val angle = (Math.random() * 2.0 * PI).toFloat()
            val spd = (35f + Math.random() * 95f).toFloat()
            val hue = (baseHue + (Math.random() * 70f - 35f).toFloat() + 360f) % 360f
            sparkles.add(
                Sparkle(
                    x = centerX + (Math.random() * 50f - 25f).toFloat(),
                    y = centerY + (Math.random() * 35f - 17f).toFloat(),
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd,
                    life = 1.0f,
                    maxLife = (0.7f + Math.random() * 0.5f).toFloat(),
                    size = (2.2f + Math.random() * 3.8f).toFloat(),
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

        // 3D Dome Contraction / Expansion
        val bellRadiusX = 95f * (1f + (1f - pulseVal) * 0.32f)
        val bellRadiusY = 75f * (1f + pulseVal * 0.32f)
        val focusGlowBoost = if (isCurrentlyFocused) 0.4f else 0.0f

        val rotationDeg = (jellyHeading * (180f / PI.toFloat()) + 90f) + jellyTiltZ

        drawScope.rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
            val topApexY = centerY - bellRadiusY * 0.85f
            val rimBaseY = centerY + bellRadiusY * 0.25f

            // A. Deep Ambient Bioluminescent Glow (Outer Fog Halo)
            val glowColor = Color.hsv(baseHue, 0.75f, 1.0f)
            val auraRadius = bellRadiusX * (1.75f + focusGlowBoost)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.42f + focusGlowBoost),
                        Color.hsv((baseHue + 40f) % 360f, 0.8f, 0.9f).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY - bellRadiusY * 0.1f),
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = Offset(centerX, centerY - bellRadiusY * 0.1f)
            )

            // B. BACK LAYER TENTACLES (Depth z < 0) - Rendered behind the dome
            renderTentacleLayer(drawScope, baseHue, centerX, rimBaseY, bellRadiusX, pulseVal, focusGlowBoost, isFront = false)

            // C. 3D Subumbrella Cavity (Internal Dome Ceiling)
            val cavityPath = Path().apply {
                val rimRx = bellRadiusX * 0.92f
                val rimRy = bellRadiusY * 0.28f
                moveTo(centerX - rimRx, rimBaseY)
                cubicTo(
                    centerX - rimRx, rimBaseY - rimRy * 2f,
                    centerX + rimRx, rimBaseY - rimRy * 2f,
                    centerX + rimRx, rimBaseY
                )
                cubicTo(
                    centerX + rimRx, rimBaseY + rimRy * 1.2f,
                    centerX - rimRx, rimBaseY + rimRy * 1.2f,
                    centerX - rimRx, rimBaseY
                )
                close()
            }
            drawScope.drawPath(
                path = cavityPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.hsv((baseHue + 75f) % 360f, 0.9f, 0.5f).copy(alpha = 0.65f),
                        Color.hsv(baseHue, 0.9f, 0.3f).copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, rimBaseY - bellRadiusY * 0.15f),
                    radius = bellRadiusX
                )
            )

            // D. Inner Bioluminescent Organ Core (Gonads / Manubrium horseshoe rings)
            val organRadius = (20f + pulseVal * 8f) * (1f + focusGlowBoost * 0.5f)
            val organCenter = Offset(centerX, centerY - bellRadiusY * 0.2f)
            val organColor = Color.hsv((baseHue + 60f) % 360f, 0.85f, 1.0f)

            // 4 Glowing Horseshoe Nodes
            for (i in 0 until 4) {
                val ang = (i * PI * 0.5 + animTime * 0.5).toFloat()
                val ox = organCenter.x + cos(ang) * (organRadius * 0.6f)
                val oy = organCenter.y + sin(ang) * (organRadius * 0.45f)

                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            organColor.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        center = Offset(ox, oy),
                        radius = organRadius * 0.5f
                    ),
                    radius = organRadius * 0.5f,
                    center = Offset(ox, oy)
                )
            }

            // Central Luminous Manubrium Core
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = organRadius * 0.3f,
                center = organCenter
            )

            // E. Cascading Frilled Oral Arms (4 Central Lacy Curtains)
            for (arm in 0 until 4) {
                val armPath = Path()
                val armOffset = (arm - 1.5f) * (bellRadiusX * 0.2f)
                val startX = centerX + armOffset
                val startY = rimBaseY - 5f

                armPath.moveTo(startX, startY)
                val armLen = 120f + (arm % 2) * 30f
                val segments = 8
                for (s in 1..segments) {
                    val progress = s.toFloat() / segments
                    val wave = sin(animTime * 4.5f + arm * 1.2f + s * 0.8f) * (12f * (1f + progress))
                    val curX = startX + wave
                    val curY = startY + progress * armLen
                    armPath.lineTo(curX, curY)
                }

                val armColor = Color.hsv((baseHue + 45f + arm * 15f) % 360f, 0.7f, 0.95f)
                drawScope.drawPath(
                    path = armPath,
                    color = armColor.copy(alpha = (0.55f + focusGlowBoost).coerceIn(0.2f, 0.95f)),
                    style = Stroke(width = 4.5f - arm * 0.5f, cap = StrokeCap.Round)
                )
            }

            // F. Translucent 3D Glass Dome (Outer Exumbrella Cap)
            val outerCapPath = Path().apply {
                val leftX = centerX - bellRadiusX
                val rightX = centerX + bellRadiusX
                val bottomY = rimBaseY

                moveTo(leftX, bottomY)
                // Left profile curve up to crown
                cubicTo(
                    leftX * 0.92f + centerX * 0.08f, centerY - bellRadiusY * 0.5f,
                    centerX - bellRadiusX * 0.5f, topApexY,
                    centerX, topApexY
                )
                // Right profile curve down to margin
                cubicTo(
                    centerX + bellRadiusX * 0.5f, topApexY,
                    rightX * 0.92f + centerX * 0.08f, centerY - bellRadiusY * 0.5f,
                    rightX, bottomY
                )
                // Undulating Scalloped Lappet Margin (12 rim scallops)
                val scallops = 12
                val scallopW = (bellRadiusX * 2f) / scallops
                for (sc in scallops downTo 1) {
                    val x1 = leftX + (sc - 0.5f) * scallopW
                    val y1 = bottomY - 5f * sin((sc.toFloat() / scallops) * PI.toFloat() * 2f + animTime * 4f)
                    val x2 = leftX + (sc - 1f) * scallopW
                    val y2 = bottomY
                    quadraticTo(x1, y1, x2, y2)
                }
                close()
            }

            // 3D Shading Gradient (Translucent Cyan -> Deep Magenta/Violet with Specular Highlight)
            val capGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.7f + focusGlowBoost * 0.3f), // Specular light reflection on apex
                    Color.hsv(baseHue, 0.6f, 1.0f).copy(alpha = 0.5f + focusGlowBoost * 0.3f),
                    Color.hsv((baseHue + 40f) % 360f, 0.8f, 0.95f).copy(alpha = 0.35f),
                    Color.hsv((baseHue + 90f) % 360f, 0.9f, 0.85f).copy(alpha = 0.2f)
                ),
                center = Offset(centerX, topApexY + bellRadiusY * 0.25f),
                radius = bellRadiusX * 1.1f
            )
            drawScope.drawPath(path = outerCapPath, brush = capGradient)

            // G. 3D Radial Striations (12 Glowing Meridian Ribs)
            for (m in 0..12) {
                val u = (m.toFloat() / 12f - 0.5f) * 2f // -1.0 to 1.0
                val meridianX = centerX + u * (bellRadiusX * 0.9f)
                val ribPath = Path().apply {
                    moveTo(centerX, topApexY + 4f)
                    cubicTo(
                        centerX * 0.4f + meridianX * 0.6f, topApexY + bellRadiusY * 0.35f,
                        meridianX, centerY,
                        meridianX, rimBaseY
                    )
                }
                val ribAlpha = (1f - abs(u) * 0.5f) * (0.45f + focusGlowBoost * 0.3f)
                drawScope.drawPath(
                    path = ribPath,
                    color = Color.White.copy(alpha = ribAlpha.coerceIn(0.1f, 0.9f)),
                    style = Stroke(width = if (m % 3 == 0) 2.0f else 1.2f, cap = StrokeCap.Round)
                )
            }

            // H. Glowing Neon Rim Margin Stroke
            val rimGlowColor = Color.hsv(baseHue, 0.4f, 1.0f).copy(alpha = 0.9f)
            drawScope.drawPath(path = outerCapPath, color = rimGlowColor, style = Stroke(width = 2.8f))

            // I. FOREGROUND LAYER TENTACLES (Depth z > 0) - Rendered over the rim
            renderTentacleLayer(drawScope, baseHue, centerX, rimBaseY, bellRadiusX, pulseVal, focusGlowBoost, isFront = true)
        }
    }

    private fun renderTentacleLayer(
        drawScope: DrawScope,
        baseHue: Float,
        centerX: Float,
        rimBaseY: Float,
        bellRadiusX: Float,
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
            val rootX = centerX + (cos(angleOnRim) * (bellRadiusX * 0.85f)).toFloat()
            val rootY = rimBaseY + (sin(angleOnRim) * 8f).toFloat()

            val tentaclePath = Path()
            tentaclePath.moveTo(rootX, rootY)

            val joints = tentacleJoints[t]
            joints[0] = Offset(rootX, rootY)

            val tentacleLength = 175f + (t % 5) * 20f
            val segmentLen = tentacleLength / jointsPerTentacle

            for (j in 1 until jointsPerTentacle) {
                val prevJoint = joints[j - 1]
                val wavePhase = animTime * 4.2f + t * 0.45f + j * 0.55f
                val waveAmplitude = (6.5f + j * 2.2f) * (1f + (1f - pulseVal) * 0.4f)
                val waveX = sin(wavePhase) * waveAmplitude
                val dragY = segmentLen + (1f - pulseVal) * 4f

                val targetX = prevJoint.x + waveX * 0.38f
                val targetY = prevJoint.y + dragY

                // Damped spring physics
                val currentJ = joints[j]
                val newX = currentJ.x + (targetX - currentJ.x) * 0.42f
                val newY = currentJ.y + (targetY - currentJ.y) * 0.42f
                joints[j] = Offset(newX, newY)

                tentaclePath.lineTo(newX, newY)
            }

            // Fiber-optic light traveling pulse down each filament
            val filamentHue = (baseHue + t * 12f + animTime * 15f) % 360f
            val filamentColor = Color.hsv(filamentHue, if (isFront) 0.65f else 0.85f, 1.0f)
            val baseAlpha = if (isFront) (0.65f + focusGlowBoost) else (0.28f + focusGlowBoost * 0.5f)

            drawScope.drawPath(
                path = tentaclePath,
                color = filamentColor.copy(alpha = baseAlpha.coerceIn(0.15f, 1.0f)),
                style = Stroke(width = if (isFront) 2.2f else 1.4f, cap = StrokeCap.Round)
            )

            // Micro glowing light bead traveling along the tentacle
            val beadJointIdx = ((animTime * 6f + t) % jointsPerTentacle).toInt().coerceIn(1, jointsPerTentacle - 1)
            val beadPos = joints[beadJointIdx]
            drawScope.drawCircle(
                color = Color.White.copy(alpha = if (isFront) 0.95f else 0.5f),
                radius = if (isFront) 2.6f else 1.6f,
                center = beadPos
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
            color = reticleColor.copy(alpha = if (isFocused) 0.55f else 0.25f),
            radius = radius + 8f,
            center = Offset(gazeX, gazeY),
            style = Stroke(width = 3f)
        )

        // Center reticle dot
        drawScope.drawCircle(
            color = reticleColor.copy(alpha = 0.95f),
            radius = if (isFocused) 6f else 4f,
            center = Offset(gazeX, gazeY)
        )

        // Crosshair ticks
        val tickLen = 6f
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX - radius - tickLen, gazeY),
            end = Offset(gazeX - radius + 2f, gazeY),
            strokeWidth = 2f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX + radius - 2f, gazeY),
            end = Offset(gazeX + radius + tickLen, gazeY),
            strokeWidth = 2f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX, gazeY - radius - tickLen),
            end = Offset(gazeX, gazeY - radius + 2f),
            strokeWidth = 2f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.85f),
            start = Offset(gazeX, gazeY + radius - 2f),
            end = Offset(gazeX, gazeY + radius + tickLen),
            strokeWidth = 2f
        )
    }
}
