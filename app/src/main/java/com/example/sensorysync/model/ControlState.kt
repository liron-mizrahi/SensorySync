package com.example.sensorysync.model

import androidx.compose.ui.geometry.Offset

enum class VisualPattern(val id: Int, val title: String, val description: String) {
    HARMONIC_PARTICLES(1, "Gentle Floating Stars", "Soft glowing particles responding to gaze & hands"),
    SACRED_MANDALA(2, "Breathing Mandala", "Slow soothing multi-layered mandala geometry"),
    ISOCHRONIC_STROBE(3, "Calming Pulse Wave", "Soft breathing light entrainment (0.2 - 3.0 Hz max)"),
    CHLADNI_RIPPLES(4, "Liquid Water Ripples", "Gentle wave interference matrix"),
    WARP_TUNNEL(5, "Soft Horizon Drift", "Peaceful starfield warp guided by eye gaze"),
    SWIRLING_SMOKE(6, "Swirling Liquid & Smoke", "Viscous colored fluid ink and smooth smoke tendrils")
}

enum class HandGesture {
    NONE,
    OPEN_PALM,
    PINCH,
    FIST,
    TWO_HAND_STRETCH
}

data class HandData(
    val isPresent: Boolean = false,
    val position: Offset = Offset(0.5f, 0.5f),
    val pinchDistance: Float = 1.0f,
    val gesture: HandGesture = HandGesture.NONE,
    val fingerCount: Int = 0
)

data class EyeGazeData(
    val isFaceDetected: Boolean = false,
    val gazePosition: Offset = Offset(0.5f, 0.5f),
    val calibratedGazePosition: Offset = Offset(0.5f, 0.5f),
    val leftEyeOpenProb: Float = 1.0f,
    val rightEyeOpenProb: Float = 1.0f,
    val isBlinking: Boolean = false,
    val headRotationY: Float = 0f,
    val headRotationZ: Float = 0f,
    val faceTrackingId: Int? = null
)

data class TouchPoint(
    val id: Int,
    val position: Offset
)

data class GazeCalibrationData(
    val isCalibrated: Boolean = false,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val offsetX: Float = 0.0f,
    val offsetY: Float = 0.0f,
    val isCalibrating: Boolean = false,
    val activeCalibrationIndex: Int = 0
)

data class EngagementMetrics(
    val sessionDurationSeconds: Long = 0L,
    val activeEyeContactSeconds: Long = 0L,
    val longestFocusStreakSeconds: Long = 0L,
    val currentFocusStreakSeconds: Long = 0L,
    val handInteractionCount: Long = 0L,
    val engagementScorePercent: Int = 0,
    val sessionHistory: List<SessionRecord> = emptyList()
)

data class SessionRecord(
    val timestampMs: Long,
    val durationSeconds: Long,
    val activeEyeContactSeconds: Long,
    val longestStreakSeconds: Long,
    val engagementScorePercent: Int
)

data class ControlState(
    // Visual Pattern Settings (SAFETY CAPPED: 0.2 Hz to 3.0 Hz MAX)
    val activePattern: VisualPattern = VisualPattern.HARMONIC_PARTICLES,
    val strobeFrequencyHz: Float = 0.5f,
    val speedMultiplier: Float = 0.6f,
    val particleCount: Int = 200,
    val primaryHue: Float = 200f,
    val saturation: Float = 0.7f,
    val isStrobeActive: Boolean = true,
    val isPhotosafetyEnabled: Boolean = true,
    
    // Target Face Locking
    val isFaceLocked: Boolean = false,
    val targetFaceTrackingId: Int? = null,
    val targetFaceStatusText: String = "Searching...",
    
    // Child Touch Lockdown & Safety Exit
    val isTouchLocked: Boolean = true,
    val shouldExitApp: Boolean = false,
    
    // Gestures & Camera tracking data
    val leftHand: HandData = HandData(),
    val rightHand: HandData = HandData(),
    val gazeData: EyeGazeData = EyeGazeData(),
    val calibrationData: GazeCalibrationData = GazeCalibrationData(),
    val engagementMetrics: EngagementMetrics = EngagementMetrics(),
    val activeTouchPoints: List<TouchPoint> = emptyList(),
    
    // UI Overlay state
    val showCameraPreview: Boolean = false,
    val showDebugOverlay: Boolean = false,
    
    // MQTT Settings
    val mqttBroker: String = "192.168.1.96",
    val mqttPort: Int = 1883,
    val mqttUsername: String = "harel_tablet",
    val mqttPassword: String = "12345678",
    val mqttTopicPrefix: String = "tablet/control",
    val isMqttConnected: Boolean = false,
    val mqttStatusText: String = "Disconnected"
)
