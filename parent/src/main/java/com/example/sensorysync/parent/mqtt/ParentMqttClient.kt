package com.example.sensorysync.parent.mqtt

import android.content.Context
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class ParentControlState(
    val brokerHost: String = "192.168.1.96",
    val brokerPort: Int = 1883,
    val username: String = "harel_tablet",
    val password: String = "12345678",
    val topicPrefix: String = "tablet/control",
    val isMqttConnected: Boolean = false,
    val statusMessage: String = "Disconnected",
    
    // Remote Child Tablet Telemetry
    val isTabletOnline: Boolean = false,
    val activePatternId: Int = 1,
    val activePatternTitle: String = "Gentle Floating Stars",
    val strobeFrequencyHz: Float = 0.5f,
    val primaryHue: Float = 200f,
    val speedMultiplier: Float = 0.6f,
    val isFaceLocked: Boolean = false,
    val targetFaceTrackingId: Int = -1,
    val targetFaceStatusText: String = "No Face Locked",
    val isGazeDetected: Boolean = false,
    val activeEyeContactSeconds: Long = 0L,
    val sessionDurationSeconds: Long = 0L,
    val longestFocusStreakSeconds: Long = 0L,
    val engagementScorePercent: Int = 0,

    // Real-Time Camera Preview Stream from Tablet
    val cameraSnapshotBase64: String? = null,

    // Acquired / Saved Target Child Face Snapshot
    val lockedFaceSnapshotBase64: String? = null,

    // Tablet Camera Preview Visibility Toggle (Default: false / hidden)
    val showChildCameraPreview: Boolean = false,

    // Jellyfish Engagement & Tracking & Size
    val jellyfishScale: Float = 0.5f,
    val isGazeFocusingOnJellyfish: Boolean = false,
    val gazeJellyfishDistance: Float = 1.0f,

    // Bubble Bloom Controls & Live Metrics
    val bubbleCount: Int = 12,
    val bubbleScale: Float = 1.0f,
    val bubbleDwellTimeSec: Float = 1.2f,
    val bubblePoppedCount: Long = 0L,
    val focusedBubbleIndex: Int = -1,
    val bubbleDwellProgress: Float = 0.0f,

    // Eye Gaze Marker Display Controls
    val showGazeMarker: Boolean = true,
    val gazeMarkerSize: Float = 1.0f,
    val gazeMarkerOpacity: Float = 0.8f,
    val gazeMarkerColor: String = "CYAN",

    // Get Attention Rainbow Boundary Band & Audio Stimulation
    val isAttentionActive: Boolean = false,
    val attentionDurationSec: Float = 4.0f,
    val attentionOpacity: Float = 0.85f,
    val attentionBandWidthDp: Float = 36.0f,
    val attentionSoundEnabled: Boolean = true,
    val attentionSoundVolume: Float = 0.85f,
    val attentionRemainingTimeSec: Float = 0.0f
)



