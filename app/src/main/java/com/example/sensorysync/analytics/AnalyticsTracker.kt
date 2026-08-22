package com.example.sensorysync.analytics

import android.content.Context
import com.example.sensorysync.model.ControlState
import com.example.sensorysync.model.EngagementMetrics
import com.example.sensorysync.model.SessionRecord
import com.example.sensorysync.mqtt.MqttController
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AnalyticsTracker(
    private val context: Context,
    private val onMetricsUpdate: (EngagementMetrics) -> Unit
) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var sessionStartTimeMs = System.currentTimeMillis()
    private var sessionDurationSec = 0L
    private var activeEyeContactSec = 0L
    private var currentStreakSec = 0L
    private var longestStreakSec = 0L
    private var handInteractions = 0L

    @Volatile
    private var latestState: ControlState = ControlState()

    private val historyFile = File(context.filesDir, "session_history.json")
    private val sessionHistoryList = mutableListOf<SessionRecord>()

    fun start() {
        loadHistory()
        sessionStartTimeMs = System.currentTimeMillis()

        scheduler.scheduleAtFixedRate({
            tick()
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun updateState(state: ControlState) {
        val oldState = latestState
        latestState = state

        // Detect new hand interactions
        if (!oldState.leftHand.isPresent && state.leftHand.isPresent ||
            !oldState.rightHand.isPresent && state.rightHand.isPresent ||
            oldState.activeTouchPoints.size < state.activeTouchPoints.size
        ) {
            handInteractions++
        }
    }

    private fun tick() {
        sessionDurationSec++

        val isEyeContact = latestState.isFaceLocked && latestState.gazeData.isFaceDetected && latestState.isGazeFocusingOnJellyfish
        if (isEyeContact) {
            activeEyeContactSec++

            currentStreakSec++
            if (currentStreakSec > longestStreakSec) {
                longestStreakSec = currentStreakSec
            }
        } else {
            currentStreakSec = 0L
        }

        val eyeRatio = if (sessionDurationSec > 0) activeEyeContactSec.toFloat() / sessionDurationSec else 0f
        val touchScore = (handInteractions * 2f).coerceAtMost(30f)
        val scorePercent = ((eyeRatio * 70f) + touchScore).toInt().coerceIn(0, 100)

        val updatedMetrics = EngagementMetrics(
            sessionDurationSeconds = sessionDurationSec,
            activeEyeContactSeconds = activeEyeContactSec,
            longestFocusStreakSeconds = longestStreakSec,
            currentFocusStreakSeconds = currentStreakSec,
            handInteractionCount = handInteractions,
            engagementScorePercent = scorePercent,
            sessionHistory = sessionHistoryList.toList()
        )

        onMetricsUpdate(updatedMetrics)
    }

    fun saveSessionRecord() {
        if (sessionDurationSec < 5) return

        val eyeRatio = if (sessionDurationSec > 0) activeEyeContactSec.toFloat() / sessionDurationSec else 0f
        val touchScore = (handInteractions * 2f).coerceAtMost(30f)
        val scorePercent = ((eyeRatio * 70f) + touchScore).toInt().coerceIn(0, 100)

        val record = SessionRecord(
            timestampMs = sessionStartTimeMs,
            durationSeconds = sessionDurationSec,
            activeEyeContactSeconds = activeEyeContactSec,
            longestStreakSeconds = longestStreakSec,
            engagementScorePercent = scorePercent
        )

        sessionHistoryList.add(0, record)
        saveHistory()
    }

    private fun loadHistory() {
        try {
            if (!historyFile.exists()) return
            val content = historyFile.readText()
            val array = JSONArray(content)
            sessionHistoryList.clear()
            for (i in 0 until array.length().coerceAtMost(50)) {
                val obj = array.getJSONObject(i)
                sessionHistoryList.add(
                    SessionRecord(
                        timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                        durationSeconds = obj.optLong("durationSeconds", 0),
                        activeEyeContactSeconds = obj.optLong("activeEyeContactSeconds", 0),
                        longestStreakSeconds = obj.optLong("longestStreakSeconds", 0),
                        engagementScorePercent = obj.optInt("engagementScorePercent", 0)
                    )
                )
            }
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        try {
            val array = JSONArray()
            sessionHistoryList.take(50).forEach { r ->
                val obj = JSONObject().apply {
                    put("timestampMs", r.timestampMs)
                    put("durationSeconds", r.durationSeconds)
                    put("activeEyeContactSeconds", r.activeEyeContactSeconds)
                    put("longestStreakSeconds", r.longestStreakSeconds)
                    put("engagementScorePercent", r.engagementScorePercent)
                }
                array.put(obj)
            }
            historyFile.writeText(array.toString())
        } catch (_: Exception) {}
    }

    fun stop() {
        saveSessionRecord()
        try {
            scheduler.shutdownNow()
        } catch (_: Exception) {}
    }
}
