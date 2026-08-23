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

class BubbleBloomRenderer {

    // Stardust and background cosmic particles
    private class CosmicParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Float,
        var color: Color
    )

    // Nebula cloud cluster
    private class NebulaCloud(
        val x: Float,
        val y: Float,
        val radius: Float,
        val color: Color
    )

    // Shard & Droplet particles spawned when a bubble pops (Matching Reference Visual)
    private class PopShard(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        var size: Float,
        var color: Color,
        var isDroplet: Boolean
    )

    // Individual floating iridescent soap bubble
    private class Bubble(
        var id: Int,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var baseRadius: Float,
        var wobblePhase: Float,
        var wobbleSpeed: Float,
        var hueOffset: Float,
        var dwellTimer: Float = 0f,
        var isPopping: Boolean = false,
        var popProgress: Float = 0f,
        var popRuptureAngle: Float = 0f,
        val shards: MutableList<PopShard> = mutableListOf()
    )

    private val cosmicParticles = List(200) {
        val isCyan = Math.random() < 0.55
        val color = if (isCyan) Color(0xFF80DEEA) else Color(0xFFE1BEE7)
        CosmicParticle(
            x = Math.random().toFloat(),
            y = Math.random().toFloat(),
            vx = ((Math.random() - 0.5) * 0.00015).toFloat(),
            vy = ((Math.random() - 0.5) * 0.00015).toFloat(),
            radius = (0.8f + Math.random() * 2.5f).toFloat(),
            alpha = (0.2f + Math.random() * 0.65f).toFloat(),
            color = color
        )
    }

    private val nebulaClouds = listOf(
        NebulaCloud(0.28f, 0.45f, 400f, Color(0xFF311B92).copy(alpha = 0.26f)),
        NebulaCloud(0.72f, 0.35f, 450f, Color(0xFF004D40).copy(alpha = 0.20f)),
        NebulaCloud(0.50f, 0.75f, 380f, Color(0xFF880E4F).copy(alpha = 0.18f))
    )

    private val bubbles = mutableListOf<Bubble>()
    private var animTime = 0f
    private var totalPoppedCount = 0L

    var activeFocusedIndex: Int? = null
        private set
    var currentDwellProgress: Float = 0.0f
        private set

    @Volatile
    private var pendingPopRandom = false
    @Volatile
    private var pendingPopAll = false
    @Volatile
    private var pendingResetBubbles = false

    fun triggerPopRandom() { pendingPopRandom = true }
    fun triggerPopAll() { pendingPopAll = true }
    fun triggerResetBubbles() { pendingResetBubbles = true }

    init {
        ensureBubbleCount(12)
    }

    private fun ensureBubbleCount(count: Int) {
        val target = count.coerceIn(3, 25)
        while (bubbles.size < target) {
            bubbles.add(createRandomBubble(bubbles.size, isInitialSpawn = true))
        }
        while (bubbles.size > target) {
            bubbles.removeAt(bubbles.size - 1)
        }
    }

    private fun createRandomBubble(id: Int, isInitialSpawn: Boolean = false): Bubble {
        val startY = if (isInitialSpawn) (0.05f + Math.random() * 0.90f).toFloat() else (1.15f + Math.random() * 0.15f).toFloat()
        val startX = (0.06f + Math.random() * 0.88f).toFloat()
        val sizeVariation = (42f + Math.random() * 68f).toFloat()
        val speedY = (-0.00035f - Math.random() * 0.00045f).toFloat()
        val speedX = ((Math.random() - 0.5) * 0.00025f).toFloat()

        return Bubble(
            id = id,
            x = startX,
            y = startY,
            vx = speedX,
            vy = speedY,
            baseRadius = sizeVariation,
            wobblePhase = (Math.random() * 2.0 * PI).toFloat(),
            wobbleSpeed = (1.5f + Math.random() * 1.5f).toFloat(),
            hueOffset = (Math.random() * 360f).toFloat()
        )
    }

    fun handleTap(normX: Float, normY: Float, width: Float, height: Float, scale: Float): Boolean {
        val tapPx = normX * width
        val tapPy = normY * height

        for (i in bubbles.indices) {
            val b = bubbles[i]
            if (b.isPopping) continue

            val bx = b.x * width
            val by = b.y * height
            val r = b.baseRadius * scale

            val dist = hypot(tapPx - bx, tapPy - by)
            if (dist < r * 1.35f) {
                b.isPopping = true
                b.popProgress = 0f
                totalPoppedCount++
                b.popRuptureAngle = ((Math.random() - 0.5) * 0.8).toFloat()
                spawnPopShards(b, width, height, scale)
                return true
            }
        }
        return false
    }

    fun render(drawScope: DrawScope, state: ControlState, frameDeltaTime: Float) {
        val dt = frameDeltaTime.coerceIn(0.001f, 0.05f)
        animTime += dt * state.speedMultiplier

        val width = drawScope.size.width
        val height = drawScope.size.height
        val scale = state.bubbleScale.coerceIn(0.5f, 2.0f)
        val dwellTargetSec = state.bubbleDwellTimeSec.coerceIn(0.5f, 3.0f)

        if (pendingResetBubbles) {
            pendingResetBubbles = false
            bubbles.clear()
        }

        ensureBubbleCount(state.bubbleCount)

        if (pendingPopRandom) {
            pendingPopRandom = false
            val candidate = bubbles.filter { !it.isPopping }.randomOrNull()
            if (candidate != null) {
                candidate.isPopping = true
                candidate.popProgress = 0f
                totalPoppedCount++
                candidate.popRuptureAngle = ((Math.random() - 0.5) * 0.8).toFloat()
                spawnPopShards(candidate, width, height, scale)
            }
        }

        if (pendingPopAll) {
            pendingPopAll = false
            for (b in bubbles) {
                if (!b.isPopping) {
                    b.isPopping = true
                    b.popProgress = 0f
                    totalPoppedCount++
                    b.popRuptureAngle = ((Math.random() - 0.5) * 0.8).toFloat()
                    spawnPopShards(b, width, height, scale)
                }
            }
        }

        // 1. Render Cosmic Nebula & Starfield
        renderCosmicBackground(drawScope, width, height)

        // 2. Gaze Targeting Logic
        val gazeX = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.x else 0.5f
        val gazeY = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.y else 0.5f
        val gazePx = gazeX * width
        val gazePy = gazeY * height

        var bestTargetIdx: Int? = null
        var bestDist = Float.MAX_VALUE

        if (state.gazeData.isFaceDetected) {
            for (i in bubbles.indices) {
                val b = bubbles[i]
                if (b.isPopping) continue

                val bx = b.x * width
                val by = b.y * height
                val r = b.baseRadius * scale

                val dist = hypot(gazePx - bx, gazePy - by)
                if (dist < r * 1.25f && dist < bestDist) {
                    bestDist = dist
                    bestTargetIdx = i
                }
            }
        }

        activeFocusedIndex = bestTargetIdx
        currentDwellProgress = 0f

        // 3. Update and Render Bubbles
        for (i in bubbles.indices) {
            val b = bubbles[i]
            val isTargeted = (i == bestTargetIdx)

            if (b.isPopping) {
                b.popProgress += dt / 0.55f
                updateAndRenderPopShards(drawScope, b, dt)

                if (b.popProgress >= 1.0f) {
                    val newB = createRandomBubble(b.id, isInitialSpawn = false)
                    bubbles[i] = newB
                }
                continue
            }

            // Gaze Dwell accumulation
            if (isTargeted) {
                b.dwellTimer += dt
                currentDwellProgress = (b.dwellTimer / dwellTargetSec).coerceIn(0f, 1f)

                if (b.dwellTimer >= dwellTargetSec) {
                    // TRIGGER POP EXPLOSION!
                    b.isPopping = true
                    b.popProgress = 0f
                    totalPoppedCount++
                    b.popRuptureAngle = ((Math.random() - 0.5) * 0.8).toFloat()
                    spawnPopShards(b, width, height, scale)
                    continue
                }
            } else {
                b.dwellTimer = (b.dwellTimer - dt * 1.5f).coerceAtLeast(0f)
            }

            // Bubble Buoyancy & Drift Movement
            b.wobblePhase += dt * b.wobbleSpeed
            val swayX = sin(animTime * 1.2f + b.id * 1.5f) * 0.00035f
            b.x = (b.x + (b.vx + swayX) * state.speedMultiplier)
            b.y += b.vy * state.speedMultiplier

            if (b.x < 0.04f) { b.x = 0.04f; b.vx = abs(b.vx) }
            if (b.x > 0.96f) { b.x = 0.96f; b.vx = -abs(b.vx) }
            if (b.y < -0.15f) {
                val newB = createRandomBubble(b.id, isInitialSpawn = false)
                bubbles[i] = newB
                continue
            }

            val bx = b.x * width
            val by = b.y * height
            val currentRadius = b.baseRadius * scale

            // Render Photorealistic Iridescent Bubble Matching Reference
            renderIridescentSoapBubble(drawScope, b, bx, by, currentRadius, isTargeted, b.dwellTimer / dwellTargetSec)
        }

        // 4. Render Eye Gaze Reticle
        if (state.gazeData.isFaceDetected && state.showGazeMarker) {
            renderGazeReticleSmall(drawScope, state, gazePx, gazePy, activeFocusedIndex != null)
        }
    }

    private fun renderCosmicBackground(drawScope: DrawScope, width: Float, height: Float) {
        // Deep Nebula Clouds
        for (nebula in nebulaClouds) {
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(nebula.color, Color.Transparent),
                    center = Offset(nebula.x * width, nebula.y * height),
                    radius = nebula.radius
                ),
                radius = nebula.radius,
                center = Offset(nebula.x * width, nebula.y * height)
            )
        }

        // Starfield Stardust Particles
        for (p in cosmicParticles) {
            p.x = (p.x + p.vx + 1f) % 1f
            p.y = (p.y + p.vy + 1f) % 1f

            val sx = p.x * width
            val sy = p.y * height

            drawScope.drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(sx, sy)
            )
        }
    }

    private fun renderIridescentSoapBubble(
        drawScope: DrawScope,
        bubble: Bubble,
        centerX: Float,
        centerY: Float,
        radius: Float,
        isTargeted: Boolean,
        dwellProgress: Float
    ) {
        val wobbleX = 1f + sin(bubble.wobblePhase) * 0.045f
        val wobbleY = 1f + cos(bubble.wobblePhase) * 0.045f

        val chargeVibration = if (isTargeted) sin(animTime * 35f) * (dwellProgress * 4.0f) else 0f
        val rx = radius * wobbleX + chargeVibration
        val ry = radius * wobbleY - chargeVibration

        // 1. Luminous Charging Aura / Gaze Focus Ring
        if (isTargeted && dwellProgress > 0.05f) {
            val auraRadius = radius * (1.20f + dwellProgress * 0.25f)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.50f * dwellProgress),
                        Color(0xFFE040FB).copy(alpha = 0.30f * dwellProgress),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = Offset(centerX, centerY)
            )

            drawScope.drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFFE040FB), Color(0xFF69F0AE), Color(0xFF00E5FF))
                ),
                startAngle = -90f,
                sweepAngle = dwellProgress * 360f,
                useCenter = false,
                topLeft = Offset(centerX - rx - 6f, centerY - ry - 6f),
                size = Size((rx + 6f) * 2, (ry + 6f) * 2),
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )
        }

        // 2. Translucent Glass Body with Internal Chromatic Sheen (Matching Reference Visual)
        drawScope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00E5FF).copy(alpha = 0.06f),
                    Color(0xFFE040FB).copy(alpha = 0.14f),
                    Color(0xFF69F0AE).copy(alpha = 0.22f),
                    Color(0xFF00E5FF).copy(alpha = 0.45f)
                ),
                center = Offset(centerX - rx * 0.15f, centerY - ry * 0.15f),
                radius = rx * 1.02f
            ),
            topLeft = Offset(centerX - rx, centerY - ry),
            size = Size(rx * 2, ry * 2)
        )

        // 3. Soap Film Thin-Film Rainbow Iridescence (Soft Edge Ring)
        val swirlAngle = (animTime * 16f + bubble.hueOffset) % 360f
        drawScope.rotate(degrees = swirlAngle, pivot = Offset(centerX, centerY)) {
            val surfaceSwirlBrush = Brush.sweepGradient(
                listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.85f), // Bright Cyan
                    Color(0xFF69F0AE).copy(alpha = 0.75f), // Emerald Lime
                    Color(0xFFFFD54F).copy(alpha = 0.80f), // Golden Amber
                    Color(0xFFFF4081).copy(alpha = 0.85f), // Coral Pink
                    Color(0xFFE040FB).copy(alpha = 0.90f), // Magenta Violet
                    Color(0xFF00E5FF).copy(alpha = 0.85f)  // Bright Cyan
                )
            )
            drawScope.drawOval(
                brush = surfaceSwirlBrush,
                topLeft = Offset(centerX - rx, centerY - ry),
                size = Size(rx * 2, ry * 2),
                style = Stroke(width = rx * 0.085f)
            )
        }

        // 4. Primary Crescent Specular Highlight (Top-Left Glint)
        val glintPath = Path().apply {
            val gx = centerX - rx * 0.55f
            val gy = centerY - ry * 0.55f
            val gw = rx * 0.70f
            val gh = ry * 0.42f
            moveTo(gx, gy + gh * 0.5f)
            cubicTo(gx, gy, gx + gw * 0.5f, gy, gx + gw, gy + gh * 0.2f)
            cubicTo(gx + gw * 0.7f, gy + gh * 0.35f, gx + gw * 0.2f, gy + gh * 0.5f, gx, gy + gh * 0.5f)
            close()
        }
        drawScope.drawPath(
            path = glintPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.98f),
                    Color(0xFFE0F7FA).copy(alpha = 0.75f),
                    Color.Transparent
                ),
                center = Offset(centerX - rx * 0.40f, centerY - ry * 0.40f),
                radius = rx * 0.42f
            )
        )

        // 5. Secondary Bounce Reflection (Bottom-Right Glint)
        val bouncePath = Path().apply {
            val bx = centerX + rx * 0.25f
            val by = centerY + ry * 0.35f
            val bw = rx * 0.50f
            val bh = ry * 0.30f
            moveTo(bx, by)
            cubicTo(bx + bw * 0.5f, by + bh * 0.7f, bx + bw, by + bh * 0.4f, bx + bw, by)
            close()
        }
        drawScope.drawPath(
            path = bouncePath,
            color = Color(0xFFE1BEE7).copy(alpha = 0.60f)
        )

        // 6. Feathered Optical Edge Bloom
        drawScope.drawOval(
            color = Color(0xFFE0F7FA).copy(alpha = 0.92f),
            topLeft = Offset(centerX - rx, centerY - ry),
            size = Size(rx * 2, ry * 2),
            style = Stroke(width = 1.4f)
        )
    }

    private fun spawnPopShards(bubble: Bubble, width: Float, height: Float, scale: Float) {
        bubble.shards.clear()
        val numShards = 220
        val bx = bubble.x * width
        val by = bubble.y * height
        val r = bubble.baseRadius * scale

        for (i in 0 until numShards) {
            // Directional burst cone to the right side matching reference image
            val isMainCone = Math.random() < 0.80
            val baseAngle = if (isMainCone) {
                ((Math.random() - 0.5) * 1.3).toFloat()
            } else {
                (Math.random() * 2.0 * PI).toFloat()
            }

            val speed = if (isMainCone) (90f + Math.random() * 450f).toFloat() else (40f + Math.random() * 180f).toFloat()

            val isDroplet = Math.random() < 0.75
            val size = if (isDroplet) (1.5f + Math.random() * 3.6f).toFloat() else (4.0f + Math.random() * 8.5f).toFloat()
            val life = (0.75f + Math.random() * 0.55f).toFloat()

            val colorIdx = (Math.random() * 6).toInt()
            val color = when (colorIdx) {
                0 -> Color(0xFF00E5FF) // Cyan
                1 -> Color(0xFFE040FB) // Magenta
                2 -> Color(0xFF69F0AE) // Lime
                3 -> Color(0xFFFFD54F) // Gold
                4 -> Color(0xFF80DEEA) // Ice Blue
                else -> Color.White
            }

            val spawnOffsetDist = (r * (0.85f + Math.random() * 0.35f)).toFloat()
            val sx = bx + cos(baseAngle) * spawnOffsetDist
            val sy = by + sin(baseAngle) * spawnOffsetDist

            bubble.shards.add(
                PopShard(
                    x = sx,
                    y = sy,
                    vx = cos(baseAngle) * speed,
                    vy = sin(baseAngle) * speed - (10f + Math.random() * 30f).toFloat(),
                    life = life,
                    maxLife = life,
                    size = size,
                    color = color,
                    isDroplet = isDroplet
                )
            )
        }
    }

    private fun updateAndRenderPopShards(drawScope: DrawScope, bubble: Bubble, dt: Float) {
        val it = bubble.shards.iterator()

        // Expanding Shockwave Ring
        val shockRadius = bubble.baseRadius * (1f + bubble.popProgress * 2.8f)
        val shockAlpha = (1f - bubble.popProgress).coerceIn(0f, 1f)
        drawScope.drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = shockAlpha * 0.65f),
            radius = shockRadius,
            center = Offset(bubble.x * drawScope.size.width, bubble.y * drawScope.size.height),
            style = Stroke(width = 2.4f * (1f - bubble.popProgress))
        )

        // Ruptured Tearing Soap Film Arc (Matching Reference Visual)
        if (bubble.popProgress < 0.70f) {
            val remainAlpha = (1f - (bubble.popProgress / 0.70f)).coerceIn(0f, 1f)
            val bx = bubble.x * drawScope.size.width
            val by = bubble.y * drawScope.size.height
            val r = bubble.baseRadius

            drawScope.drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFFE040FB), Color(0xFF69F0AE))
                ),
                startAngle = 100f,
                sweepAngle = 230f * (1f - bubble.popProgress),
                useCenter = false,
                topLeft = Offset(bx - r, by - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2.8f * remainAlpha)
            )
        }

        // Bursting Shards & Droplets
        while (it.hasNext()) {
            val s = it.next()
            s.life -= dt
            if (s.life <= 0f) {
                it.remove()
                continue
            }

            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vy += 80f * dt

            val progress = (s.life / s.maxLife).coerceIn(0f, 1f)
            val currentAlpha = progress

            if (s.isDroplet) {
                drawScope.drawCircle(
                    color = s.color.copy(alpha = currentAlpha),
                    radius = s.size * (0.6f + progress * 0.4f),
                    center = Offset(s.x, s.y)
                )
            } else {
                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = currentAlpha),
                            s.color.copy(alpha = currentAlpha * 0.7f),
                            Color.Transparent
                        ),
                        center = Offset(s.x, s.y),
                        radius = s.size
                    ),
                    radius = s.size,
                    center = Offset(s.x, s.y)
                )
            }
        }
    }

    private fun renderGazeReticleSmall(
        drawScope: DrawScope,
        state: ControlState,
        gazeX: Float,
        gazeY: Float,
        isTargetingBubble: Boolean
    ) {
        if (!state.showGazeMarker) return

        val markerScale = state.gazeMarkerSize.coerceIn(0.5f, 3.0f)
        val markerOpacity = state.gazeMarkerOpacity.coerceIn(0.1f, 1.0f)

        val baseColor = when (state.gazeMarkerColor.uppercase()) {
            "GREEN" -> Color(0xFF00E676)
            "MAGENTA", "PINK" -> Color(0xFFE040FB)
            "GOLD", "YELLOW" -> Color(0xFFFFD54F)
            "WHITE" -> Color.White
            else -> Color(0xFF00E5FF) // CYAN
        }

        val primaryColor = if (state.isFaceLocked) Color(0xFF00E676) else baseColor
        val ringRadius = (if (isTargetingBubble) 18f else 13f) * markerScale

        // 1. Soft Outer Glowing Aura
        drawScope.drawCircle(
            color = (if (isTargetingBubble) Color(0xFF69F0AE) else primaryColor).copy(alpha = 0.25f * markerOpacity),
            radius = ringRadius + (8f * markerScale),
            center = Offset(gazeX, gazeY)
        )

        // 2. Main Target Ring
        drawScope.drawCircle(
            color = primaryColor.copy(alpha = (if (isTargetingBubble) 0.90f else 0.65f) * markerOpacity),
            radius = ringRadius,
            center = Offset(gazeX, gazeY),
            style = Stroke(width = (if (isTargetingBubble) 2.5f else 1.8f) * markerScale)
        )

        // 3. Center Solid Focal Pip
        drawScope.drawCircle(
            color = (if (isTargetingBubble) Color.White else primaryColor).copy(alpha = 0.95f * markerOpacity),
            radius = (if (isTargetingBubble) 4.0f else 3.0f) * markerScale,
            center = Offset(gazeX, gazeY)
        )

        // 4. Crosshair Ticks
        val tickLen = 7f * markerScale
        val strokeW = 1.8f * markerScale
        val tickAlpha = 0.85f * markerOpacity
        val tickColor = primaryColor.copy(alpha = tickAlpha)

        // Left tick
        drawScope.drawLine(
            color = tickColor,
            start = Offset(gazeX - ringRadius - tickLen, gazeY),
            end = Offset(gazeX - ringRadius + (1.5f * markerScale), gazeY),
            strokeWidth = strokeW
        )
        // Right tick
        drawScope.drawLine(
            color = tickColor,
            start = Offset(gazeX + ringRadius - (1.5f * markerScale), gazeY),
            end = Offset(gazeX + ringRadius + tickLen, gazeY),
            strokeWidth = strokeW
        )
        // Top tick
        drawScope.drawLine(
            color = tickColor,
            start = Offset(gazeX, gazeY - ringRadius - tickLen),
            end = Offset(gazeX, gazeY - ringRadius + (1.5f * markerScale)),
            strokeWidth = strokeW
        )
        // Bottom tick
        drawScope.drawLine(
            color = tickColor,
            start = Offset(gazeX, gazeY + ringRadius - (1.5f * markerScale)),
            end = Offset(gazeX, gazeY + ringRadius + tickLen),
            strokeWidth = strokeW
        )
    }
}
