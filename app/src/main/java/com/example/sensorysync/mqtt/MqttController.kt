package com.example.sensorysync.mqtt

import com.example.sensorysync.model.ControlState
import com.example.sensorysync.model.VisualPattern
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

class MqttController(
    private val onStateUpdate: (ControlState.() -> ControlState) -> Unit,
    private val onStatusChange: (Boolean, String) -> Unit
) {
    private var mqttClient: MqttClient? = null

    fun connect(
        broker: String,
        port: Int,
        topicPrefix: String,
        username: String? = null,
        password: String? = null
    ) {
        disconnect()

        val serverUri = "tcp://$broker:$port"
        val clientId = "SensorySyncTablet_" + UUID.randomUUID().toString().take(6)

        try {
            onStatusChange(false, "Connecting to $serverUri...")
            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())

            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                if (!username.isNullOrBlank()) {
                    userName = username
                    this.password = (password ?: "").toCharArray()
                }
            }


            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    onStatusChange(false, "Connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        handleIncomingMessage(topic, topicPrefix, message.toString())
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            Thread {
                try {
                    mqttClient?.connect(connOpts)
                    val subscribeTopic = "$topicPrefix/#"
                    mqttClient?.subscribe(subscribeTopic)
                    onStatusChange(true, "Connected to $broker")
                } catch (e: Exception) {
                    onStatusChange(false, "Error: ${e.localizedMessage}")
                }
            }.start()

        } catch (e: Exception) {
            onStatusChange(false, "Failed to initialize MQTT: ${e.localizedMessage}")
        }
    }

    private fun handleIncomingMessage(topic: String, prefix: String, payload: String) {
        val subTopic = topic.removePrefix("$prefix/").trim()
        val value = payload.trim()

        onStateUpdate {
            when (subTopic) {
                "pattern" -> {
                    val pId = value.toIntOrNull() ?: 1
                    val newPattern = VisualPattern.entries.firstOrNull { it.id == pId } ?: VisualPattern.COSMIC_JELLYFISH
                    copy(activePattern = newPattern)
                }
                "strobe_freq" -> {
                    val freq = value.toFloatOrNull()?.coerceIn(1f, 40f) ?: strobeFrequencyHz
                    copy(strobeFrequencyHz = freq)
                }
                "color_hue" -> {
                    val hue = value.toFloatOrNull()?.coerceIn(0f, 360f) ?: primaryHue
                    copy(primaryHue = hue)
                }
                "speed" -> {
                    val spd = value.toFloatOrNull()?.coerceIn(0.1f, 3.0f) ?: speedMultiplier
                    copy(speedMultiplier = spd)
                }
                "jellyfish_scale", "size", "jellyfish_size" -> {
                    val scale = value.toFloatOrNull()?.coerceIn(0.2f, 2.0f) ?: jellyfishScale
                    copy(jellyfishScale = scale)
                }
                "bubble_count" -> {
                    val count = value.toIntOrNull()?.coerceIn(3, 25) ?: bubbleCount
                    copy(bubbleCount = count)
                }
                "bubble_scale", "bubble_size" -> {
                    val scale = value.toFloatOrNull()?.coerceIn(0.5f, 2.0f) ?: bubbleScale
                    copy(bubbleScale = scale)
                }
                "bubble_dwell_time", "dwell_time" -> {
                    val dwell = value.toFloatOrNull()?.coerceIn(0.5f, 3.0f) ?: bubbleDwellTimeSec
                    copy(bubbleDwellTimeSec = dwell)
                }

                "camera_preview" -> {
                    val show = value.toBooleanStrictOrNull() ?: (value == "1" || value.equals("true", ignoreCase = true))
                    copy(showCameraPreview = show)
                }
                "command" -> {
                    when (value.uppercase()) {
                        "ACQUIRE_FACE", "LOCK_FACE" -> {
                            val currentId = gazeData.faceTrackingId
                            if (currentId != null) {
                                copy(
                                    isFaceLocked = true,
                                    targetFaceTrackingId = currentId,
                                    targetFaceStatusText = "Locked (ID: #$currentId)"
                                )
                            } else {
                                copy(targetFaceStatusText = "No Face Visible to Lock")
                            }
                        }
                        "RELEASE_FACE", "UNLOCK_FACE" -> {
                            copy(
                                isFaceLocked = false,
                                targetFaceTrackingId = null,
                                targetFaceStatusText = "Unlocked"
                            )
                        }
                        "CAMERA_PREVIEW_ON" -> copy(showCameraPreview = true)
                        "CAMERA_PREVIEW_OFF" -> copy(showCameraPreview = false)
                        "CAMERA_PREVIEW_TOGGLE" -> copy(showCameraPreview = !showCameraPreview)
                        "CALIBRATE" -> {
                            copy(calibrationData = calibrationData.copy(isCalibrating = true))
                        }
                        "EXIT_APP", "CLOSE_APP" -> {
                            copy(shouldExitApp = true)
                        }
                        "RESET" -> copy(activePattern = VisualPattern.COSMIC_JELLYFISH, strobeFrequencyHz = 0.5f)
                        "STROBE_TOGGLE" -> copy(isStrobeActive = !isStrobeActive)
                        else -> this
                    }
                }

                else -> this
            }
        }
    }



    fun publishStatus(state: ControlState) {
        if (mqttClient?.isConnected != true) return

        try {
            val json = JSONObject().apply {
                put("activePattern", state.activePattern.title)
                put("activePatternId", state.activePattern.id)
                put("strobeFrequencyHz", state.strobeFrequencyHz)
                put("primaryHue", state.primaryHue)
                put("speedMultiplier", state.speedMultiplier)
                put("isFaceLocked", state.isFaceLocked)
                put("targetFaceTrackingId", state.targetFaceTrackingId ?: -1)
                put("targetFaceStatusText", state.targetFaceStatusText)
                put("gazeDetected", state.gazeData.isFaceDetected)
                put("gazeX", state.gazeData.gazePosition.x)
                put("gazeY", state.gazeData.gazePosition.y)
                put("activeEyeContactSeconds", state.engagementMetrics.activeEyeContactSeconds)
                put("sessionDurationSeconds", state.engagementMetrics.sessionDurationSeconds)
                put("longestFocusStreakSeconds", state.engagementMetrics.longestFocusStreakSeconds)
                put("engagementScorePercent", state.engagementMetrics.engagementScorePercent)
                put("showCameraPreview", state.showCameraPreview)
                put("jellyfishScale", state.jellyfishScale)
                put("bubbleCount", state.bubbleCount)
                put("bubbleScale", state.bubbleScale)
                put("bubbleDwellTimeSec", state.bubbleDwellTimeSec)
                put("bubblePoppedCount", state.bubblePoppedCount)
                put("focusedBubbleIndex", state.focusedBubbleIndex ?: -1)
                put("bubbleDwellProgress", state.bubbleDwellProgress)
                put("isGazeFocusingOnJellyfish", state.isGazeFocusingOnJellyfish)
                put("gazeJellyfishDistance", state.gazeJellyfishDistance)
                put("jellyfishX", state.jellyfishPosition.x)
                put("jellyfishY", state.jellyfishPosition.y)
                put("timestamp", System.currentTimeMillis())
            }



            val statusTopic = "${state.mqttTopicPrefix}/status"
            val message = MqttMessage(json.toString().toByteArray()).apply { qos = 0 }
            mqttClient?.publish(statusTopic, message)
        } catch (_: Exception) {}
    }

    fun publishCameraSnapshot(prefix: String, snapshotBase64: String) {
        if (mqttClient?.isConnected != true) return
        try {
            val snapshotTopic = "$prefix/camera_snapshot"
            val message = MqttMessage(snapshotBase64.toByteArray()).apply { qos = 0 }
            mqttClient?.publish(snapshotTopic, message)
        } catch (_: Exception) {}
    }

    fun publishLockedFaceSnapshot(prefix: String, snapshotBase64: String) {
        if (mqttClient?.isConnected != true) return
        try {
            val lockedTopic = "$prefix/locked_face_snapshot"
            val message = MqttMessage(snapshotBase64.toByteArray()).apply { qos = 0 }
            mqttClient?.publish(lockedTopic, message)
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
}
