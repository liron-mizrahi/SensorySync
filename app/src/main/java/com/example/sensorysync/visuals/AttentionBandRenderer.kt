package com.example.sensorysync.visuals

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
        animTime += dt * 2.8f // Smooth rotation speed

        if (activeRemainingSec <= 0f) {
            activeRemainingSec = 0f
            return
        }

        val w = drawScope.size.width
        val h = drawScope.size.height
        val density = drawScope.density

        // Compute fade-in (first 0.2s) and fade-out (last 0.35s)
        val elapsed = activeDurationSec - activeRemainingSec
        val fadeIn = (elapsed / 0.2f).coerceIn(0f, 1f)
        val fadeOut = (activeRemainingSec / 0.35f).coerceIn(0f, 1f)
        val envelope = fadeIn * fadeOut

        val baseOpacity = (state.attentionOpacity.coerceIn(0.1f, 1.0f) * envelope).coerceIn(0f, 1f)
        val bandWidth = (state.attentionBandWidthDp * density).coerceIn(14f, 280f)
        val cornerRad = (bandWidth * 1.5f + 32f * density).coerceIn(24f, 180f)
        val center = Offset(w / 2f, h / 2f)

        // 1. Generate 16-Stop Rotating Rainbow Spectrum for continuous Sweep Gradient
        val basePhase = (animTime * 85f) % 360f
        val rainbowColors = List(17) { i ->
            val hue = (basePhase + (i * (360f / 16f))) % 360f
            Color.hsv(hue, 1.0f, 1.0f, baseOpacity)
        }
        val glowColors = rainbowColors.map { it.copy(alpha = baseOpacity * 0.40f) }
        val deepGlowColors = rainbowColors.map { it.copy(alpha = baseOpacity * 0.20f) }

        val sweepRainbowBrush = Brush.sweepGradient(colors = rainbowColors, center = center)
        val sweepGlowBrush = Brush.sweepGradient(colors = glowColors, center = center)
        val sweepDeepGlowBrush = Brush.sweepGradient(colors = deepGlowColors, center = center)

        // A. Layer 1: Wide Inward & Outward Ambient Atmospheric Bloom (Smooth Rounded Rect)
        drawScope.drawRoundRect(
            brush = sweepDeepGlowBrush,
            topLeft = Offset(bandWidth * 0.3f, bandWidth * 0.3f),
            size = Size(w - bandWidth * 0.6f, h - bandWidth * 0.6f),
            cornerRadius = CornerRadius(cornerRad * 1.25f, cornerRad * 1.25f),
            style = Stroke(width = bandWidth * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // B. Layer 2: Mid-range Feathered Radiant Aura
        drawScope.drawRoundRect(
            brush = sweepGlowBrush,
            topLeft = Offset(bandWidth * 0.4f, bandWidth * 0.4f),
            size = Size(w - bandWidth * 0.8f, h - bandWidth * 0.8f),
            cornerRadius = CornerRadius(cornerRad * 1.12f, cornerRad * 1.12f),
            style = Stroke(width = bandWidth * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // C. Layer 3: Core Solid Vibrant Rainbow Rounded Border
        drawScope.drawRoundRect(
            brush = sweepRainbowBrush,
            topLeft = Offset(bandWidth * 0.5f, bandWidth * 0.5f),
            size = Size(w - bandWidth, h - bandWidth),
            cornerRadius = CornerRadius(cornerRad, cornerRad),
            style = Stroke(width = bandWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // D. Layer 4: Crisp Inner Holographic Accent Border (Rounded)
        val innerRad = (cornerRad - bandWidth * 0.5f).coerceAtLeast(8f)
        drawScope.drawRoundRect(
            color = Color.White.copy(alpha = 0.92f * baseOpacity),
            topLeft = Offset(bandWidth, bandWidth),
            size = Size(w - 2f * bandWidth, h - 2f * bandWidth),
            cornerRadius = CornerRadius(innerRad, innerRad),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // E. 4 Prismatic Shimmer Starbursts at the Rounded Corners
        val cornerOffset = cornerRad * 0.75f
        renderCornerShimmer(drawScope, cornerOffset, cornerOffset, animTime, baseOpacity)
        renderCornerShimmer(drawScope, w - cornerOffset, cornerOffset, animTime + 1f, baseOpacity)
        renderCornerShimmer(drawScope, w - cornerOffset, h - cornerOffset, animTime + 2f, baseOpacity)
        renderCornerShimmer(drawScope, cornerOffset, h - cornerOffset, animTime + 3f, baseOpacity)
    }

    private fun renderCornerShimmer(
        drawScope: DrawScope,
        cx: Float,
        cy: Float,
        time: Float,
        opacity: Float
    ) {
        val pulse = (sin(time * 6f) * 0.35f + 0.65f)
        val starRadius = 26f * pulse
        val starColor = Color.White.copy(alpha = 0.95f * opacity)

        drawScope.drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.50f * opacity),
            radius = starRadius * 1.8f,
            center = Offset(cx, cy)
        )
        drawScope.drawCircle(
            color = starColor,
            radius = 4.5f * pulse,
            center = Offset(cx, cy)
        )

        // 4-point rotating cross star
        for (a in 0..3) {
            val angle = (time * 1.8f + (a * PI.toFloat() / 2f))
            val ex = cx + cos(angle) * starRadius
            val ey = cy + sin(angle) * starRadius
            drawScope.drawLine(
                color = starColor,
                start = Offset(cx, cy),
                end = Offset(ex, ey),
                strokeWidth = 2.8f,
                cap = StrokeCap.Round
            )
        }
    }
}
