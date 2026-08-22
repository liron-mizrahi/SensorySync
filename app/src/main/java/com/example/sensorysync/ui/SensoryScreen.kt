package com.example.sensorysync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import com.example.sensorysync.model.ControlState
import com.example.sensorysync.visuals.VisualPatternRenderer

@Composable
fun SensoryScreen(
    state: ControlState,
    onStateChange: (ControlState.() -> ControlState) -> Unit,
    onBindCameraPreview: (PreviewView) -> Unit,
    onExitApp: () -> Unit
) {
    val renderer = remember { VisualPatternRenderer() }
    var lastFrameTime by remember { mutableStateOf(System.nanoTime()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Intercept all full-screen touches when child lock is active
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Touches are intentionally ignored to prevent child interference
                    }
                )
            }
    ) {
        // 1. Visual Pattern Canvas Rendering
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentTime = System.nanoTime()
            val deltaTime = ((currentTime - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
            lastFrameTime = currentTime

            renderer.render(this, state, deltaTime)

            // Draw Camera Vision Gaze and Hand Overlays if debug mode is enabled
            if (state.showDebugOverlay) {
                val width = size.width
                val height = size.height

                if (state.gazeData.isFaceDetected) {
                    val gx = state.gazeData.gazePosition.x * width
                    val gy = state.gazeData.gazePosition.y * height

                    val crosshairColor = if (state.isFaceLocked) Color(0xFF4CAF50) else Color.Cyan

                    drawCircle(
                        color = crosshairColor.copy(alpha = 0.8f),
                        radius = 24f,
                        center = Offset(gx, gy),
                        style = Stroke(width = 3f)
                    )
                    drawLine(
                        color = crosshairColor.copy(alpha = 0.8f),
                        start = Offset(gx - 36f, gy),
                        end = Offset(gx + 36f, gy),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = crosshairColor.copy(alpha = 0.8f),
                        start = Offset(gx, gy - 36f),
                        end = Offset(gx, gy + 36f),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // 2. Real-Time Camera Preview Box (Top-Right Corner, permanently mounted to prevent CameraX surface reset)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(if (state.showCameraPreview) 160.dp else 1.dp, if (state.showCameraPreview) 120.dp else 1.dp)
                .alpha(if (state.showCameraPreview) 1.0f else 0.0f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(
                    width = if (state.showCameraPreview) 2.dp else 0.dp,
                    color = if (state.isFaceLocked) Color(0xFF4CAF50) else Color(0xFF00BCD4),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        onBindCameraPreview(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (state.showCameraPreview) {
                // Live status badge at bottom of camera thumbnail
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.isFaceLocked) "TARGET LOCKED" else "ACQUIRING...",
                        color = if (state.isFaceLocked) Color(0xFF4CAF50) else Color(0xFF00BCD4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }



        // 3. Secret Bottom-Left Exit Corner (3-Second Long Press to Exit)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(80.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            onExitApp()
                        }
                    )
                }
        )

        // 4. Remote 5-Point Calibration Screen Overlay
        if (state.calibrationData.isCalibrating) {
            CalibrationScreen(
                state = state,
                onStateChange = onStateChange,
                onFinishCalibration = { newCalibData ->
                    onStateChange {
                        copy(calibrationData = newCalibData)
                    }
                }
            )
        }
    }
}
