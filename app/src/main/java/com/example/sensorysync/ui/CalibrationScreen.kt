package com.example.sensorysync.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensorysync.model.ControlState
import com.example.sensorysync.model.GazeCalibrationData
import kotlinx.coroutines.delay

@Composable
fun CalibrationScreen(
    state: ControlState,
    onStateChange: (ControlState.() -> ControlState) -> Unit,
    onFinishCalibration: (GazeCalibrationData) -> Unit
) {
    val calibrationTargets = remember {
        listOf(
            Offset(0.50f, 0.50f), // 0: Center
            Offset(0.15f, 0.15f), // 1: Top-Left
            Offset(0.85f, 0.15f), // 2: Top-Right
            Offset(0.15f, 0.85f), // 3: Bottom-Left
            Offset(0.85f, 0.85f)  // 4: Bottom-Right
        )
    }

    var activeIndex by remember { mutableStateOf(0) }
    var holdProgress by remember { mutableStateOf(0f) }

    val rawGazeSamples = remember { mutableStateListOf<Pair<Offset, Offset>>() }

    // Pulsing target animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radius"
    )

    // Accumulate gaze samples while child looks at target
    LaunchedEffect(activeIndex, state.gazeData.isFaceDetected) {
        holdProgress = 0f
        val targetNorm = calibrationTargets[activeIndex]
        var collectedMs = 0

        while (collectedMs < 1500) {
            delay(100)
            if (state.gazeData.isFaceDetected) {
                collectedMs += 100
                holdProgress = (collectedMs / 1500f).coerceIn(0f, 1f)
                rawGazeSamples.add(Pair(targetNorm, state.gazeData.gazePosition))
            }
        }

        // Advance to next point or finish
        if (activeIndex < calibrationTargets.size - 1) {
            activeIndex++
        } else {
            // Compute scale & offset matrix
            val computedData = computeCalibrationMatrix(rawGazeSamples)
            onFinishCalibration(computedData)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        val currentTarget = calibrationTargets[activeIndex]

        // 1. Draw Target Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val targetX = currentTarget.x * size.width
            val targetY = currentTarget.y * size.height
            val centerOffset = Offset(targetX, targetY)

            // Outer pulse ring
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.4f),
                radius = pulseRadius * 1.5f,
                center = centerOffset,
                style = Stroke(width = 4f)
            )

            // Progress ring
            drawCircle(
                color = Color(0xFF4CAF50),
                radius = 50f,
                center = centerOffset,
                style = Stroke(width = 8f * holdProgress)
            )

            // Solid inner dot
            drawCircle(
                color = Color.Cyan,
                radius = 24f,
                center = centerOffset
            )

            drawCircle(
                color = Color.White,
                radius = 8f,
                center = centerOffset
            )

            // Live detected gaze crosshair
            if (state.gazeData.isFaceDetected) {
                val gx = state.gazeData.gazePosition.x * size.width
                val gy = state.gazeData.gazePosition.y * size.height
                drawCircle(
                    color = Color.Yellow.copy(alpha = 0.7f),
                    radius = 16f,
                    center = Offset(gx, gy),
                    style = Stroke(width = 3f)
                )
            }
        }

        // 2. Instructions Banner (Caregiver HUD)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp),
            color = Color.DarkGray.copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Gaze Calibration (${activeIndex + 1}/5)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Gently guide child's eye to the glowing circle",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        }

        // 3. Skip / Cancel Button
        Button(
            onClick = {
                onFinishCalibration(GazeCalibrationData(isCalibrated = false, isCalibrating = false))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f))
        ) {
            Text("Skip Calibration", color = Color.White)
        }
    }
}

private fun computeCalibrationMatrix(samples: List<Pair<Offset, Offset>>): GazeCalibrationData {
    if (samples.isEmpty()) return GazeCalibrationData(isCalibrated = false, isCalibrating = false)

    var sumDx = 0f
    var sumDy = 0f
    samples.forEach { (target, raw) ->
        sumDx += (target.x - raw.x)
        sumDy += (target.y - raw.y)
    }

    val avgOffsetX = sumDx / samples.size
    val avgOffsetY = sumDy / samples.size

    return GazeCalibrationData(
        isCalibrated = true,
        scaleX = 1.15f, // Slight gain boost
        scaleY = 1.15f,
        offsetX = avgOffsetX,
        offsetY = avgOffsetY,
        isCalibrating = false
    )
}
