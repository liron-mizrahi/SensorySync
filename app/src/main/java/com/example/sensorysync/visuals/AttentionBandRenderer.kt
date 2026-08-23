package com.example.sensorysync.visuals

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.sensorysync.model.ControlState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AttentionBandRenderer {
    private var animTime: Float = 0f
    private var activeRemainingSec: Float = 0f
    private var activeDurationSec: Float = 4f
    private var lastTriggerTimestamp: Long = 0L

    val isRunning: Boolean
        get() = activeRemainingSec > 0f

    val remainingSeconds: Float
        get() = activeRemainingSec

    fun trigger(durationSec: Float) {
        activeDurationSec = durationSec.coerceIn(1f, 30f)
        activeRemainingSec = activeDurationSec
        animTime = 0f
    }

    fun stop() {
        activeRemainingSec = 0f
    }

    fun render(drawScope: DrawScope, state: ControlState, frameDeltaTime: Float) {
        // Check for trigger timestamp from state
        if (state.attentionTriggerTimestamp != lastTriggerTimestamp && state.attentionTriggerTimestamp > 0L) {
            lastTriggerTimestamp = state.attentionTriggerTimestamp
            trigger(state.attentionDurationSec)
        } else if (!state.isAttentionActive && activeRemainingSec > 0f && state.attentionTriggerTimestamp == 0L) {
            stop()
        }

        if (activeRemainingSec <= 0f) return

        val dt = frameDeltaTime.coerceIn(0.001f, 0.05f)
        activeRemainingSec -= dt
        animTime += dt * 3.5f // Rotation speed

        if (activeRemainingSec <= 0f) {
            activeRemainingSec = 0f
            return
        }

        val w = drawScope.size.width
        val h = drawScope.size.height
        val density = drawScope.density

        // Compute fade-in (first 0.2s) and fade-out (last 0.3s)
        val elapsed = activeDurationSec - activeRemainingSec
        val fadeIn = (elapsed / 0.2f).coerceIn(0f, 1f)
        val fadeOut = (activeRemainingSec / 0.3f).coerceIn(0f, 1f)
        val envelope = fadeIn * fadeOut

        val baseOpacity = (state.attentionOpacity.coerceIn(0.1f, 1.0f) * envelope).coerceIn(0f, 1f)
        val bandWidth = (state.attentionBandWidthDp * density).coerceIn(16f, 320f)

        // 1. Generate 12-Stop Rotating Rainbow Spectrum
        val basePhase = (animTime * 90f) % 360f
        val rainbowColors = List(12) { i ->
            val hue = (basePhase + (i * 30f)) % 360f
            Color.hsv(hue, 1.0f, 1.0f, baseOpacity)
        }

        // 2. Multi-stop Linear Gradient Perimeter Borders
        val topBrush = Brush.horizontalGradient(colors = rainbowColors, startX = 0f, endX = w)
        val bottomBrush = Brush.horizontalGradient(colors = rainbowColors.reversed(), startX = 0f, endX = w)
        val leftBrush = Brush.verticalGradient(colors = rainbowColors.reversed(), startY = 0f, endY = h)
        val rightBrush = Brush.verticalGradient(colors = rainbowColors, startY = 0f, endY = h)

        // A. Soft Inward Bloom Glow (Feathered Aura)
        val glowWidth = bandWidth * 1.8f

        // Top Inward Glow
        drawScope.drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(rainbowColors[0].copy(alpha = baseOpacity * 0.6f), Color.Transparent),
                startY = 0f,
                endY = glowWidth
            ),
            topLeft = Offset(0f, 0f),
            size = Size(w, glowWidth)
        )

        // Bottom Inward Glow
        drawScope.drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, rainbowColors[6].copy(alpha = baseOpacity * 0.6f)),
                startY = h - glowWidth,
                endY = h
            ),
            topLeft = Offset(0f, h - glowWidth),
            size = Size(w, glowWidth)
        )

        // Left Inward Glow
        drawScope.drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(rainbowColors[9].copy(alpha = baseOpacity * 0.6f), Color.Transparent),
                startX = 0f,
                endX = glowWidth
            ),
            topLeft = Offset(0f, 0f),
            size = Size(glowWidth, h)
        )

        // Right Inward Glow
        drawScope.drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, rainbowColors[3].copy(alpha = baseOpacity * 0.6f)),
                startX = w - glowWidth,
                endX = w
            ),
            topLeft = Offset(w - glowWidth, 0f),
            size = Size(glowWidth, h)
        )

        // B. Core Solid Vibrant Border Bars
        drawScope.drawRect(brush = topBrush, topLeft = Offset(0f, 0f), size = Size(w, bandWidth))
        drawScope.drawRect(brush = bottomBrush, topLeft = Offset(0f, h - bandWidth), size = Size(w, bandWidth))
        drawScope.drawRect(brush = leftBrush, topLeft = Offset(0f, 0f), size = Size(bandWidth, h))
        drawScope.drawRect(brush = rightBrush, topLeft = Offset(w - bandWidth, 0f), size = Size(bandWidth, h))

        // C. Crisp Inner White Holographic Accent Line
        val innerLineColor = Color.White.copy(alpha = 0.9f * baseOpacity)
        drawScope.drawRect(
            color = innerLineColor,
            topLeft = Offset(bandWidth, bandWidth),
            size = Size(w - 2 * bandWidth, h - 2 * bandWidth),
            style = Stroke(width = 3.5f)
        )

        // D. 4 Corner Rotating Prismatic Shimmer Stars
        renderCornerShimmer(drawScope, bandWidth * 0.9f, bandWidth * 0.9f, animTime, baseOpacity)
        renderCornerShimmer(drawScope, w - bandWidth * 0.9f, bandWidth * 0.9f, animTime + 1f, baseOpacity)
        renderCornerShimmer(drawScope, w - bandWidth * 0.9f, h - bandWidth * 0.9f, animTime + 2f, baseOpacity)
        renderCornerShimmer(drawScope, bandWidth * 0.9f, h - bandWidth * 0.9f, animTime + 3f, baseOpacity)
    }

    private fun renderCornerShimmer(
        drawScope: DrawScope,
        cx: Float,
        cy: Float,
        time: Float,
        opacity: Float
    ) {
        val pulse = (sin(time * 6f) * 0.3f + 0.7f)
        val starRadius = 24f * pulse
        val starColor = Color.White.copy(alpha = 0.95f * opacity)

        drawScope.drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.45f * opacity),
            radius = starRadius * 1.8f,
            center = Offset(cx, cy)
        )
        drawScope.drawCircle(
            color = starColor,
            radius = 4.0f * pulse,
            center = Offset(cx, cy)
        )

        for (a in 0..3) {
            val angle = (time * 1.5f + (a * PI.toFloat() / 2f))
            val ex = cx + cos(angle) * starRadius
            val ey = cy + sin(angle) * starRadius
            drawScope.drawLine(
                color = starColor,
                start = Offset(cx, cy),
                end = Offset(ex, ey),
                strokeWidth = 2.5f
            )
        }
    }
}
