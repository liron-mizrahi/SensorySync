package com.example.sensorysync.visuals

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.sensorysync.model.ControlState
import com.example.sensorysync.model.VisualPattern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class VisualPatternRenderer {

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var hueOffset: Float
    )

    private val particles = List(300) {
        Particle(
            x = (Math.random()).toFloat(),
            y = (Math.random()).toFloat(),
            vx = ((Math.random() - 0.5) * 0.002).toFloat(),
            vy = ((Math.random() - 0.5) * 0.002).toFloat(),
            size = (2f + Math.random() * 4f).toFloat(),
            hueOffset = (Math.random() * 60f).toFloat()
        )
    }

    private var animTime = 0f

    fun render(drawScope: DrawScope, state: ControlState, frameDeltaTime: Float) {
        animTime += frameDeltaTime * state.speedMultiplier

        when (state.activePattern) {
            VisualPattern.HARMONIC_PARTICLES -> renderParticles(drawScope, state)
            VisualPattern.SACRED_MANDALA -> renderMandala(drawScope, state)
            VisualPattern.ISOCHRONIC_STROBE -> renderIsochronicStrobe(drawScope, state)
            VisualPattern.CHLADNI_RIPPLES -> renderChladniRipples(drawScope, state)
            VisualPattern.WARP_TUNNEL -> renderWarpTunnel(drawScope, state)
            VisualPattern.SWIRLING_SMOKE -> renderFluidSmoke(drawScope, state)
        }
    }


    private fun renderParticles(drawScope: DrawScope, state: ControlState) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        // Determine attraction focal points (Gaze, Hands, Touch)
        val targetPoints = mutableListOf<Offset>()
        if (state.gazeData.isFaceDetected) {
            targetPoints.add(Offset(state.gazeData.gazePosition.x * width, state.gazeData.gazePosition.y * height))
        }
        if (state.leftHand.isPresent) {
            targetPoints.add(Offset(state.leftHand.position.x * width, state.leftHand.position.y * height))
        }
        if (state.rightHand.isPresent) {
            targetPoints.add(Offset(state.rightHand.position.x * width, state.rightHand.position.y * height))
        }
        state.activeTouchPoints.forEach { touch ->
            targetPoints.add(touch.position)
        }
        if (targetPoints.isEmpty()) {
            targetPoints.add(Offset(width * 0.5f, height * 0.5f))
        }

        // Draw connecting constellation lines & update particles
        val countToUse = state.particleCount.coerceIn(50, particles.size)
        val baseHue = state.primaryHue

        for (i in 0 until countToUse) {
            val p = particles[i]

            // Gravitate to closest target point
            var closestDist = Float.MAX_VALUE
            var targetX = width * 0.5f
            var targetY = height * 0.5f

            val px = p.x * width
            val py = p.y * height

            for (tp in targetPoints) {
                val dx = tp.x - px
                val dy = tp.y - py
                val distSq = dx * dx + dy * dy
                if (distSq < closestDist) {
                    closestDist = distSq
                    targetX = tp.x
                    targetY = tp.y
                }
            }

            // Spring force
            val fx = (targetX - px) * 0.0003f
            val fy = (targetY - py) * 0.0003f

            p.vx = (p.vx + fx) * 0.95f
            p.vy = (p.vy + fy) * 0.95f

            p.x += p.vx
            p.y += p.vy

            // Wrap boundaries
            if (p.x < 0f) p.x = 1f
            if (p.x > 1f) p.x = 0f
            if (p.y < 0f) p.y = 1f
            if (p.y > 1f) p.y = 0f

            val curPx = p.x * width
            val curPy = p.y * height

            val particleColor = Color.hsv((baseHue + p.hueOffset) % 360f, state.saturation, 0.95f)

            // Draw particle
            drawScope.drawCircle(
                color = particleColor,
                radius = p.size * (1f + (1f - state.leftHand.pinchDistance) * 2f),
                center = Offset(curPx, curPy)
            )

            // Draw constellation lines between nearby particles
            if (i % 3 == 0) {
                for (j in (i + 1) until (i + 8).coerceAtMost(countToUse)) {
                    val p2 = particles[j]
                    val dx = (p2.x - p.x) * width
                    val dy = (p2.y - p.y) * height
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist < 120f) {
                        val alpha = ((1f - dist / 120f) * 0.4f).coerceIn(0f, 1f)
                        drawScope.drawLine(
                            color = particleColor.copy(alpha = alpha),
                            start = Offset(curPx, curPy),
                            end = Offset(p2.x * width, p2.y * height),
                            strokeWidth = 1.5f
                        )
                    }
                }
            }
        }
    }

    private fun renderMandala(drawScope: DrawScope, state: ControlState) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        val center = Offset(
            if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.x * width else width * 0.5f,
            if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.y * height else height * 0.5f
        )

        val maxRadius = (width.coerceAtMost(height) * 0.4f)
        val numLayers = 6
        val baseHue = state.primaryHue

        // Pinch or two-hand stretch alters scale
        val scaleFactor = (0.5f + (1f - state.leftHand.pinchDistance) * 1.5f).coerceIn(0.3f, 2.5f)

        for (layer in 1..numLayers) {
            val radius = (maxRadius * (layer.toFloat() / numLayers)) * scaleFactor
            val sides = 3 + layer * 2
            val rotAngle = (animTime * (20f / layer) + layer * 15f) % 360f

            val hue = (baseHue + layer * 25f) % 360f
            val layerColor = Color.hsv(hue, state.saturation, 0.9f)

            drawScope.rotate(degrees = rotAngle, pivot = center) {
                val path = Path()
                for (i in 0 until sides) {
                    val angle = (2.0 * PI * i / sides)
                    val x = center.x + (radius * cos(angle)).toFloat()
                    val y = center.y + (radius * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                drawPath(
                    path = path,
                    color = layerColor,
                    style = Stroke(width = 3f + layer)
                )

                // Sub-circles at vertices
                for (i in 0 until sides) {
                    val angle = (2.0 * PI * i / sides)
                    val x = center.x + (radius * cos(angle)).toFloat()
                    val y = center.y + (radius * sin(angle)).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = 4f + layer,
                        center = Offset(x, y)
                    )
                }
            }

        }
    }

    private fun renderIsochronicStrobe(drawScope: DrawScope, state: ControlState) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        // Enforce Seizure & Epilepsy Safety: Strict 3.0 Hz maximum pulse ceiling
        val strobeFreq = if (state.isPhotosafetyEnabled) {
            state.strobeFrequencyHz.coerceIn(0.2f, 3.0f)
        } else {
            state.strobeFrequencyHz.coerceIn(0.2f, 5.0f)
        }

        val strobePhase = (animTime * strobeFreq * 2.0 * PI)
        val pulseVal = ((sin(strobePhase) + 1.0) / 2.0).toFloat()

        val baseHue = (state.primaryHue + animTime * 4f) % 360f
        val pulseColor = Color.hsv(baseHue, state.saturation, (0.3f + pulseVal * 0.5f).coerceIn(0f, 1f))

        // Background soft breathing glow (never black flash)
        drawScope.drawRect(color = pulseColor.copy(alpha = 0.25f))


        // Expanding concentric rings from gaze center
        val focalX = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.x * width else width * 0.5f
        val focalY = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.y * height else height * 0.5f

        val ringCount = 8
        val maxR = width.coerceAtLeast(height) * 0.6f

        for (r in 0 until ringCount) {
            val progress = ((animTime * 0.5f + r.toFloat() / ringCount) % 1.0f)
            val currentRadius = progress * maxR
            val alpha = (1f - progress) * pulseVal

            drawScope.drawCircle(
                color = pulseColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = currentRadius,
                center = Offset(focalX, focalY),
                style = Stroke(width = 6f + progress * 10f)
            )
        }
    }

    private fun renderChladniRipples(drawScope: DrawScope, state: ControlState) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        val rows = 12
        val cols = 16

        val cellW = width / cols
        val cellH = height / rows

        val baseHue = state.primaryHue
        val handX = if (state.rightHand.isPresent) state.rightHand.position.x else 0.5f
        val handY = if (state.rightHand.isPresent) state.rightHand.position.y else 0.5f

        val m = (3 + (handX * 5).toInt()).toFloat()
        val n = (2 + (handY * 5).toInt()).toFloat()

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val nx = (c.toFloat() / cols) * PI
                val ny = (r.toFloat() / rows) * PI

                // Chladni formula: a*sin(n*x)*sin(m*y) + b*sin(m*x)*sin(n*y)
                val wave = sin(n * nx) * sin(m * ny) + cos(m * nx) * cos(n * ny) + sin(animTime * 3f)
                val normWave = ((wave + 2.0) / 4.0).toFloat().coerceIn(0f, 1f)

                val color = Color.hsv((baseHue + normWave * 120f) % 360f, state.saturation, normWave)

                val rectX = c * cellW
                val rectY = r * cellH

                drawScope.drawRect(
                    color = color.copy(alpha = 0.8f),
                    topLeft = Offset(rectX + cellW * 0.1f, rectY + cellH * 0.1f),
                    size = Size(cellW * 0.8f * normWave, cellH * 0.8f * normWave)
                )
            }
        }
    }

    private fun renderWarpTunnel(drawScope: DrawScope, state: ControlState) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        val gazeX = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.x * width else width * 0.5f
        val gazeY = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.y * height else height * 0.5f
        val center = Offset(gazeX, gazeY)

        val tunnelRays = 24
        val baseHue = state.primaryHue

        // Radial lines outward
        for (i in 0 until tunnelRays) {
            val angle = (2.0 * PI * i / tunnelRays + animTime * 0.2)
            val rayLength = width.coerceAtLeast(height)

            val endX = center.x + (rayLength * cos(angle)).toFloat()
            val endY = center.y + (rayLength * sin(angle)).toFloat()

            val hue = (baseHue + i * (360f / tunnelRays)) % 360f

            drawScope.drawLine(
                color = Color.hsv(hue, state.saturation, 0.7f).copy(alpha = 0.5f),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2f
            )
        }

        // Concentric depth rectangles
        val numDepths = 10
        val maxR = width.coerceAtLeast(height) * 0.8f

        for (d in 0 until numDepths) {
            val progress = ((animTime * state.speedMultiplier * 0.8f + d.toFloat() / numDepths) % 1.0f)
            val depthR = progress * maxR
            val alpha = progress.coerceIn(0f, 1f)

            val hue = (baseHue + d * 20f) % 360f

            drawScope.drawRect(
                color = Color.hsv(hue, state.saturation, 0.9f).copy(alpha = alpha),
                topLeft = Offset(center.x - depthR, center.y - depthR),
                size = Size(depthR * 2f, depthR * 2f),
                style = Stroke(width = 2f + progress * 8f)
            )
        }
    }

    private fun renderFluidSmoke(drawScope: DrawScope, state: ControlState) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        val gazeX = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.x * width else width * 0.5f
        val gazeY = if (state.gazeData.isFaceDetected) state.gazeData.gazePosition.y * height else height * 0.5f

        val baseHue = state.primaryHue
        val countToUse = (particles.size * 0.8f).toInt()

        // Render fluid smoke tendrils & swirling ink
        for (i in 0 until countToUse) {
            val p = particles[i]

            val px = p.x * width
            val py = p.y * height

            // Vorticity curling velocity (swirling fluid dynamics)
            val dx = gazeX - px
            val dy = gazeY - py
            val distSq = (dx * dx + dy * dy).coerceAtLeast(100f)

            // Tangential vortex force (perpendicular swirl)
            val swirlX = -dy / distSq * 15f
            val swirlY = dx / distSq * 15f

            // Upward thermal buoyancy for smoke + vortex
            p.vx = (p.vx + swirlX * 0.05f) * 0.96f
            p.vy = (p.vy + swirlY * 0.05f - 0.0001f) * 0.96f

            p.x += p.vx
            p.y += p.vy

            // Soft wrap
            if (p.x < -0.1f) p.x = 1.1f
            if (p.x > 1.1f) p.x = -0.1f
            if (p.y < -0.1f) p.y = 1.1f
            if (p.y > 1.1f) p.y = -0.1f

            val curPx = p.x * width
            val curPy = p.y * height

            // Color gradient blend (Cyan -> Magenta -> Gold)
            val hue = (baseHue + (i % 3) * 60f + animTime * 5f) % 360f
            val alpha = (0.25f + sin(animTime * 2f + i) * 0.15f).coerceIn(0.1f, 0.6f)
            val fluidColor = Color.hsv(hue, state.saturation, 0.95f).copy(alpha = alpha)

            // Fluid smoke soft plume (layered circles with soft radius)
            val plumeRadius = p.size * 6f * (1f + (1f - state.leftHand.pinchDistance) * 1.5f)

            drawScope.drawCircle(
                color = fluidColor,
                radius = plumeRadius,
                center = Offset(curPx, curPy)
            )

            // Wispy tendril lines
            if (i % 4 == 0) {
                val nextP = particles[(i + 1) % countToUse]
                drawScope.drawLine(
                    color = fluidColor.copy(alpha = alpha * 0.5f),
                    start = Offset(curPx, curPy),
                    end = Offset(nextP.x * width, nextP.y * height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

