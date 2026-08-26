package com.example.sensorysync.visuals

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.sensorysync.model.ControlState
import kotlin.math.*

class GazeMarkerRenderer {
    private var animRotation = 0f

    fun render(
        drawScope: DrawScope,
        state: ControlState,
        gazePx: Float,
        gazePy: Float,
        isFocused: Boolean,
        dwellProgress: Float = 0f,
        deltaTime: Float = 0.016f
    ) {
        if (!state.showGazeMarker) return

        animRotation = (animRotation + deltaTime * 60f) % 360f

        val density = drawScope.density
        val markerScale = state.gazeMarkerSize.coerceIn(0.5f, 3.0f)
        val markerOpacity = state.gazeMarkerOpacity.coerceIn(0.1f, 1.0f)
        val effectRadiusPx = (state.gazeEffectRadiusDp * density).coerceIn(24f, 400f)

        val baseColor = when (state.gazeMarkerColor.uppercase()) {
            "GREEN" -> Color(0xFF00E676)
            "MAGENTA", "PINK" -> Color(0xFFE040FB)
            "GOLD", "YELLOW" -> Color(0xFFFFD54F)
            "WHITE" -> Color.White
            else -> Color(0xFF00E5FF) // CYAN
        }

        val center = Offset(gazePx, gazePy)

        // -------------------------------------------------------------
        // A. Controllable Gaze Effect Catchment Area (Visible Reach Radius)
        // -------------------------------------------------------------
        if (state.showGazeEffectRadius) {
            val catchmentAlpha = (if (isFocused) 0.30f else 0.18f) * markerOpacity
            val catchmentColor = (if (isFocused) Color(0xFF00E676) else baseColor).copy(alpha = catchmentAlpha)

            // Subtle soft filled catchment disc
            drawScope.drawCircle(
                color = catchmentColor.copy(alpha = catchmentAlpha * 0.35f),
                radius = effectRadiusPx,
                center = center
            )

            // Dashed perimeter catchment boundary line
            drawScope.drawCircle(
                color = catchmentColor,
                radius = effectRadiusPx,
                center = center,
                style = Stroke(
                    width = 1.6f * density,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * density, 6f * density), animRotation * 0.5f)
                )
            )
        }

        // -------------------------------------------------------------
        // B. Gaze Marker Body: LOOKING AROUND Mode vs IN FOCUS Mode
        // -------------------------------------------------------------
        if (!isFocused) {
            // =========================================================
            // 1. LOOKING AROUND MODE: Rotating Diamond / Compass Star
            // =========================================================
            val scanColor = if (state.isFaceLocked) Color(0xFF00E5FF) else baseColor
            val diamondRadius = 14f * markerScale * density

            // 1a. Soft Ambient Scanning Glow
            drawScope.drawCircle(
                color = scanColor.copy(alpha = 0.20f * markerOpacity),
                radius = diamondRadius * 1.5f,
                center = center
            )

            // 1b. Rotating 4-Point Diamond Compass Contour
            drawScope.rotate(degrees = animRotation, pivot = center) {
                val path = Path().apply {
                    moveTo(gazePx, gazePy - diamondRadius)           // Top
                    lineTo(gazePx + diamondRadius, gazePy)           // Right
                    lineTo(gazePx, gazePy + diamondRadius)           // Bottom
                    lineTo(gazePx - diamondRadius, gazePy)           // Left
                    close()
                }
                drawPath(
                    path = path,
                    color = scanColor.copy(alpha = 0.75f * markerOpacity),
                    style = Stroke(width = 1.8f * markerScale * density, cap = StrokeCap.Round)
                )

                // 4 Axial Tick Marks protruding from diamond tips
                val tickLen = 6f * markerScale * density
                val tickColor = scanColor.copy(alpha = 0.85f * markerOpacity)
                val tickW = 1.6f * markerScale * density

                drawLine(tickColor, Offset(gazePx, gazePy - diamondRadius), Offset(gazePx, gazePy - diamondRadius - tickLen), tickW)
                drawLine(tickColor, Offset(gazePx, gazePy + diamondRadius), Offset(gazePx, gazePy + diamondRadius + tickLen), tickW)
                drawLine(tickColor, Offset(gazePx - diamondRadius, gazePy), Offset(gazePx - diamondRadius - tickLen, gazePy), tickW)
                drawLine(tickColor, Offset(gazePx + diamondRadius, gazePy), Offset(gazePx + diamondRadius + tickLen, gazePy), tickW)
            }

            // 1c. Center Scanning Reticle Dot
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.90f * markerOpacity),
                radius = 2.8f * markerScale * density,
                center = center
            )
            drawScope.drawCircle(
                color = scanColor.copy(alpha = 0.60f * markerOpacity),
                radius = 5.5f * markerScale * density,
                center = center,
                style = Stroke(width = 1.2f * markerScale * density)
            )

        } else {
            // =========================================================
            // 2. IN FOCUS MODE: Solid Dual Lock-on Reticle & Progress Arc
            // =========================================================
            val focusColor = Color(0xFF00E676) // Radiant Emerald Green for Focus
            val ringRadius = 18f * markerScale * density

            // 2a. Intense Fixation Core Glow
            drawScope.drawCircle(
                color = focusColor.copy(alpha = 0.35f * markerOpacity),
                radius = ringRadius * 1.4f,
                center = center
            )

            // 2b. Solid Primary Lock-on Ring
            drawScope.drawCircle(
                color = focusColor.copy(alpha = 0.95f * markerOpacity),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 2.4f * markerScale * density)
            )

            // 2c. Inner Concentric Ring
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.80f * markerOpacity),
                radius = ringRadius * 0.55f,
                center = center,
                style = Stroke(width = 1.4f * markerScale * density)
            )

            // 2d. Bright Solid Center Focal Pip
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.98f * markerOpacity),
                radius = 4.2f * markerScale * density,
                center = center
            )

            // 2e. 4 Axial Lock Brackets
            val bracketLen = 8f * markerScale * density
            val strokeW = 2.2f * markerScale * density
            val bracketColor = focusColor.copy(alpha = 0.95f * markerOpacity)

            drawScope.drawLine(bracketColor, Offset(gazePx - ringRadius - bracketLen, gazePy), Offset(gazePx - ringRadius + 2f, gazePy), strokeW)
            drawScope.drawLine(bracketColor, Offset(gazePx + ringRadius - 2f, gazePy), Offset(gazePx + ringRadius + bracketLen, gazePy), strokeW)
            drawScope.drawLine(bracketColor, Offset(gazePx, gazePy - ringRadius - bracketLen), Offset(gazePx, gazePy - ringRadius + 2f), strokeW)
            drawScope.drawLine(bracketColor, Offset(gazePx, gazePy + ringRadius - 2f), Offset(gazePx, gazePy + ringRadius + bracketLen), strokeW)

            // 2f. Radial Dwell Progress Ring (when dwelling on bubble or jellyfish)
            if (dwellProgress > 0.02f) {
                val progressRadius = ringRadius + 6f * markerScale * density
                drawScope.drawArc(
                    color = Color(0xFFFFD54F).copy(alpha = 0.95f * markerOpacity),
                    startAngle = -90f,
                    sweepAngle = (dwellProgress * 360f).coerceIn(0f, 360f),
                    useCenter = false,
                    topLeft = Offset(gazePx - progressRadius, gazePy - progressRadius),
                    size = Size(progressRadius * 2f, progressRadius * 2f),
                    style = Stroke(width = 3.2f * markerScale * density, cap = StrokeCap.Round)
                )
            }
        }
    }
}
