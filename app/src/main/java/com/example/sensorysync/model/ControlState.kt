package com.example.sensorysync.model

import androidx.compose.ui.geometry.Offset

enum class VisualPattern(val id: Int, val title: String, val description: String) {
    COSMIC_JELLYFISH(1, "Cosmic Jellyfish", "Bioluminescent swimming jellyfish with gaze-following tracking & focus rewards"),
    BUBBLE_BLOOM(2, "Bubble Bloom", "Iridescent cosmic soap bubbles with gaze-dwelling pop bursts")
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
    // Visual Pattern Settings (Focused exclusively on Cosmic Jellyfish)
    val activePattern: VisualPattern = VisualPattern.COSMIC_JELLYFISH,
    val strobeFrequencyHz: Float = 0.5f,
    val speedMultiplier: Float = 0.6f,
    val particleCount: Int = 200,
    val primaryHue: Float = 195f,
    val saturation: Float = 0.75f,
    val isStrobeActive: Boolean = true,
    val isPhotosafetyEnabled: Boolean = true,
    
    // Jellyfish State & Gaze Alignment
    val jellyfishScale: Float = 0.5f,
    val jellyfishPosition: Offset = Offset(0.5f, 0.5f),
    val isGazeFocusingOnJellyfish: Boolean = false,
    val gazeJellyfishDistance: Float = 1.0f,

    // Bubble Bloom State & Controls
    val bubbleCount: Int = 12,
    val bubbleScale: Float = 1.0f,
    val bubbleDwellTimeSec: Float = 1.2f,
    val bubblePoppedCount: Long = 0L,
    val focusedBubbleIndex: Int? = null,
    val bubbleDwellProgress: Float = 0.0f,
    val bubbleActionTrigger: String? = null,

    // Target Face Locking
    val isFaceLocked: Boolean = false,
    val targetFaceTrackingId: Int? = null,
    val targetFaceStatusText: String = "Searching...",
    val shouldClearFaceProfile: Boolean = false,
    
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
    
    // UI Overlay & Gaze Marker state
    val showCameraPreview: Boolean = false,
    val showDebugOverlay: Boolean = false,
    val showGazeMarker: Boolean = true,
    val gazeMarkerSize: Float = 1.0f,
    val gazeMarkerOpacity: Float = 0.8f,
    val gazeMarkerColor: String = "CYAN",
    val gazeEffectRadiusDp: Float = 80.0f,
    val showGazeEffectRadius: Boolean = true,
    val bubblePopSoundEnabled: Boolean = true,

    // Get Attention Rainbow Boundary Band & Audio Stimulation
    val isAttentionActive: Boolean = false,
    val attentionDurationSec: Float = 4.0f,
    val attentionOpacity: Float = 0.85f,
    val attentionBandWidthDp: Float = 36.0f,
    val attentionSoundEnabled: Boolean = true,
    val attentionSoundVolume: Float = 0.85f,
    val attentionTriggerTimestamp: Long = 0L,
    val attentionRemainingTimeSec: Float = 0.0f,
    
    // MQTT Settings
    val mqttBroker: String = "192.168.1.96",
    val mqttPort: Int = 1883,
    val mqttUsername: String = "harel_tablet",
    val mqttPassword: String = "12345678",
    val mqttTopicPrefix: String = "tablet/control",
    val isMqttConnected: Boolean = false,
    val mqttStatusText: String = "Disconnected"
)