class ParentMqttClient(
    private val context: Context,
    private val onStateUpdate: (ParentControlState.() -> ParentControlState) -> Unit
) {
    private var mqttClient: MqttClient? = null
    private val watchdog: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val prefs = context.getSharedPreferences("parent_app_cache", Context.MODE_PRIVATE)

    @Volatile
    private var lastTelemetryTimestampMs: Long = 0L

    init {
        // Load saved face snapshot from cache on startup
        val cachedFace = prefs.getString("cached_locked_face_base64", null)
        if (cachedFace != null) {
            onStateUpdate { copy(lockedFaceSnapshotBase64 = cachedFace) }
        }

        // Watchdog task every 2 seconds to mark tablet offline if telemetry stops
        watchdog.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            if (lastTelemetryTimestampMs > 0 && (now - lastTelemetryTimestampMs > 4000)) {
                onStateUpdate { copy(isTabletOnline = false) }
            }
        }, 2, 2, TimeUnit.SECONDS)
    }

    fun connect(state: ParentControlState) {
        disconnect()

        val serverUri = "tcp://${state.brokerHost}:${state.brokerPort}"
        val clientId = "SensorySyncParent_" + UUID.randomUUID().toString().take(6)

        try {
            onStateUpdate { copy(statusMessage = "Connecting to $serverUri...", isMqttConnected = false, isTabletOnline = false) }
            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())

            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 8
                keepAliveInterval = 15
                if (state.username.isNotBlank()) {
                    userName = state.username
                    password = state.password.toCharArray()
                }
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    onStateUpdate { copy(isMqttConnected = false, isTabletOnline = false, statusMessage = "Connection lost") }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        handleIncomingMessage(topic, state.topicPrefix, message.toString())
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            Thread {
                try {
                    mqttClient?.connect(connOpts)
                    val subscribeTopic = "${state.topicPrefix}/#"
                    mqttClient?.subscribe(subscribeTopic)
                    onStateUpdate { copy(isMqttConnected = true, statusMessage = "Connected to ${state.brokerHost}") }
                } catch (e: Exception) {
                    onStateUpdate { copy(isMqttConnected = false, isTabletOnline = false, statusMessage = "Error: ${e.localizedMessage}") }
                }
            }.start()

        } catch (e: Exception) {
            onStateUpdate { copy(isMqttConnected = false, isTabletOnline = false, statusMessage = "Failed: ${e.localizedMessage}") }
        }
    }

    private fun handleIncomingMessage(topic: String, prefix: String, payload: String) {
        if (topic.endsWith("/camera_snapshot")) {
            onStateUpdate { copy(cameraSnapshotBase64 = payload) }
            return
        }

        if (topic.endsWith("/locked_face_snapshot")) {
            prefs.edit().putString("cached_locked_face_base64", payload).apply()
            onStateUpdate { copy(lockedFaceSnapshotBase64 = payload) }
            return
        }

        if (!topic.startsWith("$prefix/status")) return
        try {
            lastTelemetryTimestampMs = System.currentTimeMillis()
            val json = JSONObject(payload)
            onStateUpdate {
                copy(
                    isTabletOnline = true,
                    activePatternId = json.optInt("activePatternId", 1),
                    activePatternTitle = json.optString("activePattern", "Gentle Floating Stars"),
                    strobeFrequencyHz = json.optDouble("strobeFrequencyHz", 0.5).toFloat(),
                    primaryHue = json.optDouble("primaryHue", 200.0).toFloat(),
                    speedMultiplier = json.optDouble("speedMultiplier", 0.6).toFloat(),
                    isFaceLocked = json.optBoolean("isFaceLocked", false),
                    targetFaceTrackingId = json.optInt("targetFaceTrackingId", -1),
                    targetFaceStatusText = json.optString("targetFaceStatusText", "No Face Locked"),
                    isGazeDetected = json.optBoolean("gazeDetected", false),
                    activeEyeContactSeconds = json.optLong("activeEyeContactSeconds", 0L),
                    sessionDurationSeconds = json.optLong("sessionDurationSeconds", 0L),
                    longestFocusStreakSeconds = json.optLong("longestFocusStreakSeconds", 0L),
                    engagementScorePercent = json.optInt("engagementScorePercent", 0),
                    showChildCameraPreview = json.optBoolean("showCameraPreview", false),
                    showGazeMarker = json.optBoolean("showGazeMarker", true),
                    gazeMarkerSize = json.optDouble("gazeMarkerSize", 1.0).toFloat(),
                    gazeMarkerOpacity = json.optDouble("gazeMarkerOpacity", 0.8).toFloat(),
                    gazeMarkerColor = json.optString("gazeMarkerColor", "CYAN"),
                    isAttentionActive = json.optBoolean("isAttentionActive", false),
                    attentionDurationSec = json.optDouble("attentionDurationSec", 4.0).toFloat(),
                    attentionOpacity = json.optDouble("attentionOpacity", 0.85).toFloat(),
                    attentionBandWidthDp = json.optDouble("attentionBandWidthDp", 36.0).toFloat(),
                    attentionSoundEnabled = json.optBoolean("attentionSoundEnabled", true),
                    attentionSoundVolume = json.optDouble("attentionSoundVolume", 0.85).toFloat(),
                    attentionRemainingTimeSec = json.optDouble("attentionRemainingTimeSec", 0.0).toFloat(),
                    jellyfishScale = json.optDouble("jellyfishScale", 0.5).toFloat(),
                    bubbleCount = json.optInt("bubbleCount", 12),
                    bubbleScale = json.optDouble("bubbleScale", 1.0).toFloat(),
                    bubbleDwellTimeSec = json.optDouble("bubbleDwellTimeSec", 1.2).toFloat(),
                    bubblePoppedCount = json.optLong("bubblePoppedCount", 0L),
                    focusedBubbleIndex = json.optInt("focusedBubbleIndex", -1),
                    bubbleDwellProgress = json.optDouble("bubbleDwellProgress", 0.0).toFloat(),
                    isGazeFocusingOnJellyfish = json.optBoolean("isGazeFocusingOnJellyfish", false),
                    gazeJellyfishDistance = json.optDouble("gazeJellyfishDistance", 1.0).toFloat()
                )
            }


        } catch (_: Exception) {}
    }

    fun sendCommand(topicPrefix: String, subTopic: String, payload: String) {
        // Optimistic local state update for instantaneous slider/switch responsiveness
        try {
            when (subTopic) {
                "command" -> {
                    when (payload.uppercase()) {
                        "GET_ATTENTION", "START_ATTENTION", "TRIGGER_ATTENTION" -> {
                            onStateUpdate { copy(isAttentionActive = true, attentionRemainingTimeSec = attentionDurationSec) }
                        }
                        "STOP_ATTENTION", "CANCEL_ATTENTION" -> {
                            onStateUpdate { copy(isAttentionActive = false, attentionRemainingTimeSec = 0f) }
                        }
                    }
                }
                "attention_duration", "attn_duration" -> {
                    payload.toFloatOrNull()?.let { dur ->
                        onStateUpdate { copy(attentionDurationSec = dur) }
                    }
                }
                "attention_opacity", "attn_opacity" -> {
                    payload.toFloatOrNull()?.let { op ->
                        onStateUpdate { copy(attentionOpacity = op) }
                    }
                }
                "attention_band_width", "attn_width", "band_width" -> {
                    payload.toFloatOrNull()?.let { bw ->
                        onStateUpdate { copy(attentionBandWidthDp = bw) }
                    }
                }
                "attention_sound", "attention_sound_enabled", "attn_sound" -> {
                    val enabled = payload.toBooleanStrictOrNull() ?: (payload == "1" || payload.equals("true", ignoreCase = true))
                    onStateUpdate { copy(attentionSoundEnabled = enabled) }
                }
                "attention_sound_volume", "attention_volume", "attn_volume" -> {
                    payload.toFloatOrNull()?.let { vol ->
                        onStateUpdate { copy(attentionSoundVolume = vol) }
                    }
                }
                "gaze_marker", "show_gaze_marker" -> {
                    val show = payload.toBooleanStrictOrNull() ?: (payload == "1" || payload.equals("true", ignoreCase = true))
                    onStateUpdate { copy(showGazeMarker = show) }
                }
                "gaze_marker_size", "marker_size" -> {
                    payload.toFloatOrNull()?.let { size ->
                        onStateUpdate { copy(gazeMarkerSize = size) }
                    }
                }
                "gaze_marker_opacity", "marker_opacity" -> {
                    payload.toFloatOrNull()?.let { opacity ->
                        onStateUpdate { copy(gazeMarkerOpacity = opacity) }
                    }
                }
                "gaze_marker_color", "marker_color" -> {
                    onStateUpdate { copy(gazeMarkerColor = payload.uppercase()) }
                }
                "pattern" -> {
                    payload.toIntOrNull()?.let { pId ->
                        onStateUpdate { copy(activePatternId = pId) }
                    }
                }
                "bubble_count" -> {
                    payload.toIntOrNull()?.let { count ->
                        onStateUpdate { copy(bubbleCount = count) }
                    }
                }
                "bubble_scale" -> {
                    payload.toFloatOrNull()?.let { scale ->
                        onStateUpdate { copy(bubbleScale = scale) }
                    }
                }
                "speed" -> {
                    payload.toFloatOrNull()?.let { spd ->
                        onStateUpdate { copy(speedMultiplier = spd) }
                    }
                }
                "bubble_dwell_time" -> {
                    payload.toFloatOrNull()?.let { dwell ->
                        onStateUpdate { copy(bubbleDwellTimeSec = dwell) }
                    }
                }
                "jellyfish_scale", "size" -> {
                    payload.toFloatOrNull()?.let { scale ->
                        onStateUpdate { copy(jellyfishScale = scale) }
                    }
                }
                "strobe_freq" -> {
                    payload.toFloatOrNull()?.let { freq ->
                        onStateUpdate { copy(strobeFrequencyHz = freq) }
                    }
                }
                "color_hue" -> {
                    payload.toFloatOrNull()?.let { hue ->
                        onStateUpdate { copy(primaryHue = hue) }
                    }
                }
            }
        } catch (_: Exception) {}

        if (mqttClient?.isConnected != true) return
        try {
            val fullTopic = "$topicPrefix/$subTopic"
            val message = MqttMessage(payload.toByteArray()).apply { qos = 0 }
            mqttClient?.publish(fullTopic, message)
        } catch (_: Exception) {}
    }

    fun disconnect() {
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            mqttClient?.close()
        } catch (_: Exception) {}
        mqttClient = null
    }

    fun shutdown() {
        disconnect()
        try {
            watchdog.shutdownNow()
        } catch (_: Exception) {}
    }
}
