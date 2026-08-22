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

    // Dense 3D Silky Tentacles (22 filaments, 16 joints each)
    private val numTentacles = 22
    private val jointsPerTentacle = 16
    private val tentacleJoints = Array(numTentacles) { Array(jointsPerTentacle) { Offset(0.5f, 0.5f) } }

    private val spores = List(180) {
        Spore(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            vx = ((Math.random() - 0.5) * 0.0003).toFloat(),
            vy = ((Math.random() - 0.5) * 0.0003).toFloat(),
            size = (1.5f + Math.random() * 3.5f).toFloat(),
            alpha = (0.2f + Math.random() * 0.55f).toFloat(),
            hueOffset = (Math.random() * 60f).toFloat()
        )
    }

    private val sparkles = mutableListOf<Sparkle>()

    private var animTime = 0f

    // Jellyfish position, inertial velocity, and guarded angular steering dynamics
    private var jellyX = 0.5f
    private var jellyY = 0.5f
    private var jellyVx = 0f
    private var jellyVy = 0f
    private var jellyHeading = 0f
    private var swimBaseSpeed = 0.0011f
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

        // 1. Update Autonomous 3D Swimming Path with Rotation & Translation Guards
        updateJellyfishMovementGuarded(dt, state.speedMultiplier, state.strobeFrequencyHz)

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
        isCurrentlyFocused = state.gazeData.isFaceDetected && gazeJellyDistance < 0.22f

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

    private fun updateJellyfishMovementGuarded(dt: Float, speedMul: Float, pulseFreq: Float) {
        val freq = pulseFreq.coerceIn(0.2f, 3.0f)
        val pulsePhase = (animTime * freq * 2.0 * PI)
        val pulsePower = ((sin(pulsePhase) + 1.0) / 2.0).toFloat()

        // 1. Organic Lissajous Wander Vectors (Smooth continuous curves)
        val wanderForceX = cos(animTime * 0.18f) * 0.7f + sin(animTime * 0.36f) * 0.3f
        val wanderForceY = sin(animTime * 0.14f) * 0.5f + cos(animTime * 0.32f) * 0.25f

        // 2. Continuous Soft Margin Repulsion (Smooth fluid steering away from boundaries)
        var repulseX = 0f
        var repulseY = 0f

        val marginMinX = 0.22f
        val marginMaxX = 0.78f
        val marginMinY = 0.22f
        val marginMaxY = 0.76f

        if (jellyX < marginMinX) {
            val dist = (marginMinX - jellyX) / marginMinX
            repulseX += dist * dist * 4.0f
        } else if (jellyX > marginMaxX) {
            val dist = (jellyX - marginMaxX) / (1f - marginMaxX)
            repulseX -= dist * dist * 4.0f
        }

        if (jellyY < marginMinY) {
            val dist = (marginMinY - jellyY) / marginMinY
            repulseY += dist * dist * 4.0f
        } else if (jellyY > marginMaxY) {
            val dist = (jellyY - marginMaxY) / (1f - marginMaxY)
            repulseY -= dist * dist * 4.0f
        }

        // 3. Desired Steering Angle
        val steerX = wanderForceX + repulseX
        val steerY = wanderForceY + repulseY
        val targetAngle = atan2(steerY, steerX)

        // 4. Strict Angular Rotation Guard (Slew Rate Limiter - Prevents Fast Flipping & Snapping)
        val maxAngularSpeed = 1.15f // Maximum 1.15 radians/sec (~65 deg/sec)
        var angleDiff = (targetAngle - jellyHeading)
        // Normalize angleDiff to shortest arc [-PI, PI]
        while (angleDiff > PI) angleDiff -= (2.0 * PI).toFloat()
        while (angleDiff < -PI) angleDiff += (2.0 * PI).toFloat()

        val maxStep = maxAngularSpeed * dt
        val clampedTurn = angleDiff.coerceIn(-maxStep, maxStep)
        jellyHeading += clampedTurn

        // 5. Smooth Speed & Inertial Translation Guard (No Bumpy Bounces)
        val thrust = if (pulsePower > 0.35f) ((pulsePower - 0.35f) * 1.5f) else 0.15f
        val targetSpeed = swimBaseSpeed * speedMul * (0.45f + thrust)

        val desiredVx = cos(jellyHeading) * targetSpeed * 1.2f
        val desiredVy = sin(jellyHeading) * targetSpeed * 0.75f

        // Inertial damping
        val accelRate = (2.2f * dt).coerceIn(0.01f, 0.2f)
        jellyVx += (desiredVx - jellyVx) * accelRate
        jellyVy += (desiredVy - jellyVy) * accelRate

        // Translation update
        jellyX += jellyVx
        jellyY += jellyVy

        // Soft safe boundary clamping (prevents drifting off-screen without bouncing)
        jellyX = jellyX.coerceIn(0.14f, 0.86f)
        jellyY = jellyY.coerceIn(0.16f, 0.82f)

        // 6. Smooth Banking / Roll Tilt (Guarded)
        val targetTilt = (clampedTurn / maxStep.coerceAtLeast(0.001f)) * 10f
        jellyTiltZ += (targetTilt - jellyTiltZ) * (2.5f * dt)
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
        if (sparkles.size > 120) return
        for (i in 0..3) {
            val angle = (Math.random() * 2.0 * PI).toFloat()
            val spd = (40f + Math.random() * 120f).toFloat()
            val hue = (baseHue + (Math.random() * 80f - 40f).toFloat() + 360f) % 360f
            sparkles.add(
                Sparkle(
                    x = centerX + (Math.random() * 80f - 40f).toFloat(),
                    y = centerY + (Math.random() * 60f - 30f).toFloat(),
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd,
                    life = 1.0f,
                    maxLife = (0.7f + Math.random() * 0.6f).toFloat(),
                    size = (3.0f + Math.random() * 4.5f).toFloat(),
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
            s.vy += 15f * dt

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

        // Responsive Scale: Scaled prominently for tablet screens
        val minDim = width.coerceAtMost(height)
        val baseScale = minDim * 0.16f

        val bellRadiusX = baseScale * (1f + (1f - pulseVal) * 0.32f)
        val bellRadiusY = baseScale * 0.78f * (1f + pulseVal * 0.32f)
        val focusGlowBoost = if (isCurrentlyFocused) 0.45f else 0.0f

        val rotationDeg = (jellyHeading * (180f / PI.toFloat()) + 90f) + jellyTiltZ

        drawScope.rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
            val topApexY = centerY - bellRadiusY * 0.88f
            val rimBaseY = centerY + bellRadiusY * 0.28f

            // A. Deep Ambient Bioluminescent Glow
            val glowColor = Color.hsv(baseHue, 0.75f, 1.0f)
            val auraRadius = bellRadiusX * (1.85f + focusGlowBoost)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.48f + focusGlowBoost),
                        Color.hsv((baseHue + 40f) % 360f, 0.85f, 0.95f).copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY - bellRadiusY * 0.1f),
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = Offset(centerX, centerY - bellRadiusY * 0.1f)
            )

            // B. BACK LAYER TENTACLES (Depth z < 0)
            renderTentacleLayer(drawScope, baseHue, centerX, rimBaseY, bellRadiusX, baseScale, pulseVal, focusGlowBoost, isFront = false)

            // C. 3D Subumbrella Cavity
            val cavityPath = Path().apply {
                val rimRx = bellRadiusX * 0.92f
                val rimRy = bellRadiusY * 0.30f
                moveTo(centerX - rimRx, rimBaseY)
                cubicTo(
                    centerX - rimRx, rimBaseY - rimRy * 2.2f,
                    centerX + rimRx, rimBaseY - rimRy * 2.2f,
                    centerX + rimRx, rimBaseY
                )
                cubicTo(
                    centerX + rimRx, rimBaseY + rimRy * 1.3f,
                    centerX - rimRx, rimBaseY + rimRy * 1.3f,
                    centerX - rimRx, rimBaseY
                )
                close()
            }
            drawScope.drawPath(
                path = cavityPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.hsv((baseHue + 75f) % 360f, 0.95f, 0.6f).copy(alpha = 0.75f),
                        Color.hsv(baseHue, 0.9f, 0.35f).copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, rimBaseY - bellRadiusY * 0.15f),
                    radius = bellRadiusX
                )
            )

            // D. Inner Bioluminescent Organ Core (Gonads / Manubrium horseshoe rings)
            val organRadius = (baseScale * 0.28f + pulseVal * 12f) * (1f + focusGlowBoost * 0.5f)
            val organCenter = Offset(centerX, centerY - bellRadiusY * 0.2f)
            val organColor = Color.hsv((baseHue + 60f) % 360f, 0.85f, 1.0f)

            // 4 Glowing Horseshoe Nodes
            for (i in 0 until 4) {
                val ang = (i * PI * 0.5 + animTime * 0.5).toFloat()
                val ox = organCenter.x + cos(ang) * (organRadius * 0.65f)
                val oy = organCenter.y + sin(ang) * (organRadius * 0.50f)

                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.98f),
                            organColor.copy(alpha = 0.88f),
                            Color.Transparent
                        ),
                        center = Offset(ox, oy),
                        radius = organRadius * 0.55f
                    ),
                    radius = organRadius * 0.55f,
                    center = Offset(ox, oy)
                )
            }

            // Central Luminous Manubrium Core
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.98f),
                radius = organRadius * 0.35f,
                center = organCenter
            )

            // E. Cascading Frilled Oral Arms (4 Central Lacy Curtains)
            for (arm in 0 until 4) {
                val armPath = Path()
                val armOffset = (arm - 1.5f) * (bellRadiusX * 0.22f)
                val startX = centerX + armOffset
                val startY = rimBaseY - 5f

                armPath.moveTo(startX, startY)
                val armLen = baseScale * 1.6f + (arm % 2) * 40f
                val segments = 10
                for (s in 1..segments) {
                    val progress = s.toFloat() / segments
                    val wave = sin(animTime * 4.5f + arm * 1.2f + s * 0.8f) * (18f * (1f + progress))
                    val curX = startX + wave
                    val curY = startY + progress * armLen
                    armPath.lineTo(curX, curY)
                }

                val armColor = Color.hsv((baseHue + 45f + arm * 15f) % 360f, 0.7f, 0.95f)
                drawScope.drawPath(
                    path = armPath,
                    color = armColor.copy(alpha = (0.6f + focusGlowBoost).coerceIn(0.2f, 0.95f)),
                    style = Stroke(width = 6.5f - arm * 0.6f, cap = StrokeCap.Round)
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
                    val y1 = bottomY - 7f * sin((sc.toFloat() / scallops) * PI.toFloat() * 2f + animTime * 4f)
                    val x2 = leftX + (sc - 1f) * scallopW
                    val y2 = bottomY
                    quadraticTo(x1, y1, x2, y2)
                }
                close()
            }

            // 3D Shading Gradient (Translucent Cyan -> Deep Magenta/Violet with Specular Highlight)
            val capGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.75f + focusGlowBoost * 0.25f),
                    Color.hsv(baseHue, 0.65f, 1.0f).copy(alpha = 0.55f + focusGlowBoost * 0.25f),
                    Color.hsv((baseHue + 40f) % 360f, 0.85f, 0.95f).copy(alpha = 0.38f),
                    Color.hsv((baseHue + 90f) % 360f, 0.9f, 0.85f).copy(alpha = 0.22f)
                ),
                center = Offset(centerX, topApexY + bellRadiusY * 0.25f),
                radius = bellRadiusX * 1.15f
            )
            drawScope.drawPath(path = outerCapPath, brush = capGradient)

            // G. 3D Radial Striations (12 Glowing Meridian Ribs)
            for (m in 0..12) {
                val u = (m.toFloat() / 12f - 0.5f) * 2f // -1.0 to 1.0
                val meridianX = centerX + u * (bellRadiusX * 0.9f)
                val ribPath = Path().apply {
                    moveTo(centerX, topApexY + 6f)
                    cubicTo(
                        centerX * 0.4f + meridianX * 0.6f, topApexY + bellRadiusY * 0.35f,
                        meridianX, centerY,
                        meridianX, rimBaseY
                    )
                }
                val ribAlpha = (1f - abs(u) * 0.5f) * (0.5f + focusGlowBoost * 0.3f)
                drawScope.drawPath(
                    path = ribPath,
                    color = Color.White.copy(alpha = ribAlpha.coerceIn(0.15f, 0.95f)),
                    style = Stroke(width = if (m % 3 == 0) 2.8f else 1.6f, cap = StrokeCap.Round)
                )
            }

            // H. Glowing Neon Rim Margin Stroke
            val rimGlowColor = Color.hsv(baseHue, 0.4f, 1.0f).copy(alpha = 0.95f)
            drawScope.drawPath(path = outerCapPath, color = rimGlowColor, style = Stroke(width = 3.6f))

            // I. FOREGROUND LAYER TENTACLES (Depth z > 0)
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
            val rootX = centerX + (cos(angleOnRim) * (bellRadiusX * 0.85f)).toFloat()
            val rootY = rimBaseY + (sin(angleOnRim) * 10f).toFloat()

            val joints = tentacleJoints[t]
            val tentacleLength = baseScale * 2.2f + (t % 5) * 35f
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
                val wavePhase = animTime * 4.2f + t * 0.45f + j * 0.55f
                val waveAmplitude = (9.0f + j * 3.0f) * (1f + (1f - pulseVal) * 0.4f)
                val waveX = sin(wavePhase) * waveAmplitude
                val dragY = segmentLen + (1f - pulseVal) * 5f

                val targetX = prevJoint.x + waveX * 0.38f
                val targetY = prevJoint.y + dragY

                // Damped spring physics (silky smooth flow)
                val currentJ = joints[j]
                val newX = currentJ.x + (targetX - currentJ.x) * 0.35f
                val newY = currentJ.y + (targetY - currentJ.y) * 0.35f
                joints[j] = Offset(newX, newY)

                tentaclePath.lineTo(newX, newY)
            }

            // Fiber-optic light traveling pulse down each filament
            val filamentHue = (baseHue + t * 12f + animTime * 15f) % 360f
            val filamentColor = Color.hsv(filamentHue, if (isFront) 0.65f else 0.85f, 1.0f)
            val baseAlpha = if (isFront) (0.75f + focusGlowBoost) else (0.32f + focusGlowBoost * 0.5f)

            drawScope.drawPath(
                path = tentaclePath,
                color = filamentColor.copy(alpha = baseAlpha.coerceIn(0.15f, 1.0f)),
                style = Stroke(width = if (isFront) 3.0f else 1.8f, cap = StrokeCap.Round)
            )

            // Micro glowing light bead traveling along the tentacle
            val beadJointIdx = ((animTime * 6f + t) % jointsPerTentacle).toInt().coerceIn(1, jointsPerTentacle - 1)
            val beadPos = joints[beadJointIdx]
            drawScope.drawCircle(
                color = Color.White.copy(alpha = if (isFront) 0.98f else 0.55f),
                radius = if (isFront) 3.5f else 2.2f,
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
        val radius = if (isFocused) 22f else 14f

        // Soft outer glow ring
        drawScope.drawCircle(
            color = reticleColor.copy(alpha = if (isFocused) 0.6f else 0.28f),
            radius = radius + 10f,
            center = Offset(gazeX, gazeY),
            style = Stroke(width = 3.5f)
        )

        // Center reticle dot
        drawScope.drawCircle(
            color = reticleColor.copy(alpha = 0.98f),
            radius = if (isFocused) 7f else 4.5f,
            center = Offset(gazeX, gazeY)
        )

        // Crosshair ticks
        val tickLen = 8f
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.9f),
            start = Offset(gazeX - radius - tickLen, gazeY),
            end = Offset(gazeX - radius + 2f, gazeY),
            strokeWidth = 2.5f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.9f),
            start = Offset(gazeX + radius - 2f, gazeY),
            end = Offset(gazeX + radius + tickLen, gazeY),
            strokeWidth = 2.5f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.9f),
            start = Offset(gazeX, gazeY - radius - tickLen),
            end = Offset(gazeX, gazeY - radius + 2f),
            strokeWidth = 2.5f
        )
        drawScope.drawLine(
            color = reticleColor.copy(alpha = 0.9f),
            start = Offset(gazeX, gazeY + radius - 2f),
            end = Offset(gazeX, gazeY + radius + tickLen),
            strokeWidth = 2.5f
        )
    }
}
